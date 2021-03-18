/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2015 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#import "TiRootViewController.h"
#import "TiApp.h"
#import "TiErrorController.h"
#import "TiLayoutQueue.h"
#import "TiSharedConfig.h"
#import "TiUtils.h"
#import "TiViewController.h"

#ifdef FORCE_WITH_MODAL
@interface ForcingController : UIViewController {
  @private
  TiOrientationFlags orientationFlags;
  UIInterfaceOrientation supportedOrientation;
}
- (void)setOrientation:(UIInterfaceOrientation)newOrientation;
@end

@implementation ForcingController
- (void)setOrientation:(UIInterfaceOrientation)newOrientation
{
  supportedOrientation = newOrientation;
  orientationFlags = TiOrientationNone;
  TI_ORIENTATION_SET(orientationFlags, supportedOrientation);
}
- (BOOL)shouldAutorotateToInterfaceOrientation:(UIInterfaceOrientation)toInterfaceOrientation
{
  return (toInterfaceOrientation == supportedOrientation);
}

// New Autorotation support.
- (BOOL)shouldAutorotate
{
  return YES;
}
- (NSUInteger)supportedInterfaceOrientations
{
  return orientationFlags;
}
// Returns interface orientation masks.
- (UIInterfaceOrientation)preferredInterfaceOrientationForPresentation
{
  return supportedOrientation;
}

@end
#endif

@interface TiRootViewNeue : UIView
@end

@implementation TiRootViewNeue

- (void)motionEnded:(UIEventSubtype)motion withEvent:(UIEvent *)event
{
  if (event.type == UIEventTypeMotion && event.subtype == UIEventSubtypeMotionShake) {
    [[NSNotificationCenter defaultCenter] postNotificationName:kTiGestureShakeNotification object:event];
  }
}

- (BOOL)canBecomeFirstResponder
{
  return YES;
}

@end

@interface TiRootViewController (notifications_internal)
- (void)didOrientNotify:(NSNotification *)notification;
- (void)keyboardWillChangeFrame:(NSNotification *)notification;
- (void)keyboardDidChangeFrame:(NSNotification *)notification;
- (void)adjustFrameForUpSideDownOrientation:(NSNotification *)notification;
@end

@implementation TiRootViewController

@synthesize keyboardFocusedProxy = keyboardFocusedProxy;
@synthesize statusBarVisibilityChanged;
@synthesize statusBarInitiallyHidden;
@synthesize defaultStatusBarStyle;
- (void)dealloc
{
  RELEASE_TO_NIL(bgColor);
  RELEASE_TO_NIL(bgImage);
  RELEASE_TO_NIL(containedWindows);
  RELEASE_TO_NIL(modalWindows);
  RELEASE_TO_NIL(hostView);

  WARN_IF_BACKGROUND_THREAD; //NSNotificationCenter is not threadsafe!
  NSNotificationCenter *nc = [NSNotificationCenter defaultCenter];
  [nc removeObserver:self];
  [super dealloc];
}

- (id)init
{
  self = [super init];
  if (self != nil) {
    // Prefill orientation history
    orientationHistory[0] = UIInterfaceOrientationPortrait;
    orientationHistory[1] = UIInterfaceOrientationLandscapeLeft;
    orientationHistory[2] = UIInterfaceOrientationLandscapeRight;
    orientationHistory[3] = UIInterfaceOrientationPortraitUpsideDown;

    // Keyboard initialization
    leaveCurve = UIViewAnimationCurveEaseIn;
    enterCurve = UIViewAnimationCurveEaseIn;
    leaveDuration = 0.3;
    enterDuration = 0.3;
    curTransformAngle = 0;

    // Initialize default collections and confifs
    defaultOrientations = TiOrientationNone;
    containedWindows = [[NSMutableArray alloc] init];
    modalWindows = [[NSMutableArray alloc] init];

    // Initialize fallback view for non-Storyboard use cases
    if (![TiUtils isUsingLaunchScreenStoryboard]) {
      defaultImageView = [[UIImageView alloc] init];
    } else {
      defaultImageView = [[[[UIStoryboard storyboardWithName:@"LaunchScreen" bundle:[NSBundle mainBundle]] instantiateInitialViewController] view] retain];
    }
    [defaultImageView setAutoresizingMask:UIViewAutoresizingFlexibleHeight | UIViewAutoresizingFlexibleWidth];
    [defaultImageView setContentMode:UIViewContentModeScaleToFill];

    [self processInfoPlist];

    // Orientation- and keyboard notifications
    WARN_IF_BACKGROUND_THREAD;
    NSNotificationCenter *nc = [NSNotificationCenter defaultCenter];
    [nc addObserver:self selector:@selector(didOrientNotify:) name:UIDeviceOrientationDidChangeNotification object:nil];
    [nc addObserver:self selector:@selector(keyboardWillChangeFrame:) name:UIKeyboardWillChangeFrameNotification object:nil];
    [nc addObserver:self selector:@selector(keyboardDidChangeFrame:) name:UIKeyboardDidChangeFrameNotification object:nil];
    [nc addObserver:self selector:@selector(adjustFrameForUpSideDownOrientation:) name:UIApplicationDidChangeStatusBarFrameNotification object:nil];
    [[UIDevice currentDevice] beginGeneratingDeviceOrientationNotifications];
  }
  return self;
}

- (UIStatusBarStyle)styleFromString:(NSString *)theString
{
  if (!IS_NULL_OR_NIL(theString)) {
    if ([theString isEqualToString:@"UIStatusBarStyleDefault"]) {
      return UIStatusBarStyleDefault;
    } else if ([theString isEqualToString:@"UIStatusBarStyleBlackTranslucent"] || [theString isEqualToString:@"UIStatusBarStyleLightContent"] || [theString isEqualToString:@"UIStatusBarStyleBlackOpaque"]) {
      return UIStatusBarStyleLightContent;
    } else if ([theString isEqualToString:@"UIStatusBarStyleDarkContent"]) {
      return UIStatusBarStyleDarkContent;
    }
  }
  return UIStatusBarStyleDefault;
}

- (void)processInfoPlist
{
  //read the default orientations
  [self getDefaultOrientations];

  //read the default value of UIStatusBarHidden
  id statHidden = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"UIStatusBarHidden"];
  statusBarInitiallyHidden = [TiUtils boolValue:statHidden];
  //read the value of UIViewControllerBasedStatusBarAppearance
  id vcbasedStatHidden = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"UIViewControllerBasedStatusBarAppearance"];
  viewControllerControlsStatusBar = [TiUtils boolValue:vcbasedStatHidden def:YES];
  //read the value of statusBarStyle
  id statusStyle = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"UIStatusBarStyle"];
  defaultStatusBarStyle = [self styleFromString:statusStyle];
}

