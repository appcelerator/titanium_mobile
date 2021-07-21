/**
 * iOS build hook that scans for available frameworks from modules and in the
 * project folder and then configures them in the Xcode project
 */

'use strict';

const exec = require('child_process').exec; // eslint-disable-line security/detect-child-process
const fs = require('fs-extra');
const IncrementalFileTask = require('appc-tasks').IncrementalFileTask;
const path = require('path');
const plist = require('simple-plist');
const { promisify } = require('util');

const parsePlist = promisify(plist.readFile);

const frameworkPattern = /([^/]+)\.(xc)?framework$/;

const FRAMEWORK_TYPE_STATIC = 'static';
const FRAMEWORK_TYPE_DYNAMIC = 'dynamic';

const MAC_CATALYST_PLATFORM_VARIANT = 'maccatalyst';
const IOS_PLATFORM = 'ios';

exports.cliVersion = '>=3.2.1';

exports.init = function (logger, config, cli) {
	let frameworkManager = new FrameworkManager(cli, config, logger);
	frameworkManager.initialize();
};

/**
 * Manages all available frameworks either from modules or the project itself.
 *
 * Scans for available frameworks and inspects them to get the required info to
 * properly integrate them into the Xcode project.
 */
class FrameworkManager {

	/**
	 * Constructs a new framework manager.
	 *
	 * @param {Object} cli - CLI instance
	 * @param {Object} config - Project configuration
	 * @param {Object} logger - Logger instance
	 * @access public
	 */
	constructor(cli, config, logger) {
		this._cli = cli;
		this._config = config;
		this._logger = logger;
		this._builder = null;
		this._frameworks = new Map();
	}

	/**
	 * Initializes the framework manager by hooking into the required build steps.
	 *
	 * @access public
	 */
	initialize() {
		this._cli.on('build.pre.compile', {
			priority: 1200,
			post: async (builder, callback) => {
				try {
					this._logger.trace('Starting third-party framework detection');
					this._builder = builder;
					await this.detectFrameworks();
				} catch (err) {
					if (this._cli.argv.platform === 'ios') {
						this._logger.error(err.stack);
					}
					callback(err);
					return;
				}
				callback();
			}
		});

		this._cli.on('build.ios.xcodeproject', {
			pre: this.addFrameworksToXcodeProject.bind(this)
		});
	}

	/**
	 * Detects all available frameworks.
	 *
	 * @return {Promise<void>}
	 * @access private
	 */
	async detectFrameworks() {
		const frameworkPaths = await this.findFrameworkPaths();
		if (frameworkPaths.length === 0) {
			return;
		}

		const incrementalDirectory = path.join(this._builder.projectDir, 'build', 'incremental');
		const outputDirectory = path.join(this._builder.projectDir, 'build', 'inspectFrameworks');
		const task = new InspectFrameworksTask({
			name: 'ti:inspectFrameworks',
			logger: this._logger,
			incrementalDirectory
		});
		task.outputDirectory = outputDirectory;
		frameworkPaths.forEach(frameworkPath => {
			task.addFrameworkPath(frameworkPath);
		});
		task.postTaskRun = () => {
			this._frameworks = task.frameworks;

			// Convert the internal ES6 map to an object to avoid ES6 in the builder
			const frameworkObject = {};
			this._frameworks.forEach(frameworkInfo => {
				frameworkObject[frameworkInfo.name] = {
					name: frameworkInfo.name,
					path: frameworkInfo.path,
					type: frameworkInfo.type,
					architectures: Array.from(frameworkInfo.architectures)
				};
			});
			this._builder.frameworks = frameworkObject;
		};
		return task.run();
	}

	/**
	 * Finds any .framework/.xcframework directories inside the project and its modules.
	 *
	 * @return {Promise<string[]>} Promise resolving to array of available third-party framework directory paths
	 * @access private
	 */
	async findFrameworkPaths() {
		const pathsToScan = [
			path.join(this._builder.projectDir, 'platform', 'ios'),
			path.join(this._builder.projectDir, 'platform', 'iphone')
		];
		for (let module of this._builder.modules) {
			pathsToScan.push(path.join(module.modulePath));
			pathsToScan.push(path.join(module.modulePath, 'platform'));
			pathsToScan.push(path.join(module.modulePath, 'Resources'));
		}

		const results = await Promise.all(pathsToScan.map(pathToScan => this.scanPathForFrameworks(pathToScan)));
		return results.filter(foundPath => foundPath).reduce((acc, value) => acc.concat(value), []);
	}

