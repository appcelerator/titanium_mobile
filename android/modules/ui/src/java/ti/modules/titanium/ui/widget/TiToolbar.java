package ti.modules.titanium.ui.widget;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Window;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.common.AsyncResult;
import org.appcelerator.kroll.common.TiMessenger;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiColorHelper;
import org.appcelerator.titanium.view.TiDrawableReference;
import org.appcelerator.titanium.view.TiUIView;

import java.util.HashMap;
import java.util.Map;

public class TiToolbar extends TiUIView implements Handler.Callback
{
	//region private primitive fields
	private final int BACKGROUND_TRANSLUCENT_VALUE = 92;
	private final int BACKGROUND_SOLID_VALUE = 255;
	//endregion
	//region private Object fields
	private Handler mainHandler = new Handler(Looper.getMainLooper(), this);
	private Toolbar toolbar;
	private Object logo = null;
	private Object navigationIcon = null;
	private Object overflowMenuIcon = null;
	private TiViewProxy[] viewProxiesArray;
	//endregion
	//region common message types
	private final int TOOLBAR_SET_COLOR = 10001;
	private final int TOOLBAR_SET_TRANSLUCENCY = 10002;
	private final int TOOLBAR_SET_BACKGROUND_EXTENDED = 10003;
	//endregion
	//region Android only message types
	private final int TOOLBAR_SHOW_OVERFLOW_MENU = 10004;
	private final int TOOLBAR_HIDE_OVERFLOW_MENU = 10005;
	private final int TOOLBAR_SET_LOGO = 10006;
	private final int TOOLBAR_SET_NAVIGATION_ICON = 10007;
	private final int TOOLBAR_SET_OVERFLOW_MENU_ICON = 10008;
	private final int TOOLBAR_DISMISS_POPUP_MENUS = 10009;
	private final int TOOLBAR_SET_TITLE = 10010;
	private final int TOOLBAR_SET_TITLE_TEXT_COLOR = 10011;
	private final int TOOLBAR_SET_SUBTITLE = 10012;
	private final int TOOLBAR_SET_SUBTITLE_TEXT_COLOR = 10013;
	private final int TOOLBAR_COLLAPSE_ACTION_VEIW = 10014;
	private final int TOOLBAR_SET_CONTENT_INSET_END_WITH_ACTIONS = 10015;
	private final int TOOLBAR_SET_CONTENT_INSET_START_WITH_NAVIGATION = 10016;
	private final int TOOLBAR_SET_CONTENT_INSETS_ABSOLUTE = 10017;
	private final int TOOLBAR_SET_CONTENT_INSETS_RELATIVE = 10018;
	//endregion

	private Map<String, TiDrawableReference> drawableMap = new HashMap<String, TiDrawableReference>();
	private KrollFunction resourceLoadedListener = null;
	/**
	 * Constructs a TiUIView object with the associated proxy.
	 * @param proxy the associated proxy.
	 * @module.api
	 */
	public TiToolbar(final TiViewProxy proxy)
	{
		super(proxy);
		toolbar = new Toolbar(proxy.getActivity()) {
			// Check the resource loading once the view is drawn
			@Override
			protected void onDraw(Canvas canvas)
			{
				super.onDraw(canvas);
				//if we have attached listener send the dictionary
				if (resourceLoadedListener != null) {
					KrollDict callbackParam = new KrollDict();
					callbackParam.put(TiC.PROPERTY_LOGO,
									  checkResourceLoading(drawableMap.get(TiC.PROPERTY_LOGO), this.getLogo()));
					callbackParam.put(
						TiC.PROPERTY_OVERFLOW_ICON,
						checkResourceLoading(drawableMap.get(TiC.PROPERTY_OVERFLOW_ICON), this.getOverflowIcon()));
					callbackParam.put(
						TiC.PROPERTY_NAVIGATION_ICON,
						checkResourceLoading(drawableMap.get(TiC.PROPERTY_NAVIGATION_ICON), this.getNavigationIcon()));
					resourceLoadedListener.callAsync(proxy.getKrollObject(), callbackParam);
				}
			}
		};

		setNativeView(toolbar);
	}

