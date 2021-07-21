/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
/* eslint-env mocha */
/* eslint no-unused-expressions: "off" */
'use strict';

const should = require('./utilities/assertions');
const utilities = require('./utilities/utilities');

describe('Titanium.UI.Matrix2D', function () {
	it('apiName', function () {
		const matrix = Ti.UI.createMatrix2D();
		should(matrix).have.readOnlyProperty('apiName').which.is.a.String();
		should(matrix.apiName).be.eql('Ti.UI.Matrix2D');
	});

	it('#invert()', function () {
		var matrix1 = Ti.UI.createMatrix2D();
		var matrix2 = Ti.UI.createMatrix2D();
		should(matrix1.invert()).be.an.Object();
		matrix1 = matrix1.scale(2, 2);
		should(matrix1.invert()).be.an.Object();
		matrix1 = matrix1.rotate(90);
		should(matrix1.invert()).be.an.Object();
		matrix1 = matrix1.translate(2, 2);
		should(matrix1.invert()).be.an.Object();
		matrix1 = matrix1.multiply(matrix2);
		should(matrix1.invert()).be.an.Object();
	});

	it('#multiply()', function () {
		var matrix1 = Ti.UI.createMatrix2D();
		var matrix2 = Ti.UI.createMatrix2D();
		should(matrix1.multiply(matrix2)).be.an.Object();
		should(matrix1.multiply(matrix1)).be.an.Object();
		if (utilities.isAndroid()) {
			matrix1 = matrix1.rotate(90);
			matrix2 = matrix2.scale(2, 1);
			var matrix3 = matrix1.multiply(matrix2);
			var values = matrix3.finalValuesAfterInterpolation(50, 100);
			should(values[0]).eql(0);
			should(values[1]).eql(-2);
			should(values[2]).eql(125);
			should(values[3]).eql(1);
			should(values[4]).eql(0);
			should(values[5]).eql(25);
			should(values[6]).eql(0);
			should(values[7]).eql(0);
			should(values[8]).eql(1);
		}
	});

	it('#rotate()', function () {
		var matrix1 = Ti.UI.createMatrix2D();
		should(matrix1.rotate(0)).be.an.Object();
		should(matrix1.rotate(90)).be.an.Object();
		should(matrix1.rotate(360)).be.an.Object();
		should(matrix1.rotate(-180)).be.an.Object();
		should(matrix1.rotate(-720)).be.an.Object();
		should(matrix1.rotate(-0)).be.an.Object();
	});

	it('#scale()', function () {
		var matrix1 = Ti.UI.createMatrix2D();
		should(matrix1.scale(50, 50)).be.an.Object();
		should(matrix1.scale(0, -1)).be.an.Object();
		should(matrix1.scale(-100, -100)).be.an.Object();
	});

	it('#translate()', function () {
		var matrix1 = Ti.UI.createMatrix2D();
		should(matrix1.translate(-1, 0)).be.an.Object();
		should(matrix1.translate(50, 50)).be.an.Object();
		should(matrix1.translate(0, -1)).be.an.Object();
		should(matrix1.translate(-100, -100)).be.an.Object();
	});
});
