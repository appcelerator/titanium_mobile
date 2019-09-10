/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2016-2017 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.kroll.runtime.v8;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.KrollProxySupport;
import org.appcelerator.kroll.KrollObject;
import org.appcelerator.kroll.common.Log;

public final class ReferenceTable
{
	private static String TAG = "ReferenceTable";

	/**
	 * A simple Map used to hold strong/weak reference to the Java objects we have paired/wrapped in native
	 * titanium::Proxy/JavaObject instances.
	 */
	private static HashMap<Long, Object> references = new HashMap<Long, Object>();

	/**
	 * Incrementing key, used to generate new keys when a new strong reference is created. FIXME Handle "wrapping" the
	 * value around to Long.MIN_VALUE when Long.MAX_VALUE is reached? What if we ever reach back up to 0 - we need to
	 * skip it!
	 */
	private static AtomicLong lastKey = new AtomicLong(1);

	/**
	 * Creates a new strong reference. Done when attaching a native Proxy to a Java object.
	 *
	 * @param object the object to reference and retain
	 * @return an unique key for this reference
	 */
	public static long createReference(Object object)
	{
		long key = lastKey.getAndIncrement();
		Log.d(TAG, "Creating strong reference for key: " + key, Log.DEBUG_MODE);
		references.put(key, object);
		return key;
	}

	/**
	 * Destroy the reference in our map. Done when we're deleting the native proxy.
	 *
	 * @param key the key for the reference to destroy.
	 */
	public static void destroyReference(long key)
	{
		Log.d(TAG, "Destroying reference under key: " + key, Log.DEBUG_MODE);
		Object obj = getReference(key);
		// If it's an V8Object, set the ptr to 0, because the proxy is dead on C++ side
		// This *should* prevent the native code from trying to reconstruct the proxy for any reason
		if (obj instanceof KrollProxySupport) {
			KrollProxySupport proxy = (KrollProxySupport) obj;
			KrollObject ko = proxy.getKrollObject();
			if (ko instanceof V8Object) {
				V8Object v8 = (V8Object) ko;
				v8.setPointer(0);
			}
		}
		references.remove(key);
	}

	/**
	 * Makes the reference "weak" which allows it to be collected if no other references remain.
	 *
	 * @param key the key for the reference to weaken.
	 */
	public static void makeWeakReference(long key)
	{
		Log.d(TAG, "Downgrading to weak reference for key: " + key, Log.DEBUG_MODE);
		Object ref = getReference(key);
		references.remove(key);
		references.put(key, new WeakReference<Object>(ref));
	}

	/**
	 * Makes the reference "soft" which which are cleared at the discretion of the garbage collector.
	 *
	 * @param key the key for the reference to soften.
	 */
	public static void makeSoftReference(long key)
	{
		Log.d(TAG, "Downgrading to soft reference for key: " + key, Log.DEBUG_MODE);
		Object ref = getReference(key);
		references.remove(key);
		references.put(key, new SoftReference<Object>(ref));
	}

	/**
	 * Make the reference strong again to prevent future collection. This method will return null if the reference
	 * was invalidated and the object collected.
	 *
	 * @param key the key for the reference.
	 * @return the referenced object if the reference is still valid.
	 */
	public static Object clearReference(long key)
	{
		Log.d(TAG, "Upgrading reference to strong for key: " + key, Log.DEBUG_MODE);
		Object ref = getReference(key);
		references.remove(key);
		references.put(key, ref);
		return ref;
	}

	/**
	 * Returns the referenced object if it's still 'alive'. It may have been a WeakReference that has been GC'd, in
	 * which case we'd return null.
	 *
	 * @param key the key of the reference.
	 * @return the object if the reference is still valid, otherwise null.
	 */
	public static Object getReference(long key)
	{
		Object ref = references.get(key);
		if (ref instanceof Reference) {
			ref = ((Reference<?>) ref).get();
		}
		return ref;
	}

	/**
	 * Determines if the reference is strong
	 *
	 * @param key the key for the reference.
	 * @return returns true if the reference is strong
	 */
	public static boolean isStrongReference(long key)
	{
		Object ref = getReference(key);
		return !(ref instanceof Reference);
	}
}
