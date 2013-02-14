define(['Ti/_/declare', 'Ti/_/dom', 'Ti/_/event', 'Ti/_/lang', 'Ti/_/Evented'],
	function(declare, dom, event, lang, Evented) {
	
	// Ti.Media.Sound is based on tag <audio> of HTML5.
	// Ti.Media.Sound wraps the interface of the <audio> tag, adds basic
	// state management, error checking, and provides interface common for
	// Titanium namespaces.
	
	var on = require.on,
		mimeTypes = {
			'mp3': 'audio/mpeg',
			'ogg': 'audio/ogg',
			'wav': 'audio/wav'
		},
		urlRegExp = /.+\.([^\/\.]+?)$/,
		messageMap = [void 0, 'Aborted', 'Decode error', 'Network error', 'Unsupported format'],
		INITIALIZED = 1,
		PAUSED = 2,
		PLAYING = 3,
		STARTING = 4,
		STOPPED = 5,
		STOPPING = 6,
		ENDED = 9,
		ABORT = 10,
		ERROR = 11;
	
	return declare('Ti.Media.Sound', Evented, {
		
		constructor: function() {
			this._handles = [];
		},
		
		_currentState: STOPPED,
		
		// Update the state information;
		// fire external events according to changes of the internal state.
		_changeState: function(newState, msg) {
			var evt = {};
			this._currentState = newState;
			this.constants.__values__.playing = PLAYING === newState;
			this.properties.__values__.paused = PAUSED === newState;
			
			evt.src = this;
			switch (this._currentState) {
				case ENDED:
					evt.type = 'complete';
					evt.success = true;
					this.looping || this.fireEvent('complete', evt);  // external (interface) event
					break;
				case ERROR: 
					evt.type = 'error';	
					evt.message = msg;
					this.fireEvent('error', evt);  // external (interface) event
					break;
			}
		},
		
		_durationChange: function() {
			var d = this._audio.duration;
			// Blackberry OS 7 gives the initial duration as Infinity
			// So we leave duration at zero until the duration of <audio> is finite.
			d === Infinity || (this.constants.__values__.duration = Math.floor(d));
		},
		
		_error: function() {
			this._changeState(ERROR, 'error: ' + (messageMap[this._audio.error.code] || 'Unknown error'));
		},
		
		_createAudio: function(url) {
			var self = this,
				audio = self._audio = dom.create('audio'),
				i = 0, attr, match;
			
			// Handlers of events generated by the <audio> tag. 
			// These events are handled here and do not propagate outside.
			self._handles = [
				on(audio, 'playing', self, function() {
					self._changeState(PLAYING, 'playing');
				}),
				on(audio, 'play', self, function() {
					self._changeState(STARTING, 'starting');
				}),
				on(audio, 'pause', self, function() {
					self._currentState === STOPPING ? self._stop() : self._changeState(PAUSED, 'paused');
				}),
				on(audio, 'ended', self, function() {
					self._changeState(ENDED, 'ended');
				}),
				on(audio, 'abort', self, function() {
					self._changeState(ABORT, 'abort');
				}),
				on(audio, 'timeupdate', self, function() {
					self._currentState === STOPPING && self.pause();
				}),
				on(audio, 'error', self, '_error'),
				on(audio, 'canplay', self, function() {
					self._audio.volume = self.volume;
					self._audio.loop = self.looping;
					self._audio.currentTime = self.time / 1000;
					self._initialized = 1;
					
					//Audio has just initialized
					self._changeState(INITIALIZED, 'initialized');
					
					// _nextCmd: this variable records the command that was requested before the <audio> tag 
					// was initialized. It will be executed when the tag becomes initialized.
					self._nextCmd && self._nextCmd();
					self._nextCmd = 0;
				}),				
				on(audio, 'durationchange', self, '_durationChange')
			];
			
			document.body.appendChild(audio);
			
			//Set 'url' into tag <source> of tag <audio>
			require.is(url, 'Array') || (url = [url]);
			
			for (; i < url.length; i++) {
				attr = { src: url[i] };
				match = url[i].match(urlRegExp);
				match && mimeTypes[match[1]] && (attr.type = mimeTypes[match[1]]);
				dom.create('source', attr, audio);
			}

			return audio;
		},
		
		// Methods
		
		// Remove the <audio> tag from the DOM tree
		release: function() {
			var audio = this._audio,
				parent = audio && audio.parentNode,
				p = this.properties.__values__,
				c = this.constants.__values__;
				
			this._currentState = STOPPED;
			c.playing = p.paused = false;
			c.duration = p.url = this._initialized = this._nextCmd = 0;
			if (parent) {
				event.off(this._handles);
				parent.removeChild(audio);
			}
			this._audio = 0;
		},
		
		pause: function() {
			this._nextCmd = this.pause;
			this._initialized && this._currentState === PLAYING && this._audio.pause();
		},
		
		start: function() {
			this._nextCmd = this.start;
			this._initialized && this._currentState !== PLAYING && this._audio.play();
		},
		
		play: function() {
			this.start();
		},
		
		_stop: function() {
			var a = this._audio;
				
			a.currentTime = 0;
			this._changeState(STOPPED, 'stopped');

			// Some versions of Webkit has a bug: if <audio>'s current time is non-zero and we try to 
			// stop it by setting time to 0 and pausing, it won't work: music is paused, but time is 
			// not reset. This is a work around.
			a.currentTime === 0 || a.load();
		},
		
		stop: function() {
			this._nextCmd = 0;
			if (!this._initialized) {
				return;
			}
				
			var a = this._audio;
			if (!a) {
				return;
			}
				
			if (this._currentState === PAUSED) {
				this._stop();
			} else {
				this._changeState(STOPPING, 'stopping');
				a.pause();
			}
		},
		
		reset: function() {
			this.time = 0;
		},
		
		isLooping: function() {
				return this.looping;
		},
		
		isPaused: function() {
				return this.paused; 
		},
		
		isPlaying: function() {
				return this.playing;
		},
		
		constants: {
			playing: false,
			duration: 0
		},
		
		properties: {
		
			url: {
				set: function(value, oldValue) {
					if (!value || value === oldValue) {
						return oldValue;
					}
					this.release();
					this._createAudio(value);
					return value;
				}
			},

			// The following 3 properties mirror (cache) the according properties of the <audio> tag:
			// volume, time, looping.
			//
			// Reason: if the <audio> tag is not initialized, direct referencing of the tag's properties
			// leads to exception. To prevent this situation, we mirror the properties and use them
			// if the tag's properties cannot be accessed at the moment.

			volume: {
				value: 1.0,
				set: function(value) {
					value = Math.max(0, Math.min(1, value));
					this._initialized && (this._audio.volume = value);
					return value;
				}
			},

			time: {
				value: 0,
				get: function(value) {
					return this._initialized ? Math.floor(this._audio.currentTime * 1000) : value;
				},
				set: function(value) {
					this._initialized && (this._audio.currentTime = value / 1000);
					return value;
				}
			},
			
			looping: {
				value: false,
				set: function(value) {
					this._initialized && (this._audio.loop = value);
					return value;
				}
			},
			
			paused: {
				value: false,
				set: function(value, oldValue) {
					if (value === oldValue) {
						return oldValue;
					}
					value ? this.pause() : this.start();
					return oldValue;
				}
			}
		}
	});

});
