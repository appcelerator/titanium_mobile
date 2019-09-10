/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-Present by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.android;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import android.content.pm.PackageManager;
import android.os.Build;
import android.service.quicksettings.Tile;
import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.KrollPromise;
import org.appcelerator.kroll.KrollRuntime;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.ActivityProxy;
import org.appcelerator.titanium.proxy.IntentProxy;
import org.appcelerator.titanium.proxy.RProxy;
import org.appcelerator.titanium.proxy.ServiceProxy;
import org.appcelerator.titanium.util.TiConvert;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.view.MenuItem;
import android.os.PowerManager;

@SuppressWarnings("deprecation")
@Kroll.module
public class AndroidModule extends KrollModule
{
	private static final String TAG = "TiAndroid";

	@Kroll.constant
	public static final String ACTION_AIRPLANE_MODE_CHANGED = Intent.ACTION_AIRPLANE_MODE_CHANGED;
	@Kroll.constant
	public static final String ACTION_ALL_APPS = Intent.ACTION_ALL_APPS;
	@Kroll.constant
	public static final String ACTION_ANSWER = Intent.ACTION_ANSWER;
	@Kroll.constant
	public static final String ACTION_ATTACH_DATA = Intent.ACTION_ATTACH_DATA;
	@Kroll.constant
	public static final String ACTION_BATTERY_CHANGED = Intent.ACTION_BATTERY_CHANGED;
	@Kroll.constant
	public static final String ACTION_BATTERY_LOW = Intent.ACTION_BATTERY_LOW;
	@Kroll.constant
	public static final String ACTION_BATTERY_OKAY = Intent.ACTION_BATTERY_OKAY;
	@Kroll.constant
	public static final String ACTION_BOOT_COMPLETED = Intent.ACTION_BOOT_COMPLETED;
	@Kroll.constant
	public static final String ACTION_BUG_REPORT = Intent.ACTION_BUG_REPORT;
	@Kroll.constant
	public static final String ACTION_CALL = Intent.ACTION_CALL;
	@Kroll.constant
	public static final String ACTION_CALL_BUTTON = Intent.ACTION_CALL_BUTTON;
	@Kroll.constant
	public static final String ACTION_CAMERA_BUTTON = Intent.ACTION_CAMERA_BUTTON;
	@Kroll.constant
	public static final String ACTION_CHOOSER = Intent.ACTION_CHOOSER;
	@Kroll.constant
	public static final String ACTION_CLOSE_SYSTEM_DIALOGS = Intent.ACTION_CLOSE_SYSTEM_DIALOGS;
	@Kroll.constant
	public static final String ACTION_CONFIGURATION_CHANGED = Intent.ACTION_CONFIGURATION_CHANGED;
	@Kroll.constant
	public static final String ACTION_CREATE_SHORTCUT = Intent.ACTION_CREATE_SHORTCUT;
	@Kroll.constant
	public static final String ACTION_DATE_CHANGED = Intent.ACTION_DATE_CHANGED;
	@Kroll.constant
	public static final String ACTION_DEFAULT = Intent.ACTION_DEFAULT;
	@Kroll.constant
	public static final String ACTION_DELETE = Intent.ACTION_DELETE;
	@Kroll.constant
	public static final String ACTION_DEVICE_STORAGE_LOW = Intent.ACTION_DEVICE_STORAGE_LOW;
	@Kroll.constant
	public static final String ACTION_DIAL = Intent.ACTION_DIAL;
	@Kroll.constant
	public static final String ACTION_EDIT = Intent.ACTION_EDIT;
	@Kroll.constant
	public static final String ACTION_GET_CONTENT = Intent.ACTION_GET_CONTENT;
	@Kroll.constant
	public static final String ACTION_GTALK_SERVICE_CONNECTED = Intent.ACTION_GTALK_SERVICE_CONNECTED;
	@Kroll.constant
	public static final String ACTION_GTALK_SERVICE_DISCONNECTED = Intent.ACTION_GTALK_SERVICE_DISCONNECTED;
	@Kroll.constant
	public static final String ACTION_HEADSET_PLUG = Intent.ACTION_HEADSET_PLUG;
	@Kroll.constant
	public static final String ACTION_INPUT_METHOD_CHANGED = Intent.ACTION_INPUT_METHOD_CHANGED;
	@Kroll.constant
	public static final String ACTION_INSERT = Intent.ACTION_INSERT;
	@Kroll.constant
	public static final String ACTION_INSERT_OR_EDIT = Intent.ACTION_INSERT_OR_EDIT;
	@Kroll.constant
	public static final String ACTION_MAIN = Intent.ACTION_MAIN;
	@Kroll.constant
	public static final String ACTION_MANAGE_PACKAGE_STORAGE = Intent.ACTION_MANAGE_PACKAGE_STORAGE;
	@Kroll.constant
	public static final String ACTION_MEDIA_BAD_REMOVAL = Intent.ACTION_MEDIA_BAD_REMOVAL;
	@Kroll.constant
	public static final String ACTION_MEDIA_BUTTON = Intent.ACTION_MEDIA_BUTTON;
	@Kroll.constant
	public static final String ACTION_MEDIA_CHECKING = Intent.ACTION_MEDIA_CHECKING;
	@Kroll.constant
	public static final String ACTION_MEDIA_EJECT = Intent.ACTION_MEDIA_EJECT;
	@Kroll.constant
	public static final String ACTION_MEDIA_MOUNTED = Intent.ACTION_MEDIA_MOUNTED;
	@Kroll.constant
	public static final String ACTION_MEDIA_NOFS = Intent.ACTION_MEDIA_NOFS;
	@Kroll.constant
	public static final String ACTION_MEDIA_REMOVED = Intent.ACTION_MEDIA_REMOVED;
	@Kroll.constant
	public static final String ACTION_MEDIA_SCANNER_FINISHED = Intent.ACTION_MEDIA_SCANNER_FINISHED;
	@Kroll.constant
	public static final String ACTION_MEDIA_SCANNER_SCAN_FILE = Intent.ACTION_MEDIA_SCANNER_SCAN_FILE;
	@Kroll.constant
	public static final String ACTION_MEDIA_SCANNER_STARTED = Intent.ACTION_MEDIA_SCANNER_STARTED;
	@Kroll.constant
	public static final String ACTION_MEDIA_SHARED = Intent.ACTION_MEDIA_SHARED;
	@Kroll.constant
	public static final String ACTION_MEDIA_UNMOUNTABLE = Intent.ACTION_MEDIA_UNMOUNTABLE;
	@Kroll.constant
	public static final String ACTION_MEDIA_UNMOUNTED = Intent.ACTION_MEDIA_UNMOUNTED;
	@Kroll.constant
	public static final String ACTION_NEW_OUTGOING_CALL = Intent.ACTION_NEW_OUTGOING_CALL;
	@Kroll.constant
	public static final String ACTION_PACKAGE_ADDED = Intent.ACTION_PACKAGE_ADDED;
	@Kroll.constant
	public static final String ACTION_PACKAGE_CHANGED = Intent.ACTION_PACKAGE_CHANGED;
	@Kroll.constant
	public static final String ACTION_PACKAGE_DATA_CLEARED = Intent.ACTION_PACKAGE_DATA_CLEARED;
	@Kroll.constant
	public static final String ACTION_PACKAGE_INSTALL = Intent.ACTION_PACKAGE_INSTALL;
	@Kroll.constant
	public static final String ACTION_PACKAGE_REMOVED = Intent.ACTION_PACKAGE_REMOVED;
	@Kroll.constant
	public static final String ACTION_PACKAGE_REPLACED = Intent.ACTION_PACKAGE_REPLACED;
	@Kroll.constant
	public static final String ACTION_PACKAGE_RESTARTED = Intent.ACTION_PACKAGE_RESTARTED;
	@Kroll.constant
	public static final String ACTION_PICK = Intent.ACTION_PICK;
	@Kroll.constant
	public static final String ACTION_PICK_ACTIVITY = Intent.ACTION_PICK_ACTIVITY;
	@Kroll.constant
	public static final String ACTION_POWER_CONNECTED = Intent.ACTION_POWER_CONNECTED;
	@Kroll.constant
	public static final String ACTION_POWER_DISCONNECTED = Intent.ACTION_POWER_DISCONNECTED;
	@Kroll.constant
	public static final String ACTION_POWER_USAGE_SUMMARY = Intent.ACTION_POWER_USAGE_SUMMARY;
	@Kroll.constant
	public static final String ACTION_PROVIDER_CHANGED = Intent.ACTION_PROVIDER_CHANGED;
	@Kroll.constant
	public static final String ACTION_REBOOT = Intent.ACTION_REBOOT;
	@Kroll.constant
	public static final String ACTION_RUN = Intent.ACTION_RUN;
	@Kroll.constant
	public static final String ACTION_SCREEN_OFF = Intent.ACTION_SCREEN_OFF;
	@Kroll.constant
	public static final String ACTION_SCREEN_ON = Intent.ACTION_SCREEN_ON;
	@Kroll.constant
	public static final String ACTION_SEARCH = Intent.ACTION_SEARCH;
	@Kroll.constant
	public static final String ACTION_SEARCH_LONG_PRESS = Intent.ACTION_SEARCH_LONG_PRESS;
	@Kroll.constant
	public static final String ACTION_SEND = Intent.ACTION_SEND;
	@Kroll.constant
	public static final String ACTION_SENDTO = Intent.ACTION_SENDTO;
	@Kroll.constant
	public static final String ACTION_SEND_MULTIPLE = Intent.ACTION_SEND_MULTIPLE;
	@Kroll.constant
	public static final String ACTION_SET_WALLPAPER = Intent.ACTION_SET_WALLPAPER;
	@Kroll.constant
	public static final String ACTION_SHUTDOWN = Intent.ACTION_SHUTDOWN;
	@Kroll.constant
	public static final String ACTION_SYNC = Intent.ACTION_SYNC;
	@Kroll.constant
	public static final String ACTION_SYSTEM_TUTORIAL = Intent.ACTION_SYSTEM_TUTORIAL;
	@Kroll.constant
	public static final String ACTION_TIME_CHANGED = Intent.ACTION_TIME_CHANGED;
	@Kroll.constant
	public static final String ACTION_TIME_TICK = Intent.ACTION_TIME_TICK;
	@Kroll.constant
	public static final String ACTION_UID_REMOVED = Intent.ACTION_UID_REMOVED;
	@Kroll.constant
	public static final String ACTION_UMS_CONNECTED = Intent.ACTION_UMS_CONNECTED;
	@Kroll.constant
	public static final String ACTION_UMS_DISCONNECTED = Intent.ACTION_UMS_DISCONNECTED;
	@Kroll.constant
	public static final String ACTION_USER_PRESENT = Intent.ACTION_USER_PRESENT;
	@Kroll.constant
	public static final String ACTION_VIEW = Intent.ACTION_VIEW;
	@Kroll.constant
	public static final String ACTION_VOICE_COMMAND = Intent.ACTION_VOICE_COMMAND;
	@Kroll.constant
	public static final String ACTION_WALLPAPER_CHANGED = Intent.ACTION_WALLPAPER_CHANGED;
	@Kroll.constant
	public static final String ACTION_WEB_SEARCH = Intent.ACTION_WEB_SEARCH;

