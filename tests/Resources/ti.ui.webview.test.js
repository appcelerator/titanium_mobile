/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
/* globals OS_ANDROID,OS_VERSION_MAJOR */
/* eslint-env mocha */
/* eslint no-unused-expressions: "off" */
'use strict';
const should = require('./utilities/assertions');
const utilities = require('./utilities/utilities');

describe.androidARM64Broken('Titanium.UI.WebView', function () {
	this.slow(3000);
	this.timeout(30000);

	let win;
	afterEach(done => { // fires after every test in sub-suites too...
		if (win && !win.closed) {
			win.addEventListener('close', function listener () {
				win.removeEventListener('close', listener);
				win = null;
				done();
			});
			win.close();
		} else {
			win = null;
			done();
		}
	});

	// FIXME: I think we need to tweak the test here. Set URL property after adding the listeners!
	// iOS works most of the time, but also has some odd failures sometimes. SDK 8+ is reworking this.
	it.allBroken('loading', function (finish) {
		this.slow(5000);
		this.timeout(10000);

		win = Ti.UI.createWindow();
		const webView = Ti.UI.createWebView({
			url: 'https://www.google.com'
		});

		should(webView.loading).be.a.Boolean();
		should(webView.loading).be.false();

		let beforeLoaded = false;
		webView.addEventListener('beforeload', function () {
			if (beforeLoaded === false) {
				try {
					should(webView.loading).be.a.Boolean();
					should(webView.loading).be.false();

					// Use this flag for our test, because "beforeload" also fires for resources
					// inside the web-page (e.g. scripts), so this particular test may fail due
					// to recurring triggers of this event.
					beforeLoaded = true;
				} catch (err) {
					return finish(err);
				}
			}
		});

		webView.addEventListener('load', function () {
			try {
				should(webView.loading).be.a.Boolean();
				should(webView.loading).be.false();
			} catch (err) {
				return finish(err);
			}
			finish();
		});

		win.add(webView);
		win.open();
	});

	it('url', function (finish) {
		win = Ti.UI.createWindow({
			backgroundColor: 'blue'
		});
		const webview = Ti.UI.createWebView();

		win.addEventListener('focus', function () {
			try {
				webview.url = 'https://www.google.com';
			} catch (err) {
				return finish(err);
			}
			finish();
		});

		win.add(webview);
		win.open();
	});

	it.ios('.keyboardDisplayRequiresUserAction', function (finish) {
		win = Ti.UI.createWindow();
		const webView = Ti.UI.createWebView();

		win.addEventListener('focus', function () {
			try {
				webView.keyboardDisplayRequiresUserAction = true;

				should(webView.keyboardDisplayRequiresUserAction).be.a.Boolean();
				should(webView.keyboardDisplayRequiresUserAction).be.true();

				webView.keyboardDisplayRequiresUserAction = false;

				should(webView.keyboardDisplayRequiresUserAction).be.a.Boolean();
				should(webView.keyboardDisplayRequiresUserAction).be.false();
			} catch (err) {
				return finish(err);
			}
			finish();
		});

		win.add(webView);
		win.open();
	});

	// FIXME Times out on Android build machine. No idea why... Must be we never get focus event?
	it.androidBroken('url(local)', function (finish) {
		win = Ti.UI.createWindow({
			backgroundColor: 'blue'
		});
		const webview = Ti.UI.createWebView();

		win.addEventListener('focus', function () {
			try {
				webview.url = 'ti.ui.webview.test.html';
			} catch (err) {
				return finish(err);
			}
			finish();
		});

		win.add(webview);
		win.open();
	});

	// TIMOB-23542 webview data test
	it('.data', function (finish) {
		win = Ti.UI.createWindow({
			backgroundColor: 'blue'
		});
		const blob = Ti.Filesystem.getFile('app.js').read();
		const webview = Ti.UI.createWebView({
			data: blob
		});

		webview.addEventListener('load', function () {
			try {
				should(webview.data).be.an.object;
			} catch (err) {
				return finish(err);
			}
			finish();
		});
		win.add(webview);
		win.open();
	});

	// Skip this on desktop Windows apps because it crashes the app now. - Works fine locally, to investigate EH
	// FIXME Parity issue! Windows require second argument which is callback function. Other platforms return value sync!
	// FIXME Android returns null?
	// FIXME Sometimes times out on iOS. Not really sure why...
	it.allBroken('evalJS', function (finish) {
		win = Ti.UI.createWindow({
			backgroundColor: 'blue'
		});

		const webview = Ti.UI.createWebView();

		let hadError = false;
		webview.addEventListener('load', function () {
			if (hadError) {
				return;
			}

			if (utilities.isWindows()) { // Windows requires an async callback function
				webview.evalJS('Ti.API.info("Hello, World!");"WebView.evalJS.TEST";', function (result) {
					try {
						should(result).be.eql('WebView.evalJS.TEST');
					} catch (err) {
						return finish(err);
					}
					finish();
				});
			} else { // other platforms return the result as result of function call!
				const result = webview.evalJS('Ti.API.info("Hello, World!");"WebView.evalJS.TEST";');
				try {
					should(result).be.eql('WebView.evalJS.TEST'); // Android reports null
				} catch (err) {
					return finish(err);
				}
				finish();
			}
		});
		win.addEventListener('focus', function () {
			try {
				webview.url = 'ti.ui.webview.test.html';
			} catch (err) {
				hadError = true;
				finish(err);
			}
		});

		win.add(webview);
		win.open();
	});

	describe.windows('ms-appx* urls', function () {
		this.timeout(10000);

		it('ms-appx:', function (finish) {
			var w,
				webview;

			w = Ti.UI.createWindow({
				backgroundColor: 'blue'
			});
			webview = Ti.UI.createWebView();

			webview.addEventListener('load', function () {
				w.close();
				finish();
			});
			w.addEventListener('open', function () {
				should(function () {
					webview.url = 'ms-appx:///ti.ui.webview.test.html';
				}).not.throw();
			});

			w.add(webview);
			w.open();
		});

		it('ms-appx-web:', function (finish) {
			var w,
				webview;

			w = Ti.UI.createWindow({
				backgroundColor: 'blue'
			});
			webview = Ti.UI.createWebView();

			webview.addEventListener('load', function () {
				w.close();
				finish();
			});
			w.addEventListener('open', function () {
				should(function () {
					webview.url = 'ms-appx-web:///ti.ui.webview.test.html';
				}).not.throw();
			});

			w.add(webview);
			w.open();
		});

		it('ms-appx-data:', function (finish) {
			var w,
				webview;

			function prepare(files) {
				var webroot = Ti.Filesystem.applicationDataDirectory + 'webroot',
					webroot_file = Ti.Filesystem.getFile(webroot),
					i,
					file,
					from,
					to;

				if (!webroot_file.exists()) {
					webroot_file.createDirectory();
				}
				for (i = 0; i < files.length; i++) {
					file = files[i];
					from = Ti.Filesystem.getFile(Ti.Filesystem.resourcesDirectory, file);
					to = webroot + Ti.Filesystem.separator + file;
					from.copy(to);
				}
			}

			w = Ti.UI.createWindow({
				backgroundColor: 'blue'
			});
			webview = Ti.UI.createWebView();
			webview.addEventListener('load', function () {
				w.close();
				finish();
			});
			w.addEventListener('open', function () {
				prepare([ 'ti.ui.webview.test.html' ]);
				should(function () {
					webview.url = 'ms-appdata:///local/webroot/ti.ui.webview.test.html';
				}).not.throw();
			});

			w.add(webview);
			w.open();
		});
	});

	it.windowsBroken('.userAgent', function (finish) {
		this.slow(15000);
		this.timeout(60000);

		if (OS_ANDROID && OS_VERSION_MAJOR < 6) { // unsure at what exact version this fails
			return finish();
		}

		const webView = Ti.UI.createWebView({
			userAgent: 'TEST AGENT',
			ignoreSslError: true // Older Android complains about the cert at this site!
		});
		const url = 'https://www.whatsmyua.info';
		let retry = 5;

		win = Ti.UI.createWindow({ backgroundColor: 'gray' });

		webView.addEventListener('load', function (e) {
			const html = e.source.html;
			const exp = /id="rawUa">rawUa: ([^<]+)<\/li/m.exec(html); // eslint-disable-line security/detect-child-process
			const userAgent = exp && exp.length > 1 ? exp[1] : undefined;
			if (userAgent && userAgent === webView.userAgent) {
				return finish();
			}
			if (retry--) {
				Ti.API.warn('could not obtain userAgent, retrying...');
				Ti.API.warn(html);
				setTimeout(() => webView.url = url, 3000);
			} else {
				return finish(new Error('invalid userAgent'));
			}
		});
		win.add(webView);
		webView.url = url;
		win.open();
	});

	// FIXME: temperamental on Android and broken on Windows
	it.androidAndWindowsBroken('.zoomLevel', function (finish) {
		this.slow(5000);
		this.timeout(10000);

		win = Ti.UI.createWindow();

		const webView = Ti.UI.createWebView({
			html: '<!DOCTYPE html><html><body><p>HELLO WORLD</p></body></html>'
		});

		webView.addEventListener('load', function () {
			try {
				should(webView.zoomLevel).be.a.Number();
				should(webView.zoomLevel).eql(1.0);
				setTimeout(function () {
					try {
						webView.zoomLevel = 3.0;
						should(webView.zoomLevel).eql(3.0);
						setTimeout(function () {
							try {
								webView.zoomLevel = 1.0;
								should(webView.zoomLevel).eql(1.0);
							} catch (e) {
								return finish(e);
							}
							finish();
						}, 500);
					} catch (e) {
						return finish(e);
					}
				}, 500);
			} catch (e) {
				return finish(e);
			}
		});

		win.add(webView);
		win.open();
	});

	it('#evalJS(string, function) - async variant', function (finish) {
		const webview = Ti.UI.createWebView();

		win = Ti.UI.createWindow({
			backgroundColor: 'blue'
		});

		webview.addEventListener('load', function () {

			// FIXME: Android is dumb and assumes no trailing semicolon!
			webview.evalJS('Ti.API.info("Hello, World!");"WebView.evalJS.TEST"', function (result) {
				try {
					if (utilities.isAndroid()) {
						should(result).be.eql('"WebView.evalJS.TEST"'); // FIXME: Why the double-quoting?
					} else {
						should(result).be.eql('WebView.evalJS.TEST');
					}
				} catch (err) {
					return finish(err);
				}
				finish();
			});
		});

		win.add(webview);
		win.open();

		webview.url = 'ti.ui.webview.test.html';
	});

	// FIXME: This crashes on device with iOS 13
	it.iosBroken('should handle file URLs with spaces in path - TIMOB-18765', function (finish) {
		// Should handle paths with spaces!
		const URL = Ti.Filesystem.resourcesDirectory + '/folder with spaces/comingSoon.html';
		const webView = Ti.UI.createWebView({
			top: 30
		});

		webView.addEventListener('error', function () {
			finish('Failed to load HTML file from URL with spaces in path');
		});

		webView.addEventListener('load', function (e) {
			try {
				if (utilities.isIOS()) {
					should(e.url).eql('file://' + Ti.Filesystem.resourcesDirectory + 'folder%20with%20spaces/comingSoon.html');
				}
				// TODO: Replace above iOS test with below once TIMOB-26848 PR is merged in.
				// if (utilities.isAndroid() || utilities.isIOS()) {
				// 	// Resulting WebView URL is expected to be a %-encoded "file://" URL.
				// 	// Note: File.nativePath returns unencoded path on Android and %-encoded path on iOS.
				// 	let expectedUrl = Ti.Filesystem.getFile(URL).nativePath;
				// 	if (utilities.isAndroid()) {
				// 		expectedUrl = encodeURI(expectedUrl);
				// 	}
				// 	should(e.url).eql(expectedUrl);
				// }
			} catch (err) {
				return finish(err);
			}
			finish();
		});

		win = Ti.UI.createWindow({
			backgroundColor: 'white'
		});

		win.add(webView);
		win.open();

		webView.url = URL;
	});

	it.ios('startListeningToProperties', function (finish) {
		win = Ti.UI.createWindow();
		const webView = Ti.UI.createWebView({
			url: 'https://google.com'
		});
		webView.startListeningToProperties([ 'title' ]);
		webView.addEventListener('title', function () {
			finish();
		});
		win.add(webView);
		win.open();
	});

	it.iosAndWindowsBroken('sslerror', function (finish) {
		const url = 'https://expired.badssl.com/';

		win = Ti.UI.createWindow();
		const webView = Ti.UI.createWebView({
			url: url
		});

		webView.addEventListener('sslerror', function () {
			finish();
		});

		webView.addEventListener('error', function () {
			setTimeout(() => {
				console.warn('failed to load url, retrying...');
				webView.url = url;
			}, 5000);
		});

		win.add(webView);
		win.open();
	});

	// DEPRECATED: Since Titanium 9.2.0
	it.ios('blacklisturl', function (finish) {
		win = Ti.UI.createWindow();
		const webView = Ti.UI.createWebView({
			url: 'https://google.com',
			blockedURLs: [ 'https://google.com' ]
		});

		webView.addEventListener('blacklisturl', function () {
			finish();
		});
		win.add(webView);
		win.open();
	});

	it.ios('blockedurl', function (finish) {
		win = Ti.UI.createWindow();
		const webView = Ti.UI.createWebView({
			url: 'https://google.com',
			blockedURLs: [ 'https://google.com' ]
		});

		webView.addEventListener('blockedurl', function () {
			finish();
		});
		win.add(webView);
		win.open();
	});

	it('blockedURLs', (finish) => {
		win = Ti.UI.createWindow();
		const webView = Ti.UI.createWebView({
			url: 'https://www.axway.com',
			blockedURLs: [ 'www.apple.com', 'www.google.com' ]
		});
		webView.addEventListener('load', () => {
			finish();
		});
		win.add(webView);
		win.open();
	});

	it.ios('basicAuthentication', function (finish) {
		const url = 'https://httpbin.org/basic-auth/user/password';

		win = Ti.UI.createWindow();
		const webView = Ti.UI.createWebView({
			url: url,
			basicAuthentication: { username: 'user', password: 'password' }
		});

		webView.addEventListener('load', function () {
			finish();
		});

		webView.addEventListener('sslerror', function (e) {
			finish(e);
		});

		webView.addEventListener('error', function () {
			setTimeout(() => {
				console.warn('failed to load url, retrying...');
				webView.url = url;
			}, 5000);
		});

		win.add(webView);
		win.open();
	});

	it.iosAndWindowsBroken('ignoreSslError', function (finish) {
		const url = 'https://expired.badssl.com/';

		win = Ti.UI.createWindow();
		const webView = Ti.UI.createWebView({
			url: url,
			ignoreSslError: true
		});

		webView.addEventListener('load', function () {
			finish();
		});

		webView.addEventListener('error', function () {
			setTimeout(() => {
				console.warn('failed to load url, retrying...');
				webView.url = url;
			}, 5000);
		});

		win.add(webView);
		win.open();
	});

	// Verifies local HTML file can access local JS file and invoke an HTML "onload" callback.
	it.windowsMissing('html-script-tag', function (finish) {
		this.slow(3000);
		this.timeout(10000);

		Ti.App.addEventListener('ti.ui.webview.script.tag:onPageLoaded', function () {
			finish();
		});

		win = Ti.UI.createWindow();
		win.add(Ti.UI.createWebView({
			url: 'ti.ui.webview.script.tag.html'
		}));
		win.open();
	});

	it.ios('beforeload', (finish) => {
		const url = 'https://www.appcelerator.com/';
		win = Ti.UI.createWindow();
		const webView = Ti.UI.createWebView({
			url: url
		});

		let beforeLoaded = false;
		webView.addEventListener('beforeload', (e) => {
			if (beforeLoaded === true) {
				if (e.url !== url) {
					webView.stopLoading();
					finish();
				}
			}
			beforeLoaded = true;
		});

		win.add(webView);
		win.open();
	});

	it('baseURL should be accessible via window.location', (done) => {
		const win = Ti.UI.createWindow();
		const baseURL = 'https://www.google.com/';
		const webView = Ti.UI.createWebView();
		webView.setHtml(
			'<html><body></body></html>',
			{
				baseURL
			}
		);
		webView.addEventListener('load', () => {
			webView.evalJS('window.location.href', result => {
				try {
					should(result.replace(/"/g, '')).be.eql(baseURL); // Android encloses the URL in quotes, not sure why.
				} catch (err) {
					return done(err);
				} finally {
					win.close();
				}
				done();
			});
		});
		win.add(webView);
		win.open();
	});

	it.windowsBroken('basic authentication should work properly', (done) => {
		const win = Ti.UI.createWindow();
		const webView = Ti.UI.createWebView({
			url: 'https://jigsaw.w3.org/HTTP/Basic/'
		});
		webView.setBasicAuthentication('guest', 'guest');
		webView.addEventListener('load', () => {
			win.close();
			done();
		});
		win.add(webView);
		win.open();
	});

	it('requestHeaders with redirecting url should work properly', function (finish) {
		win = Ti.UI.createWindow();
		const webView = Ti.UI.createWebView({
			url: 'https://jira.appcelerator.org/',
			requestHeaders: { 'Custom-field1': 'value1' }
		});

		webView.addEventListener('load', function () {
			finish();
		});

		webView.addEventListener('error', function (e) {
			finish(new Error(e.error));
		});

		win.add(webView);
		win.open();
	});

	it.ios('#assetsDirectory', function (finish) {
		win = Ti.UI.createWindow();
		var loadCount = 0;
		function createDirectory(f) {
			if (f && !f.exists()) {
				f.createDirectory();
			}
			return f;
		}

		// Copy from Resources to cache folder
		var cacheDir = createDirectory(Ti.Filesystem.getFile(Ti.Filesystem.applicationCacheDirectory));
		createDirectory(Ti.Filesystem.getFile(cacheDir.nativePath, 'html'));
		createDirectory(Ti.Filesystem.getFile(cacheDir.nativePath, 'folder with spaces'));

		var htmlFile = Ti.Filesystem.getFile(Ti.Filesystem.resourcesDirectory, 'html', 'example.html');
		var nextHtmlFile = Ti.Filesystem.getFile(Ti.Filesystem.resourcesDirectory, 'folder with spaces', 'comingSoon.html');

		var htmlInCache = Ti.Filesystem.getFile(cacheDir.nativePath, 'html', 'example.html');
		var nextHtmlInCache = Ti.Filesystem.getFile(cacheDir.nativePath, 'folder with spaces', 'comingSoon.html');

		htmlFile.copy(htmlInCache.nativePath);
		nextHtmlFile.copy(nextHtmlInCache.nativePath);

		var webView = Ti.UI.createWebView({
			width: Ti.UI.FILL,
			height: Ti.UI.FILL,
			url: htmlInCache.nativePath,
			assetsDirectory: cacheDir.nativePath
		});

		webView.addEventListener('load', function () {
			loadCount++;
			if (loadCount > 1) {
				finish();
			}
		});
		win.add(webView);
		win.open();
	});

	it('progress event', function (finish) {
		this.slow(5000);
		this.timeout(10000);

		win = Ti.UI.createWindow();
		const webView = Ti.UI.createWebView({
			url: 'https://www.google.com'
		});
		webView.addEventListener('progress', function (e) {
			try {
				should(e).have.a.property('value').which.is.a.Number();
				should(e.value).be.within(0, 1);

				// webview.progress may have updated before we got this event fired, so can't compare

				should(e).have.a.property('url').which.is.a.String();
				should(e.url).startWith('https://www.google.com');
				// Sometimes we get an url like: https://www.google.com/#spf=1588254369582
				// should(e.url).be.equalOneOf([ 'https://www.google.com/', 'https://www.google.com' ]);
			} catch (err) {
				return finish(err);
			}
			if (e.value === 1) {
				finish();
			}
		});

		win.add(webView);
		win.open();
	});

	it('.progress', () => {
		const webView = Ti.UI.createWebView({
			url: 'https://www.google.com'
		});
		should(webView).have.a.property('progress').which.is.a.Number(); // should default to 0 until we start loading the page.
	});

	it.ios('#findString', function (finish) {
		if (OS_VERSION_MAJOR < 14) {
			return finish();
		}
		win = Ti.UI.createWindow();
		const webView = Ti.UI.createWebView({
			url: 'https://www.google.com'
		});

		webView.addEventListener('load', function () {
			webView.findString('google', function (e) {
				if (e.success) {
					finish();
				} else {
					finish(e.error);
				}
			});
		});

		win.add(webView);
		win.open();
	});

	it.ios('#createPDF', function (finish) {
		if (OS_VERSION_MAJOR < 14) {
			return finish();
		}
		win = Ti.UI.createWindow();
		const webView = Ti.UI.createWebView({
			url: 'https://www.google.com'
		});

		webView.addEventListener('load', function () {
			webView.createPDF(function (e) {
				try {
					should(e.success).be.true();
					should(e.data).be.an.Object();
					should(e.data).have.a.property('apiName').which.eql('Ti.Blob');
				} catch (err) {
					return finish(err);
				}
				finish();
			});
		});

		win.add(webView);
		win.open();
	});

	it.ios('#createWebArchive', function (finish) {
		if (OS_VERSION_MAJOR < 14) {
			return finish();
		}
		win = Ti.UI.createWindow();
		const webView = Ti.UI.createWebView({
			url: 'https://www.google.com'
		});

		webView.addEventListener('load', function () {
			webView.createWebArchive(function (e) {
				try {
					should(e.success).be.true();
					should(e.data).be.an.Object();
					should(e.data).have.a.property('apiName').which.eql('Ti.Blob');
				} catch (err) {
					return finish(err);
				}
				finish();
			});
		});

		win.add(webView);
		win.open();
	});

	it('decode url', (finish) => {
		win = Ti.UI.createWindow({
			backgroundColor: 'blue'
		});
		const webview = Ti.UI.createWebView({
			url: 'https://www.google.com/sub/api?key=TiTeSTKEy%3D%3D&var=1234'
		});

		webview.addEventListener('load', e => {
			try {
				should(e.source.url).be.a.String();
				should(e.source.url).eql('https://www.google.com/sub/api?key=TiTeSTKEy%3D%3D&var=1234');
			} catch (err) {
				return finish(err);
			}
			finish();
		});
		win.add(webview);
		win.open();
	});

	it('decode \'+\' in url', (finish) => {
		win = Ti.UI.createWindow({
			backgroundColor: 'blue'
		});
		const webview = Ti.UI.createWebView({
			url: 'https://www.google.com/pin%20wheel+.jpg'
		});

		webview.addEventListener('load', e => {
			try {
				should(e.source.url).be.a.String();
				should(e.source.url).eql('https://www.google.com/pin%20wheel+.jpg');
			} catch (err) {
				return finish(err);
			}
			finish();
		});
		win.add(webview);
		win.open();
	});

	describe.ios('#findString()', function () {
		it('is a Function', function () {
			if (OS_VERSION_MAJOR < 14) {
				this.skip();
				return;
			}
			const webView = Ti.UI.createWebView({
				url: 'https://www.appcelerator.com'
			});
			should(webView.findString).be.a.Function();
		});

		it('#findString without configuration', function (finish) {
			if (OS_VERSION_MAJOR < 14) {
				this.skip();
				return finish();
			}
			win = Ti.UI.createWindow();
			const webView = Ti.UI.createWebView({
				url: 'https://www.appcelerator.com'
			});

			webView.addEventListener('load', function () {
				webView.findString('APPCELERATOR', function (e) {
					if (e.success) {
						finish();
					} else {
						finish(e.error);
					}
				});
			});

			win.add(webView);
			win.open();
		});

		it('#findString with configuration', function (finish) {
			if (OS_VERSION_MAJOR < 14) {
				return finish();
			}
			win = Ti.UI.createWindow();
			const webView = Ti.UI.createWebView({
				url: 'https://www.appcelerator.com'
			});

			webView.addEventListener('load', function () {
				// It should fail.
				webView.findString('APPCELERATOR', { caseSensitive: true, backwards: false, wraps: true }, function (e) {
					if (e.success) {
						finish(new Error('Search should fail'));
					} else {
						finish();
					}
				});
			});

			win.add(webView);
			win.open();
		});
	});
});
