/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2016 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.kroll;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.AsyncResult;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiMessenger;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.ActivityProxy;
import org.appcelerator.titanium.proxy.TiWindowProxy;
import org.appcelerator.titanium.TiLifecycle.OnLifecycleEvent;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiRHelper;
import org.appcelerator.titanium.util.TiUrl;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Pair;

import org.json.JSONObject;

/**
 * This is the parent class of all proxies. A proxy is a dynamic object that can be created or
 * queried by the user through a module or another proxy's API. When you create a native view with
 * <a href="http://developer.appcelerator.com/apidoc/mobile/latest/Titanium.UI.createView-method.html">Titanium.UI.createView </a>,
 * the view object is a proxy itself.
 */
@Kroll.proxy(name = "KrollProxy", propertyAccessors = { KrollProxy.PROPERTY_HAS_JAVA_LISTENER })
public class KrollProxy implements Handler.Callback, KrollProxySupport, OnLifecycleEvent
{
	private static final String TAG = "KrollProxy";
	private static final int INDEX_NAME = 0;
	private static final int INDEX_OLD_VALUE = 1;
	private static final int INDEX_VALUE = 2;

	private static final String ERROR_CREATING_PROXY = "Error creating proxy";

	protected static final int MSG_MODEL_PROPERTY_CHANGE = KrollObject.MSG_LAST_ID + 100;
	protected static final int MSG_LISTENER_ADDED = KrollObject.MSG_LAST_ID + 101;
	protected static final int MSG_LISTENER_REMOVED = KrollObject.MSG_LAST_ID + 102;
	protected static final int MSG_MODEL_PROCESS_PROPERTIES = KrollObject.MSG_LAST_ID + 103;
	protected static final int MSG_MODEL_PROPERTIES_CHANGED = KrollObject.MSG_LAST_ID + 104;
	protected static final int MSG_INIT_KROLL_OBJECT = KrollObject.MSG_LAST_ID + 105;
	protected static final int MSG_SET_PROPERTY = KrollObject.MSG_LAST_ID + 106;
	protected static final int MSG_FIRE_EVENT = KrollObject.MSG_LAST_ID + 107;
	protected static final int MSG_FIRE_SYNC_EVENT = KrollObject.MSG_LAST_ID + 108;
	protected static final int MSG_CALL_PROPERTY_ASYNC = KrollObject.MSG_LAST_ID + 109;
	protected static final int MSG_CALL_PROPERTY_SYNC = KrollObject.MSG_LAST_ID + 110;
	protected static final int MSG_LAST_ID = MSG_CALL_PROPERTY_SYNC;
	protected static final String PROPERTY_NAME = "name";
	protected static final String PROPERTY_HAS_JAVA_LISTENER = "_hasJavaListener";

	protected static AtomicInteger proxyCounter = new AtomicInteger();
	protected AtomicInteger listenerIdGenerator;

	protected Map<String, HashMap<Integer, KrollEventCallback>> eventListeners;
	protected KrollObject krollObject;
	protected WeakReference<Activity> activity;
	protected String proxyId;
	protected TiUrl creationUrl;
	protected KrollProxyListener modelListener;
	protected KrollModule createdInModule;
	protected boolean coverageEnabled;
	protected KrollDict properties = new KrollDict();
	protected KrollDict defaultValues = new KrollDict();
	protected Handler mainHandler = null;
	protected Handler runtimeHandler = null;

	private KrollDict langConversionTable = null;
	private boolean bubbleParent = true;

	public static final String PROXY_ID_PREFIX = "proxy$";
	public static final int INVALID_EVENT_LISTENER_ID = -1;

	/**
	 * The default KrollProxy constructor. Equivalent to <code>KrollProxy("")</code>
	 * @module.api
	 */
	public KrollProxy()
	{
		this("");
	}

	/**
	 * Constructs a KrollProxy, using the passed in creation URL.
	 * @param baseCreationUrl the creation URL for this proxy, which can be used to resolve relative paths
	 * @module.api
	 */
	public KrollProxy(String baseCreationUrl)
	{
		creationUrl = new TiUrl(baseCreationUrl);
		this.listenerIdGenerator = new AtomicInteger(0);
		this.eventListeners = Collections.synchronizedMap(new HashMap<String, HashMap<Integer, KrollEventCallback>>());
		this.langConversionTable = getLangConversionTable();
	}

	private void setupProxy(KrollObject object, Object[] creationArguments, TiUrl creationUrl)
	{
		// Store reference to the native object that represents this proxy so we can drive changes to the JS
		// object
		krollObject = object;
		object.setProxySupport(this);
		this.creationUrl = creationUrl;

		// Associate the activity with the proxy.  if the proxy needs activity association delayed until a
		// later point then initActivity should be overridden to be a no-op and then call setActivity directly
		// at the appropriate time
		initActivity(TiApplication.getAppRootOrCurrentActivity());

		// Setup the proxy according to the creation arguments TODO - pass in createdInModule
		handleCreationArgs(null, creationArguments);
	}

	// entry point for generator code
	public static KrollProxy createProxy(Class<? extends KrollProxy> proxyClass, KrollObject object,
										 Object[] creationArguments, String creationUrl)
	{
		try {
			KrollProxy proxyInstance = proxyClass.newInstance();
			proxyInstance.setupProxy(object, creationArguments, TiUrl.createProxyUrl(creationUrl));
			return proxyInstance;

		} catch (Exception e) {
			Log.e(TAG, ERROR_CREATING_PROXY, e);
		}

		return null;
	}

	protected void initActivity(Activity activity)
	{
		this.activity = new WeakReference<Activity>(activity);
	}

	public void setActivity(Activity activity)
	{
		// our proxy must always be associated with a valid activity
		if (activity == null) {
			initActivity(TiApplication.getAppRootOrCurrentActivity());
			return;
		}
		this.activity = new WeakReference<Activity>(activity);
	}

