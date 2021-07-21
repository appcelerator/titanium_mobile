/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2021 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiActivity;
import org.appcelerator.titanium.TiActivityWindow;
import org.appcelerator.titanium.TiActivityWindows;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiRootActivity;
import org.appcelerator.titanium.proxy.ActivityProxy;
import org.appcelerator.titanium.proxy.TiWindowProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiRHelper;
import org.appcelerator.titanium.util.TiUIHelper;

import ti.modules.titanium.ui.android.AndroidModule;
import ti.modules.titanium.ui.widget.tabgroup.TiUIAbstractTabGroup;
import ti.modules.titanium.ui.widget.tabgroup.TiUIBottomNavigationTabGroup;
import ti.modules.titanium.ui.widget.tabgroup.TiUITabLayoutTabGroup;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.LayoutInflater;

@Kroll.proxy(creatableInModule = UIModule.class,
	propertyAccessors = {
		TiC.PROPERTY_TABS_BACKGROUND_COLOR,
		TiC.PROPERTY_TABS_BACKGROUND_SELECTED_COLOR,
		TiC.PROPERTY_SWIPEABLE,
		TiC.PROPERTY_EXIT_ON_CLOSE,
		TiC.PROPERTY_SMOOTH_SCROLL_ON_TAB_CLICK
})
public class TabGroupProxy extends TiWindowProxy implements TiActivityWindow
{
	private static final String TAG = "TabGroupProxy";
	private static final String PROPERTY_POST_TAB_GROUP_CREATED = "postTabGroupCreated";
	private static final int MSG_FIRST_ID = TiWindowProxy.MSG_LAST_ID + 1;

	private static final int MSG_ADD_TAB = MSG_FIRST_ID + 100;
	private static final int MSG_REMOVE_TAB = MSG_FIRST_ID + 101;
	private static final int MSG_SET_ACTIVE_TAB = MSG_FIRST_ID + 102;
	private static final int MSG_GET_ACTIVE_TAB = MSG_FIRST_ID + 103;
	private static final int MSG_SET_TABS = MSG_FIRST_ID + 104;
	private static final int MSG_DISABLE_TAB_NAVIGATION = MSG_FIRST_ID + 105;

	protected static final int MSG_LAST_ID = MSG_FIRST_ID + 999;

	private ArrayList<TabProxy> tabs = new ArrayList<>();
	private WeakReference<AppCompatActivity> tabGroupActivity = new WeakReference<>(null);
	private Object selectedTab; // NOTE: Can be TabProxy or Number
	private String tabGroupTitle = null;
	private static int id_toolbar;

	public TabGroupProxy()
	{
		super();
		defaultValues.put(TiC.PROPERTY_SWIPEABLE, true);
		defaultValues.put(TiC.PROPERTY_SMOOTH_SCROLL_ON_TAB_CLICK, true);
	}

	public int getTabIndex(TabProxy tabProxy)
	{
		return tabs.indexOf(tabProxy);
	}

	@Kroll.method
	public void disableTabNavigation(boolean disable)
	{
		TiUIAbstractTabGroup tabGroup = (TiUIAbstractTabGroup) view;
		if (tabGroup != null) {
			tabGroup.disableTabNavigation(disable);
		}
	}

	@Kroll.method
	public void addTab(TabProxy tab)
	{
		if (tab == null) {
			return;
		}

		// Set the tab's parent to this tab group.
		// This allows for certain events to bubble up.
		tab.setTabGroup(this);

		tabs.add(tab);

		TiUIAbstractTabGroup tabGroup = (TiUIAbstractTabGroup) view;
		if (tabGroup != null) {
			tabGroup.addTab(tab);
		}
	}

