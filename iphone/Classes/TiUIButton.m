/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UIBUTTON

#import "TiUIButton.h"
#import "TiUIButtonProxy.h"

#import "TiButtonUtil.h"
#import <TitaniumKit/ImageLoader.h>
#import <TitaniumKit/TiUIView.h>
#import <TitaniumKit/TiUtils.h>

#ifdef USE_TI_UIATTRIBUTEDSTRING
#import "TiUIAttributedStringProxy.h"
#endif

@implementation TiUIButton

#pragma mark Internal

#ifdef TI_USE_AUTOLAYOUT
- (void)initializeTiLayoutView
{
  [super initializeTiLayoutView];
  [self setDefaultHeight:TiDimensionAutoSize];
  [self setDefaultWidth:TiDimensionAutoSize];
}
#endif

- (void)dealloc
{
  [button removeTarget:self action:NULL forControlEvents:UIControlEventAllTouchEvents];
  RELEASE_TO_NIL(button);
#ifndef TI_USE_AUTOLAYOUT
  RELEASE_TO_NIL(viewGroupWrapper);
#endif
  RELEASE_TO_NIL(backgroundImageCache)
  RELEASE_TO_NIL(backgroundImageUnstretchedCache);
  [super dealloc];
}

- (UIView *)hitTest:(CGPoint)point withEvent:(UIEvent *)event
{
  UIView *superResult = [super hitTest:point withEvent:event];

  if (superResult == nil) {
    return nil;
  }

#ifndef TI_USE_AUTOLAYOUT
  if ((viewGroupWrapper == superResult) || ([superResult isKindOfClass:[TiUIView class]] && ![(TiUIView *)superResult touchEnabled])) {
    return [self button];
  }
#else
  if ([[superResult superview] isKindOfClass:[TiUIView class]] || [superResult isKindOfClass:[TiUIView class]]) {
    return [self button];
  }
#endif
  return superResult;
}

- (BOOL)hasTouchableListener
{
  // since this guy only works with touch events, we always want them
  // just always return YES no matter what listeners we have registered
  return YES;
}

- (void)setHighlighting:(BOOL)isHiglighted
{
#ifndef TI_USE_AUTOLAYOUT
  for (TiUIView *thisView in [viewGroupWrapper subviews])
#else
  for (TiUIView *thisView in [self subviews])
#endif
  {
    if ([thisView respondsToSelector:@selector(setHighlighted:)]) {
      [(id)thisView setHighlighted:isHiglighted];
    }
  }
}

- (void)updateBackgroundImage
{
  CGRect bounds = [self bounds];
#ifndef TI_USE_AUTOLAYOUT
  [button setFrame:bounds];
#endif
  if ((backgroundImageCache == nil) || (bounds.size.width == 0) || (bounds.size.height == 0)) {
    [button setBackgroundImage:nil forState:UIControlStateNormal];
    return;
  }
  CGSize imageSize = [backgroundImageCache size];
  if ((bounds.size.width >= imageSize.width) && (bounds.size.height >= imageSize.height)) {
    [button setBackgroundImage:backgroundImageCache forState:UIControlStateNormal];
  } else {
    //If the bounds are smaller than the image size render it in an imageView and get the image of the view.
    //Should be pretty inexpensive since it happens rarely. TIMOB-9166
    CGSize unstrechedSize = (backgroundImageUnstretchedCache != nil) ? [backgroundImageUnstretchedCache size] : CGSizeZero;
    if (backgroundImageUnstretchedCache == nil || !CGSizeEqualToSize(unstrechedSize, bounds.size)) {
      UIImageView *theView = [[UIImageView alloc] initWithFrame:bounds];
      [theView setImage:backgroundImageCache];
      UIGraphicsBeginImageContextWithOptions(bounds.size, [theView.layer isOpaque], 0.0);
      [theView.layer renderInContext:UIGraphicsGetCurrentContext()];
      UIImage *image = UIGraphicsGetImageFromCurrentImageContext();
      UIGraphicsEndImageContext();
      RELEASE_TO_NIL(backgroundImageUnstretchedCache);
      backgroundImageUnstretchedCache = [image retain];
      [theView release];
    }
    [button setBackgroundImage:backgroundImageUnstretchedCache forState:UIControlStateNormal];
  }

#ifdef TI_USE_AUTOLAYOUT
  [self sendSubviewToBack:button];
#endif
}

- (void)frameSizeChanged:(CGRect)frame bounds:(CGRect)bounds
{
  [super frameSizeChanged:frame bounds:bounds];
  [self updateBackgroundImage];
}

