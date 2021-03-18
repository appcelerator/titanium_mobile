/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UIWEBVIEW

#import "TiUIWebView.h"
#import "TiUIWebViewProxy.h"
#import "TiUIiOSWebViewConfigurationProxy.h"
#import "TiUIiOSWebViewDecisionHandlerProxy.h"

#import <TitaniumKit/Mimetypes.h>
#import <TitaniumKit/SBJSON.h>
#import <TitaniumKit/TiApp.h>
#import <TitaniumKit/TiBlob.h>
#import <TitaniumKit/TiExceptionHandler.h>
#import <TitaniumKit/TiFile.h>
#import <TitaniumKit/TiFilesystemFileProxy.h>
#import <TitaniumKit/TiHost.h>
#import <TitaniumKit/TiProxy.h>
#import <TitaniumKit/TiUtils.h>
#import <TitaniumKit/Webcolor.h>

#import <Foundation/Foundation.h>
#import <objc/runtime.h>

extern NSString *const TI_APPLICATION_ID;
static NSString *const baseInjectScript = @"Ti._hexish=function(a){var r='';var e=a.length;var c=0;var h;while(c<e){h=a.charCodeAt(c++).toString(16);r+='\\\\u';var l=4-h.length;while(l-->0){r+='0'};r+=h}return r};Ti._bridgeEnc=function(o){return'<'+Ti._hexish(o)+'>'};Ti._JSON=function(object,bridge){var type=typeof object;switch(type){case'undefined':case'function':case'unknown':return undefined;case'number':case'boolean':return object;case'string':if(bridge===1)return Ti._bridgeEnc(object);return'\"'+object.replace(/\"/g,'\\\\\"').replace(/\\n/g,'\\\\n').replace(/\\r/g,'\\\\r')+'\"'}if((object===null)||(object.nodeType==1))return'null';if(object.constructor.toString().indexOf('Date')!=-1){return'new Date('+object.getTime()+')'}if(object.constructor.toString().indexOf('Array')!=-1){var res='[';var pre='';var len=object.length;for(var i=0;i<len;i++){var value=object[i];if(value!==undefined)value=Ti._JSON(value,bridge);if(value!==undefined){res+=pre+value;pre=', '}}return res+']'}var objects=[];for(var prop in object){var value=object[prop];if(value!==undefined){value=Ti._JSON(value,bridge)}if(value!==undefined){objects.push(Ti._JSON(prop,bridge)+': '+value)}}return'{'+objects.join(',')+'}'};";

@implementation TiUIWebView

#ifdef TI_USE_AUTOLAYOUT
- (void)initializeTiLayoutView
{
  [super initializeTiLayoutView];
  [self setDefaultHeight:TiDimensionAutoFill];
  [self setDefaultWidth:TiDimensionAutoFill];
}
#endif

- (void)dealloc
{
  RELEASE_TO_NIL(_pageToken);
  RELEASE_TO_NIL(_loadingIndicator);
  RELEASE_TO_NIL(self.reloadData);
  [super dealloc];
}

- (void)viewDidClose
{
  _isViewDetached = YES;
  if (_webView != nil) {
    [_webView setUIDelegate:nil];
    [_webView setNavigationDelegate:nil];
    if (_webView.loading) {
      [_webView stopLoading];
    }
  }
  [_webView removeObserver:self forKeyPath:@"estimatedProgress"];
  [_webView removeFromSuperview];
  RELEASE_TO_NIL(_webView);
}

#pragma mark Internal API's

- (WKWebView *)webView
{
  if (_webView == nil) {
    TiUIiOSWebViewConfigurationProxy *configProxy = [[self proxy] valueForKey:@"configuration"];
    WKWebViewConfiguration *config = configProxy ? [configProxy configuration] : [[[WKWebViewConfiguration alloc] init] autorelease];
    WKUserContentController *controller = [[[WKUserContentController alloc] init] autorelease];

    [controller addUserScript:[self userScriptTitaniumInjectionForAppEvent]];

    [config setUserContentController:controller];

    if (![WKWebView handlesURLScheme:[WebAppProtocolHandler specialProtocolScheme]]) {
      [config setURLSchemeHandler:[[WebAppProtocolHandler alloc] init] forURLScheme:[WebAppProtocolHandler specialProtocolScheme]];
    }

    _willHandleTouches = [TiUtils boolValue:[[self proxy] valueForKey:@"willHandleTouches"] def:YES];

    _webView = [[WKWebView alloc] initWithFrame:[self bounds] configuration:config];

    [_webView setUIDelegate:self];
    [_webView setNavigationDelegate:self];
    [_webView setContentMode:[self contentModeForWebView]];
    [_webView setAutoresizingMask:UIViewAutoresizingFlexibleHeight | UIViewAutoresizingFlexibleWidth];

    // KVO for "progress" event
    [_webView addObserver:self forKeyPath:@"estimatedProgress" options:NSKeyValueObservingOptionNew context:NULL];

    [self addSubview:_webView];
    [self _initializeLoadingIndicator];
  }

  return _webView;
}

- (UIView *)hitTest:(CGPoint)point withEvent:(UIEvent *)event
{
  UIView *view = [super hitTest:point withEvent:event];
  if (([self hasTouchableListener]) && _willHandleTouches) {
    UIView *superView = [view superview];
    UIView *parentSuperView = [superView superview];

    if ((view == [self webView]) || (superView == [self webView]) || (parentSuperView == [self webView]) || ([parentSuperView superview] == [self webView]) || ([[parentSuperView superview] superview] == [self webView])) {
      return self;
    }
  }

  return view;
}

- (void)fireEvent:(id)listener withObject:(id)obj remove:(BOOL)yn thisObject:(id)thisObject_
{
  if (_webView != nil) {
    NSDictionary *event = (NSDictionary *)obj;
    NSString *name = [event objectForKey:@"type"];
    NSString *js = [NSString stringWithFormat:@"Ti.App._dispatchEvent('%@',%@,%@);", name, listener, [TiUtils jsonStringify:event]];
    [_webView evaluateJavaScript:js
               completionHandler:^(id result, NSError *error) {
                 if (error != nil) {
                   NSLog(@"[ERROR] Error firing event '%@': %@", name, error.localizedDescription);
                 }
               }];
  }
}

- (NSURL *)fileURLToAppURL:(NSURL *)url_
{
  NSString *basepath = [TiHost resourcePath];
  NSString *urlstr = [url_ path];
  NSString *path = [urlstr stringByReplacingOccurrencesOfString:[NSString stringWithFormat:@"%@/", basepath] withString:@""];
  if ([path hasPrefix:@"/"]) {
    path = [path substringFromIndex:1];
  }
  return [NSURL URLWithString:[[NSString stringWithFormat:@"app://%@/%@", TI_APPLICATION_ID, path] stringByAddingPercentEncodingWithAllowedCharacters:[NSCharacterSet URLHostAllowedCharacterSet]]];
}

+ (BOOL)isLocalURL:(NSURL *)url
{
  NSString *scheme = [url scheme];
  return [scheme isEqualToString:@"file"] || [scheme isEqualToString:@"app"];
}

#pragma mark Public API's

- (void)setHandlePlatformUrl_:(id)arg
{
  DEPRECATED_REPLACED(@"UI.WebView.handlePlatformUrl", @"8.0.0", @"Use UI.WebView.allowedURLSchemes in conjuction with UI.WebView.handleurl event");
}

- (void)setZoomLevel_:(id)zoomLevel
{
  ENSURE_TYPE(zoomLevel, NSNumber);
#if IS_SDK_IOS_14
  if ([TiUtils isIOSVersionOrGreater:@"14.0"]) {
    [self webView].pageZoom = [zoomLevel floatValue];
    return;
  }
#endif
  [[self webView] evaluateJavaScript:[NSString stringWithFormat:@"document.body.style.zoom = %@;", zoomLevel]
                   completionHandler:nil];
}

