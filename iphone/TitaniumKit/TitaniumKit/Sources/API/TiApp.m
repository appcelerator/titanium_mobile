/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2018 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#include <stdio.h>

#import "ImageLoader.h"
#import "Mimetypes.h"
#import "NSData+Additions.h"
#import "TiApp.h"
#import "TiBase.h"
#import "TiBlob.h"
#import "TiErrorController.h"
#import "TiExceptionHandler.h"
#import "TiLogServer.h"
#import "TiSharedConfig.h"
#import "Webcolor.h"
#import <AVFoundation/AVFoundation.h>
#import <CoreLocation/CoreLocation.h>
#import <CoreSpotlight/CoreSpotlight.h>
#import <QuartzCore/QuartzCore.h>
#import <libkern/OSAtomic.h>

TiApp *sharedApp;

NSString *TITANIUM_VERSION;

extern void UIColorFlushCache(void);

#define SHUTDOWN_TIMEOUT_IN_SEC 3

BOOL applicationInMemoryPanic = NO; // TODO: Remove in SDK 9.0+

// TODO: Remove in SDK 9.0+
TI_INLINE void waitForMemoryPanicCleared(void); //WARNING: This must never be run on main thread, or else there is a risk of deadlock!

@interface TiApp ()
- (void)checkBackgroundServices;
- (void)appBoot;
@end

@implementation TiApp

@synthesize window, controller;
@synthesize disableNetworkActivityIndicator;
@synthesize remoteNotification;
@synthesize pendingCompletionHandlers;
@synthesize backgroundTransferCompletionHandlers;
@synthesize localNotification;
@synthesize appBooted;
@synthesize userAgent;

+ (TiApp *)app
{
  return sharedApp;
}

+ (TiRootViewController *)controller;
{
  return [sharedApp controller];
}

- (JSContextGroupRef)contextGroup
{
  if (contextGroup == nil) {
    contextGroup = JSContextGroupCreate();
    JSContextGroupRetain(contextGroup);
  }
  return contextGroup;
}

+ (JSContextGroupRef)contextGroup
{
  return [sharedApp contextGroup];
}

- (NSMutableDictionary *)queuedBootEvents
{
  if (queuedBootEvents == nil) {
    queuedBootEvents = [[[NSMutableDictionary alloc] init] retain];
  }

  return queuedBootEvents;
}

- (NSMutableDictionary<NSString *, NSOrderedSet<id> *> *)queuedApplicationSelectors
{
  if (_queuedApplicationSelectors == nil) {
    _queuedApplicationSelectors = [[[NSMutableDictionary alloc] init] retain];
  }

  return _queuedApplicationSelectors;
}

- (void)startNetwork
{
  ENSURE_UI_THREAD_0_ARGS;
  if (OSAtomicIncrement32(&networkActivityCount) == 1) {
    [[UIApplication sharedApplication] setNetworkActivityIndicatorVisible:!disableNetworkActivityIndicator];
  }
}

- (void)stopNetwork
{
  ENSURE_UI_THREAD_0_ARGS;
  if (OSAtomicDecrement32(&networkActivityCount) == 0) {
    [[UIApplication sharedApplication] setNetworkActivityIndicatorVisible:NO];
  }
}

- (void)setDisableNetworkActivityIndicator:(BOOL)value
{
  disableNetworkActivityIndicator = value;
  [[UIApplication sharedApplication] setNetworkActivityIndicatorVisible:(!disableNetworkActivityIndicator && (networkActivityCount > 0))];
}

- (NSDictionary *)launchOptions
{
  return launchOptions;
}

- (NSMutableSet<id> *)applicationDelegates
{
  if (_applicationDelegates == nil) {
    _applicationDelegates = [[NSMutableSet alloc] init];
  }

  return _applicationDelegates;
}

- (void)initController
{
  sharedApp = self;

  // attach our main view controller
  controller = [[TiRootViewController alloc] init];
  // attach our main view controller... IF we haven't already loaded the main window.
  [window setRootViewController:controller];
  [window makeKeyAndVisible];
}

- (BOOL)windowIsKeyWindow
{
  return [window isKeyWindow];
}

- (UIView *)topMostView
{
  UIWindow *currentKeyWindow_ = [[UIApplication sharedApplication] keyWindow];
  return [[currentKeyWindow_ subviews] lastObject];
}

- (void)registerApplicationDelegate:(id)applicationDelegate
{
  if ([[self applicationDelegates] containsObject:applicationDelegate]) {
    NSLog(@"[WARN] Application delegate already exists in known application delegates!");
    return;
  }

  [[self applicationDelegates] addObject:applicationDelegate];
}

- (void)unregisterApplicationDelegate:(id<UIApplicationDelegate>)applicationDelegate
{
  if (![[self applicationDelegates] containsObject:applicationDelegate]) {
    NSLog(@"[WARN] Application delegate does not exist in known application delegates!");
    return;
  }

  [[self applicationDelegates] removeObject:applicationDelegate];
}

- (void)launchToUrl
{
  Class ApplicationDefaults = NSClassFromString(@"ApplicationDefaults");

  if (ApplicationDefaults == nil) {
    return;
  }

  NSDictionary *launchDefaults = [ApplicationDefaults performSelector:@selector(launchUrl)];
  if (launchDefaults != nil) {
    UIApplication *app = [UIApplication sharedApplication];
    NSURL *url = [NSURL URLWithString:[launchDefaults objectForKey:@"application-launch-url"]];
    if ([app canOpenURL:url]) {
      [app openURL:url options:@{} completionHandler:nil];
    } else {
      DebugLog(@"[WARN] The launch-url provided : %@ is invalid.", [launchDefaults objectForKey:@"application-launch-url"]);
    }
  }
}

- (void)createDefaultDirectories
{
  NSError *error = nil;
  [[NSFileManager defaultManager] URLForDirectory:NSApplicationSupportDirectory
                                         inDomain:NSUserDomainMask
                                appropriateForURL:nil
                                           create:YES
                                            error:&error];
  if (error) {
    DebugLog(@"[ERROR]  %@ %@", error, [error userInfo]);
  }
}

- (void)boot
{
  sessionId = [[TiUtils createUUID] retain];
  TITANIUM_VERSION = [[NSString stringWithCString:TI_VERSION_STR encoding:NSUTF8StringEncoding] retain];

  [self appBoot];
}

- (void)appBoot
{
  kjsBridge = [[KrollBridge alloc] initWithHost:self];

  [kjsBridge boot:self url:nil preload:nil];
  [[UIApplication sharedApplication] beginReceivingRemoteControlEvents];
}

- (void)booted:(id)bridge
{
  if ([bridge isKindOfClass:[KrollBridge class]]) {
    DebugLog(@"[DEBUG] Application booted in %f ms", ([NSDate timeIntervalSinceReferenceDate] - started) * 1000);
    fflush(stderr);
    appBooted = YES;

    if (launchedShortcutItem != nil) {
      [self handleShortcutItem:launchedShortcutItem queueToBootIfNotLaunched:YES];
      RELEASE_TO_NIL(launchedShortcutItem);
    }

    if (queuedBootEvents != nil) {
      for (NSString *notificationName in queuedBootEvents) {
        [[NSNotificationCenter defaultCenter] postNotificationName:notificationName object:self userInfo:[queuedBootEvents objectForKey:notificationName]];
      }
      RELEASE_TO_NIL(queuedBootEvents);
    }

    if (_applicationDelegates != nil) {
      for (NSString *selectorString in [self queuedApplicationSelectors]) {
        [self tryToInvokeSelector:NSSelectorFromString(selectorString) withArguments:[[self queuedApplicationSelectors] objectForKey:selectorString]];
      }
      [_queuedApplicationSelectors removeAllObjects];
    }
  }
}

- (void)applicationDidFinishLaunching:(UIApplication *)application
{
  [TiExceptionHandler defaultExceptionHandler];
  if ([[TiSharedConfig defaultConfig] logServerEnabled]) {
    [[TiLogServer defaultLogServer] start];
  }
  [self initController];
  [self launchToUrl];
  [self boot];
}

