/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2018 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#import <TitaniumKit/TiModule.h>

#ifdef USE_TI_UI

// because alert is linked in Kroll and the user can use that
// in their code instead of the full API, we need to create
// an explicit compile time dependency to UI
#import "TiUIAlertDialogProxy.h"

#ifdef USE_TI_UIACTIVITYINDICATORSTYLE
#import "TiUIActivityIndicatorStyleProxy.h"
#endif

#if defined(USE_TI_UISHORTCUT) || defined(USE_TI_UISHORTCUTITEM)
#import "TiUIShortcutProxy.h"
#endif

@interface UIModule : TiModule {

  @private
#ifdef USE_TI_UIIPAD
  TiProxy *ipad;
#endif
#ifdef USE_TI_UIIOS
  TiProxy *ios;
#endif
#ifdef USE_TI_UICLIPBOARD
  TiProxy *clipboard;
#endif
#if defined(USE_TI_UISHORTCUT) || defined(USE_TI_UISHORTCUTITEM)
  TiUIShortcutProxy *shortcut;
#endif
  NSNumber *lastEmittedMode;
}

//TODO: review these, maybe they need to go on iPhone Animation Style - however, they are platform generic

@property (nonatomic, readonly) NSNumber *ANIMATION_CURVE_EASE_IN_OUT;
@property (nonatomic, readonly) NSNumber *ANIMATION_CURVE_EASE_IN;
@property (nonatomic, readonly) NSNumber *ANIMATION_CURVE_EASE_OUT;
@property (nonatomic, readonly) NSNumber *ANIMATION_CURVE_LINEAR;

@property (nonatomic, readonly) NSNumber *TEXT_ALIGNMENT_LEFT;
@property (nonatomic, readonly) NSNumber *TEXT_ALIGNMENT_CENTER;
@property (nonatomic, readonly) NSNumber *TEXT_ALIGNMENT_RIGHT;
@property (nonatomic, readonly) NSNumber *TEXT_ALIGNMENT_JUSTIFY;

@property (nonatomic, readonly) NSNumber *TEXT_ELLIPSIZE_TRUNCATE_WORD_WRAP;
@property (nonatomic, readonly) NSNumber *TEXT_ELLIPSIZE_TRUNCATE_CHAR_WRAP;
@property (nonatomic, readonly) NSNumber *TEXT_ELLIPSIZE_TRUNCATE_CLIP;
@property (nonatomic, readonly) NSNumber *TEXT_ELLIPSIZE_TRUNCATE_START;
@property (nonatomic, readonly) NSNumber *TEXT_ELLIPSIZE_TRUNCATE_MIDDLE;
@property (nonatomic, readonly) NSNumber *TEXT_ELLIPSIZE_TRUNCATE_END;

@property (nonatomic, readonly) NSNumber *TEXT_VERTICAL_ALIGNMENT_TOP;
@property (nonatomic, readonly) NSNumber *TEXT_VERTICAL_ALIGNMENT_CENTER;
@property (nonatomic, readonly) NSNumber *TEXT_VERTICAL_ALIGNMENT_BOTTOM;

@property (nonatomic, readonly) NSNumber *RETURNKEY_DEFAULT;
@property (nonatomic, readonly) NSNumber *RETURNKEY_GO;
@property (nonatomic, readonly) NSNumber *RETURNKEY_GOOGLE;
@property (nonatomic, readonly) NSNumber *RETURNKEY_JOIN;
@property (nonatomic, readonly) NSNumber *RETURNKEY_NEXT;
@property (nonatomic, readonly) NSNumber *RETURNKEY_ROUTE;
@property (nonatomic, readonly) NSNumber *RETURNKEY_SEARCH;
@property (nonatomic, readonly) NSNumber *RETURNKEY_SEND;
@property (nonatomic, readonly) NSNumber *RETURNKEY_YAHOO;
@property (nonatomic, readonly) NSNumber *RETURNKEY_DONE;
@property (nonatomic, readonly) NSNumber *RETURNKEY_EMERGENCY_CALL;
@property (nonatomic, readonly) NSNumber *RETURNKEY_CONTINUE;

