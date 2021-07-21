/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2021 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.util;

import androidx.exifinterface.media.ExifInterface;

/** Enum type indicating the orientation/mirroring assigned to an image file via its EXIF metadata. */
public enum TiExifOrientation {
	/** Image is not rotated or mirrored. Can be displayed as-is. */
	UPRIGHT(ExifInterface.ORIENTATION_NORMAL, 0, false),

	/** Image is rotated 90 degrees counter-clockwise. */
	ROTATE_90(ExifInterface.ORIENTATION_ROTATE_90, 90, false),

	/** Image is upside-down. */
	ROTATE_180(ExifInterface.ORIENTATION_ROTATE_180, 180, false),

	/** Image is rotated 270 degrees counter-clockwise. */
	ROTATE_270(ExifInterface.ORIENTATION_ROTATE_270, 270, false),

	/** Image is upside-down and mirrored. */
	FLIP_VERTICAL(ExifInterface.ORIENTATION_FLIP_VERTICAL, 180, true),

	/** Image is upright and mirrored. */
	FLIP_HORIZONTAL(ExifInterface.ORIENTATION_FLIP_HORIZONTAL, 0, true),

	/** Image is rotated 90 degrees counter-clockwise and mirrored. */
	TRANSPOSE(ExifInterface.ORIENTATION_TRANSPOSE, 90, true),

	/** Image is rotated 270 degrees counter-clockwise and mirrored. */
	TRANSVERSE(ExifInterface.ORIENTATION_TRANSVERSE, 270, true);

	private final int exifInterfaceId;
	private final int degreesCounterClockwise;
	private final boolean isMirrored;

	/**
	 * Creates a new EXIF orientation type.
	 * @param exifInterfaceId Integer ID matching a constant in Google's "ExifInterface" class.
	 * @param degreesCounterClockwise Rotation in degrees. Must be 0, 90, 180, or 270.
	 * @param isMirrored Set true if image is flipped horizontally. Set false if not flipped.
	 */
	private TiExifOrientation(int exifInterfaceId, int degreesCounterClockwise, boolean isMirrored)
	{
		this.exifInterfaceId = exifInterfaceId;
		this.degreesCounterClockwise = degreesCounterClockwise;
		this.isMirrored = isMirrored;
	}

	/**
	 * Gets the integer ID matching an orientation constant in Google's "ExifInterface" class.
	 * @return Returns the ExifInterface class' matching constant integer ID.
	 */
	public int getExifInterfaceId()
	{
		return this.exifInterfaceId;
	}

	/**
	 * Gets the rotation of this orientation type relative to the upright position.
	 * @return Returns rotation in degrees clockwise such as 0, 90, 180, or 270.
	 */
	public int getDegreesClockwise()
	{
		return (360 - this.degreesCounterClockwise) % 360;
	}

	/**
	 * Gets the rotation of this orientation type relative to the upright position.
	 * @return Returns rotation in degrees counter-clockwise such as 0, 90, 180, or 270.
	 */
	public int getDegreesCounterClockwise()
	{
		return this.degreesCounterClockwise;
	}

	/**
	 * Determines if the image is flipped horizontally.
	 * @return Returns true if flipped horizontally. Returns false if not flipped.
	 */
	public boolean isMirrored()
	{
		return this.isMirrored;
	}

	/**
	 * Determines if the image is rotated sideways. That is, not upright or upside-down.
	 * @return
	 * Returns true if ROTATE_90, ROTATE_270, TRANSPOSE, or TRANSVERSE.
	 * Returns false if UPRIGHT, ROTATE_180, FLIP_VERTICAL, or FLIP_HORIZONTAL.
	 */
	public boolean isSideways()
	{
		return (this.degreesCounterClockwise == 90) || (this.degreesCounterClockwise == 270);
	}

	/**
	 * Fetches EXIF orientation info from the given ExifInterface object.
	 * @param exifInterface EXIF object to fetch orientation info from. Can be null.
	 * @return Returns EXIF orientation info from the given argument. Returns null if given a null argument.
	 */
	public static TiExifOrientation from(ExifInterface exifInterface)
	{
		if (exifInterface == null) {
			return null;
		}

		int exifOrientationId =
			exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
		for (TiExifOrientation nextObject : TiExifOrientation.values()) {
			if ((nextObject != null) && (nextObject.exifInterfaceId == exifOrientationId)) {
				return nextObject;
			}
		}
		return TiExifOrientation.UPRIGHT;
	}
}
