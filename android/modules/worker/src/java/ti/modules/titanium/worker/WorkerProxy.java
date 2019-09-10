/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-present by Axway Appcelerator. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.worker;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiC;

@Kroll.proxy(creatableInModule = WorkerModule.class, name = "WorkerProxy")
public class WorkerProxy extends KrollProxy
{
	private static final String TAG = "WorkerProxy";
	private V8Worker worker;

	public WorkerProxy()
	{
		this("", null);
	}

	public WorkerProxy(final String jsFileName)
	{
		this(jsFileName, null);
	}

	public WorkerProxy(final String jsFileName, Object options)
	{
		super();
		KrollDict opts;
		if (options instanceof KrollDict) {
			opts = (KrollDict) options;
		} else {
			opts = new KrollDict();
		}
		if (!opts.containsKeyAndNotNull(TiC.PROPERTY_NAME)) {
			opts.put(TiC.PROPERTY_NAME, WorkerModule.getNextName());
		}
		worker = new V8Worker(this, jsFileName, opts);
		Log.d(TAG, "start");
	}

	@Kroll.method
	public void postMessage(final Object message)
	{
		if (worker == null) {
			return;
		}
		worker.postMessage(message);
	}

	@Kroll.method
	public void terminate()
	{
		if (worker == null) {
			return;
		}
		worker.terminate();
		worker = null;
	}

	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public String getApiName()
	// clang-format on
	{
		return "Ti.Worker";
	}
}
