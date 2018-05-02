/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2018 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#if defined(USE_TI_FILESYSTEM) || defined(USE_TI_DATABASE) || defined(USE_TI_MEDIA)

#import "TiFilesystemFileProxy.h"
#import "TiBase.h"
#import "TiBlob.h"
#import "TiFilesystemFileStreamProxy.h"
#import "TiUtils.h"
#include <sys/xattr.h>

#define FILE_TOSTR(x) \
  ([x isKindOfClass:[TiFilesystemFileProxy class]]) ? [(TiFilesystemFileProxy *)x nativePath] : [TiUtils stringValue:x]

@implementation TiFilesystemFileProxy

- (id)initWithFile:(NSString *)path_
{
  if (self = [super init]) {
    fm = [[NSFileManager alloc] init];
    path = [path_ retain];
  }
  return self;
}

- (void)dealloc
{
  RELEASE_TO_NIL(fm);
  RELEASE_TO_NIL(path);
  [super dealloc];
}

- (NSString *)apiName
{
  return @"Ti.Filesystem.File";
}

- (NSString *)nativePath
{
  return [[NSURL fileURLWithPath:path] absoluteString];
}

- (NSNumber *)exists:(id)args
{
  return NUMBOOL([fm fileExistsAtPath:path]);
}

- (NSNumber *)readonly
{
  NSError *error = nil;
  NSDictionary *resultDict = [fm attributesOfItemAtPath:path error:&error];
  if (error != nil) {
    [self throwException:TiExceptionOSError subreason:[error localizedDescription] location:CODELOCATION];
  }
  return [resultDict objectForKey:NSFileImmutable];
}

- (NSNumber *)createTimestamp
{
  DEPRECATED_REPLACED(@"Filesystem.File.createTimestamp", @"7.2.0", @"Filesystem.File.createTimestamp()");
  return [self createTimestamp:nil];
}

- (NSNumber *)createTimestamp:(id)unused
{
  NSError *error = nil;
  NSDictionary *resultDict = [fm attributesOfItemAtPath:path error:&error];
  if (error != nil) {
    [self throwException:TiExceptionOSError subreason:[error localizedDescription] location:CODELOCATION];
  }
  // Have to do this one up special because of 3.x bug where NSFileCreationDate is sometimes undefined
  NSDate *result = [resultDict objectForKey:NSFileCreationDate];
  if (result == nil) {
    result = [resultDict objectForKey:NSFileModificationDate];
  }
  return @([result timeIntervalSince1970]);
}

- (NSNumber *)modificationTimestamp
{
  DEPRECATED_REPLACED(@"Filesystem.File.modificationTimestamp", @"7.2.0", @"Filesystem.File.modificationTimestamp()");
  return [self modificationTimestamp:nil];
}

- (NSNumber *)modificationTimestamp:(id)unused
{
  NSError *error = nil;
  NSDictionary *resultDict = [fm attributesOfItemAtPath:path error:&error];
  if (error != nil) {
    [self throwException:TiExceptionOSError subreason:[error localizedDescription] location:CODELOCATION];
  }
  NSDate *result = [resultDict objectForKey:NSFileModificationDate];
  return @([result timeIntervalSince1970]);
}

- (NSNumber *)symbolicLink
{
  NSError *error = nil;
  NSDictionary *resultDict = [fm attributesOfItemAtPath:path error:&error];
  if (error != nil) {
    [self throwException:TiExceptionOSError subreason:[error localizedDescription] location:CODELOCATION];
  }
  NSString *fileType = [resultDict objectForKey:NSFileType];

  return NUMBOOL([fileType isEqualToString:NSFileTypeSymbolicLink]);
}

- (NSNumber *)writable
{
  return NUMBOOL(![[self readonly] boolValue]);
}

#define FILENOOP(name)  \
  -(NSNumber *)name     \
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

- (NSArray *)getDirectoryListing:(id)args
{
  NSError *error = nil;
  NSArray *resultArray = [fm contentsOfDirectoryAtPath:path error:&error];
  if (error != nil) {
    //TODO: what should be do?
  }
  return resultArray;
}

- (NSNumber *)spaceAvailable:(id)args
{
  NSError *error = nil;
  NSDictionary *resultDict = [fm attributesOfFileSystemForPath:path error:&error];
  if (error != nil)
    return NUMBOOL(NO);
  return [resultDict objectForKey:NSFileSystemFreeSize];
}

