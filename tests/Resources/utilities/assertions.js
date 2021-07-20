'use strict';
/* globals OS_ANDROID,OS_IOS */
const should = require('should');
const utilities = require('./utilities');
const isIOSDevice = OS_IOS && !Ti.Platform.model.includes('(Simulator)');

// use pngjs and pixelmatch!
const zlib = require('browserify-zlib');
global.binding.register('zlib', zlib);
const PNG = require('pngjs').PNG;
const cgbiToPng = isIOSDevice ? require('cgbi-to-png') : { revert: buf => buf };
const pixelmatch = require('pixelmatch');

// Copied from newer should.js
// Verifies the descriptor for an own property on a target
should.Assertion.add('ownPropertyWithDescriptor', function (name, desc) {
	this.params = { actual: this.obj, operator: 'to have own property `' + name + '` with descriptor ' + JSON.stringify(desc) };

	// descriptors do have default values! If not specified, they're treated as false
	// https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/getOwnPropertyDescriptor
	const keys = [ 'writable', 'configurable', 'enumerable' ];
	const descriptor = Object.getOwnPropertyDescriptor(Object(this.obj), name);

	for (let key in keys) {
		const expected = (desc[key] || false);
		const actual = (descriptor[key] || false);
		if (expected !== actual) {
			should.fail(`Expected ${name}.${key} = ${expected}`);
		}
	}
}, false);

// Finds a property up the prototype chain with a given descriptor
should.Assertion.add('propertyWithDescriptor', function (propName, desc) {
	var target = this.obj;
	this.params = { actual: this.obj, operator: 'to have property `' + propName + '` with descriptor ' + JSON.stringify(desc) };
	if (this.obj.apiName) {
		this.params.obj = this.obj.apiName;
	}

	// first let's see if we can find the property up the prototype chain...
	// need to call hasOwnProperty in a funky way due to https://jira.appcelerator.org/browse/TIMOB-23504
	while (!Object.prototype.hasOwnProperty.call(target, propName)) {
		target = Object.getPrototypeOf(target); // go up the prototype chain
		if (!target) {
			should.fail(`Unable to find property ${propName} up the prototype chain!`);
			break;
		}
	}

	// Now verify the property descriptor for it
	should(target).have.ownPropertyWithDescriptor(propName, desc);
}, false);

/**
 * Use this to test for read-only, non-configurable properties on an Object. We
 * will look up the prototype chain to find the owner of the property.
 *
 * @param {String} propName     Name of the property to test for.
 */
should.Assertion.add('readOnlyProperty', function (propName) {
	this.params = { operator: `to have a read-only property with name: ${propName}` };
	if (this.obj.apiName) {
		this.params.obj = this.obj.apiName;
	}

	// Now verify the property descriptor for it
	const props = { writable: false };
	// FIXME read-only properties should also be non-configurable on iOS and Windows (JSC)!
	if (!utilities.isIOS() && !utilities.isWindows()) {
		props.configurable = false;
	}
	this.have.propertyWithDescriptor(propName, props);

	// lastly swap over to the property itself (on our original object)
	this.have.property(propName);
}, false);

// Is this needed? Why not just test for 'property'?
should.Assertion.add('readWriteProperty', function (propName) {
	this.params = { operator: `to have a read-write property with name: ${propName}` };
	if (this.obj.apiName) {
		this.params.obj = this.obj.apiName;
	}

	// Now verify the property descriptor for it
	const props = {
		writable: true,
		enumerable: true,
		configurable: true,
	};
	this.have.propertyWithDescriptor(propName, props);

	// lastly swap over to the property itself (on our original object)
	this.have.property(propName);
}, false);

// TODO Do we need to distinguish between constant and readOnlyproperty?
should.Assertion.alias('readOnlyProperty', 'constant');

should.Assertion.add('enumeration', function (type, names) {
	this.params = { operator: `to have a set of enumerated constants with names: ${names}` };
	if (this.obj.apiName) {
		this.params.obj = this.obj.apiName;
	}

	for (let i = 0; i < names.length; i++) {
		should(this.obj).have.constant(names[i]).which.is.a[type](); // eslint-disable-line no-unused-expressions
	}
}, false);

should.Assertion.add('getter', function (propName) {
	const upper = propName.slice(0, 1).toUpperCase();
	const getterName = `get${upper}${propName.slice(1)}`;
	this.params = { operator: `to have a getter method named: ${getterName}` };
	if (this.obj.apiName) {
		this.params.obj = this.obj.apiName;
	}
	should(this.obj).have.a.property(getterName).which.is.a.Function();
}, false);

should.Assertion.add('setter', function (propName) {
	const upper = propName.slice(0, 1).toUpperCase();
	const setterName = `set${upper}${propName.slice(1)}`;
	this.params = { operator: `to have a setter method named: ${setterName}` };
	if (this.obj.apiName) {
		this.params.obj = this.obj.apiName;
	}
	should(this.obj).have.a.property(setterName).which.is.a.Function();
}, false);

