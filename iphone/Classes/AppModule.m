/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2015 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_APP

#import "AppModule.h"
#import <TitaniumKit/ListenerEntry.h>
#import <TitaniumKit/TiApp.h>
#import <TitaniumKit/TiHost.h>
#if defined(USE_TI_APPIOS)
#import "TiAppiOSProxy.h"
#endif

#import "Titanium-Swift.h"
#import <TitaniumKit/TiLayoutQueue.h>
#import <UIKit/UILocalNotification.h>
#import <unistd.h>

extern NSString *const TI_APPLICATION_DEPLOYTYPE;
extern NSString *const TI_APPLICATION_ID;
extern NSString *const TI_APPLICATION_PUBLISHER;
extern NSString *const TI_APPLICATION_URL;
extern NSString *const TI_APPLICATION_NAME;
extern NSString *const TI_APPLICATION_VERSION;
extern NSString *const TI_APPLICATION_DESCRIPTION;
extern NSString *const TI_APPLICATION_COPYRIGHT;
extern NSString *const TI_APPLICATION_GUID;
extern BOOL const TI_APPLICATION_ANALYTICS;

@implementation AppModule

#if defined(DEBUG) || defined(DEVELOPER)

- (void)_restart:(id)unused
{
  TiThreadPerformOnMainThread(
      ^{
        [[[TiApp app] controller] shutdownUi:self];
      },
      NO);
}

- (void)_resumeRestart:(id)unused
{
  UIApplication *app = [UIApplication sharedApplication];
  TiApp *appDelegate = [TiApp app];
#ifndef TI_USE_AUTOLAYOUT
  [TiLayoutQueue resetQueue];
#endif
  /* Begin backgrounding simulation */
  [appDelegate applicationWillResignActive:app];
  [appDelegate applicationDidEnterBackground:app];
  [appDelegate endBackgrounding];
  /* End backgrounding simulation */

  /* Disconnect the old view system, intentionally leak controller and UIWindow */
  [[appDelegate window] removeFromSuperview];

  /* Disconnect the old modules. */
  NSNotificationCenter *nc = [NSNotificationCenter defaultCenter];
  NSMutableArray *delegateModules = (NSMutableArray *)[appDelegate valueForKey:@"modules"];
  for (id<Module> thisModule in delegateModules) {
    [nc removeObserver:thisModule];
  }
/* Because of other issues, we must leak the modules as well as the runtime */
#ifndef __clang_analyzer__
  [delegateModules copy];
#endif
  [delegateModules removeAllObjects];

  /* Disconnect the Kroll bridge, and spoof the shutdown */
  [nc removeObserver:[appDelegate krollBridge]];
  NSNotification *notification = [NSNotification notificationWithName:kTiContextShutdownNotification object:[appDelegate krollBridge]];
  [nc postNotification:notification];

  /* Begin foregrounding simulation */
  [appDelegate application:app didFinishLaunchingWithOptions:[appDelegate launchOptions]];
  [appDelegate applicationWillEnterForeground:app];
  [appDelegate applicationDidBecomeActive:app];
  /* End foregrounding simulation */
}

#endif

- (void)dealloc
{
  [appListeners removeAllObjects];
  RELEASE_TO_NIL(appListeners);
  RELEASE_TO_NIL(properties);
#ifdef USE_TI_APPIOS
  [self forgetProxy:iOS];
  RELEASE_TO_NIL(iOS);
#endif
  [[NSNotificationCenter defaultCenter] removeObserver:self];
  [super dealloc];
}

- (void)_configure
{
  [super _configure];
  [[NSNotificationCenter defaultCenter] addObserver:self
                                           selector:@selector(accessibilityVoiceOverStatusChanged:)
                                               name:UIAccessibilityVoiceOverStatusDidChangeNotification
                                             object:nil];
  [[NSNotificationCenter defaultCenter] addObserver:self
                                           selector:@selector(handleUserInteraction:)
                                               name:kTiUserInteraction
                                             object:nil];
}

- (NSString *)apiName
{
  return @"Ti.App";
}

- (void)addEventListener:(NSArray *)args
{
  NSString *type = [args objectAtIndex:0];
  id listener = [args objectAtIndex:1];

  if (appListeners == nil) {
    appListeners = [[NSMutableDictionary alloc] init];
  }

  id<TiEvaluator> context = [self executionContext] == nil ? [self pageContext] : [self executionContext];
  ListenerEntry *entry = [[ListenerEntry alloc] initWithListener:listener context:context proxy:self];

  if ([listener isKindOfClass:[KrollCallback class]]) {
    ((KrollCallback *)listener).type = type;
  } else {
    entry.type = type;
  }

  NSMutableArray *l = [appListeners objectForKey:type];
  if (l == nil) {
    l = [[NSMutableArray alloc] init];
    [appListeners setObject:l forKey:type];
    [l release];
  }
  [l addObject:entry];
  [entry release];
}

