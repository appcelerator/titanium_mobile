/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2019 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 *
 * Special thanks to Steve Tramer for implementing this.
 */
#ifdef USE_TI_NETWORK

#import "TiNetworkBonjourServiceProxy.h"
#import "TiNetworkSocketTCPProxy.h"
#import <TitaniumKit/JSValue+Addons.h>
#import <arpa/inet.h>
#import <netdb.h>
#import <netinet/in.h>
#import <sys/socket.h>

@implementation TiNetworkBonjourServiceProxy

@synthesize pageContext; // TODO: Remove once we've migrated TiNetworkSocketTCPProxy to obj-c API

#pragma mark Public

- (id)init
{
  if (self = [super init]) {
    local = YES;
    domain_ = @"local.";
  }

  return self;
}

- (id)initWithContext:(id<TiEvaluator>)context_ service:(NSNetService *)service_ local:(bool)local_
{
  if (self = [super init]) {
    pageContext = (id)context_; // do not retain // TODO: Remove once we've migrated TiNetworkSocketTCPProxy to obj-c API
    // NOTE: We need to resolve the service to make sure that it's available before opening this socket,
    // and make sure it's available over IPv4.
    socket = nil;

    service = [service_ retain];
    local = local_;

    [service setDelegate:self];
  }

  return self;
}

- (void)_destroy
{
  [self stop:nil];

  // Can only close if not already closed or in error state
  if (socket != nil && ([[socket state] intValue] & (SOCKET_CONNECTED | SOCKET_LISTENING | SOCKET_INITIALIZED))) {
    [socket close:nil];
  }

  RELEASE_TO_NIL(service);
  RELEASE_TO_NIL(socket);
  RELEASE_TO_NIL(name_);
  RELEASE_TO_NIL(type_);
  RELEASE_TO_NIL(domain_);

  pageContext = nil;

  [super _destroy];
}

- (NSString *)apiName
{
  return @"Ti.Network.BonjourService";
}

- (BOOL)isEqual:(id)obj
{
  if ([obj isKindOfClass:[TiNetworkBonjourServiceProxy class]]) {
    if ([[obj name] isEqual:[self name]] &&
        [[(TiNetworkBonjourServiceProxy *)obj type] isEqual:[self type]] &&
        [[obj domain] isEqual:[self domain]]) {
      return true;
    }
  }

  return false;
}

+ (NSString *)stringForErrorCode:(NSNetServicesError)code
{
  switch (code) {
  case NSNetServicesUnknownError:
    return @"UnknownError";
    break;
  case NSNetServicesCollisionError:
    return @"NameCollisionError";
    break;
  case NSNetServicesNotFoundError:
    return @"NotFoundError";
    break;
  case NSNetServicesActivityInProgress:
    return @"InProgress";
    break;
  case NSNetServicesBadArgumentError:
    return @"BadArgumentError";
    break;
  case NSNetServicesCancelledError:
    return @"Cancelled";
    break;
  case NSNetServicesInvalidError:
    return @"InvalidError";
    break;
  case NSNetServicesTimeoutError:
    return @"TimeoutError";
    break;
#if IS_SDK_IOS_14
  case NSNetServicesMissingRequiredConfigurationError:
    return @"MissingRequiredConfigurationError";
    break;
#endif
  }

  return @"";
}

- (void)setName:(NSString *)name
{
  if (service == nil) {
    // hold on to name until we publish
    RELEASE_TO_NIL(name_);
    name_ = [name retain];
  }
}

- (NSString *)name
{
  if (service != nil) {
    return [service name];
  }
  return name_;
}
READWRITE_IMPL(NSString *, name, Name);

- (void)setType:(NSString *)type
{
  if (service == nil) {
    // hold on to type until we publish
    RELEASE_TO_NIL(type_);
    type_ = [type retain];
  }
}

- (NSString *)type
{
  if (service != nil) {
    return [service type];
  }
  return type_;
}
READWRITE_IMPL(NSString *, type, Type);

- (void)setDomain:(NSString *)domain
{
  if (service == nil) {
    // hold on to domain until we publish
    RELEASE_TO_NIL(domain_);
    domain_ = [domain retain];
  }
}

- (NSString *)domain
{
  if (service != nil) {
    return [service domain];
  }
  return domain_;
}
READWRITE_IMPL(NSString *, domain, Domain);

- (JSValue *)socket
{
  return [self NativeToJSValue:socket];
}
GETTER_IMPL(JSValue *, socket, Socket);

- (void)setIsLocal:(bool)isLocal
{
  local = isLocal;
}

- (bool)isLocal
{
  return local;
}
READWRITE_IMPL(bool, isLocal, IsLocal);

- (NSNetService *)service
{
  return service;
}

- (void)publish:(JSValue *)socketProxy withCallback:(JSValue *)callback
{
  if (!local) {
    [self throwException:@"Attempt to republish discovered Bonjour service"
               subreason:nil
                location:CODELOCATION];
  }
  if (published) {
    [self throwException:@"Attempt to republish service"
               subreason:nil
                location:CODELOCATION];
  }

  // TODO: Allow a second optional callback function argument? That would allow for more typical async usage rather than having to add event listeners
  [self setSocketInternal:[self JSValueToNative:socketProxy]];
  if (!socket) {
    [self throwException:@"Attempt to publish service with no associated socket"
               subreason:nil
                location:CODELOCATION];
  }

  if (domain_ == nil) {
    domain_ = @"local.";
  }

  RELEASE_TO_NIL(service);
  id port = [socket valueForUndefinedKey:@"port"];
  service = [[NSNetService alloc] initWithDomain:domain_
                                            type:type_
                                            name:name_
                                            port:[port intValue]];
  [service setDelegate:self];

  if (callback != nil && [callback isFunction]) {
    publishCallback = [callback retain];
  }
  [service publish];
  // TODO: release name_, domain_ and type_ to nil now?
}

