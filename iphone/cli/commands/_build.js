/**
 * iOS build command.
 *
 * @module cli/_build
 *
 * @copyright
 * Copyright (c) 2009-2020 by Appcelerator, Inc. All Rights Reserved.
 *
 * @license
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

'use strict';

const appc = require('node-appc'),
	async = require('async'),
	bufferEqual = require('buffer-equal'),
	Builder = require('node-titanium-sdk/lib/builder'),
	crypto = require('crypto'),
	cyan = require('colors').cyan,
	DOMParser = require('xmldom').DOMParser,
	ejs = require('ejs'),
	fields = require('fields'),
	fs = require('fs-extra'),
	ioslib = require('ioslib'),
	moment = require('moment'),
	path = require('path'),
	PNG = require('pngjs').PNG,
	CopyResourcesTask = require('../../../cli/lib/tasks/copy-resources-task'),
	ProcessJsTask = require('../../../cli/lib/tasks/process-js-task'),
	Color = require('../../../common/lib/color'),
	ProcessCSSTask = require('../../../cli/lib/tasks/process-css-task'),
	spawn = require('child_process').spawn, // eslint-disable-line security/detect-child-process
	ti = require('node-titanium-sdk'),
	util = require('util'),
	xcode = require('xcode'),
	xcodeParser = require('xcode/lib/parser/pbxproj'),
	i18n = appc.i18n(__dirname),
	__ = i18n.__,
	__n = i18n.__n,
	parallel = appc.async.parallel,
	series = appc.async.series,
	plist = require('simple-plist'),
	version = appc.version,
	merge = require('lodash.merge');
const platformsRegExp = new RegExp('^(' + ti.allPlatformNames.join('|') + ')$'); // eslint-disable-line security/detect-non-literal-regexp
const pemCertRegExp = /(^-----BEGIN CERTIFICATE-----)|(-----END CERTIFICATE-----.*$)|\n/g;

function iOSBuilder() {
	Builder.apply(this, arguments);

	// the minimum supported iOS SDK required when building
	this.minSupportedIosSdk = parseInt(version.parseMin(this.packageJson.vendorDependencies['ios sdk']));

	// the maximum supported iOS SDK required when building
	this.maxSupportedIosSdk = parseInt(version.parseMax(this.packageJson.vendorDependencies['ios sdk']));

	// object mapping the build-targets to their deploy-types
	this.deployTypes = {
		simulator: 'development',
		device: 'test',
		'dist-appstore': 'production',
		'dist-adhoc': 'production',
		macos: 'development',
		'dist-macappstore': 'production'
	};

	// list of available build-targets
	this.targets = [ 'simulator', 'device', 'dist-appstore', 'dist-adhoc', 'macos', 'dist-macappstore' ];

	// object of device families to map the --device-family parameter to the
	// native TARGETED_DEVICE_FAMILY build-setting
	this.deviceFamilies = {
		iphone: '1',
		ipad: '2',
		universal: '1,2',
		watch: '4'
	};

	// device-family set by the --device-family parameter
	this.deviceFamily = null;

	// blacklisted files and directories that throw an error when used and will
	// lead to a rejection when submitted
	this.blacklistDirectories = [
		'contents',
		'resources',
		'plugins',
		'watch',
		'_codesignature',
		'embedded.mobileprovision',
		'info.plist',
		'pkginfo',
		'assets.car',
		'modules',
		'LaunchScreen.storyboardc',
		'hyperloop'
	];

	// graylisted directories that throw a warning when used and may lead to a
	// rejection when submitted
	this.graylistDirectories = [
		'frameworks'
	];

	// templates-directory to render the ApplicationRouting.m into
	this.templatesDir = path.join(this.platformPath, 'templates', 'build');

	// object of all used Titanium symbols, used to determine preprocessor statements, e.g. USE_TI_UIWINDOW
	this.tiSymbols = {};

	// when true, uses the new build system (Xcode 9+)
	this.useNewBuildSystem = true;

	// when true, uses the AutoLayout engine
	this.useAutoLayout = false;

	// populated the first time getDeviceInfo() is called
	this.deviceInfoCache = null;

	// the selected provisioning profile info when doing a device, dist-appstore, or dist-adhoc build
	this.provisioningProfile = null;

	// cache of provisioning profiles
	this.provisioningProfileLookup = {};

	// list of all extensions (including watch apps)
	this.extensions = [];

	// simulator handles; only used when --target is simulator
	this.simHandle = null;
	this.watchSimHandle = null;

	// the minimum iOS version the app can run on - determined by the
	// "min-ios-ver" from the tiapp or min version from the `ios sdk` in the
	// Titanium iOS package.json, but is overwritten if there's a watch app
	this.minIosVersion = null;

	// when true and building an app with a watch extension for the simulator and the --launch-watch-app
	// flag is passed in, then show the external display and launch the watch app
	this.hasWatchAppV2orNewer = false;

	// if this app has any watch apps, then we need to know the min watchOS version for one of them
	// so that we can select a watch simulator
	this.watchMinOSVersion = null;

	// the parsed build manifest from the previous build
	this.previousBuildManifest = {};

	// contains the current build's info
	this.currentBuildManifest = {
		files: {}
	};

	// when true, the entire build dir is nuked at the start of the build
	this.forceCleanBuild = false;

	// when true, calls xcodebuild
	this.forceRebuild = false;

	// a list of relative paths to js files that need to be encrypted
	// note: the filename will have all periods replaced with underscores
	// FIXME: Use a Map from original names -> encrypted names
	this.jsFilesToEncrypt = [];
	// a list of relative paths to js files that have been encrypted
	// note: this is the original filename used by our require _index_.json and referenced within the app
	this.jsFilesEncrypted = [];

	// set to true if any js files changed so that we can trigger encryption to run
	this.jsFilesChanged = false;

	// an array of products (Xcode targets) being built
	this.products = [];

	// when true and Apple Transport Security is manually enabled via custom Info.plist or
	// tiapp.xml <ios><plist> section, then injects appcelerator.com whitelisted
	//
	// we default to true, but if "ios.whitelist.appcelerator.com" tiapp.xml property is
	// set to false, then we'll force appcelerator.com to NOT be whitelisted
	this.whitelistAppceleratorDotCom = true;

	// launch screen storyboard settings
	this.enableLaunchScreenStoryboard = true;
	this.defaultLaunchScreenStoryboard = true;
	this.defaultBackgroundColor = null;

	// if the selected sim is 32-bit (iPhone 5 and older, iPad 4th gen or older), then the app
	// won't run, so we need to track the ONLY_ACTIVE_ARCH flag and disable it for 32-bit sims
	this.simOnlyActiveArch = null;

	// macros used as preprocessor in project.xcconfig file
	this.gccDefs = new Map();
	this.tiSymbolMacros = null;
}

util.inherits(iOSBuilder, Builder);

/**
 * Checks environment detection issues for a specific issue and if found,
 * displays error and exits build.
 *
 * @param {Array} issues - The array of environment issues.
 * @param {String} name - The issue id.
 * @access private
 */
iOSBuilder.prototype.assertIssue = function assertIssue(issues, name) {
	for (let i = 0; i < issues.length; i++) {
		if ((typeof name === 'string' && issues[i].id === name) || (typeof name === 'object' && name.test(issues[i].id))) {
			this.logger.banner();
			appc.string.wrap(issues[i].message, this.config.get('cli.width', 100)).split('\n').forEach(function (line) {
				this.logger.error(line.replace(/(__(.+?)__)/g, '$2'.bold));
			}, this);
			this.logger.log();
			process.exit(1);
		}
	}
};

/**
 * Retrieves the list of certificate information by name.
 *
 * @param {String} name - The cert name.
 * @param {String} [type] - The type of cert to scan (developer or distribution).
 * @returns {Array}
 * @access private
 */
iOSBuilder.prototype.findCertificates = function findCertificates(name, type) {
	const certs = [];
	/* eslint-disable max-depth */
	if (name && this.iosInfo) {
		for (const keychain of Object.keys(this.iosInfo.certs.keychains)) {
			const scopes = this.iosInfo.certs.keychains[keychain];
			const types = type ? [ type ] : Object.keys(scopes);
			for (const scope of types) {
				if (scopes[scope]) {
					for (const cert of scopes[scope]) {
						if (cert.name === name || cert.fullname === name) {
							certs.push(cert);
						}
					}
				}
			}
		}
	}

	return certs;
};

/**
 * Determines the valid list of devices or simulators. This is used for prompting
 * and validation.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.getDeviceInfo = function getDeviceInfo() {
	if (this.deviceInfoCache) {
		return this.deviceInfoCache;
	}

	const argv = this.cli.argv,
		deviceInfo = {
			devices: [],
			udids: {},
			maxName: 0,
			preferred: null
		};

	if (argv.target === 'device') {
		// build the list of devices
		this.iosInfo.devices.forEach(function (device) {
			device.name.length > deviceInfo.maxName && (deviceInfo.maxName = device.name.length);
			deviceInfo.devices.push({
				udid: device.udid,
				name: device.name,
				deviceClass: device.deviceClass,
				productVersion: device.productVersion
			});
			deviceInfo.udids[device.udid] = device;
		});

		if (this.config.get('ios.autoSelectDevice', true) && !argv['device-id']) {
			deviceInfo.preferred = deviceInfo.devices[0];
		}
	} else if (argv.target === 'simulator') {
		deviceInfo.devices = {};

		this.initTiappSettings();

		// build the list of simulators
		Object
			.keys(this.iosInfo.simulators.ios)
			.sort(function (v1, v2) {
				return appc.version.eq(v1, v2) ? 0 : appc.version.lt(v1, v2) ? -1 : 1;
			})
			.reverse()
			.forEach(function (ver) {
				if (appc.version.lt(ver, this.minIosVersion)) {
					// sim too old
					return;
				}

				deviceInfo.devices[ver] || (deviceInfo.devices[ver] = []);
				this.iosInfo.simulators.ios[ver].forEach(function (sim) {
					// get the Xcode id for the version that supports the --ios-version
					// var xcodeId = argv['ios-version'] && this.iosInfo.iosSDKtoXcode[argv['ios-version']];
					// if ((argv['launch-watch-app'] || argv['launch-watch-app-only']) && (!Object.keys(sim.watchCompanion).length || (xcodeId && !sim.watchCompanion[xcodeId]))) {
					if ((argv['launch-watch-app'] || argv['launch-watch-app-only']) && !Object.keys(sim.watchCompanion).length) {
						// this sim doesn't support watches, skip
						return;
					}

					const watchUDID = argv['watch-device-id'];
					if (watchUDID) {
						const isValid = Object
							.keys(this.iosInfo.simulators.watchos)
							.some(ver => this.iosInfo.simulators.watchos[ver].some(wsim => wsim.udid === watchUDID), this);

						if (isValid) {
							if (!Object.keys(sim.watchCompanion).length) {
								return;
							}

							if (!Object.keys(sim.watchCompanion).some(xcodeId => sim.watchCompanion[xcodeId][watchUDID])) {
								return;
							}
						}
					}

					sim.name.length > deviceInfo.maxName && (deviceInfo.maxName = sim.name.length);

					deviceInfo.devices[ver].push({
						udid: sim.udid,
						name: sim.name,
						deviceClass: sim.family,
						model: sim.model,
						productVersion: ver
					});
					deviceInfo.udids[sim.udid] = sim;

					// see if we should prefer this simulator
					if (this.config.get('ios.autoSelectDevice', true) && argv['ios-version'] && !argv['device-id']) {
						deviceInfo.preferred = deviceInfo.devices[argv['ios-version']] && deviceInfo.devices[argv['ios-version']][0];
					}
				}, this);
			}, this);
	} else if (argv.target === 'macos') {
		deviceInfo.devices = {};
	}
	return this.deviceInfoCache = deviceInfo;
};

/**
 * Returns iOS build-specific configuration options.
 *
 * @param {Object} logger - The logger instance
 * @param {Object} config - The CLI config
 * @param {Object} cli - The CLI instance
 *
 * @returns {Function|undefined} A function that returns the config info or undefined
 */
iOSBuilder.prototype.config = function config(logger, config, cli) {
	Builder.prototype.config.apply(this, arguments);

	const _t = this;

	// we hook into the pre-validate event so that we can stop the build before
	// prompting if we know the build is going to fail.
	cli.on('cli:pre-validate', function (obj, callback) {
		if (cli.argv.platform && !/^(ios|iphone|ipad)$/i.test(cli.argv.platform)) {
			return callback();
		}

		// check that the iOS environment is found and sane
		this.assertIssue(this.iosInfo.issues, 'IOS_XCODE_NOT_INSTALLED');
		this.assertIssue(this.iosInfo.issues, 'IOS_NO_SUPPORTED_XCODE_FOUND');
		this.assertIssue(this.iosInfo.issues, 'IOS_NO_IOS_SDKS');
		this.assertIssue(this.iosInfo.issues, 'IOS_NO_IOS_SIMS');

		callback();
	}.bind(this));

	return function (done) {
		ioslib.detect({
			// env
			xcodeSelect:       config.get('osx.executables.xcodeSelect'),
			security:          config.get('osx.executables.security'),
			// provisioning
			profileDir:        config.get('ios.profileDir'),
			// xcode
			searchPath:        config.get('paths.xcode'),
			minIosVersion:     this.packageJson.minIosVersion,
			supportedVersions: this.packageJson.vendorDependencies.xcode
		}, function (err, iosInfo) {
			if (err) {
				// this is bad and probably because we don't have a compatible
				// node-ios-device binary for the current version of node
				//
				// ideally we'd failout, but we can't... the Titanium CLI doesn't
				// allow the config() call to return an error. my bad design. :(
				iosInfo = {
					certs: {
						keychains: {}
					},
					devices: [],
					issues: [],
					simulators: {
						ios: []
					},
					xcode: {}
				};
			}

			this.iosInfo = iosInfo;

			// add itunes sync
			iosInfo.devices.push({
				udid: 'itunes',
				name: 'iTunes Sync'
			});

			// we have more than 1 device plus itunes, so we should show 'all'
			if (iosInfo.devices.length > 2) {
				iosInfo.devices.push({
					udid: 'all',
					name: 'All Devices'
				});
			}

			// get the all installed iOS SDKs and Simulators across all Xcode versions
			const allSdkVersions = {},
				sdkVersions = {},
				simVersions = {};
			Object.keys(iosInfo.xcode).forEach(function (ver) {
				if (iosInfo.xcode[ver].supported) {
					iosInfo.xcode[ver].sdks.forEach(function (sdk) {
						allSdkVersions[sdk] = 1;
						if (version.gte(sdk, this.minSupportedIosSdk)) {
							sdkVersions[sdk] = 1;
						}
					}, this);
					iosInfo.xcode[ver].sims.forEach(function (sim) {
						simVersions[sim] = 1;
					});
				}
			}, this);
			this.iosAllSdkVersions = version.sort(Object.keys(allSdkVersions));
			this.iosSdkVersions = version.sort(Object.keys(sdkVersions));

			cli.createHook('build.ios.config', function (callback) {
				callback(null, {
					flags: {
						'force-copy': {
							desc: __('forces files to be copied instead of symlinked for %s builds only', 'simulator'.cyan)
						},
						'hide-error-controller': {
							hidden: true
						},
						'launch-watch-app': {
							desc: __('for %s builds, after installing an app with a watch extention, launch the watch app and the main app', 'simulator'.cyan)
						},
						'launch-watch-app-only': {
							desc: __('for %s builds, after installing an app with a watch extention, launch the watch app instead of the main app', 'simulator'.cyan)
						},
						'sim-focus': {
							default: true,
							desc: __('focus the iOS Simulator')
						},
						xcode: {
							// DEPRECATED
							// secret flag to perform Xcode pre-compile build step
							callback: function (value) {
								if (value) {
									// we deprecated the --xcode flag which was passed in during the Xcode pre-compile phase
									logger.error(__('The generated Titanium Xcode project is too old.'));
									logger.error(__('Please clean and rebuild the project.'));
									process.exit(1);
								}
							},
							hidden: true
						}
					},
					options: {
						'build-type': {
							hidden: true
						},
						'debug-host': {
							hidden: true
						},
						'deploy-type':                this.configOptionDeployType(100),
						'device-id':                  this.configOptionDeviceID(210),
						'developer-name':             this.configOptionDeveloperName(170),
						'distribution-name':          this.configOptionDistributionName(180),
						'device-family':              this.configOptionDeviceFamily(120), // this MUST be processed before --device-id
						'ios-version':                this.configOptioniOSVersion(130),
						keychain:                   this.configOptionKeychain(),
						'launch-bundle-id':           this.configOptionLaunchBundleId(),
						'launch-url': {
							// url for the application to launch in mobile Safari, as soon as the app boots up
							hidden: true
						},
						'output-dir':                 this.configOptionOutputDir(200),
						'pp-uuid':                    this.configOptionPPuuid(190),
						'profiler-host': {
							hidden: true
						},
						target:                     this.configOptionTarget(110),
						'watch-app-name':             this.configOptionWatchAppName(212),
						'watch-device-id':            this.configOptionWatchDeviceId(215)
					}
				});
			}.bind(this))(function (err, result) {
				done(_t.conf = result);
			});
		}.bind(this)); // end of ioslib.detect()
	}.bind(this);
};

/**
 * Defines the --deploy-type option.
 *
 * @param {Integer} order - The order to apply to this option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptionDeployType = function configOptionDeployType(order) {
	return {
		abbr: 'D',
		desc: __('the type of deployment; only used when target is %s or %s', 'simulator'.cyan, 'device'.cyan),
		hint: __('type'),
		order: order,
		values: [ 'test', 'development' ]
	};
};

/**
 * Defines the --device-id option.
 *
 * @param {Integer} order - The order to apply to this option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptionDeviceID = function configOptionDeviceID(order) {
	return {
		abbr: 'C',
		desc: __('the udid of the iOS simulator or iOS device to install the application to; for %s builds %s',
			'device'.cyan, ('[' + 'itunes'.bold + ', <udid>, all]').grey),
		hint: __('udid'),
		order: order,
		helpNoPrompt: function (logger, msg) {
			// if prompting is disabled and there's a problem, then help will use this function to display details
			logger.error(msg);
			const info = this.getDeviceInfo();
			if (info.devices) {
				if (this.cli.argv.target === 'device') {
					logger.log('\n' + __('Available iOS Devices:'));
					info.devices.forEach(function (sim) {
						logger.log('  ' + (info.devices.length > 1 ? appc.string.rpad(sim.udid, 40) : sim.udid).cyan + '  ' + sim.name);
					});
					logger.log();
				} else {
					logger.log('\n' + __('Available iOS Simulators:'));
					Object.keys(info.devices).forEach(function (ver) {
						logger.log(String(ver).grey);
						info.devices[ver].forEach(function (sim) {
							logger.log('  ' + sim.udid.cyan + '  ' + sim.name);
						});
						logger.log();
					});
				}
			}
		}.bind(this),
		prompt: function (callback) {
			var info = this.getDeviceInfo();
			if (info.preferred) {
				this.cli.argv['device-id'] = info.preferred.udid;
				return callback();
			}

			let options = {},
				maxName = 0,
				maxDesc = 0;

			// build a filtered list of simulators based on any legacy options/flags
			if (Array.isArray(info.devices)) {
				options = info.devices;
				info.devices.forEach(function (d) {
					if (d.name.length > maxName) {
						maxName = d.name.length;
					}
					const s = d.deviceClass ? (d.deviceClass + ' (' + d.productVersion + ')') : '';
					if (s.length > maxDesc) {
						maxDesc = s.length;
					}
				});
			} else {
				Object.keys(info.devices).forEach(function (sdk) {
					info.devices[sdk].forEach(function (sim) {
						options[sdk] || (options[sdk] = []);
						options[sdk].push(sim);
						if (sim.name.length > maxName) {
							maxName = sim.name.length;
						}
					});
				});
			}

			const params = {
				formatters: {},
				default: '1', // just default to the first one, whatever that will be
				autoSelectOne: true,
				margin: '',
				optionLabel: 'name',
				optionValue: 'udid',
				numbered: true,
				relistOnError: true,
				complete: true,
				suggest: true,
				options: options
			};

			if (this.cli.argv.target === 'device') {
				// device specific settings
				params.title = __('Which device do you want to install to?');
				params.promptLabel = __('Select an device by number or name');
				params.formatters.option = function (opt, idx, num) {
					return '  ' + num + [
						appc.string.rpad(opt.name, info.maxName).cyan,
						appc.string.rpad(opt.deviceClass ? opt.deviceClass + ' (' + opt.productVersion + ')' : '', maxDesc),
						opt.udid.grey
					].join('  ');
				};
			} else if (this.cli.argv.target === 'simulator') {
				// simulator specific settings
				params.title = __('Which simulator do you want to launch?');
				params.promptLabel = __('Select an simulator by number or name');
				params.formatters.option = function (opt, idx, num) {
					return '  ' + num + appc.string.rpad(opt.name, maxName).cyan + '  ' + opt.udid.grey;
				};
			}

			callback(fields.select(params));
		}.bind(this),
		required: true,
		validate: function (udid, callback) {
			// this function is called if they specify a --device-id and we need to check that it is valid
			if (typeof udid === 'boolean') {
				return callback(true);
			}

			if (this.cli.argv['build-only']) {
				return callback();
			}

			if (this.cli.argv.target === 'device' && udid === 'all') {
				// we let 'all' slide by
				return callback(null, udid);
			}

			const info = this.getDeviceInfo();
			if (info.udids[udid]) {
				callback(null, udid);
			} else {
				callback(new Error(this.cli.argv.target === 'device' ? __('Invalid iOS device "%s"', udid) : __('Invalid iOS simulator "%s"', udid)));
			}
		}.bind(this),
		verifyIfRequired: function (callback) {
			// this function is called by the CLI when the option is not specified and is required (i.e. missing).
			// the CLI will then double check that this option is still required by calling this function
			if (this.cli.argv['build-only']) {
				// not required if we're build only
				return callback();
			}

			if (this.cli.argv['device-id'] === undefined && this.config.get('ios.autoSelectDevice', true) && (this.cli.argv.target === 'simulator' || this.cli.argv.target === 'device')) {
				// --device-id not specified and we're not prompting, so pick a device later
				callback();
			} else {
				// yup, still required
				callback(true);
			}
		}.bind(this)
	};
};

/**
 * Defines the --developer-name option.
 *
 * @param {Integer} order - The order to apply to this option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptionDeveloperName = function configOptionDeveloperName(order) {
	const cli = this.cli,
		iosInfo = this.iosInfo,
		developerCertLookup = [];

	Object.keys(iosInfo.certs.keychains).forEach(function (keychain) {
		(iosInfo.certs.keychains[keychain].developer || []).forEach(function (d) {
			if (!d.invalid) {
				developerCertLookup.push({
					name: d.name,
					fullname: d.fullname
				});
			}
		});
	});

	return {
		abbr: 'V',
		default: this.config.get('ios.developerName'),
		desc: __('the iOS Developer Certificate to use; required when target is %s', 'device'.cyan),
		hint: 'name',
		order: order,
		prompt: function (callback) {
			var developerCerts = {},
				maxDevCertLen = 0;

			Object.keys(iosInfo.certs.keychains).forEach(function (keychain) {
				(iosInfo.certs.keychains[keychain].developer || []).forEach(function (d) {
					if (!d.invalid) {
						Array.isArray(developerCerts[keychain]) || (developerCerts[keychain] = []);
						developerCerts[keychain].push(d);
						maxDevCertLen = Math.max(d.fullname.length, maxDevCertLen);
					}
				});
			});

			// sort the certs
			Object.keys(developerCerts).forEach(function (keychain) {
				developerCerts[keychain] = developerCerts[keychain].sort(function (a, b) {
					return a.fullname.toLowerCase().localeCompare(b.fullname.toLowerCase());
				});
			});

			callback(fields.select({
				title: __('Which developer certificate would you like to use?'),
				promptLabel: __('Select a certificate by number or name'),
				formatters: {
					option: function (opt, idx, num) {
						var expires = moment(opt.after),
							day = expires.format('D'),
							hour = expires.format('h');
						return '  ' + num + appc.string.rpad(opt.fullname, maxDevCertLen + 1).cyan
							+ (opt.after ? (' (' + __('expires %s', expires.format('MMM') + ' '
							+ (day.length === 1 ? ' ' : '') + day + ', ' + expires.format('YYYY') + ' '
							+ (hour.length === 1 ? ' ' : '') + hour + ':' + expires.format('mm:ss a'))
							+ ')').grey : '');
					}
				},
				margin: '',
				optionLabel: 'fullname',
				optionValue: 'fullname',
				numbered: true,
				relistOnError: true,
				complete: true,
				suggest: false,
				autoSelectOne: true,
				options: developerCerts
			}));
		},
		validate: function (value, callback) {
			if (typeof value === 'boolean') {
				return callback(true);
			}
			if (cli.argv.target !== 'device') {
				return callback(null, value);
			}
			if (value) {
				// value can either be a fullname (Apple Development: Joe Bloggs (TEAMID)) or just name (Joe Bloggs (TEAMID)). We want to use fullname, so if we were provided
				// a name try to map it back to the correct format.
				const v = developerCertLookup.filter(cert => cert.name.toLowerCase() === value.toLowerCase() || cert.fullname.toLowerCase() === value.toLowerCase());

				if (v.length === 1) {
					return callback(null, v[0].fullname);
				}

				if (v.length > 1) {
					return callback(new Error(__('Unable to determine correct certificate from supplied value')));
				}
			}
			callback(new Error(__('Invalid developer certificate "%s"', value)));
		}
	};
};

/**
 * Defines the --distribution-name option.
 *
 * @param {Integer} order - The order to apply to this option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptionDistributionName = function configOptionDistributionName(order) {
	const cli = this.cli,
		iosInfo = this.iosInfo,
		distributionCertLookup = [];

	Object.keys(iosInfo.certs.keychains).forEach(function (keychain) {
		(iosInfo.certs.keychains[keychain].distribution || []).forEach(function (d) {
			if (!d.invalid) {
				distributionCertLookup.push({
					name: d.name,
					fullname: d.fullname
				});
			}
		});
	});

	return {
		abbr: 'R',
		default: this.config.get('ios.distributionName'),
		desc: __('the iOS Distribution Certificate to use; required when target is %s or %s', 'dist-appstore'.cyan, 'dist-adhoc'.cyan),
		hint: 'name',
		order: order,
		prompt: function (callback) {
			const distributionCerts = {};
			let maxDistCertLen = 0;

			Object.keys(iosInfo.certs.keychains).forEach(function (keychain) {
				(iosInfo.certs.keychains[keychain].distribution || []).forEach(function (d) {
					if (!d.invalid) {
						Array.isArray(distributionCerts[keychain]) || (distributionCerts[keychain] = []);
						distributionCerts[keychain].push(d);
						maxDistCertLen = Math.max(d.fullname.length, maxDistCertLen);
					}
				});
			});

			// sort the certs
			Object.keys(distributionCerts).forEach(function (keychain) {
				distributionCerts[keychain] = distributionCerts[keychain].sort(function (a, b) {
					return a.fullname.toLowerCase().localeCompare(b.fullname.toLowerCase());
				});
			});

			callback(fields.select({
				title: __('Which distribution certificate would you like to use?'),
				promptLabel: __('Select a certificate by number or name'),
				formatters: {
					option: function (opt, idx, num) {
						var expires = moment(opt.after),
							day = expires.format('D'),
							hour = expires.format('h');
						return '  ' + num + appc.string.rpad(opt.fullname, maxDistCertLen + 1).cyan
							+ (opt.after ? (' (' + __('expires %s', expires.format('MMM') + ' '
							+ (day.length === 1 ? ' ' : '') + day + ', ' + expires.format('YYYY') + ' '
							+ (hour.length === 1 ? ' ' : '') + hour + ':' + expires.format('mm:ss a'))
							+ ')').grey : '');
					}
				},
				margin: '',
				optionLabel: 'fullname',
				optionValue: 'fullname',
				numbered: true,
				relistOnError: true,
				complete: true,
				suggest: false,
				options: distributionCerts
			}));
		},
		validate: function (value, callback) {
			if (typeof value === 'boolean') {
				return callback(true);
			}
			if (cli.argv.target !== 'dist-appstore' && cli.argv.target !== 'dist-adhoc') {
				return callback(null, value);
			}
			if (value) {
				// value can either be a fullname (Apple Distribution: Joe Bloggs (TEAMID)) or just name (Joe Bloggs (TEAMID)). We want to use fullname, so if we were provided
				// a name try to map it back to the correct format.
				const v = distributionCertLookup.filter(cert => cert.name.toLowerCase() === value.toLowerCase() || cert.fullname.toLowerCase() === value.toLowerCase());

				if (v.length === 1) {
					return callback(null, v[0].fullname);
				}

				if (v.length > 1) {
					return callback(new Error(__('Unable to determine correct certificate from supplied value')));
				}
			}
			callback(new Error(__('Invalid distribution certificate "%s"', value)));
		}
	};
};

/**
 * Defines the --device-family option.
 *
 * @param {Integer} order - The order to apply to this option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptionDeviceFamily = function configOptionDeviceFamily(order) {
	return {
		abbr: 'F',
		desc: __('the device family to build for'),
		order: order,
		values: Object.keys(this.deviceFamilies).filter(function (f) { return f !== 'watch'; })
	};
};

/**
 * Defines the --ios-version option.
 *
 * @param {Integer} order - The order to apply to this option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptioniOSVersion = function configOptioniOSVersion(order) {
	const _t = this,
		logger = this.logger;

	return {
		abbr: 'I',
		callback: function (value) {
			try {
				if (value && _t.iosAllSdkVersions.indexOf(value) !== -1 && version.lt(value, _t.minSupportedIosSdk)) {
					logger.banner();
					logger.error(__('The specified iOS SDK version "%s" is not supported by Titanium %s', value, _t.titaniumSdkVersion) + '\n');
					if (_t.iosSdkVersions.length) {
						logger.log(__('Available supported iOS SDKs:'));
						_t.iosSdkVersions.forEach(function (ver) {
							logger.log('   ' + ver.cyan);
						});
						logger.log();
					}
					process.exit(1);
				}
			} catch (e) {
				// squelch and let the cli detect the bad version
			}
		},
		desc: __('iOS SDK version to build with'),
		order: order,
		prompt: function (callback) {
			callback(fields.select({
				title: __('Which iOS SDK version would you like to build with?'),
				promptLabel: __('Select an iOS SDK version by number or name'),
				margin: '',
				numbered: true,
				relistOnError: true,
				complete: true,
				suggest: false,
				options: _t.iosSdkVersions
			}));
		},
		values: _t.iosSdkVersions
	};
};

/**
 * Defines the --keychain option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptionKeychain = function configOptionKeychain() {
	return {
		abbr: 'K',
		desc: __('path to the distribution keychain to use instead of the system default; only used when target is %s, %s, or %s', 'device'.cyan, 'dist-appstore'.cyan, 'dist-adhoc'.cyan),
		hideValues: true,
		validate: function (value, callback) {
			value && typeof value !== 'string' && (value = null);
			if (value && !fs.existsSync(value)) {
				callback(new Error(__('Unable to find keychain: %s', value)));
			} else {
				callback(null, value);
			}
		}
	};
};

/**
 * Defines the --launch-bundle-id option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptionLaunchBundleId = function configOptionLaunchBundleId() {
	return {
		desc: __('after installing the app, launch an different app instead; only used when target is %s', 'simulator'.cyan),
		hint: __('id')
	};
};

/**
 * Defines the --output-dir option.
 *
 * @param {Integer} order - The order to apply to this option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptionOutputDir = function configOptionOutputDir(order) {
	const _t = this,
		cli = this.cli;

	function validate(outputDir, callback) {
		callback(outputDir || !_t.conf.options['output-dir'].required ? null : new Error(__('Invalid output directory')), outputDir);
	}

	return {
		abbr: 'O',
		desc: __('the output directory when using %s or %s', 'dist-appstore'.cyan, 'dist-adhoc'.cyan),
		hint: 'dir',
		order: order,
		prompt: function (callback) {
			callback(fields.file({
				promptLabel: __('Where would you like the output IPA file saved?'),
				default: cli.argv['project-dir'] && appc.fs.resolvePath(cli.argv['project-dir'], 'dist'),
				complete: true,
				showHidden: true,
				ignoreDirs: _t.ignoreDirs,
				ignoreFiles: /.*/,
				validate: validate
			}));
		},
		validate: validate
	};
};

/**
 * Defines the --pp-uuid option.
 *
 * @param {Integer} order - The order to apply to this option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptionPPuuid = function configOptionPPuuid(order) {
	const _t = this,
		cli = this.cli,
		iosInfo = this.iosInfo,
		logger = this.logger;

	function intersection (a, b) {
		return a.filter(function (p) {
			return (b.indexOf(p) !== -1);
		});
	}

	return {
		abbr: 'P',
		desc: __('the provisioning profile uuid; required when target is %s, %s, or %s', 'device'.cyan, 'dist-appstore'.cyan, 'dist-adhoc'.cyan),
		hint: 'uuid',
		order: order,
		prompt: function (callback) {
			const provisioningProfiles = {};
			const appId = cli.tiapp.id;
			const target = cli.argv.target;
			let maxAppId = 0;
			let pp;

			function prep(a, certs) {
				return a.filter(function (p) {
					if (!p.expired && !p.managed && (!certs || intersection(p.certs, certs).length > 0)) {
						const re = new RegExp(p.appId.replace(/\./g, '\\.').replace(/\*/g, '.*')); // eslint-disable-line security/detect-non-literal-regexp
						if (re.test(appId)) {
							let label = p.name;
							if (label.indexOf(p.appId) === -1) {
								label += ': ' + p.appId;
							}
							p.label = label;
							maxAppId = Math.max(p.label.length, maxAppId);
							return true;
						}
					}
					return false;
				}).sort(function (a, b) {
					return a.label.toLowerCase().localeCompare(b.label.toLowerCase());
				});
			}

			let certs;
			if (target === 'device') {
				certs = _t.findCertificates(cli.argv['developer-name'], 'developer');
			} else {
				certs = _t.findCertificates(cli.argv['distribution-name'], 'distribution');
			}

			const pems = certs.map(function (c) {
				return c.pem.replace(pemCertRegExp, '');
			});

			if (target === 'device') {
				if (iosInfo.provisioning.development.length) {
					pp = prep(iosInfo.provisioning.development, pems);
					if (pp.length) {
						provisioningProfiles[__('Available Development UUIDs:')] = pp;
					} else {
						if (certs.length > 0) {
							logger.error(__('Unable to find any non-expired development provisioning profiles that match the app id "%s" and the "%s" certificate.', appId, certs[0].name) + '\n');
						} else {
							logger.error(__('Unable to find any non-expired development provisioning profiles that match the app id "%s".', appId) + '\n');
						}
						logger.log(__('You will need to log in to %s with your Apple Developer account, then create, download, and install a profile.',
							'https://developer.apple.com/account/resources/certificates/list'.cyan) + '\n');
						process.exit(1);
					}
				} else {
					logger.error(__('Unable to find any development provisioning profiles') + '\n');
					logger.log(__('You will need to log in to %s with your Apple Developer account, then create, download, and install a profile.',
						'https://developer.apple.com/account/resources/certificates/list'.cyan) + '\n');
					process.exit(1);
				}

			} else if (target === 'dist-appstore') {
				if (iosInfo.provisioning.distribution.length) {
					pp = prep(iosInfo.provisioning.distribution, pems);
					if (pp.length) {
						provisioningProfiles[__('Available App Store Distribution UUIDs:')] = pp;
					} else {
						logger.error(__('Unable to find any non-expired App Store distribution provisioning profiles that match the app id "%s".', appId) + '\n');
						logger.log(__('You will need to log in to %s with your Apple Developer account, then create, download, and install a profile.',
							'https://developer.apple.com/account/resources/certificates/list'.cyan) + '\n');
						process.exit(1);
					}
				} else {
					logger.error(__('Unable to find any App Store distribution provisioning profiles'));
					logger.log(__('You will need to log in to %s with your Apple Developer account, then create, download, and install a profile.',
						'https://developer.apple.com/account/resources/certificates/list'.cyan) + '\n');
					process.exit(1);
				}

			} else if (target === 'dist-adhoc') {
				if (iosInfo.provisioning.adhoc.length || iosInfo.provisioning.enterprise.length) {
					pp = prep(iosInfo.provisioning.adhoc, pems);
					let valid = pp.length;
					if (pp.length) {
						provisioningProfiles[__('Available Ad Hoc UUIDs:')] = pp;
					}

					pp = prep(iosInfo.provisioning.enterprise);
					valid += pp.length;
					if (pp.length) {
						provisioningProfiles[__('Available Enterprise Ad Hoc UUIDs:')] = pp;
					}

					if (!valid) {
						logger.error(__('Unable to find any non-expired Ad Hoc or Enterprise Ad Hoc provisioning profiles that match the app id "%s".', appId) + '\n');
						logger.log(__('You will need to log in to %s with your Apple Developer account, then create, download, and install a profile.',
							'https://developer.apple.com/account/resources/certificates/list'.cyan) + '\n');
						process.exit(1);
					}
				} else {
					logger.error(__('Unable to find any Ad Hoc or Enterprise Ad Hoc provisioning profiles'));
					logger.log(__('You will need to log in to %s with your Apple Developer account, then create, download, and install a profile.',
						'https://developer.apple.com/account/resources/certificates/list'.cyan) + '\n');
					process.exit(1);
				}
			}

			callback(fields.select({
				title: __('Which provisioning profile would you like to use?'),
				promptLabel: __('Select a provisioning profile UUID by number or name'),
				formatters: {
					option: function (opt, idx, num) {
						const expires = opt.expirationDate && moment(opt.expirationDate),
							day = expires && expires.format('D'),
							hour = expires && expires.format('h');
						return '  ' + num + String(opt.uuid).cyan + ' '
							+ appc.string.rpad(opt.label, maxAppId + 1)
							+ (opt.expirationDate ? (' (' + __('expires %s', expires.format('MMM') + ' '
							+ (day.length === 1 ? ' ' : '') + day + ', ' + expires.format('YYYY') + ' '
							+ (hour.length === 1 ? ' ' : '') + hour + ':' + expires.format('mm:ss a'))
							+ ')').grey : '');
					}
				},
				margin: '',
				optionLabel: 'name',
				optionValue: 'uuid',
				numbered: true,
				relistOnError: true,
				complete: true,
				suggest: false,
				autoSelectOne: true,
				options: provisioningProfiles
			}));
		},
		validate: function (value, callback) {
			const target = cli.argv.target;

			if (target === 'simulator'  || target === 'macos') {
				return callback(null, value);
			}

			if (value) {
				const p = _t.provisioningProfileLookup[value.toLowerCase()];
				if (!p) {
					return callback(new Error(__('Invalid provisioning profile UUID "%s"', value)));
				}
				if (p.managed) {
					return callback(new Error(__('Specified provisioning profile UUID "%s" is managed and not supported', value)));
				}
				if (p.expired) {
					return callback(new Error(__('Specified provisioning profile UUID "%s" is expired', value)));
				}

				let certs;
				if (target === 'device') {
					certs = _t.findCertificates(cli.argv['developer-name'], 'developer');
				} else {
					certs = _t.findCertificates(cli.argv['distribution-name'], 'distribution');
				}

				const pems = certs.map(function (c) {
					return c.pem.replace(pemCertRegExp, '');
				});

				if (certs.length > 0 && intersection(p.certs, pems).length === 0) {
					return callback(new Error(__('Specified provisioning profile UUID "%s" does not include the "%s" certificate', value, certs[0].name)));
				}

				return callback(null, p.uuid);
			}

			callback(true);
		}
	};
};

