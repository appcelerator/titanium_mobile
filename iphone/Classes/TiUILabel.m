/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UILABEL

#import "TiUILabel.h"
#import "TiUILabelProxy.h"
#import "TiUtils.h"
#import "UIImage+Resize.h"

@implementation TiUILabel

#pragma mark Internal

-(id)init
{
    if (self = [super init]) {
        padding = CGRectZero;
        textPadding = CGRectZero;
        initialLabelFrame = CGRectZero;
        verticalAlign = -1;
    }
    return self;
}

-(void)dealloc
{
    RELEASE_TO_NIL(label);
    RELEASE_TO_NIL(backgroundView);
    [super dealloc];
}

- (BOOL)interactionDefault
{
	// by default, labels don't have any interaction unless you explicitly add
	// it via addEventListener
	return NO;
}

-(CGFloat) verifyWidth:(CGFloat)suggestedWidth
{
	int width = ceil(suggestedWidth);
	if (width & 0x01)
	{
		width ++;
	}
	return width;
}

-(CGFloat) verifyHeight:(CGFloat)suggestedHeight
{
	int height = ceil(suggestedHeight);
	if (height & 0x01)
	{
		height ++;
	}
	return height;
}

-(CGSize)sizeForFont:(CGFloat)suggestedWidth
{
	NSString *value = [label text];
	UIFont *font = [label font];
	CGSize maxSize = CGSizeMake(suggestedWidth<=0 ? 480 : suggestedWidth, 10000);
	CGSize shadowOffset = [label shadowOffset];
	requiresLayout = YES;
	if ((suggestedWidth > 0) && [value hasSuffix:@" "]) {
		// (CGSize)sizeWithFont:(UIFont *)font constrainedToSize:(CGSize)size lineBreakMode:(UILineBreakMode)lineBreakMode method truncates
		// the string having trailing spaces when given size parameter width is equal to the expected return width, so we adjust it here.
		maxSize.width += 0.00001;
	}
	CGSize size = [value sizeWithFont:font constrainedToSize:maxSize lineBreakMode:[[self label] lineBreakMode]];
	if (shadowOffset.width > 0)
	{
		// if we have a shadow and auto, we need to adjust to prevent
		// font from clipping
		size.width += shadowOffset.width + 10;
	}
	size.width += textPadding.origin.x + textPadding.size.width;
	size.height += textPadding.origin.y + textPadding.size.height;
	return size;
}

-(CGFloat)contentWidthForWidth:(CGFloat)suggestedWidth
{
    return [self verifyWidth:([self sizeForFont:suggestedWidth].width + textPadding.origin.x + textPadding.size.width)];
}

-(CGFloat)contentHeightForWidth:(CGFloat)width
{
    return [self verifyHeight:([self sizeForFont:width].height + textPadding.origin.y + textPadding.size.height)];
}

-(void)padLabel
{
	CGRect	initFrame = CGRectMake(initialLabelFrame.origin.x + textPadding.origin.x
									, initialLabelFrame.origin.y + textPadding.origin.y
									, initialLabelFrame.size.width - textPadding.origin.x - textPadding.size.width
									, initialLabelFrame.size.height - textPadding.origin.y - textPadding.size.height); 
    if (verticalAlign != -1) {
        
        CGSize actualLabelSize = [self sizeForFont:initFrame.size.width];
        CGFloat originX = initFrame.origin.x;
        switch (label.textAlignment) {
            case UITextAlignmentRight:
                originX = (initFrame.size.width - actualLabelSize.width - textPadding.size.width);
                break;
            case UITextAlignmentCenter:
                originX = (initFrame.size.width - actualLabelSize.width)/2.0;
                break;
            default:
                break;
        }

        CGRect labelRect = CGRectMake(MAX(0,originX), MAX(0,textPadding.origin.y), actualLabelSize.width, MIN(actualLabelSize.height, initFrame.size.height));
        switch (verticalAlign) {
            case UIControlContentVerticalAlignmentBottom:
                labelRect.origin.y = initFrame.size.height - actualLabelSize.height - textPadding.size.height;
                break;
            case UIControlContentVerticalAlignmentCenter:
                labelRect.origin.y = (initFrame.size.height - actualLabelSize.height)/2;

                break;
            default:
                
                break;
        }
    
        [label setFrame:CGRectIntegral(labelRect)];
    }
    else {
        [label setFrame:initFrame];
    }

    if (repad &&
        backgroundView != nil &&
        !CGRectIsEmpty(initFrame))
    {
    	//backgroundView frame is based on the label real frame
        [backgroundView setFrame:CGRectMake(initialLabelFrame.origin.x - padding.origin.x,
                                            initialLabelFrame.origin.y - padding.origin.y,
                                            initialLabelFrame.size.width + padding.origin.x + padding.size.width,
                                            initialLabelFrame.size.height + padding.origin.y + padding.size.height)];
        repad = NO;
    }
	return;
}

