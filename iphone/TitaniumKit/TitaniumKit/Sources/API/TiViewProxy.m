/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#import "TiViewProxy.h"
#import "LayoutConstraint.h"
#import "TiApp.h"
#import "TiBlob.h"
#import "TiLayoutQueue.h"
#import "TiLocale.h"
#import "TiStylesheet.h"
#import "TiUIView.h"
#import "TiUIViewProxy.h"
#import "TiUIWindowProxy.h"
#import "TiWindowProxy.h"
#import <QuartzCore/QuartzCore.h>
#import <libkern/OSAtomic.h>
#import <pthread.h>

#define IGNORE_IF_NOT_OPENED                 \
  if (!windowOpened || ![self viewAttached]) \
    return;

static NSArray *touchEventsArray;

@implementation TiViewProxy

@synthesize eventOverrideDelegate = eventOverrideDelegate;

#pragma mark public API

@synthesize vzIndex, parentVisible;
- (void)setVzIndex:(int)newZindex
{
  if (newZindex == vzIndex) {
    return;
  }

  vzIndex = newZindex;
  [self replaceValue:NUMINT(vzIndex) forKey:@"vzIndex" notification:NO];
  [self willChangeZIndex];
}

@synthesize children;
- (NSArray *)children
{
  if (![NSThread isMainThread]) {
    __block NSArray *result = nil;
    TiThreadPerformOnMainThread(
        ^{
          result = [[self children] retain];
        },
        YES);
    return [result autorelease];
  }

  pthread_rwlock_rdlock(&childrenLock);
  NSArray *copy = [children mutableCopy];
  pthread_rwlock_unlock(&childrenLock);
  return ((copy != nil) ? [copy autorelease] : [NSMutableArray array]);
}

- (NSString *)apiName
{
  return @"Ti.View";
}

- (void)setVisible:(NSNumber *)newVisible withObject:(id)args
{
  [self setHidden:![TiUtils boolValue:newVisible def:YES] withArgs:args];
  [self replaceValue:newVisible forKey:@"visible" notification:YES];
}

- (void)setTempProperty:(id)propVal forKey:(id)propName
{
  if (layoutPropDictionary == nil) {
    layoutPropDictionary = [[NSMutableDictionary alloc] init];
  }

  if (propVal != nil && propName != nil) {
    [layoutPropDictionary setObject:propVal forKey:propName];
  }
}

- (void)setProxyObserver:(id)arg
{
  observer = arg;
}

- (void)processTempProperties:(NSDictionary *)arg
{
  // arg will be typically be nil (was non-nil when called from removed updateLayout() method)
  if (arg != nil) {
    NSEnumerator *enumerator = [arg keyEnumerator];
    id key;
    while ((key = [enumerator nextObject])) {
      [self setTempProperty:[arg objectForKey:key] forKey:key];
    }
  }

  if (layoutPropDictionary != nil) {
    [self setValuesForKeysWithDictionary:layoutPropDictionary];
    RELEASE_TO_NIL(layoutPropDictionary);
  }
}

#ifdef TI_USE_AUTOLAYOUT
- (void)setOnLayout:(id)callback
{
  ENSURE_SINGLE_ARG(callback, KrollCallback);
  TiLayoutView *thisView = [self view];
  if ([thisView onLayout] == nil) {
    KrollCallback *__block _callback = [callback retain];
    [thisView setOnLayout:^(TiLayoutView *sender, CGRect rect) {
      [sender setOnLayout:nil];
      KrollEvent *invocationEvent = [[KrollEvent alloc] initWithCallback:callback
                                                             eventObject:[TiUtils rectToDictionary:rect]
                                                              thisObject:self];
      [[_callback context] enqueue:invocationEvent];
      RELEASE_TO_NIL(invocationEvent);
      RELEASE_TO_NIL(_callback);
    }];
  } else {
    NSLog(@"[ERROR] onLayout can only be set once!");
  }
}
#endif

- (BOOL)belongsToContext:(id<TiEvaluator>)context
{
  id<TiEvaluator> myContext = ([self executionContext] == nil) ? [self pageContext] : [self executionContext];
  return (context == myContext);
}

- (void)add:(id)arg
{
  TiUIWindowProxy *windowProxy = nil;
  if ([self isKindOfClass:[TiUIWindowProxy class]]) {
    windowProxy = (TiUIWindowProxy *)self;
    if (arg == windowProxy.safeAreaViewProxy) {
      // If adding the safeAreaViewProxy, it need to be add on window.
      windowProxy = nil;
    }
  }

  // allow either an array of arrays or an array of single proxy
  if ([arg isKindOfClass:[NSArray class]]) {
    for (id a in arg) {
      if (windowProxy.safeAreaViewProxy) {
        [windowProxy.safeAreaViewProxy add:a];
      } else {
        [self add:a];
      }
    }
    return;
  }

  int position = -1;
  TiViewProxy *childView = nil;

  static SEL nativeObjSel = nil;
  if (TiUtils.isHyperloopAvailable) {
    // obfuscate this selector
    if (nativeObjSel == nil) {
      nativeObjSel = NSSelectorFromString([NSString stringWithFormat:@"%@%@", @"native", @"Object"]);
    }
  }

  if ([arg isKindOfClass:[NSDictionary class]]) {
    if (windowProxy.safeAreaViewProxy) {
      [windowProxy.safeAreaViewProxy add:arg];
      return;
    }
    childView = [arg objectForKey:@"view"];
    position = [TiUtils intValue:[arg objectForKey:@"position"] def:-1];
  } else if ([arg isKindOfClass:[TiViewProxy class]]) {
    childView = arg;
  } else if (TiUtils.isHyperloopAvailable && ([arg isKindOfClass:[UIView class]] || [arg respondsToSelector:nativeObjSel])) {
    Class hyperloopViewProxy = NSClassFromString(@"HyperloopViewProxy");
    if (hyperloopViewProxy != nil) {
      childView = [(TiViewProxy *)[[hyperloopViewProxy alloc] _initWithPageContext:[self executionContext]] autorelease];
      [childView performSelector:@selector(setNativeView:) withObject:arg];
    }
  }

  if (childView == nil) {
    DeveloperLog(@"[WARN] 'add' and 'insertAt' must be contain a view. Returning");
    return;
  }

  if ([childView conformsToProtocol:@protocol(TiWindowProtocol)]) {
    DebugLog(@"[WARN] Can not add a window as a child of a view. Returning");
    return;
  }

  if (children == nil) {
    children = [[NSMutableArray alloc] init];
  }

  [self rememberProxy:childView];
  // Lock the assignment of the position to prevent race-conditions
  pthread_rwlock_wrlock(&childrenLock);
  if (position < 0 || position > [children count]) {
    position = (int)[children count];
  }
  [children insertObject:childView atIndex:position];
  pthread_rwlock_unlock(&childrenLock);

  [childView setParent:self];

  if (windowOpened) {
    // Turn on clipping because I have children
    [[self view] updateClipping];
    [self contentsWillChange];

    if (parentVisible && !hidden) {
      [childView parentWillShow];
    }

#ifndef TI_USE_AUTOLAYOUT
    // If layout is non absolute push this into the layout queue
    // else just layout the child with current bounds
    if (!TiLayoutRuleIsAbsolute(layoutProperties.layoutStyle)) {
      [self contentsWillChange];
    } else
#endif
    {
      [self layoutChild:childView optimize:NO withMeasuredBounds:[[self view] bounds]];
    }
  }
}

- (void)insertAt:(id)args
{
  ENSURE_SINGLE_ARG(args, NSDictionary);
  [self add:args];
}

- (void)replaceAt:(id)args
{
  if ([self isKindOfClass:[TiUIWindowProxy class]]) {
    TiUIWindowProxy *windowProxy = (TiUIWindowProxy *)self;
    if (windowProxy.safeAreaViewProxy) {
      [windowProxy.safeAreaViewProxy replaceAt:args];
      return;
    }
  }

  ENSURE_SINGLE_ARG(args, NSDictionary);
  NSInteger position = [TiUtils intValue:[args objectForKey:@"position"] def:-1];
  NSArray *childrenArray = [self children];
  if (childrenArray != nil && position > -1 && [childrenArray count] > position) {
    TiViewProxy *childToRemove = [[childrenArray objectAtIndex:position] retain];
    [self add:args];
    [self remove:childToRemove];
    [childToRemove autorelease];
  }
}

- (void)remove:(id)arg
{
  if ([self isKindOfClass:[TiUIWindowProxy class]]) {
    TiUIWindowProxy *windowProxy = (TiUIWindowProxy *)self;
    if (windowProxy.safeAreaViewProxy) {
      [windowProxy.safeAreaViewProxy remove:arg];
      return;
    }
  }

  ENSURE_UI_THREAD_1_ARG(arg);

  if (TiUtils.isHyperloopAvailable) {
    // ofbuscate this selector
    static SEL nativeObjSel = nil;
    if (nativeObjSel == nil)
      nativeObjSel = NSSelectorFromString([NSString stringWithFormat:@"%@%@", @"native", @"Object"]);
    // obfuscate this class name
    static Class hlClassName = nil;
    if (hlClassName == nil)
      hlClassName = NSClassFromString([NSString stringWithFormat:@"%@%@", @"Hyperloop", @"Class"]);
    if ([arg isKindOfClass:[NSArray class]]) {
      for (id each in arg) {
        [self remove:each];
      }
      return;
    }
    if ([arg isKindOfClass:[UIView class]] || (hlClassName != nil && [arg isKindOfClass:hlClassName])) {
      TiUIView *tmpView;
      if ([arg isKindOfClass:hlClassName]) {
        UIView *v = [arg performSelector:nativeObjSel];
        if (![v isKindOfClass:[UIView class]]) {
          NSLog(@"[WARN] Trying to remove an object that is not a view");
          return;
        }
        tmpView = (TiUIView *)[v superview];
      } else {
        tmpView = (TiUIView *)[(UIView *)arg superview];
      }
      if (tmpView != nil && [tmpView isKindOfClass:[TiUIView class]]) {
        arg = [tmpView proxy];
      } else {
        NSLog(@"[WARN] Trying to remove a view that was never added or has already been removed");
        return;
      }
    }
  }

  ENSURE_SINGLE_ARG(arg, TiViewProxy);

  pthread_rwlock_wrlock(&childrenLock);
  NSMutableArray *childrenCopy = [children mutableCopy];
  if ([children containsObject:arg]) {
    [children removeObject:arg];
  }
  pthread_rwlock_unlock(&childrenLock);

  if ([childrenCopy containsObject:arg]) {
    [arg windowWillClose];
    [arg setParentVisible:NO];
    [arg setParent:nil];
    [arg windowDidClose];
    [self forgetProxy:arg];
    [childrenCopy removeObject:arg];
    [self contentsWillChange];
  }
  [childrenCopy release];
}

- (void)removeAllChildren:(id)arg
{
  if ([self isKindOfClass:[TiUIWindowProxy class]]) {
    TiUIWindowProxy *windowProxy = (TiUIWindowProxy *)self;
    if (windowProxy.safeAreaViewProxy) {
      [windowProxy.safeAreaViewProxy removeAllChildren:arg];
      return;
    }
  }

  ENSURE_UI_THREAD_1_ARG(arg);
  pthread_rwlock_wrlock(&childrenLock);
  NSMutableArray *childrenCopy = [children mutableCopy];
  [children removeAllObjects];
  RELEASE_TO_NIL(children);
  pthread_rwlock_unlock(&childrenLock);
  for (TiViewProxy *theChild in childrenCopy) {
    [theChild windowWillClose];
    [theChild setParentVisible:NO];
    [theChild setParent:nil];
    [theChild windowDidClose];
    [self forgetProxy:theChild];
  }
  [childrenCopy removeAllObjects];
  [childrenCopy release];
  [self contentsWillChange];
}

- (void)show:(id)arg
{
  TiThreadPerformOnMainThread(
      ^{
        [self setHidden:NO withArgs:arg];
        [self replaceValue:NUMBOOL(YES) forKey:@"visible" notification:YES];
      },
      NO);
}

- (void)hide:(id)arg
{
  TiThreadPerformOnMainThread(
      ^{
        [self setHidden:YES withArgs:arg];
        [self replaceValue:NUMBOOL(NO) forKey:@"visible" notification:YES];
      },
      NO);
}

- (void)clearMotionEffects:(id)unused
{
  [self.view.motionEffects enumerateObjectsUsingBlock:^(__kindof UIMotionEffect *_Nonnull obj, NSUInteger idx, BOOL *_Nonnull stop) {
    [self.view removeMotionEffect:obj];
  }];
}

- (id)getViewById:(id)arg
{
  ENSURE_SINGLE_ARG(arg, NSString);

  for (TiViewProxy *child in [self children]) {
    if ([[child children] count] > 0) {
      TiViewProxy *parentChild = [child getViewById:arg];
      if (parentChild) {
        return parentChild;
      }
    }

    if ([[child valueForKey:@"id"] isEqualToString:arg]) {
      return child;
    }
  }

  return nil;
}

- (void)animate:(id)arg
{
  TiAnimation *newAnimation = [TiAnimation animationFromArg:arg context:[self executionContext] create:NO];
  [self rememberProxy:newAnimation];
  TiThreadPerformOnMainThread(
      ^{
        if ([view superview] == nil) {
          VerboseLog(@"Entering animation without a superview Parent is %@, props are %@", parent, dynprops);
          [parent childWillResize:self];
        }
        [self windowWillOpen]; // we need to manually attach the window if you're animating
        [parent layoutChildrenIfNeeded];
        [[self view] animate:newAnimation];
      },
      NO);
}

