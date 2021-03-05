/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium;

/**
 * A comprehensive list of global Titanium events and properties constants.
 * These are sorted alphabetically.
 */
public class TiC

{
	public static final int API_LEVEL_HONEYCOMB = 11;
	public static final int API_LEVEL_ICE_CREAM_SANDWICH = 14;
	public static final int API_LEVEL_JELLY_BEAN = 16;

	public static final int PERMISSION_CODE_CALENDAR = 100;
	public static final int PERMISSION_CODE_CAMERA = 101;
	public static final int PERMISSION_CODE_CONTACTS = 102;
	public static final int PERMISSION_CODE_EXTERNAL_STORAGE = 103;
	public static final int PERMISSION_CODE_LOCATION = 104;
	public static final int PERMISSION_CODE_OLD_CALENDAR = 105;
	public static final int PERMISSION_CODE_MICROPHONE = 106;

	public static final String PERMISSION_CALENDAR = "calendar";
	public static final String PERMISSION_CAMERA = "camera";
	public static final String PERMISSION_CONTACTS = "contacts";
	public static final String PERMISSION_EXTERNAL_STORAGE = "externalstorage";
	public static final String PERMISSION_LOCATION = "location";

	public static final String ERROR_PROPERTY_CODE = "code";
	public static final String ERROR_PROPERTY_ERRORCODE = "errorcode";
	public static final String ERROR_PROPERTY_MESSAGE = "message";

	public static final String EXTRA_TI_NEW_INTENT = "ti.intent.extra.NEW_INTENT";

	/**
	 * ERROR_CODE constants are for values of the code property in
	 * events and callback objects. When you can use a nonzero OS-provided
	 * error code, use that instead.
	 */
	public static final int ERROR_CODE_UNKNOWN = -1;
	public static final int ERROR_CODE_NO_ERROR = 0;

	/**
	 * @module.api
	 */
	public static final String EVENT_ADDED_TO_TAB = "addedtotab";

	/**
	 * @module.api
	 */
	public static final String EVENT_ANDROID_BACK = "androidback";

	/**
	 * @module.api
	 */
	public static final String EVENT_ANDROID_CAMERA = "androidcamera";

	/**
	 * @module.api
	 */
	public static final String EVENT_ANDROID_FOCUS = "androidfocus";

	/**
	 * @module.api
	 */
	public static final String EVENT_ANDROID_SEARCH = "androidsearch";

	/**
	 * @module.api
	 */
	public static final String EVENT_ANDROID_VOLDOWN = "androidvoldown";

	/**
	 * @module.api
	 */
	public static final String EVENT_ANDROID_VOLUP = "androidvolup";

	/**
	 * @module.api
	 */
	public static final String EVENT_BATTERY = "battery";

	/**
	 * @module.api
	 */
	public static final String EVENT_BLACKLIST_URL = "blacklisturl";

	/**
	 * @module.api
	 */
	public static final String EVENT_BLOCKED_URL = "blockedurl";

	/**
	 * @module.api
	 */
	public static final String EVENT_BLUR = "blur";

	/**
	 * @module.api
	 */
	public static final String EVENT_CAMERA_READY = "cameraready";

	/**
	 * @module.api
	 */
	public static final String EVENT_CANCEL = "cancel";

	/**
	 * @module.api
	 */
	public static final String EVENT_COLLAPSE = "collapse";

	/**
	 * @module.api
	 */
	public static final String EVENT_CHANGE = "change";

	/**
	 * @module.api
	 */
	public static final String EVENT_CLICK = "click";

	/**
	 * @module.api
	 */
	public static final String EVENT_CLOSE = "close";

	/**
	 * @module.api
	 */
	public static final String EVENT_COMPLETE = "complete";

	/**
	 * @module.api
	 */
	public static final String EVENT_DESTROY = "destroy";

	/**
	 * @module.api
	 */
	public static final String EVENT_DISPOSE_HANDLE = "disposehandle";

	/**
	 * @module.api
	 */
	public static final String EVENT_DOUBLE_CLICK = "dblclick";

	/**
	 * @module.api
	 */
	public static final String EVENT_DOUBLE_TAP = "doubletap";

	/**
	 * @module.api
	 */
	public static final String EVENT_DRAGSTART = "dragstart";

	/**
	 * @module.api
	 */
	public static final String EVENT_DRAGEND = "dragend";

	/**
	 * @module.api
	 */
	public static final String EVENT_DURATION_AVAILABLE = "durationavailable";

	/**
	 * @module.api
	 */
	public static final String EVENT_ERROR = "error";

	/**
	 * @module.api
	 */
	public static final String EVENT_EXPAND = "expand";

	/**
	 * @module.api
	 */
	public static final String EVENT_FOCUS = "focus";

	/**
	 * @module.api
	 */
	public static final String EVENT_FOCUSED = "focused";

	/**
	 * @module.api
	 */
	public static final String EVENT_SELECTED = "selected";

	/**
	 * @module.api
	 */
	public static final String EVENT_UNSELECTED = "unselected";

	/**
	 * @module.api
	 */
	public static final String EVENT_KEY_PRESSED = "keypressed";

	/**
	 * @module.api
	 */
	public static final String EVENT_HEADING = "heading";

	/**
	 * @module.api
	 */
	public static final String EVENT_ITEM_CLICK = "itemclick";

	/**
	 * @module.api
	 */
	public static final String EVENT_LOAD = "load";

