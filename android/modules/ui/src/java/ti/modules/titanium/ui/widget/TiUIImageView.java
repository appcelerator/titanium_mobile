/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.common.AsyncResult;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiMessenger;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiLifecycle.OnLifecycleEvent;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiDownloadListener;
import org.appcelerator.titanium.util.TiDownloadManager;
import org.appcelerator.titanium.util.TiImageLruCache;
import org.appcelerator.titanium.util.TiLoadImageListener;
import org.appcelerator.titanium.util.TiLoadImageManager;
import org.appcelerator.titanium.util.TiResponseCache;
import org.appcelerator.titanium.util.TiUrl;
import org.appcelerator.titanium.view.TiDrawableReference;
import org.appcelerator.titanium.view.TiUIView;

import ti.modules.titanium.filesystem.FileProxy;
import ti.modules.titanium.ui.ImageViewProxy;
import ti.modules.titanium.ui.ScrollViewProxy;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Bundle;
import android.view.View;
import android.view.ViewParent;

public class TiUIImageView extends TiUIView implements OnLifecycleEvent, Handler.Callback
{
	private static final String TAG = "TiUIImageView";
	private static final int FRAME_QUEUE_SIZE = 5;
	public static final int MIN_DURATION = 30;
	public static final int DEFAULT_DURATION = 200;

	private Timer timer;
	private Animator animator;
	private Loader loader;
	private Thread loaderThread;
	private final AtomicBoolean animating = new AtomicBoolean(false);
	private final AtomicBoolean isLoading = new AtomicBoolean(false);
	private final AtomicBoolean isStopping = new AtomicBoolean(false);
	private boolean reverse = false;
	private boolean paused = false;
	private boolean firedLoad;
	private ImageViewProxy imageViewProxy;
	private int currentDuration;
	private TiImageView view;

	private ArrayList<TiDrawableReference> imageSources;
	private TiDrawableReference defaultImageSource;
	private TiDownloadListener downloadListener;
	private TiLoadImageListener loadImageListener;
	private final Object releasedLock = new Object();

	private Handler mainHandler = new Handler(Looper.getMainLooper(), this);
	private static final int SET_IMAGE = 10001;
	private static final int START = 10002;
	private static final int STOP = 10003;
	private static final int SET_TINT = 10004;

	// This handles the memory cache of images.
	private final TiImageLruCache mMemoryCache = TiImageLruCache.getInstance();

	public TiUIImageView(final TiViewProxy proxy)
	{
		super(proxy);
		imageViewProxy = (ImageViewProxy) proxy;

		Log.d(TAG, "Creating an ImageView", Log.DEBUG_MODE);

		view = new TiImageView(proxy.getActivity(), proxy);

		downloadListener = new TiDownloadListener() {
			@Override
			public void downloadTaskFinished(URI uri)
			{
				if (!TiResponseCache.peek(uri)) {
					// The requested image did not make it into our TiResponseCache,
					// possibly because it had a header forbidding that. Now get it
					// via the "old way" (not relying on cache).
					TiLoadImageManager.getInstance().load(TiDrawableReference.fromUrl(imageViewProxy, uri.toString()),
														  loadImageListener);
				}
			}

			@Override
			public void downloadTaskFailed(URI uri)
			{
				// If the download failed, fire an error event
				fireError("Download Failed", uri.toString());
			}

			// Handle decoding and caching in the background thread so it won't block UI.
			@Override
			public void postDownload(URI uri)
			{
				if (TiResponseCache.peekFollowingRedirects(uri)) {
					handleCacheAndSetImage(TiDrawableReference.fromUrl(imageViewProxy, uri.toString()));
				}
			}
		};

		loadImageListener = new TiLoadImageListener() {
			@Override
			public void loadImageFinished(int hash, Bitmap bitmap)
			{
				// Cache the image
				if (bitmap != null) {
					if (mMemoryCache.get(hash) == null) {
						mMemoryCache.put(hash, bitmap);
					}

					// Update UI if the current image source has not been changed.
					if (imageSources != null && imageSources.size() == 1) {
						TiDrawableReference imgsrc = imageSources.get(0);
						if (imgsrc == null) {
							return;
						}
						if (imgsrc.hashCode() == hash
							|| (imgsrc.getUrl() != null
								&& TiDrawableReference
										   .fromUrl(imageViewProxy, TiUrl.getCleanUri(imgsrc.getUrl()).toString())
										   .hashCode()
									   == hash)) {
							setImage(bitmap);
							if (!firedLoad) {
								fireLoad(TiC.PROPERTY_IMAGE);
								firedLoad = true;
							}
						}
					}
				}
			}

			@Override
			public void loadImageFailed()
			{
				Log.w(TAG, "Unable to load image", Log.DEBUG_MODE);
			}
		};

		setNativeView(view);
		// TODO proxy.getActivity().addOnLifecycleEventListener(this);
	}

