/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2018 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_MEDIA

#import "MediaModule.h"
#import "TiMediaAudioSession.h"
#import "TiMediaItem.h"
#import "TiMediaMusicPlayer.h"

#import <TitaniumKit/Mimetypes.h>
#import <TitaniumKit/Ti2DMatrix.h>
#import <TitaniumKit/TiApp.h>
#import <TitaniumKit/TiBlob.h>
#import <TitaniumKit/TiFile.h>
#import <TitaniumKit/TiUtils.h>
#import <TitaniumKit/TiViewProxy.h>

#import <AVFoundation/AVAsset.h>
#import <AVFoundation/AVAssetExportSession.h>
#import <AVFoundation/AVFAudio.h>
#import <AVFoundation/AVFoundation.h>
#import <AVFoundation/AVMediaFormat.h>
#import <AudioToolbox/AudioToolbox.h>
#import <MobileCoreServices/UTCoreTypes.h>
#import <Photos/Photos.h>
#import <QuartzCore/QuartzCore.h>
#import <UIKit/UIPopoverController.h>
#if defined(USE_TI_MEDIAHASPHOTOGALLERYPERMISSIONS) && !TARGET_OS_MACCATALYST
#import <AssetsLibrary/AssetsLibrary.h>
#endif
#import "TiUIiOSLivePhoto.h"
#ifdef USE_TI_MEDIAVIDEOPLAYER
#import "TiMediaVideoPlayerProxy.h"
#endif
#if IS_SDK_IOS_14
#import <UniformTypeIdentifiers/UTCoreTypes.h>
#endif

// by default, we want to make the camera fullscreen and
// these transform values will scale it when we have our own overlay

enum {
  MediaModuleErrorUnknown,
  MediaModuleErrorBusy,
  MediaModuleErrorNoCamera,
  MediaModuleErrorNoVideo,
  MediaModuleErrorNoMusicPlayer
};

// Have to distinguish between filterable and nonfilterable properties
#if defined(USE_TI_MEDIAOPENMUSICLIBRARY) || defined(USE_TI_MEDIAQUERYMUSICLIBRARY) || defined(USE_TI_MEDIASYSTEMMUSICPLAYER) || defined(USE_TI_MEDIAAPPMUSICPLAYER) || defined(USE_TI_MEDIAGETSYSTEMMUSICPLAYER) || defined(USE_TI_MEDIAGETAPPMUSICPLAYER)
static NSMutableDictionary *TI_itemProperties;
static NSDictionary *TI_filterableItemProperties;
#endif

@interface TiImagePickerController : UIImagePickerController {
  @private
  BOOL autoRotate;
}
@end

@implementation TiImagePickerController

- (id)initWithProperties:(NSDictionary *)dict_
{
  if (self = [self init]) {
    autoRotate = [TiUtils boolValue:@"autorotate" properties:dict_ def:YES];
  }
  return self;
}

- (void)viewDidLoad
{
  [super viewDidLoad];
  self.edgesForExtendedLayout = UIRectEdgeNone;
  [self prefersStatusBarHidden];
  [self setNeedsStatusBarAppearanceUpdate];
}

- (BOOL)shouldAutorotate
{
  return autoRotate;
}

- (BOOL)prefersStatusBarHidden
{
  return YES;
}

- (UIViewController *)childViewControllerForStatusBarHidden
{
  return nil;
}

- (UIViewController *)childViewControllerForStatusBarStyle
{
  return nil;
}

@end

@implementation MediaModule
@synthesize popoverView;

- (void)dealloc
{
  [self destroyPicker];
#if defined(USE_TI_MEDIAGETAPPMUSICPLAYER) || defined(USE_TI_MEDIAAPPMUSICPLAYER) || defined(USE_TI_MEDIAGETSYSTEMMUSICPLAYER) || defined(USE_TI_MEDIASYSTEMMUSICPLAYER)
  RELEASE_TO_NIL(systemMusicPlayer);
  RELEASE_TO_NIL(appMusicPlayer);
#endif
  RELEASE_TO_NIL(popoverView);
  [[NSNotificationCenter defaultCenter] removeObserver:self];
  [super dealloc];
}

#pragma mark Static Properties

#if defined(USE_TI_MEDIAOPENMUSICLIBRARY) || defined(USE_TI_MEDIAQUERYMUSICLIBRARY) || defined(USE_TI_MEDIASYSTEMMUSICPLAYER) || defined(USE_TI_MEDIAAPPMUSICPLAYER) || defined(USE_TI_MEDIAGETSYSTEMMUSICPLAYER) || defined(USE_TI_MEDIAGETAPPMUSICPLAYER)
+ (NSDictionary *)filterableItemProperties
{
  if (TI_filterableItemProperties == nil) {
    TI_filterableItemProperties = [[NSDictionary alloc] initWithObjectsAndKeys:MPMediaItemPropertyMediaType, @"mediaType",
                                                        MPMediaItemPropertyTitle, @"title",
                                                        MPMediaItemPropertyAlbumTitle, @"albumTitle",
                                                        MPMediaItemPropertyArtist, @"artist",
                                                        MPMediaItemPropertyAlbumArtist, @"albumArtist",
                                                        MPMediaItemPropertyGenre, @"genre",
                                                        MPMediaItemPropertyComposer, @"composer",
                                                        MPMediaItemPropertyIsCompilation, @"isCompilation",
                                                        MPMediaItemPropertyPlayCount, @"playCount",
                                                        MPMediaItemPropertyPersistentID, @"persistentID",
                                                        MPMediaItemPropertyAlbumPersistentID, @"albumPersistentID",
                                                        MPMediaItemPropertyAlbumArtistPersistentID, @"albumArtistPersistentID",
                                                        MPMediaItemPropertyGenrePersistentID, @"genrePersistentID",
                                                        MPMediaItemPropertyComposerPersistentID, @"composerPersistentID",
                                                        MPMediaItemPropertyIsCloudItem, @"isCloudItem",
                                                        [TiUtils isIOSVersionOrGreater:@"9.2"] ? MPMediaItemPropertyHasProtectedAsset : NO, @"hasProtectedAsset",
                                                        MPMediaItemPropertyPodcastTitle, @"podcastTitle",
                                                        MPMediaItemPropertyPodcastPersistentID, @"podcastPersistentID",
                                                        nil];
  }
  return TI_filterableItemProperties;
}

+ (NSDictionary *)itemProperties
{
  if (TI_itemProperties == nil) {
    TI_itemProperties = [[NSMutableDictionary alloc] initWithObjectsAndKeys:MPMediaItemPropertyPlaybackDuration, @"playbackDuration",
                                                     MPMediaItemPropertyAlbumTrackNumber, @"albumTrackNumber",
                                                     MPMediaItemPropertyAlbumTrackCount, @"albumTrackCount",
                                                     MPMediaItemPropertyDiscNumber, @"discNumber",
                                                     MPMediaItemPropertyDiscCount, @"discCount",
                                                     MPMediaItemPropertyLyrics, @"lyrics",
                                                     MPMediaItemPropertySkipCount, @"skipCount",
                                                     MPMediaItemPropertyRating, @"rating",
                                                     MPMediaItemPropertyAssetURL, @"assetURL",
                                                     MPMediaItemPropertyIsExplicit, @"isExplicit",
                                                     MPMediaItemPropertyReleaseDate, @"releaseDate",
                                                     MPMediaItemPropertyBeatsPerMinute, @"beatsPerMinute",
                                                     MPMediaItemPropertyComments, @"comments",
                                                     MPMediaItemPropertyLastPlayedDate, @"lastPlayedDate",
                                                     MPMediaItemPropertyUserGrouping, @"userGrouping",
                                                     MPMediaItemPropertyBookmarkTime, @"bookmarkTime",
                                                     nil];

    TI_itemProperties[@"dateAdded"] = MPMediaItemPropertyDateAdded;
    if ([TiUtils isIOSVersionOrGreater:@"10.3"]) {
      TI_itemProperties[@"playbackStoreID"] = MPMediaItemPropertyPlaybackStoreID;
    }
  }

  return TI_itemProperties;
}
#endif

#pragma mark Public Properties

- (NSString *)apiName
{
  return @"Ti.Media";
}

#ifdef USE_TI_MEDIAAUDIORECORDER
MAKE_SYSTEM_UINT(AUDIO_FORMAT_LINEAR_PCM, kAudioFormatLinearPCM);
MAKE_SYSTEM_UINT(AUDIO_FORMAT_ULAW, kAudioFormatULaw);
MAKE_SYSTEM_UINT(AUDIO_FORMAT_ALAW, kAudioFormatALaw);
MAKE_SYSTEM_UINT(AUDIO_FORMAT_IMA4, kAudioFormatAppleIMA4);
MAKE_SYSTEM_UINT(AUDIO_FORMAT_ILBC, kAudioFormatiLBC);
MAKE_SYSTEM_UINT(AUDIO_FORMAT_APPLE_LOSSLESS, kAudioFormatAppleLossless);
MAKE_SYSTEM_UINT(AUDIO_FORMAT_AAC, kAudioFormatMPEG4AAC);

MAKE_SYSTEM_UINT(AUDIO_FILEFORMAT_WAVE, kAudioFileWAVEType);
MAKE_SYSTEM_UINT(AUDIO_FILEFORMAT_AIFF, kAudioFileAIFFType);
MAKE_SYSTEM_UINT(AUDIO_FILEFORMAT_MP3, kAudioFileMP3Type);
MAKE_SYSTEM_UINT(AUDIO_FILEFORMAT_MP4, kAudioFileMPEG4Type);
MAKE_SYSTEM_UINT(AUDIO_FILEFORMAT_MP4A, kAudioFileM4AType);
MAKE_SYSTEM_UINT(AUDIO_FILEFORMAT_CAF, kAudioFileCAFType);
MAKE_SYSTEM_UINT(AUDIO_FILEFORMAT_3GPP, kAudioFile3GPType);
MAKE_SYSTEM_UINT(AUDIO_FILEFORMAT_3GP2, kAudioFile3GP2Type);
MAKE_SYSTEM_UINT(AUDIO_FILEFORMAT_AMR, kAudioFileAMRType);
#endif

#ifdef USE_TI_MEDIACAMERA_AUTHORIZATION_AUTHORIZED
MAKE_SYSTEM_UINT(CAMERA_AUTHORIZATION_AUTHORIZED, AVAuthorizationStatusAuthorized);
#endif
#ifdef USE_TI_MEDIACAMERA_AUTHORIZATION_DENIED
MAKE_SYSTEM_UINT(CAMERA_AUTHORIZATION_DENIED, AVAuthorizationStatusDenied);
#endif
#ifdef USE_TI_MEDIACAMERA_AUTHORIZATION_RESTRICTED
MAKE_SYSTEM_UINT(CAMERA_AUTHORIZATION_RESTRICTED, AVAuthorizationStatusRestricted);
#endif
#ifdef USE_TI_MEDIACAMERA_AUTHORIZATION_UNKNOWN
MAKE_SYSTEM_UINT(CAMERA_AUTHORIZATION_UNKNOWN, AVAuthorizationStatusNotDetermined);
#endif

#if defined(USE_TI_MEDIA) && (defined(USE_TI_MEDIAAUDIOPLAYER) || defined(USE_TI_MEDIAVIDEOPLAYER) || defined(USE_TI_MEDIASOUND) || defined(USE_TI_MEDIAAUDIORECORDER))

// Constants for currentRoute
MAKE_SYSTEM_STR(AUDIO_SESSION_PORT_LINEIN, AVAudioSessionPortLineIn)
MAKE_SYSTEM_STR(AUDIO_SESSION_PORT_BUILTINMIC, AVAudioSessionPortBuiltInMic)
MAKE_SYSTEM_STR(AUDIO_SESSION_PORT_HEADSETMIC, AVAudioSessionPortHeadsetMic)
MAKE_SYSTEM_STR(AUDIO_SESSION_PORT_LINEOUT, AVAudioSessionPortLineOut)
MAKE_SYSTEM_STR(AUDIO_SESSION_PORT_HEADPHONES, AVAudioSessionPortHeadphones)
MAKE_SYSTEM_STR(AUDIO_SESSION_PORT_BLUETOOTHA2DP, AVAudioSessionPortBluetoothA2DP)
MAKE_SYSTEM_STR(AUDIO_SESSION_PORT_BUILTINRECEIVER, AVAudioSessionPortBuiltInReceiver)
MAKE_SYSTEM_STR(AUDIO_SESSION_PORT_BUILTINSPEAKER, AVAudioSessionPortBuiltInSpeaker)
MAKE_SYSTEM_STR(AUDIO_SESSION_PORT_HDMI, AVAudioSessionPortHDMI)
MAKE_SYSTEM_STR(AUDIO_SESSION_PORT_AIRPLAY, AVAudioSessionPortAirPlay)
MAKE_SYSTEM_STR(AUDIO_SESSION_PORT_BLUETOOTHHFP, AVAudioSessionPortBluetoothHFP)
MAKE_SYSTEM_STR(AUDIO_SESSION_PORT_USBAUDIO, AVAudioSessionPortUSBAudio)
MAKE_SYSTEM_STR(AUDIO_SESSION_PORT_BLUETOOTHLE, AVAudioSessionPortBluetoothLE)
MAKE_SYSTEM_STR(AUDIO_SESSION_PORT_CARAUDIO, AVAudioSessionPortCarAudio)

