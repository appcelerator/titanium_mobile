/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget;

import java.lang.ref.WeakReference;

import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiUIHelper;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.ZoomControls;
import android.graphics.PorterDuff.Mode;
import org.appcelerator.titanium.util.TiColorHelper;

public class TiImageView extends ViewGroup implements Handler.Callback, OnClickListener
{
	private static final String TAG = "TiImageView";

	private static final int CONTROL_TIMEOUT = 4000;
	private static final int MSG_HIDE_CONTROLS = 500;

	private Handler handler;

	private OnClickListener clickListener;

	private boolean enableScale;
	private boolean enableZoomControls;

	private GestureDetector gestureDetector;
	private ImageView imageView;
	private ZoomControls zoomControls;

	private float scaleFactor;
	private float scaleIncrement;
	private float scaleMin;
	private float scaleMax;

	private Matrix baseMatrix;
	private Matrix changeMatrix;

	// Flags to help determine whether width/height is defined, so we can scale appropriately
	private boolean viewWidthDefined;
	private boolean viewHeightDefined;

	private int orientation;
	private int tintColor;
	private WeakReference<TiViewProxy> proxy;

	public TiImageView(Context context)
	{
		super(context);

		final TiImageView me = this;

		handler = new Handler(Looper.getMainLooper(), this);

		enableZoomControls = false;
		scaleFactor = 1.0f;
		scaleIncrement = 0.1f;
		scaleMin = 1.0f;
		scaleMax = 5.0f;
		orientation = 0;

		baseMatrix = new Matrix();
		changeMatrix = new Matrix();

		imageView = new ImageView(context);
		addView(imageView);
		setEnableScale(true);

		gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onDown(MotionEvent e)
			{
				if (zoomControls.getVisibility() == View.VISIBLE) {
					super.onDown(e);
					return true;
				} else {
					onClick(me);
					return false;
				}
			}

			@Override
			public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy)
			{
				boolean retValue = false;
				// Allow scrolling only if the image is zoomed in
				if (zoomControls.getVisibility() == View.VISIBLE && scaleFactor > 1) {
					// check if image scroll beyond its borders
					if (!checkImageScrollBeyondBorders(dx, dy)) {
						changeMatrix.postTranslate(-dx, -dy);
						imageView.setImageMatrix(getViewMatrix());
						requestLayout();
						scheduleControlTimeout();
						retValue = true;
					}
				}
				return retValue;
			}