	@Override
	protected void applyContentDescription()
	{
		if (proxy == null || nativeView == null) {
			return;
		}
		String contentDescription = composeContentDescription();
		if (contentDescription != null) {
			this.view.getImageView().setContentDescription(contentDescription);
		}
	}

	@Override
	protected void applyContentDescription(KrollDict properties)
	{
		if (proxy == null || nativeView == null) {
			return;
		}
		String contentDescription = composeContentDescription(properties);
		if (contentDescription != null) {
			this.view.getImageView().setContentDescription(contentDescription);
		}
	}

	@Override
	public void setProxy(TiViewProxy proxy)
	{
		super.setProxy(proxy);
		imageViewProxy = (ImageViewProxy) proxy;
	}

	private TiImageView getView()
	{
		return this.view;
	}

	protected View getParentView()
	{
		if (nativeView == null) {
			return null;
		}

		ViewParent parent = nativeView.getParent();
		if (parent instanceof View) {
			return (View) parent;
		}
		if (parent == null) {
			TiViewProxy parentProxy = proxy.getParent();
			if (parentProxy != null) {
				TiUIView parentTiUi = parentProxy.peekView();
				if (parentTiUi != null) {
					return parentTiUi.getNativeView();
				}
			}
		}
		return null;
	}

	public boolean handleMessage(Message msg)
	{
		switch (msg.what) {

			case SET_IMAGE:
				AsyncResult result = (AsyncResult) msg.obj;
				handleSetImage((Bitmap) result.getArg());
				result.setResult(null);
				return true;
			case START:
				handleStart();
				return true;
			case STOP:
				handleStop();
				return true;
			case SET_TINT:
				handleTint((String) msg.obj);
				return true;

			default:
				return false;
		}
	}

	private void handleCacheAndSetImage(TiDrawableReference imageref)
	{
		// Don't update UI if the current image source has been changed.
		if (imageSources != null && imageSources.size() == 1) {
			TiDrawableReference imgsrc = imageSources.get(0);
			if (imgsrc == null || imgsrc.getUrl() == null) {
				return;
			}
			if (imageref.equals(imgsrc)
				|| imageref.equals(
					   TiDrawableReference.fromUrl(imageViewProxy, TiUrl.getCleanUri(imgsrc.getUrl()).toString()))) {
				int hash = imageref.hashCode();
				Bitmap bitmap = imageref.getBitmap(true);
				if (bitmap != null) {
					if (mMemoryCache.get(hash) == null) {
						mMemoryCache.put(hash, bitmap);
					}
					setImage(bitmap);
					if (!firedLoad) {
						fireLoad(TiC.PROPERTY_IMAGE);
						firedLoad = true;
					}
				}
			}
		}
	}

	private void setImage(final Bitmap bitmap)
	{
		if (!TiApplication.isUIThread()) {
			TiMessenger.sendBlockingMainMessage(mainHandler.obtainMessage(SET_IMAGE), bitmap);
		} else {
			handleSetImage(bitmap);
		}
	}

	private void handleSetImage(final Bitmap bitmap)
	{
		TiImageView view = getView();
		if (view != null) {
			view.setImageBitmap(bitmap);
		}
	}

	private static class BitmapWithIndex
	{
		public BitmapWithIndex(Bitmap b, int i)
		{
			this.bitmap = b;
			this.index = i;
		}

		public Bitmap bitmap;
		public int index;
	}

	private class Loader implements Runnable
	{
		private ArrayBlockingQueue<BitmapWithIndex> bitmapQueue;
		private LinkedList<Integer> hashTable;
		private int waitTime = 0;
		private int sleepTime = 50; //ms
		private int repeatIndex = 0;

