/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#import "TiBlob.h"
#import "Mimetypes.h"
#import "TiUtils.h"
#import "UIImage+Alpha.h"
#import "UIImage+Resize.h"
#import "UIImage+RoundedCorner.h"
//NOTE:FilesystemFile is conditionally compiled based on the filesystem module.
#import "KrollPromise.h"
#import "TiFilesystemFileProxy.h"

static NSString *const MIMETYPE_PNG = @"image/png";
static NSString *const MIMETYPE_JPEG = @"image/jpeg";

@implementation TiBlob

- (void)dealloc
{
  RELEASE_TO_NIL(mimetype);
  RELEASE_TO_NIL(data);
  RELEASE_TO_NIL(image);
  RELEASE_TO_NIL(path);
  RELEASE_TO_NIL(systemImageName);
  [super dealloc];
}

- (id)description
{
  NSString *text = [self text];
  if (text == nil || [text isEqualToString:@""]) {
    return @"[object TiBlob]";
  }
  return text;
}

- (NSString *)apiName
{
  return @"Ti.Blob";
}

- (void)ensureImageLoaded
{
  if (image == nil && !imageLoadAttempted) {
    imageLoadAttempted = YES;
    switch (type) {
    case TiBlobTypeFile: {
      image = [[UIImage imageWithContentsOfFile:path] retain];
      break;
    }
    case TiBlobTypeData: {
      image = [[UIImage imageWithData:data] retain];
      break;
    }
    default: {
      break;
    }
    }
  }
}

- (NSUInteger)width
{
  [self ensureImageLoaded];
  if (image != nil) {
    return image.size.width * image.scale;
  }
  return 0;
}
GETTER_IMPL(NSUInteger, width, Width);

- (NSUInteger)uprightWidth
{
  return [self width];
}
GETTER_IMPL(NSUInteger, uprightWidth, UprightWidth);

- (NSUInteger)height
{
  [self ensureImageLoaded];
  if (image != nil) {
    return image.size.height * image.scale;
  }
  return 0;
}
GETTER_IMPL(NSUInteger, height, Height);

- (NSUInteger)uprightHeight
{
  return [self height];
}
GETTER_IMPL(NSUInteger, uprightHeight, UprightHeight);

- (NSUInteger)size
{
  [self ensureImageLoaded];
  if (image != nil) {
    return image.size.width * image.size.height * image.scale * image.scale;
  }
  switch (type) {
  case TiBlobTypeData: {
    return [data length];
  }
  case TiBlobTypeFile: {
    NSFileManager *fm = [NSFileManager defaultManager];
    NSError *error = nil;
    NSDictionary *resultDict = [fm attributesOfItemAtPath:path error:&error];
    id result = [resultDict objectForKey:NSFileSize];
    if (error != NULL) {
      return 0;
    }
    return [result intValue];
  }
  default: {
    break;
  }
  }
  return 0;
}
GETTER_IMPL(NSUInteger, size, Size);

- (id)_initWithPageContext:(id<TiEvaluator>)pageContext andImage:(UIImage *)image
{
  return [self initWithImage:image];
}

- (id)_initWithPageContext:(id<TiEvaluator>)pageContext andData:(NSData *)data mimetype:(NSString *)mimetype
{
  return [self initWithData:data mimetype:mimetype];
}

- (id)_initWithPageContext:(id<TiEvaluator>)pageContext andFile:(NSString *)path
{
  return [self initWithFile:path];
}

- (id)initWithImage:(UIImage *)image_
{
  if (self = [super init]) {
    image = [image_ retain];
    type = TiBlobTypeImage;
    mimetype = [([UIImageAlpha hasAlpha:image_] ? MIMETYPE_PNG : MIMETYPE_JPEG) copy];
  }
  return self;
}