	/**
	 * @module.api
	 */
	public static final String EVENT_LOADSTATE = "loadstate";

	/**
	 * @module.api
	 */
	public static final String EVENT_LOCATION = "location";

	/**
	 * @module.api
	 */
	public static final String EVENT_LONGCLICK = "longclick";

	/**
	 * @module.api
	 */
	public static final String EVENT_LONGPRESS = "longpress";

	/**
	 * @module.api
	 */
	public static final String EVENT_MARKER = "marker";

	/**
	 * @module.api
	 */
	public static final String EVENT_NEW_INTENT = "newintent";

	/**
	 * @module.api
	 */
	public static final String EVENT_NO_RESULTS = "noresults";

	/**
	 * @module.api
	 */
	public static final String EVENT_ON_REQUEST_PERMISSIONS = "onrequestpermissions";

	/**
	 * @module.api
	 */
	public static final String EVENT_OPEN = "open";

	/**
	 * @module.api
	 */
	public static final String EVENT_PAUSE = "pause";

	/**
	 * @module.api
	 */
	public static final String EVENT_PAUSED = "paused";

	/**
	 * @module.api
	 */
	public static final String EVENT_PINCH = "pinch";

	/**
	 * @module.api
	 */
	public static final String EVENT_PLAYBACK_STATE = "playbackstate";

	/**
	 * @module.api
	 */
	public static final String EVENT_PRELOAD = "preload";

	/**
	 * @module.api
	 */
	public static final String EVENT_POST_LAYOUT = "postlayout";

	/**
	 * @module.api
	 */
	public static final String EVENT_PLAYING = "playing";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_ACCESSORY_CLICKED = "accessoryClicked";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_CLICKSOURCE = "clicksource";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_CURRENT_PLAYBACK_TIME = "currentPlaybackTime";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_DETAIL = "detail";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_DIRECTION = "direction";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_ERROR = "error";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_INDEX = "index";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_INTENT = "intent";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_IS_DIALOG = "isDialog";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_IS_USER_GESTURE = "isUserGesture";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_KEYCODE = "keyCode";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_LAYOUT_NAME = "layoutName";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_LOADSTATE = "loadState";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_MENU = "menu";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_MESSAGE = "message";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_OBSCURED = "obscured";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_PLAYBACK_STATE = "playbackState";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_PREVIOUS_INDEX = "previousIndex";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_PREVIOUS_TAB = "previousTab";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_REASON = "reason";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_RECURRENCE_RULES = "recurrenceRules";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_REQUEST_CODE = "requestCode";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_RESULT_CODE = "resultCode";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_ROW = "row";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_SCALE = "scale";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_TIME = "time";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_FOCUS_X = "focusX";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_FOCUS_Y = "focusY";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_CURRENT_SPAN = "currentSpan";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_CURRENT_SPAN_X = "currentSpanX";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_CURRENT_SPAN_Y = "currentSpanY";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_PREVIOUS_SPAN = "previousSpan";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_PREVIOUS_SPAN_X = "previousSpanX";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_PREVIOUS_SPAN_Y = "previousSpanY";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_TIME_DELTA = "timeDelta";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_IN_PROGRESS = "inProgress";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_SEARCH_MODE = "searchMode";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_SHORTCUT = "shortcut";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_SOURCE = "source";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_STATE = "state";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_TAB = "tab";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_THUMB_OFFSET = "thumbOffset";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_THUMB_SIZE = "thumbSize";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_TYPE = "type";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_VELOCITY = "velocity";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_X = "x";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_Y = "y";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_FORCE = "force";
	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_SIZE = "size";

	/**
	 * @module.api
	 */
	public static final String EVENT_MOVE = "move";

	/**
	 * @module.api
	 */
	public static final String EVENT_REFRESH_END = "refreshend";

	/**
	 * @module.api
	 */
	public static final String EVENT_REFRESH_START = "refreshstart";

	/**
	 * @module.api
	 */
	public static final String EVENT_REGION_CHANGED = "regionchanged";

	/**
	 * @module.api
	 */
	public static final String EVENT_RESUME = "resume";

	/**
	 * @module.api
	 */
	public static final String EVENT_RESUMED = "resumed";

	/**
	 * @module.api
	 */
	public static final String EVENT_RETURN = "return";

	/**
	 * @module.api
	 */
	public static final String EVENT_SCROLL = "scroll";

	/**
	 * @module.api
	 */
	public static final String EVENT_SCROLLEND = "scrollend";

	/**
	 * @module.api
	 */
	public static final String EVENT_SCROLLING = "scrolling";

	/**
	 * @module.api
	 */
	public static final String EVENT_SCROLLSTART = "scrollstart";

	/**
	 * @module.api
	 */
	public static final String EVENT_SHORTCUT_ITEM_CLICK = "shortcutitemclick";

	/**
	 * @module.api
	 */
	public static final String EVENT_SINGLE_TAP = "singletap";

	/**
	 * @module.api
	 */
	public static final String EVENT_SLIDE = "slide";

	/**
	 * @module.api
	 */
	public static final String EVENT_START = "start";

	/**
	 * @module.api
	 */
	public static final String EVENT_STARTED = "started";

	/**
	 * @module.api
	 */
	public static final String EVENT_START_LISTENING = "startlistening";

	/**
	 * @module.api
	 */
	public static final String EVENT_STOP = "stop";

	/**
	 * @module.api
	 */
	public static final String EVENT_STOP_LISTENING = "stoplistening";

