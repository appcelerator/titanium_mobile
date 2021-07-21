/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-Present by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

'use strict';

import { handleBuild } from './test.mjs';
import { expect } from 'chai';
import fs from 'fs-extra';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

describe('test.handleBuild', function () {
	this.slow(750);

	it('works', async () => {
		// TODO: Can we interleave stderr and stdout on separate files or something?
		const stdout = fs.createReadStream(path.join(__dirname, 'out.txt'));
		const prc = {
			stdout,
			stderr: {
				on: () => {}
			},
			on: () => {},
			kill: () => {}
		};
		const results = await handleBuild(prc, 'emulator', __dirname, []);
		expect(results).to.be.a('object');
		expect(results.date).to.be.a('string'); // ISO date string
		expect(results.results).to.have.lengthOf(5134); // 5134 test results (count of '!TEST_END:')
		expect(results.results.filter(r => r.state === 'failed')).to.have.lengthOf(31); // 31 failed ('"state":"failed"')
	});
});
