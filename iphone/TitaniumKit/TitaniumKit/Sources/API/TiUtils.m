/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2015 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#import <CommonCrypto/CommonDigest.h>
#import <QuartzCore/QuartzCore.h>

#import "ImageLoader.h"
#import "TiApp.h"
#import "TiBase.h"
#import "TiBlob.h"
#import "TiColor.h"
#import "TiDimension.h"
#import "TiExceptionHandler.h"
#import "TiFile.h"
#import "TiHost.h"
#import "TiPoint.h"
#import "TiProxy.h"
#import "TiSharedConfig.h"
#import "TiUIView.h"
#import "TiUtils.h"
#import "WebFont.h"

// for checking version
#import <sys/utsname.h>

#import "UIImage+Resize.h"

static NSDictionary *encodingMap = nil;
static NSDictionary *typeMap = nil;
static NSDictionary *sizeMap = nil;

@implementation TiUtils

+ (TiOrientationFlags)TiOrientationFlagsFromObject:(id)args
{
  if (![args isKindOfClass:[NSArray class]]) {
    return TiOrientationNone;
  }

  TiOrientationFlags result = TiOrientationNone;
  for (id mode in args) {
    UIInterfaceOrientation orientation = (UIInterfaceOrientation)[TiUtils orientationValue:mode def:-1];
    switch ((int)orientation) {
    case UIDeviceOrientationPortrait:
    case UIDeviceOrientationPortraitUpsideDown:
    case UIDeviceOrientationLandscapeLeft:
    case UIDeviceOrientationLandscapeRight:
      TI_ORIENTATION_SET(result, orientation);
      break;
    case UIDeviceOrientationUnknown:
      DebugLog(@"[WARN] Ti.Gesture.UNKNOWN / Ti.UI.UNKNOWN is an invalid orientation mode.");
      break;
    case UIDeviceOrientationFaceDown:
      DebugLog(@"[WARN] Ti.Gesture.FACE_DOWN / Ti.UI.FACE_DOWN is an invalid orientation mode.");
      break;
    case UIDeviceOrientationFaceUp:
      DebugLog(@"[WARN] Ti.Gesture.FACE_UP / Ti.UI.FACE_UP is an invalid orientation mode.");
      break;
    default:
      DebugLog(@"[WARN] An invalid orientation was requested. Ignoring.");
      break;
    }
  }
  return result;
}

+ (int)dpi
{
  if ([TiUtils isIPad]) {
    if ([TiUtils is2xRetina]) {
      return 260;
    }
    return 130;
  } else {
    if ([TiUtils is3xRetina]) {
      return 480;
    } else if ([TiUtils is2xRetina]) {
      return 320;
    }
    return 160;
  }
}

+ (BOOL)isRetinaFourInch
{
  CGSize mainScreenBoundsSize = UIScreen.mainScreen.bounds.size;
  return (mainScreenBoundsSize.height == 568 || mainScreenBoundsSize.width == 568);
}

+ (BOOL)isRetinaiPhone6
{
  return [TiUtils isRetina4_7Inch];
}

+ (BOOL)isRetinaiPhone6Plus
{
  return [TiUtils isRetina5_5Inch];
}

+ (BOOL)isRetinaiPhoneX
{
  return [TiUtils isSuperRetina5_8Inch];
}

+ (BOOL)isRetina4_7Inch
{
  CGSize mainScreenBoundsSize = UIScreen.mainScreen.bounds.size;
  return (mainScreenBoundsSize.height == 667 || mainScreenBoundsSize.width == 667);
}

+ (BOOL)isRetina5_5Inch
{
  CGSize mainScreenBoundsSize = UIScreen.mainScreen.bounds.size;
  return (mainScreenBoundsSize.height == 736 || mainScreenBoundsSize.width == 736);
}

+ (BOOL)isSuperRetina5_8Inch
{
  CGSize mainScreenBoundsSize = UIScreen.mainScreen.bounds.size;
  return (mainScreenBoundsSize.height == 812 || mainScreenBoundsSize.width == 812);
}

+ (BOOL)isRetina6_1Inch
{
  CGSize mainScreenBoundsSize = UIScreen.mainScreen.bounds.size;
  return (mainScreenBoundsSize.height == 896 || mainScreenBoundsSize.width == 896) && ![TiUtils is3xRetina];
}

+ (BOOL)isSuperRetina6_5Inch
{
  CGSize mainScreenBoundsSize = UIScreen.mainScreen.bounds.size;
  return (mainScreenBoundsSize.height == 896 || mainScreenBoundsSize.width == 896) && [TiUtils is3xRetina];
}

+ (BOOL)is3xRetina
{
  return UIScreen.mainScreen.scale == 3.0;
}

+ (BOOL)is2xRetina
{
  // since we call this alot, cache it
  static CGFloat scale = 0.0;
  if (scale == 0.0) {
    // NOTE: iPad in iPhone compatibility mode will return a scale factor of 2.0
    // when in 2x zoom, which leads to false positives and bugs. This tries to
    // future proof against possible different model names, but in the event of
    // an iPad with a retina display, this will need to be fixed.
    // Credit to Brion on github for the origional fix.
    UIDevice *device = UIDevice.currentDevice;
    if (device.userInterfaceIdiom == UIUserInterfaceIdiomPhone) {
      NSRange iPadStringPosition = [device.model rangeOfString:@"iPad"];
      if (iPadStringPosition.location != NSNotFound) {
        scale = 1.0;
        return NO;
      }
    }
    scale = UIScreen.mainScreen.scale;
  }
  return scale > 1.0; // TODO: In the future (next major), this should be == 2.0 which is a breaking change
}

+ (BOOL)isMacOS
{
#if TARGET_OS_MACCATALYST
  return YES;
#else
  //  TODO: Just return NO? Use NSProcessInfo.processInfo.isMacCatalystApp or iOSAppOnMac?
  return [UIDevice.currentDevice.systemName isEqualToString:@"Mac OS X"];
#endif
}

+ (BOOL)isRetinaHDDisplay
{
  return [TiUtils is3xRetina];
}

+ (BOOL)isRetinaDisplay
{
  return [TiUtils is2xRetina];
}

+ (BOOL)isIOS7OrGreater
{
  return [TiUtils isIOSVersionOrGreater:@"7.0"];
}

+ (BOOL)isIOS8OrGreater
{
  return [TiUtils isIOSVersionOrGreater:@"8.0"];
}

+ (BOOL)isIOS82rGreater
{
  return [TiUtils isIOSVersionOrGreater:@"8.2"];
}

+ (BOOL)isIOS9OrGreater
{
  return [TiUtils isIOSVersionOrGreater:@"9.0"];
}

+ (BOOL)isIOS9_1OrGreater
{
  return [TiUtils isIOSVersionOrGreater:@"9.1"];
}

+ (BOOL)isIOS9_3OrGreater
{
  return [TiUtils isIOSVersionOrGreater:@"9.3"];
}

+ (BOOL)isIOS10OrGreater
{
  return [TiUtils isIOSVersionOrGreater:@"10.0"];
}

+ (BOOL)isIOS11OrGreater
{
  return [TiUtils isIOSVersionOrGreater:@"11.0"];
}

+ (BOOL)isIOSVersionOrGreater:(NSString *)version
{
  return [UIDevice.currentDevice.systemVersion compare:version options:NSNumericSearch] != NSOrderedAscending;
}

+ (BOOL)isIOSVersionLower:(NSString *)version
{
  return [UIDevice.currentDevice.systemVersion compare:version options:NSNumericSearch] == NSOrderedAscending;
}

+ (BOOL)isIPad
{
  return UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad;
}

+ (BOOL)isIPhone4
{
  static BOOL iphoneChecked = NO;
  static BOOL isiPhone4 = NO;
  if (!iphoneChecked) {
    iphoneChecked = YES;
    // for now, this is all we know. we assume this
    // will continue to increase with new models but
    // for now we can't really assume
    if (UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPhone) {
      struct utsname u;
      uname(&u);
      if (!strcmp(u.machine, "iPhone3,1")) {
        isiPhone4 = YES;
      }
    }
  }
  return isiPhone4;
}

+ (NSString *)UTCDateForDate:(NSDate *)data
{
  NSDateFormatter *dateFormatter = [[[NSDateFormatter alloc] init] autorelease];
  NSTimeZone *timeZone = [NSTimeZone timeZoneWithName:@"UTC"];
  [dateFormatter setTimeZone:timeZone];

  NSLocale *USLocale = [[NSLocale alloc] initWithLocaleIdentifier:@"en_US"];
  [dateFormatter setLocale:USLocale];
  [USLocale release];

  //Example UTC full format: 2009-06-15T21:46:28.685+0000
  [dateFormatter setDateFormat:@"yyyy-MM-dd'T'HH:mm:ss'.'SSS+0000"];
  return [dateFormatter stringFromDate:data];
}

+ (NSDate *)dateForUTCDate:(NSString *)date
{
  NSDateFormatter *dateFormatter = [[[NSDateFormatter alloc] init] autorelease];
  NSTimeZone *timeZone = [NSTimeZone timeZoneWithName:@"UTC"];
  [dateFormatter setTimeZone:timeZone];

  NSLocale *USLocale = [[NSLocale alloc] initWithLocaleIdentifier:@"en_US"];
  [dateFormatter setLocale:USLocale];
  [USLocale release];

  [dateFormatter setDateFormat:@"yyyy-MM-dd'T'HH:mm:ss'.'SSS+0000"];
  return [dateFormatter dateFromString:date];
}

+ (NSString *)UTCDate
{
  return [TiUtils UTCDateForDate:[NSDate date]];
}

+ (NSString *)createUUID
{
  CFUUIDRef resultID = CFUUIDCreate(NULL);
  NSString *resultString = (NSString *)CFUUIDCreateString(NULL, resultID);
  CFRelease(resultID);
  return [resultString autorelease];
}

+ (TiFile *)createTempFile:(NSString *)extension
{
  return [TiFile createTempFile:extension];
}

+ (NSString *)encodeQueryPart:(NSString *)unencodedString
{
  return [unencodedString stringByAddingPercentEncodingWithAllowedCharacters:[NSCharacterSet URLQueryAllowedCharacterSet]];
}

