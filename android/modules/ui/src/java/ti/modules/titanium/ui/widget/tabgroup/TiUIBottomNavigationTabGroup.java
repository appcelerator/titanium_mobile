/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2018-Present by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget.tabgroup;

import java.util.ArrayList;
import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import com.google.android.material.bottomnavigation.BottomNavigationItemView;
import com.google.android.material.bottomnavigation.BottomNavigationMenuView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomnavigation.LabelVisibilityMode;

import android.view.MenuItem;
import android.view.View;
import android.view.ViewParent;

import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.view.TiCompositeLayout;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewParent;

import com.google.android.material.bottomnavigation.BottomNavigationItemView;
import com.google.android.material.bottomnavigation.BottomNavigationMenuView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomnavigation.LabelVisibilityMode;

import ti.modules.titanium.ui.TabGroupProxy;
import ti.modules.titanium.ui.TabProxy;

/**
 * TabGroup implementation using BottomNavigationView as a controller.
 */
public class TiUIBottomNavigationTabGroup extends TiUIAbstractTabGroup implements MenuItem.OnMenuItemClickListener
{
	// region private fields
	private int mBottomNavigationHeightValue;
	// BottomNavigationView lacks anything similar to onTabUnselected method of TabLayout.OnTabSelectedListener.
	// We track the previously selected item index manually to mimic the behavior in order to keep parity across styles.
	private int currentlySelectedIndex = -1;
	private BottomNavigationView mBottomNavigationView;
	private ArrayList<MenuItem> mMenuItemsArray = new ArrayList<>();
	// endregion

	public TiUIBottomNavigationTabGroup(TabGroupProxy proxy, TiBaseActivity activity)
	{
		super(proxy, activity);
	}

	// Overriding addTab method to provide a proper guard for trying to add more tabs than the limit
	// for BottomNavigationView class.
	@Override
	public void addTab(TabProxy tabProxy)
	{
		if (this.mBottomNavigationView == null) {
			return;
		}
		final int MAX_TABS = this.mBottomNavigationView.getMaxItemCount();
		if (this.tabs.size() < MAX_TABS) {
			super.addTab(tabProxy);
		} else {
			Log.w(TAG, "Bottom style TabGroup cannot have more than " + MAX_TABS + " tabs.");
		}
	}

	@Override
	public void addViews(TiBaseActivity activity)
	{
		// Manually calculate the proper position of the BottomNavigationView.
		int resourceID = activity.getResources().getIdentifier("design_bottom_navigation_height", "dimen",
															   activity.getPackageName());
		this.mBottomNavigationHeightValue = activity.getResources().getDimensionPixelSize(resourceID);

		// Create the bottom tab navigation view.
		this.mBottomNavigationView = new BottomNavigationView(activity) {
			@Override
			protected boolean fitSystemWindows(Rect insets)
			{
				// Remove top inset when bottom tab bar is to be extended beneath system insets.
				// This prevents Google from blindly padding top of tab bar based on this inset.
				if ((insets != null) && getFitsSystemWindows()) {
					insets = new Rect(insets);
					insets.top = 0;
				}
				super.fitSystemWindows(insets);
				return false;
			}

			@Override
			protected void onLayout(boolean hasChanged, int left, int top, int right, int bottom)
			{
				// Update bottom inset based on tab bar's height and position in window.
				super.onLayout(hasChanged, left, top, right, bottom);
				insetsProvider.setBottomBasedOn(this);
			}
		};
		this.mBottomNavigationView.setFitsSystemWindows(true);

		// Add tab bar and view pager to the root Titanium view.
		// Note: If getFitsSystemWindows() returns false, then Titanium window's "extendSafeArea" is set true.
		//       This means the bottom tab bar should overlap/overlay the view pager content.
		TiCompositeLayout compositeLayout = (TiCompositeLayout) activity.getLayout();
		{
			TiCompositeLayout.LayoutParams params = new TiCompositeLayout.LayoutParams();
			params.autoFillsWidth = true;
			params.autoFillsHeight = true;
			if (compositeLayout.getFitsSystemWindows()) {
				params.optionBottom = new TiDimension(mBottomNavigationHeightValue, TiDimension.TYPE_BOTTOM);
			}
			compositeLayout.addView(this.tabGroupViewPager, params);
		}
		{
			TiCompositeLayout.LayoutParams params = new TiCompositeLayout.LayoutParams();
			params.autoFillsWidth = true;
			params.optionBottom = new TiDimension(0, TiDimension.TYPE_BOTTOM);
			compositeLayout.addView(this.mBottomNavigationView, params);
		}

		// Set the ViewPager as a native view.
		setNativeView(this.tabGroupViewPager);
	}

