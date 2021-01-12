/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2019 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 * 
 * WARNING: This is generated code. Modify at your own risk and without support.
 */

#import "TiErrorController.h"
#import "TiApp.h"
#import "TiBase.h"
#import "TiExceptionHandler.h"
#import "TiUtils.h"
#import <QuartzCore/QuartzCore.h>

#import <TitaniumKit/TitaniumKit-Swift.h>

@implementation TiErrorNavigationController

- (UIViewController *)childViewControllerForHomeIndicatorAutoHidden
{
  return self.topViewController;
}

@end

@implementation TiErrorController

- (id)initWithError:(NSString *)error_
{
  if (self = [super init]) {
    error = [error_ retain];
  }
  return self;
}

- (instancetype)initWithScriptError:(TiScriptError *)scriptError_
{
  if (self = [super init]) {
    error = [scriptError_.description retain];
    scriptError = [scriptError_ retain];
  }
  return self;
}

- (void)dealloc
{
  RELEASE_TO_NIL(scrollView);
  RELEASE_TO_NIL(messageView);
  RELEASE_TO_NIL(error);
  RELEASE_TO_NIL(scriptError);
  [super dealloc];
}

- (void)dismiss:(id)sender
{
  [[TiApp app] hideModalController:self.navigationController animated:YES];
}

- (void)loadView
{
  [super loadView];

  // configure view controller
  UIColor *errorColor = UIColor.redColor;
  self.navigationItem.title = NSLocalizedString(@"Application Error", nil);
  self.navigationController.navigationBar.titleTextAttributes = @{ NSForegroundColorAttributeName : errorColor };

  if (@available(iOS 13.0, *)) {
    if (scriptError != nil) {
      TiErrorViewController *errorVC = [[TiErrorViewController alloc] initWithError:scriptError];
      [self addChildViewController:errorVC];
      [self.view addSubview:errorVC.view];
      [errorVC didMoveToParentViewController:self];
      [errorVC release];
      return;
    }
  }

  [self.view setBackgroundColor:UIColor.whiteColor];

  // release previous allocations
  RELEASE_TO_NIL(scrollView);
  RELEASE_TO_NIL(messageView);
  RELEASE_TO_NIL(continueButton);

  // create scrollable view for message
  scrollView = [[UIScrollView alloc] init];
  [scrollView setTranslatesAutoresizingMaskIntoConstraints:NO];
  [scrollView setContentMode:UIViewContentModeScaleToFill];
  [scrollView setBackgroundColor:[UIColor clearColor]];
  [self.view addSubview:scrollView];

  // constraints to fill parent
  [self.view addConstraints:@[
    [NSLayoutConstraint constraintWithItem:scrollView
                                 attribute:NSLayoutAttributeWidth
                                 relatedBy:NSLayoutRelationEqual
                                    toItem:self.view
                                 attribute:NSLayoutAttributeWidth
                                multiplier:1
                                  constant:0],
    [NSLayoutConstraint constraintWithItem:scrollView
                                 attribute:NSLayoutAttributeHeight
                                 relatedBy:NSLayoutRelationEqual
                                    toItem:self.view
                                 attribute:NSLayoutAttributeHeight
                                multiplier:1
                                  constant:0]
  ]];

  // create message view inside scrollable view
  messageView = [[UITextView alloc] init];
  [messageView setTranslatesAutoresizingMaskIntoConstraints:NO];
  [messageView setContentMode:UIViewContentModeScaleToFill];
  [messageView setBounces:NO];
  [messageView setBouncesZoom:NO];
  [messageView setEditable:NO];
  [messageView setScrollEnabled:NO];
  [messageView setMultipleTouchEnabled:YES];
  [messageView setBackgroundColor:[UIColor clearColor]];
  [messageView setTextColor:errorColor];
  [messageView setText:error];
  [messageView setFont:[UIFont fontWithName:@"Courier" size:15]];
  [scrollView addSubview:messageView];

  // constraints for top and left padding
  [scrollView addConstraints:@[
    [NSLayoutConstraint constraintWithItem:messageView
                                 attribute:NSLayoutAttributeTop
                                 relatedBy:NSLayoutRelationEqual
                                    toItem:scrollView
                                 attribute:NSLayoutAttributeTop
                                multiplier:1
                                  constant:16],
    [NSLayoutConstraint constraintWithItem:messageView
                                 attribute:NSLayoutAttributeLeft
                                 relatedBy:NSLayoutRelationEqual
                                    toItem:scrollView
                                 attribute:NSLayoutAttributeLeft
                                multiplier:1
                                  constant:8],
  ]];

  // create continue button to dismiss exception
  continueButton = [UIButton buttonWithType:UIButtonTypeRoundedRect];
  [continueButton setTranslatesAutoresizingMaskIntoConstraints:NO];
  [continueButton setBackgroundColor:errorColor];

  // set title and adjust font attributes
  NSMutableAttributedString *continueAttributes = [[NSMutableAttributedString alloc] initWithString:NSLocalizedString(@"CONTINUE", nil)];
  [continueAttributes addAttribute:NSForegroundColorAttributeName value:[UIColor whiteColor] range:NSMakeRange(0, [continueAttributes length])];
  [continueAttributes addAttribute:NSFontAttributeName value:[UIFont boldSystemFontOfSize:18] range:NSMakeRange(0, [continueAttributes length])];
  [continueButton setAttributedTitle:continueAttributes forState:UIControlStateNormal];
  RELEASE_TO_NIL(continueAttributes);

  // define button behaviour
  [continueButton addTarget:self action:@selector(dismiss:) forControlEvents:UIControlEventTouchUpInside];
  [self.view addSubview:continueButton];

  // constrain button to bottom of view
  [self.view addConstraints:@[
    [NSLayoutConstraint constraintWithItem:continueButton
                                 attribute:NSLayoutAttributeWidth
                                 relatedBy:NSLayoutRelationEqual
                                    toItem:self.view
                                 attribute:NSLayoutAttributeWidth
                                multiplier:1
                                  constant:0],
    [NSLayoutConstraint constraintWithItem:continueButton
                                 attribute:NSLayoutAttributeHeight
                                 relatedBy:NSLayoutRelationEqual
                                    toItem:self.view
                                 attribute:NSLayoutAttributeHeight
                                multiplier:0.08
                                  constant:0],
    [NSLayoutConstraint constraintWithItem:continueButton
                                 attribute:NSLayoutAttributeBottom
                                 relatedBy:NSLayoutRelationEqual
                                    toItem:self.view
                                 attribute:NSLayoutAttributeBottom
                                multiplier:1
                                  constant:0]
  ]];

  // re-size message view scrolling content
  [messageView setContentSize:[messageView sizeThatFits:CGSizeMake(FLT_MAX, FLT_MAX)]];
  [messageView setFrame:CGRectMake(messageView.frame.origin.x, messageView.frame.origin.y, messageView.contentSize.width, messageView.contentSize.height)];
  [scrollView setContentSize:CGSizeMake(messageView.contentSize.width, messageView.contentSize.height)];

  [self.view layoutIfNeeded];

  UINotificationFeedbackGenerator *generator = [UINotificationFeedbackGenerator new];
  [generator prepare];
  [generator notificationOccurred:UINotificationFeedbackTypeError];
  RELEASE_TO_NIL(generator);
}

- (BOOL)prefersHomeIndicatorAutoHidden
{
  return YES;
}

- (void)viewDidAppear:(BOOL)animated
{
  [super viewDidAppear:animated];
  [self.view layoutIfNeeded];
}

@end
