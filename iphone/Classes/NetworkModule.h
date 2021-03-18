/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_NETWORK

#import "Reachability.h"
#import <TitaniumKit/KrollCallback.h>
#import <TitaniumKit/TiModule.h>

typedef enum {
  // DEFAULT TLS is 0
  TLS_VERSION_1_0 = 1,
  TLS_VERSION_1_1,
  TLS_VERSION_1_2,
  TLS_VERSION_1_3
} TLSVersion;

@interface NetworkModule : TiModule {
  @private
  TiNetworkConnectionState state;
  KrollCallback *pushNotificationCallback;
  KrollCallback *pushNotificationError;
  KrollCallback *pushNotificationSuccess;
  Reachability *reachability;

  TiProxy *socketProxy;
}

@property (nonatomic, readonly) NSNumber *online;
@property (nonatomic, readonly) NSString *networkTypeName;
@property (nonatomic, readonly) NSNumber *networkType;
@property (nonatomic, readonly) NSString *remoteDeviceUUID;
@property (nonatomic, readonly) NSNumber *remoteNotificationsEnabled;

@property (nonatomic, readonly) NSNumber *NETWORK_NONE;
@property (nonatomic, readonly) NSNumber *NETWORK_WIFI;
@property (nonatomic, readonly) NSNumber *NETWORK_MOBILE;
@property (nonatomic, readonly) NSNumber *NETWORK_LAN;
@property (nonatomic, readonly) NSNumber *NETWORK_UNKNOWN;

@property (nonatomic, readonly) NSNumber *NOTIFICATION_TYPE_BADGE;
@property (nonatomic, readonly) NSNumber *NOTIFICATION_TYPE_ALERT;
@property (nonatomic, readonly) NSNumber *NOTIFICATION_TYPE_SOUND;

@property (nonatomic, readonly) NSNumber *TLS_VERSION_1_0;
@property (nonatomic, readonly) NSNumber *TLS_VERSION_1_1;
@property (nonatomic, readonly) NSNumber *TLS_VERSION_1_2;
@property (nonatomic, readonly) NSNumber *TLS_VERSION_1_3;

- (id)encodeURIComponent:(id)args;
- (id)decodeURIComponent:(id)args;

#pragma mark Private
- (void)updateReachabilityStatus;

+ (NSOperationQueue *)operationQueue;

@end

#endif