	@Kroll.constant
	public static final String CATEGORY_ALTERNATIVE = Intent.CATEGORY_ALTERNATIVE;
	@Kroll.constant
	public static final String CATEGORY_BROWSABLE = Intent.CATEGORY_BROWSABLE;
	@Kroll.constant
	public static final String CATEGORY_DEFAULT = Intent.CATEGORY_DEFAULT;
	@Kroll.constant
	public static final String CATEGORY_DEVELOPMENT_PREFERENCE = Intent.CATEGORY_DEVELOPMENT_PREFERENCE;
	@Kroll.constant
	public static final String CATEGORY_EMBED = Intent.CATEGORY_EMBED;
	@Kroll.constant
	public static final String CATEGORY_FRAMEWORK_INSTRUMENTATION_TEST = Intent.CATEGORY_FRAMEWORK_INSTRUMENTATION_TEST;
	@Kroll.constant
	public static final String CATEGORY_HOME = Intent.CATEGORY_HOME;
	@Kroll.constant
	public static final String CATEGORY_INFO = Intent.CATEGORY_INFO;
	@Kroll.constant
	public static final String CATEGORY_LAUNCHER = Intent.CATEGORY_LAUNCHER;
	@Kroll.constant
	public static final String CATEGORY_MONKEY = Intent.CATEGORY_MONKEY;
	@Kroll.constant
	public static final String CATEGORY_OPENABLE = Intent.CATEGORY_OPENABLE;
	@Kroll.constant
	public static final String CATEGORY_PREFERENCE = Intent.CATEGORY_PREFERENCE;
	@Kroll.constant
	public static final String CATEGORY_SAMPLE_CODE = Intent.CATEGORY_SAMPLE_CODE;
	@Kroll.constant
	public static final String CATEGORY_SELECTED_ALTERNATIVE = Intent.CATEGORY_SELECTED_ALTERNATIVE;
	@Kroll.constant
	public static final String CATEGORY_TAB = Intent.CATEGORY_TAB;
	@Kroll.constant
	public static final String CATEGORY_TEST = Intent.CATEGORY_TEST;
	@Kroll.constant
	public static final String CATEGORY_UNIT_TEST = Intent.CATEGORY_UNIT_TEST;

