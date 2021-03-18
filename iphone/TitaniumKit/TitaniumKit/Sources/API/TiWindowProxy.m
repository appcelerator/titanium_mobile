/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2021 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#import "TiWindowProxy.h"
#import "KrollPromise.h"
#import "TiApp.h"
#import "TiErrorController.h"
#import "TiUIWindow.h"
#import "TiUIWindowProxy.h"

@interface TiWindowProxy (Private)
- (void)openOnUIThread:(id)args;
- (void)closeOnUIThread:(id)args;
- (void)rootViewDidForceFrame:(NSNotification *)notification;
@end

@implementation TiWindowProxy

@synthesize tab = tab;
@synthesize isManaged;

- (void)dealloc
{
  if (controller != nil) {
    TiThreadPerformOnMainThread(
        ^{
          RELEASE_TO_NIL(controller);
        },
        YES);
  }

  if (transitionProxy != nil) {
    [self forgetProxy:transitionProxy];
    RELEASE_TO_NIL(transitionProxy)
  }

  RELEASE_TO_NIL(openPromise);
  RELEASE_TO_NIL(closePromise);

  [super dealloc];
}

- (void)_destroy
{
  [super _destroy];
}

- (void)_configure
{
  forceModal = YES;
  [self replaceValue:nil forKey:@"orientationModes" notification:NO];
  [super _configure];
}

- (NSString *)apiName
{
  return @"Ti.Window";
}

- (void)rootViewDidForceFrame:(NSNotification *)notification
{
  if (focussed && opened) {
    if ((controller == nil) || ([controller navigationController] == nil)) {
      return;
    }
    UINavigationController *nc = [controller navigationController];
    BOOL isHidden = [nc isNavigationBarHidden];
    [nc setNavigationBarHidden:!isHidden animated:NO];
    [nc setNavigationBarHidden:isHidden animated:NO];
    [[nc view] setNeedsLayout];
  }
}

- (TiUIView *)newView
{
  CGRect frame = [self appFrame];
  TiUIWindow *win = [[TiUIWindow alloc] initWithFrame:frame];
  [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(rootViewDidForceFrame:) name:kTiFrameAdjustNotification object:nil];
  return win;
}

- (BOOL)suppressesRelayout
{
  if (controller != nil) {
    //If controller view is not loaded, sandbox bounds will become zero.
    //In that case we do not want to mess up our sandbox, which is by default
    //mainscreen bounds. It will adjust when view loads.
    return ![controller isViewLoaded];
  }
  return [super suppressesRelayout];
}

#pragma mark - Utility Methods
- (void)windowWillOpen
{
  [super windowWillOpen];
  if (tab == nil && !self.isManaged) {
    [[[[TiApp app] controller] topContainerController] willOpenWindow:self];
  }
}

- (void)fireFocusEvent
{
  if ([self _hasListeners:@"focus"]) {
    [self fireEvent:@"focus" withObject:nil withSource:self propagate:NO reportSuccess:NO errorCode:0 message:nil];
  }
}

- (void)windowDidOpen
{
  opening = NO;
  opened = YES;
  if ([self _hasListeners:@"open"]) {
    [self fireEvent:@"open" withObject:nil withSource:self propagate:NO reportSuccess:NO errorCode:0 message:nil];
  }
  if (openPromise != nil) {
    [openPromise resolve:@[]];
    RELEASE_TO_NIL(openPromise);
  }
  if (focussed) {
    [self fireFocusEvent];
  }
  [super windowDidOpen];
  [self forgetProxy:openAnimation];
  RELEASE_TO_NIL(openAnimation);
  if (tab == nil && !self.isManaged) {
    [[[[TiApp app] controller] topContainerController] didOpenWindow:self];
  }
}

- (void)windowWillClose
{
  if (tab == nil && !self.isManaged) {
    [[[[TiApp app] controller] topContainerController] willCloseWindow:self];
  }
  [[NSNotificationCenter defaultCenter] removeObserver:self];
  [super windowWillClose];
}