//Constants for AudioSessions
MAKE_SYSTEM_STR(AUDIO_SESSION_CATEGORY_AMBIENT, AVAudioSessionCategoryAmbient);
MAKE_SYSTEM_STR(AUDIO_SESSION_CATEGORY_SOLO_AMBIENT, AVAudioSessionCategorySoloAmbient);
MAKE_SYSTEM_STR(AUDIO_SESSION_CATEGORY_PLAYBACK, AVAudioSessionCategoryPlayback);
MAKE_SYSTEM_STR(AUDIO_SESSION_CATEGORY_RECORD, AVAudioSessionCategoryRecord);
MAKE_SYSTEM_STR(AUDIO_SESSION_CATEGORY_PLAY_AND_RECORD, AVAudioSessionCategoryPlayAndRecord);

MAKE_SYSTEM_UINT(AUDIO_SESSION_OVERRIDE_ROUTE_NONE, AVAudioSessionPortOverrideNone);
MAKE_SYSTEM_UINT(AUDIO_SESSION_OVERRIDE_ROUTE_SPEAKER, AVAudioSessionPortOverrideSpeaker);
#endif

// Constants for VideoPlayer.playbackState
MAKE_SYSTEM_PROP(VIDEO_PLAYBACK_STATE_INTERRUPTED, TiVideoPlayerPlaybackStateInterrupted);
MAKE_SYSTEM_PROP(VIDEO_PLAYBACK_STATE_PAUSED, TiVideoPlayerPlaybackStatePaused);
MAKE_SYSTEM_PROP(VIDEO_PLAYBACK_STATE_PLAYING, TiVideoPlayerPlaybackStatePlaying);
MAKE_SYSTEM_PROP(VIDEO_PLAYBACK_STATE_STOPPED, TiVideoPlayerPlaybackStateStopped);

// Constants for AudioPlayer
MAKE_SYSTEM_PROP(AUDIO_STATE_INITIALIZED, TiAudioPlayerStateInitialized);
MAKE_SYSTEM_PROP(AUDIO_STATE_STARTING, TiAudioPlayerStateStartingFileThread);
MAKE_SYSTEM_PROP(AUDIO_STATE_WAITING_FOR_DATA, TiAudioPlayerStateWaitingForData);
MAKE_SYSTEM_PROP(AUDIO_STATE_WAITING_FOR_QUEUE, TiAudioPlayerStateWaitingForQueueToStart);
MAKE_SYSTEM_PROP(AUDIO_STATE_PLAYING, TiAudioPlayerStatePlaying);
MAKE_SYSTEM_PROP(AUDIO_STATE_BUFFERING, TiAudioPlayerStateBuffering);
MAKE_SYSTEM_PROP(AUDIO_STATE_STOPPING, TiAudioPlayerStateStopping);
MAKE_SYSTEM_PROP(AUDIO_STATE_STOPPED, TiAudioPlayerStateStopped);
MAKE_SYSTEM_PROP(AUDIO_STATE_PAUSED, TiAudioPlayerStatePaused);

//Constants for Camera
#if defined(USE_TI_MEDIACAMERA_FRONT) || defined(USE_TI_MEDIACAMERA_REAR) || defined(USE_TI_MEDIACAMERA_FLASH_OFF) || defined(USE_TI_MEDIACAMERA_FLASH_AUTO) || defined(USE_TI_MEDIACAMERA_FLASH_ON)
MAKE_SYSTEM_PROP(CAMERA_FRONT, UIImagePickerControllerCameraDeviceFront);
MAKE_SYSTEM_PROP(CAMERA_REAR, UIImagePickerControllerCameraDeviceRear);

MAKE_SYSTEM_PROP(CAMERA_FLASH_OFF, UIImagePickerControllerCameraFlashModeOff);
MAKE_SYSTEM_PROP(CAMERA_FLASH_AUTO, UIImagePickerControllerCameraFlashModeAuto);
MAKE_SYSTEM_PROP(CAMERA_FLASH_ON, UIImagePickerControllerCameraFlashModeOn);
#endif

//Constants for mediaTypes in openMusicLibrary
#if defined(USE_TI_MEDIAOPENMUSICLIBRARY) || defined(USE_TI_MEDIAQUERYMUSICLIBRARY)
MAKE_SYSTEM_PROP(MUSIC_MEDIA_TYPE_MUSIC, MPMediaTypeMusic);
MAKE_SYSTEM_PROP(MUSIC_MEDIA_TYPE_PODCAST, MPMediaTypePodcast);
MAKE_SYSTEM_PROP(MUSIC_MEDIA_TYPE_AUDIOBOOK, MPMediaTypeAudioBook);
MAKE_SYSTEM_PROP(MUSIC_MEDIA_TYPE_ANY_AUDIO, MPMediaTypeAnyAudio);
- (NSNumber *)MUSIC_MEDIA_TYPE_ALL
{
  return NUMUINTEGER(MPMediaTypeAny);
}

//Constants for grouping in queryMusicLibrary
MAKE_SYSTEM_PROP(MUSIC_MEDIA_GROUP_TITLE, MPMediaGroupingTitle);
MAKE_SYSTEM_PROP(MUSIC_MEDIA_GROUP_ALBUM, MPMediaGroupingAlbum);
MAKE_SYSTEM_PROP(MUSIC_MEDIA_GROUP_ARTIST, MPMediaGroupingArtist);
MAKE_SYSTEM_PROP(MUSIC_MEDIA_GROUP_ALBUM_ARTIST, MPMediaGroupingAlbumArtist);
MAKE_SYSTEM_PROP(MUSIC_MEDIA_GROUP_COMPOSER, MPMediaGroupingComposer);
MAKE_SYSTEM_PROP(MUSIC_MEDIA_GROUP_GENRE, MPMediaGroupingGenre);
MAKE_SYSTEM_PROP(MUSIC_MEDIA_GROUP_PLAYLIST, MPMediaGroupingPlaylist);
MAKE_SYSTEM_PROP(MUSIC_MEDIA_GROUP_PODCAST_TITLE, MPMediaGroupingPodcastTitle);
#endif

#if defined(USE_TI_MEDIAGETAPPMUSICPLAYER) || defined(USE_TI_MEDIAAPPMUSICPLAYER) || defined(USE_TI_MEDIAGETSYSTEMMUSICPLAYER) || defined(USE_TI_MEDIASYSTEMMUSICPLAYER)
//Constants for MusicPlayer playback state
MAKE_SYSTEM_PROP(MUSIC_PLAYER_STATE_STOPPED, MPMusicPlaybackStateStopped);
MAKE_SYSTEM_PROP(MUSIC_PLAYER_STATE_PLAYING, MPMusicPlaybackStatePlaying);
MAKE_SYSTEM_PROP(MUSIC_PLAYER_STATE_PAUSED, MPMusicPlaybackStatePaused);
MAKE_SYSTEM_PROP(MUSIC_PLAYER_STATE_INTERRUPTED, MPMusicPlaybackStateInterrupted);
MAKE_SYSTEM_PROP(MUSIC_PLAYER_STATE_SEEK_FORWARD, MPMusicPlaybackStateSeekingForward);
MAKE_SYSTEM_PROP(MUSIC_PLAYER_STATE_SEEK_BACKWARD, MPMusicPlaybackStateSeekingBackward);

//Constants for MusicPlayer repeatMode
MAKE_SYSTEM_PROP(MUSIC_PLAYER_REPEAT_DEFAULT, MPMusicRepeatModeDefault);
MAKE_SYSTEM_PROP(MUSIC_PLAYER_REPEAT_NONE, MPMusicRepeatModeNone);
MAKE_SYSTEM_PROP(MUSIC_PLAYER_REPEAT_ONE, MPMusicRepeatModeOne);
MAKE_SYSTEM_PROP(MUSIC_PLAYER_REPEAT_ALL, MPMusicRepeatModeAll);

//Constants for MusicPlayer shuffleMode
MAKE_SYSTEM_PROP(MUSIC_PLAYER_SHUFFLE_DEFAULT, MPMusicShuffleModeDefault);
MAKE_SYSTEM_PROP(MUSIC_PLAYER_SHUFFLE_NONE, MPMusicShuffleModeOff);
MAKE_SYSTEM_PROP(MUSIC_PLAYER_SHUFFLE_SONGS, MPMusicShuffleModeSongs);
MAKE_SYSTEM_PROP(MUSIC_PLAYER_SHUFFLE_ALBUMS, MPMusicShuffleModeAlbums);
#endif
//Error constants for MediaModule
MAKE_SYSTEM_PROP(UNKNOWN_ERROR, MediaModuleErrorUnknown);
MAKE_SYSTEM_PROP(DEVICE_BUSY, MediaModuleErrorBusy);
MAKE_SYSTEM_PROP(NO_CAMERA, MediaModuleErrorNoCamera);
MAKE_SYSTEM_PROP(NO_VIDEO, MediaModuleErrorNoVideo);
MAKE_SYSTEM_PROP(NO_MUSIC_PLAYER, MediaModuleErrorNoMusicPlayer);

//Constants for mediaTypes in showCamera
#if defined(USE_TI_MEDIASHOWCAMERA) || defined(USE_TI_MEDIAOPENPHOTOGALLERY)
MAKE_SYSTEM_STR(MEDIA_TYPE_VIDEO, kUTTypeMovie);
MAKE_SYSTEM_STR(MEDIA_TYPE_PHOTO, kUTTypeImage);
MAKE_SYSTEM_STR(MEDIA_TYPE_LIVEPHOTO, kUTTypeLivePhoto);

//Constants for videoQuality for Video Editing
MAKE_SYSTEM_PROP(QUALITY_HIGH, UIImagePickerControllerQualityTypeHigh);
MAKE_SYSTEM_PROP(QUALITY_MEDIUM, UIImagePickerControllerQualityTypeMedium);
MAKE_SYSTEM_PROP(QUALITY_LOW, UIImagePickerControllerQualityTypeLow);
MAKE_SYSTEM_PROP(QUALITY_640x480, UIImagePickerControllerQualityType640x480);
MAKE_SYSTEM_PROP(QUALITY_IFRAME_1280x720, UIImagePickerControllerQualityTypeIFrame1280x720);
MAKE_SYSTEM_PROP(QUALITY_IFRAME_960x540, UIImagePickerControllerQualityTypeIFrame960x540);
#endif

// Constants for MediaTypes in VideoPlayer
#ifdef USE_TI_MEDIAVIDEOPLAYER
// Constants for VideoPlayer scalingMode
MAKE_SYSTEM_STR(VIDEO_SCALING_RESIZE, AVLayerVideoGravityResize);
MAKE_SYSTEM_STR(VIDEO_SCALING_RESIZE_ASPECT, AVLayerVideoGravityResizeAspect);
MAKE_SYSTEM_STR(VIDEO_SCALING_RESIZE_ASPECT_FILL, AVLayerVideoGravityResizeAspectFill);

// Constants for VideoPlayer mediaTypes
MAKE_SYSTEM_STR(VIDEO_MEDIA_TYPE_AUDIO, AVMediaTypeAudio);
MAKE_SYSTEM_STR(VIDEO_MEDIA_TYPE_CLOSED_CAPTION, AVMediaTypeClosedCaption);
MAKE_SYSTEM_STR(VIDEO_MEDIA_TYPE_DEPTH_DATA, AVMediaTypeDepthData);
MAKE_SYSTEM_STR(VIDEO_MEDIA_TYPE_METADATA, AVMediaTypeMetadata);
MAKE_SYSTEM_STR(VIDEO_MEDIA_TYPE_METADATA_OBJECT, AVMediaTypeMetadataObject);
MAKE_SYSTEM_STR(VIDEO_MEDIA_TYPE_MUXED, AVMediaTypeMuxed);
MAKE_SYSTEM_STR(VIDEO_MEDIA_TYPE_SUBTITLE, AVMediaTypeSubtitle);
MAKE_SYSTEM_STR(VIDEO_MEDIA_TYPE_TEXT, AVMediaTypeText);
MAKE_SYSTEM_STR(VIDEO_MEDIA_TYPE_TIMECODE, AVMediaTypeTimecode);
MAKE_SYSTEM_STR(VIDEO_MEDIA_TYPE_VIDEO, AVMediaTypeVideo);

// Constants for VideoPlayer loadState
MAKE_SYSTEM_PROP(VIDEO_LOAD_STATE_UNKNOWN, AVPlayerStatusUnknown);
MAKE_SYSTEM_PROP(VIDEO_LOAD_STATE_PLAYABLE, AVPlayerStatusReadyToPlay);
MAKE_SYSTEM_PROP(VIDEO_LOAD_STATE_FAILED, AVPlayerStatusFailed);

MAKE_SYSTEM_PROP(VIDEO_TIME_OPTION_NEAREST_KEYFRAME, VideoTimeOptionNearestKeyFrame);
MAKE_SYSTEM_PROP(VIDEO_TIME_OPTION_EXACT, VideoTimeOptionExact);

