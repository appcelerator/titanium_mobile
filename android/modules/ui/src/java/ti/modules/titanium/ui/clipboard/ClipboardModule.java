/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2021 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.clipboard;

import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;

import ti.modules.titanium.ui.UIModule;
import android.content.Context;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.ClipDescription;
import android.os.Build;

@Kroll.module(parentModule = UIModule.class)
public class ClipboardModule extends KrollModule
{
	private static final String TAG = "Clipboard";

	public ClipboardModule()
	{
		super();
	}

	/**
	 * Get the native clipboard instance.
	 */
	private ClipboardManager board()
	{
		return (ClipboardManager) TiApplication.getInstance().getSystemService(Context.CLIPBOARD_SERVICE);
	}

	/**
	 * Android's clipboard currently only handles text; when working with
	 * arbitrary data check if it looks like text that we're being handed.
	 */
	private boolean isTextType(String type)
	{
		String mimeType = type.toLowerCase();
		return mimeType.equals(ClipDescription.MIMETYPE_TEXT_PLAIN) || mimeType.startsWith("text")
			|| mimeType.equals("public.plain-text");
	}

	@Kroll.method
	public void clearData(@Kroll.argument(optional = true) String type)
	{
		clearText();
	}

	@Kroll.method
	public void clearText()
	{
		ClipboardManager board = board();
		if (Build.VERSION.SDK_INT >= 28) {
			board.clearPrimaryClip();
		} else {
			board.setText(null);
		}
	}

	@Kroll.method
	public Object getData(String type)
	{
		if (isTextType(type)) {
			return getText();
		}

		// FIXME: Support non-text data!
		return null;
	}

	@Kroll.method
	@Kroll.getProperty
	public String getText()
	{
		ClipboardManager board = board();
		if (!board.hasPrimaryClip()) {
			return null;
		}

		ClipData data = board.getPrimaryClip();
		if (data == null) {
			return null;
		}

		ClipData.Item item = data.getItemAt(0);
		CharSequence seq = item.getText();
		if (seq == null) {
			return null;
		}
		return seq.toString();
	}

	@Kroll.method
	public boolean hasData(@Kroll.argument(optional = true) String type)
	{
		if (type == null || isTextType(type)) {
			return hasText();
		}
		return false;
	}

	@Kroll.method
	public boolean hasText()
	{
		return getText() != null;
	}

	@Kroll.method
	public void setData(String type, Object data)
	{
		if (isTextType(type) && data != null) {
			setText(data.toString());
		} else {
			Log.w(TAG, "Android clipboard only supports text data");
		}
	}

	@Kroll.method
	@Kroll.setProperty
	public void setText(String text)
	{
		ClipData clip = ClipData.newPlainText("simple text", text);
		board().setPrimaryClip(clip);
	}

	@Override
	public String getApiName()
	{
		return "Ti.UI.Clipboard";
	}
}
