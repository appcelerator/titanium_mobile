'use strict';

const promisify = require('util').promisify;
const path = require('path');
const os = require('os');
const exec = promisify(require('child_process').exec);  // eslint-disable-line security/detect-child-process
const spawn = require('child_process').spawn; // eslint-disable-line security/detect-child-process
const fs = require('fs-extra');
const rollup = require('rollup').rollup;
const babel = require('rollup-plugin-babel');
const resolve = require('rollup-plugin-node-resolve');
const commonjs = require('rollup-plugin-commonjs');
const appc = require('node-appc');
const version = appc.version;
const utils = require('./utils');
const copyFile = utils.copyFile;
const copyFiles = utils.copyFiles;
const downloadURL = utils.downloadURL;
const copyPackageAndDependencies = utils.copyPackageAndDependencies;

const ROOT_DIR = path.join(__dirname, '../..');
const SUPPORT_DIR = path.join(ROOT_DIR, 'support');
const V8_STRING_VERSION_REGEXP = /(\d+)\.(\d+)\.\d+\.\d+/;

/**
 * Given a folder we'd like to zip up and the destination filename, this will zip up the directory contents.
 * Be aware that the top-level of the zip will not be the directory itself, but it's contents.
 *
 * @param  {String}   cwd   The folder whose contents will become the zip contents
 * @param  {String}   filename The output zipfile
 * @returns {Promise<void>}
 */
async function zip(cwd, filename) {
	const command = os.platform() === 'win32' ? path.join(ROOT_DIR, 'build/win32/zip') : 'zip';
	await exec(`${command} -9 -q -r "${path.join('..', path.basename(filename))}" *`, { cwd });

	const outputFolder = path.resolve(cwd, '..');
	const outputFile = path.join(outputFolder, path.basename(filename));

	if (outputFile === filename) {
		return;
	}

	const destFolder = path.dirname(filename);
	return copyFile(outputFolder, destFolder, path.basename(filename));
}

/**
 * @param {string} zipfile zip file to unzip
 * @param {string} dest destination folder to unzip to
 * @returns {Promise<void>}
 */
async function unzip(zipfile, dest) {
	return new Promise((resolve, reject) => {
		console.log(`Unzipping ${zipfile} to ${dest}`);
		const command = os.platform() === 'win32' ? path.join(ROOT_DIR, 'build/win32/unzip') : 'unzip';
		const child = spawn(command, [ '-o', zipfile, '-d', dest ], { stdio: [ 'ignore', 'ignore', 'pipe' ] });
		let err = '';
		child.stderr.on('data', buffer => {
			err += buffer.toString();
		});
		child.on('error', err => reject(err));
		child.on('close', code => {
			if (code !== 0) {
				return reject(new Error(`Unzipping of ${zipfile} exited with non-zero exit code ${code}. ${err}`));
			}
			resolve();
		});
	});
}

function determineBabelOptions() {
	// Pull out android's V8 target (and transform into equivalent chrome version)
	// eslint-disable-next-line security/detect-non-literal-require
	const v8Version = require(path.join(ROOT_DIR, 'android/package.json')).v8.version;
	const found = v8Version.match(V8_STRING_VERSION_REGEXP);
	const chromeVersion = parseInt(found[1] + found[2]); // concat the first two numbers as string, then turn to int
	// Now pull out min IOS target
	// eslint-disable-next-line security/detect-non-literal-require
	const minSupportedIosSdk = version.parseMin(require(path.join(ROOT_DIR, 'iphone/package.json')).vendorDependencies['ios sdk']);
	// TODO: filter to only targets relevant for platforms we're building?
	const options = {
		targets: {
			chrome: chromeVersion,
			ios: minSupportedIosSdk
		},
		useBuiltIns: 'entry',
		// DO NOT include web polyfills!
		exclude: [ 'web.dom.iterable', 'web.immediate', 'web.timers' ]
	};
	// pull out windows target (if it exists)
	if (fs.pathExistsSync(path.join(ROOT_DIR, 'windows/package.json'))) {
		// eslint-disable-next-line security/detect-non-literal-require
		const windowsSafariVersion = require(path.join(ROOT_DIR, 'windows/package.json')).safari;
		options.targets.safari = windowsSafariVersion;
	}
	return {
		presets: [ [ '@babel/env', options ] ],
		exclude: 'node_modules/**'
	};
}

