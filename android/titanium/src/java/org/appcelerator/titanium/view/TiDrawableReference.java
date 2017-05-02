/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.view;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.io.TiBaseFile;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiDownloadListener;
import org.appcelerator.titanium.util.TiDownloadManager;
import org.appcelerator.titanium.util.TiFileHelper;
import org.appcelerator.titanium.util.TiImageHelper;
import org.appcelerator.titanium.util.TiImageLruCache;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.util.TiUrl;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.NinePatch;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.webkit.URLUtil;

/**
 * Helper class for loading, scaling, and caching images if necessary.
 */
@SuppressWarnings("deprecation")
public class TiDrawableReference
{
	private static Map<String, Bounds> boundsCache;
	static
	{
		boundsCache = Collections.synchronizedMap(new HashMap<String, Bounds>());
	}

	public enum DrawableReferenceType
	{
		NULL, URL, RESOURCE_ID, BLOB, FILE
	}

	public static class Bounds
	{
		public static final int UNKNOWN = TiDrawableReference.UNKNOWN;
		public int height = UNKNOWN;
		public int width = UNKNOWN;
	}

	/** The default Android log tag name to be used by this class. */
	private static final String TAG = "TiDrawableReference";

	/** Unique integer used to represent an invalid/unknown resource ID. */
	private static final int UNKNOWN = -1;

	/**
	 * The maximum number of bytes a decoded bitmap can have when displaying it via an Android "Drawable".
	 * <p>
	 * Exceeding this value will cause a "Canvas: trying to draw too large" RuntimeException to be thrown.
	 * <p>
	 * This value comes from a private "MAX_BITMAP_SIZE" constant within Google's "DisplayListCanvas" class.
	 */
	private static final long MAX_BITMAP_BYTES = 100L * 1024L * 1024L;

	private int resourceId = UNKNOWN;
	private String url;
	private TiBlob blob;
	private TiBaseFile file;
	private DrawableReferenceType type;
	private boolean oomOccurred = false;
	private boolean anyDensityFalse = false;
	private boolean autoRotate;
	private int orientation = -1;

	// TIMOB-3599: A bug in Gingerbread forces us to retry decoding bitmaps when they initially fail
	public static final int DEFAULT_DECODE_RETRIES = 5;
	private int decodeRetries;

	private SoftReference<Activity> softActivity = null;

	public TiDrawableReference(Activity activity, DrawableReferenceType type)
	{
		this.type = type;
		softActivity = new SoftReference<Activity>(activity);
		ApplicationInfo appInfo;

		if (activity != null) {
			appInfo = activity.getApplicationInfo();
		} else {
			appInfo = TiApplication.getInstance().getApplicationInfo();
		}
		anyDensityFalse = (appInfo.flags & ApplicationInfo.FLAG_SUPPORTS_SCREEN_DENSITIES) == 0;
		decodeRetries = Math.max(DEFAULT_DECODE_RETRIES, 1);
	}

	/**
	 * A very primitive implementation based on org.apache.commons.lang3.builder.HashCodeBuilder,
	 * which is licensed under Apache 2.0 license.
	 * @see <a href="http://svn.apache.org/viewvc/commons/proper/lang/trunk/src/main/java/org/apache/commons/lang3/builder/HashCodeBuilder.java?view=markup">HashCodeBuilder</a>
	 */
	@Override
	public int hashCode()
	{
		int total = 17;
		final int constant = 37;
		total = total * constant + type.ordinal();
		total = total * constant + (url == null ? 0 : url.hashCode());
		total = total * constant + (blob == null ? 0 : blob.hashCode());
		total = total * constant + (file == null ? 0 : file.hashCode());
		total = total * constant + resourceId;
		return total;
	}

	@Override
	public boolean equals(Object object)
	{
		if (!(object instanceof TiDrawableReference)) {
			return super.equals(object);
		}
		return (this.hashCode() == ((TiDrawableReference)object).hashCode());
	}

	public static TiDrawableReference fromResourceId(Activity activity, int resourceId) 
	{
		TiDrawableReference ref = new TiDrawableReference(activity, DrawableReferenceType.RESOURCE_ID);
		ref.resourceId = resourceId;
		return ref;
	}

	/**
	 * Creates and returns a TiDrawableReference with type DrawableReferenceType.BLOB.
	 * @param activity the referenced activity.
	 * @param blob the referenced blob.
	 * @return A ready instance of TiDrawableReference.
	 * @module.api
	 */
	public static TiDrawableReference fromBlob(Activity activity, TiBlob blob)
	{
		TiDrawableReference ref = new TiDrawableReference(activity, DrawableReferenceType.BLOB);
		ref.blob = blob;
		return ref;
	}

	/**
	 * Resolves the url, then creates and returns a TiDrawableReference instance.
	 * @param proxy the activity proxy.
	 * @param url the url to resolve.
	 * @return A ready instance of TiDrawableReference.
	 * @module.api
	 */
	public static TiDrawableReference fromUrl(KrollProxy proxy, String url)
	{
		if (url == null || url.length() == 0 || url.trim().length() == 0) {
			return new TiDrawableReference(proxy.getActivity(), DrawableReferenceType.NULL);
		}
		return fromUrl(proxy.getActivity(), proxy.resolveUrl(null, url));
	}

	/**
	 * Creates and returns a TiDrawableReference with type DrawableReferenceType.URL.
	 * @param activity the referenced activity.
	 * @param url the resource's url.
	 * @return A ready instance of TiDrawableReference.
	 * @module.api
	 */
	public static TiDrawableReference fromUrl(Activity activity, String url)
	{
		TiDrawableReference ref = new TiDrawableReference(activity, DrawableReferenceType.URL);
		ref.url = url;

		// Could still be a resource image file in android/images/medium|high|low. Check once.
		if (url != null) {
			int id =  TiUIHelper.getResourceId(url);
			if (id != 0) {
				// This is a resource so handle it as such.  Is it evil to switch up the type on someone like this? Maybe...
				ref.type = DrawableReferenceType.RESOURCE_ID;
				ref.resourceId = id;
			}
		}
		return ref;
	}

