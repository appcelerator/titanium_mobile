import os from 'os';
import path from 'path';
import fs from 'fs-extra';
import { rollup } from 'rollup';
import { babel } from '@rollup/plugin-babel';
import { nodeResolve } from '@rollup/plugin-node-resolve';
import commonjs from '@rollup/plugin-commonjs';
import { runTests, outputMultipleResults } from './test/index.mjs';
import git from './git.js';
import utils from './utils.js';
import Packager from './packager.js';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const require = createRequire(import.meta.url);
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.join(__dirname, '..', '..');
const DIST_DIR = path.join(ROOT_DIR, 'dist');
const TMP_DIR = path.join(DIST_DIR, 'tmp');

// Platforms/OS mappings
const ALL_OSES = [ 'win32', 'osx', 'linux' ];
const ALL_PLATFORMS = [ 'ios', 'android' ];
const OS_TO_PLATFORMS = {
	win32: [ 'android' ],
	osx: [ 'android', 'ios' ],
	linux: [ 'android' ]
};

function thisOS() {
	let osName = os.platform();
	if (osName === 'darwin') {
		osName = 'osx';
	}
	return osName;
}

function determineBabelOptions(babelOptions) {
	const options = {
		...babelOptions,
		useBuiltIns: 'entry',
		corejs: getCorejsVersion()
	};
	// extract out options.transform used by babel-plugin-transform-titanium
	const transform = options.transform || {};
	delete options.transform;

	return {
		presets: [ [ '@babel/env', options ] ],
		plugins: [ [ require.resolve('babel-plugin-transform-titanium'), transform ] ],
		exclude: 'node_modules/**',
		babelHelpers: 'bundled'
	};
}

/**
 * Returns the exact core-js version from package-lock file, this is required to ensure that the
 * correct polyfills are loaded for the environment
 *
 * @returns {String}
 */
function getCorejsVersion() {
	const packageLock = require('../../package-lock');
	if (packageLock.dependencies && packageLock.dependencies['core-js']) {
		const { version } = packageLock.dependencies['core-js'];
		return version;
	}
	throw new Error('Could not lookup core-js version in package-lock file.');
}

export default class Builder {

	/**
	 * @param {object} options command line options
	 * @param {string} options.sdkVersion version of Titanium SDK
	 * @param {string} [options.versionTag] full version tag to use for Titanium SDK
	 * @param {string} [options.gitHash] SHA of Titanium SDK HEAD
	 * @param {boolean} [options.all=false] build for all platforms?
	 * @param {boolean} [options.symlink=false] symlink the SDK to install it?
	 * @param {boolean} [options.select=false] select the built SDK in Ti CLI after install?
	 * @param {boolean} [options.skipZip] Optionally skip zipping up the result
	 * @param {boolean} [options.docs] Generate docs?
	 * @param {string[]} [platforms] command line arguments (platform listing)
	 */
	constructor(options, platforms) {
		this.hostOS = thisOS();

		if (options.all) { // packaging for all platforms on all OSes
			this.platforms = ALL_PLATFORMS;
			this.oses = ALL_OSES;
		} else if (!platforms.length || (platforms.length === 1 && platforms[0] === 'full')) { // building/testing/cleaning with no specific platform selected, use all
			this.oses = [ this.hostOS ];
			this.platforms = OS_TO_PLATFORMS[this.hostOS];
		} else { // user enumerated the platforms to work with, assume this current host OS
			// TODO Replace 'ipad' or 'iphone' with 'ios'
			this.platforms = platforms;
			this.oses = [ this.hostOS ];
		}

		this.options = options;
		this.options.timestamp = utils.timestamp();
		this.options.versionTag = options.versionTag || options.sdkVersion;
	}

	async clean() {
		// TODO: Clean platforms in parallel
		for (const p of this.platforms) {
			const Platform = require(`./${p}`); // eslint-disable-line security/detect-non-literal-require
			const platform = new Platform(this.options);
			await platform.clean();
		}
		// TODO: Construct a Packager and have it clean zipdir/file too?
		return fs.remove(TMP_DIR);
	}

	async test() {
		const results = await runTests(this.platforms, this.options);
		return outputMultipleResults(results);
	}

	async ensureGitHash() {
		if (this.options.gitHash) {
			return;
		}
		const hash = await git.getHash(ROOT_DIR);
		this.options.gitHash = hash || 'n/a';
	}

