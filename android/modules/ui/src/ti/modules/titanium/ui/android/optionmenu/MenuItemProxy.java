/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.android.optionmenu;

import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiContext;

@Kroll.proxy(creatableInModule=OptionMenuModule.class)
public class MenuItemProxy extends KrollProxy {

	public MenuItemProxy(TiContext tiContext) {
		super(tiContext);
	}
}
