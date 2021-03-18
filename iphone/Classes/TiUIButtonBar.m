/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2014 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#import "TiUIButtonBar.h"
#import <TitaniumKit/TiUtils.h>
#import <TitaniumKit/TiViewProxy.h>
#import <TitaniumKit/Webcolor.h>

@implementation TiUIButtonBar

#ifdef TI_USE_AUTOLAYOUT
- (void)initializeTiLayoutView
{
  [super initializeTiLayoutView];
  [self setDefaultHeight:TiDimensionAutoSize];
  [self setDefaultWidth:TiDimensionAutoSize];
}
#endif

- (id)init
{
  self = [super init];
  if (self != nil) {
    selectedIndex = -1;
  }
  return self;
}

- (void)dealloc
{
  RELEASE_TO_NIL(segmentedControl);
  [super dealloc];
}

- (BOOL)hasTouchableListener
{
  // since this guy only works with touch events, we always want them
  // just always return YES no matter what listeners we have registered
  return YES;
}

- (UISegmentedControl *)segmentedControl
{
  if (segmentedControl == nil) {
    CGRect ourBoundsRect = [self bounds];
    segmentedControl = [[UISegmentedControl alloc] initWithFrame:ourBoundsRect];
    [segmentedControl setAutoresizingMask:UIViewAutoresizingFlexibleHeight | UIViewAutoresizingFlexibleWidth];
    [segmentedControl addTarget:self action:@selector(onSegmentChange:) forControlEvents:UIControlEventValueChanged];
    [self addSubview:segmentedControl];
  }
  return segmentedControl;
}

- (id)accessibilityElement
{
  return [self segmentedControl];
}

- (UIColor *)reverseColorOf:(UIColor *)oldColor
{
  CGColorRef oldCGColor = oldColor.CGColor;

  int numberOfComponents = (int)CGColorGetNumberOfComponents(oldCGColor);
  // can not invert - the only component is the alpha
  if (numberOfComponents == 1) {
    return [UIColor colorWithCGColor:oldCGColor];
  }

  const CGFloat *oldComponentColors = CGColorGetComponents(oldCGColor);
  CGFloat newComponentColors[numberOfComponents];

  int i = numberOfComponents - 1;
  newComponentColors[i] = oldComponentColors[i]; // alpha
  while (--i >= 0) {
    newComponentColors[i] = 1 - oldComponentColors[i];
  }

  CGColorRef newCGColor = CGColorCreate(CGColorGetColorSpace(oldCGColor), newComponentColors);
  UIColor *newColor = [UIColor colorWithCGColor:newCGColor];
  CGColorRelease(newCGColor);

  //For the GRAY colors 'Middle level colors'
  CGFloat white = 0;
  [oldColor getWhite:&white alpha:nil];

  if (white > 0.3 && white < 0.67) {
    if (white >= 0.5)
      newColor = [UIColor darkGrayColor];
    else if (white < 0.5)
      newColor = [UIColor blackColor];
  }
  return newColor;
}

// For regression #1880.  Because there are essentially TWO kinds of 'width' going on with tabbed/button bars
// (width of all elements, width of the proxy) we assume that if the user has set the width of the bar completely,
// AND the width of the proxy is undefined, they want magic!
- (void)frameSizeChanged:(CGRect)frame_ bounds:(CGRect)bounds_
{
#ifndef TI_USE_AUTOLAYOUT
  // Treat 'undefined' like 'auto' when we have an available width for ALL control segments
  UISegmentedControl *ourControl = [self segmentedControl];
  if (controlSpecifiedWidth && TiDimensionIsUndefined([(TiViewProxy *)[self proxy] layoutProperties]->width)) {
    CGRect controlBounds = bounds_;
    controlBounds.size = [ourControl sizeThatFits:CGSizeZero];
    [ourControl setBounds:controlBounds];
  } else {
    [ourControl setFrame:bounds_];
  }
#endif
  [super frameSizeChanged:frame_
                   bounds:bounds_];
}

- (void)setTabbedBar:(BOOL)newIsTabbed;
{
  [[self segmentedControl] setMomentary:!newIsTabbed];
}

- (void)setTintColor_:(id)value
{
  UIColor *color = [[TiUtils colorValue:value] color];

  if ([TiUtils isIOSVersionLower:@"13.0"]) {
    [[self segmentedControl] setTintColor:color];
    return;
  }

  UIColor *newColor = [self reverseColorOf:color];
  [[self segmentedControl] setTitleTextAttributes:@{ NSForegroundColorAttributeName : color } forState:UIControlStateNormal];
  [[self segmentedControl] setTitleTextAttributes:@{ NSForegroundColorAttributeName : newColor } forState:UIControlStateSelected];

  [[self segmentedControl] setSelectedSegmentTintColor:color];
}

- (void)setBackgroundColor_:(id)value
{
  TiColor *color = [TiUtils colorValue:value];
  [[self segmentedControl] setBackgroundColor:[color _color]];
}

- (void)setTextColor_:(id)value
{
  UIColor *color = [[TiUtils colorValue:value] color];

  [[self segmentedControl] setTitleTextAttributes:@{ NSForegroundColorAttributeName : color } forState:UIControlStateNormal];
}

