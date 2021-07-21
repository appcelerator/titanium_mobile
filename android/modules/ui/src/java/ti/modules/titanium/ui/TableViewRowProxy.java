/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2020 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.view.TiUIView;

import android.app.Activity;
import android.view.View;

import androidx.annotation.NonNull;

import ti.modules.titanium.ui.widget.TiView;
import ti.modules.titanium.ui.widget.tableview.TableViewHolder;
import ti.modules.titanium.ui.widget.tableview.TiTableView;

@Kroll.proxy(
	creatableInModule = UIModule.class,
	propertyAccessors = {
		TiC.PROPERTY_HAS_CHECK,
		TiC.PROPERTY_HAS_CHILD,
		TiC.PROPERTY_HAS_DETAIL,
		TiC.PROPERTY_EDITABLE,
		TiC.PROPERTY_MOVABLE,
		TiC.PROPERTY_MOVEABLE,
		TiC.PROPERTY_CLASS_NAME,
		TiC.PROPERTY_LAYOUT,
		TiC.PROPERTY_LEFT_IMAGE,
		TiC.PROPERTY_RIGHT_IMAGE,
		TiC.PROPERTY_TITLE,
		TiC.PROPERTY_HEADER_TITLE,
		TiC.PROPERTY_HEADER,
		TiC.PROPERTY_HEADER_VIEW,
		TiC.PROPERTY_FOOTER_TITLE,
		TiC.PROPERTY_FOOTER,
		TiC.PROPERTY_FOOTER_VIEW
	}
)
public class TableViewRowProxy extends TiViewProxy
{
	private static final String TAG = "TableViewRowProxy";

	public int index;

	private int filteredIndex = -1;
	private TableViewHolder holder;
	private boolean placeholder = false;

	// FIXME: On iOS the same row can be added to a table multiple times.
	//        Due to constraints, we need to create a new proxy and track changes.
	private final List<WeakReference<TableViewRowProxy>> clones = new ArrayList<>(0);

	public TableViewRowProxy()
	{
		super();
	}

	public TableViewRowProxy(boolean placeholder)
	{
		this();

		// Determine if row is placeholder for header or footer.
		this.placeholder = placeholder;
	}

	/**
	 * Create clone of existing proxy.
	 *
	 * @return TableViewRowProxy
	 */
	@Override
	public TableViewRowProxy clone()
	{
		final TableViewRowProxy proxy = (TableViewRowProxy) super.clone();

		// Reference clone, to update properties.
		clones.add(new WeakReference<>(proxy));

		return proxy;
	}

	/**
	 * Generate RowView for current proxy.
	 *
	 * @param activity the context activity.
	 * @return TiUIView of row.
	 */
	@Override
	public TiUIView createView(Activity activity)
	{
		if (placeholder) {

			// Placeholder for header or footer, do not create view.
			return null;
		}

		return new RowView(this);
	}

	/**
	 * Handle event data to generate payload with table data.
	 *
	 * @param eventName Name of fired event.
	 * @param data      Data payload of fired event.
	 * @return Object of payload.
	 */
	private Object handleEvent(String eventName, Object data)
	{
		// Inject row data into events.
		final TableViewProxy tableViewProxy = getTableViewProxy();

		if (tableViewProxy != null) {
			final KrollDict payload = data instanceof HashMap
				? new KrollDict((HashMap<String, Object>) data) : new KrollDict();
			final TiTableView tableView = tableViewProxy.getTableView();

			payload.put(TiC.PROPERTY_ROW_DATA, properties);
			if (getParent() instanceof TableViewSectionProxy) {
				payload.put(TiC.PROPERTY_SECTION, getParent());
			}
			payload.put(TiC.EVENT_PROPERTY_ROW, this);
			payload.put(TiC.EVENT_PROPERTY_INDEX, index);

			if (tableView != null) {
				payload.put(TiC.EVENT_PROPERTY_SEARCH_MODE, tableView.isFiltered());
			}
			data = payload;
		}

		return data;
	}

	/**
	 * Override fireEvent to inject row data into payload.
	 *
	 * @param eventName Name of fired event.
	 * @param data      Data payload of fired event.
	 * @param bubbles   Specify if event should bubble up to parent.
	 * @return Returns true if the event was successfully fired to listener(s)
	 */
	@Override
	public boolean fireEvent(String eventName, Object data, boolean bubbles)
	{
		data = handleEvent(eventName, data);
		return super.fireEvent(eventName, data, bubbles);
	}
	@Override
	public boolean fireSyncEvent(String eventName, Object data, boolean bubbles)
	{
		data = handleEvent(eventName, data);
		return super.fireSyncEvent(eventName, data, bubbles);
	}

	@Override
	public String getApiName()
	{
		return "Ti.UI.TableViewRow";
	}

	/**
	 * Get filtered index of row in section.
	 *
	 * @return Integer of filtered index.
	 */
	public int getFilteredIndex()
	{
		return this.filteredIndex;
	}

	/**
	 * Set filtered index of row in section.
	 *
	 * @param index Filtered index to set.
	 */
	public void setFilteredIndex(int index)
	{
		this.filteredIndex = index;
	}

