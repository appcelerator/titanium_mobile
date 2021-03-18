/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_FILESYSTEM

#import "FilesystemModule.h"
#import "TiFilesystemBlobProxy.h"
#import <CommonCrypto/CommonDigest.h>
@import TitaniumKit.TiBlob;
@import TitaniumKit.TiUtils;
@import TitaniumKit.TiFilesystemFileProxy;
@import TitaniumKit.TiFilesystemFileStreamProxy;
@import TitaniumKit.TiHost;

@implementation FilesystemModule

// internal
- (id)resolveFile:(id)arg
{
  if ([arg isKindOfClass:[TiFilesystemFileProxy class]]) {
    return [(TiFilesystemFileProxy *)arg path];
  }
  return [TiUtils stringValue:arg];
}

- (NSString *)apiName
{
  return @"Ti.Filesystem";
}

- (NSString *)pathFromComponents:(NSArray *)args
{
  NSString *newpath;
  NSString *first = [[args objectAtIndex:0] toString];
  if ([first hasPrefix:@"file://"]) {
    NSURL *fileUrl = [NSURL URLWithString:first];
    //Why not just crop? Because the url may have some things escaped that need to be unescaped.
    newpath = [fileUrl path];
  } else if ([first characterAtIndex:0] != '/') {
    NSURL *url = [NSURL URLWithString:[self resourcesDirectory]];
    newpath = [[url path] stringByAppendingPathComponent:[self resolveFile:first]];
  } else {
    newpath = [self resolveFile:first];
  }

  if ([args count] > 1) {
    for (int c = 1; c < [args count]; c++) {
      newpath = [newpath stringByAppendingPathComponent:[self resolveFile:[[args objectAtIndex:c] toString]]];
    }
  }
  return [newpath stringByStandardizingPath];
}

- (JSValue *)createTempFile
{
  return [self NativeToJSValue:[TiFilesystemFileProxy makeTemp:NO]];
}

- (JSValue *)createTempDirectory
{
  return [self NativeToJSValue:[TiFilesystemFileProxy makeTemp:YES]];
}

- (JSValue *)openStream:(TiStreamMode)mode
{
  ENSURE_VALUE_RANGE(mode, TI_READ, TI_APPEND);

  NSArray *args = [JSContext currentArguments];
  if ([args count] < 2) {
    [self throwException:TiExceptionNotEnoughArguments
               subreason:nil
                location:CODELOCATION];
  }

  // allow variadic file components to be passed
  NSString *resolvedPath = [self pathFromComponents:[args subarrayWithRange:NSMakeRange(1, [args count] - 1)]];
  @try {
    // Let's re-use code! we're effectively calling: getFile(path).open(mode);
    TiFile *fileProxy = [self getFileProxy:resolvedPath];
    if (fileProxy != nil) {
      NSArray *payload = @[ [NSNumber numberWithInt:mode] ];
      TiStreamProxy *streamProxy;
      if ([fileProxy isKindOfClass:[TiFilesystemFileStreamProxy class]]) {
        streamProxy = [(TiFilesystemFileProxy *)fileProxy open:payload];
      } else {
        streamProxy = [(TiFilesystemBlobProxy *)fileProxy open:payload];
      }
      if (streamProxy != nil) {
        return [self NativeToJSValue:streamProxy];
      }
    }
  } @catch (NSException *exception) {
    JSValue *jsException = [self NativeToJSValue:exception];
    [[JSContext currentContext] setException:jsException];
  }
  return nil;
}

- (TiStreamMode)MODE_APPEND
{
  return TI_APPEND;
}

- (TiStreamMode)MODE_READ
{
  return TI_READ;
}

- (TiStreamMode)MODE_WRITE
{
  return TI_WRITE;
}

- (bool)isExternalStoragePresent
{
  //IOS treats the camera connection kit as just that, and does not allow
  //R/W access to it, which is just as well as it'd mess up cameras.
  return NO;
}

#define fileURLify(foo) [[NSURL fileURLWithPath:foo isDirectory:YES] path]

- (NSString *)resourcesDirectory
{
  return [NSString stringWithFormat:@"%@/", fileURLify([TiHost resourcePath])];
}
GETTER_IMPL(NSString *, resourcesDirectory, ResourcesDirectory);

- (NSString *)applicationDirectory
{
  return [NSString stringWithFormat:@"%@/", fileURLify([NSSearchPathForDirectoriesInDomains(NSApplicationDirectory, NSUserDomainMask, YES) objectAtIndex:0])];
}
GETTER_IMPL(NSString *, applicationDirectory, ApplicationDirectory);

- (NSString *)applicationSupportDirectory
{
  return [NSString stringWithFormat:@"%@/", fileURLify([NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, YES) objectAtIndex:0])];
}
GETTER_IMPL(NSString *, applicationSupportDirectory, ApplicationSupportDirectory);

