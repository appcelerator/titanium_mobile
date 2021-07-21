/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2019 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UIWEBVIEW

@import TitaniumKit.TiBlob;
@import TitaniumKit.TiHost;
@import TitaniumKit.TiUtils;

#import "TiUIWebViewProxy.h"
#import "TiNetworkCookieProxy.h"
#import "TiUIWebView.h"

@implementation TiUIWebViewProxy

static NSArray *webViewKeySequence;

#pragma mark Internal

- (NSArray *)keySequence
{
  if (webViewKeySequence == nil) {
    webViewKeySequence = [[NSArray arrayWithObjects:@"assetsDirectory", @"url", nil] retain];
  }
  return webViewKeySequence;
}

- (id)_initWithPageContext:(id<TiEvaluator>)context
{
  if (self = [super _initWithPageContext:context]) {
  }

  return self;
}

- (TiUIWebView *)webView
{
  return (TiUIWebView *)self.view;
}

- (void)dealloc
{
  RELEASE_TO_NIL(_allowedURLSchemes);
  RELEASE_TO_NIL(_genericProperties);
  [super dealloc];
}

- (void)fireEvent:(id)listener withObject:(id)obj remove:(BOOL)yn thisObject:(id)thisObject_
{
  TiThreadPerformOnMainThread(
      ^{
        [[self webView] fireEvent:listener withObject:obj remove:yn thisObject:thisObject_];
      },
      NO);
}

- (void)setPageToken:(NSString *)pageToken
{
  if (_pageToken != nil) {
    [[self host] unregisterContext:(id<TiEvaluator>)self forToken:_pageToken];
    _pageToken = nil;
  }
  _pageToken = pageToken;
  [[self host] registerContext:self forToken:_pageToken];
}

- (void)refreshHTMLContent
{
  NSString *code = @"document.documentElement.outerHTML.toString()";

  // Refresh the "html" property async to be able to use the remote HTML content.
  // This should be deprecated asap, since it is an overhead that should be done using
  // webView.evalJS() within the app if required.
  [[self wkWebView] evaluateJavaScript:code
                     completionHandler:^(id result, NSError *error) {
                       if (error != nil) {
                         return;
                       }
                       [self replaceValue:result forKey:@"html" notification:NO];
                     }];
}

- (void)windowWillClose
{
}

- (void)windowDidClose
{
  if (_pageToken != nil) {
    [[self host] unregisterContext:(id<TiEvaluator>)self forToken:_pageToken];
    _pageToken = nil;
  }

  [(TiUIWebView *)[self view] viewDidClose];

  NSNotification *notification = [NSNotification notificationWithName:kTiContextShutdownNotification object:self];
  WARN_IF_BACKGROUND_THREAD_OBJ;
  [[NSNotificationCenter defaultCenter] postNotification:notification];
  [super windowDidClose];
}

- (void)_destroy
{
  if (_pageToken != nil) {
    [[self host] unregisterContext:(id<TiEvaluator>)self forToken:_pageToken];
    _pageToken = nil;
  }
  [super _destroy];
}

- (WKWebView *)wkWebView
{
  return (WKWebView *)[[self webView] webView];
}

#pragma mark - TiEvaluator Protocol

- (TiHost *)host
{
  return [self _host];
}

- (NSString *)basename
{
  return nil;
}

- (NSURL *)currentURL
{
  return nil;
}

- (void)setCurrentURL:(NSURL *)unused
{
}

- (void)evalFile:(NSString *)path
{
  NSURL *url_ = [path hasPrefix:@"file:"] ? [NSURL URLWithString:path] : [NSURL fileURLWithPath:path];

  if (![path hasPrefix:@"/"] && ![path hasPrefix:@"file:"]) {
    NSURL *root = [[self _host] baseURL];
    url_ = [NSURL fileURLWithPath:[NSString stringWithFormat:@"%@/%@", root, path]];
  }

  NSString *code = [NSString stringWithContentsOfURL:url_ encoding:NSUTF8StringEncoding error:nil];
  [[self wkWebView] evaluateJavaScript:code
                     completionHandler:^(id _Nullablecresult, NSError *_Nullable error){
                     }];
}