	@Kroll.constant
	public static final String EXTRA_ALARM_COUNT = Intent.EXTRA_ALARM_COUNT;
	@Kroll.constant
	public static final String EXTRA_BCC = Intent.EXTRA_BCC;
	@Kroll.constant
	public static final String EXTRA_CC = Intent.EXTRA_CC;
	@Kroll.constant
	public static final String EXTRA_DATA_REMOVED = Intent.EXTRA_DATA_REMOVED;
	@Kroll.constant
	public static final String EXTRA_DONT_KILL_APP = Intent.EXTRA_DONT_KILL_APP;
	@Kroll.constant
	public static final String EXTRA_EMAIL = Intent.EXTRA_EMAIL;
	@Kroll.constant
	public static final String EXTRA_INTENT = Intent.EXTRA_INTENT;
	@Kroll.constant
	public static final String EXTRA_KEY_EVENT = Intent.EXTRA_KEY_EVENT;
	@Kroll.constant
	public static final String EXTRA_PHONE_NUMBER = Intent.EXTRA_PHONE_NUMBER;
	@Kroll.constant
	public static final String EXTRA_REPLACING = Intent.EXTRA_REPLACING;
	@Kroll.constant
	public static final String EXTRA_SHORTCUT_ICON = Intent.EXTRA_SHORTCUT_ICON;
	@Kroll.constant
	public static final String EXTRA_SHORTCUT_ICON_RESOURCE = Intent.EXTRA_SHORTCUT_ICON_RESOURCE;
	@Kroll.constant
	public static final String EXTRA_SHORTCUT_INTENT = Intent.EXTRA_SHORTCUT_INTENT;
	@Kroll.constant
	public static final String EXTRA_SHORTCUT_NAME = Intent.EXTRA_SHORTCUT_NAME;
	@Kroll.constant
	public static final String EXTRA_STREAM = Intent.EXTRA_STREAM;
	@Kroll.constant
	public static final String EXTRA_SUBJECT = Intent.EXTRA_SUBJECT;
	@Kroll.constant
	public static final String EXTRA_TEMPLATE = Intent.EXTRA_TEMPLATE;
	@Kroll.constant
	public static final String EXTRA_TEXT = Intent.EXTRA_TEXT;
	@Kroll.constant
	public static final String EXTRA_TITLE = Intent.EXTRA_TITLE;
	@Kroll.constant
	public static final String EXTRA_UID = Intent.EXTRA_UID;

