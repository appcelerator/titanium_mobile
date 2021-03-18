/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2016 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_MEDIAVIDEOPLAYER

#import <AudioToolbox/AudioToolbox.h>
#import <QuartzCore/QuartzCore.h>

#import "TiMediaAudioSession.h"
#import "TiMediaVideoPlayer.h"
#import "TiMediaVideoPlayerProxy.h"
#import <TitaniumKit/TiApp.h>
#import <TitaniumKit/TiBlob.h>
#import <TitaniumKit/TiFile.h>
#import <TitaniumKit/TiUtils.h>
#import <TitaniumKit/TiViewProxy.h>
#import <TitaniumKit/Webcolor.h>

/**
 * Design Notes:
 *
 * Normally we'd use a ViewProxy/View pattern here ...but...
 *
 * Before 3.2, the player was always fullscreen and we were just a Proxy
 *
 * In 3.2, the player went to a different API with iPad where you could now
 * embedded the video in any view
 *
 * So, this class reflects the ability to work with both the older release
 * for older devices/apps and the newer style
 *
 */

#define RETURN_FROM_LOAD_PROPERTIES(property, default) \
  {                                                    \
    id temp = [loadProperties valueForKey:property];   \
    return temp ? temp : default;                      \
  }

@interface TiMediaVideoPlayerProxy ()
@property (nonatomic, readwrite, copy) NSNumber *mediaControlStyle;
@end

NSArray *moviePlayerKeys = nil;

@implementation TiMediaVideoPlayerProxy

#pragma mark Internal

- (NSArray *)keySequence
{
  if (moviePlayerKeys == nil) {
    moviePlayerKeys = [[NSArray alloc] initWithObjects:@"url", nil];
  }
  return moviePlayerKeys;
}

- (void)_initWithProperties:(NSDictionary *)properties
{
  loadProperties = [[NSMutableDictionary alloc] init];
  playerLock = [[NSRecursiveLock alloc] init];
  [super _initWithProperties:properties];
}

- (void)_destroy
{
  RELEASE_TO_NIL(thumbnailCallback);
  RELEASE_TO_NIL(tempFile);
  RELEASE_TO_NIL(url);
  RELEASE_TO_NIL(loadProperties);
  RELEASE_TO_NIL(playerLock);

  [super _destroy];
}

- (NSString *)apiName
{
  return @"Ti.Media.VideoPlayer";
}

- (void)addNotificationObserver
{
  WARN_IF_BACKGROUND_THREAD; //NSNotificationCenter is not threadsafe!
  NSNotificationCenter *nc = [NSNotificationCenter defaultCenter];

  // For durationavailable event
  [movie addObserver:self forKeyPath:@"player.currentItem.duration" options:NSKeyValueObservingOptionNew | NSKeyValueObservingOptionOld context:nil];

  // For playbackState property / playbackstate event
  [movie addObserver:self forKeyPath:@"player.timeControlStatus" options:NSKeyValueObservingOptionNew | NSKeyValueObservingOptionOld context:self];

  // For playing
  [self addObserver:self forKeyPath:@"url" options:NSKeyValueObservingOptionNew | NSKeyValueObservingOptionOld context:nil];

  // For load / loadstate / preload
  [movie addObserver:self forKeyPath:@"player.status" options:0 context:nil];

  // For naturalSize event
  [movie addObserver:self forKeyPath:@"player.currentItem.presentationSize" options:NSKeyValueObservingOptionNew | NSKeyValueObservingOptionInitial context:nil];

  // For complete event
  [nc addObserver:self selector:@selector(handlePlayerNotification:) name:AVPlayerItemDidPlayToEndTimeNotification object:[[movie player] currentItem]];

  // For error event
  [nc addObserver:self selector:@selector(handlePlayerErrorNotification:) name:AVPlayerItemFailedToPlayToEndTimeNotification object:[[movie player] currentItem]];
}

