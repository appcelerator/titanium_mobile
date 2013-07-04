/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget.searchbar;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiFileHelper;
import org.appcelerator.titanium.util.TiUIHelper;
import ti.modules.titanium.ui.widget.TiUIText;
import android.graphics.drawable.Drawable;
import android.text.TextUtils.TruncateAt;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class TiUISearchBar extends TiUIText
{
	protected ImageButton cancelBtn;
	private TextView promptText;

	public interface OnSearchChangeListener {
		public void filterBy(String text);
	}
	
	protected OnSearchChangeListener searchChangeListener;
	
	public TiUISearchBar(final TiViewProxy proxy)
	{
		super(proxy, true);
		
		TiEditText tv = (TiEditText) getNativeView();
		promptText = new TextView(proxy.getActivity());
		promptText.setEllipsize(TruncateAt.END);
		promptText.setSingleLine(true);
		tv.setImeOptions(EditorInfo.IME_ACTION_DONE);

		// TODO Add Filter support

		// Steal the Text's nativeView. We're going to replace it with our layout.
		cancelBtn = new ImageButton(proxy.getActivity());
		cancelBtn.isFocusable();
		cancelBtn.setId(101);
		cancelBtn.setImageResource(android.R.drawable.ic_input_delete);
		// set some minimum dimensions for the cancel button, in a density-independent way.
		final float scale = cancelBtn.getContext().getResources().getDisplayMetrics().density;
		cancelBtn.setMinimumWidth((int) (48 * scale));
		cancelBtn.setMinimumHeight((int) (20 * scale));
		cancelBtn.setOnClickListener(new OnClickListener()
		{
			public void onClick(View view)
			{
				/* TODO try {
					proxy.set(getProxy().getTiContext().getScope(), "value", "");
				} catch (NoSuchFieldException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}*/
				fireEvent("cancel", null);
			}
		});

		RelativeLayout layout = new RelativeLayout(proxy.getActivity())
		{
			@Override
			protected void onLayout(boolean changed, int left, int top, int right, int bottom)
			{
				super.onLayout(changed, left, top, right, bottom);
				TiUIHelper.firePostLayoutEvent(proxy);
			}
		};

		layout.setGravity(Gravity.NO_GRAVITY);
		layout.setPadding(0, 0, 0, 0);
		int promptWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 150, proxy.getActivity()
			.getResources().getDisplayMetrics());
		RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(promptWidth, LayoutParams.WRAP_CONTENT);
		params.addRule(RelativeLayout.CENTER_IN_PARENT);
		params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
		promptText.setGravity(Gravity.CENTER_HORIZONTAL);
		layout.addView(promptText, params);

		params = new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
		params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
		params.addRule(RelativeLayout.CENTER_VERTICAL);
		params.addRule(RelativeLayout.LEFT_OF, 101);
//		params.setMargins(4, 4, 4, 4);
		layout.addView(tv, params);

		params = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
		params.addRule(RelativeLayout.CENTER_VERTICAL);
//		params.setMargins(0, 4, 4, 4);
		layout.addView(cancelBtn, params);

		setNativeView(layout);
	}
	
	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
		if (this.searchChangeListener != null) {
			this.searchChangeListener.filterBy(s.toString());
		}
		super.onTextChanged(s, start, before, count);
	}

	@Override
	public void processProperties(KrollDict d)
	{
		super.processProperties(d);

		if (d.containsKey(TiC.PROPERTY_SHOW_CANCEL)) {
			boolean showCancel = TiConvert.toBoolean(d, TiC.PROPERTY_SHOW_CANCEL, false);
			cancelBtn.setVisibility(showCancel ? View.VISIBLE : View.GONE);
		}
		if (d.containsKey(TiC.PROPERTY_BAR_COLOR)) {
			nativeView.setBackgroundColor(TiConvert.toColor(d, TiC.PROPERTY_BAR_COLOR));
		}
		if (d.containsKey(TiC.PROPERTY_PROMPT)) {
			String strPrompt = TiConvert.toString(d, TiC.PROPERTY_PROMPT);
			promptText.setText(strPrompt);
		}
		if (d.containsKey(TiC.PROPERTY_BACKGROUND_IMAGE)) {
			String bkgdImage = TiConvert.toString(proxy.getProperty(TiC.PROPERTY_BACKGROUND_IMAGE));
			TiFileHelper tfh = new TiFileHelper(tv.getContext());
			String url = proxy.resolveUrl(null, bkgdImage.toString());
			Drawable background = tfh.loadDrawable(url, false);
			nativeView.setBackgroundDrawable(background);
		}
	}

	@Override
	public void propertyChanged(String key, Object oldValue, Object newValue, KrollProxy proxy)
	{
		if (key.equals(TiC.PROPERTY_SHOW_CANCEL)) {
			boolean showCancel = TiConvert.toBoolean(newValue);
			cancelBtn.setVisibility(showCancel ? View.VISIBLE : View.GONE);
		} else if (key.equals(TiC.PROPERTY_BAR_COLOR)) {
			nativeView.setBackgroundColor(TiConvert.toColor(TiConvert.toString(newValue)));
		} else if (key.equals(TiC.PROPERTY_PROMPT)) {
			String strPrompt = TiConvert.toString(newValue);
			promptText.setText(strPrompt);
		} else if (key.equals(TiC.PROPERTY_BACKGROUND_IMAGE)) {
			String bkgdImage = TiConvert.toString(newValue);
			TiFileHelper tfh = new TiFileHelper(tv.getContext());
			String url = proxy.resolveUrl(null, bkgdImage);
			Drawable background = tfh.loadDrawable(url, false);
			nativeView.setBackgroundDrawable(background);
		} else {
			super.propertyChanged(key, oldValue, newValue, proxy);
		}
	}
	
	public void setOnSearchChangeListener(OnSearchChangeListener listener) {
		this.searchChangeListener = listener;
	}
}
