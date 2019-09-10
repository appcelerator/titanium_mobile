/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2016 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.media;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.KrollPromise;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.util.TiTempFileHelper;
import org.appcelerator.titanium.ContextSpecific;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiFileProxy;
import org.appcelerator.titanium.io.TiBaseFile;
import org.appcelerator.titanium.io.TiFileProvider;
import org.appcelerator.titanium.io.TiFile;
import org.appcelerator.titanium.io.TiFileFactory;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiActivityResultHandler;
import org.appcelerator.titanium.util.TiActivitySupport;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiFileHelper;
import org.appcelerator.titanium.util.TiIntentWrapper;
import org.appcelerator.titanium.util.TiMimeTypeHelper;
import org.appcelerator.titanium.util.TiUIHelper;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.media.CamcorderProfile;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.view.Window;

@SuppressWarnings("deprecation")
@Kroll.module
@ContextSpecific
public class MediaModule extends KrollModule implements Handler.Callback
{
	private static final String TAG = "TiMedia";

	private static final long[] DEFAULT_VIBRATE_PATTERN = { 100L, 250L };

	// The mode FOCUS_MODE_CONTINUOUS_PICTURE is added in API 14
	protected static final String FOCUS_MODE_CONTINUOUS_PICTURE = "continuous-picture";
	protected static final String PROP_AUTOHIDE = "autohide";
	protected static final String PROP_AUTOSAVE = "saveToPhotoGallery";
	protected static final String PROP_OVERLAY = "overlay";

	@Kroll.constant
	public static final int UNKNOWN_ERROR = -1;
	@Kroll.constant
	public static final int NO_ERROR = 0;
	@Kroll.constant
	public static final int DEVICE_BUSY = 1;
	@Kroll.constant
	public static final int NO_CAMERA = 2;
	@Kroll.constant
	public static final int NO_VIDEO = 3;

	@Kroll.constant
	public static final int VIDEO_SCALING_NONE = 0;
	@Kroll.constant
	public static final int VIDEO_SCALING_ASPECT_FILL = 1;
	@Kroll.constant
	public static final int VIDEO_SCALING_ASPECT_FIT = 2;
	@Kroll.constant
	public static final int VIDEO_SCALING_MODE_FILL = 3;

	@Kroll.constant
	public static final int VIDEO_CONTROL_DEFAULT = 0;
	@Kroll.constant
	public static final int VIDEO_CONTROL_EMBEDDED = 1;
	@Kroll.constant
	public static final int VIDEO_CONTROL_FULLSCREEN = 2;
	@Kroll.constant
	public static final int VIDEO_CONTROL_NONE = 3;
	@Kroll.constant
	public static final int VIDEO_CONTROL_HIDDEN = 4;

	@Kroll.constant
	public static final int VIDEO_LOAD_STATE_UNKNOWN = 0;
	@Kroll.constant
	public static final int VIDEO_LOAD_STATE_PLAYABLE = 1 << 0;
	@Kroll.constant
	public static final int VIDEO_LOAD_STATE_PLAYTHROUGH_OK = 1 << 1;
	@Kroll.constant
	public static final int VIDEO_LOAD_STATE_STALLED = 1 << 2;

	@Kroll.constant
	public static final int VIDEO_PLAYBACK_STATE_STOPPED = 0;
	@Kroll.constant
	public static final int VIDEO_PLAYBACK_STATE_PLAYING = 1;
	@Kroll.constant
	public static final int VIDEO_PLAYBACK_STATE_PAUSED = 2;
	@Kroll.constant
	public static final int VIDEO_PLAYBACK_STATE_INTERRUPTED = 3;
	@Kroll.constant
	public static final int VIDEO_PLAYBACK_STATE_SEEKING_FORWARD = 4;
	@Kroll.constant
	public static final int VIDEO_PLAYBACK_STATE_SEEKING_BACKWARD = 5;

	@Kroll.constant
	public static final int QUALITY_LOW = CamcorderProfile.QUALITY_LOW;
	@Kroll.constant
	public static final int QUALITY_HIGH = CamcorderProfile.QUALITY_HIGH;
	@Kroll.constant
	public static final int QUALITY_640x480 = CamcorderProfile.QUALITY_480P;
	@Kroll.constant
	public static final int QUALITY_IFRAME_1280x720 = CamcorderProfile.QUALITY_720P;

	@Kroll.constant
	public static final int VIDEO_FINISH_REASON_PLAYBACK_ENDED = 0;
	@Kroll.constant
	public static final int VIDEO_FINISH_REASON_PLAYBACK_ERROR = 1;
	@Kroll.constant
	public static final int VIDEO_FINISH_REASON_USER_EXITED = 2;

	@Kroll.constant
	public static final int VIDEO_REPEAT_MODE_NONE = 0;
	@Kroll.constant
	public static final int VIDEO_REPEAT_MODE_ONE = 1;

	@Kroll.constant
	public static final int VIDEO_TIME_OPTION_NEAREST_KEYFRAME = MediaMetadataRetriever.OPTION_CLOSEST;
	@Kroll.constant
	public static final int VIDEO_TIME_OPTION_CLOSEST_SYNC = MediaMetadataRetriever.OPTION_CLOSEST_SYNC;
	@Kroll.constant
	public static final int VIDEO_TIME_OPTION_NEXT_SYNC = MediaMetadataRetriever.OPTION_NEXT_SYNC;
	@Kroll.constant
	public static final int VIDEO_TIME_OPTION_PREVIOUS_SYNC = MediaMetadataRetriever.OPTION_PREVIOUS_SYNC;

	@Kroll.constant
	public static final String MEDIA_TYPE_LIVEPHOTO = "com.apple.live-photo";
	@Kroll.constant
	public static final String MEDIA_TYPE_PHOTO = "public.image";
	@Kroll.constant
	public static final String MEDIA_TYPE_VIDEO = "public.video";

	@Kroll.constant
	public static final int CAMERA_FRONT = 0;
	@Kroll.constant
	public static final int CAMERA_REAR = 1;
	@Kroll.constant
	public static final int CAMERA_FLASH_OFF = 0;
	@Kroll.constant
	public static final int CAMERA_FLASH_ON = 1;
	@Kroll.constant
	public static final int CAMERA_FLASH_AUTO = 2;

	@Kroll.constant
	public static final int AUDIO_STATE_BUFFERING = 0; // current playback is in the buffering from the network state
	@Kroll.constant
	public static final int AUDIO_STATE_INITIALIZED = 1; // current playback is in the initialization state
	@Kroll.constant
	public static final int AUDIO_STATE_PAUSED = 2; // current playback is in the paused state
	@Kroll.constant
	public static final int AUDIO_STATE_PLAYING = 3; // current playback is in the playing state
	@Kroll.constant
	public static final int AUDIO_STATE_STARTING = 4; // current playback is in the starting playback state
	@Kroll.constant
	public static final int AUDIO_STATE_STOPPED = 5; // current playback is in the stopped state
	@Kroll.constant
	public static final int AUDIO_STATE_STOPPING = 6; // current playback is in the stopping state
	@Kroll.constant
	public static final int AUDIO_STATE_WAITING_FOR_DATA =
		7; // current playback is in the waiting for audio data from the network state
	@Kroll.constant
	public static final int AUDIO_STATE_WAITING_FOR_QUEUE =
		8; //  current playback is in the waiting for audio data to fill the queue state

