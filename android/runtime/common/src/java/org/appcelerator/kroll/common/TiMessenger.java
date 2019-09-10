/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2015 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.kroll.common;

import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.appcelerator.kroll.KrollObject;
import org.appcelerator.kroll.KrollRuntime;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

/**
 * A messenger interface that maintains a {@link android.os.MessageQueue}, and
 * {@link android.os.Looper} but with better primitives for blocking and single
 * loop iteration. The TiMessenger also provides information on the main and
 * runtime threads and supports posting runnable's on both threads.
 *
 * TiMessengers have one instance per thread tied by a ThreadLocal. The main
 * thread's TiMessenger can be retrieved by calling {@link
 * #getMainMessenger()}.  The runtime thread's TiMessenger can be retrieved by
 * calling {@link #getRuntimeMessenger()}.  A TiMessenger can be lazily created/queried for
 * the current Thread by calling {@link #getMessenger()}.
 *
 * To simply send a message, see {@link #sendMessage(Message)} and {@link
 * #post(Runnable)}.
 *
 * In situations where the current thread needs to be blocked while waiting on
 * another thread to process a message, see {@link
 * #sendBlockingMainMessage(Message, Object)} and {@link
 * #sendBlockingRuntimeMessage(Message, Object)}.
 *
 * To process and dispatch a single message from the message queue, see {@link
 * #dispatchMessage()}.
 */
public class TiMessenger implements Handler.Callback
{
	private static final String TAG = "TiMessenger";
	private static final int MSG_RUN = 3000;

	protected static TiMessenger mainMessenger;
	protected static HashMap<Long, TiMessenger> messengerHashMap = new HashMap<>();

	protected static ThreadLocal<TiMessenger> threadLocalMessenger = new ThreadLocal<TiMessenger>() {
		protected TiMessenger initialValue()
		{
			if (Looper.myLooper() == null) {
				synchronized (threadLocalMessenger)
				{
					if (Looper.myLooper() == null) {
						Looper.prepare();
					}
				}
			}

			TiMessenger messenger = new TiMessenger();

			long currentThreadId = Thread.currentThread().getId();
			if (currentThreadId == Looper.getMainLooper().getThread().getId()) {
				mainMessenger = messenger;
			}
			messengerHashMap.put(currentThreadId, messenger);
			return messenger;
		}
	};

	protected ArrayBlockingQueue<Message> messageQueue = new ArrayBlockingQueue<Message>(10);
	protected CountDownLatch blockingLatch;
	protected AtomicInteger blockingMessageCount = new AtomicInteger(0);
	protected Handler.Callback callback;
	protected long creationThreadId = -1;
	protected Looper looper;
	protected Handler handler;

	public static final int DEFAULT_TIMEOUT = 50;

	public static TiMessenger getMessenger()
	{
		return threadLocalMessenger.get();
	}

	public static TiMessenger getMessenger(long threadId)
	{
		return messengerHashMap.get(threadId);
	}

	public static TiMessenger getMessenger(KrollObject object)
	{
		return messengerHashMap.get(object.getThreadId());
	}

	public static void releaseMessenger(TiMessenger messenger)
	{
		if (messenger == null) {
			return;
		}
		long threadId = messenger.getLooper().getThread().getId();
		messengerHashMap.remove(threadId);
		messenger.getHandler().removeCallbacksAndMessages(null);
	}

	/**
	 * Gets a TiMessenger instance used for sending messages to the main UI thread.
	 * See {@link #sendBlockingRuntimeMessage(Message, Object)} for more details.
	 * <p>
	 * As of Titanium 8.0.0, the JavaScript runtime only supports running on the main thread. This means
	 * that getMainMessenger() and getRuntimeMessenger() will always return the same TiMessenger instance.
	 * @return the main UI thread TiMessenger instance.
	 * @module.api
	 */
	public static TiMessenger getMainMessenger()
	{
		return mainMessenger;
	}

	/**
	 * Gets a TiMessenger instance used for sending messages to the thread that Titanium's JavaScript
	 * runtime is running on. See {@link #sendBlockingRuntimeMessage(Message, Object)} for more details.
	 * <p>
	 * As of Titanium 8.0.0, the JavaScript runtime only supports running on the main thread. This means
	 * that getMainMessenger() and getRuntimeMessenger() will always return the same TiMessenger instance.
	 * @return the KrollRuntime TiMessenger instance.
	 * @module.api
	 */
	public static TiMessenger getRuntimeMessenger()
	{
		return getMainMessenger();
	}

	public static void postOnMain(Runnable runnable)
	{
		TiMessenger messenger = getMainMessenger();
		if (messenger == null) {
			Log.w(TAG, "Unable to post runnable on main thread, main messenger is null");
			return;
		}

		messenger.handler.post(runnable);
	}

	public static void postOnRuntime(Runnable runnable)
	{
		postOnMain(runnable);
	}