	/**
	 * Scans the given path for any .framework/.xcframework sub-directories.
	 *
	 * @param {String} frameworksPath - Path to scan for frameworks
	 * @return {Promise<string[]>}
	 * @access private
	 */
	async scanPathForFrameworks(frameworksPath) {
		let files = [];
		try {
			files = await fs.readdir(frameworksPath);
		} catch (err) {
			// ignore non-existing directories
			return;
		}

		this._logger.trace(`Scanning ${frameworksPath.cyan} for frameworks`);
		const foundFrameworkPaths = [];
		for (const filename of files) {
			const possibleFrameworkPath = path.join(frameworksPath, filename);
			if (frameworkPattern.test(possibleFrameworkPath)) {
				this._logger.trace(`  found ${path.relative(frameworksPath, possibleFrameworkPath)}`);
				foundFrameworkPaths.push(possibleFrameworkPath);
			}
		}

		return foundFrameworkPaths;
	}

	/**
	 * Adds all found framworks to the Xcode project.
	 *
	 * @param {Object} hookData - Data from the Xcode project hook
	 * @param {Function} callback - Callback function
	 * @return {undefined}
	 * @access private
	 */
	addFrameworksToXcodeProject(hookData, callback) {
		if (this._frameworks.size === 0) {
			return callback();
		}

		const xcodeProject = hookData.args[0];
		const frameworkIntegrator = new FrameworkIntegrator(xcodeProject, this._builder, this._logger);
		const frameworks = Array.from(this._frameworks.values())
			.sort((a, b) => a.name.localeCompare(b.name));
		for (const frameworkInfo of frameworks) {
			this._logger.trace(`Integrating ${frameworkInfo.type} framework ${frameworkInfo.name.green} into Xcode project.`);
			frameworkIntegrator.integrateFramework(frameworkInfo);
		}

		if (this.hasFrameworksWithFatBinary()) {
			this._logger.trace('Framework with fat binary present, integrating script to strip invalid architectures.');
			frameworkIntegrator.integrateStripFrameworksScript();
		}

		frameworkIntegrator.adjustRunpathSearchPath();

		callback();
	}

	/**
	 * Checks all found dynamic frameworks if any of them include both device and
	 * simulator architectures.
	 *
	 * XCFramework packages are excluded from this as they consist of seperate binaries
	 * for each arch instead of one fat binary.
	 *
	 * @return {Boolean} True if device and simulator archs were found, false otherwise
	 */
	hasFrameworksWithFatBinary() {
		let deviceArchitectures = new Set([ 'armv7', 'arm64' ]);
		let simulatorArchitectures = new Set([ 'i386', 'x86_64' ]);

		for (let frameworkInfo of this._frameworks.values()) {
			if (frameworkInfo.type !== FRAMEWORK_TYPE_DYNAMIC || frameworkInfo.isBinaryFramework) {
				continue;
			}

			let hasDeviceArchitectures = false;
			let hasSimulatorArchitectures = false;

			for (let deviceArchitecture of deviceArchitectures) {
				if (frameworkInfo.architectures.has(deviceArchitecture)) {
					hasDeviceArchitectures = true;
					break;
				}
			}

			for (let simulatorArchitecture of simulatorArchitectures) {
				if (frameworkInfo.architectures.has(simulatorArchitecture)) {
					hasSimulatorArchitectures = true;
					break;
				}
			}

			if (hasDeviceArchitectures && hasSimulatorArchitectures) {
				return true;
			}
		}

		return false;
	}
}

/**
 * Task that takes a set of paths and inspects the frameworks that are found
 * there.
 */
class InspectFrameworksTask extends IncrementalFileTask {

	/**
	 * Constructs a new frameworks insepcation task
	 *
	 * @param {Object} taskInfo - Task info object
	 * @access public
	 */
	constructor(taskInfo) {
		super(taskInfo);

		this._frameworkPaths = new Set();
		this._frameworks = new Map();
		this._outputDirectory = null;
		this._metadataPathAndFilename = null;
	}

	/**
	 * @inheritdoc
	 */
	get incrementalOutputs() {
		return [ this._outputDirectory ];
	}

	get outputDirectory() {
		return this._outputDirectory;
	}

