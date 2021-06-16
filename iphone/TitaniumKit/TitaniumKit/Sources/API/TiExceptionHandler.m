/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2019 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#import "TiExceptionHandler.h"
#import "APSAnalytics.h"
#import "KrollContext.h"
#import "TiApp.h"
#import "TiBase.h"

#include <JavaScriptCore/JavaScriptCore.h>
#include <execinfo.h>
#include <signal.h>

static void TiUncaughtExceptionHandler(NSException *exception);
static void TiSignalHandler(int signal);

static NSUncaughtExceptionHandler *prevUncaughtExceptionHandler = NULL;

@implementation TiExceptionHandler

@synthesize delegate = _delegate;

+ (TiExceptionHandler *)defaultExceptionHandler
{
  static TiExceptionHandler *defaultExceptionHandler;
  static dispatch_once_t onceToken;
  dispatch_once(&onceToken, ^{
    defaultExceptionHandler = [[self alloc] init];
    prevUncaughtExceptionHandler = NSGetUncaughtExceptionHandler();
    NSSetUncaughtExceptionHandler(&TiUncaughtExceptionHandler);

    signal(SIGABRT, TiSignalHandler);
    signal(SIGILL, TiSignalHandler);
    signal(SIGSEGV, TiSignalHandler);
    signal(SIGFPE, TiSignalHandler);
    signal(SIGBUS, TiSignalHandler);
    signal(SIGPIPE, TiSignalHandler);
  });
  return defaultExceptionHandler;
}

- (void)reportException:(NSException *)exception
{
  // attempt to generate a script error, which includes JS stack information
  JSContext *context = JSContext.currentContext;
  JSValue *jsError = [JSValue valueWithNewErrorFromMessage:[exception reason] inContext:context];
  @try {
    TiScriptError *error = [TiUtils scriptErrorValue:@{
      @"message" : [exception reason],
      @"sourceURL" : [[jsError valueForProperty:@"sourceURL"] toString],
      @"line" : [[jsError valueForProperty:@"line"] toNumber],
      @"column" : [[jsError valueForProperty:@"column"] toNumber],
      @"stack" : [[jsError valueForProperty:@"stack"] toString],
      @"nativeStack" : [exception callStackSymbols]
    }];
    [self reportScriptError:error];

    // cant generate script error, fallback to default behaviour
  } @catch (NSException *e) {
    id<TiExceptionHandlerDelegate> currentDelegate = _delegate;
    if (currentDelegate == nil) {
      currentDelegate = self;
    }
    [currentDelegate handleUncaughtException:exception];
    return;
  }
  [prevUncaughtExceptionHandler handleUncaughtException:exception];
}

- (void)reportScriptError:(TiScriptError *)scriptError
{
  DebugLog(@"[ERROR] %@", scriptError);

  id<TiExceptionHandlerDelegate> currentDelegate = _delegate;
  if (currentDelegate == nil) {
    currentDelegate = self;
  }
  [currentDelegate handleScriptError:scriptError];
}

- (void)reportScriptError:(JSValueRef)errorRef inKrollContext:(KrollContext *)krollContext
{
  [self reportScriptError:[TiUtils scriptErrorFromValueRef:errorRef inContext:krollContext.context]];
}

- (void)reportScriptError:(JSValue *)error inJSContext:(JSContext *)context
{
  [self reportScriptError:[TiUtils scriptErrorFromValueRef:error.JSValueRef inContext:context.JSGlobalContextRef]];
}

- (void)showScriptError:(TiScriptError *)error
{
  NSMutableDictionary *errorDict = [error.dictionaryValue mutableCopy];
  [errorDict setObject:[NSNumber numberWithLong:error.column] forKey:@"column"];
  [errorDict setObject:[NSNumber numberWithLong:error.lineNo] forKey:@"line"];
  NSString *stackTrace = [error.formattedNativeStack componentsJoinedByString:@"\n"];
  [errorDict setObject:stackTrace forKey:@"nativeStack"];
  [[TiApp app] showModalError:[error description]];
  [[NSNotificationCenter defaultCenter] postNotificationName:kTiErrorNotification object:self userInfo:errorDict];
}

#pragma mark - TiExceptionHandlerDelegate