	/**
	 * @module.api
	 */
	public static final String EVENT_SUBMIT = "submit";

	/**
	 * @module.api
	 */
	public static final String EVENT_SWIPE = "swipe";

	/**
	 * @module.api
	 */
	public static final String EVENT_TASK_REMOVED = "taskremoved";

	/**
	 * @module.api
	 */
	public static final String EVENT_TILE_ADDED = "tileadded";

	/**
	 * @module.api
	 */
	public static final String EVENT_TILE_DIALOG_OPTION_SELECTED = "tiledialogoptionselected";

	/**
	 * @module.api
	 */
	public static final String EVENT_TILE_DIALOG_CANCELED = "tiledialogcancelled";

	/**
	 * @module.api
	 */
	public static final String EVENT_TILE_DIALOG_POSITIVE = "tiledialogpositive";

	/**
	 * @module.api
	 */
	public static final String EVENT_TILE_DIALOG_NEUTRAL = "tiledialogneutral";

	/**
	 * @module.api
	 */
	public static final String EVENT_TILE_DIALOG_NEGATIVE = "tiledialognegative";

	/**
	 * @module.api
	 */
	public static final String EVENT_TILE_REMOVED = "tileremoved";

	/**
	 * @module.api
	 */
	public static final String EVENT_TOUCH_CANCEL = "touchcancel";

	/**
	 * @module.api
	 */
	public static final String EVENT_TOUCH_END = "touchend";

	/**
	 * @module.api
	 */
	public static final String EVENT_TOUCH_FILTERED = "touchfiltered";

	/**
	 * @module.api
	 */
	public static final String EVENT_TOUCH_MOVE = "touchmove";

	/**
	 * @module.api
	 */
	public static final String EVENT_TOUCH_START = "touchstart";

	/**
	 * @module.api
	 */
	public static final String EVENT_TWOFINGERTAP = "twofingertap";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROPERTY_URL = "url";

	/**
	 * @module.api
	 */
	public static final String EVENT_USER_INTERACTION = "userinteraction";

	/**
	 * @module.api
	 */
	public static final String EVENT_USER_INTERFACE_STYLE = "userinterfacestyle";

	/**
	 * @module.api
	 */
	public static final String EVENT_USER_LEAVE_HINT = "userleavehint";

	/**
	 * @module.api
	 */
	public static final String EVENT_PROXIMITY = "proximity";

	/**
	 * @module.api
	 */
	public static final String EVENT_SSL_ERROR = "sslerror";

	/**
	 * @module.api
	 */
	public static final String EVENT_WEBVIEW_ON_LOAD_RESOURCE = "onLoadResource";

