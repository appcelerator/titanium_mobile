/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2018-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#import "TiEvaluator.h"
#import <JavaScriptCore/JavaScriptCore.h>
#import <pthread.h>

// Macros to make life easier for defining properties with getters/setter accessor methods (which we'll remove in SDK 10.0.0

// Defines a setProp() accessor method in JS-world that points to setterProp:(TYPE)value in native code
#define SETTER(TYPE, NAME) JSExportAs(set##NAME, -(void)setter##NAME \
                                      : (TYPE)value);
// Implements the setterProp:(TYPEvalue method that forwards to typical setProp:value impl
#define SETTER_IMPL(TYPE, NAME)     \
  -(void)setter##NAME : (TYPE)value \
  {                                 \
    [self set##NAME:value];         \
  }

// Defines a getProp() method in JS-land
#define GETTER(TYPE, NAME) \
  -(TYPE)get##NAME __attribute((deprecated("Use the property instead.")));
// Implements the special getter method that forwards to typical property name getter
#define GETTER_IMPL(TYPE, LOWER, UPPER) \
  -(TYPE)get##UPPER                     \
  {                                     \
    return [self LOWER];                \
  }

#define READWRITE_IMPL(TYPE, LOWER, UPPER) \
  GETTER_IMPL(TYPE, LOWER, UPPER);         \
  SETTER_IMPL(TYPE, UPPER);

#define PROPERTY(TYPE, LOWER, UPPER) \
  @property TYPE LOWER;

#define CONSTANT(TYPE, NAME) \
  @property (readonly) TYPE NAME;

#define READONLY_PROPERTY(TYPE, LOWER, UPPER) \
  CONSTANT(TYPE, LOWER);

// TODO: Log a warning/error if negative?
// We could also define as NSUInteger and do: if (arg - 1 == NSIntegerMax) as NaN check
#define OPTIONAL_UINT_ARGUMENT(ARG_NAME, NEW_NAME, DEFAULT) \
  NSUInteger NEW_NAME = DEFAULT;                            \
  if (ARG_NAME < 0 || !isnan([ARG_NAME doubleValue])) {     \
    NEW_NAME = [ARG_NAME unsignedIntegerValue];             \
  }

// Methods for grabbing properties from a JSValue object argument
#define ENSURE_PROPERTY_EXISTS(dict, name)                                                                                                                 \
  if (![dict hasProperty:name]) {                                                                                                                          \
    [self throwException:TiExceptionInvalidType subreason:[NSString stringWithFormat:@"required property \"%@\" is missing", name] location:CODELOCATION]; \
    return nil;                                                                                                                                            \
  }

#define ENSURE_UINT32_OR_DEFAULT(dict, name, fallback) \
  ([dict hasProperty:name]) ? [dict[name] toUInt32] : fallback;

#define ENSURE_STRING_OR_DEFAULT(dict, name, fallback) \
  ([dict hasProperty:name]) ? [dict[name] toString] : fallback;

@protocol ProxyExports <JSExport>

// Properties (and accessors)
READONLY_PROPERTY(NSString *, apiName, ApiName);

/**
 Indicates that this proxy should honor bubbling of user events, if the proxy
 is the type that has a parent to bubble to (This is primairly views, but may
 have some exceptions).
 */
PROPERTY(BOOL, bubbleParent, BubbleParent);

// Methods
JSExportAs(addEventListener,
           -(void)addEventListener
           : (NSString *)name withCallback
           : (JSValue *)callback);
JSExportAs(removeEventListener,
           -(void)removeEventListener
           : (NSString *)name withCallback
           : (JSValue *)callback);
JSExportAs(fireEvent,
           -(void)fireEvent
           : (NSString *)name withDict
           : (NSDictionary *)dict);

@end

/**
 The base class for Titanium proxies using new Obj-C API.
 */
@interface ObjcProxy : NSObject <ProxyExports> {
  @private
  NSMutableDictionary *_listeners; // new listener map for Obj-C JSC API
  pthread_rwlock_t _listenerLock;
  NSURL *baseURL;
}

- (NSURL *)_baseURL;

- (void)_destroy;
- (void)_configure;

/**
  @deprecated Only here for backwards compatibility with SDK < 8.1.0. Use `init` instead.
 */
- (id)_initWithPageContext:(id<TiEvaluator>)context __attribute__((deprecated));
- (id)_initWithPageContext:(id<TiEvaluator>)context args:(NSArray *)args __attribute__((deprecated));

// hooks for when an event listener gets added/removed
- (void)_listenerAdded:(NSString *)type count:(int)count;
- (void)_listenerRemoved:(NSString *)type count:(int)count;

/**
 Whether or not the proxy has listeners for the specified event type.
 @param type The event type.
 @return _YES_ if the proxy has any listeners for the specified event type, _NO_ otherwise.
 */
- (BOOL)_hasListeners:(NSString *)type;

- (void)fireEvent:(NSString *)name withDict:(NSDictionary *)dict;
/**
 Tells the proxy to fire an event of the specified type to a listener.
 @param type The event type.
 @param obj The event properties.
 @param listener The listener to fire event for.
 */
- (void)_fireEventToListener:(NSString *)type withObject:(id)obj listener:(JSValue *)listener;

/**
 The convenience method to raise an exception .
 @param reason The exception reason.
 @param subreason The exception subreason.
 @param location The exception location.
 */
+ (void)throwException:(NSString *)reason subreason:(NSString *)subreason location:(NSString *)location;
- (void)throwException:(NSString *)reason subreason:(NSString *)subreason location:(NSString *)location;

+ (JSValue *)createError:(NSString *)reason subreason:(NSString *)subreason location:(NSString *)location inContext:(JSContext *)context;
- (JSValue *)createError:(NSString *)reason subreason:(NSString *)subreason location:(NSString *)location inContext:(JSContext *)context;

// FIXME: Should id be TiProxy* here?
- (id)JSValueToNative:(JSValue *)jsValue;
- (JSValue *)NativeToJSValue:(id)proxy;
- (JSValue *)JSValue;
- (JSValue *)JSValueInContext:(JSContext *)context;

/**
 * Convenience method to interface with "old-style" proxies, which typically need this passed in as
 * the "page context" in their initializers.
 **/
- (id<TiEvaluator>)executionContext;
+ (id<TiEvaluator>)executionContext:(JSContext *)jsContext;

/**
 * Due to mix and match of new and old C API, JSContext.currentContext (and currentThis) may be null
 * If we get called back through the old C API function callback, it will be, so we need to hack.
 */
- (JSContext *)currentContext;

@end