class Packager {
	/**
	 * @param {String} outputDir path to place the temp files and zipfile
	 * @param {String} targetOS  'win32', 'linux', or 'osx'
	 * @param {string[]} platforms The list of SDK platforms to package
	 * @param {string} version version string to use
	 * @param {string} versionTag version tag
	 * @param {string} moduleApiVersion module api version
	 * @param {string} gitHash git commit SHA
	 * @param {string} timestamp build date/timestamp
	 * @param {boolean} [skipZip] Optionally skip zipping up the result
	 * @constructor
	 */
	constructor(outputDir, targetOS, platforms, version, versionTag, moduleApiVersion, gitHash, timestamp, skipZip) {
		this.srcDir = ROOT_DIR;
		this.outputDir = outputDir; // root folder where output is placed
		this.targetOS = targetOS;
		this.platforms = platforms;
		this.version = version;
		this.versionTag = versionTag;
		this.moduleApiVersion = moduleApiVersion;
		this.gitHash = gitHash;
		this.timestamp = timestamp;
		this.zipFile = path.join(this.outputDir, `mobilesdk-${this.versionTag}-${this.targetOS}.zip`);
		// Location where we build up the zip file contents
		this.zipDir = path.join(this.outputDir, `mobilesdk-${this.versionTag}-${this.targetOS}`);
		this.zipSDKDir = path.join(this.zipDir, 'mobilesdk', this.targetOS, this.versionTag);
		this.skipZip = skipZip;
	}

	/**
	 * @returns {Promise<void>}
	 */
	async package() {
		// TODO: we should be able to run large chunks of this in parallel!
		// Really the sstuff in series is:
		// copying node_modules, pruning, removing node_module/.bin, node-ios-device, hacking titanium-sdk in
		// virtually all of the rest should be in parallel (after cleaning zip dir)

		await this.cleanZipDir();
		await this.generateManifestJSON();

		console.log('Writing JSCA');
		await fs.copy(path.join(this.outputDir, 'api.jsca'), path.join(this.zipSDKDir, 'api.jsca'));

		console.log('Copying SDK files');
		// Copy some root files, cli/, templates/, node_modules
		await this.copy([ 'CREDITS', 'README.md', 'package.json', 'cli', 'node_modules', 'templates' ]);

		await this.transpile(); // transpiles/bundles common/

		// Now run 'npm prune --production' on the zipSDKDir, so we retain only production dependencies
		console.log('Pruning to production npm dependencies');
		await exec('npm prune --production', { cwd: this.zipSDKDir });

		// Remove any remaining binary scripts from node_modules
		await fs.remove(path.join(this.zipSDKDir, 'node_modules/.bin'));

		// Now include all the pre-built node-ios-device bindings/binaries
		if (this.targetOS === 'osx') {
			let dir = path.join(this.zipSDKDir, 'node_modules/node-ios-device');
			if (!await fs.exists(dir)) {
				dir = path.join(this.zipSDKDir, 'node_modules/ioslib/node_modules/node-ios-device');
			}

			if (!await fs.exists(dir)) {
				throw new Error('Unable to find node-ios-device module');
			}

			await exec('node bin/download-all.js', { cwd: dir, stdio: 'inherit' });
		}

		// hack the fake titanium-sdk npm package in
		await this.hackTitaniumSDKModule();

		// grab down and unzip the native modules
		await this.includePackagedModules();

		// copy over support/
		await this.copySupportDir();

		// Zip up all the platforms!
		await this.zipPlatforms();

		// zip up the full SDK
		return this.zip();
	}

