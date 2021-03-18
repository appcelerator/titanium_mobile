/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#import "TiFilesystemBlobProxy.h"

#ifdef USE_TI_FILESYSTEM
#import <TitaniumKit/TiBase.h>

#import <TitaniumKit/Mimetypes.h>
#import <TitaniumKit/TiBlob.h>
#import <TitaniumKit/TiFilesystemFileProxy.h>
#import <TitaniumKit/TiUtils.h>

#define FILE_TOSTR(x) \
  ([x isKindOfClass:[TiFilesystemFileProxy class]]) ? [(TiFilesystemFileProxy *)x nativePath] : [TiUtils stringValue:x]

@implementation TiFilesystemBlobProxy

- (id)initWithURL:(NSURL *)url_ data:(NSData *)data_
{
  if (self = [super initWithPath:[url_ path]]) {
    url = [url_ retain];
    data = [data_ retain];
  }
  return self;
}

- (void)dealloc
{
  RELEASE_TO_NIL(url);
  RELEASE_TO_NIL(data);
  [super dealloc];
}

- (NSString *)apiName
{
  //Should we return Ti.FileSystem.Blob?
  return @"Ti.Filesystem.File";
}

- (id)nativePath
{
  return [[NSURL fileURLWithPath:path] absoluteString];
}

- (id)exists:(id)args
{
  return NUMBOOL(YES);
}

- (NSNumber *)isFile:(id)unused
{
  return NUMBOOL(YES);
}

- (NSNumber *)isDirectory:(id)unused
{
  return NUMBOOL(NO);
}

- (id)readonly
{
  return NUMBOOL(YES);
}

- (id)symbolicLink
{
  return NUMBOOL(NO);
}

- (id)writable
{
  return NUMBOOL(NO);
}

#define FILENOOP(name)  \
  -(id)name             \
  {                     \
    return NUMBOOL(NO); \
  }

FILENOOP(executable);
FILENOOP(hidden);
FILENOOP(setReadonly
         : (id)x);
FILENOOP(setExecutable
         : (id)x);
FILENOOP(setHidden
         : (id)x);

- (NSDate *)createTimestamp:(id)unused
{
  DEPRECATED_REPLACED(@"Filesystem.File.createTimestamp()", @"7.3.0", @"Filesystem.File.createdAt()");
  return [self createdAt:unused];
}

- (NSDate *)createdAt:(id)unused
{
  return [NSDate dateWithTimeIntervalSince1970:0];
}

- (NSDate *)modificationTimestamp:(id)unused
{
  DEPRECATED_REPLACED(@"Filesystem.File.modificationTimestamp()", @"7.3.0", @"Filesystem.File.modifiedAt()");
  return [self modifiedAt:nil];
}

- (NSDate *)modifiedAt:(id)unused
{
  return [NSDate dateWithTimeIntervalSince1970:0];
}

- (NSArray *)getDirectoryListing:(id)args
{
  return nil;
}

- (NSNumber *)spaceAvailable:(id)unused
{
  id parent = [self parent];
  if (parent != nil) {
    return [parent spaceAvailable:nil];
  }
  return @(0);
}

- (NSNumber *)createDirectory:(id)args
{
  return NUMBOOL(NO);
}

- (NSNumber *)createFile:(id)args
{
  return NUMBOOL(NO);
}

- (NSNumber *)deleteDirectory:(id)args
{
  return NUMBOOL(NO);
}

- (NSNumber *)deleteFile:(id)args
{
  return NUMBOOL(NO);
}

- (NSNumber *)move:(id)args
{
  return NUMBOOL(NO);
}

- (NSNumber *)rename:(id)args
{
  return NUMBOOL(NO);
}

- (TiBlob *)read:(id)args
{
  return [[[TiBlob alloc] initWithData:data andPath:[self nativePath]] autorelease];
}

- (TiStreamProxy *)open:(id)args
{
  TiStreamMode mode;
  ENSURE_INT_AT_INDEX(mode, args, 0);

  if (mode != TI_READ) {
    [self throwException:@"TypeError"
               subreason:[NSString stringWithFormat:@"Invalid mode value %d", mode]
                location:CODELOCATION];
  }

  TiDataStream *stream = [[[TiDataStream alloc] _initWithPageContext:[self executionContext]] autorelease];
  [stream setData:data];
  [stream setMode:mode];

  return stream;
}

- (NSString *)_grabFirstArgumentAsFileName_:(id)args
{
  NSString *arg = [args objectAtIndex:0];
  NSString *file = FILE_TOSTR(arg);
  NSURL *fileUrl = [NSURL URLWithString:file];
  if ([fileUrl isFileURL]) {
    file = [fileUrl path];
  }
  NSString *dest = [file stringByStandardizingPath];
  return dest;
}

// Xcode complains about the "copy" method naming, but in this case it's a proxy method so we are fine
#ifndef __clang_analyzer__
- (NSNumber *)copy:(id)args
{
  ENSURE_TYPE(args, NSArray);

  NSString *dest = [self _grabFirstArgumentAsFileName_:args];
  if (![dest isAbsolutePath]) {
    NSString *subpath = [path stringByDeletingLastPathComponent];
    dest = [subpath stringByAppendingPathComponent:dest];
  }

  NSError *err = nil;
  [data writeToFile:dest options:NSDataWritingFileProtectionComplete | NSDataWritingAtomic error:&err];
  if (err != nil) {
    NSLog(@"[ERROR] Could not write data to file at path \"%@\" - details: %@", dest, err);
  }
  return NUMBOOL(err == nil);
}
#endif

- (NSNumber *)append:(id)args
{
  return NUMBOOL(NO);
}

- (NSNumber *)write:(id)args
{
  return NUMBOOL(NO);
}

- (NSString *)extension:(id)args
{
  return [path pathExtension];
}

- (NSString *)getParent:(id)args
{
  DEPRECATED_REPLACED(@"Filesystem.File.getParent()", @"7.0.0", @"Filesystem.File.parent");
  return [path stringByDeletingLastPathComponent];
}

- (TiFilesystemFileProxy *)parent
{
  return [[[TiFilesystemFileProxy alloc] initWithFile:[path stringByDeletingLastPathComponent]] autorelease];
}

- (id)name
{
  return [path lastPathComponent];
}

- (id)resolve:(id)args
{
  return [self nativePath];
}

- (id)description
{
  return path;
}

- (unsigned long long)size
{
  return data.length;
}

@end

#endif