- (UIView *)splashScreenView
{
  if (splashScreenView == nil) {
    if ([TiUtils isUsingLaunchScreenStoryboard]) {
      UIStoryboard *launchScreen = [UIStoryboard storyboardWithName:@"LaunchScreen" bundle:nil];
      UIViewController *launchScreenViewController = [launchScreen instantiateInitialViewController];

      splashScreenView = [[launchScreenViewController view] retain];
    } else {
      splashScreenView = [[UIImageView alloc] init];
      [splashScreenView setAutoresizingMask:UIViewAutoresizingFlexibleHeight | UIViewAutoresizingFlexibleWidth];
      [splashScreenView setContentMode:UIViewContentModeScaleToFill];

      UIDeviceOrientation imageOrientation;
      UIUserInterfaceIdiom imageIdiom;

      UIImage *defaultImage = [controller defaultImageForOrientation:
                                              (UIDeviceOrientation)[[UIApplication sharedApplication] statusBarOrientation]
                                                resultingOrientation:&imageOrientation
                                                               idiom:&imageIdiom];
      [(UIImageView *)splashScreenView setImage:defaultImage];
      [splashScreenView setFrame:[[UIScreen mainScreen] bounds]];
    }
  }

  return splashScreenView;
}

- (void)generateNotification:(NSDictionary *)dict
{
  // Check and see if any keys from APS and the rest of the dictionary match; if they do, just
  // bump out the dictionary as-is
  remoteNotification = [[NSMutableDictionary alloc] initWithDictionary:dict];
  NSDictionary *aps = [dict objectForKey:@"aps"];
  for (NSString *key in aps) {
    if ([dict objectForKey:key] != nil) {
      DebugLog(@"[WARN] Conflicting keys in push APS dictionary and notification dictionary `%@`, not copying to toplevel from APS", key);
      continue;
    }
    [remoteNotification setValue:[aps valueForKey:key] forKey:key];
  }
}

- (BOOL)application:(UIApplication *)application shouldAllowExtensionPointIdentifier:(UIApplicationExtensionPointIdentifier)extensionPointIdentifier
{
  BOOL allowsCustomKeyboard = [TiUtils boolValue:[[TiApp tiAppProperties] objectForKey:@"allow-custom-keyboards"] def:YES];

  [self tryToInvokeSelector:@selector(application:shouldAllowExtensionPointIdentifier:)
              withArguments:[NSOrderedSet orderedSetWithObjects:application, extensionPointIdentifier, nil]];

  if ([extensionPointIdentifier isEqualToString:UIApplicationKeyboardExtensionPointIdentifier] && !allowsCustomKeyboard) {
    return NO;
  }

  return YES;
}

- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions_
{
  started = [NSDate timeIntervalSinceReferenceDate];
  [TiExceptionHandler defaultExceptionHandler];
  if ([[TiSharedConfig defaultConfig] logServerEnabled]) {
    [[TiLogServer defaultLogServer] start];
  }

  // Initialize the root-window
  window = [[UIWindow alloc] initWithFrame:[UIScreen mainScreen].bounds];

  // Initialize the launch options to be used by the client
  launchOptions = [[NSMutableDictionary alloc] initWithDictionary:launchOptions_];

  // Initialize the root-controller
  [self initController];

  // If we have a APNS-UUID, assign it
  NSString *apnsUUID = [[NSUserDefaults standardUserDefaults] stringForKey:@"APNSRemoteDeviceUUID"];
  if (apnsUUID != nil) {
    remoteDeviceUUID = [apnsUUID copy];
  }

  [[UNUserNotificationCenter currentNotificationCenter] setDelegate:self];

  // Get some launch options to validate before finish launching. Some of them
  // need to be mapepd from native to JS-types to be used by the client
  NSURL *urlOptions = [launchOptions objectForKey:UIApplicationLaunchOptionsURLKey];
  NSString *sourceBundleId = [launchOptions objectForKey:UIApplicationLaunchOptionsSourceApplicationKey];
  NSDictionary *_remoteNotification = [launchOptions objectForKey:UIApplicationLaunchOptionsRemoteNotificationKey];
  UILocalNotification *_localNotification = [launchOptions objectForKey:UIApplicationLaunchOptionsLocalNotificationKey];
  NSNumber *launchedLocation = [launchOptions objectForKey:UIApplicationLaunchOptionsLocationKey];
  UIApplicationShortcutItem *shortcut = [launchOptions objectForKey:UIApplicationLaunchOptionsShortcutItemKey];

  // Map background location key
  if (launchedLocation != nil) {
    [launchOptions setObject:launchedLocation forKey:@"launchOptionsLocationKey"];
    [launchOptions removeObjectForKey:UIApplicationLaunchOptionsLocationKey];
  }

  // Map local notification
  if (_localNotification != nil) {
    localNotification = [[[self class] dictionaryWithLocalNotification:_localNotification] retain];
    [launchOptions removeObjectForKey:UIApplicationLaunchOptionsLocalNotificationKey];

    // Queue the "localnotificationaction" event for iOS 9 and lower.
    // For iOS 10+, the "userNotificationCenter:didReceiveNotificationResponse:withCompletionHandler" delegate handles it
    if ([TiUtils isIOSVersionLower:@"9.0"]) {
      [self tryToPostNotification:localNotification withNotificationName:kTiLocalNotificationAction completionHandler:nil];
    }
  }

  // Map launched URL
  if (urlOptions != nil) {
    [launchOptions setObject:[urlOptions absoluteString] forKey:@"url"];
    [launchOptions removeObjectForKey:UIApplicationLaunchOptionsURLKey];
  }

  // Map launched App-ID
  if (sourceBundleId != nil) {
    [launchOptions setObject:sourceBundleId forKey:@"source"];
    [launchOptions removeObjectForKey:UIApplicationLaunchOptionsSourceApplicationKey];
  }

  // Generate remote notification of available
  if (_remoteNotification != nil) {
    [self generateNotification:_remoteNotification];
  }
  if (shortcut != nil) {
    launchedShortcutItem = [shortcut retain];
  }

  // Queue selector for usage in modules / Hyperloop
  [self tryToInvokeSelector:@selector(application:didFinishLaunchingWithOptions:)
              withArguments:[NSOrderedSet orderedSetWithObjects:application, launchOptions_, nil]];

  // If a "application-launch-url" is set, launch it directly
  [self launchToUrl];

  // Boot our kroll-core
  [self boot];

  // Create application support directory if not exists
  [self createDefaultDirectories];

  if ([TiUtils isIOSVersionOrGreater:@"13.0"]) {
    [self registerBackgroundTasks];
  }

  return YES;
}

// Handle URL-schemes / iOS >= 9
- (BOOL)application:(UIApplication *)app openURL:(NSURL *)url options:(NSDictionary<NSString *, id> *)options
{
  [self tryToInvokeSelector:@selector(application:openURL:options:)
              withArguments:[NSOrderedSet orderedSetWithObjects:app, url, options, nil]];

  [launchOptions removeObjectForKey:UIApplicationLaunchOptionsURLKey];
  [launchOptions setObject:[url absoluteString] forKey:@"url"];
  [launchOptions removeObjectForKey:UIApplicationLaunchOptionsSourceApplicationKey];

  id source = [options objectForKey:UIApplicationOpenURLOptionsSourceApplicationKey];
  if (source != nil) {
    [launchOptions setObject:source forKey:@"source"];
  } else {
    [launchOptions removeObjectForKey:@"source"];
  }

  if (appBooted) {
    [[NSNotificationCenter defaultCenter] postNotificationName:kTiApplicationLaunchedFromURL object:self userInfo:launchOptions];
  } else {
    [[self queuedBootEvents] setObject:launchOptions forKey:kTiApplicationLaunchedFromURL];
  }

  return YES;
}

// Handle URL-schemes / iOS < 9
- (BOOL)application:(UIApplication *)application openURL:(NSURL *)url sourceApplication:(NSString *)sourceApplication annotation:(id)annotation
{
  [self tryToInvokeSelector:@selector(application:sourceApplication:annotation:)
              withArguments:[NSOrderedSet orderedSetWithObjects:application, sourceApplication, annotation, nil]];

  [launchOptions removeObjectForKey:UIApplicationLaunchOptionsURLKey];
  [launchOptions setObject:[url absoluteString] forKey:@"url"];
  [launchOptions removeObjectForKey:UIApplicationLaunchOptionsSourceApplicationKey];

  if (sourceApplication != nil) {
    [launchOptions setObject:sourceApplication forKey:@"source"];
  } else {
    [launchOptions removeObjectForKey:@"source"];
  }

  if (appBooted) {
    [[NSNotificationCenter defaultCenter] postNotificationName:kTiApplicationLaunchedFromURL object:self userInfo:launchOptions];
  } else {
    [[self queuedBootEvents] setObject:launchOptions forKey:kTiApplicationLaunchedFromURL];
  }

  return YES;
}