+ (NSString *)encodeURIParameters:(NSString *)unencodedString
{
  // NOTE: we must encode each individual part for the to successfully work

  NSMutableString *result = [[[NSMutableString alloc] init] autorelease];

  NSArray *parts = [unencodedString componentsSeparatedByString:@"&"];
  for (int c = 0; c < [parts count]; c++) {
    NSString *part = [parts objectAtIndex:c];
    NSRange range = [part rangeOfString:@"="];

    if (range.location != NSNotFound) {
      [result appendString:[TiUtils encodeQueryPart:[part substringToIndex:range.location]]];
      [result appendString:@"="];
      [result appendString:[TiUtils encodeQueryPart:[part substringFromIndex:range.location + 1]]];
    } else {
      [result appendString:[TiUtils encodeQueryPart:part]];
    }

    if (c + 1 < [parts count]) {
      [result appendString:@"&"];
    }
  }

  return result;
}

+ (NSString *)stringValue:(id)value
{
  if (value == nil) {
    return nil;
  }

  if ([value isKindOfClass:[NSString class]]) {
    return (NSString *)value;
  }
  if ([value isKindOfClass:[NSURL class]]) {
    return [(NSURL *)value absoluteString];
  } else if ([value isKindOfClass:[NSNull class]]) {
    return nil;
  }
  if ([value respondsToSelector:@selector(stringValue)]) {
    return [value stringValue];
  }
  return [value description];
}

+ (BOOL)boolValue:(id)value def:(BOOL)def;
{
  if ([value respondsToSelector:@selector(boolValue)]) {
    return [value boolValue];
  }
  return def;
}

+ (BOOL)boolValue:(id)value
{
  return [self boolValue:value def:NO];
}

+ (double)doubleValue:(id)value
{
  return [self doubleValue:value def:0];
}

+ (double)doubleValue:(id)value def:(double)def
{
  return [self doubleValue:value def:def valid:NULL];
}

+ (double)doubleValue:(id)value def:(double)def valid:(BOOL *)isValid
{
  if ([value respondsToSelector:@selector(doubleValue)]) {
    if (isValid != NULL)
      *isValid = YES;
    return [value doubleValue];
  }
  return def;
}

+ (UIEdgeInsets)contentInsets:(id)value
{
  if ([value isKindOfClass:[NSDictionary class]]) {
    NSDictionary *dict = (NSDictionary *)value;

    TiDimension tDimension = [self dimensionValue:@"top" properties:dict];
    CGFloat t = tDimension.value;

    TiDimension lDimension = [self dimensionValue:@"left" properties:dict];
    CGFloat l = lDimension.value;

    TiDimension bDimension = [self dimensionValue:@"bottom" properties:dict];
    CGFloat b = bDimension.value;

    TiDimension rDimension = [self dimensionValue:@"right" properties:dict];
    CGFloat r = rDimension.value;

    return UIEdgeInsetsMake(t, l, b, r);
  }
  return UIEdgeInsetsMake(0, 0, 0, 0);
}

+ (CGRect)rectValue:(id)value
{
  if ([value isKindOfClass:[NSDictionary class]]) {
    NSDictionary *dict = (NSDictionary *)value;

    TiDimension xDimension = [self dimensionValue:@"x" properties:dict];
    CGFloat x = xDimension.value;

    TiDimension yDimension = [self dimensionValue:@"y" properties:dict];
    CGFloat y = yDimension.value;

    TiDimension wDimension = [self dimensionValue:@"width" properties:dict];
    CGFloat w = wDimension.value;

    TiDimension hDimension = [self dimensionValue:@"height" properties:dict];
    CGFloat h = hDimension.value;

    return CGRectMake(x, y, w, h);
  }
  return CGRectMake(0, 0, 0, 0);
}

+ (CGPoint)pointValue:(id)value
{
  return [self pointValue:value valid:NULL];
}

+ (CGPoint)pointValue:(id)value valid:(BOOL *)isValid
{
  if ([value isKindOfClass:[TiPoint class]]) {
    if (isValid) {
      *isValid = YES;
    }
    return [value point];
  } else if ([value isKindOfClass:[NSDictionary class]]) {
    TiDimension xDimension = [self dimensionValue:@"x" properties:value];
    TiDimension yDimension = [self dimensionValue:@"y" properties:value];
    if (!TiDimensionIsUndefined(xDimension) && !TiDimensionIsUndefined(yDimension)) {
      if (isValid) {
        *isValid = YES;
      }
      return CGPointMake(xDimension.value, yDimension.value);
    }
  }
  if (isValid) {
    *isValid = NO;
  }
  return CGPointMake(0, 0);
}

+ (CGPoint)pointValue:(id)value bounds:(CGRect)bounds defaultOffset:(CGPoint)defaultOffset;
{
  TiDimension xDimension;
  TiDimension yDimension;
  CGPoint result;

  if ([value isKindOfClass:[TiPoint class]]) {
    xDimension = [value xDimension];
    yDimension = [value yDimension];
  } else if ([value isKindOfClass:[NSDictionary class]]) {
    xDimension = [self dimensionValue:@"x" properties:value];
    yDimension = [self dimensionValue:@"y" properties:value];
  } else {
    xDimension = TiDimensionUndefined;
    yDimension = TiDimensionUndefined;
  }

  if (!TiDimensionDidCalculateValue(xDimension, bounds.size.width, &result.x)) {
    result.x = defaultOffset.x * bounds.size.width;
  }
  if (!TiDimensionDidCalculateValue(yDimension, bounds.size.height, &result.y)) {
    result.y = defaultOffset.y * bounds.size.height;
  }

  return CGPointMake(result.x + bounds.origin.x, result.y + bounds.origin.y);
}

+ (NSNumber *)numberFromObject:(id)obj
{
  if (obj == nil) {
    return nil;
  }

  if ([obj isKindOfClass:[NSNumber class]]) {
    return obj;
  }

  NSNumberFormatter *formatter = [[[NSNumberFormatter alloc] init] autorelease];

  return [formatter numberFromString:[self stringValue:obj]];
}

+ (CGFloat)floatValue:(id)value def:(CGFloat)def
{
  return [self floatValue:value def:def valid:NULL];
}

+ (CGFloat)floatValue:(id)value def:(CGFloat)def valid:(BOOL *)isValid
{
  if ([value respondsToSelector:@selector(floatValue)]) {
    if (isValid != NULL)
      *isValid = YES;
    return [value floatValue];
  }
  if (isValid != NULL) {
    *isValid = NO;
  }
  return def;
}

+ (CGFloat)floatValue:(id)value
{
  return [self floatValue:value def:NSNotFound];
}

/* Example:
 shadow = {
    offset: {
        width: 10,
        height: 10
    },
    blurRadius: 10,
    color: 'red'
 }
 */
+ (NSShadow *)shadowValue:(id)value
{
  if (![value isKindOfClass:[NSDictionary class]])
    return nil;

  NSShadow *shadow = [[NSShadow alloc] init];

  id offset = [value objectForKey:@"offset"];
  if (offset != nil && [offset isKindOfClass:[NSDictionary class]]) {
    NSDictionary *dict = (NSDictionary *)offset;

    TiDimension wDimension = [self dimensionValue:@"width" properties:dict];
    CGFloat w = wDimension.value;

    TiDimension hDimension = [self dimensionValue:@"height" properties:dict];
    CGFloat h = hDimension.value;

    [shadow setShadowOffset:CGSizeMake(w, h)];
  }

  id blurRadius = [value objectForKey:@"blurRadius"];
  TiDimension blurRadiusDimension = [self dimensionValue:blurRadius];
  if (!TiDimensionIsUndefined(blurRadiusDimension)) {
    [shadow setShadowBlurRadius:blurRadiusDimension.value];
  }

  id color = [value objectForKey:@"color"];
  if (color != nil) {
    [shadow setShadowColor:[[TiUtils colorValue:color] _color]];
  }
  return [shadow autorelease];
}

+ (int)intValue:(id)value def:(int)def valid:(BOOL *)isValid
{
  if ([value respondsToSelector:@selector(intValue)]) {
    if (isValid != NULL) {
      *isValid = YES;
    }
    return [value intValue];
  }
  if (isValid != NULL) {
    *isValid = NO;
  }
  return def;
}

+ (int)intValue:(id)value def:(int)def
{
  return [self intValue:value def:def valid:NULL];
}

+ (int)intValue:(id)value
{
  return [self intValue:value def:0];
}

+ (TiColor *)colorValue:(id)value
{
  if ([value isKindOfClass:[TiColor class]]) {
    return (TiColor *)value;
  }
  if ([value respondsToSelector:@selector(stringValue)]) {
    value = [value stringValue];
  }
  if ([value isKindOfClass:[NSString class]]) {
    return [TiColor colorNamed:value];
  }
  return nil;
}

+ (NSString *)hexColorValue:(UIColor *)color
{
  CGFloat red;
  CGFloat green;
  CGFloat blue;
  CGFloat alpha;
  BOOL wasConverted = [color getRed:&red
                              green:&green
                               blue:&blue
                              alpha:&alpha];
  if (!wasConverted) {
    return @"#000000";
  }
  if (lroundf(alpha * 255.0) != 255) {
    // AARRGGBB
    return [NSString stringWithFormat:@"#%02lX%02lX%02lX%02lX", lroundf(alpha * 255.0), lroundf(red * 255.0), lroundf(green * 255.0), lroundf(blue * 255.0)];
  }
  // RRGGBB
  return [NSString stringWithFormat:@"#%02lX%02lX%02lX", lroundf(red * 255.0), lroundf(green * 255.0), lroundf(blue * 255.0)];
}

+ (TiDimension)dimensionValue:(id)value
{
  return TiDimensionFromObject(value);
}

+ (id)valueFromDimension:(TiDimension)dimension
{
  switch (dimension.type) {
  case TiDimensionTypeUndefined:
    return [NSNull null];
  case TiDimensionTypeAuto:
    return @"auto";
  case TiDimensionTypeDip:
    return @(dimension.value);
  case TiDimensionTypePercent:
    return [NSString stringWithFormat:@"%li%%", (long)(dimension.value * 100)];
  default: {
    break;
  }
  }
  return nil;
}

+ (UIImage *)scaleImage:(UIImage *)image toSize:(CGSize)newSize
{
  if (!CGSizeEqualToSize(newSize, CGSizeZero)) {
    CGSize imageSize = [image size];
    if (newSize.width == 0) {
      newSize.width = imageSize.width;
    }
    if (newSize.height == 0) {
      newSize.height = imageSize.height;
    }
    if (!CGSizeEqualToSize(newSize, imageSize)) {
      image = [UIImageResize resizedImage:newSize interpolationQuality:kCGInterpolationDefault image:image hires:NO];
    }
  }
  return image;
}