	/**
	 * Get current TableViewHolder for row.
	 *
	 * @return TableViewHolder
	 */
	public TableViewHolder getHolder()
	{
		return this.holder;
	}

	/**
	 * Set new TableViewHolder for item.
	 *
	 * @param holder TableViewHolder to set.
	 */
	public void setHolder(TableViewHolder holder)
	{
		this.holder = holder;

		// Update to new holder.
		final RowView row = (RowView) getOrCreateView();
		if (row != null) {

			// Reset native view to our holder.
			row.setNativeView(this.holder.getNativeView());

			// Register touch events.
			row.registerForTouch();

			// Grab latest `nativeView`.
			final View nativeView = row.getNativeView();

			// Reset opacity of view.
			nativeView.setAlpha(1.0f);

			// Apply proxy properties.
			row.processProperties(this.properties);
		}
	}

	/**
	 * Override getRect() to amend dimensions.
	 *
	 * @return Dictinary of view dimensions.
	 */
	@Override
	public KrollDict getRect()
	{
		View view = null;
		if (this.holder != null) {
			view = this.holder.getNativeView();
		}
		return getViewRect(view);
	}

	/**
	 * Get item index in section.
	 *
	 * @return Integer of index.
	 */
	public int getIndexInSection()
	{
		final TiViewProxy parent = getParent();

		if (parent instanceof TableViewSectionProxy) {
			final TableViewSectionProxy section = (TableViewSectionProxy) parent;

			return section.getRowIndex(this);
		}

		return -1;
	}

	/**
	 * Get related TableView proxy for item.
	 *
	 * @return TableViewProxy
	 */
	public TableViewProxy getTableViewProxy()
	{
		TiViewProxy parent = getParent();
		while (!(parent instanceof TableViewProxy) && parent != null) {
			parent = parent.getParent();
		}
		return (TableViewProxy) parent;
	}

	/**
	 * Handle initial creation properties.
	 *
	 * @param options Initial creation properties.
	 */
	@Override
	public void handleCreationDict(KrollDict options)
	{
		if (options == null) {
			options = new KrollDict();
		}

		// TableViewRow.header and TableViewRow.footer have been deprecated.
		if (options.containsKey(TiC.PROPERTY_HEADER)) {
			headerDeprecationLog();
		}
		if (options.containsKey(TiC.PROPERTY_FOOTER)) {
			footerDeprecationLog();
		}

		if (options.containsKeyAndNotNull(TiC.PROPERTY_HEADER_VIEW)) {
			final TiViewProxy headerProxy = (TiViewProxy) options.get(TiC.PROPERTY_HEADER_VIEW);

			// Set header view parent, so it can be released correctly.
			headerProxy.setParent(this);
		}
		if (options.containsKeyAndNotNull(TiC.PROPERTY_FOOTER_VIEW)) {
			final TiViewProxy footerProxy = (TiViewProxy) options.get(TiC.PROPERTY_FOOTER_VIEW);

			// Set header view parent, so it can be released correctly.
			footerProxy.setParent(this);
		}

		super.handleCreationDict(options);
	}

	/**
	 * Deprecation log for 'TableViewRow.header' usage.
	 */
	private void headerDeprecationLog()
	{
		Log.w(TAG, "Usage of 'TableViewRow.header' has been deprecated, use 'TableViewRow.headerTitle' instead.");
	}

	/**
	 * Deprecation log for 'TableViewRow.footer' usage.
	 */
	private void footerDeprecationLog()
	{
		Log.w(TAG, "Usage of 'TableViewRow.footer' has been deprecated, use 'TableViewRow.footerTitle' instead.");
	}

	/**
	 * Invalidate item to re-bind holder.
	 * This will update the current holder to display new changes.
	 */
	public void invalidate()
	{
		if (this.holder != null) {
			this.holder.bind(this, this.holder.itemView.isActivated());
		}
	}

	/**
	 * Is current item a placeholder for header or footer.
	 *
	 * @return Boolean
	 */
	public boolean isPlaceholder()
	{
		return this.placeholder;
	}

	@Override
	public void onPropertyChanged(String name, Object value)
	{
		super.onPropertyChanged(name, value);

		processProperty(name, value);

		for (final WeakReference ref : clones) {
			final TableViewRowProxy clone = (TableViewRowProxy) ref.get();

			if (ref != null) {
				clone.onPropertyChanged(name, value);
			}
		}
	}

