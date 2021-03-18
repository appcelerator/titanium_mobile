/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2016 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#if defined(USE_TI_MEDIAGETAPPMUSICPLAYER) || defined(USE_TI_MEDIAAPPMUSICPLAYER) || defined(USE_TI_MEDIAGETSYSTEMMUSICPLAYER) || defined(USE_TI_MEDIASYSTEMMUSICPLAYER)

#import "TiMediaMusicPlayer.h"
#import "MediaModule.h"

@implementation TiMediaMusicPlayer

#pragma mark Internal

// Has to happen on main thread or notifications screw up
- (void)initializePlayer
{
  WARN_IF_BACKGROUND_THREAD_OBJ; //NSNotificationCenter is not threadsafe!
  NSNotificationCenter *nc = [NSNotificationCenter defaultCenter];
  [nc addObserver:self selector:@selector(stateDidChange:) name:MPMusicPlayerControllerPlaybackStateDidChangeNotification object:player];
  [nc addObserver:self selector:@selector(playingDidChange:) name:MPMusicPlayerControllerNowPlayingItemDidChangeNotification object:player];
  [nc addObserver:self selector:@selector(volumeDidChange:) name:MPMusicPlayerControllerVolumeDidChangeNotification object:player];

  [player beginGeneratingPlaybackNotifications];
}

- (id)_initWithPageContext:(id<TiEvaluator>)context player:(MPMusicPlayerController *)player_
{
  if (self = [super _initWithPageContext:context]) {
    player = player_;
    WARN_IF_BACKGROUND_THREAD_OBJ; //NSNotificationCenter is not threadsafe!
    NSNotificationCenter *nc = [NSNotificationCenter defaultCenter];
    [nc addObserver:self selector:@selector(stateDidChange:) name:MPMusicPlayerControllerPlaybackStateDidChangeNotification object:player];
    [nc addObserver:self selector:@selector(playingDidChange:) name:MPMusicPlayerControllerNowPlayingItemDidChangeNotification object:player];
    [nc addObserver:self selector:@selector(volumeDidChange:) name:MPMusicPlayerControllerVolumeDidChangeNotification object:player];

    [player beginGeneratingPlaybackNotifications];
  }
  return self;
}

- (void)dealloc
{
  WARN_IF_BACKGROUND_THREAD_OBJ; //NSNotificationCenter is not threadsafe!
  NSNotificationCenter *nc = [NSNotificationCenter defaultCenter];
  [nc removeObserver:self name:MPMusicPlayerControllerPlaybackStateDidChangeNotification object:player];
  [nc removeObserver:self name:MPMusicPlayerControllerNowPlayingItemDidChangeNotification object:player];
  [nc removeObserver:self name:MPMusicPlayerControllerVolumeDidChangeNotification object:player];

  [player endGeneratingPlaybackNotifications];

  [super dealloc];
}

- (NSString *)apiName
{
  return @"Ti.Media.MusicPlayer";
}

#pragma mark Queue management

// Future-proofing for more sophisticated queue management
- (NSArray *)itemsFromArg:(id)args
{
  id arg = args;
  if ([args isKindOfClass:[NSArray class]]) {
    arg = [args objectAtIndex:0];
  }
  NSMutableArray *items = [NSMutableArray array];
  if ([arg isKindOfClass:[NSDictionary class]]) {
    for (TiMediaItem *item in [arg objectForKey:@"items"]) {
      [items addObject:[item item]];
    }
  } else if ([arg isKindOfClass:[NSArray class]]) {
    for (TiMediaItem *item in arg) {
      [items addObject:[item item]];
    }
  } else if ([arg isKindOfClass:[TiMediaItem class]]) {
    [items addObject:[(TiMediaItem *)arg item]];
  } else if ([arg isKindOfClass:[NSString class]]) {
    MPMediaQuery *query = [MPMediaQuery songsQuery]; // general songs query
    MPMediaPropertyPredicate *predicate = [MPMediaPropertyPredicate predicateWithValue:arg forProperty:MPMediaItemPropertyPersistentID];

    [query addFilterPredicate:predicate];
    if ([query.items count] == 0) {
      [self throwException:[NSString stringWithFormat:@"Invalid media item persistent ID %@ for player queue", arg]
                 subreason:nil
                  location:CODELOCATION];

      return items;
    }

    [items addObject:[query.items firstObject]];
  } else {
    [self throwException:[NSString stringWithFormat:@"Invalid object type %@ for player queue", [arg class]]
               subreason:nil
                location:CODELOCATION];
  }

  return items;
}

