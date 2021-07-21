/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2020 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.RelativeLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.annotation.NonNull;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiUIView;

import ti.modules.titanium.ui.widget.listview.ListViewProxy;
import ti.modules.titanium.ui.RefreshControlProxy;
import ti.modules.titanium.ui.SearchBarProxy;
import ti.modules.titanium.ui.UIModule;
import ti.modules.titanium.ui.widget.listview.TiListView;
import ti.modules.titanium.ui.widget.searchbar.TiUISearchBar;
import ti.modules.titanium.ui.widget.searchview.TiUISearchView;

public class TiUIListView extends TiUIView
{
	private static final String TAG = "TiListView";

	private static final int SEARCHVIEW_ID = View.generateViewId();

	protected final TiListView listView;
	private TiViewProxy searchProxy;

	public TiUIListView(TiViewProxy proxy)
	{
		super(proxy);

		getLayoutParams().autoFillsHeight = true;
		getLayoutParams().autoFillsWidth = true;

		this.listView = new TiListView((ListViewProxy) proxy);
		setNativeView(listView);
	}

	/**
	 * Determine if touchFeedback can be applied to view.
	 *
	 * @param props View's property dictionary
	 * @return Boolean
	 */
	@Override
	protected boolean canApplyTouchFeedback(@NonNull KrollDict props)
	{
		// Ignore, handled by item.
		return false;
	}

	/**
	 * Get current list view instance.
	 *
	 * @return TiListView
	 */
	public TiListView getListView()
	{
		return this.listView;
	}

