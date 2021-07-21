/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2016 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui;

import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.view.TiUIView;

import ti.modules.titanium.ui.widget.TiUIImageView;
import android.app.Activity;

@Kroll.proxy(creatableInModule = UIModule.class,
	propertyAccessors = {
		TiC.PROPERTY_DECODE_RETRIES,
		TiC.PROPERTY_AUTOROTATE,
		TiC.PROPERTY_DEFAULT_IMAGE,
		TiC.PROPERTY_DURATION,
		TiC.PROPERTY_ENABLE_ZOOM_CONTROLS,
		TiC.PROPERTY_IMAGE,
		TiC.PROPERTY_IMAGE_TOUCH_FEEDBACK,
		TiC.PROPERTY_IMAGE_TOUCH_FEEDBACK_COLOR,
		TiC.PROPERTY_IMAGES,
		TiC.PROPERTY_REPEAT_COUNT
})
public class ImageViewProxy extends ViewProxy
{
	public ImageViewProxy()
	{
		super();
	}

	@Override
	public TiUIView createView(Activity activity)
	{
		return new TiUIImageView(this);
	}

	@Override
	protected TiBlob handleToImage()
	{
		return this.toBlob();
	}

	private TiUIImageView getImageView()
	{
		return (TiUIImageView) getOrCreateView();
	}

	@Kroll.method
	public void start()
	{
		getImageView().start();
	}

	@Kroll.method
	public void stop()
	{
		getImageView().stop();
	}

	@Kroll.method
	public void pause()
	{
		getImageView().pause();
	}

	@Kroll.method
	public void resume()
	{
		getImageView().resume();
	}

	@Kroll.getProperty
	public boolean getAnimating()
	{
		return getImageView().isAnimating();
	}

	@Kroll.getProperty
	public boolean getPaused()
	{
		return getImageView().isPaused();
	}

	@Kroll.getProperty
	public boolean getReverse()
	{
		return getImageView().isReverse();
	}

	@Kroll.setProperty(runOnUiThread = true)
	public void setReverse(boolean reverse)
	{
		getImageView().setReverse(reverse);
	}

	@Kroll.method
	public TiBlob toBlob()
	{
		return getImageView().toBlob();
	}

	@Kroll.setProperty(runOnUiThread = true)
	public void setTintColor(String color)
	{
		getImageView().setTintColor(color);
	}

	@Kroll.getProperty
	public int getTintColor()
	{
		return getImageView().getTintColor();
	}

	@Override
	public String getApiName()
	{
		return "Ti.UI.ImageView";
	}
}
