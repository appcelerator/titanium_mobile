/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#import "TiToJS.h"
#import <Foundation/Foundation.h>

@class KrollContext;
@class KrollCallback;

@protocol KrollDelegate <NSObject>

@required
- (id)require:(KrollContext *)kroll path:(NSString *)path;
- (BOOL)shouldDebugContext;
- (BOOL)shouldProfileContext;
@optional

- (void)willStartNewContext:(KrollContext *)kroll;
- (void)didStartNewContext:(KrollContext *)kroll;
- (void)willStopNewContext:(KrollContext *)kroll;
- (void)didStopNewContext:(KrollContext *)kroll;

@end

@interface KrollContext : NSObject {
  @private
  id<KrollDelegate> delegate;
  BOOL stopped;

  //Garbage collection variables.
  BOOL gcrequest;
  unsigned int loopCount;

  BOOL destroyed;
#ifndef __clang_analyzer__
  BOOL suspended;
#endif
  TiGlobalContextRef context;
  NSMutableDictionary *timers;
  NSRecursiveLock *timerLock;
  void *debugger;

#ifdef TI_USE_KROLL_THREAD
  NSString *krollContextId;
  NSRecursiveLock *lock;
  NSCondition *condition;
  NSMutableArray *queue;
  id cachedThreadId;
#endif
}

@property (nonatomic, readwrite, assign) id<KrollDelegate> delegate;

- (void)start;
- (void)stop;
- (BOOL)running;
- (void)gc;
- (TiGlobalContextRef)context;
- (void *)debugger;
- (BOOL)isKJSThread;

#ifdef DEBUG
// used during debugging only
#ifdef TI_USE_KROLL_THREAD
- (NSUInteger)queueCount;
#endif
#endif

- (void)invokeOnThread:(id)callback_ method:(SEL)method_ withObject:(id)obj condition:(NSCondition *)condition_;
- (void)invokeOnThread:(id)callback_ method:(SEL)method_ withObject:(id)obj callback:(id)callback selector:(SEL)selector_;
- (void)invokeBlockOnThread:(void (^)())block;
+ (void)invokeBlock:(void (^)())block;

- (void)evalJS:(NSString *)code;
- (id)evalJSAndWait:(NSString *)code;

- (void)enqueue:(id)obj;

#ifndef USE_JSCORE_FRAMEWORK
- (void)registerTimer:(id)timer timerId:(double)timerId;
- (void)unregisterTimer:(double)timerId;
#endif

- (int)forceGarbageCollectNow;
#ifdef TI_USE_KROLL_THREAD
- (NSString *)krollContextId;
- (NSString *)threadName;
#endif
@end

//====================================================================================================================

@interface KrollUnprotectOperation : NSOperation {
  TiContextRef jsContext;
  TiObjectRef firstObject;
  TiObjectRef secondObject;
}

- (id)initWithContext:(TiContextRef)newContext withJsobject:(TiObjectRef)newFirst;
- (id)initWithContext:(TiContextRef)newContext withJsobject:(TiObjectRef)newFirst andJsobject:(TiObjectRef)newSecond;

@end

@interface KrollInvocation : NSObject {
  @private
  id target;
  SEL method;
  id obj;
  NSCondition *condition;
  id notify;
  SEL notifySelector;
}
- (id)initWithTarget:(id)target_ method:(SEL)method_ withObject:(id)obj_ condition:(NSCondition *)condition_;
- (id)initWithTarget:(id)target_ method:(SEL)method_ withObject:(id)obj_ callback:(id)callback_ selector:(SEL)selector_;
- (void)invoke:(KrollContext *)context;
@end

@interface KrollEval : NSObject {
  @private
  NSString *code;
  NSURL *sourceURL;
  NSInteger startingLineNo;
}
- (id)initWithCode:(NSString *)code;
- (id)initWithCode:(NSString *)code sourceURL:(NSURL *)sourceURL;
- (id)initWithCode:(NSString *)code sourceURL:(NSURL *)sourceURL startingLineNo:(NSInteger)startingLineNo;
- (TiValueRef)jsInvokeInContext:(KrollContext *)context exception:(TiValueRef *)exceptionPointer;
- (void)invoke:(KrollContext *)context;
- (id)invokeWithResult:(KrollContext *)context;
@end

@class KrollObject;
@interface KrollEvent : NSObject {
  @private
  KrollCallback *callback;

  NSString *type;
  KrollObject *callbackObject;

  NSDictionary *eventObject;
  id thisObject;
}
- (id)initWithType:(NSString *)newType ForKrollObject:(KrollObject *)newCallbackObject eventObject:(NSDictionary *)newEventObject thisObject:(id)newThisObject;
- (id)initWithCallback:(KrollCallback *)newCallback eventObject:(NSDictionary *)newEventObject thisObject:(id)newThisObject;
- (void)invoke:(KrollContext *)context;
@end

@protocol KrollTargetable
@required
- (void)setExecutionContext:(id<KrollDelegate>)delegate;
@end

KrollContext *GetKrollContext(TiContextRef context);

//TODO: After 1.7, move to individual file and convert KrollInvocation and Callbacks to ExpandedInvocationOperation.
@interface ExpandedInvocationOperation : NSOperation {
  @private
  id invocationTarget;
  SEL invocationSelector;
  id invocationArg1;
  id invocationArg2;
  id invocationArg3;
  id invocationArg4;
}
- (id)initWithTarget:(id)target selector:(SEL)sel object:(id)arg1 object:(id)arg2;
- (id)initWithTarget:(id)target selector:(SEL)sel object:(id)arg1 object:(id)arg2 object:(id)arg3;
- (id)initWithTarget:(id)target selector:(SEL)sel object:(id)arg1 object:(id)arg2 object:(id)arg3 object:(id)arg4;

@property (nonatomic, readwrite, retain) id invocationTarget;
@property (nonatomic, readwrite, assign) SEL invocationSelector;
@property (nonatomic, readwrite, retain) id invocationArg1;
@property (nonatomic, readwrite, retain) id invocationArg2;
@property (nonatomic, readwrite, retain) id invocationArg3;
@property (nonatomic, readwrite, retain) id invocationArg4;

@end

#ifdef USE_JSCORE_FRAMEWORK

/**
 * Handles creating and clearing timers when running with JavaScriptCore
 */
@interface JSTimerManager : NSObject

/**
 * Initailizes the timer manager in the given JS context. Exposes the global set/clear
 * functions for creating and clearing intervals/timeouts.
 *
 * @param context The JSContext where timer function should be made available to.
 */
+ (void)initializeInContext:(JSContext *)context;

/**
 * Map of timer identifiers and the underlying native NSTimer.
 */
+ (NSMutableDictionary<NSNumber *, NSTimer *> *)timers;

@end

#endif