- (void)setWillHandleTouches_:(id)value
{
  ENSURE_TYPE(value, NSNumber);

  [[self proxy] replaceValue:value forKey:@"willHandleTouches" notification:NO];
  _willHandleTouches = [TiUtils boolValue:value def:YES];
}

- (void)setUrl_:(id)value
{
  ignoreNextRequest = YES;
  self.reloadData = value;
  reloadMethod = @selector(setUrl_:);

  ENSURE_TYPE(value, NSString);
  [[self proxy] replaceValue:value forKey:@"url" notification:NO];

  if ([[self webView] isLoading]) {
    [[self webView] stopLoading];
  }

  NSURL *url = [TiUtils toURL:value proxy:self.proxy];

  [_webView.configuration.userContentController removeScriptMessageHandlerForName:@"_Ti_"];

  if ([[self class] isLocalURL:url]) {
    [_webView.configuration.userContentController addScriptMessageHandler:self name:@"_Ti_"];
    [self loadLocalURL:url];
  } else {
    [self loadRequestWithURL:[NSURL URLWithString:[TiUtils stringValue:value]]];
  }
}

- (void)setBackgroundColor_:(id)value
{
  [[self proxy] replaceValue:value forKey:@"backgroundColor" notification:NO];

  [[self webView] setOpaque:NO];
  [[self webView] setBackgroundColor:[[TiUtils colorValue:value] color]];
}

- (void)setAssetsDirectory_:(id)value
{
  ENSURE_TYPE_OR_NIL(value, NSString);
  assetsDirectory = value;
}

- (void)setData_:(id)value
{
  ignoreNextRequest = YES;
  self.reloadData = value;
  reloadMethod = @selector(setData_:);

  [[self proxy] replaceValue:value forKey:@"data" notification:NO];

  if ([[self webView] isLoading]) {
    [[self webView] stopLoading];
  }

  NSData *data = nil;
  NSString *mimeType = nil;

  if ([value isKindOfClass:[TiBlob class]]) {
    data = [(TiBlob *)value data];
    mimeType = [(TiBlob *)value mimeType];
  } else if ([value isKindOfClass:[TiFile class]]) {
#ifdef USE_TI_FILESYSTEM
    data = [[(TiFilesystemFileProxy *)value blob] data];
#endif
  } else {
    NSLog(@"[ERROR] Ti.UI.WebView.data can only be a TiBlob or TiFile object, was %@", [(TiProxy *)value apiName]);
  }

  [_webView.configuration.userContentController removeScriptMessageHandlerForName:@"_Ti_"];
  [_webView.configuration.userContentController addScriptMessageHandler:self name:@"_Ti_"];

  mimeType = mimeType ?: [self mimeTypeForData:data];
  [[self webView] loadData:data
                   MIMEType:mimeType
      characterEncodingName:@"UTF-8"
                    baseURL:[[NSBundle mainBundle] resourceURL]];
}

- (void)setBlacklistedURLs_:(id)blacklistedURLs
{
  DEPRECATED_REPLACED(@"UI.WebView.blacklistedURLs", @"9.2.0", @"UI.WebView.blockedURLs");

  ENSURE_TYPE(blacklistedURLs, NSArray);
  for (id nextURL in blacklistedURLs) {
    ENSURE_TYPE(nextURL, NSString);
  }

  _blacklistedURLs = blacklistedURLs;
}

- (void)setBlockedURLs_:(id)blockedURLs
{
  ENSURE_TYPE(blockedURLs, NSArray);
  for (id nextURL in blockedURLs) {
    ENSURE_TYPE(nextURL, NSString);
  }

  _blockedURLs = blockedURLs;
}

- (void)setHtml_:(id)args
{
  ignoreNextRequest = YES;
  self.reloadData = args;
  reloadMethod = @selector(setHtml_:);

  NSString *content = nil;
  NSDictionary *options = nil;

  if ([args isKindOfClass:[NSArray class]]) {
    content = [TiUtils stringValue:[args objectAtIndex:0]];
    if ([args count] == 2) {
      options = [args objectAtIndex:1];
    }
  } else if ([args isKindOfClass:[NSString class]]) {
    content = [TiUtils stringValue:args];
  } else {
    [self throwException:@"Invalid argument" subreason:@"Requires single string argument or two arguments (String, Object)" location:CODELOCATION];
  }

  [[self proxy] replaceValue:content forKey:@"html" notification:NO];

  if ([[self webView] isLoading]) {
    [[self webView] stopLoading];
  }

  [_webView.configuration.userContentController removeScriptMessageHandlerForName:@"_Ti_"];
  [_webView.configuration.userContentController addScriptMessageHandler:self name:@"_Ti_"];

  // No options, default load behavior
  if (options == nil) {
    [[self webView] loadHTMLString:content baseURL:[NSURL fileURLWithPath:[TiHost resourcePath]]];
    return;
  }

  // Options available, handle them!
  NSString *baseURL = options[@"baseURL"];
  NSString *mimeType = options[@"mimeType"];

  NSURL *url = [baseURL hasPrefix:@"file:"] ? [NSURL fileURLWithPath:baseURL] : [NSURL URLWithString:baseURL];

  [[self webView] loadData:[content dataUsingEncoding:NSUTF8StringEncoding]
                   MIMEType:mimeType
      characterEncodingName:@"UTF-8"
                    baseURL:url];
}

- (void)setDisableBounce_:(id)value
{
  [[self proxy] replaceValue:[value isEqual:@1] ? @0 : @1 forKey:@"disableBounce" notification:NO];
  [[[self webView] scrollView] setBounces:![TiUtils boolValue:value]];
}

- (void)setScrollsToTop_:(id)value
{
  [[self proxy] replaceValue:value forKey:@"scrollsToTop" notification:NO];
  [[[self webView] scrollView] setScrollsToTop:[TiUtils boolValue:value def:YES]];
}

- (void)setAllowsBackForwardNavigationGestures_:(id)value
{
  [[self proxy] replaceValue:value forKey:@"allowsBackForwardNavigationGestures" notification:NO];
  [[self webView] setAllowsBackForwardNavigationGestures:[TiUtils boolValue:value def:NO]];
}

- (void)setUserAgent_:(id)value
{
  [[self proxy] replaceValue:value forKey:@"userAgent" notification:NO];
  [[self webView] setCustomUserAgent:[TiUtils stringValue:value]];
}

- (void)setEnableZoomControls_:(id)value
{
  ENSURE_TYPE(value, NSNumber);

  BOOL enableZoom = [TiUtils boolValue:value def:YES];

  if (!enableZoom) {
    WKUserContentController *controller = [[[self webView] configuration] userContentController];
    [controller addUserScript:[self userScriptDisableZoom]];
  }
}

- (void)setScalesPageToFit_:(id)value
{
  ENSURE_TYPE(value, NSNumber);

  BOOL scalesPageToFit = [TiUtils boolValue:value];
  BOOL enableZoom = [TiUtils boolValue:[[self proxy] valueForKey:@"enableZoomControls"] def:YES];

  if (scalesPageToFit && enableZoom) {
    WKUserContentController *controller = [[[self webView] configuration] userContentController];
    [controller addUserScript:[self userScriptScalesPageToFit]];
  }
}

- (void)setDisableContextMenu_:(id)value
{
  ENSURE_TYPE(value, NSNumber);

  BOOL disableContextMenu = [TiUtils boolValue:value];

  if (disableContextMenu == YES) {
    WKUserContentController *controller = [[[self webView] configuration] userContentController];
    [controller addUserScript:[self userScriptDisableContextMenu]];
  }
}