@property (nonatomic, readonly) NSNumber *KEYBOARD_DEFAULT;
@property (nonatomic, readonly) NSNumber *KEYBOARD_ASCII;
@property (nonatomic, readonly) NSNumber *KEYBOARD_NUMBERS_PUNCTUATION;
@property (nonatomic, readonly) NSNumber *KEYBOARD_URL;
@property (nonatomic, readonly) NSNumber *KEYBOARD_NUMBER_PAD;
@property (nonatomic, readonly) NSNumber *KEYBOARD_DECIMAL_PAD;
@property (nonatomic, readonly) NSNumber *KEYBOARD_PHONE_PAD;
@property (nonatomic, readonly) NSNumber *KEYBOARD_NAMEPHONE_PAD;
@property (nonatomic, readonly) NSNumber *KEYBOARD_EMAIL;
@property (nonatomic, readonly) NSNumber *KEYBOARD_WEBSEARCH;
@property (nonatomic, readonly) NSNumber *KEYBOARD_TWITTER;

@property (nonatomic, readonly) NSNumber *KEYBOARD_APPEARANCE_DEFAULT;
@property (nonatomic, readonly) NSNumber *KEYBOARD_APPEARANCE_ALERT;

@property (nonatomic, readonly) NSNumber *TEXT_AUTOCAPITALIZATION_NONE;
@property (nonatomic, readonly) NSNumber *TEXT_AUTOCAPITALIZATION_WORDS;
@property (nonatomic, readonly) NSNumber *TEXT_AUTOCAPITALIZATION_SENTENCES;
@property (nonatomic, readonly) NSNumber *TEXT_AUTOCAPITALIZATION_ALL;

@property (nonatomic, readonly) NSNumber *INPUT_BUTTONMODE_NEVER;
@property (nonatomic, readonly) NSNumber *INPUT_BUTTONMODE_ALWAYS;
@property (nonatomic, readonly) NSNumber *INPUT_BUTTONMODE_ONFOCUS;
@property (nonatomic, readonly) NSNumber *INPUT_BUTTONMODE_ONBLUR;

@property (nonatomic, readonly) NSNumber *INPUT_BORDERSTYLE_NONE;
@property (nonatomic, readonly) NSNumber *INPUT_BORDERSTYLE_LINE;
@property (nonatomic, readonly) NSNumber *INPUT_BORDERSTYLE_UNDERLINED;
@property (nonatomic, readonly) NSNumber *INPUT_BORDERSTYLE_BEZEL;
@property (nonatomic, readonly) NSNumber *INPUT_BORDERSTYLE_ROUNDED;
@property (nonatomic, readonly) NSNumber *INPUT_BORDERSTYLE_FILLED;

@property (nonatomic, readonly) NSNumber *PORTRAIT;
@property (nonatomic, readonly) NSNumber *LANDSCAPE_LEFT;
@property (nonatomic, readonly) NSNumber *LANDSCAPE_RIGHT;
@property (nonatomic, readonly) NSNumber *UPSIDE_PORTRAIT;
@property (nonatomic, readonly) NSNumber *UNKNOWN;
@property (nonatomic, readonly) NSNumber *FACE_UP;
@property (nonatomic, readonly) NSNumber *FACE_DOWN;

@property (nonatomic, readonly) NSNumber *PICKER_TYPE_PLAIN;
@property (nonatomic, readonly) NSNumber *PICKER_TYPE_DATE_AND_TIME;
@property (nonatomic, readonly) NSNumber *PICKER_TYPE_DATE;
@property (nonatomic, readonly) NSNumber *PICKER_TYPE_TIME;
@property (nonatomic, readonly) NSNumber *PICKER_TYPE_COUNT_DOWN_TIMER;

@property (nonatomic, readonly) NSNumber *BLEND_MODE_NORMAL;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_MULTIPLY;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_SCREEN;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_OVERLAY;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_DARKEN;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_LIGHTEN;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_COLOR_DODGE;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_COLOR_BURN;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_SOFT_LIGHT;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_HARD_LIGHT;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_DIFFERENCE;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_EXCLUSION;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_HUE;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_SATURATION;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_COLOR;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_LUMINOSITY;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_CLEAR;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_COPY;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_SOURCE_IN;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_SOURCE_OUT;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_SOURCE_ATOP;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_DESTINATION_OVER;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_DESTINATION_IN;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_DESTINATION_OUT;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_DESTINATION_ATOP;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_XOR;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_PLUS_DARKER;
@property (nonatomic, readonly) NSNumber *BLEND_MODE_PLUS_LIGHTER;

@property (nonatomic, readonly) NSNumber *BUTTON_STYLE_FILLED;
@property (nonatomic, readonly) NSNumber *BUTTON_STYLE_OUTLINED;
@property (nonatomic, readonly) NSNumber *BUTTON_STYLE_TEXT;
@property (nonatomic, readonly) NSNumber *BUTTON_STYLE_OPTION_POSITIVE;
@property (nonatomic, readonly) NSNumber *BUTTON_STYLE_OPTION_NEGATIVE;
@property (nonatomic, readonly) NSNumber *BUTTON_STYLE_OPTION_NEUTRAL;

