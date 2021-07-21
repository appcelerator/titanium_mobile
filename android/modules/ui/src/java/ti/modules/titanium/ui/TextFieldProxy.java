/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2016 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiUIView;

import ti.modules.titanium.ui.widget.TiUIText;
import android.app.Activity;

@Kroll.proxy(creatableInModule = UIModule.class,
	propertyAccessors = {
		TiC.PROPERTY_ATTRIBUTED_STRING,
		TiC.PROPERTY_ATTRIBUTED_HINT_TEXT,
		TiC.PROPERTY_AUTOCAPITALIZATION,
		TiC.PROPERTY_AUTOCORRECT,
		TiC.PROPERTY_AUTOFILL_TYPE,
		TiC.PROPERTY_AUTO_LINK,
		TiC.PROPERTY_CLEAR_ON_EDIT,
		TiC.PROPERTY_COLOR,
		TiC.PROPERTY_EDITABLE,
		TiC.PROPERTY_ELLIPSIZE,
		TiC.PROPERTY_ENABLE_COPY,
		TiC.PROPERTY_ENABLE_RETURN_KEY,
		TiC.PROPERTY_FONT,
		TiC.PROPERTY_FULLSCREEN,
		TiC.PROPERTY_HINT_TEXT,
		TiC.PROPERTY_HINT_TEXT_ID,
		TiC.PROPERTY_HINT_TEXT_COLOR,
		TiC.PROPERTY_HINT_TYPE,
		TiC.PROPERTY_INPUT_TYPE,
		TiC.PROPERTY_KEYBOARD_TYPE,
		TiC.PROPERTY_MAX_LENGTH,
		TiC.PROPERTY_PASSWORD_MASK,
		TiC.PROPERTY_TEXT_ALIGN,
		TiC.PROPERTY_VALUE,
		TiC.PROPERTY_VERTICAL_ALIGN,
		TiC.PROPERTY_RETURN_KEY_TYPE,
		TiC.PROPERTY_PADDING,
		TiC.PROPERTY_PASSWORD_VISIBILITY_TOGGLE_COLOR,
		TiC.PROPERTY_PASSWORD_VISIBILITY_TOGGLE_ENABLED,
		TiC.PROPERTY_PASSWORD_VISIBILITY_TOGGLE_TINT_COLOR
})
public class TextFieldProxy extends TiViewProxy
{
	public TextFieldProxy()
	{
		super();
		defaultValues.put(TiC.PROPERTY_VALUE, "");
		defaultValues.put(TiC.PROPERTY_MAX_LENGTH, -1);
		defaultValues.put(TiC.PROPERTY_ENABLE_COPY, true);
		defaultValues.put(TiC.PROPERTY_FULLSCREEN, true);
		defaultValues.put(TiC.PROPERTY_HINT_TYPE, UIModule.HINT_TYPE_STATIC);
		defaultValues.put(TiC.PROPERTY_PASSWORD_VISIBILITY_TOGGLE_ENABLED, false);
	}

	@Override
	public void handleCreationArgs(KrollModule createdInModule, Object[] args)
	{
		super.handleCreationArgs(createdInModule, args);
	}

	@Override
	public TiUIView createView(Activity activity)
	{
		return new TiUIText(this, true);
	}

	@Override
	protected KrollDict getLangConversionTable()
	{
		KrollDict table = new KrollDict();
		table.put(TiC.PROPERTY_HINT_TEXT, TiC.PROPERTY_HINT_TEXT_ID);
		return table;
	}

	@Kroll.method
	public Boolean hasText()
	{
		Object text = getProperty(TiC.PROPERTY_VALUE);
		return (TiConvert.toString(text, "").length() > 0);
	}

	@Kroll.method
	public void setSelection(int start, int stop)
	{
		TiUIView v = getOrCreateView();
		if (v instanceof TiUIText) {
			((TiUIText) v).setSelection(start, stop);
		}
	}

	@Kroll.getProperty
	public KrollDict getSelection()
	{
		TiUIView v = peekView();
		if (v instanceof TiUIText) {
			return ((TiUIText) v).getSelection();
		}
		return null;
	}

	@Override
	public String getApiName()
	{
		return "Ti.UI.TextField";
	}

	@Kroll.getProperty(name = "focused")
	public boolean isFocused()
	{
		TiUIView v = peekView();
		if (v != null) {
			return v.isFocused();
		}
		return false;
	}
}