- (void)setKeyboardDisplayRequiresUserAction_:(id)value
{
  [self _setKeyboardDisplayRequiresUserAction:[TiUtils boolValue:value]];
  [[self proxy] replaceValue:value forKey:@"keyboardDisplayRequiresUserAction" notification:NO];
}

- (void)reload
{
  if (_webView == nil) {
    return;
  }
  if (self.reloadData != nil) {
    [self performSelector:reloadMethod withObject:self.reloadData];
    return;
  }
  [[self webView] reload];
}

#pragma mark Utilities

- (void)loadRequestWithURL:(NSURL *)url
{
  NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:url
                                                         cachePolicy:[TiUtils intValue:[[self proxy] valueForKey:@"cachePolicy"] def:NSURLRequestUseProtocolCachePolicy]
                                                     timeoutInterval:[TiUtils doubleValue:[[self proxy] valueForKey:@"timeout"] def:60]];

  // Set request headers
  NSDictionary<NSString *, id> *requestHeaders = [[self proxy] valueForKey:@"requestHeaders"];

  if (requestHeaders != nil) {
    for (NSString *key in requestHeaders) {
      [request setValue:requestHeaders[key] forHTTPHeaderField:key];
    }

    // Inject user-agent by using the obj-c nullability to set and reset it
    NSString *userAgent = requestHeaders[@"User-Agent"];
    [[self webView] setCustomUserAgent:userAgent];
  }

  [self addCookieHeaderForRequest:request];

  [[self webView] loadRequest:request];
}

- (WKUserScript *)userScriptScalesPageToFit
{
  NSString *source = @"var meta = document.createElement('meta'); \
    meta.setAttribute('name', 'viewport'); \
    meta.setAttribute('content', 'width=device-width, initial-scale=1, maximum-scale=1'); \
    document.getElementsByTagName('head')[0].appendChild(meta);";

  return [[[WKUserScript alloc] initWithSource:source injectionTime:WKUserScriptInjectionTimeAtDocumentEnd forMainFrameOnly:YES] autorelease];
}

- (WKUserScript *)userScriptTitaniumInjectionForAppEvent
{
  if (_pageToken == nil) {
    _pageToken = [[NSString stringWithFormat:@"%lu", (unsigned long)[self hash]] retain];
    [(TiUIWebViewProxy *)self.proxy setPageToken:_pageToken];
  }

  NSString *titanium = [NSString stringWithFormat:@"%@%s", @"Ti", "tanium"];
  NSString *source = @"var callbacks = {}; var Ti = {}; var %@ = Ti; Ti.pageToken = %@; \
    Ti._listener_id = 1; Ti._listeners={}; %@\
    Ti.App = { \
                fireEvent: function(name, payload) { \
                var _payload = payload; \
                if (typeof payload === 'string') { \
                  _payload = JSON.parse(payload); \
                } \
                if (callbacks[name]) { \
                  callbacks[name](_payload); \
                } \
              window.webkit.messageHandlers._Ti_.postMessage({name: name, payload: _payload, method: 'fireEvent'},'*'); \
                }, \
    addEventListener: function(name, callback) { \
    callbacks[name] = callback; \
    var listeners=Ti._listeners[name]; \
    if(typeof(listeners)=='undefined'){ \
    listeners=[];Ti._listeners[name]=listeners} \
    var newid=Ti.pageToken+Ti._listener_id++; \
    listeners.push({callback:callback,id:newid});\
    window.webkit.messageHandlers._Ti_.postMessage({name: name, method: 'addEventListener', callback: Ti._JSON({name:name, id:newid},1)},'*'); \
    }, \
    removeEventListener: function(name, fn) { \
    var listeners=Ti._listeners[name]; \
    if(listeners){ \
    for(var c=0;c<listeners.length;c++){ \
    var entry=listeners[c]; \
    if(entry.callback==fn){ \
    listeners.splice(c,1);\
    window.webkit.messageHandlers._Ti_.postMessage({name: name, method: 'removeEventListener', callback: Ti._JSON({name:name, id:entry.id},1)},'*'); \
    delete callbacks[name]; break}}}\
    }, \
    _dispatchEvent: function(type,evtid,evt){ \
    var listeners=Ti._listeners[type]; \
    if(listeners){ \
    for(var c=0;c<listeners.length;c++){ \
    var entry=listeners[c]; \
    if(entry.id==evtid){ \
    entry.callback.call(entry.callback,evt) \
    }}}}}; \
    Ti.API = { \
    debug: function(message) { \
    window.webkit.messageHandlers._Ti_.postMessage({name:'debug', method: 'log', callback: Ti._JSON({level:'debug', message:message},1)},'*'); \
    }, \
    error: function(message) { \
    window.webkit.messageHandlers._Ti_.postMessage({name:'error', method: 'log', callback: Ti._JSON({level:'error', message:message},1)},'*'); \
    }, \
    info: function(message){ \
    window.webkit.messageHandlers._Ti_.postMessage({name:'info', method: 'log', callback: Ti._JSON({level:'info', message:message},1)},'*'); \
    }, \
    fatal: function(message){ \
    window.webkit.messageHandlers._Ti_.postMessage({name:'fatal', method: 'log', callback: Ti._JSON({level:'fatal', message:message},1)},'*'); \
    }, \
    warn: function(message){ \
    window.webkit.messageHandlers._Ti_.postMessage({name:'warn', method: 'log', callback: Ti._JSON({level:'warn', message:message},1)},'*'); \
    }, \
    log: function(level, message){ \
    window.webkit.messageHandlers._Ti_.postMessage({name: level, method: 'log', callback: Ti._JSON({level: level, message:message},1)},'*'); \
    }, \
    }; \
    ";

  NSString *sourceString = [NSString stringWithFormat:source, titanium, _pageToken, baseInjectScript];
  return [[[WKUserScript alloc] initWithSource:sourceString injectionTime:WKUserScriptInjectionTimeAtDocumentStart forMainFrameOnly:NO] autorelease];
}

- (WKUserScript *)userScriptDisableZoom
{
  NSString *source = @"var meta = document.createElement('meta'); \
    meta.setAttribute('name', 'viewport'); \
    meta.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'); \
    document.getElementsByTagName('head')[0].appendChild(meta);";

  return [[[WKUserScript alloc] initWithSource:source injectionTime:WKUserScriptInjectionTimeAtDocumentEnd forMainFrameOnly:YES] autorelease];
}

- (WKUserScript *)userScriptDisableContextMenu
{
  NSString *source = @"var style = document.createElement('style'); \
    style.type = 'text/css'; \
    style.innerText = '*:not(input):not(textarea) { -webkit-user-select: none; -webkit-touch-callout: none; }'; \
    var head = document.getElementsByTagName('head')[0]; \
    head.appendChild(style);";

  return [[[WKUserScript alloc] initWithSource:source
                                 injectionTime:WKUserScriptInjectionTimeAtDocumentEnd
                              forMainFrameOnly:YES] autorelease];
}

- (WKUserScript *)userScriptTitaniumJSEvaluationFromString:(NSString *)string
{
  return [[[WKUserScript alloc] initWithSource:string
                                 injectionTime:WKUserScriptInjectionTimeAtDocumentEnd
                              forMainFrameOnly:YES] autorelease];
}

- (WKUserScript *)userScriptCookieOut
{
  return [[[WKUserScript alloc] initWithSource:@"window.webkit.messageHandlers._Ti_Cookie_.postMessage(document.cookie);" injectionTime:WKUserScriptInjectionTimeAtDocumentStart forMainFrameOnly:NO] autorelease];
}