- (void)controlAction:(id)sender forEvent:(UIEvent *)event
{
  UITouch *touch = [[event allTouches] anyObject];
  NSString *fireEvent;
  NSString *fireActionEvent = nil;
  switch (touch.phase) {
  case UITouchPhaseBegan:
    if (touchStarted) {
      return;
    }
    touchStarted = YES;
    fireEvent = @"touchstart";
    break;
  case UITouchPhaseMoved:
    fireEvent = @"touchmove";
    break;
  case UITouchPhaseEnded:
    touchStarted = NO;
    fireEvent = @"touchend";
    if (button.highlighted) {
      fireActionEvent = [touch tapCount] == 1 ? @"click" : ([touch tapCount] == 2 ? @"dblclick" : nil);
    }
    break;
  case UITouchPhaseCancelled:
    touchStarted = NO;
    fireEvent = @"touchcancel";
    break;
  default:
    return;
  }
  [self setHighlighting:button.highlighted];
  NSMutableDictionary *evt = [NSMutableDictionary dictionaryWithDictionary:[TiUtils touchPropertiesToDictionary:touch andView:self]];
  if ((fireActionEvent != nil) && [self.proxy _hasListeners:fireActionEvent]) {
    [self.proxy fireEvent:fireActionEvent withObject:evt];
  }
  if ([self.proxy _hasListeners:fireEvent]) {
    [self.proxy fireEvent:fireEvent withObject:evt];
  }
}

- (UIButton *)button
{
  if (button == nil) {
    UIButtonType defaultType = [self hasImageProperties] ? UIButtonTypeCustom : UIButtonTypeRoundedRect;
    style = [TiUtils intValue:[self.proxy valueForKey:@"style"] def:(int)defaultType];
    UIView *btn = [TiButtonUtil buttonWithType:style];
    button = (UIButton *)[btn retain];
    [self addSubview:button];
    if (style == UIButtonTypeCustom) {
      [button setTitleColor:[UIColor whiteColor] forState:UIControlStateHighlighted];
    }
    [button addTarget:self action:@selector(controlAction:forEvent:) forControlEvents:UIControlEventAllTouchEvents];
    button.exclusiveTouch = YES;
  }
#ifndef TI_USE_AUTOLAYOUT
  if ((viewGroupWrapper != nil) && ([viewGroupWrapper superview] != button)) {
    [viewGroupWrapper setFrame:[button bounds]];
    [button addSubview:viewGroupWrapper];
  }
#endif
  return button;
}

- (BOOL)hasImageProperties
{
  return [self.proxy valueForKey:@"backgroundImage"] || [self.proxy valueForKey:@"backgroundSelectedImage"] || [self.proxy valueForKey:@"backgroundSelectedColor"];
}

- (id)accessibilityElement
{
  return [self button];
}

#ifndef TI_USE_AUTOLAYOUT
- (UIView *)viewGroupWrapper
{
  if (viewGroupWrapper == nil) {
    viewGroupWrapper = [[UIView alloc] initWithFrame:[self bounds]];
    [viewGroupWrapper setAutoresizingMask:UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight];
  }
  if (button != [viewGroupWrapper superview]) {
    if (button != nil) {
      [viewGroupWrapper setFrame:[button bounds]];
      [button addSubview:viewGroupWrapper];
    } else {
      [viewGroupWrapper removeFromSuperview];
    }
  }
  return viewGroupWrapper;
}
#endif
#pragma mark Public APIs

- (void)setStyle_:(id)style_
{
  int s = [TiUtils intValue:style_ def:UIButtonTypeCustom];
  if (s == style) {
    return;
  }
  style = s;

  if (button == nil) {
    return;
  }

  RELEASE_TO_NIL(button);
  [self button];
}

- (void)setImage_:(id)value
{
  UIImage *image = value == nil ? nil : [TiUtils image:value proxy:(TiProxy *)self.proxy];
  if (image != nil) {
    [[self button] setImage:image forState:UIControlStateNormal];
    [(TiViewProxy *)[self proxy] contentsWillChange];
  } else {
    [[self button] setImage:nil forState:UIControlStateNormal];
  }
}

- (void)setEnabled_:(id)value
{
  [[self button] setEnabled:[TiUtils boolValue:value]];
}

- (void)setTitle_:(id)value
{
  [[self button] setTitle:[TiUtils stringValue:value] forState:UIControlStateNormal];
}

- (void)setAttributedString_:(id)arg
{
#ifdef USE_TI_UIATTRIBUTEDSTRING
  ENSURE_SINGLE_ARG(arg, TiUIAttributedStringProxy);
  [[self proxy] replaceValue:arg forKey:@"attributedString" notification:NO];
  [[self button] setAttributedTitle:[arg attributedString] forState:UIControlStateNormal];
  [(TiViewProxy *)[self proxy] contentsWillChange];
#endif
}

- (void)setBackgroundImage_:(id)value
{
  [backgroundImageCache release];
  RELEASE_TO_NIL(backgroundImageUnstretchedCache);
  backgroundImageCache = [[self loadImage:value] retain];
  self.backgroundImage = value;
  [self updateBackgroundImage];
}

- (void)setBackgroundSelectedColor_:(id)value
{
  [[self button] setBackgroundImage:[TiUtils imageWithColor:[[TiUtils colorValue:value] _color]] forState:UIControlStateHighlighted];
}

- (void)setBackgroundSelectedImage_:(id)value
{
  [[self button] setBackgroundImage:[self loadImage:value] forState:UIControlStateHighlighted];
}

