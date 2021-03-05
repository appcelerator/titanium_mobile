/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget.tabgroup;

import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiWindowProxy;
import org.appcelerator.titanium.view.TiUIView;

import ti.modules.titanium.ui.TabProxy;
import android.app.Activity;
import android.graphics.Color;
import android.view.View;

public class TiUITab extends TiUIView
{

	public TiUITab(TabProxy proxy)
	{
		super(proxy);
		proxy.setView(this);
	}

	/**
	 * Returns the content view for this tab.
	 *
	 * @return the content view or null if the tab is empty
	 */
	public View getContentView()
	{
		TiWindowProxy windowProxy = getWindowProxy();
		if (windowProxy == null || proxy == null) {
			// If no window is provided use an empty view.
			View emptyContent = new View(TiApplication.getInstance().getApplicationContext());
			emptyContent.setBackgroundColor(Color.BLACK);
			return emptyContent;
		}

		// A tab's window should be bound to the tab group's activity.
		// In order for the 'activity' property to work correctly
		// we need to set the content view's activity to that of the group.
		Activity tabGroupActivity = ((TabProxy) proxy).getTabGroup().getActivity();
		windowProxy.setActivity(tabGroupActivity);

		// Assign parent so events bubble up correctly.
		windowProxy.setParent(proxy);

		return windowProxy.getOrCreateView().getOuterView();
	}

	public TiWindowProxy getWindowProxy()
	{
		if (proxy != null) {
			Object windowProxy = proxy.getProperty(TiC.PROPERTY_WINDOW);
			if (windowProxy instanceof TiWindowProxy) {
				return (TiWindowProxy) windowProxy;
			}
		}
		return null;
	}
}
