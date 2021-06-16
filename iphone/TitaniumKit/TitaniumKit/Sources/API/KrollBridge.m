/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2020 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#import "KrollBridge.h"
#import "APSAnalytics.h"
#import "AssetsModule.h"
#import "JSValue+Addons.h"
#import "KrollCallback.h"
#import "KrollModule.h"
#import "TiApp.h"
#import "TiExceptionHandler.h"
#import "TiHost.h"
#import "TiSharedConfig.h"
#import "TiUtils.h"
#import "TopTiModule.h"

#ifndef TI_USE_NATIVE
#define TI_USE_NATIVE 0
#endif

NSString *TitaniumModuleRequireFormat = @"(function(exports){"
                                         "var __OXP=exports;var module={'exports':exports,'paths':[]};var __dirname=\"%@\";var __filename=\"%@\";%@;\n"
                                         "if(module.exports !== __OXP){return module.exports;}"
                                         "return exports;})({})";

//Defined private method inside TiBindingRunLoop.m (Perhaps to move to .c?)
void TiBindingRunLoopAnnounceStart(TiBindingRunLoop runLoop);

os_unfair_lock krollBridgeRegistryLock = OS_UNFAIR_LOCK_INIT;
CFMutableSetRef krollBridgeRegistry = nil;

@implementation KrollBridge

- (NSDictionary<NSString *, id> *)modules
{
  // This is for backwards compatibility with native modules relying on a modules property that
  // cached module ids -> module instances
  // Note that this one will only return external native modules and relies on their moduleId
  // method returning the correct valiue
  NSDictionary<NSString *, id> *orig = [self.host valueForKey:@"modules"];
  NSMutableDictionary<NSString *, id> *copy = [orig mutableCopy];
  for (NSString *key in orig) {
    id module = [orig objectForKey:key];
    if ([module respondsToSelector:@selector(moduleId)]) {
      NSString *moduleId = [module moduleId];
      if (moduleId) {
        copy[moduleId] = module;
      }
    }
  }
  return copy;
}

+ (void)initialize
{
  if (krollBridgeRegistry == nil) {
    CFSetCallBacks doNotRetain = kCFTypeSetCallBacks;
    doNotRetain.retain = NULL;
    doNotRetain.release = NULL;
    krollBridgeRegistry = CFSetCreateMutable(NULL, 3, &doNotRetain);
  }
}
@synthesize currentURL;

- (void)registerForMemoryWarning
{
  WARN_IF_BACKGROUND_THREAD_OBJ; //NSNotificationCenter is not threadsafe!
  [[NSNotificationCenter defaultCenter] addObserver:self
                                           selector:@selector(didReceiveMemoryWarning:)
                                               name:UIApplicationDidReceiveMemoryWarningNotification
                                             object:nil];
}

- (void)unregisterForMemoryWarning
{
  WARN_IF_BACKGROUND_THREAD_OBJ; //NSNotificationCenter is not threadsafe!
  [[NSNotificationCenter defaultCenter] removeObserver:self name:UIApplicationDidReceiveMemoryWarningNotification object:nil];
}

- (id)init
{
  if (self = [super init]) {
#if KROLLBRIDGE_MEMORY_DEBUG == 1
    NSLog(@"[DEBUG] INIT: %@", self);
#endif

    proxyLock = OS_UNFAIR_LOCK_INIT;
    os_unfair_lock_lock(&krollBridgeRegistryLock);
    CFSetAddValue(krollBridgeRegistry, self);
    os_unfair_lock_unlock(&krollBridgeRegistryLock);
    TiThreadPerformOnMainThread(
        ^{
          [self registerForMemoryWarning];
        },
        NO);
  }
  return self;
}

- (void)didReceiveMemoryWarning:(NSNotification *)notification
{
  os_unfair_lock_lock(&proxyLock);
  if (registeredProxies == NULL) {
    os_unfair_lock_unlock(&proxyLock);
    [self gc];
    return;
  }

  BOOL keepWarning = YES;
  signed long proxiesCount = CFDictionaryGetCount(registeredProxies);
  os_unfair_lock_unlock(&proxyLock);

  // During a memory panic, we may not get the chance to copy proxies.
  while (keepWarning) {
    keepWarning = NO;

    for (id proxy in [(NSDictionary *)registeredProxies allKeys]) {
      [proxy didReceiveMemoryWarning:notification];

      os_unfair_lock_lock(&proxyLock);
      if (registeredProxies == NULL) {
        os_unfair_lock_unlock(&proxyLock);
        break;
      }

      signed long newCount = CFDictionaryGetCount(registeredProxies);
      os_unfair_lock_unlock(&proxyLock);

      if (newCount != proxiesCount) {
        proxiesCount = newCount;
        keepWarning = YES;
        break;
      }
    }
  }

  [self gc];
}