- (void)setBackgroundDisabledImage_:(id)value
{
  [[self button] setBackgroundImage:[self loadImage:value] forState:UIControlStateDisabled];
}

- (void)setBackgroundFocusedImage_:(id)value
{
  [[self button] setBackgroundImage:[self loadImage:value] forState:UIControlStateSelected];
}

- (void)setBackgroundColor_:(id)value
{
  if (value != nil) {
    TiColor *color = [TiUtils colorValue:value];
    [[self button] setBackgroundColor:[color _color]];
  }
}

- (void)setFont_:(id)font
{
  if (font != nil) {
    WebFont *f = [TiUtils fontValue:font def:nil];
    [[[self button] titleLabel] setFont:[f font]];
  }
}

- (void)setColor_:(id)color
{
  if (color != nil) {
    TiColor *c = [TiUtils colorValue:color];
    UIButton *b = [self button];
    if (c != nil) {
      [b setTitleColor:[c _color] forState:UIControlStateNormal];
    } else if (b.buttonType == UIButtonTypeCustom) {
      [b setTitleColor:[UIColor blackColor] forState:UIControlStateNormal];
    }
  }
}

- (void)setSelectedColor_:(id)color
{
  if (color != nil) {
    TiColor *selColor = [TiUtils colorValue:color];
    UIButton *b = [self button];
    if (selColor != nil) {
      [b setTitleColor:[selColor _color] forState:UIControlStateHighlighted];
    } else if (b.buttonType == UIButtonTypeCustom) {
      [b setTitleColor:[UIColor whiteColor] forState:UIControlStateHighlighted];
    }
  }
}

- (void)setDisabledColor_:(id)color
{
  if (color != nil) {
    TiColor *selColor = [TiUtils colorValue:color];
    UIButton *b = [self button];
    if (selColor != nil) {
      [b setTitleColor:[selColor _color] forState:UIControlStateDisabled];
    }
  }
}

- (void)setShadowColor_:(id)color
{
  if (color == nil) {
    [[self button] setTitleShadowColor:nil forState:UIControlStateNormal];
  } else {
    color = [TiUtils colorValue:color];
    [[self button] setTitleShadowColor:[(TiColor *)color color] forState:UIControlStateNormal];
  }
}

- (void)setShadowOffset_:(id)value
{
  CGPoint p = [TiUtils pointValue:value];
  CGSize size = { p.x, p.y };
  [[[self button] titleLabel] setShadowOffset:size];
}

- (void)setVerticalAlign_:(id)align
{
  button = [self button];
  UIControlContentVerticalAlignment alignment = UIControlContentVerticalAlignmentCenter;
  if ([align isKindOfClass:[NSString class]]) {
    if ([align isEqualToString:@"top"]) {
      alignment = UIControlContentVerticalAlignmentTop;
    } else if ([align isEqualToString:@"bottom"]) {
      alignment = UIControlContentVerticalAlignmentBottom;
    } else if ([align isEqualToString:@"center"]) {
      alignment = UIControlContentVerticalAlignmentCenter;
    }
  } else {
    alignment = (UIControlContentVerticalAlignment)[TiUtils intValue:align def:(int)UIControlContentVerticalAlignmentCenter];
  }
  UIEdgeInsets inset = [button contentEdgeInsets];

  switch (alignment) {
  case UIControlContentVerticalAlignmentTop: {
    inset.top = 10;
    inset.bottom = 0;
    break;
  }
  case UIControlContentVerticalAlignmentBottom: {
    inset.top = 0;
    inset.bottom = 10;
    break;
  }
  default: {
    inset.top = 0;
    inset.bottom = 0;
    break;
  }
  }
  [button setContentVerticalAlignment:alignment];
  [button setContentEdgeInsets:inset];
}

- (void)setTextAlign_:(id)align
{
  button = [self button];
  NSTextAlignment alignment = [TiUtils textAlignmentValue:align];
  UIControlContentHorizontalAlignment horizontalAlignment = UIControlContentHorizontalAlignmentCenter;

  UIEdgeInsets inset = [button contentEdgeInsets];
  switch (alignment) {
  case NSTextAlignmentLeft: {
    horizontalAlignment = UIControlContentHorizontalAlignmentLeft;
    inset.left = 10;
    inset.right = 0;
    break;
  }
  case NSTextAlignmentRight: {
    horizontalAlignment = UIControlContentHorizontalAlignmentRight;
    inset.right = 10;
    inset.left = 0;
    break;
  }
  default: {
    horizontalAlignment = UIControlContentHorizontalAlignmentCenter;
    inset.right = 0;
    inset.left = 0;
    break;
  }
  }
  [button setContentHorizontalAlignment:horizontalAlignment];
  [button setContentEdgeInsets:inset];
}

- (CGFloat)contentWidthForWidth:(CGFloat)value
{
  return [[self button] sizeThatFits:CGSizeMake(value, 0)].width;
}

- (CGFloat)contentHeightForWidth:(CGFloat)value
{
  return [[self button] sizeThatFits:CGSizeMake(value, 0)].height;
}

@end

#endif