should.Assertion.add('accessors', function (propName) {
	should(this.obj).have.a.getter(propName);
	should(this.obj).have.a.setter(propName);
}, false);

/**
 * @param {Ti.Blob} blob binary data to write
 * @param {string} imageFilePath relative file path to save image under
 * @returns {Ti.Filesystem.File}
 */
function saveImage(blob, imageFilePath) {
	const file = Ti.Filesystem.getFile(Ti.Filesystem.applicationDataDirectory, imageFilePath);
	if (!file.parent.exists()) {
		file.parent.createDirectory();
	}
	file.write(blob);
	return file;
}

class ImageMatchDetails {
	constructor(inputFilename) {
		this.inputFilename = inputFilename;
	}

	/**
	 * Generates the list of input image files we may try to compare against, in order
	 * of most specific match to more generic match
	 * @returns {string[]}
	 */
	possibleInputs() {
		const density = Ti.Platform.displayCaps.logicalDensityFactor;
		const withoutSuffix = this.inputFilename.substr(0, this.inputFilename.length - 4);
		const result = [];
		if (density !== 1) {
			// First try device *and* density specific image
			if (OS_IOS) {
				result.push(`${withoutSuffix}@${density}x~${Ti.Platform.osname}.png`);
			}
			// Then try density specific image
			result.push(`${withoutSuffix}@${density}x.png`);
		}
		// Try device specific image
		result.push(`${withoutSuffix}~${Ti.Platform.osname}.png`);
		// Then try base image
		result.push(`${withoutSuffix}.png`);

		// FIXME: Android should basically prefix with res- folder name based on density (if we can)
		// see https://developer.android.com/training/multiscreen/screendensities
		// 1x, 1.5x, 2x, 3x, 4x all map, but 2.625x/2.75x/3.5x don't!
		// https://blog.prototypr.io/designing-for-multiple-screen-densities-on-android-5fba8afe7ead
		return result;
	}

	/**
	 * Generates the output filename/path for the generated image.
	 * @returns {string}
	 */
	outputFile() {
		const density = Ti.Platform.displayCaps.logicalDensityFactor;
		// FIXME: On android, don't do this suffix crap, use res- folder naming!
		// Append density
		let suffix = density === 1 ? '' : `@${density}x`;
		// Append ~iphone or ~ipad!
		if (OS_IOS) {
			suffix = `${suffix}~${Ti.Platform.osname}`;
		}
		const withoutSuffix = this.inputFilename.substr(0, this.inputFilename.length - 4);
		return `${withoutSuffix}${suffix}.png`;
	}
}

/**
 * @param {string|Ti.Blob} image path to image file on disk (relative), or an in-memory Ti.Blob instance (holding an image).
 * @param {object} [options] options for comparing images
 * @param {Number} [options.threshold=0.1] threshold for comparing images
 * @param {Number} [options.maxPixelMismatch=0] maximum number of pixels this match will tolerate
 */