#pragma mark Background Fetch

#ifdef USE_TI_FETCH

- (void)application:(UIApplication *)application performFetchWithCompletionHandler:(void (^)(UIBackgroundFetchResult))completionHandler
{
  [self tryToInvokeSelector:@selector(application:performFetchWithCompletionHandler:)
              withArguments:[NSOrderedSet orderedSetWithObjects:application, [completionHandler copy], nil]];

  //Only for simulator builds
  NSArray *backgroundModes = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"UIBackgroundModes"];
  if ([backgroundModes containsObject:@"fetch"]) {

    // Generate unique key with timestamp.
    NSString *key = [NSString stringWithFormat:@"Fetch-%f", [[NSDate date] timeIntervalSince1970]];

    // Store the completionhandler till we can come back and send appropriate message.
    if (pendingCompletionHandlers == nil) {
      pendingCompletionHandlers = [[NSMutableDictionary alloc] init];
    }

    [pendingCompletionHandlers setObject:[[completionHandler copy] autorelease] forKey:key];

    [self tryToPostBackgroundModeNotification:[NSMutableDictionary dictionaryWithObjectsAndKeys:key, @"handlerId", nil]
                         withNotificationName:kTiBackgroundFetchNotification];

    // We will go ahead and keeper a timer just in case the user returns the value too late - this is the worst case scenario.
    NSTimer *flushTimer = [NSTimer timerWithTimeInterval:TI_BACKGROUNDFETCH_MAX_INTERVAL target:self selector:@selector(fireCompletionHandler:) userInfo:key repeats:NO];
    [[NSRunLoop mainRunLoop] addTimer:flushTimer forMode:NSDefaultRunLoopMode];
  }
}

#endif

#pragma mark Remote and Local Notifications

- (void)application:(UIApplication *)application didRegisterUserNotificationSettings:(UIUserNotificationSettings *)notificationSettings
{
  [self tryToInvokeSelector:@selector(application:didRegisterUserNotificationSettings:)
              withArguments:[NSOrderedSet orderedSetWithObjects:application, notificationSettings, nil]];

  [[NSNotificationCenter defaultCenter] postNotificationName:kTiUserNotificationSettingsNotification
                                                      object:self
                                                    userInfo:@{ @"userNotificationSettings" : notificationSettings }];
}

// iOS 12+
- (void)userNotificationCenter:(UNUserNotificationCenter *)center openSettingsForNotification:(UNNotification *)notification
{
  // Unused so far, may expose as an event in the future?
}

// iOS 10+
- (void)userNotificationCenter:(UNUserNotificationCenter *)center willPresentNotification:(UNNotification *)notification withCompletionHandler:(void (^)(UNNotificationPresentationOptions options))completionHandler
{
  // For backwards compatibility with iOS < 10, we do not show notifications while the app is in foreground, but make it configurable
  // @fixme this is not documented and was silently introduced in TIMOB-26399
  BOOL showInForeground = [TiUtils boolValue:notification.request.content.userInfo[@"showInForeground"] def:NO];
  BOOL isRemote = [notification.request.trigger isKindOfClass:UNPushNotificationTrigger.class];

  if (isRemote) {
    if ([self respondsToSelector:@selector(application:didReceiveRemoteNotification:)]) {
      [self application:[UIApplication sharedApplication] didReceiveRemoteNotification:notification.request.content.userInfo];
    }
  } else {
    RELEASE_TO_NIL(localNotification);
    localNotification = [[TiApp dictionaryWithUserNotification:notification
                                                withIdentifier:notification.request.identifier] retain];
    [self tryToPostNotification:localNotification withNotificationName:kTiLocalNotification completionHandler:nil];
  }

  if (showInForeground) {
    completionHandler(UNNotificationPresentationOptionBadge | UNNotificationPresentationOptionAlert | UNNotificationPresentationOptionSound);
  } else {
    completionHandler(UNNotificationPresentationOptionNone);
  }
}

// iOS 10+
- (void)userNotificationCenter:(UNUserNotificationCenter *)center didReceiveNotificationResponse:(UNNotificationResponse *)response
             withCompletionHandler:(void (^)(void))completionHandler
{
  BOOL isRemote = [response.notification.request.trigger isKindOfClass:UNPushNotificationTrigger.class];
  if (isRemote) {
    NSMutableDictionary *responseInfo = nil;
    if ([response isKindOfClass:[UNTextInputNotificationResponse class]]) {
      responseInfo = [NSMutableDictionary dictionary];
      [responseInfo setValue:((UNTextInputNotificationResponse *)response).userText forKey:UIUserNotificationActionResponseTypedTextKey];
    }
    if ([UNNotificationDefaultActionIdentifier isEqualToString:response.actionIdentifier]) {
      if ([self respondsToSelector:@selector(application:didReceiveRemoteNotification:)]) {
        [self application:[UIApplication sharedApplication] didReceiveRemoteNotification:response.notification.request.content.userInfo];
      }
      completionHandler();
    } else {
      [self application:[UIApplication sharedApplication] handleActionWithIdentifier:response.actionIdentifier forRemoteNotification:response.notification.request.content.userInfo withResponseInfo:responseInfo completionHandler:completionHandler];
    }
  } else {
    //NOTE Local notifications should be handled similar to BG above which ultimately calls handleRemoteNotificationWithIdentifier as this will allow BG Actions to execute.
    RELEASE_TO_NIL(localNotification);
    localNotification = [[[self class] dictionaryWithUserNotification:response.notification
                                                       withIdentifier:response.actionIdentifier] retain];
    if ([response isKindOfClass:[UNTextInputNotificationResponse class]]) {
      [localNotification setValue:((UNTextInputNotificationResponse *)response).userText forKey:@"typedText"];
    }
    [self tryToPostNotification:localNotification withNotificationName:kTiLocalNotificationAction completionHandler:completionHandler];
  }
}

// This is required because iOS does not conform to it's own recommended Obj-C compiler rules (Strict prototypes).
// Muting the warnings until the UIApplicationDelegate fixes this.
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wstrict-prototypes"

- (void)application:(UIApplication *)application handleActionWithIdentifier:(NSString *)identifier forLocalNotification:(UILocalNotification *)notification withResponseInfo:(NSDictionary *)responseInfo completionHandler:(void (^)())completionHandler
{
  RELEASE_TO_NIL(localNotification);
  localNotification = [[TiApp dictionaryWithLocalNotification:notification withIdentifier:identifier] retain];
  [localNotification setValue:responseInfo[UIUserNotificationActionResponseTypedTextKey] forKey:@"typedText"];

  [self tryToInvokeSelector:@selector(application:handleActionWithIdentifier:forLocalNotification:withResponseInfo:completionHandler:)
              withArguments:[NSOrderedSet orderedSetWithObjects:application, identifier, notification, responseInfo, [completionHandler copy], nil]];

  [self tryToPostNotification:localNotification withNotificationName:kTiLocalNotificationAction completionHandler:completionHandler];
}

// iOS 9+
- (void)application:(UIApplication *)application handleActionWithIdentifier:(NSString *)identifier forRemoteNotification:(NSDictionary *)userInfo withResponseInfo:(NSDictionary *)responseInfo completionHandler:(void (^)())completionHandler
{
  [self tryToInvokeSelector:@selector(application:handleActionWithIdentifier:forRemoteNotification:withResponseInfo:completionHandler:)
              withArguments:[NSOrderedSet orderedSetWithObjects:application, identifier, userInfo, responseInfo, [completionHandler copy], nil]];

  [self handleRemoteNotificationWithIdentifier:identifier
                                   andUserInfo:userInfo
                                  responseInfo:responseInfo
                             completionHandler:completionHandler];
}

// iOS < 9
- (void)application:(UIApplication *)application handleActionWithIdentifier:(NSString *)identifier forRemoteNotification:(NSDictionary *)userInfo completionHandler:(void (^)())completionHandler
{
  [self tryToInvokeSelector:@selector(application:handleActionWithIdentifier:forRemoteNotification:completionHandler:)
              withArguments:[NSOrderedSet orderedSetWithObjects:application, identifier, userInfo, [completionHandler copy], nil]];

  [self handleRemoteNotificationWithIdentifier:identifier
                                   andUserInfo:userInfo
                                  responseInfo:nil // iOS 9+ only
                             completionHandler:completionHandler];
}

#pragma clang diagnostic pop

#pragma mark Apple Watchkit handleWatchKitExtensionRequest

