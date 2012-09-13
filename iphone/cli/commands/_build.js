/*
 * build.js: Titanium IOS CLI build command
 *
 * Copyright (c) 2012, Appcelerator, Inc.  All Rights Reserved.
 * See the LICENSE file for more information.
 */

var ti = require('titanium-sdk'),
	fs = require('fs'),
	path = require('path'),
	crypto = require('crypto'),
	exec = require('child_process').exec,
	Buffer = require('buffer').Buffer,
	wrench = require('wrench'),
	appc = require('node-appc'),
	afs = appc.fs,
	ios = appc.ios,
	version = appc.version,
	hitch = appc.util.hitch,
	parallel = appc.async.parallel,
	series = appc.async.series,
	minIosSdkVersion = '4.0',
	iosEnv,
	targets = ['device', 'simulator', 'package'],
	targetTypes = ['universal', 'iphone', 'ipad'];

exports.config = function (logger, config, cli) {
	return function (callback) {
		ios.detect(function (env) {
			iosEnv = env || {};
			
			var sdks = {},
				defaultSdk;
			Object.keys(iosEnv.xcode).forEach(function (key) {
				iosEnv.xcode[key].sdks.sort().reverse().forEach(function (sdk, i) {
					if (iosEnv.xcode[key].selected && i == 0) {
						defaultSdk = sdk;
					}
					sdks[sdk] = 1;
				});
			});
			sdks = Object.keys(sdks).sort().reverse();
			
			callback({
				flags: {
					force: {
						abbr: 'f',
						default: false,
						desc: __('force a full rebuild')
					},
					xcode: {
						// secret flag to perform Xcode pre-compile build step
						default: false,
						hidden: true
					}
				},
				options: {
					'ios-version': {
						abbr: 'i',
						default: defaultSdk || (sdks.length && sdks[0]),
						desc: __('iOS SDK version to build for'),
						values: sdks
					},
					target: {
						abbr: 't',
						default: 'device',
						desc: __('the target to build for'),
						required: true,
						values: targets
					},
					'target-type': {
						abbr: 'T',
						default: 'universal',
						desc: __('the target device family type'),
						hint: __('type'),
						required: true,
						values: targetTypes
					}
				}
			});
		}, {
			minsdk: minIosSdkVersion
		});
	};
};

exports.validate = function (logger, config, cli) {
	if (!Object.keys(iosEnv.xcode).length) {
		logger.error(__('Unable to find Xcode') + '\n');
		logger.log(__('Please download and install Xcode, then try again') + '\n');
		process.exit(1);
	}
	
	if (!iosEnv.xcode.__selected__) {
		logger.error(__('No Xcode version is currently selected') + '\n');
		logger.log(__("Use 'xcode-select' to select one of the Xcode versions:"));
		Object.keys(iosEnv.xcode).forEach(function (ver) {
			if (ver != '__selected__') {
				logger.log(('   xcode-select --switch ' + iosEnv[ver].xcode).cyan);
			}
		});
		logger.log();
		process.exit(1);
	}
	
	if (!Object.keys(iosEnv.xcode).some(function (ver) { return iosEnv.xcode[ver].sdks.indexOf(cli.argv['ios-version']) != -1; })) {
		logger.error(__('Unable to find iOS SDK %s', cli.argv['ios-version']) + '\n');
		logger.log(__('Please download and install an iOS SDK (version %s or newer), then try again', version.format(minIosSdkVersion, 2)) + '\n');
		process.exit(1);
	}
	
	if (targets.indexOf(cli.argv.target) == -1) {
		logger.error(__('Invalid target "%s"', cli.argv.target) + '\n');
		appc.string.suggest(cli.argv.target, targets, logger.log, 3);
		process.exit(1);
	}
	
	if (targetTypes.indexOf(cli.argv['target-type']) == -1) {
		logger.error(__('Invalid target type "%s"', cli.argv['target-type']) + '\n');
		appc.string.suggest(cli.argv['target-type'], targetTypes, logger.log, 3);
		process.exit(1);
	}
};

