/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2018 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#import "TiUIView.h"
#import "ImageLoader.h"
#import "Ti2DMatrix.h"
#import "Ti3DMatrix.h"
#import "TiApp.h"
#import "TiBase.h"
#import "TiBlob.h"
#import "TiColor.h"
#import "TiRect.h"
#import "TiUtils.h"
#import "TiViewProxy.h"
#import "UIImage+Resize.h"

void InsetScrollViewForKeyboard(UIScrollView *scrollView, CGFloat keyboardTop, CGFloat minimumContentHeight)
{
  VerboseLog(@"ScrollView:%@, keyboardTop:%f minimumContentHeight:%f", scrollView, keyboardTop, minimumContentHeight);

  CGRect scrollVisibleRect = [scrollView convertRect:[scrollView bounds] toView:[[TiApp app] topMostView]];
  //First, find out how much we have to compensate.

  CGFloat obscuredHeight = scrollVisibleRect.origin.y + scrollVisibleRect.size.height - keyboardTop;
  //ObscuredHeight is how many vertical pixels the keyboard obscures of the scroll view. Some of this may be acceptable.

  CGFloat unimportantArea = MAX(scrollVisibleRect.size.height - minimumContentHeight, 0);
  //It's possible that some of the covered area doesn't matter. If it all matters, unimportant is 0.

  //As such, obscuredHeight is now how much actually matters of scrollVisibleRect.

  CGFloat bottomInset = MAX(0, obscuredHeight - unimportantArea);
  [scrollView setContentInset:UIEdgeInsetsMake(0, 0, bottomInset, 0)];

  CGPoint offset = [scrollView contentOffset];

  if (offset.y + bottomInset < 0) {
    offset.y = -bottomInset;
    [scrollView setContentOffset:offset animated:YES];
  }

  VerboseLog(@"ScrollVisibleRect(%f,%f),%fx%f; obscuredHeight:%f; unimportantArea:%f",
      scrollVisibleRect.origin.x, scrollVisibleRect.origin.y, scrollVisibleRect.size.width, scrollVisibleRect.size.height,
      obscuredHeight, unimportantArea);
}

void OffsetScrollViewForRect(UIScrollView *scrollView, CGFloat keyboardTop, CGFloat minimumContentHeight, CGRect responderRect)
{
  VerboseLog(@"ScrollView:%@, keyboardTop:%f minimumContentHeight:%f responderRect:(%f,%f),%fx%f;",
      scrollView, keyboardTop, minimumContentHeight,
      responderRect.origin.x, responderRect.origin.y, responderRect.size.width, responderRect.size.height);

  CGRect scrollVisibleRect = [scrollView convertRect:[scrollView bounds] toView:[[TiApp app] topMostView]];
  //First, find out how much we have to compensate.

  CGFloat obscuredHeight = scrollVisibleRect.origin.y + scrollVisibleRect.size.height - keyboardTop;
  //ObscuredHeight is how many vertical pixels the keyboard obscures of the scroll view. Some of this may be acceptable.

  //It's possible that some of the covered area doesn't matter. If it all matters, unimportant is 0.

  //As such, obscuredHeight is now how much actually matters of scrollVisibleRect.

  VerboseLog(@"ScrollVisibleRect(%f,%f),%fx%f; obscuredHeight:%f;",
      scrollVisibleRect.origin.x, scrollVisibleRect.origin.y, scrollVisibleRect.size.width, scrollVisibleRect.size.height,
      obscuredHeight);

  scrollVisibleRect.size.height -= MAX(0, obscuredHeight);

  //Okay, the scrollVisibleRect.size now represents the actually visible area.

  CGPoint offsetPoint = [scrollView contentOffset];

  CGPoint offsetForBottomRight;
  offsetForBottomRight.x = responderRect.origin.x + responderRect.size.width - scrollVisibleRect.size.width;
  offsetForBottomRight.y = responderRect.origin.y + responderRect.size.height - scrollVisibleRect.size.height;

  offsetPoint.x = MIN(responderRect.origin.x, MAX(offsetPoint.x, offsetForBottomRight.x));
  offsetPoint.y = MIN(responderRect.origin.y, MAX(offsetPoint.y, offsetForBottomRight.y));
  VerboseLog(@"OffsetForBottomright:(%f,%f) OffsetPoint:(%f,%f)",
      offsetForBottomRight.x, offsetForBottomRight.y, offsetPoint.x, offsetPoint.y);

  CGFloat maxOffset = [scrollView contentInset].bottom + [scrollView contentSize].height - scrollVisibleRect.size.height;

  if (maxOffset < offsetPoint.y) {
    offsetPoint.y = MAX(0, maxOffset);
  }

  [scrollView setContentOffset:offsetPoint animated:YES];
}

void ModifyScrollViewForKeyboardHeightAndContentHeightWithResponderRect(UIScrollView *scrollView, CGFloat keyboardTop, CGFloat minimumContentHeight, CGRect responderRect)
{
  VerboseLog(@"ScrollView:%@, keyboardTop:%f minimumContentHeight:%f responderRect:(%f,%f),%fx%f;",
      scrollView, keyboardTop, minimumContentHeight,
      responderRect.origin.x, responderRect.origin.y, responderRect.size.width, responderRect.size.height);

  CGRect scrollVisibleRect = [scrollView convertRect:[scrollView bounds] toView:[[TiApp app] topMostView]];
  //First, find out how much we have to compensate.

  CGFloat obscuredHeight = scrollVisibleRect.origin.y + scrollVisibleRect.size.height - keyboardTop;
  //ObscuredHeight is how many vertical pixels the keyboard obscures of the scroll view. Some of this may be acceptable.

  CGFloat unimportantArea = MAX(scrollVisibleRect.size.height - minimumContentHeight, 0);
  //It's possible that some of the covered area doesn't matter. If it all matters, unimportant is 0.

  //As such, obscuredHeight is now how much actually matters of scrollVisibleRect.

  [scrollView setContentInset:UIEdgeInsetsMake(0, 0, MAX(0, obscuredHeight - unimportantArea), 0)];

  VerboseLog(@"ScrollVisibleRect(%f,%f),%fx%f; obscuredHeight:%f; unimportantArea:%f",
      scrollVisibleRect.origin.x, scrollVisibleRect.origin.y, scrollVisibleRect.size.width, scrollVisibleRect.size.height,
      obscuredHeight, unimportantArea);

  scrollVisibleRect.size.height -= MAX(0, obscuredHeight);

  //Okay, the scrollVisibleRect.size now represents the actually visible area.

  CGPoint offsetPoint = [scrollView contentOffset];

  if (!CGRectIsEmpty(responderRect)) {
    CGPoint offsetForBottomRight;
    offsetForBottomRight.x = responderRect.origin.x + responderRect.size.width - scrollVisibleRect.size.width;
    offsetForBottomRight.y = responderRect.origin.y + responderRect.size.height - scrollVisibleRect.size.height;

    offsetPoint.x = MIN(responderRect.origin.x, MAX(offsetPoint.x, offsetForBottomRight.x));
    offsetPoint.y = MIN(responderRect.origin.y, MAX(offsetPoint.y, offsetForBottomRight.y));
    VerboseLog(@"OffsetForBottomright:(%f,%f) OffsetPoint:(%f,%f)",
        offsetForBottomRight.x, offsetForBottomRight.y, offsetPoint.x, offsetPoint.y);
  } else {
    offsetPoint.x = MAX(0, offsetPoint.x);
    offsetPoint.y = MAX(0, offsetPoint.y);
    VerboseLog(@"OffsetPoint:(%f,%f)", offsetPoint.x, offsetPoint.y);
  }

  [scrollView setContentOffset:offsetPoint animated:YES];
}

NSArray *listenerArray = nil;

@interface TiUIView ()
- (void)sanitycheckListeners;
@end

@interface TiUIView (Private)
- (void)renderRepeatedBackground:(id)image;
@end

@implementation TiUIView

DEFINE_EXCEPTIONS

@synthesize proxy, touchDelegate, backgroundImage, oldSize;

#pragma mark Internal Methods

#if VIEW_DEBUG
- (id)retain
{
  [super retain];
  NSLog(@"[VIEW %@] RETAIN: %d", self, [self retainCount]);
}

