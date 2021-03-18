/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2020-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#import "KrollModule.h"
#import "TiBindingTiValue.h"
#import "TiEvaluator.h"
#import "TiHost.h"
#import "TiModule.h"

@implementation KrollModule

- (NSString *)apiName
{
  return @"Kroll";
}

- (instancetype)init
{
  self = [super init];
  if (self) {
    _coreModules = [[NSSet setWithArray:@[ @"Accelerometer", @"Analytics", @"App", @"API", @"Calendar", @"Codec", @"Contacts", @"Database", @"Filesystem", @"Geolocation", @"Gesture", @"Locale", @"Media", @"Network", @"Platform", @"Stream", @"Utils", @"UI",
      @"WatchSession",
      @"XML" ]] retain];
  }
  return self;
}

- (void)_destroy
{
  RELEASE_TO_NIL(_coreModules);
  [super _destroy];
}

- (BOOL)isExternalCommonJsModule:(NSString *)moduleID
{
  id<Module> module = [KrollModule loadCoreModule:moduleID inContext:JSContext.currentContext];
  return [module isJSModule];
}

- (NSString *)getExternalCommonJsModule:(NSString *)moduleID
{
  id<Module> module = [KrollModule loadCoreModule:moduleID inContext:JSContext.currentContext];
  NSData *data = [module moduleJS];
  if (data == nil) {
    // Uh oh, no actual data. Let's just punt and return the native module as-is
    return nil;
  }

  return [[[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding] autorelease];
}

- (JSValue *)binding:(NSString *)moduleID
{
  JSContext *context = JSContext.currentContext;
  id<Module> module = [KrollModule loadCoreModule:moduleID inContext:context];
  if (module == nil) {
    return [JSValue valueWithUndefinedInContext:context];
  }
  // For native modules outside the core, we need to set the name!
  // This affects how createWhatever() style proxy factory method are handled
  // if the name is set, then we do <name-minus-Module>Proxy: TiMapModule + "Map.createAnnotation" -> "TiMapAnnotationProxy"
  // if not set, we do Ti<name-minus-Module>Proxy: UIModule + "UI.createWindow" -> "TiUIWindowProxy"
  // See [TiModule createProxy:]
  if (![_coreModules containsObject:moduleID]) {
    NSString *moduleClassName = [KrollModule pathToModuleClassName:moduleID];
    [module _setName:moduleClassName];
  }

  // If there is a JS file that collides with the given path,
  // warn the user of the collision, but prefer the native/core module
  NSURL *jsPath = [NSURL URLWithString:[NSString stringWithFormat:@"%@/%@.js", [[NSURL fileURLWithPath:[TiHost resourcePath] isDirectory:YES] path], moduleID]];
  if ([[NSFileManager defaultManager] fileExistsAtPath:[jsPath absoluteString]]) {
    NSLog(@"[WARN] The requested path '%@' has a collison between a native Ti%@um API/module and a JS file.", moduleID, @"tani");
    NSLog(@"[WARN] The native Ti%@um API/module will be loaded in preference.", @"tani");
    NSLog(@"[WARN] If you intended to address the JS file, please require the path using a prefixed string such as require('./%@') or require('/%@') instead.", moduleID, moduleID);
  }

  // FIXME: Extract a Proxy protocol for ObjcProxy/TiProxy and place methods like boundBridge:withKrollObject: on it!
  if ([(id)module isKindOfClass:[ObjcProxy class]]) {
    ObjcProxy *proxy = (ObjcProxy *)module;
    return [proxy JSValueInContext:context];
  }
  KrollObject *ko = [[self executionContext] registerProxy:module]; // This basically retains the module for the lifetime of the bridge
  return [JSValue valueWithJSValueRef:[ko jsobject] inContext:context];
}

+ (id<Module>)loadCoreModule:(NSString *)moduleID inContext:(JSContext *)jsContext
{
  NSString *moduleClassName = [KrollModule pathToModuleClassName:moduleID];
  id<TiEvaluator> eval = [ObjcProxy executionContext:jsContext];
  // Let TiHost handle caching/re-creating the module
  return [eval.host moduleNamed:moduleClassName context:eval];
}

+ (NSString *)pathToModuleClassName:(NSString *)path
{
  // TODO: switch to use ApplicationMods

  NSArray *tokens = [path componentsSeparatedByString:@"."];
  NSMutableString *modulename = [NSMutableString string];
  for (NSString *token in tokens) {
    [modulename appendFormat:@"%@%@", [[token substringToIndex:1] uppercaseString], [token substringFromIndex:1]];
  }
  [modulename appendString:@"Module"];
  return modulename;
}

@end