- (void)removeEventListener:(NSArray *)args
{
  NSString *type = [args objectAtIndex:0];
  id listener = [args objectAtIndex:1];

  ListenerEntry *entry = nil;

  NSMutableArray *l = [appListeners objectForKey:type];

  BOOL needsScanning;
  do {
    needsScanning = NO;
    for (entry in l) //The fast iteration is blindly fast when l is nil or count.
    {
      if ([listener isEqual:[entry listener]]) //NSNumber does the right thing with this too.
      {
        [l removeObject:entry]; //It's safe to modify the array as long as you break right after.
        needsScanning = [l count] > 0;
        break;
      }
    }
  } while (needsScanning);

  if ([appListeners count] == 0) {
    RELEASE_TO_NIL(appListeners);
  }

  [[self _host] removeListener:listener context:pageContext];
}

- (BOOL)_hasListeners:(NSString *)type
{
  if (appListeners != nil && [appListeners count] > 0) {
    NSArray *array = [appListeners objectForKey:type];

    if (array != nil && [array count] > 0) {
      return YES;
    }
  }
  return NO;
}

- (void)fireEvent:(NSArray *)args
{
  if (appListeners != nil) {
    id type = [args objectAtIndex:0];
    id obj = [args count] > 1 ? [args objectAtIndex:1] : nil;

    NSArray *array = [[appListeners objectForKey:type] copy];

    if (array != nil && [array count] > 0) {
      NSMutableDictionary *eventObject = nil;
      if ([obj isKindOfClass:[NSDictionary class]]) {
        eventObject = [NSMutableDictionary dictionaryWithDictionary:obj];
      } else {
        eventObject = [NSMutableDictionary dictionary];
      }
      [eventObject setValue:type forKey:@"type"];
      // since this is cross context, we need to force into a JSON so the data can serialize
      // we first force to string json, then we convert the string JSON back to a dictionary to
      // eliminate any native things like functions, native objects, etc.
      NSString *json_ = [TiUtils jsonStringify:eventObject];
      id jsonObject = [TiUtils jsonParse:json_ error:nil];

      for (ListenerEntry *entry in array) {
        // fire application level event
        [host fireEvent:[entry listener] withObject:jsonObject remove:NO context:[entry context] thisObject:nil];
      }
    }
    [array release];
  }
}

- (void)fireEvent:(NSString *)type withObject:(id)obj
{
  [self fireEvent:[NSArray arrayWithObjects:type, obj, nil]];
}

- (int)garbageCollect:(NSArray *)args
{
  KrollBridge *ourBridge = (KrollBridge *)[self executionContext];
  return [ourBridge forceGarbageCollectNow];
}

- (TiAppPropertiesProxy *)Properties
{
  if (properties == nil) {
    properties = [[TiAppPropertiesProxy alloc] _initWithPageContext:[self executionContext]];
  }
  return properties;
}

- (void)setIdleTimerDisabled:(NSNumber *)value
{
  [UIApplication sharedApplication].idleTimerDisabled = [TiUtils boolValue:value];
}

- (NSNumber *)idleTimerDisabled
{
  return NUMBOOL([UIApplication sharedApplication].idleTimerDisabled);
}

- (NSNumber *)proximityState
{
  return NUMBOOL([UIDevice currentDevice].proximityState);
}

- (void)setProximityDetection:(NSNumber *)value
{
  BOOL yn = [TiUtils boolValue:value];
  [UIDevice currentDevice].proximityMonitoringEnabled = yn;
  WARN_IF_BACKGROUND_THREAD_OBJ; //NSNotificationCenter is not threadsafe!
  if (yn) {
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(proximityDetectionChanged:)
                                                 name:UIDeviceProximityStateDidChangeNotification
                                               object:nil];
  } else {
    [[NSNotificationCenter defaultCenter] removeObserver:self name:UIDeviceProximityStateDidChangeNotification object:nil];
  }
}

- (NSNumber *)proximityDetection
{
  return NUMBOOL([UIDevice currentDevice].proximityMonitoringEnabled);
}

- (void)setDisableNetworkActivityIndicator:(NSNumber *)value
{
  BOOL yn = [TiUtils boolValue:value];
  [TiApp app].disableNetworkActivityIndicator = yn;
}

- (NSNumber *)disableNetworkActivityIndicator
{
  return NUMBOOL([TiApp app].disableNetworkActivityIndicator);
}

- (void)handleUserInteraction:(id)notification
{
  if ([self _hasListeners:@"userinteraction"]) {
    [self fireEvent:@"userinteraction" withObject:nil];
  }
}