- (void)removeNotificationObserver
{
  if ([self observationInfo]) {
    [self removeObserver:self forKeyPath:@"url"];
  }
  [movie removeObserver:self forKeyPath:@"player.currentItem.duration"];
  [movie removeObserver:self forKeyPath:@"player.status"];
  [movie removeObserver:self forKeyPath:@"player.currentItem.presentationSize"];
  [movie removeObserver:self forKeyPath:@"player.timeControlStatus"];
}

- (void)configurePlayer
{
  [self addNotificationObserver];
  [self setValuesForKeysWithDictionary:loadProperties];

  // we need this code below since the player can be realized before loading
  // properties in certain cases and when we go to create it again after setting
  // url we will need to set the new controller to the already created view
  if ([self viewAttached]) {
    TiMediaVideoPlayer *vp = (TiMediaVideoPlayer *)[self view];
    [vp setMovie:movie];
  }
}

- (AVPlayerViewController *)ensurePlayer
{
  [playerLock lock];
  if (movie == nil) {
    if (url == nil) {
      [playerLock unlock];
      // this is OK - we just need to delay creation of the
      // player until after the url is set
      return nil;
    }
    movie = [[AVPlayerViewController alloc] init];
    [movie setPlayer:[AVPlayer playerWithURL:url]];
    [self configurePlayer];
  }
  [playerLock unlock];
  return movie;
}

- (TiUIView *)newView
{
  if (reallyAttached) {
    // override since we're constructing ourselfs
    TiUIView *v = [[TiMediaVideoPlayer alloc] initWithPlayer:[self ensurePlayer] proxy:self loaded:loaded];
    return v;
  }
  return nil;
}

- (void)viewWillAttach
{
  reallyAttached = YES;
}

- (void)viewDidDetach
{
  [self removeNotificationObserver];
  [[NSNotificationCenter defaultCenter] removeObserver:self];

  [[movie player] pause];
  [movie setPlayer:nil];
  RELEASE_TO_NIL(movie);
  reallyAttached = NO;
}

- (void)windowWillClose
{
  [super windowWillClose];
  [[movie player] pause];
  if ([self viewAttached]) {
    [(TiMediaVideoPlayer *)self.view setMovie:nil];
  }
}

#pragma mark Public APIs

- (void)setOverlayView:(id)proxy
{
  if (movie != nil && [movie view] != nil) {
    ENSURE_TYPE(proxy, TiViewProxy);
    for (UIView *view_ in [movie.contentOverlayView subviews]) {
      [view_ removeFromSuperview];
    }
    [movie.contentOverlayView addSubview:[proxy view]];
  } else {
    [loadProperties setValue:proxy forKey:@"overlayView"];
  }
}

- (void)setBackgroundView:(id)proxy
{
  DEPRECATED_REPLACED(@"Media.VideoPlayer.backgroundView", @"7.0.0", @"Media.VideoPlayer.overlayView")
      [self setOverlayView:proxy];
}

- (NSNumber *)playing
{
  return NUMBOOL(playing);
}

- (void)updateScalingMode:(id)value
{
  [movie setVideoGravity:[TiUtils stringValue:value properties:loadProperties def:AVLayerVideoGravityResize]];
}

- (void)setScalingMode:(NSString *)value
{
  [loadProperties setValue:value forKey:@"scalingMode"];

  if (movie != nil) {
    TiThreadPerformOnMainThread(
        ^{
          [self updateScalingMode:@"scalingMode"];
        },
        NO);
  }
}

- (NSString *)scalingMode
{
  if (movie != nil) {
    return [movie videoGravity];
  } else {
    RETURN_FROM_LOAD_PROPERTIES(@"scalingMode", AVLayerVideoGravityResize);
  }
}

- (void)setAllowsAirPlay:(id)value
{
  if (movie != nil) {
    [[movie player] setAllowsExternalPlayback:[TiUtils boolValue:value]];
  } else {
    [loadProperties setValue:value forKey:@"allowsAirPlay"];
  }
}

- (NSNumber *)allowsAirPlay
{
  if (movie != nil) {
    return NUMBOOL([[movie player] allowsExternalPlayback]);
  } else {
    RETURN_FROM_LOAD_PROPERTIES(@"allowsAirPlay", NUMBOOL(NO));
  }
}

