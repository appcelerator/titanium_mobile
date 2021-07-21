/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2018 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_MEDIA

#if defined(USE_TI_MEDIAGETAPPMUSICPLAYER) || defined(USE_TI_MEDIAOPENMUSICLIBRARY) || defined(USE_TI_MEDIAAPPMUSICPLAYER) || defined(USE_TI_MEDIAGETSYSTEMMUSICPLAYER) || defined(USE_TI_MEDIASYSTEMMUSICPLAYER) || defined(USE_TI_MEDIAHASMUSICLIBRARYPERMISSIONS)
#import <MediaPlayer/MediaPlayer.h>
#endif
#if IS_SDK_IOS_14 && defined(USE_TI_MEDIAOPENPHOTOGALLERY)
#import <PhotosUI/PHPicker.h>
#endif
#import "TiMediaAudioSession.h"
#import "TiMediaMusicPlayer.h"
#import "TiMediaTypes.h"
#import <TitaniumKit/KrollCallback.h>
#import <TitaniumKit/TiModule.h>
#import <TitaniumKit/TiViewProxy.h>

@class AVAudioRecorder;
@interface MediaModule : TiModule <
                             UINavigationControllerDelegate,
#if defined(USE_TI_MEDIASHOWCAMERA) || defined(USE_TI_MEDIAOPENPHOTOGALLERY) || defined(USE_TI_MEDIASTARTVIDEOEDITING)
                             UIImagePickerControllerDelegate,
#endif
#if IS_SDK_IOS_14 && defined(USE_TI_MEDIAOPENPHOTOGALLERY)
                             PHPickerViewControllerDelegate,
#endif
#ifdef USE_TI_MEDIAOPENMUSICLIBRARY
                             MPMediaPickerControllerDelegate,
#endif
#if defined(USE_TI_MEDIASHOWCAMERA) || defined(USE_TI_MEDIAOPENPHOTOGALLERY) || defined(USE_TI_MEDIASTARTVIDEOEDITING)
                             UIVideoEditorControllerDelegate,
#endif
                             UIPopoverControllerDelegate,
                             UIPopoverPresentationControllerDelegate,
                             UIAdaptivePresentationControllerDelegate> {
  @private
// Camera picker
#if defined(USE_TI_MEDIASHOWCAMERA) || defined(USE_TI_MEDIAOPENPHOTOGALLERY) || defined(USE_TI_MEDIASTARTVIDEOEDITING)
  UIImagePickerController *picker;
#endif
#if IS_SDK_IOS_14 && defined(USE_TI_MEDIAOPENPHOTOGALLERY)
  PHPickerViewController *_phPicker;
  BOOL excludeLivePhoto;
#endif
  BOOL autoHidePicker;
  BOOL saveToRoll;

// Music picker
#ifdef USE_TI_MEDIAOPENMUSICLIBRARY
  MPMediaPickerController *musicPicker;
#endif

// Music players
#if defined(USE_TI_MEDIAGETAPPMUSICPLAYER) || defined(USE_TI_MEDIAAPPMUSICPLAYER) || defined(USE_TI_MEDIAGETSYSTEMMUSICPLAYER) || defined(USE_TI_MEDIASYSTEMMUSICPLAYER)
  TiMediaMusicPlayer *systemMusicPlayer;
  TiMediaMusicPlayer *appMusicPlayer;
#endif

  // Shared picker bits; OK, since they're modal (and we can perform sanity checks for the necessary bits)
  BOOL animatedPicker;
  KrollCallback *pickerSuccessCallback;
  KrollCallback *pickerErrorCallback;
  KrollCallback *pickerCancelCallback;

  TiViewProxy *cameraView;

#if defined(USE_TI_MEDIASHOWCAMERA) || defined(USE_TI_MEDIAOPENPHOTOGALLERY) || defined(USE_TI_MEDIASTARTVIDEOEDITING)
  UIVideoEditorController *editor;
#endif
  KrollCallback *editorSuccessCallback;
  KrollCallback *editorErrorCallback;
  KrollCallback *editorCancelCallback;
  UIPopoverArrowDirection arrowDirection;

  AVAudioRecorder *_microphoneRecorder;
  NSTimer *_microphoneTimer;
}