- (NSString *)evalJSAndWait:(NSString *)code
{
  return [self evalJSSync:code];
}

- (void)evalJSWithoutResult:(NSString *)code
{
  [self evalJS:code];
}

- (BOOL)evaluationError
{
  return NO;
}

- (KrollContext *)krollContext
{
  return nil;
}

- (id)krollObjectForProxy:(id)proxy
{
  return nil;
}

- (id)preloadForKey:(id)key name:(id)name
{
  return nil;
}

- (id)registerProxy:(id)proxy
{
  return nil;
}

- (void)unregisterProxy:(id)proxy
{
}

- (BOOL)usesProxy:(id)proxy
{
  return NO;
}

#pragma mark - Public APIs

#pragma mark Getters

- (NSNumber *)disableBounce
{
  return @(![[[self wkWebView] scrollView] bounces]);
}

- (NSNumber *)scrollsToTop
{
  return @([[[self wkWebView] scrollView] scrollsToTop]);
}

- (NSNumber *)allowsBackForwardNavigationGestures
{
  return @([[self wkWebView] allowsBackForwardNavigationGestures]);
}

- (NSString *)userAgent
{
  return [[self wkWebView] customUserAgent];
}

- (NSString *)url
{
  return [[[self wkWebView] URL] absoluteString];
}

- (NSString *)title
{
  return [[self wkWebView] title];
}

- (NSNumber *)progress
{
  return @([[self wkWebView] estimatedProgress]);
}

- (NSNumber *)secure
{
  return @([[self wkWebView] hasOnlySecureContent]);
}

- (NSDictionary *)backForwardList
{
  WKBackForwardList *list = [[self wkWebView] backForwardList];

  NSMutableArray *backList = [NSMutableArray arrayWithCapacity:list.backList.count];
  NSMutableArray *forwardList = [NSMutableArray arrayWithCapacity:list.forwardList.count];

  for (WKBackForwardListItem *item in list.backList) {
    [backList addObject:[[self class] _dictionaryFromBackForwardItem:item]];
  }

  for (WKBackForwardListItem *item in list.forwardList) {
    [forwardList addObject:[[self class] _dictionaryFromBackForwardItem:item]];
  }

  return @{
    @"currentItem" : [[self class] _dictionaryFromBackForwardItem:[list currentItem]],
    @"backItem" : [[self class] _dictionaryFromBackForwardItem:[list backItem]],
    @"forwardItem" : [[self class] _dictionaryFromBackForwardItem:[list forwardItem]],
    @"backList" : backList,
    @"forwardList" : forwardList
  };
}

- (NSDictionary *)preferences
{
  return @{
    @"minimumFontSize" : NUMFLOAT([[[[self wkWebView] configuration] preferences] minimumFontSize]),
    @"javaScriptEnabled" : NUMBOOL([[[[self wkWebView] configuration] preferences] javaScriptEnabled]),
    @"javaScriptCanOpenWindowsAutomatically" : NUMBOOL([[[[self wkWebView] configuration] preferences] javaScriptCanOpenWindowsAutomatically]),
  };
}

- (NSNumber *)selectionGranularity
{
  return @([[[self wkWebView] configuration] selectionGranularity]);
}

- (NSNumber *)mediaTypesRequiringUserActionForPlayback
{
  return @([[[self wkWebView] configuration] mediaTypesRequiringUserActionForPlayback]);
}

- (NSNumber *)suppressesIncrementalRendering
{
  return @([[[self wkWebView] configuration] suppressesIncrementalRendering]);
}

- (NSNumber *)allowsInlineMediaPlayback
{
  return @([[[self wkWebView] configuration] allowsInlineMediaPlayback]);
}

- (NSNumber *)allowsAirPlayMediaPlayback
{
  return @([[[self wkWebView] configuration] allowsAirPlayForMediaPlayback]);
}

- (NSNumber *)allowsPictureInPictureMediaPlayback
{
  return @([[[self wkWebView] configuration] allowsPictureInPictureMediaPlayback]);
}

- (NSArray<NSString *> *)allowedURLSchemes
{
  return _allowedURLSchemes;
}