		public Loader()
		{
			bitmapQueue = new ArrayBlockingQueue<BitmapWithIndex>(FRAME_QUEUE_SIZE);
			hashTable = new LinkedList<Integer>();
		}

		private boolean isRepeating()
		{
			int repeatCount = getRepeatCount();
			if (repeatCount <= 0) {
				return true;
			}
			return repeatIndex < repeatCount;
		}

		private int getStart()
		{
			if (imageSources == null) {
				return 0;
			}
			if (reverse) {
				return imageSources.size() - 1;
			}
			return 0;
		}

		private boolean isNotFinalFrame(int frame)
		{
			synchronized (releasedLock)
			{
				if (imageSources == null) {
					return false;
				}
				if (reverse) {
					return frame >= 0;
				}
				return frame < imageSources.size();
			}
		}

		private int getCounter()
		{
			if (reverse) {
				return -1;
			}
			return 1;
		}

		public void run()
		{
			if (getProxy() == null) {
				Log.d(TAG, "Multi-image loader exiting early because proxy has been gc'd");
				return;
			}
			repeatIndex = 0;
			isLoading.set(true);
			firedLoad = false;
			boolean shouldCache = getRepeatCount() >= 5 ? true : false;
		topLoop:
			while (isRepeating()) {

				if (imageSources == null) {
					break;
				}
				long time = System.currentTimeMillis();
				for (int j = getStart(); imageSources != null && isNotFinalFrame(j); j += getCounter()) {
					if (bitmapQueue.size() == FRAME_QUEUE_SIZE && !firedLoad) {
						fireLoad(TiC.PROPERTY_IMAGES);
						firedLoad = true;
					}
					if (paused && !Thread.currentThread().isInterrupted()) {
						try {
							Log.i(TAG, "Pausing", Log.DEBUG_MODE);
							// User backed-out while animation running
							if (loader == null) {
								break;
							}

							synchronized (this)
							{
								wait();
							}

							Log.i(TAG, "Waking from pause.", Log.DEBUG_MODE);
							// In the meantime, while paused, user could have backed out, which leads
							// to release(), which in turn leads to nullified imageSources.
							if (imageSources == null) {
								break topLoop;
							}
						} catch (InterruptedException e) {
							Log.w(TAG, "Interrupted from paused state.");
						}
					}

					if (!isLoading.get() || isStopping.get()) {
						break topLoop;
					}

					waitTime = 0;
					synchronized (releasedLock)
					{
						if (imageSources == null || j >= imageSources.size()) {
							break topLoop;
						}
						TiDrawableReference imageRef = imageSources.get(j);
						Bitmap b = null;
						if (shouldCache) {
							int hash = imageRef.hashCode();
							b = mMemoryCache.get(hash);
							if (b == null) {
								Log.i(TAG, "Image isn't cached");
								b = imageRef.getBitmap(true);
								mMemoryCache.put(hash, b);
								hashTable.add(hash);
							}
						} else {
							b = imageRef.getBitmap(true);
						}
						BitmapWithIndex bIndex = new BitmapWithIndex(b, j);
						while (waitTime < getDuration() * imageSources.size()) {
							try {
								if (!bitmapQueue.offer(bIndex)) {
									if (isStopping.get()) {
										break;
									}
									Thread.sleep(sleepTime);
									waitTime += sleepTime;

								} else {
									break;
								}

							} catch (InterruptedException e) {
								Log.w(TAG, "Interrupted while adding Bitmap into bitmapQueue");
								break;
							}
						}
					}
					repeatIndex++;
				}

				Log.d(TAG, "TIME TO LOAD FRAMES: " + (System.currentTimeMillis() - time) + "ms", Log.DEBUG_MODE);
			}
			isLoading.set(false);
			//clean out the cache after animation
			while (!hashTable.isEmpty()) {
				mMemoryCache.remove(hashTable.pop());
			}
		}

		public ArrayBlockingQueue<BitmapWithIndex> getBitmapQueue()
		{
			return bitmapQueue;
		}
	}

