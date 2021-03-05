/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget.tabgroup;

import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.ActivityProxy;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.proxy.TiWindowProxy;
import org.appcelerator.titanium.util.TiColorHelper;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiRHelper;
import org.appcelerator.titanium.view.TiInsetsProvider;
import org.appcelerator.titanium.view.TiUIView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

import ti.modules.titanium.ui.TabGroupProxy;
import ti.modules.titanium.ui.TabProxy;

/**
 *  Abstract class representing Tab Navigation in Titanium. Abstract methods in it
 *  are declared to provide an interface to connect different UI components acting
 *  as a controller. All the work done by the ViewPager is happening in this class.
 */
public abstract class TiUIAbstractTabGroup extends TiUIView
{
	/**
	 * Adds the ViewPager and the Controller to the activity's layout.
	 *
	 * @param activity the activity to be attached to.
	 */
	public abstract void addViews(TiBaseActivity activity);

	/**
	 * Adds an item in the Controller for a corresponding tab.
	 *
	 * @param tabProxy proxy to be parsed for tab item.
	 */
	public abstract void addTabItemInController(TiViewProxy tabProxy);

	/**
	 * Removes an item from the TabGroup controller for a specific position.
	 *
	 * @param position the position of the removed item.
	 */
	public abstract void removeTabItemFromController(int position);

	/**
	 * Selects an item from the Controller with a specific position.
	 *
	 * @param position the position of the item to be selected.
	 */
	public abstract void selectTabItemInController(int position);

	/**
	 * Changes the controller's background color.
	 *
	 * @param colorInt the new background color.
	 */
	public abstract void setBackgroundColor(int colorInt);

	/**
	 * Update the tab badge
	 *
	 * @param index of the Tab to update.
	 */
	public abstract void updateBadge(int index);

	/**
	 * Update the tab badge color
	 *
	 * @param index of the Tab to update.
	 */
	public abstract void updateBadgeColor(int index);

	/**
	 * Updates the tab's background drawables to the proper color states.
	 *
	 * @param index of the Tab to update.
	 */
	public abstract void updateTabBackgroundDrawable(int index);

	/**
	 * Update the tab's title to the proper text.
	 *
	 * @param index of the Tab to update.
	 */
	public abstract void updateTabTitle(int index);

	/**
	 * Updates the tab's title to the proper color states.
	 *
	 * @param index of the Tab to update.
	 */
	public abstract void updateTabTitleColor(int index);

	/**
	 * Updates the tabs' icon to the proper image.
	 *
	 * @param index of the Tab to update.
	 */
	public abstract void updateTabIcon(int index);

	/**
	 * Returns the value of the Tab's title.
	 *
	 * @param index the index of the Tab.
	 * @return
	 */
	public abstract String getTabTitle(int index);

	// region protected fields
	protected final static String TAG = "TiUIAbstractTabGroup";
	protected static final String WARNING_LAYOUT_MESSAGE =
		"Trying to customize an unknown layout, sticking to the default one";

	protected boolean swipeable = true;
	protected boolean smoothScrollOnTabClick = true;
	protected boolean tabsDisabled = false;
	protected int numTabsWhenDisabled;
	protected int colorPrimaryInt;
	protected PagerAdapter tabGroupPagerAdapter;
	protected ViewPager tabGroupViewPager;
	protected TiInsetsProvider insetsProvider = new TiInsetsProvider();
	// endregion

	// region private fields
	private boolean autoTabTitle = false;
	private int textColorInt;
	private int unselectedTextColorInt;
	private int lastTab = -1;
	private AtomicLong fragmentIdGenerator = new AtomicLong();
	private ArrayList<Long> tabFragmentIDs = new ArrayList<Long>();
	protected ArrayList<TiUITab> tabs = new ArrayList<TiUITab>();
	// endregion