	/**
	 * @module.api
	 */
	public static final String EVENT_UNFOCUSED = "unfocused";
	public static final String PROPERTY_ENTER_TRANSITION = "activityEnterTransition";
	public static final String PROPERTY_EXIT_TRANSITION = "activityExitTransition";
	public static final String PROPERTY_RETURN_TRANSITION = "activityReturnTransition";
	public static final String PROPERTY_REENTER_TRANSITION = "activityReenterTransition";
	public static final String PROPERTY_SHARED_ELEMENT_ENTER_TRANSITION = "activitySharedElementEnterTransition";
	public static final String PROPERTY_SHARED_ELEMENT_EXIT_TRANSITION = "activitySharedElementExitTransition";
	public static final String PROPERTY_SHARED_ELEMENT_REENTER_TRANSITION = "activitySharedElementReenterTransition";
	public static final String PROPERTY_SHARED_ELEMENT_RETURN_TRANSITION = "activitySharedElementReturnTransition";
	public static final String INTENT_PROPERTY_FINISH_ROOT = "finishRoot";
	public static final String INTENT_PROPERTY_IS_TAB = "isTab";
	public static final String INTENT_PROPERTY_LAYOUT = "layout";
	public static final String INTENT_PROPERTY_MESSENGER = "messenger";
	public static final String INTENT_PROPERTY_MSG_ACTIVITY_CREATED_ID = "msgActivityCreatedId";
	public static final String INTENT_PROPERTY_MSG_ID = "messageId";
	public static final String INTENT_PROPERTY_START_MODE = "startMode";
	public static final String INTENT_PROPERTY_WINDOW_ID = "windowId";
	public static final String LAYOUT_FILL = "fill";
	public static final String LAYOUT_HORIZONTAL = "horizontal";
	public static final String LAYOUT_SIZE = "size";
	public static final String LAYOUT_VERTICAL = "vertical";
	public static final String MSG_PROPERTY_EVENT_NAME = "eventName";
	public static final String MSG_PROPERTY_FILENAME = "filename";
	public static final String MSG_PROPERTY_SRC = "src";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ACCURACY = "accuracy";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SCROLLING_ENABLED = "scrollingEnabled";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ACCESSIBILITY_HIDDEN = "accessibilityHidden";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ACCESSIBILITY_HINT = "accessibilityHint";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ACCESSIBILITY_LABEL = "accessibilityLabel";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ACCESSIBILITY_VALUE = "accessibilityValue";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ACCESSORY_TYPE = "accessoryType";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ACTION = "action";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ACTION_VIEW = "actionView";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ACTIVE_TAB = "activeTab";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ACTIVE_TITLE_COLOR = "activeTitleColor";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ACTIVE_TINT_COLOR = "activeTintColor";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SWIPEABLE = "swipeable";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SMOOTH_SCROLL_ON_TAB_CLICK = "smoothScrollOnTabClick";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ACTIVITY = "activity";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ACTIVITY_ENTER_ANIMATION = "activityEnterAnimation";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ACTIVITY_EXIT_ANIMATION = "activityExitAnimation";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ADD = "add";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ADDRESS = "address";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ALLOW_BACKGROUND = "allowBackground";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ALLOW_MULTIPLE = "allowMultiple";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ALTITUDE = "altitude";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ALTITUDE_ACCURACY = "altitudeAccuracy";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ATTRIBUTES = "attributes";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ATTRIBUTED_HINT_TEXT = "attributedHintText";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ATTRIBUTE_RANGE = "range";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ATTRIBUTED_STRING = "attributedString";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ANCHOR_POINT = "anchorPoint";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ANDROID_VIEW = "androidView";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ANIMATE = "animate";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ANIMATED = "animated";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ANNIVERSARY = "anniversary";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ANNOTATION = "annotation";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ANNOTATIONS = "annotations";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ASSISTANT = "assistant";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_AUDIO_STREAM_TYPE = "audioStreamType";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_AUDIO_TYPE = "audioType";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_AUDIO_FOCUS = "audioFocus";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_AUTO_LINK = "autoLink";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_AUTOCAPITALIZATION = "autocapitalization";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_AUTOCORRECT = "autocorrect";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_AUTOFILL_TYPE = "autofillType";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_AUTOPLAY = "autoplay";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_AUTOREVERSE = "autoreverse";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_AUTOROTATE = "autorotate";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_AUTO_REDIRECT = "autoRedirect";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_AUTO_ENCODE_URL = "autoEncodeUrl";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_AUTO_TAB_TITLE = "autoTabTitle";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BACKGROUND_COLOR = "backgroundColor";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BACKGROUND_DISABLED_COLOR = "backgroundDisabledColor";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BACKGROUND_DISABLED_IMAGE = "backgroundDisabledImage";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BACKGROUND_FOCUSED_COLOR = "backgroundFocusedColor";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BACKGROUND_FOCUSED_IMAGE = "backgroundFocusedImage";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BACKGROUND_GRADIENT = "backgroundGradient";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BACKGROUND_IMAGE = "backgroundImage";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BACKGROUND_PADDING = "backgroundPadding";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BACKGROUND_PREFIX = "background";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BACKGROUND_REPEAT = "backgroundRepeat";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BACKGROUND_SELECTED_COLOR = "backgroundSelectedColor";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BACKGROUND_SELECTED_IMAGE = "backgroundSelectedImage";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BADGE = "badge";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BADGE_COLOR = "badgeColor";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TOUCH_FEEDBACK = "touchFeedback";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TOUCH_FEEDBACK_COLOR = "touchFeedbackColor";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TRANSITION_NAME = "transitionName";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BAR_COLOR = "barColor";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BASE_URL = "baseUrl";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BASE_URL_WEBVIEW = "baseURL";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BIG_TEXT = "bigText";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BIG_CONTENT_TITLE = "bigContentTitle";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BIG_LARGE_ICON = "bigLargeIcon";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BIG_PICTURE = "bigPicture";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BIND_ID = "bindId";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BIRTHDAY = "birthday";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BLACKLISTED_URLS = "blacklistedURLs";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BLOCKED_URLS = "blockedURLs";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BORDER_COLOR = "borderColor";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BORDER_PREFIX = "border";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BORDER_RADIUS = "borderRadius";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BORDER_WIDTH = "borderWidth";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BOTTOM = "bottom";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BROTHER = "brother";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BUTTON = "button";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BUBBLE_PARENT = "bubbleParent";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BUBBLES = "bubbles";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BUTTON_NAMES = "buttonNames";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BUTTON_CLICK_REQUIRED = "buttonClickRequired";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BYTE_ORDER = "byteOrder";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_BYPASS_DND = "bypassDnd";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CACHE_MODE = "cacheMode";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CACHE_SIZE = "cacheSize";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CALENDAR_DAYS_OF_THE_MONTH = "daysOfTheMonth";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CALENDAR_DAYS_OF_THE_WEEK = "daysOfTheWeek";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CALENDAR_DAYS_OF_THE_YEAR = "daysOfTheYear";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CALENDAR_ID = "calendarID";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CALENDAR_MONTHS_OF_THE_YEAR = "monthsOfTheYear";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CALENDAR_VIEW_SHOWN = "calendarViewShown";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CALENDAR_WEEKS_OF_THE_YEAR = "weeksOfTheYear";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CAMERA_FLASH_MODE = "cameraFlashMode";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CAN_CANCEL_EVENTS = "canCancelEvents";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CANCEL = "cancel";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CANCELABLE = "cancelable";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CANCELED_ON_TOUCH_OUTSIDE = "canceledOnTouchOutside";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CAN_EDIT = "canEdit";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CAN_MOVE = "canMove";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CAN_SCROLL = "canScroll";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CASE_INSENSITIVE_SEARCH = "caseInsensitiveSearch";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CENTER = "center";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CHARSET = "charset";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CHANNEL_ID = "channelId";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CHECKABLE = "checkable";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CHECKED = "checked";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CHILD = "child";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CHILD_TEMPLATES = "childTemplates";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CITY = "city";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CLASS_NAME = "className";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CLASS_NAMES = "classNames";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CLEAR_ON_EDIT = "clearOnEdit";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CLIP_VIEWS = "clipViews";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CODE = "code";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_COLOR = "color";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_COMMENT = "comment";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CONTENT_HEIGHT = "contentHeight";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CONTENT_SIZE = "contentSize";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CONTENT_INSET_END_WITH_ACTIONS = "contentInsetEndWithActions";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CONTENT_INSET_START_WITH_NAVIGATION = "contentInsetStartWithNavigation";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CONTENT_INTENT = "contentIntent";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CONTENT_OFFSET = "contentOffset";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PADDING = "padding";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PADDING_BOTTOM = "paddingBottom";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PADDING_LEFT = "paddingLeft";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PADDING_RIGHT = "paddingRight";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PADDING_TOP = "paddingTop";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CENTER_VIEW = "centerView";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CONTENT_TEXT = "contentText";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CONTENT_TITLE = "contentTitle";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CONTENT_VIEW = "contentView";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CONTENT_WIDTH = "contentWidth";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_COORDS = "coords";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_COUNT = "count";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_COUNTRY = "country";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_COUNTRY_CODE = "countryCode";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CROP_RECT = "cropRect";

