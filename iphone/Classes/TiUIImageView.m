/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UIIMAGEVIEW

#import "TiUIImageView.h"
#import "TiUIImageViewProxy.h"
#import <CommonCrypto/CommonDigest.h>
#import <TitaniumKit/ImageLoader.h>
#import <TitaniumKit/OperationQueue.h>
#import <TitaniumKit/TiBase.h>
#import <TitaniumKit/TiBlob.h>
#import <TitaniumKit/TiFile.h>
#import <TitaniumKit/TiProxy.h>
#import <TitaniumKit/TiUtils.h>
#import <TitaniumKit/TiViewProxy.h>
#import <TitaniumKit/UIImage+Resize.h>

#define IMAGEVIEW_DEBUG 0

#define IMAGEVIEW_MIN_INTERVAL 30

@interface TiUIImageView ()
- (void)startTimerWithEvent:(NSString *)eventName;
- (void)stopTimerWithEvent:(NSString *)eventName;
@end

@implementation TiUIImageView

#pragma mark Internal

DEFINE_EXCEPTIONS

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
  if (timer != nil) {
    [timer invalidate];
  }
  RELEASE_TO_NIL(timer);
  RELEASE_TO_NIL(images);
  RELEASE_TO_NIL(container);
  RELEASE_TO_NIL(previous);
  RELEASE_TO_NIL(imageView);
  [super dealloc];
}

- (CGFloat)contentWidthForWidth:(CGFloat)suggestedWidth
{
  if (autoWidth > 0) {
    //If height is DIP returned a scaled autowidth to maintain aspect ratio
    if (TiDimensionIsDip(height) && autoHeight > 0) {
      return roundf(autoWidth * height.value / autoHeight);
    }
    return autoWidth;
  }

  CGFloat calculatedWidth = TiDimensionCalculateValue(width, autoWidth);
  if (calculatedWidth > 0) {
    return calculatedWidth;
  }

  if (container != nil) {
    return container.bounds.size.width;
  }

  return 0;
}

- (CGFloat)contentHeightForWidth:(CGFloat)width_
{
  if (width_ != autoWidth && autoWidth > 0 && autoHeight > 0) {
    return (width_ * autoHeight / autoWidth);
  }

  if (autoHeight > 0) {
    return autoHeight;
  }

  CGFloat calculatedHeight = TiDimensionCalculateValue(height, autoHeight);
  if (calculatedHeight > 0) {
    return calculatedHeight;
  }

  if (container != nil) {
    return container.bounds.size.height;
  }

  return 0;
}

- (void)frameSizeChanged:(CGRect)frame bounds:(CGRect)bounds
{
  for (UIView *child in [self subviews]) {
    [TiUtils setView:child positionRect:bounds];
  }
  if (container != nil) {
    for (UIView *child in [container subviews]) {
      [TiUtils setView:child positionRect:bounds];
    }
  }
  [super frameSizeChanged:frame bounds:bounds];
}

- (void)timerFired:(id)arg
{
  if (stopped) {
    return;
  }

  // don't let the placeholder stomp on our new images
  placeholderLoading = NO;

  NSInteger position = index % loadTotal;

  if (position < 0) {
    position = loadTotal - 1;
    index = position - 1;
  }
  UIView *view = [[container subviews] objectAtIndex:position];

  // see if we have an activity indicator... if we do, that means the image hasn't yet loaded
  // and we want to start the spinner to let the user know that we're still loading. we
  // don't initially start the spinner when added since we don't want to prematurely show
  // the spinner (usually for the first image) and then immediately remove it with a flash
  UIView *spinner = [[view subviews] count] > 0 ? [[view subviews] objectAtIndex:0] : nil;
  if (spinner != nil && [spinner isKindOfClass:[UIActivityIndicatorView class]]) {
    [(UIActivityIndicatorView *)spinner startAnimating];
    [view bringSubviewToFront:spinner];
  }

  // the container sits on top of the image in case the first frame (via setUrl) is first
  [self bringSubviewToFront:container];

  if (previous != nil) {
    previous.hidden = YES;
    RELEASE_TO_NIL(previous);
  }

  previous = [view retain];
  previous.hidden = NO;

  if ([self.proxy _hasListeners:@"change"]) {
    NSDictionary *evt = [NSDictionary dictionaryWithObject:NUMINTEGER(position) forKey:@"index"];
    [self.proxy fireEvent:@"change" withObject:evt];
  }

  if (repeatCount > 0 && ((!reverse && position == (loadTotal - 1)) || (reverse && position == 0))) {
    iterations++;
    if (iterations == repeatCount) {
      stopped = YES;
      [self.proxy replaceValue:NUMBOOL(NO) forKey:@"animating" notification:NO];
      [self.proxy replaceValue:NUMBOOL(YES) forKey:@"stopped" notification:NO];
      [self.proxy replaceValue:NUMBOOL(NO) forKey:@"paused" notification:NO];
      [self stopTimerWithEvent:@"stop"];
    }
  }
  index = (reverse ? --index : ++index);
}