	/**
	 * Creates and returns a TiDrawableReference with type DrawableReferenceType.FILE.
	 * @param activity the referenced activity.
	 * @param file the referenced file.
	 * @return A ready instance of TiDrawableReference.
	 */
	public static TiDrawableReference fromFile(Activity activity, TiBaseFile file)
	{
		TiDrawableReference ref = new TiDrawableReference(activity, DrawableReferenceType.FILE);
		ref.file = file;
		return ref;
	}
	
	public static TiDrawableReference fromDictionary(Activity activity, HashMap dict)
	{
		if (dict.containsKey("media")) {
			return fromBlob(activity, TiConvert.toBlob(new KrollDict(dict), "media"));
		} else {
			Log.w(TAG,
				"Unknown drawable reference inside dictionary.  Expected key 'media' to be a blob.  Returning null drawable reference");
			return fromObject(activity, null);
		}
	}
	/**
	 * Does its best to determine the type of reference (url, blob, etc) based on object parameter.
	 * @param activity the referenced activity.
	 * @param object the referenced object.
	 * @return A ready instance of TiDrawableReference.
	 * @module.api
	 */
	public static TiDrawableReference fromObject(Activity activity, Object object)
	{
		if (object == null) {
			return new TiDrawableReference(activity, DrawableReferenceType.NULL);
		}
		
		if (object instanceof String) {
			return fromUrl(activity, TiConvert.toString(object));
		} else if (object instanceof HashMap) {
			return fromDictionary(activity, (HashMap)object);
		} else if (object instanceof TiBaseFile) {
			return fromFile(activity, (TiBaseFile)object);
		} else if (object instanceof TiBlob) {
			return fromBlob(activity, TiConvert.toBlob(object));
		} else if (object instanceof Number) {
			return fromResourceId(activity, ((Number)object).intValue());
		} else {
			Log.w(TAG, "Unknown image resource type: " + object.getClass().getSimpleName()
				+ ". Returning null drawable reference");
			return fromObject(activity, null);
		}
	}

	public boolean isNetworkUrl()
	{
		return (type == DrawableReferenceType.URL && url != null && URLUtil.isNetworkUrl(this.url));
	}

	public boolean isTypeUrl()
	{
		return type == DrawableReferenceType.URL;
	}

	public boolean isTypeFile()
	{
		return type == DrawableReferenceType.FILE;
	}
	
	public boolean isTypeBlob()
	{
		return type == DrawableReferenceType.BLOB;
	}

	public boolean isTypeResourceId()
	{
		return type == DrawableReferenceType.RESOURCE_ID;
	}
	public boolean isTypeNull()
	{
		return type == DrawableReferenceType.NULL;
	}

	/**
	 * Gets the bitmap from the resource without respect to sampling/scaling.
	 * @return Bitmap, or null if errors occurred while trying to load or fetch it.
	 * @module.api
	 */
	public Bitmap getBitmap()
	{
		return getBitmap(false);
	}

	/**
	 * Gets the bitmap from the resource without respect to sampling/scaling.
	 * When needRetry is set to true, it will retry loading when decode fails.
	 * If decode fails because of out of memory, clear the memory and call GC and retry loading a smaller image.
	 * If decode fails because of the odd Android 2.3/Gingerbread behavior (TIMOB-3599), retry loading the original image.
	 * This method should be called from a background thread when needRetry is set to true because it may block
	 * the thread if it needs to retry several times.
	 * @param needRetry If true, it will retry loading when decode fails.
	 * @return Bitmap, or null if errors occurred while trying to load or fetch it.
	 * @module.api
	 */
	public Bitmap getBitmap(boolean needRetry)
	{
		return getBitmap(needRetry, false);
	}
	