	/**
	 * @module.api
	 */ // TIMOB-4478
	public static final String PROPERTY_CURRENT_PAGE = "currentPage";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CURVE = "curve";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_DATA = "data";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_DATE = "date";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_DAY_BEFORE_MONTH = "dayBeforeMonth";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_DECODE_RETRIES = "decodeRetries";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_DESCRIPTION = "description";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_DEFAULT_IMAGE = "defaultImage";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_DEFAULT_ITEM_TEMPLATE = "defaultItemTemplate";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_DEFAULTS = "defaults";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_DELAY = "delay";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_DELETE_INTENT = "deleteIntent";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_DEST = "dest";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_DEST_POSITION = "destPosition";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_DIRECTION = "direction";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_DISABLE_CONTEXT_MENU = "disableContextMenu";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_DISPLAY_ADDRESS = "displayAddress";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_DOMAIN = "domain";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_DOMESTIC_PARTNER = "domesticPartner";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_DRAG_MARGIN = "dragMargin";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_DRAWER_INDICATOR_ENABLED = "drawerIndicatorEnabled";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_DRAWER_LOCK_MODE = "drawerLockMode";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_DURATION = "duration";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_EDITABLE = "editable";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_EDITING = "editing";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ELEVATION = "elevation";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ELLIPSIZE = "ellipsize";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_EMAIL = "email";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ENABLE_ZOOM_CONTROLS = "enableZoomControls";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ENABLED = "enabled";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ENABLE_JAVASCRIPT_INTERFACE = "enableJavascriptInterface";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ENABLE_LIGHTS = "enableLights";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ENABLE_RETURN_KEY = "enableReturnKey";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ENABLE_VIBRATION = "enableVibration";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_END = "end";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_END_PLAYBACK_TIME = "endPlaybackTime";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_EVENT = "event";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_EVENTS = "events";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_EXIT_ON_CLOSE = "exitOnClose";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_EXPIRY_DATE = "expiryDate";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_EXTEND_BACKGROUND = "extendBackground";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FAST_SCROLL = "fastScroll";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_EXTEND_SAFE_AREA = "extendSafeArea";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FATHER = "father";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FILE = "file";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FILTER_ATTRIBUTE = "filterAttribute";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FILTER_ANCHORED = "filterAnchored";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FILTER_CASE_INSENSITIVE = "filterCaseInsensitive";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FILTER_TOUCHES_WHEN_OBSCURED = "filterTouchesWhenObscured";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FIRSTNAME = "firstName";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FIRST_VISIBLE_ITEM = "firstVisibleItem";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FIRST_VISIBLE_ITEM_INDEX = "firstVisibleItemIndex";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FIRST_VISIBLE_SECTION = "firstVisibleSection";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FIRST_VISIBLE_SECTION_INDEX = "firstVisibleSectionIndex";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FLAG_SECURE = "flagSecure";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FLAGS = "flags";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FOCUSABLE = "focusable";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FONT = "font";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FONTFAMILY = "fontFamily";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FONTWEIGHT = "fontWeight";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FONTSIZE = "fontSize";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FONTSTYLE = "fontStyle";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FONT_FAMILY = "font-family";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FONT_WEIGHT = "font-weight";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FONT_SIZE = "font-size";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FOOTER_DIVIDERS_ENABLED = "footerDividersEnabled";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FOOTER = "footer";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FOOTER_TITLE = "footerTitle";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FOOTER_VIEW = "footerView";