	private static String mediaType = MEDIA_TYPE_PHOTO;
	private static String extension = ".jpg";
	private TiTempFileHelper tempFileHelper;

	private static final String MIME_IMAGE = "image/*";

	private static class ApiLevel16
	{
		private ApiLevel16()
		{
		}

		public static void setIntentClipData(Intent intent, ClipData data)
		{
			if (intent != null) {
				intent.setClipData(data);
			}
		}
	}

	public MediaModule()
	{
		super();
		tempFileHelper = new TiTempFileHelper(TiApplication.getInstance());
	}

	@Kroll.method
	public void vibrate(@Kroll.argument(optional = true) long[] pattern)
	{
		if ((pattern == null) || (pattern.length == 0)) {
			pattern = DEFAULT_VIBRATE_PATTERN;
		}
		Vibrator vibrator = (Vibrator) TiApplication.getInstance().getSystemService(Context.VIBRATOR_SERVICE);
		if (vibrator != null) {
			vibrator.vibrate(pattern, -1);
		}
	}

	private int getLastImageId(Activity activity)
	{
		final String[] imageColumns = { MediaStore.Images.Media._ID };
		final String imageOrderBy = MediaStore.Images.Media._ID + " DESC";
		final String imageWhere = null;
		final String[] imageArguments = null;
		Cursor imageCursor = activity.getContentResolver().query(
			MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageColumns, imageWhere, imageArguments, imageOrderBy);
		if (imageCursor == null) {
			return -1;
		}
		if (imageCursor.moveToFirst()) {
			int id = imageCursor.getInt(imageCursor.getColumnIndex(MediaStore.Images.Media._ID));
			imageCursor.close();
			return id;
		} else {
			return 0;
		}
	}

	private void launchNativeCamera(KrollDict cameraOptions)
	{
		KrollFunction successCallback = null;
		KrollFunction cancelCallback = null;
		KrollFunction errorCallback = null;
		boolean saveToPhotoGallery = false;
		String[] mediaTypes = null;
		String intentType = MediaStore.ACTION_IMAGE_CAPTURE;
		int videoMaximumDuration = 0;
		int videoQuality = QUALITY_HIGH;
		int cameraType = 0;

		if (cameraOptions.containsKeyAndNotNull(TiC.PROPERTY_SUCCESS)) {
			successCallback = (KrollFunction) cameraOptions.get(TiC.PROPERTY_SUCCESS);
		}
		if (cameraOptions.containsKeyAndNotNull(TiC.PROPERTY_CANCEL)) {
			cancelCallback = (KrollFunction) cameraOptions.get(TiC.PROPERTY_CANCEL);
		}
		if (cameraOptions.containsKeyAndNotNull(TiC.EVENT_ERROR)) {
			errorCallback = (KrollFunction) cameraOptions.get(TiC.EVENT_ERROR);
		}
		if (cameraOptions.containsKeyAndNotNull("saveToPhotoGallery")) {
			saveToPhotoGallery = cameraOptions.getBoolean("saveToPhotoGallery");
		}
		if (cameraOptions.containsKeyAndNotNull(TiC.PROPERTY_VIDEO_MAX_DURATION)) {
			videoMaximumDuration = cameraOptions.getInt(TiC.PROPERTY_VIDEO_MAX_DURATION) / 1000;
		}
		if (cameraOptions.containsKeyAndNotNull(TiC.PROPERTY_WHICH_CAMERA)) {
			cameraType = cameraOptions.getInt(TiC.PROPERTY_WHICH_CAMERA);
		}
		if (cameraOptions.containsKeyAndNotNull(TiC.PROPERTY_VIDEO_QUALITY)) {
			videoQuality = cameraOptions.getInt(TiC.PROPERTY_VIDEO_QUALITY);
		}
		if (cameraOptions.containsKeyAndNotNull(TiC.PROPERTY_MEDIA_TYPES)) {
			mediaTypes = cameraOptions.getStringArray(TiC.PROPERTY_MEDIA_TYPES);
			if (Arrays.asList(mediaTypes).contains(MEDIA_TYPE_VIDEO)) {
				mediaType = MEDIA_TYPE_VIDEO;
				intentType = MediaStore.ACTION_VIDEO_CAPTURE;
				extension = ".mp4";
			} else {
				mediaType = MEDIA_TYPE_PHOTO;
				intentType = MediaStore.ACTION_IMAGE_CAPTURE;
				extension = ".jpg";
			}
		}

		//Create an output file irrespective of whether saveToGallery
		//is true or false. If false, we'll delete it later
		File imageFile = null;

		if (saveToPhotoGallery) {
			imageFile = MediaModule.createGalleryImageFile(extension);
		} else {
			imageFile = MediaModule.createExternalStorageFile(extension, Environment.DIRECTORY_PICTURES, false);
		}

		//Sanity Checks
		if (imageFile == null) {
			if (errorCallback != null) {
				KrollDict response = new KrollDict();
				response.putCodeAndMessage(NO_CAMERA, "Unable to create file for storage");
				errorCallback.callAsync(getKrollObject(), response);
			}
			return;
		}

		if (getIsCameraSupported() == false) {
			if (errorCallback != null) {
				KrollDict response = new KrollDict();
				response.putCodeAndMessage(UNKNOWN_ERROR, "Camera Not Supported");
				errorCallback.callAsync(getKrollObject(), response);
			}
			Log.e(TAG, "Camera not supported");
			imageFile.delete();
			return;
		}

		//Create Intent
		Uri fileUri = TiFileProvider.createUriFrom(imageFile);
		Intent intent = new Intent(intentType);
		intent.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
		if (Build.VERSION.SDK_INT >= 16) {
			ApiLevel16.setIntentClipData(intent, android.content.ClipData.newRawUri("", fileUri));
		}
		intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
		intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, videoQuality);
		intent.putExtra("android.intent.extras.CAMERA_FACING", cameraType);

