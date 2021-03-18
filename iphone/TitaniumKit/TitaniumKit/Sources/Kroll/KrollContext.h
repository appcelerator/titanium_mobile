/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2018 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#import <Foundation/Foundation.h>
#import <JavaScriptCore/JavaScriptCore.h>

@class KrollContext;
@class KrollCallback;
@class KrollTimerManager;

@protocol KrollDelegate <NSObject>

@optional
- (id)require:(KrollContext *)kroll path:(NSString *)path;
@required
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
  JSGlobalContextRef context;
  KrollTimerManager *timerManager;
}

@property (nonatomic, readwrite, assign) id<KrollDelegate> delegate;

- (void)start;
- (void)stop;
- (BOOL)running;
- (void)gc;
- (JSGlobalContextRef)context;
- (BOOL)isKJSThread;

- (void)invokeOnThread:(id)callback_ method:(SEL)method_ withObject:(id)obj condition:(NSCondition *)condition_;
- (void)invokeOnThread:(id)callback_ method:(SEL)method_ withObject:(id)obj callback:(id)callback selector:(SEL)selector_;
- (void)invokeBlockOnThread:(void (^)(void))block;
+ (void)invokeBlock:(void (^)(void))block;

- (void)evalJS:(NSString *)code;
- (id)evalJSAndWait:(NSString *)code;

- (void)enqueue:(id)obj;

- (int)forceGarbageCollectNow;

@end

//====================================================================================================================

@interface KrollUnprotectOperation : NSOperation {
  JSContextRef jsContext;
  JSObjectRef firstObject;
  JSObjectRef secondObject;
}

- (id)initWithContext:(JSContextRef)newContext withJsobject:(JSObjectRef)newFirst;
- (id)initWithContext:(JSContextRef)newContext withJsobject:(JSObjectRef)newFirst andJsobject:(JSObjectRef)newSecond;

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
- (JSValueRef)jsInvokeInContext:(KrollContext *)context exception:(JSValueRef *)exceptionPointer;
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

KrollContext *GetKrollContext(JSContextRef context);

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
