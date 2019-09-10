/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-2015 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.kroll;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

import org.appcelerator.kroll.KrollExceptionHandler.ExceptionMessage;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiMessenger;
import org.appcelerator.kroll.util.KrollAssetHelper;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

/**
 * The common Javascript runtime instance that Titanium interacts with.
 *
 * The runtime instance itself is static and lives with the Android process.
 * KrollRuntime use activity reference counting to tear down the runtime state
 * when all of the application's Titanium activities have been destroyed.
 *
 * Even after all of the activities have been destroyed, Android can (and usually does)
 * keep the application process running. When the application is re-entered from
 * this "torn down" state, we simply re-initialize again, this time from the first
 * activity ref increment (TiBaseActivity.onCreate), instead of TiApplication.onCreate
 */
public abstract class KrollRuntime implements Handler.Callback
{
	private static final String TAG = "KrollRuntime";
	private static final int MSG_INIT = 100;
	private static final int MSG_DISPOSE = 101;
	private static final int MSG_RUN_MODULE = 102;
	private static final int MSG_EVAL_STRING = 103;
	private static final String PROPERTY_FILENAME = "filename";
	private static final String PROPERTY_SOURCE = "source";

	public interface OnDisposingListener {
		void onDisposing(KrollRuntime runtime);
	}

	private static KrollRuntime instance;
	private static ArrayList<OnDisposingListener> disposingListeners = new ArrayList<>();
	private static int activityRefCount = 0;
	private static int serviceReceiverRefCount = 0;

	private WeakReference<KrollApplication> krollApplication;
	private long threadId;
	private CountDownLatch initLatch = new CountDownLatch(1);
	private KrollEvaluator evaluator;
	private KrollExceptionHandler primaryExceptionHandler;
	private HashMap<String, KrollExceptionHandler> exceptionHandlers;

	public enum State { INITIALIZED, RELEASED, RELAUNCHED, DISPOSED }
	private static State runtimeState = State.DISPOSED;

	protected Handler handler;

	public static final int MSG_LAST_ID = MSG_RUN_MODULE + 100;

	public static final Object UNDEFINED = new Object() {
		public String toString()
		{
			return "undefined";
		}
	};

	public static final int DONT_INTERCEPT = Integer.MIN_VALUE + 1;
	public static final int DEFAULT_THREAD_STACK_SIZE = 16 * 1024;
	public static final String SOURCE_ANONYMOUS = "<anonymous>";

	public static void init(Context context, KrollRuntime runtime)
	{
		// Initialize asset helper, if not done already.
		KrollAssetHelper.init(context);

		// Do not continue if already initialized.
		if (runtimeState == State.INITIALIZED) {
			return;
		}

		// Keep a reference to the given runtime.
		// Must be done before calling doInit() below.
		instance = runtime;

		// Load all application properties.
		((KrollApplication) context).loadAppProperties();

		// Set up runtime member variables.
		Looper looper = Looper.getMainLooper();
		runtime.krollApplication = new WeakReference<KrollApplication>((KrollApplication) context);
		runtime.exceptionHandlers = new HashMap<String, KrollExceptionHandler>();
		runtime.threadId = looper.getThread().getId();
		runtime.handler = new Handler(looper, runtime);

		// Initialize the TiMessenger instance for the current thread.
		// NOTE: This must occur after "threadId" is set and before doInit() is called.
		TiMessenger.getMessenger();

		// Initialize the given runtime.
		runtime.doInit();
	}

	public static KrollRuntime getInstance()
	{
		return instance;
	}

	public static void suggestGC()
	{
		if (instance != null) {
			instance.setGCFlag();
		}
	}

	public static boolean isInitialized()
	{
		if (instance != null) {
			synchronized (runtimeState)
			{
				return runtimeState == State.INITIALIZED;
			}
		}
		return false;
	}

	public static boolean isDisposed()
	{
		if (instance != null) {
			synchronized (runtimeState)
			{
				return runtimeState == State.DISPOSED;
			}
		}
		return true;
	}