	private void setImages()
	{
		if (imageSources == null || imageSources.size() == 0) {
			fireError("Missing Images", null);
			return;
		}

		if (loader == null) {
			paused = false;
			isStopping.set(false);
			firedLoad = false;
			loader = new Loader();
			loaderThread = new Thread(loader);
			Log.d(TAG, "STARTING LOADER THREAD " + loaderThread + " for " + this, Log.DEBUG_MODE);
			loaderThread.start();
		}
	}

	public double getDuration()
	{
		if (proxy.getProperty(TiC.PROPERTY_DURATION) != null) {
			double duration = TiConvert.toDouble(proxy.getProperty(TiC.PROPERTY_DURATION));
			if (duration < MIN_DURATION) {
				return MIN_DURATION;
			} else {
				return duration;
			}
		}
		proxy.setProperty(TiC.PROPERTY_DURATION, DEFAULT_DURATION);

		return DEFAULT_DURATION;
	}

	public int getRepeatCount()
	{
		if (proxy.hasProperty(TiC.PROPERTY_REPEAT_COUNT)) {
			return TiConvert.toInt(proxy.getProperty(TiC.PROPERTY_REPEAT_COUNT));
		}
		return 0;
	}

	private void fireLoad(String state)
	{
		KrollDict data = new KrollDict();
		data.put(TiC.EVENT_PROPERTY_STATE, state);
		fireEvent(TiC.EVENT_LOAD, data);
	}

	private void fireStart()
	{
		KrollDict data = new KrollDict();
		fireEvent(TiC.EVENT_START, data);
	}

	private void fireChange(int index)
	{
		KrollDict data = new KrollDict();
		data.put(TiC.EVENT_PROPERTY_INDEX, index);
		fireEvent(TiC.EVENT_CHANGE, data);
	}

	private void fireStop()
	{
		KrollDict data = new KrollDict();
		fireEvent(TiC.EVENT_STOP, data);
	}

	private void fireError(String message, String imageUrl)
	{
		KrollDict data = new KrollDict();

		data.putCodeAndMessage(TiC.ERROR_CODE_UNKNOWN, message);
		if (imageUrl != null) {
			data.put(TiC.PROPERTY_IMAGE, imageUrl);
		}
		fireEvent(TiC.EVENT_ERROR, data);
	}

	private class Animator extends TimerTask
	{
		private Loader loader;

		public Animator(Loader loader)
		{
			this.loader = loader;
		}

		public void run()
		{
			boolean waitOnResume = false;
			try {
				if (paused) {
					synchronized (this)
					{
						KrollDict data = new KrollDict();
						fireEvent(TiC.EVENT_PAUSE, data);
						waitOnResume = true;
						wait();
					}
				}

				ArrayBlockingQueue<BitmapWithIndex> bitmapQueue = loader.getBitmapQueue();
				//Fire stop event when animation finishes
				if (!isLoading.get() && bitmapQueue.isEmpty()) {
					fireStop();
				}
				BitmapWithIndex b = bitmapQueue.take();
				Log.d(TAG, "set image: " + b.index, Log.DEBUG_MODE);
				setImage(b.bitmap);
				fireChange(b.index);

				// When the animation is paused, the timer will pause in the middle of a period.
				// When the animation resumes, the timer resumes from where it left off. As a result, it will look like
				// one frame is left out when resumed (TIMOB-10207).
				// To avoid this, we force the thread to wait for one period on resume.
				if (waitOnResume) {
					Thread.sleep(currentDuration);
					waitOnResume = false;
				}
			} catch (InterruptedException e) {
				Log.e(TAG, "Loader interrupted");
			}
		}
	}

	public void start()
	{
		if (!TiApplication.isUIThread()) {
			Message message = mainHandler.obtainMessage(START);
			message.sendToTarget();
		} else {
			handleStart();
		}
	}

	public void handleStart()
	{
		if (animator == null) {
			timer = new Timer();

			if (loader == null) {
				loader = new Loader();
				loaderThread = new Thread(loader);
				Log.d(TAG, "STARTING LOADER THREAD " + loaderThread + " for " + this, Log.DEBUG_MODE);
			}

			animator = new Animator(loader);
			if (!animating.get() && !loaderThread.isAlive()) {
				isStopping.set(false);
				loaderThread.start();
			}

			currentDuration = (int) getDuration();

			animating.set(true);
			fireStart();
			timer.schedule(animator, currentDuration, currentDuration);
		} else {
			resume();
		}
	}

