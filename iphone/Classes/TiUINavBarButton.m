/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UIBUTTON

#import "TiUINavBarButton.h"
#import "TiButtonUtil.h"
#import "TiUIButton.h"
#import "TiUIButtonProxy.h"
#import <TitaniumKit/ImageLoader.h>
#import <TitaniumKit/TiBlob.h>
#import <TitaniumKit/TiUIView.h>
#import <TitaniumKit/TiUtils.h>

#define NAVBAR_MEMORY_DEBUG 0

@implementation TiUINavBarButton
@synthesize proxy;

DEFINE_EXCEPTIONS

#if NAVBAR_MEMORY_DEBUG == 1
- (id)retain
{
  NSLog(@"[DEBUG] Retaining %X (%d)", self, [self retainCount]);
  return [super retain];
}

- (void)release
{
  NSLog(@"[DEBUG] Releasing %X (%d)", self, [self retainCount]);
  [super release];
}
#endif

- (void)dealloc
{
#if NAVBAR_MEMORY_DEBUG == 1
  NSLog(@"[DEBUG] Deallocing %X (%d)", self, [self retainCount]);
#endif
  RELEASE_TO_NIL(activityDelegate);
  [super dealloc];
}

- (void)detachProxy
{
  proxy = nil;
}

- (UIBarButtonItemStyle)style:(TiUIButtonProxy *)proxy_
{
  id value = [proxy_ valueForKey:@"style"];
  if (value == nil) {
    return UIBarButtonItemStylePlain;
  }
  return [TiUtils intValue:value];
}

- (NSString *)title:(TiUIButtonProxy *)proxy_
{
  NSString *title = [TiUtils stringValue:[proxy_ valueForKey:@"title"]];
  return title == nil ? @"" : title;
}

- (id)initWithProxy:(TiUIButtonProxy *)proxy_
{
  self = [super init];
  id systemButton = [proxy_ valueForKey:@"systemButton"];
  if (systemButton != nil) {
    UIBarButtonSystemItem type = [TiUtils intValue:systemButton];
    UIView *button = [TiButtonUtil systemButtonWithType:(int)type];
    if (button != nil) {
      if ([button isKindOfClass:[UIActivityIndicatorView class]]) {
        // we need to wrap our activity indicator view into a UIView that will delegate
        // to our proxy
        activityDelegate = [[TiUIView alloc] initWithFrame:button.frame];
        [activityDelegate addSubview:button];
        activityDelegate.proxy = (TiViewProxy *)proxy_;
        button = activityDelegate;
      }
      self = [super initWithCustomView:button];
      self.target = self;
      self.action = @selector(clicked:);
      if ([button isKindOfClass:[UIControl class]]) {
        [(UIControl *)button addTarget:self action:@selector(clicked:) forControlEvents:UIControlEventTouchUpInside];
      }
    } else {
      self = [super initWithBarButtonSystemItem:type target:self action:@selector(clicked:)];
    }
  } else {
    id image = [proxy_ valueForKey:@"image"];
    id background = [proxy_ valueForKey:@"backgroundImage"];
    if (background != nil) {
      self = [super initWithCustomView:[proxy_ view]];
      self.target = self;
      self.action = @selector(clicked:);
#ifndef TI_USE_AUTOLAYOUT
      if ([[proxy_ view] isKindOfClass:[UIControl class]]) {
        [(UIControl *)[proxy_ view] addTarget:self action:@selector(clicked:) forControlEvents:UIControlEventTouchUpInside];
      }
      //Sanity check. If the view bounds are zero set the bounds to auto dimensions
      CGRect bounds = [[proxy_ view] bounds];
      if (bounds.size.width == 0) {
        CGFloat desiredWidth = [proxy_ autoWidthForSize:CGSizeMake(1000, 1000)];
        bounds.size.width = desiredWidth;
      }
      if (bounds.size.height == 0) {
        CGFloat desiredHeight = [proxy_ autoHeightForSize:CGSizeMake(bounds.size.width, 1000)];
        bounds.size.height = desiredHeight;
      }
      [[proxy_ view] setBounds:bounds];
#endif
    } else if (image != nil) {
      NSURL *url = [TiUtils toURL:image proxy:proxy_];
      UIImage *theimage = [[ImageLoader sharedLoader] loadImmediateStretchableImage:url];
      self = [super initWithImage:theimage style:[self style:proxy_] target:self action:@selector(clicked:)];
    } else {
      self = [super initWithTitle:[self title:proxy_] style:[self style:proxy_] target:self action:@selector(clicked:)];
      self.tintColor = [proxy_ valueForKey:@"color"] ? [TiUtils colorValue:[proxy_ valueForKey:@"color"]].color : [TiUtils colorValue:[proxy_ valueForKey:@"tintColor"]].color;
    }
  }
  proxy = proxy_; // Don't retain

  self.accessibilityLabel = [proxy_ valueForUndefinedKey:@"accessibilityLabel"];
  self.accessibilityValue = [proxy_ valueForUndefinedKey:@"accessibilityValue"];
  self.accessibilityHint = [proxy_ valueForUndefinedKey:@"accessibilityHint"];
  self.accessibilityIdentifier = [TiUtils composeAccessibilityIdentifier:self];

  self.width = [TiUtils floatValue:[proxy_ valueForKey:@"width"] def:0.0];
  //A width of 0 is treated as Auto by the iPhone OS, so this is safe.
  // we need to listen manually to proxy change events if we want to be
  // able to change them dynamically
  proxy.modelDelegate = self;

  // we need to manually check for this property on init
  id enabled = [proxy valueForKey:@"enabled"];
  if (enabled != nil) {
    [self performSelector:@selector(setEnabled_:) withObject:enabled];
  }

  return self;
}