- (void)queueImage:(id)img index:(NSUInteger)index_
{
#ifdef TI_USE_AUTOLAYOUT
  UIView *view = [[TiLayoutView alloc] init];
#else
  UIView *view = [[UIView alloc] initWithFrame:self.bounds];
#endif
  UIActivityIndicatorView *spinner = [[UIActivityIndicatorView alloc] initWithActivityIndicatorStyle:UIActivityIndicatorViewStyleWhiteLarge];

  spinner.center = view.center;
  spinner.autoresizingMask = UIViewAutoresizingFlexibleLeftMargin | UIViewAutoresizingFlexibleTopMargin | UIViewAutoresizingFlexibleRightMargin | UIViewAutoresizingFlexibleBottomMargin;

  [view addSubview:spinner];
  [container addSubview:view];
  [view release];
  [spinner release];

  [images addObject:img];
  [[OperationQueue sharedQueue] queue:@selector(loadImageInBackground:) target:self arg:NUMUINTEGER(index_) after:nil on:nil ui:NO];
}

- (void)startTimerWithEvent:(NSString *)eventName
{
  RELEASE_TO_NIL(timer);
  if (!stopped) {
    if ([self.proxy _hasListeners:eventName]) {
      [self.proxy fireEvent:eventName withObject:nil];
    }

    if ([eventName isEqualToString:@"start"] && previous == nil) {
      //TIMOB-18830. Load the first image immediately
      [self timerFired:nil];
    }

    timer = [[NSTimer scheduledTimerWithTimeInterval:interval target:self selector:@selector(timerFired:) userInfo:nil repeats:YES] retain];
  }
}

- (void)stopTimerWithEvent:(NSString *)eventName
{
  if (!stopped) {
    return;
  }
  if (timer != nil) {
    [timer invalidate];
    RELEASE_TO_NIL(timer);
    if ([self.proxy _hasListeners:eventName]) {
      [self.proxy fireEvent:eventName withObject:nil];
    }
  }
}

- (void)updateTimer
{
  if ([timer isValid] && !stopped) {

    [timer invalidate];
    RELEASE_TO_NIL(timer)

    timer = [[NSTimer scheduledTimerWithTimeInterval:interval target:self selector:@selector(timerFired:) userInfo:nil repeats:YES] retain];
  }
}

- (UIImage *)rotatedImage:(UIImage *)originalImage
{
  //If autorotate is set to false and the image orientation is not UIImageOrientationUp create new image
  if (![TiUtils boolValue:[[self proxy] valueForUndefinedKey:@"autorotate"] def:YES] && (originalImage.imageOrientation != UIImageOrientationUp)) {
    UIImage *theImage = [UIImage imageWithCGImage:[originalImage CGImage] scale:[originalImage scale] orientation:UIImageOrientationUp];
    return theImage;
  } else {
    return originalImage;
  }
}

- (void)fireLoadEventWithState:(NSString *)stateString
{
  TiUIImageViewProxy *ourProxy = (TiUIImageViewProxy *)self.proxy;
  [ourProxy propagateLoadEvent:stateString];
}

- (void)animationCompleted:(NSString *)animationID finished:(NSNumber *)finished context:(void *)context
{
  for (UIView *view in [self subviews]) {
    // look for our alpha view which is the placeholder layer
    if (view.alpha == 0) {
      [view removeFromSuperview];
      break;
    }
  }
}

