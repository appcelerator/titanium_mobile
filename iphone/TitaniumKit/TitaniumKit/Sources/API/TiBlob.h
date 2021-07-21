/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#import "ObjcProxy.h"
#import <JavaScriptCore/JavaScriptCore.h>

//@class TiFile; // forward declare
@class TiBlob;
@class UIImage;

@protocol BlobExports <JSExport>

// Properties (and accessors)
// FIXME: Change to TiFile* once it's been moved to a new obj-c proxy
/**
 Returns the blob file.
 @return The file.
 */
READONLY_PROPERTY(JSValue *, file, File);
/**
 Returns height if the blob object is an image, _0_ otherwise.
 */
READONLY_PROPERTY(NSUInteger, height, Height);
/**
 Returns height of image after factoring in EXIF orientation, _0_ otherwise.
 */
READONLY_PROPERTY(NSUInteger, uprightHeight, UprightHeight);
/**
 Returns the data length.
 */
READONLY_PROPERTY(NSUInteger, length, Length);
/**
 Returns the blob mime type.
 @return The mime type string.
 */
READONLY_PROPERTY(NSString *, mimeType, MimeType);
/**
 Returns the blob native path (Android compatibility).
 @return The blob native path.
 */
READONLY_PROPERTY(NSString *, nativePath, NativePath);
/**
 Return the data size.
 
 For file, data returns the size in bytes, for image, returns the width x height.
 */
READONLY_PROPERTY(NSUInteger, size, Size);
/**
 Return a textual representation of the blob.
 
 The method converts data into a textual representation. Appropriate only for types TiBlobTypeFile and TiBlobTypeData.
 */
READONLY_PROPERTY(NSString *, text, Text);
/**
 Returns width if the blob object is an image, _0_ otherwise.
 */
READONLY_PROPERTY(NSUInteger, width, Width);
/**
 Returns width of image after factoring in EXIF orientation, _0_ otherwise.
 */
READONLY_PROPERTY(NSUInteger, uprightWidth, UprightWidth);

// Methods
- (void)append:(TiBlob *)blob;
- (TiBlob *)imageAsCompressed:(float)compressionQuality;
- (TiBlob *)imageAsCropped:(NSDictionary *)options;
JSExportAs(imageAsResized,
           -(TiBlob *)imageAsResized
           : (NSUInteger)width withHeight
           : (NSUInteger)height);
JSExportAs(imageAsThumbnail,
           -(TiBlob *)imageAsThumbnail
           : (NSUInteger)size withBorder
           : (NSNumber *)optionalBorderSize withRadius
           : (NSNumber *)optionalCornerRadius);
- (TiBlob *)imageWithAlpha;
JSExportAs(imageWithRoundedCorner,
           -(TiBlob *)imageWithRoundedCorner
           : (NSUInteger)cornerSize withBorder
           : (NSNumber *)optionalBorderSize);
- (TiBlob *)imageWithTransparentBorder:(NSUInteger)size;
- (NSString *)toString;
- (JSValue *)toArrayBuffer;
- (JSValue *)arrayBuffer;

@end

typedef enum {
  TiBlobTypeImage = 0,
  TiBlobTypeFile = 1,
  TiBlobTypeData = 2,
  TiBlobTypeSystemImage = 3
} TiBlobType;

/**
 Blob object class.
 */
@interface TiBlob : ObjcProxy <BlobExports> {
  @private
  TiBlobType type;
  NSString *mimetype;
  NSData *data;
  UIImage *image;
  NSString *path;
  BOOL imageLoadAttempted;
  NSString *systemImageName;
}

/**
 Initialize the blob with an image.
 @param image The image
 @deprecated Only here for backwards compatibility with SDK < 8.1.0. Use `initWithImage:` instead.
 */
- (id)_initWithPageContext:(__unused id<TiEvaluator>)pageContext andImage:(UIImage *)image __attribute__((deprecated));

/**
 Initialize the blob with data.
 @param data The raw data.
 @param mimetype The data mime type.
 @deprecated Only here for backwards compatibility with SDK < 8.1.0. Use `initWithData:mimeType:` instead.
 */
- (id)_initWithPageContext:(__unused id<TiEvaluator>)pageContext andData:(NSData *)data mimetype:(NSString *)mimetype __attribute__((deprecated));

/**
 Initialize the blob with contents of a file.
 @param path The path to the file.
 @deprecated Only here for backwards compatibility with SDK < 8.1.0. Use `initWithFile:` instead.
 */
- (id)_initWithPageContext:(__unused id<TiEvaluator>)pageContext andFile:(NSString *)path __attribute__((deprecated));

/**
 Initialize the blob with an image.
 @param image The image
 */
- (id)initWithImage:(UIImage *)image;

/**
Initialize the blob with a system image.
@param imageName The  system image name
*/
- (id)initWithSystemImage:(NSString *)imageName andParameters:(NSDictionary *)parameters;

/**
 Returns the System Image Name .
 @return The string or nil.
 */
- (NSString *)systemImageName;

/**
 Initialize the blob with data.
 @param data_ The raw data.
 @param mimetype_ The data mime type.
 */
- (id)initWithData:(NSData *)data_ mimetype:(NSString *)mimetype_;

/**
 Initialize the blob with data. Used for encrypted files/assets.
 @param data_ The raw data.
 @param path_ The path to the file.
 */
- (id)initWithData:(NSData *)data_ andPath:(NSString *)path_;

/**
 Initialize the blob with contents of a file.
 @param path The path to the file.
 */
- (id)initWithFile:(NSString *)path;

/**
 Initialises blob with data.
 @param data Th data to set.
 */
- (void)setData:(NSData *)data;

/**
 Initializes blob with image.
 @param image The image to set.
 */
- (void)setImage:(UIImage *)image;

/**
 Sets the blob type.
 @param mime The mime type string.
 @param type The blob type.
 */
- (void)setMimeType:(NSString *)mime type:(TiBlobType)type;

/**
 Returns the blob type.
 @return The blob type.
 */
- (TiBlobType)type;

/**
 Returns the blob raw data.
 @return The raw data.
 */
- (NSData *)data;

/**
 Returns the blob image.
 @return The image or _nil_ if the blob data cannot represent an image.
 */
- (UIImage *)image;

/**
 Returns the blob file path.
 @return The file path.
 */
- (NSString *)path;

/**
 Tells the blob to write its data to a file.
 @param path The file path.
 @param error The error result if failed.
 @return _YES_ if the write operation succeeded, _NO_ otherwise.
 */
- (BOOL)writeTo:(NSString *)path error:(NSError **)error;

#pragma mark Image specific blob manipulations

@end