- (void)loadView
{
  TiRootViewNeue *rootView = [[TiRootViewNeue alloc] initWithFrame:[TiUtils frameForController:self]];
  self.view = rootView;
  rootView.autoresizingMask = UIViewAutoresizingFlexibleHeight | UIViewAutoresizingFlexibleWidth;
  [self updateBackground];

  UIView *theHost = nil;

  hostView = [[UIView alloc] initWithFrame:[rootView bounds]];
  hostView.backgroundColor = [UIColor clearColor];

  hostView.autoresizingMask = UIViewAutoresizingFlexibleHeight | UIViewAutoresizingFlexibleWidth;
  [rootView addSubview:hostView];
  theHost = hostView;

  if (defaultImageView != nil) {
    [self rotateDefaultImageViewToOrientation:[[UIApplication sharedApplication] statusBarOrientation]];
    [theHost addSubview:defaultImageView];
  }
  [rootView becomeFirstResponder];
  [rootView release];
}

#pragma mark Remote Control Notifications

- (void)remoteControlReceivedWithEvent:(UIEvent *)event
{
  /*Can not find code associated with this anywhere. Keeping in place just in case*/
  [[NSNotificationCenter defaultCenter] postNotificationName:kTiRemoteControlNotification object:self userInfo:[NSDictionary dictionaryWithObject:event forKey:@"event"]];
}

#pragma mark - TiRootControllerProtocol
//Background Control
- (void)updateBackground
{
  UIView *ourView = [self view];
  UIColor *chosenColor = bgColor;

  if (chosenColor == nil) {
    chosenColor = [[TiSharedConfig defaultConfig] defaultBackgroundColor];
    if (chosenColor == nil) {
      chosenColor = UIColor.blackColor;
    }
  }

  [ourView setBackgroundColor:chosenColor];
  [[ourView superview] setBackgroundColor:chosenColor];
  if (bgImage != nil) {
    [[ourView layer] setContents:(id)bgImage.CGImage];
  } else {
    [[ourView layer] setContents:nil];
  }
}

- (void)setBackgroundImage:(UIImage *)newImage
{
  if ((newImage == bgImage) || [bgImage isEqual:newImage]) {
    return;
  }
  [bgImage release];
  bgImage = [newImage retain];
  TiThreadPerformOnMainThread(
      ^{
        [self updateBackground];
      },
      NO);
}

- (void)setBackgroundColor:(UIColor *)newColor
{
  if ((newColor == bgColor) || [bgColor isEqual:newColor]) {
    return;
  }
  [bgColor release];
  bgColor = [newColor retain];
  TiThreadPerformOnMainThread(
      ^{
        [self updateBackground];
      },
      NO);
}

- (void)dismissDefaultImage
{
  if (defaultImageView != nil) {
    [defaultImageView setHidden:YES];
    [defaultImageView removeFromSuperview];
    RELEASE_TO_NIL(defaultImageView);
  }
}

- (UIImage *)defaultImageForOrientation:(UIDeviceOrientation)orientation resultingOrientation:(UIDeviceOrientation *)imageOrientation idiom:(UIUserInterfaceIdiom *)imageIdiom
{
  UIImage *image;

  if ([[UIDevice currentDevice] userInterfaceIdiom] == UIUserInterfaceIdiomPad) {
    *imageOrientation = orientation;
    *imageIdiom = UIUserInterfaceIdiomPad;
    // Specific orientation check
    switch (orientation) {
    case UIDeviceOrientationPortrait:
    case UIDeviceOrientationPortraitUpsideDown:
      image = [UIImage imageNamed:@"LaunchImage-700-Portrait"];
      break;
    case UIDeviceOrientationLandscapeLeft:
    case UIDeviceOrientationLandscapeRight:
      image = [UIImage imageNamed:@"LaunchImage-700-Landscape"];
      break;
    default:
      image = nil;
    }
    if (image != nil) {
      return image;
    }

    // Generic orientation check
    if (UIDeviceOrientationIsPortrait(orientation)) {
      image = [UIImage imageNamed:@"LaunchImage-700-Portrait"];
    } else if (UIDeviceOrientationIsLandscape(orientation)) {
      image = [UIImage imageNamed:@"LaunchImage-700-Landscape"];
    }

    if (image != nil) {
      return image;
    }
  }

  *imageOrientation = UIDeviceOrientationPortrait;
  *imageIdiom = UIUserInterfaceIdiomPhone;
  image = nil;

  // iPhone X / iPhone XS
  if ([TiUtils isSuperRetina5_8Inch]) {
    if (UIDeviceOrientationIsPortrait(orientation)) {
      // Portrait
      image = [UIImage imageNamed:@"LaunchImage-1100-Portrait-2436h@3x"];
    } else if (UIDeviceOrientationIsLandscape(orientation)) {
      // Landscape
      image = [UIImage imageNamed:@"LaunchImage-1100-Landscape-2436h@3x"];
    }
    if (image != nil) {
      *imageOrientation = orientation;
      return image;
    }
  }

  // iPhone XR
  if ([TiUtils isRetina6_1Inch]) {
    if (UIDeviceOrientationIsPortrait(orientation)) {
      // Portrait
      image = [UIImage imageNamed:@"LaunchImage-1200-Portrait-1792h"];
    } else if (UIDeviceOrientationIsLandscape(orientation)) {
      // Landscape
      image = [UIImage imageNamed:@"LaunchImage-1200-Landscape-1792h"];
    }
    if (image != nil) {
      *imageOrientation = orientation;
      return image;
    }
  }

  // iPhone XS Max
  if ([TiUtils isSuperRetina6_5Inch]) {
    if (UIDeviceOrientationIsPortrait(orientation)) {
      // Portrait
      image = [UIImage imageNamed:@"LaunchImage-1200-Portrait-2688h"];
    } else if (UIDeviceOrientationIsLandscape(orientation)) {
      // Landscape
      image = [UIImage imageNamed:@"LaunchImage-1200-Landscape-2688h"];
    }
    if (image != nil) {
      *imageOrientation = orientation;
      return image;
    }
  }

  // iPhone 6 Plus
  if ([TiUtils isRetina5_5Inch]) {
    if (UIDeviceOrientationIsPortrait(orientation)) {
      image = [UIImage imageNamed:@"LaunchImage-800-Portrait-736h@3x"];
    } else if (UIDeviceOrientationIsLandscape(orientation)) {
      image = [UIImage imageNamed:@"LaunchImage-800-Landscape-736h@3x"];
    }
    if (image != nil) {
      *imageOrientation = orientation;
      return image;
    }
  }

  // iPhone 6
  if ([TiUtils isRetina4_7Inch]) {
    image = [UIImage imageNamed:@"LaunchImage-800-667h"];
    if (image != nil) {
      return image;
    }
  }

  // iPhone 5
  if ([TiUtils isRetinaFourInch]) {
    image = [UIImage imageNamed:@"LaunchImage-700-568h@2x"];
    if (image != nil) {
      return image;
    }
  }

  // iPhone 4
  return [UIImage imageNamed:@"LaunchImage-700@2x"];
}