	@Kroll.constant
	public static final int FILL_IN_ACTION = Intent.FILL_IN_ACTION;
	@Kroll.constant
	public static final int FILL_IN_CATEGORIES = Intent.FILL_IN_CATEGORIES;
	@Kroll.constant
	public static final int FILL_IN_COMPONENT = Intent.FILL_IN_COMPONENT;
	@Kroll.constant
	public static final int FILL_IN_DATA = Intent.FILL_IN_DATA;
	@Kroll.constant
	public static final int FILL_IN_PACKAGE = Intent.FILL_IN_PACKAGE;

	@Kroll.constant
	public static final int FLAG_ACTIVITY_BROUGHT_TO_FRONT = Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT;
	@Kroll.constant
	public static final int FLAG_ACTIVITY_CLEAR_TOP = Intent.FLAG_ACTIVITY_CLEAR_TOP;
	//API 11 @Kroll.constant public static final int FLAG_ACTIVITY_CLEAR_TASK = Intent.FLAG_ACTIVITY_CLEAR_TASK;
	@Kroll.constant
	public static final int FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET = Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET;
	@Kroll.constant
	public static final int FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS = Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
	@Kroll.constant
	public static final int FLAG_ACTIVITY_FORWARD_RESULT = Intent.FLAG_ACTIVITY_FORWARD_RESULT;
	@Kroll.constant
	public static final int FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY = Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY;
	@Kroll.constant
	public static final int FLAG_ACTIVITY_MULTIPLE_TASK = Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
	@Kroll.constant
	public static final int FLAG_ACTIVITY_NEW_TASK = Intent.FLAG_ACTIVITY_NEW_TASK;
	@Kroll.constant
	public static final int FLAG_ACTIVITY_NO_ANIMATION = Intent.FLAG_ACTIVITY_NO_ANIMATION;
	@Kroll.constant
	public static final int FLAG_ACTIVITY_NO_HISTORY = Intent.FLAG_ACTIVITY_NO_HISTORY;
	@Kroll.constant
	public static final int FLAG_ACTIVITY_NO_USER_ACTION = Intent.FLAG_ACTIVITY_NO_USER_ACTION;
	@Kroll.constant
	public static final int FLAG_ACTIVITY_PREVIOUS_IS_TOP = Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP;
	@Kroll.constant
	public static final int FLAG_ACTIVITY_REORDER_TO_FRONT = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT;
	@Kroll.constant
	public static final int FLAG_ACTIVITY_RESET_TASK_IF_NEEDED = Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED;
	@Kroll.constant
	public static final int FLAG_ACTIVITY_SINGLE_TOP = Intent.FLAG_ACTIVITY_SINGLE_TOP;
	//API 11 @Kroll.constant public static final int FLAG_ACTIVITY_TASK_ON_HOME = Intent.FLAG_ACTIVITY_TASK_ON_HOME;
	@Kroll.constant
	public static final int FLAG_DEBUG_LOG_RESOLUTION = Intent.FLAG_DEBUG_LOG_RESOLUTION;
	@Kroll.constant
	public static final int FLAG_FROM_BACKGROUND = Intent.FLAG_FROM_BACKGROUND;
	@Kroll.constant
	public static final int FLAG_GRANT_READ_URI_PERMISSION = Intent.FLAG_GRANT_READ_URI_PERMISSION;
	@Kroll.constant
	public static final int FLAG_GRANT_WRITE_URI_PERMISSION = Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
	@Kroll.constant
	public static final int FLAG_RECEIVER_REGISTERED_ONLY = Intent.FLAG_RECEIVER_REGISTERED_ONLY;