// FIXME: This isn't quite true.  But the brilliant soluton wasn't so brilliant, because it screwed with layout in unpredictable ways.
//	Sadly, there was a brilliant solution for fixing the blurring here, but it turns out there's a
//	quicker fix: Make sure the label itself has an even height and width. Everything else is irrelevant.
-(void)setCenter:(CGPoint)newCenter
{
	[super setCenter:CGPointMake(floorf(newCenter.x), floorf(newCenter.y))];
}

-(void)frameSizeChanged:(CGRect)frame bounds:(CGRect)bounds
{
	initialLabelFrame = bounds;

    repad = YES;
    [self padLabel];

    [super frameSizeChanged:frame bounds:bounds];
}

-(UILabel*)label
{
	if (label==nil)
	{
        label = [[UILabel alloc] initWithFrame:CGRectZero];
        label.backgroundColor = [UIColor clearColor];
        label.numberOfLines = 0;
        label.lineBreakMode = UILineBreakModeWordWrap; //default wordWrap to True
        [self addSubview:label];
        self.clipsToBounds = YES;
	}
	return label;
}

- (id)accessibilityElement
{
	return [self label];
}

-(void)setHighlighted:(BOOL)newValue
{
	[[self label] setHighlighted:newValue];
}

- (void)didMoveToSuperview
{
	/*
	 *	Because of how we re-use the same cells in both a tableview and its
	 *	search table, there is the chance that the label is transported between
	 *	the two views before a selected search row is deselected. In other
	 *	words, make sure we're not highlighted when changing superviews.
	 */
	[self setHighlighted:NO];
	[super didMoveToSuperview];
}

-(BOOL)isHighlighted
{
	return [[self label] isHighlighted];
}

#pragma mark Public APIs

-(void)setVerticalAlign_:(id)value
{
    verticalAlign = [TiUtils intValue:value def:-1];
    if (verticalAlign < UIControlContentVerticalAlignmentCenter || verticalAlign > UIControlContentVerticalAlignmentBottom) {
        verticalAlign = -1;
    }
    if (label != nil) {
        [self padLabel];
    }
}
-(void)setText_:(id)text
{
	[[self label] setText:[TiUtils stringValue:text]];
    [self padLabel];
	[(TiViewProxy *)[self proxy] contentsWillChange];
}

-(void)setColor_:(id)color
{
	UIColor * newColor = [[TiUtils colorValue:color] _color];
	[[self label] setTextColor:(newColor != nil)?newColor:[UIColor darkTextColor]];
}

-(void)setHighlightedColor_:(id)color
{
	UIColor * newColor = [[TiUtils colorValue:color] _color];
	[[self label] setHighlightedTextColor:(newColor != nil)?newColor:[UIColor lightTextColor]];
}

-(void)setFont_:(id)font
{
	[[self label] setFont:[[TiUtils fontValue:font] font]];
	[(TiViewProxy *)[self proxy] contentsWillChange];
}