- (void)clicked:(id)event
{
  if ([proxy _hasListeners:@"click"]) {
    [proxy fireEvent:@"click" withObject:nil];
  }
}

- (void)setTitle_:(id)obj
{
  [super setTitle:[TiUtils stringValue:obj]];
}

- (void)setImage_:(id)obj
{
  if (obj == nil) {
    [super setImage:nil];
    return;
  }

  if ([obj isKindOfClass:[TiBlob class]]) {
    [super setImage:[(TiBlob *)obj image]];
  } else if ([obj isKindOfClass:[NSString class]]) {
    [super setImage:[TiUtils image:obj proxy:proxy]];
  } else if ([obj isKindOfClass:[UIImage class]]) {
    [super setImage:obj];
  } else {
    [self throwException:[NSString stringWithFormat:@"Unexpected object of type %@ provided for image", [obj class]]
               subreason:nil
                location:CODELOCATION];
  }
}

- (void)setWidth_:(id)obj
{
  CGFloat width = [TiUtils floatValue:obj];
  [self setWidth:width];
}

- (void)setEnabled_:(id)value
{
  UIView *buttonView = [self customView];

  if ([buttonView isKindOfClass:[TiUIButton class]]) {
    //TODO: when using a TiUIButton, for some reason the setEnabled doesn't work.
    //So we're just going to let it do all the work of updating.
    [(TiUIButton *)buttonView setEnabled_:value];
  } else {
    BOOL enabled = [TiUtils boolValue:value];
    [self setEnabled:enabled];
  }
}

- (void)propertyChanged:(NSString *)key oldValue:(id)oldValue newValue:(id)newValue proxy:(TiProxy *)proxy_
{
  // Take into account whether or not we're a custom view
  id changeView = (self.customView != nil) ? (id)self.customView : (id)self;

  if ([key isEqualToString:@"title"]) {
    TiThreadPerformOnMainThread(
        ^{
          [changeView setTitle_:newValue];
        },
        NO);
    return;
  }
  if ([key isEqualToString:@"image"]) {
    TiThreadPerformOnMainThread(
        ^{
          [changeView setImage_:newValue];
        },
        NO);
    return;
  }
  if ([key isEqualToString:@"width"]) {
    TiThreadPerformOnMainThread(
        ^{
          [changeView setWidth_:newValue];
        },
        NO);
    return;
  }
  if ([key isEqualToString:@"enabled"]) {
    TiThreadPerformOnMainThread(
        ^{
          [self setEnabled_:newValue];
        },
        NO);
    return;
  }
}

@end

#endif