- (void)resolve:(NSTimeInterval)timeout withCallback:(JSValue *)callback
{
  if (published) {
    [self throwException:@"Attempt to resolve published Bonjour service"
               subreason:nil
                location:CODELOCATION];
  }
  if (socket) {
    [self throwException:@"Attempt to re-resolve service"
               subreason:nil
                location:CODELOCATION];
  }

  if (isnan(timeout)) {
    // FIXME: If isnan, first argument *may* have been the callback function!
    NSArray *args = [JSContext currentArguments];
    if ([args count] > 0) {
      JSValue *firstArg = args[0];
      if ([firstArg isObject]) {
        callback = firstArg;
      }
    }
    timeout = 120.0;
  } else if (timeout < 0) {
    timeout = 120.0;
  }

  if (callback != nil && [callback isFunction]) {
    resolveCallback = [callback retain];
  }

  [service resolveWithTimeout:timeout];
}

- (void)stop:(JSValue *)callback
{
  if (callback != nil && [callback isFunction]) {
    stopCallback = [callback retain];
  }
  [service stop];
}

#pragma mark Private

- (void)setSocketInternal:(TiNetworkSocketTCPProxy *)socket_
{
  RELEASE_TO_NIL(socket);
  socket = [socket_ retain];
}

#pragma mark Delegate methods

#pragma mark Publication

- (void)netService:(NSNetService *)service_ didNotPublish:(NSDictionary *)errorDict
{
  // Fire event to notify of error publishing!
  NSNetServicesError code = [[errorDict valueForKey:NSNetServicesErrorCode] integerValue];
  NSString *message = [TiNetworkBonjourServiceProxy stringForErrorCode:code];
  NSDictionary *error = @{
    @"code" : NUMINTEGER(code),
    @"message" : message,
    @"success" : @NO
  };
  [self fireEvent:@"publish" withDict:error];
  if (publishCallback != nil) {
    // Create a real Error object!
    JSValue *errorObj = [self createError:message subreason:nil location:CODELOCATION inContext:[publishCallback context]];
    errorObj[@"code"] = NUMINTEGER(code);
    [publishCallback callWithArguments:@[ errorObj, @NO ]];
    RELEASE_TO_NIL(publishCallback);
  }
}

- (void)netServiceDidPublish:(NSNetService *)service_
{
  published = YES;
  // Fire event to notify of success publishing!
  [self fireEvent:@"publish"
         withDict:@{
           @"code" : @0,
           @"message" : [NSNull null],
           @"success" : @YES
         }];
  if (publishCallback != nil) {
    [publishCallback callWithArguments:@[ [NSNull null], @YES ]];
    RELEASE_TO_NIL(publishCallback);
  }
}

#pragma mark Resolution

- (void)netService:(NSNetService *)service_ didNotResolve:(NSDictionary *)errorDict
{
  // Fire event to notify of error resolving!
  NSNetServicesError code = [[errorDict valueForKey:NSNetServicesErrorCode] integerValue];
  NSString *message = [TiNetworkBonjourServiceProxy stringForErrorCode:code];
  [self fireEvent:@"resolve"
         withDict:@{
           @"code" : NUMINTEGER(code),
           @"message" : message,
           @"success" : @NO
         }];
  if (resolveCallback != nil) {
    // Create a real Error object!
    JSValue *errorObj = [self createError:message subreason:nil location:CODELOCATION inContext:[resolveCallback context]];
    errorObj[@"code"] = NUMINTEGER(code);
    [resolveCallback callWithArguments:@[ errorObj, @NO ]];
    RELEASE_TO_NIL(resolveCallback);
  }
}

- (void)netServiceDidResolveAddress:(NSNetService *)service_
{
  // TODO: Do we really need to only check IPv4?  Why not just resolve the first given address?
  NSData *addressData = nil;
  NSEnumerator *addressEnum = [[service addresses] objectEnumerator];
  while (addressData = [addressEnum nextObject]) {
    const struct sockaddr *address = [addressData bytes];
    if (address->sa_family == AF_INET) {
      [self setSocketInternal:[[TiNetworkSocketTCPProxy alloc] _initWithPageContext:pageContext
                                                                               args:@[
                                                                                 @{
                                                                                   @"port" : NUMINTEGER([service port]),
                                                                                   @"host" : [service hostName]
                                                                                 }
                                                                               ]]];
      // Fire event to notify of success resolving!
      [self fireEvent:@"resolve"
             withDict:@{
               @"code" : @0,
               @"message" : [NSNull null],
               @"success" : @YES
             }];
      if (resolveCallback != nil) {
        [resolveCallback callWithArguments:@[ [NSNull null], @YES ]];
        RELEASE_TO_NIL(resolveCallback);
      }
      break;
    }
  }
}

#pragma mark Stopping

- (void)netServiceDidStop:(NSNetService *)service_
{
  published = NO;
  // Fire event to notify success stopping!
  [self fireEvent:@"stop"
         withDict:@{
           @"code" : @0,
           @"message" : [NSNull null],
           @"success" : @YES
         }];
  if (stopCallback != nil) {
    [stopCallback callWithArguments:@[ [NSNull null], @YES ]];
    RELEASE_TO_NIL(stopCallback);
  }
}

@end

#endif