	public void attachActivityLifecycle(Activity activity)
	{
		setActivity(activity);
		((TiBaseActivity) activity).addOnLifecycleEventListener(this);
	}

	/**
	 * @return the activity associated with this proxy.
	 * @module.api
	 */
	public Activity getActivity()
	{
		// our proxy must always be associated with a valid activity
		if (this.activity == null || this.activity.get() == null) {
			initActivity(TiApplication.getAppRootOrCurrentActivity());
		}
		return this.activity.get();
	}

	/**
	 * Handles the arguments passed into the "create" method for this proxy.
	 * If your proxy simply needs to handle a KrollDict, see {@link KrollProxy#handleCreationDict(KrollDict)}
	 * @param args
	 * @module.api
	 */
	public void handleCreationArgs(KrollModule createdInModule, Object[] args)
	{
		this.createdInModule = createdInModule;

		if (args.length == 0 || !(args[0] instanceof HashMap)) {
			handleDefaultValues();
			return;
		}

		KrollDict dict;
		if (args[0] instanceof KrollDict) {
			dict = (KrollDict) args[0];
		} else {
			dict = new KrollDict((HashMap) args[0]);
		}
		handleCreationDict(dict);
	}

	/**
	 * Handles initialization of the proxy's default property values.
	 * @module.api
	 */
	protected void handleDefaultValues()
	{
		for (String key : defaultValues.keySet()) {
			if (!properties.containsKey(key)) {
				setProperty(key, defaultValues.get(key));
			}
		}
	}

	/**
	 * @return the language conversion table used to load localized values for certain properties from the locale files.
	 *	For each localizable property, such as "title," the proxy should define a second property, such as "titleid", used to specify a
	 *	localization key for that property. If the user specifies a localization key in "titleid", the corresponding localized text from the locale file
	 *	is used for "title."
	 *
	 *	Subclasses should override this method to return a table mapping localizable properties to the corresponding localization key properties.
	 *
	 *	For example, if the proxy has two properties, "title" and "text", and the corresponding localization key properties are "titleid" and "textid", this might look like:
	 *	</br>
	 *
	 *	<pre><code>protected KrollDict getLangConversionTable()
	 *{
	 *	KrollDict table = new KrollDict();
	 *	table.put("title", "titleid");
	 *	table.put("text", "textid");
	 *	return table;
	 *} </pre> </code>
	 *
	 * @module.api
	 *
	 */
	protected KrollDict getLangConversionTable()
	{
		return null;
	}

	/**
	 * Handles initialization of the proxy's locale string properties.
	 *
	 * @see #getLangConversionTable()
	 */
	private void handleLocaleProperties()
	{
		if (langConversionTable == null) {
			return;
		}

		/*
		 * Iterate through the language conversion table.
		 * This table maps target properties to their locale lookup property.
		 * Example: title -> titleid
		 *
		 * The lookup identifier stored in the locale property (titleid) will be used
		 * to query the locale strings file to get the localized value.
		 * This localized value will be set to the targeted property (title).
		 */
		for (Map.Entry<String, Object> entry : langConversionTable.entrySet()) {
			// Get the lookup identifier stored in the locale property.
			String localeProperty = entry.getValue().toString();
			String lookupId = properties.getString(localeProperty);
			if (lookupId == null) {
				// If no locale lookup identifier is provided, skip this entry.
				continue;
			}

			// Lookup the localized string from the locale file.
			String localizedValue = getLocalizedText(lookupId);
			if (localizedValue == null) {
				// If there is no localized value for this identifier,
				// log a warning and skip over the entry.
				Log.w(TAG, "No localized string found for identifier: " + lookupId);
				continue;
			}

			// Set the localized value to the targeted property.
			String targetProperty = entry.getKey();
			setProperty(targetProperty, localizedValue);
		}
	}

	/**
	 * Updates the lookup identifier value of a locale property.
	 * This will also update the targeted value with the string found
	 * using the new lookup identifier.
	 *
	 * @param localeProperty name of the locale property (example: titleid)
	 * @param newLookupId the new lookup identifier
	 * @return a pair containing the name of the target property which was updated and the new value set on it.
	 */
	public Pair<String, String> updateLocaleProperty(String localeProperty, String newLookupId)
	{
		if (langConversionTable == null) {
			return null;
		}

		properties.put(localeProperty, newLookupId);

		// Determine which localized property this locale property updates.
		for (Map.Entry<String, Object> entry : langConversionTable.entrySet()) {
			if (entry.getValue().toString().equals(localeProperty)) {
				String targetProperty = entry.getKey();
				String localizedValue = getLocalizedText(newLookupId);
				if (localizedValue == null) {
					return null;
				}
				setProperty(targetProperty, localizedValue);

				return Pair.create(targetProperty, localizedValue);
			}
		}

		// If we reach this point, the provided locale property is not valid.
		return null;
	}

	/**
	 * Return true if the given property is a locale property.
	 * @param propertyName name of the property to check (ex: titleid)
	 * @return true if this property is a locale property
	 */
	public boolean isLocaleProperty(String propertyName)
	{
		return propertyName.endsWith("id");
	}

	/**
	 * Looks up a localized string given an identifier.
	 *
	 * @param lookupId the identifier of the localized value to look up.
	 * @return the localized string if found, otherwise null.
	 */
	private String getLocalizedText(String lookupId)
	{
		try {
			int resid = TiRHelper.getResource("string." + lookupId);
			if (resid != 0) {
				return TiApplication.getInstance().getString(resid);
			}
		} catch (Exception ex) {
		}
		return null;
	}

