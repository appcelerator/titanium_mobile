/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2020 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget.tableview;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.util.TiUIHelper;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.ItemKeyProvider;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import ti.modules.titanium.ui.TableViewProxy;
import ti.modules.titanium.ui.TableViewRowProxy;
import ti.modules.titanium.ui.TableViewSectionProxy;
import ti.modules.titanium.ui.widget.TiSwipeRefreshLayout;
import ti.modules.titanium.ui.widget.listview.ItemTouchHandler;
import ti.modules.titanium.ui.widget.listview.TiNestedRecyclerView;
import ti.modules.titanium.ui.widget.searchbar.TiUISearchBar.OnSearchChangeListener;

public class TiTableView extends TiSwipeRefreshLayout implements OnSearchChangeListener
{
	private static final String TAG = "TiTableView";

	private static final int CACHE_SIZE = 8;
	private static final int PRELOAD_SIZE = CACHE_SIZE / 2;

	private final TableViewAdapter adapter;
	private final DividerItemDecoration decoration;
	private final TableViewProxy proxy;
	private final TiNestedRecyclerView recyclerView;
	private final List<TableViewRowProxy> rows = new ArrayList<>(CACHE_SIZE);
	private final List<KrollDict> selectedRows = new ArrayList<>();

	private SelectionTracker tracker;
	private boolean isScrolling = false;
	private int scrollOffsetX = 0;
	private int scrollOffsetY = 0;
	private int totalRowCount;
	private String filterQuery;

