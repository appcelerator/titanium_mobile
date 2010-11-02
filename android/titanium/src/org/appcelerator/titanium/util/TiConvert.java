/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.util;

import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollObject;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.kroll.KrollCallback;
import org.appcelerator.titanium.view.TiCompositeLayout;
import org.appcelerator.titanium.view.TiCompositeLayout.LayoutParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.Function;

import android.graphics.drawable.ColorDrawable;
import android.net.Uri;

public class TiConvert
{
	private static final String LCAT = "TiConvert";
	private static final boolean DBG = TiConfig.LOGD;

	public static final String ASSET_URL = "file:///android_asset/"; // class scope on URLUtil

	// Bundle

	public static Object putInKrollDict(KrollDict d, String key, Object value)
	{
		if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Date) {
			d.put(key, value);
		} else if (value instanceof KrollDict) {
			KrollDict nd = new KrollDict();
			KrollDict dict = (KrollDict) value;
			for (String k : dict.keySet()) {
				putInKrollDict(nd, k, dict.get(k));
			}
			d.put(key, nd);
			value = nd;
		} else if (value instanceof Object[]) {
			Object[] a = (Object[]) value;
			int len = a.length;
			if (len > 0) {
				Object v = a[0];
				if (DBG) {
					if (v != null) {
						Log.w(LCAT, "Array member is type: " + v.getClass().getSimpleName());
					} else {
						Log.w(LCAT, "First member of array is null");
					}
				}
				if (v != null && v instanceof String) {
					String[] sa = new String[len];
					for(int i = 0; i < len; i++) {
						sa[i] = (String) a[i];
					}
					d.put(key, sa);
				} else if (v != null && v instanceof Double) {
					double[] da = new double[len];
					for(int i = 0; i < len; i++) {
						da[i] = (Double) a[i];
					}
					d.put(key, da);
				} else if (v != null && v instanceof KrollObject) {
					KrollProxy[] pa = new KrollProxy[len];
					for(int i = 0; i < len; i++) {
						KrollObject ko = (KrollObject) a[i];
						pa[i] = (KrollProxy) ko.getProxy();
					}
					d.put(key, pa);
				} else {

					Object[] oa = new Object[len];
					for(int i = 0; i < len; i++) {
						oa[i] = a[i];
					}
					d.put(key, oa);
					//throw new IllegalArgumentException("Unsupported array property type " + v.getClass().getSimpleName());
				}
			} else {
				d.put(key, (Object[]) value);
			}
		} else if (value == null) {
			d.put(key, null);
		} else if (value instanceof KrollProxy) {
			d.put(key, value);
		} else if (value instanceof KrollCallback || value instanceof Function) {
			d.put(key, value);
		} else if (value instanceof Map) {
			KrollDict dict = new KrollDict();
			Map<?,?> map = (Map<?,?>)value;
			Iterator<?> iter = map.keySet().iterator();
			while(iter.hasNext())
			{
				String k = (String)iter.next();
				putInKrollDict(dict,k,map.get(k));
			}
			d.put(key,dict);
		} else {
			throw new IllegalArgumentException("Unsupported property type " + value.getClass().getName());
		}

