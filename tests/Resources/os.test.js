/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
/* eslint-env node, titanium, mocha */
/* eslint no-unused-expressions: "off" */
/* eslint node/no-unsupported-features/node-builtins: "off" */
'use strict';
const should = require('./utilities/assertions'); // eslint-disable-line no-unused-vars
const utilities = require('./utilities/utilities');
let os;

describe('os', function () {
	it('should be required as core module', function () {
		os = require('os');
		os.should.be.an.Object();
	});

	it('.EOL', () => {
		os.should.have.a.property('EOL').which.is.a.String();
		// TODO: Validate \n or \r\n based on platform!
	});

	describe('#arch()', () => {
		it('is a function', function () {
			os.arch.should.be.a.Function();
		});

		it('returns a String', function () {
			os.arch().should.be.a.String();
		});

		// node values: 'arm', 'arm64', 'ia32', 'mips', 'mipsel', 'ppc', 'ppc64', 's390', 's390x', 'x32', and 'x64'.
		// iOS values: "arm64", "armv7", "x86_64", "i386", "Unknown"
		// Android values: "armeabi", "armeabi-v7a", "arm64-v8a", "x86", "x86_64", "mips", "mips64", "unknown"
		// Windows values: "x64", "ia64", "ARM", "x86", "unknown"
	});

	describe('.constants', () => {
		it('is an Object', () => {
			os.should.have.a.property('constants').which.is.an.Object();
		});

		it('has a signals property which is an Object', () => {
			os.constants.should.have.a.property('signals').which.is.an.Object();
		});

		it('has a errno property which is an Object', () => {
			os.constants.should.have.a.property('errno').which.is.an.Object();
		});

		it('has a priority property which is an Object', () => {
			os.constants.should.have.a.property('priority').which.is.an.Object();
		});
	});

	describe('#cpus()', () => {
		it('is a function', () => {
			os.cpus.should.be.a.Function();
		});

		it('returns array of objects whose length matches Ti.Platform.processorCount', () => {
			const cpus = os.cpus();
			cpus.should.be.an.Array();
			cpus.should.have.length(Ti.Platform.processorCount);
			// TODO: test that the elements are objects with properties: 'model', 'speed', 'times'
		});
	});

	describe('#endianness()', () => {
		it('is a function', () => {
			os.endianness.should.be.a.Function();
		});

		it('returns "LE" or "BE", value is consistent with Ti.Codec#getNativeByteOrder()', () => {
			const byteOrder = os.endianness();
			if (Ti.Codec.nativeByteOrder === Ti.Codec.BIG_ENDIAN) {
				byteOrder.should.eql('BE');
			} else {
				byteOrder.should.eql('LE');
			}
		});
	});

	describe('#freemem()', () => {
		it('is a function', () => {
			os.freemem.should.be.a.Function();
		});

		it('returns a positive Number', () => {
			os.freemem().should.be.above(0);
		});
	});

	describe('#getPriority()', () => {
		it('is a function', () => {
			os.getPriority.should.be.a.Function();
		});

		it('returns 0', () => {
			os.getPriority().should.eql(0);
		});
	});

	describe('#homedir()', () => {
		it('is a function', () => {
			os.homedir.should.be.a.Function();
		});

		it('returns Ti.Filesystem.applicationDataDirectory value', () => {
			os.homedir().should.eql(Ti.Filesystem.applicationDataDirectory);
		});
	});

	describe('#hostname()', () => {
		it('is a function', () => {
			os.hostname.should.be.a.Function();
		});

		it('returns Ti.Platform.address value', () => {
			// mey be undefined on iOS now!
			should(os.hostname()).eql(Ti.Platform.address);
		});
	});

	describe('#loadavg()', () => {
		it('is a function', () => {
			os.loadavg.should.be.a.Function();
		});

		it('returns [0, 0, 0]', () => {
			os.loadavg().should.eql([ 0, 0, 0 ]);
		});
	});

	describe('#networkInterfaces()', () => {
		it('is a function', () => {
			os.networkInterfaces.should.be.a.Function();
		});

		// TODO: Implement in some way?
		// it('returns ...', () => {
		// 	os.networkInterfaces().should.eql([ 0, 0, 0 ]);
		// });
	});

	describe('#platform()', () => {
		it('is a function', () => {
			os.platform.should.be.a.Function();
		});

		it('returns process.platform value', () => {
			os.platform().should.eql(process.platform);
		});
	});

	describe('#release()', () => {
		it('is a function', () => {
			os.release.should.be.a.Function();
		});

		it('returns Ti.Platform.version value', () => {
			os.release().should.eql(Ti.Platform.version);
		});
	});

	describe('#setPriority()', () => {
		it('is a function', () => {
			os.setPriority.should.be.a.Function();
		});

		it('doesn\'t blow up when called (but is no-op)', () => {
			(function () {
				os.setPriority(0, 12);
			}).should.not.throw();
		});
	});

	describe('#tmpdir()', () => {
		it('is a function', () => {
			os.tmpdir.should.be.a.Function();
		});

		it('returns Ti.Filesystem.tempDirectory value', () => {
			os.tmpdir().should.eql(Ti.Filesystem.tempDirectory);
		});
	});

	describe('#totalmem()', () => {
		it('is a function', () => {
			os.totalmem.should.be.a.Function();
		});

		it('returns a positive Number', () => {
			os.totalmem().should.be.above(0);
			// TODO: verify it's above freemem?
		});
	});

	describe('#type()', () => {
		it('is a function', () => {
			os.type.should.be.a.Function();
		});

		it('returns a String', () => {
			os.type().should.be.a.String(); // what values make sense here? We're coercing to node style values now, and I think that's probably wrong
			// 'Linux', 'Windows_NT', or 'Darwin'!
		});
	});

	describe('#uptime()', () => {
		it('is a function', () => {
			os.uptime.should.be.a.Function();
		});

		it('returns a positive Number', () => {
			// FIXME: Windows returns 0
			if (utilities.isWindows()) {
				os.uptime().should.eql(0);
			} else {
				os.uptime().should.be.above(0);
			}
		});
	});

	describe('#userInfo()', () => {
		it('is a function', () => {
			os.userInfo.should.be.a.Function();
		});

		it('returns an Object', () => {
			const userInfo = os.userInfo();
			userInfo.should.be.an.Object();
			userInfo.should.have.a.property('uid').which.eql(-1);
			userInfo.should.have.a.property('gid').which.eql(-1);
			userInfo.should.have.a.property('username').which.is.a.String(); // "iPhone 7 Plus" on ios Simulator, "android-build" on android emulator
			userInfo.should.have.a.property('homedir').which.eql(os.homedir());
			userInfo.should.have.a.property('shell').which.eql(null);
		});
	});
});