#if defined(USE_TI_MEDIAOPENMUSICLIBRARY) || defined(USE_TI_MEDIAQUERYMUSICLIBRARY) || defined(USE_TI_MEDIASYSTEMMUSICPLAYER) || defined(USE_TI_MEDIAAPPMUSICPLAYER) || defined(USE_TI_MEDIAGETSYSTEMMUSICPLAYER) || defined(USE_TI_MEDIAGETAPPMUSICPLAYER)
+ (NSDictionary *)itemProperties;
+ (NSDictionary *)filterableItemProperties;
#endif

@property (nonatomic, readwrite, retain) id popoverView;
@property (nonatomic, readonly) NSNumber *volume;
@property (nonatomic, readonly) NSNumber *peakMicrophonePower;
@property (nonatomic, readonly) NSNumber *averageMicrophonePower;
@property (nonatomic, readonly) NSDictionary *currentRoute;
@property (nonatomic, readonly) NSNumber *audioPlaying;
@property (nonatomic, readonly) NSNumber *isCameraSupported;
@property (nonatomic, assign) NSString *audioSessionCategory;

#if defined(USE_TI_MEDIAGETAPPMUSICPLAYER) || defined(USE_TI_MEDIAAPPMUSICPLAYER) || defined(USE_TI_MEDIAGETSYSTEMMUSICPLAYER) || defined(USE_TI_MEDIASYSTEMMUSICPLAYER)
@property (nonatomic, readonly) TiMediaMusicPlayer *systemMusicPlayer;
@property (nonatomic, readonly) TiMediaMusicPlayer *appMusicPlayer;
#endif

@property (nonatomic, readonly) NSNumber *UNKNOWN_ERROR;
@property (nonatomic, readonly) NSNumber *DEVICE_BUSY;
@property (nonatomic, readonly) NSNumber *NO_CAMERA;
@property (nonatomic, readonly) NSNumber *NO_VIDEO;
@property (nonatomic, readonly) NSNumber *NO_MUSIC_PLAYER;

@property (nonatomic, readonly) NSString *VIDEO_SCALING_RESIZE;
@property (nonatomic, readonly) NSString *VIDEO_SCALING_RESIZE_ASPECT;
@property (nonatomic, readonly) NSString *VIDEO_SCALING_RESIZE_ASPECT_FILL;

@property (nonatomic, readonly) NSNumber *QUALITY_HIGH;
@property (nonatomic, readonly) NSNumber *QUALITY_MEDIUM;
@property (nonatomic, readonly) NSNumber *QUALITY_LOW;
@property (nonatomic, readonly) NSNumber *QUALITY_640x480;
@property (nonatomic, readonly) NSNumber *QUALITY_IFRAME_1280x720;
@property (nonatomic, readonly) NSNumber *QUALITY_IFRAME_960x540;

@property (nonatomic, readonly) NSArray *availableCameraMediaTypes;
@property (nonatomic, readonly) NSArray *availablePhotoMediaTypes;
@property (nonatomic, readonly) NSArray *availablePhotoGalleryMediaTypes;

@property (nonatomic, readonly) NSNumber *CAMERA_FRONT;
@property (nonatomic, readonly) NSNumber *CAMERA_REAR;
@property (nonatomic, readonly) NSArray *availableCameras;

@property (nonatomic, readonly) NSNumber *CAMERA_FLASH_OFF;
@property (nonatomic, readonly) NSNumber *CAMERA_FLASH_AUTO;
@property (nonatomic, readonly) NSNumber *CAMERA_FLASH_ON;

@property (nonatomic, readonly) NSNumber *CAMERA_AUTHORIZATION_AUTHORIZED;
@property (nonatomic, readonly) NSNumber *CAMERA_AUTHORIZATION_DENIED;
@property (nonatomic, readonly) NSNumber *CAMERA_AUTHORIZATION_RESTRICTED;

@property (nonatomic, readonly) NSString *MEDIA_TYPE_VIDEO;
@property (nonatomic, readonly) NSString *MEDIA_TYPE_PHOTO;
@property (nonatomic, readonly) NSString *MEDIA_TYPE_LIVEPHOTO;

#ifdef USE_TI_MEDIAAUDIORECORDER
@property (nonatomic, readonly) NSNumber *AUDIO_FORMAT_LINEAR_PCM;
@property (nonatomic, readonly) NSNumber *AUDIO_FORMAT_ULAW;
@property (nonatomic, readonly) NSNumber *AUDIO_FORMAT_ALAW;
@property (nonatomic, readonly) NSNumber *AUDIO_FORMAT_IMA4;
@property (nonatomic, readonly) NSNumber *AUDIO_FORMAT_ILBC;
@property (nonatomic, readonly) NSNumber *AUDIO_FORMAT_APPLE_LOSSLESS;
@property (nonatomic, readonly) NSNumber *AUDIO_FORMAT_AAC;