	public static final String PROPERTY_FORWARD = "forward";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FRAGMENT_ONLY = "fragmentOnly";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_HIDDEN_BEHAVIOR = "hiddenBehavior";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_HIDES_BACK_BUTTON = "hidesBackButton";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FREQUENCY = "frequency";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FRIEND = "friend";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FROM = "from";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FULLNAME = "fullname";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FULLSCREEN = "fullscreen";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_GROUP_ID = "groupId";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_GROUP_KEY = "groupKey";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_GROUP_ALERT_BEHAVIOR = "groupAlertBehavior";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_GROUP_SUMMARY = "groupSummary";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_HAS_CHECK = "hasCheck";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_HAS_CHILD = "hasChild";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_HAS_DETAIL = "hasDetail";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_HAS_LINK = "hasLink";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_HEADER = "header";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_HEADER_DIVIDERS_ENABLED = "headerDividersEnabled";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_HEADER_TITLE = "headerTitle";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_HEADER_VIEW = "headerView";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_HEADING = "heading";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_HEADING_FILTER = "headingFilter";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_HEIGHT = "height";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_HIDE_ANNOTATION_WHEN_TOUCH_MAP = "hideAnnotationWhenTouchMap";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_HIGHLIGHTED_COLOR = "highlightedColor";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_HINT_TEXT = "hintText";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_HINT_TEXT_ID = "hinttextid";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_HINT_TEXT_COLOR = "hintTextColor";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_HINT_TYPE = "hintType";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_HOME = "home";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_HOMEPAGE = "homepage";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_HTML = "html";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_HTTP_ONLY = "httponly";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ICON = "icon";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ICON_LEVEL = "iconLevel";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ICONIFIED = "iconified";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ICONIFIED_BY_DEFAULT = "iconifiedByDefault";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ID = "id";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_IMAGE = "image";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_IMAGES = "images";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_IMPORTANCE = "importance";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_INDICATOR_COLOR = "indicatorColor";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_INDEX = "index";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_INCLUDE_FONT_PADDING = "includeFontPadding";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_INITIAL_PLAYBACK_TIME = "initialPlaybackTime";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_INPUT_TYPE = "inputType";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_INSTANTMSG = "instantMessage";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_INTENT = "intent";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_INTERVAL = "interval";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ITEM = "item";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ITEM_ID = "itemId";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ITEM_INDEX = "itemIndex";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ITEMS = "items";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_KEEP_SCREEN_ON = "keepScreenOn";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_KEYBOARD_TYPE = "keyboardType";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_KIND = "kind";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LABELS = "labels";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LABEL = "label";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LARGE_ICON = "largeIcon";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LASTNAME = "lastName";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LATITUDE = "latitude";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LATITUDE_DELTA = "latitudeDelta";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LAYOUT = "layout";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LAYOUT_ID = "layoutId";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LEFT_DRAWER_LOCK_MODE = "leftDrawerLockMode";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LINES = "lines";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LINE_SPACING = "lineSpacing";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LIFECYCLE_CONTAINER = "lifecycleContainer";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LIGHT_COLOR = "lightColor";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LOAD_URL = "loadUrl";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LOOPING = "looping";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LED_ARGB = "ledARGB";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LED_OFF_MS = "ledOffMS";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LED_ON_MS = "ledOnMS";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LEFT = "left";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LEFT_BUTTON = "leftButton";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LEFT_IMAGE = "leftImage";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LEFT_VIEW = "leftView";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LEFT_WIDTH = "leftWidth";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LENGTH = "length";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LEVEL = "level";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LOCALE = "locale";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LOCATION = "location";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LOGO = "logo";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LOCKSCREEN_VISIBILITY = "lockscreenVisibility";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LONGITUDE = "longitude";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LONGITUDE_DELTA = "longitudeDelta";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MAGNETIC_HEADING = "magneticHeading";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MANAGER = "manager";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MAP_TYPE = "mapType";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MASK = "mask";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MAX = "max";

	/**
     * @module.api
     */
	public static final String PROPERTY_MAX_AGE = "maxAge";

