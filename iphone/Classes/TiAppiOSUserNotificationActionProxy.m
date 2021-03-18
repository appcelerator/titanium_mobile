/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2018 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#import "TiAppiOSUserNotificationActionProxy.h"
#import <TitaniumKit/TiUtils.h>

#ifdef USE_TI_APPIOS

@implementation TiAppiOSUserNotificationActionProxy

- (void)dealloc
{
  RELEASE_TO_NIL(_notificationAction);
  [super dealloc];
}

- (NSString *)apiName
{
  return @"Ti.App.iOS.UserNotificationAction";
}

- (void)_initWithProperties:(NSDictionary *)properties
{
  if (_notificationAction == nil) {
    id identifier = [properties valueForKey:@"identifier"];
    id title = [properties valueForKey:@"title"];
    id activationMode = [properties valueForKey:@"activationMode"];
    id authenticationRequired = [properties valueForKey:@"authenticationRequired"];
    id destructive = [properties valueForKey:@"destructive"];
    id behavior = [properties valueForKey:@"behavior"];
    id textInputButtonTitle = [properties valueForKey:@"textInputButtonTitle"];
    id textInputButtonPlaceholder = [properties valueForKey:@"textInputButtonPlaceholder"];

    UNNotificationActionOptions options = UNNotificationActionOptionNone;

    if (destructive) {
      options |= UNNotificationActionOptionDestructive;
    }

    if (authenticationRequired) {
      options |= UNNotificationActionOptionAuthenticationRequired;
    }

    _notificationAction = [[UNNotificationAction actionWithIdentifier:identifier
                                                                title:title
                                                              options:[TiUtils intValue:activationMode]] retain];
  }

  [super _initWithProperties:properties];
}

- (id)notificationAction
{
  return _notificationAction;
}

@end

#endif