	/**
	 * Removes existing zip file and tmp dir used to build it
	 * @returns {Promise<void>}
	 */
	async cleanZipDir() {
		console.log('Cleaning previous zipfile and tmp dir');
		// make sure zipSDKDir exists and is empty
		await fs.emptyDir(this.zipSDKDir);

		// Remove existing zip
		return fs.remove(this.zipFile);
	}

	/**
	 * generates the manifest.json we ship with the SDK
	 * @returns {Promise<void>}
	 */
	async generateManifestJSON() {
		console.log('Writing manifest.json');
		const modifiedPlatforms = this.platforms.slice(0); // need to work on a copy!
		const json = {
			name: this.versionTag,
			version: this.version,
			moduleAPIVersion: this.moduleApiVersion,
			timestamp: this.timestamp,
			githash: this.gitHash
		};

		// Replace ios with iphone
		const index = modifiedPlatforms.indexOf('ios');
		if (index !== -1) {
			modifiedPlatforms.splice(index, 1, 'iphone');
		}
		json.platforms = modifiedPlatforms;
		return fs.writeJSON(path.join(this.zipSDKDir, 'manifest.json'), json);
	}

	/**
	 * Copy files from ROOT_DIR to zipDir.
	 * @param {string[]} files List of files/folders to copy
	 * @returns {Promise<void>}
	 */
	async copy(files) {
		return copyFiles(this.srcDir, this.zipSDKDir, files);
	}

	async transpile() {
		// Copy over common dir, @babel/polyfill, etc into some temp dir
		// Then run rollup/babel on it, then just copy the resulting bundle to our real destination!
		// The temporary location we'll assembled the transpiled bundle
		const tmpBundleDir = path.join(this.zipSDKDir, 'common_temp');

		console.log('Copying common SDK JS over');
		await fs.copy(path.join(this.srcDir, 'common'), tmpBundleDir);

		// copy over polyfill and its dependencies
		console.log('Copying JS polyfills over');
		const modulesDir = path.join(tmpBundleDir, 'Resources/node_modules');
		// make sure our 'node_modules' directory exists
		await fs.ensureDir(modulesDir);
		copyPackageAndDependencies('@babel/polyfill', modulesDir);

		console.log('Transpiling and bundling common SDK JS');
		// the ultimate destinatio for our common SDK JS
		const destDir = path.join(this.zipSDKDir, 'common');
		// create a bundle
		console.log('running rollup');
		const babelOptions = determineBabelOptions();
		const bundle = await rollup({
			input: `${tmpBundleDir}/Resources/ti.main.js`,
			plugins: [
				resolve(),
				commonjs(),
				babel(babelOptions)
			],
			external: [ './app', 'com.appcelerator.aca' ]
		});

		// write the bundle to disk
		console.log('Writing common SDK JS bundle to disk');
		await bundle.write({ format: 'cjs', file: `${destDir}/Resources/ti.main.js` });

		// Copy over the files we can't bundle/inline: common/Resources/ti.internal
		await fs.copy(path.join(this.srcDir, 'common/Resources/ti.internal'), path.join(destDir, 'Resources/ti.internal'));

		// Remove the temp dir we assembled the parts inside!
		console.log('Removing temporary common SDK JS bundle directory');
		await fs.remove(tmpBundleDir);
	}

