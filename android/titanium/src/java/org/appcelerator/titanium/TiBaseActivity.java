/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2018 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.KrollObject;
import org.appcelerator.kroll.KrollPromise;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.KrollRuntime;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiMessenger;
import org.appcelerator.titanium.TiLifecycle.OnLifecycleEvent;
import org.appcelerator.titanium.TiLifecycle.OnWindowFocusChangedEvent;
import org.appcelerator.titanium.TiLifecycle.interceptOnBackPressedEvent;
import org.appcelerator.titanium.TiLifecycle.OnActivityResultEvent;
import org.appcelerator.titanium.TiLifecycle.OnInstanceStateEvent;
import org.appcelerator.titanium.TiLifecycle.OnCreateOptionsMenuEvent;
import org.appcelerator.titanium.TiLifecycle.OnPrepareOptionsMenuEvent;
import org.appcelerator.titanium.proxy.ActionBarProxy;
import org.appcelerator.titanium.proxy.ActivityProxy;
import org.appcelerator.titanium.proxy.IntentProxy;
import org.appcelerator.titanium.proxy.TiToolbarProxy;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.proxy.TiWindowProxy;
import org.appcelerator.titanium.util.TiActivityResultHandler;
import org.appcelerator.titanium.util.TiActivitySupport;
import org.appcelerator.titanium.util.TiActivitySupportHelper;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiLocaleManager;
import org.appcelerator.titanium.util.TiMenuSupport;
import org.appcelerator.titanium.util.TiPlatformHelper;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.util.TiWeakList;
import org.appcelerator.titanium.view.TiActionBarStyleHandler;
import org.appcelerator.titanium.view.TiActivitySafeAreaMonitor;
import org.appcelerator.titanium.view.TiCompositeLayout;
import org.appcelerator.titanium.view.TiCompositeLayout.LayoutArrangement;
import org.appcelerator.titanium.view.TiInsetsProvider;

import android.app.Activity;
import android.support.v7.app.AppCompatActivity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;

/**
 * The base class for all non tab Titanium activities. To learn more about Activities, see the
 * <a href="http://developer.android.com/reference/android/app/Activity.html">Android Activity documentation</a>.
 */
public abstract class TiBaseActivity extends AppCompatActivity implements TiActivitySupport /*, ITiWindowHandler*/
{
	private static final String TAG = "TiBaseActivity";

	private boolean onDestroyFired = false;
	private int originalOrientationMode = -1;
	private boolean inForeground = false; // Indicates whether this activity is in foreground or not.
	private TiWeakList<OnLifecycleEvent> lifecycleListeners = new TiWeakList<OnLifecycleEvent>();
	private TiWeakList<OnWindowFocusChangedEvent> windowFocusChangedListeners =
		new TiWeakList<OnWindowFocusChangedEvent>();
	private TiWeakList<interceptOnBackPressedEvent> interceptOnBackPressedListeners =
		new TiWeakList<interceptOnBackPressedEvent>();
	private TiWeakList<OnInstanceStateEvent> instanceStateListeners = new TiWeakList<OnInstanceStateEvent>();
	private TiWeakList<OnActivityResultEvent> onActivityResultListeners = new TiWeakList<OnActivityResultEvent>();
	private TiWeakList<OnCreateOptionsMenuEvent> onCreateOptionsMenuListeners =
		new TiWeakList<OnCreateOptionsMenuEvent>();
	private TiWeakList<OnPrepareOptionsMenuEvent> onPrepareOptionsMenuListeners =
		new TiWeakList<OnPrepareOptionsMenuEvent>();
	private boolean sustainMode = false;
	private Intent launchIntent = null;
	private TiActionBarStyleHandler actionBarStyleHandler;
	private TiActivitySafeAreaMonitor safeAreaMonitor;

	public static class PermissionContextData
	{
		private final Integer requestCode;
		private final KrollObject context;
		private final KrollFunction callback;
		private final KrollPromise promise;

		public PermissionContextData(Integer requestCode, KrollFunction callback, KrollObject context,
									 KrollPromise promise)
		{
			this.requestCode = requestCode;
			this.callback = callback;
			this.context = context;
			this.promise = promise;
		}

		public Integer getRequestCode()
		{
			return requestCode;
		}

		public KrollFunction getCallback()
		{
			return callback;
		}

		public KrollObject getContext()
		{
			return context;
		}

		public KrollPromise getPromise()
		{
			return promise;
		}
	}

	private static ConcurrentHashMap<Integer, PermissionContextData> callbackDataByPermission =
		new ConcurrentHashMap<Integer, PermissionContextData>();

	protected View layout;
	protected TiActivitySupportHelper supportHelper;
	protected int supportHelperId = -1;
	protected TiWindowProxy window;
	protected TiViewProxy view;
	protected ActivityProxy activityProxy;
	protected TiWeakList<ConfigurationChangedListener> configChangedListeners =
		new TiWeakList<ConfigurationChangedListener>();
	protected TiMenuSupport menuHelper;
	protected Messenger messenger;
	protected int msgActivityCreatedId = -1;
	protected int msgId = -1;
	//Storing the activity's dialogs and their persistence
	private CopyOnWriteArrayList<DialogWrapper> dialogs = new CopyOnWriteArrayList<DialogWrapper>();

	public TiWindowProxy lwWindow;
	public boolean isResumed = false;

	public static boolean canFinishRoot = true;

	private boolean overridenLayout;

	public static class DialogWrapper
	{
		boolean isPersistent;
		Dialog dialog;

		WeakReference<TiBaseActivity> dialogActivity;

		public DialogWrapper(Dialog d, boolean persistent, WeakReference<TiBaseActivity> activity)
		{
			isPersistent = persistent;
			dialog = d;
			dialogActivity = activity;
		}

		public TiBaseActivity getActivity()
		{
			if (dialogActivity == null) {
				return null;
			} else {
				return dialogActivity.get();
			}
		}

		public void setActivity(WeakReference<TiBaseActivity> da)
		{
			dialogActivity = da;
		}

		public Dialog getDialog()
		{
			return dialog;
		}

		public void setDialog(Dialog d)
		{
			dialog = d;
		}

		public void release()
		{
			dialog = null;
			dialogActivity = null;
		}

		public boolean getPersistent()
		{
			return isPersistent;
		}

		public void setPersistent(boolean p)
		{
			isPersistent = p;
		}
	}

	public static interface ConfigurationChangedListener {
		public void onConfigurationChanged(TiBaseActivity activity, Configuration newConfig);
	}

	/**
	 * @return the instance of TiApplication.
	 */
	public TiApplication getTiApp()
	{
		return (TiApplication) getApplication();
	}

	/**
	 * @return the window proxy associated with this activity.
	 */
	public TiWindowProxy getWindowProxy()
	{
		return this.window;
	}

	/**
	 * Sets the window proxy.
	 * @param proxy
	 */
	public void setWindowProxy(TiWindowProxy proxy)
	{
		this.window = proxy;
	}

	/**
	 * Sets the proxy for our layout (used for post layout event)
	 *
	 * @param proxy
	 */
	public void setLayoutProxy(TiViewProxy proxy)
	{
		if (layout instanceof TiCompositeLayout) {
			((TiCompositeLayout) layout).setProxy(proxy);
		}
	}

	/**
	 * Sets the view proxy.
	 * @param proxy
	 */
	public void setViewProxy(TiViewProxy proxy)
	{
		this.view = proxy;
	}

	/**
	 * @return activity proxy associated with this activity.
	 */
	public ActivityProxy getActivityProxy()
	{
		return activityProxy;
	}