	/**
     * @module.api
     */
	public static final String PROPERTY_MAX_CLASSNAME = "maxClassname";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MAX_ELEVATION = "maxElevation";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MAX_LENGTH = "maxLength";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MAX_LINES = "maxLines";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MEDIA = "media";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MEDIA_CONTROL_STYLE = "mediaControlStyle";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MEDIA_TYPES = "mediaTypes";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MENU = EVENT_PROPERTY_MENU;

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MESSAGE = "message";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MESSAGEID = "messageid";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MESSENGER = "messenger";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MESSENGER_RECEIVER = "messengerReceiver";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MIDDLENAME = "middleName";
	/**
	 * @module.api
	 */
	public static final String PROPERTY_MIMETYPE = "mimeType";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MIN = "min";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MIN_AGE = "minAge";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MIN_UPDATE_DISTANCE = "minUpdateDistance";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MIN_UPDATE_TIME = "minUpdateTime";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MIN_ROW_HEIGHT = "minRowHeight";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MINIMUM_FONT_SIZE = "minimumFontSize";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MINUTE_INTERVAL = "minuteInterval";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MIXED_CONTENT_MODE = "mixedContentMode";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MOBILE = "mobile";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MODAL = "modal";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MODE = "mode";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MOTHER = "mother";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MOVABLE = "movable";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MOVING = "moving";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MULTIPLY = "multiply";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_NAME = "name";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_NATIVE_SPINNER = "nativeSpinner";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_NAVIGATION_ICON = "navigationIcon";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_NICKNAME = "nickname";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_NOTE = "note";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_NUMBER = "number";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_NUMBERING_SYSTEM = "numberingSystem";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_NUMERIC_MONTHS = "numericMonths";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_OK = "ok";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_OKID = "okid";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_OPEN = "open";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ON_BACK = "onBack";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ON_CREATE_OPTIONS_MENU = "onCreateOptionsMenu";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ON_INTENT = "onIntent";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ON_CREATE = "onCreate";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ON_START = "onStart";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ON_RESUME = "onResume";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ON_RESTART = "onRestart";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ON_PAUSE = "onPause";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ON_STOP = "onStop";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TLS_VERSION = "tlsVersion";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ON_DESTROY = "onDestroy";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ON_CREATE_WINDOW = "onCreateWindow";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ONDATASTREAM = "ondatastream";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ONERROR = "onerror";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ON_HOME_ICON_ITEM_SELECTED = "onHomeIconItemSelected";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ONLOAD = "onload";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ON_LINK = "onlink";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ON_PREPARE_OPTIONS_MENU = "onPrepareOptionsMenu";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ONREADYSTATECHANGE = "onreadystatechange";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ON_RECEIVED = "onReceived";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ONSENDSTREAM = "onsendstream";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_OPACITY = "opacity";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_OPTIONS = "options";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_OPTED_OUT = "optedOut";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ORDER = "order";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ORGANIZATION = "organization";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ORIENTATION_MODES = "orientationModes";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_OTHER = "other";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_OVER_SCROLL_MODE = "overScrollMode";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_OVERFLOW_ICON = "overflowIcon";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_OVERRIDE_CURRENT_ANIMATION = "overrideCurrentAnimation";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PACKAGE_NAME = "packageName";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PAGING_CONTROL_TIMEOUT = "pagingControlTimeout";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PARENT = "parent";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PARTNER = "partner";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PASSWORD = "password";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PASSWORD_MASK = "passwordMask";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PATH = "path";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PERSISTENT = "persistent";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PHONE = "phone";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PIN_IMAGE = "pinImage";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PINCOLOR = "pincolor";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PLACES = "places";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PLAY = "play";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PLUGIN_STATE = "pluginState";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_POSITION = "position";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_POSTAL_CODE = "postalCode";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_POWER = "power";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PREFERRED_PROVIDER = "preferredProvider";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PREVENT_CORNER_OVERLAP = "preventCornerOverlap";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PROMPT = "prompt";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PROPERTIES = "properties";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PLAYABLE_DURATION = "playableDuration";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PROVIDER = "provider";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_REFERRED_BY = "referredBy";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_REFRESH_CONTROL = "refreshControl";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_REGION = "region";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_REGION1 = "region1";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_REGION2 = "region2";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_REGION_FIT = "regionFit";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_RELATED_NAMES = "relatedNames";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_RELATIONSHIP = "relationship";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_REPEAT = "repeat";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_REPEAT_COUNT = "repeatCount";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_REPEAT_MODE = "repeatMode";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_REQUEST_HEADERS = "requestHeaders";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_RETURN_KEY_TYPE = "returnKeyType";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_REVERSE = "reverse";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_RIGHT = "right";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_RIGHT_BUTTON = "rightButton";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_RIGHT_DRAWER_LOCK_MODE = "rightDrawerLockMode";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_RIGHT_IMAGE = "rightImage";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_RIGHT_VIEW = "rightView";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_RIGHT_WIDTH = "rightWidth";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ROTATE = "rotate";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ROTATION = "rotation";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ROTATION_X = "rotationX";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ROTATION_Y = "rotationY";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ROW_DATA = "rowData";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ROW_HEIGHT = "rowHeight";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SCALE = "scale";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SCALE_X = "scaleX";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SCALE_Y = "scaleY";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SCALING_MODE = "scalingMode";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SCALES_PAGE_TO_FIT = "scalesPageToFit";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SCROLL_ENABLED = "scrollEnabled";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SCROLL_TYPE = "scrollType";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SCROLLABLE = "scrollable";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SEARCH = "search";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SEARCH_AS_CHILD = "searchAsChild";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SEARCH_TEXT = "searchText";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SEARCH_VIEW = "searchView";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SEARCHABLE_TEXT = "searchableText";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SECTION = "section";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SECTION_INDEX = "sectionIndex";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SECTIONS = "sections";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SECURE = "secure";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SELECTED_BACKGROUND_COLOR = "selectedBackgroundColor";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SELECTED_BACKGROUND_IMAGE = "selectedBackgroundImage";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SELECTED_INDEX = "selectedIndex";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SELECTION_INDICATOR = "selectionIndicator";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SELECTION_OPENS = "selectionOpens";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SEPARATOR_COLOR = "separatorColor";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SEPARATOR_HEIGHT = "separatorHeight";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SEPARATOR_STYLE = "separatorStyle";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SHADOW_COLOR = "shadowColor";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SHADOW_OFFSET = "shadowOffset";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SHADOW_RADIUS = "shadowRadius";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SHIFT_MODE = "shiftMode";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SHOW_AS_ACTION = "showAsAction";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SHOW_BADGE = "showBadge";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SHOW_CANCEL = "showCancel";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SHOWS_CONTROLS = "showsControls";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SHOW_HORIZONTAL_SCROLL_INDICATOR = "showHorizontalScrollIndicator";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SHOW_VERTICAL_SCROLL_INDICATOR = "showVerticalScrollIndicator";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SHOW_PAGING_CONTROL = "showPagingControl";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SHOW_PROGRESS = "showProgress";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SISTER = "sister";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SIZE = "size";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SOFT_KEYBOARD_ON_FOCUS = "softKeyboardOnFocus";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SOUND = "sound";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SPEED = "speed";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SOURCE = EVENT_PROPERTY_SOURCE;

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SOURCE_LENGTH = "sourceLength";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SOURCE_POSITION = "sourcePosition";

	/**
	* @module.api
	*/
	public static final String PROPERTY_SPLIT_ACTIONBAR = "splitActionBar";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SPLIT_TRACK = "splitTrack";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SPOUSE = "spouse";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_START = "start";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_STATE = "state";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_STATUS = "status";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_STOP = "stop";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_STREET = "street";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_STREET1 = "street1";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_STYLE = "style";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SUBMIT_ENABLED = "submitEnabled";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SUBTITLE = "subtitle";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SUBTITLE_TEXT_COLOR = "subtitleTextColor";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SUBTITLEID = "subtitleid";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SUCCESS = "success";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SUMMARY_TEXT = "summaryText";

