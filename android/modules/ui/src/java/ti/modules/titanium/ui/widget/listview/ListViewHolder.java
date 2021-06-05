/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2020 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget.listview;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.titanium.R;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiRHelper;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.view.TiBackgroundDrawable;
import org.appcelerator.titanium.view.TiCompositeLayout;
import org.appcelerator.titanium.view.TiUIView;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;

import ti.modules.titanium.ui.UIModule;
import ti.modules.titanium.ui.widget.TiUIListView;

public class ListViewHolder extends TiRecyclerViewHolder
{
	private static final String TAG = "ListViewHolder";

	// Top
	private final TiCompositeLayout header;
	private final TextView headerTitle;

	// Middle
	private final ViewGroup container;
	private final ImageView leftImage;
	private final TiCompositeLayout content;
	private final ImageView rightImage;

	// Bottom
	private final TiCompositeLayout footer;
	private final TextView footerTitle;

	public ListViewHolder(final Context context, final ViewGroup viewGroup)
	{
		super(context, viewGroup);

		// Obtain views from identifiers.
		this.header = viewGroup.findViewById(R.id.titanium_ui_listview_holder_header);

		this.headerTitle = viewGroup.findViewById(R.id.titanium_ui_listview_holder_header_title);

		// Header attributes.
		setTitleAttributes("header", context, this.headerTitle);

		this.container = viewGroup.findViewById(R.id.titanium_ui_listview_holder_outer_content_container);

		this.leftImage = viewGroup.findViewById(R.id.titanium_ui_listview_holder_left_image);

		this.content = viewGroup.findViewById(R.id.titanium_ui_listview_holder_content);

		this.rightImage = viewGroup.findViewById(R.id.titanium_ui_listview_holder_right_image);

		this.footer = viewGroup.findViewById(R.id.titanium_ui_listview_holder_footer);

		this.footerTitle = viewGroup.findViewById(R.id.titanium_ui_listview_holder_footer_title);

		// Footer attributes.
		setTitleAttributes("footer", context, this.footerTitle);
	}