@property (nonatomic, readonly) NSNumber *AUDIO_FILEFORMAT_WAVE;
@property (nonatomic, readonly) NSNumber *AUDIO_FILEFORMAT_AIFF;
@property (nonatomic, readonly) NSNumber *AUDIO_FILEFORMAT_MP3;
@property (nonatomic, readonly) NSNumber *AUDIO_FILEFORMAT_MP4;
@property (nonatomic, readonly) NSNumber *AUDIO_FILEFORMAT_MP4A;
@property (nonatomic, readonly) NSNumber *AUDIO_FILEFORMAT_CAF;
@property (nonatomic, readonly) NSNumber *AUDIO_FILEFORMAT_3GPP;
@property (nonatomic, readonly) NSNumber *AUDIO_FILEFORMAT_3GP2;
@property (nonatomic, readonly) NSNumber *AUDIO_FILEFORMAT_AMR;
#endif

@property (nonatomic, readonly) NSString *AUDIO_SESSION_CATEGORY_AMBIENT;
@property (nonatomic, readonly) NSString *AUDIO_SESSION_CATEGORY_SOLO_AMBIENT;
@property (nonatomic, readonly) NSString *AUDIO_SESSION_CATEGORY_PLAYBACK;
@property (nonatomic, readonly) NSString *AUDIO_SESSION_CATEGORY_RECORD;
@property (nonatomic, readonly) NSString *AUDIO_SESSION_CATEGORY_PLAY_AND_RECORD;

@property (nonatomic, readonly) NSNumber *AUDIO_SESSION_OVERRIDE_ROUTE_NONE;
@property (nonatomic, readonly) NSNumber *AUDIO_SESSION_OVERRIDE_ROUTE_SPEAKER;

@property (nonatomic, readonly) NSNumber *IMAGE_SCALING_AUTO;
@property (nonatomic, readonly) NSNumber *IMAGE_SCALING_NONE;
@property (nonatomic, readonly) NSNumber *IMAGE_SCALING_FILL;
@property (nonatomic, readonly) NSNumber *IMAGE_SCALING_ASPECT_FILL;
@property (nonatomic, readonly) NSNumber *IMAGE_SCALING_ASPECT_FIT;

@property (nonatomic, readonly) NSNumber *MUSIC_MEDIA_TYPE_MUSIC;
@property (nonatomic, readonly) NSNumber *MUSIC_MEDIA_TYPE_PODCAST;
@property (nonatomic, readonly) NSNumber *MUSIC_MEDIA_TYPE_AUDIOBOOK;
@property (nonatomic, readonly) NSNumber *MUSIC_MEDIA_TYPE_ANY_AUDIO;
@property (nonatomic, readonly) NSNumber *MUSIC_MEDIA_TYPE_ALL;

@property (nonatomic, readonly) NSNumber *MUSIC_MEDIA_GROUP_TITLE;
@property (nonatomic, readonly) NSNumber *MUSIC_MEDIA_GROUP_ALBUM;
@property (nonatomic, readonly) NSNumber *MUSIC_MEDIA_GROUP_ARTIST;
@property (nonatomic, readonly) NSNumber *MUSIC_MEDIA_GROUP_ALBUM_ARTIST;
@property (nonatomic, readonly) NSNumber *MUSIC_MEDIA_GROUP_COMPOSER;
@property (nonatomic, readonly) NSNumber *MUSIC_MEDIA_GROUP_GENRE;
@property (nonatomic, readonly) NSNumber *MUSIC_MEDIA_GROUP_PLAYLIST;
@property (nonatomic, readonly) NSNumber *MUSIC_MEDIA_GROUP_PODCAST_TITLE;

@property (nonatomic, readonly) NSNumber *MUSIC_PLAYER_STATE_STOPPED;
@property (nonatomic, readonly) NSNumber *MUSIC_PLAYER_STATE_PLAYING;
@property (nonatomic, readonly) NSNumber *MUSIC_PLAYER_STATE_PAUSED;
@property (nonatomic, readonly) NSNumber *MUSIC_PLAYER_STATE_INTERRUPTED;
@property (nonatomic, readonly) NSNumber *MUSIC_PLAYER_STATE_SEEK_FORWARD;
@property (nonatomic, readonly) NSNumber *MUSIC_PLAYER_STATE_SEEK_BACKWARD;