	@Kroll.method
	public void removeTab(TabProxy tab)
	{
		int indexToRemove = tabs.indexOf(tab);
		// Guard for trying to remove a Tab that has not been added.
		if (indexToRemove < 0) {
			return;
		}
		tabs.remove(tab);

		TiUIAbstractTabGroup tabGroup = (TiUIAbstractTabGroup) view;
		if (tabGroup != null) {
			tabGroup.removeTabAt(indexToRemove);
		}

		tab.setParent(null);
	}

	@Kroll.getProperty
	public Object getActiveTab()
	{
		//selectedTab may not be set when user queries activeTab, so we return
		//the first tab (default selected tab) if it exists.
		if (selectedTab != null) {
			return selectedTab;
		} else if (tabs.size() > 0) {
			return tabs.get(0);
		} else {
			return null;
		}
	}

	private TabProxy getActiveTabProxy()
	{
		Object activeTab = getActiveTab();
		return (activeTab != null) ? parseTab(activeTab) : null;
	}

	@Kroll.setProperty
	public void setActiveTab(Object tabOrIndex)
	{
		if (view == null) {

			// Pre-select tab if TabGroup not open.
			selectedTab = tabOrIndex;
		}

		// Attempt to parse tab.
		TabProxy tab = parseTab(tabOrIndex);
		if (tab == null) {
			return;
		}

		TiUIAbstractTabGroup tabGroup = (TiUIAbstractTabGroup) view;
		if (tabGroup != null) {
			// Change the selected tab of the group.
			// Once the change is completed onTabSelected() will be
			// called to fire events and update the active tab.
			tabGroup.selectTab(tab);
		}
	}

	@Kroll.getProperty(name = "activity")
	public ActivityProxy _getActivity()
	{
		final AppCompatActivity activity = tabGroupActivity.get();

		if (activity instanceof TiBaseActivity) {
			return ((TiBaseActivity) activity).getActivityProxy();
		}
		return null;
	}

	@Kroll.getProperty
	public TabProxy[] getTabs()
	{
		return tabs.toArray(new TabProxy[tabs.size()]);
	}

	@Kroll.setProperty
	public void setTabs(Object obj)
	{
		for (final TabProxy tab : getTabs()) {
			removeTab(tab);
		}
		tabs.clear();

		if (obj instanceof Object[]) {
			Object[] objArray = (Object[]) obj;
			for (Object tabProxy : objArray) {
				if (tabProxy instanceof TabProxy) {
					addTab((TabProxy) tabProxy);
				}
			}
		}
	}

	private TabProxy parseTab(Object tabOrIndex)
	{
		TabProxy tab = null;
		if (tabOrIndex instanceof Number) {
			int tabIndex = ((Number) tabOrIndex).intValue();
			if (tabIndex < 0 || tabIndex >= tabs.size()) {
				Log.e(TAG, "Invalid tab index.");
			} else {
				tab = tabs.get(tabIndex);
			}

		} else if (tabOrIndex instanceof TabProxy) {
			if (!tabs.contains((TabProxy) tabOrIndex)) {
				Log.e(TAG, "Cannot activate tab not in this group.");
			} else {
				tab = (TabProxy) tabOrIndex;
			}
		} else {
			Log.e(TAG, "No valid tab provided when setting active tab.");
		}
		return tab;
	}

	@Override
	public void handleCreationDict(KrollDict options)
	{
		super.handleCreationDict(options);

		// Support setting orientation modes at creation.
		Object orientationModes = options.get(TiC.PROPERTY_ORIENTATION_MODES);
		if (orientationModes instanceof Object[]) {
			try {
				int[] modes = TiConvert.toIntArray((Object[]) orientationModes);
				setOrientationModes(modes);

			} catch (ClassCastException e) {
				Log.e(TAG, "Invalid orientationMode array. Must only contain orientation mode constants.");
			}
		}

		if (options.containsKeyAndNotNull(TiC.PROPERTY_TABS)) {
			setTabs(options.get(TiC.PROPERTY_TABS));
		}
		if (options.containsKeyAndNotNull(TiC.PROPERTY_ACTIVE_TAB)) {
			setActiveTab(options.get(TiC.PROPERTY_ACTIVE_TAB));
		}
	}

