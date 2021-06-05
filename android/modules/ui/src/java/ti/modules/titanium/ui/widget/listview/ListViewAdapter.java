/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2020 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget.listview;

import java.util.List;

import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiRHelper;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;

public class ListViewAdapter extends TiRecyclerViewAdapter<ListViewHolder>
{
	private static final String TAG = "ListViewAdapter";

	private static int id_holder;
	private LayoutInflater inflater;
	private List<ListItemProxy> models;

	public ListViewAdapter(@NonNull Context context, @NonNull List<ListItemProxy> models)
	{
		this.context = context;

		// Obtain layout inflater instance.
		inflater = LayoutInflater.from(context);

		// Obtain TableViewHolder layout identifier.
		try {
			if (id_holder == 0) {
				id_holder = TiRHelper.getResource("layout.titanium_ui_listview_holder");
			}
		} catch (TiRHelper.ResourceNotFoundException e) {
			Log.e(TAG, "Could not load 'layout.titanium_ui_listview_holder'.");
		}

		this.models = models;

		setHasStableIds(true);
	}

	/**
	 * Get number of items in list.
	 *
	 * @return Integer of item count.
	 */
	@Override
	public int getItemCount()
	{
		return this.models.size();
	}

	/**
	 * Get unique item identifier.
	 *
	 * @param position Index position of item to obtain identifier.
	 * @return Long of item identifier.
	 */
	@Override
	public long getItemId(int position)
	{
		return this.models.get(position).hashCode();
	}

	/**
	 * Get list of models (items).
	 *
	 * @return List of models.
	 */
	public List<ListItemProxy> getModels()
	{
		return this.models;
	}

	/**
	 * Bind item to holder.
	 * This is the listener that updates our list holders to the correct items.
	 *
	 * @param holder Holder to bind.
	 * @param position Position of item in list.
	 */
	@Override
	public void onBindViewHolder(@NonNull ListViewHolder holder, int position)
	{
		final ListItemProxy item = this.models.get(position);
		final boolean selected = this.tracker != null ? this.tracker.isSelected(item) : false;

		// Notify item of its selected status.
		// This is necessary to maintain selection status on theme change.
		item.setSelected(selected);

		// Update ListViewHolder with new model data.
		holder.bind(item, selected);
	}

	/**
	 * Create new holder.
	 * This listener creates new holders which are used to bind with.
	 *
	 * @param parent Parent view.
	 * @param viewType Type of view.
	 * @return ListViewHolder.
	 */
	@Override
	public ListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
	{
		// Create new TableViewHolder instance.
		final RelativeLayout layout = (RelativeLayout) inflater.inflate(id_holder, null);
		return new ListViewHolder(parent.getContext(), layout);
	}

	/**
	 * Recycle holder for future binding.
	 * This is used when holders are scrolled out of visibility.
	 *
	 * @param holder
	 */
	@Override
	public void onViewRecycled(@NonNull ListViewHolder holder)
	{
		super.onViewRecycled(holder);

		// Release child views for recycled holder.
		final TiViewProxy proxy = holder.getProxy();
		if (proxy != null) {
			proxy.releaseViews();
		}
	}

	/**
	 * Replace models in adapter.
	 *
	 * @param models Replacement models.
	 */
	public void replaceModels(List<ListItemProxy> models)
	{
		this.models = models;
		notifyDataSetChanged();
	}
}
