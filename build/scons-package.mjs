#!/usr/bin/env node
import program from 'commander';
import Builder from './lib/builder.mjs';
import fs from 'fs-extra';

const version = fs.readJsonSync(new URL('../package.json', import.meta.url)).version;

program
	.option('-a, --all', 'Build a zipfile for every OS')
	.option('-v, --sdk-version [version]', 'Override the SDK version we report', process.env.PRODUCT_VERSION || version)
	.option('-t, --version-tag [tag]', 'Override the SDK version tag we report')
	.option('-z, --skip-zip', 'Do not zip up the package')
	.option('--no-docs', 'Do not produce docs')
	.parse(process.argv);

async function main(program) {
	const builder = new Builder(program.opts(), program.args);
	await builder.generateDocs();
	return builder.package();
}

main(program)
	.then(() => process.exit(0))
	.catch(err => {
		console.error(err);
		process.exit(1);
	});