	/**
	 * Loads/decodes the referenced image and returns it as an uncompressed bitmap.
	 * <p>
	 * Note that the the returned bitmap may be returned downscaled if it is too big to be displayed
	 * by an Android "View" object or if there is not enough memory on the heap.
	 * @param needRetry
	 * Set true to attempt to load the image multiple times if there is a failure such as problems
	 * accessing the file stream immediately or due to out-of-memory issues. This method should be called
	 * on a separate thread when set true since it may block the thread.
	 * <p>
	 * Set false to attempt to load the image only once, without fault tolerance.
	 * @param densityScaled
	 * Set true to scale the returned bitmap based on the device's DPI scale factor, if applicable.
	 * An image loaded outside of the "res/drawable" directory are assumed to have an "mdpi" based resolution
	 * and will be scaled based on current DPI scale factory (ex: xhdpi = 2x scale).
	 * An image from a "res/drawable" directory will only be scaled if one was not found matching the device's DPI.
	 * <p>
	 * Set false to load the bitmap as-is. Note that the image can still be downscaled if it is
	 * too big to be displayed by an Android "View".
	 * @return Bitmap, or null if errors occurred while trying to load or fetch it.
	 */
	public Bitmap getBitmap(boolean needRetry, boolean densityScaled)
	{
		// Attempt to access the encoded image's bytes.
		InputStream is = getInputStream();
		Bitmap b = null;
		Bounds originalBounds = null;
		BitmapFactory.Options opts = new BitmapFactory.Options();
		opts.inInputShareable = true;
		opts.inPurgeable = true;
		opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
		opts.inSampleSize = 1;

		// Set up DPI scaling, if enabled.
		// Note: Non-resource files are assumed to use an "mdpi" based resolution.
		if (densityScaled) {
			DisplayMetrics dm = new DisplayMetrics();
			dm.setToDefaults();
			opts.inScaled = true;
			opts.inTargetDensity = dm.densityDpi;
			opts.inDensity = DisplayMetrics.DENSITY_MEDIUM;
		}

		// If loading a "res/drawable", then always set BitmapFactory option "inDensity" to the
		// DPI directory the resource was loaded from, even if "densityScaled" argument is false.
		// This does the following:
		// - Allow BitmapFactory density scaling to work correctly. (Can't assume mdpi like assets.)
		// - Returned object's Bitmap.getDensity() method will return the res file's density/DPI,
		//   which is needed by a BitmapDrawable to size itself correctly when displaying the bitmap.
		if (isTypeResourceId() && (this.resourceId != UNKNOWN)) {
			try {
				Resources resources = getResources();
				TypedValue typedValue = new TypedValue();
				resources.getValue(this.resourceId, typedValue, true);
				if (typedValue.density > 0) {
					opts.inDensity = typedValue.density;
				}
			}
			catch (Exception ex) {}
		}

		// Attempt to decode the referenced image as an uncompressed bitmap.
		try {
			final int MAX_ATTEMPTS = needRetry ? this.decodeRetries : 1;
			for (int attemptCount = 1; attemptCount <= MAX_ATTEMPTS; attemptCount++) {
				// Check if we've acquired an input stream to the image.
				// Note: getInputStream() sometimes fails. Might have to wait for garbage collection.
				if (is == null) {
					if (needRetry) {
						Log.i(TAG, "Unable to get input stream for bitmap. Will retry.", Log.DEBUG_MODE);
						try {
							Thread.sleep(100);
						}
						catch (InterruptedException ie) {}
						is = getInputStream();
						if (is == null) {
							continue;
						}
					}
					else {
						break;
					}
				}

				// The image's input stream has been acquired.
				// Fetch the image's bounds, if not done already.
				if (originalBounds == null) {
					// Fetch the image's pixel width and height.
					// If this fails, then the stream references an unsupported image format or is corrupted.
					originalBounds = peekBounds();
					if ((originalBounds == null) || (originalBounds.width <= 0) || (originalBounds.height <= 0)) {
						break;
					}

					// If density scaling is enabled, then calculate what the new bounds will be.
					Bounds newBounds = originalBounds;
					if (opts.inScaled && (opts.inDensity > 0) && (opts.inTargetDensity > 0)) {
						if ((opts.inDensity != opts.inTargetDensity) && (opts.inDensity != opts.inScreenDensity)) {
							double scale = (double)opts.inTargetDensity / (double)opts.inDensity;
							newBounds = new Bounds();
							newBounds.width = (int)(((double)originalBounds.width * scale) + 0.5);
							newBounds.height = (int)(((double)originalBounds.height * scale) + 0.5);
						}
					}

					// If the image size exceeds the GPU's max texture size,
					// then set up the image decoder to down-sample it to fit.
					// Note: If we don't do this, then the UI will fail to display the image or throw an exception.
					int maxTextureSize = GpuInfo.getMaxTextureSize();
					if (maxTextureSize > 0) {
						int longestLength = Math.max(newBounds.width, newBounds.height);
						while (longestLength > maxTextureSize) {
							opts.inSampleSize *= 2;		// Must be down-sampled by powers of 2.
							longestLength /= 2;
						}
					}

					// Down-sample if the image exceeds 100 MB when decoded as a 32-bit color uncompressed bitmap.
					// This will prevent a "Canvas: trying to draw too large" RuntimeException from being thrown.
					// Note: The 100 MB limit comes from a private MAX_BITMAP_SIZE constant within the
					//       "DisplayListCanvas" class. (See Android's source code on GitHub.)
					final long MAX_BITMAP_PIXELS = MAX_BITMAP_BYTES / 4L;
					while ((long)(newBounds.width / opts.inSampleSize) * (long)(newBounds.height / opts.inSampleSize) > MAX_BITMAP_PIXELS) {
						opts.inSampleSize *= 2;
					}
				}

				// Attempt to load the image.
				try {
					// Decode the image to an uncompressed bitmap.
					oomOccurred = false;
					b = BitmapFactory.decodeStream(is, null, opts);
					if (b != null) {
						break;
					}

					// Decode fails because of TIMOB-3599.
					// Really odd Android 2.3/Gingerbread behavior -- BitmapFactory.decode* Skia functions
					// fail randomly and seemingly without a cause. Retry 5 times by default w/ 250ms between each try.
					// Usually the 2nd or 3rd try succeeds, but the "decodeRetries" property in ImageView
					// will allow users to tweak this if needed
					Log.i(TAG, "Unable to decode bitmap. Will retry.", Log.DEBUG_MODE);
					try {
						Thread.sleep(250);
					} catch (InterruptedException ie) {}
				} catch (OutOfMemoryError e) {
					// Decode fails because of out of memory
					oomOccurred = true;
					Log.e(TAG, "Unable to load bitmap. Not enough memory: " + e.getMessage(), e);
					if (needRetry) {
						// Free up memory the next time we "retry" image decoding.
						Log.i(TAG, "Clear memory cache and signal a GC. Will retry load.", Log.DEBUG_MODE);
						TiImageLruCache.getInstance().evictAll();
						System.gc();	// Force garbage collection.
						try {
							Thread.sleep(1000);
						} catch (InterruptedException ie) {}

						// Down-sample the image the next time we decode it.
						// Note: This will reduce the decoded memory footprint by a power of 2.
						opts.inSampleSize *= 2;
					}
				} catch (Exception e) {
					Log.e(TAG, "Unable to load bitmap. Reason: " + e.getMessage(), e);
				}
			}

			// If decoding fails, then try to get load it via an HttpClient.
			if ((b == null) && needRetry) {
				HttpURLConnection connection = null;
				try {
					URL mURL = new URL(url);
					connection = (HttpURLConnection) mURL.openConnection();
					connection.setInstanceFollowRedirects(true);
					connection.setDoInput(true);
					connection.connect();
					int responseCode = connection.getResponseCode();
					if (responseCode == 200) {
						b = BitmapFactory.decodeStream(connection.getInputStream());
					} else if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
						String location = connection.getHeaderField("Location");
						URL nURL = new URL(location);
						String prevProtocol = mURL.getProtocol();
						//HttpURLConnection doesn't handle http to https redirects so we do it manually.
						if (prevProtocol != null && !prevProtocol.equals(nURL.getProtocol())) {
							b = BitmapFactory.decodeStream(nURL.openStream());
						} else {
							b = BitmapFactory.decodeStream(connection.getInputStream());
						}
					} else {
						b = null;
					}
				} catch (Exception e) {
					b = null;
				} finally {
					if (connection != null) {
						connection.disconnect();
					}
				}
			}
		} finally {
			// Close the image stream.
			if (is == null) {
				Log.w(TAG, "Could not open stream to get bitmap");
			}
			else {
				try {
					is.close();
				} catch (Exception e) {
					Log.e(TAG, "Problem closing stream: " + e.getMessage(), e);
				}
			}
		}

