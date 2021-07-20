/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2020 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiRHelper;
import org.appcelerator.titanium.view.TiUIView;

public class TiUIBottomSheetView extends TiUIView
{
	private static final String TAG = "TiUIBottomSheetView";
	int id_drawer_layout = 0;
	int id_bottomSheet = 0;
	CoordinatorLayout layout;
	AppCompatActivity appCompatActivity;
	BottomSheetBehavior<RelativeLayout> bottomSheetBehavior;
	RelativeLayout bsLayout;
	int peakHeight = 32;

	public TiUIBottomSheetView(TiViewProxy proxy)
	{
		super(proxy);

		if (this.nativeView == null) {
			processProperties(getProxy().getProperties());
		}
	}

	@Override
	public void add(TiUIView child)
	{
		View view = getNativeView(child.getProxy());
		Object width = child.getProxy().getProperty(TiC.PROPERTY_WIDTH);
		Object height = child.getProxy().getProperty(TiC.PROPERTY_HEIGHT);

		int w = RelativeLayout.LayoutParams.MATCH_PARENT;
		if (width.equals(TiC.LAYOUT_SIZE)) {
			w = RelativeLayout.LayoutParams.WRAP_CONTENT;
		} else if (width.equals(TiC.LAYOUT_FILL)) {
			w = RelativeLayout.LayoutParams.MATCH_PARENT;
		} else {
			w = (int) TiConvert.toTiDimension(TiConvert.toString(width), TiDimension.TYPE_WIDTH).getAsPixels(view);
		}

		int h = RelativeLayout.LayoutParams.MATCH_PARENT;
		if (height.equals(TiC.LAYOUT_SIZE)) {
			h = RelativeLayout.LayoutParams.WRAP_CONTENT;
		} else if (height.equals(TiC.LAYOUT_FILL)) {
			h = RelativeLayout.LayoutParams.MATCH_PARENT;
		} else {
			h = (int) TiConvert.toTiDimension(TiConvert.toString(height), TiDimension.TYPE_HEIGHT).getAsPixels(view);
		}

		RelativeLayout.LayoutParams rlp = new RelativeLayout.LayoutParams(w, h);
		bsLayout.addView(view, rlp);
	}

	private View getNativeView(TiViewProxy viewProxy)
	{
		TiUIView view = viewProxy.getOrCreateView();
		View outerView = view.getOuterView();
		View nativeView = outerView != null ? outerView : view.getNativeView();
		ViewGroup parentViewGroup = (ViewGroup) nativeView.getParent();
		if (parentViewGroup != null) {
			parentViewGroup.removeAllViews();
		}
		return nativeView;
	}

	@Override
	public void processProperties(KrollDict d)
	{
		super.processProperties(d);

		if (d.containsKeyAndNotNull("peakHeight")) {
			peakHeight = TiConvert.toInt(d.get("peakHeight"), 32);
		}

		try {
			id_drawer_layout = TiRHelper.getResource("layout.titanium_ui_bottomsheet");
			id_bottomSheet = TiRHelper.getResource("id.bottomSheet");
		} catch (TiRHelper.ResourceNotFoundException e) {
			//
		}
		LayoutInflater inflater = LayoutInflater.from(proxy.getActivity());
		layout = (CoordinatorLayout) inflater.inflate(id_drawer_layout, null);
		setNativeView(layout);

		bsLayout = (RelativeLayout) layout.findViewById(id_bottomSheet);
		bottomSheetBehavior = BottomSheetBehavior.from(bsLayout);
		bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

		int localPeak = (int) TiConvert.toTiDimension(TiConvert.toString(peakHeight),
			TiDimension.TYPE_HEIGHT).getAsPixels(getNativeView());
		bottomSheetBehavior.setPeekHeight(localPeak);

		if (d.containsKeyAndNotNull(TiC.PROPERTY_BACKGROUND_COLOR)) {
			bsLayout.setBackgroundColor(TiConvert.toColor(TiConvert.toString(d.get(TiC.PROPERTY_BACKGROUND_COLOR))));
		}

		bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback()
		{
			@Override
			public void onStateChanged(@NonNull View bottomSheet, int newState)
			{
				KrollDict kd = new KrollDict();
				kd.put("state", newState);
				fireEvent("stateChanged", kd);
			}

			@Override
			public void onSlide(@NonNull View bottomSheet, float slideOffset)
			{

			}
		});
	}

	public void toggle()
	{
		if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
			bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
		} else {
			bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
		}
	}

	public void expand()
	{
		bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
	}

	public void collapse()
	{
		bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
	}
}
