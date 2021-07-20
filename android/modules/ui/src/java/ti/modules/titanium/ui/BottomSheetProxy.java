/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2021 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui;

import android.app.Activity;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiUIView;

import ti.modules.titanium.ui.widget.TiUIBottomSheetView;

@Kroll.proxy(creatableInModule = UIModule.class,
	propertyAccessors = {
		"peakHeight"
	})
public class BottomSheetProxy extends TiViewProxy
{
	private static final String TAG = "BottomSheetProxy";
	private static int id_toolbar;
	private TiUIBottomSheetView bottomSheet;
	private int peakHeight = 32;

	public BottomSheetProxy()
	{
		super();
		defaultValues.put("peakHeight", peakHeight);
	}

	@Override
	public void handleCreationDict(KrollDict options)
	{
		super.handleCreationDict(options);

		if (options.containsKeyAndNotNull("peakHeight")) {
			peakHeight = TiConvert.toInt(options.get("peakHeight"));
		}

	}

	@Override
	public TiUIView createView(Activity activity)
	{
		bottomSheet = new TiUIBottomSheetView(this);
		return bottomSheet;
	}

	@Kroll.method
	public void toggle()
	{
		bottomSheet.toggle();
	}

	@Kroll.method
	public void collapse()
	{
		bottomSheet.collapse();
	}

	@Kroll.method
	public void expand()
	{
		bottomSheet.expand();
	}

	@Override
	public String getApiName()
	{
		return "Ti.UI.BottomSheet";
	}
}
