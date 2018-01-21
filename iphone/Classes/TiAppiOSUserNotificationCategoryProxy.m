/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2016 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#import "TiAppiOSUserNotificationCategoryProxy.h"
#import "TiAppiOSUserNotificationActionProxy.h"
#import "TiUtils.h"

#ifdef USE_TI_APPIOS

@implementation TiAppiOSUserNotificationCategoryProxy

- (void)dealloc
{
  RELEASE_TO_NIL(_notificationCategory);
  [super dealloc];
}

- (NSString *)apiName
{
  return @"Ti.App.iOS.UserNotificationCategory";
}

- (void)_initWithProperties:(NSDictionary *)properties
{
  if (_notificationCategory == nil) {

    id identifier = [properties valueForKey:@"identifier"];
    id actionsForDefaultContext = [properties valueForKey:@"actionsForDefaultContext"];
    id actionsForMinimalContext = [properties valueForKey:@"actionsForMinimalContext"];
    id intentIdentifiers = [properties valueForKey:@"intentIdentifiers"];

    NSMutableArray *defaultActions = [NSMutableArray new];
    NSMutableArray *minimalActions = [NSMutableArray new];

    for (TiAppiOSUserNotificationActionProxy *action in actionsForDefaultContext) {
      [defaultActions addObject:[action notificationAction]];
    }

    for (TiAppiOSUserNotificationActionProxy *action in actionsForMinimalContext) {
      [minimalActions addObject:[action notificationAction]];
    }

    if (intentIdentifiers) {
      for (id intentIdentifier in intentIdentifiers) {
        if (![intentIdentifier isKindOfClass:[NSString class]]) {
          DebugLog(@"[ERROR] All elements in \"intentIdentifiers\" must be a String, \"%@\" is not!", intentIdentifier);
        }
      }
    }

    if ([TiUtils isIOS10OrGreater]) {
      _notificationCategory = [[UNNotificationCategory categoryWithIdentifier:identifier
                                                                      actions:defaultActions
                                                            intentIdentifiers:intentIdentifiers ?: @[]
                                                                      options:UNNotificationCategoryOptionCustomDismissAction] retain];
    } else {
      _notificationCategory = [UIMutableUserNotificationCategory new];

      [_notificationCategory setIdentifier:identifier];
      [_notificationCategory setActions:defaultActions forContext:UIUserNotificationActionContextDefault];
      [_notificationCategory setActions:minimalActions forContext:UIUserNotificationActionContextMinimal];
    }

    RELEASE_TO_NIL(minimalActions);
    RELEASE_TO_NIL(defaultActions);
  }

  [super _initWithProperties:properties];
}

- (id)notificationCategory
{
  return _notificationCategory;
}

- (NSString *)identifier
{
  return [[self notificationCategory] identifier];
}

@end

#endif
