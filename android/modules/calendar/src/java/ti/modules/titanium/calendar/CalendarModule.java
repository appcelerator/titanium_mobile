/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-2016 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.calendar;

import java.util.ArrayList;

import android.provider.CalendarContract;
import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.KrollPromise;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.TiC;

import android.Manifest;
import android.app.Activity;
import android.os.Build;

@Kroll.module
public class CalendarModule extends KrollModule
{
	@Kroll.constant
	public static final int STATUS_TENTATIVE = EventProxy.STATUS_TENTATIVE;
	@Kroll.constant
	public static final int STATUS_CONFIRMED = EventProxy.STATUS_CONFIRMED;
	@Kroll.constant
	public static final int STATUS_CANCELED = EventProxy.STATUS_CANCELED;

	@Kroll.constant
	public static final int VISIBILITY_DEFAULT = EventProxy.VISIBILITY_DEFAULT;
	@Kroll.constant
	public static final int VISIBILITY_CONFIDENTIAL = EventProxy.VISIBILITY_CONFIDENTIAL;
	@Kroll.constant
	public static final int VISIBILITY_PRIVATE = EventProxy.VISIBILITY_PRIVATE;
	@Kroll.constant
	public static final int VISIBILITY_PUBLIC = EventProxy.VISIBILITY_PUBLIC;

	@Kroll.constant
	public static final int METHOD_ALERT = ReminderProxy.METHOD_ALERT;
	@Kroll.constant
	public static final int METHOD_DEFAULT = ReminderProxy.METHOD_DEFAULT;
	@Kroll.constant
	public static final int METHOD_EMAIL = ReminderProxy.METHOD_EMAIL;
	@Kroll.constant
	public static final int METHOD_SMS = ReminderProxy.METHOD_SMS;

	@Kroll.constant
	public static final int STATE_DISMISSED = AlertProxy.STATE_DISMISSED;
	@Kroll.constant
	public static final int STATE_FIRED = AlertProxy.STATE_FIRED;
	@Kroll.constant
	public static final int STATE_SCHEDULED = AlertProxy.STATE_SCHEDULED;

	//region recurrence frequency
	@Kroll.constant
	public static final int RECURRENCEFREQUENCY_DAILY = 0;
	@Kroll.constant
	public static final int RECURRENCEFREQUENCY_WEEKLY = 1;
	@Kroll.constant
	public static final int RECURRENCEFREQUENCY_MONTHLY = 2;
	@Kroll.constant
	public static final int RECURRENCEFREQUENCY_YEARLY = 3;
	//endregion

	//region attendee relationships
	@Kroll.constant
	public static final int RELATIONSHIP_ATTENDEE = CalendarContract.Attendees.RELATIONSHIP_ATTENDEE;
	@Kroll.constant
	public static final int RELATIONSHIP_NONE = CalendarContract.Attendees.RELATIONSHIP_NONE;
	@Kroll.constant
	public static final int RELATIONSHIP_ORGANIZER = CalendarContract.Attendees.RELATIONSHIP_ORGANIZER;
	@Kroll.constant
	public static final int RELATIONSHIP_PERFORMER = CalendarContract.Attendees.RELATIONSHIP_PERFORMER;
	@Kroll.constant
	public static final int RELATIONSHIP_SPEAKER = CalendarContract.Attendees.RELATIONSHIP_SPEAKER;
	@Kroll.constant
	public static final int RELATIONSHIP_UNKNOWN = 11001;
	//endregion

	//region attendee status
	@Kroll.constant
	public static final int ATTENDEE_STATUS_ACCEPTED = CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED;
	@Kroll.constant
	public static final int ATTENDEE_STATUS_DECLINED = CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED;
	@Kroll.constant
	public static final int ATTENDEE_STATUS_INVITED = CalendarContract.Attendees.ATTENDEE_STATUS_INVITED;
	@Kroll.constant
	public static final int ATTENDEE_STATUS_NONE = CalendarContract.Attendees.ATTENDEE_STATUS_NONE;
	@Kroll.constant
	public static final int ATTENDEE_STATUS_TENTATIVE = CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE;
	@Kroll.constant
	public static final int ATTENDEE_STATUS_UNKNOWN = 11001;
	//endregion

	//region attendee type
	@Kroll.constant
	public static final int ATTENDEE_TYPE_NONE = CalendarContract.Attendees.TYPE_NONE;
	@Kroll.constant
	public static final int ATTENDEE_TYPE_OPTIONAL = CalendarContract.Attendees.TYPE_OPTIONAL;
	@Kroll.constant
	public static final int ATTENDEE_TYPE_RESOURCE = CalendarContract.Attendees.TYPE_RESOURCE;
	@Kroll.constant
	public static final int ATTENDEE_TYPE_REQUIRED = CalendarContract.Attendees.TYPE_REQUIRED;
	@Kroll.constant
	public static final int ATTENDEE_TYPE_UNKNOWN = 11001;
	//endregion

	public static final String EVENT_LOCATION = "eventLocation";

	public CalendarModule()
	{
		super();
	}

	@Kroll.method
	public boolean hasCalendarPermissions()
	{
		return CalendarProxy.hasCalendarPermissions();
	}

	@Kroll.method
	public void requestCalendarPermissions(@Kroll.argument(optional = true) KrollFunction permissionCallback,
										   KrollPromise promise)
	{
		if (hasCalendarPermissions()) {
			return;
		}

		TiBaseActivity.registerPermissionRequestCallback(TiC.PERMISSION_CODE_CALENDAR, permissionCallback,
														 getKrollObject(), promise);
		Activity currentActivity = TiApplication.getInstance().getCurrentActivity();
		currentActivity.requestPermissions(
			new String[] { Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR },
			TiC.PERMISSION_CODE_CALENDAR);
	}

	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public CalendarProxy[] getAllCalendars()
	// clang-format on
	{
		ArrayList<CalendarProxy> calendars = CalendarProxy.queryCalendars(null, null);
		return calendars.toArray(new CalendarProxy[calendars.size()]);
	}

	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public CalendarProxy[] getSelectableCalendars()
	// clang-format on
	{
		ArrayList<CalendarProxy> calendars;
		// selectable calendars are "visible"
		if (Build.VERSION.SDK_INT >= 14) { // ICE_CREAM_SANDWICH, 4.0
			calendars = CalendarProxy.queryCalendars("Calendars.visible = ?", new String[] { "1" });
		}
		// selectable calendars are "selected"
		else if (Build.VERSION.SDK_INT >= 11) { // HONEYCOMB, 3.0
			calendars = CalendarProxy.queryCalendars("Calendars.selected = ?", new String[] { "1" });
		}
		// selectable calendars are "selected" && !"hidden"
		else {
			calendars = CalendarProxy.queryCalendars("Calendars.selected = ? AND Calendars.hidden = ?",
													 new String[] { "1", "0" });
		}
		return calendars.toArray(new CalendarProxy[calendars.size()]);
	}

	@Kroll.method
	public CalendarProxy getCalendarById(int id)
	{
		ArrayList<CalendarProxy> calendars =
			CalendarProxy.queryCalendars("Calendars._id = ?", new String[] { "" + id });

		if (calendars.size() > 0) {
			return calendars.get(0);
		} else {
			return null;
		}
	}

	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public AlertProxy[] getAllAlerts()
	// clang-format on
	{
		ArrayList<AlertProxy> alerts = AlertProxy.queryAlerts(null, null, null);
		return alerts.toArray(new AlertProxy[alerts.size()]);
	}

	@Override
	public String getApiName()
	{
		return "Ti.Calendar";
	}
}