	public void addDialog(DialogWrapper d)
	{
		if ((d != null) && !dialogs.contains(d)) {
			dialogs.add(d);
		}
	}

	public void removeDialog(Dialog d)
	{
		for (int i = 0; i < dialogs.size(); i++) {
			DialogWrapper p = dialogs.get(i);
			if (p.getDialog().equals(d)) {
				p.release();
				dialogs.remove(i);
				return;
			}
		}
	}
	public void setActivityProxy(ActivityProxy proxy)
	{
		this.activityProxy = proxy;
	}

	/**
	 * @return the activity's current layout.
	 */
	public View getLayout()
	{
		return layout;
	}

	public void setLayout(View layout)
	{
		this.layout = layout;
	}

	public void addConfigurationChangedListener(ConfigurationChangedListener listener)
	{
		configChangedListeners.add(new WeakReference<ConfigurationChangedListener>(listener));
	}

	public void removeConfigurationChangedListener(ConfigurationChangedListener listener)
	{
		configChangedListeners.remove(listener);
	}

	protected boolean getIntentBoolean(String property, boolean defaultValue)
	{
		Intent intent = getIntent();
		if (intent != null) {
			if (intent.hasExtra(property)) {
				return intent.getBooleanExtra(property, defaultValue);
			}
		}

		return defaultValue;
	}

	protected int getIntentInt(String property, int defaultValue)
	{
		Intent intent = getIntent();
		if (intent != null) {
			if (intent.hasExtra(property)) {
				return intent.getIntExtra(property, defaultValue);
			}
		}

		return defaultValue;
	}

	protected String getIntentString(String property, String defaultValue)
	{
		Intent intent = getIntent();
		if (intent != null) {
			if (intent.hasExtra(property)) {
				return intent.getStringExtra(property);
			}
		}

		return defaultValue;
	}

	protected void updateTitle()
	{
		if (window == null)
			return;

		if (window.hasProperty(TiC.PROPERTY_TITLE)) {
			String oldTitle = (String) getTitle();
			String newTitle = TiConvert.toString(window.getProperty(TiC.PROPERTY_TITLE));

			if (oldTitle == null) {
				oldTitle = "";
			}

			if (newTitle == null) {
				newTitle = "";
			}

			if (!newTitle.equals(oldTitle)) {
				final String fnewTitle = newTitle;
				runOnUiThread(new Runnable() {
					public void run()
					{
						setTitle(fnewTitle);
					}
				});
			}
		}
	}

	// Subclasses can override to provide a custom layout
	protected View createLayout()
	{
		// Set up the view's layout.
		LayoutArrangement arrangement = LayoutArrangement.DEFAULT;
		String layoutFromIntent = getIntentString(TiC.INTENT_PROPERTY_LAYOUT, "");
		if (layoutFromIntent.equals(TiC.LAYOUT_HORIZONTAL)) {
			arrangement = LayoutArrangement.HORIZONTAL;
		} else if (layoutFromIntent.equals(TiC.LAYOUT_VERTICAL)) {
			arrangement = LayoutArrangement.VERTICAL;
		}

		// Create the root view to be used by the activity.
		// - Set the layout's proxy to null for now. Will be set later via setWindowProxy().
		// - Make it focusable so Android won't auto-focus on the first focusable child view in the window.
		//   This makes it match iOS' behavior where no child view has the focus when a window is opened.
		TiCompositeLayout compositeLayout = new TiCompositeLayout(this, arrangement, null);
		compositeLayout.setFocusable(true);
		compositeLayout.setFocusableInTouchMode(true);
		compositeLayout.setDescendantFocusability(TiCompositeLayout.FOCUS_BEFORE_DESCENDANTS);
		return compositeLayout;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults)
	{
		if (!callbackDataByPermission.isEmpty()) {
			handlePermissionRequestResult(requestCode, permissions, grantResults);
		}
	}

	private void handlePermissionRequestResult(Integer requestCode, String[] permissions, int[] grantResults)
	{
		PermissionContextData cbd = callbackDataByPermission.get(requestCode);
		if (cbd == null) {
			return;
		}

		String deniedPermissions = "";
		for (int i = 0; i < grantResults.length; ++i) {
			if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
				if (deniedPermissions.isEmpty()) {
					deniedPermissions = permissions[i];
				} else {
					deniedPermissions = deniedPermissions + ", " + permissions[i];
				}
			}
		}

		KrollPromise promise = cbd.getPromise();
		KrollDict response = new KrollDict();

		if (deniedPermissions.isEmpty()) {
			response.putCodeAndMessage(0, null);
			promise.resolve(response);
		} else {
			String message = "Permission(s) denied: " + deniedPermissions;
			response.putCodeAndMessage(-1, message);
			promise.reject(response);
		}