	@Kroll.constant
	public static final int URI_INTENT_SCHEME = Intent.URI_INTENT_SCHEME;

	@Kroll.constant
	public static final int PENDING_INTENT_FOR_ACTIVITY = IntentProxy.TYPE_ACTIVITY;
	@Kroll.constant
	public static final int PENDING_INTENT_FOR_SERVICE = IntentProxy.TYPE_SERVICE;
	@Kroll.constant
	public static final int PENDING_INTENT_FOR_BROADCAST = IntentProxy.TYPE_BROADCAST;
	@Kroll.constant
	public static final int PENDING_INTENT_MAX_VALUE = PENDING_INTENT_FOR_BROADCAST;
	@Kroll.constant
	public static final int FLAG_CANCEL_CURRENT = PendingIntent.FLAG_CANCEL_CURRENT;
	@Kroll.constant
	public static final int FLAG_NO_CREATE = PendingIntent.FLAG_NO_CREATE;
	@Kroll.constant
	public static final int FLAG_ONE_SHOT = PendingIntent.FLAG_ONE_SHOT;
	@Kroll.constant
	public static final int FLAG_UPDATE_CURRENT = PendingIntent.FLAG_UPDATE_CURRENT;

	@Kroll.constant
	public static final int RESULT_OK = Activity.RESULT_OK;
	@Kroll.constant
	public static final int RESULT_CANCELED = Activity.RESULT_CANCELED;
	@Kroll.constant
	public static final int RESULT_FIRST_USER = Activity.RESULT_FIRST_USER;

	@Kroll.constant
	public static final int SCREEN_ORIENTATION_BEHIND = ActivityInfo.SCREEN_ORIENTATION_BEHIND;
	@Kroll.constant
	public static final int SCREEN_ORIENTATION_LANDSCAPE = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
	@Kroll.constant
	public static final int SCREEN_ORIENTATION_NOSENSOR = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
	@Kroll.constant
	public static final int SCREEN_ORIENTATION_PORTRAIT = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
	@Kroll.constant
	public static final int SCREEN_ORIENTATION_SENSOR = ActivityInfo.SCREEN_ORIENTATION_SENSOR;
	@Kroll.constant
	public static final int SCREEN_ORIENTATION_UNSPECIFIED = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
	@Kroll.constant
	public static final int SCREEN_ORIENTATION_USER = ActivityInfo.SCREEN_ORIENTATION_USER;

