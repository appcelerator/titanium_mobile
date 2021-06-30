/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2020 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget.listview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollEventCallback;
import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollObject;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.ColorProxy;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.view.TiUIView;

import android.app.Activity;
import android.view.View;

import androidx.annotation.NonNull;

import ti.modules.titanium.ui.UIModule;
import ti.modules.titanium.ui.widget.TiView;

@Kroll.proxy(
	creatableInModule = ti.modules.titanium.ui.UIModule.class,
	propertyAccessors = {
		TiC.PROPERTY_ACCESSORY_TYPE,
		TiC.PROPERTY_CAN_EDIT,
		TiC.PROPERTY_CAN_MOVE,
		TiC.PROPERTY_ITEM_ID,
		TiC.PROPERTY_SEARCHABLE_TEXT
	}
)
public class ListItemProxy extends TiViewProxy
{
	private static final String TAG = "ListItemProxy";

	private final HashMap<String, TiViewProxy> binds = new HashMap<>();
	private final HashMap<String, Object> childProperties = new HashMap<>();
	private final List<String> ignoredTemplateKeys = new ArrayList<>();

	public int index;

	private int filteredIndex = -1;
	private ListViewHolder holder;
	private KrollDict template;
	private String templateId;
	private boolean placeholder = false;
	private boolean hasAddedItemEvents = false;

	public ListItemProxy()
	{
		super();

		defaultValues.put(TiC.PROPERTY_ACCESSORY_TYPE, UIModule.LIST_ACCESSORY_TYPE_NONE);
	}

	public ListItemProxy(boolean placeholder)
	{
		this();

		// Determine if item is placeholder for header or footer.
		this.placeholder = placeholder;
	}

	/**
	 * Generate ListItem view from specified template.
	 *
	 * @param activity the context activity.
	 * @return TiUIView of ListItem.
	 */
	@Override
	public TiUIView createView(Activity activity)
	{
		// Do not continue if this ListItem is a placeholder for a header/footer.
		if (placeholder) {
			return null;
		}

		// Fetch template assigned to this ListItem.
		loadTemplate();
		if (this.template == null) {
			return null;
		}

		// Apply template defined properties to this ListItem.
		final KrollDict templateProperties = this.template.getKrollDict(TiC.PROPERTY_PROPERTIES);
		if (templateProperties != null) {
			for (KrollDict.Entry<String, Object> entry : templateProperties.entrySet()) {
				if (!this.ignoredTemplateKeys.contains(entry.getKey())) {
					setProperty(entry.getKey(), entry.getValue());
				}
			}
		}

		// Generate all child proxies from template and create all views.
		if (!hasChildren()) {
			generateViewFromTemplate(this, this.template);
		}
		return new ItemView(this);
	}

	/**
	 * Handle event payload manipulation.
	 *
	 * @param eventName Name of fired event.
	 * @param data      Data payload of fired event.
	 * @return Object of event payload.
	 */
	public Object handleEvent(String eventName, Object data, boolean fireItemClick)
	{
		// Inject row data into events.
		final ListViewProxy listViewProxy = getListViewProxy();
		if (listViewProxy != null) {
			final KrollDict payload = data instanceof HashMap
				? new KrollDict((HashMap<String, Object>) data) : new KrollDict();
			final Object sourceObject = payload.containsKeyAndNotNull(TiC.EVENT_PROPERTY_SOURCE)
				? payload.get(TiC.EVENT_PROPERTY_SOURCE) : this;
			final TiViewProxy source = sourceObject instanceof TiViewProxy ? (TiViewProxy) sourceObject : this;

			final Object parent = getParent();
			if (parent instanceof ListSectionProxy) {
				final ListSectionProxy section = (ListSectionProxy) parent;

				// Include section specific properties.
				payload.put(TiC.PROPERTY_SECTION, section);
				payload.put(TiC.PROPERTY_SECTION_INDEX, listViewProxy.getIndexOfSection(section));
				payload.put(TiC.PROPERTY_ITEM_INDEX, getIndexInSection());
			}

			final Object itemId = getProperties().get(TiC.PROPERTY_ITEM_ID);
			if (itemId != null) {

				// Include `itemId` if specified.
				payload.put(TiC.PROPERTY_ITEM_ID, itemId);
			}

			for (final String key : binds.keySet()) {
				if (binds.get(key).equals(source)) {

					// Reverse lookup `bindId`.
					// Include `bindId` of template if specified.
					payload.put(TiC.PROPERTY_BIND_ID, key);
					break;
				}
			}

			final int accessoryType = getProperties().optInt(TiC.PROPERTY_ACCESSORY_TYPE,
				UIModule.LIST_ACCESSORY_TYPE_NONE);
			final boolean isAccessoryDetail = accessoryType == UIModule.LIST_ACCESSORY_TYPE_DETAIL;

			// If item is `LIST_ACCESSORY_TYPE_DETAIL` then `accessoryClicked` will be `true`.
			payload.put(TiC.EVENT_PROPERTY_ACCESSORY_CLICKED, isAccessoryDetail);

			// Override data with new payload.
			data = payload;

			// Fire `itemclick` event on ListView.
			if (fireItemClick && eventName.equals(TiC.EVENT_CLICK)) {
				listViewProxy.fireSyncEvent(TiC.EVENT_ITEM_CLICK, data);
			}
		}

		return data;
	}