- (void)setMediaControlStyle:(NSNumber *)value
{
  DEPRECATED_REPLACED_REMOVED(@"Media.VideoPlayer.mediaControlStyle", @"4.1.0", @"7.0.0", @"Media.VideoPlayer.showsControls");
}

- (NSNumber *)mediaControlStyle
{
  DEPRECATED_REPLACED_REMOVED(@"Media.VideoPlayer.mediaControlStyle", @"4.1.0", @"7.0.0", @"Media.VideoPlayer.showsControls");
  return NUMINT(0);
}

- (void)setMedia:(id)media_
{
  if ([media_ isKindOfClass:[TiFile class]]) {
    [self setUrl:[media_ absoluteURL]];
  } else if ([media_ isKindOfClass:[TiBlob class]]) {
    TiBlob *blob = (TiBlob *)media_;
    if ([blob type] == TiBlobTypeFile) {
      [self setUrl:[blob nativePath]];
    } else if ([blob type] == TiBlobTypeData) {
      RELEASE_TO_NIL(tempFile);
      tempFile = [[TiUtils createTempFile:@"mov"] retain];
      [blob writeTo:[tempFile path] error:nil];

      [self setUrl:[tempFile path]];
    } else {
      NSLog(@"[ERROR] Unsupported blob for video player: %@", media_);
    }
  } else {
    [self setUrl:media_];
  }
}

// Used to avoid duplicate code in Brightcove module; makes things easier to maintain.
- (void)restart
{
  BOOL restart = playing;
  if (playing) {
    [[movie player] pause];
    [movie setPlayer:nil];
    playing = NO;
  }

  if ([self viewAttached]) {
    TiMediaVideoPlayer *video = (TiMediaVideoPlayer *)[self view];
    if (movie != nil) {
      [[movie player] seekToTime:kCMTimeZero];
    } else {
      [self ensurePlayer];
    }
    [video frameSizeChanged:[video frame] bounds:[video bounds]];
  }

  if (restart) {
    TiThreadPerformOnMainThread(
        ^{
          [self play:nil];
        },
        NO);
  }
}

- (void)setUrl:(id)url_
{
  ENSURE_UI_THREAD(setUrl, url_);
  NSURL *newUrl = [TiUtils toURL:url_ proxy:self];

  if ([url isEqual:newUrl]) {
    return;
  }

  RELEASE_TO_NIL(url);

  url = [newUrl retain];
  loaded = NO;
  sizeSet = NO;
  if (movie != nil) {
    AVPlayerItem *newVideoItem = [AVPlayerItem playerItemWithURL:url];
    [[movie player] replaceCurrentItemWithPlayerItem:newVideoItem];
    [self removeNotificationObserver]; // Remove old observers
    [self addNotificationObserver]; // Add new oberservers
  } else {
    [self ensurePlayer];
  }
}

- (id)url
{
  return url;
}

- (NSNumber *)autoplay
{
  if (movie != nil) {
    return NUMBOOL([[[movie player] currentItem] isPlaybackLikelyToKeepUp]);
  } else {
    RETURN_FROM_LOAD_PROPERTIES(@"autoplay", NUMBOOL(YES));
  }
}

- (void)setAutoplay:(id)value
{
  [loadProperties setValue:value forKey:@"autoplay"];
}

- (NSNumber *)volume
{
  if (movie != nil) {
    __block float volume = 1.0;
    TiThreadPerformOnMainThread(
        ^{
          volume = [[movie player] volume];
        },
        YES);

    return NUMFLOAT(volume);
  } else {
    return NUMFLOAT(1.0);
  }
}

- (void)setVolume:(NSNumber *)newVolume
{
  float volume = [TiUtils floatValue:newVolume def:-1.0];
  volume = MAX(0.0, MIN(volume, 1.0));
  TiThreadPerformOnMainThread(
      ^{
        [[movie player] setVolume:volume];
      },
      YES);
}

