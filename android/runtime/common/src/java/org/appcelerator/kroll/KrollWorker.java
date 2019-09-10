/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-present by Axway Appcelerator. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package org.appcelerator.kroll;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import org.appcelerator.kroll.KrollRuntime;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiMessenger;

import java.util.LinkedList;

abstract public class KrollWorker extends HandlerThread implements Handler.Callback
{
	private static final String TAG = "V8Worker";
	private Handler mWorkerHandler;
	private volatile LinkedList<Message> messageQueue = new LinkedList<Message>();
	private TiMessenger messenger;
	private KrollRuntime.State runtimeState = KrollRuntime.State.DISPOSED;
	private long threadId = -1;
	protected String url;
	protected String name;

	private static final int MSG_POST_MESSAGE = 100;
	private static final int MSG_DISPOSE = 101;

	public KrollWorker(String url, String name)
	{
		super(name);
		this.url = url;
		this.name = name;
	}

	abstract public boolean tryExecScript();

	abstract public void handleMessage(Object message);

	abstract public void globalClose();

	abstract public void globalPostMessage(Object message);

	@Override
	protected void onLooperPrepared()
	{
		threadId = Thread.currentThread().getId();
		messenger = TiMessenger.getMessenger();
		if (!tryExecScript()) {
			this.quit();
			return;
		}

		while (!messageQueue.isEmpty()) {
			Message message = messageQueue.removeFirst();
			handleMessage(message);
		}
		mWorkerHandler = new Handler(getLooper(), this);
		runtimeState = KrollRuntime.State.INITIALIZED;
	}

	@Override
	public boolean handleMessage(Message msg)
	{
		switch (msg.what) {
			case MSG_POST_MESSAGE: {
				handleMessage(msg.obj);
				return true;
			}
			case MSG_DISPOSE: {
				handleDispose();
				return true;
			}
		}
		return false;
	}

	public void handleDispose()
	{
		synchronized (runtimeState)
		{
			if (runtimeState == KrollRuntime.State.DISPOSED) {
				return;
			}
			runtimeState = KrollRuntime.State.DISPOSED;
		}
		if (messenger != null) {
			TiMessenger.releaseMessenger(messenger);
			messenger = null;
		}
		if (mWorkerHandler != null) {
			mWorkerHandler.removeCallbacksAndMessages(null);
			mWorkerHandler = null;
		}
		this.quit();
	}

	public void postMessage(Object message)
	{
		if (mWorkerHandler != null) {
			mWorkerHandler.obtainMessage(MSG_POST_MESSAGE, message).sendToTarget();
		} else {
			Message msg = new Message();
			msg.what = MSG_POST_MESSAGE;
			msg.obj = message;
			messageQueue.add(msg);
		}
	}

	public void terminate()
	{
		Log.d(TAG, "Disposing runtime.", Log.DEBUG_MODE);

		// Set state to released when since we have not fully disposed of it yet
		synchronized (runtimeState)
		{
			if (runtimeState == KrollRuntime.State.DISPOSED) {
				return;
			}
			runtimeState = KrollRuntime.State.RELEASED;
		}

		if (Thread.currentThread().getId() == threadId) {
			handleDispose();
		} else {
			if (mWorkerHandler != null) {
				mWorkerHandler.sendEmptyMessage(MSG_DISPOSE);
			} else {
				Message msg = new Message();
				msg.what = MSG_DISPOSE;
				messageQueue.add(msg);
			}
		}
	}
}