	/**
	 * Sets the output directory where this task will write the framework metadata
	 *
	 * @param {String} outputPath - Full path to the output directory
	 * @access public
	 */
	set outputDirectory(outputPath) {
		this._outputDirectory = outputPath;
		this.registerOutputPath(outputPath);
		this._metadataPathAndFilename = path.join(this._outputDirectory, 'frameworks.json');
	}

	/**
	 * Returns a list with metadata of all recognized frameworks
	 *
	 * @return {Map.<String, FrameworkInfo>} Map of framework paths and the associated metadata
	 * @access public
	 */
	get frameworks() {
		return this._frameworks;
	}

	/**
	 * Adds a .framework/.xcframework folder so this task can inspect it to collect metadata
	 * about the framework.
	 *
	 * @param {String} frameworkPath - Path to the .framework/,xcframework folder to inspect
	 * @access public
	 */
	addFrameworkPath(frameworkPath) {
		if (this._frameworkPaths.has(frameworkPath)) {
			return;
		}

		this._frameworkPaths.add(frameworkPath);
		this.addInputDirectory(frameworkPath);
	}

	/**
	 * Does a full task run by inspecting all available framework paths
	 *
	 * @return {Promise<void>}
	 * @access private
	 */
	async doFullTaskRun() {
		this._frameworks = new Map();
		await this.inspectFrameworks(this._frameworkPaths);
		return this.writeFrameworkMetadata();
	}

	/**
	 * Does an incremental task run by only scanning changed framework folders
	 * and removing deleted frameworks from the metadata object
	 *
	 * @param {Map.<String, String>} changedFiles - Map of changed files and their status (created, changed or deleted)
	 * @return {Promise<void>}
	 * @access private
	 */
	async doIncrementalTaskRun(changedFiles) {
		const loaded = await this.loadFrameworkMetadata();
		if (!loaded) {
			return this.doFullTaskRun();
		}

		this._frameworks.forEach(frameworkInfo => {
			if (!fs.existsSync(frameworkInfo.path)) {
				this.logger.trace(`Framework at ${frameworkInfo.path} deleted, removing metadata`);
				this._frameworks.delete(frameworkInfo.name);
				return;
			}

			// Remove any frameworks from deactivated modules
			if (!this._frameworkPaths.has(frameworkInfo.path)) {
				this.logger.trace(`Framework at ${frameworkInfo.path} no longer in search path, removing metadata`);
				this._frameworks.delete(frameworkInfo.name);
				return;
			}
		});

		const changedFrameworks = new Set();
		changedFiles.forEach((fileStatus, pathAndFilename) => {
			if (fileStatus === 'created' || fileStatus === 'changed') {
				const packageExtension = pathAndFilename.includes('.xcframework') ? '.xcframework' : '.framework';
				const frameworkPath = pathAndFilename.substring(0, pathAndFilename.indexOf(packageExtension) + packageExtension.length);
				if (!changedFrameworks.has(frameworkPath)) {
					this.logger.trace(`Framework at ${frameworkPath} changed, regenerating metadata`);
					changedFrameworks.add(frameworkPath);
				}
			}
		});

		await this.inspectFrameworks(changedFrameworks);
		return this.writeFrameworkMetadata();
	}

	/**
	 * @return {Promise<void>}
	 * @inheritdoc
	 */
	async loadResultAndSkip() {
		const loaded = await this.loadFrameworkMetadata();
		if (!loaded) {
			return this.doFullTaskRun();
		}
	}

	/**
	 * Loads stored metadata from disk and recreates the {@link FrameworkInfo}
	 * objects.
	 *
	 * @return {Promise<Boolean>} True if the metadata was sucessfully loaded, false if not
	 * @access private
	 */
	async loadFrameworkMetadata() {
		try {
			const metadata = await fs.readJSON(this._metadataPathAndFilename);
			for (const frameworkPath of Object.keys(metadata)) {
				const frameworkMetadata = metadata[frameworkPath];
				frameworkMetadata.architectures = new Set(frameworkMetadata.architectures);
				this._frameworks.set(frameworkMetadata.name, new FrameworkInfo(frameworkMetadata));
			}
			return true;
		} catch (e) {
			return false;
		}
	}