- (void)application:(UIApplication *)application
    handleWatchKitExtensionRequest:(NSDictionary *)userInfo
                             reply:(void (^)(NSDictionary *replyInfo))reply
{

  // Generate unique key with timestamp.
  NSString *key = [NSString stringWithFormat:@"watchkit-reply-%f", [[NSDate date] timeIntervalSince1970]];

  if (pendingReplyHandlers == nil) {
    pendingReplyHandlers = [[NSMutableDictionary alloc] init];
  }

  [pendingReplyHandlers setObject:[[reply copy] autorelease] forKey:key];

  NSMutableDictionary *dic = [[[NSMutableDictionary alloc] init] autorelease];
  [dic setObject:key forKey:@"handlerId"];
  if (userInfo != nil) {
    [dic setObject:userInfo forKey:@"userInfo"];
  }

  [self tryToInvokeSelector:@selector(application:handleWatchKitExtensionRequest:reply:)
              withArguments:[NSOrderedSet orderedSetWithObjects:application, userInfo, [reply copy], nil]];

  [[NSNotificationCenter defaultCenter] postNotificationName:kTiWatchKitExtensionRequest object:self userInfo:dic];
}

- (void)watchKitExtensionRequestHandler:(id)key withUserInfo:(NSDictionary *)userInfo
{
  if (pendingReplyHandlers == nil) {
    DebugLog(@"[ERROR] No WatchKitExtensionRequest have been recieved yet");
    return;
  }

  if ([pendingReplyHandlers objectForKey:key]) {
    void (^replyBlock)(NSDictionary *input);
    replyBlock = [pendingReplyHandlers objectForKey:key];
    replyBlock(userInfo);
    [pendingReplyHandlers removeObjectForKey:key];
  } else {
    DebugLog(@"[ERROR] The specified WatchKitExtensionRequest Handler with ID: %@ has already expired or removed from the system", key);
  }
}

#pragma mark -

#pragma mark Helper Methods

- (void)invokeSelector:(SEL)selector withArguments:(NSOrderedSet<id> *)arguments onDelegate:(id)delegate
{
  NSInteger index = 2; // Index 0 and 1 are reserved for the invocation internals ("self" and "_cmd")
  NSInvocation *inv = [NSInvocation invocationWithMethodSignature:[delegate methodSignatureForSelector:selector]];
  [inv setSelector:selector];
  [inv setTarget:delegate];

  for (id obj in arguments) {
    // clang-format off
    [inv setArgument:&(obj) atIndex:index++];
    // clang-format on
  }

  [inv invoke];
}

- (void)tryToInvokeSelector:(SEL)selector withArguments:(NSOrderedSet<id> *)arguments
{
  NSString *selectorString = NSStringFromSelector(selector);

  if (appBooted && _applicationDelegates != nil) {
    for (id applicationDelegate in _applicationDelegates) {
      if ([applicationDelegate respondsToSelector:selector]) {
        [self invokeSelector:selector withArguments:arguments onDelegate:applicationDelegate];
      }
    }
  } else if (!appBooted && _applicationDelegates == nil) {
    [[self queuedApplicationSelectors] setObject:arguments forKey:selectorString];
  }
}

- (void)tryToPostNotification:(NSDictionary *)_notification withNotificationName:(NSString *)_notificationName completionHandler:(void (^)(void))completionHandler
{
  typedef void (^NotificationBlock)(void);

  NotificationBlock myNotificationBlock = ^void() {
    [[NSNotificationCenter defaultCenter] postNotificationName:_notificationName object:self userInfo:_notification];

    if (completionHandler != nil) {
      completionHandler();
    }
  };

  if (appBooted) {
    myNotificationBlock();
  } else {
    [[self queuedBootEvents] setObject:_notification forKey:_notificationName];
  }
}

- (void)tryToPostBackgroundModeNotification:(NSMutableDictionary *)userInfo withNotificationName:(NSString *)notificationName
{
  // Check to see if the app booted and we still have the completion handler in the system
  NSString *key = [userInfo objectForKey:@"handlerId"];
  BOOL shouldContinue = NO;
  if ([key rangeOfString:@"Session"].location != NSNotFound) {
    if ([backgroundTransferCompletionHandlers objectForKey:key] != nil) {
      shouldContinue = YES;
    }
  } else if ([key hasPrefix:@"BgTask-"]) {
    shouldContinue = YES;
  } else if ([pendingCompletionHandlers objectForKey:key] != nil) {
    shouldContinue = YES;
  }
  if (!shouldContinue) {
    return;
  }
  if (appBooted) {
    [[NSNotificationCenter defaultCenter] postNotificationName:notificationName object:self userInfo:userInfo];
  } else {
    [[self queuedBootEvents] setObject:userInfo forKey:notificationName];
  }
}

// Clear out the pending completion handlers
- (void)flushCompletionHandlerQueue
{
  if (pendingCompletionHandlers != nil) {
    for (NSString *key in [pendingCompletionHandlers allKeys]) {
      // Do not remove from the pending handlers for now, as it's removed after the enumeration finished
      [self performCompletionHandlerWithKey:key andResult:UIBackgroundFetchResultFailed removeAfterExecution:NO];
    }
  }
  RELEASE_TO_NIL(pendingCompletionHandlers);
}

// This method gets called when the wall clock runs out and the completion handler is still there
- (void)fireCompletionHandler:(NSTimer *)timer
{
  NSString *key = (NSString *)timer.userInfo;
  if ([pendingCompletionHandlers objectForKey:key]) {
    // Send an event notifying the developer that the background-fetch failed
    [self performCompletionHandlerWithKey:key andResult:UIBackgroundFetchResultFailed removeAfterExecution:YES];
  }
}

// Gets called when user ends finishes with backgrounding stuff. By default this would always be called with UIBackgroundFetchResultNoData.
- (void)performCompletionHandlerWithKey:(NSString *)key andResult:(UIBackgroundFetchResult)result removeAfterExecution:(BOOL)removeAfterExecution
{
  if ([pendingCompletionHandlers objectForKey:key]) {
    void (^completionHandler)(UIBackgroundFetchResult) = [pendingCompletionHandlers objectForKey:key];
    completionHandler(result);
    if (removeAfterExecution) {
      [pendingCompletionHandlers removeObjectForKey:key];
    }
  } else {
    DebugLog(@"[ERROR] The specified completion handler with ID = %@ has already expired or been removed from the system", key);
  }
}

//Called to mark the end of background transfer while in the background.
- (void)performCompletionHandlerForBackgroundTransferWithKey:(NSString *)key
{
  if ([backgroundTransferCompletionHandlers objectForKey:key] != nil) {
    void (^completionHandler)(void);
    completionHandler = [backgroundTransferCompletionHandlers objectForKey:key];
    [backgroundTransferCompletionHandlers removeObjectForKey:key];
    completionHandler();
  } else {
    DebugLog(@"[ERROR] The specified completion handler with ID = %@ has already expired or been removed from the system", key);
  }
}

#pragma mark
#pragma mark Remote Notifications

#ifdef USE_TI_SILENTPUSH
// Delegate callback for Silent Remote Notification.
- (void)application:(UIApplication *)application didReceiveRemoteNotification:(NSDictionary *)userInfo fetchCompletionHandler:(void (^)(UIBackgroundFetchResult result))completionHandler
{
  [self tryToInvokeSelector:@selector(application:didReceiveRemoteNotification:fetchCompletionHandler:)
              withArguments:[NSOrderedSet orderedSetWithObjects:application, userInfo, [completionHandler copy], nil]];

  NSArray *backgroundModes = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"UIBackgroundModes"];
  if ([backgroundModes containsObject:@"remote-notification"]) {

    // Generate unique key with timestamp.
    NSString *key = [NSString stringWithFormat:@"SilentPush-%f", [[NSDate date] timeIntervalSince1970]];

    // Store the completionhandler till we can come back and send appropriate message.
    if (pendingCompletionHandlers == nil) {
      pendingCompletionHandlers = [[NSMutableDictionary alloc] init];
    }

    [pendingCompletionHandlers setObject:[[completionHandler copy] autorelease] forKey:key];

    NSMutableDictionary *dict = [NSMutableDictionary dictionaryWithObjectsAndKeys:key, @"handlerId", nil];
    [dict addEntriesFromDictionary:userInfo];

    [self tryToPostBackgroundModeNotification:dict
                         withNotificationName:kTiSilentPushNotification];

    // We will go ahead and keeper a timer just in case the user returns the value too late - this is the worst case scenario.
    NSTimer *flushTimer = [NSTimer timerWithTimeInterval:TI_BACKGROUNDFETCH_MAX_INTERVAL target:self selector:@selector(fireCompletionHandler:) userInfo:key repeats:NO];
    [[NSRunLoop mainRunLoop] addTimer:flushTimer forMode:NSDefaultRunLoopMode];
  }
}
#endif