MAKE_SYSTEM_PROP(VIDEO_REPEAT_MODE_NONE, VideoRepeatModeNone);
MAKE_SYSTEM_PROP(VIDEO_REPEAT_MODE_ONE, VideoRepeatModeOne);

#endif

#if defined(USE_TI_MEDIAGETAPPMUSICPLAYER) || defined(USE_TI_MEDIAAPPMUSICPLAYER) || defined(USE_TI_MEDIAGETSYSTEMMUSICPLAYER) || defined(USE_TI_MEDIASYSTEMMUSICPLAYER)
- (TiMediaMusicPlayer *)systemMusicPlayer
{
  if (systemMusicPlayer == nil) {
    if (![NSThread isMainThread]) {
      __block id result;
      TiThreadPerformOnMainThread(
          ^{
            result = [self systemMusicPlayer];
          },
          YES);
      return result;
    }
    systemMusicPlayer = [[TiMediaMusicPlayer alloc] _initWithPageContext:[self pageContext] player:[MPMusicPlayerController systemMusicPlayer]];
  }
  return systemMusicPlayer;
}

- (TiMediaMusicPlayer *)appMusicPlayer
{
  if (appMusicPlayer == nil) {
    if (![NSThread isMainThread]) {
      __block id result;
      TiThreadPerformOnMainThread(
          ^{
            result = [self appMusicPlayer];
          },
          YES);
      return appMusicPlayer;
    }
    appMusicPlayer = [[TiMediaMusicPlayer alloc] _initWithPageContext:[self pageContext] player:[MPMusicPlayerController applicationMusicPlayer]];
  }
  return appMusicPlayer;
}
#endif

- (void)setAudioSessionCategory:(NSString *)mode
{
  [[TiMediaAudioSession sharedSession] setSessionMode:mode];
}

- (NSString *)audioSessionCategory
{
  return [[TiMediaAudioSession sharedSession] sessionMode];
}

- (NSNumber *)canRecord
{
  return NUMBOOL([[TiMediaAudioSession sharedSession] hasInput]);
}

- (NSNumber *)volume
{
  return NUMFLOAT([[TiMediaAudioSession sharedSession] volume]);
}

- (NSNumber *)audioPlaying
{
  return NUMBOOL([[TiMediaAudioSession sharedSession] isAudioPlaying]);
}

- (NSDictionary *)currentRoute
{
  return [[TiMediaAudioSession sharedSession] currentRoute];
}

- (void)setOverrideAudioRoute:(NSNumber *)mode
{
  [[TiMediaAudioSession sharedSession] setRouteOverride:[mode unsignedIntValue]];
}

- (void)_listenerAdded:(NSString *)type count:(int)count
{
  if (count == 1 && [type isEqualToString:@"routechange"]) {
    WARN_IF_BACKGROUND_THREAD_OBJ; //NSNotificationCenter is not threadsafe
    [[TiMediaAudioSession sharedSession] startAudioSession]; // Have to start a session to get a listener
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(audioRouteChanged:) name:kTiMediaAudioSessionRouteChange object:[TiMediaAudioSession sharedSession]];
  } else if (count == 1 && [type isEqualToString:@"volume"]) {
    WARN_IF_BACKGROUND_THREAD_OBJ; //NSNotificationCenter is not threadsafe!
    [[TiMediaAudioSession sharedSession] startAudioSession]; // Have to start a session to get a listener
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(audioVolumeChanged:) name:kTiMediaAudioSessionVolumeChange object:[TiMediaAudioSession sharedSession]];
  }
}

- (void)_listenerRemoved:(NSString *)type count:(int)count
{
  if (count == 0 && [type isEqualToString:@"routechange"]) {
    WARN_IF_BACKGROUND_THREAD_OBJ; //NSNotificationCenter is not threadsafe!
    [[TiMediaAudioSession sharedSession] stopAudioSession];
    [[NSNotificationCenter defaultCenter] removeObserver:self name:kTiMediaAudioSessionRouteChange object:[TiMediaAudioSession sharedSession]];
  } else if (count == 0 && [type isEqualToString:@"volume"]) {
    WARN_IF_BACKGROUND_THREAD_OBJ; //NSNotificationCenter is not threadsafe!
    [[TiMediaAudioSession sharedSession] stopAudioSession];
    [[NSNotificationCenter defaultCenter] removeObserver:self name:kTiMediaAudioSessionVolumeChange object:[TiMediaAudioSession sharedSession]];
  }
}

#pragma mark Event Listener Management

- (void)audioRouteChanged:(NSNotification *)note
{
  NSDictionary *event = [note userInfo];
  [self fireEvent:@"routechange" withObject:event];
}

- (void)audioVolumeChanged:(NSNotification *)note
{
  NSDictionary *userInfo = [note userInfo];
  if (userInfo != nil) {
    [self fireEvent:@"volume" withObject:userInfo];
  } else {
    NSMutableDictionary *event = [NSMutableDictionary dictionary];
    [event setObject:[self volume] forKey:@"volume"];
    [self fireEvent:@"volume" withObject:event];
  }
}

#if defined(USE_TI_MEDIAAVAILABLECAMERAMEDIATYPES) || defined(USE_TI_MEDIAISMEDIATYPESUPPORTED)
- (NSArray *)availableCameraMediaTypes
{
  NSArray *mediaSourceTypes = [UIImagePickerController availableMediaTypesForSourceType:UIImagePickerControllerSourceTypeCamera];
  return mediaSourceTypes == nil ? [NSArray arrayWithObject:(NSString *)kUTTypeImage] : mediaSourceTypes;
}
#endif

#if defined(USE_TI_MEDIAAVAILABLEPHOTOMEDIATYPES) || defined(USE_TI_MEDIAISMEDIATYPESUPPORTED)
- (NSArray *)availablePhotoMediaTypes
{
  NSArray *photoSourceTypes = [UIImagePickerController availableMediaTypesForSourceType:UIImagePickerControllerSourceTypePhotoLibrary];
  return photoSourceTypes == nil ? [NSArray arrayWithObject:(NSString *)kUTTypeImage] : photoSourceTypes;
}
#endif

#if defined(USE_TI_MEDIAAVAILABLEPHOTOGALLERYMEDIATYPES) || defined(USE_TI_MEDIAISMEDIATYPESUPPORTED)
- (NSArray *)availablePhotoGalleryMediaTypes
{
  NSArray *albumSourceTypes = [UIImagePickerController availableMediaTypesForSourceType:UIImagePickerControllerSourceTypeSavedPhotosAlbum];
  return albumSourceTypes == nil ? [NSArray arrayWithObject:(NSString *)kUTTypeImage] : albumSourceTypes;
}
#endif

#if defined(USE_TI_MEDIAAVAILABLECAMERAS)
- (NSArray *)availableCameras
{
  NSMutableArray *types = [NSMutableArray arrayWithCapacity:2];
  if ([UIImagePickerController isCameraDeviceAvailable:UIImagePickerControllerCameraDeviceFront]) {
    [types addObject:NUMINT(UIImagePickerControllerCameraDeviceFront)];
  }
  if ([UIImagePickerController isCameraDeviceAvailable:UIImagePickerControllerCameraDeviceRear]) {
    [types addObject:NUMINT(UIImagePickerControllerCameraDeviceRear)];
  }
  return types;
}
#endif

#ifdef USE_TI_MEDIACAMERAFLASHMODE
- (id)cameraFlashMode
{
  if (picker != nil) {
    return @([picker cameraFlashMode]);
  }
  return @(UIImagePickerControllerCameraFlashModeAuto);
}

- (void)setCameraFlashMode:(id)args
{
  // Return nothing
  ENSURE_SINGLE_ARG(args, NSNumber);
  ENSURE_UI_THREAD(setCameraFlashMode, args);

  if (picker != nil) {
    [picker setCameraFlashMode:[TiUtils intValue:args]];
  }
}
#endif

#ifdef USE_TI_MEDIAISCAMERASUPPORTED
- (NSNumber *)isCameraSupported
{
  return NUMBOOL([UIImagePickerController isSourceTypeAvailable:UIImagePickerControllerSourceTypeCamera]);
}
#endif

#if defined(USE_TI_MEDIACAMERAAUTHORIZATION)
- (NSNumber *)cameraAuthorization
{
  return NUMINTEGER([AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeVideo]);
}
#endif

#pragma mark Public Methods

#if defined(USE_TI_MEDIABEEP) || defined(USE_TI_MEDIAVIBRATE)
- (void)beep:(id)unused
{
  ENSURE_UI_THREAD(beep, unused);
  AudioServicesPlayAlertSound(kSystemSoundID_Vibrate);
}

- (void)vibrate:(id)args
{
  //No pattern support on iOS
  [self beep:nil];
}
#endif

/**
 Microphone And Recording Support. These make no sense here and should be moved to Audiorecorder
 **/
#if defined(USE_TI_MEDIAHASAUDIORECORDERPERMISSIONS) || defined(USE_TI_MEDIASTARTMICROPHONEMONITOR) || defined(USE_TI_MEDIASTOPMICROPHONEMONITOR) || defined(USE_TI_MEDIAPEAKMICROPHONEPOWER) || defined(USE_TI_MEDIAGETPEAKMICROPHONEPOWER) || defined(USE_TI_MEDIAAVERAGEMICROPHONEPOWER) || defined(USE_TI_MEDIAGETAVERAGEMICROPHONEPOWER)
- (id)hasAudioRecorderPermissions:(id)unused
{
  NSString *microphonePermission = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"NSMicrophoneUsageDescription"];

  if (!microphonePermission) {
    NSLog(@"[ERROR] iOS 10 and later requires the key \"NSMicrophoneUsageDescription\" inside the plist in your tiapp.xml when accessing the native microphone. Please add the key and re-run the application.");
  }

  return NUMBOOL([AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeAudio] == AVAuthorizationStatusAuthorized);
}
#endif

#if defined(USE_TI_MEDIAREQUESTAUDIORECORDERPERMISSIONS)
- (void)requestAudioRecorderPermissions:(id)args
{
  ENSURE_SINGLE_ARG(args, KrollCallback);
  KrollCallback *callback = args;
  TiThreadPerformOnMainThread(
      ^() {
        [[AVAudioSession sharedInstance] requestRecordPermission:^(BOOL granted) {
          KrollEvent *invocationEvent = [[KrollEvent alloc] initWithCallback:callback
                                                                 eventObject:[TiUtils dictionaryWithCode:(granted ? 0 : 1) message:nil]
                                                                  thisObject:self];
          [[callback context] enqueue:invocationEvent];
          RELEASE_TO_NIL(invocationEvent);
        }];
      },
      NO);
}
#endif

#if defined(USE_TI_MEDIASTARTMICROPHONEMONITOR) || defined(USE_TI_MEDIASTOPMICROPHONEMONITOR) || defined(USE_TI_MEDIAPEAKMICROPHONEPOWER) || defined(USE_TI_MEDIAGETPEAKMICROPHONEPOWER) || defined(USE_TI_MEDIAAVERAGEMICROPHONEPOWER) || defined(USE_TI_MEDIAGETAVERAGEMICROPHONEPOWER)

- (void)startMicrophoneMonitor:(id)args
{
  if (_microphoneRecorder != nil) {
    DebugLog(@"[ERROR] Trying to start new microphone monitoring while the old one is still active. Call \"stopMicrophoneMonitor()\" before and try again.");
    return;
  }

  if (![TiUtils boolValue:[self hasAudioRecorderPermissions:nil]]) {
    DebugLog(@"[ERROR] Microphone permissions are required to use the microphone monitoring API.");
  }

  NSURL *url = [NSURL fileURLWithPath:@"/dev/null"];
  NSDictionary *settings = [NSDictionary dictionaryWithObjectsAndKeys:
                                             [NSNumber numberWithFloat:44100.0], AVSampleRateKey,
                                         [NSNumber numberWithInt:kAudioFormatAppleLossless], AVFormatIDKey,
                                         [NSNumber numberWithInt:1], AVNumberOfChannelsKey,
                                         [NSNumber numberWithInt:AVAudioQualityMax], AVEncoderAudioQualityKey,
                                         nil];

  NSError *error;

  _microphoneRecorder = [[AVAudioRecorder alloc] initWithURL:url settings:settings error:&error];

  if (_microphoneRecorder == nil) {
    DebugLog(@"[ERROR] Error starting audio monitoring: %@", [error description]);
    return;
  }

  [_microphoneRecorder prepareToRecord];
  _microphoneRecorder.meteringEnabled = YES;
  [_microphoneRecorder record];
  _microphoneTimer = [NSTimer scheduledTimerWithTimeInterval:0.03
                                                      target:self
                                                    selector:@selector(_microphoneTimerChanged:)
                                                    userInfo:nil
                                                     repeats:YES];
}

- (void)_microphoneTimerChanged:(NSTimer *)timer
{
  [_microphoneRecorder updateMeters];
}

- (void)stopMicrophoneMonitor:(id)args
{
  if (_microphoneRecorder == nil) {
    DebugLog(@"[ERROR] Trying to stop microphone monitoring which is not active. Call \"startMicrophoneMonitor()\" before and try again.");
    return;
  }

  [_microphoneRecorder stop];
  [_microphoneTimer invalidate];
  _microphoneTimer = nil;

  RELEASE_TO_NIL(_microphoneRecorder);
}

