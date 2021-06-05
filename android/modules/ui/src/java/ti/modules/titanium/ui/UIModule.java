/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2020 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.KrollRuntime;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.TiRootActivity;
import org.appcelerator.titanium.util.TiAnimationCurve;
import org.appcelerator.titanium.util.TiColorHelper;
import org.appcelerator.titanium.util.TiDeviceOrientation;
import org.appcelerator.titanium.util.TiUIHelper;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.text.util.Linkify;
import android.view.View;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatDelegate;

@Kroll.module
public class UIModule extends KrollModule
{
	private static final String TAG = "TiUIModule";

	@Kroll.constant
	public static final int RETURN_KEY_TYPE_ACTION = 0;
	@Kroll.constant
	public static final int RETURN_KEY_TYPE_NEW_LINE = 1;
	@Kroll.constant
	public static final int RETURNKEY_GO = 0;
	@Kroll.constant
	public static final int RETURNKEY_GOOGLE = 1;
	@Kroll.constant
	public static final int RETURNKEY_JOIN = 2;
	@Kroll.constant
	public static final int RETURNKEY_NEXT = 3;
	@Kroll.constant
	public static final int RETURNKEY_ROUTE = 4;
	@Kroll.constant
	public static final int RETURNKEY_SEARCH = 5;
	@Kroll.constant
	public static final int RETURNKEY_YAHOO = 6;
	@Kroll.constant
	public static final int RETURNKEY_DONE = 7;
	@Kroll.constant
	public static final int RETURNKEY_EMERGENCY_CALL = 8;
	@Kroll.constant
	public static final int RETURNKEY_DEFAULT = 9;
	@Kroll.constant
	public static final int RETURNKEY_SEND = 10;

	@Kroll.constant
	public static final int KEYBOARD_APPEARANCE_DEFAULT = -1; // Not supported

	@Kroll.constant
	public static final int KEYBOARD_TYPE_ASCII = 0;
	@Kroll.constant
	public static final int KEYBOARD_TYPE_NUMBERS_PUNCTUATION = 1;
	@Kroll.constant
	public static final int KEYBOARD_TYPE_URL = 2;
	@Kroll.constant
	public static final int KEYBOARD_TYPE_NUMBER_PAD = 3;
	@Kroll.constant
	public static final int KEYBOARD_TYPE_PHONE_PAD = 4;
	@Kroll.constant
	public static final int KEYBOARD_TYPE_EMAIL = 5;
	@Kroll.constant
	public static final int KEYBOARD_TYPE_NAMEPHONE_PAD = 6;
	@Kroll.constant
	public static final int KEYBOARD_TYPE_DEFAULT = 7;
	@Kroll.constant
	public static final int KEYBOARD_TYPE_DECIMAL_PAD = 8;

	@Kroll.constant
	public static final int ANIMATION_CURVE_EASE_IN = TiAnimationCurve.EASE_IN.toTiIntId();
	@Kroll.constant
	public static final int ANIMATION_CURVE_EASE_IN_OUT = TiAnimationCurve.EASE_IN_OUT.toTiIntId();
	@Kroll.constant
	public static final int ANIMATION_CURVE_EASE_OUT = TiAnimationCurve.EASE_OUT.toTiIntId();
	@Kroll.constant
	public static final int ANIMATION_CURVE_LINEAR = TiAnimationCurve.LINEAR.toTiIntId();

	@Kroll.constant
	public static final int AUTOLINK_ALL = Linkify.ALL;
	@Kroll.constant
	public static final int AUTOLINK_EMAIL_ADDRESSES = Linkify.EMAIL_ADDRESSES;
	@Kroll.constant
	public static final int AUTOLINK_MAP_ADDRESSES = Linkify.MAP_ADDRESSES;
	@Kroll.constant
	public static final int AUTOLINK_PHONE_NUMBERS = Linkify.PHONE_NUMBERS;
	@Kroll.constant
	public static final int AUTOLINK_URLS = Linkify.WEB_URLS;
	@Kroll.constant
	public static final int AUTOLINK_NONE = 16;

