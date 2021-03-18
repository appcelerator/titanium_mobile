/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_GESTURE

#import "GestureModule.h"
#import <TitaniumKit/TiUtils.h>

@implementation GestureModule

#pragma mark Internal

- (NSString *)apiName
{
  return @"Ti.Gesture";
}

- (void)shakeEvent:(NSNotification *)sender
{
  UIEvent *evt = [sender object];
  // since we get multiple motion end events we need to detect the first one and then ignore the subsequent
  // ones during the same shake motion
  if (evt.timestamp == 0 || evt.timestamp - lastShakeTime > 1) {
    NSDictionary *event = [NSDictionary dictionaryWithObject:NUMDOUBLE(evt.timestamp) forKey:@"timestamp"];
    [self fireEvent:@"shake" withDict:event];
    lastShakeTime = evt.timestamp;
  }
  // TODO: This will still allow "fast shakes" to send two events initially, since evt.timestamp is 0 initially.
  if (lastShakeTime == 0) {
    lastShakeTime = evt.timestamp;
  }
}

- (void)rotateEvent:(NSNotification *)sender
{
  NSDictionary *event = [NSDictionary dictionaryWithObject:[self orientation] forKey:@"orientation"];
  [self fireEvent:@"orientationchange" withDict:event];
}

- (void)registerForShake
{
  WARN_IF_BACKGROUND_THREAD_OBJ; //NSNotificationCenter is not threadsafe!
  [[NSNotificationCenter defaultCenter] addObserver:self
                                           selector:@selector(shakeEvent:)
                                               name:kTiGestureShakeNotification
                                             object:nil];
}

- (void)registerForOrientation
{
  [[UIDevice currentDevice] beginGeneratingDeviceOrientationNotifications];
  WARN_IF_BACKGROUND_THREAD_OBJ; //NSNotificationCenter is not threadsafe!
  [[NSNotificationCenter defaultCenter] addObserver:self
                                           selector:@selector(rotateEvent:)
                                               name:UIDeviceOrientationDidChangeNotification
                                             object:nil];
}

- (void)_listenerAdded:(NSString *)type count:(int)count
{
  if (count == 1 && [type isEqualToString:@"shake"]) {
    lastShakeTime = 0;
    TiThreadPerformOnMainThread(
        ^{
          [self registerForShake];
        },
        NO);
  } else if (count == 1 && [type isEqualToString:@"orientationchange"]) {
    TiThreadPerformOnMainThread(
        ^{
          [self registerForOrientation];
        },
        NO);
  }
}

- (void)unregisterForNotificationNamed:(NSString *)oldNotification
{
  WARN_IF_BACKGROUND_THREAD_OBJ; //NSNotificationCenter is not threadsafe!
  [[NSNotificationCenter defaultCenter] removeObserver:self name:oldNotification object:nil];
}

- (void)_listenerRemoved:(NSString *)type count:(int)count
{
  if (count == 0 && [type isEqualToString:@"shake"]) {
    TiThreadPerformOnMainThread(
        ^{
          [self unregisterForNotificationNamed:kTiGestureShakeNotification];
        },
        NO);
  } else if (count == 0 && [type isEqualToString:@"orientationchange"]) {
    TiThreadPerformOnMainThread(
        ^{
          [self unregisterForNotificationNamed:UIDeviceOrientationDidChangeNotification];
        },
        NO);
  }
}

- (bool)landscape
{
  return [TiUtils isOrientationLandscape];
}
GETTER_IMPL(bool, landscape, Landscape);

- (bool)portrait
{
  return [TiUtils isOrientationPortait];
}
GETTER_IMPL(bool, portrait, Portrait);

- (NSNumber *)orientation
{
  UIDeviceOrientation orientation = [UIDevice currentDevice].orientation;
  return @(orientation);
}
GETTER_IMPL(NSNumber *, orientation, Orientation);

@end

#endif
