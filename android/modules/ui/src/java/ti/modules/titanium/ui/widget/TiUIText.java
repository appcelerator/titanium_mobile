/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2020 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget;

import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.R;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.view.TiUIView;

import ti.modules.titanium.ui.AttributedStringProxy;
import ti.modules.titanium.ui.UIModule;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.method.BaseKeyListener;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils.TruncateAt;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.text.method.KeyListener;
import android.text.method.LinkMovementMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import androidx.appcompat.view.ContextThemeWrapper;
import com.google.android.material.textfield.TextInputLayout;

public class TiUIText extends TiUIView implements TextWatcher, OnEditorActionListener, OnFocusChangeListener
{
	private static final String TAG = "TiUIText";

	private boolean field;
	private boolean disableChangeEvent = false;
	private int viewHeightInLines;
	private int maxLines = Integer.MAX_VALUE;
	private int hintTextPadding;
	private InputFilterHandler inputFilterHandler;

	protected TiUIEditText tv;
	protected TextInputLayout textInputLayout;

	public TiUIText(final TiViewProxy proxy, boolean field)
	{
		super(proxy);
		Log.d(TAG, "Creating a text field", Log.DEBUG_MODE);

		this.field = field;

		int borderStyle = UIModule.INPUT_BORDERSTYLE_FILLED;
		borderStyle = TiConvert.toInt(proxy.getProperty(TiC.PROPERTY_BORDER_STYLE), borderStyle);
		switch (borderStyle) {
			case UIModule.INPUT_BORDERSTYLE_BEZEL:
			case UIModule.INPUT_BORDERSTYLE_LINE:
			case UIModule.INPUT_BORDERSTYLE_ROUNDED:
				textInputLayout = new TextInputLayout(new ContextThemeWrapper(
					proxy.getActivity(), R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox));
				textInputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
				textInputLayout.setBoxBackgroundColor(Color.TRANSPARENT);
				if (borderStyle == UIModule.INPUT_BORDERSTYLE_ROUNDED) {
					float radius = (new TiDimension("5dp", TiDimension.TYPE_LEFT)).getAsPixels(textInputLayout);
					textInputLayout.setBoxCornerRadii(radius, radius, radius, radius);
				} else {
					textInputLayout.setBoxCornerRadii(0, 0, 0, 0);
				}
				break;
			case UIModule.INPUT_BORDERSTYLE_NONE:
			case UIModule.INPUT_BORDERSTYLE_UNDERLINED:
				textInputLayout = new TextInputLayout(proxy.getActivity());
				textInputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_NONE);
				break;
			case UIModule.INPUT_BORDERSTYLE_FILLED:
			default:
				textInputLayout = new TextInputLayout(proxy.getActivity());
				textInputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_FILLED);
				break;
		}

		if (textInputLayout.getBoxBackgroundMode() != TextInputLayout.BOX_BACKGROUND_NONE) {
			this.tv = new TiUIEditText(textInputLayout.getContext());
		} else {
			this.tv = new TiUIEditText(proxy.getActivity());
			if (borderStyle == UIModule.INPUT_BORDERSTYLE_NONE) {
				this.tv.setBackground(null);
			}
		}
		this.hintTextPadding = this.tv.getPaddingTop();
		this.inputFilterHandler = new InputFilterHandler(this.tv);
		this.tv.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
			@Override
			public void onLayoutChange(
				View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom)
			{
				TiUIHelper.firePostLayoutEvent(proxy);
			}
		});
		if (field) {
			this.tv.setSingleLine();
			this.tv.setMaxLines(1);
		}
		registerForTouch(this.tv);
		this.tv.addTextChangedListener(this);
		this.tv.setOnEditorActionListener(this);
		this.tv.setOnFocusChangeListener(this);
		this.tv.setIncludeFontPadding(true);
		if (field) {
			this.tv.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
		} else {
			this.tv.setGravity(Gravity.TOP | Gravity.START);
		}
		textInputLayout.addView(this.tv, new TextInputLayout.LayoutParams(
			TextInputLayout.LayoutParams.MATCH_PARENT, TextInputLayout.LayoutParams.MATCH_PARENT));

		setNativeView(textInputLayout);
	}

	@Override
	public void processProperties(KrollDict d)
	{
		super.processProperties(d);

		if (d.containsKey(TiC.PROPERTY_AUTOFILL_TYPE) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			tv.setAutofillHints(d.getString(TiC.PROPERTY_AUTOFILL_TYPE));
		}

		if (d.containsKey(TiC.PROPERTY_ENABLED)) {
			tv.setEnabled(TiConvert.toBoolean(d, TiC.PROPERTY_ENABLED, true));
		}

		if (d.containsKey(TiC.PROPERTY_ENABLE_COPY)) {
			tv.setIsCopyEnabled(TiConvert.toBoolean(d, TiC.PROPERTY_ENABLE_COPY, true));
		}

		this.inputFilterHandler.setMaxLength(TiConvert.toInt(d.get(TiC.PROPERTY_MAX_LENGTH), -1));

		// Disable change event temporarily as we are setting the default value
		disableChangeEvent = true;

		tv.setText(TiConvert.toString(d.get(TiC.PROPERTY_VALUE), ""));

		if (d.containsKey(TiC.PROPERTY_BACKGROUND_COLOR)) {
			tv.setBackgroundColor(Color.TRANSPARENT);
		}

		if (d.containsKey(TiC.PROPERTY_COLOR)) {
			tv.setTextColor(TiConvert.toColor(d, TiC.PROPERTY_COLOR));
		}

		if (d.containsKey(TiC.PROPERTY_HINT_TEXT) || d.containsKey(TiC.PROPERTY_HINT_TYPE)) {
			String hintText = TiConvert.toString(d.get(TiC.PROPERTY_HINT_TEXT), "");
			if (hintText != null) {
				int type = TiConvert.toInt(d.get(TiC.PROPERTY_HINT_TYPE), UIModule.HINT_TYPE_STATIC);
				setHintText(type, hintText);
			}
		}

		if (d.containsKey(TiC.PROPERTY_HINT_TEXT_COLOR)) {
			tv.setHintTextColor(TiConvert.toColor(d, TiC.PROPERTY_HINT_TEXT_COLOR));
		}

		if (d.containsKey(TiC.PROPERTY_ELLIPSIZE)) {
			if (TiConvert.toBoolean(d, TiC.PROPERTY_ELLIPSIZE)) {
				tv.setEllipsize(TruncateAt.END);
			} else {
				tv.setEllipsize(null);
			}
		}

		if (d.containsKey(TiC.PROPERTY_FONT)) {
			TiUIHelper.styleText(tv, d.getKrollDict(TiC.PROPERTY_FONT));
		}

		if (d.containsKey(TiC.PROPERTY_TEXT_ALIGN) || d.containsKey(TiC.PROPERTY_VERTICAL_ALIGN)) {
			String textAlign = null;
			String verticalAlign = null;
			if (d.containsKey(TiC.PROPERTY_TEXT_ALIGN)) {
				textAlign = d.getString(TiC.PROPERTY_TEXT_ALIGN);
			}
			if (d.containsKey(TiC.PROPERTY_VERTICAL_ALIGN)) {
				verticalAlign = d.getString(TiC.PROPERTY_VERTICAL_ALIGN);
			}
			handleTextAlign(textAlign, verticalAlign);
		}

		if (d.containsKey(TiC.PROPERTY_RETURN_KEY_TYPE)) {
			handleReturnKeyType(TiConvert.toInt(d.get(TiC.PROPERTY_RETURN_KEY_TYPE), UIModule.RETURNKEY_DEFAULT));
		}

		if (d.containsKey(TiC.PROPERTY_KEYBOARD_TYPE) || d.containsKey(TiC.PROPERTY_AUTOCORRECT)
			|| d.containsKey(TiC.PROPERTY_PASSWORD_MASK) || d.containsKey(TiC.PROPERTY_AUTOCAPITALIZATION)
			|| d.containsKey(TiC.PROPERTY_EDITABLE) || d.containsKey(TiC.PROPERTY_INPUT_TYPE)
			|| d.containsKey(TiC.PROPERTY_FULLSCREEN)) {
			handleKeyboard(d);
		}

		if (d.containsKey(TiC.PROPERTY_ATTRIBUTED_HINT_TEXT)) {
			Object attributedString = d.get(TiC.PROPERTY_ATTRIBUTED_HINT_TEXT);
			if (attributedString instanceof AttributedStringProxy) {
				setAttributedStringHint((AttributedStringProxy) attributedString);
			}
		}

		if (d.containsKey(TiC.PROPERTY_ATTRIBUTED_STRING)) {
			Object attributedString = d.get(TiC.PROPERTY_ATTRIBUTED_STRING);
			if (attributedString instanceof AttributedStringProxy) {
				setAttributedStringText((AttributedStringProxy) attributedString);
			}
		}

		if (d.containsKey(TiC.PROPERTY_AUTO_LINK)) {
			TiUIHelper.linkifyIfEnabled(tv, d.get(TiC.PROPERTY_AUTO_LINK));
		}

		if (d.containsKey(TiC.PROPERTY_PADDING)) {
			setTextPadding((HashMap) d.get(TiC.PROPERTY_PADDING));
		}

		if (d.containsKey(TiC.PROPERTY_LINES)) {
			if (!field) {
				this.viewHeightInLines = TiConvert.toInt(d.get(TiC.PROPERTY_LINES), 0);
				updateTextField();
			}
		}

		if (d.containsKey(TiC.PROPERTY_MAX_LINES)) {
			if (!field) {
				int value = TiConvert.toInt(d.get(TiC.PROPERTY_MAX_LINES), Integer.MAX_VALUE);
				if (value < 1) {
					value = Integer.MAX_VALUE;
				}
				this.maxLines = value;
				updateTextField();
			}
		}

		// Update virtual keyboard to use view's current IME settings.
		restartInputMethodManager();

		disableChangeEvent = false;
	}

	@Override
	protected void applyContentDescription()
	{
		if (proxy == null || nativeView == null) {
			return;
		}
		String contentDescription = composeContentDescription();
		if (contentDescription != null) {
			this.tv.setContentDescription(contentDescription);
		}
	}

	@Override
	protected void applyContentDescription(KrollDict properties)
	{
		if (proxy == null || nativeView == null) {
			return;
		}
		String contentDescription = composeContentDescription(properties);
		if (contentDescription != null) {
			this.tv.setContentDescription(contentDescription);
		}
	}

	private void updateTextField()
	{
		if (!field) {
			if (this.viewHeightInLines > 0) {
				tv.setLines(this.viewHeightInLines);
			} else {
				tv.setMinLines(0);
			}
			tv.setMaxLines((this.maxLines > 0) ? this.maxLines : 1);
		}
	}

	private void setTextPadding(HashMap<String, Object> d)
	{
		int paddingLeft = textInputLayout.getPaddingLeft();
		int paddingRight = textInputLayout.getPaddingRight();
		int paddingTop = textInputLayout.getPaddingTop();
		int paddingBottom = textInputLayout.getPaddingBottom();

		if (d.containsKey(TiC.PROPERTY_LEFT)) {
			paddingLeft = TiConvert.toInt(d.get(TiC.PROPERTY_LEFT), 0);
		}

		if (d.containsKey(TiC.PROPERTY_RIGHT)) {
			paddingRight = TiConvert.toInt(d.get(TiC.PROPERTY_RIGHT), 0);
		}

		if (d.containsKey(TiC.PROPERTY_TOP)) {
			paddingTop = TiConvert.toInt(d.get(TiC.PROPERTY_TOP), 0);
		}

		if (d.containsKey(TiC.PROPERTY_BOTTOM)) {
			paddingBottom = TiConvert.toInt(d.get(TiC.PROPERTY_BOTTOM), 0);
		}

		textInputLayout.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
	}

	@Override
	public void propertyChanged(String key, Object oldValue, Object newValue, KrollProxy proxy)
	{
		if (Log.isDebugModeEnabled()) {
			Log.d(TAG, "Property: " + key + " old: " + oldValue + " new: " + newValue, Log.DEBUG_MODE);
		}
		if (key.equals(TiC.PROPERTY_AUTOFILL_TYPE) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			tv.setAutofillHints(TiConvert.toString(newValue));
		} else if (key.equals(TiC.PROPERTY_ENABLED)) {
			tv.setEnabled(TiConvert.toBoolean(newValue));
		} else if (key.equals(TiC.PROPERTY_ENABLE_COPY)) {
			tv.setIsCopyEnabled(TiConvert.toBoolean(newValue));
		} else if (key.equals(TiC.PROPERTY_VALUE)) {
			this.disableChangeEvent = true;
			tv.setText(TiConvert.toString(newValue, ""));
			this.disableChangeEvent = false;
		} else if (key.equals(TiC.PROPERTY_MAX_LENGTH)) {
			this.inputFilterHandler.setMaxLength(TiConvert.toInt(newValue, -1));
		} else if (key.equals(TiC.PROPERTY_BACKGROUND_COLOR)) {
			tv.setBackgroundColor(Color.TRANSPARENT);
			super.propertyChanged(key, oldValue, newValue, proxy);
		} else if (key.equals(TiC.PROPERTY_COLOR)) {
			tv.setTextColor(TiConvert.toColor((String) newValue));
		} else if (key.equals(TiC.PROPERTY_HINT_TEXT)) {
			int type = UIModule.HINT_TYPE_STATIC;
			if (proxy.getProperties() != null) {
				type = TiConvert.toInt(proxy.getProperties().get(TiC.PROPERTY_HINT_TYPE), type);
			}
			setHintText(type, TiConvert.toString(newValue));
		} else if (key.equals(TiC.PROPERTY_HINT_TEXT_COLOR)) {
			tv.setHintTextColor(TiConvert.toColor((String) newValue));
		} else if (key.equals(TiC.PROPERTY_HINT_TYPE)) {
			Object attributedHintText = proxy.getProperty(TiC.PROPERTY_ATTRIBUTED_HINT_TEXT);
			if (attributedHintText instanceof AttributedStringProxy) {
				setAttributedStringHint((AttributedStringProxy) attributedHintText);
			} else {
				String hintText = TiConvert.toString(proxy.getProperty(TiC.PROPERTY_HINT_TEXT));
				if (hintText != null) {
					int type = TiConvert.toInt(newValue);
					setHintText(type, hintText);
				}
			}
		} else if (key.equals(TiC.PROPERTY_ELLIPSIZE)) {
			if (TiConvert.toBoolean(newValue)) {
				tv.setEllipsize(TruncateAt.END);
			} else {
				tv.setEllipsize(null);
			}
		} else if (key.equals(TiC.PROPERTY_TEXT_ALIGN) || key.equals(TiC.PROPERTY_VERTICAL_ALIGN)) {
			String textAlign = null;
			String verticalAlign = null;
			if (key.equals(TiC.PROPERTY_TEXT_ALIGN)) {
				textAlign = TiConvert.toString(newValue);
			} else if (proxy.hasProperty(TiC.PROPERTY_TEXT_ALIGN)) {
				textAlign = TiConvert.toString(proxy.getProperty(TiC.PROPERTY_TEXT_ALIGN));
			}
			if (key.equals(TiC.PROPERTY_VERTICAL_ALIGN)) {
				verticalAlign = TiConvert.toString(newValue);
			} else if (proxy.hasProperty(TiC.PROPERTY_VERTICAL_ALIGN)) {
				verticalAlign = TiConvert.toString(proxy.getProperty(TiC.PROPERTY_VERTICAL_ALIGN));
			}
			handleTextAlign(textAlign, verticalAlign);
		} else if (key.equals(TiC.PROPERTY_KEYBOARD_TYPE) || (key.equals(TiC.PROPERTY_INPUT_TYPE))
				   || (key.equals(TiC.PROPERTY_AUTOCORRECT) || key.equals(TiC.PROPERTY_AUTOCAPITALIZATION)
				   || key.equals(TiC.PROPERTY_PASSWORD_MASK) || key.equals(TiC.PROPERTY_EDITABLE))) {
			handleKeyboard(proxy.getProperties());
			restartInputMethodManager();
		} else if (key.equals(TiC.PROPERTY_RETURN_KEY_TYPE)) {
			handleReturnKeyType(TiConvert.toInt(newValue));
			restartInputMethodManager();
		} else if (key.equals(TiC.PROPERTY_FONT)) {
			TiUIHelper.styleText(tv, (HashMap) newValue);
		} else if (key.equals(TiC.PROPERTY_AUTO_LINK)) {
			TiUIHelper.linkifyIfEnabled(tv, newValue);
		} else if (key.equals(TiC.PROPERTY_ATTRIBUTED_HINT_TEXT) && newValue instanceof AttributedStringProxy) {
			setAttributedStringHint((AttributedStringProxy) newValue);
		} else if (key.equals(TiC.PROPERTY_ATTRIBUTED_STRING) && newValue instanceof AttributedStringProxy) {
			setAttributedStringText((AttributedStringProxy) newValue);
		} else if (key.equals(TiC.PROPERTY_PADDING)) {
			setTextPadding((HashMap) newValue);
		} else if (key.equals(TiC.PROPERTY_FULLSCREEN)) {
			handleFullscreen(proxy.getProperties());
			restartInputMethodManager();
		} else if (key.equals(TiC.PROPERTY_LINES)) {
			if (!field) {
				this.viewHeightInLines = TiConvert.toInt(newValue, 0);
				updateTextField();
			}
		} else if (key.equals(TiC.PROPERTY_MAX_LINES)) {
			if (!field) {
				int value = TiConvert.toInt(newValue, Integer.MAX_VALUE);
				if (value < 1) {
					value = Integer.MAX_VALUE;
				}
				this.maxLines = value;
				updateTextField();
			}
		} else {
			super.propertyChanged(key, oldValue, newValue, proxy);
		}
	}

	@Override
	public void afterTextChanged(Editable editable)
	{
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int before, int count)
	{
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count)
	{
		//Since Jelly Bean, pressing the 'return' key won't trigger onEditorAction callback
		//http://stackoverflow.com/questions/11311790/oneditoraction-is-not-called-after-enter-key-has-been-pressed-on-jelly-bean-em
		//So here we need to handle the 'return' key manually
		if (before == 0 && s.length() > start && s.charAt(start) == '\n') {
			//We use the previous value to make it consistent with pre Jelly Bean behavior (onEditorAction is called before
			//onTextChanged.
			String value = TiConvert.toString(proxy.getProperty(TiC.PROPERTY_VALUE));
			KrollDict data = new KrollDict();
			data.put(TiC.PROPERTY_VALUE, value);
			// TODO: Enable this once we have it on iOS as well.
			//data.put(TiC.PROPERTY_BUTTON, RETURN_KEY_TYPE_NEW_LINE);
			fireEvent(TiC.EVENT_RETURN, data);
		}

		// Fire change events, but only if it's coming from the end-user (ignore programmatic text changes).
		if (!disableChangeEvent) {
			// Fire a text "change" event.
			String newText = tv.getText().toString();
			if (proxy.shouldFireChange(proxy.getProperty(TiC.PROPERTY_VALUE), newText)) {
				KrollDict data = new KrollDict();
				data.put(TiC.PROPERTY_VALUE, newText);
				proxy.setProperty(TiC.PROPERTY_VALUE, newText);
				fireEvent(TiC.EVENT_CHANGE, data);
			}

			// Fire an app "userinteraction" event when the end-user is typing on the keyboard.
			// Note: The Activity.onUserInteraction() method does not get called in this case.
			TiApplication.getInstance().fireAppEvent(TiC.EVENT_USER_INTERACTION, null);
		}
	}

	@Override
	public void focus()
	{
		super.focus();
		if (tv != null && proxy != null && proxy.getProperties() != null) {
			final boolean editable = proxy.getProperties().optBoolean(TiC.PROPERTY_EDITABLE, true);
			TiUIHelper.showSoftKeyboard(tv, editable);
		}
	}

	@Override
	public void blur()
	{
		View rootView = tv.getRootView();
		if (rootView != null) {
			//set rootView to focus and hide keyboard
			rootView.setFocusable(true);
			rootView.setFocusableInTouchMode(true);
			if (rootView instanceof ViewGroup) {
				((ViewGroup) rootView).setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
			}
			rootView.requestFocus();
			tv.clearFocus();
			Context context = TiApplication.getInstance().getApplicationContext();
			InputMethodManager inputManager =
				(InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
			inputManager.hideSoftInputFromWindow(tv.getWindowToken(), 0);
		}
	}

	@Override
	public void onFocusChange(View v, boolean hasFocus)
	{
		if (hasFocus) {
			Boolean clearOnEdit = (Boolean) proxy.getProperty(TiC.PROPERTY_CLEAR_ON_EDIT);
			if (clearOnEdit != null && clearOnEdit) {
				tv.setText("");
			}
			Rect r = new Rect();
			tv.getFocusedRect(r);
			tv.requestRectangleOnScreen(r);
		}
		super.onFocusChange(v, hasFocus);
	}

	@Override
	protected KrollDict getFocusEventObject(boolean hasFocus)
	{
		KrollDict event = new KrollDict();
		event.put(TiC.PROPERTY_VALUE, tv.getText().toString());
		return event;
	}

	@Override
	public boolean onEditorAction(TextView v, int actionId, KeyEvent keyEvent)
	{
		// TIMOB-23757: https://code.google.com/p/android/issues/detail?id=182191
		if (Build.VERSION.SDK_INT < 24 && (tv.getGravity() & Gravity.START) != Gravity.START) {
			if (getNativeView() != null) {
				ViewGroup view = (ViewGroup) getNativeView().getParent();
				view.setFocusableInTouchMode(true);
				view.requestFocus();
			}
			Context context = TiApplication.getInstance().getApplicationContext();
			InputMethodManager inputManager =
				(InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
			inputManager.hideSoftInputFromWindow(tv.getWindowToken(), 0);
		}

		String value = tv.getText().toString();
		KrollDict data = new KrollDict();
		data.put(TiC.PROPERTY_VALUE, value);

		proxy.setProperty(TiC.PROPERTY_VALUE, value);
		Log.d(TAG, "ActionID: " + actionId + " KeyEvent: " + (keyEvent != null ? keyEvent.getKeyCode() : null),
			  Log.DEBUG_MODE);

		boolean enableReturnKey = false;
		if (proxy.hasProperty(TiC.PROPERTY_ENABLE_RETURN_KEY)) {
			enableReturnKey = TiConvert.toBoolean(proxy.getProperty(TiC.PROPERTY_ENABLE_RETURN_KEY), false);
		}
		if (enableReturnKey && v.getText().length() == 0) {
			return true;
		}

		// TODO: Enable this once we have it on iOS as well.
		//data.put(TiC.PROPERTY_BUTTON, RETURN_KEY_TYPE_ACTION);

		// Check whether we are dealing with text area or text field. Multiline TextViews in Landscape
		// orientation for phones have separate buttons for IME_ACTION and new line.
		// And because of that we skip the firing of a RETURN event from this call in favor of the
		// one from onTextChanged. The event carries a property to determine whether it was fired
		// from the IME_ACTION button or the new line one.
		if (this.field) {
			fireEvent(TiC.EVENT_RETURN, data);
			// Since IME_ACTION_NEXT and IME_ACTION_DONE take care of consuming the second call to
			// onEditorAction we do not consume it for either of them.
			return (!(actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE
					  || (keyEvent != null)));
		} else {
			// After clicking the IME_ACTION button we get two calls of onEditorAction.
			// The second call of onEditorAction is treated as a KeyPress event and gives the
			// keyEvent for that as the third parameter. If it is 'null' that's the first call -
			// fire the JS event and consume the event to prevent the duplicate call.
			if (keyEvent == null) {
				fireEvent(TiC.EVENT_RETURN, data);
				return true;
			}
			// New line is treated immediately as KeyEvent, so we let the system propagate it
			// to onTextChange where the JS event is loaded with the property that with was a new line.
			return false;
		}
	}

	public void handleTextAlign(String textAlign, String verticalAlign)
	{
		if (verticalAlign == null) {
			verticalAlign = field ? "middle" : "top";
		}
		if (textAlign == null) {
			textAlign = "left";
		}
		TiUIHelper.setAlignment(tv, textAlign, verticalAlign);
	}

	public void handleKeyboard(KrollDict d)
	{
		// Temporarily disable "change" events while re-configuring the view below.
		boolean wasChangeEventDisabled = this.disableChangeEvent;
		this.disableChangeEvent = true;

		// Initialize input type flags based on keyboard type.
		int inputTypeFlags;
		KeyListener keyListener = null;
		boolean useEmojiFilter = false;
		boolean useImeFlagForceAscii = false;
		int keyboardType = TiConvert.toInt(d.get(TiC.PROPERTY_KEYBOARD_TYPE), UIModule.KEYBOARD_TYPE_DEFAULT);
		switch (keyboardType) {
			case UIModule.KEYBOARD_TYPE_DEFAULT:
				// Use a normal text keyboard.
				inputTypeFlags = InputType.TYPE_CLASS_TEXT;
				break;
			case UIModule.KEYBOARD_TYPE_NUMBERS_PUNCTUATION:
				// Use a normal text keyboard, but don't allow emoji. (This matches iOS' behavior.)
				inputTypeFlags = InputType.TYPE_CLASS_TEXT;
				useEmojiFilter = true;
				break;
			case UIModule.KEYBOARD_TYPE_ASCII:
				// Default to English keyboard layout, regardless of OS language settings.
				// Allow all chars, including non-English chars, except emoji. (This matches iOS' behavior.)
				inputTypeFlags = InputType.TYPE_CLASS_TEXT;
				useEmojiFilter = true;
				useImeFlagForceAscii = true;
				break;
			case UIModule.KEYBOARD_TYPE_URL:
				// Use keyboard with URL keys.
				inputTypeFlags = InputType.TYPE_CLASS_TEXT;
				inputTypeFlags |= InputType.TYPE_TEXT_VARIATION_URI;
				break;
			case UIModule.KEYBOARD_TYPE_DECIMAL_PAD:
				// Only accepts numbers, sign character, and decimal separator.
				inputTypeFlags = InputType.TYPE_CLASS_NUMBER;
				inputTypeFlags |= InputType.TYPE_NUMBER_FLAG_DECIMAL;
				inputTypeFlags |= InputType.TYPE_NUMBER_FLAG_SIGNED;

				// Use our custom key listener to work-around localization bugs on Google's end.
				// Note: Google made a partial fix for regions using ',' for decimal separator in API Level 26,
				//       but it doesn't work in fullscreen mode. It also doesn't fix Arabic decimal handling.
				keyListener = new TiUIText.DecimalPadKeyListener();
				break;
			case UIModule.KEYBOARD_TYPE_NUMBER_PAD:
				// Only numbers are allowed. No decimal separator or sign character.
				inputTypeFlags = InputType.TYPE_CLASS_NUMBER;
				break;
			case UIModule.KEYBOARD_TYPE_PHONE_PAD:
				// Use phone keypad.
				inputTypeFlags = InputType.TYPE_CLASS_PHONE;
				break;
			case UIModule.KEYBOARD_TYPE_EMAIL:
				// Use keyboard with e-mail related keys.
				inputTypeFlags = InputType.TYPE_CLASS_TEXT;
				inputTypeFlags |= InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
				break;
			default:
				inputTypeFlags = InputType.TYPE_CLASS_TEXT;
				break;
		}

		// Enable/disable an emoji character filter.
		this.inputFilterHandler.setEmojiFilterEnabled(useEmojiFilter);

		// Enable auto-correction if configured.
		if (TiConvert.toBoolean(d.get(TiC.PROPERTY_AUTOCORRECT), true)) {
			inputTypeFlags |= InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
		}

		// Enable auto-capitalization if configured.
		int autoCapType = TiConvert.toInt(
			d.get(TiC.PROPERTY_AUTOCAPITALIZATION), UIModule.TEXT_AUTOCAPITALIZATION_NONE);
		switch (autoCapType) {
			case UIModule.TEXT_AUTOCAPITALIZATION_NONE:
				break;
			case UIModule.TEXT_AUTOCAPITALIZATION_ALL:
				inputTypeFlags |= InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;
				break;
			case UIModule.TEXT_AUTOCAPITALIZATION_SENTENCES:
				inputTypeFlags |= InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
				break;
			case UIModule.TEXT_AUTOCAPITALIZATION_WORDS:
				inputTypeFlags |= InputType.TYPE_TEXT_FLAG_CAP_WORDS;
				break;
			default: {
				String message
					= "Unknown '" + TiC.PROPERTY_AUTOCAPITALIZATION + "' Value: "
					+ d.getString(TiC.PROPERTY_AUTOCAPITALIZATION);
				Log.w(TAG, message);
				break;
			}
		}
		this.inputFilterHandler.setAllCapsEnabled((inputTypeFlags & InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0);

		// If "inputType" property is set, then let it override all above input type settings.
		if (d.containsKey(TiC.PROPERTY_INPUT_TYPE)) {
			int[] flagArray = null;
			Object obj = d.get(TiC.PROPERTY_INPUT_TYPE);
			if (obj instanceof Object[]) {
				flagArray = TiConvert.toIntArray((Object[]) obj);
			}
			if (flagArray != null) {
				keyListener = null;
				inputTypeFlags = 0;
				for (int nextFlag : flagArray) {
					inputTypeFlags |= nextFlag;
				}
			}
		}

		// Add password masking flag if configured.
		Typeface originalTypeface = this.tv.getTypeface();
		boolean passwordMask = TiConvert.toBoolean(d.get(TiC.PROPERTY_PASSWORD_MASK), false);
		if (passwordMask) {
			boolean isNumeric = ((inputTypeFlags & InputType.TYPE_CLASS_NUMBER) != 0);
			boolean hasNumericSymbols = ((inputTypeFlags & InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0);
			hasNumericSymbols |= ((inputTypeFlags & InputType.TYPE_NUMBER_FLAG_SIGNED) != 0);
			if (isNumeric && !hasNumericSymbols) {
				// Numeric password keyboard only supports numbers. No decimal points or minus signs.
				// This also supports fullscreen numeric password entry in landscape.
				inputTypeFlags |= InputType.TYPE_NUMBER_VARIATION_PASSWORD;
			} else {
				// Use text based password keyboard for all other input types.
				inputTypeFlags |= InputType.TYPE_TEXT_VARIATION_PASSWORD;
			}
		}

		// Make the view read-only if configured.
		// Note: Setting "canShowSoftKeyboard" false is intended for apps which have their own custom keyboard UI.
		int keyboardFocusBehaviorType =
			TiConvert.toInt(d.get(TiC.PROPERTY_SOFT_KEYBOARD_ON_FOCUS), TiUIView.SOFT_KEYBOARD_DEFAULT_ON_FOCUS);
		boolean canShowSoftKeyboard = (keyboardFocusBehaviorType != TiUIView.SOFT_KEYBOARD_HIDE_ON_FOCUS);
		boolean isEditable = TiConvert.toBoolean(d.get(TiC.PROPERTY_EDITABLE), true);
		if (!isEditable || !canShowSoftKeyboard) {
			// Disable virtual keyboard. (This does not disable physical keyboard input.)
			inputTypeFlags = InputType.TYPE_NULL;
			keyListener = null;

			// Disable all key input, including physical keyboard, if not editable. (This makes it read-only.)
			if (!isEditable) {
				keyListener = new TiUIText.DisabledKeyListener();
			}
		}
		this.tv.setCursorVisible(isEditable);
		this.tv.setTextIsSelectable(!isEditable);

		// Apply the above configured key listener and input type flags.
		// Note: The setInputType() method internally calls setKeyListener(). Can only use one or the other.
		if (keyListener != null) {
			this.tv.setKeyListener(keyListener);
			this.tv.setRawInputType(inputTypeFlags);  // Add flags to key listener.
		} else {
			this.tv.setInputType(inputTypeFlags);
		}

		// Finish setting up password handling.
		// Must be done after calling setInputType() or setKeyListener().
		if (passwordMask) {
			// Make sure EditText is still using our originally configured font.
			// Note: The setInputType() method changes font to monospace when applying password flag.
			this.tv.setTypeface(originalTypeface);

			// Password transformation must be applied after setInputType() has been called.
			this.tv.setTransformationMethod(PasswordTransformationMethod.getInstance());
		} else {
			// Remove password transformation if previous configured.
			if (this.tv.getTransformationMethod() instanceof PasswordTransformationMethod) {
				this.tv.setTransformationMethod(null);
			}
		}

		// Update fullscreen edit handling.
		// We might have to diable it due to Google bugs with password handling of certain input types.
		handleFullscreen(d);

		// Force keyboard to use English if enabled. (Not all keyboards honor this setting.)
		if (useImeFlagForceAscii) {
			this.tv.setImeOptions(this.tv.getImeOptions() | EditorInfo.IME_FLAG_FORCE_ASCII);
		} else {
			this.tv.setImeOptions(this.tv.getImeOptions() & ~EditorInfo.IME_FLAG_FORCE_ASCII);
		}

		// Make the EditText multiline if this is a Titanium TextArea view. (ie: Not a Titanium TextField.)
		// Note: This applies flag TYPE_TEXT_FLAG_MULTI_LINE to configured input type. Must be done last.
		if (!this.field) {
			this.tv.setSingleLine(false);
		}

		// Restore "change" events.
		this.disableChangeEvent = wasChangeEventDisabled;
	}

	private void handleFullscreen(KrollDict properties)
	{
		// Validate.
		if (properties == null) {
			return;
		}

		// Fetch current settings.
		int inputTypeFlags = this.tv.getInputType();
		boolean isFullscreenEnabled = TiConvert.toBoolean(properties.get(TiC.PROPERTY_FULLSCREEN), true);
		boolean hasPasswordMask = TiConvert.toBoolean(properties.get(TiC.PROPERTY_PASSWORD_MASK), false);

		// Always disable fullscreen edit mode for input settings that have known bugs on Google's end.
		// Most common issue is password masking doesn't work in fullscreen for particular input flags.
		boolean hasFullscreenIssue = false;
		switch (inputTypeFlags & InputType.TYPE_MASK_CLASS) {
			case InputType.TYPE_CLASS_NUMBER:
				if ((inputTypeFlags & (InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED)) != 0) {
					if (hasPasswordMask) {
						hasFullscreenIssue = true;
					} else if (DecimalFormatSymbols.getInstance(Locale.getDefault()).getDecimalSeparator() != '.') {
						hasFullscreenIssue = true;  // Fullscreen keyboard only supports '.' for decimal separator.
					}
				}
				break;
			case InputType.TYPE_CLASS_PHONE:
				hasFullscreenIssue = hasPasswordMask;
				break;
			case InputType.TYPE_CLASS_TEXT:
				if (hasPasswordMask) {
					hasFullscreenIssue |= ((inputTypeFlags & InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) != 0);
					hasFullscreenIssue |= ((inputTypeFlags & InputType.TYPE_TEXT_VARIATION_URI) != 0);
				}
				break;
		}
		if (hasFullscreenIssue) {
			isFullscreenEnabled = false;
		}

		// Enable/disable fullscreen edit mode.
		final int NO_FULLSCREEN_FLAGS = EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN;
		int imeFlags = this.tv.getImeOptions();
		if (isFullscreenEnabled) {
			imeFlags &= ~NO_FULLSCREEN_FLAGS;
		} else {
			imeFlags |= NO_FULLSCREEN_FLAGS;
		}
		this.tv.setImeOptions(imeFlags);
	}

	public void setSelection(int start, int end)
	{
		// Validate arguments.
		int textLength = tv.length();
		if (start < 0 || start > textLength || end < 0 || end > textLength) {
			Log.w(TAG, "Invalid range for text selection. Ignoring.");
			return;
		}

		// Do not continue if selection isn't changing. (This is an optimization.)
		if ((start == tv.getSelectionStart()) && (end == tv.getSelectionEnd())) {
			return;
		}

		// This works-around an Android 4.x bug where the "end" index will be ignored
		// if setSelection() is called just after tapping the text field. (See: TIMOB-19639)
		Editable text = tv.getText();
		if (text.length() > 0) {
			boolean wasDisabled = this.disableChangeEvent;
			this.disableChangeEvent = true;
			text.replace(0, 1, text.subSequence(0, 1), 0, 1);
			this.disableChangeEvent = wasDisabled;
		}

		// Change the cursor position and text selection.
		tv.setSelection(start, end);
	}

	public KrollDict getSelection()
	{
		KrollDict result = new KrollDict(2);
		int start = tv.getSelectionStart();
		result.put(TiC.PROPERTY_LOCATION, start);
		if (start != -1) {
			int end = tv.getSelectionEnd();
			result.put(TiC.PROPERTY_LENGTH, end - start);
		} else {
			result.put(TiC.PROPERTY_LENGTH, -1);
		}

		return result;
	}

	public void handleReturnKeyType(int type)
	{
		int imeActionFlag = 0;
		switch (type) {
			case UIModule.RETURNKEY_DEFAULT:
				imeActionFlag = EditorInfo.IME_ACTION_UNSPECIFIED;
				break;
			case UIModule.RETURNKEY_DONE:
			case UIModule.RETURNKEY_JOIN:
			case UIModule.RETURNKEY_ROUTE:
				imeActionFlag = EditorInfo.IME_ACTION_DONE;
				break;
			case UIModule.RETURNKEY_EMERGENCY_CALL:
			case UIModule.RETURNKEY_GO:
			case UIModule.RETURNKEY_GOOGLE:
			case UIModule.RETURNKEY_YAHOO:
				imeActionFlag = EditorInfo.IME_ACTION_GO;
				break;
			case UIModule.RETURNKEY_NEXT:
				imeActionFlag = EditorInfo.IME_ACTION_NEXT;
				break;
			case UIModule.RETURNKEY_SEARCH:
				imeActionFlag = EditorInfo.IME_ACTION_SEARCH;
				break;
			case UIModule.RETURNKEY_SEND:
				imeActionFlag = EditorInfo.IME_ACTION_SEND;
				break;
			default:
				return;
		}
		tv.setImeOptions((tv.getImeOptions() & ~EditorInfo.IME_MASK_ACTION) | imeActionFlag);
	}

	public void setAttributedStringText(AttributedStringProxy attrString)
	{
		Bundle bundleText =
			AttributedStringProxy.toSpannableInBundle(attrString, TiApplication.getAppCurrentActivity());
		if (bundleText.containsKey(TiC.PROPERTY_ATTRIBUTED_STRING)) {
			//TIMOB-17210 Android: A textfield change listener is wrongly triggered also if the value is programmatically set before creation
			boolean wasDisabled = disableChangeEvent;
			disableChangeEvent = true;
			tv.setText(bundleText.getCharSequence(TiC.PROPERTY_ATTRIBUTED_STRING));
			if (bundleText.getBoolean(TiC.PROPERTY_HAS_LINK, false)) {
				tv.setMovementMethod(LinkMovementMethod.getInstance());
			}
			disableChangeEvent = wasDisabled;
		}
	}

	public void setAttributedStringHint(AttributedStringProxy attrString)
	{
		Spannable spannableText = AttributedStringProxy.toSpannable(attrString, TiApplication.getAppCurrentActivity());
		if (spannableText != null) {
			int type = UIModule.HINT_TYPE_STATIC;
			KrollProxy proxy = getProxy();
			if ((proxy != null) && (proxy.getProperties() != null)) {
				type = TiConvert.toInt(proxy.getProperties().get(TiC.PROPERTY_HINT_TYPE), type);
			}
			setHintText(type, spannableText);
		}
	}

	public void setHintText(int type, CharSequence hintText)
	{
		if (type == UIModule.HINT_TYPE_STATIC) {
			this.textInputLayout.setHint("");
			this.textInputLayout.setHintEnabled(false);
			this.tv.setHint(hintText);
		} else if (type == UIModule.HINT_TYPE_ANIMATED) {
			this.tv.setHint("");
			this.textInputLayout.setHint(hintText);
			this.textInputLayout.setHintEnabled(true);
		}

		this.tv.setPadding(
			this.tv.getPaddingLeft(),
			(type == UIModule.HINT_TYPE_ANIMATED) ? this.hintTextPadding : this.tv.getPaddingBottom(),
			this.tv.getPaddingRight(),
			this.tv.getPaddingBottom());
	}

	/**
	 * Updates the virtual keyboard's IME handling for the edit text using its current settings.
	 * Must be called after calling the setImeOptions() method for it to take affect.
	 */
	private void restartInputMethodManager()
	{
		if (this.tv != null) {
			InputMethodManager inputMethodManager =
				(InputMethodManager) this.tv.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
			if (inputMethodManager != null) {
				inputMethodManager.restartInput(this.tv);
			}
		}
	}

	@Override
	public void release()
	{
		super.release();

		tv = null;
		textInputLayout = null;
	}

	/** Adds/Removes input filters such as all-caps and max-length to an EditText. */
	private static class InputFilterHandler
	{
		private TiUIEditText view;
		private InputFilter.AllCaps allCapsFilter;
		private InputFilter.LengthFilter maxLengthFilter;
		private TiUIText.EmojiInputFilter emojiFilter;

		/**
		 * Creates a new handler used to apply input filters to the given EditText derived view.
		 * @param view The view to apply input filters to. Cannot be null or else an exception will be thrown.
		 */
		public InputFilterHandler(TiUIEditText view)
		{
			if (view == null) {
				throw new NullPointerException();
			}
			this.view = view;
		}

		/**
		 * Adds or removes an all-caps input filter to the EditText view.
		 * When enabled, all entered characters will be automatically capitalized.
		 * @param value Set true to add an all-caps filter. Set false to remove it.
		 */
		public void setAllCapsEnabled(boolean value)
		{
			if (value) {
				// Add all-caps filter to view.
				if (this.allCapsFilter == null) {
					if (Build.VERSION.SDK_INT >= 27) {
						this.allCapsFilter = new InputFilter.AllCaps(Locale.getDefault());
					} else {
						this.allCapsFilter = new InputFilter.AllCaps();
					}
					updateView();
				}
			} else {
				// Remove all-caps filter from view.
				if (this.allCapsFilter != null) {
					this.allCapsFilter = null;
					updateView();
				}
			}
		}

		/**
		 * Adds or removes a max character length input filter to the EditText view with the given length.
		 * This restricts the number of characters entered when set to zero or higher.
		 * @param length
		 * Set to zero or higher to restrict the number of characters entered.
		 * Set negative to remove the input filter, which removes the max character restriction.
		 */
		public void setMaxLength(int length)
		{
			if (length >= 0) {
				// Add/Replace max character length filter to view.
				this.maxLengthFilter = new InputFilter.LengthFilter(length);
				updateView();
			} else {
				// Remove max character length filter from view.
				if (this.maxLengthFilter != null) {
					this.maxLengthFilter = null;
					updateView();
				}
			}
		}

		/**
		 * Enables or disables a filter which prevents emoji-like characters from being entered into EditText.
		 * @param value Set true to enable filter disallowing emoji characters. Set false to disable.
		 */
		public void setEmojiFilterEnabled(boolean value)
		{
			if (value) {
				if (this.emojiFilter == null) {
					this.emojiFilter = new EmojiInputFilter();
					updateView();
				}
			} else {
				if (this.emojiFilter != null) {
					this.emojiFilter = null;
					updateView();
				}
			}
		}

		/** Updates the EditText with the currently configured input filters. */
		private void updateView()
		{
			ArrayList<InputFilter> filterList = new ArrayList<>();
			if (this.allCapsFilter != null) {
				filterList.add(this.allCapsFilter);
			}
			if (this.emojiFilter != null) {
				filterList.add(this.emojiFilter);
			}
			if (this.maxLengthFilter != null) {
				filterList.add(this.maxLengthFilter);
			}
			this.view.setFilters(filterList.toArray(new InputFilter[0]));
		}
	}

	/** Input filter used to prevent emoji-like characters from being entered into the edit text. */
	private static class EmojiInputFilter implements InputFilter
	{
		@Override
		public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend)
		{
			SpannableStringBuilder stringBuilder = null;
			for (int index = end - 1; index >= start; index--) {
				boolean shouldFilter = false;
				int type = Character.getType(source.charAt(index));
				switch (type) {
					case Character.NON_SPACING_MARK:
					case Character.SURROGATE:
					case Character.OTHER_SYMBOL:
						shouldFilter = true;
						break;
				}
				if (shouldFilter) {
					if ((end == (start + 1))) {
						return "";
					}
					if (stringBuilder == null) {
						stringBuilder = new SpannableStringBuilder(source, start, end);
					}
					stringBuilder.delete(index - start, index + 1 - start);
				}
			}
			return stringBuilder;
		}
	}

	/** Disables all key input from being entered into an EditText, making it read-only. */
	private static class DisabledKeyListener extends BaseKeyListener
	{
		@Override
		public int getInputType()
		{
			return InputType.TYPE_NULL;
		}

		@Override
		public boolean backspace(View view, Editable content, int keyCode, KeyEvent event)
		{
			return false;
		}

		@Override
		public boolean forwardDelete(View view, Editable content, int keyCode, KeyEvent event)
		{
			return false;
		}
	}

	/**
	 * Implements a decimal number key listener/filter for Titanium's "KEYBOARD_TYPE_DECIMAL_PAD" type.
	 * <p>
	 * This works-around localization bugs in Google's DigitsKeyListener class by:
	 * <ul>
	 *  <li>Allowing comma decimal separators to be used on Android OS versions older than 8.0.</li>
	 *  <li>Allowing Arabic decimal separators.</li>
	 *  <li>Replaces English symbols to localized symbols so string can be correctly parsed to a float.</li>
	 * </ul>
	 */
	private static class DecimalPadKeyListener extends DigitsKeyListener
	{
		private final char UNICODE_MINUS = '\u2212';
		private final char UNICODE_DASH = '\u2013';
		private final char UNICODE_ARABIC_DECIMAL_SEPARATOR = '\u066B';

		private char[] acceptedChars;
		private char localizedDecimalSeparatorChar;
		private char localizedMinusChar;
		private int inputTypeFlags;

		private DecimalPadKeyListener()
		{
			// Passing true to both arguments enables decimal separator and +/- sign support in base class.
			// Note: Do NOT pass Locale to constructor due to localization bugs mentioned above.
			super(true, true);

			// Initialize keyboard flags for decimal number input.
			this.inputTypeFlags = UIModule.INPUT_TYPE_CLASS_NUMBER;
			this.inputTypeFlags |= InputType.TYPE_NUMBER_FLAG_DECIMAL;
			this.inputTypeFlags |= InputType.TYPE_NUMBER_FLAG_SIGNED;

			// Fetch localize decimal separator and negative sign character.
			DecimalFormatSymbols decimalFormatSymbols = DecimalFormatSymbols.getInstance(Locale.getDefault());
			this.localizedDecimalSeparatorChar = decimalFormatSymbols.getDecimalSeparator();
			this.localizedMinusChar = decimalFormatSymbols.getMinusSign();

			// Create the "accepted" characters array for current locale.
			// Keyboard character entered that are not in this array will be automatically filtered out.
			CharacterSet charSet = new CharacterSet();
			charSet.add("0123456789+");
			{
				// Add localized numbers (such as Arabic) if different than the above.
				NumberFormat numberFormat = NumberFormat.getIntegerInstance();
				numberFormat.setGroupingUsed(false);            // Disable thousands separator.
				charSet.add(numberFormat.format(1234567890L));
			}
			charSet.add(this.localizedMinusChar);
			if ((this.localizedMinusChar == UNICODE_MINUS) || (this.localizedMinusChar == UNICODE_DASH)) {
				// Allow ASCII dash from keyboard. We'll replace it with localized minus via filter() method.
				charSet.add('-');
			}
			charSet.add(this.localizedDecimalSeparatorChar);
			if (this.localizedDecimalSeparatorChar == UNICODE_ARABIC_DECIMAL_SEPARATOR) {
				// Allow ASCII comma from keyboard. We'll replace it with localized decimal via filter() method.
				charSet.add(',');
			}
			this.acceptedChars = charSet.toArray();
		}

		@Override
		public int getInputType()
		{
			return this.inputTypeFlags;
		}

		@Override
		protected char[] getAcceptedChars()
		{
			return this.acceptedChars;
		}

		private boolean isDecimalSeparatorChar(char value)
		{
			if (this.localizedDecimalSeparatorChar == value) {
				return true;
			}
			if (this.localizedDecimalSeparatorChar == UNICODE_ARABIC_DECIMAL_SEPARATOR) {
				return (value == ',');
			}
			return false;
		}

		private boolean isSignChar(char value)
		{
			return (value == '-')
				|| (value == '+')
				|| (value == UNICODE_MINUS)
				|| (value == UNICODE_DASH)
				|| (value == this.localizedMinusChar);
		}

		@Override
		public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend)
		{
			// ------------------------------------------------------------------------------------------
			// Below is based on Google's "DigitsKeyListener.java" code as of API Level 26.
			// Was heavily modified to fix locale issues and to back-port it for older OS versions.
			// ------------------------------------------------------------------------------------------

			// Find the decimal separator and sign characters if they exist.
			int signIndex = -1;
			int decimalIndex = -1;
			for (int index = 0; index < dstart; index++) {
				char nextChar = dest.charAt(index);
				if (isSignChar(nextChar)) {
					signIndex = index;
				} else if (isDecimalSeparatorChar(nextChar)) {
					decimalIndex = index;
				}
			}
			int dlen = dest.length();
			for (int index = dend; index < dlen; index++) {
				char nextChar = dest.charAt(index);
				if (isSignChar(nextChar)) {
					return "";
				} else if (isDecimalSeparatorChar(nextChar)) {
					decimalIndex = index;
				}
			}

			// Some Android devices like Samsung/Huawei do not have localized keyboards and only have a '.' character.
			// Replace it with a localized decimal separator. Unfortunately, there is no other solution.
			// Note: Only do this if 1 character was received which assumes it comes from the virtual keyboard.
			//       If multiple characters were received, then it was probably pasted or assigned programmatically.
			if ((this.localizedDecimalSeparatorChar != '.') && ((end - start) == 1) && (source.charAt(start) == '.')) {
				SpannableStringBuilder stringBuilder = new SpannableStringBuilder(source, start, end);
				if (decimalIndex < 0) {
					stringBuilder.replace(0, 1, Character.toString(this.localizedDecimalSeparatorChar));
				} else {
					stringBuilder.clear();
				}
				return stringBuilder;
			}

			// Let base class attempt to filter the entered text first.
			// We normally use this string for English locales where period is used for decimal separator.
			CharSequence defaultFilteredString = super.filter(source, start, end, dest, dstart, dend);
			if (defaultFilteredString != null) {
				source = defaultFilteredString;
				start = 0;
				end = defaultFilteredString.length();
			}

			// Create a new stripped string if any invalid characters were found.
			SpannableStringBuilder strippedStringBuilder = null;
			for (int index = end - 1; index >= start; index--) {
				char nextChar = source.charAt(index);
				boolean shouldRemove = false;
				boolean shouldReplace = false;
				if (isSignChar(nextChar)) {
					if (index != start || dstart != 0) {
						shouldRemove = true;
					} else if (signIndex >= 0) {
						shouldRemove = true;
					} else {
						signIndex = index;
						if (nextChar != this.localizedMinusChar) {
							nextChar = this.localizedMinusChar;
							shouldReplace = true;
						}
					}
				} else if (isDecimalSeparatorChar(nextChar)) {
					if (decimalIndex >= 0) {
						shouldRemove = true;
					} else {
						decimalIndex = index;
						if (nextChar != this.localizedDecimalSeparatorChar) {
							nextChar = this.localizedDecimalSeparatorChar;
							shouldReplace = true;
						}
					}
				}
				if (shouldRemove || shouldReplace) {
					if (shouldRemove && (end == (start + 1))) {
						return "";
					}
					if (strippedStringBuilder == null) {
						strippedStringBuilder = new SpannableStringBuilder(source, start, end);
					}
					if (shouldRemove) {
						strippedStringBuilder.delete(index - start, index + 1 - start);
					} else if (shouldReplace) {
						strippedStringBuilder.replace(index, index + 1, Character.toString(nextChar));
					}
				}
			}

			// Return the stripped string if invalid chars were found. Otherwise use default string as-is.
			if (strippedStringBuilder != null) {
				return strippedStringBuilder;
			} else if (defaultFilteredString != null) {
				return defaultFilteredString;
			}
			return null;
		}
	}

	/** Stores a set of unique characters that can be easily turned into an array. */
	private static class CharacterSet
	{
		private final StringBuilder stringBuilder = new StringBuilder(32);

		/**
		 * Adds the given character if not already contained in the set.
		 * @param value The character to be added.
		 */
		public void add(char value)
		{
			if (!contains(value)) {
				this.stringBuilder.append(value);
			}
		}

		/**
		 * Adds all of the characters in the given string to the set.
		 * @param value The string of characters to be added. Can be null.
		 */
		public void add(String value)
		{
			if (value == null) {
				return;
			}

			for (int index = 0; index < value.length(); index++) {
				add(value.charAt(index));
			}
		}

		/**
		 * Determines if the given character already exists in the set.
		 * @param value The character to search for.
		 * @return Returns true if given character exists in the set. Returns false if not.
		 */
		public boolean contains(char value)
		{
			for (int index = 0; index < this.stringBuilder.length(); index++) {
				if (this.stringBuilder.charAt(index) == value) {
					return true;
				}
			}
			return false;
		}

		/**
		 * Creates an array containing all of the characters in the set.
		 * @return Returns an array of characters copied from the set. Will never be null.
		 */
		public char[] toArray()
		{
			return this.stringBuilder.toString().toCharArray();
		}
	}
}
