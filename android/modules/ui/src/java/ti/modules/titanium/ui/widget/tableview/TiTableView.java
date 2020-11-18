/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2017 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget.tableview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiColorHelper;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.view.TiCompositeLayout;
import org.appcelerator.titanium.view.TiCompositeLayout.LayoutArrangement;
import org.appcelerator.titanium.view.TiUIView;

import ti.modules.titanium.ui.TableViewProxy;
import ti.modules.titanium.ui.TableViewRowProxy;
import ti.modules.titanium.ui.UIModule;
import ti.modules.titanium.ui.widget.listview.TiNestedListView;
import ti.modules.titanium.ui.widget.searchbar.TiUISearchBar.OnSearchChangeListener;
import ti.modules.titanium.ui.widget.tableview.TableViewModel.Item;
import ti.modules.titanium.ui.widget.TiSwipeRefreshLayout;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;

public class TiTableView extends TiSwipeRefreshLayout implements OnSearchChangeListener
{
	public static final int HEADER_FOOTER_WRAP_ID = View.generateViewId();
	private static final String TAG = "TiTableView";

	protected int maxClassname = 32;

	private TableViewModel viewModel;
	private ListView listView;
	private TTVListAdapter adapter;
	private OnItemClickedListener itemClickListener;
	private OnItemLongClickedListener itemLongClickListener;

	private HashMap<String, Integer> rowTypes;
	private AtomicInteger rowTypeCounter;

	private String filterAttribute;
	private String filterText;

	private int dividerHeight;
	private TableViewProxy proxy;
	private boolean filterCaseInsensitive = true;
	private boolean filterAnchored = false;
	private StateListDrawable selector;

	public interface OnItemClickedListener {
		void onClick(KrollDict item);
	}

	public interface OnItemLongClickedListener {
		boolean onLongClick(KrollDict item);
	}

	class TTVListAdapter extends BaseAdapter
	{
		TableViewModel viewModel;
		ArrayList<Integer> index;
		private boolean filtered;

		TTVListAdapter(TableViewModel viewModel)
		{
			this.viewModel = viewModel;
			this.index = new ArrayList<Integer>(viewModel.getRowCount());
			reIndexItems();
		}

		protected void registerClassName(String className)
		{
			if (!rowTypes.containsKey(className)) {
				Log.d(TAG, "registering new className " + className, Log.DEBUG_MODE);
				rowTypes.put(className, rowTypeCounter.incrementAndGet());
			}
		}

		public void reIndexItems()
		{
			ArrayList<Item> items = viewModel.getViewModel();
			int count = items.size();
			index.clear();

			filtered = false;
			if (filterAttribute != null && filterText != null && filterAttribute.length() > 0
				&& filterText.length() > 0) {
				filtered = true;
				String filter = filterText;
				if (filterCaseInsensitive) {
					filter = filterText.toLowerCase();
				}
				for (int i = 0; i < count; i++) {
					boolean keep = true;
					Item item = items.get(i);
					registerClassName(item.className);
					if (item.proxy.hasProperty(filterAttribute)) {
						String t = TiConvert.toString(item.proxy.getProperty(filterAttribute));
						if (filterCaseInsensitive) {
							t = t.toLowerCase();
						}
						if (filterAnchored) {
							if (!t.startsWith(filter)) {
								keep = false;
							}
						} else {
							if (t.indexOf(filter) < 0) {
								keep = false;
							}
						}
					}
					if (keep) {
						index.add(i);
					}
				}
			} else {
				for (int i = 0; i < count; i++) {
					Item item = items.get(i);
					registerClassName(item.className);
					index.add(i);
				}
			}
			if (index.size() == 0) {
				proxy.fireEvent(TiC.EVENT_NO_RESULTS, null);
			}
		}

		public int getCount()
		{
			//return viewModel.getViewModel().length();
			return index.size();
		}

		public Object getItem(int position)
		{
			if ((position < 0) || (position >= index.size())) {
				return null;
			}

			return viewModel.getViewModel().get(index.get(position));
		}

		public long getItemId(int position)
		{
			return position;
		}