- (NSNumber *)zoomLevel
{
#if IS_SDK_IOS_14
  if ([TiUtils isIOSVersionOrGreater:@"14.0"]) {
    return @([self wkWebView].pageZoom);
  }
#endif

  NSString *zoomLevel = [self evalJS:@[ @"document.body.style.zoom" ]];

  if (zoomLevel == nil || zoomLevel.length == 0) {
    return @(1.0);
  }

  return @([zoomLevel doubleValue]);
}

#pragma mark Setter

- (void)setAllowedURLSchemes:(NSArray *)schemes
{
  RELEASE_TO_NIL(_allowedURLSchemes);
  for (id scheme in schemes) {
    ENSURE_TYPE(scheme, NSString);
  }

  _allowedURLSchemes = [[NSArray arrayWithArray:schemes] retain];
}

- (void)setBasicAuthentication:(id)value
{
  // This was a regression between 7.x and 8.0.0. It should be removed in later versions
  if ([value isKindOfClass:[NSDictionary class]]) {
    NSLog(@"[WARN] Please pass the basicAuthentication parameters as function arguments, e.g. \"webView.setBasicAuthentication(username, password, persistence)\"");
    [self replaceValue:value forKey:@"basicAuthentication" notification:NO];
    return;
  }

  NSString *username = value[0];
  NSString *password = value[1];
  NSURLCredentialPersistence persistence = NSURLCredentialPersistenceNone;

  if ([value count] == 3) {
    persistence = [TiUtils intValue:value[2] def:NSURLCredentialPersistenceNone];
  }

  NSDictionary *params = @{ @"username" : username,
    @"password" : password,
    @"persistence" : @(persistence) };
  [self replaceValue:params forKey:@"basicAuthentication" notification:NO];
}

- (void)setHtml:(id)args
{
  [[self webView] setHtml_:args];
}

#pragma mark Methods

- (void)addUserScript:(id)args
{
  ENSURE_SINGLE_ARG(args, NSDictionary);

  NSString *source = [TiUtils stringValue:@"source" properties:args];
  WKUserScriptInjectionTime injectionTime = [TiUtils intValue:@"injectionTime" properties:args def:WKUserScriptInjectionTimeAtDocumentStart];
  BOOL mainFrameOnly = [TiUtils boolValue:@"mainFrameOnly" properties:args];

  WKUserScript *script = [[WKUserScript alloc] initWithSource:source injectionTime:injectionTime forMainFrameOnly:mainFrameOnly];
  WKUserContentController *controller = [[[self wkWebView] configuration] userContentController];
  [controller addUserScript:script];
}

- (void)removeAllUserScripts:(id)unused
{
  WKUserContentController *controller = [[[self wkWebView] configuration] userContentController];
  [controller removeAllUserScripts];
}

- (void)addScriptMessageHandler:(id)value
{
  ENSURE_SINGLE_ARG(value, NSString);

  WKUserContentController *controller = [[[self wkWebView] configuration] userContentController];
  [controller addScriptMessageHandler:[self webView] name:value];
}

- (void)removeScriptMessageHandler:(id)value
{
  ENSURE_SINGLE_ARG(value, NSString);

  WKUserContentController *controller = [[[self wkWebView] configuration] userContentController];
  [controller removeScriptMessageHandlerForName:value];
}

- (void)stopLoading:(id)unused
{
  [[self wkWebView] stopLoading];
}

- (void)reload:(id)unused
{
  TiThreadPerformOnMainThread(
      ^{
        [[self webView] reload];
      },
      NO);
}

- (void)repaint:(id)unused
{
  [self contentsWillChange];
}

- (void)goBack:(id)unused
{
  [[self wkWebView] goBack];
}

