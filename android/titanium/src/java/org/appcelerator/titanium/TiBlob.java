/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2020 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ThumbnailUtils;
import android.util.Base64;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.KrollRuntime;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.util.KrollStreamHelper;
import org.appcelerator.titanium.io.TiBaseFile;
import org.appcelerator.titanium.util.TiBlobLruCache;
import org.appcelerator.titanium.util.TiImageHelper;
import org.appcelerator.titanium.util.TiMimeTypeHelper;

/**
 * A Titanium Blob object. A Blob can represent any opaque data or input stream.
 */
@Kroll.proxy
public class TiBlob extends KrollProxy
{
	private static final String TAG = "TiBlob";

	/** Represents a Blob that contains image data. */
	public static final int TYPE_IMAGE = 0;

	/** Represents a Blob that contains file data. */
	public static final int TYPE_FILE = 1;

	/** Represents a Blob that contains data. */
	public static final int TYPE_DATA = 2;

	/** Represents a Blob that contains String data. */
	public static final int TYPE_STRING = 3;

	private int type;
	private Object data;
	private String mimetype;
	private Bitmap image;
	private int width;
	private int height;
	private int uprightWidth;
	private int uprightHeight;

	// This handles the memory cache of images.
	private final TiBlobLruCache mMemoryCache = TiBlobLruCache.getInstance();

	private TiBlob(int type, Object data, String mimetype)
	{
		super();
		this.type = type;
		this.data = data;
		this.mimetype = mimetype;
		this.image = null;
		this.width = 0;
		this.height = 0;
		this.uprightWidth = 0;
		this.uprightHeight = 0;
	}

	/**
	 * Creates a new TiBlob object from String data.
	 * @param data the data used to create blob.
	 * @return new instance of TiBlob.
	 */
	public static TiBlob blobFromString(String data)
	{
		return new TiBlob(TYPE_STRING, data, "text/plain");
	}

	/**
	 * Creates a blob from a file and sets a mimeType based on the file name.
	 * @param file the file used to create blob.
	 * @return new instane of TiBlob.
	 */
	public static TiBlob blobFromFile(TiBaseFile file)
	{
		return blobFromFile(file, TiMimeTypeHelper.getMimeType(file.nativePath()));
	}

	/**
	 * Creates a blob from a file with the specified mimeType. If the passed mimeType is null,
	 * the mimeType will be determined using the file name.
	 * @param file the file used to create blob.
	 * @param mimeType the mimeType used to create blob.
	 * @return new instance of TiBlob.
	 */
	public static TiBlob blobFromFile(TiBaseFile file, String mimeType)
	{
		if (mimeType == null) {
			mimeType = TiMimeTypeHelper.getMimeType(file.nativePath());
		}
		TiBlob blob = new TiBlob(TYPE_FILE, file, mimeType);
		blob.loadBitmapInfo();
		return blob;
	}

	/**
	 * Creates a blob from a bitmap.
	 * @param image the image used to create blob.
	 * @return new instance of TiBlob.
	 */
	public static TiBlob blobFromImage(Bitmap image)
	{

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		String mimeType = "image/bitmap";
		byte[] data = new byte[0];
		if (image.hasAlpha()) {
			if (image.compress(CompressFormat.PNG, 100, bos)) {
				data = bos.toByteArray();
				mimeType = "image/png";
			}
		} else {
			if (image.compress(CompressFormat.JPEG, 100, bos)) {
				data = bos.toByteArray();
				mimeType = "image/jpeg";
			}
		}

		TiBlob blob = new TiBlob(TYPE_IMAGE, data, mimeType);
		blob.image = image;
		blob.width = image.getWidth();
		blob.height = image.getHeight();
		blob.uprightWidth = blob.width;
		blob.uprightHeight = blob.height;
		return blob;
	}

	/**
	 * Creates a blob from binary data, with mimeType as "application/octet-stream".
	 * @param data data used to create blob.
	 * @return new instance of TiBlob.
	 */
	public static TiBlob blobFromData(byte[] data)
	{
		return blobFromData(data, TiMimeTypeHelper.MIME_TYPE_OCTET_STREAM);
	}

