/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#import "KrollCallback.h"
#import "KrollObject.h"
#import "KrollPromise.h"
#import "TiBindingRunLoop.h"
#import "TiEvaluator.h"
#import <pthread.h>

#ifndef TI_BASE_H
#import "TiBase.h"
#endif

@class KrollBridge;
@class KrollObject;

// Common exceptions to throw when the function call was improper
extern NSString *const TiExceptionInvalidType;
extern NSString *const TiExceptionNotEnoughArguments;
extern NSString *const TiExceptionRangeError;

extern NSString *const TiExceptionOSError;

// This is when a normally allowed command is not allowed (Say, adding a row to a table when it already is added elsewhere)
extern NSString *const TiExceptionInternalInconsistency;

// Rare exceptions to indicate a bug in the titanium code (Eg, function that a subclass should have implemented)
extern NSString *const TiExceptionUnimplementedFunction;

// Rare exception in the case of malloc failure
extern NSString *const TiExceptionMemoryFailure;

@class TiHost;
@class TiProxy;

typedef enum {
  NativeBridge,
  WebBridge
} TiProxyBridgeType;

/**
 The proxy delegate protocol
 */
@protocol TiProxyDelegate <NSObject>

@required

/**
 Tells the delegate that the proxy property has changed.
 @param key The property name.
 @param oldValue An old value of the property.
 @param newValue A new value of the property.
 @param proxy The proxy where the property has changed.
 */
- (void)propertyChanged:(NSString *)key oldValue:(id)oldValue newValue:(id)newValue proxy:(TiProxy *)proxy;

@optional

/**
 Tells the delegate to read proxy values.
 @param keys The enumeration of keys to read.
 */
- (void)readProxyValuesWithKeys:(id<NSFastEnumeration>)keys;

/**
 Tells the delegate that a listener has been added to the proxy.
 @param type The listener type.
 @param count The current number of active listeners
 */
- (void)listenerAdded:(NSString *)type count:(int)count;

/**
 Tells the delegate that a listener has been removed to the proxy.
 @param type The listener type.
 @param count The current number of active listeners after the remove
 */
- (void)listenerRemoved:(NSString *)type count:(int)count;

/**
 Tells the delegate to detach from proxy.
 */
- (void)detachProxy;

@end

SEL SetterForKrollProperty(NSString *key);
SEL SetterWithObjectForKrollProperty(NSString *key);

void DoProxyDelegateChangedValuesWithProxy(UIView<TiProxyDelegate> *target, NSString *key, id oldValue, id newValue, TiProxy *proxy);
void DoProxyDelegateReadValuesWithKeysFromProxy(UIView<TiProxyDelegate> *target, id<NSFastEnumeration> keys, TiProxy *proxy);
//Why are these here? Because they can be commonly used between TiUIView and TiUITableViewCell.

/**
 The base class for Titanium proxies.
 */
@interface TiProxy : NSObject <KrollTargetable> {
  @public
  BOOL _bubbleParent;

  @private
  NSMutableDictionary *listeners;
  BOOL destroyed;
  id<TiProxyDelegate> modelDelegate;
  NSURL *baseURL;
  NSString *krollDescription;
  pthread_rwlock_t listenerLock;
  BOOL reproxying;
  @protected
  NSMutableDictionary *dynprops;
  NSMutableArray *dynpropnames;
  pthread_rwlock_t dynpropsLock; // NOTE: You must respect the dynprops lock when accessing dynprops elsewhere!

  int bridgeCount;
  KrollObject *pageKrollObject;
  id<TiEvaluator> pageContext;
  id<TiEvaluator> executionContext;
}

/* Convenience method, especially for autoloading modules. The selector
 * is a class method taking one argument, which is the TiBindingRunLoop
 * started.
 */
+ (void)performSelectorDuringRunLoopStart:(SEL)selector;

- (void)boundBridge:(id<TiEvaluator>)newBridge withKrollObject:(KrollObject *)newKrollObject;
- (void)unboundBridge:(id<TiEvaluator>)oldBridge;