@property (nonatomic, readonly) NSNumber *SELECTION_STYLE_NONE;
@property (nonatomic, readonly) NSNumber *SELECTION_STYLE_DEFAULT;

@property (nonatomic, readonly) NSNumber *SWITCH_STYLE_SLIDER;
@property (nonatomic, readonly) NSNumber *SWITCH_STYLE_CHECKBOX;
@property (nonatomic, readonly) NSNumber *SWITCH_STYLE_TOGGLE_BUTTON;
@property (nonatomic, readonly) NSNumber *SWITCH_STYLE_CHIP;

@property (nonatomic, readonly) NSNumber *AUTOLINK_NONE;
@property (nonatomic, readonly) NSNumber *AUTOLINK_ALL;
@property (nonatomic, readonly) NSNumber *AUTOLINK_PHONE_NUMBERS;
@property (nonatomic, readonly) NSNumber *AUTOLINK_URLS;
@property (nonatomic, readonly) NSNumber *AUTOLINK_EMAIL_ADDRESSES;
@property (nonatomic, readonly) NSNumber *AUTOLINK_MAP_ADDRESSES;
@property (nonatomic, readonly) NSNumber *AUTOLINK_CALENDAR;

@property (nonatomic, readonly) NSString *SIZE;
@property (nonatomic, readonly) NSString *FILL;
@property (nonatomic, readonly) NSString *UNIT_PX;
@property (nonatomic, readonly) NSString *UNIT_CM;
@property (nonatomic, readonly) NSString *UNIT_MM;
@property (nonatomic, readonly) NSString *UNIT_IN;
@property (nonatomic, readonly) NSString *UNIT_DIP;

@property (nonatomic, readonly) NSNumber *URL_ERROR_AUTHENTICATION;
@property (nonatomic, readonly) NSNumber *URL_ERROR_BAD_URL;
@property (nonatomic, readonly) NSNumber *URL_ERROR_CONNECT;
@property (nonatomic, readonly) NSNumber *URL_ERROR_SSL_FAILED;
@property (nonatomic, readonly) NSNumber *URL_ERROR_FILE;
@property (nonatomic, readonly) NSNumber *URL_ERROR_FILE_NOT_FOUND;
@property (nonatomic, readonly) NSNumber *URL_ERROR_HOST_LOOKUP;
@property (nonatomic, readonly) NSNumber *URL_ERROR_REDIRECT_LOOP;
@property (nonatomic, readonly) NSNumber *URL_ERROR_TIMEOUT;
@property (nonatomic, readonly) NSNumber *URL_ERROR_UNKNOWN;
@property (nonatomic, readonly) NSNumber *URL_ERROR_UNSUPPORTED_SCHEME;

@property (nonatomic, readonly) NSNumber *LIST_ITEM_TEMPLATE_DEFAULT;
@property (nonatomic, readonly) NSNumber *LIST_ITEM_TEMPLATE_SETTINGS;
@property (nonatomic, readonly) NSNumber *LIST_ITEM_TEMPLATE_CONTACTS;
@property (nonatomic, readonly) NSNumber *LIST_ITEM_TEMPLATE_SUBTITLE;

@property (nonatomic, readonly) NSNumber *LIST_ACCESSORY_TYPE_NONE;
@property (nonatomic, readonly) NSNumber *LIST_ACCESSORY_TYPE_CHECKMARK;
@property (nonatomic, readonly) NSNumber *LIST_ACCESSORY_TYPE_DETAIL;
@property (nonatomic, readonly) NSNumber *LIST_ACCESSORY_TYPE_DISCLOSURE;

@property (nonatomic, readonly) NSNumber *EXTEND_EDGE_LEFT;
@property (nonatomic, readonly) NSNumber *EXTEND_EDGE_RIGHT;
@property (nonatomic, readonly) NSNumber *EXTEND_EDGE_TOP;
@property (nonatomic, readonly) NSNumber *EXTEND_EDGE_BOTTOM;
@property (nonatomic, readonly) NSNumber *EXTEND_EDGE_NONE;
@property (nonatomic, readonly) NSNumber *EXTEND_EDGE_ALL;