/**
 * Defines the --target option.
 *
 * @param {Integer} order - The order to apply to this option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptionTarget = function configOptionTarget(order) {
	const _t = this,
		iosInfo = this.iosInfo;

	return {
		abbr: 'T',
		callback: function (value) {
			if (value !== 'simulator' && value !== 'macos') {
				_t.assertIssue(iosInfo.issues, 'IOS_NO_KEYCHAINS_FOUND');
				_t.assertIssue(iosInfo.issues, 'IOS_NO_WWDR_CERT_FOUND');
			}

			// as soon as we know the target, toggle required options for validation
			switch (value) {
				case 'device':
					_t.assertIssue(iosInfo.issues, 'IOS_NO_VALID_DEV_CERTS_FOUND');
					_t.assertIssue(iosInfo.issues, 'IOS_NO_VALID_DEVELOPMENT_PROVISIONING_PROFILES');
					iosInfo.provisioning.development.forEach(p => {
						_t.provisioningProfileLookup[p.uuid.toLowerCase()] = p;
					});
					_t.conf.options['developer-name'].required = true;
					_t.conf.options['pp-uuid'].required = true;
					break;

				case 'dist-adhoc':
					_t.assertIssue(iosInfo.issues, 'IOS_NO_VALID_DIST_CERTS_FOUND');
					// TODO: assert there is at least one distribution or adhoc provisioning profile

					_t.conf.options['output-dir'].required = true;
					_t.conf.options['deploy-type'].values = [ 'production' ];
					_t.conf.options['device-id'].required = false;
					_t.conf.options['distribution-name'].required = true;
					_t.conf.options['pp-uuid'].required = true;

					iosInfo.provisioning.adhoc.forEach(p => {
						_t.provisioningProfileLookup[p.uuid.toLowerCase()] = p;
					});
					iosInfo.provisioning.enterprise.forEach(p => {
						_t.provisioningProfileLookup[p.uuid.toLowerCase()] = p;
					});

					break;

				case 'dist-appstore':
					_t.assertIssue(iosInfo.issues, 'IOS_NO_VALID_DIST_CERTS_FOUND');

					_t.conf.options['deploy-type'].values = [ 'production' ];
					_t.conf.options['device-id'].required = false;
					_t.conf.options['distribution-name'].required = true;
					_t.conf.options['pp-uuid'].required = true;

					// build lookup maps
					iosInfo.provisioning.distribution.forEach(function (p) {
						_t.provisioningProfileLookup[p.uuid.toLowerCase()] = p;
					});
					break;

				case 'macos':
					_t.conf.options['device-id'].required = false;
					break;
			}
		},
		default: 'simulator',
		desc: __('the target to build for'),
		order: order,
		required: true,
		values: this.targets
	};
};

/**
 * Defines the --watch-app-name option.
 *
 * @param {Integer} order - The order to apply to this option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptionWatchAppName = function configOptionWatchAppName(order) {
	return {
		desc: __('when building an app with multiple watch app, the name of the watch app to launch; only used when target is %s', 'simulator'.cyan),
		hint: __('name'),
		order: order
	};
};

/**
 * Defines the --watch-device-id option.
 *
 * @param {Integer} order - The order to apply to this option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptionWatchDeviceId = function configOptionWatchDeviceId(order) {
	const cli = this.cli,
		iosSims = this.iosInfo.simulators.ios,
		watchSims = this.iosInfo.simulators.watchos,
		xcodes = this.iosInfo.xcode;

	return {
		abbr: 'W',
		desc: __('the watch simulator UDID to launch when building an app with a watch app; only used when target is %s', 'simulator'.cyan),
		hint: __('udid'),
		order: order,
		prompt: function (callback) {
			if (cli.argv.target !== 'simulator') {
				return callback();
			}

			const options = {},
				iosSdkVersion = cli.argv['ios-version'];
			let iphoneSim = null,
				maxName = 0;

			if (cli.argv['device-id']) {
				iphoneSim = Object.keys(iosSims).find(ver => iosSims[ver].find(sim => sim.udid === cli.argv['device-id']));
			}

			Object.keys(watchSims).forEach(function (sdk) {
				watchSims[sdk].forEach(function (sim) {
					// check iOS SDK compatibility
					if ((!iosSdkVersion
							|| Object.keys(sim.supportsXcode).some(function (xcodeId) {
								if (sim.supportsXcode[xcodeId] && xcodes[xcodeId].sdks.indexOf(iosSdkVersion) !== -1) {
									return true;
								}
								return false;
							})
					)
						&& (!iphoneSim || iphoneSim.watchCompanion[sim.udid])
					) {
						options[sdk] || (options[sdk] = []);
						options[sdk].push(sim);
						if (sim.name.length > maxName) {
							maxName = sim.name.length;
						}
					}
				});
			});

			const params = {
				formatters: {},
				default: '1', // just default to the first one, whatever that will be
				autoSelectOne: true,
				margin: '',
				optionLabel: 'name',
				optionValue: 'udid',
				numbered: true,
				relistOnError: true,
				complete: true,
				suggest: true,
				options: options
			};

			// simulator specific settings
			params.title = __('Which simulator do you want to launch your app in?');
			params.promptLabel = __('Select an simulator by number or name');
			params.formatters.option = function (opt, idx, num) {
				return '  ' + num + appc.string.rpad(opt.name, maxName).cyan + '  ' + opt.udid.grey;
			};

			callback(fields.select(params));
		},
		validate: function (value, callback) {
			if (!cli.argv['build-only'] && cli.argv.target === 'simulator') {
				if (!value || value === true) {
					return callback(true);
				} else if (!Object.keys(watchSims).some(ver => watchSims[ver].some(sim => sim.udid === value))) {
					return callback(new Error(__('Invalid Watch Simulator UDID "%s"', value)));
				}
			}
			callback(null, value);
		}
	};
};

/**
 * Validates and initializes settings from the tiapp.xml. This function can be
 * invoked when trying to build the list of iOS Simulators for --device-id
 * prompting, otherwise it'll be called from the iOS build's validate().
 *
 * It's critical that this function ONLY logs output in the event of a fatal
 * error.
 */
iOSBuilder.prototype.initTiappSettings = function initTiappSettings() {
	if (this._tiappSettingsInitialized) {
		return;
	}

	const cli = this.cli;
	const logger = this.logger;

	// redundant, but we need it earlier than validate()
	this.projectDir = cli.argv['project-dir'];

	const tiapp = cli.tiapp;
	tiapp.ios || (tiapp.ios = {});
	tiapp.ios.capabilities || (tiapp.ios.capabilities = {});
	Array.isArray(tiapp.ios.extensions) || (tiapp.ios.extensions = []);

	// the existance of an app id was already checked in cli/commands/build.js,
	// but now we need to check for underscores
	if (!this.config.get('app.skipAppIdValidation') && !tiapp.properties['ti.skipAppIdValidation']) {
		if (!/^([a-zA-Z_]{1}[a-zA-Z0-9_-]*(\.[a-zA-Z0-9_-]*)*)$/.test(tiapp.id)) {
			logger.error(__('tiapp.xml contains an invalid app id "%s"', tiapp.id));
			logger.error(__('The app id must consist only of letters, numbers, dashes, and underscores.'));
			logger.error(__('Note: iOS does not allow underscores.'));
			logger.error(__('The first character must be a letter or underscore.'));
			logger.error(__('Usually the app id is your company\'s reversed Internet domain name. (i.e. com.example.myapp)') + '\n');
			process.exit(1);
		}

		if (tiapp.id.indexOf('_') !== -1) {
			logger.error(__('tiapp.xml contains an invalid app id "%s"', tiapp.id));
			logger.error(__('The app id must consist of letters, numbers, and dashes.'));
			logger.error(__('The first character must be a letter.'));
			logger.error(__('Usually the app id is your company\'s reversed Internet domain name. (i.e. com.example.myapp)') + '\n');
			process.exit(1);
		}
	}

	// make sure the app doesn't have any blacklisted directories or files in the Resources directory and warn about graylisted names
	if (this.blacklistDirectories.indexOf(tiapp.name.toLowerCase()) !== -1 || tiapp.name.toLowerCase() === 'frameworks') {
		logger.error(__('The app name conflicts with a reserved file.'));
		logger.error(__('You must change the name of the app in the tiapp.xml.') + '\n');
		process.exit(1);
	}

	// make sure we have an app icon
	if (!tiapp.icon || ![ 'Resources', 'Resources/iphone', 'Resources/ios' ].some(p => fs.existsSync(this.projectDir, p, tiapp.icon), this)) {
		tiapp.icon = 'appicon.png';
	}

	if (!/\.png$/.test(tiapp.icon)) {
		logger.error(__('Application icon must be a PNG formatted image.') + '\n');
		process.exit(1);
	}

	// validate the log server port
	const logServerPort = tiapp.ios['log-server-port'];
	if (!/^dist-(appstore|adhoc)$/.test(this.target) && logServerPort && (typeof logServerPort !== 'number' || logServerPort < 1024 || logServerPort > 65535)) {
		logger.error(__('Invalid <log-server-port> found in the tiapp.xml'));
		logger.error(__('Port must be a positive integer between 1024 and 65535') + '\n');
		process.exit(1);
	}

	// process min ios version
	this.minIosVersion = tiapp.ios['min-ios-ver'] && appc.version.gt(tiapp.ios['min-ios-ver'], this.packageJson.minIosVersion) ? tiapp.ios['min-ios-ver'] : this.packageJson.minIosVersion;

	// process device family
	const deploymentTargets = tiapp['deployment-targets'];
	this.deviceFamily = cli.argv['device-family'];
	if (!this.deviceFamily && deploymentTargets) {
		if (deploymentTargets.ipad && (!deploymentTargets.iphone || cli.argv.$originalPlatform === 'ipad')) {
			this.deviceFamily = 'ipad';
		} else if (deploymentTargets.iphone && !deploymentTargets.ipad) {
			this.deviceFamily = 'iphone';
		} else {
			this.deviceFamily = 'universal';
		}
	}

	if (cli.argv.$originalPlatform === 'ipad') {
		logger.warn(__('--platform ipad has been deprecated and will be removed in Titanium SDK 7.0.0'));
		logger.warn(__('See %s for more details', 'https://jira.appcelerator.org/browse/TIMOB-24228'));
	}

	// init the extensions
	tiapp.ios.extensions.forEach(function (ext) {
		if (!ext.projectPath) {
			logger.error(__('iOS extensions must have a "projectPath" attribute that points to a folder containing an Xcode project.') + '\n');
			process.exit(1);
		}

		// projectPath could be either the path to a project directory or the actual .xcodeproj
		ext.origProjectPath = ext.projectPath;
		ext.projectPath = ext.projectPath[0] === '/' ? appc.fs.resolvePath(ext.projectPath) : appc.fs.resolvePath(this.projectDir, ext.projectPath);

		const xcodeprojRegExp = /\.xcodeproj$/;
		if (!xcodeprojRegExp.test(ext.projectPath)) {
			// maybe we're the parent dir?
			ext.projectPath = path.join(ext.projectPath, path.basename(ext.projectPath) + '.xcodeproj');
		}

		const projectName = path.basename(ext.projectPath.replace(xcodeprojRegExp, ''));

		if (!fs.existsSync(ext.projectPath)) {
			logger.error(__('iOS extension "%s" Xcode project not found: %s', projectName, ext.projectPath) + '\n');
			process.exit(1);
		}

		const projFile = path.join(ext.projectPath, 'project.pbxproj');
		if (!fs.existsSync(projFile)) {
			logger.error(__('iOS extension "%s" project missing Xcode project file: %s', projectName, projFile) + '\n');
			process.exit(1);
		}

		if (!Array.isArray(ext.targets) || !ext.targets.length) {
			return;
		}

		const proj = xcode.project(path.join(ext.projectPath, 'project.pbxproj')).parseSync();

		// flag each target we care about
		let tiappTargets = {};
		ext.targets.forEach(function (target) {
			tiappTargets[target.name] = target;
		});

		// augment the ext entry with some extra details that we'll use later when constructing the Xcode project
		ext.objs        = proj.hash.project.objects;
		ext.project     = ext.objs.PBXProject[proj.hash.project.rootObject];
		ext.projectName = path.basename(ext.projectPath).replace(/\.xcodeproj$/, '');
		ext.basePath    = path.dirname(ext.projectPath);
		ext.relPath     = 'extensions/' + path.basename(path.dirname(ext.projectPath));
		ext.targetInfo  = {};

		const globalCfg = ext.objs.XCConfigurationList[ext.project.buildConfigurationList];
		const globalCfgId = globalCfg.buildConfigurations
			.filter(function (c) { return c.comment.toLowerCase() === (globalCfg.defaultConfigurationName ? globalCfg.defaultConfigurationName.toLowerCase() : 'release'); })
			.map(function (c) { return c.value; })
			.shift();
		const globalBuildSettings = ext.objs.XCBuildConfiguration[globalCfgId].buildSettings;

		// check that the PP UUID is correct
		let pps = [];
		if (cli.argv.target === 'device') {
			pps = this.iosInfo.provisioning.development;
		} else if (cli.argv.target === 'dist-appstore') {
			pps = this.iosInfo.provisioning.distribution;
		} else if (cli.argv.target === 'dist-adhoc') {
			pps = []
				.concat(this.iosInfo.provisioning.adhoc, this.iosInfo.provisioning.enterprise)
				.filter(function (p) {
					return p;
				});
		}

		function getPPbyUUID(ppuuid) {
			return pps
				.filter(function (p) {
					if (!p.expired && !p.managed && p.uuid === ppuuid) {
						return true;
					}
					return false;
				})
				.shift();
		}

		// find our targets
		ext.project.targets.forEach(function (t) {
			const targetName = t.comment;

			if (!tiappTargets[targetName]) {
				// not a target we care about
				return;
			}

			// we have found our target!

			const nativeTarget = ext.objs.PBXNativeTarget[t.value];
			const cfg = ext.objs.XCConfigurationList[nativeTarget.buildConfigurationList];
			const cfgid = cfg.buildConfigurations
				.filter(function (c) { return c.comment.toLowerCase() === (cfg.defaultConfigurationName ? cfg.defaultConfigurationName.toLowerCase() : 'release'); })
				.map(function (c) { return c.value; })
				.shift();
			const buildSettings = ext.objs.XCBuildConfiguration[cfgid].buildSettings;
			const productType = nativeTarget.productType.replace(/^"/, '').replace(/"$/, '');
			const containsExtension = productType.indexOf('extension') !== -1;
			const containsWatchApp = productType.indexOf('watchapp') !== -1;
			const containsWatchKit = productType.indexOf('watchkit') !== -1;

			const targetInfo = ext.targetInfo[targetName] = {
				productType:           productType,
				isWatchAppV1Extension: productType === 'com.apple.product-type.watchkit-extension',
				isExtension:           containsExtension && (!containsWatchKit || productType === 'com.apple.product-type.watchkit-extension'),
				isAppClip:             productType === 'com.apple.product-type.application.on-demand-install-capable',
				isWatchAppV1:          productType === 'com.apple.product-type.application.watchapp',
				isWatchAppV2orNewer:   containsWatchApp && productType !== 'com.apple.product-type.application.watchapp',
				sdkRoot:               productType === 'com.apple.product-type.application.watchapp' ? 'watchos' : (buildSettings.SDKROOT || globalBuildSettings.SDKROOT || null),
				watchOS:               productType === 'com.apple.product-type.application.watchapp' ? '1.0' : (buildSettings.WATCHOS_DEPLOYMENT_TARGET || globalBuildSettings.WATCHOS_DEPLOYMENT_TARGET || null),
				infoPlist:             null
			};

			if (targetInfo.isWatchAppV1Extension || targetInfo.isWatchAppV1) {
				logger.error(__('WatchOS1 app detected.'));
				logger.error(__('Titanium %s does not support WatchOS1 apps.', this.titaniumSdkVersion) + '\n');
				process.exit(1);
			}

			// we need to get a min watch os version so that we can intelligently pick an appropriate watch simulator
			if (targetInfo.isWatchAppV2orNewer
					&& (!cli.argv['watch-app-name'] || targetName === cli.argv['watch-app-name'])
					&& (!this.watchMinOSVersion || appc.version.lt(targetInfo.watchOS, this.watchMinOSVersion))) {
				this.watchMinOSVersion = targetInfo.watchOS;
			}

			if (targetInfo.isWatchAppV2orNewer) {
				this.hasWatchAppV2orNewer = true;
			}

			// find this target's Info.plist
			ext.objs.PBXGroup[ext.project.mainGroup].children.some(function (child) {
				if (child.comment !== targetName) {
					return false;
				}

				(function walkGroup(uuid, basePath) {
					if (ext.objs.PBXGroup[uuid].path) {
						basePath = path.join(basePath, ext.objs.PBXGroup[uuid].path.replace(/^"/, '').replace(/"$/, ''));
					}

					ext.objs.PBXGroup[uuid].children.some(function (child) {
						if (ext.objs.PBXGroup[child.value]) {
							return walkGroup(child.value, basePath);
						} else if (ext.objs.PBXFileReference[child.value] && child.comment === 'Info.plist') {
							const infoPlistFile = path.join(basePath, 'Info.plist');
							if (!fs.existsSync(infoPlistFile)) {
								logger.error(__('Unable to find "%s" iOS extension\'s "%s" target\'s Info.plist: %s', ext.projectName, targetName, infoPlistFile) + '\n');
								process.exit(1);
							}

							const plist = ext.targetInfo[targetName].infoPlist = ioslib.utilities.readPlist(infoPlistFile);
							if (!plist) {
								logger.error(__('Failed to parse "%s" iOS extension\'s "%s" target\'s Info.plist: %s', ext.projectName, targetName, infoPlistFile) + '\n');
								process.exit(1);
							}

							if (plist.WKWatchKitApp) {
								const CFBundleIdentifier = plist.CFBundleIdentifier.replace('$(PRODUCT_BUNDLE_IDENTIFIER)', buildSettings.PRODUCT_BUNDLE_IDENTIFIER);
								if (CFBundleIdentifier.indexOf(tiapp.id) !== 0) {
									logger.error(__('iOS extension "%s" WatchKit App bundle identifier is "%s", but must be prefixed with "%s".', ext.projectName, plist.CFBundleIdentifier, tiapp.id) + '\n');
									process.exit(1);
								}

								if (CFBundleIdentifier.toLowerCase() === tiapp.id.toLowerCase()) {
									logger.error(__('iOS extension "%s" WatchKit App bundle identifier must be different from the Titanium app\'s id "%s".', ext.projectName, tiapp.id) + '\n');
									process.exit(1);
								}
							} else if (targetInfo.isWatchAppV1 || targetInfo.isWatchAppV2orNewer) {
								logger.error(__('The "%s" iOS extension "%s" target\'s Info.plist is missing the WKWatchKitApp property, yet the product type is of a watch: %s', ext.projectName, targetName, productType) + '\n');
								process.exit(1);
							}

							ext.targetInfo.id = plist.CFBundleIdentifier.replace(/^\$\((.*)\)$/, function (s, m) {
								return buildSettings[m] || s;
							});

							return true;
						}
						return false;
					});
				}(child.value, ext.basePath));

				return true;
			});

			if (cli.argv.target !== 'simulator' && cli.argv.target !== 'macos') {
				// check that all target provisioning profile uuids are valid
				if (!tiappTargets[targetName].ppUUIDs || !tiappTargets[targetName].ppUUIDs[cli.argv.target]) {
					if (cli.argv['pp-uuid']) {
						if (!tiappTargets[targetName].ppUUIDs) {
							tiappTargets[targetName].ppUUIDs = {};
						}
						tiappTargets[targetName].ppUUIDs[cli.argv.target] = cli.argv['pp-uuid'];
						// logger.warn(__('iOS extension "%s" target "%s" is missing the %s provisioning profile UUID in tiapp.xml.', projectName, '<' + cli.argv.target + '>', targetName));
						// logger.warn(__('Using the iOS app provisioning profile UUID "%s"', cli.argv['pp-uuid']));
					} else {
						logger.error(__('iOS extension "%s" target "%s" is missing the %s provisioning profile UUID in tiapp.xml.', projectName, '<' + cli.argv.target + '>', targetName));
						logger.log();
						logger.log('<ti:app xmlns:ti="http://ti.appcelerator.org">'.grey);
						logger.log('    <ios>'.grey);
						logger.log('        <extensions>'.grey);
						logger.log(('            <extension projectPath="' + ext.origProjectPath + '">').grey);
						logger.log(('                <target name="' + targetName + '">').grey);
						logger.log('                    <provisioning-profiles>'.grey);
						logger.log(('                        <' + cli.argv.target + '>PROVISIONING PROFILE UUID</' + cli.argv.target + '>').magenta);
						logger.log('                    </provisioning-profiles>'.grey);
						logger.log('                </target>'.grey);
						logger.log('            </extension>'.grey);
						logger.log('        </extensions>'.grey);
						logger.log('    </ios>'.grey);
						logger.log('</ti:app>'.grey);
						logger.log();
						process.exit(1);
					}
				}

				// find the selected provisioning profile
				const ppuuid = tiappTargets[targetName].ppUUIDs[cli.argv.target];
				const pp = getPPbyUUID(ppuuid);

				if (!pp) {
					logger.error(__('iOS extension "%s" target "%s" has invalid provisioning profile UUID in tiapp.xml.', projectName, targetName));
					logger.error(__('Unable to find a valid provisioning profile matching the UUID "%s".', ppuuid) + '\n');
					process.exit(1);
				}

				if (ext.targetInfo.id && !(new RegExp('^' + pp.appId.replace(/\*/g, '.*') + '$')).test(ext.targetInfo.id)) { // eslint-disable-line security/detect-non-literal-regexp
					logger.error(__('iOS extension "%s" target "%s" has invalid provisioning profile UUID in tiapp.xml.', projectName, targetName));
					logger.error(__('The provisioning profile "%s" is tied to the application identifier "%s", however the extension\'s identifier is "%s".', ppuuid, pp.appId, ext.targetInfo.id));
					logger.log();

					const matches = pps.filter(function (p) {
						return !p.expired && !p.managed && (new RegExp('^' + p.appId.replace(/\./g, '\\.').replace(/\*/g, '.*') + '$')).test(ext.targetInfo.id); // eslint-disable-line security/detect-non-literal-regexp
					});
					if (matches.length) {
						logger.log(__('Did you mean?'));
						let max = 0;
						matches.forEach(function (m) {
							if (m.appId.length > max) {
								max = m.appId.length;
							}
						});
						matches.forEach(function (m) {
							const expires = m.expirationDate && moment(m.expirationDate),
								day = expires && expires.format('D'),
								hour = expires && expires.format('h');
							logger.log('  ' + String(m.uuid).cyan + ' '
								+ appc.string.rpad(m.appId, max + 1)
								+ (m.expirationDate ? (' (' + __('expires %s', expires.format('MMM') + ' '
								+ (day.length === 1 ? ' ' : '') + day + ', ' + expires.format('YYYY') + ' '
								+ (hour.length === 1 ? ' ' : '') + hour + ':' + expires.format('mm:ss a'))
								+ ')').grey : ''));
						});
						logger.log();
					}

					process.exit(1);
				}
			}

			// we don't need the tiapp target lookup anymore
			delete tiappTargets[targetName];
		}, this);

		// check if we're missing any targets
		tiappTargets = Object.keys(tiappTargets);
		if (tiappTargets.length) {
			logger.error(__n('iOS extension "%%s" does not contain a target named "%%s".', 'iOS extension "%%s" does not contain the following targets: "%%s".', tiappTargets.length, projectName, tiappTargets.join(', ')) + '\n');
			process.exit(1);
		}

		this.extensions.push(ext);
	}, this);

	this._tiappSettingsInitialized = true;
};

/**
 * Validates the iOS build-specific arguments, tiapp.xml settings, and environment.
 *
 * @param {Object} logger - The logger instance.
 * @param {Object} config - The Titanium CLI config instance.
 * @param {Object} cli - The Titanium CLI instance.
 *
 * @returns {Function} A function to be called async which returns the actual configuration.
 */
