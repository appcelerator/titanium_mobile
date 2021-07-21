#!/usr/bin/env node
import program from 'commander';
import Builder from './lib/builder.mjs';
import fs from 'fs-extra';

const version = fs.readJsonSync(new URL('../package.json', import.meta.url)).version;

program
	.option('-v, --sdk-version [version]', 'Override the SDK version we report', process.env.PRODUCT_VERSION || version)
	.option('-t, --version-tag [tag]', 'Override the SDK version tag we report')
	.option('-s, --android-sdk [path]', 'Explicitly set the path to the Android SDK used for building')
	.option('--no-docs', 'Do not produce docs')
	.option('--symlink', 'If possible, symlink the SDK folder to destination rather than copying')
	.option('-a, --all', 'Build a zipfile for every OS')
	.option('-z, --skip-zip', 'Do not zip up the package, leaving folder contents under dist (typically used for local development)')
	.parse(process.argv);

async function main(program) {
	const builder = new Builder(program.opts(), program.args);
	await builder.clean();
	await builder.build();
	await builder.generateDocs();
	await builder.package();
	await builder.install();
}

main(program)
	.then(() => process.exit(0))
	.catch(err => {
		console.error(err);
		process.exit(1);
	});