- (oneway void)release
{
  NSLog(@"[VIEW %@] RELEASE: %d", self, [self retainCount] - 1);
  [super release];
}
#endif

- (void)dealloc
{
  [transformMatrix release];
  [animation release];
  [backgroundImage release];
  [gradientLayer release];
  [bgdImageLayer release];
  [singleTapRecognizer release];
  [doubleTapRecognizer release];
  [twoFingerTapRecognizer release];
  [pinchRecognizer release];
  [leftSwipeRecognizer release];
  [rightSwipeRecognizer release];
  [upSwipeRecognizer release];
  [downSwipeRecognizer release];
  [longPressRecognizer release];
  proxy = nil;
  touchDelegate = nil;
  [super dealloc];
}

- (void)removeFromSuperview
{
  if ([NSThread isMainThread]) {
    [super removeFromSuperview];
  } else {
    TiThreadPerformOnMainThread(
        ^{
          [super removeFromSuperview];
        },
        YES);
  }
}

#ifdef TI_USE_AUTOLAYOUT
- (void)initializeTiLayoutView
{
  [super initializeTiLayoutView];
  if ([self class] == [TiUIView class]) {
    [self setDefaultHeight:TiDimensionAutoFill];
    [self setDefaultWidth:TiDimensionAutoFill];
  }
}
#endif

- (id)init
{
  self = [super init];
  if (self != nil) {
  }
  return self;
}

- (BOOL)viewSupportsBaseTouchEvents
{
  // give the ability for the subclass to turn off our event handling
  // if it wants too
  return YES;
}

- (void)ensureGestureListeners
{
  if ([(TiViewProxy *)proxy _hasListeners:@"swipe"]) {
    [[self gestureRecognizerForEvent:@"uswipe"] setEnabled:YES];
    [[self gestureRecognizerForEvent:@"dswipe"] setEnabled:YES];
    [[self gestureRecognizerForEvent:@"rswipe"] setEnabled:YES];
    [[self gestureRecognizerForEvent:@"lswipe"] setEnabled:YES];
  }
  if ([(TiViewProxy *)proxy _hasListeners:@"pinch"]) {
    [[self gestureRecognizerForEvent:@"pinch"] setEnabled:YES];
  }
  if ([(TiViewProxy *)proxy _hasListeners:@"longpress"]) {
    [[self gestureRecognizerForEvent:@"longpress"] setEnabled:YES];
  }
}

- (BOOL)proxyHasGestureListeners
{
  return [proxy _hasListeners:@"singletap"] ||
      [proxy _hasListeners:@"doubletap"] ||
      [proxy _hasListeners:@"twofingertap"] ||
      [proxy _hasListeners:@"swipe"] ||
      [proxy _hasListeners:@"pinch"] ||
      [proxy _hasListeners:@"longpress"];
}

- (BOOL)proxyHasTouchListener
{
  return [proxy _hasListeners:@"touchstart"] ||
      [proxy _hasListeners:@"touchcancel"] ||
      [proxy _hasListeners:@"touchend"] ||
      [proxy _hasListeners:@"touchmove"] ||
      [proxy _hasListeners:@"click"] ||
      [proxy _hasListeners:@"dblclick"];
}

- (void)updateTouchHandling
{
  BOOL touchEventsSupported = [self viewSupportsBaseTouchEvents];
  handlesTouches = touchEventsSupported && ([self proxyHasTouchListener] || [self proxyHasGestureListeners]);
  [self ensureGestureListeners];
  // If a user has not explicitly set whether or not the view interacts, base it on whether or
  // not it handles events, and if not, set it to the interaction default.
  if (!changedInteraction) {
    self.userInteractionEnabled = handlesTouches || [self interactionDefault];
  }
}

- (void)initializeState
{
  virtualParentTransform = CGAffineTransformIdentity;

  [self updateTouchHandling];
  [[self proxy] setValue:NUMBOOL([TiUtils boolValue:[[self proxy] valueForKey:@"touchEnabled"] def:YES]) forKey:@"touchEnabled"];
  self.backgroundColor = [UIColor clearColor];
#ifndef TI_USE_AUTOLAYOUT
  self.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
#else
  if (!self.translatesAutoresizingMaskIntoConstraints) {
    self.autoresizingMask = UIViewAutoresizingNone;
  } else {
    self.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
  }
#endif
}

- (void)configurationSet
{
  // can be used to trigger things after all properties are set
  configurationSet = YES;
}

- (void)setProxy:(TiProxy *)p
{
  proxy = p;
  [proxy setModelDelegate:self];
  [self sanitycheckListeners];
}

- (UIImage *)loadImage:(id)image
{
  if (image == nil)
    return nil;
  NSURL *url = [TiUtils toURL:image proxy:proxy];
  if (url == nil && ![image isKindOfClass:[TiBlob class]]) {
    NSLog(@"[WARN] could not find image: %@", image);
    return nil;
  }
  return [TiUtils loadCappedBackgroundImage:image forProxy:[self proxy] withLeftCap:leftCap topCap:topCap];
}

- (id)transformMatrix
{
  return transformMatrix;
}

- (id)accessibilityElement
{
  return self;
}

#pragma mark - Accessibility API

- (void)setAccessibilityLabel_:(id)accessibilityLabel
{
  id accessibilityElement = self.accessibilityElement;
  if (accessibilityElement != nil) {
    [accessibilityElement setIsAccessibilityElement:YES];
    [accessibilityElement setAccessibilityLabel:[TiUtils stringValue:accessibilityLabel]];
    [accessibilityElement setAccessibilityIdentifier:[TiUtils composeAccessibilityIdentifier:accessibilityElement]];
  }
}

- (void)setAccessibilityValue_:(id)accessibilityValue
{
  id accessibilityElement = self.accessibilityElement;
  if (accessibilityElement != nil) {
    [accessibilityElement setIsAccessibilityElement:YES];
    [accessibilityElement setAccessibilityValue:[TiUtils stringValue:accessibilityValue]];
    [accessibilityElement setAccessibilityIdentifier:[TiUtils composeAccessibilityIdentifier:accessibilityElement]];
  }
}

- (void)setAccessibilityHint_:(id)accessibilityHint
{
  id accessibilityElement = self.accessibilityElement;
  if (accessibilityElement != nil) {
    [accessibilityElement setIsAccessibilityElement:YES];
    [accessibilityElement setAccessibilityHint:[TiUtils stringValue:accessibilityHint]];
    [accessibilityElement setAccessibilityIdentifier:[TiUtils composeAccessibilityIdentifier:accessibilityElement]];
  }
}

- (void)setAccessibilityHidden_:(id)accessibilityHidden
{
  id accessibilityElement = self.accessibilityElement;
  if (accessibilityElement != nil) {
    [accessibilityElement setAccessibilityElementsHidden:[TiUtils boolValue:accessibilityHidden def:NO]];
  }
}

#pragma mark Layout

- (void)frameSizeChanged:(CGRect)frame bounds:(CGRect)bounds
{
  if (bgdImageLayer != nil) {
    [CATransaction begin];
    [CATransaction setValue:(id)kCFBooleanTrue forKey:kCATransactionDisableActions];
    [bgdImageLayer setFrame:bounds];
    [CATransaction commit];
  }

  if (backgroundRepeat) {
    [self renderRepeatedBackground:backgroundImage];
  }
  if ([proxy valueForUndefinedKey:@"borderRadius"]) {
    [self updateBorderRadius:[proxy valueForUndefinedKey:@"borderRadius"]];
  }
  [self updateViewShadowPath];
}

- (void)setFrame:(CGRect)frame
{
  [super setFrame:frame];

#ifndef TI_USE_AUTOLAYOUT
  // this happens when a view is added to another view but not
  // through the framework (such as a tableview header) and it
  // means we need to force the layout of our children
  if (!childrenInitialized && !CGRectIsEmpty(frame) && [self.proxy isKindOfClass:[TiViewProxy class]]) {
    childrenInitialized = YES;
    [(TiViewProxy *)self.proxy layoutChildren:NO];
  }
#endif
}