	/**
	 * Override fireEvent to inject ListItem data into payload.
	 *
	 * @param eventName Name of fired event.
	 * @param data      Data payload of fired event.
	 * @param bubbles   Specify if event should bubble up to parent.
	 * @return
	 */
	@Override
	public boolean fireEvent(String eventName, Object data, boolean bubbles)
	{
		data = handleEvent(eventName, data, true);
		return super.fireEvent(eventName, data, bubbles);
	}
	@Override
	public boolean fireSyncEvent(String eventName, Object data, boolean bubbles)
	{
		data = handleEvent(eventName, data, true);
		return super.fireSyncEvent(eventName, data, bubbles);
	}

	/**
	 * Generate views from specified template.
	 *
	 * @param parent   Parent proxy to add child views from template. (Creates new proxy when null)
	 * @param template Template to generate views from.
	 * @return Generated TiViewProxy.
	 */
	protected TiViewProxy generateViewFromTemplate(TiViewProxy parent, KrollDict template)
	{
		final TiViewProxy tiProxy = (TiViewProxy) template.get("tiProxy");
		final KrollDict templateProperties = template.getKrollDict(TiC.PROPERTY_PROPERTIES);
		final Object[] childTemplates = (Object[]) template.get(TiC.PROPERTY_CHILD_TEMPLATES);
		final String bindId = template.getString(TiC.PROPERTY_BIND_ID);

		if (parent == null) {
			try {

				// Parent undefined, create new proxy instance.
				parent = tiProxy.getClass().newInstance();

				final KrollDict properties = new KrollDict(tiProxy.getProperties());

				if (this.childProperties.containsKey(bindId)) {
					final Object childPropertiesObj = this.childProperties.get(bindId);

					if (childPropertiesObj instanceof KrollDict) {
						final KrollDict childProperties = (KrollDict) childPropertiesObj;

						// Include child properties for specified `bindId`.
						properties.putAll(childProperties);
					}
				}

				// Create instance with new properties.
				parent.setActivity(getActivity());
				parent.handleCreationArgs(null, new Object[] { properties });

			} catch (Exception e) {
				Log.w(TAG, "Failed to create child proxy instance from template.");
			}
		}
		if (bindId != null) {

			// Include new instance binding.
			binds.put(bindId, parent);
		}

		// Fetch event listeners from template and add them to the proxy.
		addTemplateEventListeners(parent, template);

		if (childTemplates != null) {

			// Recursively process child templates.
			for (Object o : childTemplates) {
				if (o instanceof HashMap) {
					final KrollDict childTemplate = new KrollDict((HashMap) o);
					final TiViewProxy childView = generateViewFromTemplate(null, childTemplate);
					if (childView != null) {
						parent.add(childView);
					}
				}
			}
		}

		return parent;
	}