- (id)initWithSystemImage:(NSString *)imageName andParameters:(NSDictionary *)parameters
{
  if (![TiUtils isIOSVersionOrGreater:@"13.0"]) {
    return nil;
  }

  if (self = [super init]) {
    if (parameters == nil) {
      image = [[UIImage systemImageNamed:imageName] retain];
    } else {
      UIImageSymbolWeight nativeWeight = [TiUtils symbolWeightFromString:parameters[@"weight"]];
      CGFloat nativeSize = [TiUtils floatValue:parameters[@"size"] def:0.0];
      UIImageSymbolConfiguration *configuration;

      if (nativeSize > 0) {
        configuration = [UIImageSymbolConfiguration configurationWithPointSize:nativeSize weight:nativeWeight scale:UIImageSymbolScaleDefault];
      } else {
        configuration = [UIImageSymbolConfiguration configurationWithWeight:nativeWeight];
      }

      image = [[UIImage systemImageNamed:imageName withConfiguration:configuration] retain];
    }
    type = TiBlobTypeSystemImage;
    systemImageName = [imageName retain];
    mimetype = [([UIImageAlpha hasAlpha:image] ? MIMETYPE_PNG : MIMETYPE_JPEG) copy];
  }
  return self;
}

- (NSString *)systemImageName
{
  return systemImageName;
}

- (id)initWithData:(NSData *)data_ mimetype:(NSString *)mimetype_
{
  if (self = [super init]) {
    data = [data_ retain];
    type = TiBlobTypeData;
    mimetype = [mimetype_ copy];
  }
  return self;
}

- (id)initWithData:(NSData *)data_ andPath:(NSString *)path_
{
  if (self = [super init]) {
    data = [data_ retain];
    type = TiBlobTypeData;
    path = [path_ retain];
    mimetype = [[Mimetypes mimeTypeForExtension:path] copy];
  }
  return self;
}

- (id)initWithFile:(NSString *)path_
{
  if (self = [super init]) {
    type = TiBlobTypeFile;
    path = [path_ retain];
    mimetype = [[Mimetypes mimeTypeForExtension:path] copy];
  }
  return self;
}

- (TiBlobType)type
{
  return type;
}

- (NSString *)mimeType
{
  return mimetype;
}
GETTER_IMPL(NSString *, mimeType, Mimetype);