+ (UIImage *)toImage:(id)object proxy:(TiProxy *)proxy size:(CGSize)imageSize
{
  if ([object isKindOfClass:[TiBlob class]]) {
    return [self scaleImage:[(TiBlob *)object image] toSize:imageSize];
  }

  if ([object isKindOfClass:[TiFile class]]) {
    TiFile *file = (TiFile *)object;
    UIImage *image = [UIImage imageWithContentsOfFile:[file path]];
    return [self scaleImage:image toSize:imageSize];
  }

  NSURL *urlAttempt = [self toURL:object proxy:proxy];
  UIImage *image = [[ImageLoader sharedLoader] loadImmediateImage:urlAttempt withSize:imageSize];
  return image;
  //Note: If url is a nonimmediate image, this returns nil.
}

+ (UIImage *)toImage:(id)object proxy:(TiProxy *)proxy
{
  if ([object isKindOfClass:[TiBlob class]]) {
    return [(TiBlob *)object image];
  }

  if ([object isKindOfClass:[TiFile class]]) {
    TiFile *file = (TiFile *)object;
    UIImage *image = [UIImage imageWithContentsOfFile:[file path]];
    return image;
  }

  NSURL *urlAttempt = [self toURL:object proxy:proxy];
  UIImage *image = [[ImageLoader sharedLoader] loadImmediateImage:urlAttempt];
  return image;
  //Note: If url is a nonimmediate image, this returns nil.
}

+ (UIImage *)adjustRotation:(UIImage *)image
{
  CGImageRef imgRef = image.CGImage;
  CGFloat width = CGImageGetWidth(imgRef);
  CGFloat height = CGImageGetHeight(imgRef);
  CGAffineTransform transform = CGAffineTransformIdentity;
  CGRect bounds = CGRectMake(0, 0, width, height);
  CGFloat scaleRatio = bounds.size.width / width;
  CGSize imageSize = CGSizeMake(CGImageGetWidth(imgRef), CGImageGetHeight(imgRef));
  CGFloat boundHeight;
  UIImageOrientation orient = image.imageOrientation;
  switch (orient) {

  case UIImageOrientationUp: //EXIF = 1
    transform = CGAffineTransformIdentity;
    break;

  case UIImageOrientationUpMirrored: //EXIF = 2
    transform = CGAffineTransformMakeTranslation(imageSize.width, 0.0);
    transform = CGAffineTransformScale(transform, -1.0, 1.0);
    break;

  case UIImageOrientationDown: //EXIF = 3
    transform = CGAffineTransformMakeTranslation(imageSize.width, imageSize.height);
    transform = CGAffineTransformRotate(transform, M_PI);
    break;

  case UIImageOrientationDownMirrored: //EXIF = 4
    transform = CGAffineTransformMakeTranslation(0.0, imageSize.height);
    transform = CGAffineTransformScale(transform, 1.0, -1.0);
    break;

  case UIImageOrientationLeftMirrored: //EXIF = 5
    boundHeight = bounds.size.height;
    bounds.size.height = bounds.size.width;
    bounds.size.width = boundHeight;
    transform = CGAffineTransformMakeTranslation(imageSize.height, imageSize.width);
    transform = CGAffineTransformScale(transform, -1.0, 1.0);
    transform = CGAffineTransformRotate(transform, 3.0 * M_PI / 2.0);
    break;

  case UIImageOrientationLeft: //EXIF = 6
    boundHeight = bounds.size.height;
    bounds.size.height = bounds.size.width;
    bounds.size.width = boundHeight;
    transform = CGAffineTransformMakeTranslation(0.0, imageSize.width);
    transform = CGAffineTransformRotate(transform, 3.0 * M_PI / 2.0);
    break;

  case UIImageOrientationRightMirrored: //EXIF = 7
    boundHeight = bounds.size.height;
    bounds.size.height = bounds.size.width;
    bounds.size.width = boundHeight;
    transform = CGAffineTransformMakeScale(-1.0, 1.0);
    transform = CGAffineTransformRotate(transform, M_PI / 2.0);
    break;

  case UIImageOrientationRight: //EXIF = 8
    boundHeight = bounds.size.height;
    bounds.size.height = bounds.size.width;
    bounds.size.width = boundHeight;
    transform = CGAffineTransformMakeTranslation(imageSize.height, 0.0);
    transform = CGAffineTransformRotate(transform, M_PI / 2.0);
    break;

  default:
    [NSException raise:NSInternalInconsistencyException format:@"Invalid image orientation"];
  }

  UIGraphicsBeginImageContext(bounds.size);

  CGContextRef context = UIGraphicsGetCurrentContext();

  if (orient == UIImageOrientationRight || orient == UIImageOrientationLeft) {
    CGContextScaleCTM(context, -scaleRatio, scaleRatio);
    CGContextTranslateCTM(context, -height, 0);
  } else {
    CGContextScaleCTM(context, scaleRatio, -scaleRatio);
    CGContextTranslateCTM(context, 0, -height);
  }

  CGContextConcatCTM(context, transform);

  CGContextDrawImage(UIGraphicsGetCurrentContext(), CGRectMake(0, 0, width, height), imgRef);
  UIImage *imageCopy = UIGraphicsGetImageFromCurrentImageContext();
  UIGraphicsEndImageContext();

  return imageCopy;
}

+ (UIImage *)imageWithTint:(UIImage *)image tintColor:(UIColor *)tintColor
{
  if ([TiUtils isIOSVersionOrGreater:@"13.0"]) {
    return [image imageWithTintColor:tintColor renderingMode:UIImageRenderingModeAlwaysOriginal];
  }
  UIImage *imageNew = [image imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate];
  UIImageView *imageView = [[UIImageView alloc] initWithImage:imageNew];
  imageView.tintColor = tintColor;

  UIGraphicsBeginImageContextWithOptions(imageView.bounds.size, NO, 0.0);
  [imageView.layer renderInContext:UIGraphicsGetCurrentContext()];
  UIImage *tintedImage = UIGraphicsGetImageFromCurrentImageContext();
  UIGraphicsEndImageContext();

  return tintedImage;
}

+ (NSURL *)checkFor2XImage:(NSURL *)url
{
  NSString *path = nil;

  if ([url isFileURL]) {
    path = [url path];
  }

  if ([[url scheme] isEqualToString:@"app"]) { //Technically, this will have an extra /, but iOS ignores this.
    path = [url resourceSpecifier];
  }

  NSString *ext = [path pathExtension];

  if (![ext isEqualToString:@"png"] && ![ext isEqualToString:@"jpg"] && ![ext isEqualToString:@"jpeg"]) { //It's not an image.
    return url;
  }

  NSFileManager *fm = [NSFileManager defaultManager];
  NSString *partial = [path stringByDeletingPathExtension];

  NSString *os = [TiUtils isIPad] ? @"~ipad" : @"~iphone";

  if ([TiUtils is3xRetina]) {
    if ([TiUtils isSuperRetina6_5Inch]) {
      // -2688@3x iPhone XS Max specific
      NSString *testpath = [NSString stringWithFormat:@"%@-2688h@3x.%@", partial, ext];
      if ([fm fileExistsAtPath:testpath]) {
        return [NSURL fileURLWithPath:testpath];
      }
    } else if ([TiUtils isSuperRetina5_8Inch]) {
      // -2436h@3x iPhone X specific
      NSString *testpath = [NSString stringWithFormat:@"%@-2436h@3x.%@", partial, ext];
      if ([fm fileExistsAtPath:testpath]) {
        return [NSURL fileURLWithPath:testpath];
      }
    } else if ([TiUtils isRetina4_7Inch]) {
      // -736h@3x iPhone 6/7 Plus specific
      NSString *testpath = [NSString stringWithFormat:@"%@-736h@3x.%@", partial, ext];
      if ([fm fileExistsAtPath:testpath]) {
        return [NSURL fileURLWithPath:testpath];
      }
    }

    // Plain @3x
    NSString *testpath = [NSString stringWithFormat:@"%@@3x.%@", partial, ext];
    if ([fm fileExistsAtPath:testpath]) {
      return [NSURL fileURLWithPath:testpath];
    }
  }
  if ([TiUtils is2xRetina]) {
    if ([TiUtils isRetina6_1Inch]) {
      // -1792h@2x iPhone XR specific
      NSString *testpath = [NSString stringWithFormat:@"%@-1792h@2x.%@", partial, ext];
      if ([fm fileExistsAtPath:testpath]) {
        return [NSURL fileURLWithPath:testpath];
      }
    } else if ([TiUtils isRetina4_7Inch]) {
      // -667h@2x iPhone 6/7 specific
      NSString *testpath = [NSString stringWithFormat:@"%@-667h@2x.%@", partial, ext];
      if ([fm fileExistsAtPath:testpath]) {
        return [NSURL fileURLWithPath:testpath];
      }
    } else if ([TiUtils isRetinaFourInch]) {
      // -568h@2x iPhone 5 specific
      NSString *testpath = [NSString stringWithFormat:@"%@-568h@2x.%@", partial, ext];
      if ([fm fileExistsAtPath:testpath]) {
        return [NSURL fileURLWithPath:testpath];
      }
    }
    // @2x device specific
    NSString *testpath = [NSString stringWithFormat:@"%@@2x%@.%@", partial, os, ext];
    if ([fm fileExistsAtPath:testpath]) {
      return [NSURL fileURLWithPath:testpath];
    }
    // Plain @2x
    testpath = [NSString stringWithFormat:@"%@@2x.%@", partial, ext];
    if ([fm fileExistsAtPath:testpath]) {
      return [NSURL fileURLWithPath:testpath];
    }
  }

  // Fallback: Just device specific normal res
  NSString *testpath = [NSString stringWithFormat:@"%@%@.%@", partial, os, ext];
  if ([fm fileExistsAtPath:testpath]) {
    return [NSURL fileURLWithPath:testpath];
  }

  return url;
}

