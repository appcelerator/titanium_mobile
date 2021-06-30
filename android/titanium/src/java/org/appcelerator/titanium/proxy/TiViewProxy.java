/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.proxy;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.TreeSet;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.KrollRuntime;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
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
import android.os.Message;
import android.view.View;
import android.view.ViewAnimationUtils;

/**
 * The parent class of view proxies.
 */
@Kroll.proxy(propertyAccessors = {
	// background properties
	TiC.PROPERTY_BACKGROUND_IMAGE,
	TiC.PROPERTY_BACKGROUND_REPEAT,
	TiC.PROPERTY_BACKGROUND_SELECTED_IMAGE,
	TiC.PROPERTY_BACKGROUND_FOCUSED_IMAGE,
	TiC.PROPERTY_BACKGROUND_DISABLED_IMAGE,
	TiC.PROPERTY_BACKGROUND_COLOR,
	TiC.PROPERTY_BACKGROUND_SELECTED_COLOR,
	TiC.PROPERTY_BACKGROUND_FOCUSED_COLOR,
	TiC.PROPERTY_BACKGROUND_DISABLED_COLOR,
	TiC.PROPERTY_BACKGROUND_PADDING,
	TiC.PROPERTY_BACKGROUND_GRADIENT,
	// border properties
	TiC.PROPERTY_BORDER_COLOR,
	TiC.PROPERTY_BORDER_RADIUS,
	TiC.PROPERTY_BORDER_WIDTH,
	// layout / dimension (size/width/height have custom accessors)
	TiC.PROPERTY_LEFT,
	TiC.PROPERTY_TOP,
	TiC.PROPERTY_RIGHT,
	TiC.PROPERTY_BOTTOM,
	TiC.PROPERTY_LAYOUT,
	TiC.PROPERTY_ZINDEX,
	// accessibility
	TiC.PROPERTY_ACCESSIBILITY_HINT,
	TiC.PROPERTY_ACCESSIBILITY_LABEL,
	TiC.PROPERTY_ACCESSIBILITY_VALUE,
	TiC.PROPERTY_ACCESSIBILITY_HIDDEN,
	// others
	TiC.PROPERTY_FOCUSABLE,
	TiC.PROPERTY_TOUCH_ENABLED,
	TiC.PROPERTY_FILTER_TOUCHES_WHEN_OBSCURED,
	TiC.PROPERTY_VISIBLE,
	TiC.PROPERTY_ENABLED,
	TiC.PROPERTY_OPACITY,
	TiC.PROPERTY_SOFT_KEYBOARD_ON_FOCUS,
	TiC.PROPERTY_TRANSFORM,
	TiC.PROPERTY_ELEVATION,
	TiC.PROPERTY_TRANSLATION_X,
	TiC.PROPERTY_TRANSLATION_Y,
	TiC.PROPERTY_TRANSLATION_Z,
	TiC.PROPERTY_ROTATION,
	TiC.PROPERTY_ROTATION_X,
	TiC.PROPERTY_ROTATION_Y,
	TiC.PROPERTY_SCALE_X,
	TiC.PROPERTY_SCALE_Y,
	TiC.PROPERTY_TOUCH_FEEDBACK,
	TiC.PROPERTY_TOUCH_FEEDBACK_COLOR,
	TiC.PROPERTY_TRANSITION_NAME,
	TiC.PROPERTY_HIDDEN_BEHAVIOR,
	TiC.PROPERTY_ANCHOR_POINT
})
public abstract class TiViewProxy extends KrollProxy
{
	private static final String TAG = "TiViewProxy";

	private static final int MSG_FIRST_ID = KrollProxy.MSG_LAST_ID + 1;
	private static final int MSG_ANIMATE = MSG_FIRST_ID + 108;
	private static final int MSG_QUEUED_ANIMATE = MSG_FIRST_ID + 114;
	protected static final int MSG_LAST_ID = MSG_FIRST_ID + 999;

	protected ArrayList<TiViewProxy> children = new ArrayList<>();
	protected WeakReference<TiViewProxy> parent;

