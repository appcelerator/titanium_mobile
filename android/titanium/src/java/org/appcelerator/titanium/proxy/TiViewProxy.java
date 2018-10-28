/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.proxy;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.TreeSet;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.KrollRuntime;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.AsyncResult;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiMessenger;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.util.TiAnimationBuilder;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiUrl;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.view.TiAnimation;
import org.appcelerator.titanium.view.TiBackgroundDrawable;
import org.appcelerator.titanium.view.TiUIView;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.ViewAnimationUtils;

/**
 * The parent class of view proxies.
 */
// clang-format off
@Kroll.proxy(propertyAccessors = {
	// background properties
	"backgroundImage",
	"backgroundRepeat",
	"backgroundSelectedImage",
	"backgroundFocusedImage",
	"backgroundDisabledImage",
	"backgroundColor",
	"backgroundSelectedColor",
	"backgroundFocusedColor",
	"backgroundDisabledColor",
	"backgroundPadding",
	"backgroundGradient",
	// border properties
	"borderColor", "borderRadius", "borderWidth",
	// layout / dimension (size/width/height have custom accessors)
	"left", "top", "right", "bottom", "layout", "zIndex",
	// accessibility
	TiC.PROPERTY_ACCESSIBILITY_HINT,
	TiC.PROPERTY_ACCESSIBILITY_LABEL,
	TiC.PROPERTY_ACCESSIBILITY_VALUE,
	TiC.PROPERTY_ACCESSIBILITY_HIDDEN,
	// others
	"focusable",
	"touchEnabled",
	"visible",
	"enabled",
	"opacity",
	"softKeyboardOnFocus",
	"transform",
	"elevation",
	"touchTestId",
	"translationX",
	"translationY",
	"translationZ",
	"rotation",
	"rotationX",
	"rotationY",
	"scaleX",
	"scaleY",
	TiC.PROPERTY_TOUCH_FEEDBACK,
	TiC.PROPERTY_TOUCH_FEEDBACK_COLOR,
	TiC.PROPERTY_TRANSITION_NAME,
	TiC.PROPERTY_HIDDEN_BEHAVIOR,
	TiC.PROPERTY_ANCHOR_POINT
})
// clang-format on
public abstract class TiViewProxy extends KrollProxy implements Handler.Callback
{
	private static final String TAG = "TiViewProxy";

	private static final int MSG_FIRST_ID = KrollProxy.MSG_LAST_ID + 1;

	private static final int MSG_GETVIEW = MSG_FIRST_ID + 100;
	private static final int MSG_ADD_CHILD = MSG_FIRST_ID + 102;
	private static final int MSG_REMOVE_CHILD = MSG_FIRST_ID + 103;
	private static final int MSG_BLUR = MSG_FIRST_ID + 104;
	private static final int MSG_FOCUS = MSG_FIRST_ID + 105;
	private static final int MSG_SHOW = MSG_FIRST_ID + 106;
	private static final int MSG_HIDE = MSG_FIRST_ID + 107;
	private static final int MSG_ANIMATE = MSG_FIRST_ID + 108;
	private static final int MSG_TOIMAGE = MSG_FIRST_ID + 109;
	private static final int MSG_GETSIZE = MSG_FIRST_ID + 110;
	private static final int MSG_GETRECT = MSG_FIRST_ID + 111;
	private static final int MSG_FINISH_LAYOUT = MSG_FIRST_ID + 112;
	private static final int MSG_UPDATE_LAYOUT = MSG_FIRST_ID + 113;
	private static final int MSG_QUEUED_ANIMATE = MSG_FIRST_ID + 114;
	private static final int MSG_INSERT_VIEW_AT = MSG_FIRST_ID + 115;
	private static final int MSG_HIDE_KEYBOARD = MSG_FIRST_ID + 116;

	protected static final int MSG_LAST_ID = MSG_FIRST_ID + 999;

	protected ArrayList<TiViewProxy> children;
	protected WeakReference<TiViewProxy> parent;

	protected TiUIView view;
	protected Object pendingAnimationLock;
	protected TiAnimationBuilder pendingAnimation;
	private boolean isDecorView = false;
	private boolean overrideCurrentAnimation = false;

	/**
	 * Constructs a new TiViewProxy instance.
	 * @module.api
	 */
	public TiViewProxy()
	{
		pendingAnimationLock = new Object();

		defaultValues.put(TiC.PROPERTY_TOUCH_ENABLED, true);
		defaultValues.put(TiC.PROPERTY_SOUND_EFFECTS_ENABLED, true);
		defaultValues.put(TiC.PROPERTY_BACKGROUND_REPEAT, false);
		defaultValues.put(TiC.PROPERTY_VISIBLE, true);
		defaultValues.put(TiC.PROPERTY_ENABLED, true);
		defaultValues.put(TiC.PROPERTY_HIDDEN_BEHAVIOR, View.INVISIBLE);
		defaultValues.put(TiC.PROPERTY_HORIZONTAL_WRAP, true);
	}

	@Override
	public void handleCreationDict(KrollDict options)
	{
		options = handleStyleOptions(options);
		super.handleCreationDict(options);

		if (options.containsKey(TiC.PROPERTY_OVERRIDE_CURRENT_ANIMATION)) {
			overrideCurrentAnimation = TiConvert.toBoolean(options, TiC.PROPERTY_OVERRIDE_CURRENT_ANIMATION, false);
		}

		//TODO eventManager.addOnEventChangeListener(this);
	}