iOSBuilder.prototype.validate = function validate(logger, config, cli) {
	Builder.prototype.validate.apply(this, arguments);

	return function (callback) {
		this.target = cli.argv.target;
		this.deployType = !/^dist-/.test(this.target) && cli.argv['deploy-type'] ? cli.argv['deploy-type'] : this.deployTypes[this.target];
		this.buildType = cli.argv['build-type'] || '';
		this.provisioningProfileUUID = cli.argv['pp-uuid'];
		if (this.provisioningProfileUUID) {
			this.provisioningProfile = this.findProvisioningProfile(this.target, this.provisioningProfileUUID);
		}

		// add the ios specific default icon to the list of icons
		this.defaultIcons.unshift(path.join(this.projectDir, 'DefaultIcon-ios.png'));

		// manually inject the build profile settings
		switch (this.deployType) {
			case 'production':
				this.showErrorController = false;
				this.minifyJS = true;
				this.encryptJS = true;
				this.minifyCSS = true;
				this.allowDebugging = false;
				this.allowProfiling = false;
				this.includeAllTiModules = false;
				break;

			case 'test':
				this.showErrorController = true;
				this.minifyJS = true;
				this.encryptJS = true;
				this.minifyCSS = true;
				this.allowDebugging = true;
				this.allowProfiling = true;
				this.includeAllTiModules = false;
				break;

			case 'development':
			default:
				this.showErrorController = true;
				this.minifyJS = false;
				this.encryptJS = false;
				this.minifyCSS = false;
				this.allowDebugging = true;
				this.allowProfiling = true;
				this.includeAllTiModules = true;
		}

		if (cli.argv['skip-js-minify']) {
			this.minifyJS = false;
		}
		if (cli.argv['hide-error-controller']) {
			this.showErrorController = false;
		}

		// this may have already been called in an option validate() callback
		this.initTiappSettings();

		// Do we write out process.env into a file in the app to use?
		this.writeEnvVars = this.deployType !== 'production';

		// Transpilation details
		this.transpile = cli.tiapp['transpile'] !== false; // Transpiling is an opt-out process now
		// this.minSupportedIosSdk holds the target ios version to transpile down to
		// If they're passing flag to do source-mapping, that overrides everything, so turn it on
		if (cli.argv['source-maps']) {
			this.sourceMaps = true;
			// if they haven't, respect the tiapp.xml value if set one way or the other
		} else if (Object.prototype.hasOwnProperty.call(cli.tiapp, 'source-maps')) { // they've explicitly set a value in tiapp.xml
			this.sourceMaps = cli.tiapp['source-maps'] === true; // respect the tiapp.xml value
		} else { // otherwise turn on by default for non-production builds
			this.sourceMaps = this.deployType !== 'production';
		}

		// check for blacklisted files in the Resources directory
		[	path.join(this.projectDir, 'Resources'),
			path.join(this.projectDir, 'Resources', 'iphone'),
			path.join(this.projectDir, 'Resources', 'ios')
		].forEach(function (dir) {
			fs.existsSync(dir) && fs.readdirSync(dir).forEach(function (filename) {
				const lcaseFilename = filename.toLowerCase(),
					isDir = fs.statSync(path.join(dir, filename)).isDirectory();

				// if we have a platform resource dir, then this will not be copied and we should be ok
				if (ti.allPlatformNames.indexOf(lcaseFilename) !== -1) {
					return;
				}

				if (this.blacklistDirectories.indexOf(lcaseFilename) !== -1) {
					if (isDir) {
						logger.error(__('Found blacklisted directory in the Resources directory.'));
						logger.error(__('The directory "%s" is a reserved directory.', filename));
						logger.error(__('You must rename this directory to something else.') + '\n');
					} else {
						logger.error(__('Found blacklisted file in the Resources directory.'));
						logger.error(__('The file "%s" is a reserved file.', filename));
						logger.error(__('You must rename this file to something else.') + '\n');
					}
					process.exit(1);
				} else if (this.graylistDirectories.indexOf(lcaseFilename) !== -1) {
					if (isDir) {
						logger.warn(__('Found graylisted directory in the Resources directory.'));
						logger.warn(__('The directory "%s" is potentially a reserved directory.', filename));
						logger.warn(__('There is a good chance your app will be rejected by Apple.'));
						logger.warn(__('It is highly recommended you rename this directory to something else.'));
					} else {
						logger.warn(__('Found graylisted file in the Resources directory.'));
						logger.warn(__('The file "%s" is potentially a reserved file.', filename));
						logger.warn(__('There is a good chance your app will be rejected by Apple.'));
						logger.warn(__('It is highly recommended you rename this file to something else.'));
					}
				}
			}, this);
		}, this);

		// if in the prepare phase and doing a device/dist build...
		if (cli.argv.target !== 'simulator' || cli.argv.target !== 'macos') {
			// make sure they have Apple's WWDR cert installed
			if (!this.iosInfo.certs.wwdr) {
				logger.error(__('WWDR Intermediate Certificate not found') + '\n');
				logger.log(__('Download and install the certificate from %s', 'http://developer.apple.com/certificationauthority/AppleWWDRCA.cer'.cyan) + '\n');
				process.exit(1);
			}

			// validate keychain
			const keychain = cli.argv.keychain ? appc.fs.resolvePath(cli.argv.keychain) : null;
			if (keychain && !fs.existsSync(keychain)) {
				logger.error(__('Unable to find keychain "%s"', keychain) + '\n');
				logger.log(__('Available keychains:'));
				Object.keys(this.iosInfo.certs.keychains).forEach(function (kc) {
					logger.log('    ' + kc.cyan);
				});
				logger.log();
				appc.string.suggest(keychain, Object.keys(this.iosInfo.certs.keychains), logger.log);
				process.exit(1);
			}
		}

		if (cli.argv.target !== 'dist-appstore') {
			const tool = [];
			this.allowDebugging && tool.push('debug');
			this.allowProfiling && tool.push('profiler');
			tool.forEach(function (type) {
				if (cli.argv[type + '-host']) {
					if (typeof cli.argv[type + '-host'] === 'number') {
						logger.error(__('Invalid %s host "%s"', type, cli.argv[type + '-host']) + '\n');
						logger.log(__('The %s host must be in the format "host:port".', type) + '\n');
						process.exit(1);
					}

					const parts = cli.argv[type + '-host'].split(':');

					if ((cli.argv.target === 'simulator' && parts.length < 2) || (cli.argv.target !== 'simulator' && parts.length < 4)) {
						logger.error(__('Invalid ' + type + ' host "%s"', cli.argv[type + '-host']) + '\n');
						if (cli.argv.target === 'simulator') {
							logger.log(__('The %s host must be in the format "host:port".', type) + '\n');
						} else {
							logger.log(__('The %s host must be in the format "host:port:airkey:hosts".', type) + '\n');
						}
						process.exit(1);
					}

					if (parts.length > 1 && parts[1]) {
						const port = parseInt(parts[1]);
						if (isNaN(port) || port < 1 || port > 65535) {
							logger.error(__('Invalid ' + type + ' host "%s"', cli.argv[type + '-host']) + '\n');
							logger.log(__('The port must be a valid integer between 1 and 65535.') + '\n');
							process.exit(1);
						}
					}
				}
			});
		}

		series(this, [
			function selectIosVersion() {
				this.iosSdkVersion = cli.argv['ios-version'] || null;
				this.xcodeEnv = null;

				const xcodeInfo = this.iosInfo.xcode;

				function sortXcodeIds(a, b) {
					// prioritize selected xcode
					if (xcodeInfo[a].selected) {
						return -1;
					}
					if (xcodeInfo[b].selected) {
						return 1;
					}
					// newest to oldest
					return appc.version.gt(xcodeInfo[a].version, xcodeInfo[b].version) ? -1 : appc.version.lt(xcodeInfo[a].version, xcodeInfo[b].version) ? 1 : 0;
				}

				const sortedXcodeIds = Object.keys(xcodeInfo).sort(sortXcodeIds);
				if (this.iosSdkVersion) {
					// find the Xcode for this version
					sortedXcodeIds.some(function (ver) {
						if (xcodeInfo[ver].sdks.includes(this.iosSdkVersion)) {
							this.xcodeEnv = xcodeInfo[ver];
							return true;
						}
						return false;
					}, this);

					if (!this.xcodeEnv) {
						// this should not be possible, but you never know
						logger.error(__('Unable to find any Xcode installations that support iOS SDK %s.', this.iosSdkVersion) + '\n');
						process.exit(1);
					}

				} else { // device, simulator, dist-appstore, dist-adhoc
					sortedXcodeIds
						.filter(id => xcodeInfo[id].supported)
						.some(function (id) {
							return xcodeInfo[id].sdks.sort().reverse().some(function (ver) {
								if (appc.version.gte(ver, this.minIosVersion)) {
									this.iosSdkVersion = ver;
									this.xcodeEnv = xcodeInfo[id];
									return true;
								}
								return false;
							}, this);
						}, this);

					if (!this.iosSdkVersion) {
						logger.error(__('Unable to find any Xcode installations with a supported iOS SDK.'));
						logger.error(__('Please install the latest Xcode and point xcode-select to it.') + '\n');
						process.exit(1);
					}
				}
			},

			function selectDevice(next) {
				if (cli.argv.target === 'dist-appstore' || cli.argv.target === 'dist-adhoc') {
					return next();
				}

				// no --device-id or doing a build-only sim build, so pick a device

				if (cli.argv.target === 'device') {
					if (!cli.argv['build-only'] && !cli.argv['device-id']) {
						cli.argv['device-id'] = this.iosInfo.devices.length ? this.iosInfo.devices[0].udid : 'itunes';
					}
					return next();
				}

				// if we found a watch app and --watch-device-id was set, but --launch-watch-app was not, then set it
				if (this.hasWatchAppV2orNewer && cli.argv['watch-device-id'] && !cli.argv['launch-watch-app-only']) {
					cli.argv['launch-watch-app'] = true;
				}

				// make sure we have a watch app
				if ((cli.argv['launch-watch-app'] || cli.argv['launch-watch-app-only']) && !this.hasWatchAppV2orNewer) {
					logger.warn(__('%s flag was set, however there are no iOS extensions containing a watch app.', cli.argv['launch-watch-app'] ? '--launch-watch-app' : '--launch-watch-app-only'));
					logger.warn(__('Disabling launch watch app flag'));
					cli.argv['launch-watch-app'] = cli.argv['launch-watch-app-only'] = false;
				}

				// target is simulator
				ioslib.simulator.findSimulators({
					// env
					xcodeSelect:            config.get('osx.executables.xcodeSelect'),
					security:               config.get('osx.executables.security'),
					// provisioning
					profileDir:             config.get('ios.profileDir'),
					// xcode
					searchPath:             config.get('paths.xcode'),
					minIosVersion:          this.minIosVersion,
					supportedVersions:      this.packageJson.vendorDependencies.xcode,
					// find params
					appBeingInstalled:      true,
					simHandleOrUDID:        cli.argv['device-id'],
					iosVersion:             this.iosSdkVersion,
					simType:                this.deviceFamily === 'ipad' ? 'ipad' : 'iphone',
					simVersion:             this.iosSdkVersion,
					watchAppBeingInstalled: this.hasWatchAppV2orNewer && (cli.argv['launch-watch-app'] || cli.argv['launch-watch-app-only']),
					watchHandleOrUDID:      cli.argv['watch-device-id'],
					watchMinOSVersion:      this.watchMinOSVersion,
					logger: function (msg) {
						logger.trace(('[ioslib] ' + msg).grey);
					}
				}, function (err, simHandle, watchSimHandle, selectedXcode) {
					if (err) {
						return next(err);
					}

					this.simHandle = simHandle;
					this.watchSimHandle = watchSimHandle;
					this.xcodeEnv = selectedXcode;

					// only build active arch simulator is 64-bit (iPhone 5s or newer, iPhone 5 and older are not 64-bit)
					const m = this.simHandle.model.match(/^(iPad|iPhone)([\d]+)/);
					this.simOnlyActiveArch = !!(m && (m[1] === 'iPad' && parseInt(m[2]) >= 4) || (m[1] === 'iPhone' && parseInt(m[2]) >= 6));

					if (!this.iosSdkVersion) {
						const sdks = selectedXcode.sdks.sort();
						this.iosSdkVersion = sdks[sdks.length - 1];
					}

					next();
				}.bind(this));
			},

			function checkEULA() {
				if (!this.xcodeEnv.eulaAccepted) {
					logger.error(__('Xcode %s end-user license agreement has not been accepted.', this.xcodeEnv.version));
					logger.error(__('Please launch "%s" or run "sudo xcodebuild -license" to accept the license.', this.xcodeEnv.xcodeapp) + '\n');
					process.exit(1);
				}
			},

			function validateTeamId() {
				this.teamId = this.tiapp.ios['team-id'];
				if (!this.teamId && this.provisioningProfile) {
					if (this.provisioningProfile.team.length === 1) {
						// only one team, so choose this over the appPrefix
						this.teamId = this.provisioningProfile.team[0];
					} else {
						// we have multiple teams and we don't know which one to pick, so prefer the appPrefix
						this.teamId = this.provisioningProfile.appPrefix;

						// if the appPrefix is not in the list of teams, then we need to fail and force the user
						// to manually specify their team id
						if (this.provisioningProfile.team.length && this.provisioningProfile.team.indexOf(this.teamId) === -1) {
							logger.log(__('Available teams:'));
							this.provisioningProfile.team.forEach(function (id) {
								logger.log('  ' + id.cyan);
							});
							logger.log();
							logger.log('<ti:app xmlns:ti="http://ti.appcelerator.org">'.grey);
							logger.log('    <ios>'.grey);
							logger.log('        <team-id>TEAM ID</team-id>'.magenta);
							logger.log('    </ios>'.grey);
							logger.log('</ti:app>'.grey);
							logger.log();
							process.exit(1);
						}
					}
				}
			},

			function toSymlinkOrNotToSymlink() {
				this.symlinkLibrariesOnCopy = config.get('ios.symlinkResources', true) && !cli.argv['force-copy'];
				this.symlinkFilesOnCopy = false;
			},

			function determineMinIosVer() {
				// figure out the min-ios-ver that this app is going to support
				let defaultMinIosVersion = this.packageJson.minIosVersion;

				this.minIosVer = this.tiapp.ios['min-ios-ver'] || defaultMinIosVersion;

				if (version.lt(this.minIosVer, defaultMinIosVersion)) {
					logger.warn(__('The %s of the iOS section in the tiapp.xml is lower than the recommended minimum iOS version %s', 'min-ios-ver', defaultMinIosVersion));
					logger.warn(__('Consider bumping the %s to at least %s', 'min-ios-ver', defaultMinIosVersion));
					this.minIosVer = defaultMinIosVersion;
				} else if (version.gt(this.minIosVer, this.iosSdkVersion)) {
					logger.error(__('The <min-ios-ver> of the iOS section in the tiapp.xml is set to %s and is greater than the specified iOS version %s', version.format(this.minIosVer, 2), version.format(this.iosSdkVersion, 2)));
					logger.error(__('Either rerun with --ios-version %s or set the <min-ios-ver> to %s.', version.format(this.minIosVer, 2), version.format(this.iosSdkVersion, 2)) + '\n');
					process.exit(1);
				}
			},

			function validateDevice() {
				// check the min-ios-ver for the device we're installing to
				if (this.target === 'device') {
					this.getDeviceInfo().devices.forEach(function (device) {
						if (device.udid !== 'all' && device.udid !== 'itunes' && (cli.argv['device-id'] === 'all' || cli.argv['device-id'] === device.udid) && version.lt(device.productVersion, this.minIosVer)) {
							logger.error(__('This app does not support the device "%s"', device.name) + '\n');
							logger.log(__('The device is running iOS %s, however the app\'s the minimum iOS version is set to %s', device.productVersion.cyan, version.format(this.minIosVer, 2, 3).cyan));
							logger.log(__('In order to install this app on this device, lower the %s to %s in the tiapp.xml:', '<min-ios-ver>'.cyan, version.format(device.productVersion, 2, 2).cyan));
							logger.log();
							logger.log('<ti:app xmlns:ti="http://ti.appcelerator.org">'.grey);
							logger.log('    <ios>'.grey);
							logger.log(('        <min-ios-ver>' + version.format(device.productVersion, 2, 2) + '</min-ios-ver>').magenta);
							logger.log('    </ios>'.grey);
							logger.log('</ti:app>'.grey);
							logger.log();
							process.exit(0);
						}
					}, this);
				}
			},

			function validateModules(next) {
				this.validateTiModules([ 'ios', 'iphone' ], this.deployType, function (err, modules) {
					this.modules = modules.found;

					this.commonJsModules = [];
					this.nativeLibModules = [];
					this.legacyModules = new Set();

					const nativeHashes = [];

					modules.found.forEach(function (module) {
						if (module.platform.indexOf('commonjs') !== -1) {
							module.native = false;

							// look for legacy module.id.js first
							let libFile = path.join(module.modulePath, module.id + '.js');
							module.libFile = fs.existsSync(libFile) ? libFile : null;
							// If no legacy file, let require.resolve get the main script
							if (!module.libFile) {
								libFile = require.resolve(module.modulePath);
								if (fs.existsSync(libFile)) {
									module.libFile = libFile;
								}

								if (!module.libFile) {
									this.logger.error(__('Module "%s" (%s) is missing main file: %s, package.json with "main" entry, index.js, or index.json', module.id, module.manifest.version || 'latest', module.id + '.js') + '\n');
									process.exit(1);
								}
							}

							this.commonJsModules.push(module);
						} else {
							module.native = true;
							const frameworkName = this.scrubbedModuleId(module.id) + '.framework';
							const xcFrameworkOfLib = module.id + '.xcframework';
							const xcFrameworkOfFramework = this.scrubbedModuleId(module.id) + '.xcframework';

							module.isFramework = false;

							// Try to load native module as static library (Obj-C)
							if (fs.existsSync(path.join(module.modulePath, 'lib' + module.id.toLowerCase() + '.a'))) {
								module.libName = 'lib' + module.id.toLowerCase() + '.a';
								module.libFile = path.join(module.modulePath, module.libName);
								module.isFramework = false;

								// For Obj-C static libraries, use the .a library or hashing
								this.legacyModules.add(module.id); // Record that this won't support macos or arm64 sim!
								nativeHashes.push(module.hash = this.hash(fs.readFileSync(module.libFile)));
								// Try to load native module as framework (Swift)
							} else if (fs.existsSync(path.join(module.modulePath, frameworkName))) {
								module.libName = frameworkName;
								module.libFile = path.join(module.modulePath, module.libName);
								module.isFramework = true;

								// For Swift frameworks, use the binary inside the .framework for hashing
								this.legacyModules.add(module.id); // Record that this won't support macos or arm64 sim!
								nativeHashes.push(module.hash = this.hash(fs.readFileSync(path.join(module.libFile, this.scrubbedModuleId(module.id)))));
							} else if (fs.existsSync(path.join(module.modulePath, xcFrameworkOfLib))) {
								module.libName = xcFrameworkOfLib;
								module.libFile = path.join(module.modulePath, module.libName);
								module.isFramework = true;

								const xcFrameworkInfo = plist.readFileSync(path.join(module.libFile, 'Info.plist'));
								for (const libInfo of xcFrameworkInfo.AvailableLibraries) {
									if (libInfo.SupportedPlatformVariant === undefined) {
										// Device library is used for hash calculation.
										// TODO: Probably we want to add other varient's library as well.
										nativeHashes.push(module.hash = this.hash(fs.readFileSync(path.join(module.libFile, libInfo.LibraryIdentifier,  'lib' + module.id.toLowerCase() + '.a'))));
									} else if (libInfo.SupportedPlatformVariant === 'simulator' && !libInfo.SupportedArchitectures.includes('arm64')) {
										this.legacyModules.add(module.id);// Record that this won't support arm64 sim!
									}
								}
							} else if (fs.existsSync(path.join(module.modulePath, xcFrameworkOfFramework))) {
								module.libName = xcFrameworkOfFramework;
								module.libFile = path.join(module.modulePath, module.libName);
								module.isFramework = true;

								const xcFrameworkInfo = plist.readFileSync(path.join(module.libFile, 'Info.plist'));
								const scrubbedModuleId = this.scrubbedModuleId(module.id);
								for (const libInfo of xcFrameworkInfo.AvailableLibraries) {
									if (libInfo.SupportedPlatformVariant === undefined) {
										// Device library is used for hash calculation.
										// TODO: Probably we want to add other varient's library as well.
										nativeHashes.push(module.hash = this.hash(fs.readFileSync(path.join(module.libFile, libInfo.LibraryIdentifier, scrubbedModuleId + '.framework', scrubbedModuleId))));
									} else if (libInfo.SupportedPlatformVariant === 'simulator' && !libInfo.SupportedArchitectures.includes('arm64')) {
										this.legacyModules.add(module.id);// Record that this won't support arm64 sim!
									}
								}
							} else {
								this.logger.error(__('Module %s (%s) is missing library or framework file.', module.id.cyan, (module.manifest.version || 'latest').cyan) + '\n');
								this.logger.error(__('Please validate that your module has been packaged correctly and try it again.'));
								process.exit(1);
							}

							this.nativeLibModules.push(module);
						}

						// scan the module for any CLI hooks
						cli.scanHooks(path.join(module.modulePath, 'hooks'));
					}, this);

					this.modulesNativeHash = this.hash(nativeHashes.length ? nativeHashes.sort().join(',') : '');

					next();
				}.bind(this));
			}
		], function (err) {
			if (err) {
				logger.error((err.message || err.toString()) + '\n');
				process.exit(1);
			}
			callback();
		});
	}.bind(this); // end of function returned by validate()
};

iOSBuilder.prototype.scrubbedModuleId = function (moduleId) {
	return moduleId.replace(/[\s-]/g, '_').replace(/_+/g, '_').split(/\./).map(function (s) {
		return s.substring(0, 1).toUpperCase() + s.substring(1);
	}).join('');
};

/**
 * Performs the build operations.
 *
 * @param {Object} logger - The logger instance.
 * @param {Object} config - The Titanium CLI config instance.
 * @param {Object} cli - The Titanium CLI instance.
 * @param {Function} finished - A function to call when the build has finished or errored.
 */
iOSBuilder.prototype.run = async function run(logger, config, cli, finished) {
	try {
		Builder.prototype.run.apply(this, arguments);

		// force the platform to "ios" just in case it was "iphone" so that plugins can reference it
		cli.argv.platform = 'ios';

		// Notify plugins that we're about to begin.
		await new Promise((resolve, reject) => {
			cli.emit('build.pre.construct', this, e => {
				if (e) {
					return reject(e);
				}
				resolve();
			});
		});

		// Post build anlytics.
		await this.doAnalytics();

		// Initialize build system. Checks if we need to do a clean or incremental build.
		await this.initialize(); // sets a lot of fields/properties
		await this.determineLogServerPort(); // needs to be run before loginfo and createXcodeProject (sets this.tiLogServerPort)
		await this.loginfo(); // spits out fields to CLI
		await this.readBuildManifest(); // sets this.previousBuildManifest and needs to be run before checkIfNeedToRecompile
		await this.checkIfNeedToRecompile(); // compares various properties/fields versus last build
		await this.initBuildDir(); // wipe or re-create build dir

		// Notify plugins that we're prepping to compile.
		await new Promise((resolve, reject) => {
			cli.emit('build.pre.compile', this, e => {
				if (e) {
					return reject(e);
				}
				resolve();
			});
		});

		// Make sure we have an "app.js" script. Will exit with a build failure if not found.
		// Note: This used to be validated by the validate() method, but Alloy plugin
		//       generates the "app.js" script via the "build.pre.compile" hook event above.
		ti.validateAppJsExists(this.projectDir, this.logger, [ 'iphone', 'ios' ]);

		// xcode related tasks
		// TODO: Can we use util.promisify(this.whatever).bind(this)()?
		// Or just bite the bullet and move from callbacks to async impls for each...
		await new Promise((resolve, reject) => {
			this.createXcodeProject(e => {
				if (e) {
					return reject(e);
				}
				resolve();
			});
		});
		await new Promise((resolve, reject) => {
			this.writeEntitlementsPlist(e => {
				if (e) {
					return reject(e);
				}
				resolve();
			});
		});
		this.writeInfoPlist();
		await this.writeMain();
		this.copyTitaniumiOSFiles();
		await new Promise((resolve, reject) => {
			this.copyExtensionFiles(e => {
				if (e) {
					return reject(e);
				}
				resolve();
			});
		});
		await new Promise((resolve, reject) => {
			this.cleanXcodeDerivedData(e => {
				if (e) {
					return reject(e);
				}
				resolve();
			});
		});

		// titanium related tasks
		this.writeDebugProfilePlists();
		await this.copyResources();
		await new Promise((resolve, reject) => {
			this.encryptJSFiles(e => {
				if (e) {
					return reject(e);
				}
				resolve();
			});
		});
		this.writeI18NFiles();
		this.processTiSymbols();

		// preprocessor macros writting
		this.writeXcodeConfigFiles();

		// cleanup and optimization
		await new Promise((resolve, reject) => {
			this.removeFiles(e => {
				if (e) {
					return reject(e);
				}
				resolve();
			});
		});
		await new Promise((resolve, reject) => {
			this.optimizeFiles(e => {
				if (e) {
					return reject(e);
				}
				resolve();
			});
		});

		// Build the app.
		// provide a hook event before xcodebuild
		await new Promise((resolve, reject) => {
			cli.emit('build.pre.build', this, e => {
				if (e) {
					return reject(e);
				}
				resolve();
			});
		});
		await this.generateRequireIndex(); // has to be run just before build (and after hook) so it gathers hyperloop generated JS files
		// build baby, build
		await new Promise((resolve, reject) => {
			this.invokeXcodeBuild(e => {
				if (e) {
					return reject(e);
				}
				resolve();
			});
		});
		// provide a hook event after xcodebuild
		await new Promise((resolve, reject) => {
			cli.emit('build.post.build', this, e => {
				if (e) {
					return reject(e);
				}
				resolve();
			});
		});

		// Write Titanium build settings to file. Used to determine if next build can be incremental or not.
		await new Promise((resolve, reject) => {
			this.writeBuildManifest(e => {
				if (e) {
					return reject(e);
				}
				resolve();
			});
		});

		// Log how long the build took.
		if (!this.buildOnly) {
			const delta = appc.time.prettyDiff(this.cli.startTime, Date.now());
			logger.info(__('Finished building the application in %s', delta.cyan));
		}

		// Notify plugins that the build is done.
		await new Promise((resolve, reject) => {
			cli.emit('build.post.compile', this, e => {
				if (e) {
					return reject(e);
				}
				resolve();
			});
		});
		await new Promise((resolve, reject) => {
			cli.emit('build.finalize', this, e => {
				if (e) {
					return reject(e);
				}
				resolve();
			});
		});
	} catch (err) {
		// TODO: call finished callback with error?
		// Failed to build app. Print the error message and stack trace (if possible), then exit out.
		// Note: "err" can be whatever type (including undefined) that was passed into Promise.reject().
		if (err instanceof Error) {
			this.logger.error(err.stack || err.message);
		} else if ((typeof err === 'string') && (err.length > 0)) {
			this.logger.error(err);
		} else {
			this.logger.error('Build failed. Reason: Unknown');
		}
		process.exit(1);
	}

	// We're done. Invoke optional callback if provided.
	if (finished) {
		finished();
	}
};

/**
 * Creates and adds the analytics event for the build.
 */
iOSBuilder.prototype.doAnalytics = async function doAnalytics() {
	const cli = this.cli;
	let eventName = this.deviceFamily + '.' + cli.argv.target;

	if (cli.argv.target === 'dist-appstore' || cli.argv.target === 'dist-adhoc') {
		eventName = this.deviceFamily + '.distribute.' + cli.argv.target.replace('dist-', '');
	} else if (this.allowDebugging && cli.argv['debug-host']) {
		eventName += '.debug';
	} else if (this.allowProfiling && cli.argv['profiler-host']) {
		eventName += '.profile';
	} else {
		eventName += '.run';
	}

	cli.addAnalyticsEvent(eventName, {
		name:        this.tiapp.name,
		publisher:   this.tiapp.publisher,
		url:         this.tiapp.url,
		image:       this.tiapp.icon,
		appid:       this.tiapp.id,
		description: this.tiapp.description,
		type:        cli.argv.type,
		guid:        this.tiapp.guid,
		version:     this.tiapp.version,
		copyright:   this.tiapp.copyright,
		date:        (new Date()).toDateString()
	});
};