	/**
	 * Handles the creation {@link KrollDict} passed into the create method for this proxy.
	 * This is usually the first (and sometimes only) argument to the proxy's create method.
	 *
	 * To set default property values, add them to the {@link KrollProxy#defaultValues map}
	 * @param dict
	 * @module.api
	 */
	public void handleCreationDict(KrollDict dict)
	{
		if (dict == null) {
			return;
		}

		if (dict.containsKey(TiC.PROPERTY_BUBBLE_PARENT)) {
			bubbleParent = TiConvert.toBoolean(dict, TiC.PROPERTY_BUBBLE_PARENT, true);
		}

		if (dict.containsKey(TiC.PROPERTY_LIFECYCLE_CONTAINER)) {
			KrollProxy lifecycleProxy = (KrollProxy) dict.get(TiC.PROPERTY_LIFECYCLE_CONTAINER);
			if (lifecycleProxy instanceof TiWindowProxy) {
				ActivityProxy activityProxy = ((TiWindowProxy) lifecycleProxy).getWindowActivityProxy();
				if (activityProxy != null) {
					attachActivityLifecycle(activityProxy.getActivity());
				} else {
					((TiWindowProxy) lifecycleProxy).addProxyWaitingForActivity(this);
				}
			} else {
				Log.e(TAG,
					  TiC.PROPERTY_LIFECYCLE_CONTAINER + " must be a WindowProxy or TabGroupProxy (TiWindowProxy)");
			}
		}

		for (String key : dict.keySet()) {
			setProperty(key, dict.get(key));
		}
		handleDefaultValues();
		handleLocaleProperties();

		if (modelListener != null) {
			modelListener.processProperties(properties);
		}
	}

	public Handler getMainHandler()
	{
		if (mainHandler == null) {
			mainHandler = new Handler(TiMessenger.getMainMessenger().getLooper(), this);
		}

		return mainHandler;
	}

	public Handler getRuntimeHandler()
	{
		if (krollObject != null) {
			return getRuntimeHandler(krollObject);
		}
		return mainHandler;
	}

	public Handler getRuntimeHandler(KrollObject object)
	{
		if (runtimeHandler == null) {
			TiMessenger messenger = TiMessenger.getMessenger(object);
			if (messenger != null) {
				runtimeHandler = new Handler(messenger.getLooper(), this);
			}
		}

		return runtimeHandler;
	}

	/**
	 * Invokes the given runnable on the main UI thread.
	 * <p>
	 * If this method was called on the main UI thread, then the given runnable is invoked immediately.
	 * Otherwise, the runnable is posted to the main UI thread via the main "Handler".
	 * <p>
	 * This method is the equivalent to the Android Activity.runOnUiThread() method.
	 * @param runnable The runnable to be invoked. Can be null, in which case this method will no-op.
	 */
	public void runOnMainThread(Runnable runnable)
	{
		runWithHandler(runnable, getMainHandler());
	}

	/**
	 * Invokes the given runnable on the JavaScript runtime thread.
	 * <p>
	 * If this method was called on the runtime thread, then the given runnable is invoked immediately.
	 * Otherwise, the runnable is posted to the runtime thread via a "Handler".
	 * @param runnable The runnable to be invoked. Can be null, in which case this method will no-op.
	 */
	public void runOnRuntimeThread(Runnable runnable)
	{
		runWithHandler(runnable, getRuntimeHandler());
	}

	/**
	 * Invokes the given runnable on the thread the given handler is attached to.
	 * <p>
	 * If this method was called on the handler's thread, then the runnable is invoked immediately.
	 * Otherwise, handler.post() is called with the given runnable to be invoked on its thread.
	 * @param runnable The runnable to be invoked. Can be null, in which case this method will no-op.
	 * @param handler The handler to be used. Can be null, which case this method will no-op.
	 */
	private void runWithHandler(Runnable runnable, Handler handler)
	{
		if ((runnable != null) && (handler != null)) {
			Looper looper = handler.getLooper();
			if ((looper != null) && (looper.getThread().getId() == Thread.currentThread().getId())) {
				runnable.run();
			} else {
				handler.post(runnable);
			}
		}
	}

	public void setKrollObject(KrollObject object)
	{
		this.krollObject = object;
	}

	/**
	 * @return the KrollObject associated with this proxy if it exists. Otherwise create it in the KrollRuntime thread.
	 * @module.api
	 */
	public KrollObject getKrollObject()
	{
		if (krollObject == null) {
			if (KrollRuntime.getInstance().isRuntimeThread()) {
				initKrollObject();

			} else {
				TiMessenger.sendBlockingRuntimeMessage(getRuntimeHandler().obtainMessage(MSG_INIT_KROLL_OBJECT));
			}
		}

		return krollObject;
	}

	public void initKrollObject()
	{
		if (!KrollRuntime.isDisposed()) {
			KrollRuntime.getInstance().initObject(this);
		}
	}

	/**
	 * @return the absolute URL of the location in code where the proxy was created in Javascript.
	 * @module.api
	 */
	public TiUrl getCreationUrl()
	{
		return creationUrl;
	}

	@Kroll.method
	public void setCreationUrl(String url)
	{
		creationUrl = TiUrl.createProxyUrl(url);
	}

	// native extending support allows us to whole-sale apply properties and only fire one event / job
	@Kroll.method
	public void extend(KrollDict options)
	{
		if (options == null || options.isEmpty()) {
			return;
		}

		ArrayList<KrollPropertyChange> propertyChanges = new ArrayList<KrollPropertyChange>();
		for (String name : options.keySet()) {
			Object oldValue = properties.get(name);
			Object value = options.get(name);

			// dont just fire the change event, make sure we set the property back on the KrollObject
			// since the property change may not be driven from JS (KrollObject->Java proxy)
			setProperty(name, value);

			if (shouldFireChange(oldValue, value)) {
				KrollPropertyChange pch = new KrollPropertyChange(name, oldValue, value);
				propertyChanges.add(pch);
			}
		}

		// convert to two dimensional array
		int changeSize = propertyChanges.size();
		Object[][] changeArray = new Object[changeSize][];
		for (int i = 0; i < changeSize; i++) {
			KrollPropertyChange propertyChange = propertyChanges.get(i);
			changeArray[i] = new Object[] { propertyChange.name, propertyChange.oldValue, propertyChange.newValue };
		}

		if (KrollRuntime.getInstance().isRuntimeThread()) {
			firePropertiesChanged(changeArray);

		} else {
			Message message = getMainHandler().obtainMessage(MSG_MODEL_PROPERTIES_CHANGED, changeArray);
			message.sendToTarget();
		}
	}