@property (readonly, nonatomic) id<TiEvaluator> pageContext;
@property (readonly, nonatomic) id<TiEvaluator> executionContext;

@property (readonly, nonatomic) int bindingRunLoopCount;
@property (readonly, nonatomic) TiBindingRunLoop primaryBindingRunLoop;
@property (readonly, nonatomic) NSArray *bindingRunLoopArray;

/**
 Provides access to proxy delegate.
 */
@property (nonatomic, retain, readwrite) id<TiProxyDelegate> modelDelegate;

+ (BOOL)shouldRegisterOnInit;

#pragma mark Private

- (id)_initWithPageContext:(id<TiEvaluator>)context;
- (id)_initWithPageContext:(id<TiEvaluator>)context args:(NSArray *)args;
- (void)_initWithProperties:(NSDictionary *)properties;

/**
 Whether or not the proxy has listeners for the specified event type.
 @param type The event type.
 @return _YES_ if the proxy has any listeners for the specified event type, _NO_ otherwise.
 */
- (BOOL)_hasListeners:(NSString *)type;

/**
 Tells the proxy to fire an event of the specified type to a listener.
 @param type The event type.
 @param obj The event properties.
 @param listener The listener to fire event for.
 @param thisObject The object representing 'this' in the context of the event handler.
 */
- (void)_fireEventToListener:(NSString *)type withObject:(id)obj listener:(KrollCallback *)listener thisObject:(TiProxy *)thisObject;

- (id)_proxy:(TiProxyBridgeType)type;
- (void)contextWasShutdown:(id<TiEvaluator>)context;
- (TiHost *)_host;
- (NSURL *)_baseURL;
- (void)_setBaseURL:(NSURL *)url;
- (void)_destroy;

/**
 Called to perform the proxy initial configuration.
 */
- (void)_configure;

- (void)_dispatchWithObjectOnUIThread:(NSArray *)args;
- (void)didReceiveMemoryWarning:(NSNotification *)notification;
- (void)contextShutdown:(id)sender;
- (id)toString:(id)args;

/**
 Whether or not the proxy was destroyed.
 @return _YES_ if destroyed, _NO_ otherwise.
 */
- (BOOL)destroyed;

/**
 Tells the proxy that it is in reproxying stage.
 @param yn _YES_ if the proxy is in reproxying stage, _NO_ otherwise.
 */
- (void)setReproxying:(BOOL)yn;

/**
 Whether or not the proxy is in reproxying stage.
 @return _YES_ if the proxy is in reproxying stage, _NO_ otherwise.
 */
- (BOOL)inReproxy;

#pragma Subclassable

/**
 Returns proxy that should receive the event next in a case of bubbling.
 Return nil if the class does not bubble or there is no parent. Optionally
 return nil if bubbleParent is false -- i.e., bubbleParent must be checked
 as well.
 
 Override this method for views that do not follow the standard children/parent
 model (e.g., table rows). Note that this is NOT for use by JS, because this is
 intentionally an iOS-only solution.
 */
- (TiProxy *)parentForBubbling;

/**
 Returns an array of properties that must be set on the proxy object in a specific order, ordered from first to last.
 Any properties which are not in this list are set after the listed properties, and are set in undefined order.
 
 Override this method if the order in which properties are set is significant.
 @return The array of property keys.
 */
- (NSArray *)keySequence;

#pragma JS - facing
/**
 Indicates that this proxy should honor bubbling of user events, if the proxy
 is the type that has a parent to bubble to (This is primairly views, but may
 have some exceptions).
 */
- (NSNumber *)bubbleParent;
- (void)setBubbleParent:(id)arg;

#pragma mark Utility
- (KrollObject *)krollObjectForContext:(KrollContext *)context;

- (BOOL)retainsJsObjectForKey:(NSString *)key;

//TODO: Find everywhere were we retain a proxy in a non-assignment way, and do remember/forget properly.