- (NSString *)text
{
  switch (type) {
  case TiBlobTypeFile: {
    NSData *fdata = [self data];
    return [[[NSString alloc] initWithData:fdata encoding:NSUTF8StringEncoding] autorelease];
  }
  case TiBlobTypeData: {
    return [[[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding] autorelease];
  }
  default: {
    break;
  }
  }
  // anything else we refuse to write out
  return nil;
}
GETTER_IMPL(NSString *, text, Text);

- (NSData *)data
{
  switch (type) {
  case TiBlobTypeFile: {
    NSError *error = nil;
    return [NSData dataWithContentsOfFile:path options:0 error:&error];
  }
  case TiBlobTypeImage: {
    if ([mimetype isEqualToString:MIMETYPE_PNG]) {
      return UIImagePNGRepresentation(image);
    }
    return UIImageJPEGRepresentation(image, 1.0);
  }
  default: {
    break;
  }
  }
  return data;
}

- (UIImage *)image
{
  [self ensureImageLoaded];
  return image;
}

- (void)setData:(NSData *)data_
{
  RELEASE_TO_NIL(data);
  RELEASE_TO_NIL(image);
  type = TiBlobTypeData;
  data = [data_ retain];
  imageLoadAttempted = NO;
}

- (void)setImage:(UIImage *)image_
{
  RELEASE_TO_NIL(image);
  image = [image_ retain];
  [self setMimeType:([UIImageAlpha hasAlpha:image_] ? MIMETYPE_PNG : MIMETYPE_JPEG) type:TiBlobTypeImage];
}

- (NSString *)path
{
  return path;
}

// FIXME Move to addon in filesystem module!
// FIXME: Change to TiFile * once it's been moved to a new obj-c proxy
- (JSValue *)file
{
  if (path != nil) {
    JSContext *objcJsContext = [JSContext currentContext];
    JSGlobalContextRef contextRef = [objcJsContext JSGlobalContextRef];
    KrollContext *context = GetKrollContext(contextRef);
    TiFile *file = [[[TiFilesystemFileProxy alloc] initWithFile:path] autorelease];
    KrollObject *o = [[[KrollObject alloc] initWithTarget:file context:context] autorelease];
    return [JSValue valueWithJSValueRef:JSObjectMake(contextRef, KrollObjectClassRef, o) inContext:objcJsContext];
  }
  return nil;
}
GETTER_IMPL(JSValue *, file, File);

- (NSString *)nativePath
{
  if (path != nil) {
    return [[NSURL fileURLWithPath:path] absoluteString];
  }
  return nil;
}
GETTER_IMPL(NSString *, nativePath, NativePath);

- (NSUInteger)length
{
  return [[self data] length];
}
GETTER_IMPL(NSUInteger, length, Length);

- (void)setMimeType:(NSString *)mime type:(TiBlobType)type_
{
  RELEASE_TO_NIL(mimetype);
  mimetype = [mime copy];
  type = type_;
}

- (BOOL)writeTo:(NSString *)destination error:(NSError **)error
{
  NSData *writeData = nil;
  switch (type) {
  case TiBlobTypeFile: {
    NSFileManager *fm = [NSFileManager defaultManager];
    return [fm copyItemAtPath:path toPath:destination error:error];
  }
  case TiBlobTypeImage: {
    writeData = [self data];
    break;
  }
  case TiBlobTypeData: {
    writeData = data;
    break;
  }
  }
  if (writeData != nil) {
    return [writeData writeToFile:destination atomically:YES];
  }
  return NO;
}

- (void)append:(TiBlob *)blob
{
  NSData *otherData = [blob data]; // other Blob's data

  NSMutableData *newData = [[NSMutableData alloc] initWithData:[self data]];
  [newData appendData:otherData];

  [self setData:newData];
  RELEASE_TO_NIL(newData);
}

#pragma mark Image Manipulations

- (TiBlob *)imageWithAlpha
{
  [self ensureImageLoaded];
  if (image != nil) {
    TiBlob *blob = [[TiBlob alloc] initWithImage:[UIImageAlpha imageWithAlpha:image]];
    return [blob autorelease];
  }
  return nil;
}

- (TiBlob *)imageWithTransparentBorder:(NSUInteger)size
{
  [self ensureImageLoaded];
  if (image != nil) {
    TiBlob *blob = [[TiBlob alloc] initWithImage:[UIImageAlpha transparentBorderImage:size image:image]];
    return [blob autorelease];
  }
  return nil;
}

- (TiBlob *)imageWithRoundedCorner:(NSUInteger)cornerSize withBorder:(NSNumber *)optionalBorderSize
{
  [self ensureImageLoaded];
  if (image != nil) {
    // border is optional and should default to 1 if not specified! (or if negative)
    OPTIONAL_UINT_ARGUMENT(optionalBorderSize, borderSize, 1);
    TiBlob *blob = [[TiBlob alloc] initWithImage:[UIImageRoundedCorner roundedCornerImage:cornerSize borderSize:borderSize image:image]];
    return [blob autorelease];
  }
  return nil;
}

- (TiBlob *)imageAsThumbnail:(NSUInteger)size withBorder:(NSNumber *)optionalBorderSize withRadius:(NSNumber *)optionalCornerRadius
{
  [self ensureImageLoaded];
  if (image != nil) {
    // border is optional and should default to 1 if not specified! (or if negative)
    OPTIONAL_UINT_ARGUMENT(optionalBorderSize, borderSize, 1);
    // radius is optional and should default to 0 if not specified! (or if negative)
    OPTIONAL_UINT_ARGUMENT(optionalCornerRadius, cornerRadius, 0);
    TiBlob *blob = [[TiBlob alloc] initWithImage:[UIImageResize thumbnailImage:size
                                                             transparentBorder:borderSize
                                                                  cornerRadius:cornerRadius
                                                          interpolationQuality:kCGInterpolationHigh
                                                                         image:image]];
    return [blob autorelease];
  }
  return nil;
}

- (TiBlob *)imageAsResized:(NSUInteger)width withHeight:(NSUInteger)height
{
  // How do we test that they didn't send us a "bad" value (i.e. negative, or double/float)? It'll get coerced here. Do we care?
  if (isnan(width)) {
    THROW_INVALID_ARG(@"width argument must be an integer value");
  }
  if (isnan(height)) {
    THROW_INVALID_ARG(@"height argument must be an integer value");
  }
  [self ensureImageLoaded];
  if (image != nil) {
    TiBlob *blob = [[TiBlob alloc] initWithImage:[UIImageResize resizedImage:CGSizeMake(width, height) interpolationQuality:kCGInterpolationHigh image:image hires:NO]];
    return [blob autorelease];
  }
  return nil;
}

- (TiBlob *)imageAsCompressed:(float)compressionQuality
{
  [self ensureImageLoaded];
  if (image != nil) {
    // if unspecified, use 1.0 as default
    if (isnan(compressionQuality)) {
      compressionQuality = 1.0f;
    }
    return [[[TiBlob alloc] initWithData:UIImageJPEGRepresentation(image, compressionQuality) mimetype:@"image/jpeg"] autorelease];
  }
  return nil;
}

- (TiBlob *)imageAsCropped:(NSDictionary *)args
{
  [self ensureImageLoaded];
  if (image != nil) {
    CGRect bounds;
    CGSize imageSize = [image size];
    bounds.size.width = [TiUtils floatValue:@"width" properties:args def:imageSize.width];
    bounds.size.height = [TiUtils floatValue:@"height" properties:args def:imageSize.height];
    bounds.origin.x = [TiUtils floatValue:@"x" properties:args def:(imageSize.width - bounds.size.width) / 2.0];
    bounds.origin.y = [TiUtils floatValue:@"y" properties:args def:(imageSize.height - bounds.size.height) / 2.0];
    TiBlob *blob = [[TiBlob alloc] initWithImage:[UIImageResize croppedImage:bounds image:image]];
    return [blob autorelease];
  }
  return nil;
}

- (NSString *)toString
{
  NSString *t = [self text];
  if (t != nil) {
    return t;
  }
  return @"[object TiBlob]";
}

static void jsArrayBufferFreeDeallocator(void *data, void *ctx)
{
  free(data);
}

- (JSValue *)toArrayBuffer
{
  NSData *theData = [self data];
  // Copy the raw bytes of the NSData we're wrapping
  NSUInteger len = [theData length];
  void *arrayBytes = malloc(len);
  [theData getBytes:arrayBytes length:len];

  // Now make an ArrayBuffer with the copied bytes
  JSContext *context = JSContext.currentContext;
  JSValueRef *exception;
  JSObjectRef arrayBuffer = JSObjectMakeArrayBufferWithBytesNoCopy(context.JSGlobalContextRef, arrayBytes, len, jsArrayBufferFreeDeallocator, nil, exception);
  return [JSValue valueWithJSValueRef:arrayBuffer inContext:context];
}

- (JSValue *)arrayBuffer
{
  JSContext *context = [self currentContext];
  KrollPromise *promise = [[[KrollPromise alloc] initInContext:context] autorelease];
  TiThreadPerformOnMainThread(
      ^{
        NSData *theData = [self data];
        // Copy the raw bytes of the NSData we're wrapping
        NSUInteger len = [theData length];
        void *arrayBytes = malloc(len);
        [theData getBytes:arrayBytes length:len];

        // Now make an ArrayBuffer with the copied bytes
        JSValueRef *exception;
        JSObjectRef arrayBuffer = JSObjectMakeArrayBufferWithBytesNoCopy(context.JSGlobalContextRef, arrayBytes, len, jsArrayBufferFreeDeallocator, nil, exception);
        if (exception) {
          [promise reject:@[ [JSValue valueWithJSValueRef:exception inContext:context] ]];
        } else {
          JSValue *buffer = [JSValue valueWithJSValueRef:arrayBuffer inContext:context];
          [promise resolve:@[ buffer ]];
        }
      },
      NO);
  return promise.JSValue;
}
@end