- (void)windowDidClose
{
  opened = NO;
  closing = NO;
  if ([self _hasListeners:@"close"]) {
    [self fireEvent:@"close" withObject:nil withSource:self propagate:NO reportSuccess:NO errorCode:0 message:nil];
  }
  if (closePromise != nil) {
    [closePromise resolve:@[]];
    RELEASE_TO_NIL(closePromise);
  }
  [self forgetProxy:closeAnimation];
  RELEASE_TO_NIL(closeAnimation);
  if (tab == nil && !self.isManaged) {
    [[[[TiApp app] controller] topContainerController] didCloseWindow:self];
  }
  tab = nil;
  self.isManaged = NO;
  RELEASE_TO_NIL_AUTORELEASE(controller);
  [super windowDidClose];
  [self forgetSelf];
}

- (void)attachViewToTopContainerController
{
  UIViewController<TiControllerContainment> *topContainerController = [[[TiApp app] controller] topContainerController];
  UIView *rootView = [topContainerController hostingView];
  TiUIView *theView = [self view];
  [rootView addSubview:theView];
  [rootView bringSubviewToFront:theView];
}

- (BOOL)argOrWindowPropertyExists:(NSString *)key args:(id)args
{
  id value = [self valueForUndefinedKey:key];
  if (!IS_NULL_OR_NIL(value)) {
    return YES;
  }
  if (([args count] > 0) && [[args objectAtIndex:0] isKindOfClass:[NSDictionary class]]) {
    value = [[args objectAtIndex:0] objectForKey:key];
    if (!IS_NULL_OR_NIL(value)) {
      return YES;
    }
  }
  return NO;
}

- (BOOL)argOrWindowProperty:(NSString *)key args:(id)args
{
  if ([TiUtils boolValue:[self valueForUndefinedKey:key]]) {
    return YES;
  }
  if (([args count] > 0) && [[args objectAtIndex:0] isKindOfClass:[NSDictionary class]]) {
    return [TiUtils boolValue:key properties:[args objectAtIndex:0] def:NO];
  }
  return NO;
}

- (BOOL)isRootViewLoaded
{
  return [[[TiApp app] controller] isViewLoaded];
}

- (BOOL)isRootViewAttached
{
  //When a modal window is up, just return yes
  if ([[[TiApp app] controller] presentedViewController] != nil) {
    return YES;
  }
  return ([[[[TiApp app] controller] view] superview] != nil);
}

#pragma mark - TiWindowProtocol Base Methods
- (KrollPromise *)open:(id)args
{
  JSContext *context = [self currentContext];

  // If an error is up, Go away
  if ([[[[TiApp app] controller] topPresentedController] isKindOfClass:[TiErrorNavigationController class]]) {
    DebugLog(@"[ERROR] ErrorController is up. ABORTING open");
    return [KrollPromise rejectedWithErrorMessage:@"ErrorController is up. ABORTING open" inContext:context];
  }

  // I am already open or will be soon. Go Away
  if (opening || opened) {
    return [KrollPromise rejectedWithErrorMessage:@"Window is already opened or opening." inContext:context];
  }

  // Lets keep ourselves safe
  [self rememberSelf];

  // Don't recreate if we're calling ourselves again because root view is not loaded/attached!
  if (openPromise == nil) {
    openPromise = [[KrollPromise alloc] initInContext:context];
  }

  //Make sure our RootView Controller is attached
  if (![self isRootViewLoaded]) {
    DebugLog(@"[WARN] ROOT VIEW NOT LOADED. WAITING");
    [self performSelector:@selector(open:) withObject:args afterDelay:0.1];
    return openPromise;
  }
  if (![self isRootViewAttached]) {
    DebugLog(@"[WARN] ROOT VIEW NOT ATTACHED. WAITING");
    [self performSelector:@selector(open:) withObject:args afterDelay:0.1];
    return openPromise;
  }

  opening = YES;

  isModal = (tab == nil && !self.isManaged) ? [self argOrWindowProperty:@"modal" args:args] : NO;

  if ([self argOrWindowProperty:@"fullscreen" args:args]) {
    hidesStatusBar = YES;
  } else {
    if ([self argOrWindowPropertyExists:@"fullscreen" args:args]) {
      hidesStatusBar = NO;
    } else {
      hidesStatusBar = [[[TiApp app] controller] statusBarInitiallyHidden];
    }
  }

  UIStatusBarStyle theStyle = [TiUtils intValue:[self valueForUndefinedKey:@"statusBarStyle"] def:(int)[[[TiApp app] controller] defaultStatusBarStyle]];

  [self assignStatusBarStyle:theStyle];

  if (!isModal && (tab == nil)) {
    openAnimation = [[TiAnimation animationFromArg:args context:[self pageContext] create:NO] retain];
    [self rememberProxy:openAnimation];
  }
  //TODO Argument Processing
  id object = [self valueForUndefinedKey:@"orientationModes"];
  _supportedOrientations = [TiUtils TiOrientationFlagsFromObject:object];

  //GO ahead and call open on the UI thread
  TiThreadPerformOnMainThread(
      ^{
        [self openOnUIThread:args];
      },
      NO);
  return openPromise;
}

