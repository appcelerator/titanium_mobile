/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2014 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.view;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollObject;
import org.appcelerator.kroll.KrollPropertyChange;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.KrollProxyListener;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiMessenger;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiAnimationBuilder;
import org.appcelerator.titanium.util.TiAnimationBuilder.TiMatrixAnimation;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.view.TiCompositeLayout.LayoutParams;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import android.text.TextUtils;
import android.util.Pair;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.view.animation.Animation;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;

/**
 * This class is for Titanium View implementations, that correspond with TiViewProxy.
 * A TiUIView is responsible for creating and maintaining a native Android View instance.
 */
@SuppressWarnings("deprecation")
public abstract class TiUIView implements KrollProxyListener, OnFocusChangeListener
{
	private static final boolean LOWER_THAN_MARSHMALLOW = (Build.VERSION.SDK_INT < Build.VERSION_CODES.M);

	private static final String TAG = "TiUIView";

	// When distinguishing twofingertap and pinch events, minimum motion (in pixels)
	// to qualify as a scale event.
	private static final float SCALE_THRESHOLD = 6.0f;

	public static final int SOFT_KEYBOARD_DEFAULT_ON_FOCUS = 0;
	public static final int SOFT_KEYBOARD_HIDE_ON_FOCUS = 1;
	public static final int SOFT_KEYBOARD_SHOW_ON_FOCUS = 2;

	public static final int TRANSITION_NONE = 0;
	public static final int TRANSITION_EXPLODE = 1;
	public static final int TRANSITION_FADE_IN = 2;
	public static final int TRANSITION_FADE_OUT = 3;
	public static final int TRANSITION_SLIDE_TOP = 4;
	public static final int TRANSITION_SLIDE_RIGHT = 5;
	public static final int TRANSITION_SLIDE_BOTTOM = 6;
	public static final int TRANSITION_SLIDE_LEFT = 7;
	public static final int TRANSITION_CHANGE_BOUNDS = 8;
	public static final int TRANSITION_CHANGE_CLIP_BOUNDS = 9;
	public static final int TRANSITION_CHANGE_TRANSFORM = 10;
	public static final int TRANSITION_CHANGE_IMAGE_TRANSFORM = 11;

	protected View nativeView; // Native View object

	protected TiViewProxy proxy;
	protected TiViewProxy parent;
	protected ArrayList<TiUIView> children = new ArrayList<>();

	protected LayoutParams layoutParams;
	protected TiAnimationBuilder animBuilder;
	protected TiBackgroundDrawable background;

	public TiBackgroundDrawable getBackground()
	{
		return background;
	}

	protected KrollDict additionalEventData;

	// Since Android doesn't have a property to check to indicate
	// the current animated x/y scale (from a scale animation), we track it here
	// so if another scale animation is done we can gleen the fromX and fromY values
	// rather than starting the next animation always from scale 1.0f (i.e., normal scale).
	// This gives us parity with iPhone for scale animations that use the 2-argument variant
	// of Ti2DMatrix.scale().
	private Pair<Float, Float> animatedScaleValues =
		Pair.create(Float.valueOf(1f), Float.valueOf(1f)); // default = full size (1f)

	// Same for rotation animation and for alpha animation.
	private float animatedRotationDegrees = 0f;    // i.e., no rotation.
	private float animatedAlpha = Float.MIN_VALUE; // i.e., no animated alpha.

	protected KrollDict lastUpEvent = new KrollDict(3);
	private MotionEvent longPressMotionEvent;

	// In the case of heavy-weight windows, the "nativeView" is null,
	// so this holds a reference to the view which is used for touching,
	// i.e., the view passed to registerForTouch.
	private WeakReference<View> touchView = null;

	private boolean zIndexChanged = false;
	protected TiBorderWrapperView borderView;
	// For twofingertap detection
	private boolean didScale = false;

	//to maintain sync visibility between borderview and view. Default is visible
	private int visibility = View.VISIBLE;
	private int hiddenBehavior = View.INVISIBLE;

	protected GestureDetector detector = null;

	private final AtomicBoolean bLayoutPending = new AtomicBoolean();
	private final AtomicBoolean bTransformPending = new AtomicBoolean();

	/**
	 * Constructs a TiUIView object with the associated proxy.
	 * @param proxy the associated proxy.
	 */
	public TiUIView(TiViewProxy proxy)
	{
		this.proxy = proxy;
		this.layoutParams = new TiCompositeLayout.LayoutParams();
	}

	/**
	 * Adds a child view into the ViewGroup.
	 * @param child the view to be added.
	 */
	public void add(TiUIView child)
	{
		add(child, -1);
	}

	/**
	 * Adds a child view into the ViewGroup in specific position.
	 * @param child the view to be added.
	 * @param position position the view to be added.
	 */
	public void insertAt(TiUIView child, int position)
	{
		add(child, position);
	}

	private void add(TiUIView child, int childIndex)
	{
		if (child != null) {
			View cv = child.getOuterView();
			if (cv != null) {
				View nv = getNativeView();
				if (nv instanceof ViewGroup) {
					if (cv.getParent() == null) {
						if (childIndex != -1) {
							((ViewGroup) nv).addView(cv, childIndex, child.getLayoutParams());
						} else {
							((ViewGroup) nv).addView(cv, child.getLayoutParams());
						}
					}
					if (children.contains(child)) {
						children.remove(child);
					}
					if (childIndex == -1) {
						children.add(child);
					} else {
						children.add(childIndex, child);
					}
					child.parent = proxy;
				}
			}
		}
	}

	private int findChildIndex(TiUIView child)
	{
		int idxChild = -1;
		if (child != null) {
			View cv = child.getOuterView();
			if (cv != null) {
				View nv = getNativeView();
				if (nv instanceof ViewGroup) {
					idxChild = ((ViewGroup) nv).indexOfChild(cv);
				}
			}
		}
		return idxChild;
	}

	/**
	 * Removes the child view from the ViewGroup, if child exists.
	 * @param child the view to be removed.
	 */
	public void remove(TiUIView child)
	{
		if (child != null) {
			View cv = child.getOuterView();
			if (cv != null) {
				View nv = getNativeView();
				if (nv instanceof ViewGroup) {
					((ViewGroup) nv).removeView(cv);
					children.remove(child);
					child.parent = null;
				}
			}
		}
	}

	public void setAdditionalEventData(KrollDict dict)
	{
		additionalEventData = dict;
	}

	public KrollDict getAdditionalEventData()
	{
		return additionalEventData;
	}

	/**
	 * @return list of views added.
	 */
	public List<TiUIView> getChildren()
	{
		return children;
	}

	/**
	 * @return the view proxy.
	 */
	public TiViewProxy getProxy()
	{
		return proxy;
	}

	/**
	 * Sets the view proxy.
	 * @param proxy the proxy to set.
	 */
	public void setProxy(TiViewProxy proxy)
	{
		this.proxy = proxy;
	}

	public TiViewProxy getParent()
	{
		return parent;
	}

	public void setParent(TiViewProxy parent)
	{
		this.parent = parent;
	}

	/**
	 * @return the view's layout params.
	 */
	public LayoutParams getLayoutParams()
	{
		return layoutParams;
	}

	/**
	 * Gets the native view's internal content view used to host child views.
	 * Called by the convertPointToView() method to convert coordinates.
	 * <p>
	 * Overridden by ScrollView since its content view is typically larger than the parent view.
	 * <p>
	 * For most other views, this method returns same view as getNativeView() method.
	 * @return Returns the native content view used to host child views.
	 */
	public View getNativeContentView()
	{
		return getNativeView();
	}

	/**
	 * @return the Android native view.
	 */
	public View getNativeView()
	{
		return nativeView;
	}

	/**
	 * Sets the nativeView to view.
	 * @param view the view to set
	 */
	protected void setNativeView(View view)
	{
		if (view instanceof TiBorderWrapperView) {
			this.borderView = (TiBorderWrapperView) view;
			view = this.borderView.getChildAt(0);
		}

		this.nativeView = view;
		boolean clickable = true;

		if (proxy.hasProperty(TiC.PROPERTY_TOUCH_ENABLED)) {
			clickable = TiConvert.toBoolean(proxy.getProperty(TiC.PROPERTY_TOUCH_ENABLED), true);
		}
		doSetClickable(nativeView, clickable);
		nativeView.setOnFocusChangeListener(this);

		applyAccessibilityProperties();
	}

	protected void setLayoutParams(LayoutParams layoutParams)
	{
		this.layoutParams = layoutParams;
	}

	/**
	 * Animates the view if there are pending animations.
	 */
	public void animate()
	{
		View outerView = getOuterView();
		if (outerView == null || bTransformPending.get()) {
			return;
		}

		TiAnimationBuilder builder = proxy.getPendingAnimation();
		if (builder == null) {
			return;
		}

		proxy.clearAnimation(builder);

		// If a view is "visible" but not currently seen (such as because it's covered or
		// its position is currently set to be fully outside its parent's region),
		// then Android might not animate it immediately because by default it animates
		// "on first frame" and apparently "first frame" won't happen right away if the
		// view has no visible rectangle on screen.  In that case invalidate its parent, which will
		// kick off the pending animation.
		boolean invalidateParent = false;
		ViewParent viewParent = outerView.getParent();

		if (this.visibility == View.VISIBLE && viewParent instanceof View) {
			int width = outerView.getWidth();
			int height = outerView.getHeight();

			if (width == 0 || height == 0) {
				// Could be animating from nothing to something
				invalidateParent = true;
			} else {
				Rect r = new Rect(0, 0, width, height);
				Point p = new Point(0, 0);
				invalidateParent = !(viewParent.getChildVisibleRect(outerView, r, p));
			}
		}

		if (Log.isDebugModeEnabled()) {
			Log.d(TAG, "starting animation", Log.DEBUG_MODE);
		}

		builder.start(proxy, outerView);

		if (invalidateParent) {
			((View) viewParent).postInvalidate();
		}
	}

	public void listenerAdded(String type, int count, KrollProxy proxy)
	{
	}

	public void listenerRemoved(String type, int count, KrollProxy proxy)
	{
	}