	public boolean getOverrideCurrentAnimation()
	{
		return overrideCurrentAnimation;
	}

	private static HashMap<TiUrl, String> styleSheetUrlCache = new HashMap<TiUrl, String>(5);
	protected String getBaseUrlForStylesheet()
	{
		TiUrl creationUrl = getCreationUrl();
		if (styleSheetUrlCache.containsKey(creationUrl)) {
			return styleSheetUrlCache.get(creationUrl);
		}

		String baseUrl = creationUrl.baseUrl;
		if (baseUrl == null || (baseUrl.equals("app://") && creationUrl.url.equals(""))) {
			baseUrl = "app://app.js";
		} else {
			baseUrl = creationUrl.resolve();
		}

		int idx = baseUrl.lastIndexOf("/");
		if (idx != -1) {
			baseUrl = baseUrl.substring(idx + 1).replace(".js", "");
		}

		styleSheetUrlCache.put(creationUrl, baseUrl);
		return baseUrl;
	}

	protected KrollDict handleStyleOptions(KrollDict options)
	{
		String viewId = getProxyId();
		TreeSet<String> styleClasses = new TreeSet<String>();
		// TODO styleClasses.add(getShortAPIName().toLowerCase());

		if (options.containsKey(TiC.PROPERTY_ID)) {
			viewId = TiConvert.toString(options, TiC.PROPERTY_ID);
		}
		if (options.containsKey(TiC.PROPERTY_CLASS_NAME)) {
			String className = TiConvert.toString(options, TiC.PROPERTY_CLASS_NAME);
			for (String clazz : className.split(" ")) {
				styleClasses.add(clazz);
			}
		}
		if (options.containsKey(TiC.PROPERTY_CLASS_NAMES)) {
			Object c = options.get(TiC.PROPERTY_CLASS_NAMES);
			if (c.getClass().isArray()) {
				int length = Array.getLength(c);
				for (int i = 0; i < length; i++) {
					Object clazz = Array.get(c, i);
					if (clazz != null) {
						styleClasses.add(clazz.toString());
					}
				}
			}
		}

		String baseUrl = getBaseUrlForStylesheet();
		KrollDict dict = TiApplication.getInstance().getStylesheet(baseUrl, styleClasses, viewId);
		if (dict == null || dict.isEmpty()) {
			return options;
		}

		extend(dict);
		if (Log.isDebugModeEnabled()) {
			Log.d(TAG,
				  "trying to get stylesheet for base:" + baseUrl + ",classes:" + styleClasses + ",id:" + viewId
					  + ",dict:" + dict,
				  Log.DEBUG_MODE);
		}
		// merge in our stylesheet details to the passed in dictionary
		// our passed in dictionary takes precedence over the stylesheet
		dict.putAll(options);
		return dict;
	}

	public TiAnimationBuilder getPendingAnimation()
	{
		synchronized (pendingAnimationLock)
		{
			return pendingAnimation;
		}
	}

	public void clearAnimation(TiAnimationBuilder builder)
	{
		synchronized (pendingAnimationLock)
		{
			if (pendingAnimation != null && pendingAnimation == builder) {
				pendingAnimation = null;
			}
		}
	}