	/**
	 * Creates a blob from binary data with the specified mimetype.
	 * If the passed mimetype is null, "application/octet-stream" will be used instead.
	 * @param data  binary data used to create blob.
	 * @param mimetype mimetype used to create blob.
	 * @return a new instance of TiBlob.
	 */
	public static TiBlob blobFromData(byte[] data, String mimetype)
	{
		if (mimetype == null || mimetype.length() == 0) {
			return new TiBlob(TYPE_DATA, data, TiMimeTypeHelper.MIME_TYPE_OCTET_STREAM);
		}
		TiBlob blob = new TiBlob(TYPE_DATA, data, mimetype);
		blob.loadBitmapInfo();
		return blob;
	}

	/**
	 * Determines the MIME-type by reading first few characters from the given input stream.
	 * @return the guessed MIME-type or null if the type could not be determined.
	 */
	public String guessContentTypeFromStream()
	{
		String mt = null;
		InputStream is = getInputStream();
		// We shouldn't try and sniff content type if mark isn't supported by this
		// input stream! Otherwise we'll read bytes that we can't stuff back anymore
		// so the stream will have been modified for future reads.
		if (is != null && is.markSupported()) {
			try {
				mt = URLConnection.guessContentTypeFromStream(is);
				if (mt == null) {
					mt = guessAdditionalContentTypeFromStream(is);
				}
			} catch (Exception e) {
				Log.e(TAG, e.getMessage(), e, Log.DEBUG_MODE);
			}
		}
		return mt;
	}