- (void)rotateDefaultImageViewToOrientation:(UIInterfaceOrientation)newOrientation;
{
  if (defaultImageView == nil || [TiUtils isUsingLaunchScreenStoryboard]) {
    return;
  }

  UIDeviceOrientation imageOrientation;
  UIUserInterfaceIdiom imageIdiom;
  UIUserInterfaceIdiom deviceIdiom = [[UIDevice currentDevice] userInterfaceIdiom];
  /*
     *	This code could stand for some refinement, but it is rarely called during
     *	an application's lifetime and is meant to recreate the quirks and edge cases
     *	that iOS uses during application startup, including Apple's own
     *	inconsistencies between iPad and iPhone.
     */

  UIImage *defaultImage = [self defaultImageForOrientation:
                                    (UIDeviceOrientation)newOrientation
                                      resultingOrientation:&imageOrientation
                                                     idiom:&imageIdiom];

  CGFloat imageScale = [defaultImage scale];
  CGRect newFrame = [[self view] bounds];
  CGSize imageSize = [defaultImage size];
  UIViewContentMode contentMode = UIViewContentModeScaleToFill;

  if (imageOrientation == UIDeviceOrientationPortrait) {
    if (newOrientation == UIInterfaceOrientationLandscapeLeft) {
      UIImageOrientation imageOrientation;
      if (deviceIdiom == UIUserInterfaceIdiomPad) {
        imageOrientation = UIImageOrientationLeft;
      } else {
        imageOrientation = UIImageOrientationRight;
      }
      defaultImage = [UIImage imageWithCGImage:[defaultImage CGImage] scale:imageScale orientation:imageOrientation];
      imageSize = CGSizeMake(imageSize.height, imageSize.width);
      if (imageScale > 1.5) {
        contentMode = UIViewContentModeCenter;
      }
    } else if (newOrientation == UIInterfaceOrientationLandscapeRight) {
      defaultImage = [UIImage imageWithCGImage:[defaultImage CGImage] scale:imageScale orientation:UIImageOrientationLeft];
      imageSize = CGSizeMake(imageSize.height, imageSize.width);
      if (imageScale > 1.5) {
        contentMode = UIViewContentModeCenter;
      }
    } else if ((newOrientation == UIInterfaceOrientationPortraitUpsideDown) && (deviceIdiom == UIUserInterfaceIdiomPhone)) {
      defaultImage = [UIImage imageWithCGImage:[defaultImage CGImage] scale:imageScale orientation:UIImageOrientationDown];
      if (imageScale > 1.5) {
        contentMode = UIViewContentModeCenter;
      }
    }
  }

  if (imageSize.width == newFrame.size.width) {
    CGFloat overheight;
    overheight = imageSize.height - newFrame.size.height;
    if (overheight > 0.0) {
      newFrame.origin.y -= overheight;
      newFrame.size.height += overheight;
    }
  }
  [defaultImageView setContentMode:contentMode];
  [(UIImageView *)defaultImageView setImage:defaultImage];
  [defaultImageView setFrame:newFrame];
}

#pragma mark - Keyboard Control

- (void)dismissKeyboard
{
  [keyboardFocusedProxy blur:nil];
}

- (BOOL)keyboardVisible
{
  return keyboardVisible;
}

- (void)adjustKeyboardHeight:(NSNumber *)_keyboardVisible
{
  if (!updatingAccessoryView && [TiUtils boolValue:_keyboardVisible] == keyboardVisible) {
    updatingAccessoryView = YES;
    [self performSelector:@selector(handleNewKeyboardStatus) withObject:nil afterDelay:0.0];
    if (!keyboardVisible) {
      RELEASE_TO_NIL_AUTORELEASE(keyboardFocusedProxy);
    }
  }
}

- (void)keyboardDidChangeFrame:(NSNotification *)notification
{
  CGRect keyboardEndFrame = [[notification.userInfo objectForKey:UIKeyboardFrameEndUserInfoKey] CGRectValue];
  CGRect screenRect = [[UIScreen mainScreen] bounds];
  startFrame = endFrame;
  if (CGRectIntersectsRect(keyboardEndFrame, screenRect)) {
    // Keyboard is visible
    [self performSelector:@selector(adjustKeyboardHeight:) withObject:[NSNumber numberWithBool:YES] afterDelay:enterDuration];
  } else {
    // Keyboard is hidden
    [self performSelector:@selector(adjustKeyboardHeight:) withObject:[NSNumber numberWithBool:NO]];
  }
}

- (void)keyboardWillChangeFrame:(NSNotification *)notification
{
  NSDictionary *userInfo = [notification userInfo];
  enterCurve = [[userInfo valueForKey:UIKeyboardAnimationCurveUserInfoKey] intValue];
  enterDuration = [[userInfo valueForKey:UIKeyboardAnimationDurationUserInfoKey] floatValue];
  [self extractKeyboardInfo:userInfo];
  CGRect keyboardEndFrame = [[notification.userInfo objectForKey:UIKeyboardFrameEndUserInfoKey] CGRectValue];
  CGRect screenRect = [[UIScreen mainScreen] bounds];
  startFrame = endFrame;
  if (CGRectIntersectsRect(keyboardEndFrame, screenRect)) { // Keyboard is visible
    keyboardVisible = YES;
  } else {
    // Keyboard is hidden
    keyboardVisible = NO;
  }
  if (!updatingAccessoryView) {
    updatingAccessoryView = YES;
    [self performSelector:@selector(handleNewKeyboardStatus) withObject:nil afterDelay:0.0];
  }
}

- (UIView *)viewForKeyboardAccessory;
{
  return [[[[TiApp app] window] subviews] lastObject];
}

- (void)extractKeyboardInfo:(NSDictionary *)userInfo
{
  NSValue *v = nil;
  v = [userInfo valueForKey:UIKeyboardFrameEndUserInfoKey];

  if (v != nil) {
    endFrame = [v CGRectValue];
  }

  v = [userInfo valueForKey:UIKeyboardFrameBeginUserInfoKey];

  if (v != nil) {
    startFrame = [v CGRectValue];
  }
}

- (UIView *)keyboardAccessoryViewForProxy:(TiViewProxy<TiKeyboardFocusableView> *)visibleProxy withView:(UIView **)proxyView
{
  //If the toolbar actually contains the view, then we have to give that precidence.
  if ([visibleProxy viewInitialized]) {
    UIView *ourView = [visibleProxy view];
    *proxyView = ourView;

    while (ourView != nil) {
      if ((ourView == enteringAccessoryView) || (ourView == accessoryView) || (ourView == leavingAccessoryView)) {
        //We found a match!
        *proxyView = nil;
        return ourView;
      }
      ourView = [ourView superview];
    }
  } else {
    *proxyView = nil;
  }
  return [visibleProxy keyboardAccessoryView];
}