- (void)requestThumbnailImagesAtTimes:(id)args
{
  ENSURE_ARG_COUNT(args, 3);
  ENSURE_TYPE([args objectAtIndex:0], NSArray);
  ENSURE_TYPE([args objectAtIndex:1], NSNumber);
  ENSURE_TYPE([args objectAtIndex:2], KrollCallback);

  NSArray *array = [args objectAtIndex:0];
  if ([array count] > 0) {
    NSMutableArray *cmTimeArray = [NSMutableArray arrayWithCapacity:[array count]];

    for (NSNumber *time in array) {
      CMTime cmTime = CMTimeMakeWithSeconds([time floatValue], 1);
      [cmTimeArray addObject:[NSValue valueWithCMTime:cmTime]];
    }

    AVAssetImageGenerator *imageGenerator = [AVAssetImageGenerator assetImageGeneratorWithAsset:[[[movie player] currentItem] asset]];
    NSNumber *option = [args objectAtIndex:1];

    if ([option intValue] == VideoTimeOptionExact) {
      imageGenerator.requestedTimeToleranceBefore = kCMTimeZero;
      imageGenerator.requestedTimeToleranceAfter = kCMTimeZero;
    }

    [imageGenerator cancelAllCGImageGeneration];

    RELEASE_TO_NIL(thumbnailCallback);
    callbackRequestCount = [array count];
    thumbnailCallback = [[args objectAtIndex:2] retain];

    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
      [imageGenerator generateCGImagesAsynchronouslyForTimes:cmTimeArray
                                           completionHandler:^(CMTime requestedTime, CGImageRef _Nullable imageRef, CMTime actualTime, AVAssetImageGeneratorResult result, NSError *_Nullable error) {
                                             NSMutableDictionary *event = [TiUtils dictionaryWithCode:[error code] message:[TiUtils messageFromError:error]];

                                             if (error == nil) {
                                               UIImage *image = [[UIImage alloc] initWithCGImage:imageRef];
                                               TiBlob *blob = [[[TiBlob alloc] initWithImage:image] autorelease];
                                               [event setObject:blob forKey:@"image"];
                                               [image release];
                                               [event setObject:NUMDOUBLE(actualTime.value / actualTime.timescale) forKey:@"time"];
                                             }

                                             TiThreadPerformOnMainThread(
                                                 ^{
                                                   [self _fireEventToListener:@"thumbnail" withObject:event listener:thumbnailCallback thisObject:nil];
                                                 },
                                                 YES);

                                             if (--callbackRequestCount <= 0) {
                                               RELEASE_TO_NIL(thumbnailCallback);
                                             }
                                           }];
    });
  }
}

- (NSNumber *)pictureInPictureEnabled
{
  if (movie) {
    return @(movie.allowsPictureInPicturePlayback);
  } else {
    RETURN_FROM_LOAD_PROPERTIES(@"pictureInPictureEnabled", @YES);
  }
}

- (void)setPictureInPictureEnabled:(NSNumber *)value
{
  if (movie) {
    movie.allowsPictureInPicturePlayback = [TiUtils boolValue:value def:YES];
  } else {
    [loadProperties setValue:value forKey:@"pictureInPictureEnabled"];
  }
}

- (NSNumber *)showsControls
{
  if (movie != nil) {
    return NUMBOOL([movie showsPlaybackControls]);
  } else {
    return NUMBOOL(YES);
  }
}

- (void)setShowsControls:(NSNumber *)value
{
  ENSURE_UI_THREAD(setShowsControls, value);

  if (movie != nil) {
    [movie setShowsPlaybackControls:[TiUtils boolValue:value def:YES]];
  } else {
    [loadProperties setValue:value forKey:@"showsControls"];
  }
}

- (void)cancelAllThumbnailImageRequests:(id)value
{
  TiThreadPerformOnMainThread(
      ^{
        AVAssetImageGenerator *imageGenerator = [AVAssetImageGenerator assetImageGeneratorWithAsset:[[[movie player] currentItem] asset]];
        [imageGenerator cancelAllCGImageGeneration];
      },
      NO);
}