- (NSNumber *)peakMicrophonePower
{
  if (_microphoneRecorder != nil && [_microphoneRecorder isRecording]) {
    return @([_microphoneRecorder peakPowerForChannel:0]);
  }
  return @(-1);
}

- (NSNumber *)averageMicrophonePower
{
  if (_microphoneRecorder != nil && [_microphoneRecorder isRecording]) {
    return @([_microphoneRecorder averagePowerForChannel:0]);
  }
  return @(-1);
}

#endif

/**
 End Microphone and Recording Support
 **/

#ifdef USE_TI_MEDIAISMEDIATYPESUPPORTED
- (NSNumber *)isMediaTypeSupported:(id)args
{
  ENSURE_ARG_COUNT(args, 2);

  NSString *media = [[TiUtils stringValue:[args objectAtIndex:0]] lowercaseString];
  NSString *type = [[TiUtils stringValue:[args objectAtIndex:1]] lowercaseString];

  NSArray *array = nil;

  if ([media isEqualToString:@"camera"]) {
    array = [self availableCameraMediaTypes];
  } else if ([media isEqualToString:@"photo"]) {
    array = [self availablePhotoMediaTypes];
  } else if ([media isEqualToString:@"photogallery"]) {
    array = [self availablePhotoGalleryMediaTypes];
  }
  if (array != nil) {
    for (NSString *atype in array) {
      if ([[atype lowercaseString] isEqualToString:type]) {
        return NUMBOOL(YES);
      }
    }
  }
  return NUMBOOL(NO);
}
#endif

- (void)takeScreenshot:(id)arg
{
  ENSURE_SINGLE_ARG(arg, KrollCallback);
  ENSURE_UI_THREAD(takeScreenshot, arg);

  // Create a graphics context with the target size

  CGSize imageSize = [[UIScreen mainScreen] bounds].size;
  UIGraphicsBeginImageContextWithOptions(imageSize, NO, 0);

  CGContextRef context = UIGraphicsGetCurrentContext();

  // Iterate over every window from back to front
  for (UIWindow *window in [[UIApplication sharedApplication] windows]) {
    if (![window respondsToSelector:@selector(screen)] || [window screen] == [UIScreen mainScreen]) {
      // -renderInContext: renders in the coordinate space of the layer,
      // so we must first apply the layer's geometry to the graphics context
      CGContextSaveGState(context);
      // Center the context around the window's anchor point
      CGContextTranslateCTM(context, [window center].x, [window center].y);
      // Apply the window's transform about the anchor point
      CGContextConcatCTM(context, [window transform]);
      // Offset by the portion of the bounds left of and above the anchor point
      CGContextTranslateCTM(context,
          -[window bounds].size.width * [[window layer] anchorPoint].x,
          -[window bounds].size.height * [[window layer] anchorPoint].y);

      // Render the layer hierarchy to the current context
      if ([window respondsToSelector:@selector(drawViewHierarchyInRect:afterScreenUpdates:)]) {
        [window drawViewHierarchyInRect:[window bounds] afterScreenUpdates:NO];
      } else {
        [[window layer] renderInContext:context];
      }

      // Restore the context
      CGContextRestoreGState(context);
    }
  }

  // Retrieve the screenshot image
  UIImage *image = UIGraphicsGetImageFromCurrentImageContext();

  UIGraphicsEndImageContext();

  TiBlob *blob = [[[TiBlob alloc] initWithImage:image] autorelease];
  NSDictionary *event = [NSDictionary dictionaryWithObject:blob forKey:@"media"];
  [self _fireEventToListener:@"screenshot" withObject:event listener:arg thisObject:nil];
}

#ifdef USE_TI_MEDIASAVETOPHOTOGALLERY
- (void)saveToPhotoGallery:(id)arg
{
  ENSURE_UI_THREAD(saveToPhotoGallery, arg);
  NSObject *image = [arg objectAtIndex:0];
  ENSURE_TYPE(image, NSObject);

  NSDictionary *saveCallbacks = nil;
  if ([arg count] > 1) {
    saveCallbacks = [arg objectAtIndex:1];
    ENSURE_TYPE(saveCallbacks, NSDictionary);
    KrollCallback *successCallback = [saveCallbacks valueForKey:@"success"];
    ENSURE_TYPE_OR_NIL(successCallback, KrollCallback);
    KrollCallback *errorCallback = [saveCallbacks valueForKey:@"error"];
    ENSURE_TYPE_OR_NIL(errorCallback, KrollCallback);
  }

  if ([image isKindOfClass:[TiBlob class]]) {
    TiBlob *blob = (TiBlob *)image;
    NSString *mime = [blob mimeType];

    if (mime == nil || [mime hasPrefix:@"image/"]) {
      UIImage *savedImage = [blob image];
      if (savedImage == nil)
        return;
      UIImageWriteToSavedPhotosAlbum(savedImage, self, @selector(saveCompletedForImage:error:contextInfo:), [saveCallbacks retain]);
    } else if ([mime hasPrefix:@"video/"]) {
      NSString *filePath;
      switch ([blob type]) {
      case TiBlobTypeFile: {
        filePath = [blob path];
        break;
      }
      case TiBlobTypeData: {
        // In this case, we need to write the blob data to a /tmp file and then load it.
        NSArray *typeinfo = [mime componentsSeparatedByString:@"/"];
        TiFile *tempFile = [TiUtils createTempFile:[typeinfo objectAtIndex:1]];
        filePath = [tempFile path];

        NSError *error = nil;
        [blob writeTo:filePath error:&error];

        if (error != nil) {
          NSString *message = [NSString stringWithFormat:@"problem writing to temporary file %@: %@", filePath, [TiUtils messageFromError:error]];
          NSMutableDictionary *event = [TiUtils dictionaryWithCode:[error code] message:message];
          [self dispatchCallback:[NSArray arrayWithObjects:@"error", event, [saveCallbacks valueForKey:@"error"], nil]];
          return;
        }

        // Have to keep the temp file from being deleted when we leave scope, so add it to the userinfo so it can be cleaned up there
        [saveCallbacks setValue:tempFile forKey:@"tempFile"];
        break;
      }
      default: {
        NSMutableDictionary *event = [TiUtils dictionaryWithCode:-1 message:@"invalid media format: MIME type was video/, but data is image"];
        [self dispatchCallback:[NSArray arrayWithObjects:@"error", event, [saveCallbacks valueForKey:@"error"], nil]];
        return;
      }
      }
      UISaveVideoAtPathToSavedPhotosAlbum(filePath, self, @selector(saveCompletedForVideo:error:contextInfo:), [saveCallbacks retain]);
    } else {
      KrollCallback *errorCallback = [saveCallbacks valueForKey:@"error"];
      if (errorCallback != nil) {
        NSMutableDictionary *event = [TiUtils dictionaryWithCode:-1 message:[NSString stringWithFormat:@"Invalid mime type: Expected either image/* or video/*, was: %@", mime]];
        [self dispatchCallback:[NSArray arrayWithObjects:@"error", event, errorCallback, nil]];
      } else {
        [self throwException:@"Invalid mime type"
                   subreason:[NSString stringWithFormat:@"Invalid mime type: Expected either image/* or video/*, was: %@", mime]
                    location:CODELOCATION];
      }
    }
  } else if ([image isKindOfClass:[TiFile class]]) {
    TiFile *file = (TiFile *)image;
    NSString *mime = [Mimetypes mimeTypeForExtension:[file path]];
    if (mime == nil || [mime hasPrefix:@"image/"]) {
      NSData *data = [NSData dataWithContentsOfFile:[file path]];
      UIImage *image = [[[UIImage alloc] initWithData:data] autorelease];
      UIImageWriteToSavedPhotosAlbum(image, self, @selector(saveCompletedForImage:error:contextInfo:), [saveCallbacks retain]);
    } else if ([mime hasPrefix:@"video/"]) {
      UISaveVideoAtPathToSavedPhotosAlbum([file path], self, @selector(saveCompletedForVideo:error:contextInfo:), [saveCallbacks retain]);
    }
  } else {
    KrollCallback *errorCallback = [saveCallbacks valueForKey:@"error"];
    if (errorCallback != nil) {
      NSMutableDictionary *event = [TiUtils dictionaryWithCode:-1 message:[NSString stringWithFormat:@"Invalid media type: Expected either TiBlob or TiFile, was: %@", JavascriptNameForClass([image class])]];
      [self dispatchCallback:[NSArray arrayWithObjects:@"error", event, errorCallback, nil]];
    } else {
      [self throwException:@"Invalid media type"
                 subreason:[NSString stringWithFormat:@"Expected either TiBlob or TiFile, was: %@", JavascriptNameForClass([image class])]
                  location:CODELOCATION];
    }
  }
}
#endif

/**
 Camera & Video Capture methods
 **/
#ifdef USE_TI_MEDIASHOWCAMERA
- (void)showCamera:(id)args
{
  ENSURE_SINGLE_ARG_OR_NIL(args, NSDictionary);
  if (![NSThread isMainThread]) {
    TiThreadPerformOnMainThread(
        ^{
          [self showCamera:args];
        },
        NO);
    return;
  }

  [self rememberProxy:[args objectForKey:@"overlay"]];
  [self showPicker:args isCamera:YES];
}
#endif

#ifdef USE_TI_MEDIAHIDECAMERA
- (void)hideCamera:(id)args
{
  [self destroyPickerCallbacks];
  //Hopefully, if we remove the callbacks before going to the main thread, we may reduce deadlock.
  ENSURE_UI_THREAD(hideCamera, args);
  if (picker != nil) {
    if (cameraView != nil) {
      [cameraView windowWillClose];
    }

    [[TiApp app] hideModalController:picker animated:animatedPicker];
    [[TiApp controller] repositionSubviews];

    if (cameraView != nil) {
      [cameraView windowDidClose];
      [self forgetProxy:cameraView];
      RELEASE_TO_NIL(cameraView);
    }
    [self destroyPicker];
  }
}
#endif

#ifdef USE_TI_MEDIATAKEPICTURE
- (void)takePicture:(id)args
{
  // must have a picker, doh
  if (picker == nil) {
    [self throwException:@"invalid state" subreason:nil location:CODELOCATION];
  }
  ENSURE_UI_THREAD(takePicture, args);
  [picker takePicture];
}
#endif

#ifdef USE_TI_MEDIASTARTVIDEOCAPTURE
- (void)startVideoCapture:(id)args
{
  // Return nothing
  ENSURE_UI_THREAD(startVideoCapture, args);
  // must have a picker, doh
  if (picker == nil) {
    [self throwException:@"invalid state" subreason:nil location:CODELOCATION];
  }
  [picker startVideoCapture];
}
#endif

#ifdef USE_TI_MEDIASTOPVIDEOCAPTURE
- (void)stopVideoCapture:(id)args
{
  ENSURE_UI_THREAD(stopVideoCapture, args);
  // must have a picker, doh
  if (picker != nil) {
    [picker stopVideoCapture];
  }
}
#endif

#ifdef USE_TI_MEDIASWITCHCAMERA
- (void)switchCamera:(id)args
{
  ENSURE_TYPE(args, NSArray);
  ENSURE_UI_THREAD(switchCamera, args);

  // TIMOB-17951
  if ([args objectAtIndex:0] == [NSNull null]) {
    return;
  }

  ENSURE_SINGLE_ARG(args, NSNumber);
  ENSURE_UI_THREAD(switchCamera, args);

  // must have a picker, doh
  if (picker == nil) {
    [self throwException:@"invalid state" subreason:nil location:CODELOCATION];
  }
  [picker setCameraDevice:[TiUtils intValue:args]];
}
#endif

//Undocumented property
#ifdef USE_TI_MEDIASHOWCAMERA
- (id)camera
{
  if (picker != nil) {
    return @([picker cameraDevice]);
  }
  return @(UIImagePickerControllerCameraDeviceRear);
}
#endif

#if defined(USE_TI_MEDIAREQUESTCAMERAPERMISSIONS)
//request camera access. for >= IOS7
- (void)requestCameraPermissions:(id)arg
{
  ENSURE_SINGLE_ARG(arg, KrollCallback);
  KrollCallback *callback = arg;
  TiThreadPerformOnMainThread(
      ^() {
        [AVCaptureDevice requestAccessForMediaType:AVMediaTypeVideo
                                 completionHandler:^(BOOL granted) {
                                   NSString *errorMessage = granted ? nil : @"The user denied access to use the camera.";
                                   KrollEvent *invocationEvent = [[KrollEvent alloc] initWithCallback:callback
                                                                                          eventObject:[TiUtils dictionaryWithCode:(granted ? 0 : 1) message:errorMessage]
                                                                                           thisObject:self];
                                   [[callback context] enqueue:invocationEvent];
                                   RELEASE_TO_NIL(invocationEvent);
                                 }];
      },
      NO);
}
#endif

