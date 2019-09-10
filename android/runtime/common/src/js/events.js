// Copyright Joyent, Inc. and other Node contributors.

// Permission is hereby granted, free of charge, to any person obtaining a
// copy of this software and associated documentation files (the
// "Software"), to deal in the Software without restriction, including
// without limitation the rights to use, copy, modify, merge, publish,
// distribute, sublicense, and/or sell copies of the Software, and to permit
// persons to whom the Software is furnished to do so, subject to the
// following conditions:

// The above copyright notice and this permission notice shall be included
// in all copies or substantial portions of the Software.

// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
// OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
// NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
// DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
// OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
// USE OR OTHER DEALINGS IN THE SOFTWARE.

// Modifications Copyright 2011-Present Appcelerator, Inc.
'use strict';

var TAG = 'EventEmitter';
var EventEmitter = exports.EventEmitter = kroll.EventEmitter;
var isArray = Array.isArray;

// By default EventEmitters will print a warning if more than
// 10 listeners are added to it. This is a useful default which
// helps finding memory leaks.

Object.defineProperty(EventEmitter.prototype, 'callHandler', {
	value: function (handler, type, data) {
		// kroll.log(TAG, "calling event handler: type:" + type + ", data: " + data + ", handler: " + handler);

		var handled = false,
			cancelBubble = data.cancelBubble,
			event;

		if (handler.listener && handler.listener.call) {
			// Create event object, copy any custom event data, and set the "type" and "source" properties.
			event = { type: type, source: this };
			kroll.extend(event, data);

			if (handler.self && (event.source == handler.self.view)) { // eslint-disable-line eqeqeq
				event.source = handler.self;
			}

			handler.listener.call(this, event);

			// The "cancelBubble" property may be reset in the handler.
			if (event.cancelBubble !== cancelBubble) {
				cancelBubble = event.cancelBubble;
			}

			handled = true;

		} else if (kroll.DBG) {
			kroll.log(TAG, 'handler for event \'' + type + '\' is ' + (typeof handler.listener) + ' and cannot be called.');
		}

		// Bubble the events to the parent view if needed.
		if (data.bubbles && !cancelBubble && typeof this._fireEventToParent === 'function') {
			handled = this._fireEventToParent(type, data) || handled;
		}

		return handled;
	},
	enumerable: false
});

Object.defineProperty(EventEmitter.prototype, 'emit', {
	value: function (type) {
		var handled = false,
			data = arguments[1],
			handler,
			listeners;

		// Set the "bubbles" and "cancelBubble" properties for event data.
		if (data !== null && typeof data === 'object') {
			data.bubbles = !!data.bubbles;
			data.cancelBubble = !!data.cancelBubble;

		} else {
			data = { bubbles: false, cancelBubble: false };
		}

		if (this._hasJavaListener && typeof this._onEventFired === 'function') {
			this._onEventFired(type,  data);
		}

		if (!this._events || !this._events[type] || !this.callHandler) {
			if (data.bubbles && !data.cancelBubble && typeof this._fireEventToParent === 'function') {
				handled = this._fireEventToParent(type, data);
			}
			return handled;
		}

		handler = this._events[type];
		if (typeof handler.listener === 'function') {
			handled = this.callHandler(handler, type, data);

		} else if (isArray(handler)) {
			listeners = handler.slice();
			for (var i = 0, l = listeners.length; i < l; i++) {
				handled = this.callHandler(listeners[i], type, data) || handled;
			}

		} else if (data.bubbles && !data.cancelBubble && typeof this._fireEventToParent === 'function') {
			handled = this._fireEventToParent(type, data);
		}

		return handled;
	},
	enumerable: false
});

// Titanium compatibility
Object.defineProperty(EventEmitter.prototype, 'fireEvent', {
	value: EventEmitter.prototype.emit,
	enumerable: false,
	writable: true
});

Object.defineProperty(EventEmitter.prototype, 'fireSyncEvent', {
	value: EventEmitter.prototype.emit,
	enumerable: false
});