- (void)setStatusBarStyle:(id)style
{
  UIStatusBarStyle theStyle = [TiUtils intValue:style def:(int)[[[TiApp app] controller] defaultStatusBarStyle]];
  [self assignStatusBarStyle:theStyle];
  [self setValue:@(barStyle) forUndefinedKey:@"statusBarStyle"];
  if (focussed) {
    TiThreadPerformOnMainThread(
        ^{
          [[[TiApp app] controller] updateStatusBar];
        },
        YES);
  }
}

- (KrollPromise *)close:(id)args
{
  JSContext *context = [self currentContext];

  if (!opened) {
    // If I've been asked to open but haven't yet, short-circuit it and tell it not to open
    if (opening) {
      opening = NO; // _handleOpen: should check this and abort opening
      DebugLog(@"Window is not open yet. Attempting to stop it from opening...");
      // Should we reject or resolve here?! This feels more like "success", so maybe resolve? or do we have to wait on the end result of the openPromise to know?
      return [KrollPromise resolved:@[] inContext:context];
    }

    DebugLog(@"Window is not open. Ignoring this close call");
    return [KrollPromise rejectedWithErrorMessage:@"Window is not open. Ignoring this close call" inContext:context];
  }

  if (closing) {
    DebugLog(@"Window is already closing. Ignoring this close call.");
    return [KrollPromise rejectedWithErrorMessage:@"Window is already closing. Ignoring this close call." inContext:context];
  }

  if (tab != nil) {
    if ([args count] > 0) {
      args = [NSArray arrayWithObjects:self, [args objectAtIndex:0], nil];
    } else {
      args = [NSArray arrayWithObject:self];
    }
    return [tab closeWindow:args];
  }

  if (closePromise == nil) {
    closePromise = [[KrollPromise alloc] initInContext:context];
  }

  closing = YES;

  // TODO: Argument Processing
  closeAnimation = [[TiAnimation animationFromArg:args context:[self pageContext] create:NO] retain];
  [self rememberProxy:closeAnimation];

  // GO ahead and call close on UI thread
  TiThreadPerformOnMainThread(
      ^{
        [self closeOnUIThread:args];
      },
      NO);
  return closePromise;
}

- (NSNumber *)closed
{
  return NUMBOOL(!opening && !opened && !closing);
}

- (NSNumber *)focused
{
  return NUMBOOL(focussed);
}

- (BOOL)_handleOpen:(id)args
{
  TiRootViewController *theController = [[TiApp app] controller];
  if (isModal || (tab != nil) || self.isManaged) {
    [self forgetProxy:openAnimation];
    RELEASE_TO_NIL(openAnimation);
  }

  if ((!self.isManaged) && (!isModal) && (openAnimation != nil) && ([theController topPresentedController] != [theController topContainerController])) {
    DeveloperLog(@"[WARN] The top View controller is not a container controller. This window will open behind the presented controller without animations.")
        [self forgetProxy:openAnimation];
    RELEASE_TO_NIL(openAnimation);
  }

  // Did someone try to close before we ever finished opening?
  if (!opening) {
    return NO;
  }

  return YES;
}