- (UIViewContentMode)contentModeForImageView
{
  int contentMode = [TiUtils intValue:[self.proxy valueForKey:@"scalingMode"] def:-1];
  if (contentMode < 0) {
    if (TiDimensionIsAuto(width) || TiDimensionIsAutoSize(width) || TiDimensionIsUndefined(width) || TiDimensionIsAuto(height) || TiDimensionIsAutoSize(height) || TiDimensionIsUndefined(height)) {
      contentMode = UIViewContentModeScaleAspectFit;
    } else {
      contentMode = UIViewContentModeScaleToFill;
    }
  }
  return contentMode;
}

- (void)updateContentMode
{
  UIViewContentMode curMode = [self contentModeForImageView];
  if (imageView != nil) {
    imageView.contentMode = curMode;
  }
  if (container != nil) {
    for (UIView *view in [container subviews]) {
      UIView *child = [[view subviews] count] > 0 ? [[view subviews] objectAtIndex:0] : nil;
      if (child != nil && [child isKindOfClass:[UIImageView class]]) {
        child.contentMode = curMode;
      }
    }
  }
}

- (UIImageView *)imageView
{
  if (imageView == nil) {
    imageView = [[UIImageView alloc] initWithFrame:[self bounds]];
    [imageView setAutoresizingMask:UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight];
    [imageView setContentMode:[self contentModeForImageView]];
    [self addSubview:imageView];
  }
  return imageView;
}

- (id)accessibilityElement
{
  return [self imageView];
}

- (void)setURLImageOnUIThread:(UIImage *)image
{
  ENSURE_UI_THREAD(setURLImageOnUIThread, image);
  if (self.proxy == nil) {
    // this can happen after receiving an async callback for loading the image
    // but after we've detached our view.  In which case, we need to just ignore this
    return;
  }

  [self setTintedImage:image];

  if (placeholderLoading) {
    UIImageView *iv = [self imageView];
    iv.alpha = 0;

    [(TiViewProxy *)[self proxy] contentsWillChange];

    // do a nice fade in animation to replace the new incoming image
    // with our placeholder
    [UIView beginAnimations:nil context:nil];
    [UIView setAnimationDuration:0.5];
    [UIView setAnimationDelegate:self];
    [UIView setAnimationDidStopSelector:@selector(animationCompleted:finished:context:)];

    for (UIView *view in [self subviews]) {
      if (view != iv) {
        [view setAlpha:0];
      }
    }

    iv.alpha = 1;

    [UIView commitAnimations];

    placeholderLoading = NO;
    [self fireLoadEventWithState:@"image"];
  }
}

- (void)loadImageInBackground:(NSNumber *)pos
{
  int position = [TiUtils intValue:pos];
  NSURL *theurl = [TiUtils toURL:[images objectAtIndex:position] proxy:self.proxy];
  UIImage *theimage = [[ImageLoader sharedLoader] loadImmediateImage:theurl];
  if (theimage == nil) {
    theimage = [[ImageLoader sharedLoader] loadRemote:theurl];
  }
  if (theimage == nil) {
    NSLog(@"[ERROR] couldn't load imageview image: %@ at position: %d", theurl, position);
    return;
  }

  UIImage *imageToUse = [self rotatedImage:theimage];

  if (autoWidth < imageToUse.size.width) {
    autoWidth = imageToUse.size.width;
  }

  if (autoHeight < imageToUse.size.height) {
    autoHeight = imageToUse.size.height;
  }

  TiThreadPerformOnMainThread(
      ^{
        UIView *view = [[container subviews] objectAtIndex:position];
        UIImageView *newImageView = [[UIImageView alloc] initWithFrame:[view bounds]];
        newImageView.image = imageToUse;
        newImageView.autoresizingMask = UIViewAutoresizingFlexibleHeight | UIViewAutoresizingFlexibleWidth;
        newImageView.contentMode = [self contentModeForImageView];

        // remove the spinner now that we've loaded our image
        UIView *spinner = [[view subviews] count] > 0 ? [[view subviews] objectAtIndex:0] : nil;
        if (spinner != nil && [spinner isKindOfClass:[UIActivityIndicatorView class]]) {
          [spinner removeFromSuperview];
        }
        [view addSubview:newImageView];
        view.clipsToBounds = YES;
        [newImageView release];
        view.hidden = YES;

#if IMAGEVIEW_DEBUG == 1
        UILabel *label = [[UILabel alloc] initWithFrame:CGRectMake(10, 10, 50, 20)];
        label.text = [NSString stringWithFormat:@"%d", position];
        label.font = [UIFont boldSystemFontOfSize:28];
        label.textColor = [UIColor redColor];
        label.backgroundColor = [UIColor clearColor];
        [view addSubview:label];
        [view bringSubviewToFront:label];
        [label release];
#endif

        loadCount++;
        if (loadCount == loadTotal) {
          [self fireLoadEventWithState:@"images"];
        }

        if (ready) {
          //NOTE: for now i'm just making sure you have at least one frame loaded before starting the timer
          //but in the future we may want to be more sophisticated
          int min = 1;
          readyCount++;
          if (readyCount >= min) {
            readyCount = 0;
            ready = NO;

            [self startTimerWithEvent:@"start"];
          }
        }
      },
      NO);
}

