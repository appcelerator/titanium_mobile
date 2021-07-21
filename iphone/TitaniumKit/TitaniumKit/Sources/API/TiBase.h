/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2015 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#import "TiSharedConfig.h"
#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

#ifndef TI_BASE_H
#define TI_BASE_H

#ifdef __cplusplus
extern "C" {
#endif

#define MEMORY_DEBUG 0
#define VIEW_DEBUG 0

#ifndef __IPHONE_4_1
#define __IPHONE_4_1 40100
#endif

#ifndef __IPHONE_4_2
#define __IPHONE_4_2 40200
#endif

#ifndef __IPHONE_4_3
#define __IPHONE_4_3 40300
#endif

#ifndef __IPHONE_5_0
#define __IPHONE_5_0 50000
#endif

#ifndef __IPHONE_5_1
#define __IPHONE_5_1 50100
#endif

#ifndef __IPHONE_6_0
#define __IPHONE_6_0 60000
#endif

#ifdef DEBUG
// Kroll memory debugging
#define KROLLBRIDGE_MEMORY_DEBUG MEMORY_DEBUG
#define KOBJECT_MEMORY_DEBUG MEMORY_DEBUG
#define CONTEXT_MEMORY_DEBUG MEMORY_DEBUG

// Proxy memory debugging
#define PROXY_MEMORY_TRACK MEMORY_DEBUG
#define TABWINDOW_MEMORY_DEBUG MEMORY_DEBUG
#define CONTEXT_DEBUG MEMORY_DEBUG

// Kroll debugging
#define KOBJECT_DEBUG MEMORY_DEBUG
#define KMETHOD_DEBUG MEMORY_DEBUG
#endif

// in simulator we redefine to format for Titanium Developer console

#define TI_INLINE static __inline__

#define TI_BACKGROUNDFETCH_MAX_INTERVAL 29

// We need to overload NSLog as a macro so that we capture system messages as well.
// It has to be a wrapper because other appc-libraries use TiBase's NSLog, and can't
// spoof TiApp without symbol conflicts and other issues

#define NSLog(...)             \
  {                            \
    TiLogMessage(__VA_ARGS__); \
  }

// create a mutable array that doesn't retain internal references to objects
NSMutableArray *TiCreateNonRetainingArray(void);

// create a mutable dictionary that doesn't retain internal references to objects
NSMutableDictionary *TiCreateNonRetainingDictionary(void);

CGPoint midpointBetweenPoints(CGPoint a, CGPoint b);
void TiLogMessage(NSString *str, ...);

/**
 * Protocol for classes to provide their JavaScript details (class name, in particular).
 */
@protocol JavascriptClass <NSObject>
@required
+ (NSString *)javascriptClassName;
@end

NSString *JavascriptNameForClass(Class c);

#define CLASS2JS(x) JavascriptNameForClass(x)
#define OBJTYPE2JS(x) JavascriptNameForClass([x class])

#define degreesToRadians(x) (M_PI * x / 180.0)
#define radiansToDegrees(x) (x * (180.0 / M_PI))

// TODO: Need to update RELEASE_TO_NIL etc. to be friendly to rememberproxy/forgetproxy for concurrent
// memory mgt.
#define RELEASE_TO_NIL(x) \
  {                       \
    if (x != nil) {       \
      [x release];        \
      x = nil;            \
    }                     \
  }
#define RELEASE_TO_NIL_AUTORELEASE(x) \
  {                                   \
    if (x != nil) {                   \
      [x autorelease];                \
      x = nil;                        \
    }                                 \
  }
#define RELEASE_AND_REPLACE(x, y) \
  {                               \
    [x release];                  \
    x = [y retain];               \
  }

#ifdef DEBUG
#define CODELOCATION [NSString stringWithFormat:@"%s (%@:%d)", __FUNCTION__, [[NSString stringWithFormat:@"%s", __FILE__] lastPathComponent], __LINE__]
#else
#define CODELOCATION @""
#endif

#define NULL_IF_NIL(x) ({ id xx = (x); (xx==nil)?[NSNull null]:xx; })

#define IS_NULL_OR_NIL(x) ((x == nil) || ((id)x == [NSNull null]))

#define ENSURE_CLASS_OR_NIL(x, t)                                                                                                                                         \
  if (IS_NULL_OR_NIL(x)) {                                                                                                                                                \
    x = nil;                                                                                                                                                              \
  } else if (![x isKindOfClass:t]) {                                                                                                                                      \
    [self throwException:TiExceptionInvalidType subreason:[NSString stringWithFormat:@"expected: %@ or nil, was: %@", CLASS2JS(t), OBJTYPE2JS(x)] location:CODELOCATION]; \
  }

#define ENSURE_TYPE_OR_NIL(x, t) ENSURE_CLASS_OR_NIL(x, [t class])

#define ENSURE_CLASS(x, t)                                                                                                                                         \
  if (![x isKindOfClass:t]) {                                                                                                                                      \
    [self throwException:TiExceptionInvalidType subreason:[NSString stringWithFormat:@"expected: %@, was: %@", CLASS2JS(t), OBJTYPE2JS(x)] location:CODELOCATION]; \
  }

#define ENSURE_TYPE(x, t) ENSURE_CLASS(x, [t class])

//NOTE: these checks can be pulled out of production build type

//Question: Given that some of these silently massage the data during development but not production,
//Should the data massage either be kept in production or removed in development? --Blain.

#define ENSURE_STRING_OR_NIL(x)                        \
  if ([x respondsToSelector:@selector(stringValue)]) { \
    x = [(id)x stringValue];                           \
  } else {                                             \
    ENSURE_TYPE_OR_NIL(x, NSString);                   \
  }

#define ENSURE_SINGLE_ARG(x, t)                                        \
  if ([x isKindOfClass:[NSArray class]] && [(NSArray *)x count] > 0) { \
    x = (t *)[x objectAtIndex:0];                                      \
  }                                                                    \
  ENSURE_TYPE(x, t);

#define ENSURE_SINGLE_ARG_OR_NIL(x, t) \
  if (IS_NULL_OR_NIL(x)) {             \
    x = nil;                           \
  } else {                             \
    ENSURE_SINGLE_ARG(x, t);           \
  }

#define ENSURE_ARG_AT_INDEX(out, args, index, type)                              \
  if ([args isKindOfClass:[NSArray class]] && [(NSArray *)args count] > index) { \
    out = (type *)[(NSArray *)args objectAtIndex:index];                         \
  }                                                                              \
  ENSURE_TYPE(out, type);

#define ENSURE_ARG_OR_NIL_AT_INDEX(out, args, index, type)                                                                                                                        \
  if (IS_NULL_OR_NIL(args)) {                                                                                                                                                     \
    out = nil;                                                                                                                                                                    \
  } else if ([args isKindOfClass:[NSArray class]]) {                                                                                                                              \
    if ([(NSArray *)args count] > index) {                                                                                                                                        \
      out = [(NSArray *)args objectAtIndex:index];                                                                                                                                \
    } else {                                                                                                                                                                      \
      out = nil;                                                                                                                                                                  \
    }                                                                                                                                                                             \
    if (out && ![out isKindOfClass:[type class]]) {                                                                                                                               \
      [self throwException:TiExceptionInvalidType subreason:[NSString stringWithFormat:@"expected: %@, was: %@", CLASS2JS([type class]), OBJTYPE2JS(out)] location:CODELOCATION]; \
    }                                                                                                                                                                             \
  }

#define COERCE_TO_INT(out, in)                                                                                                                                \
  if (![in respondsToSelector:@selector(intValue)]) {                                                                                                         \
    [self throwException:TiExceptionInvalidType subreason:[NSString stringWithFormat:@"cannot coerce type %@ to int", OBJTYPE2JS(in)] location:CODELOCATION]; \
  }                                                                                                                                                           \
  out = [in intValue];

#define ENSURE_INT_AT_INDEX(out, args, index)        \
  {                                                  \
    id tmp = nil;                                    \
    ENSURE_ARG_AT_INDEX(tmp, args, index, NSObject); \
    COERCE_TO_INT(out, tmp);                         \
  }

#define ENSURE_INT_OR_NIL_AT_INDEX(out, args, index, hasValue) \
  {                                                            \
    id tmp = nil;                                              \
    ENSURE_ARG_OR_NIL_AT_INDEX(tmp, args, index, NSObject);    \
    if (tmp == nil) {                                          \
      hasValue = NO;                                           \
    } else {                                                   \
      hasValue = YES;                                          \
      COERCE_TO_INT(out, tmp)                                  \
    }                                                          \
  }

#define ENSURE_ARG_FOR_KEY(out, args, key, type) \
  {                                              \
    out = [args objectForKey:key];               \
    ENSURE_TYPE(out, type);                      \
  }

#define ENSURE_ARG_OR_NIL_FOR_KEY(out, args, key, type) \
  {                                                     \
    out = [args objectForKey:key];                      \
    ENSURE_TYPE_OR_NIL(out, type);                      \
  }

#define ENSURE_INT_FOR_KEY(out, args, key)        \
  {                                               \
    id tmp = nil;                                 \
    ENSURE_ARG_FOR_KEY(tmp, args, key, NSObject); \
    COERCE_TO_INT(out, tmp);                      \
  }

#define ENSURE_INT_OR_NIL_FOR_KEY(out, args, key, hasValue) \
  {                                                         \
    id tmp = nil;                                           \
    ENSURE_ARG_OR_NIL_FOR_KEY(tmp, args, key, NSObject);    \
    if (tmp == nil) {                                       \
      hasValue = NO;                                        \
    } else {                                                \
      hasValue = YES;                                       \
      COERCE_TO_INT(out, tmp);                              \
    }                                                       \
  }

//Because both NSString and NSNumber respond to intValue, etc, this is a wider net
#define ENSURE_METHOD(x, t)                                                                                                                                            \
  if (![x respondsToSelector:@selector(t)]) {                                                                                                                          \
    [self throwException:TiExceptionInvalidType subreason:[NSString stringWithFormat:@"%@ doesn't respond to method: %@", OBJTYPE2JS(x), @ #t] location:CODELOCATION]; \
  }

#define ENSURE_ARG_COUNT(x, c)                                                                                                                                                            \
  if ([x count] < c) {                                                                                                                                                                    \
    [self throwException:TiExceptionNotEnoughArguments subreason:[NSString stringWithFormat:@"expected %d arguments, received: %lu", c, (unsigned long)[x count]] location:CODELOCATION]; \
  }

#define VALUE_AT_INDEX_OR_NIL(x, i) \
  ({ NSArray * y = (x); ([y count]>i)?[y objectAtIndex:i]:nil; })

#define ENSURE_CONSISTENCY(x)                                                                   \
  if (!(x)) {                                                                                   \
    [self throwException:TiExceptionInternalInconsistency subreason:nil location:CODELOCATION]; \
  }

#define ENSURE_VALUE_CONSISTENCY(x, v)                                                                                                                 \
  {                                                                                                                                                    \
    __typeof__(x) __x = (x);                                                                                                                           \
    __typeof__(v) __v = (v);                                                                                                                           \
    if (__x != __v) {                                                                                                                                  \
      [self throwException:TiExceptionInternalInconsistency subreason:[NSString stringWithFormat:@"(" #x ") was not (" #v ")"] location:CODELOCATION]; \
    }                                                                                                                                                  \
  }

#define ENSURE_VALUE_RANGE(x, minX, maxX)                                                                                                                                                                \
  {                                                                                                                                                                                                      \
    __typeof__(x) __x = (x);                                                                                                                                                                             \
    __typeof__(minX) __minX = (minX);                                                                                                                                                                    \
    __typeof__(maxX) __maxX = (maxX);                                                                                                                                                                    \
    if ((__x < __minX) || (__x > __maxX)) {                                                                                                                                                              \
      [self throwException:TiExceptionRangeError subreason:[NSString stringWithFormat:@"%lld was not >= %lld and <= %lld", (long long)__x, (long long)__maxX, (long long)__minX] location:CODELOCATION]; \
    }                                                                                                                                                                                                    \
  }

#define ENSURE_DICT(x) ENSURE_TYPE(x, NSDictionary)
#define ENSURE_ARRAY(x) ENSURE_TYPE(x, NSArray)
#define ENSURE_STRING(x) ENSURE_TYPE(x, NSString)

void TiExceptionThrowWithNameAndReason(NSString *exceptionName, NSString *reason, NSString *subreason, NSString *location);

#define DEFINE_EXCEPTIONS                                                                                      \
  -(void)throwException : (NSString *)reason subreason : (NSString *)subreason location : (NSString *)location \
  {                                                                                                            \
    NSString *exceptionName = [@"org.appcelerator." stringByAppendingString:NSStringFromClass([self class])];  \
    TiExceptionThrowWithNameAndReason(exceptionName, reason, subreason, location);                             \
  }                                                                                                            \
                                                                                                               \
  +(void)throwException : (NSString *)reason subreason : (NSString *)subreason location : (NSString *)location \
  {                                                                                                            \
    NSString *exceptionName = @"org.appcelerator";                                                             \
    TiExceptionThrowWithNameAndReason(exceptionName, reason, subreason, location);                             \
  }

#define THROW_INVALID_ARG(m) \
  [self throwException:TiExceptionInvalidType subreason:m location:CODELOCATION];

#define MAKE_SYSTEM_PROP_IPAD(name, map)   \
  -(NSNumber *)name                        \
  {                                        \
    if ([TiUtils isIPad]) {                \
      return [NSNumber numberWithInt:map]; \
    }                                      \
  }

#define MAKE_SYSTEM_PROP(name, map)      \
  -(NSNumber *)name                      \
  {                                      \
    return [NSNumber numberWithInt:map]; \
  }

#define MAKE_SYSTEM_PROP_UINTEGER(name, map)         \
  -(NSNumber *)name                                  \
  {                                                  \
    return [NSNumber numberWithUnsignedInteger:map]; \
  }

#define MAKE_SYSTEM_PROP_DEPRECATED_REPLACED(name, map, api, in, newapi) \
  -(NSNumber *)name                                                      \
  {                                                                      \
    DEPRECATED_REPLACED(api, in, newapi)                                 \
    return [NSNumber numberWithInt:map];                                 \
  }

#define MAKE_SYSTEM_PROP_DEPRECATED_REPLACED_REMOVED(name, map, api, in, removed, newapi) \
  -(NSNumber *)name                                                                       \
  {                                                                                       \
    DEPRECATED_REPLACED_REMOVED(api, in, removed, newapi)                                 \
    return [NSNumber numberWithInt:map];                                                  \
  }

#define MAKE_SYSTEM_PROP_DEPRECATED_REMOVED(name, map, api, in, removed) \
  -(NSNumber *)name                                                      \
  {                                                                      \
    DEPRECATED_REMOVED(api, in, removed)                                 \
    return [NSNumber numberWithInt:map];                                 \
  }

#define MAKE_SYSTEM_PROP_DBL(name, map)     \
  -(NSNumber *)name                         \
  {                                         \
    return [NSNumber numberWithDouble:map]; \
  }

#define MAKE_SYSTEM_STR(name, map) \
  -(NSString *)name                \
  {                                \
    return (NSString *)map;        \
  }

#define MAKE_SYSTEM_UINT(name, map)              \
  -(NSNumber *)name                              \
  {                                              \
    return [NSNumber numberWithUnsignedInt:map]; \
  }

#define MAKE_SYSTEM_NUMBER(name, map) \
  -(NSNumber *)name                   \
  {                                   \
    return map;                       \
  }

#define DEPRECATED_REMOVED(api, in, removed) \
  DebugLog(@"[WARN] Ti.%@ DEPRECATED in %@: REMOVED in %@", api, in, removed);

#define DEPRECATED_REPLACED_REMOVED(api, in, removed, newapi) \
  DebugLog(@"[WARN] Ti.%@ DEPRECATED in %@, in favor of Ti.%@: REMOVED in %@", api, in, newapi, removed);

#define DEPRECATED_REPLACED(api, in, newapi) \
  DebugLog(@"[WARN] Ti.%@ DEPRECATED in %@, in favor of Ti.%@", api, in, newapi);

#define NUMBOOL(x) \
  [NSNumber numberWithBool:x]

#define NUMLONG(x) \
  [NSNumber numberWithLong:x]

#define NUMULONG(x) \
  [NSNumber numberWithUnsignedLong:x]

#define NUMLONGLONG(x) \
  [NSNumber numberWithLongLong:x]

#define NUMINT(x) \
  [NSNumber numberWithInt:x]

#define NUMUINT(x) \
  [NSNumber numberWithUnsignedInt:x]

#define NUMDOUBLE(x) \
  [NSNumber numberWithDouble:x]

#define NUMFLOAT(x) \
  [NSNumber numberWithFloat:x]

#define NUMINTEGER(x) \
  [NSNumber numberWithInteger:x]

#define NUMUINTEGER(x) \
  [NSNumber numberWithUnsignedInteger:x]

//MUST BE NEGATIVE, as it inhabits the same space as UIBarButtonSystemItem
enum {
  UITitaniumNativeItemNone = -1,
  UITitaniumNativeItemSpinner = -2,
  UITitaniumNativeItemProgressBar = -3,

  UITitaniumNativeItemSlider = -4,
  UITitaniumNativeItemSwitch = -5,
  UITitaniumNativeItemMultiButton = -6,
  UITitaniumNativeItemSegmented = -7,

  UITitaniumNativeItemTextView = -8,
  UITitaniumNativeItemTextField = -9,
  UITitaniumNativeItemSearchBar = -10,

  UITitaniumNativeItemPicker = -11,
  UITitaniumNativeItemDatePicker = -12,

  UITitaniumNativeItemInfoLight = -13,
  UITitaniumNativeItemInfoDark = -14,

  UITitaniumNativeItemDisclosure = -15,

  UITitaniumNativeItemContactAdd = -16
};

// common sizes for iPhone (will these change for iPad?)

#define TI_STATUSBAR_HEIGHT 20

#define TI_NAVBAR_HEIGHT 44
#define TI_NAVBAR_HEIGHT_WITH_PROMPT 64 //?
#define TI_NAVBAR_BUTTON_WIDTH 20
#define TI_NAVBAR_BUTTON_HEIGHT 20

#define TI_TABBAR_HEIGHT 49

#define TI_TEXTFIELD_HEIGHT 31

#define TI_KEYBOARD_PORTRAIT_HEIGHT 216
#define TI_KEYBOARD_LANDSCAPE_HEIGHT 140

#define TI_SEARCHBAR_HEIGHT 56

#define TI_STATUSBAR_TAG 11101

#ifdef DEBUG
#define FRAME_DEBUG(f) \
  NSLog(@"FRAME -- size=%fx%f, origin=%f,%f", f.size.width, f.size.height, f.origin.x, f.origin.y);

#else
#define FRAME_DEBUG(f)
#endif

#define DEFINE_DEF_PROP(name, defval)                \
  -(id)name                                          \
  {                                                  \
    id value = [super valueForUndefinedKey:@ #name]; \
    if (value == nil || value == [NSNull null]) {    \
      return defval;                                 \
    }                                                \
    return value;                                    \
  }

#define DEFINE_DEF_BOOL_PROP(name, defval) DEFINE_DEF_PROP(name, NUMBOOL(defval))
#define DEFINE_DEF_NULL_PROP(name) DEFINE_DEF_PROP(name, [NSNull null])
#define DEFINE_DEF_INT_PROP(name, val) DEFINE_DEF_PROP(name, NUMINT(val))

// TI_VERSION will be set via an external source if not set
// display a warning and set it to 0.0.0

#ifndef TI_VERSION
#define TI_VERSION 0.0.0
#endif

#define _QUOTEME(x) #x
#define STRING(x) _QUOTEME(x)

#define TI_VERSION_STR STRING(TI_VERSION)

//#define VERBOSE

#ifdef VERBOSE
#define VerboseLog(...) \
  {                     \
    NSLog(__VA_ARGS__); \
  }
#else
#define VerboseLog(...) \
  {                     \
  }
#endif

#ifdef DEVELOPER
#define DeveloperLog(...) \
  {                       \
    NSLog(__VA_ARGS__);   \
  }
#else
#define DeveloperLog(...) \
  {                       \
  }
#endif

#ifndef DebugLog
#define DebugLog(...)                                  \
  {                                                    \
    if ([TiSharedConfig defaultConfig].debugEnabled) { \
      NSLog(__VA_ARGS__);                              \
    }                                                  \
  }
#endif

#define VAL_OR_NSNULL(foo) (((foo) != nil) ? ((id)foo) : [NSNull null])

NSString *hexString(NSData *thedata);

typedef enum {
  TiNetworkConnectionStateNone = 0,
  TiNetworkConnectionStateWifi = 1,
  TiNetworkConnectionStateMobile = 2,
  TiNetworkConnectionStateLan = 3,
  TiNetworkConnectionStateUnknown = 4,
} TiNetworkConnectionState;

typedef enum {
  TI_BYTE = 1,
  TI_SHORT,
  TI_INT,
  TI_LONG,
  TI_FLOAT,
  TI_DOUBLE
} TiDataType;

typedef enum {
  TI_READ = 1 << 0,
  TI_WRITE = 1 << 1,
  TI_APPEND = 1 << 2
} TiStreamMode;

extern NSString *const kTiASCIIEncoding;
extern NSString *const kTiISOLatin1Encoding;
extern NSString *const kTiUTF8Encoding;
extern NSString *const kTiUTF16Encoding;
extern NSString *const kTiUTF16LEEncoding;
extern NSString *const kTiUTF16BEEncoding;

extern NSString *const kTiByteTypeName;
extern NSString *const kTiShortTypeName;
extern NSString *const kTiIntTypeName;
extern NSString *const kTiLongTypeName;
extern NSString *const kTiFloatTypeName;
extern NSString *const kTiDoubleTypeName;

extern NSString *const kTiContextShutdownNotification;
extern NSString *const kTiWillShutdownNotification;
extern NSString *const kTiShutdownNotification;
extern NSString *const kTiSuspendNotification;
extern NSString *const kTiPausedNotification;
extern NSString *const kTiResumeNotification;
extern NSString *const kTiResumedNotification;
extern NSString *const kTiErrorNotification;
extern NSString *const kTiAnalyticsNotification;
extern NSString *const kTiRemoteDeviceUUIDNotification;
extern NSString *const kTiGestureShakeNotification;
extern NSString *const kTiRemoteControlNotification;
extern NSString *const kTiBackgroundFetchNotification;
extern NSString *const kTiBackgroundProcessNotification;
extern NSString *const kTiSilentPushNotification;
extern NSString *const kTiBackgroundTransfer;
extern NSString *const kTiCurrentLocale;
extern NSString *const kTiUserInteraction;
extern NSString *const kTiFrameAdjustNotification;
extern NSString *const kTiLocalNotification;
extern NSString *const kTiLocalNotificationAction;
extern NSString *const kTiRemoteNotificationAction;
extern NSString *const kTiRemoteExtentionWillExpire;
extern NSString *const kTiUserNotificationSettingsNotification;
extern NSString *const kTiURLDownloadFinished;
extern NSString *const kTiURLSessionCompleted;
extern NSString *const kTiURLSessionEventsCompleted;
extern NSString *const kTiURLDowloadProgress;
extern NSString *const kTiURLUploadProgress;
extern NSString *const kTiWatchKitExtensionRequest;
extern NSString *const kTiContinueActivity;
extern NSString *const kTiApplicationShortcut;
extern NSString *const kTiApplicationLaunchedFromURL;
extern NSString *const kTiTraitCollectionChanged;

#ifndef TI_USE_AUTOLAYOUT
extern NSString *const kTiBehaviorSize;
extern NSString *const kTiBehaviorFill;
extern NSString *const kTiBehaviorAuto;
extern NSString *const kTiUnitPixel;
extern NSString *const kTiUnitCm;
extern NSString *const kTiUnitMm;
extern NSString *const kTiUnitInch;
extern NSString *const kTiUnitDip;
extern NSString *const kTiUnitDipAlternate;
extern NSString *const kTiUnitSystem;
extern NSString *const kTiUnitPercent;
#endif
extern NSString *const kTiExceptionSubreason;
extern NSString *const kTiExceptionLocation;

#ifndef ASI_AUTOUPDATE_NETWORK_INDICATOR
#define ASI_AUTOUPDATE_NETWORK_INDICATOR 0
#endif

#ifndef ASI_AUTOUPDATE_NETWORK_INDICATOR
#define REACHABILITY_20_API 1
#endif

#include "TiThreading.h"

/**
 *	TiThreadPerformOnMainThread should replace all Titanium instances of
 *	performSelectorOnMainThread, ESPECIALLY if wait is to be yes. That way,
 *	exceptional-case main thread activities can process them outside of the
 *	standard event loop.
 */
void TiThreadPerformOnMainThread(void (^mainBlock)(void), BOOL waitForFinish);

#include "TiPublicAPI.h"

#ifdef __cplusplus
}
#endif

#endif