	public void pause()
	{
		paused = true;
	}

	public void resume()
	{
		paused = false;

		if (animator != null) {
			synchronized (animator)
			{
				animator.notify();
			}
		}

		if (loader != null) {
			synchronized (loader)
			{
				loader.notify();
			}
		}
	}

	public void stop()
	{
		if (!TiApplication.isUIThread()) {
			Message message = mainHandler.obtainMessage(STOP);
			message.sendToTarget();
		} else {
			handleStop();
		}
	}
	public void handleStop()
	{
		if (timer != null) {
			timer.cancel();
		}
		animating.set(false);
		isStopping.set(true);

		if (loaderThread != null) {
			try {
				loaderThread.join();
			} catch (InterruptedException e) {
				Log.e(TAG, "LoaderThread termination interrupted");
			}
			loaderThread = null;
		}
		if (loader != null) {
			synchronized (loader)
			{
				loader.notify();
			}
		}

		loader = null;
		timer = null;
		animator = null;
		paused = false;

		fireStop();
	}

	private void setImageSource(Object object)
	{
		imageSources = new ArrayList<>();
		if (object instanceof Object[]) {
			for (Object o : (Object[]) object) {
				imageSources.add(TiDrawableReference.fromObject(getProxy(), o));
			}
		} else {
			imageSources.add(TiDrawableReference.fromObject(getProxy(), object));
		}
	}

	private void setImageSource(TiDrawableReference source)
	{
		imageSources = new ArrayList<>();
		imageSources.add(source);
	}

	private void setDefaultImageSource(Object object)
	{
		if (object instanceof FileProxy) {
			defaultImageSource = TiDrawableReference.fromFile(proxy.getActivity(), ((FileProxy) object).getBaseFile());
		} else if (object instanceof String) {
			defaultImageSource = TiDrawableReference.fromUrl(proxy, (String) object);
		} else {
			defaultImageSource = TiDrawableReference.fromObject(proxy, object);
		}
	}

	private void setImageInternal()
	{
		// Set default image or clear previous image first.
		if (defaultImageSource != null) {
			setDefaultImage();
		} else {
			setImage(null);
		}

		if (imageSources == null || imageSources.size() == 0 || imageSources.get(0) == null
			|| imageSources.get(0).isTypeNull()) {
			return;
		}

		if (imageSources.size() == 1) {
			TiDrawableReference imageref = imageSources.get(0);

			// Check if the image is cached in memory
			int hash = imageref.hashCode();
			Bitmap bitmap = mMemoryCache.get(hash);
			if (bitmap != null) {
				if (!bitmap.isRecycled()) {
					setImage(bitmap);
					if (!firedLoad) {
						fireLoad(TiC.PROPERTY_IMAGE);
						firedLoad = true;
					}
					return;
				} else { // If the cached image has been recycled, remove it from the cache.
					mMemoryCache.remove(hash);
				}
			}

			if (imageref.isNetworkUrl()) {
				boolean isCachedInDisk = false;
				URI uri = null;
				try {
					String imageUrl = TiUrl.getCleanUri(imageref.getUrl()).toString();
					uri = new URI(imageUrl);
					isCachedInDisk = TiResponseCache.peekFollowingRedirects(uri);
				} catch (URISyntaxException e) {
					Log.e(TAG, "URISyntaxException for url " + imageref.getUrl(), e);
				} catch (NullPointerException e) {
					Log.e(TAG, "NullPointerException for url " + imageref.getUrl(), e);
				}

				// Check if the image is not cached in disc and the uri is valid.
				if (!isCachedInDisk && uri != null) {
					TiDownloadManager.getInstance().download(uri, downloadListener);
				} else {
					// If the image has been cached in disk or the uri is not valid,
					// fetch and cache it and update the UI.
					TiLoadImageManager.getInstance().load(imageref, loadImageListener);
				}
			} else {
				TiLoadImageManager.getInstance().load(imageref, loadImageListener);
			}
		} else {
			setImages();
		}
	}

	private void setDefaultImage()
	{
		if (defaultImageSource == null) {
			setImage(null);
			return;
		}
		// Have to set default image in the UI thread to make sure it shows before the image
		// is ready. Don't need to retry decode because we don't want to block UI.
		setImage(defaultImageSource.getBitmap(false));
	}