	private void addTemplateEventListeners(final TiViewProxy proxy, KrollDict template)
	{
		// Validate arguments.
		if ((proxy == null) || (template == null)) {
			return;
		}

		// Fetch events assigned to given template.
		final KrollDict eventDictionary = template.getKrollDict(TiC.PROPERTY_EVENTS);
		if (eventDictionary == null) {
			return;
		}

		// Make sure we only add events listeners once for the root ListItem.
		// Note: ListItem's view is always re-created, unlike child views which are only created once.
		if (proxy instanceof ListItemProxy) {
			if (this.hasAddedItemEvents) {
				return;
			}
			this.hasAddedItemEvents = true;
		}

		// Add event listeners to given proxy.
		for (KrollDict.Entry<String, Object> entry : eventDictionary.entrySet()) {
			if (!(entry.getValue() instanceof KrollFunction)) {
				continue;
			}
			final String eventName = entry.getKey();
			final KrollFunction callback = (KrollFunction) entry.getValue();
			final KrollObject krollObject = proxy.getKrollObject();
			proxy.addEventListener(eventName, new KrollEventCallback() {
				@Override
				public void call(Object data)
				{
					ListItemProxy itemProxy = getListItemContainerFor(proxy);
					if (itemProxy != null) {
						callback.call(krollObject, new Object[] { itemProxy.handleEvent(eventName, data, false) });
					}
				}
			});
			krollObject.setHasListenersForEventType(eventName, true);
		}
	}

	private static ListItemProxy getListItemContainerFor(TiViewProxy proxy)
	{
		if (proxy == null) {
			return null;
		} else if (proxy instanceof ListItemProxy) {
			return (ListItemProxy) proxy;
		}
		return getListItemContainerFor(proxy.getParent());
	}

	public void moveChildrenTo(ListItemProxy proxy)
	{
		// Validate argument.
		if (proxy == null) {
			return;
		}

		// Copy all child view proxy properties to this list item's dictionary.
		// Saves view's current state when scrolled offscreen so we can restore it when scrolled back in.
		// Note: Given "proxy" shouldn't have any child proxies, but do the copy just in case.
		copyChildPropertiesFromProxies();
		proxy.copyChildPropertiesFromProxies();

		// Remove all children from given proxy. (If recycling is working as expected, it should have none.)
		proxy.removeAllChildren();

		// Do not continue if templates don't match. Prevents given proxy from re-using this proxy's views.
		// Note: Should never happen if ListViewAdapter.getItemViewType() returns different IDs for different templates.
		String templateId = getTemplateId();
		if ((templateId == null) || !templateId.equals(proxy.getTemplateId())) {
			return;
		}

		// Do not continue if this proxy has no child views.
		// This will force given proxy to regenerate child proxies from template.
		if (!hasChildren()) {
			return;
		}
		TiViewProxy[] childProxies = getChildren();
		if (childProxies[0].peekView() == null) {
			return;
		}

		// Move this proxy's children to given proxy and overwrite their properties.
		// Note: This also moves their native views. Updating properties will also update the native views.
		proxy.loadTemplate();
		proxy.copyChildPropertiesTo(childProxies, proxy.template);
		proxy.add(childProxies);

		// Now that this proxy has no children, release this proxy's 1 native view container.
		// We do this to reduce memory footprint of all offscreen list items.
		releaseViews();

		// Remove all child binding IDs from this proxy except for the ListItem itself.
		final String bindId = this.template.getString(TiC.PROPERTY_BIND_ID);
		this.binds.clear();
		if (bindId != null) {
			this.binds.put(bindId, this);
		}
	}

	private void copyChildPropertiesFromProxies()
	{
		for (HashMap.Entry<String, TiViewProxy> bindsEntry : this.binds.entrySet()) {
			// Fetch the next child view proxy. (Skip the root ListItemProxy object.)
			TiViewProxy proxy = bindsEntry.getValue();
			if (proxy == this) {
				continue;
			}

			// Do no continue if child proxy has no properties.
			KrollDict proxyProperties = proxy.getProperties();
			if (proxyProperties == null) {
				continue;
			}

			// Copy all view proxy properties to this list item's dictionary.
			// Saves view's current state when scrolled offscreen so we can restore it when scrolled back in.
			Object object = this.childProperties.get(bindsEntry.getKey());
			if (object instanceof HashMap) {
				((HashMap) object).putAll(proxyProperties);
			} else {
				this.childProperties.put(bindsEntry.getKey(), new KrollDict(proxyProperties));
			}
		}
	}