#ifdef USE_TI_MEDIAHASCAMERAPERMISSIONS
- (NSNumber *)hasCameraPermissions:(id)unused
{
  NSString *cameraPermission = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"NSCameraUsageDescription"];

  if (!cameraPermission) {
    NSLog(@"[ERROR] iOS 10 and later requires the key \"NSCameraUsageDescription\" inside the plist in your tiapp.xml when accessing the native camera. Please add the key and re-run the application.");
  }

  return NUMBOOL([AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeVideo] == AVAuthorizationStatusAuthorized);
}
#endif

#ifdef USE_TI_MEDIAREQUESTPHOTOGALLERYPERMISSIONS
- (void)requestPhotoGalleryPermissions:(id)arg
{
  ENSURE_SINGLE_ARG(arg, KrollCallback);
  KrollCallback *callback = arg;

  TiThreadPerformOnMainThread(
      ^() {
        [PHPhotoLibrary requestAuthorization:^(PHAuthorizationStatus status) {
          BOOL granted = (status == PHAuthorizationStatusAuthorized);
          NSString *errorMessage = granted ? @"" : @"The user denied access to use the photo gallery.";
          KrollEvent *invocationEvent = [[[KrollEvent alloc] initWithCallback:callback
                                                                  eventObject:[TiUtils dictionaryWithCode:(granted ? 0 : 1) message:errorMessage]
                                                                   thisObject:self] autorelease];
          [[callback context] enqueue:invocationEvent];
        }];
      },
      YES);
}
#endif

#ifdef USE_TI_MEDIAHASPHOTOGALLERYPERMISSIONS
- (NSNumber *)hasPhotoGalleryPermissions:(id)unused
{
  NSString *readFromGalleryPermission = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"NSPhotoLibraryUsageDescription"];
  NSString *addToGalleryPermission = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"NSPhotoLibraryAddUsageDescription"];

  // Reading (!) from gallery permissions are required on iOS 10 and later.
  if (!readFromGalleryPermission) {
    NSLog(@"[ERROR] iOS 10 and later requires the key \"NSPhotoLibraryUsageDescription\" inside the plist in your tiapp.xml when accessing the photo library to store media. It will be ignored on devices < iOS 10. Please add the key and re-run the application.");
  }

  // Writing (!) to gallery permissions are required on iOS 11 and later.
  if (!addToGalleryPermission) {
    NSLog(@"[ERROR] iOS 11 and later requires the key \"NSPhotoLibraryAddUsageDescription\" inside the plist in your tiapp.xml when writing to the photo library to store media. It will be ignored on devices < iOS 11. Please add the key and re-run the application.");
  }

  return NUMBOOL([PHPhotoLibrary authorizationStatus] == PHAuthorizationStatusAuthorized);
}
#endif

/**
 End Camera Support
 **/
#ifdef USE_TI_MEDIAOPENPHOTOGALLERY
- (void)openPhotoGallery:(id)args
{
  ENSURE_SINGLE_ARG_OR_NIL(args, NSDictionary);
  ENSURE_UI_THREAD(openPhotoGallery, args);

  NSArray *types = (NSArray *)[args objectForKey:@"mediaTypes"];
#if IS_SDK_IOS_14
  if ([TiUtils isIOSVersionOrGreater:@"14.0"] && [TiUtils boolValue:[args objectForKey:@"allowMultiple"] def:NO]) {
    [self showPHPicker:args];
  } else {
#endif
    [self showPicker:args
            isCamera:NO];
#if IS_SDK_IOS_14
  }
#endif
}

#endif

/**
 Music Library Support
 **/
#ifdef USE_TI_MEDIAOPENMUSICLIBRARY
- (void)openMusicLibrary:(id)args
{
  ENSURE_SINGLE_ARG_OR_NIL(args, NSDictionary);
  ENSURE_UI_THREAD(openMusicLibrary, args);

  if (musicPicker != nil) {
    [self sendPickerError:MediaModuleErrorBusy];
    return;
  }

  animatedPicker = YES;

  // Have to perform setup & manual check for simulator; otherwise things
  // fail less than gracefully.
  [self commonPickerSetup:args];

// iPod not available on simulator
#if TARGET_OS_SIMULATOR
  [self sendPickerError:MediaModuleErrorNoMusicPlayer];
#else

  if (args != nil) {
    MPMediaType mediaTypes = 0;
    id mediaList = [args objectForKey:@"mediaTypes"];

    if (mediaList != nil) {
      if ([mediaList isKindOfClass:[NSArray class]]) {
        for (NSNumber *type in mediaList) {
          switch ([type integerValue]) {
          case MPMediaTypeMusic:
          case MPMediaTypeAnyAudio:
          case MPMediaTypeAudioBook:
          case MPMediaTypePodcast:
          case MPMediaTypeAny:
            mediaTypes |= [type integerValue];
          }
        }
      } else {
        ENSURE_TYPE(mediaList, NSNumber);
        switch ([mediaList integerValue]) {
        case MPMediaTypeMusic:
        case MPMediaTypeAnyAudio:
        case MPMediaTypeAudioBook:
        case MPMediaTypePodcast:
        case MPMediaTypeAny:
          mediaTypes = [mediaList integerValue];
        }
      }
    }

    if (mediaTypes == 0) {
      mediaTypes = MPMediaTypeAny;
    }

    musicPicker = [[MPMediaPickerController alloc] initWithMediaTypes:mediaTypes];
    musicPicker.allowsPickingMultipleItems = [TiUtils boolValue:[args objectForKey:@"allowMultipleSelections"] def:NO];
  } else {
    musicPicker = [[MPMediaPickerController alloc] init];
  }
  [musicPicker setDelegate:self];

  [self displayModalPicker:musicPicker settings:args];
#endif
}

- (void)hideMusicLibrary:(id)args
{
  ENSURE_UI_THREAD(hideMusicLibrary, args);
  if (musicPicker != nil) {
    [[TiApp app] hideModalController:musicPicker animated:animatedPicker];
    [[TiApp controller] repositionSubviews];
    [self destroyPicker];
  }
}
#endif

#ifdef USE_TI_MEDIAHASMUSICLIBRARYPERMISSIONS
- (NSNumber *)hasMusicLibraryPermissions:(id)unused
{
  // Will return true for iOS < 9.3, since authorization was introduced in iOS 9.3
  return NUMBOOL(![TiUtils isIOSVersionOrGreater:@"9.3"] || [MPMediaLibrary authorizationStatus] == MPMediaLibraryAuthorizationStatusAuthorized);
}
#endif

#ifdef USE_TI_MEDIAREQUESTMUSICLIBRARYPERMISSIONS
- (void)requestMusicLibraryPermissions:(id)args
{
  NSString *musicPermission = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"NSAppleMusicUsageDescription"];

  if (!musicPermission) {
    NSLog(@"[ERROR] iOS 10 and later requires the key \"NSAppleMusicUsageDescription\" inside the plist in your tiapp.xml when accessing the native microphone. Please add the key and re-run the application.");
  }

  ENSURE_SINGLE_ARG(args, KrollCallback);
  KrollCallback *callback = args;

  if ([TiUtils isIOSVersionOrGreater:@"9.3"]) {
    TiThreadPerformOnMainThread(
        ^() {
          [MPMediaLibrary requestAuthorization:^(MPMediaLibraryAuthorizationStatus status) {
            BOOL granted = status == MPMediaLibraryAuthorizationStatusAuthorized;
            KrollEvent *invocationEvent = [[KrollEvent alloc] initWithCallback:callback
                                                                   eventObject:[TiUtils dictionaryWithCode:(granted ? 0 : 1) message:nil]
                                                                    thisObject:self];
            [[callback context] enqueue:invocationEvent];
            RELEASE_TO_NIL(invocationEvent);
          }];
        },
        NO);
  } else {
    NSDictionary *propertiesDict = [TiUtils dictionaryWithCode:0 message:nil];
    NSArray *invocationArray = [[NSArray alloc] initWithObjects:&propertiesDict count:1];
    [callback call:invocationArray thisObject:self];
    [invocationArray release];
    return;
  }
}
#endif

#ifdef USE_TI_MEDIAQUERYMUSICLIBRARY
- (NSArray *)queryMusicLibrary:(id)arg
{
  ENSURE_SINGLE_ARG(arg, NSDictionary);

  NSString *musicPermission = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"NSAppleMusicUsageDescription"];

  if ([TiUtils isIOSVersionOrGreater:@"9.3"] && [MPMediaLibrary authorizationStatus] != MPMediaLibraryAuthorizationStatusAuthorized) {
    NSLog(@"[ERROR] It looks like you are accessing the music-library without sufficient permissions. Please use the Ti.Media.hasMusicLibraryPermissions() method to validate the permissions and only call this method if permissions are granted.");
  }

  if (!musicPermission) {
    NSLog(@"[ERROR] iOS 10 and later requires the key \"NSAppleMusicUsageDescription\" inside the plist in your tiapp.xml when accessing the native microphone. Please add the key and re-run the application.");
  }

  NSMutableSet *predicates = [NSMutableSet set];
  for (NSString *prop in [MediaModule filterableItemProperties]) {
    id value = [arg valueForKey:prop];
    if (value != nil) {
      if ([value isKindOfClass:[NSDictionary class]]) {
        id propVal = [value objectForKey:@"value"];
        bool exact = [TiUtils boolValue:[value objectForKey:@"exact"] def:YES];
        MPMediaPredicateComparison comparison = (exact) ? MPMediaPredicateComparisonEqualTo : MPMediaPredicateComparisonContains;
        [predicates addObject:[MPMediaPropertyPredicate predicateWithValue:propVal
                                                               forProperty:[[MediaModule filterableItemProperties] valueForKey:prop]
                                                            comparisonType:comparison]];
      } else {
        [predicates addObject:[MPMediaPropertyPredicate predicateWithValue:value
                                                               forProperty:[[MediaModule filterableItemProperties] valueForKey:prop]]];
      }
    }
  }

  MPMediaQuery *query = [[[MPMediaQuery alloc] initWithFilterPredicates:predicates] autorelease];
  NSMutableArray *result = [NSMutableArray arrayWithCapacity:[[query items] count]];
  for (MPMediaItem *item in [query items]) {
    TiMediaItem *newItem = [[[TiMediaItem alloc] _initWithPageContext:[self pageContext] item:item] autorelease];
    [result addObject:newItem];
  }
  return result;
}
#endif

/**
 End Music Library Support
 **/

/**
 Video Editing Support (undocumented)
 **/
#if defined(USE_TI_MEDIASTARTVIDEOEDITING) || defined(USE_TI_MEDIASTOPVIDEOEDITING)
- (void)startVideoEditing:(id)args
{
  ENSURE_SINGLE_ARG_OR_NIL(args, NSDictionary);
  ENSURE_UI_THREAD(startVideoEditing, args);

  RELEASE_TO_NIL(editor);

  BOOL animated = [TiUtils boolValue:@"animated" properties:args def:YES];
  id media = [args objectForKey:@"media"];

  editorSuccessCallback = [args objectForKey:@"success"];
  ENSURE_TYPE_OR_NIL(editorSuccessCallback, KrollCallback);
  [editorSuccessCallback retain];

  editorErrorCallback = [args objectForKey:@"error"];
  ENSURE_TYPE_OR_NIL(editorErrorCallback, KrollCallback);
  [editorErrorCallback retain];

  editorCancelCallback = [args objectForKey:@"cancel"];
  ENSURE_TYPE_OR_NIL(pickerCancelCallback, KrollCallback);
  [editorCancelCallback retain];

  //TODO: check canEditVideoAtPath

  editor = [[UIVideoEditorController alloc] init];
  editor.delegate = self;
  editor.videoQuality = [TiUtils intValue:@"videoQuality" properties:args def:UIImagePickerControllerQualityTypeMedium];
  editor.videoMaximumDuration = [TiUtils doubleValue:@"videoMaximumDuration" properties:args def:600];

  if ([media isKindOfClass:[NSString class]]) {
    NSURL *url = [TiUtils toURL:media proxy:self];
    editor.videoPath = [url path];
  } else if ([media isKindOfClass:[TiBlob class]]) {
    TiBlob *blob = (TiBlob *)media;
    editor.videoPath = [blob path];
  } else if ([media isKindOfClass:[TiFile class]]) {
    TiFile *file = (TiFile *)media;
    editor.videoPath = [file path];
  } else {
    RELEASE_TO_NIL(editor);
    NSLog(@"[ERROR] Unsupported video media: %@", [media class]);
    return;
  }

  TiApp *tiApp = [TiApp app];
  [tiApp showModalController:editor animated:animated];
}

- (void)stopVideoEditing:(id)args
{
  ENSURE_SINGLE_ARG_OR_NIL(args, NSDictionary);
  ENSURE_UI_THREAD(stopVideoEditing, args);

  if (editor != nil) {
    BOOL animated = [TiUtils boolValue:@"animated" properties:args def:YES];
    [[TiApp app] hideModalController:editor animated:animated];
    RELEASE_TO_NIL(editor);
  }
}
#endif

/**
 Video Editing Support Ends
 **/

#pragma mark Internal Methods