- (void)checkBounds
{
#ifndef TI_USE_AUTOLAYOUT
  CGRect newBounds = [self bounds];
  if (!CGSizeEqualToSize(oldSize, newBounds.size)) {
    oldSize = newBounds.size;
    //TIMOB-11197, TC-1264
    if (!animating) {
      [CATransaction begin];
      [CATransaction setValue:(id)kCFBooleanTrue forKey:kCATransactionDisableActions];
    }
    if ([self gradientLayer] != self.layer) {
      [[self gradientLayer] setFrame:newBounds];
    }
    if ([self backgroundImageLayer] != self.layer) {
      [[self backgroundImageLayer] setFrame:newBounds];
    }
    if (!animating) {
      [CATransaction commit];
    }
    [self frameSizeChanged:[TiUtils viewPositionRect:self] bounds:newBounds];
  }

  if (_shadowLayer && _shadowLayer.shadowOpacity > 0.0f && self.layer.superlayer && _shadowLayer != self.layer) {
    // Set _shadowLayer frame and insert at right place
    [self.layer.superlayer insertSublayer:_shadowLayer below:self.layer];
    _shadowLayer.frame = CGRectMake(self.frame.origin.x, self.frame.origin.y, self.bounds.size.width, self.bounds.size.height);
  }

  if (_borderLayer && _borderLayer != self.layer) {
    // Set _borderLayer frame and path
    _borderLayer.path = [self bezierPathOfView].CGPath;
    _borderLayer.frame = self.bounds;
  }
#endif
}

- (void)setBounds:(CGRect)bounds
{
  [super setBounds:bounds];
  [self checkBounds];
}

- (void)layoutSubviews
{
  [super layoutSubviews];

  [self checkBounds];
}
- (void)updateTransform
{
  if ([transformMatrix isKindOfClass:[Ti2DMatrix class]]) {
    self.transform = CGAffineTransformConcat(virtualParentTransform, [(Ti2DMatrix *)transformMatrix matrix]);
    return;
  }
  if ([transformMatrix isKindOfClass:[Ti3DMatrix class]]) {
    self.layer.transform = CATransform3DConcat(CATransform3DMakeAffineTransform(virtualParentTransform), [(Ti3DMatrix *)transformMatrix matrix]);
    return;
  }
  self.transform = virtualParentTransform;
}

- (void)setVirtualParentTransform:(CGAffineTransform)newTransform
{
  virtualParentTransform = newTransform;
  [self updateTransform];
}

- (void)fillBoundsToRect:(TiRect *)rect
{
  CGRect r = [self bounds];
  [rect setRect:r];
}

- (void)fillFrameToRect:(TiRect *)rect
{
  CGRect r = [self frame];
  [rect setRect:r];
}

- (CAShapeLayer *)borderLayer
{
  // If borderRadius has multiple values, create a custom borderlayer and add as sublayer on self.layer. Otherwise use self.layer.
  NSArray *array = [self cornerArrayFromRadius:[proxy valueForUndefinedKey:@"borderRadius"]];
  if ((!array || array.count <= 1) && _borderLayer != self.layer) {
    self.layer.mask = nil;
    self.layer.borderColor = _borderLayer.strokeColor;
    self.layer.borderWidth = _borderLayer.lineWidth / 2;
    [_borderLayer removeFromSuperlayer];
    RELEASE_TO_NIL(_borderLayer);
    _borderLayer = (CAShapeLayer *)self.layer;
  } else if ((array.count > 1) && (!_borderLayer || _borderLayer == self.layer)) {
    _borderLayer = [[CAShapeLayer alloc] init];
    _borderLayer.fillColor = UIColor.clearColor.CGColor;
    _borderLayer.strokeColor = self.layer.borderColor;
    _borderLayer.lineWidth = self.layer.borderWidth * 2;
    [self.layer addSublayer:_borderLayer];
  }
  return _borderLayer;
}

#pragma mark Public APIs

- (void)setTintColor_:(id)color
{
  TiColor *ticolor = [TiUtils colorValue:color];
  [self setTintColor:[ticolor _color]];
}

- (void)setBorderColor_:(id)color
{
  TiColor *ticolor = [TiUtils colorValue:color];
  CAShapeLayer *layer = [self borderLayer];
  if (layer == self.layer) {
    layer.borderWidth = MAX(layer.borderWidth, 1);
    layer.borderColor = [ticolor _color].CGColor;
  } else {
    layer.lineWidth = MAX(layer.lineWidth * 2, 1 * 2);
    layer.strokeColor = [ticolor _color].CGColor;
  }
}

- (void)setBorderWidth_:(id)w
{
  TiDimension theDim = TiDimensionFromObject(w);
  CGFloat borderWidth = 0;
  if (TiDimensionIsDip(theDim)) {
    borderWidth = MAX(theDim.value, 0);
  }

  CAShapeLayer *layer = [self borderLayer];
  if (layer == self.layer) {
    layer.borderWidth = MAX(borderWidth, 0);
  } else {
    layer.lineWidth = MAX(borderWidth * 2, 0);
  }
  [self updateClipping];
}

- (void)setBackgroundColor_:(id)color
{
  if ([color isKindOfClass:[UIColor class]]) {
    super.backgroundColor = color;
  } else {
    TiColor *ticolor = [TiUtils colorValue:color];
    super.backgroundColor = [ticolor _color];
  }
}

- (void)setOpacity_:(id)opacity
{
  self.alpha = [TiUtils floatValue:opacity];
}

- (CALayer *)backgroundImageLayer
{
  return bgdImageLayer;
}

- (CALayer *)gradientLayer
{
  return gradientLayer;
}

// You might wonder why we don't just use the native feature of -[UIColor colorWithPatternImage:].
// Here's why:
// * It doesn't properly handle alpha channels
// * You can't combine background tesselations with background colors
// * By making the background-repeat flag a boolean swap, we would have to cache, check, and uncache
//   background colors everywhere - and this starts getting really complicated for some views
//   (on the off chance somebody wants to swap tesselation AND has a background color they want to replace it with).

- (void)renderRepeatedBackground:(id)image
{
  if (![NSThread isMainThread]) {
    TiThreadPerformOnMainThread(
        ^{
          [self renderRepeatedBackground:image];
        },
        NO);
    return;
  }

  UIImage *bgImage = [TiUtils loadBackgroundImage:image forProxy:proxy];
  if (bgImage == nil) {
    [self backgroundImageLayer].contents = nil;
    return;
  }

  // Due to coordinate system shenanagins (there are multiple translations between the UIKit coordinate system
  // and the CG coordinate system happening here) we have to manually flip the background image to render
  // before passing it to the tiling system (via passing it through another UIGraphics context; this orients the
  // image in the "correct" way for the second pass).
  //
  // Note that this means passes through two different graphics contexts. They can be nested, but that makes the code
  // even uglier.
  //
  // NOTE: Doing this begins the image tesselation starting at the upper-left, which is considered the 'origin' for all
  // drawing operations on iOS (and presumably Android). By removing this code and instead blitting the [bgImage CGImage]
  // directly into the graphics context, it tesselates from the lower-left.

  UIGraphicsBeginImageContextWithOptions(bgImage.size, NO, bgImage.scale);
  CGContextRef imageContext = UIGraphicsGetCurrentContext();
  CGContextDrawImage(imageContext, CGRectMake(0, 0, bgImage.size.width, bgImage.size.height), [bgImage CGImage]);
  UIImage *translatedImage = UIGraphicsGetImageFromCurrentImageContext();
  UIGraphicsEndImageContext();

  UIGraphicsBeginImageContextWithOptions(self.bounds.size, NO, bgImage.scale);
  CGContextRef background = UIGraphicsGetCurrentContext();
  if (background == nil) {
    //TIMOB-11564. Either width or height of the bounds is zero
    UIGraphicsEndImageContext();
    return;
  }
  CGRect imageRect = CGRectMake(0, 0, bgImage.size.width, bgImage.size.height);
  CGContextDrawTiledImage(background, imageRect, [translatedImage CGImage]);
  UIImage *renderedBg = UIGraphicsGetImageFromCurrentImageContext();
  UIGraphicsEndImageContext();

  [self backgroundImageLayer].contents = (id)renderedBg.CGImage;
}

