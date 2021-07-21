#!/usr/bin/env node
'use strict';

const commander = require('commander');
const version = require('../package.json').version;

commander
	.version(version)
	.command('check-lockfile', 'Ensures there\'s no mismatch between version string and assumed version from url in our package-lock.json')
	.command('clean [platforms]', 'clean up build directories for one or more platforms')
	.command('clean-modules', 'clean up global modules folder (mainly used for CI)')
	.command('clean-sdks', 'clean up sdk installs (mainly used for CI, defaults to non-GA versions)')
	.command('cleanbuild [platforms]', 'clean, build, package and install locally')
	.command('install [zipfile]', 'install the built SDK')
	.command('package [platforms]', 'package one or more platforms')
	.command('build [platforms]', 'build one or more platforms')
	.command('test [platforms]', 'Runs our unit tests')
	.command('ssri [urls]', 'generates ssri integrity hashes for URLs')
	.command('modules-integrity', 'Regenerates ssri integrity hashes for all the modules in our pre-packaged listing under support/module/packged/modules.json given the current url values')
	.command('xcode-test', 'Hacks the XCode project for iOS to copy in the unit test suite so it can be run under XCode\'s debugger')
	.command('check-ios-toplevel', 'Ensures we don\'t check in prefilled values for version/hash/timestamp')
	.command('xcode-project-build <projectDir> <targetBuildDir> <productName>', 'Runs the portion of the xcode project setup')
	.command('gradlew <task>', 'Executes an Android gradle task via the gradlew command line tool')
	.command('deprecations', 'Checks the apidocs for deprecated but unremoved types/properties/methods')
	.command('removals <minVersion>', 'Checks the apidocs for deprecated and removed types/properties/methods older than a given version')
	.command('generate-test <pathToapidocYmlFile>', 'Generates a unit test suite for a given type')
	.command('missing-tests [apiDocDir]', 'List types defined in APIdocs that don\'t have a test suite')
	.parse(process.argv);
