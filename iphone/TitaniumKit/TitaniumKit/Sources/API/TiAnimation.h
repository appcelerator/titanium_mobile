/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#import "LayoutConstraint.h"
#import "ListenerEntry.h"
#import "TiColor.h"
#import "TiPoint.h"
#import "TiProxy.h"
#import <QuartzCore/QuartzCore.h>

@class TiViewProxy;
@class TiAnimation;
@class KrollPromise;

/**
 Protocol for animation delegate.
 */
@protocol TiAnimationDelegate

@optional

/**
 Whether or not the animation should transition.
 
 The method is only called if the animation is a transition animation type.
 @param animation The animation this delegate is assigned to.
 @return _YES_ if the animation should transition, _NO_ otherwise.
 */
- (BOOL)animationShouldTransition:(TiAnimation *)animation;

/**
 Tells the delegate that the animation will start.
 @param animation The animation this delegate is assigned to.
 */
- (void)animationWillStart:(TiAnimation *)animation;

/**
 Tells the delegate that the animation did start.
 @param animation The animation this delegate is assigned to.
 */
- (void)animationDidStart:(TiAnimation *)animation;

/**
 Tells the delegate that the animation will complete.
 @param animation The animation this delegate is assigned to.
 */
- (void)animationWillComplete:(TiAnimation *)animation;

/**
 Tells the delegate that the animation did complete.
 @param animation The animation this delegate is assigned to.
 */
- (void)animationDidComplete:(TiAnimation *)animation;

@end

/**
 A type of proxy representing an animation to apply to a view. 
 */
@interface TiAnimation : TiProxy {
  @private
  NSNumber *zIndex;
  id left;
  id right;
  id top;
  id bottom;
  id width;
  id height;
  NSNumber *duration;
  TiPoint *center;
  TiColor *backgroundColor;
  TiColor *color;
  NSNumber *opacity;
  NSNumber *opaque;
  NSNumber *visible;
  NSNumber *curve;
  NSNumber *repeat;
  NSNumber *autoreverse;
  NSNumber *delay;
  TiProxy *transform;
  NSNumber *transition;
  TiViewProxy *view;
  TiViewProxy *animatedViewProxy;
  NSNumber *dampingRatio;
  NSNumber *springVelocity;

  NSDictionary *properties;

  // this is a temporary function passed in
  ListenerEntry *callback;
  KrollPromise *promise;

  NSObject<TiAnimationDelegate> *delegate;

  // for animation delegate
  UIView *animatedView;

  // for autoreverse
  TiAnimation *reverseAnimation;
  BOOL isReverse;
  BOOL resetState;
}

/**
 Provides access to animation delegate object.
 */
@property (nonatomic, assign, readwrite) NSObject<TiAnimationDelegate> *delegate;

@property (nonatomic, readwrite, assign) UIView *animatedView;
@property (nonatomic, readonly) ListenerEntry *callback;
@property (nonatomic, readonly) KrollPromise *promise;
@property (nonatomic, readwrite, assign) TiAnimation *reverseAnimation;
@property (nonatomic, readwrite, assign) BOOL isReverse;
@property (nonatomic, readwrite, assign) BOOL resetState;

// animatable properties against what is being animated
@property (nonatomic, retain, readwrite) NSNumber *zIndex;
@property (nonatomic, retain, readwrite) id left;
@property (nonatomic, retain, readwrite) id right;
@property (nonatomic, retain, readwrite) id top;
@property (nonatomic, retain, readwrite) id bottom;
@property (nonatomic, retain, readwrite) id width;
@property (nonatomic, retain, readwrite) id height;
@property (nonatomic, retain, readwrite) NSNumber *duration;
@property (nonatomic, retain, readwrite) TiPoint *center;
@property (nonatomic, retain, readwrite) TiColor *color;
@property (nonatomic, retain, readwrite) TiColor *backgroundColor;
@property (nonatomic, retain, readwrite) NSNumber *opacity;
@property (nonatomic, retain, readwrite) NSNumber *opaque;
@property (nonatomic, retain, readwrite) NSNumber *visible;

// properties that control the animation
@property (nonatomic, retain, readwrite) NSNumber *curve;
@property (nonatomic, retain, readwrite) NSNumber *repeat;
@property (nonatomic, retain, readwrite) NSNumber *autoreverse;
@property (nonatomic, retain, readwrite) NSNumber *delay;
@property (nonatomic, retain, readwrite) TiProxy *transform;
@property (nonatomic, retain, readwrite) NSNumber *transition;
@property (nonatomic, retain, readwrite) TiProxy *view;
@property (nonatomic, retain, readwrite) NSNumber *dampingRatio;
@property (nonatomic, retain, readwrite) NSNumber *springVelocity;

+ (TiAnimation *)animationFromArg:(id)args context:(id<TiEvaluator>)context create:(BOOL)yn promise:(KrollPromise *)promise;
+ (TiAnimation *)animationFromArg:(id)args context:(id<TiEvaluator>)context create:(BOOL)yn;

- (id)initWithDictionary:(NSDictionary *)properties context:(id<TiEvaluator>)context;

- (id)initWithDictionary:(NSDictionary *)properties context:(id<TiEvaluator>)context callback:(KrollCallback *)callback;

- (void)animate:(id)args;

/**
 Whether or not the animation is a transition animation type.
 @return _YES_ if the animation is a transition animation type, _NO_ otherwise.
 */
- (BOOL)isTransitionAnimation;

- (NSTimeInterval)animationDuration;
- (void)animationCompleted:(NSString *)animationID finished:(NSNumber *)finished context:(void *)context;

@end