- (void)setInitialPlaybackTime:(id)time
{
  ENSURE_UI_THREAD_1_ARG(time);
  if (movie != nil) {
    double ourTime = [TiUtils doubleValue:time];
    if (ourTime > 0 || isnan(ourTime)) {
      ourTime /= 1000.0f; // convert from milliseconds to seconds

      // Handle both setting this value on running videos and on creation
      if ([[movie player] status] == AVPlayerStatusReadyToPlay) {
        [[movie player] seekToTime:CMTimeMake(ourTime, 1) toleranceBefore:kCMTimeZero toleranceAfter:kCMTimeZero];
      } else {
        // Set the time in the "load" event
      }
    }
  }
  [loadProperties setValue:time forKey:@"initialPlaybackTime"];
}

- (NSNumber *)initialPlaybackTime
{
  RETURN_FROM_LOAD_PROPERTIES(@"initialPlaybackTime", NUMINT(0));
}

- (void)setBackgroundColor:(id)color
{
  [self replaceValue:color forKey:@"backgroundColor" notification:NO];

  RELEASE_TO_NIL(backgroundColor);
  backgroundColor = [[TiUtils colorValue:color] retain];

  if (movie != nil) {
    TiThreadPerformOnMainThread(
        ^{
          [[movie view] setBackgroundColor:[backgroundColor _color]];
        },
        NO);
    return;
  } else {
    [loadProperties setValue:color forKey:@"backgroundColor"];
  }
}

- (NSNumber *)playableDuration
{
  if (movie != nil && [[[[movie player] currentItem] asset] isPlayable] == YES) {
    return NUMINT(1000.0f * CMTimeGetSeconds([[[[movie player] currentItem] asset] duration]));
  } else {
    return NUMINT(0);
  }
}

- (NSNumber *)duration
{
  if (movie != nil) {
    return NUMFLOAT(1000.0f * CMTimeGetSeconds([[[[movie player] currentItem] asset] duration]));
  } else {
    return NUMFLOAT(0);
  }
}

- (NSNumber *)currentPlaybackTime
{
  if (movie != nil) {
    return NUMFLOAT(1000.0f * CMTimeGetSeconds([[[movie player] currentItem] currentTime]));
  } else {
    RETURN_FROM_LOAD_PROPERTIES(@"currentPlaybackTime", NUMFLOAT(0));
  }
}

- (void)setCurrentPlaybackTime:(id)time
{
  if (movie != nil) {
    [[[movie player] currentItem] seekToTime:CMTimeMake([TiUtils doubleValue:time], 1000)
                           completionHandler:^(BOOL finished){
                           }];
  } else {
    [loadProperties setValue:time forKey:@"currentPlaybackTime"];
  }
}

- (UIImage *)takeScreenshotFromPlayer:(CGSize)maxSize andSpecifiedTime:(CMTime)specifiedTime
{

  NSError *error;
  CMTime actualTime;

  AVAssetImageGenerator *generator = [[AVAssetImageGenerator alloc] initWithAsset:[[[movie player] currentItem] asset]];

  // Setting a maximum size is not necessary for this code to
  // successfully get a screenshot, but it was useful for my project.
  generator.maximumSize = maxSize;

  CGImageRef cgIm = [generator copyCGImageAtTime:specifiedTime
                                      actualTime:&actualTime
                                           error:&error];
  UIImage *image = [UIImage imageWithCGImage:cgIm];
  [generator release];

  if (cgIm == NULL) {
    DebugLog(@"[ERROR] Error making screenshot: Actual screenshot time: %f, requested screenshot time: %f", CMTimeGetSeconds(actualTime),
        CMTimeGetSeconds([[movie player] currentTime]));
    return nil;
  }

  CFRelease(cgIm);

  return image;
}

- (NSNumber *)endPlaybackTime
{
  if (movie != nil) {
    NSTimeInterval n = CMTimeGetSeconds([[[[movie player] currentItem] asset] duration]);
    if (n == -1) {
      n = NAN;
    }
    return NUMDOUBLE(1000.0f * n);
  } else {
    return NUMDOUBLE(0);
  }
}

- (NSNumber *)fullscreen
{
  DEPRECATED_REMOVED(@"Media.VideoPlayer.fullscreen", @"7.0.0", @"7.0.0");
  return NUMINT(-1);
}

