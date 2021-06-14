/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2012-2020 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.media;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollObject;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.TiC;
// import org.appcelerator.titanium.io.TiContentFile;
import org.appcelerator.titanium.io.TiFileFactory;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiFileHelper;
import org.appcelerator.titanium.util.TiRHelper;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCapture.OnImageSavedCallback;
import androidx.camera.core.ImageCapture.OutputFileOptions;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.VideoCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

@SuppressWarnings("deprecation")
public class TiCameraXActivity extends TiBaseActivity
{
	private static final String TAG = "TiCameraXActivity";
	PreviewView viewFinder;
	Preview preview;
	int lensFacing = CameraSelector.LENS_FACING_BACK;
	private static ImageCapture imageCapture;
	private static VideoCapture videoCapture;
	private static Executor executor = Executors.newSingleThreadExecutor();
	private TiViewProxy localOverlayProxy = null;
	private ProcessCameraProvider cameraProvider;
	public static boolean autohide = true;
	public static int cameraFlashMode = MediaModule.CAMERA_FLASH_OFF;
	public static int whichCamera = MediaModule.CAMERA_REAR;
	public static TiViewProxy overlayProxy = null;
	public static TiCameraXActivity cameraActivity = null;
	public static KrollObject callbackContext;
	public static KrollFunction successCallback, errorCallback, cancelCallback, androidbackCallback;
	public static String mediaType = MediaModule.MEDIA_TYPE_PHOTO;
	public static boolean saveToPhotoGallery = false;
	private static boolean isRecording = false;
	private static Uri videoUri = null;

	public static int bitRate = 8 * 1024 * 1024; // in bits/s - default 8 Mbit/s
	public static int frameRate = 30;
	public static int videoMaximumDuration = 0;
	public static long videoMaximumSize = 0;
	public static Size maxResolution = new Size(1920, 1080);

	FrameLayout layout;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		super.onCreate(savedInstanceState);

		localOverlayProxy = overlayProxy;