//To fire the keyboard frame change event.
- (void)keyboardFrameChanged:(NSNotification *)notification
{
  if (![self _hasListeners:@"keyboardframechanged"]) {
    return;
  }

  NSDictionary *userInfo = [notification userInfo];
  NSNumber *duration = [userInfo objectForKey:UIKeyboardAnimationDurationUserInfoKey];
  CGRect keyboardEndFrame = [[userInfo objectForKey:UIKeyboardFrameEndUserInfoKey] CGRectValue];

  NSDictionary *event = [NSDictionary dictionaryWithObjectsAndKeys:
                                          [TiUtils rectToDictionary:keyboardEndFrame], @"keyboardFrame",
                                      duration, @"animationDuration",
                                      nil];

  [self fireEvent:@"keyboardframechanged" withObject:event];
}

- (void)timeChanged:(NSNotification *)notiication
{
  if ([self _hasListeners:@"significanttimechange"]) {
    [self fireEvent:@"significanttimechange" withObject:nil];
  }
}

#pragma mark Internal Memory Management

- (void)didReceiveMemoryWarning:(NSNotification *)notification
{
  if ([self _hasListeners:@"memorywarning"]) {
    [self fireEvent:@"memorywarning" withObject:nil];
  }

  RELEASE_TO_NIL(properties);
#ifdef USE_TI_APPIOS
  [self forgetProxy:iOS];
  RELEASE_TO_NIL(iOS);
#endif
  [super didReceiveMemoryWarning:notification];
}

- (void)willShutdown:(id)sender;
{
  // fire the application close event when shutting down
  if ([self _hasListeners:@"close"]) {
    [self fireEvent:@"close" withObject:nil];
  }
}

- (void)willShutdownContext:(NSNotification *)note
{
  // we have to check and see if this context has any listeners
  // that are registered at the global scope and that haven't been
  // removed and if so, we need to remove them since their context
  // is toast.
  if (appListeners != nil) {
    NSMutableArray *found = [NSMutableArray array];
    id context = [note object];
    for (NSString *type in appListeners) {
      for (ListenerEntry *entry in [appListeners objectForKey:type]) {
        if ([entry context] == context) {
          id listener = [entry listener];
          if ([listener isKindOfClass:[KrollCallback class]]) {
            [found addObject:[NSArray
                                 arrayWithObjects:((KrollCallback *)listener).type, listener, nil]];
          } else {
            [found addObject:[NSArray
                                 arrayWithObjects:[entry type], listener, nil]];
          }
        }
      }
    }
    if ([found count] > 0) {
      for (NSArray *a in found) {
        [self removeEventListener:a];
      }
    }
  }
}

- (void)startup
{
  WARN_IF_BACKGROUND_THREAD_OBJ; //NSNotificationCenter is not threadsafe!
  NSNotificationCenter *nc = [NSNotificationCenter defaultCenter];
  [nc addObserver:self selector:@selector(willShutdown:) name:kTiWillShutdownNotification object:nil];
  [nc addObserver:self selector:@selector(willShutdownContext:) name:kTiContextShutdownNotification object:nil];
  [nc addObserver:self selector:@selector(errored:) name:kTiErrorNotification object:nil];

  [nc addObserver:self selector:@selector(keyboardFrameChanged:) name:UIKeyboardWillChangeFrameNotification object:nil];
  [nc addObserver:self selector:@selector(timeChanged:) name:UIApplicationSignificantTimeChangeNotification object:nil];

  [super startup];
}

- (void)shutdown:(id)sender
{
  // make sure we force any changes made on shutdown
  [[NSUserDefaults standardUserDefaults] synchronize];
  WARN_IF_BACKGROUND_THREAD_OBJ; //NSNotificationCenter is not threadsafe!
  [[NSNotificationCenter defaultCenter] removeObserver:self];
  [super shutdown:sender];
}

- (void)paused:(id)sender
{
  if ([self _hasListeners:@"paused"]) {
    [self fireEvent:@"paused" withObject:nil];
  }
}

- (void)suspend:(id)sender
{
  // make sure we force any changes made on suspend in case we don't come back
  [[NSUserDefaults standardUserDefaults] synchronize];

  if ([self _hasListeners:@"pause"]) {
    [self fireEvent:@"pause" withObject:nil];
  }
}

- (void)resume:(id)sender
{
  if ([self _hasListeners:@"resume"]) {
    [self fireEvent:@"resume" withObject:nil];
  }
}

- (void)resumed:(id)sender
{
  if ([self _hasListeners:@"resumed"]) {
    [self fireEvent:@"resumed" withObject:nil];
  }
}