@property (nonatomic, readonly) NSNumber *MUSIC_PLAYER_REPEAT_DEFAULT;
@property (nonatomic, readonly) NSNumber *MUSIC_PLAYER_REPEAT_NONE;
@property (nonatomic, readonly) NSNumber *MUSIC_PLAYER_REPEAT_ONE;
@property (nonatomic, readonly) NSNumber *MUSIC_PLAYER_REPEAT_ALL;

@property (nonatomic, readonly) NSNumber *MUSIC_PLAYER_SHUFFLE_DEFAULT;
@property (nonatomic, readonly) NSNumber *MUSIC_PLAYER_SHUFFLE_NONE;
@property (nonatomic, readonly) NSNumber *MUSIC_PLAYER_SHUFFLE_SONGS;
@property (nonatomic, readonly) NSNumber *MUSIC_PLAYER_SHUFFLE_ALBUMS;

@property (nonatomic, readonly) NSNumber *VIDEO_PLAYBACK_STATE_STOPPED;
@property (nonatomic, readonly) NSNumber *VIDEO_PLAYBACK_STATE_PLAYING;
@property (nonatomic, readonly) NSNumber *VIDEO_PLAYBACK_STATE_PAUSED;
@property (nonatomic, readonly) NSNumber *VIDEO_PLAYBACK_STATE_INTERRUPTED;

@property (nonatomic, readonly) NSNumber *VIDEO_LOAD_STATE_UNKNOWN;
@property (nonatomic, readonly) NSNumber *VIDEO_LOAD_STATE_PLAYABLE;
@property (nonatomic, readonly) NSNumber *VIDEO_LOAD_STATE_FAILED;

@property (nonatomic, readonly) NSNumber *VIDEO_TIME_OPTION_NEAREST_KEYFRAME;
@property (nonatomic, readonly) NSNumber *VIDEO_TIME_OPTION_EXACT;

@property (nonatomic, readonly) NSString *AUDIO_SESSION_PORT_LINEIN;
@property (nonatomic, readonly) NSString *AUDIO_SESSION_PORT_BUILTINMIC;
@property (nonatomic, readonly) NSString *AUDIO_SESSION_PORT_HEADSETMIC;
@property (nonatomic, readonly) NSString *AUDIO_SESSION_PORT_LINEOUT;
@property (nonatomic, readonly) NSString *AUDIO_SESSION_PORT_HEADPHONES;
@property (nonatomic, readonly) NSString *AUDIO_SESSION_PORT_BLUETOOTHA2DP;
@property (nonatomic, readonly) NSString *AUDIO_SESSION_PORT_BUILTINRECEIVER;
@property (nonatomic, readonly) NSString *AUDIO_SESSION_PORT_BUILTINSPEAKER;
@property (nonatomic, readonly) NSString *AUDIO_SESSION_PORT_HDMI;
@property (nonatomic, readonly) NSString *AUDIO_SESSION_PORT_AIRPLAY;
@property (nonatomic, readonly) NSString *AUDIO_SESSION_PORT_BLUETOOTHLE;
@property (nonatomic, readonly) NSString *AUDIO_SESSION_PORT_BLUETOOTHHFP;
@property (nonatomic, readonly) NSString *AUDIO_SESSION_PORT_USBAUDIO;
@property (nonatomic, readonly) NSString *AUDIO_SESSION_PORT_CARAUDIO;

@property (nonatomic, readonly) NSNumber *AUDIO_STATE_INITIALIZED;
@property (nonatomic, readonly) NSNumber *AUDIO_STATE_STARTING;
@property (nonatomic, readonly) NSNumber *AUDIO_STATE_WAITING_FOR_DATA;
@property (nonatomic, readonly) NSNumber *AUDIO_STATE_WAITING_FOR_QUEUE;
@property (nonatomic, readonly) NSNumber *AUDIO_STATE_PLAYING;
@property (nonatomic, readonly) NSNumber *AUDIO_STATE_BUFFERING;
@property (nonatomic, readonly) NSNumber *AUDIO_STATE_STOPPING;
@property (nonatomic, readonly) NSNumber *AUDIO_STATE_STOPPED;
@property (nonatomic, readonly) NSNumber *AUDIO_STATE_PAUSED;

@end

#endif
