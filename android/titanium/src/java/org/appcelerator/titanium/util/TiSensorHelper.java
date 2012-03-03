/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package org.appcelerator.titanium.util;

import java.util.concurrent.atomic.AtomicInteger;

import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiConfig;
import org.appcelerator.titanium.TiApplication;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Utility methods to register/unregister sensor listeners.
 */
public class TiSensorHelper
{
	private static final String LCAT = "TiSensorHelper";
	private static final boolean DBG = TiConfig.LOGD;

	private static AtomicInteger listenerCount = new AtomicInteger();
	private static SensorManager sensorManager;


	/**
	 * Registers a sensor listener with specified types and sensitivity.
	 * @param types sensor's types, refer to {@link android.hardware.Sensor} for the supported list.
	 * @param listener the sensor listener to be registered.
	 * @param rate the listener sensitivity measured in milliseconds.
	 */
	public static void registerListener(int[] types, SensorEventListener listener, int rate)
	{
		for (int type : types) {
			registerListener(type, listener, rate);
		}
	}

	public static void registerListener(int type, SensorEventListener listener, int rate)
	{
		if (sensorManager == null) {
			sensorManager = (SensorManager) TiApplication.getInstance().getSystemService(Context.SENSOR_SERVICE);
		}

		Sensor sensor  = sensorManager.getDefaultSensor(type);
		if (sensor != null) {
			if (DBG) {
				Log.d(LCAT, "Enabling Listener: " + sensor.getName());
			}
			sensorManager.registerListener(listener, sensor, rate);
			listenerCount.incrementAndGet();
		} else {
			Log.e(LCAT, "unable to register, sensor is null");
		}
	}

	public static void unregisterListener(int[] types, SensorEventListener listener)
	{
		for (int type : types) {
			unregisterListener(type, listener);
		}
	}

	/**
	 * Attempts to unregister the listener. An error will be logged if unable to unregister.
	 * @param type the register's type, refer to {@link android.hardware.Sensor} for the supported list.
	 * @param listener the sensor listener.
	 */
	public static void unregisterListener(int type, SensorEventListener listener)
	{
		if (sensorManager != null) {
			Sensor sensor = sensorManager.getDefaultSensor(type);
			if (sensor != null) {
				if (DBG) {
					Log.d(LCAT, "Disabling Listener: " + sensor.getName());
				}
				sensorManager.unregisterListener(listener, sensor);

				if (listenerCount.decrementAndGet() == 0) {
					sensorManager = null;
				}
			} else {
				Log.e(LCAT, "unable to unregister, sensor is null");
			}
		} else {
			Log.e(LCAT, "unable to unregister, sensorManager is null");
		}
	}

	/**
	 * @param activity the referenced activity.
	 * @param type the sensor's type, refer to {@link android.hardware.Sensor} for the supported list.
	 * @return true if activity has a default sensor of the given type, false otherwise.
	 */
	public static boolean hasDefaultSensor(Activity activity, int type)
	{
		boolean oneShot = false;
		boolean result = false;

		if (sensorManager == null)
		{
			oneShot = true;
			sensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
		}
		if (sensorManager != null)
		{
			result = (sensorManager.getDefaultSensor(type) != null);
			if (oneShot) {
				sensorManager = null;
			}
		}

		return result;
	}

	public static SensorManager getSensorManager() {
		return sensorManager;
	}
}