- (void)setBackgroundImage_:(id)image
{
  UIImage *bgImage = [TiUtils loadBackgroundImage:image forProxy:proxy];

  if (bgImage == nil) {
    [bgdImageLayer removeFromSuperlayer];
    RELEASE_TO_NIL(bgdImageLayer);
    return;
  }

  if (bgdImageLayer == nil) {
    bgdImageLayer = [[CALayer alloc] init];
    [bgdImageLayer setFrame:[self bounds]];
    bgdImageLayer.masksToBounds = YES;
    NSArray *cornerRadiusArray = [self cornerArrayFromRadius:[proxy valueForUndefinedKey:@"borderRadius"]];
    if (cornerRadiusArray && cornerRadiusArray.count > 0) {
      [self addCornerRadius:cornerRadiusArray toLayer:bgdImageLayer];
    } else {
      bgdImageLayer.cornerRadius = self.layer.cornerRadius;
    }
    if (gradientLayer != nil) {
      [[self gradientWrapperView].layer insertSublayer:bgdImageLayer above:gradientLayer];
    } else {
      [[self gradientWrapperView].layer insertSublayer:bgdImageLayer atIndex:0];
    }
  }

  if (backgroundRepeat) {
    [self renderRepeatedBackground:bgImage];
  } else {
    // To fix TIMOB-28150
    [CATransaction begin];
    [CATransaction setDisableActions:YES];
    [self backgroundImageLayer].contents = (id)bgImage.CGImage;
    [CATransaction commit];
    if (bgImage != nil) {
      [self backgroundImageLayer].contentsScale = [bgImage scale];
      [self backgroundImageLayer].contentsCenter = TiDimensionLayerContentCenter(topCap, leftCap, topCap, leftCap, [bgImage size]);
      if (!CGPointEqualToPoint([self backgroundImageLayer].contentsCenter.origin, CGPointZero)) {
        [self backgroundImageLayer].magnificationFilter = @"nearest";
      } else {
        [self backgroundImageLayer].magnificationFilter = @"linear";
      }
    }
  }

  self.backgroundImage = bgImage;
}

- (void)setBackgroundRepeat_:(id)repeat
{
  backgroundRepeat = [TiUtils boolValue:repeat def:NO];
  [self setBackgroundImage_:backgroundImage];
}

- (void)setBackgroundLeftCap_:(id)value
{
  TiDimension cap = TiDimensionFromObject(value);
  if (!TiDimensionEqual(leftCap, cap)) {
    leftCap = cap;
    [self setBackgroundImage_:backgroundImage];
  }
}

- (void)setBackgroundTopCap_:(id)value
{
  TiDimension cap = TiDimensionFromObject(value);
  if (!TiDimensionEqual(topCap, cap)) {
    topCap = cap;
    [self setBackgroundImage_:backgroundImage];
  }
}

- (CGFloat)radiusFromObject:(id)object
{
  TiDimension theDim = TiDimensionFromObject(object);
  return TiDimensionIsDip(theDim) ? MAX(theDim.value, 0) : 0;
}

- (UIBezierPath *)bezierPathOfSize:(CGSize)size forRadiusArray:(NSArray *)radiusArray
{
  CGFloat topLeftRadius;
  CGFloat bottomLeftRadius;
  CGFloat topRightRadius;
  CGFloat bottomRightRadius;

  if (radiusArray.count >= 4) {
    topLeftRadius = [self radiusFromObject:radiusArray[0]];
    topRightRadius = [self radiusFromObject:radiusArray[1]];
    bottomRightRadius = [self radiusFromObject:radiusArray[2]];
    bottomLeftRadius = [self radiusFromObject:radiusArray[3]];
  } else if (radiusArray.count >= 2) {
    CGFloat radius = [self radiusFromObject:radiusArray[0]];
    topLeftRadius = radius;
    bottomRightRadius = radius;

    radius = [self radiusFromObject:radiusArray[1]];
    bottomLeftRadius = radius;
    topRightRadius = radius;
  } else if (radiusArray.count == 1) {
    CGFloat radius = [self radiusFromObject:radiusArray[0]];
    topLeftRadius = radius;
    topRightRadius = radius;
    bottomRightRadius = radius;
    bottomLeftRadius = radius;
  }

  UIBezierPath *borderPath = [UIBezierPath bezierPath];
  [borderPath moveToPoint:CGPointMake(0 + topLeftRadius, 0)];
  [borderPath addLineToPoint:CGPointMake(size.width - topRightRadius, 0)];
  [borderPath addArcWithCenter:CGPointMake(size.width - topRightRadius, topRightRadius) radius:topRightRadius startAngle:3 * M_PI_2 endAngle:0 clockwise:YES];
  [borderPath addLineToPoint:CGPointMake(size.width, size.height - bottomRightRadius)];
  [borderPath addArcWithCenter:CGPointMake(size.width - bottomRightRadius, size.height - bottomRightRadius) radius:bottomRightRadius startAngle:0 endAngle:M_PI_2 clockwise:YES];
  [borderPath addLineToPoint:CGPointMake(bottomLeftRadius, size.height)];
  [borderPath addArcWithCenter:CGPointMake(bottomLeftRadius, size.height - bottomLeftRadius) radius:bottomLeftRadius startAngle:M_PI_2 endAngle:M_PI clockwise:YES];
  [borderPath addLineToPoint:CGPointMake(0, topLeftRadius)];
  [borderPath addArcWithCenter:CGPointMake(topLeftRadius, topLeftRadius) radius:topLeftRadius startAngle:M_PI endAngle:3 * M_PI_2 clockwise:YES];
  [borderPath closePath];
  return borderPath;
}

- (void)addCornerRadius:(NSArray *)radiusArray toLayer:(CALayer *)viewLayer
{
  CAShapeLayer *shapeLayer = [[[CAShapeLayer alloc] initWithLayer:viewLayer] autorelease];
  CGSize size = viewLayer.bounds.size;
  UIBezierPath *bezierPath = [self bezierPathOfSize:size forRadiusArray:radiusArray];

  shapeLayer.path = bezierPath.CGPath;
  shapeLayer.frame = viewLayer.bounds;
  viewLayer.mask = shapeLayer;
  [self updateViewShadowPath];
}

- (NSArray *)cornerArrayFromRadius:(id)radius
{
  NSArray *cornerRadiusArray = nil;
  if ([radius isKindOfClass:[NSString class]]) {
    cornerRadiusArray = [(NSString *)radius componentsSeparatedByString:@" "];
  } else if ([radius isKindOfClass:[NSArray class]]) {
    cornerRadiusArray = radius;
  } else if ([radius isKindOfClass:[NSNumber class]]) {
    cornerRadiusArray = [NSArray arrayWithObject:radius];
  }
  return cornerRadiusArray;
}

- (void)updateBorderRadius:(id)radius
{
  NSArray *cornerRadiusArray = [self cornerArrayFromRadius:radius];

  if (!cornerRadiusArray) {
    NSLog(@"[WARN] No value specified for borderRadius.");
    return;
  }

  if (cornerRadiusArray.count == 0) {
    NSLog(@"[WARN] No value specified for borderRadius.");
    return;
  }

  // If there is 1 corner radius to be set for all corner, use cornerRadius of self.layer. Otherwise use bezierpath.
  if (cornerRadiusArray.count == 1) {
    CGFloat cornerRadius = [self radiusFromObject:cornerRadiusArray[0]];
    self.layer.mask = nil;
    self.layer.cornerRadius = cornerRadius;
    if (bgdImageLayer != nil) {
      bgdImageLayer.mask = nil;
      bgdImageLayer.cornerRadius = cornerRadius;
    }
    if (gradientLayer != nil) {
      gradientLayer.mask = nil;
      gradientLayer.cornerRadius = cornerRadius;
    }
  } else {
    self.layer.cornerRadius = 0;
    [self addCornerRadius:cornerRadiusArray toLayer:self.layer];
    if (bgdImageLayer != nil) {
      bgdImageLayer.cornerRadius = 0;
      [self addCornerRadius:cornerRadiusArray toLayer:bgdImageLayer];
    }
    if (gradientLayer != nil) {
      gradientLayer.cornerRadius = 0;
      [self addCornerRadius:cornerRadiusArray toLayer:gradientLayer];
    }
  }
  [self updateClipping];
  [self updateViewShadowPath];
}

- (void)setBorderRadius_:(id)radius
{
  // Ensure that proper shadowLayer and borderLayer is there.
  // e.g if raidus has multiple values, custom shadowLayer and borderLayer need to added. if it has single value self.layer should be used.

  if (_shadowLayer) {
    [self shadowLayer];
  }
  if (_borderLayer) {
    [self borderLayer];
  }
  [self updateBorderRadius:radius];
}