		if (videoMaximumDuration > 0) {
			intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, videoMaximumDuration);
		}

		//Setup CameraResultHandler
		Activity activity = TiApplication.getInstance().getCurrentActivity();
		TiActivitySupport activitySupport = (TiActivitySupport) activity;

		CameraResultHandler resultHandler = new CameraResultHandler();
		resultHandler.imageFile = imageFile;
		resultHandler.successCallback = successCallback;
		resultHandler.errorCallback = errorCallback;
		resultHandler.cancelCallback = cancelCallback;
		resultHandler.cameraIntent = intent;
		resultHandler.saveToPhotoGallery = saveToPhotoGallery;
		resultHandler.activitySupport = activitySupport;
		resultHandler.lastImageId = getLastImageId(activity);
		resultHandler.intentType = intentType;
		activity.runOnUiThread(resultHandler);
	}

	private void launchCameraActivity(KrollDict cameraOptions, TiViewProxy overLayProxy)
	{
		KrollFunction successCallback = null;
		KrollFunction cancelCallback = null;
		KrollFunction errorCallback = null;
		boolean saveToPhotoGallery = false;
		boolean autohide = true;
		int videoMaximumDuration = 0;
		int videoQuality = QUALITY_HIGH;
		int cameraType = 0;
		String[] mediaTypes = null;
		int flashMode = CAMERA_FLASH_OFF;
		int whichCamera = CAMERA_REAR;

		if (cameraOptions.containsKeyAndNotNull(TiC.PROPERTY_SUCCESS)) {
			successCallback = (KrollFunction) cameraOptions.get(TiC.PROPERTY_SUCCESS);
		}
		if (cameraOptions.containsKeyAndNotNull(TiC.PROPERTY_CANCEL)) {
			cancelCallback = (KrollFunction) cameraOptions.get(TiC.PROPERTY_CANCEL);
		}
		if (cameraOptions.containsKeyAndNotNull(TiC.EVENT_ERROR)) {
			errorCallback = (KrollFunction) cameraOptions.get(TiC.EVENT_ERROR);
		}
		if (cameraOptions.containsKeyAndNotNull(PROP_AUTOSAVE)) {
			saveToPhotoGallery = cameraOptions.getBoolean(PROP_AUTOSAVE);
		}
		if (cameraOptions.containsKeyAndNotNull(PROP_AUTOHIDE)) {
			autohide = cameraOptions.getBoolean(PROP_AUTOHIDE);
		}
		if (cameraOptions.containsKeyAndNotNull(TiC.PROPERTY_CAMERA_FLASH_MODE)) {
			flashMode = cameraOptions.getInt(TiC.PROPERTY_CAMERA_FLASH_MODE);
		}
		if (cameraOptions.containsKeyAndNotNull(TiC.PROPERTY_WHICH_CAMERA)) {
			whichCamera = cameraOptions.getInt(TiC.PROPERTY_WHICH_CAMERA);
		}

		// VIDEO
		if (cameraOptions.containsKeyAndNotNull(TiC.PROPERTY_VIDEO_MAX_DURATION)) {
			videoMaximumDuration = cameraOptions.getInt(TiC.PROPERTY_VIDEO_MAX_DURATION);
		}
		if (cameraOptions.containsKeyAndNotNull(TiC.PROPERTY_WHICH_CAMERA)) {
			cameraType = cameraOptions.getInt(TiC.PROPERTY_WHICH_CAMERA);
		}
		if (cameraOptions.containsKeyAndNotNull(TiC.PROPERTY_VIDEO_QUALITY)) {
			videoQuality = cameraOptions.getInt(TiC.PROPERTY_VIDEO_QUALITY);
		}
		if (cameraOptions.containsKeyAndNotNull(TiC.PROPERTY_MEDIA_TYPES)) {
			mediaTypes = cameraOptions.getStringArray(TiC.PROPERTY_MEDIA_TYPES);
			if (Arrays.asList(mediaTypes).contains(MEDIA_TYPE_VIDEO)) {
				mediaType = MEDIA_TYPE_VIDEO;
				extension = ".mp4";
			} else {
				mediaType = MEDIA_TYPE_PHOTO;
				extension = ".jpg";
			}
		}

		TiCameraActivity.callbackContext = getKrollObject();
		TiCameraActivity.mediaContext = this;
		TiCameraActivity.successCallback = successCallback;
		TiCameraActivity.cancelCallback = cancelCallback;
		TiCameraActivity.errorCallback = errorCallback;
		TiCameraActivity.saveToPhotoGallery = saveToPhotoGallery;
		TiCameraActivity.autohide = autohide;
		TiCameraActivity.overlayProxy = overLayProxy;
		TiCameraActivity.whichCamera = whichCamera;
		TiCameraActivity.videoQuality = videoQuality;
		TiCameraActivity.videoMaximumDuration = videoMaximumDuration;
		TiCameraActivity.mediaType = mediaType;
		TiCameraActivity.setFlashMode(flashMode);

		//Create Intent and Launch
		Activity activity = TiApplication.getInstance().getCurrentActivity();
		Intent intent = new Intent(activity, TiCameraActivity.class);
		activity.startActivity(intent);
	}

	@Kroll.method
	public boolean hasCameraPermissions()
	{
		if (Build.VERSION.SDK_INT < 23) {
			return true;
		}
		Context context = TiApplication.getInstance().getApplicationContext();
		if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
			&& context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
				   == PackageManager.PERMISSION_GRANTED
			&& context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
				   == PackageManager.PERMISSION_GRANTED) {
			return true;
		}
		return false;
	}

	@Kroll.method
	public boolean hasAudioRecorderPermissions()
	{
		if (Build.VERSION.SDK_INT < 23) {
			return true;
		}
		Context context = TiApplication.getInstance().getApplicationContext();
		if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
			return true;
		}
		return false;
	}

	private boolean hasCameraPermission()
	{
		if (Build.VERSION.SDK_INT < 23) {
			return true;
		}
		Context context = TiApplication.getInstance().getApplicationContext();
		if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
			return true;
		}
		return false;
	}

	private boolean hasStoragePermission()
	{
		if (Build.VERSION.SDK_INT < 23) {
			return true;
		}
		Context context = TiApplication.getInstance().getApplicationContext();

		return (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
					== PackageManager.PERMISSION_GRANTED
				&& context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
					   == PackageManager.PERMISSION_GRANTED);
	}

	@SuppressWarnings("unchecked")
	@Kroll.method
	public void showCamera(@SuppressWarnings("rawtypes") HashMap options)
	{
		if (!hasCameraPermissions()) {
			return;
		}
		KrollDict cameraOptions = null;
		if ((options == null) || !(options instanceof HashMap<?, ?>) ) {
			if (Log.isDebugModeEnabled()) {
				Log.d(TAG, "showCamera called with invalid options", Log.DEBUG_MODE);
			}
			return;
		} else {
			cameraOptions = new KrollDict(options);
		}

		Object overlay = cameraOptions.get(PROP_OVERLAY);

		if ((overlay != null) && (overlay instanceof TiViewProxy)) {
			launchCameraActivity(cameraOptions, (TiViewProxy) overlay);
		} else {
			launchNativeCamera(cameraOptions);
		}
	}

	@Kroll.method
	public void requestCameraPermissions(@Kroll.argument(optional = true) KrollFunction permissionCallback,
										 KrollPromise promise)
	{
		if (hasCameraPermissions()) {
			return;
		}

		String[] permissions = null;
		if (!hasCameraPermission() && !hasStoragePermission()) {
			permissions = new String[] { Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE,
										 Manifest.permission.WRITE_EXTERNAL_STORAGE };
		} else if (!hasCameraPermission()) {
			permissions = new String[] { Manifest.permission.CAMERA };
		} else {
			permissions =
				new String[] { Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE };
		}

		TiBaseActivity.registerPermissionRequestCallback(TiC.PERMISSION_CODE_CAMERA, permissionCallback,
														 getKrollObject(), promise);
		Activity currentActivity = TiApplication.getInstance().getCurrentActivity();
		currentActivity.requestPermissions(permissions, TiC.PERMISSION_CODE_CAMERA);
	}

	@Kroll.method
	public void requestAudioRecorderPermissions(@Kroll.argument(optional = true) KrollFunction permissionCallback,
												KrollPromise promise)
	{
		if (hasAudioRecorderPermissions()) {
			return;
		}
		String[] permissions = new String[] { Manifest.permission.RECORD_AUDIO };
		TiBaseActivity.registerPermissionRequestCallback(TiC.PERMISSION_CODE_MICROPHONE, permissionCallback,
														 getKrollObject(), promise);
		Activity currentActivity = TiApplication.getInstance().getCurrentActivity();
		currentActivity.requestPermissions(permissions, TiC.PERMISSION_CODE_MICROPHONE);
	}

	/*
	 * Current implementation on Android limited to saving Images only to photo gallery
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Kroll.method
	public void saveToPhotoGallery(Object arg, @Kroll.argument(optional = true) HashMap callbackargs)
	{
		KrollFunction successCallback = null;
		KrollFunction errorCallback = null;

		KrollDict callbackDict = null;

		//Check for callbacks
		if (callbackargs != null) {
			callbackDict = new KrollDict(callbackargs);
			if (callbackDict.containsKeyAndNotNull(TiC.PROPERTY_SUCCESS)) {
				successCallback = (KrollFunction) callbackDict.get(TiC.PROPERTY_SUCCESS);
			}
			if (callbackDict.containsKeyAndNotNull(TiC.EVENT_ERROR)) {
				errorCallback = (KrollFunction) callbackDict.get(TiC.EVENT_ERROR);
			}
		}

		//Validate arguments
		boolean validType = ((arg instanceof TiBlob) || (arg instanceof TiFileProxy));
		if (!validType) {
			if (errorCallback != null) {
				KrollDict response = new KrollDict();
				response.putCodeAndMessage(UNKNOWN_ERROR, "Invalid type passed as argument");
				errorCallback.callAsync(getKrollObject(), response);
			}
			return;
		}

		TiBlob blob = null;
		try {
			//Make sure our processing argument is a Blob
			if (arg instanceof TiFileProxy) {
				blob = TiBlob.blobFromFile(((TiFileProxy) arg).getBaseFile());
			} else {
				blob = (TiBlob) arg;
			}

			boolean isVideo = blob.getMimeType().startsWith("video");
			if (((blob.getWidth() == 0) || (blob.getHeight() == 0)) && !isVideo) {
				if (errorCallback != null) {
					KrollDict response = new KrollDict();
					response.putCodeAndMessage(UNKNOWN_ERROR, "Could not decode bitmap from argument");
					errorCallback.callAsync(getKrollObject(), response);
				}
				return;
			}

			if (blob.getType() == TiBlob.TYPE_IMAGE) {
				Bitmap image = blob.getImage();
				if (image.hasAlpha()) {
					extension = ".png";
				} else {
					extension = ".jpg";
				}
			} else {
				try {
					String mimetype = blob.getMimeType();
					extension =
						'.' + TiMimeTypeHelper.getFileExtensionFromMimeType(mimetype, isVideo ? ".mp4" : ".jpg");
				} catch (Throwable t) {
					extension = null;
				}
			}

			File file = isVideo ? MediaModule.createExternalStorageFile(extension, Environment.DIRECTORY_MOVIES, true)
								: MediaModule.createGalleryImageFile(extension);
			BufferedInputStream inputStream = new BufferedInputStream(blob.getInputStream());
			BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file));
			byte[] buffer = new byte[1024 * 1024 * 8];
			int len = -1;
			while ((len = inputStream.read(buffer)) > 0) {
				outputStream.write(buffer, 0, len);
			}
			if (outputStream != null) {
				outputStream.close();
				outputStream = null;
			}
			if (inputStream != null) {
				inputStream.close();
				inputStream = null;
			}

			Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
			Uri contentUri = Uri.fromFile(file);
			mediaScanIntent.setData(contentUri);
			Activity activity = TiApplication.getInstance().getCurrentActivity();
			activity.sendBroadcast(mediaScanIntent);

			//All good. Dispatch success callback
			if (successCallback != null) {
				KrollDict response = new KrollDict();
				response.putCodeAndMessage(NO_ERROR, null);
				successCallback.callAsync(getKrollObject(), response);
			}

		} catch (Throwable t) {
			if (errorCallback != null) {
				KrollDict response = new KrollDict();
				response.putCodeAndMessage(UNKNOWN_ERROR, t.getMessage());
				errorCallback.callAsync(getKrollObject(), response);
			}
		}
	}

	@Kroll.method
	public void hideCamera()
	{
		// make sure the preview / camera are open before trying to hide
		if (TiCameraActivity.cameraActivity != null) {
			TiCameraActivity.hide();
		} else {
			Log.e(TAG, "Camera preview is not open, unable to hide");
		}
	}

	/**
	 * @see org.appcelerator.kroll.KrollProxy#handleMessage(android.os.Message)
	 */
	@Override
	public boolean handleMessage(Message message)
	{
		return super.handleMessage(message);
	}

	protected static File createExternalStorageFile()
	{
		return createExternalStorageFile(null, Environment.DIRECTORY_PICTURES, false);
	}

	protected static File createGalleryImageFile()
	{
		return createGalleryImageFile(extension);
	}

	private static File createExternalStorageFile(String extension, String type, boolean isPublic)
	{
		File dir = isPublic ? Environment.getExternalStoragePublicDirectory(type)
							: TiApplication.getInstance().getExternalFilesDir(type);
		File appDir = new File(dir, TiApplication.getInstance().getAppInfo().getName());
		if (!appDir.exists()) {
			if (!appDir.mkdirs()) {
				Log.e(TAG, "Failed to create external storage directory.");
				return null;
			}
		}
		File file;
		String ext = extension == null ? ".jpg" : extension;
		try {
			file = TiFileHelper.getInstance().getTempFile(appDir, ext, !isPublic);
		} catch (IOException e) {
			Log.e(TAG, "Failed to create file: " + e.getMessage());
			return null;
		}
		return file;
	}

	private static File createGalleryImageFile(String extension)
	{
		return createExternalStorageFile(extension, Environment.DIRECTORY_PICTURES, true);
	}

	protected class CameraResultHandler implements TiActivityResultHandler, Runnable
	{
		protected File imageFile;
		protected boolean saveToPhotoGallery;
		protected int code;
		protected KrollFunction successCallback, cancelCallback, errorCallback;
		protected TiActivitySupport activitySupport;
		protected Intent cameraIntent;
		protected int lastImageId;
		private boolean validFileCreated;
		protected String intentType;

		//Validates if the file is a valid bitmap
		private void validateFile() throws Throwable
		{
			try {
				if (MediaStore.ACTION_VIDEO_CAPTURE.equals(intentType)) {
					// video
				} else {
					// bitmap
					BitmapFactory.Options opts = new BitmapFactory.Options();
					opts.inJustDecodeBounds = true;

					BitmapFactory.decodeStream(new FileInputStream(imageFile), null, opts);
					if (opts.outWidth == -1 || opts.outHeight == -1) {
						throw new Exception("Could not decode the bitmap from imageFile");
					}
				}
			} catch (Throwable t) {
				Log.e(TAG, t.getMessage());
				throw t;
			}
		}

		//Cleanup duplicates if possible.
		private void checkAndDeleteDuplicate(Activity activity)
		{
			if (lastImageId != -1) {
				final String[] imageColumns = { MediaStore.Images.Media.DATA, MediaStore.Images.Media._ID };
				final String imageOrderBy = MediaStore.Images.Media._ID + " DESC";
				final String imageWhere = MediaStore.Images.Media._ID + ">?";
				final String[] imageArguments = { Integer.toString(lastImageId) };
				Cursor imageCursor =
					activity.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageColumns,
														imageWhere, imageArguments, imageOrderBy);
				String refPath = imageFile.getAbsolutePath();
				if (imageCursor == null) {
					Log.e(TAG, "Could not load image cursor. Can not check and delete duplicates");
					return;
				}
				if (imageCursor.getCount() > 0) {

					if (!validFileCreated) {
						try {
							imageFile.delete();
						} catch (Throwable t) {
							//Ignore error
						}
						imageFile = saveToPhotoGallery ? MediaModule.createGalleryImageFile(extension)
													   : MediaModule.createExternalStorageFile(
															 extension, Environment.DIRECTORY_PICTURES, false);
					}
					long compareLength = (validFileCreated) ? imageFile.length() : 0;

					while (imageCursor.moveToNext()) {
						int id = imageCursor.getInt(imageCursor.getColumnIndex(MediaStore.Images.Media._ID));
						String path = imageCursor.getString(imageCursor.getColumnIndex(MediaStore.Images.Media.DATA));

						if (!validFileCreated) {
							//If file is invalid we will copy over the last image in the gallery
							if (imageFile != null) {
								try {
									File srcFile = new File(path);
									copyFile(srcFile, imageFile);
									validFileCreated = true;
									refPath = imageFile.getAbsolutePath();
									compareLength = imageFile.length();
								} catch (Throwable t) {
									//Ignore this error. It will be caught on the next pass to validateFile.
								}
							}
						}

						if (!path.equalsIgnoreCase(refPath)) {
							File compareFile = new File(path);
							long fileLength = compareFile.length();

							if (compareFile.length() == compareLength) {
								//Same file length
								int result = activity.getContentResolver().delete(
									MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Images.Media._ID + "=?",
									new String[] { Integer.toString(id) });
								if (result == 1) {
									if (Log.isDebugModeEnabled()) {
										Log.d(TAG, "Deleting possible duplicate at " + path + " with id " + id,
											  Log.DEBUG_MODE);
									}
								} else {
									if (Log.isDebugModeEnabled()) {
										Log.d(TAG, "Could not delete possible duplicate at " + path + " with id " + id,
											  Log.DEBUG_MODE);
									}
								}
							} else {
								if (Log.isDebugModeEnabled()) {
									Log.d(TAG,
										  "Ignoring file as not a duplicate at path " + path + " with id " + id
											  + ". Different Sizes " + fileLength + " " + compareLength,
										  Log.DEBUG_MODE);
								}
							}
						}
					}
				}
				imageCursor.close();
			}
		}

		//Copies files over using Buffered Streams.
		private void copyFile(File source, File destination) throws Throwable
		{
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(source));
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(destination));
			byte[] buf = new byte[8096];
			int len = 0;

			while ((len = bis.read(buf)) != -1) {
				bos.write(buf, 0, len);
			}

			if (bis != null) {
				bis.close();
				bis = null;
			}
			if (bos != null) {
				bos.close();
				bos = null;
			}
		}

		@Override
		public void run()
		{
			code = activitySupport.getUniqueResultCode();
			activitySupport.launchActivityForResult(cameraIntent, code, this);
		}

		@Override
		public void onResult(Activity activity, int requestCode, int resultCode, Intent data)
		{
			if (requestCode == code) {
				if (resultCode == Activity.RESULT_OK) {

					validFileCreated = true;
					try {
						validateFile();
					} catch (Throwable t) {
						validFileCreated = false;
					}

					checkAndDeleteDuplicate(activity);

					try {
						validateFile();
					} catch (Throwable t) {
						if (errorCallback != null) {
							KrollDict response = new KrollDict();
							response.putCodeAndMessage(UNKNOWN_ERROR, t.getMessage());
							errorCallback.callAsync(getKrollObject(), response);
						}
						return;
					}

					if (!saveToPhotoGallery) {
						//Create a file in the internal data directory and delete the original file
						try {
							File dataFile = tempFileHelper.createTempFile("tia", extension);
							copyFile(imageFile, dataFile);
							imageFile.delete();
							imageFile = dataFile;
							TiFileHelper.getInstance().destroyOnExit(imageFile);
						} catch (Throwable t) {
							if (errorCallback != null) {
								KrollDict response = new KrollDict();
								response.putCodeAndMessage(UNKNOWN_ERROR, t.getMessage());
								errorCallback.callAsync(getKrollObject(), response);
							}
							return;
						}
					} else {
						//Send out broadcast to add to image gallery
						Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
						Uri contentUri = Uri.fromFile(imageFile);
						mediaScanIntent.setData(contentUri);
						activity.sendBroadcast(mediaScanIntent);
					}

					//Create a blob for response
					try {
						TiFile theFile = new TiFile(imageFile, imageFile.toURI().toURL().toExternalForm(), false);
						TiBlob theBlob = TiBlob.blobFromFile(theFile);
						if (!saveToPhotoGallery) {
							TiFileHelper.getInstance().destroyOnExit(imageFile);
						}
						KrollDict response = MediaModule.createDictForImage(theBlob, theBlob.getMimeType());
						if (successCallback != null) {
							successCallback.callAsync(getKrollObject(), response);
						}
					} catch (Throwable t) {
						if (errorCallback != null) {
							KrollDict response = new KrollDict();
							response.putCodeAndMessage(UNKNOWN_ERROR, t.getMessage());
							errorCallback.callAsync(getKrollObject(), response);
						}
						return;
					}

				} else {
					//Delete the file
					if (imageFile != null) {
						imageFile.delete();
					}
					if (resultCode == Activity.RESULT_CANCELED) {
						if (cancelCallback != null) {
							KrollDict response = new KrollDict();
							response.putCodeAndMessage(NO_ERROR, null);
							cancelCallback.callAsync(getKrollObject(), response);
						}
					} else {
						//Assume error
						if (errorCallback != null) {
							KrollDict response = new KrollDict();
							response.putCodeAndMessage(UNKNOWN_ERROR, null);
							errorCallback.callAsync(getKrollObject(), response);
						}
					}
				}
			}
		}

		@Override
		public void onError(Activity activity, int requestCode, Exception e)
		{
			if (requestCode != code) {
				return;
			}
			if (imageFile != null) {
				imageFile.delete();
			}
			String msg = "Camera problem: " + e.getMessage();
			Log.e(TAG, msg, e);
			if (errorCallback != null) {
				errorCallback.callAsync(getKrollObject(), createErrorResponse(UNKNOWN_ERROR, msg));
			}
		}
	}

	// clang-format off
	@Kroll.method
	@Kroll.setProperty
	public void setCameraFlashMode(int flashMode)
	// clang-format on
	{
		TiCameraActivity.setFlashMode(flashMode);
	}

	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public int getCameraFlashMode()
	// clang-format on
	{
		return TiCameraActivity.cameraFlashMode;
	}

	@Kroll.method
	public void openPhotoGallery(KrollDict options)
	{
		KrollFunction successCallback = null;
		KrollFunction cancelCallback = null;
		KrollFunction errorCallback = null;

		if (options.containsKey(TiC.PROPERTY_SUCCESS)) {
			successCallback = (KrollFunction) options.get(TiC.PROPERTY_SUCCESS);
		}
		if (options.containsKey(TiC.PROPERTY_CANCEL)) {
			cancelCallback = (KrollFunction) options.get(TiC.PROPERTY_CANCEL);
		}
		if (options.containsKey(TiC.EVENT_ERROR)) {
			errorCallback = (KrollFunction) options.get(TiC.EVENT_ERROR);
		}

		final KrollFunction fSuccessCallback = successCallback;
		final KrollFunction fCancelCallback = cancelCallback;
		final KrollFunction fErrorCallback = errorCallback;

		Log.d(TAG, "openPhotoGallery called", Log.DEBUG_MODE);

		Activity activity = TiApplication.getInstance().getCurrentActivity();
		TiActivitySupport activitySupport = (TiActivitySupport) activity;

		TiIntentWrapper galleryIntent = new TiIntentWrapper(new Intent());
		galleryIntent.getIntent().setAction(Intent.ACTION_GET_CONTENT);

		boolean isSelectingPhoto = false;
		boolean isSelectingVideo = false;
		if (options.containsKey(TiC.PROPERTY_MEDIA_TYPES)) {
			Object value = options.get(TiC.PROPERTY_MEDIA_TYPES);
			if (value instanceof String) {
				value = new String[] { (String) value };
			}
			if (value instanceof Object[]) {
				for (Object nextType : (Object[]) value) {
					String stringType = (nextType instanceof String) ? (String) nextType : "";
					switch (stringType) {
						case MEDIA_TYPE_PHOTO:
							isSelectingPhoto = true;
							break;
						case MEDIA_TYPE_VIDEO:
							isSelectingVideo = true;
							break;
					}
				}
			}
		} else {
			isSelectingPhoto = true;
		}
		if (isSelectingPhoto && isSelectingVideo) {
			galleryIntent.getIntent().setType("*/*");
			if (Build.VERSION.SDK_INT >= 19) {
				galleryIntent.getIntent().putExtra(Intent.EXTRA_MIME_TYPES, new String[] { "image/*", "video/*" });
			}
			MediaModule.mediaType = MEDIA_TYPE_PHOTO;
		} else if (isSelectingVideo) {
			galleryIntent.getIntent().setType("video/*");
			MediaModule.mediaType = MEDIA_TYPE_VIDEO;
		} else {
			galleryIntent.getIntent().setType("image/*");
			MediaModule.mediaType = MEDIA_TYPE_PHOTO;
		}

		galleryIntent.getIntent().addCategory(Intent.CATEGORY_DEFAULT);
		galleryIntent.setWindowId(TiIntentWrapper.createActivityName("GALLERY"));

		final int PICK_IMAGE_SINGLE = activitySupport.getUniqueResultCode();
		final int PICK_IMAGE_MULTIPLE = activitySupport.getUniqueResultCode();
		boolean allowMultiple = false;

		if (options.containsKey(TiC.PROPERTY_ALLOW_MULTIPLE) && Build.VERSION.SDK_INT >= 18) {
			allowMultiple = TiConvert.toBoolean(options.get(TiC.PROPERTY_ALLOW_MULTIPLE));
			galleryIntent.getIntent().putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple);
		}

		final int code = allowMultiple ? PICK_IMAGE_MULTIPLE : PICK_IMAGE_SINGLE;

		activitySupport.launchActivityForResult(galleryIntent.getIntent(), code, new TiActivityResultHandler() {
			@Override
			public void onResult(Activity activity, int requestCode, int resultCode, Intent data)
			{
				if (requestCode != code) {
					return;
				}
				Log.d(TAG, "OnResult called: " + resultCode, Log.DEBUG_MODE);
				String path = null;
				if (data != null) {
					path = data.getDataString();
				}
				//Starting with Android-L, backing out of the gallery no longer returns cancel code, but with
				//an ok code and a null data.
				if (resultCode == Activity.RESULT_CANCELED || (Build.VERSION.SDK_INT >= 20 && data == null)) {
					if (fCancelCallback != null) {
						KrollDict response = new KrollDict();
						response.putCodeAndMessage(NO_ERROR, null);
						fCancelCallback.callAsync(getKrollObject(), response);
					}

				} else {
					// Handle multiple file selection, if enabled.
					if (requestCode == PICK_IMAGE_MULTIPLE && Build.VERSION.SDK_INT >= 18) {
						// Wrap all selected file(s) in Titanium "CameraMediaItemType" dictionaries.
						ArrayList<KrollDict> selectedFiles = new ArrayList<>();
						ClipData clipData = data.getClipData();
						if (clipData != null) {
							// Fetch file(s) from clip data.
							int count = clipData.getItemCount();
							for (int index = 0; index < count; index++) {
								ClipData.Item item = clipData.getItemAt(index);
								if ((item == null) || (item.getUri() == null)) {
									continue;
								}
								KrollDict dictionary = createDictForImage(item.getUri().toString());
								if (dictionary == null) {
									continue;
								}
								selectedFiles.add(dictionary);
							}
						} else if (path != null) {
							// Only a single file was found.
							KrollDict dictionary = createDictForImage(path);
							if (dictionary != null) {
								selectedFiles.add(dictionary);
							}
						}

						// Copy each selected file to either an "images" or "videos" collection.
						ArrayList<KrollDict> selectedImages = new ArrayList<>();
						ArrayList<KrollDict> selectedVideos = new ArrayList<>();
						for (KrollDict dictionary : selectedFiles) {
							String mediaType = dictionary.getString("mediaType");
							if (mediaType != null) {
								if (mediaType.equals(MEDIA_TYPE_PHOTO)) {
									selectedImages.add(dictionary);
								} else if (mediaType.equals(MEDIA_TYPE_VIDEO)) {
									selectedVideos.add(dictionary);
								}
							}
						}

						// Invoke a callback with the selection result.
						if (selectedImages.isEmpty() && selectedVideos.isEmpty()) {
							if (selectedFiles.isEmpty()) {
								// Invoke the "cancel" callback if no files were selected.
								if (fCancelCallback != null) {
									KrollDict response = new KrollDict();
									response.putCodeAndMessage(NO_ERROR, null);
									fCancelCallback.callAsync(getKrollObject(), response);
								}
							} else {
								// Invoke the "error" callback if non-image/video files were selected.
								String message = "Invalid file types were selected";
								Log.e(TAG, message);
								if (fErrorCallback != null) {
									fErrorCallback.callAsync(getKrollObject(),
															 createErrorResponse(UNKNOWN_ERROR, message));
								}
							}
						} else {
							// Invoke the "success" callback with the selected file(s).
							if (fSuccessCallback != null) {
								KrollDict d = new KrollDict();
								d.putCodeAndMessage(NO_ERROR, null);
								d.put("images", selectedImages.toArray(new KrollDict[0]));
								d.put("videos", selectedVideos.toArray(new KrollDict[0]));
								fSuccessCallback.callAsync(getKrollObject(), d);
							}
						}
						return;
					}

					// Handle single file selection.
					try {
						//Check for invalid path
						if (path == null) {
							String msg = "File path is invalid";
							Log.e(TAG, msg);
							if (fErrorCallback != null) {
								fErrorCallback.callAsync(getKrollObject(), createErrorResponse(UNKNOWN_ERROR, msg));
							}
							return;
						}
						if (fSuccessCallback != null) {
							fSuccessCallback.callAsync(getKrollObject(), createDictForImage(path));
						}

					} catch (OutOfMemoryError e) {
						String msg = "Not enough memory to get image: " + e.getMessage();
						Log.e(TAG, msg);
						if (fErrorCallback != null) {
							fErrorCallback.callAsync(getKrollObject(), createErrorResponse(UNKNOWN_ERROR, msg));
						}
					}
				}
			}

			@Override
			public void onError(Activity activity, int requestCode, Exception e)
			{
				if (requestCode != code) {
					return;
				}
				String msg = "Gallery problem: " + e.getMessage();
				Log.e(TAG, msg, e);
				if (fErrorCallback != null) {
					fErrorCallback.callAsync(getKrollObject(), createErrorResponse(UNKNOWN_ERROR, msg));
				}
			}
		});
	}

	protected static KrollDict createDictForImage(String path)
	{
		// Validate argument.
		if ((path == null) || path.isEmpty()) {
			return null;
		}

		// Determine the mime type for the given file.
		String mimeType = null;
		boolean isPhoto = false;
		try {
			mimeType = TiApplication.getInstance().getContentResolver().getType(Uri.parse(path));
			if ((mimeType != null) && mimeType.toLowerCase().startsWith("image")) {
				isPhoto = true;
			}
		} catch (Exception ex) {
		}

		// Wrap the given file in a blob.
		String[] parts = { path };
		TiBlob imageData = null;
		if (isPhoto && path.startsWith("content://com.google.android.apps.photos.contentprovider")) {
			// This is an image file from the Google Photos cloud.
			// We don't have direct file access to it. So, load it into memory.
			ParcelFileDescriptor parcelFileDescriptor;
			Bitmap image;
			try {
				parcelFileDescriptor =
					TiApplication.getInstance().getContentResolver().openFileDescriptor(Uri.parse(path), "r");
				FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
				image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
				parcelFileDescriptor.close();
				if (image != null) {
					imageData = TiBlob.blobFromImage(image);
				}
			} catch (Exception ex) {
			}
		}
		if (imageData == null) {
			// Have the blob wrap the file path or URL.
			imageData = createImageData(parts, mimeType);
		}

		// Return a Titanium "CameraMediaItemType" dictionary which wraps the given file.
		return createDictForImage(imageData, mimeType);
	}

	public static TiBlob createImageData(String[] parts, String mimeType)
	{
		return TiBlob.blobFromFile(TiFileFactory.createTitaniumFile(parts, false), mimeType);
	}

	protected static KrollDict createDictForImage(TiBlob imageData, String mimeType)
	{
		// Create the dictionary.
		KrollDict d = new KrollDict();
		d.putCodeAndMessage(NO_ERROR, null);

		// Determine and set the media type based on the given mime type.
		boolean isPhoto = false;
		boolean isVideo = false;
		String actualMediaType = MediaModule.mediaType;
		if (mimeType != null) {
			String lowerCaseMimeType = mimeType.toLowerCase();
			if (lowerCaseMimeType.startsWith("image")) {
				actualMediaType = MEDIA_TYPE_PHOTO;
				isPhoto = true;
			} else if (lowerCaseMimeType.startsWith("video")) {
				actualMediaType = MEDIA_TYPE_VIDEO;
				isVideo = true;
			}
		}
		if (actualMediaType == null) {
			actualMediaType = MEDIA_TYPE_PHOTO;
		}
		d.put("mediaType", actualMediaType);

		// Add the image/video's width and height to the dictionary.
		int width = -1;
		int height = -1;
		if (imageData != null) {
			if (isPhoto) {
				// Fetch the image file's pixel width and height.
				BitmapFactory.Options opts = new BitmapFactory.Options();
				opts.inJustDecodeBounds = true;
				opts.outWidth = width;
				opts.outHeight = height;
				try (InputStream inputStream = imageData.getInputStream()) {
					BitmapFactory.decodeStream(inputStream, null, opts);
					width = opts.outWidth;
					height = opts.outHeight;
				} catch (Exception ex) {
					Log.e(TAG, "Failed to acquire dimensions for image.", ex);
				}
			} else if (isVideo) {
				// Fetch the video file's pixel width and height.
				String path = null;
				TiFileProxy fileProxy = imageData.getFile();
				if (fileProxy != null) {
					path = fileProxy.getNativePath();
				}
				if (path != null) {
					MediaMetadataRetriever retriever = new MediaMetadataRetriever();
					try {
						if (path.startsWith("content:")) {
							retriever.setDataSource(TiApplication.getInstance(), Uri.parse(path));
						} else {
							retriever.setDataSource(path);
						}
						width = TiConvert.toInt(
							retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH), width);
						height = TiConvert.toInt(
							retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT), height);
					} catch (Exception ex) {
						Log.e(TAG, "Failed to acquire dimensions for video: " + path, ex);
					} finally {
						try {
							retriever.release();
						} catch (Exception ex) {
						}
					}
				}
			}
		}
		d.put("x", 0);
		d.put("y", 0);
		d.put("width", width);
		d.put("height", height);

		// Add the image/video's crop dimensiosn to the dictionary.
		KrollDict cropRect = new KrollDict();
		cropRect.put("x", 0);
		cropRect.put("y", 0);
		cropRect.put("width", width);
		cropRect.put("height", height);
		d.put("cropRect", cropRect);

		// Add the blob to the dictionary.
		d.put("media", imageData);
		return d;
	}

	KrollDict createDictForImage(int width, int height, byte[] data)
	{
		KrollDict d = new KrollDict();

		d.put("x", 0);
		d.put("y", 0);
		d.put("width", width);
		d.put("height", height);

		KrollDict cropRect = new KrollDict();
		cropRect.put("x", 0);
		cropRect.put("y", 0);
		cropRect.put("width", width);
		cropRect.put("height", height);
		d.put("cropRect", cropRect);
		d.put("mediaType", mediaType);
		d.put("media", TiBlob.blobFromData(data, MIME_IMAGE));

		return d;
	}

	@Kroll.method
	public void previewImage(KrollDict options)
	{
		Log.d(TAG, "openPhotoGallery called", Log.DEBUG_MODE);

		Activity activity = TiApplication.getAppCurrentActivity();
		if (activity == null) {
			Log.w(TAG, "Unable to get current activity for previewImage.", Log.DEBUG_MODE);
			return;
		}

		KrollFunction successCallback = null;
		if (options.containsKey(TiC.PROPERTY_SUCCESS)) {
			successCallback = (KrollFunction) options.get(TiC.PROPERTY_SUCCESS);
		}
		final KrollFunction fSuccessCallback = successCallback;

		KrollFunction errorCallback = null;
		if (options.containsKey(TiC.EVENT_ERROR)) {
			errorCallback = (KrollFunction) options.get(TiC.EVENT_ERROR);
		}
		final KrollFunction fErrorCallback = errorCallback;

		TiBlob imageBlob = null;
		if (options.containsKey(TiC.PROPERTY_IMAGE)) {
			String errorMessage = null;
			Object value = options.get(TiC.PROPERTY_IMAGE);
			if (value instanceof TiBlob) {
				imageBlob = (TiBlob) value;
			} else if (value != null) {
				errorMessage = "The image property must be of type blob";
			} else {
				errorMessage = "Missing image property";
			}
			if (errorMessage != null) {
				Log.w(TAG, errorMessage);
				if (errorCallback != null) {
					errorCallback.callAsync(getKrollObject(), createErrorResponse(UNKNOWN_ERROR, errorMessage));
				}
				return;
			}
		}

		Uri imageUri = null;
		if (imageBlob.getType() == TiBlob.TYPE_FILE) {
			TiFileProxy fileProxy = imageBlob.getFile();
			if (fileProxy != null) {
				File file = fileProxy.getBaseFile().getNativeFile();
				if (file != null) {
					imageUri = TiFileProvider.createUriFrom(file);
				} else {
					String path = fileProxy.getNativePath();
					if ((path != null) && path.startsWith("content:")) {
						imageUri = Uri.parse(path);
					}
				}
			}
		}
		if (imageUri == null) {
			try (InputStream stream = imageBlob.getInputStream()) {
				imageUri = TiFileProvider.createUriFrom(
					TiFileHelper.getInstance().getTempFileFromInputStream(stream, null, true));
			} catch (Exception ex) {
			}
		}
		if (imageUri == null) {
			String errorMessage = "Failed to create URI from given 'image' blob";
			Log.w(TAG, errorMessage);
			if (errorCallback != null) {
				errorCallback.callAsync(getKrollObject(), createErrorResponse(UNKNOWN_ERROR, errorMessage));
			}
			return;
		}

		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		String mimeType = imageBlob.getMimeType();
		if ((mimeType == null) || mimeType.isEmpty()) {
			intent.setData(imageUri);
		} else {
			intent.setDataAndType(imageUri, mimeType);
		}

		TiIntentWrapper previewIntent = new TiIntentWrapper(intent);
		previewIntent.setWindowId(TiIntentWrapper.createActivityName("PREVIEW"));

		TiActivitySupport activitySupport = (TiActivitySupport) activity;
		final int code = activitySupport.getUniqueResultCode();
		activitySupport.launchActivityForResult(intent, code, new TiActivityResultHandler() {
			public void onResult(Activity activity, int requestCode, int resultCode, Intent data)
			{
				if (requestCode != code) {
					return;
				}
				Log.i(TAG, "OnResult called: " + resultCode);
				if (fSuccessCallback != null) {
					KrollDict response = new KrollDict();
					response.putCodeAndMessage(NO_ERROR, null);
					fSuccessCallback.callAsync(getKrollObject(), response);
				}
			}

			public void onError(Activity activity, int requestCode, Exception e)
			{
				if (requestCode != code) {
					return;
				}
				String msg = "Gallery problem: " + e.getMessage();
				Log.e(TAG, msg, e);
				if (fErrorCallback != null) {
					fErrorCallback.callAsync(getKrollObject(), createErrorResponse(UNKNOWN_ERROR, msg));
				}
			}
		});
	}

	@Kroll.method
	public void takeScreenshot(KrollFunction callback)
	{
		Activity a = TiApplication.getAppCurrentActivity();

		if (a == null) {
			Log.w(TAG, "Could not get current activity for takeScreenshot.", Log.DEBUG_MODE);
			callback.callAsync(getKrollObject(), new Object[] { null });
			return;
		}

		while (a.getParent() != null) {
			a = a.getParent();
		}

		Window w = a.getWindow();

		while (w.getContainer() != null) {
			w = w.getContainer();
		}

		KrollDict image = TiUIHelper.viewToImage(null, w.getDecorView());
		if (callback != null) {
			callback.callAsync(getKrollObject(), new Object[] { image });
		}
	}

	@Kroll.method
	public void takePicture()
	{
		// make sure the preview / camera are open before trying to take photo
		if (TiCameraActivity.cameraActivity != null) {
			TiCameraActivity.takePicture();
		} else {
			Log.e(TAG, "Camera preview is not open, unable to take photo");
		}
	}

	@Kroll.method
	public void startVideoCapture()
	{
		// make sure the preview / camera are open before trying to take photo
		if (TiCameraActivity.cameraActivity != null) {
			TiCameraActivity.startVideoCapture();
		} else {
			Log.e(TAG, "Camera preview is not open, unable to take photo");
		}
	}

	@Kroll.method
	public void stopVideoCapture()
	{
		// make sure the preview / camera are open before trying to take photo
		if (TiCameraActivity.cameraActivity != null) {
			TiCameraActivity.stopVideoCapture();
		} else {
			Log.e(TAG, "Camera preview is not open, unable to take photo");
		}
	}

	@Kroll.method
	public void switchCamera(int whichCamera)
	{
		TiCameraActivity activity = TiCameraActivity.cameraActivity;

		if (activity == null || !activity.isPreviewRunning()) {
			Log.e(TAG, "Camera preview is not open, unable to switch camera.");
			return;
		}

		activity.switchCamera(whichCamera);
	}

	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public boolean getIsCameraSupported()
	// clang-format on
	{
		return Camera.getNumberOfCameras() > 0;
	}

	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public int[] getAvailableCameras()
	// clang-format on
	{
		int cameraCount = Camera.getNumberOfCameras();
		if (cameraCount == 0) {
			return null;
		}

		int[] result = new int[cameraCount];

		CameraInfo cameraInfo = new CameraInfo();

		for (int i = 0; i < cameraCount; i++) {
			Camera.getCameraInfo(i, cameraInfo);
			switch (cameraInfo.facing) {
				case CameraInfo.CAMERA_FACING_FRONT:
					result[i] = CAMERA_FRONT;
					break;
				case CameraInfo.CAMERA_FACING_BACK:
					result[i] = CAMERA_REAR;
					break;
				default:
					// This would be odd. As of API level 17,
					// there are just the two options.
					result[i] = -1;
			}
		}

		return result;
	}

	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public boolean getCanRecord()
	// clang-format on
	{
		return TiApplication.getInstance().getPackageManager().hasSystemFeature("android.hardware.microphone");
	}

	@Override
	public String getApiName()
	{
		return "Ti.Media";
	}
}
