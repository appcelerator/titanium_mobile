/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2020-Present by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
/* eslint-env mocha */
/* eslint no-unused-expressions: "off" */
/* eslint no-undef: "off" */
'use strict';

const should = require('./utilities/assertions');

describe.android('Titanium.Android', () => {
	describe('properties', () => {
		describe('.apiName', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.readOnlyProperty('apiName').which.is.a.String();
			});

			it('equals Ti.Android', () => {
				should(Ti.Android.apiName).eql('Ti.Android');
			});
		});

		describe('.currentActivity', () => {
			it('is a Titanium.Android.Activity', () => {
				should(Ti.Android).have.a.readOnlyProperty('currentActivity').which.is.an.Object();
				should(Ti.Android.currentActivity).have.a.readOnlyProperty('apiName').which.eql('Ti.Android.Activity');
			});
		});

		describe('.currentService', () => {
			it('is null when running main app', () => {
				should(Ti.Android).have.a.readOnlyProperty('currentService').which.is.null();
			});

			// TODO: Test this is a service when running a service!
			// it('is a Titanium.Android.Service', () => {
			// 	should(Ti.Android).have.a.readOnlyProperty('currentService').which.is.an.Object();
			// 	should(Ti.Android.currentService).have.a.readOnlyProperty('apiName').which.eql('Ti.Android.Service');
			// });
		});

		describe('.rootActivity', () => {
			it('is a Titanium.Android.Activity', () => {
				should(Ti.Android).have.a.readOnlyProperty('rootActivity').which.is.an.Object();
				should(Ti.Android.rootActivity).have.a.readOnlyProperty('apiName').which.eql('Ti.Android.Activity');
			});
		});

	});

	describe('methods', () => {
		describe('#createBroadcastIntent', () => {
			it('is a Function', () => {
				should(Ti.Android.createBroadcastIntent).be.a.Function();
			});
		});

		describe('#createIntentChooser', () => {
			it('is a Function', () => {
				should(Ti.Android.createIntentChooser).be.a.Function();
			});
		});

		describe('#createPendingIntent', () => {
			it('is a Function', () => {
				should(Ti.Android.createPendingIntent).be.a.Function();
			});
		});

		describe('#createService', () => {
			it('is a Function', () => {
				should(Ti.Android.createService).be.a.Function();
			});
		});

		describe('#createServiceIntent', () => {
			it('is a Function', () => {
				should(Ti.Android.createServiceIntent).be.a.Function();
			});
		});

		describe('#hasPermission', () => {
			it('is a Function', () => {
				should(Ti.Android.hasPermission).be.a.Function();
			});
		});

		describe('#isServiceRunning', () => {
			it('is a Function', () => {
				should(Ti.Android.isServiceRunning).be.a.Function();
			});
		});

		describe('#registerBroadcastReceiver', () => {
			it('is a Function', () => {
				should(Ti.Android.registerBroadcastReceiver).be.a.Function();
			});
		});

		describe('#requestPermissions', () => {
			it('is a Function', () => {
				should(Ti.Android.requestPermissions).be.a.Function();
			});
		});

		describe('#startService', () => {
			it('is a Function', () => {
				should(Ti.Android.startService).be.a.Function();
			});
		});

		describe('#stopService', () => {
			it('is a Function', () => {
				should(Ti.Android.stopService).be.a.Function();
			});
		});

		describe('#unregisterBroadcastReceiver', () => {
			it('is a Function', () => {
				should(Ti.Android.unregisterBroadcastReceiver).be.a.Function();
			});
		});

	});

	describe('constants', () => {
		describe('.ACTION_AIRPLANE_MODE_CHANGED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_AIRPLANE_MODE_CHANGED').which.is.a.String();
			});
		});

		describe('.ACTION_ALL_APPS', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_ALL_APPS').which.is.a.String();
			});
		});

		describe('.ACTION_ANSWER', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_ANSWER').which.is.a.String();
			});
		});

		describe('.ACTION_ATTACH_DATA', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_ATTACH_DATA').which.is.a.String();
			});
		});

		describe('.ACTION_BATTERY_CHANGED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_BATTERY_CHANGED').which.is.a.String();
			});
		});

		describe('.ACTION_BATTERY_LOW', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_BATTERY_LOW').which.is.a.String();
			});
		});

		describe('.ACTION_BATTERY_OKAY', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_BATTERY_OKAY').which.is.a.String();
			});
		});

		describe('.ACTION_BOOT_COMPLETED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_BOOT_COMPLETED').which.is.a.String();
			});
		});

		describe('.ACTION_BUG_REPORT', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_BUG_REPORT').which.is.a.String();
			});
		});

		describe('.ACTION_CALL', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_CALL').which.is.a.String();
			});
		});

		describe('.ACTION_CALL_BUTTON', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_CALL_BUTTON').which.is.a.String();
			});
		});

		describe('.ACTION_CAMERA_BUTTON', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_CAMERA_BUTTON').which.is.a.String();
			});
		});

		describe('.ACTION_CHOOSER', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_CHOOSER').which.is.a.String();
			});
		});

		describe('.ACTION_CLOSE_SYSTEM_DIALOGS', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_CLOSE_SYSTEM_DIALOGS').which.is.a.String();
			});
		});

		describe('.ACTION_CONFIGURATION_CHANGED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_CONFIGURATION_CHANGED').which.is.a.String();
			});
		});

		describe('.ACTION_CREATE_SHORTCUT', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_CREATE_SHORTCUT').which.is.a.String();
			});
		});

		describe('.ACTION_DATE_CHANGED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_DATE_CHANGED').which.is.a.String();
			});
		});

		describe('.ACTION_DEFAULT', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_DEFAULT').which.is.a.String();
			});
		});

		describe('.ACTION_DELETE', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_DELETE').which.is.a.String();
			});
		});

		describe('.ACTION_DEVICE_STORAGE_LOW', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_DEVICE_STORAGE_LOW').which.is.a.String();
			});
		});

		describe('.ACTION_DIAL', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_DIAL').which.is.a.String();
			});
		});

		describe('.ACTION_EDIT', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_EDIT').which.is.a.String();
			});
		});

		describe('.ACTION_GET_CONTENT', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_GET_CONTENT').which.is.a.String();
			});
		});

		describe('.ACTION_GTALK_SERVICE_CONNECTED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_GTALK_SERVICE_CONNECTED').which.is.a.String();
			});
		});

		describe('.ACTION_GTALK_SERVICE_DISCONNECTED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_GTALK_SERVICE_DISCONNECTED').which.is.a.String();
			});
		});

		describe('.ACTION_HEADSET_PLUG', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_HEADSET_PLUG').which.is.a.String();
			});
		});

		describe('.ACTION_INPUT_METHOD_CHANGED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_INPUT_METHOD_CHANGED').which.is.a.String();
			});
		});

		describe('.ACTION_INSERT', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_INSERT').which.is.a.String();
			});
		});

		describe('.ACTION_INSERT_OR_EDIT', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_INSERT_OR_EDIT').which.is.a.String();
			});
		});

		describe('.ACTION_MAIN', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_MAIN').which.is.a.String();
			});
		});

		describe('.ACTION_MANAGE_PACKAGE_STORAGE', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_MANAGE_PACKAGE_STORAGE').which.is.a.String();
			});
		});

		describe('.ACTION_MEDIA_BAD_REMOVAL', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_MEDIA_BAD_REMOVAL').which.is.a.String();
			});
		});

		describe('.ACTION_MEDIA_BUTTON', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_MEDIA_BUTTON').which.is.a.String();
			});
		});

		describe('.ACTION_MEDIA_CHECKING', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_MEDIA_CHECKING').which.is.a.String();
			});
		});

		describe('.ACTION_MEDIA_EJECT', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_MEDIA_EJECT').which.is.a.String();
			});
		});

		describe('.ACTION_MEDIA_MOUNTED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_MEDIA_MOUNTED').which.is.a.String();
			});
		});

		describe('.ACTION_MEDIA_NOFS', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_MEDIA_NOFS').which.is.a.String();
			});
		});

		describe('.ACTION_MEDIA_REMOVED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_MEDIA_REMOVED').which.is.a.String();
			});
		});

		describe('.ACTION_MEDIA_SCANNER_FINISHED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_MEDIA_SCANNER_FINISHED').which.is.a.String();
			});
		});

		describe('.ACTION_MEDIA_SCANNER_SCAN_FILE', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_MEDIA_SCANNER_SCAN_FILE').which.is.a.String();
			});
		});

		describe('.ACTION_MEDIA_SCANNER_STARTED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_MEDIA_SCANNER_STARTED').which.is.a.String();
			});
		});

		describe('.ACTION_MEDIA_SHARED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_MEDIA_SHARED').which.is.a.String();
			});
		});

		describe('.ACTION_MEDIA_UNMOUNTABLE', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_MEDIA_UNMOUNTABLE').which.is.a.String();
			});
		});

		describe('.ACTION_MEDIA_UNMOUNTED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_MEDIA_UNMOUNTED').which.is.a.String();
			});
		});

		describe('.ACTION_NEW_OUTGOING_CALL', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_NEW_OUTGOING_CALL').which.is.a.String();
			});
		});

		describe('.ACTION_PACKAGE_ADDED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_PACKAGE_ADDED').which.is.a.String();
			});
		});

		describe('.ACTION_PACKAGE_CHANGED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_PACKAGE_CHANGED').which.is.a.String();
			});
		});

		describe('.ACTION_PACKAGE_DATA_CLEARED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_PACKAGE_DATA_CLEARED').which.is.a.String();
			});
		});

		describe('.ACTION_PACKAGE_REMOVED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_PACKAGE_REMOVED').which.is.a.String();
			});
		});

		describe('.ACTION_PACKAGE_REPLACED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_PACKAGE_REPLACED').which.is.a.String();
			});
		});

		describe('.ACTION_PACKAGE_RESTARTED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_PACKAGE_RESTARTED').which.is.a.String();
			});
		});

		describe('.ACTION_PICK', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_PICK').which.is.a.String();
			});
		});

		describe('.ACTION_PICK_ACTIVITY', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_PICK_ACTIVITY').which.is.a.String();
			});
		});

		describe('.ACTION_POWER_CONNECTED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_POWER_CONNECTED').which.is.a.String();
			});
		});

		describe('.ACTION_POWER_DISCONNECTED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_POWER_DISCONNECTED').which.is.a.String();
			});
		});

		describe('.ACTION_POWER_USAGE_SUMMARY', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_POWER_USAGE_SUMMARY').which.is.a.String();
			});
		});

		describe('.ACTION_PROVIDER_CHANGED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_PROVIDER_CHANGED').which.is.a.String();
			});
		});

		describe('.ACTION_REBOOT', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_REBOOT').which.is.a.String();
			});
		});

		describe('.ACTION_RUN', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_RUN').which.is.a.String();
			});
		});

		describe('.ACTION_SCREEN_OFF', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_SCREEN_OFF').which.is.a.String();
			});
		});

		describe('.ACTION_SCREEN_ON', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_SCREEN_ON').which.is.a.String();
			});
		});

		describe('.ACTION_SEARCH', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_SEARCH').which.is.a.String();
			});
		});

		describe('.ACTION_SEARCH_LONG_PRESS', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_SEARCH_LONG_PRESS').which.is.a.String();
			});
		});

		describe('.ACTION_SEND', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_SEND').which.is.a.String();
			});
		});

		describe('.ACTION_SEND_MULTIPLE', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_SEND_MULTIPLE').which.is.a.String();
			});
		});

		describe('.ACTION_SENDTO', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_SENDTO').which.is.a.String();
			});
		});

		describe('.ACTION_SET_WALLPAPER', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_SET_WALLPAPER').which.is.a.String();
			});
		});

		describe('.ACTION_SHUTDOWN', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_SHUTDOWN').which.is.a.String();
			});
		});

		describe('.ACTION_SYNC', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_SYNC').which.is.a.String();
			});
		});

		describe('.ACTION_SYSTEM_TUTORIAL', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_SYSTEM_TUTORIAL').which.is.a.String();
			});
		});

		describe('.ACTION_TIME_CHANGED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_TIME_CHANGED').which.is.a.String();
			});
		});

		describe('.ACTION_TIME_TICK', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_TIME_TICK').which.is.a.String();
			});
		});

		describe('.ACTION_UID_REMOVED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_UID_REMOVED').which.is.a.String();
			});
		});

		describe('.ACTION_USER_PRESENT', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_USER_PRESENT').which.is.a.String();
			});
		});

		describe('.ACTION_VIEW', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_VIEW').which.is.a.String();
			});
		});

		describe('.ACTION_VOICE_COMMAND', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_VOICE_COMMAND').which.is.a.String();
			});
		});

		describe('.ACTION_WALLPAPER_CHANGED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_WALLPAPER_CHANGED').which.is.a.String();
			});
		});

		describe('.ACTION_WEB_SEARCH', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('ACTION_WEB_SEARCH').which.is.a.String();
			});
		});

		describe('.CATEGORY_ALARM', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_ALARM').which.is.a.String();
			});
		});

		describe('.CATEGORY_ALTERNATIVE', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_ALTERNATIVE').which.is.a.String();
			});
		});

		describe('.CATEGORY_BROWSABLE', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_BROWSABLE').which.is.a.String();
			});
		});

		describe('.CATEGORY_CALL', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_CALL').which.is.a.String();
			});
		});

		describe('.CATEGORY_DEFAULT', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_DEFAULT').which.is.a.String();
			});
		});

		describe('.CATEGORY_DEVELOPMENT_PREFERENCE', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_DEVELOPMENT_PREFERENCE').which.is.a.String();
			});
		});

		describe('.CATEGORY_EMAIL', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_EMAIL').which.is.a.String();
			});
		});

		describe('.CATEGORY_EMBED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_EMBED').which.is.a.String();
			});
		});

		describe('.CATEGORY_ERROR', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_ERROR').which.is.a.String();
			});
		});

		describe('.CATEGORY_EVENT', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_EVENT').which.is.a.String();
			});
		});

		describe('.CATEGORY_FRAMEWORK_INSTRUMENTATION_TEST', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_FRAMEWORK_INSTRUMENTATION_TEST').which.is.a.String();
			});
		});

		describe('.CATEGORY_HOME', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_HOME').which.is.a.String();
			});
		});

		describe('.CATEGORY_INFO', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_INFO').which.is.a.String();
			});
		});

		describe('.CATEGORY_LAUNCHER', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_LAUNCHER').which.is.a.String();
			});
		});

		describe('.CATEGORY_MESSAGE', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_MESSAGE').which.is.a.String();
			});
		});

		describe('.CATEGORY_MONKEY', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_MONKEY').which.is.a.String();
			});
		});

		describe('.CATEGORY_OPENABLE', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_OPENABLE').which.is.a.String();
			});
		});

		describe('.CATEGORY_PREFERENCE', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_PREFERENCE').which.is.a.String();
			});
		});

		describe('.CATEGORY_PROGRESS', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_PROGRESS').which.is.a.String();
			});
		});

		describe('.CATEGORY_PROMO', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_PROMO').which.is.a.String();
			});
		});

		describe('.CATEGORY_RECOMMENDATION', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_RECOMMENDATION').which.is.a.String();
			});
		});

		describe('.CATEGORY_SAMPLE_CODE', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_SAMPLE_CODE').which.is.a.String();
			});
		});

		describe('.CATEGORY_SELECTED_ALTERNATIVE', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_SELECTED_ALTERNATIVE').which.is.a.String();
			});
		});

		describe('.CATEGORY_SERVICE', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_SERVICE').which.is.a.String();
			});
		});

		describe('.CATEGORY_SOCIAL', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_SOCIAL').which.is.a.String();
			});
		});

		describe('.CATEGORY_STATUS', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_STATUS').which.is.a.String();
			});
		});

		describe('.CATEGORY_TAB', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_TAB').which.is.a.String();
			});
		});

		describe('.CATEGORY_TEST', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_TEST').which.is.a.String();
			});
		});

		describe('.CATEGORY_TRANSPORT', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_TRANSPORT').which.is.a.String();
			});
		});

		describe('.CATEGORY_UNIT_TEST', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('CATEGORY_UNIT_TEST').which.is.a.String();
			});
		});

		describe('.DEFAULT_ALL', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('DEFAULT_ALL').which.is.a.Number();
			});
		});

		describe('.DEFAULT_LIGHTS', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('DEFAULT_LIGHTS').which.is.a.Number();
			});
		});

		describe('.DEFAULT_SOUND', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('DEFAULT_SOUND').which.is.a.Number();
			});
		});

		describe('.DEFAULT_VIBRATE', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('DEFAULT_VIBRATE').which.is.a.Number();
			});
		});

		describe('.EXTRA_ALARM_COUNT', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('EXTRA_ALARM_COUNT').which.is.a.String();
			});
		});

		describe('.EXTRA_BCC', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('EXTRA_BCC').which.is.a.String();
			});
		});

		describe('.EXTRA_CC', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('EXTRA_CC').which.is.a.String();
			});
		});

		describe('.EXTRA_DATA_REMOVED', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('EXTRA_DATA_REMOVED').which.is.a.String();
			});
		});

		describe('.EXTRA_DONT_KILL_APP', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('EXTRA_DONT_KILL_APP').which.is.a.String();
			});
		});

		describe('.EXTRA_EMAIL', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('EXTRA_EMAIL').which.is.a.String();
			});
		});

		describe('.EXTRA_INTENT', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('EXTRA_INTENT').which.is.a.String();
			});
		});

		describe('.EXTRA_KEY_EVENT', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('EXTRA_KEY_EVENT').which.is.a.String();
			});
		});

		describe('.EXTRA_PHONE_NUMBER', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('EXTRA_PHONE_NUMBER').which.is.a.String();
			});
		});

		describe('.EXTRA_REPLACING', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('EXTRA_REPLACING').which.is.a.String();
			});
		});

		describe('.EXTRA_SHORTCUT_ICON', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('EXTRA_SHORTCUT_ICON').which.is.a.String();
			});
		});

		describe('.EXTRA_SHORTCUT_ICON_RESOURCE', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('EXTRA_SHORTCUT_ICON_RESOURCE').which.is.a.String();
			});
		});

		describe('.EXTRA_SHORTCUT_INTENT', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('EXTRA_SHORTCUT_INTENT').which.is.a.String();
			});
		});

		describe('.EXTRA_SHORTCUT_NAME', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('EXTRA_SHORTCUT_NAME').which.is.a.String();
			});
		});

		describe('.EXTRA_STREAM', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('EXTRA_STREAM').which.is.a.String();
			});
		});

		describe('.EXTRA_SUBJECT', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('EXTRA_SUBJECT').which.is.a.String();
			});
		});

		describe('.EXTRA_TEMPLATE', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('EXTRA_TEMPLATE').which.is.a.String();
			});
		});

		describe('.EXTRA_TEXT', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('EXTRA_TEXT').which.is.a.String();
			});
		});

		describe('.EXTRA_TITLE', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('EXTRA_TITLE').which.is.a.String();
			});
		});

		describe('.EXTRA_UID', () => {
			it('is a String', () => {
				should(Ti.Android).have.a.constant('EXTRA_UID').which.is.a.String();
			});
		});

		describe('.FILL_IN_ACTION', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FILL_IN_ACTION').which.is.a.Number();
			});
		});

		describe('.FILL_IN_CATEGORIES', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FILL_IN_CATEGORIES').which.is.a.Number();
			});
		});

		describe('.FILL_IN_COMPONENT', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FILL_IN_COMPONENT').which.is.a.Number();
			});
		});

		describe('.FILL_IN_DATA', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FILL_IN_DATA').which.is.a.Number();
			});
		});

		describe('.FILL_IN_PACKAGE', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FILL_IN_PACKAGE').which.is.a.Number();
			});
		});

		describe('.FLAG_ACTIVITY_BROUGHT_TO_FRONT', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_ACTIVITY_BROUGHT_TO_FRONT').which.is.a.Number();
			});
		});

		describe('.FLAG_ACTIVITY_CLEAR_TOP', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_ACTIVITY_CLEAR_TOP').which.is.a.Number();
			});
		});

		describe('.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET').which.is.a.Number();
			});
		});

		describe('.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS').which.is.a.Number();
			});
		});

		describe('.FLAG_ACTIVITY_FORWARD_RESULT', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_ACTIVITY_FORWARD_RESULT').which.is.a.Number();
			});
		});

		describe('.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY').which.is.a.Number();
			});
		});

		describe('.FLAG_ACTIVITY_MULTIPLE_TASK', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_ACTIVITY_MULTIPLE_TASK').which.is.a.Number();
			});
		});

		describe('.FLAG_ACTIVITY_NEW_TASK', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_ACTIVITY_NEW_TASK').which.is.a.Number();
			});
		});

		describe('.FLAG_ACTIVITY_NO_ANIMATION', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_ACTIVITY_NO_ANIMATION').which.is.a.Number();
			});
		});

		describe('.FLAG_ACTIVITY_NO_HISTORY', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_ACTIVITY_NO_HISTORY').which.is.a.Number();
			});
		});

		describe('.FLAG_ACTIVITY_NO_USER_ACTION', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_ACTIVITY_NO_USER_ACTION').which.is.a.Number();
			});
		});

		describe('.FLAG_ACTIVITY_PREVIOUS_IS_TOP', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_ACTIVITY_PREVIOUS_IS_TOP').which.is.a.Number();
			});
		});

		describe('.FLAG_ACTIVITY_REORDER_TO_FRONT', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_ACTIVITY_REORDER_TO_FRONT').which.is.a.Number();
			});
		});

		describe('.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_ACTIVITY_RESET_TASK_IF_NEEDED').which.is.a.Number();
			});
		});

		describe('.FLAG_ACTIVITY_SINGLE_TOP', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_ACTIVITY_SINGLE_TOP').which.is.a.Number();
			});
		});

		describe('.FLAG_AUTO_CANCEL', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_AUTO_CANCEL').which.is.a.Number();
			});
		});

		describe('.FLAG_CANCEL_CURRENT', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_CANCEL_CURRENT').which.is.a.Number();
			});
		});

		describe('.FLAG_DEBUG_LOG_RESOLUTION', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_DEBUG_LOG_RESOLUTION').which.is.a.Number();
			});
		});

		describe('.FLAG_FROM_BACKGROUND', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_FROM_BACKGROUND').which.is.a.Number();
			});
		});

		describe('.FLAG_GRANT_READ_URI_PERMISSION', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_GRANT_READ_URI_PERMISSION').which.is.a.Number();
			});
		});

		describe('.FLAG_GRANT_WRITE_URI_PERMISSION', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_GRANT_WRITE_URI_PERMISSION').which.is.a.Number();
			});
		});

		describe('.FLAG_IMMUTABLE', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_IMMUTABLE').which.is.a.Number();
			});
		});

		describe('.FLAG_INSISTENT', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_INSISTENT').which.is.a.Number();
			});
		});

		describe('.FLAG_MUTABLE', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_MUTABLE').which.is.a.Number();
			});
		});

		describe('.FLAG_NO_CLEAR', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_NO_CLEAR').which.is.a.Number();
			});
		});

		describe('.FLAG_NO_CREATE', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_NO_CREATE').which.is.a.Number();
			});
		});

		describe('.FLAG_ONE_SHOT', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_ONE_SHOT').which.is.a.Number();
			});
		});

		describe('.FLAG_ONGOING_EVENT', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_ONGOING_EVENT').which.is.a.Number();
			});
		});

		describe('.FLAG_ONLY_ALERT_ONCE', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_ONLY_ALERT_ONCE').which.is.a.Number();
			});
		});

		describe('.FLAG_RECEIVER_REGISTERED_ONLY', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_RECEIVER_REGISTERED_ONLY').which.is.a.Number();
			});
		});

		describe('.FLAG_SHOW_LIGHTS', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_SHOW_LIGHTS').which.is.a.Number();
			});
		});

		describe('.FLAG_UPDATE_CURRENT', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FLAG_UPDATE_CURRENT').which.is.a.Number();
			});
		});

		describe('.FOREGROUND_SERVICE_TYPE_CAMERA', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FOREGROUND_SERVICE_TYPE_CAMERA').which.is.a.Number();
			});
		});

		describe('.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE').which.is.a.Number();
			});
		});

		describe('.FOREGROUND_SERVICE_TYPE_LOCATION', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FOREGROUND_SERVICE_TYPE_LOCATION').which.is.a.Number();
			});
		});

		describe('.FOREGROUND_SERVICE_TYPE_MANIFEST', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FOREGROUND_SERVICE_TYPE_MANIFEST').which.is.a.Number();
			});
		});

		describe('.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK').which.is.a.Number();
			});
		});

		describe('.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION').which.is.a.Number();
			});
		});

		describe('.FOREGROUND_SERVICE_TYPE_MICROPHONE', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FOREGROUND_SERVICE_TYPE_MICROPHONE').which.is.a.Number();
			});
		});

		describe('.FOREGROUND_SERVICE_TYPE_NONE', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FOREGROUND_SERVICE_TYPE_NONE').which.is.a.Number();
			});
		});

		describe('.FOREGROUND_SERVICE_TYPE_PHONE_CALL', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('FOREGROUND_SERVICE_TYPE_PHONE_CALL').which.is.a.Number();
			});
		});

		describe('.IMPORTANCE_DEFAULT', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('IMPORTANCE_DEFAULT').which.is.a.Number();
			});
		});

		describe('.IMPORTANCE_HIGH', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('IMPORTANCE_HIGH').which.is.a.Number();
			});
		});

		describe('.IMPORTANCE_LOW', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('IMPORTANCE_LOW').which.is.a.Number();
			});
		});

		describe('.IMPORTANCE_MAX', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('IMPORTANCE_MAX').which.is.a.Number();
			});
		});

		describe('.IMPORTANCE_MIN', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('IMPORTANCE_MIN').which.is.a.Number();
			});
		});

		describe('.IMPORTANCE_NONE', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('IMPORTANCE_NONE').which.is.a.Number();
			});
		});

		describe('.IMPORTANCE_UNSPECIFIED', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('IMPORTANCE_UNSPECIFIED').which.is.a.Number();
			});
		});

		describe('.NAVIGATION_MODE_STANDARD', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('NAVIGATION_MODE_STANDARD').which.is.a.Number();
			});
		});

		describe('.NAVIGATION_MODE_TABS', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('NAVIGATION_MODE_TABS').which.is.a.Number();
			});
		});

		describe('.PRIORITY_DEFAULT', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('PRIORITY_DEFAULT').which.is.a.Number();
			});
		});

		describe('.PRIORITY_HIGH', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('PRIORITY_HIGH').which.is.a.Number();
			});
		});

		describe('.PRIORITY_LOW', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('PRIORITY_LOW').which.is.a.Number();
			});
		});

		describe('.PRIORITY_MAX', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('PRIORITY_MAX').which.is.a.Number();
			});
		});

		describe('.PRIORITY_MIN', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('PRIORITY_MIN').which.is.a.Number();
			});
		});

		describe('.R', () => {
			it('is an Object', () => {
				should(Ti.Android).have.a.constant('R').which.is.an.Object();
			});
		});

		describe('.RESULT_CANCELED', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('RESULT_CANCELED').which.is.a.Number();
			});
		});

		describe('.RESULT_FIRST_USER', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('RESULT_FIRST_USER').which.is.a.Number();
			});
		});

		describe('.RESULT_OK', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('RESULT_OK').which.is.a.Number();
			});
		});

		describe('.SCREEN_ORIENTATION_BEHIND', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('SCREEN_ORIENTATION_BEHIND').which.is.a.Number();
			});
		});

		describe('.SCREEN_ORIENTATION_LANDSCAPE', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('SCREEN_ORIENTATION_LANDSCAPE').which.is.a.Number();
			});
		});

		describe('.SCREEN_ORIENTATION_NOSENSOR', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('SCREEN_ORIENTATION_NOSENSOR').which.is.a.Number();
			});
		});

		describe('.SCREEN_ORIENTATION_PORTRAIT', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('SCREEN_ORIENTATION_PORTRAIT').which.is.a.Number();
			});
		});

		describe('.SCREEN_ORIENTATION_SENSOR', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('SCREEN_ORIENTATION_SENSOR').which.is.a.Number();
			});
		});

		describe('.SCREEN_ORIENTATION_UNSPECIFIED', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('SCREEN_ORIENTATION_UNSPECIFIED').which.is.a.Number();
			});
		});

		describe('.SCREEN_ORIENTATION_USER', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('SCREEN_ORIENTATION_USER').which.is.a.Number();
			});
		});

		describe('.SHOW_AS_ACTION_ALWAYS', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('SHOW_AS_ACTION_ALWAYS').which.is.a.Number();
			});
		});

		describe('.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW').which.is.a.Number();
			});
		});

		describe('.SHOW_AS_ACTION_IF_ROOM', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('SHOW_AS_ACTION_IF_ROOM').which.is.a.Number();
			});
		});

		describe('.SHOW_AS_ACTION_NEVER', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('SHOW_AS_ACTION_NEVER').which.is.a.Number();
			});
		});

		describe('.SHOW_AS_ACTION_WITH_TEXT', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('SHOW_AS_ACTION_WITH_TEXT').which.is.a.Number();
			});
		});

		describe('.START_NOT_STICKY', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('START_NOT_STICKY').which.is.a.Number();
			});
		});

		describe('.START_REDELIVER_INTENT', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('START_REDELIVER_INTENT').which.is.a.Number();
			});
		});

		describe('.STREAM_ALARM', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('STREAM_ALARM').which.is.a.Number();
			});
		});

		describe('.STREAM_DEFAULT', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('STREAM_DEFAULT').which.is.a.Number();
			});
		});

		describe('.STREAM_MUSIC', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('STREAM_MUSIC').which.is.a.Number();
			});
		});

		describe('.STREAM_NOTIFICATION', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('STREAM_NOTIFICATION').which.is.a.Number();
			});
		});

		describe('.STREAM_RING', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('STREAM_RING').which.is.a.Number();
			});
		});

		describe('.STREAM_SYSTEM', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('STREAM_SYSTEM').which.is.a.Number();
			});
		});

		describe('.STREAM_VOICE_CALL', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('STREAM_VOICE_CALL').which.is.a.Number();
			});
		});

		describe('.TILE_STATE_ACTIVE', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('TILE_STATE_ACTIVE').which.is.a.Number();
			});
		});

		describe('.TILE_STATE_INACTIVE', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('TILE_STATE_INACTIVE').which.is.a.Number();
			});
		});

		describe('.TILE_STATE_UNAVAILABLE', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('TILE_STATE_UNAVAILABLE').which.is.a.Number();
			});
		});

		describe('.URI_INTENT_SCHEME', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('URI_INTENT_SCHEME').which.is.a.Number();
			});
		});

		describe('.VISIBILITY_PRIVATE', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('VISIBILITY_PRIVATE').which.is.a.Number();
			});
		});

		describe('.VISIBILITY_PUBLIC', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('VISIBILITY_PUBLIC').which.is.a.Number();
			});
		});

		describe('.VISIBILITY_SECRET', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('VISIBILITY_SECRET').which.is.a.Number();
			});
		});

		describe('.WAKE_LOCK_ACQUIRE_CAUSES_WAKEUP', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('WAKE_LOCK_ACQUIRE_CAUSES_WAKEUP').which.is.a.Number();
			});
		});

		describe('.WAKE_LOCK_FULL', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('WAKE_LOCK_FULL').which.is.a.Number();
			});
		});

		describe('.WAKE_LOCK_ON_AFTER_RELEASE', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('WAKE_LOCK_ON_AFTER_RELEASE').which.is.a.Number();
			});
		});

		describe('.WAKE_LOCK_PARTIAL', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('WAKE_LOCK_PARTIAL').which.is.a.Number();
			});
		});

		describe('.WAKE_LOCK_SCREEN_BRIGHT', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('WAKE_LOCK_SCREEN_BRIGHT').which.is.a.Number();
			});
		});

		describe('.WAKE_LOCK_SCREEN_DIM', () => {
			it('is a Number', () => {
				should(Ti.Android).have.a.constant('WAKE_LOCK_SCREEN_DIM').which.is.a.Number();
			});
		});

	});

	it('newintent', function (finish) {
		this.timeout(5000);
		const launchIntent = Ti.App.Android.launchIntent;
		const newIntent = Ti.Android.createIntent({
			action: Ti.Android.ACTION_VIEW,
			data: 'https://www.appcelerator.com',
			packageName: launchIntent.packageName,
			className: launchIntent.className
		});
		newIntent.putExtra('MyBoolean', true);
		newIntent.putExtra('MyDouble', 123.456);
		newIntent.putExtra('MyString', 'Hello World');
		Ti.Android.rootActivity.addEventListener('newintent', function listener(e) {
			try {
				Ti.Android.rootActivity.removeEventListener('newintent', listener);
				function validateIntent(intent) {
					should(intent).be.a.Object();
					should(intent.action).eql(newIntent.action);
					should(intent.data).eql(newIntent.data);
					should(intent.packageName).eql(newIntent.packageName);
					should(intent.className).eql(newIntent.className);
					should(intent.hasExtra('MyBoolean')).be.true();
					should(intent.getBooleanExtra('MyBoolean', false)).be.true();
					should(intent.hasExtra('MyDouble')).be.true();
					should(intent.getDoubleExtra('MyDouble', 0)).eql(123.456);
					should(intent.hasExtra('MyString')).be.true();
					should(intent.getStringExtra('MyString')).eql('Hello World');
				}
				Ti.API.info('- Validating: e.intent');
				validateIntent(e.intent);
				Ti.API.info('- Validating: Ti.Android.rootActivity.intent');
				validateIntent(Ti.Android.rootActivity.intent);
				finish();
			} catch (err) {
				finish(err);
			}
		});
		Ti.Android.currentActivity.startActivity(newIntent);
	});

	it('activity callbacks', function (finish) {
		let childWindow = null;
		let wasOnCreateCalled = false;
		let wasOnRestartCalled = false;
		let wasOnStartCalled = false;
		let wasOnResumeCalled = false;
		let wasOnPauseCalled = false;
		let wasOnStopCalled = false;
		let wasOnDestroyCalled = false;

		this.timeout(5000);

		const win = Ti.UI.createWindow();
		win.activity.onCreate = () => {
			wasOnCreateCalled = true;
			win.activity.onCreate = null;
		};
		win.activity.onRestart = () => {
			wasOnRestartCalled = true;
			win.activity.onRestart = null;
			setTimeout(() => {
				// Now that we've returned to this activity, test destroy behavior.
				win.close();
			}, 50);
		};
		win.activity.onStart = () => {
			wasOnStartCalled = true;
			win.activity.onStart = null;
		};
		win.activity.onResume = () => {
			wasOnResumeCalled = true;
			win.activity.onResume = null;
		};
		win.activity.onPause = () => {
			wasOnPauseCalled = true;
			win.activity.onPause = null;
		};
		win.activity.onStop = () => {
			wasOnStopCalled = true;
			win.activity.onStop = null;
			if (childWindow) {
				// Close child activity to invoke parent's onRestart() callback.
				childWindow.close();
			}
		};
		win.activity.onDestroy = () => {
			wasOnDestroyCalled = true;
			win.activity.onDestroy = null;
		};
		win.addEventListener('open', () => {
			// Open child activity to invoke parent's onPause() and onStop() callbacks.
			childWindow = Ti.UI.createWindow();
			childWindow.open();
		});
		win.addEventListener('close', () => {
			try {
				should(wasOnCreateCalled).be.true();
				should(wasOnRestartCalled).be.true();
				should(wasOnStartCalled).be.true();
				should(wasOnResumeCalled).be.true();
				should(wasOnPauseCalled).be.true();
				should(wasOnStopCalled).be.true();
				should(wasOnDestroyCalled).be.true();
				finish();
			} catch (err) {
				finish(err);
			}
		});
		win.open();
	});
});