- (WKUserScript *)userScriptCookieInForDomain:(NSString *)validDomain
{
  NSMutableString *script = [[NSMutableString alloc] init];
  [script appendString:@"var cookieNames = document.cookie.split('; ').map(function(cookie) { return cookie.split('=')[0] } );\n"];

  for (NSHTTPCookie *cookie in [[NSHTTPCookieStorage sharedHTTPCookieStorage] cookies]) {
    // Skip cookies that will break our script
    if ([cookie.value rangeOfString:@"'"].location != NSNotFound) {
      continue;
    }
    // Check the cookie for current domain?
    if (![validDomain hasSuffix:cookie.domain] && ![cookie.domain hasSuffix:validDomain]) {
      continue;
    }
    // Create a line that appends this cookie to the web view's document's cookies
    [script appendFormat:@"if (cookieNames.indexOf('%@') == -1) { document.cookie='%@'; };\n", cookie.name, [self javascriptStringWithCookie:cookie]];
  }

  return [[[WKUserScript alloc] initWithSource:script injectionTime:WKUserScriptInjectionTimeAtDocumentStart forMainFrameOnly:NO] autorelease];
}

- (NSString *)javascriptStringWithCookie:(NSHTTPCookie *)cookie
{
  NSString *string = [NSString stringWithFormat:@"%@=%@;domain=%@;path=%@", cookie.name, cookie.value, cookie.domain, cookie.path ?: @"/"];

  if (cookie.secure) {
    string = [string stringByAppendingString:@";secure=true"];
  }

  return string;
}

- (NSString *)pathFromComponents:(NSArray *)args
{
  NSString *newPath;
  id first = [args objectAtIndex:0];

  if ([first hasPrefix:@"file://"]) {
    newPath = [[NSURL URLWithString:first] path];
  } else if ([first characterAtIndex:0] != '/') {
    newPath = [[[NSURL URLWithString:[self resourcesDirectory]] path] stringByAppendingPathComponent:[self resolveFile:first]];
  } else {
    newPath = [self resolveFile:first];
  }

  if ([args count] > 1) {
    for (int c = 1; c < [args count]; c++) {
      newPath = [newPath stringByAppendingPathComponent:[self resolveFile:[args objectAtIndex:c]]];
    }
  }

  return [newPath stringByStandardizingPath];
}

- (id)resolveFile:(id)arg
{
#ifdef USE_TI_FILESYSTEM
  if ([arg isKindOfClass:[TiFilesystemFileProxy class]]) {
    return [(TiFilesystemFileProxy *)arg path];
  }
#endif
  return [TiUtils stringValue:arg];
}

- (NSString *)resourcesDirectory
{
  return [NSString stringWithFormat:@"%@/", [[NSURL fileURLWithPath:[TiHost resourcePath] isDirectory:YES] path]];
}

// http://stackoverflow.com/a/32765708/5537752
- (NSString *)mimeTypeForData:(NSData *)data
{
  uint8_t c;
  [data getBytes:&c length:1];

  switch (c) {
  case 0xFF:
    return @"image/jpeg";
    break;
  case 0x89:
    return @"image/png";
    break;
  case 0x47:
    return @"image/gif";
    break;
  case 0x49:
  case 0x4D:
    return @"image/tiff";
    break;
  case 0x25:
    return @"application/pdf";
    break;
  case 0xD0:
    return @"application/vnd";
    break;
  case 0x46:
    return @"text/plain";
    break;
  default:
    return @"application/octet-stream";
  }

  return nil;
}

// WARNING: This is not officially available in WKWebView!
- (void)_setKeyboardDisplayRequiresUserAction:(BOOL)value
{
  Class class = NSClassFromString([NSString stringWithFormat:@"W%@tV%@", @"KConten", @"iew"]);

  if ([TiUtils isIOSVersionOrGreater:@"11.3"]) {
    SEL selector = sel_getUid("_startAssistingNode:userIsInteracting:blurPreviousNode:changingActivityState:userObject:");
    Method method = class_getInstanceMethod(class, selector);
    IMP original = method_getImplementation(method);
    IMP override = imp_implementationWithBlock(^void(id me, void *arg0, BOOL arg1, BOOL arg2, BOOL arg3, id arg4) {
      ((void (*)(id, SEL, void *, BOOL, BOOL, BOOL, id))original)(me, selector, arg0, !value, arg2, arg3, arg4);
    });
    method_setImplementation(method, override);
  } else {
    SEL selector = sel_getUid("_startAssistingNode:userIsInteracting:blurPreviousNode:userObject:");
    Method method = class_getInstanceMethod(class, selector);
    IMP original = method_getImplementation(method);
    IMP override = imp_implementationWithBlock(^void(id me, void *arg0, BOOL arg1, BOOL arg2, id arg3) {
      ((void (*)(id, SEL, void *, BOOL, BOOL, id))original)(me, selector, arg0, !value, arg2, arg3);
    });
    method_setImplementation(method, override);
  }
}

- (void)addCookieHeaderForRequest:(NSMutableURLRequest *)request
{
  /*
   To support cookie
   https://stackoverflow.com/questions/26573137
   https://github.com/haifengkao/YWebView
   */

  NSString *validDomain = request.URL.host;

  if (validDomain.length <= 0) {
    return;
  }
  if (!_tiCookieHandlerAdded) {
    _tiCookieHandlerAdded = YES;
    WKUserContentController *controller = [[[self webView] configuration] userContentController];
    [controller addUserScript:[self userScriptCookieInForDomain:validDomain]];
    [controller addUserScript:[self userScriptCookieOut]];
    [controller addScriptMessageHandler:self name:@"_Ti_Cookie_"];
  }

  BOOL requestIsSecure = [request.URL.scheme isEqualToString:@"https"];

  NSMutableArray *array = [NSMutableArray array];
  for (NSHTTPCookie *cookie in [[NSHTTPCookieStorage sharedHTTPCookieStorage] cookies]) {
    // Don't even bother with values containing a `'`
    if ([cookie.name rangeOfString:@"'"].location != NSNotFound) {
      continue;
    }
    // Check the cookie for current domain.
    if (![validDomain hasSuffix:cookie.domain] && ![cookie.domain hasSuffix:validDomain]) {
      continue;
    }

    if (cookie.secure && !requestIsSecure) {
      continue;
    }
    NSString *value = [NSString stringWithFormat:@"%@=%@", cookie.name, cookie.value];
    [array addObject:value];
  }

  NSString *header = [array componentsJoinedByString:@";"];
  if (![header isEqualToString:@""]) {
    [request setValue:header forHTTPHeaderField:@"Cookie"];
  }
}