	@Kroll.constant
	public static final String AUTOFILL_TYPE_USERNAME = View.AUTOFILL_HINT_USERNAME;
	@Kroll.constant
	public static final String AUTOFILL_TYPE_PASSWORD = View.AUTOFILL_HINT_PASSWORD;
	@Kroll.constant
	public static final String AUTOFILL_TYPE_EMAIL = View.AUTOFILL_HINT_EMAIL_ADDRESS;
	@Kroll.constant
	public static final String AUTOFILL_TYPE_NAME = View.AUTOFILL_HINT_NAME;
	@Kroll.constant
	public static final String AUTOFILL_TYPE_PHONE = View.AUTOFILL_HINT_PHONE;
	@Kroll.constant
	public static final String AUTOFILL_TYPE_ADDRESS = View.AUTOFILL_HINT_POSTAL_ADDRESS;
	@Kroll.constant
	public static final String AUTOFILL_TYPE_POSTAL_CODE = View.AUTOFILL_HINT_POSTAL_CODE;
	@Kroll.constant
	public static final String AUTOFILL_TYPE_CARD_NUMBER = View.AUTOFILL_HINT_CREDIT_CARD_NUMBER;
	@Kroll.constant
	public static final String AUTOFILL_TYPE_CARD_SECURITY_CODE = View.AUTOFILL_HINT_CREDIT_CARD_SECURITY_CODE;
	@Kroll.constant
	public static final String AUTOFILL_TYPE_CARD_EXPIRATION_DATE = View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_DATE;
	@Kroll.constant
	public static final String AUTOFILL_TYPE_CARD_EXPIRATION_DAY = View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_DAY;
	@Kroll.constant
	public static final String AUTOFILL_TYPE_CARD_EXPIRATION_MONTH = View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_MONTH;
	@Kroll.constant
	public static final String AUTOFILL_TYPE_CARD_EXPIRATION_YEAR = View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_YEAR;

	@Kroll.constant
	public static final int BLEND_MODE_NORMAL = 0;
	@Kroll.constant
	public static final int BLEND_MODE_MULTIPLY = 1;
	@Kroll.constant
	public static final int BLEND_MODE_SCREEN = 2;
	@Kroll.constant
	public static final int BLEND_MODE_OVERLAY = 3;
	@Kroll.constant
	public static final int BLEND_MODE_DARKEN = 4;
	@Kroll.constant
	public static final int BLEND_MODE_LIGHTEN = 5;
	@Kroll.constant
	public static final int BLEND_MODE_COLOR_DODGE = 6;
	@Kroll.constant
	public static final int BLEND_MODE_COLOR_BURN = 7;
	@Kroll.constant
	public static final int BLEND_MODE_SOFT_LIGHT = 8;
	@Kroll.constant
	public static final int BLEND_MODE_HARD_LIGHT = 9;
	@Kroll.constant
	public static final int BLEND_MODE_DIFFERENCE = 10;
	@Kroll.constant
	public static final int BLEND_MODE_EXCLUSION = 11;
	@Kroll.constant
	public static final int BLEND_MODE_HUE = 12;
	@Kroll.constant
	public static final int BLEND_MODE_SATURATION = 13;
	@Kroll.constant
	public static final int BLEND_MODE_COLOR = 14;
	@Kroll.constant
	public static final int BLEND_MODE_LUMINOSITY = 15;
	@Kroll.constant
	public static final int BLEND_MODE_CLEAR = 16;
	@Kroll.constant
	public static final int BLEND_MODE_COPY = 17;
	@Kroll.constant
	public static final int BLEND_MODE_SOURCE_IN = 18;
	@Kroll.constant
	public static final int BLEND_MODE_SOURCE_OUT = 19;
	@Kroll.constant
	public static final int BLEND_MODE_SOURCE_ATOP = 20;
	@Kroll.constant
	public static final int BLEND_MODE_DESTINATION_OVER = 21;
	@Kroll.constant
	public static final int BLEND_MODE_DESTINATION_IN = 22;
	@Kroll.constant
	public static final int BLEND_MODE_DESTINATION_OUT = 23;
	@Kroll.constant
	public static final int BLEND_MODE_DESTINATION_ATOP = 24;
	@Kroll.constant
	public static final int BLEND_MODE_XOR = 25;
	@Kroll.constant
	public static final int BLEND_MODE_PLUS_DARKER = 26;
	@Kroll.constant
	public static final int BLEND_MODE_PLUS_LIGHTER = 27;