/**
 Tells the proxy to associate another proxy with it.
 
 The associated proxy will be retained.
 Note: rememberProxy/forgetProxy are not reference counted - multiple calls to <rememberProxy:> are all undone by a single call to <forgetProxy:> 
 @param rememberedProxy The proxy to remember.
 @see forgetProxy:
 */
- (void)rememberProxy:(TiProxy *)rememberedProxy;

/**
 Tells the proxy to disassociate another proxy from it.
 
 The deassociated proxy will be released.
 Note: rememberProxy/forgetProxy are not reference counted - multiple calls to <rememberProxy:> are all undone by a single call to <forgetProxy:> 
 @param forgottenProxy The proxy to forget.
 @see rememberProxy:
 */
- (void)forgetProxy:(TiProxy *)forgottenProxy;

//These are when, say, a window is opened, so you want to do JSValueProtect to make SURE it doesn't go away.

/**
 Tells the proxy to retain associated JS object.
 */
- (void)rememberSelf;

/**
 Tells the proxy to release associated JS object.
 */
- (void)forgetSelf;

//SetCallback is done internally by setValue:forUndefinedKey:
- (void)fireCallback:(NSString *)type withArg:(NSDictionary *)argDict withSource:(id)source;
- (void)fireCallback:(NSString *)type withArg:(NSDictionary *)argDict withSource:(id)source withHandler:(void (^)(id result))handler;

#pragma mark Public

/**
 Returns an enumeration of keys of all properties set on the proxy object.
 @return The enumeration of property keys.
 */
- (id<NSFastEnumeration>)allKeys;

+ (void)throwException:(NSString *)reason subreason:(NSString *)subreason location:(NSString *)location;
- (void)throwException:(NSString *)reason subreason:(NSString *)subreason location:(NSString *)location;
- (KrollPromise *)addEventListener:(NSArray *)args;
- (void)removeEventListener:(NSArray *)args;

- (void)fireEvent:(id)args;
- (void)fireEvent:(NSString *)type withObject:(id)obj;

//For UI events:
- (void)fireEvent:(NSString *)type withObject:(id)obj propagate:(BOOL)yn;

//For events that report an error or success
- (void)fireEvent:(NSString *)type withObject:(id)obj errorCode:(NSInteger)code message:(NSString *)message;

//What classes should actually override:
- (void)fireEvent:(NSString *)type withObject:(id)obj propagate:(BOOL)propagate reportSuccess:(BOOL)report errorCode:(NSInteger)code message:(NSString *)message;

//Temporary override point during the transition. Both the one below AND the one above should be overridden if needed.
- (void)fireEvent:(NSString *)type withObject:(id)obj withSource:(id)source propagate:(BOOL)propagate reportSuccess:(BOOL)report errorCode:(int)code message:(NSString *)message;

//** Deprecated: bubbling is done at a lower point so source is always 'self' at this point.
- (void)fireEvent:(NSString *)type withObject:(id)obj withSource:(id)source;
- (void)fireEvent:(NSString *)type withObject:(id)obj withSource:(id)source propagate:(BOOL)yn;

/**
 Returns a dictionary of all properties set on the proxy object.
 @return The dictionary containing all properties.
 */
- (NSDictionary *)allProperties;

/**
 Initializes a new property on the proxy object.
 @param name The property name.
 @param value The initial value to set on the property.
 */
- (void)initializeProperty:(NSString *)name defaultValue:(id)value;

/**
 Sets or replaces the property on the proxy object.
 @param value The new value.
 @param key The property key.
 @param notify The flag to send value change notification to model delegate.
 */
- (void)replaceValue:(id)value forKey:(NSString *)key notification:(BOOL)notify;

/**
 Removes the property on the proxy object.
 @param key The property key.
 */
- (void)deleteKey:(NSString *)key;

- (id)sanitizeURL:(id)value;

- (void)setExecutionContext:(id<TiEvaluator>)context;

+ (id)createProxy:(NSString *)qualifiedName withProperties:(NSDictionary *)properties inContext:(id<TiEvaluator>)context;

- (NSString *)apiName;

- (JSContext *)currentContext;

@end