- (void)removeAllImagesFromContainer
{
  // remove any existing images
  if (container != nil) {
    for (UIView *view in [container subviews]) {
      [view removeFromSuperview];
    }
  }
  if (imageView != nil) {
    imageView.image = nil;
  }
}

- (void)cancelPendingImageLoads
{
  // cancel a pending request if we have one pending
  [(TiUIImageViewProxy *)[self proxy] cancelPendingImageLoads];
  placeholderLoading = NO;
}

- (void)loadDefaultImage:(CGSize)imageSize
{
  // use a placeholder image - which the dev can specify with the
  // defaultImage property or we'll provide the Titanium stock one
  // if not specified
  NSURL *defURL = [TiUtils toURL:[self.proxy valueForKey:@"defaultImage"] proxy:self.proxy];

  if ((defURL == nil) && ![TiUtils boolValue:[self.proxy valueForKey:@"preventDefaultImage"] def:NO]) { //This is a special case, because it IS built into the bundle despite being in the simulator.
    NSString *filePath = [[[NSBundle mainBundle] resourcePath] stringByAppendingPathComponent:@"modules/ui/images/photoDefault.png"];
    defURL = [NSURL fileURLWithPath:filePath];
  }

  if (defURL != nil) {
    UIImage *poster = [[ImageLoader sharedLoader] loadImmediateImage:defURL withSize:imageSize];

    UIImage *imageToUse = [self rotatedImage:poster];

    // TODO: Use the full image size here?  Auto width/height is going to be changed once the image is loaded.
    autoWidth = imageToUse.size.width;
    autoHeight = imageToUse.size.height;
    [self setTintedImage:imageToUse];
  }
}

- (void)loadUrl:(NSURL *)img
{
  [self cancelPendingImageLoads];

  if (img != nil) {
    [self removeAllImagesFromContainer];

    // NOTE: Loading from URL means we can't pre-determine any % value.
    CGSize imageSize = CGSizeMake(TiDimensionCalculateValue(width, 0.0),
        TiDimensionCalculateValue(height, 0.0));

    if ([TiUtils boolValue:[[self proxy] valueForKey:@"hires"]]) {
      imageSize.width *= 2;
      imageSize.height *= 2;
    }

    // Skip the imageloader completely if this is obviously a file we can load off the fileystem.
    // why were we ever doing that in the first place...?
    if ([img isFileURL]) {
      UIImage *image = nil;
      NSString *pathStr = [img path];
      NSRange range = [pathStr rangeOfString:@".app"];
      NSString *imageArg = nil;
      if (range.location != NSNotFound) {
        if ([TiUtils isMacOS]) {
          imageArg = [pathStr substringFromIndex:range.location + 24]; //Contents/Resources/ for mac
        } else {
          imageArg = [pathStr substringFromIndex:range.location + 5];
        }
      }

      //remove suffixes.
      imageArg = [imageArg stringByReplacingOccurrencesOfString:@"@3x" withString:@""];
      imageArg = [imageArg stringByReplacingOccurrencesOfString:@"@2x" withString:@""];
      imageArg = [imageArg stringByReplacingOccurrencesOfString:@"~iphone" withString:@""];
      imageArg = [imageArg stringByReplacingOccurrencesOfString:@"~ipad" withString:@""];

      image = [UIImage imageNamed:imageArg];

      if (image == nil) {
        image = [UIImage imageWithContentsOfFile:[img path]];
      }

      if (image != nil) {
        UIImage *imageToUse = [self rotatedImage:image];
        autoWidth = imageToUse.size.width;
        autoHeight = imageToUse.size.height;
        [self setTintedImage:imageToUse];
        [self fireLoadEventWithState:@"image"];
      } else {
        [self loadDefaultImage:imageSize];
      }
      return;
    }

    UIImage *image = [[ImageLoader sharedLoader] loadImmediateImage:img];
    if (image == nil) {
      [self loadDefaultImage:imageSize];
      placeholderLoading = YES;
      [(TiUIImageViewProxy *)[self proxy] startImageLoad:img];
      return;
    }

    if (image != nil) {
      UIImage *imageToUse = [self rotatedImage:image];
      [(TiUIImageViewProxy *)[self proxy] setImageURL:img];

      autoWidth = imageToUse.size.width;
      autoHeight = imageToUse.size.height;
      if ([TiUtils boolValue:[[self proxy] valueForKey:@"hires"]]) {
        autoWidth = autoWidth / 2;
        autoHeight = autoHeight / 2;
      }
      [self setTintedImage:imageToUse];
      [self fireLoadEventWithState:@"image"];
    }
  }
}