	private void copyChildPropertiesTo(TiViewProxy[] proxies, KrollDict template)
	{
		// Validate arguments.
		if ((proxies == null) || (proxies.length <= 0) || (template == null)) {
			return;
		}

		// Fetch child templates nested under given template.
		final Object[] childTemplates = (Object[]) template.get(TiC.PROPERTY_CHILD_TEMPLATES);

		// Update all child proxies.
		for (int index = 0; index < proxies.length; index++) {
			// Fetch child's template.
			KrollDict childTemplate = null;
			if ((childTemplates != null) && (index < childTemplates.length)) {
				Object objectTemplate = childTemplates[index];
				if (objectTemplate instanceof KrollDict) {
					childTemplate = (KrollDict) objectTemplate;
				} else if (objectTemplate instanceof HashMap) {
					childTemplate = new KrollDict((HashMap) objectTemplate);
				}
			}

			// Fetch child's assigned configuration.
			TiViewProxy tiProxy = null;
			String bindId = null;
			KrollDict events = null;
			if (childTemplate != null) {
				tiProxy = (TiViewProxy) childTemplate.get("tiProxy");
				bindId = childTemplate.getString(TiC.PROPERTY_BIND_ID);
				events = childTemplate.getKrollDict(TiC.PROPERTY_EVENTS);
			}

			// Fetch child's properties.
			TiViewProxy proxy = proxies[index];
			final KrollDict properties = new KrollDict();
			if (tiProxy != null) {
				properties.putAll(tiProxy.getProperties());
			}
			final Object childPropertiesObj = this.childProperties.get(bindId);
			if (childPropertiesObj instanceof HashMap) {
				properties.putAll((HashMap) childPropertiesObj);
			}

			// Assign properties to child. For best performance, only update property if value is different.
			for (KrollDict.Entry<String, Object> entry : properties.entrySet()) {
				if (proxy.shouldFireChange(proxy.getProperty(entry.getKey()), entry.getValue())) {
					proxy.setPropertyAndFire(entry.getKey(), entry.getValue());
				}
			}

			// Add child's binding ID to main dictionary.
			if (bindId != null) {
				this.binds.put(bindId, proxy);
			}

			// Update child's children. (This is recursive.)
			copyChildPropertiesTo(proxy.getChildren(), childTemplate);
		}
	}

	@Override
	public String getApiName()
	{
		return "Ti.UI.ListItem";
	}