	@Kroll.constant
	public static final int BUTTON_STYLE_FILLED = 1;
	@Kroll.constant
	public static final int BUTTON_STYLE_OUTLINED = 2;
	@Kroll.constant
	public static final int BUTTON_STYLE_TEXT = 3;
	@Kroll.constant
	public static final int BUTTON_STYLE_OPTION_POSITIVE = 4;
	@Kroll.constant
	public static final int BUTTON_STYLE_OPTION_NEGATIVE = 5;
	@Kroll.constant
	public static final int BUTTON_STYLE_OPTION_NEUTRAL = 6;

	@Kroll.constant
	public static final int DATE_PICKER_STYLE_AUTOMATIC = 1;
	@Kroll.constant
	public static final int DATE_PICKER_STYLE_COMPACT = 2;
	@Kroll.constant
	public static final int DATE_PICKER_STYLE_INLINE = 3;
	@Kroll.constant
	public static final int DATE_PICKER_STYLE_WHEELS = 4;

	@Kroll.constant
	public static final int INPUT_BORDERSTYLE_NONE = 0;
	@Kroll.constant
	public static final int INPUT_BORDERSTYLE_ROUNDED = 1;
	@Kroll.constant
	public static final int INPUT_BORDERSTYLE_BEZEL = 2;
	@Kroll.constant
	public static final int INPUT_BORDERSTYLE_LINE = 3;
	@Kroll.constant
	public static final int INPUT_BORDERSTYLE_UNDERLINED = 4;
	@Kroll.constant
	public static final int INPUT_BORDERSTYLE_FILLED = 5;

	@Kroll.constant
	public static final int INPUT_BUTTONMODE_ONFOCUS = 0;
	@Kroll.constant
	public static final int INPUT_BUTTONMODE_ALWAYS = 1;
	@Kroll.constant
	public static final int INPUT_BUTTONMODE_NEVER = 2;

	@Kroll.constant
	public static final String LIST_ITEM_TEMPLATE_DEFAULT = "listDefaultTemplate";
	@Kroll.constant
	public static final int LIST_ACCESSORY_TYPE_NONE = 0;
	@Kroll.constant
	public static final int LIST_ACCESSORY_TYPE_CHECKMARK = 1;
	@Kroll.constant
	public static final int LIST_ACCESSORY_TYPE_DETAIL = 2;
	@Kroll.constant
	public static final int LIST_ACCESSORY_TYPE_DISCLOSURE = 3;

	@Kroll.constant
	public static final int MAP_VIEW_STANDARD = 1;
	@Kroll.constant
	public static final int MAP_VIEW_SATELLITE = 2;
	@Kroll.constant
	public static final int MAP_VIEW_HYBRID = 3;

	@Kroll.constant
	public static final int SELECTION_STYLE_NONE = 0;
	@Kroll.constant
	public static final int SELECTION_STYLE_DEFAULT = 1;

	@Kroll.constant
	public static final int SWITCH_STYLE_CHECKBOX = 0;
	@Kroll.constant
	public static final int SWITCH_STYLE_TOGGLE_BUTTON = 1;
	@Kroll.constant
	public static final int SWITCH_STYLE_SLIDER = 2;
	@Kroll.constant
	public static final int SWITCH_STYLE_CHIP = 3;

	@Kroll.constant
	public static final int TABLEVIEW_POSITION_ANY = 0;
	@Kroll.constant
	public static final int TABLEVIEW_POSITION_TOP = 1;
	@Kroll.constant
	public static final int TABLEVIEW_POSITION_MIDDLE = 2;
	@Kroll.constant
	public static final int TABLEVIEW_POSITION_BOTTOM = 3;

	@Kroll.constant
	public static final String TEXT_ALIGNMENT_LEFT = "left";
	@Kroll.constant
	public static final String TEXT_ALIGNMENT_CENTER = "center";
	@Kroll.constant
	public static final String TEXT_ALIGNMENT_RIGHT = "right";
	@Kroll.constant
	public static final String TEXT_ALIGNMENT_JUSTIFY = "justify";
	@Kroll.constant
	public static final String TEXT_VERTICAL_ALIGNMENT_BOTTOM = "bottom";
	@Kroll.constant
	public static final String TEXT_VERTICAL_ALIGNMENT_CENTER = "middle";
	@Kroll.constant
	public static final String TEXT_VERTICAL_ALIGNMENT_TOP = "top";

