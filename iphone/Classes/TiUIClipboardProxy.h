/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-Present by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#ifdef USE_TI_UICLIPBOARD
@import TitaniumKit.ObjcProxy;

@protocol ClipboardExports <JSExport>

// Properties (and accessors)
READONLY_PROPERTY(bool, allowCreation, AllowCreation);
READONLY_PROPERTY(NSString *, name, Name);
READONLY_PROPERTY(bool, unique, Unique);

// Methods
- (void)clearData:(NSString *)type;
- (void)clearText;
- (id)getData:(NSString *)type;
- (NSArray<NSDictionary<NSString *, id> *> *)getItems;
- (NSString *)getText;
- (bool)hasData:(id)type;
- (bool)hasText;
- (bool)hasURLs;
- (bool)hasImages;
- (bool)hasColors;
JSExportAs(setData,
           -(void)setData
           : (NSString *)type withData
           : (id)data);
- (void)setText:(NSString *)text;
- (void)setItems:(NSDictionary<NSString *, id> *)items;
- (void)remove;

@end

@interface TiUIClipboardProxy : ObjcProxy <ClipboardExports> {
  @private
  UIPasteboard *_pasteboard;
  BOOL shouldCreatePasteboard;
  BOOL isUnique;
}

- (id)initWithProperties:(NSDictionary *)dict;
- (id)getData_:(NSString *)mimeType;

@end
#endif