	/**
	 * Process proxy property.
	 *
	 * @param name  Property name.
	 * @param value Property value.
	 */
	private void processProperty(String name, Object value)
	{
		if (name.equals(TiC.PROPERTY_OVER_SCROLL_MODE)) {
			final int overscrollMode = TiConvert.toInt(value, View.OVER_SCROLL_ALWAYS);

			// Set overscroll mode.
			this.listView.getRecyclerView().setOverScrollMode(overscrollMode);
		}

		if (name.equals(TiC.PROPERTY_CAN_SCROLL)) {
			final boolean isScrollable = TiConvert.toBoolean(value, true);

			// Set list scrolling.
			this.listView.getRecyclerView().setScrollEnabled(isScrollable);
		}

		if (name.equals(TiC.PROPERTY_GRID_COLUMNS)) {
			// create a simple GridLayout
			final int gridColumns = TiConvert.toInt(value, 0);
			if (gridColumns > 1) {
				this.listView.getRecyclerView().setLayoutManager(new GridLayoutManager(
					getProxy().getActivity(), gridColumns));
			} else {
				this.listView.getRecyclerView().setLayoutManager(new LinearLayoutManager(
					getProxy().getActivity()));
			}
		}

		if (name.equals(TiC.PROPERTY_REFRESH_CONTROL)) {

			if (value instanceof RefreshControlProxy) {

				// Set refresh control.
				((RefreshControlProxy) value).assignTo(this.listView);

			} else if (value == null) {

				// Remove refresh control.
				RefreshControlProxy.unassignFrom(this.listView);
			}
		}

		if (name.equals(TiC.PROPERTY_SEARCH_VIEW)) {
			final ViewParent parent = getOuterView().getParent();
			final TiViewProxy parentProxy = getProxy().getParent();

			if (parent instanceof ViewGroup) {

				// Remove current view from parent.
				((ViewGroup) parent).removeView(getOuterView());
			}

			// Reset current border and background so this does not corrupt the new view.
			this.borderView = null;
			this.background = null;

			final ViewParent listViewParent = this.listView.getParent();
			if (listViewParent instanceof ViewGroup) {

				// Remove list view from parent, could be TiBorderWrapperView or RelativeLayout.
				((ViewGroup) listViewParent).removeView(this.listView);
			}

			if (value instanceof TiViewProxy) {

				// Set search proxy.
				this.searchProxy = (TiViewProxy) value;
				final TiUIView search = this.searchProxy.getOrCreateView();

				// Set search listeners.
				if (this.searchProxy instanceof SearchBarProxy) {
					((TiUISearchBar) search).setOnSearchChangeListener(this.listView);
				} else {
					((TiUISearchView) search).setOnSearchChangeListener(this.listView);
				}

				final RelativeLayout view = new RelativeLayout(proxy.getActivity());
				final View searchView = search.getOuterView();
				final ViewGroup searchViewParent = (ViewGroup) searchView.getParent();
				final RelativeLayout.LayoutParams searchViewLayout = new RelativeLayout.LayoutParams(
					RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
				searchViewLayout.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
				searchViewLayout.addRule(RelativeLayout.ALIGN_PARENT_TOP);
				searchViewLayout.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);

				searchView.setId(SEARCHVIEW_ID);

				if (searchViewParent != null) {
					searchViewParent.removeView(searchView);
				}
				view.addView(searchView, searchViewLayout);

				final RelativeLayout.LayoutParams listViewLayout = new RelativeLayout.LayoutParams(
					RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
				listViewLayout.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
				listViewLayout.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
				listViewLayout.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
				listViewLayout.addRule(RelativeLayout.BELOW, SEARCHVIEW_ID);

				view.addView(listView, listViewLayout);

				// Set new view layout.
				setNativeView(view);
			} else {

				// Reset back to standard list view.
				setNativeView(listView);
			}

			// Re-apply background and border to new view.
			super.processProperties(getProxy().getProperties());

			if (parent != null && parentProxy != null) {
				final TiUIView parentView = parentProxy.peekView();

				if (parentView != null) {

					// Release view and reconstruct parent.
					proxy.releaseViews();
					parentProxy.realizeViews(parentView);
				}
			}
		}

		if ((name.equals(TiC.PROPERTY_SEPARATOR_STYLE)
			|| name.equals(TiC.PROPERTY_SEPARATOR_HEIGHT)
			|| name.equals(TiC.PROPERTY_SEPARATOR_COLOR))
			&& value != null) {
			final Context context = getProxy().getActivity();
			final KrollDict properties = getProxy().getProperties();

			int style = properties.optInt(TiC.PROPERTY_SEPARATOR_STYLE,
				UIModule.TABLE_VIEW_SEPARATOR_STYLE_SINGLE_LINE);
			if (name.equals(TiC.PROPERTY_SEPARATOR_STYLE)) {
				style = TiConvert.toInt(value);
			}

			String heightString = properties.optString(TiC.PROPERTY_SEPARATOR_HEIGHT, "1dp");
			if (name.equals(TiC.PROPERTY_SEPARATOR_HEIGHT)) {
				heightString = TiConvert.toString(value);
			}
			final int height = style == UIModule.TABLE_VIEW_SEPARATOR_STYLE_SINGLE_LINE
				? TiConvert.toTiDimension(heightString, TiDimension.TYPE_HEIGHT)
				.getAsPixels((View) getNativeView().getParent()) : 0;

			if (name.equals(TiC.PROPERTY_SEPARATOR_COLOR)
				|| properties.containsKey(TiC.PROPERTY_SEPARATOR_COLOR)) {
				String colorString = properties.getString(TiC.PROPERTY_SEPARATOR_COLOR);
				if (name.equals(TiC.PROPERTY_SEPARATOR_COLOR)) {
					colorString = TiConvert.toString(value);
				}
				final int color = TiConvert.toColor(colorString);

				// Set separator with specified color.
				this.listView.setSeparator(color, height);

			} else {
				final TypedArray divider = context.obtainStyledAttributes(new int[] { android.R.attr.listDivider });
				final GradientDrawable defaultDrawable = (GradientDrawable) divider.getDrawable(0);

				// Set platform default separator.
				defaultDrawable.setSize(0, height);
				this.listView.setSeparator(defaultDrawable);
				divider.recycle();
			}
		}

		if (name.equals(TiC.PROPERTY_SHOW_VERTICAL_SCROLL_INDICATOR)) {

			// Set vertical scroll indicator.
			this.listView.getRecyclerView().setVerticalScrollBarEnabled(TiConvert.toBoolean(value, true));
		}

		if (name.equals(TiC.PROPERTY_HEADER_TITLE)
			|| name.equals(TiC.PROPERTY_HEADER_VIEW)
			|| name.equals(TiC.PROPERTY_FOOTER_TITLE)
			|| name.equals(TiC.PROPERTY_FOOTER_VIEW)
			|| name.equals(TiC.PROPERTY_SEARCH_TEXT)) {
			this.listView.update();
		}
	}

	/**
	 * Process proxy properties.
	 *
	 * @param d Dictionary object of properties being set.
	 */
	@Override
	public void processProperties(KrollDict d)
	{
		super.processProperties(d);

		for (final String key : d.keySet()) {
			processProperty(key, d.get(key));
		}
	}

	/**
	 * Property changed listener.
	 *
	 * @param key      Key of property changed.
	 * @param oldValue Previous property value.
	 * @param newValue New property value.
	 * @param proxy    Proxy property was changed on.
	 */
	@Override
	public void propertyChanged(String key, Object oldValue, Object newValue, KrollProxy proxy)
	{
		if (key.equals(TiC.PROPERTY_TOUCH_FEEDBACK)
			|| key.equals(TiC.PROPERTY_TOUCH_FEEDBACK_COLOR)) {

			// Update list items.
			this.listView.update();

			// Return to prevent property being handled by TiUIView.
			return;
		}
		super.propertyChanged(key, oldValue, newValue, proxy);

		processProperty(key, newValue);
	}

	/**
	 * Release views.
	 */
	@Override
	public void release()
	{
		if (this.searchProxy != null) {
			searchProxy.releaseViews();
		}

		RefreshControlProxy.unassignFrom(this.listView);

		this.listView.release();

		super.release();
	}
}