+ (NSURL *)toURL:(NSString *)relativeString relativeToURL:(NSURL *)rootPath
{
  /*
Okay, behavior: Bad values are either converted or ejected.
sms:, tel:, mailto: are all done

If the new path is HTTP:// etc, then punt and massage the code.

If the new path starts with / and the base url is app://..., we have to massage the url.


*/
  if ((relativeString == nil) || ((void *)relativeString == (void *)[NSNull null])) {
    return nil;
  }

  if (![relativeString isKindOfClass:[NSString class]]) {
    relativeString = [TiUtils stringValue:relativeString];
  }

  if ([relativeString hasPrefix:@"sms:"] ||
      [relativeString hasPrefix:@"tel:"] ||
      [relativeString hasPrefix:@"mailto:"]) {
    return [NSURL URLWithString:relativeString];
  }

  NSURL *result = [NSURL URLWithString:relativeString relativeToURL:rootPath];
  // If the path looks absolute (but isn't valid), try to make it relative to resource path
  if ([relativeString hasPrefix:@"/"]) {
    if (result == nil) {
      // assume it's a file path with some funkiness in it that caused URL parsing to fail. Try standardizing the path
      result = [NSURL fileURLWithPath:[relativeString stringByStandardizingPath]];
    }
    NSString *rootScheme = [rootPath scheme];
    NSString *resourcePath = [TiHost resourcePath];
    BOOL usesApp = [rootScheme isEqualToString:@"app"];
    if (!usesApp && [rootScheme isEqualToString:@"file"]) {
      usesApp = [[rootPath path] hasPrefix:resourcePath];
    }
    if (usesApp) {
      if (result && [result isFileURL] && [result checkResourceIsReachableAndReturnError:nil]) {
        // good URL, no need to treat it like it's relative to resources dir
        result = [result filePathURL];
      } else {
        // bad URL, assume it's relative to app's resources dir
        result = [NSURL fileURLWithPath:[resourcePath stringByAppendingPathComponent:relativeString]];
      }
    }
  }
  // Fall back if somehow the URL is bad
  if (result == nil) {
    // encoding problem - fail fast and make sure we re-escape
    NSRange range = [relativeString rangeOfString:@"?"];
    if (range.location != NSNotFound) {
      NSString *qs = [TiUtils encodeURIParameters:[relativeString substringFromIndex:range.location + 1]];
      NSString *newurl = [NSString stringWithFormat:@"%@?%@", [relativeString substringToIndex:range.location], qs];
      result = [NSURL URLWithString:newurl];
    }
  }

  // If we have a URL and it's a 'file:' one, check for 2x images
  // TIMOB-18262
  if (result && ([result isFileURL])) {
    BOOL isDir = NO;
    BOOL exists = [[NSFileManager defaultManager] fileExistsAtPath:[result path] isDirectory:&isDir];

    if (exists && !isDir) {
      return [TiUtils checkFor2XImage:result];
    }
  }

  return result;
}

+ (NSURL *)toURL:(NSString *)object proxy:(TiProxy *)proxy
{
  return [self toURL:object relativeToURL:[proxy _baseURL]];
}

+ (UIImage *)stretchableImage:(id)object proxy:(TiProxy *)proxy
{
  return [[ImageLoader sharedLoader] loadImmediateStretchableImage:[self toURL:object proxy:proxy]];
}

+ (UIImage *)image:(id)object proxy:(TiProxy *)proxy
{
  if ([object isKindOfClass:[TiBlob class]]) {
    return [(TiBlob *)object image];
  } else if ([object isKindOfClass:[NSString class]]) {
    return [[ImageLoader sharedLoader] loadImmediateImage:[self toURL:object proxy:proxy]];
  }

  return nil;
}

+ (int)intValue:(NSString *)name properties:(NSDictionary *)properties def:(int)def exists:(BOOL *)exists
{
  if ([properties isKindOfClass:[NSDictionary class]]) {
    id value = [properties objectForKey:name];
    if ([value respondsToSelector:@selector(intValue)]) {
      if (exists != NULL)
        *exists = YES;
      return [value intValue];
    }
  }
  if (exists != NULL)
    *exists = NO;
  return def;
}

+ (double)doubleValue:(NSString *)name properties:(NSDictionary *)properties def:(double)def exists:(BOOL *)exists
{
  if ([properties isKindOfClass:[NSDictionary class]]) {
    id value = [properties objectForKey:name];
    if ([value respondsToSelector:@selector(doubleValue)]) {
      if (exists != NULL)
        *exists = YES;
      return [value doubleValue];
    }
  }
  if (exists != NULL)
    *exists = NO;
  return def;
}

+ (float)floatValue:(NSString *)name properties:(NSDictionary *)properties def:(float)def exists:(BOOL *)exists
{
  if ([properties isKindOfClass:[NSDictionary class]]) {
    id value = [properties objectForKey:name];
    if ([value respondsToSelector:@selector(floatValue)]) {
      if (exists != NULL)
        *exists = YES;
      return [value floatValue];
    }
  }
  if (exists != NULL)
    *exists = NO;
  return def;
}

+ (BOOL)boolValue:(NSString *)name properties:(NSDictionary *)properties def:(BOOL)def exists:(BOOL *)exists
{
  if ([properties isKindOfClass:[NSDictionary class]]) {
    id value = [properties objectForKey:name];
    if ([value respondsToSelector:@selector(boolValue)]) {
      if (exists != NULL)
        *exists = YES;
      return [value boolValue];
    }
  }
  if (exists != NULL)
    *exists = NO;
  return def;
}

+ (NSString *)stringValue:(NSString *)name properties:(NSDictionary *)properties def:(NSString *)def exists:(BOOL *)exists
{
  if ([properties isKindOfClass:[NSDictionary class]]) {
    id value = [properties objectForKey:name];
    if ([value isKindOfClass:[NSString class]]) {
      if (exists != NULL)
        *exists = YES;
      return value;
    } else if (value == [NSNull null]) {
      if (exists != NULL)
        *exists = YES;
      return nil;
    } else if ([value respondsToSelector:@selector(stringValue)]) {
      if (exists != NULL)
        *exists = YES;
      return [value stringValue];
    }
  }
  if (exists != NULL)
    *exists = NO;
  return def;
}

+ (CGPoint)pointValue:(NSString *)name properties:(NSDictionary *)properties def:(CGPoint)def exists:(BOOL *)exists
{
  if ([properties isKindOfClass:[NSDictionary class]]) {
    id value = [properties objectForKey:name];
    if ([value isKindOfClass:[NSDictionary class]]) {
      NSDictionary *dict = (NSDictionary *)value;
      CGPoint point;
      point.x = [self doubleValue:@"x" properties:dict def:def.x];
      point.y = [self doubleValue:@"y" properties:dict def:def.y];
      if (exists != NULL)
        *exists = YES;
      return point;
    }
  }

  if (exists != NULL)
    *exists = NO;
  return def;
}

+ (TiColor *)colorValue:(NSString *)name properties:(NSDictionary *)properties def:(TiColor *)def exists:(BOOL *)exists
{
  TiColor *result = nil;
  if ([properties isKindOfClass:[NSDictionary class]]) {
    id value = [properties objectForKey:name];
    if (value == [NSNull null]) {
      if (exists != NULL)
        *exists = YES;
      return nil;
    }
    if ([value respondsToSelector:@selector(stringValue)]) {
      value = [value stringValue];
    }
    if ([value isKindOfClass:[NSString class]]) {
      // need to retain here since we autorelease below and since colorName also autoreleases
      result = [[TiColor colorNamed:value] retain];
    }
  }
  if (result != nil) {
    if (exists != NULL)
      *exists = YES;
    return [result autorelease];
  }

  if (exists != NULL)
    *exists = NO;
  return def;
}

+ (TiDimension)dimensionValue:(NSString *)name properties:(NSDictionary *)properties def:(TiDimension)def exists:(BOOL *)exists
{
  if ([properties isKindOfClass:[NSDictionary class]]) {
    id value = [properties objectForKey:name];
    if (value != nil) {
      if (exists != NULL) {
        *exists = YES;
      }
      return [self dimensionValue:value];
    }
  }
  if (exists != NULL) {
    *exists = NO;
  }
  return def;
}

+ (int)intValue:(NSString *)name properties:(NSDictionary *)props def:(int)def;
{
  return [self intValue:name properties:props def:def exists:NULL];
}

+ (double)doubleValue:(NSString *)name properties:(NSDictionary *)props def:(double)def;
{
  return [self doubleValue:name properties:props def:def exists:NULL];
}

+ (float)floatValue:(NSString *)name properties:(NSDictionary *)props def:(float)def;
{
  return [self floatValue:name properties:props def:def exists:NULL];
}

+ (BOOL)boolValue:(NSString *)name properties:(NSDictionary *)props def:(BOOL)def;
{
  return [self boolValue:name properties:props def:def exists:NULL];
}

+ (NSString *)stringValue:(NSString *)name properties:(NSDictionary *)properties def:(NSString *)def;
{
  return [self stringValue:name properties:properties def:def exists:NULL];
}

+ (CGPoint)pointValue:(NSString *)name properties:(NSDictionary *)properties def:(CGPoint)def;
{
  return [self pointValue:name properties:properties def:def exists:NULL];
}

+ (TiColor *)colorValue:(NSString *)name properties:(NSDictionary *)properties def:(TiColor *)def;
{
  return [self colorValue:name properties:properties def:def exists:NULL];
}

+ (TiDimension)dimensionValue:(NSString *)name properties:(NSDictionary *)properties def:(TiDimension)def
{
  return [self dimensionValue:name properties:properties def:def exists:NULL];
}

+ (int)intValue:(NSString *)name properties:(NSDictionary *)props;
{
  return [self intValue:name properties:props def:0 exists:NULL];
}

+ (double)doubleValue:(NSString *)name properties:(NSDictionary *)props;
{
  return [self doubleValue:name properties:props def:0.0 exists:NULL];
}

+ (float)floatValue:(NSString *)name properties:(NSDictionary *)props;
{
  return [self floatValue:name properties:props def:0.0 exists:NULL];
}

+ (BOOL)boolValue:(NSString *)name properties:(NSDictionary *)props;
{
  return [self boolValue:name properties:props def:NO exists:NULL];
}

+ (NSString *)stringValue:(NSString *)name properties:(NSDictionary *)properties;
{
  return [self stringValue:name properties:properties def:nil exists:NULL];
}

+ (CGPoint)pointValue:(NSString *)name properties:(NSDictionary *)properties;
{
  return [self pointValue:name properties:properties def:CGPointZero exists:NULL];
}

+ (TiColor *)colorValue:(NSString *)name properties:(NSDictionary *)properties;
{
  return [self colorValue:name properties:properties def:nil exists:NULL];
}

+ (TiDimension)dimensionValue:(NSString *)name properties:(NSDictionary *)properties
{
  return [self dimensionValue:name properties:properties def:TiDimensionUndefined exists:NULL];
}

