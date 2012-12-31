//
//  DTCoreTextParagraphStyle.m
//  CoreTextExtensions
//
//  Created by Oliver Drobnik on 4/14/11.
//  Copyright 2011 Drobnik.com. All rights reserved.
//

#import "DTCoreTextParagraphStyle.h"

@implementation DTCoreTextParagraphStyle
{
	CGFloat _firstLineHeadIndent;
	CGFloat _defaultTabInterval;
	CGFloat _paragraphSpacingBefore;
	CGFloat _paragraphSpacing;
	CGFloat _headIndent;
	CGFloat _tailIndent;
	CGFloat _listIndent;
	CGFloat _lineHeightMultiple;
	CGFloat _minimumLineHeight;
	CGFloat _maximumLineHeight;
	
	CTTextAlignment _alignment;
	CTLineBreakMode _linebreak;
	CTWritingDirection _baseWritingDirection;
	
	NSMutableArray *_tabStops;
}

+ (DTCoreTextParagraphStyle *)defaultParagraphStyle
{
	return [[DTCoreTextParagraphStyle alloc] init];
}

+ (DTCoreTextParagraphStyle *)paragraphStyleWithCTParagraphStyle:(CTParagraphStyleRef)ctParagraphStyle
{
	return [[DTCoreTextParagraphStyle alloc] initWithCTParagraphStyle:ctParagraphStyle];
}

#if __IPHONE_OS_VERSION_MAX_ALLOWED > __IPHONE_5_1
+ (DTCoreTextParagraphStyle *)paragraphStyleWithNSParagraphStyle:(NSParagraphStyle *)paragraphStyle
{
	DTCoreTextParagraphStyle *retStyle = [[DTCoreTextParagraphStyle alloc] init];
	
	retStyle.firstLineHeadIndent = paragraphStyle.firstLineHeadIndent;
	retStyle.headIndent = paragraphStyle.headIndent;
	
	retStyle.paragraphSpacing = paragraphStyle.paragraphSpacing;
	retStyle.paragraphSpacingBefore = paragraphStyle.paragraphSpacingBefore;
	
	retStyle.lineHeightMultiple = paragraphStyle.lineHeightMultiple;
	retStyle.minimumLineHeight = paragraphStyle.minimumLineHeight;
	retStyle.maximumLineHeight = paragraphStyle.maximumLineHeight;
	
	switch(paragraphStyle.alignment)
	{
		case NSTextAlignmentLeft:
			retStyle.alignment = kCTLeftTextAlignment;
			break;
		case NSTextAlignmentRight:
			retStyle.alignment = kCTRightTextAlignment;
			break;
		case NSTextAlignmentCenter:
			retStyle.alignment = kCTCenterTextAlignment;
			break;
		case NSTextAlignmentJustified:
			retStyle.alignment = kCTJustifiedTextAlignment;
			break;
		case NSTextAlignmentNatural:
			retStyle.alignment = kCTNaturalTextAlignment;
			break;
	}
	
    switch(paragraphStyle.lineBreakMode)
	{
		case NSLineBreakByCharWrapping:
			retStyle.alignment = kCTLineBreakByCharWrapping;
			break;
		case NSLineBreakByClipping:
			retStyle.alignment = kCTLineBreakByClipping;
			break;
		case NSLineBreakByTruncatingHead:
			retStyle.alignment = kCTLineBreakByTruncatingHead;
			break;
		case NSLineBreakByTruncatingMiddle:
			retStyle.alignment = kCTLineBreakByTruncatingMiddle;
			break;
		case NSLineBreakByTruncatingTail:
			retStyle.alignment = kCTLineBreakByTruncatingTail;
			break;
        case NSLineBreakByWordWrapping:
			retStyle.alignment = kCTLineBreakByWordWrapping;
			break;
	}
	
	switch (paragraphStyle.baseWritingDirection)
	{
		case NSWritingDirectionNatural:
			retStyle.baseWritingDirection = kCTWritingDirectionNatural;
			break;
		case NSWritingDirectionLeftToRight:
			retStyle.baseWritingDirection = kCTWritingDirectionLeftToRight;
			break;
		case NSWritingDirectionRightToLeft:
			retStyle.baseWritingDirection = kCTWritingDirectionRightToLeft;
			break;
	}
	
	return retStyle;
}
#endif

