/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2015-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
/* eslint-env mocha */
/* global Ti */
/* eslint no-unused-expressions: "off" */
'use strict';
var should = require('./utilities/assertions');

describe('Titanium.UI.View', function () {
	var rootWindow,
		win,
		didFocus = false;

	this.slow(2000);
	this.timeout(10000);

	before(function (finish) {
		rootWindow = Ti.UI.createWindow();
		rootWindow.addEventListener('open', function () {
			finish();
		});
		rootWindow.open();
	});

	after(function (finish) {
		rootWindow.addEventListener('close', function () {
			finish();
		});
		rootWindow.close();
	});

	beforeEach(function () {
		didFocus = false;
	});

	afterEach(function () {
		if (win) {
			win.close();
		}
		win = null;
	});

	// TIMOB-25461: Android - Views with alpha channel in background color and border radius act
	// as a mask.
	it.android('Border Radius with trasparency', function (finish) {
		var win0 = Ti.UI.createWindow({ backgroundColor: 'green' }),
			win1 = Ti.UI.createWindow({ backgroundColor: 'transparent' }),
			view0 = Ti.UI.createView({ width: '90%', height: '90%', borderRadius: 5, backgroundColor: 'blue' }),
			view1 = Ti.UI.createView({ width: 200, height: 200, borderRadius: 100, backgroundColor: '#33FFFFFF' }),
			view2 = Ti.UI.createView({ width: 100, height: 100, borderRadius: 100, backgroundColor: 'white' });
		view0.add([ view1, view2 ]);
		win1.add(view0);
		win0.open();
		win1.open();
		win1.addEventListener('open', function () {
			finish();
		});
	});

});