- (BOOL)_handleClose:(id)args
{
  TiRootViewController *theController = [[TiApp app] controller];
  if (isModal || (tab != nil) || self.isManaged) {
    [self forgetProxy:closeAnimation];
    RELEASE_TO_NIL(closeAnimation);
  }
  if ((!self.isManaged) && (!isModal) && (closeAnimation != nil) && ([theController topPresentedController] != [theController topContainerController])) {
    DeveloperLog(@"[WARN] The top View controller is not a container controller. This window will close behind the presented controller without animations.")
        [self forgetProxy:closeAnimation];
    RELEASE_TO_NIL(closeAnimation);
  }
  return YES;
}

- (BOOL)opening
{
  return opening;
}

- (BOOL)closing
{
  return closing;
}

- (void)setModal:(id)val
{
  [self replaceValue:val forKey:@"modal" notification:NO];
}

- (BOOL)isModal
{
  return isModal;
}

- (NSNumber *)homeIndicatorAutoHidden
{
  return @([self homeIndicatorAutoHide]);
}

- (void)setHomeIndicatorAutoHidden:(id)arg
{
  ENSURE_TYPE(arg, NSNumber);
  id current = [self valueForUndefinedKey:@"homeIndicatorAutoHidden"];
  [self replaceValue:arg forKey:@"homeIndicatorAutoHidden" notification:NO];
  if (current != arg) {
    [[[TiApp app] controller] setNeedsUpdateOfHomeIndicatorAutoHidden];
  }
}

- (BOOL)homeIndicatorAutoHide
{
  return [TiUtils boolValue:[self valueForUndefinedKey:@"homeIndicatorAutoHidden"] def:NO];
}

- (BOOL)hidesStatusBar
{
  return hidesStatusBar;
}

- (UIStatusBarStyle)preferredStatusBarStyle;
{
  return barStyle;
}

- (BOOL)handleFocusEvents
{
  return YES;
}

- (void)gainFocus
{
  if (!focussed) {
    focussed = YES;
    if (opened) {
      [self fireFocusEvent];
    }
    UIAccessibilityPostNotification(UIAccessibilityScreenChangedNotification, nil);
    [[self view] setAccessibilityElementsHidden:NO];
    NSArray *childProxies = [self children];
    for (TiViewProxy *thisProxy in childProxies) {
      // Will pass messsage to view proxies if they are listening e.g TiUIListViewProxy
      if ([thisProxy respondsToSelector:@selector(gainFocus)]) {
        [(id)thisProxy gainFocus];
      }
    }
    [self processForSafeArea];
  }
  TiThreadPerformOnMainThread(
      ^{
        [self forceNavBarFrame];
      },
      NO);
}

- (void)resignFocus
{
  if (focussed) {
    focussed = NO;
    if ([self handleFocusEvents]) {
      if ([self _hasListeners:@"blur"]) {
        [self fireEvent:@"blur" withObject:nil withSource:self propagate:NO reportSuccess:NO errorCode:0 message:nil];
      }
    }
    [[self view] setAccessibilityElementsHidden:YES];
  }
  NSArray *childProxies = [self children];
  for (TiViewProxy *thisProxy in childProxies) {
    // Will pass messsage to view proxies if they are listening e.g TiUIListViewProxy
    if ([thisProxy respondsToSelector:@selector(resignFocus)]) {
      [(id)thisProxy resignFocus];
    }
  }
}

- (UIViewController *)hostingController;
{
  if (controller == nil) {
    controller = [[TiViewController alloc] initWithViewProxy:self];
  }
  return controller;
}

- (UIViewController *)windowHoldingController
{
  if (controller != nil) {
    return controller;
  } else {
    return [[TiApp app] controller];
  }
}

#pragma mark - Private Methods
- (TiProxy *)tabGroup
{
  return [tab tabGroup];
}

- (void)assignStatusBarStyle:(UIStatusBarStyle)style
{
  switch (style) {
  case UIStatusBarStyleDefault:
  case UIStatusBarStyleLightContent:
  case UIStatusBarStyleDarkContent:
    barStyle = style;
    break;
  default:
    barStyle = UIStatusBarStyleDefault;
  }
}

- (NSNumber *)orientation
{
  return @([UIApplication sharedApplication].statusBarOrientation);
}

