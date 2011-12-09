(function(api){
	// Interfaces
	Ti._5.EventDriven(api);

	// Properties
	api.UNKNOWN_ERROR = 0;
	api.DEVICE_BUSY = 1;
	api.NO_CAMERA = 2;
	api.NO_VIDEO = 3;

	api.VIDEO_CONTROL_DEFAULT = 4;
	api.VIDEO_CONTROL_EMBEDDED = 5;
	api.VIDEO_CONTROL_FULLSCREEN = 6;
	api.VIDEO_CONTROL_NONE = 7;
	api.VIDEO_CONTROL_HIDDEN = 8;

	api.VIDEO_SCALING_NONE = 9;
	api.VIDEO_SCALING_ASPECT_FILL = 10;
	api.VIDEO_SCALING_ASPECT_FIT = 11;
	api.VIDEO_SCALING_MODE_FILL = 12;

	api.VIDEO_PLAYBACK_STATE_STOPPED = 13;
	api.VIDEO_PLAYBACK_STATE_PLAYING = 14;
	api.VIDEO_PLAYBACK_STATE_PAUSED = 15;

	api.VIDEO_LOAD_STATE_PLAYABLE = 16;
	api.VIDEO_LOAD_STATE_PLAYTHROUGH_OK = 17;
	api.VIDEO_LOAD_STATE_STALLED = 18;
	api.VIDEO_LOAD_STATE_UNKNOWN = 19;

	api.VIDEO_REPEAT_MODE_NONE = 20;
	api.VIDEO_REPEAT_MODE_ONE = 21;

	api.VIDEO_FINISH_REASON_PLAYBACK_ENDED = 22;
	api.VIDEO_FINISH_REASON_PLAYBACK_ERROR = 23;
	api.VIDEO_FINISH_REASON_USER_EXITED = 24;

	// Methods
	api.beep = function(){
		console.debug('Method "Titanium.Media.beep" is not implemented yet.');
	};
	api.createAudioPlayer = function(){
		console.debug('Method "Titanium.Media.createAudioPlayer" is not implemented yet.');
	};
	api.createAudioRecorder = function(){
		console.debug('Method "Titanium.Media.createAudioRecorder" is not implemented yet.');
	};
	api.createItem = function(){
		console.debug('Method "Titanium.Media.createItem" is not implemented yet.');
	};
	api.createMusicPlayer = function(){
		console.debug('Method "Titanium.Media.createMusicPlayer" is not implemented yet.');
	};
	api.createSound = function(){
		console.debug('Method "Titanium.Media.createSound" is not implemented yet.');
	};
	api.createVideoPlayer = function(args){
		return new Ti.Media.VideoPlayer(args);
	};
})(Ti._5.createClass('Titanium.Media'));