/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-present by Axway Appcelerator. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.worker;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.KrollObject;
import org.appcelerator.kroll.annotations.Kroll;

import org.appcelerator.titanium.TiApplication;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiConfig;

@Kroll.module
public class WorkerModule extends KrollModule
{

	private static final String TAG = "TiWorkerModule";
	private static int nextId = 0;

	public WorkerModule()
	{
		super();
	}

	@Kroll.onAppCreate
	public static void onAppCreate(TiApplication app)
	{
		Log.d(TAG, "inside onAppCreate");
	}

	@Kroll.method
	public WorkerProxy createWorker(final String workerFile, @Kroll.argument(optional = true) final Object options)
	{
		return new WorkerProxy(workerFile, options);
	}

	public static String getNextName()
	{
		return "#" + nextId++;
	}
}