	/**
	 * Check for additional content type reading first few characters from the given input stream.
	 *
	 * @return the guessed MIME-type or null if the type could not be determined.
	 */
	private String guessAdditionalContentTypeFromStream(InputStream is)
	{
		String mt = null;

		if (is != null) {
			try {

				// Look ahead up to 12 bytes (highest number of bytes we care about for now)
				is.mark(12);
				byte[] bytes = new byte[12];
				int length = is.read(bytes);
				is.reset();
				if (length == -1) {
					return null;
				}

				// This is basically exactly what the normal JDK sniffs for, but Android's fork does not
				if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8') {
					mt = "image/gif";
				} else if (bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50 && bytes[2] == (byte) 0x4E
						   && bytes[3] == (byte) 0x47 && bytes[4] == (byte) 0x0D && bytes[5] == (byte) 0x0A
						   && bytes[6] == (byte) 0x1A && bytes[7] == (byte) 0x0A) {
					mt = "image/png";
				} else if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
					if ((bytes[3] == (byte) 0xE0)
						|| (bytes[3] == (byte) 0xE1 && bytes[6] == 'E' && bytes[7] == 'x' && bytes[8] == 'i'
							&& bytes[9] == 'f' && bytes[10] == 0)) {
						mt = "image/jpeg";
					} else if (bytes[3] == (byte) 0xEE) {
						mt = "image/jpg";
					}
				}
			} catch (Exception e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}
		return mt;
	}

	/**
	 * Update width and height if the file / data can be decoded into a bitmap successfully.
	 */
	public void loadBitmapInfo()
	{
		String mt = guessContentTypeFromStream();
		// Update mimetype based on the guessed MIME-type.
		if (mt != null && !mt.equals(mimetype)) {
			mimetype = mt;
		}

		// If the MIME-type is "image/*" or undetermined, try to decode the file / data into a bitmap.
		if (mimetype == null || mimetype.startsWith("image/")) {
			// Query the dimensions of a bitmap without allocating the memory for its pixels
			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inJustDecodeBounds = true;

			switch (type) {
				case TYPE_FILE:
					BitmapFactory.decodeStream(getInputStream(), null, opts);
					break;
				case TYPE_DATA:
					byte[] byteArray = (byte[]) data;
					BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length, opts);
					break;
			}

			// Update width and height after the file / data is decoded successfully
			if (opts.outWidth != -1 && opts.outHeight != -1) {
				this.width = opts.outWidth;
				this.height = opts.outHeight;

				int rotation = getImageOrientation();
				if ((rotation == 90) || (rotation == 270)) {
					this.uprightWidth = opts.outHeight;
					this.uprightHeight = opts.outWidth;
				} else {
					this.uprightWidth = opts.outWidth;
					this.uprightHeight = opts.outHeight;
				}
			}
		}
	}

	/**
	 * Returns the content of blob in form of binary data. Exception will be thrown
	 * if blob's type is unknown.
	 * @return binary data.
	 */
	@Kroll.method(name = "toArrayBuffer")
	public byte[] getBytes()
	{
		byte[] bytes = new byte[0];

		switch (type) {
			case TYPE_STRING:
				bytes = ((String) data).getBytes(StandardCharsets.UTF_8);
				break;
			case TYPE_DATA:
			case TYPE_IMAGE:
				//TODO deal with mimetypes.
				bytes = (byte[]) data;
				break;
			case TYPE_FILE:
				InputStream stream = getInputStream();
				if (stream != null) {
					try {
						bytes = KrollStreamHelper.toByteArray(stream, getLength());
					} finally {
						try {
							stream.close();
						} catch (IOException e) {
							Log.w(TAG, e.getMessage(), e);
						}
					}
				}
				break;
			default:
				throw new IllegalArgumentException("Unknown Blob type id " + type);
		}

		return bytes;
	}

	@Kroll.getProperty
	public int getLength()
	{
		switch (type) {
			case TYPE_FILE:
				return (int) ((TiBaseFile) data).size();
			case TYPE_DATA:
			case TYPE_IMAGE:
				return ((byte[]) data).length;
			default:
				// this is probably overly expensive.. is there a better way?
				// This should only happen for string types...
				return getBytes().length;
		}
	}

	/**
	 * @return An InputStream for reading the data of this blob.
	 */
	public InputStream getInputStream()
	{
		switch (type) {
			case TYPE_FILE:
				try {
					return ((TiBaseFile) data).getInputStream();
				} catch (Exception e) {
					Log.e(TAG, e.getMessage(), e);
					return null;
				}
			default:
				return new ByteArrayInputStream(getBytes());
		}
	}

	@Kroll.method
	public void append(TiBlob blob)
	{
		byte[] dataBytes = getBytes();
		byte[] appendBytes = blob.getBytes();
		byte[] newData = new byte[dataBytes.length + appendBytes.length];
		System.arraycopy(dataBytes, 0, newData, 0, dataBytes.length);
		System.arraycopy(appendBytes, 0, newData, dataBytes.length, appendBytes.length);

		switch (type) {
			case TYPE_STRING:
				data = new String(newData, StandardCharsets.UTF_8);
				break;
			case TYPE_IMAGE:
			case TYPE_DATA:
				data = newData;
				break;
			case TYPE_FILE:
				type = TYPE_DATA; // now it's a pure Data blob...
				data = newData;
				break;
			default:
				throw new IllegalArgumentException("Unknown Blob type id " + type);
		}
	}

	@Kroll.getProperty
	public String getText()
	{
		String result = null;

		// Only support String and Data. Same as iPhone
		switch (type) {
			case TYPE_STRING:
				result = (String) data;
			case TYPE_DATA:
			case TYPE_FILE:
				try {
					result = new String(getBytes(), StandardCharsets.UTF_8);
				} catch (Exception ex) {
					Log.w(TAG, "Unable to convert to string.");
				}
				break;
		}

		return result;
	}

	@Kroll.getProperty
	public String getMimeType()
	{
		return mimetype;
	}

	/**
	 * @return the blob's data.
	 */
	public Object getData()
	{
		return data;
	}

	/**
	 * @return The type of this Blob.
	 * @see TiBlob#TYPE_DATA
	 * @see TiBlob#TYPE_FILE
	 * @see TiBlob#TYPE_IMAGE
	 * @see TiBlob#TYPE_STRING
	 */
	@Kroll.getProperty
	public int getType()
	{
		return type;
	}

	@Kroll.getProperty
	public int getWidth()
	{
		return width;
	}

	@Kroll.getProperty
	public int getUprightWidth()
	{
		return this.uprightWidth;
	}

	@Kroll.getProperty
	public int getSize()
	{
		// if it's an image, return number of pixels (width * height)
		if (width != 0) {
			return width * height;
		}
		// Return number of bytes!
		return getLength();
	}

	@Kroll.getProperty
	public int getHeight()
	{
		return height;
	}

	@Kroll.getProperty
	public int getUprightHeight()
	{
		return this.uprightHeight;
	}

	@Kroll.method
	public String toString()
	{
		// Determine if this blob references binary data instead of text.
		// Note: If mime-type is "application/octet-stream", then type is unknown. Assume it is text.
		boolean isBinary = false;
		if ((this.mimetype != null) && TiMimeTypeHelper.isBinaryMimeType(this.mimetype)) {
			if (!this.mimetype.equals(TiMimeTypeHelper.MIME_TYPE_OCTET_STREAM)) {
				isBinary = true;
			}
		}

		// Attempt to fetch text from the blob, but only if it's NOT a known binary type.
		// Ex: Don't let Javascript toString() waste huge amounts of memory on a blob that wraps an image file.
		String text = null;
		if (!isBinary) {
			text = getText();
		}
		if (text == null) {
			text = "[object TiBlob]";
		}

		// Return the blob's text.
		return text;
	}

	@Kroll.getProperty
	public String getNativePath()
	{
		if (data == null) {
			return null;
		}
		if (this.type != TYPE_FILE) {
			return null;
		} else if (!(data instanceof TiBaseFile)) {
			Log.w(TAG, "getNativePath unable to return value: underlying data is not file, rather "
						   + data.getClass().getName());
			return null;
		} else {
			String path = ((TiBaseFile) data).nativePath();
			if (path != null && path.startsWith("content://")) {
				File f = ((TiBaseFile) data).getNativeFile();
				if (f != null) {
					path = f.getAbsolutePath();
					if (path != null && path.startsWith("/")) {
						path = "file://" + path;
					}
				}
			}
			return path;
		}
	}

	@Kroll.getProperty
	public TiFileProxy getFile()
	{
		TiFileProxy fileProxy = null;
		if (data instanceof TiBaseFile) {
			fileProxy = new TiFileProxy((TiBaseFile) data);
		} else if (data != null) {
			Log.w(TAG, "getFile not supported for non-file blob types.");
		}
		return fileProxy;
	}

	@Kroll.method
	public String toBase64()
	{
		return Base64.encodeToString(getBytes(), Base64.NO_WRAP);
	}

	public Bitmap getImage()
	{
		// If the image is not available but the width and height of the image are successfully fetched, the image can
		// be created by decoding the data.
		return getImage(null);
	}

	private Bitmap getImage(BitmapFactory.Options opts)
	{
		if (image == null && (width > 0 && height > 0)) {

			if (opts == null) {
				opts = new BitmapFactory.Options();
				opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
			}

			// The only opts that can be used to uniquely name an image is the inSampleSize
			int inSampleSize = opts.inSampleSize;
			String nativePath = getNativePath();
			String key = null;
			if (nativePath != null) {
				key = getNativePath() + "_" + inSampleSize;
				Bitmap bitmap = mMemoryCache.get(key);
				if (bitmap != null) {
					if (!bitmap.isRecycled()) {
						return bitmap;
					} else {
						mMemoryCache.remove(key);
					}
				}
			}

			try {
				Bitmap bitmap;
				switch (type) {
					case TYPE_FILE:
						bitmap = BitmapFactory.decodeStream(getInputStream(), null, opts);
						if (key != null) {
							mMemoryCache.put(key, bitmap);
						}
						return bitmap;
					case TYPE_DATA:
						byte[] byteArray = (byte[]) data;
						bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length, opts);
						if (key != null) {
							mMemoryCache.put(key, bitmap);
						}
						return bitmap;
				}
			} catch (OutOfMemoryError e) {
				TiBlobLruCache.getInstance().evictAll();
				Log.e(TAG, "Unable to get the image. Not enough memory: " + e.getMessage(), e);
				return null;
			}
		}
		return image;
	}

	private int getImageOrientation()
	{
		int rotation = 0;
		try (InputStream stream = getInputStream()) {
			rotation = TiImageHelper.getOrientation(stream);
		} catch (Exception ex) {
			Log.e(TAG, "Failed to fetch image EXIF orientation from blob.", ex);
		}
		return rotation;
	}

	@Kroll.method
	public TiBlob imageAsCropped(Object params)
	{
		Bitmap img = getImage();
		if (img == null) {
			return null;
		}
		if (!(params instanceof HashMap)) {
			Log.e(TAG, "Argument for imageAsCropped must be a dictionary");
			return null;
		}

		int rotation = getImageOrientation();

		KrollDict options = new KrollDict((HashMap) params);
		int widthCropped = options.optInt(TiC.PROPERTY_WIDTH, width);
		int heightCropped = options.optInt(TiC.PROPERTY_HEIGHT, height);
		int x = options.optInt(TiC.PROPERTY_X, (width - widthCropped) / 2);
		int y = options.optInt(TiC.PROPERTY_Y, (height - heightCropped) / 2);

		String nativePath = getNativePath();
		String key = null;
		if (nativePath != null) {
			key = getNativePath() + "_imageAsCropped_" + rotation + "_" + widthCropped + "_" + heightCropped + "_" + x
				  + "_" + y;
			Bitmap bitmap = mMemoryCache.get(key);
			if (bitmap != null) {
				if (!bitmap.isRecycled()) {
					return blobFromImage(bitmap);
				} else {
					mMemoryCache.remove(key);
				}
			}
		}

		try {
			Matrix matrix = new Matrix();
			//rotate
			matrix.postRotate(rotation);
			Bitmap imageCropped = Bitmap.createBitmap(img, x, y, widthCropped, heightCropped, matrix, true);
			if (img != image && img != imageCropped) {
				img.recycle();
				img = null;
			}
			if (key != null) {
				mMemoryCache.put(key, imageCropped);
			}
			return blobFromImage(imageCropped);
		} catch (OutOfMemoryError e) {
			TiBlobLruCache.getInstance().evictAll();
			Log.e(TAG, "Unable to crop the image. Not enough memory: " + e.getMessage(), e);
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "Unable to crop the image. Illegal Argument: " + e.getMessage(), e);
		} catch (Throwable t) {
			Log.e(TAG, "Unable to crop the image. Unknown exception: " + t.getMessage(), t);
		} finally {
			// Perform soft garbage collection to reclaim memory.
			if (KrollRuntime.getInstance() != null) {
				KrollRuntime.softGC();
			}
		}
		return null;
	}

	@Kroll.method
	public TiBlob imageAsResized(Number width, Number height)
	{
		boolean valid = (image != null) || (image == null && (this.width > 0 && this.height > 0));
		if (!valid) {
			return null;
		}

		int dstWidth = width.intValue();
		int dstHeight = height.intValue();
		int imgWidth = this.width;
		int imgHeight = this.height;

		BitmapFactory.Options opts = null;
		boolean scaleDown = ((image == null) && (dstWidth < imgWidth) && (dstHeight < imgHeight));
		if (scaleDown) {
			int scaleWidth = imgWidth / dstWidth;
			int scaleHeight = imgHeight / dstHeight;

			int targetScale = (scaleWidth < scaleHeight) ? scaleWidth : scaleHeight;
			int sampleSize = 1;
			while (targetScale >= 2) {
				sampleSize *= 2;
				targetScale /= 2;
			}

			opts = new BitmapFactory.Options();
			opts.inSampleSize = sampleSize;
			if (dstWidth == dstHeight) {
				// square images
				opts.inDensity = imgWidth;
				opts.inTargetDensity = dstWidth * sampleSize;
			}
			opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
		}

		int rotation = getImageOrientation();

		String key = null;
		String nativePath = getNativePath();
		if (nativePath != null) {
			key = nativePath + "_imageAsResized_" + rotation + "_" + dstWidth + "_" + dstHeight;
			Bitmap bitmap = mMemoryCache.get(key);
			if (bitmap != null) {
				if (!bitmap.isRecycled()) {
					return blobFromImage(bitmap);
				} else {
					mMemoryCache.remove(key);
				}
			}
		}

		Bitmap img = getImage(opts);
		if (img == null) {
			return null;
		}

		try {
			Bitmap imageResized = null;
			imgWidth = img.getWidth();
			imgHeight = img.getHeight();
			if (rotation != 0) {
				if ((rotation == 90) || (rotation == 270)) {
					int value = dstWidth;
					dstWidth = dstHeight;
					dstHeight = value;
				}
				float scaleWidth = (float) dstWidth / imgWidth;
				float scaleHeight = (float) dstHeight / imgHeight;
				Matrix matrix = new Matrix();
				matrix.postScale(scaleWidth, scaleHeight);
				matrix.postRotate(rotation);
				imageResized = Bitmap.createBitmap(img, 0, 0, imgWidth, imgHeight, matrix, true);
			} else {
				if (dstWidth == dstHeight) {
					// squared image
					imageResized = img;
				} else {
					// non squared image
					imageResized = Bitmap.createScaledBitmap(img, dstWidth, dstHeight, true);
				}
			}
			if (img != image && img != imageResized) {
				img.recycle();
				img = null;
			}
			if (key != null) {
				mMemoryCache.put(key, imageResized);
			}
			return blobFromImage(imageResized);
		} catch (OutOfMemoryError e) {
			TiBlobLruCache.getInstance().evictAll();
			Log.e(TAG, "Unable to resize the image. Not enough memory: " + e.getMessage(), e);
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "Unable to resize the image. Illegal Argument: " + e.getMessage(), e);
		} catch (Throwable t) {
			Log.e(TAG, "Unable to resize the image. Unknown exception: " + t.getMessage(), t);
		} finally {
			// Perform soft garbage collection to reclaim memory.
			if (KrollRuntime.getInstance() != null) {
				KrollRuntime.softGC();
			}
		}
		return null;
	}

	@Kroll.method
	public TiBlob imageAsCompressed(Number compressionQuality)
	{
		Bitmap img = getImage();
		if (img == null) {
			return null;
		}

		float quality = 1f;
		if (compressionQuality != null) {
			quality = compressionQuality.floatValue();
		}

		TiBlob result = null;
		ByteArrayOutputStream bos;

		try {
			bos = new ByteArrayOutputStream();
			if (img.compress(CompressFormat.JPEG, (int) (quality * 100), bos)) {
				byte[] data = bos.toByteArray();
				result = TiBlob.blobFromData(data, "image/jpeg");
			}
		} catch (OutOfMemoryError e) {
			TiBlobLruCache.getInstance().evictAll();
			Log.e(TAG, "Unable to get the thumbnail image. Not enough memory: " + e.getMessage(), e);
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "Unable to get the thumbnail image. Illegal Argument: " + e.getMessage(), e);
		} catch (Throwable t) {
			Log.e(TAG, "Unable to get the thumbnail image. Unknown exception: " + t.getMessage(), t);
		} finally {
			// [MOD-309] Free up memory to work around issue in Android
			if (img != null) {
				img.recycle();
				img = null;
			}
			bos = null;

			// Perform soft garbage collection to reclaim memory.
			if (KrollRuntime.getInstance() != null) {
				KrollRuntime.softGC();
			}
		}

		return result;
	}

	@Kroll.method
	public TiBlob imageAsWebP(Number compressionQuality)
	{
		Bitmap img = getImage();
		if (img == null) {
			return null;
		}

		float quality = 1f;
		if (compressionQuality != null) {
			quality = compressionQuality.floatValue();
		}

		TiBlob result = null;
		ByteArrayOutputStream bos;

		try {
			bos = new ByteArrayOutputStream();
			if (img.compress(
				quality == 1f ? CompressFormat.WEBP_LOSSLESS : CompressFormat.WEBP_LOSSY, (int) (quality * 100), bos)
			) {
				byte[] data = bos.toByteArray();

				BitmapFactory.Options bfOptions = new BitmapFactory.Options();
				bfOptions.inPurgeable = true;
				bfOptions.inInputShareable = true;

				result = TiBlob.blobFromData(data, "image/webp");
			}
		} catch (OutOfMemoryError e) {
			TiBlobLruCache.getInstance().evictAll();
			Log.e(TAG, "Unable to get the thumbnail image. Not enough memory: " + e.getMessage(), e);
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "Unable to get the thumbnail image. Illegal Argument: " + e.getMessage(), e);
		} catch (Throwable t) {
			Log.e(TAG, "Unable to get the thumbnail image. Unknown exception: " + t.getMessage(), t);
		} finally {
			// [MOD-309] Free up memory to work around issue in Android
			if (img != null) {
				img.recycle();
				img = null;
			}
			bos = null;

			// Perform soft garbage collection to reclaim memory.
			KrollRuntime instance = KrollRuntime.getInstance();
			if (instance != null) {
				instance.softGC();
			}
		}

		return result;
	}

	@Kroll.method
	public TiBlob imageAsThumbnail(Number size, @Kroll.argument(optional = true) Number borderSize,
								   @Kroll.argument(optional = true) Number cornerRadius)
	{
		Bitmap img = getImage();
		if (img == null) {
			return null;
		}

		int rotation = getImageOrientation();

		int thumbnailSize = size.intValue();

		int border = 1;
		if (borderSize != null) {
			border = borderSize.intValue();
		}
		float radius = 0f;
		if (cornerRadius != null) {
			radius = cornerRadius.floatValue();
		}

		String nativePath = getNativePath();
		String key = null;
		if (nativePath != null) {
			key = getNativePath() + "_imageAsThumbnail_" + rotation + "_" + thumbnailSize + "_" + border + "_" + radius;
			Bitmap bitmap = mMemoryCache.get(key);
			if (bitmap != null) {
				if (!bitmap.isRecycled()) {
					return blobFromImage(bitmap);
				} else {
					mMemoryCache.remove(key);
				}
			}
		}

		try {
			Bitmap imageFinal = null;
			Bitmap imageThumbnail = ThumbnailUtils.extractThumbnail(img, thumbnailSize, thumbnailSize);
			if (img != image && img != imageThumbnail) {
				img.recycle();
				img = null;
			}

			if (radius == 0) {
				if (border == 0) {
					imageFinal = imageThumbnail;
				} else {
					imageFinal = TiImageHelper.imageWithTransparentBorder(imageThumbnail, border);
					if (imageThumbnail != image && imageThumbnail != imageFinal) {
						imageThumbnail.recycle();
						imageThumbnail = null;
					}
				}
			} else {
				imageFinal = TiImageHelper.imageWithRoundedCorner(imageThumbnail, radius, border);
				if (imageThumbnail != image && imageThumbnail != imageFinal) {
					imageThumbnail.recycle();
					imageThumbnail = null;
				}
			}

			if (rotation != 0) {
				Log.w(TAG, "Rotating: " + rotation);
				imageFinal = TiImageHelper.rotateImage(imageFinal, rotation);
			}
			if (key != null) {
				mMemoryCache.put(key, imageFinal);
			}
			return blobFromImage(imageFinal);

		} catch (OutOfMemoryError e) {
			TiBlobLruCache.getInstance().evictAll();
			Log.e(TAG, "Unable to get the thumbnail image. Not enough memory: " + e.getMessage(), e);
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "Unable to get the thumbnail image. Illegal Argument: " + e.getMessage(), e);
		} catch (Throwable t) {
			Log.e(TAG, "Unable to get the thumbnail image. Unknown exception: " + t.getMessage(), t);
		} finally {
			// Perform soft garbage collection to reclaim memory.
			if (KrollRuntime.getInstance() != null) {
				KrollRuntime.softGC();
			}
		}
		return null;
	}

	@Kroll.method
	public TiBlob imageWithAlpha()
	{
		Bitmap img = getImage();
		if (img == null) {
			return null;
		}

		int rotation = getImageOrientation();

		String nativePath = getNativePath();
		String key = null;
		if (nativePath != null) {
			key = getNativePath() + "_imageWithAlpha_" + rotation;
			Bitmap bitmap = mMemoryCache.get(key);
			if (bitmap != null) {
				if (!bitmap.isRecycled()) {
					return blobFromImage(bitmap);
				} else {
					mMemoryCache.remove(key);
				}
			}
		}

		try {
			Bitmap imageWithAlpha = TiImageHelper.imageWithAlpha(img);
			if (img != image && img != imageWithAlpha) {
				img.recycle();
				img = null;
			}
			if (rotation != 0) {
				imageWithAlpha = TiImageHelper.rotateImage(imageWithAlpha, rotation);
			}
			if (key != null) {
				mMemoryCache.put(key, imageWithAlpha);
			}
			return blobFromImage(imageWithAlpha);
		} catch (OutOfMemoryError e) {
			TiBlobLruCache.getInstance().evictAll();
			Log.e(TAG, "Unable to get the image with alpha. Not enough memory: " + e.getMessage(), e);
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "Unable to get the image with alpha. Illegal Argument: " + e.getMessage(), e);
		} catch (Throwable t) {
			Log.e(TAG, "Unable to get the image with alpha. Unknown exception: " + t.getMessage(), t);
		} finally {
			// Perform soft garbage collection to reclaim memory.
			if (KrollRuntime.getInstance() != null) {
				KrollRuntime.softGC();
			}
		}
		return null;
	}

	@Kroll.method
	public TiBlob imageWithRoundedCorner(Number cornerRadius, @Kroll.argument(optional = true) Number borderSize)
	{
		Bitmap img = getImage();
		if (img == null) {
			return null;
		}

		int rotation = getImageOrientation();

		float radius = cornerRadius.floatValue();
		float border = 1f;
		if (borderSize != null) {
			border = borderSize.floatValue();
		}

		String nativePath = getNativePath();
		String key = null;
		if (nativePath != null) {
			key = getNativePath() + "_imageWithRoundedCorner_" + rotation + "_" + border + "_" + radius;
			Bitmap bitmap = mMemoryCache.get(key);
			if (bitmap != null) {
				if (!bitmap.isRecycled()) {
					return blobFromImage(bitmap);
				} else {
					mMemoryCache.remove(key);
				}
			}
		}

		try {
			Bitmap imageRoundedCorner = TiImageHelper.imageWithRoundedCorner(img, radius, border);
			if (img != image && img != imageRoundedCorner) {
				img.recycle();
				img = null;
			}
			if (rotation != 0) {
				imageRoundedCorner = TiImageHelper.rotateImage(imageRoundedCorner, rotation);
			}
			if (key != null) {
				mMemoryCache.put(key, imageRoundedCorner);
			}
			return blobFromImage(imageRoundedCorner);
		} catch (OutOfMemoryError e) {
			TiBlobLruCache.getInstance().evictAll();
			Log.e(TAG, "Unable to get the image with rounded corner. Not enough memory: " + e.getMessage(), e);
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "Unable to get the image with rounded corner. Illegal Argument: " + e.getMessage(), e);
		} catch (Throwable t) {
			Log.e(TAG, "Unable to get the image with rounded corner. Unknown exception: " + t.getMessage(), t);
		} finally {
			// Perform soft garbage collection to reclaim memory.
			if (KrollRuntime.getInstance() != null) {
				KrollRuntime.softGC();
			}
		}
		return null;
	}

	@Kroll.method
	public TiBlob imageWithTransparentBorder(Number size)
	{
		Bitmap img = getImage();
		if (img == null) {
			return null;
		}

		int rotation = getImageOrientation();

		int borderSize = size.intValue();

		String nativePath = getNativePath();
		String key = null;
		if (nativePath != null) {
			key = getNativePath() + "_imageWithTransparentBorder_" + rotation + "_" + borderSize;
			Bitmap bitmap = mMemoryCache.get(key);
			if (bitmap != null) {
				if (!bitmap.isRecycled()) {
					return blobFromImage(bitmap);
				} else {
					mMemoryCache.remove(key);
				}
			}
		}

		try {
			Bitmap imageWithBorder = TiImageHelper.imageWithTransparentBorder(img, borderSize);
			if (img != image && img != imageWithBorder) {
				img.recycle();
				img = null;
			}
			if (rotation != 0) {
				imageWithBorder = TiImageHelper.rotateImage(imageWithBorder, rotation);
			}
			if (key != null) {
				mMemoryCache.put(key, imageWithBorder);
			}
			return blobFromImage(imageWithBorder);
		} catch (OutOfMemoryError e) {
			TiBlobLruCache.getInstance().evictAll();
			Log.e(TAG, "Unable to get the image with transparent border. Not enough memory: " + e.getMessage(), e);
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "Unable to get the image with transparent border. Illegal Argument: " + e.getMessage(), e);
		} catch (Throwable t) {
			Log.e(TAG, "Unable to get the image with transparent border. Unknown exception: " + t.getMessage(), t);
		} finally {
			// Perform soft garbage collection to reclaim memory.
			if (KrollRuntime.getInstance() != null) {
				KrollRuntime.softGC();
			}
		}
		return null;
	}

	@Override
	public void release()
	{
		if (data != null) {
			data = null;
		}
		if (image != null) {
			image = null;
		}
		super.release();
	}

	@Override
	public String getApiName()
	{
		return "Ti.Blob";
	}
}