- (void)destroyPickerCallbacks
{
  RELEASE_TO_NIL(editorSuccessCallback);
  RELEASE_TO_NIL(editorErrorCallback);
  RELEASE_TO_NIL(editorCancelCallback);
  RELEASE_TO_NIL(pickerSuccessCallback);
  RELEASE_TO_NIL(pickerErrorCallback);
  RELEASE_TO_NIL(pickerCancelCallback);
}

- (void)destroyPicker
{
  [self forgetProxy:cameraView];
  RELEASE_TO_NIL(cameraView);
  RELEASE_TO_NIL(editorSuccessCallback);
  RELEASE_TO_NIL(editorErrorCallback);
  RELEASE_TO_NIL(editorCancelCallback);
#ifdef USE_TI_MEDIAOPENMUSICLIBRARY
  RELEASE_TO_NIL(musicPicker);
#endif
#if defined(USE_TI_MEDIASHOWCAMERA) || defined(USE_TI_MEDIAOPENPHOTOGALLERY)
  if ([TiUtils isIOSVersionOrGreater:@"13.0"]) {
    picker.presentationController.delegate = nil;
  }
  RELEASE_TO_NIL(picker);
#endif

#if IS_SDK_IOS_14 && defined(USE_TI_MEDIAOPENPHOTOGALLERY)
  _phPicker.presentationController.delegate = nil;
  RELEASE_TO_NIL(_phPicker);
#endif

#if defined(USE_TI_MEDIASTARTVIDEOEDITING) || defined(USE_TI_MEDIASTOPVIDEOEDITING)
  RELEASE_TO_NIL(editor);
#endif
  RELEASE_TO_NIL(pickerSuccessCallback);
  RELEASE_TO_NIL(pickerErrorCallback);
  RELEASE_TO_NIL(pickerCancelCallback);
}

- (void)dispatchCallback:(NSArray *)args
{
  NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];
  NSString *type = [args objectAtIndex:0];
  id object = [args objectAtIndex:1];
  id listener = [args objectAtIndex:2];
  // we have to give our modal picker view time to
  // dismiss with animation or if you do anything in a callback that
  // attempt to also touch a modal controller, you'll get into deep doodoo
  // wait for the picker to dismiss with animation
  [NSThread sleepForTimeInterval:0.5];
  [self _fireEventToListener:type withObject:object listener:listener thisObject:nil];
  [pool release];

  //TIMOB-24389: Force the heap to be GC'd to avoid Ti.Blob references to be dumped.
  KrollContext *krollContext = [self.pageContext krollContext];
  [krollContext forceGarbageCollectNow];
}

- (void)sendPickerError:(int)code
{
  id listener = [[pickerErrorCallback retain] autorelease];
  [self destroyPicker];
  if (listener != nil) {
    NSDictionary *event = [TiUtils dictionaryWithCode:code message:nil];
    [self dispatchCallback:@[ @"error", event, listener ]];
  }
}

- (void)sendPickerCancel
{
  id listener = [[pickerCancelCallback retain] autorelease];
  [self destroyPicker];
  if (listener != nil) {
    NSMutableDictionary *event = [TiUtils dictionaryWithCode:-1 message:@"The user cancelled the picker"];
    [self dispatchCallback:@[ @"cancel", event, listener ]];
  }
}

- (void)sendPickerSuccess:(id)event
{
  id listener = [[pickerSuccessCallback retain] autorelease];
  if (autoHidePicker) {
    [self destroyPicker];
  }
  if (listener != nil) {
    [self dispatchCallback:@[ @"success", event, listener ]];
  }
}

- (void)commonPickerSetup:(NSDictionary *)args
{
  if (args != nil) {
    pickerSuccessCallback = [args objectForKey:@"success"];
    ENSURE_TYPE_OR_NIL(pickerSuccessCallback, KrollCallback);
    [pickerSuccessCallback retain];

    pickerErrorCallback = [args objectForKey:@"error"];
    ENSURE_TYPE_OR_NIL(pickerErrorCallback, KrollCallback);
    [pickerErrorCallback retain];

    pickerCancelCallback = [args objectForKey:@"cancel"];
    ENSURE_TYPE_OR_NIL(pickerCancelCallback, KrollCallback);
    [pickerCancelCallback retain];

    // we use this to determine if we should hide the camera after taking
    // a picture/video -- you can programmatically take multiple pictures
    // and use your own controls so this allows you to control that
    // (similarly for ipod library picking)
    autoHidePicker = [TiUtils boolValue:@"autohide" properties:args def:YES];

    animatedPicker = [TiUtils boolValue:@"animated" properties:args def:YES];
  }
}

#if defined(USE_TI_MEDIASHOWCAMERA) || defined(USE_TI_MEDIAOPENPHOTOGALLERY)
- (void)displayCamera:(UIViewController *)picker_
{
  TiApp *tiApp = [TiApp app];
  [tiApp showModalController:picker_ animated:animatedPicker];
}
#endif

#if defined(USE_TI_MEDIASHOWCAMERA) || defined(USE_TI_MEDIAOPENPHOTOGALLERY) || defined(USE_TI_MEDIAOPENMUSICLIBRARY)
- (void)displayModalPicker:(UIViewController *)picker_ settings:(NSDictionary *)args
{
  TiApp *tiApp = [TiApp app];
  if (![TiUtils isIPad]) {
    if ([TiUtils isIOSVersionOrGreater:@"13.0"]) {
      picker_.presentationController.delegate = self;
    }
    [tiApp showModalController:picker_ animated:animatedPicker];
  } else {
    TiViewProxy *popoverViewProxy = [args objectForKey:@"popoverView"];

    if (![popoverViewProxy isKindOfClass:[TiViewProxy class]]) {
      popoverViewProxy = nil;
    }

    self.popoverView = popoverViewProxy;
    arrowDirection = [TiUtils intValue:@"arrowDirection" properties:args def:UIPopoverArrowDirectionAny];

    TiThreadPerformOnMainThread(
        ^{
          [self updatePopoverNow:picker_];
        },
        YES);
  }
}

- (void)updatePopoverNow:(UIViewController *)picker_
{
  UIViewController *theController = picker_;
  [theController setModalPresentationStyle:UIModalPresentationPopover];
  UIPopoverPresentationController *thePresenter = [theController popoverPresentationController];
  [thePresenter setPermittedArrowDirections:arrowDirection];
  [thePresenter setDelegate:self];
  [[TiApp app] showModalController:theController animated:animatedPicker];
  return;
}
#endif

- (void)closeModalPicker:(UIViewController *)picker_
{
  if (cameraView != nil) {
    [cameraView windowWillClose];
  }

  [[TiApp app] hideModalController:picker_ animated:animatedPicker];
  [[TiApp controller] repositionSubviews];

  if (cameraView != nil) {
    [cameraView windowDidClose];
    [self forgetProxy:cameraView];
    RELEASE_TO_NIL(cameraView);
  }
}

#if defined(USE_TI_MEDIASHOWCAMERA) || defined(USE_TI_MEDIAOPENPHOTOGALLERY)
- (void)showPicker:(NSDictionary *)args isCamera:(BOOL)isCamera
{
  if (picker != nil) {
    [self sendPickerError:MediaModuleErrorBusy];
    return;
  }
  BOOL customPicker = isCamera;

  BOOL inPopOver = [TiUtils boolValue:@"inPopOver" properties:args def:NO] && isCamera && [TiUtils isIPad];

  if (customPicker) {
    customPicker = !inPopOver;
  }

  if (customPicker) {
    picker = [[TiImagePickerController alloc] initWithProperties:args];
  } else {
    picker = [[UIImagePickerController alloc] init];
  }

  [picker setDelegate:self];

  animatedPicker = YES;
  saveToRoll = NO;
  BOOL editable = NO;
  UIImagePickerControllerSourceType ourSource = (isCamera ? UIImagePickerControllerSourceTypeCamera : UIImagePickerControllerSourceTypePhotoLibrary);

  if (args != nil) {
    [self commonPickerSetup:args];

    NSNumber *imageEditingObject = [args objectForKey:@"allowEditing"];
    saveToRoll = [TiUtils boolValue:@"saveToPhotoGallery" properties:args def:NO];

    if (imageEditingObject == nil) {
      imageEditingObject = [args objectForKey:@"allowImageEditing"];
    }

    editable = [TiUtils boolValue:imageEditingObject def:NO];
    [picker setAllowsEditing:editable];

    NSArray *sourceTypes = [UIImagePickerController availableMediaTypesForSourceType:ourSource];
    id types = [args objectForKey:@"mediaTypes"];

    BOOL movieRequired = NO;
    BOOL imageRequired = NO;
    BOOL livePhotoRequired = NO;

    if ([types isKindOfClass:[NSArray class]]) {
      for (int c = 0; c < [types count]; c++) {
        if ([[types objectAtIndex:c] isEqualToString:(NSString *)kUTTypeMovie]) {
          movieRequired = YES;
        } else if ([[types objectAtIndex:c] isEqualToString:(NSString *)kUTTypeImage]) {
          imageRequired = YES;
        }
      }
      picker.mediaTypes = [NSArray arrayWithArray:types];
    } else if ([types isKindOfClass:[NSString class]]) {
      if ([types isEqualToString:(NSString *)kUTTypeMovie] && ![sourceTypes containsObject:(NSString *)kUTTypeMovie]) {
        // no movie type supported...
        [self sendPickerError:MediaModuleErrorNoVideo];
        return;
      }
      picker.mediaTypes = [NSArray arrayWithObject:types];
    }

    // if we require movie but not image and we don't support movie, bail...
    if (movieRequired && !imageRequired && ![sourceTypes containsObject:(NSString *)kUTTypeMovie]) {
      // no movie type supported...
      [self sendPickerError:MediaModuleErrorNoCamera];
      return;
    }

    // iOS 10 requires a certain number of additional permissions declared in the Info.plist (<ios><plist/></ios>)
    NSString *microphonePermission = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"NSMicrophoneUsageDescription"];
    NSString *readFromGalleryPermission = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"NSPhotoLibraryUsageDescription"];
    NSString *addToGalleryPermission = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"NSPhotoLibraryAddUsageDescription"];

    // Microphone permissions are required when using the video-camera
    if (movieRequired && !microphonePermission) {
      NSLog(@"[ERROR] iOS 10 and later requires the key \"NSMicrophoneUsageDescription\" inside the plist in your tiapp.xml when accessing the native camera to take videos. Please add the key and re-run the application.");
    }

    // Gallery permissions are required when saving or selecting media from the gallery
    if (saveToRoll && !readFromGalleryPermission) {
      NSLog(@"[ERROR] iOS 10 and later requires the key \"NSPhotoLibraryUsageDescription\" inside the plist in your tiapp.xml when accessing the photo library to store media. Please add the key and re-run the application.");
    }

    // Writing (!) to gallery permissions are also required on iOS 11 and later.
    if (saveToRoll && !addToGalleryPermission) {
      NSLog(@"[ERROR] iOS 11 and later requires the key \"NSPhotoLibraryAddUsageDescription\" inside the plist in your tiapp.xml when writing to the photo library to store media. It will be ignored on devices < iOS 11. Please add the key and re-run the application.");
    }

    double videoMaximumDuration = [TiUtils doubleValue:[args objectForKey:@"videoMaximumDuration"] def:0.0];

    if (videoMaximumDuration != 0.0) {
      [picker setVideoMaximumDuration:videoMaximumDuration / 1000];
    }

    [picker setVideoQuality:[TiUtils intValue:[args objectForKey:@"videoQuality"] def:UIImagePickerControllerQualityTypeMedium]];
  }

  // do this afterwards above so we can first check for video support

  if (![UIImagePickerController isSourceTypeAvailable:ourSource]) {
    [self sendPickerError:MediaModuleErrorNoCamera];
    return;
  }
  [picker setSourceType:ourSource];

  // this must be done after we set the source type or you'll get an exception
  if (isCamera && ourSource == UIImagePickerControllerSourceTypeCamera) {
    // turn on/off camera controls - nice to turn off when you want to have your own UI
    [picker setShowsCameraControls:[TiUtils boolValue:@"showControls" properties:args def:YES]];

    // allow an overlay view
    TiViewProxy *cameraViewProxy = [args objectForKey:@"overlay"];
    if (cameraViewProxy != nil) {
      ENSURE_TYPE(cameraViewProxy, TiViewProxy);
      cameraView = [cameraViewProxy retain];
      UIView *view = [cameraView view];

#ifndef TI_USE_AUTOLAYOUT
      ApplyConstraintToViewWithBounds([cameraViewProxy layoutProperties], (TiUIView *)view, [[UIScreen mainScreen] bounds]);
#else
      [TiUtils setView:view
          positionRect:view.bounds];
#endif

      [cameraView windowWillOpen];
      [picker setCameraOverlayView:view];

      [cameraView windowDidOpen];
      [cameraView layoutChildren:NO];
    }

    // allow a transform on the preview image
    id transform = [args objectForKey:@"transform"];
    if (transform != nil) {
      ENSURE_TYPE(transform, Ti2DMatrix);
      [picker setCameraViewTransform:[transform matrix]];
    } else if (cameraView != nil && customPicker && ![TiUtils boolValue:@"showControls" properties:args def:YES]) {
      //No transforms in popover
      CGSize screenSize = [[UIScreen mainScreen] bounds].size;
      UIInterfaceOrientation orientation = [[UIApplication sharedApplication] statusBarOrientation];
      if (!UIInterfaceOrientationIsPortrait(orientation)) {
        screenSize = CGSizeMake(screenSize.height, screenSize.width);
      }
      float cameraAspectRatio = 4.0 / 3.0;
      float camViewHeight = screenSize.width * cameraAspectRatio;
      float scale = screenSize.height / camViewHeight;

      CGAffineTransform translate = CGAffineTransformMakeTranslation(0, (screenSize.height - camViewHeight) / 2.0);
      picker.cameraViewTransform = CGAffineTransformScale(translate, scale, scale);
    }
  }

  if (isCamera) {
    if (inPopOver) {
      [self displayModalPicker:picker settings:args];
    } else {
      [self displayCamera:picker];
    }
  } else {
    BOOL allowTranscoding = [TiUtils boolValue:@"allowTranscoding" properties:args def:YES];
    if (!allowTranscoding) {
      picker.videoExportPreset = AVAssetExportPresetPassthrough;
    }
    [self displayModalPicker:picker settings:args];
  }
}
#endif