	@Kroll.getProperty
	public String getTitle()
	{
		// If the native view is drawn get the title value from the SupportActionBar
		if (view != null) {
			if (getActivity() != null) {
				ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
				if (actionBar != null) {
					return actionBar.getTitle().toString();
				}
			}
			return null;
		} else {
			// If the native view is not drawn return tha latest String saved as a title
			return tabGroupTitle;
		}
	}

	@Kroll.setProperty
	public void setTitle(String title)
	{
		// If the native view is drawn directly set the String as a title for the SupportActionBar.
		if (view != null) {
			((TiUIAbstractTabGroup) view).updateTitle(title);
		} else {
			// If the native view is not yet drawn save the value to be passed during creation.
			this.tabGroupTitle = title;
		}
	}

	@Override
	protected void handleOpen(KrollDict options)
	{
		Activity topActivity = TiApplication.getAppCurrentActivity();
		// Don't open if app is closing or closed
		if (topActivity == null || topActivity.isFinishing()) {
			return;
		}
		Intent intent = new Intent(topActivity, TiActivity.class);
		fillIntent(topActivity, intent);

		int windowId = TiActivityWindows.addWindow(this);
		intent.putExtra(TiC.INTENT_PROPERTY_WINDOW_ID, windowId);

		boolean animated = TiConvert.toBoolean(options, TiC.PROPERTY_ANIMATED, true);
		if (!animated) {
			intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			topActivity.startActivity(intent);
			topActivity.overridePendingTransition(0, 0);
		} else if (options.containsKey(TiC.PROPERTY_ACTIVITY_ENTER_ANIMATION)
				   || options.containsKey(TiC.PROPERTY_ACTIVITY_EXIT_ANIMATION)) {
			topActivity.startActivity(intent);
			int enterAnimation = TiConvert.toInt(options.get(TiC.PROPERTY_ACTIVITY_ENTER_ANIMATION), 0);
			int exitAnimation = TiConvert.toInt(options.get(TiC.PROPERTY_ACTIVITY_EXIT_ANIMATION), 0);
			topActivity.overridePendingTransition(enterAnimation, exitAnimation);
		} else {
			topActivity.startActivity(intent);
			if (topActivity instanceof TiRootActivity) {
				// A fade-in transition from root splash screen to first window looks better than a slide-up.
				// Also works-around issue where splash in mid-transition might do a 2nd transition on cold start.
				topActivity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
			}
		}
	}

	@Override
	public void windowCreated(TiBaseActivity activity, Bundle savedInstanceState)
	{
		tabGroupActivity = new WeakReference<>(activity);
		activity.setWindowProxy(this);
		activity.setLayoutProxy(this);
		setActivity(activity);

		// Currently only two styles. Introducing new ones will work with a switch statement.
		if (getProperty(TiC.PROPERTY_STYLE) == null
			|| ((Integer) getProperty(TiC.PROPERTY_STYLE)) == AndroidModule.TABS_STYLE_DEFAULT) {
			view = new TiUITabLayoutTabGroup(this, activity);

			if (getProperty(TiC.PROPERTY_TAB_MODE) != null) {
				((TiUITabLayoutTabGroup) view).setTabMode((Integer) getProperty(TiC.PROPERTY_TAB_MODE));
			}
		} else {
			view = new TiUIBottomNavigationTabGroup(this, activity);
		}
		// If we have set a title before the creation of the native view, set it now.
		if (this.tabGroupTitle != null) {
			((TiUIAbstractTabGroup) view).updateTitle(this.tabGroupTitle);
		}
		setModelListener(view);

		// add toolbar to NavigationWindow
		if (this.getNavigationWindow() != null) {
			if (activity.getSupportActionBar() == null) {
				try {
					if (id_toolbar == 0) {
						id_toolbar = TiRHelper.getResource("layout.titanium_ui_toolbar");
					}
				} catch (TiRHelper.ResourceNotFoundException e) {
					android.util.Log.e(TAG, "XML resources could not be found!!!");
				}
				LayoutInflater inflater = LayoutInflater.from(activity);
				Toolbar toolbar = (Toolbar) inflater.inflate(id_toolbar, null, false);

				activity.setSupportActionBar(toolbar);
			}
			activity.getSupportActionBar().setHomeButtonEnabled(
				!getProperties().optBoolean(TiC.PROPERTY_HIDES_BACK_BUTTON, false));
			// Get a reference to the root window in the NavigationWindow.
			TiWindowProxy rootTiWindowProxy =
				((NavigationWindowProxy) this.getNavigationWindow()).getRootTiWindowProxy();
			// If the root window matches this window do not show the Up navigation button.
			activity.getSupportActionBar().setDisplayHomeAsUpEnabled(rootTiWindowProxy != this);
		}

		// Need to handle the cached activity proxy properties in the JS side.
		callPropertySync(PROPERTY_POST_TAB_GROUP_CREATED, null);
	}