#if KROLLBRIDGE_MEMORY_DEBUG == 1
- (id)retain
{
  NSLog(@"[MEMORY DEBUG] RETAIN: %@ (%d)", self, [self retainCount] + 1);
  return [super retain];
}
- (oneway void)release
{
  NSLog(@"[MEMORY DEBUG] RELEASE: %@ (%d)", self, [self retainCount] - 1);
  [super release];
}
#endif

- (void)removeProxies
{
  os_unfair_lock_lock(&proxyLock);
  CFDictionaryRef oldProxies = registeredProxies;
  registeredProxies = NULL;
  os_unfair_lock_unlock(&proxyLock);

  for (id thisProxy in (NSDictionary *)oldProxies) {
    KrollObject *thisKrollObject = (id)CFDictionaryGetValue(oldProxies, thisProxy);
    [thisProxy contextShutdown:self];
    [thisKrollObject unprotectJsobject];
  }

  if (oldProxies != NULL) {
    CFRelease(oldProxies);
  }
}

- (void)dealloc
{
#if KROLLBRIDGE_MEMORY_DEBUG == 1
  NSLog(@"[MEMORY DEBUG] DEALLOC: %@", self);
#endif

  [self removeProxies];
  RELEASE_TO_NIL(preload);
  RELEASE_TO_NIL(context);
  os_unfair_lock_lock(&krollBridgeRegistryLock);
  CFSetRemoveValue(krollBridgeRegistry, self);
  os_unfair_lock_unlock(&krollBridgeRegistryLock);
  [super dealloc];
}

- (TiHost *)host
{
  return host;
}

- (KrollContext *)krollContext
{
  return context;
}

- (id)preloadForKey:(id)key name:(id)name
{
  if (preload != nil) {
    NSDictionary *dict = [preload objectForKey:name];
    if (dict != nil) {
      return [dict objectForKey:key];
    }
  }
  return nil;
}

- (void)boot:(id)callback url:(NSURL *)url_ preload:(NSDictionary *)preload_
{
  preload = [preload_ retain];
  [super boot:callback url:url_ preload:preload_];
  context = [[KrollContext alloc] init];
  context.delegate = self;
  [context start];
}

- (void)evalJSWithoutResult:(NSString *)code
{
  [context evalJS:code];
}

// NOTE: this must only be called on the JS thread or an exception will be raised
- (id)evalJSAndWait:(NSString *)code
{
  return [context evalJSAndWait:code];
}

- (BOOL)evaluationError
{
  return evaluationError;
}

- (void)setEvaluationError:(BOOL)value
{
  evaluationError = value;
}

- (void)evalFileOnThread:(NSString *)path context:(KrollContext *)context_
{
  NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];

  NSURL *url_ = [path hasPrefix:@"file:"] ? [NSURL URLWithString:path] : [NSURL fileURLWithPath:path];
  if (![path hasPrefix:@"/"] && ![path hasPrefix:@"file:"]) {
    url_ = [NSURL URLWithString:path relativeToURL:url];
  }

  NSString *jcode = [AssetsModule readURL:url_];
  if (jcode == nil) {
    NSLog(@"[ERROR] Error loading path: %@", path);
    evaluationError = YES;
    TiScriptError *scriptError = [[TiScriptError alloc] initWithMessage:[NSString stringWithFormat:@"Error loading script %@.", [path lastPathComponent]] sourceURL:nil lineNo:0];
    [TiExceptionHandler.defaultExceptionHandler reportScriptError:scriptError];
    [scriptError release];
    return;
  }

  // When we run a file as the entry point of the app or a service:
  // we bootstrap, then Module.runModule(source, filename, service)
  JSGlobalContextRef jsContext = context_.context;
  JSContext *objCContext = [JSContext contextWithJSGlobalContextRef:jsContext];
  JSValue *moduleGlobal = objCContext.globalObject[@"Module"];
  // make the path relative to the resources Dir!
  NSString *relativePath = [TiHost resourceRelativePath:url_];
  // FIXME: If the entry point is a service, we should pass that in here as last arg instead of null!
  [moduleGlobal invokeMethod:@"runModule" withArguments:@[ jcode, relativePath, [NSNull null] ]];

  if (objCContext.exception != nil) {
    evaluationError = YES;
    [TiExceptionHandler.defaultExceptionHandler reportScriptError:objCContext.exception inJSContext:objCContext];
  }

  [pool release];
}