- (void)forceNavBarFrame
{
  if (!focussed) {
    return;
  }
  if ((controller == nil) || ([controller navigationController] == nil)) {
    return;
  }

  if (![[[TiApp app] controller] statusBarVisibilityChanged]) {
    return;
  }

  UINavigationController *nc = [controller navigationController];
  BOOL isHidden = [nc isNavigationBarHidden];
  [nc setNavigationBarHidden:!isHidden animated:NO];
  [nc setNavigationBarHidden:isHidden animated:NO];
  [[nc view] setNeedsLayout];
}

- (void)openOnUIThread:(NSArray *)args
{
  if ([self _handleOpen:args]) {
    [self parentWillShow];
    [self view];
    if (tab != nil) {
      if ([args count] > 0) {
        args = [NSArray arrayWithObjects:self, [args objectAtIndex:0], nil];
      } else {
        args = [NSArray arrayWithObject:self];
      }
      [[tab openWindow:args] flush]; // TODO: release?
    } else if (isModal) {
      UIViewController *theController = [self hostingController];
      [self windowWillOpen];
      NSDictionary *dict = [args count] > 0 ? [args objectAtIndex:0] : nil;
      int style = [TiUtils intValue:@"modalTransitionStyle" properties:dict def:-1];
      if (style != -1) {
        [theController setModalTransitionStyle:style];
      }
      UIModalPresentationStyle modalStyle = [TiUtils intValue:@"modalStyle" properties:dict def:-1];
      if (modalStyle != -1) {
        // modal transition style page curl must be done only in fullscreen
        // so only allow if not page curl
        if ([theController modalTransitionStyle] != UIModalTransitionStylePartialCurl) {
          [theController setModalPresentationStyle:modalStyle];
        }
      }

      if ([TiUtils isIOSVersionOrGreater:@"13.0"]) {
        forceModal = [TiUtils boolValue:@"forceModal" properties:dict def:NO];
        theController.modalInPresentation = forceModal;
      }
      BOOL animated = [TiUtils boolValue:@"animated" properties:dict def:YES];
      [[TiApp app] showModalController:theController animated:animated];
    } else {
      [self windowWillOpen];
      if (!self.isManaged && ((openAnimation == nil) || (![openAnimation isTransitionAnimation]))) {
        [self attachViewToTopContainerController];
      }
      if (openAnimation != nil) {
        [openAnimation setDelegate:self];
        [openAnimation animate:self];
      } else {
        [self windowDidOpen];
      }
    }
  } else {
    DebugLog(@"[WARN] OPEN ABORTED. _handleOpen returned NO");
    opening = NO;
    opened = NO;
    [self forgetProxy:openAnimation];
    RELEASE_TO_NIL(openAnimation);
    // reject the openPromise!
    if (openPromise != nil) {
      [openPromise rejectWithErrorMessage:@"open aborted"];
      RELEASE_TO_NIL(openPromise);
    }
  }
}

- (void)closeOnUIThread:(NSArray *)args
{
  if ([self _handleClose:args]) {
    [self windowWillClose];
    if (isModal) {
      NSDictionary *dict = [args count] > 0 ? [args objectAtIndex:0] : nil;
      BOOL animated = [TiUtils boolValue:@"animated" properties:dict def:YES];
      [[TiApp app] hideModalController:controller animated:animated];
    } else {
      if (closeAnimation != nil) {
        [closeAnimation setDelegate:self];
        [closeAnimation animate:self];
      } else {
        [self windowDidClose];
      }
    }

  } else {
    DebugLog(@"[WARN] CLOSE ABORTED. _handleClose returned NO");
    closing = NO;
    RELEASE_TO_NIL(closeAnimation);
    if (closePromise != nil) {
      [closePromise rejectWithErrorMessage:@"close aborted"];
      RELEASE_TO_NIL(closePromise);
    }
  }
}

#pragma mark - TiOrientationController
- (void)childOrientationControllerChangedFlags:(id<TiOrientationController>)orientationController;
{
  [parentController childOrientationControllerChangedFlags:self];
}