	@Override
	public void onWindowActivityCreated()
	{
		// Flag that this tab group has been opened.
		opened = true;
		opening = false;

		// Fire open event before we load and focus on first tab.
		fireEvent(TiC.EVENT_OPEN, null);

		// Finish open handling by loading proxy settings.
		handlePostOpen();

		super.onWindowActivityCreated();
	}

	@Override
	protected void handlePostOpen()
	{
		super.handlePostOpen();

		if (view == null) {
			return;
		}

		// Load any tabs added before the tab group opened.
		TiUIAbstractTabGroup tg = (TiUIAbstractTabGroup) view;
		for (TabProxy tab : tabs) {
			if (tab != null) {
				tg.addTab(tab);
			}
		}

		// If TabGroup's selected tab is same as the active tab,
		// then we need to invoke onTabSelected so focus/blur event fire appropriately
		TabProxy tab = getActiveTabProxy();
		if (tab != null) {
			tg.selectTab(tab);
			selectedTab = tab;
		}

		// Selected tab should have been focused by now.
		// Prevent any duplicate events from firing by marking
		// this group has having focus.
		isFocused = true;
	}

	@Override
	protected void handleClose(@NonNull KrollDict options)
	{
		Log.d(TAG, "handleClose: " + options, Log.DEBUG_MODE);

		// Remove this TabGroup proxy from the active/open collection.
		// Note: If the activity's onCreate() can't find this proxy, then it'll automatically destroy itself.
		//       This is needed in case the proxy's close() method was called before the activity was created.
		TiActivityWindows.removeWindow(this);

		// Release views/resources.
		modelListener = null;
		releaseViews();
		view = null;

		// Destroy this proxy's activity.
		AppCompatActivity activity = (tabGroupActivity != null) ? tabGroupActivity.get() : null;
		tabGroupActivity = null;
		if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
			activity.finish();
		}