	@Kroll.constant
	public static final int PORTRAIT = TiDeviceOrientation.PORTRAIT.toTiIntId();
	@Kroll.constant
	public static final int UPSIDE_PORTRAIT = TiDeviceOrientation.UPSIDE_PORTRAIT.toTiIntId();
	@Kroll.constant
	public static final int LANDSCAPE_LEFT = TiDeviceOrientation.LANDSCAPE_LEFT.toTiIntId();
	@Kroll.constant
	public static final int LANDSCAPE_RIGHT = TiDeviceOrientation.LANDSCAPE_RIGHT.toTiIntId();
	@Kroll.constant
	public static final int FACE_UP = TiDeviceOrientation.FACE_UP.toTiIntId();
	@Kroll.constant
	public static final int FACE_DOWN = TiDeviceOrientation.FACE_DOWN.toTiIntId();
	@Kroll.constant
	public static final int UNKNOWN = TiDeviceOrientation.UNKNOWN.toTiIntId();

	@Kroll.constant
	public static final int PICKER_TYPE_PLAIN = -1;
	@Kroll.constant
	public static final int PICKER_TYPE_TIME = 0;
	@Kroll.constant
	public static final int PICKER_TYPE_DATE = 1;
	@Kroll.constant
	public static final int PICKER_TYPE_DATE_AND_TIME = 2;
	@Kroll.constant
	public static final int PICKER_TYPE_COUNT_DOWN_TIMER = 3;

	@Kroll.constant
	public static final int NOTIFICATION_DURATION_LONG = Toast.LENGTH_LONG;
	@Kroll.constant
	public static final int NOTIFICATION_DURATION_SHORT = Toast.LENGTH_SHORT;

	@Kroll.constant
	public static final int TABLE_VIEW_SEPARATOR_STYLE_NONE = 0;
	@Kroll.constant
	public static final int TABLE_VIEW_SEPARATOR_STYLE_SINGLE_LINE = 1;

	@Kroll.constant
	public static final int TEXT_AUTOCAPITALIZATION_NONE = 0;
	@Kroll.constant
	public static final int TEXT_AUTOCAPITALIZATION_SENTENCES = 1;
	@Kroll.constant
	public static final int TEXT_AUTOCAPITALIZATION_WORDS = 2;
	@Kroll.constant
	public static final int TEXT_AUTOCAPITALIZATION_ALL = 3;

	@Kroll.constant
	public static final int TEXT_ELLIPSIZE_TRUNCATE_START = 0;
	@Kroll.constant
	public static final int TEXT_ELLIPSIZE_TRUNCATE_MIDDLE = 1;
	@Kroll.constant
	public static final int TEXT_ELLIPSIZE_TRUNCATE_END = 2;
	@Kroll.constant
	public static final int TEXT_ELLIPSIZE_TRUNCATE_MARQUEE = 3;
	@Kroll.constant
	public static final int TEXT_ELLIPSIZE_TRUNCATE_NONE = 4;

	@Kroll.constant
	public static final String SIZE = TiC.LAYOUT_SIZE;
	@Kroll.constant
	public static final String FILL = TiC.LAYOUT_FILL;
	@Kroll.constant
	public static final String UNIT_PX = TiDimension.UNIT_PX;
	@Kroll.constant
	public static final String UNIT_MM = TiDimension.UNIT_MM;
	@Kroll.constant
	public static final String UNIT_CM = TiDimension.UNIT_CM;
	@Kroll.constant
	public static final String UNIT_IN = TiDimension.UNIT_IN;
	@Kroll.constant
	public static final String UNIT_DIP = TiDimension.UNIT_DIP;

	// TiWebViewClient onReceivedError error codes.
	@Kroll.constant
	public static final int URL_ERROR_AUTHENTICATION = WebViewClient.ERROR_AUTHENTICATION;
	@Kroll.constant
	public static final int URL_ERROR_BAD_URL = WebViewClient.ERROR_BAD_URL;
	@Kroll.constant
	public static final int URL_ERROR_CONNECT = WebViewClient.ERROR_CONNECT;
	@Kroll.constant
	public static final int URL_ERROR_SSL_FAILED = WebViewClient.ERROR_FAILED_SSL_HANDSHAKE;
	@Kroll.constant
	public static final int URL_ERROR_FILE = WebViewClient.ERROR_FILE;
	@Kroll.constant
	public static final int URL_ERROR_FILE_NOT_FOUND = WebViewClient.ERROR_FILE_NOT_FOUND;
	@Kroll.constant
	public static final int URL_ERROR_HOST_LOOKUP = WebViewClient.ERROR_HOST_LOOKUP;
	@Kroll.constant
	public static final int URL_ERROR_REDIRECT_LOOP = WebViewClient.ERROR_REDIRECT_LOOP;
	@Kroll.constant
	public static final int URL_ERROR_TIMEOUT = WebViewClient.ERROR_TIMEOUT;
	@Kroll.constant
	public static final int URL_ERROR_UNKNOWN = WebViewClient.ERROR_UNKNOWN;
	@Kroll.constant
	public static final int URL_ERROR_UNSUPPORTED_SCHEME = WebViewClient.ERROR_UNSUPPORTED_SCHEME;