	/**
	 * Saves the internal matadata object to disk for reuse on subsequent builds.
	 *
	 * @return {Promise<void>}
	 * @access private
	 */
	async writeFrameworkMetadata() {
		const metadataObject = {};
		for (const frameworkInfo of this._frameworks.values()) {
			metadataObject[frameworkInfo.path] = {
				name: frameworkInfo.name,
				path: frameworkInfo.path,
				type: frameworkInfo.type,
				architectures: Array.from(frameworkInfo.architectures)
			};
		}
		await fs.ensureDir(this._outputDirectory);
		return fs.writeJSON(this._metadataPathAndFilename, metadataObject);
	}

	/**
	 * Inspects each framework for their type and supported architectures.
	 *
	 * @param {Set.<String>} frameworkPaths - List of framework paths to inspect
	 * @return {Promise}
	 * @access private
	 */
	inspectFrameworks(frameworkPaths) {
		const metadataPromises = [];
		const frameworkInspector = new FrameworkInspector(this._logger);
		for (const frameworkPath of frameworkPaths) {
			const metadataPromise = frameworkInspector.inspect(frameworkPath).then(frameworkInfo => {
				if (this._frameworks.has(frameworkInfo.name)) {
					const existingFrameworkInfo = this._frameworks.get(frameworkInfo.name);
					if (existingFrameworkInfo.path === frameworkInfo.path) {
						this._frameworks.set(frameworkInfo.name, frameworkInfo);
						return;
					}

					this.logger.error(`Duplicate framework ${frameworkInfo.name} detected at these paths:`);
					this.logger.error('');
					this.logger.error(`  ${existingFrameworkInfo.path}`);
					this.logger.error(`  ${frameworkInfo.path}`);
					this.logger.error('');
					this.logger.error('Please resolve this conflict by choosing one of the above frameworks that you want to keep and remove the other before continuing.');

					throw new Error(`Duplicate framework ${frameworkInfo.name} detected.`);
				}
				this._frameworks.set(frameworkInfo.name, frameworkInfo);

				return;
			});
			metadataPromises.push(metadataPromise);
		}

		return Promise.all(metadataPromises);
	}

}

/**
 * Integrates frameworks into a Xcode project by adding the required build phases
 * and adjusting build settings
 */
class FrameworkIntegrator {

	/**
	 * Constructs a new framework integrator
	 *
	 * @param {Object} xcodeProject Parsed Xcode project from node-xcode
	 * @param {Object} builder iOS builder instance
	 * @param {Object} logger Appc logger instance
	 * @access public
	 */
	constructor(xcodeProject, builder, logger) {
		this._builder = builder;
		this._logger = logger;

		this._xcodeProject = xcodeProject;
		this._xobjs = xcodeProject.hash.project.objects;
		this._projectUuid = xcodeProject.hash.project.rootObject;
		this._pbxProject = this._xobjs.PBXProject[this._projectUuid];
		this._mainTargetUuid = this._pbxProject.targets.filter((target) => {
			return target.comment.replace(/^"/, '').replace(/"$/, '') === this._builder.tiapp.name;
		})[0].value;
		this._mainTarget = this._xobjs.PBXNativeTarget[this._mainTargetUuid];
		this._mainGroupChildren = this._xobjs.PBXGroup[this._pbxProject.mainGroup].children;
		this._frameworksGroup = this._xobjs.PBXGroup[this._mainGroupChildren.filter((child) => {
			return child.comment === 'Frameworks';
		})[0].value];
		this._frameworksBuildPhase = this._xobjs.PBXFrameworksBuildPhase[this._mainTarget.buildPhases.filter((phase) => {
			return this._xobjs.PBXFrameworksBuildPhase[phase.value];
		})[0].value];

		this._frameworkSearchPaths = new Map();
		let buildConfigurations = this._xobjs.XCConfigurationList[this._mainTarget.buildConfigurationList].buildConfigurations;
		for (let buildConf of buildConfigurations) {
			if (!this._frameworkSearchPaths.has(buildConf.value)) {
				this._frameworkSearchPaths.set(buildConf.value, new Set());
			}

			let buildSettings = this._xobjs.XCBuildConfiguration[buildConf.value].buildSettings;
			if (Array.isArray(buildSettings.FRAMEWORK_SEARCH_PATHS)) {
				let frameworkSearchPaths = this._frameworkSearchPaths.get(buildConf.value);
				for (let frameworkSearchPath of buildSettings.FRAMEWORK_SEARCH_PATHS) {
					let cleanFrameworkSearchPath = frameworkSearchPath.replace('"', '');
					if (!frameworkSearchPaths.has(cleanFrameworkSearchPath)) {
						frameworkSearchPaths.add(cleanFrameworkSearchPath);
					}
				}
			}
		}
	}

