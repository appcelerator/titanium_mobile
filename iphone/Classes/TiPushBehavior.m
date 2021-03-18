/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UIIOSANIMATOR
#ifdef USE_TI_UIIOSPUSHBEHAVIOR
#import "TiPushBehavior.h"

@implementation TiPushBehavior

- (void)_initWithProperties:(NSDictionary *)properties
{
  _mode = UIPushBehaviorModeContinuous;
  _items = [[NSMutableArray alloc] init];
  _angle = 0;
  _magnitude = 0;
  _vector = CGVectorMake(0, 0);
  _active = YES;
  [super _initWithProperties:properties];
}

- (void)dealloc
{
  RELEASE_TO_NIL(_items);
  RELEASE_TO_NIL(_pushBehavior);
  [super dealloc];
}

- (NSString *)apiName
{
  return @"Ti.UI.iOS.PushBehavior";
}

#pragma mark - TiBehaviorProtocol
- (UIDynamicBehavior *)behaviorObject
{
  if (_needsRefresh) {
    RELEASE_TO_NIL(_pushBehavior);
  }
  if (_pushBehavior == nil) {
    NSMutableArray *viewItems = [[NSMutableArray alloc] initWithCapacity:_items.count];
    for (TiViewProxy *theArg in _items) {
      [viewItems addObject:[theArg view]];
    }
    _pushBehavior = [[UIPushBehavior alloc] initWithItems:viewItems mode:_mode];
    if (_vectorDefined) {
      [_pushBehavior setPushDirection:_vector];
    } else {
      [_pushBehavior setAngle:_angle];
      [_pushBehavior setMagnitude:_magnitude];
    }
    [_pushBehavior setActive:_active];
    [viewItems release];
    void (^update)(void) = ^{
      [self updateItems];
    };
    [_pushBehavior setAction:update];
  }
  _needsRefresh = NO;
  return _pushBehavior;
}

- (void)updateItems
{
  //Update params for reinitialization
  _angle = [_pushBehavior angle];
  _magnitude = [_pushBehavior magnitude];
  _vector = [_pushBehavior pushDirection];
}

- (void)updatePositioning
{
  for (TiViewProxy *theItem in _items) {
    CGPoint center = [[theItem view] center];
#ifndef TI_USE_AUTOLAYOUT
    LayoutConstraint *constraint = [theItem layoutProperties];
    constraint->centerX = TiDimensionDip(center.x);
    constraint->centerY = TiDimensionDip(center.y);
#endif
  }
}

#pragma mark - Public API
- (void)addItem:(id)args
{
  ENSURE_SINGLE_ARG(args, TiViewProxy);
  if (![_items containsObject:args]) {
    [self rememberProxy:args];
    [_items addObject:args];
    if (_pushBehavior != nil) {
      TiThreadPerformOnMainThread(
          ^{
            [_pushBehavior addItem:[(TiViewProxy *)args view]];
          },
          YES);
    }
  }
}

- (void)removeItem:(id)args
{
  ENSURE_SINGLE_ARG(args, TiViewProxy);
  if ([_items containsObject:args]) {
    if (_pushBehavior != nil) {
      TiThreadPerformOnMainThread(
          ^{
            [_pushBehavior removeItem:[(TiViewProxy *)args view]];
          },
          YES);
    }
    [_items removeObject:args];
    [self forgetProxy:args];
  }
}

- (NSArray *)items
{
  return [NSArray arrayWithArray:_items];
}

- (void)setAngle:(id)args
{
  ENSURE_SINGLE_ARG(args, NSNumber);
  _angle = [TiUtils floatValue:args def:0];
  if (_pushBehavior != nil) {
    TiThreadPerformOnMainThread(
        ^{
          [_pushBehavior setAngle:_angle];
        },
        YES);
  }
}

- (NSNumber *)angle
{
  if (_pushBehavior != nil) {
    TiThreadPerformOnMainThread(
        ^{
          _angle = [_pushBehavior angle];
        },
        YES);
  }
  return NUMFLOAT(_angle);
}

- (void)setMagnitude:(id)args
{
  ENSURE_SINGLE_ARG(args, NSNumber);
  _magnitude = [TiUtils floatValue:args def:0];
  if (_pushBehavior != nil) {
    TiThreadPerformOnMainThread(
        ^{
          [_pushBehavior setMagnitude:_magnitude];
        },
        YES);
  }
}

- (NSNumber *)magnitude
{
  if (_pushBehavior != nil) {
    TiThreadPerformOnMainThread(
        ^{
          _magnitude = [_pushBehavior magnitude];
        },
        YES);
  }
  return NUMFLOAT(_magnitude);
}

- (void)setPushDirection:(id)args
{
  ENSURE_SINGLE_ARG(args, NSObject);
  CGPoint newPoint = [TiUtils pointValue:args];
  _vectorDefined = YES;
  if (newPoint.x != _vector.dx || newPoint.y != _vector.dy) {
    _vector.dx = newPoint.x;
    _vector.dy = newPoint.y;
    if (_pushBehavior != nil) {
      TiThreadPerformOnMainThread(
          ^{
            [_pushBehavior setPushDirection:_vector];
          },
          YES);
    }
  }
}

- (NSDictionary *)pushDirection
{
  if (_pushBehavior != nil) {
    TiThreadPerformOnMainThread(
        ^{
          _vector = [_pushBehavior pushDirection];
        },
        YES);
  }
  return [TiUtils pointToDictionary:CGPointMake(_vector.dx, _vector.dy)];
}

- (void)setPushMode:(id)args
{
  ENSURE_SINGLE_ARG(args, NSNumber);
  int newVal = [TiUtils intValue:args def:0];
  if (newVal == 1) {
    _mode = UIPushBehaviorModeInstantaneous;
  } else {
    _mode = UIPushBehaviorModeContinuous;
  }
  _needsRefresh = (_pushBehavior != nil);
  if (_needsRefresh) {
    TiThreadPerformOnMainThread(
        ^{
          UIDynamicAnimator *theAnimator = _pushBehavior.dynamicAnimator;
          if (theAnimator != nil) {
            [theAnimator removeBehavior:_pushBehavior];
            [theAnimator addBehavior:[self behaviorObject]];
          }
        },
        YES);
  }
}

- (NSNumber *)pushMode
{
  return @(_mode);
}

- (void)setActive:(id)args
{
  ENSURE_SINGLE_ARG(args, NSNumber);
  _active = [TiUtils boolValue:args def:YES];
  if (_pushBehavior != nil) {
    TiThreadPerformOnMainThread(
        ^{
          [_pushBehavior setActive:_active];
        },
        YES);
  }
}

- (NSNumber *)active
{
  return NUMBOOL(_active);
}

@end
#endif
#endif