	//This handler callback is tied to the UI thread.
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public boolean handleMessage(Message msg)
	{
		switch (msg.what) {
			case MSG_GETVIEW: {
				AsyncResult result = (AsyncResult) msg.obj;
				result.setResult(handleGetView());
				return true;
			}
			case MSG_ADD_CHILD: {
				AsyncResult result = (AsyncResult) msg.obj;
				handleAdd((TiViewProxy) result.getArg());
				result.setResult(null); //Signal added.
				return true;
			}
			case MSG_REMOVE_CHILD: {
				AsyncResult result = (AsyncResult) msg.obj;
				handleRemove((TiViewProxy) result.getArg());
				result.setResult(null); //Signal removed.
				return true;
			}
			case MSG_BLUR: {
				handleBlur();
				return true;
			}
			case MSG_HIDE_KEYBOARD: {
				handleHideKeyboard();
				return true;
			}
			case MSG_FOCUS: {
				handleFocus();
				return true;
			}
			case MSG_SHOW: {
				handleShow((KrollDict) msg.obj);
				return true;
			}
			case MSG_HIDE: {
				handleHide((KrollDict) msg.obj);
				return true;
			}
			case MSG_ANIMATE: {
				handleAnimate();
				return true;
			}
			case MSG_QUEUED_ANIMATE: {
				// An animation that was re-queued
				// because the view's height and width
				// were not yet known (i.e., not yet laid out)
				handleQueuedAnimate();
				return true;
			}
			case MSG_TOIMAGE: {
				AsyncResult result = (AsyncResult) msg.obj;
				result.setResult(handleToImage());
				return true;
			}
			case MSG_GETSIZE: {
				AsyncResult result = (AsyncResult) msg.obj;
				KrollDict d = null;
				d = new KrollDict();
				d.put(TiC.PROPERTY_X, 0);
				d.put(TiC.PROPERTY_Y, 0);
				if (view != null) {
					View v = view.getNativeView();
					if (v != null) {
						TiDimension nativeWidth = new TiDimension(v.getWidth(), TiDimension.TYPE_WIDTH);
						TiDimension nativeHeight = new TiDimension(v.getHeight(), TiDimension.TYPE_HEIGHT);

						// TiDimension needs a view to grab the window manager, so we'll just use the decorview of the current window
						View decorView = TiApplication.getAppRootOrCurrentActivity().getWindow().getDecorView();
						if (decorView != null) {
							d.put(TiC.PROPERTY_WIDTH, nativeWidth.getAsDefault(decorView));
							d.put(TiC.PROPERTY_HEIGHT, nativeHeight.getAsDefault(decorView));
						}
					}
				}
				if (!d.containsKey(TiC.PROPERTY_WIDTH)) {
					d.put(TiC.PROPERTY_WIDTH, 0);
					d.put(TiC.PROPERTY_HEIGHT, 0);
				}

				result.setResult(d);
				return true;
			}
			case MSG_GETRECT: {
				AsyncResult result = (AsyncResult) msg.obj;
				KrollDict d = null;
				d = new KrollDict();
				if (view != null) {
					View v = view.getOuterView();
					if (v != null) {
						int position[] = new int[2];
						v.getLocationInWindow(position);

						TiDimension nativeWidth = new TiDimension(v.getWidth(), TiDimension.TYPE_WIDTH);
						TiDimension nativeHeight = new TiDimension(v.getHeight(), TiDimension.TYPE_HEIGHT);
						TiDimension nativeLeft = new TiDimension(position[0], TiDimension.TYPE_LEFT);
						TiDimension nativeTop = new TiDimension(position[1], TiDimension.TYPE_TOP);
						TiDimension localLeft = new TiDimension(v.getX(), TiDimension.TYPE_LEFT);
						TiDimension localTop = new TiDimension(v.getY(), TiDimension.TYPE_TOP);

						// TiDimension needs a view to grab the window manager, so we'll just use the decorview of the current window
						View decorView = TiApplication.getAppRootOrCurrentActivity().getWindow().getDecorView();
						if (decorView != null) {
							d.put(TiC.PROPERTY_WIDTH, nativeWidth.getAsDefault(decorView));
							d.put(TiC.PROPERTY_HEIGHT, nativeHeight.getAsDefault(decorView));
							d.put(TiC.PROPERTY_X, localLeft.getAsDefault(decorView));
							d.put(TiC.PROPERTY_Y, localTop.getAsDefault(decorView));
							d.put(TiC.PROPERTY_X_ABSOLUTE, nativeLeft.getAsDefault(decorView));
							d.put(TiC.PROPERTY_Y_ABSOLUTE, nativeTop.getAsDefault(decorView));
						}
					}
				}
				if (!d.containsKey(TiC.PROPERTY_WIDTH)) {
					d.put(TiC.PROPERTY_WIDTH, 0);
					d.put(TiC.PROPERTY_HEIGHT, 0);
					d.put(TiC.PROPERTY_X, 0);
					d.put(TiC.PROPERTY_Y, 0);
				}

				result.setResult(d);
				return true;
			}
			case MSG_FINISH_LAYOUT: {
				handleFinishLayout();
				return true;
			}
			case MSG_UPDATE_LAYOUT: {
				handleUpdateLayout((HashMap) msg.obj);
				return true;
			}
			case MSG_INSERT_VIEW_AT: {
				handleInsertAt((HashMap) msg.obj);
				return true;
			}
		}
		return super.handleMessage(msg);
	}

	/*
	public Context getContext()
	{
		return getActivity();
	}
	*/

	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public KrollDict getRect()
	// clang-format on
	{
		return (KrollDict) TiMessenger.sendBlockingMainMessage(getMainHandler().obtainMessage(MSG_GETRECT),
															   getActivity());
	}

	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public KrollDict getSize()
	// clang-format on
	{
		return (KrollDict) TiMessenger.sendBlockingMainMessage(getMainHandler().obtainMessage(MSG_GETSIZE),
															   getActivity());
	}

	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public Object getWidth()
	// clang-format on
	{
		if (hasProperty(TiC.PROPERTY_WIDTH)) {
			return getProperty(TiC.PROPERTY_WIDTH);
		}

		return KrollRuntime.UNDEFINED;
	}

	// clang-format off
	@Kroll.setProperty(retain = false)
	@Kroll.method
	public void setWidth(Object width)
	// clang-format on
	{
		setPropertyAndFire(TiC.PROPERTY_WIDTH, width);
	}

	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public Object getHeight()
	// clang-format on
	{
		if (hasProperty(TiC.PROPERTY_HEIGHT)) {
			return getProperty(TiC.PROPERTY_HEIGHT);
		}

		return KrollRuntime.UNDEFINED;
	}

	// clang-format off
	@Kroll.setProperty(retain = false)
	@Kroll.method
	public void setHeight(Object height)
	// clang-format on
	{
		setPropertyAndFire(TiC.PROPERTY_HEIGHT, height);
	}

	// clang-format off
	@Kroll.method
	@Kroll.getProperty(writableKeys=true)
	public Object getCenter()
	// clang-format on
	{
		return hasProperty(TiC.PROPERTY_CENTER) ? getProperty(TiC.PROPERTY_CENTER) : KrollRuntime.UNDEFINED;
	}

	// clang-format off
	@Kroll.setProperty(retain = false)
	@Kroll.method
	public void setCenter(Object center)
	// clang-format on
	{
		setPropertyAndFire(TiC.PROPERTY_CENTER, center);
	}

	public void clearView()
	{
		if (view != null) {
			view.release();
		}
		view = null;
	}

	/**
	 * @return the TiUIView associated with this proxy.
	 * @module.api
	 */
	public TiUIView peekView()
	{
		return view;
	}