- (void)evalFile:(NSString *)path callback:(id)callback selector:(SEL)selector
{
  [context invokeOnThread:self method:@selector(evalFileOnThread:context:) withObject:path callback:callback selector:selector];
}

- (void)evalFile:(NSString *)file
{
  [context invokeOnThread:self method:@selector(evalFileOnThread:context:) withObject:file condition:nil];
}

- (void)fireEvent:(id)listener withObject:(id)obj remove:(BOOL)yn thisObject:(TiProxy *)thisObject_
{
  if (![listener isKindOfClass:[KrollCallback class]]) {
    DebugLog(@"[ERROR] Listener callback is of a non-supported type: %@", [listener class]);
    return;
  }

  KrollEvent *event = [[KrollEvent alloc] initWithCallback:listener eventObject:obj thisObject:thisObject_];
  [context enqueue:event];
  [event release];
}

- (void)enqueueEvent:(NSString *)type forProxy:(TiProxy *)proxy withObject:(id)obj
{
  KrollObject *eventKrollObject = [self krollObjectForProxy:proxy];

  KrollEvent *newEvent = [[KrollEvent alloc]
        initWithType:type
      ForKrollObject:eventKrollObject
         eventObject:obj
          thisObject:eventKrollObject];

  [context enqueue:newEvent];
  [newEvent release];
}

- (void)shutdown:(NSCondition *)condition
{
#if KROLLBRIDGE_MEMORY_DEBUG == 1
  NSLog(@"[MEMORY DEBUG] DESTROY: %@", self);
#endif

  if (!shutdown) {
    shutdownCondition = [condition retain];
    shutdown = YES;
    // fire a notification event to our listeners
    WARN_IF_BACKGROUND_THREAD_OBJ; //NSNotificationCenter is not threadsafe!
    NSNotification *notification = [NSNotification notificationWithName:kTiContextShutdownNotification object:self];
    [[NSNotificationCenter defaultCenter] postNotification:notification];

    [context stop];
  } else {
    [condition lock];
    [condition signal];
    [condition unlock];
  }
}

- (void)gc
{
  [context gc];
}

#pragma mark Delegate

- (void)willStartNewContext:(KrollContext *)kroll
{
  if (TiUtils.isHyperloopAvailable) {
    Class cls = NSClassFromString(@"Hyperloop");
    [cls performSelector:@selector(willStartNewContext:bridge:) withObject:kroll withObject:self];
  }

  [self retain]; // Hold onto ourselves as long as the context needs us
}