	/**
	 * Handle the removing of the controller from the UI layout when tab navigation is disabled.
	 * @param disable
	 */
	@Override
	public void disableTabNavigation(boolean disable, boolean animated)
	{
		super.disableTabNavigation(disable, animated);

		// Resize the view pager (the tab's content) to compensate for shown/hidden tab bar.
		// Not applicable if Titanium "extendSafeArea" is true, because tab bar overlaps content in this case.
		ViewParent viewParent = this.tabGroupViewPager.getParent();
		if ((viewParent instanceof View) && ((View) viewParent).getFitsSystemWindows()) {
			TiCompositeLayout.LayoutParams params = new TiCompositeLayout.LayoutParams();
			params.autoFillsWidth = true;
			params.optionBottom = new TiDimension(disable ? 0 : mBottomNavigationHeightValue, TiDimension.TYPE_BOTTOM);
			this.tabGroupViewPager.setLayoutParams(params);
		}

		if (animated) {
			if (disable) {
				this.mBottomNavigationView.animate().translationY(mBottomNavigationHeightValue).setDuration(200);
			} else {
				this.mBottomNavigationView.animate().translationY(0).setDuration(200);
			}
		} else {
			this.mBottomNavigationView.setVisibility(disable ? View.GONE : View.VISIBLE);
			this.mBottomNavigationView.requestLayout();
		}

		// Update top inset. (Will remove bottom inset if tab bar is "gone".)
		this.insetsProvider.setBottomBasedOn(this.mBottomNavigationView);
	}

	@Override
	public void addTabItemInController(TiViewProxy tabProxy)
	{
		// Guard for the limit of tabs in the BottomNavigationView.
		if (this.mMenuItemsArray.size() == 5) {
			Log.e(TAG, "Trying to add more than five tabs in a TabGroup with TABS_STYLE_BOTTOM_NAVIGATION style.");
			return;
		}
		// Create a new item with id representing its index in mMenuItemsArray.
		MenuItem menuItem = this.mBottomNavigationView.getMenu().add(null);
		// Set the click listener.
		menuItem.setOnMenuItemClickListener(this);
		// Add the MenuItem to the menu of BottomNavigationView.
		this.mMenuItemsArray.add(menuItem);
		// Get the MenuItem index
		int index = this.mMenuItemsArray.size() - 1;
		updateDrawablesAfterNewItem(index);
		// Handle shift mode.
		final int shiftMode = proxy.getProperties().optInt(TiC.PROPERTY_SHIFT_MODE, 1);
		switch (shiftMode) {
			case 0:
				this.mBottomNavigationView.setLabelVisibilityMode(LabelVisibilityMode.LABEL_VISIBILITY_LABELED);
				break;
			case 1:
				this.mBottomNavigationView.setLabelVisibilityMode(LabelVisibilityMode.LABEL_VISIBILITY_AUTO);
				break;
			case 2:
				// NOTE: Undocumented for now, will create new property that has parity with iOS.
				this.mBottomNavigationView.setLabelVisibilityMode(LabelVisibilityMode.LABEL_VISIBILITY_UNLABELED);
				break;
		}
	}

	private void updateDrawablesAfterNewItem(int index)
	{
		// Set the title.
		updateTabTitle(index);
		// Set the icon.
		updateTabIcon(index);
		for (int i = 0; i < this.mBottomNavigationView.getMenu().size(); i++) {
			// Set the title text color.
			updateTabTitleColor(i);
			// Set the background drawable.
			updateTabBackgroundDrawable(i);
		}
	}

	/**
	 * Remove an item from the BottomNavigationView for a specific index.
	 *
	 * @param position the position of the removed item.
	 */
	@Override
	public void removeTabItemFromController(int position)
	{
		this.mBottomNavigationView.getMenu().clear();
		this.mMenuItemsArray.clear();
		for (TiUITab tabView : tabs) {
			addTabItemInController(tabView.getProxy());
		}
	}

	/**
	 * Select an item from the BottomNavigationView with a specific position.
	 *
	 * @param position the position of the item to be selected.
	 */
	@Override
	public void selectTabItemInController(int position)
	{
		// Fire the UNSELECTED event from the currently selected tab.
		if ((currentlySelectedIndex >= 0) && (currentlySelectedIndex < this.tabs.size()) && (getProxy() != null)) {
			TiViewProxy tabProxy = this.tabs.get(currentlySelectedIndex).getProxy();
			if (tabProxy != null) {
				tabProxy.fireEvent(TiC.EVENT_UNSELECTED, null, false);
			}
		}
		currentlySelectedIndex = position;
		// The ViewPager has changed current page from swiping.
		// Or any other interaction with it that can cause page change.
		// Set the proper item in the controller.
		this.mBottomNavigationView.getMenu().getItem(position).setChecked(true);
		// Trigger the select event firing for the newly selected tab.
		((TabGroupProxy) getProxy()).onTabSelected(position);
	}