	protected TiUIView view;
	protected Object pendingAnimationLock = new Object();
	protected TiAnimationBuilder pendingAnimation;
	private boolean isDecorView = false;
	private boolean overrideCurrentAnimation = false;

	/**
	 * Constructs a new TiViewProxy instance.
	 */
	public TiViewProxy()
	{
		defaultValues.put(TiC.PROPERTY_TOUCH_ENABLED, true);
		defaultValues.put(TiC.PROPERTY_FILTER_TOUCHES_WHEN_OBSCURED, false);
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

	private static final HashMap<TiUrl, String> styleSheetUrlCache = new HashMap<>(5);
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
	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public boolean handleMessage(Message msg)
	{
		switch (msg.what) {
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
		}
		return super.handleMessage(msg);
	}

	@Kroll.getProperty
	public KrollDict getRect()
	{
		View v = null;
		if (view != null) {
			v = view.getOuterView();
		}
		return getViewRect(v);
	}

	protected KrollDict getViewRect(View v)
	{
		KrollDict d = new KrollDict();
		if (v != null) {
			int[] position = new int[2];
			v.getLocationInWindow(position);

			TiDimension nativeWidth = new TiDimension(v.getWidth(), TiDimension.TYPE_WIDTH);
			TiDimension nativeHeight = new TiDimension(v.getHeight(), TiDimension.TYPE_HEIGHT);
			TiDimension nativeLeft = new TiDimension(position[0], TiDimension.TYPE_LEFT);
			TiDimension nativeTop = new TiDimension(position[1], TiDimension.TYPE_TOP);
			TiDimension localLeft = new TiDimension(v.getX(), TiDimension.TYPE_LEFT);
			TiDimension localTop = new TiDimension(v.getY(), TiDimension.TYPE_TOP);

			// TiDimension needs a view to grab the window manager.
			d.put(TiC.PROPERTY_WIDTH, nativeWidth.getAsDefault(v));
			d.put(TiC.PROPERTY_HEIGHT, nativeHeight.getAsDefault(v));
			d.put(TiC.PROPERTY_X, localLeft.getAsDefault(v));
			d.put(TiC.PROPERTY_Y, localTop.getAsDefault(v));
			d.put(TiC.PROPERTY_X_ABSOLUTE, nativeLeft.getAsDefault(v));
			d.put(TiC.PROPERTY_Y_ABSOLUTE, nativeTop.getAsDefault(v));
		}
		if (!d.containsKey(TiC.PROPERTY_WIDTH)) {
			d.put(TiC.PROPERTY_WIDTH, 0);
			d.put(TiC.PROPERTY_HEIGHT, 0);
			d.put(TiC.PROPERTY_X, 0);
			d.put(TiC.PROPERTY_Y, 0);
		}
		return d;
	}

	@Kroll.getProperty
	public KrollDict getSize()
	{
		KrollDict d = new KrollDict();
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
		return d;
	}

	@Kroll.getProperty
	public Object getWidth()
	{
		if (hasProperty(TiC.PROPERTY_WIDTH)) {
			return getProperty(TiC.PROPERTY_WIDTH);
		}

		return KrollRuntime.UNDEFINED;
	}

	@Kroll.setProperty(retain = false)
	public void setWidth(Object width)
	{
		setPropertyAndFire(TiC.PROPERTY_WIDTH, width);
	}

	@Kroll.getProperty
	public Object getHeight()
	{
		if (hasProperty(TiC.PROPERTY_HEIGHT)) {
			return getProperty(TiC.PROPERTY_HEIGHT);
		}

		return KrollRuntime.UNDEFINED;
	}

	@Kroll.setProperty(retain = false)
	public void setHeight(Object height)
	{
		setPropertyAndFire(TiC.PROPERTY_HEIGHT, height);
	}

	@Kroll.getProperty
	public Object getCenter()
	{
		Object dict = KrollRuntime.UNDEFINED;
		if (hasProperty(TiC.PROPERTY_CENTER)) {
			dict = getProperty(TiC.PROPERTY_CENTER);
		}

		return dict;
	}

	@Kroll.setProperty(retain = false)
	public void setCenter(Object center)
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
	 */
	public TiUIView getOrCreateView()
	{
		if (view != null) {
			return view;
		}
		return handleGetView();
	}

	protected TiUIView handleGetView()
	{
		if (view == null) {
			if (Log.isDebugModeEnabled()) {
				Log.d(TAG, "getView: " + getClass().getSimpleName(), Log.DEBUG_MODE);
			}

			TiViewProxy parentProxy = getParent();
			Activity parentActivity = (parentProxy != null) ? parentProxy.getActivity() : null;

			Activity lastActivity = getActivity();
			Activity activity = (parentActivity != null) ? parentActivity : lastActivity;
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
				if (parentActivity != null) {
					activity = parentActivity;
				} else {
					activity = TiApplication.getAppRootOrCurrentActivity();
				}
			}

			if (activity != lastActivity) {
				setActivity(activity);
			}

			view = createView(activity);
			if (view != null) {
				if (isDecorView && baseActivity != null) {
					baseActivity.setViewProxy(view.getProxy());
				}

				realizeViews(view);
				view.registerForTouch();
				view.registerForKeyPress();
			}
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
	 */
	public abstract TiUIView createView(Activity activity);

	/**
	 * Create clone of existing proxy, including children.
	 *
	 * @return TiViewProxy
	 */
	public TiViewProxy clone()
	{
		final TiViewProxy proxy = (TiViewProxy) KrollProxy.createProxy(
			this.getClass(),
			getKrollObject(),
			new Object[] { properties },
			this.creationUrl.url
		);

		// Include children.
		for (final TiViewProxy child : this.children) {
			proxy.add(child.clone());
		}

		return proxy;
	}

	/**
	 * Adds a child to this view proxy.
	 * @param args The child view proxy/proxies to add.
	 */
	@Kroll.method
	public void add(Object args)
	{
		if (args == null) {
			Log.e(TAG, "Add called with a null child");
			return;
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

			// Check if given view already has a parent.
			TiViewProxy parent = child.getParent();
			if ((parent == this) || (parent == child)) {
				// Do not continue if already added or given view is try to add to itself.
				return;
			} else if (parent != null) {
				// Remove given view from its current parent. (Do not release its native view.)
				if (parent.children != null) {
					parent.children.remove(child);
				}
				if (parent.view != null) {
					parent.view.remove(child.peekView());
				}
			}

			// Add given view as a child to this view.
			children.add(child);
			child.parent = new WeakReference<>(this);
			if ((peekView() != null) && (view != null)) {
				child.setActivity(getActivity());
				if (this instanceof DecorViewProxy) {
					child.isDecorView = true;
				}
				view.add(child.getOrCreateView());
			}
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

	/**
	 * Removes a view from this view proxy, releasing the underlying native view if it exists.
	 * @param child The child to remove.
	 */
	@Kroll.method
	public void remove(TiViewProxy child)
	{
		if (child == null) {
			Log.e(TAG, "Add called with null child");
			return;
		}

		if (peekView() != null) {
			handleRemove(child);
		} else {
			if (children != null) {
				children.remove(child);
			}
		}
		if (child.parent != null && child.parent.get() == this) {
			child.parent = null;
		}
	}

	/**
	 * Removes all children views.
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
		handleShow(options);
	}

	protected void handleShow(KrollDict options)
	{
		if (view != null) {
			if (TiConvert.toBoolean(options, TiC.PROPERTY_ANIMATED, false)) {
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
		handleHide(options);
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
			if (TiConvert.toBoolean(options, TiC.PROPERTY_ANIMATED, false)) {
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
			if (forceQueue) {
				getMainHandler().sendEmptyMessage(MSG_ANIMATE);
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
		handleBlur();
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
		handleFocus();
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
			blob = handleToImage();
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
		if (data instanceof HashMap) {
			((HashMap) data).put(TiC.PROPERTY_BUBBLES, bubbles);
		}
		return super.fireEvent(eventName, data);
	}

	/**
	 * Fires an event that will be bubbled to the parent view.
	 */
	@Override
	public boolean fireEvent(String eventName, Object data)
	{
		return fireEvent(eventName, data, true);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public boolean fireSyncEvent(String eventName, Object data, boolean bubbles)
	{
		if (data == null) {
			data = new KrollDict();
		}
		if (data instanceof HashMap) {
			((HashMap) data).put(TiC.PROPERTY_BUBBLES, bubbles);
		}
		return super.fireSyncEvent(eventName, data);
	}

	@Override
	public boolean fireSyncEvent(String eventName, Object data)
	{
		return fireSyncEvent(eventName, data, true);
	}

	/**
	 * @return The parent view proxy of this view proxy.
	 */
	@Kroll.getProperty
	public TiViewProxy getParent()
	{
		if (this.parent == null) {
			return null;
		}
		return this.parent.get();
	}

	@Kroll.getProperty
	public String getBackgroundColor()
	{
		// Return originally assigned property value, if available.
		if (hasPropertyAndNotNull(TiC.PROPERTY_BACKGROUND_COLOR)) {
			return TiConvert.toString(getProperty(TiC.PROPERTY_BACKGROUND_COLOR));
		}

		// Fetch the view.
		TiUIView view = getOrCreateView();
		if (view == null) {
			return null;
		}

		// Try to get the background drawable if one is available.
		// If only backgroundColor is defined then no ColorStateList is created, we resort to only the color defined.
		TiBackgroundDrawable tiBackgroundDrawable = view.getBackground();
		if (tiBackgroundDrawable == null) {
			View nativeView = view.getNativeView();
			if (nativeView != null) {
				Drawable drawable = nativeView.getBackground();
				if (drawable instanceof ColorDrawable) {
					return TiUIHelper.hexStringFrom(((ColorDrawable) drawable).getColor());
				}
			}
			return null;
		}

		// It shouldn't matter if we request the color for DEFAULT_STATE_1 or DEFAULT_STATE_2. They are the same.
		return TiUIHelper.getBackgroundColorForState(tiBackgroundDrawable, TiUIHelper.BACKGROUND_DEFAULT_STATE_1);
	}

	@Kroll.getProperty
	public String getBackgroundSelectedColor()
	{
		// Return originally assigned property value, if available.
		if (hasPropertyAndNotNull(TiC.PROPERTY_BACKGROUND_SELECTED_COLOR)) {
			return TiConvert.toString(getProperty(TiC.PROPERTY_BACKGROUND_SELECTED_COLOR));
		}

		// Fetch the view.
		TiUIView view = getOrCreateView();
		if (view == null) {
			return null;
		}

		// Try to get the background drawable if one is available.
		TiBackgroundDrawable backgroundDrawable = view.getBackground();
		if (backgroundDrawable == null) {
			return null;
		}
		return TiUIHelper.getBackgroundColorForState(backgroundDrawable, TiUIHelper.BACKGROUND_SELECTED_STATE);
	}

	@Kroll.getProperty
	public String getBackgroundFocusedColor()
	{
		// Return originally assigned property value, if available.
		if (hasPropertyAndNotNull(TiC.PROPERTY_BACKGROUND_FOCUSED_COLOR)) {
			return TiConvert.toString(getProperty(TiC.PROPERTY_BACKGROUND_FOCUSED_COLOR));
		}

		// Fetch the view.
		TiUIView view = getOrCreateView();
		if (view == null) {
			return null;
		}

		// Try to get the background drawable if one is available.
		TiBackgroundDrawable backgroundDrawable = view.getBackground();
		if (backgroundDrawable == null) {
			return null;
		}
		return TiUIHelper.getBackgroundColorForState(backgroundDrawable, TiUIHelper.BACKGROUND_FOCUSED_STATE);
	}

	@Kroll.getProperty
	public String getBackgroundDisabledColor()
	{
		// Return originally assigned property value, if available.
		if (hasPropertyAndNotNull(TiC.PROPERTY_BACKGROUND_DISABLED_COLOR)) {
			return TiConvert.toString(getProperty(TiC.PROPERTY_BACKGROUND_DISABLED_COLOR));
		}

		// Fetch the view.
		TiUIView view = getOrCreateView();
		if (view == null) {
			return null;
		}

		// Try to get the background drawable if one is available.
		TiBackgroundDrawable backgroundDrawable = view.getBackground();
		if (backgroundDrawable == null) {
			return null;
		}
		return TiUIHelper.getBackgroundColorForState(backgroundDrawable, TiUIHelper.BACKGROUND_DISABLED_STATE);
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
	 * Determines if this proxy has any child view proxies.
	 * @return Returns true if this view has at least 1 child view proxy. Returns false if it has no children.
	 */
	public boolean hasChildren()
	{
		if (this.children == null) {
			return false;
		}
		return !this.children.isEmpty();
	}

	/**
	 * @return An array of the children view proxies of this view.
	 */
	@Kroll.getProperty
	public TiViewProxy[] getChildren()
	{
		if (children == null) {
			return new TiViewProxy[0];
		}
		return children.toArray(new TiViewProxy[0]);
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
		ArrayList<String> classes = new ArrayList<>();
		for (Object c : classNames) {
			classes.add(TiConvert.toString(c));
		}
		KrollDict options = TiApplication.getInstance().getStylesheet(baseUrl, classes, null);
		extend(options);
	}

	@Kroll.getProperty
	public boolean getKeepScreenOn()
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

	@Kroll.setProperty(retain = false)
	public void setKeepScreenOn(boolean keepScreenOn)
	{
		setPropertyAndFire(TiC.PROPERTY_KEEP_SCREEN_ON, keepScreenOn);
	}

	@Kroll.method
	public KrollDict convertPointToView(KrollDict point, TiViewProxy dest)
	{
		// Validate arguments.
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

		// Fetch the given coordinate.
		TiDimension dimensionX = TiConvert.toTiDimension(point, TiC.PROPERTY_X, TiDimension.TYPE_LEFT);
		TiDimension dimensionY = TiConvert.toTiDimension(point, TiC.PROPERTY_Y, TiDimension.TYPE_TOP);
		if (dimensionX == null) {
			throw new IllegalArgumentException("convertPointToView: property \"x\" must be of type number or string");
		}
		if (dimensionY == null) {
			throw new IllegalArgumentException("convertPointToView: property \"y\" must be of type number or string");
		}

		// Fetch the views to convert between.
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
		View destNativeView = destView.getNativeContentView();
		if (nativeView == null || nativeView.getParent() == null) {
			Log.w(TAG, "convertPointToView: View has not been attached, cannot convert point");
			return null;
		}
		if (destNativeView == null || destNativeView.getParent() == null) {
			Log.w(TAG, "convertPointToView: DestinationView has not been attached, cannot convert point");
			return null;
		}

		// Convert this view's point to the given view's coordinate space.
		int[] viewLocation = new int[2];
		int[] destLocation = new int[2];
		nativeView.getLocationInWindow(viewLocation);
		destNativeView.getLocationInWindow(destLocation);
		float[] points = new float[] { viewLocation[0] - destLocation[0], viewLocation[1] - destLocation[1] };
		points = destView.getPreTranslationValue(points);
		points[0] += (float) dimensionX.getPixels(nativeView);
		points[1] += (float) dimensionY.getPixels(nativeView);
		dimensionX = new TiDimension((double) points[0], TiDimension.TYPE_LEFT);
		dimensionY = new TiDimension((double) points[1], TiDimension.TYPE_TOP);

		// Return converted point using the app's default units.
		KrollDict destPoint = new KrollDict();
		destPoint.put(TiC.PROPERTY_X, dimensionX.getAsDefault(destNativeView));
		destPoint.put(TiC.PROPERTY_Y, dimensionY.getAsDefault(destNativeView));
		return destPoint;
	}

	@Kroll.method
	public void hideKeyboard()
	{
		handleHideKeyboard();
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