	public void setView(TiUIView view)
	{
		this.view = view;
	}

	public TiUIView forceCreateView()
	{
		view = null;
		return getOrCreateView();
	}

	/**
	 * Transfer an existing view to this view proxy.
	 * Special use in tableView. Do not use anywhere else.
	 * Called from TiTableViewRowProxyItem.java
	 * @param transferview - The view to transfer
	 * @param oldProxy - The currentProxy of the view
	 */
	public void transferView(TiUIView transferview, TiViewProxy oldProxy)
	{
		if (oldProxy != null) {
			oldProxy.setView(null);
			oldProxy.setModelListener(null);
		}
		view = transferview;
		modelListener = transferview;
		view.setProxy(this);
	}

	/**
	 * Creates or retrieves the view associated with this proxy.
	 * @return a TiUIView instance.
	 * @module.api
	 */
	public TiUIView getOrCreateView()
	{
		if (view != null) {
			return view;
		}

		if (TiApplication.isUIThread()) {
			return handleGetView();
		}

		return (TiUIView) TiMessenger.sendBlockingMainMessage(getMainHandler().obtainMessage(MSG_GETVIEW), 0);
	}

	protected TiUIView handleGetView()
	{
		if (view == null) {
			if (Log.isDebugModeEnabled()) {
				Log.d(TAG, "getView: " + getClass().getSimpleName(), Log.DEBUG_MODE);
			}

			Activity activity = getActivity();
			TiBaseActivity baseActivity = null;

			if (activity instanceof TiBaseActivity) {
				baseActivity = (TiBaseActivity) activity;

				if (baseActivity != null && baseActivity.isDestroyed()) {
					baseActivity = (TiBaseActivity) baseActivity.getParent();
				}
				if (baseActivity == null || baseActivity.isDestroyed()) {
					baseActivity = (TiBaseActivity) TiApplication.getAppRootOrCurrentActivity();
				}
				activity = baseActivity;

			} else if (activity == null) {
				activity = TiApplication.getAppRootOrCurrentActivity();
			}

			view = createView(activity);

			if (isDecorView && baseActivity != null) {
				baseActivity.setViewProxy(view.getProxy());
			}

			realizeViews(view);
			view.registerForTouch();
			view.registerForKeyPress();
		}
		return view;
	}