should.Assertion.add('matchImage', function (image, options = { threshold: 0.1, maxPixelMismatch: 0 }) {
	options.maxPixelMismatch = options.maxPixelMismatch || 0;

	// Validate object is valid view.
	this.obj.should.have.property('toImage').which.is.a.Function();

	const actualBlob = this.obj.toImage();
	const isExpectedBlob = image.apiName === 'Ti.Blob';
	const platform = OS_ANDROID ? 'android' : 'ios';
	const now = Date.now();
	const density = Ti.Platform.displayCaps.logicalDensityFactor;
	const suffix = density === 1 ? '' : `@${density}x`;
	let outputFilePath = image;
	let expectedBlob = null;

	if (isExpectedBlob) {
		this.params = {
			obj: this.obj.apiName,
			operator: 'to match Ti.Blob'
		};
		expectedBlob = image;
		outputFilePath = `snapshots/${now}_${expectedBlob.width}x${expectedBlob.height}${suffix}.png`;
	} else {
		const details = new ImageMatchDetails(image);
		outputFilePath = details.outputFile();
		this.params = {
			obj: this.obj.apiName,
			operator: `to match image ('${outputFilePath}')`
		};

		// Determine if any of the possible input images to match against exist
		const possibleInputs = details.possibleInputs();
		let snapshot;
		let inputFilename;
		for (const filepath of possibleInputs) {
			// Does it exist?
			const possibleFile = Ti.Filesystem.getFile(Ti.Filesystem.resourcesDirectory, filepath);
			if (possibleFile.exists()) {
				snapshot = possibleFile;
				inputFilename = filepath;
				break; // found it!
			}
		}
		if (!snapshot) { // none of the input images exist!
			// Save current view as snapshot for platform/density combo
			const file = saveImage(actualBlob, outputFilePath);
			console.log(`!IMAGE: {"path":"${file.nativePath}","platform":"${platform}","relativePath":"${outputFilePath}"}`);
			should.fail(`No snapshot image to compare for platform '${platform}' ('${possibleInputs}')`);
		}

		// Load expected snapshot blob.
		expectedBlob = snapshot.read();
		this.params = {
			obj: this.obj.apiName,
			operator: `to match image ('${inputFilename}')`
		};
	}

	// Validate size of blobs.
	try {
		should(actualBlob).have.property('width').equal(expectedBlob.width);
		should(actualBlob).have.property('height').equal(expectedBlob.height);
		should(actualBlob).have.property('size').equal(expectedBlob.size);
	} catch (e) {
		// Invalid size, save current view for investigation.
		const actualOut = saveImage(actualBlob, outputFilePath);
		console.log(`!IMAGE: {"path":"${actualOut.nativePath}","platform":"${platform}","relativePath":"${outputFilePath}"}`);

		// Save expected blob for investigation.
		const expectedPath = outputFilePath.slice(0, -4) + '_expected.png';
		const expectedOut = saveImage(expectedBlob, expectedPath);
		console.log(`!IMG_DIFF: {"path":"${expectedOut.nativePath}","platform":"${platform}","relativePath":"${expectedPath}"}`);

		throw e;
	}

	// Create a Buffer around the contents of each snapshot.
	const expectedBuffer = Buffer.from(expectedBlob.toArrayBuffer());
	const expectedImg = PNG.sync.read(cgbiToPng.revert(expectedBuffer));

	const actualBuffer = Buffer.from(actualBlob.toArrayBuffer());
	const actualImg = PNG.sync.read(cgbiToPng.revert(actualBuffer));

	const { width, height } = actualImg;
	const diffImg = new PNG({ width, height });
	const diff = pixelmatch(actualImg.data, expectedImg.data, diffImg.data, width, height, { threshold: options.threshold });

	try {
		should(diff).be.belowOrEqual(options.maxPixelMismatch, `mismatched pixels. allowed: ${options.maxPixelMismatch}, actual: ${diff}`);
	} catch (err) {
		// Snapshots did not match, save current view.
		const actualOut = saveImage(actualBlob, outputFilePath);
		console.log(`!IMAGE: {"path":"${actualOut.nativePath}","platform":"${platform}","relativePath":"${outputFilePath}"}`);

		// Save expected blob for investigation.
		if (isExpectedBlob) {
			const expectedPath = `snapshots/${now}_${expectedBlob.width}x${expectedBlob.height}${suffix}_expected.png`;
			const expectedOut = saveImage(expectedBlob, expectedPath);
			console.log(`!IMG_DIFF: {"path":"${expectedOut.nativePath}","platform":"${platform}","relativePath":"${expectedPath}"}`);
		} else {
			const expectedPath = outputFilePath.slice(0, -4) + '_expected.png';
			const expectedOut = saveImage(expectedBlob, expectedPath);
			console.log(`!IMG_DIFF: {"path":"${expectedOut.nativePath}","platform":"${platform}","relativePath":"${expectedPath}"}`);
		}

		const diffBuffer = PNG.sync.write(diffImg);
		const diffPath = outputFilePath.slice(0, -4) + '_diff.png';

		// Save difference image for investigation.
		const diffOut = saveImage(diffBuffer.toTiBuffer().toBlob(), diffPath);
		console.log(`!IMG_DIFF: {"path":"${diffOut.nativePath}","platform":"${platform}","relativePath":"${diffPath}","blob":${isExpectedBlob}}`);

		throw err;
	}
}, false);

// TODO Add an assertion for "exclusive" group of constants: A set of constants whose values must be unique (basically an enum), i.e. Ti.UI.FILL vs SIZE vs UNKNOWN
// TODO Use more custom assertions for things like color properties?
module.exports = should;