exports.run = function (logger, config, cli, finished) {
	new build(logger, config, cli, finished);
};

function build(logger, config, cli, finished) {
	logger.info(__('Compiling "%s" build', cli.argv['build-type']));
	
	this.logger = logger;
	this.cli = cli;
	
	this.titaniumSdkVersion = ti.manifest.version;
	this.titaniumIosSdkPath = afs.resolvePath(path.dirname(module.filename), '..', '..');
	
	this.platformName = path.basename(this.titaniumIosSdkPath); // the name of the actual platform directory which will some day be "ios"
	
	this.projectDir = afs.resolvePath(cli.argv.dir);
	this.tiapp = new ti.tiappxml(path.join(this.projectDir, 'tiapp.xml'));
	
	this.buildDir = path.join(this.projectDir, 'build', this.platformName);
	this.buildVersionFile = path.join(this.buildDir, 'Resources', '.version');
	
	this.xcodeEnv = iosEnv.xcode.__selected__;
	this.xcodeTarget = cli.argv.target == 'simulator' ? 'Debug' : 'Release';
	this.xcodeTargetOS = cli.argv.target == 'simulator' ? 'simulator' : 'os';
	this.xcodeBuildDir = path.join(this.buildDir, 'build', this.xcodeTarget + '-iphone' + this.xcodeTargetOS);
	this.xcodeProjectConfigFile = path.join(this.buildDir, 'project.xcconfig');
	this.xcodeAppDir = path.join(this.xcodeBuildDir, this.tiapp.name + '.app')
	
	this.logger.debug(__('Titanium iOS SDK directory: %s', this.titaniumIosSdkPath.cyan));
	this.logger.debug(__('Setting Xcode target to %s', this.xcodeTarget.cyan));
	this.logger.debug(__('Setting Xcode build OS to %s', ('iphone' + this.xcodeTargetOS).cyan));
	this.logger.debug(__('Xcode installation: %s', this.xcodeEnv.path.cyan));
	this.logger.debug(__('Installed iOS SDKs: %s', this.xcodeEnv.sdks.join(', ').cyan));
	this.logger.debug(__('Installed iOS Simulators: %s', this.xcodeEnv.sims.join(', ').cyan));
	this.logger.debug(__('iOS development certificates: %s', iosEnv.certs.dev ? iosEnv.certs.devNames.join(', ').cyan : __('not found').cyan));
	this.logger.debug(__('iOS distribution certificates: %s', iosEnv.certs.dist ? iosEnv.certs.distNames.join(', ').cyan : __('not found').cyan));
	this.logger.debug(__('iOS WWDR certificate: %s', iosEnv.certs.wwdr ? __('installed').cyan : __('not found').cyan));
	
	if (cli.argv.xcode) {
		this.logger.warn('Xcode pre-compile step not finished yet!');
		process.exit(0);
	}
	
	// create the build directory (<project dir>/build/[iphone|ios])
	wrench.mkdirSyncRecursive(this.buildDir);
	
	parallel(this, [
		function (callback) {
			this.createInfoPlist();
			
			// if we're not running in the simulator we want to clean out the build directory
			if (cli.argv.target == 'simulator' && afs.exists(this.xcodeBuildDir)) {
				wrench.rmdirSyncRecursive(this.xcodeBuildDir);
			}
			// TODO: do we need this? doesn't xcode make this for us?
			// wrench.mkdirSyncRecursive(xcodeBuildDir);
			
			// determine the libTiCore hash
			var libTiCoreHash;
				libTiCoreFD = fs.openSync(path.join(this.titaniumIosSdkPath, 'libTiCore.a'), 'r');
			if (libTiCoreFD) {
				try {
					var buffer = new Buffer(1024);
					fs.readSync(libTiCoreFD, buffer, 0, 1024, 0);
					libTiCoreHash = crypto.createHash('md5').update(buffer).digest("hex");
				} finally {
					fs.closeSync(libTiCoreFD);
				}
			}
			
			// read the version file
			var versionIosSdkPath, versionGuid, versionTiCoreHash, versionGitHash;
			if (afs.exists(this.buildVersionFile)) {
				var parts = fs.readFileSync(this.buildVersionFile).toString().split(',');
				parts.length > 0 && (versionIosSdkPath = parts[0]);
				parts.length > 1 && (versionGuid = parts[1]);
				parts.length > 2 && (versionTiCoreHash = parts[2]);
				parts.length > 3 && (versionGitHash = parts[3]);
			}
			
			// check if we need to do a rebuild
			var forceRebuild = this.checkForceRebuild(versionIosSdkPath, versionGuid, versionTiCoreHash, versionGitHash, libTiCoreHash);
			if (forceRebuild) {
				this.logger.info(__('Performing full rebuild'));
				this.createXcodeProject();
			}
			
			// only build if force rebuild (different version) or the app hasn't yet been built initially
			if (this.tiapp.guid != versionGuid) { // TODO: or force_xcode
				fs.writeFileSync(this.buildVersionFile, [
					this.titaniumIosSdkPath,
					this.tiapp.guid,
					libTiCoreHash,
					ti.manifest.githash
				].join(','));
			}
			
			callback();
		},
		
		function (callback) {
			if (!this.tiapp.modules || !this.tiapp.modules.length) {
				this.logger.info(__('No Titanium Modules required, continuing'));
				callback();
				return;
			}
			
			ti.module.find(this.tiapp.modules, ['ios', 'iphone'], this.projectDir, this.logger, hitch(this, function (modules) {
				if (modules.missing.length) {
					this.logger.error(__('Could not find all required Titanium Modules:'))
					modules.missing.forEach(function (m) {
						this.logger.error('   id: ' + m.id + '\t version: ' + m.version + '\t platform: ' + m.platform);
					}, this);
					this.logger.log();
					process.exit(1);
				}
				
				if (modules.incompatible.length) {
					this.logger.error(__('Found incompatible Titanium Modules:'));
					modules.incompatible.forEach(function (m) {
						this.logger.error('   id: ' + m.id + '\t version: ' + m.version + '\t platform: ' + m.platform + '\t min sdk: ' + m.minsdk);
					}, this);
					this.logger.log();
					process.exit(1);
				}
				
				// dump(modules);
				/*
				detector = ModuleDetector(this.projectDir)
				missing_modules, modules = detector.find_app_modules(ti, 'iphone')
				module_lib_search_path, module_asset_dirs = locate_modules(modules, this.projectDir, this.xcodeAppDir, log)
				common_js_modules = []
				
				# search for modules that the project is using
				# and make sure we add them to the compile
				for module in modules:
					if module.js:
						common_js_modules.append(module)
						continue
					module_id = module.manifest.moduleid.lower()
					module_version = module.manifest.version
					module_lib_name = ('lib%s.a' % module_id).lower()
					# check first in the local project
					local_module_lib = os.path.join(this.projectDir, 'modules', 'iphone', module_lib_name)
					local = False
					if os.path.exists(local_module_lib):
						module_lib_search_path.append([module_lib_name, local_module_lib])
						local = True
						log("[INFO] Detected third-party module: %s" % (local_module_lib))
					else:
						if module.lib is None:
							module_lib_path = module.get_resource(module_lib_name)
							log("[ERROR] Third-party module: %s/%s missing library at %s" % (module_id, module_version, module_lib_path))
							sys.exit(1)
						module_lib_search_path.append([module_lib_name, os.path.abspath(module.lib).rsplit('/',1)[0]])
						log("[INFO] Detected third-party module: %s/%s" % (module_id, module_version))
					force_xcode = True
	
					if not local:
						# copy module resources
						img_dir = module.get_resource('assets', 'images')
						if os.path.exists(img_dir):
							dest_img_dir = os.path.join(this.xcodeAppDir, 'modules', module_id, 'images')
							if not os.path.exists(dest_img_dir):
								os.makedirs(dest_img_dir)
							module_asset_dirs.append([img_dir, dest_img_dir])
	
						# copy in any module assets
						module_assets_dir = module.get_resource('assets')
						if os.path.exists(module_assets_dir):
							module_dir = os.path.join(this.xcodeAppDir, 'modules', module_id)
							module_asset_dirs.append([module_assets_dir, module_dir])
				*/
				callback();
			}));
		},
		
		function (callback) {
			if (!this.tiapp.plugins || !this.tiapp.plugins.length) {
				this.logger.info(__('No legacy Titanium plugins required, continuing'));
				callback();
				return;
			}
			
			ti.plugin.find(this.tiapp.plugins, this.projectDir, this.logger, hitch(this, function (plugins) {
				if (plugins.missing.length) {
					this.logger.error(__('Could not find all required Titanium plugins:'))
					plugins.missing.forEach(function (m) {
						this.logger.error('   id: ' + m.id + '\t version: ' + m.version);
					}, this);
					this.logger.log();
					process.exit(1);
				}
				
				// dump(plugins);
				
				callback();
			}));
		}
	], function () {
		var minVer = this.tiapp.ios && this.tiapp.ios['min-ios-ver'] || minIosSdkVersion;
		
		if (version.lt(minVer, minIosSdkVersion)) {
			this.logger.warn(__('The %s of the iOS section in the tiapp.xml is lower than minimum supported version: Using %s as minimum', 'min-ios-ver'.cyan, version.format(minIosSdkVersion, 2).cyan));
			minVer = minIosSdkVersion;
		} else if (version.gt(minVer, cli.argv['ios-version'])) {
			this.logger.warn(__('The %s of the iOS section in the tiapp.xml is greater than the specified %s: Using %s as minimum', 'min-ios-ver'.cyan, 'ios-version'.cyan, version.format(cli.argv['ios-version'], 2).cyan));
			minVer = cli.argv['ios-version'];
		}
		this.logger.info(__('Minimum iOS version: %s', version.format(minVer, 3, 3).cyan));
		
		// no armv6 support above 4.3 or with 6.0+ SDK
		if (version.lt('4.3', minVer) || version.lt('6.0', cli.argv['ios-version'])) {
			validArchs = 'armv7 i386';
		} else {
			validArchs = 'armv6 armv7 i386';
		}

		//deployTarget = 'IPHONEOS_DEPLOYMENT_TARGET=' + minVer
		//deviceTarget = 'TARGETED_DEVICE_FAMILY=1'
		
		dump(cli.argv);
		finished && finished();
	});
}

