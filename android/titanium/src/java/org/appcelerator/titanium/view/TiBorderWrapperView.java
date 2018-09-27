/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2012-2017 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.view;

import com.nineoldandroids.view.ViewHelper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;
import android.view.ViewOutlineProvider;
import android.graphics.Outline;

/**
 * This class is a wrapper for Titanium Views with borders. Any view that specifies a border
 * related property will have a border wrapper view to maintain its border.
 */
public class TiBorderWrapperView extends FrameLayout
{
	private static final String TAG = "TiBorderWrapperView";

	private int color = Color.TRANSPARENT;
	private int backgroundColor = Color.TRANSPARENT;
	private float radius = 0;
	private float borderWidth = 0;
	private int alpha = -1;
	private Paint paint;
	private Rect bounds;
	private ViewOutlineProvider viewOutlineProvider;

	public TiBorderWrapperView(Context context)
	{
		super(context);
		setWillNotDraw(false);

		paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		bounds = new Rect();
	}

	@Override
	protected void onDraw(Canvas canvas)
	{
		getDrawingRect(bounds);

		int maxPadding = (int) Math.min(bounds.right / 2, bounds.bottom / 2);
		int padding = (int) Math.min(borderWidth, maxPadding);
		RectF innerRect =
			new RectF(bounds.left + padding, bounds.top + padding, bounds.right - padding, bounds.bottom - padding);
		RectF outerRect = new RectF(bounds);

		paint.setColor(color);
		if (alpha > -1) {
			paint.setAlpha(alpha);
		}

		Path outerPath = new Path();
		if (radius > 0f) {
			//region create paths
			float innerRadius = radius - padding;
			if (innerRadius > 0f) {
				outerPath.addRoundRect(innerRect, innerRadius, innerRadius, Direction.CW);
			} else {
				outerPath.addRect(innerRect, Direction.CW);
			}
			Path innerPath = new Path(outerPath);
			//endregion

			//region draw border
			outerPath.addRoundRect(outerRect, radius, radius, Direction.CCW);
			canvas.drawPath(outerPath, paint);
			//endregion

			//region draw inner path with background color
			// TIMOB-16909: hack to fix anti-aliasing
			if (backgroundColor != Color.TRANSPARENT) {
				paint.setColor(backgroundColor);
				canvas.drawPath(innerPath, paint);
			}
			//endregion

			//region clip inner path
			canvas.clipPath(innerPath);
			//endregion

			//region fill canvas with transparency
			if (backgroundColor != Color.TRANSPARENT) {
				canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
			}
			//endregion

		} else {
			outerPath.addRect(outerRect, Direction.CW);
			outerPath.addRect(innerRect, Direction.CCW);
			canvas.drawPath(outerPath, paint);
			canvas.clipRect(innerRect);
		}

		// TIMOB-20076: set the outline for the view in order to use elevation
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && viewOutlineProvider == null) {
			viewOutlineProvider = new ViewOutlineProvider() {
				@Override
				public void getOutline(View view, Outline outline)
				{
					outline.setRoundRect(bounds, radius);
				}
			};
			setOutlineProvider(viewOutlineProvider);
		}
	}

	public void setColor(int color)
	{
		this.color = color;
	}

	public void setBgColor(int color)
	{
		this.backgroundColor = color;
	}

	public void setRadius(float radius)
	{
		this.radius = radius;
	}

	public void setBorderWidth(float borderWidth)
	{
		this.borderWidth = borderWidth;
	}

	@Override
	public boolean onSetAlpha(int alpha)
	{
		if (Build.VERSION.SDK_INT < 11) {
			/*
			 * TIMOB-17287: This is an ugly hack. ViewHelper.setAlpha does not work on border
			 * when alpha < 1. So we are going to manage alpha animation for ourselves and our
			 * child view manually. This needs to be researched and factored out.
			 */
			this.alpha = alpha;
			if (getChildCount() > 0) {
				ViewHelper.setAlpha(getChildAt(0), alpha / 255.0f);
			}
			return true;
		}
		return false;
	}
}