+ (NSDictionary *)pointToDictionary:(CGPoint)point
{
  return [NSDictionary dictionaryWithObjectsAndKeys:
                           [NSNumber numberWithDouble:point.x], @"x",
                       [NSNumber numberWithDouble:point.y], @"y",
                       nil];
}

+ (NSDictionary *)rectToDictionary:(CGRect)rect
{
  return [NSDictionary dictionaryWithObjectsAndKeys:
                           [NSNumber numberWithDouble:rect.origin.x], @"x",
                       [NSNumber numberWithDouble:rect.origin.y], @"y",
                       [NSNumber numberWithDouble:rect.size.width], @"width",
                       [NSNumber numberWithDouble:rect.size.height], @"height",
                       nil];
}

+ (NSDictionary *)sizeToDictionary:(CGSize)size
{
  return [NSDictionary dictionaryWithObjectsAndKeys:
                           [NSNumber numberWithDouble:size.width], @"width",
                       [NSNumber numberWithDouble:size.height], @"height",
                       nil];
}

+ (NSDictionary *)touchPropertiesToDictionary:(UITouch *)touch andView:(UIView *)view
{
  CGPoint point = [touch locationInView:view];
  point.x = convertDipToDefaultUnit(point.x);
  point.y = convertDipToDefaultUnit(point.y);

  if ([self forceTouchSupported] || [self validatePencilWithTouch:touch]) {
    NSMutableDictionary *dict = [NSMutableDictionary dictionaryWithObjectsAndKeys:
                                                         [NSNumber numberWithFloat:point.x], @"x",
                                                     [NSNumber numberWithFloat:point.y], @"y",
                                                     [NSNumber numberWithFloat:touch.force], @"force",
                                                     [NSNumber numberWithFloat:touch.maximumPossibleForce], @"maximumPossibleForce",
                                                     [NSNumber numberWithDouble:touch.timestamp], @"timestamp",
                                                     nil];

    if ([self isIOSVersionOrGreater:@"9.1"]) {
      [dict setValue:[NSNumber numberWithFloat:touch.altitudeAngle] forKey:@"altitudeAngle"];
    }

    if ([self validatePencilWithTouch:touch]) {
      [dict setValue:[NSNumber numberWithFloat:[touch azimuthUnitVectorInView:view].dx] forKey:@"azimuthUnitVectorInViewX"];
      [dict setValue:[NSNumber numberWithFloat:[touch azimuthUnitVectorInView:view].dy] forKey:@"azimuthUnitVectorInViewY"];
    }

    return dict;
  }

  return [self pointToDictionary:point];
}

+ (CGRect)contentFrame:(BOOL)window
{
  double height = 0;
  if (window && !UIApplication.sharedApplication.isStatusBarHidden) {
    CGRect statusFrame = UIApplication.sharedApplication.statusBarFrame;
    height = statusFrame.size.height;
  }

  CGRect f = UIApplication.sharedApplication.keyWindow.frame;
  return CGRectMake(f.origin.x, height, f.size.width, f.size.height);
}

+ (CGFloat)sizeValue:(id)value
{
  if ([value isKindOfClass:[NSString class]]) {
    NSString *s = [(NSString *)value stringByReplacingOccurrencesOfString:@"px" withString:@""];
    return [[s stringByReplacingOccurrencesOfString:@" " withString:@""] floatValue];
  }
  return [value floatValue];
}

+ (WebFont *)fontValue:(id)value def:(WebFont *)def
{
  if ([value isKindOfClass:[NSDictionary class]]) {
    WebFont *font = [[WebFont alloc] init];
    [font updateWithDict:value inherits:nil];
    return [font autorelease];
  }
  if ([value isKindOfClass:[NSString class]]) {
    WebFont *font = [[WebFont alloc] init];
    font.family = value;
    font.size = 17;
    return [font autorelease];
  }
  return def;
}

+ (WebFont *)fontValue:(id)value
{
  return [self fontValue:value def:[WebFont defaultFont]];
}

+ (TiScriptError *)scriptErrorFromValueRef:(JSValueRef)valueRef inContext:(JSGlobalContextRef)contextRef
{
  JSContext *context = [JSContext contextWithJSGlobalContextRef:contextRef];
  JSValue *error = [JSValue valueWithJSValueRef:valueRef inContext:context];
  NSMutableDictionary *errorDict = [NSMutableDictionary new];

  if ([error hasProperty:@"constructor"]) {
    [errorDict setObject:[error[@"constructor"][@"name"] toString] forKey:@"type"];
  }

  // error message
  if ([error hasProperty:@"message"]) {
    [errorDict setObject:[error[@"message"] toString] forKey:@"message"];
  }
  if ([error hasProperty:@"nativeReason"]) {
    [errorDict setObject:[error[@"nativeReason"] toString] forKey:@"nativeReason"];
  }

  // error location
  if ([error hasProperty:@"sourceURL"]) {
    [errorDict setObject:[error[@"sourceURL"] toString] forKey:@"sourceURL"];
  }
  if ([error hasProperty:@"line"]) {
    [errorDict setObject:[error[@"line"] toNumber] forKey:@"line"];
  }
  if ([error hasProperty:@"column"]) {
    [errorDict setObject:[error[@"column"] toNumber] forKey:@"column"];
  }

  // stack trace
  if ([error hasProperty:@"backtrace"]) {
    [errorDict setObject:[error[@"backtrace"] toString] forKey:@"backtrace"];
  }
  if ([error hasProperty:@"stack"]) {
    [errorDict setObject:[error[@"stack"] toString] forKey:@"stack"];
  }
  if ([error hasProperty:@"nativeStack"] && error[@"nativeStack"].isString) {
    NSString *callStack = error[@"nativeStack"].toString;
    NSArray<NSString *> *nativeStack = [callStack componentsSeparatedByCharactersInSet:NSCharacterSet.newlineCharacterSet];
    [errorDict setObject:nativeStack forKey:@"nativeStack"];
  }

  return [[[TiScriptError alloc] initWithDictionary:errorDict] autorelease];
}

+ (TiScriptError *)scriptErrorValue:(id)value;
{
  if ((value == nil) || (value == [NSNull null])) {
    return nil;
  }
  if ([value isKindOfClass:[TiScriptError class]]) {
    return value;
  }
  if ([value isKindOfClass:[NSDictionary class]]) {
    return [[[TiScriptError alloc] initWithDictionary:value] autorelease];
  }
  return [[[TiScriptError alloc] initWithMessage:[value description] sourceURL:nil lineNo:0] autorelease];
}

+ (NSTextAlignment)textAlignmentValue:(id)alignment
{
  NSTextAlignment align = NSTextAlignmentNatural;

  if ([alignment isKindOfClass:[NSString class]]) {
    if ([alignment isEqualToString:@"left"]) {
      align = NSTextAlignmentLeft;
    } else if ([alignment isEqualToString:@"center"]) {
      align = NSTextAlignmentCenter;
    } else if ([alignment isEqualToString:@"right"]) {
      align = NSTextAlignmentRight;
    } else if ([alignment isEqualToString:@"justify"]) {
      align = NSTextAlignmentJustified;
    }
  } else if ([alignment isKindOfClass:[NSNumber class]]) {
    align = [alignment intValue];
  }
  return align;
}