- (void)setSelectedTextColor_:(id)value
{
  UIColor *color = [[TiUtils colorValue:value] color];

  [[self segmentedControl] setTitleTextAttributes:@{ NSForegroundColorAttributeName : color } forState:UIControlStateSelected];
}

- (void)setSelectedButtonColor_:(id)value
{
  if (![TiUtils isIOSVersionOrGreater:@"13.0"]) {
    return;
  }
  UIColor *color = [[TiUtils colorValue:value] color];
  [[self segmentedControl] setSelectedSegmentTintColor:color];
}

- (void)setIndex_:(id)value
{
  selectedIndex = [TiUtils intValue:value def:-1];
  if ([[self segmentedControl] isMomentary]) {
    DEPRECATED_REMOVED(@"UI.ButtonBar.index", @"8.0.0", @"9.0.0");
    selectedIndex = -1;
    [self.proxy replaceValue:NUMINT(-1) forKey:@"index" notification:NO];
  } else {
    [[self segmentedControl] setSelectedSegmentIndex:selectedIndex];
    [segmentedControl setNeedsLayout];
  }
}

- (void)setStyle_:(id)value
{
  DebugLog(@"[WARN] The style property has been deprecated in 3.4.2 and no longer has any effect");
}

- (void)setLabels_:(id)value
{
  [[self segmentedControl] removeAllSegments];
  if (IS_NULL_OR_NIL(value)) {
    return;
  }
  ENSURE_ARRAY(value);

  int thisSegmentIndex = 0;
  controlSpecifiedWidth = YES;
  for (id thisSegmentEntry in value) {
    NSString *thisSegmentTitle = [TiUtils stringValue:thisSegmentEntry];
    UIImage *thisSegmentImage = nil;
    CGFloat thisSegmentWidth = 0;
    BOOL thisSegmentEnabled = YES;
    NSString *thisSegmentAccessibilityLabel = nil;

    if ([thisSegmentEntry isKindOfClass:[NSDictionary class]]) {
      thisSegmentTitle = [TiUtils stringValue:@"title" properties:thisSegmentEntry];
      thisSegmentImage = [TiUtils image:[thisSegmentEntry objectForKey:@"image"] proxy:[self proxy]];
      thisSegmentWidth = [TiUtils floatValue:@"width" properties:thisSegmentEntry];
      thisSegmentEnabled = [TiUtils boolValue:@"enabled" properties:thisSegmentEntry def:YES];
      thisSegmentAccessibilityLabel = [TiUtils stringValue:@"accessibilityLabel" properties:thisSegmentEntry];
    }

    if (thisSegmentImage != nil) {
      if (thisSegmentAccessibilityLabel != nil) {
        thisSegmentImage.accessibilityLabel = thisSegmentAccessibilityLabel;
      }
      thisSegmentImage = [thisSegmentImage imageWithRenderingMode:UIImageRenderingModeAlwaysOriginal];
      [segmentedControl insertSegmentWithImage:thisSegmentImage atIndex:thisSegmentIndex animated:NO];
    } else {
      if (thisSegmentTitle == nil) {
        thisSegmentTitle = @"";
      }
      if (thisSegmentAccessibilityLabel != nil) {
        thisSegmentTitle.accessibilityLabel = thisSegmentAccessibilityLabel;
      }
      [segmentedControl insertSegmentWithTitle:thisSegmentTitle atIndex:thisSegmentIndex animated:NO];
    }

    [segmentedControl setWidth:thisSegmentWidth forSegmentAtIndex:thisSegmentIndex];
    [segmentedControl setEnabled:thisSegmentEnabled forSegmentAtIndex:thisSegmentIndex];
    thisSegmentIndex++;
    controlSpecifiedWidth &= (thisSegmentWidth != 0.0);
  }

  if (![segmentedControl isMomentary]) {
    if ((selectedIndex < 0) && (segmentedControl.numberOfSegments > 0)) {
      selectedIndex = 0;
    }
    [segmentedControl setSelectedSegmentIndex:selectedIndex];
  }
}

- (IBAction)onSegmentChange:(id)sender
{
  NSInteger newIndex = [(UISegmentedControl *)sender selectedSegmentIndex];

  [self.proxy replaceValue:NUMINTEGER(newIndex) forKey:@"index" notification:NO];

  if (newIndex == selectedIndex) {
    return;
  }

  selectedIndex = newIndex;

  if ([(UISegmentedControl *)sender isMomentary]) {
    selectedIndex = -1;
    [self.proxy replaceValue:NUMINT(-1) forKey:@"index" notification:NO];
  }

  if ([self.proxy _hasListeners:@"click"]) {
    NSDictionary *event = [NSDictionary dictionaryWithObject:NUMINTEGER(newIndex) forKey:@"index"];
    [self.proxy fireEvent:@"click" withObject:event];
  }
}

- (CGFloat)contentWidthForWidth:(CGFloat)suggestedWidth
{
  return [[self segmentedControl] sizeThatFits:CGSizeZero].width;
}

- (CGFloat)contentHeightForWidth:(CGFloat)width
{
  return [[self segmentedControl] sizeThatFits:CGSizeZero].height;
}

@end
