/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
/* global OS_VERSION_MAJOR */
/* eslint-env mocha */
/* eslint no-unused-expressions: "off" */
'use strict';
const should = require('./utilities/assertions');

describe.ios('Titanium.UI.iOS', function () {
	// --- properties ---
	it.iosBroken('.appBadge', function () {
		should(Ti.UI.iOS.appBadge).be.undefined(); // FIXME: Defaults to 0!
		// TODO Set the value and test it got set
	});

	it('.appSupportsShakeToEdit', function () {
		should(Ti.UI.iOS.appSupportsShakeToEdit).be.true(); // TODO Set default to true in docs?
		// TODO Set the value and test it got set
	});

	it('.forceTouchSupported', function () {
		should(Ti.UI.iOS).have.readOnlyProperty('forceTouchSupported').which.is.a.Boolean();
		// TODO Validate the value based on the device?
	});

	it('.statusBarBackgroundColor', function () {
		should(Ti.UI.iOS.statusBarBackgroundColor).be.undefined();
		// TODO Test that this accepts normal color values (names, hex, etc)
	});

	// --- methods ---

	// TIMOB-23542 test livePhotoBadge
	it('#createLivePhotoBadge()', function () {
		var livePhotoBadge;
		should(Ti.UI.iOS.createLivePhotoBadge).not.be.undefined();
		should(Ti.UI.iOS.createLivePhotoBadge).be.a.Function();
		livePhotoBadge = Ti.UI.iOS.createLivePhotoBadge(Ti.UI.iOS.LIVEPHOTO_BADGE_OPTIONS_OVER_CONTENT);
		should(livePhotoBadge).be.an.Object();
		// TODO Test that we created a Ti.Blob!
	});

	// TODO Add tests for createTransitionAnimation
	it('#createTransitionAnimation(Object)');

	it('#constants', function () {
		// Used in BlurView.effect
		should(Ti.UI.iOS.BLUR_EFFECT_STYLE_DARK).be.a.Number();
		should(Ti.UI.iOS.BLUR_EFFECT_STYLE_EXTRA_LIGHT).be.a.Number();
		should(Ti.UI.iOS.BLUR_EFFECT_STYLE_LIGHT).be.a.Number();
		should(Ti.UI.iOS.BLUR_EFFECT_STYLE_PROMINENT).be.a.Number();
		should(Ti.UI.iOS.BLUR_EFFECT_STYLE_REGULAR).be.a.Number();

		// Used in View.clipMode
		should(Ti.UI.iOS.CLIP_MODE_DEFAULT).be.a.Number();
		should(Ti.UI.iOS.CLIP_MODE_DISABLED).be.a.Number();
		should(Ti.UI.iOS.CLIP_MODE_ENABLED).be.a.Number();

		// Used in CollisionBehavior.collisionMode
		should(Ti.UI.iOS.COLLISION_MODE_ALL).be.a.Number();
		should(Ti.UI.iOS.COLLISION_MODE_BOUNDARY).be.a.Number();
		should(Ti.UI.iOS.COLLISION_MODE_ITEM).be.a.Number();

		// Used in FeedbackGenerator.style
		should(Ti.UI.iOS.FEEDBACK_GENERATOR_IMPACT_STYLE_HEAVY).be.a.Number();
		should(Ti.UI.iOS.FEEDBACK_GENERATOR_IMPACT_STYLE_LIGHT).be.a.Number();
		should(Ti.UI.iOS.FEEDBACK_GENERATOR_IMPACT_STYLE_MEDIUM).be.a.Number();
		should(Ti.UI.iOS.FEEDBACK_GENERATOR_NOTIFICATION_TYPE_ERROR).be.a.Number();
		should(Ti.UI.iOS.FEEDBACK_GENERATOR_NOTIFICATION_TYPE_SUCCESS).be.a.Number();
		should(Ti.UI.iOS.FEEDBACK_GENERATOR_NOTIFICATION_TYPE_WARNING).be.a.Number();

		// Used in FeedbackGenerator.type
		should(Ti.UI.iOS.FEEDBACK_GENERATOR_TYPE_IMPACT).be.a.Number();
		should(Ti.UI.iOS.FEEDBACK_GENERATOR_TYPE_NOTIFICATION).be.a.Number();
		should(Ti.UI.iOS.FEEDBACK_GENERATOR_TYPE_SELECTION).be.a.Number();

		// Used in ScrollView.keyboardDismissMode, ListView.keyboardDismissMode & TableView.keyboardDismissMode
		should(Ti.UI.iOS.KEYBOARD_DISMISS_MODE_INTERACTIVE).be.a.Number();
		should(Ti.UI.iOS.KEYBOARD_DISMISS_MODE_NONE).be.a.Number();
		should(Ti.UI.iOS.KEYBOARD_DISMISS_MODE_ON_DRAG).be.a.Number();

		// Used in Window.largeTitleDisplayMode
		should(Ti.UI.iOS.LARGE_TITLE_DISPLAY_MODE_ALWAYS).be.a.Number();
		should(Ti.UI.iOS.LARGE_TITLE_DISPLAY_MODE_AUTOMATIC).be.a.Number();
		should(Ti.UI.iOS.LARGE_TITLE_DISPLAY_MODE_NEVER).be.a.Number();

		// Used in LivePhotoBadge.type
		should(Ti.UI.iOS.LIVEPHOTO_BADGE_OPTIONS_LIVE_OFF).be.a.Number();
		should(Ti.UI.iOS.LIVEPHOTO_BADGE_OPTIONS_OVER_CONTENT).be.a.Number();

		// modal Presentation Styles
		should(Ti.UI.iOS.MODAL_PRESENTATION_PAGESHEET).be.a.Number();
		should(Ti.UI.iOS.MODAL_PRESENTATION_FORMSHEET).be.a.Number();
		should(Ti.UI.iOS.MODAL_PRESENTATION_CURRENT_CONTEXT).be.a.Number();
		should(Ti.UI.iOS.MODAL_PRESENTATION_OVER_CURRENT_CONTEXT).be.a.Number();
		should(Ti.UI.iOS.MODAL_PRESENTATION_OVER_CURRENT_FULL_SCREEN).be.a.Number();

		// Used in WebView.basicAuthentication.persistence
		should(Ti.UI.iOS.CREDENTIAL_PERSISTENCE_NONE).be.a.Number();
		should(Ti.UI.iOS.CREDENTIAL_PERSISTENCE_FOR_SESSION).be.a.Number();
		should(Ti.UI.iOS.CREDENTIAL_PERSISTENCE_PERMANENT).be.a.Number();
		should(Ti.UI.iOS.CREDENTIAL_PERSISTENCE_SYNCHRONIZABLE).be.a.Number();

		// Used in WebViewConfiguration.mediaTypesRequiringUserActionForPlayback
		should(Ti.UI.iOS.AUDIOVISUAL_MEDIA_TYPE_NONE).be.a.Number();
		should(Ti.UI.iOS.AUDIOVISUAL_MEDIA_TYPE_AUDIO).be.a.Number();
		should(Ti.UI.iOS.AUDIOVISUAL_MEDIA_TYPE_VIDEO).be.a.Number();
		should(Ti.UI.iOS.AUDIOVISUAL_MEDIA_TYPE_ALL).be.a.Number();

		// Used in WebView.cachePolicy
		should(Ti.UI.iOS.CACHE_POLICY_USE_PROTOCOL_CACHE_POLICY).be.a.Number();
		should(Ti.UI.iOS.CACHE_POLICY_RELOAD_IGNORING_LOCAL_CACHE_DATA).be.a.Number();
		should(Ti.UI.iOS.CACHE_POLICY_RETURN_CACHE_DATA_ELSE_LOAD).be.a.Number();
		should(Ti.UI.iOS.CACHE_POLICY_RETURN_CACHE_DATA_DONT_LOAD).be.a.Number();

		// Used in WebViewConfiguration.selectionGranularity
		should(Ti.UI.iOS.SELECTION_GRANULARITY_DYNAMIC).be.a.Number();
		should(Ti.UI.iOS.SELECTION_GRANULARITY_CHARACTER).be.a.Number();

		// Used in WebViewDecisionHandler.invoke
		should(Ti.UI.iOS.ACTION_POLICY_CANCEL).be.a.Number();
		should(Ti.UI.iOS.ACTION_POLICY_ALLOW).be.a.Number();

		// Used in WebView.addUserScript.injectionTime
		should(Ti.UI.iOS.INJECTION_TIME_DOCUMENT_START).be.a.Number();
		should(Ti.UI.iOS.INJECTION_TIME_DOCUMENT_END).be.a.Number();
	});

	it('#systemImage()', function () {
		if (OS_VERSION_MAJOR >= 13) {
			should(Ti.UI.iOS.systemImage).not.be.undefined();
			should(Ti.UI.iOS.systemImage).be.a.Function();
			const systemImage = Ti.UI.iOS.systemImage('drop.triangle.fill');
			should(systemImage).be.an.Object();
			const systemImageWithParameters = Ti.UI.iOS.systemImage('drop.triangle.fill', { weight: 'bold', size: 60 });
			should(systemImageWithParameters).be.an.Object();
		}
	});

	it('.BLUR_EFFECT_STYLE_SYSTEM_* constants', function () {
		// Used in BlurView.effect. Need to copy under #constatnt test case
		if (OS_VERSION_MAJOR >= 13) {
			should(Ti.UI.iOS.BLUR_EFFECT_STYLE_SYSTEM_ULTRA_THIN_MATERIAL).be.a.Number();
			should(Ti.UI.iOS.BLUR_EFFECT_STYLE_SYSTEM_THIN_MATERIAL).be.a.Number();
			should(Ti.UI.iOS.BLUR_EFFECT_STYLE_SYSTEM_MATERIAL).be.a.Number();
			should(Ti.UI.iOS.BLUR_EFFECT_STYLE_SYSTEM_THICK_MATERIAL).be.a.Number();
			should(Ti.UI.iOS.BLUR_EFFECT_STYLE_SYSTEM_CHROME_MATERIAL).be.a.Number();
			should(Ti.UI.iOS.BLUR_EFFECT_STYLE_SYSTEM_ULTRA_THIN_MATERIAL_LIGHT).be.a.Number();
			should(Ti.UI.iOS.BLUR_EFFECT_STYLE_SYSTEM_THIN_MATERIAL_LIGHT).be.a.Number();
			should(Ti.UI.iOS.BLUR_EFFECT_STYLE_SYSTEM_MATERIAL_LIGHT).be.a.Number();
			should(Ti.UI.iOS.BLUR_EFFECT_STYLE_SYSTEM_THICK_MATERIAL_LIGHT).be.a.Number();
			should(Ti.UI.iOS.BLUR_EFFECT_STYLE_SYSTEM_CHROME_MATERIAL_LIGHT).be.a.Number();
			should(Ti.UI.iOS.BLUR_EFFECT_STYLE_SYSTEM_ULTRA_THIN_MATERIAL_DARK).be.a.Number();
			should(Ti.UI.iOS.BLUR_EFFECT_STYLE_SYSTEM_THIN_MATERIAL_DARK).be.a.Number();
			should(Ti.UI.iOS.BLUR_EFFECT_STYLE_SYSTEM_MATERIAL_DARK).be.a.Number();
			should(Ti.UI.iOS.BLUR_EFFECT_STYLE_SYSTEM_THICK_MATERIAL_DARK).be.a.Number();
			should(Ti.UI.iOS.BLUR_EFFECT_STYLE_SYSTEM_CHROME_MATERIAL_DARK).be.a.Number();
		}
	});
});