	public KrollApplication getKrollApplication()
	{
		if (krollApplication != null) {
			return krollApplication.get();
		}
		return null;
	}

	public boolean isOriginThreadForObject(KrollObject object)
	{
		return Thread.currentThread().getId() == object.getThreadId();
	}

	public boolean isRuntimeThread()
	{
		return Thread.currentThread().getId() == threadId;
	}

	public long getThreadId()
	{
		return threadId;
	}

	protected void doInit()
	{
		// initializer for the specific runtime implementation (V8)
		initRuntime();

		// Notify the main thread that the runtime has been initialized
		synchronized (runtimeState)
		{
			runtimeState = State.INITIALIZED;
		}
		initLatch.countDown();
	}

	public void dispose()
	{

		Log.d(TAG, "Disposing runtime.", Log.DEBUG_MODE);

		// Set state to released when since we have not fully disposed of it yet
		synchronized (runtimeState)
		{
			if (runtimeState == State.DISPOSED) {
				return;
			}
			runtimeState = State.RELEASED;
		}

		if (isRuntimeThread()) {
			internalDispose();
		} else {
			handler.sendEmptyMessage(MSG_DISPOSE);
		}
	}

	public void runModule(String source, String filename, KrollProxySupport activityProxy)
	{
		if (isRuntimeThread()) {
			doRunModule(source, filename, activityProxy);

		} else {
			Message message = handler.obtainMessage(MSG_RUN_MODULE, activityProxy);
			message.getData().putString(PROPERTY_SOURCE, source);
			message.getData().putString(PROPERTY_FILENAME, filename);
			message.sendToTarget();
		}
	}

	/**
	 * Equivalent to <pre>evalString(source, SOURCE_ANONYMOUS)</pre>
	 * @see #evalString(String, String)
	 * @param source A string containing Javascript source
	 * @return The Java representation of the return value of {@link source}, as long as Kroll supports the return value
	 */
	public Object evalString(String source)
	{
		return evalString(source, SOURCE_ANONYMOUS);
	}

	/**
	 * Evaluates a String of Javascript code, returning the result of the execution
	 * when this method is called on the KrollRuntime thread. If this method is called
	 * ony any other thread, then the code is executed asynchronous, and this method returns null.
	 *
	 * Currently, Kroll supports converting the following Javascript return types:
	 * <ul>
	 * <li>Primitives (String, Number, Boolean, etc)</li>
	 * <li>Javascript object literals as {@link org.appcelerator.kroll.KrollDict}</li>
	 * <li>Arrays</li>
	 * <li>Any Proxy type that extends {@link org.appcelerator.kroll.KrollProxy}</li>
	 * </ul>
	 * @param source A string containing Javascript source
	 * @param filename The name of the filename represented by {@link source}
	 * @return The Java representation of the return value of {@link source}, as long as Kroll supports the return value
	 */
	public Object evalString(String source, String filename)
	{
		if (isRuntimeThread()) {
			return doEvalString(source, filename);

		} else {
			Message message = handler.obtainMessage(MSG_EVAL_STRING);
			message.getData().putString(PROPERTY_SOURCE, source);
			message.getData().putString(PROPERTY_FILENAME, filename);
			message.sendToTarget();
			return null;
		}
	}

	public int getThreadStackSize(Context context)
	{
		if (context instanceof KrollApplication) {
			KrollApplication app = (KrollApplication) context;
			return app.getThreadStackSize();
		}
		return DEFAULT_THREAD_STACK_SIZE;
	}

	public boolean handleMessage(Message msg)
	{
		switch (msg.what) {
			case MSG_INIT: {
				doInit();
				return true;
			}

			case MSG_DISPOSE: {
				internalDispose();
				return true;
			}

			case MSG_RUN_MODULE: {
				String source = msg.getData().getString(PROPERTY_SOURCE);
				String filename = msg.getData().getString(PROPERTY_FILENAME);
				KrollProxySupport activityProxy = (KrollProxySupport) msg.obj;

				doRunModule(source, filename, activityProxy);
				return true;
			}

			case MSG_EVAL_STRING: {
				String source = msg.getData().getString(PROPERTY_SOURCE);
				String filename = msg.getData().getString(PROPERTY_FILENAME);

				doEvalString(source, filename);
				return true;
			}
		}

		return false;
	}