- (void)setAnchorPoint_:(id)point
{
  self.layer.anchorPoint = [TiUtils pointValue:point];
}

- (void)setTransform_:(id)transform_
{
  RELEASE_TO_NIL(transformMatrix);
  transformMatrix = [transform_ retain];
  [self updateTransform];
}

- (void)setCenter_:(id)point
{
  self.center = [TiUtils pointValue:point];
}

- (void)setVisible_:(id)visible
{
  BOOL oldVal = self.hidden;
  self.hidden = ![TiUtils boolValue:visible];
  //Redraw ourselves if changing from invisible to visible, to handle any changes made
  if (!self.hidden && oldVal) {
    TiViewProxy *viewProxy = (TiViewProxy *)[self proxy];
    [viewProxy willEnqueue];
  }
}

- (void)setTouchEnabled_:(id)arg
{
  self.userInteractionEnabled = [TiUtils boolValue:arg def:[self interactionDefault]];
  changedInteraction = YES;
}

- (BOOL)touchEnabled
{
  return touchEnabled;
}

- (UIView *)gradientWrapperView
{
  return self;
}

- (void)setBackgroundGradient_:(id)arg
{
  if (arg == nil) {
    [gradientLayer removeFromSuperlayer];
    RELEASE_TO_NIL(gradientLayer);
  } else if (gradientLayer == nil) {
    gradientLayer = [[TiGradientLayer alloc] init];
    [(TiGradientLayer *)gradientLayer setGradient:arg];
    [gradientLayer setNeedsDisplayOnBoundsChange:YES];
    [gradientLayer setFrame:[self bounds]];
    [gradientLayer setNeedsDisplay];
    NSArray *cornerRadiusArray = [self cornerArrayFromRadius:[proxy valueForUndefinedKey:@"borderRadius"]];
    if (cornerRadiusArray && cornerRadiusArray.count > 0) {
      [self addCornerRadius:cornerRadiusArray toLayer:gradientLayer];
    } else {
      gradientLayer.cornerRadius = self.layer.cornerRadius;
    }
    gradientLayer.masksToBounds = YES;
    [[self gradientWrapperView].layer insertSublayer:gradientLayer atIndex:0];
  } else {
    [(TiGradientLayer *)gradientLayer setGradient:arg];
    [gradientLayer setNeedsDisplay];
  }
}

- (void)updateClipping
{
  if (clipMode != 0) {
    //Explicitly overridden
    self.clipsToBounds = (clipMode > 0);
  } else {
    if (_shadowLayer.shadowOpacity > 0) {
      //If shadow is visible, disble clipping
      self.clipsToBounds = NO;
    } else if (self.layer.borderWidth > 0 || self.layer.cornerRadius > 0 || [proxy valueForUndefinedKey:@"borderRadius"]) {
      //If borderWidth > 0, or borderRadius > 0 enable clipping
      self.clipsToBounds = YES;
    } else if ([[self proxy] isKindOfClass:[TiViewProxy class]]) {
      self.clipsToBounds = ([[((TiViewProxy *)self.proxy) children] count] > 0);
    } else {
      DeveloperLog(@"[WARN] Proxy is nil or not of kind TiViewProxy. Check");
      self.clipsToBounds = NO;
    }
  }
}

- (void)setClipMode_:(id)arg
{
  [[self proxy] replaceValue:arg forKey:@"clipMode" notification:NO];
  clipMode = [TiUtils intValue:arg def:0];
  [self updateClipping];
}

/**
 This section of code for shadow support adapted from contributions by Martin Guillon
 See https://github.com/appcelerator/titanium_mobile/pull/2996
 */
- (void)assignShadowPropertyFromLayer:(CALayer *)fromLayer toLayer:(CALayer *)toLayer
{
  toLayer.shadowColor = fromLayer.shadowColor;
  toLayer.shadowOffset = fromLayer.shadowOffset;
  toLayer.shadowRadius = fromLayer.shadowRadius;
  toLayer.shadowOpacity = fromLayer.shadowOpacity;
}

- (CAShapeLayer *)shadowLayer
{
  // If there is single cborderRadius, use self.layer as shadwoLayer.
  // Shadow animation does not work if shadowLayer is added on self.layer.superlayer
  // But in this case, shadwoLayer is self.layer. So animation will work on shadow as well.
  NSArray *array = [self cornerArrayFromRadius:[proxy valueForUndefinedKey:@"borderRadius"]];
  if ((!array || array.count <= 1) && _shadowLayer != self.layer) {
    self.layer.mask = nil;
    [self assignShadowPropertyFromLayer:_shadowLayer toLayer:self.layer];
    [_shadowLayer removeFromSuperlayer];
    RELEASE_TO_NIL(_shadowLayer);
    _shadowLayer = (CAShapeLayer *)self.layer;
  } else if ((array.count > 1) && (!_shadowLayer || _shadowLayer == self.layer)) {
    _shadowLayer = [[CAShapeLayer alloc] init];
    _shadowLayer.fillColor = UIColor.clearColor.CGColor;
    [self assignShadowPropertyFromLayer:self.layer toLayer:_shadowLayer];
    if (self.layer.superlayer) {
      [self.layer.superlayer insertSublayer:_shadowLayer below:self.layer];
    }
  }
  return _shadowLayer;
}

- (UIBezierPath *)bezierPathOfView
{
  if ([proxy valueForUndefinedKey:@"borderRadius"]) {
    NSArray *array = [self cornerArrayFromRadius:[proxy valueForUndefinedKey:@"borderRadius"]];
    if (array.count > 1) {
      return [self bezierPathOfSize:self.bounds.size forRadiusArray:array];
    }
  }
  return [UIBezierPath bezierPathWithRoundedRect:[self bounds] cornerRadius:self.layer.cornerRadius];
}

- (void)setViewShadowOffset_:(id)arg
{
  [[self proxy] replaceValue:arg forKey:@"viewShadowOffset" notification:NO];
  CGPoint p = [TiUtils pointValue:arg];
  [[self shadowLayer] setShadowOffset:CGSizeMake(p.x, p.y)];
}

- (void)setViewShadowRadius_:(id)arg
{
  [[self proxy] replaceValue:arg forKey:@"viewShadowRadius" notification:NO];
  [[self shadowLayer] setShadowRadius:[TiUtils floatValue:arg def:0.0]];
}

- (void)setViewShadowColor_:(id)arg
{
  [[self proxy] replaceValue:arg forKey:@"viewShadowColor" notification:NO];
  TiColor *theColor = [TiUtils colorValue:arg];

  if (theColor == nil) {
    [[self shadowLayer] setShadowColor:nil];
    [[self shadowLayer] setShadowOpacity:0.0];
  } else {
    CGFloat alpha = CGColorGetAlpha([[theColor color] CGColor]);
    [[self shadowLayer] setShadowColor:[[theColor color] CGColor]];
    [[self shadowLayer] setShadowOpacity:alpha];
    [self updateViewShadowPath];
  }
  [self updateClipping];
}

- (void)setHorizontalMotionEffect_:(id)motionEffect
{
  CGFloat min = [TiUtils floatValue:[motionEffect objectForKey:@"min"]];
  CGFloat max = [TiUtils floatValue:[motionEffect objectForKey:@"max"]];
  UIInterpolatingMotionEffect *effect = [[UIInterpolatingMotionEffect alloc] initWithKeyPath:@"center.x"
                                                                                        type:UIInterpolatingMotionEffectTypeTiltAlongHorizontalAxis];
  effect.minimumRelativeValue = @(min);
  effect.maximumRelativeValue = @(max);

  [self addMotionEffect:effect];
  RELEASE_TO_NIL(effect);
}

- (void)setVerticalMotionEffect_:(id)motionEffect
{
  CGFloat min = [TiUtils floatValue:[motionEffect objectForKey:@"min"]];
  CGFloat max = [TiUtils floatValue:[motionEffect objectForKey:@"max"]];
  UIInterpolatingMotionEffect *effect = [[UIInterpolatingMotionEffect alloc] initWithKeyPath:@"center.y"
                                                                                        type:UIInterpolatingMotionEffectTypeTiltAlongVerticalAxis];
  effect.minimumRelativeValue = @(min);
  effect.maximumRelativeValue = @(max);

  [self addMotionEffect:effect];
  RELEASE_TO_NIL(effect);
}