- (void)loadLocalURL:(NSURL *)url
{
  NSString *path = [url path];
  NSURL *baseURL = [[url copy] autorelease];

  // first check to see if we're attempting to load a file from the
  // filesystem and if so, and it exists, use that
  if ([[NSFileManager defaultManager] fileExistsAtPath:path]) {
    NSURL *requestURL = [NSURL fileURLWithPath:path];
    NSString *readAccessDirectory = assetsDirectory ?: [[url URLByDeletingLastPathComponent] absoluteString];

    [[self webView] loadFileURL:requestURL
        allowingReadAccessToURL:[NSURL URLWithString:readAccessDirectory]];

  } else {
    // convert it into a app:// relative path to load the resource
    // from our application
    url = [[self fileURLToAppURL:url] retain];
    NSData *data = [TiUtils loadAppResource:url];
    NSString *html = nil;
    if (data != nil) {
      html = [[[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding] autorelease];
    }
    if (html != nil) {
      //Because local HTML may rely on JS that's stored in the app: schema, we must kee the url in the app: format.
      [[self webView] loadHTMLString:html baseURL:baseURL];
    } else {
      NSLog(@"[WARN] couldn't load URL: %@", url);
      RELEASE_TO_NIL(url);
    }
  }
}

#pragma mark Delegates

- (void)userContentController:(WKUserContentController *)userContentController didReceiveScriptMessage:(WKScriptMessage *)message
{
  BOOL isEvent = [[message body] isKindOfClass:[NSDictionary class]] && [[message body] objectForKey:@"name"];

  if (isEvent) {
    NSString *name = [[message body] objectForKey:@"name"];
    NSDictionary *payload = [[message body] objectForKey:@"payload"];

    if ([message.name isEqualToString:@"_Ti_"]) {
      NSString *callback = [[message body] objectForKey:@"callback"];

      SBJSON *decoder = [[[SBJSON alloc] init] autorelease];
      NSError *error = nil;
      NSDictionary *event = [decoder fragmentWithString:callback error:&error];

      NSString *method = [[message body] objectForKey:@"method"];
      NSString *moduleName = [method isEqualToString:@"log"] ? @"API" : @"App";

      TiHost *host = [(TiUIWebViewProxy *)self.proxy host];
      id<TiEvaluator> context = [host contextForToken:_pageToken];
      id<Module> module = [host moduleNamed:[NSString stringWithFormat:@"%@Module", moduleName] context:context];
      [module setExecutionContext:context];

      if ([method isEqualToString:@"fireEvent"]) {
        [(TiProxy *)module fireEvent:name withObject:payload];
      } else if ([method isEqualToString:@"addEventListener"]) {
        id listenerid = [event objectForKey:@"id"];
        [(TiProxy *)module addEventListener:[NSArray arrayWithObjects:name, listenerid, nil]];
      } else if ([method isEqualToString:@"removeEventListener"]) {
        id listenerid = [event objectForKey:@"id"];
        [(TiProxy *)module removeEventListener:[NSArray arrayWithObjects:name, listenerid, nil]];
      } else if ([method isEqualToString:@"log"]) {
        NSString *level = [event objectForKey:@"level"];
        NSString *message = [event objectForKey:@"message"];
        if ([(TiProxy *)module respondsToSelector:@selector(log:withMessage:)]) {
          [(TiProxy *)module performSelector:@selector(log:withMessage:) withObject:level withObject:message];
        }
      }
      return;
    }
  }

  if ([message.name isEqualToString:@"_Ti_Cookie_"]) {
    NSArray<NSString *> *cookies = [message.body componentsSeparatedByString:@"; "];
    for (NSString *cookie in cookies) {
      // Get this cookie's name and value
      NSRange separatorRange = [cookie rangeOfString:@"="];
      if (separatorRange.location == NSNotFound || separatorRange.location == 0 || separatorRange.location == ([cookie length] - 1)) {
        continue;
      }
      NSString *cookieName = [cookie substringToIndex:separatorRange.location];
      NSString *cookieValue = [cookie substringFromIndex:separatorRange.location + separatorRange.length];

      // Get the cookie in shared storage with that name
      NSHTTPCookie *localCookie = nil;
      for (NSHTTPCookie *httpCookie in [[NSHTTPCookieStorage sharedHTTPCookieStorage] cookiesForURL:self.webView.URL]) {
        if ([httpCookie.name isEqualToString:cookieName]) {
          localCookie = httpCookie;
          break;
        }
      }

      //If there is a cookie with a stale value, update it now.
      if (localCookie != nil) {
        NSMutableDictionary *cookieProperties = [localCookie.properties mutableCopy];
        cookieProperties[NSHTTPCookieValue] = cookieValue;
        NSHTTPCookie *updatedCookie = [NSHTTPCookie cookieWithProperties:cookieProperties];
        [[NSHTTPCookieStorage sharedHTTPCookieStorage] setCookie:updatedCookie];
      } else {
        // We need NSHTTPCookieOriginURL for NSHTTPCookie to be created
        NSString *cookieWithURL = [NSString stringWithFormat:@"%@; ORIGINURL=%@;", cookie, self.webView.URL];
        NSHTTPCookie *httpCookie = [self cookieForString:cookieWithURL];

        if (httpCookie) {
          [[NSHTTPCookieStorage sharedHTTPCookieStorage] setCookie:httpCookie];
        }
      }
    }
  }

  if ([[self proxy] _hasListeners:@"message"]) {
    [[self proxy] fireEvent:@"message"
                 withObject:@{
                   @"url" : message.frameInfo.request.URL.absoluteString ?: [[NSBundle mainBundle] bundlePath],
                   @"body" : message.body,
                   @"name" : message.name,
                   @"isMainFrame" : NUMBOOL(message.frameInfo.isMainFrame),
                 }];
  }
}

- (void)webView:(WKWebView *)webView didReceiveAuthenticationChallenge:(NSURLAuthenticationChallenge *)challenge completionHandler:(void (^)(NSURLSessionAuthChallengeDisposition, NSURLCredential *_Nullable))completionHandler
{
  NSString *authenticationMethod = [[challenge protectionSpace] authenticationMethod];
  NSDictionary<NSString *, NSString *> *basicAuthentication = [[self proxy] valueForKey:@"basicAuthentication"];
  BOOL ignoreSSLError = [TiUtils boolValue:[[self proxy] valueForKey:@"ignoreSslError"] def:NO];

  // Basic authentication
  if ([authenticationMethod isEqualToString:NSURLAuthenticationMethodDefault]
      || [authenticationMethod isEqualToString:NSURLAuthenticationMethodHTTPBasic]
      || [authenticationMethod isEqualToString:NSURLAuthenticationMethodHTTPDigest]) {

    // If "basicAuthentication" property set -> Try to handle
    if (basicAuthentication != nil && [challenge previousFailureCount] == 0) {
      NSString *username = [TiUtils stringValue:@"username" properties:basicAuthentication];
      NSString *password = [TiUtils stringValue:@"password" properties:basicAuthentication];
      NSURLCredentialPersistence persistence = [TiUtils intValue:@"persistence" properties:basicAuthentication def:NSURLCredentialPersistenceNone];

      completionHandler(NSURLSessionAuthChallengeUseCredential, [[[NSURLCredential alloc] initWithUser:username
                                                                                              password:password
                                                                                           persistence:persistence] autorelease]);
      // If "ignoreSslError" is set, ignore the possible error
    } else if (ignoreSSLError) {
      // Allow invalid certificates if specified
      NSURLCredential *credential = [[[NSURLCredential alloc] initWithTrust:[challenge protectionSpace].serverTrust] autorelease];
      completionHandler(NSURLSessionAuthChallengeUseCredential, credential);
      // Default: Reject authentication challenge
    } else {
      if ([[self proxy] _hasListeners:@"sslerror"]) {
        [self.proxy fireEvent:@"sslerror"];
      }
      completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, nil);
    }
    // HTTPS in general
  } else if ([authenticationMethod isEqualToString:NSURLAuthenticationMethodServerTrust]) {
    completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, nil);
    // Default: Reject authentication challenge
  } else {
    completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, nil);
  }
}

- (void)webView:(WKWebView *)webView didFinishNavigation:(WKNavigation *)navigation
{
  [self _cleanupLoadingIndicator];
  [(TiUIWebViewProxy *)[self proxy] refreshHTMLContent];

  // TO DO: Once TIMOB-26915 done, remove this
  __block BOOL finishedEvaluation = NO;
  [_webView.configuration.websiteDataStore.httpCookieStore getAllCookies:^(NSArray<NSHTTPCookie *> *cookies) {
    for (NSHTTPCookie *cookie in cookies) {
      [NSHTTPCookieStorage.sharedHTTPCookieStorage setCookie:cookie];
    }
    finishedEvaluation = YES;
  }];
  while (!finishedEvaluation) {
    [NSRunLoop.currentRunLoop runMode:NSDefaultRunLoopMode beforeDate:NSDate.distantFuture];
  }

  if ([[self proxy] _hasListeners:@"load"]) {
    [[self proxy] fireEvent:@"load" withObject:@{ @"url" : webView.URL.absoluteString, @"title" : webView.title }];
  }
  ignoreNextRequest = NO;
}