	async generateKernelBundle(platform, babelOptions, outDir) {
		const TMP_COMMON_DIR = path.join(TMP_DIR, '_kernel');
		const TMP_COMMON_PLAFORM_DIR = path.join(TMP_DIR, '_kernel', platform);

		console.log(`Creating temporary 'kernel' directory...`); // eslint-disable-line quotes
		await fs.copy(path.join(ROOT_DIR, 'common'), TMP_COMMON_PLAFORM_DIR);

		// Write the bootstrap file
		const bootstrapBundle = await rollup({
			input: `${TMP_COMMON_PLAFORM_DIR}/Resources/ti.kernel.js`,
			plugins: [
				commonjs(),
				babel(determineBabelOptions(babelOptions))
			],
			external: [ ]
		});

		const tiBootstrapJs = path.join(outDir, 'ti.kernel.js');
		console.log(`Writing 'kernel' bundle to ${tiBootstrapJs} ...`); // eslint-disable-line quotes
		await bootstrapBundle.write({ format: 'iife', file: tiBootstrapJs });

		console.log(`Removing temporary 'common' directory...`); // eslint-disable-line quotes
		await fs.remove(TMP_COMMON_DIR);
	}

	async generateTiMain(platform, babelOptions, outDir) {
		// Copy over common dir, into some temp dir
		// Then run rollup/babel on it, then just copy the resulting bundle to our real destination!
		// The temporary location we'll assembled the transpiled bundle
		const TMP_COMMON_DIR = path.join(TMP_DIR, '_common');
		const TMP_COMMON_PLAFORM_DIR = path.join(TMP_DIR, '_common', platform);

		console.log(`Creating temporary 'common' directory...`); // eslint-disable-line quotes
		await fs.copy(path.join(ROOT_DIR, 'common'), TMP_COMMON_PLAFORM_DIR);

		// create a bundle
		console.log('Transpile and run rollup...');
		const bundle = await rollup({
			input: `${TMP_COMMON_PLAFORM_DIR}/Resources/ti.main.js`,
			plugins: [
				nodeResolve(),
				commonjs(),
				babel(determineBabelOptions(babelOptions))
			],
			external: [ './app', 'com.appcelerator.aca' ]
		});

		const tiMainJs = path.join(outDir,  'ti.main.js');
		console.log(`Writing 'common' bundle to ${tiMainJs} ...`); // eslint-disable-line quotes
		await bundle.write({ format: 'cjs', file: tiMainJs });

		// We used to have to copy over ti.internal, but it is now bundled into ti.main.js
		// if we ever have files there that cannot be bundled or are not hooked up properly, we'll need to copy them here manually.

		console.log(`Removing temporary 'common' directory...`); // eslint-disable-line quotes
		await fs.remove(TMP_COMMON_DIR);
	}

	async transpile(platform, babelOptions, outDir) {
		if (!outDir) {
			outDir = path.join(TMP_DIR, 'common', platform);
		}
		await fs.emptyDir(outDir);
		return Promise.all([
			this.generateTiMain(platform, babelOptions, outDir),
			this.generateKernelBundle(platform, babelOptions, outDir),
		]);
	}

	async build() {
		await this.ensureGitHash();
		console.log('Building MobileSDK version %s, githash %s', this.options.sdkVersion, this.options.gitHash);

		// TODO: build platforms in parallel
		for (const item of this.platforms) {
			const Platform = require(`./${item}`); // eslint-disable-line security/detect-non-literal-require
			const platform = new Platform(this.options);
			await this.transpile(item, platform.babelOptions());
			await platform.build();
		}
	}

	async package() {
		await this.ensureGitHash();

		console.log('Packaging Mobile SDK (%s)...', this.options.versionTag);
		// FIXME: Work on allowing parallel OS packaging
		// Only hurdle for packaging in parallel seems to be if a commonjs/hyperloop modules needs to
		// be downloaded (each os would try to grab it simultaneously)
		// await Promise.all(this.oses.map(currentOS => this.packageForOS(currentOS)));

		// For now, package for each os in series
		for (const os of this.oses) {
			await this.packageForOS(os);
		}
		console.log(`Packaging version (${this.options.versionTag}) complete`);
	}

	async packageForOS(currentOS) {
		// Match our master platform list against OS_TO_PLATFORMS[item] listing.
		// Only package the platform if its in both arrays
		const platformsForThisOS = OS_TO_PLATFORMS[currentOS];
		const filteredPlatforms = this.platforms.filter(p => platformsForThisOS.includes(p));

		const packager = new Packager(DIST_DIR, currentOS, filteredPlatforms, this.options);
		return packager.package();
	}

	async generateDocs() {
		if (!this.options.docs) { // are we skipping doc generation?
			return;
		}

		const Documentation = require('./docs');
		const docs = new Documentation(DIST_DIR);
		return docs.generate();
	}

	async install(zipfile) {
		if (zipfile) {
			// Assume we have explicitly said to install this zipfile (from CLI command)
			zipfile = path.resolve(process.cwd(), zipfile);
			return utils.installSDKFromZipFile(zipfile, this.options.select);
		}
		// Otherwise use fuzzier logic that tries to install local dev built version
		return utils.installSDK(this.options.versionTag, this.options.symlink);
	}
}

