/**
* Appcelerator Titanium Mobile
* Copyright (c) 2018-Present by Appcelerator, Inc. All Rights Reserved.
* Licensed under the terms of the Apache Public License
* Please see the LICENSE included with this distribution for details.
*/
@import JavaScriptCore;

NS_ASSUME_NONNULL_BEGIN

@interface JSValue (Addons)

- (BOOL)isFunction;

- (void)defineReadOnlyProperty:(NSString *)propertyName withValue:(id)value;

@end

NS_ASSUME_NONNULL_END
