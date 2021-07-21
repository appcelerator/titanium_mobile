/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2021 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
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
import java.lang.ref.WeakReference;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiExifOrientation;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.util.TiColorHelper;
import ti.modules.titanium.media.MediaModule;

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

	private int scalingMode = MediaModule.IMAGE_SCALING_AUTO;
	private float scaleFactor;
	private float scaleIncrement;
	private float scaleMin;
	private float scaleMax;

	private Matrix baseMatrix;
	private Matrix changeMatrix;

	// Flags to help determine whether width/height is defined, so we can scale appropriately
	private boolean viewWidthDefined;
	private boolean viewHeightDefined;

	private TiExifOrientation exifOrientation = TiExifOrientation.UPRIGHT;
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

		baseMatrix = new Matrix();
		changeMatrix = new Matrix();

		imageView = new ImageView(context);
		imageView.setAdjustViewBounds(true);
		imageView.setScaleType(ScaleType.MATRIX);
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
		requestLayout();
	}

	public void setEnableZoomControls(boolean enableZoomControls)
	{
		this.enableZoomControls = enableZoomControls;
		updateScaleType();
	}

	public void setScalingMode(int mode)
	{
		if (mode != this.scalingMode) {
			this.scalingMode = mode;
			requestLayout();
		}
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
		// Reset base matrix used to apply "scalingMode" and EXIF orientation to image,
		// but does not have "zoom" scaling/panning applied to it.
		this.baseMatrix.reset();

		// Fetch the image drawable.
		Drawable drawable = imageView.getDrawable();
		if (drawable == null) {
			return;
		}

		// Fetch the image's pixel dimensions.
		boolean isUpright = !this.exifOrientation.isSideways();
		float imageWidth = isUpright ? drawable.getIntrinsicWidth() : drawable.getIntrinsicHeight();
		float imageHeight = isUpright ? drawable.getIntrinsicHeight() : drawable.getIntrinsicWidth();

		// Rotate image to upright position and undo mirroring if needed.
		if (this.exifOrientation != TiExifOrientation.UPRIGHT) {
			this.baseMatrix.postRotate(this.exifOrientation.getDegreesCounterClockwise());
			switch (this.exifOrientation.getDegreesCounterClockwise()) {
				case 90:
					this.baseMatrix.postTranslate(imageWidth, 0);
					break;
				case 180:
					this.baseMatrix.postTranslate(imageWidth, imageHeight);
					break;
				case 270:
					this.baseMatrix.postTranslate(0, imageHeight);
					break;
			}
			if (this.exifOrientation.isMirrored()) {
				this.baseMatrix.postScale(-1.0f, 1.0f, imageWidth * 0.5f, 0.0f);
			}
		}

		// Fetch the pixel width/height of the view hosting the image.
		float viewWidth = getWidth() - getPaddingLeft() - getPaddingRight();
		float viewHeight = getHeight() - getPaddingTop() - getPaddingBottom();

		// Calculate scale factors needed to stretch image to fit width/height of view.
		float scaleX = 0.0f;
		float scaleY = 0.0f;
		if (imageWidth > 0) {
			scaleX = viewWidth / imageWidth;
		}
		if (imageHeight > 0) {
			scaleY = viewHeight / imageHeight;
		}

		// Apply "scalingMode" to image.
		int scalingMode = this.scalingMode;
		if (scalingMode == MediaModule.IMAGE_SCALING_AUTO) {
			if (!this.enableZoomControls && !this.enableScale && this.viewWidthDefined && this.viewHeightDefined) {
				scalingMode = MediaModule.IMAGE_SCALING_FILL;
			} else {
				scalingMode = MediaModule.IMAGE_SCALING_ASPECT_FIT;
			}
		}
		switch (scalingMode) {
			case MediaModule.IMAGE_SCALING_ASPECT_FILL:
			case MediaModule.IMAGE_SCALING_ASPECT_FIT: {
				// Crop or letterbox/pillar-box scale the image.
				boolean isCropped = (scalingMode == MediaModule.IMAGE_SCALING_ASPECT_FILL);
				float scale = isCropped ? Math.max(scaleX, scaleY) : Math.min(scaleX, scaleY);
				this.baseMatrix.postScale(scale, scale);
				this.baseMatrix.postTranslate(
					(viewWidth - (imageWidth * scale)) * 0.5f, (viewHeight - (imageHeight * scale)) * 0.5f);
				break;
			}
			case MediaModule.IMAGE_SCALING_FILL:
				// Stretch the image to fit container.
				this.baseMatrix.postScale(scaleX, scaleY);
				break;
			case MediaModule.IMAGE_SCALING_NONE:
			default:
				// Do not scale the image.
				this.baseMatrix.postTranslate((viewWidth - imageWidth) * 0.5f, (viewHeight - imageHeight) * 0.5f);
				break;
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

		boolean isUpright = !this.exifOrientation.isSideways();
		int maxWidth = 0;
		int maxHeight = 0;

		// If height or width is not defined and we have an image, we need to set the height/width properly
		// so that it doesn't get the content height/width
		if (!viewWidthDefined || !viewHeightDefined) {
			Drawable d = imageView.getDrawable();
			if (d != null) {
				float aspectRatio = 1.0f;
				int w = MeasureSpec.getSize(widthMeasureSpec);
				int h = MeasureSpec.getSize(heightMeasureSpec);

				int ih = isUpright ? d.getIntrinsicHeight() : d.getIntrinsicWidth();
				int iw = isUpright ? d.getIntrinsicWidth() : d.getIntrinsicHeight();
				if (ih != 0 && iw != 0) {
					aspectRatio = (float) ih / (float) iw;
				}

				switch (this.scalingMode) {
					case MediaModule.IMAGE_SCALING_NONE:
					case MediaModule.IMAGE_SCALING_ASPECT_FILL:
						maxWidth = iw;
						maxHeight = ih;
						break;
				}

				if (viewWidthDefined) {
					maxWidth = w;
					if (maxHeight <= 0) {
						maxHeight = Math.round(w * aspectRatio);
					}
				}
				if (viewHeightDefined) {
					maxHeight = h;
					if (maxWidth <= 0) {
						maxWidth = Math.round(h / aspectRatio);
					}
				}
			}
		}

		// TODO padding and margins

		if (isUpright) {
			measureChild(imageView, widthMeasureSpec, heightMeasureSpec);
		} else {
			measureChild(imageView, heightMeasureSpec, widthMeasureSpec);
		}

		maxWidth = Math.max(maxWidth, isUpright ? imageView.getMeasuredWidth() : imageView.getMeasuredHeight());
		maxHeight = Math.max(maxHeight, isUpright ? imageView.getMeasuredHeight() : imageView.getMeasuredWidth());

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
		if (this.enableZoomControls || (this.viewWidthDefined && this.viewHeightDefined)) {
			this.imageView.setAdjustViewBounds(false);
		} else {
			this.imageView.setAdjustViewBounds(true);
		}
		this.imageView.setScaleType(ScaleType.MATRIX);
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

	public void setOrientation(TiExifOrientation exifOrientation)
	{
		if (exifOrientation == null) {
			exifOrientation = TiExifOrientation.UPRIGHT;
		}
		this.exifOrientation = exifOrientation;
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
}
