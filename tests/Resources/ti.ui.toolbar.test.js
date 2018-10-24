/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2017-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
/* eslint-env mocha */
/* global Ti */
/* eslint no-unused-expressions: "off" */

'use strict';
var should = require('./utilities/assertions'),
	utilities = require('./utilities/utilities');

describe('Titanium.UI.Toolbar', function () {

	var window;

	beforeEach(function () {
		window = Ti.UI.createWindow({ exitOnClose: false, theme: 'Theme.AppCompat.NoTitleBar', backgroundColor: '#ACDC77' });
	});

	afterEach(function () {
		window.close();
	});

	it('apiName', function () {
		var toolbar = Ti.UI.createToolbar();
		window.add(toolbar);
		should(toolbar).have.readOnlyProperty('apiName').which.is.a.String;
		should(toolbar.apiName).be.eql('Ti.UI.Toolbar');
	});

	it('barColor word value', function () {
		var toolbar = Ti.UI.createToolbar({ barColor: 'gray' });
		window.add(toolbar);
		should(toolbar.barColor).eql('gray');
	});

	it('barColor hex value', function () {
		var toolbar = Ti.UI.createToolbar({ barColor: '#ACDC77' });
		window.add(toolbar);
		should(toolbar.barColor).eql('#ACDC77');
	});

	it('barColor shorthand hex value', function () {
		var toolbar = Ti.UI.createToolbar({ barColor: '#ABC' });
		window.add(toolbar);
		should(toolbar.barColor).eql('#ABC');
	});

	it.android('contentInsetEndWithActions', function () {
		var toolbar = Ti.UI.createToolbar({ contentInsetEndWithActions: 20, top: 0, width: Ti.UI.FILL });
		window.activity.supportToolbar = toolbar;
		window.add(toolbar);
		window.activity.onCreateOptionsMenu = function (e) {
			var menu = e.menu;
			menu.add({
				title: 'Item 1',
				showAsAction: Ti.Android.SHOW_AS_ACTION_NEVER
			});
		};
		window.addEventListener('open', function () {
			should(toolbar.contentInsetEndWithActions).eql(20);
		});
		window.open();
	});

	it.android('contentInsetStartWithNavigation', function () {
		var toolbar = Ti.UI.createToolbar({ contentInsetStartWithNavigation: 20, top: 0, width: Ti.UI.FILL });
		window.activity.supportToolbar = toolbar;
		window.add(toolbar);
		window.addEventListener('open', function () {
			window.activity.actionBar.displayHomeAsUp = true;
			should(toolbar.contentInsetStartWithNavigation).eql(20);
		});
		window.open();
	});

	it('extendBackground', function () {
		var toolbar = Ti.UI.createToolbar({ contentInsetStartWithNavigation: 20, top: 0, width: Ti.UI.FILL, extendBackground: true, barColor: 'red' });
		window.activity.supportToolbar = toolbar;
		window.add(toolbar);
		window.addEventListener('open', function () {
			should(toolbar.extendBackground).eql(true);
		});
		window.open();
	});

	it.android('logo', function (finish) {
		function listener (e) {
			should(e.logo).eql(true);
			finish();
		}
		var toolbar = Ti.UI.createToolbar({ top: 0, width: Ti.UI.FILL, logo: Ti.Filesystem.resourcesDirectory + 'Logo.png', barColor: 'red', resourceLoadedListener: listener });
		window.activity.supportToolbar = toolbar;
		window.add(toolbar);
		window.open();
	});

	it.android('logo pixel density specific', function (finish) {
		function listener (e) {
			should(e.logo).eql(true);
			finish();
		}
		var toolbar = Ti.UI.createToolbar({ top: 0, width: Ti.UI.FILL, logo: '/images/dip.png', barColor: 'red', resourceLoadedListener: listener });
		window.activity.supportToolbar = toolbar;
		window.add(toolbar);
		window.open();
	});

	it.android('navigationIcon', function (finish) {
		function listener (e) {
			should(e.navigationIcon).eql(true);
			finish();
		}
		var toolbar = Ti.UI.createToolbar({ top: 0, width: Ti.UI.FILL, displayHomeAsUp: true, navigationIcon: Ti.Filesystem.resourcesDirectory + 'Logo.png', barColor: 'red', resourceLoadedListener: listener });
		window.activity.supportToolbar = toolbar;
		window.add(toolbar);
		window.open();
	});

	it.android('navigationIcon pixel density specific', function (finish) {
		function listener (e) {
			should(e.navigationIcon).eql(true);
			finish();
		}
		var toolbar = Ti.UI.createToolbar({ top: 0, width: Ti.UI.FILL, displayHomeAsUp: true, navigationIcon: '/images/dip.png', barColor: 'red', resourceLoadedListener: listener });
		window.activity.supportToolbar = toolbar;
		window.add(toolbar);
		window.open();
	});

	it.android('overflowIcon', function (finish) {
		function listener (e) {
			should(e.overflowIcon).eql(true);
			finish();
		}
		var toolbar = Ti.UI.createToolbar({ top: 0, width: Ti.UI.FILL, overflowIcon: Ti.Filesystem.resourcesDirectory + 'Logo.png', barColor: 'red', resourceLoadedListener: listener });
		window.activity.supportToolbar = toolbar;
		window.add(toolbar);
		window.activity.onCreateOptionsMenu = function (e) {
			var menu = e.menu;
			menu.add({
				title: 'Item 1',
				showAsAction: Ti.Android.SHOW_AS_ACTION_NEVER
			});
		};
		window.open();
	});

	it.android('overflowIcon pixel density specific', function (finish) {
		function listener (e) {
			should(e.overflowIcon).eql(true);
			finish();
		}
		var toolbar = Ti.UI.createToolbar({ top: 0, width: Ti.UI.FILL, overflowIcon: '/images/dip.png', barColor: 'red', resourceLoadedListener: listener });
		window.activity.supportToolbar = toolbar;
		window.add(toolbar);
		window.activity.onCreateOptionsMenu = function (e) {
			var menu = e.menu;
			menu.add({
				title: 'Item 1',
				showAsAction: Ti.Android.SHOW_AS_ACTION_NEVER
			});
		};
		window.open();
	});

	it.android('title', function () {
		var toolbar = Ti.UI.createToolbar({ top: 0, width: Ti.UI.FILL, title: 'Title', barColor: 'blue' });
		window.add(toolbar);
		window.addEventListener('open', function () {
			should(toolbar.title).eql('Title');
		});
		window.open();
	});

	it.android('subtitle', function () {
		var toolbar = Ti.UI.createToolbar({ top: 0, width: Ti.UI.FILL, subtitle: 'Subtitle', barColor: 'blue' });
		window.add(toolbar);
		window.addEventListener('open', function () {
			should(toolbar.subtitle).eql('Subtitle');
		});
		window.open();
	});

	it.android('titleTextColor', function () {
		var toolbar = Ti.UI.createToolbar({ top: 0, width: Ti.UI.FILL, title: 'Title', titleTextColor: 'red', barColor: 'blue' });
		window.add(toolbar);
		window.addEventListener('open', function () {
			should(toolbar.titleTextColor).eql('red');
		});
		window.open();
	});

	it.android('subtitleTextColor', function () {
		var toolbar = Ti.UI.createToolbar({ top: 0, width: Ti.UI.FILL, subtitle: 'Subtitle', subtitleTextColor: 'green', barColor: 'blue' });
		window.add(toolbar);
		window.addEventListener('open', function () {
			should(toolbar.subtitleTextColor).eql('green');
		});
		window.open();
	});

	it('translucent', function () {
		var toolbar = Ti.UI.createToolbar({ top: 0, width: Ti.UI.FILL, translucent: true, barColor: 'blue' });
		window.add(toolbar);
		window.addEventListener('open', function () {
			should(toolbar.translucent).eql(true);
		});
		window.open();
	});
});
