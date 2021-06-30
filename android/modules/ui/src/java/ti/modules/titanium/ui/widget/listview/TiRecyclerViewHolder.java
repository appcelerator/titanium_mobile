/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2020 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget.listview;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.R;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiUIHelper;

import java.lang.ref.WeakReference;

public abstract class TiRecyclerViewHolder<V extends TiViewProxy> extends RecyclerView.ViewHolder
{
	private static final String TAG = "TiRecyclerViewHolder";

	protected static final int COLOR_GRAY = Color.rgb(169, 169, 169);

	protected static Drawable checkDrawable;
	protected static Drawable disclosureDrawable;
	protected static Drawable dragDrawable;
	protected static Drawable moreDrawable;

	protected static Resources resources;

	protected WeakReference<V> proxy;

	public TiRecyclerViewHolder(final Context context, final ViewGroup viewGroup)
	{
		super(viewGroup);

		if (resources == null) {

			// Obtain resources instance.
			resources = context.getResources();
		}
		if (resources != null) {

			// Attempt to load `titanium_icon_more` drawable.
			if (moreDrawable == null) {
				try {
					final int icon_more_id = R.drawable.titanium_icon_more;
					moreDrawable = resources.getDrawable(icon_more_id);
				} catch (Exception e) {
					Log.w(TAG, "Drawable 'drawable.titanium_icon_more' not found.");
				}
			}

			// Attempt to load `titanium_icon_checkmark` drawable.
			if (checkDrawable == null) {
				try {
					final int icon_checkmark_id = R.drawable.titanium_icon_checkmark;
					checkDrawable = resources.getDrawable(icon_checkmark_id);
				} catch (Exception e) {
					Log.w(TAG, "Drawable 'drawable.titanium_icon_checkmark' not found.");
				}
			}

			// Attempt to load `titanium_icon_disclosure` drawable.
			if (disclosureDrawable == null) {
				try {
					final int icon_disclosure_id = R.drawable.titanium_icon_disclosure;
					disclosureDrawable = resources.getDrawable(icon_disclosure_id);
				} catch (Exception e) {
					Log.w(TAG, "Drawable 'drawable.titanium_icon_disclosure' not found.");
				}
			}

			// Attempt to load `titanium_icon_drag` drawable.
			if (dragDrawable == null) {
				try {
					final int icon_drag_id = R.drawable.titanium_icon_drag;
					dragDrawable = resources.getDrawable(icon_drag_id);
				} catch (Exception e) {
					Log.w(TAG, "Drawable 'drawable.titanium_icon_drag' not found.");
				}
			}
		} else {
			Log.w(TAG, "Could not obtain context resources instance.");
		}
	}

	/**
	 * Generate ripple effect drawable from specified drawable.
	 *
	 * @param drawable Drawable to apply ripple effect.
	 * @return Drawable
	 */
	protected Drawable generateRippleDrawable(Drawable drawable, String color)
	{
		final Activity activity = TiApplication.getAppCurrentActivity();

		if (activity != null) {
			final int[][] rippleStates = new int[][] { new int[] {} };
			final TypedValue typedValue = new TypedValue();

			final TypedArray colorControlHighlight = activity.obtainStyledAttributes(
				typedValue.data, new int[] { android.R.attr.colorControlHighlight });
			final int colorControlHighlightInt = color != null && !color.isEmpty()
				? TiConvert.toColor(color) : colorControlHighlight.getColor(0, 0);
			final int[] rippleColors = new int[] { colorControlHighlightInt };
			final ColorStateList colorStateList = new ColorStateList(rippleStates, rippleColors);
			final ShapeDrawable maskDrawable = drawable == null ? new ShapeDrawable() : null;

			// Create the RippleDrawable.
			drawable = new RippleDrawable(colorStateList, drawable, maskDrawable);
		}

		return drawable;
	}

	/**
	 * Generate selected background from proxy properties.
	 *
	 * @param properties Dictionary containing selected background properties.
	 * @return Drawable
	 */
	protected Drawable generateSelectedDrawable(KrollDict properties, Drawable drawable)
	{
		if (properties.containsKeyAndNotNull(TiC.PROPERTY_SELECTED_BACKGROUND_COLOR)
			|| properties.containsKeyAndNotNull(TiC.PROPERTY_SELECTED_BACKGROUND_IMAGE)) {

			final StateListDrawable stateDrawable = new StateListDrawable();
			final Drawable selectedBackgroundDrawable = TiUIHelper.buildBackgroundDrawable(
				properties.getString(TiC.PROPERTY_SELECTED_BACKGROUND_COLOR),
				properties.getString(TiC.PROPERTY_SELECTED_BACKGROUND_IMAGE),
				TiConvert.toBoolean(properties.get(TiC.PROPERTY_BACKGROUND_REPEAT), false),
				null
			);

			stateDrawable.addState(
				new int[] { android.R.attr.state_activated }, selectedBackgroundDrawable);
			stateDrawable.addState(new int[] {}, drawable);

			return stateDrawable;
		}

		return drawable;
	}

	/**
	 * Get current proxy assigned to holder.
	 *
	 * @return TiViewProxy
	 */
	public V getProxy()
	{
		if (this.proxy != null) {
			return this.proxy.get();
		}
		return null;
	}

	public abstract void bind(V proxy, boolean selected);
}
