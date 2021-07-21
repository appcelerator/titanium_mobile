/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2020 by Axway. All Rights Reserved.
 * Licensed under the terms of the Apache Public License.
 * Please see the LICENSE included with this distribution for details.
 */
/* eslint no-unused-expressions: "off" */
/* eslint security/detect-child-process: "off" */
import { promisify } from 'util';
import ejs from 'ejs';
import fs from 'fs-extra';
import path from 'path';
import request from 'request-promise-native';
import AndroidBuilder from '../../build/lib/android/index.js';
import Builder from '../../build/lib/builder.mjs';
import BuildUtils from '../../build/lib/utils.js';
import { fileURLToPath } from 'url';
import { exec as execSync, execFile as execFileSync } from 'child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const exec = promisify(execSync);
const execFile = promisify(execFileSync);

// Determine if we're running on a Windows machine.
const isWindows = process.platform === 'win32';
const isLinux = process.platform === 'linux';

/**
 * Double quotes given path and escapes double quote characters in file/directory names.
 * @param {String} filePath The path to be double quoted. Can be null/undefined.
 * @return {String} Returns the double quoted path. Will be null/undefined if given argument is null/undefined.
 */
function quotePath(filePath) {
	if (!filePath) {
		return '""';
	}
	if (!isWindows) {
		filePath = filePath.replace(/"/g, '\\"');
	}
	return `"${filePath}"`;
}

/**
 * Async loads the "titanium_mobile/android/package.json" file and returns it as a dictionary.
 * @returns {Promise<Object>} Dictionary of the parsed JSON file if loaded successfully.
 */
async function loadPackageJson() {
	return fs.readJson(new URL('../package.json', import.meta.url));
}

/**
 * Generate snapshots locally.
 * @param {String} v8SnapshotHeaderFilePath The path to save generated snapshot header.
 * @param {String} rollupFileContent Javascript content to store in snapshot.
 */
async function generateSnapshot(v8SnapshotHeaderFilePath, rollupFileContent) {

	if (!isLinux) {
		console.warn('Snapshot generation only available on linux, skipping...');
		return;
	}

	const distTmpPath = path.join(__dirname, '..', '..', 'dist', 'tmp');
	const startupPath = path.join(distTmpPath, 'startup.js');

	// Create startup.js
	await fs.ensureDir(distTmpPath);
	await fs.writeFile(startupPath, `this._startSnapshot = global => { ${rollupFileContent} };`);

	const packageJsonData = await loadPackageJson();
	const v8TargetVersion = packageJsonData.v8.version;
	const v8TargetMode = packageJsonData.v8.mode;
	const v8LibDirectory = path.join(__dirname, '..', '..', 'dist', 'android', 'libv8', v8TargetVersion, v8TargetMode, 'libs');

	// Obtain V8 architectures.
	const archs = (await fs.readdir(v8LibDirectory, { withFileTypes: true }))
		.filter(entry => entry.isDirectory())
		.map(entry => entry.name);

	console.log(`Found architectures ${archs.join(', ')}`);

	let blobs = {};

	// Generate snapshots.
	for (const arch of archs) {
		const mksnapshotPath = path.join(v8LibDirectory, arch, 'mksnapshot');
		const blobPath = path.join(v8LibDirectory, arch, 'blob.bin');
		const emboddedPath = path.join(v8LibDirectory, arch, 'embedded.S');

		// Delete existing snapshot blob.
		try {
			await fs.unlink(blobPath);
		} catch (e) {
			// Do nothing...
		}

		const args = [
			'--startup_blob=' + blobPath,
			startupPath
		];

		console.log(`Generating snapshot blob for ${arch}...`);

		// Include embedded blob.
		if (await fs.exists(emboddedPath)) {
			args.unshift(
				'--turbo_instruction_scheduling',
				'--embedded_src=' + emboddedPath,
				'--embedded_variant=Default',
				'--no-native-code-counters',
			);
		}

		// Generate snapshot blob.
		try {
			await fs.chmod(mksnapshotPath, 0o755);
			await execFile(mksnapshotPath, args);
		} catch (e) {
			console.error(e);
		}

		// Load snapshot blob.
		if (await fs.exists(blobPath)) {
			blobs[arch] = Buffer.from(await fs.readFile(blobPath, 'binary'), 'binary');
			console.log(`Generated ${arch} snapshot blob.`);
		}

		// Delete snapshot blob.
		await fs.unlink(blobPath);
	}

	// Generate 'V8Snapshots.h' from template
	const template = await promisify(ejs.renderFile)('V8Snapshots.h.ejs', { blobs }, {});
	await fs.writeFile(v8SnapshotHeaderFilePath, template);

	console.log('Generated snapshot header.');
}

/**
 * Does a transpile/polyfill/rollup of our "titanium_mobile/common/Resources" JS files.
 * Will then generate a C++ header file providing a V8 snapshot of the rolled-up JS for fast startup times.
 */
export async function createSnapshot() {
	// Perform a transpile/polyfill/rollup of our common JS files.
	// This contains our "ti.main.js" which the app executes on startup before executing the "app.js" file.
	const rollupOutputDirPath = path.join(__dirname, 'build', 'outputs', 'ti-assets', 'Resources');
	const rollupOutputFilePath = path.join(rollupOutputDirPath, 'ti.main.js');
	await fs.ensureDir(rollupOutputDirPath);

	const options = { };
	const mainBuilder = new Builder(options, [ 'android' ]);
	await mainBuilder.ensureGitHash();
	const sdkVersion = (await fs.readJson(new URL('../../package.json', import.meta.url))).version;
	const androidBuilder = new AndroidBuilder({
		sdkVersion,
		gitHash: options.gitHash,
		timestamp: options.timestamp
	});
	await mainBuilder.generateTiMain('android', androidBuilder.babelOptions(), rollupOutputDirPath);
	const rollupFileContent = (await fs.readFile(rollupOutputFilePath)).toString();

	// Create the C++ directory we'll be generating the snapshot header file to.
	const cppOutputDirPath = path.join(__dirname, '..', 'runtime', 'v8', 'generated');
	const v8SnapshotHeaderFilePath = path.join(cppOutputDirPath, 'V8Snapshots.h');
	await fs.ensureDir(cppOutputDirPath);

	if (isLinux) {

		// Generate snapshots locally.
		await generateSnapshot(v8SnapshotHeaderFilePath, rollupFileContent);
	} else {

		// Requests our server to create snapshot of rolled-up "ti.main" in a C++ header file.
		let wasSuccessful = false;
		try {
			// Post rolled-up "ti.main" script to server and obtain a snapshot ID as a response.
			// We will send an HTTP request for the snapshot code later.
			console.log('Attempting to request snapshot...');
			const snapshotUrl = 'https://v8-snapshot.appcelerator.com';
			const packageJsonData = await loadPackageJson();
			const requestOptions = {
				body: {
					v8: packageJsonData.v8.version,
					script: rollupFileContent
				},
				json: true
			};
			const snapshotId = await request.post(snapshotUrl, requestOptions);

			// Request generated snapshot from server using `snapshotId` obtained from server above.
			const MAX_ATTEMPTS = 20; // Time-out after two minutes.
			let attempts;
			for (attempts = 1; attempts <= MAX_ATTEMPTS; attempts++) {
				const response = await request.get(`${snapshotUrl}/snapshot/${snapshotId}`, {
					simple: false,
					resolveWithFullResponse: true
				});

				if (response.statusCode === 200) {

					// Server has finished creating a C++ header file containing all V8 snapshots.
					// Write it to file and flag that we're done.
					console.log('Writing snapshot...');
					await fs.writeFile(v8SnapshotHeaderFilePath, response.body);
					wasSuccessful = true;
					break;
				} else if (response.statusCode === 202) {

					// Snapshot server is still building. We need to retry later.
					console.log('Waiting for snapshot generation...');
					await new Promise(resolve => setTimeout(resolve, 6000));
				} else {

					// Give up if received an unexpected response.
					console.error('Could not generate snapshot, skipping...');
					break;
				}
			}
			if (attempts > MAX_ATTEMPTS) {
				console.error('Max retries exceeded fetching snapshot from server, skipping...');
			}
		} catch (err) {
			console.error(`Failed to request snapshot: ${err}`);
		}

		// Do the following if we've failed to generate snapshot header file above.
		// Note: The C++ build will fail if file is missing. This is because it is #included in our code.
		if (!wasSuccessful) {

			// Trigger a build failure if snapshots are required. The "titanium_mobile/build" SDK build scripts set this.
			if (process.env.TI_SDK_BUILD_REQUIRES_V8_SNAPSHOTS === '1') {
				process.exit(1);
			}

			// Generaet an empty C++ header. Allows build to succeed and app will load "ti.main.js" normally instead.
			await fs.writeFile(v8SnapshotHeaderFilePath, '// Failed to generate V8 snapshots. See build log.');
		}
	}
}

/**
 * Checks if the V8 library referenced by the "titanium_mobile/android/package.json" file is installed.
 * If not, then this function will automatically download/install it. Function will do nothing if already installed.
 */
export async function updateLibrary() {
	// Fetch info about the V8 library we should be building with.
	// This info is stored in our "package.json" under "titanium_mobile/android" folder.
	const packageJsonData = await loadPackageJson();
	const v8TargetVersion = packageJsonData.v8.version;
	const v8TargetMode = packageJsonData.v8.mode;
	const integrity = packageJsonData.v8.integrity;
	const v8ArchiveFileName = `libv8-${v8TargetVersion}-${v8TargetMode}.tar.bz2`;
	const installedLibV8DirPath = path.join(
		__dirname, '../../dist/android/libv8', v8TargetVersion, v8TargetMode);

	// Download V8 archive (downloads to temp dir, which helps CI server avoid re-downloading between builds generally)
	const downloadUrl = `https://github.com/appcelerator/v8_titanium/releases/download/v${v8TargetVersion}/${v8ArchiveFileName}`;
	// FIXME: Can we skip the download if the ultimate destination exists?!
	const downloadedTarball = await BuildUtils.downloadURL(downloadUrl, integrity, { progress: false });
	let tmpExtractDir = BuildUtils.cachedDownloadPath(downloadUrl); // store alongside the place we store the tar.bz2, just drop the extension
	tmpExtractDir = tmpExtractDir.substring(0, tmpExtractDir.length - '.tar.bz2'.length); // drop .tar.bz2
	// Now extract to tmp dir and "cache" it for later builds
	await BuildUtils.cacheExtract(downloadedTarball, integrity, tmpExtractDir, async function (inFile, outDir) {
		// Extract the downloaded V8 archive's files.
		console.log(`Decompressing downloaded V8 file: ${inFile}`);
		const untarCommandLine
			= quotePath(path.join(__dirname, '..', isWindows ? 'gradlew.bat' : 'gradlew'))
			+ ' -b ' + quotePath(path.join(__dirname, '..', 'untar.gradle'))
			+ ' -Pcompression=bzip2'
			+ ' -Psrc=' + quotePath(inFile)
			+ ' -Pdest=' + quotePath(outDir);
		return exec(untarCommandLine);
	});
	// Now copy from tmp dir to ultimate destination
	return fs.copy(tmpExtractDir, installedLibV8DirPath);
}

/**
 * Does a transpile/polyfill/rollup of our "titanium_mobile/common/Resources" JS files.
 * Will then generate a C++ header file providing a V8 snapshot of the rolled-up JS for fast startup times.
 *
 * Will exit the process when the async operation ends. Intended to be called from the command line.
 */
export function createSnapshotThenExit() {
	exitWhenDone(createSnapshot());
}

/**
 * Checks if the V8 library referenced by the "titanium_mobile/android/package.json" file is installed.
 * If not, then this function will automatically download/install it. Function will do nothing if alredy installed.
 *
 * Will exit the process when the async operation ends. Intended to be called from the command line.
 */
export function updateLibraryThenExit() {
	exitWhenDone(updateLibrary());
}

/**
 * Exits the process when the given promise's operation ends.
 * @param {Promise} promise The promise to be monitored. Cannot be null/undefined.
 */
function exitWhenDone(promise) {
	promise
		.then(() => process.exit(0))
		.catch((err) => {
			console.error(err);
			process.exit(1);
		});
}

if (process.argv.length > 0) {
	const lastArg = process.argv.pop();
	if (lastArg === 'createSnapshotThenExit') {
		createSnapshotThenExit();
	} else if (lastArg === 'updateLibraryThenExit') {
		updateLibraryThenExit();
	} else {
		console.error(`Unknown argument to libv8-services: ${lastArg}`);
	}
}