	public TiTableView(TableViewProxy proxy)
	{
		super(proxy.getActivity());

		this.proxy = proxy;

		this.recyclerView = new TiNestedRecyclerView(getContext());
		this.recyclerView.setFocusable(true);
		this.recyclerView.setFocusableInTouchMode(true);
		this.recyclerView.setBackgroundColor(Color.TRANSPARENT);
		this.recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

		// Add listener to fire scroll events.
		this.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener()
		{
			@Override
			public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState)
			{
				super.onScrollStateChanged(recyclerView, newState);

				if (isScrolling && newState == RecyclerView.SCROLL_STATE_IDLE) {
					final KrollDict payload = generateScrollPayload();
					final TiNestedRecyclerView nestedRecyclerView = getRecyclerView();

					isScrolling = false;

					// Obtain last touch position for `scrollend` event.
					final TiDimension xDimension =
						new TiDimension(nestedRecyclerView.getLastTouchX(), TiDimension.TYPE_WIDTH);
					final TiDimension yDimension =
						new TiDimension(nestedRecyclerView.getLastTouchY(), TiDimension.TYPE_HEIGHT);
					payload.put(TiC.EVENT_PROPERTY_X, xDimension.getAsDefault(nestedRecyclerView));
					payload.put(TiC.EVENT_PROPERTY_Y, yDimension.getAsDefault(nestedRecyclerView));

					proxy.fireSyncEvent(TiC.EVENT_SCROLLEND, payload);
				}
			}

			@Override
			public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy)
			{
				super.onScrolled(recyclerView, dx, dy);

				// Update scroll offsets.
				scrollOffsetX += dx;
				scrollOffsetY += dy;

				if (dx == 0 && dy == 0) {

					// Not scrolled, skip.
					return;
				}

				isScrolling = true;

				final KrollDict payload = generateScrollPayload();

				proxy.fireSyncEvent(TiC.EVENT_SCROLL, payload);
			}
		});

		// Disable table animations.
		this.recyclerView.setItemAnimator(null);

		// Optimize scroll performance.
		recyclerView.setItemViewCacheSize(CACHE_SIZE);

		// Set list separator.
		decoration = new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL);
		this.recyclerView.addItemDecoration(decoration);

		this.adapter = new TableViewAdapter(getContext(), this.rows);
		this.recyclerView.setAdapter(this.adapter);

		// Create ItemTouchHelper for swipe-to-delete and move gestures.
		final ItemTouchHandler itemTouchHandler = new ItemTouchHandler(this.adapter, this.proxy, this.recyclerView);
		final ItemTouchHelper itemTouchHelper = new ItemTouchHelper(itemTouchHandler);
		itemTouchHelper.attachToRecyclerView(this.recyclerView);

		// Fire `postlayout` on layout changes.
		this.addOnLayoutChangeListener(new OnLayoutChangeListener()
		{
			@Override
			public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop,
									   int oldRight, int oldBottom)
			{
				TiUIHelper.firePostLayoutEvent(proxy);
			}
		});

		final SelectionTracker.Builder trackerBuilder = new SelectionTracker.Builder("table_view_selection",
			this.recyclerView,
			new ItemKeyProvider(1)
			{
				@Nullable
				@Override
				public Object getKey(int position)
				{
					if (position > -1 && position < rows.size()) {
						return rows.get(position);
					}
					return null;
				}

				@Override
				public int getPosition(@NonNull Object key)
				{
					return rows.indexOf(key);
				}
			},
			new ItemDetailsLookup()
			{
				@Nullable
				@Override
				public ItemDetails getItemDetails(@NonNull MotionEvent e)
				{
					final View view = recyclerView.findChildViewUnder(e.getX(), e.getY());
					if (view != null) {
						final TableViewHolder holder = (TableViewHolder) recyclerView.getChildViewHolder(view);
						return new ItemDetails()
						{
							@Override
							public int getPosition()
							{
								return holder.getAdapterPosition();
							}

							@Nullable
							@Override
							public Object getSelectionKey()
							{
								final int position = getPosition();

								if (position > -1 && position < rows.size()) {
									return rows.get(position);
								}
								return null;
							}

							@Override
							public boolean inSelectionHotspot(@NonNull MotionEvent e)
							{
								if (holder.getProxy() instanceof TableViewRowProxy) {
									final TableViewRowProxy row = (TableViewRowProxy) holder.getProxy();

									// Prevent selection of placeholders.
									return !row.isPlaceholder();
								}

								// Returning true allows taps to immediately select this row.
								return true;
							}
						};
					}
					return null;
				}
			},
			StorageStrategy.createLongStorage()
		);

		final KrollDict properties = proxy.getProperties();
		final boolean editing = properties.optBoolean(TiC.PROPERTY_EDITING, false);
		final boolean allowsSelection = properties.optBoolean(TiC.PROPERTY_ALLOWS_SELECTION_DURING_EDITING, false);
		final boolean allowsMultipleSelection
			= properties.optBoolean(TiC.PROPERTY_ALLOWS_MULTIPLE_SELECTION_DURING_EDITING, false);

		if (editing && allowsSelection) {
			if (allowsMultipleSelection) {
				this.tracker = trackerBuilder.withSelectionPredicate(SelectionPredicates.createSelectAnything())
					.build();
			} else {
				this.tracker = trackerBuilder.withSelectionPredicate(SelectionPredicates.createSelectSingleAnything())
					.build();
			}

			if (this.tracker != null) {
				this.tracker.addObserver(new SelectionTracker.SelectionObserver()
				{
					@Override
					public void onSelectionChanged()
					{
						super.onSelectionChanged();

						selectedRows.clear();

						if (tracker.hasSelection()) {
							final Iterator<TableViewRowProxy> i = tracker.getSelection().iterator();

							while (i.hasNext()) {
								final TableViewRowProxy row = i.next();

								if (row.isPlaceholder()) {
									continue;
								}
								if (!allowsMultipleSelection) {
									row.fireEvent(TiC.EVENT_CLICK, null);
									return;
								}

								final KrollDict selectedRow = new KrollDict();

								selectedRow.put(TiC.PROPERTY_INDEX, row.index);
								selectedRow.put(TiC.EVENT_PROPERTY_ROW, row);
								selectedRow.put(TiC.PROPERTY_ROW_DATA, row.getProperties());
								if (getParent() instanceof TableViewSectionProxy) {
									selectedRow.put(TiC.PROPERTY_SECTION, getParent());
								}

								selectedRows.add(selectedRow);
							}
						}

						if (allowsMultipleSelection) {
							final KrollDict data = new KrollDict();

							data.put(TiC.PROPERTY_SELECTED_ROWS, selectedRows.toArray(new KrollDict[0]));
							data.put(TiC.PROPERTY_STARTING_ROW, selectedRows.isEmpty() ? null : selectedRows.get(0));
							proxy.fireEvent(TiC.EVENT_ROWS_SELECTED, data);
						}
					}
				});
				this.adapter.setTracker(this.tracker);
			}
		}

		// Disable pull-down refresh support until a Titanium "RefreshControl" has been assigned.
		setSwipeRefreshEnabled(false);

		addView(this.recyclerView);
	}

	/**
	 * Filter current rows with query string.
	 *
	 * @param query String to query rows with.
	 */
	@Override
	public void filterBy(String query)
	{
		this.filterQuery = query;
		update();
	}

	/**
	 * Generate payload for `scroll` and `scrollend` events.
	 *
	 * @return KrollDict
	 */
	public KrollDict generateScrollPayload()
	{
		final KrollDict payload = new KrollDict();
		final LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();

		// Obtain index for first visible row.
		final View firstVisibleView =
			layoutManager.findViewByPosition(layoutManager.findFirstVisibleItemPosition());
		if (firstVisibleView != null) {
			final TableViewHolder firstVisibleHolder =
				(TableViewHolder) recyclerView.getChildViewHolder(firstVisibleView);
			final TableViewRowProxy firstVisibleProxy = (TableViewRowProxy) firstVisibleHolder.getProxy();
			final int firstVisibleIndex = firstVisibleProxy.getIndexInSection();
			payload.put(TiC.PROPERTY_FIRST_VISIBLE_ITEM, firstVisibleIndex);
		}

		// Define visible item count.
		final int visibleItemCount =
			layoutManager.findLastVisibleItemPosition() - layoutManager.findFirstVisibleItemPosition();
		payload.put(TiC.PROPERTY_VISIBLE_ITEM_COUNT, visibleItemCount);

		// Define total item count.
		payload.put(TiC.PROPERTY_TOTAL_ITEM_COUNT, totalRowCount);

		// Obtain scroll offset for content.
		final KrollDict contentOffset = new KrollDict();
		final TiDimension scrollOffsetXDimension = new TiDimension(scrollOffsetX, TiDimension.TYPE_WIDTH);
		final TiDimension scrollOffsetYDimension = new TiDimension(scrollOffsetY, TiDimension.TYPE_HEIGHT);
		contentOffset.put(TiC.EVENT_PROPERTY_X, scrollOffsetXDimension.getAsDefault(recyclerView));
		contentOffset.put(TiC.EVENT_PROPERTY_Y, scrollOffsetYDimension.getAsDefault(recyclerView));
		payload.put(TiC.PROPERTY_CONTENT_OFFSET, contentOffset);

		// Approximate content size.
		// NOTE: Due to recycling of views, we cannot calculate the true
		// content size without loading all rows. The best we can do is an
		// approximation based on first visible row.
		final KrollDict contentSize = new KrollDict();
		final TiDimension contentWidthDimension =
			new TiDimension(firstVisibleView.getMeasuredWidth(), TiDimension.TYPE_WIDTH);
		final TiDimension contentHeightDimension =
			new TiDimension(firstVisibleView.getMeasuredHeight() * rows.size(), TiDimension.TYPE_HEIGHT);
		contentSize.put(TiC.PROPERTY_WIDTH, contentWidthDimension.getAsDefault(recyclerView));
		contentSize.put(TiC.PROPERTY_HEIGHT, contentHeightDimension.getAsDefault(recyclerView));
		payload.put(TiC.PROPERTY_CONTENT_SIZE, contentSize);

		// Obtain view size.
		final KrollDict size = new KrollDict();
		final TiDimension widthDimension =
			new TiDimension(recyclerView.getMeasuredWidth(), TiDimension.TYPE_WIDTH);
		final TiDimension heightDimension =
			new TiDimension(recyclerView.getMeasuredHeight(), TiDimension.TYPE_HEIGHT);
		size.put(TiC.PROPERTY_WIDTH, widthDimension.getAsDefault(recyclerView));
		size.put(TiC.PROPERTY_HEIGHT, heightDimension.getAsDefault(recyclerView));
		payload.put(TiC.PROPERTY_SIZE, size);

		return payload;
	}

	/**
	 * Get table adapter.
	 *
	 * @return TableViewAdapter
	 */
	public TableViewAdapter getAdapter()
	{
		return this.adapter;
	}

	/**
	 * Get linear layout manager.
	 *
	 * @return LinearLayoutManager
	 */
	public LinearLayoutManager getLayoutManager()
	{
		return (LinearLayoutManager) this.recyclerView.getLayoutManager();
	}

	/**
	 * Get recycler view of table.
	 *
	 * @return TiNestedRecyclerView
	 */
	public TiNestedRecyclerView getRecyclerView()
	{
		return this.recyclerView;
	}

	/**
	 * Obtain row for specified index.
	 *
	 * @param index Index of row.
	 * @return TableViewRowProxy
	 */
	public TableViewRowProxy getRowByIndex(int index)
	{
		for (TableViewRowProxy row : this.rows) {
			if (row.index == index) {
				return row;
			}
		}
		return null;
	}

	/**
	 * Get current x-axis scroll offset.
	 * NOTE: This is unreliable when items are added/removed.
	 *
	 * @return Integer of scroll offset.
	 */
	public int getScrollOffsetX()
	{
		return this.scrollOffsetX;
	}

	/**
	 * Get current y-axis scroll offset.
	 * NOTE: This is unreliable when items are added/removed.
	 *
	 * @return Integer of scroll offset.
	 */
	public int getScrollOffsetY()
	{
		return this.scrollOffsetY;
	}

	/**
	 * Get selection tracker.
	 *
	 * @return SelectionTracker
	 */
	public SelectionTracker getTracker()
	{
		return this.tracker;
	}

	/**
	 * Obtain adapter index from list item index.
	 *
	 * @param index List item index.
	 * @return Integer of adapter index.
	 */
	public int getAdapterIndex(int index)
	{
		for (TableViewRowProxy row : this.rows) {
			if (row.index == index) {
				return this.rows.indexOf(row);
			}
		}
		return -1;
	}

	/**
	 * Obtain row from adapter index.
	 *
	 * @param index List item adapter index.
	 * @return Row at specified adapter index.
	 */
	public TableViewRowProxy getAdapterItem(int index)
	{
		return this.rows.get(index);
	}

	/**
	 * Obtain first visible table row proxy.
	 *
	 * @return TableViewRowProxy
	 */
	public TableViewRowProxy getFirstVisibleItem()
	{
		final LinearLayoutManager layoutManager = getLayoutManager();
		final View firstVisibleView =
			layoutManager.findViewByPosition(layoutManager.findFirstVisibleItemPosition());

		if (firstVisibleView != null) {
			final TableViewHolder firstVisibleHolder =
				(TableViewHolder) recyclerView.getChildViewHolder(firstVisibleView);

			// Obtain first visible table row proxy.
			return (TableViewRowProxy) firstVisibleHolder.getProxy();
		}

		return null;
	}

	/**
	 * Obtain last visible table row proxy.
	 *
	 * @return TableViewRowProxy
	 */
	public TableViewRowProxy getLastVisibleItem()
	{
		final LinearLayoutManager layoutManager = getLayoutManager();
		final View lastVisibleView =
			layoutManager.findViewByPosition(layoutManager.findLastVisibleItemPosition());

		if (lastVisibleView != null) {
			final TableViewHolder lastVisibleHolder =
				(TableViewHolder) recyclerView.getChildViewHolder(lastVisibleView);

			// Obtain last visible table row proxy.
			return (TableViewRowProxy) lastVisibleHolder.getProxy();
		}

		return null;
	}

	/**
	 * Determine if table results are filtered by query.
	 *
	 * @return Boolean
	 */
	public boolean isFiltered()
	{
		return this.filterQuery != null && !this.filterQuery.isEmpty();
	}

	/**
	 * Release models.
	 */
	public void release()
	{
		for (TableViewRowProxy row : this.rows) {
			row.releaseViews();
		}
		this.rows.clear();
	}

	/**
	 * Set row separator color and height.
	 *
	 * @param color Color of separator.
	 * @param height Height of separator.
	 */
	public void setSeparator(int color, int height)
	{
		final ShapeDrawable separator = new ShapeDrawable(new RectShape());

		separator.setIntrinsicHeight(height);
		separator.getPaint().setColor(color);
		decoration.setDrawable(separator);

		this.recyclerView.invalidate();
	}

	/**
	 * Set row separator drawable.
	 *
	 * @param drawable Separator drawable.
	 */
	public void setSeparator(Drawable drawable)
	{
		decoration.setDrawable(drawable);
	}

	/**
	 * Update table rows, including headers and footers.
	 */
	public void update()
	{
		final KrollDict properties = this.proxy.getProperties();
		final boolean firstUpdate = this.rows.size() == 0;

		final boolean hasHeader = properties.containsKeyAndNotNull(TiC.PROPERTY_HEADER_TITLE)
			|| properties.containsKeyAndNotNull(TiC.PROPERTY_HEADER_VIEW);
		final boolean hasFooter = properties.containsKeyAndNotNull(TiC.PROPERTY_FOOTER_TITLE)
			|| properties.containsKeyAndNotNull(TiC.PROPERTY_FOOTER_VIEW);

		final boolean caseInsensitive = properties.optBoolean(TiC.PROPERTY_FILTER_CASE_INSENSITIVE, true);
		final boolean filterAnchored = properties.optBoolean(TiC.PROPERTY_FILTER_ANCHORED, false);
		final String filterAttribute = properties.optString(TiC.PROPERTY_FILTER_ATTRIBUTE, TiC.PROPERTY_TITLE);
		int filterResultsCount = 0;
		int index = 0;

		String query = this.filterQuery;
		if (query != null && caseInsensitive) {
			query = query.toLowerCase();
		}

		// Clear current models.
		this.rows.clear();

		// Add placeholder item for TableView header.
		if (hasHeader) {
			final TableViewRowProxy row = new TableViewRowProxy(true);

			row.getProperties().put(TiC.PROPERTY_HEADER_TITLE, properties.get(TiC.PROPERTY_HEADER_TITLE));
			row.getProperties().put(TiC.PROPERTY_HEADER_VIEW, properties.get(TiC.PROPERTY_HEADER_VIEW));
			row.setParent(this.proxy);

			this.rows.add(row);
		}

		// Reset total row count.
		this.totalRowCount = 0;

		// Iterate through data, processing each supported entry.
		for (final Object entry : this.proxy.getData()) {

			int filteredIndex = 0;
			if (entry instanceof TableViewSectionProxy) {
				final TableViewSectionProxy section = (TableViewSectionProxy) entry;
				final TableViewRowProxy[] rows = section.getRows();

				// Add placeholder item for TableViewSection header/footer.
				if (rows.length == 0 && (section.hasHeader() || section.hasFooter())) {
					final TableViewRowProxy row = new TableViewRowProxy(true);

					row.setParent(section);
					this.rows.add(row);
				}

				for (int i = 0; i < rows.length; i++) {
					final TableViewRowProxy row = rows[i];

					// Maintain true row index.
					row.index = index++;

					// Handle search query.
					if (query != null) {
						String attribute = row.getProperties().optString(filterAttribute, null);

						if (attribute != null) {
							if (caseInsensitive) {
								attribute = attribute.toLowerCase();
							}

							if (!((filterAnchored && attribute.startsWith(query))
								|| (!filterAnchored && attribute.contains(query)))) {
								continue;
							}
						}
					}

					// Update filtered index of row.
					row.setFilteredIndex(query != null ? filteredIndex++ : -1);

					this.rows.add(row);
				}
				filterResultsCount += filteredIndex;
				this.totalRowCount += rows.length;

				// Update section filtered row count.
				section.setFilteredRowCount(query != null ? filteredIndex : -1);
			}
		}

		// Add placeholder item for TableView footer.
		if (hasFooter) {
			final TableViewRowProxy row = new TableViewRowProxy(true);

			row.getProperties().put(TiC.PROPERTY_FOOTER_TITLE, properties.get(TiC.PROPERTY_FOOTER_TITLE));
			row.getProperties().put(TiC.PROPERTY_FOOTER_VIEW, properties.get(TiC.PROPERTY_FOOTER_VIEW));
			row.setParent(this.proxy);

			this.rows.add(row);
		}

		// If filtered and no results, fire `noresult` event.
		if (isFiltered() && filterResultsCount == 0) {
			this.proxy.fireEvent(TiC.EVENT_NO_RESULTS, null);
		}

		if (firstUpdate) {
			final int preloadSize = Math.min(this.rows.size(), PRELOAD_SIZE);

			for (int i = 0; i < preloadSize; i++) {

				// Pre-load views for smooth initial scroll.
				this.rows.get(i).getOrCreateView();
			}
		}

		// Notify adapter of changes on UI thread.
		this.adapter.notifyDataSetChanged();

		// FIXME: This is not an ideal workaround for an issue where recycled items that were in focus
		//        lose their focus when the data set changes. There are improvements to be made here.
		//        This can be reproduced when setting a Ti.UI.TextField in the Ti.UI.ListView.headerView for search.
		final Activity activity = TiApplication.getAppCurrentActivity();
		final View previousFocus = activity != null ? activity.getCurrentFocus() : null;

		activity.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				if (previousFocus != null) {
					final View currentFocus = activity != null ? activity.getCurrentFocus() : null;

					if (currentFocus != previousFocus) {

						// Request focus on previous component before dataset changed.
						previousFocus.requestFocus();
					}
				}

				if (firstUpdate && tracker != null) {
					for (final TableViewRowProxy row : rows) {

						// Re-select previously selected items.
						// This can occur when the theme is changed.
						if (row.isSelected()) {
							tracker.select(row);
						}
					}
				}
			}
		});
	}
}