#pragma mark Background Transfer Service

//Delegate callback for Background Transfer completes.
- (void)application:(UIApplication *)application handleEventsForBackgroundURLSession:(NSString *)identifier completionHandler:(void (^)(void))completionHandler
{
  // Generate unique key with timestamp.
  NSString *key = [NSString stringWithFormat:@"Session-%f", [[NSDate date] timeIntervalSince1970]];

  // Store the completionhandler till we can come back and send appropriate message.
  if (backgroundTransferCompletionHandlers == nil) {
    backgroundTransferCompletionHandlers = [[NSMutableDictionary alloc] init];
  }

  [backgroundTransferCompletionHandlers setObject:[[completionHandler copy] autorelease] forKey:key];

  [self tryToInvokeSelector:@selector(application:handleEventsForBackgroundURLSession:completionHandler:)
              withArguments:[NSOrderedSet orderedSetWithObjects:application, identifier, [completionHandler copy], nil]];

  NSMutableDictionary *dict = [NSMutableDictionary dictionaryWithObjectsAndKeys:identifier, @"sessionId",
                                                   key, @"handlerId", nil];
  [self tryToPostBackgroundModeNotification:dict withNotificationName:kTiBackgroundTransfer];
}

#pragma mark Background Transfer Service Delegates.

//TODO: Move these delegates to the module post 3.2.0

- (void)URLSession:(NSURLSession *)session downloadTask:(NSURLSessionDownloadTask *)downloadTask didFinishDownloadingToURL:(NSURL *)location
{
  // Copy downloaded file from location to tempFile (in NSTemporaryDirectory), because file in location will be removed after delegate completes
  NSError *error;
  NSFileManager *fileManager = [NSFileManager defaultManager];
  NSString *destinationFilename = location.lastPathComponent;
  NSURL *destinationURL = [[NSURL fileURLWithPath:NSTemporaryDirectory()] URLByAppendingPathComponent:destinationFilename];
  if ([fileManager fileExistsAtPath:[destinationURL path]]) {
    [fileManager removeItemAtURL:destinationURL error:nil];
  }
  BOOL success = [fileManager copyItemAtURL:location toURL:destinationURL error:&error];
  TiBlob *downloadedData;
  if (!success) {
    DebugLog(@"Unable to copy temp file. Error: %@", [error localizedDescription]);
    downloadedData = [[[TiBlob alloc] initWithData:[NSData dataWithContentsOfURL:location] mimetype:[Mimetypes mimeTypeForExtension:[location absoluteString]]] autorelease];
  } else {
    downloadedData = [[[TiBlob alloc] initWithFile:[destinationURL path]] autorelease];
  }

  NSMutableDictionary *dict = [NSMutableDictionary dictionaryWithObjectsAndKeys:
                                                       [NSNumber numberWithUnsignedInteger:downloadTask.taskIdentifier], @"taskIdentifier",
                                                   downloadedData, @"data", nil];

  if (session.configuration.identifier) {
    [dict setObject:session.configuration.identifier forKey:@"sessionIdentifier"];
  }

  [[NSNotificationCenter defaultCenter] postNotificationName:kTiURLDownloadFinished object:self userInfo:dict];
}

- (void)URLSession:(NSURLSession *)session downloadTask:(NSURLSessionDownloadTask *)downloadTask didWriteData:(int64_t)bytesWritten totalBytesWritten:(int64_t)totalBytesWritten totalBytesExpectedToWrite:(int64_t)totalBytesExpectedToWrite
{
  NSMutableDictionary *dict = [NSMutableDictionary dictionaryWithObjectsAndKeys:
                                                       [NSNumber numberWithUnsignedInteger:downloadTask.taskIdentifier], @"taskIdentifier",
                                                   [NSNumber numberWithUnsignedLongLong:bytesWritten], @"bytesWritten",
                                                   [NSNumber numberWithUnsignedLongLong:totalBytesWritten], @"totalBytesWritten",
                                                   [NSNumber numberWithUnsignedLongLong:totalBytesExpectedToWrite], @"totalBytesExpectedToWrite", nil];

  if (session.configuration.identifier) {
    [dict setObject:session.configuration.identifier forKey:@"sessionIdentifier"];
  }

  [[NSNotificationCenter defaultCenter] postNotificationName:kTiURLDowloadProgress object:self userInfo:dict];
}

- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task didSendBodyData:(int64_t)bytesSent
              totalBytesSent:(int64_t)totalBytesSent
    totalBytesExpectedToSend:(int64_t)totalBytesExpectedToSend
{
  NSMutableDictionary *dict = [NSMutableDictionary dictionaryWithObjectsAndKeys:NUMUINTEGER(task.taskIdentifier), @"taskIdentifier",
                                                   [NSNumber numberWithUnsignedLongLong:bytesSent], @"bytesSent",
                                                   [NSNumber numberWithUnsignedLongLong:totalBytesSent], @"totalBytesSent",
                                                   [NSNumber numberWithUnsignedLongLong:totalBytesExpectedToSend], @"totalBytesExpectedToSend", nil];

  if (session.configuration.identifier) {
    [dict setObject:session.configuration.identifier forKey:@"sessionIdentifier"];
  }

  [[NSNotificationCenter defaultCenter] postNotificationName:kTiURLUploadProgress object:self userInfo:dict];
}

- (void)URLSession:(NSURLSession *)session dataTask:(NSURLSessionDataTask *)dataTask didReceiveData:(NSData *)data
{
  if (!uploadTaskResponses) {
    uploadTaskResponses = [[NSMutableDictionary alloc] init];
  }
  //This dictionary will mutate if delegate is called
  NSMutableDictionary *responseObj = [uploadTaskResponses objectForKey:@(dataTask.taskIdentifier)];
  if (!responseObj) {
    NSMutableData *responseData = [NSMutableData dataWithData:data];
    responseObj = [NSMutableDictionary dictionaryWithObjectsAndKeys:responseData, @"responseData", nil];
    [uploadTaskResponses setValue:responseObj forKey:(NSString *)@(dataTask.taskIdentifier)];
  } else {
    [[responseObj objectForKey:@"responseData"] appendData:data];
  }
}

- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task didCompleteWithError:(NSError *)error
{
  NSMutableDictionary *dict = [NSMutableDictionary dictionaryWithObjectsAndKeys:
                                                       [NSNumber numberWithUnsignedInteger:task.taskIdentifier], @"taskIdentifier",
                                                   nil];

  if (session.configuration.identifier) {
    [dict setObject:session.configuration.identifier forKey:@"sessionIdentifier"];
  }

  if (error) {
    NSDictionary *errorinfo = [NSDictionary dictionaryWithObjectsAndKeys:@(NO), @"success",
                                            @([error code]), @"errorCode",
                                            [error localizedDescription], @"message",
                                            nil];
    [dict addEntriesFromDictionary:errorinfo];
  } else {
    NSInteger statusCode = [(NSHTTPURLResponse *)[task response] statusCode];

    NSMutableDictionary *successResponse = [NSMutableDictionary dictionaryWithObjectsAndKeys:NUMBOOL(YES), @"success",
                                                                NUMINT(0), @"errorCode",
                                                                @(statusCode), @"statusCode", nil];
    NSMutableDictionary *responseObj = [uploadTaskResponses objectForKey:@(task.taskIdentifier)];

    if (responseObj != nil) {
      // We only send "responseText" as the "responsesData" is only set with data from uploads
      NSString *responseText = [[NSString alloc] initWithData:[responseObj objectForKey:@"responseData"] encoding:NSUTF8StringEncoding];

      [successResponse setValue:responseText forKey:@"responseText"];
      [uploadTaskResponses removeObjectForKey:@(task.taskIdentifier)];
      RELEASE_TO_NIL(responseText);
    }
    [dict addEntriesFromDictionary:successResponse];
  }
  [[NSNotificationCenter defaultCenter] postNotificationName:kTiURLSessionCompleted object:self userInfo:dict];
}

- (void)URLSession:(NSURLSession *)session downloadTask:(NSURLSessionDownloadTask *)downloadTask
     didResumeAtOffset:(int64_t)fileOffset
    expectedTotalBytes:(int64_t)expectedTotalBytes
{
}