-(void)setMinimumFontSize_:(id)size
{
    CGFloat newSize = [TiUtils floatValue:size];
    if (newSize < 4) { // Beholden to 'most minimum' font size
        [[self label] setAdjustsFontSizeToFitWidth:NO];
        [[self label] setMinimumFontSize:0.0];
        [[self label] setNumberOfLines:0];
    }
    else {
        [[self label] setNumberOfLines:1];
        [[self label] setAdjustsFontSizeToFitWidth:YES];
        [[self label] setMinimumFontSize:newSize];
    }

}

-(void)setBackgroundImage_:(id)url
{
    if (url != nil) {
        UIImage* bgImage = [self loadImage:url];
        if (backgroundView == nil) {
            backgroundView = [[UIImageView alloc] initWithImage:bgImage];
            backgroundView.userInteractionEnabled = NO;
            [self insertSubview:backgroundView atIndex:0];
            repad = YES;
            [self padLabel];
        }
        else {
            backgroundView.image = bgImage;
            [backgroundView setNeedsDisplay];

            repad = YES;
            [self padLabel];
        }
    }
    else {
        if (backgroundView) {
            [backgroundView removeFromSuperview];
            RELEASE_TO_NIL(backgroundView);
        }
    }

    self.backgroundImage = url;
}

-(void)setBackgroundPaddingLeft_:(id)left
{
    padding.origin.x = [TiUtils floatValue:left];
    repad = YES;
    [self padLabel];
}

-(void)setBackgroundPaddingRight_:(id)right
{
    padding.size.width = [TiUtils floatValue:right];
    repad = YES;
    [self padLabel];
}

-(void)setBackgroundPaddingTop_:(id)top
{
    padding.origin.y = [TiUtils floatValue:top];
    repad = YES;
    [self padLabel];
}

-(void)setBackgroundPaddingBottom_:(id)bottom
{
    padding.size.height = [TiUtils floatValue:bottom];
    repad = YES;
    [self padLabel];
}

-(void)setTextAlign_:(id)alignment
{
	[[self label] setTextAlignment:[TiUtils textAlignmentValue:alignment]];
    [self padLabel];
}

-(void)setShadowColor_:(id)color
{
	if (color==nil)
	{
		[[self label] setShadowColor:nil];
	}
	else
	{
		color = [TiUtils colorValue:color];
		[[self label] setShadowColor:[color _color]];
	}
}

-(void)setShadowOffset_:(id)value
{
	CGPoint p = [TiUtils pointValue:value];
	CGSize size = {p.x,p.y};
	[[self label] setShadowOffset:size];
}

-(void)setTextPaddingLeft_:(id)left
{
    textPadding.origin.x = [TiUtils floatValue:left];
    [self padLabel];
	[(TiViewProxy *)[self proxy] contentsWillChange];
}

-(void)setTextPaddingRight_:(id)right
{
    textPadding.size.width = [TiUtils floatValue:right];
    [self padLabel];
	[(TiViewProxy *)[self proxy] contentsWillChange];
}

-(void)setTextPaddingTop_:(id)top
{
    textPadding.origin.y = [TiUtils floatValue:top];
    [self padLabel];
	[(TiViewProxy *)[self proxy] contentsWillChange];
}

-(void)setTextPaddingBottom_:(id)bottom
{
    textPadding.size.height = [TiUtils floatValue:bottom];
    [self padLabel];
	[(TiViewProxy *)[self proxy] contentsWillChange];
}

-(void)setWordWrap_:(id)value
{
    BOOL shouldWordWrap = [TiUtils boolValue:value def:YES];
    if (shouldWordWrap)
        [[self label] setLineBreakMode:UILineBreakModeWordWrap];
    else 
        [[self label] setLineBreakMode:UILineBreakModeTailTruncation];
    [self padLabel];
	[(TiViewProxy *)[self proxy] contentsWillChange];
}

@end

#endif
