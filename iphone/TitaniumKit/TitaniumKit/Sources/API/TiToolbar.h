/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#import <TitaniumKit/TiViewProxy.h>

// marker interface for toolbars

/**
 Protocol for toolbar classes.
 */
@protocol TiToolbar <NSObject>
@required

/**
 Returns the underlying toolbar.
 @return The toolbar.
 */
- (UIToolbar *)toolbar;

@end