	@Override
	public void setBackgroundColor(int colorInt)
	{
		this.mBottomNavigationView.setBackgroundColor(colorInt);
	}

	@Override
	public void updateTabBackgroundDrawable(int index)
	{
		try {
			BottomNavigationMenuView bottomMenuView =
				((BottomNavigationMenuView) this.mBottomNavigationView.getChildAt(0));
			// BottomNavigationMenuView rebuilds itself after adding a new item, so we need to reset the colors each time.
			TiViewProxy tabProxy = tabs.get(index).getProxy();
			Drawable backgroundDrawable = createBackgroundDrawableForState(tabProxy, android.R.attr.state_checked);
			bottomMenuView.getChildAt(index).setBackground(backgroundDrawable);
		} catch (Exception e) {
			Log.w(TAG, WARNING_LAYOUT_MESSAGE);
		}
	}

	@Override
	public void updateTabTitle(int index)
	{
		if ((index < 0) || (index >= this.tabs.size())) {
			return;
		}

		TiViewProxy tabProxy = this.tabs.get(index).getProxy();
		if (tabProxy == null) {
			return;
		}

		String title = TiConvert.toString(tabProxy.getProperty(TiC.PROPERTY_TITLE));
		this.mBottomNavigationView.getMenu().getItem(index).setTitle(title);
	}

	@SuppressLint("RestrictedApi")
	@Override
	public void updateTabTitleColor(int index)
	{
		try {
			BottomNavigationMenuView bottomMenuView =
				((BottomNavigationMenuView) this.mBottomNavigationView.getChildAt(0));
			// BottomNavigationMenuView rebuilds itself after adding a new item, so we need to reset the colors each time.
			TiViewProxy tabProxy = tabs.get(index).getProxy();
			// Set the TextView textColor.
			((BottomNavigationItemView) bottomMenuView.getChildAt(index))
				.setTextColor(textColorStateList(tabProxy, android.R.attr.state_checked));
		} catch (Exception e) {
			Log.w(TAG, WARNING_LAYOUT_MESSAGE);
		}
	}

	@Override
	public void updateTabIcon(int index)
	{
		if ((index < 0) || (index >= this.tabs.size())) {
			return;
		}

		TiViewProxy tabProxy = this.tabs.get(index).getProxy();
		if (tabProxy == null) {
			return;
		}

		final Drawable drawable = TiUIHelper.getResourceDrawable(tabProxy.getProperty(TiC.PROPERTY_ICON));
		this.mBottomNavigationView.getMenu().getItem(index).setIcon(drawable);
	}

	@Override
	public String getTabTitle(int index)
	{
		// Validate index.
		if (index < 0 || index > tabs.size() - 1) {
			return null;
		}
		return this.mBottomNavigationView.getMenu().getItem(index).getTitle().toString();
	}

	/**
	 * After a menu item is clicked this method sends the proper index to the ViewPager to a select
	 * a page. Also takes care of sending SELECTED/UNSELECTED events from the proper tabs.
	 * @param item
	 * @return
	 */
	@Override
	public boolean onMenuItemClick(MenuItem item)
	{
		// The controller has changed its selected item.
		int index = this.mMenuItemsArray.indexOf(item);
		// Guard for clicking on the currently selected tab.
		// This is required to have parity with the default style tab.
		if (index == this.currentlySelectedIndex) {
			return true;
		}
		if ((index != currentlySelectedIndex) && (getProxy() != null)) {
			if ((currentlySelectedIndex >= 0) && (currentlySelectedIndex < this.tabs.size())) {
				TiViewProxy tabProxy = this.tabs.get(currentlySelectedIndex).getProxy();
				if (tabProxy != null) {
					tabProxy.fireEvent(TiC.EVENT_UNSELECTED, null, false);
				}
			}
			currentlySelectedIndex = index;
		}
		// Make the ViewPager to select the proper page too.
		selectTab(index);

		// Trigger the select event firing for the new tab.
		((TabGroupProxy) getProxy()).onTabSelected(index);
		return false;
	}

	private void updateIconTint()
	{
		for (int i = 0; i < this.tabs.size(); i++) {
			final TiUITab tab = this.tabs.get(i);
			if (tab.getProxy() != null) {
				final TiViewProxy tabProxy = tab.getProxy();
				final boolean selected = i == currentlySelectedIndex;
				Drawable drawable = this.mBottomNavigationView.getMenu().getItem(i).getIcon();
				drawable = updateIconTint(tabProxy, drawable, selected);
				this.mBottomNavigationView.getMenu().getItem(i).setIcon(drawable);
			}
		}
	}

	@Override
	public void selectTab(int tabIndex)
	{
		super.selectTab(tabIndex);

		updateIconTint();
		updateTabBackgroundDrawable(tabIndex);
	}
}