// EventEmitter is defined in src/node_events.cc
// EventEmitter.prototype.emit() is also defined there.
Object.defineProperty(EventEmitter.prototype, 'addListener', {
	value: function (type, listener, view) {
		if (typeof listener !== 'function') {
			throw new Error('addListener only takes instances of Function. The listener for event "' + type + '" is "' + (typeof listener) + '"');
		}

		if (!this._events) {
			this._events = {};
		}

		var id;

		// Setup ID first so we can pass count in to "listenerAdded"
		if (!this._events[type]) {
			id = 0;
		} else if (isArray(this._events[type])) {
			id = this._events[type].length;
		} else {
			id = 1;
		}

		var listenerWrapper = {};
		listenerWrapper.listener = listener;
		listenerWrapper.self = view;

		if (!this._events[type]) {
			// Optimize the case of one listener. Don't need the extra array object.
			this._events[type] = listenerWrapper;
		} else if (isArray(this._events[type])) {
			// If we've already got an array, just append.
			this._events[type].push(listenerWrapper);
		} else {
			// Adding the second element, need to change to array.
			this._events[type] = [ this._events[type], listenerWrapper ];
		}

		// Notify the Java proxy if this is the first listener added.
		if (id === 0 && typeof this._hasListenersForEventType === 'function') {
			this._hasListenersForEventType(type, true);
		}

		return id;
	},
	enumerable: false
});

// The JavaObject prototype will provide a version of this
// that delegates back to the Java proxy. Non-Java versions
// of EventEmitter don't care, so this no op is called instead.
Object.defineProperty(EventEmitter.prototype, '_listenerForEvent', {
	value: function () {},
	enumerable: false
});

Object.defineProperty(EventEmitter.prototype, 'on', {
	value: EventEmitter.prototype.addListener,
	enumerable: false
});

// Titanium compatibility
Object.defineProperty(EventEmitter.prototype, 'addEventListener', {
	value: EventEmitter.prototype.addListener,
	enumerable: false,
	writable: true
});

Object.defineProperty(EventEmitter.prototype, 'once', {
	value: function (type, listener) {
		var self = this;
		function g() {
			self.removeListener(type, g);
			listener.apply(this, arguments);
		}

		g.listener = listener;
		self.on(type, g);

		return this;
	},
	enumerable: false
});

Object.defineProperty(EventEmitter.prototype, 'removeListener', {
	value: function (type, listener) {
		if (typeof listener !== 'function') {
			throw new Error('removeListener only takes instances of Function');
		}

		// does not use listeners(), so no side effect of creating _events[type]
		if (!this._events || !this._events[type]) {
			return this;
		}

		var list = this._events[type];
		var count = 0;

		if (isArray(list)) {
			var position = -1;
			// Also support listener indexes / ids
			if (typeof listener === 'number') {
				position = listener;
				if (position > list.length || position < 0) {
					return this;
				}
			} else {
				for (var i = 0, length = list.length; i < length; i++) {
					if (list[i].listener === listener) {
						position = i;
						break;
					}
				}
			}

			if (position < 0) {
				return this;
			}

			list.splice(position, 1);

			if (list.length === 0) {
				delete this._events[type];
			}

			count = list.length;

		} else if (list.listener === listener || listener == 0) { // eslint-disable-line eqeqeq
			delete this._events[type];

		} else {
			return this;
		}

		if (count === 0 && typeof this._hasListenersForEventType === 'function') {
			this._hasListenersForEventType(type, false);
		}

		return this;
	},
	enumerable: false
});

Object.defineProperty(EventEmitter.prototype, 'removeEventListener', {
	value: EventEmitter.prototype.removeListener,
	enumerable: false,
	writable: true
});

Object.defineProperty(EventEmitter.prototype, 'removeAllListeners', {
	value: function (type) {
		// does not use listeners(), so no side effect of creating _events[type]
		if (type && this._events && this._events[type]) {
			this._events[type] = null;
			if (typeof this._hasListenersForEventType === 'function') {
				this._hasListenersForEventType(type, false);
			}
		}
		return this;
	},
	enumerable: false
});

Object.defineProperty(EventEmitter.prototype, 'listeners', {
	value: function (type) {
		if (!this._events) {
			this._events = {};
		}
		if (!this._events[type]) {
			this._events[type] = [];
		}
		if (!isArray(this._events[type])) {
			this._events[type] = [ this._events[type] ];
		}
		return this._events[type];
	},
	enumerable: false
});
