/*
 * run.js: Titanium iOS Android run hook
 *
 * Copyright (c) 2012-2013, Appcelerator, Inc.  All Rights Reserved.
 * See the LICENSE file for more information.
 */

var ADB = require('titanium-sdk/lib/adb'),
	appc = require('node-appc'),
	async = require('async'),
	EmulatorManager = require('titanium-sdk/lib/emulator'),
	fs = require('fs'),
	path = require('path'),
	__ = appc.i18n(__dirname).__;

exports.cliVersion = '>=3.2';

exports.init = function (logger, config, cli) {
	var deviceInfo;

	cli.on('build.pre.compile', {
		priority: 8000,
		post: function (builder, finished) {
			if (builder.buildOnly) return finished();

			if (builder.target == 'emulator') {
				logger.info(__('Launching emulator: %s', builder.deviceId.cyan));

				cli.createHook('build.android.startEmulator', function (deviceId, opts, cb) {
					var emulator = new EmulatorManager(config);
					emulator.start(deviceId, opts, function (err, emu) {
						if (err) {
							logger.error(__('Unable to start emulator "%s"', deviceId) + '\n');
							process.exit(1);
						}

						emu.on('ready', function (device) {
							logger.info(__('Emulator ready!'));
							deviceInfo = device;
						});

						cb();
					});
				})(builder.deviceId, {}, function (err, results, opts) {
					finished();
				});

			} else if (builder.target == 'device') {
				var adb = new ADB(config);
				adb.devices(function (err, devices) {
					if (err) {
						err.toString.split('\n').forEach(logger.error);
						logger.log();
						process.exit(1);
					}

					deviceInfo = devices.filter(function (d) { return d.id == builder.deviceId; }).shift();

					if (!deviceInfo) {
						logger.error(__('Unable to find device "%s"', builder.deviceId));
						logger.error(__('Did you unplug it?') + '\n');
						process.exit(1);
					}

					finished();
				});

			} else {
				finished();
			}
		}
	});

	cli.on('build.post.compile', {
		priority: 10000,
		post: function (builder, finished) {
			if (builder.target != 'emulator' && builder.target != 'device') return finished();

			if (builder.buildOnly) {
				logger.info(__('Performed build only, skipping installing of the application'));
				return finished();
			}

			if (!builder.apkFile || !fs.existsSync(builder.apkFile)) {
				logger.error(__('No APK file to install and run, skipping'));
				return finished();
			}

			var adb = new ADB(config),
				deployData = {
					debuggerEnabled: builder.allowDebugging && builder.debugPort,
					debuggerPort: builder.allowDebugging && builder.debugPort || -1,
					profilerEnabled: builder.allowProfiling && builder.profilePort,
					profilerPort: builder.allowProfiling && builder.profilePort || -1
				},
				appPidRegExp = null;

			async.series([
				function (next) {
					logger.info(__('Making sure the adb server is running'));
					adb.startServer(next);
				},

				function (next) {
					if (deviceInfo || builder.target != 'emulator') {
						return next();
					}

					logger.info(__('Waiting for emulator to become ready'));

					var timeout = config.get('android.emulatorStartTimeout', 2 * 60 * 1000),  // 2 minute default
						waitUntil = Date.now() + timeout,
						timer = setInterval(function () {
							if (deviceInfo) {
								clearInterval(timer);
								next();
							} else if (Date.now() > waitUntil) {
								logger.error(__('Emulator failed to start in a timely manner') + '\n');
								logger.log(__('The current timeout is set to %s ms', String(timeout).cyan));
								logger.log(__('You can increase this timeout by running: %s', (cli.argv.$ + ' config android.emulatorStartTimeout <timeout ms>').cyan) + '\n');
								process.exit(1);
							}
						}, 250);
				},

				function (next) {
					if (deployData.debuggerEnabled || deployData.profilerEnabled) {
						// push deploy.json
						var deployJsonFile = path.join(builder.buildDir, 'bin', 'deploy.json');
						fs.writeFileSync(deployJsonFile, JSON.stringify(deployData));
						logger.info(__('Pushing %s to sdcard', deployJsonFile.cyan));
						adb.shell(deviceInfo.id, 'mkdir /sdcard/' + builder.appid + ' || echo', function () {
							adb.push(deviceInfo.id, deployJsonFile, '/sdcard/' + builder.appid + '/deploy.json', next);
						});
					} else {
						logger.info(__('Removing %s from sdcard', 'deploy.json'.cyan));
						adb.shell(deviceInfo.id, '[ -f "/sdcard/' + builder.appid + '/deploy.json"] && rm -f "/sdcard/' + builder.appid + '/deploy.json" || echo ""', next);
					}
				},

				function (next) {
					// install the app
					logger.info(__('Installing apk: %s', builder.apkFile.cyan));

					adb.installApp(deviceInfo.id, builder.apkFile, function (err) {
						if (err) {
							logger.error(__('Failed to install apk on "%s"', deviceInfo.id));
							err.toString().split('\n').forEach(logger.error);
							logger.log();
							process.exit(1);
						}

						logger.info(__('App successfully installed'));
						next();
					});
				},

				function (next) {
					var logBuffer = [],
						displayStartLog = true,
						tiapiRegExp = /^(\w\/TiAPI\s*\:)/,
						endLog = false;

					function printData(line) {
						if (appPidRegExp.test(line)) {
							line = line.trim().replace(/\%/g, '%%').replace(appPidRegExp, ':');
							var logLevel = line.charAt(0).toLowerCase();
							if (tiapiRegExp.test(line)) {
								line = line.replace(tiapiRegExp, '').trim();
							} else {
								line = line.replace(/^\w\/(\w+)\s*\:/g, '$1:').grey;
							}
							switch (logLevel) {
								case 'v':
									logger.trace(line);
									break;
								case 'd':
									logger.debug(line);
									break;
								case 'w':
									logger.warn(line);
									break;
								case 'e':
									logger.error(line);
									break;
								case 'i':
								default:
									logger.info(line);
							}
						}
					}

					adb.logcat(deviceInfo.id, function (data) {
						if (appPidRegExp) {
							if (displayStartLog) {
								var startLogTxt = __('Start application log');
								logger.log(('-- ' + startLogTxt + ' ' + (new Array(75 - startLogTxt.length)).join('-')).grey);
								displayStartLog = false;
							}

							// flush log buffer
							if (logBuffer.length) {
								logBuffer.forEach(printData);
								logBuffer = [];
							}

							// flush data
							data.trim().split('\n').forEach(printData);
						} else {
							logBuffer = logBuffer.concat(data.trim().split('\n'));
						}
					}, function endLog() {
						// the adb server shutdown, the emulator quit, or the device was unplugged
						var endLogTxt = __('End application log');
						logger.log(('-- ' + endLogTxt + ' ' + (new Array(75 - endLogTxt.length)).join('-')).grey + '\n');
						endLog = true;
					});

					// listen for ctrl-c
					process.on('SIGINT', function () {
						if (!endLog) {
							var endLogTxt = __('End application log');
							logger.log('\r' + ('-- ' + endLogTxt + ' ' + (new Array(75 - endLogTxt.length)).join('-')).grey + '\n');
						}
						process.exit(0);
					});

					next();
				},

				function (next) {
					logger.info(__('Starting app: %s', (builder.appid + '/.' + builder.classname + 'Activity').cyan));
					adb.startApp(deviceInfo.id, builder.appid, builder.classname + 'Activity', function (err) {
						var timeout = config.get('android.appStartTimeout', 2 * 60 * 1000),  // 2 minute default
							waitUntil = Date.now() + timeout,
							done = false;

						async.whilst(
							function () { return !done; },
							function (cb) {
								if (Date.now() > waitUntil) {
									logger.error(__('Application failed to launch') + '\n');
									logger.log(__('The current timeout is set to %s ms', String(timeout).cyan));
									logger.log(__('You can increase this timeout by running: %s', (cli.argv.$ + ' config android.emulatorStartTimeout <timeout ms>').cyan) + '\n');
									process.exit(1);
								}

								adb.getPid(deviceInfo.id, builder.appid, function (err, pid) {
									if (err || !pid) {
										setTimeout(cb, 100);
									} else {
										logger.info(__('Application pid: %s', String(pid).cyan));
										appPidRegExp = new RegExp('\\(\\s*' + pid + '\\)\:');
										done = true;
										cb();
									}
								});
							},
							next
						);
					});
				},

				function (next) {
					if (deployData.debuggerEnabled) {
						logger.info(__('Forwarding host port %s to device for debugging', builder.debugPort));
						var forwardPort = 'tcp:' + builder.debugPort;
						adb.forward(deviceInfo.id, forwardPort, forwardPort, next);
					} else {
						next();
					}
				},

				function (next) {
					if (deployData.profilerEnabled) {
						logger.info(__('Forwarding host port %s:%s to device for profiling', builder.profilePort));
						var forwardPort = 'tcp:' + builder.profilePort;
						adb.forward(deviceInfo.id, forwardPort, forwardPort, next);
					} else {
						next();
					}
				},

			], function (err) {
				if (err) {
					logger.error(err);
				}
				finished();
			});
		}
	});

};