	async hackTitaniumSDKModule() {
		// FIXME Remove these hacks for titanium-sdk when titanium-cli has been released and the tisdk3fixes.js hook is gone!
		// Now copy over hacked titanium-sdk fake node_module
		console.log('Copying titanium-sdk node_module stub for backwards compatibility with titanium-cli');
		await fs.copy(path.join(__dirname, '../titanium-sdk'), path.join(this.zipSDKDir, 'node_modules/titanium-sdk'));

		// Hack the package.json to include "titanium-sdk": "*" in dependencies
		console.log('Inserting titanium-sdk as production dependency');
		const packageJSONPath = path.join(this.zipSDKDir, 'package.json');
		const packageJSON = require(packageJSONPath); // eslint-disable-line security/detect-non-literal-require
		packageJSON.dependencies['titanium-sdk'] = '*';
		return fs.writeJSON(packageJSONPath, packageJSON);
	}

	/**
	 * Includes the pre-packaged pre-built native modules. We now gather them from a JSON file listing URLs to download.
	 * @returns {Promise<void>}
	 */
	async includePackagedModules() {
		console.log('Zipping packaged modules');
		// Unzip all the zipfiles in support/module/packaged
		const supportedPlatforms = this.platforms.concat([ 'commonjs' ]); // we want a copy here...
		// Include aliases for ios/iphone/ipad
		if (supportedPlatforms.includes('ios')
			|| supportedPlatforms.includes('iphone')
			|| supportedPlatforms.includes('ipad')) {
			supportedPlatforms.push('ios', 'iphone', 'ipad');
		}

		// Hyperloop has no single platform downloads yet, so we use a fake platform
		// that will download the all-in-one distribution.
		supportedPlatforms.push('hyperloop');

		let modules = []; // module objects holding url/integrity
		// Read modules.json, grab the object for each supportedPlatform
		// eslint-disable-next-line security/detect-non-literal-require
		const modulesJSON = require(path.join(SUPPORT_DIR, 'module', 'packaged', 'modules.json'));
		supportedPlatforms.forEach(platform => {
			const modulesForPlatform = modulesJSON[platform];
			if (modulesForPlatform) {
				modules = modules.concat(Object.values(modulesForPlatform));
			}
		});
		// remove duplicates
		modules = Array.from(new Set(modules));

		// Fetch the listed modules from URLs...
		// FIXME Don't show progress bars, because they clobber each other
		const zipFiles = await Promise.all(modules.map(m => downloadURL(m.url, m.integrity)));

		// ...then unzip them
		// MUST RUN IN SERIES or they will clobber each other and unzip will fail mysteriously
		const outDir = this.zipDir;
		for (const zipFile of zipFiles) {
			await unzip(zipFile, outDir);
		}
	}

	async copySupportDir() {
		const ignoreDirs = [ 'packaged', '.pyc', path.join(SUPPORT_DIR, 'dev') ];
		// Copy support/ into root, but filter out folders based on OS
		if (this.targetOS !== 'osx') {
			ignoreDirs.push(path.join(SUPPORT_DIR, 'iphone'), path.join(SUPPORT_DIR, 'osx'));
		}
		if (this.targetOS !== 'win32') {
			ignoreDirs.push(path.join(SUPPORT_DIR, 'win32'));
		}
		// FIXME: Usee Array.prototype.some to filter more succinctly
		const filter = src => {
			for (const ignore of ignoreDirs) {
				if (src.includes(ignore)) {
					return false;
				}
			}
			return true;
		};
		return fs.copy(SUPPORT_DIR, this.zipSDKDir, { filter });
	}

	async zipPlatforms() {
		// TODO: do in parallel?
		for (const p of this.platforms) {
			const Platform = require(`./${p}`); // eslint-disable-line security/detect-non-literal-require
			await new Platform({ sdkVersion: this.version, gitHash: this.gitHash, timestamp: this.timestamp }).package(this);
		}
	}

	/**
	 * Zip it all up and wipe the zip dir
	 * @returns {Promise<void>}
	 */
	async zip() {
		if (this.skipZip) {
			return;
		}

		console.log(`Zipping up packaged SDK to ${this.zipFile}`);
		await zip(this.zipDir, this.zipFile);

		// delete the zipdir!
		return fs.remove(this.zipDir);
	}
}

module.exports = Packager;
