/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2018 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui;

import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiC;

import java.util.ArrayList;
import java.util.List;

@Kroll.proxy(creatableInModule = UIModule.class)
public class NavigationWindowProxy extends WindowProxy
{
	private static final String TAG = "NavigationWindowProxy";

	private List<WindowProxy> windows = new ArrayList<>();

	public NavigationWindowProxy()
	{
		super();
	}

	// clang-format off
	@Override
	@Kroll.method
	public void open(@Kroll.argument(optional = true) Object arg)
	// clang-format on
	{
		// FIXME: Shouldn't this complain/blow up if window isn't specified?
		if (getProperties().containsKeyAndNotNull(TiC.PROPERTY_WINDOW)) {
			opened = true;
			WindowProxy window = (WindowProxy) getProperties().get(TiC.PROPERTY_WINDOW);
			openWindow(window, arg);
			return;
		}
		super.open(arg);
	}

	// clang-format off
	@Kroll.method
	public void popToRootWindow(@Kroll.argument(optional = true) Object arg)
	// clang-format on
	{
		// Keep first "root" window
		for (int i = windows.size() - 1; i > 0; i--) {
			WindowProxy window = windows.get(i);
			closeWindow(window, arg);
		}
	}

	// clang-format off
	@Override
	@Kroll.method
	public void close(@Kroll.argument(optional = true) Object arg)
	// clang-format on
	{
		popToRootWindow(arg);
		closeWindow(windows.get(0), arg); // close the root window
		super.close(arg);
	}

	// clang-format off
	@Kroll.method
	public void openWindow(WindowProxy window, @Kroll.argument(optional = true) Object arg)
	// clang-format on
	{
		if (!opened) {
			open(null);
		}
		window.setNavigationWindow(this);
		windows.add(window);
		window.open(arg);
	}

	// clang-format off
	@Kroll.method
	public void closeWindow(WindowProxy window, @Kroll.argument(optional = true) Object arg)
	// clang-format on
	{
		// TODO: If they try to close root window, yell at them:
		// DebugLog(@"[ERROR] Can not close the root window of the NavigationWindow. Close the NavigationWindow instead.");
		windows.remove(window);
		window.close(arg);
		window.setNavigationWindow(null);
	}

	@Override
	public String getApiName()
	{
		return "Ti.UI.NavigationWindow";
	}

	public WindowProxy getRootWindowProxy()
	{
		if (!windows.isEmpty()) {
			return windows.get(0);
		}
		return null;
	}
}