		// NOTE: this does not directly fire the close event, but is fired by closeFromActivity()
	}

	@Override
	public void closeFromActivity(boolean activityIsFinishing)
	{
		// Allow each tab to close its window before the tab group closes.
		for (TabProxy tab : tabs) {
			tab.close(activityIsFinishing);
		}

		// Call super to fire the close event on the tab group.
		// This event must fire after each tab has been closed.
		super.closeFromActivity(activityIsFinishing);
	}

	@Override
	public void onWindowFocusChange(boolean focused)
	{
		// Do not dispatch duplicate focus events.
		// Duplicates may occur when the group opens because
		// both the initial tab selection and the activity resuming
		// will attempt to focus the tabs.
		if (isFocused == focused) {
			return;
		}
		isFocused = focused;

		// Fetch a proxy to the active tab. (Skip this if no tabs exists in group yet to avoid warnings in the log.)
		TabProxy tab = (tabs.size() > 0) ? getActiveTabProxy() : null;

		// If no tab is selected fall back to the default behavior.
		if (tab == null) {
			super.onWindowFocusChange(focused);
			return;
		}

		// When the tab group gains focus we need to re-focus
		// the currently selected tab. No UI state change is required
		// since no tab selection actually occurred. This should only
		// happen if the activity is paused or the window stack changed.
		tab.onFocusChanged(focused, null);
	}

	public void onTabSelected(int position)
	{
		onTabSelected(tabs.get(position));
	}

	/**
	 * Invoked when a tab in the group is selected.
	 *
	 * @param tabProxy the tab that was selected
	 */
	public void onTabSelected(TabProxy tabProxy)
	{
		TabProxy previousSelectedTab = getActiveTabProxy();
		selectedTab = tabProxy;

		// Focus event data which will be dispatched to the selected tab.
		// The 'source' of these events will always be the tab being focused.
		KrollDict focusEventData = new KrollDict();
		focusEventData.put(TiC.EVENT_PROPERTY_SOURCE, tabProxy);
		focusEventData.put(TiC.EVENT_PROPERTY_PREVIOUS_TAB, previousSelectedTab);
		focusEventData.put(TiC.EVENT_PROPERTY_PREVIOUS_INDEX, tabs.indexOf(previousSelectedTab));
		focusEventData.put(TiC.EVENT_PROPERTY_TAB, tabProxy);
		focusEventData.put(TiC.EVENT_PROPERTY_INDEX, tabs.indexOf(tabProxy));

		// We cannot modify event data after firing an event with it.
		// To change the 'source' to the previously selected tab we must clone it.
		KrollDict blurEventData = (KrollDict) focusEventData.clone();
		blurEventData.put(TiC.EVENT_PROPERTY_SOURCE, previousSelectedTab);

		// Notify the previously and currently selected tabs about the change.
		// Tab implementations should update their UI state and fire focus/blur events.
		if (previousSelectedTab != null) {
			previousSelectedTab.onSelectionChanged(false);
			previousSelectedTab.onFocusChanged(false, blurEventData);
		}
		tabProxy.onSelectionChanged(true);
		tabProxy.onFocusChanged(true, focusEventData);

		tabProxy.fireEvent(TiC.EVENT_SELECTED, null, false);
	}

	@Override
	public TiBlob handleToImage()
	{
		KrollDict d = TiUIHelper.viewToImage(new KrollDict(), getActivity().getWindow().getDecorView());
		return TiUIHelper.getImageFromDict(d);
	}

	@Override
	public void releaseViews()
	{
		super.releaseViews();
		if (tabs != null) {
			for (TabProxy t : tabs) {
				t.releaseViews();
			}
		}
	}

	@Override
	public void releaseViewsForActivityForcedToDestroy()
	{
		super.releaseViews();
		if (tabs != null) {
			for (TabProxy t : tabs) {
				t.releaseViewsForActivityForcedToDestroy();
			}
		}
	}

	@Override
	protected AppCompatActivity getWindowActivity()
	{
		return (tabGroupActivity != null) ? tabGroupActivity.get() : null;
	}

	public void fireSafeAreaChangedEvent()
	{
		// First, fire the event for this TabGroup.
		super.fireSafeAreaChangedEvent();

		// Create a shallow copy of the tab proxy collection owned by this TabGroup.
		// We need to do this since a tab's event handler can remove a tab, which would break iteration.
		ArrayList<TabProxy> clonedTabList = (ArrayList<TabProxy>) this.tabs.clone();
		if (clonedTabList == null) {
			return;
		}

		// Fire a safe-area change event for each tab window.
		for (TabProxy tab : clonedTabList) {
			if (tab != null) {
				TiWindowProxy window = tab.getWindow();
				if (window != null) {
					window.fireSafeAreaChangedEvent();
				}
			}
		}
	}

	@Override
	public String getApiName()
	{
		return "Ti.UI.TabGroup";
	}
}