	/**
	 * Get ListDataItem dictionary.
	 *
	 * @return KrollDict
	 */
	public KrollDict getDataItem()
	{
		final KrollDict dataItem = new KrollDict();

		// Include child items.
		for (final String key : this.childProperties.keySet()) {
			dataItem.put(key, this.childProperties.get(key));
		}

		// Include properties and template.
		dataItem.put(TiC.PROPERTY_PROPERTIES, getProperties());
		dataItem.put(TiC.PROPERTY_TEMPLATE, getTemplateId());

		return dataItem;
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
	 * Get current ListViewHolder for item.
	 *
	 * @return ListViewHolder
	 */
	public ListViewHolder getHolder()
	{
		return this.holder;
	}

	/**
	 * Set new ListViewHolder for item.
	 *
	 * @param holder ListViewHolder to set.
	 */
	public void setHolder(ListViewHolder holder)
	{
		this.holder = holder;
	}

	/**
	 * Get item index in section.
	 *
	 * @return Integer of index.
	 */
	public int getIndexInSection()
	{
		final TiViewProxy parent = getParent();

		if (parent instanceof ListSectionProxy) {
			final ListSectionProxy section = (ListSectionProxy) parent;

			return section.getListItemIndex(this);
		}

		return -1;
	}

	/**
	 * Get related ListView proxy for item.
	 *
	 * @return ListViewProxy
	 */
	public ListViewProxy getListViewProxy()
	{
		TiViewProxy parent = getParent();
		while (!(parent instanceof ListViewProxy) && parent != null) {
			parent = parent.getParent();
		}
		return (ListViewProxy) parent;
	}

	/**
	 * Override getRect() to amend dimensions.
	 *
	 * @return Dictinary of view dimensions.
	 */
	@Override
	public KrollDict getRect()
	{
		final View view = this.holder != null ? this.holder.itemView : null;

		return getViewRect(view);
	}

	/**
	 * Get template identifier associated with item.
	 *
	 * @return Template identifier.
	 */
	public String getTemplateId()
	{
		if (this.templateId == null) {
			loadTemplate();
		}
		return this.templateId;
	}

	/**
	 * Handle creation from ListDataItem object.
	 *
	 * @param options ListDataItem properties.
	 */
	public void handleCreationDataItem(KrollDict options)
	{
		if (options == null) {
			options = new KrollDict();
		}

		final KrollDict properties = options.getKrollDict(TiC.PROPERTY_PROPERTIES);

		// Iterate through properties for children.
		for (String k : options.keySet()) {

			// Ignore reserved ListDataItem properties.
			if (k.equals(TiC.PROPERTY_PROPERTIES)
				|| k.equals(TiC.PROPERTY_TEMPLATE)) {
				continue;
			}

			final KrollDict childDict = options.getKrollDict(k);

			if (childDict != null) {

				// Additional dictionary properties are regarded as child properties for templates.
				// NOTE: `getKrollDict()` is necessary as using `get()` would return a `HashMap`.
				this.childProperties.put(k, childDict);
			} else {

				// Non-template properties are regarded as custom properties.
				this.childProperties.put(k, options.get(k));
			}
		}

		// Set item template.
		this.templateId = options.getString(TiC.PROPERTY_TEMPLATE);

		// Process item properties.
		handleCreationDict(properties);
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

		// Prevent template from overriding specified properties.
		ignoredTemplateKeys.addAll(options.keySet());

		super.handleCreationDict(options);
	}

	/**
	 * Determine if proxy contains listener for specified event.
	 *
	 * @param event Event name.
	 * @return Boolean determining if listener is present.
	 */
	@Override
	public boolean hierarchyHasListener(String event)
	{
		// Override to always detect `click` events, even when no listeners are present.
		// This is so we can fire `itemclick` events.
		if (event.equals(TiC.EVENT_CLICK)) {
			return true;
		}
		return super.hierarchyHasListener(event);
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
	}

	/**
	 * Process property set on proxy.
	 *
	 * @param name Property name.
	 * @param value Property value.
	 */
	private void processProperty(String name, Object value)
	{
		// Handle convenience properties for default template.
		if (name.equals(TiC.PROPERTY_TITLE)
			|| name.equals(TiC.PROPERTY_FONT)
			|| name.equals(TiC.PROPERTY_COLOR)) {

			// Properties apply to `title` in default template.
			final KrollDict titleProperties = new KrollDict();

			// Handle title text.
			if (name.equals(TiC.PROPERTY_TITLE) && value instanceof String) {
				titleProperties.put(TiC.PROPERTY_TEXT, value);
			}

			// Handle title font.
			if (name.equals(TiC.PROPERTY_FONT) && value instanceof HashMap) {
				titleProperties.put(TiC.PROPERTY_FONT, value);
			}

			// Handle title color.
			if (name.equals(TiC.PROPERTY_COLOR)
				&& (value instanceof String || value instanceof ColorProxy)) {
				titleProperties.put(TiC.PROPERTY_COLOR, value);
			}

			if (titleProperties.size() > 0) {
				if (this.childProperties.containsKey(TiC.PROPERTY_TITLE)) {
					final Object existingTitleObject = this.childProperties.get(TiC.PROPERTY_TITLE);

					if (existingTitleObject instanceof KrollDict) {
						final KrollDict existingTitleProperties = (KrollDict) existingTitleObject;

						// Child template already exists.
						// Merge new properties with existing properties.
						existingTitleProperties.putAll(titleProperties);
					}
				} else {

					// Add as child property to apply to default template.
					this.childProperties.put(TiC.PROPERTY_TITLE, titleProperties);
				}
			}
		}
		if (name.equals(TiC.PROPERTY_IMAGE) && value instanceof String) {
			final KrollDict imageProperties = new KrollDict();

			// Handle image path.
			imageProperties.put(TiC.PROPERTY_IMAGE, value);

			if (this.childProperties.containsKey(TiC.PROPERTY_IMAGE)) {
				final Object existingImageObject = this.childProperties.get(TiC.PROPERTY_IMAGE);

				if (existingImageObject instanceof KrollDict) {
					final KrollDict existingImageProperties = (KrollDict) existingImageObject;

					// Child template already exists.
					// Merge new properties with existing properties.
					existingImageProperties.putAll(imageProperties);
				}
			} else {

				// Add as child property to apply to default template.
				this.childProperties.put(TiC.PROPERTY_IMAGE, imageProperties);
			}
		}

		if (name.equals(TiC.PROPERTY_HEADER_VIEW) && value instanceof TiViewProxy) {
			final TiViewProxy headerProxy = (TiViewProxy) value;

			// Set header view parent, so it can be released correctly.
			headerProxy.setParent(this);
		}
		if (name.equals(TiC.PROPERTY_FOOTER_VIEW) && value instanceof TiViewProxy) {
			final TiViewProxy footerProxy = (TiViewProxy) value;

			// Set footer view parent, so it can be released correctly.
			footerProxy.setParent(this);
		}

		if (name.equals(TiC.PROPERTY_SELECTED_BACKGROUND_COLOR)) {
			Log.w(TAG, "selectedBackgroundColor is deprecated, use backgroundSelectedColor instead.");
			setProperty(TiC.PROPERTY_BACKGROUND_SELECTED_COLOR, value);
		}
		if (name.equals(TiC.PROPERTY_SELECTED_BACKGROUND_IMAGE)) {
			Log.w(TAG, "selectedBackgroundImage is deprecated, use backgroundSelectedImage instead.");
			setProperty(TiC.PROPERTY_BACKGROUND_SELECTED_IMAGE, value);
		}

		if (name.equals(TiC.PROPERTY_CAN_MOVE)) {
			invalidate();
		}
	}

	private void loadTemplate()
	{
		// Fetch the ListView this ListItem has been added to.
		final ListViewProxy listViewProxy = getListViewProxy();
		if (listViewProxy == null) {
			return;
		}

		// Fetch template that this ListItem should use, if not done already.
		final KrollDict listViewProperties = listViewProxy.getProperties();
		final KrollDict listViewTemplates = listViewProperties.getKrollDict(TiC.PROPERTY_TEMPLATES);
		if (this.templateId == null) {
			// Attempt to obtain ListItem `template` identifier. If not set, then use default template.
			final String defaultTemplateId = listViewProperties.optString(
				TiC.PROPERTY_DEFAULT_ITEM_TEMPLATE, UIModule.LIST_ITEM_TEMPLATE_DEFAULT);
			this.templateId = properties.optString(TiC.PROPERTY_TEMPLATE, defaultTemplateId);
		}
		if ((listViewTemplates != null) && listViewTemplates.containsKey(this.templateId)) {
			// Obtain specified template for item.
			this.template = listViewTemplates.getKrollDict(this.templateId);
		}
	}

	/**
	 * Release views for item to reclaim memory.
	 */
	@Override
	public void releaseViews()
	{
		this.holder = null;

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
	 * ItemView class used for ListItem.
	 * Auto-fills to width of parent.
	 */
	private static class ItemView extends TiView
	{
		public ItemView(TiViewProxy proxy)
		{
			super(proxy);

			getLayoutParams().autoFillsWidth = true;
		}

		@Override
		protected boolean canApplyTouchFeedback(@NonNull KrollDict props)
		{
			// Prevent TiUIView from overriding `touchFeedback` effect.
			return false;
		}
	}
}