- (void)handleNewKeyboardStatus
{
  updatingAccessoryView = NO;
  UIView *ourView = [self viewForKeyboardAccessory];
  CGRect endingFrame = [ourView convertRect:endFrame fromView:nil];

  //Sanity check. Look at our focused proxy, and see if we mismarked it as leaving.
  TiUIView *scrolledView; //We check at the update anyways.

  UIView *focusedToolbar = [self keyboardAccessoryViewForProxy:keyboardFocusedProxy withView:&scrolledView];

  CGRect focusedToolbarBounds;
  //special case for undocked split keyboard
  if (CGRectEqualToRect(CGRectZero, endingFrame)) {
    focusedToolbarBounds = CGRectMake(0, 0, targetedFrame.size.width, [keyboardFocusedProxy keyboardAccessoryHeight]);
  } else {
    focusedToolbarBounds = CGRectMake(0, 0, endingFrame.size.width, [keyboardFocusedProxy keyboardAccessoryHeight]);
  }
  [focusedToolbar setBounds:focusedToolbarBounds];

  CGFloat keyboardHeight = endingFrame.origin.y;

  if ((scrolledView != nil) && (keyboardHeight > 0)) //If this isn't IN the toolbar, then we update the scrollviews to compensate.
  {
    UIView *possibleScrollView = [scrolledView superview];
    UIView<TiScrolling> *confirmedScrollView = nil;

    while (possibleScrollView != nil) {
      if ([possibleScrollView conformsToProtocol:@protocol(TiScrolling)]) {
        confirmedScrollView = (UIView<TiScrolling> *)possibleScrollView;
      }
      possibleScrollView = [possibleScrollView superview];
    }

    [confirmedScrollView keyboardDidShowAtHeight:keyboardHeight];
    [confirmedScrollView scrollToShowView:scrolledView withKeyboardHeight:keyboardHeight];
  }
}

- (void)didKeyboardFocusOnProxy:(TiViewProxy<TiKeyboardFocusableView> *)visibleProxy
{
  WARN_IF_BACKGROUND_THREAD_OBJ

  if ((visibleProxy == keyboardFocusedProxy) && (leavingAccessoryView == nil)) {
    DeveloperLog(@"[WARN] Focused for %@<%X>, despite it already being the focus.", keyboardFocusedProxy, keyboardFocusedProxy);
    return;
  }
  if (nil != keyboardFocusedProxy) {
    DeveloperLog(@"[WARN] Focused for %@<%X>, despite %@<%X> already being the focus.", visibleProxy, visibleProxy, keyboardFocusedProxy, keyboardFocusedProxy);
    [self didKeyboardBlurOnProxy:keyboardFocusedProxy];
    RELEASE_TO_NIL_AUTORELEASE(keyboardFocusedProxy);
  }

  keyboardFocusedProxy = [visibleProxy retain];

  TiUIView *unused; //We check at the update anyways.
  UIView *newView = [self keyboardAccessoryViewForProxy:visibleProxy withView:&unused];

  if ((newView == enteringAccessoryView) || (newView == accessoryView)) {
    //We're already up or soon will be.
    //Note that this is valid where newView can be accessoryView despite a new visibleProxy.
    //Specifically, if one proxy's view is a subview of another's toolbar.
  } else {
    if (enteringAccessoryView != nil) {
      DebugLog(@"[WARN] Moving in view %@, despite %@ already in line to move in.", newView, enteringAccessoryView);
      [enteringAccessoryView release];
    }

    if (newView == leavingAccessoryView) {
      //Hold on, you're not leaving YET! We don't need to release you since we're going to retain right afterwards.
      enteringAccessoryView = newView;
      leavingAccessoryView = nil;
    } else {
      enteringAccessoryView = [newView retain];
    }
  }

  if (!updatingAccessoryView) {
    updatingAccessoryView = YES;
    [self performSelector:@selector(handleNewKeyboardStatus) withObject:nil afterDelay:0.0];
  }
}

- (void)didKeyboardBlurOnProxy:(TiViewProxy<TiKeyboardFocusableView> *)blurredProxy
{
  WARN_IF_BACKGROUND_THREAD_OBJ
  if (blurredProxy != keyboardFocusedProxy) {
    DeveloperLog(@"[WARN] Blurred for %@<%X>, despite %@<%X> being the focus.", blurredProxy, blurredProxy, keyboardFocusedProxy, keyboardFocusedProxy);
    return;
  }

  TiUIView *scrolledView; //We check at the update anyways.
  UIView *doomedView = [self keyboardAccessoryViewForProxy:blurredProxy withView:&scrolledView];

  if (doomedView != accessoryView) {
    DeveloperLog(@"[WARN] Trying to blur out %@, but %@ is the one with focus.", doomedView, accessoryView);
    return;
  }

  if ((doomedView == nil) || (leavingAccessoryView == doomedView)) {
    //Nothing to worry about. No toolbar or it's on its way out.
    return;
  }

  if (leavingAccessoryView != nil) {
    DeveloperLog(@"[WARN] Trying to blur out %@, but %@ is already leaving focus.", accessoryView, leavingAccessoryView);
    [leavingAccessoryView removeFromSuperview];
    RELEASE_TO_NIL_AUTORELEASE(leavingAccessoryView);
  }

  leavingAccessoryView = accessoryView;
  accessoryView = nil;

  if (!updatingAccessoryView) {
    updatingAccessoryView = YES;
    [self performSelector:@selector(handleNewKeyboardStatus) withObject:nil afterDelay:0.0];
  }
}

- (void)animationDidStop:(NSString *)animationID finished:(NSNumber *)finished context:(void *)context
{
  if (![finished boolValue]) {
    return;
  }
  if (context == leavingAccessoryView) {
    [leavingAccessoryView removeFromSuperview];
    RELEASE_TO_NIL(leavingAccessoryView);
  }
}

- (UIView *)topWindowProxyView
{
  if ([modalWindows count] > 0) {
    return (UIView *)[[modalWindows lastObject] view];
  } else if ([containedWindows count] > 0) {
    return (UIView *)[[containedWindows lastObject] view];
  } else {
    return [self view];
  }
}

- (void)shutdownUi:(id)arg
{
  if (![TiSharedConfig defaultConfig].debugEnabled) {
    return;
  }
  //FIRST DISMISS ALL MODAL WINDOWS
  UIViewController *topVC = [self topPresentedController];
  if (topVC != self) {
    UIViewController *presenter = [topVC presentingViewController];
    [presenter dismissViewControllerAnimated:NO
                                  completion:^{
                                    [self shutdownUi:arg];
                                  }];
    return;
  }
  //At this point all modal stuff is done. Go ahead and clean up proxies.
  NSArray *modalCopy = [modalWindows copy];
  NSArray *windowCopy = [containedWindows copy];

  if (modalCopy != nil) {
    for (TiViewProxy *theWindow in [modalCopy reverseObjectEnumerator]) {
      [theWindow windowWillClose];
      [theWindow windowDidClose];
    }
    [modalCopy release];
  }
  if (windowCopy != nil) {
    for (TiViewProxy *theWindow in [windowCopy reverseObjectEnumerator]) {
      [theWindow windowWillClose];
      [theWindow windowDidClose];
    }
    [windowCopy release];
  }

  DebugLog(@"[INFO] UI SHUTDOWN COMPLETE. TRYING TO RESUME RESTART");
  if ([arg respondsToSelector:@selector(_resumeRestart:)]) {
    [arg performSelector:@selector(_resumeRestart:) withObject:nil];
  } else {
    DebugLog(@"[WARN] Could not resume. No selector _resumeRestart: found for arg");
  }
}

