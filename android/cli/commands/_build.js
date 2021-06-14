/**
 * Android build command.
 *
 * @module cli/_build
 *
 * @copyright
 * Copyright (c) 2009-2021 by Axway, Inc. All Rights Reserved.
 *
 * @license
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

'use strict';

const ADB = require('node-titanium-sdk/lib/adb'),
	android = require('node-titanium-sdk/lib/android'),
	androidDetect = require('../lib/detect').detect,
	AndroidManifest = require('../lib/android-manifest'),
	appc = require('node-appc'),
	async = require('async'),
	Builder = require('node-titanium-sdk/lib/builder'),
	GradleWrapper = require('../lib/gradle-wrapper'),
	ProcessJsTask = require('../../../cli/lib/tasks/process-js-task'),
	ProcessDrawablesTask = require('../lib/process-drawables-task'),
	ProcessSplashesTask = require('../lib/process-splashes-task'),
	Color = require('../../../common/lib/color'),
	ProcessCSSTask = require('../../../cli/lib/tasks/process-css-task'),
	CopyResourcesTask = require('../../../cli/lib/tasks/copy-resources-task'),
	DOMParser = require('xmldom').DOMParser,
	ejs = require('ejs'),
	EmulatorManager = require('node-titanium-sdk/lib/emulator'),
	fields = require('fields'),
	fs = require('fs-extra'),
	i18n = require('node-titanium-sdk/lib/i18n'),
	path = require('path'),
	temp = require('temp'),
	ti = require('node-titanium-sdk'),
	tiappxml = require('node-titanium-sdk/lib/tiappxml'),
	util = require('util'),

	afs = appc.fs,
	i18nLib = appc.i18n(__dirname),
	__ = i18nLib.__,
	__n = i18nLib.__n,
	version = appc.version,
	V8_STRING_VERSION_REGEXP = /(\d+)\.(\d+)\.\d+\.\d+/;

const platformsRegExp = new RegExp('^(' + ti.allPlatformNames.join('|') + ')$'); // eslint-disable-line security/detect-non-literal-regexp

function AndroidBuilder() {
	Builder.apply(this, arguments);

	this.devices = null; // set by findTargetDevices() during 'config' phase
	this.devicesToAutoSelectFrom = [];

	this.keystoreAliases = [];

	this.tiSymbols = {};

	this.validABIs = this.packageJson.architectures;
	this.compileSdkVersion = this.packageJson.compileSDKVersion; // this should always be >= maxSupportedApiLevel
	this.minSupportedApiLevel = parseInt(this.packageJson.minSDKVersion);
	this.minTargetApiLevel = parseInt(version.parseMin(this.packageJson.vendorDependencies['android sdk']));
	this.maxSupportedApiLevel = parseInt(version.parseMax(this.packageJson.vendorDependencies['android sdk']));

	this.deployTypes = {
		emulator: 'development',
		device: 'test',
		'dist-playstore': 'production'
	};

	this.targets = [ 'emulator', 'device', 'dist-playstore' ];
}

util.inherits(AndroidBuilder, Builder);

AndroidBuilder.prototype.config = function config(logger, config, cli) {
	Builder.prototype.config.apply(this, arguments);

	const _t = this;

	function assertIssue(logger, issues, name) {
		for (let i = 0; i < issues.length; i++) {
			if ((typeof name === 'string' && issues[i].id === name) || (typeof name === 'object' && name.test(issues[i].id))) {
				issues[i].message.split('\n').forEach(function (line) {
					logger[issues[i].type === 'error' ? 'error' : 'warn'](line.replace(/(__(.+?)__)/g, '$2'.bold));
				});
				logger.log();
				if (issues[i].type === 'error') {
					process.exit(1);
				}
			}
		}
	}

	// we hook into the pre-validate event so that we can stop the build before
	// prompting if we know the build is going to fail.
	//
	// this is also where we can detect android and jdk environments before
	// prompting occurs. because detection is expensive we also do it here instead
	// of during config() because there's no sense detecting if config() is being
	// called because of the help command.
	cli.on('cli:pre-validate', function (obj, callback) {
		if (cli.argv.platform && cli.argv.platform !== 'android') {
			return callback();
		}

		_t.buildOnly = cli.argv['build-only'];

		async.series([
			function (next) {
				// detect android environment
				androidDetect(config, { packageJson: _t.packageJson }, function (androidInfo) {
					_t.androidInfo = androidInfo;
					assertIssue(logger, androidInfo.issues, 'ANDROID_JDK_NOT_FOUND');
					assertIssue(logger, androidInfo.issues, 'ANDROID_JDK_PATH_CONTAINS_AMPERSANDS');

					// if --android-sdk was not specified, then we simply try to set a default android sdk
					if (!cli.argv['android-sdk']) {
						let androidSdkPath = config.android && config.android.sdkPath;
						if (!androidSdkPath && androidInfo.sdk) {
							androidSdkPath = androidInfo.sdk.path;
						}
						androidSdkPath && (cli.argv['android-sdk'] = afs.resolvePath(androidSdkPath));
					}

					next();
				});
			},

			function (next) {
				// detect java development kit
				appc.jdk.detect(config, null, function (jdkInfo) {
					assertIssue(logger, jdkInfo.issues, 'JDK_NOT_INSTALLED');
					assertIssue(logger, jdkInfo.issues, 'JDK_MISSING_PROGRAMS');
					assertIssue(logger, jdkInfo.issues, 'JDK_INVALID_JAVA_HOME');

					if (!jdkInfo.version) {
						logger.error(__('Unable to locate the Java Development Kit') + '\n');
						logger.log(__('You can specify the location by setting the %s environment variable.', 'JAVA_HOME'.cyan) + '\n');
						process.exit(1);
					}

					if (!version.satisfies(jdkInfo.version, _t.packageJson.vendorDependencies.java)) {
						logger.error(__('JDK version %s detected, but only version %s is supported', jdkInfo.version, _t.packageJson.vendorDependencies.java) + '\n');
						process.exit(1);
					}

					_t.jdkInfo = jdkInfo;
					next();
				});
			}
		], callback);
	});

	const targetDeviceCache = {},
		findTargetDevices = function findTargetDevices(target, callback) {
			if (targetDeviceCache[target]) {
				return callback(null, targetDeviceCache[target]);
			}

			if (target === 'device') {
				new ADB(config).devices(function (err, devices) {
					if (err) {
						callback(err);
					} else {
						this.devices = devices.filter(function (d) {
							return !d.emulator && d.state === 'device';
						});
						if (this.devices.length > 1) {
							// we have more than 1 device, so we should show 'all'
							this.devices.push({
								id: 'all',
								model: 'All Devices'
							});
						}
						callback(null, targetDeviceCache[target] = this.devices.map(function (d) {
							return {
								name: d.model || d.manufacturer,
								id: d.id,
								version: d.release,
								abi: Array.isArray(d.abi) ? d.abi.join(',') : d.abi,
								type: 'device'
							};
						}));
					}
				}.bind(this));
			} else if (target === 'emulator') {
				new EmulatorManager(config).detect(function (err, emus) {
					if (err) {
						callback(err);
					} else {
						this.devices = emus;
						callback(null, targetDeviceCache[target] = emus.map(function (emu) {
							// normalize the emulator info
							if (emu.type === 'avd') {
								return {
									name: emu.name,
									id: emu.id,
									api: emu['api-level'],
									version: emu['sdk-version'],
									abi: emu.abi,
									type: emu.type,
									googleApis: emu.googleApis,
									sdcard: emu.sdcard
								};
							} else if (emu.type === 'genymotion') {
								return {
									name: emu.name,
									id: emu.name,
									api: emu['api-level'],
									version: emu['sdk-version'],
									abi: emu.abi,
									type: emu.type,
									googleApis: emu.googleApis,
									sdcard: true
								};
							}
							return emu; // not good
						}));
					}
				}.bind(this));
			} else {
				callback();
			}
		}.bind(this);

	return function (finished) {
		cli.createHook('build.android.config', this, function (callback) {
			const conf = {
				flags: {
					launch: {
						desc: __('disable launching the app after installing'),
						default: true,
						hideDefault: true,
						negate: true
					}
				},
				options: {
					alias: {
						abbr: 'L',
						desc: __('the alias for the keystore'),
						hint: 'alias',
						order: 155,
						prompt: function (callback) {
							callback(fields.select({
								title: __('What is the name of the keystore\'s certificate alias?'),
								promptLabel: __('Select a certificate alias by number or name'),
								margin: '',
								optionLabel: 'name',
								optionValue: 'name',
								numbered: true,
								relistOnError: true,
								complete: true,
								suggest: false,
								options: _t.keystoreAliases,
								validate: conf.options.alias.validate
							}));
						},
						validate: function (value, callback) {
							// if there's a value, then they entered something, otherwise let the cli prompt
							if (value) {
								const selectedAlias = value.toLowerCase(),
									alias = _t.keystoreAlias = _t.keystoreAliases.filter(function (a) { return a.name && a.name.toLowerCase() === selectedAlias; }).shift();
								if (!alias) {
									return callback(new Error(__('Invalid "--alias" value "%s"', value)));
								}
								if (alias.sigalg && alias.sigalg.toLowerCase() === 'sha256withrsa') {
									logger.warn(__('The selected alias %s uses the %s signature algorithm which will likely have issues with Android 4.3 and older.', ('"' + value + '"').cyan, ('"' + alias.sigalg + '"').cyan));
									logger.warn(__('Certificates that use the %s or %s signature algorithm will provide better compatibility.', '"SHA1withRSA"'.cyan, '"MD5withRSA"'.cyan));
								}
							}
							callback(null, value);
						}
					},
					'android-sdk': {
						abbr: 'A',
						default: config.android && config.android.sdkPath && afs.resolvePath(config.android.sdkPath),
						desc: __('the path to the Android SDK'),
						hint: __('path'),
						order: 100,
						prompt: function (callback) {
							let androidSdkPath = config.android && config.android.sdkPath;
							if (!androidSdkPath && _t.androidInfo.sdk) {
								androidSdkPath = _t.androidInfo.sdk.path;
							}
							if (androidSdkPath) {
								androidSdkPath = afs.resolvePath(androidSdkPath);
								if (process.platform === 'win32' || androidSdkPath.indexOf('&') !== -1) {
									androidSdkPath = undefined;
								}
							}

							callback(fields.file({
								promptLabel: __('Where is the Android SDK?'),
								default: androidSdkPath,
								complete: true,
								showHidden: true,
								ignoreDirs: _t.ignoreDirs,
								ignoreFiles: _t.ignoreFiles,
								validate: _t.conf.options['android-sdk'].validate.bind(_t)
							}));
						},
						required: true,
						validate: function (value, callback) {
							if (!value) {
								callback(new Error(__('Invalid Android SDK path')));
							} else if (process.platform === 'win32' && value.indexOf('&') !== -1) {
								callback(new Error(__('The Android SDK path cannot contain ampersands (&) on Windows')));
							} else if (_t.androidInfo.sdk && _t.androidInfo.sdk.path === afs.resolvePath(value)) {
								callback(null, value);
							} else {
								// attempt to find android sdk
								android.findSDK(value, config, appc.pkginfo.package(module), function () {

									// NOTE: ignore errors when finding sdk, let gradle validate the sdk

									function next() {
										// set the android sdk in the config just in case a plugin or something needs it
										config.set('android.sdkPath', value);

										// path looks good, do a full scan again
										androidDetect(config, { packageJson: _t.packageJson, bypassCache: true }, function (androidInfo) {

											// assume sdk is valid, let gradle validate the sdk
											if (!androidInfo.sdk) {
												androidInfo.sdk = { path: value };
											}

											_t.androidInfo = androidInfo;
											callback(null, value);
										});
									}

									// new android sdk path looks good
									// if we found an android sdk in the pre-validate hook, then we need to kill the other sdk's adb server
									if (_t.androidInfo.sdk) {
										new ADB(config).stopServer(next);
									} else {
										next();
									}
								});
							}
						}
					},
					'avd-abi': {
						abbr: 'B',
						desc: __('the abi for the Android emulator; deprecated, use --device-id'),
						hint: __('abi')
					},
					'avd-id': {
						abbr: 'I',
						desc: __('the id for the Android emulator; deprecated, use --device-id'),
						hint: __('id')
					},
					'avd-skin': {
						abbr: 'S',
						desc: __('the skin for the Android emulator; deprecated, use --device-id'),
						hint: __('skin')
					},
					'build-type': {
						hidden: true
					},
					'debug-host': {
						hidden: true
					},
					'deploy-type': {
						abbr: 'D',
						desc: __('the type of deployment; only used when target is %s or %s', 'emulator'.cyan, 'device'.cyan),
						hint: __('type'),
						order: 110,
						values: [ 'test', 'development' ]
					},
					'device-id': {
						abbr: 'C',
						desc: __('the id of the Android emulator or the device id to install the application to'),
						hint: __('name'),
						order: 130,
						prompt: function (callback) {
							findTargetDevices(cli.argv.target, function (err, results) {
								var opts = {},
									title,
									promptLabel;

								// we need to sort all results into groups for the select field
								if (cli.argv.target === 'device' && results.length) {
									opts[__('Devices')] = results;
									title = __('Which device do you want to install your app on?');
									promptLabel = __('Select a device by number or name');
								} else if (cli.argv.target === 'emulator') {
									// for emulators, we sort by type
									let emus = results.filter(function (e) {
										return e.type === 'avd';
									});

									if (emus.length) {
										opts[__('Android Emulators')] = emus;
									}

									emus = results.filter(function (e) {
										return e.type === 'genymotion';
									});
									if (emus.length) {
										opts[__('Genymotion Emulators')] = emus;

										logger.log(__('NOTE: Genymotion emulator must be running to detect Google API support').magenta + '\n');
									}

									title = __('Which emulator do you want to launch your app in?');
									promptLabel = __('Select an emulator by number or name');
								}

								// if there are no devices/emulators, error
								if (!Object.keys(opts).length) {
									if (cli.argv.target === 'device') {
										logger.warn(__('Unable to find any devices, possibily due to missing dependencies.') + '\n');
										logger.log(__('Continuing with build... (will attempt to install missing dependencies)') + '\n');
									} else {
										logger.warn(__('Unable to find any emulators, possibily due to missing dependencies.') + '\n');
										logger.log(__('Continuing with build... (will attempt to install missing dependencies)') + '\n');
									}
									_t.buildOnly = true;
									return callback();
								}

								callback(fields.select({
									title: title,
									promptLabel: promptLabel,
									formatters: {
										option: function (opt, idx, num) {
											return '  ' + num + opt.name.cyan + (opt.version ? ' (' + opt.version + ')' : '') + (opt.googleApis
												? (' (' + __('Google APIs supported') + ')').grey
												: opt.googleApis === null
													? (' (' + __('Google APIs support unknown') + ')').grey
													: '');
										}
									},
									autoSelectOne: true,
									margin: '',
									optionLabel: 'name',
									optionValue: 'id',
									numbered: true,
									relistOnError: true,
									complete: true,
									suggest: true,
									options: opts
								}));
							});
						},
						required: true,
						validate: function (device, callback) {
							const dev = device.toLowerCase();
							findTargetDevices(cli.argv.target, function (err, devices) {
								if (cli.argv.target === 'device' && dev === 'all') {
									// we let 'all' slide by
									return callback(null, dev);
								}
								for (let i = 0; i < devices.length; i++) {
									if (devices[i].id.toLowerCase() === dev) {
										return callback(null, devices[i].id);
									}
								}
								callback(new Error(cli.argv.target ? __('Invalid Android device "%s"', device) : __('Invalid Android emulator "%s"', device)));
							});
						},
						verifyIfRequired: function (callback) {
							if (_t.buildOnly) {
								// not required if we're build only
								return callback();
							}

							findTargetDevices(cli.argv.target, function (err, results) {
								if (cli.argv.target === 'emulator' && cli.argv['device-id'] === undefined && cli.argv['avd-id']) {
									// if --device-id was not specified, but --avd-id was, then we need to
									// try to resolve a device based on the legacy --avd-* options
									let avds = results.filter(function (a) {
											return a.type === 'avd';
										}).map(function (a) {
											return a.name;
										}),
										name = 'titanium_' + cli.argv['avd-id'] + '_';

									if (avds.length) {
										// try finding the first avd that starts with the avd id
										avds = avds.filter(function (avd) {
											return avd.indexOf(name) === 0;
										});
										if (avds.length === 1) {
											cli.argv['device-id'] = avds[0];
											return callback();
										} else if (avds.length > 1) {
											// next try using the avd skin
											if (!cli.argv['avd-skin']) {
												// we have more than one match
												logger.error(__n('Found %s avd with id "%%s"', 'Found %s avds with id "%%s"', avds.length, cli.argv['avd-id']));
												logger.error(__('Specify --avd-skin and --avd-abi to select a specific emulator') + '\n');
											} else {
												name += cli.argv['avd-skin'];
												// try exact match
												let tmp = avds.filter(function (avd) {
													return avd === name;
												});
												if (tmp.length) {
													avds = tmp;
												} else {
													// try partial match
													avds = avds.filter(function (avd) {
														return avd.indexOf(name + '_') === 0;
													});
												}
												if (avds.length === 0) {
													logger.error(__('No emulators found with id "%s" and skin "%s"', cli.argv['avd-id'], cli.argv['avd-skin']) + '\n');
												} else if (avds.length === 1) {
													cli.argv['device-id'] = avds[0];
													return callback();
												} else if (!cli.argv['avd-abi']) {
													// we have more than one matching avd, but no abi to filter by so we have to error
													logger.error(__n('Found %s avd with id "%%s" and skin "%%s"', 'Found %s avds with id "%%s" and skin "%%s"', avds.length, cli.argv['avd-id'], cli.argv['avd-skin']));
													logger.error(__('Specify --avd-abi to select a specific emulator') + '\n');
												} else {
													name += '_' + cli.argv['avd-abi'];
													// try exact match
													tmp = avds.filter(function (avd) {
														return avd === name;
													});
													/* eslint-disable max-depth */
													if (tmp.length) {
														avds = tmp;
													} else {
														avds = avds.filter(function (avd) {
															return avd.indexOf(name + '_') === 0;
														});
													}
													if (avds.length === 0) {
														logger.error(__('No emulators found with id "%s", skin "%s", and abi "%s"', cli.argv['avd-id'], cli.argv['avd-skin'], cli.argv['avd-abi']) + '\n');
													} else {
														// there is one or more avds, but we'll just return the first one
														cli.argv['device-id'] = avds[0];
														return callback();
													}
													/* eslint-enable max-depth */
												}
											}
										}

										logger.warn(__('%s options have been %s, please use %s', '--avd-*'.cyan, 'deprecated'.red, '--device-id'.cyan) + '\n');

										// print list of available avds
										if (results.length && !cli.argv.prompt) {
											logger.log(__('Available Emulators:'));
											results.forEach(function (emu) {
												logger.log('   ' + emu.name.cyan + ' (' + emu.version + ')');
											});
											logger.log();
										}
									}

								} else if (cli.argv['device-id'] === undefined && results && results.length && config.get('android.autoSelectDevice', true)) {
									// we set the device-id to an array of devices so that later in validate()
									// after the tiapp.xml has been parsed, we can auto select the best device
									_t.devicesToAutoSelectFrom = results.sort(function (a, b) {
										var eq = a.api && b.api && appc.version.eq(a.api, b.api),
											gt = a.api && b.api && appc.version.gt(a.api, b.api);

										if (eq) {
											if (a.type === b.type) {
												if (a.googleApis === b.googleApis) {
													return 0;
												} else if (b.googleApis) {
													return 1;
												} else if (a.googleApis === false && b.googleApis === null) {
													return 1;
												}
												return -1;
											}
											return a.type === 'avd' ? -1 : 1;
										}

										return gt ? 1 : -1;
									});
									return callback();
								}

								// Failed to find devices, fallback to buildOnly.
								logger.warn('Unable to find any emulators or devices, possibily due to missing dependencies.');
								logger.warn('Continuing with build... (will attempt to install missing dependencies)');
								_t.buildOnly = true;
								return callback();
							});
						}
					},
					'key-password': {
						desc: __('the password for the keystore private key (defaults to the store-password)'),
						hint: 'keypass',
						order: 160,
						prompt: function (callback) {
							callback(fields.text({
								promptLabel: __('What is the keystore\'s __key password__?') + ' ' + __('(leave blank to use the store password)').grey,
								password: true,
								validate: _t.conf.options['key-password'].validate.bind(_t)
							}));
						},
						secret: true,
						validate: function (keyPassword, callback) {
							// sanity check the keystore and store password
							_t.conf.options['store-password'].validate(cli.argv['store-password'], function (err, storePassword) {
								if (err) {
									// we have a bad --keystore or --store-password arg
									cli.argv.keystore = cli.argv['store-password'] = undefined;
									return callback(err);
								}

								const keystoreFile = cli.argv.keystore,
									alias = cli.argv.alias,
									tmpKeystoreFile = temp.path({ suffix: '.jks' });

								if (keystoreFile && storePassword && alias && _t.jdkInfo && _t.jdkInfo.executables.keytool) {
									// the only way to test the key password is to export the cert
									appc.subprocess.run(_t.jdkInfo.executables.keytool, [
										'-J-Duser.language=en',
										'-importkeystore',
										'-v',
										'-srckeystore', keystoreFile,
										'-destkeystore', tmpKeystoreFile,
										'-srcstorepass', storePassword,
										'-deststorepass', storePassword,
										'-srcalias', alias,
										'-destalias', alias,
										'-srckeypass', keyPassword || storePassword,
										'-noprompt'
									], function (code, out) {
										if (code) {
											if (out.indexOf('java.security.UnrecoverableKeyException') !== -1) {
												return callback(new Error(__('Bad key password')));
											}
											return callback(new Error(out.trim()));
										}

										// remove the temp keystore
										fs.existsSync(tmpKeystoreFile) && fs.unlinkSync(tmpKeystoreFile);

										callback(null, keyPassword);
									});
								} else {
									callback(null, keyPassword);
								}
							});
						}
					},
					keystore: {
						abbr: 'K',
						callback: function () {
							_t.conf.options['alias'].required = true;
							_t.conf.options['store-password'].required = true;
						},
						desc: __('the location of the keystore file'),
						hint: 'path',
						order: 140,
						prompt: function (callback) {
							_t.conf.options['key-password'].required = true;
							callback(fields.file({
								promptLabel: __('Where is the __keystore file__ used to sign the app?'),
								complete: true,
								showHidden: true,
								ignoreDirs: _t.ignoreDirs,
								ignoreFiles: _t.ignoreFiles,
								validate: _t.conf.options.keystore.validate.bind(_t)
							}));
						},
						validate: function (keystoreFile, callback) {
							if (!keystoreFile) {
								callback(new Error(__('Please specify the path to your keystore file')));
							} else {
								keystoreFile = afs.resolvePath(keystoreFile);
								if (!fs.existsSync(keystoreFile) || !fs.statSync(keystoreFile).isFile()) {
									callback(new Error(__('Invalid keystore file')));
								} else {
									callback(null, keystoreFile);
								}
							}
						}
					},
					'output-dir': {
						abbr: 'O',
						desc: __('the output directory when using %s', 'dist-playstore'.cyan),
						hint: 'dir',
						order: 180,
						prompt: function (callback) {
							callback(fields.file({
								promptLabel: __('Where would you like the output APK file saved?'),
								default: cli.argv['project-dir'] && afs.resolvePath(cli.argv['project-dir'], 'dist'),
								complete: true,
								showHidden: true,
								ignoreDirs: _t.ignoreDirs,
								ignoreFiles: /.*/,
								validate: _t.conf.options['output-dir'].validate.bind(_t)
							}));
						},
						validate: function (outputDir, callback) {
							callback(outputDir || !_t.conf.options['output-dir'].required ? null : new Error(__('Invalid output directory')), outputDir);
						}
					},
					'profiler-host': {
						hidden: true
					},
					'store-password': {
						abbr: 'P',
						desc: __('the password for the keystore'),
						hint: 'password',
						order: 150,
						prompt: function (callback) {
							callback(fields.text({
								next: function (err) {
									return err && err.next || null;
								},
								promptLabel: __('What is the keystore\'s __password__?'),
								password: true,
								// if the password fails due to bad keystore file,
								// we need to prompt for the keystore file again
								repromptOnError: false,
								validate: _t.conf.options['store-password'].validate.bind(_t)
							}));
						},
						secret: true,
						validate: function (storePassword, callback) {
							if (!storePassword) {
								return callback(new Error(__('Please specify a keystore password')));
							}

							// sanity check the keystore
							_t.conf.options.keystore.validate(cli.argv.keystore, function (err, keystoreFile) {
								if (err) {
									// we have a bad --keystore arg
									cli.argv.keystore = undefined;
									return callback(err);
								}

								if (keystoreFile && _t.jdkInfo && _t.jdkInfo.executables.keytool) {
									appc.subprocess.run(_t.jdkInfo.executables.keytool, [
										'-J-Duser.language=en',
										'-list',
										'-v',
										'-keystore', keystoreFile,
										'-storepass', storePassword
									], function (code, out) {
										if (code) {
											let msg = out.split('\n').shift().split('java.io.IOException:');
											if (msg.length > 1) {
												msg = msg[1].trim();
												if (/invalid keystore format/i.test(msg)) {
													msg = __('Invalid keystore file');
													cli.argv.keystore = undefined;
													_t.conf.options.keystore.required = true;
												}
											} else {
												msg = out.trim();
											}

											return callback(new Error(msg));
										}

										// empty the alias array. it is important that we don't destory the original
										// instance since it was passed by reference to the alias select list
										while (_t.keystoreAliases.length) {
											_t.keystoreAliases.pop();
										}

										// Parse the keystore's alias name and signature algorithm.
										// Note: Algorithm can return "MD5withRSA (weak)" on JDK 8 and higher.
										//       Only extract 1st token since we need a valid algorithm name.
										const aliasRegExp = /Alias name: (.+)/,
											sigalgRegExp = /Signature algorithm name: (.[^\s]+)/;
										out.split('\n\n').forEach(function (chunk) {
											chunk = chunk.trim();
											const m = chunk.match(aliasRegExp);
											if (m) {
												const sigalg = chunk.match(sigalgRegExp);
												_t.keystoreAliases.push({
													name: m[1],
													sigalg: sigalg && sigalg[1] && sigalg[1].trim()
												});
											}
										});

										if (_t.keystoreAliases.length === 0) {
											cli.argv.keystore = undefined;
											return callback(new Error(__('Keystore does not contain any certificates')));
										} else if (!cli.argv.alias && _t.keystoreAliases.length === 1) {
											cli.argv.alias = _t.keystoreAliases[0].name;
										}

										// check if this keystore requires a key password
										const keystoreFile = cli.argv.keystore,
											alias = cli.argv.alias,
											tmpKeystoreFile = temp.path({ suffix: '.jks' });

										if (keystoreFile && storePassword && alias && _t.jdkInfo && _t.jdkInfo.executables.keytool) {
											// the only way to test the key password is to export the cert
											appc.subprocess.run(_t.jdkInfo.executables.keytool, [
												'-J-Duser.language=en',
												'-importkeystore',
												'-v',
												'-srckeystore', keystoreFile,
												'-destkeystore', tmpKeystoreFile,
												'-srcstorepass', storePassword,
												'-deststorepass', storePassword,
												'-srcalias', alias,
												'-destalias', alias,
												'-srckeypass', storePassword,
												'-noprompt'
											], function (code, out) {
												if (code) {
													if (out.indexOf('Alias <' + alias + '> does not exist') !== -1) {
														// bad alias, we'll let --alias find it again
														_t.conf.options['alias'].required = true;
													}

													// since we have an error, force the key password to be required
													_t.conf.options['key-password'].required = true;
												} else {
													// remove the temp keystore
													fs.existsSync(tmpKeystoreFile) && fs.unlinkSync(tmpKeystoreFile);
												}
												callback(null, storePassword);
											});
										} else {
											callback(null, storePassword);
										}
									});
								} else {
									callback(null, storePassword);
								}
							});
						}
					},
					target: {
						abbr: 'T',
						callback: function (value) {
							// as soon as we know the target, toggle required options for validation
							if (value === 'dist-playstore') {
								_t.conf.options['alias'].required = true;
								_t.conf.options['deploy-type'].values = [ 'production' ];
								_t.conf.options['device-id'].required = false;
								_t.conf.options['keystore'].required = true;
								_t.conf.options['output-dir'].required = true;
								_t.conf.options['store-password'].required = true;
							}
						},
						default: 'emulator',
						desc: __('the target to build for'),
						order: 120,
						required: true,
						values: _t.targets
					},
					sigalg: {
						desc: __('the type of a digital signature algorithm. only used when overriding keystore signing algorithm'),
						hint: __('signing'),
						order: 170,
						values: [ 'MD5withRSA', 'SHA1withRSA', 'SHA256withRSA' ]
					}
				}
			};

			callback(null, _t.conf = conf);
		})(function (err, result) {
			finished(result);
		});
	}.bind(this);
};

