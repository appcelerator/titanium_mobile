/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2020 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget.listview;

import android.content.Context;
import android.os.Handler;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.titanium.proxy.TiViewProxy;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

public abstract class TiRecyclerViewAdapter<VH extends TiRecyclerViewHolder<V>, V extends TiViewProxy>
	extends RecyclerView.Adapter<VH>
{
	protected Context context;
	protected int id_holder;
	protected LayoutInflater inflater;
	protected List<V> models;
	protected SelectionTracker tracker;

	private Deque<List<V>> pendingUpdates = new ArrayDeque<>();

	public TiRecyclerViewAdapter(@NonNull Context context, @NonNull List<V> models)
	{
		// Obtain context.
		this.context = context;

		// Obtain layout inflater instance.
		this.inflater = LayoutInflater.from(context);

		// Set models.
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
	 * Bind item to holder.
	 * This is the listener that updates our list holders to the correct items.
	 *
	 * @param holder Holder to bind.
	 * @param position Position of item in list.
	 */
	@Override
	public void onBindViewHolder(@NonNull VH holder, int position)
	{
		final V item = this.models.get(position);
		final boolean selected = tracker != null ? tracker.isSelected(item) : false;

		// Update holder with new model data.
		holder.bind(item, selected);
	}

	/**
	 * Get adapter context.
	 *
	 * @return Context
	 */
	public Context getContext()
	{
		return this.context;
	}

	/**
	 * Get selection tracker object.
	 *
	 * @return Selection tracker.
	 */
	public SelectionTracker getTracker()
	{
		return this.tracker;
	}

	/**
	 * Set selection tracker for adapter.
	 *
	 * @param tracker Selection tracker.
	 */
	public void setTracker(SelectionTracker tracker)
	{
		this.tracker = tracker;
	}

	/**
	 * Replace models in adapter.
	 *
	 * @param newModels Replacement models.
	 */
	public void update(final List<V> newModels, boolean force)
	{
		if (force) {
			pendingUpdates.clear();

			// Update adapter for all models.
			notifyDataSetChanged();

			// Update models.
			this.models = newModels;

			return;
		}

		// Queue update.
		pendingUpdates.push(newModels);

		if (pendingUpdates.size() > 1) {

			// Wait for current update to finish.
			return;
		}

		processUpdates(newModels);
	}

	/**
	 * Process diff of adapter models on background thread.
	 *
	 * @param newModels Replacement models.
	 */
	protected void processUpdates(List<V> newModels)
	{
		final Handler handler = new Handler();
		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				// Calculate diff on background thread.
				final DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffCallback(newModels, models));

				handler.post(new Runnable() {
					@Override
					public void run()
					{
						// Dispatch updates on UI thread.
						dispatchUpdates(newModels, diffResult);
					}
				});
			}
		}).start();
	}

	/**
	 * Update adapter based on diff.
	 *
	 * @param models Replacement models.
	 * @param diffResult Difference of models.
	 */
	protected void dispatchUpdates(List<V> models, DiffUtil.DiffResult diffResult)
	{
		pendingUpdates.remove(models);

		if (pendingUpdates.size() > 0) {

			// Obtain latest update.
			final List<V> latestModels = pendingUpdates.pop();

			// Clear all older updates.
			pendingUpdates.clear();

			// Update adapter with latest update.
			processUpdates(latestModels);
			return;
		}

		// Update adapter based on diff.
		diffResult.dispatchUpdatesTo(this);

		// Update models.
		this.models = models;
	}

	/**
	 * Define DiffUtil.Callback to optimize updating the adapter.
	 */
	private class DiffCallback extends DiffUtil.Callback
	{

		List<V> newViews;
		List<V> oldViews;

		public DiffCallback(List<V> newViews, List<V> oldViews)
		{
			this.newViews = newViews;
			this.oldViews = oldViews;
		}

		@Override
		public int getOldListSize()
		{
			return oldViews.size();
		}

		@Override
		public int getNewListSize()
		{
			return newViews.size();
		}

		@Override
		public boolean areItemsTheSame(int oldItemPosition, int newItemPosition)
		{
			// TODO: It may be possible to optimize this?
			return oldViews.get(oldItemPosition).equals(newViews.get(newItemPosition));
		}

		@Override
		public boolean areContentsTheSame(int oldItemPosition, int newItemPosition)
		{
			final V oldView = oldViews.get(oldItemPosition);
			final V newView = newViews.get(newItemPosition);
			final KrollDict oldProperties = oldView.getProperties();
			final KrollDict newProperties = newView.getProperties();

			if (oldProperties == null || newProperties == null) {
				return false;
			}

			// Calculate content specific hashes.
			// Compare properties and children.
			final int oldHash = oldProperties.hashCode() + Arrays.hashCode(oldView.getChildren());
			final int newHash = newProperties.hashCode() + Arrays.hashCode(newView.getChildren());

			return oldHash == newHash;
		}
	}
}
