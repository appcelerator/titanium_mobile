/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-2018 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
/* eslint-env mocha */
/* eslint no-unused-expressions: "off" */
'use strict';
var should = require('./utilities/assertions');

describe('Titanium', function () {
	it('#createBuffer()', function () {
		var buffer;
		should(Ti.createBuffer).be.a.Function();
		buffer = Ti.createBuffer();
		should(buffer).be.an.Object();
	});
});

describe('Titanium.Buffer', function () {
	it('.apiName', function () {
		var buffer = Ti.createBuffer();
		should(buffer).have.a.readOnlyProperty('apiName').which.is.a.String();
		should(buffer.apiName).be.eql('Ti.Buffer');
	});

	// TODO Add tests for insert/append/fill/copy that use negative offsets/lengths
	// TODO Add tests for methods/properties and pass in invalid types (i.e. pass in null/undefined/string for Number args)

	describe('.length', function () {
		it('defaults to 0 when not specified', function () {
			var buffer = Ti.createBuffer();
			should(buffer.length).eql(0);
		});

		it('matches value from constructor options, and fills with 0', function () {
			var buffer = Ti.createBuffer({
					length: 100
				}),
				i;
			should(buffer.length).eql(100);
			for (i = 0; i < 100; i++) {
				should(buffer[i]).eql(0);
			}
		});
	});

	describe('#append()', function () {
		it('is a Function', function () {
			var buffer = Ti.createBuffer();
			should(buffer.append).be.a.Function();
		});

		it('throws Error unless we get at least 1 argument', function () {
			var buffer1 = Ti.createBuffer({
				length: 20
			});
			should(function () {
				buffer1.append();
			}).throw();
		});

		it('throws Error if sourceLength argument extends beyond sourceBuffer length', function () {
			var buffer1 = Ti.createBuffer({
					length: 20
				}),
				buffer2 = Ti.createBuffer({
					length: 5
				});
			should(function () {
				buffer1.append(buffer2, 0, 6);
			}).throw();
		});

		it('throws Error if sourceOffset argument extends beyond sourceBuffer length', function () {
			var buffer1 = Ti.createBuffer({
					length: 20
				}),
				buffer2 = Ti.createBuffer({
					length: 5
				});
			should(function () {
				buffer1.append(buffer2, 5, 1);
			}).throw();
		});

		it('throws Error if sourceOffset + sourceLength extends beyond sourceBuffer length', function () {
			var buffer1 = Ti.createBuffer({
					length: 20
				}),
				buffer2 = Ti.createBuffer({
					length: 5
				});
			should(function () {
				buffer1.append(buffer2, 3, 5);
			}).throw();
		});

		// TODO Add test that you must specify sourceoffset + sourceLength, not just sourceoffset

		it('concatenates Buffer at end, extending length', function () {
			var buffer1 = Ti.createBuffer({
					length: 20
				}),
				buffer2 = Ti.createBuffer({
					length: 5
				}),
				n;
			buffer2[0] = 100;
			buffer2[1] = 101;

			n = buffer1.append(buffer2);
			should(buffer1.length).eql(25);
			should(buffer1[20]).eql(buffer2[0]);
			should(buffer1[21]).eql(buffer2[1]);
			should(n).eql(buffer2.length);
		});

		it('concatenates portion of Buffer at end, extending destination length', function () {
			var buffer1 = Ti.createBuffer({
					length: 20
				}),
				buffer2 = Ti.createBuffer({
					length: 5
				}),
				n;
			buffer2[3] = 100;
			buffer2[4] = 101;
			n = buffer1.append(buffer2, 3, 2);
			should(buffer1.length).eql(22);
			should(buffer1[20]).eql(buffer2[3]);
			should(buffer1[21]).eql(buffer2[4]);
			should(n).eql(2);
		});
	});

	describe('#insert()', function () {
		it('is a Function', function () {
			var buffer = Ti.createBuffer();
			should(buffer.insert).be.a.Function();
		});

		it('throws Error unless we get at least 2 arguments', function () {
			var buffer1 = Ti.createBuffer({
					length: 20
				}),
				buffer2 = Ti.createBuffer({
					length: 5
				});
			should(function () {
				buffer1.insert(buffer2);
			}).throw();
		});

		it('throws Error if inserting past length of destination Buffer', function () {
			var buffer1 = Ti.createBuffer({
					length: 20
				}),
				buffer2 = Ti.createBuffer({
					length: 5
				});
			should(function () {
				buffer1.insert(buffer2, 99);
			}).throw();
		});

		it('throws Error if inserting past length of source Buffer', function () {
			var buffer1 = Ti.createBuffer({
					length: 20
				}),
				buffer2 = Ti.createBuffer({
					length: 5
				});
			should(function () {
				buffer1.insert(buffer2, 0, 0, 99);
			}).throw();
		});

		it('throws Error if sourceOffset + sourceLength extends past length of source Buffer', function () {
			var buffer1 = Ti.createBuffer({
					length: 20
				}),
				buffer2 = Ti.createBuffer({
					length: 5
				});
			should(function () {
				buffer1.insert(buffer2, 0, 4, 2);
			}).throw();
		});

		// TODO Add test that you must specify sourceoffset + sourceLength, not just sourceoffset

		it('inserts Buffer at offset', function () {
			var buffer1 = Ti.createBuffer({
					length: 20
				}),
				buffer2 = Ti.createBuffer({
					length: 5
				}),
				initialLength,
				n;
			buffer2[0] = 103;
			buffer2[1] = 104;

			initialLength = buffer1.length;
			n = buffer1.insert(buffer2, 3);
			should(buffer1.length).eql(initialLength + buffer2.length); // dest buffer got expanded
			should(buffer1[3]).eql(buffer2[0]); // at start offset of insert, matches first offset of source buffer
			should(buffer1[4]).eql(buffer2[1]);
			should(n).eql(buffer2.length); // should have inserted the full length of buffer2
		});

		it('inserts portion of Buffer at offset', function () {
			var buffer1 = Ti.createBuffer({
					length: 20
				}),
				buffer2 = Ti.createBuffer({
					length: 5
				}),
				n;
			buffer2[0] = 103;
			buffer2[1] = 104;
			buffer2[2] = 105;
			n = buffer1.insert(buffer2, 3, 1, 2);
			should(buffer1.length).eql(22);
			should(buffer1[3]).eql(buffer2[1]);
			should(buffer1[4]).eql(buffer2[2]);
			should(n).eql(2);
		});

		it('blogExample', function () {
			var buffer = Ti.createBuffer({
					length: 2
				}),
				buffer2;
			buffer[0] = 1;
			buffer[1] = 3;

			buffer2 = Ti.createBuffer({
				length: 1
			});
			buffer2[0] = 2;
			buffer.insert(buffer2, 1);
			should(String(buffer[0]) + String(buffer[1]) + String(buffer[2])).eql('123');
			should(buffer.length).eql(3);
			should(buffer[0]).eql(1);
			should(buffer[1]).eql(2);
			should(buffer[2]).eql(3);
			should(buffer2.length).eql(1);
			// unchanged
			should(buffer2[0]).eql(2);
		});
	});

	describe('#copy()', function () {
		it('is a Function', function () {
			var buffer = Ti.createBuffer();
			should(buffer.copy).be.a.Function();
		});

		it('throws Error unless we get at least 1 argument', function () {
			var buffer1 = Ti.createBuffer({
				length: 20
			});
			should(function () {
				buffer1.copy();
			}).throw();
		});

		it('throws Error if offset argument extends beyond destination length', function () {
			var buffer1 = Ti.createBuffer({
					length: 20
				}),
				buffer2 = Ti.createBuffer({
					length: 5
				});
			should(function () {
				buffer1.copy(buffer2, 25);
			}).throw();
		});

		// FIXME Windows doesn't throw Error
		it.windowsBroken('throws Error if sourceLength extends beyond destination length', function () {
			var buffer1 = Ti.createBuffer({
					length: 20
				}),
				buffer2 = Ti.createBuffer({
					length: 5
				});
			// FIXME Should this throw an Error? Just return the correct number of bytes we could copy?
			// Java/Android throws an error here.
			// I believe that iOS just picks the min it can copy and returns that
			should(function () {
				buffer1.copy(buffer2, 19, 0, 5); // can't copy 5 characters, since we're doing it at last index of dest
			}).throw();
		});

		it('throws Error if sourceLength argument extends beyond sourceBuffer length', function () {
			var buffer1 = Ti.createBuffer({
					length: 20
				}),
				buffer2 = Ti.createBuffer({
					length: 5
				});
			should(function () {
				buffer1.copy(buffer2, 0, 0, 6);
			}).throw();
		});

		it('throws Error if sourceOffset argument extends beyond sourceBuffer length', function () {
			var buffer1 = Ti.createBuffer({
					length: 20
				}),
				buffer2 = Ti.createBuffer({
					length: 5
				});
			should(function () {
				buffer1.copy(buffer2, 0, 5, 1);
			}).throw();
		});

		it('throws Error if sourceOffset + sourceLength extends past length of source Buffer', function () {
			var buffer1 = Ti.createBuffer({
					length: 20
				}),
				buffer2 = Ti.createBuffer({
					length: 5
				});
			should(function () {
				buffer1.copy(buffer2, 0, 4, 2);
			}).throw();
		});

		it('copies full Buffer to offset', function () {
			var buffer1 = Ti.createBuffer({
					length: 20
				}),
				buffer2 = Ti.createBuffer({
					length: 5
				}),
				n;
			buffer2[0] = 109;
			buffer2[1] = 110;

			n = buffer1.copy(buffer2, 0);
			should(buffer1.length).eql(20);
			should(buffer1[0]).eql(buffer2[0]);
			should(buffer1[1]).eql(buffer2[1]);
			should(n).eql(5);
		});

		it('copies portion of Buffer to offset', function () {
			var buffer1 = Ti.createBuffer({
					length: 20
				}),
				buffer2 = Ti.createBuffer({
					length: 5
				}),
				n;
			buffer2[0] = 109;
			buffer2[1] = 110;

			n = buffer1.copy(buffer2, 15, 0, 2);
			should(buffer1.length).eql(20);
			should(buffer1[15]).eql(buffer2[0]);
			should(buffer1[16]).eql(buffer2[1]);
			should(n).eql(2);
		});

		// TODO Test if we can copy a buffer onto itself! (and what happens if you overlap the positions/range!)
	});

	describe('#clone()', function () {
		it('is a Function', function () {
			var buffer = Ti.createBuffer();
			should(buffer.clone).be.a.Function();
		});

		it('throws Error if offset argument extends beyond length', function () {
			var buffer1 = Ti.createBuffer({
				length: 20
			});
			should(function () {
				buffer1.clone(25, 10);
			}).throw();
		});

		it('throws Error if offset + length extends beyond length', function () {
			var buffer1 = Ti.createBuffer({
				length: 20
			});
			should(function () {
				buffer1.clone(10, 20);
			}).throw();
		});

		it('copies full Buffer', function () {
			var buffer1 = Ti.createBuffer({ length: 20 }),
				buffer2;
			buffer1[0] = 100;
			buffer1[6] = 103;
			buffer1[12] = 106;
			buffer1[18] = 109;

			buffer2 = buffer1.clone();
			should(buffer2.length).eql(buffer1.length);
			should(buffer2).not.equal(buffer1); // not exact same object
			// make sure type and byteOrder are the same!
			should(buffer2.byteOrder).eql(buffer1.byteOrder);
			should(buffer2.type).eql(buffer1.type);
			should(buffer2).eql(buffer1);
			// TODO Check each index
		});

		it('copies portion of Buffer', function () {
			var buffer1 = Ti.createBuffer({ length: 20 }),
				buffer2;
			buffer1[0] = 100;
			buffer1[6] = 103;
			buffer1[12] = 106;
			buffer1[18] = 109;

			buffer2 = buffer1.clone(6, 13);
			should(buffer2.length).eql(13);
			should(buffer2[0]).eql(buffer1[6]);
			should(buffer2[6]).eql(buffer1[12]);
			should(buffer2[12]).eql(buffer1[18]);
			// make sure type and byteOrder are the same!
			should(buffer2.byteOrder).eql(buffer1.byteOrder);
			should(buffer2.type).eql(buffer1.type);
		});

		// FIXME Windows returns buffer1.byteOrder=0 and buffer2.byteOrder=1
		it.windowsBroken('copies type, byteOrder, and value of fully cloned Buffer', function () {
			var buffer1 = Ti.createBuffer({
					value: 305419896,
					type: Ti.Codec.TYPE_LONG,
					byteOrder: Ti.Codec.BIG_ENDIAN
				}),
				buffer2 = buffer1.clone();
			Ti.API.info('BIG_ENDIAN: ' + Ti.Codec.BIG_ENDIAN);
			Ti.API.info('LITTLE_ENDIAN: ' + Ti.Codec.LITTLE_ENDIAN);
			Ti.API.info('buffer1: ' + buffer1.byteOrder);
			Ti.API.info('buffer2: ' + buffer2.byteOrder);
			should(buffer2.length).eql(buffer1.length);
			should(buffer2.byteOrder).eql(buffer1.byteOrder);
			should(buffer2.type).eql(buffer1.type);
			should(buffer2.value).eql(buffer1.value);
		});
		// TODO What happens if we do partial clone with a type/byteOrder/value? I don't think we can copy value property.
	});

	describe('#fill()', function () {
		it('is a Function', function () {
			var buffer = Ti.createBuffer();
			should(buffer.fill).be.a.Function();
		});

		// FIXME Get working for iOS - doesn't throw exception when we expect
		it('throws Error unless we get at least 1 argument', function () {
			var buffer = Ti.createBuffer({
				length: 20
			});
			should(function () {
				buffer.fill();
			}).throw();
		});

		it('throws Error if specified length goes beyond length of Buffer', function () {
			var buffer = Ti.createBuffer({
				length: 20
			});
			should(function () {
				buffer.fill(102, 0, 99);
			}).throw();
		});

		it('throws Error if offset + length extends beyond length of Buffer', function () {
			var buffer = Ti.createBuffer({
				length: 20
			});
			should(function () {
				buffer.fill(100, 19, 2);
			}).throw();
		});

		it('fills entire buffer with single value if only value specified', function () {
			var buffer = Ti.createBuffer({
					length: 20
				}),
				i;
			buffer.fill(100);
			for (i = 0; i < 20; i++) {
				should(buffer[i]).eql(100);
			}
		});

		// TODO Add test that you must specify sourceoffset + sourceLength, not just sourceoffset

		it('fills portion of Buffer based on specified offset and length', function () {
			var buffer = Ti.createBuffer({
					length: 20
				}),
				i;
			buffer.fill(101, 5, 10);
			// offsets 0-4 should be 0
			for (i = 0; i < 5; i++) {
				should(buffer[i]).eql(0);
			}
			// offsets 5-14 should be 101
			for (i = 5; i < 15; i++) {
				should(buffer[i]).eql(101);
			}
			// offsets 15-20 should be 0
			for (i = 15; i < 20; i++) {
				should(buffer[i]).eql(0);
			}
		});

		// TODO Add test that we can fill the last offset (no off by one error)
	});

	describe('#clear()', function () {
		it('is a Function', function () {
			var buffer = Ti.createBuffer();
			should(buffer.clear).be.a.Function();
		});

		it('retains length but fills with 0', function () {
			var buffer = Ti.createBuffer({
					length: 100
				}),
				i;
			// fill with 99
			buffer.fill(99);
			for (i = 0; i < 100; i++) {
				should(buffer[i]).eql(99);
			}
			// then clear
			buffer.clear();
			// length should stay same
			should(buffer.length).eql(100);
			// fills with 0
			for (i = 0; i < 100; i++) {
				should(buffer[i]).eql(0);
			}
		});
	});

	describe('#release()', function () {
		it('is a Function', function () {
			var buffer = Ti.createBuffer();
			should(buffer.release).be.a.Function();
		});

		it('sets length back to 0', function () {
			var buffer = Ti.createBuffer({
				length: 100
			});
			buffer.release();
			should(buffer.length).eql(0);
		});
	});

	it('#toString() and #toBlob()', function () {
		var buffer,
			blob;
		// just a simple ascii string
		buffer = Ti.createBuffer({
			length: 12
		});
		buffer[0] = 97; // a
		buffer[1] = 112; // p
		buffer[2] = 112; // p
		buffer[3] = 99; // c
		buffer[4] = 101; // e
		buffer[5] = 108; // l
		buffer[6] = 101; // e
		buffer[7] = 114; // r
		buffer[8] = 97; // a
		buffer[9] = 116; // t
		buffer[10] = 111; // o
		buffer[11] = 114; // r

		should(buffer.toString()).eql('appcelerator');

		blob = buffer.toBlob();
		should(blob.length).eql(buffer.length);
		should(blob.text).eql('appcelerator');
	});

	it('defaults to UTF-8', function () {
		// default UTF8
		var buffer = Ti.createBuffer({
			value: 'appcelerator'
		});
		should(buffer.length).eql(12);
		should(buffer[0]).eql(97); // a
		should(buffer[1]).eql(112); // p
		should(buffer[2]).eql(112); // p
		should(buffer[3]).eql(99); // c
		should(buffer[4]).eql(101); // e
		should(buffer[5]).eql(108); // l
		should(buffer[6]).eql(101); // e
		should(buffer[7]).eql(114); // r
		should(buffer[8]).eql(97); // a
		should(buffer[9]).eql(116); // t
		should(buffer[10]).eql(111); // o
		should(buffer[11]).eql(114); // r
	});

	it('encode with UTF-16', function () {
		var buffer = Ti.createBuffer({
				value: 'appcelerator',
				type: Ti.Codec.CHARSET_UTF16
			}),
			length = 24,
			start = 0;
		// some impls will add a UTF-16 BOM
		// http://en.wikipedia.org/wiki/UTF-16/UCS-2#Byte_order_encoding_schemes
		if (buffer[0] === 255 && buffer[1] === 254) {
			// UTF-16 BE
			length = 26;
			start = 1;
		} else if (buffer[0] === 254 && buffer[1] === 255) {
			// UTF-16 LE
			length = 26;
			start = 2;
		}
		should(buffer.length).eql(length);
		should(buffer.byteOrder).eql(Ti.Codec.nativeByteOrder);
		should(buffer[start + 1]).eql(97); // a
		should(buffer[start + 3]).eql(112); // p
		should(buffer[start + 5]).eql(112); // p
		should(buffer[start + 7]).eql(99); // c
		should(buffer[start + 9]).eql(101); // e
		should(buffer[start + 11]).eql(108); // l
		should(buffer[start + 13]).eql(101); // e
		should(buffer[start + 15]).eql(114); // r
		should(buffer[start + 17]).eql(97); // a
		should(buffer[start + 19]).eql(116); // t
		should(buffer[start + 21]).eql(111); // o
		should(buffer[start + 23]).eql(114); // r
	});

	describe('.byteOrder', function () {
		// TODO Write tests for BYTE, DOUBLE, FLOAT, SHORT, with LITTLE/BIG_ENDIAN
		it('BIG_ENDIAN LONG', function () {
			var buffer = Ti.createBuffer({
					value: 305419896,
					type: Ti.Codec.TYPE_LONG,
					byteOrder: Ti.Codec.BIG_ENDIAN
				}),
				i;
			should(buffer.byteOrder).eql(Ti.Codec.BIG_ENDIAN);
			should(buffer.length).eql(8);
			for (i = 0; i < 4; i++) { // first 4 bytes are 0
				should(buffer[i]).eql(0);
			}
			should(buffer[4]).eql(18);
			should(buffer[5]).eql(52);
			should(buffer[6]).eql(86);
			should(buffer[7]).eql(120);
		});

		it('LITTLE_ENDIAN LONG', function () {
			var buffer = Ti.createBuffer({
					value: 305419896,
					type: Ti.Codec.TYPE_LONG,
					byteOrder: Ti.Codec.LITTLE_ENDIAN
				}),
				i;
			should(buffer.byteOrder).eql(Ti.Codec.LITTLE_ENDIAN);
			should(buffer.length).eql(8);
			should(buffer[0]).eql(120);
			should(buffer[1]).eql(86);
			should(buffer[2]).eql(52);
			should(buffer[3]).eql(18);
			for (i = 4; i < 8; i++) { // last 4 bytes are 0
				should(buffer[i]).eql(0);
			}
		});

		it('LITTLE_ENDIAN INT', function () {
			var buffer = Ti.createBuffer({
				value: 305419896,
				type: Ti.Codec.TYPE_INT,
				byteOrder: Ti.Codec.LITTLE_ENDIAN
			});
			should(buffer.byteOrder).eql(Ti.Codec.LITTLE_ENDIAN);
			should(buffer[0]).eql(120);
			should(buffer[1]).eql(86);
			should(buffer[2]).eql(52);
			should(buffer[3]).eql(18);
		});

		it('BIG_ENDIAN INT', function () {
			var buffer = Ti.createBuffer({
				value: 305419896,
				type: Ti.Codec.TYPE_INT,
				byteOrder: Ti.Codec.BIG_ENDIAN
			});
			should(buffer.byteOrder).eql(Ti.Codec.BIG_ENDIAN);
			should(buffer[0]).eql(18);
			should(buffer[1]).eql(52);
			should(buffer[2]).eql(86);
			should(buffer[3]).eql(120);
		});
	});
});
