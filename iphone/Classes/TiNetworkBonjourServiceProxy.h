/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2019 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 *
 * Special thanks to Steve Tramer for implementing this.
 */
#ifdef USE_TI_NETWORK

@import JavaScriptCore;
@import Foundation;
@import Foundation.NSNetServices;
@import TitaniumKit.ObjcProxy;

@class TiNetworkSocketTCPProxy; // forward declare

@protocol TiNetworkBonjourServiceProxyExports <JSExport>
// Properties (and accessors)
@property (nonatomic, assign) NSString *domain;
PROPERTY(bool, isLocal, IsLocal);
@property (nonatomic, assign) NSString *name;
@optional
@property (nonatomic, strong) JSValue *socket;
@property (nonatomic, assign) NSString *type;

// Methods
// FIXME: socketProxy can be TiNetworkSocketTCPProxy* once that proxy is moved to obj-c api
JSExportAs(publish,
           -(void)publish
           : (JSValue *)socketProxy withCallback
           : (JSValue *)callback);
JSExportAs(resolve,
           -(void)resolve
           : (NSTimeInterval)timeout withCallback
           : (JSValue *)callback);
- (void)stop:(JSValue *)callback;

@end

// NSNetService Delegate
@interface TiNetworkBonjourServiceProxy : ObjcProxy <TiNetworkBonjourServiceProxyExports, NSNetServiceDelegate> {
  @private
  TiNetworkSocketTCPProxy *socket;
  NSNetService *service;
  BOOL local;
  BOOL published;

  // Temporarily hold onto name/type/domain for manually created services until we publish
  NSString *name_;
  NSString *domain_;
  NSString *type_;

  id<TiEvaluator> pageContext; // TODO: Remove once we've migrated TiNetworkSocketTCPProxy to obj-c API
  JSValue *publishCallback;
  JSValue *resolveCallback;
  JSValue *stopCallback;
}

- (NSNetService *)service;

- (id)initWithContext:(id<TiEvaluator>)context_ service:(NSNetService *)service_ local:(bool)local_;

@property (readonly, nonatomic) id<TiEvaluator> pageContext; // TODO: Remove once we've migrated TiNetworkSocketTCPProxy to obj-c API

#pragma mark internal

+ (NSString *)stringForErrorCode:(NSNetServicesError)code;

@end

#endif
