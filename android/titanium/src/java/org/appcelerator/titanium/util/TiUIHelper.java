/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.digest.DigestUtils;
import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.common.CurrentActivityListener;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiMessenger;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.io.TiBaseFile;
import org.appcelerator.titanium.io.TiFileFactory;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.proxy.TiWindowProxy;
import org.appcelerator.titanium.proxy.TiWindowProxy.PostOpenListener;
import org.appcelerator.titanium.view.TiBackgroundDrawable;
import org.appcelerator.titanium.view.TiDrawableReference;
import org.appcelerator.titanium.view.TiUIView;

import android.app.Activity;
import android.support.v7.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

/**
 * A set of utility methods focused on UI and View operations.
 */
@SuppressWarnings("deprecation")
public class TiUIHelper
{
	private static final String TAG = "TiUIHelper";
	private static final String customFontPath = "Resources/fonts";

	public static final int PORTRAIT = 1;
	public static final int UPSIDE_PORTRAIT = 2;
	public static final int LANDSCAPE_LEFT = 3;
	public static final int LANDSCAPE_RIGHT = 4;
	public static final int FACE_UP = 5;
	public static final int FACE_DOWN = 6;
	public static final int UNKNOWN = 7;
	public static final Pattern SIZED_VALUE = Pattern.compile("([0-9]*\\.?[0-9]+)\\W*(px|dp|dip|sp|sip|mm|pt|in)?");
	public static final String MIME_TYPE_PNG = "image/png";

	private static Method overridePendingTransition;
	private static Map<String, String> resourceImageKeys = Collections.synchronizedMap(new HashMap<String, String>());
	private static Map<String, Typeface> mCustomTypeFaces = Collections.synchronizedMap(new HashMap<String, Typeface>());
	