- (void)setTintedImage:(UIImage *)image
{
  id tintColor = [self.proxy valueForUndefinedKey:@"tintColor"];

  if (tintColor != nil) {
    [[self imageView] setImage:[image imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate]];
    [[self imageView] setTintColor:[TiUtils colorValue:tintColor].color];
  } else {
    [[self imageView] setImage:image];
  }
}

- (UIView *)container
{
  if (container == nil) {
// we use a separate container view so we can both have an image
// and a set of images
#ifdef TI_USE_AUTOLAYOUT
    container = [[TiLayoutView alloc] initWithFrame:self.bounds];
#else
    container = [[UIView alloc] initWithFrame:self.bounds];
#endif
    container.userInteractionEnabled = NO;
    [self addSubview:container];
  }
  return container;
}

- (UIImage *)convertToUIImage:(id)arg
{
  UIImage *image = nil;

  if ([arg isKindOfClass:[TiBlob class]]) {
    TiBlob *blob = (TiBlob *)arg;
    image = [blob image];
  } else if ([arg isKindOfClass:[TiFile class]]) {
    TiFile *file = (TiFile *)arg;
    NSURL *fileUrl = [NSURL fileURLWithPath:[file path]];
    image = [[ImageLoader sharedLoader] loadImmediateImage:fileUrl];
  } else if ([arg isKindOfClass:[UIImage class]]) {
    // called within this class
    image = (UIImage *)arg;
  }

  UIImage *imageToUse = [self rotatedImage:image];

  if (imageToUse != nil) {
    autoHeight = imageToUse.size.height;
    autoWidth = imageToUse.size.width;
  } else {
    autoHeight = autoWidth = 0;
  }
  return imageToUse;
}

#pragma mark Public APIs

- (void)stop
{
  stopped = YES;
  [self stopTimerWithEvent:@"stop"];
  ready = NO;
  index = -1;
  iterations = -1;
  [self.proxy replaceValue:NUMBOOL(NO) forKey:@"animating" notification:NO];
  [self.proxy replaceValue:NUMBOOL(YES) forKey:@"stopped" notification:NO];
  [self.proxy replaceValue:NUMBOOL(YES) forKey:@"paused" notification:NO];
}

- (void)start
{
  stopped = NO;
  BOOL paused = [TiUtils boolValue:[self.proxy valueForKey:@"paused"] def:NO];
  [self.proxy replaceValue:NUMBOOL(NO) forKey:@"paused" notification:NO];
  [self.proxy replaceValue:NUMBOOL(NO) forKey:@"stopped" notification:NO];

  if (iterations < 0 || !paused) {
    iterations = 0;
  }

  if (index < 0 || !paused) {
    if (reverse) {
      index = loadTotal - 1;
    } else {
      index = 0;
    }
  }

  // refuse to start animation if you don't have any images
  if (loadTotal > 0) {
    ready = YES;
    [self.proxy replaceValue:NUMBOOL(YES) forKey:@"animating" notification:NO];

    if (timer == nil) {
      readyCount = 0;
      ready = NO;
      [self startTimerWithEvent:@"start"];
    }
  }
}

- (void)pause
{
  stopped = YES;
  [self.proxy replaceValue:NUMBOOL(YES) forKey:@"paused" notification:NO];
  [self.proxy replaceValue:NUMBOOL(NO) forKey:@"animating" notification:NO];
  [self stopTimerWithEvent:@"pause"];
}