- (void)setFullscreen:(id)value
{
  DEPRECATED_REMOVED(@"Media.VideoPlayer.fullscreen", @"7.0.0", @"7.0.0");
}

- (NSNumber *)moviePlayerStatus
{
  if ([movie player] != nil) {
    return @([[movie player] status]);
  }
  return @(AVPlayerStatusUnknown);
}

- (NSNumber *)loadState
{
  DEPRECATED_REPLACED(@"Media.VideoPlayer.loadState", @"7.0.0", @"Media.VideoPlayer.moviePlayerStatus");
  return [self moviePlayerStatus];
}

- (NSString *)mediaTypes
{
  // Available media types: https://developer.apple.com/library/prerelease/ios/documentation/AVFoundation/Reference/AVFoundation_Constants/index.html#//apple_ref/doc/constant_group/Media_Types
  // TODO: Not always use the first asset track
  if (movie != nil) {
    return [[[[[[movie player] currentItem] asset] tracks] objectAtIndex:0] mediaType];
  } else {
    return AVMediaTypeVideo;
  }
}

- (NSNumber *)playbackState
{
  if (_playbackState != TiVideoPlayerPlaybackStateUnknown) {
    return NUMINTEGER(_playbackState);
  }

  return NUMINTEGER(TiVideoPlayerPlaybackStateStopped);
}

- (void)setRepeatMode:(id)value
{
  [loadProperties setValue:value forKey:@"repeatMode"];
}

- (NSNumber *)repeatMode
{
  RETURN_FROM_LOAD_PROPERTIES(@"repeatMode", NUMINT(VideoRepeatModeNone));
}

- (id)naturalSize
{
  CGSize size = CGSizeZero;
  if (movie.player.currentItem) {
    size = movie.player.currentItem.presentationSize;
  }
  return @{
    @"width" : NUMFLOAT(size.width),
    @"height" : NUMFLOAT(size.height)
  };
}

- (TiColor *)backgroundColor
{
  if (movie != nil) {
    return backgroundColor;
  } else {
    RETURN_FROM_LOAD_PROPERTIES(@"backgroundColor", nil);
  }
}

- (void)stop:(id)args
{
  ENSURE_UI_THREAD(stop, args);

  playing = NO;

  [[movie player] seekToTime:CMTimeMake(0, 1)];
  [[movie player] pause];
}

- (void)play:(id)args
{
  ENSURE_UI_THREAD(play, args);
  if (playing) {
    return;
  }

  if (url == nil) {
    [self throwException:TiExceptionInvalidType
               subreason:@"Tried to play movie player without a valid url or media property"
                location:CODELOCATION];
  }

  playing = YES;
  AVPlayer *player = [[self ensurePlayer] player];

  if (seekToZeroBeforePlay) {
    seekToZeroBeforePlay = NO;
    [player seekToTime:kCMTimeZero];
  }

  [player play];
}

// Synonym for 'play' from the docs
- (void)start:(id)args
{
  DEPRECATED_REPLACED(@"Media.VideoPlayer.start", @"7.0.0", @"Media.VideoPlayer.play");
  [self play:args];
}

- (void)pause:(id)args
{
  ENSURE_UI_THREAD(pause, args)
  if (!playing) {
    return;
  }

  playing = NO;
  [[movie player] pause];
}

- (void)release:(id)args
{
  ENSURE_UI_THREAD(release, args);
  [self stop:nil];
  [self detachView];
  [self _destroy];
}

- (void)add:(id)viewProxy
{
  ENSURE_SINGLE_ARG(viewProxy, TiViewProxy);
  ENSURE_UI_THREAD(add, viewProxy);
  if (views == nil) {
    views = TiCreateNonRetainingArray();
  }
  [views addObject:viewProxy];
  [super add:viewProxy];
}

- (void)remove:(id)viewProxy
{
  ENSURE_SINGLE_ARG(viewProxy, TiViewProxy);
  [views removeObject:viewProxy];
  if ([self viewAttached]) {
    [super remove:viewProxy];
  }
}

#pragma mark Delegate Callbacks

