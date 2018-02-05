/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2018 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#ifndef TiMediaTypes_h
#define TiMediaTypes_h

#pragma mark VideoPlayer

typedef NS_ENUM(NSInteger, VideoTimeOption) {
  VideoTimeOptionNearestKeyFrame = 0,
  VideoTimeOptionExact,
};

typedef NS_ENUM(NSInteger, VideoRepeatMode) {
  VideoRepeatModeNone = 0,
  VideoRepeatModeOne,
};

typedef NS_ENUM(NSInteger, TiVideoPlayerPlaybackState) {
  TiVideoPlayerPlaybackStateUnknown = -1,
  TiVideoPlayerPlaybackStateStopped,
  TiVideoPlayerPlaybackStatePlaying,
  TiVideoPlayerPlaybackStatePaused,
  TiVideoPlayerPlaybackStateInterrupted,
  TiVideoPlayerPlaybackStateSeekingForward, // Not supported so far
  TiVideoPlayerPlaybackStateSeekingBackward, // Not supported so far
};

#pragma mark AudioRecorder

typedef enum {
  RecordStarted = 0,
  RecordStopped = 1,
  RecordPaused = 2
} RecorderState;

#endif /* TiMediaTypes_h */