- (void)updateViewShadowPath
{
  if (_shadowLayer.shadowOpacity > 0.0f) {
    //to speedup things
    UIBezierPath *bezierPath = [self bezierPathOfView];
    if (_shadowLayer != self.layer) {
      _shadowLayer.frame = CGRectMake(self.frame.origin.x, self.frame.origin.y, self.bounds.size.width, self.bounds.size.height);
    } else {
      _shadowLayer.bounds = self.bounds;
    }
    _shadowLayer.shadowPath = bezierPath.CGPath;
  }
  if (_borderLayer && _borderLayer != self.layer) {
    _borderLayer.path = [self bezierPathOfView].CGPath;
    _borderLayer.bounds = self.bounds;
  }
}

- (void)didAddSubview:(UIView *)view
{
  // So, it turns out that adding a subview places it beneath the gradient layer.
  // Every time we add a new subview, we have to make sure the gradient stays where it belongs...
  if (gradientLayer != nil) {
    [[self gradientWrapperView].layer insertSublayer:gradientLayer atIndex:0];
    if (bgdImageLayer != nil) {
      [[self gradientWrapperView].layer insertSublayer:bgdImageLayer above:gradientLayer];
    }
  } else if (bgdImageLayer != nil) {
    [[self gradientWrapperView].layer insertSublayer:bgdImageLayer atIndex:0];
  }
}

- (void)animate:(TiAnimation *)newAnimation
{
  RELEASE_TO_NIL(animation);

  if ([self.proxy isKindOfClass:[TiViewProxy class]] && ![(TiViewProxy *)self.proxy viewReady]) {
    DebugLog(@"[DEBUG] Ti.View.animate() called before view %@ was ready: Will re-attempt", self);
    if (animationDelayGuard++ > 5) {
      DebugLog(@"[DEBUG] Animation guard triggered, exceeded timeout to perform animation.");
      animationDelayGuard = 0;
      return;
    }
    [self performSelector:@selector(animate:) withObject:newAnimation afterDelay:0.01];
    return;
  }

  animationDelayGuard = 0;
  BOOL resetState = NO;
  if ([self.proxy isKindOfClass:[TiViewProxy class]] && [(TiViewProxy *)self.proxy willBeRelaying]) {
    DeveloperLog(@"RESETTING STATE");
    resetState = YES;
  }

  animationDelayGuardForLayout = 0;

  if (newAnimation != nil) {
    RELEASE_TO_NIL(animation);
    animation = [newAnimation retain];
    animation.resetState = resetState;
    [animation animate:self];
  } else {
    DebugLog(@"[WARN] Ti.View.animate() (view %@) could not make animation from: %@", self, newAnimation);
  }
}
- (void)animationStarted
{
  animating = YES;
}
- (void)animationCompleted
{
  animating = NO;
}

- (BOOL)animating
{
  return animating;
}

#pragma mark Property Change Support

- (SEL)selectorForProperty:(NSString *)key
{
  NSString *method = [NSString stringWithFormat:@"set%@%@_:", [[key substringToIndex:1] uppercaseString], [key substringFromIndex:1]];
  return NSSelectorFromString(method);
}

- (void)readProxyValuesWithKeys:(id<NSFastEnumeration>)keys
{
  DoProxyDelegateReadValuesWithKeysFromProxy(self, keys, proxy);
}

- (void)propertyChanged:(NSString *)key oldValue:(id)oldValue newValue:(id)newValue proxy:(TiProxy *)proxy_
{
  DoProxyDelegateChangedValuesWithProxy(self, key, oldValue, newValue, proxy_);
}

//Todo: Generalize.
- (void)setKrollValue:(id)value forKey:(NSString *)key withObject:(id)props
{
  if (value == [NSNull null]) {
    value = nil;
  }

  SEL method = SetterWithObjectForKrollProperty(key);
  if ([self respondsToSelector:method]) {
    [self performSelector:method withObject:value withObject:props];
    return;
  }

  method = SetterForKrollProperty(key);
  if ([self respondsToSelector:method]) {
    [self performSelector:method withObject:value];
  }
}

- (void)transferProxy:(TiViewProxy *)newProxy deep:(BOOL)deep
{
  TiViewProxy *oldProxy = (TiViewProxy *)[self proxy];

  // We can safely skip everything if we're transferring to ourself.
  if (oldProxy != newProxy) {
    NSArray *oldProperties = (NSArray *)[oldProxy allKeys];
    NSArray *newProperties = (NSArray *)[newProxy allKeys];
    NSArray *keySequence = [newProxy keySequence];
    [oldProxy retain];
    [self retain];

    [newProxy setReproxying:YES];

    [oldProxy setView:nil];
    [newProxy setView:self];
    [self setProxy:[newProxy retain]];

    //The important sequence first:
    for (NSString *thisKey in keySequence) {
      id newValue = [newProxy valueForKey:thisKey];
      id oldValue = [oldProxy valueForKey:thisKey];
      if ((oldValue != newValue) && ![oldValue isEqual:newValue]) {
        [self setKrollValue:newValue forKey:thisKey withObject:nil];
      }
    }

    for (NSString *thisKey in oldProperties) {
      if ([newProperties containsObject:thisKey] || [keySequence containsObject:thisKey]) {
        continue;
      }
      [self setKrollValue:nil forKey:thisKey withObject:nil];
    }

    for (NSString *thisKey in newProperties) {
      if ([keySequence containsObject:thisKey]) {
        continue;
      }

      // Always set the new value, even if 'equal' - some view setters (as in UIImageView)
      // use internal voodoo to determine what to display.
      // TODO: We may be able to take this out once the imageView.url property is taken out, and change it back to an equality test.
      id newValue = [newProxy valueForUndefinedKey:thisKey];
      [self setKrollValue:newValue forKey:thisKey withObject:nil];
    }

    if (deep) {
      NSArray *subProxies = [newProxy children];
      [[oldProxy children] enumerateObjectsUsingBlock:^(TiViewProxy *oldSubProxy, NSUInteger idx, BOOL *stop) {
        TiViewProxy *newSubProxy = idx < [subProxies count] ? [subProxies objectAtIndex:idx] : nil;
        [[oldSubProxy view] transferProxy:newSubProxy deep:YES];
      }];
    }
    [oldProxy release];

    [newProxy setReproxying:NO];
    [self release];
  }
}

- (BOOL)validateTransferToProxy:(TiViewProxy *)newProxy deep:(BOOL)deep
{
  TiViewProxy *oldProxy = (TiViewProxy *)[self proxy];

  if (oldProxy == newProxy) {
    return YES;
  }
  if (![newProxy isMemberOfClass:[oldProxy class]]) {
    return NO;
  }

  __block BOOL result = YES;
  if (deep) {
    NSArray *subProxies = [newProxy children];
    NSArray *oldSubProxies = [oldProxy children];
    if ([subProxies count] != [oldSubProxies count]) {
      return NO;
    }
    [oldSubProxies enumerateObjectsUsingBlock:^(TiViewProxy *oldSubProxy, NSUInteger idx, BOOL *stop) {
      TiViewProxy *newSubProxy = [subProxies objectAtIndex:idx];
      result = [[oldSubProxy view] validateTransferToProxy:newSubProxy deep:YES];
      if (!result) {
        *stop = YES;
      }
    }];
  }
  return result;
}

- (id)proxyValueForKey:(NSString *)key
{
  return [proxy valueForKey:key];
}

#pragma mark First Responder delegation

- (void)makeRootViewFirstResponder
{
  [[[TiApp controller] view] becomeFirstResponder];
}

#pragma mark Recognizers

- (UITapGestureRecognizer *)singleTapRecognizer
{
  if (singleTapRecognizer == nil) {
    singleTapRecognizer = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(recognizedTap:)];
    [self configureGestureRecognizer:singleTapRecognizer];
    [self addGestureRecognizer:singleTapRecognizer];
    if (doubleTapRecognizer != nil) {
      [singleTapRecognizer requireGestureRecognizerToFail:doubleTapRecognizer];
    }
  }
  singleTapRecognizer.delegate = self;
  return singleTapRecognizer;
}