const filter = require('./mocha-filter');
// Use custom mocha filters for platform-specific tests
const filters = {
	android: () => utilities.isAndroid(),
	ios: () => utilities.isIOS(),
	ipad: () => utilities.isIPad(),
	iphone: () => utilities.isIPhone(),
	mac: () => utilities.isMacOS(),
	windows: () => utilities.isWindows(),
	// To mark APIs meant to be cross-platform but missing from a given platform
	androidMissing: () => (utilities.isAndroid() ? 'skip' : true),
	iosMissing: () => (utilities.isIOS() ? 'skip' : true),
	macMissing: () => (utilities.isMacOS() ? 'skip' : true),
	windowsMissing: () => (utilities.isWindows() ? 'skip' : true),
	androidARM64Broken: () => (utilities.isAndroidARM64Emulator() ? 'skip' : true),
	androidIosAndWindowsPhoneBroken: function () {
		if (utilities.isAndroid() || utilities.isIOS() || utilities.isWindowsPhone()) {
			return 'skip';
		}
		return true;
	},
	androidIosAndWindowsDesktopBroken: function () {
		if (utilities.isAndroid() || utilities.isIOS() || utilities.isWindowsDesktop()) {
			return 'skip';
		}
		return true;
	},
	// to mark when there's a bug in both iOS and Android impl
	androidAndIosBroken: function () {
		if (utilities.isAndroid() || utilities.isIOS()) {
			return 'skip';
		}
		return true;
	},
	androidAndMacBroken: function () {
		if (utilities.isAndroid() || utilities.isMacOS()) {
			return 'skip';
		}
		return true;
	},
	// to mark when there's a bug in both Android and Windows Desktop impl
	androidAndWindowsDesktopBroken: function () {
		if (utilities.isAndroid() || utilities.isWindowsDesktop()) {
			return 'skip';
		}
		return true;
	},
	// to mark when there's a bug in both Android and Windows Phone impl
	androidAndWindowsPhoneBroken: function () {
		if (utilities.isAndroid() || utilities.isWindowsPhone()) {
			return 'skip';
		}
		return true;
	},
	// to mark when there's a bug in both Android and Windows impl
	androidAndWindowsBroken: function () {
		if (utilities.isAndroid() || utilities.isWindows()) {
			return 'skip';
		}
		return true;
	},
	// to mark when there's a bug in both iOS and Windows impl
	iosAndWindowsBroken: function () {
		if (utilities.isWindows() || utilities.isIOS()) {
			return 'skip';
		}
		return true;
	},
	iosAndWindowsPhoneBroken: function () {
		if (utilities.isIOS() || utilities.isWindowsPhone()) {
			return 'skip';
		}
		return true;
	},
	iosAndWindowsDesktopBroken: function () {
		if (utilities.isWindowsDesktop() || utilities.isIOS()) {
			return 'skip';
		}
		return true;
	},
	macAndWindowsBroken: function () {
		if (utilities.isWindows() || utilities.isMacOS()) {
			return 'skip';
		}
		return true;
	},
	macAndWindowsDesktopBroken: function () {
		if (utilities.isWindowsDesktop() || utilities.isMacOS()) {
			return 'skip';
		}
		return true;
	},
	// mark bugs specific to Windows 8.1 Desktop/Store
	windowsDesktop81Broken: function () {
		if (utilities.isWindows8_1() || utilities.isWindowsDesktop()) {
			return 'skip';
		}
		return true;
	},
	// mark bugs specific to Windows 8.1 Phone
	windowsPhone81Broken: function () {
		if (utilities.isWindows8_1() || utilities.isWindowsPhone()) {
			return 'skip';
		}
		return true;
	},
	// mark bugs specific to Windows Emulator
	windowsEmulatorBroken: function () {
		if (utilities.isWindowsEmulator()) {
			return 'skip';
		}
		return true;
	},
	// mark bugs specific to Windows Store
	windowsDesktopBroken: function () {
		if (utilities.isWindowsDesktop()) {
			return 'skip';
		}
		return true;
	},
	// mark bugs specific to Windows Phone
	windowsPhoneBroken: function () {
		if (utilities.isWindowsPhone()) {
			return 'skip';
		}
		return true;
	},
	// mark bugs specific to Windows 8.1
	windows81Broken: function () {
		if (utilities.isWindows8_1()) {
			return 'skip';
		}
		return true;
	},
	allBroken: () => 'skip'
};

// Alias broken tests on a given platform to "missing" filter for that platform.
// This is just handy to try and label where we have gaps in our APIs versus where we have bugs in our impl for a given platform
filters.androidBroken = filters.androidMissing;
filters.iosBroken = filters.iosMissing;
filters.macBroken = filters.macMissing;
filters.windowsBroken = filters.windowsMissing;
filters.androidAndWindowsMissing = filters.androidAndWindowsBroken;
filters.androidBrokenAndIosMissing = filters.androidAndIosBroken;
filters.androidMissingAndIosBroken = filters.androidAndIosBroken;
filters.androidMissingAndWindowsBroken = filters.androidAndWindowsMissing;
filters.androidMissingAndWindowsDesktopBroken = filters.androidAndWindowsDesktopBroken;
filters.iosMissingAndWindowsDesktopBroken = filters.iosAndWindowsDesktopBroken;
filters.macBrokenAndWindowsMissing = filters.macAndWindowsBroken;
filters.macMissingAndWindowsBroken = filters.macAndWindowsBroken;
filters.macAndWindowsMissing = filters.macAndWindowsBroken;
// Add our custom filters
filter.addFilters(filters);