		try {
			int idLayout = TiRHelper.getResource("layout.titanium_ui_camera");
			int idPreview = TiRHelper.getResource("id.view_finder");

			layout =
				(FrameLayout) TiApplication.getAppCurrentActivity().getLayoutInflater().inflate(idLayout, null, false);
			viewFinder = (PreviewView) layout.findViewById(idPreview);
			setContentView(layout);

			boolean front = whichCamera == MediaModule.CAMERA_FRONT;
			if (front) {
				lensFacing = CameraSelector.LENS_FACING_FRONT;
			} else {
				lensFacing = CameraSelector.LENS_FACING_BACK;
			}

			startCamera();
		} catch (TiRHelper.ResourceNotFoundException e) {
			//
			Log.e(TAG, "Can't create camera.");
		}
	}

	private void startCamera()
	{
		int rotation = getWindowManager().getDefaultDisplay().getRotation();

		Activity activity = TiApplication.getAppCurrentActivity();
		ListenableFuture cameraProviderFuture = ProcessCameraProvider.getInstance(activity);
		cameraProviderFuture.addListener(() -> {
			try {
				preview = new Preview.Builder().build();
				cameraProvider = (ProcessCameraProvider) cameraProviderFuture.get();
				CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();

				if (MediaModule.MEDIA_TYPE_VIDEO.equals(mediaType)) {
					VideoCapture.Builder videoBuilder = new VideoCapture.Builder();
					videoBuilder = videoBuilder.setTargetRotation(rotation)
									   .setTargetAspectRatio(AspectRatio.RATIO_16_9)
									   .setCameraSelector(cameraSelector)
									   .setVideoFrameRate(frameRate)
									   .setMaxResolution(maxResolution)
									   .setBitRate(bitRate);
					videoCapture = videoBuilder.build();
					Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, videoCapture, preview);
				} else {
					imageCapture =
						new ImageCapture.Builder().setFlashMode(cameraFlashMode).setTargetRotation(rotation).build();

					Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture, preview);
				}
				preview.setSurfaceProvider(((PreviewView) viewFinder).getSurfaceProvider());
			} catch (InterruptedException | ExecutionException e) {
				// Currently no exceptions thrown. cameraProviderFuture.get()
				// shouldn't block since the listener is being called, so no need to
				// handle InterruptedException.
			}
		}, ContextCompat.getMainExecutor(activity));
	}

	public static void takePicture()
	{
		try {
			File file = TiFileHelper.getInstance().getTempFile(".jpg", true);
			OutputFileOptions outputFileOptions = new OutputFileOptions.Builder(file).build();

			imageCapture.takePicture(outputFileOptions, executor, new OnImageSavedCallback() {
				@Override
				public void onImageSaved(ImageCapture.OutputFileResults outputFileResults)
				{
					if (successCallback != null) {
						TiBlob blob = TiBlob.blobFromFile(TiFileFactory.createTitaniumFile(file.getPath(), false));
						KrollDict response = MediaModule.createDictForImage(blob, blob.getMimeType());
						successCallback.callAsync(callbackContext, response);

						if (cameraActivity != null && autohide) {
							cameraActivity.finish();
						}
					}
				}
				@Override
				public void onError(ImageCaptureException error)
				{
					if (errorCallback == null) {
						Log.e(TAG, error.toString());
						return;
					}

					KrollDict dict = new KrollDict();
					dict.putCodeAndMessage(error.getImageCaptureError(), error.toString());
					dict.put(TiC.PROPERTY_MESSAGE, error.toString());
					errorCallback.callAsync(callbackContext, dict);
				}
			});
		} catch (Exception ex) {
			//
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		cameraActivity = this;
		if (localOverlayProxy != null) {
			View overlayView = localOverlayProxy.getOrCreateView().getNativeView();
			ViewGroup parent = (ViewGroup) overlayView.getParent();
			if (parent != null) {
				parent.removeView(overlayView);
			}
			layout.addView(overlayView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
																	 FrameLayout.LayoutParams.MATCH_PARENT));
		}
	}

	@Override
	protected void onDestroy()
	{
		// Release our camera activity reference.
		if (cameraActivity == this) {
			cameraActivity = null;
		}
		// Destroy this activity.
		super.onDestroy();
	}

	@Override
	public void finish()
	{
		overlayProxy = null;
		super.finish();
	}

	@Override
	public void onBackPressed()
	{
		if (androidbackCallback != null) {
			KrollDict response = new KrollDict();
			response.putCodeAndMessage(-1, "User pressed androidback");
			androidbackCallback.callAsync(callbackContext, response);
		} else {
			if (cancelCallback != null) {
				KrollDict response = new KrollDict();
				response.putCodeAndMessage(-1, "User cancelled the request");
				cancelCallback.callAsync(callbackContext, response);
			}
			finish();
		}
	}

	protected void switchCamera(int whichCamera)
	{
		if (isRecording) {
			// don't switch cameras during video recording
			return;
		}

		boolean front = whichCamera == MediaModule.CAMERA_FRONT;
		if (front) {
			lensFacing = CameraSelector.LENS_FACING_FRONT;
		} else {
			lensFacing = CameraSelector.LENS_FACING_BACK;
		}
		cameraProvider.unbindAll();
		startCamera();
	}

	public static void startVideoCapture()
	{
		try {
			try {
				videoUri = MediaModule.createExternalVideoContentUri(saveToPhotoGallery);
			} catch (Exception ex) {
				Log.e(TAG, "Failed to open video file in gallery. Creating file in app's sandbox instead.", ex);
				try {
					videoUri = MediaModule.createExternalVideoContentUri(false);
				} catch (Exception e) {}
			}
			if (videoUri == null) {
				KrollDict dict = new KrollDict();
				dict.put(TiC.PROPERTY_MESSAGE, "Failed to create video file.");
				errorCallback.callAsync(callbackContext, dict);

				if (cameraActivity != null && autohide) {
					cameraActivity.finish();
				}
				return;
			}
			// File file = new File(videoUri.getPath());	// can't save there
			File file = TiFileHelper.getInstance().getTempFile(".mp4", true);

			VideoCapture.OutputFileOptions fileOptions = new VideoCapture.OutputFileOptions.Builder(file).build();

			isRecording = true;
			videoCapture.startRecording(fileOptions, executor, new VideoCapture.OnVideoSavedCallback() {
				@Override
				public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults)
				{
					// TiBlob blob = TiBlob.blobFromFile(new TiContentFile(videoUri));	// see above
					TiBlob blob = TiBlob.blobFromFile(TiFileFactory.createTitaniumFile(file.getPath(), false));
					KrollDict response = MediaModule.createDictForImage(blob, blob.getMimeType());
					successCallback.callAsync(callbackContext, response);

					if (cameraActivity != null && autohide) {
						cameraActivity.finish();
					}
				}

				@Override
				public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause)
				{
					Log.e(TAG, "Can't caputre video file. " + message);
				}
			});

			// TODO once CameraX supports a maxDuration this can be removed:
			if (videoMaximumDuration > 0) {
				final Handler handler = new Handler(Looper.getMainLooper());
				handler.postDelayed(new Runnable() {
					@Override
					public void run()
					{
						videoCapture.stopRecording();
					}
				}, videoMaximumDuration);
			}
		} catch (Exception e) {
			Log.e(TAG, "Can't create video file: " + e.getMessage());
		}
	}

	public static void stopVideoCapture()
	{
		videoCapture.stopRecording();
		isRecording = false;
	}

	public static void setFlashMode(int cameraFlashMode) {}

	public static void hide()
	{
		if (cameraActivity != null) {
			cameraActivity.finish();
		}
	}
}
