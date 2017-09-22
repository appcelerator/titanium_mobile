/*
 * install.js: Titanium iOS CLI install hook
 *
 * Copyright (c) 2012-2017, Appcelerator, Inc.  All Rights Reserved.
 * See the LICENSE file for more information.
 */

'use strict';

const appc = require('node-appc'),
	async = require('async'),
	fs = require('fs'),
	wrench = require('wrench'),
	ioslib = require('ioslib'),
	path = require('path'),
	run = appc.subprocess.run,
	__ = appc.i18n(__dirname).__;

exports.cliVersion = '>=3.2';

exports.init = function (logger, config, cli) {
	cli.addHook('build.post.compile', {
		priority: 8000,
		post: function (builder, finished) {
			if (cli.argv.target !== 'device') {
				return finished();
			}

			if (cli.argv['build-only']) {
				logger.info(__('Performed build only, skipping installing of the application'));
				return finished();
			}

			ioslib.device.detect({ bypassCache: true }, function (err, results) {
				const devices = {};
				if (!err) {
					results.devices.forEach(function (device) {
						if (device.udid !== 'itunes' && device.udid !== 'all' && (builder.deviceId === 'all' || device.udid === builder.deviceId)) {
							devices[device.udid] = device;
						}
					});
				}

				// if we don't have a deviceId, or it's "Export .ipa", or it's "all", but not devices are connected,
				// then export .ipa file
				if (!builder.deviceId || builder.deviceId === 'export-ipa' || (builder.deviceId && !Object.keys(devices).length)) {
					logger.info(__('Exporting application as ' + (builder.tiapp.name + '.ipa').cyan));

					// Prepare related files & directories
					const appFile = builder.tiapp.name + '.app';
					const appPath = path.join(path.dirname(builder.xcodeAppDir), appFile);
					const ipaPath = path.join(path.dirname(builder.xcodeAppDir), builder.tiapp.name + '.ipa');
					const payloadDir = path.join(path.dirname(builder.xcodeAppDir), 'Payload') 
					
					// Create "Payload/" directory
					fs.mkdirSync(payloadDir);
					
					// Copy the <AppName>.app file to Payload/<AppName>.app
					fs.rename(appPath, path.join(payloadDir, appFile));
					
					// Zip the directory to create an <AppName>.ipa
					var output = fs.createWriteStream(ipaPath);
					var archive = archiver('zip', {
					    zlib: { level: 9 }
					});

					output.on('close', function () {
						fs.existsSync(payloadDir) && wrench.rmdirSyncRecursive(payloadDir);
						logger.info(__('Successfully exported application to: ' + ipaPath.cyan));
					});

					archive.on('warning', function (err) {
					  if (err.code !== 'ENOENT') {
							new appc.exception(__('Failed to export .ipa file: ' + err));
					  }
					});

					archive.on('error', function (err) {
						new appc.exception(__('Failed to export .ipa file: ' + err));
					});

					archive.pipe(output);
					archive.directory(payloadDir, payloadDir);
					archive.finalize();

					return;
				}

				const udids = Object.keys(devices),
					levels = logger.getLevels(),
					logLevelRE = new RegExp('^(\u001b\\[\\d+m)?\\[?(' + levels.join('|') + '|log|timestamp)\\]?\\s*(\u001b\\[\\d+m)?(.*)', 'i'), // eslint-disable-line security/detect-non-literal-regexp
					handles = {};
				let startLog = false,
					runningCount = 0,
					installCount = 0;

				function quit(force, udid) {
					runningCount--;

					if (udid && handles[udid]) {
						handles[udid].stop();
						delete handles[udid];
					} else if (force) {
						Object.keys(handles).forEach(function (udid) {
							handles[udid].stop();
							delete handles[udid];
						});
					}

					if (force || runningCount <= 0) {
						if (startLog) {
							const endLogTxt = __('End application log');
							logger.log(('-- ' + endLogTxt + ' ' + (new Array(75 - endLogTxt.length)).join('-')).grey + '\n');
						}
						process.exit(0);
					}
				}

				// install the app for the specified device or "all" devices
				async.eachSeries(udids, function (udid, next) {
					const device = devices[udid];
					let lastLogger = 'debug';

					logger.info(__('Installing app on device: %s', device.name.cyan));

					const handle = handles[udid] = ioslib.device
						.install(udid, builder.xcodeAppDir, {
							appName: builder.tiapp.name,
							logPort: builder.tiLogServerPort
						})
						.on('installed', function () {
							logger.info(__('App successfully installed on device: %s', device.name.cyan));
							if (++installCount === udids.length && !startLog) {
								setTimeout(function () {
									if (process.env.STUDIO_VERSION) {
										logger.log(__('Please manually launch the application').magenta + '\n');
									} else {
										logger.log(__('Please manually launch the application or press CTRL-C to quit').magenta + '\n');
									}
								}, 50);
							}
							next();
						})
						.on('app-started', function () {
							runningCount++;
						})
						.on('log', function (msg) {
							let skipLine = false;

							if (!handles[udid].logStarted) {
								if (msg.indexOf('{') === 0) {
									try {
										const headers = JSON.parse(msg);
										if (headers.appId !== builder.tiapp.id) {
											logger.error(__('Another Titanium app "%s" is currently running and using the log server port %d', headers.appId, builder.tiLogServerPort));
											logger.error(__('Stop the running Titanium app, then rebuild this app'));
											logger.error(__('-or-'));
											logger.error(__('Set a unique <log-server-port> between 1024 and 65535 in the <ios> section of the tiapp.xml'));
											handle.stop();

											if (--runningCount <= 0) {
												logger.log();
												process.exit(1);
											}
										}
									} catch (e) {
										// squeltch
									}
									skipLine = true;
								}
								handles[udid].logStarted = true;
							}

							if (!startLog) {
								const startLogTxt = __('Start application log');
								logger.log(('-- ' + startLogTxt + ' ' + (new Array(75 - startLogTxt.length)).join('-')).grey);
								startLog = true;
							}

							if (skipLine) {
								return;
							}

							let m = msg.match(logLevelRE);
							if (m) {
								let line = m[0].trim();
								m = line.match(logLevelRE);
								if (m) {
									lastLogger = m[2].toLowerCase();
									line = m[4].trim();
								}
								if (levels.indexOf(lastLogger) === -1) {
									// unknown log level
									logger.log(('[' + lastLogger.toUpperCase() + '] ').cyan + line);
								} else {
									logger[lastLogger](line);
								}
							} else if (levels.indexOf(lastLogger) === -1) {
								logger.log(('[' + lastLogger.toUpperCase() + '] ').cyan + msg);
							} else {
								logger[lastLogger](msg);
							}
						})
						.on('app-quit', function () { quit(false, udid); })
						.on('disconnect', function () { quit(false, udid); })
						.on('error', function (err) {
							err = err.message || err.toString();
							let details;
							if (err.indexOf('0xe8008017') !== -1) {
								details = __('Chances are there is a signing issue with your provisioning profile or the generated app is not compatible with your device.');
							} else if (err.indexOf('0xe8008019') !== -1) {
								details = __('Chances are there is a signing issue. Clean the project and try building the project again.');
							} else if (err.indexOf('0xe800007f') !== -1) {
								details = __('Try reconnecting your device and try again.');
							} else if (err.indexOf('0xe8008016') !== -1) {
								details = __('Chances are there is an issue with your entitlements. Verify the bundle IDs in the generated Info.plist file.');
							} else if (err.indexOf('0xe8008016') !== -1) {
								details = __('Your provisioning profile probably has some entitlements that are not enabled in the Entitlements.plist file.');
							} else {
								details = __('For some reason the app failed to install on the device. Try reconnecting your device and check your provisioning profile and entitlements.');
							}
							next(new appc.exception(err, details));
						});
				}, finished);

				// listen for ctrl-c
				process.on('SIGINT', function () {
					logger.log();
					quit(true);
				});
			});
		}
	});
};