	private void firePropertiesChanged(Object[][] changes)
	{
		if (modelListener == null) {
			return;
		}

		int changesLength = changes.length;
		for (int i = 0; i < changesLength; ++i) {
			Object[] change = changes[i];
			if (change.length != 3) {
				continue;
			}

			Object name = change[INDEX_NAME];
			if (name == null || !(name instanceof String)) {
				continue;
			}

			if (modelListener != null) {
				modelListener.propertyChanged((String) name, change[INDEX_OLD_VALUE], change[INDEX_VALUE], this);
			}
		}
	}

	public Object getIndexedProperty(int index)
	{
		// TODO(josh): return undefined value
		return 0;
	}

	public void setIndexedProperty(int index, Object value)
	{
		// no-op
	}

	/**
	 * @param name  the lookup key.
	 * @return  true if the proxy contains this property, false otherwise.
	 * @module.api
	 */
	public boolean hasProperty(String name)
	{
		if (properties != null) {
			return properties.containsKey(name);
		}
		return false;
	}

	/**
	 * @param name  the lookup key.
	 * @return  true if the proxy contains this property and it is not null, false otherwise.
	 * @module.api
	 */
	public boolean hasPropertyAndNotNull(String name)
	{
		if (properties != null) {
			return properties.containsKeyAndNotNull(name);
		}
		return false;
	}

	/**
	 * Returns the property value given its key.
	 * Properties are cached on the Proxy and updated from JS for relevant annotated APIs
	 * @param name  the lookup key.
	 * @return the property object or null if a property for the given key does not exist.
	 * @module.api
	 */
	public Object getProperty(String name)
	{
		if (properties != null) {
			return properties.get(name);
		}
		return null;
	}

	/**
	 * This sets the named property as well as updating the actual JS object.
	 * @module.api
	 */
	public void setProperty(String name, Object value)
	{
		if (properties == null) {
			return;
		}

		properties.put(name, value);

		if (KrollRuntime.getInstance().isRuntimeThread()) {
			doSetProperty(name, value);

		} else {
			Message message = getRuntimeHandler().obtainMessage(MSG_SET_PROPERTY, value);
			message.getData().putString(PROPERTY_NAME, name);
			message.sendToTarget();
		}
	}

	public class KrollPropertyChangeSet extends KrollPropertyChange
	{
		public int entryCount;
		public String[] keys;
		public Object[] oldValues;
		public Object[] newValues;

		public KrollPropertyChangeSet(int capacity)
		{
			super(null, null, null);
			entryCount = 0;
			keys = new String[capacity];
			oldValues = new Object[capacity];
			newValues = new Object[capacity];
		}

		public void addChange(String key, Object oldValue, Object newValue)
		{
			keys[entryCount] = key;
			oldValues[entryCount] = oldValue;
			newValues[entryCount] = newValue;
			entryCount++;
		}

		public void fireEvent(KrollProxy proxy, KrollProxyListener listener)
		{
			if (listener == null) {
				return;
			}
			for (int i = 0; i < entryCount; i++) {
				listener.propertyChanged(keys[i], oldValues[i], newValues[i], proxy);
			}
		}
	}

	@Kroll.method
	public void applyProperties(Object arg)
	{
		if (!(arg instanceof HashMap)) {
			Log.w(TAG, "Cannot apply properties: invalid type for properties", Log.DEBUG_MODE);
			return;
		}
		HashMap props = (HashMap) arg;
		if (modelListener == null) {
			for (Object name : props.keySet()) {
				setProperty(TiConvert.toString(name), props.get(name));
			}
			return;
		}
		if (TiApplication.isUIThread()) {
			for (Object key : props.keySet()) {
				String name = TiConvert.toString(key);
				Object value = props.get(key);
				Object current = getProperty(name);
				setProperty(name, value);
				if (shouldFireChange(current, value)) {
					modelListener.propertyChanged(name, current, value, this);
				}
			}
			return;
		}

		KrollPropertyChangeSet changes = new KrollPropertyChangeSet(props.size());
		for (Object key : props.keySet()) {
			String name = TiConvert.toString(key);
			Object value = props.get(key);
			Object current = getProperty(name);
			setProperty(name, value);
			if (shouldFireChange(current, value)) {
				changes.addChange(name, current, value);
			}
		}
		if (changes.entryCount > 0) {
			getMainHandler().obtainMessage(MSG_MODEL_PROPERTY_CHANGE, changes).sendToTarget();
		}
	}

	/**
	 * Asynchronously calls a function referenced by a property on this object.
	 * This may be called safely on any thread.
	 *
	 * @see KrollObject#callProperty(String, Object[])
	 * @param name the property that references the function
	 * @param args the arguments to pass when calling the function.
	 */
	public void callPropertyAsync(String name, Object[] args)
	{
		Message msg = getRuntimeHandler().obtainMessage(MSG_CALL_PROPERTY_ASYNC, args);
		msg.getData().putString(PROPERTY_NAME, name);
		msg.sendToTarget();
	}

	/**
	 * Synchronously calls a function referenced by a property on this object.
	 * This may be called safely on any thread.
	 *
	 * @see KrollObject#callProperty(String, Object[])
	 * @param name the property that references the function
	 * @param args the arguments to pass when calling the function.
	 */
	public void callPropertySync(String name, Object[] args)
	{
		final KrollObject krollObject = getKrollObject();
		if (krollObject == null) {
			return;
		}
		if (KrollRuntime.getInstance().isRuntimeThread()) {
			krollObject.callProperty(name, args);
		} else {
			Message msg = getRuntimeHandler().obtainMessage(MSG_CALL_PROPERTY_SYNC);
			msg.getData().putString(PROPERTY_NAME, name);
			TiMessenger.sendBlockingRuntimeMessage(msg, args);
		}
	}