- (void)didStartNewContext:(KrollContext *)kroll
{
  NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];

  JSGlobalContextRef jsContext = kroll.context;
  JSContext *objcJSContext = [JSContext contextWithJSGlobalContextRef:jsContext];
  JSValue *global = [objcJSContext globalObject];

  // TODO: Move to real paths for __dirname/__filename, but that affects Android and may break users/debugging?
  //  NSString *dirname = [[TiHost resourcePath] stringByStandardizingPath];
  // Set the __dirname and __filename for the app.js.
  // For other files, it will be injected via require
  [global defineReadOnlyProperty:@"__dirname" withValue:@"/"];
  //  NSString *filename = [dirname stringByAppendingString:@"/ti.kernel.js"];
  [global defineReadOnlyProperty:@"__filename" withValue:@"/ti.kernel.js"];

  // Load ti.kernel.js (kroll.js equivalent)
  NSURL *bootstrapURL = [TiHost resourceBasedURL:@"ti.kernel.js" baseURL:NULL];
  NSString *source = [AssetsModule readURL:bootstrapURL];
  if (source == nil || source.length == 0) {
    NSLog(@"[ERROR] Error reading ti.kernel.js, source is nil/empty. Most likely due to failure to decrypt via remote policy and device offline.");
    evaluationError = YES;
    // Don't put up a dialog, as the most likely cause here is remote policy encryption/decryption failing to decrypt due to being offline.
    // And if we have a dialog up, the security violation dialog won't appear
  } else {
    JSValue *bootstrapFunc = [objcJSContext evaluateScript:source withSourceURL:bootstrapURL];
    if (objcJSContext.exception != nil) {
      NSLog(@"[ERROR] Error eval'ing ti.kernel.js bootstrap script. Please contact Titanium developers and file a bug report.");
      evaluationError = YES;
      [TiExceptionHandler.defaultExceptionHandler reportScriptError:objcJSContext.exception inJSContext:objcJSContext];
    } else if (bootstrapFunc == nil || [bootstrapFunc isUndefined]) {
      // it didn't export a bootstrap function! Most likely reason is we hosed the file
      NSLog(@"[ERROR] Error eval'ing ti.kernel.js, bootstrap function is undefined/nil. Please contact Titanium developers and file a bug report.");
      evaluationError = YES;
      [TiExceptionHandler.defaultExceptionHandler reportScriptError:objcJSContext.exception inJSContext:objcJSContext];
    } else {
      // ti.kernel.js eval'd OK, so now let's run the exported bootstrap function
      [bootstrapFunc callWithArguments:@[ global, [[KrollModule alloc] init] ]];
      if (objcJSContext.exception != nil) {
        NSLog(@"[ERROR] Error calling ti.kernel.js' exported bootstrap function. Please contact Titanium developers and file a bug report.");
        evaluationError = YES;
        [TiExceptionHandler.defaultExceptionHandler reportScriptError:objcJSContext.exception inJSContext:objcJSContext];
      }
    }
  }

  JSValue *titanium = global[@"Ti"]; // This may be nil/undefined it we couldn't load ti.kernel.js or the bootstrapping failed
  if (TiSharedConfig.defaultConfig.isAnalyticsEnabled) {
    APSAnalytics *sharedAnalytics = APSAnalytics.sharedInstance;
    NSString *buildType = TiSharedConfig.defaultConfig.applicationBuildType;
    if (buildType != nil || buildType.length > 0) {
      [sharedAnalytics setBuildType:buildType];
    }
    TopTiModule *module;
    if (titanium != nil && ![titanium isUndefined]) {
      module = [titanium toObject];
    } else {
      // Uh-oh, something went really wrong! For analytics sake, let's just create the module
      module = [[TopTiModule alloc] init];
      global[@"Ti"] = module;
      global[@"Titanium"] = module;
    }
    [sharedAnalytics setSDKVersion:module.version];
    NSString *deployType = TiSharedConfig.defaultConfig.applicationDeployType;
    NSString *guid = TiSharedConfig.defaultConfig.applicationGUID;
    [sharedAnalytics enableWithAppKey:guid andDeployType:deployType];

    // Set analytics event cache size.
    id cacheSizeObj = [[TiApp tiAppProperties] objectForKey:@"ti.analytics.cacheSize"];
    if ([cacheSizeObj isKindOfClass:[NSNumber class]]) {
      int cacheSize = [cacheSizeObj intValue];
      if (cacheSize > -1) {
        [sharedAnalytics setCacheSize:cacheSize];
      }
    }
  }

  NSURL *startURL = nil;
  //if we have a preload dictionary, register those static key/values into our namespace
  if (preload != nil) {
    // Guard for top level Titanium object being unassigned. likley means we had issues
    // setting up ti.kernel.js, so we likely need to skip most everything here.
    if (titanium != nil && ![titanium isUndefined]) {
      for (NSString *name in preload) {
        JSValue *moduleJSObject = titanium[name];
        KrollObject *ti = (KrollObject *)JSObjectGetPrivate(JSValueToObject(jsContext, moduleJSObject.JSValueRef, NULL));
        NSDictionary *values = preload[name];
        for (id key in values) {
          id target = values[key];
          KrollObject *ko = [self krollObjectForProxy:target];
          if (ko == nil) {
            ko = [self registerProxy:target];
          }
          [ti noteKrollObject:ko forKey:key];
          [ti setStaticValue:ko forKey:key purgable:NO];
        }
      }
    }
    startURL = [url copy]; // should be the entry point of the background service js file
  } else {
    startURL = [host startURL]; // should be ti.main.js
  }

  // We need to run this before the entry js file, which means it has to be here.
  TiBindingRunLoopAnnounceStart(kroll);
  if (!evaluationError) {
    [self evalFile:[startURL absoluteString] callback:self selector:@selector(booted)];
  } else {
    NSLog(@"[ERROR] Error loading/executing ti.kernel.js bootstrap code, refusing to launch app main file.");
    // DO NOT POP AN ERROR DIALOG! The most likley scenario here is that the app is remotely encrypted
    // and the decryption failed because the device is offline
    // If we pop a dialog here, it will block the "Security Violation" error dialog that would show in that case
  }

  if (TiUtils.isHyperloopAvailable) {
    Class cls = NSClassFromString(@"Hyperloop");
    [cls performSelector:@selector(didStartNewContext:bridge:) withObject:kroll withObject:self];
  }

  [pool release];
}

