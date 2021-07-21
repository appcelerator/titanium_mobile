/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.view;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.TiFileProxy;
import org.appcelerator.titanium.io.TiBaseFile;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiDownloadListener;
import org.appcelerator.titanium.util.TiDownloadManager;
import org.appcelerator.titanium.util.TiExifOrientation;
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
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.View;
import android.webkit.URLUtil;

/**
 * Helper class for loading, scaling, and caching images if necessary.
 */
@SuppressWarnings("deprecation")
public class TiDrawableReference
{
	private static Map<Integer, Bounds> boundsCache;
	static
	{
		boundsCache = Collections.synchronizedMap(new HashMap<Integer, Bounds>());
	}

	public enum DrawableReferenceType { NULL, URL, RESOURCE_ID, BLOB, FILE }

	public static class Bounds
	{
		public static final int UNKNOWN = TiDrawableReference.UNKNOWN;
		public int height = UNKNOWN;
		public int width = UNKNOWN;
	}

	private static final String TAG = "TiDrawableReference";
	private static final String FILE_PREFIX = "file://";
	private static final int UNKNOWN = -1;
	private static final int DEFAULT_SAMPLE_SIZE = 1;
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
		softActivity = new SoftReference<>(activity);
		ApplicationInfo appInfo;

		if (activity != null) {
			appInfo = activity.getApplicationInfo();
		} else {
			appInfo = TiApplication.getInstance().getApplicationInfo();
		}
		anyDensityFalse = (appInfo.flags & ApplicationInfo.FLAG_SUPPORTS_SCREEN_DENSITIES) == 0;
		decodeRetries = DEFAULT_DECODE_RETRIES;
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
		return (this.hashCode() == ((TiDrawableReference) object).hashCode());
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
	 */
	public static TiDrawableReference fromUrl(KrollProxy proxy, String url)
	{
		Activity activity = TiApplication.getAppCurrentActivity();
		// Attempt to fetch an activity from the given proxy.
		if (proxy != null) {
			activity = proxy.getActivity();
		}

		if (url == null || url.length() == 0 || url.trim().length() == 0) {
			return new TiDrawableReference(activity, DrawableReferenceType.NULL);
		}

		return fromUrl(activity, TiUrl.resolve(TiC.URL_APP_PREFIX, url, null));
	}

	/**
	 * Creates and returns a TiDrawableReference with type DrawableReferenceType.URL.
	 * @param activity the referenced activity.
	 * @param url the resource's url.
	 * @return A ready instance of TiDrawableReference.
	 */
	public static TiDrawableReference fromUrl(Activity activity, String url)
	{
		TiDrawableReference ref = new TiDrawableReference(activity, DrawableReferenceType.URL);
		ref.url = url;

		// Could still be a resource image file in android/images/medium|high|low. Check once.
		if (url != null) {
			int id = TiUIHelper.getResourceId(url);
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
			String message
				= "Unknown drawable reference inside dictionary. Expected key 'media' to be a blob. "
				+ "Returning null drawable reference.";
			Log.w(TAG, message);
			return fromObject(activity, null);
		}
	}

	/**
	 * Does its best to determine the type of reference (url, blob, etc) based on object parameter.
	 * <p>
	 * Uses the given proxy to resolve relative paths to an image file, if applicable.
	 * @param proxy Used to acquire an activty and resolve relative paths if given object is a string path.
	 * @param object Reference to the image to be loaded such as a file, path, blob, etc.
	 * @return Returns an instance of TiDrawableReference wrapping the given object.
	 */
	public static TiDrawableReference fromObject(KrollProxy proxy, Object object)
	{
		// Attempt to fetch an activity from the given proxy.
		Activity activity = null;
		if (proxy != null) {
			activity = proxy.getActivity();
		}

		// If given object is a string:
		// - Resolve its relative path, if applicable.
		// - Convert the string to a URL.
		if ((proxy != null) && (object instanceof String)) {
			object = proxy.resolveUrl(null, (String) object);
		}

		// Create a drawable reference from the given object.
		return TiDrawableReference.fromObject(activity, object);
	}