		@Override
		public int getViewTypeCount()
		{
			// Fix for TIMOB-20038. Seems that there are 3 more
			// hidden views that needs to be recreated onLayout
			return maxClassname + 3;
		}

		@Override
		public int getItemViewType(int position)
		{
			Item item = (Item) getItem(position);
			registerClassName(item.className);
			return rowTypes.get(item.className);
		}

		/*
		 * IMPORTANT NOTE:
		 * getView() is called by the Android framework whenever it needs a view.
		 * The call to getView() could come on a measurement pass or on a layout
		 * pass. It's not possible to tell from the arguments whether the framework
		 * is calling getView() for a measurement pass or for a layout pass. Therefore,
		 * it is important that getView() and all methods call by getView() only create
		 * the views and fill them in with the appropriate data.  What getView() and the
		 * methods call by getView MUST NOT do is to make any associations between
		 * proxies and views. Those associations must be made only for the views
		 *  that are used for layout, and should be driven from the onLayout() callback.
		 */
		public View getView(int position, View convertView, ViewGroup parent)
		{
			TiBaseTableViewItem v = null;

			// Fetch the indexed row item.
			Item item = (Item) getItem(position);
			if (item == null) {
				Log.w(TAG, "getView() received invalid 'position' index: " + position);
				v = new TiTableViewRowProxyItem(proxy.getActivity());
				v.setClassName(TableViewProxy.CLASSNAME_NORMAL);
				return v;
			}

			if (item.proxy instanceof TableViewRowProxy) {
				TableViewRowProxy row = (TableViewRowProxy) item.proxy;
				TiTableViewRowProxyItem rowItem = row.getTableViewRowProxyItem();

				// Attempt to reuse old view.
				if (rowItem != null) {
					v = rowItem.equals(convertView) ? (TiBaseTableViewItem) convertView : null;
				}
			}

			// If we haven't created a view container for the given row item, then do so now.
			if (v == null) {
				if (item.className.equals(TableViewProxy.CLASSNAME_HEADERVIEW)) {
					TiViewProxy vproxy = item.proxy;
					TiUIView headerView = layoutSectionHeaderOrFooter(vproxy);
					v = new TiTableViewHeaderItem(proxy.getActivity(), headerView);
					v.setClassName(TableViewProxy.CLASSNAME_HEADERVIEW);
					return v;
				} else if (item.className.equals(TableViewProxy.CLASSNAME_HEADER)) {
					v = new TiTableViewHeaderItem(proxy.getActivity());
					v.setClassName(TableViewProxy.CLASSNAME_HEADER);
				} else if (item.className.equals(TableViewProxy.CLASSNAME_NORMAL)) {
					v = new TiTableViewRowProxyItem(proxy.getActivity());
					v.setClassName(TableViewProxy.CLASSNAME_NORMAL);
				} else if (item.className.equals(TableViewProxy.CLASSNAME_DEFAULT)) {
					v = new TiTableViewRowProxyItem(proxy.getActivity());
					v.setClassName(TableViewProxy.CLASSNAME_DEFAULT);
				} else {
					v = new TiTableViewRowProxyItem(proxy.getActivity());
					v.setClassName(item.className);
				}
				v.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT,
															   AbsListView.LayoutParams.MATCH_PARENT));
			}

			// Copy the proxy's current settings to the row's views.
			v.setRowData(item);