- (void)willStopNewContext:(KrollContext *)kroll
{
  if (TiUtils.isHyperloopAvailable) {
    Class cls = NSClassFromString(@"Hyperloop");
    [cls performSelector:@selector(willStopNewContext:bridge:) withObject:kroll withObject:self];
  }

  if (!shutdown) {
    shutdown = YES;
    // fire a notification event to our listeners
    WARN_IF_BACKGROUND_THREAD_OBJ; //NSNotificationCenter is not threadsafe!
    NSNotification *notification = [NSNotification notificationWithName:kTiContextShutdownNotification object:self];
    [[NSNotificationCenter defaultCenter] postNotification:notification];
  }

  if (shutdownCondition) {
    [shutdownCondition lock];
    [shutdownCondition signal];
    [shutdownCondition unlock];
    RELEASE_TO_NIL(shutdownCondition);
  }
}

- (void)didStopNewContext:(KrollContext *)kroll
{
  TiThreadPerformOnMainThread(
      ^{
        [self unregisterForMemoryWarning];
      },
      NO);
  [self removeProxies];
  RELEASE_TO_NIL(context);
  RELEASE_TO_NIL(preload);

  if (TiUtils.isHyperloopAvailable) {
    Class cls = NSClassFromString(@"Hyperloop");
    [cls performSelector:@selector(didStopNewContext:bridge:) withObject:kroll withObject:self];
  }
  [self autorelease]; // Safe to release now that the context is done
}

- (void)registerProxy:(id)proxy krollObject:(KrollObject *)ourKrollObject
{
  os_unfair_lock_lock(&proxyLock);
  if (registeredProxies == NULL) {
    registeredProxies = CFDictionaryCreateMutable(NULL, 10, &kCFTypeDictionaryKeyCallBacks, &kCFTypeDictionaryValueCallBacks);
  }
  //NOTE: Do NOT treat registeredProxies like a mutableDictionary; mutable dictionaries copy keys,
  //CFMutableDictionaryRefs only retain keys, which lets them work with proxies properly.

  CFDictionaryAddValue(registeredProxies, proxy, ourKrollObject);
  os_unfair_lock_unlock(&proxyLock);
  [proxy boundBridge:self withKrollObject:ourKrollObject];
}

- (id)registerProxy:(id)proxy
{
  KrollObject *ourKrollObject = [self krollObjectForProxy:proxy];

  if (ourKrollObject != nil) {
    return ourKrollObject;
  }

  if (![context isKJSThread]) {
    return nil;
  }

  ourKrollObject = [[KrollObject alloc] initWithTarget:proxy context:context];
  [ourKrollObject applyGarbageCollectionSafeguard];

  [self registerProxy:proxy
          krollObject:ourKrollObject];
  return [ourKrollObject autorelease];
}

- (void)unregisterProxy:(id)proxy
{
  os_unfair_lock_lock(&proxyLock);
  if (registeredProxies != NULL) {
    CFDictionaryRemoveValue(registeredProxies, proxy);
    //Don't bother with removing the empty registry. It's small and leaves on dealloc anyways.
  }
  os_unfair_lock_unlock(&proxyLock);
  [proxy unboundBridge:self];
}

- (BOOL)usesProxy:(id)proxy
{
  if (proxy == nil) {
    return NO;
  }
  BOOL result = NO;
  os_unfair_lock_lock(&proxyLock);

  if (registeredProxies != NULL) {
    result = (CFDictionaryGetCountOfKey(registeredProxies, proxy) != 0);
  }
  os_unfair_lock_unlock(&proxyLock);
  return result;
}