- (UITapGestureRecognizer *)doubleTapRecognizer
{
  if (doubleTapRecognizer == nil) {
    doubleTapRecognizer = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(recognizedTap:)];
    [doubleTapRecognizer setNumberOfTapsRequired:2];
    [self configureGestureRecognizer:doubleTapRecognizer];
    [self addGestureRecognizer:doubleTapRecognizer];

    if (singleTapRecognizer != nil) {
      [singleTapRecognizer requireGestureRecognizerToFail:doubleTapRecognizer];
    }
  }
  doubleTapRecognizer.delegate = self;
  return doubleTapRecognizer;
}

- (UITapGestureRecognizer *)twoFingerTapRecognizer
{
  if (twoFingerTapRecognizer == nil) {
    twoFingerTapRecognizer = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(recognizedTap:)];
    [twoFingerTapRecognizer setNumberOfTouchesRequired:2];
    [self configureGestureRecognizer:twoFingerTapRecognizer];
    [self addGestureRecognizer:twoFingerTapRecognizer];
  }
  twoFingerTapRecognizer.delegate = self;
  return twoFingerTapRecognizer;
}

- (UIPinchGestureRecognizer *)pinchRecognizer
{
  if (pinchRecognizer == nil) {
    pinchRecognizer = [[UIPinchGestureRecognizer alloc] initWithTarget:self action:@selector(recognizedPinch:)];
    [self configureGestureRecognizer:pinchRecognizer];
    [self addGestureRecognizer:pinchRecognizer];
  }
  pinchRecognizer.delegate = self;
  return pinchRecognizer;
}

- (UISwipeGestureRecognizer *)leftSwipeRecognizer
{
  if (leftSwipeRecognizer == nil) {
    leftSwipeRecognizer = [[UISwipeGestureRecognizer alloc] initWithTarget:self action:@selector(recognizedSwipe:)];
    [leftSwipeRecognizer setDirection:UISwipeGestureRecognizerDirectionLeft];
    [self configureGestureRecognizer:leftSwipeRecognizer];
    [self addGestureRecognizer:leftSwipeRecognizer];
  }
  leftSwipeRecognizer.delegate = self;
  return leftSwipeRecognizer;
}

- (UISwipeGestureRecognizer *)rightSwipeRecognizer
{
  if (rightSwipeRecognizer == nil) {
    rightSwipeRecognizer = [[UISwipeGestureRecognizer alloc] initWithTarget:self action:@selector(recognizedSwipe:)];
    [rightSwipeRecognizer setDirection:UISwipeGestureRecognizerDirectionRight];
    [self configureGestureRecognizer:rightSwipeRecognizer];
    [self addGestureRecognizer:rightSwipeRecognizer];
  }
  rightSwipeRecognizer.delegate = self;
  return rightSwipeRecognizer;
}
- (UISwipeGestureRecognizer *)upSwipeRecognizer
{
  if (upSwipeRecognizer == nil) {
    upSwipeRecognizer = [[UISwipeGestureRecognizer alloc] initWithTarget:self action:@selector(recognizedSwipe:)];
    [upSwipeRecognizer setDirection:UISwipeGestureRecognizerDirectionUp];
    [self configureGestureRecognizer:upSwipeRecognizer];
    [self addGestureRecognizer:upSwipeRecognizer];
  }
  upSwipeRecognizer.delegate = self;
  return upSwipeRecognizer;
}
- (UISwipeGestureRecognizer *)downSwipeRecognizer
{
  if (downSwipeRecognizer == nil) {
    downSwipeRecognizer = [[UISwipeGestureRecognizer alloc] initWithTarget:self action:@selector(recognizedSwipe:)];
    [downSwipeRecognizer setDirection:UISwipeGestureRecognizerDirectionDown];
    [self configureGestureRecognizer:downSwipeRecognizer];
    [self addGestureRecognizer:downSwipeRecognizer];
  }
  downSwipeRecognizer.delegate = self;
  return downSwipeRecognizer;
}

- (UILongPressGestureRecognizer *)longPressRecognizer
{
  if (longPressRecognizer == nil) {
    longPressRecognizer = [[UILongPressGestureRecognizer alloc] initWithTarget:self action:@selector(recognizedLongPress:)];
    [self configureGestureRecognizer:longPressRecognizer];
    [self addGestureRecognizer:longPressRecognizer];
  }
  longPressRecognizer.delegate = self;
  return longPressRecognizer;
}

- (void)recognizedTap:(UITapGestureRecognizer *)recognizer
{
  CGPoint tapPoint = [recognizer locationInView:self];
  NSDictionary *event = [TiUtils pointToDictionary:tapPoint];

  if ([recognizer numberOfTouchesRequired] == 2) {
    [proxy fireEvent:@"twofingertap" withObject:event];
  } else if ([recognizer numberOfTapsRequired] == 2) {
    //Because double-tap suppresses touchStart and double-click, we must do this:
    if ([proxy _hasListeners:@"touchstart"]) {
      [proxy fireEvent:@"touchstart" withObject:event propagate:YES];
    }
    if ([proxy _hasListeners:@"dblclick"]) {
      [proxy fireEvent:@"dblclick" withObject:event propagate:YES];
    }
    [proxy fireEvent:@"doubletap" withObject:event];
  } else {
    [proxy fireEvent:@"singletap" withObject:event];
  }
}

- (void)recognizedPinch:(UIPinchGestureRecognizer *)recognizer
{
  NSDictionary *event = [NSDictionary dictionaryWithObjectsAndKeys:
                                          NUMDOUBLE(recognizer.scale), @"scale",
                                      NUMDOUBLE(recognizer.velocity), @"velocity",
                                      nil];
  [self.proxy fireEvent:@"pinch" withObject:event];
}

- (void)recognizedLongPress:(UILongPressGestureRecognizer *)recognizer
{
  if ([recognizer state] == UIGestureRecognizerStateBegan) {
    CGPoint p = [recognizer locationInView:self];
    NSDictionary *event = [NSDictionary dictionaryWithObjectsAndKeys:
                                            NUMFLOAT(p.x), @"x",
                                        NUMFLOAT(p.y), @"y",
                                        nil];
    [self.proxy fireEvent:@"longpress" withObject:event];
  }
}

- (void)recognizedSwipe:(UISwipeGestureRecognizer *)recognizer
{
  NSString *swipeString;
  switch ([recognizer direction]) {
  case UISwipeGestureRecognizerDirectionUp:
    swipeString = @"up";
    break;
  case UISwipeGestureRecognizerDirectionDown:
    swipeString = @"down";
    break;
  case UISwipeGestureRecognizerDirectionLeft:
    swipeString = @"left";
    break;
  case UISwipeGestureRecognizerDirectionRight:
    swipeString = @"right";
    break;
  default:
    swipeString = @"unknown";
    break;
  }

  CGPoint tapPoint = [recognizer locationInView:self];
  NSMutableDictionary *event = [[TiUtils pointToDictionary:tapPoint] mutableCopy];
  [event setValue:swipeString forKey:@"direction"];
  [proxy fireEvent:@"swipe" withObject:event];
  [event release];
}

#pragma mark Touch Events

- (BOOL)interactionDefault
{
  return YES;
}

- (BOOL)interactionEnabled
{
  return self.userInteractionEnabled;
}

- (BOOL)hasTouchableListener
{
  return handlesTouches;
}

- (UIView *)hitTest:(CGPoint)point withEvent:(UIEvent *)event
{
  BOOL hasTouchListeners = [self hasTouchableListener];

  // if we don't have any touch listeners, see if interaction should
  // be handled at all.. NOTE: we don't turn off the views interactionEnabled
  // property since we need special handling ourselves and if we turn it off
  // on the view, we'd never get this event
  if (!hasTouchListeners && ![self interactionEnabled]) {
    return nil;
  }

  // OK, this is problematic because of the situation where:
  // touchDelegate --> view --> button
  // The touch never reaches the button, because the touchDelegate is as deep as the touch goes.

  /*
	// delegate to our touch delegate if we're hit but it's not for us
	if (hasTouchListeners==NO && touchDelegate!=nil)
	{
		return touchDelegate;
	}
     */

  return [super hitTest:point withEvent:event];
}

// TODO: Revisit this design decision in post-1.3.0
- (void)handleControlEvents:(UIControlEvents)events
{
  // For subclasses (esp. buttons) to override when they have event handlers.
  TiViewProxy *parentProxy = [(TiViewProxy *)proxy parent];
  if ([parentProxy viewAttached] && [parentProxy canHaveControllerParent]) {
    [[parentProxy view] handleControlEvents:events];
  }
}