- (void)setParentOrientationController:(id<TiOrientationController>)newParent
{
  parentController = newParent;
}

- (id)parentOrientationController
{
  return parentController;
}

- (TiOrientationFlags)orientationFlags
{
  if ([self isModal]) {
    return (_supportedOrientations == TiOrientationNone) ? [[[TiApp app] controller] getDefaultOrientations] : _supportedOrientations;
  }
  return _supportedOrientations;
}

- (void)showNavBar:(NSArray *)args
{
  ENSURE_UI_THREAD(showNavBar, args);
  [self replaceValue:[NSNumber numberWithBool:NO] forKey:@"navBarHidden" notification:NO];
  if (controller != nil) {
    id properties = (args != nil && [args count] > 0) ? [args objectAtIndex:0] : nil;
    BOOL animated = [TiUtils boolValue:@"animated" properties:properties def:YES];
    [[controller navigationController] setNavigationBarHidden:NO animated:animated];
    [self processForSafeArea];
  }
}

- (void)hideNavBar:(NSArray *)args
{
  ENSURE_UI_THREAD(hideNavBar, args);
  [self replaceValue:[NSNumber numberWithBool:YES] forKey:@"navBarHidden" notification:NO];
  if (controller != nil) {
    id properties = (args != nil && [args count] > 0) ? [args objectAtIndex:0] : nil;
    BOOL animated = [TiUtils boolValue:@"animated" properties:properties def:YES];
    [[controller navigationController] setNavigationBarHidden:YES animated:animated];
    [self processForSafeArea];
    //TODO: need to fix height
  }
}

- (void)showToolbar:(NSArray *)args
{
  ENSURE_UI_THREAD(showToolbar, args);
  [self replaceValue:[NSNumber numberWithBool:NO] forKey:@"toolbarHidden" notification:NO];
  if (controller != nil) {
    id properties = (args != nil && [args count] > 0) ? [args objectAtIndex:0] : nil;
    BOOL animated = [TiUtils boolValue:@"animated" properties:properties def:YES];
    [[controller navigationController] setToolbarHidden:NO animated:animated];
  } else {
    NSLog(@"[WARN] Use this method only with toolbars which are attached to a Ti.UI.NavigationWindow by using the setToolbar method.");
  }
}

- (void)hideToolbar:(NSArray *)args
{
  ENSURE_UI_THREAD(hideToolbar, args);
  [self replaceValue:[NSNumber numberWithBool:YES] forKey:@"toolbarHidden" notification:NO];
  if (controller != nil) {
    id properties = (args != nil && [args count] > 0) ? [args objectAtIndex:0] : nil;
    BOOL animated = [TiUtils boolValue:@"animated" properties:properties def:YES];
    [[controller navigationController] setToolbarHidden:YES animated:animated];
  } else {
    NSLog(@"[WARN] Use this method only with toolbars which are attached to a Ti.UI.NavigationWindow by using the setToolbar method.");
  }
}

#pragma mark - Appearance and Rotation Callbacks. For subclasses to override.
//Containing controller will call these callbacks(appearance/rotation) on contained windows when it receives them.
- (void)viewWillAppear:(BOOL)animated
{
  id navBarHidden = [self valueForUndefinedKey:@"navBarHidden"];
  id hidesBarsOnSwipe = [self valueForUndefinedKey:@"hidesBarsOnSwipe"];
  id hidesBarsOnTap = [self valueForUndefinedKey:@"hidesBarsOnTap"];
  id hidesBarsWhenKeyboardAppears = [self valueForUndefinedKey:@"hidesBarsWhenKeyboardAppears"];
  id hidesBackButton = [self valueForUndefinedKey:@"hidesBackButton"];

  if (navBarHidden) {
    id properties = [NSArray arrayWithObject:[NSDictionary dictionaryWithObject:[NSNumber numberWithBool:NO] forKey:@"animated"]];
    if ([TiUtils boolValue:navBarHidden]) {
      [self hideNavBar:properties];
    } else {
      [self showNavBar:properties];
    }
  }
  if (hidesBarsOnSwipe) {
    [self setHidesBarsOnSwipe:hidesBarsOnSwipe];
  }
  if (hidesBarsOnTap) {
    [self setHidesBarsOnTap:hidesBarsOnTap];
  }
  if (hidesBarsWhenKeyboardAppears) {
    [self setHidesBarsWhenKeyboardAppears:hidesBarsWhenKeyboardAppears];
  }
  if (hidesBackButton) {
    [self setHidesBackButton:hidesBackButton];
  }

  [self willShow];
}
- (void)viewWillDisappear:(BOOL)animated
{
  if (controller != nil) {
    [self resignFocus];
  }
  [self willHide];
}
- (void)viewDidAppear:(BOOL)animated
{
  if (isModal && opening) {
    [self windowDidOpen];
  }
  if (controller != nil && !self.isManaged) {
    [self gainFocus];
  }
}
- (void)viewDidDisappear:(BOOL)animated
{
  if (isModal && closing) {
    [self windowDidClose];
  }
}