- (void)webView:(WKWebView *)webView didFailNavigation:(WKNavigation *)navigation withError:(NSError *)error
{
  [self _cleanupLoadingIndicator];
  [self _fireErrorEventWithError:error];
}

- (void)webView:(WKWebView *)webView didFailProvisionalNavigation:(WKNavigation *)navigation withError:(NSError *)error
{
  [self _cleanupLoadingIndicator];
  [self _fireErrorEventWithError:error];
}

- (void)webView:(WKWebView *)webView didReceiveServerRedirectForProvisionalNavigation:(WKNavigation *)navigation
{
  if ([[self proxy] _hasListeners:@"redirect"]) {
    [[self proxy] fireEvent:@"redirect" withObject:@{ @"url" : webView.URL.absoluteString, @"title" : webView.title }];
  }
}

- (BOOL)webView:(WKWebView *)webView shouldPreviewElement:(WKPreviewElementInfo *)elementInfo
{
  return [TiUtils boolValue:[[self proxy] valueForKey:@"allowsLinkPreview"] def:NO];
}

- (void)webView:(WKWebView *)webView runJavaScriptAlertPanelWithMessage:(NSString *)message initiatedByFrame:(WKFrameInfo *)frame completionHandler:(void (^)(void))completionHandler
{
  UIAlertController *alertController = [UIAlertController alertControllerWithTitle:nil
                                                                           message:message
                                                                    preferredStyle:UIAlertControllerStyleAlert];
  [alertController addAction:[UIAlertAction actionWithTitle:UIKitLocalizedString([TiUtils stringValue:[[self proxy] valueForKey:@"ok"]] ?: NSLocalizedString(@"OK", nil))
                                                      style:UIAlertActionStyleCancel
                                                    handler:^(UIAlertAction *action) {
                                                      completionHandler();
                                                    }]];

  [[TiApp app] showModalController:alertController animated:YES];
}

- (void)webView:(WKWebView *)webView runJavaScriptConfirmPanelWithMessage:(NSString *)message initiatedByFrame:(WKFrameInfo *)frame completionHandler:(void (^)(BOOL))completionHandler
{
  UIAlertController *alertController = [UIAlertController alertControllerWithTitle:nil
                                                                           message:message
                                                                    preferredStyle:UIAlertControllerStyleAlert];

  [alertController addAction:[UIAlertAction actionWithTitle:UIKitLocalizedString([TiUtils stringValue:[[self proxy] valueForKey:@"ok"]] ?: NSLocalizedString(@"OK", nil))
                                                      style:UIAlertActionStyleDefault
                                                    handler:^(UIAlertAction *action) {
                                                      completionHandler(YES);
                                                    }]];

  [alertController addAction:[UIAlertAction actionWithTitle:UIKitLocalizedString([TiUtils stringValue:[[self proxy] valueForKey:@"cancel"]] ?: NSLocalizedString(@"Cancel", nil))
                                                      style:UIAlertActionStyleCancel
                                                    handler:^(UIAlertAction *action) {
                                                      completionHandler(NO);
                                                    }]];

  [[TiApp app] showModalController:alertController animated:YES];
}

- (void)webView:(WKWebView *)webView runJavaScriptTextInputPanelWithPrompt:(NSString *)prompt defaultText:(NSString *)defaultText initiatedByFrame:(WKFrameInfo *)frame completionHandler:(void (^)(NSString *_Nullable))completionHandler
{
  UIAlertController *alertController = [UIAlertController alertControllerWithTitle:nil
                                                                           message:prompt
                                                                    preferredStyle:UIAlertControllerStyleAlert];

  [alertController addTextFieldWithConfigurationHandler:^(UITextField *textField) {
    textField.text = defaultText;
  }];
  [alertController addAction:[UIAlertAction actionWithTitle:UIKitLocalizedString([TiUtils stringValue:[[self proxy] valueForKey:@"ok"]] ?: NSLocalizedString(@"OK", nil))
                                                      style:UIAlertActionStyleDefault
                                                    handler:^(UIAlertAction *action) {
                                                      completionHandler(alertController.textFields.firstObject.text ?: defaultText);
                                                    }]];

  [alertController addAction:[UIAlertAction actionWithTitle:UIKitLocalizedString([TiUtils stringValue:[[self proxy] valueForKey:@"cancel"]] ?: NSLocalizedString(@"Cancel", nil))
                                                      style:UIAlertActionStyleCancel
                                                    handler:^(UIAlertAction *action) {
                                                      completionHandler(nil);
                                                    }]];

  [[TiApp app] showModalController:alertController animated:YES];
}

- (void)fireBeforeLoad:(nonnull WKNavigationAction *)navigationAction
{
  if ([[self proxy] _hasListeners:@"beforeload"]) {
    [[self proxy] fireEvent:@"beforeload"
                 withObject:@{
                   @"url" : navigationAction.request.URL.absoluteString,
                   @"navigationType" : @(navigationAction.navigationType),
                   @"isMainFrame" : NUMBOOL(navigationAction.targetFrame.isMainFrame),
                 }];
  }
}