AndroidBuilder.prototype.validate = function validate(logger, config, cli) {
	Builder.prototype.validate.apply(this, arguments);

	this.target = cli.argv.target;
	this.deployType = !/^dist-/.test(this.target) && cli.argv['deploy-type'] ? cli.argv['deploy-type'] : this.deployTypes[this.target];
	this.buildType = cli.argv['build-type'] || '';

	// ti.deploytype is deprecated and so we force the real deploy type
	if (cli.tiapp.properties['ti.deploytype']) {
		logger.warn(__('The %s tiapp.xml property has been deprecated, please use the %s option', 'ti.deploytype'.cyan, '--deploy-type'.cyan));
	}
	cli.tiapp.properties['ti.deploytype'] = { type: 'string', value: this.deployType };

	// Fetch Java max heap size settings.
	this.javacMaxMemory = cli.tiapp.properties['android.javac.maxmemory'] && cli.tiapp.properties['android.javac.maxmemory'].value || config.get('android.javac.maxMemory', '3072M');
	this.dxMaxMemory = cli.tiapp.properties['android.dx.maxmemory'] && cli.tiapp.properties['android.dx.maxmemory'].value || config.get('android.dx.maxMemory', '3072M');

	// Transpilation details
	this.transpile = cli.tiapp['transpile'] !== false; // Transpiling is an opt-out process now
	// If they're passing flag to do source-mapping, that overrides everything, so turn it on
	if (cli.argv['source-maps']) {
		this.sourceMaps = true;
		// if they haven't, respect the tiapp.xml value if set one way or the other
	} else if (Object.prototype.hasOwnProperty.call(cli.tiapp, 'source-maps')) { // they've explicitly set a value in tiapp.xml
		this.sourceMaps = cli.tiapp['source-maps'] === true; // respect the tiapp.xml value
	} else { // otherwise turn on by default for non-production builds
		this.sourceMaps = this.deployType !== 'production';
	}

	// We get a string here like 6.2.414.36, we need to convert it to 62 (integer)
	const v8Version = this.packageJson.v8.version;
	const found = v8Version.match(V8_STRING_VERSION_REGEXP);
	this.chromeVersion = parseInt(found[1] + found[2]); // concat the first two numbers as string, then turn to int

	// manually inject the build profile settings into the tiapp.xml
	switch (this.deployType) {
		case 'production':
			this.minifyJS = true;
			this.encryptJS = true;
			this.minifyCSS = true;
			this.allowDebugging = false;
			this.allowProfiling = false;
			this.proguard = false;
			break;

		case 'test':
			this.minifyJS = true;
			this.encryptJS = true;
			this.minifyCSS = true;
			this.allowDebugging = true;
			this.allowProfiling = true;
			this.proguard = false;
			break;

		case 'development':
		default:
			this.minifyJS = false;
			this.encryptJS = false;
			this.minifyCSS = false;
			this.allowDebugging = true;
			this.allowProfiling = true;
			this.proguard = false;
	}

	if (cli.tiapp.properties['ti.android.compilejs']) {
		logger.warn(__('The %s tiapp.xml property has been deprecated, please use the %s option to bypass JavaScript minification', 'ti.android.compilejs'.cyan, '--skip-js-minify'.cyan));
	}

	if (cli.argv['skip-js-minify']) {
		this.minifyJS = false;
	}

	// Do we write out process.env into a file in the app to use?
	this.writeEnvVars = this.deployType !== 'production';

	// check the app name
	if (cli.tiapp.name.indexOf('&') !== -1) {
		if (config.get('android.allowAppNameAmpersands', false)) {
			logger.warn(__('The app name "%s" contains an ampersand (&) which will most likely cause problems.', cli.tiapp.name));
			logger.warn(__('It is recommended that you define the app name using i18n strings.'));
			logger.warn(__('Refer to %s for more information.', 'https://titaniumsdk.com/guide/Titanium_SDK/Titanium_SDK_How-tos/Cross-Platform_Mobile_Development_In_Titanium/Internationalization.html'.cyan));
		} else {
			logger.error(__('The app name "%s" contains an ampersand (&) which will most likely cause problems.', cli.tiapp.name));
			logger.error(__('It is recommended that you define the app name using i18n strings.'));
			logger.error(__('Refer to %s for more information.', 'https://titaniumsdk.com/guide/Titanium_SDK/Titanium_SDK_How-tos/Cross-Platform_Mobile_Development_In_Titanium/Internationalization.html'));
			logger.error(__('To allow ampersands in the app name, run:'));
			logger.error('    %sti config android.allowAppNameAmpersands true\n', process.env.APPC_ENV ? 'appc ' : '');
			process.exit(1);
		}
	}

	// check the Android specific app id rules
	if (!config.get('app.skipAppIdValidation') && !cli.tiapp.properties['ti.skipAppIdValidation']) {
		if (!/^([a-zA-Z_]{1}[a-zA-Z0-9_-]*(\.[a-zA-Z0-9_-]*)*)$/.test(cli.tiapp.id)) {
			logger.error(__('tiapp.xml contains an invalid app id "%s"', cli.tiapp.id));
			logger.error(__('The app id must consist only of letters, numbers, dashes, and underscores.'));
			logger.error(__('Note: Android does not allow dashes.'));
			logger.error(__('The first character must be a letter or underscore.'));
			logger.error(__('Usually the app id is your company\'s reversed Internet domain name. (i.e. com.example.myapp)') + '\n');
			process.exit(1);
		}

		if (!/^([a-zA-Z_]{1}[a-zA-Z0-9_]*(\.[a-zA-Z_]{1}[a-zA-Z0-9_]*)*)$/.test(cli.tiapp.id)) {
			logger.error(__('tiapp.xml contains an invalid app id "%s"', cli.tiapp.id));
			logger.error(__('The app id must consist of letters, numbers, and underscores.'));
			logger.error(__('The first character must be a letter or underscore.'));
			logger.error(__('The first character after a period must not be a number.'));
			logger.error(__('Usually the app id is your company\'s reversed Internet domain name. (i.e. com.example.myapp)') + '\n');
			process.exit(1);
		}

		if (!ti.validAppId(cli.tiapp.id)) {
			logger.error(__('Invalid app id "%s"', cli.tiapp.id));
			logger.error(__('The app id must not contain Java reserved words.') + '\n');
			process.exit(1);
		}
	}

	// check the default unit
	cli.tiapp.properties || (cli.tiapp.properties = {});
	cli.tiapp.properties['ti.ui.defaultunit'] || (cli.tiapp.properties['ti.ui.defaultunit'] = { type: 'string', value: 'system' });
	if (!/^system|px|dp|dip|mm|cm|in$/.test(cli.tiapp.properties['ti.ui.defaultunit'].value)) {
		logger.error(__('Invalid "ti.ui.defaultunit" property value "%s"', cli.tiapp.properties['ti.ui.defaultunit'].value) + '\n');
		logger.log(__('Valid units:'));
		'system,px,dp,dip,mm,cm,in'.split(',').forEach(function (unit) {
			logger.log('  ' + unit.cyan);
		});
		logger.log();
		process.exit(1);
	}

	// if we're building for the emulator, make sure we don't have any issues
	if (cli.argv.target === 'emulator') {
		this.androidInfo.issues.forEach(function (issue) {
			if (/^ANDROID_MISSING_(LIBGL|I386_ARCH|IA32_LIBS|32BIT_GLIBC|32BIT_LIBSTDCPP)$/.test(issue.id)) {
				issue.message.split('\n').forEach(function (line) {
					logger.warn(line);
				});
			}
		});
	}

	// check that the proguard config exists
	const proguardConfigFile = path.join(cli.argv['project-dir'], 'platform', 'android', 'proguard.cfg');
	if (this.proguard && !fs.existsSync(proguardConfigFile)) {
		logger.error(__('Missing ProGuard configuration file'));
		logger.error(__('ProGuard settings must go in the file "%s"', proguardConfigFile));
		logger.error(__('For example configurations, visit %s', 'http://proguard.sourceforge.net/index.html#manual/examples.html') + '\n');
		process.exit(1);
	}

	// map sdk versions to sdk targets instead of by id
	const targetSDKMap = {

		// placeholder for gradle to use
		[this.compileSdkVersion]: {
			sdk: this.compileSdkVersion
		}
	};
	Object.keys(this.androidInfo.targets).forEach(function (i) {
		var t = this.androidInfo.targets[i];
		if (t.type === 'platform') {
			targetSDKMap[t.id.replace('android-', '')] = t;
		}
	}, this);

	// check the Android SDK we require to build exists
	this.androidCompileSDK = targetSDKMap[this.compileSdkVersion];

	// If "tiapp.xml" contains "AndroidManifest.xml" info, then load/store it to "this.customAndroidManifest" field.
	try {
		if (cli.tiapp.android && cli.tiapp.android.manifest) {
			this.customAndroidManifest = AndroidManifest.fromXmlString(cli.tiapp.android.manifest);
		}
	} catch (ex) {
		logger.error(__n('Malformed <manifest> definition in the <android> section of the tiapp.xml'));
		process.exit(1);
	}

	// If project has "./platform/android/AndroidManifest.xml" file, then load/store it to "this.customAndroidManifest" field.
	const externalAndroidManifestFilePath = path.join(cli.argv['project-dir'], 'platform', 'android', 'AndroidManifest.xml');
	try {
		if (fs.existsSync(externalAndroidManifestFilePath)) {
			const externalAndroidManifest = AndroidManifest.fromFilePathSync(externalAndroidManifestFilePath);
			if (externalAndroidManifest) {
				if (this.customAndroidManifest) {
					// External manifest file's settings will overwrite "tiapp.xml" manifest settings.
					this.customAndroidManifest.copyFromAndroidManifest(externalAndroidManifest);
				} else {
					// The "tiapp.xml" did not contain any manifest settings. So, keep external manifest settings as-is.
					this.customAndroidManifest = externalAndroidManifest;
				}
			}
		}
	} catch (ex) {
		logger.error(__n('Malformed custom AndroidManifest.xml file: %s', externalAndroidManifestFilePath));
		process.exit(1);
	}

	// validate the sdk levels
	const usesSDK = this.customAndroidManifest ? this.customAndroidManifest.getUsesSdk() : null;

	this.minSDK = this.minSupportedApiLevel;
	this.targetSDK = cli.tiapp.android && ~~cli.tiapp.android['tool-api-level'] || null;
	this.maxSDK = null;

	if (this.targetSDK) {
		logger.log();
		logger.warn(__('%s has been deprecated, please specify the target SDK API using the %s tag:', '<tool-api-level>'.cyan, '<uses-sdk>'.cyan));
		logger.warn();
		logger.warn('<ti:app xmlns:ti="http://ti.appcelerator.org">'.grey);
		logger.warn('    <android>'.grey);
		logger.warn('        <manifest>'.grey);
		logger.warn(('            <uses-sdk android:minSdkVersion="' + this.minSupportedApiLevel + '" android:targetSdkVersion="' + this.minTargetApiLevel + '" android:maxSdkVersion="' + this.maxSupportedApiLevel + '"/>').magenta);
		logger.warn('        </manifest>'.grey);
		logger.warn('    </android>'.grey);
		logger.warn('</ti:app>'.grey);
		logger.log();
	}

	if (usesSDK) {
		usesSDK.minSdkVersion    && (this.minSDK    = usesSDK.minSdkVersion);
		usesSDK.targetSdkVersion && (this.targetSDK = usesSDK.targetSdkVersion);
		usesSDK.maxSdkVersion    && (this.maxSDK    = usesSDK.maxSdkVersion);
	}

	// we need to translate the sdk to a real api level (i.e. L => 20, MNC => 22) so that
	// we can valiate them
	function getRealAPILevel(ver) {
		return (ver && targetSDKMap[ver] && targetSDKMap[ver].sdk) || ver;
	}
	this.realMinSDK    = getRealAPILevel(this.minSDK);
	this.realTargetSDK = getRealAPILevel(this.targetSDK);
	this.realMaxSDK    = getRealAPILevel(this.maxSDK);

	// min sdk is too old
	if (this.minSDK && this.realMinSDK < this.minSupportedApiLevel) {
		logger.error(__('The minimum supported SDK API version must be %s or newer, but is currently set to %s', this.minSupportedApiLevel, this.minSDK + (this.minSDK !== this.realMinSDK ? ' (' + this.realMinSDK + ')' : '')) + '\n');
		logger.log(
			appc.string.wrap(
				__('Update the %s in the tiapp.xml or custom AndroidManifest to at least %s:', 'android:minSdkVersion'.cyan, String(this.minSupportedApiLevel).cyan),
				config.get('cli.width', 100)
			)
		);
		logger.log();
		logger.log('<ti:app xmlns:ti="http://ti.appcelerator.org">'.grey);
		logger.log('    <android>'.grey);
		logger.log('        <manifest>'.grey);
		logger.log(('            <uses-sdk '
			+ 'android:minSdkVersion="' + this.minSupportedApiLevel + '" '
			+ (this.targetSDK ? 'android:targetSdkVersion="' + this.targetSDK + '" ' : '')
			+ (this.maxSDK ? 'android:maxSdkVersion="' + this.maxSDK + '" ' : '')
			+ '/>').magenta);
		logger.log('        </manifest>'.grey);
		logger.log('    </android>'.grey);
		logger.log('</ti:app>'.grey);
		logger.log();
		process.exit(1);
	}

	if (this.targetSDK) {
		// target sdk is too old
		if (this.realTargetSDK < this.minTargetApiLevel) {
			logger.error(__('The target SDK API %s is not supported by Titanium SDK %s', this.targetSDK + (this.targetSDK !== this.realTargetSDK ? ' (' + this.realTargetSDK + ')' : ''), ti.manifest.version));
			logger.error(__('The target SDK API version must be %s or newer', this.minTargetApiLevel) + '\n');
			logger.log(
				appc.string.wrap(
					__('Update the %s in the tiapp.xml or custom AndroidManifest to at least %s:', 'android:targetSdkVersion'.cyan, String(this.minTargetApiLevel).cyan),
					config.get('cli.width', 100)
				)
			);
			logger.log();
			logger.log('<ti:app xmlns:ti="http://ti.appcelerator.org">'.grey);
			logger.log('    <android>'.grey);
			logger.log('        <manifest>'.grey);
			logger.log(('            <uses-sdk '
				+ (this.minSupportedApiLevel ? 'android:minSdkVersion="' + this.minSupportedApiLevel + '" ' : '')
				+ 'android:targetSdkVersion="' + this.minTargetApiLevel + '" '
				+ (this.maxSDK ? 'android:maxSdkVersion="' + this.maxSDK + '" ' : '')
				+ '/>').magenta);
			logger.log('        </manifest>'.grey);
			logger.log('    </android>'.grey);
			logger.log('</ti:app>'.grey);
			logger.log();
			process.exit(1);
		}

		// target sdk < min sdk
		if (this.realTargetSDK < this.realMinSDK) {
			logger.error(__('The target SDK API must be greater than or equal to the minimum SDK %s, but is currently set to %s',
				this.minSDK + (this.minSDK !== this.realMinSDK ? ' (' + this.realMinSDK + ')' : ''),
				this.targetSDK + (this.targetSDK !== this.realTargetSDK ? ' (' + this.realTargetSDK + ')' : '')
			) + '\n');
			process.exit(1);
		}

	} else {
		this.targetSDK = this.maxSupportedApiLevel;
		this.realTargetSDK = this.targetSDK;
	}

	// check that we have this target sdk installed
	this.androidTargetSDK = targetSDKMap[this.targetSDK];

	if (!this.androidTargetSDK) {
		this.androidTargetSDK = {
			sdk: this.targetSDK
		};
	}

	if (this.realTargetSDK < this.realMinSDK) {
		logger.error(__('Target Android SDK API version must be %s or newer', this.minSDK) + '\n');
		process.exit(1);
	}

	if (this.realMaxSDK && this.realMaxSDK < this.realTargetSDK) {
		logger.error(__('Maximum Android SDK API version must be greater than or equal to the target SDK API %s, but is currently set to %s',
			this.targetSDK + (this.targetSDK !== this.realTargetSDK ? ' (' + this.realTargetSDK + ')' : ''),
			this.maxSDK + (this.maxSDK !== this.realMaxSDK ? ' (' + this.realMaxSDK + ')' : '')
		) + '\n');
		process.exit(1);
	}

	if (this.maxSupportedApiLevel && this.realTargetSDK > this.maxSupportedApiLevel) {
		// print warning that version this.targetSDK is not tested
		logger.warn(__('Building with Android SDK API %s which hasn\'t been tested against Titanium SDK %s',
			String(this.targetSDK + (this.targetSDK !== this.realTargetSDK ? ' (' + this.realTargetSDK + ')' : '')).cyan,
			this.titaniumSdkVersion
		));
	}

	// determine the abis to support
	this.abis = this.validABIs;
	const customABIs = cli.tiapp.android && cli.tiapp.android.abi && cli.tiapp.android.abi.indexOf('all') === -1;
	if (!customABIs && (this.deployType === 'production')) {
		// If "tiapp.xml" does not have <abi/> entry, then exclude "x86" and "x86_64" from production builds by default.
		// These abis are mostly needed for testing in an emulator. Physical x86 devices are extremely rare.
		this.abis = this.abis.filter(abi => {
			return !abi.startsWith('x86');
		});
	}
	if (customABIs) {
		this.abis = cli.tiapp.android.abi;
		this.abis.forEach(function (abi) {
			if (this.validABIs.indexOf(abi) === -1) {
				logger.error(__('Invalid ABI "%s"', abi) + '\n');
				logger.log(__('Valid ABIs:'));
				this.validABIs.forEach(function (name) {
					logger.log('   ' + name.cyan);
				});
				logger.log();
				process.exit(1);
			}
		}, this);
	}

	let deviceId = cli.argv['device-id'];

	if (!this.buildOnly && /^device|emulator$/.test(this.target) && deviceId === undefined && config.get('android.autoSelectDevice', true)) {
		// no --device-id, so intelligently auto select one
		const apiLevel = this.androidTargetSDK.sdk,
			devicesToAutoSelectFrom = this.devicesToAutoSelectFrom.sort((a, b) => b.api - a.api),
			len = devicesToAutoSelectFrom.length;

		// reset the device id
		deviceId = null;

		if (cli.argv.target === 'device') {
			logger.info('Auto selecting device');
		} else {
			logger.info('Auto selecting emulator');
		}

		function setDeviceId(device) {
			deviceId = cli.argv['device-id'] = device.id;

			let gapi = '';
			if (device.googleApis) {
				gapi = (' (' + __('Google APIs supported') + ')').grey;
			} else if (device.googleApis === null) {
				gapi = (' (' + __('Google APIs support unknown') + ')').grey;
			}

			if (cli.argv.target === 'device') {
				logger.info(__('Auto selected device %s %s', device.name.cyan, device.version) + gapi);
			} else {
				logger.info(__('Auto selected emulator %s %s', device.name.cyan, device.version) + gapi);
			}
		}

		logger.debug(__('Searching for API >= %s and has Google APIs', apiLevel.cyan));
		for (let i = 0; i < len; i++) {
			if (devicesToAutoSelectFrom[i].api >= apiLevel && devicesToAutoSelectFrom[i].googleApis) {
				setDeviceId(devicesToAutoSelectFrom[i]);
				break;
			}
		}

		if (!deviceId) {
			logger.debug(__('Searching for API >= %s', apiLevel.cyan));
			for (let i = 0; i < len; i++) {
				if (devicesToAutoSelectFrom[i].api >= apiLevel) {
					setDeviceId(devicesToAutoSelectFrom[i]);
					break;
				}
			}

			if (!deviceId) {
				logger.debug(__('Searching for API < %s and has Google APIs', apiLevel.cyan));
				for (let i = 0; i < len; i++) {
					if (devicesToAutoSelectFrom[i].api < apiLevel && devicesToAutoSelectFrom[i].googleApis) { // eslint-disable-line max-depth
						setDeviceId(devicesToAutoSelectFrom[i]);
						break;
					}
				}

				if (!deviceId) {
					logger.debug(__('Searching for API < %s', apiLevel.cyan));
					for (let i = 0; i < len; i++) { // eslint-disable-line max-depth
						if (devicesToAutoSelectFrom[i].api < apiLevel) { // eslint-disable-line max-depth
							setDeviceId(devicesToAutoSelectFrom[i]);
							break;
						}
					}

					if (!deviceId) { // eslint-disable-line max-depth
						logger.debug(__('Selecting first device'));
						setDeviceId(devicesToAutoSelectFrom[0]);
					}
				}
			}
		}

		const devices = deviceId === 'all'
			? this.devices
			: this.devices.filter(function (d) { return d.id === deviceId; });
		devices.forEach(function (device) {
			if (Array.isArray(device.abi) && !device.abi.some(function (a) { return this.abis.indexOf(a) !== -1; }.bind(this))) { // eslint-disable-line max-statements-per-line
				if (this.target === 'emulator') {
					logger.error(__n('The emulator "%%s" does not support the desired ABI %%s', 'The emulator "%%s" does not support the desired ABIs %%s', this.abis.length, device.name, '"' + this.abis.join('", "') + '"'));
				} else {
					logger.error(__n('The device "%%s" does not support the desired ABI %%s', 'The device "%%s" does not support the desired ABIs %%s', this.abis.length, device.model || device.manufacturer, '"' + this.abis.join('", "') + '"'));
				}
				logger.error(__('Supported ABIs: %s', device.abi.join(', ')) + '\n');

				logger.log(__('You need to add at least one of the device\'s supported ABIs to the tiapp.xml'));
				logger.log();
				logger.log('<ti:app xmlns:ti="http://ti.appcelerator.org">'.grey);
				logger.log('    <!-- snip -->'.grey);
				logger.log('    <android>'.grey);
				logger.log(('        <abi>' + this.abis.concat(device.abi).join(',') + '</abi>').magenta);
				logger.log('    </android>'.grey);
				logger.log('</ti:app>'.grey);
				logger.log();

				process.exit(1);
			}
		}, this);
	}

	// validate debugger and profiler options
	const tool = [];
	this.allowDebugging && tool.push('debug');
	this.allowProfiling && tool.push('profiler');
	this.debugHost = null;
	this.debugPort = null;
	this.profilerHost = null;
	this.profilerPort = null;
	tool.forEach(function (type) {
		if (cli.argv[type + '-host']) {
			if (typeof cli.argv[type + '-host'] === 'number') {
				logger.error(__('Invalid %s host "%s"', type, cli.argv[type + '-host']) + '\n');
				logger.log(__('The %s host must be in the format "host:port".', type) + '\n');
				process.exit(1);
			}

			const parts = cli.argv[type + '-host'].split(':');
			if (parts.length < 2) {
				logger.error(__('Invalid ' + type + ' host "%s"', cli.argv[type + '-host']) + '\n');
				logger.log(__('The %s host must be in the format "host:port".', type) + '\n');
				process.exit(1);
			}

			const port = parseInt(parts[1]);
			if (isNaN(port) || port < 1 || port > 65535) {
				logger.error(__('Invalid ' + type + ' host "%s"', cli.argv[type + '-host']) + '\n');
				logger.log(__('The port must be a valid integer between 1 and 65535.') + '\n');
				process.exit(1);
			}

			this[type + 'Host'] = parts[0];
			this[type + 'Port'] = port;
		}
	}, this);

	if (this.debugPort || this.profilerPort) {
		// if debugging/profiling, make sure we only have one device and that it has an sd card
		if (this.target === 'emulator') {
			const emu = this.devices.filter(function (d) { return d.id === deviceId; }).shift(); // eslint-disable-line max-statements-per-line
			if (!emu) {
				logger.error(__('Unable find emulator "%s"', deviceId) + '\n');
				process.exit(1);
			} else if (!emu.sdcard && emu.type !== 'genymotion') {
				logger.error(__('The selected emulator "%s" does not have an SD card.', emu.name));
				if (this.profilerPort) {
					logger.error(__('An SD card is required for profiling.') + '\n');
				} else {
					logger.error(__('An SD card is required for debugging.') + '\n');
				}
				process.exit(1);
			}
		} else if (this.target === 'device' && deviceId === 'all' && this.devices.length > 1) {
			// fail, can't do 'all' for debug builds
			logger.error(__('Cannot debug application when --device-id is set to "all" and more than one device is connected.'));
			logger.error(__('Please specify a single device to debug on.') + '\n');
			process.exit(1);
		}
	}

	// check that the build directory is writeable
	const buildDir = path.join(cli.argv['project-dir'], 'build');
	if (fs.existsSync(buildDir)) {
		if (!afs.isDirWritable(buildDir)) {
			logger.error(__('The build directory is not writeable: %s', buildDir) + '\n');
			logger.log(__('Make sure the build directory is writeable and that you have sufficient free disk space.') + '\n');
			process.exit(1);
		}
	} else if (!afs.isDirWritable(cli.argv['project-dir'])) {
		logger.error(__('The project directory is not writeable: %s', cli.argv['project-dir']) + '\n');
		logger.log(__('Make sure the project directory is writeable and that you have sufficient free disk space.') + '\n');
		process.exit(1);
	}

	// make sure we have an icon
	if (this.customAndroidManifest) {
		const appIconValue = this.customAndroidManifest.getAppAttribute('android:icon');
		if (appIconValue) {
			cli.tiapp.icon = appIconValue.replace(/^@drawable\//, '') + '.png';
		}
	}
	if (!cli.tiapp.icon || ![ 'Resources', 'Resources/android' ].some(function (p) {
		return fs.existsSync(cli.argv['project-dir'], p, cli.tiapp.icon);
	})) {
		cli.tiapp.icon = 'appicon.png';
	}

	return function (callback) {
		this.validateTiModules('android', this.deployType, function validateTiModulesCallback(err, modules) {
			// Create a copy of the given modules found in "tiapp.xml", excluding modules that we no longer support.
			const blacklistedModuleNames = [ 'com.soasta.touchtest' ];
			this.modules = modules.found.filter((module) => {
				const isBlackListed = blacklistedModuleNames.includes(module.id);
				if (isBlackListed) {
					this.logger.warn(__('Skipping unsupported module "%s"', module.id.cyan));
				}
				return !isBlackListed;
			});

			for (const module of this.modules) {
				// Flag object as either a native JAR/AAR module or a scripted CommonJS module for fast if-checks later.
				module.native = (module.platform.indexOf('commonjs') < 0);

				// For native modules, verify they are built with API version 2.0 or higher.
				if (module.native && (~~module.manifest.apiversion < 2)) {
					this.logger.error(__('The "apiversion" for "%s" in the module manifest is less than version 2.', module.manifest.moduleid.cyan));
					this.logger.error(__('The module was likely built against a Titanium SDK 1.8.0.1 or older.'));
					this.logger.error(__('Please use a version of the module that has "apiversion" 2 or greater'));
					this.logger.log();
					process.exit(1);
				}

				// For CommonJS modules, verfiy we can find the main script to be loaded by require() method.
				if (!module.native) {
					// Look for legacy "<module.id>.js" script file first.
					let jsFilePath = path.join(module.modulePath, module.id + '.js');
					if (!fs.existsSync(jsFilePath)) {
						// Check if require API can find the script.
						jsFilePath = require.resolve(module.modulePath);
						if (!fs.existsSync(jsFilePath)) {
							this.logger.error(__(
								'Module "%s" v%s is missing main file: %s, package.json with "main" entry, index.js, or index.json',
								module.id, module.manifest.version || 'latest', module.id + '.js') + '\n');
							process.exit(1);
						}
					}
				} else {
					// Limit application build ABI to that of provided native modules.
					this.abis = this.abis.filter(abi => {
						if (!module.manifest.architectures.includes(abi)) {
							this.logger.warn(__('Module %s does not contain %s ABI. Application will build without %s ABI support!', module.id.cyan, abi.cyan, abi.cyan));
							return false;
						}
						return true;
					});
				}

				// scan the module for any CLI hooks
				cli.scanHooks(path.join(module.modulePath, 'hooks'));
			}

			// check for any missing module dependencies
			let hasAddedModule = false;
			for (const module of this.modules) {
				if (!module.native) {
					continue;
				}

				const timoduleXmlFile = path.join(module.modulePath, 'timodule.xml');
				const timodule = fs.existsSync(timoduleXmlFile) ? new tiappxml(timoduleXmlFile) : undefined;

				if (timodule && Array.isArray(timodule.modules)) {
					for (let dependency of timodule.modules) {
						if (!dependency.platform || /^android$/.test(dependency.platform)) {
							const isMissing = !this.modules.some(function (mod) {
								return mod.native && (mod.id === dependency.id);
							});
							if (isMissing) {
								// attempt to include missing dependency
								dependency.depended = module;
								this.cli.tiapp.modules.push({
									id: dependency.id,
									version: dependency.version,
									platform: [ 'android' ],
									deployType: [ this.deployType ]
								});
								hasAddedModule = true;
							}
						}
					}
				}
			}

			// Re-validate if a module dependency was added to the modules array.
			if (hasAddedModule) {
				return this.validateTiModules('android', this.deployType, validateTiModulesCallback.bind(this));
			}

			callback();
		}.bind(this));
	}.bind(this);
};

AndroidBuilder.prototype.run = async function run(logger, config, cli, finished) {
	try {
		// Call the base builder's run() method.
		Builder.prototype.run.apply(this, arguments);

		// Notify plugins that we're about to begin.
		await new Promise(resolve => cli.emit('build.pre.construct', this, resolve));

		// Post build anlytics.
		await this.doAnalytics();

		// Initialize build system. Checks if we need to do a clean or incremental build.
		await this.initialize();
		await this.loginfo();
		await this.computeHashes();
		await this.readBuildManifest();
		await this.checkIfNeedToRecompile();

		// Notify plugins that we're prepping to compile.
		await new Promise((resolve, reject) => {
			cli.emit('build.pre.compile', this, e => (e ? reject(e) : resolve()));
		});

		// Make sure we have an "app.js" script. Will exit with a build failure if not found.
		// Note: This used to be validated by the validate() method, but Alloy plugin
		//       generates the "app.js" script via the "build.pre.compile" hook event above.
		ti.validateAppJsExists(this.projectDir, logger, 'android');

		// Generate all gradle files, gradle app project, and gradle library projects (if needed).
		await this.processLibraries();
		await this.generateRootProjectFiles();
		await this.generateAppProject();

		// Build the app.
		await new Promise(resolve => cli.emit('build.pre.build', this, resolve));
		await this.buildAppProject();
		await new Promise(resolve => cli.emit('build.post.build', this, resolve));

		// Write Titanium build settings to file. Used to determine if next build can be incremental or not.
		await this.writeBuildManifest();

		// Log how long the build took.
		if (!this.buildOnly && this.target === 'simulator') {
			const delta = appc.time.prettyDiff(this.cli.startTime, Date.now());
			logger.info(__('Finished building the application in %s', delta.cyan));
		}

		// Notify plugins that the build is done.
		await new Promise(resolve => cli.emit('build.post.compile', this, resolve));
		await new Promise(resolve => cli.emit('build.finalize', this, resolve));
	} catch (err) {
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

AndroidBuilder.prototype.doAnalytics = async function doAnalytics() {
	const cli = this.cli;
	let eventName = 'android.' + cli.argv.target;

	if (cli.argv.target === 'dist-playstore') {
		eventName = 'android.distribute.playstore';
	} else if (this.allowDebugging && this.debugPort) {
		eventName += '.debug';
	} else if (this.allowProfiling && this.profilerPort) {
		eventName += '.profile';
	} else {
		eventName += '.run';
	}

	cli.addAnalyticsEvent(eventName, {
		name: cli.tiapp.name,
		publisher: cli.tiapp.publisher,
		url: cli.tiapp.url,
		image: cli.tiapp.icon,
		appid: cli.tiapp.id,
		description: cli.tiapp.description,
		type: cli.argv.type,
		guid: cli.tiapp.guid,
		version: cli.tiapp.version,
		copyright: cli.tiapp.copyright,
		date: (new Date()).toDateString()
	});
};

AndroidBuilder.prototype.initialize = async function initialize() {
	const argv = this.cli.argv;

	this.appid = this.tiapp.id;
	this.appid.indexOf('.') === -1 && (this.appid = 'com.' + this.appid);

	this.classname = this.tiapp.name.split(/[^A-Za-z0-9_]/).map(function (word) {
		return appc.string.capitalize(word.toLowerCase());
	}).join('');
	/^[0-9]/.test(this.classname) && (this.classname = '_' + this.classname);

	const deviceId = this.deviceId = argv['device-id'];
	if (!this.buildOnly && this.target === 'emulator') {
		const emu = this.devices.filter(function (e) { return e.id === deviceId; }).shift(); // eslint-disable-line max-statements-per-line
		if (!emu) {
			// sanity check
			this.logger.error(__('Unable to find Android emulator "%s"', deviceId) + '\n');
			process.exit(0);
		}
		this.emulator = emu;
	}

	this.outputDir = argv['output-dir'] ? afs.resolvePath(argv['output-dir']) : null;

	// set the keystore to the dev keystore, if not already set
	this.keystore = argv.keystore;
	this.keystoreStorePassword = argv['store-password'];
	this.keystoreKeyPassword = argv['key-password'];
	this.sigalg = argv['sigalg'];
	if (!this.keystore) {
		this.keystore = path.join(this.platformPath, 'dev_keystore');
		this.keystoreStorePassword = 'tirocks';
		this.keystoreAlias = {
			name: 'tidev',
			sigalg: 'MD5withRSA'
		};
	}

	const loadFromSDCardProp = this.tiapp.properties['ti.android.loadfromsdcard'];
	this.loadFromSDCard = loadFromSDCardProp && loadFromSDCardProp.value === true;

	// Array of gradle/maven compatible library reference names the app project depends on.
	// Formatted as: "<group.id>:<artifact-id>:<version>"
	// Example: "com.google.android.gms:play-services-base:11.0.4"
	this.libDependencyStrings = [];

	// Array of JAR/AAR library file paths the app project depends on.
	this.libFilePaths = [];

	// Array of gradle library project names the app depends on.
	this.libProjectNames = [];

	// Array of maven repository URLs the app project will need to search for dependencies.
	// Typically set to local "file://" URLs referencing installed Titanium module.
	this.mavenRepositoryUrls = [];

	// Set up directory paths.
	this.buildAssetsDir                 = path.join(this.buildDir, 'assets');
	this.buildTiIncrementalDir          = path.join(this.buildDir, 'ti-incremental');
	this.buildAppDir                    = path.join(this.buildDir, 'app');
	this.buildAppMainDir                = path.join(this.buildAppDir, 'src', 'main');
	this.buildAppMainAssetsDir          = path.join(this.buildAppMainDir, 'assets');
	this.buildAppMainAssetsResourcesDir = path.join(this.buildAppMainAssetsDir, 'Resources');
	this.buildGenAppIdDir               = path.join(this.buildAppMainDir, 'java', this.appid.split('.').join(path.sep));
	this.buildAppMainResDir             = path.join(this.buildAppMainDir, 'res');
	this.buildAppMainResDrawableDir     = path.join(this.buildAppMainResDir, 'drawable');
	this.templatesDir                   = path.join(this.platformPath, 'templates', 'build');

	// The "appc-cli-titanium" module reads this builder's "buildBinAssetsDir" variable when "tiapp.xml"
	// property "appc-sourcecode-encryption-policy" is set to "remote" or "embed".
	this.buildBinAssetsDir = this.buildAppMainAssetsDir;

	// Path to file storing some Titanium build settings.
	// Used to determine if next build can be an incremental build or must be a clean/rebuild.
	this.buildManifestFile = path.join(this.buildDir, 'build-manifest.json');

	const buildTypeName = (this.allowDebugging) ? 'debug' : 'release';
	this.apkFile = path.join(this.buildDir, 'app', 'build', 'outputs', 'apk', buildTypeName, `app-${buildTypeName}.apk`);

	// Assign base builder file list for backwards compatibility with existing
	// hooks that may use lastBuildFiles.
	// TODO: remove in 9.0
	this.lastBuildFiles = this.buildDirFiles;
};

AndroidBuilder.prototype.loginfo = async function loginfo() {
	this.logger.debug(__('Titanium SDK Android directory: %s', this.platformPath.cyan));
	this.logger.info(__('Deploy type: %s', this.deployType.cyan));
	this.logger.info(__('Building for target: %s', this.target.cyan));

	if (this.buildOnly) {
		this.logger.info(__('Performing build only'));
	} else if (this.target === 'emulator') {
		this.logger.info(__('Building for emulator: %s', this.deviceId.cyan));
	} else if (this.target === 'device') {
		this.logger.info(__('Building for device: %s', this.deviceId.cyan));
	}

	this.logger.info(__('Targeting Android SDK API: %s', String(this.targetSDK + (this.targetSDK !== this.realTargetSDK ? ' (' + this.realTargetSDK + ')' : '')).cyan));
	this.logger.info(__('Building for the following architectures: %s', this.abis.join(', ').cyan));
	this.logger.info(__('Signing with keystore: %s', (this.keystore + ' (' + this.keystoreAlias.name + ')').cyan));

	this.logger.debug(__('App ID: %s', this.appid.cyan));
	this.logger.debug(__('Classname: %s', this.classname.cyan));

	if (this.allowDebugging && this.debugPort) {
		this.logger.info(__('Debugging enabled via debug port: %s', String(this.debugPort).cyan));
	} else {
		this.logger.info(__('Debugging disabled'));
	}

	if (this.allowProfiling && this.profilerPort) {
		this.logger.info(__('Profiler enabled via profiler port: %s', String(this.profilerPort).cyan));
	} else {
		this.logger.info(__('Profiler disabled'));
	}

	this.logger.info(__('Transpile javascript: %s', (this.transpile ? 'true' : 'false').cyan));
	this.logger.info(__('Generate source maps: %s', (this.sourceMaps ? 'true' : 'false').cyan));
};

AndroidBuilder.prototype.computeHashes = async function computeHashes() {
	// modules
	this.modulesHash = !Array.isArray(this.tiapp.modules) ? '' : this.hash(this.tiapp.modules.filter(function (m) {
		return !m.platform || /^android|commonjs$/.test(m.platform);
	}).map(function (m) {
		return m.id + ',' + m.platform + ',' + m.version;
	}).join('|'));

	// tiapp.xml properties, activities, and services
	this.propertiesHash = this.hash(this.tiapp.properties ? JSON.stringify(this.tiapp.properties) : '');
	const android = this.tiapp.android;
	this.activitiesHash = this.hash(android && android.application && android.application ? JSON.stringify(android.application.activities) : '');
	this.servicesHash = this.hash(android && android.services ? JSON.stringify(android.services) : '');
};

AndroidBuilder.prototype.readBuildManifest = async function readBuildManifest() {
	// read the build manifest from the last build, if exists, so we
	// can determine if we need to do a full rebuild
	this.buildManifest = {};

	if (await fs.exists(this.buildManifestFile)) {
		try {
			this.buildManifest = JSON.parse(await fs.readFile(this.buildManifestFile)) || {};
		} catch (e) {
			// ignore
		}
	}
};

AndroidBuilder.prototype.checkIfShouldForceRebuild = function checkIfShouldForceRebuild() {
	var manifest = this.buildManifest;

	if (this.cli.argv.force) {
		this.logger.info(__('Forcing rebuild: %s flag was set', '--force'.cyan));
		return true;
	}

	if (!fs.existsSync(this.buildManifestFile)) {
		this.logger.info(__('Forcing rebuild: %s does not exist', this.buildManifestFile.cyan));
		return true;
	}

	// check if the target changed
	if (this.target !== manifest.target) {
		this.logger.info(__('Forcing rebuild: target changed since last build'));
		this.logger.info('  ' + __('Was: %s', manifest.target));
		this.logger.info('  ' + __('Now: %s', this.target));
		return true;
	}

	// check if the deploy type changed
	if (this.deployType !== manifest.deployType) {
		this.logger.info(__('Forcing rebuild: deploy type changed since last build'));
		this.logger.info('  ' + __('Was: %s', manifest.deployType));
		this.logger.info('  ' + __('Now: %s', this.deployType));
		return true;
	}

	// check if the classname changed
	if (this.classname !== manifest.classname) {
		this.logger.info(__('Forcing rebuild: classname changed since last build'));
		this.logger.info('  ' + __('Was: %s', manifest.classname));
		this.logger.info('  ' + __('Now: %s', this.classname));
		return true;
	}

	// if encryptJS changed, then we need to recompile the java files
	if (this.encryptJS !== manifest.encryptJS) {
		this.logger.info(__('Forcing rebuild: JavaScript encryption flag changed'));
		this.logger.info('  ' + __('Was: %s', manifest.encryptJS));
		this.logger.info('  ' + __('Now: %s', this.encryptJS));
		return true;
	}

	// check if the titanium sdk paths are different
	if (this.platformPath !== manifest.platformPath) {
		this.logger.info(__('Forcing rebuild: Titanium SDK path changed since last build'));
		this.logger.info('  ' + __('Was: %s', manifest.platformPath));
		this.logger.info('  ' + __('Now: %s', this.platformPath));
		return true;
	}

	// check the git hashes are different
	if (!manifest.gitHash || manifest.gitHash !== ti.manifest.githash) {
		this.logger.info(__('Forcing rebuild: githash changed since last build'));
		this.logger.info('  ' + __('Was: %s', manifest.gitHash));
		this.logger.info('  ' + __('Now: %s', ti.manifest.githash));
		return true;
	}

	// check if the modules hashes are different
	if (this.modulesHash !== manifest.modulesHash) {
		this.logger.info(__('Forcing rebuild: modules hash changed since last build'));
		this.logger.info('  ' + __('Was: %s', manifest.modulesHash));
		this.logger.info('  ' + __('Now: %s', this.modulesHash));
		return true;
	}

	// next we check if any tiapp.xml values changed so we know if we need to reconstruct the main.m
	if (this.tiapp.name !== manifest.name) {
		this.logger.info(__('Forcing rebuild: tiapp.xml project name changed since last build'));
		this.logger.info('  ' + __('Was: %s', manifest.name));
		this.logger.info('  ' + __('Now: %s', this.tiapp.name));
		return true;
	}

	if (this.tiapp.id !== manifest.id) {
		this.logger.info(__('Forcing rebuild: tiapp.xml app id changed since last build'));
		this.logger.info('  ' + __('Was: %s', manifest.id));
		this.logger.info('  ' + __('Now: %s', this.tiapp.id));
		return true;
	}

	if (!this.tiapp.analytics !== !manifest.analytics) {
		this.logger.info(__('Forcing rebuild: tiapp.xml analytics flag changed since last build'));
		this.logger.info('  ' + __('Was: %s', !!manifest.analytics));
		this.logger.info('  ' + __('Now: %s', !!this.tiapp.analytics));
		return true;
	}
	if (this.tiapp.publisher !== manifest.publisher) {
		this.logger.info(__('Forcing rebuild: tiapp.xml publisher changed since last build'));
		this.logger.info('  ' + __('Was: %s', manifest.publisher));
		this.logger.info('  ' + __('Now: %s', this.tiapp.publisher));
		return true;
	}

	if (this.tiapp.url !== manifest.url) {
		this.logger.info(__('Forcing rebuild: tiapp.xml url changed since last build'));
		this.logger.info('  ' + __('Was: %s', manifest.url));
		this.logger.info('  ' + __('Now: %s', this.tiapp.url));
		return true;
	}

	if (this.tiapp.version !== manifest.version) {
		this.logger.info(__('Forcing rebuild: tiapp.xml version changed since last build'));
		this.logger.info('  ' + __('Was: %s', manifest.version));
		this.logger.info('  ' + __('Now: %s', this.tiapp.version));
		return true;
	}

	if (this.tiapp.description !== manifest.description) {
		this.logger.info(__('Forcing rebuild: tiapp.xml description changed since last build'));
		this.logger.info('  ' + __('Was: %s', manifest.description));
		this.logger.info('  ' + __('Now: %s', this.tiapp.description));
		return true;
	}

	if (this.tiapp.copyright !== manifest.copyright) {
		this.logger.info(__('Forcing rebuild: tiapp.xml copyright changed since last build'));
		this.logger.info('  ' + __('Was: %s', manifest.copyright));
		this.logger.info('  ' + __('Now: %s', this.tiapp.copyright));
		return true;
	}

	if (this.tiapp.guid !== manifest.guid) {
		this.logger.info(__('Forcing rebuild: tiapp.xml guid changed since last build'));
		this.logger.info('  ' + __('Was: %s', manifest.guid));
		this.logger.info('  ' + __('Now: %s', this.tiapp.guid));
		return true;
	}

	if (this.tiapp.icon !== manifest.icon) {
		this.logger.info(__('Forcing rebuild: tiapp.xml icon changed since last build'));
		this.logger.info('  ' + __('Was: %s', manifest.icon));
		this.logger.info('  ' + __('Now: %s', this.tiapp.icon));
		return true;
	}

	if (this.tiapp.fullscreen !== manifest.fullscreen) {
		this.logger.info(__('Forcing rebuild: tiapp.xml fullscreen changed since last build'));
		this.logger.info('  ' + __('Was: %s', manifest.fullscreen));
		this.logger.info('  ' + __('Now: %s', this.tiapp.fullscreen));
		return true;
	}

	if (this.tiapp['navbar-hidden'] !== manifest.navbarHidden) {
		this.logger.info(__('Forcing rebuild: tiapp.xml navbar-hidden changed since last build'));
		this.logger.info('  ' + __('Was: %s', manifest.navbarHidden));
		this.logger.info('  ' + __('Now: %s', this.tiapp['navbar-hidden']));
		return true;
	}

	if (this.minSDK !== manifest.minSDK) {
		this.logger.info(__('Forcing rebuild: Android minimum SDK changed since last build'));
		this.logger.info('  ' + __('Was: %s', manifest.minSDK));
		this.logger.info('  ' + __('Now: %s', this.minSDK));
		return true;
	}

	if (this.targetSDK !== manifest.targetSDK) {
		this.logger.info(__('Forcing rebuild: Android target SDK changed since last build'));
		this.logger.info('  ' + __('Was: %s', manifest.targetSDK));
		this.logger.info('  ' + __('Now: %s', this.targetSDK));
		return true;
	}

	if (this.propertiesHash !== manifest.propertiesHash) {
		this.logger.info(__('Forcing rebuild: tiapp.xml properties changed since last build'));
		this.logger.info('  ' + __('Was: %s', manifest.propertiesHash));
		this.logger.info('  ' + __('Now: %s', this.propertiesHash));
		return true;
	}

	if (this.activitiesHash !== manifest.activitiesHash) {
		this.logger.info(__('Forcing rebuild: Android activites in tiapp.xml changed since last build'));
		this.logger.info('  ' + __('Was: %s', manifest.activitiesHash));
		this.logger.info('  ' + __('Now: %s', this.activitiesHash));
		return true;
	}

	if (this.servicesHash !== manifest.servicesHash) {
		this.logger.info(__('Forcing rebuild: Android services in tiapp.xml SDK changed since last build'));
		this.logger.info('  ' + __('Was: %s', manifest.servicesHash));
		this.logger.info('  ' + __('Now: %s', this.servicesHash));
		return true;
	}

	return false;
};

AndroidBuilder.prototype.checkIfNeedToRecompile = async function checkIfNeedToRecompile() {
	// Determine if we should do a "clean" build.
	this.forceRebuild = this.checkIfShouldForceRebuild();
	if (this.forceRebuild) {
		// On Windows, stop gradle daemon to make it release its file locks so that they can be deleted.
		if (process.platform === 'win32') {
			try {
				const gradlew = new GradleWrapper(this.buildDir);
				gradlew.logger = this.logger;
				if (await gradlew.hasWrapperFiles()) {
					await gradlew.stopDaemon();
				}
			} catch (err) {
				this.logger.error(`Failed to stop gradle daemon. Reason:\n${err}`);
			}
		}

		// Delete all files under the "./build/android" directory.
		await fs.emptyDir(this.buildDir);
		this.unmarkBuildDirFiles(this.buildDir);
	}

	// Delete the "build-manifest.json" in case the build fails and errors out.
	// If the build succeeds, then we'll re-create this file which will later allow an incremental build.
	// But if the file is missing, then the next build will attempt a clean build.
	if (await fs.exists(this.buildManifestFile)) {
		await fs.unlink(this.buildManifestFile);
	}
};

AndroidBuilder.prototype.generateLibProjectForModule = async function generateLibProjectForModule(moduleInfo) {
	// Validate arguments.
	if (!moduleInfo || !moduleInfo.native) {
		return;
	}

	// Create the library project subdirectory, if it doesn't already exist.
	const projectDirName = 'lib.' + moduleInfo.manifest.moduleid;
	const projectDirPath = path.join(this.buildDir, projectDirName);
	this.logger.info(__('Generating gradle project: %s', projectDirName.cyan));
	await fs.ensureDir(projectDirPath);

	// Add the library project's name to our array.
	// This array of names will later be added to the app project's "build.gradle" file as library dependencies.
	if (this.libProjectNames.includes(projectDirName) === false) {
		this.libProjectNames.push(projectDirName);
	}

	// Create the library project's "libs" directory where JAR/AAR libraries go.
	// Delete the directory's files if it already exists.
	const projectLibsDirPath = path.join(projectDirPath, 'libs');
	await fs.emptyDir(projectLibsDirPath);

	// Copy module's main JAR to project's "libs" directory.
	const sourceJarFileName = moduleInfo.manifest.name + '.jar';
	const sourceJarFilePath = path.join(moduleInfo.modulePath, sourceJarFileName);
	afs.copyFileSync(sourceJarFilePath, path.join(projectLibsDirPath, sourceJarFileName), {
		logger: this.logger.debug
	});

	// Copy module's dependency JAR/AAR files to project's "libs" directory.
	const sourceLibDirPath = path.join(moduleInfo.modulePath, 'lib');
	if (await fs.exists(sourceLibDirPath)) {
		afs.copyDirSyncRecursive(sourceLibDirPath, projectLibsDirPath, {
			logger: this.logger.debug,
			preserve: true
		});
	}

	// Delete the library project's "./src/main" subdirectory.
	// Note: Do not delete project's "./build" directory. It contains incremental build info.
	const projectSrcMainDirPath = path.join(projectDirPath, 'src', 'main');
	await fs.emptyDir(projectSrcMainDirPath);

	// Copy module's APK "assets" files, "res" files, and other native Android specific files.
	// Do this by copying its "platform/android" directory tree to library project's "src/main" directory.
	const sourcePlaformAndroidDirPath = path.join(moduleInfo.modulePath, 'platform', 'android');
	if (await fs.exists(sourcePlaformAndroidDirPath)) {
		afs.copyDirSyncRecursive(sourcePlaformAndroidDirPath, projectSrcMainDirPath, {
			logger: this.logger.debug,
			preserve: false
		});
	}

	// Copy module's C/C++ "*.so" libraries to project's "jniLibs" directory.
	// Note: Must be done last since above code deletes the "src/main" directory.
	const sourceJniLibsDirPath = path.join(moduleInfo.modulePath, 'libs');
	if (await fs.exists(sourceJniLibsDirPath)) {
		const projectJniLibsDirPath = path.join(projectDirPath, 'src', 'main', 'jniLibs');
		afs.copyDirSyncRecursive(sourceJniLibsDirPath, projectJniLibsDirPath, {
			logger: this.logger.debug,
			preserve: false
		});
	}

	// If module has "AndroidManifest.xml" file under its "./platform/android" directory,
	// then copy it to library project's "debug" and "release" subdirectories.
	// This makes them extend main "AndroidManifest.xml" under "./src/main" which is taken from "timodule.xml".
	const sourceManifestFilePath = path.join(sourcePlaformAndroidDirPath, 'AndroidManifest.xml');
	if (await fs.exists(sourceManifestFilePath)) {
		// Create the "debug" and "release" subdirectories.
		const debugDirPath = path.join(projectDirPath, 'src', 'debug');
		const releaseDirPath = path.join(projectDirPath, 'src', 'release');
		await fs.ensureDir(debugDirPath);
		await fs.ensureDir(releaseDirPath);

		// Load "AndroidManifest.xml", replace ${tiapp.properties['key']} variables, and save to above directories.
		const manifest = await AndroidManifest.fromFilePath(sourceManifestFilePath);
		manifest.setPackageName(moduleInfo.manifest.moduleid);
		manifest.replaceTiPlaceholdersUsing(this.tiapp, this.appid);
		await manifest.writeToFilePath(path.join(debugDirPath, 'AndroidManifest.xml'));
		await manifest.writeToFilePath(path.join(releaseDirPath, 'AndroidManifest.xml'));
	}

	// Create main "AndroidManifest.xml" file under library project's "./src/main".
	// If manifest settings exist in "timodule.xml", then merge it into main manifest.
	const mainManifest = AndroidManifest.fromXmlString('<manifest/>');
	const tiModuleXmlFilePath = path.join(moduleInfo.modulePath, 'timodule.xml');
	try {
		if (await fs.exists(tiModuleXmlFilePath)) {
			const tiModuleInfo = new tiappxml(tiModuleXmlFilePath);
			if (tiModuleInfo && tiModuleInfo.android && tiModuleInfo.android.manifest) {
				const tiModuleManifest = AndroidManifest.fromXmlString(tiModuleInfo.android.manifest);
				tiModuleManifest.replaceTiPlaceholdersUsing(this.tiapp, this.appid);
				mainManifest.copyFromAndroidManifest(tiModuleManifest);
			}
		}
	} catch (ex) {
		this.logger.error(`Unable to load Android <manifest/> content from: ${tiModuleXmlFilePath}`);
		throw ex;
	}
	mainManifest.setPackageName(moduleInfo.manifest.moduleid);
	await mainManifest.writeToFilePath(path.join(projectSrcMainDirPath, 'AndroidManifest.xml'));

	// Generate a "build.gradle" file for this project from the SDK's "lib.build.gradle" EJS template.
	// Note: Google does not support setting "maxSdkVersion" via gradle script.
	let buildGradleContent = await fs.readFile(path.join(this.templatesDir, 'lib.build.gradle'));
	buildGradleContent = ejs.render(buildGradleContent.toString(), {
		compileSdkVersion: this.compileSdkVersion,
		minSdkVersion: this.minSDK,
		targetSdkVersion: this.targetSDK
	});
	await fs.writeFile(path.join(projectDirPath, 'build.gradle'), buildGradleContent);
};

AndroidBuilder.prototype.processLibraries = async function processLibraries() {
	this.logger.info(__('Processing libraries'));

	// Clear last fetched library information.
	this.libProjectNames = [];
	this.libDependencyStrings = [];
	this.mavenRepositoryUrls = [];

	// Make sure "modules" property is set to a valid value.
	if (!this.modules) {
		this.modules = [];
	}

	// Add a reference to the core Titanium library.
	const tiMavenRepoUrl = 'file://' + path.join(this.platformPath, 'm2repository').replace(/\\/g, '/');
	this.mavenRepositoryUrls.push(encodeURI(tiMavenRepoUrl));
	this.libDependencyStrings.push(`org.appcelerator:titanium:${this.titaniumSdkVersion}`);

	// Process all Titanium modules referenced by the Titanium project.
	for (const nextModule of this.modules) {
		// Skip non-native modules.
		if (!nextModule.native) {
			continue;
		}

		// Check if the module has a maven repository directory.
		// If it does, then we can leverage gradle/maven's depency management system.
		let dependencyString = null;
		const repositoryDirPath = path.join(nextModule.modulePath, 'm2repository');
		if (await fs.exists(repositoryDirPath)) {
			const moduleId = nextModule.manifest.moduleid;
			let index = moduleId.lastIndexOf('.');
			if ((index !== 0) && ((index + 1) < moduleId.length)) {
				if (index > 0) {
					dependencyString = moduleId.substring(0, index);
					dependencyString += ':';
					dependencyString += moduleId.substring(index + 1);
				} else {
					dependencyString = moduleId;
					dependencyString += ':';
					dependencyString += moduleId;
				}
				dependencyString += ':';
				dependencyString += nextModule.manifest.version;
			}
		}

		// Determine how to reference the module in gradle.
		if (repositoryDirPath && dependencyString) {
			// Referenced module has a maven repository.
			// This supports dependency management to avoid library version conflicts.
			const url = 'file://' + repositoryDirPath.replace(/\\/g, '/');
			this.mavenRepositoryUrls.push(encodeURI(url));
			this.libDependencyStrings.push(dependencyString);
		} else {
			// Module directory only contains JARs/AARs. (This is our legacy module distribution.)
			// We must create a gradle library project and copy the module's files to it.
			await this.generateLibProjectForModule(nextModule);
		}
	}
};

AndroidBuilder.prototype.generateRootProjectFiles = async function generateRootProjectFiles() {
	this.logger.info(__('Generating root project files'));

	// Copy our SDK's gradle files to the build directory. (Includes "gradlew" scripts and "gradle" directory tree.)
	// The below install method will also generate a "gradle.properties" file.
	const gradlew = new GradleWrapper(this.buildDir);
	gradlew.logger = this.logger;
	await gradlew.installTemplate(path.join(this.platformPath, 'templates', 'gradle'));

	// Create a "gradle.properties" file. Will add network proxy settings if needed.
	// Note: Enable Jetifier to replace all Google Support library references with AndroidX in all pre-built JARs.
	//       This is needed because using both libraries will cause class name collisions, causing a build failure.
	const gradleProperties = await gradlew.fetchDefaultGradleProperties();
	gradleProperties.push({ key: 'android.useAndroidX', value: 'true' });
	gradleProperties.push({ key: 'android.enableJetifier', value: 'true' });
	gradleProperties.push({ key: 'org.gradle.jvmargs', value: `-Xmx${this.javacMaxMemory}` });
	await gradlew.writeGradlePropertiesFile(gradleProperties);

	// Copy optional "gradle.properties" file contents from Titainum project to the above generated file.
	// These properties must be copied to the end of the file so that they can override Titanium's default properties.
	const customGradlePropertiesFilePath = path.join(this.projectDir, 'platform', 'android', 'gradle.properties');
	if (await fs.exists(customGradlePropertiesFilePath)) {
		const targetGradlePropertiesFilePath = path.join(this.buildDir, 'gradle.properties');
		const fileContent = await fs.readFile(customGradlePropertiesFilePath);
		await fs.appendFile(targetGradlePropertiesFilePath,
			'\n\n'
			+ '# The below was copied from project file: ./platform/android/gradle.properties\n'
			+ fileContent.toString() + '\n');
	}

	// Create a "local.properties" file providing a path to the Android SDK/NDK directories.
	const androidNdkPath = this.androidInfo.ndk ? this.androidInfo.ndk.path : null;
	await gradlew.writeLocalPropertiesFile(this.androidInfo.sdk.path, androidNdkPath);

	// Copy our root "build.gradle" template script to the root build directory.
	await fs.copyFile(
		path.join(this.templatesDir, 'root.build.gradle'),
		path.join(this.buildDir, 'build.gradle'));

	// Copy our Titanium template's gradle constants file.
	// This provides the Google library versions we use and defines our custom "AndroidManifest.xml" placeholders.
	const tiConstantsGradleFileName = 'ti.constants.gradle';
	await fs.copyFile(
		path.join(this.templatesDir, tiConstantsGradleFileName),
		path.join(this.buildDir, tiConstantsGradleFileName));

	// Create a "settings.gradle" file providing all of the gradle projects configured.
	// By default, these project names must match the subdirectory names.
	const fileLines = [
		`rootProject.name = '${this.tiapp.name.replace(/'/g, "\\'")}'`, // eslint-disable-line quotes
		"include ':app'" // eslint-disable-line quotes
	];
	if (this.libProjectNames) {
		for (const projectName of this.libProjectNames) {
			fileLines.push(`include ':${projectName}'`);
		}
	}
	await fs.writeFile(path.join(this.buildDir, 'settings.gradle'), fileLines.join('\n') + '\n');
};

AndroidBuilder.prototype.generateAppProject = async function generateAppProject() {
	this.logger.info(__('Generating gradle project: %s', 'app'.cyan));

	// Create the "app" project directory and its "./src/main" subdirectory tree.
	// Delete all files under its "./src/main" subdirectory if it already exists.
	// Note: Do not delete the "./build" subdirectory. It contains incremental build info.
	await fs.emptyDir(this.buildAppMainDir);
	await fs.ensureDir(this.buildAppMainAssetsResourcesDir);

	// Make sure Titanium's "assets" directory exists. (This is not an APK "assets" directory.)
	// We output transpiled/polyfilled JS files here via copyResources() method.
	// This is a temporary output path for transpiled JS files
	// If we encrypt, we expect the input files to be here and encrypted copies placed in this.buildAppMainAssetsResourcesDir
	// IF we do not encrypt we copy from this path to this.buildAppMainAssetsResourcesDir
	// Note: Do NOT delete this folder. We do our own incremental build handling on it.
	await fs.ensureDir(this.buildAssetsDir);

	// Create a "libs" folder under root build folder.
	// NOTE: This is NOT a standard location to put JAR/AAR files, but node module "appc-cli-titanium"
	//       will put libraries here depending on "tiapp.xml" property settings.
	const rootLibsDirPath = path.join(this.buildDir, 'libs');
	await fs.emptyDir(rootLibsDirPath);
	await fs.ensureDir(rootLibsDirPath);

	// Create a "deploy.json" file if debugging/profiling is enabled.
	const deployJsonFile = path.join(this.buildAppMainAssetsDir, 'deploy.json');
	const deployData = {
		debuggerEnabled: !!this.debugPort,
		debuggerPort: this.debugPort || -1,
		profilerEnabled: !!this.profilerPort,
		profilerPort: this.profilerPort || -1
	};
	if (await fs.exists(deployJsonFile)) {
		await fs.unlink(deployJsonFile);
	}
	if (deployData.debuggerEnabled || deployData.profilerEnabled) {
		await fs.writeFile(deployJsonFile, JSON.stringify(deployData));
	}

	// Copy files from Titanium project's "Resources" directory to the build directory.
	await fs.ensureDir(this.buildAppMainResDrawableDir);
	await this.copyResources();

	// We can do the following in parallel.
	await Promise.all([
		// Generate an "index.json" file referencing every JavaScript file bundled into the app.
		// This is used by the require() function to find the required-in JS files.
		this.generateRequireIndex(),

		// Generate "*.java" source files for application.
		this.generateJavaFiles(),

		// Generate a "res/values" XML file from a Titanium i18n file, if it exists.
		this.generateI18N(),

		// Generate a "res/values" styles XML file if a custom theme was assigned in app's "AndroidManifest.xml".
		this.generateTheme(),

		// Generate "semantic.colors.xml" in "res/values" and "res/values-night"
		this.generateSemanticColors()
	]);

	// Generate an "AndroidManifest.xml" for the app and copy in any custom manifest settings from "tiapp.xml".
	await this.generateAndroidManifest();

	// Generate a "java-sources.txt" file containing paths to all Java source files under our "app" project.
	// Note: Our "appc-cli-titanium" node module uses this to make source code adjustments based on build settings.
	const javaFilePaths = [];
	function fetchJavaFilePathsFrom(directoryPath) {
		for (const fileName of fs.readdirSync(directoryPath)) {
			const filePath = path.join(directoryPath, fileName);
			if (fs.statSync(filePath).isDirectory()) {
				fetchJavaFilePathsFrom(filePath);
			} else if (fileName.toLowerCase().endsWith('.java')) {
				javaFilePaths.push(filePath);
			}
		}
	}
	fetchJavaFilePathsFrom(path.join(this.buildAppMainDir, 'java'));
	await fs.writeFile(
		path.join(this.buildDir, 'java-sources.txt'),
		'"' + javaFilePaths.join('"\n"').replace(/\\/g, '/') + '"');  // Paths must be double quoted and escaped.

	// Emit our legacy "aapt" hook event so that old plugins can copy in additional resources to the project.
	// Note: Our "appc-cli-titanium" node module needs this when property "appc-sourcecode-encryption-policy" is set.
	await new Promise((resolve) => {
		const aaptHook = this.cli.createHook('build.android.aapt', this, function (exe, args, opts, done) {
			done();
		});
		aaptHook('', [], {}, resolve);
	});

	// Emit a "javac" hook event so that plugins can copy in addition Java files or source code changes.
	// Note: Our "appc-cli-titanium" node module requires this event and the "-bootclasspath" argument too.
	await new Promise((resolve) => {
		const javacHook = this.cli.createHook('build.android.javac', this, (exe, args, opts, done) => {
			done();
		});
		javacHook('', [ '-bootclasspath', '' ], {}, resolve);
	});

	// Emit a "dexer" hook event to acquire additional JAR/AAR files from plugins.
	// Note: Our "appc-cli-titanium" node module needs "args" array to have a 2nd element.
	await new Promise((resolve) => {
		const dexerHook = this.cli.createHook('build.android.dexer', this, (exe, args, opts, done) => {
			for (const nextArg of args) {
				try {
					if (fs.existsSync(nextArg) && fs.statSync(nextArg).isFile()) {
						this.libFilePaths.push(nextArg);
					}
				} catch (err) {
					// Ignore.
				}
			}
			done();
		});
		dexerHook('', [ '', '' ], {}, resolve);
	});

	// Acquire the app's version integer code and name to be written to "build.gradle" later.
	let versionCode = '1';
	let versionName = this.tiapp.version ? this.tiapp.version : '1';
	if (this.customAndroidManifest) {
		const versionInfo = this.customAndroidManifest.getAppVersionInfo();
		if (versionInfo) {
			if (versionInfo.versionCode) {
				versionCode = versionInfo.versionCode;
			}
			if (versionInfo.versionName) {
				versionName = versionInfo.versionName;
			}
		}
	}

	// Generate a "build.gradle" file for this project from the SDK's "app.build.gradle" EJS template.
	// Note: Google does not support setting "maxSdkVersion" via gradle script.
	let buildGradleContent = await fs.readFile(path.join(this.templatesDir, 'app.build.gradle'));
	buildGradleContent = ejs.render(buildGradleContent.toString(), {
		applicationId: this.appid,
		compileSdkVersion: this.compileSdkVersion,
		dexJavaMaxHeapSize: this.dxMaxMemory,
		minSdkVersion: this.minSDK,
		targetSdkVersion: this.targetSDK,
		versionCode: versionCode,
		versionName: versionName,
		libFilePaths: this.libFilePaths,
		libProjectNames: this.libProjectNames,
		libDependencyStrings: this.libDependencyStrings,
		mavenRepositoryUrls: this.mavenRepositoryUrls,
		ndkAbiArray: this.abis,
		proguardFilePaths: this.proguardConfigFile ? [ this.proguardConfigFile ] : null,
		tiSdkAndroidDir: this.platformPath
	});
	await fs.writeFile(path.join(this.buildAppDir, 'build.gradle'), buildGradleContent);
};

/**
 * Walks the project resources/assets, module resources/assets and gathers up a categorized listing
 * of files to process. (css, html, js, images, etc)
 * @returns {Promise<Result>}
 */
AndroidBuilder.prototype.gatherResources = async function gatherResources() {
	const gather = require('../../../cli/lib/gather');
	const walker = new gather.Walker({
		ignoreDirs: this.ignoreDirs,
		ignoreFiles: this.ignoreFiles,
	});

	this.logger.info(__('Analyzing Resources directory'));
	const firstWave = await Promise.all([
		walker.walk(path.join(this.titaniumSdkPath, 'common', 'Resources', 'android'), this.buildAppMainAssetsResourcesDir),
		// NOTE: we copy over platform/android as-is without any transform/walk. Should iOS do the same?
		// walker.walk(path.join(this.projectDir, 'platform', 'android'), this.buildAppMainDir),
		walker.walk(path.join(this.projectDir, 'Resources'),           this.buildAppMainAssetsResourcesDir, platformsRegExp),
		walker.walk(path.join(this.projectDir, 'Resources', 'android'), this.buildAppMainAssetsResourcesDir),
	]);
	let combined = gather.mergeMaps(firstWave);

	// node_modules
	this.logger.info(__('Analyzing NPM package files'));
	const moduleCopier = require('../../../cli/lib/module-copier');
	const dirSet = await moduleCopier.gather(this.projectDir);
	const nodeModuleDirs = Array.from(dirSet);
	const secondWave = await Promise.all(nodeModuleDirs.map(async dir => {
		// here dir is the absolute path to the directory
		// That means we need to construct the relative path to append to this.projectDir and this.xcodeAppDir
		const relativePath = dir.substring(this.projectDir.length + 1);
		return walker.walk(dir, path.join(this.buildAppMainAssetsResourcesDir, relativePath), null, null, relativePath);
	}));
	// merge the node_modules results on top of the project results... (shouldn't be any conflicts!)
	secondWave.unshift(combined);
	combined = gather.mergeMaps(secondWave);

	// Fire an event requesting additional "Resources" paths from plugins. (used by hyperloop)
	this.logger.info(__('Analyzing plugin-contributed files'));
	this.htmlJsFiles = {}; // for hyperloop to mark files it doesn't want processed
	const hook = this.cli.createHook('build.android.requestResourcesDirPaths', this, async (paths, done) => {
		try {
			const newTasks = [];
			if (Array.isArray(paths)) {
				for (const nextPath of paths) {
					if (typeof nextPath !== 'string') {
						continue;
					}
					if (!await fs.exists(nextPath) || !(await fs.stat(nextPath)).isDirectory()) {
						continue;
					}
					newTasks.push(
						walker.walk(nextPath, this.buildAppMainAssetsResourcesDir)
					);
				}
			}
			const results = await Promise.all(newTasks);
			done(null, results);
		} catch (err) {
			return done(err);
		}
	});
	const hookResults = await util.promisify(hook)([]);
	// merge the hook results on top of the project/node_modules results... (shouldn't be any conflicts!)
	hookResults.unshift(combined);
	combined = gather.mergeMaps(hookResults);

	this.logger.info(__('Analyzing module files'));
	// detect ambiguous modules
	// this.modules.forEach(module => {
	// 	const filename = `${module.id}.js`;
	// 	if (combined.has(filename)) {
	// 		this.logger.error(__('There is a project resource "%s" that conflicts with a native Android module', filename));
	// 		this.logger.error(__('Please rename the file, then rebuild') + '\n');
	// 		process.exit(1);
	// 	}
	// });
	// do modules in parallel - and for each we need to merge the results together!
	const allModulesResults = await Promise.all(this.modules.map(async module => {
		const tasks = [];
		let assetDest = this.buildAppMainAssetsResourcesDir;
		if (!module.native) {
			// Copy CommonJS non-asset files
			const dest = path.join(this.buildAppMainAssetsResourcesDir, path.basename(module.id));
			// Pass in the relative path prefix we should give because we aren't copying direct to the root here.
			// Otherwise index.js in one module "overwrites" index.js in another (because they're at same relative path inside module)
			tasks.push(walker.walk(module.modulePath, dest, /^(apidoc|docs|documentation|example|assets)$/, null, module.id)); // TODO Consult some .moduleignore file in the module or something? .npmignore?
			// CommonJS assets go to special location
			assetDest = path.join(assetDest, 'modules', module.id.toLowerCase());
		}
		// Create a task which copies "assets" file tree from all modules.
		// Note: Android native module asset handling is inconsistent with commonjs modules and iOS native modules where
		//       we're not copying assets to "modules/moduleId" directory. Continue doing this for backward compatibility.
		tasks.push(walker.walk(path.join(module.modulePath, 'assets'), assetDest));

		// NOTE: Android just copies without any special processing for platform/android. Should iOS?
		// walker.walk(path.join(module.modulePath, 'platform', 'android'), this.buildAppMainDir),

		// Resources
		tasks.push(walker.walk(path.join(module.modulePath, 'Resources'), this.buildAppMainAssetsResourcesDir, platformsRegExp));
		tasks.push(walker.walk(path.join(module.modulePath, 'Resources', 'android'), this.buildAppMainAssetsResourcesDir));
		const moduleResults = await Promise.all(tasks);
		return gather.mergeMaps(moduleResults);
	}));
	//  merge the allModulesResults over top our current combined!
	allModulesResults.unshift(combined);
	combined = gather.mergeMaps(allModulesResults);

	// Ok, so we have a Map<string, FileInfo> for the full set of unique relative paths
	// now categorize (i.e. lump into buckets of js/css/html/assets/generic resources)
	const categorizer = new gather.Categorizer({
		tiappIcon: this.tiapp.icon,
		jsFilesNotToProcess: Object.keys(this.htmlJsFiles),
		platform: 'android',
	});
	return await categorizer.run(combined);
};

/**
 * Optionally mifies the input css files and copies them to the app
 * @param {Map<string,object>} files map from filename to file info
 * @returns {Promise<void>}
 */
AndroidBuilder.prototype.copyCSSFiles = async function copyCSSFiles(files) {
	this.logger.debug(__('Copying CSS files'));
	const task = new ProcessCSSTask({
		files,
		incrementalDirectory: path.join(this.buildTiIncrementalDir, 'process-css'),
		logger: this.logger,
		builder: this,
	});
	return task.run();
};

/**
 * Copies drawable resources into the app
 * @param {Map<string,object>} files map from filename to file info
 * @returns {Promise<void>}
 */
AndroidBuilder.prototype.processDrawableFiles = async function processDrawableFiles(files) {
	this.logger.debug(__('Copying Drawables'));
	const task = new ProcessDrawablesTask({
		files,
		incrementalDirectory: path.join(this.buildTiIncrementalDir, 'process-drawables'),
		logger: this.logger,
		builder: this,
	});
	return task.run();
};

/**
 * Copies splash screen resources into the app
 * @param {Map<string,object>} files map from filename to file info
 * @returns {Promise<void>}
 */
AndroidBuilder.prototype.processSplashesFiles = async function processSplashesFiles(files) {
	this.logger.debug(__('Copying Splash Screens'));
	const task = new ProcessSplashesTask({
		files,
		incrementalDirectory: path.join(this.buildTiIncrementalDir, 'process-splashes'),
		logger: this.logger,
		builder: this,
	});
	return task.run();
};

/**
 * Used to de4termine the destination path for special assets (_app_props_.json, bootstrap.json) based on encyption or not.
 * @returns {string} destination directory to place file
 */
AndroidBuilder.prototype.buildAssetsPath = function buildAssetsPath() {
	return this.encryptJS ? this.buildAssetsDir : this.buildAppMainAssetsResourcesDir;
};

/**
 * Write out file used by Ti.Properties to access properties at runtime
 * This may modify this.jsFilesToEncrypt
 * @returns {Promise<void>}
 */
AndroidBuilder.prototype.writeAppProps = async function writeAppProps() {
	const appPropsFile = path.join(this.buildAssetsPath(), '_app_props_.json');
	const props = {};
	Object.keys(this.tiapp.properties).forEach(prop => {
		props[prop] = this.tiapp.properties[prop].value;
	});
	await fs.writeFile(appPropsFile, JSON.stringify(props));
	this.encryptJS && this.jsFilesToEncrypt.push('_app_props_.json');
	this.unmarkBuildDirFile(appPropsFile);
};

/**
 * Write the env variables file - used by node shim for process.env
 * This may modify this.jsFilesToEncrypt
 * @returns {Promise<void>}
 */
AndroidBuilder.prototype.writeEnvironmentVariables = async function writeEnvironmentVariables() {
	const envVarsFile = path.join(this.buildAssetsPath(), '_env_.json');
	await fs.writeFile(
		envVarsFile,
		// for non-development builds, DO NOT WRITE OUT ENV VARIABLES TO APP
		this.writeEnvVars ? JSON.stringify(process.env) : '{}'
	);
	this.encryptJS && this.jsFilesToEncrypt.push('_env_.json');
	this.unmarkBuildDirFile(envVarsFile);
};

/**
 * This may modify this.jsFilesToEncrypt
 * @param {Map<string, object>} jsFilesMap map from filename to file info
 * @returns {Promise<void>}
 */
AndroidBuilder.prototype.processJSFiles = async function processJSFiles(jsFilesMap) {
	// do the processing
	this.logger.info(__('Processing JavaScript files'));
	const sdkCommonFolder = path.join(this.titaniumSdkPath, 'common', 'Resources', 'android');
	// For now, need to adapt our Map results to String[] and Object for ProcessJsTask
	// Note that because we wipe build/android/app/src/main every build, we have to use a middleman
	// directory to hold processed files for incremental builds!
	// FIXME: Can we avoid emptying the directory each time?! see generateAppProject()
	const jsFiles = {};
	const inputFiles = [];
	for (let [ key, value ] of jsFilesMap) {
		jsFiles[key] = {
			src: value.src,
			dest: path.join(this.buildAssetsDir, key) // hijack destination to point to build/assets
		};
		inputFiles.push(value.src);
	}

	const jsBootstrapFiles = []; // modified by the task and then used after the fact to write our bootstrap.json file
	const task = new ProcessJsTask({
		inputFiles,
		incrementalDirectory: path.join(this.buildTiIncrementalDir, 'process-js'),
		logger: this.logger,
		builder: this,
		jsFiles,
		jsBootstrapFiles,
		sdkCommonFolder,
		defaultAnalyzeOptions: {
			minify: this.minifyJS,
			transpile: this.transpile,
			sourceMap: this.sourceMaps,
			resourcesDir: this.buildAssetsDir,
			logger: this.logger,
			targets: {
				chrome: this.chromeVersion
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
		this.tiSymbols = task.data.tiSymbols;  // record API usage for analytics
	}

	// Copy all unencrypted files processed by ProcessJsTask to "app" project's APK "assets" directory.
	// Note: For encrypted builds, our encryptJSFiles() method will write encrypted JS files to the app project.
	if (!this.encryptJS) {
		// Now we need to copy the processed JS files from build/assets to build/app/src/main/assets/Resources
		const resourcesToCopy = new Map();
		for (let [ key, value ] of jsFilesMap) {
			resourcesToCopy.set(key, {
				src: path.join(this.buildAssetsDir, key),
				dest: value.dest
			});
			this.unmarkBuildDirFile(value.dest);
		}
		const copyTask = new CopyResourcesTask({
			incrementalDirectory: path.join(this.buildTiIncrementalDir, 'copy-processed-js'),
			name: 'copy-processed-js',
			logger: this.logger,
			builder: this,
			files: resourcesToCopy
		});
		await copyTask.run();
	}

	// then write the bootstrap json
	return this.writeBootstrapJson(jsBootstrapFiles);
};

/**
 * @param {string[]} jsBootstrapFiles list of bootstrap js files to add to listing we generate
 * @returns {Promise<void>}
 */
AndroidBuilder.prototype.writeBootstrapJson = async function writeBootstrapJson(jsBootstrapFiles) {
	this.logger.info(__('Writing bootstrap json'));
	// Write the "bootstrap.json" file, even if the bootstrap array is empty.
	// Note: An empty array indicates the app has no bootstrap files.
	const bootstrapJsonRelativePath = path.join('ti.internal', 'bootstrap.json');
	const bootstrapJsonAbsolutePath = path.join(this.buildAssetsPath(), bootstrapJsonRelativePath);
	await fs.ensureDir(path.dirname(bootstrapJsonAbsolutePath));
	await fs.writeFile(bootstrapJsonAbsolutePath, JSON.stringify({ scripts: jsBootstrapFiles }));
	this.encryptJS && this.jsFilesToEncrypt.push(bootstrapJsonRelativePath);
	this.unmarkBuildDirFile(bootstrapJsonAbsolutePath);
};

/**
 * Copy "./platform/android" directory tree from all modules and main project to "app" project's "./src/main".
 * Android build tools auto-grabs folders named "assets", "res", "aidl", etc. from this folder.
 * Note 1: Our "build.gradle" is configured to look for JAR/AAR files here too. (Needed by hyperloop.)
 * Note 2: Main Titanium project's folder must be copied last, allowing it to replace asset or res files.
 * @returns {Promise<void>}
 */
AndroidBuilder.prototype.copyPlatformDirs = async function copyPlatformDirs() {
	const platformDirPaths = [];
	for (const module of this.modules) {
		if (!module.native) {
			platformDirPaths.push(path.join(module.modulePath, 'platform', 'android'));
		}
	}
	const googleServicesFile = path.join(this.projectDir, 'platform', 'android', 'google-services.json');
	if (await fs.exists(googleServicesFile)) {
		afs.copyFileSync(googleServicesFile, path.join(this.buildAppDir, 'google-services.json'), {
			logger: this.logger.debug,
			preserve: true
		});
	}
	platformDirPaths.push(path.join(this.projectDir, 'platform', 'android'));
	for (const nextPath of platformDirPaths) {
		if (await fs.exists(nextPath)) {
			afs.copyDirSyncRecursive(nextPath, this.buildAppMainDir, {
				logger: this.logger.debug,
				preserve: true
			});
		}
	}
};

AndroidBuilder.prototype.copyResources = async function copyResources() {
	// First walk all the input dirs and gather/categorize the files into buckets
	const gatheredResults = await this.gatherResources();
	this.jsFilesToEncrypt = []; // set listing of files to encrypt to empty array (may be modified by tasks below)

	// ok we now have them organized into broad categories
	// we can schedule tasks to happen in parallel:
	await Promise.all([
		this.copyCSSFiles(gatheredResults.cssFiles),
		this.processJSFiles(gatheredResults.jsFiles),
		this.processDrawableFiles(gatheredResults.imageAssets),
		this.processSplashesFiles(gatheredResults.launchImages),
		this.writeAppProps(), // writes _app_props_.json for Ti.Properties
		this.writeEnvironmentVariables(), // writes _env_.json for process.env
		this.copyPlatformDirs(), // copies platform/android dirs from project/modules
		this.copyUnmodifiedResources(gatheredResults.resourcesToCopy), // copies any other files that don't require special handling (like JS/CSS do)
	]);

	// Then do the rest of the shit...
	const templateDir = path.join(this.platformPath, 'templates', 'app', 'default', 'template', 'Resources', 'android');
	return Promise.all([
		this.encryptJSFiles(),
		this.ensureAppIcon(templateDir),
		this.ensureSplashScreen(templateDir),
	]);
};

/**
 * Copies all the rest of the files that need no extra processing.
 * @param {Map<string, object>} resourcesToCopy filepaths to file info
 * @returns {Promise<void>}
 */
AndroidBuilder.prototype.copyUnmodifiedResources = async function copyUnmodifiedResources(resourcesToCopy) {
	this.logger.debug(__('Copying resources'));
	const task = new CopyResourcesTask({
		incrementalDirectory: path.join(this.buildTiIncrementalDir, 'copy-resources'),
		logger: this.logger,
		builder: this,
		files: resourcesToCopy
	});
	return task.run();
};

/**
 * Ensures the generated app has a splash screen image
 * @param {string} templateDir the filepath to the Titanium SDK's app template for Android apps
 */
AndroidBuilder.prototype.ensureSplashScreen = async function ensureSplashScreen(templateDir) {
	// make sure we have a splash screen
	const backgroundRegExp = /^background(\.9)?\.(png|jpg)$/;
	const destBg = path.join(this.buildAppMainResDrawableDir, 'background.png');
	const nodpiDir = path.join(this.buildAppMainResDir, 'drawable-nodpi');
	if (!(await fs.readdir(this.buildAppMainResDrawableDir)).some(name => {
		if (backgroundRegExp.test(name)) {
			this.unmarkBuildDirFile(path.join(this.buildAppMainResDrawableDir, name));
			return true;
		}
		return false;
	}, this)) {
		// no background image in drawable, but what about drawable-nodpi?
		if (!(await fs.exists(nodpiDir)) || !(await fs.readdir(nodpiDir)).some(name => {
			if (backgroundRegExp.test(name)) {
				this.unmarkBuildDirFile(path.join(nodpiDir, name));
				return true;
			}
			return false;
		}, this)) {
			this.unmarkBuildDirFile(destBg);
			this.copyFileSync(path.join(templateDir, 'default.png'), destBg);
		}
	}
};

/**
 * Ensures the generated app has an app icon
 * @param {string} templateDir the filepath to the Titanium SDK's app template for Android apps
 */
AndroidBuilder.prototype.ensureAppIcon = async function ensureAppIcon(templateDir) {
	const srcIcon = path.join(templateDir, 'appicon.png');
	const destIcon = path.join(this.buildAppMainAssetsResourcesDir, this.tiapp.icon);

	// if an app icon hasn't been copied, copy the default one from our app template
	if (!(await fs.exists(destIcon))) {
		this.copyFileSync(srcIcon, destIcon); // TODO: Use async call!
	}
	this.unmarkBuildDirFile(destIcon);

	const destIcon2 = path.join(this.buildAppMainResDrawableDir, this.tiapp.icon);
	if (!(await fs.exists(destIcon2))) {
		// Note, we are explicitly copying destIcon here as we want to ensure that we're
		// copying the user specified icon, srcIcon is the default Titanium icon
		this.copyFileSync(destIcon, destIcon2); // TODO: Use async call!
	}
	this.unmarkBuildDirFile(destIcon2);
};

/**
 * @returns {Promise<void>}
 */
AndroidBuilder.prototype.encryptJSFiles = async function encryptJSFiles() {
	if (!this.jsFilesToEncrypt.length) {
		// nothing to encrypt, continue
		return;
	}

	const Cloak = require('ti.cloak').default;
	const cloak = this.encryptJS ? new Cloak() : null;
	if (!cloak) {
		throw new Error('Could not load encryption library!');
	}

	this.logger.info('Encrypting javascript assets...');

	// NOTE: maintain 'build.android.titaniumprep' hook for remote encryption policy.
	const hook = this.cli.createHook('build.android.titaniumprep', this, async function (exe, args, opts, next) {
		try {
			await Promise.all(
				this.jsFilesToEncrypt.map(async file => {
					const from = path.join(this.buildAssetsDir, file);
					const to = path.join(this.buildAppMainAssetsResourcesDir, file + '.bin');

					this.logger.debug(__('Encrypting: %s', from.cyan));
					await fs.ensureDir(path.dirname(to));
					this.unmarkBuildDirFile(to);
					return await cloak.encryptFile(from, to);
				})
			);

			this.logger.info('Writing encryption key...');
			await cloak.setKey('android', this.abis, path.join(this.buildAppMainDir, 'jniLibs'));

			// Generate 'AssetCryptImpl.java' from template.
			const assetCryptDest = path.join(this.buildGenAppIdDir, 'AssetCryptImpl.java');
			this.unmarkBuildDirFile(assetCryptDest);
			await fs.ensureDir(this.buildGenAppIdDir);
			await fs.writeFile(
				assetCryptDest,
				ejs.render(
					await fs.readFile(path.join(this.templatesDir, 'AssetCryptImpl.java'), 'utf8'),
					{
						appid: this.appid,
						assets: this.jsFilesToEncrypt.map(f => f.replace(/\\/g, '/')),
						salt: cloak.salt
					}
				)
			);

			next();
		} catch (e) {
			next(new Error('Could not encrypt assets!\n' + e));
		}
	});
	return util.promisify(hook)(null, [ this.tiapp.guid, '' ], {});
};

AndroidBuilder.prototype.generateRequireIndex = async function generateRequireIndex() {
	this.logger.info('Generating import/require index file');

	// Fetch relative paths to all of the app's *.js and *.json files.
	const filePathDictionary = {};
	const normalizedAssetsDir = this.buildAppMainAssetsDir.replace(/\\/g, '/');
	const walkDir = async (directoryPath) => {
		const fileNameArray = await fs.readdir(directoryPath);
		for (const fileName of fileNameArray) {
			const filePath = path.join(directoryPath, fileName);
			const stat = await fs.stat(filePath);
			if (stat.isDirectory()) {
				await walkDir(filePath);
			} else if (stat.isFile()) {
				const lowerCaseFileName = fileName.toLowerCase();
				// TODO: Support mjs files!
				if (lowerCaseFileName.endsWith('.js') || lowerCaseFileName.endsWith('.json') || lowerCaseFileName.endsWith('.cjs')) {
					let normalizedFilePath = filePath.replace(/\\/g, '/');
					normalizedFilePath = normalizedFilePath.replace(normalizedAssetsDir + '/', '');
					filePathDictionary[normalizedFilePath] = 1;
				}
			}
		}
	};
	await walkDir(this.buildAppMainAssetsResourcesDir);
	for (const filePath of this.jsFilesToEncrypt) {
		filePathDictionary['Resources/' + filePath.replace(/\\/g, '/')] = 1;
	}
	delete filePathDictionary['Resources/_app_props_.json'];

	// Create the "index.json" file. This is used by our require/import function to load these files.
	const indexJsonFilePath = path.join(normalizedAssetsDir, 'index.json');
	if (await fs.exists(indexJsonFilePath)) {
		await fs.unlink(indexJsonFilePath);
	}
	await fs.writeFile(indexJsonFilePath, JSON.stringify(filePathDictionary));

	// Fetch JavaScript files that should be pre-loaded by the app before required/imported in.
	// Always pre-load "app.js" and Alloy generated *.js files. Allows for faster app startup time.
	const cacheAssets = [ 'Resources/app.js' ];
	const assets = Object.keys(filePathDictionary);
	if (assets.includes('Resources/alloy.js')) {
		for (let asset of assets) {
			if (asset.startsWith('Resources/alloy')) {
				cacheAssets.push(asset);
			}
		}
	}

	// Create the "cache.json" file.
	const cacheJsonFilePath = path.join(this.buildAppMainAssetsDir, 'cache.json');
	await fs.writeFile(cacheJsonFilePath, JSON.stringify(cacheAssets));
};

/**
 * @param {string} jarFile filepath to JAR
 * @returns {Promise<Object>} parsed JSON of the module's bindings
 */
AndroidBuilder.prototype.getNativeModuleBindings = async function getNativeModuleBindings(jarFile) {
	return new Promise((resolve, reject) => {
		const yauzl = require('yauzl');
		yauzl.open(jarFile, { lazyEntries: true }, (err, zipfile) => {
			if (err) {
				return reject(err);
			}

			zipfile.once('error', reject);
			zipfile.on('entry', entry => {
				if (!entry.fileName.startsWith('org/appcelerator/titanium/bindings/')) {
					zipfile.readEntry(); // move on
					return;
				}
				// read the entry
				zipfile.openReadStream(entry, function (err, readStream) {
					if (err) {
						return reject(err);
					}

					// read file contents and when done, parse as JSON
					const chunks = [];
					readStream.once('error', reject);
					readStream.on('data', chunk => chunks.push(chunk));
					readStream.on('end', () => {
						try {
							zipfile.close();
							const str = Buffer.concat(chunks).toString('utf8');
							return resolve(JSON.parse(str));
						} catch (error) {
							reject(error);
						}
					});
				});
			});
			zipfile.readEntry();
		});
	});
};

AndroidBuilder.prototype.generateJavaFiles = async function generateJavaFiles() {
	this.logger.info('Generating Java files');

	const copyTemplate = async (src, dest, ejsParams) => {
		this.logger.debug(__('Copying template %s => %s', src.cyan, dest.cyan));
		let fileContent = await fs.readFile(src);
		fileContent = ejs.render(fileContent.toString(), ejsParams);
		await fs.writeFile(dest, fileContent);
	};

	// Fetch Java proxy class information from all modules.
	// Needed so they can be required-in via JavaScript and to enable onAppCreate() method support on app startup.
	const moduleProxyArray = [];
	for (const module of this.modules) {
		// Skip commonjs modules.
		if (!module.native) {
			continue;
		}

		// Attempt to read the module's Java bindings JSON file.
		let javaBindings = null;
		const moduleName = module.manifest.name;
		{
			// Check if a "<module.name>.json" file exists in the module's root directory.
			const jsonFilePath = path.join(module.modulePath, moduleName + '.json');
			try {
				if (await fs.exists(jsonFilePath)) {
					const fileContent = await fs.readFile(jsonFilePath);
					if (fileContent) {
						javaBindings = JSON.parse(fileContent);
					} else {
						this.logger.error(__n('Failed to read module "%s" file "%s"', module.id, jsonFilePath));
					}
				}
			} catch (ex) {
				this.logger.error(__n(
					'Error accessing module "%s" file "%s". Reason: %s', module.id, jsonFilePath, ex.message));
			}
		}
		if (!javaBindings) {
			// Check if a JSON file is embedded within the module's main JAR file.
			const jarFilePath = path.join(module.modulePath, moduleName + '.jar');
			try {
				if (await fs.exists(jarFilePath)) {
					javaBindings = await this.getNativeModuleBindings(jarFilePath);
				}
			} catch (ex) {
				this.logger.error(__n('The module "%s" has an invalid jar file: %s', module.id, jarFilePath));
			}
		}
		if (!javaBindings || !javaBindings.modules || !javaBindings.proxies) {
			continue;
		}

		// Add the module's main Java proxy class info to our "moduleProxyArray" object.
		for (const moduleClass in javaBindings.modules) {
			// Skip proxy classes not named after the module.
			const proxy = javaBindings.proxies[moduleClass];
			if (!proxy || !proxy.proxyAttrs || (proxy.proxyAttrs.id !== module.manifest.moduleid)) {
				continue;
			}

			// Add the module's proxy info to array.
			moduleProxyArray.push({
				apiName: javaBindings.modules[moduleClass].apiName,
				proxyName: proxy.proxyClassName,
				className: moduleClass,
				manifest: module.manifest,
				onAppCreate: proxy.onAppCreate || proxy['on_app_create'] || null,
				isNativeJsModule: !!module.manifest.commonjs
			});
		}
	}

	// Copy main application Java classes.
	await fs.ensureDir(this.buildGenAppIdDir);
	await copyTemplate(
		path.join(this.templatesDir, 'AppInfo.java'),
		path.join(this.buildGenAppIdDir, `${this.classname}AppInfo.java`),
		{
			appid: this.appid,
			buildType: this.buildType,
			classname: this.classname,
			deployType: this.deployType,
			tiapp: this.tiapp
		});
	await copyTemplate(
		path.join(this.templatesDir, 'App.java'),
		path.join(this.buildGenAppIdDir, `${this.classname}Application.java`),
		{
			appid: this.appid,
			classname: this.classname,
			customModules: moduleProxyArray,
			deployType: this.deployType,
			encryptJS: this.encryptJS
		});
	await copyTemplate(
		path.join(this.templatesDir, 'Activity.java'),
		path.join(this.buildGenAppIdDir, `${this.classname}Activity.java`),
		{
			appid: this.appid,
			classname: this.classname
		});

	// Copy git-ignore file.
	afs.copyFileSync(
		path.join(this.templatesDir, 'gitignore'),
		path.join(this.buildDir, '.gitignore'),
		{ logger: this.logger.debug });

	// Generate the JavaScript-based activity classes.
	const android = this.tiapp.android;
	if (android && android.activities) {
		const activityTemplate = (await fs.readFile(path.join(this.templatesDir, 'JSActivity.java'))).toString();
		for (const activityName in android.activities) {
			const activity = android.activities[activityName];
			this.logger.debug(__('Generating activity class: %s', activity.classname.cyan));
			const fileContent = ejs.render(activityTemplate, {
				appid: this.appid,
				activity: activity
			});
			await fs.writeFile(path.join(this.buildGenAppIdDir, `${activity.classname}.java`), fileContent);
		}
	}

	// Generate the JavaScript-based Service classes.
	if (android && android.services) {
		const serviceTemplate = (await fs.readFile(path.join(this.templatesDir, 'JSService.java'))).toString();
		const intervalServiceTemplate = (await fs.readFile(path.join(this.templatesDir, 'JSIntervalService.java'))).toString();
		const quickSettingsServiceTemplate = (await fs.readFile(path.join(this.templatesDir, 'JSQuickSettingsService.java'))).toString();
		for (const serviceName in android.services) {
			const service = android.services[serviceName];
			let tpl = serviceTemplate;
			if (service.type === 'interval') {
				tpl = intervalServiceTemplate;
				this.logger.debug(__('Generating interval service class: %s', service.classname.cyan));
			} else if (service.type === 'quicksettings') {
				tpl = quickSettingsServiceTemplate;
				this.logger.debug(__('Generating quick settings service class: %s', service.classname.cyan));
			} else {
				this.logger.debug(__('Generating service class: %s', service.classname.cyan));
			}
			const fileContent = ejs.render(tpl, {
				appid: this.appid,
				service: service
			});
			await fs.writeFile(path.join(this.buildGenAppIdDir, `${service.classname}.java`), fileContent);
		}
	}
};

AndroidBuilder.prototype.generateI18N = async function generateI18N() {
	this.logger.info(__('Generating i18n files'));

	const badStringNames = {};
	const data = i18n.load(this.projectDir, this.logger, {
		ignoreDirs: this.ignoreDirs,
		ignoreFiles: this.ignoreFiles
	});
	if (!data.en) {
		data.en = data['en-US'] || {};
	}
	if (!data.en.app) {
		data.en.app = {};
	}
	if (!data.en.app.appname) {
		data.en.app.appname = this.tiapp.name;
	}

	function replaceSpaces(s) {
		return s.replace(/./g, '\\u0020');
	}

	function resolveRegionName(locale) {
		if (locale.match(/\w{2}(-|_)r?\w{2}/)) {
			const parts = locale.split(/-|_/),
				lang = parts[0],
				region = parts[1];
			let separator = '-';

			if (region.length === 2) {
				separator = '-r';
			}

			return lang + separator + region;
		}
		return locale;
	}

	// Traverse all loaded i18n locales and write them to XML files under the Android "res" folder.
	for (const locale in data) {
		// Create a localized strings dictionary if no i18n "strings.xml" file was found.
		const localeData = data[locale];
		if (!localeData.strings) {
			localeData.strings = {};
		}

		// Add localized app name to strings dictionary under the "app_name" key:
		// 1) If not already defined in i18n "strings.xml" file. (This is undocumented, but some devs do this.)
		// 2) If defined in i18n "app.xml". (The preferred cross-platform way to localize it.)
		// 3) Default to "tiapp.xml" file's <name/> if not defined under i18n. (Not localized.)
		let appName = localeData.strings.app_name;
		if (!appName) {
			appName = localeData.app && localeData.app.appname;
			if (!appName) {
				appName = this.tiapp.name;
			}
			localeData.strings.app_name = appName;
		}

		// Create the XML content for all localized strings.
		const dom = new DOMParser().parseFromString('<resources/>', 'text/xml');
		const root = dom.documentElement;
		for (const name in localeData.strings) {
			if (name.indexOf(' ') !== -1) {
				badStringNames[locale] || (badStringNames[locale] = []);
				badStringNames[locale].push(name);
			} else {
				const node = dom.createElement('string');
				node.setAttribute('name', name);
				node.setAttribute('formatted', 'false');
				node.appendChild(dom.createTextNode(localeData.strings[name].replace(/\\?'/g, '\\\'').replace(/^\s+/g, replaceSpaces).replace(/\s+$/g, replaceSpaces)));
				root.appendChild(dom.createTextNode('\n\t'));
				root.appendChild(node);
			}
		}
		root.appendChild(dom.createTextNode('\n'));

		// Create the XML file under the Android "res/values-<locale>" folder.
		const localeSuffixName = (locale === 'en' ? '' : '-' + resolveRegionName(locale));
		const dirPath = path.join(this.buildAppMainResDir, `values${localeSuffixName}`);
		const filePath = path.join(dirPath, 'ti_i18n_strings.xml');
		this.logger.debug(__('Writing %s strings => %s', locale.cyan, filePath.cyan));
		await fs.ensureDir(dirPath);
		await fs.writeFile(filePath, '<?xml version="1.0" encoding="UTF-8"?>\n' + dom.documentElement.toString());
	}

	if (Object.keys(badStringNames).length) {
		this.logger.error(__('Found invalid i18n string names:'));
		Object.keys(badStringNames).forEach(function (locale) {
			badStringNames[locale].forEach(function (s) {
				this.logger.error('  "' + s + '" (' + locale + ')');
			}, this);
		}, this);
		this.logger.error(__('Android does not allow i18n string names with spaces.'));
		if (!this.config.get('android.excludeInvalidI18nStrings', false)) {
			this.logger.error(__('To exclude invalid i18n strings from the build, run:'));
			this.logger.error('    ' + this.cli.argv.$ + ' config android.excludeInvalidI18nStrings true');
			this.logger.log();
			process.exit(1);
		}
	}
};

AndroidBuilder.prototype.generateSemanticColors = async function generateSemanticColors() {
	this.logger.info(__('Generating semantic colors resources'));
	const _t = this;
	const xmlFileName = 'ti.semantic.colors.xml';
	const valuesDirPath = path.join(this.buildAppMainResDir, 'values');
	const valuesNightDirPath = path.join(this.buildAppMainResDir, 'values-night');
	await fs.ensureDir(valuesDirPath);
	await fs.ensureDir(valuesNightDirPath);
	const destLight = path.join(valuesDirPath, xmlFileName);
	const destNight = path.join(valuesNightDirPath, xmlFileName);

	let colorsFile = path.join(this.projectDir, 'Resources', 'android', 'semantic.colors.json');

	if (!fs.existsSync(colorsFile)) {
		// Fallback to root of Resources folder for Classic applications
		colorsFile = path.join(this.projectDir, 'Resources', 'semantic.colors.json');
	}

	if (!fs.existsSync(colorsFile)) {
		this.logger.debug(__('Skipping colorset generation as "semantic.colors.json" file does not exist'));
		return;
	}

	function appendToXml(dom, root, color, colorValue) {
		const appnameNode = dom.createElement('color');
		appnameNode.setAttribute('name', `${color}`);
		const colorObj = Color.fromSemanticColorsEntry(colorValue);
		appnameNode.appendChild(dom.createTextNode(colorObj.toARGBHexString()));
		root.appendChild(dom.createTextNode('\n\t'));
		root.appendChild(appnameNode);
	}

	function writeXml(dom, dest, mode) {
		if (fs.existsSync(dest)) {
			_t.logger.debug(__('Merging %s semantic colors => %s', mode.cyan, dest.cyan));
		} else {
			_t.logger.debug(__('Writing %s semantic colors => %s', mode.cyan, dest.cyan));
		}
		return fs.writeFile(dest, '<?xml version="1.0" encoding="UTF-8"?>\n' + dom.documentElement.toString());
	}

	const colors = fs.readJSONSync(colorsFile);
	const domLight = new DOMParser().parseFromString('<resources/>', 'text/xml');
	const domNight = new DOMParser().parseFromString('<resources/>', 'text/xml');

	const rootLight = domLight.documentElement;
	const rootNight = domNight.documentElement;

	for (const [ color, colorValue ] of Object.entries(colors)) {
		if (!colorValue.light) {
			this.logger.warn(`Skipping ${color} as it does not include a light value`);
			continue;
		}

		if (!colorValue.dark) {
			this.logger.warn(`Skipping ${color} as it does not include a dark value`);
			continue;
		}

		appendToXml(domLight, rootLight, color, colorValue.light);
		appendToXml(domNight, rootNight, color, colorValue.dark);
	}

	return Promise.all([
		writeXml(domLight, destLight, 'light'),
		writeXml(domNight, destNight, 'night')
	]);
};

AndroidBuilder.prototype.generateTheme = async function generateTheme() {
	// Log the theme XML file we're about to generate.
	const valuesDirPath = path.join(this.buildAppMainResDir, 'values');
	const xmlFilePath = path.join(valuesDirPath, 'ti_styles.xml');
	this.logger.info(__('Generating theme file: %s', xmlFilePath.cyan));

	// Set default theme to be used in "AndroidManifest.xml" and style resources.
	let defaultAppThemeName = 'Theme.Titanium.DayNight';
	if (this.tiapp.fullscreen || this.tiapp['statusbar-hidden']) {
		defaultAppThemeName = 'Theme.Titanium.DayNight.Fullscreen';
	} else if (this.tiapp['navbar-hidden']) {
		defaultAppThemeName = 'Theme.Titanium.DayNight.NoTitleBar';
	}

	// Set up "Theme.AppDerived" to use the <application/> defined theme, if assigned.
	let actualAppTheme = 'Theme.Titanium.App';
	if (this.customAndroidManifest) {
		const appTheme = this.customAndroidManifest.getAppAttribute('android:theme');
		if (appTheme && !appTheme.startsWith('@style/Theme.AppDerived') && (appTheme !== '@style/Theme.Titanium')) {
			actualAppTheme = appTheme;
		}
	}

	// Create the theme XML file with above activity style.
	// Also apply app's background image to root splash activity theme.
	let xmlLines = [
		'<?xml version="1.0" encoding="utf-8"?>',
		'<resources>',
		`	<style name="Theme.Titanium.App" parent="${defaultAppThemeName}"/>`,
		`	<style name="Theme.AppDerived" parent="${actualAppTheme}"/>`,
		'',
		'	<!-- Theme used by "TiRootActivity" derived class which displays the splash screen. -->',
		'	<style name="Theme.Titanium" parent="Base.Theme.Titanium.Splash">',
		'		<item name="android:windowBackground">@drawable/background</item>',
		'	</style>',
		'</resources>'
	];
	await fs.ensureDir(valuesDirPath);
	await fs.writeFile(xmlFilePath, xmlLines.join('\n'));
};

AndroidBuilder.prototype.fetchNeededManifestSettings = function fetchNeededManifestSettings() {
	// Check if permission injection is disabled in "tiapp.xml".
	// Note: Recommended solution is to use 'tools:node="remove"' attributes within <manifest/> instead.
	const canAddPermissions = !this.tiapp['override-permissions'];

	// Define Android <uses-permission/> names needed by our core Titanium APIs.
	const calendarPermissions = [ 'android.permission.READ_CALENDAR', 'android.permission.WRITE_CALENDAR' ];
	const cameraPermissions = [ 'android.permission.CAMERA', 'android.permission.WRITE_EXTERNAL_STORAGE' ];
	const contactsPermissions = [ 'android.permission.READ_CONTACTS', 'android.permission.WRITE_CONTACTS' ];
	const contactsReadPermissions = [ 'android.permission.READ_CONTACTS' ];
	const geoPermissions = [ 'android.permission.ACCESS_COARSE_LOCATION', 'android.permission.ACCESS_FINE_LOCATION' ];
	const storagePermissions = [ 'android.permission.WRITE_EXTERNAL_STORAGE' ];
	const vibratePermissions = [ 'android.permission.VIBRATE' ];
	const wallpaperPermissions = [ 'android.permission.SET_WALLPAPER' ];

	// Define namespaces that need permissions when accessed in JavaScript.
	const tiNamespacePermissions = {
		Geolocation: geoPermissions
	};

	// Define methods that need permissions when invoked in JavaScript.
	const tiMethodPermissions = {
		'Calendar.getAllAlerts': calendarPermissions,
		'Calendar.getAllCalendars': calendarPermissions,
		'Calendar.getCalendarById': calendarPermissions,
		'Calendar.getSelectableCalendars': calendarPermissions,

		'Contacts.createPerson': contactsPermissions,
		'Contacts.removePerson': contactsPermissions,
		'Contacts.getAllContacts': contactsReadPermissions,
		'Contacts.showContactPicker': contactsReadPermissions,
		'Contacts.showContacts': contactsReadPermissions,
		'Contacts.getPeopleWithName': contactsReadPermissions,
		'Contacts.getAllPeople': contactsReadPermissions,
		'Contacts.getAllGroups': contactsReadPermissions,

		'Filesystem.requestStoragePermissions': storagePermissions,

		'Media.Android.setSystemWallpaper': wallpaperPermissions,
		'Media.saveToPhotoGallery': storagePermissions,
		'Media.showCamera': cameraPermissions,
		'Media.vibrate': vibratePermissions,
	};

	// Add Titanium's default permissions.
	// Note: You would normally define needed permissions in AAR library's manifest file,
	//       but we want "tiapp.xml" property "override-permissions" to be able to override this behavior.
	const neededPermissionDictionary = {};
	if (canAddPermissions) {
		neededPermissionDictionary['android.permission.INTERNET'] = true;
		neededPermissionDictionary['android.permission.ACCESS_WIFI_STATE'] = true;
		neededPermissionDictionary['android.permission.ACCESS_NETWORK_STATE'] = true;
	}

	// Set the max API Level the "WRITE_EXTERNAL_STORAGE" permission should use.
	// Android 10 and higher doesn't need this permission unless requestStoragePermissions() method is used.
	let storagePermissionMaxSdkVersion = 28;

	// Define JavaScript methods that need manifest <queries> entries.
	// The value strings are used as boolean property names in our "AndroidManifest.xml" EJS template.
	const tiMethodQueries = {
		'UI.createEmailDialog': 'sendEmail',
		'UI.EmailDialog': 'sendEmail'
	};

	// To be populated with <queries/> needed by the app.
	// Uses the string values from "tiMethodQueries" as keys.
	const neededQueriesDictionary = {};

	// Make sure Titanium symbols variable "tiSymbols" is valid.
	if (!this.tiSymbols) {
		this.tiSymbols = {};
	}

	// Traverse all accessed namespaces/methods in JavaScript.
	// Add any Android permissions/queries needed if matching the above mappings.
	const accessedSymbols = {};
	for (const file in this.tiSymbols) {
		// Fetch all symbols from the next JavaScript file.
		const symbolArray = this.tiSymbols[file];
		if (!symbolArray) {
			continue;
		}

		// Traverse all of JavaScript symbols.
		for (const symbol of symbolArray) {
			// Do not continue if we've already evaluated this symbol before.
			if (!symbol || accessedSymbols[symbol]) {
				continue;
			}
			accessedSymbols[symbol] = true;

			// Check if symbol requires any Android permissions.
			if (canAddPermissions) {
				let permissionArray;

				// If symbol is a namespace, then check if it needs permission.
				// Note: Check each namespace component separately, split via periods.
				const namespaceParts = symbol.split('.').slice(0, -1);
				for (;namespaceParts.length > 0; namespaceParts.pop()) {
					const namespace = namespaceParts.join('.');
					if (namespace) {
						permissionArray = tiNamespacePermissions[namespace];
						if (permissionArray) { // eslint-disable-line max-depth
							for (const permission of permissionArray) { // eslint-disable-line max-depth
								neededPermissionDictionary[permission] = true;
							}
						}
					}
				}

				// If symbol is a method, then check if it needs permission.
				permissionArray = tiMethodPermissions[symbol];
				if (permissionArray) {
					for (const permission of permissionArray) {
						neededPermissionDictionary[permission] = true;
					}
					if (symbol === 'Filesystem.requestStoragePermissions') {
						storagePermissionMaxSdkVersion = undefined;
					}
				}
			}

			// Check if symbol requires an Android <queries/> entry.
			const queryName = tiMethodQueries[symbol];
			if (queryName) {
				neededQueriesDictionary[queryName] = true;
			}
		}
	}

	// Return the entries needed to be injected into the generated "AndroidManifest.xml" file.
	const neededSettings = {
		queries: neededQueriesDictionary,
		storagePermissionMaxSdkVersion: storagePermissionMaxSdkVersion,
		usesPermissions: Object.keys(neededPermissionDictionary)
	};
	return neededSettings;
};

AndroidBuilder.prototype.generateAndroidManifest = async function generateAndroidManifest() {
	this.logger.info(__('Generating main "AndroidManifest.xml" files'));

	// Make sure app project's "./src/main" directory exists.
	await fs.ensureDir(this.buildAppMainDir);

	// We no longer support setting the following option to false anymore. Log a warning if not set to merge.
	// Note: Gradle handles the manifest merge between libraries and app project. Must use its features to manage it.
	if (!this.config.get('android.mergeCustomAndroidManifest', true)) {
		const message
			= 'Titanium CLI option "android.mergeCustomAndroidManifest" is no longer supported. '
			+ 'Use Google\'s "AndroidManifest.xml" feature "tools:remove" to remove XML elements instead.';
		this.logger.warn(__n(message));
	}

	// Generate all XML lines to be added as children within the manifest's <application/> block.
	const appChildXmlLines = [];
	if (this.tiapp.android) {
		// Fetch all "ti:app/android/activities" defined in "tiapp.xml" file.
		// These are our own custom JSActivity settings and are outside of the <manifest/> block.
		const tiappActivities = this.tiapp.android.activities || {};
		for (const jsFileName in tiappActivities) {
			// Get the next JSActivity.
			const tiActivityInfo = tiappActivities[jsFileName];
			if (!tiActivityInfo || !tiActivityInfo.url || !tiActivityInfo.classname) {
				continue;
			}

			// Add its <activity/> XML string to array.
			const xmlDoc = (new DOMParser()).parseFromString('<activity/>', 'text/xml');
			for (const propertyName in tiActivityInfo) {
				if (propertyName.startsWith('android:')) {
					const propertyValue = tiActivityInfo[propertyName];
					xmlDoc.documentElement.setAttribute(propertyName, propertyValue ? propertyValue.toString() : '');
				}
			}
			xmlDoc.documentElement.setAttribute('android:name', `${this.appid}.${tiActivityInfo.classname}`);
			appChildXmlLines.push(xmlDoc.documentElement.toString());
		}

		// Fetch all "ti:app/android/services" defined in "tiapp.xml" file.
		// These are our own custom JSService settings and are outside of the <manifest/> block.
		const tiappServices = this.tiapp.android.services || {};
		for (const jsFileName in tiappServices) {
			// Get the next JSService.
			const tiServiceInfo = tiappServices[jsFileName];
			if (!tiServiceInfo || !tiServiceInfo.url || !tiServiceInfo.classname) {
				continue;
			}

			// Add its <service/> and <intent-filter/> XML string(s) to array.
			const serviceName = `${this.appid}.${tiServiceInfo.classname}`;
			if (tiServiceInfo.type === 'quicksettings') {
				// QuickSettings service is generated via EJS template.
				let xmlContent = await fs.readFile(path.join(this.templatesDir, 'QuickService.xml'));
				xmlContent = ejs.render(xmlContent.toString(), {
					icon: '@drawable/' + (tiServiceInfo.icon || this.tiapp.icon).replace(/((\.9)?\.(png|jpg))$/, ''),
					label: tiServiceInfo.label || this.tiapp.name,
					serviceName: serviceName
				});
				const xmlDoc = (new DOMParser()).parseFromString(xmlContent, 'text/xml');
				const xmlString = xmlDoc.documentElement.toString().replace(/xmlns:android=""/g, '');
				const xmlLines = xmlString.split('\n');
				appChildXmlLines.push(...xmlLines); // Spread operator "..." turns an array into multiple arguments.
			} else {
				// This is a simple service. Add its 1 XML line to the array.
				const xmlDoc = (new DOMParser()).parseFromString('<service/>', 'text/xml');
				for (const propertyName in tiServiceInfo) {
					if (propertyName.startsWith('android:')) {
						const propertyValue = tiServiceInfo[propertyName];
						xmlDoc.documentElement.setAttribute(propertyName, propertyValue ? propertyValue.toString() : '');
					}
				}
				xmlDoc.documentElement.setAttribute('android:name', serviceName);
				appChildXmlLines.push(xmlDoc.documentElement.toString());
			}
		}
	}

	// Scan app's JS code to see what <uses-permission/> and <queries/> entries should be auto-injected into manifest.
	const neededManifestSettings = this.fetchNeededManifestSettings();

	// Generate the app's main manifest from EJS template.
	let mainManifestContent = await fs.readFile(path.join(this.templatesDir, 'AndroidManifest.xml'));
	mainManifestContent = ejs.render(mainManifestContent.toString(), {
		appChildXmlLines: appChildXmlLines,
		appIcon: '@drawable/' + this.tiapp.icon.replace(/((\.9)?\.(png|jpg))$/, ''),
		appLabel: this.tiapp.name,
		classname: this.classname,
		storagePermissionMaxSdkVersion: neededManifestSettings.storagePermissionMaxSdkVersion,
		packageName: this.appid,
		queries: neededManifestSettings.queries,
		usesPermissions: neededManifestSettings.usesPermissions
	});
	const mainManifest = AndroidManifest.fromXmlString(mainManifestContent);

	// Write the main "AndroidManifest.xml" file providing Titanium's default app manifest settings.
	const mainManifestFilePath = path.join(this.buildAppMainDir, 'AndroidManifest.xml');
	await new Promise((resolve) => {
		const writeHook = this.cli.createHook('build.android.writeAndroidManifest', this, (file, xml, done) => {
			done();
		});
		writeHook(mainManifestFilePath, null, resolve);
	});
	await mainManifest.writeToFilePath(mainManifestFilePath);

	// Set up secondary manifest object which will store custom manifest settings provided by Titanium app developer.
	// This will be written to app project's "debug" and "release" directories.
	const secondaryManifest = new AndroidManifest();

	// Copy all CommonJS module "AndroidManifest.xml" settings to secondary manifest object first.
	for (const module of this.modules) {
		// Skip native modules. Their manifest files will be handled by gradle build system.
		if (module.native) {
			continue;
		}

		// Copy manifest settings from "timodule.xml" if provided.
		const tiModuleXmlFilePath = path.join(module.modulePath, 'timodule.xml');
		try {
			if (await fs.exists(tiModuleXmlFilePath)) {
				const tiModuleInfo = new tiappxml(tiModuleXmlFilePath);
				if (tiModuleInfo && tiModuleInfo.android && tiModuleInfo.android.manifest) {
					const tiModuleManifest = AndroidManifest.fromXmlString(tiModuleInfo.android.manifest);
					secondaryManifest.copyFromAndroidManifest(tiModuleManifest);
				}
			}
		} catch (ex) {
			this.logger.error(`Unable to load Android <manifest/> content from: ${tiModuleXmlFilePath}`);
			throw ex;
		}

		// Copy module's "./platform/android/AndroidManifest.xml" file if it exists.
		const externalXmlFilePath = path.join(module.modulePath, 'platform', 'android', 'AndroidManifest.xml');
		try {
			if (await fs.exists(externalXmlFilePath)) {
				const externalManifest = await AndroidManifest.fromFilePath(externalXmlFilePath);
				secondaryManifest.copyFromAndroidManifest(externalManifest);
			}
		} catch (ex) {
			this.logger.error(`Unable to load file: ${externalXmlFilePath}`);
			throw ex;
		}
	}
	secondaryManifest.removeUsesSdk();  // Don't let modules define <uses-sdk/> elements.

	// Copy the manifest settings loaded from "tiapp.xml" and Titanium project's "./platform/android" directory.
	// Since this is copied last, it will overwrite all XML settings made by modules up above.
	// Note: The "customAndroidManifest" field is expected to be loaded/assigned in build.validate() method.
	if (this.customAndroidManifest) {
		secondaryManifest.copyFromAndroidManifest(this.customAndroidManifest);
	}

	// Write secondary "AndroidManifest.xml" if not empty.
	if (!secondaryManifest.isEmpty()) {
		// Make sure package name is set in <manifest/> so that ".ClassName" references in XML can be resolved.
		secondaryManifest.setPackageName(this.appid);

		// Replace ${tiapp.properties['key']} placeholders in manifest.
		secondaryManifest.replaceTiPlaceholdersUsing(this.tiapp, this.appid);

		// Do not allow developers to override the "configChanges" attribute on "TiBaseActivity" derived activities.
		// Most devs don't set this right, causing UI to disappear when a config change occurs for a missing setting.
		const tiActivityNames = [
			`.${this.classname}Activity`,
			`${this.appid}.${this.classname}Activity`,
			'org.appcelerator.titanium.TiActivity',
			'org.appcelerator.titanium.TiTranslucentActivity',
			'org.appcelerator.titanium.TiCameraXActivity',
			'org.appcelerator.titanium.TiVideoActivity'
		];
		for (const activityName of tiActivityNames) {
			secondaryManifest.removeActivityAttribute(activityName, 'android:configChanges');
		}

		// Apply "tools:replace" attributes to <manifest/>, <application/>, and <activity/> attributes set by app.
		// Avoids Google build errors if app's attributes conflict with attributes set by libraries.
		// Note: Old Titanium build system (before gradle) didn't error out. So, this is for backward compatibility.
		secondaryManifest.applyToolsReplace();

		// Create the "debug" and "release" subdirectories.
		const debugDirPath = path.join(this.buildAppDir, 'src', 'debug');
		const releaseDirPath = path.join(this.buildAppDir, 'src', 'release');
		await fs.ensureDir(debugDirPath);
		await fs.ensureDir(releaseDirPath);

		// Save manifest to above subdirectories.
		await secondaryManifest.writeToFilePath(path.join(debugDirPath, 'AndroidManifest.xml'));
		await secondaryManifest.writeToFilePath(path.join(releaseDirPath, 'AndroidManifest.xml'));
	}
};

AndroidBuilder.prototype.buildAppProject = async function buildAppProject() {
	this.logger.info(__('Building app'));

	// Configure keystore digital signing info via temporary environment variables.
	// Helps keep release key info a secret. The "build.gradle" will default to debug keystore if not provided.
	if (this.keystore) {
		process.env.TI_ANDROID_APP_KEYSTORE_FILE = this.keystore;
	}
	if (this.keystoreStorePassword) {
		process.env.TI_ANDROID_APP_KEYSTORE_PASSWORD = this.keystoreStorePassword;
	}
	if (this.keystoreAlias && this.keystoreAlias.name) {
		process.env.TI_ANDROID_APP_KEYSTORE_ALIAS_NAME = this.keystoreAlias.name;
	}
	if (this.keystoreKeyPassword) {
		process.env.TI_ANDROID_APP_KEYSTORE_ALIAS_PASSWORD = this.keystoreKeyPassword;
	}

	// Build the "app" project.
	const gradlew = new GradleWrapper(this.buildDir);
	gradlew.logger = this.logger;
	if (this.allowDebugging) {
		// Build a debug version of the APK. (Native code can be debugged via Android Studio.)
		await gradlew.assembleDebug('app');
	} else {
		// Build a release version of the APK.
		await gradlew.assembleRelease('app');

		// Create an "*.aab" app-bundle file of the app.
		// Note: This is a Google Play publishing format. App-bundles cannot be ran on Android devices.
		//       Google's server will generate multiple APKs from this split by architecture and image density.
		await gradlew.bundleRelease('app');

		// Set path to the app-bundle file that was built up above.
		// Our "package.js" event hook will later copy it to the developer's chosen destination directory.
		this.aabFile = path.join(this.buildDir, 'app', 'build', 'outputs', 'bundle', 'release', 'app-release.aab');
	}

	// Verify that we can find the above built file(s).
	if (!await fs.exists(this.apkFile)) {
		throw new Error(`Failed to find built APK file: ${this.apkFile}`);
	}
	if (this.aabFile && !await fs.exists(this.aabFile)) {
		throw new Error(`Failed to find built AAB file: ${this.aabFile}`);
	}
};

AndroidBuilder.prototype.writeBuildManifest = async function writeBuildManifest() {
	this.logger.info(__('Writing build manifest: %s', this.buildManifestFile.cyan));

	await new Promise((resolve) => {
		this.cli.createHook('build.android.writeBuildManifest', this, function (manifest, cb) {
			fs.ensureDirSync(this.buildDir);
			fs.existsSync(this.buildManifestFile) && fs.unlinkSync(this.buildManifestFile);
			fs.writeFile(this.buildManifestFile, JSON.stringify(this.buildManifest = manifest, null, '\t'), cb);
		})({
			target: this.target,
			deployType: this.deployType,
			classname: this.classname,
			platformPath: this.platformPath,
			modulesHash: this.modulesHash,
			gitHash: ti.manifest.githash,
			outputDir: this.cli.argv['output-dir'],
			name: this.tiapp.name,
			id: this.tiapp.id,
			analytics: this.tiapp.analytics,
			publisher: this.tiapp.publisher,
			url: this.tiapp.url,
			version: this.tiapp.version,
			description: this.tiapp.description,
			copyright: this.tiapp.copyright,
			guid: this.tiapp.guid,
			icon: this.tiapp.icon,
			fullscreen: this.tiapp.fullscreen,
			navbarHidden: this.tiapp['navbar-hidden'],
			skipJSMinification: !!this.cli.argv['skip-js-minify'],
			encryptJS: this.encryptJS,
			minSDK: this.minSDK,
			targetSDK: this.targetSDK,
			propertiesHash: this.propertiesHash,
			activitiesHash: this.activitiesHash,
			servicesHash: this.servicesHash
		}, resolve);
	});
};

AndroidBuilder.prototype.createGradleWrapper = function createGradleWrapper(directoryPath) {
	// Creates a gradle handling object for plugins such as hyperloop.
	return new GradleWrapper(directoryPath);
};

// create the builder instance and expose the public api
(function (androidBuilder) {
	exports.config   = androidBuilder.config.bind(androidBuilder);
	exports.validate = androidBuilder.validate.bind(androidBuilder);
	exports.run      = androidBuilder.run.bind(androidBuilder);
}(new AndroidBuilder(module)));