- (void)presentationControllerWillDismiss:(UIPresentationController *)presentationController
{
  if (isModal) {
    [self windowWillClose];
  }
}

- (void)presentationControllerDidDismiss:(UIPresentationController *)presentationController
{
  if (isModal) {
    [self windowDidClose];
  }
}

- (void)setHidesBarsOnSwipe:(id)value
{
  ENSURE_TYPE(value, NSNumber);
  ENSURE_UI_THREAD(setHidesBarsOnSwipe, value);

  [self replaceValue:value forKey:@"hidesBarsOnSwipe" notification:NO];

  if ((controller != nil) && ([controller navigationController] != nil)) {
    [[controller navigationController] setHidesBarsOnSwipe:[TiUtils boolValue:value def:NO]];
  }
}

- (void)setHidesBarsOnTap:(id)value
{
  ENSURE_TYPE(value, NSNumber);
  ENSURE_UI_THREAD(setHidesBarsOnTap, value);

  [self replaceValue:value forKey:@"hidesBarsOnTap" notification:NO];

  if ((controller != nil) && ([controller navigationController] != nil)) {
    [[controller navigationController] setHidesBarsOnTap:[TiUtils boolValue:value def:NO]];
  }
}

- (void)setHidesBarsWhenKeyboardAppears:(id)value
{
  ENSURE_TYPE(value, NSNumber);
  ENSURE_UI_THREAD(setHidesBarsWhenKeyboardAppears, value);

  [self replaceValue:value forKey:@"hidesBarsWhenKeyboardAppears" notification:NO];

  if ((controller != nil) && ([controller navigationController] != nil)) {
    [[controller navigationController] setHidesBarsWhenKeyboardAppears:[TiUtils boolValue:value def:NO]];
  }
}

- (void)setHidesBackButton:(id)value
{
  ENSURE_TYPE(value, NSNumber);
  ENSURE_UI_THREAD(setHidesBackButton, value);

  [self replaceValue:value forKey:@"hidesBackButton" notification:NO];

  if ((controller != nil) && ([controller navigationItem] != nil)) {
    [[controller navigationItem] setHidesBackButton:[TiUtils boolValue:value def:NO]];
  }
}

- (void)viewWillTransitionToSize:(CGSize)size withTransitionCoordinator:(id<UIViewControllerTransitionCoordinator>)coordinator
{
  //For various views (scrollableView, NavGroup etc this info neeeds to be forwarded)
  NSArray *childProxies = [self children];
  for (TiViewProxy *thisProxy in childProxies) {
    if ([thisProxy respondsToSelector:@selector(viewWillTransitionToSize:withTransitionCoordinator:)]) {
      [(id)thisProxy viewWillTransitionToSize:size withTransitionCoordinator:coordinator];
    }
  }
}

- (void)willTransitionToTraitCollection:(UITraitCollection *)newCollection withTransitionCoordinator:(id<UIViewControllerTransitionCoordinator>)coordinator
{
  //For various views (scrollableView, NavGroup etc this info neeeds to be forwarded)
  NSArray *childProxies = [self children];
  for (TiViewProxy *thisProxy in childProxies) {
    if ([thisProxy respondsToSelector:@selector(willTransitionToTraitCollection:withTransitionCoordinator:)]) {
      [(id)thisProxy willTransitionToTraitCollection:newCollection withTransitionCoordinator:coordinator];
    }
  }
}