- (void)webView:(WKWebView *)webView decidePolicyForNavigationAction:(nonnull WKNavigationAction *)navigationAction decisionHandler:(nonnull void (^)(WKNavigationActionPolicy))decisionHandler
{
  if (_isViewDetached) {
    decisionHandler(WKNavigationActionPolicyCancel);
    return;
  }
  NSArray<NSString *> *allowedURLSchemes = [[self proxy] valueForKey:@"allowedURLSchemes"];

  // Do not load the URL if it's black-listed.
  // DEPRECATED: Should use "blockedURLs" property with a "blockedurl" event listener instead.
  NSString *urlCandidate = navigationAction.request.URL.absoluteString;
  if (_blacklistedURLs != nil && _blacklistedURLs.count > 0) {
    for (NSString *blockedURL in _blacklistedURLs) {
      if ([urlCandidate rangeOfString:blockedURL options:NSCaseInsensitiveSearch].location != NSNotFound) {
        if ([[self proxy] _hasListeners:@"blacklisturl"]) {
          [[self proxy] fireEvent:@"blacklisturl"
                       withObject:@{
                         @"url" : urlCandidate,
                         @"message" : @"Webview did not load blacklisted url."
                       }];
        }
        decisionHandler(WKNavigationActionPolicyCancel);
        [self _cleanupLoadingIndicator];
        return;
      }
    }
  }

  // Do not load the URL if it's on the block-list.
  if (_blockedURLs != nil && _blockedURLs.count > 0) {
    for (NSString *blockedURL in _blockedURLs) {
      if ([urlCandidate rangeOfString:blockedURL options:NSCaseInsensitiveSearch].location != NSNotFound) {
        if ([[self proxy] _hasListeners:@"blockedurl"]) {
          [[self proxy] fireEvent:@"blockedurl"
                       withObject:@{
                         @"url" : urlCandidate,
                         @"message" : @"Webview did not load blocked url."
                       }];
        }
        decisionHandler(WKNavigationActionPolicyCancel);
        [self _cleanupLoadingIndicator];
        return;
      }
    }
  }

  // Use "onlink" callback property to decide the navigation policy
  KrollWrapper *onLink = [[self proxy] valueForKey:@"onlink"];
  if (onLink != nil && navigationAction.navigationType == WKNavigationTypeLinkActivated) {
    JSValueRef functionResult = [onLink executeWithArguments:@[ @{ @"url" : navigationAction.request.URL.absoluteString } ]];
    if (functionResult != NULL && JSValueIsBoolean([onLink.bridge.krollContext context], functionResult)) {
      if (JSValueToBoolean([onLink.bridge.krollContext context], functionResult)) {
        [self fireBeforeLoad:navigationAction];
        decisionHandler(WKNavigationActionPolicyAllow);
      } else {
        decisionHandler(WKNavigationActionPolicyCancel);
      }
      return;
    }
  }

  [self fireBeforeLoad:navigationAction];

  NSString *scheme = [navigationAction.request.URL.scheme lowercaseString];

  if ([allowedURLSchemes containsObject:navigationAction.request.URL.scheme]) {
    if ([[UIApplication sharedApplication] canOpenURL:navigationAction.request.URL]) {
      // Event to return url to Titanium in order to handle OAuth and more
      if ([[self proxy] _hasListeners:@"handleurl"]) {
        TiThreadPerformOnMainThread(
            ^{
              [[self proxy] fireEvent:@"handleurl"
                           withObject:@{
                             @"url" : [TiUtils stringValue:[[navigationAction request] URL]],
                             @"handler" : [[[TiUIiOSWebViewDecisionHandlerProxy alloc] _initWithPageContext:[[self proxy] pageContext] andDecisionHandler:decisionHandler] autorelease]
                           }];
            },
            NO);
      } else {
        // DEPRECATED: Should use the "handleurl" event instead and call openURL on Ti.Platform.openURL instead
        DebugLog(@"[WARN] In iOS, please use the \"handleurl\" event together with \"allowedURLSchemes\" in Ti.UI.WebView.");
        DebugLog(@"[WARN] In iOS, it returns both the \"url\" and \"handler\" property to open a URL and invoke the decision-handler.");

        [UIApplication.sharedApplication openURL:navigationAction.request.URL options:@{} completionHandler:nil];
        decisionHandler(WKNavigationActionPolicyCancel);
      }
    }
  } else if (!([scheme hasPrefix:@"http"] || [scheme isEqualToString:@"ftp"] || [scheme isEqualToString:@"file"] || [scheme isEqualToString:@"app"]) && [[UIApplication sharedApplication] canOpenURL:navigationAction.request.URL]) {
    // Support tel: protocol
    [UIApplication.sharedApplication openURL:navigationAction.request.URL options:@{} completionHandler:nil];
    decisionHandler(WKNavigationActionPolicyCancel);
  } else {
    BOOL valid = !ignoreNextRequest;
    if ([scheme hasPrefix:@"http"]) {
      //UIWebViewNavigationTypeOther means we are either in a META redirect
      //or it is a js request from within the page
      valid = valid && (navigationAction.navigationType != WKNavigationTypeOther);
    }
    if (valid) {
      self.reloadData = navigationAction.request.URL.absoluteString;
      reloadMethod = @selector(setUrl_:);
    }
    decisionHandler(WKNavigationActionPolicyAllow);
  }
}

- (WKWebView *)webView:(WKWebView *)webView createWebViewWithConfiguration:(WKWebViewConfiguration *)configuration forNavigationAction:(WKNavigationAction *)navigationAction windowFeatures:(WKWindowFeatures *)windowFeatures
{
  if (!navigationAction.targetFrame.isMainFrame) {
    [webView loadRequest:navigationAction.request];
  }

  return nil;
}
#pragma mark Internal Utilities

static NSString *UIKitLocalizedString(NSString *string)
{
  NSBundle *UIKitBundle = [NSBundle bundleForClass:[UIApplication class]];
  return UIKitBundle ? [UIKitBundle localizedStringForKey:string value:string table:nil] : string;
}

- (void)_fireErrorEventWithError:(NSError *)error
{
  if ([[self proxy] _hasListeners:@"error"]) {
    NSURL *errorURL = _webView.URL;

    if (errorURL.absoluteString == nil) {
      errorURL = [NSURL URLWithString:[[error userInfo] objectForKey:NSURLErrorFailingURLStringErrorKey]];
    }

    [[self proxy] fireEvent:@"error"
                 withObject:@{
                   @"success" : @NO,
                   @"code" : @(error.code),
                   @"url" : NULL_IF_NIL(errorURL),
                   @"error" : [error localizedDescription]
                 }];
  }
}

- (void)_initializeLoadingIndicator
{
  BOOL hideLoadIndicator = [TiUtils boolValue:[self.proxy valueForKey:@"hideLoadIndicator"] def:NO];

  if ([[self class] _isLocalURL:_webView.URL] || hideLoadIndicator) {
    return;
  }

  TiColor *backgroundColor = [TiUtils colorValue:[self.proxy valueForKey:@"backgroundColor"]];
  UIActivityIndicatorViewStyle style = UIActivityIndicatorViewStyleGray;

  if (backgroundColor != nil && [Webcolor isDarkColor:backgroundColor.color]) {
    style = UIActivityIndicatorViewStyleWhite;
  }
  _loadingIndicator = [[UIActivityIndicatorView alloc] initWithActivityIndicatorStyle:style];
  [_loadingIndicator setHidesWhenStopped:YES];

  [self addSubview:_loadingIndicator];

  UIView *superview = self;
  NSDictionary *variables = NSDictionaryOfVariableBindings(_loadingIndicator, superview);
  NSArray<NSLayoutConstraint *> *verticalConstraints =
      [NSLayoutConstraint constraintsWithVisualFormat:@"V:[superview]-(<=1)-[_loadingIndicator]"
                                              options:NSLayoutFormatAlignAllCenterX
                                              metrics:nil
                                                views:variables];
  [self addConstraints:verticalConstraints];

  NSArray<NSLayoutConstraint *> *horizontalConstraints =
      [NSLayoutConstraint constraintsWithVisualFormat:@"H:[superview]-(<=1)-[_loadingIndicator]"
                                              options:NSLayoutFormatAlignAllCenterY
                                              metrics:nil
                                                views:variables];
  [self addConstraints:horizontalConstraints];
  [_loadingIndicator startAnimating];
}

- (void)_cleanupLoadingIndicator
{
  if (_loadingIndicator == nil)
    return;

  [UIView beginAnimations:@"_hideAnimation" context:nil];
  [UIView setAnimationDuration:0.3];
  [_loadingIndicator removeFromSuperview];
  [UIView commitAnimations];
  _loadingIndicator = nil;
}

+ (BOOL)_isLocalURL:(NSURL *)url
{
  NSString *scheme = [url scheme];
  return [scheme isEqualToString:@"file"] || [scheme isEqualToString:@"app"];
}

#pragma mark Layout helper

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

- (void)updateContentMode
{
  if ([self webView] != nil) {
    [[self webView] setContentMode:[self contentModeForWebView]];
  }
}

- (UIViewContentMode)contentModeForWebView
{
  if (TiDimensionIsAuto(width) || TiDimensionIsAutoSize(width) || TiDimensionIsUndefined(width) || TiDimensionIsAuto(height) || TiDimensionIsAutoSize(height) || TiDimensionIsUndefined(height)) {
    return UIViewContentModeScaleAspectFit;
  } else {
    return UIViewContentModeScaleToFill;
  }
}

- (void)frameSizeChanged:(CGRect)frame bounds:(CGRect)bounds
{
  for (UIView *child in [self subviews]) {
    [TiUtils setView:child positionRect:bounds];
  }

  [super frameSizeChanged:frame bounds:bounds];
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

  return 0;
}

- (UIViewContentMode)contentMode
{
  if (TiDimensionIsAuto(width) || TiDimensionIsAutoSize(width) || TiDimensionIsUndefined(width) || TiDimensionIsAuto(height) || TiDimensionIsAutoSize(height) || TiDimensionIsUndefined(height)) {
    return UIViewContentModeScaleAspectFit;
  } else {
    return UIViewContentModeScaleToFill;
  }
}

#pragma mark KVO