	/**
	 * Process property set on proxy.
	 *
	 * @param name Property name.
	 * @param value Property value.
	 */
	private void processProperty(String name, Object value)
	{
		final TableViewProxy tableViewProxy = getTableViewProxy();

		if (name.equals(TiC.PROPERTY_SELECTED_BACKGROUND_COLOR)) {
			Log.w(TAG, "selectedBackgroundColor is deprecated, use backgroundSelectedColor instead.");
			setProperty(TiC.PROPERTY_BACKGROUND_SELECTED_COLOR, value);
		}
		if (name.equals(TiC.PROPERTY_SELECTED_BACKGROUND_IMAGE)) {
			Log.w(TAG, "selectedBackgroundImage is deprecated, use backgroundSelectedImage instead.");
			setProperty(TiC.PROPERTY_BACKGROUND_SELECTED_IMAGE, value);
		}

		// TableViewRow.header and TableViewRow.footer have been deprecated.
		if (name.equals(TiC.PROPERTY_HEADER)) {
			headerDeprecationLog();
		}
		if (name.equals(TiC.PROPERTY_FOOTER)) {
			footerDeprecationLog();
		}

		if (name.equals(TiC.PROPERTY_LEFT_IMAGE)
			|| name.equals(TiC.PROPERTY_RIGHT_IMAGE)
			|| name.equals(TiC.PROPERTY_HAS_CHECK)
			|| name.equals(TiC.PROPERTY_HAS_CHILD)
			|| name.equals(TiC.PROPERTY_HAS_DETAIL)
			|| name.equals(TiC.PROPERTY_BACKGROUND_COLOR)
			|| name.equals(TiC.PROPERTY_BACKGROUND_IMAGE)
			|| name.equals(TiC.PROPERTY_SELECTED_BACKGROUND_COLOR)
			|| name.equals(TiC.PROPERTY_SELECTED_BACKGROUND_IMAGE)
			|| name.equals(TiC.PROPERTY_TITLE)
			|| name.equals(TiC.PROPERTY_COLOR)
			|| name.equals(TiC.PROPERTY_FONT)
			|| name.equals(TiC.PROPERTY_LEFT)
			|| name.equals(TiC.PROPERTY_RIGHT)
			|| name.equals(TiC.PROPERTY_TOP)
			|| name.equals(TiC.PROPERTY_BOTTOM)
			|| name.equals(TiC.PROPERTY_MOVABLE)) {

			// Force re-bind of row.
			invalidate();
		}
	}

	/**
	 * Release row views.
	 */
	@Override
	public void releaseViews()
	{
		this.holder = null;

		final KrollDict properties = getProperties();

		if (properties.containsKeyAndNotNull(TiC.PROPERTY_HEADER_VIEW)) {
			final TiViewProxy header = (TiViewProxy) properties.get(TiC.PROPERTY_HEADER_VIEW);

			if (header.getParent() == this) {
				header.releaseViews();
			}
		}
		if (properties.containsKeyAndNotNull(TiC.PROPERTY_FOOTER_VIEW)) {
			final TiViewProxy footer = (TiViewProxy) properties.get(TiC.PROPERTY_FOOTER_VIEW);

			if (footer.getParent() == this) {
				footer.releaseViews();
			}
		}

		final RowView rowView = (RowView) peekView();
		if (rowView != null) {

			// Set `nativeView` back to original content to release correctly.
			rowView.setNativeView(rowView.getContent());
		}

		super.releaseViews();
	}

	/**
	 * Process property set on proxy.
	 *
	 * @param name Name of proxy property.
	 * @param value Value to set property.
	 */
	@Override
	public void setProperty(String name, Object value)
	{
		super.setProperty(name, value);

		processProperty(name, value);
	}

	/**
	 * RowView class used for table row.
	 * Auto-fills to width of parent.
	 */
	public static class RowView extends TiView
	{
		View content;

		public RowView(TiViewProxy proxy)
		{
			super(proxy);

			getLayoutParams().autoFillsWidth = true;

			// Maintain a reference to our original row contents.
			// Since we adjust native view.
			this.content = this.nativeView;
		}

		public View getContent()
		{
			return this.content;
		}

		public void setNativeView(View view)
		{
			if (this.content == null) {
				this.content = view;
			}
			super.setNativeView(view);
		}

		@Override
		public void add(TiUIView child)
		{
			// TODO: This could be improved to prevent the need for swapping native views.
			// Our `nativeView` is currently set as our TableViewHolder view as a workaround
			// for allowing events/properties to set on our holder instead of our row content.
			// Temporarily swap our native view back to original content while new child is added.
			final View nativeView = getNativeView();
			if (nativeView != null) {
				setNativeView(this.content);
				super.add(child);
				setNativeView(nativeView);
			} else {
				super.add(child);
			}
		}

		@Override
		public void remove(TiUIView child)
		{
			// TODO: This could be improved to prevent the need for swapping native views.
			// Our `nativeView` is currently set as our TableViewHolder view as a workaround
			// for allowing events/properties to set on our holder instead of our row content.
			// Temporarily swap our native view back to original content while new child is removed.
			final View nativeView = getNativeView();
			if (nativeView != null) {
				setNativeView(this.content);
				super.remove(child);
				setNativeView(nativeView);
			} else {
				super.remove(child);
			}
		}

		protected boolean canApplyTouchFeedback(@NonNull KrollDict props)
		{
			// Prevent TiUIView from overriding `touchFeedback` effect.
			return false;
		}

		@Override
		protected boolean hasBorder(KrollDict d)
		{
			// Always create custom background drawable.
			return true;
		}
	}
}