	protected void doSetProperty(String name, Object value)
	{
		final KrollObject krollObject = getKrollObject();
		if (krollObject == null) {
			return;
		}
		krollObject.setProperty(name, value);
	}

	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public boolean getBubbleParent()
	// clang-format on
	{
		return bubbleParent;
	}

	// clang-format off
	@Kroll.method
	@Kroll.setProperty
	public void setBubbleParent(Object value)
	// clang-format on
	{
		bubbleParent = TiConvert.toBoolean(value);
	}

	/**
	 * Fires an event asynchronously via KrollRuntime thread, which can be intercepted on JS side.
	 * @param event the event to be fired.
	 * @param data  the data to be sent.
	 * @return whether this proxy has an eventListener for this event.
	 * @module.api
	 */
	public boolean fireEvent(String event, Object data)
	{
		if (hierarchyHasListener(event)) {
			Message message = getRuntimeHandler().obtainMessage(MSG_FIRE_EVENT, data);
			message.getData().putString(PROPERTY_NAME, event);
			message.sendToTarget();
			return true;
		}

		return false;
	}

	/**
	 * Send an event to the view who is next to receive the event.
	 *
	 * @param eventName event to send to the next view
	 * @param data the data to include in the event
	 * @return true if the event was handled
	 */
	@Kroll.method(name = "_fireEventToParent")
	public boolean fireEventToParent(String eventName, Object data)
	{
		if (bubbleParent) {
			KrollProxy parentProxy = getParentForBubbling();
			if (parentProxy != null) {
				return parentProxy.fireEvent(eventName, data);
			}
		}
		return false;
	}

	/**
	 * Fires an event synchronously via KrollRuntime thread, which can be intercepted on JS side.
	 * @param event the event to be fired.
	 * @param data  the data to be sent.
	 * @return whether this proxy has an eventListener for this event.
	 * @module.api
	 */
	public boolean fireSyncEvent(String event, Object data)
	{
		if (KrollRuntime.getInstance().isRuntimeThread()) {
			return doFireEvent(event, data);

		} else {
			Message message = getRuntimeHandler().obtainMessage(MSG_FIRE_SYNC_EVENT);
			message.getData().putString(PROPERTY_NAME, event);

			return (Boolean) TiMessenger.sendBlockingRuntimeMessage(message, data);
		}
	}

