/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2018-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
/* eslint-env mocha */
/* eslint no-unused-expressions: "off" */
'use strict';

var should = require('./utilities/assertions');

describe.android('Titanium.Worker', function () {
	let worker;
	it('apiName', function () {
		worker = Ti.Worker.createWorker('worker.js', { name: 'Worker task ' });
		should(worker).have.readOnlyProperty('apiName').which.is.a.String;
		should(worker.apiName).be.eql('Ti.Worker');
	});

	it('#postMessage()', function () {
		should(worker).have.readOnlyProperty('postMessage').which.is.a.Function;
	});

	it('#addEventListener()', function () {
		should(worker).have.readOnlyProperty('addEventListener').which.is.a.Function;
	});

	it('[inside worker] addEventListener', function (finish) {
		this.timeout(1e4); // could take a while to init worker, but only once
		worker.addEventListener('message', function onMessage(e) {
			worker.removeEventListener('message', onMessage);
			if (!e && e.data !== 'function') {
				finish(Error('Unexpected message from worker'));
			}
			finish();
		});
		worker.postMessage({ eval: 'typeof addEventListener' });
	});

	it('[inside worker] onmessage', function (finish) {
		worker.addEventListener('message', function onMessage(e) {
			worker.removeEventListener('message', onMessage);
			if (!e && e.data !== 'function') {
				finish(Error('Unexpected message from worker'));
			}
			finish();
		});
		worker.postMessage({ eval: 'typeof onmessage' });
	});

	it('[inside worker] postMessage', function (finish) {
		worker.addEventListener('message', function onMessage(e) {
			worker.removeEventListener('message', onMessage);
			if (!e && e.data !== 'function') {
				finish(Error('Unexpected message from worker'));
			}
			finish();
		});
		worker.postMessage({ eval: 'typeof postMessage' });
	});

	it('[inside worker] worker', function (finish) {
		worker.addEventListener('message', function onMessage(e) {
			worker.removeEventListener('message', onMessage);
			if (!e && e.data !== 'object') {
				finish(Error('Unexpected message from worker'));
			}
			finish();
		});
		worker.postMessage({ eval: 'typeof worker' });
	});

	it('[inside worker] worker.addEventListener', function (finish) {
		worker.addEventListener('message', function onMessage(e) {
			worker.removeEventListener('message', onMessage);
			if (!e && e.data !== 'function') {
				finish(Error('Unexpected message from worker'));
			}
			finish();
		});
		worker.postMessage({ eval: 'typeof worker.addEventListener' });
	});

	it('[inside worker] worker.onmessage', function (finish) {
		worker.addEventListener('message', function onMessage(e) {
			worker.removeEventListener('message', onMessage);
			if (!e && e.data !== 'function') {
				finish(Error('Unexpected message from worker'));
			}
			finish();
		});
		worker.postMessage({ eval: 'typeof worker.onmessage' });
	});

	it('[inside worker] worker.postMessage', function (finish) {
		worker.addEventListener('message', function onMessage(e) {
			worker.removeEventListener('message', onMessage);
			if (!e && e.data !== 'function') {
				finish(Error('Unexpected message from worker'));
			}
			finish();
		});
		worker.postMessage({ eval: 'typeof worker.postMessage' });
	});

	it('send/receive messages', function (finish) {
		let answers = [ 42, 4, 2 ];
		worker.addEventListener('message', function onMessage(e) {
			let error;
			try {
				should(e).be.an.Object;
				should(e.data).be.eql(answers.shift());
			} catch (err) {
				error = err;
			}

			if (!answers.length || error) {
				worker.removeEventListener('message', onMessage);
				return finish(error);
			}
		});
		worker.postMessage({ eval: '6 * 7' });
		worker.postMessage({ eval: '9 - 5' });
		worker.postMessage({ eval: '6 / 3' });
	});

	it('#terminate()', function () {
		should(worker).have.readOnlyProperty('terminate').which.is.a.Function;
		worker.terminate();
	});
});