		KrollFunction callback = cbd.getCallback();
		if (callback != null) {
			KrollObject context = cbd.getContext();
			if (context == null) {
				Log.w(TAG, "Permission callback context object is null");
			}
			callback.callAsync(context, response);
		}
	}

	/**
	 * register permission request result callback for activity
	 *
	 * @param requestCode request code (8 Bit) to associate callback with request
	 * @param callback callback function which receives a KrollDict with success,
	 *                 code, optional message and requestCode
	 * @param context KrollObject as required by async callback pattern
	 */
	public static void registerPermissionRequestCallback(Integer requestCode, KrollFunction callback,
														 KrollObject context, KrollPromise promise)
	{
		callbackDataByPermission.put(requestCode, new PermissionContextData(requestCode, callback, context, promise));
	}

	protected void setFullscreen(boolean fullscreen)
	{
		if (fullscreen) {
			//getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
			View decorView = getWindow().getDecorView();
			// Hide both the navigation bar and the status bar.
			// SYSTEM_UI_FLAG_FULLSCREEN is only available on Android 4.1 and higher, but as
			// a general rule, you should design your app to hide the status bar whenever you
			// hide the navigation bar.
			int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
			decorView.setSystemUiVisibility(uiOptions);
		}
	}

	// Subclasses can override to handle post-creation (but pre-message fire) logic
	@SuppressWarnings("deprecation")
	protected void windowCreated(Bundle savedInstanceState)
	{
		boolean fullscreen = getIntentBoolean(TiC.PROPERTY_FULLSCREEN, false);
		boolean modal = getIntentBoolean(TiC.PROPERTY_MODAL, false);
		int softInputMode = getIntentInt(TiC.PROPERTY_WINDOW_SOFT_INPUT_MODE, -1);
		int windowFlags = getIntentInt(TiC.PROPERTY_WINDOW_FLAGS, 0);
		boolean hasSoftInputMode = softInputMode != -1;

		setFullscreen(fullscreen);

		// Add additional window flags to better handle fullscreen support on devices with notches.
		{
			// Fetch flags.
			int uiFlags = getWindow().getDecorView().getSystemUiVisibility();
			int allWindowFlags = windowFlags | getWindow().getAttributes().flags;

			// If status bar is to be hidden, then we must also set the translucent status bar flag
			// or else devices with a notch will show a black bar where the status bar used to be.
			boolean isHidingStatusBar = (allWindowFlags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0;
			isHidingStatusBar |= (uiFlags & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0;
			isHidingStatusBar |= (uiFlags & View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN) != 0;
			if (isHidingStatusBar) {
				windowFlags |= WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;
			}

			// If navigation bar is to be hidden, then we must also set its translucent flag
			// or else devices with a notch will show a black bar where the navigation bar used to be.
			if ((uiFlags & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0) {
				windowFlags |= WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION;
			}
		}

		// Always allow screen cutouts/notches (such as the camera) to overlap window.
		// Note: This won't overlap window's inner contents unless we call setFitsSystemWindows(true) down below,
		//       which is only enabled when Titanium's "extendSafeArea" property is set true.
		if (Build.VERSION.SDK_INT >= 28) {
			WindowManager.LayoutParams params = getWindow().getAttributes();
			params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
			getWindow().setAttributes(params);
		}

		// Add the flags provided via property 'windowFlags'.
		if (windowFlags > 0) {
			getWindow().addFlags(windowFlags);
		}

		// Remove translucent StatusBar/NavigationBar flags if window is not set up to extend beneath them.
		// Not doing so will cause window to stretch beneath them anyways, but will fail to render there.
		if (this.layout.getFitsSystemWindows() && !(this instanceof TiLaunchActivity)) {
			int mask = WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;
			mask |= WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION;
			if ((getWindow().getAttributes().flags & mask) != 0) {
				String message = "You cannot use a translucent status bar or navigation bar unless you "
								 + "set the window's '" + TiC.PROPERTY_EXTEND_SAFE_AREA + "' property to true.";
				Log.w(TAG, message);
				getWindow().clearFlags(mask);
			}
		}

		// Update system UI flags with based on currently assigned translucency flags.
		{
			int systemUIFlags = 0;
			int allWindowFlags = getWindow().getAttributes().flags;
			if ((allWindowFlags & WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS) != 0) {
				systemUIFlags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
			}
			if ((allWindowFlags & WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION) != 0) {
				systemUIFlags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
			}
			if (systemUIFlags != 0) {
				systemUIFlags |= getWindow().getDecorView().getSystemUiVisibility();
				getWindow().getDecorView().setSystemUiVisibility(systemUIFlags);
			}
		}

		if (modal) {
			if (Build.VERSION.SDK_INT < TiC.API_LEVEL_ICE_CREAM_SANDWICH) {
				// This flag is deprecated in API 14. On ICS, the background is not blurred but straight black.
				getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
			}
		}

		if (hasSoftInputMode) {
			Log.d(TAG, "windowSoftInputMode: " + softInputMode, Log.DEBUG_MODE);
			getWindow().setSoftInputMode(softInputMode);
		}

		// If this activity was created by a Window/TabGroup proxy, then give it this activity's reference.
		int windowId = getIntentInt(TiC.INTENT_PROPERTY_WINDOW_ID, TiActivityWindows.INVALID_WINDOW_ID);
		if (windowId != TiActivityWindows.INVALID_WINDOW_ID) {
			if (TiActivityWindows.hasWindow(windowId)) {
				// Pass this activity to the proxy so that it can add views to it.
				TiActivityWindows.windowCreated(this, windowId, savedInstanceState);
			} else {
				// This activity's assigned proxy was not found.
				// This happens when proxy has been closed before activity was created. Destroy this activity.
				finish();
			}
		}
	}

	// Record if user has set a content view manually from hyperloop code during require of app.js!
	@Override
	public void setContentView(View view)
	{
		overridenLayout = true;
		super.setContentView(view);
	}

	@Override
	public void setContentView(int layoutResID)
	{
		overridenLayout = true;
		super.setContentView(layoutResID);
	}

	@Override
	public void setContentView(View view, LayoutParams params)
	{
		overridenLayout = true;
		super.setContentView(view, params);
	}

	@Override
	protected void attachBaseContext(Context newBase)
	{
		super.attachBaseContext(TiLocaleManager.getLocalizedContext(newBase));
	}

	@Override
	/**
	 * When the activity is created, this method adds it to the activity stack and
	 * fires a javascript 'create' event.
	 * @param savedInstanceState Bundle of saved data.
	 */
	protected void onCreate(Bundle savedInstanceState)
	{
		Log.d(TAG, "Activity " + this + " onCreate", Log.DEBUG_MODE);

		this.inForeground = true;
		this.launchIntent = getIntent();
		this.safeAreaMonitor = new TiActivitySafeAreaMonitor(this);

		TiApplication tiApp = getTiApp();
		TiApplication.addToActivityStack(this);

		// Increment the Titanium activity reference count. To be decremented in onDestroy() method.
		// Titanium's JavaScript runtime is created when we have at least 1 activity and destroyed when we have 0.
		KrollRuntime.incrementActivityRefCount();

		// We must create activity proxy after incrementing the activity reference count above.
		// This is because proxy needs the JS runtime to exist when created.
		this.activityProxy = new ActivityProxy(this);

		Intent intent = this.launchIntent;
		if (intent != null) {
			if (intent.hasExtra(TiC.INTENT_PROPERTY_MESSENGER)) {
				messenger = (Messenger) intent.getParcelableExtra(TiC.INTENT_PROPERTY_MESSENGER);
				msgActivityCreatedId = intent.getIntExtra(TiC.INTENT_PROPERTY_MSG_ACTIVITY_CREATED_ID, -1);
				msgId = intent.getIntExtra(TiC.INTENT_PROPERTY_MSG_ID, -1);
			}

			if (intent.hasExtra(TiC.PROPERTY_WINDOW_PIXEL_FORMAT)) {
				getWindow().setFormat(intent.getIntExtra(TiC.PROPERTY_WINDOW_PIXEL_FORMAT, PixelFormat.UNKNOWN));
			}
		}

		// Doing this on every create in case the activity is externally created.
		TiPlatformHelper.getInstance().intializeDisplayMetrics(this);

		// Create the root content layout, if not done already.
		if (layout == null) {
			layout = createLayout();
		}

		// Extend window's view under screen insets, if requested.
		boolean extendSafeArea = false;
		if (intent != null) {
			extendSafeArea = intent.getBooleanExtra(TiC.PROPERTY_EXTEND_SAFE_AREA, false);
		}
		layout.setFitsSystemWindows(!extendSafeArea);

		// Enable/disable timer used to turn off the screen if idle.
		if ((intent != null) && intent.hasExtra(TiC.PROPERTY_KEEP_SCREEN_ON)) {
			boolean keepScreenOn = intent.getBooleanExtra(TiC.PROPERTY_KEEP_SCREEN_ON, layout.getKeepScreenOn());
			layout.setKeepScreenOn(keepScreenOn);
		}

		// Set the theme of the activity before calling super.onCreate().
		// On 2.3 devices, it does not work if the theme is set after super.onCreate.
		int theme = getIntentInt(TiC.PROPERTY_THEME, -1);
		if (theme != -1) {
			this.setTheme(theme);
		}

		// Set ActionBar into split mode must be done before the decor view has been created
		// we need to do this before calling super.onCreate()
		if (intent != null && intent.hasExtra(TiC.PROPERTY_SPLIT_ACTIONBAR)) {
			getWindow().setUiOptions(ActivityInfo.UIOPTION_SPLIT_ACTION_BAR_WHEN_NARROW);
		}

		// we only want to set the current activity for good in the resume state but we need it right now.
		// save off the existing current activity, set ourselves to be the new current activity temporarily
		// so we don't run into problems when we give the proxy the event
		Activity tempCurrentActivity = tiApp.getCurrentActivity();
		tiApp.setCurrentActivity(this, this);

		// we need to set window features before calling onCreate
		this.requestWindowFeature(Window.FEATURE_PROGRESS);
		this.requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			this.requestWindowFeature(Window.FEATURE_ACTIVITY_TRANSITIONS);
		}
		super.onCreate(savedInstanceState);

		// If activity is using Google's default ActionBar, then the below will return an ActionBar style handler
		// intended to be called by onConfigurationChanged() which will resize its title bar and font.
		// Note: We need to do this since we override "configChanges" in the "AndroidManifest.xml".
		//       Default ActionBar height is typically 56dp for portrait and 48dp for landscape.
		this.actionBarStyleHandler = TiActionBarStyleHandler.from(this);

		// If Google's ActionBar is being used, then add it to the top inset height. (Exclude from safe-area.)
		// Note: If a toolbar is passed to AppCompatActivity.setSupportActionBar(), then ActionBar is a wrapper
		//       around that toolbar under our content view and is included in the safe-area.
		this.safeAreaMonitor.setActionBarAddedAsInset(this.actionBarStyleHandler != null);

		// Start handling safe-area inset changes.
		this.safeAreaMonitor.setOnChangedListener(new TiActivitySafeAreaMonitor.OnChangedListener() {
			@Override
			public void onChanged(TiActivitySafeAreaMonitor monitor)
			{
				TiWindowProxy windowProxy = TiBaseActivity.this.window;
				if (windowProxy != null) {
					windowProxy.fireSafeAreaChangedEvent();
				}
			}
		});
		this.safeAreaMonitor.start();

		try {
			windowCreated(savedInstanceState);
		} catch (Throwable t) {
			Thread.getDefaultUncaughtExceptionHandler().uncaughtException(null, t);
		}

		// set the current activity back to what it was originally
		tiApp.setCurrentActivity(this, tempCurrentActivity);

		// If user changed the layout during app.js load, keep that
		if (!overridenLayout) {
			super.setContentView(layout);
		}

		// Set the title of the activity after setContentView.
		// On 2.3 devices, if the title is set before setContentView, the app will crash when a NoTitleBar theme is used.
		updateTitle();

		sendMessage(msgActivityCreatedId);
		// for backwards compatibility
		sendMessage(msgId);

		// store off the original orientation for the activity set in the AndroidManifest.xml
		// for later use
		originalOrientationMode = getRequestedOrientation();

		if (window != null) {
			window.onWindowActivityCreated();
		}
		if (activityProxy != null) {
			dispatchCallback(TiC.PROPERTY_ON_CREATE, null);
			activityProxy.fireEvent(TiC.EVENT_CREATE, null);
		}
		synchronized (lifecycleListeners.synchronizedList())
		{
			for (OnLifecycleEvent listener : lifecycleListeners.nonNull()) {
				try {
					TiLifecycle.fireLifecycleEvent(this, listener, savedInstanceState, TiLifecycle.LIFECYCLE_ON_CREATE);

				} catch (Throwable t) {
					Log.e(TAG, "Error dispatching lifecycle event: " + t.getMessage(), t);
				}
			}
		}
		setCustomActionBar();
	}

	private void setCustomActionBar()
	{
		if (activityProxy.hasProperty(TiC.PROPERTY_SUPPORT_TOOLBAR)) {
			try {
				this.setSupportActionBar(
					((Toolbar) ((TiToolbarProxy) activityProxy.getProperty(TiC.PROPERTY_SUPPORT_TOOLBAR))
						 .getToolbarInstance()));
			} catch (RuntimeException e) {
				Log.e(
					TAG,
					"Attempting to use Toolbar as ActionBar without disabling the default ActionBar in the current theme.\n"
						+ "You must set 'windowActionBar' to false in your current theme. Or use one of the following themes:\n"
						+ " - Theme.Titanium\n - Theme.AppCompat.Translucent.NoTitleBar\n - Theme.AppCompat.NoTitleBar\n"
						+ "Which have ActionBar disabled by default.");
				TiApplication.terminateActivityStack();
				finish();
			}
		}
	}

	/**
	 * Gets the intent that originally created and launched this activity.
	 * <p>
	 * This intent is assigned within this activity's onCreate() method.
	 * It is intended to be used to resume this activity via startActivity().
	 * <p>
	 * The returned intent will not change when onNewIntent() or setIntent() has been called. Those methods
	 * are typically called when tapping a notification or when a custom URL scheme has been invoked.
	 * The activity's getIntent() method will return the updated intent, if changed.
	 * @return The intent used to create/launch this activity.
	 */
	public Intent getLaunchIntent()
	{
		return this.launchIntent;
	}

	public int getOriginalOrientationMode()
	{
		return originalOrientationMode;
	}

	public boolean isInForeground()
	{
		return inForeground;
	}

	public boolean isDestroyed()
	{
		return onDestroyFired;
	}

	protected void sendMessage(final int msgId)
	{
		if (messenger == null || msgId == -1) {
			return;
		}

		// fire an async message on this thread's queue
		// so we don't block onCreate() from returning
		TiMessenger.postOnMain(new Runnable() {
			public void run()
			{
				handleSendMessage(msgId);
			}
		});
	}

	protected void handleSendMessage(int messageId)
	{
		try {
			Message message = TiMessenger.getMainMessenger().getHandler().obtainMessage(messageId, this);
			messenger.send(message);

		} catch (RemoteException e) {
			Log.e(TAG, "Unable to message creator. finishing.", e);
			finish();

		} catch (RuntimeException e) {
			Log.e(TAG, "Unable to message creator. finishing.", e);
			finish();
		}
	}

	protected TiActivitySupportHelper getSupportHelper()
	{
		if (supportHelper == null) {
			this.supportHelper = new TiActivitySupportHelper(this);
			// Register the supportHelper so we can get it back when the activity is recovered from force-quitting.
			supportHelperId = TiActivitySupportHelpers.addSupportHelper(supportHelper);
		}

		return supportHelper;
	}

	// Activity Support
	public int getUniqueResultCode()
	{
		return getSupportHelper().getUniqueResultCode();
	}

	/**
	 * See TiActivitySupport.launchActivityForResult for more details.
	 */
	public void launchActivityForResult(Intent intent, int code, TiActivityResultHandler resultHandler)
	{
		getSupportHelper().launchActivityForResult(intent, code, resultHandler);
	}

	/**
	 * See TiActivitySupport.launchIntentSenderForResult for more details.
	 */
	public void launchIntentSenderForResult(IntentSender intent, int requestCode, Intent fillInIntent, int flagsMask,
											int flagsValues, int extraFlags, Bundle options,
											TiActivityResultHandler resultHandler)
	{
		getSupportHelper().launchIntentSenderForResult(intent, requestCode, fillInIntent, flagsMask, flagsValues,
													   extraFlags, options, resultHandler);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		synchronized (onActivityResultListeners.synchronizedList())
		{
			for (OnActivityResultEvent listener : onActivityResultListeners.nonNull()) {
				try {
					TiLifecycle.fireOnActivityResultEvent(this, listener, requestCode, resultCode, data);
				} catch (Throwable t) {
					Log.e(TAG, "Error dispatching onActivityResult event: " + t.getMessage(), t);
				}
			}
		}
		getSupportHelper().onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void onBackPressed()
	{
		// Notify all listener that the back button was pressed.
		synchronized (interceptOnBackPressedListeners.synchronizedList())
		{
			for (interceptOnBackPressedEvent listener : interceptOnBackPressedListeners.nonNull()) {
				try {
					if (listener.interceptOnBackPressed()) {
						return;
					}

				} catch (Throwable t) {
					Log.e(TAG, "Error dispatching interceptOnBackPressed event: " + t.getMessage(), t);
				}
			}
		}

		// Let the window proxy handle the back event first, if configured.
		if (this.window != null) {
			boolean hasBackEventHandler = false;

			// Fire an "androidback" event if a listener exists.
			if (this.window.hasListeners(TiC.EVENT_ANDROID_BACK)) {
				this.window.fireEvent(TiC.EVENT_ANDROID_BACK, null);
				hasBackEventHandler = true;
			}

			// Invoke the "onBack" property's callback if assigned.
			if (this.window.hasProperty(TiC.PROPERTY_ON_BACK) && (this.activityProxy != null)) {
				Object value = this.window.getProperty(TiC.PROPERTY_ON_BACK);
				if (value instanceof KrollFunction) {
					KrollFunction onBackCallback = (KrollFunction) value;
					onBackCallback.callAsync(activityProxy.getKrollObject(), new Object[] {});
					hasBackEventHandler = true;
				}
			}

			// Do not allow the system to handle back press if window proxy has an event handler.
			// In this case, the JS code must explicity close() or finish() the activity window itself.
			if (hasBackEventHandler) {
				return;
			}
		}

		// Handle app exit ourselves since the above window proxy did not handle the back event.
		boolean exitOnClose = (TiActivityWindows.getWindowCount() <= 1);
		if (this.window != null) {
			exitOnClose = TiConvert.toBoolean(this.window.getProperty(TiC.PROPERTY_EXIT_ON_CLOSE), exitOnClose);
		}
		if (exitOnClose) {
			// Destroy all remaining activitities, including root splash activity.
			Log.d(TAG, "onBackPressed: exit");
			finishAffinity();
			TiApplication.terminateActivityStack();
			return;
		} else if (TiActivityWindows.getWindowCount() <= 1) {
			// Don't destroy this activity if it's the last one left. Home-out instead.
			Log.d(TAG, "onBackPressed: suspend to background");
			moveTaskToBack(true);
			return;
		}

		// Allow the system to finish/destroy this activity.
		super.onBackPressed();
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event)
	{
		boolean handled = false;

		TiViewProxy window;
		if (this.window != null) {
			window = this.window;
		} else {
			window = this.view;
		}

		if (window == null) {
			return super.dispatchKeyEvent(event);
		}

		switch (event.getKeyCode()) {
			case KeyEvent.KEYCODE_BACK: {

				if (event.getAction() == KeyEvent.ACTION_UP) {
					String backEvent = "android:back";
					KrollProxy proxy = null;
					//android:back could be fired from a tabGroup window (activityProxy)
					//or hw window (window).This event is added specifically to the activity
					//proxy of a tab group in window.js
					if (activityProxy.hasListeners(backEvent)) {
						proxy = activityProxy;
					} else if (window.hasListeners(backEvent)) {
						proxy = window;
					}

					if (proxy != null) {
						proxy.fireEvent(backEvent, null);
						handled = true;
					}
				}
				break;
			}
			case KeyEvent.KEYCODE_CAMERA: {
				if (window.hasListeners(TiC.EVENT_ANDROID_CAMERA)) {
					if (event.getAction() == KeyEvent.ACTION_UP) {
						window.fireEvent(TiC.EVENT_ANDROID_CAMERA, null);
					}
					handled = true;
				}
				// TODO: Deprecate old event
				if (window.hasListeners("android:camera")) {
					if (event.getAction() == KeyEvent.ACTION_UP) {
						window.fireEvent("android:camera", null);
					}
					handled = true;
				}

				break;
			}
			case KeyEvent.KEYCODE_FOCUS: {
				if (window.hasListeners(TiC.EVENT_ANDROID_FOCUS)) {
					if (event.getAction() == KeyEvent.ACTION_UP) {
						window.fireEvent(TiC.EVENT_ANDROID_FOCUS, null);
					}
					handled = true;
				}
				// TODO: Deprecate old event
				if (window.hasListeners("android:focus")) {
					if (event.getAction() == KeyEvent.ACTION_UP) {
						window.fireEvent("android:focus", null);
					}
					handled = true;
				}

				break;
			}
			case KeyEvent.KEYCODE_SEARCH: {
				if (window.hasListeners(TiC.EVENT_ANDROID_SEARCH)) {
					if (event.getAction() == KeyEvent.ACTION_UP) {
						window.fireEvent(TiC.EVENT_ANDROID_SEARCH, null);
					}
					handled = true;
				}
				// TODO: Deprecate old event
				if (window.hasListeners("android:search")) {
					if (event.getAction() == KeyEvent.ACTION_UP) {
						window.fireEvent("android:search", null);
					}
					handled = true;
				}

				break;
			}
			case KeyEvent.KEYCODE_VOLUME_UP: {
				if (window.hasListeners(TiC.EVENT_ANDROID_VOLUP)) {
					if (event.getAction() == KeyEvent.ACTION_UP) {
						window.fireEvent(TiC.EVENT_ANDROID_VOLUP, null);
					}
					handled = true;
				}
				// TODO: Deprecate old event
				if (window.hasListeners("android:volup")) {
					if (event.getAction() == KeyEvent.ACTION_UP) {
						window.fireEvent("android:volup", null);
					}
					handled = true;
				}

				break;
			}
			case KeyEvent.KEYCODE_VOLUME_DOWN: {
				if (window.hasListeners(TiC.EVENT_ANDROID_VOLDOWN)) {
					if (event.getAction() == KeyEvent.ACTION_UP) {
						window.fireEvent(TiC.EVENT_ANDROID_VOLDOWN, null);
					}
					handled = true;
				}
				// TODO: Deprecate old event
				if (window.hasListeners("android:voldown")) {
					if (event.getAction() == KeyEvent.ACTION_UP) {
						window.fireEvent("android:voldown", null);
					}
					handled = true;
				}

				break;
			}
		}

		if (!handled) {
			handled = super.dispatchKeyEvent(event);
		}

		return handled;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		if (activityProxy == null) {
			return false;
		}

		boolean listenerExists = false;
		synchronized (onCreateOptionsMenuListeners.synchronizedList())
		{
			for (OnCreateOptionsMenuEvent listener : onCreateOptionsMenuListeners.nonNull()) {
				try {
					listenerExists = true;
					TiLifecycle.fireOnCreateOptionsMenuEvent(this, listener, menu);
				} catch (Throwable t) {
					Log.e(TAG, "Error dispatching OnCreateOptionsMenuEvent: " + t.getMessage(), t);
				}
			}
		}

		if (menuHelper == null) {
			menuHelper = new TiMenuSupport(activityProxy);
		}

		return menuHelper.onCreateOptionsMenu(super.onCreateOptionsMenu(menu) || listenerExists, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId()) {
			case android.R.id.home:
				if (activityProxy != null) {
					ActionBarProxy actionBarProxy = activityProxy.getActionBar();
					if (actionBarProxy != null) {
						KrollFunction onHomeIconItemSelected =
							(KrollFunction) actionBarProxy.getProperty(TiC.PROPERTY_ON_HOME_ICON_ITEM_SELECTED);
						KrollDict event = new KrollDict();
						event.put(TiC.EVENT_PROPERTY_SOURCE, actionBarProxy);
						if (onHomeIconItemSelected != null) {
							onHomeIconItemSelected.call(activityProxy.getKrollObject(), new Object[] { event });

							// handle NavigationWindow back press
						} else if (window.getNavigationWindow() != null) {
							onBackPressed();
						}
					}
				}
				return true;
			default:
				return menuHelper.onOptionsItemSelected(item);
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		boolean listenerExists = false;
		synchronized (onPrepareOptionsMenuListeners.synchronizedList())
		{
			for (OnPrepareOptionsMenuEvent listener : onPrepareOptionsMenuListeners.nonNull()) {
				try {
					listenerExists = true;
					TiLifecycle.fireOnPrepareOptionsMenuEvent(this, listener, menu);
				} catch (Throwable t) {
					Log.e(TAG, "Error dispatching OnPrepareOptionsMenuEvent: " + t.getMessage(), t);
				}
			}
		}
		return menuHelper.onPrepareOptionsMenu(super.onPrepareOptionsMenu(menu) || listenerExists, menu);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);

		// Update ActionBar height and font size, if needed.
		// Handler will only be null if activity was set up without a title bar.
		if (this.actionBarStyleHandler != null) {
			this.actionBarStyleHandler.onConfigurationChanged(newConfig);
		}

		// Notify all listener of this configuration change.
		for (WeakReference<ConfigurationChangedListener> listener : configChangedListeners) {
			if (listener.get() != null) {
				listener.get().onConfigurationChanged(this, newConfig);
			}
		}
	}

	@Override
	protected void onNewIntent(Intent intent)
	{
		super.onNewIntent(intent);
		Log.d(TAG, "Activity " + this + " onNewIntent", Log.DEBUG_MODE);

		// Store the new intent.
		setIntent(intent);

		// Update proxy's "intent" property and fire a "newintent" event.
		if (this.activityProxy != null) {
			this.activityProxy.onNewIntent(intent);
		}
	}

	public void addOnLifecycleEventListener(OnLifecycleEvent listener)
	{
		lifecycleListeners.add(new WeakReference<OnLifecycleEvent>(listener));
	}

	public void addOnInstanceStateEventListener(OnInstanceStateEvent listener)
	{
		instanceStateListeners.add(new WeakReference<OnInstanceStateEvent>(listener));
	}

	public void addOnWindowFocusChangedEventListener(OnWindowFocusChangedEvent listener)
	{
		windowFocusChangedListeners.add(new WeakReference<OnWindowFocusChangedEvent>(listener));
	}

	public void addInterceptOnBackPressedEventListener(interceptOnBackPressedEvent listener)
	{
		interceptOnBackPressedListeners.add(new WeakReference<interceptOnBackPressedEvent>(listener));
	}

	public void addOnActivityResultListener(OnActivityResultEvent listener)
	{
		onActivityResultListeners.add(new WeakReference<OnActivityResultEvent>(listener));
	}

	public void addOnCreateOptionsMenuEventListener(OnCreateOptionsMenuEvent listener)
	{
		onCreateOptionsMenuListeners.add(new WeakReference<OnCreateOptionsMenuEvent>(listener));
	}

	public void addOnPrepareOptionsMenuEventListener(OnPrepareOptionsMenuEvent listener)
	{
		onPrepareOptionsMenuListeners.add(new WeakReference<OnPrepareOptionsMenuEvent>(listener));
	}

	public void removeOnLifecycleEventListener(OnLifecycleEvent listener)
	{
		// TODO stub
	}

	private void dispatchCallback(String propertyName, KrollDict data)
	{
		// Do not continue if activity proxy has been released.
		if (this.activityProxy == null) {
			return;
		}

		// Invoke callback assigned to given property name.
		// Note: This must be done synchronously. Especially for "onDestroy" when exiting the app.
		try {
			if (data == null) {
				data = new KrollDict();
			}
			data.put(TiC.EVENT_PROPERTY_SOURCE, this.activityProxy);
			this.activityProxy.callPropertySync(propertyName, new Object[] { data });
		} catch (Throwable ex) {
			Thread.getDefaultUncaughtExceptionHandler().uncaughtException(null, ex);
		}
	}

	private void releaseDialogs(boolean finish)
	{
		//clean up dialogs when activity is pausing or finishing
		for (Iterator<DialogWrapper> iter = dialogs.iterator(); iter.hasNext();) {
			DialogWrapper p = iter.next();
			Dialog dialog = p.getDialog();
			boolean persistent = p.getPersistent();
			//if the activity is pausing but not finishing, clean up dialogs only if
			//they are non-persistent
			if (finish || !persistent) {
				if (dialog != null && dialog.isShowing()) {
					try {
						dialog.dismiss();
					} catch (Exception ex) {
						Log.e(TAG, "Failed to hide dialog.", ex);
					}
				}
				dialogs.remove(p);
			}
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus)
	{
		synchronized (windowFocusChangedListeners.synchronizedList())
		{
			for (OnWindowFocusChangedEvent listener : windowFocusChangedListeners.nonNull()) {
				try {
					listener.onWindowFocusChanged(hasFocus);

				} catch (Throwable t) {
					Log.e(TAG, "Error dispatching onWindowFocusChanged event: " + t.getMessage(), t);
				}
			}
		}
		super.onWindowFocusChanged(hasFocus);
	}

	@Override
	/**
	 * When this activity pauses, this method sets the current activity to null, fires a javascript 'pause' event,
	 * and if the activity is finishing, remove all dialogs associated with it.
	 */
	protected void onPause()
	{
		inForeground = false;
		dispatchCallback(TiC.PROPERTY_ON_PAUSE, null);
		super.onPause();
		isResumed = false;

		Log.d(TAG, "Activity " + this + " onPause", Log.DEBUG_MODE);

		if (this.window != null) {
			this.window.onWindowFocusChange(false);
		}

		TiApplication.updateActivityTransitionState(true);
		TiApplication tiApp = getTiApp();
		tiApp.setCurrentActivity(this, null);
		TiUIHelper.showSoftKeyboard(getWindow().getDecorView(), false);

		if (this.isFinishing()) {
			releaseDialogs(true);
		} else {
			//release non-persistent dialogs when activity hides
			releaseDialogs(false);
		}

		if (activityProxy != null) {
			activityProxy.fireEvent(TiC.EVENT_PAUSE, null);
		}

		synchronized (lifecycleListeners.synchronizedList())
		{
			for (OnLifecycleEvent listener : lifecycleListeners.nonNull()) {
				try {
					TiLifecycle.fireLifecycleEvent(this, listener, TiLifecycle.LIFECYCLE_ON_PAUSE);

				} catch (Throwable t) {
					Log.e(TAG, "Error dispatching lifecycle event: " + t.getMessage(), t);
				}
			}
		}
	}

	@Override
	/**
	 * When the activity resumes, this method updates the current activity to this and fires a javascript
	 * 'resume' event.
	 */
	protected void onResume()
	{
		inForeground = true;
		dispatchCallback(TiC.PROPERTY_ON_RESUME, null);
		super.onResume();
		if (isFinishing()) {
			return;
		}

		Log.d(TAG, "Activity " + this + " onResume", Log.DEBUG_MODE);

		if (this.window != null) {
			this.window.onWindowFocusChange(true);
		}

		TiApplication tiApp = getTiApp();
		tiApp.setCurrentActivity(this, this);
		TiApplication.updateActivityTransitionState(false);

		if (activityProxy != null) {
			activityProxy.fireEvent(TiC.EVENT_RESUME, null);
		}

		synchronized (lifecycleListeners.synchronizedList())
		{
			for (OnLifecycleEvent listener : lifecycleListeners.nonNull()) {
				try {
					TiLifecycle.fireLifecycleEvent(this, listener, TiLifecycle.LIFECYCLE_ON_RESUME);

				} catch (Throwable t) {
					Log.e(TAG, "Error dispatching lifecycle event: " + t.getMessage(), t);
				}
			}
		}

		isResumed = true;
	}

	@Override
	/**
	 * When this activity starts, this method updates the current activity to this if necessary and
	 * fire javascript 'start' and 'focus' events. Focus events will only fire if
	 * the activity is not a tab activity.
	 */
	protected void onStart()
	{
		inForeground = true;
		dispatchCallback(TiC.PROPERTY_ON_START, null);
		super.onStart();
		if (isFinishing()) {
			return;
		}

		// Newer versions of Android appear to turn this on by default.
		// Turn if off until an activity indicator is shown.
		setProgressBarIndeterminateVisibility(false);

		Log.d(TAG, "Activity " + this + " onStart", Log.DEBUG_MODE);

		updateTitle();

		if (activityProxy != null) {
			// we only want to set the current activity for good in the resume state but we need it right now.
			// save off the existing current activity, set ourselves to be the new current activity temporarily
			// so we don't run into problems when we give the proxy the event
			TiApplication tiApp = getTiApp();
			Activity tempCurrentActivity = tiApp.getCurrentActivity();
			tiApp.setCurrentActivity(this, this);

			activityProxy.fireEvent(TiC.EVENT_START, null);

			// set the current activity back to what it was originally
			tiApp.setCurrentActivity(this, tempCurrentActivity);
		}

		synchronized (lifecycleListeners.synchronizedList())
		{
			for (OnLifecycleEvent listener : lifecycleListeners.nonNull()) {
				try {
					TiLifecycle.fireLifecycleEvent(this, listener, TiLifecycle.LIFECYCLE_ON_START);

				} catch (Throwable t) {
					Log.e(TAG, "Error dispatching lifecycle event: " + t.getMessage(), t);
				}
			}
		}
	}

	@Override
	/**
	 * When this activity stops, this method fires the javascript 'blur' and 'stop' events. Blur events will only fire
	 * if the activity is not a tab activity.
	 */
	protected void onStop()
	{
		inForeground = false;
		dispatchCallback(TiC.PROPERTY_ON_STOP, null);
		super.onStop();

		Log.d(TAG, "Activity " + this + " onStop", Log.DEBUG_MODE);

		if (activityProxy != null) {
			activityProxy.fireEvent(TiC.EVENT_STOP, null);
		}

		synchronized (lifecycleListeners.synchronizedList())
		{
			for (OnLifecycleEvent listener : lifecycleListeners.nonNull()) {
				try {
					TiLifecycle.fireLifecycleEvent(this, listener, TiLifecycle.LIFECYCLE_ON_STOP);

				} catch (Throwable t) {
					Log.e(TAG, "Error dispatching lifecycle event: " + t.getMessage(), t);
				}
			}
		}
	}

	@Override
	/**
	 * Called when the activity was moved from the background to the foreground.
	 * Will be called after onStop() and before onStart().
	 */
	protected void onRestart()
	{
		inForeground = true;
		dispatchCallback(TiC.PROPERTY_ON_RESTART, null);
		super.onRestart();

		Log.d(TAG, "Activity " + this + " onRestart", Log.DEBUG_MODE);

		if (activityProxy != null) {
			// we only want to set the current activity for good in the resume state but we need it right now.
			// save off the existing current activity, set ourselves to be the new current activity temporarily
			// so we don't run into problems when we give the proxy the event
			TiApplication tiApp = getTiApp();
			Activity tempCurrentActivity = tiApp.getCurrentActivity();
			tiApp.setCurrentActivity(this, this);

			activityProxy.fireEvent(TiC.EVENT_RESTART, null);

			// set the current activity back to what it was originally
			tiApp.setCurrentActivity(this, tempCurrentActivity);
		}
	}

	@Override
	/**
	 * When a key, touch, or trackball event is dispatched to the activity, this method fires the
	 * javascript 'userinteraction' event.
	 */
	public void onUserInteraction()
	{
		TiApplication.getInstance().fireAppEvent(TiC.EVENT_USER_INTERACTION, null);

		super.onUserInteraction();
	}

	@Override
	/**
	 * When the activity is about to go into the background as a result of user choice, this method fires the
	 * javascript 'userleavehint' event.
	 */
	protected void onUserLeaveHint()
	{
		Log.d(TAG, "Activity " + this + " onUserLeaveHint", Log.DEBUG_MODE);

		if (activityProxy != null) {
			activityProxy.fireEvent(TiC.EVENT_USER_LEAVE_HINT, null);
		}

		super.onUserLeaveHint();
	}

	@Override
	/**
	 * When this activity is destroyed, this method removes it from the activity stack, performs
	 * clean up, and fires javascript 'destroy' event.
	 */
	protected void onDestroy()
	{
		Log.d(TAG, "Activity " + this + " onDestroy", Log.DEBUG_MODE);
		dispatchCallback(TiC.PROPERTY_ON_DESTROY, null);

		// Flag that this activity is no longer in the foreground.
		this.inForeground = false;

		// Release the dialogs owned by this activity.
		releaseDialogs(true);

		// Stop listening for safe-area inset changes.
		if (this.safeAreaMonitor != null) {
			this.safeAreaMonitor.stop();
		}

		// Notify listeners that this activity is being destroyed.
		synchronized (lifecycleListeners.synchronizedList())
		{
			for (OnLifecycleEvent listener : lifecycleListeners.nonNull()) {
				try {
					TiLifecycle.fireLifecycleEvent(this, listener, TiLifecycle.LIFECYCLE_ON_DESTROY);

				} catch (Throwable t) {
					Log.e(TAG, "Error dispatching lifecycle event: " + t.getMessage(), t);
				}
			}
		}

		super.onDestroy();

		// "isFinishing" will return true if the Android OS won't restore this destroyed activity later.
		// This happens when finish() method is called of end-user back navigates out of the activity.
		// Note: Will breturn false if system intends to restore the activity later, which happens if
		//       system setting "Don't keep activities" is enabled or "Background process limit" was exceeded.
		boolean isFinishing = isFinishing();

		// If activities is finished (not coming back), then stop tracking the activity and remove from collection.
		if (isFinishing) {
			if (this.launchIntent != null) {
				int windowId =
					this.launchIntent.getIntExtra(TiC.INTENT_PROPERTY_WINDOW_ID, TiActivityWindows.INVALID_WINDOW_ID);
				TiActivityWindows.removeWindow(windowId);
			}
			TiActivitySupportHelpers.removeSupportHelper(supportHelperId);
		}

		// Invoke the Titanium activity proxy's "onDestroy" callback.
		fireOnDestroy();

		// Release proxy references and resources.
		if (layout instanceof TiCompositeLayout) {
			Log.d(TAG, "Layout cleanup.", Log.DEBUG_MODE);
			((TiCompositeLayout) layout).removeAllViews();
		}
		layout = null;
		if (view != null) {
			if (window != null) {
				view.releaseViews();
			} else {
				view.release();
			}
			view = null;
		}
		if (window != null) {
			window.closeFromActivity(isFinishing);
			window = null;
		}
		if (menuHelper != null) {
			menuHelper.destroy();
			menuHelper = null;
		}
		if (activityProxy != null) {
			activityProxy.release();
			activityProxy = null;
		}

		// Remove this activity from the app-wide Titanium UI stack.
		TiApplication.removeFromActivityStack(this);

		// Decrement the activity count. Once the counts hits zero, we'll terminate the JavaScript runtime.
		// Note: If "isFinishing" is false, then the Android OS is temporarily destroying this activity
		//       and intends to restore it later. We don't want to terminate the JS runtime in this case.
		//       This happens when "Don't keep activities" is enabled or "Background process limit" is exceeded.
		KrollRuntime.decrementActivityRefCount(isFinishing);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);

		// If the activity is forced to destroy by Android, save the supportHelperId so
		// we can get it back when the activity is recovered.
		if (!isFinishing() && supportHelper != null) {
			outState.putInt("supportHelperId", supportHelperId);
		}

		synchronized (instanceStateListeners.synchronizedList())
		{
			for (OnInstanceStateEvent listener : instanceStateListeners.nonNull()) {
				try {
					TiLifecycle.fireInstanceStateEvent(outState, listener, TiLifecycle.ON_SAVE_INSTANCE_STATE);
				} catch (Throwable t) {
					Log.e(TAG, "Error dispatching OnInstanceStateEvent: " + t.getMessage(), t);
				}
			}
		}
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState)
	{
		super.onRestoreInstanceState(savedInstanceState);

		if (savedInstanceState.containsKey("supportHelperId")) {
			supportHelperId = savedInstanceState.getInt("supportHelperId");
			supportHelper = TiActivitySupportHelpers.retrieveSupportHelper(this, supportHelperId);
			if (supportHelper == null) {
				Log.e(TAG, "Unable to retrieve the activity support helper.");
			}
		}
		synchronized (instanceStateListeners.synchronizedList())
		{
			for (OnInstanceStateEvent listener : instanceStateListeners.nonNull()) {
				try {
					TiLifecycle.fireInstanceStateEvent(savedInstanceState, listener,
													   TiLifecycle.ON_RESTORE_INSTANCE_STATE);
				} catch (Throwable t) {
					Log.e(TAG, "Error dispatching OnInstanceStateEvent: " + t.getMessage(), t);
				}
			}
		}
	}

	// called in order to ensure that the onDestroy call is only acted upon once.
	// should be called by any subclass
	protected void fireOnDestroy()
	{
		if (!onDestroyFired) {
			if (activityProxy != null) {
				activityProxy.fireEvent(TiC.EVENT_DESTROY, null);
			}
			onDestroyFired = true;
		}
	}

	private boolean shouldFinishRootActivity()
	{
		// Do not finish root activity if disabled globally. (Typically done when restarting LiveView.)
		if (TiBaseActivity.canFinishRoot == false) {
			return false;
		}

		// This method only applies to "Ti.UI.Window" based activities.
		// If this is the root activity, then let it do its default finish handling.
		if (this instanceof TiRootActivity) {
			return false;
		}

		// Determine if this activity's "Ti.UI.Window" reference is still in the global collection.
		// - Will not be in the collection if its close() method was called.
		// - Will be in collection when pressing Back button or finish() was called natively.
		boolean isTiWindowOpen = false;
		if (this.launchIntent != null) {
			int windowId =
				this.launchIntent.getIntExtra(TiC.INTENT_PROPERTY_WINDOW_ID, TiActivityWindows.INVALID_WINDOW_ID);
			if (windowId != TiActivityWindows.INVALID_WINDOW_ID) {
				isTiWindowOpen = TiActivityWindows.hasWindow(windowId);
			}
		}

		// If this is the last "Ti.UI.Window" activity, then exit by default unless "exitOnClose" property was set.
		boolean exitOnClose = (TiActivityWindows.getWindowCount() <= (isTiWindowOpen ? 1 : 0));
		if ((this.window != null) && this.window.hasProperty(TiC.PROPERTY_EXIT_ON_CLOSE)) {
			exitOnClose = TiConvert.toBoolean(this.window.getProperty(TiC.PROPERTY_EXIT_ON_CLOSE), exitOnClose);
		}
		return exitOnClose;
	}

	@Override
	public void finishAfterTransition()
	{
		// This is only supported on Android 5.0 and above. Do a normal finish on older OS versions.
		if (Build.VERSION.SDK_INT < 21) {
			finish();
			return;
		}

		// Remove this activity from the app-wide Titanium UI stack.
		TiApplication.removeFromActivityStack(this);

		// Finish this activity after its exit transition, if supported.
		super.finishAfterTransition();
	}

	@Override
	public void finish()
	{
		// Do not continue if already called.
		if (isFinishing()) {
			return;
		}

		// If the root activity that is hosting the JS runtime is being closed,
		// then close all Titanium child activities too.
		boolean isTiRootActivity = (this instanceof TiRootActivity);
		if (isTiRootActivity && (getTiApp().getRootActivity() == this)) {
			TiApplication.terminateActivityStack();
		}

		// Remove this activity from the app-wide Titanium UI stack.
		TiApplication.removeFromActivityStack(this);

		// Close this activity.
		super.finish();

		// If this is the 1st child activity below root activity, then close root activity too if configured.
		// Note: Setting Ti.UI.Window property "exitOnClose" to false will prevent this.
		if (!isTiRootActivity && shouldFinishRootActivity()) {
			TiRootActivity rootActivity = getTiApp().getRootActivity();
			if (rootActivity != null) {
				// Destroy the root activity. This in turn will destroy its child activities.
				rootActivity.finish();
			} else if (TiRootActivity.isScriptRunning()) {
				// Root activity not found, but its script is still running.
				// Can happen when "Don't keep activities" is enabled. Our only option is to terminate the task.
				finishAffinity();
				TiApplication.terminateActivityStack();
			}
		}
	}

	/**
	 * Internal method used to call Google's Activity.onCreate() method, bypassing this class' onCreate().
	 * Only intended to be called by a 2nd instance of TiRootActivity class. (Only 1 instance is allowed at a time.)
	 * @param savedInstanceState Bundle to be passed to the Activity.onCreate() method.
	 */
	void activityOnCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
	}

	/**
	 * Internal method used to call Google's Activity.onDestroy() method, bypassing this class' onDestroy().
	 * Only intended to be called by a 2nd instance of TiRootActivity class. (Only 1 instance is allowed at a time.)
	 */
	void activityOnDestroy()
	{
		super.onDestroy();
	}

	public boolean hasSustainMode()
	{
		PowerManager manager = (PowerManager) getSystemService(Context.POWER_SERVICE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			return manager.isSustainedPerformanceModeSupported();
		}
		return false;
	}

	public void setSustainMode(boolean sustainMode)
	{
		if (hasSustainMode() && this.sustainMode != sustainMode) {
			getWindow().setSustainedPerformanceMode(sustainMode);
			this.sustainMode = sustainMode;
		} else {
			Log.w(TAG, "sustainedPerformanceMode is not supported on this device");
		}
	}

	/**
	 * Gets the safe area in pixels, relative to the root decor view. This is the region between
	 * the top/bottom/left/right insets that overlap the view's content such as a translucent
	 * status bar, translucent navigation bar, or screen notches.
	 * @return
	 * Returns the safe area region in pixels relative to the root decor view.
	 * <p>
	 * Returns null if activity's root view is not available, such as after it's been destroyed.
	 */
	public Rect getSafeAreaRect()
	{
		if (this.safeAreaMonitor == null) {
			return null;
		}
		return this.safeAreaMonitor.getSafeAreaRect();
	}

	/**
	 * Adds an object used to provide custom insets to be excluded from the safe-area returned
	 * by this activity's getSafeAreaRect() method.
	 * <p>
	 * For example, Titanium's TabGroup will use this method to add its tab bar as a custom inset.
	 * <p>
	 * The provider's insets are expected to be relative to this activity's root decor view.
	 * @param provider Object used to provide custom insets. If given null, then this method will no-op.
	 */
	public void addCustomInsetsProvider(TiInsetsProvider provider)
	{
		if (this.safeAreaMonitor != null) {
			this.safeAreaMonitor.addInsetsProvider(provider);
		}
	}

	/**
	 * Removes the provider added via the addCustomInsetsProvider() method by reference.
	 * Once removed, the provider's insets will no longer apply to this activity's safe-area.
	 * @param provider The insets provider to be removed by reference. Can be null.
	 */
	public void removeCustomInsetsProvider(TiInsetsProvider provider)
	{
		if (this.safeAreaMonitor != null) {
			this.safeAreaMonitor.removeInsetsProvider(provider);
		}
	}
}