	/**
	 * Fires an event synchronously via KrollRuntime thread, which can be intercepted on JS side.
	 * @param event the event to be fired.
	 * @param data  the data to be sent.
	 * @param maxTimeout the maximum time to wait for the result to return, in the unit of milliseconds.
	 * @return whether this proxy has an eventListener for this event.
	 * @module.api
	 */
	public boolean fireSyncEvent(String event, Object data, long maxTimeout)
	{
		if (KrollRuntime.getInstance().isRuntimeThread()) {
			return doFireEvent(event, data);

		} else {
			Message message = getRuntimeHandler().obtainMessage(MSG_FIRE_SYNC_EVENT);
			message.getData().putString(PROPERTY_NAME, event);

			Object result = TiMessenger.sendBlockingRuntimeMessage(message, data, maxTimeout);
			return TiConvert.toBoolean(result, false);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public boolean doFireEvent(String event, Object data)
	{
		final KrollObject krollObject = getKrollObject();
		if (krollObject == null || !hierarchyHasListener(event) || eventListeners == null) {
			return false;
		}

		boolean bubbles = false;
		boolean reportSuccess = false;
		int code = 0;
		KrollObject source = null;
		String message = null;
		KrollDict krollData = null;

		/* TODO: Is eventListeners still used? */
		if (!eventListeners.isEmpty()) {
			HashMap<String, Object> dict = (HashMap) data;
			if (dict == null) {
				dict = new KrollDict();
				dict.put(TiC.EVENT_PROPERTY_SOURCE, this);
			} else if (dict instanceof HashMap) {
				Object sourceProxy = dict.get(TiC.EVENT_PROPERTY_SOURCE);
				if (sourceProxy == null) {
					dict.put(TiC.EVENT_PROPERTY_SOURCE, this);
				}
			}
			onEventFired(event, dict);
		}

		if (data != null) {
			if (data instanceof KrollDict) {
				krollData = (KrollDict) data;
			} else if (data instanceof HashMap) {
				try {
					krollData = new KrollDict((HashMap) data);
				} catch (Exception e) {
				}
			} else if (data instanceof JSONObject) {
				try {
					krollData = new KrollDict((JSONObject) data);
				} catch (Exception e) {
				}
			}
		}

		if (krollData != null) {
			Object hashValue = krollData.get(TiC.PROPERTY_BUBBLES);
			if (hashValue != null) {
				bubbles = TiConvert.toBoolean(hashValue);
				krollData.remove(TiC.PROPERTY_BUBBLES);
			}
			hashValue = krollData.get(TiC.PROPERTY_SUCCESS);
			if (hashValue instanceof Boolean) {
				boolean successValue = ((Boolean) hashValue).booleanValue();
				hashValue = krollData.get(TiC.PROPERTY_CODE);
				if (hashValue instanceof Integer) {
					int codeValue = ((Integer) hashValue).intValue();
					if (successValue == (codeValue == 0)) {
						reportSuccess = true;
						code = codeValue;
						krollData.remove(TiC.PROPERTY_SUCCESS);
						krollData.remove(TiC.PROPERTY_CODE);
					} else {
						Log.w(
							TAG,
							"DEPRECATION WARNING: Events with 'code' and 'success' should have success be true if and only if code is nonzero. For java modules, consider the putCodeAndMessage() method to do this for you. The capability to use other types will be removed in a future version.",
							Log.DEBUG_MODE);
					}
				} else if (successValue) {
					Log.w(
						TAG,
						"DEPRECATION WARNING: Events with 'success' of true should have an integer 'code' property that is 0. For java modules, consider the putCodeAndMessage() method to do this for you. The capability to use other types will be removed in a future version.",
						Log.DEBUG_MODE);
				} else {
					Log.w(
						TAG,
						"DEPRECATION WARNING: Events with 'success' of false should have an integer 'code' property that is nonzero. For java modules, consider the putCodeAndMessage() method to do this for you. The capability to use other types will be removed in a future version.",
						Log.DEBUG_MODE);
				}
			} else if (hashValue != null) {
				Log.w(
					TAG,
					"DEPRECATION WARNING: The 'success' event property is reserved to be a boolean. For java modules, consider the putCodeAndMessage() method to do this for you. The capability to use other types will be removed in a future version.",
					Log.DEBUG_MODE);
			}
			hashValue = krollData.get(TiC.EVENT_PROPERTY_ERROR);
			if (hashValue instanceof String) {
				message = (String) hashValue;
				krollData.remove(TiC.EVENT_PROPERTY_ERROR);
			} else if (hashValue != null) {
				Log.w(
					TAG,
					"DEPRECATION WARNING: The 'error' event property is reserved to be a string. For java modules, consider the putCodeAndMessage() method to do this for you. The capability to use other types will be removed in a future version.",
					Log.DEBUG_MODE);
			}
			hashValue = krollData.get(TiC.EVENT_PROPERTY_SOURCE);
			if (hashValue instanceof KrollProxy) {
				if (hashValue != this) {
					source = ((KrollProxy) hashValue).getKrollObject();
				}
				krollData.remove(TiC.EVENT_PROPERTY_SOURCE);
			} else {
				source = krollObject;
			}
			if (krollData.size() == 0) {
				krollData = null;
			}
		}

		return krollObject.fireEvent(source, event, krollData, bubbles, reportSuccess, code, message);
	}

	/**
	 * Our view proxy supports three properties to match iOS regarding
	 * the text that is read aloud (or otherwise communicated) by the
	 * assistive technology: accessibilityLabel, accessibilityHint
	 * and accessibilityValue.
	 *
	 * We combine these to create the single Android property contentDescription.
	 * (e.g., View.setContentDescription(...));
	 */
	public String composeContentDescription()
	{
		if (properties == null) {
			return null;
		}

		final String punctuationPattern = "^.*\\p{Punct}\\s*$";
		StringBuilder buffer = new StringBuilder();
		String label = TiConvert.toString(properties.get(TiC.PROPERTY_ACCESSIBILITY_LABEL));
		String hint = TiConvert.toString(properties.get(TiC.PROPERTY_ACCESSIBILITY_HINT));
		String value = TiConvert.toString(properties.get(TiC.PROPERTY_ACCESSIBILITY_VALUE));

		if (!TextUtils.isEmpty(label)) {
			buffer.append(label);
			if (!label.matches(punctuationPattern)) {
				buffer.append(".");
			}
		}

		if (!TextUtils.isEmpty(value)) {
			if (buffer.length() > 0) {
				buffer.append(" ");
			}
			buffer.append(value);
			if (!value.matches(punctuationPattern)) {
				buffer.append(".");
			}
		}

		if (!TextUtils.isEmpty(hint)) {
			if (buffer.length() > 0) {
				buffer.append(" ");
			}
			buffer.append(hint);
			if (!hint.matches(punctuationPattern)) {
				buffer.append(".");
			}
		}

		return buffer.toString();
	}

	public void firePropertyChanged(String name, Object oldValue, Object newValue)
	{
		if (modelListener != null) {
			if (TiApplication.isUIThread()) {
				modelListener.propertyChanged(name, oldValue, newValue, this);

			} else {
				KrollPropertyChange pch = new KrollPropertyChange(name, oldValue, newValue);
				getMainHandler().obtainMessage(MSG_MODEL_PROPERTY_CHANGE, pch).sendToTarget();
			}
		}
	}

	public void onHasListenersChanged(String event, boolean hasListeners)
	{
		Message msg = getMainHandler().obtainMessage(hasListeners ? MSG_LISTENER_ADDED : MSG_LISTENER_REMOVED);
		msg.obj = event;
		TiMessenger.getMainMessenger().sendMessage(msg);
	}

	/**
	 * @param event the event to check
	 * @return whether the associated KrollObject has an event listener for the passed in event.
	 * @module.api
	 */
	public boolean hasListeners(String event)
	{
		final KrollObject krollObject = getKrollObject();
		if (krollObject == null) {
			return false;
		}
		return krollObject.hasListeners(event);
	}

	/**
	 * Returns true if any view in the hierarchy has the event listener.
	 */
	public boolean hierarchyHasListener(String event)
	{
		boolean hasListener = hasListeners(event);

		// Checks whether the parent has the listener or not
		if (!hasListener) {
			KrollProxy parentProxy = getParentForBubbling();
			if (parentProxy != null && bubbleParent) {
				return parentProxy.hierarchyHasListener(event);
			}
		}

		return hasListener;
	}

	public boolean shouldFireChange(Object oldValue, Object newValue)
	{
		if (!(oldValue == null && newValue == null)) {
			if ((oldValue == null && newValue != null) || (newValue == null && oldValue != null)
				|| (!oldValue.equals(newValue))) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Same behavior as {@link #setProperty(String, Object)}, but also invokes
	 * {@link KrollProxyListener#propertyChanged(String, Object, Object, KrollProxy)}.
	 * @param name the property name.
	 * @param value the property value.
	 * @module.api
	 */
	public void setPropertyAndFire(String name, Object value)
	{
		Object current = getProperty(name);
		setProperty(name, value);

		if (shouldFireChange(current, value)) {
			firePropertyChanged(name, current, value);
		}
	}

	public void onPropertyChanged(String name, Object value)
	{
		String propertyName = name;
		Object newValue = value;

		if (isLocaleProperty(name)) {
			Log.i(TAG, "Updating locale: " + name, Log.DEBUG_MODE);
			Pair<String, String> update = updateLocaleProperty(name, TiConvert.toString(value));
			if (update != null) {
				propertyName = update.first;
				newValue = update.second;
			}
		}

		Object oldValue = properties.get(propertyName);
		properties.put(propertyName, newValue);
		firePropertyChanged(propertyName, oldValue, newValue);
	}

	public void onPropertiesChanged(Object[][] changes)
	{
		int changesLength = changes.length;
		boolean isUiThread = TiApplication.isUIThread();

		for (int i = 0; i < changesLength; ++i) {
			Object[] change = changes[i];
			if (change.length != 3) {
				continue;
			}

			Object name = change[INDEX_NAME];
			if (name == null || !(name instanceof String)) {
				continue;
			}

			String nameString = (String) name;
			Object value = change[INDEX_VALUE];

			properties.put(nameString, change[INDEX_VALUE]);
			if (isUiThread && modelListener != null) {
				modelListener.propertyChanged(nameString, change[INDEX_OLD_VALUE], value, this);
			}
		}

		if (isUiThread || modelListener == null) {
			return;
		}

		Message message = getMainHandler().obtainMessage(MSG_MODEL_PROPERTIES_CHANGED, changes);
		message.sendToTarget();
	}

	public ActivityProxy getActivityProxy()
	{
		Activity activity = getActivity();
		if (activity instanceof TiBaseActivity) {
			return ((TiBaseActivity) activity).getActivityProxy();
		}

		return null;
	}

	/**
	 * Returns proxy that should receive the event next in a case of bubbling. Return null if the class does not
	 * bubble or there is no parent. Optionally return null if the "bubbleParent" property is false -- i.e.,
	 * bubbleParent must be checked as well.
	 *
	 * @return proxy which is next to receive events
	 */
	public KrollProxy getParentForBubbling()
	{
		return null;
	}

	/**
	 * Returns a KrollDict object that contains all current properties associated with this proxy.
	 * @return KrollDict properties object.
	 * @module.api
	 */
	public KrollDict getProperties()
	{
		return properties;
	}

	/**
	 * @return the KrollModule that this proxy was created in.
	 */
	public KrollModule getCreatedInModule()
	{
		return createdInModule;
	}

	public boolean handleMessage(Message msg)
	{
		try {
			switch (msg.what) {
				case MSG_MODEL_PROPERTY_CHANGE: {
					((KrollPropertyChange) msg.obj).fireEvent(this, modelListener);

					return true;
				}
				case MSG_LISTENER_ADDED:
				case MSG_LISTENER_REMOVED: {
					if (modelListener == null) {
						return true;
					}

					String event = (String) msg.obj;

					if (msg.what == MSG_LISTENER_ADDED) {
						eventListenerAdded(event, 1, this);

					} else {
						eventListenerRemoved(event, 0, this);
					}

					return true;
				}
				case MSG_MODEL_PROCESS_PROPERTIES: {
					if (modelListener != null) {
						modelListener.processProperties(properties);
					}
					return true;
				}
				case MSG_MODEL_PROPERTIES_CHANGED: {
					firePropertiesChanged((Object[][]) msg.obj);

					return true;
				}
				case MSG_INIT_KROLL_OBJECT: {
					initKrollObject();
					((AsyncResult) msg.obj).setResult(null);

					return true;
				}
				case MSG_SET_PROPERTY: {
					Object value = msg.obj;
					String property = msg.getData().getString(PROPERTY_NAME);
					doSetProperty(property, value);

					return true;
				}
				case MSG_FIRE_EVENT: {
					Object data = msg.obj;
					String event = msg.getData().getString(PROPERTY_NAME);
					doFireEvent(event, data);

					return true;
				}
				case MSG_FIRE_SYNC_EVENT: {
					AsyncResult asyncResult = (AsyncResult) msg.obj;
					boolean handled = doFireEvent(msg.getData().getString(PROPERTY_NAME), asyncResult.getArg());
					asyncResult.setResult(handled);

					return handled;
				}
				case MSG_CALL_PROPERTY_ASYNC: {
					final KrollObject krollObject = getKrollObject();
					if (krollObject == null) {
						return false;
					}
					String propertyName = msg.getData().getString(PROPERTY_NAME);
					Object[] args = (Object[]) msg.obj;
					krollObject.callProperty(propertyName, args);

					return true;
				}
				case MSG_CALL_PROPERTY_SYNC: {
					final KrollObject krollObject = getKrollObject();
					if (krollObject == null) {
						return false;
					}
					String propertyName = msg.getData().getString(PROPERTY_NAME);
					AsyncResult asyncResult = (AsyncResult) msg.obj;
					Object[] args = (Object[]) asyncResult.getArg();
					krollObject.callProperty(propertyName, args);
					asyncResult.setResult(null);

					return true;
				}
			}
		} catch (Throwable t) {
			Thread.getDefaultUncaughtExceptionHandler().uncaughtException(null, t);
		}

		return false;
	}

	// TODO: count should be removed since we no longer report it.
	//       These methods only gets called now when the first listener
	//       is added or the last one has been removed.
	/**
	 * Called when a event listener is added to the proxy
	 *
	 * @param event			the event that the listener has been added for
	 * @param count			the number of listeners for this event.  should not be used as this value
	 * 						is not reported correctly
	 * @param proxy			the proxy that the event was added to.  otherwise known as "this"
	 * @return				<code>void</code>
	 */
	protected void eventListenerAdded(String event, int count, KrollProxy proxy)
	{
		modelListener.listenerAdded(event, count, this);
	}

	/**
	 * Called when a event listener is removed from the proxy
	 *
	 * @param event			the event that the listener has been removed for
	 * @param count			the number of listeners for this event.  should not be used as this value
	 * 						is not reported correctly
	 * @param proxy			the proxy that the event was removed from.  otherwise known as "this"
	 * @return				<code>void</code>
	 */
	protected void eventListenerRemoved(String event, int count, KrollProxy proxy)
	{
		modelListener.listenerRemoved(event, count, this);
	}

	/**
	 * Associates this proxy with the passed in {@link KrollProxyListener}.
	 * @param modelListener the passed in KrollProxyListener.
	 * @module.api
	 */
	public void setModelListener(KrollProxyListener modelListener)
	{
		// Double-setting the same modelListener can potentially have weird side-effects.
		if (this.modelListener != null && this.modelListener.equals(modelListener)) {
			return;
		}

		this.modelListener = modelListener;
		if (modelListener != null) {
			if (TiApplication.isUIThread()) {
				modelListener.processProperties(properties);
			} else {
				getMainHandler().sendEmptyMessage(MSG_MODEL_PROCESS_PROPERTIES);
			}
		}
	}

	public int addEventListener(String eventName, KrollEventCallback callback)
	{
		int listenerId = KrollProxy.INVALID_EVENT_LISTENER_ID;

		if (eventName == null) {
			throw new IllegalStateException("addEventListener expects a non-null eventName");

		} else if (callback == null) {
			throw new IllegalStateException("addEventListener expects a non-null listener");
		}

		synchronized (eventListeners)
		{
			if (eventListeners.isEmpty()) {
				setProperty(PROPERTY_HAS_JAVA_LISTENER, true);
			}

			HashMap<Integer, KrollEventCallback> listeners = eventListeners.get(eventName);
			if (listeners == null) {
				listeners = new HashMap<Integer, KrollEventCallback>();
				eventListeners.put(eventName, listeners);
			}

			if (Log.isDebugModeEnabled()) {
				Log.d(TAG, "Added for eventName '" + eventName + "' with id " + listenerId, Log.DEBUG_MODE);
			}
			listenerId = listenerIdGenerator.incrementAndGet();
			listeners.put(listenerId, callback);
		}

		return listenerId;
	}

	public void removeEventListener(String eventName, int listenerId)
	{
		if (eventName == null) {
			throw new IllegalStateException("removeEventListener expects a non-null eventName");
		}

		synchronized (eventListeners)
		{
			HashMap<Integer, KrollEventCallback> listeners = eventListeners.get(eventName);
			if (listeners != null) {
				if (listeners.remove(listenerId) == null) {
					Log.d(TAG, "listenerId " + listenerId + " not for eventName '" + eventName + "'", Log.DEBUG_MODE);
				}
				if (listeners.isEmpty()) {
					eventListeners.remove(eventName);
				}
				if (eventListeners.isEmpty()) {
					// If we don't have any java listeners, we set the property to false
					setProperty(PROPERTY_HAS_JAVA_LISTENER, false);
				}
			}
		}
	}

	public void onEventFired(String event, Object data)
	{
		HashMap<Integer, KrollEventCallback> listeners = eventListeners.get(event);
		if (listeners != null) {
			Integer[] clonedKeys = listeners.keySet().toArray(new Integer[0]);
			if (clonedKeys != null) {
				for (Integer listenerId : clonedKeys) {
					KrollEventCallback callback = listeners.get(listenerId);
					if (callback != null) {
						callback.call(data);
					}
				}
			}
		}
	}

	/**
	 * Resolves the passed in scheme / path, and uses the Proxy's creationUrl if the path is relative.
	 * @param scheme the scheme of Url.
	 * @param path   the path of Url.
	 * @return a string representation of URL given its components.
	 * @module.api
	 */
	public String resolveUrl(String scheme, String path)
	{
		return TiUrl.resolve(creationUrl.baseUrl, path, scheme);
	}

	public String getProxyId()
	{
		return proxyId;
	}

	protected KrollDict createErrorResponse(int code, String message)
	{
		KrollDict error = new KrollDict();
		error.putCodeAndMessage(code, message);
		error.put(TiC.ERROR_PROPERTY_MESSAGE, message);
		return error;
	}

	/**
	 * Releases the KrollObject, freeing memory.
	 * @module.api
	 */
	public void release()
	{
		releaseKroll();

		if (eventListeners != null) {
			eventListeners.clear();
			eventListeners = null;
		}
		if (properties != null) {
			properties.clear();
			properties = null;
		}
		if (defaultValues != null) {
			defaultValues.clear();
			defaultValues = null;
		}
		if (krollObject != null) {
			krollObject.release();
			krollObject = null;
		}
	}

	/**
	 * Only release kroll, but maintain instance
	 * @module.api
	 */
	public void releaseKroll()
	{
		if (krollObject != null) {
			krollObject.release();
		}
	}

	// For subclasses to override
	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public String getApiName()
	// clang-format on
	{
		return "Ti.Proxy";
	}

	/**
	 * A place holder for subclasses to extend. Its purpose is to receive native Android onCreate life cycle events.
	 * @param activity the activity attached to this module.
	 * @module.api
	 */
	public void onCreate(Activity activity, Bundle savedInstanceState)
	{
	}

	/**
	 * A place holder for subclasses to extend. Its purpose is to receive native Android onResume life cycle events.
	 * @param activity the activity attached to this module.
	 * @module.api
	 */
	public void onResume(Activity activity)
	{
	}

	/**
	 * A place holder for subclasses to extend. Its purpose is to receive native Android onPause life cycle events.
	 * @param activity the activity attached to this module.
	 * @module.api
	 */
	public void onPause(Activity activity)
	{
	}

	/**
	 * A place holder for subclasses to extend. Its purpose is to receive native Android onDestroy life cycle events.
	 * @param activity the activity attached to this module.
	 * @module.api
	 */
	public void onDestroy(Activity activity)
	{
	}

	/**
	 * A place holder for subclasses to extend. Its purpose is to receive native Android onStart life cycle events.
	 * @param activity the activity attached to this module.
	 * @module.api
	 */
	public void onStart(Activity activity)
	{
	}

	/**
	 * A place holder for subclasses to extend. Its purpose is to receive native Android onStop life cycle events.
	 * @param activity the activity attached to this module.
	 * @module.api
	 */
	public void onStop(Activity activity)
	{
	}
}