	/**
	 * Adds custom views in the toolbar
	 * @param proxies View proxies to be used
	 */
	public void setItems(TiViewProxy[] proxies)
	{
		if (proxies != null) {
			for (int i = 0; i < proxies.length; i++) {
				toolbar.addView(convertLayoutParamsForView(proxies[i].getOrCreateView()));
			}
		}
	}

	/**
	 * Sets the background color of the toolbar
	 * @param color String in Titanium color format
	 */
	public void setToolbarColor(String color)
	{
		if (!TiApplication.isUIThread()) {
			TiMessenger.sendBlockingMainMessage(mainHandler.obtainMessage(TOOLBAR_SET_COLOR), color);
		} else {
			handleBackgroundColor(color);
		}
	}

	/**
	 * Handler for background color change.
	 * @param color String in Titanium color format
	 */
	private void handleBackgroundColor(String color)
	{
		toolbar.setBackgroundColor((TiColorHelper.parseColor(color)));
		if (proxy.hasProperty(TiC.PROPERTY_TRANSLUCENT)) {
			if ((Boolean) proxy.getProperty(TiC.PROPERTY_TRANSLUCENT)) {
				toolbar.getBackground().setAlpha(BACKGROUND_TRANSLUCENT_VALUE);
			}
		}
	}

	/**
	 * Sets up the Toolbar to extend behind the Status Bar.
	 * This is useful when the Toolbar instance is set as a support bar in an activity
	 * and it positioned at the very top of it.
	 */
	public void setToolbarExtendBackground()
	{
		if (!TiApplication.isUIThread()) {
			TiMessenger.sendBlockingMainMessage(mainHandler.obtainMessage(TOOLBAR_SET_BACKGROUND_EXTENDED));
		} else {
			handleBackgroundExtended();
		}
	}