- (id)init
{	
	if ((self = [super init]))
	{
		// defaults
		_firstLineHeadIndent = 0.0;
		_defaultTabInterval = 36.0;
		_baseWritingDirection = kCTWritingDirectionNatural;
		_alignment = kCTNaturalTextAlignment;
		_linebreak = kCTLineBreakByCharWrapping;
		_lineHeightMultiple = 0.0;
		_minimumLineHeight = 0.0;
		_maximumLineHeight = 0.0;
		_paragraphSpacing = 0.0;
		_listIndent = 0;
	}
	
	return self;
}


- (id)initWithCTParagraphStyle:(CTParagraphStyleRef)ctParagraphStyle
{	
	if ((self = [super init]))
	{
		// text alignment
		CTParagraphStyleGetValueForSpecifier(ctParagraphStyle, kCTParagraphStyleSpecifierAlignment,sizeof(_alignment), &_alignment);
		
        // line break
		CTParagraphStyleGetValueForSpecifier(ctParagraphStyle, kCTParagraphStyleSpecifierLineBreakMode,sizeof(_linebreak), &_linebreak);
		
		// indents
		CTParagraphStyleGetValueForSpecifier(ctParagraphStyle, kCTParagraphStyleSpecifierFirstLineHeadIndent, sizeof(_firstLineHeadIndent), &_firstLineHeadIndent);
		CTParagraphStyleGetValueForSpecifier(ctParagraphStyle, kCTParagraphStyleSpecifierHeadIndent, sizeof(_headIndent), &_headIndent);
		CTParagraphStyleGetValueForSpecifier(ctParagraphStyle, kCTParagraphStyleSpecifierTailIndent, sizeof(_tailIndent), &_tailIndent);
		
		// paragraph spacing
		CTParagraphStyleGetValueForSpecifier(ctParagraphStyle, kCTParagraphStyleSpecifierParagraphSpacing, sizeof(_paragraphSpacing), &_paragraphSpacing);
		CTParagraphStyleGetValueForSpecifier(ctParagraphStyle, kCTParagraphStyleSpecifierParagraphSpacingBefore,sizeof(_paragraphSpacingBefore), &_paragraphSpacingBefore);


		// tab stops
		CTParagraphStyleGetValueForSpecifier(ctParagraphStyle, kCTParagraphStyleSpecifierDefaultTabInterval, sizeof(_defaultTabInterval), &_defaultTabInterval);
		
		__unsafe_unretained NSArray *stops; // Could use a CFArray too, leave as a reminder how to do this in the future
		if (CTParagraphStyleGetValueForSpecifier(ctParagraphStyle, kCTParagraphStyleSpecifierTabStops, sizeof(stops), &stops))
		{
			self.tabStops = stops;
		}
		
		
		CTParagraphStyleGetValueForSpecifier(ctParagraphStyle, kCTParagraphStyleSpecifierBaseWritingDirection, sizeof(_baseWritingDirection), &_baseWritingDirection);
		
		// line height
		CTParagraphStyleGetValueForSpecifier(ctParagraphStyle, kCTParagraphStyleSpecifierMinimumLineHeight, sizeof(_minimumLineHeight), &_minimumLineHeight);
		CTParagraphStyleGetValueForSpecifier(ctParagraphStyle, kCTParagraphStyleSpecifierMaximumLineHeight, sizeof(_maximumLineHeight), &_maximumLineHeight);

		
		CTParagraphStyleGetValueForSpecifier(ctParagraphStyle, kCTParagraphStyleSpecifierLineHeightMultiple, sizeof(_lineHeightMultiple), &_lineHeightMultiple);
		
		if (_lineHeightMultiple)
		{
			// paragraph space is pre-multiplied
			if (_paragraphSpacing)
			{
				_paragraphSpacing /= _lineHeightMultiple;
			}
			
			if (_paragraphSpacingBefore)
			{
				_paragraphSpacingBefore /= _lineHeightMultiple;
			}
		}
	}
	
	return self;
}