	@Kroll.constant
	public static final int ATTRIBUTE_FONT = 0;
	@Kroll.constant
	public static final int ATTRIBUTE_FOREGROUND_COLOR = 1;
	@Kroll.constant
	public static final int ATTRIBUTE_BACKGROUND_COLOR = 2;
	@Kroll.constant
	public static final int ATTRIBUTE_STRIKETHROUGH_STYLE = 3;
	@Kroll.constant
	public static final int ATTRIBUTE_UNDERLINES_STYLE = 4;
	@Kroll.constant
	public static final int ATTRIBUTE_LINK = 5;
	@Kroll.constant
	public static final int ATTRIBUTE_UNDERLINE_COLOR = 6;
	@Kroll.constant
	public static final int ATTRIBUTE_SUPERSCRIPT_STYLE = 7;
	@Kroll.constant
	public static final int ATTRIBUTE_SUBSCRIPT_STYLE = 8;
	@Kroll.constant
	public static final int ATTRIBUTE_BASELINE_OFFSET = 9;

	@Kroll.constant
	public static final int INPUT_TYPE_CLASS_NUMBER = InputType.TYPE_CLASS_NUMBER;
	@Kroll.constant
	public static final int INPUT_TYPE_CLASS_TEXT = InputType.TYPE_CLASS_TEXT;

	@Kroll.constant
	public static final int HINT_TYPE_STATIC = 0;
	@Kroll.constant
	public static final int HINT_TYPE_ANIMATED = 1;

	@Kroll.constant
	public static final int HIDDEN_BEHAVIOR_GONE = View.GONE;
	@Kroll.constant
	public static final int HIDDEN_BEHAVIOR_INVISIBLE = View.INVISIBLE;

	@Kroll.constant
	public static final int USER_INTERFACE_STYLE_LIGHT = Configuration.UI_MODE_NIGHT_NO;
	@Kroll.constant
	public static final int USER_INTERFACE_STYLE_DARK = Configuration.UI_MODE_NIGHT_YES;
	@Kroll.constant
	public static final int USER_INTERFACE_STYLE_UNSPECIFIED = Configuration.UI_MODE_NIGHT_UNDEFINED;

	protected static final int MSG_LAST_ID = KrollProxy.MSG_LAST_ID + 101;

	private UIModule.Receiver broadcastReceiver;

	public UIModule()
	{
		super();

		// Register the module's broadcast receiver.
		this.broadcastReceiver = new UIModule.Receiver(this);
		TiApplication.getInstance().registerReceiver(broadcastReceiver,
													 new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED));

