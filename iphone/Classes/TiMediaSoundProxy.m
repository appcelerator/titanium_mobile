/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2016 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_MEDIASOUND

#import <AVFoundation/AVFAudio.h>
#import <AudioToolbox/AudioToolbox.h>

#import "TiMediaAudioSession.h"
#import "TiMediaSoundProxy.h"
#import <TitaniumKit/TiBlob.h>
#import <TitaniumKit/TiFile.h>
#import <TitaniumKit/TiUtils.h>

@implementation TiMediaSoundProxy

#pragma mark Internal

- (AVAudioPlayer *)player
{
  if (player == nil && url != nil) {
    NSError *error = nil;
    player = [[AVAudioPlayer alloc] initWithContentsOfURL:url error:(NSError **)&error];
    if (error == nil) {
      [player setDelegate:self];
      [player prepareToPlay];
      [player setVolume:volume];
      [player setNumberOfLoops:(looping ? -1 : 0)];
      [player setCurrentTime:resumeTime];
    } else {
      [self throwException:[error description] subreason:[NSString stringWithFormat:@"error loading sound url: %@", url] location:CODELOCATION];
    }
  }
  return player;
}

- (void)_configure
{
  volume = 1.0;
  resumeTime = 0;

  TiThreadPerformOnMainThread(
      ^{
        [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(remoteControlEvent:) name:kTiRemoteControlNotification object:nil];
      },
      NO);
}

- (NSString *)apiName
{
  return @"Ti.Media.Sound";
}

- (void)_destroy
{
  if (player != nil) {
    if ([player isPlaying] || paused) {
      [player stop];
      [[TiMediaAudioSession sharedSession] stopAudioSession];
    }
    [player setDelegate:nil];
  }
  TiThreadPerformOnMainThread(
      ^{
        [[NSNotificationCenter defaultCenter] removeObserver:self];
      },
      YES);

  RELEASE_TO_NIL(player);
  RELEASE_TO_NIL(url);
  RELEASE_TO_NIL(tempFile);

  [super _destroy];
}

#pragma mark Public APIs

- (void)play:(id)args
{
  [self rememberSelf];
  TiThreadPerformOnMainThread(
      ^{
        // indicate we're going to start playback
        if (![[TiMediaAudioSession sharedSession] canPlayback]) {
          [self throwException:@"Improper audio session mode for playback"
                     subreason:[[TiMediaAudioSession sharedSession] sessionMode]
                      location:CODELOCATION];
        }

        if (player == nil || !([player isPlaying] || paused)) {
          [[TiMediaAudioSession sharedSession] startAudioSession];
        }
        [[self player] play];
        paused = NO;
      },
      NO);
}

- (void)stop:(id)args
{
  TiThreadPerformOnMainThread(
      ^{
        if (player != nil) {
          if ([player isPlaying] || paused) {
            [player stop];
            [player setCurrentTime:0];
            [[TiMediaAudioSession sharedSession] stopAudioSession];
          }
        }
        resumeTime = 0;
        paused = NO;
      },
      NO);
}

- (void)pause:(id)args
{
  TiThreadPerformOnMainThread(
      ^{
        if (player != nil) {
          if ([player isPlaying]) {
            [player pause];
            paused = YES;
          }
        }
      },
      NO);
}

- (void)reset:(id)args
{
  TiThreadPerformOnMainThread(
      ^{
        if (player != nil) {
          if (!([player isPlaying] || paused)) {
            [[TiMediaAudioSession sharedSession] startAudioSession];
          }

          [player stop];
          [player setCurrentTime:0];
          [player play];
        }
        resumeTime = 0;
        paused = NO;
      },
      NO);
}

- (void)release:(id)args
{
  if (player != nil) {
    resumeTime = 0;
    paused = NO;
    TiThreadPerformOnMainThread(
        ^{
          [player stop];
          RELEASE_TO_NIL(player);
        },
        YES);
  }
  [self forgetSelf];
  [self _destroy];
}

- (NSNumber *)volume
{
  if (player != nil) {
    return NUMFLOAT(player.volume);
  }
  return NUMFLOAT(0);
}

- (void)setVolume:(id)value
{
  volume = [TiUtils floatValue:value];
  if (player != nil) {
    [player setVolume:volume];
  }
}

- (NSNumber *)duration
{
  if (player != nil) {
    return NUMDOUBLE([player duration]);
  }
  return NUMDOUBLE(0);
}