	public TiUIAbstractTabGroup(final TabGroupProxy proxy, TiBaseActivity activity)
	{
		super(proxy);

		// Fetch primary background and text colors from ActionBar style assigned to activity theme.
		// Note: We use ActionBar style for backward compatibility with Titanium versions older than 8.0.0.
		this.colorPrimaryInt = 0xFF212121; // Default to dark gray.
		this.textColorInt = 0xFFFFFFFF;    // Default to white.
		this.unselectedTextColorInt = 0xFFBBBBBB; // Default to light gray.
		try {
			final int styleAttributeId = TiRHelper.getResource("attr.actionBarStyle");
			final int[] idArray = new int[] {
				TiRHelper.getResource("attr.colorPrimary"),
				TiRHelper.getResource("attr.textColorPrimary"),
				TiRHelper.getResource("attr.colorControlNormal")
			};
			final TypedArray typedArray = activity.obtainStyledAttributes(null, idArray, styleAttributeId, 0);

			this.colorPrimaryInt = typedArray.getColor(0, this.colorPrimaryInt);
			this.textColorInt = typedArray.getColor(1, this.textColorInt);
			this.unselectedTextColorInt = typedArray.getColor(2, this.unselectedTextColorInt);
			typedArray.recycle();
		} catch (Exception ex) {
			Log.e(TAG, "Failed to fetch color from theme.", ex);
		}

		this.tabGroupPagerAdapter = new TabGroupFragmentPagerAdapter(activity.getSupportFragmentManager());

		this.tabGroupViewPager = (new ViewPager(proxy.getActivity()) {
			@Override
			public boolean onTouchEvent(MotionEvent event)
			{
				return swipeable && !tabsDisabled ? super.onTouchEvent(event) : false;
			}

			@Override
			public boolean onInterceptTouchEvent(MotionEvent event)
			{
				return swipeable && !tabsDisabled ? super.onInterceptTouchEvent(event) : false;
			}
		});

		// The default pager limit is 1. This means only 1 tab to the left and right of current tab is kept in memory.
		// Tab fragments outside of this limit will be destroyed and later recreated/restored when selecting that tab.
		// We don't want to lose the current UI state of offscreen tabs. So, set limit to a high value to avoid it.
		this.tabGroupViewPager.setOffscreenPageLimit(128);

		this.tabGroupViewPager.setId(android.R.id.tabcontent);
		this.tabGroupViewPager.setAdapter(this.tabGroupPagerAdapter);

		// Add the tab group's custom insets provider to the activity.
		// This provides the tab bar as an inset so that it can be excluded from the activity's safe-area.
		activity.addCustomInsetsProvider(this.insetsProvider);

		addViews(activity);
	}

	/**
	 * Sets the tabsDisabled flag to whether the tabs navigation is enabled/disabled.
	 * @param value boolean to set for the flag.
	 */
	public void disableTabNavigation(boolean value)
	{
		this.tabsDisabled = value;
		this.numTabsWhenDisabled = tabs.size();
	}

	/**
	 * Method for handling of the setActiveTab. It is used to set the currently selected page
	 * through the code and not clicking/swiping.
	 * @param tabProxy the TabProxy instance to be set as currently selected
	 */
	public void selectTab(TabProxy tabProxy)
	{
		int index = ((TabGroupProxy) getProxy()).getTabIndex(tabProxy);
		// Guard for trying to set a tab, that is not part of the group, as active.
		if (index != -1 && !tabsDisabled) {
			selectTabItemInController(index);
			selectTab(index);
		}
	}

	/**
	 * Add the provided tab to this group.
	 *
	 * Implementations may automatically select the first tab
	 * added, but must not call {@link TabGroupProxy#onTabSelected(TabProxy)}
	 * when doing so.
	 */
	public void addTab(TabProxy tabProxy)
	{
		TiUITab abstractTab = new TiUITab(tabProxy);
		tabs.add(abstractTab);
		tabFragmentIDs.add(fragmentIdGenerator.getAndIncrement());

		this.tabGroupPagerAdapter.notifyDataSetChanged();
		addTabItemInController(tabProxy);
	}