- (void)handlePlayerNotification:(NSNotification *)notification
{
  if ([notification object] != [[movie player] currentItem]) {
    return;
  }

  NSString *name = [notification name];

  if ([name isEqualToString:AVPlayerItemDidPlayToEndTimeNotification]) {
    if ([self _hasListeners:@"complete"]) {
      NSNumber *reason = [[notification userInfo] objectForKey:AVPlayerItemDidPlayToEndTimeNotification];

      NSString *errorMessage;
      int errorCode;
      if ([reason intValue] == AVPlayerStatusFailed) {
        errorMessage = @"Video Playback encountered an error";
        errorCode = -1;
      } else {
        errorMessage = nil;
        errorCode = 0;
      }

      NSMutableDictionary *event;
      if (reason != nil) {
        event = [NSMutableDictionary dictionaryWithObject:reason forKey:@"reason"];
      } else {
        event = nil;
      }
      [self fireEvent:@"complete" withObject:event errorCode:errorCode message:errorMessage];
    }
    playing = NO;
    [self playerItemDidReachEnd];
    if ([[self repeatMode] intValue] == VideoRepeatModeOne) {
      [self play:nil];
    }
  }
}

- (void)handlePlayerErrorNotification:(NSNotification *)note
{
  NSError *error = note.userInfo[AVPlayerItemFailedToPlayToEndTimeErrorKey];
  _playbackState = TiVideoPlayerPlaybackStateInterrupted;

  if ([self _hasListeners:@"error"]) {
    NSDictionary *event = [TiUtils dictionaryWithCode:[error code] message:[TiUtils messageFromError:error]];
    [self fireEvent:@"error" withObject:event];
  }
}

/* Called when the player item has played to its end time. */
- (void)playerItemDidReachEnd
{
  seekToZeroBeforePlay = YES;
}

- (void)handleDurationAvailableNotification:(NSNotification *)note
{
  if ([self _hasListeners:@"durationAvailable"]) {
    DEPRECATED_REPLACED(@"Media.VideoPlayer.Event.durationAvailable", @"7.0.0", @"Media.VideoPlayer.Event.durationavailable");
    NSDictionary *event = [NSDictionary dictionaryWithObject:[self duration] forKey:@"duration"];
    [self fireEvent:@"durationAvailable" withObject:event];
  }

  if ([self _hasListeners:@"durationavailable"]) {
    NSDictionary *event = [NSDictionary dictionaryWithObject:[self duration] forKey:@"duration"];
    [self fireEvent:@"durationavailable" withObject:event];
  }
}

- (void)handleLoadStateChangeNotification:(NSNotification *)note
{
  if ([[movie player] status] == AVPlayerStatusReadyToPlay) {
    if ([self viewAttached]) {
      TiMediaVideoPlayer *vp = (TiMediaVideoPlayer *)[self view];
      loaded = YES;
      float initialPlaybackTime = [TiUtils floatValue:[loadProperties valueForKey:@"initialPlaybackTime"] def:0];
      [vp movieLoaded];

      if ([self _hasListeners:@"load"]) {
        [self fireEvent:@"load" withObject:nil];
      }

      if ([self _hasListeners:@"preload"]) {
        DEPRECATED_REPLACED(@"Media.VideoPlayer.preload", @"7.0.0", @"Media.VideoPlayer.load");
      }

      // Seek to the initial playback time if set
      if (initialPlaybackTime > 0) {
        [[movie player] seekToTime:CMTimeMake(initialPlaybackTime, 1000)];
      }

      // Start the video if autoplay is enabled
      if ([TiUtils boolValue:[loadProperties valueForKey:@"autoplay"]]) {
        [self play:nil];
      }
    } else {
      loaded = YES;
    }
  }

  if ([self _hasListeners:@"loadstate"]) {
    NSDictionary *event = [NSDictionary dictionaryWithObject:[self loadState] forKey:@"loadState"];
    [self fireEvent:@"loadstate" withObject:event];
  }
}

