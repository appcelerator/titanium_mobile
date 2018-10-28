/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2016 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.KrollRuntime;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.AsyncResult;
import org.appcelerator.kroll.common.TiMessenger;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiUIView;

import ti.modules.titanium.ui.widget.TiUIText;
import android.app.Activity;
import android.os.Message;
// clang-format off
@Kroll.proxy(creatableInModule = UIModule.class,
	propertyAccessors = {
		TiC.PROPERTY_ATTRIBUTED_STRING,
		TiC.PROPERTY_AUTOCAPITALIZATION,
		TiC.PROPERTY_AUTOCORRECT,
		TiC.PROPERTY_AUTOFILL_TYPE,
		TiC.PROPERTY_AUTO_LINK,
		TiC.PROPERTY_CLEAR_ON_EDIT,
		TiC.PROPERTY_COLOR,
		TiC.PROPERTY_EDITABLE,
		TiC.PROPERTY_ELLIPSIZE,
		TiC.PROPERTY_ENABLE_RETURN_KEY,
		TiC.PROPERTY_FONT,
		TiC.PROPERTY_FULLSCREEN,
		TiC.PROPERTY_HINT_TEXT,
		TiC.PROPERTY_HINT_TEXT_COLOR,
		TiC.PROPERTY_HINT_TYPE,
		TiC.PROPERTY_KEYBOARD_TYPE,
		TiC.PROPERTY_LINES,
		TiC.PROPERTY_MAX_LINES,
		TiC.PROPERTY_MAX_LENGTH,
		TiC.PROPERTY_PASSWORD_MASK,
		TiC.PROPERTY_TEXT_ALIGN,
		TiC.PROPERTY_VALUE,
		TiC.PROPERTY_VERTICAL_ALIGN,
		TiC.PROPERTY_PADDING,
		TiC.PROPERTY_RETURN_KEY_TYPE
})
// clang-format on
public class TextAreaProxy extends TiViewProxy
{
	private static final int MSG_FIRST_ID = TiViewProxy.MSG_LAST_ID + 1;
	private static final int MSG_SET_SELECTION = MSG_FIRST_ID + 201;
	private static final int MSG_GET_SELECTION = MSG_FIRST_ID + 202;

	public TextAreaProxy()
	{
		super();
		defaultValues.put(TiC.PROPERTY_VALUE, "");
		defaultValues.put(TiC.PROPERTY_LINES, -1);
		defaultValues.put(TiC.PROPERTY_MAX_LINES, -1);
		defaultValues.put(TiC.PROPERTY_MAX_LENGTH, -1);
		defaultValues.put(TiC.PROPERTY_FULLSCREEN, true);
		defaultValues.put(TiC.PROPERTY_EDITABLE, true);
		defaultValues.put(TiC.PROPERTY_HINT_TYPE, UIModule.HINT_TYPE_STATIC);
	}

	@Override
	public void handleCreationArgs(KrollModule createdInModule, Object[] args)
	{
		super.handleCreationArgs(createdInModule, args);
	}

	@Override
	public TiUIView createView(Activity activity)
	{
		return new TiUIText(this, false);
	}

	@Kroll
		.getProperty(writableKeys = true)
		@Kroll.method
		public Object getFont()
	{
		return hasProperty(TiC.PROPERTY_FONT) ? getProperty(TiC.PROPERTY_FONT) : KrollRuntime.UNDEFINED;
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
		if (v != null) {
			if (TiApplication.isUIThread()) {
				((TiUIText) v).setSelection(start, stop);
				return;
			}
			KrollDict args = new KrollDict();
			args.put(TiC.PROPERTY_START, start);
			args.put(TiC.PROPERTY_STOP, stop);
			getMainHandler().obtainMessage(MSG_SET_SELECTION, args).sendToTarget();
		}
	}

	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public KrollDict getSelection()
	// clang-format on
	{
		TiUIView v = peekView();
		if (v != null) {
			if (TiApplication.isUIThread()) {
				return ((TiUIText) v).getSelection();
			} else {
				return (KrollDict) TiMessenger.sendBlockingMainMessage(
					getMainHandler().obtainMessage(MSG_GET_SELECTION));
			}
		} else {
			return null;
		}
	}

	public boolean handleMessage(Message msg)
	{
		switch (msg.what) {
			case MSG_SET_SELECTION: {
				TiUIView v = getOrCreateView();
				if (v != null) {
					Object argsObj = msg.obj;
					if (argsObj instanceof KrollDict) {
						KrollDict args = (KrollDict) argsObj;
						((TiUIText) v).setSelection(args.getInt(TiC.PROPERTY_START), args.getInt(TiC.PROPERTY_STOP));
					}
				}
				return true;
			}

			case MSG_GET_SELECTION: {
				AsyncResult result = null;
				result = (AsyncResult) msg.obj;
				TiUIView v = peekView();
				if (v != null) {
					result.setResult(((TiUIText) v).getSelection());
				} else {
					result.setResult(null);
				}
				return true;
			}

			default: {
				return super.handleMessage(msg);
			}
		}
	}

	@Override
	public String getApiName()
	{
		return "Ti.UI.TextArea";
	}
}