#pragma mark - TiControllerContainment
- (BOOL)canHostWindows
{
  return YES;
}

- (UIView *)hostingView
{
  if ([self canHostWindows] && [self isViewLoaded]) {
    return hostView;
  } else {
    return nil;
  }
}

- (void)willOpenWindow:(id<TiWindowProtocol>)theWindow
{
  [self dismissKeyboard];
  [[containedWindows lastObject] resignFocus];
  if ([theWindow isModal]) {
    [modalWindows addObject:theWindow];
  } else {
    [containedWindows addObject:theWindow];
    theWindow.parentOrientationController = self;
  }
}

- (void)didOpenWindow:(id<TiWindowProtocol>)theWindow
{
  [self dismissKeyboard];
  if ([self presentedViewController] == nil) {
    [self childOrientationControllerChangedFlags:[containedWindows lastObject]];
    [[containedWindows lastObject] gainFocus];
  }
  [self dismissDefaultImage];
}

- (void)willCloseWindow:(id<TiWindowProtocol>)theWindow
{
  [self dismissKeyboard];
  [theWindow resignFocus];
  if ([theWindow isModal]) {
    [modalWindows removeObject:theWindow];
  } else {
    [containedWindows removeObject:theWindow];
    theWindow.parentOrientationController = nil;
  }
}

- (void)didCloseWindow:(id<TiWindowProtocol>)theWindow
{
  [self dismissKeyboard];
  if ([self presentedViewController] == nil) {
    [self childOrientationControllerChangedFlags:[containedWindows lastObject]];
    [[containedWindows lastObject] gainFocus];
  }
}

@class TiViewController;

- (void)showControllerModal:(UIViewController *)theController animated:(BOOL)animated
{
  BOOL trulyAnimated = animated;
  UIViewController *topVC = [self topPresentedController];

  if ([topVC isBeingDismissed]) {
    topVC = [topVC presentingViewController];
  }

  if ([topVC isKindOfClass:[TiErrorNavigationController class]]) {
    DebugLog(@"[ERROR] ErrorController is up. ABORTING showing of modal controller");
    return;
  }

  if ([topVC isKindOfClass:[UIAlertController class]]) {
    if (((UIAlertController *)topVC).preferredStyle == UIAlertControllerStyleAlert) {
      trulyAnimated = NO;
      if (![theController isKindOfClass:[TiErrorNavigationController class]]) {
        DebugLog(@"[ERROR] UIAlertController is up and showing an alert. ABORTING showing of modal controller");
        return;
      }
    }
  }

  if (topVC == self) {
    [[containedWindows lastObject] resignFocus];
  } else if ([topVC respondsToSelector:@selector(proxy)]) {
    id theProxy = [(id)topVC proxy];
    if ([theProxy conformsToProtocol:@protocol(TiWindowProtocol)]) {
      [(id<TiWindowProtocol>)theProxy resignFocus];
    }
  }
  [self dismissKeyboard];
  if ([theController isKindOfClass:[TiViewController class]] && theController.popoverPresentationController == nil) {
    TiViewController *controller = (TiViewController *)theController;
    controller.presentationController.delegate = controller;
  }
  [topVC presentViewController:theController animated:trulyAnimated completion:nil];
}

- (void)hideControllerModal:(UIViewController *)theController animated:(BOOL)animated
{
  UIViewController *topVC = [self topPresentedController];
  if (topVC != theController) {
    DebugLog(@"[WARN] Dismissing a view controller when it is not the top presented view controller. Will probably crash now.");
  }
  BOOL trulyAnimated = animated;
  UIViewController *presenter = [theController presentingViewController];

  if ([presenter isKindOfClass:[UIAlertController class]]) {
    if (((UIAlertController *)presenter).preferredStyle == UIAlertControllerStyleAlert) {
      trulyAnimated = NO;
    }
  }
  [presenter dismissViewControllerAnimated:trulyAnimated
                                completion:^{
                                  if (presenter == self) {
                                    [self didCloseWindow:nil];
                                  } else {
                                    [self dismissKeyboard];

                                    if ([presenter respondsToSelector:@selector(proxy)]) {
                                      id theProxy = [(id)presenter proxy];
                                      if ([theProxy conformsToProtocol:@protocol(TiWindowProtocol)]) {
                                        [(id<TiWindowProtocol>)theProxy gainFocus];
                                      }
                                    } else {
                                      //This code block will only execute when errorController is presented on top of an alert
                                      if ([presenter isKindOfClass:[UIAlertController class]] && (((UIAlertController *)presenter).preferredStyle == UIAlertControllerStyleAlert)) {
                                        UIViewController *alertPresenter = [presenter presentingViewController];
                                        [alertPresenter dismissViewControllerAnimated:NO
                                                                           completion:^{
                                                                             [alertPresenter presentViewController:presenter animated:NO completion:nil];
                                                                           }];
                                      }
                                    }
                                  }
                                }];
}

#pragma mark - Orientation Control
- (TiOrientationFlags)getDefaultOrientations
{
  if (defaultOrientations == TiOrientationNone) {
    // Read the orientation values from the plist - if they exist.
    NSArray *orientations = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"UISupportedInterfaceOrientations"];
    TiOrientationFlags defaultFlags = TiOrientationPortrait;

    if ([TiUtils isIPad]) {
      defaultFlags = TiOrientationAny;
      NSArray *ipadOrientations = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"UISupportedInterfaceOrientations~ipad"];
      if ([ipadOrientations respondsToSelector:@selector(count)] && ([ipadOrientations count] > 0)) {
        orientations = ipadOrientations;
      }
    }

    if ([orientations respondsToSelector:@selector(count)] && ([orientations count] > 0)) {
      defaultFlags = TiOrientationNone;
      for (NSString *orientationString in orientations) {
        UIInterfaceOrientation orientation = (UIInterfaceOrientation)[TiUtils orientationValue:orientationString def:-1];
        switch (orientation) {
        case UIInterfaceOrientationLandscapeLeft:
        case UIInterfaceOrientationLandscapeRight:
        case UIInterfaceOrientationPortrait:
        case UIInterfaceOrientationPortraitUpsideDown:
          TI_ORIENTATION_SET(defaultFlags, orientation);
          break;

        default:
          break;
        }
      }
    }
    defaultOrientations = defaultFlags;
  }

  return defaultOrientations;
}

- (UIViewController *)topPresentedControllerCheckingPopover:(BOOL)checkPopover
{
  UIViewController *topmostController = self;
  UIViewController *presentedViewController = nil;
  while (topmostController != nil) {
    presentedViewController = [topmostController presentedViewController];
    if ((presentedViewController != nil) && checkPopover) {
      if (presentedViewController.modalPresentationStyle == UIModalPresentationPopover) {
        presentedViewController = nil;
      } else if ([presentedViewController isKindOfClass:[UIAlertController class]]) {
        presentedViewController = nil;
      }
    }
    if (presentedViewController != nil) {
      topmostController = presentedViewController;
      presentedViewController = nil;
    } else {
      break;
    }
  }
  return topmostController;
}

