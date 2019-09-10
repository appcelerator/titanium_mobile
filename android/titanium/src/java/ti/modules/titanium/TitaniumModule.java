/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.KrollObject;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiMessenger;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiPlatformHelper;
import org.appcelerator.titanium.util.TiRHelper;
import org.appcelerator.titanium.util.TiUIHelper;

import android.app.Activity;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.SparseArray;
// clang-format off
@Kroll.module
@Kroll.topLevel({ "Ti", "Titanium" })
public class TitaniumModule extends KrollModule
// clang-format on
{
	private static final String TAG = "TitaniumModule";

	private static final int MSG_ALERT = KrollProxy.MSG_LAST_ID + 100;

	private Stack<String> basePath;
	private Map<String, NumberFormat> numberFormats =
		java.util.Collections.synchronizedMap(new HashMap<String, NumberFormat>());

	private static final SparseArray<Timer> activeTimers = new SparseArray<TitaniumModule.Timer>();
	private static int lastTimerId = 1;

	public TitaniumModule()
	{
		basePath = new Stack<String>();
	}

	@Override
	protected void initActivity(Activity activity)
	{
		super.initActivity(activity);
		basePath.push(getCreationUrl().baseUrl);
	}

	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public String getUserAgent()
	// clang-format on
	{
		StringBuilder builder = new StringBuilder();
		String httpAgent = System.getProperty("http.agent");
		if (httpAgent != null) {
			builder.append(httpAgent);
		}
		builder.append(" Titanium/").append(getVersion());
		return builder.toString();
	}

	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public String getVersion()
	// clang-format on
	{
		return TiApplication.getInstance().getTiBuildVersion();
	}

	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public String getBuildTimestamp()
	// clang-format on
	{
		return TiApplication.getInstance().getTiBuildTimestamp();
	}

	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public String getBuildDate()
	// clang-format on
	{
		return TiApplication.getInstance().getTiBuildTimestamp();
	}

	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public String getBuildHash()
	// clang-format on
	{
		return TiApplication.getInstance().getTiBuildHash();
	}

	// For testing exception handling.  Can remove after ticket 2032
	@Kroll.method
	public void testThrow()
	{
		throw new Error("Testing throwing throwables");
	}

	private class Timer implements Runnable
	{
		protected long timeout;
		protected boolean interval;
		protected Object[] args;
		protected KrollFunction callback;
		protected Handler handler;
		protected int id;
		protected boolean canceled;

		public Timer(int id, Handler handler, KrollFunction callback, long timeout, Object[] args, boolean interval)
		{
			this.id = id;
			this.handler = handler;
			this.callback = callback;
			this.timeout = timeout;
			this.args = args;
			this.interval = interval;
		}

		public void schedule()
		{
			handler.postDelayed(this, timeout);
		}

		public void run()
		{
			if (canceled) {
				return;
			}

			if (Log.isDebugModeEnabled()) {
				StringBuilder message = new StringBuilder("calling ")
											.append(interval ? "interval" : "timeout")
											.append(" timer ")
											.append(id)
											.append(" @")
											.append(new Date().getTime());

				Log.d(TAG, message.toString());
			}

			long start = System.currentTimeMillis();
			callback.call(getKrollObject(), args);

			// If this timer is repeating schedule it for another round.
			// Otherwise remove the timer from the active list and quit.
			if (interval && !canceled) {
				handler.postDelayed(this, timeout - (System.currentTimeMillis() - start));

			} else {
				activeTimers.remove(id);
			}
		}

		public boolean cancel()
		{
			long threadId = handler.getLooper().getThread().getId();
			if (Thread.currentThread().getId() != threadId) {
				return false;
			}
			handler.removeCallbacks(this);
			canceled = true;
			return true;
		}
	}

	private int createTimer(KrollFunction callback, Number timeout, Object[] args, boolean interval)
	{
		// Generate an unique identifier for this timer.
		// This will later be used by the developer to cancel a timer.
		final int timerId = lastTimerId++;

		if (timeout == null || timeout.longValue() < 1L) {
			timeout = 1L;
		}
		Timer timer = new Timer(timerId, getRuntimeHandler(), callback, timeout.longValue(), args, interval);
		activeTimers.append(timerId, timer);

		timer.schedule();

		return timerId;
	}

	private void cancelTimer(int timerId)
	{
		Timer timer = activeTimers.get(timerId);
		if (timer != null) {
			timer.cancel();
			activeTimers.remove(timerId);
		}
	}

	public static void cancelTimers()
	{
		int timerCount = activeTimers.size();
		for (int i = 0; i < timerCount; i++) {
			Timer timer = activeTimers.valueAt(i);
			if (timer.cancel()) {
				activeTimers.removeAt(i);
				timerCount--;
				i--;
			};
		}
	}

	// clang-format off
	@Kroll.method
	@Kroll.topLevel
	public int setTimeout(KrollFunction krollFunction, Object[] args)
	// clang-format on
	{
		Number timeout = null;
		if (args != null && args.length > 0) {
			timeout = (Number) args[0];
			args = Arrays.copyOfRange(args, 1, args.length);
		}
		return createTimer(krollFunction, timeout, args, false);
	}

	// clang-format off
	@Kroll.method
	@Kroll.topLevel
	public int setInterval(KrollFunction krollFunction, Object[] args)
	// clang-format on
	{
		Number timeout = null;
		if (args != null && args.length > 0) {
			timeout = (Number) args[0];
			args = Arrays.copyOfRange(args, 1, args.length);
		}
		return createTimer(krollFunction, timeout, args, true);
	}

	// clang-format off
	@Kroll.method
	@Kroll.topLevel
	public void clearTimeout(int timerId)
	// clang-format on
	{
		cancelTimer(timerId);
	}

	// clang-format off
	@Kroll.method
	@Kroll.topLevel
	public void clearInterval(int timerId)
	// clang-format on
	{
		cancelTimer(timerId);
	}

	// clang-format off
	@Kroll.method
	@Kroll.topLevel
	public void alert(Object message)
	// clang-format on
	{
		String msg = (message == null ? null : message.toString());

		if (TiApplication.isUIThread()) {
			TiUIHelper.doOkDialog("", msg, null);
		} else {
			getMainHandler().obtainMessage(MSG_ALERT, msg).sendToTarget();
		}
	}

	// clang-format off
	@Kroll.method
	@Kroll.topLevel("String.format")
	public String stringFormat(String format, Object args[])
	// clang-format on
	{
		try {

			// in case someone passes an iphone formatter symbol, convert
			format = format.replaceAll("%@", "%s");

			if (args.length == 0) {
				return String.format(format);

			} else {
				return String.format(format, args);
			}

		} catch (Exception ex) {
			Log.e(TAG, "Error occured while formatting string", ex);
			return null;
		}
	}

	// clang-format off
	@Kroll.method
	@Kroll.topLevel("String.formatDate")
	public String stringFormatDate(Object date, @Kroll.argument(optional = true) String format)
	// clang-format on
	{
		int style = DateFormat.SHORT;

		if (format != null) {
			if (format.equals("medium")) {
				style = DateFormat.MEDIUM;

			} else if (format.equals("long")) {
				style = DateFormat.LONG;
			} else if (format.equals("full")) {
				style = DateFormat.FULL;
			}
		}
		if (date instanceof Date) {
			return (DateFormat.getDateInstance(style)).format(date);
		} else {
			Log.e(TAG, "The string.formatDate() function was given an invalid argument. Must be of type 'Date'.");
			return null;
		}
	}

	// clang-format off
	@Kroll.method
	@Kroll.topLevel("String.formatTime")
	public String stringFormatTime(Object time)
	// clang-format on
	{
		int style = DateFormat.SHORT;

		if (time instanceof Date) {
			try {
				return (DateFormat.getTimeInstance(style)).format(time);
			} catch (Exception ex) {
				Log.e(TAG, "Error occurred while formatting time", ex);
				return null;
			}
		} else {
			Log.e(TAG, "The string.formatTime() function was given an invalid argument. Must be of type 'Date'.");
			return null;
		}
	}

	// clang-format off
	@Kroll.method
	@Kroll.topLevel("String.formatCurrency")
	public String stringFormatCurrency(double currency)
	// clang-format on
	{
		return NumberFormat.getCurrencyInstance().format(currency);
	}

	// clang-format off
	@Kroll.method
	@Kroll.topLevel("String.formatDecimal")
	public String stringFormatDecimal(Object args[])
	// clang-format on
	{
		String pattern = null;
		String locale = null;

		if (args.length == 2) {
			// Is the second argument a locale string or a format string?
			String test = TiConvert.toString(args[1]);
			if (test != null && test.length() > 0) {
				if (test.contains(".") || test.contains("#") || test.contains("0")) {
					pattern = test;

				} else {
					locale = test;
				}
			}

		} else if (args.length >= 3) {
			// this is: stringFormatDecimal(n, locale_string, pattern_string);
			locale = TiConvert.toString(args[1]);
			pattern = TiConvert.toString(args[2]);
		}

		String key = (locale == null ? "" : locale) + " keysep " + (pattern == null ? "" : pattern);

		NumberFormat format;
		if (numberFormats.containsKey(key)) {
			format = numberFormats.get(key);

		} else {
			if (locale != null) {
				format = NumberFormat.getInstance(TiPlatformHelper.getInstance().getLocale(locale));

			} else {
				format = NumberFormat.getInstance();
			}

			if (pattern != null && format instanceof DecimalFormat) {
				((DecimalFormat) format).applyPattern(pattern);
			}

			numberFormats.put(key, format);
		}

		return format.format((Number) args[0]);
	}

	@Kroll.method
	public String localize(Object args[])
	{
		String key = (String) args[0];
		String defaultValue = args.length > 1 ? (String) args[1] : null;

		try {
			int resid = TiRHelper.getResource("string." + key);
			if (resid != 0) {
				return TiApplication.getInstance().getString(resid);

			} else {
				return defaultValue;
			}

		} catch (TiRHelper.ResourceNotFoundException e) {
			if (Log.isDebugModeEnabled()) {
				Log.d(TAG, "Resource string with key '" + key + "' not found.  Returning default value.",
					  Log.DEBUG_MODE);
			}

			return defaultValue;

		} catch (Exception e) {
			Log.e(TAG, "Exception trying to localize string '" + key + "': ", e);

			return defaultValue;
		}
	}

	@Kroll.method
	public void dumpCoverage()
	{
		TiApplication app = TiApplication.getInstance();
		if (app == null || !app.isCoverageEnabled()) {
			Log.w(TAG, "Coverage is not enabled, no coverage data will be generated");

			return;
		}

		try {
			File extStorage = Environment.getExternalStorageDirectory();
			File reportFile = new File(new File(extStorage, app.getPackageName()), "coverage.json");
			FileOutputStream reportOut = new FileOutputStream(reportFile);
			// TODO KrollCoverage.writeCoverageReport(reportOut);
			reportOut.close();

		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	@Override
	public boolean handleMessage(Message msg)
	{
		switch (msg.what) {
			case MSG_ALERT: {
				TiUIHelper.doOkDialog("Alert", (String) msg.obj, null);

				return true;
			}
		}

		return super.handleMessage(msg);
	}

	@Override
	public String getApiName()
	{
		return "Ti";
	}
}
