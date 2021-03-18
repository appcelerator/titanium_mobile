/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#import "TiViewController.h"
#import "TiApp.h"

@implementation TiViewController

- (id)initWithViewProxy:(TiViewProxy *)window
{
  if (self = [super init]) {
    _proxy = window;
    [self updateOrientations];
    [TiUtils configureController:self withObject:_proxy];
  }
  return self;
}

- (void)dealloc
{
  RELEASE_TO_NIL(_previewActions);
  [super dealloc];
}

- (NSArray<id<UIPreviewActionItem>> *)previewActionItems
{
  if ([self previewActions] == nil) {
    [self setPreviewActions:[NSArray array]];
  }

  return [self previewActions];
}

- (void)updateOrientations
{
  id object = [_proxy valueForUndefinedKey:@"orientationModes"];
  _supportedOrientations = [TiUtils TiOrientationFlagsFromObject:object];
  if (_supportedOrientations == TiOrientationNone) {
    _supportedOrientations = [[[TiApp app] controller] getDefaultOrientations];
  }
}

- (TiViewProxy *)proxy
{
  return _proxy;
}

#ifdef DEVELOPER
- (void)viewWillLayoutSubviews
{
  CGRect bounds = [[self view] bounds];
  NSLog(@"TIVIEWCONTROLLER WILL LAYOUT SUBVIEWS %.1f %.1f", bounds.size.width, bounds.size.height);
  [super viewWillLayoutSubviews];
}
#endif

- (void)viewDidLayoutSubviews
{
#ifdef DEVELOPER
  CGRect bounds = [[self view] bounds];
  NSLog(@"TIVIEWCONTROLLER DID LAYOUT SUBVIEWS %.1f %.1f", bounds.size.width, bounds.size.height);
#endif
  if (_proxy != nil && !CGRectEqualToRect([_proxy sandboxBounds], [[self view] bounds])) {
    [_proxy parentSizeWillChange];
  }
  [super viewDidLayoutSubviews];
}

- (BOOL)shouldAutomaticallyForwardAppearanceMethods
{
  return YES;
}

- (BOOL)shouldAutorotate
{
  return YES;
}

- (UIInterfaceOrientationMask)supportedInterfaceOrientations
{
  /*
     If we are in a navigation controller, let us match so it doesn't get freaked 
     out in when pushing/popping. We are going to force orientation anyways.
     */
  /*
   TIMOB-28282. Shouldn't UINavigationController.topViewController decide the supported orientation?
   */
  if ([self navigationController] != nil && [[self navigationController] topViewController] != self) {
    return [[[self navigationController] topViewController] supportedInterfaceOrientations];
  }
  //This would be for modal.
  return (UIInterfaceOrientationMask)_supportedOrientations;
}

- (UIInterfaceOrientation)preferredInterfaceOrientationForPresentation
{
  return [[[TiApp app] controller] lastValidOrientation:_supportedOrientations];
}

- (void)loadView
{
  if (_proxy == nil) {
    DebugLog(@"NO PROXY ASSOCIATED WITH VIEWCONTROLLER. RETURNING") return;
  }
  [self updateOrientations];
  [self setHidesBottomBarWhenPushed:[TiUtils boolValue:[_proxy valueForUndefinedKey:@"tabBarHidden"] def:NO]];
  //Always wrap proxy view with a wrapperView.
  //This way proxy always has correct sandbox when laying out
  [_proxy parentWillShow];
  UIView *wrapperView = [[UIView alloc] initWithFrame:UIApplication.sharedApplication.keyWindow.frame];
  wrapperView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
  [wrapperView addSubview:[_proxy view]];
  [wrapperView bringSubviewToFront:[_proxy view]];
  self.view = wrapperView;
  [wrapperView release];
}

#pragma mark - Appearance & rotation methods

- (void)viewWillAppear:(BOOL)animated
{
  if (_proxy != nil) {
    [_proxy parentWillShow];
    if ([_proxy conformsToProtocol:@protocol(TiWindowProtocol)]) {
      [(id<TiWindowProtocol>)_proxy viewWillAppear:animated];
    }
  }
  [super viewWillAppear:animated];
}
- (void)viewWillDisappear:(BOOL)animated
{
  if (_proxy != nil) {
    [_proxy parentWillHide];
    if ([_proxy conformsToProtocol:@protocol(TiWindowProtocol)]) {
      [(id<TiWindowProtocol>)_proxy viewWillDisappear:animated];
    }
  }
  [super viewWillDisappear:animated];
}
- (void)viewDidAppear:(BOOL)animated
{
  if (_proxy != nil && [_proxy conformsToProtocol:@protocol(TiWindowProtocol)]) {
    [(id<TiWindowProtocol>)_proxy viewDidAppear:animated];
  }
  [super viewDidAppear:animated];
}
- (void)viewDidDisappear:(BOOL)animated
{
  if (_proxy != nil && [_proxy conformsToProtocol:@protocol(TiWindowProtocol)]) {
    [(id<TiWindowProtocol>)_proxy viewDidDisappear:animated];
  }
  [super viewDidDisappear:animated];
}

