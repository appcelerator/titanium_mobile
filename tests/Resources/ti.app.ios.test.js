/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2018-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
/* global OS_VERSION_MAJOR */
/* eslint-env mocha */
/* eslint no-unused-expressions: "off" */
'use strict';
var should = require('./utilities/assertions');

describe.ios('Titanium.App.iOS', function () {

	// --- properties ---

	it('apiName', function () {
		should(Ti.App.iOS).have.readOnlyProperty('apiName').which.is.a.String();
		should(Ti.App.iOS.apiName).eql('Ti.App.iOS');
	});

	it('applicationOpenSettingsURL', function () {
		should(Ti.App.iOS.applicationOpenSettingsURL).be.a.String();
	});

	it('supportedUserActivityTypes', function () {
		should(Ti.App.iOS.supportedUserActivityTypes).be.undefined(); // Only non-null if set via Info.plist NSUserActivityTypes key
	});

	// --- methods ---

	it('#cancelLocalNotification(id)', function () {
		should(Ti.App.iOS.cancelLocalNotification).be.a.Function();
		// TODO: Add more tests
	});

	it('#cancelAllLocalNotifications()', function () {
		should(Ti.App.iOS.cancelAllLocalNotifications).be.a.Function();
		// TODO: Add more tests
	});

	it('#createSearchQuery(args)', function () {
		should(Ti.App.iOS.createSearchQuery).be.a.Function(); // More tests can be found in own suite
	});

	it('#createSearchableIndex(args)', function () {
		should(Ti.App.iOS.createSearchableIndex).be.a.Function();

		var searchableIndex = Ti.App.iOS.createSearchableIndex();

		should(searchableIndex).be.an.Object();
		should(searchableIndex.apiName).eql('Ti.App.iOS.SearchableIndex');
		should(searchableIndex.addToDefaultSearchableIndex).be.a.Function();
		should(searchableIndex.deleteAllSearchableItemByDomainIdenifiers).be.a.Function();
		should(searchableIndex.deleteAllSearchableItems).be.a.Function();
		should(searchableIndex.deleteAllSearchableItems).be.a.Function();
		should(searchableIndex.deleteSearchableItemsByIdentifiers).be.a.Function();
		should(searchableIndex.isSupported).be.a.Function();
	});

	it('#createSearchableItem(args)', function () {
		should(Ti.App.iOS.createSearchableItem).be.a.Function();

		var itemAttr = Ti.App.iOS.createSearchableItemAttributeSet({
			itemContentType: Ti.App.iOS.UTTYPE_IMAGE,
			title: 'Titanium Core Spotlight Tutorial',
			contentDescription: 'Tech Example \nOn: ' + String.formatDate(new Date(), 'short'),
			keywords: [ 'Mobile', 'Appcelerator', 'Titanium' ]
		});

		var searchableItem = Ti.App.iOS.createSearchableItem({
			uniqueIdentifier: 'my-id',
			domainIdentifier: 'com.mydomain',
			attributeSet: itemAttr
		});

		should(searchableItem).be.an.Object();
		should(searchableItem.apiName).eql('Ti.App.iOS.SearchableItem');
	});

	it('#createSearchableItemAttributeSet(args)', function () {
		should(Ti.App.iOS.createSearchableItemAttributeSet).be.a.Function();

		var itemAttr = Ti.App.iOS.createSearchableItemAttributeSet({
			itemContentType: Ti.App.iOS.UTTYPE_IMAGE,
			title: 'Titanium Core Spotlight Tutorial',
			contentDescription: 'Tech Example \nOn: ' + String.formatDate(new Date(), 'short'),
			keywords: [ 'Mobile', 'Appcelerator', 'Titanium' ]
		});

		should(itemAttr).be.an.Object();
		should(itemAttr.apiName).eql('Ti.App.iOS.SearchableItemAttributeSet');

		// TODO: Move to own test suite, test all 90+ properties
	});

	it('#createUserActivity(args)', function () {
		should(Ti.App.iOS.createUserActivity).be.a.Function();

		var userActivity = Ti.App.iOS.createUserActivity({
			activityType: 'com.setdirection.home',
			title: 'activity 1',
			userInfo: {
				msg: 'hello world'
			}
		});

		should(userActivity).be.an.Object();
		should(userActivity.apiName).eql('Ti.App.iOS.UserActivity');
		should(userActivity.addContentAttributeSet).be.a.Function();
		should(userActivity.becomeCurrent).be.a.Function();
		should(userActivity.resignCurrent).be.a.Function();
		should(userActivity.invalidate).be.a.Function();
		should(userActivity.isSupported).be.a.Function();
	});

	it('#createUserDefaults(args)', function (finish) {
		var userDefaults;

		function finishTest() {
			// This little hack is required to migrate from old tests where we
			// did not flush all existing properties
			if (!userDefaults || userDefaults.getString('test', null) !== 'tirocks') {
				return;
			}

			userDefaults.removeEventListener('change', finishTest);
			finish();
		}

		this.timeout(5000);

		should(Ti.App.iOS.createUserDefaults).be.a.Function();

		userDefaults = Ti.App.iOS.createUserDefaults({
			suiteName: 'group.mySuite'
		});

		// Flush all old values for fresh results
		userDefaults.removeAllProperties();

		userDefaults.addEventListener('change', finishTest);

		should(userDefaults).be.an.Object();
		should(userDefaults.apiName).eql('Ti.App.iOS.UserDefaults');
		should(userDefaults.getInt).be.a.Function();
		should(userDefaults.setInt).be.a.Function();
		should(userDefaults.getBool).be.a.Function();
		should(userDefaults.setBool).be.a.Function();
		should(userDefaults.getDouble).be.a.Function();
		should(userDefaults.setDouble).be.a.Function();
		should(userDefaults.getList).be.a.Function();
		should(userDefaults.setList).be.a.Function();
		should(userDefaults.getObject).be.a.Function();
		should(userDefaults.setObject).be.a.Function();
		should(userDefaults.getString).be.a.Function();
		should(userDefaults.setString).be.a.Function();
		should(userDefaults.hasProperty).be.a.Function();
		should(userDefaults.listProperties).be.a.Function();
		should(userDefaults.removeProperty).be.a.Function();
		should(userDefaults.removeAllProperties).be.a.Function();
		should(userDefaults.setString).be.a.Function();

		// Trigger change
		userDefaults.setString('test', 'tirocks');
	});

	it('#createUserNotificationAction(args)', function () {
		should(Ti.App.iOS.createUserNotificationAction).be.a.Function();
		// TODO: Add more tests
	});

	it('#createUserNotificationCategory(args)', function () {
		should(Ti.App.iOS.createUserNotificationCategory).be.a.Function();
		// TODO: Add more tests
	});

	it('UserNotificationCenter', function () {
		should(Ti.App.iOS.UserNotificationCenter).be.an.Object();
		should(Ti.App.iOS.UserNotificationCenter.apiName).eql('Ti.App.iOS.UserNotificationCenter');
		should(Ti.App.iOS.UserNotificationCenter.getDeliveredNotifications).be.a.Function();
		should(Ti.App.iOS.UserNotificationCenter.getPendingNotifications).be.a.Function();
		should(Ti.App.iOS.UserNotificationCenter.removeDeliveredNotifications).be.a.Function();
		should(Ti.App.iOS.UserNotificationCenter.removePendingNotifications).be.a.Function();
		should(Ti.App.iOS.UserNotificationCenter.requestUserNotificationSettings).be.a.Function();
	});

	it('#constants', function () {
		should(Ti.App.iOS.BACKGROUNDFETCHINTERVAL_MIN).be.a.Number();
		should(Ti.App.iOS.BACKGROUNDFETCHINTERVAL_NEVER).be.a.Number();

		should(Ti.App.iOS.EVENT_ACCESSIBILITY_LAYOUT_CHANGED).be.a.String();
		should(Ti.App.iOS.EVENT_ACCESSIBILITY_SCREEN_CHANGED).be.a.String();

		if (OS_VERSION_MAJOR >= 13) {
			should(Ti.App.iOS.USER_INTERFACE_STYLE_UNSPECIFIED).be.a.Number();
			should(Ti.App.iOS.USER_INTERFACE_STYLE_LIGHT).be.a.Number();
			should(Ti.App.iOS.USER_INTERFACE_STYLE_DARK).be.a.Number();
		}

		should(Ti.App.iOS.USER_NOTIFICATION_ACTIVATION_MODE_BACKGROUND).be.a.Number();
		should(Ti.App.iOS.USER_NOTIFICATION_ACTIVATION_MODE_FOREGROUND).be.a.Number();

		should(Ti.App.iOS.USER_NOTIFICATION_ALERT_STYLE_ALERT).be.a.Number();
		should(Ti.App.iOS.USER_NOTIFICATION_ALERT_STYLE_BANNER).be.a.Number();
		should(Ti.App.iOS.USER_NOTIFICATION_ALERT_STYLE_NONE).be.a.Number();

		should(Ti.App.iOS.USER_NOTIFICATION_AUTHORIZATION_STATUS_AUTHORIZED).be.a.Number();
		should(Ti.App.iOS.USER_NOTIFICATION_AUTHORIZATION_STATUS_DENIED).be.a.Number();
		should(Ti.App.iOS.USER_NOTIFICATION_AUTHORIZATION_STATUS_NOT_DETERMINED).be.a.Number();

		should(Ti.App.iOS.USER_NOTIFICATION_BEHAVIOR_DEFAULT).be.a.Number();
		should(Ti.App.iOS.USER_NOTIFICATION_BEHAVIOR_TEXTINPUT).be.a.Number();

		should(Ti.App.iOS.USER_NOTIFICATION_CATEGORY_OPTION_CUSTOM_DISMISS_ACTION).be.a.Number();
		should(Ti.App.iOS.USER_NOTIFICATION_CATEGORY_OPTION_ALLOW_IN_CARPLAY).be.a.Number();
		should(Ti.App.iOS.USER_NOTIFICATION_CATEGORY_OPTION_HIDDEN_PREVIEWS_SHOW_TITLE).be.a.Number();
		should(Ti.App.iOS.USER_NOTIFICATION_CATEGORY_OPTION_HIDDEN_PREVIEWS_SHOW_SUBTITLE).be.a.Number();
		should(Ti.App.iOS.USER_NOTIFICATION_CATEGORY_OPTION_NONE).be.a.Number();

		should(Ti.App.iOS.USER_NOTIFICATION_SETTING_DISABLED).be.a.Number();
		should(Ti.App.iOS.USER_NOTIFICATION_SETTING_ENABLED).be.a.Number();
		should(Ti.App.iOS.USER_NOTIFICATION_SETTING_NOT_SUPPORTED).be.a.Number();

		should(Ti.App.iOS.USER_NOTIFICATION_TYPE_ALERT).be.a.Number();
		should(Ti.App.iOS.USER_NOTIFICATION_TYPE_BADGE).be.a.Number();
		should(Ti.App.iOS.USER_NOTIFICATION_TYPE_NONE).be.a.Number();
		should(Ti.App.iOS.USER_NOTIFICATION_TYPE_SOUND).be.a.Number();

		should(Ti.App.iOS.UTTYPE_AUDIO).be.a.String();
		should(Ti.App.iOS.UTTYPE_BMP).be.a.String();
		should(Ti.App.iOS.UTTYPE_FLAT_RTFD).be.a.String();
		should(Ti.App.iOS.UTTYPE_GIF).be.a.String();
		should(Ti.App.iOS.UTTYPE_HTML).be.a.String();
		should(Ti.App.iOS.UTTYPE_ICO).be.a.String();
		should(Ti.App.iOS.UTTYPE_IMAGE).be.a.String();
		should(Ti.App.iOS.UTTYPE_JPEG).be.a.String();
		should(Ti.App.iOS.UTTYPE_JPEG2000).be.a.String();
		should(Ti.App.iOS.UTTYPE_MOVIE).be.a.String();
		should(Ti.App.iOS.UTTYPE_MP3).be.a.String();
		should(Ti.App.iOS.UTTYPE_MPEG).be.a.String();
		should(Ti.App.iOS.UTTYPE_MPEG4).be.a.String();
		should(Ti.App.iOS.UTTYPE_MPEG4_AUDIO).be.a.String();
		should(Ti.App.iOS.UTTYPE_PDF).be.a.String();
		should(Ti.App.iOS.UTTYPE_PICT).be.a.String();
		should(Ti.App.iOS.UTTYPE_PLAIN_TEXT).be.a.String();
		should(Ti.App.iOS.UTTYPE_PNG).be.a.String();
		should(Ti.App.iOS.UTTYPE_QUICKTIME_IMAGE).be.a.String();
		should(Ti.App.iOS.UTTYPE_QUICKTIME_MOVIE).be.a.String();
		should(Ti.App.iOS.UTTYPE_RTF).be.a.String();
		should(Ti.App.iOS.UTTYPE_RTFD).be.a.String();
		should(Ti.App.iOS.UTTYPE_TEXT).be.a.String();
		should(Ti.App.iOS.UTTYPE_TIFF).be.a.String();
		should(Ti.App.iOS.UTTYPE_TXN_TEXT_AND_MULTIMEDIA_DATA).be.a.String();
		should(Ti.App.iOS.UTTYPE_UTF16_EXTERNAL_PLAIN_TEXT).be.a.String();
		should(Ti.App.iOS.UTTYPE_UTF16_PLAIN_TEXT).be.a.String();
		should(Ti.App.iOS.UTTYPE_UTF8_PLAIN_TEXT).be.a.String();
		should(Ti.App.iOS.UTTYPE_VIDEO).be.a.String();
		should(Ti.App.iOS.UTTYPE_WEB_ARCHIVE).be.a.String();
		should(Ti.App.iOS.UTTYPE_XML).be.a.String();

		should(Ti.App.iOS.BACKGROUND_TASK_TYPE_PROCESS).be.a.String();
		should(Ti.App.iOS.BACKGROUND_TASK_TYPE_REFRESH).be.a.String();
	});

	it.ios('.userInterfaceStyle', () => {
		if (OS_VERSION_MAJOR >= 13) {
			// We only check for the type, since the value (light, dark, unspecified)
			// can vary between device configs
			should(Ti.App.iOS.userInterfaceStyle).be.a.Number();
		}
	});

	it('#registerBackgroundTask', function (finish) {
		try {
			should(Ti.App.iOS.registerBackgroundTask).be.a.Function();

			Ti.App.iOS.registerBackgroundTask({
				identifier: 'com.test.bgprocessing',
				type: Ti.App.iOS.BACKGROUND_TASK_TYPE_PROCESS,
				powerConnect: true,
				networkConnect: true
			});
		} catch (err) {
			return finish(err);
		}
		finish();
	});
});