	/**
	 * Method for creating a ColorStateList instance usef for item in the Controller.
	 * It creates a ColorStateList with two states - one for the provided parameter and
	 * one for the negative value of the provided parameter.
	 * If the properties are not set the method falls back to the textColorPrimary of the
	 * current theme.
	 *
	 * @param tabProxy proxy from which are the values taken.
	 * @param stateToUse appropriate state for the Controller type.
	 * @return ColorStateList for the provided state.
	 */
	protected ColorStateList textColorStateList(TiViewProxy tabProxy, int stateToUse)
	{
		if (getProxy() == null || tabProxy == null) {
			return null;
		}

		int[][] textColorStates = new int[][] { new int[] { -stateToUse }, new int[] { stateToUse } };
		int[] textColors = { this.unselectedTextColorInt, this.textColorInt };

		final KrollDict tabProperties = tabProxy.getProperties();
		final KrollDict properties = getProxy().getProperties();

		if (tabProperties.containsKeyAndNotNull(TiC.PROPERTY_TITLE_COLOR)
			|| properties.containsKeyAndNotNull(TiC.PROPERTY_TITLE_COLOR)) {
			final String colorString = tabProperties.optString(TiC.PROPERTY_TITLE_COLOR,
				properties.getString(TiC.PROPERTY_TITLE_COLOR));
			textColors[0] = TiColorHelper.parseColor(colorString);
		}
		if (tabProperties.containsKeyAndNotNull(TiC.PROPERTY_ACTIVE_TITLE_COLOR)
			|| properties.containsKeyAndNotNull(TiC.PROPERTY_ACTIVE_TITLE_COLOR)) {
			final String colorString = tabProperties.optString(TiC.PROPERTY_ACTIVE_TITLE_COLOR,
				properties.getString(TiC.PROPERTY_ACTIVE_TITLE_COLOR));
			textColors[1] = TiColorHelper.parseColor(colorString);
		}
		return new ColorStateList(textColorStates, textColors);
	}

	/**
	 * Method for creating a RippleDrawable to be used as a bacgkround for an item in the Controller.
	 * Creates the RippleDrawable for two states - the provided state and its negative value.
	 * If the properties are not set the method falls back to the colorPrimary of the current theme.
	 * The previous implementation of TabGroup added the ripple effect by default for tabs, thus this
	 * method is manually adding it.
	 *
	 * @param tabProxy proxy from which are the values taken.
	 * @param stateToUse appropriate state for the Controller type.
	 * @return RippleDrawable for the provided state.
	 */
	protected Drawable createBackgroundDrawableForState(TiViewProxy tabProxy, int stateToUse)
	{
		StateListDrawable stateListDrawable = new StateListDrawable();
		int colorInt;
		// If the TabGroup has backgroundColor property, use it. If not - use the primaryColor of the theme.
		colorInt = proxy.hasPropertyAndNotNull(TiC.PROPERTY_TABS_BACKGROUND_COLOR)
					   ? TiColorHelper.parseColor(proxy.getProperty(TiC.PROPERTY_TABS_BACKGROUND_COLOR).toString())
					   : this.colorPrimaryInt;
		// If the Tab has its own backgroundColor property, use it instead.
		colorInt = tabProxy.hasPropertyAndNotNull(TiC.PROPERTY_BACKGROUND_COLOR)
					   ? TiColorHelper.parseColor(tabProxy.getProperty(TiC.PROPERTY_BACKGROUND_COLOR).toString())
					   : colorInt;
		stateListDrawable.addState(new int[] { -stateToUse }, new ColorDrawable(colorInt));
		// Take the TabGroup tabsBackgroundSelectedProperty.
		colorInt =
			proxy.hasPropertyAndNotNull(TiC.PROPERTY_TABS_BACKGROUND_SELECTED_COLOR)
				? TiColorHelper.parseColor(proxy.getProperty(TiC.PROPERTY_TABS_BACKGROUND_SELECTED_COLOR).toString())
				: colorInt;
		// If a tab specific background color is defined for selected state, use it instead.
		colorInt =
			tabProxy.hasPropertyAndNotNull(TiC.PROPERTY_BACKGROUND_FOCUSED_COLOR)
				? TiColorHelper.parseColor(tabProxy.getProperty(TiC.PROPERTY_BACKGROUND_FOCUSED_COLOR).toString())
				: colorInt;
		stateListDrawable.addState(new int[] { stateToUse }, new ColorDrawable(colorInt));

		// ActionBar Tabs had ripple effect by default, but support library TabLayout does not have ripple effect
		// out of the box, so we create a ripple drawable for that.
		int[][] rippleStates = new int[][] { new int[] { android.R.attr.state_pressed } };
		TypedValue typedValue = new TypedValue();
		TypedArray colorControlHighlight = proxy.getActivity().obtainStyledAttributes(
			typedValue.data, new int[] { android.R.attr.colorControlHighlight });
		int colorControlHighlightInt = colorControlHighlight.getColor(0, 0);
		int[] rippleColors = new int[] { colorControlHighlightInt };
		ColorStateList colorStateList = new ColorStateList(rippleStates, rippleColors);
		return new RippleDrawable(colorStateList, stateListDrawable, null);
	}