- (NSNumber *)time
{
  if (player != nil) {
    return NUMDOUBLE([player currentTime] * 1000.0);
  }
  return NUMDOUBLE(resumeTime * 1000.0);
}

- (void)setTime:(NSNumber *)value
{
  if (player != nil) {
    [player setCurrentTime:([TiUtils doubleValue:(value)] / 1000.0)];
  } else {
    resumeTime = [TiUtils doubleValue:value] / 1000.0;
  }
}

- (NSNumber *)isPaused:(id)args
{
  return NUMBOOL(paused);
}

- (void)setPaused:(id)value
{
  if ([TiUtils boolValue:value]) {
    paused = YES;
  } else {
    paused = NO;
  }
  if (player != nil) {
    if (paused) {
      [player pause];
    } else {
      [player play];
    }
  }
}

- (NSNumber *)isLooping:(id)args
{
  if (player != nil) {
    return NUMBOOL(player.numberOfLoops != 0);
  }
  return NUMBOOL(NO);
}

- (void)setLooping:(id)value
{
  looping = [TiUtils boolValue:value];
  if (player != nil) {
    player.numberOfLoops = looping ? -1 : 0;
  }
}

- (NSNumber *)isPlaying:(id)args
{
  if (player != nil) {
    return NUMBOOL([player isPlaying]);
  }
  return NUMBOOL(NO);
}

- (NSNumber *)playing
{
  return [self isPlaying:nil];
}

- (NSNumber *)paused
{
  return [self isPaused:nil];
}

- (NSNumber *)looping
{
  return [self isLooping:nil];
}

- (void)setUrl:(id)url_
{
  if ([url_ isKindOfClass:[NSString class]]) {
    url = [[TiUtils toURL:url_ proxy:self] retain];
    if (![url isFileURL]) {
      // we need to download it and save it off into temp file
      NSData *data = [NSData dataWithContentsOfURL:url];
      NSString *ext = [[[url path] lastPathComponent] pathExtension];
      tempFile = [[TiFile createTempFile:ext] retain]; // file auto-deleted on release
      [data writeToFile:[tempFile path] atomically:YES];
      RELEASE_TO_NIL(url);
      url = [[NSURL fileURLWithPath:[tempFile path]] retain];
    }
  } else if ([url_ isKindOfClass:[TiBlob class]]) {
    TiBlob *blob = (TiBlob *)url_;
    //TODO: for now we're only supporting File-type blobs
    if ([blob type] == TiBlobTypeFile) {
      url = [[NSURL fileURLWithPath:[blob path]] retain];
    }
  } else if ([url_ isKindOfClass:[TiFile class]]) {
    url = [[NSURL fileURLWithPath:[(TiFile *)url_ path]] retain];
  }
  TiThreadPerformOnMainThread(
      ^{
        [self player]; // instantiate the player
      },
      YES);
}

- (NSURL *)url
{
  return url;
}

#pragma mark Delegate

- (void)audioPlayerDidFinishPlaying:(AVAudioPlayer *)player successfully:(BOOL)flag
{
  if ([self _hasListeners:@"complete"]) {
    NSString *message = flag ? nil : @"could not decode the audio data";
    [self fireEvent:@"complete" withObject:nil errorCode:(flag ? 0 : -1)message:message];
  }
  if (flag) {
    [[TiMediaAudioSession sharedSession] stopAudioSession];
  }
  [self forgetSelf];
}

- (void)audioPlayerDecodeErrorDidOccur:(AVAudioPlayer *)player error:(NSError *)error
{
  if ([self _hasListeners:@"error"]) {
    NSMutableDictionary *event = [TiUtils dictionaryWithCode:[error code] message:[TiUtils messageFromError:error]];
    [self fireEvent:@"error" withObject:event];
  }
  [self forgetSelf];
}

- (void)remoteControlEvent:(NSNotification *)note
{
  UIEvent *event = [[note userInfo] objectForKey:@"event"];
  switch (event.subtype) {
  case UIEventSubtypeRemoteControlTogglePlayPause: {
    if (paused) {
      [self play:nil];
    } else {
      [self pause:nil];
    }
    break;
  }
  case UIEventSubtypeRemoteControlPause: {
    [self pause:nil];
    break;
  }
  case UIEventSubtypeRemoteControlStop: {
    [self stop:nil];
    break;
  }
  case UIEventSubtypeRemoteControlPlay: {
    [self play:nil];
    break;
  }
  default: {
    break;
  }
  }
}

@end

#endif