- (void)handleNowPlayingNotification:(NSNotification *)note
{
  _playbackState = TiVideoPlayerPlaybackStatePlaying;

  if ([self _hasListeners:@"playing"]) {
    NSDictionary *event = [NSDictionary dictionaryWithObject:NULL_IF_NIL([self url]) forKey:@"url"];
    [self fireEvent:@"playing" withObject:event];
  }
}

- (void)handleNaturalSizeAvailableNotification:(CGSize)size
{
  if ([self _hasListeners:@"naturalsizeavailable"]) {
    [self fireEvent:@"naturalsizeavailable"
         withObject:@{
           @"naturalSize" : @{
             @"width" : NUMFLOAT(size.width),
             @"height" : NUMFLOAT(size.height)
           }
         }];
  }
}

// iOS < 10
- (void)handlePlaybackStateChangeNotification:(NSNotification *)note
{
  TiVideoPlayerPlaybackState oldState = _playbackState;

  switch ([[movie player] status]) {
  case AVPlayerStatusUnknown:
  case AVPlayerStatusFailed:
    playing = NO;
    _playbackState = TiVideoPlayerPlaybackStateInterrupted;
    break;
  case AVPlayerStatusReadyToPlay:
    playing = ([[movie player] rate] == 1.0);
    if (playing) {
      _playbackState = TiVideoPlayerPlaybackStatePlaying;
    } else if (movie.player.currentItem.currentTime.value == 0 || !movie.player.currentItem.canStepBackward) {
      _playbackState = TiVideoPlayerPlaybackStateStopped;
    } else {
      _playbackState = TiVideoPlayerPlaybackStatePaused;
    }
    break;
  }

  if ([self _hasListeners:@"playbackstate"] && oldState != _playbackState) {
    NSDictionary *event = [NSDictionary dictionaryWithObject:[self playbackState] forKey:@"playbackState"];
    [self fireEvent:@"playbackstate" withObject:event];
  }
}

// iOS 10+
- (void)handleTimeControlStatusNotification:(NSNotification *)note
{
  TiVideoPlayerPlaybackState oldState = _playbackState;
  playing = movie.player.timeControlStatus == AVPlayerTimeControlStatusPlaying;

  if (movie.player.timeControlStatus == AVPlayerTimeControlStatusPlaying) {
    _playbackState = TiVideoPlayerPlaybackStatePlaying;
  } else if (movie.player.timeControlStatus == AVPlayerTimeControlStatusPaused) {
    if (movie.player.currentItem.currentTime.value == 0) {
      _playbackState = TiVideoPlayerPlaybackStateStopped;
    } else {
      _playbackState = TiVideoPlayerPlaybackStatePaused;
    }
  } else if ([TiUtils boolValue:[loadProperties valueForKey:@"autoplay"]]) {
    _playbackState = TiVideoPlayerPlaybackStatePlaying;
  }

  if ([self _hasListeners:@"playbackstate"] && oldState != _playbackState) {
    NSDictionary *event = [NSDictionary dictionaryWithObject:NUMINTEGER(_playbackState) forKey:@"playbackState"];
    [self fireEvent:@"playbackstate" withObject:event];
  }
}

- (void)observeValueForKeyPath:(NSString *)keyPath ofObject:(id)object change:(NSDictionary<NSString *, id> *)change context:(void *)context
{
  if ([keyPath isEqualToString:@"player.currentItem.duration"]) {
    [self handleDurationAvailableNotification:nil];
  }
  if ([keyPath isEqualToString:@"url"]) {
    [self handleNowPlayingNotification:nil];
  }
  if ([keyPath isEqualToString:@"player.status"]) {
    [self handleLoadStateChangeNotification:nil];
  }
  if ([keyPath isEqualToString:@"player.currentItem.presentationSize"]) {
    id sizeObject = [change objectForKey:@"new"];
    if (![sizeObject isKindOfClass:[NSNull class]]) {
      CGSize size = [sizeObject CGSizeValue];
      [self handleNaturalSizeAvailableNotification:size];
    }
  }
  if ([keyPath isEqualToString:@"player.timeControlStatus"]) {
    [self handleTimeControlStatusNotification:nil];
  }
}

@end

#endif
