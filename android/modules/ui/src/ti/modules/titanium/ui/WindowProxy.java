/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.proxy.TiWindowProxy;
import org.appcelerator.titanium.util.Log;
import org.appcelerator.titanium.util.TiConfig;
import org.appcelerator.titanium.view.TiUIView;

import android.app.Activity;
import android.content.Intent;
import android.os.Message;
import android.os.Messenger;

@Kroll.proxy(creatableInModule=UIModule.class)
public class WindowProxy extends TiWindowProxy
{
	private static final String LCAT = "WindowProxy";
	private static final boolean DBG = TiConfig.LOGD;

	private static final int MSG_FIRST_ID = TiWindowProxy.MSG_LAST_ID + 1;
	private static final int MSG_FINISH_OPEN = MSG_FIRST_ID + 100;
	private static final int MSG_TAB_OPEN = MSG_FIRST_ID + 101;
	protected static final int MSG_LAST_ID = MSG_FIRST_ID + 999;

	ArrayList<TiViewProxy> views;
	WeakReference<Activity> weakActivity;
	String windowId;

	public WindowProxy(TiContext tiContext) {
		super(tiContext);
	}
	
	@Override
	protected KrollDict getLangConversionTable() {
		KrollDict table = new KrollDict();
		table.put("title", "titleid");
		table.put("titlePrompt", "titlepromptid");
		return table;
	}
	

	@Override
	public TiUIView getView(Activity activity) {
		throw new IllegalStateException("call to getView on a Window");
	}

	@Override
	public boolean handleMessage(Message msg) {
		switch(msg.what) {
			case MSG_FINISH_OPEN: {
				realizeViews(getTiContext().getActivity(), view);
				if (tab == null) {
					//TODO attach window
				}
				opened = true;
				fireEvent(TiC.EVENT_OPEN, null);
				return true;
			}
			case MSG_TAB_OPEN : {
				view = new TiUIWindow(this, (Activity) msg.obj);
				realizeViews(null, view);
				opened = true;
				fireEvent(TiC.EVENT_OPEN, null);
				return true;
			}
			default : {
				return super.handleMessage(msg);
			}
		}
	}

	@Override
	protected void handleOpen(KrollDict options) {
		if (DBG) {
			Log.d(LCAT, "handleOpen");
		}

		Messenger messenger = new Messenger(getUIHandler());
		view = new TiUIWindow(this, options, messenger, MSG_FINISH_OPEN);
	}

	public void fillIntentForTab(Intent intent) {
		Messenger messenger = new Messenger(getUIHandler());
		intent.putExtra(TiC.INTENT_PROPERTY_MESSENGER, messenger);
		intent.putExtra(TiC.INTENT_PROPERTY_MESSAGE_ID, MSG_TAB_OPEN);
	}

	@Override
	protected void handleClose(KrollDict options) {
		if (DBG) {
			Log.d(LCAT, "handleClose");
		}

		if (view != null) {
			((TiUIWindow) view).close(options);
		}
		releaseViews();
		opened = false;
	}

	@Kroll.getProperty @Kroll.method
	public TiViewProxy getTab() {
		return tab;
	}

	@Kroll.getProperty @Kroll.method
	public TiViewProxy getTabGroup() {
		return tabGroup;
	}
	
	@Kroll.setProperty @Kroll.method
	public void setLeftNavButton(ButtonProxy button) {
		Log.w(LCAT, "setLeftNavButton not supported in Android");
	}
	
	@Override
	protected Activity handleGetActivity() {
		if (view == null) return null;
		return ((TiUIWindow)view).getActivity();
	}
}
