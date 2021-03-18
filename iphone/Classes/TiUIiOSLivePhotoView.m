/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2015 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#ifdef USE_TI_UIIOSLIVEPHOTOVIEW
#import "TiUIiOSLivePhotoView.h"
#import "TiUIiOSLivePhotoViewProxy.h"
#import <TitaniumKit/TiViewProxy.h>

@implementation TiUIiOSLivePhotoView

#ifdef TI_USE_AUTOLAYOUT
- (void)initializeTiLayoutView
{
  [super initializeTiLayoutView];
  [self setDefaultHeight:TiDimensionAutoSize];
  [self setDefaultWidth:TiDimensionAutoSize];
}
#endif

- (TiUIiOSLivePhotoViewProxy *)livePhotoViewProxy
{
  return (TiUIiOSLivePhotoViewProxy *)self.proxy;
}

- (PHLivePhotoView *)livePhotoView
{
  if (_livePhotoView == nil) {
    _livePhotoView = [[PHLivePhotoView alloc] initWithFrame:[self bounds]];
    [_livePhotoView setDelegate:self];

    [_livePhotoView setAutoresizingMask:UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight];
    [_livePhotoView setContentMode:[self contentModeForLivePhotoView]];

    [self addSubview:_livePhotoView];
  }

  return _livePhotoView;
}

#pragma mark Cleanup

- (void)dealloc
{
  RELEASE_TO_NIL(_livePhotoView);

  [super dealloc];
}

#pragma mark Public APIs

- (void)setLivePhoto_:(id)arg
{
  ENSURE_UI_THREAD(setLivePhoto_, arg);
  ENSURE_TYPE(arg, TiUIiOSLivePhoto);

  PHLivePhoto *livePhoto = [arg livePhoto];

  autoWidth = livePhoto.size.width;
  autoHeight = livePhoto.size.height;

  [[self livePhotoView] setLivePhoto:livePhoto];
  [(TiViewProxy *)self.proxy relayout];
}

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

#pragma mark Layout helper

- (void)updateContentMode
{
  if (_livePhotoView != nil) {
    [_livePhotoView setContentMode:[self contentModeForLivePhotoView]];
  }
}

- (UIViewContentMode)contentModeForLivePhotoView
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

#pragma mark Delegates

- (void)livePhotoView:(PHLivePhotoView *)livePhotoView willBeginPlaybackWithStyle:(PHLivePhotoViewPlaybackStyle)playbackStyle
{
  if ([[self livePhotoViewProxy] _hasListeners:@"start"]) {
    NSDictionary *event = @{ @"playbackStyle" : @(playbackStyle) };
    [[self livePhotoViewProxy] fireEvent:@"start" withObject:event];
  }
}

- (void)livePhotoView:(PHLivePhotoView *)livePhotoView didEndPlaybackWithStyle:(PHLivePhotoViewPlaybackStyle)playbackStyle
{
  if ([[self livePhotoViewProxy] _hasListeners:@"stop"]) {
    NSDictionary *event = @{ @"playbackStyle" : @(playbackStyle) };
    [[self livePhotoViewProxy] fireEvent:@"stop" withObject:event];
  }
}

@end
#endif