	@Kroll.constant
	public static final int DEFAULT_ALL = Notification.DEFAULT_ALL;
	@Kroll.constant
	public static final int DEFAULT_LIGHTS = Notification.DEFAULT_LIGHTS;
	@Kroll.constant
	public static final int DEFAULT_SOUND = Notification.DEFAULT_SOUND;
	@Kroll.constant
	public static final int DEFAULT_VIBRATE = Notification.DEFAULT_VIBRATE;
	@Kroll.constant
	public static final int FLAG_AUTO_CANCEL = Notification.FLAG_AUTO_CANCEL;
	@Kroll.constant
	public static final int FLAG_INSISTENT = Notification.FLAG_INSISTENT;
	@Kroll.constant
	public static final int FLAG_NO_CLEAR = Notification.FLAG_NO_CLEAR;
	@Kroll.constant
	public static final int FLAG_ONGOING_EVENT = Notification.FLAG_ONGOING_EVENT;
	@Kroll.constant
	public static final int FLAG_ONLY_ALERT_ONCE = Notification.FLAG_ONLY_ALERT_ONCE;
	@Kroll.constant
	public static final int FLAG_SHOW_LIGHTS = Notification.FLAG_SHOW_LIGHTS;
	@Kroll.constant
	public static final int STREAM_DEFAULT = Notification.STREAM_DEFAULT;
	@Kroll.constant
	public static final int VISIBILITY_PRIVATE = NotificationCompat.VISIBILITY_PRIVATE;
	@Kroll.constant
	public static final int VISIBILITY_PUBLIC = NotificationCompat.VISIBILITY_PUBLIC;
	@Kroll.constant
	public static final int VISIBILITY_SECRET = NotificationCompat.VISIBILITY_SECRET;
	@Kroll.constant
	public static final int PRIORITY_HIGH = NotificationCompat.PRIORITY_HIGH;
	@Kroll.constant
	public static final int PRIORITY_MAX = NotificationCompat.PRIORITY_MAX;
	@Kroll.constant
	public static final int PRIORITY_DEFAULT = NotificationCompat.PRIORITY_DEFAULT;
	@Kroll.constant
	public static final int PRIORITY_LOW = NotificationCompat.PRIORITY_LOW;
	@Kroll.constant
	public static final int PRIORITY_MIN = NotificationCompat.PRIORITY_MIN;
	@Kroll.constant
	public static final String CATEGORY_ALARM = NotificationCompat.CATEGORY_ALARM;
	@Kroll.constant
	public static final String CATEGORY_CALL = NotificationCompat.CATEGORY_CALL;
	@Kroll.constant
	public static final String CATEGORY_EMAIL = NotificationCompat.CATEGORY_EMAIL;
	@Kroll.constant
	public static final String CATEGORY_ERROR = NotificationCompat.CATEGORY_ERROR;
	@Kroll.constant
	public static final String CATEGORY_EVENT = NotificationCompat.CATEGORY_EVENT;
	@Kroll.constant
	public static final String CATEGORY_MESSAGE = NotificationCompat.CATEGORY_MESSAGE;
	@Kroll.constant
	public static final String CATEGORY_PROGRESS = NotificationCompat.CATEGORY_PROGRESS;
	@Kroll.constant
	public static final String CATEGORY_PROMO = NotificationCompat.CATEGORY_PROMO;
	@Kroll.constant
	public static final String CATEGORY_RECOMMENDATION = NotificationCompat.CATEGORY_RECOMMENDATION;
	@Kroll.constant
	public static final String CATEGORY_SERVICE = NotificationCompat.CATEGORY_SERVICE;
	@Kroll.constant
	public static final String CATEGORY_SOCIAL = NotificationCompat.CATEGORY_SOCIAL;
	@Kroll.constant
	public static final String CATEGORY_STATUS = NotificationCompat.CATEGORY_STATUS;
	@Kroll.constant
	public static final String CATEGORY_TRANSPORT = NotificationCompat.CATEGORY_TRANSPORT;

	@Kroll.constant
	public static final int START_NOT_STICKY = Service.START_NOT_STICKY;
	@Kroll.constant
	public static final int START_REDELIVER_INTENT = Service.START_REDELIVER_INTENT;

	@Kroll.constant
	public static final int STREAM_ALARM = AudioManager.STREAM_ALARM;
	@Kroll.constant
	public static final int STREAM_MUSIC = AudioManager.STREAM_MUSIC;
	@Kroll.constant
	public static final int STREAM_NOTIFICATION = AudioManager.STREAM_NOTIFICATION;
	@Kroll.constant
	public static final int STREAM_RING = AudioManager.STREAM_RING;
	@Kroll.constant
	public static final int STREAM_SYSTEM = AudioManager.STREAM_SYSTEM;
	@Kroll.constant
	public static final int STREAM_VOICE_CALL = AudioManager.STREAM_VOICE_CALL;

	@Kroll.constant
	public static final int SHOW_AS_ACTION_ALWAYS = MenuItem.SHOW_AS_ACTION_ALWAYS;
	@Kroll.constant
	public static final int SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW = MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW;
	@Kroll.constant
	public static final int SHOW_AS_ACTION_IF_ROOM = MenuItem.SHOW_AS_ACTION_IF_ROOM;
	@Kroll.constant
	public static final int SHOW_AS_ACTION_NEVER = MenuItem.SHOW_AS_ACTION_NEVER;
	@Kroll.constant
	public static final int SHOW_AS_ACTION_WITH_TEXT = MenuItem.SHOW_AS_ACTION_WITH_TEXT;

	@Kroll.constant
	public static final int NAVIGATION_MODE_LIST = ActionBar.NAVIGATION_MODE_LIST;
	@Kroll.constant
	public static final int NAVIGATION_MODE_STANDARD = ActionBar.NAVIGATION_MODE_STANDARD;
	@Kroll.constant
	public static final int NAVIGATION_MODE_TABS = ActionBar.NAVIGATION_MODE_TABS;

	@Kroll.constant
	public static final int TILE_STATE_UNAVAILABLE = Tile.STATE_UNAVAILABLE;
	@Kroll.constant
	public static final int TILE_STATE_INACTIVE = Tile.STATE_INACTIVE;
	@Kroll.constant
	public static final int TILE_STATE_ACTIVE = Tile.STATE_ACTIVE;