- (id)krollObjectForProxy:(id)proxy
{
  id result = nil;
  os_unfair_lock_lock(&proxyLock);
  if (registeredProxies != NULL) {
    result = (id)CFDictionaryGetValue(registeredProxies, proxy);
  }
  os_unfair_lock_unlock(&proxyLock);
  return result;
}

+ (NSArray *)krollBridgesUsingProxy:(id)proxy
{
  NSMutableArray *results = nil;

  os_unfair_lock_lock(&krollBridgeRegistryLock);
  signed long bridgeCount = CFSetGetCount(krollBridgeRegistry);
  KrollBridge *registryObjects[bridgeCount];
  CFSetGetValues(krollBridgeRegistry, (const void **)registryObjects);

  for (int currentBridgeIndex = 0; currentBridgeIndex < bridgeCount; currentBridgeIndex++) {
    KrollBridge *currentBridge = registryObjects[currentBridgeIndex];
    if (![currentBridge usesProxy:proxy]) {
      continue;
    }
    if (results == nil) {
      results = [NSMutableArray arrayWithObject:currentBridge];
      continue;
    }
    [results addObject:currentBridge];
  }

  //Why do we wait so long? In case someone tries to dealloc the krollBridge while we're looking at it.
  //registryObjects nor the registry does a retain here!
  os_unfair_lock_unlock(&krollBridgeRegistryLock);
  return results;
}

+ (NSArray *)krollContexts
{
  os_unfair_lock_lock(&krollBridgeRegistryLock);
  signed long bridgeCount = CFSetGetCount(krollBridgeRegistry);
  KrollBridge *registryObjects[bridgeCount];
  CFSetGetValues(krollBridgeRegistry, (const void **)registryObjects);

  NSMutableArray *results = [[NSMutableArray alloc] initWithCapacity:0];
  for (NSUInteger currentBridgeIndex = 0; currentBridgeIndex < bridgeCount; ++currentBridgeIndex) {
    KrollBridge *bridge = registryObjects[currentBridgeIndex];
    [results addObject:bridge.krollContext];
  }

  os_unfair_lock_unlock(&krollBridgeRegistryLock);
  return [results autorelease];
}

+ (BOOL)krollBridgeExists:(KrollBridge *)bridge
{
  if (bridge == nil) {
    return NO;
  }

  bool result = NO;
  os_unfair_lock_lock(&krollBridgeRegistryLock);
  signed long bridgeCount = CFSetGetCount(krollBridgeRegistry);
  KrollBridge *registryObjects[bridgeCount];
  CFSetGetValues(krollBridgeRegistry, (const void **)registryObjects);
  for (int currentBridgeIndex = 0; currentBridgeIndex < bridgeCount; currentBridgeIndex++) {
    KrollBridge *currentBridge = registryObjects[currentBridgeIndex];
    if (currentBridge == bridge) {
      result = YES;
      break;
    }
  }
  //Why not CFSetContainsValue? Because bridge may not be a valid pointer, and SetContainsValue
  //will ask it for a hash!
  os_unfair_lock_unlock(&krollBridgeRegistryLock);

  return result;
}

+ (KrollBridge *)krollBridgeForThreadName:(NSString *)threadName;
{
  if (threadName == nil) {
    return nil;
  }

  KrollBridge *result = nil;
  os_unfair_lock_lock(&krollBridgeRegistryLock);
  signed long bridgeCount = CFSetGetCount(krollBridgeRegistry);
  KrollBridge *registryObjects[bridgeCount];
  CFSetGetValues(krollBridgeRegistry, (const void **)registryObjects);
  for (int currentBridgeIndex = 0; currentBridgeIndex < bridgeCount; currentBridgeIndex++) {
  }
  os_unfair_lock_unlock(&krollBridgeRegistryLock);

  return result;
}

- (int)forceGarbageCollectNow;
{
  [context gc];
  //Actually forcing garbage collect now will cause a deadlock.
  return 0;
}

- (BOOL)shouldDebugContext
{
  return [[self host] debugMode];
}

- (BOOL)shouldProfileContext
{
  return [[self host] profileMode];
}

@end
