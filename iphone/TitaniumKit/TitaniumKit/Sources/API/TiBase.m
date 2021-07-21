/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2015 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#import "TiBase.h"
#import "TiApp.h"
#import "TiLogServer.h"

#include <pthread.h>
#include <stdarg.h>
#include <sys/time.h>

#if DEBUG
#include <assert.h>
#include <stdbool.h>
#include <sys/sysctl.h>
#include <sys/types.h>
#include <unistd.h>
#endif

NSMutableArray *TiCreateNonRetainingArray(void)
{
  CFArrayCallBacks callbacks = kCFTypeArrayCallBacks;
  callbacks.retain = NULL;
  callbacks.release = NULL;
  return (NSMutableArray *)CFArrayCreateMutable(nil, 0, &callbacks);
}

NSMutableDictionary *TiCreateNonRetainingDictionary(void)
{
  CFDictionaryKeyCallBacks keyCallbacks = kCFTypeDictionaryKeyCallBacks;
  CFDictionaryValueCallBacks callbacks = kCFTypeDictionaryValueCallBacks;
  callbacks.retain = NULL;
  callbacks.release = NULL;
  return (NSMutableDictionary *)CFDictionaryCreateMutable(nil, 0, &keyCallbacks, &callbacks);
}

CGPoint midpointBetweenPoints(CGPoint a, CGPoint b)
{
  CGFloat x = (a.x + b.x) / 2.0;
  CGFloat y = (a.y + b.y) / 2.0;
  return CGPointMake(x, y);
}

/**
 * Logs a message from the app's console.log(), Ti.API.info(), and native
 * NSLog() calls to the log server.
 */
void TiLogMessage(NSString *str, ...)
{
  va_list args;
  va_start(args, str);

  NSString *message = [[[NSString alloc] initWithFormat:str arguments:args] autorelease];

#pragma push
#undef NSLog
  // first output the message to the system log
  // we want to see the message regardless of which target we're running on
  NSLog(@"%@", message);
#pragma pop

  if ([[TiSharedConfig defaultConfig] logServerEnabled]) { // FIXME: cache the value, since it only changes once?
    // next we send the message to the log server to be sent or queued up
    [[TiLogServer defaultLogServer] log:message];
  }
}

NSString *const kTiASCIIEncoding = @"ascii";
NSString *const kTiISOLatin1Encoding = @"latin1";
NSString *const kTiUTF8Encoding = @"utf8";
NSString *const kTiUTF16Encoding = @"utf16";
NSString *const kTiUTF16LEEncoding = @"utf16le";
NSString *const kTiUTF16BEEncoding = @"utf16be";

NSString *const kTiByteTypeName = @"byte";
NSString *const kTiShortTypeName = @"short";
NSString *const kTiIntTypeName = @"int";
NSString *const kTiLongTypeName = @"long";
NSString *const kTiFloatTypeName = @"float";
NSString *const kTiDoubleTypeName = @"double";

