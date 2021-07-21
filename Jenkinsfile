#!groovy
library 'pipeline-library'

// Some branch flags to alter behavior
def isPR = env.CHANGE_ID || false // CHANGE_ID is set if this is a PR. (We used to look whether branch name started with PR-, which would not be true for a branch from origin filed as PR)
def MAINLINE_BRANCH_REGEXP = /master|next|\d+_\d_(X|\d)/ // a branch is considered mainline if 'master' or like: 6_2_X, 7_0_X, 6_2_1
def isMainlineBranch = (env.BRANCH_NAME ==~ MAINLINE_BRANCH_REGEXP)

// Keep logs/reports/etc of last 30 builds, only keep build artifacts of last 3 builds
properties([buildDiscarder(logRotator(numToKeepStr: '30', artifactNumToKeepStr: '3'))])

// These values could be changed manually on PRs/branches, but be careful we don't merge the changes in. We want this to be the default behavior for now!
// target branch of test suite to test with
def runDanger = isPR // run Danger.JS if it's a PR by default. (should we also run on origin branches that aren't mainline?)
def publishToS3 = isMainlineBranch // publish zips to S3 if on mainline branch, by default
def testOnDevices = isMainlineBranch // run tests on devices
def testOnAndroidDevices = false // testOnDevices // FIXME: Our android device in CI is gone for now!
def testOnIOSDevices = false // testOnDevices // FIXME: Our iphone device in CI is gone for now!

// Variables we can change
def nodeVersion = '12.18.0' // NOTE that changing this requires we set up the desired version on jenkins master first!
def npmVersion = 'latest' // We can change this without any changes to Jenkins. 5.7.1 is minimum to use 'npm ci'

// Variables which we assign and share between nodes
// Don't modify these yourself, these are generated during the build
def gitCommit = ''
def basename = ''
def vtag = ''

@NonCPS
def hasAPIDocChanges() {
	// https://javadoc.jenkins-ci.org/hudson/scm/ChangeLogSet.html
    def changeLogSets = currentBuild.changeSets
    for (int i = 0; i < changeLogSets.size(); i++) {
        def entries = changeLogSets[i].items
        for (int j = 0; j < entries.size(); j++) {
            def entry = entries[j]
			if (entry.msg.contains('[skip ci]')) {
				echo "skipping commit: ${entry.msg}"
				continue; // skip this commit
			}
			// echo "checking commit: ${entry.msg}"
            def paths = entry.affectedPaths
			for (int k = 0; k < paths.size(); k++) {
				def path = paths[k]
				if (path.startsWith('apidoc/')) {
					return true
				}
			}
        }
    }
	return false
}

def getBuiltSDK() {
	// Unarchive the osx build of the SDK (as a zip)
	sh 'rm -rf osx.zip' // delete osx.zip file if it already exists
	unarchive mapping: ['dist/mobilesdk-*-osx.zip': 'osx.zip'] // grab the osx zip from our current build
	return sh(returnStdout: true, script: 'ls osx.zip/dist/mobilesdk-*-osx.zip').trim()
}

def gatherAndroidCrashReports() {
	// gather crash reports/tombstones for Android
	timeout(5) {
		sh label: 'gather crash reports/tombstones for Android', returnStatus: true, script: './tests/adb-all.sh pull /data/tombstones'
		archiveArtifacts allowEmptyArchive: true, artifacts: 'tombstones/'
		sh returnStatus: true, script: 'rm -rf tombstones/'
		// wipe tombstones and re-build dir with proper permissions/ownership on emulator
		sh returnStatus: true, script: './tests/adb-all.sh shell rm -rf /data/tombstones'
		sh returnStatus: true, script: './tests/adb-all.sh shell mkdir -m 771 /data/tombstones'
		sh returnStatus: true, script: './tests/adb-all.sh shell chown system:system /data/tombstones'
	}
}