			@Override
			public boolean onSingleTapConfirmed(MotionEvent e)
			{
				onClick(me);
				return super.onSingleTapConfirmed(e);
			}
		});
		gestureDetector.setIsLongpressEnabled(false);

		zoomControls = new ZoomControls(context);
		addView(zoomControls);
		zoomControls.setVisibility(View.GONE);
		zoomControls.setZoomSpeed(75);
		zoomControls.setOnZoomInClickListener(new OnClickListener() {
			public void onClick(View v)
			{
				handleScaleUp();
			}
		});
		zoomControls.setOnZoomOutClickListener(new OnClickListener() {
			public void onClick(View v)
			{
				handleScaleDown();
			}
		});

		super.setOnClickListener(this);
	}

	/**
	 * Constructs a new TiImageView object.
	 * @param context the associated context.
	 * @param proxy the associated proxy.
	 */
	public TiImageView(Context context, TiViewProxy proxy)
	{
		this(context);
		this.proxy = new WeakReference<>(proxy);
	}

	public void setEnableScale(boolean enableScale)
	{
		this.enableScale = enableScale;
		updateScaleType();
	}

	public void setEnableZoomControls(boolean enableZoomControls)
	{
		this.enableZoomControls = enableZoomControls;
		updateScaleType();
	}

	public Drawable getImageDrawable()
	{
		return imageView.getDrawable();
	}

	/**
	 * Sets a Bitmap as the content of imageView
	 * @param bitmap The bitmap to set. If it is null, it will clear the previous image.
	 */
	public void setImageBitmap(Bitmap bitmap)
	{
		if (bitmap == null) {

			// Reset drawable to null.
			// setImageBitmap() will create a drawable that will affect width/height.
			imageView.setImageDrawable(null);
			return;
		}

		imageView.setImageBitmap(bitmap);
	}

	public void setOnClickListener(OnClickListener clickListener)
	{
		this.clickListener = clickListener;
	}

	public boolean handleMessage(Message msg)
	{
		switch (msg.what) {
			case MSG_HIDE_CONTROLS: {
				handleHideControls();
				return true;
			}
		}
		return false;
	}

	public void onClick(View view)
	{
		boolean sendClick = true;
		if (enableZoomControls) {
			if (zoomControls.getVisibility() != View.VISIBLE) {
				sendClick = false;
				manageControls();
				zoomControls.setVisibility(View.VISIBLE);
			}
			scheduleControlTimeout();
		}
		if (sendClick && clickListener != null) {
			clickListener.onClick(view);
		}
	}

	private void handleScaleUp()
	{
		if (scaleFactor < scaleMax) {
			onViewChanged(scaleIncrement);
		}
	}

	private void handleScaleDown()
	{
		if (scaleFactor > scaleMin) {
			onViewChanged(-scaleIncrement);
		}
	}

	private void handleHideControls()
	{
		zoomControls.setVisibility(View.GONE);
	}

	private void manageControls()
	{
		if (scaleFactor == scaleMax) {
			zoomControls.setIsZoomInEnabled(false);
		} else {
			zoomControls.setIsZoomInEnabled(true);
		}

		if (scaleFactor == scaleMin) {
			zoomControls.setIsZoomOutEnabled(false);
		} else {
			zoomControls.setIsZoomOutEnabled(true);
		}
	}

	private void onViewChanged(float dscale)
	{
		updateChangeMatrix(dscale);
		manageControls();
		requestLayout();
		scheduleControlTimeout();
	}

	private void computeBaseMatrix()
	{
		Drawable d = imageView.getDrawable();
		baseMatrix.reset();

		if (d != null) {
			// The base matrix is the matrix that displays the entire image bitmap.
			// It orients the image when orientation is set and scales in X and Y independently,
			// so that src matches dst exactly.
			// This may change the aspect ratio of the src.
			Rect r = new Rect();
			getDrawingRect(r);
			int intrinsicWidth = d.getIntrinsicWidth();
			int intrinsicHeight = d.getIntrinsicHeight();
			int dwidth = intrinsicWidth;
			int dheight = intrinsicHeight;

			if (orientation > 0) {
				baseMatrix.postRotate(orientation);
				if (orientation == 90 || orientation == 270) {
					dwidth = intrinsicHeight;
					dheight = intrinsicWidth;
				}
			}

			float vwidth = getWidth() - getPaddingLeft() - getPaddingRight();
			float vheight = getHeight() - getPaddingTop() - getPaddingBottom();

			RectF dRectF = null;
			RectF vRectF = new RectF(0, 0, vwidth, vheight);
			if (orientation == 0) {
				dRectF = new RectF(0, 0, dwidth, dheight);
			} else if (orientation == 90) {
				dRectF = new RectF(-dwidth, 0, 0, dheight);
			} else if (orientation == 180) {
				dRectF = new RectF(-dwidth, -dheight, 0, 0);
			} else if (orientation == 270) {
				dRectF = new RectF(0, -dheight, dwidth, 0);
			} else {
				Log.e(TAG, "Invalid value for orientation. Cannot compute the base matrix for the image.");
				return;
			}

			Matrix m = new Matrix();
			Matrix.ScaleToFit scaleType;
			if (viewWidthDefined && viewHeightDefined) {
				scaleType = Matrix.ScaleToFit.FILL;
			} else {
				scaleType = Matrix.ScaleToFit.CENTER;
			}
			m.setRectToRect(dRectF, vRectF, scaleType);
			baseMatrix.postConcat(m);
		}
	}

	private void updateChangeMatrix(float dscale)
	{
		changeMatrix.reset();
		scaleFactor += dscale;
		scaleFactor = Math.max(scaleFactor, scaleMin);
		scaleFactor = Math.min(scaleFactor, scaleMax);
		changeMatrix.postScale(scaleFactor, scaleFactor, getWidth() / 2, getHeight() / 2);
	}

	private Matrix getViewMatrix()
	{
		Matrix m = new Matrix(baseMatrix);
		m.postConcat(changeMatrix);
		return m;
	}

	private void scheduleControlTimeout()
	{
		handler.removeMessages(MSG_HIDE_CONTROLS);
		handler.sendEmptyMessageDelayed(MSG_HIDE_CONTROLS, CONTROL_TIMEOUT);
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev)
	{
		boolean handled = false;
		if (enableZoomControls) {
			if (zoomControls.getVisibility() == View.VISIBLE) {
				zoomControls.onTouchEvent(ev);
			}
			handled = gestureDetector.onTouchEvent(ev);
		}
		if (!handled) {
			handled = super.onTouchEvent(ev);
		}
		return handled;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		int maxWidth = 0;
		int maxHeight = 0;

		// If height or width is not defined and we have an image, we need to set the height/width properly
		// so that it doesn't get the content height/width
		if (!viewWidthDefined || !viewHeightDefined) {
			Drawable d = imageView.getDrawable();
			if (d != null) {
				float aspectRatio = 1;
				int w = MeasureSpec.getSize(widthMeasureSpec);
				int h = MeasureSpec.getSize(heightMeasureSpec);

				int ih = d.getIntrinsicHeight();
				int iw = d.getIntrinsicWidth();
				if (ih != 0 && iw != 0) {
					aspectRatio = 1f * ih / iw;
				}

				if (viewWidthDefined) {
					maxWidth = w;
					maxHeight = Math.round(w * aspectRatio);
				}
				if (viewHeightDefined) {
					maxHeight = h;
					maxWidth = Math.round(h / aspectRatio);
				}
			}
		}

		// TODO padding and margins

		measureChild(imageView, widthMeasureSpec, heightMeasureSpec);

		maxWidth = Math.max(maxWidth, imageView.getMeasuredWidth());
		maxHeight = Math.max(maxHeight, imageView.getMeasuredHeight());

		// Allow for zoom controls.
		if (enableZoomControls) {
			measureChild(zoomControls, widthMeasureSpec, heightMeasureSpec);
			maxWidth = Math.max(maxWidth, zoomControls.getMeasuredWidth());
			maxHeight = Math.max(maxHeight, zoomControls.getMeasuredHeight());
		}

		setMeasuredDimension(resolveSize(maxWidth, widthMeasureSpec), resolveSize(maxHeight, heightMeasureSpec));
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom)
	{
		computeBaseMatrix();
		imageView.setImageMatrix(getViewMatrix());

		int parentLeft = 0;
		int parentRight = right - left;
		int parentTop = 0;
		int parentBottom = bottom - top;

		// imageView.layout(parentLeft, parentTop, imageView.getMeasuredWidth(), imageView.getMeasuredHeight());
		imageView.layout(parentLeft, parentTop, parentRight, parentBottom);
		if (enableZoomControls && zoomControls.getVisibility() == View.VISIBLE) {
			int zoomWidth = zoomControls.getMeasuredWidth();
			int zoomHeight = zoomControls.getMeasuredHeight();
			zoomControls.layout(parentRight - zoomWidth, parentBottom - zoomHeight, parentRight, parentBottom);
		}

		TiViewProxy viewProxy = (proxy == null ? null : proxy.get());
		TiUIHelper.firePostLayoutEvent(viewProxy);
	}

	public void setColorFilter(ColorFilter filter)
	{
		imageView.setColorFilter(filter);
	}

	private void updateScaleType()
	{
		if (orientation > 0 || enableZoomControls) {
			imageView.setScaleType(ScaleType.MATRIX);
			imageView.setAdjustViewBounds(false);
		} else {
			if (viewWidthDefined && viewHeightDefined) {
				imageView.setAdjustViewBounds(false);
				imageView.setScaleType(ScaleType.FIT_XY);
			} else if (!enableScale) {
				imageView.setAdjustViewBounds(false);
				imageView.setScaleType(ScaleType.CENTER);
			} else {
				imageView.setAdjustViewBounds(true);
				imageView.setScaleType(ScaleType.FIT_CENTER);
			}
		}
		requestLayout();
	}

	public void setWidthDefined(boolean defined)
	{
		viewWidthDefined = defined;
		updateScaleType();
	}

	public void setHeightDefined(boolean defined)
	{
		viewHeightDefined = defined;
		updateScaleType();
	}

	public void setOrientation(int orientation)
	{
		this.orientation = orientation;
		updateScaleType();
	}

	private boolean checkImageScrollBeyondBorders(float dx, float dy)
	{
		float[] matrixValues = new float[9];
		Matrix m = new Matrix(changeMatrix);
		// Apply the translation
		m.postTranslate(-dx, -dy);
		m.getValues(matrixValues);
		// Image can move only the extra width or height that is available
		// after scaling from the original width or height
		float scaledAdditionalHeight = imageView.getHeight() * (matrixValues[4] - 1);
		float scaledAdditionalWidth = imageView.getWidth() * (matrixValues[0] - 1);
		if (matrixValues[5] > -scaledAdditionalHeight && matrixValues[5] < 0 && matrixValues[2] > -scaledAdditionalWidth
			&& matrixValues[2] < 0) {
			return false;
		}
		return true;
	}

	public void setTintColor(String color)
	{
		if (color == null || color.isEmpty()) {
			imageView.clearColorFilter();
			return;
		}

		this.tintColor = TiColorHelper.parseColor(color);
		imageView.setColorFilter(this.tintColor, Mode.SRC_IN);
	}

	public int getTintColor()
	{
		return tintColor;
	}

	public ImageView getImageView()
	{
		return this.imageView;
	}
}