- (void)resume
{
  stopped = NO;
  [self.proxy replaceValue:NUMBOOL(NO) forKey:@"paused" notification:NO];
  [self.proxy replaceValue:NUMBOOL(YES) forKey:@"animating" notification:NO];
  [self startTimerWithEvent:@"resume"];
}

- (void)setWidth_:(id)width_
{
  width = TiDimensionFromObject(width_);
  [self updateContentMode];
}

- (void)setHeight_:(id)height_
{
  height = TiDimensionFromObject(height_);
  [self updateContentMode];
}

- (void)setScalingMode_:(id)value
{
  [self updateContentMode];
}

- (void)setTintColor_:(id)value
{
  ENSURE_TYPE_OR_NIL(value, NSObject);
  UIImageRenderingMode renderingMode = value ? UIImageRenderingModeAlwaysTemplate : UIImageRenderingModeAlwaysOriginal;

  [imageView setImage:[[imageView image] imageWithRenderingMode:renderingMode]];
  [imageView setTintColor:value ? [[TiUtils colorValue:value] color] : nil];
}

- (void)setImage_:(id)arg
{
  id currentImage = [self.proxy valueForUndefinedKey:@"image"];

  UIImageView *imageview = [self imageView];

  [self removeAllImagesFromContainer];
  [self cancelPendingImageLoads];

  if (arg == nil || arg == imageview.image || [arg isEqual:@""] || [arg isKindOfClass:[NSNull class]]) {
    return;
  }

  UIImage *image = [self convertToUIImage:arg];

  if (image == nil) {
    NSURL *imageURL = [[self proxy] sanitizeURL:arg];
    if (![imageURL isKindOfClass:[NSURL class]]) {
      [self throwException:@"invalid image type"
                 subreason:[NSString stringWithFormat:@"expected TiBlob, String, TiFile, was: %@", [arg class]]
                  location:CODELOCATION];
    }

    [self loadUrl:imageURL];
    return;
  }

  [self setTintedImage:image];

  [(TiViewProxy *)[self proxy] contentsWillChange]; // Have to resize the proxy view to fit new subview size, if necessary

  if (currentImage != image) {
    [self fireLoadEventWithState:@"image"];
  }
}

- (void)setImages_:(id)args
{
  BOOL running = (timer != nil);

  [self stop];

  if (imageView != nil) {
    imageView.image = nil;
  }

  // remove any existing images
  [self removeAllImagesFromContainer];

  RELEASE_TO_NIL(images);
  ENSURE_TYPE_OR_NIL(args, NSArray);

  if (args != nil) {
    [self container];
    images = [[NSMutableArray alloc] initWithCapacity:[args count]];
    loadTotal = [args count];
    for (NSUInteger c = 0; c < [args count]; c++) {
      [self queueImage:[args objectAtIndex:c] index:c];
    }
  } else {
    RELEASE_TO_NIL(container);
  }

  // if we were running, re-start it
  if (running) {
    [self start];
  }
}

- (void)setDuration_:(id)duration
{
  float dur = [TiUtils floatValue:duration];
  dur = MAX(IMAGEVIEW_MIN_INTERVAL, dur);

  interval = dur / 1000;
  [self.proxy replaceValue:NUMINT(dur) forKey:@"duration" notification:NO];

  [self updateTimer];
}

- (void)setRepeatCount_:(id)count
{
  repeatCount = [TiUtils intValue:count];
}

- (void)setReverse_:(id)value
{
  reverse = [TiUtils boolValue:value];
}

#pragma mark ImageLoader delegates

- (void)imageLoadSuccess:(ImageLoaderRequest *)request image:(UIImage *)image
{
  UIImage *imageToUse = [self rotatedImage:image];

  autoWidth = imageToUse.size.width;
  autoHeight = imageToUse.size.height;

  //Setting hires to true causes image to de displayed at 50%
  if ([TiUtils boolValue:[[self proxy] valueForKey:@"hires"]]) {
    autoWidth = autoWidth / 2;
    autoHeight = autoHeight / 2;
  }

  TiThreadPerformOnMainThread(
      ^{
        [self setURLImageOnUIThread:imageToUse];
      },
      NO);
}

- (void)imageLoadFailed:(ImageLoaderRequest *)request error:(NSError *)error
{
  NSLog(@"[ERROR] Failed to load image: %@, Error: %@", [request url], error);
}

@end

#endif
