/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.kroll;

import java.lang.ref.WeakReference;
import java.util.HashMap;

import org.appcelerator.kroll.common.AsyncResult;
import org.appcelerator.kroll.common.TiMessenger;

import android.os.Handler;
import android.os.Message;

/**
 * This class maintains a reference between the JavaScript and Java objects.
 */
public abstract class KrollObject implements Handler.Callback
{

	protected static final int MSG_RELEASE = 100;
	protected static final int MSG_SET_WINDOW = 101;
	protected static final int MSG_LAST_ID = MSG_SET_WINDOW;

	protected HashMap<String, Boolean> hasListenersForEventType = new HashMap<String, Boolean>();
	protected Handler handler;
	private volatile long threadId;

	private WeakReference<KrollProxySupport> proxySupport;

	public KrollObject()
	{
		threadId = Thread.currentThread().getId();
		handler = new Handler(TiMessenger.getMessenger(threadId).getLooper(), this);
	}

	public long getThreadId()
	{
		return threadId;
	}

	public void setThreadId(long id)
	{
		this.threadId = id;
	}

	/**
	 * Sets the Proxy associated with this object.
	 * @param proxySupport the Proxy to be set.
	 */
	public void setProxySupport(KrollProxySupport proxySupport)
	{
		this.proxySupport = new WeakReference<KrollProxySupport>(proxySupport);
	}

	/**
	 * Checks to see if this object has event listeners added.
	 * @param event the event name to check.
	 * @return whether this object has an eventListener for this event.
	 */
	public boolean hasListeners(String event)
	{
		Boolean hasListeners = hasListenersForEventType.get(event);
		if (hasListeners == null) {
			return false;
		}

		return hasListeners.booleanValue();
	}

	/**
	 * Sets whether the passed in event has a corresponding eventListener associated with it on JS side.
	 * @param event  the event to be set.
	 * @param hasListeners  If this is true, then the passed in event has a javascript event listener, false otherwise.
	 */
	public void setHasListenersForEventType(String event, boolean hasListeners)
	{
		hasListenersForEventType.put(event, hasListeners);

		KrollProxySupport proxy = (proxySupport != null) ? proxySupport.get() : null;
		if (proxy != null) {
			proxy.onHasListenersChanged(event, hasListeners);
		}
	}

	/**
	 * This is used to notify Java side when JS fires an event. Right now only webView uses this.
	 * @param event the event fired.
	 * @param data  the event data.
	 */
	public void onEventFired(String event, Object data)
	{
		KrollProxySupport proxy = (proxySupport != null) ? proxySupport.get() : null;
		if (proxy != null) {
			proxy.onEventFired(event, data);
		}
	}

	/**
	 * Call a function referenced by a property on this object.
	 *
	 * <p>
	 * For example if we have the following JavaScript:
	 *   <pre>
	 *   math.add = function(x, y) { return a + b; }
	 *   </pre>
	 *
	 * To call the "add" function on the "math" object from Java:
	 *   <pre>
	 *   Number sum = (Number) krollObject.callProperty("add", new Object[] { 1, 2});
	 *   </pre>
	 * </p>
	 *
	 * <p>
	 * If the property does not reference a function this method
	 * will return the {@link KrollRuntime.UNDEFINED} value.
	 * </p>
	 *
	 * @param propertyName name of the property that references the function to call
	 * @param args the arguments to pass when calling function
	 * @return the value returned by the function call
	 */
	public abstract Object callProperty(String propertyName, Object[] args);

	/**
	 * Releases this KrollObject, that is, removes event listeners and any associated native views or content.
	 */
	protected void release()
	{
		if (KrollRuntime.getInstance().isOriginThreadForObject(this)) {
			doRelease();

		} else {
			Message message = handler.obtainMessage(MSG_RELEASE, null);
			message.sendToTarget();
		}
	}

	public void setWindow(Object windowProxyObject)
	{
		if (KrollRuntime.getInstance().isOriginThreadForObject(this)) {
			doSetWindow(windowProxyObject);

		} else {
			TiMessenger.sendBlockingRuntimeMessage(handler.obtainMessage(MSG_SET_WINDOW), windowProxyObject);
		}
	}

	public boolean handleMessage(Message msg)
	{
		switch (msg.what) {
			case MSG_RELEASE: {
				doRelease();

				return true;
			}
			case MSG_SET_WINDOW: {
				AsyncResult result = (AsyncResult) msg.obj;
				doSetWindow(result.getArg());
				result.setResult(null);

				return true;
			}
		}

		return false;
	}

	public abstract Object getNativeObject();
	protected abstract void setProperty(String name, Object value);
	protected abstract boolean fireEvent(KrollObject source, String type, Object data, boolean bubbles,
										 boolean reportSuccess, int code, String message);
	protected abstract void doRelease();
	protected abstract void doSetWindow(Object windowProxyObject);
}
