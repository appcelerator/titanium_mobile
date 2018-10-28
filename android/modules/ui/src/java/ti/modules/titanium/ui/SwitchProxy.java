/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2016 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui;

import org.appcelerator.kroll.KrollRuntime;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.view.TiUIView;

import ti.modules.titanium.ui.android.AndroidModule;
import ti.modules.titanium.ui.widget.TiUISwitch;
import android.app.Activity;
// clang-format off
@Kroll.proxy(creatableInModule = UIModule.class,
	propertyAccessors = {
		TiC.PROPERTY_VALUE,
		TiC.PROPERTY_STYLE,
		TiC.PROPERTY_TITLE,
		TiC.PROPERTY_TITLE_ON,
		TiC.PROPERTY_TITLE_OFF,
		TiC.PROPERTY_COLOR,
		TiC.PROPERTY_FONT,
		TiC.PROPERTY_TEXT_ALIGN,
		TiC.PROPERTY_VERTICAL_ALIGN
})
// clang-format on
public class SwitchProxy extends TiViewProxy
{
	public SwitchProxy()
	{
		super();
		defaultValues.put(TiC.PROPERTY_VALUE, false);
		defaultValues.put(TiC.PROPERTY_STYLE, AndroidModule.SWITCH_STYLE_SWITCH);
	}

	@Override
	public TiUIView createView(Activity activity)
	{
		return new TiUISwitch(this);
	}

	@Kroll
		.getProperty(writableKeys = true)
		@Kroll.method
		public Object getFont()
	{
		return hasProperty(TiC.PROPERTY_FONT) ? getProperty(TiC.PROPERTY_FONT) : KrollRuntime.UNDEFINED;
	}

	@Override
	public String getApiName()
	{
		return "Ti.UI.Switch";
	}
}