@property (nonatomic, readonly) NSString *TEXT_STYLE_HEADLINE;
@property (nonatomic, readonly) NSString *TEXT_STYLE_SUBHEADLINE;
@property (nonatomic, readonly) NSString *TEXT_STYLE_BODY;
@property (nonatomic, readonly) NSString *TEXT_STYLE_FOOTNOTE;
@property (nonatomic, readonly) NSString *TEXT_STYLE_CAPTION1;
@property (nonatomic, readonly) NSString *TEXT_STYLE_CAPTION2;
@property (nonatomic, readonly) NSString *TEXT_STYLE_TITLE1;
@property (nonatomic, readonly) NSString *TEXT_STYLE_TITLE2;
@property (nonatomic, readonly) NSString *TEXT_STYLE_TITLE3;
@property (nonatomic, readonly) NSString *TEXT_STYLE_CALLOUT;
@property (nonatomic, readonly) NSString *TEXT_STYLE_LARGE_TITLE;

- (id)createMatrix2D:(id)args;
- (id)create2DMatrix:(id)args; // Deprecated since 8.0.0
- (id)createMatrix3D:(id)args;
- (id)create3DMatrix:(id)args; // Deprecated since 8.0.0

#ifdef USE_TI_UIANIMATION
- (id)createAnimation:(id)args;
#endif

#ifdef USE_TI_UIIPAD
@property (nonatomic, readonly) TiProxy *iPad;
#endif

#ifdef USE_TI_UIIOS
@property (nonatomic, readonly) TiProxy *iOS;
#endif

#ifdef USE_TI_UICLIPBOARD
@property (nonatomic, readonly) TiProxy *Clipboard;
#endif

#ifdef USE_TI_UIATTRIBUTEDSTRING
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_FONT;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_PARAGRAPH_STYLE;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_FOREGROUND_COLOR;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_BACKGROUND_COLOR;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_LIGATURE;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_KERN;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_STRIKETHROUGH_STYLE;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_UNDERLINES_STYLE;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_STROKE_COLOR;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_STROKE_WIDTH;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_SHADOW;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_VERTICAL_GLYPH_FORM;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_WRITING_DIRECTION;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_TEXT_EFFECT;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_ATTACHMENT;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_LINK;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_BASELINE_OFFSET;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_UNDERLINE_COLOR;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_STRIKETHROUGH_COLOR;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_OBLIQUENESS;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_EXPANSION;

@property (nonatomic, readonly) NSNumber *ATTRIBUTE_UNDERLINE_STYLE_NONE;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_UNDERLINE_STYLE_SINGLE;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_UNDERLINE_STYLE_THICK;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_UNDERLINE_STYLE_DOUBLE;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_UNDERLINE_PATTERN_SOLID;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_UNDERLINE_PATTERN_DOT;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_UNDERLINE_PATTERN_DASH;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_UNDERLINE_PATTERN_DASH_DOT;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_UNDERLINE_PATTERN_DASH_DOT_DOT;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_UNDERLINE_BY_WORD;

@property (nonatomic, readonly) NSNumber *ATTRIBUTE_WRITING_DIRECTION_EMBEDDING;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_WRITING_DIRECTION_OVERRIDE;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_WRITING_DIRECTION_NATURAL;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_WRITING_DIRECTION_LEFT_TO_RIGHT;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_WRITING_DIRECTION_RIGHT_TO_LEFT;

@property (nonatomic, readonly) NSNumber *ATTRIBUTE_LINE_BREAK_BY_WORD_WRAPPING;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_LINE_BREAK_BY_CHAR_WRAPPING;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_LINE_BREAK_BY_CLIPPING;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_LINE_BREAK_BY_TRUNCATING_HEAD;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_LINE_BREAK_BY_TRUNCATING_TAIL;
@property (nonatomic, readonly) NSNumber *ATTRIBUTE_LINE_BREAK_BY_TRUNCATING_MIDDLE;

@property (nonatomic, readonly) NSString *ATTRIBUTE_LETTERPRESS_STYLE;

#endif

@property (nonatomic, readonly) NSNumber *TABLE_VIEW_SEPARATOR_STYLE_NONE;
@property (nonatomic, readonly) NSNumber *TABLE_VIEW_SEPARATOR_STYLE_SINGLE_LINE;

#ifdef USE_TI_UIACTIVITYINDICATORSTYLE
@property (nonatomic, readonly) TiUIActivityIndicatorStyleProxy *ActivityIndicatorStyle;
#endif

#if defined(USE_TI_UISHORTCUT) || defined(USE_TI_UISHORTCUTITEM)
@property (nonatomic, readonly) TiUIShortcutProxy *Shortcut;
#endif

@end

#endif