	/**
	 * Integrates a frameworks into the Xcode project by adding the required
	 * build phases and adjusting the framework search path
	 *
	 * @param {FrameworkInfo} frameworkInfo - Framework metadata
	 * @access public
	 */
	integrateFramework(frameworkInfo) {
		const fileRefUuid = this.addFrameworkFileReference(frameworkInfo);
		this.addLinkFrameworkBuildPhase(frameworkInfo, fileRefUuid);
		if (frameworkInfo.type === FRAMEWORK_TYPE_DYNAMIC) {
			this.addEmbedFrameworkBuildPhase(frameworkInfo, fileRefUuid);
		}
		// Xcode has a special build phase step that handles XCFrameworks and
		// extracts the correct .framework for an architecture. This works without
		// consulting FRAMEWORK_SEARCH_PATHS so we don't need to modify it.
		if (!frameworkInfo.isBinaryFramework) {
			this.addFrameworkSearchPath(frameworkInfo.searchPath);
		}
	}

	/**
	 * Add the framework as a new file reference to the Xcode project.
	 *
	 * @param {FrameworkInfo} frameworkInfo - Framework metadata
	 * @return {String} Uuid of the created file reference
	 * @access private
	 */
	addFrameworkFileReference(frameworkInfo) {
		const frameworkPackageName = frameworkInfo.packageName;
		const fileRefUuid = this._builder.generateXcodeUuid();
		this._xobjs.PBXFileReference[fileRefUuid] = {
			isa: 'PBXFileReference',
			lastKnownFileType: frameworkInfo.wrapperName,
			name: `"${frameworkPackageName}"`,
			path: `"${frameworkInfo.path}"`,
			sourceTree: '"<absolute>"'
		};
		this._xobjs.PBXFileReference[fileRefUuid + '_comment'] = frameworkPackageName;
		this._frameworksGroup.children.push({
			value: fileRefUuid,
			comment: frameworkPackageName
		});

		return fileRefUuid;
	}

	/**
	 * Adds the framework to the project's link frameworks build phase.
	 *
	 * @param {FrameworkInfo} frameworkInfo - Framework metadata
	 * @param {String} fileRefUuid - Uuid of the frameworks file reference inside the Xcode project
	 * @access private
	 */
	addLinkFrameworkBuildPhase(frameworkInfo, fileRefUuid) {
		const frameworkPackageName = frameworkInfo.packageName;
		const buildFileUuid = this._builder.generateXcodeUuid();
		const buildFile = {
			isa: 'PBXBuildFile',
			fileRef: fileRefUuid,
			fileRef_comment: frameworkPackageName
		};
		if (frameworkInfo.platformFilter) {
			buildFile.platformFilter = frameworkInfo.platformFilter;
		}
		this._xobjs.PBXBuildFile[buildFileUuid] = buildFile;
		this._xobjs.PBXBuildFile[buildFileUuid + '_comment'] = frameworkPackageName + ' in Frameworks';
		this._frameworksBuildPhase.files.push({
			value: buildFileUuid,
			comment: frameworkPackageName + ' in Frameworks'
		});
	}