NSString *const kTiContextShutdownNotification = @"TiContextShutdown";
NSString *const kTiWillShutdownNotification = @"TiWillShutdown";
NSString *const kTiShutdownNotification = @"TiShutdown";
NSString *const kTiSuspendNotification = @"TiSuspend";
NSString *const kTiPausedNotification = @"TiPaused";
NSString *const kTiResumeNotification = @"TiResume";
NSString *const kTiResumedNotification = @"TiResumed";
NSString *const kTiErrorNotification = @"TiError";
NSString *const kTiAnalyticsNotification = @"TiAnalytics";
NSString *const kTiRemoteDeviceUUIDNotification = @"TiDeviceUUID";
NSString *const kTiGestureShakeNotification = @"TiGestureShake";
NSString *const kTiRemoteControlNotification = @"TiRemoteControl";
NSString *const kTiBackgroundFetchNotification = @"TiBackgroundFetch";
NSString *const kTiBackgroundProcessNotification = @"TiBackgroundProcess";
NSString *const kTiSilentPushNotification = @"TiSilentPush";
NSString *const kTiBackgroundTransfer = @"TiBackgroundTransfer";
NSString *const kTiCurrentLocale = @"kTiCurrentLocale";
NSString *const kTiUserInteraction = @"kTiUserInteraction";
NSString *const kTiURLDownloadFinished = @"TiDownloadFinished";
NSString *const kTiURLSessionCompleted = @"TiSessionCompleted";
NSString *const kTiURLSessionEventsCompleted = @"TiSessionEventsCompleted";
NSString *const kTiURLDowloadProgress = @"TiDownloadProgress";
NSString *const kTiURLUploadProgress = @"TiUploadProgress";
NSString *const kTiFrameAdjustNotification = @"TiFrameAdjust";
NSString *const kTiLocalNotification = @"TiLocalNotification";
NSString *const kTiLocalNotificationAction = @"TiLocalNotificationAction";
NSString *const kTiRemoteNotificationAction = @"TiRemoteNotificationAction";
NSString *const kTiRemoteExtentionWillExpire = @"remoteextentionwillexpire";
NSString *const kTiUserNotificationSettingsNotification = @"TiUserNotificationSettingsNotification";
NSString *const kTiWatchKitExtensionRequest = @"TiWatchKitExtensionRequest";
NSString *const kTiContinueActivity = @"TiContinueActivity";
NSString *const kTiApplicationShortcut = @"TiApplicationShortcut";
NSString *const kTiApplicationLaunchedFromURL = @"TiApplicationLaunchedFromURL";
NSString *const kTiTraitCollectionChanged = @"TiTraitCollectionChanged";

#ifndef TI_USE_AUTOLAYOUT
NSString *const kTiBehaviorSize = @"SIZE";
NSString *const kTiBehaviorFill = @"FILL";
NSString *const kTiBehaviorAuto = @"auto";
NSString *const kTiUnitPixel = @"px";
NSString *const kTiUnitCm = @"cm";
NSString *const kTiUnitMm = @"mm";
NSString *const kTiUnitInch = @"in";
NSString *const kTiUnitDip = @"dip";
NSString *const kTiUnitDipAlternate = @"dp";
NSString *const kTiUnitSystem = @"system";
NSString *const kTiUnitPercent = @"%";
#endif

NSString *const kTiExceptionSubreason = @"TiExceptionSubreason";
NSString *const kTiExceptionLocation = @"TiExceptionLocation";

BOOL TiExceptionIsSafeOnMainThread = NO;

void TiExceptionThrowWithNameAndReason(NSString *exceptionName, NSString *reason, NSString *subreason, NSString *location)
{
  NSDictionary *details = [NSDictionary dictionaryWithObjectsAndKeys:subreason, kTiExceptionSubreason, location, kTiExceptionLocation, nil];
  @throw [NSException exceptionWithName:exceptionName reason:reason userInfo:details];
}

NSString *JavascriptNameForClass(Class c)
{
  if ([c isSubclassOfClass:[NSString class]])
    return @"String";
  else if ([c isSubclassOfClass:[NSNumber class]])
    return @"Number";
  else if ([c isSubclassOfClass:[NSArray class]])
    return @"Array";
  else if ([c isSubclassOfClass:[NSDictionary class]])
    return @"Object";
  else if ([c isSubclassOfClass:[KrollCallback class]])
    return @"Function";
  else if ([c isSubclassOfClass:[KrollWrapper class]])
    return @"Function";
  else if ([c conformsToProtocol:@protocol(JavascriptClass)]) {
    return [(id<JavascriptClass>)c javascriptClassName];
  }
  return NSStringFromClass(c);
}

void TiThreadPerformOnMainThread(void (^mainBlock)(void), BOOL waitForFinish)
{
  BOOL alreadyOnMainThread = [NSThread isMainThread];

  if (waitForFinish) {
    if (alreadyOnMainThread) {
      mainBlock();
    } else {
      dispatch_sync(dispatch_get_main_queue(), mainBlock);
    }
  } else {
    dispatch_async(dispatch_get_main_queue(), mainBlock);
  }
}