#define RETURN_IF_ORIENTATION_STRING(str, orientation) \
  if ([str isEqualToString:@ #orientation])            \
    return (UIDeviceOrientation)orientation;

+ (UIDeviceOrientation)orientationValue:(id)value def:(UIDeviceOrientation)def
{
  if ([value isKindOfClass:[NSString class]]) {
    if ([value isEqualToString:@"portrait"]) {
      return UIDeviceOrientationPortrait;
    }
    if ([value isEqualToString:@"landscape"]) {
      return (UIDeviceOrientation)UIInterfaceOrientationLandscapeRight;
    }

    RETURN_IF_ORIENTATION_STRING(value, UIInterfaceOrientationPortrait)
    RETURN_IF_ORIENTATION_STRING(value, UIInterfaceOrientationPortraitUpsideDown)
    RETURN_IF_ORIENTATION_STRING(value, UIInterfaceOrientationLandscapeLeft)
    RETURN_IF_ORIENTATION_STRING(value, UIInterfaceOrientationLandscapeRight)
  }

  if ([value respondsToSelector:@selector(intValue)]) {
    return [value intValue];
  }
  return def;
}

+ (BOOL)isOrientationPortait
{
  return UIInterfaceOrientationIsPortrait([self orientation]);
}

+ (BOOL)isOrientationLandscape
{
  return UIInterfaceOrientationIsLandscape([self orientation]);
}

+ (UIInterfaceOrientation)orientation
{
  UIDeviceOrientation orient = UIDevice.currentDevice.orientation;
  //	TODO: A previous bug was DeviceOrientationUnknown == 0, which is always true. Uncomment this when pushing.
  if (UIDeviceOrientationUnknown == orient) {
    return (UIInterfaceOrientation)UIDeviceOrientationPortrait;
  } else {
    return (UIInterfaceOrientation)orient;
  }
}

+ (CGRect)screenRect
{
  return UIScreen.mainScreen.bounds;
}

//TODO: rework these to be more accurate and multi-device

+ (CGRect)navBarRect
{
  CGRect rect = [self screenRect];
  rect.size.height = TI_NAVBAR_HEIGHT;
  return rect;
}

+ (CGSize)navBarTitleViewSize
{
  CGRect rect = [self screenRect];
  return CGSizeMake(rect.size.width - TI_NAVBAR_BUTTON_WIDTH, TI_NAVBAR_HEIGHT);
}

+ (CGRect)navBarTitleViewRect
{
  CGRect rect = [self screenRect];
  rect.size.height = TI_NAVBAR_HEIGHT;
  rect.size.width -= TI_NAVBAR_BUTTON_WIDTH; // offset for padding on both sides
  return rect;
}

+ (CGPoint)centerSize:(CGSize)smallerSize inRect:(CGRect)largerRect
{
  return CGPointMake(
      largerRect.origin.x + (largerRect.size.width - smallerSize.width) / 2,
      largerRect.origin.y + (largerRect.size.height - smallerSize.height) / 2);
}

+ (CGRect)centerRect:(CGRect)smallerRect inRect:(CGRect)largerRect
{
  smallerRect.origin = [self centerSize:smallerRect.size inRect:largerRect];

  return smallerRect;
}

#define USEFRAME 0

+ (void)setView:(UIView *)view positionRect:(CGRect)frameRect
{
#if USEFRAME
  [view setFrame:frameRect];
  return;
#endif

  CGPoint anchorPoint = view.layer.anchorPoint;
  CGPoint newCenter;
  newCenter.x = frameRect.origin.x + (anchorPoint.x * frameRect.size.width);
  newCenter.y = frameRect.origin.y + (anchorPoint.y * frameRect.size.height);
  CGRect newBounds = CGRectMake(0, 0, frameRect.size.width, frameRect.size.height);

  [view setBounds:newBounds];
  [view setCenter:newCenter];
}

+ (void)applyConstraintToView:(TiUIView *)view forProxy:(TiViewProxy *)proxy withBounds:(CGRect)bounds
{
  ApplyConstraintToViewWithBounds([proxy layoutProperties], view, bounds);
}

+ (NSString *)composeAccessibilityIdentifier:(id)object
{
  NSString *accessibilityLabel = [object accessibilityLabel];
  NSString *accessibilityValue = [object accessibilityValue];
  NSString *accessibilityHint = [object accessibilityHint];

  NSString *pattern = @"^.*[!\"#$%&'()*+,\\-./:;<=>?@\\[\\]^_`{|}~]\\s*$";
  NSString *dot = @".";
  NSString *space = @" ";
  NSError *error = nil;
  NSRegularExpression *regex = [NSRegularExpression regularExpressionWithPattern:pattern options:0 error:&error];
  NSMutableArray *array = [NSMutableArray array];
  NSUInteger numberOfMatches;

  if (accessibilityLabel != nil && accessibilityLabel.length) {
    [array addObject:accessibilityLabel];
    numberOfMatches = [regex numberOfMatchesInString:accessibilityLabel options:0 range:NSMakeRange(0, [accessibilityLabel length])];
    if (numberOfMatches == 0) {
      [array addObject:dot];
    }
  }

  if (accessibilityValue != nil && accessibilityValue.length) {
    if ([array count] > 0) {
      [array addObject:space];
    }
    [array addObject:accessibilityValue];
    numberOfMatches = [regex numberOfMatchesInString:accessibilityValue options:0 range:NSMakeRange(0, [accessibilityValue length])];
    if (numberOfMatches == 0) {
      [array addObject:dot];
    }
  }

  if (accessibilityHint != nil && accessibilityHint.length) {
    if ([array count] > 0) {
      [array addObject:space];
    }
    [array addObject:accessibilityHint];
    numberOfMatches = [regex numberOfMatchesInString:accessibilityHint options:0 range:NSMakeRange(0, [accessibilityHint length])];
    if (numberOfMatches == 0) {
      [array addObject:dot];
    }
  }
  return [array componentsJoinedByString:@""];
}

+ (CGRect)viewPositionRect:(UIView *)view
{
#if USEFRAME
  return [view frame];
#endif

  if (view == nil) {
    return CGRectZero;
  }

  CGPoint anchorPoint = view.layer.anchorPoint;
  CGRect bounds = view.bounds;
  CGPoint center = view.center;

  return CGRectMake(center.x - (anchorPoint.x * bounds.size.width),
      center.y - (anchorPoint.y * bounds.size.height),
      bounds.size.width, bounds.size.height);
}

+ (NSData *)loadAppResource:(NSURL *)url
{
  BOOL app = [[url scheme] hasPrefix:@"app"];
  BOOL isFileURL = [url isFileURL];

  if (isFileURL || app) {
    BOOL leadingSlashRemoved = NO;
    NSString *urlstring = [[url standardizedURL] path];
    NSString *appurlstr = urlstring;
    if (isFileURL) {
      // strip resources dir filepath prefix (if it's there)
      NSString *resourceurl = [[NSBundle mainBundle] resourcePath];
      NSString *standardized = [resourceurl stringByStandardizingPath];
      if ([urlstring hasPrefix:resourceurl]) {
        appurlstr = [urlstring stringByReplacingOccurrencesOfString:resourceurl withString:@""];
      } else if (![resourceurl isEqualToString:standardized] && [urlstring hasPrefix:standardized]) {
        appurlstr = [urlstring stringByReplacingOccurrencesOfString:standardized withString:@""];
      }
    }
    if ([appurlstr hasPrefix:@"/"]) {
#ifndef __clang_analyzer__
      leadingSlashRemoved = YES;
#endif
      appurlstr = [appurlstr substringFromIndex:1];
    }
#if TARGET_OS_SIMULATOR
    NSString *resourcesDirectory = [[TiSharedConfig defaultConfig] applicationResourcesDirectory];

    if (app == YES && leadingSlashRemoved) {
      // on simulator we want to keep slash since it's coming from file
      appurlstr = [@"/" stringByAppendingString:appurlstr];
    }

    if (resourcesDirectory != nil && [resourcesDirectory isEqualToString:@""] == NO) {
      if ([appurlstr hasPrefix:resourcesDirectory]) {
        if ([[NSFileManager defaultManager] fileExistsAtPath:appurlstr]) {
          return [NSData dataWithContentsOfFile:appurlstr];
        }
      }
      // this path is only taken during a simulator build
      // in this path, we will attempt to load resources directly from the
      // app's Resources directory to speed up round-trips
      NSString *filepath = [resourcesDirectory stringByAppendingPathComponent:appurlstr];
      if ([[NSFileManager defaultManager] fileExistsAtPath:filepath]) {
        return [NSData dataWithContentsOfFile:filepath];
      }
    }
#endif
    static id AppRouter;
    if (AppRouter == nil) {
      AppRouter = NSClassFromString(@"ApplicationRouting");
    }
    if (AppRouter != nil) {
      appurlstr = [appurlstr stringByReplacingOccurrencesOfString:@"." withString:@"_"];
      if ([appurlstr characterAtIndex:0] == '/') {
        appurlstr = [appurlstr substringFromIndex:1];
      }
#if DEBUG_RESOURCE_PATHS
      DebugLog(@"[DEBUG] Loading: %@, Resource: %@", urlstring, appurlstr);
#endif
      return [AppRouter performSelector:@selector(resolveAppAsset:) withObject:appurlstr];
    }
  }
  return nil;
}

+ (BOOL)barTranslucencyForColor:(TiColor *)color
{
  return [color _color] == UIColor.clearColor;
}

+ (UIColor *)barColorForColor:(TiColor *)color
{
  UIColor *result = [color _color];
  // TODO: Return nil for the appropriate colors once Apple fixes how the 'cancel' button
  // is displayed on nil-color bars.
  if (result == UIColor.clearColor) {
    return nil;
  }
  return result;
}

+ (UIBarStyle)barStyleForColor:(TiColor *)color
{
  UIColor *result = [color _color];
  // TODO: Return UIBarStyleBlack for the appropriate colors once Apple fixes how the 'cancel' button
  // is displayed on nil-color bars.
  if (result == UIColor.clearColor) {
    return UIBarStyleBlack;
  }
  return UIBarStyleDefault;
}

+ (NSUInteger)extendedEdgesFromProp:(id)prop
{
  if (![prop isKindOfClass:[NSArray class]]) {
    return 0; // TODO: Change the default value in SDK 8+ to match native iOS behavior
  }

  NSUInteger result = 0;
  for (id mode in prop) {
    int value = [TiUtils intValue:mode def:0];
    switch (value) {
    case 0:
    case 1:
    case 2:
    case 4:
    case 8:
    case 15:
      result = result | value;
      break;
    default:
      DebugLog(@"Invalid value passed for extendEdges %d", value);
      break;
    }
  }
  return result;
}

+ (void)setVolume:(float)volume onObject:(id)theObject
{
  //Must be called on the main thread
  if ([NSThread isMainThread]) {
    if ([theObject respondsToSelector:@selector(setVolume:)]) {
      [(id<VolumeSupport>)theObject setVolume:volume];
    } else {
      DebugLog(@"[WARN] The Object %@ does not respond to method -(void)setVolume:(float)volume", [theObject description]);
    }
  }
}

+ (float)volumeFromObject:(id)theObject default:(float)def
{
  //Must be called on the main thread
  float returnValue = def;
  if ([NSThread isMainThread]) {
    if ([theObject respondsToSelector:@selector(volume)]) {
      returnValue = [(id<VolumeSupport>)theObject volume];
    } else {
      DebugLog(@"[WARN] The Object %@ does not respond to method -(float)volume", [theObject description]);
    }
  }
  return returnValue;
}

+ (void)configureController:(UIViewController *)controller withObject:(id)object
{
  id edgesValue = nil;
  id includeOpaque = nil;
  id autoAdjust = nil;
  if ([object isKindOfClass:[TiProxy class]]) {
    edgesValue = [(TiProxy *)object valueForUndefinedKey:@"extendEdges"];
    includeOpaque = [(TiProxy *)object valueForUndefinedKey:@"includeOpaqueBars"];
    autoAdjust = [(TiProxy *)object valueForUndefinedKey:@"autoAdjustScrollViewInsets"];
  } else if ([object isKindOfClass:[NSDictionary class]]) {
    edgesValue = [(NSDictionary *)object objectForKey:@"extendEdges"];
    includeOpaque = [(NSDictionary *)object objectForKey:@"includeOpaqueBars"];
    autoAdjust = [(NSDictionary *)object objectForKey:@"autoAdjustScrollViewInsets"];
  }

  [controller setEdgesForExtendedLayout:[self extendedEdgesFromProp:edgesValue]];
  [controller setExtendedLayoutIncludesOpaqueBars:[self boolValue:includeOpaque def:NO]];
  [controller setAutomaticallyAdjustsScrollViewInsets:[self boolValue:autoAdjust def:NO]];
}

+ (CGRect)frameForController:(UIViewController *)theController
{
  CGRect mainScreen = UIScreen.mainScreen.bounds;
  CGRect rect = UIApplication.sharedApplication.keyWindow.frame;
  NSUInteger edges = [theController edgesForExtendedLayout];
  //Check if I cover status bar
  if (((edges & UIRectEdgeTop) != 0)) {
    return mainScreen;
  }
  return rect;
}

+ (void)applyColor:(TiColor *)color toNavigationController:(UINavigationController *)navController
{
  UIColor *barColor = [self barColorForColor:color];
  UIBarStyle barStyle = [self barStyleForColor:color];
  BOOL isTranslucent = [self barTranslucencyForColor:color];

  UINavigationBar *navBar = [navController navigationBar];
  [navBar setBarStyle:barStyle];
  [navBar setTranslucent:isTranslucent];
  [navBar setBarTintColor:barColor];

  //This should not be here but in setToolBar. But keeping in place. Clean in 3.2.0
  UIToolbar *toolBar = [navController toolbar];
  [toolBar setBarStyle:barStyle];
  [toolBar setTranslucent:isTranslucent];
  [toolBar setBarTintColor:barColor];
}

+ (NSString *)replaceString:(NSString *)string characters:(NSCharacterSet *)characterSet withString:(NSString *)replacementString
{
  if (string == nil) {
    return nil;
  }

  NSRange setRange = [string rangeOfCharacterFromSet:characterSet];

  if (setRange.location == NSNotFound) {
    return string;
  }

  return [[string componentsSeparatedByCharactersInSet:characterSet] componentsJoinedByString:replacementString];
}

+ (NSStringEncoding)charsetToEncoding:(NSString *)type
{
  if (encodingMap == nil) {
    encodingMap = [[NSDictionary alloc] initWithObjectsAndKeys:
                                            NUMUINT(NSASCIIStringEncoding), kTiASCIIEncoding,
                                        NUMUINT(NSISOLatin1StringEncoding), kTiISOLatin1Encoding,
                                        NUMUINT(NSUTF8StringEncoding), kTiUTF8Encoding,
                                        NUMUINT(NSUTF16StringEncoding), kTiUTF16Encoding,
                                        NUMUINT(NSUTF16BigEndianStringEncoding), kTiUTF16BEEncoding,
                                        NUMUINT(NSUTF16LittleEndianStringEncoding), kTiUTF16LEEncoding,
                                        nil];
  }
  return [[encodingMap valueForKey:type] unsignedIntegerValue];
}

+ (TiDataType)constantToType:(NSString *)type
{
  if (typeMap == nil) {
    typeMap = [[NSDictionary alloc] initWithObjectsAndKeys:
                                        NUMINT(TI_BYTE), kTiByteTypeName,
                                    NUMINT(TI_SHORT), kTiShortTypeName,
                                    NUMINT(TI_INT), kTiIntTypeName,
                                    NUMINT(TI_LONG), kTiLongTypeName,
                                    NUMINT(TI_FLOAT), kTiFloatTypeName,
                                    NUMINT(TI_DOUBLE), kTiDoubleTypeName,
                                    nil];
  }
  return [[typeMap valueForKey:type] intValue];
}

+ (int)dataSize:(TiDataType)type
{
  if (sizeMap == nil) {
    sizeMap = [[NSDictionary alloc] initWithObjectsAndKeys:
                                        NUMINT(sizeof(char)), NUMINT(TI_BYTE),
                                    NUMINT(sizeof(uint16_t)), NUMINT(TI_SHORT),
                                    NUMINT(sizeof(uint32_t)), NUMINT(TI_INT),
                                    NUMINT(sizeof(uint64_t)), NUMINT(TI_LONG),
                                    NUMINT(sizeof(Float32)), NUMINT(TI_FLOAT),
                                    NUMINT(sizeof(Float64)), NUMINT(TI_DOUBLE),
                                    nil];
  }
  return [[sizeMap objectForKey:NUMINT(type)] intValue];
}

+ (int)encodeString:(NSString *)string toBuffer:(TiBuffer *)dest charset:(NSString *)charset offset:(NSUInteger)destPosition sourceOffset:(NSUInteger)srcPosition length:(NSUInteger)srcLength
{
  // TODO: Define standardized behavior.. but for now:
  // 1. Throw exception if destPosition extends past [dest length]
  // 2. Throw exception if srcPosition > [string length]
  // 3. Use srcLength as a HINT (as in all other buffer ops)

  if (destPosition >= [[dest data] length]) {
    return BAD_DEST_OFFSET;
  }
  if (srcPosition >= [string length]) {
    return BAD_SRC_OFFSET;
  }

  NSStringEncoding encoding = [TiUtils charsetToEncoding:charset];

  if (encoding == 0) {
    return BAD_ENCODING;
  }

  NSUInteger length = MIN(srcLength, [string length] - srcPosition);
  NSData *encodedString = [[string substringWithRange:NSMakeRange(srcPosition, length)] dataUsingEncoding:encoding];
  NSUInteger encodeLength = MIN([encodedString length], [[dest data] length] - destPosition);

  void *bufferBytes = [[dest data] mutableBytes];
  const void *stringBytes = [encodedString bytes];

  memcpy(bufferBytes + destPosition, stringBytes, encodeLength);

  return (int)(destPosition + encodeLength);
}

+ (int)encodeNumber:(NSNumber *)data toBuffer:(TiBuffer *)dest offset:(int)position type:(NSString *)type endianness:(CFByteOrder)byteOrder
{
  switch (byteOrder) {
  case CFByteOrderBigEndian:
  case CFByteOrderLittleEndian:
    break;
  default:
    return BAD_ENDIAN;
  }

  if (position >= [[dest data] length]) {
    return BAD_DEST_OFFSET;
  }

  void *bytes = [[dest data] mutableBytes];
  TiDataType dataType = [TiUtils constantToType:type];
  int size = [TiUtils dataSize:dataType];

  if (size > MIN([[dest data] length], [[dest data] length] - position)) {
    return TOO_SMALL;
  }

  switch ([self constantToType:type]) {
  case TI_BYTE: {
    char byte = [data charValue];
    memcpy(bytes + position, &byte, size);
    break;
  }
  case TI_SHORT: {
    uint16_t val = [data shortValue];
    switch (byteOrder) {
    case CFByteOrderLittleEndian: {
      val = CFSwapInt16HostToLittle(val);
      break;
    }
    case CFByteOrderBigEndian: {
      val = CFSwapInt16HostToBig(val);
      break;
    }
    }
    memcpy(bytes + position, &val, size);
    break;
  }
  case TI_INT: {
    uint32_t val = [data intValue];
    switch (byteOrder) {
    case CFByteOrderLittleEndian: {
      val = CFSwapInt32HostToLittle(val);
      break;
    }
    case CFByteOrderBigEndian: {
      val = CFSwapInt32HostToBig(val);
      break;
    }
    }
    memcpy(bytes + position, &val, size);
    break;
  }
  case TI_LONG: {
    uint64_t val = [data longLongValue];
    switch (byteOrder) {
    case CFByteOrderLittleEndian: {
      val = CFSwapInt64HostToLittle(val);
      break;
    }
    case CFByteOrderBigEndian: {
      val = CFSwapInt64HostToBig(val);
      break;
    }
    }
    memcpy(bytes + position, &val, size);
    break;
  }
  case TI_FLOAT: {
    // To prevent type coercion, we use a union where we assign the floatVaue as a Float32, and then access the integer byte representation off of the CFSwappedFloat struct.
    union {
      Float32 f;
      CFSwappedFloat32 sf;
    } val;
    val.f = [data floatValue];
    switch (byteOrder) {
    case CFByteOrderLittleEndian: {
      val.sf.v = CFSwapInt32HostToLittle(val.sf.v);
      break;
    }
    case CFByteOrderBigEndian: {
      val.sf.v = CFSwapInt32HostToBig(val.sf.v);
      break;
    }
    }
    memcpy(bytes + position, &(val.sf.v), size);
    break;
  }
  case TI_DOUBLE: {
    // See above for why we do union encoding.
    union {
      Float64 f;
      CFSwappedFloat64 sf;
    } val;
    val.f = [data doubleValue];
    switch (byteOrder) {
    case CFByteOrderLittleEndian: {
      val.sf.v = CFSwapInt64HostToLittle(val.sf.v);
      break;
    }
    case CFByteOrderBigEndian: {
      val.sf.v = CFSwapInt64HostToBig(val.sf.v);
      break;
    }
    }
    memcpy(bytes + position, &(val.sf.v), size);
    break;
  }
  default:
    return BAD_TYPE;
  }

  return (position + size);
}

+ (NSString *)convertToHex:(unsigned char *)result length:(size_t)length
{
  NSMutableString *encoded = [[NSMutableString alloc] initWithCapacity:length];
  for (int i = 0; i < length; i++) {
    [encoded appendFormat:@"%02x", result[i]];
  }
  NSString *value = [encoded lowercaseString];
  [encoded release];
  return value;
}

+ (NSString *)convertToHexFromData:(NSData *)data
{
  NSUInteger dataLength = data.length;
  unsigned char *dataBytes = (unsigned char *)data.bytes;
  NSMutableString *encoded = [[NSMutableString alloc] initWithCapacity:dataLength * 2];
  for (int i = 0; i < dataLength; i++) {
    [encoded appendFormat:@"%02x", dataBytes[i]];
  }
  NSString *value = [encoded lowercaseString];
  [encoded release];
  return value;
}

+ (NSString *)md5:(NSData *)data
{
  unsigned char result[CC_MD5_DIGEST_LENGTH];
  CC_MD5([data bytes], (CC_LONG)[data length], result);
  return [self convertToHex:(unsigned char *)&result length:CC_MD5_DIGEST_LENGTH];
}

// In pre-iOS 5, it looks like response headers were case-mangled.
// (i.e. WWW-Authenticate became Www-Authenticate). So we have to take this
// mangling into mind; headers such as FooBar-XYZ may also have been mangled
// to be case-correct. We can't be certain.
//
// This means we need to follow the RFC2616 implied MUST that headers are case-insensitive.

+ (NSString *)getResponseHeader:(NSString *)header fromHeaders:(NSDictionary *)responseHeaders
{
  // Do a direct comparison first, and then iterate through the headers if we have to.
  // This makes things faster in almost all scenarios, and ALWAYS so under iOS 5 unless
  // the developer is also taking advantage of RFC2616's header spec.
  __block NSString *responseHeader = [responseHeaders valueForKey:header];
  if (responseHeader != nil) {
    return responseHeader;
  }

  [responseHeaders enumerateKeysAndObjectsUsingBlock:^(id key, id obj, BOOL *stop) {
    if ([key localizedCaseInsensitiveCompare:header] == NSOrderedSame) {
      *stop = YES;
      responseHeader = obj;
    }
  }];

  return responseHeader;
}

+ (UIImage *)loadCappedBackgroundImage:(id)image forProxy:(TiProxy *)proxy withLeftCap:(TiDimension)leftCap topCap:(TiDimension)topCap
{
  UIImage *resultImage = nil;
  if ([image isKindOfClass:[UIImage class]]) {
    resultImage = [UIImageResize resizedImageWithLeftCap:leftCap topCap:topCap image:image];
  } else if ([image isKindOfClass:[NSString class]]) {
    if ([image isEqualToString:@""]) {
      return nil;
    }
    NSURL *bgURL = [TiUtils toURL:image proxy:proxy];
    resultImage = [[ImageLoader sharedLoader] loadImmediateStretchableImage:bgURL withLeftCap:leftCap topCap:topCap];
    if (resultImage == nil) {
      UIImage *downloadedImgage = [[ImageLoader sharedLoader] loadRemote:bgURL];
      resultImage = [UIImageResize resizedImageWithLeftCap:leftCap topCap:topCap image:downloadedImgage];
    }
    if (resultImage == nil && [image isEqualToString:@"Default.png"]) {
      // special case where we're asking for Default.png and it's in Bundle not path
      resultImage = [UIImageResize resizedImageWithLeftCap:leftCap topCap:topCap image:[UIImage imageNamed:image]];
    }
    if ((resultImage != nil) && ([resultImage imageOrientation] != UIImageOrientationUp)) {
      resultImage = [UIImageResize resizedImage:[resultImage size]
                           interpolationQuality:kCGInterpolationNone
                                          image:resultImage
                                          hires:NO];
    }
  } else if ([image isKindOfClass:[TiBlob class]]) {
    resultImage = [UIImageResize resizedImageWithLeftCap:leftCap topCap:topCap image:[(TiBlob *)image image]];
  }
  return resultImage;
}

+ (UIImage *)loadBackgroundImage:(id)image forProxy:(TiProxy *)proxy
{
  UIImage *resultImage = nil;
  if ([image isKindOfClass:[UIImage class]]) {
    resultImage = image;
  } else if ([image isKindOfClass:[NSString class]]) {
    if ([image isEqualToString:@""]) {
      return nil;
    }

    NSURL *bgURL = [TiUtils toURL:image proxy:proxy];
    resultImage = [[ImageLoader sharedLoader] loadImmediateImage:bgURL];
    if (resultImage == nil) {
      resultImage = [[ImageLoader sharedLoader] loadRemote:bgURL];
    }
    if (resultImage == nil && [image isEqualToString:@"Default.png"]) {
      // special case where we're asking for Default.png and it's in Bundle not path
      resultImage = [UIImage imageNamed:image];
    }
    if ((resultImage != nil) && ([resultImage imageOrientation] != UIImageOrientationUp)) {
      resultImage = [UIImageResize resizedImage:[resultImage size]
                           interpolationQuality:kCGInterpolationNone
                                          image:resultImage
                                          hires:NO];
    }
  } else if ([image isKindOfClass:[TiBlob class]]) {
    resultImage = [(TiBlob *)image image];
  }
  return resultImage;
}

+ (NSString *)messageFromError:(NSError *)error
{
  if (error == nil) {
    return nil;
  }
  NSString *result = [error localizedDescription];
  NSString *userInfoMessage = [[error userInfo] objectForKey:@"message"];
  if (result == nil) {
    result = userInfoMessage;
  } else if (userInfoMessage != nil) {
    result = [result stringByAppendingFormat:@" %@", userInfoMessage];
  }
  return result;
}

+ (NSMutableDictionary *)dictionaryWithCode:(NSInteger)code message:(NSString *)message
{
  return [NSMutableDictionary dictionaryWithObjectsAndKeys:
                                  NUMBOOL(code == 0), @"success",
                              NUMLONG(code), @"code",
                              message, @"error", nil];
}

+ (NSString *)jsonStringify:(id)value error:(NSError **)error
{
  if (value == nil) {
    return nil;
  }

  // TIMOB-25785: Try to repair invalid JSON objects for backwards
  // compatibility. Eventually remove later once developers are sensitized
  if (![NSJSONSerialization isValidJSONObject:value]) {
    DebugLog(@"[WARN] Cannot serialize object, trying to repair ...");
    value = [TiUtils stripInvalidJSONPayload:value];
  }

  NSData *jsonData = [NSJSONSerialization dataWithJSONObject:value
                                                     options:kNilOptions
                                                       error:error];
  if (jsonData == nil || [jsonData length] == 0) {
    return nil;
  } else {
    NSString *str = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
    return [str autorelease];
  }
}
+ (id)jsonParse:(NSString *)value error:(NSError **)error;
{
  return [NSJSONSerialization JSONObjectWithData:[value dataUsingEncoding:NSUTF8StringEncoding]
                                         options:NSJSONReadingMutableContainers
                                           error:error];
}
+ (NSString *)jsonStringify:(id)value
{
  NSError *error = nil;
  NSString *r = [self jsonStringify:value error:&error];
  if (error != nil) {
    NSLog(@"Could not stringify JSON. Error: %@", error);
  }
  return r;
}
+ (id)jsonParse:(NSString *)value
{
  NSError *error = nil;
  id r = [self jsonParse:value error:&error];
  if (error != nil) {
    NSLog(@"Could not parse JSON. Error: %@", error);
  }
  return r;
}

+ (BOOL)forceTouchSupported
{
  return [[[[TiApp app] window] traitCollection] forceTouchCapability] == UIForceTouchCapabilityAvailable;
}

+ (BOOL)livePhotoSupported
{
  return [self isIOSVersionOrGreater:@"9.1"];
}

+ (NSString *)currentArchitecture
{
#ifdef __arm64__
  return @"arm64";
#endif
#ifdef __arm__
  return @"armv7";
#endif
#ifdef __x86_64__
  return @"x86_64";
#endif
#ifdef __i386__
  return @"i386";
#endif
  return @"Unknown";
}

+ (BOOL)validatePencilWithTouch:(UITouch *)touch
{
  if ([self isIOSVersionOrGreater:@"9.1"]) {
    return [touch type] == UITouchTypeStylus;
  } else {
    return NO;
  }
}

// Credits: http://stackoverflow.com/a/14525049/5537752
+ (UIImage *)imageWithColor:(UIColor *)color
{
  CGRect rect = CGRectMake(0.0f, 0.0f, 1.0f, 1.0f);
  UIGraphicsBeginImageContext(rect.size);
  CGContextRef context = UIGraphicsGetCurrentContext();

  CGContextSetFillColorWithColor(context, [color CGColor]);
  CGContextFillRect(context, rect);

  UIImage *image = UIGraphicsGetImageFromCurrentImageContext();
  UIGraphicsEndImageContext();

  return image;
}

+ (id)stripInvalidJSONPayload:(id)jsonPayload
{
  NSDateFormatter *dateFormatter = [[NSDateFormatter alloc] init];
  dateFormatter.locale = [NSLocale localeWithLocaleIdentifier:@"en_US_POSIX"];
  dateFormatter.timeZone = [NSTimeZone timeZoneForSecondsFromGMT:0];
  // ISO08601 formatting, same as Javascript stringified new Date()
  dateFormatter.dateFormat = @"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

  if ([jsonPayload isKindOfClass:[NSDictionary class]]) {
    NSMutableDictionary *result = [NSMutableDictionary new];
    for (NSString *key in [jsonPayload allKeys]) {
      id value = [jsonPayload valueForKey:key];
      if ([value isKindOfClass:[NSArray class]] || [value isKindOfClass:[NSDictionary class]]) {
        value = [TiUtils stripInvalidJSONPayload:value];
      }
      if ([self isSupportedFragment:value]) {
        if ([value isKindOfClass:[NSDate class]]) {
          value = [dateFormatter stringFromDate:value];
        }
        [result setObject:value forKey:key];
      } else {
        DebugLog(@"[WARN] Found invalid attribute \"%@\" that cannot be serialized, skipping it ...", key)
      }
    }
    return [result autorelease];
  } else if ([jsonPayload isKindOfClass:[NSArray class]]) {
    NSMutableArray *result = [NSMutableArray new];
    for (id value in [jsonPayload allObjects]) {
      if ([value isKindOfClass:[NSArray class]] || [value isKindOfClass:[NSDictionary class]]) {
        value = [TiUtils stripInvalidJSONPayload:value];
      }
      if ([self isSupportedFragment:value]) {
        if ([value isKindOfClass:[NSDate class]]) {
          value = [dateFormatter stringFromDate:value];
        }
        [result addObject:value];
      } else {
        DebugLog(@"[WARN] Found invalid value \"%@\" that cannot be serialized, skipping it ...", value);
      }
    }
    return [result autorelease];
  } else {
    DebugLog(@"[ERROR] Unhandled JSON type: %@", NSStringFromClass([jsonPayload class]));
  }

  return jsonPayload;
}

+ (BOOL)isSupportedFragment:(id)fragment
{
  return ([fragment isKindOfClass:[NSDictionary class]] || [fragment isKindOfClass:[NSArray class]] ||
      [fragment isKindOfClass:[NSString class]] || [fragment isKindOfClass:[NSNumber class]] ||
      [fragment isKindOfClass:[NSDate class]] || [fragment isKindOfClass:[NSNull class]] || fragment == nil);
}

+ (BOOL)isUsingLaunchScreenStoryboard
{
  @try {
    return [UIStoryboard storyboardWithName:@"LaunchScreen" bundle:[NSBundle mainBundle]] != nil;
  } @catch (NSException *e) {
    return NO;
  }
}

+ (BOOL)isHyperloopAvailable
{
  static BOOL isHyperloopAvailable = NO;
  static dispatch_once_t onceToken;
  dispatch_once(&onceToken, ^{
    Class cls = NSClassFromString(@"Hyperloop");
    isHyperloopAvailable = cls != nil;
  });
  return isHyperloopAvailable;
}

+ (UIImageSymbolWeight)symbolWeightFromString:(NSString *)string
{
  if ([string isEqualToString:@"ultralight"]) {
    return UIImageSymbolWeightUltraLight;
  } else if ([string isEqualToString:@"light"]) {
    return UIImageSymbolWeightLight;
  } else if ([string isEqualToString:@"thin"]) {
    return UIImageSymbolWeightThin;
  } else if ([string isEqualToString:@"normal"]) {
    return UIImageSymbolWeightRegular;
  } else if ([string isEqualToString:@"semibold"]) {
    return UIImageSymbolWeightSemibold;
  } else if ([string isEqualToString:@"bold"]) {
    return UIImageSymbolWeightBold;
  } else if ([string isEqualToString:@"heavy"]) {
    return UIImageSymbolWeightHeavy;
  } else if ([string isEqualToString:@"black"]) {
    return UIImageSymbolWeightBlack;
  }

  return UIImageSymbolWeightRegular;
}

@end
