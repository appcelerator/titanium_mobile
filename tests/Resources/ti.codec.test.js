/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2020-Present by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
/* eslint-env mocha */
/* eslint no-unused-expressions: "off" */
/* eslint no-undef: "off" */
'use strict';

const should = require('./utilities/assertions');

describe('Titanium.Codec', () => {

	describe('.apiName', () => {
		it('is a String', () => {
			should(Ti.Codec).have.a.readOnlyProperty('apiName').which.is.a.String();
		});

		it('equals Ti.Codec', () => {
			should(Ti.Codec.apiName).eql('Ti.Codec');
		});
	});

	describe('.BIG_ENDIAN', () => {
		it('is a Number', () => {
			should(Ti.Codec).have.a.constant('BIG_ENDIAN').which.is.a.Number();
		});
	});

	describe('.CHARSET_ASCII', () => {
		it('is a String', () => {
			should(Ti.Codec).have.a.constant('CHARSET_ASCII').which.is.a.String();
		});

		it('is \'ascii\'', () => {
			should(Ti.Codec.CHARSET_ASCII).eql('ascii');
		});
	});

	describe('.CHARSET_ISO_LATIN_1', () => {
		it('is a String', () => {
			should(Ti.Codec).have.a.constant('CHARSET_ISO_LATIN_1').which.is.a.String();
		});

		it('is \'latin1\'', () => {
			should(Ti.Codec.CHARSET_ISO_LATIN_1).eql('latin1');
		});
	});

	describe('.CHARSET_UTF16', () => {
		it('is a String', () => {
			should(Ti.Codec).have.a.constant('CHARSET_UTF16').which.is.a.String();
		});

		it('is \'utf16\'', () => {
			should(Ti.Codec.CHARSET_UTF16).eql('utf16');
		});
	});

	describe('.CHARSET_UTF16BE', () => {
		it('is a String', () => {
			should(Ti.Codec).have.a.constant('CHARSET_UTF16BE').which.is.a.String();
		});

		it('is \'utf16be\'', () => {
			should(Ti.Codec.CHARSET_UTF16BE).eql('utf16be');
		});
	});

	describe('.CHARSET_UTF16LE', () => {
		it('is a String', () => {
			should(Ti.Codec).have.a.constant('CHARSET_UTF16LE').which.is.a.String();
		});

		it('is \'utf16le\'', () => {
			should(Ti.Codec.CHARSET_UTF16LE).eql('utf16le');
		});
	});

	describe('.CHARSET_UTF8', () => {
		it('is a String', () => {
			should(Ti.Codec).have.a.constant('CHARSET_UTF8').which.is.a.String();
		});

		it('is \'utf8\'', () => {
			should(Ti.Codec.CHARSET_UTF8).eql('utf8');
		});
	});

	describe('.LITTLE_ENDIAN', () => {
		it('is a Number', () => {
			should(Ti.Codec).have.a.constant('LITTLE_ENDIAN').which.is.a.Number();
		});
	});

	describe('.TYPE_BYTE', () => {
		it('is a String', () => {
			should(Ti.Codec).have.a.constant('TYPE_BYTE').which.is.a.String();
		});

		it('is \'byte\'', () => {
			should(Ti.Codec.TYPE_BYTE).eql('byte');
		});
	});

	describe('.TYPE_DOUBLE', () => {
		it('is a String', () => {
			should(Ti.Codec).have.a.constant('TYPE_DOUBLE').which.is.a.String();
		});

		it('is \'double\'', () => {
			should(Ti.Codec.TYPE_DOUBLE).eql('double');
		});
	});

	describe('.TYPE_FLOAT', () => {
		it('is a String', () => {
			should(Ti.Codec).have.a.constant('TYPE_FLOAT').which.is.a.String();
		});

		it('is \'float\'', () => {
			should(Ti.Codec.TYPE_FLOAT).eql('float');
		});
	});

	describe('.TYPE_INT', () => {
		it('is a String', () => {
			should(Ti.Codec).have.a.constant('TYPE_INT').which.is.a.String();
		});

		it('is \'int\'', () => {
			should(Ti.Codec.TYPE_INT).eql('int');
		});
	});

	describe('.TYPE_LONG', () => {
		it('is a String', () => {
			should(Ti.Codec).have.a.constant('TYPE_LONG').which.is.a.String();
		});

		it('is \'long\'', () => {
			should(Ti.Codec.TYPE_LONG).eql('long');
		});
	});

	describe('.TYPE_SHORT', () => {
		it('is a String', () => {
			should(Ti.Codec).have.a.constant('TYPE_SHORT').which.is.a.String();
		});

		it('is \'short\'', () => {
			should(Ti.Codec.TYPE_SHORT).eql('short');
		});
	});

	describe('#decodeNumber', () => {
		it('is a Function', () => {
			should(Ti.Codec.decodeNumber).be.a.Function();
		});

		it('should throw Error when source not specified', () => {
			should(() => {
				const dest = Ti.createBuffer({
					length: 8
				});
				Ti.Codec.decodeNumber({
					dest,
					type: Ti.Codec.TYPE_LONG,
				});
			}).throw();
		});

		it('should throw Error when type not specified', () => {
			should(() => {
				const dest = Ti.createBuffer({
					length: 8
				});
				Ti.Codec.decodeNumber({
					source: 123,
					dest
				});
			}).throw();
		});

		it('can decode Integers', () => {
			var buffer = Ti.createBuffer({
				length: 8
			});
			buffer[0] = 154;
			buffer[1] = 120;
			buffer[2] = 86;
			buffer[3] = 52;
			buffer[4] = 18;
			var n = Ti.Codec.decodeNumber({
				source: buffer,
				type: Ti.Codec.TYPE_LONG,
				byteOrder: Ti.Codec.LITTLE_ENDIAN
			});
			should(n).eql(78187493530);
			n = Ti.Codec.decodeNumber({
				source: buffer,
				type: Ti.Codec.TYPE_INT,
				byteOrder: Ti.Codec.BIG_ENDIAN,
				position: 1
			});
			should(n).eql(2018915346);
			n = Ti.Codec.decodeNumber({
				source: buffer,
				type: Ti.Codec.TYPE_SHORT,
				byteOrder: Ti.Codec.LITTLE_ENDIAN
			});
			// down casting discards the low bits (0x563412)
			should(n).eql(30874);
		});

		it('can Decode Floating Point', () => {
			var buffer = Ti.createBuffer({
				length: 8
			});
			// Should be ~1/3
			buffer[0] = 63;
			buffer[1] = 213;
			for (var i = 2; i < 8; i++) {
				buffer[i] = 85;
			}
			var n = Ti.Codec.decodeNumber({
				source: buffer,
				type: Ti.Codec.TYPE_DOUBLE,
				byteOrder: Ti.Codec.BIG_ENDIAN
			});
			should(n).eql(1 / 3);
			// 0x3ff3c0ca4283de1b -> 1.23456789
			buffer.clear();
			buffer.length = 8;
			buffer[0] = 63;
			buffer[1] = 243;
			buffer[2] = 192;
			buffer[3] = 202;
			buffer[4] = 66;
			buffer[5] = 131;
			buffer[6] = 222;
			buffer[7] = 27;
			n = Ti.Codec.decodeNumber({
				source: buffer,
				type: Ti.Codec.TYPE_DOUBLE,
				byteOrder: Ti.Codec.BIG_ENDIAN
			});
			should(n).eql(1.23456789);
			// 0x3f9e0419 ~> 1.2345 (float / little endian)
			buffer.clear();
			buffer.length = 4;
			buffer[0] = 25;
			buffer[1] = 4;
			buffer[2] = 158;
			buffer[3] = 63;
			n = Ti.Codec.decodeNumber({
				source: buffer,
				type: Ti.Codec.TYPE_FLOAT,
				byteOrder: Ti.Codec.LITTLE_ENDIAN
			});
			should(n.toFixed(4)).eql('1.2345');
		});
	});

	describe('#decodeString', () => {
		it('is a Function', () => {
			should(Ti.Codec.decodeString).be.a.Function();
		});

		it('can decode Strings', () => {
			const TEST = 'spät';
			let buffer = Ti.createBuffer({
				length: 5
			});
			buffer[0] = 115; // s
			buffer[1] = 112; // p
			buffer[2] = 195; // char table
			buffer[3] = 164; // umlaut-a
			buffer[4] = 116; // t
			should(Ti.Codec.decodeString({
				source: buffer,
				charset: Ti.Codec.CHARSET_UTF8
			})).eql(TEST);

			buffer.clear();

			// UTF-16 LE
			buffer.length = 10; // BOM=2, then 4 chars * 2
			// BOM: Little Endian Encoding
			buffer[0] = 255;
			buffer[1] = 254;
			// 's'
			buffer[2] = 115;
			buffer[3] = 0;
			// 'p'
			buffer[4] = 112;
			buffer[5] = 0;
			// 'ä'
			buffer[6] = 228;
			buffer[7] = 0;
			// 't'
			buffer[8] = 116;
			buffer[9] = 0;
			should(Ti.Codec.decodeString({
				source: buffer,
				charset: Ti.Codec.CHARSET_UTF16
			})).eql(TEST);

			// UTF-16 BE
			// BOM: Big Endian Encoding
			buffer[1] = 255;
			buffer[0] = 254;
			// 's'
			buffer[3] = 115;
			buffer[2] = 0;
			// 'p'
			buffer[5] = 112;
			buffer[4] = 0;
			// 'ä'
			buffer[7] = 228;
			buffer[6] = 0;
			// 't'
			buffer[9] = 116;
			buffer[8] = 0;
			should(Ti.Codec.decodeString({
				source: buffer,
				charset: Ti.Codec.CHARSET_UTF16
			})).eql(TEST);
			// Test decoding of a string with empty data after it using 'length'
			buffer = Ti.createBuffer({
				value: 'The system is down',
				length: 100
			});
			should(buffer.length).eql(100);
			const str = Ti.Codec.decodeString({
				source: buffer,
				length: 18
			});
			should(str).eql('The system is down');
		});
	});

	describe('#encodeNumber', () => {
		it('is a Function', () => {
			should(Ti.Codec.encodeNumber).be.a.Function();
		});

		it('should throw Error when "dest" not specified', () => {
			should(() => {
				Ti.Codec.encodeNumber({
					source: 123,
					type: Ti.Codec.TYPE_LONG,
				});
			}).throw();
		});

		it('should throw Error when "source" not specified', () => {
			should(() => {
				const dest = Ti.createBuffer({
					length: 8
				});
				Ti.Codec.encodeNumber({
					dest,
					type: Ti.Codec.TYPE_LONG,
				});
			}).throw();
		});

		it('should throw Error when "type" not specified', () => {
			should(() => {
				const dest = Ti.createBuffer({
					length: 8
				});
				Ti.Codec.encodeNumber({
					source: 123,
					dest
				});
			}).throw();
		});

		it('can encode Integers', () => {
			var buffer = Ti.createBuffer({
					length: 8
				}),
				i;
			Ti.Codec.encodeNumber({
				source: 78187493530,
				dest: buffer,
				type: Ti.Codec.TYPE_LONG
			});
			if (Ti.Codec.nativeByteOrder == Ti.Codec.BIG_ENDIAN) { // eslint-disable-line eqeqeq
				for (i = 0; i < 3; i++) {
					should(buffer[i]).eql(0);
				}
				should(buffer[3]).eql(18);
				should(buffer[4]).eql(52);
				should(buffer[5]).eql(86);
				should(buffer[6]).eql(120);
				should(buffer[7]).eql(154);
			} else {
				should(buffer[4]).eql(18);
				should(buffer[3]).eql(52);
				should(buffer[2]).eql(86);
				should(buffer[1]).eql(120);
				should(buffer[0]).eql(154);
				for (i = 5; i < 8; i++) {
					should(buffer[i]).eql(0);
				}
			}
			buffer.length = 10;
			buffer.clear();
			Ti.Codec.encodeNumber({
				source: 78187493530,
				dest: buffer,
				type: Ti.Codec.TYPE_LONG,
				byteOrder: Ti.Codec.BIG_ENDIAN,
				position: 2
			});
			for (i = 2; i < 5; i++) {
				should(buffer[i]).eql(0);
			}
			should(buffer[5]).eql(18);
			should(buffer[6]).eql(52);
			should(buffer[7]).eql(86);
			should(buffer[8]).eql(120);
			should(buffer[9]).eql(154);
			buffer.length = 4;
			buffer.clear();
			// down casting discards the high bits (0x12)
			Ti.Codec.encodeNumber({
				source: 78187493530,
				dest: buffer,
				type: Ti.Codec.TYPE_INT,
				byteOrder: Ti.Codec.BIG_ENDIAN
			});
			should(buffer[0]).eql(52);
			should(buffer[1]).eql(86);
			should(buffer[2]).eql(120);
			should(buffer[3]).eql(154);
			buffer.length = 2;
			buffer.clear();
			// down casting discards the high bits (0x3)
			Ti.Codec.encodeNumber({
				source: 214375,
				dest: buffer,
				type: Ti.Codec.TYPE_SHORT,
				byteOrder: Ti.Codec.BIG_ENDIAN
			});
			should(buffer[0]).eql(69);
			should(buffer[1]).eql(103);
			buffer.clear();
			buffer[0] = 63;
			Ti.Codec.encodeNumber({
				source: 63,
				dest: buffer,
				position: 1,
				type: Ti.Codec.TYPE_BYTE
			});
			should(buffer[0]).eql(buffer[1]);
		});

		it('can encode Floating Point', () => {
			var buffer = Ti.createBuffer({
				length: 8
			});
			// 1/3 -> 0x3fd5555555555555
			Ti.Codec.encodeNumber({
				source: 1 / 3,
				dest: buffer,
				type: Ti.Codec.TYPE_DOUBLE,
				byteOrder: Ti.Codec.BIG_ENDIAN
			});
			should(buffer[0]).eql(63);
			should(buffer[1]).eql(213);
			for (var i = 2; i < 8; i++) {
				should(buffer[i]).eql(85);
			}
			// 1.23456789 -> 0x3ff3c0ca4283de1b
			buffer = Ti.createBuffer({
				value: 1.23456789,
				type: Ti.Codec.TYPE_DOUBLE,
				byteOrder: Ti.Codec.BIG_ENDIAN
			});
			should(buffer[0]).eql(63);
			should(buffer[1]).eql(243);
			should(buffer[2]).eql(192);
			should(buffer[3]).eql(202);
			should(buffer[4]).eql(66);
			should(buffer[5]).eql(131);
			should(buffer[6]).eql(222);
			should(buffer[7]).eql(27);
			// 1.2345 -> 0x3f9e0419 (float)
			buffer.clear();
			buffer.length = 4;
			Ti.Codec.encodeNumber({
				source: 1.2345,
				dest: buffer,
				type: Ti.Codec.TYPE_FLOAT,
				byteOrder: Ti.Codec.BIG_ENDIAN
			});
			should(buffer[0]).eql(63);
			should(buffer[1]).eql(158);
			should(buffer[2]).eql(4);
			should(buffer[3]).eql(25);
		});
	});

	describe('#encodeString', () => {
		it('is a Function', () => {
			should(Ti.Codec.encodeString).be.a.Function();
		});

		it('can encode Strings', () => {
			const PHRASE = 'Wer reitet so spät durch Nacht und Wind?';
			const buffer = Ti.createBuffer({
				length: 1024
			});
			let length = Ti.Codec.encodeString({
				source: PHRASE,
				dest: buffer
			});
			should(length).eql(PHRASE.length + 1);
			// +1 for the umlaut char set byte
			const umlautLoc = PHRASE.indexOf('ä');
			should(buffer[umlautLoc]).eql(195);
			// C3 char set in utf-8
			should(buffer[umlautLoc + 1]).eql(164);
			// umlaut-a itself
			buffer.clear();
			buffer.length = 1024;
			length = Ti.Codec.encodeString({
				source: PHRASE,
				dest: buffer,
				charset: Ti.Codec.CHARSET_UTF16
			});
			should(length).eql(2 * PHRASE.length + 2);
			// The final '+ 2' is for the BOM.
			buffer.length = length;
			// round trip?
			should(Ti.Codec.decodeString({
				source: buffer,
				charset: Ti.Codec.CHARSET_UTF16
			})).eql(PHRASE);
		});
	});

	describe('#nativeByteOrder', () => {
		it('is a getter', () => {
			should(Ti.Codec).have.a.getter('nativeByteOrder');
		});

		it('returns one of [Ti.Codec.BIG_ENDIAN,Ti.Codec.LITTLE_ENDIAN]', () => {
			should([ Ti.Codec.BIG_ENDIAN, Ti.Codec.LITTLE_ENDIAN ]).containEql(Ti.Codec.nativeByteOrder);
		});
	});

});
