/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.android;

import java.util.ArrayList;
import java.util.HashMap;

import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.titanium.ITiMenuDispatcherListener;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.proxy.MenuItemProxy;
import org.appcelerator.titanium.proxy.MenuProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiFileHelper;

import android.graphics.drawable.Drawable;
import android.view.Menu;
import android.view.MenuItem;

public class TiMenuDispatchListener implements ITiMenuDispatcherListener {
	protected HashMap<Integer, MenuItemProxy> itemMap = new HashMap<Integer, MenuItemProxy>();
	protected TiContext context;
	protected KrollProxy menuOwner;
	protected TiFileHelper fileHelper;

	public TiMenuDispatchListener(TiContext context, KrollProxy menuOwner) {
		this.context = context;
		this.menuOwner = menuOwner;
		this.fileHelper = new TiFileHelper(context.getActivity());
	}

	protected MenuProxy getMenuProxy() {
		if (menuOwner == null) return null;
		if (menuOwner.hasProperty(TiC.PROPERTY_MENU)) {
			return (MenuProxy) menuOwner.getProperty(TiC.PROPERTY_MENU);
		}
		if (menuOwner instanceof MenuProxy) {
			return (MenuProxy) menuOwner;
		}
		return null;
	}

	@Override
	public boolean dispatchHasMenu() {
		return getMenuProxy() != null;
	}

	@Override
	public boolean dispatchMenuItemSelected(MenuItem item) {
		MenuItemProxy menuItem = itemMap.get(item.getItemId());
		if (menuItem != null) {
			menuItem.fireEvent(TiC.EVENT_CLICK, null);
			return true;
		}
		return false;
	}

	@Override
	public boolean dispatchPrepareMenu(Menu menu) {
		MenuProxy menuProxy = getMenuProxy();
		if (menuProxy == null) return false;

		menu.clear();
		ArrayList<MenuItemProxy> menuItems = menuProxy.getMenuItems();
		itemMap = new HashMap<Integer, MenuItemProxy>(menuItems.size());
		int id = 0;

		for (MenuItemProxy menuItemProxy : menuItems) {
			String title = TiConvert.toString(menuItemProxy.getProperty(TiC.PROPERTY_TITLE));
			if (title != null) {
				MenuItem menuItem = menu.add(0, id, 0, title);
				itemMap.put(id, menuItemProxy);
				id++;

				String iconPath = TiConvert.toString(menuItemProxy.getProperty(TiC.PROPERTY_ICON));
				if (iconPath != null) {
					Drawable d = fileHelper.loadDrawable(context, iconPath, false);
					if (d != null) {
						menuItem.setIcon(d);
					}
				}
			}
		}
		return true;
	}
}
