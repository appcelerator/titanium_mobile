/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

module.exports = new function() {
	var finish,
		valueOf,
		reportError,
		Tizen = require('tizen');

	this.init = function(testUtils) {
		finish = testUtils.finish;
		valueOf = testUtils.valueOf;
		reportError = testUtils.reportError;
	}

	this.name = 'calendar';
	this.tests = [
		{name: 'getEventCalendars'},
		{name: 'getTaskCalendars'},
		{name: 'getDefaultCalendar'},
		{name: 'createEvent'},
		{name: 'createTask'},
		{name: 'createEvents'},
		{name: 'createEventsBatch'},
		{name: 'updateEventsBatch'},
		{name: 'changeCallbacks'}
	]

	var finishError = function (testRun, errorMsg) {
		Ti.API.info('The following error occurred: ' +  errorMsg);

		reportError(testRun, 'The following error occurred: ' +  errorMsg);

		finish(testRun);
	};

	var createCalendarItem = function(testRun, calendarType) {
		Ti.API.info('Start to get default calendar.');

		var calendar = Tizen.Calendar.getDefaultCalendar(calendarType),
			startDate = new Date(),
			itemParams = {
				description: calendarType + ' 1',
				summary: 'Summary 1',
				startDate: startDate,
				duration: 3600000,
				location: 'Lviv'
			},
			filter,
			item;

		valueOf(testRun, startDate).shouldBeObject();
		valueOf(testRun, startDate instanceof Date).shouldBeTrue();		

		if (calendarType == 'EVENT') {
			Ti.API.info('Start to create Event.');

			item = Tizen.Calendar.createCalendarEvent(itemParams);

			valueOf(testRun, item).shouldBe('[object TizenCalendarCalendarEvent]');
		} else {
			Ti.API.info('Start to create Task.');

			item = Tizen.Calendar.createCalendarTask(itemParams);

			valueOf(testRun, item).shouldBe('[object TizenCalendarCalendarTask]');
		}

		valueOf(testRun, item.duration).shouldBeNumber();
		valueOf(testRun, item.startDate instanceof Date).shouldBeTrue();
		valueOf(testRun, calendar).shouldBe('[object TizenCalendarCalendarInstance]');
		valueOf(testRun, calendar.add).shouldBeFunction();
		valueOf(testRun, calendar.find).shouldBeFunction();
		valueOf(testRun, calendar.remove).shouldBeFunction();

		calendar.add(item);

		Ti.API.info(calendarType +' id:' + item.id + '; id.uid:' + item.id.uid + ' has been added');

		// Check item
		filter = Tizen.createAttributeFilter({
			attributeName: calendarType == 'EVENT' ? 'id.uid' : 'id',
			matchFlag: 'EXACTLY',
			matchValue: calendarType == 'EVENT' ? item.id.uid : item.id
		});

		valueOf(testRun, filter).shouldBe('[object TizenAttributeFilter]');		

		Ti.API.info('Start to find items.');

		calendar.find(
			function(items) {
				// Check that array is not empty
				if (!items || items.length == 0) {
					errorCB({message: 'Array items is empty'});

					return;
				}

				valueOf(testRun, items.length).shouldBe(1);
				valueOf(testRun, items[0]).shouldBe('[object TizenCalendarCalendarItem]');				

				Ti.API.info(calendarType + ' id:' + items[0].id + '; id.uid:' + items[0].id.uid + ' has been found');

				if (calendarType == 'TASK') {
					valueOf(testRun, items[0].id).shouldBeString();
					valueOf(testRun, items[0].id).shouldBe(item.id);
				}

				if (calendarType == 'EVENT') {
					valueOf(testRun, items[0].id).shouldBe('[object TizenCalendarCalendarEventId]');
					valueOf(testRun, items[0].id.uid).shouldBe(item.id.uid);
				}

				// Clear and finish
				calendar.remove(items[0].id);

				Ti.API.info('Finish test.');

				finish(testRun);
			},
			function(e) {
				Ti.API.error("In errorCB function. Type: " + e.type);

				valueOf(testRun, e).shouldBe('[object TizenWebAPIError]');

				finishError(testRun, e);
			},
			filter
		);
	};

	this.getEventCalendars = function(testRun) {
		function errorCB(e) {
			valueOf(testRun, e).shouldBe('[object TizenWebAPIError]');

			finishError(testRun,e);
		}

		function successCB(calendars) {
			Ti.API.info(calendars.length + " item found.");

			valueOf(testRun, calendars.length).shouldBeGreaterThan(0);

			for (var i = 0, len = calendars.length; i < len; i++) {				
				Ti.API.info('The calendar id:' + calendars[i].id + ', name:' + calendars[i].name + ', accountServiceId:' + calendars[i].accountServiceId);

				valueOf(testRun, calendars[i]).shouldBe('[object TizenCalendarCalendarInstance]');
				valueOf(testRun, calendars[i].id).shouldNotBeUndefined();
				valueOf(testRun, calendars[i].id).shouldNotBeNull();
			}

			finish(testRun);
		}

		valueOf(testRun, Tizen).shouldBeObject();
		valueOf(testRun, Tizen).shouldNotBeUndefined();

		Ti.API.info("Test getCalendars method for 'EVENT'.");

		Tizen.Calendar.getCalendars('EVENT', successCB, errorCB);
	};
	
	this.getTaskCalendars = function(testRun) {
		function errorCB(e) {
			valueOf(testRun, e).shouldBe('[object TizenWebAPIError]');

			finishError(testRun, e);
		}

		function successCB(calendars) {
			valueOf(testRun, calendars.length).shouldBeGreaterThan(0);

			for (var i = 0, len = calendars.length; i < len; i++) {
				Ti.API.info('The calendar id:' + calendars[i].id + ', name:' + calendars[i].name + ', accountServiceId:' + calendars[i].accountServiceId);

				valueOf(testRun, calendars[i]).shouldBe('[object TizenCalendarCalendarInstance]');
				valueOf(testRun, calendars[i].id).shouldNotBeUndefined();
				valueOf(testRun, calendars[i].id).shouldNotBeNull();
			}

			finish(testRun);
		}

		Ti.API.info("Test getCalendars method for 'TASK'.");

		Tizen.Calendar.getCalendars('TASK', successCB, errorCB);
	};
	
	this.getDefaultCalendar = function(testRun) {
		var tc = Tizen.Calendar.getDefaultCalendar('TASK'),
			ec = Tizen.Calendar.getDefaultCalendar('EVENT');

		valueOf(testRun, tc).shouldBe('[object TizenCalendarCalendarInstance]');
		valueOf(testRun, tc).shouldNotBeUndefined();
		valueOf(testRun, tc).shouldBeObject();
		valueOf(testRun, tc.id).shouldBeString();
		valueOf(testRun, tc.name).shouldBeString();

		Ti.API.info('The calendar id:' + tc.id + ', name:' + tc.name + ', accountServiceId:' + tc.accountServiceId);
	
		valueOf(testRun, ec).shouldBe('[object TizenCalendarCalendarInstance]');
		valueOf(testRun, ec).shouldNotBeUndefined();
		valueOf(testRun, ec).shouldBeObject();
		valueOf(testRun, ec.id).shouldBeString();
		valueOf(testRun, ec.name).shouldBeString();

		Ti.API.info('The calendar id:' + ec.id + ', name:' + ec.name + ', accountServiceId:' + ec.accountServiceId);

		finish(testRun);
	};

	this.createEvent = function(testRun) {
		return createCalendarItem(testRun, 'EVENT');
	}

	this.createTask = function(testRun) {
		return createCalendarItem(testRun, 'TASK');
	}

	this.createEvents = function(testRun) {
		var calendar = Tizen.Calendar.getDefaultCalendar('EVENT'),
			today = new Date(),
			h = today.getHours(),
			m = today.getMinutes(),
			dd = today.getDate(),
			mm = today.getMonth(),
			yy = today.getFullYear(),
			filter = Tizen.createAttributeFilter({
				attributeName: 'description',
				matchFlag: 'CONTAINS',
				matchValue: 'SuperEvent'
			}),
			sortingMode = Tizen.createSortMode({
				attributeName: 'summary',
				order: 'ASC'
			}),
			startDate = new Date(yy, mm, dd, h, m),
			ev = Tizen.Calendar.createCalendarEvent({
				description: 'SuperEvent...1',
				summary: 'Summary 1',
				startDate: startDate,
				duration: 3600000,
				location: 'Lviv'
			});

		function errorCB(e) {
			valueOf(testRun, e).shouldBe('[object TizenWebAPIError]');

			finishError(testRun, e);
		}
		
		function successCB() {
			Ti.API.info('Finish test.');

			finish(testRun);
		}

		valueOf(testRun, calendar).shouldBe('[object TizenCalendarCalendarInstance]');
		valueOf(testRun, filter).shouldBe('[object TizenAttributeFilter]');
		valueOf(testRun, sortingMode).shouldBe('[object TizenSortMode]');
		valueOf(testRun, startDate).shouldBeObject();
		valueOf(testRun, ev).shouldBe('[object TizenCalendarCalendarEvent]');
		valueOf(testRun, calendar.add).shouldBeFunction();

		calendar.add(ev);

		valueOf(testRun, ev.id).shouldNotBeUndefined();
		valueOf(testRun, ev.id.uid).shouldNotBeUndefined();

		Ti.API.info('event id:' + ev.id +'; ev.id.uid:' + ev.id.uid + 'has been added');

		startDate = new Date(yy, mm, dd, h+1, m);
		// Create event 2
		ev = Tizen.Calendar.createCalendarEvent({
			description: 'SuperEvent...2',
			summary: 'Summary 2',
			startDate: startDate,
			duration: 3600000,
			location: 'Lviv'
		});

		valueOf(testRun, calendar).shouldBe('[object TizenCalendarCalendarInstance]');
		valueOf(testRun, startDate).shouldBeObject();
		valueOf(testRun, ev).shouldBe('[object TizenCalendarCalendarEvent]');
		valueOf(testRun, calendar.find).shouldBeFunction();
		valueOf(testRun, calendar.removeBatch).shouldBeFunction();

		calendar.add(ev);

		valueOf(testRun, ev.id).shouldBe('[object TizenCalendarCalendarEventId]');
		valueOf(testRun, ev.id.uid).shouldNotBeUndefined();

		Ti.API.info('event id:' + ev.id + '; ev.id.uid:' + ev.id.uid + ' has been added');	

		calendar.find(
			function(events) {
				// Check that array is not empty
				if (!events || events.length == 0) {
					errorCB({message:'Array of events is empty'});

					return;
				}

				valueOf(testRun, events.length).shouldBeGreaterThan(1);
				
				var idEvents = [],
					i = 0,
					len = events.length;

				for(; i < len; i++) {
					valueOf(testRun, events[i]).shouldBe('[object TizenCalendarCalendarItem]');
					valueOf(testRun, events[i].id).shouldBe('[object TizenCalendarCalendarEventId]');
					valueOf(testRun, events[i].id.uid).shouldNotBeUndefined();
					valueOf(testRun, events[i].description).shouldContain('SuperEvent');

					Ti.API.info('event id:' + events[i].id +'; ev.id.uid:' + events[i].id.uid + ' has been found');

					idEvents.push(events[i].id);
				}

				// Clear events and finish	
				calendar.removeBatch(idEvents, successCB, errorCB);

				finish(testRun);
			},
			errorCB,
			filter,
			sortingMode
		);
	};
	
	this.createEventsBatch = function(testRun) {
		var calendar = Tizen.Calendar.getDefaultCalendar('EVENT'),
			today = new Date(),
			h = today.getHours(),
			m = today.getMinutes(),
			dd = today.getDate(),
			mm = today.getMonth(),
			yy = today.getFullYear(),
			ev1 = Tizen.Calendar.createCalendarEvent({
				description: 'Event 1',
				summary: 'Summary 1',
				startDate: new Date(yy, mm, dd, h, m),
				duration: 3600000,
				location: 'Lviv'
			}),
			ev2 = Tizen.Calendar.createCalendarEvent({
				description: 'Event 2',
				summary: 'Summary 2',
				startDate: new Date(yy, mm, dd, h+1, m),
				duration: 3600000,
				location: 'Lviv'
			});

		valueOf(testRun, calendar).shouldBe('[object TizenCalendarCalendarInstance]');
		valueOf(testRun, ev1).shouldBe('[object TizenCalendarCalendarEvent]');
		valueOf(testRun, ev2).shouldBe('[object TizenCalendarCalendarEvent]');

		function errorCB(e) {
			valueOf(testRun, e).shouldBe('[object TizenWebAPIError]');

			finishError(testRun, e);
		}

		function successCB() {
			Ti.API.info('Finish createEventsBatch test.');

			finish(testRun);
		}

		function addEventsBatchCB(events) {
			if (!events || events.length == 0) {
				errorCB({message:'Array of events is empty'});

				return;
			}

			valueOf(testRun, events.length).shouldBeGreaterThan(1);

			var idEvents = [],
				i = 0,
				len = events.length;

			for(; i < len; i++) {
				Ti.API.info('event id:' + events[i].id + '; ev.id.uid:' + events[i].id.uid + ' has been added');

				valueOf(testRun, events[i]).shouldBe('[object TizenCalendarCalendarItem]');
				valueOf(testRun, events[i].id).shouldBe('[object TizenCalendarCalendarEventId]');
				valueOf(testRun, events[i].id.uid).shouldNotBeUndefined();

				idEvents.push(events[i].id);
			}

			// Clear events and finish	
			calendar.removeBatch(idEvents, successCB, errorCB);
		}	

		// Create and addBatch
		calendar.addBatch([ev1, ev2], addEventsBatchCB, errorCB);
	};
	
	this.updateEventsBatch = function(testRun) {
		var calendar = Tizen.Calendar.getDefaultCalendar('EVENT'),
			today = new Date(),
			h = today.getHours(),
			m = today.getMinutes(),
			dd = today.getDate(),
			mm = today.getMonth(),
			yy = today.getFullYear(),
			filter,
			isUpdated;		

		function errorCB(e) {
			valueOf(testRun, e).shouldBe('[object TizenWebAPIError]');

			finishError(testRun, e);
		}
		
		function successCB() {
			Ti.API.info('Finish updateEventsBatch test.');

			finish(testRun);
		}
		
		function updateEventsCB() {
			// Find again
			calendar.find(findEventsCB, errorCB, filter);
		}
		
		function findEventsCB(events) {
			// Check that array is not empty
			if (!events || events.length == 0) {
				errorCB({message: 'Array events is empty'});
				return;
			}
			
			Ti.API.info('event ev.id.uid:' + events[0].id.uid + 'has been found, summary:' + events[0].summary);

			// Update or finish
			if (!isUpdated) {
				try {
					valueOf(testRun, events[0]).shouldBe('[object TizenCalendarCalendarItem]');

					events[0].summary = 'new summary 1';
					calendar.updateBatch([events[0]], updateEventsCB, errorCB);
					isUpdated = true;
				} catch (e) {
					finishError(testRun, e);
				}
			} else {
				// Check updated values
				valueOf(testRun, events[0].summary).shouldBe('new summary 1');

				// Clear and finish
				calendar.remove(events[0].id);

				finish(testRun);
			}
		}

		var ev = Tizen.Calendar.createCalendarEvent({
			description: 'Event 1',
			summary: 'Summary 1',
			startDate: new Date(yy, mm, dd, h, m),
			duration: 3600000,
			location: 'Lviv'
		});

		valueOf(testRun, calendar.add).shouldBeFunction();

		calendar.add(ev);

		Ti.API.info('event id:' + ev.id + '; ev.id.uid:' + ev.id.uid + 'has been added');

		// Find item
		filter = Tizen.createAttributeFilter({
			attributeName: 'id.uid',
			matchFlag: 'EXACTLY',
			matchValue: ev.id.uid
		});

		valueOf(testRun, ev).shouldBe('[object TizenCalendarCalendarEvent]');
		valueOf(testRun, ev.id).shouldBe('[object TizenCalendarCalendarEventId]');
		valueOf(testRun, ev.id.uid).shouldNotBeUndefined();

		valueOf(testRun, calendar).shouldBe('[object TizenCalendarCalendarInstance]');
		valueOf(testRun, filter).shouldBe('[object TizenAttributeFilter]');

		calendar.find(findEventsCB, errorCB, filter);
	};
	
	this.changeCallbacks = function(testRun) {
		function errorCB(e) {
			valueOf(testRun, e).shouldBe('[object TizenWebAPIError]');

			finishError(testRun, e.message);
		}
		
		function successCB() {
			finish(testRun);
		}
		
		var eventUID,
			today = new Date(),
			h = today.getHours(),
			m = today.getMinutes(),
			dd = today.getDate(),
			mm = today.getMonth(),
			yy = today.getFullYear(),
			calendar = Tizen.Calendar.getDefaultCalendar('EVENT'),
			checkEvent = function(eventName, items, callback) {
				Ti.API.info("eventName: " + eventName);

				var checkedEvent,
					i = 0,
					len = items.length;

				for (; i < len; i++) {
					Ti.API.info('event: ' + items[i] + '; event id:' + items[i].id + '; id.uid:' + items[i].id.uid + '; summary:' + items[i].summary);

					valueOf(testRun, items[i]).shouldBe('[object TizenCalendarCalendarItem]');
					valueOf(testRun, items[i].id).shouldBe('[object TizenCalendarCalendarEventId]');

					// Find our event
					if (eventUID == items[i].id.uid) {
						checkedEvent = items[i];

						break;
					}
				}

				if (checkedEvent) {
					valueOf(testRun, checkedEvent.summary).shouldBe(eventName);

					callback && callback(checkedEvent);
				}
			},
			watcher = {
				onitemsadded: function(items) {
					checkEvent('ADDED', items, function(event) {
						Ti.API.info('Item added');

						event.summary = 'UPDATED';
						calendar.update(event);
					});
				},
				onitemsupdated: function(items) {
					 checkEvent('UPDATED', items, function(event) {
						Ti.API.info('Item updated.');

						event.summary = 'REMOVED';
						calendar.remove(event.id);
					 });
				},
				onitemsremoved: function(ids) {
					if (ids.indexOf(eventUID)) {
						Ti.API.info('REMOVED: '+ eventUID);

						successCB();
					} else {
						errorCB({message: 'Cannot find ' + eventUID + ' in list of IDs'});
					}
				}
			};

		try {
			// Add Listener
			var watcherId = calendar.addChangeListener(watcher, errorCB),
				ev = Tizen.Calendar.createCalendarEvent({
					description: 'test events',
					summary: 'ADDED',
					startDate: new Date(yy, mm, dd, h, m),
					duration: 3600000,
					location: 'Lviv'
				});

			valueOf(testRun, calendar).shouldBe('[object TizenCalendarCalendarInstance]');
			valueOf(testRun, calendar.add).shouldBeFunction();
			valueOf(testRun, calendar.update).shouldBeFunction();
			valueOf(testRun, calendar.remove).shouldBeFunction();
			valueOf(testRun, ev).shouldBe('[object TizenCalendarCalendarEvent]');

			calendar.add(ev);

			valueOf(testRun, ev.id).shouldBe('[object TizenCalendarCalendarEventId]');
			valueOf(testRun, ev.id.uid).shouldNotBeUndefined();

			eventUID = ev.id.uid;
		} catch (e) {
			Ti.API.error("exception: " + e.message);

			errorCB(e);
		}
	};
}
