/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.util;

import java.util.ArrayList;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;

@SuppressWarnings("deprecation")
public class TiNinePatchHelper
{

	// Constants for writing 9 patch data from bitmap
	// Each one represents on of the sides of the bitmap.
	private final int BITMAP_SIDE_LEFT = 1;
	private final int BITMAP_SIDE_TOP = 2;
	private final int BITMAP_SIDE_RIGHT = 4;
	private final int BITMAP_SIDE_BOTTOM = 8;

	class SegmentColor
	{
		int index;
		int color;
	}

	public Drawable process(Bitmap b)
	{
		Drawable nd = null;
		if (b != null) {
			if (isNinePatch(b)) {
				byte[] newChunk = createChunk(b);
				Rect paddingRectUnscaled = processPadding(b);
				nd = new NinePatchDrawable(null, cropNinePatch(b), newChunk, paddingRectUnscaled, "");
			} else {
				nd = new BitmapDrawable(b);
			}
		}

		return nd;
	}

	private Rect processPadding(Bitmap b)
	{
		int left = 0;
		int top = 0;
		int right = 0;
		int bottom = 0;

		ArrayList<SegmentColor> xdivs = walkPath(b, BITMAP_SIDE_BOTTOM);
		ArrayList<SegmentColor> ydivs = walkPath(b, BITMAP_SIDE_RIGHT);

		// Setting multiple padding areas in 9Patch images results
		// in content area defined by outermost pixels' indexes.
		// All the indexes are subtracted by one to reflect the proper
		// position in the cropped bitmap (without the 9Patch border)

		if (xdivs.size() != 0) {
			// leftmost pixel of the first padding area
			left = xdivs.get(0).index - 1;
			// rightmost pixel of the last padding area
			right = b.getWidth() - (xdivs.get(xdivs.size() - 1).index - 1);
		}

		if (ydivs.size() != 0) {
			// leftmost pixel of the first padding area
			top = ydivs.get(0).index - 1;
			// bottommost pixel of the last padding area
			bottom = b.getHeight() - (ydivs.get(ydivs.size() - 1).index - 1);
		}

		return new Rect(left, top, right, bottom);
	}

	// Common function for walking the 9Patch border in all directions.
	private ArrayList<SegmentColor> walkPath(Bitmap b, int side)
	{

		ArrayList<SegmentColor> result = new ArrayList<SegmentColor>();

		int left = ((side >> 0) & 1);
		int top = ((side >> 1) & 1);
		int right = ((side >> 2) & 1);
		int bottom = ((side >> 3) & 1);

		int horizontalMovement = top | bottom;
		int verticalMovement = left | right;

		//last pixel for comparison condition
		int last = b.getPixel((b.getWidth() - 1) * right, (b.getHeight() - 1) * bottom);

		for (int i = 1; i < b.getWidth() * (horizontalMovement) + b.getHeight() * (verticalMovement); i++) {
			// common movement condition
			int p = b.getPixel(i * horizontalMovement + ((b.getWidth() - 1) * right),
							   i * verticalMovement + ((b.getHeight() - 1) * bottom));

			if (p != last) {
				SegmentColor sc = new SegmentColor();
				sc.index = i;
				sc.color = last;
				result.add(sc);
				last = p;
			}
		}

		return result;
	}

	private boolean isNinePatch(Bitmap b)
	{
		// NinePatch image is a standard PNG image that includes an extra 1-pixel-wide border.
		// It must have black lines in the top and left part of the border to define the stretchable section.
		// It may have black lines in the bottom and right part of the border to define the drawable section.
		if (!b.hasAlpha()) {
			return false;
		}

		boolean result = true;

		int width = b.getWidth();
		int height = b.getHeight();

		int topSum = 0;
		int leftSum = 0;

		if (width >= 3 && height >= 3) {
			for (int i = 0; i < width; i++) {
				int c = b.getPixel(i, 0);
				topSum += (c == 0 ? 0 : 1);
				if (!isValidColor(c)) {
					result = false;
					break;
				}

				c = b.getPixel(i, height - 1);
				if (!isValidColor(c)) {
					result = false;
					break;
				}
			}

			if (result) {
				for (int i = 0; i < height; i++) {
					int c = b.getPixel(0, i);
					leftSum += (c == 0 ? 0 : 1);
					if (!isValidColor(c)) {
						result = false;
						break;
					}

					c = b.getPixel(width - 1, i);
					if (!isValidColor(c)) {
						result = false;
						break;
					}
				}
			}
		} else {
			result = false;
		}

		// Nine patch cannot have a completely black border or a completely transparent border in the left or top.
		if (leftSum == 0 || topSum == 0 || leftSum == height || topSum == width) {
			result = false;
		}

		return result;
	}