#if IS_SDK_IOS_14
- (void)findString:(id)args
{
  NSString *searchString = [args objectAtIndex:0];
  ENSURE_STRING(searchString);
  KrollCallback *callback;
  NSDictionary *options = nil;
  if ([args count] > 2) {
    options = [args objectAtIndex:1];
    ENSURE_DICT(options);
    callback = [args objectAtIndex:2];
  } else {
    callback = [args objectAtIndex:1];
  }
  ENSURE_TYPE(callback, KrollCallback);

  if (![TiUtils isIOSVersionOrGreater:@"14.0"]) {
    [callback call:@[ @{ @"success" : NUMBOOL(NO), @"error" : @"Supported on iOS 14+" } ] thisObject:self];
    return;
  }

  WKFindConfiguration *configuration = [[WKFindConfiguration alloc] init];
  configuration.backwards = [TiUtils boolValue:@"backwards" properties:options def:NO];
  configuration.caseSensitive = [TiUtils boolValue:@"caseSensitive" properties:options def:NO];
  configuration.wraps = [TiUtils boolValue:@"wraps" properties:options def:YES];

  [[self wkWebView] findString:searchString
             withConfiguration:configuration
             completionHandler:^(WKFindResult *_Nonnull result) {
               [callback call:@[ @{ @"success" : NUMBOOL(result.matchFound) } ] thisObject:self];
             }];
}

- (void)createPDF:(id)args
{
  KrollCallback *callback = (KrollCallback *)[args objectAtIndex:0];
  ENSURE_TYPE(callback, KrollCallback);

  if (![TiUtils isIOSVersionOrGreater:@"14.0"]) {
    [callback call:@[ @{ @"success" : NUMBOOL(NO), @"error" : @"Supported on iOS 14+" } ] thisObject:self];
    return;
  }

  [[self wkWebView] createPDFWithConfiguration:nil
                             completionHandler:^(NSData *_Nullable pdfDocumentData, NSError *_Nullable error) {
                               if (error != nil) {
                                 [callback call:@[ @{ @"success" : NUMBOOL(NO), @"error" : error.localizedDescription } ] thisObject:self];
                                 return;
                               }
                               TiBlob *blob = [[[TiBlob alloc] initWithData:pdfDocumentData mimetype:@"application/pdf"] autorelease];
                               [callback call:@[ @{ @"success" : NUMBOOL(YES), @"data" : blob } ] thisObject:self];
                             }];
}

- (void)createWebArchive:(id)args
{
  KrollCallback *callback = (KrollCallback *)[args objectAtIndex:0];
  ENSURE_TYPE(callback, KrollCallback);

  if (![TiUtils isIOSVersionOrGreater:@"14.0"]) {
    [callback call:@[ @{ @"success" : NUMBOOL(NO), @"error" : @"Supported on iOS 14+" } ] thisObject:self];
    return;
  }

  [[self wkWebView] createWebArchiveDataWithCompletionHandler:^(NSData *_Nonnull archiveData, NSError *_Nonnull error) {
    if (error != nil) {
      [callback call:@[ @{ @"success" : NUMBOOL(NO), @"error" : error.localizedDescription } ] thisObject:self];
      return;
    }

    TiBlob *blob = [[[TiBlob alloc] initWithData:archiveData mimetype:@"application/x-webarchive"] autorelease];
    [callback call:@[ @{ @"success" : NUMBOOL(YES), @"data" : blob } ] thisObject:self];
  }];
}
#endif

- (void)goForward:(id)unused
{
  [[self wkWebView] goForward];
}

- (NSNumber *)canGoBack:(id)unused
{
  return NUMBOOL([[self wkWebView] canGoBack]);
}

- (NSNumber *)canGoForward:(id)unused
{
  return NUMBOOL([[self wkWebView] canGoForward]);
}

- (NSNumber *)loading
{
  return @([[self wkWebView] isLoading]);
}

- (void)startListeningToProperties:(id)args
{
  ENSURE_SINGLE_ARG(args, NSArray);

  if (_genericProperties == nil) {
    _genericProperties = [[NSMutableArray alloc] init];
  }
  for (id property in args) {
    ENSURE_TYPE(property, NSString);
    [_genericProperties addObject:property];
    [[self wkWebView] addObserver:self forKeyPath:property options:NSKeyValueObservingOptionNew context:NULL];
  }
}

- (void)stopListeningToProperties:(id)args
{
  ENSURE_SINGLE_ARG(args, NSArray);

  for (id property in args) {
    ENSURE_TYPE(property, NSString);

    [[self wkWebView] removeObserver:self forKeyPath:property];
    [_genericProperties removeObject:property];
  }
}

