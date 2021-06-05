/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
/* eslint-env mocha */
/* eslint no-unused-expressions: "off" */
'use strict';
const should = require('./utilities/assertions');
const utilities = require('./utilities/utilities');

describe('Titanium.UI', function () {

	it.ios('.AUTOFILL_TYPE_NEW_PASSWORD', function () {
		should(Ti.UI).have.a.constant('AUTOFILL_TYPE_NEW_PASSWORD').which.is.a.String();
	});

	it.ios('.AUTOFILL_TYPE_ONE_TIME_CODE', function () {
		should(Ti.UI).have.a.constant('AUTOFILL_TYPE_ONE_TIME_CODE').which.is.a.String();
	});

	it.androidMissing('TEXT_STYLE_* constants', function () {
		should(Ti.UI.TEXT_STYLE_HEADLINE).be.a.String();
		should(Ti.UI.TEXT_STYLE_SUBHEADLINE).be.a.String();
		should(Ti.UI.TEXT_STYLE_BODY).be.a.String();
		should(Ti.UI.TEXT_STYLE_FOOTNOTE).be.a.String();
		should(Ti.UI.TEXT_STYLE_CAPTION1).be.a.String();
		should(Ti.UI.TEXT_STYLE_CAPTION2).be.a.String();
		should(Ti.UI.TEXT_STYLE_CALLOUT).be.a.String();
		should(Ti.UI.TEXT_STYLE_TITLE1).be.a.String();
		should(Ti.UI.TEXT_STYLE_TITLE2).be.a.String();
		should(Ti.UI.TEXT_STYLE_TITLE3).be.a.String();
		should(Ti.UI.TEXT_STYLE_LARGE_TITLE).be.a.String();
	});

	it.ios('.BLEND_MODE_* constants', () => {
		// Used in MaskedImage.mode
		// While these are defined for both platforms, only a subset are actually supported on Android
		// when passed to MaskedImage.mode
		should(Ti.UI.BLEND_MODE_CLEAR).be.a.Number();
		should(Ti.UI.BLEND_MODE_COLOR).be.a.Number();
		should(Ti.UI.BLEND_MODE_COLOR_BURN).be.a.Number();
		should(Ti.UI.BLEND_MODE_COLOR_DODGE).be.a.Number();
		should(Ti.UI.BLEND_MODE_COPY).be.a.Number();
		should(Ti.UI.BLEND_MODE_DARKEN).be.a.Number();
		should(Ti.UI.BLEND_MODE_DESTINATION_ATOP).be.a.Number();
		should(Ti.UI.BLEND_MODE_DESTINATION_IN).be.a.Number();
		should(Ti.UI.BLEND_MODE_DESTINATION_OUT).be.a.Number();
		should(Ti.UI.BLEND_MODE_DESTINATION_OVER).be.a.Number();
		should(Ti.UI.BLEND_MODE_DIFFERENCE).be.a.Number();
		should(Ti.UI.BLEND_MODE_EXCLUSION).be.a.Number();
		should(Ti.UI.BLEND_MODE_HARD_LIGHT).be.a.Number();
		should(Ti.UI.BLEND_MODE_HUE).be.a.Number();
		should(Ti.UI.BLEND_MODE_LIGHTEN).be.a.Number();
		should(Ti.UI.BLEND_MODE_LUMINOSITY).be.a.Number();
		should(Ti.UI.BLEND_MODE_MULTIPLY).be.a.Number();
		should(Ti.UI.BLEND_MODE_NORMAL).be.a.Number();
		should(Ti.UI.BLEND_MODE_OVERLAY).be.a.Number();
		should(Ti.UI.BLEND_MODE_PLUS_DARKER).be.a.Number();
		should(Ti.UI.BLEND_MODE_PLUS_LIGHTER).be.a.Number();
		should(Ti.UI.BLEND_MODE_SATURATION).be.a.Number();
		should(Ti.UI.BLEND_MODE_SCREEN).be.a.Number();
		should(Ti.UI.BLEND_MODE_SOFT_LIGHT).be.a.Number();
		should(Ti.UI.BLEND_MODE_SOURCE_ATOP).be.a.Number();
		should(Ti.UI.BLEND_MODE_SOURCE_IN).be.a.Number();
		should(Ti.UI.BLEND_MODE_SOURCE_OUT).be.a.Number();
		should(Ti.UI.BLEND_MODE_XOR).be.a.Number();
	});

	// TODO Use the JSCA file to generate tests!
	var ALL = [ 'iphone', 'ipad', 'android', 'mobileweb', 'windowsstore', 'windowsphone' ],
		NOT_ANDROID = [ 'iphone', 'ipad', 'mobileweb', 'windowsstore', 'windowsphone' ],
		NOT_MOBILEWEB = [ 'iphone', 'ipad', 'android', 'windowsstore', 'windowsphone' ],
		IOS_ONLY = [ 'iphone', 'ipad' ],
		IOS_AND_WINDOWS = [ 'iphone', 'ipad', 'windowsstore', 'windowsphone' ],
		ANDROID_AND_WINDOWS = [ 'android', 'windowsstore', 'windowsphone' ],
		constants = {
			ANIMATION_CURVE_EASE_IN: { type: 'Number', platforms: NOT_ANDROID },
			ANIMATION_CURVE_EASE_IN_OUT: { type: 'Number', platforms: NOT_ANDROID },
			ANIMATION_CURVE_EASE_OUT: { type: 'Number', platforms: NOT_ANDROID },
			ANIMATION_CURVE_LINEAR: { type: 'Number', platforms: NOT_ANDROID },

			ATTRIBUTE_BACKGROUND_COLOR: { type: 'Number', platforms: NOT_MOBILEWEB },
			ATTRIBUTE_BASELINE_OFFSET: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_EXPANSION: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_FONT: { type: 'Number', platforms: NOT_MOBILEWEB },
			ATTRIBUTE_FOREGROUND_COLOR: { type: 'Number', platforms: NOT_MOBILEWEB },
			ATTRIBUTE_KERN: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_LETTERPRESS_STYLE: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_LIGATURE: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_LINE_BREAK: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_LINE_BREAK_BY_CHAR_WRAPPING: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_LINE_BREAK_BY_CLIPPING: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_LINE_BREAK_BY_TRUNCATING_HEAD: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_LINE_BREAK_BY_TRUNCATING_MIDDLE: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_LINE_BREAK_BY_TRUNCATING_TAIL: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_LINE_BREAK_BY_WORD_WRAPPING: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_LINK: { type: 'Number', platforms: NOT_MOBILEWEB },
			ATTRIBUTE_OBLIQUENESS: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_SHADOW: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_STRIKETHROUGH_COLOR: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_STRIKETHROUGH_STYLE: { type: 'Number', platforms: NOT_MOBILEWEB },
			ATTRIBUTE_STROKE_COLOR: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_STROKE_WIDTH: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_TEXT_EFFECT: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_UNDERLINES_STYLE: { type: 'Number', platforms: NOT_MOBILEWEB },
			ATTRIBUTE_UNDERLINE_BY_WORD: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_UNDERLINE_COLOR: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_UNDERLINE_PATTERN_DASH: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_UNDERLINE_PATTERN_DASH_DOT: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_UNDERLINE_PATTERN_DASH_DOT_DOT: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_UNDERLINE_PATTERN_DOT: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_UNDERLINE_PATTERN_SOLID: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_UNDERLINE_STYLE_DOUBLE: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_UNDERLINE_STYLE_NONE: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_UNDERLINE_STYLE_SINGLE: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_UNDERLINE_STYLE_THICK: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_WRITING_DIRECTION: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_WRITING_DIRECTION_EMBEDDING: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_WRITING_DIRECTION_LEFT_TO_RIGHT: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_WRITING_DIRECTION_NATURAL: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_WRITING_DIRECTION_OVERRIDE: { type: 'Number', platforms: IOS_AND_WINDOWS },
			ATTRIBUTE_WRITING_DIRECTION_RIGHT_TO_LEFT: { type: 'Number', platforms: IOS_AND_WINDOWS },

			AUTOLINK_ALL: { type: 'Number', platforms: NOT_MOBILEWEB },
			AUTOLINK_CALENDAR: { type: 'Number', platforms: IOS_AND_WINDOWS },
			AUTOLINK_EMAIL_ADDRESSES: { type: 'Number', platforms: NOT_MOBILEWEB },
			AUTOLINK_MAP_ADDRESSES: { type: 'Number', platforms: NOT_MOBILEWEB },
			AUTOLINK_NONE: { type: 'Number', platforms: NOT_MOBILEWEB },
			AUTOLINK_PHONE_NUMBERS: { type: 'Number', platforms: NOT_MOBILEWEB },
			AUTOLINK_URLS: { type: 'Number', platforms: NOT_MOBILEWEB },

			EXTEND_EDGE_ALL: { type: 'Number', platforms: IOS_AND_WINDOWS },
			EXTEND_EDGE_BOTTOM: { type: 'Number', platforms: IOS_AND_WINDOWS },
			EXTEND_EDGE_LEFT: { type: 'Number', platforms: IOS_AND_WINDOWS },
			EXTEND_EDGE_NONE: { type: 'Number', platforms: IOS_AND_WINDOWS },
			EXTEND_EDGE_RIGHT: { type: 'Number', platforms: IOS_AND_WINDOWS },
			EXTEND_EDGE_TOP: { type: 'Number', platforms: IOS_AND_WINDOWS },

			FACE_DOWN: { type: 'Number', platforms: ALL },
			FACE_UP: { type: 'Number', platforms: ALL },
			LANDSCAPE_LEFT: { type: 'Number', platforms: ALL },
			LANDSCAPE_RIGHT: { type: 'Number', platforms: NOT_MOBILEWEB },
			PORTRAIT: { type: 'Number', platforms: ALL },
			UNKNOWN: { type: 'Number', platforms: ALL },
			UPSIDE_PORTRAIT: { type: 'Number', platforms: NOT_MOBILEWEB },

			FILL: { type: 'String', platforms: ALL },
			INHERIT: { type: 'String', platforms: [ 'mobileweb' ] },
			SIZE: { type: 'String', platforms: ALL },

			INPUT_BORDERSTYLE_BEZEL: { type: 'Number', platforms: ALL },
			INPUT_BORDERSTYLE_LINE: { type: 'Number', platforms: ALL },
			INPUT_BORDERSTYLE_NONE: { type: 'Number', platforms: ALL },
			INPUT_BORDERSTYLE_ROUNDED: { type: 'Number', platforms: ALL },

			INPUT_BUTTONMODE_ALWAYS: { type: 'Number', platforms: NOT_MOBILEWEB },
			INPUT_BUTTONMODE_NEVER: { type: 'Number', platforms: NOT_MOBILEWEB },
			INPUT_BUTTONMODE_ONBLUR: { type: 'Number', platforms: IOS_AND_WINDOWS },
			INPUT_BUTTONMODE_ONFOCUS: { type: 'Number', platforms: NOT_MOBILEWEB },

			INPUT_TYPE_CLASS_NUMBER: { type: 'Number', platforms: [ 'android' ] },
			INPUT_TYPE_CLASS_TEXT: { type: 'Number', platforms: [ 'android' ] },

			KEYBOARD_APPEARANCE_DARK: { type: 'Number', platforms: IOS_AND_WINDOWS },
			KEYBOARD_APPEARANCE_DEFAULT: { type: 'Number', platforms: IOS_AND_WINDOWS },
			KEYBOARD_APPEARANCE_LIGHT: { type: 'Number', platforms: IOS_AND_WINDOWS },

			KEYBOARD_TYPE_ASCII: { type: 'Number', platforms: NOT_MOBILEWEB },
			KEYBOARD_TYPE_DECIMAL_PAD: { type: 'Number', platforms: NOT_MOBILEWEB },
			KEYBOARD_TYPE_DEFAULT: { type: 'Number', platforms: ALL },
			KEYBOARD_TYPE_EMAIL: { type: 'Number', platforms: ALL },
			KEYBOARD_TYPE_NAMEPHONE_PAD: { type: 'Number', platforms: NOT_MOBILEWEB },
			KEYBOARD_TYPE_NUMBERS_PUNCTUATION: { type: 'Number', platforms: NOT_MOBILEWEB },
			KEYBOARD_TYPE_NUMBER_PAD: { type: 'Number', platforms: ALL },
			KEYBOARD_TYPE_PHONE_PAD: { type: 'Number', platforms: ALL },
			KEYBOARD_TYPE_TWITTER: { type: 'Number', platforms: IOS_AND_WINDOWS },
			KEYBOARD_TYPE_URL: { type: 'Number', platforms: ALL },
			KEYBOARD_TYPE_WEBSEARCH: { type: 'Number', platforms: IOS_AND_WINDOWS },

			LIST_ACCESSORY_TYPE_CHECKMARK: { type: 'Number', platforms: NOT_MOBILEWEB },
			LIST_ACCESSORY_TYPE_DETAIL: { type: 'Number', platforms: NOT_MOBILEWEB },
			LIST_ACCESSORY_TYPE_DISCLOSURE: { type: 'Number', platforms: NOT_MOBILEWEB },
			LIST_ACCESSORY_TYPE_NONE: { type: 'Number', platforms: NOT_MOBILEWEB },

			LIST_ITEM_TEMPLATE_CONTACTS: { type: 'Number', platforms: IOS_AND_WINDOWS },
			LIST_ITEM_TEMPLATE_DEFAULT: { type: 'Number', platforms: NOT_MOBILEWEB },
			LIST_ITEM_TEMPLATE_SETTINGS: { type: 'Number', platforms: IOS_AND_WINDOWS },
			LIST_ITEM_TEMPLATE_SUBTITLE: { type: 'Number', platforms: IOS_AND_WINDOWS },

			NOTIFICATION_DURATION_LONG: { type: 'Number', platforms: ANDROID_AND_WINDOWS },
			NOTIFICATION_DURATION_SHORT: { type: 'Number', platforms: ANDROID_AND_WINDOWS },

			PICKER_TYPE_COUNT_DOWN_TIMER: { type: 'Number', platforms: IOS_AND_WINDOWS },
			PICKER_TYPE_DATE: { type: 'Number', platforms: ALL },
			PICKER_TYPE_DATE_AND_TIME: { type: 'Number', platforms: ALL },
			PICKER_TYPE_PLAIN: { type: 'Number', platforms: ALL },
			PICKER_TYPE_TIME: { type: 'Number', platforms: ALL },

			RETURNKEY_CONTINUE: { type: 'Number', platforms: IOS_AND_WINDOWS },
			RETURNKEY_DEFAULT: { type: 'Number', platforms: ALL },
			RETURNKEY_DONE: { type: 'Number', platforms: NOT_MOBILEWEB },
			RETURNKEY_EMERGENCY_CALL: { type: 'Number', platforms: NOT_MOBILEWEB },
			RETURNKEY_GO: { type: 'Number', platforms: ALL },
			RETURNKEY_GOOGLE: { type: 'Number', platforms: ALL },
			RETURNKEY_JOIN: { type: 'Number', platforms: NOT_MOBILEWEB },
			RETURNKEY_NEXT: { type: 'Number', platforms: NOT_MOBILEWEB },
			RETURNKEY_ROUTE: { type: 'Number', platforms: NOT_MOBILEWEB },
			RETURNKEY_SEARCH: { type: 'Number', platforms: ALL },
			RETURNKEY_SEND: { type: 'Number', platforms: NOT_MOBILEWEB },
			RETURNKEY_YAHOO: { type: 'Number', platforms: ALL },

			SELECTION_STYLE_NONE: { type: 'Number', platforms: NOT_MOBILEWEB },
			SELECTION_STYLE_DEFAULT: { type: 'Number', platforms: NOT_MOBILEWEB },

			TABLE_VIEW_SEPARATOR_STYLE_NONE: { type: 'Number', platforms: NOT_MOBILEWEB },
			TABLE_VIEW_SEPARATOR_STYLE_SINGLE_LINE: { type: 'Number', platforms: NOT_MOBILEWEB },

			TEXT_AUTOCAPITALIZATION_ALL: { type: 'Number', platforms: ALL },
			TEXT_AUTOCAPITALIZATION_NONE: { type: 'Number', platforms: ALL },
			TEXT_AUTOCAPITALIZATION_SENTENCES: { type: 'Number', platforms: ALL },
			TEXT_AUTOCAPITALIZATION_WORDS: { type: 'Number', platforms: NOT_MOBILEWEB },

			TEXT_ELLIPSIZE_TRUNCATE_END: { type: 'Number', platforms: NOT_MOBILEWEB },
			TEXT_ELLIPSIZE_TRUNCATE_MARQUEE: { type: 'Number', platforms: [ 'android' ] },
			TEXT_ELLIPSIZE_TRUNCATE_MIDDLE: { type: 'Number', platforms: NOT_MOBILEWEB },
			TEXT_ELLIPSIZE_TRUNCATE_START: { type: 'Number', platforms: NOT_MOBILEWEB },

			TEXT_STYLE_BODY: { type: 'Number', platforms: IOS_ONLY },
			TEXT_STYLE_CAPTION1: { type: 'Number', platforms: IOS_ONLY },
			TEXT_STYLE_CAPTION2: { type: 'Number', platforms: IOS_ONLY },
			TEXT_STYLE_FOOTNOTE: { type: 'Number', platforms: IOS_ONLY },
			TEXT_STYLE_HEADLINE: { type: 'Number', platforms: IOS_ONLY },
			TEXT_STYLE_SUBHEADLINE: { type: 'Number', platforms: IOS_ONLY },

			UNIT_CM: { type: 'String', platforms: ALL },
			UNIT_DIP: { type: 'String', platforms: ALL },
			UNIT_IN: { type: 'String', platforms: ALL },
			UNIT_MM: { type: 'String', platforms: ALL },
			UNIT_PX: { type: 'String', platforms: ALL },

			URL_ERROR_AUTHENTICATION: { type: 'Number', platforms: NOT_MOBILEWEB },
			URL_ERROR_BAD_URL: { type: 'Number', platforms: NOT_MOBILEWEB },
			URL_ERROR_CONNECT: { type: 'Number', platforms: NOT_MOBILEWEB },
			URL_ERROR_FILE: { type: 'Number', platforms: NOT_MOBILEWEB },
			URL_ERROR_FILE_NOT_FOUND: { type: 'Number', platforms: NOT_MOBILEWEB },
			URL_ERROR_HOST_LOOKUP: { type: 'Number', platforms: NOT_MOBILEWEB },
			URL_ERROR_REDIRECT_LOOP: { type: 'Number', platforms: NOT_MOBILEWEB },
			URL_ERROR_SSL_FAILED: { type: 'Number', platforms: NOT_MOBILEWEB },
			URL_ERROR_TIMEOUT: { type: 'Number', platforms: NOT_MOBILEWEB },
			URL_ERROR_UNKNOWN: { type: 'Number', platforms: NOT_MOBILEWEB },
			URL_ERROR_UNSUPPORTED_SCHEME: { type: 'Number', platforms: NOT_MOBILEWEB }
		},
		platform = Ti.Platform.osname,
		name,
		i,
		constantsVary;

	for (name in constants) {
		if (Object.prototype.hasOwnProperty.call(constants, name)) {
			// Don't test if the constant isn't for this platform!
			if (constants[name].platforms.indexOf(platform) === -1) {
				continue;
			}
			if (constants[name].type === 'Number') {
				it(name, function () { // eslint-disable-line no-loop-func
					should(Ti.UI).have.a.constant(name).which.is.a.Number();
				});
			} else if (constants[name].type === 'String') {
				// FIXME These special constants are failing on Android and iOS. They appear to be hard-coded numbers (and not unique!)
				([ 'FILL', 'SIZE', 'UNIT_CM', 'UNIT_DIP', 'UNIT_IN', 'UNIT_MM', 'UNIT_PX' ].indexOf(name) !== -1 ? it.skip : it)(name, function () { // eslint-disable-line no-loop-func
					should(Ti.UI).have.a.constant(name).which.is.a.String();
				});
			}
		}
	}

	// Constants that are Strings on Android and Numbers elsewhere
	constantsVary = [
		'TEXT_ALIGNMENT_CENTER', 'TEXT_ALIGNMENT_LEFT', 'TEXT_ALIGNMENT_RIGHT',
		'TEXT_VERTICAL_ALIGNMENT_BOTTOM', 'TEXT_VERTICAL_ALIGNMENT_CENTER', 'TEXT_VERTICAL_ALIGNMENT_TOP'
	];
	// Verify our constants that may be String or Number depending on platform.
	for (i = 0; i < constantsVary.length; i++) {
		// FIXME Get these working on iOS, Android, and Windows.
		it.allBroken(constantsVary[i], function () { // eslint-disable-line no-loop-func
			if (utilities.isAndroid()) {
				should(Ti.UI).have.a.constant(constantsVary[i]).which.is.a.String();
			} else {
				should(Ti.UI).have.a.constant(constantsVary[i]).which.is.a.Number();
			}
		});
	}
});
