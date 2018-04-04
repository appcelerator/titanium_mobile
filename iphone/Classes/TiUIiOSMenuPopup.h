/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2015 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UIIOSMENUPOPUP
#import <TitaniumKit/TiUIView.h>
#import <UIKit/UIKit.h>

#import "TiUIiOSMenuPopupProxy.h"

@interface TiUIiOSMenuPopup : TiUIView

/**
 Shows the menu popup.
 */
- (void)show:(id)args;

/**
 Hides the menu popup.
 */
- (void)hide:(id)args;

@end
#endif
