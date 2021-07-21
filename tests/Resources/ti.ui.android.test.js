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

describe.android('Titanium.UI.Android', () => {
	describe('#getColorResource()', () => {
		it('is a function', () => should(Ti.UI.Android).have.a.property('getColorResource').which.is.a.Function());

		it('handles resource id as argument', () => {
			const result = Ti.UI.Android.getColorResource(Ti.Android.R.color.darker_gray);
			result.toHex().toLowerCase().should.eql('#ffaaaaaa');
		});

		it('handles color name as argument', () => {
			const result = Ti.UI.Android.getColorResource('darker_gray');
			result.toHex().toLowerCase().should.eql('#ffaaaaaa');
		});

		it('returns null for unknown color name as argument', () => {
			const result = Ti.UI.Android.getColorResource('made_up_color_resource_name');
			should.not.exist(result);
		});

		it('returns null for bad resource id', () => {
			const result = Ti.UI.Android.getColorResource(0);
			should.not.exist(result);
		});
	});

	describe('constants', () => {
		it('FLAG_LAYOUT_NO_LIMITS', () => {
			should(Ti.UI.Android).have.constant('FLAG_LAYOUT_NO_LIMITS').which.is.a.Number();
		});

		it('FLAG_TRANSLUCENT_NAVIGATION', () => {
			should(Ti.UI.Android).have.constant('FLAG_TRANSLUCENT_NAVIGATION').which.is.a.Number();
		});
		it('FLAG_TRANSLUCENT_STATUS', () => {
			should(Ti.UI.Android).have.constant('FLAG_TRANSLUCENT_STATUS').which.is.a.Number();
		});

		it('GRAVITY_AXIS_CLIP', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_AXIS_CLIP').which.is.a.Number();
		});
		it('GRAVITY_AXIS_PULL_AFTER', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_AXIS_PULL_AFTER').which.is.a.Number();
		});
		it('GRAVITY_AXIS_PULL_BEFORE', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_AXIS_PULL_BEFORE').which.is.a.Number();
		});
		it('GRAVITY_AXIS_SPECIFIED', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_AXIS_SPECIFIED').which.is.a.Number();
		});
		it('GRAVITY_AXIS_X_SHIFT', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_AXIS_X_SHIFT').which.is.a.Number();
		});
		it('GRAVITY_AXIS_Y_SHIFT', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_AXIS_Y_SHIFT').which.is.a.Number();
		});
		it('GRAVITY_BOTTOM', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_BOTTOM').which.is.a.Number();
		});
		it('GRAVITY_CENTER', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_CENTER').which.is.a.Number();
		});
		it('GRAVITY_CENTER_HORIZONTAL', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_CENTER_HORIZONTAL').which.is.a.Number();
		});
		it('GRAVITY_CENTER_VERTICAL', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_CENTER_VERTICAL').which.is.a.Number();
		});
		it('GRAVITY_CLIP_HORIZONTAL', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_CLIP_HORIZONTAL').which.is.a.Number();
		});
		it('GRAVITY_CLIP_VERTICAL', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_CLIP_VERTICAL').which.is.a.Number();
		});
		it('GRAVITY_DISPLAY_CLIP_HORIZONTAL', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_DISPLAY_CLIP_HORIZONTAL').which.is.a.Number();
		});
		it('GRAVITY_DISPLAY_CLIP_VERTICAL', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_DISPLAY_CLIP_VERTICAL').which.is.a.Number();
		});
		it('GRAVITY_END', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_END').which.is.a.Number();
		});
		it('GRAVITY_FILL', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_FILL').which.is.a.Number();
		});
		it('GRAVITY_FILL_HORIZONTAL', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_FILL_HORIZONTAL').which.is.a.Number();
		});
		it('GRAVITY_FILL_VERTICAL', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_FILL_VERTICAL').which.is.a.Number();
		});
		it('GRAVITY_HORIZONTAL_GRAVITY_MASK', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_HORIZONTAL_GRAVITY_MASK').which.is.a.Number();
		});
		it('GRAVITY_LEFT', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_LEFT').which.is.a.Number();
		});
		it('GRAVITY_NO_GRAVITY', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_NO_GRAVITY').which.is.a.Number();
		});
		it('GRAVITY_RELATIVE_HORIZONTAL_GRAVITY_MASK', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_RELATIVE_HORIZONTAL_GRAVITY_MASK').which.is.a.Number();
		});
		it('GRAVITY_RELATIVE_LAYOUT_DIRECTION', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_RELATIVE_LAYOUT_DIRECTION').which.is.a.Number();
		});
		it('GRAVITY_RIGHT', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_RIGHT').which.is.a.Number();
		});
		it('GRAVITY_START', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_START').which.is.a.Number();
		});
		it('GRAVITY_TOP', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_TOP').which.is.a.Number();
		});
		it('GRAVITY_VERTICAL_GRAVITY_MASK', () => {
			should(Ti.UI.Android).have.constant('GRAVITY_VERTICAL_GRAVITY_MASK').which.is.a.Number();
		});

		it('OVER_SCROLL_ALWAYS', () => {
			should(Ti.UI.Android).have.constant('OVER_SCROLL_ALWAYS').which.is.a.Number();
		});
		it('OVER_SCROLL_IF_CONTENT_SCROLLS', () => {
			should(Ti.UI.Android).have.constant('OVER_SCROLL_IF_CONTENT_SCROLLS').which.is.a.Number();
		});
		it('OVER_SCROLL_NEVER', () => {
			should(Ti.UI.Android).have.constant('OVER_SCROLL_NEVER').which.is.a.Number();
		});

		it('PIXEL_FORMAT_A_8', () => {
			should(Ti.UI.Android).have.constant('PIXEL_FORMAT_A_8').which.is.a.Number();
		});
		it('PIXEL_FORMAT_L_8', () => {
			should(Ti.UI.Android).have.constant('PIXEL_FORMAT_L_8').which.is.a.Number();
		});
		it('PIXEL_FORMAT_LA_88', () => {
			should(Ti.UI.Android).have.constant('PIXEL_FORMAT_LA_88').which.is.a.Number();
		});
		it('PIXEL_FORMAT_OPAQUE', () => {
			should(Ti.UI.Android).have.constant('PIXEL_FORMAT_OPAQUE').which.is.a.Number();
		});
		it('PIXEL_FORMAT_RGB_332', () => {
			should(Ti.UI.Android).have.constant('PIXEL_FORMAT_RGB_332').which.is.a.Number();
		});
		it('PIXEL_FORMAT_RGB_565', () => {
			should(Ti.UI.Android).have.constant('PIXEL_FORMAT_RGB_565').which.is.a.Number();
		});
		it('PIXEL_FORMAT_RGB_888', () => {
			should(Ti.UI.Android).have.constant('PIXEL_FORMAT_RGB_888').which.is.a.Number();
		});
		it('PIXEL_FORMAT_RGBA_4444', () => {
			should(Ti.UI.Android).have.constant('PIXEL_FORMAT_RGBA_4444').which.is.a.Number();
		});
		it('PIXEL_FORMAT_RGBA_5551', () => {
			should(Ti.UI.Android).have.constant('PIXEL_FORMAT_RGBA_5551').which.is.a.Number();
		});
		it('PIXEL_FORMAT_RGBA_8888', () => {
			should(Ti.UI.Android).have.constant('PIXEL_FORMAT_RGBA_8888').which.is.a.Number();
		});
		it('PIXEL_FORMAT_RGBX_8888', () => {
			should(Ti.UI.Android).have.constant('PIXEL_FORMAT_RGBX_8888').which.is.a.Number();
		});
		it('PIXEL_FORMAT_TRANSLUCENT', () => {
			should(Ti.UI.Android).have.constant('PIXEL_FORMAT_TRANSLUCENT').which.is.a.Number();
		});
		it('PIXEL_FORMAT_TRANSPARENT', () => {
			should(Ti.UI.Android).have.constant('PIXEL_FORMAT_TRANSPARENT').which.is.a.Number();
		});
		it('PIXEL_FORMAT_UNKNOWN', () => {
			should(Ti.UI.Android).have.constant('PIXEL_FORMAT_UNKNOWN').which.is.a.Number();
		});

		it('PROGRESS_INDICATOR_DETERMINANT', () => {
			should(Ti.UI.Android).have.constant('PROGRESS_INDICATOR_DETERMINANT').which.is.a.Number();
		});
		it('PROGRESS_INDICATOR_DIALOG', () => {
			should(Ti.UI.Android).have.constant('PROGRESS_INDICATOR_DIALOG').which.is.a.Number();
		});
		it('PROGRESS_INDICATOR_INDETERMINANT', () => {
			should(Ti.UI.Android).have.constant('PROGRESS_INDICATOR_INDETERMINANT').which.is.a.Number();
		});
		it('PROGRESS_INDICATOR_STATUS_BAR', () => {
			should(Ti.UI.Android).have.constant('PROGRESS_INDICATOR_STATUS_BAR').which.is.a.Number();
		});

		it('SOFT_INPUT_ADJUST_PAN', () => {
			should(Ti.UI.Android).have.constant('SOFT_INPUT_ADJUST_PAN').which.is.a.Number();
		});
		it('SOFT_INPUT_ADJUST_RESIZE', () => {
			should(Ti.UI.Android).have.constant('SOFT_INPUT_ADJUST_RESIZE').which.is.a.Number();
		});
		it('SOFT_INPUT_ADJUST_UNSPECIFIED', () => {
			should(Ti.UI.Android).have.constant('SOFT_INPUT_ADJUST_UNSPECIFIED').which.is.a.Number();
		});
		it('SOFT_INPUT_STATE_ALWAYS_HIDDEN', () => {
			should(Ti.UI.Android).have.constant('SOFT_INPUT_STATE_ALWAYS_HIDDEN').which.is.a.Number();
		});
		it('SOFT_INPUT_STATE_ALWAYS_VISIBLE', () => {
			should(Ti.UI.Android).have.constant('SOFT_INPUT_STATE_ALWAYS_VISIBLE').which.is.a.Number();
		});
		it('SOFT_INPUT_STATE_HIDDEN', () => {
			should(Ti.UI.Android).have.constant('SOFT_INPUT_STATE_HIDDEN').which.is.a.Number();
		});
		it('SOFT_INPUT_STATE_UNSPECIFIED', () => {
			should(Ti.UI.Android).have.constant('SOFT_INPUT_STATE_UNSPECIFIED').which.is.a.Number();
		});
		it('SOFT_INPUT_STATE_VISIBLE', () => {
			should(Ti.UI.Android).have.constant('SOFT_INPUT_STATE_VISIBLE').which.is.a.Number();
		});

		it('SOFT_KEYBOARD_DEFAULT_ON_FOCUS', () => {
			should(Ti.UI.Android).have.constant('SOFT_KEYBOARD_DEFAULT_ON_FOCUS').which.is.a.Number();
		});
		it('SOFT_KEYBOARD_HIDE_ON_FOCUS', () => {
			should(Ti.UI.Android).have.constant('SOFT_KEYBOARD_HIDE_ON_FOCUS').which.is.a.Number();
		});
		it('SOFT_KEYBOARD_SHOW_ON_FOCUS', () => {
			should(Ti.UI.Android).have.constant('SOFT_KEYBOARD_SHOW_ON_FOCUS').which.is.a.Number();
		});

		it('TABS_STYLE_BOTTOM_NAVIGATION', () => {
			should(Ti.UI.Android).have.constant('TABS_STYLE_BOTTOM_NAVIGATION').which.is.a.Number();
		});
		it('TABS_STYLE_DEFAULT', () => {
			should(Ti.UI.Android).have.constant('TABS_STYLE_DEFAULT').which.is.a.Number();
		});

		it('TRANSITION_CHANGE_BOUNDS', () => {
			should(Ti.UI.Android).have.constant('TRANSITION_CHANGE_BOUNDS').which.is.a.Number();
		});
		it('TRANSITION_CHANGE_CLIP_BOUNDS', () => {
			should(Ti.UI.Android).have.constant('TRANSITION_CHANGE_CLIP_BOUNDS').which.is.a.Number();
		});
		it('TRANSITION_CHANGE_IMAGE_TRANSFORM', () => {
			should(Ti.UI.Android).have.constant('TRANSITION_CHANGE_IMAGE_TRANSFORM').which.is.a.Number();
		});
		it('TRANSITION_CHANGE_TRANSFORM', () => {
			should(Ti.UI.Android).have.constant('TRANSITION_CHANGE_TRANSFORM').which.is.a.Number();
		});
		it('TRANSITION_EXPLODE', () => {
			should(Ti.UI.Android).have.constant('TRANSITION_EXPLODE').which.is.a.Number();
		});
		it('TRANSITION_FADE_IN', () => {
			should(Ti.UI.Android).have.constant('TRANSITION_FADE_IN').which.is.a.Number();
		});
		it('TRANSITION_FADE_OUT', () => {
			should(Ti.UI.Android).have.constant('TRANSITION_FADE_OUT').which.is.a.Number();
		});
		it('TRANSITION_NONE', () => {
			should(Ti.UI.Android).have.constant('TRANSITION_NONE').which.is.a.Number();
		});
		it('TRANSITION_SLIDE_BOTTOM', () => {
			should(Ti.UI.Android).have.constant('TRANSITION_SLIDE_BOTTOM').which.is.a.Number();
		});
		it('TRANSITION_SLIDE_LEFT', () => {
			should(Ti.UI.Android).have.constant('TRANSITION_SLIDE_LEFT').which.is.a.Number();
		});
		it('TRANSITION_SLIDE_RIGHT', () => {
			should(Ti.UI.Android).have.constant('TRANSITION_SLIDE_RIGHT').which.is.a.Number();
		});
		it('TRANSITION_SLIDE_TOP', () => {
			should(Ti.UI.Android).have.constant('TRANSITION_SLIDE_TOP').which.is.a.Number();
		});

		it('WEBVIEW_LOAD_CACHE_ELSE_NETWORK', () => {
			should(Ti.UI.Android).have.constant('WEBVIEW_LOAD_CACHE_ELSE_NETWORK').which.is.a.Number();
		});
		it('WEBVIEW_LOAD_CACHE_ONLY', () => {
			should(Ti.UI.Android).have.constant('WEBVIEW_LOAD_CACHE_ONLY').which.is.a.Number();
		});
		it('WEBVIEW_LOAD_DEFAULT', () => {
			should(Ti.UI.Android).have.constant('WEBVIEW_LOAD_DEFAULT').which.is.a.Number();
		});
		it('WEBVIEW_LOAD_NO_CACHE', () => {
			should(Ti.UI.Android).have.constant('WEBVIEW_LOAD_NO_CACHE').which.is.a.Number();
		});
		it('WEBVIEW_PLUGINS_OFF', () => {
			should(Ti.UI.Android).have.constant('WEBVIEW_PLUGINS_OFF').which.is.a.Number();
		});
		it('WEBVIEW_PLUGINS_ON', () => {
			should(Ti.UI.Android).have.constant('WEBVIEW_PLUGINS_ON').which.is.a.Number();
		});
		it('WEBVIEW_PLUGINS_ON_DEMAND', () => {
			should(Ti.UI.Android).have.constant('WEBVIEW_PLUGINS_ON_DEMAND').which.is.a.Number();
		});
	});
});
