/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2016 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.android;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.IntentProxy;
import org.appcelerator.titanium.util.TiConvert;

import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;

@Kroll.proxy(creatableInModule = AndroidModule.class,
			 propertyAccessors = { TiC.PROPERTY_FLAGS, TiC.PROPERTY_INTENT, TiC.PROPERTY_UPDATE_CURRENT_INTENT })
public class PendingIntentProxy extends KrollProxy
{

	protected PendingIntent pendingIntent;
	protected IntentProxy intent;
	protected Context pendingIntentContext;
	protected boolean updateCurrentIntent = true;
	protected int flags;

	public PendingIntentProxy()
	{
		super();
	}

	@Override
	public void handleCreationArgs(KrollModule createdInModule, Object[] args)
	{
		if (args.length >= 1 && args[0] instanceof IntentProxy) {
			intent = (IntentProxy) args[0];
			if (args.length >= 2) {
				flags = TiConvert.toInt(args[1]);
			}
		}

		super.handleCreationArgs(createdInModule, args);

		pendingIntentContext = getActivity();
		if (pendingIntentContext == null) {
			pendingIntentContext = TiApplication.getAppCurrentActivity();
		}
		if (pendingIntentContext == null) {
			pendingIntentContext = TiApplication.getInstance();
		}

		if (pendingIntentContext == null || intent == null) {
			throw new IllegalStateException("Creation arguments must contain intent");
		}
		switch (intent.getInternalType()) {
			case IntentProxy.TYPE_ACTIVITY: {
				pendingIntent = PendingIntent.getActivity(pendingIntentContext, 0, intent.getIntent(), flags);
				break;
			}
			case IntentProxy.TYPE_BROADCAST: {
				pendingIntent = PendingIntent.getBroadcast(pendingIntentContext, 0, intent.getIntent(), flags);
				break;
			}
			case IntentProxy.TYPE_SERVICE: {
				pendingIntent = PendingIntent.getService(pendingIntentContext, 0, intent.getIntent(), flags);
				break;
			}
		}
	}

	public void handleCreationDict(KrollDict dict)
	{
		if (dict.containsKey(TiC.PROPERTY_INTENT)) {
			intent = (IntentProxy) dict.get(TiC.PROPERTY_INTENT);
		}
		if (dict.containsKey(TiC.PROPERTY_UPDATE_CURRENT_INTENT)) {
			updateCurrentIntent = TiConvert.toBoolean(dict.get(TiC.PROPERTY_UPDATE_CURRENT_INTENT));
		}
		if (dict.containsKey(TiC.PROPERTY_FLAGS)) {
			flags = dict.getInt(TiC.PROPERTY_FLAGS);
		}

		//add FLAG_UPDATE_CURRENT if updateCurrentIntent is true
		if (updateCurrentIntent) {
			flags |= PendingIntent.FLAG_UPDATE_CURRENT;
		}

		// NOTE: Android 12 requires mutability flags.
		// Set `FLAG_IMMUTABLE` if not `FLAG_MUTABLE`.
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
			&& (flags & PendingIntent.FLAG_MUTABLE) == 0) {
			flags |= PendingIntent.FLAG_IMMUTABLE;
		}

		super.handleCreationDict(dict);
	}

	public PendingIntent getPendingIntent()
	{
		return pendingIntent;
	}

	@Override
	public String getApiName()
	{
		return "Ti.Android.PendingIntent";
	}
}