- (NSString *)getProtectionKey:(id)args
{
  NSError *error = nil;
  NSDictionary *resultDict = [fm attributesOfItemAtPath:path error:&error];
  if (error != nil) {
    NSLog(@"[ERROR] Error getting protection key: %@", [TiUtils messageFromError:error]);
    return nil;
  }
  return [resultDict objectForKey:NSFileProtectionKey];
}

- (NSNumber *)setProtectionKey:(id)args
{
  ENSURE_SINGLE_ARG(args, NSString);
  NSError *error = nil;
  [fm setAttributes:[NSDictionary dictionaryWithObjectsAndKeys:args, NSFileProtectionKey, nil] ofItemAtPath:path error:&error];
  if (error != nil) {
    NSLog(@"[ERROR] Error setting protection key: %@", [TiUtils messageFromError:error]);
    return NUMBOOL(NO);
  }
  return NUMBOOL(YES);
}

- (NSNumber *)createDirectory:(id)args
{
  BOOL result = NO;
  if (![fm fileExistsAtPath:path]) {
    BOOL recurse = args != nil && [args count] > 0 ? [TiUtils boolValue:[args objectAtIndex:0]] : YES;
    result = [fm createDirectoryAtPath:path withIntermediateDirectories:recurse attributes:nil error:nil];
  }
  return NUMBOOL(result);
}

- (NSNumber *)isFile:(id)unused
{
  BOOL isDirectory;
  return NUMBOOL([fm fileExistsAtPath:path isDirectory:&isDirectory] && !isDirectory);
}

- (NSNumber *)isDirectory:(id)unused
{
  BOOL isDirectory;
  return NUMBOOL([fm fileExistsAtPath:path isDirectory:&isDirectory] && isDirectory);
}

- (TiFilesystemFileStreamProxy *)open:(id)args
{
  NSNumber *mode = nil;
  ENSURE_ARG_AT_INDEX(mode, args, 0, NSNumber);
  ENSURE_VALUE_RANGE([mode intValue], TI_READ, TI_APPEND);

  NSArray *payload = [NSArray arrayWithObjects:[self path], mode, nil];

  return [[[TiFilesystemFileStreamProxy alloc] _initWithPageContext:[self executionContext] args:payload] autorelease];
}

- (NSNumber *)copy:(id)args
{
  ENSURE_TYPE(args, NSArray);
  NSError *error = nil;
  NSString *dest = [self _grabFirstArgumentAsFileName_:args];

  if (![dest isAbsolutePath]) {
    NSString *subpath = [path stringByDeletingLastPathComponent];
    dest = [subpath stringByAppendingPathComponent:dest];
  }

  BOOL result = [fm copyItemAtPath:path toPath:dest error:&error];
  if (error != nil) {
    NSLog(@"[ERROR] Could not copy: %@", error.localizedDescription);
    return @NO;
  }
  return NUMBOOL(result);
}

- (NSNumber *)createFile:(id)args
{
  BOOL result = NO;
  if (![fm fileExistsAtPath:path]) {
    BOOL shouldCreate = args != nil && [args count] > 0 ? [TiUtils boolValue:[args objectAtIndex:0]] : NO;
    if (shouldCreate) {
      [fm createDirectoryAtPath:[path stringByDeletingLastPathComponent] withIntermediateDirectories:YES attributes:nil error:nil];
      //We don't care if this fails.
    }
    result = [[NSData data] writeToFile:path options:NSDataWritingFileProtectionComplete | NSDataWritingAtomic error:nil];
  }
  return NUMBOOL(result);
}

- (NSNumber *)deleteDirectory:(id)args
{
  BOOL result = NO;
  BOOL isDirectory = NO;
  BOOL exists = [fm fileExistsAtPath:path isDirectory:&isDirectory];
  if (exists && isDirectory) {
    NSError *error = nil;
    BOOL shouldDelete = args != nil && [args count] > 0 ? [TiUtils boolValue:[args objectAtIndex:0]] : NO;
    if (!shouldDelete) {
      NSArray *remainers = [fm contentsOfDirectoryAtPath:path error:&error];
      if (error == nil) {
        if ([remainers count] == 0) {
          shouldDelete = YES;
        }
      }
    }
    if (shouldDelete) {
      result = [fm removeItemAtPath:path error:&error];
    }
  }
  return NUMBOOL(result);
}

