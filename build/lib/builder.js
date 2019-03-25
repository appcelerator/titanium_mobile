'use strict';

const os = require('os');
const path = require('path');
const fs = require('fs-extra');

const git = require('./git');
const utils = require('./utils');
const packageJSON = require('../../package.json');

const ROOT_DIR = path.join(__dirname, '../..');
const DIST_DIR = path.join(ROOT_DIR, 'dist');

// platforms/OS mappings
const ALL_OSES = [ 'win32', 'linux', 'osx' ];
const ALL_PLATFORMS = [ 'ios', 'android', 'windows' ];
const OS_TO_PLATFORMS = {
	win32: [ 'android', 'windows' ],
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

class Builder {
	constructor(program) {
		this.hostOS = thisOS();

		let platforms = program.args;
		if (program.all) { // packaging for all platforms on all OSes
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

		this.program = program;
		this.program.timestamp = utils.timestamp();
		this.program.versionTag = program.versionTag || program.sdkVersion;
	}

	async clean() {
		for (const p of this.platforms) {
			const Platform = require(`./${p}`); // eslint-disable-line security/detect-non-literal-require
			const platform = new Platform(this.program);
			await platform.clean();
		}
	}

	async test() {
		const zipfile = path.join(DIST_DIR, `mobilesdk-${this.program.versionTag}-${this.hostOS}.zip`);
		// Only enforce zipfile exists if we're going to install it
		if (!this.program.skipSdkInstall && !await fs.exists(zipfile)) {
			throw new Error(`Could not find zipped SDK in dist dir: ${zipfile}. Please run node scons.js cleanbuild first.`);
		}

		const test = require('./test');
		const runTests = test.runTests;
		const outputResults = test.outputResults;
		const results = await runTests(zipfile, this.platforms, this.program);
		return outputResults(results);
	}

	async ensureGitHash() {
		if (this.program.gitHash) {
			return;
		}
		const hash = await git.getHash(ROOT_DIR);
		this.program.gitHash = hash || 'n/a';
	}

	async build() {
		await this.ensureGitHash();
		console.log('Building MobileSDK version %s, githash %s', this.program.sdkVersion, this.program.gitHash);
		for (const item of this.platforms) {
			const Platform = require(`./${item}`); // eslint-disable-line security/detect-non-literal-require
			const platform = new Platform(this.program);
			await platform.build();
		}
	}

	async package() {
		await this.ensureGitHash();

		console.log('Packaging Mobile SDK (%s)...', this.program.versionTag);

		// Match our master platform list against OS_TO_PLATFORMS[item] listing.
		// Only package the platform if its in both arrays
		const platformsForThisOS = OS_TO_PLATFORMS[this.hostOS];
		const filteredPlatforms = this.platforms.filter(p => platformsForThisOS.includes(p));

		const Packager = require('./packager');
		const packager = new Packager(DIST_DIR, this.hostOS, filteredPlatforms, this.program.sdkVersion, this.program.versionTag, packageJSON.moduleApiVersion, this.program.gitHash, this.program.timestamp, this.program.skipZip);
		await packager.package();
		console.log(`Packaging version (${this.program.versionTag}) complete`);
	}

	async generateDocs() {
		if (!this.program.docs) { // are we skipping doc generation?
			return;
		}

		const Documentation = require('./docs');
		const docs = new Documentation(DIST_DIR);
		return docs.generate();
	}

	async install () {
		return utils.installSDK(this.program.versionTag);
	}
}

module.exports = Builder;