	/**
	 * Remove the tab from this group.
	 *
	 * @param index the tab to remove from the group
	 */
	public void removeTabAt(int index)
	{
		// Remove the reference in tabsMap.
		tabs.remove(index);
		tabFragmentIDs.remove(index);
		// Update the ViewPager.
		this.tabGroupPagerAdapter.notifyDataSetChanged();
		// Remove the item from the controller.
		removeTabItemFromController(index);
	}

	/**
	 * Changes the selected tab of the group.
	 *
	 * @param tabIndex the index of the tab that will become selected
	 */
	public void selectTab(int tabIndex)
	{
		// Release the OnPageChangeListener in order to calling an unnecessary item selection.
		this.tabGroupViewPager.clearOnPageChangeListeners();
		this.tabGroupViewPager.setCurrentItem(tabIndex, this.smoothScrollOnTabClick);
		this.tabGroupViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
			@Override
			public void onPageScrolled(int i, float v, int i1)
			{
				if (autoTabTitle && i != lastTab) {
					if (tabs.get(i).getWindowProxy() != null) {
						updateTitle(tabs.get(i).getWindowProxy().getProperty(TiC.PROPERTY_TITLE).toString());
					}
					lastTab = i;
				}
			}

			@Override
			public void onPageSelected(int i)
			{
				selectTabItemInController(i);
				selectTab(i);
			}

			@Override
			public void onPageScrollStateChanged(int i)
			{
			}
		});

		// Set action bar color.
		final ActionBar actionBar = ((AppCompatActivity) proxy.getActivity()).getSupportActionBar();
		if (actionBar != null) {
			final TiWindowProxy windowProxy = ((TabProxy) this.tabs.get(tabIndex).getProxy()).getWindow();
			final KrollDict windowProperties = windowProxy.getProperties();
			final KrollDict properties = getProxy().getProperties();

			if (properties.containsKeyAndNotNull(TiC.PROPERTY_BAR_COLOR)
				|| windowProperties.containsKeyAndNotNull(TiC.PROPERTY_BAR_COLOR)) {
				final String colorString = properties.optString(TiC.PROPERTY_BAR_COLOR,
					windowProperties.getString(TiC.PROPERTY_BAR_COLOR));
				final int color = TiColorHelper.parseColor(colorString);
				actionBar.setBackgroundDrawable(new ColorDrawable(color));
			}
		}
	}

	@Override
	public void processProperties(KrollDict d)
	{
		if (d.containsKey(TiC.PROPERTY_ACTIVITY)) {
			Object activityObject = d.get(TiC.PROPERTY_ACTIVITY);
			ActivityProxy activityProxy = getProxy().getActivityProxy();
			if (activityObject instanceof HashMap<?, ?> && activityProxy != null) {
				KrollDict options = new KrollDict((HashMap<String, Object>) activityObject);
				activityProxy.handleCreationDict(options);
			}
		}
		if (d.containsKey(TiC.PROPERTY_SWIPEABLE)) {
			this.swipeable = d.getBoolean(TiC.PROPERTY_SWIPEABLE);
		}
		if (d.containsKey(TiC.PROPERTY_AUTO_TAB_TITLE)) {
			this.autoTabTitle = d.getBoolean(TiC.PROPERTY_AUTO_TAB_TITLE);
		}
		if (d.containsKey(TiC.PROPERTY_SMOOTH_SCROLL_ON_TAB_CLICK)) {
			this.smoothScrollOnTabClick = d.getBoolean(TiC.PROPERTY_SMOOTH_SCROLL_ON_TAB_CLICK);
		}
		if (d.containsKeyAndNotNull(TiC.PROPERTY_TABS_BACKGROUND_COLOR)) {
			setBackgroundColor(TiColorHelper.parseColor(d.get(TiC.PROPERTY_TABS_BACKGROUND_COLOR).toString()));
		} else {
			setBackgroundColor(this.colorPrimaryInt);
		}
		super.processProperties(d);
	}

	@Override
	public void propertyChanged(String key, Object oldValue, Object newValue, KrollProxy proxy)
	{
		if (key.equals(TiC.PROPERTY_SWIPEABLE)) {
			this.swipeable = TiConvert.toBoolean(newValue);
		} else if (key.equals(TiC.PROPERTY_SMOOTH_SCROLL_ON_TAB_CLICK)) {
			this.smoothScrollOnTabClick = TiConvert.toBoolean(newValue);
		} else if (key.equals(TiC.PROPERTY_AUTO_TAB_TITLE)) {
			this.autoTabTitle = TiConvert.toBoolean(newValue);
		} else if (key.equals(TiC.PROPERTY_TABS_BACKGROUND_COLOR)) {
			for (TiUITab tabView : tabs) {
				updateTabBackgroundDrawable(tabs.indexOf(tabView));
			}
			setBackgroundColor(TiColorHelper.parseColor(newValue.toString()));
		} else if (key.equals(TiC.PROPERTY_TABS_BACKGROUND_SELECTED_COLOR)) {
			for (TiUITab tabView : tabs) {
				updateTabBackgroundDrawable(tabs.indexOf(tabView));
			}
		} else {
			super.propertyChanged(key, oldValue, newValue, proxy);
		}
	}

	public void updateTitle(String title)
	{
		// Get a reference to the ActionBar
		ActionBar actionBar = ((AppCompatActivity) proxy.getActivity()).getSupportActionBar();
		// Guard for trying to update the ActionBar's title when a theme without one is used.
		if (actionBar != null) {
			actionBar.setTitle(title);
		}
	}

	public Drawable updateIconTint(TiViewProxy tabProxy, Drawable drawable, boolean selected)
	{
		if (drawable == null) {
			return null;
		}

		// Clone existing drawable so color filter applies correctly.
		drawable = drawable.getConstantState().newDrawable();

		final KrollDict tabProperties = tabProxy.getProperties();
		final KrollDict properties = getProxy().getProperties();

		int color = this.textColorInt;
		if (selected) {
			if (tabProperties.containsKeyAndNotNull(TiC.PROPERTY_ACTIVE_TINT_COLOR)
				|| properties.containsKeyAndNotNull(TiC.PROPERTY_ACTIVE_TINT_COLOR)) {
				final String colorString = tabProperties.optString(TiC.PROPERTY_ACTIVE_TINT_COLOR,
					properties.getString(TiC.PROPERTY_ACTIVE_TINT_COLOR));
				color = TiColorHelper.parseColor(colorString);
			}
			drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
		} else {
			color = this.unselectedTextColorInt;
			if (tabProperties.containsKeyAndNotNull(TiC.PROPERTY_TINT_COLOR)
				|| properties.containsKeyAndNotNull(TiC.PROPERTY_TINT_COLOR)) {
				final String colorString = tabProperties.optString(TiC.PROPERTY_TINT_COLOR,
					properties.getString(TiC.PROPERTY_TINT_COLOR));
				color = TiColorHelper.parseColor(colorString);
			}
			drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
		}

		return drawable;
	}

	/**
	 * Implementation of the FragmentPagerAdapter
	 */
	private class TabGroupFragmentPagerAdapter extends FragmentPagerAdapter
	{

		public TabGroupFragmentPagerAdapter(FragmentManager fm)
		{
			super(fm);
		}

		// getItem only gets called by the FragmentPagerAdapter when the fragment is not found
		// in the FragmentManager. We construct it and associate it to the tab view.
		@Override
		public Fragment getItem(int i)
		{
			TabFragment tabFragment = new TabFragment();
			tabFragment.setTab(tabs.get(i));
			return tabFragment;
		}

		// Android docs say we must override the default implementation if item position can change
		// The FragmentPagerAdapter uses the ID to construct the fragment tag (android:switcher:containerID:ID)
		// and then checks the FragmentManager for presence of said tag. The default Android implementation
		// returns the fragment position in the ViewPager, which is of course wrong when a tab is removed
		// and fragments in higher positions move. Thus we maintain the position and IDs in an ArrayList ourselves.
		@Override
		public long getItemId(int position)
		{
			long id = tabFragmentIDs.get(position);
			return id;
		}

		@Override
		public int getCount()
		{
			if (tabsDisabled) {
				// Since we don't want the FragmentPagerAdapter to do all kinds of rearrangements
				// just because we decided to disable tabs. We want the fragments to stay alive for when
				// we enable the tabs again.
				return numTabsWhenDisabled;
			} else {
				return tabs.size();
			}
		}

		// We must override the default implementation since item position can change
		// The default Android implementation returns POSITION_UNCHANGED
		// This gets called when getCount returns an unexpected value (e.g. a tab was removed)
		// and now the FragmentPagerAdapter wants to check where the fragments are.
		// We thus need to maintain a list of fragment tags since that's how we check based on
		// the fragment passed into this function.
		@Override
		public int getItemPosition(Object object)
		{
			int index = tabs.indexOf(((TabFragment) object).getTab());
			// Notify the PagerAdapter that we have removed a tab.
			if (index < 0) {
				return POSITION_NONE;
			}
			return index;
		}

		// The implementation in the super class calls getItemId for the fragment in this position
		// The builds a tag based on that ID, and then checks the FragmentManager for that tag.
		// If the tag does not exist getItem is called to construct a new fragment.
		// In any case, the super class method returns the fragment tag which we later use in
		// getItemPosition, for example.
		@Override
		public Object instantiateItem(ViewGroup container, int position)
		{
			TabFragment fragment = (TabFragment) super.instantiateItem(container, position);
			return fragment;
		}
	}

	/**
	 * Helper class to connect Fragment with TabProxies
	 */
	public static class TabFragment extends Fragment
	{
		private TiUITab tab;

		public void setTab(TiUITab tab)
		{
			this.tab = tab;
		}

		public TiUITab getTab()
		{
			return this.tab;
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
		{
			// Remove this fragment from the fragment manager if no longer assigned a tab reference.
			// This will cause fragment manager to request a new fragment from FragmentPagerAdapter.getItem() method.
			// Note: This can happen when fragment is restored from a bundle since we don't save tab settings
			//       via onSaveInstanceState() method. Restore happens when activity is destroyed, but not finished.
			if (tab == null) {
				FragmentManager fragmentManager = getFragmentManager();
				if (fragmentManager != null) {
					fragmentManager.beginTransaction().remove(this).commit();
				}
				return null;
			}

			// We have our tab object. Have it generate the tab's views and return them.
			return tab.getContentView();
		}
	}
}
