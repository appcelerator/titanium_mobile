/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui;

import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.view.TiAnimation;

@Kroll.proxy(creatableInModule = UIModule.class,
	propertyAccessors = {
		TiC.PROPERTY_ANCHOR_POINT,
		TiC.PROPERTY_TRANSFORM,
		TiC.PROPERTY_DELAY,
		TiC.PROPERTY_DURATION,
		TiC.PROPERTY_OPACITY,
		TiC.PROPERTY_REPEAT,
		TiC.PROPERTY_AUTOREVERSE,
		TiC.PROPERTY_TOP,
		TiC.PROPERTY_BOTTOM,
		TiC.PROPERTY_LEFT,
		TiC.PROPERTY_RIGHT,
		TiC.PROPERTY_CENTER,
		TiC.PROPERTY_WIDTH,
		TiC.PROPERTY_HEIGHT,
		TiC.PROPERTY_BACKGROUND_COLOR
})
public class AnimationProxy extends TiAnimation
{
	public AnimationProxy()
	{
		defaultValues.put(TiC.PROPERTY_REPEAT, 1);
		defaultValues.put(TiC.PROPERTY_AUTOREVERSE, false);
	}

	@Override
	public String getApiName()
	{
		return "Ti.UI.Animation";
	}
}