- (UIViewController *)topPresentedController
{
  return [self topPresentedControllerCheckingPopover:NO];
}

- (UIViewController<TiControllerContainment> *)topContainerController;
{
  UIViewController *topmostController = self;
  UIViewController *presentedViewController = nil;
  UIViewController<TiControllerContainment> *result = nil;
  UIViewController<TiControllerContainment> *match = nil;
  while (topmostController != nil) {
    if ([topmostController conformsToProtocol:@protocol(TiControllerContainment)]) {
      match = (UIViewController<TiControllerContainment> *)topmostController;
      if ([match canHostWindows]) {
        result = match;
      }
    }
    presentedViewController = [topmostController presentedViewController];
    if (presentedViewController != nil) {
      topmostController = presentedViewController;
      presentedViewController = nil;
    } else {
      break;
    }
  }

  return result;
}

- (CGRect)resizeView
{
  CGRect rect = [TiUtils frameForController:self];
  [[self view] setFrame:rect];
  return [[self view] bounds];
}

- (void)repositionSubviews
{
  //Since the window relayout is now driven from viewDidLayoutSubviews
  //this is not required. Leaving it in place in case someone is using it now.
  /*
    for (id<TiWindowProtocol> thisWindow in [containedWindows reverseObjectEnumerator]) {
        [TiLayoutQueue layoutProxy:(TiViewProxy*)thisWindow];
    }
    */
}

- (UIInterfaceOrientation)lastValidOrientation:(TiOrientationFlags)orientationFlags
{
  if (TI_ORIENTATION_ALLOWED(orientationFlags, deviceOrientation)) {
    return deviceOrientation;
  }
  for (int i = 0; i < 4; i++) {
    if (TI_ORIENTATION_ALLOWED(orientationFlags, orientationHistory[i])) {
      return orientationHistory[i];
    }
  }

  //This line should never happen, but just in case...
  return UIInterfaceOrientationPortrait;
}

- (BOOL)shouldRotateToInterfaceOrientation:(UIInterfaceOrientation)toInterfaceOrientation checkModal:(BOOL)check
{
  return TI_ORIENTATION_ALLOWED([self getFlags:check], toInterfaceOrientation) ? YES : NO;
}

- (void)adjustFrameForUpSideDownOrientation:(NSNotification *)notification
{
  if ((![TiUtils isIPad]) && ([[UIApplication sharedApplication] statusBarOrientation] == UIInterfaceOrientationPortraitUpsideDown)) {
    CGRect statusBarFrame = [[UIApplication sharedApplication] statusBarFrame];
    if (statusBarFrame.size.height == 0) {
      return;
    }

    CGRect mainScreenBounds = [[UIScreen mainScreen] bounds];
    CGRect viewBounds = [[self view] bounds];

    //Need to do this to force navigation bar to draw correctly on iOS7
    [[NSNotificationCenter defaultCenter] postNotificationName:kTiFrameAdjustNotification object:nil];
    if (statusBarFrame.size.height > 20) {
      if (viewBounds.size.height != (mainScreenBounds.size.height - statusBarFrame.size.height)) {
        CGRect newBounds = CGRectMake(0, 0, mainScreenBounds.size.width, mainScreenBounds.size.height - statusBarFrame.size.height);
        CGPoint newCenter = CGPointMake(mainScreenBounds.size.width / 2, (mainScreenBounds.size.height - statusBarFrame.size.height) / 2);
        [[self view] setBounds:newBounds];
        [[self view] setCenter:newCenter];
        [[self view] setNeedsLayout];
      }
    } else {
      if (viewBounds.size.height != mainScreenBounds.size.height) {
        CGRect newBounds = CGRectMake(0, 0, mainScreenBounds.size.width, mainScreenBounds.size.height);
        CGPoint newCenter = CGPointMake(mainScreenBounds.size.width / 2, mainScreenBounds.size.height / 2);
        [[self view] setBounds:newBounds];
        [[self view] setCenter:newCenter];
        [[self view] setNeedsLayout];
      }
    }
  }
}

#ifdef DEVELOPER
- (void)viewWillLayoutSubviews
{
  CGRect bounds = [[self hostingView] bounds];
  NSLog(@"ROOT WILL LAYOUT SUBVIEWS %.1f %.1f", bounds.size.width, bounds.size.height);
  [super viewWillLayoutSubviews];
}
#endif

- (void)viewDidLayoutSubviews
{
  if (curTransformAngle == 0 && forceLayout) {
    [[self hostingView] setFrame:self.view.bounds];
  }
#ifdef DEVELOPER
  CGRect bounds = [[self hostingView] bounds];
  NSLog(@"ROOT DID LAYOUT SUBVIEWS %.1f %.1f", bounds.size.width, bounds.size.height);
#endif
  for (id<TiWindowProtocol> thisWindow in containedWindows) {
    if ([thisWindow isKindOfClass:[TiViewProxy class]]) {
      if (!CGRectEqualToRect([(TiViewProxy *)thisWindow sandboxBounds], [[self hostingView] bounds])) {
        [(TiViewProxy *)thisWindow parentSizeWillChange];
      }
    }
  }
  forceLayout = NO;
  [super viewDidLayoutSubviews];
  [self adjustFrameForUpSideDownOrientation:nil];
}

- (BOOL)shouldAutomaticallyForwardAppearanceMethods
{
  return YES;
}

- (BOOL)shouldAutorotate
{
  return YES;
}

- (void)incrementActiveAlertControllerCount
{
  ++activeAlertControllerCount;
}
- (void)decrementActiveAlertControllerCount
{
  --activeAlertControllerCount;
  if (activeAlertControllerCount == 0) {
    UIViewController *topVC = [self topPresentedController];
    if (topVC == self) {
      [self didCloseWindow:nil];
    } else {
      [self dismissKeyboard];

      if ([topVC respondsToSelector:@selector(proxy)]) {
        id theProxy = [(id)topVC proxy];
        if ([theProxy conformsToProtocol:@protocol(TiWindowProtocol)]) {
          [(id<TiWindowProtocol>)theProxy gainFocus];
        }
      }
    }
  }
}

- (NSUInteger)supportedOrientationsForAppDelegate;
{
  if (forcingStatusBarOrientation) {
    return 0;
  }

  if (activeAlertControllerCount > 0) {
    return [self supportedInterfaceOrientations];
  }

  //Since this is used just for intersection, ok to return UIInterfaceOrientationMaskAll
  return 30; //UIInterfaceOrientationMaskAll
}

- (UIInterfaceOrientationMask)supportedInterfaceOrientations
{
  //IOS6. If forcing status bar orientation, this must return 0.
  if (forcingStatusBarOrientation) {
    return 0;
  }
  //IOS6. If we are presenting a modal view controller, get the supported
  //orientations from the modal view controller
  UIViewController *topmostController = [self topPresentedControllerCheckingPopover:YES];
  if (topmostController != self) {
    NSUInteger retVal = [topmostController supportedInterfaceOrientations];
    if ([topmostController isBeingDismissed]) {
      UIViewController *presenter = [topmostController presentingViewController];
      if (presenter == self) {
        return retVal | [self orientationFlags];
      } else {
        return retVal | [presenter supportedInterfaceOrientations];
      }
    } else {
      return retVal;
    }
  }
  return (UIInterfaceOrientationMask)[self orientationFlags];
}