	protected boolean hasImage(KrollDict d)
	{
		return d.containsKeyAndNotNull(TiC.PROPERTY_BACKGROUND_IMAGE)
			|| d.containsKeyAndNotNull(TiC.PROPERTY_BACKGROUND_SELECTED_IMAGE)
			|| d.containsKeyAndNotNull(TiC.PROPERTY_BACKGROUND_FOCUSED_IMAGE)
			|| d.containsKeyAndNotNull(TiC.PROPERTY_BACKGROUND_DISABLED_IMAGE);
	}

	private boolean hasRepeat(KrollDict d)
	{
		return d.containsKeyAndNotNull(TiC.PROPERTY_BACKGROUND_REPEAT);
	}

	protected boolean hasGradient(KrollDict d)
	{
		return d.containsKeyAndNotNull(TiC.PROPERTY_BACKGROUND_GRADIENT);
	}

	protected boolean hasBorder(KrollDict d)
	{
		return d.containsKeyAndNotNull(TiC.PROPERTY_BORDER_COLOR)
			|| (d.containsKeyAndNotNull(TiC.PROPERTY_BORDER_WIDTH)
				&& TiConvert.toTiDimension(d.getString(TiC.PROPERTY_BORDER_WIDTH), TiDimension.TYPE_WIDTH).getValue()
					   > 0f)
			|| (d.containsKeyAndNotNull(TiC.PROPERTY_BORDER_RADIUS));
	}

	protected boolean hasColorState(KrollDict d)
	{
		return d.containsKeyAndNotNull(TiC.PROPERTY_BACKGROUND_SELECTED_COLOR)
			|| d.containsKeyAndNotNull(TiC.PROPERTY_BACKGROUND_FOCUSED_COLOR)
			|| d.containsKeyAndNotNull(TiC.PROPERTY_BACKGROUND_DISABLED_COLOR);
	}

	public float[] getPreTranslationValue(float[] points)
	{
		if (layoutParams.optionTransform != null) {
			TiMatrixAnimation matrixAnimation = animBuilder.createMatrixAnimation(layoutParams.optionTransform);
			int width = getNativeView().getWidth();
			int height = getNativeView().getHeight();
			Matrix m = matrixAnimation.getFinalMatrix(width, height);
			// Get the translation values
			float[] values = new float[9];
			m.getValues(values);
			points[0] = points[0] - values[2];
			points[1] = points[1] - values[5];
		}
		return points;
	}

	protected void applyTransform(Ti2DMatrix matrix)
	{
		layoutParams.optionTransform = matrix;
		if (animBuilder == null) {
			animBuilder = new TiAnimationBuilder();
		}

		View outerView = getOuterView();
		if (outerView == null) {
			return;
		}

		boolean clearTransform = (matrix == null);
		Ti2DMatrix matrixApply = matrix; // To not change original.

		if (clearTransform) {
			outerView.clearAnimation();
			// Since we may have used property animators, which
			// do not set the animation property of a view,
			// we should also quickly apply a matrix with
			// no rotation, no rotation and scale of 1.
			matrixApply =
				(new Ti2DMatrix()).rotate(new Object[] { 0d }).translate(0d, 0d).scale(new Object[] { 1d, 1d });
		}

		HashMap<String, Object> options = new HashMap<>(2);
		options.put(TiC.PROPERTY_TRANSFORM, matrixApply);
		options.put(TiC.PROPERTY_DURATION, 1);

		animBuilder.applyOptions(options);

		// When using property Animators, we can only use absolute values to specify the anchor point, eg. "50px".
		// Therefore, we must start the transformation after the layout pass when we get the height and width of the view.
		if (animBuilder.isUsingPropertyAnimators()) {
			startTransformAfterLayout(outerView);
			//If the layout already done, the above call won't trigger the change, force layout change.
			outerView.requestLayout();
		} else {
			animBuilder.start(this.proxy, outerView);
		}
	}

	/**
	 * When using property Animators, we start the transformation after the layout pass.
	 * @param v the view to animate
	 */
	protected void startTransformAfterLayout(final View v)
	{
		final TiViewProxy p = this.proxy;
		bTransformPending.set(true);
		OnGlobalLayoutListener layoutListener = new OnGlobalLayoutListener() {
			public void onGlobalLayout()
			{
				animBuilder.setCallback(new KrollFunction() {
					public Object call(KrollObject krollObject, HashMap args)
					{
						return null;
					}
					public Object call(KrollObject krollObject, Object[] args)
					{
						return null;
					}
					public void callAsync(KrollObject krollObject, HashMap args)
					{
					}
					public void callAsync(KrollObject krollObject, Object[] args)
					{
						bTransformPending.set(false);
						p.handlePendingAnimation(true);
					}
				});
				animBuilder.start(p, v);
				try {
					v.getViewTreeObserver().removeOnGlobalLayoutListener(this);
				} catch (IllegalStateException e) {
					if (Log.isDebugModeEnabled()) {
						Log.w(TAG, "Unable to remove the OnGlobalLayoutListener.", e.getMessage());
					}
				}
			}
		};
		v.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);

