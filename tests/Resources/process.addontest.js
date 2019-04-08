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
const should = require('./utilities/assertions');

describe('process', () => {
	it('should be available as a global', () => {
		should(process).be.ok;
	});

	describe('#abort()', () => {
		it('is a function', () => {
			should(process.abort).be.a.Function;
		});
	});

	// TODO: binding(), chdir(), cpuUsage(), cwd(), disconnect(), dlopen(), emit(), emitWarning(), eventNames(), exit(), on(), umask(), uptime()

	describe('.arch', () => {
		it('is a string', () => {
			should(process.arch).be.a.String;
		});
	});

	describe('.argv', () => {
		it('is a string[]', () => {
			should(process.argv).be.an.Array;
		});
	});

	describe('.argv0', () => {
		it('is a string', () => {
			should(process.argv0).be.a.String;
		});
	});

	describe('.channel', () => {
		it('is undefined', () => {
			should.not.exist(process.channel);
		});
	});

	describe('.config', () => {
		it('is an object', () => {
			should(process.config).be.an.Object;
		});
	});

	describe('.connected', () => {
		it('is false', () => {
			should(process.connected).eql(false);
		});
	});

	describe('.debugPort', () => {
		it('is a Number', () => {
			should(process.debugPort).be.a.Number;
		});

		it('matches expected default', () => {
			if (Ti.Platform.osname === 'iphone' || Ti.Platform.osname === 'ipad') {
				should(process.debugPort).eql(27753); // standard port for iOS simulators pre-iOS 11.3
			} else {
				should(process.debugPort).eql(0);
			}
		});
	});

	describe('.env', () => {
		it('is an object', () => {
			should(process.env).be.an.Object;
		});
	});

	describe('.execArgv', () => {
		it('is an Array', () => {
			should(process.env).be.an.Array;
		});
	});

	describe('.execPath', () => {
		it('is a string', () => {
			should(process.execPath).be.a.String;
		});
	});

	describe('.exitCode', () => {
		it('is undefined by default', () => {
			should.not.exist(process.exitCode);
		});
	});

	describe('.noDeprecation', () => {
		it('is false', () => { // FIXME: Node sets to undefined by default!
			should(process.noDeprecation).eql(false);
		});
	});

	describe('.pid', () => {
		it('is 0', () => {
			should(process.pid).eql(0);
		});
	});

	describe('.platform', () => {
		// TODO: Should this give different values?
		it('echoes Ti.Platform.osname value', () => {
			should(process.platform).eql(Ti.Platform.osname);
		});
	});

	describe('.ppid', () => {
		it('is 0', () => {
			should(process.ppid).eql(0);
		});
	});

	describe('.stderr', () => {
		it('is an Object', () => {
			should(process.stderr).be.an.Object;
		});

		it('is not a TTY', () => {
			should(process.stderr.isTTY).eql(false);
		});

		it('is writable', () => {
			should(process.stderr.writable).eql(true);
		});

		describe('#write()', () => {
			it('is a Function', () => {
				should(process.stderr.write).be.a.Function;
			});
		});
	});

	describe('.stdout', () => {
		it('is an Object', () => {
			should(process.stdout).be.an.Object;
		});

		it('is not a TTY', () => {
			should(process.stdout.isTTY).eql(false);
		});

		it('is writable', () => {
			should(process.stdout.writable).eql(true);
		});

		describe('#write()', () => {
			it('is a Function', () => {
				should(process.stdout.write).be.a.Function;
			});
		});
	});

	describe('.title', () => {
		// TODO: Should this give different values?
		it('echoes Ti.App.name value', () => {
			should(process.title).eql(Ti.App.name);
		});
	});

	describe('.throwDeprecation', () => {
		it('is false', () => { // FIXME: Node sets to undefined by default!
			should(process.throwDeprecation).eql(false);
		});
	});

	describe('.traceDeprecation', () => {
		it('is false', () => { // FIXME: Node sets to undefined by default!
			should(process.traceDeprecation).eql(false);
		});
	});

	describe('.version', () => {
		it('echoes Ti.version', () => {
			should(process.version).eql(Ti.version);
		});
	});

	describe('.versions', () => {
		it('is an object', () => {
			should(process.versions).be.an.Object;
		});
	});
});