	public static OnClickListener createDoNothingListener() {
		return new OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				// Do nothing
			}
		};
	}

	public static OnClickListener createKillListener() {
		return new OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				Process.killProcess(Process.myPid());
			}
		};
	}

	public static OnClickListener createFinishListener(final Activity me) {
		return new OnClickListener(){
			public void onClick(DialogInterface dialog, int which) {
				me.finish();
			}
		};
	}

	public static void doKillOrContinueDialog(Context context, String title, String message, OnClickListener positiveListener, OnClickListener negativeListener) {
		if (positiveListener == null) {
			positiveListener = createDoNothingListener();
		}
		if (negativeListener == null) {
			negativeListener = createKillListener();
		}
		
		new AlertDialog.Builder(context).setTitle(title).setMessage(message)
			.setPositiveButton("Continue", positiveListener)
			.setNegativeButton("Kill", negativeListener)
			.setCancelable(false).create().show();
	}
	
	public static void linkifyIfEnabled(TextView tv, Object autoLink)
	{ 
		if (autoLink != null) {
			//Default to Ti.UI.AUTOLINK_NONE
			boolean success = Linkify.addLinks(tv, TiConvert.toInt(autoLink, 16));
			if (!success && tv.getText() instanceof Spanned) {
				tv.setMovementMethod(LinkMovementMethod.getInstance());
			}
		}
	}

	/**
	 * Waits for the current activity to be ready, then invokes
	 * {@link CurrentActivityListener#onCurrentActivityReady(Activity)}.
	 * @param l the CurrentActivityListener.
	 */
	public static void waitForCurrentActivity(final CurrentActivityListener l)
	{
		// Some window opens are async, so we need to make sure we don't
		// sandwich ourselves in between windows when transitioning
		// between activities TIMOB-3644
		TiWindowProxy waitingForOpen = TiWindowProxy.getWaitingForOpen();
		if (waitingForOpen != null) {
			waitingForOpen.setPostOpenListener(new PostOpenListener() {
				// TODO @Override
				public void onPostOpen(TiWindowProxy window)
				{
					TiApplication app = TiApplication.getInstance();
					Activity activity = app.getCurrentActivity();
					if (activity != null) {
						l.onCurrentActivityReady(activity);
					}
				}
			});
		} else {
			TiApplication app = TiApplication.getInstance();
			Activity activity = app.getCurrentActivity();
			if (activity != null) {
				l.onCurrentActivityReady(activity);
			}
		}
	}

	/**
	 * Creates and shows a dialog with an OK button given title and message.
	 * The dialog's creation context is the current activity.
	 * @param title  the title of dialog.
	 * @param message  the dialog's message.
	 * @param listener the click listener for click events.
	 */
	public static void doOkDialog(final String title, final String message, OnClickListener listener) {
		if (listener == null) {
			listener = new OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					Activity ownerActivity = ((AlertDialog)dialog).getOwnerActivity();
					//if activity is not finishing, remove dialog to free memory
					if (ownerActivity != null && !ownerActivity.isFinishing()) {
						((TiBaseActivity)ownerActivity).removeDialog((AlertDialog) dialog);
					}
				}};
		}
		final OnClickListener fListener = listener;
		waitForCurrentActivity(new CurrentActivityListener() {
			// TODO @Override
			public void onCurrentActivityReady(Activity activity)
			{
				//add dialog to activity for cleaning up purposes
				if (!activity.isFinishing()) {
					AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(title).setMessage(message)
							.setPositiveButton(android.R.string.ok, fListener)
							.setCancelable(false).create();
					if (activity instanceof TiBaseActivity) {
						TiBaseActivity baseActivity = (TiBaseActivity) activity;
						baseActivity.addDialog(baseActivity.new DialogWrapper(dialog, true, new WeakReference<TiBaseActivity>(baseActivity)));
						dialog.setOwnerActivity(activity);
					}
					dialog.show();

				}

			}
		});
	}

	public static int toTypefaceStyle(String fontWeight, String fontStyle)
	{
		int style = Typeface.NORMAL;

		if (fontWeight != null) {
			if (fontWeight.equals("bold")) {
				if (fontStyle != null && fontStyle.equals("italic")) {
					style = Typeface.BOLD_ITALIC;
				} else {
					style = Typeface.BOLD;
				}
			} else if (fontStyle != null && fontStyle.equals("italic")) {
				style = Typeface.ITALIC;
			}
		} else if (fontStyle != null && fontStyle.equals("italic")) {
			style = Typeface.ITALIC;
		}
		return style;
	}

	public static int getSizeUnits(String size) {
		int units = TypedValue.COMPLEX_UNIT_PX;
		String unitString = null;

		if (size != null) {
			Matcher m = SIZED_VALUE.matcher(size.trim());
			if (m.matches()) {
				if (m.groupCount() == 2) {
					unitString = m.group(2);
				}
			}
		}

		if (unitString == null) {
			unitString = TiApplication.getInstance().getDefaultUnit();
		}

		if (TiDimension.UNIT_PX.equals(unitString) || TiDimension.UNIT_SYSTEM.equals(unitString)) {
			units = TypedValue.COMPLEX_UNIT_PX;
		} else if (TiDimension.UNIT_PT.equals(unitString)) {
			units = TypedValue.COMPLEX_UNIT_PT;
		} else if (TiDimension.UNIT_DP.equals(unitString) || TiDimension.UNIT_DIP.equals(unitString)) {
			units = TypedValue.COMPLEX_UNIT_DIP;
		} else if (TiDimension.UNIT_SP.equals(unitString) || TiDimension.UNIT_SIP.equals(unitString)) {
			units = TypedValue.COMPLEX_UNIT_SP;
		} else if (TiDimension.UNIT_MM.equals(unitString)) {
			units = TypedValue.COMPLEX_UNIT_MM;
		} else if (TiDimension.UNIT_CM.equals(unitString)) {
			units = TiDimension.COMPLEX_UNIT_CM;
		} else if (TiDimension.UNIT_IN.equals(unitString)) {
			units = TypedValue.COMPLEX_UNIT_IN;
		} else {
			if (unitString != null) {
				Log.w(TAG, "Unknown unit: " + unitString, Log.DEBUG_MODE);
			}
		}

		return units;
	}

	public static float getSize(String size) {
		float value = 15.0f;
		if (size != null) {
			Matcher m = SIZED_VALUE.matcher(size.trim());
			if (m.matches()) {
				value = Float.parseFloat(m.group(1));
			}
		}

		return value;
	}
	
	public static float getRawSize(int unit, float size, Context context) {
		Resources r;
		if (context != null) {
			r = context.getResources();
		} else {
			r = Resources.getSystem();
		}
		return TypedValue.applyDimension(unit, size, r.getDisplayMetrics());
	}
	
	public static float getRawDIPSize(float size, Context context) {
		return getRawSize(TypedValue.COMPLEX_UNIT_DIP, size, context);
	}
	
	public static float getRawSize(String size, Context context) {
		return getRawSize(getSizeUnits(size), getSize(size), context);
	}

	public static void styleText(TextView tv, HashMap<String, Object> d) {
	
		if (d == null) {
			TiUIHelper.styleText(tv, null, null, null);
			return;
		}
		
		String fontSize = null;
		String fontWeight = null;
		String fontFamily = null;
		String fontStyle = null;

		if (d.containsKey("fontSize")) {
			fontSize = TiConvert.toString(d, "fontSize");
		}
		if (d.containsKey("fontWeight")) {
			fontWeight = TiConvert.toString(d, "fontWeight");
		}
		if (d.containsKey("fontFamily")) {
			fontFamily = TiConvert.toString(d, "fontFamily");
		}
		if (d.containsKey("fontStyle")) {
			fontStyle = TiConvert.toString(d, "fontStyle");
		}
		TiUIHelper.styleText(tv, fontFamily, fontSize, fontWeight, fontStyle);
	}

	public static void styleText(TextView tv, String fontFamily, String fontSize, String fontWeight)
	{
		styleText(tv, fontFamily, fontSize, fontWeight, null);
	}

	public static void styleText(TextView tv, String fontFamily, String fontSize, String fontWeight, String fontStyle)
	{
		Typeface tf = tv.getTypeface();
		tf = toTypeface(tv.getContext(), fontFamily);
		tv.setTypeface(tf, toTypefaceStyle(fontWeight, fontStyle));
		tv.setTextSize(getSizeUnits(fontSize), getSize(fontSize));
	}

	public static boolean isAndroidTypeface(String fontFamily)
	{
		if (fontFamily != null) {
			if ("monospace".equals(fontFamily)) {
				return true;
			} else if ("serif".equals(fontFamily)) {
				return true;
			} else if ("sans-serif".equals(fontFamily)) {
				return true;
			}
		}
		return false;
	}

	public static Typeface toTypeface(Context context, String fontFamily)
	{
		Typeface tf = Typeface.SANS_SERIF; // default

		if (fontFamily != null) {
			if ("monospace".equals(fontFamily)) {
				tf = Typeface.MONOSPACE;
			} else if ("serif".equals(fontFamily)) {
				tf = Typeface.SERIF;
			} else if ("sans-serif".equals(fontFamily)) {
				tf = Typeface.SANS_SERIF;
			} else {
				Typeface loadedTf = null;
				if (context != null) {
					loadedTf = loadTypeface(context, fontFamily);
				}
				if (loadedTf == null) {
					Log.w(TAG, "Unsupported font: '" + fontFamily
						+ "' supported fonts are 'monospace', 'serif', 'sans-serif'.", Log.DEBUG_MODE);
				} else {
					tf = loadedTf;
				}
			}
		}
		return tf;
	}
	public static Typeface toTypeface(String fontFamily) {
		return toTypeface(null, fontFamily);
	}

	private static Typeface loadTypeface(Context context, String fontFamily)
	{
		if (context == null) {
			return null;
		}
		if (mCustomTypeFaces.containsKey(fontFamily)) {
			return mCustomTypeFaces.get(fontFamily);
		}
		AssetManager mgr = context.getAssets();
		try {
			String[] fontFiles = mgr.list(customFontPath);
			for (String f : fontFiles) {
				if (f.toLowerCase().equals(fontFamily.toLowerCase()) || f.toLowerCase().startsWith(fontFamily.toLowerCase() + ".")) {
					Typeface tf = Typeface.createFromAsset(mgr, customFontPath + "/" + f);
					synchronized(mCustomTypeFaces) {
						mCustomTypeFaces.put(fontFamily, tf);
					}
					return tf;
				}
			}
		} catch (IOException e) {
			Log.e(TAG, "Unable to load 'fonts' assets. Perhaps doesn't exist? " + e.getMessage());
		}

		mCustomTypeFaces.put(fontFamily, null);
		return null;
	}

	public static String getDefaultFontSize(Context context) {
		String size = "15.0px";
		TextView tv = new TextView(context);
		if (tv != null) {
			size = String.valueOf(tv.getTextSize()) + "px";
			tv = null;
		}

		return size;
	}

	public static String getDefaultFontWeight(Context context) {
		String style = "normal";
		TextView tv = new TextView(context);
		if (tv != null) {
			Typeface tf = tv.getTypeface();
			if (tf != null && tf.isBold()) {
				style = "bold";
			}
		}

		return style;
	}

	public static void setAlignment(TextView tv, String textAlign, String verticalAlign) 
	{
		int gravity = Gravity.NO_GRAVITY;
		
		if (textAlign != null) {
			if ("left".equals(textAlign)) {
				 gravity |= Gravity.LEFT;
			} else if ("center".equals(textAlign)) {
				gravity |=  Gravity.CENTER_HORIZONTAL;
			} else if ("right".equals(textAlign)) {
				gravity |=  Gravity.RIGHT;
			} else {
				Log.w(TAG, "Unsupported horizontal alignment: " + textAlign);
			}
		} else {
			// Nothing has been set - let's set if something was set previously
			// You can do this with shortcut syntax - but long term maint of code is easier if it's explicit
			Log.w(TAG,
				"No alignment set - old horizontal align was: " + (tv.getGravity() & Gravity.HORIZONTAL_GRAVITY_MASK),
				Log.DEBUG_MODE);
			
			if ((tv.getGravity() & Gravity.HORIZONTAL_GRAVITY_MASK) != Gravity.NO_GRAVITY) {
				// Something was set before - so let's use it
				gravity |= tv.getGravity() & Gravity.HORIZONTAL_GRAVITY_MASK;
			}
		}
		
		if (verticalAlign != null) {
			if ("top".equals(verticalAlign)) {
				gravity |= Gravity.TOP;
			} else if ("middle".equals(verticalAlign)) {
				gravity |= Gravity.CENTER_VERTICAL;			
			} else if ("bottom".equals(verticalAlign)) {
				gravity |= Gravity.BOTTOM;			
			} else {
				Log.w(TAG, "Unsupported vertical alignment: " + verticalAlign);
			}
		} else {
			// Nothing has been set - let's set if something was set previously
			// You can do this with shortcut syntax - but long term maint of code is easier if it's explicit
			Log.w(TAG, "No alignment set - old vertical align was: " + (tv.getGravity() & Gravity.VERTICAL_GRAVITY_MASK),
				Log.DEBUG_MODE);
			if ((tv.getGravity() & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.NO_GRAVITY) {
				// Something was set before - so let's use it
				gravity |= tv.getGravity() & Gravity.VERTICAL_GRAVITY_MASK;
			}			
		}
		
		tv.setGravity(gravity);
	}

	public static final int FONT_SIZE_POSITION = 0;
	public static final int FONT_FAMILY_POSITION = 1;
	public static final int FONT_WEIGHT_POSITION = 2;
	public static final int FONT_STYLE_POSITION = 3;
	
	public static String[] getFontProperties(KrollDict fontProps)
	{
		boolean bFontSet = false;
		String[] fontProperties = new String[4];
		if (fontProps.containsKey(TiC.PROPERTY_FONT) && fontProps.get(TiC.PROPERTY_FONT) instanceof HashMap) {
			bFontSet = true;
			KrollDict font = fontProps.getKrollDict(TiC.PROPERTY_FONT);
			if (font.containsKey(TiC.PROPERTY_FONTSIZE)) {
				fontProperties[FONT_SIZE_POSITION] = TiConvert.toString(font, TiC.PROPERTY_FONTSIZE);
			}
			if (font.containsKey(TiC.PROPERTY_FONTFAMILY)) {
				fontProperties[FONT_FAMILY_POSITION] = TiConvert.toString(font, TiC.PROPERTY_FONTFAMILY);
			}
			if (font.containsKey(TiC.PROPERTY_FONTWEIGHT)) {
				fontProperties[FONT_WEIGHT_POSITION] = TiConvert.toString(font, TiC.PROPERTY_FONTWEIGHT);
			}
			if (font.containsKey(TiC.PROPERTY_FONTSTYLE)) {
				fontProperties[FONT_STYLE_POSITION] = TiConvert.toString(font, TiC.PROPERTY_FONTSTYLE);
			}
		} else {
			if (fontProps.containsKey(TiC.PROPERTY_FONT_FAMILY)) {
				bFontSet = true;
				fontProperties[FONT_FAMILY_POSITION] = TiConvert.toString(fontProps, TiC.PROPERTY_FONT_FAMILY);
			}
			if (fontProps.containsKey(TiC.PROPERTY_FONT_SIZE)) {
				bFontSet = true;
				fontProperties[FONT_SIZE_POSITION] = TiConvert.toString(fontProps, TiC.PROPERTY_FONT_SIZE);
			}
			if (fontProps.containsKey(TiC.PROPERTY_FONT_WEIGHT)) {
				bFontSet = true;
				fontProperties[FONT_WEIGHT_POSITION] = TiConvert.toString(fontProps, TiC.PROPERTY_FONT_WEIGHT);
			}
			if (fontProps.containsKey(TiC.PROPERTY_FONTFAMILY)) {
				bFontSet = true;
				fontProperties[FONT_FAMILY_POSITION] = TiConvert.toString(fontProps, TiC.PROPERTY_FONTFAMILY);
			}
			if (fontProps.containsKey(TiC.PROPERTY_FONTSIZE)) {
				bFontSet = true;
				fontProperties[FONT_SIZE_POSITION] = TiConvert.toString(fontProps, TiC.PROPERTY_FONTSIZE);
			}
			if (fontProps.containsKey(TiC.PROPERTY_FONTWEIGHT)) {
				bFontSet = true;
				fontProperties[FONT_WEIGHT_POSITION] = TiConvert.toString(fontProps, TiC.PROPERTY_FONTWEIGHT);
			}
			if (fontProps.containsKey(TiC.PROPERTY_FONTSTYLE)) {
				bFontSet = true;
				fontProperties[FONT_STYLE_POSITION] = TiConvert.toString(fontProps, TiC.PROPERTY_FONTSTYLE);
			}
		}
		if (!bFontSet) {
			return null;
		}
		return fontProperties;
	}
	public static void setTextViewDIPPadding(TextView textView, int horizontalPadding, int verticalPadding) {
		int rawHPadding = (int)getRawDIPSize(horizontalPadding, textView.getContext());
		int rawVPadding = (int)getRawDIPSize(verticalPadding, textView.getContext());
		textView.setPadding(rawHPadding, rawVPadding, rawHPadding, rawVPadding);
	}

	public static Drawable buildBackgroundDrawable(String color, String image, boolean tileImage, Drawable gradientDrawable)
	{
		// Create an array of the layers that will compose this background.
		// Note that the order in which the layers is important to get the
		// correct rendering behavior.
		ArrayList<Drawable> layers = new ArrayList<Drawable>(3);

		if (color != null) {
			Drawable colorDrawable = new ColorDrawable(TiColorHelper.parseColor(color));
			layers.add(colorDrawable);
		}

		if (gradientDrawable != null) {
			layers.add(gradientDrawable);
		}

		Drawable imageDrawable = null;
		if (image != null) {
			TiFileHelper tfh = TiFileHelper.getInstance();
			imageDrawable = tfh.loadDrawable(image, false, true, false);

			if (tileImage) {
				if (imageDrawable instanceof BitmapDrawable) {
					BitmapDrawable tiledBackground = (BitmapDrawable) imageDrawable;
					tiledBackground.setTileModeX(Shader.TileMode.REPEAT);
					tiledBackground.setTileModeY(Shader.TileMode.REPEAT);
					imageDrawable = tiledBackground;
				}
			}

			if (imageDrawable != null) {
				layers.add(imageDrawable);
			}
		}

		return new LayerDrawable(layers.toArray(new Drawable[layers.size()]));
	}

	private static final int[] BACKGROUND_DEFAULT_STATE_1 = {
		android.R.attr.state_window_focused,
		android.R.attr.state_enabled
	};
	private static final int[] BACKGROUND_DEFAULT_STATE_2 = {
		android.R.attr.state_enabled
	};
	private static final int[] BACKGROUND_SELECTED_STATE = {
		android.R.attr.state_window_focused,
		android.R.attr.state_enabled,
		android.R.attr.state_pressed
	};
	private static final int[] BACKGROUND_FOCUSED_STATE = {
		android.R.attr.state_focused,
		android.R.attr.state_window_focused,
		android.R.attr.state_enabled
	};
	private static final int[] BACKGROUND_DISABLED_STATE = {
		-android.R.attr.state_enabled
	};

	public static StateListDrawable buildBackgroundDrawable(
		String image,
		boolean tileImage,
		String color,
		String selectedImage,
		String selectedColor,
		String disabledImage,
		String disabledColor,
		String focusedImage,
		String focusedColor,
		Drawable gradientDrawable)
	{
		StateListDrawable sld = new StateListDrawable();

		Drawable bgSelectedDrawable = buildBackgroundDrawable(selectedColor, selectedImage, tileImage, gradientDrawable);
		if (bgSelectedDrawable != null) {
			sld.addState(BACKGROUND_SELECTED_STATE, bgSelectedDrawable);
		}

		Drawable bgFocusedDrawable = buildBackgroundDrawable(focusedColor, focusedImage, tileImage, gradientDrawable);
		if (bgFocusedDrawable != null) {
			sld.addState(BACKGROUND_FOCUSED_STATE, bgFocusedDrawable);
		}

		Drawable bgDisabledDrawable = buildBackgroundDrawable(disabledColor, disabledImage, tileImage, gradientDrawable);
		if (bgDisabledDrawable != null) {
			sld.addState(BACKGROUND_DISABLED_STATE, bgDisabledDrawable);
		}

		Drawable bgDrawable = buildBackgroundDrawable(color, image, tileImage, gradientDrawable);
		if (bgDrawable != null) {
			sld.addState(BACKGROUND_DEFAULT_STATE_1, bgDrawable);
			sld.addState(BACKGROUND_DEFAULT_STATE_2, bgDrawable);
		}

		return sld;
	}

	public static KrollDict createDictForImage(int width, int height, byte[] data)
	{
		KrollDict d = new KrollDict();
		d.put(TiC.PROPERTY_X, 0);
		d.put(TiC.PROPERTY_Y, 0);
		d.put(TiC.PROPERTY_WIDTH, width);
		d.put(TiC.PROPERTY_HEIGHT, height);
		d.put(TiC.PROPERTY_MIMETYPE, MIME_TYPE_PNG);

		KrollDict cropRect = new KrollDict();
		cropRect.put(TiC.PROPERTY_X, 0);
		cropRect.put(TiC.PROPERTY_X, 0);
		cropRect.put(TiC.PROPERTY_WIDTH, width);
		cropRect.put(TiC.PROPERTY_HEIGHT, height);
		d.put(TiC.PROPERTY_CROP_RECT, cropRect);
		d.put(TiC.PROPERTY_MEDIA, TiBlob.blobFromData(data, MIME_TYPE_PNG));

		return d;
	}

	public static TiBlob getImageFromDict(KrollDict dict)
	{
		if (dict != null) {
			if (dict.containsKey(TiC.PROPERTY_MEDIA)) {
				Object media = dict.get(TiC.PROPERTY_MEDIA);
				if (media instanceof TiBlob) {
					return (TiBlob) media;
				}
			}
		}
		return null;
	}

	public static KrollDict viewToImage(KrollDict proxyDict, View view)
	{
		KrollDict image = new KrollDict();

		if (view != null) {
			int width = view.getWidth();
			int height = view.getHeight();

			// maybe move this out to a separate method once other refactor regarding "getWidth", etc is done
			if (view.getWidth() == 0 && proxyDict != null && proxyDict.containsKey(TiC.PROPERTY_WIDTH)) {
				TiDimension widthDimension = new TiDimension(proxyDict.getString(TiC.PROPERTY_WIDTH), TiDimension.TYPE_WIDTH);
				width = widthDimension.getAsPixels(view);
			}
			if (view.getHeight() == 0 && proxyDict != null && proxyDict.containsKey(TiC.PROPERTY_HEIGHT)) {
				TiDimension heightDimension = new TiDimension(proxyDict.getString(TiC.PROPERTY_HEIGHT),
					TiDimension.TYPE_HEIGHT);
				height = heightDimension.getAsPixels(view);
			}

			int wmode = width == 0 ? MeasureSpec.UNSPECIFIED : MeasureSpec.EXACTLY;
			int hmode = height == 0 ? MeasureSpec.UNSPECIFIED : MeasureSpec.EXACTLY;
			view.measure(MeasureSpec.makeMeasureSpec(width, wmode), MeasureSpec.makeMeasureSpec(height, hmode));

			// Will force the view to layout itself, grab dimensions
			width = view.getMeasuredWidth();
			height = view.getMeasuredHeight();

			// set a default BS value if the dimension is still 0 and log a warning
			if (width == 0) {
				width = 100;
				Log.e(TAG, "Width property is 0 for view, display view before calling toImage()", Log.DEBUG_MODE);
			}
			if (height == 0) {
				height = 100;
				Log.e(TAG, "Height property is 0 for view, display view before calling toImage()", Log.DEBUG_MODE);
			}

			if (view.getParent() == null) {
				Log.i(TAG, "View does not have parent, calling layout", Log.DEBUG_MODE);
				view.layout(0, 0, width, height);
			}

			// opacity should support transparency by default
			Config bitmapConfig = Config.ARGB_8888;

			Drawable viewBackground = view.getBackground();
			if (viewBackground != null) {
				/*
				 * If the background is opaque then we should be able to safely use a space saving format that
				 * does not support the alpha channel. Basically, if a view has a background color set then the
				 * the pixel format will be opaque. If a background image supports an alpha channel, the pixel
				 * format will report transparency (even if the image doesn't actually look transparent). In
				 * short, most of the time the Config.ARGB_8888 format will be used when viewToImage is used
				 * but in the cases where the background is opaque, the lower memory approach will be used.
				 */
				if (viewBackground.getOpacity() == PixelFormat.OPAQUE) {
					bitmapConfig = Config.RGB_565;
				}
			}

			Bitmap bitmap = Bitmap.createBitmap(width, height, bitmapConfig);
			Canvas canvas = new Canvas(bitmap);
			view.draw(canvas);

			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			if (bitmap.compress(CompressFormat.PNG, 100, bos)) {
				image = createDictForImage(width, height, bos.toByteArray());
			}

			canvas = null;
			bitmap.recycle();
		}

		return image;
	}

	/**
	 * Creates and returns a Bitmap from an InputStream.
	 * @param stream an InputStream to read bitmap data.
	 * @return a new bitmap instance.
	 * @module.api
	 */
	public static Bitmap createBitmap(InputStream stream)
	{
		Rect pad = new Rect();
		BitmapFactory.Options opts = new BitmapFactory.Options();
		opts.inPurgeable = true;
		opts.inInputShareable = true;

		Bitmap b = null;
		try {
			b = BitmapFactory.decodeResourceStream(null, null, stream, pad, opts);
		} catch (OutOfMemoryError e) {
			Log.e(TAG, "Unable to load bitmap. Not enough memory: " + e.getMessage());
		}
		return b;
	}
	
	/**
	 * Creates and returns a density scaled Bitmap from an InputStream.
	 * @param stream an InputStream to read bitmap data.
	 * @return a new bitmap instance.
	 */
	public static Bitmap createDensityScaledBitmap(InputStream stream)
	{
		Rect pad = new Rect();
		BitmapFactory.Options opts = new BitmapFactory.Options();
		opts.inPurgeable = true;
		opts.inInputShareable = true;
		DisplayMetrics dm = new DisplayMetrics();
		dm.setToDefaults();
		opts.inDensity = DisplayMetrics.DENSITY_MEDIUM;
		opts.inTargetDensity = dm.densityDpi;
		opts.inScaled = true;

		Bitmap b = null;
		try {
			b = BitmapFactory.decodeResourceStream(null, null, stream, pad, opts);
		} catch (OutOfMemoryError e) {
			Log.e(TAG, "Unable to load bitmap. Not enough memory: " + e.getMessage());
		}
		return b;
	}
	
	private static String getResourceKeyForImage(String url)
	{
		if (resourceImageKeys.containsKey(url)) {
			return resourceImageKeys.get(url);
		}
		
		Pattern pattern = Pattern.compile("^.*/Resources/images/(.*$)");
		Matcher matcher = pattern.matcher(url);
		if (!matcher.matches()) {
			return null;
		}
		
		String chopped = matcher.group(1);
		if (chopped == null) {
			return null;
		}
		
		chopped = chopped.toLowerCase();
		String forHash = chopped;
		if (forHash.endsWith(".9.png")) {
			forHash = forHash.replace(".9.png", ".png");
		}
		String withoutExtension = chopped;
		
		if (chopped.matches("^.*\\..*$")) {
			if (chopped.endsWith(".9.png")) {
				withoutExtension = chopped.substring(0, chopped.lastIndexOf(".9.png"));
			} else {
				withoutExtension = chopped.substring(0, chopped.lastIndexOf('.'));
			}
		}
		
		String cleanedWithoutExtension = withoutExtension.replaceAll("[^a-z0-9_]", "_");
		StringBuilder result = new StringBuilder(100);
		result.append(cleanedWithoutExtension.substring(0, Math.min(cleanedWithoutExtension.length(), 80))) ;
		result.append("_");
		result.append(DigestUtils.md5Hex(forHash).substring(0, 10));
		String sResult = result.toString();
		resourceImageKeys.put(url, sResult);
		return sResult;
	}
	
	public static int getResourceId(String url)
	{
		if (!url.contains("Resources/images/")) {
			return 0;
		}
		
		String key = getResourceKeyForImage(url);
		if (key == null) {
			return 0;
		}
		
		try {
			return TiRHelper.getResource("drawable." + key, false);
		} catch (TiRHelper.ResourceNotFoundException e) {
			return 0;
		}
	}
	
	/**
	 * Creates and returns a bitmap from its url.
	 * @param url the bitmap url.
	 * @return a new bitmap instance
	 * @module.api
	 */
	public static Bitmap getResourceBitmap(String url)
	{
		int id = getResourceId(url);
		if (id == 0) {
			return null;
		} else {
			return getResourceBitmap(id);
		}
	}
	
	/**
	 * Creates and returns a bitmap for the specified resource ID.
	 * @param res_id the bitmap id.
	 * @return a new bitmap instance.
	 * @module.api
	 */
	public static Bitmap getResourceBitmap(int res_id)
	{
		BitmapFactory.Options opts = new BitmapFactory.Options();
		opts.inPurgeable = true;
		opts.inInputShareable = true;
		
		Bitmap bitmap = null;
		try {
			bitmap = BitmapFactory.decodeResource(TiApplication.getInstance().getResources(), res_id, opts);
		} catch (OutOfMemoryError e) {
			Log.e(TAG, "Unable to load bitmap. Not enough memory: " + e.getMessage());
		}
		return bitmap;
	}

	public static Drawable loadFastDevDrawable(String url)
	{
		try {
			TiBaseFile tbf = TiFileFactory.createTitaniumFile(new String[] { url }, false);
			InputStream stream = tbf.getInputStream();
			Drawable d = BitmapDrawable.createFromStream(stream, url);
			stream.close();
			return d;
		} catch (IOException e) {
			Log.w(TAG, e.getMessage(), e);
		}
		return null;
	}

	public static Drawable getResourceDrawable(String url)
	{
		int id = getResourceId(url);
		if (id == 0) {
			return null;
		}
		
		return getResourceDrawable(id);
	}
	
	public static Drawable getResourceDrawable(int res_id)
	{
		Activity activity = TiApplication.getInstance().getCurrentActivity();
		return TiDrawableReference.fromResourceId(activity, res_id).getDrawable();
	}

	public static Drawable getResourceDrawable(Object path)
	{
		Drawable d = null;
		
		try {
	
			if (path instanceof String) {
				TiUrl imageUrl = new TiUrl((String) path);
				TiFileHelper tfh = new TiFileHelper(TiApplication.getInstance());
				d = tfh.loadDrawable(imageUrl.resolve(), false);
			} else {
				d = TiDrawableReference.fromObject(TiApplication.getInstance().getCurrentActivity(), path).getDrawable();
			}
		} catch (Exception e) {
			Log.w(TAG, "Could not load drawable "+e.getMessage(), Log.DEBUG_MODE);
			d = null;
		}
		return d;
	}

	public static void overridePendingTransition(Activity activity) 
	{
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.DONUT) {
			return;
		}
		
		if (overridePendingTransition == null) {
			try {
				overridePendingTransition = Activity.class.getMethod("overridePendingTransition", Integer.TYPE, Integer.TYPE);
			} catch (NoSuchMethodException e) {
				Log.w(TAG, "Activity.overridePendingTransition() not found");
			}
			
		}
		
		if (overridePendingTransition != null) {
			try {
				overridePendingTransition.invoke(activity, new Object[]{0,0});
			} catch (InvocationTargetException e) {
				Log.e(TAG, "Called incorrectly: " + e.getMessage());
			} catch (IllegalAccessException e) {
				Log.e(TAG, "Illegal access: " + e.getMessage());
			}
		}
	}
	
	public static ColorFilter createColorFilterForOpacity(float opacity) {
		// 5x4 identity color matrix + fade the alpha to achieve opacity
		float[] matrix = {
			1, 0, 0, 0, 0,
			0, 1, 0, 0, 0,
			0, 0, 1, 0, 0,
			0, 0, 0, opacity, 0
		};
		
		return new ColorMatrixColorFilter(new ColorMatrix(matrix));
	}
	
	public static void setDrawableOpacity(Drawable drawable, float opacity) {
		if (drawable instanceof ColorDrawable || drawable instanceof TiBackgroundDrawable) {
			drawable.setAlpha(Math.round(opacity * 255));
		} else if (drawable != null) {
			drawable.setColorFilter(createColorFilterForOpacity(opacity));
		}
	}
	
	public static void setPaintOpacity(Paint paint, float opacity) {
		paint.setColorFilter(createColorFilterForOpacity(opacity));
	}

	public static void requestSoftInputChange(KrollProxy proxy, View view) 
	{
		if (proxy == null) {
			return;
		}
		
		int focusState = TiUIView.SOFT_KEYBOARD_DEFAULT_ON_FOCUS;
		
		if (proxy.hasProperty(TiC.PROPERTY_SOFT_KEYBOARD_ON_FOCUS)) {
			focusState = TiConvert.toInt(proxy.getProperty(TiC.PROPERTY_SOFT_KEYBOARD_ON_FOCUS));
		}

		if (focusState > TiUIView.SOFT_KEYBOARD_DEFAULT_ON_FOCUS) {
			if (focusState == TiUIView.SOFT_KEYBOARD_SHOW_ON_FOCUS) {
				showSoftKeyboard(view, true);
			} else if (focusState == TiUIView.SOFT_KEYBOARD_HIDE_ON_FOCUS) {
				showSoftKeyboard(view, false);
			} else {
				Log.w(TAG, "Unknown onFocus state: " + focusState);
			}
		}
	}
	
	/**
	 * Shows/hides the soft keyboard.
	 * @param view the current focused view.
	 * @param show whether to show soft keyboard.
	 */
	public static void showSoftKeyboard(View view, boolean show) 
	{
		InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Activity.INPUT_METHOD_SERVICE);

		if (imm != null) {
			boolean useForce = (Build.VERSION.SDK_INT <= Build.VERSION_CODES.DONUT || Build.VERSION.SDK_INT >= 8) ? true : false;
			String model = TiPlatformHelper.getInstance().getModel(); 
			if (model != null && model.toLowerCase().startsWith("droid")) {
				useForce = true;
			}
			if (show) {
				imm.showSoftInput(view, useForce ? InputMethodManager.SHOW_FORCED : InputMethodManager.SHOW_IMPLICIT);
			} else {
				imm.hideSoftInputFromWindow(view.getWindowToken(), useForce ? 0 : InputMethodManager.HIDE_IMPLICIT_ONLY);
			}
		}
	}

	/**
	 * Run the Runnable "delayed" by using an AsyncTask to first require a new
	 * thread and only then, in onPostExecute, run the Runnable on the UI thread.
	 * @param runnable Runnable to run on UI thread.
	 */
	public static void runUiDelayed(final Runnable runnable)
	{
		(new AsyncTask<Void, Void, Void>()
		{
			@Override
			protected Void doInBackground(Void... arg0)
			{
				return null;
			}
			/**
			 * Always invoked on UI thread.
			 */
			@Override
			protected void onPostExecute(Void result)
			{
				Handler handler = new Handler(Looper.getMainLooper());
				handler.post(runnable);
			}
		}).execute();
	}

	/**
	 * If there is a block on the UI message queue, run the Runnable "delayed".
	 * @param runnable Runnable to run on UI thread.
	 */
	public static void runUiDelayedIfBlock(final Runnable runnable)
	{
		//if (TiApplication.getInstance().getMessageQueue().isBlocking()) {
		if (TiMessenger.getMainMessenger().isBlocking()) {
			runUiDelayed(runnable);
		} else {
			//Handler handler = new Handler(Looper.getMainLooper());
			//handler.post(runnable);
			TiMessenger.getMainMessenger().getHandler().post(runnable);
		}
	}

	public static void firePostLayoutEvent(TiViewProxy proxy)
	{
		if (proxy != null && proxy.hasListeners(TiC.EVENT_POST_LAYOUT)) {
			proxy.fireEvent(TiC.EVENT_POST_LAYOUT, null, false);
		}
	}

	/**
	 * To get the redirected Uri
	 * @param Uri
	 */
	public static Uri getRedirectUri(Uri mUri) throws MalformedURLException, IOException
	{
		if (Build.VERSION.SDK_INT < TiC.API_LEVEL_HONEYCOMB &&
				("http".equals(mUri.getScheme()) || "https".equals(mUri.getScheme()))) {
			// Media player doesn't handle redirects, try to follow them
			// here. (Redirects work fine without this in ICS.)
			while (true) {
				// java.net.URL doesn't handle rtsp
				if (mUri.getScheme() != null && mUri.getScheme().equals("rtsp"))
					break;

				URL url = new URL(mUri.toString());
				HttpURLConnection cn = (HttpURLConnection) url.openConnection();
				cn.setInstanceFollowRedirects(false);
				String location = cn.getHeaderField("Location");
				if (location != null) {
					String host = mUri.getHost();
					int port = mUri.getPort();
					String scheme = mUri.getScheme();
					mUri = Uri.parse(location);
					if (mUri.getScheme() == null) {
						// Absolute URL on existing host/port/scheme
						if (scheme == null) {
							scheme = "http";
						}
						String authority = port == -1 ? host : host + ":" + port;
						mUri = mUri.buildUpon().scheme(scheme).encodedAuthority(authority).build();
					}
				} else {
					break;
				}
			}
		}
		return mUri;
	}
}
