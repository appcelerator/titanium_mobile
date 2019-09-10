package org.appcelerator.kroll.runtime.v8;

import android.os.Message;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollPromise;
import org.appcelerator.kroll.KrollRuntime;
import org.appcelerator.kroll.common.AsyncResult;
import org.appcelerator.kroll.common.TiMessenger;

public class V8Promise extends V8Object implements KrollPromise
{
	private static final String TAG = "V8Promise";

	protected static final int MSG_RESOLVE = V8Object.MSG_LAST_ID + 100;
	protected static final int MSG_REJECT = V8Object.MSG_LAST_ID + 101;
	protected static final int MSG_LAST_ID = MSG_REJECT;

	public V8Promise(long resolver)
	{
		super(resolver);
	}

	@Override
	public void resolve(Object value)
	{
		if (KrollRuntime.getInstance().isOriginThreadForObject(this)) {
			nativeResolve(getPointer(), value);
		} else {
			TiMessenger.sendBlockingMessageToOrigin(this, handler.obtainMessage(MSG_RESOLVE), value);
		}
	}

	@Override
	public void reject(Object value)
	{
		if (KrollRuntime.getInstance().isOriginThreadForObject(this)) {
			nativeReject(getPointer(), value);
		} else {
			TiMessenger.sendBlockingMessageToOrigin(this, handler.obtainMessage(MSG_REJECT), value);
		}
	}

	@Override
	public boolean handleMessage(Message message)
	{
		switch (message.what) {
			case MSG_RESOLVE: {
				AsyncResult asyncResult = ((AsyncResult) message.obj);
				Object value = asyncResult.getArg();
				nativeResolve(getPointer(), value);
				asyncResult.setResult(null);
				return true;
			}
			case MSG_REJECT: {
				AsyncResult asyncResult = ((AsyncResult) message.obj);
				Object value = asyncResult.getArg();
				nativeReject(getPointer(), value);
				asyncResult.setResult(null);
				return true;
			}
		}

		return super.handleMessage(message);
	}

	@Override
	public void doRelease()
	{
		long resolverPointer = getPointer();
		if (resolverPointer == 0) {
			return;
		}

		nativeRelease(resolverPointer);
	}

	// JNI method prototypes
	private native void nativeResolve(long resolver, Object value);

	private native void nativeReject(long resolver, Object value);

	private native void nativeRelease(long resolver);
}