- (NSString *)applicationDataDirectory
{
#if TARGET_OS_MACCATALYST
  NSString *home = NSHomeDirectory();
  return [NSString stringWithFormat:@"%@/Documents/", fileURLify(home)];
#else
  // TODO: Unify these. Appending /Documents to the home directory appears to give the same path as below code for ios sim (probably also device)
  return [NSString stringWithFormat:@"%@/", fileURLify([NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES) objectAtIndex:0])];
#endif
}
GETTER_IMPL(NSString *, applicationDataDirectory, ApplicationDataDirectory);

- (NSString *)applicationCacheDirectory
{
  return [NSString stringWithFormat:@"%@/", fileURLify([NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES) objectAtIndex:0])];
}
GETTER_IMPL(NSString *, applicationCacheDirectory, ApplicationCacheDirectory);

- (NSString *)tempDirectory
{
  return [NSString stringWithFormat:@"%@/", fileURLify(NSTemporaryDirectory())];
}
GETTER_IMPL(NSString *, tempDirectory, TempDirectory);

- (NSString *)directoryForSuite:(NSString *)suiteName
{
  NSURL *groupURL = [[NSFileManager defaultManager] containerURLForSecurityApplicationGroupIdentifier:suiteName];
  if (!groupURL) {
    NSLog(@"[ERROR] Directory not found for suite: %@ check the com.apple.security.application-groups entitlement.", suiteName);
    return nil;
  }
  return [NSString stringWithFormat:@"%@/", fileURLify([groupURL path])];
}

- (NSString *)separator
{
  return @"/";
}
GETTER_IMPL(NSString *, separator, Separator);

- (NSString *)lineEnding
{
  return @"\n";
}
GETTER_IMPL(NSString *, lineEnding, LineEnding);

- (JSValue *)getFile
{
  NSArray *args = JSContext.currentArguments;
  NSString *newpath = [self pathFromComponents:args];
  TiFile *fileProxy = [self getFileProxy:newpath];
  return [self NativeToJSValue:fileProxy];
}

- (TiFile *)getFileProxy:(NSString *)path
{
  if ([path hasSuffix:@".js"] || [path hasSuffix:@".json"] || [path hasSuffix:@".cjs"]) { // FIXME: Handle mjs?
    NSString *resourcesDir = [self resourcesDirectory];
    if ([path hasPrefix:resourcesDir] || [path hasPrefix:[resourcesDir stringByStandardizingPath]]) {
      NSURL *url = [NSURL fileURLWithPath:path];
      NSData *data = [TiUtils loadAppResource:url];
      if (data != nil) {
        return [[[TiFilesystemBlobProxy alloc] initWithURL:url data:data] autorelease];
      }
    }
  }

  return [[[TiFilesystemFileProxy alloc] initWithFile:path] autorelease];
}

- (TiBlob *)getAsset
{
  NSArray *args = JSContext.currentArguments;
  NSString *newpath = [self pathFromComponents:args];
  if ([newpath hasSuffix:@".jpg"] || [newpath hasSuffix:@".png"]) {
    NSString *resourcesDir = [self resourcesDirectory];
    if ([newpath hasPrefix:resourcesDir] || [newpath hasPrefix:[resourcesDir stringByStandardizingPath]]) {
      NSRange range = [newpath rangeOfString:@".app"];
      if (range.location != NSNotFound) {
        NSString *imageArg = nil;
        if ([TiUtils isMacOS]) {
          imageArg = [newpath substringFromIndex:range.location + 24]; //Contents/Resources/ for mac
        } else {
          imageArg = [newpath substringFromIndex:range.location + 5];
        }
        //remove suffixes.
        imageArg = [imageArg stringByReplacingOccurrencesOfString:@"@3x" withString:@""];
        imageArg = [imageArg stringByReplacingOccurrencesOfString:@"@2x" withString:@""];
        imageArg = [imageArg stringByReplacingOccurrencesOfString:@"~iphone" withString:@""];
        imageArg = [imageArg stringByReplacingOccurrencesOfString:@"~ipad" withString:@""];

        UIImage *image = [UIImage imageNamed:imageArg];

        return [[[TiBlob alloc] initWithImage:image] autorelease];
      }
    }
  }
  return nil;
}

- (NSString *)IOS_FILE_PROTECTION_NONE
{
  return NSFileProtectionNone;
}

- (NSString *)IOS_FILE_PROTECTION_COMPLETE
{
  return NSFileProtectionComplete;
}

- (NSString *)IOS_FILE_PROTECTION_COMPLETE_UNLESS_OPEN
{
  return NSFileProtectionCompleteUnlessOpen;
}

- (NSString *)IOS_FILE_PROTECTION_COMPLETE_UNTIL_FIRST_USER_AUTHENTICATION
{
  return NSFileProtectionCompleteUntilFirstUserAuthentication;
}

@end

#endif