#ifdef USE_TI_MEDIASAVETOPHOTOGALLERY
- (void)saveCompletedForImage:(UIImage *)image error:(NSError *)error contextInfo:(void *)contextInfo
{
  NSDictionary *saveCallbacks = (NSDictionary *)contextInfo;
  TiBlob *blob = [[[TiBlob alloc] initWithImage:image] autorelease];

  if (error != nil) {
    KrollCallback *errorCallback = [saveCallbacks objectForKey:@"error"];
    if (errorCallback != nil) {
      NSMutableDictionary *event = [TiUtils dictionaryWithCode:[error code] message:[TiUtils messageFromError:error]];
      [event setObject:blob forKey:@"image"];
      [self dispatchCallback:@[ @"error", event, errorCallback ]];
    }
    return;
  }

  KrollCallback *successCallback = [saveCallbacks objectForKey:@"success"];
  if (successCallback != nil) {
    NSMutableDictionary *event = [TiUtils dictionaryWithCode:0 message:nil];
    [event setObject:blob forKey:@"image"];
    [self dispatchCallback:@[ @"success", event, successCallback ]];
  }
}

- (void)saveCompletedForVideo:(NSString *)path error:(NSError *)error contextInfo:(void *)contextInfo
{
  NSDictionary *saveCallbacks = (NSDictionary *)contextInfo;
  if (error != nil) {
    KrollCallback *errorCallback = [saveCallbacks objectForKey:@"error"];
    if (errorCallback != nil) {
      NSMutableDictionary *event = [TiUtils dictionaryWithCode:[error code] message:[TiUtils messageFromError:error]];
      [event setObject:path forKey:@"path"];
      [self dispatchCallback:@[ @"error", event, errorCallback ]];
    }
    return;
  }

  KrollCallback *successCallback = [saveCallbacks objectForKey:@"success"];
  if (successCallback != nil) {
    NSMutableDictionary *event = [TiUtils dictionaryWithCode:0 message:nil];
    [event setObject:path forKey:@"path"];
    [self dispatchCallback:@[ @"success", event, successCallback ]];
  }

  // This object was retained for use in this callback; release it.
  [saveCallbacks release];
}
#endif

#if defined(USE_TI_MEDIASHOWCAMERA) || defined(USE_TI_MEDIAOPENPHOTOGALLERY) || defined(USE_TI_MEDIASTARTVIDEOEDITING)
- (void)handleTrimmedVideo:(NSURL *)theURL withDictionary:(NSDictionary *)dictionary
{
  TiBlob *media = [[[TiBlob alloc] initWithFile:[theURL path]] autorelease];
  NSMutableDictionary *eventDict = [NSMutableDictionary dictionaryWithDictionary:dictionary];
  [eventDict setObject:media forKey:@"media"];
  if (saveToRoll) {
    NSString *tempFilePath = [theURL absoluteString];
    UISaveVideoAtPathToSavedPhotosAlbum(tempFilePath, nil, nil, NULL);
  }

  [self sendPickerSuccess:eventDict];
}
#endif

#if IS_SDK_IOS_14 && defined(USE_TI_MEDIAOPENPHOTOGALLERY)
- (void)showPHPicker:(NSDictionary *)args
{
  if (_phPicker != nil) {
    [self sendPickerError:MediaModuleErrorBusy];
    return;
  }

  animatedPicker = YES;
  excludeLivePhoto = YES;

  PHPickerConfiguration *configuration = [[PHPickerConfiguration alloc] init];
  NSMutableArray *filterList = [NSMutableArray array];

  if (args != nil) {
    [self commonPickerSetup:args];

    BOOL allowMultiple = [TiUtils boolValue:[args objectForKey:@"allowMultiple"] def:NO];
    configuration.selectionLimit = [TiUtils intValue:[args objectForKey:@"selectionLimit"] def:allowMultiple ? 0 : 1];

    NSArray *mediaTypes = (NSArray *)[args objectForKey:@"mediaTypes"];
    if (mediaTypes) {
      for (NSString *mediaType in mediaTypes) {
        if ([mediaType isEqualToString:(NSString *)kUTTypeImage]) {
          [filterList addObject:PHPickerFilter.imagesFilter];
        } else if ([mediaType isEqualToString:(NSString *)kUTTypeLivePhoto]) {
          excludeLivePhoto = NO;
          [filterList addObject:PHPickerFilter.livePhotosFilter];
        } else if ([mediaType isEqualToString:(NSString *)kUTTypeMovie]) {
          [filterList addObject:PHPickerFilter.videosFilter];
        }
      }
    }
  }

  if (filterList.count == 0) {
    [filterList addObject:PHPickerFilter.imagesFilter];
  }

  PHPickerFilter *filter = [PHPickerFilter anyFilterMatchingSubfilters:filterList];
  configuration.filter = filter;

  _phPicker = [[PHPickerViewController alloc] initWithConfiguration:configuration];

  [_phPicker setDelegate:self];
  [self displayModalPicker:_phPicker settings:args];

  RELEASE_TO_NIL(configuration);
}

#pragma mark PHPickerViewControllerDelegate

- (void)picker:(PHPickerViewController *)picker didFinishPicking:(NSArray<PHPickerResult *> *)results
{
  // If user cancels, results count will be 0
  if (results.count == 0) {
    [self closeModalPicker:picker];
    [self sendPickerCancel];
    return;
  }

  dispatch_group_t group = dispatch_group_create();
  __block NSMutableArray<NSDictionary *> *imageArray = nil;
  __block NSMutableArray<NSDictionary *> *livePhotoArray = nil;
  __block NSMutableArray<NSDictionary *> *videoArray = nil;

  for (PHPickerResult *result in results) {
    dispatch_group_enter(group);

    // Live photo has image data as well. Untill live photo is not required, do not load it. Instead load image data.
    if (!excludeLivePhoto && [result.itemProvider canLoadObjectOfClass:PHLivePhoto.class]) {
      if (!livePhotoArray) {
        livePhotoArray = [[NSMutableArray alloc] init];
      }
      [result.itemProvider loadObjectOfClass:PHLivePhoto.class
                           completionHandler:^(__kindof id<NSItemProviderReading> _Nullable object, NSError *_Nullable error) {
                             if (!error) {
                               TiUIiOSLivePhoto *livePhoto = [[[TiUIiOSLivePhoto alloc] _initWithPageContext:[self pageContext]] autorelease];
                               [livePhoto setLivePhoto:(PHLivePhoto *)object];
                               [livePhotoArray addObject:@{@"livePhoto" : livePhoto,
                                 @"mediaType" : (NSString *)kUTTypeLivePhoto,
                                 @"success" : @(YES),
                                 @"code" : @(0)}];
                             } else {
                               [livePhotoArray addObject:@{@"error" : error.description,
                                 @"code" : @(error.code),
                                 @"success" : @(NO),
                                 @"mediaType" : (NSString *)kUTTypeLivePhoto}];
                               DebugLog(@"[ERROR] Failed to load live photo- %@ .", error.description);
                             }
                             dispatch_group_leave(group);
                           }];
    } else if ([result.itemProvider canLoadObjectOfClass:UIImage.class]) {
      if (!imageArray) {
        imageArray = [[NSMutableArray alloc] init];
      }
      [result.itemProvider loadObjectOfClass:UIImage.class
                           completionHandler:^(__kindof id<NSItemProviderReading> _Nullable object, NSError *_Nullable error) {
                             if (!error) {
                               TiBlob *media = [[[TiBlob alloc] initWithImage:(UIImage *)object] autorelease];
                               [imageArray addObject:@{@"media" : media,
                                 @"mediaType" : (NSString *)kUTTypeImage,
                                 @"success" : @(YES),
                                 @"code" : @(0)}];
                             } else {
                               [imageArray addObject:@{@"error" : error.description,
                                 @"code" : @(error.code),
                                 @"success" : @(NO),
                                 @"mediaType" : (NSString *)kUTTypeImage}];
                               DebugLog(@"[ERROR] Failed to load image- %@ .", error.description);
                             }
                             dispatch_group_leave(group);
                           }];
    } else if ([result.itemProvider hasItemConformingToTypeIdentifier:UTTypeMovie.identifier]) {
      if (!videoArray) {
        videoArray = [[NSMutableArray alloc] init];
      }
      [result.itemProvider loadFileRepresentationForTypeIdentifier:UTTypeMovie.identifier
                                                 completionHandler:^(NSURL *_Nullable url, NSError *_Nullable error) {
                                                   // As per discussion- https://developer.apple.com/forums/thread/652695
                                                   if (!error) {
                                                     NSString *filename = url.lastPathComponent;
                                                     NSFileManager *fileManager = NSFileManager.defaultManager;
                                                     NSURL *destPath = [fileManager.temporaryDirectory URLByAppendingPathComponent:filename];

                                                     NSError *copyError;

                                                     [fileManager copyItemAtURL:url
                                                                          toURL:destPath
                                                                          error:&copyError];
                                                     TiBlob *media = [[[TiBlob alloc] initWithFile:[destPath path]] autorelease];
                                                     if ([media mimeType] == nil) {
                                                       [media setMimeType:@"video/mpeg" type:TiBlobTypeFile];
                                                     }
                                                     [videoArray addObject:@{@"media" : media,
                                                       @"mediaType" : (NSString *)kUTTypeMovie,
                                                       @"success" : @(YES),
                                                       @"code" : @(0)}];
                                                   } else {
                                                     [videoArray addObject:@{@"error" : error.description,
                                                       @"code" : @(error.code),
                                                       @"success" : @(NO),
                                                       @"mediaType" : (NSString *)kUTTypeMovie}];
                                                     DebugLog(@"[ERROR] Failed to load video- %@ .", error.description);
                                                   }
                                                   dispatch_group_leave(group);
                                                 }];
    } else {
      dispatch_group_leave(group);
      NSLog(@"Unsupported media type");
    }
  }

  dispatch_group_notify(group, dispatch_get_global_queue(QOS_CLASS_DEFAULT, DISPATCH_QUEUE_PRIORITY_DEFAULT), ^{
    // Perform completition block
    NSMutableDictionary *dictionary = [TiUtils dictionaryWithCode:0 message:nil];
    if (livePhotoArray != nil) {
      [dictionary setObject:livePhotoArray forKey:@"livePhotos"];
    }
    if (imageArray != nil) {
      [dictionary setObject:imageArray forKey:@"images"];
    }
    if (videoArray != nil) {
      [dictionary setObject:videoArray forKey:@"videos"];
    }
    [self sendPickerSuccess:dictionary];
  });

  if (autoHidePicker) {
    [self closeModalPicker:picker];
  }
}
#endif

#pragma mark UIPopoverPresentationControllerDelegate

- (void)prepareForPopoverPresentation:(UIPopoverPresentationController *)popoverPresentationController
{
  if (self.popoverView != nil) {
    if ([self.popoverView supportsNavBarPositioning] && [self.popoverView isUsingBarButtonItem]) {
      UIBarButtonItem *theItem = [self.popoverView barButtonItem];
      if (theItem != nil) {
        popoverPresentationController.barButtonItem = [self.popoverView barButtonItem];
        return;
      }
    }

    UIView *view = [self.popoverView view];
    if (view != nil && (view.window != nil)) {
      popoverPresentationController.sourceView = view;
      popoverPresentationController.sourceRect = [view bounds];
      return;
    }
  }

  //Fell through.
  UIViewController *presentingController = [popoverPresentationController presentingViewController];
  popoverPresentationController.sourceView = [presentingController view];
  CGRect viewrect = [[presentingController view] bounds];
  if (viewrect.size.height > 50) {
    viewrect.size.height = 50;
  }
  popoverPresentationController.sourceRect = viewrect;
}