	private static void waitForInit()
	{
		try {
			instance.initLatch.await();
		} catch (InterruptedException e) {
			Log.e(TAG, "Interrupted while waiting for runtime to initialize", e);
		}
	}

	private static void syncInit()
	{
		waitForInit();

		// When the process is re-entered, it is either in the RELEASED or DISPOSED state. If it is in the RELEASED
		// state, that means we have not disposed of the runtime from the previous launch. In that case, we set the
		// state to RELAUNCHED. If we are in the DISPOSED state, we need to re-initialize the runtime here.
		synchronized (runtimeState)
		{
			if (runtimeState == State.DISPOSED) {
				instance.initLatch = new CountDownLatch(1);

				if (instance.isRuntimeThread()) {
					instance.doInit();
				} else {
					instance.handler.sendEmptyMessage(MSG_INIT);
				}

			} else if (runtimeState == State.RELEASED) {
				runtimeState = State.RELAUNCHED;
			}
		}

		waitForInit();
	}

	/**
	 * This method is expected to be called for every Titanium activity that has been created.
	 * This will startup the JavaScript runtime when the activity count is 1 (the first activity created).
	 * <p>
	 * You must call the decrementActivityRefCount() when the Titanium activity is being destroyed.
	 */
	public static void incrementActivityRefCount()
	{
		waitForInit();

		// Increment the activity count.
		activityRefCount++;

		// If this is the 1st activity, then start up the runtime, if not done already.
		if ((activityRefCount == 1) && (instance != null)) {
			syncInit();
		}
	}

	public static void decrementActivityRefCount(boolean willDisposeRuntime)
	{
		// Validate.
		if (activityRefCount <= 0) {
			Log.e(TAG, "The decrementActivityRefCount() method was called while count is zero.");
			return;
		}

		// Decrement the activity count.
		activityRefCount--;

		// Terminate the runtime if activity count is zero.
		if (!willDisposeRuntime) {
			return;
		}
		if ((activityRefCount > 0) || (instance == null)) {
			return;
		}
		instance.dispose();
	}

	public static int getActivityRefCount()
	{
		return activityRefCount;
	}

	// Similar to {@link #incrementActivityRefCount} but for a Titanium Service.
	public static void incrementServiceReceiverRefCount()
	{
		serviceReceiverRefCount++;
	}

	public static void decrementServiceReceiverRefCount()
	{
		if (serviceReceiverRefCount > 0) {
			serviceReceiverRefCount--;
		} else {
			Log.e(TAG, "The decrementServiceReceiverRefCount() method was called while count is zero.");
		}
	}

	public static int getServiceReceiverRefCount()
	{
		return serviceReceiverRefCount;
	}

	private void internalDispose()
	{
		synchronized (runtimeState)
		{
			if (runtimeState == State.DISPOSED) {
				return;
			}
			if (runtimeState == State.RELAUNCHED) {
				// Abort the dispose if the application has been re-launched since we scheduled this dispose during the
				// last exit. Then set it back to the initialized state.
				runtimeState = State.INITIALIZED;
				return;
			}

			runtimeState = State.DISPOSED;
		}

		// Invoke all OnDisposingListener objects before disposing this runtime.
		onDisposing(this);

		// Dispose/terminate the runtime.
		doDispose();

		KrollApplication app = krollApplication.get();
		if (app != null) {
			// Cancel all timers associated with the app
			app.cancelTimers();
			// Request the application to dispose its native resources.
			app.dispose();
		}
	}

	public KrollEvaluator getEvaluator()
	{
		return evaluator;
	}

	public void setEvaluator(KrollEvaluator eval)
	{
		evaluator = eval;
	}

	public void setGCFlag()
	{
		// No-op V8 should override.
	}

	public State getRuntimeState()
	{
		return runtimeState;
	}