- (CTParagraphStyleRef)createCTParagraphStyle
{
	// need to multiple paragraph spacing with line height multiplier
	float tmpParagraphSpacing = _paragraphSpacing;
	float tmpParagraphSpacingBefore = _paragraphSpacingBefore;
	
	if (_lineHeightMultiple&&(_lineHeightMultiple!=1.0))
	{
		tmpParagraphSpacing *= _lineHeightMultiple;
		tmpParagraphSpacingBefore *= _lineHeightMultiple;
	}
	
	// This just makes it that much easier to track down memory issues with tabstops
	CFArrayRef stops = _tabStops ? CFArrayCreateCopy (NULL, (__bridge CFArrayRef)_tabStops) : NULL;
	
	CTParagraphStyleSetting settings[] = 
	{
		{kCTParagraphStyleSpecifierAlignment, sizeof(_alignment), &_alignment},
		{kCTParagraphStyleSpecifierLineBreakMode, sizeof(_linebreak), &_linebreak},
		{kCTParagraphStyleSpecifierFirstLineHeadIndent, sizeof(_firstLineHeadIndent), &_firstLineHeadIndent},
		{kCTParagraphStyleSpecifierDefaultTabInterval, sizeof(_defaultTabInterval), &_defaultTabInterval},
		
		{kCTParagraphStyleSpecifierTabStops, sizeof(stops), &stops},
		
		{kCTParagraphStyleSpecifierParagraphSpacing, sizeof(tmpParagraphSpacing), &tmpParagraphSpacing},
		{kCTParagraphStyleSpecifierParagraphSpacingBefore, sizeof(tmpParagraphSpacingBefore), &tmpParagraphSpacingBefore},
		
		{kCTParagraphStyleSpecifierHeadIndent, sizeof(_headIndent), &_headIndent},
		{kCTParagraphStyleSpecifierTailIndent, sizeof(_tailIndent), &_tailIndent},
		{kCTParagraphStyleSpecifierBaseWritingDirection, sizeof(_baseWritingDirection), &_baseWritingDirection},
		{kCTParagraphStyleSpecifierLineHeightMultiple, sizeof(_lineHeightMultiple), &_lineHeightMultiple},
		
		{kCTParagraphStyleSpecifierMinimumLineHeight, sizeof(_minimumLineHeight), &_minimumLineHeight},
		{kCTParagraphStyleSpecifierMaximumLineHeight, sizeof(_maximumLineHeight), &_maximumLineHeight}
	};	
	
	CTParagraphStyleRef ret = CTParagraphStyleCreate(settings, 12);
	if (stops) CFRelease(stops);
	
	return ret;
}