- (void)popoverPresentationController:(UIPopoverPresentationController *)popoverPresentationController willRepositionPopoverToRect:(inout CGRect *)rect inView:(inout UIView **)view
{
  //This will never be called when using bar button item
  UIView *theSourceView = *view;
  BOOL canUseSourceRect = (theSourceView == self.popoverView);
  rect->origin = CGPointMake(theSourceView.bounds.origin.x, theSourceView.bounds.origin.y);

  if (!canUseSourceRect && theSourceView.bounds.size.height > 50) {
    rect->size = CGSizeMake(theSourceView.bounds.size.width, 50);
  } else {
    rect->size = CGSizeMake(theSourceView.bounds.size.width, theSourceView.bounds.size.height);
  }

  popoverPresentationController.sourceRect = *rect;
}

- (void)popoverPresentationControllerDidDismissPopover:(UIPopoverPresentationController *)popoverPresentationController
{
#ifdef USE_TI_MEDIAOPENMUSICLIBRARY
  if ([popoverPresentationController presentedViewController] == musicPicker) {
    RELEASE_TO_NIL(musicPicker);
  }
#endif
  [self sendPickerCancel];
}

#pragma mark UIAdaptivePresentationControllerDelegate

#if IS_SDK_IOS_13
- (void)presentationControllerDidDismiss:(UIPresentationController *)presentationController
{
#if defined(USE_TI_MEDIASHOWCAMERA) || defined(USE_TI_MEDIAOPENPHOTOGALLERY) || defined(USE_TI_MEDIASTARTVIDEOEDITING)
#if IS_SDK_IOS_14 && defined(USE_TI_MEDIAOPENPHOTOGALLERY)
  [self closeModalPicker:picker ?: _phPicker];
#else
  [self closeModalPicker:picker];
#endif
  [self sendPickerCancel];
#endif
}
#endif

#pragma mark UIImagePickerControllerDelegate

#if defined(USE_TI_MEDIASHOWCAMERA) || defined(USE_TI_MEDIAOPENPHOTOGALLERY)
- (void)imagePickerController:(UIImagePickerController *)picker_ didFinishPickingMediaWithInfo:(NSDictionary *)editingInfo
{
  if (autoHidePicker) {
    [self closeModalPicker:picker];
  }

  NSString *mediaType = [editingInfo objectForKey:UIImagePickerControllerMediaType];
  if (mediaType == nil) {
    mediaType = (NSString *)kUTTypeImage; // default to in case older OS
  }

  BOOL isVideo = [mediaType isEqualToString:(NSString *)kUTTypeMovie];
  BOOL isLivePhoto = ([TiUtils isIOSVersionOrGreater:@"9.1"] && [mediaType isEqualToString:(NSString *)kUTTypeLivePhoto]);

  NSURL *mediaURL = [editingInfo objectForKey:UIImagePickerControllerMediaURL];

  NSDictionary *cropRect = nil;
  TiBlob *media = nil;
  TiUIiOSLivePhoto *livePhoto = nil;
  TiBlob *thumbnail = nil;

  BOOL imageWrittenToAlbum = NO;

  if (isVideo) {

    UIImage *thumbnailImage = [editingInfo objectForKey:UIImagePickerControllerOriginalImage];
    thumbnail = [[[TiBlob alloc] initWithImage:thumbnailImage] autorelease];

    if (picker.allowsEditing) {
      NSNumber *startTime = [editingInfo objectForKey:@"_UIImagePickerControllerVideoEditingStart"];
      NSNumber *endTime = [editingInfo objectForKey:@"_UIImagePickerControllerVideoEditingEnd"];

      if ((startTime != nil) && (endTime != nil)) {
        int startMilliseconds = ([startTime doubleValue] * 1000);
        int endMilliseconds = ([endTime doubleValue] * 1000);

        NSString *tmpDirectory = [[NSURL fileURLWithPath:NSTemporaryDirectory() isDirectory:YES] path];

        NSFileManager *manager = [NSFileManager defaultManager];
        NSString *outputURL = [tmpDirectory stringByAppendingPathComponent:@"editedVideo"];
        [manager createDirectoryAtPath:outputURL withIntermediateDirectories:YES attributes:nil error:nil];
        NSString *fileName = [[[NSString stringWithFormat:@"%f", CFAbsoluteTimeGetCurrent()] stringByReplacingOccurrencesOfString:@"." withString:@"-"] stringByAppendingString:@".MOV"];
        outputURL = [outputURL stringByAppendingPathComponent:fileName];
        AVURLAsset *videoAsset = [AVURLAsset URLAssetWithURL:mediaURL options:nil];
        AVAssetExportSession *exportSession = [[AVAssetExportSession alloc] initWithAsset:videoAsset presetName:AVAssetExportPresetHighestQuality];
        exportSession.outputURL = [NSURL fileURLWithPath:outputURL isDirectory:NO];
        exportSession.outputFileType = AVFileTypeQuickTimeMovie;
        CMTimeRange timeRange = CMTimeRangeMake(CMTimeMake(startMilliseconds, 1000), CMTimeMake(endMilliseconds - startMilliseconds, 1000));
        exportSession.timeRange = timeRange;

        NSMutableDictionary *dictionary = [TiUtils dictionaryWithCode:0 message:nil];
        [dictionary setObject:mediaType forKey:@"mediaType"];

        if (thumbnail != nil) {
          [dictionary setObject:thumbnail forKey:@"thumbnail"];
        }

        [exportSession exportAsynchronouslyWithCompletionHandler:^{
          switch (exportSession.status) {
          // If the export succeeds, return the URL of the proposed tmp-directory
          case AVAssetExportSessionStatusCompleted:
            [self handleTrimmedVideo:[NSURL URLWithString:outputURL] withDictionary:dictionary];
            break;
          // If it fails, return the original image URL
          default:
            [self handleTrimmedVideo:mediaURL withDictionary:dictionary];
            break;
          }
        }];
        return;
      }
    }

    media = [[[TiBlob alloc] initWithFile:[mediaURL path]] autorelease];
    if ([media mimeType] == nil) {
      [media setMimeType:@"video/mpeg" type:TiBlobTypeFile];
    }
    if (saveToRoll) {
      NSString *tempFilePath = [mediaURL path];
      UISaveVideoAtPathToSavedPhotosAlbum(tempFilePath, nil, nil, NULL);
    }
  } else {
    UIImage *editedImage = [editingInfo objectForKey:UIImagePickerControllerEditedImage];
    if ((mediaURL != nil) && (editedImage == nil)) {

      media = [[[TiBlob alloc] initWithFile:[mediaURL path]] autorelease];
      [media setMimeType:@"image/jpeg" type:TiBlobTypeFile];

      if (saveToRoll) {
        UIImage *image = [editingInfo objectForKey:UIImagePickerControllerOriginalImage];
        UIImageWriteToSavedPhotosAlbum(image, nil, nil, NULL);
      }
    } else {
      NSValue *ourRectValue = [editingInfo objectForKey:UIImagePickerControllerCropRect];
      if (ourRectValue != nil) {
        CGRect ourRect = [ourRectValue CGRectValue];
        cropRect = [NSDictionary dictionaryWithObjectsAndKeys:
                                     [NSNumber numberWithFloat:ourRect.origin.x], @"x",
                                 [NSNumber numberWithFloat:ourRect.origin.y], @"y",
                                 [NSNumber numberWithFloat:ourRect.size.width], @"width",
                                 [NSNumber numberWithFloat:ourRect.size.height], @"height",
                                 nil];
      }

      UIImage *resultImage = nil;
      UIImage *originalImage = [editingInfo objectForKey:UIImagePickerControllerOriginalImage];
      if ((editedImage != nil) && (ourRectValue != nil) && (originalImage != nil)) {

        CGRect ourRect = [ourRectValue CGRectValue];

        if ((ourRect.size.width > editedImage.size.width) || (ourRect.size.height > editedImage.size.height)) {
          UIGraphicsBeginImageContext(ourRect.size);
          CGContextRef context = UIGraphicsGetCurrentContext();
          CGContextSetRGBFillColor(context, 0.0, 0.0, 0.0, 1.0);
          CGContextFillRect(context, CGRectMake(0.0, 0.0, ourRect.size.width, ourRect.size.height));

          // translated rectangle for drawing sub image
          CGRect drawRect = CGRectMake(-ourRect.origin.x, -ourRect.origin.y, originalImage.size.width, originalImage.size.height);

          // clip to the bounds of the image context
          CGContextClipToRect(context, CGRectMake(0, 0, ourRect.size.width, ourRect.size.height));

          // draw image
          [originalImage drawInRect:drawRect];

          // grab image
          resultImage = UIGraphicsGetImageFromCurrentImageContext();

          UIGraphicsEndImageContext();
        }
      }

      if (resultImage == nil) {
        resultImage = [TiUtils adjustRotation:editedImage ?: originalImage];
      }

      media = [[[TiBlob alloc] initWithImage:resultImage] autorelease];

      if (saveToRoll) {
        UIImageWriteToSavedPhotosAlbum(resultImage, nil, nil, NULL);
      }
    }

    if (isLivePhoto) {
      livePhoto = [[[TiUIiOSLivePhoto alloc] _initWithPageContext:[self pageContext]] autorelease];
      [livePhoto setLivePhoto:[editingInfo objectForKey:UIImagePickerControllerLivePhoto]];
    }
  }

  NSMutableDictionary *dictionary = [TiUtils dictionaryWithCode:0 message:nil];
  [dictionary setObject:mediaType forKey:@"mediaType"];
  [dictionary setObject:media forKey:@"media"];

  if (thumbnail != nil) {
    [dictionary setObject:thumbnail forKey:@"thumbnail"];
  }
  if (livePhoto != nil) {
    [dictionary setObject:livePhoto forKey:@"livePhoto"];
  }
  if (cropRect != nil) {
    [dictionary setObject:cropRect forKey:@"cropRect"];
  }

  [self sendPickerSuccess:dictionary];
}

- (void)imagePickerControllerDidCancel:(UIImagePickerController *)picker_
{
  [self closeModalPicker:picker];
  [self sendPickerCancel];
}
#endif

#pragma mark MPMediaPickerControllerDelegate
#if defined(USE_TI_MEDIAOPENMUSICLIBRARY) || defined(USE_TI_MEDIAQUERYMUSICLIBRARY)
- (void)mediaPicker:(MPMediaPickerController *)mediaPicker_ didPickMediaItems:(MPMediaItemCollection *)collection
{
  if (autoHidePicker) {
    [self closeModalPicker:musicPicker];
  }

  TiMediaItem *representative = [[[TiMediaItem alloc] _initWithPageContext:[self pageContext] item:[collection representativeItem]] autorelease];
  NSNumber *mediaTypes = [NSNumber numberWithUnsignedInteger:[collection mediaTypes]];
  NSMutableArray *items = [NSMutableArray array];

  for (MPMediaItem *item in [collection items]) {
    TiMediaItem *newItem = [[[TiMediaItem alloc] _initWithPageContext:[self pageContext] item:item] autorelease];
    [items addObject:newItem];
  }

  NSMutableDictionary *picked = [TiUtils dictionaryWithCode:0 message:nil];
  [picked setObject:representative forKey:@"representative"];
  [picked setObject:mediaTypes forKey:@"types"];
  [picked setObject:items forKey:@"items"];

  [self sendPickerSuccess:picked];
}

- (void)mediaPickerDidCancel:(MPMediaPickerController *)mediaPicker_
{
  [self closeModalPicker:musicPicker];
  [self sendPickerCancel];
}
#endif

#pragma mark UIVideoEditorControllerDelegate

#ifdef USE_TI_MEDIASTARTVIDEOEDITING
- (void)videoEditorController:(UIVideoEditorController *)editor_ didSaveEditedVideoToPath:(NSString *)editedVideoPath
{
  id listener = [[editorSuccessCallback retain] autorelease];
  [self closeModalPicker:editor_];
  [self destroyPicker];

  if (listener != nil) {
    TiBlob *media = [[[TiBlob alloc] initWithFile:editedVideoPath] autorelease];
    [media setMimeType:@"video/mpeg" type:TiBlobTypeFile];
    NSMutableDictionary *event = [TiUtils dictionaryWithCode:0 message:nil];
    [event setObject:NUMBOOL(NO) forKey:@"cancel"];
    [event setObject:media forKey:@"media"];
    [self dispatchCallback:@[ @"error", event, listener ]];
  }
}

- (void)videoEditorControllerDidCancel:(UIVideoEditorController *)editor_
{
  id listener = [[editorCancelCallback retain] autorelease];
  [self closeModalPicker:editor_];
  [self destroyPicker];

  if (listener != nil) {
    NSMutableDictionary *event = [TiUtils dictionaryWithCode:-1 message:@"The user cancelled"];
    [event setObject:NUMBOOL(YES) forKey:@"cancel"];
    [self dispatchCallback:@[ @"error", event, listener ]];
  }
}

- (void)videoEditorController:(UIVideoEditorController *)editor_ didFailWithError:(NSError *)error
{
  id listener = [[editorErrorCallback retain] autorelease];
  [self closeModalPicker:editor_];
  [self destroyPicker];

  if (listener != nil) {
    NSMutableDictionary *event = [TiUtils dictionaryWithCode:[error code] message:[TiUtils messageFromError:error]];
    [event setObject:NUMBOOL(NO) forKey:@"cancel"];
    [self dispatchCallback:@[ @"error", event, listener ]];
  }
}
#endif

@end

#endif