		// Set up a listener to be invoked when the JavaScript runtime is about to be terminated/disposed.
		KrollRuntime.addOnDisposingListener(new KrollRuntime.OnDisposingListener() {
			@Override
			public void onDisposing(KrollRuntime runtime)
			{
				// Remove this listener from the runtime's static collection.
				KrollRuntime.removeOnDisposingListener(this);

				// Unregister this module's broadcast receviers.
				TiApplication.getInstance().unregisterReceiver(broadcastReceiver);
			}
		});
	}

	@Kroll.setProperty(runOnUiThread = true)
	public void setBackgroundColor(String color)
	{
		doSetBackgroundColor(color);
	}

	protected void doSetBackgroundColor(String color)
	{
		TiRootActivity root = TiApplication.getInstance().getRootActivity();
		if (root != null) {
			root.setBackgroundColor(color != null ? TiColorHelper.parseColor(color) : Color.TRANSPARENT);
		}
	}

	@Kroll.setProperty(runOnUiThread = true)
	public void setBackgroundImage(Object image)
	{
		doSetBackgroundImage(image);
	}

	protected void doSetBackgroundImage(Object image)
	{
		TiRootActivity root = TiApplication.getInstance().getRootActivity();
		if (root != null) {
			Drawable imageDrawable = null;

			if (image instanceof Number) {
				try {
					imageDrawable = TiUIHelper.getResourceDrawable((Integer) image);
				} catch (Resources.NotFoundException e) {
					String warningMessage
						= "Unable to set background drawable for root window. "
						+ "An integer id was provided but no such drawable resource exists.";
					Log.w(TAG, warningMessage);
				}
			} else {
				imageDrawable = TiUIHelper.getResourceDrawable(image);
			}

			root.setBackgroundImage(imageDrawable);
		}
	}

	@Kroll.method
	public double convertUnits(String convertFromValue, String convertToUnits)
	{
		double result = 0;
		TiDimension dimension = new TiDimension(convertFromValue, TiDimension.TYPE_UNDEFINED);

		// TiDimension needs a view to grab the window manager, so we'll just use the decorview of the current window
		View view = TiApplication.getAppCurrentActivity().getWindow().getDecorView();

		if (view != null) {
			if (convertToUnits.equals(UNIT_PX)) {
				result = (double) dimension.getAsPixels(view);
			} else if (convertToUnits.equals(UNIT_MM)) {
				result = dimension.getAsMillimeters(view);
			} else if (convertToUnits.equals(UNIT_CM)) {
				result = dimension.getAsCentimeters(view);
			} else if (convertToUnits.equals(UNIT_IN)) {
				result = dimension.getAsInches(view);
			} else if (convertToUnits.equals(UNIT_DIP)) {
				result = (double) dimension.getAsDIP(view);
			}
		}

		return result;
	}

	@Kroll.getProperty
	public int getOverrideUserInterfaceStyle()
	{
		switch (AppCompatDelegate.getDefaultNightMode()) {
			case AppCompatDelegate.MODE_NIGHT_NO:
				return Configuration.UI_MODE_NIGHT_NO;
			case AppCompatDelegate.MODE_NIGHT_YES:
				return Configuration.UI_MODE_NIGHT_YES;
		}
		return Configuration.UI_MODE_NIGHT_UNDEFINED;
	}

	@Kroll.setProperty
	public void setOverrideUserInterfaceStyle(int styleId)
	{
		// Convert given "UI_MODE_*" constant to a "MODE_NIGHT_*" constant.
		int nightModeId = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
		if (styleId == Configuration.UI_MODE_NIGHT_NO) {
			nightModeId = AppCompatDelegate.MODE_NIGHT_NO;
		} else if (styleId == Configuration.UI_MODE_NIGHT_YES) {
			nightModeId = AppCompatDelegate.MODE_NIGHT_YES;
		}

		// Do not continue if the mode isn't changing.
		if (nightModeId == AppCompatDelegate.getDefaultNightMode()) {
			return;
		}

		// Change the night mode.
		AppCompatDelegate.setDefaultNightMode(nightModeId);

		// Fire a "userinterfacestyle" change event via our broadcast receiver.
		this.broadcastReceiver.onReceive(TiApplication.getInstance(), null);

		// Force our top-most activity apply the assigned night mode.
		// Note: Works-around a Google bug where it doesn't always call the activity's onNightModeChanged() method.
		Activity activity = TiApplication.getAppCurrentActivity();
		if (activity instanceof TiBaseActivity) {
			((TiBaseActivity) activity).applyNightMode();
		}
	}

	@Kroll.getProperty
	public int getUserInterfaceStyle()
	{
		int styleId = getOverrideUserInterfaceStyle();
		if (styleId == Configuration.UI_MODE_NIGHT_UNDEFINED) {
			Configuration config = TiApplication.getInstance().getResources().getConfiguration();
			styleId = config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
		}
		return styleId;
	}

	@Override
	public String getApiName()
	{
		return "Ti.UI";
	}

	private static class Receiver extends BroadcastReceiver
	{
		private UIModule module;
		private int lastEmittedStyle;

		public Receiver(UIModule module)
		{
			super();
			this.module = module;
			lastEmittedStyle = this.module.getUserInterfaceStyle();
		}

		@Override
		public void onReceive(Context context, Intent intent)
		{
			int currentMode = this.module.getUserInterfaceStyle();
			if (currentMode == lastEmittedStyle) {
				return;
			}
			lastEmittedStyle = currentMode;

			KrollDict event = new KrollDict();
			event.put(TiC.PROPERTY_VALUE, lastEmittedStyle);
			this.module.fireEvent(TiC.EVENT_USER_INTERFACE_STYLE, event);
		}
	}
}