			// Return the row view configured above.
			return v;
		}

		@Override
		public boolean areAllItemsEnabled()
		{
			return false;
		}

		@Override
		public boolean isEnabled(int position)
		{
			Item item = (Item) getItem(position);
			boolean enabled = true;
			if (item != null && item.className.equals(TableViewProxy.CLASSNAME_HEADER)) {
				enabled = false;
			}
			return enabled;
		}

		@Override
		public boolean hasStableIds()
		{
			return true;
		}

		@Override
		public void notifyDataSetChanged()
		{
			reIndexItems();
			super.notifyDataSetChanged();
		}

		public boolean isFiltered()
		{
			return filtered;
		}
	}

	public TiTableView(TableViewProxy proxy)
	{
		super(proxy.getActivity());
		this.proxy = proxy;

		// Disable pull-down refresh support until a Titanium "RefreshControl" has been assigned.
		setSwipeRefreshEnabled(false);

		if (proxy.getProperties().containsKey(TiC.PROPERTY_MAX_CLASSNAME)) {
			maxClassname = Math.max(TiConvert.toInt(proxy.getProperty(TiC.PROPERTY_MAX_CLASSNAME)), maxClassname);
		}
		rowTypes = new HashMap<String, Integer>();
		rowTypeCounter = new AtomicInteger(-1);
		rowTypes.put(TableViewProxy.CLASSNAME_HEADER, rowTypeCounter.incrementAndGet());
		rowTypes.put(TableViewProxy.CLASSNAME_NORMAL, rowTypeCounter.incrementAndGet());
		rowTypes.put(TableViewProxy.CLASSNAME_DEFAULT, rowTypeCounter.incrementAndGet());

		this.viewModel = new TableViewModel(proxy);
		this.listView = TiNestedListView.createUsing(getContext());

		listView.setFocusable(true);
		listView.setFocusableInTouchMode(true);
		listView.setBackgroundColor(Color.TRANSPARENT);
		listView.setCacheColorHint(Color.TRANSPARENT);
		final KrollProxy fProxy = proxy;
		listView.setOnScrollListener(new OnScrollListener() {
			private boolean scrollValid = false;
			private int lastValidfirstItem = 0;

			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState)
			{
				if (scrollState == OnScrollListener.SCROLL_STATE_IDLE) {
					scrollValid = false;
					KrollDict eventArgs = new KrollDict();
					KrollDict size = new KrollDict();
					size.put("width", TiTableView.this.getWidth());
					size.put("height", TiTableView.this.getHeight());
					eventArgs.put("size", size);
					KrollDict scrollEndArgs = new KrollDict(eventArgs);
					fProxy.fireEvent(TiC.EVENT_SCROLLEND, eventArgs);
					// TODO: Deprecate old event
					fProxy.fireEvent("scrollEnd", scrollEndArgs);
				} else if (scrollState == OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
					scrollValid = true;
				}
			}

			@Override
			public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount)
			{
				boolean fireScroll = scrollValid;
				if (!fireScroll && visibleItemCount > 0) {
					//Items in a list can be selected with a track ball in which case
					//we must check to see if the first visibleItem has changed.
					fireScroll = (lastValidfirstItem != firstVisibleItem);
				}
				if (fireScroll) {
					lastValidfirstItem = firstVisibleItem;
					KrollDict eventArgs = new KrollDict();
					eventArgs.put("firstVisibleItem", firstVisibleItem);
					eventArgs.put("visibleItemCount", visibleItemCount);
					eventArgs.put("totalItemCount", totalItemCount);
					KrollDict size = new KrollDict();
					size.put("width", TiTableView.this.getWidth());
					size.put("height", TiTableView.this.getHeight());
					eventArgs.put("size", size);
					fProxy.fireEvent(TiC.EVENT_SCROLL, eventArgs);
				}
			}
		});
		// get default divider height
		dividerHeight = listView.getDividerHeight();
		if (proxy.hasProperty(TiC.PROPERTY_SEPARATOR_COLOR)) {
			setSeparatorColor(TiConvert.toString(proxy.getProperty(TiC.PROPERTY_SEPARATOR_COLOR)));
		}

		if (proxy.hasProperty(TiC.PROPERTY_SEPARATOR_STYLE)) {
			setSeparatorStyle(TiConvert.toInt(proxy.getProperty(TiC.PROPERTY_SEPARATOR_STYLE),
											  UIModule.TABLE_VIEW_SEPARATOR_STYLE_NONE));
		}
		adapter = new TTVListAdapter(viewModel);
		if (proxy.hasPropertyAndNotNull(TiC.PROPERTY_HEADER_VIEW)) {
			TiViewProxy view = (TiViewProxy) proxy.getProperty(TiC.PROPERTY_HEADER_VIEW);
			listView.addHeaderView(layoutTableHeaderOrFooter(view), null, false);
		}
		if (proxy.hasPropertyAndNotNull(TiC.PROPERTY_FOOTER_VIEW)) {
			TiViewProxy view = (TiViewProxy) proxy.getProperty(TiC.PROPERTY_FOOTER_VIEW);
			listView.addFooterView(layoutTableHeaderOrFooter(view), null, false);
		}

		listView.setAdapter(adapter);
		listView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id)
			{
				if (itemClickListener != null) {
					if (!(view instanceof TiBaseTableViewItem)) {
						return;
					}
					rowClicked((TiBaseTableViewItem) view, position, false);
				}
			}
		});
		listView.setOnItemLongClickListener(new OnItemLongClickListener() {
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id)
			{
				if (itemLongClickListener == null) {
					return false;
				}
				TiBaseTableViewItem tvItem = null;
				if (view instanceof TiBaseTableViewItem) {
					tvItem = (TiBaseTableViewItem) view;
				} else {
					tvItem = getParentTableViewItem(view);
				}
				if (tvItem == null) {
					return false;
				}
				return rowClicked(tvItem, position, true);
			}
		});
		addView(listView);
	}

	public void removeHeaderView(TiViewProxy viewProxy)
	{
		TiUIView peekView = viewProxy.peekView();
		View outerView = (peekView == null) ? null : peekView.getOuterView();
		if (outerView != null) {
			listView.removeHeaderView(outerView);
		}
	}

	public void setHeaderView()
	{
		if (proxy.hasPropertyAndNotNull(TiC.PROPERTY_HEADER_VIEW)) {
			listView.setAdapter(null);
			TiViewProxy view = (TiViewProxy) proxy.getProperty(TiC.PROPERTY_HEADER_VIEW);
			listView.addHeaderView(layoutTableHeaderOrFooter(view), null, false);
			listView.setAdapter(adapter);
		}
	}

	public void removeFooterView(TiViewProxy viewProxy)
	{
		TiUIView peekView = viewProxy.peekView();
		View outerView = (peekView == null) ? null : peekView.getOuterView();
		if (outerView != null) {
			listView.removeFooterView(outerView);
		}
	}

	public void setFooterView()
	{
		if (proxy.hasPropertyAndNotNull(TiC.PROPERTY_FOOTER_VIEW)) {
			listView.setAdapter(null);
			TiViewProxy view = (TiViewProxy) proxy.getProperty(TiC.PROPERTY_FOOTER_VIEW);
			listView.addFooterView(layoutTableHeaderOrFooter(view), null, false);
			listView.setAdapter(adapter);
		}
	}

	private TiBaseTableViewItem getParentTableViewItem(View view)
	{
		ViewParent parent = view.getParent();
		while (parent != null) {
			if (parent instanceof TiBaseTableViewItem) {
				return (TiBaseTableViewItem) parent;
			}
			parent = parent.getParent();
		}
		return null;
	}

	public void enableCustomSelector()
	{
		Drawable currentSelector = listView.getSelector();
		if (currentSelector != selector) {
			selector = new StateListDrawable();
			TiTableViewSelector selectorDrawable = new TiTableViewSelector(listView);
			selector.addState(new int[] { android.R.attr.state_pressed }, selectorDrawable);
			listView.setSelector(selector);
		}
	}

	public Item getItemAtPosition(int position)
	{
		if (proxy.hasPropertyAndNotNull(TiC.PROPERTY_HEADER_VIEW)) {
			position -= 1;
		}
		if (position == -1 || position == adapter.getCount()) {
			return null;
		}
		return viewModel.getViewModel().get(adapter.index.get(position));
	}

	public int getIndexFromXY(double x, double y)
	{
		// Coordinates received are in the measurement unit defined in tiapp.xml.
		// Convert them to pixels in order to define the item clicked.
		final double xInPixels = (double) TiUIHelper.getRawSize(
			TiUIHelper.getSizeUnits(TiApplication.getInstance().getDefaultUnit()), (float) x, getContext());
		final double yInPixels = (double) TiUIHelper.getRawSize(
			TiUIHelper.getSizeUnits(TiApplication.getInstance().getDefaultUnit()), (float) y, getContext());

		int bound = listView.getLastVisiblePosition() - listView.getFirstVisiblePosition();
		for (int i = 0; i <= bound; i++) {
			View child = listView.getChildAt(i);
			if (child != null && xInPixels >= child.getLeft() && xInPixels <= child.getRight()
				&& yInPixels >= child.getTop() && yInPixels <= child.getBottom()) {
				return listView.getFirstVisiblePosition() + i;
			}
		}
		return -1;
	}

	protected boolean rowClicked(TiBaseTableViewItem rowView, int position, boolean longClick)
	{
		String viewClicked = rowView.getLastClickedViewName();
		Item item = getItemAtPosition(position);
		KrollDict event = new KrollDict();
		String eventName = longClick ? TiC.EVENT_LONGCLICK : TiC.EVENT_CLICK;
		TableViewRowProxy.fillClickEvent(event, viewModel, item);
		if (viewClicked != null) {
			event.put(TiC.EVENT_PROPERTY_LAYOUT_NAME, viewClicked);
		}
		event.put(TiC.EVENT_PROPERTY_SEARCH_MODE, adapter.isFiltered());

		boolean longClickFired = false;
		if (item.proxy != null && item.proxy instanceof TableViewRowProxy) {
			TableViewRowProxy rp = (TableViewRowProxy) item.proxy;
			event.put(TiC.EVENT_PROPERTY_SOURCE, rp);
			// The event will bubble up to the parent.
			if (rp.hierarchyHasListener(eventName)) {
				rp.fireEvent(eventName, event);
				longClickFired = true;
			}
		}
		if (longClick && !longClickFired) {
			return itemLongClickListener.onLongClick(event);
		} else if (longClickFired) {
			return true;
		} else {
			return false; // standard (not-long) click handling has no return value.
		}
	}

	private View layoutTableHeaderOrFooter(TiViewProxy viewProxy)
	{
		TiUIView tiView = viewProxy.peekView();
		if (tiView != null) {
			TiViewProxy parentProxy = viewProxy.getParent();
			// Remove parent view if possible
			if (parentProxy != null) {
				TiUIView parentView = parentProxy.peekView();
				if (parentView != null) {
					parentView.remove(tiView);
				}
			}
		} else {
			if ((proxy != null) && (proxy.getActivity() != null)) {
				viewProxy.setActivity(proxy.getActivity());
			}
			tiView = viewProxy.forceCreateView();
		}
		View outerView = tiView.getOuterView();
		ViewGroup parentView = (ViewGroup) outerView.getParent();
		if (parentView != null && parentView.getId() == HEADER_FOOTER_WRAP_ID) {
			return parentView;
		} else {
			TiCompositeLayout wrapper = new TiCompositeLayout(viewProxy.getActivity(), LayoutArrangement.DEFAULT, null);
			AbsListView.LayoutParams params = new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT,
																		   AbsListView.LayoutParams.WRAP_CONTENT);
			wrapper.setLayoutParams(params);
			outerView = tiView.getOuterView();
			wrapper.addView(outerView, tiView.getLayoutParams());
			wrapper.setId(HEADER_FOOTER_WRAP_ID);
			return wrapper;
		}
	}

	private TiUIView layoutSectionHeaderOrFooter(TiViewProxy viewProxy)
	{
		//We are always going to create a new view here. So detach outer view here and recreate
		View outerView = (viewProxy.peekView() == null) ? null : viewProxy.peekView().getOuterView();
		if (outerView != null) {
			ViewParent vParent = outerView.getParent();
			if (vParent instanceof ViewGroup) {
				((ViewGroup) vParent).removeView(outerView);
			}
		}
		TiBaseTableViewItem.clearChildViews(viewProxy);
		TiUIView tiView = viewProxy.forceCreateView();
		View nativeView = tiView.getOuterView();
		TiCompositeLayout.LayoutParams params = tiView.getLayoutParams();

		// Set width to MATCH_PARENT to be consistent with iPhone
		int width = AbsListView.LayoutParams.MATCH_PARENT;
		int height = AbsListView.LayoutParams.WRAP_CONTENT;
		if (params.sizeOrFillHeightEnabled) {
			if (params.autoFillsHeight) {
				height = AbsListView.LayoutParams.MATCH_PARENT;
			}
		} else if (params.optionHeight != null) {
			height = params.optionHeight.getAsPixels(listView);
		}

		AbsListView.LayoutParams p = new AbsListView.LayoutParams(width, height);
		nativeView.setLayoutParams(p);
		return tiView;
	}

	public void dataSetChanged()
	{
		if (adapter != null) {
			adapter.notifyDataSetChanged();
		}
	}

	public void setOnItemClickListener(OnItemClickedListener listener)
	{
		this.itemClickListener = listener;
	}

	public void setOnItemLongClickListener(OnItemLongClickedListener listener)
	{
		this.itemLongClickListener = listener;
	}

	public void setSeparatorColor(String colorstring)
	{
		int sepColor = TiColorHelper.parseColor(colorstring);
		listView.setDivider(new ColorDrawable(sepColor));
		listView.setDividerHeight(dividerHeight);
	}

	public void setSeparatorStyle(int style)
	{
		if (style == UIModule.TABLE_VIEW_SEPARATOR_STYLE_NONE) {
			listView.setDividerHeight(0);
		} else if (style == UIModule.TABLE_VIEW_SEPARATOR_STYLE_SINGLE_LINE) {
			listView.setDividerHeight(dividerHeight);
		}
	}

	public TableViewModel getTableViewModel()
	{
		return this.viewModel;
	}

	public ListView getListView()
	{
		return listView;
	}

	@Override
	public void filterBy(String text)
	{
		filterText = text;
		if (adapter != null) {
			proxy.getActivity().runOnUiThread(new Runnable() {
				public void run()
				{
					dataSetChanged();
				}
			});
		}
	}

	public void setFilterAttribute(String filterAttribute)
	{
		this.filterAttribute = filterAttribute;
	}

	public void setFilterAnchored(boolean filterAnchored)
	{
		this.filterAnchored = filterAnchored;
	}

	public void setFilterCaseInsensitive(boolean filterCaseInsensitive)
	{
		this.filterCaseInsensitive = filterCaseInsensitive;
	}

	public void release()
	{
		adapter = null;
		if (listView != null) {
			listView.setAdapter(null);
		}
		listView = null;
		if (viewModel != null) {
			viewModel.release();
		}
		viewModel = null;
		itemClickListener = null;
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom)
	{
		// To prevent undesired "focus" and "blur" events during layout caused
		// by ListView temporarily taking focus, we will disable focus events until
		// layout has finished.
		// First check for a quick exit. listView can be null, such as if window closing.
		if (listView == null) {
			super.onLayout(changed, left, top, right, bottom);
			return;
		}
		OnFocusChangeListener focusListener = null;
		View focusedView = listView.findFocus();
		if (focusedView != null) {
			OnFocusChangeListener listener = focusedView.getOnFocusChangeListener();
			if (listener != null && listener instanceof TiUIView) {
				focusedView.setOnFocusChangeListener(null);
				focusListener = listener;
			}
		}

		super.onLayout(changed, left, top, right, bottom);

		TiViewProxy viewProxy = proxy;
		if (viewProxy != null && viewProxy.hasListeners(TiC.EVENT_POST_LAYOUT)) {
			viewProxy.fireEvent(TiC.EVENT_POST_LAYOUT, null);
		}

		// Layout is finished, re-enable focus events.
		if (focusListener != null) {
			focusedView.setOnFocusChangeListener(focusListener);
			// The following code was removed for TIMOB-7618 Android: Text field loses focus when changing orientation. Couldn't verify necessary
//			// If the configuration changed, we manually fire the blur event
//			if (changed) {
//				focusListener.onFocusChange(focusedView, false);
//			}
		}
	}
}