- (void)handleUncaughtException:(NSException *)exception
{
  [[TiApp app] showModalError:[NSString stringWithFormat:@"%@\n    %@", exception.reason, [[exception callStackSymbols] componentsJoinedByString:@"\n    "]]];
}

- (void)handleScriptError:(TiScriptError *)error
{
  [self showScriptError:error];
}

@end

@implementation TiScriptError

@synthesize message = _message;
@synthesize sourceURL = _sourceURL;
@synthesize sourceLine = _sourceLine;
@synthesize lineNo = _lineNo;
@synthesize column = _column;
@synthesize dictionaryValue = _dictionaryValue;
@synthesize backtrace = _backtrace;
@synthesize nativeStack = _nativeStack;
@synthesize formattedNativeStack = _formattedNativeStack;

- (id)initWithMessage:(NSString *)message sourceURL:(NSString *)sourceURL lineNo:(NSInteger)lineNo
{
  self = [super init];
  if (self) {
    _message = [message copy];
    _sourceURL = [sourceURL copy];
    _lineNo = lineNo;
  }
  return self;
}

- (id)initWithDictionary:(NSDictionary *)dictionary
{
  NSString *message = [[dictionary objectForKey:@"message"] description];
  NSString *sourceURL = [[dictionary objectForKey:@"sourceURL"] description];
  NSInteger lineNo = [[dictionary objectForKey:@"line"] integerValue];

  self = [self initWithMessage:message sourceURL:sourceURL lineNo:lineNo];
  if (self) {
    _column = [[dictionary objectForKey:@"column"] integerValue];
    _backtrace = [[[dictionary objectForKey:@"backtrace"] description] copy];
    if (_backtrace == nil) {
      _backtrace = [[[dictionary objectForKey:@"stack"] description] copy];
    }
    _dictionaryValue = [dictionary copy];

    id nativeStackObject = [dictionary objectForKey:@"nativeStack"];
    if (nativeStackObject) {
      if ([nativeStackObject isKindOfClass:[NSArray class]]) {
        nativeStackObject = [nativeStackObject copy];
      } else if ([nativeStackObject isKindOfClass:[NSString class]]) {
        nativeStackObject = [[nativeStackObject componentsSeparatedByString:@"\n"] retain];
      } else {
        nativeStackObject = nil;
      }
    }
    _nativeStack = nativeStackObject;
  }
  return self;
}

- (void)dealloc
{
  RELEASE_TO_NIL(_message);
  RELEASE_TO_NIL(_sourceURL);
  RELEASE_TO_NIL(_sourceLine);
  RELEASE_TO_NIL(_backtrace);
  RELEASE_TO_NIL(_dictionaryValue);
  RELEASE_TO_NIL(_nativeStack);
  RELEASE_TO_NIL(_formattedNativeStack);
  [super dealloc];
}

- (NSString *)description
{
  NSMutableString *message = [[NSMutableString new] autorelease];
  NSString *encodedBundlePath = [NSString stringWithFormat:@"file://%@", [[NSBundle mainBundle].bundlePath stringByReplacingOccurrencesOfString:@" " withString:@"%20"]];

  if (self.sourceURL) {
    [message appendFormat:@"%@:%ld\n", [self.sourceURL stringByReplacingOccurrencesOfString:encodedBundlePath withString:@""], (long)self.lineNo];
    [message appendFormat:@"%@\n", self.sourceLine];
    NSString *columnIndicatorPadding = [@"" stringByPaddingToLength:self.column withString:@" " startingAtIndex:0];
    [message appendFormat:@"%@^\n", columnIndicatorPadding];
  }

  NSString *type = self.dictionaryValue[@"type"] != nil ? self.dictionaryValue[@"type"] : @"Error";
  [message appendFormat:@"%@: %@", type, self.message];

  NSString *jsStack = [self.backtrace stringByReplacingOccurrencesOfString:encodedBundlePath withString:@""];
  NSArray *jsStackLines = [jsStack componentsSeparatedByCharactersInSet:NSCharacterSet.newlineCharacterSet];
  NSMutableString *formattedJsStack = [[NSMutableString new] autorelease];
  for (NSString *line in jsStackLines) {
    NSRange atSymbolRange = [line rangeOfString:@"@"];
    NSInteger atSymbolIndex = atSymbolRange.location == NSNotFound ? -1 : atSymbolRange.location;
    NSString *source = [line substringFromIndex:atSymbolIndex + 1];
    NSString *symbolName = @"Object.<anonymous>";
    if (atSymbolIndex != -1) {
      symbolName = [line substringWithRange:NSMakeRange(0, atSymbolIndex)];
    }
    // global code is our module wrapper code which can be ignored
    if ([symbolName isEqualToString:@"global code"]) {
      continue;
    }
    [formattedJsStack appendFormat:@"\n    at %@ (%@)", symbolName, source];
  }
  [message appendString:formattedJsStack];

  [message appendFormat:@"\n\n    %@", [self.formattedNativeStack componentsJoinedByString:@"\n    "]];

  return message;
}

