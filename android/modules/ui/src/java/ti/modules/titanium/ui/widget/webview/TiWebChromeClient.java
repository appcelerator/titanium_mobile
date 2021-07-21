/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2020 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget.webview;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Message;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebStorage.QuotaUpdater;
import android.webkit.WebView;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import java.util.ArrayList;
import java.util.HashMap;
import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiActivityResultHandler;
import org.appcelerator.titanium.util.TiUIHelper;
import ti.modules.titanium.media.MediaModule;
import ti.modules.titanium.ui.WebViewProxy;

@SuppressWarnings("deprecation")
public class TiWebChromeClient extends WebChromeClient
{
	private static final String TAG = "TiWebChromeClient";
	private static final String CONSOLE_TAG = TAG + ".console";

	private TiUIWebView tiWebView;
	private FrameLayout mCustomViewContainer;
	private CustomViewCallback mCustomViewCallback;
	private View mCustomView;
	private Uri mCaptureFileUri;

	public TiWebChromeClient(TiUIWebView webView)
	{
		super();
		this.tiWebView = webView;
	}

	/**
	 * Called when the HTML attempts to use the geolocation APIs and needs permission from the end-user.
	 * @param origin The website requesting permission.
	 * @param callback To be invoked to grant/deny permission to location data.
	 */
	@Override
	public void onGeolocationPermissionsShowPrompt(
		final String origin, final GeolocationPermissions.Callback callback)
	{
		// Validate.
		if (callback == null) {
			return;
		}

		// Fetch the WebView's activity.
		TiBaseActivity activity = getTiBaseActivity();
		if (activity == null) {
			callback.invoke(origin, false, false);
			return;
		}

		// Prompt end-user for permission on Android 6.0 and higher if needed.
		if (Build.VERSION.SDK_INT >= 23) {
			int permissionResult = activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
				& activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
			if (permissionResult != PackageManager.PERMISSION_GRANTED) {
				TiBaseActivity.OnRequestPermissionsResultCallback activityCallback;
				activityCallback = new TiBaseActivity.OnRequestPermissionsResultCallback() {
					@Override
					public void onRequestPermissionsResult(
						@NonNull TiBaseActivity activity, int requestCode,
						@NonNull String[] permissions, @NonNull int[] grantResults)
					{
						// Unregister this callback.
						TiBaseActivity.unregisterPermissionRequestCallback(TiC.PERMISSION_CODE_LOCATION);

						// Determine if location permission was granted.
						boolean granted = false;
						if (permissions.length == grantResults.length) {
							for (int index = 0; index < permissions.length; index++) {
								if (Manifest.permission.ACCESS_FINE_LOCATION.equals(permissions[index])
									|| Manifest.permission.ACCESS_COARSE_LOCATION.equals(permissions[index])) {
									granted = (grantResults[index] == PackageManager.PERMISSION_GRANTED);
									break;
								}
							}
						}

						// Notify WebView whether or not location access was granted.
						callback.invoke(origin, granted, false);
					}
				};
				TiBaseActivity.registerPermissionRequestCallback(TiC.PERMISSION_CODE_LOCATION, activityCallback);
				String[] permissions = new String[] { Manifest.permission.ACCESS_FINE_LOCATION,
					Manifest.permission.ACCESS_COARSE_LOCATION };
				activity.requestPermissions(permissions, TiC.PERMISSION_CODE_LOCATION);
				return;
			}
		}

		// Notify WebView that location access is granted.
		callback.invoke(origin, true, false);
	}