- (void)observeValueForKeyPath:(NSString *)keyPath ofObject:(id)object change:(NSDictionary *)change context:(void *)context
{
  if ([keyPath isEqualToString:@"estimatedProgress"] && object == [self webView]) {
    if ([[self proxy] _hasListeners:@"progress"]) {
      [[self proxy] fireEvent:@"progress"
                   withObject:@{
                     @"value" : NUMDOUBLE([[self webView] estimatedProgress]),
                     @"url" : [[[self webView] URL] absoluteString] ?: [[NSBundle mainBundle] bundlePath]
                   }];
    }
  } else {
    [super observeValueForKeyPath:keyPath ofObject:object change:change context:context];
  }
}

#pragma mark Cookie Utility

/*
 To support cookie for iOS <11
 https://stackoverflow.com/questions/26573137
 https://github.com/haifengkao/YWebView
 */

- (NSDictionary *)cookieMapForString:(NSString *)cokieStr
{
  NSMutableDictionary *cookieMap = [NSMutableDictionary dictionary];

  NSArray *cookieKeyValueStrings = [cokieStr componentsSeparatedByString:@";"];
  for (NSString *cookieKeyValueString in cookieKeyValueStrings) {
    //Find the position of the first "="
    NSRange separatorRange = [cookieKeyValueString rangeOfString:@"="];

    if (separatorRange.location != NSNotFound && separatorRange.location > 0 && separatorRange.location < ([cookieKeyValueString length] - 1)) {
      //The above conditions ensure that there is content before and after "=", and the key or value is not empty.

      NSRange keyRange = NSMakeRange(0, separatorRange.location);
      NSString *key = [cookieKeyValueString substringWithRange:keyRange];
      NSString *value = [cookieKeyValueString substringFromIndex:separatorRange.location + separatorRange.length];

      key = [key stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
      value = [value stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
      [cookieMap setObject:value forKey:key];
    }
  }
  return cookieMap;
}

- (NSDictionary *)cookiePropertiesForString:(NSString *)cookieStr
{
  NSDictionary *cookieMap = [self cookieMapForString:cookieStr];

  NSMutableDictionary *cookieProperties = [NSMutableDictionary dictionary];
  for (NSString *key in [cookieMap allKeys]) {

    NSString *value = [cookieMap objectForKey:key];
    NSString *uppercaseKey = [key uppercaseString]; //Mainly to eliminate the problem of naming irregularities

    if ([uppercaseKey isEqualToString:@"DOMAIN"]) {
      if (![value hasPrefix:@"."] && ![value hasPrefix:@"www"]) {
        value = [NSString stringWithFormat:@".%@", value];
      }
      [cookieProperties setObject:value forKey:NSHTTPCookieDomain];
    } else if ([uppercaseKey isEqualToString:@"VERSION"]) {
      [cookieProperties setObject:value forKey:NSHTTPCookieVersion];
    } else if ([uppercaseKey isEqualToString:@"MAX-AGE"] || [uppercaseKey isEqualToString:@"MAXAGE"]) {
      [cookieProperties setObject:value forKey:NSHTTPCookieMaximumAge];
    } else if ([uppercaseKey isEqualToString:@"PATH"]) {
      [cookieProperties setObject:value forKey:NSHTTPCookiePath];
    } else if ([uppercaseKey isEqualToString:@"ORIGINURL"]) {
      [cookieProperties setObject:value forKey:NSHTTPCookieOriginURL];
    } else if ([uppercaseKey isEqualToString:@"PORT"]) {
      [cookieProperties setObject:value forKey:NSHTTPCookiePort];
    } else if ([uppercaseKey isEqualToString:@"SECURE"] || [uppercaseKey isEqualToString:@"ISSECURE"]) {
      [cookieProperties setObject:value forKey:NSHTTPCookieSecure];
    } else if ([uppercaseKey isEqualToString:@"COMMENT"]) {
      [cookieProperties setObject:value forKey:NSHTTPCookieComment];
    } else if ([uppercaseKey isEqualToString:@"COMMENTURL"]) {
      [cookieProperties setObject:value forKey:NSHTTPCookieCommentURL];
    } else if ([uppercaseKey isEqualToString:@"EXPIRES"]) {
      NSDateFormatter *dateFormatter = [[NSDateFormatter alloc] init];
      [dateFormatter setDateFormat:@"yyyy-MM-dd'T'HH:mm:ss'.'SSS+0000"];
      [cookieProperties setObject:[dateFormatter dateFromString:value] forKey:NSHTTPCookieExpires];
    } else if ([uppercaseKey isEqualToString:@"DISCART"]) {
      [cookieProperties setObject:value forKey:NSHTTPCookieDiscard];
    } else if ([uppercaseKey isEqualToString:@"NAME"]) {
      [cookieProperties setObject:value forKey:NSHTTPCookieName];
    } else if ([uppercaseKey isEqualToString:@"VALUE"]) {
      [cookieProperties setObject:value forKey:NSHTTPCookieValue];
    } else {
      [cookieProperties setObject:key forKey:NSHTTPCookieName];
      [cookieProperties setObject:value forKey:NSHTTPCookieValue];
    }
  }

  //Since the cookieWithProperties: method properties can not be without NSHTTPCookiePath, so you need to confirm this, if not, the default is "/"
  if (![cookieProperties objectForKey:NSHTTPCookiePath]) {
    [cookieProperties setObject:@"/" forKey:NSHTTPCookiePath];
  }
  return cookieProperties;
}

- (NSHTTPCookie *)cookieForString:(NSString *)cookieStr
{
  NSDictionary *cookieProperties = [self cookiePropertiesForString:cookieStr];
  NSHTTPCookie *cookie = [NSHTTPCookie cookieWithProperties:cookieProperties];
  return cookie;
}

@end

@implementation WebAppProtocolHandler

+ (NSString *)specialProtocolScheme
{
  return @"app";
}

- (void)webView:(WKWebView *)webView startURLSchemeTask:(id<WKURLSchemeTask>)urlSchemeTask
{
  NSURLRequest *request = [urlSchemeTask request];
  NSURL *url = [request URL];
  DebugLog(@"[DEBUG] Requested resource via app protocol, loading: %@", url);

  // see if it's a compiled resource
  NSData *data = [TiUtils loadAppResource:url];
  if (data == nil) {
    // check to see if it's a local resource in the bundle, could be
    // a bundled image, etc. - or we could be running from XCode :)
    NSString *urlpath = [url path];
    if ([urlpath characterAtIndex:0] == '/') {
      if ([[NSFileManager defaultManager] fileExistsAtPath:urlpath]) {
        data = [[[NSData alloc] initWithContentsOfFile:urlpath] autorelease];
      }
    }
    if (data == nil) {
      NSString *resourceurl = [TiHost resourcePath];
      NSString *path = [NSString stringWithFormat:@"%@%@", resourceurl, urlpath];
      data = [[[NSData alloc] initWithContentsOfFile:path] autorelease];
    }
  }

  if (data != nil) {
    NSURLCacheStoragePolicy caching = NSURLCacheStorageAllowedInMemoryOnly;
    NSString *mime = [Mimetypes mimeTypeForExtension:[url path]];
    NSURLResponse *response = [[NSURLResponse alloc] initWithURL:url MIMEType:mime expectedContentLength:[data length] textEncodingName:@"utf-8"];
    [urlSchemeTask didReceiveResponse:response];
    [urlSchemeTask didReceiveData:data];
    [urlSchemeTask didFinish];
    [response release];
  } else {
    NSLog(@"[ERROR] Error loading %@", url);
    [urlSchemeTask didFailWithError:[NSError errorWithDomain:NSURLErrorDomain code:NSURLErrorResourceUnavailable userInfo:nil]];
  }
}

- (void)webView:(nonnull WKWebView *)webView stopURLSchemeTask:(nonnull id<WKURLSchemeTask>)urlSchemeTask
{
}

@end

#endif
