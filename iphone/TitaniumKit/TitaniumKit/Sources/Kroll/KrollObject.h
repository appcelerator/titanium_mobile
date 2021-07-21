/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#import <Foundation/Foundation.h>
#import <JavaScriptCore/JavaScriptCore.h>

@class KrollContext, KrollCallback, KrollPromise, TiProxy;
extern JSClassRef KrollObjectClassRef;
extern JSStringRef kTiStringExportsKey;

void KrollFinalizer(JSObjectRef ref);
void KrollInitializer(JSContextRef ctx, JSObjectRef object);
JSValueRef KrollGetProperty(JSContextRef jsContext, JSObjectRef obj, JSStringRef prop, JSValueRef *exception);
bool KrollSetProperty(JSContextRef jsContext, JSObjectRef obj, JSStringRef prop, JSValueRef value, JSValueRef *exception);
bool KrollDeleteProperty(JSContextRef ctx, JSObjectRef object, JSStringRef propertyName, JSValueRef *exception);

//
// KrollObject is a generic native wrapper around a native object exposed as a JS object
// in JS land.
//

@class KrollBridge;
@interface KrollObject : NSObject {
  @private
  NSMutableDictionary *properties;
  NSMutableDictionary *statics;
  NSMutableDictionary *promises;
  JSObjectRef _jsobject;
  BOOL targetable;
  BOOL finalized;
  BOOL protecting;
  @protected
  id target;
  KrollContext *context;
  JSContextRef jsContext;
  KrollBridge *bridge; //Used only in finalizing for sake of safe lookup.
}
@property (nonatomic, assign) BOOL finalized;
@property (nonatomic, readonly) KrollBridge *bridge;
@property (nonatomic, readonly) KrollContext *context;
@property (nonatomic, readonly) JSContextRef jsContext;

- (id)initWithTarget:(id)target_ context:(KrollContext *)context_;

+ (JSValueRef)create:(id)object context:(KrollContext *)context_;
+ (id)toID:(KrollContext *)context value:(JSValueRef)ref;
+ (JSValueRef)toValue:(KrollContext *)context value:(id)obj;
+ (id)nonNull:(id)value;
+ (BOOL)isFinalizing;

/**
 Checks if a property with the given name exists on our target.
 
 Contains all the magic of valueForKey withouth trying to retrieve any actual
 value.
 
 The checks for property existance are done in the following order:
 * The Kroll object's own statics and properties cache
 * Dynamic getter and setter in the form of getSomeProperty or setSomeProperty
 * Property on the actual target
 * "toString" and "valueOf" are always available on all objects
 * "className" has a special handling with valueForUndefinedKey, return true
 for the sake of simplicity
 * Method with the same name on the target and single parameter
 * Method with the same name on the target and no parameter
 * Create factory method
 
 As soon as one of the above checks passes this method returns true, meaning
 the property exists. If none of the checks passed the property does not exists
 and the method returns false.
 
 @param propertyName The property name to check for.
 */
- (BOOL)hasProperty:(NSString *)propertyName;
- (id)valueForKey:(NSString *)key;
- (void)deleteKey:(NSString *)key;
- (void)setValue:(id)value forKey:(NSString *)key;
- (void)setStaticValue:(id)value forKey:(NSString *)key purgable:(BOOL)purgable;
- (id)target;

//TODO: Lots of copypasted code in these methods could be refactored out.
@property (nonatomic, assign) JSObjectRef propsObject;
- (JSObjectRef)jsobject;
- (JSValueRef)jsvalueForUndefinedKey:(NSString *)key;

- (void)noteKeylessKrollObject:(KrollObject *)value;
- (void)forgetKeylessKrollObject:(KrollObject *)value;
- (void)protectJsobject;
- (void)unprotectJsobject;

- (void)noteKrollObject:(KrollObject *)value forKey:(NSString *)key;
- (void)forgetKrollObjectforKey:(NSString *)key;
- (void)noteObject:(JSObjectRef)storedJSObject forTiString:(JSStringRef)keyString context:(JSContextRef)jsxContext;
- (void)forgetObjectForTiString:(JSStringRef)keyString context:(JSContextRef)jsContext;
- (JSObjectRef)objectForTiString:(JSStringRef)keyString context:(JSContextRef)jsContext;

- (void)noteCallback:(KrollCallback *)eventCallback forKey:(NSString *)key;
- (void)forgetCallbackForKey:(NSString *)key;
- (void)invokeCallbackForKey:(NSString *)key withObject:(NSDictionary *)eventData thisObject:(KrollObject *)thisObject;
- (void)invokeCallbackForKey:(NSString *)key withObject:(NSDictionary *)eventData thisObject:(KrollObject *)thisObject onDone:(void (^)(id result))block;

- (JSObjectRef)callbacksForEvent:(JSStringRef)jsEventTypeString;
- (NSMutableArray<KrollPromise *> *)promisesForEvent:(NSString *)eventName;
- (void)resolvePromisesForEvent:(NSString *)eventName withObject:(id)eventData;
- (void)storeListener:(id)eventCallbackOrWrapper forEvent:(NSString *)eventName;
- (void)removeListener:(KrollCallback *)eventCallback forEvent:(NSString *)eventName;
- (void)triggerEvent:(NSString *)eventName withObject:(NSDictionary *)eventData thisObject:(KrollObject *)thisObject;

- (void)applyGarbageCollectionSafeguard;
- (void)removeGarbageCollectionSafeguard;

@end