- (void)setQueue:(id)arg
{
  [player setQueueWithItemCollection:[MPMediaItemCollection collectionWithItems:[self itemsFromArg:arg]]];
}

#pragma mark Playback management

- (void)play:(id)unused
{
  [player play];
}

- (void)pause:(id)unused
{
  [player pause];
}

- (void)stop:(id)unused
{
  [player stop];
}

- (void)seekForward:(id)unused
{
  [player beginSeekingForward];
}

- (void)seekBackward:(id)unusued
{
  [player beginSeekingBackward];
}

- (void)stopSeeking:(id)unused
{
  [player endSeeking];
}

- (void)skipToNext:(id)unused
{
  [player skipToNextItem];
}

- (void)skipToBeginning:(id)unused
{
  [player skipToBeginning];
}

- (void)skipToPrevious:(id)unused
{
  [player skipToPreviousItem];
}

#pragma mark Property handlers

- (NSNumber *)currentPlaybackTime
{
  return NUMDOUBLE([player currentPlaybackTime]);
}

- (void)setCurrentPlaybackTime:(NSNumber *)time
{
  [player setCurrentPlaybackTime:[time doubleValue]];
}

- (NSNumber *)playbackState
{
  return @([player playbackState]);
}

- (TiMediaItem *)nowPlaying
{
  return [[[TiMediaItem alloc] _initWithPageContext:[self pageContext] item:[player nowPlayingItem]] autorelease];
}

- (NSNumber *)repeatMode
{
  return @([player repeatMode]);
}

- (void)setRepeatMode:(NSNumber *)mode
{
  // Sanity check
  switch ([mode intValue]) {
  case MPMusicRepeatModeAll:
  case MPMusicRepeatModeDefault:
  case MPMusicRepeatModeNone:
  case MPMusicRepeatModeOne:
    break;
  default:
    [self throwException:@"Invalid repeat mode"
               subreason:nil
                location:CODELOCATION];
  }
  [player setRepeatMode:[mode intValue]];
}

- (NSNumber *)shuffleMode
{
  return @([player shuffleMode]);
}

- (void)setShuffleMode:(NSNumber *)mode
{
  // Sanity check
  switch ([mode intValue]) {
  case MPMusicShuffleModeOff:
  case MPMusicShuffleModeSongs:
  case MPMusicShuffleModeDefault:
  case MPMusicShuffleModeAlbums:
    break;
  default:
    [self throwException:@"Invalid shuffle mode"
               subreason:nil
                location:CODELOCATION];
  }
  [player setShuffleMode:[mode intValue]];
}

- (NSNumber *)volume
{
  __block float volume = 1.0;
  if (player != nil) {
    TiThreadPerformOnMainThread(
        ^{
          volume = [TiUtils volumeFromObject:player default:volume];
        },
        YES);
  }

  return NUMFLOAT(volume);
}

- (void)setVolume:(NSNumber *)vol
{
  float volume = [TiUtils floatValue:vol def:-1];
  volume = MAX(0.0, MIN(volume, 1.0));
  if (player != nil) {
    TiThreadPerformOnMainThread(
        ^{
          [TiUtils setVolume:volume onObject:player];
        },
        NO);
  }
}

#pragma mark Notifications

// TODO: Change to KrollCallback properties for faster response times?
- (void)stateDidChange:(NSNotification *)note
{
  if ([self _hasListeners:@"stateChange"]) { //TODO: Deprecate old event.
    [self fireEvent:@"stateChange"];
  }
  if ([self _hasListeners:@"statechange"]) {
    [self fireEvent:@"statechange"];
  }
}

- (void)playingDidChange:(NSNotification *)note
{
  if ([self _hasListeners:@"playingChange"]) { //TODO: Deprecate old event.
    [self fireEvent:@"playingChange"];
  }
  if ([self _hasListeners:@"playingchange"]) {
    [self fireEvent:@"playingchange"];
  }
}

- (void)volumeDidChange:(NSNotification *)note
{
  if ([self _hasListeners:@"volumeChange"]) { //TODO: Deprecate old event.
    [self fireEvent:@"volumeChange"];
  }
  if ([self _hasListeners:@"volumechange"]) {
    [self fireEvent:@"volumechange"];
  }
}

@end

#endif