	public static void postOnOriginThread(KrollObject object, Runnable runnable)
	{
		if (object == null) {
			Log.w(TAG, "Unable to post runnable, object is null");
			return;
		}
		TiMessenger messenger = TiMessenger.getMessenger(object);
		if (messenger == null) {
			long threadId = object.getThreadId();
			Log.w(TAG, "Unable to post runnable on thread \"" + threadId + "\", messenger is null");
			return;
		}

		messenger.handler.post(runnable);
	}

	/**
	 * Sends a message to an {@link java.util.concurrent.ArrayBlockingQueue#ArrayBlockingQueue(int) ArrayBlockingQueue},
	 * and dispatch messages on the current
	 * queue while blocking on the passed in AsyncResult. The blocking is done on the Main thread.
	 * @param message  the message to send.
	 * @return  The getResult() value of the AsyncResult put on the message.
	 * @module.api
	 */
	public static Object sendBlockingMainMessage(Message message)
	{
		return threadLocalMessenger.get().sendBlockingMessage(message, getMainMessenger(), null, -1);
	}

	/**
	 * Sends a message to an {@link java.util.concurrent.ArrayBlockingQueue#ArrayBlockingQueue(int) ArrayBlockingQueue},
	 * and dispatch messages on the current
	 * queue while blocking on the passed in AsyncResult. The blocking is done on the Main thread.
	 * @param message   the message to send.
	 * @param asyncArg  argument to be added to the AsyncResult.
	 * @return  The getResult() value of the AsyncResult put on the message.
	 * @module.api
	 */
	public static Object sendBlockingMainMessage(Message message, Object asyncArg)
	{
		return threadLocalMessenger.get().sendBlockingMessage(message, getMainMessenger(), asyncArg, -1);
	}

	/**
	 * Sends a message to an {@link java.util.concurrent.ArrayBlockingQueue#ArrayBlockingQueue(int) ArrayBlockingQueue},
	 * and dispatch messages on the current
	 * queue while blocking on the passed in AsyncResult. The blocking is done on the KrollRuntime thread.
	 * @param message  the message to send.
	 * @return  The getResult() value of the AsyncResult put on the message.
	 * @module.api
	 */
	public static Object sendBlockingRuntimeMessage(Message message)
	{
		return sendBlockingMainMessage(message);
	}

	/**
	 * Sends a message to an {@link java.util.concurrent.ArrayBlockingQueue#ArrayBlockingQueue(int) ArrayBlockingQueue},
	 * and dispatch messages on the current
	 * queue while blocking on the passed in AsyncResult. The blocking is done on the KrollRuntime thread.
	 * @param message   the message to send.
	 * @param asyncArg  the argument to be added to AsyncResult.
	 * @return  The getResult() value of the AsyncResult put on the message.
	 * @module.api
	 */
	public static Object sendBlockingRuntimeMessage(Message message, Object asyncArg)
	{
		return sendBlockingMainMessage(message, asyncArg);
	}

	/**
	 * Sends a message to an {@link java.util.concurrent.ArrayBlockingQueue#ArrayBlockingQueue(int) ArrayBlockingQueue},
	 * and dispatch messages on the current
	 * queue while blocking on the passed in AsyncResult. The blocking is done on the KrollRuntime thread.
	 * If maxTimeout > 0, it will throw an error and return when we cannot get the permission from the semaphore within maxTimeout.
	 * @param message   the message to send.
	 * @param asyncArg  the argument to be added to AsyncResult.
	 * @param maxTimeout the maximum time to wait for a permit from the semaphore, in the unit of milliseconds.
	 * @return  The getResult() value of the AsyncResult put on the message.
	 * @module.api
	 */
	public static Object sendBlockingRuntimeMessage(Message message, Object asyncArg, long maxTimeout)
	{
		return threadLocalMessenger.get().sendBlockingMessage(message, getRuntimeMessenger(), asyncArg, maxTimeout);
	}

	private TiMessenger()
	{
		looper = Looper.myLooper();
		handler = new Handler(this);
	}

	/**
	 * @return the native looper. See {@link android.os.Looper} for more details.
	 * @module.api
	 */
	public Looper getLooper()
	{
		return looper;
	}

	public Handler getHandler()
	{
		return handler;
	}

	public static Object sendBlockingMessageToOrigin(KrollObject object, Message message)
	{
		TiMessenger targetMessenger = TiMessenger.getMessenger(object);
		return threadLocalMessenger.get().sendBlockingMessage(message, targetMessenger, null, -1);
	}