	/**
	 * Called when the HTML is requesting permission to do WebRTC audio/video capture or access a media resource.
	 * This method will prompt the end-user for permission to grant access to the requested web resource.
	 * @param request Object providing the grant/deny callback and the resoruces being requested.
	 */
	@Override
	@RequiresApi(21)
	public void onPermissionRequest(final PermissionRequest request)
	{
		// Validate argument
		if (request == null) {
			return;
		}

		// Fetch resources that the WebView is requesting permissions for.
		// Note: Array will be empty for resources Android does not support, such as screen sharing.
		String[] resourceNames = request.getResources();
		if ((resourceNames == null) || (resourceNames.length <= 0)) {
			request.deny();
			return;
		}

		// Fetch the WebView's activity.
		TiBaseActivity tiActivity = getTiBaseActivity();
		if (tiActivity == null) {
			request.deny();
			return;
		}

		// Prompt end-user for permission on Android 6.0 and higher if needed.
		if (Build.VERSION.SDK_INT >= 23) {
			// Determine if we need to request for any permissions.
			boolean isAudioRecordingPermissionRequired = false;
			boolean isCameraPermissionRequired = false;
			for (String nextName : resourceNames) {
				if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(nextName)) {
					int result = tiActivity.checkSelfPermission(Manifest.permission.RECORD_AUDIO);
					isAudioRecordingPermissionRequired = (result != PackageManager.PERMISSION_GRANTED);
				} else if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(nextName)) {
					int result = tiActivity.checkSelfPermission(Manifest.permission.CAMERA);
					isCameraPermissionRequired = (result != PackageManager.PERMISSION_GRANTED);
				}
			}