- (void)errored:(NSNotification *)notification
{
  if ([self _hasListeners:@"uncaughtException"]) {
    [self fireEvent:@"uncaughtException" withObject:[notification userInfo]];
  }
}

#pragma mark Delegate stuff

- (void)proximityDetectionChanged:(NSNotification *)note
{
  if ([self _hasListeners:@"proximity"]) {
    [self fireEvent:@"proximity" withObject:[NSDictionary dictionaryWithObject:[self proximityState] forKey:@"state"]];
  }
}

#pragma mark Public APIs

- (id)appURLToPath:(id)args
{
  ENSURE_SINGLE_ARG(args, NSString);
  if ([args hasPrefix:@"app://"]) {
    args = [args stringByReplacingOccurrencesOfString:@"app://" withString:@""];
  }
  return [[TiHost resourcePath] stringByAppendingPathComponent:args];
}

- (void)fireSystemEvent:(id)args
{
  NSString *eventName = nil;
  id argument = nil;
  UIAccessibilityNotifications notification;

  ENSURE_ARG_COUNT(args, 1);
  ENSURE_ARG_AT_INDEX(eventName, args, 0, NSString);

  if ([eventName isEqualToString:self.EVENT_ACCESSIBILITY_ANNOUNCEMENT]) {
    notification = UIAccessibilityAnnouncementNotification;
    ENSURE_ARG_COUNT(args, 2);
    ENSURE_ARG_AT_INDEX(argument, args, 1, NSString);
  } else if ([eventName isEqualToString:@"accessibilitylayoutchanged"]) {
    notification = UIAccessibilityLayoutChangedNotification;
  } else if ([eventName isEqualToString:@"accessibilityscreenchanged"]) {
    notification = UIAccessibilityScreenChangedNotification;
  } else {
    NSLog(@"[WARN] unknown system event: %@", eventName);
    return;
  }
  UIAccessibilityPostNotification(notification, argument);
}

- (NSNumber *)accessibilityEnabled
{
  return NUMBOOL(UIAccessibilityIsVoiceOverRunning());
}

- (void)accessibilityVoiceOverStatusChanged:(NSNotification *)notification
{
  if ([self _hasListeners:@"accessibilitychanged"]) {
    NSDictionary *event = [NSDictionary dictionaryWithObject:[self accessibilityEnabled] forKey:@"enabled"];
    [self fireEvent:@"accessibilitychanged" withObject:event];
  }
}

- (id)arguments:(id)args
{
  return [[TiApp app] launchOptions];
}

- (id)iD
{
  return TI_APPLICATION_ID;
}

- (id)installId
{
  return [TiUtils appIdentifier];
}

- (id)id
{
  return TI_APPLICATION_ID;
}

- (id)name
{
  return TI_APPLICATION_NAME;
}

- (id)version
{
  return TI_APPLICATION_VERSION;
}

- (id)publisher
{
  return TI_APPLICATION_PUBLISHER;
}

- (id)description
{
  return TI_APPLICATION_DESCRIPTION;
}

- (id)copyright
{
  return TI_APPLICATION_COPYRIGHT;
}

- (id)uRL
{
  return TI_APPLICATION_URL;
}

- (id)url
{
  return TI_APPLICATION_URL;
}

- (id)gUID
{
  return TI_APPLICATION_GUID;
}

- (id)guid
{
  return TI_APPLICATION_GUID;
}

- (id)deployType
{
  return TI_APPLICATION_DEPLOYTYPE;
}

- (id)sessionId
{
  return [[TiApp app] sessionId];
}

- (id)analytics
{
  return NUMBOOL(TI_APPLICATION_ANALYTICS);
}

- (NSNumber *)keyboardVisible
{
  return NUMBOOL([[[TiApp app] controller] keyboardVisible]);
}

- (void)setForceSplashAsSnapshot:(id)args
{
  ENSURE_SINGLE_ARG(args, NSNumber)
      [self replaceValue:args
                  forKey:@"forceSplashAsSnapshot"
            notification:NO];
  BOOL flag = [TiUtils boolValue:args def:NO];
  [[TiApp app] setForceSplashAsSnapshot:flag];
}

- (NSNumber *)forceSplashAsSnapshot
{
  return @([[TiApp app] forceSplashAsSnapshot]);
}

#if defined(USE_TI_APPIOS)
- (id)iOS
{
  if (iOS == nil) {
    iOS = [[TiAppiOSProxy alloc] _initWithPageContext:[self executionContext]];
    [self rememberProxy:iOS];
  }
  return iOS;
}
#endif

MAKE_SYSTEM_STR(EVENT_ACCESSIBILITY_ANNOUNCEMENT, @"accessibilityannouncement");
MAKE_SYSTEM_STR(EVENT_ACCESSIBILITY_CHANGED, @"accessibilitychanged");

@end

#endif
