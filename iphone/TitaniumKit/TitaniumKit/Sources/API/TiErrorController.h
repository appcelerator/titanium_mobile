/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#import <UIKit/UIKit.h>

@class TiScriptError;

@interface TiErrorNavigationController : UINavigationController

@end

@interface TiErrorController : UIViewController {

  NSString *error;
  TiScriptError *scriptError;
  UIScrollView *scrollView;
  UITextView *messageView;
  UIButton *continueButton;
}

- (id)initWithError:(NSString *)error_;
- (id)initWithScriptError:(TiScriptError *)scriptError_;

@end