			// Request permissions from end-user if needed.
			if (isAudioRecordingPermissionRequired || isCameraPermissionRequired) {
				ArrayList<String> permissionNameList = new ArrayList<>();
				if (isAudioRecordingPermissionRequired) {
					permissionNameList.add(Manifest.permission.RECORD_AUDIO);
				}
				if (isCameraPermissionRequired) {
					permissionNameList.add(Manifest.permission.CAMERA);
				}
				TiBaseActivity.OnRequestPermissionsResultCallback callback;
				callback = new TiBaseActivity.OnRequestPermissionsResultCallback() {
					@Override
					public void onRequestPermissionsResult(
						@NonNull TiBaseActivity activity, int requestCode,
						@NonNull String[] permissions, @NonNull int[] grantResults)
					{
						// Unregister this callback.
						TiBaseActivity.unregisterPermissionRequestCallback(TiC.PERMISSION_CODE_CAMERA);

						// Create a new resource name list with all granted permissions.
						ArrayList<String> resourceNameList = new ArrayList<>();
						for (String resourceName : request.getResources()) {
							String permissionName = null;
							if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resourceName)) {
								permissionName = Manifest.permission.RECORD_AUDIO;
							} else if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resourceName)) {
								permissionName = Manifest.permission.CAMERA;
							}
							if (permissionName != null) {
								// Check if resource was granted permission. If so, then add it to the list.
								for (int index = 0; index < permissions.length; index++) {
									if (permissionName.equals(permissions[index])) {
										if (grantResults[index] == PackageManager.PERMISSION_GRANTED) {
											resourceNameList.add(resourceName);
											break;
										}
									}
								}
							} else {
								// This resource does not require permission. Add it to the granted list.
								resourceNameList.add(resourceName);
							}
						}

						// Notify WebView if which resources were granted permission.
						if (resourceNameList.isEmpty()) {
							request.deny();
						} else {
							request.grant(resourceNameList.toArray(new String[0]));
						}
					}
				};
				TiBaseActivity.registerPermissionRequestCallback(TiC.PERMISSION_CODE_CAMERA, callback);
				tiActivity.requestPermissions(permissionNameList.toArray(new String[0]), TiC.PERMISSION_CODE_CAMERA);
				return;
			}
		}

		// Grant permission to all resources.
		request.grant(resourceNames);
	}

	@Override
	public boolean onConsoleMessage(ConsoleMessage message)
	{
		String text = message.message() + " (" + message.lineNumber() + ":" + message.sourceId() + ")";
		switch (message.messageLevel()) {
			case DEBUG:
				Log.d(CONSOLE_TAG, text);
				break;
			case ERROR:
				Log.e(CONSOLE_TAG, text);
				break;
			case WARNING:
				Log.w(CONSOLE_TAG, text);
				break;
			default:
				Log.i(CONSOLE_TAG, text);
				break;
		}
		return true;
	}

	@Override
	public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult result)
	{
		TiUIHelper.doOkDialog("Alert", message, (DialogInterface dialog, int which) -> {
			result.confirm();
		});
		return true;
	}

	@Override
	public void onProgressChanged(WebView view, int progress)
	{
		WebViewProxy proxy = (WebViewProxy) tiWebView.getProxy();
		if (proxy != null) {
			KrollDict data = new KrollDict();
			data.put(TiC.PROPERTY_VALUE, (double) progress / 100.0d); // docs state 0.0 to 1.0
			data.put(TiC.EVENT_PROPERTY_URL, proxy.getProperty(TiC.PROPERTY_URL));
			proxy.fireEvent("progress", data);
		}
	}

	// This exposes onCreateWindow to JS with a similar API to Android:
	// If the end-developer sets the 'onCreateWindow' property of the WebViewProxy
	// to a callback function, then it gets executed when a new window is created
	// by the WebView (generally, when a link is clicked that has a non-existent target such as _blank)
	// If the end-developer wants to open a new window, they can simply create a WebViewProxy,
	// along with any other supporting views, and return the proxy from their callback.
	// Otherwise, they can return null (or anything other than a WebViewProxy), and nothing will happen
	@Override
	public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg)
	{
		WebViewProxy proxy = (WebViewProxy) tiWebView.getProxy();
		if (proxy == null) {
			return false;
		}

		Object onCreateWindow = proxy.getProperty(TiC.PROPERTY_ON_CREATE_WINDOW);
		if (!(onCreateWindow instanceof KrollFunction)) {
			return false;
		}

		Message href = view.getHandler().obtainMessage();
		view.requestFocusNodeHref(href);
		String url = href.getData().getString("url");

		Object onLink = proxy.getProperty(TiC.PROPERTY_ON_LINK);
		if (onLink instanceof KrollFunction) {
			KrollFunction onLinkFunction = (KrollFunction) onLink;
			KrollDict args = new KrollDict();
			args.put(TiC.EVENT_PROPERTY_URL, url);
			Object result = onLinkFunction.call(proxy.getKrollObject(), args);
			if (result == null || (result instanceof Boolean && ((Boolean) result) == false)) {
				return false;
			}
		}

		KrollFunction onCreateWindowFunction = (KrollFunction) onCreateWindow;
		HashMap<String, Object> args = new HashMap<>();
		args.put(TiC.EVENT_PROPERTY_IS_DIALOG, isDialog);
		args.put(TiC.EVENT_PROPERTY_IS_USER_GESTURE, isUserGesture);

		Object result = onCreateWindowFunction.call(proxy.getKrollObject(), args);
		if (result instanceof WebViewProxy) {
			WebViewProxy newProxy = (WebViewProxy) result;
			newProxy.setPostCreateMessage(resultMsg);
			newProxy.getWebView().setProxy(proxy);
			return true;
		}

		return false;
	}

	@Override
	public void onExceededDatabaseQuota(String url, String databaseIdentifier, long currentQuota, long estimatedSize,
										long totalUsedQuota, QuotaUpdater quotaUpdater)
	{
		quotaUpdater.updateQuota(estimatedSize * 2);
	}

	@Override
	public void onShowCustomView(View view, CustomViewCallback callback)
	{
		tiWebView.getWebView().setVisibility(View.GONE);

		// If a view already existed then immediately terminate the new one.
		if (mCustomView != null) {
			callback.onCustomViewHidden();
			return;
		}

		Activity activity = tiWebView.getProxy().getActivity();
		FrameLayout.LayoutParams params =
			new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
		if (activity instanceof TiBaseActivity) {
			if (mCustomViewContainer == null) {
				mCustomViewContainer = new FrameLayout(activity);
				mCustomViewContainer.setBackgroundColor(Color.BLACK);
				mCustomViewContainer.setLayoutParams(params);
				activity.getWindow().addContentView(mCustomViewContainer, params);
			}
			mCustomViewContainer.addView(view);
			mCustomView = view;
			mCustomViewCallback = callback;
			mCustomViewContainer.setVisibility(View.VISIBLE);
		}
	}

	@Override
	public void onHideCustomView()
	{
		if (mCustomView == null) {
			return;
		}

		// Hide the custom view and remove it from its container.
		mCustomView.setVisibility(View.GONE);
		mCustomViewContainer.removeView(mCustomView);
		mCustomView = null;
		mCustomViewContainer.setVisibility(View.GONE);
		mCustomViewCallback.onCustomViewHidden();

		tiWebView.getWebView().setVisibility(View.VISIBLE);
	}

	public boolean interceptOnBackPressed()
	{
		if (mCustomView != null) {
			onHideCustomView();
			if (Log.isDebugModeEnabled()) {
				Log.d(TAG, "WebView intercepts the OnBackPressed event to close the full-screen video.");
			}
			return true;
		}
		return false;
	}

	/**
	 * Called when an HTML <input/> element has been tapped on for file selection or photo/video capture.
	 * @param webView The WebView requesting the file.
	 * @param filePathCallback The callback to be invoked with the file(s) selected or null if nothing was selected.
	 * @param chooserParams Provides title to be shown in dialog, mime types to filter by, etc.
	 * @return
	 * Returns true if this method will invoke the "filePathCallback" argument.
	 * Returns false if not and the system should do its default handling.
	 */
	@Override
	@RequiresApi(21)
	public boolean onShowFileChooser(
		final WebView webView, final ValueCallback<Uri[]> filePathCallback,
		final WebChromeClient.FileChooserParams chooserParams)
	{
		// Validate arguments.
		if (filePathCallback == null) {
			return false;
		}

		// Fetch the WebView's activity.
		TiBaseActivity activity = getTiBaseActivity();
		if (activity == null) {
			filePathCallback.onReceiveValue(null);
			return true;
		}

		// Prompt the end-user for camera permission if required.
		// Note: We only need external storage permission on OS versions older than Android 10.
		if (chooserParams.isCaptureEnabled() && (Build.VERSION.SDK_INT >= 23)) {
			int permissionResult = activity.checkSelfPermission(Manifest.permission.CAMERA);
			boolean isCameraPermissionRequired = (permissionResult != PackageManager.PERMISSION_GRANTED);
			boolean isStoragePermissionRequired = (Build.VERSION.SDK_INT < 29);
			if (isStoragePermissionRequired) {
				permissionResult = activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
				isStoragePermissionRequired = (permissionResult != PackageManager.PERMISSION_GRANTED);
			}
			if (isCameraPermissionRequired || isStoragePermissionRequired) {
				final ArrayList<String> permissionList = new ArrayList<>();
				if (isCameraPermissionRequired) {
					permissionList.add(Manifest.permission.CAMERA);
				}
				if (isStoragePermissionRequired) {
					permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
				}
				TiBaseActivity.OnRequestPermissionsResultCallback permissionsCallback;
				permissionsCallback = new TiBaseActivity.OnRequestPermissionsResultCallback() {
					@Override
					public void onRequestPermissionsResult(
						@NonNull TiBaseActivity activity, int requestCode,
						@NonNull String[] permissions, @NonNull int[] grantResults)
					{
						// Unregister this callback.
						TiBaseActivity.unregisterPermissionRequestCallback(TiC.PERMISSION_CODE_CAMERA);

						// Determine if all needed permissions were granted.
						boolean wasGranted = false;
						for (String permissionName : permissionList) {
							wasGranted = false;
							for (int index = 0; index < permissions.length; index++) {
								if (permissionName.equals(permissions[index])) {
									wasGranted = (grantResults[index] == PackageManager.PERMISSION_GRANTED);
									break;
								}
							}
							if (!wasGranted) {
								break;
							}
						}

						// Retry file chooser if permission was granted.
						// If denied, then invoke callback with null file results.
						if (wasGranted) {
							onShowFileChooser(webView, filePathCallback, chooserParams);
						} else {
							filePathCallback.onReceiveValue(null);
						}
					}
				};
				TiBaseActivity.registerPermissionRequestCallback(TiC.PERMISSION_CODE_CAMERA, permissionsCallback);
				activity.requestPermissions(permissionList.toArray(new String[0]), TiC.PERMISSION_CODE_CAMERA);
				return true;
			}
		}

		// Set up the onActivityResult() handler.
		TiActivityResultHandler resultHandler = new TiActivityResultHandler() {
			@Override
			public void onResult(Activity activity, int requestCode, int resultCode, Intent data)
			{
				// Fetch the child activity's results.
				Uri[] results = null;
				if (resultCode == Activity.RESULT_OK) {
					// Check the intent's results.
					if (data != null) {
						ClipData clipData = data.getClipData();
						if ((clipData != null) && (clipData.getItemCount() > 0)) {
							// Multiple files have been selected.
							results = new Uri[clipData.getItemCount()];
							for (int index = 0; index < clipData.getItemCount(); index++) {
								ClipData.Item item = clipData.getItemAt(index);
								if (item != null) {
									results[index] = item.getUri();
								}
							}
						} else if (data.getData() != null) {
							// Only 1 file has been selected.
							results = new Uri[] { data.getData() };
						}
					}

					// If intent did not provide any results, then check if we have a camera capture file.
					if ((results == null) && (mCaptureFileUri != null)) {
						results = new Uri[] { mCaptureFileUri };
					}
				}

				// Delete pre-generated capture file if canceled out or camera created its own file.
				if (mCaptureFileUri != null) {
					boolean wasFound = false;
					if (results != null) {
						for (Uri nextUri : results) {
							if (mCaptureFileUri.equals(nextUri)) {
								wasFound = true;
								break;
							}
						}
					}
					if (!wasFound) {
						deleteCaptureFileUri();
					}
				}

				// Invoke WebView's callback with selected file(s).
				filePathCallback.onReceiveValue(results);
			}

			@Override
			public void onError(Activity activity, int requestCode, Exception ex)
			{
				Log.e(TAG, "onShowFileChooser() failed to get permission.", ex);
				deleteCaptureFileUri();
				filePathCallback.onReceiveValue(null);
			}
		};

		// Display the file chooser or catpure activity.
		try {
			Intent intent = createFileChooserIntentFrom(chooserParams);
			activity.launchActivityForResult(intent, activity.getUniqueResultCode(), resultHandler);
		} catch (Exception ex) {
			Log.w(TAG, "onShowFileChooser() could not open window. Reason: " + ex.getMessage());
			deleteCaptureFileUri();
			filePathCallback.onReceiveValue(null);
		}
		return true;
	}

	/**
	 * Creates an intent for selecting file(s) from the given parameters.
	 * Intended to be called by the onShowFileChooser() method.
	 * @param chooserParams Provides the HTML <input/> element's settings for file selection or image/video capture.
	 * @return Returns an intent to be passed to the startActivityForResult() method.
	 */
	@NonNull
	@RequiresApi(21)
	private Intent createFileChooserIntentFrom(WebChromeClient.FileChooserParams chooserParams)
	{
		// Create the intent.
		Intent intent = new Intent();

		// Set the intent's category, if needed.
		if (!chooserParams.isCaptureEnabled()) {
			intent.addCategory(Intent.CATEGORY_OPENABLE);
		}

		// Fetch the file mime-types, if any.
		String[] acceptTypes = chooserParams.getAcceptTypes();
		if (acceptTypes == null) {
			acceptTypes = new String[0];
		}

		// Fetch info about all mime types given.
		boolean hasAudioMimeType = false;
		boolean hasImageMimeType = false;
		boolean hasVideoMimeType = false;
		for (String nextType : acceptTypes) {
			if (nextType != null) {
				String lowerCaseType = nextType.toLowerCase();
				if (lowerCaseType.startsWith("audio/")) {
					hasAudioMimeType = true;
				} else if (lowerCaseType.startsWith("image/")) {
					hasImageMimeType = true;
				} else if (lowerCaseType.startsWith("video/")) {
					hasVideoMimeType = true;
				}
			}
		}

		// Set the intent's action.
		String actionName = Intent.ACTION_GET_CONTENT;
		if (chooserParams.isCaptureEnabled()) {
			// Use camera photo/video taking action. (There is no action for capturing audio only.)
			if (hasVideoMimeType || hasAudioMimeType) {
				actionName = MediaStore.ACTION_VIDEO_CAPTURE;
			} else {
				actionName = MediaStore.ACTION_IMAGE_CAPTURE;
			}
		} else if ((acceptTypes.length > 1) && !hasImageMimeType && !hasVideoMimeType) {
			// Only use this action when given multiple mime types, but none of them are photos/videos.
			// Will show files from storage, but won't show files from photo gallery or camera's sandbox.
			actionName = Intent.ACTION_OPEN_DOCUMENT;
		}
		intent.setAction(actionName);

		// Set the mime type(s) of the files allowed to be chosen. Default to all types if not set.
		if (!chooserParams.isCaptureEnabled()) {
			intent.setType("*/*");
			if (acceptTypes.length > 1) {
				// Set multiple mime types. (Main type must be "*/*" or else this Extra will be ignored.)
				intent.putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes);
			} else if (acceptTypes.length == 1) {
				// Only given 1 mime type. Use it as the main type.
				String mimeType = acceptTypes[0];
				if ((mimeType != null) && !mimeType.isEmpty()) {
					intent.setType(mimeType);
				}
			}
		}

		// If capturing a photo/video, create a file for it in the gallery and get a "content://" URI to it.
		switch (actionName) {
			case MediaStore.ACTION_IMAGE_CAPTURE:
				mCaptureFileUri = MediaModule.createExternalPictureContentUri(true);
				break;
			case MediaStore.ACTION_VIDEO_CAPTURE:
				mCaptureFileUri = MediaModule.createExternalVideoContentUri(true);
				break;
			default:
				mCaptureFileUri = null;
				break;
		}
		if (mCaptureFileUri != null) {
			intent.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
			intent.setClipData(ClipData.newRawUri("", mCaptureFileUri));
			intent.putExtra(MediaStore.EXTRA_OUTPUT, mCaptureFileUri);
		}

		// Set up multiple file selection if enabled.
		if (chooserParams.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
			intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
		}

		// If multiple apps can handle the intent, then let the end-user choose which one to use.
		intent = Intent.createChooser(intent, chooserParams.getTitle());

		// Return the final intent for file selection or image/video capturing.
		return intent;
	}

	/**
	 * Deletes the photo/video file referenced by member variable "mCaptureFileUri" if currently assigned
	 * and then nulls out the references. To be called when canceling/erroring out from onShowFileChooser().
	 * @return
	 * Returns true if successfully deleted the file.
	 * Returns false if failed to delete or "mCaptureFileUri" is not assigned.
	 */
	private boolean deleteCaptureFileUri()
	{
		boolean wasDeleted = false;
		try {
			if (mCaptureFileUri != null) {
				int rowCount = TiApplication.getInstance().getContentResolver().delete(mCaptureFileUri, null, null);
				wasDeleted = (rowCount > 0);
			}
		} catch (Exception ex) {
			Log.e(TAG, "Failed to delete WebView capture file.", ex);
		} finally {
			mCaptureFileUri = null;
		}
		return wasDeleted;
	}

	/**
	 * Gets the "TiBaseActivity" the WebView is assigned to.
	 * @return
	 * Returns the "TiBaseActivity" instance.
	 * Returns null if WebView belongs to a non-TiBaseActivity derived activity or if detached from an activity.
	 */
	private TiBaseActivity getTiBaseActivity()
	{
		TiViewProxy webViewProxy = this.tiWebView.getProxy();
		if (webViewProxy != null) {
			Activity activity = webViewProxy.getActivity();
			if (activity instanceof TiBaseActivity) {
				return (TiBaseActivity) activity;
			}
		}
		return null;
	}
}
