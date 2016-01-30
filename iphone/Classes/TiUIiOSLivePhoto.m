/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2015 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#ifdef USE_TI_UIIOSLIVEPHOTOVIEW
#import "TiUIiOSLivePhoto.h"

@implementation TiUIiOSLivePhoto

-(instancetype)initWithLivePhoto:(PHLivePhoto*)livePhoto
{
    if(self = [self init]) {
        [self setLivePhoto:livePhoto];
    }
    
    return self;
}

@end
#endif