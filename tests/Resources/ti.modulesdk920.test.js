/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2020-Present by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
/* eslint-env mocha */
/* eslint no-unused-expressions: "off" */
'use strict';
const should = require('./utilities/assertions');

describe('Module Built with SDK 9.2.0', function () {
	const MAX_INT32 = 2147483647;
	const MIN_INT32 = -2147483648;
	const DICTIONARY_TEST_DATA = {
		trueValue: true,
		falseValue: false,
		zeroValue: 0,
		maxInt32Value: MAX_INT32,
		minInt32Value: MIN_INT32,
		doubleValue: 123.456,
		maxDoubleValue: Number.MAX_VALUE,
		minDoubleValue: Number.MIN_VALUE,
		emptyString: '',
		helloString: 'Hello World',
		dateValue: new Date(),
		emptyArray: [],
		oneElementArray: [ 'foo' ],
		twoElementArray: [ 'foo', 'bar' ],
		emptyDictionary: {},
		filledDictionary: { booleanValue: true, numberValue: 123, stringValue: 'bar' }
	};
	let tiModule;

	it('require', () => {
		tiModule = require('ti.modulesdk920');
		should(tiModule).not.be.undefined();
		should(tiModule).be.an.Object();
	});

	it('.wasModuleInitialized', () => {
		should(tiModule.wasModuleInitialized).be.true();
	});

	it('.INT_VALUE_1', () => {
		should(tiModule.INT_VALUE_1).be.eql(1);
	});

	it('.HELLO_WORLD', () => {
		should(tiModule.HELLO_WORLD).be.eql('Hello World');
	});

	it('.booleanValue', () => {
		should(tiModule.booleanValue).be.a.Boolean();
		should(tiModule.booleanValue).be.false();
		tiModule.booleanValue = true;
		should(tiModule.booleanValue).be.true();
	});

	it.ios('.booleanArray', () => {
		should(tiModule.booleanArray).be.equalOneOf([ null, undefined ]);
		tiModule.booleanArray = [ true, false ];
		should(tiModule.booleanArray).be.eql([ true, false ]);
		tiModule.booleanArray = [];
		should(tiModule.booleanArray).be.eql([]);
		tiModule.booleanArray = null;
		should(tiModule.booleanArray).be.equalOneOf([ null, undefined ]);
	});

	it('.intValue', () => {
		should(tiModule.intValue).be.a.Number();
		should(tiModule.intValue).be.eql(0);
		tiModule.intValue = MAX_INT32;
		should(tiModule.intValue).be.eql(MAX_INT32);
		tiModule.intValue = MIN_INT32;
		should(tiModule.intValue).be.eql(MIN_INT32);
	});

	it('.intArray', () => {
		should(tiModule.intArray).be.equalOneOf([ null, undefined ]);
		tiModule.intArray = [ MAX_INT32, MIN_INT32 ];
		should(tiModule.intArray).be.eql([ MAX_INT32, MIN_INT32 ]);
		tiModule.intArray = [];
		should(tiModule.intArray).be.eql([]);
		tiModule.intArray = null;
		should(tiModule.intArray).be.equalOneOf([ null, undefined ]);
	});

	it('.longValue', () => {
		should(tiModule.longValue).be.a.Number();
		should(tiModule.longValue).be.eql(0);
		tiModule.longValue = Number.MAX_SAFE_INTEGER;
		should(tiModule.longValue).be.eql(Number.MAX_SAFE_INTEGER);
		tiModule.longValue = Number.MIN_SAFE_INTEGER;
		should(tiModule.longValue).be.eql(Number.MIN_SAFE_INTEGER);
	});

	it('.longArray', () => {
		should(tiModule.longArray).be.equalOneOf([ null, undefined ]);
		tiModule.longArray = [ Number.MAX_SAFE_INTEGER, Number.MIN_SAFE_INTEGER ];
		should(tiModule.longArray).be.eql([ Number.MAX_SAFE_INTEGER, Number.MIN_SAFE_INTEGER ]);
		tiModule.longArray = [];
		should(tiModule.longArray).be.eql([]);
		tiModule.longArray = null;
		should(tiModule.longArray).be.equalOneOf([ null, undefined ]);
	});

	it('.floatValue', () => {
		should(tiModule.floatValue).be.a.Number();
		should(tiModule.floatValue).be.eql(0);
		tiModule.floatValue = 123.456;
		should(tiModule.floatValue).be.approximately(123.456, 0.0001);
	});

	it('.floatArray', () => {
		should(tiModule.floatArray).be.equalOneOf([ null, undefined ]);
		tiModule.floatArray = [ 123.4, -567.8 ];
		should(tiModule.floatArray).be.an.Array();
		should(tiModule.floatArray[0]).be.approximately(123.4, 0.01);
		should(tiModule.floatArray[1]).be.approximately(-567.8, 0.01);
		tiModule.floatArray = [];
		should(tiModule.floatArray).be.eql([]);
		tiModule.floatArray = null;
		should(tiModule.floatArray).be.equalOneOf([ null, undefined ]);
	});

	it('.doubleValue', () => {
		should(tiModule.doubleValue).be.a.Number();
		should(tiModule.doubleValue).be.eql(0);
		tiModule.doubleValue = 12345678.9;
		should(tiModule.doubleValue).be.approximately(12345678.9, 0.01);
	});

	it.ios('.doubleArray', () => {
		should(tiModule.doubleArray).be.equalOneOf([ null, undefined ]);
		tiModule.doubleArray = [ 12345678.9, -98765432.1 ];
		should(tiModule.doubleArray).be.an.Array();
		should(tiModule.doubleArray[0]).be.approximately(12345678.9, 0.01);
		should(tiModule.doubleArray[1]).be.approximately(-98765432.1, 0.01);
		tiModule.doubleArray = [];
		should(tiModule.doubleArray).be.eql([]);
		tiModule.doubleArray = null;
		should(tiModule.doubleArray).be.equalOneOf([ null, undefined ]);
	});

	it('.stringValue', () => {
		should(tiModule.stringValue).be.equalOneOf([ null, undefined ]);
		tiModule.stringValue = 'Hello World';
		should(tiModule.stringValue).be.eql('Hello World');
		tiModule.stringValue = '';
		should(tiModule.stringValue).be.eql('');
		tiModule.stringValue = null;
		should(tiModule.stringValue).be.equalOneOf([ null, undefined ]);
	});

	it('.stringArray', () => {
		should(tiModule.stringArray).be.equalOneOf([ null, undefined ]);
		tiModule.stringArray = [ 'foo', 'bar' ];
		should(tiModule.stringArray).be.eql([ 'foo', 'bar' ]);
		tiModule.stringArray = [];
		should(tiModule.stringArray).be.eql([]);
		tiModule.stringArray = null;
		should(tiModule.stringArray).be.equalOneOf([ null, undefined ]);
	});

	it('.dateValue', () => {
		const currentDate = new Date();
		should(tiModule.dateValue).be.equalOneOf([ null, undefined ]);
		tiModule.dateValue = currentDate;
		should(tiModule.dateValue).be.eql(currentDate);
		tiModule.dateValue = null;
		should(tiModule.dateValue).be.equalOneOf([ null, undefined ]);
	});

	it('.dateArray', () => {
		const currentDate = new Date();
		const oldDate = new Date(2020, 0, 31);
		should(tiModule.dateArray).be.equalOneOf([ null, undefined ]);
		tiModule.dateArray = [ currentDate, oldDate ];
		should(tiModule.dateArray).be.eql([ currentDate, oldDate ]);
		tiModule.dateArray = [];
		should(tiModule.dateArray).be.eql([]);
		tiModule.dateArray = null;
		should(tiModule.dateArray).be.equalOneOf([ null, undefined ]);
	});

	it('.dictionaryValue', () => {
		should(tiModule.dictionaryValue).be.equalOneOf([ null, undefined ]);
		tiModule.dictionaryValue = {};
		should(tiModule.dictionaryValue).be.eql({});
		tiModule.dictionaryValue = null;
		should(tiModule.dictionaryValue).be.equalOneOf([ null, undefined ]);
		tiModule.dictionaryValue = DICTIONARY_TEST_DATA;
		should(tiModule.dictionaryValue).be.eql(DICTIONARY_TEST_DATA);
	});

	it('.dictionaryArray', () => {
		should(tiModule.dictionaryArray).be.equalOneOf([ null, undefined ]);
		tiModule.dictionaryArray = [];
		should(tiModule.dictionaryArray).be.eql([]);
		tiModule.dictionaryArray = null;
		should(tiModule.dictionaryArray).be.equalOneOf([ null, undefined ]);
		tiModule.dictionaryArray = [ {} ];
		should(tiModule.dictionaryArray).be.eql([ {} ]);
		tiModule.dictionaryArray = [ DICTIONARY_TEST_DATA, { foo: 'bar' } ];
		should(tiModule.dictionaryArray).be.eql([ DICTIONARY_TEST_DATA, { foo: 'bar' } ]);
	});

	it('.callback', () => {
		let wasInvoked = false;
		const onCallbackInvoked = () => {
			wasInvoked = true;
			return true;
		};
		should(tiModule.callback).be.equalOneOf([ null, undefined ]);
		tiModule.callback = onCallbackInvoked;
		should(tiModule.callback).be.eql(onCallbackInvoked);
		should(tiModule.callback()).be.true();
		should(wasInvoked).be.true();
	});

	it('#invokeCallbackSync()', () => {
		tiModule.callback = (arg1, arg2, arg3) => {
			if ((arg1 === true) && (arg2 === 123) && (arg3 === 'test')) {
				return 'succeeded';
			}
			return 'failed';
		};
		should(tiModule.invokeCallbackSync(true, 123, 'test')).be.eql('succeeded');
	});

	it('#invokeCallbackAsync()', function (finish) {
		this.timeout(1000);
		let wasInvoked = false;
		tiModule.callback = (arg1, arg2, arg3) => {
			try {
				wasInvoked = true;
				should(arg1).be.true();
				should(arg2).be.eql(123);
				should(arg3).be.eql('test');
				finish();
			} catch (err) {
				finish(err);
			}
		};
		tiModule.invokeCallbackAsync(true, 123, 'test');
		should(wasInvoked).be.false();
	});

	it('Resource File', () => {
		const jsonData = require('./ti.modulesdk920/test.json');
		should(jsonData.foo).be.eql('bar');
	});
});