- (UIInterfaceOrientation)preferredInterfaceOrientationForPresentation
{
  return [self lastValidOrientation:[self getFlags:NO]];
}

- (void)didOrientNotify:(NSNotification *)notification
{
  UIDeviceOrientation newOrientation = [[UIDevice currentDevice] orientation];

  if (!UIDeviceOrientationIsValidInterfaceOrientation(newOrientation)) {
    return;
  }
  deviceOrientation = (UIInterfaceOrientation)newOrientation;
  if ([self shouldRotateToInterfaceOrientation:deviceOrientation checkModal:NO]) {
    [self resetTransformAndForceLayout:YES];
    [self updateOrientationHistory:deviceOrientation];
  }
}

- (void)refreshOrientationWithDuration:(id)unused
{
  if (![[TiApp app] windowIsKeyWindow]) {
    VerboseLog(@"[DEBUG] RETURNING BECAUSE WE ARE NOT KEY WINDOW");
    return;
  }

  if (forcingRotation) {
    return;
  }

  UIInterfaceOrientation target = [self lastValidOrientation:[self getFlags:NO]];
  //Device Orientation takes precedence.
  if (target != deviceOrientation) {
    if ([self shouldRotateToInterfaceOrientation:deviceOrientation checkModal:NO]) {
      target = deviceOrientation;
    }
  }

  if ([[UIApplication sharedApplication] statusBarOrientation] != target) {
    forcingRotation = YES;
    if ([TiSharedConfig defaultConfig].debugEnabled) {
      DebugLog(@"Forcing rotation to %d. Current Orientation %d. This is not good UI design. Please reconsider.", target, [[UIApplication sharedApplication] statusBarOrientation]);
    }
#ifdef FORCE_WITH_MODAL
    [self forceRotateToOrientation:target];
#else
    [self rotateHostingViewToOrientation:target
                         fromOrientation:[[UIApplication sharedApplication] statusBarOrientation]];
    forcingRotation = NO;
#endif
  } else {
    [self resetTransformAndForceLayout:NO];
  }
}

- (void)updateOrientationHistory:(UIInterfaceOrientation)newOrientation
{
  /*
	 *	And now, to push the orientation onto the history stack. This could be
	 *	expressed as a for loop, but the loop is so small that it might as well
	 *	be unrolled. The end result of this push is that only other orientations
	 *	are copied back, ensuring the newOrientation will be unique when it's
	 *	placed at the top of the stack.
	 */
  int i = 0;
  for (int j = 0; j < 4; j++) {
    if (orientationHistory[j] == newOrientation) {
      i = j;
      break;
    }
  }
  while (i > 0) {
    orientationHistory[i] = orientationHistory[i - 1];
    i--;
  }
  orientationHistory[0] = newOrientation;
}

#ifdef FORCE_WITH_MODAL
- (void)forceRotateToOrientation:(UIInterfaceOrientation)newOrientation
{
  UIViewController *tempPresenter = [self topPresentedController];
  ForcingController *dummy = [[ForcingController alloc] init];
  [dummy setOrientation:newOrientation];
  forcingStatusBarOrientation = YES;

  [[UIApplication sharedApplication] setStatusBarOrientation:newOrientation animated:NO];

  forcingStatusBarOrientation = NO;

  [self updateOrientationHistory:newOrientation];

  [tempPresenter presentViewController:dummy
                              animated:NO
                            completion:^{
                              [UIViewController attemptRotationToDeviceOrientation];
                              [tempPresenter dismissViewControllerAnimated:NO completion:nil];
                              [dummy release];
                            }];
}
#endif

- (void)resetTransformAndForceLayout:(BOOL)updateStatusBar
{
  if (curTransformAngle != 0) {
    curTransformAngle = 0;
    forceLayout = YES;
    [[self hostingView] setTransform:CGAffineTransformIdentity];
    [[self view] setNeedsLayout];
    if (updateStatusBar) {
      [self updateStatusBar];
    }
  }
}

- (void)rotateHostingViewToOrientation:(UIInterfaceOrientation)newOrientation fromOrientation:(UIInterfaceOrientation)oldOrientation
{
  if (!forcingRotation || (newOrientation == oldOrientation)) {
    return;
  }

  NSInteger offset = 0;
  CGAffineTransform transform;

  switch (oldOrientation) {
  case UIInterfaceOrientationPortrait:
  case UIInterfaceOrientationUnknown:

    if (newOrientation == UIInterfaceOrientationPortraitUpsideDown) {
      offset = 180;
    } else if (newOrientation == UIInterfaceOrientationLandscapeLeft) {
      offset = -90;
    } else if (newOrientation == UIInterfaceOrientationLandscapeRight) {
      offset = 90;
    }
    break;

  case UIInterfaceOrientationLandscapeLeft:
    if (newOrientation == UIInterfaceOrientationPortraitUpsideDown) {
      offset = -90;
    } else if (newOrientation == UIInterfaceOrientationPortrait) {
      offset = 90;
    } else if (newOrientation == UIInterfaceOrientationLandscapeRight) {
      offset = 180;
    }
    break;

  case UIInterfaceOrientationLandscapeRight:
    if (newOrientation == UIInterfaceOrientationPortraitUpsideDown) {
      offset = 90;
    } else if (newOrientation == UIInterfaceOrientationPortrait) {
      offset = -90;
    } else if (newOrientation == UIInterfaceOrientationLandscapeLeft) {
      offset = 180;
    }
    break;

  case UIInterfaceOrientationPortraitUpsideDown:
    if (newOrientation == UIInterfaceOrientationPortrait) {
      offset = 180;
    } else if (newOrientation == UIInterfaceOrientationLandscapeLeft) {
      offset = 90;
    } else if (newOrientation == UIInterfaceOrientationLandscapeRight) {
      offset = -90;
    }
    break;
  }
  //Blur out keyboard
  [keyboardFocusedProxy blur:nil];

  //Rotate statusbar
  /*
     We will not rotae the status bar here but will temporarily force hide it. That way we will get
     correct size in viewWillTransitionToSize and re-enable visibility there. If we force the status
     bar to rotate the sizes are completely messed up.
    forcingStatusBarOrientation = YES;
    [[UIApplication sharedApplication] setStatusBarOrientation:newOrientation animated:NO];
    forcingStatusBarOrientation = NO;
    */
  curTransformAngle = offset % 360;

  switch (curTransformAngle) {
  case 90:
  case -270:
    transform = CGAffineTransformMakeRotation(M_PI_2);
    break;
  case -90:
  case 270:
    transform = CGAffineTransformMakeRotation(-M_PI_2);
    break;
  case 180:
    transform = CGAffineTransformMakeRotation(M_PI);
    break;
  default:
    transform = CGAffineTransformIdentity;
    break;
  }
  [hostView setTransform:transform];
  [hostView setFrame:self.view.bounds];
}