- (void)URLSessionDidFinishEventsForBackgroundURLSession:(NSURLSession *)session
{
  NSDictionary *dict = nil;

  if (session.configuration.identifier) {
    dict = @{ @"sessionIdentifier" : session.configuration.identifier };
  }

  [[NSNotificationCenter defaultCenter] postNotificationName:kTiURLSessionEventsCompleted object:self userInfo:dict];
}

#pragma mark

- (void)applicationWillTerminate:(UIApplication *)application
{
  [self tryToInvokeSelector:@selector(applicationWillTerminate:)
              withArguments:[NSOrderedSet orderedSetWithObject:application]];

  NSNotificationCenter *theNotificationCenter = [NSNotificationCenter defaultCenter];
  _willTerminate = YES;
  //This will send out the 'close' message.
  [theNotificationCenter postNotificationName:kTiWillShutdownNotification object:self];
  NSCondition *condition = [[NSCondition alloc] init];

  // These shutdowns return immediately, yes, but the main will still run the close that's in their queue.
  [kjsBridge shutdown:condition];

  if ([[TiSharedConfig defaultConfig] logServerEnabled]) {
    [[TiLogServer defaultLogServer] stop];
  }

  //This will shut down the modules.
  [theNotificationCenter postNotificationName:kTiShutdownNotification object:self];
  RELEASE_TO_NIL(condition);
  RELEASE_TO_NIL(kjsBridge);
  RELEASE_TO_NIL(remoteNotification);
  RELEASE_TO_NIL(pendingCompletionHandlers);
  RELEASE_TO_NIL(backgroundTransferCompletionHandlers);
  RELEASE_TO_NIL(sessionId);
}

- (void)applicationDidReceiveMemoryWarning:(UIApplication *)application
{
  [self tryToInvokeSelector:@selector(applicationDidReceiveMemoryWarning:)
              withArguments:[NSOrderedSet orderedSetWithObject:application]];

  [Webcolor flushCache];
}

- (void)applicationWillResignActive:(UIApplication *)application
{
  [self tryToInvokeSelector:@selector(applicationWillResignActive:)
              withArguments:[NSOrderedSet orderedSetWithObject:application]];

  if ([self forceSplashAsSnapshot]) {
    [window addSubview:[self splashScreenView]];
  }
  [[NSNotificationCenter defaultCenter] postNotificationName:kTiSuspendNotification object:self];

  // suspend any image loading
  [[ImageLoader sharedLoader] suspend];
  [kjsBridge gc];
}

- (void)applicationDidBecomeActive:(UIApplication *)application
{
  [self tryToInvokeSelector:@selector(applicationDidBecomeActive:)
              withArguments:[NSOrderedSet orderedSetWithObject:application]];

  // We should think about placing this inside "applicationWillBecomeActive" instead to make
  // the UI re-useable again more quickly
  if ([self forceSplashAsSnapshot] && splashScreenView != nil) {
    [[self splashScreenView] removeFromSuperview];
    RELEASE_TO_NIL(splashScreenView);
  }

  // NOTE: Have to fire a separate but non-'resume' event here because there is SOME information
  // (like new URL) that is not passed through as part of the normal foregrounding process.
  [[NSNotificationCenter defaultCenter] postNotificationName:kTiResumedNotification object:self];

  // resume any image loading
  [[ImageLoader sharedLoader] resume];
}

- (void)applicationDidEnterBackground:(UIApplication *)application
{
  [self tryToInvokeSelector:@selector(applicationDidEnterBackground:)
              withArguments:[NSOrderedSet orderedSetWithObject:application]];

  [[NSNotificationCenter defaultCenter] postNotificationName:kTiPausedNotification object:self];

  if ([TiUtils isIOSVersionOrGreater:@"13.0"]) {
    for (NSDictionary *backgroundTask in backgroundTasks) {
      [self submitBackgroundTask:backgroundTask];
    }
  }

  if (backgroundServices == nil) {
    return;
  }

  UIApplication *app = [UIApplication sharedApplication];
  TiApp *tiapp = self;
  bgTaskIdentifier = [app beginBackgroundTaskWithExpirationHandler:^{
    // Synchronize the cleanup call on the main thread in case
    // the task actually finishes at around the same time.
    TiThreadPerformOnMainThread(
        ^{
          if (bgTaskIdentifier != UIBackgroundTaskInvalid) {
            [app endBackgroundTask:bgTaskIdentifier];
            bgTaskIdentifier = UIBackgroundTaskInvalid;
          }
        },
        NO);
  }];
  // Start the long-running task and return immediately.
  dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
    // Do the work associated with the task.
    [tiapp beginBackgrounding];
  });
}

- (void)applicationWillEnterForeground:(UIApplication *)application
{
  [self tryToInvokeSelector:@selector(applicationWillEnterForeground:)
              withArguments:[NSOrderedSet orderedSetWithObject:application]];

  [self flushCompletionHandlerQueue];
  [sessionId release];
  sessionId = [[TiUtils createUUID] retain];

  //TIMOB-3432. Ensure url is cleared when resume event is fired.
  [launchOptions removeObjectForKey:@"url"];
  [launchOptions removeObjectForKey:@"source"];

  [[NSNotificationCenter defaultCenter] postNotificationName:kTiResumeNotification object:self];

  if (backgroundServices == nil) {
    return;
  }

  [self endBackgrounding];
}

//TODO: this should be compiled out in production mode
- (void)showModalError:(NSString *)message
{
  if ([[TiSharedConfig defaultConfig] showErrorController] == NO) {
    NSLog(@"[ERROR] Application received error: %@", message);
    return;
  }
  ENSURE_UI_THREAD(showModalError, message);

  TiErrorController *error = [[TiErrorController alloc] initWithError:message];
  TiErrorNavigationController *nav = [[[TiErrorNavigationController alloc] initWithRootViewController:error] autorelease];
  RELEASE_TO_NIL(error);

  [[[self controller] topPresentedController] presentViewController:nav animated:YES completion:nil];
}

- (void)showModalController:(UIViewController *)modalController animated:(BOOL)animated
{
  [controller showControllerModal:modalController animated:animated];
}

- (void)hideModalController:(UIViewController *)modalController animated:(BOOL)animated
{
  [controller hideControllerModal:modalController animated:animated];
}

- (UIInterfaceOrientationMask)application:(UIApplication *)application supportedInterfaceOrientationsForWindow:(UIWindow *)window
{
  if ([self windowIsKeyWindow]) {
    return [controller supportedOrientationsForAppDelegate];
  }

  return 30;
}

- (void)dealloc
{
  RELEASE_TO_NIL(kjsBridge);
  RELEASE_TO_NIL(loadView);
  RELEASE_TO_NIL(window);
  RELEASE_TO_NIL(launchOptions);
  RELEASE_TO_NIL(controller);
  RELEASE_TO_NIL(userAgent);
  RELEASE_TO_NIL(remoteDeviceUUID);
  RELEASE_TO_NIL(remoteNotification);
  RELEASE_TO_NIL(splashScreenView);
  RELEASE_TO_NIL(backgroundServices);
  RELEASE_TO_NIL(localNotification);
  RELEASE_TO_NIL(uploadTaskResponses);
  RELEASE_TO_NIL(queuedBootEvents);
  RELEASE_TO_NIL(_queuedApplicationSelectors);
  RELEASE_TO_NIL(_applicationDelegates);
  RELEASE_TO_NIL(backgroundTasks);
  RELEASE_TO_NIL(registeredBackgroundTasks);

  [super dealloc];
}

- (NSString *)systemUserAgent
{
  UIDevice *currentDevice = [UIDevice currentDevice];
  NSString *currentLocaleIdentifier = [[NSLocale currentLocale] localeIdentifier];
  NSString *currentDeviceInfo = [NSString stringWithFormat:@"%@/%@; %@; %@;", [currentDevice model], [currentDevice systemVersion], [currentDevice systemName], currentLocaleIdentifier];
  NSString *kTitaniumUserAgentPrefix = [NSString stringWithFormat:@"%s%s%s %s%s", "Appc", "eler", "ator", "Tita", "nium"];
  return [NSString stringWithFormat:@"%@/%s (%@)", kTitaniumUserAgentPrefix, TI_VERSION_STR, currentDeviceInfo];
}

- (NSString *)userAgent
{
  return !userAgent ? [self systemUserAgent] : userAgent;
}

- (NSString *)remoteDeviceUUID
{
  return remoteDeviceUUID;
}