	/**
	 * Does its best to determine the type of reference (url, blob, etc) based on object parameter.
	 * @param activity the referenced activity.
	 * @param object the referenced object.
	 * @return A ready instance of TiDrawableReference.
	 */
	public static TiDrawableReference fromObject(Activity activity, Object object)
	{
		if (object == null) {
			return new TiDrawableReference(activity, DrawableReferenceType.NULL);
		}

		if (object instanceof String) {
			return fromUrl(activity, TiConvert.toString(object));
		} else if (object instanceof HashMap) {
			return fromDictionary(activity, (HashMap) object);
		} else if (object instanceof TiBaseFile) {
			return fromFile(activity, (TiBaseFile) object);
		} else if (object instanceof TiBlob) {
			return fromBlob(activity, TiConvert.toBlob(object));
		} else if (object instanceof Number) {
			return fromResourceId(activity, ((Number) object).intValue());
		} else if (object instanceof TiFileProxy) {
			return fromFile(activity, ((TiFileProxy) object).getBaseFile());
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
	 */
	public Bitmap getBitmap(boolean needRetry)
	{
		return getBitmap(needRetry, false);
	}

	/**
	 * Gets the bitmap from the resource. If densityScaled is set to true, image is scaled
	 * based on the device density otherwise no sampling/scaling is done.
	 * When needRetry is set to true, it will retry loading when decode fails.
	 * If decode fails because of out of memory, clear the memory and call GC and retry loading a smaller image.
	 * If decode fails because of the odd Android 2.3/Gingerbread behavior (TIMOB-3599), retry loading the original image.
	 * This method should be called from a background thread when needRetry is set to true because it may block
	 * the thread if it needs to retry several times.
	 * @param needRetry If true, it will retry loading when decode fails.
	 * @return Bitmap, or null if errors occurred while trying to load or fetch it.
	 */
	public Bitmap getBitmap(boolean needRetry, boolean densityScaled)
	{
		InputStream is = getInputStream();
		Bitmap b = null;
		BitmapFactory.Options opts = new BitmapFactory.Options();
		opts.inInputShareable = true;
		opts.inPurgeable = true;
		opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
		if (densityScaled) {
			DisplayMetrics dm = new DisplayMetrics();
			dm.setToDefaults();
			opts.inDensity = DisplayMetrics.DENSITY_MEDIUM;

			opts.inTargetDensity = dm.densityDpi;
			opts.inScaled = true;
		}

		try {
			if (needRetry) {
				for (int i = 0; i < decodeRetries; i++) {
					// getInputStream() fails sometimes but after retry it will get
					// input stream successfully.
					if (is == null) {
						Log.i(TAG, "Unable to get input stream for bitmap. Will retry.", Log.DEBUG_MODE);
						try {
							Thread.sleep(100);
						} catch (InterruptedException ie) {
							// Ignore
						}
						is = getInputStream();
						continue;
					}
					try {
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
						} catch (InterruptedException ie) {
							// Ignore
						}
					} catch (OutOfMemoryError e) { // Decode fails because of out of memory
						oomOccurred = true;
						Log.e(TAG, "Unable to load bitmap. Not enough memory: " + e.getMessage(), e);
						Log.i(TAG, "Clear memory cache and signal a GC. Will retry load.", Log.DEBUG_MODE);
						TiImageLruCache.getInstance().evictAll();
						System.gc(); // See if we can force a compaction
						try {
							Thread.sleep(1000);
						} catch (InterruptedException ie) {
							// Ignore
						}
						opts.inSampleSize = (int) Math.pow(2, i);
					}
				}
			} else {
				if (is == null) {
					return null;
				}
				try {
					oomOccurred = false;
					b = BitmapFactory.decodeStream(is, null, opts);
				} catch (OutOfMemoryError e) {
					oomOccurred = true;
					Log.e(TAG, "Unable to load bitmap. Not enough memory: " + e.getMessage(), e);
				}
			}
		} finally {
			if (is == null) {
				return null;
			}
			try {
				is.close();
			} catch (IOException e) {
				Log.e(TAG, "Problem closing stream: " + e.getMessage(), e);
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
		if (!isTypeResourceId()) {
			return null;
		}
		Drawable drawable = null;
		Resources resources = getResources();
		if (resources != null && resourceId > 0) {
			try {
				drawable = resources.getDrawable(resourceId);
			} catch (Resources.NotFoundException e) {
				drawable = null;
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
	 * Gets a resource drawable directly if the reference is to a resource, else
	 * makes a BitmapDrawable with default attributes.
	 */
	public Drawable getDrawable()
	{
		Drawable drawable = getResourceDrawable();
		if (drawable == null) {
			Bitmap b = getBitmap();
			if (b != null) {
				drawable = new BitmapDrawable(b);
			}
		}
		return drawable;
	}

	/**
	 * Gets a scaled resource drawable directly if the reference is to a resource, else
	 * makes a BitmapDrawable with default attributes. Scaling is done based on the device
	 * resolution.
	 */
	public Drawable getDensityScaledDrawable()
	{
		Drawable drawable = getResourceDrawable();
		if (drawable == null) {
			Bitmap b = getBitmap(false, true);
			if (b != null) {
				drawable = new BitmapDrawable(b);
			}
		}
		return drawable;
	}
	/**
	 * Gets the bitmap, scaled to a specific width & height.
	 * @param destWidth Width in pixels of resulting scaled bitmap
	 * @param destHeight Height in pixels of resulting scaled bitmap
	 * @return Bitmap, or null if any problem getting it.  Check logcat if null.
	 */
	public Bitmap getBitmap(int destWidth, int destHeight)
	{
		return getBitmap(null, TiConvert.toTiDimension(new Integer(destWidth), TiDimension.TYPE_WIDTH),
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
		double aspectRatio = (double) srcWidth / (double) srcHeight;
		destHeight = (int) ((double) destWidth / aspectRatio);
		return getBitmap(destWidth, destHeight);
	}

	private Bounds calcDestSize(int srcWidth, int srcHeight, TiDimension destWidthDimension,
								TiDimension destHeightDimension, View parent)
	{
		Bounds bounds = new Bounds();
		int destWidth, destHeight, containerWidth, containerHeight, parentWidth, parentHeight;
		destWidth = destHeight = parentWidth = parentHeight = containerWidth = containerHeight =
			TiDrawableReference.UNKNOWN;
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
			Log.w(TAG,
				  "Could not determine container width for image. Defaulting to source width. This shouldn't happen.");
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
			Log.w(
				TAG,
				"Could not determine container height for image. Defaulting to source height. This shouldn't happen.");
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
			return null;
		}

		Bitmap b = null;
		try {
			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inInputShareable = true;
			opts.inPurgeable = true;
			opts.inSampleSize = calcSampleSize(srcWidth, srcHeight, destWidth, destHeight);
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
					if (orientation < 0) {
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
						destWidth =
							(int) (destWidth * displayMetrics.density
								   + 0.5f); // 0.5 is to force round up of dimension. Casting to int drops decimals.
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
		int hash = this.hashCode();
		if (boundsCache.containsKey(hash)) {
			return boundsCache.get(hash);
		}
		Bounds bounds = new Bounds();
		if (isTypeNull()) {
			return bounds;
		}

		try (InputStream stream = getInputStream()) {
			if (stream != null) {
				BitmapFactory.Options bfo = new BitmapFactory.Options();
				bfo.inJustDecodeBounds = true;
				BitmapFactory.decodeStream(stream, null, bfo);
				bounds.height = bfo.outHeight;
				bounds.width = bfo.outWidth;
			} else {
				Log.w(TAG, "Could not open stream for drawable, therefore bounds checking could not be completed");
			}
		} catch (Exception ex) {
			Log.w(TAG, "Failed to access image bounds", ex);
		}

		boundsCache.put(hash, bounds);
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
				Log.e(TAG, "Problem opening stream with url " + url + ": " + e.getMessage());
			}

		} else if (isTypeFile() && file != null) {
			try {
				stream = file.getInputStream();
			} catch (IOException e) {
				Log.e(TAG, "Problem opening stream from file " + file.name() + ": " + e.getMessage());
			}

		} else if (isTypeBlob() && blob != null) {
			stream = blob.getInputStream();
		} else if (isTypeResourceId() && resourceId != UNKNOWN) {
			try {
				stream = TiApplication.getInstance().getResources().openRawResource(resourceId);
			} catch (Resources.NotFoundException e) {
				String errorMessage
					= "Drawable resource could not be opened. Are you sure you have the resource for the current "
					+ "device configuration (orientation, screen size, etc.)?";
				Log.e(TAG, errorMessage);
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
			return DEFAULT_SAMPLE_SIZE;
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
	public int calcSampleSize(View parent, int srcWidth, int srcHeight, TiDimension destWidthDimension,
							  TiDimension destHeightDimension)
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

	private Bitmap getRotatedBitmap(Bitmap src, int orientation)
	{
		Matrix m = new Matrix();
		m.postRotate(orientation);
		return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, false);
	}

	public int getOrientation()
	{
		int orientation = 0;
		try (InputStream inputStream = getInputStream()) {
			if (inputStream != null) {
				orientation = TiImageHelper.getOrientation(inputStream);
			}
		} catch (Exception ex) {
		}
		return orientation;
	}

	public TiExifOrientation getExifOrientation()
	{
		try (InputStream inputStream = getInputStream()) {
			if (inputStream != null) {
				return TiImageHelper.getExifOrientation(inputStream);
			}
		} catch (Exception ex) {
		}
		return TiExifOrientation.UPRIGHT;
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
}