	@Kroll.constant
	public static final int WAKE_LOCK_PARTIAL = PowerManager.PARTIAL_WAKE_LOCK;
	@Kroll.constant
	public static final int WAKE_LOCK_FULL = PowerManager.FULL_WAKE_LOCK;
	@Kroll.constant
	public static final int WAKE_LOCK_SCREEN_DIM = PowerManager.SCREEN_DIM_WAKE_LOCK;
	@Kroll.constant
	public static final int WAKE_LOCK_SCREEN_BRIGHT = PowerManager.SCREEN_BRIGHT_WAKE_LOCK;
	@Kroll.constant
	public static final int WAKE_LOCK_ACQUIRE_CAUSES_WAKEUP = PowerManager.ACQUIRE_CAUSES_WAKEUP;
	@Kroll.constant
	public static final int WAKE_LOCK_ON_AFTER_RELEASE = PowerManager.ON_AFTER_RELEASE;

	@Kroll.constant
	public static final int IMPORTANCE_DEFAULT = NotificationManagerCompat.IMPORTANCE_DEFAULT;
	@Kroll.constant
	public static final int IMPORTANCE_HIGH = NotificationManagerCompat.IMPORTANCE_HIGH;
	@Kroll.constant
	public static final int IMPORTANCE_LOW = NotificationManagerCompat.IMPORTANCE_LOW;
	@Kroll.constant
	public static final int IMPORTANCE_MAX = NotificationManagerCompat.IMPORTANCE_MAX;
	@Kroll.constant
	public static final int IMPORTANCE_MIN = NotificationManagerCompat.IMPORTANCE_MIN;
	@Kroll.constant
	public static final int IMPORTANCE_NONE = NotificationManagerCompat.IMPORTANCE_NONE;
	@Kroll.constant
	public static final int IMPORTANCE_UNSPECIFIED = NotificationManagerCompat.IMPORTANCE_UNSPECIFIED;

	protected RProxy r;
	private LinkedList<BroadcastReceiverProxy> registeredBroadcastReceiverProxyList = new LinkedList<>();

	private static final int REQUEST_CODE = 99;

	public AndroidModule()
	{
		super();

		// Set up a listener to be invoked when the JavaScript runtime is about to be terminated/disposed.
		KrollRuntime.addOnDisposingListener(new KrollRuntime.OnDisposingListener() {
			@Override
			public void onDisposing(KrollRuntime runtime)
			{
				// Remove this listener from the runtime's static collection.
				KrollRuntime.removeOnDisposingListener(this);

				// Unregister all currently registerd broadcast receviers.
				// They can no longer be handled by the terminating JavaScript runtime.
				while (registeredBroadcastReceiverProxyList.isEmpty() == false) {
					unregisterBroadcastReceiver(registeredBroadcastReceiverProxyList.pollFirst());
				}
			}
		});
	}

	@Kroll.method
	public IntentProxy createIntent(Object[] args)
	{
		IntentProxy intent = new IntentProxy();
		intent.handleCreationArgs(this, args);
		return intent;
	}

	@Kroll.method
	public IntentProxy createServiceIntent(Object[] args)
	{
		IntentProxy intent = new IntentProxy();
		intent.setInternalType(IntentProxy.TYPE_SERVICE);
		intent.handleCreationArgs(this, args);
		Object startMode = intent.getProperty(TiC.INTENT_PROPERTY_START_MODE);
		if (startMode != null) {
			intent.putExtra(TiC.INTENT_PROPERTY_START_MODE, TiConvert.toInt(startMode));
		}
		return intent;
	}

	@Kroll.method
	public IntentProxy createBroadcastIntent(Object[] args)
	{
		IntentProxy intent = new IntentProxy();
		intent.setInternalType(IntentProxy.TYPE_BROADCAST);
		intent.handleCreationArgs(this, args);
		return intent;
	}

	@Kroll.method
	public IntentProxy createIntentChooser(IntentProxy target, String title)
	{
		return new IntentProxy(Intent.createChooser(target.getIntent(), title));
	}

	@Kroll.getProperty(name = "R")
	public RProxy getR()
	{
		if (r == null) {
			r = new RProxy(RProxy.RESOURCE_TYPE_ANDROID);
		}
		return r;
	}

	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public ActivityProxy getCurrentActivity()
	// clang-format on
	{
		Activity activity = TiApplication.getAppCurrentActivity();
		if (activity instanceof TiBaseActivity) {
			return ((TiBaseActivity) activity).getActivityProxy();
		}
		return null;
	}

	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public ActivityProxy getRootActivity()
	// clang-format on
	{
		TiBaseActivity activity = TiApplication.getInstance().getRootActivity();
		if (activity != null) {
			return activity.getActivityProxy();
		}
		return null;
	}