function scrubName(name) {
	name = name.replace(/-/g, '_').replace(/\W/g, '')
	return /^[0-9]/.test(name) ? 'k' + name : name;
}

build.prototype = {

	createInfoPlist: function () {
		var src = this.projectDir + '/Info.plist';
		// if the user has a Info.plist in their project directory, consider that a custom override
		if (afs.exists(src)) {
			this.logger.info(__('Copying Info.plist'));
			afs.copyFileSync(src, this.buildDir + '/Info.plist', { logger: this.logger.debug });
		} else {
			this.logger.info(__('Building Info.plist'));
			
			var iphone = this.tiapp.iphone,
				ios = this.tiapp.ios,
				fbAppId = this.tiapp.properties && this.tiapp.properties['ti.facebook.appid'],
				iconName = (this.tiapp.appicon || 'appicon.png').replace(/(.+)(\..*)$/, '$1'),
				consts = {
					'__APPICON__': iconName,
					'__PROJECT_NAME__': this.tiapp.name,
					'__PROJECT_ID__': this.tiapp.id,
					'__URL__': this.tiapp.id,
					'__URLSCHEME__': this.tiapp.name.replace(/\./g, '_').replace(/ /g, '').toLowerCase(),
					'__ADDITIONAL_URL_SCHEMES__': fbAppId ? '<string>fb' + fbAppId + '</string>' : ''
				},
				plist = new appc.plist();
			
			if (afs.exists(this.titaniumIosSdkPath + '/Info.plist')) {
				plist.parse(fs.readFileSync(this.titaniumIosSdkPath + '/Info.plist').toString().replace(/(__.+__)/g, function (match, key, format) {
					return consts.hasOwnProperty(key) ? consts[key] : '<!-- ' + key + ' -->'; // if they key is not a match, just comment out the key
				}));
			}
			
			this.tiapp['persistent-wifi'] === true && (plist.UIRequiresPersistentWiFi = true);
			this.tiapp['prerendered-icon'] === true && (plist.UIPrerenderedIcon = true);
			this.tiapp['statusbar-hidden'] === true && (plist.UIStatusBarHidden = true);
			
			if (/opaque_black|opaque|black/.test(this.tiapp['statusbar-style'])) {
				plist.UIStatusBarStyle = 'UIStatusBarStyleBlackOpaque';
			} else if (/translucent_black|transparent|translucent/.test(this.tiapp['statusbar-style'])) {
				plist.UIStatusBarStyle = 'UIStatusBarStyleBlackTranslucent';
			} else {
				plist.UIStatusBarStyle = 'UIStatusBarStyleDefault';
			}
			
			if (iphone) {
				if (iphone.orientations) {
					Object.keys(iphone.orientations).forEach(function (key) {
						var arr = plist['UISupportedInterfaceOrientations' + (key == 'ipad' ? '~ipad' : '')] = [];
						iphone.orientations[key].forEach(function (name) {
							arr.push(name);
						});
					});
				}
				
				if (iphone.backgroundModes) {
					plist.UIBackgroundModes = [].concat(iphone.backgroundModes);
				}
				
				if (iphone.requires) {
					plist.UIRequiredDeviceCapabilities = [].concat(iphone.requiredFeatures);
				}
				
				if (iphone.types) {
					var types = plist.CFBundleDocumentTypes = [];
					iphone.types.forEach(function (type) {
						types.push({
							CFBundleTypeName: type.name,
							CFBundleTypeIconFiles: type.icon,
							LSItemContentTypes: type.uti,
							LSHandlerRank: type.owner ? 'Owner' : 'Alternate'
						});
					});
				}
			}
			
			ios && ios.plist && Object.keys(ios.plist).forEach(function (prop) {
				if (!/^\+/.test(prop)) {
					plist[prop] = ios.plist[prop];
				}
			});
			
			plist.CFBundleIdentifier = this.tiapp.id;
			plist.CFBundleVersion = appc.version.format(this.tiapp.version || 1, 3);
			plist.CFBundleShortVersionString = appc.version.format(this.tiapp.version || 1, 3, 3);
			
			plist.CFBundleIconFiles = [];
			['.png', '@2x.png', '-72.png', '-Small-50.png', '-72@2x.png', '-Small-50@2x.png', '-Small.png', '-Small@2x.png'].forEach(function (name) {
				name = iconName + name;
				if (afs.exists(this.projectDir, 'Resources', name) ||
					afs.exists(this.projectDir, 'Resources', 'iphone', name) ||
					afs.exists(this.projectDir, 'Resources', this.platformName, name)) {
					plist.CFBundleIconFiles.push(name);
				}
			}, this);
			
			fs.writeFileSync(this.buildDir + '/Info.plist', plist.toString('xml'));
		}
	},
	
	createXcodeProject: function () {
		var xcodeDir = path.join(this.buildDir, this.tiapp.name + '.xcodeproj'),
			namespace = scrubName(this.tiapp.name),
			copyFileRegExps = [
				// note: order of regexps matters
				[/TitaniumModule/g, namespace + '$Module'],
				[/Titanium|Appcelerator/g, namespace],
				[/titanium/g, '_' + namespace.toLowerCase()],
				[new RegExp(namespace + '(' + namespace + '\\$?Module)', 'g'), '$1'],
				[/(org|com)\.appcelerator/g, '$1.' + namespace.toLowerCase()],
				[new RegExp('\\* ' + namespace + ' ' + namespace + ' Mobile', 'g'), '* Appcelerator Titanium Mobile'],
				[new RegExp('\\* Copyright \\(c\\) \\d{4}(-\\d{4})? by ' + namespace + ', Inc\\.', 'g'), '* Copyright (c) 2009-' + (new Date).getFullYear() + ' by Appcelerator, Inc.'],
				[/(\* Please see the LICENSE included with this distribution for details.\n)(?! \*\s*\* WARNING)/g, '$1 * \n * WARNING: This is generated code. Modify at your own risk and without support.\n']
			],
			extRegExp = /\.(c|cpp|h|m|mm|pbxproj)$/,
			copyOpts = {
				preserve: true,
				logger: this.logger.debug,
				ignoreDirs: ['.git','.svn', 'CVS'],
				ignoreFiles: ['.gitignore', '.cvsignore', 'bridge.txt', 'libTitanium.a'],
				callback: function (src, dest, contents, logger) {
					if (extRegExp.test(src) && src.indexOf('TiCore') == -1) {
						logger && logger(__('Processing %s', src.cyan));
						for (var i = 0, l = copyFileRegExps.length; i < l; i++) {
							contents = contents.replace(copyFileRegExps[i][0], copyFileRegExps[i][1]);
						}
					}
					return contents;
				}
			};
		
		this.logger.info(__('Copying Xcode iOS files'));
		['Classes', 'headers', 'Resources'].forEach(function (dir) {
			afs.copyDirSyncRecursive(
				path.join(this.titaniumIosSdkPath, dir),
				path.join(this.buildDir, dir),
				copyOpts
			);
		}, this);
		
		afs.copyFileSync(path.join(this.titaniumIosSdkPath, this.platformName, 'Titanium_Prefix.pch'), path.join(this.buildDir, this.tiapp.name + '_Prefix.pch'), { logger: this.logger.debug });
		
		this.logger.info(__('Creating Xcode project directory: %s', xcodeDir.cyan));
		wrench.mkdirSyncRecursive(xcodeDir);
		
		function injectCompileShellScript(str, sectionName, shellScript) {
			var p = 0;
			while (p != -1) {
				p = str.indexOf('name = "' + sectionName + '"', p);
				if (p != -1) {
					p = str.indexOf('shellScript = ', p);
					if (p != -1) {
						str = str.substring(0, p) + 'shellScript = "' + shellScript + '";' + str.substring(str.indexOf('\n', p));
					}
				}
			}
			return str;
		}
		
		this.logger.info(__('Writing Xcode project data file: %s', 'Titanium.xcodeproj/project.pbxproj'.cyan));
		var proj = fs.readFileSync(path.join(this.titaniumIosSdkPath, this.platformName, 'Titanium.xcodeproj', 'project.pbxproj')).toString();
		proj = proj.replace(/\.\.\/Classes/g, 'Classes')
			.replace(/\.\.\/Resources/g, 'Resources')
			.replace(/\.\.\/headers/g, 'headers')
			.replace(/\.\.\/lib/g, 'lib')
			.replace(/Titanium\.plist/g, 'Info.plist')
			.replace(/Titanium\-KitchenSink/g, this.tiapp.name)
			.replace(/path \= Titanium.app;/g, 'path = "' + this.tiapp.name + '.app";')
			.replace(/Titanium.app/g, this.tiapp.name + '.app')
			.replace(/PRODUCT_NAME \= ['"]?Titanium(-iPad|-universal)?['"]?/g, 'PRODUCT_NAME = "' + this.tiapp.name + '$1"') // note: there are no PRODUCT_NAMEs with -iPad and -univeral
			.replace(/path \= Titanium_Prefix\.pch;/g, 'path = "' + this.tiapp.name + '_Prefix.pch";')
			.replace(/GCC_PREFIX_HEADER \= Titanium_Prefix\.pch;/g, 'GCC_PREFIX_HEADER = "' + this.tiapp.name + '_Prefix.pch";')
			.replace(/Titanium_Prefix\.pch/g, this.tiapp.name + '_Prefix.pch')
			.replace(/Titanium/g, namespace);
		
		proj = injectCompileShellScript(proj, 'Pre-Compile', '\\"' + this.cli.argv.$0.replace(/^node /, '') + '\\" build --platform ' + this.platformName + ' --sdk ' + this.titaniumSdkVersion + ' --xcode\\nexit $?')
		proj = injectCompileShellScript(proj, 'Post-Compile', "echo 'post-compile'")
		fs.writeFileSync(path.join(this.buildDir, this.tiapp.name + '.xcodeproj', 'project.pbxproj'), proj);
		
		this.logger.info(__('Writing Xcode project configuration: %s', 'project.xcconfig'.cyan));
		fs.writeFileSync(this.xcodeProjectConfigFile, [
			'TI_VERSION=' + this.titaniumSdkVersion,
			'TI_SDK_DIR=' + this.titaniumIosSdkPath.replace(this.titaniumSdkVersion, '$(TI_VERSION)'),
			'TI_APPID=' + this.tiapp.id,
			'OTHER_LDFLAGS[sdk=iphoneos*]=$(inherited) -weak_framework iAd',
			'OTHER_LDFLAGS[sdk=iphonesimulator*]=$(inherited) -weak_framework iAd',
			'#include "module"'
		].join('\n') + '\n');
		
		this.logger.info(__('Writing Xcode module configuration: %s', 'module.xcconfig'.cyan));
		fs.writeFileSync(path.join(this.buildDir, 'module.xcconfig'), '// this is a generated file - DO NOT EDIT\n\n');
	},
	
	checkForceRebuild: function (versionIosSdkPath, versionGuid, versionTiCoreHash, versionGitHash, libTiCoreHash) {
		if (this.cli.argv.force) {
			this.logger.debug(__('Forcing rebuild: %s flag was set', '--force'.cyan));
			return true;
		}
		
		if (!afs.exists(this.buildVersionFile)) {
			// if no .version file, rebuild!
			this.logger.debug(__('Forcing rebuild: %s does not exist', this.buildVersionFile.cyan));
			return true;
		}
		
		if (afs.exists(this.xcodeProjectConfigFile)) {
			// we have a previous build, see if the Titanium SDK changed
			var m = fs.readFileSync(this.xcodeProjectConfigFile).toString().match(/TI_VERSION\=([^\n]*)/);
			if (m && !appc.version.eq(m[1], this.titaniumSdkVersion)) {
				this.logger.debug(__("Forcing rebuild: last build was under Titanium SDK version %s and we're compiling for version %s", m[1].cyan, this.titaniumSdkVersion.cyan));
				return true;
			}
		}
		
		if (!afs.exists(this.xcodeAppDir)) {
			this.logger.debug(__('Forcing rebuild: %s does not exist', this.xcodeAppDir.cyan));
			return true;
		}
		
		// check that we have a libTiCore hash
		if (!versionTiCoreHash) {
			this.logger.debug(__('Forcing rebuild: incomplete version file %s', this.buildVersionFile.cyan));
			return true;
		}
		
		// check if the libTiCore hashes are different
		if (libTiCoreHash != versionTiCoreHash) {
			this.logger.debug(__('Forcing rebuild: libTiCore hash changed since last build'));
			this.logger.debug('  ' + __('Was: %s', versionTiCoreHash));
			this.logger.debug('  ' + __('Now: %s', libTiCoreHash));
			return true;
		}
		
		// check if the titanium sdk paths are different
		if (versionIosSdkPath != this.titaniumIosSdkPath) {
			this.logger.debug(__('Forcing rebuild: Titanium SDK path changed since last build'));
			this.logger.debug('  ' + __('Was: %s', versionIosSdkPath));
			this.logger.debug('  ' + __('Now: %s', this.titaniumIosSdkPath));
			return true;
		}
		
		// check the git hashes are different
		if (!versionGitHash || versionGitHash != ti.manifest.githash) {
			this.logger.debug(__('Forcing rebuild: githash changed since last build'));
			this.logger.debug('  ' + __('Was: %s', versionGitHash));
			this.logger.debug('  ' + __('Now: %s', this.lib.manifest.githash));
			return true;
		}
		
		return false;
	}

};