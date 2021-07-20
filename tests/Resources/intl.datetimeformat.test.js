/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2018-Present by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
/* eslint-env mocha */
/* globals OS_ANDROID, OS_VERSION_MAJOR */

/* eslint no-unused-expressions: "off" */
'use strict';
const should = require('./utilities/assertions');
const utilities = require('./utilities/utilities');

describe('Intl.DateTimeFormat',  () => {
	it('#constructor()', () => {
		should(Intl.DateTimeFormat).not.be.undefined();
		should(Intl.DateTimeFormat).be.a.Function();
		should(Intl.DateTimeFormat.prototype.constructor).not.be.undefined();
		should(Intl.DateTimeFormat.prototype.constructor).be.a.Function();
		should(Intl.DateTimeFormat.prototype.constructor === Intl.DateTimeFormat).be.true();
		should(new Intl.DateTimeFormat()).be.an.Object();
		should(new Intl.DateTimeFormat('en-US')).be.an.Object();
		should(new Intl.DateTimeFormat([ 'en-US' ])).be.an.Object();
		should(new Intl.DateTimeFormat([ 'en-US', 'de-DE' ])).be.an.Object();
		should(new Intl.DateTimeFormat(undefined, { dateStyle: 'full' })).be.an.Object();
		should(new Intl.DateTimeFormat('en-US', { dateStyle: 'full' })).be.an.Object();
		should(new Intl.DateTimeFormat([ 'en-US' ], { dateStyle: 'full' })).be.an.Object();
		should(new Intl.DateTimeFormat([ 'en-US', 'de-DE' ], { dateStyle: 'full' })).be.an.Object();
	});

	describe('#format()', () => {
		it('validate function', () => {
			const formatter = new Intl.DateTimeFormat();
			should(formatter.format).not.be.undefined();
			should(formatter.format).be.a.Function();
			should(formatter.format(new Date())).be.a.String();
		});

		it('default format', () => {
			// 2020-March-1st 08:02:05 PM
			const date = new Date(2020, 2, 1, 20, 2, 5);
			let formatter = new Intl.DateTimeFormat('en-US');
			should(formatter.format(date)).be.eql('3/1/2020');
			formatter = new Intl.DateTimeFormat('en-US', {});
			should(formatter.format(date)).be.eql('3/1/2020');
			formatter = new Intl.DateTimeFormat('de-DE');
			should(formatter.format(date)).be.eql('1.3.2020');
			formatter = new Intl.DateTimeFormat('ja-JP');
			should(formatter.format(date)).be.eql('2020/3/1');
		});

		it('numeric date', () => {
			// 2020-March-1st
			const date = new Date(Date.UTC(2020, 2, 1));
			const options = {
				year: 'numeric',
				month: 'numeric',
				day: 'numeric',
				timeZone: 'UTC'
			};
			let formatter = new Intl.DateTimeFormat('en-US', options);
			should(formatter.format(date)).be.eql('3/1/2020');
			formatter = new Intl.DateTimeFormat('de-DE', options);
			should(formatter.format(date)).be.eql('1.3.2020');
			formatter = new Intl.DateTimeFormat('ja-JP', options);
			should(formatter.format(date)).be.eql('2020/3/1');
		});

		it('2-digit date', () => {
			// 2020-March-1st
			const date = new Date(Date.UTC(2020, 2, 1));
			const options = {
				year: '2-digit',
				month: '2-digit',
				day: '2-digit',
				timeZone: 'UTC'
			};
			let formatter = new Intl.DateTimeFormat('en-US', options);
			should(formatter.format(date)).be.eql('03/01/20');
			formatter = new Intl.DateTimeFormat('de-DE', options);
			should(formatter.format(date)).be.eql('01.03.20');
			formatter = new Intl.DateTimeFormat('ja-JP', options);
			should(formatter.format(date)).be.eql('20/03/01');
		});

		it('12 hour time', () => {
			// 2020-Jan-1st 08:02:05 PM
			const date = new Date(Date.UTC(2020, 0, 1, 20, 2, 5));
			const options = {
				hour: 'numeric',
				minute: '2-digit',
				second: '2-digit',
				hour12: true,
				dayPeriod: 'narrow',
				timeZone: 'UTC'
			};
			let formatter = new Intl.DateTimeFormat('en-US', options);
			should(formatter.format(date)).be.equalOneOf([ '8:02:05 PM', '8:02:05 in the evening' ]);
			formatter = new Intl.DateTimeFormat('de-DE', options);
			should(formatter.format(date)).be.equalOneOf([ '8:02:05 PM', '8:02:05 nachm.', '8:02:05 abends'  ]);
		});

		it('24 hour time', () => {
			// 2020-Jan-1st 08:02:05 PM
			const date = new Date(Date.UTC(2020, 0, 1, 20, 2, 5));
			const options = {
				hour: '2-digit',
				minute: '2-digit',
				second: '2-digit',
				hour12: false,
				timeZone: 'UTC'
			};
			let formatter = new Intl.DateTimeFormat('en-US', options);
			should(formatter.format(date)).be.eql('20:02:05');
			formatter = new Intl.DateTimeFormat('de-DE', options);
			should(formatter.format(date)).be.eql('20:02:05');
			formatter = new Intl.DateTimeFormat('ja-JP', options);
			should(formatter.format(date)).be.eql('20:02:05');
		});

		it('month name', () => {
			// 2020-October-1st
			const date = new Date(Date.UTC(2020, 9, 1));
			let formatter = new Intl.DateTimeFormat('en-US', { month: 'long', timeZone: 'UTC' });
			should(formatter.format(date).toLowerCase()).be.eql('october');
			formatter = new Intl.DateTimeFormat('en-US', { month: 'short', timeZone: 'UTC' });
			should(formatter.format(date).toLowerCase()).be.eql('oct');
			formatter = new Intl.DateTimeFormat('de-DE', { month: 'long', timeZone: 'UTC' });
			should(formatter.format(date).toLowerCase()).be.eql('oktober');
			formatter = new Intl.DateTimeFormat('de-DE', { month: 'short', timeZone: 'UTC' });
			should(formatter.format(date).toLowerCase()).be.eql('okt');
		});

		it('weekday name', () => {
			// 2020-Jan-1st = Wednesday
			const date = new Date(Date.UTC(2020, 0, 1));
			let formatter = new Intl.DateTimeFormat('en-US', { weekday: 'long', timeZone: 'UTC' });
			should(formatter.format(date).toLowerCase()).be.eql('wednesday');
			formatter = new Intl.DateTimeFormat('en-US', { weekday: 'short', timeZone: 'UTC' });
			should(formatter.format(date).toLowerCase()).be.eql('wed');
			formatter = new Intl.DateTimeFormat('de-DE', { weekday: 'long', timeZone: 'UTC' });
			should(formatter.format(date).toLowerCase()).be.eql('mittwoch');
			formatter = new Intl.DateTimeFormat('de-DE', { weekday: 'short', timeZone: 'UTC' });
			should(formatter.format(date).toLowerCase()).be.equalOneOf([ 'mi', 'mi.' ]);
		});

		it.android('fractionalSecondDigits', () => {
			// 2020-Jan-1st 12:00:00.456
			const date = new Date(Date.UTC(2020, 0, 1, 12, 0, 0, 456));
			let formatter = new Intl.DateTimeFormat('en-US', { fractionalSecondDigits: 3, timeZone: 'UTC' });
			should(formatter.format(date)).be.eql('456');
			formatter = new Intl.DateTimeFormat('en-US', { fractionalSecondDigits: 2, timeZone: 'UTC' });
			should(formatter.format(date)).be.eql('45');
			formatter = new Intl.DateTimeFormat('en-US', { fractionalSecondDigits: 1, timeZone: 'UTC' });
			should(formatter.format(date)).be.eql('4');
		});
	});

	describe('#formatToParts()', () => {
		it('validate function', () => {
			const formatter = new Intl.DateTimeFormat();
			should(formatter.formatToParts).not.be.undefined();
			should(formatter.formatToParts).be.a.Function();
			should(formatter.formatToParts(new Date())).be.an.Array();
		});

		it('yyyy/MM/dd h:mm:ss.SSS aa', () => {
			// 2020-March-31 08:15:30.123 PM
			const date = new Date(Date.UTC(2020, 2, 31, 20, 15, 30, 123));
			const formatter = new Intl.DateTimeFormat('en-US', {
				year: 'numeric',
				month: '2-digit',
				day: '2-digit',
				hour: 'numeric',
				minute: '2-digit',
				second: '2-digit',
				fractionalSecondDigits: 3,
				hour12: true,
				timeZone: 'UTC'
			});
			const partsArray = formatter.formatToParts(date);
			let index = 0;
			should(partsArray).be.an.Array();
			should(partsArray[index++]).be.eql({ type: 'month', value: '03' });
			should(partsArray[index++]).be.eql({ type: 'literal', value: '/' });
			should(partsArray[index++]).be.eql({ type: 'day', value: '31' });
			should(partsArray[index++]).be.eql({ type: 'literal', value: '/' });
			should(partsArray[index++]).be.eql({ type: 'year', value: '2020' });
			should(partsArray[index++]).be.eql({ type: 'literal', value: ', ' });
			should(partsArray[index++]).be.eql({ type: 'hour', value: '8' });
			should(partsArray[index++]).be.eql({ type: 'literal', value: ':' });
			should(partsArray[index++]).be.eql({ type: 'minute', value: '15' });
			should(partsArray[index++]).be.eql({ type: 'literal', value: ':' });
			should(partsArray[index++]).be.eql({ type: 'second', value: '30' });
			if (OS_ANDROID || (utilities.isMacOS() && OS_VERSION_MAJOR >= 11)) {
				should(partsArray[index++]).be.eql({ type: 'literal', value: '.' });
				should(partsArray[index++]).be.eql({ type: 'fractionalSecond', value: '123' });
			}
			should(partsArray[index++]).be.eql({ type: 'literal', value: ' ' });
			should(partsArray[index++]).be.eql({ type: 'dayPeriod', value: 'PM' });
			should(partsArray.length).be.eql(index);
		});
	});

	describe('#resolvedOptions()', () => {
		it('validate function', () => {
			const formatter = new Intl.DateTimeFormat();
			should(formatter.resolvedOptions).not.be.undefined();
			should(formatter.resolvedOptions).be.a.Function();
			should(formatter.resolvedOptions()).be.an.Object();
		});

		it('assigned from constructor', () => {
			const options = {
				year: 'numeric',
				month: 'long',
				day: '2-digit',
				weekday: 'long',
				hour: 'numeric',
				minute: '2-digit',
				second: '2-digit',
				hour12: true,
				timeZoneName: 'short'
			};
			if (OS_ANDROID) {
				options.dayPeriod = 'narrow';
				options.fractionalSecondDigits = 3;
			}
			const formatter = new Intl.DateTimeFormat('en-US', options);
			const result = formatter.resolvedOptions();
			for (const key in options) {
				should(result[key]).be.eql(options[key]);
			}
		});

		it('timeZone', () => {
			should(Intl.DateTimeFormat().resolvedOptions().timeZone).be.a.String();
			let formatter = new Intl.DateTimeFormat('en-US', { timeZone: 'UTC' });
			should(formatter.resolvedOptions().timeZone).be.eql('UTC');
			formatter = new Intl.DateTimeFormat('en-US', { timeZone: 'Etc/GMT+8' });
			should(formatter.resolvedOptions().timeZone).be.eql('Etc/GMT+8');
			formatter = new Intl.DateTimeFormat('en-US', { timeZone: 'America/Los_Angeles' });
			should(formatter.resolvedOptions().timeZone).be.eql('America/Los_Angeles');
		});

		it.android('dateStyle', () => {
			const formatter = new Intl.DateTimeFormat('en-US', { dateStyle: 'long' });
			const result = formatter.resolvedOptions();
			should(result.day).be.equalOneOf([ 'numeric', '2-digit' ]);
			should(result.month).be.equalOneOf([ 'numeric', '2-digit', 'long', 'short', 'narrow' ]);
			should(result.year).be.equalOneOf([ 'numeric', '2-digit' ]);
		});

		it.android('timeStyle', () => {
			const formatter = new Intl.DateTimeFormat('en-US', { timeStyle: 'long' });
			const result = formatter.resolvedOptions();
			should(result.hour).be.equalOneOf([ 'numeric', '2-digit' ]);
			should(result.minute).be.equalOneOf([ 'numeric', '2-digit' ]);
			should(result.second).be.equalOneOf([ 'numeric', '2-digit' ]);
		});
	});

	describe('#supportedLocalesOf()', () => {
		it('is a Function', () => {
			should(Intl.DateTimeFormat.supportedLocalesOf).not.be.undefined();
			should(Intl.DateTimeFormat.supportedLocalesOf).be.a.Function();
		});

		it('empty Array', () => {
			let locales = Intl.DateTimeFormat.supportedLocalesOf([]);
			should(locales).be.an.Array();
			should(locales.length).be.eql(0);
		});

		it('Array with 1 String', () => {
			const locales = Intl.DateTimeFormat.supportedLocalesOf([ 'en-US' ]);
			should(locales).be.an.Array();
			should(locales.length).be.eql(1);
			should(locales[0]).be.eql('en-US');
		});

		it('Array with 2 Strings', () => {
			const locales = Intl.DateTimeFormat.supportedLocalesOf([ 'en-US', 'de-DE' ]);
			should(locales).be.an.Array();
			should(locales.length).be.eql(2);
			should(locales[0]).be.eql('en-US');
			should(locales[1]).be.eql('de-DE');
		});

		it('with invalid locale string', () => {
			const locales = Intl.DateTimeFormat.supportedLocalesOf([ 'uh-oh', 'en-US' ]);
			should(locales).be.an.Array();
			should(locales.length).be.eql(1);
			should(locales[0]).be.eql('en-US');
		});
	});
});
