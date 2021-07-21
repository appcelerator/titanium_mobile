/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2020 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.media;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.KrollObject;
import org.appcelerator.kroll.KrollPromise;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.ContextSpecific;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiFileProxy;
import org.appcelerator.titanium.io.TiBaseFile;
import org.appcelerator.titanium.io.TiFileFactory;
import org.appcelerator.titanium.io.TiFileProvider;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiActivityResultHandler;
import org.appcelerator.titanium.util.TiActivitySupport;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiFileHelper;
import org.appcelerator.titanium.util.TiIntentWrapper;
import org.appcelerator.titanium.util.TiUIHelper;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
	public static final int IMAGE_SCALING_AUTO = -1;
	@Kroll.constant
	public static final int IMAGE_SCALING_NONE = 0;
	@Kroll.constant
	public static final int IMAGE_SCALING_FILL = 1;
	@Kroll.constant
	public static final int IMAGE_SCALING_ASPECT_FILL = 2;
	@Kroll.constant
	public static final int IMAGE_SCALING_ASPECT_FIT = 3;

	@Kroll.constant
	public static final int VIDEO_SCALING_NONE = 0;
	@Kroll.constant
	public static final int VIDEO_SCALING_ASPECT_FILL = 1;
	@Kroll.constant
	public static final int VIDEO_SCALING_ASPECT_FIT = 2;
	@Kroll.constant
	public static final int VIDEO_SCALING_MODE_FILL = 3;
	@Kroll.constant
	public static final int VIDEO_SCALING_RESIZE = 4;
	@Kroll.constant
	public static final int VIDEO_SCALING_RESIZE_ASPECT = 5;
	@Kroll.constant
	public static final int VIDEO_SCALING_RESIZE_ASPECT_FILL = 6;

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
	public static final int VIDEO_LOAD_STATE_PLAYABLE = 1;

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
	public static final String MEDIA_TYPE_VIDEO = "public.movie";

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
	private static ContentResolver contentResolver;

	public MediaModule()
	{
		super();

		if (contentResolver == null) {
			contentResolver = TiApplication.getInstance().getContentResolver();
		}
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

	private void launchNativeCamera(KrollDict cameraOptions)
	{
		// Fetch camera settings.
		final KrollFunction successCallback = (KrollFunction) cameraOptions.get(TiC.PROPERTY_SUCCESS);
		final KrollFunction cancelCallback = (KrollFunction) cameraOptions.get(TiC.PROPERTY_CANCEL);
		final KrollFunction errorCallback = (KrollFunction) cameraOptions.get(TiC.EVENT_ERROR);
		final boolean saveToPhotoGallery = TiConvert.toBoolean(cameraOptions.get(PROP_AUTOSAVE), false);
		String[] mediaTypes = null;
		String intentType = MediaStore.ACTION_IMAGE_CAPTURE;
		int videoMaximumDuration = 0;
		int videoQuality = QUALITY_HIGH;
		int cameraType = 0;
		boolean isVideo = false;
		MediaModule.mediaType = MEDIA_TYPE_PHOTO;
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
				isVideo = true;
				intentType = MediaStore.ACTION_VIDEO_CAPTURE;
			} else {
				isVideo = false;
				intentType = MediaStore.ACTION_IMAGE_CAPTURE;
			}
		}
		MediaModule.mediaType = isVideo ? MEDIA_TYPE_VIDEO : MEDIA_TYPE_PHOTO;

		// Fetch the top-most activity to spawn the camera activity from.
		TiActivitySupport activitySupport = null;
		Activity activity = TiApplication.getInstance().getCurrentActivity();
		if (activity instanceof TiActivitySupport) {
			activitySupport = (TiActivitySupport) activity;
		}
		if (activitySupport == null) {
			if (errorCallback != null) {
				KrollDict response = new KrollDict();
				response.putCodeAndMessage(UNKNOWN_ERROR, "There are no activities to show the camera from.");
				errorCallback.callAsync(getKrollObject(), response);
			}
			return;
		}

		// Do not continue if device does not have a camera.
		if (getIsCameraSupported() == false) {
			if (errorCallback != null) {
				KrollDict response = new KrollDict();
				response.putCodeAndMessage(UNKNOWN_ERROR, "Camera Not Supported");
				errorCallback.callAsync(getKrollObject(), response);
			}
			Log.e(TAG, "Camera not supported");
			return;
		}

		// Create file URI for the camera to write the capture photo/video to.
		final Uri mediaUri = createExternalMediaContentUri(isVideo, saveToPhotoGallery);
		if (mediaUri == null) {
			if (errorCallback != null) {
				KrollDict response = new KrollDict();
				response.putCodeAndMessage(NO_CAMERA, "Unable to create file for storage");
				errorCallback.callAsync(getKrollObject(), response);
			}
			return;
		}

		// Set up the camera intent.
		Intent intent = new Intent(intentType);
		intent.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
		intent.setClipData(ClipData.newRawUri("", mediaUri));
		intent.putExtra(MediaStore.EXTRA_OUTPUT, mediaUri);
		intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, videoQuality);
		intent.putExtra("android.intent.extras.CAMERA_FACING", cameraType);
		if (videoMaximumDuration > 0) {
			intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, videoMaximumDuration);
		}

		// Show the default camera app's activity for capturing a photo/video.
		int requestCode = activitySupport.getUniqueResultCode();
		activitySupport.launchActivityForResult(intent, requestCode, new TiActivityResultHandler() {
			@Override
			public void onResult(Activity activity, int requestCode, int resultCode, Intent data)
			{
				// Fetch a URI to the photo/video if successfully captured.
				Uri captureUri = null;
				if (resultCode == Activity.RESULT_OK) {
					if (data != null) {
						captureUri = data.getData();
					}
					if (captureUri == null) {
						captureUri = mediaUri;
					}
				}

				// Delete temp file we created if not used by camera or user canceled out.
				if (!mediaUri.equals(captureUri)) {
					try {
						contentResolver.delete(mediaUri, null, null);
					} catch (Exception ex) {
						Log.e(TAG, "Failed to delete temporary camera file.", ex);
					}
				}

				// Notify the caller with the results.
				if ((resultCode == Activity.RESULT_OK) && (captureUri != null)) {
					if (successCallback != null) {
						TiBaseFile tiFile = TiFileFactory.createTitaniumFile(captureUri.toString(), false);
						TiBlob blob = TiBlob.blobFromFile(tiFile);
						KrollDict response = MediaModule.createDictForImage(blob, blob.getMimeType());
						successCallback.callAsync(getKrollObject(), response);
					}
				} else if (resultCode == Activity.RESULT_CANCELED) {
					if (cancelCallback != null) {
						KrollDict response = new KrollDict();
						response.putCodeAndMessage(NO_ERROR, null);
						cancelCallback.callAsync(getKrollObject(), response);
					}
				} else {
					if (errorCallback != null) {
						errorCallback.callAsync(getKrollObject(), createErrorResponse(UNKNOWN_ERROR, null));
					}
				}
			}

			@Override
			public void onError(Activity activity, int requestCode, Exception ex)
			{
				// Delete the temp file we created.
				try {
					contentResolver.delete(mediaUri, null, null);
				} catch (Exception ex2) {
				}

				// Notify the caller about the failure.
				String message = "Camera problem: " + ex.getMessage();
				Log.e(TAG, message, ex);
				if (errorCallback != null) {
					errorCallback.callAsync(getKrollObject(), createErrorResponse(UNKNOWN_ERROR, message));
				}
			}
		});
	}

	private void launchCameraActivity(KrollDict cameraOptions, TiViewProxy overLayProxy)
	{
		KrollFunction successCallback = null;
		KrollFunction cancelCallback = null;
		KrollFunction errorCallback = null;
		KrollFunction androidbackCallback = null;
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
		if (cameraOptions.containsKeyAndNotNull(TiC.EVENT_ANDROID_BACK)) {
			androidbackCallback = (KrollFunction) cameraOptions.get(TiC.EVENT_ANDROID_BACK);
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
		}
		if ((mediaTypes != null) && Arrays.asList(mediaTypes).contains(MEDIA_TYPE_VIDEO)) {
			MediaModule.mediaType = MEDIA_TYPE_VIDEO;
		} else {
			MediaModule.mediaType = MEDIA_TYPE_PHOTO;
		}

		TiCameraActivity.callbackContext = getKrollObject();
		TiCameraActivity.mediaContext = this;
		TiCameraActivity.successCallback = successCallback;
		TiCameraActivity.cancelCallback = cancelCallback;
		TiCameraActivity.errorCallback = errorCallback;
		TiCameraActivity.androidbackCallback = androidbackCallback;
		TiCameraActivity.saveToPhotoGallery = saveToPhotoGallery;
		TiCameraActivity.autohide = autohide;
		TiCameraActivity.overlayProxy = overLayProxy;
		TiCameraActivity.whichCamera = whichCamera;
		TiCameraActivity.videoQuality = videoQuality;
		TiCameraActivity.videoMaximumDuration = videoMaximumDuration;
		TiCameraActivity.mediaType = MediaModule.mediaType;
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

		TiApplication app = TiApplication.getInstance();
		int status = app.checkSelfPermission(Manifest.permission.CAMERA);
		if (status != PackageManager.PERMISSION_GRANTED) {
			return false;
		}
		if (Build.VERSION.SDK_INT < 29) {
			status = app.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
			if (status != PackageManager.PERMISSION_GRANTED) {
				return false;
			}
		}
		return true;
	}

	@Kroll.method
	public boolean hasAudioRecorderPermissions()
	{
		if (Build.VERSION.SDK_INT < 23) {
			return true;
		}
		int status = TiApplication.getInstance().checkSelfPermission(Manifest.permission.RECORD_AUDIO);
		return (status == PackageManager.PERMISSION_GRANTED);
	}

	@Kroll.method
	public boolean hasPhotoGalleryPermissions()
	{
		// We don't have to request permission on versions older than Android 6.0.
		if (Build.VERSION.SDK_INT < 23) {
			return true;
		}

		// We don't need write permission on Android 10 and above.
		if (Build.VERSION.SDK_INT >= 29) {
			return true;
		}

		// Check if we can write to external storage.
		int status = TiApplication.getInstance().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
		return (status == PackageManager.PERMISSION_GRANTED);
	}

	@SuppressWarnings("unchecked")
	@Kroll.method
	public void showCamera(@SuppressWarnings("rawtypes") HashMap options)
	{
		if (!hasCameraPermissions()) {
			return;
		}
		KrollDict cameraOptions = null;
		if ((options == null) || !(options instanceof HashMap<?, ?>)) {
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
	public KrollPromise<KrollDict> requestCameraPermissions(
		@Kroll.argument(optional = true) KrollFunction permissionCallback)
	{
		final KrollObject callbackThisObject = getKrollObject();
		return KrollPromise.create((promise) -> {
			// Do not continue if we already have permission.
			if (hasCameraPermissions()) {
				KrollDict response = new KrollDict();
				response.putCodeAndMessage(0, null);
				if (permissionCallback != null) {
					permissionCallback.callAsync(callbackThisObject, response);
				}
				promise.resolve(response);
				return;
			}

			// Do not continue if there is no activity to host the request dialog.
			Activity activity = TiApplication.getInstance().getCurrentActivity();
			if (activity == null) {
				KrollDict response = new KrollDict();
				response.putCodeAndMessage(-1, "There are no activities to host the camera request dialog.");
				if (permissionCallback != null) {
					permissionCallback.callAsync(callbackThisObject, response);
				}
				promise.reject(new Throwable(response.getString(TiC.EVENT_PROPERTY_ERROR)));
				return;
			}

			// Create the permission list. On Android 10+, we don't need external storage permission anymore.
			ArrayList<String> permissionList = new ArrayList<>();
			permissionList.add(Manifest.permission.CAMERA);
			if (Build.VERSION.SDK_INT < 29) {
				permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
			}

			// Show dialog requesting permission.
			TiBaseActivity.registerPermissionRequestCallback(
				TiC.PERMISSION_CODE_CAMERA, permissionCallback, callbackThisObject, promise);
			activity.requestPermissions(permissionList.toArray(new String[0]), TiC.PERMISSION_CODE_CAMERA);
		});
	}

	@Kroll.method
	public KrollPromise<KrollDict> requestAudioRecorderPermissions(
		@Kroll.argument(optional = true) final KrollFunction permissionCallback)
	{
		final KrollObject callbackThisObject = getKrollObject();
		return KrollPromise.create((promise) -> {
			// Do not continue if we already have permission.
			if (hasAudioRecorderPermissions()) {
				KrollDict response = new KrollDict();
				response.putCodeAndMessage(0, null);
				if (permissionCallback != null) {
					permissionCallback.callAsync(callbackThisObject, response);
				}
				promise.resolve(response);
				return;
			}

			// Do not continue if there is no activity to host the request dialog.
			Activity activity = TiApplication.getInstance().getCurrentActivity();
			if (activity == null) {
				KrollDict response = new KrollDict();
				response.putCodeAndMessage(-1, "There are no activities to host the camera request dialog.");
				if (permissionCallback != null) {
					permissionCallback.callAsync(callbackThisObject, response);
				}
				promise.reject(new Throwable(response.getString(TiC.EVENT_PROPERTY_ERROR)));
				return;
			}

			// Show dialog requesting permission.
			TiBaseActivity.registerPermissionRequestCallback(
				TiC.PERMISSION_CODE_MICROPHONE, permissionCallback, callbackThisObject, promise);
			activity.requestPermissions(
				new String[] { Manifest.permission.RECORD_AUDIO }, TiC.PERMISSION_CODE_MICROPHONE);
		});
	}

	@Kroll.method
	public KrollPromise<KrollDict> requestPhotoGalleryPermissions(
		@Kroll.argument(optional = true) KrollFunction permissionCallback)
	{
		final KrollObject callbackThisObject = getKrollObject();
		return KrollPromise.create((promise) -> {
			// Do not continue if we already have permission.
			if (hasPhotoGalleryPermissions()) {
				KrollDict response = new KrollDict();
				response.putCodeAndMessage(0, null);
				if (permissionCallback != null) {
					permissionCallback.callAsync(callbackThisObject, response);
				}
				promise.resolve(response);
				return;
			}

			// Do not continue if there is no activity to host the request dialog.
			Activity activity = TiApplication.getInstance().getCurrentActivity();
			if (activity == null) {
				KrollDict response = new KrollDict();
				response.putCodeAndMessage(
					-1, "There are no activities to host the external storage permission request dialog.");
				if (permissionCallback != null) {
					permissionCallback.callAsync(callbackThisObject, response);
				}
				promise.reject(new Throwable(response.getString(TiC.EVENT_PROPERTY_ERROR)));
				return;
			}

			// Show dialog requesting permission.
			TiBaseActivity.registerPermissionRequestCallback(
				TiC.PERMISSION_CODE_EXTERNAL_STORAGE, permissionCallback, callbackThisObject, promise);
			activity.requestPermissions(
				new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, TiC.PERMISSION_CODE_EXTERNAL_STORAGE);
		});
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Kroll.method
	public void saveToPhotoGallery(Object arg, @Kroll.argument(optional = true) HashMap callbackargs)
	{
		// Fetch callbacks.
		KrollFunction successCallback = null;
		KrollFunction errorCallback = null;
		if (callbackargs != null) {
			KrollDict callbackDict = new KrollDict(callbackargs);
			if (callbackDict.containsKeyAndNotNull(TiC.PROPERTY_SUCCESS)) {
				successCallback = (KrollFunction) callbackDict.get(TiC.PROPERTY_SUCCESS);
			}
			if (callbackDict.containsKeyAndNotNull(TiC.EVENT_ERROR)) {
				errorCallback = (KrollFunction) callbackDict.get(TiC.EVENT_ERROR);
			}
		}

		// Verify first argument references a blob or file.
		boolean validType = ((arg instanceof TiBlob) || (arg instanceof TiFileProxy));
		if (!validType) {
			if (errorCallback != null) {
				KrollDict response = new KrollDict();
				response.putCodeAndMessage(UNKNOWN_ERROR, "Invalid type passed as argument");
				errorCallback.callAsync(getKrollObject(), response);
			}
			return;
		}

		// Save given image/video to the gallery.
		Uri contentUri = null;
		try {
			// Fetch the 1st argument as a blob.
			TiBlob blob = null;
			if (arg instanceof TiFileProxy) {
				blob = TiBlob.blobFromFile(((TiFileProxy) arg).getBaseFile());
			} else {
				blob = (TiBlob) arg;
			}

			// If we think we were given an image, verify that it was successfully decoded.
			boolean isVideo = blob.getMimeType().startsWith("video");
			if (!isVideo && ((blob.getWidth() <= 0) || (blob.getHeight() <= 0))) {
				if (errorCallback != null) {
					KrollDict response = new KrollDict();
					response.putCodeAndMessage(UNKNOWN_ERROR, "Could not decode bitmap from argument");
					errorCallback.callAsync(getKrollObject(), response);
				}
				return;
			}

			// Create an empty image/video in the gallery and fetch its "content://" URI.
			String mediaName = createExternalMediaName();
			long unixTime = System.currentTimeMillis();
			ContentValues contentValues = new ContentValues();
			contentValues.put(MediaStore.MediaColumns.TITLE, mediaName);
			contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, mediaName);
			contentValues.put(MediaStore.MediaColumns.MIME_TYPE, blob.getMimeType());
			contentValues.put(MediaStore.MediaColumns.SIZE, blob.getLength());
			contentValues.put(MediaStore.MediaColumns.DATE_ADDED, unixTime / 1000L);
			contentValues.put(MediaStore.MediaColumns.DATE_MODIFIED, unixTime / 1000L);
			if (Build.VERSION.SDK_INT >= 29) {
				contentValues.put(MediaStore.MediaColumns.DATE_TAKEN, unixTime);
			}
			ensureExternalPublicMediaDirectoryExists();
			if (isVideo) {
				contentUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues);
			} else {
				contentUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
			}

			// Copy the file to the gallery.
			try (BufferedInputStream in = new BufferedInputStream(blob.getInputStream());
				 BufferedOutputStream out = new BufferedOutputStream(contentResolver.openOutputStream(contentUri))) {
				byte[] byteBuffer = new byte[8192];
				int byteCount = 0;
				while ((byteCount = in.read(byteBuffer)) > 0) {
					out.write(byteBuffer, 0, byteCount);
				}
				out.flush();
			}
		} catch (Throwable t) {
			// Failed to write to the gallery. Delete the file if we can.
			if (contentUri != null) {
				try {
					contentResolver.delete(contentUri, null, null);
				} catch (Exception ex) {
				}
			}

			// Notify caller about the error.
			if (errorCallback != null) {
				KrollDict response = new KrollDict();
				response.putCodeAndMessage(UNKNOWN_ERROR, t.getMessage());
				errorCallback.callAsync(getKrollObject(), response);
			}
			return;
		}

		// We've successfully copied the file to the gallery. Invoke the success callback.
		if (successCallback != null) {
			KrollDict response = new KrollDict();
			response.putCodeAndMessage(NO_ERROR, null);
			successCallback.callAsync(getKrollObject(), response);
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

	public static Uri createExternalPictureContentUri(boolean isPublic)
	{
		boolean isVideo = false;
		return createExternalMediaContentUri(isVideo, isPublic);
	}

	public static Uri createExternalVideoContentUri(boolean isPublic)
	{
		boolean isVideo = true;
		return createExternalMediaContentUri(isVideo, isPublic);
	}

	private static Uri createExternalMediaContentUri(boolean isVideo, boolean isPublic)
	{
		TiApplication app = TiApplication.getInstance();

		// Generate a name for the media file. (Will not have an extension.)
		String fileName = createExternalMediaName();

		// Create video/photo file and fetch its "content://" URI.
		Uri contentUri;
		if (isPublic) {
			// Create file under system's "Movies" or "Pictures" folder.
			long unixTime = System.currentTimeMillis();
			ContentValues contentValues = new ContentValues();
			contentValues.put(MediaStore.MediaColumns.TITLE, fileName);
			contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
			contentValues.put(MediaStore.MediaColumns.MIME_TYPE, isVideo ? "video/mp4" : "image/jpeg");
			contentValues.put(MediaStore.MediaColumns.DATE_ADDED, unixTime / 1000L);
			contentValues.put(MediaStore.MediaColumns.DATE_MODIFIED, unixTime / 1000L);
			if (Build.VERSION.SDK_INT >= 29) {
				contentValues.put(MediaStore.MediaColumns.DATE_TAKEN, unixTime);
			}
			ensureExternalPublicMediaDirectoryExists();
			if (isVideo) {
				contentUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues);
			} else {
				contentUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
			}
		} else if (isVideo) {
			// Create video file under app's private external storage folder.
			// Note: This folder does not require "WRITE_EXTERNAL_STORAGE" permission.
			File moviesDir = app.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
			moviesDir.mkdirs();
			File videoFile = new File(moviesDir, fileName + ".mp4");
			contentUri = TiFileProvider.createUriFrom(videoFile);
		} else {
			// Create image file under app's private external storage folder.
			// Note: This folder does not require "WRITE_EXTERNAL_STORAGE" permission.
			File picturesDir = app.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
			picturesDir.mkdirs();
			File imageFile = new File(picturesDir, fileName + ".jpg");
			contentUri = TiFileProvider.createUriFrom(imageFile);
		}
		return contentUri;
	}

	private static String createExternalMediaName()
	{
		TiApplication app = TiApplication.getInstance();

		// Fetch app name and restrict it to chars: a-z, A-Z, 0-9, periods, dashes, and underscores
		String normalizedAppName = app.getAppInfo().getName();
		normalizedAppName = normalizedAppName.replaceAll("[^\\w.-]", "_");

		// Generate file name.
		return normalizedAppName + "_" + (new SimpleDateFormat("yyyyMMdd_HHmmssSSS")).format(new Date());
	}

	private static void ensureExternalPublicMediaDirectoryExists()
	{
		// Work-around bug on Android 5.x and below where saving a file to gallery via MediaStore insert()
		// will fail if its directory on external storage doesn't exist yet. Create it if needed.
		// Bug Report: https://issuetracker.google.com/issues/37002888
		if (Build.VERSION.SDK_INT < 23) {
			File externalDir = Environment.getExternalStorageDirectory();
			if (externalDir != null) {
				File cameraDir = new File(externalDir, "DCIM/Camera");
				cameraDir.mkdirs();
			}
		}
	}

	@Kroll.setProperty
	public void setCameraFlashMode(int flashMode)
	{
		TiCameraActivity.setFlashMode(flashMode);
	}

	@Kroll.getProperty
	public int getCameraFlashMode()
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
			galleryIntent.getIntent().putExtra(Intent.EXTRA_MIME_TYPES, new String[] { "image/*", "video/*" });
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

		if (options.containsKey(TiC.PROPERTY_ALLOW_MULTIPLE)) {
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

				// Do not continue if no selection was made.
				Log.d(TAG, "OnResult called: " + resultCode, Log.DEBUG_MODE);
				if ((resultCode == Activity.RESULT_CANCELED) || (data == null)) {
					if (fCancelCallback != null) {
						KrollDict response = new KrollDict();
						response.putCodeAndMessage(NO_ERROR, null);
						fCancelCallback.callAsync(getKrollObject(), response);
					}
					return;
				}

				// Fetch a URI to file selected. (Only applicable to single file selection.)
				Uri uri = data.getData();
				String path = (uri != null) ? uri.toString() : null;

				// Handle multiple file selection, if enabled.
				if (requestCode == PICK_IMAGE_MULTIPLE) {
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
		try {
			mimeType = contentResolver.getType(Uri.parse(path));
		} catch (Exception ex) {
		}

		// Wrap the given file in a blob.
		TiBlob imageData = createImageData(new String[] { path }, mimeType);

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
		d.put("media", TiBlob.blobFromData(data, "image/*"));

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

	@Kroll.getProperty
	public boolean getIsCameraSupported()
	{
		return Camera.getNumberOfCameras() > 0;
	}

	@Kroll.getProperty
	public int[] getAvailableCameras()
	{
		int cameraCount = Camera.getNumberOfCameras();
		int[] result = new int[cameraCount];

		if (cameraCount == 0) {
			return result;
		}

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

	@Kroll.getProperty
	public boolean getCanRecord()
	{
		return TiApplication.getInstance().getPackageManager().hasSystemFeature("android.hardware.microphone");
	}

	@Override
	public String getApiName()
	{
		return "Ti.Media";
	}
}