- (void)systemLayoutFittingSizeDidChangeForChildContentContainer:(id<UIContentContainer>)container
{
  //For various views (scrollableView, NavGroup etc this info neeeds to be forwarded)
  NSArray *childProxies = [self children];
  for (TiViewProxy *thisProxy in childProxies) {
    if ([thisProxy respondsToSelector:@selector(systemLayoutFittingSizeDidChangeForChildContentContainer:)]) {
      [(id)thisProxy systemLayoutFittingSizeDidChangeForChildContentContainer:container];
    }
  }
}

- (void)preferredContentSizeDidChangeForChildContentContainer:(id<UIContentContainer>)container
{
  //For various views (scrollableView, NavGroup etc this info neeeds to be forwarded)
  NSArray *childProxies = [self children];
  for (TiViewProxy *thisProxy in childProxies) {
    if ([thisProxy respondsToSelector:@selector(preferredContentSizeDidChangeForChildContentContainer:)]) {
      [(id)thisProxy preferredContentSizeDidChangeForChildContentContainer:container];
    }
  }
}

#pragma mark - TiAnimation Delegate Methods
- (BOOL)animationShouldTransition:(TiAnimation *)sender
{
  BOOL isOpenAnimation = NO;
  UIView *hostingView = nil;
  if (sender == openAnimation) {
    hostingView = [[[[TiApp app] controller] topContainerController] hostingView];
    isOpenAnimation = YES;
  } else {
    hostingView = [[self view] superview];
  }

  void (^animation)(void) = ^{
    if (isOpenAnimation) {
      RELEASE_TO_NIL(animatedOver);
      NSArray *subviews = [hostingView subviews];
      if ([subviews count] > 0) {
        animatedOver = [[subviews lastObject] retain];
      }
      if (animatedOver != nil) {
        [animatedOver removeFromSuperview];
      }
      [hostingView addSubview:[self view]];
    } else {
      [[self view] removeFromSuperview];
    }
  };

  [UIView transitionWithView:hostingView
                    duration:[(TiAnimation *)sender animationDuration]
                     options:[[(TiAnimation *)sender transition] intValue]
                  animations:animation
                  completion:^(BOOL finished) {
                    [sender animationCompleted:[NSString stringWithFormat:@"%@", hostingView]
                                      finished:[NSNumber numberWithBool:finished]
                                       context:sender];
                  }];

  return NO;
}

- (void)animationDidComplete:(TiAnimation *)sender
{
  if (sender == openAnimation) {
    if (animatedOver != nil) {
      if ([animatedOver isKindOfClass:[TiUIView class]]) {
        TiViewProxy *theProxy = (TiViewProxy *)[(TiUIView *)animatedOver proxy];
        if ([theProxy viewAttached]) {
          [[[self view] superview] insertSubview:animatedOver belowSubview:[self view]];
#ifndef TI_USE_AUTOLAYOUT
          LayoutConstraint *layoutProps = [theProxy layoutProperties];
          ApplyConstraintToViewWithBounds(layoutProps, (TiUIView *)animatedOver, [[animatedOver superview] bounds]);
#endif
          [theProxy layoutChildren:NO];
          RELEASE_TO_NIL(animatedOver);
        }
      } else {
        [[[self view] superview] insertSubview:animatedOver belowSubview:[self view]];
      }
    }
    [self windowDidOpen];
  } else {
    [self windowDidClose];
  }
}
- (TiUIiOSTransitionAnimationProxy *)transitionAnimation
{
  return transitionProxy;
}

- (void)setTransitionAnimation:(id)args
{
  ENSURE_SINGLE_ARG_OR_NIL(args, TiUIiOSTransitionAnimationProxy)
  if (transitionProxy != nil) {
    [self forgetProxy:transitionProxy];
    RELEASE_TO_NIL(transitionProxy)
  }
  transitionProxy = [args retain];
  [self rememberProxy:transitionProxy];
}

- (void)processForSafeArea
{
  // Overridden in subclass
}

@end