- (NSString *)sessionId
{
  return sessionId;
}

- (KrollBridge *)krollBridge
{
  return kjsBridge;
}

#pragma mark Background Tasks

- (void)registerBackgroundTasks
{
  NSArray *identifiers = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"BGTaskSchedulerPermittedIdentifiers"];

  for (NSString *identifier in identifiers) {
    if (registeredBackgroundTasks == nil) {
      registeredBackgroundTasks = [[NSMutableArray alloc] init];
    }
    [[BGTaskScheduler sharedScheduler] registerForTaskWithIdentifier:identifier
                                                          usingQueue:nil
                                                       launchHandler:^(__kindof BGTask *_Nonnull task) {
                                                         [registeredBackgroundTasks addObject:task];
                                                         [self handleBGTask:task];
                                                       }];
  }
}

- (void)handleBGTask:(BGTask *)task
{
  NSString *notificationName = kTiBackgroundProcessNotification;
  if ([task isKindOfClass:[BGAppRefreshTask class]]) {
    // Fo refresh task submit it again
    [self submitTaskForIdentifier:task.identifier];
    notificationName = kTiBackgroundFetchNotification;
  }
  NSString *key = [NSString stringWithFormat:@"BgTask-%@", task.identifier];

  [self tryToPostBackgroundModeNotification:[NSMutableDictionary dictionaryWithObjectsAndKeys:key, @"handlerId", nil]
                       withNotificationName:notificationName];

  task.expirationHandler = ^{
    if ([task isKindOfClass:[BGProcessingTask class]]) {
      // Fo processing task, if it is not completed in time then only submit it again.
      [self submitTaskForIdentifier:task.identifier];
    }
    [task setTaskCompletedWithSuccess:false];
    [registeredBackgroundTasks removeObject:task];
  };
}

- (void)submitTaskForIdentifier:(NSString *)identifier
{
  NSDictionary *backgroundTask = [self backgroundTaskForIdentifier:identifier];
  if (backgroundTask) {
    [self submitBackgroundTask:backgroundTask];
  }
}

- (void)submitBackgroundTask:(NSDictionary *)bgTask
{
  BGTaskRequest *taskRequest;
  if ([bgTask[@"type"] isEqualToString:@"process"]) {
    taskRequest = [[[BGProcessingTaskRequest alloc] initWithIdentifier:bgTask[@"identifier"]] autorelease];
    ((BGProcessingTaskRequest *)taskRequest).requiresNetworkConnectivity = [TiUtils boolValue:bgTask[@"networkConnect"] def:NO];
    ((BGProcessingTaskRequest *)taskRequest).requiresExternalPower = [TiUtils boolValue:bgTask[@"powerConnect"] def:NO];
  } else {
    taskRequest = [[[BGAppRefreshTaskRequest alloc] initWithIdentifier:bgTask[@"identifier"]] autorelease];
  }
  taskRequest.earliestBeginDate = bgTask[@"beginDate"];

  [BGTaskScheduler.sharedScheduler submitTaskRequest:taskRequest error:nil];
}

- (void)backgroundTaskCompletedForIdentifier:(NSString *)identifier
{
  for (BGTask *task in registeredBackgroundTasks) {
    if ([task.identifier isEqualToString:identifier]) {
      [task setTaskCompletedWithSuccess:YES];
      [registeredBackgroundTasks removeObject:task];
      break;
    }
  }
}

- (NSDictionary *_Nullable)backgroundTaskForIdentifier:(NSString *)identifier
{
  NSDictionary *bgTask = nil;
  for (NSDictionary *backgroundTask in backgroundTasks) {
    if (backgroundTask[@"identifier"] == identifier) {
      bgTask = backgroundTask;
      break;
    }
  }
  return bgTask;
}

- (void)addBackgroundTask:(NSDictionary *)backgroundTask
{
  if (backgroundTasks == nil) {
    backgroundTasks = [[NSMutableArray alloc] init];
  }
  NSArray *identifiers = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"BGTaskSchedulerPermittedIdentifiers"];
  NSDictionary *oldTask = [self backgroundTaskForIdentifier:backgroundTask[@"identifier"]];
  if ([identifiers containsObject:backgroundTask[@"identifier"]]) {
    if (oldTask) {
      [backgroundTasks removeObject:oldTask];
    }
    [backgroundTasks addObject:backgroundTask];
  } else {
    DebugLog(@"The identifier, %@, is not added in tiapp.xml. Add it against key BGTaskSchedulerPermittedIdentifiers", backgroundTask[@"identifier"]);
  }
}

#pragma mark Background Services

- (void)beginBackgrounding
{
  if (runningServices == nil) {
    runningServices = [[NSMutableArray alloc] initWithCapacity:[backgroundServices count]];
  }

  for (TiProxy *proxy in backgroundServices) {
    [runningServices addObject:proxy];
    [proxy performSelector:@selector(beginBackground)];
  }
  [self checkBackgroundServices];
}

- (void)endBackgrounding
{
  for (TiProxy *proxy in backgroundServices) {
    [proxy performSelector:@selector(endBackground)];
    [runningServices removeObject:proxy];
  }

  [self checkBackgroundServices];
  RELEASE_TO_NIL(runningServices);
}

- (void)application:(UIApplication *)application didReceiveLocalNotification:(UILocalNotification *)notification
{
  RELEASE_TO_NIL(localNotification);
  localNotification = [[[self class] dictionaryWithLocalNotification:notification] retain];

  [self tryToInvokeSelector:@selector(application:didReceiveLocalNotification:)
              withArguments:[NSOrderedSet orderedSetWithObjects:application, notification, nil]];

  [self tryToPostNotification:localNotification withNotificationName:kTiLocalNotification completionHandler:nil];
}

- (BOOL)handleShortcutItem:(UIApplicationShortcutItem *)shortcutItem queueToBootIfNotLaunched:(BOOL)bootWait
{
  if (shortcutItem.type == nil) {
    NSLog(@"[ERROR] The shortcut type property is required");
    return NO;
  }

  NSMutableDictionary *dict = [NSMutableDictionary
      dictionaryWithObjectsAndKeys:shortcutItem.type, @"type",
      nil];

  if (shortcutItem.localizedTitle != nil) {
    [dict setObject:shortcutItem.localizedTitle forKey:@"title"];
  }

  if (shortcutItem.localizedSubtitle != nil) {
    [dict setObject:shortcutItem.localizedSubtitle forKey:@"subtitle"];
  }

  if (shortcutItem.userInfo != nil) {
    [dict setObject:shortcutItem.userInfo forKey:@"userInfo"];
  }

  // Update launchOptions to include the mapped dictionary-shortcut instead of the UIShortcutItem
  [launchOptions setObject:dict forKey:UIApplicationLaunchOptionsShortcutItemKey];

  if (appBooted) {
    [[NSNotificationCenter defaultCenter] postNotificationName:kTiApplicationShortcut
                                                        object:self
                                                      userInfo:dict];
  } else if (bootWait) {
    [[self queuedBootEvents] setObject:dict forKey:kTiApplicationShortcut];
  }

  return YES;
}

- (void)handleRemoteNotificationWithIdentifier:(NSString *)identifier
                                   andUserInfo:(NSDictionary *)userInfo
                                  responseInfo:(NSDictionary *)responseInfo
                             completionHandler:(void (^)(void))completionHandler
{
  RELEASE_TO_NIL(remoteNotification);
  [self generateNotification:userInfo];

  NSMutableDictionary *event = [[NSMutableDictionary alloc] init];
  NSString *category = remoteNotification[@"category"];

  event[@"data"] = remoteNotification;

  if (identifier != nil) {
    event[@"identifier"] = identifier;
  }
  if (responseInfo[UIUserNotificationActionResponseTypedTextKey] != nil) {
    event[@"typedText"] = responseInfo[UIUserNotificationActionResponseTypedTextKey];
  }
  if (category != nil) {
    event[@"category"] = category;
  }
  NSArray *backgroundModes = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"UIBackgroundModes"];
  if ([backgroundModes containsObject:@"remote-notification"]) {
    // Generate unique key with timestamp.
    id key = [NSString stringWithFormat:@"CategoryPush-%f", [[NSDate date] timeIntervalSince1970]];
    // Store the completionhandler till we can come back and send appropriate message.
    if (pendingCompletionHandlers == nil) {
      pendingCompletionHandlers = [[NSMutableDictionary alloc] init];
    }
    [pendingCompletionHandlers setObject:[[completionHandler copy] autorelease] forKey:key];

    NSMutableDictionary *dict = [NSMutableDictionary dictionaryWithObjectsAndKeys:key, @"handlerId", nil];
    [dict addEntriesFromDictionary:event];
    [self tryToPostBackgroundModeNotification:dict
                         withNotificationName:kTiRemoteNotificationAction];
    // We will go ahead and keeper a timer just in case the user returns the value too late - this is the worst case scenario.
    NSTimer *flushTimer = [NSTimer timerWithTimeInterval:TI_BACKGROUNDFETCH_MAX_INTERVAL target:self selector:@selector(fireCompletionHandler:) userInfo:key repeats:NO];
    [[NSRunLoop mainRunLoop] addTimer:flushTimer forMode:NSDefaultRunLoopMode];
  } else {
    [self tryToPostNotification:[event autorelease] withNotificationName:kTiRemoteNotificationAction completionHandler:completionHandler];
  }
}