def androidUnitTests(testName, nodeVersion, npmVersion, testOnDevices, deviceId) {
	return {
		def labels = 'git && osx && android-emulator && android-sdk' // FIXME get working on windows/linux!
		if (testOnDevices) {
			labels += ' && macos-rocket' // run main branch tests on devices, use node with devices connected
		}
		if (!deviceId) {
			deviceId = testOnDevices ? 'all' : 'android-30-playstore-x86';
		}

		node(labels) {
			// TODO: Do a shallow checkout rather than stash/unstash?
			unstash 'mocha-tests'
			try {
				nodejs(nodeJSInstallationName: "node ${nodeVersion}") {
					ensureNPM(npmVersion)
					sh 'npm ci'
					def zipName = getBuiltSDK()
					sh label: 'Install SDK', script: "npm run deploy -- ${zipName} --select" // installs the sdk
					try {
						withEnv(['CI=1']) {
							timeout(30) {
								// Forcibly remove value for specific build tools version to use (set by module builds)
								sh returnStatus: true, script: 'ti config android.buildTools.selectedVersion --remove'
								// run main branch tests on devices
								if (testOnDevices) {
									sh label: 'Run Test Suite on device(s)', script: "npm run test:integration -- android -T device -C ${deviceId}"
								// run PR tests on emulator
								} else {
									sh label: 'Run Test Suite on emulator', script: "npm run test:integration -- android -T emulator -D test -C ${deviceId}"
								}
							} // timeout
						}
					} catch (e) {
						archiveArtifacts 'tmp/mocha/build/build_*.log' // save build log if build failed
						gatherAndroidCrashReports()
						throw e
					} finally {
						try {
							// Kill the app and emulators!
							timeout(5) {
								sh returnStatus: true, script: './tests/adb-all.sh shell am force-stop com.appcelerator.testApp.testing'
								sh returnStatus: true, script: './tests/adb-all.sh uninstall com.appcelerator.testApp.testing'
							}
							killAndroidEmulators()
						} finally {
							sh 'npm run clean:sdks' // remove non-GA sdks
							sh 'npm run clean:modules' // remove modules
						}
					} // try/catch/finally
					// save the junit reports as artifacts explicitly so danger.js can use them later
					stash includes: 'junit.*.xml', name: "test-report-android-${testName}"
					junit 'junit.*.xml'
					archiveArtifacts allowEmptyArchive: true, artifacts: 'tests/diffs/,tests/generated/'
				} // nodejs
			} finally {
				deleteDir()
			}
		} // node
	}
}

def macosUnitTests(nodeVersion, npmVersion) {
	return {
		node('git && osx && xcode-12 && osx-10.15') {
			// TODO: Do a shallow checkout rather than stash/unstash?
			unstash 'mocha-tests'
			try {
				nodejs(nodeJSInstallationName: "node ${nodeVersion}") {
					ensureNPM(npmVersion)
					sh 'npm ci'
					def zipName = getBuiltSDK()
					sh label: 'Install SDK', script: "npm run deploy -- ${zipName} --select" // installs the sdk
					try {
						withEnv(['CI=1']) {
							timeout(20) {
								sh label: 'Run Test Suite on macOS', script: 'npm run test:integration -- ios -T macos'
							}
						}
					} catch (e) {
						gatherIOSCrashReports('mocha') // app name is mocha
						throw e
					} finally {
						sh 'npm run clean:sdks' // remove non-GA sdks
						sh 'npm run clean:modules' // remove modules
					}
					// save the junit reports as artifacts explicitly so danger.js can use them later
					stash includes: 'junit.ios.macos.xml', name: "test-report-ios-macos"
					junit 'junit.ios.macos.xml'
					// Save any diffed images
					archiveArtifacts allowEmptyArchive: true, artifacts: 'tests/diffs/,tests/generated/'
				} // nodejs
			} finally {
				deleteDir()
			}
		}
	}
}