- (id)evalJS:(id)args
{
  NSString *code = nil;
  KrollCallback *callback = nil;

  ENSURE_ARG_AT_INDEX(code, args, 0, NSString);
  ENSURE_ARG_OR_NIL_AT_INDEX(callback, args, 1, KrollCallback);

  if (code == nil) {
    [self throwException:@"Missing JavaScript code"
               subreason:@"The required first argument is missinf and should contain a valid JavaScript string."
                location:CODELOCATION];
    return nil;
  }

  // If no argument is passed, return in sync (NOT recommended)
  if (callback == nil) {
    __block id result = nil;
    TiThreadPerformOnMainThread(
        ^{
          result = [[self evalJSSync:@[ code ]] retain];
        },
        YES);
    return [result autorelease];
  }

  TiThreadPerformOnMainThread(
      ^{
        [[self wkWebView] evaluateJavaScript:code
                           completionHandler:^(id result, NSError *error) {
                             if (!callback) {
                               return;
                             }
                             result = result ?: [NSNull null];
                             [callback call:@[ result ] thisObject:self];
                           }];
      },
      NO);
  return nil;
}

- (NSString *)evalJSSync:(id)args
{
  NSString *code = nil;

  __block NSString *resultString = nil;
  __block BOOL finishedEvaluation = NO;

  ENSURE_ARG_AT_INDEX(code, args, 0, NSString);

  [[self wkWebView] evaluateJavaScript:code
                     completionHandler:^(id result, NSError *error) {
                       resultString = [NULL_IF_NIL(result) retain];
                       finishedEvaluation = YES;
                     }];

  while (!finishedEvaluation) {
    [[NSRunLoop currentRunLoop] runMode:NSDefaultRunLoopMode beforeDate:[NSDate distantFuture]];
  }

  return [resultString autorelease];
}

- (void)takeSnapshot:(id)args
{
  KrollCallback *callback = (KrollCallback *)[args objectAtIndex:0];
  ENSURE_TYPE(callback, KrollCallback);

  [[self wkWebView] takeSnapshotWithConfiguration:nil
                                completionHandler:^(UIImage *snapshotImage, NSError *error) {
                                  if (error != nil) {
                                    [callback call:@[ @{ @"success" : NUMBOOL(NO), @"error" : error.localizedDescription } ] thisObject:self];
                                    return;
                                  }

                                  [callback call:@[ @{ @"success" : NUMBOOL(YES), @"snapshot" : [[TiBlob alloc] initWithImage:snapshotImage] } ] thisObject:self];
                                }];
}

#pragma mark Utilities

+ (NSDictionary *)_dictionaryFromBackForwardItem:(WKBackForwardListItem *)item
{
  if (item == nil) {
    return @{};
  }
  return @{ @"url" : item.URL.absoluteString, @"initialUrl" : item.initialURL.absoluteString, @"title" : item.title };
}

#pragma mark Generic KVO

- (void)observeValueForKeyPath:(NSString *)keyPath ofObject:(id)object change:(NSDictionary *)change context:(void *)context
{
  for (NSString *property in _genericProperties) {
    if ([self _hasListeners:property] && [keyPath isEqualToString:property] && object == [self wkWebView]) {
      [self fireEvent:property withObject:@{ @"value" : NULL_IF_NIL([[self wkWebView] valueForKey:property]) }];
      return;
    }
  }
}

#pragma mark Cookies

- (id<TiEvaluator>)evaluationContext
{
  id<TiEvaluator> context = [self executionContext];
  if (context == nil) {
    context = [self pageContext];
  }
  return context;
}

- (WKHTTPCookieStore *)cookieStore
{
  return [self wkWebView].configuration.websiteDataStore.httpCookieStore;
}

- (NSArray *)cookiesForDomain:(id)args
{
  ENSURE_SINGLE_ARG(args, NSString);
  __block NSMutableArray *returnArray = [NSMutableArray array];
  __block BOOL finishedEvaluation = NO;
  [[self cookieStore] getAllCookies:^(NSArray<NSHTTPCookie *> *cookies) {
    NSMutableArray *allCookies = [NSMutableArray array];
    for (NSHTTPCookie *cookie in cookies) {
      if ([[cookie domain] isEqualToString:args]) {
        [allCookies addObject:cookie];
      }
    }
    for (NSHTTPCookie *cookie in allCookies) {
      [returnArray addObject:[[[TiNetworkCookieProxy alloc] initWithCookie:cookie andPageContext:[self evaluationContext]] autorelease]];
    }
    finishedEvaluation = YES;
  }];
  while (!finishedEvaluation) {
    [[NSRunLoop currentRunLoop] runMode:NSDefaultRunLoopMode beforeDate:[NSDate distantFuture]];
  }
  return returnArray;
}

