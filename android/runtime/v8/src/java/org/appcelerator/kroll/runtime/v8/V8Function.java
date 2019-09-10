/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.kroll.runtime.v8;

import java.util.HashMap;

import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollObject;
import org.appcelerator.kroll.KrollRuntime;
import org.appcelerator.kroll.common.AsyncResult;
import org.appcelerator.kroll.common.TiMessenger;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class V8Function extends V8Object implements KrollFunction, Handler.Callback
{
	private static final String TAG = "V8Function";

	protected static final int MSG_CALL_SYNC = V8Object.MSG_LAST_ID + 100;
	protected static final int MSG_LAST_ID = MSG_CALL_SYNC;

	public V8Function(long pointer)
	{
		super(pointer);
	}

	public Object call(KrollObject krollObject, HashMap args)
	{
		return call(krollObject, new Object[] { args });
	}

	public Object call(KrollObject krollObject, Object[] args)
	{
		if (KrollRuntime.getInstance().isOriginThreadForObject(this)) {
			return callSync(krollObject, args);

		} else {
			return TiMessenger.sendBlockingMessageToOrigin(this, handler.obtainMessage(MSG_CALL_SYNC),
														   new FunctionArgs(krollObject, args));
		}
	}

	private Object callSync(KrollObject krollObject, Object[] args)
	{
		// FIXME: check
		if (!KrollRuntime.isInitialized()) {
			Log.w(TAG, "Runtime disposed, cannot call function.");
			return null;
		}
		return nativeInvoke(((V8Object) krollObject).getPointer(), getPointer(), args);
	}

	public void callAsync(KrollObject krollObject, HashMap args)
	{
		callAsync(krollObject, new Object[] { args });
	}

	public void callAsync(final KrollObject krollObject, final Object[] args)
	{
		TiMessenger.postOnOriginThread(this, new Runnable() {
			public void run()
			{
				call(krollObject, args);
			}
		});
	}

	@Override
	public boolean handleMessage(Message message)
	{
		switch (message.what) {
			case MSG_CALL_SYNC: {
				AsyncResult asyncResult = ((AsyncResult) message.obj);
				FunctionArgs functionArgs = (FunctionArgs) asyncResult.getArg();
				asyncResult.setResult(callSync(functionArgs.krollObject, functionArgs.args));

				return true;
			}
		}

		return super.handleMessage(message);
	}

	@Override
	public void doRelease()
	{
		long functionPointer = getPointer();
		if (functionPointer == 0) {
			return;
		}

		nativeRelease(functionPointer);
	}

	@Override
	protected void finalize() throws Throwable
	{
		super.finalize();
	}

	// JNI method prototypes
	private native Object nativeInvoke(long thisPointer, long functionPointer, Object[] functionArgs);

	private static native void nativeRelease(long functionPointer);
}