	/**
	 @module.api
	 */
	public static final String PROPERTY_SUSTAINED_PERFORMANCE_MODE = "sustainedPerformanceMode";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SUPPORT_TOOLBAR = "supportToolbar";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TABS = "tabs";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TAB_OPEN = "tabOpen";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TABS_BACKGROUND_COLOR = "tabsBackgroundColor";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TABS_BACKGROUND_SELECTED_COLOR = "tabsBackgroundSelectedColor";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TAG = "tag";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TEMPLATE = "template";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TEMPLATES = "templates";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TEXT = "text";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TEXTID = "textid";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_THEME = "theme";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TEXT_ALIGN = "textAlign";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TI_PROXY = "tiProxy";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TICKER_TEXT = "tickerText";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TIME = "time";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TIMEOUT = "timeout";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TIMESTAMP = "timestamp";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TINT = "tint";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TINT_COLOR = "tintColor";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TITLE = "title";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TITLE_COLOR = "titleColor";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TITLE_CONDENSED = "titleCondensed";
	/**
	 * @module.api
	 */
	public static final String PROPERTY_TITLEID = "titleid";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TITLE_ON = "titleOn";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TITLE_OFF = "titleOff";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TITLE_PROMPT = "titlePrompt";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TITLE_PROMPTID = "titlepromptid";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TITLE_TEXT_COLOR = "titleTextColor";
	/**
	 * @module.api
	 */
	public static final String PROPERTY_TO = "to";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TOP = "top";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TOTAL_ITEM_COUNT = "totalItemCount";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TOUCH_ENABLED = "touchEnabled";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TOOLBAR = "toolbar";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TOOLBAR_ENABLED = "toolbarEnabled";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SOUND_EFFECTS_ENABLED = "soundEffectsEnabled";

	/**
	 * module.api
	 */
	public static final String PROPERTY_TRACK_TINT_COLOR = "trackTintColor";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TRANSFORM = "transform";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TRANSLATION_X = "translationX";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TRANSLATION_Y = "translationY";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TRANSLATION_Z = "translationZ";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TRANSLUCENT = "translucent";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TRUE_HEADING = "trueHeading";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_TYPE = "type";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_UPDATE_CURRENT_INTENT = "updateCurrentIntent";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_URI = "uri";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_URL = "url";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_USE_COMPAT_PADDING = "useCompatPadding";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_USE_SPINNER = "useSpinner";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_USER_AGENT = "userAgent";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_USERNAME = "username";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_USER_LOCATION = "userLocation";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_VALUE = "value";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_VERSION = "version";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_VERTICAL_ALIGN = "verticalAlign";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_VIBRATE_PATTERN = "vibratePattern";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_VIDEO_MAX_DURATION = "videoMaximumDuration";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_VIDEO_QUALITY = "videoQuality";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_VISIBLE = "visible";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_VISIBILITY = "visibility";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_VISIBLE_ITEM_COUNT = "visibleItemCount";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_VISIBLE_ITEMS = "visibleItems";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_VIEW = "view";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_VIEWS = "views";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_VOLUME = "volume";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_WAKE_LOCK = "wakeLock";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_WEBVIEW_IGNORE_SSL_ERROR = "ignoreSslError";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_WHEN = "when";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_WHICH_CAMERA = "whichCamera";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_WIDTH = "width";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_WINDOW = "window";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_WINDOW_FLAGS = "windowFlags";
	/**
	 * @module.api
	 */
	public static final String PROPERTY_WINDOW_PIXEL_FORMAT = "windowPixelFormat";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_WINDOW_SOFT_INPUT_MODE = "windowSoftInputMode";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_WORD_WRAP = "wordWrap";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_WORK = "work";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_HORIZONTAL_WRAP = "horizontalWrap";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_X = "x";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_X_ABSOLUTE = "absoluteX";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_Y = "y";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_Y_ABSOLUTE = "absoluteY";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_Z = "z";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ZINDEX = "zIndex";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ZOOM_LEVEL = "zoomLevel";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_ZOOM_ENABLED = "zoomEnabled";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LIGHT_TOUCH_ENABLED = "lightTouchEnabled";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CATEGORY = "category";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CUSTOM = "custom";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_CUSTOM_VIEW = "customView";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MIN_DATE = "minDate";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MAX_DATE = "maxDate";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PREFIX = "prefix";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_PRIORITY = "priority";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_SUFFIX = "suffix";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_FIRSTPHONETIC = "firstPhonetic";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_MIDDLEPHONETIC = "middlePhonetic";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_LASTPHONETIC = "lastPhonetic";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_JOBTITLE = "jobTitle";

	/**
	 * @module.api
	 */
	public static final String PROPERTY_DEPARTMENT = "department";

	public static final String SIZE_AUTO = "auto";
	public static final String URL_APP_PREFIX = "app://";
	public static final String URL_APP_SCHEME = "app";
	public static final String URL_APP_JS = "app://app.js";
	public static final String URL_ANDROID_ASSET = "file:///android_asset/";
	public static final String URL_ANDROID_ASSET_RESOURCES = URL_ANDROID_ASSET + "Resources/";
	public static final String PATH_APP_JS = "Resources/app.js";
}