#if __IPHONE_OS_VERSION_MAX_ALLOWED > __IPHONE_5_1
- (NSParagraphStyle *)NSParagraphStyle
{
	NSMutableParagraphStyle *mps = [[NSMutableParagraphStyle alloc] init];

	[mps setFirstLineHeadIndent:_firstLineHeadIndent];

	// _defaultTabInterval not supported

	[mps setParagraphSpacing:_paragraphSpacing];
	[mps setParagraphSpacingBefore:_paragraphSpacingBefore];
	
	[mps setHeadIndent:_headIndent];
	[mps setTailIndent:_tailIndent];
	
	// _listIndent not supported
	
	[mps setMinimumLineHeight:_minimumLineHeight];
	[mps setMaximumLineHeight:_maximumLineHeight];
	
	switch(_alignment)
	{
		case kCTLeftTextAlignment:
			[mps setAlignment:NSTextAlignmentLeft];
			break;
		case kCTRightTextAlignment:
			[mps setAlignment:NSTextAlignmentRight];
			break;
		case kCTCenterTextAlignment:
			[mps setAlignment:NSTextAlignmentCenter];
			break;
		case kCTJustifiedTextAlignment:
			[mps setAlignment:NSTextAlignmentJustified];
			break;
		case kCTNaturalTextAlignment:
			[mps setAlignment:NSTextAlignmentNatural];
			break;
	}
	
    switch(_linebreak)
	{
		case kCTLineBreakByCharWrapping:
			[mps setLineBreakMode:NSLineBreakByCharWrapping];
			break;
        case kCTLineBreakByTruncatingHead:
			[mps setLineBreakMode:NSLineBreakByTruncatingHead];
			break;
        case kCTLineBreakByClipping:
			[mps setLineBreakMode:NSLineBreakByClipping];
			break;
        case kCTLineBreakByTruncatingMiddle:
			[mps setLineBreakMode:NSLineBreakByTruncatingMiddle];
			break;
        case kCTLineBreakByTruncatingTail:
			[mps setLineBreakMode:NSLineBreakByTruncatingTail];
			break;
        case kCTLineBreakByWordWrapping:
			[mps setLineBreakMode:NSLineBreakByWordWrapping];
			break;
	}
	
	switch (_baseWritingDirection) {
		case  kCTWritingDirectionNatural:
			[mps setBaseWritingDirection:NSWritingDirectionNatural];
			break;
		case  kCTWritingDirectionLeftToRight:
			[mps setBaseWritingDirection:NSWritingDirectionLeftToRight];
			break;
		case  kCTWritingDirectionRightToLeft:
			[mps setBaseWritingDirection:NSWritingDirectionRightToLeft];
			break;
	}

	// _tap stops not supported
	return (NSParagraphStyle *)mps;
}
#endif

- (void)addTabStopAtPosition:(CGFloat)position alignment:(CTTextAlignment)alignment
{
	CTTextTabRef tab = CTTextTabCreate(alignment, position, NULL);
	if(tab)
	{
		if (!_tabStops)
		{
			_tabStops = [[NSMutableArray alloc] init];
		}
		[_tabStops addObject:CFBridgingRelease(tab)];
	}
}

#pragma mark HTML Encoding

