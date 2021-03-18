/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#import "ObjcModule.h"
@import JavaScriptCore;

@protocol TiExports <JSExport>

// Properties (and accessors)
READONLY_PROPERTY(NSString *, buildDate, BuildDate);
READONLY_PROPERTY(NSString *, buildHash, BuildHash);
READONLY_PROPERTY(NSString *, version, Version);
@property (nonatomic, assign) NSString *userAgent;

// Methods
- (JSValue *)createBuffer:(NSDictionary *)arg;

@end

@interface TopTiModule : ObjcModule <TiExports>
@end