- (void)addCookie:(id)args
{
  ENSURE_SINGLE_ARG(args, TiNetworkCookieProxy);

  NSHTTPCookie *cookie = [args newCookie];
  if (cookie != nil) {
    [[self cookieStore] setCookie:cookie completionHandler:nil];
  }
  RELEASE_TO_NIL(cookie);
}

- (NSArray *)getCookies:(id)args
{
  NSString *domain = [TiUtils stringValue:[args objectAtIndex:0]];
  NSString *path = [TiUtils stringValue:[args objectAtIndex:1]];
  NSString *name = [TiUtils stringValue:[args objectAtIndex:2]];
  if (path == nil || [path isEqual:@""]) {
    path = @"/";
  }

  __block NSMutableArray *returnArray = [NSMutableArray array];
  __block BOOL finishedEvaluation = NO;

  [[self cookieStore] getAllCookies:^(NSArray<NSHTTPCookie *> *cookies) {
    for (NSHTTPCookie *cookie in cookies) {
      if ([[cookie domain] isEqualToString:domain] &&
          [[cookie path] isEqualToString:path] && ([[cookie name] isEqualToString:name] || name == nil)) {
        TiNetworkCookieProxy *tempCookieProxy = [[TiNetworkCookieProxy alloc] initWithCookie:cookie andPageContext:[self evaluationContext]];
        [returnArray addObject:tempCookieProxy];
        RELEASE_TO_NIL(tempCookieProxy);
      }
    }
    finishedEvaluation = YES;
  }];
  while (!finishedEvaluation) {
    [[NSRunLoop currentRunLoop] runMode:NSDefaultRunLoopMode beforeDate:[NSDate distantFuture]];
  }
  return returnArray;
}

- (void)removeAllCookies:(id)args
{
  __block BOOL finishedEvaluation = NO;
  [[self cookieStore] getAllCookies:^(NSArray<NSHTTPCookie *> *cookies) {
    for (NSHTTPCookie *cookie in cookies) {
      [[self cookieStore] deleteCookie:cookie completionHandler:nil];
    }
    finishedEvaluation = YES;
  }];
  while (!finishedEvaluation) {
    [[NSRunLoop currentRunLoop] runMode:NSDefaultRunLoopMode beforeDate:[NSDate distantFuture]];
  }
}

- (void)removeCookie:(id)args
{
  NSArray *cookies = [self getCookies:args];
  for (TiNetworkCookieProxy *cookie in cookies) {
    NSHTTPCookie *tempCookie = [cookie newCookie];
    [[self cookieStore] deleteCookie:tempCookie completionHandler:nil];
    RELEASE_TO_NIL(tempCookie);
  }
}

- (void)removeCookiesForDomain:(id)args
{
  NSArray *cookies = [self cookiesForDomain:args];
  for (TiNetworkCookieProxy *cookie in cookies) {
    NSHTTPCookie *tempCookie = [cookie newCookie];
    [[self cookieStore] deleteCookie:tempCookie completionHandler:nil];
    RELEASE_TO_NIL(tempCookie);
  }
}

- (NSArray *)allCookies
{
  __block NSMutableArray *returnArray = [NSMutableArray array];
  __block BOOL finishedEvaluation = NO;

  [[self cookieStore] getAllCookies:^(NSArray<NSHTTPCookie *> *cookies) {
    for (NSHTTPCookie *cookie in cookies) {
      [returnArray addObject:[[[TiNetworkCookieProxy alloc] initWithCookie:cookie andPageContext:[self evaluationContext]] autorelease]];
    }
    finishedEvaluation = YES;
  }];
  while (!finishedEvaluation) {
    [[NSRunLoop currentRunLoop] runMode:NSDefaultRunLoopMode beforeDate:[NSDate distantFuture]];
  }
  return returnArray;
}

@end
#endif
