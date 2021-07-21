/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2020 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiUIView;

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

import ti.modules.titanium.ui.RefreshControlProxy;
import ti.modules.titanium.ui.SearchBarProxy;
import ti.modules.titanium.ui.TableViewProxy;
import ti.modules.titanium.ui.UIModule;
import ti.modules.titanium.ui.widget.searchbar.TiUISearchBar;
import ti.modules.titanium.ui.widget.searchview.TiUISearchView;
import ti.modules.titanium.ui.widget.tableview.TiTableView;

public class TiUITableView extends TiUIView
{
	private static final String TAG = "TitaniumTableView";

	private static final int SEARCHVIEW_ID = View.generateViewId();

	protected final TiTableView tableView;
	private TiViewProxy searchProxy;

	public TiUITableView(TiViewProxy proxy)
	{
		super(proxy);

		getLayoutParams().autoFillsHeight = true;
		getLayoutParams().autoFillsWidth = true;

		this.tableView = new TiTableView((TableViewProxy) proxy);
		setNativeView(tableView);
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
		// Ignore, handled by row.
		return false;
	}

	/**
	 * Get current table view instance.
	 *
	 * @return TiTableView
	 */
	public TiTableView getTableView()
	{
		return this.tableView;
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

			// Set overscroll mode.
			this.tableView.getRecyclerView().setOverScrollMode(
				TiConvert.toInt(value, View.OVER_SCROLL_ALWAYS));
		}

		if (name.equals(TiC.PROPERTY_REFRESH_CONTROL)) {

			// Set refresh control.
			if (value instanceof RefreshControlProxy) {
				((RefreshControlProxy) value).assignTo(this.tableView);
			} else if (value == null) {
				RefreshControlProxy.unassignFrom(this.tableView);
			}
		}

		if (name.equals(TiC.PROPERTY_GRID_COLUMNS)) {
			// create a simple GridLayout
			final int gridColumns = TiConvert.toInt(value, 0);
			if (gridColumns > 1) {
				this.tableView.getRecyclerView().setLayoutManager(new GridLayoutManager(
					getProxy().getActivity(), gridColumns));
			} else {
				this.tableView.getRecyclerView().setLayoutManager(new LinearLayoutManager(
					getProxy().getActivity()));
			}
		}

		if (name.equals(TiC.PROPERTY_SCROLLABLE)) {
			final boolean isScrollable = TiConvert.toBoolean(value, true);

			// Set list scrolling.
			this.tableView.getRecyclerView().setScrollEnabled(isScrollable);
		}

		if (name.equals(TiC.PROPERTY_SHOW_VERTICAL_SCROLL_INDICATOR)) {

			// Set vertical scroll indicator.
			this.tableView.getRecyclerView().setVerticalScrollBarEnabled(TiConvert.toBoolean(value, true));
		}

		if (name.equals(TiC.PROPERTY_SEARCH) || name.equals(TiC.PROPERTY_SEARCH_AS_CHILD)) {
			final ViewParent parent = getOuterView().getParent();
			final TiViewProxy parentProxy = getProxy().getParent();
			final KrollDict properties = getProxy().getProperties();
			final boolean searchAsChild = TiConvert.toBoolean(
				name.equals(TiC.PROPERTY_SEARCH_AS_CHILD)
					? value : properties.optBoolean(TiC.PROPERTY_SEARCH_AS_CHILD, true));

			if (parent instanceof ViewGroup) {

				// Remove current view from parent.
				((ViewGroup) parent).removeView(getOuterView());
			}

			this.searchProxy = (TiViewProxy) (name.equals(TiC.PROPERTY_SEARCH)
				? value : properties.get(TiC.PROPERTY_SEARCH));

			// Reset current border and background so this does not corrupt the new view.
			this.borderView = null;
			this.background = null;

			final ViewParent tableViewParent = this.tableView.getParent();
			if (tableViewParent instanceof ViewGroup) {

				// Remove table view from parent, could be TiBorderWrapperView or RelativeLayout.
				((ViewGroup) tableViewParent).removeView(this.tableView);
			}

			if (this.searchProxy != null) {
				final TiUIView search = this.searchProxy.getOrCreateView();

				if (this.searchProxy instanceof SearchBarProxy) {
					((TiUISearchBar) search).setOnSearchChangeListener(this.tableView);
				} else {
					((TiUISearchView) search).setOnSearchChangeListener(this.tableView);
				}

				if (searchAsChild) {
					final View searchView = search.getOuterView();
					final ViewGroup searchViewParent = (ViewGroup) searchView.getParent();
					searchView.setId(SEARCHVIEW_ID);

					final RelativeLayout view = new RelativeLayout(proxy.getActivity());

					final RelativeLayout.LayoutParams searchViewLayout = new RelativeLayout.LayoutParams(
						RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
					searchViewLayout.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
					searchViewLayout.addRule(RelativeLayout.ALIGN_PARENT_TOP);
					searchViewLayout.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);

					if (searchViewParent != null) {
						searchViewParent.removeView(searchView);
					}
					view.addView(searchView, searchViewLayout);

					final RelativeLayout.LayoutParams tableViewLayout = new RelativeLayout.LayoutParams(
						RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
					tableViewLayout.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
					tableViewLayout.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
					tableViewLayout.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
					tableViewLayout.addRule(RelativeLayout.BELOW, SEARCHVIEW_ID);

					view.addView(this.tableView, tableViewLayout);

					// Set new view layout with search child.
					setNativeView(view);
				}
			}
			if (this.searchProxy == null || !searchAsChild) {

				// No search proxy or not child, reset back to table.
				setNativeView(this.tableView);
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
				this.tableView.setSeparator(color, height);

			} else {
				final TypedArray divider = context.obtainStyledAttributes(new int[] { android.R.attr.listDivider });
				final GradientDrawable defaultDrawable = (GradientDrawable) divider.getDrawable(0);

				// Set platform default separator.
				defaultDrawable.setSize(0, height);
				this.tableView.setSeparator(defaultDrawable);
				divider.recycle();
			}
		}

		if (name.equals(TiC.PROPERTY_HEADER_TITLE)
			|| name.equals(TiC.PROPERTY_HEADER_VIEW)
			|| name.equals(TiC.PROPERTY_FOOTER_TITLE)
			|| name.equals(TiC.PROPERTY_FOOTER_VIEW)
			|| name.equals(TiC.PROPERTY_BACKGROUND_COLOR)) {
			this.tableView.update();
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

			// Update table items.
			this.tableView.update();

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

		RefreshControlProxy.unassignFrom(this.tableView);

		this.tableView.release();

		super.release();
	}
}
