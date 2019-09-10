/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-present by Axway Appcelerator. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.worker;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxySupport;
import org.appcelerator.kroll.KrollWorker;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.util.KrollAssetHelper;
import org.appcelerator.titanium.TiC;

public class V8Worker extends KrollWorker
{
	private static final String TAG = "V8Worker";
	private WorkerProxy proxy;

	public V8Worker(WorkerProxy proxy, String url, KrollDict options)
	{
		super(url, options.getString(TiC.PROPERTY_NAME));
		this.proxy = proxy;
		this.start();
	}

	@Override
	public boolean tryExecScript()
	{
		String fullUrl = this.url;
		if (!fullUrl.contains("://") && !fullUrl.startsWith("/") && proxy.getCreationUrl().baseUrl != null) {
			fullUrl = proxy.getCreationUrl().baseUrl + fullUrl;
		}

		if (fullUrl.startsWith(TiC.URL_APP_PREFIX)) {
			fullUrl = fullUrl.replaceAll("app:/", "Resources");

		} else if (fullUrl.startsWith(TiC.URL_ANDROID_ASSET_RESOURCES)) {
			fullUrl = fullUrl.replaceAll("file:///android_asset/", "");
		}
		String script = KrollAssetHelper.readAsset(fullUrl);
		if (script == null || script.trim().length() == 0) {
			String message = "File not found or empty";
			Log.e(TAG, message, Log.DEBUG_MODE);
			KrollDict data = new KrollDict();
			data.putCodeAndMessage(TiC.ERROR_CODE_UNKNOWN, message);
			data.put(TiC.EVENT_PROPERTY_URL, url);
			proxy.fireEvent(TiC.EVENT_ERROR, data);
			return false;
		}
		nativeInit(this.name);
		nativeRunModule(script, url, null);
		return true;
	}

	@Override
	public void handleMessage(Object message)
	{
		KrollDict d = new KrollDict();
		d.put("type", "message");
		d.put("data", message);
		nativeOnMessage(d);
	}

	@Override
	public void handleDispose()
	{
		super.handleDispose();

		ti.modules.titanium.TitaniumModule.cancelTimers();
		nativeDispose();
	}

	@Override
	public void globalClose()
	{
		this.proxy.terminate();
	}

	@Override
	public void globalPostMessage(Object message)
	{
		KrollDict d = new KrollDict();
		d.put("data", message);
		this.proxy.fireEvent("message", d);
	}

	// JNI method prototypes
	private native void nativeInit(String name);
	private native void nativeRunModule(String source, String filename, KrollProxySupport workerProxy);
	private native void nativeDispose();
	private native void nativeOnMessage(Object message);
}