	/**
	 * Adds the frameworks to the project's embed frameworks build phase
	 *
	 * @param {FrameworkInfo} frameworkInfo - Framework metadata
	 * @param {String} fileRefUuid - Uuid of the frameworks file reference inside the Xcode project
	 * @access private
	 */
	addEmbedFrameworkBuildPhase(frameworkInfo, fileRefUuid) {
		const frameworkPackageName = frameworkInfo.packageName;
		const embeddedBuildFileUuid = this._builder.generateXcodeUuid();
		const buildFile = {
			isa: 'PBXBuildFile',
			fileRef: fileRefUuid,
			fileRef_comment: frameworkPackageName,
			settings: { ATTRIBUTES: [ 'CodeSignOnCopy', 'RemoveHeadersOnCopy' ] }
		};
		if (frameworkInfo.platformFilter) {
			buildFile.platformFilter = frameworkInfo.platformFilter;
		}
		this._xobjs.PBXBuildFile[embeddedBuildFileUuid] = buildFile;
		this._xobjs.PBXBuildFile[embeddedBuildFileUuid + '_comment'] = frameworkPackageName + ' in Embed Frameworks';

		let embedFrameworksBuildPhase = null;
		for (let phase of this._mainTarget.buildPhases) {
			if (phase.comment === 'Embed Frameworks') {
				embedFrameworksBuildPhase = this._xobjs.PBXCopyFilesBuildPhase[phase.value];
				break;
			}
		}
		if (embedFrameworksBuildPhase === null) {
			const embedFrameworksBuildPhaseUuid = this._builder.generateXcodeUuid();
			embedFrameworksBuildPhase = {
				isa: 'PBXCopyFilesBuildPhase',
				buildActionMask: 2147483647,
				dstPath: '""',
				dstSubfolderSpec: 10,
				files: [],
				name: '"Embed Frameworks"',
				runOnlyForDeploymentPostprocessing: 0
			};
			this._xobjs.PBXCopyFilesBuildPhase = this._xobjs.PBXCopyFilesBuildPhase || {};
			this._xobjs.PBXCopyFilesBuildPhase[embedFrameworksBuildPhaseUuid] = embedFrameworksBuildPhase;
			this._xobjs.PBXCopyFilesBuildPhase[embedFrameworksBuildPhaseUuid + '_comment'] = 'Embed Frameworks';
			this._mainTarget.buildPhases.push(embedFrameworksBuildPhaseUuid);
		}
		embedFrameworksBuildPhase.files.push({
			value: embeddedBuildFileUuid,
			comment: frameworkPackageName + ' in Embed Frameworks'
		});
	}

	/**
	 * Adds the given paths to the framework search paths build setting.
	 *
	 * @param {String} frameworkSearchPath - Path to add to the framework search paths
	 * @access private
	 */
	addFrameworkSearchPath(frameworkSearchPath) {
		const buildConfigurations = this._xobjs.XCConfigurationList[this._mainTarget.buildConfigurationList].buildConfigurations;
		for (let buildConf of buildConfigurations) {
			let frameworkSearchPaths = this._frameworkSearchPaths.get(buildConf.value);
			if (frameworkSearchPaths.has(frameworkSearchPath)) {
				continue;
			}

			let buildSettings = this._xobjs.XCBuildConfiguration[buildConf.value].buildSettings;
			buildSettings.FRAMEWORK_SEARCH_PATHS = buildSettings.FRAMEWORK_SEARCH_PATHS || [ '"$(inherited)"' ];
			buildSettings.FRAMEWORK_SEARCH_PATHS.push('"\\"' + frameworkSearchPath + '\\""');
			frameworkSearchPaths.add(frameworkSearchPath);
		}
	}

	/**
	 * Adjusts the LD_RUNPATH_SEARCH_PATHS build setting and adds the path for
	 * embedded frameworks.
	 *
	 * @access public
	 */
	adjustRunpathSearchPath() {
		const dynamicFrameworksSearchPath = '@executable_path/Frameworks';
		const buildConfigurations = this._xobjs.XCConfigurationList[this._mainTarget.buildConfigurationList].buildConfigurations;
		for (const buildConf of buildConfigurations) {
			const buildSettings = this._xobjs.XCBuildConfiguration[buildConf.value].buildSettings;
			let searchPaths = buildSettings.LD_RUNPATH_SEARCH_PATHS;
			if (searchPaths.indexOf('$(inherited)') === -1) {
				searchPaths += ' $(inherited)';
			}
			if (searchPaths.indexOf(dynamicFrameworksSearchPath) === -1) {
				searchPaths += ` ${dynamicFrameworksSearchPath}`;
			}
		}
	}