// representation of this paragraph style in css (as far as possible)
- (NSString *)cssStyleRepresentation
{
	NSMutableString *retString = [NSMutableString string];
	
	switch (_alignment) 
	{
		case kCTLeftTextAlignment:
			[retString appendString:@"text-align:left;"];
			break;
		case kCTRightTextAlignment:
			[retString appendString:@"text-align:right;"];
			break;
		case kCTCenterTextAlignment:
			[retString appendString:@"text-align:center;"];
			break;
		case kCTJustifiedTextAlignment:
			[retString appendString:@"text-align:justify;"];
			break;
		case kCTNaturalTextAlignment:
			// no output, this is default
			break;
	}
	
    switch (_linebreak)
	{
		case kCTLineBreakByCharWrapping:
			[retString appendString:@"word-break:break-all;word-wrap:break-word;"];
			break;
		case kCTLineBreakByClipping:
			[retString appendString:@"word-wrap:normal;"];
			break;
		case kCTLineBreakByTruncatingHead:
			//			[retString appendString:@"text-align:center;"];
			break;
		case kCTLineBreakByTruncatingMiddle:
			//			[retString appendString:@"text-align:justify;"];
			break;
        case kCTLineBreakByWordWrapping:
			[retString appendString:@"word-break:keep-all;word-wrap:break-word;"];
			break;
		case kCTLineBreakByTruncatingTail:
			// no output, this is default
			break;
	}
	
	if (_lineHeightMultiple && _lineHeightMultiple!=1.0f)
	{
		NSNumber *number = [NSNumber numberWithFloat:_lineHeightMultiple];
		[retString appendFormat:@"line-height:%@em;", number];
	}
	
	switch (_baseWritingDirection)
	{
		case kCTWritingDirectionRightToLeft:
			[retString appendString:@"direction:rtl;"];
			break;
		case kCTWritingDirectionLeftToRight:
			[retString appendString:@"direction:ltr;"];
			break;
		case kCTWritingDirectionNatural:
			// no output, this is default
			break;
	}	
	
	// Spacing at the bottom
	if (_paragraphSpacing!=0.0f)
	{
		NSNumber *number = [NSNumber numberWithFloat:_paragraphSpacing];
		[retString appendFormat:@"margin-bottom:%@px;", number];
	}

	// Spacing at the top
	if (_paragraphSpacingBefore!=0.0f)
	{
		NSNumber *number = [NSNumber numberWithFloat:_paragraphSpacingBefore];
		[retString appendFormat:@"margin-top:%@px;", number];
	}
	
	// Spacing at the left
	if (_headIndent!=0.0f)
	{
		NSNumber *number = [NSNumber numberWithFloat:_headIndent];
		[retString appendFormat:@"margin-left:%@px;", number];
	}

	// Spacing at the right
	if (_tailIndent!=0.0f)
	{
		NSNumber *number = [NSNumber numberWithFloat:_tailIndent];
		[retString appendFormat:@"margin-right:%@px;", number];
	}

	// return nil if no content
	if ([retString length])
	{
		return retString;
	}
	else
	{
		return nil;
	}
}

#pragma mark Copying

- (id)copyWithZone:(NSZone *)zone
{
	DTCoreTextParagraphStyle *newObject = [[DTCoreTextParagraphStyle allocWithZone:zone] init];
	
	newObject.firstLineHeadIndent = self.firstLineHeadIndent;
	newObject.tailIndent = self.tailIndent;
	newObject.defaultTabInterval = self.defaultTabInterval;
	newObject.paragraphSpacing = self.paragraphSpacing;
	newObject.paragraphSpacingBefore = self.paragraphSpacingBefore;
	newObject.lineHeightMultiple = self.lineHeightMultiple;
	newObject.minimumLineHeight = self.minimumLineHeight;
	newObject.maximumLineHeight = self.maximumLineHeight;
	newObject.headIndent = self.headIndent;
	newObject.listIndent = self.listIndent;
	newObject.alignment = self.alignment;
	newObject.linebreak = self.linebreak;
	newObject.baseWritingDirection = self.baseWritingDirection;
	newObject.tabStops = self.tabStops; // copy
	newObject.textLists = self.textLists; //copy
	newObject.textBlocks = self.textBlocks; //copy
	
	return newObject;
}

#pragma mark Properties

- (void)setTabStops:(NSArray *)tabStops
{
	if (tabStops != _tabStops)
	{
		_tabStops = [tabStops mutableCopy]; // keep mutability
	}
}

@synthesize firstLineHeadIndent = _firstLineHeadIndent;
@synthesize defaultTabInterval = _defaultTabInterval;
@synthesize paragraphSpacingBefore = _paragraphSpacingBefore;
@synthesize paragraphSpacing = _paragraphSpacing;
@synthesize lineHeightMultiple = _lineHeightMultiple;
@synthesize minimumLineHeight = _minimumLineHeight;
@synthesize maximumLineHeight = _maximumLineHeight;
@synthesize headIndent = _headIndent;
@synthesize tailIndent = _tailIndent;
@synthesize listIndent = _listIndent;
@synthesize alignment = _alignment;
@synthesize linebreak = _linebreak;
@synthesize textLists = _textLists;
@synthesize textBlocks = _textBlocks;
@synthesize baseWritingDirection = _baseWritingDirection;
@synthesize tabStops = _tabStops;

@end