	@Kroll.method
	public void startService(IntentProxy intentProxy)
	{
		try {
			TiApplication.getInstance().startService(intentProxy.getIntent());
		} catch (Exception ex) {
			String message = ex.getMessage();
			if (message == null) {
				message = "startService() failed. Reason unknown.";
			}
			Log.w(TAG, message);
		}
	}

	@Kroll.method
	public void stopService(IntentProxy intentProxy)
	{
		TiApplication.getInstance().stopService(intentProxy.getIntent());
	}

	@Kroll.method
	public boolean hasPermission(Object permissionObject)
	{
		if (Build.VERSION.SDK_INT >= 23) {
			ArrayList<String> permissions = new ArrayList<String>();
			if (permissionObject instanceof String) {
				permissions.add((String) permissionObject);
			} else if (permissionObject instanceof Object[]) {
				for (Object permission : (Object[]) permissionObject) {
					if (permission instanceof String) {
						permissions.add((String) permission);
					}
				}
			}
			Activity currentActivity = TiApplication.getInstance().getCurrentActivity();
			for (String permission : permissions) {
				if (currentActivity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
					return false;
				}
			}
		}
		return true;
	}

	@Kroll.method
	public void requestPermissions(Object permissionObject,
								   @Kroll.argument(optional = true) KrollFunction permissionCallback,
								   KrollPromise promise)
	{
		if (Build.VERSION.SDK_INT >= 23) {
			ArrayList<String> permissions = new ArrayList<String>();
			if (permissionObject instanceof String) {
				permissions.add((String) permissionObject);
			} else if (permissionObject instanceof Object[]) {
				for (Object permission : (Object[]) permissionObject) {
					if (permission instanceof String) {
						permissions.add((String) permission);
					}
				}
			}
			Activity currentActivity = TiApplication.getInstance().getCurrentActivity();
			ArrayList<String> filteredPermissions = new ArrayList<String>();
			for (String permission : permissions) {
				if (currentActivity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
					continue;
				}
				filteredPermissions.add(permission);
			}
			if (filteredPermissions.size() > 0) {
				TiBaseActivity.registerPermissionRequestCallback(REQUEST_CODE, permissionCallback, getKrollObject(),
																 promise);
				currentActivity.requestPermissions(filteredPermissions.toArray(new String[filteredPermissions.size()]),
												   REQUEST_CODE);
				return;
			}
		}
		KrollDict response = new KrollDict();
		response.putCodeAndMessage(0, null);
		if (permissionCallback != null) {
			permissionCallback.callAsync(getKrollObject(), response);
		}
		promise.resolve(response);
	}

	@Kroll.method
	public boolean isServiceRunning(IntentProxy intentProxy)
	{
		Intent intent = intentProxy.getIntent();
		if (intent == null) {
			Log.w(TAG, "isServiceRunning called with empty intent.  Will return false, but value is meaningless.");
			return false;
		}

		TiApplication app = TiApplication.getInstance();
		ActivityManager am = (ActivityManager) app.getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
		if (am != null) {
			List<RunningServiceInfo> services = am.getRunningServices(Integer.MAX_VALUE);
			for (RunningServiceInfo service : services) {
				if (service.service.equals(intent.getComponent())) {
					return true;
				}
			}
		}
		return false;
	}

	@Kroll.method
	public void registerBroadcastReceiver(BroadcastReceiverProxy receiverProxy, Object[] args)
	{
		if (receiverProxy != null && args != null && args.length > 0 && args[0] instanceof Object[]) {
			IntentFilter filter = new IntentFilter();
			Object[] actions = (Object[]) args[0];

			for (Object action : actions) {
				filter.addAction(TiConvert.toString(action));
			}

			TiApplication.getInstance().registerReceiver(receiverProxy.getBroadcastReceiver(), filter);
			if (this.registeredBroadcastReceiverProxyList.contains(receiverProxy) == false) {
				this.registeredBroadcastReceiverProxyList.add(receiverProxy);
			}
			KrollRuntime.incrementServiceReceiverRefCount();
		}
	}

	@Kroll.method
	public void unregisterBroadcastReceiver(BroadcastReceiverProxy receiverProxy)
	{
		if (receiverProxy != null) {
			try {
				TiApplication.getInstance().unregisterReceiver(receiverProxy.getBroadcastReceiver());
				this.registeredBroadcastReceiverProxyList.remove(receiverProxy);
				KrollRuntime.decrementServiceReceiverRefCount();
			} catch (Exception e) {
				Log.e(TAG, "Unable to unregister broadcast receiver: " + e.getMessage());
			}
		}
	}

	/**
	 * A "bound" service instance. Returns the proxy so that .start and .stop can be called directly on the service.
	 */
	@Kroll.method
	public ServiceProxy createService(IntentProxy intentProxy)
	{
		return new ServiceProxy(intentProxy);
	}

	@Override
	public String getApiName()
	{
		return "Ti.Android";
	}
}