- (void)application:(UIApplication *)application
    performActionForShortcutItem:(UIApplicationShortcutItem *)shortcutItem
               completionHandler:(void (^)(BOOL succeeded))completionHandler
{
  [self tryToInvokeSelector:@selector(application:performActionForShortcutItem:completionHandler:)
              withArguments:[NSOrderedSet orderedSetWithObjects:application, shortcutItem, [completionHandler copy], nil]];

  BOOL handledShortCutItem = [self handleShortcutItem:shortcutItem queueToBootIfNotLaunched:NO];
  completionHandler(handledShortCutItem);
}

- (void)registerBackgroundService:(TiProxy *)proxy
{
  if (backgroundServices == nil) {
    backgroundServices = [[NSMutableArray alloc] initWithCapacity:1];
  }

  //Only add if it isn't already added
  if (![backgroundServices containsObject:proxy]) {
    [backgroundServices addObject:proxy];
  }
}

- (void)checkBackgroundServices
{
  if ([runningServices count] == 0) {
    // Synchronize the cleanup call on the main thread in case
    // the expiration handler is fired at the same time.
    TiThreadPerformOnMainThread(
        ^{
          if (bgTaskIdentifier != UIBackgroundTaskInvalid) {
            [[UIApplication sharedApplication] endBackgroundTask:bgTaskIdentifier];
            bgTaskIdentifier = UIBackgroundTaskInvalid;
          }
        },
        NO);
  }
}

- (void)unregisterBackgroundService:(TiProxy *)proxy
{
  [backgroundServices removeObject:proxy];
  [runningServices removeObject:proxy];
  [self checkBackgroundServices];
}

- (void)stopBackgroundService:(TiProxy *)proxy
{
  [runningServices removeObject:proxy];
  [self checkBackgroundServices];
}

+ (NSDictionary *)dictionaryWithUserNotification:(UNNotification *)notification withIdentifier:(NSString *)identifier
{
  if (notification == nil) {
    return nil;
  }
  NSMutableDictionary *event = [NSMutableDictionary dictionary];

  [event setObject:NULL_IF_NIL(notification.date) forKey:@"date"];
  [event setObject:NSTimeZone.defaultTimeZone.name forKey:@"timezone"];
  [event setObject:NULL_IF_NIL(notification.request.content.body) forKey:@"alertBody"];
  [event setObject:NULL_IF_NIL(notification.request.content.title) forKey:@"alertTitle"];
  [event setObject:NULL_IF_NIL(notification.request.content.subtitle) forKey:@"alertSubtitle"];
  [event setObject:NULL_IF_NIL(notification.request.content.launchImageName) forKey:@"alertLaunchImage"];
  [event setObject:NULL_IF_NIL(notification.request.content.badge) forKey:@"badge"];
  [event setObject:NULL_IF_NIL(notification.request.content.userInfo) forKey:@"userInfo"];
  [event setObject:NULL_IF_NIL(notification.request.content.categoryIdentifier) forKey:@"category"];
  [event setObject:NULL_IF_NIL(notification.request.content.threadIdentifier) forKey:@"threadIdentifier"];
  [event setObject:NULL_IF_NIL(identifier) forKey:@"identifier"];

  // iOS 10+ does have "soundName" but "sound" which is a native object. But if we find
  // a sound in the APS dictionary, we can provide that one for parity
  if (notification.request.content.userInfo[@"aps"] && notification.request.content.userInfo[@"aps"][@"sound"]) {
    [event setObject:notification.request.content.userInfo[@"aps"][@"sound"] forKey:@"sound"];
  }

#if !TARGET_OS_MACCATALYST
  // Inject the trigger (time- or location-based) into the payload
  UNNotificationTrigger *trigger = notification.request.trigger;
  if (trigger != nil) {
    if ([trigger isKindOfClass:[UNCalendarNotificationTrigger class]]) {
      [event setObject:NULL_IF_NIL([(UNCalendarNotificationTrigger *)trigger nextTriggerDate]) forKey:@"date"];
    } else if ([trigger isKindOfClass:[UNLocationNotificationTrigger class]]) {
      CLCircularRegion *region = (CLCircularRegion *)[(UNLocationNotificationTrigger *)trigger region];
      NSDictionary *dict = @{
        @"latitude" : NUMDOUBLE(region.center.latitude),
        @"longitude" : NUMDOUBLE(region.center.longitude),
        @"radius" : NUMDOUBLE(region.radius),
        @"identifier" : region.identifier
      };
      [event setObject:dict forKey:@"region"];
    }
  }
#endif
  return event;
}

+ (NSDictionary *)dictionaryWithLocalNotification:(id)notification withIdentifier:(NSString *)identifier
{
  if (notification == nil) {
    return nil;
  }
  NSMutableDictionary *event = [NSMutableDictionary dictionary];
  [event setObject:NULL_IF_NIL([notification fireDate]) forKey:@"date"];
  [event setObject:NULL_IF_NIL([[notification timeZone] name]) forKey:@"timezone"];
  [event setObject:NULL_IF_NIL([notification alertTitle]) forKey:@"alertTitle"];
  [event setObject:NULL_IF_NIL([notification alertBody]) forKey:@"alertBody"];
  [event setObject:NULL_IF_NIL([notification alertAction]) forKey:@"alertAction"];
  [event setObject:NULL_IF_NIL([notification alertLaunchImage]) forKey:@"alertLaunchImage"];
  [event setObject:NULL_IF_NIL([notification soundName]) forKey:@"sound"];
  [event setObject:@([notification applicationIconBadgeNumber]) forKey:@"badge"];
  [event setObject:NULL_IF_NIL([notification userInfo]) forKey:@"userInfo"];
  [event setObject:NULL_IF_NIL([notification category]) forKey:@"category"];
  [event setObject:NULL_IF_NIL(identifier) forKey:@"identifier"];
  [event setObject:@([[UIApplication sharedApplication] applicationState] != UIApplicationStateActive) forKey:@"inBackground"];

  return event;
}
+ (NSDictionary *)dictionaryWithLocalNotification:(UILocalNotification *)notification
{
  return [self dictionaryWithLocalNotification:notification withIdentifier:notification.userInfo[@"id"]];
}

// Returns an NSDictionary with the properties from tiapp.xml
// this is called from Ti.App.Properties and other places.
+ (NSDictionary *)tiAppProperties
{
  static NSDictionary *props;

  if (props == nil) {
    // Get the props from the encrypted json file
    NSString *tiAppPropertiesPath = [[TiHost resourcePath] stringByAppendingPathComponent:@"_app_props_.json"];
    NSData *jsonData = [TiUtils loadAppResource:[NSURL fileURLWithPath:tiAppPropertiesPath]];

    if (jsonData == nil) {
      // Not found in encrypted file, this means we're in development mode, get it from the filesystem
      jsonData = [NSData dataWithContentsOfFile:tiAppPropertiesPath];
    }

    NSString *errorString = nil;
    // Get the JSON data and create the NSDictionary.
    if (jsonData) {
      NSError *error = nil;
      props = [[NSJSONSerialization JSONObjectWithData:jsonData options:0 error:&error] retain];
      errorString = [error localizedDescription];
    } else {
      // If we have no data...
      // This should never happen on a Titanium app using the node.js CLI
      errorString = @"File not found";
    }
    if (errorString != nil) {
      // Let the developer know that we could not load the tiapp.xml properties.
      DebugLog(@"[ERROR] Could not load tiapp.xml properties, error was %@", errorString);
      // Create an empty dictioary to avoid running this code over and over again.
      props = [[NSDictionary dictionary] retain];
    }
  }
  return props;
}

@end