		// The view will visibly transform if the transformation starts after the layout pass.
		// So, we add OnPreDrawListener to skip the drawing pass before the animation is ended.
		final OnPreDrawListener preDrawListener = new OnPreDrawListener() {
			@Override
			public boolean onPreDraw()
			{
				if (TiAnimationBuilder.isAnimationRunningFor(v)) {
					// Skip the current drawing pass.
					return false;
				}
				try {
					v.getViewTreeObserver().removeOnPreDrawListener(this);
				} catch (IllegalStateException e) {
					if (Log.isDebugModeEnabled()) {
						Log.w(TAG, "Unable to remove the OnPreDrawListener.", e.getMessage());
					}
				}
				return true;
			}
		};
		v.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
	}

	public void forceLayoutNativeView(boolean informParent)
	{
		layoutNativeView(informParent);
	}

	protected void layoutNativeView()
	{
		layoutNativeView(false);
	}

	public boolean isLayoutPending()
	{
		return bLayoutPending.get();
	}

	protected void layoutNativeView(boolean informParent)
	{
		if (nativeView != null) {
			Animation a = nativeView.getAnimation();
			if (a instanceof TiMatrixAnimation) {
				TiMatrixAnimation matrixAnimation = (TiMatrixAnimation) a;
				matrixAnimation.invalidateWithMatrix(nativeView);
			}
			if (informParent) {
				if (parent != null) {
					TiUIView uiv = parent.peekView();
					if (uiv != null) {
						uiv.resort();
					}
				}
			}
			final View v = getOuterView();
			if (v != null) {
				bLayoutPending.set(true);
				OnGlobalLayoutListener layoutListener = new OnGlobalLayoutListener() {
					@Override
					public void onGlobalLayout()
					{
						bLayoutPending.set(false);
						try {
							v.getViewTreeObserver().removeOnGlobalLayoutListener(this);
						} catch (IllegalStateException e) {
							if (Log.isDebugModeEnabled()) {
								Log.w(TAG, "Unable to remove the OnGlobalLayoutListener.", e.getMessage());
							}
						}
					}
				};
				v.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
			}

			nativeView.requestLayout();
		}
	}

	public void resort()
	{
		View v = getNativeView();
		if (v instanceof TiCompositeLayout) {
			((TiCompositeLayout) v).resort();
		}
	}
	public boolean iszIndexChanged()
	{
		return zIndexChanged;
	}

	public void setzIndexChanged(boolean zIndexChanged)
	{
		this.zIndexChanged = zIndexChanged;
	}

	/**
	 * Property animations may affect translation values.
	 * We need to reset translationX when 'left', 'right', or 'center' property is changed.
	 */
	private void resetTranslationX()
	{
		if (nativeView != null) {
			nativeView.setTranslationX(0);
		}
	}

	/**
	 * Property animations may affect translation values.
	 * We need to reset translationX when 'top', 'bottom', or 'center' property is changed.
	 */
	private void resetTranslationY()
	{
		if (nativeView != null) {
			nativeView.setTranslationY(0);
		}
	}

	public void propertyChanged(String key, Object oldValue, Object newValue, KrollProxy proxy)
	{
		if (key.equals(TiC.PROPERTY_LEFT)) {
			resetPostAnimationValues();
			resetTranslationX();
			if (newValue != null) {
				layoutParams.optionLeft = TiConvert.toTiDimension(TiConvert.toString(newValue), TiDimension.TYPE_LEFT);
			} else {
				layoutParams.optionLeft = null;
			}
			layoutNativeView();
		} else if (key.equals(TiC.PROPERTY_TOP)) {
			resetPostAnimationValues();
			resetTranslationY();
			if (newValue != null) {
				layoutParams.optionTop = TiConvert.toTiDimension(TiConvert.toString(newValue), TiDimension.TYPE_TOP);
			} else {
				layoutParams.optionTop = null;
			}
			layoutNativeView();
		} else if (key.equals(TiC.PROPERTY_CENTER)) {
			resetPostAnimationValues();
			resetTranslationX();
			resetTranslationY();
			TiConvert.updateLayoutCenter(newValue, layoutParams);
			layoutNativeView();
		} else if (key.equals(TiC.PROPERTY_RIGHT)) {
			resetPostAnimationValues();
			resetTranslationX();
			if (newValue != null) {
				layoutParams.optionRight =
					TiConvert.toTiDimension(TiConvert.toString(newValue), TiDimension.TYPE_RIGHT);
			} else {
				layoutParams.optionRight = null;
			}
			layoutNativeView();
		} else if (key.equals(TiC.PROPERTY_BOTTOM)) {
			resetPostAnimationValues();
			resetTranslationY();
			if (newValue != null) {
				layoutParams.optionBottom =
					TiConvert.toTiDimension(TiConvert.toString(newValue), TiDimension.TYPE_BOTTOM);
			} else {
				layoutParams.optionBottom = null;
			}
			layoutNativeView();
		} else if (key.equals(TiC.PROPERTY_SIZE)) {
			if (newValue instanceof HashMap) {
				@SuppressWarnings("unchecked")
				HashMap<String, Object> d = (HashMap<String, Object>) newValue;
				propertyChanged(TiC.PROPERTY_WIDTH, oldValue, d.get(TiC.PROPERTY_WIDTH), proxy);
				propertyChanged(TiC.PROPERTY_HEIGHT, oldValue, d.get(TiC.PROPERTY_HEIGHT), proxy);
			} else if (newValue != null) {
				Log.w(TAG, "Unsupported property type (" + (newValue.getClass().getSimpleName()) + ") for key: " + key
							   + ". Must be an object/dictionary");
			}
		} else if (key.equals(TiC.PROPERTY_HEIGHT)) {
			resetPostAnimationValues();
			if (newValue != null) {
				layoutParams.optionHeight = null;
				layoutParams.sizeOrFillHeightEnabled = true;
				if (newValue.equals(TiC.LAYOUT_SIZE)) {
					layoutParams.autoFillsHeight = false;
				} else if (newValue.equals(TiC.LAYOUT_FILL)) {
					layoutParams.autoFillsHeight = true;
				} else if (!newValue.equals(TiC.SIZE_AUTO)) {
					layoutParams.optionHeight =
						TiConvert.toTiDimension(TiConvert.toString(newValue), TiDimension.TYPE_HEIGHT);
					layoutParams.sizeOrFillHeightEnabled = false;
				}
			} else {
				layoutParams.optionHeight = null;
			}
			layoutNativeView();
		} else if (key.equals(TiC.PROPERTY_HORIZONTAL_WRAP)) {
			if (nativeView instanceof TiCompositeLayout) {
				((TiCompositeLayout) nativeView).setEnableHorizontalWrap(TiConvert.toBoolean(newValue, true));
			}
			layoutNativeView();
		} else if (key.equals(TiC.PROPERTY_WIDTH)) {
			resetPostAnimationValues();
			if (newValue != null) {
				layoutParams.optionWidth = null;
				layoutParams.sizeOrFillWidthEnabled = true;
				if (newValue.equals(TiC.LAYOUT_SIZE)) {
					layoutParams.autoFillsWidth = false;
				} else if (newValue.equals(TiC.LAYOUT_FILL)) {
					layoutParams.autoFillsWidth = true;
				} else if (!newValue.equals(TiC.SIZE_AUTO)) {
					layoutParams.optionWidth =
						TiConvert.toTiDimension(TiConvert.toString(newValue), TiDimension.TYPE_WIDTH);
					layoutParams.sizeOrFillWidthEnabled = false;
				}
			} else {
				layoutParams.optionWidth = null;
			}
			layoutNativeView();
		} else if (key.equals(TiC.PROPERTY_LAYOUT)) {
			String layout = TiConvert.toString(newValue);
			if (nativeView instanceof TiCompositeLayout) {
				resetPostAnimationValues();
				((TiCompositeLayout) nativeView).setLayoutArrangement(layout);
				layoutNativeView();
			}
		} else if (key.equals(TiC.PROPERTY_ZINDEX)) {
			if (newValue != null) {
				layoutParams.optionZIndex = TiConvert.toInt(newValue);
			} else {
				layoutParams.optionZIndex = 0;
			}
			layoutNativeView(true);
		} else if (key.equals(TiC.PROPERTY_FOCUSABLE) && newValue != null) {
			registerForKeyPress(nativeView, TiConvert.toBoolean(newValue, false));
		} else if (key.equals(TiC.PROPERTY_TOUCH_ENABLED)) {
			nativeView.setEnabled(TiConvert.toBoolean(newValue));
			doSetClickable(TiConvert.toBoolean(newValue));
		} else if (key.equals(TiC.PROPERTY_FILTER_TOUCHES_WHEN_OBSCURED)) {
			setFilterTouchesWhenObscured(TiConvert.toBoolean(newValue, false));
		} else if (key.equals(TiC.PROPERTY_VISIBLE)) {
			newValue = (newValue == null) ? false : newValue;
			this.setVisibility(TiConvert.toBoolean(newValue) ? View.VISIBLE : View.INVISIBLE);
		} else if (key.equals(TiC.PROPERTY_ENABLED)) {
			nativeView.setEnabled(TiConvert.toBoolean(newValue));
		} else if (key.startsWith(TiC.PROPERTY_BACKGROUND_PADDING)) {
			Log.i(TAG, key + " not yet implemented.");
		} else if (key.equals(TiC.PROPERTY_OPACITY) || key.equals(TiC.PROPERTY_TOUCH_FEEDBACK_COLOR)
				   || key.equals(TiC.PROPERTY_TOUCH_FEEDBACK) || key.startsWith(TiC.PROPERTY_BACKGROUND_PREFIX)
				   || key.startsWith(TiC.PROPERTY_BORDER_PREFIX)) {
			// Update first before querying.
			proxy.setProperty(key, newValue);

			KrollDict d = proxy.getProperties();

			boolean hasImage = hasImage(d);
			boolean hasRepeat = hasRepeat(d);
			boolean hasColorState = hasColorState(d);
			boolean hasBorder = hasBorder(d);
			boolean hasGradient = hasGradient(d);
			boolean requiresCustomBackground = hasImage || hasColorState || hasBorder || hasGradient;

			// PROPERTY_BACKGROUND_REPEAT is implicitly passed as false though not used in JS. So check the truth value and proceed.
			if (!requiresCustomBackground) {
				requiresCustomBackground =
					requiresCustomBackground && d.optBoolean(TiC.PROPERTY_BACKGROUND_REPEAT, false);
			}

			if (!requiresCustomBackground) {
				if (background != null) {
					background.releaseDelegate();
					background.setCallback(null);
					background = null;
				}

				if (this.nativeView != null) {
					if (d.containsKeyAndNotNull(TiC.PROPERTY_BACKGROUND_COLOR)) {
						this.nativeView.setBackgroundColor(TiConvert.toColor(d, TiC.PROPERTY_BACKGROUND_COLOR));
					} else {
						this.nativeView.setBackground(null);
					}
				}
			} else {
				boolean newBackground = background == null;
				if (newBackground) {
					background = new TiBackgroundDrawable();
				}

				Integer bgColor = null;

				if (!hasColorState && !hasGradient) {
					if (d.get(TiC.PROPERTY_BACKGROUND_COLOR) != null) {
						bgColor = TiConvert.toColor(d, TiC.PROPERTY_BACKGROUND_COLOR);
						if (newBackground
							|| (key.equals(TiC.PROPERTY_OPACITY) || key.equals(TiC.PROPERTY_BACKGROUND_COLOR))) {
							background.setBackgroundColor(bgColor);
						}
					}
				}

				if (hasImage || hasRepeat || hasColorState || hasGradient) {
					if (newBackground || key.equals(TiC.PROPERTY_OPACITY)
						|| key.startsWith(TiC.PROPERTY_BACKGROUND_PREFIX)) {
						handleBackgroundImage(d);
					}
				}

				if (hasBorder) {
					if (borderView == null && parent != null) {
						// Since we have to create a new border wrapper view, we need to remove this view, and re-add
						// it.
						// This will ensure the border wrapper view is added correctly.
						TiUIView parentView = parent.getOrCreateView();
						if (parentView != null) {
							int removedChildIndex = parentView.findChildIndex(this);
							parentView.remove(this);
							initializeBorder(d, bgColor);
							if (removedChildIndex == -1) {
								parentView.add(this);
							} else {
								parentView.add(this, removedChildIndex);
							}
						}
					} else if (key.startsWith(TiC.PROPERTY_BORDER_PREFIX)) {
						handleBorderProperty(key, newValue);
					}

					// TIMOB-24898: disable HW acceleration to allow transparency
					// when the backgroundColor alpha channel has been set
					if ((bgColor != null) && (Color.alpha(bgColor) < 255)) {
						disableHWAcceleration();
					}
				}

				applyCustomBackground();
			}
			if (canApplyTouchFeedback(d)) {
				String colorString = TiConvert.toString(d.get(TiC.PROPERTY_TOUCH_FEEDBACK_COLOR));
				applyTouchFeedback((colorString != null) ? TiConvert.toColor(colorString) : null);
			}
			if (key.equals(TiC.PROPERTY_OPACITY)) {
				setOpacity(TiConvert.toFloat(newValue, 1f));
			}
			if (this.nativeView != null) {
				this.nativeView.postInvalidate();
			}
		} else if (key.equals(TiC.PROPERTY_SOFT_KEYBOARD_ON_FOCUS)) {
			Log.w(TAG,
				  "Focus state changed to " + TiConvert.toString(newValue) + " not honored until next focus event.",
				  Log.DEBUG_MODE);
		} else if (key.equals(TiC.PROPERTY_TRANSFORM)) {
			if (nativeView != null) {
				applyTransform((Ti2DMatrix) newValue);
			}
		} else if (key.equals(TiC.PROPERTY_KEEP_SCREEN_ON)) {
			if (nativeView != null) {
				nativeView.setKeepScreenOn(TiConvert.toBoolean(newValue));
			}

		} else if (key.indexOf("accessibility") == 0 && !key.equals(TiC.PROPERTY_ACCESSIBILITY_HIDDEN)) {
			applyContentDescription();

		} else if (key.equals(TiC.PROPERTY_ACCESSIBILITY_HIDDEN)) {
			applyAccessibilityHidden(newValue);

		} else if (key.equals(TiC.PROPERTY_ELEVATION)) {
			if (getOuterView() != null) {
				ViewCompat.setElevation(getOuterView(), TiConvert.toFloat(newValue));
			}
		} else if (key.equals(TiC.PROPERTY_ANCHOR_POINT)) {
			if (getOuterView() != null) {
				if (newValue instanceof HashMap) {
					setAnchor((HashMap) newValue);
				} else {
					Log.e(TAG, "Invalid argument type for anchorPoint property. Ignoring");
				}
			}
		} else if (key.equals(TiC.PROPERTY_TRANSLATION_X)) {
			if (getOuterView() != null) {
				ViewCompat.setTranslationX(getOuterView(), TiConvert.toFloat(newValue));
			}
		} else if (key.equals(TiC.PROPERTY_TRANSLATION_Y)) {
			if (getOuterView() != null) {
				ViewCompat.setTranslationY(getOuterView(), TiConvert.toFloat(newValue));
			}
		} else if (key.equals(TiC.PROPERTY_TRANSLATION_Z)) {
			if (getOuterView() != null) {
				ViewCompat.setTranslationZ(getOuterView(), TiConvert.toFloat(newValue));
			}
		} else if (key.equals(TiC.PROPERTY_TRANSITION_NAME)) {
			if (nativeView != null) {
				ViewCompat.setTransitionName(nativeView, TiConvert.toString(newValue));
			}
		} else if (key.equals(TiC.PROPERTY_SCALE_X)) {
			if (getOuterView() != null) {
				ViewCompat.setScaleX(getOuterView(), TiConvert.toFloat(newValue));
			}
		} else if (key.equals(TiC.PROPERTY_SCALE_Y)) {
			if (getOuterView() != null) {
				ViewCompat.setScaleY(getOuterView(), TiConvert.toFloat(newValue));
			}
		} else if (key.equals(TiC.PROPERTY_ROTATION)) {
			if (getOuterView() != null) {
				ViewCompat.setRotation(getOuterView(), TiConvert.toFloat(newValue));
			}
		} else if (key.equals(TiC.PROPERTY_ROTATION_X)) {
			if (getOuterView() != null) {
				ViewCompat.setRotationX(getOuterView(), TiConvert.toFloat(newValue));
			}
		} else if (key.equals(TiC.PROPERTY_ROTATION_Y)) {
			if (getOuterView() != null) {
				ViewCompat.setRotationY(getOuterView(), TiConvert.toFloat(newValue));
			}
		} else if (key.equals(TiC.PROPERTY_HIDDEN_BEHAVIOR)) {
			hiddenBehavior = TiConvert.toInt(newValue, View.INVISIBLE);
		} else if (Log.isDebugModeEnabled()) {
			Log.d(TAG, "Unhandled property key: " + key, Log.DEBUG_MODE);
		}
	}

	public void processProperties(KrollDict d)
	{
		if (d == null) {
			return;
		}

		boolean nativeViewNull = false;
		if (nativeView == null) {
			nativeViewNull = true;
			Log.d(TAG, "Nativeview is null", Log.DEBUG_MODE);
		}
		if (d.containsKey(TiC.PROPERTY_LAYOUT)) {
			String layout = TiConvert.toString(d, TiC.PROPERTY_LAYOUT);
			if (nativeView instanceof TiCompositeLayout) {
				((TiCompositeLayout) nativeView).setLayoutArrangement(layout);
			}
		}
		if (TiConvert.fillLayout(d, layoutParams) && !nativeViewNull) {
			nativeView.requestLayout();
		}

		if (d.containsKey(TiC.PROPERTY_HORIZONTAL_WRAP)) {
			if (nativeView instanceof TiCompositeLayout) {
				((TiCompositeLayout) nativeView)
					.setEnableHorizontalWrap(TiConvert.toBoolean(d, TiC.PROPERTY_HORIZONTAL_WRAP, true));
			}
		}

		Integer bgColor = null;

		// Default background processing.
		// Prefer image to color.
		if (hasImage(d) || hasColorState(d) || hasGradient(d)) {
			handleBackgroundImage(d);

		} else if (d.containsKey(TiC.PROPERTY_BACKGROUND_COLOR) && !nativeViewNull) {
			// Set the background color on the view directly only if there is no border.
			// If border is present, then we must use the TiBackgroundDrawable.
			bgColor = TiConvert.toColor(d, TiC.PROPERTY_BACKGROUND_COLOR);
			if (hasBorder(d)) {
				if (background == null) {
					applyCustomBackground(false);
				}
				background.setBackgroundColor(bgColor);
			} else {
				nativeView.setBackgroundColor(bgColor);
			}
		}
		if (canApplyTouchFeedback(d)) {
			String colorString = TiConvert.toString(d.get(TiC.PROPERTY_TOUCH_FEEDBACK_COLOR));
			applyTouchFeedback((colorString != null) ? TiConvert.toColor(colorString) : null);
		}

		if (d.containsKey(TiC.PROPERTY_FILTER_TOUCHES_WHEN_OBSCURED) && !nativeViewNull) {
			setFilterTouchesWhenObscured(
				TiConvert.toBoolean(d.get(TiC.PROPERTY_FILTER_TOUCHES_WHEN_OBSCURED), false));
		}

		if (d.containsKey(TiC.PROPERTY_HIDDEN_BEHAVIOR) && !nativeViewNull) {
			Object hidden = d.get(TiC.PROPERTY_HIDDEN_BEHAVIOR);
			if (hidden != null) {
				hiddenBehavior = TiConvert.toInt(hidden, View.INVISIBLE);
			} else {
				hiddenBehavior = View.INVISIBLE;
			}
		}
		if (d.containsKey(TiC.PROPERTY_VISIBLE) && !nativeViewNull) {
			Object visible = d.get(TiC.PROPERTY_VISIBLE);
			if (visible != null) {
				setVisibility(TiConvert.toBoolean(visible, true) ? View.VISIBLE : View.INVISIBLE);
			} else {
				setVisibility(View.INVISIBLE);
			}
		}
		if (d.containsKey(TiC.PROPERTY_ENABLED) && !nativeViewNull) {
			nativeView.setEnabled(TiConvert.toBoolean(d, TiC.PROPERTY_ENABLED, true));
		}

		initializeBorder(d, bgColor);

		if (d.containsKey(TiC.PROPERTY_OPACITY) && !nativeViewNull) {
			setOpacity(TiConvert.toFloat(d, TiC.PROPERTY_OPACITY, 1f));
		}

		if (d.containsKey(TiC.PROPERTY_TRANSFORM)) {
			Ti2DMatrix matrix = (Ti2DMatrix) d.get(TiC.PROPERTY_TRANSFORM);
			if (matrix != null) {
				applyTransform(matrix);
			}
		}

		if (d.containsKey(TiC.PROPERTY_KEEP_SCREEN_ON) && !nativeViewNull) {
			nativeView.setKeepScreenOn(TiConvert.toBoolean(d, TiC.PROPERTY_KEEP_SCREEN_ON, false));
		}

		if (d.containsKey(TiC.PROPERTY_ACCESSIBILITY_HINT) || d.containsKey(TiC.PROPERTY_ACCESSIBILITY_LABEL)
			|| d.containsKey(TiC.PROPERTY_ACCESSIBILITY_VALUE)) {
			applyContentDescription(d);
		}

		if (d.containsKey(TiC.PROPERTY_ACCESSIBILITY_HIDDEN)) {
			applyAccessibilityHidden(d.get(TiC.PROPERTY_ACCESSIBILITY_HIDDEN));
		}

		if (d.containsKey(TiC.PROPERTY_ELEVATION) && !nativeViewNull) {
			ViewCompat.setElevation(getOuterView(), TiConvert.toFloat(d, TiC.PROPERTY_ELEVATION));
		}

		if (d.containsKey(TiC.PROPERTY_ANCHOR_POINT) && !nativeViewNull) {
			Object value = d.get(TiC.PROPERTY_ANCHOR_POINT);
			if (value instanceof HashMap) {
				setAnchor((HashMap) d);
			} else {
				Log.e(TAG, "Invalid argument type for anchorPoint property. Ignoring");
			}
		}

		if (d.containsKey(TiC.PROPERTY_ROTATION) && !nativeViewNull) {
			ViewCompat.setRotation(nativeView, TiConvert.toFloat(d, TiC.PROPERTY_ROTATION));
		}

		if (d.containsKey(TiC.PROPERTY_ROTATION_X) && !nativeViewNull) {
			ViewCompat.setRotationX(nativeView, TiConvert.toFloat(d, TiC.PROPERTY_ROTATION_X));
		}

		if (d.containsKey(TiC.PROPERTY_ROTATION_Y) && !nativeViewNull) {
			ViewCompat.setRotationY(nativeView, TiConvert.toFloat(d, TiC.PROPERTY_ROTATION_Y));
		}

		if (d.containsKey(TiC.PROPERTY_SCALE_X) && !nativeViewNull) {
			ViewCompat.setScaleX(nativeView, TiConvert.toFloat(d, TiC.PROPERTY_SCALE_X));
		}

		if (d.containsKey(TiC.PROPERTY_SCALE_Y) && !nativeViewNull) {
			ViewCompat.setScaleY(nativeView, TiConvert.toFloat(d, TiC.PROPERTY_SCALE_Y));
		}

		if (d.containsKey(TiC.PROPERTY_TRANSLATION_X) && !nativeViewNull) {
			ViewCompat.setTranslationX(nativeView, TiConvert.toFloat(d, TiC.PROPERTY_TRANSLATION_X));
		}

		if (d.containsKey(TiC.PROPERTY_TRANSLATION_Y) && !nativeViewNull) {
			ViewCompat.setTranslationY(nativeView, TiConvert.toFloat(d, TiC.PROPERTY_TRANSLATION_Y));
		}

		if (d.containsKey(TiC.PROPERTY_TRANSLATION_Z) && !nativeViewNull) {
			ViewCompat.setTranslationZ(nativeView, TiConvert.toFloat(d, TiC.PROPERTY_TRANSLATION_Z));
		}

		if (!nativeViewNull && d.containsKeyAndNotNull(TiC.PROPERTY_TRANSITION_NAME)) {
			ViewCompat.setTransitionName(nativeView, d.getString(TiC.PROPERTY_TRANSITION_NAME));
		}
	}

	private void setAnchor(HashMap point)
	{
		View outerView = getOuterView();
		if (outerView == null) {
			return;
		}
		float pivotX = TiConvert.toFloat(point.get(TiC.PROPERTY_X), 0.5f);
		float pivotY = TiConvert.toFloat(point.get(TiC.PROPERTY_Y), 0.5f);

		ViewCompat.setPivotX(outerView, pivotX * outerView.getWidth());
		ViewCompat.setPivotY(outerView, pivotY * outerView.getHeight());
	}

	// TODO dead code?
	@Override
	public void propertiesChanged(List<KrollPropertyChange> changes, KrollProxy proxy)
	{
		for (KrollPropertyChange change : changes) {
			propertyChanged(change.getName(), change.getOldValue(), change.getNewValue(), proxy);
		}
	}

	private void applyCustomBackground()
	{
		applyCustomBackground(true);
	}

	private void applyCustomBackground(boolean reuseCurrentDrawable)
	{
		if (nativeView != null) {
			if (background == null) {
				background = new TiBackgroundDrawable();

				Drawable currentDrawable = nativeView.getBackground();
				if (currentDrawable != null) {
					if (reuseCurrentDrawable) {
						background.setBackgroundDrawable(currentDrawable);

					} else {
						nativeView.setBackgroundDrawable(null);
						currentDrawable.setCallback(null);
						if (currentDrawable instanceof TiBackgroundDrawable) {
							((TiBackgroundDrawable) currentDrawable).releaseDelegate();
						}
					}
				}
			}
			nativeView.setBackgroundDrawable(background);
		}
	}

	/**
	 * @param props View's property dictionary
	 * @return true if touch feedback can be applied.
	 */
	protected boolean canApplyTouchFeedback(@NonNull KrollDict props)
	{
		return props.optBoolean(TiC.PROPERTY_TOUCH_FEEDBACK, false);
	}

	/**
	 * Applies touch feedback. Should check canApplyTouchFeedback() before calling this.
	 * @param rippleColor The ripple color to use. Set to null to use system's default ripple color.
	 */
	private void applyTouchFeedback(Integer rippleColor)
	{
		// Do not continue if there is no view to modify.
		if (this.nativeView == null) {
			return;
		}

		// Fetch default ripple color if given null.
		if (rippleColor == null) {
			Context context = proxy.getActivity();
			TypedValue attribute = new TypedValue();
			if (context.getTheme().resolveAttribute(android.R.attr.colorControlHighlight, attribute, true)) {
				rippleColor = attribute.data;
			}
			if (rippleColor == null) {
				Log.e(TAG, "android.R.attr.colorControlHighlight cannot be resolved into Drawable");
				return;
			}
		}

		// Fetch the background drawable that we'll be applying the ripple effect to.
		Drawable backgroundDrawable = this.background;
		if (backgroundDrawable == null) {
			backgroundDrawable = this.nativeView.getBackground();
		}
		if (backgroundDrawable instanceof TiBackgroundDrawable) {
			final TiBackgroundDrawable drawable = (TiBackgroundDrawable) backgroundDrawable;

			backgroundDrawable = drawable.getBackground();
		}

		// Parse background color to determine transparency.
		int backgroundColor = -1;
		if (backgroundDrawable instanceof PaintDrawable) {
			final PaintDrawable drawable = (PaintDrawable) backgroundDrawable;

			backgroundColor = drawable.getPaint().getColor();
		} else if (backgroundDrawable instanceof ColorDrawable) {
			final ColorDrawable drawable = (ColorDrawable) backgroundDrawable;

			backgroundColor = drawable.getColor();
		}
		if (Color.alpha(backgroundColor) <= 0) {

			// Do not use drawable for transparent backgrounds.
			backgroundDrawable = null;
		}

		// Create a mask if a background doesn't exist or if it's completely transparent.
		// Note: Ripple effect won't work unless it has something opaque to draw to. Use mask as a fallback.
		final ShapeDrawable maskDrawable = backgroundDrawable == null ? new ShapeDrawable() : null;

		// Replace existing background with ripple effect wrapping the old drawable.
		nativeView.setBackground(
			new RippleDrawable(ColorStateList.valueOf(rippleColor), backgroundDrawable, maskDrawable));
	}

	@Override
	public void onFocusChange(final View v, boolean hasFocus)
	{
		// Show/hide the virtual keyboard.
		if (hasFocus) {
			TiMessenger.postOnMain(new Runnable() {
				@Override
				public void run()
				{
					TiUIHelper.requestSoftInputChange(proxy, v);
				}
			});
		}

		// Fire a focus/blur event. (This event should not be bubbled.)
		boolean isBubbled = false;
		String eventName = hasFocus ? TiC.EVENT_FOCUS : TiC.EVENT_BLUR;
		fireEvent(eventName, getFocusEventObject(hasFocus), isBubbled);
	}

	protected KrollDict getFocusEventObject(boolean hasFocus)
	{
		return null;
	}

	protected InputMethodManager getIMM()
	{
		InputMethodManager imm = null;
		imm = (InputMethodManager) TiApplication.getInstance().getSystemService(Context.INPUT_METHOD_SERVICE);
		return imm;
	}

	/**
	 * Focuses the view.
	 */
	public void focus()
	{
		if (nativeView != null) {
			nativeView.requestFocus();
		}
	}

	public boolean isFocused()
	{
		if (nativeView != null) {
			return nativeView.hasFocus();
		}
		return false;
	}

	/**
	 * Blurs the view.
	 */
	public void blur()
	{
		if (nativeView != null) {
			nativeView.clearFocus();
			TiMessenger.postOnMain(new Runnable() {
				public void run()
				{
					if (nativeView != null) {
						TiUIHelper.showSoftKeyboard(nativeView, false);
					}
				}
			});
		}
	}

	public void release()
	{
		if (Log.isDebugModeEnabled()) {
			Log.d(TAG, "Releasing: " + this, Log.DEBUG_MODE);
		}
		releaseLongPressMotionEvent();
		View nv = getNativeView();
		if (nv != null) {
			if (nv instanceof ViewGroup) {
				ViewGroup vg = (ViewGroup) nv;
				if (Log.isDebugModeEnabled()) {
					Log.d(TAG, "Group has: " + vg.getChildCount(), Log.DEBUG_MODE);
				}
				if (!(vg instanceof AdapterView<?>)) {
					vg.removeAllViews();
				}
			}
			Drawable d = nv.getBackground();
			if (d != null) {
				d.setCallback(null);
				if (d instanceof TiBackgroundDrawable) {
					((TiBackgroundDrawable) d).releaseDelegate();
				}
				d = null;
			}
			if (!(nativeView instanceof AdapterView)) {
				nativeView.setOnClickListener(null);
			}
			nativeView.setOnLongClickListener(null);
			nativeView.setOnTouchListener(null);
			nativeView.setOnDragListener(null);
			nativeView.setOnFocusChangeListener(null);
			nativeView = null;
			borderView = null;
			if (proxy != null) {
				proxy.setModelListener(null);
			}
		}
		if (children != null) {
			for (TiUIView child : children) {
				remove(child);
			}
			children.clear();
			children = null;
		}
		proxy = null;
		layoutParams = null;
	}

	private void releaseLongPressMotionEvent()
	{
		if (this.longPressMotionEvent != null) {
			this.longPressMotionEvent.recycle();
			this.longPressMotionEvent = null;
		}
	}

	private void setVisibility(int visibility)
	{
		if (visibility == View.INVISIBLE) {
			visibility = hiddenBehavior;
		}
		this.visibility = visibility;
		if (borderView != null) {
			borderView.setVisibility(this.visibility);
		}
		if (nativeView != null) {
			nativeView.setVisibility(this.visibility);
		}
	}

	/**
	 * Shows the view, changing the view's visibility to View.VISIBLE.
	 */
	public void show()
	{
		this.setVisibility(View.VISIBLE);
		if (borderView == null && nativeView == null) {
			Log.w(TAG, "Attempt to show null native control", Log.DEBUG_MODE);
		}
	}

	/**
	 * Hides the view, changing the view's visibility to View.INVISIBLE.
	 */
	public void hide()
	{
		this.setVisibility(View.INVISIBLE);
		if (borderView == null && nativeView == null) {
			Log.w(TAG, "Attempt to hide null native control", Log.DEBUG_MODE);
		}
	}

	private String resolveImageUrl(String path)
	{
		return path.length() > 0 ? proxy.resolveUrl(null, path) : null;
	}

	private void handleBackgroundImage(KrollDict d)
	{
		String bg = d.getString(TiC.PROPERTY_BACKGROUND_IMAGE);
		String bgSelected = d.optString(TiC.PROPERTY_BACKGROUND_SELECTED_IMAGE, bg);
		String bgFocused = d.optString(TiC.PROPERTY_BACKGROUND_FOCUSED_IMAGE, bg);
		String bgDisabled = d.optString(TiC.PROPERTY_BACKGROUND_DISABLED_IMAGE, bg);

		String bgColor = d.getString(TiC.PROPERTY_BACKGROUND_COLOR);
		String bgSelectedColor = d.optString(TiC.PROPERTY_BACKGROUND_SELECTED_COLOR, bgColor);
		String bgFocusedColor = d.optString(TiC.PROPERTY_BACKGROUND_FOCUSED_COLOR, bgColor);
		String bgDisabledColor = d.optString(TiC.PROPERTY_BACKGROUND_DISABLED_COLOR, bgColor);

		if (bg != null) {
			bg = resolveImageUrl(bg);
		}
		if (bgSelected != null) {
			bgSelected = resolveImageUrl(bgSelected);
		}
		if (bgFocused != null) {
			bgFocused = resolveImageUrl(bgFocused);
		}
		if (bgDisabled != null) {
			bgDisabled = resolveImageUrl(bgDisabled);
		}

		TiGradientDrawable gradientDrawable = null;
		KrollDict gradientProperties = d.getKrollDict(TiC.PROPERTY_BACKGROUND_GRADIENT);
		if (gradientProperties != null) {
			try {
				gradientDrawable = new TiGradientDrawable(nativeView, gradientProperties);
			} catch (Exception ex) {
				String message = ex.getMessage();
				if (message == null) {
					message = "Unknown";
				}
				Log.e(TAG, "Failed to create '" + TiC.PROPERTY_BACKGROUND_GRADIENT + "'. Reason: " + message);
			}
		}

		if (bg != null || bgSelected != null || bgFocused != null || bgDisabled != null || bgColor != null
			|| bgSelectedColor != null || bgFocusedColor != null || bgDisabledColor != null
			|| gradientDrawable != null) {
			if (background == null) {
				applyCustomBackground(false);
			}
			if (background != null) {
				Drawable bgDrawable = TiUIHelper.buildBackgroundDrawable(
					bg, TiConvert.toBoolean(d, TiC.PROPERTY_BACKGROUND_REPEAT, false), bgColor, bgSelected,
					bgSelectedColor, bgDisabled, bgDisabledColor, bgFocused, bgFocusedColor, gradientDrawable);

				background.setBackgroundDrawable(bgDrawable);
			}
		}
	}

	private void initializeBorder(KrollDict d, Integer bgColor)
	{
		if (hasBorder(d)) {

			if (nativeView != null) {

				if (borderView == null) {
					Activity currentActivity = proxy.getActivity();
					if (currentActivity == null) {
						currentActivity = TiApplication.getAppCurrentActivity();
					}
					borderView = new TiBorderWrapperView(currentActivity);
					// Create new layout params for the child view since we just want the
					// wrapper to control the layout
					LayoutParams params = new LayoutParams();
					params.height = android.widget.FrameLayout.LayoutParams.MATCH_PARENT;
					params.width = android.widget.FrameLayout.LayoutParams.MATCH_PARENT;
					// If the view already has a parent, we need to detach it from the parent
					// and add the borderView to the parent as the child
					ViewGroup savedParent = null;
					int childIndex = -1;
					if (nativeView.getParent() != null) {
						ViewParent nativeParent = nativeView.getParent();
						if (nativeParent instanceof ViewGroup) {
							savedParent = (ViewGroup) nativeParent;
							childIndex = savedParent.indexOfChild(nativeView);
							savedParent.removeView(nativeView);
						}
					}
					borderView.addView(nativeView, params);
					if (savedParent != null) {
						savedParent.addView(borderView, childIndex, getLayoutParams());
					}
					borderView.setVisibility(this.visibility);
				}

				if (d.containsKey(TiC.PROPERTY_BORDER_RADIUS)) {
					if (d.containsKey(TiC.PROPERTY_OPACITY) && LOWER_THAN_MARSHMALLOW) {
						disableHWAcceleration();
					}
					borderView.setRadius(d.get(TiC.PROPERTY_BORDER_RADIUS));
				}

				if (bgColor != null) {
					borderView.setBgColor(bgColor);
					borderView.setColor(bgColor);
				}
				if (d.containsKey(TiC.PROPERTY_BORDER_COLOR)) {
					borderView.setColor(TiConvert.toColor(d, TiC.PROPERTY_BORDER_COLOR));
				}

				//Have a default border width of 1 if the border has defined color.
				Object borderWidth = d.containsKeyAndNotNull(TiC.PROPERTY_BORDER_COLOR) ? "1" : "0";
				if (d.containsKey(TiC.PROPERTY_BORDER_WIDTH)) {
					borderWidth = d.get(TiC.PROPERTY_BORDER_WIDTH);
				} else {
					// Add the default width of 1 to the proxy as well
					proxy.setProperty(TiC.PROPERTY_BORDER_WIDTH, borderWidth);
				}

				TiDimension width = TiConvert.toTiDimension(borderWidth, TiDimension.TYPE_WIDTH);
				if (width != null) {
					borderView.setBorderWidth((float) width.getPixels(borderView));
				}

				nativeView.invalidate();
				borderView.invalidate();
			}

			// TIMOB-24898: disable HW acceleration to allow transparency
			// when the backgroundColor alpha channel has been set
			if ((bgColor != null) && (Color.alpha(bgColor) < 255)) {
				disableHWAcceleration();
			}
		}
	}

	private void handleBorderProperty(String property, Object value)
	{
		if (TiC.PROPERTY_BORDER_COLOR.equals(property)) {
			borderView.setColor(value != null ? TiConvert.toColor(value.toString()) : Color.TRANSPARENT);
			if (!proxy.hasProperty(TiC.PROPERTY_BORDER_WIDTH)) {
				borderView.setBorderWidth(1);
			}
		} else if (TiC.PROPERTY_BORDER_RADIUS.equals(property)) {
			if (proxy.hasProperty(TiC.PROPERTY_OPACITY) && LOWER_THAN_MARSHMALLOW) {
				disableHWAcceleration();
			}
			borderView.setRadius(value);
		} else if (TiC.PROPERTY_BORDER_WIDTH.equals(property)) {
			float width = 0;
			TiDimension bwidth = TiConvert.toTiDimension(value, TiDimension.TYPE_WIDTH);
			if (bwidth != null) {
				width = (float) bwidth.getPixels(getNativeView());
			}
			borderView.setBorderWidth(width);
		}
		borderView.postInvalidate();
	}

	private static final SparseArray<String> motionEvents = new SparseArray<>();
	static
	{
		motionEvents.put(MotionEvent.ACTION_DOWN, TiC.EVENT_TOUCH_START);
		motionEvents.put(MotionEvent.ACTION_UP, TiC.EVENT_TOUCH_END);
		motionEvents.put(MotionEvent.ACTION_MOVE, TiC.EVENT_TOUCH_MOVE);
		motionEvents.put(MotionEvent.ACTION_CANCEL, TiC.EVENT_TOUCH_CANCEL);
	}

	private void setFilterTouchesWhenObscured(boolean isEnabled)
	{
		if (this.nativeView != null) {
			this.nativeView.setFilterTouchesWhenObscured(isEnabled);
		}
	}

	/**
	 * Determines if touch event was obscurred by an overlapping translucent window belonging to another app.
	 * This is used for security purposes to detect "tapjacking".
	 * @param event The touch event to be analyzed. Can be null.
	 * @return Returns true if touch event was obscurred. Returns false if not or if given a null argument.
	 */
	private boolean wasObscured(MotionEvent event)
	{
		if (event != null) {
			int flags = event.getFlags();
			if ((flags & MotionEvent.FLAG_WINDOW_IS_OBSCURED) != 0) {
				return true;
			}
			if ((Build.VERSION.SDK_INT >= 29) && ((flags & MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED) != 0)) {
				return true;
			}
		}
		return false;
	}

	protected KrollDict dictFromEvent(MotionEvent e)
	{
		TiDimension xDimension = new TiDimension((double) e.getX(), TiDimension.TYPE_LEFT);
		TiDimension yDimension = new TiDimension((double) e.getY(), TiDimension.TYPE_TOP);

		KrollDict data = new KrollDict();
		data.put(TiC.EVENT_PROPERTY_X, xDimension.getAsDefault(this.nativeView));
		data.put(TiC.EVENT_PROPERTY_Y, yDimension.getAsDefault(this.nativeView));
		data.put(TiC.EVENT_PROPERTY_FORCE, (double) e.getPressure());
		data.put(TiC.EVENT_PROPERTY_OBSCURED, wasObscured(e));
		data.put(TiC.EVENT_PROPERTY_SIZE, (double) e.getSize());
		data.put(TiC.EVENT_PROPERTY_SOURCE, proxy);
		return data;
	}

	protected KrollDict dictFromEvent(KrollDict dictToCopy)
	{
		KrollDict data = new KrollDict();
		if (dictToCopy.containsKey(TiC.EVENT_PROPERTY_X)) {
			data.put(TiC.EVENT_PROPERTY_X, dictToCopy.get(TiC.EVENT_PROPERTY_X));
		} else {
			data.put(TiC.EVENT_PROPERTY_X, (double) 0);
		}
		if (dictToCopy.containsKey(TiC.EVENT_PROPERTY_Y)) {
			data.put(TiC.EVENT_PROPERTY_Y, dictToCopy.get(TiC.EVENT_PROPERTY_Y));
		} else {
			data.put(TiC.EVENT_PROPERTY_Y, (double) 0);
		}
		if (dictToCopy.containsKey(TiC.EVENT_PROPERTY_FORCE)) {
			data.put(TiC.EVENT_PROPERTY_FORCE, dictToCopy.get(TiC.EVENT_PROPERTY_FORCE));
		} else {
			data.put(TiC.EVENT_PROPERTY_FORCE, (double) 0);
		}
		if (dictToCopy.containsKey(TiC.EVENT_PROPERTY_OBSCURED)) {
			data.put(TiC.EVENT_PROPERTY_OBSCURED, dictToCopy.get(TiC.EVENT_PROPERTY_OBSCURED));
		} else {
			data.put(TiC.EVENT_PROPERTY_OBSCURED, false);
		}
		if (dictToCopy.containsKey(TiC.EVENT_PROPERTY_SIZE)) {
			data.put(TiC.EVENT_PROPERTY_SIZE, dictToCopy.get(TiC.EVENT_PROPERTY_SIZE));
		} else {
			data.put(TiC.EVENT_PROPERTY_SIZE, (double) 0);
		}
		data.put(TiC.EVENT_PROPERTY_SOURCE, proxy);
		return data;
	}

	protected boolean allowRegisterForTouch()
	{
		return true;
	}

	protected boolean allowRegisterForKeyPress()
	{
		return true;
	}

	public View getOuterView()
	{
		return borderView == null ? getNativeView() : borderView;
	}

	public void registerForTouch()
	{
		if (allowRegisterForTouch()) {
			registerForTouch(getNativeView());
		}
	}

	protected void registerTouchEvents(final View touchable)
	{
		touchView = new WeakReference<>(touchable);

		final ScaleGestureDetector scaleDetector =
			new ScaleGestureDetector(touchable.getContext(), new SimpleOnScaleGestureListener() {
				// protect from divide by zero errors
				final long minTimeDelta = 1;
				final float minStartSpan = 1.0f;
				float startSpan;

				@Override
				public boolean onScale(ScaleGestureDetector sgd)
				{
					float timeDelta = sgd.getTimeDelta() == 0 ? minTimeDelta : sgd.getTimeDelta();

					// Suppress scale events (and allow for possible two-finger tap events)
					// until we've moved at least a few pixels. Without this check, two-finger
					// taps are very hard to register on some older devices.
					if (!didScale) {
						if (Math.abs(sgd.getCurrentSpan() - startSpan) > SCALE_THRESHOLD) {
							didScale = true;
						}
					}

					if (didScale) {
						KrollDict data = new KrollDict();
						data.put(TiC.EVENT_PROPERTY_CURRENT_SPAN, sgd.getCurrentSpan());
						data.put(TiC.EVENT_PROPERTY_CURRENT_SPAN_X, sgd.getCurrentSpanX());
						data.put(TiC.EVENT_PROPERTY_CURRENT_SPAN_Y, sgd.getCurrentSpanY());
						data.put(TiC.EVENT_PROPERTY_TIME, sgd.getEventTime());
						data.put(TiC.EVENT_PROPERTY_FOCUS_X, sgd.getFocusX());
						data.put(TiC.EVENT_PROPERTY_FOCUS_Y, sgd.getFocusY());
						data.put(TiC.EVENT_PROPERTY_PREVIOUS_SPAN, sgd.getPreviousSpan());
						data.put(TiC.EVENT_PROPERTY_PREVIOUS_SPAN_X, sgd.getPreviousSpanX());
						data.put(TiC.EVENT_PROPERTY_PREVIOUS_SPAN_Y, sgd.getPreviousSpanY());
						data.put(TiC.EVENT_PROPERTY_SCALE, sgd.getCurrentSpan() / startSpan);
						data.put(TiC.EVENT_PROPERTY_TIME_DELTA, sgd.getTimeDelta());
						data.put(TiC.EVENT_PROPERTY_IN_PROGRESS, sgd.isInProgress());
						data.put(TiC.EVENT_PROPERTY_VELOCITY, (sgd.getScaleFactor() - 1.0f) / timeDelta * 1000);
						data.put(TiC.EVENT_PROPERTY_SOURCE, proxy);

						return fireSyncEvent(TiC.EVENT_PINCH, data);
					}
					return false;
				}

				@Override
				public boolean onScaleBegin(ScaleGestureDetector sgd)
				{
					startSpan = sgd.getCurrentSpan() == 0 ? minStartSpan : sgd.getCurrentSpan();
					return true;
				}
			});

		detector = new GestureDetector(touchable.getContext(), new SimpleOnGestureListener() {
			@Override
			public boolean onDoubleTap(MotionEvent e)
			{
				if (proxy != null
					&& (proxy.hierarchyHasListener(TiC.EVENT_DOUBLE_TAP)
						|| proxy.hierarchyHasListener(TiC.EVENT_DOUBLE_CLICK))) {
					boolean handledTap = fireEvent(TiC.EVENT_DOUBLE_TAP, dictFromEvent(e));
					boolean handledClick = fireEvent(TiC.EVENT_DOUBLE_CLICK, dictFromEvent(e));
					return handledTap || handledClick;
				}
				return false;
			}

			@Override
			public boolean onSingleTapConfirmed(MotionEvent e)
			{
				Log.d(TAG, "TAP, TAP, TAP on " + proxy, Log.DEBUG_MODE);
				if (proxy != null && proxy.hierarchyHasListener(TiC.EVENT_SINGLE_TAP)) {
					return fireEvent(TiC.EVENT_SINGLE_TAP, dictFromEvent(e));
					// Moved click handling to the onTouch listener, because a single tap is not the
					// same as a click. A single tap is a quick tap only, whereas clicks can be held
					// before lifting.
					// boolean handledClick = proxy.fireEvent(TiC.EVENT_CLICK, dictFromEvent(event));
					// Note: this return value is irrelevant in our case. We "want" to use it
					// in onTouch below, when we call detector.onTouchEvent(event); But, in fact,
					// onSingleTapConfirmed is *not* called in the course of onTouchEvent. It's
					// called via Handler in GestureDetector. <-- See its Java source.
					// return handledTap;// || handledClick;
				}
				return false;
			}

			@Override
			public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
			{
				Log.d(TAG, "SWIPE on " + proxy, Log.DEBUG_MODE);
				if (proxy != null && proxy.hierarchyHasListener(TiC.EVENT_SWIPE)) {
					KrollDict data = dictFromEvent(e2);
					if (Math.abs(velocityX) > Math.abs(velocityY)) {
						data.put(TiC.EVENT_PROPERTY_DIRECTION, velocityX > 0 ? "right" : "left");
					} else {
						data.put(TiC.EVENT_PROPERTY_DIRECTION, velocityY > 0 ? "down" : "up");
					}
					return fireEvent(TiC.EVENT_SWIPE, data);
				}
				return false;
			}
		});
		detector.setIsLongpressEnabled(false);  // We do our own "longpress" detection via onTouch().

		touchable.setOnTouchListener(new OnTouchListener() {
			int pointersDown = 0;
			int touchSlop;

			@Override
			public boolean onTouch(View view, MotionEvent event)
			{
				// Fetch max distance finger can travel until it can't be considered a click/tap.
				if (this.touchSlop <= 0) {
					this.touchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
				}

				// Store position where touch was released for the onClick() listener.
				if (event.getAction() == MotionEvent.ACTION_UP) {
					TiDimension xDimension = new TiDimension((double) event.getX(), TiDimension.TYPE_LEFT);
					TiDimension yDimension = new TiDimension((double) event.getY(), TiDimension.TYPE_TOP);
					lastUpEvent.put(TiC.EVENT_PROPERTY_X, xDimension.getAsDefault(view));
					lastUpEvent.put(TiC.EVENT_PROPERTY_Y, yDimension.getAsDefault(view));
					lastUpEvent.put(TiC.EVENT_PROPERTY_OBSCURED, wasObscured(event));
				}

				// Do custom "longpress" event tracking. Store motion event data to be used by onLongClick() listener.
				// Note: Can't use GestureDetector for this since we would have to handle onDown() to make it work,
				//       which would prevent view's onClick() and onLongClick() listeners from being called.
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						// Start tracking.
						releaseLongPressMotionEvent();
						longPressMotionEvent = MotionEvent.obtain(event);
						break;
					case MotionEvent.ACTION_MOVE:
						// Stop tracking if dragged too far from initial touch point.
						if (longPressMotionEvent != null) {
							float deltaX = Math.abs(longPressMotionEvent.getRawX() - event.getRawX());
							float deltaY = Math.abs(longPressMotionEvent.getRawY() - event.getRawY());
							if ((deltaX > this.touchSlop) || (deltaY > this.touchSlop)) {
								releaseLongPressMotionEvent();
							}
						}
						break;
					default:
						// Stop tracking on ACTION_UP, ACTION_CANCEL, etc.
						releaseLongPressMotionEvent();
						break;
				}

				if (proxy != null && proxy.hierarchyHasListener(TiC.EVENT_PINCH)) {
					scaleDetector.onTouchEvent(event);
					if (scaleDetector.isInProgress()) {
						pointersDown = 0;
						return true;
					}
				}

				boolean handled = detector.onTouchEvent(event);
				if (handled) {
					pointersDown = 0;
					return true;
				}

				if (event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
					if (didScale) {
						didScale = false;
						pointersDown = 0;
					} else {
						int index = event.getActionIndex();
						float x = event.getX(index);
						float y = event.getY(index);
						if (x >= 0 && x < touchable.getWidth() && y >= 0 && y < touchable.getHeight()) {
							// If the second touch-up happens inside the view, it is a two-finger touch.
							pointersDown++;
						}
					}
				} else if (event.getAction() == MotionEvent.ACTION_UP) {
					// Don't fire twofingertap if there is no listener
					if (proxy != null && proxy.hierarchyHasListener(TiC.EVENT_TWOFINGERTAP) && pointersDown == 1) {
						float x = event.getX();
						float y = event.getY();
						if (x >= 0 && x < touchable.getWidth() && y >= 0 && y < touchable.getHeight()) {
							// If the touch-up happens inside the view, fire the event.
							fireEvent(TiC.EVENT_TWOFINGERTAP, dictFromEvent(event));
						}
					}
					pointersDown = 0;
				}

				String motionEvent = motionEvents.get(event.getAction());
				if (motionEvent != null) {
					if (proxy != null && proxy.hierarchyHasListener(motionEvent)) {
						fireSyncEvent(motionEvent, dictFromEvent(event));
					}
				}

				// Inside View.java, dispatchTouchEvent() does not call onTouchEvent() if this listener returns true. As
				// a result, click and other motion events do not occur on the native Android side. To prevent this, we
				// always return false and let Android generate click and other motion events.
				return false;
			}
		});
	}

	protected void registerForTouch(final View touchable)
	{
		if (touchable == null) {
			return;
		}

		if (proxy.hasProperty(TiC.PROPERTY_TOUCH_ENABLED)) {
			boolean enabled = TiConvert.toBoolean(proxy.getProperty(TiC.PROPERTY_TOUCH_ENABLED), true);
			touchable.setClickable(enabled);
		}
		//Checking and setting touch sound for view
		if (proxy.hasProperty(TiC.PROPERTY_SOUND_EFFECTS_ENABLED)) {
			boolean soundEnabled = TiConvert.toBoolean(proxy.getProperty(TiC.PROPERTY_SOUND_EFFECTS_ENABLED), true);
			touchable.setSoundEffectsEnabled(soundEnabled);
		}
		registerTouchEvents(touchable);

		// Previously, we used the single tap handling above to fire our click event. It doesn't
		// work: a single tap is not the same as a click. A click can be held for a while before
		// lifting the finger; a single-tap is only generated from a quick tap (which will also cause
		// a click.) We wanted to do it in single-tap handling presumably because the singletap
		// listener gets a MotionEvent, which gives us the information we want to provide to our
		// users in our click event, whereas Android's standard OnClickListener does _not_ contain
		// that info. However, an "up" seems to always occur before the click listener gets invoked,
		// so we store the last up event's x,y coordinates (see onTouch above) and use them here.
		// Note: AdapterView throws an exception if you try to put a click listener on it.
		doSetClickable(touchable);
	}

	public void registerForKeyPress()
	{
		if (allowRegisterForKeyPress()) {
			registerForKeyPress(getNativeView());
		}
	}

	protected void registerForKeyPress(final View v)
	{
		if (v == null) {
			return;
		}

		Object focusable = proxy.getProperty(TiC.PROPERTY_FOCUSABLE);
		if (focusable != null) {
			registerForKeyPress(v, TiConvert.toBoolean(focusable, false));
		}
	}

	protected void registerForKeyPress(final View v, boolean focusable)
	{
		if (v == null) {
			return;
		}

		v.setFocusable(focusable);

		// The listener for the "keypressed" event is only triggered when the view has focus. So we only register the
		// "keypressed" event when the view is focusable.
		if (focusable) {
			registerForKeyPressEvents(v);
		} else {
			v.setOnKeyListener(null);
		}
	}

	/**
	 * Registers a callback to be invoked when a hardware key is pressed in this view.
	 *
	 * @param v The view to have the key listener to attach to.
	 */
	protected void registerForKeyPressEvents(final View v)
	{
		if (v == null) {
			return;
		}

		v.setOnKeyListener(new OnKeyListener() {
			public boolean onKey(View view, int keyCode, KeyEvent event)
			{
				if (event.getAction() == KeyEvent.ACTION_UP) {
					KrollDict data = new KrollDict();
					data.put(TiC.EVENT_PROPERTY_KEYCODE, keyCode);
					fireEvent(TiC.EVENT_KEY_PRESSED, data);

					switch (keyCode) {
						case KeyEvent.KEYCODE_ENTER:
						case KeyEvent.KEYCODE_DPAD_CENTER:
							if (proxy != null && proxy.hasListeners(TiC.EVENT_CLICK)) {
								fireEvent(TiC.EVENT_CLICK, null);
								return true;
							}
					}
				}
				return false;
			}
		});
	}

	/**
	 * Sets the nativeView's opacity.
	 * @param opacity the opacity to set.
	 */
	public void setOpacity(float opacity)
	{
		if (opacity < 0 || opacity > 1) {
			Log.w(TAG, "Ignoring invalid value for opacity: " + opacity);
			return;
		}

		if (borderView != null) {
			setOpacity(borderView, opacity);
		} else if (nativeView != null) {
			setOpacity(nativeView, opacity);
		}
	}

	/**
	 * Sets the view's alpha.
	 * @param view The native view object
	 * @param alpha The new alpha value
	 */
	protected void setAlpha(View view, float alpha)
	{
		view.setAlpha(alpha);
		view.postInvalidate();
	}

	/**
	 * Sets the view's opacity.
	 * @param view the view object.
	 * @param opacity the opacity to set.
	 */
	@SuppressLint("NewApi")
	protected void setOpacity(View view, float opacity)
	{
		if (view == null) {
			return;
		}

		setAlpha(view, opacity);

		if (opacity == 1.0f) {
			clearOpacity(view);
		}
	}

	protected void clearOpacity(View view)
	{
		// Sub-classes can implement if needed.
	}

	public KrollDict toImage()
	{
		return TiUIHelper.viewToImage(proxy.getProperties(), getOuterView());
	}

	private View getTouchView()
	{
		if (nativeView != null) {
			return nativeView;
		} else {
			if (touchView != null) {
				return touchView.get();
			}
		}
		return null;
	}

	private void doSetClickable(View view, boolean clickable)
	{
		if (view == null) {
			return;
		}
		if (!clickable) {
			// If view is AdapterView, setOnClickListener(null) will throw a RuntimeException: Don't call setOnClickListener for an AdapterView. You probably want setOnItemClickListener
			// TIMOB-18951
			if (!(view instanceof AdapterView)) {
				view.setOnClickListener(
					null); // This will set clickable to true in the view, so make sure it stays here so the next line turns it off.
			}
			view.setClickable(false);
			view.setOnLongClickListener(null);
			view.setLongClickable(false);
		} else if (!(view instanceof AdapterView)) {
			// n.b.: AdapterView throws if click listener set.
			// n.b.: setting onclicklistener automatically sets clickable to true.
			setOnClickListener(view);
			setOnLongClickListener(view);
		}
		if (view instanceof ViewGroup) {

			// Allow parent views to receive states from child views.
			// This allows the touch feedback effect when clicking on child views.
			((ViewGroup) view).setAddStatesFromChildren(clickable);
		}
	}

	private void doSetClickable(boolean clickable)
	{
		doSetClickable(getTouchView(), clickable);
	}

	/*
	 * Used just to setup the click listener if applicable.
	 */
	private void doSetClickable(View view)
	{
		if (view == null) {
			return;
		}
		doSetClickable(view, view.isClickable());
	}

	/**
	 * Can be overriden by inheriting views for special click handling.  For example,
	 * the Facebook module's login button view needs special click handling.
	 */
	protected void setOnClickListener(View view)
	{

		view.setOnClickListener(new OnClickListener() {
			public void onClick(View view)
			{
				fireEvent(TiC.EVENT_CLICK, dictFromEvent(lastUpEvent));
			}
		});
	}

	public boolean fireEvent(String eventName, KrollDict data)
	{
		return fireEvent(eventName, data, true);
	}

	public boolean fireEvent(String eventName, KrollDict data, boolean bubbles)
	{
		if (proxy == null) {
			return false;
		}
		if (data == null && additionalEventData != null) {
			data = new KrollDict(additionalEventData);
		} else if (additionalEventData != null) {
			data.putAll(additionalEventData);
		}
		return proxy.fireEvent(eventName, data, bubbles);
	}

	public boolean fireSyncEvent(String eventName, KrollDict data)
	{
		return fireSyncEvent(eventName, data, true);
	}

	public boolean fireSyncEvent(String eventName, KrollDict data, boolean bubbles)
	{
		if (proxy == null) {
			return false;
		}
		if (data == null && additionalEventData != null) {
			data = new KrollDict(additionalEventData);
		} else if (additionalEventData != null) {
			data.putAll(additionalEventData);
		}
		return proxy.fireSyncEvent(eventName, data, bubbles);
	}

	protected void setOnLongClickListener(View view)
	{
		view.setOnLongClickListener((View v) -> {
			boolean wasHandled = false;
			if (this.longPressMotionEvent != null) {
				wasHandled = fireEvent(TiC.EVENT_LONGPRESS, dictFromEvent(this.longPressMotionEvent));
				releaseLongPressMotionEvent();
			}
			wasHandled |= fireEvent(TiC.EVENT_LONGCLICK, null);
			return wasHandled;
		});
	}

	protected void disableHWAcceleration()
	{
		if (this.borderView != null) {
			this.borderView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
		}
	}

	public KrollDict getLastUpEvent()
	{
		return dictFromEvent(this.lastUpEvent);
	}

	/**
	 * Retrieve the saved animated scale values, which we store here since Android provides no property
	 * for looking them up.
	 */
	public Pair<Float, Float> getAnimatedScaleValues()
	{
		return animatedScaleValues;
	}

	/**
	 * Store the animated x and y scale values (i.e., the scale after an animation)
	 * since Android provides no property for looking them up.
	 */
	public void setAnimatedScaleValues(Pair<Float, Float> newValues)
	{
		animatedScaleValues = newValues;
	}

	/**
	 * Set the animated rotation degrees, since Android provides no property for looking it up.
	 */
	public void setAnimatedRotationDegrees(float degrees)
	{
		animatedRotationDegrees = degrees;
	}

	/**
	 * Retrieve the animated rotation degrees, which we store here since Android provides no property
	 * for looking it up.
	 */
	public float getAnimatedRotationDegrees()
	{
		return animatedRotationDegrees;
	}

	/**
	 * Set the animated alpha values, since Android provides no property for looking it up.
	 */
	public void setAnimatedAlpha(float alpha)
	{
		animatedAlpha = alpha;
	}

	/**
	 * Retrieve the animated alpha value, which we store here since Android provides no property
	 * for looking it up.
	 */
	public float getAnimatedAlpha()
	{
		return animatedAlpha;
	}

	/**
	 * "Forget" the values we save after scale and rotation and alpha animations.
	 */
	private void resetPostAnimationValues()
	{
		animatedRotationDegrees = 0f;                                            // i.e., no rotation.
		animatedScaleValues = Pair.create(Float.valueOf(1f), Float.valueOf(1f)); // 1 means no scaling
		animatedAlpha = Float.MIN_VALUE;                                         // we use min val to signal no val.
	}

	private void applyContentDescription()
	{
		if (proxy == null || nativeView == null) {
			return;
		}
		String contentDescription = composeContentDescription();
		if (contentDescription != null) {
			nativeView.setContentDescription(contentDescription);
		}
	}

	private void applyContentDescription(KrollDict properties)
	{
		if (proxy == null || nativeView == null) {
			return;
		}
		String contentDescription = composeContentDescription(properties);
		if (contentDescription != null) {
			nativeView.setContentDescription(contentDescription);
		}
	}

	private void applyAccessibilityProperties()
	{
		if (nativeView != null) {
			applyContentDescription();
			applyAccessibilityHidden();
		}
	}

	private void applyAccessibilityHidden()
	{
		if (nativeView == null || proxy == null) {
			return;
		}

		applyAccessibilityHidden(proxy.getProperty(TiC.PROPERTY_ACCESSIBILITY_HIDDEN));
	}

	private void applyAccessibilityHidden(Object hiddenPropertyValue)
	{
		if (nativeView == null) {
			return;
		}

		int importanceMode = ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO;

		if (hiddenPropertyValue != null && TiConvert.toBoolean(hiddenPropertyValue, false)) {
			importanceMode = ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO;
		}

		ViewCompat.setImportantForAccessibility(nativeView, importanceMode);
	}

	/**
	 * Our view proxy supports three properties to match iOS regarding
	 * the text that is read aloud (or otherwise communicated) by the
	 * assistive technology: accessibilityLabel, accessibilityHint
	 * and accessibilityValue.
	 *
	 * We combine these to create the single Android property contentDescription.
	 * (e.g., View.setContentDescription(...));
	 */
	public static String composeContentDescription(KrollDict properties)
	{
		if (properties == null) {
			return null;
		}

		final String punctuationPattern = "^.*\\p{Punct}\\s*$";
		StringBuilder buffer = new StringBuilder();
		String label = properties.optString(TiC.PROPERTY_ACCESSIBILITY_LABEL, "");
		String hint = properties.optString(TiC.PROPERTY_ACCESSIBILITY_HINT, "");
		String value = properties.optString(TiC.PROPERTY_ACCESSIBILITY_VALUE, "");

		if (!TextUtils.isEmpty(label)) {
			buffer.append(label);
			if (!label.matches(punctuationPattern)) {
				buffer.append(".");
			}
		}

		if (!TextUtils.isEmpty(value)) {
			if (buffer.length() > 0) {
				buffer.append(" ");
			}
			buffer.append(value);
			if (!value.matches(punctuationPattern)) {
				buffer.append(".");
			}
		}

		if (!TextUtils.isEmpty(hint)) {
			if (buffer.length() > 0) {
				buffer.append(" ");
			}
			buffer.append(hint);
			if (!hint.matches(punctuationPattern)) {
				buffer.append(".");
			}
		}

		return buffer.toString();
	}

	public String composeContentDescription()
	{
		if (proxy == null) {
			return null;
		}
		return composeContentDescription(proxy.getProperties());
	}
}