	private boolean isValidColor(int c)
	{
		return (c == 0 || (c == Color.BLACK));
	}

	private Bitmap cropNinePatch(Bitmap b)
	{
		Bitmap cb = null;

		cb = Bitmap.createBitmap(b.getWidth() - 2, b.getHeight() - 2, b.getConfig());
		int[] pixels = new int[cb.getWidth() * cb.getHeight()];
		b.getPixels(pixels, 0, cb.getWidth(), 1, 1, cb.getWidth(), cb.getHeight());
		cb.setPixels(pixels, 0, cb.getWidth(), 0, 0, cb.getWidth(), cb.getHeight());

		return cb;
	}

	byte[] createChunk(Bitmap b)
	{
		byte[] chunk = null;
		int numColors = 1;

		ArrayList<SegmentColor> xdivs = walkPath(b, BITMAP_SIDE_TOP);
		int numXDivs = xdivs.size();

		ArrayList<SegmentColor> ydivs = walkPath(b, BITMAP_SIDE_LEFT);
		int numYDivs = ydivs.size();

		// Calculate Region Colors
		ArrayList<Integer> colors = new ArrayList<Integer>();
		for (int y = 0; y < ydivs.size(); y++) {
			int yc = ydivs.get(y).color;
			for (int x = 0; x < xdivs.size(); x++) {
				if (yc == 0) {
					colors.add(xdivs.get(x).color == 0 ? 0 : 1);
				} else {
					colors.add(ydivs.get(y).color == 0 ? 0 : 1);
				}
			}
			if (yc == 0) {
				colors.add(colors.get(colors.size() - 1) == 1 ? 0 : 1);
			} else {
				colors.add(1);
			}
		}
		for (int i = 0; i < xdivs.size() + 1; i++) {
			colors.add(colors.get(i));
		}

		numColors = colors.size();

		// Figure out the size / looks like padded to 32bits.
		int size = 32 + // wasDeserialized, numXDivs, numYDivs, numColors, padLeft, padRight, padTop, padBottom
				   numXDivs * 32 + numYDivs * 32 + numColors * 32;

		chunk = new byte[size];
		chunk[0] = 0;
		chunk[1] = (byte) (0xFF & numXDivs);
		chunk[2] = (byte) (0xFF & numYDivs);
		chunk[3] = (byte) (0xFF & numColors);

		int startOfXData = 32;
		for (int i = 0; i < xdivs.size(); i++) {
			toBytes(chunk, startOfXData + (i * 4), xdivs.get(i).index - 1);
		}

		int startOfYData = startOfXData + (numXDivs * 4);
		for (int i = 0; i < ydivs.size(); i++) {
			toBytes(chunk, startOfYData + (i * 4), ydivs.get(i).index - 1);
		}

		int startOfColors = startOfYData + (numYDivs * 4);
		for (int i = 0; i < colors.size(); i++) {
			toBytes(chunk, startOfColors + (i * 4), 1 /*colors.get(i)*/);
		}

		return chunk;
	}

	private void toBytes(byte[] a, int offset, int v)
	{
		a[offset] = (byte) (0x000000FF & v);
		a[offset + 1] = (byte) ((0x0000FF00 & v) >> 8);
		a[offset + 2] = (byte) ((0x00FF0000 & v) >> 16);
		a[offset + 3] = (byte) ((0xFF000000 & v) >> 24);
	}
}