	/**
	 * Sets the default exception handler for the runtime. There can only be one default exception handler set at a
	 * time.
	 *
	 * @param handler The exception handler to set
	 * @module.api
	 */
	public static void setPrimaryExceptionHandler(KrollExceptionHandler handler)
	{
		if (instance != null) {
			instance.primaryExceptionHandler = handler;
		}
	}

	/**
	 * Adds an exception handler to a list of handlers that will be called in addition to the default one. To replace the
	 * default exception, use {@link #setPrimaryExceptionHandler(KrollExceptionHandler)}.
	 *
	 * @param handler The exception handler to set
	 * @param key The key for the exception handler
	 * @module.api
	 */
	public static void addAdditionalExceptionHandler(KrollExceptionHandler handler, String key)
	{
		if (instance != null && key != null) {
			instance.exceptionHandlers.put(key, handler);
		}
	}

	/**
	 * Removes the exception handler from the list of additional handlers. This will not affect the default handler.
	 * @param key The key for the exception handler
	 * @module.api
	 */
	public static void removeExceptionHandler(String key)
	{
		if (instance != null && key != null) {
			instance.exceptionHandlers.remove(key);
		}
	}

	public static void dispatchException(final String title, final String message, final String sourceName,
										 final int line, final String lineSource, final int lineOffset,
										 final String jsStack, final String javaStack)
	{
		if (instance != null) {
			HashMap<String, KrollExceptionHandler> handlers = instance.exceptionHandlers;
			ExceptionMessage exceptionMessage =
				new ExceptionMessage(title, message, sourceName, line, lineSource, lineOffset, jsStack, javaStack);

			if (!handlers.isEmpty()) {
				for (KrollExceptionHandler handler : handlers.values()) {
					if (handler != null) {
						handler.handleException(exceptionMessage);
					}
				}
				return;
			}

			// Handle exception with defaultExceptionHandler
			instance.primaryExceptionHandler.handleException(exceptionMessage);
		}
	}

	/**
	 * Adds a listener to be invoked just before a Titanium JavaScript runtime instance has been terminated.
	 * <p>
	 * You cannot add the same listener instance twice. Duplicate listener instances will be ignored.
	 * @param listener The listener to be added. Null references will be ignored.
	 */
	public static void addOnDisposingListener(KrollRuntime.OnDisposingListener listener)
	{
		if ((listener != null) && (disposingListeners.contains(listener) == false)) {
			disposingListeners.add(listener);
		}
	}

	/**
	 * Removes the given listener by reference that was added via the addOnDisposingListener() method.
	 * @param listener The listener to be removed by reference. Can be null.
	 */
	public static void removeOnDisposingListener(KrollRuntime.OnDisposingListener listener)
	{
		if (listener != null) {
			disposingListeners.remove(listener);
		}
	}

	/**
	 * This method is intended to be called just before the given runtime's doDispose() method has been called.
	 * Invokes all "OnDisposingListener" objects, allowing them to perform final cleanup operations.
	 * @param runtime The runtime instance that is about to be disposed of. Can be null.
	 */
	private static void onDisposing(KrollRuntime runtime)
	{
		// Validate.
		if (runtime == null) {
			return;
		}

		// Create a shallow copy of all listeners to be iterated down below.
		// We do this because an invoked listener can add/remove listeners in main collection.
		ArrayList<OnDisposingListener> clonedListeners =
			(ArrayList<OnDisposingListener>) KrollRuntime.disposingListeners.clone();
		if (clonedListeners == null) {
			return;
		}

		// Notify all listeners.
		for (OnDisposingListener listener : clonedListeners) {
			if (KrollRuntime.disposingListeners.contains(listener)) {
				// Previous listener did not remove this listener. Invoke it.
				listener.onDisposing(runtime);
			}
		}
	}

	public abstract void doDispose();
	public abstract void doRunModule(String source, String filename, KrollProxySupport activityProxy);
	public abstract Object doEvalString(String source, String filename);

	public abstract String getRuntimeName();
	public abstract void initRuntime();
	public abstract void initObject(KrollProxySupport proxy);
}