def iosUnitTests(deviceFamily, nodeVersion, npmVersion, testOnDevices) {
	return {
		def labels = 'git && osx'
		if (testOnDevices && deviceFamily == 'iphone') {
			labels += ' && macos-darwin' // run main branch tests on devices, use node with devices connected
		} else {
			labels += '&& xcode-12' // Use xcode-12 to make use of ios 14 APIs
		}
		node(labels) {
			// TODO: Do a shallow checkout rather than stash/unstash?
			unstash 'mocha-tests'
			try {
				nodejs(nodeJSInstallationName: "node ${nodeVersion}") {
					ensureNPM(npmVersion)
					sh 'npm ci'
					def zipName = getBuiltSDK()
					sh label: 'Install SDK', script: "npm run deploy -- ${zipName} --select" // installs the sdk
					try {
						withEnv(['CI=1']) {
							timeout(40) {
								if (testOnDevices && deviceFamily == 'iphone') {
									sh label: 'Run Test Suite on device(s)', script: "npm run test:integration -- ios -F ${deviceFamily} -T device -C all"
								} else { // run PR tests on simulator
									sh label: 'Run Test Suite on simulator', script: "npm run test:integration -- ios -F ${deviceFamily}"
								}
							}
						}
					} catch (e) {
						archiveArtifacts 'tmp/mocha/build/build_*.log' // save build log if build failed
						gatherIOSCrashReports('mocha') // app name is mocha
						throw e
					} finally {
						sh 'npm run clean:sdks' // remove non-GA sdks
						sh 'npm run clean:modules' // remove modules
					}
					// save the junit reports as artifacts explicitly so danger.js can use them later
					stash includes: 'junit.ios.*.xml', name: "test-report-ios-${deviceFamily}"
					junit 'junit.ios.*.xml'
					// Save any diffed images
					archiveArtifacts allowEmptyArchive: true, artifacts: 'tests/diffs/,tests/generated/'
				} // nodejs
			} finally {
				deleteDir()
			}
		}
	}
}

def cliUnitTests(nodeVersion, npmVersion) {
	return {
		node('git && osx') { // ToDo: refactor to try and run across mac, linux, and windows?
			unstash 'cli-unit-tests'
			nodejs(nodeJSInstallationName: "node ${nodeVersion}") {
				ensureNPM(npmVersion)
				sh 'npm ci'
				try {
					sh 'npm run test:cli'
				} finally {
					if (fileExists('coverage/cobertura-coverage.xml')) {
						step([$class: 'CoberturaPublisher', autoUpdateHealth: false, autoUpdateStability: false, coberturaReportFile: 'coverage/cobertura-coverage.xml', failUnhealthy: false, failUnstable: false, maxNumberOfBuilds: 0, onlyStable: false, sourceEncoding: 'ASCII', zoomCoverageChart: false])
					}
					stash includes: 'junit.cli.report.xml', name: 'test-report-cli'
					junit 'junit.cli.report.xml'
				}
			}
		}
	}
}