	@Override
	public void processProperties(KrollDict d)
	{
		boolean heightDefined = false;
		boolean widthDefined = false;
		TiImageView view = getView();

		if (view == null) {
			return;
		}

		if (d.containsKey(TiC.PROPERTY_WIDTH)) {
			String widthProperty = d.getString(TiC.PROPERTY_WIDTH);
			widthDefined = !TiC.LAYOUT_SIZE.equals(widthProperty) && !TiC.SIZE_AUTO.equals(widthProperty);
			view.setWidthDefined(widthDefined);
		}
		if (d.containsKey(TiC.PROPERTY_HEIGHT)) {
			String heightProperty = d.getString(TiC.PROPERTY_HEIGHT);
			heightDefined = !TiC.LAYOUT_SIZE.equals(heightProperty) && !TiC.SIZE_AUTO.equals(heightProperty);
			view.setHeightDefined(heightDefined);
		}

		if (d.containsKey(TiC.PROPERTY_LEFT) && d.containsKey(TiC.PROPERTY_RIGHT)) {
			view.setWidthDefined(true);
		}

		if (d.containsKey(TiC.PROPERTY_TOP) && d.containsKey(TiC.PROPERTY_BOTTOM)) {
			view.setHeightDefined(true);
		}

		if (d.containsKey(TiC.PROPERTY_IMAGES)) {
			setImageSource(d.get(TiC.PROPERTY_IMAGES));
			setImages();
		}
		if (d.containsKey(TiC.PROPERTY_ENABLE_ZOOM_CONTROLS)) {
			view.setEnableZoomControls(TiConvert.toBoolean(d, TiC.PROPERTY_ENABLE_ZOOM_CONTROLS, true));
		}
		if (d.containsKey(TiC.PROPERTY_DEFAULT_IMAGE)) {
			setDefaultImageSource(d.get(TiC.PROPERTY_DEFAULT_IMAGE));
		}
		if (d.containsKey(TiC.PROPERTY_IMAGE)) {
			// processProperties is also called from TableView, we need check if we changed before re-creating the
			// bitmap
			boolean changeImage = true;
			TiDrawableReference source = TiDrawableReference.fromObject(getProxy(), d.get(TiC.PROPERTY_IMAGE));
			if (imageSources != null && imageSources.size() == 1) {
				if (imageSources.get(0).equals(source)) {
					changeImage = false;
				}
			}
			if (changeImage) {
				// Check for orientation and decodeRetries only if an image is specified
				Object autoRotate = d.get(TiC.PROPERTY_AUTOROTATE);
				if (autoRotate != null && TiConvert.toBoolean(autoRotate)) {
					view.setOrientation(source.getOrientation());
				}
				if (d.containsKey(TiC.PROPERTY_DECODE_RETRIES)) {
					source.setDecodeRetries(TiConvert.toInt(d.get(TiC.PROPERTY_DECODE_RETRIES),
															TiDrawableReference.DEFAULT_DECODE_RETRIES));
				}
				setImageSource(source);
				firedLoad = false;
				setImageInternal();
			}
		} else {
			if (!d.containsKey(TiC.PROPERTY_IMAGES)) {
				getProxy().setProperty(TiC.PROPERTY_IMAGE, null);
				if (defaultImageSource != null) {
					setDefaultImage();
				}
			}
		}
		if (d.containsKey(TiC.PROPERTY_TINT_COLOR)) {
			setTintColor(d.getString("tintColor"));
		}

		// If height and width is not defined, disable scaling for scrollview since an image
		// can extend beyond the screensize in scrollview.
		if (proxy.getParent() instanceof ScrollViewProxy && !heightDefined && !widthDefined) {
			view.setEnableScale(false);
		}

		super.processProperties(d);
	}