	/**
	 * Bind proxy to holder.
	 *
	 * @param proxy    ListItemProxy to bind.
	 * @param selected Is row selected.
	 */
	public void bind(final ListItemProxy proxy, final boolean selected)
	{
		reset();

		// Update model proxy holder.
		this.proxy = new WeakReference<>(proxy);
		proxy.setHolder(this);

		// Obtain ListView proxy for item.
		final ListViewProxy listViewProxy = proxy.getListViewProxy();
		if (listViewProxy == null) {
			return;
		}
		final KrollDict listViewProperties = listViewProxy.getProperties();

		// Attempt to obtain parent section proxy is available.
		final ListSectionProxy section =
			proxy.getParent() instanceof ListSectionProxy ? (ListSectionProxy) proxy.getParent() : null;

		// Obtain proxy properties.
		final KrollDict properties = proxy.getProperties();

		// Set minimum row height.
		final String rawMinHeight = properties.optString(TiC.PROPERTY_MIN_ROW_HEIGHT, "0");
		final int minHeight = TiConvert.toTiDimension(rawMinHeight, TiDimension.TYPE_HEIGHT).getAsPixels(itemView);
		this.content.setMinimumHeight(minHeight);

		// Handle selection checkmark.
		if (listViewProperties.optBoolean(TiC.PROPERTY_SHOW_SELECTION_CHECK, false)
			&& listViewProperties.optBoolean(TiC.PROPERTY_EDITING, false)
			&& listViewProperties.optBoolean(TiC.PROPERTY_ALLOWS_SELECTION_DURING_EDITING, false)
			&& listViewProperties.optBoolean(TiC.PROPERTY_ALLOWS_MULTIPLE_SELECTION_DURING_EDITING, false)
			&& !proxy.isPlaceholder()) {

			if (selected) {
				this.leftImage.setImageDrawable(checkcircleDrawable);
			} else {
				this.leftImage.setImageDrawable(circleDrawable);
			}
			this.leftImage.setVisibility(View.VISIBLE);
		}

		// Handle accessory type icon.
		if (properties.containsKeyAndNotNull(TiC.PROPERTY_ACCESSORY_TYPE)) {
			final int accessorType = properties.optInt(TiC.PROPERTY_ACCESSORY_TYPE, UIModule.LIST_ACCESSORY_TYPE_NONE);

			switch (accessorType) {
				case UIModule.LIST_ACCESSORY_TYPE_CHECKMARK:
					this.rightImage.setImageDrawable(checkDrawable);
					break;
				case UIModule.LIST_ACCESSORY_TYPE_DETAIL:
					this.rightImage.setImageDrawable(moreDrawable);
					break;
				case UIModule.LIST_ACCESSORY_TYPE_DISCLOSURE:
					this.rightImage.setImageDrawable(disclosureDrawable);
			}
			if (accessorType != UIModule.LIST_ACCESSORY_TYPE_NONE) {
				this.rightImage.setVisibility(View.VISIBLE);
			}
		}

		// Display drag drawable when item can move.
		final boolean isEditing = listViewProperties.optBoolean(TiC.PROPERTY_EDITING, false);
		final boolean canMove = properties.optBoolean(TiC.PROPERTY_CAN_MOVE,
			listViewProperties.optBoolean(TiC.PROPERTY_CAN_MOVE, false));
		if (isEditing && canMove) {
			this.rightImage.setImageDrawable(dragDrawable);
			this.rightImage.setVisibility(View.VISIBLE);
		}

		if (proxy != null) {
			// Update list item proxy's activity in case it has changed, such as after a dark/light theme change.
			final Context context = this.itemView.getContext();
			if ((context instanceof Activity) && (proxy.getActivity() != context)) {
				proxy.releaseViews();
				proxy.setActivity((Activity) context);
			}

			// Get or create the view. (Must be called after updating activity above.)
			final TiUIView view = proxy.getOrCreateView();
			if (view != null) {
				final ViewGroup borderView = (ViewGroup) view.getOuterView();
				final ViewGroup nativeView = (ViewGroup) view.getNativeView();

				if (nativeView != null) {
					final TiUIListView listView = (TiUIListView) listViewProxy.getOrCreateView();
					final View nativeListView = listView.getOuterView();
					final ViewGroup parentView = (ViewGroup) borderView.getParent();

					// Obtain background drawable.
					Drawable backgroundDrawable = view.getBackground();
					if (backgroundDrawable == null
							&& properties.containsKeyAndNotNull(TiC.PROPERTY_BACKGROUND_COLOR)) {
						backgroundDrawable = nativeView.getBackground();
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

					if (parentView != null) {
						parentView.removeView(borderView);
					}

					final boolean touchFeedback = listViewProperties.optBoolean(TiC.PROPERTY_TOUCH_FEEDBACK, false);
					final String touchFeedbackColor =
						listViewProperties.optString(TiC.PROPERTY_TOUCH_FEEDBACK_COLOR, null);

					// Set ripple background.
					if (touchFeedback) {
						backgroundDrawable = generateRippleDrawable(backgroundDrawable, touchFeedbackColor);
					}

					// Support selected backgrounds.
					this.container.setBackground(generateSelectedDrawable(properties, backgroundDrawable));
					this.container.setActivated(selected);

					// Allow states to bubble up for ripple effect.
					borderView.setAddStatesFromChildren(true);

					// Amend maximum size for content to parent ListView measured height.
					this.content.setChildFillHeight(nativeListView.getMeasuredHeight());

					// Add ListViewItem to content.
					this.content.addView(borderView, view.getLayoutParams());
					this.content.setVisibility(View.VISIBLE);
				}
			}
		}

		if (section == null) {

			// Handle `header` and `footer` for rows without a parent section.
			setHeaderFooter(listViewProxy, properties, true, true);

		} else {

			// Handle `header` and `footer` for  rows with a parent section.
			// Obtain parent section properties.
			final KrollDict sectionProperties = section.getProperties();
			final int indexInSection = proxy.getIndexInSection();
			final int filteredIndex = proxy.getFilteredIndex();

			if (indexInSection == 0 || filteredIndex == 0 || proxy.isPlaceholder()) {

				// Only set header on first row in section.
				setHeaderFooter(listViewProxy, sectionProperties, true, false);
			}
			if ((indexInSection >= section.getItems().length - 1)
				|| (filteredIndex >= section.getFilteredItemCount() - 1)
				|| proxy.isPlaceholder()) {

				// Only set footer on last row in section.
				setHeaderFooter(listViewProxy, sectionProperties, false, true);
			}
		}
	}

	/**
	 * Reset row into nominal state.
	 */
	private void reset()
	{
		this.header.removeAllViews();
		this.content.removeAllViews();
		this.footer.removeAllViews();

		this.header.setVisibility(View.GONE);
		this.headerTitle.setVisibility(View.GONE);
		this.footer.setVisibility(View.GONE);
		this.footerTitle.setVisibility(View.GONE);
		this.content.setVisibility(View.GONE);
		this.leftImage.setVisibility(View.GONE);
		this.rightImage.setVisibility(View.GONE);
	}

	/**
	 * Set header and footer views of holder.
	 *
	 * @param listViewProxy ListView proxy.
	 * @param properties   Properties containing header and footer entires.
	 * @param updateHeader Boolean to determine if the header should be updated.
	 * @param updateFooter Boolean to determine if the footer should be updated.
	 */
	private void setHeaderFooter(TiViewProxy listViewProxy,
								 KrollDict properties,
								 boolean updateHeader,
								 boolean updateFooter)
	{
		if (listViewProxy == null) {
			return;
		}

		final View nativeListView = listViewProxy.getOrCreateView().getNativeView();
		if (nativeListView == null) {
			return;
		}

		final Context context = this.itemView.getContext();

		// Handle `header` and `footer`.
		if (updateHeader) {
			if (properties.containsKeyAndNotNull(TiC.PROPERTY_HEADER_TITLE)) {

				// Handle header title.
				this.headerTitle.setText(properties.getString(TiC.PROPERTY_HEADER_TITLE));
				this.headerTitle.setVisibility(View.VISIBLE);

			} else if (properties.containsKeyAndNotNull(TiC.PROPERTY_HEADER_VIEW)) {

				// Handle header view.
				final TiViewProxy headerProxy = (TiViewProxy) properties.get(TiC.PROPERTY_HEADER_VIEW);
				if ((context instanceof Activity) && (headerProxy.getActivity() != context)) {
					headerProxy.releaseViews();
					headerProxy.setActivity((Activity) context);
				}

				final TiUIView view = headerProxy.getOrCreateView();
				if (view != null) {
					final View headerView = view.getOuterView();
					if (headerView != null) {
						final ViewGroup parent = (ViewGroup) headerView.getParent();
						if (parent != null) {
							parent.removeView(headerView);
						}

						// Amend maximum size for header to parent ListView measured height.
						this.header.setChildFillHeight(nativeListView.getMeasuredHeight());

						this.header.addView(headerView, view.getLayoutParams());
						this.header.setVisibility(View.VISIBLE);
					}
				}
			}
		}
		if (updateFooter) {
			if (properties.containsKeyAndNotNull(TiC.PROPERTY_FOOTER_TITLE)) {

				// Handle footer title.
				this.footerTitle.setText(properties.getString(TiC.PROPERTY_FOOTER_TITLE));
				this.footerTitle.setVisibility(View.VISIBLE);

			} else if (properties.containsKeyAndNotNull(TiC.PROPERTY_FOOTER_VIEW)) {

				// Handle footer view.
				final TiViewProxy footerProxy = (TiViewProxy) properties.get(TiC.PROPERTY_FOOTER_VIEW);
				if ((context instanceof Activity) && (footerProxy.getActivity() != context)) {
					footerProxy.releaseViews();
					footerProxy.setActivity((Activity) context);
				}

				final TiUIView view = footerProxy.getOrCreateView();
				if (view != null) {
					final View footerView = view.getOuterView();
					if (footerView != null) {
						final ViewGroup parent = (ViewGroup) footerView.getParent();
						if (parent != null) {
							parent.removeView(footerView);
						}

						// Amend maximum size for footer to parent ListView measured height.
						this.footer.setChildFillHeight(nativeListView.getMeasuredHeight());

						this.footer.addView(footerView, view.getLayoutParams());
						this.footer.setVisibility(View.VISIBLE);
					}
				}
			}
		}
	}

	/**
	 * Set header or footer title attribute values.
	 *
	 * @param prefix  Attribute prefix (e.g: 'header' or 'footer')
	 * @param context Application context.
	 * @param title   Header or Footer TextView.
	 */
	private void setTitleAttributes(final String prefix, final Context context, final TextView title)
	{
		final Resources.Theme theme = context.getTheme();

		TypedValue sizeValue = new TypedValue();
		TypedValue styleValue = new TypedValue();
		TypedValue colorValue = new TypedValue();
		TypedValue backgroundValue = new TypedValue();
		TypedValue backgroundColorValue = new TypedValue();

		// Obtain header or footer theme attributes.
		try {
			theme.resolveAttribute(TiRHelper.getResource("attr.ti_" + prefix + "TitleSize"),
				sizeValue, true);
		} catch (TiRHelper.ResourceNotFoundException e) {
		}
		try {
			theme.resolveAttribute(TiRHelper.getResource("attr.ti_" + prefix + "TitleStyle"),
				styleValue, true);
		} catch (TiRHelper.ResourceNotFoundException e) {
		}
		try {
			theme.resolveAttribute(TiRHelper.getResource("attr.ti_" + prefix + "TitleColor"),
				colorValue, true);
		} catch (TiRHelper.ResourceNotFoundException e) {
		}
		try {
			theme.resolveAttribute(TiRHelper.getResource("attr.ti_" + prefix + "TitleBackground"),
				backgroundValue, true);
		} catch (TiRHelper.ResourceNotFoundException e) {
		}
		try {
			theme.resolveAttribute(TiRHelper.getResource("attr.ti_" + prefix + "TitleBackgroundColor"),
				backgroundColorValue, true);
		} catch (TiRHelper.ResourceNotFoundException e) {
		}

		String size = null;
		String style = null;
		if (sizeValue.resourceId != 0) {
			size = resources.getString(sizeValue.resourceId);
		}
		if (styleValue.resourceId != 0) {
			style = resources.getString(styleValue.resourceId);
		}

		// Set title size and style.
		TiUIHelper.styleText(title, null, size, null, style);

		if (colorValue.resourceId != 0) {

			// Set title text color.
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				title.setTextColor(resources.getColor(colorValue.resourceId, theme));
			} else {
				title.setTextColor(resources.getColor(colorValue.resourceId));
			}

		} else {

			// Set title default text color.
			title.setTextColor(Color.WHITE);
		}

		if (backgroundValue.resourceId != 0) {

			// Set title background drawable.
			title.setBackground(resources.getDrawable(backgroundValue.resourceId, theme));

		} else if (backgroundColorValue.resourceId != 0) {

			// Set title background color.
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				title.setBackgroundColor(resources.getColor(backgroundColorValue.resourceId, theme));
			} else {
				title.setBackgroundColor(resources.getColor(backgroundColorValue.resourceId));
			}

		} else {

			// Set title default background color.
			title.setBackgroundColor(COLOR_GRAY);
		}
	}
}