	public static Object sendBlockingMessageToOrigin(KrollObject object, Message message, Object asyncArg)
	{
		TiMessenger targetMessenger = TiMessenger.getMessenger(object);
		return threadLocalMessenger.get().sendBlockingMessage(message, targetMessenger, asyncArg, -1);
	}
	public static Object sendBlockingMessageToOrigin(KrollObject object, Message message, Object asyncArg,
													 final long maxTimeout)
	{
		TiMessenger targetMessenger = TiMessenger.getMessenger(object);
		return threadLocalMessenger.get().sendBlockingMessage(message, targetMessenger, asyncArg, maxTimeout);
	}
	/**
	 * Sends a message to an {@link java.util.concurrent.ArrayBlockingQueue#ArrayBlockingQueue(int) ArrayBlockingQueue}, and dispatch messages on the current
	 * queue while blocking on the passed in AsyncResult. If maxTimeout > 0 and the cannot get the permission from
	 * the semaphore within maxTimeout, throw an error and return.
	 * @param message The message to send.
	 * @param targetMessenger The TiMessenger to send it to.
	 * @param asyncArg argument to be added to the AsyncResult put on the message.
	 * @param maxTimeout the maximum time to wait for a permit from the semaphore.
	 * @return The getResult() value of the AsyncResult put on the message.
	 */
	private Object sendBlockingMessage(Message message, TiMessenger targetMessenger, Object asyncArg,
									   final long maxTimeout)
	{
		@SuppressWarnings("serial")
		AsyncResult wrappedAsyncResult = new AsyncResult(asyncArg) {
			@Override
			public Object getResult()
			{
				int timeout = 0;
				long elapsedTime = 0;
				try {
					// TODO: create a multi-semaphore condition
					// here so we don't unnecessarily poll
					while (!tryAcquire(timeout, TimeUnit.MILLISECONDS)) {
						if (messageQueue.size() == 0) {
							timeout = 50;
						} else {
							dispatchPendingMessages();
						}

						elapsedTime += timeout;
						if (maxTimeout > 0 && elapsedTime > maxTimeout) {
							setException(new Throwable("getResult() has timed out."));
							break;
						}
					}
				} catch (InterruptedException e) {
					if (Log.isDebugModeEnabled()) {
						Log.e(TAG, "Interrupted waiting for async result", e);
					}
					dispatchPendingMessages();
				}

				if (exception != null && Log.isDebugModeEnabled()) {
					Log.e(TAG, "Unable to get the result from the blocking message.", exception);
				}

				return result;
			}

			@Override
			public void setResult(Object result)
			{
				super.setResult(result);
			}
		};

		blockingMessageCount.incrementAndGet();
		message.obj = wrappedAsyncResult;
		targetMessenger.sendMessage(message);

		Object messageResult = wrappedAsyncResult.getResult();
		blockingMessageCount.decrementAndGet();
		dispatchPendingMessages();

		return messageResult;
	}

	/**
	 * Sends this message using one three methods:
	 * <ul>
	 * <li>If the current thread is the same thread as the message Handler's
	 * thread, it is dispatched immediately to the Handler</li>
	 * <li>If this TiMessenger is currently blocking, it is pushed into the
	 * internal message queue to be processed by the next call to {@link
	 * #dispatchMessage()}</li>
	 * <li>If this TiMessenger is <b>NOT</b> current blocking, it is queued to
	 * it's Handler normally by using msg.sendToTarget()</li>
	 * </ul>
	 *
	 * @param message The message to send
	 */
	public void sendMessage(Message message)
	{
		Handler target = message.getTarget();
		long currentThreadId = Thread.currentThread().getId();
		long targetThreadId = -1;

		if (target != null) {
			targetThreadId = target.getLooper().getThread().getId();
		}

		if (target != null && currentThreadId == targetThreadId) {
			target.dispatchMessage(message);

		} else {
			if (isBlocking()) {
				try {
					messageQueue.put(message);

				} catch (InterruptedException e) {
					Log.w(TAG, "Interrupted trying to put new message, sending to handler", e);
					message.sendToTarget();
				}

			} else {
				message.sendToTarget();
			}
		}
	}

	public void post(Runnable runnable)
	{
		sendMessage(handler.obtainMessage(MSG_RUN, runnable));
	}

	public void setCallback(Handler.Callback callback)
	{
		this.callback = callback;
	}

	public boolean handleMessage(Message message)
	{
		if (message.what == MSG_RUN) {
			((Runnable) message.obj).run();

			return true;
		}

		if (callback != null) {
			return callback.handleMessage(message);
		}

		return false;
	}

	public void resetLatch()
	{
		blockingLatch = new CountDownLatch(1);
	}

	public boolean isBlocking()
	{
		return blockingMessageCount.get() > 0;
	}

	public void dispatchPendingMessages()
	{
		while (true) {
			if (!dispatchMessage()) {
				break;
			}
		}
	}

	public boolean dispatchMessage()
	{
		Message message = messageQueue.poll();

		if (message == null) {
			return false;
		}

		if (message.getTarget() != null) {
			message.getTarget().dispatchMessage(message);
			message.recycle();
			return true;
		}

		return false;
	}

	public boolean dispatchMessage(int timeout, TimeUnit timeUnit)
	{
		try {
			Message message = messageQueue.poll(timeout, timeUnit);
			if (message != null) {
				Log.d(TAG, "Dispatching message: " + message, Log.DEBUG_MODE);

				if (message.getTarget() != null) {
					message.getTarget().dispatchMessage(message);
					message.recycle();
					return true;
				}

				return false;
			}

		} catch (InterruptedException e) {
			// ignore
		}
		return false;
	}
}