		return b;
	}

	private Resources getResources()
	{
		return TiApplication.getInstance().getResources();
	}

	private Drawable getResourceDrawable()
	{
		// Do not continue if not referencing an APK resource file.
		if (!isTypeResourceId() || (this.resourceId == UNKNOWN)) {
			return null;
		}

		// Fetch the image's pixel width and height.
		// If this fails, then we're referencing an unsupported/invalid image format or it is corrupted.
		Bounds bounds = peekBounds();
		if ((bounds == null) || (bounds.width <= 0) || (bounds.height <= 0)) {
			return null;
		}

		// Determine if the referenced image is too big to be displayed as-is and requires downscaling.
		boolean isImageTooBig = true;
		{
			// Check if the image exceeds the GPU's max texture size.
			int maxTextureSize = GpuInfo.getMaxTextureSize();
			int longestLength = Math.max(bounds.width, bounds.height);
			if (longestLength <= maxTextureSize) {
				// Check if the image exceeds a drawable's max allowed bitmap size (in bytes).
				// Note: Images are decoded to 32-bit color bitmaps. So, there are 4 bytes per pixel.
				final long MAX_BITMAP_PIXELS = MAX_BITMAP_BYTES / 4L;
				if (((long)bounds.width * (long)bounds.height) <= MAX_BITMAP_PIXELS) {
					// The image can be successfully displayed as-is. No downscaling required.
					isImageTooBig = false;
				}
			}
		}

		// Create and return a drawable that'll display the referenced image.
		Drawable drawable = null;
		if (isImageTooBig) {
			// The image is too big to be displayed by the GPU or UI.
			// We must downscale it and load it to a drawable ourselves in order to display it.
			boolean isDensityScalingEnabled = true;
			drawable = getDrawable(isDensityScalingEnabled);
		}
		else {
			// Load the drawable image normally.
			// Note: This takes advantage of Android's drawable caching and is more future proof.
			try {
				Resources resources = getResources();
				if (resources != null) {
					drawable = resources.getDrawable(this.resourceId);
				}
			}
			catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		return drawable;
	}

	/**
	 * Gets a resource drawable directly if the reference is to a resource, else
	 * makes a BitmapDrawable with the given attributes.
	 */
	public Drawable getDrawable(View parent, TiDimension destWidthDimension, TiDimension destHeightDimension)
	{
		Drawable drawable = getResourceDrawable();
		if (drawable == null) {
			Bitmap b = getBitmap(parent, destWidthDimension, destHeightDimension);
			if (b != null) {
				drawable = new BitmapDrawable(b);
			}
		}
		return drawable;
	}

	/**
	 * Gets a resource drawable directly if the reference is to a resource, else
	 * makes a BitmapDrawable with the given attributes.
	 */
	public Drawable getDrawable(int destWidth, int destHeight)
	{
		Drawable drawable = getResourceDrawable();
		if (drawable == null) {
			Bitmap b = getBitmap(destWidth, destHeight);
			if (b != null) {
				drawable = new BitmapDrawable(b);
			}
		}
		return drawable;
	}

	/**
	 * Loads the referenced image and returns it as a drawable to be drawn in a view/canvas.
	 * <p>
	 * The returned drawable will be density scaled if referencing an image in the "res/drawable" directory.
	 * All other images will not be density scaled and be drawn as-is, pixel perfect.
	 * @return Returns the referenced image as a drawable. Returns null if failed to load the image.
	 */
	public Drawable getDrawable()
	{
		Drawable drawable = getResourceDrawable();
		if (drawable == null) {
			boolean isDensityScalingEnabled = false;
			drawable = getDrawable(isDensityScalingEnabled);
		}
		return drawable;
	}
	
	/**
	 * Loads the referenced image and returns it as a drawable to be drawn in a view/canvas.
	 * <p>
	 * The returned drawable will be density scaled to a size matching the device's current DPI.
	 * <p>
	 * Images loaded outside of the "res/drawable" directory are assumed to use an "mdpi" base resolution
	 * and will be scaled to the device's DPI. For example, "xhdpi" device will apply a 2x scale factor,
	 * "xxhdpi" device will apply a 3x scale factor, etc.
	 * @return Returns the referenced image as a drawable. Returns null if failed to load the image.
	 */
	public Drawable getDensityScaledDrawable()
	{
		Drawable drawable = getResourceDrawable();
		if (drawable == null) {
			boolean isDensityScalingEnabled = true;
			drawable = getDrawable(isDensityScalingEnabled);
		}
		return drawable;
	}

	/**
	 * Loads the referenced image and returns it as a drawable to be drawn in a view/canvas.
	 * <p>
	 * If argument "isDensityScalingEnabled" is set true, then the returned drawable will be
	 * density scaled to a size matching the device's current DPI.
	 * <p>
	 * Images loaded outside of the "res/drawable" directory are assumed to use an "mdpi" base resolution
	 * and will be scaled to the device's DPI. For example, "xhdpi" device will apply a 2x scale factor,
	 * "xxhdpi" device will apply a 3x scale factor, etc.
	 * @param isDensityScalingEnabled
	 * Set true to scale images loaded outside of the "res/drawable" directory.
	 * Set false to load the images as-is, without scaling.
	 * <p>
	 * Note that "res/drawable" images are always scaled to device's DPI, even if this argument is set false.
	 * @return Returns the referenced image as a drawable. Returns null if failed to load the image.
	 */
	private Drawable getDrawable(boolean isDensityScalingEnabled)
	{
		try {
			// Load the referenced image.
			// Note: Do not scale the actual decoded bitmap in memory. (That's expensive.)
			//       Instead, decode the image as-is and then have the drawable stretch it to the size needed.
			boolean areRetriesEnabled = false;
			boolean isBitmapScalingEnabled = false;
			Bitmap bitmap = getBitmap(areRetriesEnabled, isBitmapScalingEnabled);
			if (bitmap != null) {
				// If the decoded image is not a "res/drawable", then flag it as "mdpi".
				// This is a hint to the drawable, telling it to stretch it from "mdpi" to the device's DPI.
				// Note: Bitmaps loaded from "res/drawable" will already have their density assigned.
				if (isDensityScalingEnabled) {
					if (!isTypeResourceId() || (bitmap.getDensity() == Bitmap.DENSITY_NONE)) {
						bitmap.setDensity(DisplayMetrics.DENSITY_MEDIUM);
					}
				}

				// Return the bitmap wrapped in a drawable.
				// Note: Resources argument tells it to scale using device DPI. Otherwise, it'll scale via "mdpi".
				byte[] ninePatchChunk = bitmap.getNinePatchChunk();
				if ((ninePatchChunk != null) && NinePatch.isNinePatchChunk(ninePatchChunk)) {
					return new NinePatchDrawable(getResources(), bitmap, ninePatchChunk, new Rect(1,1,1,1), "");
				}
				else {
					return new BitmapDrawable(getResources(), bitmap);
				}
			}
		}
		catch (Exception ex) {}
		return null;
	}

	/**
	 * Gets the bitmap, scaled to a specific width & height.
	 * @param destWidth Width in pixels of resulting scaled bitmap
	 * @param destHeight Height in pixels of resulting scaled bitmap
	 * @return Bitmap, or null if any problem getting it.  Check logcat if null.
	 */
	public Bitmap getBitmap(int destWidth, int destHeight)
	{
		return getBitmap(null,
			TiConvert.toTiDimension(new Integer(destWidth), TiDimension.TYPE_WIDTH),
			TiConvert.toTiDimension(new Integer(destHeight), TiDimension.TYPE_HEIGHT));
	}
	/**
	 * Gets the bitmap, scaled to a specific width, with the height matching the
	 * original aspect ratio.
	 * @param destWidth Width in pixels of resulting bitmap
	 * @return Bitmap, or null if any problem getting it.  Check logcat if null.
	 */
	public Bitmap getBitmap(int destWidth)
	{
		int srcWidth, srcHeight, destHeight;
		Bounds orig = peekBounds();
		srcWidth = orig.width;
		srcHeight = orig.height;
		if (srcWidth <= 0 || srcHeight <= 0) {
			Log.w(TAG, "Bitmap bounds could not be determined.  If bitmap is loaded, it won't be scaled.");
			return getBitmap(); // fallback
		}
		double aspectRatio = (double)srcWidth/(double)srcHeight;
		destHeight = (int) ((double)destWidth / aspectRatio);
		return getBitmap(destWidth, destHeight);
	}

	private Bounds calcDestSize(int srcWidth, int srcHeight, TiDimension destWidthDimension,
			TiDimension destHeightDimension, View parent)
	{
		Bounds bounds = new Bounds();
		int destWidth, destHeight, containerWidth, containerHeight,
			parentWidth, parentHeight;
		destWidth = destHeight = parentWidth = parentHeight =
			containerWidth = containerHeight = TiDrawableReference.UNKNOWN;
		boolean widthSpecified = false;
		boolean heightSpecified = false;

		if (parent != null) {
			parentWidth = parent.getWidth();
			parentHeight = parent.getHeight();
		}

		// Width to fit into
		if (destWidthDimension != null) {
			if (destWidthDimension.isUnitAuto()) {
				containerWidth = srcWidth;
			} else {
				widthSpecified = true;
				containerWidth = destWidthDimension.getAsPixels(parent);
			}
		} else {
			if (parentWidth >= 0) {
				containerWidth = parentWidth;
			}
		}
		if (containerWidth < 0) {
			Log.w(TAG, "Could not determine container width for image. Defaulting to source width. This shouldn't happen.");
			containerWidth = srcWidth;
		}

		// Height to fit into
		if (destHeightDimension != null) {
			if (destHeightDimension.isUnitAuto()) {
				containerHeight = srcHeight;
			} else {
				heightSpecified = true;
				containerHeight = destHeightDimension.getAsPixels(parent);
			}
		} else {
			if (parentHeight >= 0) {
				containerHeight = parentHeight;
			}
		}

		if (containerHeight < 0) {
			Log.w(TAG, "Could not determine container height for image. Defaulting to source height. This shouldn't happen.");
			containerHeight = srcHeight;
		}

		float origAspectRatio = (float) srcWidth / (float) srcHeight;

		if (widthSpecified && heightSpecified) {
			destWidth = containerWidth;
			destHeight = containerHeight;
		} else if (widthSpecified) {
			destWidth = containerWidth;
			destHeight = (int) ((float) destWidth / origAspectRatio);
		} else if (heightSpecified) {
			destHeight = containerHeight;
			destWidth = (int) ((float) destHeight * origAspectRatio);
		} else {
			if (origAspectRatio > 1f) {
				destWidth = containerWidth;
				destHeight = (int) ((float) destWidth / origAspectRatio);
			} else {
				destHeight = containerHeight;
				destWidth = (int) ((float) destHeight * origAspectRatio);
			}
		}

		bounds.width = destWidth;
		bounds.height = destHeight;
		return bounds;
	}

	/**
	 * Gets the bitmap, scaled to a width & height specified in TiDimension params.
	 * @param destWidthDimension (null-ok) TiDimension specifying the desired width.  If .isUnitAuto()
	 * then the width will be the source width.  If destWidthDimension is null, then the TiContext
	 * will be used to determine the activity window's decor width and use that.
	 * @param destHeightDimension (null-ok) TiDimension specifying the desired height.  If .isUnitAuto()
	 * then the height will be the source height.  If destHeightDimension is null, then resulting height will
	 * be at same ratio to the resulting width as the original height:width.
	 * @return Bitmap, or null if any problem getting it.  Check logcat if null.
	 */
	public Bitmap getBitmap(View parent, TiDimension destWidthDimension, TiDimension destHeightDimension)
	{
		int srcWidth, srcHeight, destWidth, destHeight;

		Bounds bounds = peekBounds();
		srcWidth = bounds.width;
		srcHeight = bounds.height;

		if (srcWidth <= 0 || srcHeight <= 0) {
			Log.w(TAG, "Bitmap bounds could not be determined. If bitmap is loaded, it won't be scaled.");
			return getBitmap(); // fallback
		}

		if (parent == null) {
			Activity activity = softActivity.get();
			if (activity != null && activity.getWindow() != null) {
				parent = activity.getWindow().getDecorView();
			}
		}

		Bounds destBounds = calcDestSize(srcWidth, srcHeight, destWidthDimension, destHeightDimension, parent);
		destWidth = destBounds.width;
		destHeight = destBounds.height;

		// If src and dest width/height are same, no need to go through all the sampling and scaling jazz.
		if (srcWidth == destWidth && srcHeight == destHeight) {
			return getBitmap();
		}

		if (destWidth <= 0 || destHeight <= 0) {
			// If we can't determine the size, then return null instead of an unscaled bitmap
			return getBitmap();
		}

		InputStream is = getInputStream();
		if (is == null) {
			Log.w(TAG, "Could not open stream to get bitmap");
			return null;
		}

		Bitmap b = null;
		try {
			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inInputShareable = true;
			opts.inPurgeable = true;
			opts.inSampleSize =  calcSampleSize(srcWidth, srcHeight, destWidth, destHeight);
			if (Log.isDebugModeEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append("Bitmap calcSampleSize results: inSampleSize=");
				sb.append(opts.inSampleSize);
				sb.append("; srcWidth=");
				sb.append(srcWidth);
				sb.append("; srcHeight=");
				sb.append(srcHeight);
				sb.append("; finalWidth=");
				sb.append(opts.outWidth);
				sb.append("; finalHeight=");
				sb.append(opts.outHeight);
				Log.d(TAG, sb.toString());
			}

			Bitmap bTemp = null;
			try {
				oomOccurred = false;
				bTemp = BitmapFactory.decodeStream(is, null, opts);
				if (bTemp == null) {
					Log.w(TAG, "Decoded bitmap is null");
					return null;
				}

				if (Log.isDebugModeEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append("decodeStream resulting bitmap: .getWidth()=" + bTemp.getWidth());
					sb.append("; .getHeight()=" + bTemp.getHeight());
					sb.append("; getDensity()=" + bTemp.getDensity());
					Log.d(TAG, sb.toString());
				}

				// Set the bitmap density to match the view density before scaling, so that scaling
				// algorithm takes destination density into account.
				DisplayMetrics displayMetrics = new DisplayMetrics();
				displayMetrics.setToDefaults();
				bTemp.setDensity(displayMetrics.densityDpi);

				// Orient the image when orientation is set.
				if (autoRotate) {
					// Only set the orientation if it is uninitialized
					if(orientation < 0) {
						orientation = getOrientation();
					}
					if (orientation > 0) {
						return getRotatedBitmap(bTemp, orientation);
					}
				}

				if (bTemp.getNinePatchChunk() != null) {
					// Don't scale nine-patches
					b = bTemp;
					bTemp = null;
				} else {
					if (Log.isDebugModeEnabled()) {
						Log.d(TAG, "Scaling bitmap to " + destWidth + "x" + destHeight, Log.DEBUG_MODE);
					}

					// If anyDensity=false, meaning Android is automatically scaling
					// pixel dimensions, need to do that here as well, because Bitmap width/height
					// calculations do _not_ do that automatically.
					if (anyDensityFalse && displayMetrics.density != 1f) {
						destWidth = (int) (destWidth * displayMetrics.density + 0.5f); // 0.5 is to force round up of dimension. Casting to int drops decimals.
						destHeight = (int) (destHeight * displayMetrics.density + 0.5f);
					}

					// Created a scaled copy of the bitmap. Note we will get
					// back the same bitmap if no scaling is required.
					b = Bitmap.createScaledBitmap(bTemp, destWidth, destHeight, true);
				}

			} catch (OutOfMemoryError e) {
				oomOccurred = true;
				Log.e(TAG, "Unable to load bitmap. Not enough memory: " + e.getMessage(), e);

			} finally {
				// Recycle the temporary bitmap only if it isn't
				// the same instance as our scaled bitmap.
				if (bTemp != null && bTemp != b) {
					bTemp.recycle();
					bTemp = null;
				}
			}

		} finally {
			try {
				is.close();
			} catch (IOException e) {
				Log.e(TAG, "Problem closing stream: " + e.getMessage(), e);
			}
		}
		if (Log.isDebugModeEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append("Details of returned bitmap: .getWidth()=" + b.getWidth());
			sb.append("; getHeight()=" + b.getHeight());
			sb.append("; getDensity()=" + b.getDensity());
			Log.d(TAG, sb.toString());
		}
		return b;
	}

	/**
	 * Just runs TiDownloadManager.download(URI, listener) giving it the passed listener.
	 */
	public void getBitmapAsync(TiDownloadListener listener)
	{
		if (!isNetworkUrl()) {
			Log.w(TAG, "getBitmapAsync called on non-network url.  Will attempt load.", Log.DEBUG_MODE);
		}
		
		try {
			TiDownloadManager.getInstance().download(new URI(TiUrl.getCleanUri(url).toString()), listener);
		} catch (URISyntaxException e) {
			Log.e(TAG, "URI Invalid: " + url, e);
		} catch (NullPointerException e) {
			Log.e(TAG, "NullPointerException: " + url, e);
		}
	}

	/**
	 * Uses BitmapFactory.Options' 'inJustDecodeBounds' to peak at the bitmap's bounds
	 * (height & width) so we can do some sampling and scaling.
	 * @return Bounds object with width and height.
	 */
	public Bounds peekBounds()
	{
		// If we're referencing an immutable image, then set up a unique string key.
		// Note: External files, external URLs, and blobs may reference mutable images whose bounds could change.
		String cacheKey = null;
		if (isTypeResourceId()) {
			// Resource files within the APK are immutable.
			cacheKey = "resource:" + this.resourceId;
		}
		else if (isTypeUrl()) {
			if ((this.url != null) && (this.url.length() > 0)) {
				if (TiFileHelper.getInstance().isTitaniumResource(this.url)) {
					// "ti:" custom URL schemes reference immutable resource files within the APK.
					cacheKey = this.url;
				}
				else if (this.url.startsWith("file:///android_asset/")) {
					// Asset files within the APK are immutable.
					cacheKey = this.url;
				}
			}
		}

		// Check if the image's bounds have already been fetched.
		if ((cacheKey != null) && TiDrawableReference.boundsCache.containsKey(cacheKey)) {
			return TiDrawableReference.boundsCache.get(cacheKey);
		}

		// If we're not referencing an image, then return 0x0.
		Bounds bounds = new Bounds();
		if (isTypeNull()) {
			return bounds;
		}

		// Fetch the image's width and height.
		InputStream stream = getInputStream();
		try {
			if (stream != null) {
				BitmapFactory.Options bfo = new BitmapFactory.Options();
				bfo.inJustDecodeBounds = true;
				BitmapFactory.decodeStream(stream, null, bfo);
				bounds.height = bfo.outHeight;
				bounds.width = bfo.outWidth;
			} else {
				Log.w(TAG, "Could not open stream for drawable, therefore bounds checking could not be completed");
			}
		} finally {
			try {
				if (stream != null) {
					stream.close();
				}
			} catch (IOException e) {
				Log.e(TAG, "problem closing stream: " + e.getMessage(), e);
			}
		}

		// Cache the image's bounds for fast retrieval later.
		// Note: We'll only have a cache key if the image is immutable.
		if (cacheKey != null) {
			TiDrawableReference.boundsCache.put(cacheKey, bounds);
		}

		// Return the image's bounds.
		return bounds;
	}

	/**
	 * Based on the underlying type of reference this is, figures out how to get
	 * an InputStream for it.  E.g., if a blob, calls blob.getInputStream, if 
	 * a resource id, calls context.getTiApp().getResources().openRawResource(resourceId).
	 * @return InputStream or null if problem getting it (check logcat in that case)
	 */
	public InputStream getInputStream()
	{
		InputStream stream = null;

		if (isTypeUrl() && url != null) {
			try {
				stream = TiFileHelper.getInstance().openInputStream(url, false);
				
			} catch (IOException e) {
				Log.e(TAG, "Problem opening stream with url " + url + ": " + e.getMessage(), e);
			}

		} else if (isTypeFile() && file != null) {
			try {
				stream = file.getInputStream();
			} catch (IOException e) {
				Log.e(TAG, "Problem opening stream from file " + file.name() + ": " + e.getMessage(), e);
			}

		} else if (isTypeBlob() && blob != null) {
			stream = blob.getInputStream();
		} else if (isTypeResourceId() && resourceId != UNKNOWN) {
			try {
				stream = TiApplication.getInstance().getResources().openRawResource(resourceId);
			} catch (Resources.NotFoundException e) {
				Log.e(TAG, "Drawable resource could not be opened. Are you sure you have the resource for the current device configuration (orientation, screen size, etc.)?");
				throw e;
			}
		}

		return stream;
	}

	/**
	 * Calculates a value for the BitmapFactory.Options .inSampleSize property.
	 * 
	 * @see <a href="http://developer.android.com/reference/android/graphics/BitmapFactory.Options.html#inSampleSize">BitmapFactory.Options.inSampleSize</a>
	 * @param srcWidth int
	 * @param srcHeight int
	 * @param destWidth int
	 * @param destHeight int
	 * @return max of srcWidth/destWidth or srcHeight/destHeight
	 */
	public int calcSampleSize(int srcWidth, int srcHeight, int destWidth, int destHeight)
	{
		if (srcWidth <= 0 || srcHeight <= 0 || destWidth <= 0 || destHeight <= 0) {
			return 1;
		}
		return Math.max(srcWidth / destWidth, srcHeight / destHeight);
	}

	/**
	 * Calculates a value for the BitmapFactory.Options .inSampleSize property.
	 * 
	 * @see <a href="http://developer.android.com/reference/android/graphics/BitmapFactory.Options.html#inSampleSize">BitmapFactory.Options.inSampleSize</a>
	 * @param srcWidth int
	 * @param srcHeight int
	 * @param destWidthDimension TiDimension holding the destination width. If null, the TiContext's Activity's Window's decor width is used
	 * as the destWidth.
	 * @param destHeightDimension TiDimension holding the destination height.  If null, the destHeight will be proportional to destWidth as srcHeight
	 * is to srcWidth.
	 * @return max of srcWidth/destWidth or srcHeight/destHeight
	 */
	public int calcSampleSize(View parent, int srcWidth, int srcHeight, TiDimension destWidthDimension, TiDimension destHeightDimension) 
	{
		int destWidth, destHeight;
		destWidth = destHeight = TiDrawableReference.UNKNOWN;
		Bounds destBounds = calcDestSize(srcWidth, srcHeight, destWidthDimension, destHeightDimension, parent);
		destWidth = destBounds.width;
		destHeight = destBounds.height;
		return calcSampleSize(srcWidth, srcHeight, destWidth, destHeight);
	}

	/**
	 * @return true if most recent attempt to getBitmap caused an OutOfMemoryError
	 */
	public boolean outOfMemoryOccurred()
	{
		return oomOccurred;
	}

	private Bitmap getRotatedBitmap(Bitmap src, int orientation) {
		Matrix m = new Matrix();
		m.postRotate(orientation);
		return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, false);
	}

	public int getOrientation()
	{
		String path = null;
		int orientation = 0;

		if (isTypeBlob() && blob != null) {
			path = blob.getNativePath();
		} else if (isTypeFile() && file != null) {
			path = file.getNativeFile().getAbsolutePath();
		} else {
			InputStream is = getInputStream();
			if (is != null) {
				File file = TiFileHelper.getInstance().getTempFileFromInputStream(is, "EXIF-TMP", true);
				path = file.getAbsolutePath();
			}
		}

		return TiImageHelper.getOrientation(path);

	}

	public void setAutoRotate(boolean autoRotate)
	{
		this.autoRotate = autoRotate;
	}

	public void setDecodeRetries(int decodeRetries)
	{
		this.decodeRetries = decodeRetries;
	}

	public String getUrl()
	{
		return url;
	}


	/**
	 * Private class used to fetch GPU information such as the max texture size.
	 * <p>
	 * You cannot create instances of this class. Instead, you are expected to call its static functions.
	 */
	private static class GpuInfo
	{
		/** Set true if GPU info has been loaded. Set false if not. (Only needs to be loaded once.) */
		private static boolean wasInitialized;

		/** The maximum number of pixels wide/tall that the GPU supports when loading textures/bitmaps. */
		private static int maxTextureSize;


		/** Constructor made private to prevent instances from being made. */
		private GpuInfo() {}

		/**
		 * Fetches the maximum number of pixels wide or tall that the GPU supports for bitmaps/textures.
		 * That is, the GPU will not accept/display images larger than this size.
		 * <p>
		 * For example, if the returned value is 2048, then the GPU cannot display an image larger
		 * than 2048x2048 pixels.
		 * @return Returns the maximum texture size in pixels supported by the GPU.
		 *         <p>
		 *         Returns zero if failed to load this information from the GPU.
		 */
		public static int getMaxTextureSize()
		{
			load();
			return GpuInfo.maxTextureSize;
		}

		/** Loads GPU information and stores them to this class' static member variables. */
		private static void load()
		{
			// Do not continue if GPU info has already been loaded. (Only need to do so once.)
			if (wasInitialized) {
				return;
			}

			// Load GPU information.
			synchronized (GpuInfo.class) {
				// Handle race condition where 2 threads try to load GPU info simultaneously.
				if (wasInitialized) {
					return;
				}

				// Create a temporary OpenGL context to acquire GPU information.
				EGL10 egl = null;
				EGLDisplay display = null;
				EGLContext context = null;
				EGLSurface surface = null;
				try {
					// Set up an OpenGL interface to the display.
					egl = (EGL10)EGLContext.getEGL();
					display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
					if (display == EGL10.EGL_NO_DISPLAY) {
						display = null;
					}
					if (display != null) {
						int[] versionArray = new int[2];
						boolean wasSuccessful = egl.eglInitialize(display, versionArray);
						if (!wasSuccessful) {
							display = null;
						}
					}

					// Fetch a 32-bit color frame buffer config for the display interface.
					EGLConfig eglConfig = null;
					if (display != null) {
						int[] eglConfigAttributes = {
							EGL10.EGL_RED_SIZE, 8,
							EGL10.EGL_GREEN_SIZE, 8,
							EGL10.EGL_BLUE_SIZE, 8,
							EGL10.EGL_ALPHA_SIZE, 8,
							EGL10.EGL_DEPTH_SIZE, 16,
							EGL10.EGL_NONE
						};
						int[] eglConfigCount = new int[1];
						eglConfigCount[0] = 0;
						egl.eglChooseConfig(display, eglConfigAttributes, null, 0, eglConfigCount);
						if (eglConfigCount[0] > 0) {
							EGLConfig[] eglConfigArray = new EGLConfig[eglConfigCount[0]];
							boolean wasSuccessful = egl.eglChooseConfig(
									display, eglConfigAttributes, eglConfigArray,
									eglConfigCount[0], eglConfigCount);
							if (wasSuccessful) {
								eglConfig = eglConfigArray[0];
							}
						}
					}

					// Create an OpenGL context with the acquired display configuration.
					if (eglConfig != null) {
						context = egl.eglCreateContext(display, eglConfig, EGL10.EGL_NO_CONTEXT, null);
						if (context == EGL10.EGL_NO_CONTEXT) {
							context = null;
						}
					}

					// Create an offscreen surface to bind the OpenGL context to.
					if (context != null) {
						int[] surfaceAttributes = {
							EGL10.EGL_WIDTH, 64,
							EGL10.EGL_HEIGHT, 64,
							EGL10.EGL_NONE
						};
						surface = egl.eglCreatePbufferSurface(display, eglConfig, surfaceAttributes);
						if (surface == EGL10.EGL_NO_SURFACE) {
							surface = null;
						}
					}

					// Fetch GPU info if an OpenGL context/surface was successfully created above.
					if (surface != null) {
						// Fetch the OpenGL C API interface.
						GL10 gl = (GL10)context.getGL();

						// Select our OpenGL context.
						egl.eglMakeCurrent(display, surface, surface, context);
						
						// Fetch the GPU's max texture size.
						{
							int[] intArray = new int[1];
							intArray[0] = 0;
							gl.glGetIntegerv(GL10.GL_MAX_TEXTURE_SIZE, intArray, 0);
							if (intArray[0] > 0) {
								GpuInfo.maxTextureSize = intArray[0];
							}
						}

						// Unselect our OpenGL context. (Must do so before detroying it.)
						egl.eglMakeCurrent(
								display, EGL10.EGL_NO_SURFACE,
								EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);

					}
				}
				catch (Exception ex) {
					ex.printStackTrace();
				}
				finally {
					// Destroy the temporary OpenGL context and its associated objects.
					if (egl != null) {
						if (surface != null) {
							try { egl.eglDestroySurface(display, surface); }
							catch (Exception ex) {}
						}
						if (context != null) {
							try { egl.eglDestroyContext(display, context); }
							catch (Exception ex) {}
						}
						if (display != null) {
							try { egl.eglTerminate(display); }
							catch (Exception ex) {}
						}
					}
				}

				// Flag that we've attempted to load GPU info. (We only need to do so once.)
				wasInitialized = true;
			}
		}
	}
}