	/**
	 * Integrates a script to strip unused architectures from any used frameworks
	 *
	 * This is required for proper app store submission. We either use our bundled
	 * one from the platform template folder, or a user provided script with the
	 * name strip-frameworks.sh which can be placed under platform/ios.
	 *
	 * @access public
	 */
	integrateStripFrameworksScript() {
		let stripFrameworksScriptPath = null;
		const scriptFilename = 'strip-frameworks.sh';

		[ 'ios', 'iphone' ].some((platformName) => {
			let scriptPath = path.join(this._builder.projectDir, 'platform', platformName, scriptFilename);
			if (fs.existsSync(scriptPath)) {
				stripFrameworksScriptPath = scriptPath;
				this._logger.trace('Using custom user script at ' + stripFrameworksScriptPath.cyan);
				return true;
			}
			return false;
		});

		if (stripFrameworksScriptPath === null) {
			stripFrameworksScriptPath = path.join(this._builder.templatesDir, scriptFilename);
			this._logger.trace('Using bundled script at ' + stripFrameworksScriptPath.cyan);
		}

		let scriptPhaseUuid = this._builder.generateXcodeUuid();
		let shellPath = '/bin/sh';
		let shellScript = '/bin/bash "' + stripFrameworksScriptPath + '"';

		this._xobjs.PBXShellScriptBuildPhase = this._xobjs.PBXShellScriptBuildPhase || {};
		this._xobjs.PBXShellScriptBuildPhase[scriptPhaseUuid] = {
			isa: 'PBXShellScriptBuildPhase',
			buildActionMask: '2147483647',
			files: [],
			inputPaths: [],
			name: '"[Ti] Strip framework architectures"',
			outputPaths: [],
			runOnlyForDeploymentPostprocessing: 0,
			shellPath: shellPath,
			shellScript: JSON.stringify(shellScript)
		};
		this._mainTarget.buildPhases.push(scriptPhaseUuid);
	}
}

/**
 * Inspects a framework and collects data about it that is required to integrate
 * it with the Xcode project.
 *
 * The framework metadata that is collected here will also be added to the
 * builder at the end of this hook so it can then be used by other hooks.
 */
class FrameworkInspector {

	/**
	 * Constructs a new framework inspector.
	 *
	 * @param {Object} logger - Appc logger instance
	 * @access public
	 */
	constructor(logger) {
		this._logger = logger;
	}

	/**
	 * Inspects the framework under the given path and returns a new {@link FrameworkInfo}
	 * instance for it.
	 *
	 * @param {String} frameworkPath - Path to the framework to inspect
	 * @return {Promise<FrameworkInfo>}
	 * @access public
	 */
	async inspect(frameworkPath) {
		const packageExtension = path.extname(frameworkPath);
		const frameworkName = path.basename(frameworkPath, packageExtension);
		let frameworkInfo;
		let type;
		const meta = { name: frameworkName, path: frameworkPath };
		if (packageExtension === '.framework') {
			const binaryPathAndFilename = path.join(frameworkPath, frameworkName);
			const result = await this.detectBinaryTypeAndArchitectures(binaryPathAndFilename);
			meta.type = type = result.type;
			meta.architectures = result.architectures;
			frameworkInfo = new FrameworkInfo(meta);
		} else {
			const archs = new Set(); // FIXME: Bare architectures don't make sense, they ened to be combined with platform variant now!
			const supportedPlatforms = new Set();
			const xcFrameworkInfo = await parsePlist(path.join(frameworkPath, 'Info.plist'));
			for (const libInfo of xcFrameworkInfo.AvailableLibraries) {
				if (!type) {
					let libraryPath = path.join(frameworkPath, libInfo.LibraryIdentifier, libInfo.LibraryPath);
					if (libraryPath.endsWith('.framework')) {
						libraryPath = path.join(libraryPath, frameworkName);
					}
					if (await fs.pathExists(libraryPath)) {
						const result = await this.detectBinaryTypeAndArchitectures(libraryPath);
						type = result.type;
					} else {
						this._logger.warn(`Unable to find framework binary at path ${libraryPath} to detect architectures and dynamic/static.`);
					}
				}

				if (libInfo.SupportedPlatformVariant === MAC_CATALYST_PLATFORM_VARIANT) {
					supportedPlatforms.add(MAC_CATALYST_PLATFORM_VARIANT);
				} else {
					// ios device and sim
					libInfo.SupportedArchitectures.forEach(a => archs.add(a));
					supportedPlatforms.add(libInfo.SupportedPlatform); // should always be 'ios'
				}
			}
			meta.type = type;
			meta.architectures = archs;
			meta.supportedPlatforms = supportedPlatforms;
			frameworkInfo = new FrameworkInfo(meta);
		}
		const archs = Array.from(frameworkInfo.architectures.values()).join(', ');
		this._logger.debug(`Found framework ${frameworkName.green} (type: ${type}, archs: ${archs}) at ${frameworkPath.cyan}`);
		return frameworkInfo;
	}

