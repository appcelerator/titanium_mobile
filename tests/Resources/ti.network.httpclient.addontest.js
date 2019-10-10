/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
/* eslint-env mocha */
/* eslint no-unused-expressions: "off" */
'use strict';
var should = require('./utilities/assertions');

describe('Titanium.Network.HTTPClient', function () {

	it('progress event', function (finish) {
		var progressVar = -1,
			blob = Ti.UI.createImageView({ image: 'SplashScreen.png' }).toBlob(),
			base64String = Ti.Utils.base64encode(blob).toString(),
			xhr = Ti.Network.createHTTPClient({
				onsendstream: function (e) {
					try {
						should(e.progress).be.above(0);
						should(e.progress).be.above(progressVar);
						progressVar = e.progress;
					} catch (error) {
						finish(error);
					}
				},
				onload: function () {
					finish();
				}
			});
		xhr.open('POST', 'https://httpbin.org/post');
		xhr.send(base64String);
	});

});