- (NSString *)detailedDescription
{
  return _dictionaryValue != nil ? [_dictionaryValue description] : [self description];
}

- (NSArray<NSString *> *)formattedNativeStack
{
  if (_formattedNativeStack != nil) {
    return _formattedNativeStack;
  }

  NSArray<NSString *> *stackTrace = self.nativeStack;
  NSInteger startIndex = 0;
  if (stackTrace == nil) {
    stackTrace = [NSThread callStackSymbols];
    // starting at index = 2 to not include this method and callee
    startIndex = 2;
  }
  NSMutableArray<NSString *> *formattedStackTrace = [[[NSMutableArray alloc] init] autorelease];
  NSUInteger stackTraceLength = MIN([stackTrace count], 20 + startIndex);
  // re-size stack trace and format results.
  for (NSInteger i = startIndex; i < stackTraceLength; i++) {
    NSString *line = [self removeWhitespace:stackTrace[i]];
    // remove stack index
    line = [line substringFromIndex:[line rangeOfString:@" "].location + 1];
    [formattedStackTrace addObject:line];
  }
  _formattedNativeStack = [formattedStackTrace copy];

  return _formattedNativeStack;
}

- (NSString *)sourceLine
{
  if (_sourceURL == nil) {
    return nil;
  }

  if (_sourceLine != nil) {
    return _sourceLine;
  }

  NSURL *sourceURL = [NSURL URLWithString:self.sourceURL];
  NSData *data = [TiUtils loadAppResource:sourceURL];
  NSString *source = nil;
  if (data == nil) {
    source = [NSString stringWithContentsOfFile:[sourceURL path] encoding:NSUTF8StringEncoding error:NULL];
  } else {
    source = [[[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding] autorelease];
  }
  NSArray<NSString *> *lines = [source componentsSeparatedByCharactersInSet:[NSCharacterSet newlineCharacterSet]];
  _sourceLine = [[lines objectAtIndex:self.lineNo - 1] retain];

  return _sourceLine;
}

- (NSString *)removeWhitespace:(NSString *)line
{
  while ([line rangeOfString:@"  "].length > 0) {
    line = [line stringByReplacingOccurrencesOfString:@"  " withString:@" "];
  }
  return line;
}

@end

//
// thanks to: http://www.restoroot.com/Blog/2008/10/18/crash-reporter-for-iphone-applications/
//
static BOOL uncaughtException = NO;
static void TiUncaughtExceptionHandler(NSException *exception)
{
  static BOOL insideException = NO;

  // prevent signal handler repeating exception
  uncaughtException = YES;

  // prevent recursive exceptions
  if (insideException) {
    exit(1);
    return;
  }
  insideException = YES;

  [[TiExceptionHandler defaultExceptionHandler] reportException:exception];

  insideException = NO;
  if (prevUncaughtExceptionHandler != NULL) {
    prevUncaughtExceptionHandler(exception);
  }

  // exceptions on seperate threads can cause the application to terminate
  // if we let the thread continue
  if (![NSThread isMainThread]) {
    [NSThread exit];
  }
}

static void TiSignalHandler(int code)
{
  // already caught exception, no need for signal exception
  if (uncaughtException) {
    signal(code, SIG_DFL);
    return;
  }
  NSException *exception = [NSException exceptionWithName:@"SIGNAL_ERROR" reason:[NSString stringWithFormat:@"signal error code: %d", code] userInfo:nil];
  [[TiExceptionHandler defaultExceptionHandler] reportException:exception];
  signal(code, SIG_DFL);
}