- (void)presentationControllerWillDismiss:(UIPresentationController *)presentationController
{
  if (_proxy != nil && [_proxy conformsToProtocol:@protocol(TiWindowProtocol)]) {
    [(id<TiWindowProtocol>)_proxy presentationControllerWillDismiss:presentationController];
  }
}

- (void)presentationControllerDidDismiss:(UIPresentationController *)presentationController
{
  if (_proxy != nil && [_proxy conformsToProtocol:@protocol(TiWindowProtocol)]) {
    [(id<TiWindowProtocol>)_proxy presentationControllerDidDismiss:presentationController];
  }
}

- (void)viewWillTransitionToSize:(CGSize)size withTransitionCoordinator:(id<UIViewControllerTransitionCoordinator>)coordinator
{
  if (_proxy != nil && [_proxy conformsToProtocol:@protocol(TiWindowProtocol)]) {
    [(id<TiWindowProtocol>)_proxy viewWillTransitionToSize:size withTransitionCoordinator:coordinator];
  }
  [super viewWillTransitionToSize:size withTransitionCoordinator:coordinator];
}

- (void)systemLayoutFittingSizeDidChangeForChildContentContainer:(id<UIContentContainer>)container
{
  if (_proxy != nil && [_proxy conformsToProtocol:@protocol(TiWindowProtocol)]) {
    [(id<TiWindowProtocol>)_proxy systemLayoutFittingSizeDidChangeForChildContentContainer:container];
  }
  [super systemLayoutFittingSizeDidChangeForChildContentContainer:container];
}

- (void)willTransitionToTraitCollection:(UITraitCollection *)newCollection withTransitionCoordinator:(id<UIViewControllerTransitionCoordinator>)coordinator
{
  if (_proxy != nil && [_proxy conformsToProtocol:@protocol(TiWindowProtocol)]) {
    [(id<TiWindowProtocol>)_proxy willTransitionToTraitCollection:newCollection withTransitionCoordinator:coordinator];
  }
  [super willTransitionToTraitCollection:newCollection withTransitionCoordinator:coordinator];
}

- (void)preferredContentSizeDidChangeForChildContentContainer:(id<UIContentContainer>)container
{
  if (_proxy != nil && [_proxy conformsToProtocol:@protocol(TiWindowProtocol)]) {
    [(id<TiWindowProtocol>)_proxy preferredContentSizeDidChangeForChildContentContainer:container];
  }
  [super preferredContentSizeDidChangeForChildContentContainer:container];
}

#pragma mark - HomeIndicatorAutoHidden

- (BOOL)prefersHomeIndicatorAutoHidden
{
  if (_proxy != nil && [_proxy conformsToProtocol:@protocol(TiWindowProtocol)]) {
    return [(id<TiWindowProtocol>)_proxy homeIndicatorAutoHide];
  }

  return NO;
}

#pragma mark - Status Bar Appearance

- (BOOL)prefersStatusBarHidden
{
  if (_proxy != nil && [_proxy conformsToProtocol:@protocol(TiWindowProtocol)]) {
    return [(id<TiWindowProtocol>)_proxy hidesStatusBar];
  }

  return NO;
}

- (UIStatusBarStyle)preferredStatusBarStyle
{
  if (_proxy != nil && [_proxy conformsToProtocol:@protocol(TiWindowProtocol)]) {
    return [(id<TiWindowProtocol>)_proxy preferredStatusBarStyle];
  }

  if ([[[TiApp app] controller] topContainerController] != nil) {
    // Prefer the style of the most recent view controller.
    return [[[[TiApp app] controller] topContainerController] preferredStatusBarStyle];
  }

  return UIStatusBarStyleDefault;
}

- (BOOL)modalPresentationCapturesStatusBarAppearance
{
  return YES;
}

- (UIStatusBarAnimation)preferredStatusBarUpdateAnimation
{
  return UIStatusBarAnimationNone;
}
- (BOOL)disablesAutomaticKeyboardDismissal
{
  return NO;
}
@end