// Wrap in timestamper
timestamps {
	try {
		node('git && android-sdk && ant && gperf && osx && xcode-12 && osx-10.15') {
			stage('Checkout') {
				// Update our shared reference repo for all branches/PRs
				dir('..') {
					if (fileExists('titanium_mobile.git')) {
						dir('titanium_mobile.git') {
							sh 'git gc'
							sh 'git remote update -p' // update the clone
							sh 'git prune' // prune to avoid "warning: There are too many unreachable loose objects"
						}
					} else {
						sh 'git clone --mirror git@github.com:appcelerator/titanium_mobile.git' // create a mirror
					}
				}

				// checkout scm
				// Hack for JENKINS-37658 - see https://support.cloudbees.com/hc/en-us/articles/226122247-How-to-Customize-Checkout-for-Pipeline-Multibranch
				checkout([
					$class: 'GitSCM',
					branches: scm.branches,
					extensions: scm.extensions + [
						[$class: 'WipeWorkspace'],
						[$class: 'CloneOption', honorRefspec: true, noTags: true, reference: "${pwd()}/../titanium_mobile.git", shallow: true, depth: 30, timeout: 30]],
					userRemoteConfigs: scm.userRemoteConfigs
				])
				// FIXME: Workaround for missing env.GIT_COMMIT: http://stackoverflow.com/questions/36304208/jenkins-workflow-checkout-accessing-branch-name-and-git-commit
				gitCommit = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
			}

			nodejs(nodeJSInstallationName: "node ${nodeVersion}") {

				stage('Lint') {
					ensureNPM(npmVersion)

					// Install dependencies
					timeout(5) {
						sh 'npm ci'
					}
					// Run npm test, but record output in a file and check for failure of command by checking output
					if (fileExists('npm_test.log')) {
						sh 'rm -rf npm_test.log'
					}
					// forcibly grab and set correct value for android sdk path by grabbing from node we're actually building on (using env.ANDROID_SDK will pick up master node's env value!)
					def androidSDK = env.ANDROID_SDK
					withEnv(['ANDROID_SDK=']) {
					    try {
							androidSDK = sh(returnStdout: true, script: 'printenv ANDROID_SDK').trim()
						} catch (e) {
							// squash, env var not set at OS-level
						}
					}
					def npmTestResult = sh(returnStatus: true, script: "ANDROID_SDK_ROOT=${androidSDK} npm test &> npm_test.log")
					recordIssues(tools: [checkStyle(pattern: 'android/**/build/reports/checkstyle/checkJavaStyle.xml')])
					if (runDanger) { // Stash files for danger.js later
						stash includes: 'package.json,package-lock.json,dangerfile.js,.eslintignore,.eslintrc,npm_test.log,android/**/*.java', name: 'danger'
					}
					stash includes: 'package.json,package-lock.json,android/cli/**,iphone/cli/**,build/**', name: 'cli-unit-tests'
					stash includes: 'package.json,package-lock.json,tests/**,build/**', name: 'mocha-tests'
					// was it a failure?
					if (npmTestResult != 0) {
						error readFile('npm_test.log')
					} else if (env.BRANCH_NAME.equals('master') && hasAPIDocChanges()) {
						// if we have a master branch build of SDK with updated apidocs, trigger a new doc site build
						build job: 'docs/doctools/docs', wait: false
					}
				}

				stage('Build') {
					// Normal build, pull out the version
					def version = sh(returnStdout: true, script: 'sed -n \'s/^ *"version": *"//p\' package.json | tr -d \'"\' | tr -d \',\'').trim()
					echo "VERSION:         ${version}"
					// Create a timestamp
					def timestamp = sh(returnStdout: true, script: 'date +\'%Y%m%d%H%M%S\'').trim()
					echo "TIMESTAMP:       ${timestamp}"
					vtag = "${version}.v${timestamp}"
					echo "VTAG:            ${vtag}"
					basename = "dist/mobilesdk-${vtag}"
					echo "BASENAME:        ${basename}"

					ansiColor('xterm') {
						timeout(15) {
							def buildCommand = 'npm run clean'
							if (isMainlineBranch) {
								buildCommand += ' -- --all'
							}
							sh label: 'clean', script: buildCommand
						} // timeout
						timeout(15) {
							def buildCommand = 'npm run build'
							if (isMainlineBranch) {
								buildCommand += ' -- --all'
							}
							try {
								withEnv(["JAVA_HOME=${tool name:'OpenJDK 11.0.11+9', type: 'jdk'}"]) {
									sh label: 'build', script: buildCommand
								}
							} finally {
								recordIssues(tools: [clang(), java()])
							}
						} // timeout
						timeout(25) {
							def packageCommand = "npm run package -- --version-tag ${vtag}"
							if (isMainlineBranch) {
								// on mainline builds, build for all 3 host OSes
								packageCommand += ' --all'
							} else {
								// On PRs, just build android and ios for macOS
								packageCommand += ' android ios'
							}
							sh label: 'package', script: packageCommand
						} // timeout
					} // ansiColor

					archiveArtifacts artifacts: "${basename}-*.zip"
					stash includes: 'dist/parity.html', name: 'parity'
				} // end 'Build' stage
			} // nodeJs
		} // end node for checkout/build

		// Run unit tests in parallel for android/iOS
		stage('Test') {
			parallel(
				'android main unit tests': androidUnitTests('main', nodeVersion, npmVersion, testOnAndroidDevices, null),
				'android 5.0 unit tests': androidUnitTests('5.0', nodeVersion, npmVersion, false, 'android-21-x86'),
				'iPhone unit tests': iosUnitTests('iphone', nodeVersion, npmVersion, testOnIOSDevices),
				'iPad unit tests': iosUnitTests('ipad', nodeVersion, npmVersion, testOnIOSDevices),
				'macOS unit tests': macosUnitTests(nodeVersion, npmVersion),
				'cli unit tests': cliUnitTests(nodeVersion, npmVersion),
				failFast: false
			)
		}

		stage('Deploy') {
			if (publishToS3) {
				// Now allocate a node for uploading artifacts to s3 and in Jenkins
				node('(osx || linux) && !axway-internal && curl') {
					def indexJson = []
					if (fileExists('index.json')) {
						sh 'rm -f index.json'
					}
					try {
						sh "curl -O http://builds.appcelerator.com.s3.amazonaws.com/mobile/${env.BRANCH_NAME}/index.json"
					} catch (err) {
						// ignore? Not able to grab the index.json, so assume it means it's a new branch
					}
					if (fileExists('index.json')) {
						def contents = readFile('index.json')
						if (!contents.startsWith('<?xml')) { // May be an 'Access denied' xml file/response
							indexJson = jsonParse(contents)
						} else {
							// we get access denied if it doesn't exist! Let's treat that as us needing to add branch to branches.json listing
							try {
								sh 'curl -O http://builds.appcelerator.com.s3.amazonaws.com/mobile/branches.json'
								if (fileExists('branches.json')) {
									def branchesJSONContents = readFile('branches.json')
									if (!branchesJSONContents.startsWith('<?xml')) { // May be an 'Access denied' xml file/response
										def branchesJSON = jsonParse(branchesJSONContents)
										if (!(branchesJSON['branches'].contains(env.BRANCH_NAME))) {
											// Update the branches.json on S3
											echo 'updating mobile/branches.json to include new branch...'
											branchesJSON['branches'] << env.BRANCH_NAME
											writeFile file: 'branches.json', text: new groovy.json.JsonBuilder(branchesJSON).toPrettyString()
											step([
												$class: 'S3BucketPublisher',
												consoleLogLevel: 'INFO',
												entries: [[
													bucket: 'builds.appcelerator.com/mobile',
													gzipFiles: false,
													selectedRegion: 'us-east-1',
													sourceFile: 'branches.json',
													uploadFromSlave: true,
													userMetadata: []
												]],
												profileName: 'builds.appcelerator.com',
												pluginFailureResultConstraint: 'FAILURE',
												userMetadata: []])
										}
									}
								}
							} catch (err) {
								// ignore? Not able to grab the branches.json, what should we assume? In 99.9% of the cases, it's not a new build
							}
						}
					}

					// unarchive zips
					sh 'rm -rf dist/'
					unarchive mapping: ['dist/': '.']
					// Have to use Java-style loop for now: https://issues.jenkins-ci.org/browse/JENKINS-26481
					def oses = ['osx', 'linux', 'win32']
					for (int i = 0; i < oses.size(); i++) {
						def os = oses[i]
						def sha1 = sh(returnStdout: true, script: "shasum ${basename}-${os}.zip").trim().split()[0]
						def filesize = Long.valueOf(sh(returnStdout: true, script: "wc -c < ${basename}-${os}.zip").trim())
						step([
							$class: 'S3BucketPublisher',
							consoleLogLevel: 'INFO',
							entries: [[
								bucket: "builds.appcelerator.com/mobile/${env.BRANCH_NAME}",
								gzipFiles: false,
								selectedRegion: 'us-east-1',
								sourceFile: "${basename}-${os}.zip",
								uploadFromSlave: true,
								userMetadata: [[key: 'sha1', value: sha1]]
							]],
							profileName: 'builds.appcelerator.com',
							pluginFailureResultConstraint: 'FAILURE',
							userMetadata: [
								[key: 'build_type', value: 'mobile'],
								[key: 'git_branch', value: env.BRANCH_NAME],
								[key: 'git_revision', value: gitCommit],
								[key: 'build_url', value: env.BUILD_URL]]
						])

						// Add the entry to index json!
						indexJson << [
							'filename': "mobilesdk-${vtag}-${os}.zip",
							'git_branch': env.BRANCH_NAME,
							'git_revision': gitCommit,
							'build_url': env.BUILD_URL,
							'build_type': 'mobile',
							'sha1': sha1,
							'size': filesize
						]
					}

					// Update the index.json on S3
					echo "updating mobile/${env.BRANCH_NAME}/index.json..."
					writeFile file: "index.json", text: new groovy.json.JsonBuilder(indexJson).toPrettyString()
					step([
						$class: 'S3BucketPublisher',
						consoleLogLevel: 'INFO',
						entries: [[
							bucket: "builds.appcelerator.com/mobile/${env.BRANCH_NAME}",
							gzipFiles: false,
							selectedRegion: 'us-east-1',
							sourceFile: 'index.json',
							uploadFromSlave: true,
							userMetadata: []
						]],
						profileName: 'builds.appcelerator.com',
						pluginFailureResultConstraint: 'FAILURE',
						userMetadata: []])

					// Upload the parity report
					unstash 'parity'
					sh "mv dist/parity.html ${basename}-parity.html"
					step([
						$class: 'S3BucketPublisher',
						consoleLogLevel: 'INFO',
						entries: [[
							bucket: "builds.appcelerator.com/mobile/${env.BRANCH_NAME}",
							gzipFiles: false,
							selectedRegion: 'us-east-1',
							sourceFile: "${basename}-parity.html",
							uploadFromSlave: true,
							userMetadata: []
						]],
						profileName: 'builds.appcelerator.com',
						pluginFailureResultConstraint: 'FAILURE',
						userMetadata: []])

					// Now wipe the workspace. otherwise the unstashed artifacts will stick around on the node (master)
					deleteDir()
				} // node
			} // if(publishToS3)
		} // stage
	} // try
	catch (err) {
		// TODO Use try/catch at lower level (like around tests) so we can give more detailed failures?
		currentBuild.result = 'FAILURE'

		throw err
	}
	finally {
		// Run Danger.JS at the end so we can provide useful comments/info to the PR/commit author
		if (runDanger) {
			stage('Danger') {
				node('osx || linux') {
					nodejs(nodeJSInstallationName: "node ${nodeVersion}") {
						unstash 'danger' // this gives us dangerfile.js, package.json, package-lock.json, node_modules/, android java sources for format check

						// ok to not grab crash logs, still run Danger.JS
						try {
							unarchive mapping: ['mocha_*.crash': '.'] // unarchive any iOS simulator crashes
						} catch (e) {}

						// it's ok to not grab all test results, still run Danger.JS (even if some platforms crashed or we failed before tests)
						def reports = [ 'ios-ipad', 'ios-iphone', 'ios-macos', 'android-main', 'android-5.0', 'cli' ]
						for (int i = 0; i < reports.size(); i++) {
							try {
								unstash "test-report-${reports[i]}"
							} catch (e) {}
						}

						ensureNPM(npmVersion)
						sh 'npm ci'
						// FIXME We need to hack the env vars for Danger.JS because it assumes Github Pull Request Builder plugin only
						// We use Github branch source plugin implicitly through pipeline job
						// See https://github.com/danger/danger-js/issues/379
						withEnv(["ZIPFILE=${basename}-osx.zip", "BUILD_STATUS=${currentBuild.currentResult}","DANGER_JS_APP_INSTALL_ID=''"]) {
							// FIXME Can't pass along env variables properly, so we cheat and write them as a JSON file we can require
							sh 'node -p \'JSON.stringify(process.env)\' > env.json'
							sh returnStatus: true, script: 'npx danger ci --verbose' // Don't fail build if danger fails. We want to retain existing build status.
						} // withEnv
					} // nodejs
					deleteDir()
				} // node
			} // Danger stage
		} // if(runDanger)
	}
}