	@Override
	public void propertyChanged(String key, Object oldValue, Object newValue, KrollProxy proxy)
	{
		TiImageView view = getView();
		if (view == null) {
			return;
		}

		if (key.equals(TiC.PROPERTY_ENABLE_ZOOM_CONTROLS)) {
			view.setEnableZoomControls(TiConvert.toBoolean(newValue));
		} else if (key.equals(TiC.PROPERTY_IMAGE)) {
			if ((oldValue == null && newValue != null) || (oldValue != null && !oldValue.equals(newValue))) {
				TiDrawableReference source = TiDrawableReference.fromObject(getProxy(), newValue);
				Object autoRotate = proxy.getProperty(TiC.PROPERTY_AUTOROTATE);
				if (autoRotate != null && TiConvert.toBoolean(autoRotate)) {
					view.setOrientation(source.getOrientation());
				}
				if (proxy.hasProperty(TiC.PROPERTY_DECODE_RETRIES)) {
					source.setDecodeRetries(TiConvert.toInt(proxy.getProperty(TiC.PROPERTY_DECODE_RETRIES),
															TiDrawableReference.DEFAULT_DECODE_RETRIES));
				}
				setImageSource(source);
				firedLoad = false;
				setImageInternal();
			}
		} else if (key.equals(TiC.PROPERTY_IMAGES)) {
			if (newValue instanceof Object[]) {
				if (oldValue == null || !oldValue.equals(newValue)) {
					setImageSource(newValue);
					setImages();
				}
			}
		} else {
			if (key.equals(TiC.PROPERTY_WIDTH)) {
				String widthProperty = TiConvert.toString(newValue);
				view.setWidthDefined(!TiC.LAYOUT_SIZE.equals(widthProperty) && !TiC.SIZE_AUTO.equals(widthProperty));
			} else if (key.equals(TiC.PROPERTY_HEIGHT)) {
				String heightProperty = TiConvert.toString(newValue);
				view.setHeightDefined(!TiC.LAYOUT_SIZE.equals(heightProperty) && !TiC.SIZE_AUTO.equals(heightProperty));
			}
			super.propertyChanged(key, oldValue, newValue, proxy);
		}
	}

	public void onCreate(Activity activity, Bundle savedInstanceState)
	{
	}

	public void onDestroy(Activity activity)
	{
	}

	public void onPause(Activity activity)
	{
		pause();
	}

	public void onResume(Activity activity)
	{
		resume();
	}

	public void onStart(Activity activity)
	{
	}

	public void onStop(Activity activity)
	{
		stop();
	}

	public boolean isAnimating()
	{
		return animating.get() && !paused;
	}

	public boolean isPaused()
	{
		return paused;
	}

	public boolean isReverse()
	{
		return reverse;
	}

	public void setReverse(boolean reverse)
	{
		this.reverse = reverse;
	}

	public TiBlob toBlob()
	{
		TiDrawableReference imageReference =
			imageSources != null && imageSources.size() == 1 ? imageSources.get(0) : null;
		Bitmap cachedBitmap = imageReference != null ? mMemoryCache.get(imageReference.hashCode()) : null;

		if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
			return TiBlob.blobFromImage(cachedBitmap);

		} else {
			TiImageView view = getView();
			if (view != null) {
				Drawable drawable = view.getImageDrawable();
				if (drawable instanceof BitmapDrawable) {
					Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
					if (bitmap == null && imageSources != null && imageSources.size() == 1) {
						bitmap = imageSources.get(0).getBitmap(true);
					}
					if (bitmap != null) {
						if (imageReference != null) {
							mMemoryCache.put(imageReference.hashCode(), bitmap);
						}
						return TiBlob.blobFromImage(bitmap);
					}
				}
			}
		}

		return null;
	}

	public void setTintColor(String color)
	{
		if (!TiApplication.isUIThread()) {
			Message message = mainHandler.obtainMessage(SET_TINT, color);
			message.sendToTarget();
		} else {
			handleTint(color);
		}
	}

	public void handleTint(String color)
	{
		TiImageView view = getView();
		view.setTintColor(color);
	}

	public int getTintColor()
	{
		TiImageView view = getView();
		return view.getTintColor();
	}

	@Override
	public void release()
	{
		handleStop();
		synchronized (releasedLock)
		{
			if (imageSources != null) {
				for (TiDrawableReference imageref : imageSources) {
					int hash = imageref.hashCode();
					mMemoryCache.remove(hash); //Release the cached images
				}
				imageSources.clear();
				imageSources = null;
			}
		}
		if (timer != null) {
			timer.cancel();
			timer = null;
		}
		defaultImageSource = null;
		imageViewProxy = null;
		downloadListener = null;
		loadImageListener = null;

		super.release();
	}
}