// For subclasses
- (BOOL)touchedContentViewWithEvent:(UIEvent *)event
{
  return NO;
}

- (void)touchesBegan:(NSSet *)touches withEvent:(UIEvent *)event
{
  if ([[event touchesForView:self] count] > 0 || [self touchedContentViewWithEvent:event]) {
    [self processTouchesBegan:touches withEvent:event];
  }
  [super touchesBegan:touches withEvent:event];
}

- (void)processTouchesBegan:(NSSet *)touches withEvent:(UIEvent *)event
{
  if (handlesTouches) {
    if ([proxy _hasListeners:@"touchstart"]) {
      UITouch *touch = [touches anyObject];
      NSDictionary *evt = [NSMutableDictionary dictionaryWithDictionary:[TiUtils touchPropertiesToDictionary:touch andView:self]];
      [proxy fireEvent:@"touchstart" withObject:evt propagate:YES];
      [self handleControlEvents:UIControlEventTouchDown];
    }
  }
}

- (void)touchesMoved:(NSSet *)touches withEvent:(UIEvent *)event
{
  if ([[event touchesForView:self] count] > 0 || [self touchedContentViewWithEvent:event]) {
    [self processTouchesMoved:touches withEvent:event];
  }
  [super touchesMoved:touches withEvent:event];
}

- (void)processTouchesMoved:(NSSet *)touches withEvent:(UIEvent *)event
{
  if (handlesTouches) {
    if ([proxy _hasListeners:@"touchmove"]) {
      UITouch *touch = [touches anyObject];
      NSDictionary *evt = [NSMutableDictionary dictionaryWithDictionary:[TiUtils touchPropertiesToDictionary:touch andView:self]];
      [proxy fireEvent:@"touchmove" withObject:evt propagate:YES];
    }
  }
}

- (void)touchesEnded:(NSSet *)touches withEvent:(UIEvent *)event
{
  if ([[event touchesForView:self] count] > 0 || [self touchedContentViewWithEvent:event]) {
    [self processTouchesEnded:touches withEvent:event];
  }
  [super touchesEnded:touches withEvent:event];
}

- (void)processTouchesEnded:(NSSet *)touches withEvent:(UIEvent *)event
{
  if (handlesTouches) {
    UITouch *touch = [touches anyObject];
    NSDictionary *evt = [NSMutableDictionary dictionaryWithDictionary:[TiUtils touchPropertiesToDictionary:touch andView:self]];

    if ([proxy _hasListeners:@"touchend"]) {
      [proxy fireEvent:@"touchend" withObject:evt propagate:YES];
      [self handleControlEvents:UIControlEventTouchCancel];
    }

    // Click handling is special; don't propagate if we have a delegate,
    // but DO invoke the touch delegate.
    // clicks should also be handled by any control the view is embedded in.
    if ([touch tapCount] == 1 && [proxy _hasListeners:@"click"]) {
      if (touchDelegate == nil) {
        [proxy fireEvent:@"click" withObject:evt propagate:YES];
        return;
      }
    } else if ([touch tapCount] == 2 && [proxy _hasListeners:@"dblclick"]) {
      [proxy fireEvent:@"dblclick" withObject:evt propagate:YES];
      return;
    }
  }
}

- (void)touchesCancelled:(NSSet *)touches withEvent:(UIEvent *)event
{
  if ([[event touchesForView:self] count] > 0 || [self touchedContentViewWithEvent:event]) {
    [self processTouchesCancelled:touches withEvent:event];
  }
  [super touchesCancelled:touches withEvent:event];
}

- (void)processTouchesCancelled:(NSSet *)touches withEvent:(UIEvent *)event
{
  if (handlesTouches) {
    if ([proxy _hasListeners:@"touchcancel"]) {
      UITouch *touch = [touches anyObject];
      NSDictionary *evt = [NSMutableDictionary dictionaryWithDictionary:[TiUtils touchPropertiesToDictionary:touch andView:self]];
      [proxy fireEvent:@"touchcancel" withObject:evt propagate:YES];
    }
  }
}

- (void)processKeyPressed:(NSString *)key
{
  if ([self.proxy _hasListeners:@"keypressed"]) {
    [self.proxy fireEvent:@"keypressed" withObject:@{ @"keyCode" : key }];
  }
}

#pragma mark Listener management

- (void)removeGestureRecognizerOfClass:(Class)c
{
  for (UIGestureRecognizer *r in [self gestureRecognizers]) {
    if ([r isKindOfClass:c]) {
      [self removeGestureRecognizer:r];
      break;
    }
  }
}

- (void)configureGestureRecognizer:(UIGestureRecognizer *)gestureRecognizer
{
  [gestureRecognizer setDelaysTouchesBegan:NO];
  [gestureRecognizer setDelaysTouchesEnded:NO];
  [gestureRecognizer setCancelsTouchesInView:NO];
}

- (UIGestureRecognizer *)gestureRecognizerForEvent:(NSString *)event
{
  if ([event isEqualToString:@"singletap"]) {
    return [self singleTapRecognizer];
  }
  if ([event isEqualToString:@"doubletap"]) {
    return [self doubleTapRecognizer];
  }
  if ([event isEqualToString:@"twofingertap"]) {
    return [self twoFingerTapRecognizer];
  }
  if ([event isEqualToString:@"lswipe"]) {
    return [self leftSwipeRecognizer];
  }
  if ([event isEqualToString:@"rswipe"]) {
    return [self rightSwipeRecognizer];
  }
  if ([event isEqualToString:@"uswipe"]) {
    return [self upSwipeRecognizer];
  }
  if ([event isEqualToString:@"dswipe"]) {
    return [self downSwipeRecognizer];
  }
  if ([event isEqualToString:@"pinch"]) {
    return [self pinchRecognizer];
  }
  if ([event isEqualToString:@"longpress"]) {
    return [self longPressRecognizer];
  }
  return nil;
}

- (void)handleListenerAddedWithEvent:(NSString *)event
{
  ENSURE_UI_THREAD_1_ARG(event);
  [self updateTouchHandling];
  if ([event isEqualToString:@"swipe"]) {
    [[self gestureRecognizerForEvent:@"uswipe"] setEnabled:YES];
    [[self gestureRecognizerForEvent:@"dswipe"] setEnabled:YES];
    [[self gestureRecognizerForEvent:@"rswipe"] setEnabled:YES];
    [[self gestureRecognizerForEvent:@"lswipe"] setEnabled:YES];
  } else {
    [[self gestureRecognizerForEvent:event] setEnabled:YES];
  }
}

- (void)handleListenerRemovedWithEvent:(NSString *)event
{
  ENSURE_UI_THREAD_1_ARG(event);
  // unfortunately on a remove, we have to check all of them
  // since we might be removing one but we still have others

  [self updateTouchHandling];
  if ([event isEqualToString:@"swipe"]) {
    [[self gestureRecognizerForEvent:@"uswipe"] setEnabled:NO];
    [[self gestureRecognizerForEvent:@"dswipe"] setEnabled:NO];
    [[self gestureRecognizerForEvent:@"rswipe"] setEnabled:NO];
    [[self gestureRecognizerForEvent:@"lswipe"] setEnabled:NO];
  } else {
    [[self gestureRecognizerForEvent:event] setEnabled:NO];
  }
}

- (void)listenerAdded:(NSString *)event count:(int)count
{
  if (count == 1 && [self viewSupportsBaseTouchEvents]) {
    [self handleListenerAddedWithEvent:event];
  }
}

- (void)listenerRemoved:(NSString *)event count:(int)count
{
  if (count == 0) {
    [self handleListenerRemovedWithEvent:event];
  }
}

- (void)sanitycheckListeners //TODO: This can be optimized and unwound later.
{
  if (listenerArray == nil) {
    listenerArray = [[NSArray alloc] initWithObjects:@"singletap",
                                     @"doubletap", @"twofingertap", @"swipe", @"pinch", @"longpress", nil];
  }
  for (NSString *eventName in listenerArray) {
    if ([proxy _hasListeners:eventName]) {
      [self handleListenerAddedWithEvent:eventName];
    }
  }
}

@end