	public void realizeViews(TiUIView view)
	{
		setModelListener(view);

		// Use a copy so bundle can be modified as it passes up the inheritance
		// tree. Allows defaults to be added and keys removed.
		if (children != null) {
			try {
				for (TiViewProxy p : children) {
					TiUIView cv = p.getOrCreateView();
					view.add(cv);
				}
			} catch (ConcurrentModificationException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}

		synchronized (pendingAnimationLock)
		{
			if (pendingAnimation != null) {
				handlePendingAnimation(true);
			}
		}
	}

	public void releaseViews()
	{
		if (children != null) {
			for (TiViewProxy p : children) {
				p.releaseViews();
			}
		}
		if (view != null) {
			view.release();
			view = null;
		}
		if (runtimeHandler != null) {
			runtimeHandler = null;
		}
		if (mainHandler != null) {
			mainHandler = null;
		}
		setModelListener(null);
	}

	@Override
	public void release()
	{
		releaseViews();

		if (children != null) {
			children.clear();
			children = null;
		}

		super.release();
	}

	/**
	 * Implementing classes should use this method to create and return the appropriate view.
	 * @param activity the context activity.
	 * @return a TiUIView instance.
	 * @module.api
	 */
	public abstract TiUIView createView(Activity activity);

	/**
	 * Adds a child to this view proxy.
	 * @param args The child view proxy/proxies to add.
	 * @module.api
	 */
	@Kroll.method
	public void add(Object args)
	{
		if (args == null) {
			Log.e(TAG, "Add called with a null child");
			return;
		}
		if (children == null) {
			children = new ArrayList<TiViewProxy>();
		}
		if (args instanceof Object[]) {
			for (Object arg : (Object[]) args) {
				if (arg instanceof TiViewProxy) {
					add((TiViewProxy) arg);
				} else {
					Log.w(TAG, "add() unsupported array object: " + arg.getClass().getSimpleName());
				}
			}
		} else if (args instanceof TiViewProxy) {
			TiViewProxy child = (TiViewProxy) args;
			if (peekView() != null) {
				if (TiApplication.isUIThread()) {
					handleAdd(child);
					return;
				}
				TiMessenger.sendBlockingMainMessage(getMainHandler().obtainMessage(MSG_ADD_CHILD), child);
			} else {
				children.add(child);
				child.parent = new WeakReference<TiViewProxy>(this);
			}
			//TODO zOrder
		} else {
			Log.w(TAG, "add() unsupported argument type: " + args.getClass().getSimpleName());
		}
	}

	@Kroll.method
	public void replaceAt(Object params)
	{
		if (!(params instanceof HashMap)) {
			Log.e(TAG, "Argument for replaceAt must be a dictionary");
			return;
		}
		@SuppressWarnings("rawtypes")
		HashMap options = (HashMap) params;
		Integer position = -1;
		if (options.containsKey("position")) {
			position = (Integer) options.get("position");
		}
		if (children != null && children.size() > position) {
			TiViewProxy childToRemove = children.get(position);
			insertAt(params);
			remove(childToRemove);
		}
	}

	/**
	 * Adds a child to this view proxy in the specified position. This is useful for "vertical" and
	 * "horizontal" layouts.
	 * @param params A Dictionary containing a TiViewProxy for the view and an int for the position
	 * @module.api
	 */
	@Kroll.method
	public void insertAt(Object params)
	{
		if (!(params instanceof HashMap)) {
			Log.e(TAG, "Argument for insertAt must be a dictionary");
			return;
		}
		@SuppressWarnings("rawtypes")
		HashMap options = (HashMap) params;

		if (children == null) {
			children = new ArrayList<TiViewProxy>();
		}

		if (view != null) {
			if (TiApplication.isUIThread()) {
				handleInsertAt(options);
				return;
			}
			getMainHandler().obtainMessage(MSG_INSERT_VIEW_AT, options).sendToTarget();
		} else {
			handleInsertAt(options);
		}
	}

	private void handleInsertAt(@SuppressWarnings("rawtypes") HashMap options)
	{
		TiViewProxy child = null;
		Integer position = -1;
		if (options.containsKey("view")) {
			child = (TiViewProxy) options.get("view");
		}
		if (options.containsKey("position")) {
			position = (Integer) options.get("position");
		}
		if (child == null) {
			Log.e(TAG, "insertAt must be contain a view");
			return;
		}
		if (position < 0 || position > children.size()) {
			position = children.size();
		}

		children.add(position, child);
		child.parent = new WeakReference<TiViewProxy>(this);

		if (view != null) {
			child.setActivity(getActivity());
			if (this instanceof DecorViewProxy) {
				child.isDecorView = true;
			}
			TiUIView cv = child.getOrCreateView();
			view.insertAt(cv, position);
		}
	}

	private void handleAdd(TiViewProxy child)
	{
		children.add(child);
		child.parent = new WeakReference<TiViewProxy>(this);
		if (view != null) {
			child.setActivity(getActivity());
			if (this instanceof DecorViewProxy) {
				child.isDecorView = true;
			}
			view.add(child.getOrCreateView());
		}
	}

	/**
	 * Removes a view from this view proxy, releasing the underlying native view if it exists.
	 * @param child The child to remove.
	 * @module.api
	 */
	@Kroll.method
	public void remove(TiViewProxy child)
	{
		if (child == null) {
			Log.e(TAG, "Add called with null child");
			return;
		}

		if (peekView() != null) {
			if (TiApplication.isUIThread()) {
				handleRemove(child);
				return;
			}

			TiMessenger.sendBlockingMainMessage(getMainHandler().obtainMessage(MSG_REMOVE_CHILD), child);

		} else {
			if (children != null) {
				children.remove(child);
				if (child.parent != null && child.parent.get() == this) {
					child.parent = null;
				}
			}
		}
	}

	/**
	 * Removes all children views.
	 * @module.api
	 */
	@Kroll.method
	public void removeAllChildren()
	{
		if (children != null) {
			//children might be altered while we loop through it (threading)
			//so we first copy children as it was when asked to remove all children
			ArrayList<TiViewProxy> childViews = new ArrayList<TiViewProxy>();
			childViews.addAll(children);
			for (TiViewProxy child : childViews) {
				remove(child);
			}
		}
	}

	/**
	* Returns the view by the given ID.
	* @module.api
	*/
	@Kroll.method
	public TiViewProxy getViewById(String id)
	{
		if (children != null) {
			for (TiViewProxy child : children) {
				if (child.children != null && child.children.size() > 0) {
					TiViewProxy parentChild = child.getViewById(id);
					if (parentChild != null) {
						return parentChild;
					}
				}

				if (child.hasProperty(TiC.PROPERTY_ID) && child.getProperty(TiC.PROPERTY_ID).equals(id)) {
					return child;
				}
			}
		}

		return null;
	}

	public void handleRemove(TiViewProxy child)
	{
		if (children != null) {
			children.remove(child);
			if (view != null) {
				view.remove(child.peekView());
			}
			if (child != null) {
				child.releaseViews();
			}
		}
	}

	@Kroll.method
	public void show(@Kroll.argument(optional = true) KrollDict options)
	{
		setProperty(TiC.PROPERTY_VISIBLE, true);
		if (TiApplication.isUIThread()) {
			handleShow(options);
		} else {
			getMainHandler().obtainMessage(MSG_SHOW, options).sendToTarget();
		}
	}

	protected void handleShow(KrollDict options)
	{
		if (view != null) {
			if (Build.VERSION.SDK_INT >= 21 && TiConvert.toBoolean(options, TiC.PROPERTY_ANIMATED, false)) {
				View nativeView = view.getOuterView();
				int width = nativeView.getWidth();
				int height = nativeView.getHeight();
				int radius = Math.max(width, height);
				Animator anim = ViewAnimationUtils.createCircularReveal(nativeView, width / 2, height / 2, 0, radius);
				view.show();
				anim.start();
				return;
			}
			view.show();
		}
	}

	@Kroll.method
	public void hide(@Kroll.argument(optional = true) KrollDict options)
	{
		setProperty(TiC.PROPERTY_VISIBLE, false);
		if (TiApplication.isUIThread()) {
			handleHide(options);
		} else {
			getMainHandler().obtainMessage(MSG_HIDE, options).sendToTarget();
		}
	}

	protected void handleHide(KrollDict options)
	{
		if (view != null) {
			synchronized (pendingAnimationLock)
			{
				if (pendingAnimation != null) {
					handlePendingAnimation(false);
				}
			}
			if (Build.VERSION.SDK_INT >= 21 && TiConvert.toBoolean(options, TiC.PROPERTY_ANIMATED, false)) {
				View nativeView = view.getOuterView();
				int width = nativeView.getWidth();
				int height = nativeView.getHeight();
				int radius = Math.max(width, height);
				Animator anim = ViewAnimationUtils.createCircularReveal(nativeView, width / 2, height / 2, radius, 0);
				anim.addListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(Animator animation)
					{
						super.onAnimationEnd(animation);
						view.hide();
					}
				});

				anim.start();
				return;
			}
			view.hide();
		}
	}

	@Kroll.method
	public void animate(Object arg, @Kroll.argument(optional = true) KrollFunction callback)
	{
		synchronized (pendingAnimationLock)
		{
			if (arg instanceof HashMap) {
				@SuppressWarnings("rawtypes")
				HashMap options = (HashMap) arg;
				pendingAnimation = new TiAnimationBuilder();
				pendingAnimation.applyOptions(options);
			} else if (arg instanceof TiAnimation) {
				TiAnimation anim = (TiAnimation) arg;
				pendingAnimation = new TiAnimationBuilder();
				pendingAnimation.applyAnimation(anim);
			} else {
				throw new IllegalArgumentException("Unhandled argument to animate: " + arg.getClass().getSimpleName());
			}

			if (callback != null) {
				pendingAnimation.setCallback(callback);
			}

			handlePendingAnimation(false);
		}
	}

	public void handlePendingAnimation(boolean forceQueue)
	{
		if (pendingAnimation != null && peekView() != null) {
			if (forceQueue || !(TiApplication.isUIThread())) {
				if (Build.VERSION.SDK_INT < TiC.API_LEVEL_HONEYCOMB) {
					// Even this very small delay can help eliminate the bug
					// whereby the animated view's parent suddenly becomes
					// transparent (pre-honeycomb). cf. TIMOB-9813.
					getMainHandler().sendEmptyMessageDelayed(MSG_ANIMATE, 10);
				} else {
					getMainHandler().sendEmptyMessage(MSG_ANIMATE);
				}
			} else {
				handleAnimate();
			}
		}
	}

	protected void handleAnimate()
	{
		TiUIView tiv = peekView();

		if (tiv != null) {
			// If the nativeView's width and height are
			// zero, it could be that animate() was called
			// immediately upon window opening and the first
			// layout hasn't happened yet. In this case,
			// queue up a new request to animate.
			// Also do the same if layout properties
			// are changed and layout hasn't completed.
			View view = tiv.getNativeView();
			if (view == null || (view.getWidth() == 0 && view.getHeight() == 0) || tiv.isLayoutPending()) {
				getMainHandler().sendEmptyMessage(MSG_QUEUED_ANIMATE);
				return;
			} else {
				tiv.animate();
			}
		}
	}

	protected void handleQueuedAnimate()
	{
		TiUIView tiv = peekView();

		if (tiv != null) {
			tiv.animate();
		}
	}

	@Kroll.method
	public void blur()
	{
		if (TiApplication.isUIThread()) {
			handleBlur();
		} else {
			getMainHandler().sendEmptyMessage(MSG_BLUR);
		}
	}

	protected void handleBlur()
	{
		if (view != null) {
			view.blur();
		}
	}

	@Kroll.method
	public void focus()
	{
		if (TiApplication.isUIThread()) {
			handleFocus();
		} else {
			getMainHandler().sendEmptyMessage(MSG_FOCUS);
		}
	}

	protected void handleFocus()
	{
		if (view != null) {
			view.focus();
		}
	}

	public TiBlob toImage()
	{
		//backward compat with maps
		return toImage(null);
	}

	@Kroll.method
	public TiBlob toImage(final @Kroll.argument(optional = true) KrollFunction callback)
	{
		final boolean waitForFinish = (callback == null);
		TiBlob blob;

		/*
		 * Callback don't exist. Just render on main thread and return blob.
		 */
		if (waitForFinish) {
			if (TiApplication.isUIThread()) {
				blob = handleToImage();
			} else {
				blob = (TiBlob) TiMessenger.sendBlockingMainMessage(getMainHandler().obtainMessage(MSG_TOIMAGE),
																	getActivity());
			}
		}

		/*
		 * Callback exists. Perform async rendering and return an empty blob.
		 */
		else {
			// Create a non-null empty blob to return.
			blob = TiBlob.blobFromImage(Bitmap.createBitmap(1, 1, Config.ARGB_8888));
			Runnable renderRunnable = new Runnable() {
				public void run()
				{
					callback.callAsync(getKrollObject(), new Object[] { handleToImage() });
				}
			};

			Thread renderThread = new Thread(renderRunnable);
			renderThread.setPriority(Thread.MAX_PRIORITY);
			renderThread.start();
		}

		return blob;
	}

	protected TiBlob handleToImage()
	{
		TiUIView view = getOrCreateView();
		if (view == null) {
			return null;
		}
		KrollDict dict = view.toImage();
		return TiUIHelper.getImageFromDict(dict);
	}

	/**
	 * Fires an event that can optionally be "bubbled" to the parent view.
	 *
	 * @param eventName event to get dispatched to listeners
	 * @param data data to include in the event
	 * @param bubbles if true will send the event to the parent view after it has been dispatched to this view's listeners.
	 * @return true if the event was handled
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public boolean fireEvent(String eventName, Object data, boolean bubbles)
	{
		if (data == null) {
			data = new KrollDict();
		}

		// Set the "bubbles" property to indicate if the event needs to be bubbled.
		if (data instanceof HashMap) {
			((HashMap) data).put(TiC.PROPERTY_BUBBLES, bubbles);
		}

		// Dispatch the event to JavaScript which takes care of the bubbling.
		return super.fireEvent(eventName, data);
	}

	/**
	 * Fires an event that will be bubbled to the parent view.
	 */
	@Override
	public boolean fireEvent(String eventName, Object data)
	{
		// To remain compatible this override of fireEvent will always
		// bubble the event to the parent view. It should eventually be deprecated
		// in favor of using the fireEvent(String, Object, boolean) method.
		return fireEvent(eventName, data, true);
	}

	/**
	 * @return The parent view proxy of this view proxy.
	 * @module.api
	 */
	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public TiViewProxy getParent()
	// clang-format on
	{
		if (this.parent == null) {
			return null;
		}

		return this.parent.get();
	}

	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public String getBackgroundDisabledColor()
	// clang-format on
	{
		// Try to get the background drawable if one is available.
		TiBackgroundDrawable backgroundDrawable = getOrCreateView().getBackground();
		// Guard for views without color state backgrounds.
		if (backgroundDrawable == null) {
			return null;
		} else {
			try {
				// Get the backgroundDrawable background as a StateListDrawable.
				StateListDrawable stateListDrawable = ((StateListDrawable) backgroundDrawable.getBackground());
				// Get the reflection methods.
				Method getStateDrawableIndexMethod =
					StateListDrawable.class.getMethod("getStateDrawableIndex", int[].class);
				Method getStateDrawableMethod = StateListDrawable.class.getMethod("getStateDrawable", int.class);
				// Get the disabled state's (as defined in TiUIHelper) index.
				int index =
					(int) getStateDrawableIndexMethod.invoke(stateListDrawable, TiUIHelper.BACKGROUND_DISABLED_STATE);
				// Get the drawable at the index.
				Drawable drawable = (Drawable) getStateDrawableMethod.invoke(stateListDrawable, index);
				// Try to get the 0 index of the result.
				if (drawable instanceof LayerDrawable) {
					Drawable drawableFromLayer = ((LayerDrawable) drawable).getDrawable(0);
					// Cast it as a ColorDrawable.
					if (drawableFromLayer instanceof ColorDrawable) {
						// Transcript the color int to HexString.
						String strColor =
							String.format("#%08X", 0xFFFFFFFF & ((ColorDrawable) drawableFromLayer).getColor());
						return strColor;
					} else {
						Log.w(TAG, "Background drawable of unexpected type. Expected - ColorDrawable. Found - "
									   + drawableFromLayer.getClass().toString());
						return null;
					}
				} else {
					Log.w(TAG, "Background drawable of unexpected type. Expected - LayerDrawable. Found - "
								   + drawable.getClass().toString());
					return null;
				}
			} catch (NoSuchMethodException e) {
				Log.w(TAG, "Unable to get a method for reflection.");
			} catch (IllegalAccessException e) {
				Log.w(TAG, "Unable to access a method for reflection.");
			} catch (InvocationTargetException e) {
				Log.w(TAG, "Unable to invoke a method for reflection.");
			}
			return null;
		}
	}

	public void setParent(TiViewProxy parent)
	{
		if (parent == null) {
			this.parent = null;
			return;
		}

		this.parent = new WeakReference<TiViewProxy>(parent);
	}

	@Override
	public KrollProxy getParentForBubbling()
	{
		return getParent();
	}

	@Override
	public void setActivity(Activity activity)
	{
		super.setActivity(activity);
		if (children != null) {
			for (TiViewProxy child : children) {
				child.setActivity(activity);
			}
		}
	}

	/**
	 * @return An array of the children view proxies of this view.
	 * @module.api
	 */
	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public TiViewProxy[] getChildren()
	// clang-format on
	{
		if (children == null)
			return new TiViewProxy[0];
		return children.toArray(new TiViewProxy[children.size()]);
	}

	@Override
	public void eventListenerAdded(String eventName, int count, KrollProxy proxy)
	{
		super.eventListenerAdded(eventName, count, proxy);
		if (eventName.equals(TiC.EVENT_CLICK) && proxy.equals(this) && count == 1
			&& !(proxy instanceof TiWindowProxy)) {
			if (!proxy.hasProperty(TiC.PROPERTY_TOUCH_ENABLED)
				|| TiConvert.toBoolean(proxy.getProperty(TiC.PROPERTY_TOUCH_ENABLED))) {
				setClickable(true);
			}
		}
	}

	@Override
	public void eventListenerRemoved(String eventName, int count, KrollProxy proxy)
	{
		super.eventListenerRemoved(eventName, count, proxy);
		if (eventName.equals(TiC.EVENT_CLICK) && count == 0 && proxy.equals(this)
			&& !(proxy instanceof TiWindowProxy)) {
			if (proxy.hasProperty(TiC.PROPERTY_TOUCH_ENABLED)
				&& !TiConvert.toBoolean(proxy.getProperty(TiC.PROPERTY_TOUCH_ENABLED))) {
				setClickable(false);
			}
		}
	}

	public void setClickable(boolean clickable)
	{
		TiUIView v = peekView();
		if (v != null) {
			View nv = v.getNativeView();
			if (nv != null) {
				nv.setClickable(clickable);
			}
		}
	}

	@Kroll.method
	public void addClass(Object[] classNames)
	{
		// This is a pretty naive implementation right now,
		// but it will work for our current needs
		String baseUrl = getBaseUrlForStylesheet();
		ArrayList<String> classes = new ArrayList<String>();
		for (Object c : classNames) {
			classes.add(TiConvert.toString(c));
		}
		KrollDict options = TiApplication.getInstance().getStylesheet(baseUrl, classes, null);
		extend(options);
	}

	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public boolean getKeepScreenOn()
	// clang-format on
	{
		Boolean keepScreenOn = null;
		TiUIView v = peekView();
		if (v != null) {
			View nv = v.getNativeView();
			if (nv != null) {
				keepScreenOn = nv.getKeepScreenOn();
			}
		}

		//Keep the proxy in the correct state
		Object current = getProperty(TiC.PROPERTY_KEEP_SCREEN_ON);
		if (current != null) {
			boolean currentValue = TiConvert.toBoolean(current);
			if (keepScreenOn == null) {
				keepScreenOn = currentValue;
			} else {
				if (currentValue != keepScreenOn) {
					setProperty(TiC.PROPERTY_KEEP_SCREEN_ON, keepScreenOn);
				} else {
					keepScreenOn = currentValue;
				}
			}
		} else {
			if (keepScreenOn == null) {
				keepScreenOn = false; // Android default
			}

			setProperty(TiC.PROPERTY_KEEP_SCREEN_ON, keepScreenOn);
		}

		return keepScreenOn;
	}

	// clang-format off
	@Kroll.method
	@Kroll.setProperty(retain = false)
	public void setKeepScreenOn(boolean keepScreenOn)
	// clang-format on
	{
		setPropertyAndFire(TiC.PROPERTY_KEEP_SCREEN_ON, keepScreenOn);
	}

	@Kroll.method
	public KrollDict convertPointToView(KrollDict point, TiViewProxy dest)
	{
		if (point == null) {
			throw new IllegalArgumentException("convertPointToView: point must not be null");
		}

		if (dest == null) {
			throw new IllegalArgumentException("convertPointToView: destinationView must not be null");
		}

		if (!point.containsKey(TiC.PROPERTY_X)) {
			throw new IllegalArgumentException("convertPointToView: required property \"x\" not found in point");
		}

		if (!point.containsKey(TiC.PROPERTY_Y)) {
			throw new IllegalArgumentException("convertPointToView: required property \"y\" not found in point");
		}

		// The spec says to throw an exception if x or y cannot be converted to numbers.
		// TiConvert does that automatically for us.
		int x = TiConvert.toInt(point, TiC.PROPERTY_X);
		int y = TiConvert.toInt(point, TiC.PROPERTY_Y);

		TiUIView view = peekView();
		TiUIView destView = dest.peekView();
		if (view == null) {
			Log.w(TAG, "convertPointToView: View has not been attached, cannot convert point");
			return null;
		}

		if (destView == null) {
			Log.w(TAG, "convertPointToView: DestinationView has not been attached, cannot convert point");
			return null;
		}

		View nativeView = view.getNativeView();
		View destNativeView = destView.getNativeView();
		if (nativeView == null || nativeView.getParent() == null) {
			Log.w(TAG, "convertPointToView: View has not been attached, cannot convert point");
			return null;
		}

		if (destNativeView == null || destNativeView.getParent() == null) {
			Log.w(TAG, "convertPointToView: DestinationView has not been attached, cannot convert point");
			return null;
		}

		int viewLocation[] = new int[2];
		int destLocation[] = new int[2];
		nativeView.getLocationInWindow(viewLocation);
		destNativeView.getLocationInWindow(destLocation);

		if (Log.isDebugModeEnabled()) {
			Log.d(TAG, "nativeView location in window, x: " + viewLocation[0] + ", y: " + viewLocation[1],
				  Log.DEBUG_MODE);
			Log.d(TAG, "destNativeView location in window, x: " + destLocation[0] + ", y: " + destLocation[1],
				  Log.DEBUG_MODE);
		}

		int pointWindowX = viewLocation[0] + x;
		int pointWindowY = viewLocation[1] + y;

		// Apply reverse transformation to get the original location
		float[] points = new float[] { pointWindowX - destLocation[0], pointWindowY - destLocation[1] };
		points = destView.getPreTranslationValue(points);

		KrollDict destPoint = new KrollDict();
		destPoint.put(TiC.PROPERTY_X, (int) points[0]);
		destPoint.put(TiC.PROPERTY_Y, (int) points[1]);
		return destPoint;
	}

	private void handleFinishLayout()
	{
		if (view.iszIndexChanged()) {
			view.forceLayoutNativeView(true);
			view.setzIndexChanged(false);
		} else {
			view.forceLayoutNativeView(false);
		}
	}

	private void handleUpdateLayout(HashMap<String, Object> params)
	{
		for (String key : params.keySet()) {
			setPropertyAndFire(key, params.get(key));
		}
		handleFinishLayout();
	}

	@Kroll.method
	public void hideKeyboard()
	{
		if (TiApplication.isUIThread()) {
			handleHideKeyboard();
		} else {
			getMainHandler().sendEmptyMessage(MSG_HIDE_KEYBOARD);
		}
	}

	protected void handleHideKeyboard()
	{
		TiUIView v = peekView();
		if (v != null) {
			View nv = v.getNativeView();
			if (nv != null) {
				TiUIHelper.showSoftKeyboard(nv, false);
			}
		}
	}
}