	/**
	 * Detects the framwork's binary type (static or dynamic) and the included
	 * architectures.
	 *
	 * @param {String} binaryPathAndFilename - Path to a framwork's binary
	 * @return {Promise<object>}
	 * @access private
	 */
	detectBinaryTypeAndArchitectures(binaryPathAndFilename) {
		return new Promise((resolve, reject) => {
			exec('file -b "' + binaryPathAndFilename + '"', (error, stdout) => {
				if (error) {
					return reject(error);
				}

				const architectures = new Set();
				const architecturePattern = /architecture (\w+)/g;
				let architectureMatch = architecturePattern.exec(stdout);
				while (architectureMatch !== null) {
					architectures.add(architectureMatch[1]);
					architectureMatch = architecturePattern.exec(stdout);
				}

				let type;
				if (stdout.indexOf('dynamically linked shared library') !== -1) {
					type = FRAMEWORK_TYPE_DYNAMIC;
				} else {
					type = FRAMEWORK_TYPE_STATIC;
				}

				resolve({
					type,
					architectures
				});
			});
		});
	}
}

/**
 * @typedef FrameworkMetadata
 * @property {Object} name Framework metadata
 * @property {String} path Path to the framework
 * @property {String} type Framework's binary type (static or dynamic)
 * @property {Set<string>} architectures Set of supported architectures
 * @property {Set<string>} [supportedPlatforms] Set of supported platforms
 */

/**
 * Holds information about a framwork.
 */
class FrameworkInfo {

	/**
	 * Constructs a new framework info container
	 *
	 * @param {FrameworkMetadata} meta - Framework metadata
	 * @access public
	 */
	constructor(meta) {
		const {
			name,
			path,
			type,
			architectures,
			supportedPlatforms = new Set()
		} = meta;
		if (typeof name  !== 'string') {
			throw new TypeError('Framework name needs to be a string');
		}
		this.name = name;

		if (typeof path !== 'string') {
			throw new TypeError('Framework path needs to be a string');
		}
		this.path = path;

		if (type !== FRAMEWORK_TYPE_STATIC && type !== FRAMEWORK_TYPE_DYNAMIC) {
			throw new TypeError('Framework type needs to be either static or dynamic');
		}
		this.type = type;

		if (!(architectures instanceof Set)) {
			throw new TypeError('Framework architectures must be a set of valid architectures');
		}
		this.architectures = architectures;

		this.supportedPlatforms = supportedPlatforms;
	}

	/**
	 * Gets the framework search path that should be added to the xcode project
	 *
	 * @return {string}
	 */
	get searchPath() {
		// xcframework requires a recursive search path
		if (this.isBinaryFramework) {
			return `${path.dirname(this.path)}/**`;
		}
		return path.dirname(this.path);
	}

	/**
	 * Gets the name of the framework to stuff into xcode
	 *
	 * @return {string}
	 */
	get packageName() {
		return path.basename(this.path);
	}

	/**
	 * Name used in xcode build file reference to denote framework/xcframework
	 *
	 * @return {string}
	 */
	get wrapperName() {
		if (this.isBinaryFramework) {
			return 'wrapper.xcframework';
		}
		return 'wrapper.framework';
	}

	/**
	 * True if this is an XCFramework package that contains multiple
	 * binaries.
	 */
	get isBinaryFramework() {
		return this.path.endsWith('.xcframework');
	}

	/**
	 * Platform filter string used in xcode build file entries.
	 *
	 * Returns `ios` for frameworks that support iOS only, `maccatalyst` for
	 * MacOS only frameworks, and `undefined` if both platforms are supported
	 * or if there is no info about supported platforms.
	 *
	 * @return {string|undefined}
	 */
	get platformFilter() {
		if (this.supportedPlatforms.size === 0) {
			return undefined;
		}

		let supportsIos = this.supportedPlatforms.has(IOS_PLATFORM);
		let supportsMacOs = this.supportedPlatforms.has(MAC_CATALYST_PLATFORM_VARIANT);
		if (supportsIos && supportsMacOs) {
			return undefined;
		} else if (supportsIos) {
			return IOS_PLATFORM;
		} else if (supportsMacOs) {
			return MAC_CATALYST_PLATFORM_VARIANT;
		} else {
			return undefined;
		}
	}
}