	/**
	 * Handler for extending the background.
	 */
	private void handleBackgroundExtended()
	{
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			Window window = TiApplication.getAppCurrentActivity().getWindow();
			//Compensate for status bar's height
			toolbar.setFitsSystemWindows(true);
			//Set flags for the current window that allow drawing behind status bar
			window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
														| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
			window.setStatusBarColor(Color.TRANSPARENT);
		}
	}

	/**
	 * Calculates the Status Bar's height depending on the device
	 * @return The status bar's height. 0 if the API level does not have status_bar_height resource
	 */
	private int calculateStatusBarHeight()
	{
		int resourceId =
			TiApplication.getAppCurrentActivity().getResources().getIdentifier("status_bar_height", "dimen", "android");
		if (resourceId > 0) {
			return TiApplication.getAppCurrentActivity().getResources().getDimensionPixelSize(resourceId);
		}
		return 0;
	}

	/**
	 * Sets whether the Toolbar's background is translucent.
	 * Achieved by setting the alpha of the background to 36%.
	 * @param value Boolean value to set to translucency.
	 */
	public void setTranslucent(boolean value)
	{
		if (!TiApplication.isUIThread()) {
			TiMessenger.sendBlockingMainMessage(mainHandler.obtainMessage(TOOLBAR_SET_TRANSLUCENCY), value);
		} else {
			handleTranslucency(value);
		}
	}

	/**
	 * Handler for translucency change
	 * @param value Boolean value to set to translucency.
	 */
	private void handleTranslucency(boolean value)
	{
		toolbar.getBackground().setAlpha(value ? BACKGROUND_TRANSLUCENT_VALUE : BACKGROUND_SOLID_VALUE);
	}

	/**
	 * Changes the LayoutParams type of custom views added to the Toolbar.
	 * Width and height are preserved.
	 * They need to be of type Toolbar.LayoutParams.
	 * @param source
	 * @return
	 */
	private View convertLayoutParamsForView(TiUIView source)
	{
		View res = source.getNativeView();
		TiDimension widthDimension = source.getLayoutParams().optionWidth;
		int width = widthDimension != null ? widthDimension.getAsPixels(toolbar) : Toolbar.LayoutParams.WRAP_CONTENT;
		TiDimension heightDimension = source.getLayoutParams().optionHeight;
		int height = heightDimension != null ? heightDimension.getAsPixels(toolbar) : Toolbar.LayoutParams.WRAP_CONTENT;
		res.setLayoutParams(new Toolbar.LayoutParams(width, height));
		return res;
	}

	/**
	 * Shows the overflow menu if there is one.
	 */
	public void showOverFlowMenu()
	{
		if (!TiApplication.isUIThread()) {
			TiMessenger.sendBlockingMainMessage(mainHandler.obtainMessage(TOOLBAR_SHOW_OVERFLOW_MENU));
		} else {
			handleShowOverFlowMenu();
		}
	}

	/**
	 * Handles overflow menu show.
	 */
	private void handleShowOverFlowMenu()
	{
		((Toolbar) getNativeView()).showOverflowMenu();
	}

	/**
	 * Hides the overflow menu if there is one.
	 */
	public void hideOverFlowMenu()
	{
		if (!TiApplication.isUIThread()) {
			TiMessenger.sendBlockingMainMessage(mainHandler.obtainMessage(TOOLBAR_HIDE_OVERFLOW_MENU));
		} else {
			handleHideOverFlowMenu();
		}
	}

	/**
	 * Handles overflow menu hide.
	 */
	private void handleHideOverFlowMenu()
	{
		((Toolbar) getNativeView()).hideOverflowMenu();
	}

	/**
	 * Sets the Toolbar's logo image.
	 * @param object Image to load. It can be passed as a Blob, File or path to a resource.
	 */
	public void setLogo(Object object)
	{
		if (!TiApplication.isUIThread()) {
			TiMessenger.sendBlockingMainMessage(mainHandler.obtainMessage(TOOLBAR_SET_LOGO), object);
		} else {
			handleSetLogo(object);
		}
	}

	/**
	 * Handles the logo change.
	 * @param object
	 */
	private void handleSetLogo(Object object)
	{
		logo = object;
		TiDrawableReference tiDrawableReference = TiDrawableReference.fromObject(proxy, object);
		((Toolbar) getNativeView()).setLogo(tiDrawableReference.getDrawable());
		drawableMap.put(TiC.PROPERTY_LOGO, tiDrawableReference);
	}

	/**
	 * Return the current logo in the format it was passed
	 * @return
	 */
	public Object getLogo()
	{
		return logo;
	}

	/**
	 * Sets the Toolbar's navigation icon.
	 * @param object Image to load. It can be passed as a Blob, File or path to a resource.
	 */
	public void setNavigationIcon(Object object)
	{
		if (!TiApplication.isUIThread()) {
			TiMessenger.sendBlockingMainMessage(mainHandler.obtainMessage(TOOLBAR_SET_NAVIGATION_ICON), object);
		} else {
			handleSetNavigationIcon(object);
		}
	}

	/**
	 * Handles the navigation icon change.
	 * @param object
	 */
	private void handleSetNavigationIcon(Object object)
	{
		navigationIcon = object;
		TiDrawableReference tiDrawableReference = TiDrawableReference.fromObject(proxy, object);
		((Toolbar) getNativeView()).setNavigationIcon(tiDrawableReference.getDrawable());
		drawableMap.put(TiC.PROPERTY_NAVIGATION_ICON, tiDrawableReference);
	}

	/**
	 * Returns the currently set navigation icon in the format it was set.
	 * @return
	 */
	public Object getNavigationIcon()
	{
		return navigationIcon;
	}

	/**
	 * Sets the overflow menu icon.
	 * @param object Image to load. It can be passed as a Blob, File or path to a resource.
	 */
	public void setOverflowMenuIcon(Object object)
	{
		if (!TiApplication.isUIThread()) {
			TiMessenger.sendBlockingMainMessage(mainHandler.obtainMessage(TOOLBAR_SET_OVERFLOW_MENU_ICON), object);
		} else {
			handleSetOverflowMenuIcon(object);
		}
	}

	/**
	 * Handles overflow menu icon change
	 * @param object
	 */
	private void handleSetOverflowMenuIcon(Object object)
	{
		overflowMenuIcon = object;
		TiDrawableReference tiDrawableReference = TiDrawableReference.fromObject(proxy, object);
		((Toolbar) getNativeView()).setOverflowIcon(tiDrawableReference.getDrawable());
		drawableMap.put(TiC.PROPERTY_OVERFLOW_ICON, tiDrawableReference);
	}

	/**
	 * Returns the overflow menu icon in the format it was set.
	 * @return
	 */
	public Object getOverflowMenuIcon()
	{
		return overflowMenuIcon;
	}

	/**
	 * Closes all action views expanded and hides overflow menu.
	 */
	public void dismissPopupMenus()
	{
		if (!TiApplication.isUIThread()) {
			TiMessenger.sendBlockingMainMessage(mainHandler.obtainMessage((TOOLBAR_DISMISS_POPUP_MENUS)));
		} else {
			handleDismissPopupMenus();
		}
	}

	/**
	 * Handles closing all action views expanded and hiding overflow menu.
	 */
	private void handleDismissPopupMenus()
	{
		((Toolbar) getNativeView()).dismissPopupMenus();
	}

	/**
	 * Sets the Toolbar title
	 * @param value String to be used as title.
	 */
	private void setTitle(String value)
	{
		if (!TiApplication.isUIThread()) {
			TiMessenger.sendBlockingMainMessage(mainHandler.obtainMessage(TOOLBAR_SET_TITLE), value);
		} else {
			handleSetTitle(value);
		}
	}

	/**
	 * Handles title change
	 * @param value
	 */
	private void handleSetTitle(String value)
	{
		toolbar.setTitle(value);
	}

	/**
	 * Sets title's text color
	 * @param value Color in any format supported by Titanium.
	 */
	private void setTitleTextColor(String value)
	{
		if (!TiApplication.isUIThread()) {
			TiMessenger.sendBlockingMainMessage(mainHandler.obtainMessage(TOOLBAR_SET_TITLE_TEXT_COLOR), value);
		} else {
			handleSetTitleTextColor(value);
		}
	}

	/**
	 * Handles title's text color change.
	 * @param value
	 */
	private void handleSetTitleTextColor(String value)
	{
		toolbar.setTitleTextColor(TiColorHelper.parseColor(value));
	}

	/**
	 * Sets subtitle.
	 * @param value String to be used as title.
	 */
	private void setSubtitle(String value)
	{
		if (!TiApplication.isUIThread()) {
			TiMessenger.sendBlockingMainMessage(mainHandler.obtainMessage(TOOLBAR_SET_SUBTITLE), value);
		} else {
			handleSetSubtitle(value);
		}
	}

	/**
	 * Handles subtitle change.
	 * @param value
	 */
	private void handleSetSubtitle(String value)
	{
		toolbar.setSubtitle(value);
	}

	/**
	 * Sets subtitle's text color
	 * @param value Color in any format supported by Titanium.
	 */
	private void setSubtitleTextColor(String value)
	{
		if (!TiApplication.isUIThread()) {
			TiMessenger.sendBlockingMainMessage(mainHandler.obtainMessage(TOOLBAR_SET_SUBTITLE_TEXT_COLOR), value);
		} else {
			handleSetSubtitleTextColor(value);
		}
	}

	/**
	 * Handles subtitle's text color change.
	 * @param value
	 */
	private void handleSetSubtitleTextColor(String value)
	{
		toolbar.setSubtitleTextColor(TiColorHelper.parseColor(value));
	}

	/**
	 * Saves the proxy objects of the views passed as custom items.
	 * Sets them as current custom views.
	 * @param value
	 */
	private void setViewProxiesArray(Object[] value)
	{
		viewProxiesArray = new TiViewProxy[value.length];
		for (int i = 0; i < value.length; i++) {
			viewProxiesArray[i] = (TiViewProxy) value[i];
		}
		setItems(viewProxiesArray);
	}

	/**
	 * Closes custom views's added in the toolbar.
	 */
	public void collapseActionView()
	{
		if (!TiApplication.isUIThread()) {
			TiMessenger.sendBlockingMainMessage(mainHandler.obtainMessage(TOOLBAR_COLLAPSE_ACTION_VEIW));
		} else {
			handleCollapseActionView();
		}
	}

	/**
	 * Handles closing custom view.
	 */
	private void handleCollapseActionView()
	{
		toolbar.collapseActionView();
	}

	public void setContentInsetEndWithActions(int value)
	{
		if (!TiApplication.isUIThread()) {
			TiMessenger.sendBlockingMainMessage(mainHandler.obtainMessage(TOOLBAR_SET_CONTENT_INSET_END_WITH_ACTIONS),
												value);
		} else {
			handleSetContentInsetEndWithActions(value);
		}
	}

	private void handleSetContentInsetEndWithActions(int value)
	{
		toolbar.setContentInsetEndWithActions(value);
	}

	public void setContentInsetStartWithNavigation(int value)
	{
		if (!TiApplication.isUIThread()) {
			TiMessenger.sendBlockingMainMessage(
				mainHandler.obtainMessage(TOOLBAR_SET_CONTENT_INSET_START_WITH_NAVIGATION), value);
		} else {
			handleSetContentInsetStartWithNavigation(value);
		}
	}

	private void handleSetContentInsetStartWithNavigation(int value)
	{
		toolbar.setContentInsetStartWithNavigation(value);
	}

	public void setContentInsetsAbsolute(int insetLeft, int insetRight)
	{
		Integer[] values = new Integer[] { insetLeft, insetRight };
		if (!TiApplication.isUIThread()) {
			TiMessenger.sendBlockingMainMessage(mainHandler.obtainMessage(TOOLBAR_SET_CONTENT_INSETS_ABSOLUTE), values);
		} else {
			handleSetContentInsetsAbsolute(values);
		}
	}

	private void handleSetContentInsetsAbsolute(Integer values[])
	{
		toolbar.setContentInsetsAbsolute(values[0], values[1]);
	}

	public void setContentInsetsRelative(int insetLeft, int insetRight)
	{
		Integer[] values = new Integer[] { insetLeft, insetRight };
		if (!TiApplication.isUIThread()) {
			TiMessenger.sendBlockingMainMessage(mainHandler.obtainMessage(TOOLBAR_SET_CONTENT_INSETS_RELATIVE), values);
		} else {
			handleSetContentInsetsRelative(values);
		}
	}

	private void handleSetContentInsetsRelative(Integer values[])
	{
		toolbar.setContentInsetsAbsolute(values[0], values[1]);
	}

	private boolean checkResourceLoading(TiDrawableReference source, Drawable result)
	{
		return (source != null && result != null && source.compareBitmapWith(result));
	}

	@Override
	public void processProperties(KrollDict d)
	{
		//process internal properties
		if (d.containsKey("resourceLoadedListener") && d.get("resourceLoadedListener") instanceof KrollFunction) {
			resourceLoadedListener = ((KrollFunction) d.get("resourceLoadedListener"));
		}
		//region process common properties
		if (d.containsKey(TiC.PROPERTY_BAR_COLOR)) {
			setToolbarColor(d.getString(TiC.PROPERTY_BAR_COLOR));
		}
		if (d.containsKey(TiC.PROPERTY_EXTEND_BACKGROUND)) {
			if (d.getBoolean(TiC.PROPERTY_EXTEND_BACKGROUND)) {
				setToolbarExtendBackground();
			}
		}
		if (d.containsKey(TiC.PROPERTY_ITEMS)) {
			setViewProxiesArray(((Object[]) d.get(TiC.PROPERTY_ITEMS)));
		}
		if (d.containsKey(TiC.PROPERTY_TRANSLUCENT)) {
			setTranslucent(d.getBoolean(TiC.PROPERTY_TRANSLUCENT));
		}
		//endregion
		//region Android only properties
		if (d.containsKey(TiC.PROPERTY_LOGO)) {
			setLogo(d.get(TiC.PROPERTY_LOGO));
		}
		if (d.containsKey(TiC.PROPERTY_NAVIGATION_ICON)) {
			setNavigationIcon(d.get(TiC.PROPERTY_NAVIGATION_ICON));
		}
		if (d.containsKey(TiC.PROPERTY_OVERFLOW_ICON)) {
			setOverflowMenuIcon(d.get(TiC.PROPERTY_OVERFLOW_ICON));
		}
		if (d.containsKey(TiC.PROPERTY_TITLE)) {
			setTitle(d.getString(TiC.PROPERTY_TITLE));
		}
		if (d.containsKey(TiC.PROPERTY_TITLE_TEXT_COLOR)) {
			setTitleTextColor(d.getString(TiC.PROPERTY_TITLE_TEXT_COLOR));
		}
		if (d.containsKey(TiC.PROPERTY_SUBTITLE)) {
			setSubtitle(d.getString(TiC.PROPERTY_SUBTITLE));
		}
		if (d.containsKey(TiC.PROPERTY_SUBTITLE_TEXT_COLOR)) {
			setSubtitleTextColor(d.getString(TiC.PROPERTY_SUBTITLE_TEXT_COLOR));
		}
		if (d.containsKey(TiC.PROPERTY_SUBTITLE_TEXT_COLOR)) {
			setSubtitleTextColor(d.getString(TiC.PROPERTY_SUBTITLE_TEXT_COLOR));
		}
		if (d.containsKey(TiC.PROPERTY_CONTENT_INSET_END_WITH_ACTIONS)) {
			if (toolbar != null) {
				setContentInsetEndWithActions(d.getInt(TiC.PROPERTY_CONTENT_INSET_END_WITH_ACTIONS));
			}
		}
		if (d.containsKey(TiC.PROPERTY_CONTENT_INSET_START_WITH_NAVIGATION)) {
			if (toolbar != null) {
				setContentInsetStartWithNavigation(d.getInt(TiC.PROPERTY_CONTENT_INSET_START_WITH_NAVIGATION));
			}
		}
		//end region
		super.processProperties(d);
	}

	@Override
	public void propertyChanged(String key, Object oldValue, Object newValue, KrollProxy proxy)
	{
		super.propertyChanged(key, oldValue, newValue, proxy);
		if (key.equals(TiC.PROPERTY_BAR_COLOR)) {
			setToolbarColor(((String) newValue));
		}
		if (key.equals(TiC.PROPERTY_TRANSLUCENT)) {
			setTranslucent(((Boolean) newValue));
		}
		if (key.equals(TiC.PROPERTY_ITEMS)) {
			setItems(((TiViewProxy[]) newValue));
		}
		if (key.equals(TiC.PROPERTY_LOGO)) {
			setLogo(newValue);
		}
		if (key.equals(TiC.PROPERTY_NAVIGATION_ICON)) {
			setNavigationIcon(newValue);
		}
		if (key.equals(TiC.PROPERTY_OVERFLOW_ICON)) {
			setOverflowMenuIcon(newValue);
		}
		if (key.equals(TiC.PROPERTY_TITLE)) {
			setTitle((String) newValue);
		}
		if (key.equals(TiC.PROPERTY_TITLE_TEXT_COLOR)) {
			setTitleTextColor((String) newValue);
		}
		if (key.equals(TiC.PROPERTY_SUBTITLE)) {
			setSubtitle((String) newValue);
		}
		if (key.equals(TiC.PROPERTY_SUBTITLE_TEXT_COLOR)) {
			setSubtitleTextColor((String) newValue);
		}
		if (key.equals(TiC.PROPERTY_CONTENT_INSET_END_WITH_ACTIONS)) {
			setContentInsetEndWithActions((Integer) newValue);
		}
		if (key.equals(TiC.PROPERTY_CONTENT_INSET_START_WITH_NAVIGATION)) {
			setContentInsetStartWithNavigation((Integer) newValue);
		}
	}

	@Override
	public boolean handleMessage(Message msg)
	{
		switch (msg.what) {
			case TOOLBAR_SET_COLOR:
				AsyncResult resultBackgroundColor = (AsyncResult) msg.obj;
				handleBackgroundColor((String) resultBackgroundColor.getArg());
				resultBackgroundColor.setResult(null);
				return true;
			case TOOLBAR_SET_TRANSLUCENCY:
				AsyncResult resultTranslucency = (AsyncResult) msg.obj;
				handleTranslucency((Boolean) resultTranslucency.getArg());
				resultTranslucency.setResult(null);
				return true;
			case TOOLBAR_SET_BACKGROUND_EXTENDED:
				AsyncResult resultBackgroundExtended = (AsyncResult) msg.obj;
				handleBackgroundExtended();
				resultBackgroundExtended.setResult(null);
				return true;
			case TOOLBAR_SHOW_OVERFLOW_MENU:
				AsyncResult resultShowOverflow = (AsyncResult) msg.obj;
				handleShowOverFlowMenu();
				resultShowOverflow.setResult(null);
				return true;
			case TOOLBAR_HIDE_OVERFLOW_MENU:
				AsyncResult resultHideOverflow = (AsyncResult) msg.obj;
				handleHideOverFlowMenu();
				resultHideOverflow.setResult(null);
				return true;
			case TOOLBAR_SET_LOGO:
				AsyncResult resultSetLogo = (AsyncResult) msg.obj;
				handleSetLogo(resultSetLogo.getArg());
				resultSetLogo.setResult(null);
				return true;
			case TOOLBAR_SET_NAVIGATION_ICON:
				AsyncResult resultSetNavigationIcon = (AsyncResult) msg.obj;
				handleSetNavigationIcon(resultSetNavigationIcon.getArg());
				resultSetNavigationIcon.setResult(null);
				return true;
			case TOOLBAR_SET_OVERFLOW_MENU_ICON:
				AsyncResult resultSetOverflowMenuIcon = (AsyncResult) msg.obj;
				handleSetOverflowMenuIcon(resultSetOverflowMenuIcon.getArg());
				resultSetOverflowMenuIcon.setResult(null);
				return true;
			case TOOLBAR_DISMISS_POPUP_MENUS:
				AsyncResult resultDismissPopupMenus = ((AsyncResult) msg.obj);
				handleDismissPopupMenus();
				resultDismissPopupMenus.setResult(null);
				return true;
			case TOOLBAR_SET_TITLE:
				AsyncResult setTitleResult = ((AsyncResult) msg.obj);
				handleSetTitle(((String) setTitleResult.getArg()));
				setTitleResult.setResult(null);
				return true;
			case TOOLBAR_SET_TITLE_TEXT_COLOR:
				AsyncResult setTitleTextColorResult = ((AsyncResult) msg.obj);
				handleSetTitleTextColor(((String) setTitleTextColorResult.getArg()));
				setTitleTextColorResult.setResult(null);
				return true;
			case TOOLBAR_SET_SUBTITLE:
				AsyncResult setSubtitleResult = ((AsyncResult) msg.obj);
				handleSetSubtitle(((String) setSubtitleResult.getArg()));
				setSubtitleResult.setResult(null);
				return true;
			case TOOLBAR_SET_SUBTITLE_TEXT_COLOR:
				AsyncResult setSubtitleTextColorResult = ((AsyncResult) msg.obj);
				handleSetSubtitleTextColor(((String) setSubtitleTextColorResult.getArg()));
				setSubtitleTextColorResult.setResult(null);
				return true;
			case TOOLBAR_COLLAPSE_ACTION_VEIW:
				AsyncResult collapseActionViewResult = ((AsyncResult) msg.obj);
				handleCollapseActionView();
				collapseActionViewResult.setResult(null);
				return true;
			case TOOLBAR_SET_CONTENT_INSET_END_WITH_ACTIONS:
				AsyncResult setContentInsetEndWithActionResult = ((AsyncResult) msg.obj);
				handleSetContentInsetEndWithActions((Integer) setContentInsetEndWithActionResult.getArg());
				setContentInsetEndWithActionResult.setResult(null);
				return true;
			case TOOLBAR_SET_CONTENT_INSET_START_WITH_NAVIGATION:
				AsyncResult setContentInsetStartWithNavigationResult = ((AsyncResult) msg.obj);
				handleSetContentInsetStartWithNavigation((Integer) setContentInsetStartWithNavigationResult.getArg());
				setContentInsetStartWithNavigationResult.setResult(null);
				return true;
			case TOOLBAR_SET_CONTENT_INSETS_ABSOLUTE:
				AsyncResult setContentInsetsAbsoluteResult = ((AsyncResult) msg.obj);
				handleSetContentInsetsAbsolute((Integer[]) setContentInsetsAbsoluteResult.getArg());
				setContentInsetsAbsoluteResult.setResult(null);
				return true;
			case TOOLBAR_SET_CONTENT_INSETS_RELATIVE:
				AsyncResult setContentInsetsRelativeResult = ((AsyncResult) msg.obj);
				handleSetContentInsetsRelative(((Integer[]) setContentInsetsRelativeResult.getArg()));
				setContentInsetsRelativeResult.setResult(null);
				return true;
			default:
				return false;
		}
	}
}