		return value;
	}
	// Color conversions
	public static int toColor(String value) {
		return TiColorHelper.parseColor(value);
	}
	public static int toColor(KrollDict d, String key) {
		return toColor(d.getString(key));
	}
	public static ColorDrawable toColorDrawable(String value) {
		return new ColorDrawable(toColor(value));
	}
	public static ColorDrawable toColorDrawable(KrollDict d, String key) {
		return toColorDrawable(d.getString(key));
	}

	// Layout
	public static boolean fillLayout(KrollDict d, LayoutParams layoutParams) {
		boolean dirty = false;
		Object width = null;
		Object height = null;
		if (d.containsKey("size")) {
			KrollDict size = (KrollDict)d.get("size");
			width = size.get("width");
			height = size.get("height");
		}
		if (d.containsKey("left")) {
			layoutParams.optionLeft = toTiDimension(d, "left").getIntValue();
			dirty = true;
		}
		if (d.containsKey("top")) {
			layoutParams.optionTop = toTiDimension(d, "top").getIntValue();
			dirty = true;
		}
		if (d.containsKey("right")) {
			layoutParams.optionRight = toTiDimension(d, "right").getIntValue();
			dirty = true;
		}
		if (d.containsKey("bottom")) {
			layoutParams.optionBottom = toTiDimension(d, "bottom").getIntValue();
			dirty = true;
		}
		if (width != null || d.containsKey("width")) {
			if (width == null)
			{
				width = d.get("width");
			}
			if (width == null || width.equals("auto")) {
				layoutParams.optionWidth = TiCompositeLayout.NOT_SET;
				layoutParams.autoWidth = true;
			} else {
				layoutParams.optionWidth = toTiDimension(width).getIntValue();
				layoutParams.autoWidth = false;
			}
			dirty = true;
		}
		if (height != null || d.containsKey("height")) {
			if (height == null)
			{
				height = d.get("height");
			}
			if (height == null || height.equals("auto")) {
				layoutParams.optionHeight = TiCompositeLayout.NOT_SET;
				layoutParams.autoHeight = true;
			} else {
				layoutParams.optionHeight = toTiDimension(height).getIntValue();
				layoutParams.autoHeight = false;
			}
			dirty = true;
		}
		if (d.containsKey("zIndex")) {
			Object zIndex = d.get("zIndex");
			if (zIndex != null) {
				layoutParams.optionZIndex = toInt(zIndex);
			} else {
				layoutParams.optionZIndex = 0;
			}
			dirty = true;
		}

		return dirty;
	}

	// Values

	public static boolean toBoolean(Object value)
	{
		if (value instanceof Boolean) {
			return (Boolean) value;
		} else if (value instanceof String) {
			return Boolean.parseBoolean(((String) value));
		} else {
			throw new IllegalArgumentException("Unable to convert " + value.getClass().getName() + " to boolean.");
		}
	}
	public static boolean toBoolean(KrollDict d, String key) {
		return toBoolean(d.get(key));
	}

	public static int toInt(Object value) {
		if (value instanceof Double) {
			return ((Double) value).intValue();
		} else if (value instanceof Integer) {
			return ((Integer) value);
		} else if (value instanceof String) {
			return Integer.parseInt((String) value);
		} else {
			throw new NumberFormatException("Unable to convert " + value.getClass().getName());
		}
	}
	public static int toInt(KrollDict d, String key) {
		return toInt(d.get(key));
	}

	public static float toFloat(Object value) {
		if (value instanceof Double) {
			return ((Double) value).floatValue();
		} else if (value instanceof Integer) {
			return ((Integer) value).floatValue();
		} else if (value instanceof String) {
			return Float.parseFloat((String) value);
		} else {
			throw new NumberFormatException("Unable to convert " + value.getClass().getName());
		}
	}
	public static float toFloat(KrollDict d, String key) {
		return toFloat(d.get(key));
	}

	public static double toDouble(Object value) {
		if (value instanceof Double) {
			return ((Double) value);
		} else if (value instanceof Integer) {
			return ((Integer) value).doubleValue();
		} else if (value instanceof String) {
			return Double.parseDouble((String) value);
		} else {
			throw new NumberFormatException("Unable to convert " + value.getClass().getName());
		}
	}
	public static double toDouble(KrollDict d, String key) {
		return toDouble(d.get(key));
	}

	public static String toString(Object value) {
		return value == null ? null : value.toString();
	}
	public static String toString(KrollDict d, String key) {
		return toString(d.get(key));
	}

	public static String[] toStringArray(Object[] parts) {
		String[] sparts = (parts != null ? new String[parts.length] : new String[0]);

		if (parts != null) {
			for (int i = 0; i < parts.length; i++) {
				sparts[i] = (String) parts[i];
			}
		}
		return sparts;
	}

	// Dimensions
	public static TiDimension toTiDimension(String value) {
		return new TiDimension(value);
	}

	public static TiDimension toTiDimension(Object value) {
		if (value instanceof Number) {
			value = value.toString() + "px";
		}
		return toTiDimension((String) value);
	}
	
	public static TiDimension toTiDimension(KrollDict d, String key) {
		return toTiDimension(d.get(key));
	}

	// URL
	public static String toURL(Uri uri)
	{
		//TODO handle Ti URLs.
		String url = null;
		if (uri.isRelative()) {
			url = uri.toString();
			if (url.startsWith("/")) {
				url = ASSET_URL + "Resources" + url.substring(1);
			} else {
				url = ASSET_URL + "Resources/" + url;
			}
		} else {
			url = uri.toString();
		}

		return url;
	}

	//Error
	public static KrollDict toErrorObject(int code, String msg) {
		KrollDict d = new KrollDict(1);
		KrollDict e = new KrollDict();
		e.put("code", code);
		e.put("message", msg);

		d.put("error", e);
		return d;
	}

	public static TiBlob toBlob(Object value) {
		return (TiBlob) value;
	}

	public static TiBlob toBlob(KrollDict object, String property) {
		return toBlob(object.get(property));
	}

	// JSON
	public static JSONObject toJSON(KrollDict data) {
		if (data == null) {
			return null;
		}
		JSONObject json = new JSONObject();

		for (String key : data.keySet()) {
			try {
				Object o = data.get(key);
				if (o == null) {
					json.put(key, JSONObject.NULL);
				} else if (o instanceof Number) {
					json.put(key, (Number) o);
				} else if (o instanceof String) {
					json.put(key, (String) o);
				} else if (o instanceof Boolean) {
					json.put(key, (Boolean) o);
				} else if (o instanceof KrollDict) {
					json.put(key, toJSON((KrollDict) o));
				} else if (o.getClass().isArray()) {
					json.put(key, toJSONArray((Object[]) o));
				} else {
					Log.w(LCAT, "Unsupported type " + o.getClass());
				}
			} catch (JSONException e) {
				Log.w(LCAT, "Unable to JSON encode key: " + key);
			}
		}

		return json;
	}

	public static JSONArray toJSONArray(Object[] a) {
		JSONArray ja = new JSONArray();
		for (Object o : a) {
			if (o == null) {
				if (DBG) {
					Log.w(LCAT, "Skipping null value in array");
				}
				continue;
			}
			if (o == null) {
				ja.put(JSONObject.NULL);
			} else if (o instanceof Number) {
				ja.put((Number) o);
			} else if (o instanceof String) {
				ja.put((String) o);
			} else if (o instanceof Boolean) {
				ja.put((Boolean) o);
			} else if (o instanceof KrollDict) {
				ja.put(toJSON((KrollDict) o));
			} else if (o.getClass().isArray()) {
				ja.put(toJSONArray((Object[]) o));
			} else {
				Log.w(LCAT, "Unsupported type " + o.getClass());
			}
    	}
    	return ja;
    }
    
    public static Date toDate(Object value) {
		if (value instanceof Date) {
			return (Date)value;
		} else if (value instanceof Number) {
			long millis = ((Number)value).longValue();
			return new Date(millis);
		}
		return null;
	}
	
	public static Date toDate(KrollDict d, String key) {
		return toDate(d.get(key));
	}
}
