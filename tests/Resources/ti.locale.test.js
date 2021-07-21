/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2015-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
/* eslint-env mocha */
/* eslint no-unused-expressions: "off" */
'use strict';
const should = require('./utilities/assertions');

describe('Global', () => {
	it('#L() is a Function', () => should(L).be.a.Function());
});

describe('Titanium.Locale', () => {

	// reset back to US english when done
	after(() => Ti.Locale.language = 'en-US');

	it('exists', () => {
		should(Ti.Locale).not.be.undefined();
		should(Ti.Locale).not.be.null();
		should(Ti.Locale).be.an.Object();
	});

	describe('properties', () => {
		it('.apiName', () => {
			should(Ti.Locale).have.a.readOnlyProperty('apiName').which.is.a.String();
			should(Ti.Locale.apiName).be.eql('Ti.Locale');
		});

		describe('.currentCountry', () => {
			it('is a String', () => {
				should(Ti.Locale).have.a.readOnlyProperty('currentCountry').which.is.a.String();
			});

			it('defaults to \'US\'', () => {
				should(Ti.Locale.currentCountry).eql('US');
			});

			it('has no getter', () => {
				should(Ti.Locale).not.have.a.getter('currentCountry');
			});
		});

		describe('.currentLanguage', () => {
			it('is a String', () => {
				should(Ti.Locale).have.a.readOnlyProperty('currentLanguage').which.is.a.String();
			});

			it('defaults to \'en\'', () => {
				should(Ti.Locale.currentLanguage).eql('en');
			});

			it('has no getter', () => {
				should(Ti.Locale).not.have.a.getter('currentLanguage');
			});
		});

		describe('.currentLocale', () => {
			it('is a String', () => {
				should(Ti.Locale).have.a.readOnlyProperty('currentLocale').which.is.a.String();
			});

			it('defaults to \'en-US\'', () => {
				should(Ti.Locale.currentLocale).eql('en-US');
			});

			it('has no getter', () => {
				should(Ti.Locale).not.have.a.getter('currentLocale');
			});
		});
	});

	describe('methods', () => {
		// TODO Support Ti.Locale.formatTelephoneNumber on other platforms?
		it.android('#formatTelephoneNumber(String)', () => {
			should(Ti.Locale.formatTelephoneNumber).be.a.Function();
			// TODO Actually check inputs/outputs!
		});

		it('#getCurrencyCode(String)', () => {
			should(Ti.Locale.getCurrencyCode).be.a.Function();
			should(Ti.Locale.getCurrencyCode('en-US')).eql('USD');
			should(Ti.Locale.getCurrencyCode('ja-JP')).eql('JPY');
			should(Ti.Locale.getCurrencyCode('zh-CN')).eql('CNY');
			should(Ti.Locale.getCurrencyCode('zh-TW')).eql('TWD');
		});

		// FIXME Get working on iOS
		// FIXME Get working properly cross-platform. JPY gives us ¥ on Windows and Android, JP¥ on iOS. CNY gives us ¥ on Windows, CN¥ on Android
		it.androidAndIosBroken('#getCurrencySymbol(String)', () => {
			should(Ti.Locale.getCurrencySymbol).be.a.Function();
			should(Ti.Locale.getCurrencySymbol('USD')).eql('$');
			should(Ti.Locale.getCurrencySymbol('JPY')).eql('¥'); // 'JP¥' on iOS
			should(Ti.Locale.getCurrencySymbol('CNY')).eql('¥'); // 'CN¥' on Android
			should(Ti.Locale.getCurrencySymbol('TWD')).eql('NT$');
		});

		it('#getLocaleCurrencySymbol()', () => {
			should(Ti.Locale.getLocaleCurrencySymbol).be.a.Function();
			should(Ti.Locale.getLocaleCurrencySymbol('en-US')).eql('$');
		});

		describe('#getString()', () => {
			it('is a Function', () => {
				should(Ti.Locale.getString).be.a.Function();
			});

			beforeEach(() => {
				Ti.Locale.language = 'en-US';
			});

			it('returns stored value for found key', () => {
				should(Ti.Locale.getString('this_is_my_key')).eql('this is my value');
				should(L('this_is_my_key')).eql('this is my value');
			});

			it('returns key if not found and no default specified', () => {
				should(Ti.Locale.getString('this_should_not_be_found')).eql('this_should_not_be_found');
				should(L('this_should_not_be_found')).eql('this_should_not_be_found');
			});

			it('returns supplied default if key not found', () => {
				should(Ti.Locale.getString('this_should_not_be_found', 'this is the default value')).eql('this is the default value');
				should(L('this_should_not_be_found', 'this is the default value')).eql('this is the default value');
			});

			// FIXME: returns null - we can fix this in a cross-platform way via same extension we used for Android to fix issue
			it('returns key if supplied default is not a String and key/value pair not found', () => {
				should(Ti.Locale.getString('this_should_not_be_found', null)).eql('this_should_not_be_found');
				should(L('this_should_not_be_found', null)).eql('this_should_not_be_found');
				should(Ti.Locale.getString('this_should_not_be_found', 123)).eql('this_should_not_be_found');
				should(L('this_should_not_be_found', 123)).eql('this_should_not_be_found');
			});

			// https://jira.appcelerator.org/browse/TIMOB-26651
			it('handles locale/country specific languages (i.e. en-GB vs en-US)', () => {
				Ti.Locale.language = 'en-GB';
				should(Ti.Locale.getString('this_is_my_key')).eql('this is my en-GB value'); // This fails on Windows, gives 'this is my value'
				should(L('this_is_my_key')).eql('this is my en-GB value'); // This fails on Windows, gives 'this is my value'
			});

			// and then this one fails because it's using en-GB strings after we tell it to be ja...
			it('handles single segment language (i.e. ja)', () => {
				Ti.Locale.language = 'ja';
				should(Ti.Locale.getString('this_is_my_key')).eql('これは私の値です');
				should(L('this_is_my_key')).eql('これは私の値です');
			});

			// ...and this one is now using ja strings, but langauge value is en-US!
			// FIXME iOS seems to ignore position info on the format string.
			// We're trying to force the 1st argument into the second slot, and vice versa here. iOS handles the %2$s syntax, but ignores position
			it.iosAndWindowsBroken('usage with String.format()', () => {
				var i18nMissingMsg = '<no translation available>';
				var string1 = 'You say ' + Ti.Locale.getString('signoff', i18nMissingMsg) + ' and I say ' + Ti.Locale.getString('greeting', i18nMissingMsg) + '!';
				var string2 = String.format(L('phrase'), L('greeting', i18nMissingMsg), L('signoff', i18nMissingMsg));

				should(string1).eql(string2);
				if (Ti.Locale.currentLanguage === 'en') {
					should(string1).eql('You say goodbye and I say hello!');
					should(string2).eql('You say goodbye and I say hello!');
				} else if (Ti.Locale.currentLanguage === 'ja') {
					should(string1).eql('You say さようなら and I say こんにちは!');
					should(string2).eql('You say さようなら and I say こんにちは!');
				}
			});
		});

		describe('#parseDecimal()', () => {
			it('compared with String.formatDecimal()', () => {
				should(Ti.Locale.parseDecimal).be.a.Function();

				const numericValue = 1234567.8;

				let numericString = String.formatDecimal(numericValue);
				let parsedValue = Ti.Locale.parseDecimal(numericString);
				should(parsedValue).be.a.Number();
				should(Math.abs(parsedValue - numericValue)).be.lessThan(Number.EPSILON);

				numericString = String.formatDecimal(numericValue, 'de-DE');
				parsedValue = Ti.Locale.parseDecimal(numericString, 'de-DE');
				should(Math.abs(parsedValue - numericValue)).be.lessThan(Number.EPSILON);

				numericString = String.formatDecimal(numericValue, 'fr-FR');
				parsedValue = Ti.Locale.parseDecimal(numericString, 'fr-FR');
				should(Math.abs(parsedValue - numericValue)).be.lessThan(Number.EPSILON);

				numericString = String.formatDecimal(numericValue, 'ar-EG');
				parsedValue = Ti.Locale.parseDecimal(numericString, 'ar-EG');
				should(Math.abs(parsedValue - numericValue)).be.lessThan(Number.EPSILON);
			});

			it('localized values', () => {
				let result = Ti.Locale.parseDecimal('1,234,567.8', 'en-US');
				should(Math.abs(result - 1234567.8)).be.lessThan(Number.EPSILON);

				result = Ti.Locale.parseDecimal('1.234.567,8', 'de-DE');
				should(Math.abs(result - 1234567.8)).be.lessThan(Number.EPSILON);

				// France uses non-breaking unicode spaces for thousands separator.
				result = Ti.Locale.parseDecimal('1\u00A0234\u00A0567,8', 'fr-FR');
				should(Math.abs(result - 1234567.8)).be.lessThan(Number.EPSILON);

				// But it was then changed to narrow non-breaking spaces!
				result = Ti.Locale.parseDecimal('1\u202F234\u202F567,8', 'fr-FR');
				should(Math.abs(result - 1234567.8)).be.lessThan(Number.EPSILON);
			});

			it('various values', () => {
				let result = Ti.Locale.parseDecimal('0', 'en-US');
				should(Math.abs(result)).be.lessThan(Number.EPSILON);

				result = Ti.Locale.parseDecimal('0.', 'en-US');
				should(Math.abs(result)).be.lessThan(Number.EPSILON);

				result = Ti.Locale.parseDecimal('.0', 'en-US');
				should(Math.abs(result)).be.lessThan(Number.EPSILON);

				result = Ti.Locale.parseDecimal('00.00123', 'en-US');
				should(Math.abs(result - 0.00123)).be.lessThan(Number.EPSILON);

				result = Ti.Locale.parseDecimal('+0', 'en-US');
				should(Math.abs(result)).be.lessThan(Number.EPSILON);

				result = Ti.Locale.parseDecimal('-0', 'en-US');
				should(Math.abs(result)).be.lessThan(Number.EPSILON);

				result = Ti.Locale.parseDecimal('+1234.5', 'en-US');
				should(Math.abs(result - 1234.5)).be.lessThan(Number.EPSILON);

				result = Ti.Locale.parseDecimal('-1234.5', 'en-US');
				should(Math.abs(result + 1234.5)).be.lessThan(Number.EPSILON);
			});

			it('scientific values', () => {
				let result = Ti.Locale.parseDecimal('1.2E+3', 'en-US');
				should(Math.abs(result - 1200.0)).be.lessThan(Number.EPSILON);

				result = Ti.Locale.parseDecimal('-1.2E+3', 'en-US');
				should(Math.abs(result + 1200.0)).be.lessThan(Number.EPSILON);

				result = Ti.Locale.parseDecimal('1.2E-3', 'en-US');
				should(Math.abs(result - 0.0012)).be.lessThan(Number.EPSILON);

				result = Ti.Locale.parseDecimal('-1.2E-3', 'en-US');
				should(Math.abs(result + 0.0012)).be.lessThan(Number.EPSILON);
			});

			it('padded with spaces', () => {
				let result = Ti.Locale.parseDecimal(' 123 ', 'en-US');
				should(Math.abs(result - 123)).be.lessThan(Number.EPSILON);

				result = Ti.Locale.parseDecimal(' +123 ', 'en-US');
				should(Math.abs(result - 123)).be.lessThan(Number.EPSILON);

				result = Ti.Locale.parseDecimal(' -123 ', 'en-US');
				should(Math.abs(result + 123)).be.lessThan(Number.EPSILON);
			});

			it('NaN', () => {
				let result = Ti.Locale.parseDecimal('ThisShouldFail');
				should(result).be.a.Number();
				should(result).be.eql(Number.NaN);

				result = Ti.Locale.parseDecimal('');
				should(result).be.eql(Number.NaN);

				result = Ti.Locale.parseDecimal(' ');
				should(result).be.eql(Number.NaN);

				result = Ti.Locale.parseDecimal(null);
				should(result).be.eql(Number.NaN);

				result = Ti.Locale.parseDecimal(undefined);
				should(result).be.eql(Number.NaN);
			});
		});

		describe('#set language(String)', () => {
			it('has a setter', () => {
				should(Ti.Locale).have.a.setter('language');
			});

			it('changes .currentLanguage', () => {
				Ti.Locale.language = 'fr';
				should(Ti.Locale.currentLanguage).eql('fr');
			});

			// FIXME Get working on iOS, setLangauge doesn't seem to affect currentLocale
			it.iosBroken('changes .currentLocale', () => {
				Ti.Locale.language = 'en-GB';
				should(Ti.Locale.currentLocale).eql('en-GB'); // iOS returns 'en-US'
				Ti.Locale.language = 'fr';
				should(Ti.Locale.currentLocale).eql('fr');
			});

			// TODO test if it changes the currentCountry?
		});
	});
});
