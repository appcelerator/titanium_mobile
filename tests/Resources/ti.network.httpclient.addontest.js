/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
/* eslint-env mocha */
/* global Ti */
/* eslint no-unused-expressions: "off" */
'use strict';
var should = require('./utilities/assertions');

describe('Titanium.Network.HTTPClient', function () {
	it('send on response', function (finish) {
		this.timeout(6e4);

		var xhr = Ti.Network.createHTTPClient({}),
			count = 0;

		xhr.setTimeout(6e4);

		xhr.onload = function (e) {
			try {
				const response = JSON.parse(e.source.responseText).json;

				if (response.count <= 8) {
					xhr.send(JSON.stringify({ count: ++count }));
					return;
				}
				finish();
			} catch (err) {
				finish(err);
			}
		};
		xhr.onerror = function (e) {
			finish(e);
		};

		xhr.open('POST', 'https://httpbin.org/post');
		xhr.setRequestHeader('Content-Type', 'application/json; charset=utf8');
		xhr.send(JSON.stringify({ count: count }));
	});
});