iOSBuilder.prototype.initialize = async function initialize() {
	const argv = this.cli.argv;

	// populate the build manifest object
	this.currentBuildManifest.target            = this.target;
	this.currentBuildManifest.deployType        = this.deployType;
	this.currentBuildManifest.sdkVersion        = this.tiapp['sdk-version'];
	this.currentBuildManifest.iosSdkVersion     = this.iosSdkVersion;
	this.currentBuildManifest.deviceFamily      = this.deviceFamily;
	this.currentBuildManifest.iosSdkPath        = this.platformPath;
	this.currentBuildManifest.developerName     = this.certDeveloperName        = argv['developer-name'];
	this.currentBuildManifest.distributionName  = this.certDistributionName     = argv['distribution-name'];
	this.currentBuildManifest.modulesHash       = this.modulesHash              = this.hash(!Array.isArray(this.tiapp.modules) ? '' : this.tiapp.modules.filter(function (m) {
		return !m.platform || /^iphone|ipad|ios|commonjs$/.test(m.platform);
	}).map(function (m) {
		return m.id + ',' + m.platform + ',' + m.version;
	}).join('|'));
	this.currentBuildManifest.modulesNativeHash  = this.modulesNativeHash;
	this.currentBuildManifest.gitHash            = ti.manifest.githash;
	this.currentBuildManifest.ppUuid             = this.provisioningProfileUUID;
	this.currentBuildManifest.outputDir          = this.cli.argv['output-dir'];
	this.currentBuildManifest.forceCopy          = this.forceCopy               = !!argv['force-copy'];
	this.currentBuildManifest.name               = this.tiapp.name;
	this.currentBuildManifest.id                 = this.tiapp.id;
	this.currentBuildManifest.analytics          = this.tiapp.analytics;
	this.currentBuildManifest.publisher          = this.tiapp.publisher;
	this.currentBuildManifest.url                = this.tiapp.url;
	this.currentBuildManifest.version            = this.tiapp.version;
	this.currentBuildManifest.description        = this.tiapp.description;
	this.currentBuildManifest.copyright          = this.tiapp.copyright;
	this.currentBuildManifest.guid               = this.tiapp.guid;
	this.currentBuildManifest.useAppThinning     = this.useAppThinning = this.tiapp.ios['use-app-thinning'] === true;
	this.currentBuildManifest.skipJSMinification = !!this.cli.argv['skip-js-minify'];
	this.currentBuildManifest.encryptJS          = !!this.encryptJS;
	this.currentBuildManifest.simOnlyActiveArch  = this.simOnlyActiveArch;
	this.currentBuildManifest.showErrorController = this.showErrorController;

	this.currentBuildManifest.useJSCore = this.useJSCore = !this.debugHost && !this.profilerHost && this.tiapp.ios['use-jscore-framework'] !== false;

	// use Auto Layout if enabled via tiapp.xml
	this.currentBuildManifest.useAutoLayout = this.useAutoLayout = this.tiapp.ios && (this.tiapp.ios['use-autolayout'] === true);

	this.moduleSearchPaths = [ this.projectDir, appc.fs.resolvePath(this.platformPath, '..', '..', '..', '..') ];
	if (this.config.paths && Array.isArray(this.config.paths.modules)) {
		this.moduleSearchPaths = this.moduleSearchPaths.concat(this.config.paths.modules);
	}

	this.debugHost     = this.allowDebugging && argv['debug-host'];
	this.profilerHost  = this.allowProfiling && argv['profiler-host'];
	this.buildOnly     = argv['build-only'];
	this.launchUrl     = argv['launch-url'];
	this.keychain      = argv['keychain'];
	this.deviceId      = argv['device-id'];
	this.deviceInfo    = this.deviceId ? this.getDeviceInfo().udids[this.deviceId] : null;
	this.xcodeTarget   = /^device|simulator|macos$/.test(this.target) ? 'Debug' : 'Release';

	if (this.target === 'simulator') {
		this.xcodeTargetOS = 'iphonesimulator';
	} else {
		this.xcodeTargetOS = 'iphoneos';
	}

	let osName = this.xcodeTargetOS;
	if (this.target === 'macos' || this.target === 'dist-macappstore') {
		osName = 'maccatalyst';
	}
	const xcodeProductName = `${this.xcodeTarget}-${osName}`;

	this.iosBuildDir            = path.join(this.buildDir, 'build', 'Products', xcodeProductName);
	if (this.target === 'dist-appstore' || this.target === 'dist-adhoc' || this.target === 'dist-macappstore') {
		this.xcodeAppDir        = path.join(this.buildDir, 'ArchiveStaging');
	} else if (this.target === 'macos') {
		this.xcodeAppDir        = path.join(this.iosBuildDir, this.tiapp.name + '.app/Contents/Resources');
	} else {
		this.xcodeAppDir        = path.join(this.iosBuildDir, this.tiapp.name + '.app');
	}
	this.xcodeProjectConfigFile = path.join(this.buildDir, 'project.xcconfig');
	this.buildAssetsDir         = path.join(this.buildDir, 'assets');
	this.buildManifestFile      = path.join(this.buildDir, 'build-manifest.json');

	if ((this.tiapp.properties && this.tiapp.properties['ios.whitelist.appcelerator.com'] && this.tiapp.properties['ios.whitelist.appcelerator.com'].value === false) || !this.tiapp.analytics) {
		// force appcelerator.com to not be whitelisted in the Info.plist ATS section
		this.whitelistAppceleratorDotCom = false;
	}

	if (!this.tiapp.ios['enable-launch-screen-storyboard'] || appc.version.lt(this.xcodeEnv.version, '7.0.0')) {
		this.enableLaunchScreenStoryboard = false;
		this.defaultLaunchScreenStoryboard = false;
	}

	if (!Object.prototype.hasOwnProperty.call(this.tiapp.ios, 'use-new-build-system') && appc.version.lt(this.xcodeEnv.version, '10.0.0')) {
		// if running on Xcode < 10, do not use the new build system by default
		this.useNewBuildSystem = false;
	} else if (Object.prototype.hasOwnProperty.call(this.tiapp.ios, 'use-new-build-system')) {
		// if explicitly set via tiapp.xml, go with that one
		this.useNewBuildSystem = this.tiapp.ios['use-new-build-system'];
	} else {
		// if not set and Xcode >= 10, use the new build system
		this.useNewBuildSystem = true;
	}

	this.currentBuildManifest.useNewBuildSystem = this.useNewBuildSystem;

	if (this.enableLaunchScreenStoryboard && (fs.existsSync(path.join(this.projectDir, 'platform', 'ios', 'LaunchScreen.storyboard')) || fs.existsSync(path.join(this.projectDir, 'platform', 'iphone', 'LaunchScreen.storyboard')))) {
		this.defaultLaunchScreenStoryboard = false;
	}

	const defaultColor = this.defaultLaunchScreenStoryboard ? 'ffffff' : null,
		color = this.tiapp.ios['default-background-color'] || defaultColor;
	if (color) {
		const m = color.match(/^#?([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/);
		let c = m && m[1];
		if (c && (c.length === 3 || c.length === 6)) {
			if (c.length === 3) {
				c = c
					.split('')
					.map(function (b) {
						return String(b) + String(b);
					}).join('');
			}
			this.defaultBackgroundColor = {
				red: parseInt(c.substr(0, 2), 16) / 255,
				green: parseInt(c.substr(2, 2), 16) / 255,
				blue: parseInt(c.substr(4, 2), 16) / 255
			};
		} else {
			this.logger.warn(__('Invalid default background color "%s" in the <ios> section of the tiapp.xml', color));
			if (defaultColor) {
				this.logger.warn(__('Using default background color "%s"', '#' + defaultColor));
			}
		}
	}
};

iOSBuilder.prototype.findProvisioningProfile = function findProvisioningProfile(target, uuid) {
	const provisioning = this.iosInfo.provisioning;

	function getPP(type, uuid) {
		const list = provisioning[type];
		for (let i = 0; i < list.length; i++) {
			if (list[i].uuid === uuid) {
				list[i].getTaskAllow = !!list[i].getTaskAllow;
				list[i].type = type;
				return list[i];
			}
		}
	}

	switch (target) {
		case 'device':
			return getPP('development', uuid);
		case 'dist-appstore':
			return getPP('distribution', uuid);
		case 'dist-adhoc':
			return getPP('adhoc', uuid) || getPP('enterprise', uuid);
	}
};

iOSBuilder.prototype.determineLogServerPort = async function determineLogServerPort() {
	this.tiLogServerPort = 0;

	// We only turn the log server on for a device
	if (this.target !== 'device') {
		// we don't allow the log server in production
		return;
	}

	// if there's not an explicit <log-server-port> in the <ios> section of
	// the tiapp.xml, then we pick a port between 10000 and 60000 based on
	// the app's id. this is VERY prone to collisions, but we will show an
	// error if two different apps have been assigned the same port.
	this.tiLogServerPort = this.tiapp.ios['log-server-port'] || (parseInt(sha1(this.tiapp.id), 16) % 50000 + 10000);
};

iOSBuilder.prototype.loginfo = async function loginfo() {
	this.logger.debug(__('Titanium SDK iOS directory: %s', cyan(this.platformPath)));
	this.logger.info(__('Deploy type: %s', cyan(this.deployType)));
	this.logger.info(__('Building for target: %s', cyan(this.target)));
	this.logger.info(__('Building using iOS SDK: %s', cyan(version.format(this.iosSdkVersion, 2))));

	if (this.buildOnly) {
		this.logger.info(__('Performing build only'));
	} else if (this.target === 'simulator') {
		this.logger.info(__('Building for iOS Simulator: %s', cyan(this.simHandle.name)));
		this.logger.debug(__('UDID: %s', cyan(this.simHandle.udid)));
		this.logger.debug(__('Simulator type: %s', cyan(this.simHandle.family)));
		this.logger.debug(__('Simulator version: %s', cyan(this.simHandle.version)));
	} else if (this.target === 'device') {
		this.logger.info(__('Building for iOS device: %s', cyan(this.deviceId)));
	} else if (this.target === 'macos') {
		this.logger.info(__('Building for maccatalyst'));
	}

	this.logger.info(__('Building for device family: %s', cyan(this.deviceFamily)));
	this.logger.debug(__('Setting Xcode target to %s', cyan(this.xcodeTarget)));
	this.logger.debug(__('Setting Xcode build OS to %s', cyan(this.xcodeTargetOS)));
	this.logger.debug(__('Xcode installation: %s', cyan(this.xcodeEnv.path)));
	this.logger.debug(__('iOS WWDR certificate: %s', cyan(this.iosInfo.certs.wwdr ? __('installed') : __('not found'))));

	if (this.target === 'device') {
		this.logger.info(__('iOS Development Certificate: %s', cyan(this.certDeveloperName)));
	} else if (/^dist-appstore|dist-adhoc$/.test(this.target)) {
		this.logger.info(__('iOS Distribution Certificate: %s', cyan(this.certDistributionName)));
	}
	this.logger.info(__('Team ID: %s', this.teamId ? cyan(this.teamId) : 'n/a'.grey));

	// validate the min-ios-ver from the tiapp.xml
	this.logger.info(__('Minimum iOS version: %s', cyan(version.format(this.minIosVer, 2, 3))));

	if (/^device|dist-appstore|dist-adhoc$/.test(this.target)) {
		if (this.keychain) {
			this.logger.info(__('Using keychain: %s', cyan(this.keychain)));
		} else {
			this.logger.info(__('Using default keychain'));
		}
	}

	if (this.tiLogServerPort) {
		this.logger.info(__('Logging enabled on port %s', cyan(String(this.tiLogServerPort))));
	} else {
		this.logger.info(__('Logging disabled'));
	}

	if (this.debugHost) {
		this.logger.info(__('Debugging enabled via debug host: %s', cyan(this.debugHost)));
	} else {
		this.logger.info(__('Debugging disabled'));
	}

	if (this.profilerHost) {
		this.logger.info(__('Profiler enabled via profiler host: %s', cyan(this.profilerHost)));
	} else {
		this.logger.info(__('Profiler disabled'));
	}

	if (this.symlinkFilesOnCopy) {
		this.logger.info(__('Set to symlink files instead of copying'));
	} else {
		this.logger.info(__('Set to copy files instead of symlinking'));
	}

	this.logger.info(__('Transpile javascript: %s', (this.transpile ? 'true' : 'false').cyan));
	this.logger.info(__('Generate source maps: %s', (this.sourceMaps ? 'true' : 'false').cyan));
};

iOSBuilder.prototype.readBuildManifest = async function readBuildManifest() {
	// read the build manifest from the last build, if exists, so we
	// can determine if we need to do a full rebuild
	if (await fs.exists(this.buildManifestFile)) {
		try {
			this.previousBuildManifest = (await fs.readJSON(this.buildManifestFile)) || {};
		} catch (e) {
			// ignore
		}
		// now that we've read the build manifest, delete it so if this build
		// becomes incomplete, the next build will be a full rebuild
		await fs.unlink(this.buildManifestFile);
	}

	this.unmarkBuildDirFile(this.buildManifestFile);
};

iOSBuilder.prototype.checkIfNeedToRecompile = async function checkIfNeedToRecompile() {
	const manifest = this.previousBuildManifest;

	// check if we need to clean the build directory
	this.forceCleanBuild = function () {
		// check if the --force flag was passed in
		if (this.cli.argv.force) {
			this.logger.info(__('Forcing clean build: %s flag was set', cyan('--force')));
			return true;
		}

		// check if the build manifest file was read
		if (!Object.keys(this.previousBuildManifest).length) {
			this.logger.info(__('Forcing clean build: %s does not exist', cyan(this.buildManifestFile)));
			return true;
		}

		// check the <sdk-version> from the tiapp.xml
		if (!appc.version.eq(this.tiapp['sdk-version'], manifest.sdkVersion)) {
			this.logger.info(__('Forcing rebuild: tiapp.xml Titanium SDK version changed since last build'));
			this.logger.info('  ' + __('Was: %s', cyan(manifest.sdkVersion)));
			this.logger.info('  ' + __('Now: %s', cyan(this.tiapp['sdk-version'])));
			return true;
		}

		// check if the deployType changed
		if (this.deployType !== manifest.deployType) {
			this.logger.info(__('Forcing rebuild: deployType changed since last build'));
			this.logger.info('  ' + __('Was: %s', manifest.deployType));
			this.logger.info('  ' + __('Now: %s', this.deployType));
			return true;
		}

		// check if the titanium sdk version changed
		if (fs.existsSync(this.xcodeProjectConfigFile)) {
			// we have a previous build, see if the Titanium SDK changed
			const conf = fs.readFileSync(this.xcodeProjectConfigFile).toString(),
				versionMatch = conf.match(/TI_VERSION=([^\n]*)/);

			if (versionMatch && !appc.version.eq(versionMatch[1], this.titaniumSdkVersion)) {
				this.logger.info(__('Forcing rebuild: Titanium SDK version in the project.xcconfig changed since last build'));
				this.logger.info('  ' + __('Was: %s', cyan(versionMatch[1])));
				this.logger.info('  ' + __('Now: %s', cyan(this.titaniumSdkVersion)));
				return true;
			}
		}

		if (this.certDeveloperName !== manifest.developerName) {
			this.logger.info(__('Forcing rebuild: developerName changed since last build'));
			this.logger.info('  ' + __('Was: %s', manifest.developerName));
			this.logger.info('  ' + __('Now: %s', this.certDeveloperName));
			return true;
		}

		return false;
	}.call(this);

	// if true, this will cause xcodebuild to be called
	// if false, it's possible that other steps after this will force xcodebuild to be called
	this.forceRebuild = this.forceCleanBuild || function () {
		// check if the xcode app directory exists
		if (!fs.existsSync(this.xcodeAppDir)) {
			this.logger.info(__('Forcing rebuild: %s does not exist', cyan(this.xcodeAppDir)));
			return true;
		}

		// check if the --force-copy flag was set
		if (this.forceCopy !== manifest.forceCopy) {
			this.logger.info(__('Forcing rebuild: force copy flag changed since last build'));
			this.logger.info('  ' + __('Was: %s', cyan(manifest.forceCopy)));
			this.logger.info('  ' + __('Now: %s', cyan(this.forceCopy)));
			return true;
		}

		// check if the target changed
		if (this.target !== manifest.target) {
			this.logger.info(__('Forcing rebuild: target changed since last build'));
			this.logger.info('  ' + __('Was: %s', cyan(manifest.target)));
			this.logger.info('  ' + __('Now: %s', cyan(this.target)));
			return true;
		}

		if (this.target === 'dist-adhoc' || this.target === 'dist-appstore') {
			this.logger.info(__('Forcing rebuild: distribution builds require \'xcodebuild\' to be run so that resources are copied into the archive'));
			return true;
		}

		if (fs.existsSync(this.xcodeProjectConfigFile)) {
			// we have a previous build, see if the app id changed
			const conf = fs.readFileSync(this.xcodeProjectConfigFile).toString(),
				idMatch = conf.match(/TI_APPID=([^\n]*)/);

			if (idMatch && idMatch[1] !== this.tiapp.id) {
				this.logger.info(__('Forcing rebuild: app id changed since last build'));
				this.logger.info('  ' + __('Was: %s', cyan(idMatch[1])));
				this.logger.info('  ' + __('Now: %s', cyan(this.tiapp.id)));
				return true;
			}
		}

		// check if the titanium sdk paths are different
		if (manifest.iosSdkPath !== this.platformPath) {
			this.logger.info(__('Forcing rebuild: Titanium SDK path changed since last build'));
			this.logger.info('  ' + __('Was: %s', cyan(manifest.iosSdkPath)));
			this.logger.info('  ' + __('Now: %s', cyan(this.platformPath)));
			return true;
		}

		// check if the iOS SDK has changed
		if (manifest.iosSdkVersion !== this.iosSdkVersion) {
			this.logger.info(__('Forcing rebuild: iOS SDK version changed since last build'));
			this.logger.info('  ' + __('Was: %s', cyan(manifest.iosSdkVersion)));
			this.logger.info('  ' + __('Now: %s', cyan(this.iosSdkVersion)));
			return true;
		}

		// check if the device family has changed (i.e. was universal, now iphone)
		if (manifest.deviceFamily !== this.deviceFamily) {
			this.logger.info(__('Forcing rebuild: device family changed since last build'));
			this.logger.info('  ' + __('Was: %s', cyan(manifest.deviceFamily)));
			this.logger.info('  ' + __('Now: %s', cyan(this.deviceFamily)));
			return true;
		}

		// check the git hashes are different
		if (!manifest.gitHash || manifest.gitHash !== ti.manifest.githash) {
			this.logger.info(__('Forcing rebuild: githash changed since last build'));
			this.logger.info('  ' + __('Was: %s', cyan(manifest.gitHash)));
			this.logger.info('  ' + __('Now: %s', cyan(ti.manifest.githash)));
			return true;
		}

		// determine the modules hash and check if the modules hashes has changed
		if (this.modulesHash !== manifest.modulesHash) {
			this.logger.info(__('Forcing rebuild: modules hash changed since last build'));
			this.logger.info('  ' + __('Was: %s', cyan(manifest.modulesHash)));
			this.logger.info('  ' + __('Now: %s', cyan(this.modulesHash)));
			return true;
		}

		// check if the native modules hashes has changed
		if (this.modulesNativeHash !== manifest.modulesNativeHash) {
			this.logger.info(__('Forcing rebuild: native modules hash changed since last build'));
			this.logger.info('  ' + __('Was: %s', manifest.modulesNativeHash));
			this.logger.info('  ' + __('Now: %s', this.modulesNativeHash));
			return true;
		}

		// check if the provisioning profile has changed
		if (this.provisioningProfileUUID !== manifest.ppUuid) {
			this.logger.info(__('Forcing rebuild: provisioning profile changed since last build'));
			this.logger.info('  ' + __('Was: %s', manifest.ppUuid));
			this.logger.info('  ' + __('Now: %s', this.provisioningProfileUUID));
			return true;
		}

		// check if the use UserAutoLayout flag has changed
		if (this.useAutoLayout !== manifest.useAutoLayout) {
			this.logger.info(__('Forcing rebuild: use-autolayout flag changed since last build'));
			this.logger.info('  ' + __('Was: %s', manifest.useAutoLayout));
			this.logger.info('  ' + __('Now: %s', this.useAutoLayout));
			return true;
		}

		// check if the use use-app-thinning flag has changed
		if (this.useAppThinning !== manifest.useAppThinning) {
			this.logger.info(__('Forcing rebuild: use-app-thinning flag changed since last build'));
			this.logger.info('  ' + __('Was: %s', manifest.useAppThinning));
			this.logger.info('  ' + __('Now: %s', this.useAppThinning));
			return true;
		}

		// check if the use use-new-build-system flag has changed
		if (this.useNewBuildSystem !== manifest.useNewBuildSystem) {
			this.logger.info(__('Forcing rebuild: use-new-build-system flag changed since last build'));
			this.logger.info('  ' + __('Was: %s', manifest.useNewBuildSystem));
			this.logger.info('  ' + __('Now: %s', this.useNewBuildSystem));
			return true;
		}

		// check if the showErrorController flag has changed
		if (this.showErrorController !== manifest.showErrorController) {
			this.logger.info(__('Forcing rebuild: showErrorController flag changed since last build'));
			this.logger.info('  ' + __('Was: %s', manifest.showErrorController));
			this.logger.info('  ' + __('Now: %s', this.showErrorController));
			return true;
		}

		if (this.simOnlyActiveArch !== manifest.simOnlyActiveArch) {
			this.logger.info(__('Forcing rebuild: simOnlyActiveArch flag changed since last build'));
			this.logger.info('  ' + __('Was: %s', manifest.simOnlyActiveArch));
			this.logger.info('  ' + __('Now: %s', this.simOnlyActiveArch));
			return true;
		}

		// next we check if any tiapp.xml values changed so we know if we need to reconstruct the main.m
		// note: as soon as these tiapp.xml settings are written to an encrypted file instead of the binary, we can remove this whole section
		const tiappSettings = {
			name:        'project name',
			id:          'app id',
			analytics:   'analytics flag',
			publisher:   'publisher',
			url:         'url',
			version:     'version',
			description: 'description',
			copyright:   'copyright',
			guid:        'guid'
		};

		const changed = Object.keys(tiappSettings).find(key => this.tiapp[key] !== manifest[key], this);
		if (changed) {
			this.logger.info(__('Forcing rebuild: tiapp.xml %s changed since last build', tiappSettings[changed]));
			this.logger.info('  ' + __('Was: %s', cyan(manifest[changed])));
			this.logger.info('  ' + __('Now: %s', cyan(this.tiapp[changed])));
			return true;
		}

		return false;
	}.call(this);
};

iOSBuilder.prototype.initBuildDir = async function initBuildDir() {
	this.logger.info(__('Initializing the build directory'));

	const buildDirExists = await fs.exists(this.buildDir);

	if (this.forceCleanBuild && buildDirExists) {
		this.logger.debug(__('Recreating %s', cyan(this.buildDir)));
		await fs.emptyDir(this.buildDir);
	} else if (!buildDirExists) {
		this.logger.debug(__('Creating %s', cyan(this.buildDir)));
		await fs.ensureDir(this.buildDir);
		this.forceCleanBuild = true;
	}

	return fs.ensureDir(this.xcodeAppDir);
};

iOSBuilder.prototype.generateXcodeUuid = function generateXcodeUuid(xcodeProject) {
	// normally we would want truly unique ids, but we want predictability so that we
	// can detect when the project has changed and if we need to rebuild the app
	if (!this.xcodeUuidIndex) {
		this.xcodeUuidIndex = 1;
	}
	const id = appc.string.lpad(this.xcodeUuidIndex++, 24, '0');
	if (xcodeProject && xcodeProject.allUuids().indexOf(id) >= 0) {
		return this.generateXcodeUuid(xcodeProject);
	} else {
		return id;
	}
};

// FIXME: Make async and not use callback!
iOSBuilder.prototype.createXcodeProject = function createXcodeProject(next) {
	this.logger.info(__('Creating Xcode project'));

	const appName = this.tiapp.name;
	const scrubbedAppName = appName.replace(/[-\W]/g, '_');
	const srcFile = path.join(this.platformPath, 'iphone', 'Titanium.xcodeproj', 'project.pbxproj');
	const xcodeProject = xcode.project(path.join(this.buildDir, this.tiapp.name + '.xcodeproj', 'project.pbxproj'));
	const relPathRegExp = /\.\.\/(Classes|Resources|headers|lib)/;
	const contents = fs.readFileSync(srcFile).toString();

	xcodeProject.hash = xcodeParser.parse(contents);
	const xobjs = xcodeProject.hash.project.objects;

	if (appc.version.lt(this.xcodeEnv.version, '7.0.0')) {
		this.logger.info(__('LaunchScreen.storyboard is not supported with Xcode %s, removing from Xcode project', this.xcodeEnv.version));
	}

	function removeFromBuildPhase(phase, id) {
		if (phase) {
			Object.keys(phase).some(function (phaseId) {
				const files = phase[phaseId].files;
				if (Array.isArray(files)) {
					for (let i = 0; i < files.length; i++) {
						if (files[i].value === id) {
							files.splice(i, 1);
							return true;
						}
					}
				}
				return false;
			});
		}
	}

	// we need to replace all instances of "Titanium" with the app name
	Object.keys(xobjs.PBXFileReference).forEach(function (id) {
		var obj = xobjs.PBXFileReference[id];
		if (obj && typeof obj === 'object') {
			if (obj.path === 'Titanium_Prefix.pch') {
				obj.path = xobjs.PBXFileReference[id + '_comment'] = scrubbedAppName + '_Prefix.pch';
			} else if (obj.path === 'Titanium.plist') {
				obj.path = xobjs.PBXFileReference[id + '_comment'] = 'Info.plist';
			} else if (obj.path === 'Titanium.entitlements') {
				obj.path = xobjs.PBXFileReference[id + '_comment'] = '"' + appName + '.entitlements"';
			} else if (obj.path === 'Titanium.app') {
				obj.path = xobjs.PBXFileReference[id + '_comment'] = '"' + appName + '.app"';
			}	else if (obj.path === '"Titanium-Bridging-Header.h"') {
				obj.path = xobjs.PBXFileReference[id + '_comment'] = `"${scrubbedAppName}-Bridging-Header.h"`;
			} else if (relPathRegExp.test(obj.path)) {
				obj.path = obj.path.replace(relPathRegExp, '$1');
			} else if (obj.path === 'LaunchScreen.storyboard' && appc.version.lt(this.xcodeEnv.version, '7.0.0')) {
				delete xobjs.PBXFileReference[id];

				// remove the LaunchScreen.storyboard BuildFile and BuildPhase records
				Object.keys(xobjs.PBXBuildFile).some(function (bfid) {
					if (typeof xobjs.PBXBuildFile[bfid] === 'object' && xobjs.PBXBuildFile[bfid].fileRef === id) {
						delete xobjs.PBXBuildFile[bfid];
						delete xobjs.PBXBuildFile[bfid + '_comment'];

						removeFromBuildPhase(xobjs.PBXResourcesBuildPhase, bfid);
						removeFromBuildPhase(xobjs.PBXFrameworksBuildPhase, bfid);

						return true;
					}
					return false;
				});
			}
		}
	}, this);

	Object.keys(xobjs.PBXGroup).forEach(function (id) {
		const obj = xobjs.PBXGroup[id];
		if (obj && typeof obj === 'object') {
			if (obj.children) {
				for (let i = 0; i < obj.children.length; i++) {
					const child = obj.children[i];
					if (child.comment === 'Titanium_Prefix.pch') {
						child.comment = scrubbedAppName + '_Prefix.pch';
					} else if (child.comment === 'Titanium.plist') {
						child.comment = 'Info.plist';
					} else if (child.comment === 'Titanium.app') {
						child.comment = '"' + appName + '.app"';
					} else if (child.comment === 'Titanium.entitlements') {
						child.comment = '"' + appName + '.entitlements"';
					} else if (child.comment === 'LaunchScreen.storyboard' && appc.version.lt(this.xcodeEnv.version, '7.0.0')) {
						obj.children.splice(i--, 1);
					} else if (child.comment === 'Titanium-Bridging-Header.h') {
						child.comment = '"' + scrubbedAppName + '-Bridging-Header.h"';
					}
				}
			}
			if (obj.path && relPathRegExp.test(obj.path)) {
				obj.path = obj.path.replace(relPathRegExp, '$1');
			}
		}
	}, this);

	Object.keys(xobjs.PBXNativeTarget).forEach(function (id) {
		const obj = xobjs.PBXNativeTarget[id];
		if (obj && typeof obj === 'object') {
			Object.keys(obj).forEach(function (key) {
				if (obj[key] && typeof obj[key] === 'string' && obj[key].indexOf('Titanium') !== -1) {
					obj[key] = xobjs.PBXNativeTarget[id + '_comment'] = '"' + obj[key].replace(/Titanium/g, appName).replace(/^"/, '').replace(/"$/, '') + '"';
				}
			});
		}
	});

	Object.keys(xobjs.PBXProject).forEach(function (id) {
		const obj = xobjs.PBXProject[id];
		if (obj && typeof obj === 'object') {
			obj.buildConfigurationList_comment = '"' + obj.buildConfigurationList_comment.replace(/Titanium/g, appName).replace(/^"/, '').replace(/"$/, '') + '"';
			obj.targets.forEach(function (item) {
				item.comment = '"' + item.comment.replace(/Titanium/g, appName).replace(/^"/, '').replace(/"$/, '') + '"';
			});
		}
	});

	Object.keys(xobjs.XCBuildConfiguration).forEach(function (id) {
		const obj = xobjs.XCBuildConfiguration[id];
		if (obj && typeof obj === 'object' && obj.buildSettings) {
			if (obj.buildSettings.GCC_PREFIX_HEADER === 'Titanium_Prefix.pch') {
				obj.buildSettings.GCC_PREFIX_HEADER = scrubbedAppName + '_Prefix.pch';
			}
			if (obj.buildSettings.INFOPLIST_FILE === 'Titanium.plist') {
				obj.buildSettings.INFOPLIST_FILE = 'Info.plist';
			}
			if (obj.buildSettings.PRODUCT_NAME === 'Titanium') {
				obj.buildSettings.PRODUCT_NAME = '"' + appName + '"';
			}
			if (obj.buildSettings.SWIFT_OBJC_BRIDGING_HEADER === '"Titanium-Bridging-Header.h"') {
				obj.buildSettings.SWIFT_OBJC_BRIDGING_HEADER = `"${scrubbedAppName}-Bridging-Header.h"`;
			}
			if (Array.isArray(obj.buildSettings.LIBRARY_SEARCH_PATHS)) {
				obj.buildSettings.LIBRARY_SEARCH_PATHS.forEach(function (item, i, arr) {
					arr[i] = item.replace(relPathRegExp, '$1');
				});
			}
			if (Array.isArray(obj.buildSettings.HEADER_SEARCH_PATHS)) {
				obj.buildSettings.HEADER_SEARCH_PATHS.forEach(function (item, i, arr) {
					arr[i] = item.replace(relPathRegExp, '$1');
				});
			}
		}
	});

	Object.keys(xobjs.XCConfigurationList).forEach(function (id) {
		if (xobjs.XCConfigurationList[id] && typeof xobjs.XCConfigurationList[id] === 'string') {
			xobjs.XCConfigurationList[id] = xobjs.XCConfigurationList[id].replace(/Titanium/g, appName);
		}
	});

	// delete the pre-compile build phases since we don't need it
	this.logger.trace(__('Removing pre-compile phase'));
	Object.keys(xobjs.PBXShellScriptBuildPhase).forEach(function (buildPhaseUuid) {
		if (xobjs.PBXShellScriptBuildPhase[buildPhaseUuid] && typeof xobjs.PBXShellScriptBuildPhase[buildPhaseUuid] === 'object' && /^"?Pre-Compile"?$/i.test(xobjs.PBXShellScriptBuildPhase[buildPhaseUuid].name)) {
			Object.keys(xobjs.PBXNativeTarget).forEach(function (key) {
				if (xobjs.PBXNativeTarget[key] && typeof xobjs.PBXNativeTarget[key] === 'object') {
					xobjs.PBXNativeTarget[key].buildPhases = xobjs.PBXNativeTarget[key].buildPhases.filter(function (phase) {
						return phase.value !== buildPhaseUuid;
					});
				}
			});
			delete xobjs.PBXShellScriptBuildPhase[buildPhaseUuid];
			delete xobjs.PBXShellScriptBuildPhase[buildPhaseUuid + '_comment'];
		}
	});

	const projectUuid = xcodeProject.hash.project.rootObject,
		pbxProject = xobjs.PBXProject[projectUuid],
		mainTargetUuid = pbxProject.targets.filter(function (t) { return t.comment.replace(/^"/, '').replace(/"$/, '') === appName; })[0].value,
		mainGroupChildren = xobjs.PBXGroup[pbxProject.mainGroup].children,
		buildPhases = xobjs.PBXNativeTarget[mainTargetUuid].buildPhases,
		extensionsGroup = xobjs.PBXGroup[mainGroupChildren.filter(function (child) { return child.comment === 'Extensions'; })[0].value],
		frameworksGroup = xobjs.PBXGroup[mainGroupChildren.filter(function (child) { return child.comment === 'Frameworks'; })[0].value],
		resourcesGroup = xobjs.PBXGroup[mainGroupChildren.filter(function (child) { return child.comment === 'Resources'; })[0].value],
		productsGroup = xobjs.PBXGroup[mainGroupChildren.filter(function (child) { return child.comment === 'Products'; })[0].value],
		// we lazily find the frameworks and embed frameworks uuids by working our way backwards so we don't have to compare comments
		frameworksBuildPhase = xobjs.PBXFrameworksBuildPhase[
			buildPhases.filter(phase => xobjs.PBXFrameworksBuildPhase[phase.value])[0].value
		],
		copyFilesBuildPhase = xobjs.PBXCopyFilesBuildPhase[
			buildPhases.filter(phase => xobjs.PBXCopyFilesBuildPhase[phase.value])[0].value
		],
		resourcesBuildPhase = xobjs.PBXResourcesBuildPhase[xobjs.PBXNativeTarget[mainTargetUuid].buildPhases.filter(function (phase) { return xobjs.PBXResourcesBuildPhase[phase.value]; })[0].value],
		caps = this.tiapp.ios.capabilities,
		buildSettings = {
			IPHONEOS_DEPLOYMENT_TARGET: appc.version.format(this.minIosVer, 2),
			TARGETED_DEVICE_FAMILY: '"' + this.deviceFamilies[this.deviceFamily] + '"',
			ONLY_ACTIVE_ARCH: 'NO',
			DEAD_CODE_STRIPPING: 'YES',
			SDKROOT: 'iphoneos',
			CODE_SIGN_ENTITLEMENTS: '"' + appName + '.entitlements"',
			FRAMEWORK_SEARCH_PATHS: [ '"$(inherited)"', '"Frameworks"' ]
		},
		legacySwift = version.lt(this.xcodeEnv.version, '8.0.0');

	this.gccDefs.set('DEPLOYTYPE', this.deployType);
	// set additional build settings
	if (this.target === 'simulator' || this.target === 'macos') {
		this.gccDefs.set('__LOG__ID__', this.tiapp.guid);
		this.gccDefs.set('DEBUG', 1);
	}

	if (this.enableLaunchScreenStoryboard) {
		this.gccDefs.set('LAUNCHSCREEN_STORYBOARD', 1);
	}

	if (this.defaultBackgroundColor) {
		this.gccDefs.set('DEFAULT_BGCOLOR_RED', this.defaultBackgroundColor.red);
		this.gccDefs.set('DEFAULT_BGCOLOR_GREEN', this.defaultBackgroundColor.green);
		this.gccDefs.set('DEFAULT_BGCOLOR_BLUE', this.defaultBackgroundColor.blue);
	}

	if (this.tiLogServerPort === 0) {
		this.gccDefs.set('DISABLE_TI_LOG_SERVER', 1);
	} else {
		this.gccDefs.set('TI_LOG_SERVER_PORT', this.tiLogServerPort);
	}

	if (/device|dist-appstore|dist-adhoc/.test(this.target)) {
		buildSettings.DEPLOYMENT_POSTPROCESSING = 'YES';
		if (this.keychain) {
			buildSettings.OTHER_CODE_SIGN_FLAGS = '"--keychain ' + this.keychain + '"';
		}
	}

	// add the post-compile build phase for dist-appstore builds
	if (this.target === 'dist-appstore' || this.target === 'dist-adhoc') {
		buildSettings.CODE_SIGN_IDENTITY = `"${this.certDistributionName}"`;
		buildSettings.CODE_SIGN_STYLE = 'Manual';

		xobjs.PBXShellScriptBuildPhase || (xobjs.PBXShellScriptBuildPhase = {});
		const buildPhaseUuid = this.generateXcodeUuid(xcodeProject);
		const name = 'Copy Resources to Archive';

		xobjs.PBXNativeTarget[mainTargetUuid].buildPhases.push({
			value: buildPhaseUuid,
			comment: '"' + name + '"'
		});

		xobjs.PBXShellScriptBuildPhase[buildPhaseUuid] = {
			isa: 'PBXShellScriptBuildPhase',
			buildActionMask: 2147483647,
			files: [],
			inputPaths: [],
			name: '"' + name + '"',
			outputPaths: [],
			runOnlyForDeploymentPostprocessing: 0,
			shellPath: '/bin/sh',
			shellScript: '"/bin/cp -rf \\"$PROJECT_DIR/ArchiveStaging\\"/ \\"$TARGET_BUILD_DIR/$PRODUCT_NAME.app/\\""',
			showEnvVarsInLog: 0
		};
		xobjs.PBXShellScriptBuildPhase[buildPhaseUuid + '_comment'] = '"' + name + '"';
	} else if (this.target === 'device') {
		buildSettings.CODE_SIGN_IDENTITY = `"${this.certDeveloperName}"`;
		buildSettings.CODE_SIGN_STYLE = 'Manual';
	} else if (this.target === 'macos') {
		buildSettings['"CODE_SIGN_IDENTITY[sdk=macosx*]"'] = '"-"';
		buildSettings.CODE_SIGN_STYLE = 'Manual';
	}  else if (this.target === 'dist-macappstore') {
		buildSettings['"CODE_SIGN_IDENTITY[sdk=macosx*]"'] = '"-"';
		buildSettings.CODE_SIGN_STYLE = 'Manual';

		xobjs.PBXShellScriptBuildPhase || (xobjs.PBXShellScriptBuildPhase = {});
		const buildPhaseUuid = this.generateXcodeUuid(xcodeProject);
		const name = 'Copy Resources to Archive';

		xobjs.PBXNativeTarget[mainTargetUuid].buildPhases.push({
			value: buildPhaseUuid,
			comment: '"' + name + '"'
		});

		xobjs.PBXShellScriptBuildPhase[buildPhaseUuid] = {
			isa: 'PBXShellScriptBuildPhase',
			buildActionMask: 2147483647,
			files: [],
			inputPaths: [],
			name: '"' + name + '"',
			outputPaths: [],
			runOnlyForDeploymentPostprocessing: 0,
			shellPath: '/bin/sh',
			shellScript: '"/bin/cp -rf \\"$PROJECT_DIR/ArchiveStaging\\"/ \\"$TARGET_BUILD_DIR/$PRODUCT_NAME.app/Contents/Resources/\\""',
			showEnvVarsInLog: 0
		};
		xobjs.PBXShellScriptBuildPhase[buildPhaseUuid + '_comment'] = '"' + name + '"';
	}

	// inject the team id and app groups
	if (this.provisioningProfile) {
		const attr = pbxProject.attributes || (pbxProject.attributes = {});
		const targetAttr = attr.TargetAttributes || (attr.TargetAttributes = {});
		const mainTargetAttr = targetAttr[mainTargetUuid] || (targetAttr[mainTargetUuid] = {});

		mainTargetAttr.DevelopmentTeam = this.teamId;

		// turn on any capabilities
		Object.keys(caps).forEach(function (cap) {
			if (cap === 'app-groups') {
				const syscaps = mainTargetAttr.SystemCapabilities || (mainTargetAttr.SystemCapabilities = {});
				syscaps['com.apple.ApplicationGroups.iOS'] || (syscaps['com.apple.ApplicationGroups.iOS'] = {});
				syscaps['com.apple.ApplicationGroups.iOS'].enabled = 1;
			}
		});
	}

	// set the min ios version for the whole project
	xobjs.XCConfigurationList[pbxProject.buildConfigurationList].buildConfigurations.forEach(function (buildConf) {
		const buildSettings = xobjs.XCBuildConfiguration[buildConf.value].buildSettings;
		buildSettings.IPHONEOS_DEPLOYMENT_TARGET = appc.version.format(this.minIosVer, 2);
		delete buildSettings['"CODE_SIGN_IDENTITY[sdk=iphoneos*]"'];
	}, this);

	const isMacOS = this.target === 'macos' || this.target === 'dist-macappstore';

	// set the target-specific build settings
	xobjs.XCConfigurationList[xobjs.PBXNativeTarget[mainTargetUuid].buildConfigurationList].buildConfigurations.forEach(function (buildConf) {
		const bs = merge(xobjs.XCBuildConfiguration[buildConf.value].buildSettings, buildSettings);
		delete bs['"CODE_SIGN_IDENTITY[sdk=iphoneos*]"'];

		bs.PRODUCT_BUNDLE_IDENTIFIER = '"' + this.tiapp.id + '"';
		bs.SUPPORTS_MACCATALYST = isMacOS;
		if (this.provisioningProfile) {
			bs.DEVELOPMENT_TEAM = this.teamId;
			bs.PROVISIONING_PROFILE = '"' + this.provisioningProfile.uuid + '"';
			bs.PROVISIONING_PROFILE_SPECIFIER = '"' + this.provisioningProfile.name + '"';
		}
	}, this);

	// if the storyboard launch screen is disabled, remove it from the resources build phase
	if (!this.enableLaunchScreenStoryboard) {
		for (let i = 0; i < resourcesBuildPhase.files.length; i++) {
			if (xobjs.PBXBuildFile[resourcesBuildPhase.files[i].value].fileRef_comment === 'LaunchScreen.storyboard') {
				resourcesBuildPhase.files.splice(i, 1);
				break;
			}
		}
	}

	// if we have a Settings.bundle, add it to the project
	[ 'ios', 'iphone' ].some(function (name) {
		const settingsBundleDir = path.join(this.projectDir, 'platform', name, 'Settings.bundle');
		if (!fs.existsSync(settingsBundleDir) || !fs.statSync(settingsBundleDir).isDirectory()) {
			return false;
		}

		const fileRefUuid = this.generateXcodeUuid(xcodeProject),
			buildFileUuid = this.generateXcodeUuid(xcodeProject);

		// add the file reference
		xobjs.PBXFileReference[fileRefUuid] = {
			isa: 'PBXFileReference',
			lastKnownFileType: 'wrapper.plug-in',
			path: 'Settings.bundle',
			sourceTree: '"<group>"'
		};
		xobjs.PBXFileReference[fileRefUuid + '_comment'] = 'Settings.bundle';

		// add the build file
		xobjs.PBXBuildFile[buildFileUuid] = {
			isa: 'PBXBuildFile',
			fileRef: fileRefUuid,
			fileRef_comment: 'Settings.bundle'
		};
		xobjs.PBXBuildFile[buildFileUuid + '_comment'] = 'Settings.bundle in Resources';

		// add the resources build phase
		resourcesBuildPhase.files.push({
			value: buildFileUuid,
			comment: 'Settings.bundle in Resources'
		});

		// add to resouces group
		resourcesGroup.children.push({
			value: fileRefUuid,
			comment: 'Settings.bundle'
		});

		return true;
	}, this);

	// add the native libraries to the project
	if (this.nativeLibModules.length) {
		this.logger.trace(__n('Adding %%d native module library', 'Adding %%d native module libraries', this.nativeLibModules.length === 1 ? 1 : 2, this.nativeLibModules.length));
		this.nativeLibModules.forEach(function (lib) {
			const isFramework = lib.isFramework,
				fileRefUuid = this.generateXcodeUuid(xcodeProject),
				buildFileUuid = this.generateXcodeUuid(xcodeProject);

			// Framworks are handled by our framework manager!
			if (isFramework) {
				return;
			} else {
				// add the file reference
				xobjs.PBXFileReference[fileRefUuid] = {
					isa: 'PBXFileReference',
					lastKnownFileType: 'archive.ar',
					name: lib.libName,
					path: '"' + lib.libFile + '"',
					sourceTree: '"<absolute>"'
				};
				xobjs.PBXFileReference[fileRefUuid + '_comment'] = lib.libName;

				// add the library to the Frameworks group
				frameworksGroup.children.push({
					value: fileRefUuid,
					comment: lib.libName
				});

				// add the build file
				xobjs.PBXBuildFile[buildFileUuid] = {
					isa: 'PBXBuildFile',
					fileRef: fileRefUuid,
					fileRef_comment: lib.libName,
					platformFilter: 'ios'
				};
				xobjs.PBXBuildFile[buildFileUuid + '_comment'] = lib.libName + ' in Frameworks';

				// add the library to the frameworks build phase
				frameworksBuildPhase.files.push({
					value: buildFileUuid,
					comment: lib.libName + ' in Frameworks'
				});

				// add the library / framework to the dedicated search paths
				xobjs.XCConfigurationList[xobjs.PBXNativeTarget[mainTargetUuid].buildConfigurationList].buildConfigurations.forEach(function (buildConf) {
					var buildSettings = xobjs.XCBuildConfiguration[buildConf.value].buildSettings;

					buildSettings.LIBRARY_SEARCH_PATHS || (buildSettings.LIBRARY_SEARCH_PATHS = []);
					buildSettings.LIBRARY_SEARCH_PATHS.push('"\\"' + path.dirname(lib.libFile) + '\\""');
				});
			}
		}, this);
	} else {
		this.logger.trace(__('No native module libraries to add'));
	}

	// add extensions and their targets to the project
	if (this.extensions.length) {
		this.logger.trace(__n('Adding %%d iOS extension', 'Adding %%d iOS extensions', this.extensions.length === 1 ? 1 : 2, this.extensions.length));

		const swiftRegExp = /\.swift$/;

		this.extensions.forEach(function (ext) {
			const extObjs = ext.objs,
				extPBXProject = ext.project;

			// create a group in the Extensions group for all the extension's groups
			const groupUuid = this.generateXcodeUuid(xcodeProject);
			extensionsGroup.children.push({
				value: groupUuid,
				comment: ext.projectName
			});
			xobjs.PBXGroup[groupUuid] = {
				isa: 'PBXGroup',
				children: [],
				name: '"' + ext.projectName + '"',
				path: '"' + ext.relPath + '"',
				sourceTree: '"<group>"'
			};
			xobjs.PBXGroup[groupUuid + '_comment'] = ext.projectName;

			// loop through all of the extension's targets
			extPBXProject.targets.forEach(function (extTarget) {
				const
					targetUuid = extTarget.value,
					targetName = extTarget.comment,
					targetInfo = ext.targetInfo[targetName];
				let targetGroup = null,
					extFrameworksGroup = null,
					extFrameworkReference = null,
					extResourcesGroup = null,
					extResourceReference = null;

				// do we care about this target?
				const target = ext.targets.find(t => t.name === targetName);
				if (!target) {
					return;
				}

				pbxProject.targets.push(extTarget);

				// add target attributes
				if (extPBXProject.attributes && extPBXProject.attributes.TargetAttributes && extPBXProject.attributes.TargetAttributes[targetUuid]) {
					pbxProject.attributes || (pbxProject.attributes = {});
					pbxProject.attributes.TargetAttributes || (pbxProject.attributes.TargetAttributes = {});
					pbxProject.attributes.TargetAttributes[targetUuid] = extPBXProject.attributes.TargetAttributes[targetUuid];
				}

				if (this.provisioningProfile) {
					const ta = pbxProject.attributes.TargetAttributes[targetUuid] || (pbxProject.attributes.TargetAttributes[targetUuid] = {});
					ta.DevelopmentTeam = this.teamId;

					Object.keys(caps).forEach(function (cap) {
						ta.SystemCapabilities || (ta.SystemCapabilities = {});
						if (cap === 'app-groups') {
							ta.SystemCapabilities['com.apple.ApplicationGroups.iOS'] || (ta.SystemCapabilities['com.apple.ApplicationGroups.iOS'] = {});
							ta.SystemCapabilities['com.apple.ApplicationGroups.iOS'].enabled = 1;
						}
					});
				}

				// add the native target
				xobjs.PBXNativeTarget[targetUuid] = extObjs.PBXNativeTarget[targetUuid];
				xobjs.PBXNativeTarget[targetUuid + '_comment'] = extObjs.PBXNativeTarget[targetUuid + '_comment'];

				// add the target product to the products group
				productsGroup.children.push({
					value: xobjs.PBXNativeTarget[targetUuid].productReference,
					comment: xobjs.PBXNativeTarget[targetUuid].productReference_comment
				});

				// find the extension frameworks and resources group
				Object.keys(extObjs.PBXGroup).forEach(function (key) {
					if (extObjs.PBXGroup[key] === 'Frameworks') {
						extFrameworksGroup = key.split('_')[0];
					}
					if (extObjs.PBXGroup[key] === 'Resources') {
						extResourcesGroup = key.split('_')[0];
					}
				});

				// add the extension frameworks to the frameworks group
				if (extFrameworksGroup) {
					extObjs.PBXGroup[extFrameworksGroup].children.forEach(function (child) {
						frameworksGroup.children.push(child);
						// find the extension framework file reference
						Object.keys(extObjs.PBXFileReference).forEach(function (key) {
							if (extObjs.PBXFileReference[key] === child.comment) {
								// add the file reference
								extFrameworkReference = key.split('_')[0];
								xobjs.PBXFileReference[extFrameworkReference] = extObjs.PBXFileReference[extFrameworkReference];
								xobjs.PBXFileReference[extFrameworkReference + '_comment'] = child.comment;

								const currentFileRef = Object.assign({}, xobjs.PBXFileReference[extFrameworkReference]);

								if (!currentFileRef.path.includes('extensions/') && currentFileRef.sourceTree === '"<group>"') {
									xobjs.PBXFileReference[extFrameworkReference].path = `${ext.relPath}/${currentFileRef.path}`;
									xobjs.PBXFileReference[extFrameworkReference].name = currentFileRef.path;
								}
							}
						});
					});
				}

				// add the extension resources to the resources group
				if (extResourcesGroup) {
					extObjs.PBXGroup[extResourcesGroup].children.forEach(function (child) {
						resourcesGroup.children.push(child);
						// find the extension framework file reference
						Object.keys(extObjs.PBXFileReference).forEach(function (key) {
							if (extObjs.PBXFileReference[key] === child.comment) {
								// add the file reference
								extResourceReference = key.split('_')[0];
								xobjs.PBXFileReference[extResourceReference] = extObjs.PBXFileReference[extResourceReference];
								xobjs.PBXFileReference[extResourceReference + '_comment'] = child.comment;
							}
						});
					});
				}

				const handledBuildPhases = [ 'PBXSourcesBuildPhase', 'PBXFrameworksBuildPhase', 'PBXResourcesBuildPhase', 'PBXCopyFilesBuildPhase', 'PBXShellScriptBuildPhase' ];

				// add the build phases
				xobjs.PBXNativeTarget[targetUuid].buildPhases.forEach(phase => {
					let type;

					for (const handledBuildPhase of handledBuildPhases) {
						if (extObjs[handledBuildPhase] && extObjs[handledBuildPhase][phase.value]) {
							type = handledBuildPhase;
							break;
						}
					}

					if (!type) {
						this.logger.warn(`No build phases found for extension target "${targetName}"`);
						return;
					}

					if (type === 'PBXShellScriptBuildPhase' && this.deployType !== 'production') {
						this.logger.debug(`Excluding PBXShellScriptBuildPhase in "${targetName}" for non-production build`);
						return;
					}

					xobjs[type] || (xobjs[type] = {});
					xobjs[type][phase.value] = extObjs[type][phase.value];
					xobjs[type][phase.value + '_comment'] = extObjs[type][phase.value + '_comment'];

					// add files
					xobjs[type][phase.value].files.forEach(function (file) {
						xobjs.PBXBuildFile[file.value] = extObjs.PBXBuildFile[file.value];
						xobjs.PBXBuildFile[file.value + '_comment'] = extObjs.PBXBuildFile[file.value + '_comment'];
					});
				});

				// add dependencies
				xobjs.PBXNativeTarget[targetUuid].dependencies.forEach(function (dep) {
					xobjs.PBXTargetDependency || (xobjs.PBXTargetDependency = {});
					xobjs.PBXTargetDependency[dep.value] = extObjs.PBXTargetDependency[dep.value];
					xobjs.PBXTargetDependency[dep.value + '_comment'] = extObjs.PBXTargetDependency[dep.value + '_comment'];

					// add the target proxy
					const proxyUuid = xobjs.PBXTargetDependency[dep.value].targetProxy;
					xobjs.PBXContainerItemProxy || (xobjs.PBXContainerItemProxy = {});
					xobjs.PBXContainerItemProxy[proxyUuid] = extObjs.PBXContainerItemProxy[proxyUuid];
					xobjs.PBXContainerItemProxy[proxyUuid].containerPortal = projectUuid;
					xobjs.PBXContainerItemProxy[proxyUuid + '_comment'] = extObjs.PBXContainerItemProxy[proxyUuid + '_comment'];
				});

				// add the product reference
				const productUuid = xobjs.PBXNativeTarget[targetUuid].productReference;
				xobjs.PBXFileReference[productUuid] = extObjs.PBXFileReference[productUuid];
				xobjs.PBXFileReference[productUuid + '_comment'] = extObjs.PBXFileReference[productUuid + '_comment'];

				// add the groups and files
				let hasSwiftFiles = false;
				extObjs.PBXGroup[extPBXProject.mainGroup].children.some(function (child) {
					// While creating Widget Extention, in target name 'Extension' is appended.
					if (child.comment !== target.name && `${child.comment}Extension` !== target.name) {
						return false;
					}

					xobjs.PBXGroup[groupUuid].children.push(child);

					(function addGroup(uuid, basePath) {
						if (extObjs.PBXGroup[uuid].path) {
							basePath = path.join(basePath, extObjs.PBXGroup[uuid].path.replace(/^"/, '').replace(/"$/, ''));
						}

						xobjs.PBXGroup[uuid] = extObjs.PBXGroup[uuid];
						xobjs.PBXGroup[uuid + '_comment'] = extObjs.PBXGroup[uuid + '_comment'];

						extObjs.PBXGroup[uuid].children.forEach(function (child) {
							if (extObjs.PBXGroup[child.value]) {
								return addGroup(child.value, basePath);
							}

							if (extObjs.PBXFileReference[child.value]) {
								xobjs.PBXFileReference[child.value] = extObjs.PBXFileReference[child.value];
								xobjs.PBXFileReference[child.value + '_comment'] = extObjs.PBXFileReference[child.value + '_comment'];
								if (!hasSwiftFiles && swiftRegExp.test(xobjs.PBXFileReference[child.value + '_comment'])) {
									hasSwiftFiles = true;
								}
							}

							if (extObjs.PBXVariantGroup && extObjs.PBXVariantGroup[child.value]) {
								xobjs.PBXVariantGroup || (xobjs.PBXVariantGroup = {});
								const varGroup = xobjs.PBXVariantGroup[child.value] = extObjs.PBXVariantGroup[child.value];
								varGroup.children && varGroup.children.forEach(function (child) {
									xobjs.PBXFileReference[child.value] = extObjs.PBXFileReference[child.value];
									xobjs.PBXFileReference[child.value + '_comment'] = extObjs.PBXFileReference[child.value + '_comment'];
									if (!hasSwiftFiles && swiftRegExp.test(xobjs.PBXFileReference[child.value + '_comment'])) {
										hasSwiftFiles = true;
									}
								});
							}
						});
					}(child.value, ext.basePath));

					// save the target group so that we can add an entitlements.plist to it if it doesn't already exist
					targetGroup = xobjs.PBXGroup[child.value];

					return true;
				});

				// add the build configuration
				const buildConfigurationListUuid = xobjs.PBXNativeTarget[targetUuid].buildConfigurationList;
				xobjs.XCConfigurationList[buildConfigurationListUuid] = extObjs.XCConfigurationList[buildConfigurationListUuid];
				xobjs.XCConfigurationList[buildConfigurationListUuid + '_comment'] = extObjs.XCConfigurationList[buildConfigurationListUuid + '_comment'];

				let haveEntitlements = this.provisioningProfile
					&& Object.keys(caps).some(cap => /^(app-groups)$/.test(cap));

				xobjs.XCConfigurationList[buildConfigurationListUuid].buildConfigurations.forEach(function (conf) {
					xobjs.XCBuildConfiguration[conf.value] = extObjs.XCBuildConfiguration[conf.value];
					xobjs.XCBuildConfiguration[conf.value + '_comment'] = extObjs.XCBuildConfiguration[conf.value + '_comment'];

					// update info.plist path
					const extBuildSettings = xobjs.XCBuildConfiguration[conf.value].buildSettings;

					if (extBuildSettings.INFOPLIST_FILE) {
						extBuildSettings.INFOPLIST_FILE = '"' + ext.relPath + '/' + extBuildSettings.INFOPLIST_FILE.replace(/^"/, '').replace(/"$/, '') + '"';
					}

					if (!extBuildSettings.CLANG_ENABLE_OBJC_ARC) {
						// inherits from project
						const confList = extObjs.XCConfigurationList[extPBXProject.buildConfigurationList],
							confUuid = confList.buildConfigurations.filter(function (c) { return c.comment === confList.defaultConfigurationName || 'Release'; })[0].value;
						if (extObjs.XCBuildConfiguration[confUuid].buildSettings.CLANG_ENABLE_OBJC_ARC === 'YES') {
							extBuildSettings.CLANG_ENABLE_OBJC_ARC = 'YES';
						}
					}

					if (/device|dist-appstore|dist-adhoc/.test(this.target)) {
						const pp = this.findProvisioningProfile(this.target, target.ppUUIDs[this.target]);
						extBuildSettings.PROVISIONING_PROFILE = '"' + pp.uuid + '"';

						// NOTE: if there isn't an explicit <team-id> in the tiapp.xml and there is no
						// teams or more than 1 team in the provisioning profile, then we use the appPrefix
						// which should be the team id, but can differ and since we don't check it, this
						// next line of code could be problematic
						extBuildSettings.DEVELOPMENT_TEAM = this.teamId || (pp.team.length === 1 ? pp.team[0] : pp.appPrefix);

						extBuildSettings.PROVISIONING_PROFILE_SPECIFIER = '"' + pp.name + '"';
						extBuildSettings.DEPLOYMENT_POSTPROCESSING = 'YES';
						if (this.keychain) {
							extBuildSettings.OTHER_CODE_SIGN_FLAGS = '"--keychain ' + this.keychain + '"';
						}
					}

					if (extBuildSettings['"CODE_SIGN_IDENTITY[sdk=iphoneos*]"']) {
						extBuildSettings.CODE_SIGN_IDENTITY = extBuildSettings['"CODE_SIGN_IDENTITY[sdk=iphoneos*]"'];
						delete extBuildSettings['"CODE_SIGN_IDENTITY[sdk=iphoneos*]"'];
					}

					if (buildSettings.CODE_SIGN_IDENTITY) {
						extBuildSettings.CODE_SIGN_IDENTITY = buildSettings.CODE_SIGN_IDENTITY;
					}

					const setEntitlementsFile = (entFile, warn) => {
						let src = path.join(ext.basePath, entFile);
						if (fs.existsSync(src)) {
							extBuildSettings.CODE_SIGN_ENTITLEMENTS = '"' + path.join(ext.relPath, entFile) + '"';
							targetInfo.entitlementsFile = path.join(this.buildDir, ext.relPath, entFile);
						} else {
							src = path.join(ext.basePath, targetName, entFile);
							if (fs.existsSync(src)) {
								extBuildSettings.CODE_SIGN_ENTITLEMENTS = '"' + path.join(ext.relPath, targetName, entFile) + '"';
								targetInfo.entitlementsFile = path.join(this.buildDir, ext.relPath, targetName, entFile);
							} else {
								delete extBuildSettings.CODE_SIGN_ENTITLEMENTS;
								targetInfo.entitlementsFile = null;
								if (warn) {
									this.logger.warn(`Unable to find extension target "${targetName}" CODE_SIGN_ENTITLEMENTS file: ${entFile}`);
								}
							}
						}
					};

					if (extBuildSettings.CODE_SIGN_ENTITLEMENTS) {
						setEntitlementsFile(extBuildSettings.CODE_SIGN_ENTITLEMENTS.replace(/^"/, '').replace(/"$/, ''), true);

					} else if (haveEntitlements) {
						haveEntitlements = false;

						const entFile = targetName + '.entitlements';
						setEntitlementsFile(entFile);

						if (targetInfo.entitlementsFile) {
							const exists = Object.keys(xobjs.PBXFileReference).some(function (uuid) {
								return xobjs.PBXFileReference[uuid + '_comment'] === entFile;
							});

							if (!exists) {
								// create the file reference
								const entFileRefUuid = this.generateXcodeUuid(xcodeProject);
								xobjs.PBXFileReference[entFileRefUuid] = {
									isa: 'PBXFileReference',
									lastKnownFileType: 'text.xml',
									path: '"' + entFile + '"',
									sourceTree: '"<group>"'
								};
								xobjs.PBXFileReference[entFileRefUuid + '_comment'] = entFile;

								// add the file to the target's pbx group
								targetGroup && targetGroup.children.push({
									value: entFileRefUuid,
									comment: entFile
								});
							}
						}
					}

					if (hasSwiftFiles) {
						if (!extBuildSettings.SWIFT_VERSION) {
							extBuildSettings.SWIFT_VERSION = '3.1';
						}

						if (legacySwift) {
							extBuildSettings.EMBEDDED_CONTENT_CONTAINS_SWIFT = 'YES';
						}
					}
				}, this);

				if (targetInfo.isWatchAppV1Extension) {
					this.unmarkBuildDirFiles(path.join(this.xcodeAppDir, 'PlugIns', xobjs.PBXFileReference[productUuid].path.replace(/^"/, '').replace(/"$/, '')));
				} else if (targetInfo.isWatchAppV2orNewer) {
					this.unmarkBuildDirFiles(path.join(this.xcodeAppDir, 'Watch', xobjs.PBXFileReference[productUuid].path.replace(/^"/, '').replace(/"$/, '')));
				} else if (targetInfo.isAppClip) {
					const xcodeProj = path.basename(ext.projectPath);
					const originPath = path.join(ext.projectPath.split(xcodeProj)[0], ext.targets[0].name);
					const destinationPath = path.join(this.buildDir, ext.targets[0].name);

					this.unmarkBuildDirFiles(path.join(this.xcodeAppDir, 'AppClips', xobjs.PBXFileReference[productUuid].path.replace(/^"/, '').replace(/"$/, '')));
					this.copyDirSync(originPath, destinationPath);
				}

				if (targetInfo.isExtension || targetInfo.isWatchAppV2orNewer || targetInfo.isAppClip) {
					// add this target as a dependency of the titanium app's project
					const proxyUuid = this.generateXcodeUuid(xcodeProject);
					xobjs.PBXContainerItemProxy || (xobjs.PBXContainerItemProxy = {});
					xobjs.PBXContainerItemProxy[proxyUuid] = {
						isa: 'PBXContainerItemProxy',
						containerPortal: projectUuid,
						containerPortal_comment: 'Project object',
						proxyType: 1,
						remoteGlobalIDString: targetUuid,
						remoteInfo: '"' + targetName + '"'
					};
					xobjs.PBXContainerItemProxy[proxyUuid + '_comment'] = 'PBXContainerItemProxy';

					const depUuid = this.generateXcodeUuid(xcodeProject);
					xobjs.PBXTargetDependency || (xobjs.PBXTargetDependency = {});
					xobjs.PBXTargetDependency[depUuid] = {
						isa: 'PBXTargetDependency',
						target: targetUuid,
						target_comment: targetName,
						targetProxy: proxyUuid,
						targetProxy_comment: 'PBXContainerItemProxy'
					};
					xobjs.PBXTargetDependency[depUuid + '_comment'] = 'PBXTargetDependency';

					xobjs.PBXNativeTarget[mainTargetUuid].dependencies.push({
						value: depUuid,
						comment: 'PBXTargetDependency'
					});

					function addEmbedBuildPhase(name, dstPath, dstSubfolderSpec) {
						const embedExtPhase = xobjs.PBXNativeTarget[mainTargetUuid]
							.buildPhases
							.filter(function (phase) {
								return phase.comment === name;
							}).shift();
						let embedUuid = embedExtPhase && embedExtPhase.value;

						if (!embedUuid) {
							embedUuid = this.generateXcodeUuid(xcodeProject);
							xobjs.PBXNativeTarget[mainTargetUuid].buildPhases.push({
								value: embedUuid,
								comment: name
							});
							xobjs.PBXCopyFilesBuildPhase || (xobjs.PBXCopyFilesBuildPhase = {});
							xobjs.PBXCopyFilesBuildPhase[embedUuid] = {
								isa: 'PBXCopyFilesBuildPhase',
								buildActionMask: 2147483647,
								dstPath: '"' + (dstPath || '') + '"',
								dstSubfolderSpec: dstSubfolderSpec,
								files: [],
								name: '"' + name + '"',
								runOnlyForDeploymentPostprocessing: 0
							};
							xobjs.PBXCopyFilesBuildPhase[embedUuid + '_comment'] = name;
						}

						const productName = xobjs.PBXNativeTarget[targetUuid].productReference_comment;

						// add the copy files build phase
						const copyFilesUuid = this.generateXcodeUuid(xcodeProject);

						xobjs.PBXCopyFilesBuildPhase[embedUuid].files.push({
							value: copyFilesUuid,
							comment: productName + ' in ' + name
						});

						xobjs.PBXBuildFile[copyFilesUuid] = {
							isa: 'PBXBuildFile',
							fileRef: productUuid,
							fileRef_comment: productName,
							settings: { ATTRIBUTES: [ 'RemoveHeadersOnCopy' ] }
						};
						xobjs.PBXBuildFile[copyFilesUuid + '_comment'] = productName + ' in ' + name;
					}

					if (targetInfo.isWatchAppV2orNewer) {
						addEmbedBuildPhase.call(this, 'Embed Watch Content', '$(CONTENTS_FOLDER_PATH)/Watch', 16 /* type "watch app" */);
					} else if (targetInfo.isExtension) {
						addEmbedBuildPhase.call(this, 'Embed App Extensions', null, 13 /* type "plugin" */);
					} else if (targetInfo.isAppClip) {
						addEmbedBuildPhase.call(this, 'Embed App Clips', '$(CONTENTS_FOLDER_PATH)/AppClips', 16 /* type "application.on-demand-install-capable" */);
					}
				}
			}, this);
		}, this);
	} else {
		this.logger.trace(__('No extensions to add'));
	}

	// if any extensions contain a watch app, we must force the min iOS deployment target to 8.2
	if (this.hasWatchAppV2orNewer) {
		// TODO: Make sure the version of Xcode can support this version of watch app

		let once = 0;
		const iosDeploymentTarget = this.hasWatchAppV2orNewer ? '9.0' : '8.2';

		xobjs.XCConfigurationList[pbxProject.buildConfigurationList].buildConfigurations.forEach(function (buildConf) {
			const buildSettings = xobjs.XCBuildConfiguration[buildConf.value].buildSettings;
			if (buildSettings.IPHONEOS_DEPLOYMENT_TARGET && appc.version.lt(buildSettings.IPHONEOS_DEPLOYMENT_TARGET, iosDeploymentTarget)) {
				once++ === 0 && this.logger.warn(__('WatchKit App detected, changing minimum iOS deployment target from %s to %s', buildSettings.IPHONEOS_DEPLOYMENT_TARGET, iosDeploymentTarget));
				buildSettings.IPHONEOS_DEPLOYMENT_TARGET = iosDeploymentTarget;
			}
		}, this);

		this.hasWatchApp = true;
	}

	Object.keys(xobjs.XCBuildConfiguration).forEach(function (key) {
		const conf = xobjs.XCBuildConfiguration[key];
		if (!conf || typeof conf !== 'object' || !conf.buildSettings) {
			return;
		}

		if (legacySwift) {
			delete conf.buildSettings.ALWAYS_EMBED_SWIFT_STANDARD_LIBRARIES;
		} else {
			delete conf.buildSettings.EMBEDDED_CONTENT_CONTAINS_SWIFT;
		}
	});

	// get the product names
	this.products = productsGroup.children.map(function (product) {
		return product.comment;
	});

	// add TitaniumKit.framework
	xcodeProject.removeFramework('TitaniumKit.framework', { customFramework: true, embed: true });

	const frameworkFileRefUuid = this.generateXcodeUuid(xcodeProject);
	const frameworkBuildFileUuid = this.generateXcodeUuid(xcodeProject);
	const embedFrameworkBuildFileUuid = this.generateXcodeUuid(xcodeProject);

	xobjs.PBXFileReference[frameworkFileRefUuid] = {
		isa: 'PBXFileReference',
		lastKnownFileType: 'wrapper.xcframework',
		name: '"TitaniumKit.xcframework"',
		path: '"Frameworks/TitaniumKit.xcframework"',
		sourceTree: '"<group>"'
	};
	xobjs.PBXFileReference[frameworkFileRefUuid + '_comment'] = 'TitaniumKit.xcframework';

	xobjs.PBXBuildFile[frameworkBuildFileUuid] = {
		isa: 'PBXBuildFile',
		fileRef: frameworkFileRefUuid,
		fileRef_comment: 'TitaniumKit.xcframework'
	};
	xobjs.PBXBuildFile[frameworkBuildFileUuid + '_comment'] = 'TitaniumKit.xcframework in Frameworks';

	frameworksBuildPhase.files.push({
		value: frameworkBuildFileUuid,
		comment: xobjs.PBXBuildFile[frameworkBuildFileUuid + '_comment']
	});

	xobjs.PBXBuildFile[embedFrameworkBuildFileUuid] = {
		isa: 'PBXBuildFile',
		fileRef: frameworkFileRefUuid,
		fileRef_comment: 'TitaniumKit.xcframework',
		settings: { ATTRIBUTES: [ 'CodeSignOnCopy', 'RemoveHeadersOnCopy' ] }
	};
	xobjs.PBXBuildFile[embedFrameworkBuildFileUuid + '_comment'] = 'TitaniumKit.xcframework in Embed Frameworks';

	copyFilesBuildPhase.files.push({
		value: embedFrameworkBuildFileUuid,
		comment: xobjs.PBXBuildFile[embedFrameworkBuildFileUuid + '_comment']
	});

	frameworksGroup.children.push({
		value: frameworkFileRefUuid,
		comment: 'TitaniumKit.xcframework'
	});

	// add tiverify.framework
	// technically the `lastKNownFileType` option is wrong, but the xcode module
	// does not properly handle `.xcframework` extensions without it yet
	xcodeProject.removeFramework('tiverify.xcframework', { lastKnownFileType: 'wrapper.framework' });

	const tiverifyFrameworkFileRefUuid = this.generateXcodeUuid(xcodeProject);
	const tiverifyFrameworkBuildFileUuid = this.generateXcodeUuid(xcodeProject);

	xobjs.PBXFileReference[tiverifyFrameworkFileRefUuid] = {
		isa: 'PBXFileReference',
		lastKnownFileType: 'wrapper.xcframework',
		name: '"tiverify.xcframework"',
		path: '"Frameworks/tiverify.xcframework"',
		sourceTree: '"<group>"'
	};
	xobjs.PBXFileReference[tiverifyFrameworkFileRefUuid + '_comment'] = 'tiverify.xcframework';

	xobjs.PBXBuildFile[tiverifyFrameworkBuildFileUuid] = {
		isa: 'PBXBuildFile',
		fileRef: tiverifyFrameworkFileRefUuid,
		fileRef_comment: 'tiverify.xcframework'
	};
	xobjs.PBXBuildFile[tiverifyFrameworkBuildFileUuid + '_comment'] = 'tiverify.xcframework in Frameworks';

	frameworksBuildPhase.files.push({
		value: tiverifyFrameworkBuildFileUuid,
		comment: xobjs.PBXBuildFile[tiverifyFrameworkBuildFileUuid + '_comment']
	});

	frameworksGroup.children.push({
		value: tiverifyFrameworkFileRefUuid,
		comment: 'tiverify.xcframework'
	});

	// run the xcode project hook
	const hook = this.cli.createHook('build.ios.xcodeproject', this, function (xcodeProject, done) {
		const contents = xcodeProject.writeSync(),
			dest = xcodeProject.filepath,
			parent = path.dirname(dest);

		if (!fs.existsSync(dest) || contents !== fs.readFileSync(dest).toString()) {
			if (!this.forceRebuild) {
				this.logger.info(__('Forcing rebuild: Xcode project has changed since last build'));
				this.forceRebuild = true;
			}
			this.logger.debug(__('Writing %s', dest.cyan));
			fs.ensureDirSync(parent);
			fs.writeFileSync(dest, contents);
		} else {
			this.logger.trace(__('No change, skipping %s', dest.cyan));
		}

		this.unmarkBuildDirFile(dest);

		done();
	});

	hook(xcodeProject, next);
};

iOSBuilder.prototype.mergePlist = function mergePlist(src, dest) {
	return (function merge(src, dest) {
		Object.keys(src).forEach(function (prop) {
			if (!/^\+/.test(prop)) {
				if (Object.prototype.toString.call(src[prop]) === '[object Object]') {
					Object.prototype.hasOwnProperty.call(dest, prop) || (dest[prop] = {});
					merge(src[prop], dest[prop]);
				} else {
					dest[prop] = src[prop];
				}
			}
		});
	}(src, dest));
};

iOSBuilder.prototype._embedCapabilitiesAndWriteEntitlementsPlist = function _embedCapabilitiesAndWriteEntitlementsPlist(plist, dest, isExtension, next) {
	const caps = this.tiapp.ios.capabilities,
		parent = path.dirname(dest);

	// add any capabilities entitlements
	Object.keys(caps).forEach(function (cap) {
		if (cap === 'app-groups') {
			Array.isArray(plist['com.apple.security.application-groups']) || (plist['com.apple.security.application-groups'] = []);
			caps[cap].forEach(function (group) {
				if (plist['com.apple.security.application-groups'].indexOf(group) === -1) {
					plist['com.apple.security.application-groups'].push(group);
				}
			});
		}
	});

	this.unmarkBuildDirFile(dest);

	const rel = path.relative(this.buildDir, dest);
	if ([ 'ios', 'iphone' ].some(function (dir) {
		if (fs.existsSync(path.join(this.projectDir, 'platform', dir, rel))) {
			return true;
		}
		return false;
	}, this)) {
		return next();
	}

	const name = 'build.ios.write' + (isExtension ? 'Extension' : '') + 'Entitlements';
	const hook = this.cli.createHook(name, this, function (plist, dest, done) {
		// write the entitlements.plist
		const contents = plist.toString('xml');

		if (!fs.existsSync(dest) || contents !== fs.readFileSync(dest, 'utf-8').trim()) {
			if (!this.forceRebuild) {
				this.logger.info(__('Forcing rebuild: %s has changed since last build', dest.replace(this.projectDir + '/', '')));
				this.forceRebuild = true;
			}
			this.logger.debug(__('Writing %s', dest.cyan));
			fs.ensureDirSync(parent);
			fs.writeFileSync(dest, contents);
		} else {
			this.logger.trace(__('No change, skipping %s', dest.cyan));
		}

		done();
	});

	hook(plist, dest, next);
};

iOSBuilder.prototype.writeEntitlementsPlist = function writeEntitlementsPlist(next) {
	this.logger.info(__('Creating Entitlements.plist'));

	// allow the project to have its own custom entitlements
	const entitlementsFile = path.join(this.projectDir, 'Entitlements.plist');
	let plist = new appc.plist();

	// check if we have a custom entitlements plist file
	if (fs.existsSync(entitlementsFile)) {
		this.logger.info(__('Found custom entitlements: %s', entitlementsFile.cyan));
		plist = new appc.plist(entitlementsFile);
	}

	// tiapp.xml entitlements
	if (this.tiapp.ios.entitlements) {
		this.mergePlist(this.tiapp.ios.entitlements, plist);
	}

	// if we have a provisioning profile, make sure some entitlement settings are correct set
	const pp = this.provisioningProfile;
	if (pp) {
		// attempt to customize it by reading provisioning profile
		if (!Object.prototype.hasOwnProperty.call(plist, 'application-identifier')) {
			plist['application-identifier'] = pp.appPrefix + '.' + this.tiapp.id;
		}
		if (pp.apsEnvironment) {
			plist['aps-environment'] = this.target === 'dist-appstore' || this.target === 'dist-adhoc' ? 'production' : 'development';
		}
		if (this.target === 'dist-appstore' && !Object.prototype.hasOwnProperty.call(plist, 'beta-reports-active')) {
			plist['beta-reports-active'] = true;
		}
		if (!Object.prototype.hasOwnProperty.call(plist, 'get-task-allow')) {
			plist['get-task-allow'] = pp.getTaskAllow;
		}
		Array.isArray(plist['keychain-access-groups']) || (plist['keychain-access-groups'] = []);
		if (!plist['keychain-access-groups'].some(id => id === plist['application-identifier'])) {
			plist['keychain-access-groups'].push(plist['application-identifier']);
		}
	}

	if (this.target === 'macos' || this.target === 'dist-macappstore') {
		plist['com.apple.security.app-sandbox'] = true; // required for app store
		plist['com.apple.security.cs.disable-library-validation'] = true; // To run locally, disable library validation
		plist['com.apple.security.cs.allow-jit'] = true; // allow JIT for JavaScriptCore fast code paths

		// FIXME: Based on API usage we really need to turn on other entitlements!
		// Can we copyResources first and then write the entitlements plist based on tiSymbol usage?
		// Do we rely on the end developer adding entitlements to their app?
		// See https://developer.apple.com/documentation/security/app_sandbox?language=objc
		plist['com.apple.security.device.audio-input'] = true;
		plist['com.apple.security.device.camera'] = true;
		plist['com.apple.security.device.microphone'] = true;
		plist['com.apple.security.network.client'] = true;
		plist['com.apple.security.network.server'] = true;
		plist['com.apple.security.personal-information.addressbook'] = true;
		plist['com.apple.security.personal-information.calendars'] = true;
		plist['com.apple.security.personal-information.location'] = true;
		plist['com.apple.security.personal-information.photos-library'] = true;
	}

	this._embedCapabilitiesAndWriteEntitlementsPlist(plist, path.join(this.buildDir, this.tiapp.name + '.entitlements'), false, next);
};

iOSBuilder.prototype.writeInfoPlist = function writeInfoPlist() {
	this.logger.info(__('Creating Info.plist'));

	const defaultInfoPlistFile = path.join(this.platformPath, 'Info.plist'),
		customInfoPlistFile = this.projectDir + '/Info.plist',
		plist = this.infoPlist = new appc.plist(),
		ios = this.tiapp.ios,
		fbAppId = this.tiapp.properties && this.tiapp.properties['ti.facebook.appid'] && this.tiapp.properties['ti.facebook.appid'].value,
		iconName = this.tiapp.icon.replace(/(.+)(\..*)$/, '$1'), // note: this is basically stripping the file extension
		consts = {
			__APPICON__: iconName,
			__PROJECT_NAME__: this.tiapp.name,
			__PROJECT_ID__: this.tiapp.id,
			__URL__: this.tiapp.id,
			__URLSCHEME__: this.tiapp.name.replace(/[^0-9a-z]/gi, '').toLowerCase(),
			__ADDITIONAL_URL_SCHEMES__: fbAppId ? '<string>fb' + fbAppId + '</string>' : ''
		},
		resourceDir = path.join(this.projectDir, 'Resources'),
		iphoneDir = path.join(resourceDir, 'iphone'),
		iosDir = path.join(resourceDir, 'ios');

	// load the default Info.plist
	plist.parse(fs.readFileSync(defaultInfoPlistFile).toString().replace(/(__.+__)/g, function (match, key) {
		return Object.prototype.hasOwnProperty.call(consts, key) ? consts[key] : '<!-- ' + key + ' -->'; // if they key is not a match, just comment out the key
	}));

	// override the default versions with the tiapp.xml version
	plist.CFBundleVersion = String(this.tiapp.version);
	try {
		plist.CFBundleShortVersionString = appc.version.format(this.tiapp.version, 0, 3);
	} catch (ex) {
		plist.CFBundleShortVersionString = this.tiapp.version;
	}

	// if they have not explicitly set the UIRequiresFullScreen setting, then force it to true
	if (plist.UIRequiresFullScreen === undefined) {
		plist.UIRequiresFullScreen = true;
	}

	// this should not exist, but nuke it so we can create it below
	delete plist.UIAppFonts;

	// delete the app icon and launch image keys (which there should be any in the default Info.plist)
	// so that we can detect below if the custom Info.plist uses these keys.
	delete plist.CFBundleIconFile;
	delete plist.CFBundleIconFiles;
	delete plist.UILaunchImages;
	delete plist['UILaunchImages~ipad'];
	delete plist.UILaunchImageFile;

	const i18nLaunchScreens = {};
	ti.i18n.findLaunchScreens(this.projectDir, this.logger, { ignoreDirs: this.ignoreDirs }).forEach(function (p) {
		i18nLaunchScreens[path.basename(p)] = 1;
	});

	[ {
		orientation: 'Portrait',
		'minimum-system-version': '12.0',
		name: 'Default-Portrait',
		subtype: '2688h',
		scale: [ '3x' ],
		size: '{414, 896}'
	},
	{
		orientation: 'Landscape',
		'minimum-system-version': '12.0',
		name: 'Default-Landscape',
		subtype: '2688h',
		scale: [ '3x' ],
		size: '{414, 896}'
	},
	{
		orientation: 'Portrait',
		'minimum-system-version': '12.0',
		name: 'Default-Portrait',
		subtype: '1792h',
		scale: [ '2x' ],
		size: '{414, 896}'
	},
	{
		orientation: 'Landscape',
		'minimum-system-version': '12.0',
		name: 'Default-Landscape',
		subtype: '1792h',
		scale: [ '2x' ],
		size: '{414, 896}'
	},
	{
		orientation: 'Portrait',
		'minimum-system-version': '11.0',
		name: 'Default-Portrait',
		subtype: '2436h',
		scale: [ '3x' ],
		size: '{375, 812}'
	},
	{
		orientation: 'Landscape',
		'minimum-system-version': '11.0',
		name: 'Default-Landscape',
		subtype: '2436h',
		scale: [ '3x' ],
		size: '{375, 812}'
	},
	{
		orientation: 'Portrait',
		'minimum-system-version': '8.0',
		name: 'Default-Portrait',
		subtype: '736h',
		scale: [ '3x' ],
		size: '{414, 736}'
	},
	{
		orientation: 'Landscape',
		'minimum-system-version': '8.0',
		name: 'Default-Landscape',
		subtype: '736h',
		scale: [ '3x' ],
		size: '{414, 736}'
	},
	{
		orientation: 'Portrait',
		'minimum-system-version': '8.0',
		name: 'Default',
		subtype: '667h',
		scale: [ '2x' ],
		size: '{375, 667}'
	},
	{
		orientation: 'Portrait',
		'minimum-system-version': '7.0',
		name: 'Default',
		scale: [ '2x', '1x' ],
		size: '{320, 480}'
	},
	{
		orientation: 'Portrait',
		'minimum-system-version': '7.0',
		name: 'Default',
		subtype: '568h',
		scale: [ '2x' ],
		size: '{320, 568}'
	},
	{
		orientation: 'Portrait',
		idiom: 'ipad',
		'minimum-system-version': '7.0',
		name: 'Default-Portrait',
		scale: [ '2x', '1x' ],
		size: '{768, 1024}'
	},
	{
		orientation: 'Landscape',
		idiom: 'ipad',
		'minimum-system-version': '7.0',
		name: 'Default-Landscape',
		scale: [ '2x', '1x' ],
		size: '{768, 1024}'
	} ].forEach(function (asset) {
		asset.scale.some(function (scale) {
			let key;
			const basefilename = asset.name + (asset.subtype ? '-' + asset.subtype : ''),
				filename = basefilename + (scale !== '1x' ? '@' + scale : '') + '.png';

			// if we have a launch image and if we're doing iPhone only, don't include iPad launch images
			if (i18nLaunchScreens[filename] && (this.deviceFamily !== 'iphone' || asset.idiom === 'iphone')) {
				key = 'UILaunchImages' + (asset.idiom === 'ipad' ? '~ipad' : '');
				Array.isArray(plist[key]) || (plist[key] = []);
				plist[key].push({
					UILaunchImageName: basefilename,
					UILaunchImageOrientation: asset.orientation,
					UILaunchImageSize: asset.size,
					UILaunchImageMinimumOSVersion: asset['minimum-system-version']
				});
				return true;
			}
			return false;
		}, this);
	}, this);

	if (this.enableLaunchScreenStoryboard) {
		plist.UILaunchStoryboardName = 'LaunchScreen';
	} else {
		if (appc.version.gte(this.xcodeEnv.version, '11.0.0')) {
			this.logger.warn(__('Launch images are deprecated by Xcode 11 and you will need to adopt a storyboard-based launch screen'));
		}
		delete plist.UILaunchStoryboardName;
	}

	// if the user has a Info.plist in their project directory, consider that a custom override
	if (fs.existsSync(customInfoPlistFile)) {
		this.logger.info(__('Copying custom Info.plist from project directory'));
		const custom = new appc.plist().parse(fs.readFileSync(customInfoPlistFile).toString());
		this.mergePlist(custom, plist);
	}

	// tiapp.xml settings override the default and custom Info.plist
	plist.UIRequiresPersistentWiFi = Object.prototype.hasOwnProperty.call(this.tiapp, 'persistent-wifi')  ? !!this.tiapp['persistent-wifi']  : false;
	plist.UIPrerenderedIcon        = Object.prototype.hasOwnProperty.call(this.tiapp, 'prerendered-icon') ? !!this.tiapp['prerendered-icon'] : false;
	plist.UIStatusBarHidden        = Object.prototype.hasOwnProperty.call(this.tiapp, 'statusbar-hidden') ? !!this.tiapp['statusbar-hidden'] : false;

	plist.UIStatusBarStyle = 'UIStatusBarStyleDefault';
	if (/opaque_black|opaque|black/.test(this.tiapp['statusbar-style'])) {
		plist.UIStatusBarStyle = 'UIStatusBarStyleBlackOpaque';
	} else if (/translucent_black|transparent|translucent/.test(this.tiapp['statusbar-style'])) {
		plist.UIStatusBarStyle = 'UIStatusBarStyleBlackTranslucent';
	}

	if (this.tiapp.iphone) {
		this.logger.error(__('The <iphone> section of the tiapp.xml has been removed in Titanium SDK 7.0.0 and later.'));
		this.logger.log(__('Please use the <ios> section of the tiapp.xml to specify iOS-specific values instead:'));
		this.logger.log();
		this.logger.log('<ti:app xmlns:ti="http://ti.appcelerator.org">'.grey);
		this.logger.log('    <ios>'.grey);
		this.logger.log('        <plist>'.grey);
		this.logger.log('            <dict>'.grey);
		this.logger.log('                <!-- Enter your Info.plist keys here -->'.magenta);
		this.logger.log('            </dict>'.grey);
		this.logger.log('        </plist>'.grey);
		this.logger.log('    </ios>'.grey);
		this.logger.log('</ti:app>'.grey);
		this.logger.log();

		process.exit(1);
	}

	// custom Info.plist from the tiapp.xml overrides everything
	ios && ios.plist && this.mergePlist(ios.plist, plist);

	// override the CFBundleIdentifier to the app id
	plist.CFBundleIdentifier = this.tiapp.id;

	// inject Apple Transport Security settings
	if (!plist.NSAppTransportSecurity || typeof plist.NSAppTransportSecurity !== 'object') {
		this.logger.info(__('Disabling ATS'));
		// disable ATS
		plist.NSAppTransportSecurity = {
			NSAllowsArbitraryLoads: true
		};
	} else if (plist.NSAppTransportSecurity.NSAllowsArbitraryLoads) {
		this.logger.info(__('ATS explicitly disabled'));
	} else if (this.whitelistAppceleratorDotCom) {
		// we have a whitelist, make sure appcelerator.com is in the list
		plist.NSAppTransportSecurity || (plist.NSAppTransportSecurity = {});
		plist.NSAppTransportSecurity.NSAllowsArbitraryLoads = false;

		this.logger.info(__('ATS enabled, injecting appcelerator.com into ATS whitelist'));
		plist.NSAppTransportSecurity.NSExceptionDomains || (plist.NSAppTransportSecurity.NSExceptionDomains = {});
		if (!plist.NSAppTransportSecurity.NSExceptionDomains['appcelerator.com']) {
			plist.NSAppTransportSecurity.NSExceptionDomains['appcelerator.com'] = {
				NSExceptionMinimumTLSVersion: 'TLSv1.2',
				NSExceptionRequiresForwardSecrecy: true,
				NSExceptionAllowsInsecureHTTPLoads: false,
				NSRequiresCertificateTransparency: false,
				NSIncludesSubdomains: true,
				NSThirdPartyExceptionMinimumTLSVersion: 'TLSv1.2',
				NSThirdPartyExceptionRequiresForwardSecrecy: true,
				NSThirdPartyExceptionAllowsInsecureHTTPLoads: true
			};
		}
	} else {
		this.logger.warn(__('ATS enabled, however *.appcelerator.com are not whitelisted'));
		this.logger.warn(__('Consider setting the "ios.whitelist.appcelerator.com" property in the tiapp.xml to "true"'));
	}

	if (this.target === 'device' && this.deviceId === 'itunes') {
		// device builds require an additional token to ensure uniqueness so that iTunes will detect an updated app to sync.
		// we drop the milliseconds from the current time so that we still have a unique identifier, but is less than 10
		// characters so iTunes 11.2 doesn't get upset.
		plist.CFBundleVersion = String(+new Date());
		this.logger.debug(__('Building for iTunes sync which requires us to set the CFBundleVersion to a unique number to trigger iTunes to update your app'));
		this.logger.debug(__('Setting Info.plist CFBundleVersion to current epoch time %s', plist.CFBundleVersion.cyan));
	}

	// scan for ttf and otf font files
	const fontMap = {};
	(function scanFonts(dir, isRoot) {
		fs.existsSync(dir) && fs.readdirSync(dir).forEach(function (file) {
			const p = path.join(dir, file);
			if (fs.statSync(p).isDirectory() && (!isRoot || file === 'iphone' || file === 'ios' || ti.availablePlatformsNames.indexOf(file) === -1)) {
				scanFonts(p);
			} else if (/\.(otf|ttf)$/i.test(file)) {
				fontMap['/' + p.replace(iphoneDir, '').replace(iosDir, '').replace(resourceDir, '').replace(/^\//, '')] = 1;
			}
		});
	}(resourceDir, true));

	if (Array.isArray(plist.UIAppFonts)) {
		plist.UIAppFonts.forEach(function (f) {
			if (!fontMap[f]) {
				this.logger.warn(__('Info.plist references non-existent font: %s', cyan(f)));
				fontMap[f] = 1;
			}
		}, this);
	}

	const fonts = Object.keys(fontMap);
	fonts.length && (plist.UIAppFonts = fonts);

	// if CFBundleIconFile, CFBundleIconFiles, & UILaunchImages exists, delete it since we're going to use an asset catalog
	if (plist.CFBundleIconFile) {
		this.logger.warn(__('Removing custom Info.plist "CFBundleIconFile" since we now use an asset catalog for app icons.'));
		delete plist.CFBundleIconFile;
	}
	if (plist.CFBundleIconFiles) {
		this.logger.warn(__('Removing custom Info.plist "CFBundleIconFiles" since we now use an asset catalog for app icons.'));
		delete plist.CFBundleIconFiles;
	}
	if (!Object.keys(i18nLaunchScreens).length) {
		// no i18n launch images, so nuke the launch image related keys
		if (plist.UILaunchImages) {
			this.logger.warn(__('Removing custom Info.plist "UILaunchImages" since we now use an asset catalog for launch images.'));
			delete plist.UILaunchImages;
		}
		if (plist['UILaunchImages~ipad']) {
			this.logger.warn(__('Removing custom Info.plist "UILaunchImages~ipad" since we now use an asset catalog for launch images.'));
			delete plist['UILaunchImages~ipad'];
		}
	}
	if (plist.UILaunchImageFile) {
		this.logger.warn(__('Removing custom Info.plist "UILaunchImageFile" since we now use an asset catalog for launch images.'));
		delete plist.UILaunchImageFile;
	}

	// write the Info.plist
	const prev = this.previousBuildManifest.files && this.previousBuildManifest.files['Info.plist'],
		contents = plist.toString('xml'),
		hash = this.hash(contents),
		dest = path.join(this.buildDir, 'Info.plist');

	this.currentBuildManifest.files['Info.plist'] = {
		hash:  hash,
		mtime: 0,
		size:  contents.length
	};

	if (!fs.existsSync(dest) || !prev || prev.size !== contents.length || prev.hash !== hash) {
		if (!this.forceRebuild) {
			this.logger.info(__('Forcing rebuild: %s changed since last build', 'Info.plist'));
			this.forceRebuild = true;
		}
		this.logger.debug(__('Writing %s', dest.cyan));
		fs.writeFileSync(dest, contents);
	} else {
		this.logger.trace(__('No change, skipping %s', dest.cyan));
	}

	this.unmarkBuildDirFile(dest);
};

iOSBuilder.prototype.writeMain = async function writeMain() {
	this.logger.info(__('Creating main.m'));

	const srcFile = path.join(this.platformPath, 'main.m');
	const origSrc = await fs.readFile(srcFile, 'utf8');

	// replace templated values
	const consts = {
		__PROJECT_NAME__:     this.tiapp.name,
		__PROJECT_ID__:       this.tiapp.id,
		__DEPLOYTYPE__:       this.deployType,
		__SHOW_ERROR_CONTROLLER__:       this.showErrorController,
		__APP_ID__:           this.tiapp.id,
		__APP_ANALYTICS__:    String(Object.prototype.hasOwnProperty.call(this.tiapp, 'analytics') ? !!this.tiapp.analytics : true),
		__APP_PUBLISHER__:    this.tiapp.publisher,
		__APP_URL__:          this.tiapp.url,
		__APP_NAME__:         this.tiapp.name,
		__APP_VERSION__:      this.tiapp.version,
		__APP_DESCRIPTION__:  this.tiapp.description,
		__APP_COPYRIGHT__:    this.tiapp.copyright,
		__APP_GUID__:         this.tiapp.guid,
		__APP_RESOURCE_DIR__: '',
		__APP_DEPLOY_TYPE__:  this.buildType
	};
	const contents = origSrc.replace(/(__.+?__)/g, function (match, key) {
		const s = Object.prototype.hasOwnProperty.call(consts, key) ? consts[key] : key;
		return typeof s === 'string' ? s.replace(/"/g, '\\"').replace(/\n/g, '\\n') : s;
	});

	const dest = path.join(this.buildDir, 'main.m');
	if (!fs.existsSync(dest) || contents !== fs.readFileSync(dest, 'utf8')) {
		if (!this.forceRebuild) {
			this.logger.info(__('Forcing rebuild: %s has changed since last build', 'main.m'));
			this.forceRebuild = true;
		}
		this.logger.debug(__('Writing %s', dest.cyan));
		await fs.writeFile(dest, contents);
	} else {
		this.logger.trace(__('No change, skipping %s', dest.cyan));
	}

	this.unmarkBuildDirFile(dest);
};

iOSBuilder.prototype.writeXcodeConfigFiles = function writeXcodeConfigFiles() {
	this.logger.info(__('Creating Xcode config files'));

	// write the project.xcconfig
	let dest = this.xcodeProjectConfigFile,
		contents = [
			'TI_VERSION=' + this.titaniumSdkVersion,
			'TI_SDK_DIR=' + this.platformPath.replace(this.titaniumSdkVersion, '$(TI_VERSION)'),
			'TI_APPID=' + this.tiapp.id,
			'JSCORE_LD_FLAGS=-weak_framework JavaScriptCore',
			'OTHER_LDFLAGS[sdk=iphoneos*]=$(inherited) $(JSCORE_LD_FLAGS)',
			'OTHER_LDFLAGS[sdk=iphonesimulator*]=$(inherited) $(JSCORE_LD_FLAGS)',
			'OTHER_LDFLAGS[sdk=iphoneos9.*]=$(inherited) -weak_framework Contacts -weak_framework ContactsUI -weak_framework WatchConnectivity -weak_framework CoreSpotlight',
			'OTHER_LDFLAGS[sdk=iphonesimulator9.*]=$(inherited) -weak_framework Contacts -weak_framework ContactsUI -weak_framework WatchConnectivity -weak_framework CoreSpotlight',
			'GCC_DEFINITIONS=' + Array.from(this.gccDefs.entries()).map(function ([ key, value ]) { return key + '=' + value; }).join(' '),
			'TI_SYMBOL_MACROS=' + this.tiSymbolMacros,
			'#include "module"'
		].join('\n') + '\n';

	if (!fs.existsSync(dest) || contents !== fs.readFileSync(dest).toString()) {
		if (!this.forceRebuild) {
			this.logger.info(__('Forcing rebuild: %s has changed since last build', path.basename(this.xcodeProjectConfigFile)));
			this.forceRebuild = true;
		}
		this.logger.debug(__('Writing %s', this.xcodeProjectConfigFile.cyan));
		fs.writeFileSync(dest, contents);
	} else {
		this.logger.trace(__('No change, skipping %s', this.xcodeProjectConfigFile.cyan));
	}
	this.unmarkBuildDirFile(dest);

	// write the module.xcconfig
	const variables = {};
	dest = path.join(this.buildDir, 'module.xcconfig');
	contents = [
		'// this is a generated file - DO NOT EDIT',
		''
	];

	this.modules.forEach(function (m) {
		const moduleName = m.manifest.name.toLowerCase(),
			prefix = m.manifest.moduleid.toUpperCase().replace(/\./g, '_');

		[	path.join(m.modulePath, 'module.xcconfig'),
			path.join(this.projectDir, 'modules', 'iphone', moduleName + '.xcconfig')
		].forEach(function (file) {
			if (fs.existsSync(file)) {
				const xc = new appc.xcconfig(file);
				Object.keys(xc).forEach(function (key) {
					var name = (prefix + '_' + key).replace(/[^\w]/g, '_');
					Array.isArray(variables[key]) || (variables[key] = []);
					variables[key].push(name);
					contents.push((name + '=' + xc[key]).replace(new RegExp('\\$\\(' + key + '\\)', 'g'), '$(' + name + ')')); // eslint-disable-line security/detect-non-literal-regexp
				});
			}
		});
	}, this);

	Object.keys(variables).forEach(function (v) {
		contents.push(v + '=$(inherited) '
			+ variables[v].map(function (x) { return '$(' + x + ') '; }).join(''));
	});
	contents = contents.join('\n');

	if (!fs.existsSync(dest) || contents !== fs.readFileSync(dest).toString()) {
		if (!this.forceRebuild) {
			this.logger.info(__('Forcing rebuild: %s has changed since last build', 'module.xcconfig'));
			this.forceRebuild = true;
		}
		this.logger.debug(__('Writing %s', dest.cyan));
		fs.writeFileSync(dest, contents);
	} else {
		this.logger.trace(__('No change, skipping %s', dest.cyan));
	}
	this.unmarkBuildDirFile(dest);
};

iOSBuilder.prototype._scrubiOSSourceFile = function _scrubiOSSourceFile(contents) {
	const name = this.tiapp.name.replace(/[-\W]/g, '_'),
		namespace = /^[0-9]/.test(name) ? 'k' + name : name,
		regexes = [
			// note: order of regexps matters
			[ /TitaniumViewController/g, namespace + '$ViewController' ],
			[ /TitaniumModule/g, namespace + '$Module' ],
			[ /(?!TitaniumKit)(Titanium|Appcelerator)/g, namespace ],
			[ /titanium/g, '_' + namespace.toLowerCase() ],
			[ /(org|com)\.appcelerator/g, '$1.' + namespace.toLowerCase() ],
			[ new RegExp('\\* ' + namespace + ' ' + namespace + ' Mobile', 'g'), '* Appcelerator Titanium Mobile' ], // eslint-disable-line security/detect-non-literal-regexp
			[ new RegExp('\\* Copyright \\(c\\) \\d{4}(-\\d{4})? by ' + namespace + ', Inc\\.', 'g'), '* Copyright (c) 2009-' + (new Date()).getFullYear() + ' by Appcelerator, Inc.' ], // eslint-disable-line security/detect-non-literal-regexp
			[ /(\* Please see the LICENSE included with this distribution for details.\n)(?! \*\s*\* WARNING)/g, '$1 * \n * WARNING: This is generated code. Modify at your own risk and without support.\n' ]
		];

	for (let i = 0; i < regexes.length; i++) {
		contents = contents.replace(regexes[i][0], regexes[i][1]);
	}

	return contents;
};

iOSBuilder.prototype.copyTitaniumiOSFiles = function copyTitaniumiOSFiles() {
	this.logger.info(__('Copying Titanium iOS files'));

	const nameChanged = !this.previousBuildManifest || this.tiapp.name !== this.previousBuildManifest.name,
		name = this.sanitizedAppName(),
		extRegExp = /\.(c|cpp|h|m|mm|swift)$/,

		// files to watch for while copying
		appFiles = {};
	let copyFrameworks = true;

	appFiles['ApplicationDefaults.m'] = {
		props:      this.tiapp.properties || {},
		deployType: this.deployType,
		launchUrl:  this.launchUrl
	};

	appFiles['ApplicationMods.m'] = {
		modules: this.modules
	};

	[ 'Classes', 'Frameworks' ].forEach(function (dir) {
		this.copyDirSync(path.join(this.platformPath, dir), path.join(this.buildDir, dir), {
			ignoreDirs: this.ignoreDirs,
			ignoreFiles: /^(defines\.h|bridge\.txt|libTitanium\.a|\.gitignore|\.npmignore|\.cvsignore|\.DS_Store|\._.*|[Tt]humbs.db|\.vspscc|\.vssscc|\.sublime-project|\.sublime-workspace|\.project|\.tmproj)$/, // eslint-disable-line max-len
			beforeCopy: function (srcFile, destFile, srcStat) {
				var filename = path.basename(srcFile);

				// we skip the ApplicationRouting.m file here because we'll copy it in the encryptJSFiles task below
				if (dir === 'Classes' && (filename === 'ApplicationRouting.m')) {
					this.logger.trace(__('Skipping %s, it\'ll be processed later', (dir + '/' + filename).cyan));
					return null;
				}

				let contents = fs.readFileSync(srcFile),
					changed = false;
				const rel = srcFile.replace(path.dirname(this.titaniumSdkPath) + '/', ''),
					destExists = fs.existsSync(destFile),
					existingContent = destExists && fs.readFileSync(destFile),
					srcHash = this.hash(contents),
					srcMtime = JSON.parse(JSON.stringify(srcStat.mtime));

				this.unmarkBuildDirFile(destFile);

				this.currentBuildManifest.files[rel] = {
					hash: srcHash,
					mtime: srcMtime,
					size: srcStat.size
				};

				if (appFiles[filename]) {
					contents = ejs.render(contents.toString(), appFiles[filename]);
					if (!destExists || contents !== existingContent.toString()) {
						if (!this.forceRebuild) {
							this.logger.info(__('Forcing rebuild: %s has changed since last build', rel));
							this.forceRebuild = true;
						}
						this.logger.debug(__('Writing %s', destFile.cyan));
						fs.writeFileSync(destFile, contents);
					}
					return null;
				}

				if (extRegExp.test(srcFile)) {
					// look up the file to see if the original source changed
					const prev = this.previousBuildManifest.files && this.previousBuildManifest.files[rel];
					if (destExists && !nameChanged && prev && prev.size === srcStat.size && prev.mtime === srcMtime && prev.hash === srcHash && !(dir === 'Frameworks' && !copyFrameworks)) {
						// the original hasn't changed, so let's assume that there's nothing to do
						return null;
					}

					contents = this._scrubiOSSourceFile(contents.toString());
					changed = contents !== existingContent.toString();
				} else {
					changed = !destExists || !bufferEqual(contents, existingContent);
					if (!changed) {
						return null;
					}
				}

				if (!destExists || changed) {
					if (!this.forceRebuild) {
						this.logger.info(__('Forcing rebuild: %s has changed since last build', rel));
						this.forceRebuild = true;
					}

					if (dir === 'Frameworks' && copyFrameworks) {
						// The content of Frameworks directory only change if we change SDK version. So it is safe to copy whole directory.
						// Copy whole 'Frameworks' directory from SDK to build directory, to preserve symlink available in Titaniumkit.xcframework.
						// TODO: Is there any better way to do this?

						// We remove the directory before copying due to frameworks making extensive use of symlinks
						// when copying symlinks over we commonly get errors about trying to place a directory under itself
						// so if anything needs copying, then blow the whole thing away and start fresh
						fs.emptyDirSync(path.join(this.buildDir, dir));
						fs.copySync(path.join(this.platformPath, dir), path.join(this.buildDir, dir));
						copyFrameworks = false;
					}

					this.logger.debug(__('Writing %s', destFile.cyan));
					fs.writeFileSync(destFile, contents);

					return null; // tell copyDirSync not to copy the file because we wrote it ourselves
				} else if (destExists && !changed) {
					// if the destination exists and the file contents haven't changed, return null
					// so that copyDirSync doesn't copy over a file where the contents need to be ran
					// through _scrubiOSSourceFile
					return null;
				}
			}.bind(this),
			afterCopy: function (srcFile, destFile, srcStat, result) {
				if (!result) {
					this.logger.trace(__('No change, skipping %s', destFile.cyan));
				}
			}.bind(this)
		});

		if (dir === 'Frameworks') {
			this.unmarkBuildDirFiles(path.join(this.buildDir, dir));
		}
	}, this);

	function copyAndReplaceFile(src, dest, processContent) {
		const srcStat = fs.statSync(src),
			srcMtime = JSON.parse(JSON.stringify(srcStat.mtime)),
			rel = src.replace(path.dirname(this.titaniumSdkPath) + '/', ''),
			prev = this.previousBuildManifest.files && this.previousBuildManifest.files[rel],
			destDir = path.dirname(dest),
			destExists = fs.existsSync(dest),
			contents = (typeof processContent === 'function' ? processContent(fs.readFileSync(src).toString()) : fs.readFileSync(src).toString()).replace(/Titanium/g, this.tiapp.name),
			hash = this.hash(contents),
			fileChanged = !destExists || !prev || prev.size !== srcStat.size || prev.mtime !== srcMtime || prev.hash !== hash;

		if (fileChanged) {
			this.logger.debug(__('Writing %s', dest.cyan));
			fs.ensureDirSync(destDir);
			fs.writeFileSync(dest, contents);
		} else {
			this.logger.trace(__('No change, skipping %s', dest.cyan));
		}

		this.currentBuildManifest.files[rel] = {
			hash:  contents === null && prev ? prev.hash  : hash || this.hash(contents || ''),
			mtime: contents === null && prev ? prev.mtime : srcMtime,
			size:  contents === null && prev ? prev.size  : srcStat.size
		};

		this.unmarkBuildDirFile(dest);
	}

	copyAndReplaceFile.call(
		this,
		path.join(this.platformPath, 'iphone', 'Titanium_Prefix.pch'),
		path.join(this.buildDir, name + '_Prefix.pch')
	);
	copyAndReplaceFile.call(
		this,
		path.join(this.platformPath, 'iphone', 'Titanium-Bridging-Header.h'),
		path.join(this.buildDir, `${name}-Bridging-Header.h`)
	);
	copyAndReplaceFile.call(
		this,
		path.join(this.platformPath, 'iphone', 'Titanium.xcodeproj', 'xcshareddata', 'xcschemes', 'Titanium.xcscheme'),
		path.join(this.buildDir, this.tiapp.name + '.xcodeproj', 'xcshareddata', 'xcschemes', name + '.xcscheme')
	);
	copyAndReplaceFile.call(
		this,
		path.join(this.platformPath, 'iphone', 'Titanium.xcodeproj', 'project.xcworkspace', 'contents.xcworkspacedata'),
		path.join(this.buildDir, this.tiapp.name + '.xcodeproj', 'project.xcworkspace', 'contents.xcworkspacedata')
	);

	if (this.enableLaunchScreenStoryboard && this.defaultLaunchScreenStoryboard) {
		this.logger.info(__('Installing default %s', 'LaunchScreen.storyboard'.cyan));
		copyAndReplaceFile.call(
			this,
			path.join(this.platformPath, 'iphone', 'LaunchScreen.storyboard'),
			path.join(this.buildDir, 'LaunchScreen.storyboard'),
			function (contents) {
				const bgColor = this.defaultBackgroundColor;
				if (!bgColor) {
					return contents;
				}

				function findNode(node, tags) {
					let child = node.firstChild;
					while (child) {
						if (child.nodeType === 1 && child.tagName === tags[0]) {
							return tags.length === 1 ? child : findNode(child, tags.slice(1));
						}
						child = child.nextSibling;
					}
					return null;
				}

				const dom = new DOMParser({ errorHandler: function () {} }).parseFromString(contents, 'text/xml'),
					colorNode = findNode(dom.documentElement, [ 'scenes', 'scene', 'objects', 'viewController', 'view', 'color' ]);

				if (colorNode) {
					colorNode.setAttribute('red', bgColor.red);
					colorNode.setAttribute('green', bgColor.green);
					colorNode.setAttribute('blue', bgColor.blue);
					colorNode.setAttribute('alpha', 1);
				}

				return '<?xml version="1.0" encoding="UTF-8"?>\n' + dom.documentElement.toString();
			}.bind(this)
		);
	}
};

iOSBuilder.prototype.copyExtensionFiles = function copyExtensionFiles(next) {
	if (!this.extensions.length) {
		return next();
	}

	this.logger.info(__('Copying iOS extensions'));

	async.eachSeries(this.extensions, function (extension, next) {
		const extName = path.basename(extension.projectPath).replace(/\.xcodeproj$/, ''),
			src = path.dirname(extension.projectPath),
			dest = path.join(this.buildDir, 'extensions', path.basename(src));

		this.logger.debug(__('Copying %s', extName.cyan));

		this.copyDirSync(src, dest, {
			rootIgnoreDirs: new RegExp('^(build|' + path.basename(extension.projectPath) + ')$', 'i'), // eslint-disable-line security/detect-non-literal-regexp
			ignoreDirs: this.ignoreDirs,
			ignoreFiles: this.ignoreFiles,
			beforeCopy: function (srcFile, destFile, srcStat) {
				this.unmarkBuildDirFile(destFile);

				// Only check source Info.plist files, not compiled framework Info.plist files
				if (path.basename(srcFile) === 'Info.plist' && !srcFile.includes('.framework')) {
					// validate the info.plist
					const infoPlist = new appc.plist(srcFile);
					if (infoPlist.WKWatchKitApp) {
						infoPlist.WKCompanionAppBundleIdentifier = this.tiapp.id;

						// note: we track whether the versions changed here to not confuse the output with warnings
						// if doing an subsequent build and the extension's Info.plist hasn't changed.
						const origCFBundleShortVersionString = infoPlist.CFBundleShortVersionString,
							changedCFBundleShortVersionString = origCFBundleShortVersionString !== this.infoPlist.CFBundleShortVersionString,
							origCFBundleVersion = infoPlist.CFBundleVersion,
							changedCFBundleVersion = origCFBundleVersion !== this.infoPlist.CFBundleVersion;

						if (changedCFBundleShortVersionString) {
							infoPlist.CFBundleShortVersionString = this.infoPlist.CFBundleShortVersionString;
						}

						if (changedCFBundleVersion) {
							infoPlist.CFBundleVersion = this.infoPlist.CFBundleVersion;
						}

						const contents = infoPlist.toString('xml');
						if (!fs.existsSync(destFile) || contents !== fs.readFileSync(destFile).toString()) {
							if (!this.forceRebuild) {
								this.logger.info(__('Forcing rebuild: iOS Extension "%s" has changed since last build', extName));
								this.forceRebuild = true;
							}
							if (changedCFBundleShortVersionString) {
								this.logger.warn(__('WatchKit App\'s CFBundleShortVersionString "%s" does not match the app\'s CFBundleShortVersionString "%s".', origCFBundleShortVersionString, this.infoPlist.CFBundleShortVersionString));
								this.logger.warn(__('Setting the WatchKit App\'s CFBundleShortVersionString to "%s"', this.infoPlist.CFBundleShortVersionString));
							}
							if (changedCFBundleVersion) {
								this.logger.warn(__('WatchKit App\'s CFBundleVersion "%s" does not match the app\'s CFBundleVersion "%s".', origCFBundleVersion, this.infoPlist.CFBundleVersion));
								this.logger.warn(__('Setting the WatchKit App\'s CFBundleVersion to "%s"', this.infoPlist.CFBundleVersion));
							}
							this.logger.debug(__('Writing %s', destFile.cyan));
							fs.writeFileSync(destFile, contents);
						} else {
							this.logger.trace(__('No change, skipping %s', destFile.cyan));
						}

						return null;
					}
				}

				const prev = this.previousBuildManifest.files && this.previousBuildManifest.files[srcFile],
					srcMtime = JSON.parse(JSON.stringify(srcStat.mtime)),
					srcHash = this.hash(fs.readFileSync(srcFile));

				if (!this.forceRebuild && prev && (prev.size !== srcStat.size || prev.mtime !== srcMtime || prev.hash !== srcHash)) {
					this.logger.info(__('Forcing rebuild: iOS Extension "%s" has changed since last build', extName));
					this.forceRebuild = true;
				}

				this.currentBuildManifest.files[srcFile] = {
					hash: srcHash,
					mtime: srcMtime,
					size: srcStat.size
				};
			}.bind(this),
			afterCopy: function (srcFile, destFile, srcStat, result) {
				if (!result) {
					this.logger.trace(__('No change, skipping %s', destFile.cyan));
				}
			}.bind(this)
		});

		extension.projectPath = path.join(dest, path.basename(extension.projectPath));

		// check if we need to write an entitlements file
		async.eachSeries(Object.keys(extension.targetInfo), function (target, next) {
			if (!extension.targetInfo[target].entitlementsFile) {
				return next();
			}

			const plist = new appc.plist(fs.existsSync(extension.targetInfo[target].entitlementsFile) ? extension.targetInfo[target].entitlementsFile : null);
			this._embedCapabilitiesAndWriteEntitlementsPlist(plist, extension.targetInfo[target].entitlementsFile, true, next);
		}.bind(this), next);
	}.bind(this), next);
};

iOSBuilder.prototype.cleanXcodeDerivedData = function cleanXcodeDerivedData(next) {
	if (!this.forceCleanBuild) {
		return next();
	}

	const exe = this.xcodeEnv.executables.xcodebuild,
		args = [ 'clean', '-UseNewBuildSystem=' + (this.useNewBuildSystem ? 'YES' : 'NO'), '-scheme', this.sanitizedAppName() ];
	let tries = 0,
		lastErr = null,
		done = false;

	this.logger.info(__('Cleaning Xcode derived data'));
	this.logger.debug(__('Invoking: %s', ('DEVELOPER_DIR=' + this.xcodeEnv.path + ' ' + exe + ' ' + args.join(' ')).cyan));

	// If going back and forth between compiling with different iOS SDKs that
	// are tied to different Xcode versions, then it's possible for the
	// CoreSimulator service to panic and cause a "INTERNAL ERROR: Uncaught
	// exception". To fix it, we simply retry up to 3 times. If we detect a
	// failure, we wait 500ms between tries. Super hacky, but seems to work.
	async.whilst(
		function (cb) {
			return cb(null, tries++ < 3 && !done);
		},

		function (cb) {
			const child = spawn(exe, args, {
				cwd: this.buildDir,
				env: {
					DEVELOPER_DIR: this.xcodeEnv.path,
					TMPDIR: process.env.TMPDIR,
					HOME: process.env.HOME,
					PATH: process.env.PATH
				}
			});

			let out = '';
			child.stdout.on('data', function (data) {
				out += data.toString();
			});
			child.stderr.on('data', function (data) {
				out += data.toString();
			});

			child.on('close', function (code) {
				if (code === 65) {
					lastErr = out;
					done = true;
					cb();
				} else if (code) {
					this.logger.debug(__('Retrying to clean project (code %s)', code));
					lastErr = out;
					setTimeout(cb, 500);
				} else if (out.indexOf('INTERNAL ERROR') !== -1) {
					this.logger.debug(__('Retrying to clean project'));
					lastErr = out;
					setTimeout(cb, 500);
				} else {
					done = true;
					lastErr = null;
					fs.ensureDirSync(this.xcodeAppDir);
					out.split('\n').forEach(function (line) {
						line = line.trim();
						line && this.logger.trace(line);
					}, this);
					cb();
				}
			}.bind(this));
		}.bind(this),

		function () {
			if (lastErr) {
				lastErr.split('\n').forEach(function (line) {
					line = line.trim();
					line && this.logger.error(line);
				}, this);
				process.exit(1);
			}
			next();
		}.bind(this)
	);
};

iOSBuilder.prototype.writeDebugProfilePlists = function writeDebugProfilePlists() {
	this.logger.info(__('Creating debugger and profiler plists'));

	function processPlist(filename, host) {
		const src = path.join(this.templatesDir, filename),
			dest = path.join(this.xcodeAppDir, filename),
			exists = fs.existsSync(dest);

		if (host) {
			const prev = this.previousBuildManifest.files && this.previousBuildManifest.files[filename],
				parts = host.split(':'),
				contents = ejs.render(fs.readFileSync(src).toString(), {
					host: parts.length > 0 ? parts[0] : '',
					port: parts.length > 1 ? parts[1] : '',
					airkey: parts.length > 2 ? parts[2] : '',
					hosts: parts.length > 3 ? parts[3] : ''
				}),
				hash = this.hash(contents);

			this.currentBuildManifest.files[filename] = {
				hash:  hash,
				mtime: 0,
				size:  contents.length
			};

			if (!exists || !prev || prev.size !== contents.length || prev.hash !== hash) {
				if (!this.forceRebuild && /device|dist-appstore|dist-macappstore|dist-adhoc/.test(this.target)) {
					this.logger.info(__('Forcing rebuild: %s changed since last build', filename));
					this.forceRebuild = true;
				}
				this.logger.debug(__('Writing %s', dest.cyan));
				fs.writeFileSync(dest, contents);
			} else {
				this.logger.trace(__('No change, skipping %s', dest.cyan));
			}
		} else if (exists) {
			this.logger.debug(__('Removing %s', dest.cyan));
			fs.unlinkSync(dest);
		} else {
			this.logger.debug(__('Skipping %s', dest.cyan));
		}

		this.unmarkBuildDirFile(dest);
	}

	processPlist.call(this, 'debugger.plist', this.debugHost);
	processPlist.call(this, 'profiler.plist', this.profilerHost);
};

iOSBuilder.prototype.copyResources = async function copyResources() {
	const gatheredResults = await this.gatherResources();

	// ok we now have them organized into broad categories
	// we can schedule tasks to happen in parallel:
	return Promise.all([
		this.copyCSSFiles(gatheredResults.cssFiles),
		this.processJSFiles(gatheredResults.jsFiles),
		this.writeAppProps(), // writes _app_props_.json for Ti.Properties
		this.writeEnvironmentVariables(), // writes _env_.json for process.env
		(async () => {
			// processing assets alters the list of resources to copy...
			await this.processAssets(gatheredResults.imageAssets, gatheredResults.launchImages, gatheredResults.launchLogos, gatheredResults.appIcons, gatheredResults.resourcesToCopy);
			// copies any other files that don't require special handling (like JS/CSS do)
			return this.copyUnmodifiedResources(gatheredResults.resourcesToCopy);
		})(),
	]);
};

iOSBuilder.prototype.gatherResources = async function gatherResources() {
	const gather = require('../../../cli/lib/gather');
	const walker = new gather.Walker({
		ignoreDirs: this.ignoreDirs,
		ignoreFiles: this.ignoreFiles,
	});

	this.logger.info(__('Analyzing Resources directory'));
	const firstWave = await Promise.all([
		walker.walk(path.join(this.titaniumSdkPath, 'common', 'Resources', 'ios'), this.xcodeAppDir),
		walker.walk(path.join(this.projectDir, 'platform', 'iphone'), this.buildDir),
		walker.walk(path.join(this.projectDir, 'platform', 'ios'), this.buildDir),
		walker.walk(path.join(this.projectDir, 'Resources'),           this.xcodeAppDir, platformsRegExp),
		walker.walk(path.join(this.projectDir, 'Resources', 'iphone'), this.xcodeAppDir),
		walker.walk(path.join(this.projectDir, 'Resources', 'ios'),    this.xcodeAppDir),
	]);
	let combined = gather.mergeMaps(firstWave);

	const moduleCopier = require('../../../cli/lib/module-copier');
	const dirSet = await moduleCopier.gather(this.projectDir);
	const nodeModuleDirs = Array.from(dirSet);
	const secondWave = await Promise.all(nodeModuleDirs.map(async dir => {
		// here dir is the absolute path to the directory
		// That means we need to construct the relative path to append to this.projectDir and this.xcodeAppDir
		const relativePath = dir.substring(this.projectDir.length + 1);
		return walker.walk(dir, path.join(this.xcodeAppDir, relativePath), null, null, relativePath);
	}));
	// merge the node_modules results on top of the project results... (shouldn't be any conflicts!)
	secondWave.unshift(combined);
	combined = gather.mergeMaps(secondWave);

	this.logger.info(__('Analyzing module files'));
	// detect ambiguous modules
	this.modules.forEach(module => {
		const filename = `${module.id}.js`;
		if (combined.has(filename)) {
			this.logger.error(__('There is a project resource "%s" that conflicts with a native iOS module', filename));
			this.logger.error(__('Please rename the file, then rebuild') + '\n');
			process.exit(1);
		}
	});
	// do modules in parallel - and for each we need to merge the results together!
	const allModulesResults = await Promise.all(this.modules.map(async module => {
		const moduleResults = await Promise.all([
			walker.walk(path.join(module.modulePath, 'assets'), path.join(this.xcodeAppDir, 'modules', module.id.toLowerCase())),
			walker.walk(path.join(module.modulePath, 'platform', 'iphone'), this.buildDir),
			walker.walk(path.join(module.modulePath, 'platform', 'ios'), this.buildDir),
			walker.walk(path.join(module.modulePath, 'Resources'), this.xcodeAppDir, platformsRegExp),
			walker.walk(path.join(module.modulePath, 'Resources', 'iphone'), this.xcodeAppDir),
			walker.walk(path.join(module.modulePath, 'Resources', 'ios'),    this.xcodeAppDir),
		]);
		return gather.mergeMaps(moduleResults);
	}));
	//  merge the allModulesResults over top our current combined!
	allModulesResults.unshift(combined);
	combined = gather.mergeMaps(allModulesResults);

	this.logger.info(__('Analyzing CommonJS modules'));
	const commonJsResults = await Promise.all(this.commonJsModules.map(async module => {
		this.logger.info(__('Analyzing CommonJS module: %s', module.id));
		const dest = path.join(this.xcodeAppDir, path.basename(module.id));
		// Pass in the relative path prefix we should give because we aren't copying direct to the root here.
		// Otherwise index.js in one module "overwrites" index.js in another (because they're at same relative path inside module)
		return walker.walk(module.modulePath, dest, /^(apidoc|docs|documentation|example)$/, null, module.id); // TODO Consult some .moduleignore file in the module or something? .npmignore?
	}));
	// merge the commonJsResults over top our current combined!
	commonJsResults.unshift(combined);
	combined = gather.mergeMaps(commonJsResults);

	// Ok, so we have a Map<string, FileInfo> for the full set of unique relative paths
	// now categorize (i.e. lump into buckets of js/css/html/assets/generic resources)
	const categorizer = new gather.Categorizer({
		tiappIcon: this.tiapp.icon,
		useAppThinning: this.useAppThinning,
		platform: 'ios',
	});
	const categorized = await categorizer.run(combined);

	this.logger.info(__('Analyzing localized launch images'));
	// TODO: this logic is very similar to what we have in gather.Categorizer already! Basically looks in i18n/<lang>/*.png but only inclues those matching LAUNCH_IMAGE_REGEXP
	ti.i18n.findLaunchScreens(this.projectDir, this.logger, { ignoreDirs: this.ignoreDirs }).forEach(launchImage => {
		const parts = launchImage.split('/'),
			file = parts.pop(),
			lang = parts.pop(),
			relPath = path.join(lang + '.lproj', file);

		categorized.launchImages.set(relPath, {
			i18n: lang,
			src: launchImage,
			dest: path.join(this.xcodeAppDir, relPath),
			srcStat: fs.statSync(launchImage)
		});
	});

	return categorized;
};

iOSBuilder.prototype.copyCSSFiles = async function copyCSSFiles(files) {
	this.logger.debug(__('Copying CSS files'));
	const task = new ProcessCSSTask({
		files,
		incrementalDirectory: path.join(this.buildDir, 'incremental', 'process-css'),
		logger: this.logger,
		builder: this,
	});
	return task.run();
};

/**
 * This may modify this.jsFilesToEncrypt
 * @param {Map<string, FileInfo>} jsFilesMap map from filename to file info
 * @returns {Promise<void>}
 */
iOSBuilder.prototype.processJSFiles = async function processJSFiles(jsFilesMap) {
	this.logger.info(__('Processing JavaScript files'));
	const sdkCommonFolder = path.join(this.titaniumSdkPath, 'common', 'Resources', 'ios');
	// For now, need to adapt our Map results to String[] and Object for ProcessJsTask
	const jsFiles = {};
	const inputFiles = [];
	for (let [ key, value ] of jsFilesMap) {
		jsFiles[key] = value;
		inputFiles.push(value.src);
	}
	const jsBootstrapFiles = []; // modified by the task and then used after the fact to write our bootstrap.json file
	const task = new ProcessJsTask({
		inputFiles,
		incrementalDirectory: path.join(this.buildDir, 'incremental', 'process-js'),
		logger: this.logger,
		builder: this,
		jsFiles,
		jsBootstrapFiles,
		sdkCommonFolder,
		defaultAnalyzeOptions: {
			minify: this.minifyJS,
			transpile: this.transpile,
			sourceMap: this.sourceMaps,
			resourcesDir: this.xcodeAppDir,
			logger: this.logger,
			targets: {
				ios: this.minIosVersion
			}
		}
	});
	await task.run();
	if (this.useWebpack) {
		// Merge Ti symbols from Webpack with the ones from legacy js processing
		Object.keys(task.data.tiSymbols).forEach(file => {
			const existingSymbols = this.tiSymbols[file] || [];
			const additionalSymbols = task.data.tiSymbols[file];
			this.tiSymbols[file] = Array.from(new Set(existingSymbols.concat(additionalSymbols)));
		});
	} else {
		this.tiSymbols = task.data.tiSymbols; // record API usage for analytics
	}

	// then write the bootstrap json
	return this.writeBootstrapJson(jsBootstrapFiles);
};

/**
 * @param {string[]} jsBootstrapFiles list of bootstrap js files to add to listing we generate
 * @returns {Promise<void>}
 */
iOSBuilder.prototype.writeBootstrapJson = async function writeBootstrapJson(jsBootstrapFiles) {
	this.logger.info(__('Writing bootstrap json'));

	const originalBootstrapJsonName = path.join('ti.internal', 'bootstrap.json');
	const bootstrapJsonRelativePath = this.encryptJS ? path.join('ti_internal', 'bootstrap_json') : originalBootstrapJsonName;
	const bootstrapJsonAbsolutePath = path.join(this.encryptJS ? this.buildAssetsDir : this.xcodeAppDir, bootstrapJsonRelativePath);
	const bootstrapJsonString = JSON.stringify({ scripts: jsBootstrapFiles });

	if (this.encryptJS) {
		this.jsFilesEncrypted.push(originalBootstrapJsonName); // original name
		this.jsFilesToEncrypt.push(bootstrapJsonRelativePath); // encrypted name
	}

	if (!fs.existsSync(bootstrapJsonAbsolutePath) || (bootstrapJsonString !== fs.readFileSync(bootstrapJsonAbsolutePath, 'utf8'))) {
		this.logger.debug(__('Writing %s', bootstrapJsonAbsolutePath.cyan));

		await fs.ensureDir(path.dirname(bootstrapJsonAbsolutePath));
		await fs.writeFile(bootstrapJsonAbsolutePath, bootstrapJsonString);
	} else {
		this.logger.trace(__('No change, skipping %s', bootstrapJsonAbsolutePath.cyan));
	}

	this.unmarkBuildDirFile(bootstrapJsonAbsolutePath);
};

iOSBuilder.prototype.writeAppProps = async function writeAppProps() {
	this.logger.info(__('Writing app properties'));

	const appPropsFile = this.encryptJS ? path.join(this.buildAssetsDir, '_app_props__json') : path.join(this.xcodeAppDir, '_app_props_.json');
	const props = {};

	if (this.encryptJS) {
		this.jsFilesEncrypted.push('_app_props_.json'); // original name
		this.jsFilesToEncrypt.push('_app_props__json'); // encrypted name
	}

	this.tiapp.properties && Object.keys(this.tiapp.properties).forEach(prop => {
		props[prop] = this.tiapp.properties[prop].value;
	});

	const contents = JSON.stringify(props);
	if (!fs.existsSync(appPropsFile) || contents !== fs.readFileSync(appPropsFile, 'utf8')) {
		this.logger.debug(__('Writing %s', appPropsFile.cyan));

		if (this.encryptJS) {
			await fs.ensureDir(this.buildAssetsDir);
		}
		await fs.writeFile(appPropsFile, contents);
	} else {
		this.logger.trace(__('No change, skipping %s', appPropsFile.cyan));
	}

	this.unmarkBuildDirFile(appPropsFile);
};

iOSBuilder.prototype.writeEnvironmentVariables = async function writeEnvironmentVariables() {
	this.logger.debug(__('Writing ENV variables'));

	const envVarsFile = this.encryptJS ? path.join(this.buildAssetsDir, '_env__json') : path.join(this.xcodeAppDir, '_env_.json');

	if (this.encryptJS) {
		this.jsFilesEncrypted.push('_env_.json'); // original name
		this.jsFilesToEncrypt.push('_env__json'); // encrypted name
	}

	// for non-development builds, DO NOT WRITE OUT ENV VARIABLES TO APP
	const contents = this.writeEnvVars ? JSON.stringify(process.env) : '{}';
	if (!fs.existsSync(envVarsFile) || contents !== fs.readFileSync(envVarsFile, 'utf8')) {
		this.logger.debug(__('Writing %s', envVarsFile.cyan));

		if (this.encryptJS) {
			await fs.ensureDir(this.buildAssetsDir);
		}
		await fs.writeFile(envVarsFile, contents);
	} else {
		this.logger.trace(__('No change, skipping %s', envVarsFile.cyan));
	}

	this.unmarkBuildDirFile(envVarsFile);
};

/**
 * This may modify the list of resources to copy!
 * @param {Map.<string,FileInfo>} imageAssets image assets to handle
 * @param {Map.<string,FileInfo>} launchImages launch image assets to handle
 * @param {Map.<string,FileInfo>} launchLogos launch logos to handle
 * @param {Map.<string,FileInfo>} appIcons icon assets to handle
 * @param {Map.<string,FileInfo>} resourcesToCopy plain files to handle
 */
iOSBuilder.prototype.processAssets = async function processAssets(imageAssets, launchImages, launchLogos, appIcons, resourcesToCopy) {
	this.logger.info(__('Creating asset catalog'));
	await this.writeAssetContentsFile(path.join(this.buildDir, 'Assets.xcassets', 'Contents.json'), {
		info: {
			version: 1,
			author: 'xcode'
		}
	});

	return Promise.all([
		this.generateSemanticColors(), // really just needs the Assets.xcassets folder to be created first
		this.createAssetImageSets(imageAssets, resourcesToCopy), // needs same folder, modifies the resources to copy
		this.createLaunchImageSet(launchImages, resourcesToCopy), // needs same folder, modifies the resources to copy
		this.createAppIconSetAndiTunesArtwork(appIcons, launchLogos, resourcesToCopy), // needs same folder, modifies the resources to copy
	]);
};

/**
 * @param {string} dest destination filepath
 * @param {object} json json to write to file
 */
iOSBuilder.prototype.writeAssetContentsFile = async function writeAssetContentsFile(dest, json) {
	const contents = JSON.stringify(json, null, '  ');

	this.unmarkBuildDirFile(dest);

	if (!(await fs.exists(dest)) || contents !== (await fs.readFile(dest, 'utf8'))) {
		if (!this.forceRebuild) {
			this.logger.info(__('Forcing rebuild: %s has changed since last build', dest.replace(this.projectDir + '/', '')));
			this.forceRebuild = true;
		}
		this.logger.debug(__('Writing %s', dest.cyan));
		const parent = path.dirname(dest);
		await fs.ensureDir(parent);
		return fs.writeFile(dest, contents);
	} else {
		this.logger.trace(__('No change, skipping %s', dest.cyan));
	}
};

iOSBuilder.prototype.generateSemanticColors = async function generateSemanticColors() {
	let colorsFile = path.join(this.projectDir, 'Resources', 'iphone', 'semantic.colors.json');
	if (!(await fs.exists(colorsFile))) {
		// Fallback to root of Resources folder for Classic applications
		colorsFile = path.join(this.projectDir, 'Resources', 'semantic.colors.json');
		if (!(await fs.exists(colorsFile))) {
			this.logger.debug(__('Skipping colorset generation as "semantic.colors.json" file does not exist'));
			return;
		}
	}

	const assetCatalog = path.join(this.buildDir, 'Assets.xcassets');
	const colors = await fs.readJson(colorsFile);

	// FIXME: Do them in parallel
	for (const [ color, colorValue ] of Object.entries(colors)) {
		const colorDir = path.join(assetCatalog, `${color}.colorset`);

		if (!colorValue.light) {
			this.logger.warn(`Skipping ${color} as it does not include a light value`);
			continue;
		}

		if (!colorValue.dark) {
			this.logger.warn(`Skipping ${color} as it does not include a dark value`);
			continue;
		}

		const defaultRGB = Color.fromSemanticColorsEntry(colorValue.default || colorValue.light);
		const lightRGB = Color.fromSemanticColorsEntry(colorValue.light);
		const darkRGB = Color.fromSemanticColorsEntry(colorValue.dark);

		const colorSource = {
			info: {
				version: 1,
				author: 'xcode'
			},
			colors: []
		};

		// Contents.json can hold string or numeric values for colors. 0-255 integers or 0-1 floats.

		// Default
		colorSource.colors.push({
			idiom: 'universal',
			color: {
				'color-space': 'srgb',
				components: {
					red: `${defaultRGB.r}`,
					green: `${defaultRGB.g}`,
					blue: `${defaultRGB.b}`,
					alpha: defaultRGB.alpha.toFixed(3) // explicitly force float with decimal, or it interprets 1 as integer 1/255
				}
			}
		});

		// Light
		colorSource.colors.push({
			idiom: 'universal',
			appearances: [ {
				appearance: 'luminosity',
				value: 'light'
			} ],
			color: {
				'color-space': 'srgb',
				components: {
					red: `${lightRGB.r}`,
					green: `${lightRGB.g}`,
					blue: `${lightRGB.b}`,
					alpha: lightRGB.alpha.toFixed(3) // explicitly force float with decimal, or it interprets 1 as integer 1/255
				}
			}
		});

		// Dark
		colorSource.colors.push({
			idiom: 'universal',
			appearances: [ {
				appearance: 'luminosity',
				value: 'dark'
			} ],
			color: {
				'color-space': 'srgb',
				components: {
					red: `${darkRGB.r}`,
					green: `${darkRGB.g}`,
					blue: `${darkRGB.b}`,
					alpha: darkRGB.alpha.toFixed(3) // explicitly force float with decimal, or it interprets 1 as integer 1/255
				}
			}
		});

		await fs.ensureDir(colorDir);
		await fs.writeJson(path.join(colorDir, 'Contents.json'), colorSource);
		this.unmarkBuildDirFile(path.join(colorDir, 'Contents.json'));
	}
};

/**
 * This may modify the list of resources to copy!
 * @param {Map.<string,FileInfo>} imageAssets image assets to handle
 * @param {Map.<string,FileInfo>} resourcesToCopy plain files to handle
 */
iOSBuilder.prototype.createAssetImageSets = async function createAssetImageSets(imageAssets, resourcesToCopy) {
	if (!this.useAppThinning) {
		this.logger.info(__('App thinning disabled, skipping asset image sets'));
		return;
	}

	this.logger.info(__('Creating assets image set'));
	const assetCatalog = path.join(this.buildDir, 'Assets.xcassets');
	const imageNameRegExp = /^(.*?)(-dark)?(@[23]x)?(~iphone|~ipad)?\.(png|jpg)$/;

	const imageSets = {};
	for (let [ file, info ] of imageAssets) {
		const directories = file.split('/');
		let newPath = '';
		for (let i = 0; i < directories.length - 1; i++) {
			newPath = newPath + '/' + directories[i];

			await this.writeAssetContentsFile(path.join(assetCatalog, newPath, 'Contents.json'), {
				info: {
					version: 1,
					author: 'xcode'
				},
				properties: {
					'provides-namespace': true
				},
			});
		}

		const match = file.match(imageNameRegExp);
		if (match) {
			const imageExt = info.ext;
			const imageSetName = match[1];
			const imageSetRelPath = imageSetName + '.imageset';

			// update image file's destination
			const imageName = info.name;
			const dest = path.join(assetCatalog, imageSetRelPath, imageName + '.' + imageExt);
			info.dest = dest;

			this.unmarkBuildDirFile(dest);

			if (!imageSets[imageSetRelPath]) {
				imageSets[imageSetRelPath] = {
					images: [],
					name: imageSetName
				};
			}

			const imageSet = {
				idiom: !match[4] ? 'universal' : match[4].replace('~', ''),
				filename: imageName + '.' + imageExt,
				scale: !match[3] ? '1x' : match[3].replace('@', '')
			};

			if (match[2]) {
				imageSet.appearances = [ {
					appearance: 'luminosity',
					value: 'dark'
				} ];
			}

			imageSets[imageSetRelPath].images.push(imageSet);
		}
		resourcesToCopy.set(file, imageAssets.get(file));
		resourcesToCopy.get(file).isImage = true;
	}

	// finally create all the Content.json files
	return Promise.all(Object.keys(imageSets).map(async set => {
		return this.writeAssetContentsFile(path.join(assetCatalog, set, 'Contents.json'), {
			images: imageSets[set].images,
			info: {
				version: 1,
				author: 'xcode'
			}
		});
	}));
};

/**
 * This may modify the list of resources to copy!
 * @param {Map.<String,FileInfo>} launchImages launch image assets to handle
 * @param {Map.<string,FileInfo>} resourcesToCopy plain files to handle
 */
iOSBuilder.prototype.createLaunchImageSet = async function createLaunchImageSet(launchImages, resourcesToCopy) {
	this.logger.info(__('Creating launch image set'));

	const launchImageDir = path.join(this.buildDir, 'Assets.xcassets', 'LaunchImage.launchimage');
	await fs.ensureDir(launchImageDir);

	const launchImageSet = {
		images: [],
		info: {
			version: 1,
			author: 'xcode'
		}
	};
	const lookup = {
		// iPhone Portrait - iOS 7-9 - 2x (640x960)
		'Default@2x.png':                { idiom: 'iphone', extent: 'full-screen', minSysVer: '7.0', orientation: 'portrait', width: 640, height: 960, scale: 2 },
		// iPhone Portrait - iOS 7-9 - Retina 4 (640x1136)
		'Default-568h@2x.png':           { idiom: 'iphone', extent: 'full-screen', minSysVer: '7.0', orientation: 'portrait', width: 640, height: 1136, scale: 2, subtype: 'retina4' },
		// iPhone Portrait - iOS 8,9 - Retina HD 4.7 (750x1334) iPhone 6
		'Default-667h@2x.png':           { idiom: 'iphone', extent: 'full-screen', minSysVer: '8.0', orientation: 'portrait', width: 750, height: 1334, scale: 2, subtype: '667h' },

		// iPad Landscape - iOS 7-9 - 1x (1024x768)
		'Default-Landscape.png':         { idiom: 'ipad',   extent: 'full-screen', minSysVer: '7.0', orientation: 'landscape', width: 1024, height: 768, scale: 1 },
		// iPad Landscape - iOS 7-9 - 2x (2048x1536)
		'Default-Landscape@2x.png':      { idiom: 'ipad',   extent: 'full-screen', minSysVer: '7.0', orientation: 'landscape', width: 2048, height: 1536, scale: 2 },
		// iPhone Landscape - iOS 8,9 - Retina HD 5.5 (2208x1242)
		'Default-Landscape-736h@3x.png': { idiom: 'iphone', extent: 'full-screen', minSysVer: '8.0', orientation: 'landscape', width: 2208, height: 1242, scale: 3, subtype: '736h' },
		// iPhone Landscape - iOS 11 - Retina HD iPhone X (2436x1125)
		'Default-Landscape-2436h@3x.png': { idiom: 'iphone', extent: 'full-screen', minSysVer: '11.0', orientation: 'landscape', width: 2436, height: 1125, scale: 3, subtype: '2436h' },
		// iPhone Landscape - iOS 12 - Retina HD iPhone X Max (2688x1242)
		'Default-Landscape-2688h@3x.png': { idiom: 'iphone', extent: 'full-screen', minSysVer: '12.0', orientation: 'landscape', width: 2688, height: 1242, scale: 3, subtype: '2688h' },
		// iPhone Landscape - iOS 12 - Retina iPhone XR (1792x828)
		'Default-Landscape-1792h@2x.png': { idiom: 'iphone', extent: 'full-screen', minSysVer: '12.0', orientation: 'landscape', width: 1792, height: 828, scale: 2, subtype: '1792h' },

		// iPad Portrait - iOS 7-9 - 1x (????)
		'Default-Portrait.png':          { idiom: 'ipad',   extent: 'full-screen', minSysVer: '7.0', orientation: 'portrait', width: 768, height: 1024, scale: 1 },
		// iPad Portrait - iOS 7-9 - 2x (????)
		'Default-Portrait@2x.png':       { idiom: 'ipad',   extent: 'full-screen', minSysVer: '7.0', orientation: 'portrait', width: 1536, height: 2048, scale: 2 },
		// iPhone Portrait - iOS 8,9 - Retina HD 5.5 (1242x2208)
		'Default-Portrait-736h@3x.png':  { idiom: 'iphone', extent: 'full-screen', minSysVer: '8.0', orientation: 'portrait', width: 1242, height: 2208, scale: 3, subtype: '736h' },
		// iPhone Portrait - iOS 11 - Retina HD iPhone X (1125x2436)
		'Default-Portrait-2436h@3x.png':  { idiom: 'iphone', extent: 'full-screen', minSysVer: '11.0', orientation: 'portrait', width: 1125, height: 2436, scale: 3, subtype: '2436h' },
		// iPhone Portrait - iOS 12 - Retina HD iPhone X Max (1242x2688)
		'Default-Portrait-2688h@3x.png':  { idiom: 'iphone', extent: 'full-screen', minSysVer: '12.0', orientation: 'portrait', width: 1242, height: 2688, scale: 3, subtype: '2688h' },
		// iPhone Portrait - iOS 12 - Retina iPhone XR (828x1792)
		'Default-Portrait-1792h@2x.png':  { idiom: 'iphone', extent: 'full-screen', minSysVer: '12.0', orientation: 'portrait', width: 828, height: 1792, scale: 2, subtype: '1792h' }
	};
	const found = {};
	launchImages.forEach((info, filename) => {
		let meta = lookup[filename];

		if (info.i18n) {
			meta = lookup[path.basename(filename)];
		}

		if (!meta) {
			// we don't care about this image
			this.logger.debug(__('Unsupported launch image %s, skipping', path.relative(this.projectDir, info.src).cyan));
			return;
		}

		// skip device specific launch images
		if (this.deviceFamily === 'iphone' && meta.idiom !== 'iphone') {
			this.logger.debug(__('Skipping iPad launch image: %s', path.relative(this.projectDir, info.src).cyan));
			return;
		}

		if (this.deviceFamily === 'ipad' && meta.idiom !== 'ipad') {
			this.logger.debug(__('Skipping iPhone launch image: %s', path.relative(this.projectDir, info.src).cyan));
			return;
		}

		if (!info.i18n) {
			const img = {
				extent: meta.extent,
				idiom: meta.idiom,
				filename: filename,
				'minimum-system-version': meta.minSysVer,
				orientation: meta.orientation,
				scale: meta.scale + 'x'
			};
			meta.subtype && (img.subtype = meta.subtype);
			launchImageSet.images.push(img);

			// only override the dest if this is NOT an i18n image
			info.dest = path.join(launchImageDir, filename);
		}

		found[info.i18n || '_'] || (found[info.i18n || '_'] = {});
		found[info.i18n || '_'][path.basename(filename)] = 1;

		resourcesToCopy.set(filename, info);
	});

	// determine if we're missing any launch images
	const missing = {};
	let totalMissing = 0;
	Object.keys(found).forEach(lang => {
		Object.keys(lookup).forEach(filename => {
			if (!found[lang][filename] && (this.deviceFamily !== 'ipad' || lookup[filename].idiom === 'ipad') && (this.deviceFamily !== 'iphone' || lookup[filename].idiom === 'iphone')) {
				missing[lang] || (missing[lang] = {});
				missing[lang][filename] = 1;
				totalMissing++;
			}
		});
	});

	if (totalMissing) {
		// we have missing launch images :(
		this.logger.warn(__n('Missing a launch image:', 'Missing %s launch images:', totalMissing));
		Object.keys(missing).forEach(lang => {
			this.logger.warn('  ' + (lang === '_' ? __('Default') : 'i18n/' + lang));
			Object.keys(missing[lang]).forEach(filename => {
				const meta = lookup[filename];
				this.logger.warn('    '
					+ __('%s - Used for %s - dimensions: %sx%s, orientation: %s',
						filename,
						meta.idiom === 'ipad' ? 'iPad' : 'iPhone',
						meta.width,
						meta.height,
						meta.orientation
					)
				);
			});
		});
	}

	return this.writeAssetContentsFile(path.join(launchImageDir, 'Contents.json'), launchImageSet);
};

/**
 * This may modify the list of resources to copy!
 * @param {Map.<String,FileInfo>} appIcons app icons to handle
 * @param {Map.<String,FileInfo>} launchLogos launch logos to process
 * @param {Map.<string,FileInfo>} resourcesToCopy plain files to handle
 */
iOSBuilder.prototype.createAppIconSetAndiTunesArtwork = async function createAppIconSetAndiTunesArtwork(appIcons, launchLogos, resourcesToCopy) {
	this.logger.info(__('Creating app icon set'));

	// check for default icon
	let defaultIconChanged = false;
	let defaultIconHasAlpha = false;
	const defaultIcon = this.defaultIcons.find(icon => fs.existsSync(icon));
	const flattenedDefaultIconDest = path.join(this.buildDir, 'DefaultIcon.png');

	if (defaultIcon) {
		const defaultIconPrev = this.previousBuildManifest.files && this.previousBuildManifest.files['DefaultIcon.png'],
			defaultIconContents = fs.readFileSync(defaultIcon),
			defaultIconInfo = appc.image.pngInfo(defaultIconContents),
			defaultIconExists = !defaultIconInfo.alpha || fs.existsSync(flattenedDefaultIconDest),
			defaultIconStat = defaultIconExists && fs.statSync(defaultIconInfo.alpha ? flattenedDefaultIconDest : defaultIcon),
			defaultIconMtime = defaultIconExists && JSON.parse(JSON.stringify(defaultIconStat.mtime)),
			defaultIconHash = this.hash(defaultIconContents);

		if (!defaultIconExists
			|| !defaultIconPrev
			|| defaultIconPrev.size !== defaultIconStat.size
			|| defaultIconPrev.mtime !== defaultIconMtime
			|| defaultIconPrev.hash !== defaultIconHash) {
			defaultIconChanged = true;
		}

		defaultIconHasAlpha = defaultIconInfo.alpha;

		this.currentBuildManifest.files['DefaultIcon.png'] = {
			hash: defaultIconHash,
			mtime: defaultIconMtime,
			size: defaultIconStat.size
		};
	}

	// The original list of icons we may need (we trim this down as we match them)
	const lookup = {
		'-Small':       { height: 29,   width: 29,   scale: 1, idioms: [ 'ipad' ] },
		'-Small@2x':    { height: 29,   width: 29,   scale: 2, idioms: [ 'iphone', 'ipad' ] },
		'-Small@3x':    { height: 29,   width: 29,   scale: 3, idioms: [ 'iphone' ] },
		'-Small-40':    { height: 40,   width: 40,   scale: 1, idioms: [ 'ipad' ] },
		'-Small-40@2x': { height: 40,   width: 40,   scale: 2, idioms: [ 'iphone', 'ipad' ] },
		'-Small-40@3x': { height: 40,   width: 40,   scale: 3, idioms: [ 'iphone' ] },
		'-60@2x':       { height: 60,   width: 60,   scale: 2, idioms: [ 'iphone' ], required: true },
		'-60@3x':       { height: 60,   width: 60,   scale: 3, idioms: [ 'iphone' ], required: true },
		'-76':          { height: 76,   width: 76,   scale: 1, idioms: [ 'ipad' ], required: true },
		'-76@2x':       { height: 76,   width: 76,   scale: 2, idioms: [ 'ipad' ], required: true },
		'-83.5@2x':     { height: 83.5, width: 83.5, scale: 2, idioms: [ 'ipad' ], minXcodeVer: '7.2' },
		'-Marketing':   { height: 1024, width: 1024, scale: 1, idioms: [ 'ios-marketing' ], required: true, minXcodeVer: '9.0' }
	};
	// Add macOS icons if target is macOS
	if (this.target === 'macos' || this.target === 'dist-macappstore') {
		Object.assign(lookup, {
			'-16':    		{ height: 16, width: 16, scale: 1, idioms: [ 'mac' ], required: true, minXcodeVer: '12.0' },
			'-16@2x':    	{ height: 16, width: 16, scale: 2, idioms: [ 'mac' ], required: true, minXcodeVer: '12.0' },
			'-32':    		{ height: 32, width: 32, scale: 1, idioms: [ 'mac' ], required: true, minXcodeVer: '12.0' },
			'-32@2x':    	{ height: 32, width: 32, scale: 2, idioms: [ 'mac' ], required: true, minXcodeVer: '12.0' },
			'-128':    		{ height: 128, width: 128, scale: 1, idioms: [ 'mac' ], required: true, minXcodeVer: '12.0' },
			'-128@2x':    	{ height: 128, width: 128, scale: 2, idioms: [ 'mac' ], required: true, minXcodeVer: '12.0' },
			'-256':    		{ height: 256, width: 256, scale: 1, idioms: [ 'mac' ], required: true, minXcodeVer: '12.0' },
			'-256@2x':    	{ height: 256, width: 256, scale: 2, idioms: [ 'mac' ], required: true, minXcodeVer: '12.0' },
			'-AppStore':    { height: 512, width: 512, scale: 1, idioms: [ 'mac' ], required: true, minXcodeVer: '12.0' },
			'-AppStore@2x': { height: 512, width: 512, scale: 2, idioms: [ 'mac' ], required: true, minXcodeVer: '12.0' }
		});
	}
	const deviceFamily = this.deviceFamily;
	// filter out ones we don't need for our target
	Object.keys(lookup).forEach(key => {
		if (deviceFamily === 'iphone' && !lookup[key].idioms.includes('iphone') && !lookup[key].idioms.includes('ios-marketing')) {
			// remove ipad only
			delete lookup[key];
		} else if (deviceFamily === 'ipad' && !lookup[key].idioms.includes('ipad') && !lookup[key].idioms.includes('ios-marketing')) {
			// remove iphone only
			delete lookup[key];
		} else if (lookup[key].minXcodeVer && appc.version.lt(this.xcodeEnv.version, lookup[key].minXcodeVer)) {
			// remove unsupported
			delete lookup[key];
		}
	});

	const appIconSetDir = path.join(this.buildDir, 'Assets.xcassets', 'AppIcon.appiconset');
	await fs.ensureDir(appIconSetDir);

	// Generate the app icon set
	const appIconSet = {
		images: [],
		info: {
			version: 1,
			author: 'xcode'
		}
	};
	const flattenIcons = []; // icons with alpha channel to "flatten" with white bg
	// FIXME: Do these in parallel
	// This goes through the app icons and tries to:
	// validate it matches one of the ones we need
	// its height/width matches what we need
	// it's square
	// check if it has alpha and needs to be "flattened"
	// if not, we can copy as-is
	// so for each icon we need to know: is it valid/usable? if usable, does it need to be flattened?
	// if usable, we expand out into appIconSet and remove from lookup map (this is how we keep track of what's missing)
	// if we don't need to flatten, we add to resources to copy (after chnaging dest to iconset asset catalog)
	// if we do need to flatten we eventually merge over top white bg
	appIcons.forEach((info, filename) => {
		if (!info.tag) {
			// probably appicon.png, we don't care so skip it
			return;
		}

		if (!lookup[info.tag]) {
			// we don't care about this image
			this.logger.debug(__('Unsupported app icon %s, skipping', info.src.replace(this.projectDir + '/', '').cyan));
			return;
		}

		const contents = fs.readFileSync(info.src);
		const pngInfo = appc.image.pngInfo(contents);

		// check that the app icon is square
		if (pngInfo.width !== pngInfo.height) {
			this.logger.warn(__('Skipping app icon %s because dimensions (%sx%s) are not equal', info.src.replace(this.projectDir + '/', ''), pngInfo.width, pngInfo.height));
			return;
		}

		// validate the app icon meets the requirements
		const meta = lookup[info.tag];
		const w = meta.width * meta.scale;
		if (pngInfo.width !== w) { // we enforce it's square above, so no need to check height too
			const h = meta.height * meta.scale;
			this.logger.warn(__('Expected app icon %s to be %sx%s, but was %sx%s, skipping', info.src.replace(this.projectDir + '/', ''), w, h, pngInfo.width, pngInfo.height));
			return;
		}

		let flatten = false;
		if (pngInfo.alpha) {
			if (defaultIcon && !defaultIconHasAlpha) {
				this.logger.warn(__('Skipping %s because it has an alpha channel and generating one from %s', info.src.replace(this.projectDir + '/', ''), defaultIcon.replace(this.projectDir + '/', '')));
				return;
			}

			this.logger.warn(__('%s contains an alpha channel and will be flattened against a white background', info.src.replace(this.projectDir + '/', '')));
			flatten = true;
			flattenIcons.push(info);
		}

		// inject images into the app icon set
		meta.idioms.forEach(function (idiom) {
			appIconSet.images.push({
				size:     meta.width + 'x' + meta.height,
				idiom:    idiom,
				filename: filename,
				scale:    meta.scale + 'x'
			});
		});

		delete lookup[info.tag];

		info.dest = path.join(appIconSetDir, filename);

		if (!flatten) {
			this.logger.debug(__('Found valid app icon %s (%sx%s)', info.src.replace(this.projectDir + '/', '').cyan, pngInfo.width, pngInfo.height));
			info.contents = contents;
			resourcesToCopy.set(filename, info);
		}
	});

	let missingIcons = [];
	if (this.target === 'dist-adhoc') {
		missingIcons = missingIcons.concat(await this.copyiTunesArtwork());
	}
	missingIcons = missingIcons.concat(await this.writeAppIconSet(lookup, appIconSetDir, appIconSet, defaultIconChanged));
	// we may punt and generate launch logos from the default icon if there's no logo to resize...
	missingIcons = missingIcons.concat(await this.processLaunchLogos(launchLogos, resourcesToCopy, defaultIcon, defaultIconChanged));

	// Do we need to flatten the default icon?
	if (missingIcons.length !== 0 && defaultIcon && defaultIconChanged && defaultIconHasAlpha) {
		this.defaultIcons = [ flattenedDefaultIconDest ];
		flattenIcons.push({
			name: path.basename(defaultIcon),
			src: defaultIcon,
			dest: flattenedDefaultIconDest
		});
		this.logger.warn(__('The default icon "%s" contains an alpha channel and will be flattened against a white background', defaultIcon.replace(this.projectDir + '/', '')));
		this.logger.warn(__('You may create an image named "DefaultIcon-ios.png" that does not have an alpha channel in the root of your project'));
		this.logger.warn(__('It is highly recommended that the DefaultIcon.png be 1024x1024'));
	}

	// FIXME: use-plimit to limit 5 at a time again?
	// Can we flatten and generate app icons in parallel? I think not because we may be flattening the default icon!
	await Promise.all(flattenIcons.map(icon => this.flattenIcon(icon)));

	// If there are no missing icons we're done!
	if (!missingIcons.length) {
		return;
	}

	// Turn callback api to promise...
	const boundGenerateAppIcons = util.promisify(this.generateAppIcons).bind(this);
	if (!defaultIcon) {
		// we're going to fail, but we let generateAppIcons() do the dirty work
		return boundGenerateAppIcons(missingIcons);
	}

	if (!defaultIconChanged) {
		// we have missing icons, but the default icon hasn't changed
		// call generateAppIcons() and have it deal with determining if the icons need
		// to be generated or if it needs to error out
		return boundGenerateAppIcons(missingIcons);
	}

	if (!this.forceRebuild) {
		this.logger.info(__('Forcing rebuild: %s changed since last build', defaultIcon.replace(this.projectDir + '/', '')));
		this.forceRebuild = true;
	}

	return boundGenerateAppIcons(missingIcons);
};

/**
 * "Flattens" an icon with an alpha channel over top a white background
 * If it was the default icon we update the build manifest
 * @param {FileInfo} icon icon metadata with alpha to flatten over white
 * @param {string} flattenedDefaultIconDest filepath to flattened default icon (used to determine if we're flattening the default icon)
 * @returns {Promise<void>}
 */
iOSBuilder.prototype.flattenIcon = function flattenIcon(icon, flattenedDefaultIconDest) {
	return new Promise((resolve, reject) => {
		this.logger.debug(__('Stripping alpha channel: %s => %s', icon.src.cyan, icon.dest.cyan));
		const _t = this;
		fs.createReadStream(icon.src)
			.pipe(new PNG({
				colorType: 2,
				bgColor: {
					red: 255,
					green: 255,
					blue: 255
				}
			}))
			.on('parsed', function () {
				if (icon.dest === flattenedDefaultIconDest) {
					// if the icon we just flattened is the DefaultIcon, then we need to
					// update the currentBuildManifest which means we can't just pipe the
					// the flattened icon to disk, we need to compute the hash and stat it
					const buf = [];
					this.pack()
						.on('data', function (bytes) {
							buf.push(Buffer.from(bytes));
						})
						.on('end', function (err) {
							if (err) {
								return reject(err);
							}

							const contents = Buffer.concat(buf);
							fs.writeFileSync(icon.dest, contents);

							const stat = fs.statSync(icon.dest);
							_t.currentBuildManifest.files['DefaultIcon.png'] = {
								hash: _t.hash(contents),
								mtime: JSON.parse(JSON.stringify(stat.mtime)),
								size: stat.size
							};

							resolve();
						});
					return;
				}

				this.pack()
					.on('end', err => {
						if (err) {
							return reject(err);
						}
						resolve();
					})
					.pipe(fs.createWriteStream(icon.dest));
			});
	});
};

/**
 * This may modify the list of resources to copy!
 * @param {Map.<String,FileInfo>} launchLogos logos to handle
 * @param {Map.<string,FileInfo>} resourcesToCopy plain files to handle
 * @param {string} defaultIcon filepath the the default icon
 * @param {boolean} defaultIconChanged did the default icon change this build?
 * @returns {object[]} missing launch logos to generate from default icon
 */
iOSBuilder.prototype.processLaunchLogos = async function processLaunchLogos(launchLogos, resourcesToCopy, defaultIcon, defaultIconChanged) {
	if (!this.enableLaunchScreenStoryboard || !this.defaultLaunchScreenStoryboard) {
		return [];
	}

	this.logger.info(__('Creating launch logo image set'));

	const assetCatalogDir = path.join(this.buildDir, 'Assets.xcassets', 'LaunchLogo.imageset');
	await fs.ensureDir(assetCatalogDir);

	const lookup = {
		'LaunchLogo~iphone':    { idiom: 'iphone', scale: 1, size: 320 },
		'LaunchLogo@2x~iphone': { idiom: 'iphone', scale: 2, size: 374 },
		'LaunchLogo@3x~iphone': { idiom: 'iphone', scale: 3, size: 621 },
		'LaunchLogo~ipad':      { idiom: 'ipad', scale: 1, size: 384 },
		'LaunchLogo@2x~ipad':   { idiom: 'ipad', scale: 2, size: 1024 }
	};
	// loop over each of the launch logos that we found, then for each remove it from the lookup
	// anything left in the lookup will be considered missing
	let launchLogo = null;
	const images = [];
	if (launchLogos.size !== 0) {
		launchLogos.forEach((img, file) => {
			if (img.name === 'LaunchLogo') {
				launchLogo = img;
				return;
			}

			if (!lookup[img.name]) {
				return;
			}
			delete lookup[img.name];

			images.push({
				// size?
				idiom: img.device || 'universal',
				filename: img.name + '.' + img.ext,
				scale: (img.scale || 1) + 'x'
			});

			const dest = path.join(assetCatalogDir, img.name + '.' + img.ext);
			img.dest = dest;
			resourcesToCopy.set(file, img);
		});
	}

	// if there's anything left in the `lookup`, then they are missing
	const missingCount = Object.keys(lookup).length;
	const missingLaunchLogos = [];
	if (missingCount !== 0) {
		// We have no LaunchLogo.png or DefaultIcon.png, copy our template logos and move on
		if (!launchLogo && !defaultIcon) {
			await this.copyDefaultLaunchLogos(assetCatalogDir, resourcesToCopy);
			// Don't generate any logos from icon (because there was none and we're copying default set of launch logos)
			return [];
		}

		let changed = false;
		const prev = this.previousBuildManifest.files && this.previousBuildManifest.files['LaunchLogo.png'];

		if (launchLogo) {
			// sanity check that LaunchLogo is usable
			const stat = fs.statSync(launchLogo.src),
				mtime = JSON.parse(JSON.stringify(stat.mtime)),
				launchLogoContents = fs.readFileSync(launchLogo.src),
				hash = this.hash(launchLogoContents);

			changed = !prev || prev.size !== stat.size || prev.mtime !== mtime || prev.hash !== hash;

			this.currentBuildManifest.files['LaunchLogo.png'] = {
				hash: hash,
				mtime: mtime,
				size: stat.size
			};

			if (changed) {
				const launchLogoInfo = appc.image.pngInfo(launchLogoContents);
				if (launchLogoInfo.width !== 1024 || launchLogoInfo.height !== 1024) {
					this.logger.warn(__('Found LaunchLogo.png that is %sx%s, however the size must be 1024x1024', launchLogoInfo.width, launchLogoInfo.height));
					launchLogo = null;
				}
			}
		} else {
			// using the DefaultIcon.png
			const cur = this.currentBuildManifest.files['LaunchLogo.png'] = this.currentBuildManifest.files['DefaultIcon.png'];
			if (defaultIconChanged || !prev || prev.size !== cur.size || prev.mtime !== cur.mtime || prev.hash !== cur.hash) {
				changed = true;
			}
		}

		let logged = false;

		// build the list of images to be generated
		Object.keys(lookup).forEach(name => {
			const spec = lookup[name],
				filename = name + '.png',
				dest = path.join(assetCatalogDir, filename),
				desc = __('%s - Used for %s - size: %sx%s',
					name,
					spec.idiom,
					spec.size,
					spec.size
				);

			images.push({
				idiom: spec.idiom,
				filename: filename,
				scale: spec.scale + 'x'
			});

			this.unmarkBuildDirFile(dest);

			// if the source image hasn't changed, then don't need to regenerate the missing launch logos
			if (!changed && fs.existsSync(dest)) {
				this.logger.trace(__('Found generated %sx%s launch logo: %s', spec.size, spec.size, dest.cyan));
				return;
			}

			missingLaunchLogos.push({
				description: desc,
				file: dest,
				width: spec.size,
				height: spec.size,
				required: false
			});

			if (!logged) {
				logged = true;
				this.logger.info(__n(
					'Missing %s launch logo, generating missing launch logo from %%s',
					'Missing %s launch logos, generating missing launch logos from %%s',
					missingCount,
					launchLogo ? 'LaunchLogo.png' : 'DefaultIcon.png'
				));
			}

			if (launchLogo) {
				this.logger.info('  ' + desc);
			}
		});
	}

	await this.writeAssetContentsFile(path.join(assetCatalogDir, 'Contents.json'), {
		images: images,
		info: {
			version: 1,
			author: 'xcode'
		}
	});

	// If there are no missing launch logos, we're done
	if (!missingLaunchLogos.length) {
		return [];
	}

	if (!this.forceRebuild) {
		this.logger.info(__('Forcing rebuild: launch logos changed since last build'));
		this.forceRebuild = true;
	}

	if (!this.buildOnly && (this.target === 'device' || this.target === 'simulator')) {
		this.logger.warn(__('If this app has been previously installed on this %s, you may need restart it to see the latest launch logo', this.target));
		this.logger.warn(__('iOS renders and caches the launch screen to a PNG image that seems to only be invalidated by restarting iOS'));
	}

	if (!launchLogo) {
		// just use the DefaultIcon.png to generate the missing LaunchLogos
		return missingLaunchLogos;
	}

	// Resize the launch logo to the other dimensions needed
	await new Promise((resolve) => {
		appc.image.resize(launchLogo.src, missingLaunchLogos, error => {
			if (error) {
				this.logger.error(error);
				this.logger.log();
				process.exit(1);
				// TODO: Instead call reject?
			}
			resolve();
		}, this.logger);
	});
	// we generated the launch logos ourselves, so return none left to generate (from default icon)!
	return [];
};

/**
 * This modifies the input map to add files to queue up to copy
 * @param {string} assetCatalogDir filepath to asset catalog
 * @param {Map.<String,FileInfo>} resourcesToCopy plain resources to copy
 */
iOSBuilder.prototype.copyDefaultLaunchLogos = async function copyDefaultLaunchLogos(assetCatalogDir, resourcesToCopy) {
	this.logger.warn(__('No DefaultIcon.png found, copying default Titanium LaunchLogo images'));

	// copy the default launch logos (by queueing them up in our resourcesToCopy map)
	const defaultLaunchLogosDir = path.join(this.platformPath, 'iphone', 'Assets.xcassets', 'LaunchLogo.imageset');
	const defaultFilesRegExp = /\.(json|png)$/;
	const dirs = await fs.readdir(defaultLaunchLogosDir);
	dirs.forEach(filename => {
		const file = path.join(defaultLaunchLogosDir, filename);
		if (defaultFilesRegExp.test(filename) && fs.statSync(file).isFile()) {
			resourcesToCopy.set(filename, {
				src: file,
				dest: path.join(assetCatalogDir, filename)
			});
		}
	});
};

/**
 * @returns {Promise<object[]>} missing icons
 */
iOSBuilder.prototype.copyiTunesArtwork = async function copyiTunesArtwork() {
	this.logger.info(__('Copying iTunes artwork'));

	const missingIcons = [];

	const artworkFiles = [
		{ filename: 'iTunesArtwork', size: 512 },
		{ filename: 'iTunesArtwork@2x', size: 1024 }
	];

	await Promise.all(artworkFiles.map(async artwork => {
		const dest = path.join(this.xcodeAppDir, artwork.filename);

		this.unmarkBuildDirFile(dest);

		try {
			const src = path.join(this.projectDir, artwork.filename);
			if (!(await fs.exists(src))) { // TODO: just try and read it, it should throw if it doesn't exist
				throw new Error();
			}

			const contents = await fs.readFile(src);
			const pngInfo = appc.image.pngInfo(contents);

			if (pngInfo.width !== artwork.size || pngInfo.height !== artwork.size) {
				this.logger.warn(__('Skipping %s because dimensions (%sx%s) are wrong; should be %sx%s', artwork.filename, pngInfo.width, pngInfo.height, artwork.size, artwork.size));
				throw new Error();
			}

			if (pngInfo.alpha) {
				this.logger.warn(__('Skipping %s because iTunesArtwork must not have an alpha channel', artwork.filename));
				throw new Error();
			}

			if (!this.copyFileSync(src, dest, { contents: contents })) {
				this.logger.trace(__('No change, skipping %s', dest.cyan));
			}
		} catch (ex) {
			missingIcons.push({
				description: __('%s - Used for Ad Hoc dist', artwork.filename),
				file: dest,
				width: artwork.size,
				height: artwork.size,
				required: false
			});
		}
	}));
	return missingIcons;
};

/**
 * write the app icon set, and gather up missing icons we should try to generate
 * @param {object} lookup icons we don't have
 * @param {string} appIconSetDir filepath to dir we're writing our app icon set
 * @param {object} appIconSet app icon set we're building/writing (we will modify this)
 * @param {boolean} defaultIconChanged did the default icon change this build?
 * @returns {Promise<object[]>} missing icons
 */
iOSBuilder.prototype.writeAppIconSet = async function writeAppIconSet(lookup, appIconSetDir, appIconSet, defaultIconChanged) {
	if (Object.keys(lookup).length === 0) {
		// wow, we had all of the icons! amazing!
		if (this.target === 'dist-adhoc') {
			this.logger.debug(__('All app icons and iTunes artwork are present and are correct'));
		} else {
			this.logger.debug(__('All app icons are present and are correct'));
		}
		await this.writeAssetContentsFile(path.join(appIconSetDir, 'Contents.json'), appIconSet);
		return [];
	}

	const missingIcons = [];
	Object.keys(lookup).forEach(key => {
		const filename = this.tiapp.icon.replace(/\.png$/, '') + key + '.png';
		const dest = path.join(appIconSetDir, filename);
		this.unmarkBuildDirFile(dest);

		// inject images into the app icon set
		const meta = lookup[key];
		meta.idioms.forEach(function (idiom) {
			appIconSet.images.push({
				size:     meta.width + 'x' + meta.height,
				idiom:    idiom,
				filename: filename,
				scale:    meta.scale + 'x'
			});
		});

		// check if the icon was previously resized
		const width = meta.width * meta.scale;
		const height = meta.height * meta.scale;
		if (!defaultIconChanged && fs.existsSync(dest)) {
			const contents = fs.readFileSync(dest),
				pngInfo = appc.image.pngInfo(contents);

			if (pngInfo.width === width && pngInfo.height === height) {
				this.logger.trace(__('Found generated %sx%s app icon: %s', width, height, dest.cyan));
				// icon looks good, no need to generate it!
				return;
			}
		}

		missingIcons.push({
			description: __('%s - Used for %s',
				filename,
				meta.idioms.map(function (i) { return i === 'ipad' ? 'iPad' : 'iPhone'; }).join(', ')
			),
			file: dest,
			width: width,
			height: height,
			required: !!meta.required
		});
	});

	await this.writeAssetContentsFile(path.join(appIconSetDir, 'Contents.json'), appIconSet);
	return missingIcons;
};

/**
 * Copies all the rest of the files that need no extra processing.
 * @param {Map<string, object>} resourcesToCopy filepaths to file info
 * @returns {Promise<void>}
 */
iOSBuilder.prototype.copyUnmodifiedResources = async function copyUnmodifiedResources(resourcesToCopy) {
	this.logger.debug(__('Copying resources'));
	const task = new CopyResourcesTask({
		incrementalDirectory: path.join(this.buildDir, 'incremental', 'copy-resources'),
		logger: this.logger,
		builder: this,
		files: resourcesToCopy
	});
	return task.run();
};

iOSBuilder.prototype.encryptJSFiles = function encryptJSFiles(next) {
	const rel = 'Classes/ApplicationRouting.m',
		dest = path.join(this.buildDir, 'Classes', 'ApplicationRouting.m'),
		destExists = fs.existsSync(dest),
		destStat = destExists && fs.statSync(dest),
		existingContent = destExists && fs.readFileSync(dest).toString(),
		prev = this.previousBuildManifest.files && this.previousBuildManifest.files[rel];

	this.unmarkBuildDirFile(dest);

	if (!this.encryptJS || !this.jsFilesToEncrypt.length) {
		const srcFile = path.join(this.platformPath, 'Classes', 'ApplicationRouting.m'),
			srcStat = fs.statSync(srcFile),
			srcMtime = JSON.parse(JSON.stringify(srcStat.mtime)),
			contents = this._scrubiOSSourceFile(fs.readFileSync(srcFile).toString()),
			srcHash = this.hash(contents);

		this.logger.debug(__('Using default application routing'));

		if (!destExists || contents !== existingContent) {
			if (!this.forceRebuild) {
				this.logger.info(__('Forcing rebuild: %s has changed since last build', rel));
				this.forceRebuild = true;
			}
			this.logger.debug(__('Writing %s', dest.cyan));
			fs.writeFileSync(dest, contents);
		} else {
			this.logger.trace(__('No change, skipping %s', dest.cyan));
		}

		this.currentBuildManifest.files[rel] = {
			hash: srcHash,
			mtime: srcMtime,
			size: srcStat.size
		};

		return next();
	}

	this.logger.info(__('Encrypting JavaScript files'));

	if (!this.jsFilesChanged && destExists && prev && prev.size === destStat.size && prev.mtime === JSON.parse(JSON.stringify(destStat.mtime)) && prev.hash === this.hash(existingContent)) {
		this.logger.info(__('No JavaScript file changes, skipping titanium_prep'));
		this.currentBuildManifest.files[rel] = prev;
		return next();
	}

	const titaniumPrepHook = this.cli.createHook('build.ios.titaniumprep', this, function (exe, args, opts, done) {
		let tries = 0;
		let completed = false;

		this.jsFilesToEncrypt.forEach(file => this.logger.debug(__('Preparing %s', file.cyan)));

		async.whilst(
			function (cb) {
				if (!completed && tries > 3) {
					// we failed 3 times, so just give up
					this.logger.error(__('titanium_prep failed to complete successfully'));
					this.logger.error(__('Try cleaning this project and build again') + '\n');
					process.exit(1);
				}
				return cb(null, !completed);
			},
			function (cb) {
				this.logger.debug(__('Running %s', (exe + ' "' + args.slice(0, -1).join('" "') + '"').cyan));

				const child = spawn(exe, args, opts);
				let out = '',
					err = '';

				child.stdin.write(this.jsFilesToEncrypt.join('\n'));
				child.stdin.end();

				child.stdout.on('data', data => out += data.toString());
				child.stderr.on('data', data => err += data.toString());

				child.on('close', function (code) {
					if (code) {
						this.logger.error(__('titanium_prep failed to run (%s)', code));
						this.logger.error(__(err)  + '\n');
						process.exit(1);
					}

					if (out.indexOf('initWithObjectsAndKeys') !== -1) {
						// success!
						const contents = ejs.render(fs.readFileSync(path.join(this.templatesDir, 'ApplicationRouting.m')).toString(), { bytes: out });

						if (!destExists || contents !== existingContent) {
							if (!this.forceRebuild) {
								// since we just modified the ApplicationRouting.m, we need to force xcodebuild
								this.forceRebuild = true;
								this.logger.info(__('Forcing rebuild: %s changed since last build', dest.replace(this.buildDir + '/', '').cyan));
							}

							this.logger.debug(__('Writing application routing source file: %s', dest.cyan));
							fs.writeFileSync(dest, contents);

							const stat = fs.statSync(dest);
							this.currentBuildManifest.files['Classes/ApplicationRouting.m'] = {
								hash: this.hash(contents),
								mtime: stat.mtime,
								size: stat.size
							};
						} else {
							this.logger.trace(__('No change, skipping %s', dest.cyan));
						}

						this.unmarkBuildDirFile(dest);
						completed = true;
					} else {
						// failure, maybe it was a fluke, try again
						this.logger.warn(__('titanium_prep failed to complete successfully, trying again'));
						tries++;
					}

					cb();
				}.bind(this));
			}.bind(this),
			done
		);
	});

	titaniumPrepHook(
		path.join(this.platformPath, 'titanium_prep'),
		[ this.tiapp.id, this.buildAssetsDir, this.tiapp.guid ],
		{},
		next
	);
};

iOSBuilder.prototype.generateRequireIndex = async function generateRequireIndex() {
	this.logger.info(__('Writing index.json with listing of JS/JSON files'));
	const index = {}; // TODO: use a Map
	const binAssetsDir = this.xcodeAppDir.replace(/\\/g, '/');

	// Write _index_.json file with our JS/JSON file listing. This may also be encrypted
	const destFile = path.join(this.xcodeAppDir, '_index_.json');

	// Grab unencrypted JS/JSON files
	// FIXME: use async calls
	(function walk(dir) {
		fs.readdirSync(dir).forEach(filename => {
			const file = path.join(dir, filename);
			if (fs.existsSync(file)) {
				if (fs.statSync(file).isDirectory()) {
					walk(file);
				} else if (/\.(c?js|json)$/.test(filename)) { // TODO: Support mjs files!
					const modifiedFilename = file.replace(/\\/g, '/').replace(binAssetsDir + '/', 'Resources/');
					index[modifiedFilename] = 1; // 1 for exists on disk
				}
			}
		});
	}(this.xcodeAppDir));

	// Grab encrypted JS/JSON files
	this.jsFilesEncrypted.forEach(file => {
		index['Resources/' + file.replace(/\\/g, '/')] = 2; // 2 for encrypted
	});

	delete index['Resources/_app_props_.json'];
	index['Resources/_index_.json'] = 1;

	return fs.writeFile(destFile, JSON.stringify(index));
};

iOSBuilder.prototype.writeI18NFiles = function writeI18NFiles() {
	this.logger.info(__('Writing i18n files'));

	const data = ti.i18n.load(this.projectDir, this.logger),
		header = '/**\n'
			+ ' * Appcelerator Titanium\n'
			+ ' * this is a generated file - DO NOT EDIT\n'
			+ ' */\n\n';

	function add(obj, dest, map) {
		if (obj) {
			const rel = dest.replace(this.xcodeAppDir + '/', ''),
				contents = header + Object.keys(obj).map(function (name) {
					return '"' + (map && map[name] || name).replace(/\\"/g, '"').replace(/"/g, '\\"')
						+ '" = "' + ('' + obj[name]).replace(/%s/g, '%@').replace(/\\"/g, '"').replace(/"/g, '\\"') + '";';
				}).join('\n');

			this.currentBuildManifest.files[rel] = {
				hash: this.hash(contents),
				mtime: 0,
				size: contents.length
			};

			if (!fs.existsSync(dest) || contents !== fs.readFileSync(dest).toString()) {
				if (!this.forceRebuild && /device|dist-appstore|dist-macappstore|dist-adhoc/.test(this.target)) {
					this.logger.info(__('Forcing rebuild: %s changed since last build', rel));
					this.forceRebuild = true;
				}
				this.logger.debug(__('Writing %s', dest.cyan));
				fs.writeFileSync(dest, contents);
			} else {
				this.logger.trace(__('No change, skipping %s', dest.cyan));
			}

			this.unmarkBuildDirFile(dest);
		}
	}

	const keys = Object.keys(data);
	if (keys.length) {
		keys.forEach(function (lang) {
			const dir = path.join(this.xcodeAppDir, lang + '.lproj');
			fs.ensureDirSync(dir);

			add.call(this, data[lang].app, path.join(dir, 'InfoPlist.strings'), { appname: 'CFBundleDisplayName' });
			add.call(this, data[lang].strings, path.join(dir, 'Localizable.strings'));
		}, this);
	} else {
		this.logger.debug(__('No i18n files to process'));
	}
};

iOSBuilder.prototype.processTiSymbols = function processTiSymbols() {
	this.logger.info(__('Processing Titanium symbols'));

	const namespaces = {
			analytics: 1,
			api: 1,
			network: 1,
			platform: 1,
			ui: 1
		},
		symbols = {};

	// generate the default symbols
	Object.keys(namespaces).forEach(function (ns) {
		symbols[ns.toUpperCase()] = 1;
	});

	function addSymbol(symbol) {
		const parts = symbol.replace(/^(Ti|Titanium)./, '').split('.');
		if (parts.length) {
			namespaces[parts[0].toLowerCase()] = 1;
			while (parts.length) {
				symbols[parts.join('.').replace(/\.create/gi, '').replace(/\./g, '').replace(/-/g, '_').toUpperCase()] = 1;
				parts.pop();
			}
		}
	}

	// add the symbols we found
	Object.keys(this.tiSymbols).forEach(function (file) {
		this.tiSymbols[file].forEach(addSymbol);
	}, this);

	// for each module, if it has a metadata.json file, add its symbols
	this.modules.forEach(function (m) {
		const file = path.join(m.modulePath, 'metadata.json');
		if (fs.existsSync(file)) {
			try {
				const metadata = JSON.parse(fs.readFileSync(file));
				if (metadata && typeof metadata === 'object' && Array.isArray(metadata.exports)) {
					metadata.exports.forEach(addSymbol);
				}
			} catch (e) {
				// ignore
			}
		}
	});

	// for each Titanium namespace, copy any resources
	this.logger.debug(__('Processing Titanium namespace resources'));
	Object.keys(namespaces).forEach(function (ns) {
		const dir = path.join(this.platformPath, 'modules', ns, 'images');
		fs.existsSync(dir) && fs.readdirSync(dir).forEach(function (name) {
			const src = path.join(dir, name),
				srcStat = fs.statSync(src),
				srcMtime = JSON.parse(JSON.stringify(srcStat.mtime)),
				relPath = path.join('modules', ns, 'images', name),
				prev = this.previousBuildManifest.files && this.previousBuildManifest.files[relPath],
				dest = path.join(this.xcodeAppDir, relPath),
				destExists = fs.existsSync(dest);
			let contents = null,
				hash = null;
			const fileChanged = !destExists || !prev || prev.size !== srcStat.size || prev.mtime !== srcMtime || prev.hash !== (hash = this.hash(contents = fs.readFileSync(src)));

			if (!fileChanged || !this.copyFileSync(src, dest, { contents: contents || (contents = fs.readFileSync(src)) })) {
				this.logger.trace(__('No change, skipping %s', dest.cyan));
			}

			this.currentBuildManifest.files[relPath] = {
				hash:  contents === null && prev ? prev.hash  : hash || this.hash(contents || ''),
				mtime: contents === null && prev ? prev.mtime : srcMtime,
				size:  contents === null && prev ? prev.size  : srcStat.size
			};

			this.unmarkBuildDirFile(dest);
		}, this);
	}, this);

	const infoPlist = this.infoPlist;
	let hasRemoteNotification = false,
		hasFetch = false,
		contents;

	if (Array.isArray(infoPlist.UIBackgroundModes) && infoPlist.UIBackgroundModes.indexOf('remote-notification') !== -1) {
		hasRemoteNotification = true;
	}
	if (Array.isArray(infoPlist.UIBackgroundModes) && infoPlist.UIBackgroundModes.indexOf('fetch') !== -1) {
		hasFetch = true;
	}
	// if we're doing a simulator build or we're including all titanium modules, get it from source xcconfig file
	if (this.target === 'simulator' || this.target === 'macos' || this.includeAllTiModules) {
		const sourceConfigPath = path.join(this.platformPath, 'iphone', 'project.xcconfig');
		const conf = fs.readFileSync(sourceConfigPath).toString();
		contents = conf.match(/TI_SYMBOL_MACROS=([^\n]*)/)[1];

		if (this.useAutoLayout) {
			contents += ' TI_USE_AUTOLAYOUT';
		}
	} else {
		let isListView = false;
		contents = [
		].concat(Object.keys(symbols).sort().map(function (s) {
			if (s === 'UILISTVIEW') {
				isListView = true;
			}

			return 'USE_TI_' + s;
		}));

		if (isListView) {
			contents.push(
				'USE_TI_UILABEL',
				'USE_TI_UIBUTTON',
				'USE_TI_UIBUTTONBAR',
				'USE_TI_UIIMAGEVIEW',
				'USE_TI_UIMASKEDIMAGE',
				'USE_TI_UIPROGRESSBAR',
				'USE_TI_UIACTIVITYINDICATOR',
				'USE_TI_UIOPTIONBAR',
				'USE_TI_UISWITCH',
				'USE_TI_UISLIDER',
				'USE_TI_UITABBEDBAR',
				'USE_TI_UITEXTFIELD',
				'USE_TI_UITEXTAREA',
				'USE_TI_UISCROLLABLEVIEW',
				'USE_TI_UIIOSSTEPPER',
				'USE_TI_UIIOSBLURVIEW',
				'USE_TI_UIIOSLIVEPHOTOVIEW',
				'USE_TI_UIIOSTABBEDBAR',
				'USE_TI_UIPICKER',
			);
		}

		if (this.useAutoLayout) {
			contents.push('TI_USE_AUTOLAYOUT');
		}

		contents = contents.join(' ');
	}

	if (hasRemoteNotification) {
		contents += ' USE_TI_SILENTPUSH';
	}
	if (hasFetch) {
		contents += ' USE_TI_FETCH';
	}

	this.tiSymbolMacros = contents;
};

iOSBuilder.prototype.removeFiles = function removeFiles(next) {
	this.unmarkBuildDirFiles(path.join(this.buildDir, 'DerivedData'));
	this.unmarkBuildDirFiles(path.join(this.buildDir, 'build', 'Intermediates'));
	this.unmarkBuildDirFiles(path.join(this.buildDir, 'build', this.tiapp.name + '.build'));
	this.products.forEach(function (product) {
		product = product.replace(/^"/, '').replace(/"$/, '');
		this.unmarkBuildDirFiles(path.join(this.iosBuildDir, product));
		this.unmarkBuildDirFiles(path.join(this.iosBuildDir, product + '.dSYM'));
	}, this);
	this.unmarkBuildDirFiles(path.join(this.xcodeAppDir, '_CodeSignature'));
	this.unmarkBuildDirFiles(path.join(this.xcodeAppDir, 'AppIcon*'));
	this.unmarkBuildDirFiles(path.join(this.xcodeAppDir, 'LaunchImage-*'));

	// mark a few files that would be generated by xcodebuild
	this.unmarkBuildDirFile(path.join(this.xcodeAppDir, this.tiapp.name));
	this.unmarkBuildDirFile(path.join(this.xcodeAppDir, 'Info.plist'));
	this.unmarkBuildDirFile(path.join(this.xcodeAppDir, 'PkgInfo'));
	this.unmarkBuildDirFile(path.join(this.xcodeAppDir, 'embedded.mobileprovision'));

	this.unmarkBuildDirFiles(path.join(this.buildDir, 'export_options.plist'));
	this.unmarkBuildDirFiles(path.join(this.buildDir, this.tiapp.name + '.xcarchive'));

	const productsDir = path.join(this.buildDir, 'build', 'Products');
	try {
		const releaseDir = path.join(productsDir, 'Release-iphoneos');
		if (fs.lstatSync(path.join(releaseDir, this.tiapp.name + '.app')).isSymbolicLink()) {
			this.unmarkBuildDirFiles(releaseDir);
		}
	} catch (e) {
		// ignore
	}

	if (fs.existsSync(this.iosBuildDir)) {
		this.unmarkBuildDirFiles(this.iosBuildDir);
	}

	this.logger.info(__('Removing files'));

	const hook = this.cli.createHook('build.ios.removeFiles', this, function (done) {
		Object.keys(this.buildDirFiles).forEach(function (file) {
			try {
				this.logger.debug(__('Removing %s', file.cyan));
				fs.unlinkSync(file);
			} catch (ex) {
				// ignore
			}
		}, this);

		// TODO: This should not be required as xcframework builds for specified target
		// remove invalid architectures from TitaniumKit.xcframework for App Store distributions
		if (this.target === 'dist-appstore' || this.target === 'dist-adhoc') {
			this.logger.info(__('Removing invalid architectures from TitaniumKit.xcframework'));

			const titaniumKitPath = path.join(this.buildDir, 'Frameworks', 'TitaniumKit.xcframework', 'TitaniumKit');
			async.eachSeries([ 'x86_64', 'i386' ], function (architecture, next) {
				const args = [ '-remove', architecture, titaniumKitPath, '-o', titaniumKitPath ];
				this.logger.debug(__('Running: %s', (this.xcodeEnv.executables.lipo + ' ' + args.join(' ')).cyan));
				appc.subprocess.run(this.xcodeEnv.executables.lipo, args, function (_code, _out) {
					next();
				});
			}.bind(this), function () {
				done();
			});
		} else {
			done();
		}
	});

	hook(function () {
		this.logger.debug(__('Removing empty directories'));
		appc.subprocess.run('find', [ '.', '-type', 'd', '-empty', '-delete' ], { cwd: this.xcodeAppDir }, next);
	}.bind(this));
};

iOSBuilder.prototype.optimizeFiles = function optimizeFiles(next) {
	// if we're doing a simulator build, return now since we don't care about optimizing images
	if (this.target === 'simulator' || this.target === 'macos') {
		return next();
	}

	this.logger.info(__('Optimizing .plist and .png files'));

	const plistRegExp = /\.plist$/,
		pngRegExp = /\.png$/,
		plists = [],
		pngs = [],
		xcodeAppDir = this.xcodeAppDir + '/',
		previousBuildFiles = this.previousBuildManifest.files || {},
		currentBuildFiles = this.currentBuildManifest.files,
		logger = this.logger;

	function add(arr, name, file) {
		const rel = file.replace(xcodeAppDir, ''),
			prev = previousBuildFiles[rel],
			curr = currentBuildFiles[rel];

		if (!prev || prev.hash !== curr.hash) {
			arr.push(file);
		} else {
			logger.trace(__('No change, skipping %s', file.cyan));
		}
	}

	// find all plist and png files
	(function walk(dir, ignore) {
		fs.readdirSync(dir).forEach(function (name) {
			if (!ignore || !ignore.test(name)) {
				const file = path.join(dir, name);
				if (fs.existsSync(file)) {
					if (fs.statSync(file).isDirectory()) {
						walk(file, ignore);
					} else if (name === 'InfoPlist.strings' || name === 'Localizable.strings' || plistRegExp.test(name)) {
						add(plists, name, file);
					} else if (pngRegExp.test(name)) {
						add(pngs, name, file);
					}
				}
			}
		});
	}(this.xcodeAppDir, /^(PlugIns|Watch|.+\.framework)$/i));

	parallel(this, [
		function (next) {
			async.each(plists, function (file, cb) {
				this.logger.debug(__('Optimizing %s', file.cyan));
				appc.subprocess.run('plutil', [ '-convert', 'binary1', file ], cb);
			}.bind(this), next);
		},

		function (next) {
			if (!fs.existsSync(this.xcodeEnv.executables.pngcrush)) {
				this.logger.warn(__('Unable to find pngcrush in Xcode directory, skipping image optimization'));
				return next();
			}

			async.eachLimit(pngs, 5, function (file, cb) {
				const output = file + '.tmp';
				this.logger.debug(__('Optimizing %s', file.cyan));
				appc.subprocess.run(this.xcodeEnv.executables.pngcrush, [ '-q', '-iphone', '-f', 0, file, output ], function (code) {
					if (code) {
						this.logger.error(__('Failed to optimize %s (code %s)', file, code));
					} else if (fs.existsSync(output)) {
						fs.existsSync(file) && fs.unlinkSync(file);
						fs.renameSync(output, file);
					} else {
						this.logger.warn(__('Unable to optimize %s; invalid png?', file));
					}
					cb();
				}.bind(this));
			}.bind(this), next);
		}
	], next);
};

iOSBuilder.prototype.invokeXcodeBuild = function invokeXcodeBuild(next) {
	if (!this.forceRebuild) {
		this.logger.info(__('Skipping xcodebuild'));
		return next();
	}

	this.logger.info(__('Invoking xcodebuild'));

	const xcodebuildHook = this.cli.createHook('build.ios.xcodebuild', this, function (exe, args, opts, done) {
		const DEVELOPER_DIR = this.xcodeEnv.path
			+ ' ' + exe + ' '
			+ args.map(function (a) { return a.indexOf(' ') !== -1 ? '"' + a + '"' : a; })
				.join(' ');
		this.logger.debug(__('Invoking: %s', ('DEVELOPER_DIR=' + DEVELOPER_DIR).cyan));

		const p = spawn(exe, args, opts),
			out = [],
			err = [],
			clangCompileMFileRegExp = / -c ((?:.+)\.m) /,
			// here's a list of tasks that Xcode can perform... we use this so we can inject some whitespace and make the xcodebuild output pretty
			/* eslint-disable security/detect-non-literal-regexp */
			taskRegExp = new RegExp('^(' + [
				'CodeSign',
				'CompileAssetCatalog',
				'CompileC',
				'CompileStoryboard',
				'CopySwiftLibs',
				'CpHeader',
				'CreateUniversalBinary',
				'Ditto',
				'GenerateDSYMFile',
				'Ld',
				'Libtool',
				'LinkStoryboards',
				'PBXCp',
				'PhaseScriptExecution',
				'ProcessInfoPlistFile',
				'ProcessPCH',
				'ProcessPCH\\+\\+',
				'ProcessProductPackaging',
				'Strip',
				'Stripping',
				'Touch',
				'Validate',
				'ValidateEmbeddedBinary'
			].join('|') + ') ');
		/* eslint-enable security/detect-non-literal-regexp */
		let buffer = '',
			stopOutputting = false;

		function printLine(line) {
			if (line.length) {
				out.push(line);
				if (line.indexOf('Failed to minify') !== -1) {
					stopOutputting = true;
				}
				if (!stopOutputting) {
					if (taskRegExp.test(line)) {
						// add a blank line between tasks to make things easier to read
						this.logger.trace();
						this.logger.trace(line.cyan);
					} else if (line.indexOf('=== BUILD TARGET ') !== -1) {
						// build target
						this.logger.trace();
						this.logger.trace(line.magenta);
					} else if (/^\s+export /.test(line)) {
						// environment variable
						this.logger.trace(line.grey);
					} else if (line.indexOf('/usr/bin/clang') !== -1) {
						// highlight the source file being compiled
						this.logger.trace(line.replace(clangCompileMFileRegExp, ' -c ' + '$1'.green + ' '));
					} else if (line === '** BUILD SUCCEEDED **') {
						this.logger.trace();
						this.logger.trace(line.green);
					} else {
						this.logger.trace(line);
					}
				}
			}
		}

		p.stdout.on('data', function (data) {
			buffer += data.toString();
			const lines = buffer.split('\n');
			buffer = lines.pop();
			lines.forEach(printLine.bind(this));
		}.bind(this));

		p.stderr.on('data', function (data) {
			data.toString().split('\n').forEach(function (line) {
				if (line.length) {
					err.push(line);
				}
			}, this);
		}.bind(this));

		p.on('close', function (code) {
			if (buffer.length) {
				buffer.split('\n').forEach(printLine.bind(this));
			}

			if (code) {
				// first see if we errored due to a dependency issue
				if (err.join('\n').indexOf('Check dependencies') !== -1) {
					let len = out.length;
					for (let i = len - 1; i >= 0; i--) {
						if (out[i].indexOf('Check dependencies') !== -1) {
							if (out[out.length - 1].indexOf('Command /bin/sh failed with exit code') !== -1) {
								len--;
							}
							for (let j = i + 1; j < len; j++) {
								this.logger.error(__('Error details: %s', out[j]));
							}
							this.logger.log();
							process.exit(1);
						}
					}
				}

				// next see if it was a minification issue
				let len = out.length;
				for (let i = len - 1, k = 0; i >= 0 && k < 10; i--, k++) {
					if (out[i].indexOf('Failed to minify') !== -1) {
						if (out[out.length - 1].indexOf('Command /bin/sh failed with exit code') !== -1) {
							len--;
						}
						while (i < len) {
							this.logger.log(out[i++]);
						}
						this.logger.log();
						process.exit(1);
					}
				}

				// just print the entire error buffer
				err.forEach(function (line) {
					this.logger.error(line);
				}, this);
				this.logger.log();
				process.exit(1);
			}

			// end of the line
			done(code);
		}.bind(this));
	});

	const args = [
		this.target === 'dist-appstore' || this.target === 'dist-adhoc' || this.target === 'dist-macappstore' ? 'archive' : 'build',
		'-target', this.tiapp.name,
		'-configuration', this.xcodeTarget,
		'-scheme', this.sanitizedAppName(),
		'-derivedDataPath', path.join(this.buildDir, 'DerivedData'),
		'-UseNewBuildSystem=' + (this.useNewBuildSystem ? 'YES' : 'NO'),
		'OBJROOT=' + path.join(this.buildDir, 'build', 'Intermediates'),
		'SHARED_PRECOMPS_DIR=' + path.join(this.buildDir, 'build', 'Intermediates', 'PrecompiledHeaders'),
		'SYMROOT=' + path.join(this.buildDir, 'build', 'Products'),
	];

	if (this.simHandle && this.target !== 'macos' && this.target !== 'dist-macappstore') {
		args.push('-destination', 'generic/platform=iOS Simulator');

		// only build active architecture, which is 64-bit, if simulator is not 32-bit (iPhone 5s or newer, iPhone 5 and older are not 64-bit)
		if (this.simOnlyActiveArch) {
			args.push('ONLY_ACTIVE_ARCH=1');
		}
		// Exclude arm64 architecture from simulator build in XCode 12+ - TIMOB-28042
		if (this.legacyModules.size > 0 && parseFloat(this.xcodeEnv.version) >= 12.0) {
			if (process.arch === 'arm64') {
				return next(new Error('The app is using native modules that do not support arm64 simulators and you are on an arm64 device.'));
			}
			this.logger.warn(`The app is using native modules (${Array.from(this.legacyModules)}) that do not support arm64 simulators, we will exclude arm64. This may fail if you're on an arm64 Apple Silicon device.`);
			args.push('EXCLUDED_ARCHS=arm64');
		}
	}

	xcodebuildHook(
		this.xcodeEnv.executables.xcodebuild,
		args,
		{
			cwd: this.buildDir,
			env: {
				DEVELOPER_DIR: this.xcodeEnv.path,
				TMPDIR: process.env.TMPDIR,
				HOME: process.env.HOME,
				PATH: process.env.PATH,
				TITANIUM_CLI_XCODEBUILD: 'Enjoy hacking? https://www.axway.com/en/career'
			}
		},
		next
	);
};

iOSBuilder.prototype.writeBuildManifest = function writeBuildManifest(next) {
	this.cli.createHook('build.ios.writeBuildManifest', this, function (manifest, cb) {
		fs.ensureDirSync(this.buildDir);
		fs.existsSync(this.buildManifestFile) && fs.unlinkSync(this.buildManifestFile);
		fs.writeFile(this.buildManifestFile, JSON.stringify(this.buildManifest = manifest, null, '\t'), cb);
	})(this.currentBuildManifest, next);
};

/**
 * Returns the sanitized app name to replace invalid characters.
 *
 * @returns {String}
 */
iOSBuilder.prototype.sanitizedAppName = function sanitizedAppName() {
	return this.tiapp.name.replace(/[-\W]/g, '_');
};

function sha1(value) {
	return crypto.createHash('sha1').update(value).digest('hex');
}

// create the builder instance and expose the public api
(function (iosBuilder) {
	exports.config   = iosBuilder.config.bind(iosBuilder);
	exports.validate = iosBuilder.validate.bind(iosBuilder);
	exports.run      = iosBuilder.run.bind(iosBuilder);
}(new iOSBuilder(module)));