- (NSNumber *)deleteFile:(id)args
{
  BOOL result = NO;
  BOOL isDirectory = YES;
  BOOL exists = [fm fileExistsAtPath:path isDirectory:&isDirectory];
  if (exists && !isDirectory) {
    result = [fm removeItemAtPath:path error:nil];
  }
  return NUMBOOL(result);
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

- (NSNumber *)move:(id)args
{
  ENSURE_TYPE(args, NSArray);
  NSError *error = nil;
  NSString *dest = [self _grabFirstArgumentAsFileName_:args];

  if (![dest isAbsolutePath]) {
    NSString *subpath = [path stringByDeletingLastPathComponent];
    dest = [subpath stringByAppendingPathComponent:dest];
  }

  BOOL result = [fm moveItemAtPath:path toPath:dest error:&error];
  return NUMBOOL(result);
}

- (NSNumber *)rename:(id)args
{
  ENSURE_TYPE(args, NSArray);
  NSString *dest = [self _grabFirstArgumentAsFileName_:args];
  NSString *ourSubpath = [path stringByDeletingLastPathComponent];

  if ([dest isAbsolutePath]) {
    NSString *destSubpath = [dest stringByDeletingLastPathComponent];

    if (![ourSubpath isEqualToString:destSubpath]) {
      return NUMBOOL(NO); // rename is not move
    }
  }

  return [self move:args];
}

- (TiBlob *)read:(id)args
{
  BOOL exists = [fm fileExistsAtPath:path];
  if (!exists)
    return nil;
  return [[[TiBlob alloc] _initWithPageContext:[self executionContext] andFile:path] autorelease];
}

- (NSNumber *)append:(id)args
{
  ENSURE_TYPE(args, NSArray);
  id arg = [args objectAtIndex:0];

  if ([arg isKindOfClass:[TiFile class]]) {
    //allow the ability to append files to another file
    //e.g. file.append(Ti.Filesystem.getFile('somewhere'));

    TiFile *file_arg = (TiFile *)arg;
    NSError *err = nil;
    NSString *contents = [NSString stringWithContentsOfFile:[file_arg path] encoding:NSUTF8StringEncoding error:&err];
    if (contents != nil && err == nil) {
      arg = contents;
    } else {
      NSLog(@"[ERROR] Can't open file (%@) for reading!\n%@", [file_arg path], err);
      return NUMBOOL(NO);
    }
  }

  if ([arg isKindOfClass:[TiBlob class]] ||
      [arg isKindOfClass:[NSString class]]) {

    NSData *data = nil;
    if ([arg isKindOfClass:[NSString class]]) {
      data = [arg dataUsingEncoding:NSUTF8StringEncoding];
    } else {
      data = [(TiBlob *)arg data];
    }

    if (data == nil) {
      return NUMBOOL(NO);
    }

    if (![fm fileExistsAtPath:path]) {
      //create the file if it doesn't exist already
      NSError *writeError = nil;
      [data writeToFile:path options:NSDataWritingFileProtectionComplete | NSDataWritingAtomic error:&writeError];
      if (writeError != nil) {
        NSLog(@"[ERROR] Could not write data to file at path \"%@\"", path);
      }
      return NUMBOOL(writeError == nil);
    }

    NSFileHandle *handle = [NSFileHandle fileHandleForUpdatingAtPath:path];

    unsigned long long offset = [handle seekToEndOfFile];
    [handle writeData:data];

    BOOL success = ([handle offsetInFile] - offset) == [data length];
    [handle closeFile];

    return NUMBOOL(success);
  } else {
    NSLog(@"[ERROR] Can only append blobs and strings");
  }
  return NUMBOOL(NO);
}

- (NSNumber *)write:(id)args
{
  ENSURE_TYPE(args, NSArray);
  id arg = [args objectAtIndex:0];

  //Short-circuit against non-supported types
  if (!([arg isKindOfClass:[TiFile class]] || [arg isKindOfClass:[TiBlob class]]
          || [arg isKindOfClass:[NSString class]])) {
    return NUMBOOL(NO);
  }

  if ([args count] > 1) {
    ENSURE_TYPE([args objectAtIndex:1], NSNumber);

    //We have a second argument, is it truthy?
    //If yes, we'll hand the args to -append:
    NSNumber *append = [args objectAtIndex:1];
    if ([append boolValue] == YES) {
      return [self append:[args subarrayWithRange:NSMakeRange(0, 1)]];
    }
  }
  if ([arg isKindOfClass:[TiBlob class]]) {
    TiBlob *blob = (TiBlob *)arg;
    return NUMBOOL([blob writeTo:path error:nil]);
  } else if ([arg isKindOfClass:[TiFile class]]) {
    TiFile *file = (TiFile *)arg;
    [[NSFileManager defaultManager] removeItemAtPath:path error:nil];
    NSError *error = nil;
    [[NSFileManager defaultManager] copyItemAtPath:[file path] toPath:path error:&error];
    if (error != nil) {
      NSLog(@"[ERROR] error writing file: %@ to: %@. Error: %@", [file path], path, error);
    }
    return NUMBOOL(error == nil);
  }
  NSString *dataString = [TiUtils stringValue:arg];
  NSData *data = [dataString dataUsingEncoding:NSUTF8StringEncoding];
  NSError *err = nil;
  [data writeToFile:path options:NSDataWritingFileProtectionComplete | NSDataWritingAtomic error:&err];
  if (err != nil) {
    NSLog(@"[ERROR] Could not write data to file at path \"%@\" - details: %@", path, err);
  }
  return NUMBOOL(err == nil);
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

- (NSString *)name
{
  return [path lastPathComponent];
}

- (NSString *)resolve:(id)args
{
  return path;
}

- (NSString *)description
{
  return path;
}

+ (TiFilesystemFileProxy *)makeTemp:(BOOL)isDirectory
{
  NSString *tempDir = NSTemporaryDirectory();
  NSError *error = nil;

  NSFileManager *fm = [NSFileManager defaultManager];
  if (![fm fileExistsAtPath:tempDir]) {
    [fm createDirectoryAtPath:tempDir withIntermediateDirectories:YES attributes:nil error:&error];
    if (error != nil) {
      //TODO: ?
      return nil;
    }
  }

  int timestamp = (int)(time(NULL) & 0xFFFFL);
  NSString *resultPath;
  do {
    resultPath = [tempDir stringByAppendingPathComponent:[NSString stringWithFormat:@"%X", timestamp]];
    timestamp++;
  } while ([fm fileExistsAtPath:resultPath]);

  if (isDirectory) {
    [fm createDirectoryAtPath:resultPath withIntermediateDirectories:NO attributes:nil error:&error];
  } else {
    [[NSData data] writeToFile:resultPath options:NSDataWritingFileProtectionComplete | NSDataWritingAtomic error:&error];
  }

  if (error != nil) {
    //TODO: ?
    return nil;
  }

  return [[[TiFilesystemFileProxy alloc] initWithFile:resultPath] autorelease];
}

- (NSNumber *)remoteBackup
{
  NSURL *URL = [NSURL fileURLWithPath:[self path]];
  NSError *error;
  NSNumber *isExcluded;

  BOOL success = [URL getResourceValue:&isExcluded
                                forKey:NSURLIsExcludedFromBackupKey
                                 error:&error];
  if (!success) {
    // Doesn't matter what error is set to; this means that we're backing up.
    return NUMBOOL(YES);
  }

  // A value of @FALSE means backup, so:
  return NUMBOOL([isExcluded isEqualToNumber:@YES] ? NO : YES);
}

- (void)setRemoteBackup:(id)value
{
  ENSURE_TYPE(value, NSNumber);
  BOOL isExcluded = ![TiUtils boolValue:value def:YES];

  [self addSkipBackupAttributeToFolder:[NSURL fileURLWithPath:[self path]] withFlag:isExcluded];
}

- (void)addSkipBackupAttributeToFolder:(NSURL *)folder withFlag:(BOOL)flag
{
  [self addSkipBackupAttributeToItemAtURL:folder withFlag:flag];

  NSError *error = nil;
  NSArray *folderContent = [[NSFileManager defaultManager] contentsOfDirectoryAtPath:[folder path] error:&error];

  for (NSString *item in folderContent) {
    [self addSkipBackupAttributeToFolder:[NSURL fileURLWithPath:[folder.path stringByAppendingPathComponent:item]] withFlag:flag];
  }
}

- (BOOL)addSkipBackupAttributeToItemAtURL:(NSURL *)URL withFlag:(BOOL)flag
{
  NSError *error = nil;
  BOOL success = [URL setResourceValue:[NSNumber numberWithBool:flag]
                                forKey:NSURLIsExcludedFromBackupKey
                                 error:&error];

  if (!success) {
    NSLog(@"[ERROR] Remote-backup status of %@ could not be changed: %@", [URL lastPathComponent], [error localizedDescription]);
  }

  return success;
}

@end

#endif
