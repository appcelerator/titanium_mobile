import { test, outputResults } from './test.mjs';
import path from 'path';
import fs from 'fs-extra';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.join(__dirname, '../../..');
const LOCAL_TESTS = path.join(ROOT_DIR, 'tests');

/**
 * Runs our unit testing script against the supplied platforms for the currently select SDK in `ti` cli.
 *
 * @param {string[]} platforms array of platform names for which we should run tests
 * @param {object} program object holding options/switches from CLI
 * @param {string} [program.target] 'emulator' || 'simulator' || 'device'
 * @param {string} [program.deviceId] Titanium device id target to run the tests on
 * @param {string} [program.deployType] 'development' || 'test'
 * @param {string} [program.deviceFamily] 'ipad' || 'iphone'
 * @returns {Promise<object>} returns an object whose keys are platform names
 */
export async function runTests(platforms, program) {
	const snapshotDir = path.join(LOCAL_TESTS, 'Resources');
	// wipe generated images and diffs from previous run
	await Promise.all([
		fs.emptyDir(path.join(snapshotDir, '..', 'generated')),
		fs.emptyDir(path.join(snapshotDir, '..', 'diffs'))
	]);
	return test(platforms, program.target, program.deviceId, program.deployType, program.deviceFamily, snapshotDir);
}

/**
 * Outputs the given test results to the console.
 * @param {object} results Dictionary of test results to be outputted.
 * @returns {Promise<void>}
 */
export async function outputMultipleResults(results) {
	const platforms = Object.keys(results);
	for (const p of platforms) {
		console.log();
		console.log('=====================================');
		console.log(p.toUpperCase());
		console.log('-------------------------------------');
		await outputResults(results[p].results);
	}
}