- (void)setAnimation:(id)arg
{ //We don't actually store the animation this way.
  //Because the setter doesn't have the argument array, we will be passing a nonarray to animate:
  //In this RARE case, this is okay, because TiAnimation animationFromArg handles with or without array.
  [self animate:arg];
}

#ifndef TI_USE_AUTOLAYOUT

#define CHECK_LAYOUT_UPDATE(layoutName, value)           \
  if (ENFORCE_BATCH_UPDATE) {                            \
    if (updateStarted) {                                 \
      [self setTempProperty:value forKey:@ #layoutName]; \
      return;                                            \
    } else if (!allowLayoutUpdate) {                     \
      return;                                            \
    }                                                    \
  }

#define LAYOUTPROPERTIES_SETTER_IGNORES_AUTO(methodName, layoutName, converter, postaction)                         \
  -(void)methodName : (id)value                                                                                     \
  {                                                                                                                 \
    CHECK_LAYOUT_UPDATE(layoutName, value)                                                                          \
    TiDimension result = converter(value);                                                                          \
    if (TiDimensionIsDip(result) || TiDimensionIsPercent(result)) {                                                 \
      layoutProperties.layoutName = result;                                                                         \
    } else {                                                                                                        \
      if (!TiDimensionIsUndefined(result)) {                                                                        \
        DebugLog(@"[WARN] Invalid value %@ specified for property %@", [TiUtils stringValue:value], @ #layoutName); \
      }                                                                                                             \
      layoutProperties.layoutName = TiDimensionUndefined;                                                           \
    }                                                                                                               \
    [self replaceValue:value forKey:@ #layoutName notification:YES];                                                \
    postaction;                                                                                                     \
  }

#define LAYOUTPROPERTIES_SETTER(methodName, layoutName, converter, postaction) \
  -(void)methodName : (id)value                                                \
  {                                                                            \
    CHECK_LAYOUT_UPDATE(layoutName, value)                                     \
    layoutProperties.layoutName = converter(value);                            \
    [self replaceValue:value forKey:@ #layoutName notification:YES];           \
    postaction;                                                                \
  }

#define LAYOUTFLAGS_SETTER(methodName, layoutName, flagName, postaction) \
  -(void)methodName : (id)value                                          \
  {                                                                      \
    CHECK_LAYOUT_UPDATE(layoutName, value)                               \
    layoutProperties.layoutFlags.flagName = [TiUtils boolValue:value];   \
    [self replaceValue:value forKey:@ #layoutName notification:NO];      \
    postaction;                                                          \
  }

LAYOUTPROPERTIES_SETTER_IGNORES_AUTO(setTop, top, TiDimensionFromObject, [self willChangePosition])
LAYOUTPROPERTIES_SETTER_IGNORES_AUTO(setBottom, bottom, TiDimensionFromObject, [self willChangePosition])

LAYOUTPROPERTIES_SETTER_IGNORES_AUTO(setLeft, left, TiDimensionFromObject, [self willChangePosition])
LAYOUTPROPERTIES_SETTER_IGNORES_AUTO(setRight, right, TiDimensionFromObject, [self willChangePosition])

LAYOUTPROPERTIES_SETTER(setWidth, width, TiDimensionFromObject, [self willChangeSize])
LAYOUTPROPERTIES_SETTER(setHeight, height, TiDimensionFromObject, [self willChangeSize])

// See below for how we handle setLayout
//LAYOUTPROPERTIES_SETTER(setLayout,layoutStyle,TiLayoutRuleFromObject,[self willChangeLayout])

LAYOUTPROPERTIES_SETTER(setMinWidth, minimumWidth, TiFixedValueRuleFromObject, [self willChangeSize])
LAYOUTPROPERTIES_SETTER(setMinHeight, minimumHeight, TiFixedValueRuleFromObject, [self willChangeSize])

LAYOUTFLAGS_SETTER(setHorizontalWrap, horizontalWrap, horizontalWrap, [self willChangeLayout])

// Special handling to try and avoid Apple's detection of private API 'layout'
- (void)setValue:(id)value forUndefinedKey:(NSString *)key
{
  if ([key isEqualToString:[@"lay" stringByAppendingString:@"out"]]) {
    //CAN NOT USE THE MACRO
    if (ENFORCE_BATCH_UPDATE) {
      if (updateStarted) {
        [self setTempProperty:value forKey:key];
        return;
      } else if (!allowLayoutUpdate) {
        return;
      }
    }
    layoutProperties.layoutStyle = TiLayoutRuleFromObject(value);
    [self replaceValue:value forKey:[@"lay" stringByAppendingString:@"out"] notification:YES];

    [self willChangeLayout];
    return;
  }
  [super setValue:value forUndefinedKey:key];
}

#endif

- (TiRect *)size
{
  TiRect *rect = [[TiRect alloc] init];
  if ([self viewAttached]) {
    [self makeViewPerformSelector:@selector(fillBoundsToRect:) withObject:rect createIfNeeded:YES waitUntilDone:YES];
    id defaultUnit = [[TiApp tiAppProperties] objectForKey:@"ti.ui.defaultunit"];
    if ([defaultUnit isKindOfClass:[NSString class]]) {
      [rect convertToUnit:defaultUnit];
    }
  } else {
    [rect setRect:CGRectZero];
  }

  return [rect autorelease];
}

- (TiRect *)rect
{
  TiRect *rect = [[TiRect alloc] init];
  if ([self viewAttached]) {
    __block CGRect viewRect;
    __block CGPoint viewPosition;
    __block CGAffineTransform viewTransform;
    __block CGPoint viewAnchor;
    TiThreadPerformOnMainThread(
        ^{
          TiUIView *ourView = [self view];
          viewRect = [ourView bounds];
          viewPosition = [ourView center];
          viewTransform = [ourView transform];
          viewAnchor = [[ourView layer] anchorPoint];
        },
        YES);
    viewRect.origin = CGPointMake(-viewAnchor.x * viewRect.size.width, -viewAnchor.y * viewRect.size.height);
    viewRect = CGRectApplyAffineTransform(viewRect, viewTransform);
    viewRect.origin.x += viewPosition.x;
    viewRect.origin.y += viewPosition.y;
    [rect setRect:viewRect];

    id defaultUnit = [[TiApp tiAppProperties] objectForKey:@"ti.ui.defaultunit"];
    if ([defaultUnit isKindOfClass:[NSString class]]) {
      [rect convertToUnit:defaultUnit];
    }
  } else {
    [rect setRect:CGRectZero];
  }
  return [rect autorelease];
}

- (id)zIndex
{
  return [self valueForUndefinedKey:@"zindex_"];
}

- (void)setZIndex:(id)value
{
#ifndef TI_USE_AUTOLAYOUT
  CHECK_LAYOUT_UPDATE(zIndex, value);
#endif
  if ([value respondsToSelector:@selector(intValue)]) {
    [self setVzIndex:[TiUtils intValue:value]];
    [self replaceValue:value forKey:@"zindex_" notification:NO];
  }
}

- (NSMutableDictionary *)center
{
  NSMutableDictionary *result = [[[NSMutableDictionary alloc] init] autorelease];
  id xVal = [self valueForUndefinedKey:@"centerX_"];
  if (xVal != nil) {
    [result setObject:xVal forKey:@"x"];
  }
  id yVal = [self valueForUndefinedKey:@"centerY_"];
  if (yVal != nil) {
    [result setObject:yVal forKey:@"y"];
  }

  if ([[result allKeys] count] > 0) {
    return result;
  }
  return nil;
}

#ifndef TI_USE_AUTOLAYOUT
- (void)setCenter:(id)value
{
  CHECK_LAYOUT_UPDATE(center, value);

  if ([value isKindOfClass:[NSDictionary class]]) {
    TiDimension result;
    id obj = [value objectForKey:@"x"];
    if (obj != nil) {
      [self replaceValue:obj forKey:@"centerX_" notification:NO];
      result = TiDimensionFromObject(obj);
      if (TiDimensionIsDip(result) || TiDimensionIsPercent(result)) {
        layoutProperties.centerX = result;
      } else {
        layoutProperties.centerX = TiDimensionUndefined;
      }
    }
    obj = [value objectForKey:@"y"];
    if (obj != nil) {
      [self replaceValue:obj forKey:@"centerY_" notification:NO];
      result = TiDimensionFromObject(obj);
      if (TiDimensionIsDip(result) || TiDimensionIsPercent(result)) {
        layoutProperties.centerY = result;
      } else {
        layoutProperties.centerY = TiDimensionUndefined;
      }
    }

  } else if ([value isKindOfClass:[TiPoint class]]) {
    CGPoint p = [value point];
    layoutProperties.centerX = TiDimensionDip(p.x);
    layoutProperties.centerY = TiDimensionDip(p.y);
  } else {
    layoutProperties.centerX = TiDimensionUndefined;
    layoutProperties.centerY = TiDimensionUndefined;
  }

  [self willChangePosition];
}
#endif

- (id)animatedCenter
{
  if (![self viewAttached]) {
    return nil;
  }
  __block CGPoint result;
  TiThreadPerformOnMainThread(
      ^{
        UIView *ourView = view;
        CALayer *ourLayer = [ourView layer];
        CALayer *animatedLayer = [ourLayer presentationLayer];

        if (animatedLayer != nil) {
          result = [animatedLayer position];
        } else {
          result = [ourLayer position];
        }
      },
      YES);
  //TODO: Should this be a TiPoint? If so, the accessor fetcher might try to
  //hold onto the point, which is undesired.
  return [NSDictionary dictionaryWithObjectsAndKeys:NUMFLOAT(result.x), @"x", NUMFLOAT(result.y), @"y", nil];
}

- (void)setBackgroundGradient:(id)arg
{
  TiGradient *newGradient = [TiGradient gradientFromObject:arg proxy:self];
  [self replaceValue:newGradient forKey:@"backgroundGradient" notification:YES];
}

- (KrollPromise *)toImage:(id)args
{
  KrollPromise *promise = [[[KrollPromise alloc] initInContext:[self currentContext]] autorelease];
  KrollCallback *callback = nil;
  BOOL honorScale = YES;

  NSObject *obj = nil;
  if ([args count] > 0) {
    obj = [args objectAtIndex:0];

    if (obj == [NSNull null]) {
      obj = nil;
    }

    if ([args count] > 1) {
      honorScale = [TiUtils boolValue:[args objectAtIndex:1] def:YES];
    }
  }
  callback = (KrollCallback *)obj;
  __block TiBlob *blob = nil;
  // we spin on the UI thread and have him convert and then add back to the blob
  // if you pass a callback function, we'll run the render asynchronously, if you
  // don't, we'll do it synchronously
  TiThreadPerformOnMainThread(
      ^{
        BOOL viewIsAttached = [self viewAttached];
        if (!viewIsAttached) {
          [self windowWillOpen];
        }
        TiUIView *myview = [self view];
        CGSize size = myview.bounds.size;
        if (CGSizeEqualToSize(size, CGSizeZero) || size.width == 0 || size.height == 0) {
#ifndef TI_USE_AUTOLAYOUT
          CGFloat width = [self autoWidthForSize:CGSizeMake(1000, 1000)];
          CGFloat height = [self autoHeightForSize:CGSizeMake(width, 0)];
#else
          CGSize s = [[self view] sizeThatFits:CGSizeMake(1000, 1000)];
          CGFloat width = s.width;
          CGFloat height = s.height;
#endif

          if (width > 0 && height > 0) {
            size = CGSizeMake(width, height);
          }
          if (CGSizeEqualToSize(size, CGSizeZero) || width == 0 || height == 0) {
            size = [UIScreen mainScreen].bounds.size;
          }
          CGRect rect = CGRectMake(0, 0, size.width, size.height);
          [TiUtils setView:myview positionRect:rect];
        }
        if (!viewIsAttached) {
          [self layoutChildren:NO];
        }
        UIGraphicsBeginImageContextWithOptions(size, [myview.layer isOpaque], (honorScale ? 0.0 : 1.0));
        [myview.layer renderInContext:UIGraphicsGetCurrentContext()];
        UIImage *image = UIGraphicsGetImageFromCurrentImageContext();
        blob = [[[TiBlob alloc] initWithImage:image] autorelease];
        [blob setMimeType:@"image/png" type:TiBlobTypeImage];
        UIGraphicsEndImageContext();
        if (callback != nil) {
          [callback call:@[ blob ] thisObject:self];
        }
        [promise resolve:@[ blob ]];
      },
      (callback == nil));

  return promise;
}

- (TiPoint *)convertPointToView:(id)args
{
  id arg1 = nil;
  TiViewProxy *arg2 = nil;
  ENSURE_ARG_AT_INDEX(arg1, args, 0, NSObject);
  ENSURE_ARG_AT_INDEX(arg2, args, 1, TiViewProxy);
  BOOL validPoint;
  CGPoint givenPoint = [TiUtils pointValue:arg1 valid:&validPoint];
  if (!validPoint) {
    [self throwException:TiExceptionInvalidType subreason:@"Parameter is not convertable to a TiPoint" location:CODELOCATION];
  }

  __block BOOL validView = NO;
  __block CGPoint pointOffsetDips;
  TiThreadPerformOnMainThread(
      ^{
        if ([self viewAttached] && self.view.window && [arg2 viewAttached] && arg2.view.window) {
          validView = YES;
          pointOffsetDips = [self.view convertPoint:CGPointZero toView:arg2.view];
        }
      },
      YES);
  if (!validView) {
    return (TiPoint *)[NSNull null];
  }
  TiPoint *tiPoint = [[TiPoint alloc] autorelease];
  [tiPoint setX:NUMFLOAT(convertDipToDefaultUnit(pointOffsetDips.x + givenPoint.x))];
  [tiPoint setY:NUMFLOAT(convertDipToDefaultUnit(pointOffsetDips.y + givenPoint.y))];
  return tiPoint;
}

#pragma mark nonpublic accessors not related to Housecleaning

@synthesize parent, barButtonItem;

- (void)setParent:(TiViewProxy *)parent_
{
  parent = parent_;

  if (parent_ != nil && [parent windowHasOpened]) {
    [self windowWillOpen];
  }
}

#ifndef TI_USE_AUTOLAYOUT
- (LayoutConstraint *)layoutProperties
{
  return &layoutProperties;
}
#endif
@synthesize sandboxBounds;

- (void)setHidden:(BOOL)newHidden withArgs:(id)args
{
  if (hidden == newHidden) {
    return;
  }
  hidden = newHidden;

  //TODO: If we have an animated show, hide, or setVisible, here's the spot for it.

  if (parentVisible) {
    if (hidden) {
      [self willHide];
    } else {
      [self willShow];
    }
  }
}

#ifndef TI_USE_AUTOLAYOUT
- (CGFloat)autoWidthForSize:(CGSize)size
{
  CGFloat suggestedWidth = size.width;
  //This is the content width, which is implemented by widgets
  CGFloat contentWidth = -1.0;
  if ([self respondsToSelector:@selector(contentWidthForWidth:)]) {
    contentWidth = [self contentWidthForWidth:suggestedWidth];
  }

  BOOL isHorizontal = TiLayoutRuleIsHorizontal(layoutProperties.layoutStyle);
  CGFloat result = 0.0;

  CGRect bounds = CGRectZero;
  if (isHorizontal) {
    bounds.size.width = size.width;
    bounds.size.height = size.height;
    verticalLayoutBoundary = 0;
    horizontalLayoutBoundary = 0;
    horizontalLayoutRowHeight = 0;
  }
  CGRect sandBox = CGRectZero;
  CGFloat thisWidth = 0.0;

  NSArray *subproxies = [self children];
  for (TiViewProxy *thisChildProxy in subproxies) {
    if (isHorizontal) {
      sandBox = [self computeChildSandbox:thisChildProxy withBounds:bounds];
      thisWidth = sandBox.origin.x + sandBox.size.width;
    } else {
      thisWidth = [thisChildProxy minimumParentWidthForSize:size];
    }
    if (result < thisWidth) {
      result = thisWidth;
    }
  }

  if (result < contentWidth) {
    result = contentWidth;
  }

  if ([self respondsToSelector:@selector(verifyWidth:)]) {
    result = [self verifyWidth:result];
  }

  return result;
}
#endif

#ifndef TI_USE_AUTOLAYOUT
- (CGFloat)autoHeightForSize:(CGSize)size
{
  CGFloat width = size.width;
  //This is the content width, which is implemented by widgets
  CGFloat contentHeight = -1.0;
  if ([self respondsToSelector:@selector(contentHeightForWidth:)]) {
    contentHeight = [self contentHeightForWidth:width];
  }

  BOOL isAbsolute = TiLayoutRuleIsAbsolute(layoutProperties.layoutStyle);

  CGFloat result = 0.0;

  CGRect bounds = CGRectZero;
  if (!isAbsolute) {
    bounds.size.width = size.width;
    bounds.size.height = size.height;
    verticalLayoutBoundary = 0;
    horizontalLayoutBoundary = 0;
    horizontalLayoutRowHeight = 0;
  }
  CGRect sandBox = CGRectZero;
  CGFloat thisHeight = 0.0;

  NSArray *array = [self children];

  for (TiViewProxy *thisChildProxy in array) {
    if (!isAbsolute) {
      sandBox = [self computeChildSandbox:thisChildProxy withBounds:bounds];
      thisHeight = sandBox.origin.y + sandBox.size.height;
    } else {
      thisHeight = [thisChildProxy minimumParentHeightForSize:size];
    }
    if (result < thisHeight) {
      result = thisHeight;
    }
  }
  //result += currentRowHeight;

  if (result < contentHeight) {
    result = contentHeight;
  }

  if ([self respondsToSelector:@selector(verifyHeight:)]) {
    result = [self verifyHeight:result];
  }

  return result;
}
#endif

#ifndef TI_USE_AUTOLAYOUT
- (CGFloat)minimumParentWidthForSize:(CGSize)size
{
  CGFloat suggestedWidth = size.width;
  BOOL followsFillBehavior = TiDimensionIsAutoFill([self defaultAutoWidthBehavior:nil]);
  BOOL recheckForFill = NO;

  CGFloat offset = TiDimensionCalculateValue(layoutProperties.left, size.width)
      + TiDimensionCalculateValue(layoutProperties.right, size.width);

  CGFloat offset2 = TiDimensionCalculateValue(layoutProperties.top, size.height)
      + TiDimensionCalculateValue(layoutProperties.bottom, size.height);

  CGFloat result = offset;

  if (TiDimensionIsDip(layoutProperties.width) || TiDimensionIsPercent(layoutProperties.width)) {
    result += TiDimensionCalculateValue(layoutProperties.width, suggestedWidth);
  } else if (TiDimensionIsAutoFill(layoutProperties.width) || (TiDimensionIsAuto(layoutProperties.width) && followsFillBehavior)) {
    recheckForFill = YES;
    result += [self autoWidthForSize:CGSizeMake(size.width - offset, size.height - offset2)];
  } else if (TiDimensionIsUndefined(layoutProperties.width)) {
    if (!TiDimensionIsUndefined(layoutProperties.left) && !TiDimensionIsUndefined(layoutProperties.centerX)) {
      result += 2 * (TiDimensionCalculateValue(layoutProperties.centerX, suggestedWidth) - TiDimensionCalculateValue(layoutProperties.left, suggestedWidth));
    } else if (!TiDimensionIsUndefined(layoutProperties.left) && !TiDimensionIsUndefined(layoutProperties.right)) {
      result += TiDimensionCalculateMargins(layoutProperties.left, layoutProperties.right, suggestedWidth);
    } else if (!TiDimensionIsUndefined(layoutProperties.centerX) && !TiDimensionIsUndefined(layoutProperties.right)) {
      result += 2 * (size.width - TiDimensionCalculateValue(layoutProperties.right, suggestedWidth) - TiDimensionCalculateValue(layoutProperties.centerX, suggestedWidth));
    } else {
      recheckForFill = followsFillBehavior;
      result += [self autoWidthForSize:CGSizeMake(size.width - offset, size.height - offset2)];
    }
  } else {
    result += [self autoWidthForSize:CGSizeMake(size.width - offset, size.height - offset2)];
  }
  if (recheckForFill && (result < suggestedWidth)) {
    result = suggestedWidth;
  }
  return result;
}
#endif

#ifndef TI_USE_AUTOLAYOUT
- (CGFloat)minimumParentHeightForSize:(CGSize)size
{
  CGFloat suggestedHeight = size.height;
  BOOL followsFillBehavior = TiDimensionIsAutoFill([self defaultAutoHeightBehavior:nil]);
  BOOL recheckForFill = NO;

  //Ensure that autoHeightForSize is called with the lowest limiting bound
  CGFloat desiredWidth = MIN([self minimumParentWidthForSize:size], size.width);

  CGFloat offset = TiDimensionCalculateValue(layoutProperties.left, size.width)
      + TiDimensionCalculateValue(layoutProperties.right, size.width);

  CGFloat offset2 = TiDimensionCalculateValue(layoutProperties.top, suggestedHeight)
      + TiDimensionCalculateValue(layoutProperties.bottom, suggestedHeight);

  CGFloat result = offset2;

  if (TiDimensionIsDip(layoutProperties.height) || TiDimensionIsPercent(layoutProperties.height)) {
    result += TiDimensionCalculateValue(layoutProperties.height, suggestedHeight);
  } else if (TiDimensionIsAutoFill(layoutProperties.height) || (TiDimensionIsAuto(layoutProperties.height) && followsFillBehavior)) {
    recheckForFill = YES;
    result += [self autoHeightForSize:CGSizeMake(desiredWidth - offset, size.height - offset2)];
  } else if (TiDimensionIsUndefined(layoutProperties.height)) {
    if (!TiDimensionIsUndefined(layoutProperties.top) && !TiDimensionIsUndefined(layoutProperties.centerY)) {
      result += 2 * (TiDimensionCalculateValue(layoutProperties.centerY, suggestedHeight) - TiDimensionCalculateValue(layoutProperties.top, suggestedHeight));
    } else if (!TiDimensionIsUndefined(layoutProperties.top) && !TiDimensionIsUndefined(layoutProperties.bottom)) {
      result += TiDimensionCalculateMargins(layoutProperties.top, layoutProperties.bottom, suggestedHeight);
    } else if (!TiDimensionIsUndefined(layoutProperties.centerY) && !TiDimensionIsUndefined(layoutProperties.bottom)) {
      result += 2 * (suggestedHeight - TiDimensionCalculateValue(layoutProperties.bottom, suggestedHeight) - TiDimensionCalculateValue(layoutProperties.centerY, suggestedHeight));
    } else {
      recheckForFill = followsFillBehavior;
      result += [self autoHeightForSize:CGSizeMake(desiredWidth - offset, size.height - offset2)];
    }
  } else {
    result += [self autoHeightForSize:CGSizeMake(desiredWidth - offset, size.height - offset2)];
  }
  if (recheckForFill && (result < suggestedHeight)) {
    result = suggestedHeight;
  }
  return result;
}
#endif

- (UIBarButtonItem *)barButtonItem
{
  if (barButtonItem == nil) {
    isUsingBarButtonItem = YES;
    barButtonItem = [[UIBarButtonItem alloc] initWithCustomView:[self barButtonViewForSize:CGSizeZero]];
  }
  return barButtonItem;
}

- (TiUIView *)barButtonViewForSize:(CGSize)bounds
{
  TiUIView *barButtonView = [self view];
#ifndef TI_USE_AUTOLAYOUT
  //TODO: This logic should have a good place in case that refreshLayout is used.
  LayoutConstraint barButtonLayout = layoutProperties;
  if (TiDimensionIsUndefined(barButtonLayout.width)) {
    barButtonLayout.width = TiDimensionAutoSize;
  }
  if (TiDimensionIsUndefined(barButtonLayout.height)) {
    barButtonLayout.height = TiDimensionAutoSize;
  }
  if ((bounds.width == 0) && !(TiDimensionIsDip(barButtonLayout.width))) {
    bounds.width = [self autoWidthForSize:CGSizeMake(1000, 1000)];
    barButtonLayout.width = TiDimensionDip(bounds.width);
  }
  if ((bounds.height == 0) && !(TiDimensionIsDip(barButtonLayout.height))) {
    bounds.height = [self autoHeightForSize:CGSizeMake(bounds.width, 1000)];
    barButtonLayout.height = TiDimensionDip(bounds.height);
  }
  CGRect barBounds;
  barBounds.origin = CGPointZero;
  barBounds.size = SizeConstraintViewWithSizeAddingResizing(&barButtonLayout, self, bounds, NULL);

  [TiUtils setView:barButtonView positionRect:barBounds];
  [barButtonView setAutoresizingMask:UIViewAutoresizingNone];

  //Ensure all the child views are laid out as well
  [self windowWillOpen];
  [self setParentVisible:YES];
  [self layoutChildren:NO];
  if (!isUsingBarButtonItem) {
    [self refreshSize];
    [self refreshPosition];
  }
#endif
  return barButtonView;
}

#pragma mark Recognizers

- (BOOL)implementsSelector:(SEL)inSelector
{
  return [self respondsToSelector:inSelector] && !([super respondsToSelector:inSelector] && [self methodForSelector:inSelector] == [super methodForSelector:inSelector]);
}

- (TiUIView *)view
{
  if (view == nil) {
    WARN_IF_BACKGROUND_THREAD_OBJ
#ifdef VERBOSE
    if (![NSThread isMainThread]) {
      NSLog(@"[WARN] Break here");
    }
#endif
    // on open we need to create a new view
    [self viewWillAttach];
    view = [self newView];

#ifdef TI_USE_AUTOLAYOUT
    if ([self implementsSelector:@selector(defaultAutoWidthBehavior:)]) {
      [view setDefaultWidth:[self defaultAutoWidthBehavior:nil]];
    }
    if ([self implementsSelector:@selector(defaultAutoHeightBehavior:)]) {
      [view setDefaultHeight:[self defaultAutoHeightBehavior:nil]];
    }
#endif
    view.proxy = self;
    view.layer.transform = CATransform3DIdentity;
    view.transform = CGAffineTransformIdentity;

    [view initializeState];

    // fire property changes for all properties to our delegate
    [self firePropertyChanges];

    [view configurationSet];

    NSArray *childrenArray = [[self children] retain];
    for (id child in childrenArray) {
      TiUIView *childView = [(TiViewProxy *)child view];
      [self insertSubview:childView forProxy:child];
    }

    [childrenArray release];
    [self viewDidAttach];
    [view updateClipping];
#ifndef TI_USE_AUTOLAYOUT

    // If parent has a non absolute layout signal the parent that
    //contents will change else just lay ourselves out
    if (parent != nil && (!TiLayoutRuleIsAbsolute([parent layoutProperties]->layoutStyle))) {
      [parent contentsWillChange];
    } else {
      if (CGRectIsEmpty(sandboxBounds) && (view != nil)) {
        [self setSandboxBounds:view.bounds];
      }
      [self relayout];
    }
#endif
    viewInitialized = YES;
  }

  CGRect bounds = [view bounds];
  if (!CGPointEqualToPoint(bounds.origin, CGPointZero)) {
    [view setBounds:CGRectMake(0, 0, bounds.size.width, bounds.size.height)];
  }

  return view;
}

//CAUTION: TO BE USED ONLY WITH TABLEVIEW MAGIC
- (void)setView:(TiUIView *)newView
{
  if (view != newView) {
    [view removeFromSuperview];
    [view release];
    view = [newView retain];
  }

  if (self.modelDelegate != newView) {
    if (self.modelDelegate != nil && [self.modelDelegate respondsToSelector:@selector(detachProxy)]) {
      [self.modelDelegate detachProxy];
      self.modelDelegate = nil;
    }
    self.modelDelegate = newView;
  }
}

- (NSMutableDictionary *)langConversionTable
{
  return nil;
}

#pragma mark Methods subclasses should override for behavior changes
- (BOOL)optimizeSubviewInsertion
{
  //Return YES for any view that implements a wrapperView that is a TiUIView (Button and ScrollView currently) and a basic view
  return ([view isMemberOfClass:[TiUIView class]]);
}

- (BOOL)suppressesRelayout
{
  return NO;
}

- (BOOL)supportsNavBarPositioning
{
  return YES;
}

// TODO: Re-evaluate this along with the other controller propagation mechanisms, post 1.3.0.
// Returns YES for anything that can have a UIController object in its parent view
- (BOOL)canHaveControllerParent
{
  return YES;
}

- (BOOL)shouldDetachViewOnUnload
{
  return YES;
}

- (UIView *)parentViewForChild:(TiViewProxy *)child
{
  return view;
}

#pragma mark Event trigger methods

- (void)windowWillOpen
{
  //TODO: This should be properly handled and moved, but for now, let's force it (Redundantly, I know.)
  if (parent != nil) {
    [self parentWillShow];
  }

  pthread_rwlock_rdlock(&childrenLock);

  // this method is called just before the top level window
  // that this proxy is part of will open and is ready for
  // the views to be attached

  if (windowOpened) {
    pthread_rwlock_unlock(&childrenLock);
    return;
  }

  windowOpened = YES;
  windowOpening = YES;

#ifndef TI_USE_AUTOLAYOUT
  BOOL absoluteLayout = TiLayoutRuleIsAbsolute(layoutProperties.layoutStyle);
#endif
  // If the window was previously opened, it may need to have
  // its existing children redrawn
  // Maybe need to call layout children instead for non absolute layout
  if (children != nil) {
    for (TiViewProxy *child in children) {
#ifndef TI_USE_AUTOLAYOUT
      if (absoluteLayout) {
        [self layoutChild:child optimize:NO withMeasuredBounds:[[self size] rect]];
      }
#endif
      [child windowWillOpen];
    }
  }

  pthread_rwlock_unlock(&childrenLock);

#ifndef TI_USE_AUTOLAYOUT
  //TIMOB-17923 - Do a full layout pass (set proper sandbox) if non absolute layout
  if (!absoluteLayout) {
    [self layoutChildren:NO];
  }
#endif
}

- (void)windowDidOpen
{
  windowOpening = NO;
  for (TiViewProxy *child in [self children]) {
    [child windowDidOpen];
  }
}

- (void)windowWillClose
{
  [[self children] makeObjectsPerformSelector:@selector(windowWillClose)];
}

- (void)windowDidClose
{
  for (TiViewProxy *child in [self children]) {
    [child windowDidClose];
  }
  [self detachView];
  windowOpened = NO;
}

- (void)willFirePropertyChanges
{
  // for subclasses
  if ([view respondsToSelector:@selector(willFirePropertyChanges)]) {
    [view performSelector:@selector(willFirePropertyChanges)];
  }
}

- (void)didFirePropertyChanges
{
  // for subclasses
  if ([view respondsToSelector:@selector(didFirePropertyChanges)]) {
    [view performSelector:@selector(didFirePropertyChanges)];
  }
}

- (void)viewWillAttach
{
  // for subclasses
}

- (void)viewDidAttach
{
  // for subclasses
}

- (void)viewWillDetach
{
  // for subclasses
}

- (void)viewDidDetach
{
  // for subclasses
}

- (void)gainFocus
{
  NSArray *childProxies = [self children];
  for (TiViewProxy *thisProxy in childProxies) {
    if ([thisProxy respondsToSelector:@selector(gainFocus)]) {
      [(id)thisProxy gainFocus];
    }
  }
}

- (void)resignFocus
{
  NSArray *childProxies = [self children];
  for (TiViewProxy *thisProxy in childProxies) {
    if ([thisProxy respondsToSelector:@selector(resignFocus)]) {
      [(id)thisProxy resignFocus];
    }
  }
}

#pragma mark Housecleaning state accessors

- (BOOL)viewHasSuperview:(UIView *)superview
{
  return [(UIView *)view superview] == superview;
}

- (BOOL)viewAttached
{
  return view != nil && windowOpened;
}

//TODO: When swapping about proxies, views are uninitialized, aren't they?
- (BOOL)viewInitialized
{
  return viewInitialized && (view != nil);
}

- (BOOL)viewReady
{
  return (view != nil) && ([view superview] != nil);
}

- (BOOL)windowHasOpened
{
  return windowOpened;
}

- (void)setPreviewContext:(id)context
{
  Class TiUIiOSPreviewContextProxy = NSClassFromString(@"TiUIiOSPreviewContextProxy");

  ENSURE_TYPE(context, TiUIiOSPreviewContextProxy);

  if ([context performSelector:@selector(preview)] == nil) {
    NSLog(@"[ERROR] The 'preview' property of your preview context is not existing or invalid. Please provide a valid view to use peek and pop.");
    RELEASE_TO_NIL(context);
    return;
  }

  [context performSelector:@selector(setSourceView:) withObject:self];
  [context performSelector:@selector(connectToDelegate)];

  [self replaceValue:context forKey:@"previewContext" notification:NO];
}

- (BOOL)windowIsOpening
{
  return windowOpening;
}

- (BOOL)isUsingBarButtonItem
{
  return isUsingBarButtonItem;
}

- (CGRect)appFrame //TODO: Why is this here? It doesn't have anything to do with a specific instance.
{
  CGRect result = [[[[TiApp app] controller] view] bounds];
  return result;
}

#pragma mark Building up and Tearing down

- (id)init
{
  if ((self = [super init])) {
    destroyLock = [[NSRecursiveLock alloc] init];
    pthread_rwlock_init(&childrenLock, NULL);
    _bubbleParent = YES;
  }
  return self;
}

- (void)_initWithProperties:(NSDictionary *)properties
{
  updateStarted = YES;
  allowLayoutUpdate = NO;
// Set horizontal layout wrap:true as default
#ifndef TI_USE_AUTOLAYOUT
  layoutProperties.layoutFlags.horizontalWrap = YES;
#endif
  [self initializeProperty:@"horizontalWrap"
              defaultValue:NUMBOOL(YES)];
  [self initializeProperty:@"visible" defaultValue:NUMBOOL(YES)];
  [self initializeProperty:@"touchEnabled" defaultValue:NUMBOOL(YES)];

  if (properties != nil) {
    NSString *objectId = [properties objectForKey:@"id"];
    NSString *className = [properties objectForKey:@"className"];
    NSMutableArray *classNames = [properties objectForKey:@"classNames"];

    NSString *type = [NSStringFromClass([self class]) stringByReplacingOccurrencesOfString:@"TiUI" withString:@""];
    type = [[type stringByReplacingOccurrencesOfString:@"Proxy" withString:@""] lowercaseString];

    TiStylesheet *stylesheet = [[[self pageContext] host] stylesheet];
    NSString *basename = [[self pageContext] basename];
    NSString *density = [TiUtils is2xRetina] ? @"high" : @"medium";

    if (objectId != nil || className != nil || classNames != nil || [stylesheet basename:basename density:density hasTag:type]) {
      // get classes from proxy
      NSString *className = [properties objectForKey:@"className"];
      NSMutableArray *classNames = [properties objectForKey:@"classNames"];
      if (classNames == nil) {
        classNames = [NSMutableArray arrayWithCapacity:1];
      }
      if (className != nil) {
        [classNames addObject:className];
      }

      NSDictionary *merge = [stylesheet stylesheet:objectId density:density basename:basename classes:classNames tags:[NSArray arrayWithObject:type]];
      if (merge != nil) {
        // incoming keys take precendence over existing stylesheet keys
        NSMutableDictionary *dict = [NSMutableDictionary dictionaryWithDictionary:merge];
        [dict addEntriesFromDictionary:properties];

        properties = dict;
      }
    }
    // do a translation of language driven keys to their converted counterparts
    // for example titleid should look up the title in the Locale
    NSMutableDictionary *table = [self langConversionTable];
    if (table != nil) {
      for (id key in table) {
        // determine which key in the lang table we need to use
        // from the lang property conversion key
        id langKey = [properties objectForKey:key];
        if (langKey != nil) {
          // eg. titleid -> title
          id convertKey = [table objectForKey:key];
          // check and make sure we don't already have that key
          // since you can't override it if already present
          if ([properties objectForKey:convertKey] == nil) {
            id newValue = [TiLocale getString:langKey comment:nil];
            if (newValue != nil) {
              [(NSMutableDictionary *)properties setObject:newValue forKey:convertKey];
            }
          }
        }
      }
    }
  }
  [super _initWithProperties:properties];
  updateStarted = NO;
  allowLayoutUpdate = YES;
  [self processTempProperties:nil];
  allowLayoutUpdate = NO;

  [self createSafeAreaViewProxyForWindowProperties:properties];
}

- (void)createSafeAreaViewProxyForWindowProperties:(NSDictionary *)properties
{
  if ([self isKindOfClass:[TiUIWindowProxy class]]) {
    /*
     Added a transparent safeAreaViewProxy above window for safe area layouts if shouldExtendSafeArea is false. All views added on window will be added on safeAreaViewProxy. Layouts of safeAreaViewProxy is getting modified wherever required.
     */
    TiUIWindowProxy *windowProxy = (TiUIWindowProxy *)self;
    windowProxy.shouldExtendSafeArea = [TiUtils boolValue:[self valueForUndefinedKey:@"extendSafeArea"] def:YES];

    if (!windowProxy.safeAreaViewProxy && !windowProxy.shouldExtendSafeArea) {
      NSMutableDictionary *safeAreaProperties = [NSMutableDictionary dictionary];

      id layout = [self valueForUndefinedKey:@"layout"];
      if (layout) {
        safeAreaProperties[@"layout"] = layout;
      }

      id horizontalWrap = [self valueForUndefinedKey:@"horizontalWrap"];
      if (horizontalWrap) {
        safeAreaProperties[@"horizontalWrap"] = horizontalWrap;
      }

      windowProxy.safeAreaViewProxy = [[[TiUIViewProxy alloc] _initWithPageContext:[self pageContext] args:@[ safeAreaProperties ]] autorelease];
      [windowProxy processForSafeArea];
      [self add:windowProxy.safeAreaViewProxy];
    }
  }
}

- (void)dealloc
{
  //	RELEASE_TO_NIL(pendingAdds);
  RELEASE_TO_NIL(destroyLock);
  pthread_rwlock_destroy(&childrenLock);

  //Dealing with children is in _destroy, which is called by super dealloc.
  RELEASE_TO_NIL(barButtonItem);
  [super dealloc];
}

- (BOOL)retainsJsObjectForKey:(NSString *)key
{
  return ![key isEqualToString:@"animation"];
}

- (void)firePropertyChanges
{
  [self willFirePropertyChanges];

  if ([view respondsToSelector:@selector(readProxyValuesWithKeys:)]) {
    id<NSFastEnumeration> values = [self allKeys];
    [view readProxyValuesWithKeys:values];
  }

  [self didFirePropertyChanges];
}

- (TiUIView *)newView
{
  NSString *proxyName = NSStringFromClass([self class]);
  if ([proxyName hasSuffix:@"Proxy"]) {
    Class viewClass = nil;
    NSString *className = [proxyName substringToIndex:[proxyName length] - 5];
    viewClass = NSClassFromString(className);
    if (viewClass != nil) {
      return [[viewClass alloc] init];
    }
  } else {
    DeveloperLog(@"[WARN] No TiView for Proxy: %@, couldn't find class: %@", self, proxyName);
  }
  return [[TiUIView alloc] initWithFrame:[self appFrame]];
}

- (void)detachView
{
  [destroyLock lock];
  if (view != nil) {
    [self viewWillDetach];
    // hold the view during detachment -- but we can't release it immediately.
    // What if it (or a superview or subview) is in the middle of an animation?
    // We probably need to be even MORE careful here.
    [[view retain] autorelease];
    view.proxy = nil;
    if (self.modelDelegate != nil && [self.modelDelegate respondsToSelector:@selector(detachProxy)]) {
      [self.modelDelegate detachProxy];
    }
    self.modelDelegate = nil;
    [view removeFromSuperview];
    RELEASE_TO_NIL(view);
    [self viewDidDetach];
  }

  [[self children] makeObjectsPerformSelector:@selector(detachView)];
  [destroyLock unlock];
}

- (void)_destroy
{
  [destroyLock lock];
  if ([self destroyed]) {
    // not safe to do multiple times given rwlock
    [destroyLock unlock];
    return;
  }
  // _destroy is called during a JS context shutdown, to inform the object to
  // release all its memory and references.  this will then cause dealloc
  // on objects that it contains (assuming we don't have circular references)
  // since some of these objects are registered in the context and thus still
  // reachable, we need _destroy to help us start the unreferencing part

  pthread_rwlock_wrlock(&childrenLock);
  [children makeObjectsPerformSelector:@selector(setParent:) withObject:nil];
  RELEASE_TO_NIL(children);
  pthread_rwlock_unlock(&childrenLock);
  [super _destroy];

  //Part of super's _destroy is to release the modelDelegate, which in our case is ALSO the view.
  //As such, we need to have the super happen before we release the view, so that we can insure that the
  //release that triggers the dealloc happens on the main thread.

  if (barButtonItem != nil) {
    if ([NSThread isMainThread]) {
      RELEASE_TO_NIL(barButtonItem);
    } else {
      TiThreadPerformOnMainThread(
          ^{
            RELEASE_TO_NIL(barButtonItem);
          },
          NO);
    }
  }

  if (view != nil) {
    if ([NSThread isMainThread]) {
      [self detachView];
    } else {
      view.proxy = nil;
      TiThreadPerformOnMainThread(
          ^{
            RELEASE_TO_NIL(view);
          },
          YES);
    }
  }
  [destroyLock unlock];
}

- (void)destroy
{
  //FIXME- me already have a _destroy, refactor this
  [self _destroy];
}

- (void)removeBarButtonView
{
  isUsingBarButtonItem = NO;
  [self setBarButtonItem:nil];
}

#pragma mark Callbacks

- (void)didReceiveMemoryWarning:(NSNotification *)notification
{
  // Only release a view if we're the only living reference for it
  // WARNING: do not call [self view] here as that will create the
  // view if it doesn't yet exist (thus defeating the purpose of
  // this method)

  //NOTE: for now, we're going to have to turn this off until post
  //1.4 where we can figure out why the drawing is screwed up since
  //the views aren't reattaching.
  /*
	if (view!=nil && [view retainCount]==1)
	{
		[self detachView];
	}*/
  [super didReceiveMemoryWarning:notification];
}

- (void)animationCompleted:(TiAnimation *)animation
{
  [self forgetProxy:animation];
  [[self view] animationCompleted];
  //Let us add ourselves to the queue to cleanup layout
  OSAtomicTestAndClearBarrier(TiRefreshViewEnqueued, &dirtyflags);
  [self willEnqueue];
}

- (void)makeViewPerformSelector:(SEL)selector withObject:(id)object createIfNeeded:(BOOL)create waitUntilDone:(BOOL)wait
{
  BOOL isAttached = [self viewAttached];

  if ((!isAttached && !create) || ![[self view] respondsToSelector:selector]) {
    return;
  }

  if ([NSThread isMainThread]) {
    [[self view] performSelector:selector withObject:object];
    return;
  }

  if (isAttached) {
    TiThreadPerformOnMainThread(
        ^{
          [[self view] performSelector:selector withObject:object];
        },
        wait);
    return;
  }

  TiThreadPerformOnMainThread(
      ^{
        [[self view] performSelector:selector withObject:object];
      },
      wait);
}

#pragma mark Listener Management

- (BOOL)_hasListeners:(NSString *)type checkParent:(BOOL)check
{
  BOOL returnVal = [super _hasListeners:type];
  if (!returnVal && check) {
    returnVal = [[self parentForBubbling] _hasListeners:type];
  }
  return returnVal;
}
- (BOOL)_hasListeners:(NSString *)type
{
  return [self _hasListeners:type checkParent:YES];
}

- (BOOL)checkTouchEvent:(NSString *)event
{
  if (touchEventsArray == nil) {
    touchEventsArray = [[NSArray arrayWithObjects:@"touchstart", @"touchend", @"touchmove", @"touchcancel",
                                 @"click", @"dblclick", @"singletap", @"doubletap", @"twofingertap",
                                 @"swipe", @"pinch", @"longpress", nil] retain];
  }

  return [touchEventsArray containsObject:event];
}

//TODO: Remove once we've properly deprecated.
- (void)fireEvent:(NSString *)type withObject:(id)obj withSource:(id)source propagate:(BOOL)propagate reportSuccess:(BOOL)report errorCode:(int)code message:(NSString *)message;
{
  // Note that some events (like movie 'complete') are fired after the view is removed/dealloc'd.
  // Because of the handling below, we can safely set the view to 'nil' in this case.
  TiUIView *proxyView = [self viewAttached] ? view : nil;
  //TODO: We have to do view instead of [self view] because of a freaky race condition that can
  //happen in the background (See bug 2809). This assumes that view == [self view], which may
  //not always be the case in the future. Then again, we shouldn't be dealing with view in the BG...

  // Have to handle the situation in which the proxy's view might be nil... like, for example,
  // with table rows.  Automagically assume any nil view we're firing an event for is A-OK.
  // NOTE: We want to fire postlayout events on ANY view, even those which do not allow interactions.
  BOOL isTouchEvent = [self checkTouchEvent:type];
  if (proxyView == nil || !isTouchEvent || (isTouchEvent && [proxyView interactionEnabled])) {
    [super fireEvent:type withObject:obj withSource:source propagate:propagate reportSuccess:report errorCode:code message:message];
  }
}

- (void)fireEvent:(NSString *)type withObject:(id)obj propagate:(BOOL)propagate reportSuccess:(BOOL)report errorCode:(NSInteger)code message:(NSString *)message;
{
  // Note that some events (like movie 'complete') are fired after the view is removed/dealloc'd.
  // Because of the handling below, we can safely set the view to 'nil' in this case.
  TiUIView *proxyView = [self viewAttached] ? view : nil;
  //TODO: We have to do view instead of [self view] because of a freaky race condition that can
  //happen in the background (See bug 2809). This assumes that view == [self view], which may
  //not always be the case in the future. Then again, we shouldn't be dealing with view in the BG...

  // Have to handle the situation in which the proxy's view might be nil... like, for example,
  // with table rows.  Automagically assume any nil view we're firing an event for is A-OK.
  // NOTE: We want to fire postlayout events on ANY view, even those which do not allow interactions.
  BOOL isTouchEvent = [self checkTouchEvent:type];
  if (proxyView == nil || !isTouchEvent || (isTouchEvent && [proxyView interactionEnabled])) {
    if (eventOverrideDelegate != nil) {
      obj = [eventOverrideDelegate overrideEventObject:obj forEvent:type fromViewProxy:self];
    }
    [super fireEvent:type withObject:obj propagate:propagate reportSuccess:report errorCode:code message:message];
  }
}

- (void)parentListenersChanged
{
  TiThreadPerformOnMainThread(
      ^{
        if (view != nil && [view respondsToSelector:@selector(updateTouchHandling)]) {
          [view updateTouchHandling];
        }
      },
      NO);
}

- (void)_listenerAdded:(NSString *)type count:(int)count
{
  if (self.modelDelegate != nil && [(NSObject *)self.modelDelegate respondsToSelector:@selector(listenerAdded:count:)]) {
    [self.modelDelegate listenerAdded:type count:count];
  } else if (view != nil) // don't create the view if not already realized
  {
    if ([self.view respondsToSelector:@selector(listenerAdded:count:)]) {
      [self.view listenerAdded:type count:count];
    }
  }

  //TIMOB-15991 Update children as well
  NSArray *childrenArray = [[self children] retain];
  for (id child in childrenArray) {
    if ([child respondsToSelector:@selector(parentListenersChanged)]) {
      [child parentListenersChanged];
    }
  }
  [childrenArray release];
}

- (void)_listenerRemoved:(NSString *)type count:(int)count
{
  if (self.modelDelegate != nil && [(NSObject *)self.modelDelegate respondsToSelector:@selector(listenerRemoved:count:)]) {
    [self.modelDelegate listenerRemoved:type count:count];
  } else if (view != nil) // don't create the view if not already realized
  {
    if ([self.view respondsToSelector:@selector(listenerRemoved:count:)]) {
      [self.view listenerRemoved:type count:count];
    }
  }

  //TIMOB-15991 Update children as well
  NSArray *childrenArray = [[self children] retain];
  for (id child in childrenArray) {
    if ([child respondsToSelector:@selector(parentListenersChanged)]) {
      [child parentListenersChanged];
    }
  }
  [childrenArray release];
}

- (TiProxy *)parentForBubbling
{
  return parent;
}

#pragma mark Layout events, internal and external

#define SET_AND_PERFORM(flagBit, action)                 \
  if (OSAtomicTestAndSetBarrier(flagBit, &dirtyflags)) { \
    action;                                              \
  }

- (void)willEnqueue
{
#ifndef TI_USE_AUTOLAYOUT
  SET_AND_PERFORM(TiRefreshViewEnqueued, return );
  [TiLayoutQueue addViewProxy:self];
#endif
}

- (void)willEnqueueIfVisible
{
#ifndef TI_USE_AUTOLAYOUT
  if (parentVisible && !hidden) {
    [self willEnqueue];
  }
#endif
}

- (void)willChangeSize
{
#ifndef TI_USE_AUTOLAYOUT
  SET_AND_PERFORM(TiRefreshViewSize, return );

  if (!TiLayoutRuleIsAbsolute(layoutProperties.layoutStyle)) {
    [self willChangeLayout];
  }
  if (TiDimensionIsUndefined(layoutProperties.centerX) || TiDimensionIsUndefined(layoutProperties.centerY)) {
    [self willChangePosition];
  }

  [self willEnqueueIfVisible];
  [parent contentsWillChange];
  pthread_rwlock_rdlock(&childrenLock);
  [children makeObjectsPerformSelector:@selector(parentSizeWillChange)];
  pthread_rwlock_unlock(&childrenLock);
#endif
}

- (void)willChangePosition
{
#ifndef TI_USE_AUTOLAYOUT
  SET_AND_PERFORM(TiRefreshViewPosition, return );

  if (TiDimensionIsUndefined(layoutProperties.width) || TiDimensionIsUndefined(layoutProperties.height)) { //The only time size can be changed by the margins is if the margins define the size.
    [self willChangeSize];
  }
  [self willEnqueueIfVisible];
  [parent contentsWillChange];
#endif
}

- (void)willChangeZIndex
{
#ifndef TI_USE_AUTOLAYOUT
  SET_AND_PERFORM(TiRefreshViewZIndex, );
  //Nothing cascades from here.
  [self willEnqueueIfVisible];
#endif
}

- (void)willShow;
{
  if (dirtyflags) { //If we have any need for changes, let's enroll ourselves.
    [self willEnqueue];
  }

  SET_AND_PERFORM(TiRefreshViewZIndex, );
  [parent contentsWillChange];

  pthread_rwlock_rdlock(&childrenLock);
  [children makeObjectsPerformSelector:@selector(parentWillShow)];
  pthread_rwlock_unlock(&childrenLock);
}

- (void)willHide;
{
  SET_AND_PERFORM(TiRefreshViewZIndex, );
  [parent contentsWillChange];

  [self willEnqueue];

  pthread_rwlock_rdlock(&childrenLock);
  [children makeObjectsPerformSelector:@selector(parentWillHide)];
  pthread_rwlock_unlock(&childrenLock);
}

- (void)willChangeLayout
{
  SET_AND_PERFORM(TiRefreshViewChildrenPosition, return );

  [self willEnqueueIfVisible];

  pthread_rwlock_rdlock(&childrenLock);
  [children makeObjectsPerformSelector:@selector(parentWillRelay)];
  pthread_rwlock_unlock(&childrenLock);
}

- (BOOL)widthIsAutoSize
{
  BOOL isAutoSize = NO;
#ifndef TI_USE_AUTOLAYOUT
  if (TiDimensionIsAutoSize(layoutProperties.width)) {
    isAutoSize = YES;
  } else if (TiDimensionIsAuto(layoutProperties.width) && TiDimensionIsAutoSize([self defaultAutoWidthBehavior:nil])) {
    isAutoSize = YES;
  } else if (TiDimensionIsUndefined(layoutProperties.width) && TiDimensionIsAutoSize([self defaultAutoWidthBehavior:nil])) {
    int pinCount = 0;
    if (!TiDimensionIsUndefined(layoutProperties.left)) {
      pinCount++;
    }
    if (!TiDimensionIsUndefined(layoutProperties.centerX)) {
      pinCount++;
    }
    if (!TiDimensionIsUndefined(layoutProperties.right)) {
      pinCount++;
    }
    if (pinCount < 2) {
      isAutoSize = YES;
    }
  }
#endif
  return isAutoSize;
}

- (BOOL)heightIsAutoSize
{
  BOOL isAutoSize = NO;
#ifndef TI_USE_AUTOLAYOUT
  if (TiDimensionIsAutoSize(layoutProperties.height)) {
    isAutoSize = YES;
  } else if (TiDimensionIsAuto(layoutProperties.height) && TiDimensionIsAutoSize([self defaultAutoHeightBehavior:nil])) {
    isAutoSize = YES;
  } else if (TiDimensionIsUndefined(layoutProperties.height) && TiDimensionIsAutoSize([self defaultAutoHeightBehavior:nil])) {
    int pinCount = 0;
    if (!TiDimensionIsUndefined(layoutProperties.top)) {
      pinCount++;
    }
    if (!TiDimensionIsUndefined(layoutProperties.centerY)) {
      pinCount++;
    }
    if (!TiDimensionIsUndefined(layoutProperties.bottom)) {
      pinCount++;
    }
    if (pinCount < 2) {
      isAutoSize = YES;
    }
  }
#endif
  return isAutoSize;
}

- (BOOL)widthIsAutoFill
{
  BOOL isAutoFill = NO;
#ifndef TI_USE_AUTOLAYOUT
  BOOL followsFillBehavior = TiDimensionIsAutoFill([self defaultAutoWidthBehavior:nil]);

  if (TiDimensionIsAutoFill(layoutProperties.width)) {
    isAutoFill = YES;
  } else if (TiDimensionIsAuto(layoutProperties.width)) {
    isAutoFill = followsFillBehavior;
  } else if (TiDimensionIsUndefined(layoutProperties.width)) {
    BOOL centerDefined = NO;
    int pinCount = 0;
    if (!TiDimensionIsUndefined(layoutProperties.left)) {
      pinCount++;
    }
    if (!TiDimensionIsUndefined(layoutProperties.centerX)) {
      centerDefined = YES;
      pinCount++;
    }
    if (!TiDimensionIsUndefined(layoutProperties.right)) {
      pinCount++;
    }
    if ((pinCount < 2) || (!centerDefined)) {
      isAutoFill = followsFillBehavior;
    }
  }
#endif
  return isAutoFill;
}

- (BOOL)heightIsAutoFill
{
  BOOL isAutoFill = NO;
#ifndef TI_USE_AUTOLAYOUT
  BOOL followsFillBehavior = TiDimensionIsAutoFill([self defaultAutoHeightBehavior:nil]);

  if (TiDimensionIsAutoFill(layoutProperties.height)) {
    isAutoFill = YES;
  } else if (TiDimensionIsAuto(layoutProperties.height)) {
    isAutoFill = followsFillBehavior;
  } else if (TiDimensionIsUndefined(layoutProperties.height)) {
    BOOL centerDefined = NO;
    int pinCount = 0;
    if (!TiDimensionIsUndefined(layoutProperties.top)) {
      pinCount++;
    }
    if (!TiDimensionIsUndefined(layoutProperties.centerY)) {
      centerDefined = YES;
      pinCount++;
    }
    if (!TiDimensionIsUndefined(layoutProperties.bottom)) {
      pinCount++;
    }
    if ((pinCount < 2) || (!centerDefined)) {
      isAutoFill = followsFillBehavior;
    }
  }
#endif
  return isAutoFill;
}

- (void)contentsWillChange
{
#ifndef TI_USE_AUTOLAYOUT
  BOOL isAutoSize = [self widthIsAutoSize] || [self heightIsAutoSize];

  if (isAutoSize) {
    [self willChangeSize];
  } else if (!TiLayoutRuleIsAbsolute(layoutProperties.layoutStyle)) { //Since changing size already does this, we only need to check
    //Layout if the changeSize didn't
    [self willChangeLayout];
  }
#endif
}

- (void)parentSizeWillChange
{
#ifndef TI_USE_AUTOLAYOUT
  //	if not dip, change size
  if (!TiDimensionIsDip(layoutProperties.width) || !TiDimensionIsDip(layoutProperties.height)) {
    [self willChangeSize];
  }
  if (!TiDimensionIsDip(layoutProperties.centerX) || !TiDimensionIsDip(layoutProperties.centerY)) {
    [self willChangePosition];
  }
#endif
}

- (void)parentWillRelay
{
#ifndef TI_USE_AUTOLAYOUT
  //	if percent or undefined size, change size
  if (TiDimensionIsUndefined(layoutProperties.width) || TiDimensionIsUndefined(layoutProperties.height) || TiDimensionIsPercent(layoutProperties.width) || TiDimensionIsPercent(layoutProperties.height)) {
    [self willChangeSize];
  }
  [self willChangePosition];
#endif
}

- (void)parentWillShow
{
  VerboseLog(@"[INFO] Parent Will Show for %@", self);
  if (parentVisible) { //Nothing to do here, we're already visible here.
    return;
  }
  parentVisible = YES;
  if (!hidden) { //We should propagate this new status! Note this does not change the visible property.
    [self willShow];
  }
}

- (void)parentWillHide
{
  VerboseLog(@"[INFO] Parent Will Hide for %@", self);
  if (!parentVisible) { //Nothing to do here, we're already visible here.
    return;
  }
  parentVisible = NO;
  if (!hidden) { //We should propagate this new status! Note this does not change the visible property.
    [self willHide];
  }
}

#pragma mark Layout actions

// Need this so we can overload the sandbox bounds on split view detail/master
- (void)determineSandboxBounds
{
#ifndef TI_USE_AUTOLAYOUT
  UIView *ourSuperview = [[self view] superview];
  if (ourSuperview == nil) {
    //TODO: Should we even be relaying out? I guess so.
    sandboxBounds = CGRectZero;
  } else {
    sandboxBounds = [ourSuperview bounds];
  }
#endif
}

- (void)refreshView:(TiUIView *)transferView
{
#ifndef TI_USE_AUTOLAYOUT
  WARN_IF_BACKGROUND_THREAD_OBJ;
  OSAtomicTestAndClearBarrier(TiRefreshViewEnqueued, &dirtyflags);

  if (!parentVisible) {
    VerboseLog(@"[INFO] Parent Invisible");
    return;
  }

  if (hidden) {
    VerboseLog(@"Removing from superview");
    if ([self viewAttached]) {
      [[self view] removeFromSuperview];
    }
    return;
  }

  BOOL changedFrame = NO;
  //BUG BARRIER: Code in this block is legacy code that should be factored out.
  if (windowOpened && [self viewAttached]) {
    CGRect oldFrame = [[self view] frame];
    BOOL relayout = ![self suppressesRelayout];
    if (parent != nil && (!TiLayoutRuleIsAbsolute([parent layoutProperties]->layoutStyle))) {
      //Do not mess up the sandbox in vertical/horizontal layouts
      relayout = NO;
    }
    if (relayout) {
      [self determineSandboxBounds];
    }

    [self relayout];
    [self layoutChildren:NO];
    if (!CGRectEqualToRect(oldFrame, [[self view] frame])) {
      [parent childWillResize:self];
    }
  }

  //END BUG BARRIER

  if (OSAtomicTestAndClearBarrier(TiRefreshViewSize, &dirtyflags)) {
    [self refreshSize];
    if (TiLayoutRuleIsAbsolute(layoutProperties.layoutStyle)) {
      for (TiViewProxy *thisChild in [self children]) {
        [thisChild setSandboxBounds:sizeCache];
      }
    }
    changedFrame = YES;
  } else if (transferView != nil) {
    [transferView setBounds:sizeCache];
  }

  if (OSAtomicTestAndClearBarrier(TiRefreshViewPosition, &dirtyflags)) {
    [self refreshPosition];
    changedFrame = YES;
  } else if (transferView != nil) {
    [transferView setCenter:positionCache];
  }

  //We should only recurse if we're a non-absolute layout. Otherwise, the views can take care of themselves.
  if (OSAtomicTestAndClearBarrier(TiRefreshViewChildrenPosition, &dirtyflags) && (transferView == nil))
  //If transferView is non-nil, this will be managed by the table row.
  {
  }

  if (transferView != nil) {
    //TODO: Better handoff of view
    [self setView:transferView];
  }

  //By now, we MUST have our view set to transferView.
  if (changedFrame || (transferView != nil)) {
    [view setAutoresizingMask:autoresizeCache];
  }

  if (OSAtomicTestAndClearBarrier(TiRefreshViewZIndex, &dirtyflags) || (transferView != nil)) {
    [parent insertSubview:view forProxy:self];
  }
#endif
}

- (void)refreshPosition
{
#ifndef TI_USE_AUTOLAYOUT
  OSAtomicTestAndClearBarrier(TiRefreshViewPosition, &dirtyflags);
#endif
}

- (void)refreshSize
{
#ifndef TI_USE_AUTOLAYOUT
  OSAtomicTestAndClearBarrier(TiRefreshViewSize, &dirtyflags);
#endif
}

- (void)insertSubview:(UIView *)childView forProxy:(TiViewProxy *)childProxy
{
  NSUInteger result = 0;
  UIView *ourView = [self parentViewForChild:childProxy];

  if (ourView == nil || childView == nil) {
    return;
  }

  UIView *lastView = nil;

  if (![self optimizeSubviewInsertion]) {
    NSArray *subViews = [[ourView subviews] retain];

    for (UIView *subview in subViews) {
      if (![subview isKindOfClass:[TiUIView class]]) {
        result++;
        lastView = subview;
      }
    }
    [subViews release];
  }

  NSArray *sortedArray;
  sortedArray = [[self children] sortedArrayUsingComparator:^NSComparisonResult(id a, id b) {
    int first = [(TiViewProxy *)a vzIndex];
    int second = [(TiViewProxy *)b vzIndex];
    return (first > second) ? NSOrderedDescending : (first < second ? NSOrderedAscending : NSOrderedSame);
  }];

  for (TiViewProxy *thisChildProxy in sortedArray) {
    if ([thisChildProxy viewInitialized]) {
      UIView *newView = [thisChildProxy view];
      if (lastView == nil) {
        [ourView insertSubview:newView atIndex:result];
      } else {
        [ourView insertSubview:newView aboveSubview:lastView];
      }
      result++;
      lastView = newView;
    }
  }
}

#pragma mark Layout commands that need refactoring out

- (void)relayout
{
#ifndef TI_USE_AUTOLAYOUT
  if (!repositioning) {
    ENSURE_UI_THREAD_0_ARGS

    repositioning = YES;

    CGRect oldRect = sizeCache;
    CGPoint oldCenter = positionCache;

    UIView *parentView = [parent parentViewForChild:self];
    CGSize referenceSize = (parentView != nil) ? parentView.bounds.size : sandboxBounds.size;
    if (parent != nil && (!TiLayoutRuleIsAbsolute([parent layoutProperties]->layoutStyle))) {
      sizeCache.size = SizeConstraintViewWithSizeAddingResizing(&layoutProperties, self, sandboxBounds.size, &autoresizeCache);
    } else {
      sizeCache.size = SizeConstraintViewWithSizeAddingResizing(&layoutProperties, self, referenceSize, &autoresizeCache);
    }

    positionCache = PositionConstraintGivenSizeBoundsAddingResizing(&layoutProperties, self, sizeCache.size,
        [[view layer] anchorPoint], referenceSize, sandboxBounds.size, &autoresizeCache);

    positionCache.x += sizeCache.origin.x + sandboxBounds.origin.x;
    positionCache.y += sizeCache.origin.y + sandboxBounds.origin.y;

    BOOL layoutChanged = (!CGRectEqualToRect(oldRect, sizeCache) || !CGPointEqualToPoint(oldCenter, positionCache));

    [view setAutoresizingMask:autoresizeCache];
    [view setCenter:positionCache];
    [view setBounds:sizeCache];
    if ([view superview] != parentView) {
      [parent insertSubview:view forProxy:self];
    }

    [self refreshSize];
    [self refreshPosition];
    repositioning = NO;

    if ([observer respondsToSelector:@selector(proxyDidRelayout:)]) {
      [observer proxyDidRelayout:self];
    }

    if ([self respondsToSelector:@selector(processForSafeArea)]) {
      TiUIWindowProxy *windowProxy = (TiUIWindowProxy *)self;

      [windowProxy processForSafeArea];
      layoutChanged = layoutChanged || windowProxy.safeAreaInsetsUpdated;
      windowProxy.safeAreaInsetsUpdated = NO;
    }

    if (layoutChanged && [self _hasListeners:@"postlayout" checkParent:NO]) {

      dispatch_block_t block = ^{
        [self fireEvent:@"postlayout" withObject:nil propagate:NO];
      };
      TiThreadPerformOnMainThread(block, NO);
    }
  }
#ifdef VERBOSE
  else {
    DeveloperLog(@"[INFO] %@ Calling Relayout from within relayout.", self);
  }
#endif

#endif
}

- (void)layoutChildrenIfNeeded
{
  IGNORE_IF_NOT_OPENED

  // if not attached, ignore layout
  if ([self viewAttached]) {
    // if not visible, ignore layout
    if (view.hidden) {
#ifndef TI_USE_AUTOLAYOUT
      OSAtomicTestAndClearBarrier(TiRefreshViewEnqueued, &dirtyflags);
#endif
      return;
    }

    [self refreshView:nil];

#ifndef TI_USE_AUTOLAYOUT
    BOOL wasSet = OSAtomicTestAndClearBarrier(NEEDS_LAYOUT_CHILDREN, &dirtyflags);
    if (wasSet && [self viewAttached]) {
      [self layoutChildren:NO];
    }
#endif
  }
}

- (BOOL)willBeRelaying
{
#ifndef TI_USE_AUTOLAYOUT
  DeveloperLog(@"DIRTY FLAGS %d WILLBERELAYING %d", dirtyflags, (*((char *)&dirtyflags) & (1 << (7 - TiRefreshViewEnqueued))));
  return ((*((char *)&dirtyflags) & (1 << (7 - TiRefreshViewEnqueued))) != 0);
#else
  return NO;
#endif
}

- (void)childWillResize:(TiViewProxy *)child
{
#ifndef TI_USE_AUTOLAYOUT
  [self contentsWillChange];

  IGNORE_IF_NOT_OPENED

  BOOL containsChild = [[self children] containsObject:child];

  ENSURE_VALUE_CONSISTENCY(containsChild, YES);

  if (!TiLayoutRuleIsAbsolute(layoutProperties.layoutStyle)) {
    BOOL alreadySet = OSAtomicTestAndSetBarrier(NEEDS_LAYOUT_CHILDREN, &dirtyflags);
    if (!alreadySet) {
      [self willEnqueue];
    }
  }
#endif
}

- (void)reposition
{
  IGNORE_IF_NOT_OPENED

  UIView *superview = [[self view] superview];
  if (![self viewAttached] || view.hidden || superview == nil) {
    VerboseLog(@"[INFO] Reposition is exiting early in %@.", self);
    return;
  }
  if ([NSThread isMainThread]) { //NOTE: This will cause problems with ScrollableView, or is a new wrapper needed?
    [self willChangeSize];
    [self willChangePosition];

    [self refreshView:nil];
  } else {
    VerboseLog(@"[INFO] Reposition was called by a background thread in %@.", self);
    TiThreadPerformOnMainThread(
        ^{
          [self reposition];
        },
        NO);
  }
}

- (NSArray *)measureChildren:(NSArray *)childArray
{
#ifndef TI_USE_AUTOLAYOUT
  if ([childArray count] == 0) {
    return nil;
  }

  BOOL horizontalNoWrap = TiLayoutRuleIsHorizontal(layoutProperties.layoutStyle) && !TiLayoutFlagsHasHorizontalWrap(&layoutProperties);
  NSMutableArray *measuredBounds = [NSMutableArray arrayWithCapacity:[childArray count]];
  int i, count = (int)[childArray count];
  int maxHeight = 0;

  //First measure the sandbox bounds
  for (id child in childArray) {
    TiRect *childRect = [[TiRect alloc] init];
    CGRect childBounds = CGRectZero;
    UIView *ourView = [self parentViewForChild:child];
    if (ourView != nil) {
      CGRect bounds = [ourView bounds];
      if (horizontalNoWrap) {
        maxHeight = MAX(maxHeight, bounds.size.height);
      }
      if (!TiLayoutRuleIsAbsolute(layoutProperties.layoutStyle)) {
        bounds = [self computeChildSandbox:child withBounds:bounds];
      }
      childBounds.origin.x = bounds.origin.x;
      childBounds.origin.y = bounds.origin.y;
      childBounds.size.width = bounds.size.width;
      childBounds.size.height = bounds.size.height;
    }
    [childRect setRect:childBounds];
    [measuredBounds addObject:childRect];
    [childRect release];
  }

  //If it is a horizontal layout ensure that all the children in a row have the
  //same height for the sandbox
  if (horizontalNoWrap) {
    for (i = 0; i < count; i++) {
      [(TiRect *)[measuredBounds objectAtIndex:i] setHeight:[NSNumber numberWithInt:maxHeight]];
    }
  } else if (TiLayoutRuleIsHorizontal(layoutProperties.layoutStyle) && (count > 1)) {
    int startIndex, endIndex, currentTop;
    startIndex = endIndex = maxHeight = currentTop = -1;
    for (i = 0; i < count; i++) {
      CGRect childSandbox = (CGRect)[(TiRect *)[measuredBounds objectAtIndex:i] rect];
      if (startIndex == -1) {
        //FIRST ELEMENT
        startIndex = i;
        maxHeight = childSandbox.size.height;
        currentTop = childSandbox.origin.y;
      } else {
        if (childSandbox.origin.y != currentTop) {
          //MOVED TO NEXT ROW
          endIndex = i;
          for (int j = startIndex; j < endIndex; j++) {
            [(TiRect *)[measuredBounds objectAtIndex:j] setHeight:[NSNumber numberWithInt:maxHeight]];
          }
          startIndex = i;
          endIndex = -1;
          maxHeight = childSandbox.size.height;
          currentTop = childSandbox.origin.y;
        } else if (childSandbox.size.height > maxHeight) {
          //SAME ROW HEIGHT CHANGED
          maxHeight = childSandbox.size.height;
        }
      }
    }
    if (endIndex == -1) {
      //LAST ROW
      for (i = startIndex; i < count; i++) {
        [(TiRect *)[measuredBounds objectAtIndex:i] setHeight:[NSNumber numberWithInt:maxHeight]];
      }
    }
  }
  return measuredBounds;
#else
  return nil;
#endif
}

- (CGRect)computeChildSandbox:(TiViewProxy *)child withBounds:(CGRect)bounds
{
#ifndef TI_USE_AUTOLAYOUT
  if (TiLayoutRuleIsVertical(layoutProperties.layoutStyle)) {
    BOOL followsFillBehavior = TiDimensionIsAutoFill([child defaultAutoHeightBehavior:nil]);
    bounds.origin.y = verticalLayoutBoundary;
    CGFloat boundingValue = bounds.size.height - verticalLayoutBoundary;
    if (boundingValue < 0) {
      boundingValue = 0;
    }

    //Ensure that autoHeightForSize is called with the lowest limiting bound
    CGFloat desiredWidth = MIN([child minimumParentWidthForSize:bounds.size], bounds.size.width);

    //TOP + BOTTOM
    CGFloat offsetV = TiDimensionCalculateValue([child layoutProperties]->top, bounds.size.height)
        + TiDimensionCalculateValue([child layoutProperties]->bottom, bounds.size.height);
    //LEFT + RIGHT
    CGFloat offsetH = TiDimensionCalculateValue([child layoutProperties]->left, bounds.size.width)
        + TiDimensionCalculateValue([child layoutProperties]->right, bounds.size.width);

    TiDimension constraint = [child layoutProperties]->height;

    if (TiDimensionIsDip(constraint) || TiDimensionIsPercent(constraint)) {
      bounds.size.height = TiDimensionCalculateValue(constraint, bounds.size.height) + offsetV;
      verticalLayoutBoundary += bounds.size.height;
    } else if (TiDimensionIsAutoFill(constraint)) {
      //Fill up the remaining
      bounds.size.height = boundingValue;
      verticalLayoutBoundary += bounds.size.height;
    } else if (TiDimensionIsAutoSize(constraint)) {
      bounds.size.height = [child autoHeightForSize:CGSizeMake(desiredWidth - offsetH, boundingValue)] + offsetV;
      verticalLayoutBoundary += bounds.size.height;
    } else if (TiDimensionIsAuto(constraint)) {
      if (followsFillBehavior) {
        //FILL behavior
        bounds.size.height = boundingValue + offsetV;
        verticalLayoutBoundary += bounds.size.height;
      } else {
        //SIZE behavior
        bounds.size.height = [child autoHeightForSize:CGSizeMake(desiredWidth - offsetH, boundingValue)] + offsetV;
        verticalLayoutBoundary += bounds.size.height;
      }
    } else if (TiDimensionIsUndefined(constraint)) {
      if (!TiDimensionIsUndefined([child layoutProperties]->top) && !TiDimensionIsUndefined([child layoutProperties]->centerY)) {
        CGFloat height = 2 * (TiDimensionCalculateValue([child layoutProperties]->centerY, boundingValue) - TiDimensionCalculateValue([child layoutProperties]->top, boundingValue));
        bounds.size.height = height + offsetV;
        verticalLayoutBoundary += bounds.size.height;
      } else if (!TiDimensionIsUndefined([child layoutProperties]->top) && !TiDimensionIsUndefined([child layoutProperties]->bottom)) {
        bounds.size.height = boundingValue;
        verticalLayoutBoundary += bounds.size.height;
      } else if (!TiDimensionIsUndefined([child layoutProperties]->centerY) && !TiDimensionIsUndefined([child layoutProperties]->bottom)) {
        CGFloat height = 2 * (boundingValue - TiDimensionCalculateValue([child layoutProperties]->bottom, boundingValue) - TiDimensionCalculateValue([child layoutProperties]->centerY, boundingValue));
        bounds.size.height = height + offsetV;
        verticalLayoutBoundary += bounds.size.height;
      } else if (followsFillBehavior) {
        //FILL behavior
        bounds.size.height = boundingValue + offsetV;
        verticalLayoutBoundary += bounds.size.height;
      } else {
        //SIZE behavior
        bounds.size.height = [child autoHeightForSize:CGSizeMake(desiredWidth - offsetH, boundingValue)] + offsetV;
        verticalLayoutBoundary += bounds.size.height;
      }
    }
  } else if (TiLayoutRuleIsHorizontal(layoutProperties.layoutStyle)) {
    BOOL horizontalWrap = TiLayoutFlagsHasHorizontalWrap(&layoutProperties);
    BOOL followsFillBehavior = TiDimensionIsAutoFill([child defaultAutoWidthBehavior:nil]);
    CGFloat boundingWidth = bounds.size.width - horizontalLayoutBoundary;
    CGFloat boundingHeight = bounds.size.height - verticalLayoutBoundary;

    //LEFT + RIGHT
    CGFloat offsetH = TiDimensionCalculateValue([child layoutProperties]->left, bounds.size.width)
        + TiDimensionCalculateValue([child layoutProperties]->right, bounds.size.width);
    //TOP + BOTTOM
    CGFloat offsetV = TiDimensionCalculateValue([child layoutProperties]->top, bounds.size.height)
        + TiDimensionCalculateValue([child layoutProperties]->bottom, bounds.size.height);

    TiDimension constraint = [child layoutProperties]->width;

    CGFloat desiredWidth;
    BOOL recalculateWidth = NO;
    BOOL isPercent = NO;

    if (TiDimensionIsDip(constraint) || TiDimensionIsPercent(constraint)) {
      desiredWidth = TiDimensionCalculateValue(constraint, bounds.size.width) + offsetH;
      isPercent = TiDimensionIsPercent(constraint);
    } else if (TiDimensionIsUndefined(constraint)) {
      if (!TiDimensionIsUndefined([child layoutProperties]->left) && !TiDimensionIsUndefined([child layoutProperties]->centerX)) {
        desiredWidth = 2 * (TiDimensionCalculateValue([child layoutProperties]->centerX, boundingWidth) - TiDimensionCalculateValue([child layoutProperties]->left, boundingWidth));
        desiredWidth += offsetH;
      } else if (!TiDimensionIsUndefined([child layoutProperties]->left) && !TiDimensionIsUndefined([child layoutProperties]->right)) {
        recalculateWidth = YES;
        followsFillBehavior = YES;
        desiredWidth = [child autoWidthForSize:CGSizeMake(boundingWidth - offsetH, boundingHeight - offsetV)] + offsetH;
      } else if (!TiDimensionIsUndefined([child layoutProperties]->centerX) && !TiDimensionIsUndefined([child layoutProperties]->right)) {
        desiredWidth = 2 * (boundingWidth - TiDimensionCalculateValue([child layoutProperties]->right, boundingWidth) - TiDimensionCalculateValue([child layoutProperties]->centerX, boundingWidth));
        desiredWidth += offsetH;
      } else {
        recalculateWidth = YES;
        desiredWidth = [child autoWidthForSize:CGSizeMake(boundingWidth - offsetH, boundingHeight - offsetV)] + offsetH;
      }
    } else {
      //This block takes care of auto,SIZE and FILL. If it is size ensure followsFillBehavior is set to false
      recalculateWidth = YES;
      desiredWidth = [child autoWidthForSize:CGSizeMake(boundingWidth - offsetH, boundingHeight - offsetV)] + offsetH;
      if (TiDimensionIsAutoSize(constraint)) {
        followsFillBehavior = NO;
      } else if (TiDimensionIsAutoFill(constraint)) {
        followsFillBehavior = YES;
      }
    }
    CGFloat desiredHeight;
    BOOL childIsFixedHeight = TiDimensionIsPercent([child layoutProperties]->height) || TiDimensionIsDip([child layoutProperties]->height);
    if (childIsFixedHeight) {
      //For percent width is irrelevant
      desiredHeight = [child minimumParentHeightForSize:CGSizeMake(0, bounds.size.height)];
      bounds.size.height = desiredHeight;
    }
    if (horizontalWrap && (desiredWidth > boundingWidth)) {
      if (horizontalLayoutBoundary == 0.0) {
        //This is start of row
        bounds.origin.x = horizontalLayoutBoundary;
        bounds.origin.y = verticalLayoutBoundary;
        if (!childIsFixedHeight) {
          //TIMOB-11998. minimumParentHeightForSize:CGSize will limit width anyways. Pass bounding width here
          //desiredHeight = [child minimumParentHeightForSize:CGSizeMake(desiredWidth,boundingHeight)];
          if (isPercent) {
            desiredHeight = [child minimumParentHeightForSize:CGSizeMake(bounds.size.width, boundingHeight)];
          } else {
            desiredHeight = [child minimumParentHeightForSize:CGSizeMake(boundingWidth, boundingHeight)];
          }
          bounds.size.height = desiredHeight;
        }
        verticalLayoutBoundary += bounds.size.height;
        horizontalLayoutRowHeight = 0.0;
      } else {
        //This is not the start of row. Move to next row
        horizontalLayoutBoundary = 0.0;
        verticalLayoutBoundary += horizontalLayoutRowHeight;
        horizontalLayoutRowHeight = 0;
        bounds.origin.x = horizontalLayoutBoundary;
        bounds.origin.y = verticalLayoutBoundary;

        boundingWidth = bounds.size.width;
        boundingHeight = bounds.size.height - verticalLayoutBoundary;

        if (!recalculateWidth) {
          if (desiredWidth < boundingWidth) {
            if (!childIsFixedHeight) {
              //TIMOB-11998. minimumParentHeightForSize:CGSize will limit width anyways. Pass bounding width here
              //desiredHeight = [child minimumParentHeightForSize:CGSizeMake(desiredWidth,boundingHeight)];
              if (isPercent) {
                desiredHeight = [child minimumParentHeightForSize:CGSizeMake(bounds.size.width, boundingHeight)];
              } else {
                desiredHeight = [child minimumParentHeightForSize:CGSizeMake(boundingWidth, boundingHeight)];
              }
              bounds.size.height = desiredHeight;
            }
            horizontalLayoutBoundary += desiredWidth;
            bounds.size.width = desiredWidth;
            horizontalLayoutRowHeight = bounds.size.height;
          } else {
            //Will take up whole row
            if (!childIsFixedHeight) {
              if (isPercent) {
                desiredHeight = [child minimumParentHeightForSize:CGSizeMake(bounds.size.width, boundingHeight)];
              } else {
                desiredHeight = [child minimumParentHeightForSize:CGSizeMake(boundingWidth, boundingHeight)];
              }
              bounds.size.height = desiredHeight;
            }
            verticalLayoutBoundary += bounds.size.height;
          }
        } else if (followsFillBehavior) {
          //Will take up whole row
          if (!childIsFixedHeight) {
            desiredHeight = [child minimumParentHeightForSize:CGSizeMake(boundingWidth, boundingHeight)];
            bounds.size.height = desiredHeight;
          }
          verticalLayoutBoundary += bounds.size.height;
        } else {
          desiredWidth = [child autoWidthForSize:CGSizeMake(boundingWidth - offsetH, boundingHeight - offsetV)] + offsetH;
          if (desiredWidth < boundingWidth) {
            if (!childIsFixedHeight) {
              //TIMOB-11998. minimumParentHeightForSize:CGSize will limit width anyways. Pass bounding width here
              //desiredHeight = [child minimumParentHeightForSize:CGSizeMake(desiredWidth,boundingHeight)];
              if (isPercent) {
                desiredHeight = [child minimumParentHeightForSize:CGSizeMake(bounds.size.width, boundingHeight)];
              } else {
                desiredHeight = [child minimumParentHeightForSize:CGSizeMake(boundingWidth, boundingHeight)];
              }
              bounds.size.height = desiredHeight;
            }
            bounds.size.width = desiredWidth;
            horizontalLayoutBoundary = bounds.size.width;
            horizontalLayoutRowHeight = bounds.size.height;
          } else {
            //Will take up whole row
            if (!childIsFixedHeight) {
              if (isPercent) {
                desiredHeight = [child minimumParentHeightForSize:CGSizeMake(bounds.size.width, boundingHeight)];
              } else {
                desiredHeight = [child minimumParentHeightForSize:CGSizeMake(boundingWidth, boundingHeight)];
              }
              bounds.size.height = desiredHeight;
            }
            verticalLayoutBoundary += bounds.size.height;
          }
        }
      }
    } else {
      //If it fits update the horizontal layout row height
      if (!childIsFixedHeight) {
        //TIMOB-11998. minimumParentHeightForSize:CGSize will limit width anyways. Pass bounding width here
        //desiredHeight = [child minimumParentHeightForSize:CGSizeMake(desiredWidth,boundingHeight)];
        if (isPercent) {
          desiredHeight = [child minimumParentHeightForSize:CGSizeMake(bounds.size.width, boundingHeight)];
        } else {
          desiredHeight = [child minimumParentHeightForSize:CGSizeMake(boundingWidth, boundingHeight)];
        }
        bounds.size.height = desiredHeight;
      }
      bounds.origin.x = horizontalLayoutBoundary;
      bounds.origin.y = verticalLayoutBoundary;

      if (bounds.size.height > horizontalLayoutRowHeight) {
        horizontalLayoutRowHeight = bounds.size.height;
      }
      if (!recalculateWidth) {
        //DIP,PERCENT,UNDEFINED WITH ATLEAST 2 PINS one of them being centerX
        bounds.size.width = desiredWidth;
        horizontalLayoutBoundary += bounds.size.width;
      } else if (followsFillBehavior) {
        //FILL that fits in left over space. Move to next row
        bounds.size.width = boundingWidth;
        if (horizontalWrap) {
          horizontalLayoutBoundary = 0.0;
          verticalLayoutBoundary += horizontalLayoutRowHeight;
          horizontalLayoutRowHeight = 0.0;
        } else {
          horizontalLayoutBoundary += bounds.size.width;
        }
      } else {
        //SIZE behavior
        bounds.size.width = desiredWidth;
        horizontalLayoutBoundary += bounds.size.width;
      }
    }
  }

  return bounds;
#else
  return CGRectZero;
#endif
}

- (void)layoutChild:(TiViewProxy *)child optimize:(BOOL)optimize withMeasuredBounds:(CGRect)bounds
{
  UIView *ourView = [self parentViewForChild:child];

  if (ourView == nil) {
    return;
  }

  if (!optimize) {
    TiUIView *childView = [child view];
    if ([childView superview] != ourView) {
      [self insertSubview:childView forProxy:child];
    }
  }
  [child setSandboxBounds:bounds];
  if ([[child view] animating]) {
    // changing the layout while animating is bad, ignore for now
    DebugLog(@"[WARN] New layout set while view %@ animating: Will relayout after animation.", child);
  } else {
    [child relayout];
  }
  // tell our children to also layout
  [child layoutChildren:optimize];
}

- (void)layoutChildren:(BOOL)optimize
{
#ifndef TI_USE_AUTOLAYOUT
  IGNORE_IF_NOT_OPENED

  verticalLayoutBoundary = 0.0;
  horizontalLayoutBoundary = 0.0;
  horizontalLayoutRowHeight = 0.0;

  if (!optimize) {
    OSAtomicTestAndSetBarrier(NEEDS_LAYOUT_CHILDREN, &dirtyflags);
  }

  //TODO: This is really expensive, but what can you do? Laying out the child needs the lock again.
  NSArray *childrenArray = [[self children] retain];

  NSUInteger childCount = [childrenArray count];
  if (childCount > 0) {
    NSArray *measuredBounds = [[self measureChildren:childrenArray] retain];
    NSUInteger childIndex;
    for (childIndex = 0; childIndex < childCount; childIndex++) {
      id child = [childrenArray objectAtIndex:childIndex];
      CGRect childSandBox = (CGRect)[(TiRect *)[measuredBounds objectAtIndex:childIndex] rect];
      [self layoutChild:child optimize:optimize withMeasuredBounds:childSandBox];
    }
    [measuredBounds release];
  }
  [childrenArray release];

  if (!optimize) {
    OSAtomicTestAndClearBarrier(NEEDS_LAYOUT_CHILDREN, &dirtyflags);
  }
#endif
}

- (TiDimension)defaultAutoWidthBehavior:(id)unused
{
  return TiDimensionAutoFill;
}
- (TiDimension)defaultAutoHeightBehavior:(id)unused
{
  return TiDimensionAutoFill;
}

#pragma mark - Accessibility API

- (void)setAccessibilityLabel:(id)accessibilityLabel
{
  ENSURE_UI_THREAD(setAccessibilityLabel, accessibilityLabel);
  if ([self viewAttached]) {
    [[self view] setAccessibilityLabel_:accessibilityLabel];
  }
  [self replaceValue:accessibilityLabel forKey:@"accessibilityLabel" notification:NO];
}

- (void)setAccessibilityValue:(id)accessibilityValue
{
  ENSURE_UI_THREAD(setAccessibilityValue, accessibilityValue);
  if ([self viewAttached]) {
    [[self view] setAccessibilityValue_:accessibilityValue];
  }
  [self replaceValue:accessibilityValue forKey:@"accessibilityValue" notification:NO];
}

- (void)setAccessibilityHint:(id)accessibilityHint
{
  ENSURE_UI_THREAD(setAccessibilityHint, accessibilityHint);
  if ([self viewAttached]) {
    [[self view] setAccessibilityHint_:accessibilityHint];
  }
  [self replaceValue:accessibilityHint forKey:@"accessibilityHint" notification:NO];
}

- (void)setAccessibilityHidden:(id)accessibilityHidden
{
  ENSURE_UI_THREAD(setAccessibilityHidden, accessibilityHidden);
  if ([self viewAttached]) {
    [[self view] setAccessibilityHidden_:accessibilityHidden];
  }
  [self replaceValue:accessibilityHidden forKey:@"accessibilityHidden" notification:NO];
}

- (NSObject *)accessibilityElement
{
  return self.view.accessibilityElement;
}

- (NSString *)accessibilityLabel
{
  return [self accessibilityElement].accessibilityLabel;
}

- (NSString *)accessibilityValue
{
  return [self accessibilityElement].accessibilityValue;
}

- (NSString *)accessibilityHint
{
  return [self accessibilityElement].accessibilityHint;
}

- (NSNumber *)accessibilityHidden
{
  return NUMBOOL([self accessibilityElement].accessibilityElementsHidden);
}

#pragma mark - View Templates

- (void)unarchiveFromTemplate:(id)viewTemplate_
{
  TiViewTemplate *viewTemplate = [TiViewTemplate templateFromViewTemplate:viewTemplate_];
  if (viewTemplate == nil) {
    return;
  }

  id<TiEvaluator> context = self.executionContext;
  if (context == nil) {
    context = self.pageContext;
  }

  [self _initWithProperties:viewTemplate.properties];
  if ([viewTemplate.events count] > 0) {
    [context.krollContext invokeBlockOnThread:^{
      [viewTemplate.events enumerateKeysAndObjectsUsingBlock:^(NSString *eventName, NSArray *listeners, BOOL *stop) {
        [listeners enumerateObjectsUsingBlock:^(KrollWrapper *wrapper, NSUInteger idx, BOOL *stop) {
          [self addEventListener:[NSArray arrayWithObjects:eventName, wrapper, nil]];
        }];
      }];
    }];
  }

  [viewTemplate.childTemplates enumerateObjectsUsingBlock:^(TiViewTemplate *childTemplate, NSUInteger idx, BOOL *stop) {
    TiViewProxy *child = [[self class] unarchiveFromTemplate:childTemplate inContext:context];
    if (child != nil) {
      [context.krollContext invokeBlockOnThread:^{
        [self rememberProxy:child];
        [child forgetSelf];
      }];
      [self add:child];
    }
  }];
}

// Returns protected proxy, caller should do forgetSelf.
+ (TiViewProxy *)unarchiveFromTemplate:(id)viewTemplate_ inContext:(id<TiEvaluator>)context
{
  TiViewTemplate *viewTemplate = [TiViewTemplate templateFromViewTemplate:viewTemplate_];
  if (viewTemplate == nil || viewTemplate.type == nil) {
    return nil;
  }

  TiViewProxy *proxy = nil;
  @try {
    // First try a native proxy class...
    proxy = (TiViewProxy *)[[self class] createProxy:viewTemplate.type withProperties:nil inContext:context];
    [context.krollContext invokeBlockOnThread:^{
      [context registerProxy:proxy];
      [proxy rememberSelf];
    }];
  } @catch (NSException *exception) {
    // No such class exists, so fall back to trying to load an Alloy widget or CommonJS module
    // TODO Cache by name? Otherwise it seems to keep trying to load native class repeatedly (and failing) and has to re-eval this below!
    // TODO eval once and pass in the controller name and props as args?
    DebugLog(@"[DEBUG] Failed to load native class %@, trying Alloy widget / CommonJS module", viewTemplate.type);
    NSString *code = [NSString stringWithFormat:@"var result;"
                                                 "var jsModule;"
                                                 "try {"
                                                 "    jsModule = require('/alloy/widgets/%@/controllers/widget');"
                                                 "} catch (error) {"
                                                 "  try {"
                                                 "    jsModule = require('%@');"
                                                 "  } catch (e) {"
                                                 "    Ti.API.error('Failed to load Alloy widget / CommonJS module \"%@\" to be used as template');"
                                                 "  }"
                                                 "}"
                                                 "if (jsModule) {"
                                                 "  result = function (parameters) {"
                                                 "      const obj = new jsModule(parameters);"
                                                 "      return obj.getView();"
                                                 "    };"
                                                 "}"
                                                 "result;",
                               viewTemplate.type, viewTemplate.type, viewTemplate.type];
    id result = [context evalJSAndWait:code];
    if (result != nil) {
      KrollCallback *func = (KrollCallback *)result;
      id contructResult = [func call:@[ viewTemplate.properties ] thisObject:nil]; // TODO: Do some error-handling here if the widget contructor fails?
      proxy = (TiViewProxy *)contructResult;
    } else {
      [self throwException:@"Invalid item template type provided"
                 subreason:@"The item template type provided cannot be resolved."
                  location:CODELOCATION];
    }
  } @finally {
    if (proxy != nil) {
      [proxy unarchiveFromTemplate:viewTemplate];
      return proxy;
    }
  }

  return nil;
}

- (void)hideKeyboard:(id)arg
{
  ENSURE_UI_THREAD_1_ARG(arg);
  if (view != nil)
    [self.view endEditing:YES];
}

@end