#pragma mark - TiOrientationController
- (void)childOrientationControllerChangedFlags:(id<TiOrientationController>)orientationController;
{
  WARN_IF_BACKGROUND_THREAD_OBJ;
  if ([self presentedViewController] == nil && isCurrentlyVisible) {
#if !TARGET_OS_MACCATALYST
    [self refreshOrientationWithDuration:nil];
#endif
    [self updateStatusBar];
  }

  if ([self respondsToSelector:@selector(setNeedsUpdateOfHomeIndicatorAutoHidden)]) {
    [self setNeedsUpdateOfHomeIndicatorAutoHidden];
  }
}

- (void)setParentOrientationController:(id<TiOrientationController>)newParent
{
  //Blank method since we never have a parent.
}

- (id)parentOrientationController
{
  //Blank method since we never have a parent.
  return nil;
}

- (TiOrientationFlags)orientationFlags
{
  return [self getFlags:YES];
}

- (TiOrientationFlags)getFlags:(BOOL)checkModal
{
  TiOrientationFlags result = TiOrientationNone;
  if (checkModal) {
    for (id<TiWindowProtocol> thisWindow in [modalWindows reverseObjectEnumerator]) {
      if (![thisWindow closing]) {
        result = [thisWindow orientationFlags];
        if (result != TiOrientationNone) {
          return result;
        }
      }
    }
  }
  for (id<TiWindowProtocol> thisWindow in [containedWindows reverseObjectEnumerator]) {
    if (![thisWindow closing]) {
      result = [thisWindow orientationFlags];
      if (result != TiOrientationNone) {
        return result;
      }
    }
  }
  return [self getDefaultOrientations];
}

#pragma mark - Appearance and rotation callbacks

- (void)traitCollectionDidChange:(UITraitCollection *)previousTraitCollection;
{
  [self resetTransformAndForceLayout:YES];

  [[NSNotificationCenter defaultCenter] postNotificationName:kTiTraitCollectionChanged object:self];

  [super traitCollectionDidChange:previousTraitCollection];
}

//Containing controller will call these callbacks(appearance/rotation) on contained windows when it receives them.
- (void)viewWillAppear:(BOOL)animated
{
  for (id<TiWindowProtocol> thisWindow in containedWindows) {
    [thisWindow viewWillAppear:animated];
  }
  [super viewWillAppear:animated];
}
- (void)viewWillDisappear:(BOOL)animated
{
  for (id<TiWindowProtocol> thisWindow in containedWindows) {
    [thisWindow viewWillDisappear:animated];
  }
  [[containedWindows lastObject] resignFocus];
  [super viewWillDisappear:animated];
}
- (void)viewDidAppear:(BOOL)animated
{
  isCurrentlyVisible = YES;
  [self.view becomeFirstResponder];
  if ([containedWindows count] > 0) {
    for (id<TiWindowProtocol> thisWindow in containedWindows) {
      [thisWindow viewDidAppear:animated];
    }
    forcingRotation = NO;
    [self performSelector:@selector(childOrientationControllerChangedFlags:) withObject:[containedWindows lastObject] afterDelay:[[UIApplication sharedApplication] statusBarOrientationAnimationDuration]];

    [[containedWindows lastObject] gainFocus];
  }
  [super viewDidAppear:animated];
}
- (void)viewDidDisappear:(BOOL)animated
{
  isCurrentlyVisible = NO;
  for (id<TiWindowProtocol> thisWindow in containedWindows) {
    [thisWindow viewDidDisappear:animated];
  }
  [super viewDidDisappear:animated];
}

- (void)viewWillTransitionToSize:(CGSize)size withTransitionCoordinator:(id<UIViewControllerTransitionCoordinator>)coordinator
{
  for (id<TiWindowProtocol> thisWindow in containedWindows) {
    [thisWindow viewWillTransitionToSize:size withTransitionCoordinator:coordinator];
  }
  UIInterfaceOrientation interfaceOrientation = (UIInterfaceOrientation)[[UIDevice currentDevice] orientation];
  [self updateOrientationHistory:interfaceOrientation];
  [self rotateDefaultImageViewToOrientation:interfaceOrientation];
  [super viewWillTransitionToSize:size withTransitionCoordinator:coordinator];
}

- (void)systemLayoutFittingSizeDidChangeForChildContentContainer:(id<UIContentContainer>)container
{
  for (id<TiWindowProtocol> thisWindow in containedWindows) {
    [thisWindow systemLayoutFittingSizeDidChangeForChildContentContainer:container];
  }
  [super systemLayoutFittingSizeDidChangeForChildContentContainer:container];
}

- (void)willTransitionToTraitCollection:(UITraitCollection *)newCollection withTransitionCoordinator:(id<UIViewControllerTransitionCoordinator>)coordinator
{
  for (id<TiWindowProtocol> thisWindow in containedWindows) {
    [thisWindow willTransitionToTraitCollection:newCollection withTransitionCoordinator:coordinator];
  }
  [super willTransitionToTraitCollection:newCollection withTransitionCoordinator:coordinator];
}

- (void)preferredContentSizeDidChangeForChildContentContainer:(id<UIContentContainer>)container
{
  for (id<TiWindowProtocol> thisWindow in containedWindows) {
    [thisWindow preferredContentSizeDidChangeForChildContentContainer:container];
  }
  [super preferredContentSizeDidChangeForChildContentContainer:container];
}

#pragma mark - HomeIndicatorAutoHidden

- (BOOL)prefersHomeIndicatorAutoHidden
{
  if ([containedWindows count] > 0) {
    return [[containedWindows lastObject] homeIndicatorAutoHide];
  }
  return NO;
}

#pragma mark - Status Bar Appearance

- (BOOL)prefersStatusBarHidden
{
  BOOL oldStatus = statusBarIsHidden;
  if ([containedWindows count] > 0) {
    statusBarIsHidden = [[containedWindows lastObject] hidesStatusBar];
    if (curTransformAngle != 0) {
      statusBarIsHidden = YES;
    }
  } else {
    statusBarIsHidden = oldStatus = statusBarInitiallyHidden;
  }

  statusBarVisibilityChanged = (statusBarIsHidden != oldStatus);
  return statusBarIsHidden;
}

- (UIStatusBarAnimation)preferredStatusBarUpdateAnimation
{
  return UIStatusBarAnimationNone;
}

- (UIStatusBarStyle)preferredStatusBarStyle
{
  if ([containedWindows count] > 0) {
    return [[containedWindows lastObject] preferredStatusBarStyle];
  }
  return defaultStatusBarStyle;
}

- (BOOL)modalPresentationCapturesStatusBarAppearance
{
  return YES;
}

- (void)updateStatusBar
{
  if (viewControllerControlsStatusBar) {
    [self performSelector:@selector(setNeedsStatusBarAppearanceUpdate) withObject:nil];
  } else {
    [UIApplication sharedApplication].statusBarHidden = [self prefersStatusBarHidden];
    [UIApplication sharedApplication].statusBarStyle = [self preferredStatusBarStyle];
    [self resizeView];
  }
}

@end
