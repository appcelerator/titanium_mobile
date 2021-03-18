/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UITABLEVIEW

#import "TiUITableViewRowProxy.h"
#import "TiSelectedCellbackgroundView.h"
#import "TiUITableView.h"
#import "TiUITableViewAction.h"
#import "TiUITableViewSectionProxy.h"
#import <TitaniumKit/ImageLoader.h>
#import <TitaniumKit/TiLayoutQueue.h>
#import <TitaniumKit/TiUtils.h>
#import <TitaniumKit/TiViewProxy.h>
#import <TitaniumKit/Webcolor.h>
#import <libkern/OSAtomic.h>
#import <stdatomic.h>

NSString *const defaultRowTableClass = @"_default_";
#define CHILD_ACCESSORY_WIDTH 20.0
#define CHECK_ACCESSORY_WIDTH 20.0
#define DETAIL_ACCESSORY_WIDTH 33.0
#define IOS7_ACCESSORY_EXTRA_OFFSET 15.0
// TODO: Clean this up a bit
#define NEEDS_UPDATE_ROW 1

#ifdef TI_USE_AUTOLAYOUT
@interface TiUITableViewRowContainer : TiLayoutView
#else
@interface TiUITableViewRowContainer : TiUIView
#endif
{
  TiProxy *hitTarget;
  CGPoint hitPoint;
#ifdef TI_USE_AUTOLAYOUT
  CGFloat m_height;
  CGFloat m_width;
#endif
}
@property (nonatomic, retain, readwrite) TiProxy *hitTarget;
@property (nonatomic, assign, readwrite) CGPoint hitPoint;
- (void)clearHitTarget;

@end

TiProxy *DeepScanForProxyOfViewContainingPoint(UIView *targetView, CGPoint point)
{
  if (!CGRectContainsPoint([targetView bounds], point)) {
    return nil;
  }
  for (UIView *subView in [targetView subviews]) {
    TiProxy *subProxy = DeepScanForProxyOfViewContainingPoint(subView, [targetView convertPoint:point toView:subView]);
    if (subProxy != nil) {
      return subProxy;
    }
  }

  //By now, no subviews have claimed ownership.
  if ([targetView respondsToSelector:@selector(proxy)]) {
    return [(TiUIView *)targetView proxy];
  }
  return nil;
}

@implementation TiUITableViewRowContainer
@synthesize hitTarget, hitPoint;

- (id)init
{
  if (self = [super init]) {
    hitPoint = CGPointZero;
  }
  return self;
}

- (void)clearHitTarget
{
  [hitTarget autorelease];
  hitTarget = nil;
}

- (NSString *)apiName
{
  return @"Ti.UI.TableViewRow";
}

- (TiProxy *)hitTarget
{
  TiProxy *result = hitTarget;
  [self clearHitTarget];
  return result;
}

- (UIView *)hitTest:(CGPoint)point withEvent:(UIEvent *)event
{
  UIView *result = [super hitTest:point withEvent:event];
  [self setHitPoint:point];

  if (result == nil) {
    [self setHitTarget:DeepScanForProxyOfViewContainingPoint(self, point)];
    return nil;
  }

  if ([result respondsToSelector:@selector(proxy)]) {
    [self setHitTarget:[(TiUIView *)result proxy]];
  } else {
    [self clearHitTarget];
  }

  return result;
}

- (void)dealloc
{
  [self clearHitTarget];
  [super dealloc];
}

#ifdef TI_USE_AUTOLAYOUT
- (void)initializeTiLayoutView
{
  [super initializeTiLayoutView];
  [self setDefaultWidth:TiDimensionAutoSize];
  [self setDefaultHeight:TiDimensionAutoSize];
  [self setHeight_:@"SIZE"];
}

- (CGFloat)heightIfWidthWere:(CGFloat)width
{
  if (m_width != width) {
    m_width = width;
    m_height = [super heightIfWidthWere:width];
  }
  if (m_height == 0) {
    m_height = [super heightIfWidthWere:width];
  }
  return m_height;
}
#endif

@end

@implementation TiUITableViewRowProxy

@synthesize tableClass, table, section, row, callbackCell;
@synthesize reusable = reusable_;

- (void)_destroy
{
  RELEASE_TO_NIL(tableClass);
  TiThreadPerformOnMainThread(
      ^{
        [rowContainerView removeFromSuperview];
        RELEASE_TO_NIL(rowContainerView);
      },
      YES);
  rowContainerView = nil;
  [callbackCell setProxy:nil];
  callbackCell = nil;
  [super _destroy];
}

- (void)_initWithProperties:(NSDictionary *)properties
{
  [super _initWithProperties:properties];

  [self initializeProperty:@"enabled" defaultValue:NUMBOOL(YES)];
  [self initializeProperty:@"backgroundRepeat" defaultValue:NUMBOOL(NO)];

  self.modelDelegate = self;
}

- (NSString *)tableClass
{
  if (tableClass == nil) {
    // must use undefined key since class is a special
    // property on the NSObject class
    id value = [self valueForUndefinedKey:@"className"];
    if (value == nil) {
      value = defaultRowTableClass;
    }
    // tableClass must always be a string so we coerce it
    tableClass = [[TiUtils stringValue:value] retain];
  }
  return tableClass;
}

- (id)height
{
  return [self valueForUndefinedKey:@"height"];
}

- (void)setHeight:(id)value
{
  height = [TiUtils dimensionValue:value];
  [self replaceValue:value forKey:@"height" notification:YES];
}

- (id)backgroundLeftCap
{
  return [self valueForUndefinedKey:@"backgroundLeftCap"];
}

- (void)setBackgroundLeftCap:(id)value
{
  leftCap = TiDimensionFromObject(value);
  [self replaceValue:value forKey:@"backgroundLeftCap" notification:NO];
  if (callbackCell != nil) {
    [self configureBackground:callbackCell];
  }
}

- (id)backgroundTopCap
{
  return [self valueForUndefinedKey:@"backgroundTopCap"];
}

- (void)setBackgroundTopCap:(id)value
{
  topCap = TiDimensionFromObject(value);
  [self replaceValue:value forKey:@"backgroundTopCap" notification:NO];
  if (callbackCell != nil) {
    [self configureBackground:callbackCell];
  }
}

// Special handling to try and avoid Apple's detection of private API 'layout'
- (void)setValue:(id)value forUndefinedKey:(NSString *)key
{
  if ([key isEqualToString:[@"lay" stringByAppendingString:@"out"]]) {
    //CAN NOT USE THE MACRO
    if (ENFORCE_BATCH_UPDATE) {
      if (updateStarted) {
        [self setTempProperty:value forKey:key];
        return;
      } else if (!allowLayoutUpdate) {
        return;
      }
    }
#ifndef TI_USE_AUTOLAYOUT
    layoutProperties.layoutStyle = TiLayoutRuleFromObject(value);
#else
    [[self currentRowContainerView] setLayout_:value];
#endif
    [self replaceValue:value
                forKey:[@"lay" stringByAppendingString:@"out"]
          notification:YES];
    return;
  }
  [super setValue:value forUndefinedKey:key];
}
- (CGFloat)sizeWidthForDecorations:(CGFloat)oldWidth forceResizing:(BOOL)force
{
  CGFloat width = oldWidth;
  BOOL updateForiOS7 = NO;
  if (force || !configuredChildren) {
    if ([TiUtils boolValue:[self valueForKey:@"hasChild"] def:NO]) {
      width -= CHILD_ACCESSORY_WIDTH;
      updateForiOS7 = YES;
    } else if ([TiUtils boolValue:[self valueForKey:@"hasDetail"] def:NO]) {
      width -= DETAIL_ACCESSORY_WIDTH;
      updateForiOS7 = YES;
    } else if ([TiUtils boolValue:[self valueForKey:@"hasCheck"] def:NO]) {
      width -= CHECK_ACCESSORY_WIDTH;
      updateForiOS7 = YES;
    }

    id rightImage = [self valueForKey:@"rightImage"];
    if (rightImage != nil) {
      NSURL *url = [TiUtils toURL:rightImage proxy:self];
      UIImage *image = [[ImageLoader sharedLoader] loadImmediateImage:url];
      width -= [image size].width;
    }

    id leftImage = [self valueForKey:@"leftImage"];
    if (leftImage != nil) {
      NSURL *url = [TiUtils toURL:leftImage proxy:self];
      UIImage *image = [[ImageLoader sharedLoader] loadImmediateImage:url];
      width -= [image size].width;
    }
  }

  if (updateForiOS7) {
    width -= IOS7_ACCESSORY_EXTRA_OFFSET;
  }

  return width;
}

- (CGFloat)rowHeight:(CGFloat)width
{
  if (TiDimensionIsDip(height)) {
    return height.value;
  }
  CGFloat result = 0;
#ifndef TI_USE_AUTOLAYOUT
  if (TiDimensionIsAuto(height) || TiDimensionIsAutoSize(height) || TiDimensionIsUndefined(height)) {
    result = [self minimumParentHeightForSize:CGSizeMake(width, [self table].bounds.size.height)];
  }
  if (TiDimensionIsPercent(height) && [self table] != nil) {
    result = TiDimensionCalculateValue(height, [self table].bounds.size.height);
  }
#else
  result = [(TiLayoutView *)[self currentRowContainerView] heightIfWidthWere:width];
  result = result == 0 ? 0 : result + 1;
#endif
  return (result == 0) ? [table tableRowHeight:0] : result;
}

- (void)updateRow:(NSDictionary *)data withObject:(NSDictionary *)properties
{
  modifyingRow = YES;
  [super _initWithProperties:data];

  // check to see if we have a section header change, too...
  if ([data objectForKey:@"header"]) {
    [section setValue:[data objectForKey:@"header"] forUndefinedKey:@"headerTitle"];
    // we can return since we're reloading the section, will cause the
    // row to be repainted at the same time
  }
  if ([data objectForKey:@"footer"]) {
    [section setValue:[data objectForKey:@"footer"] forUndefinedKey:@"footerTitle"];
    // we can return since we're reloading the section, will cause the
    // row to be repainted at the same time
  }
  modifyingRow = NO;
}

- (void)configureTitle:(UITableViewCell *)cell
{
  UILabel *textLabel = [cell textLabel];
  NSString *title = [TiUtils stringValue:[self valueForKey:@"title"]];

  if (title != nil) {
    [textLabel setText:title]; //UILabel already checks to see if it hasn't changed.

    UIColor *textColor = [[TiUtils colorValue:[self valueForKey:@"color"]] _color];
    [textLabel setTextColor:(textColor == nil) ? [UIColor blackColor] : textColor];

    UIColor *selectedTextColor = [[TiUtils colorValue:[self valueForKey:@"selectedColor"]] _color];
    [textLabel setHighlightedTextColor:(selectedTextColor == nil) ? [UIColor whiteColor] : selectedTextColor];

    id fontValue = [self valueForKey:@"font"];
    UIFont *font;
    if (fontValue != nil) {
      font = [[TiUtils fontValue:fontValue] font];
    } else {
      font = [UIFont systemFontOfSize:0];
    }
    [textLabel setFont:font];
  } else {
    [textLabel setText:nil];
  }
}

- (void)configureRightSide:(UITableViewCell *)cell
{
  BOOL hasChild = [TiUtils boolValue:[self valueForKey:@"hasChild"] def:NO];
  if (hasChild) {
    cell.accessoryType = UITableViewCellAccessoryDisclosureIndicator;
  } else {
    BOOL hasDetail = [TiUtils boolValue:[self valueForKey:@"hasDetail"] def:NO];
    if (hasDetail) {
      cell.accessoryType = UITableViewCellAccessoryDetailDisclosureButton;
    } else {
      BOOL hasCheck = [TiUtils boolValue:[self valueForKey:@"hasCheck"] def:NO];
      if (hasCheck) {
        cell.accessoryType = UITableViewCellAccessoryCheckmark;
      } else {
        cell.accessoryType = UITableViewCellAccessoryNone;
      }
    }
  }
  id rightImage = [self valueForKey:@"rightImage"];
  if (rightImage != nil) {
    NSURL *url = [TiUtils toURL:rightImage proxy:self];
    UIImage *image = [[ImageLoader sharedLoader] loadImmediateImage:url];
    cell.accessoryView = [[[UIImageView alloc] initWithImage:image] autorelease];
  } else {
    cell.accessoryView = nil;
  }
}

- (void)configureBackground:(UITableViewCell *)cell
{
  if (![self shouldUseBackgroundView]) {
    return; // Ignore custom selection styles for native selections
  }

  [(TiUITableViewCell *)cell setBackgroundGradient_:[self valueForKey:@"backgroundGradient"]];
  if (IS_NULL_OR_NIL([self valueForKey:@"selectedBackgroundGradient"])) {
    [(TiUITableViewCell *)cell setSelectedBackgroundGradient_:[self valueForKey:@"selectedBackgroundGradient"]];
  }
  [(TiUITableViewCell *)cell setBackgroundSelectedGradient_:[self valueForKey:@"backgroundSelectedGradient"]];

  id bgImage = [self valueForKey:@"backgroundImage"];
  id selBgColor = [self valueForKey:@"backgroundSelectedColor"];
  if (IS_NULL_OR_NIL(selBgColor)) {
    selBgColor = [self valueForKey:@"selectedBackgroundColor"];

    if (!IS_NULL_OR_NIL(selBgColor)) {
      DEPRECATED_REPLACED(@"selectedBackgroundColor", @"10.0.0", @"backgroundSelectedColor");
    }
  }

  if (bgImage != nil) {
    NSURL *url = [TiUtils toURL:bgImage proxy:(TiProxy *)table.proxy];
    UIImage *image = [[ImageLoader sharedLoader] loadImmediateStretchableImage:url withLeftCap:leftCap topCap:topCap];
    if (![cell.backgroundView isKindOfClass:[UIImageView class]]) {
      UIImageView *view_ = [[[UIImageView alloc] initWithFrame:CGRectZero] autorelease];
      cell.backgroundView = view_;
    }
    if (image != ((UIImageView *)cell.backgroundView).image) {
      ((UIImageView *)cell.backgroundView).image = image;
    }
  } else if (cell.backgroundView != nil && [cell.backgroundView isKindOfClass:[UIImageView class]] && ((UIImageView *)cell.backgroundView).image != nil) {
    cell.backgroundView = nil;
  }

  id selBgImage = [self valueForKey:@"backgroundSelectedImage"];
  if (IS_NULL_OR_NIL(selBgImage)) {
    selBgImage = [self valueForKey:@"selectedBackgroundImage"];

    if (!IS_NULL_OR_NIL(selBgImage)) {
      DEPRECATED_REPLACED(@"selectedBackgroundImage", @"10.0.0", @"backgroundSelectedImage");
    }
  }
  if (selBgImage != nil) {
    NSURL *url = [TiUtils toURL:selBgImage proxy:(TiProxy *)table.proxy];
    UIImage *image = [[ImageLoader sharedLoader] loadImmediateStretchableImage:url withLeftCap:leftCap topCap:topCap];
    if (![cell.selectedBackgroundView isKindOfClass:[UIImageView class]]) {
      UIImageView *view_ = [[[UIImageView alloc] initWithFrame:CGRectZero] autorelease];
      cell.selectedBackgroundView = view_;
    }
    if (image != ((UIImageView *)cell.selectedBackgroundView).image) {
      ((UIImageView *)cell.selectedBackgroundView).image = image;
    }

    UIColor *theColor = [TiUtils colorValue:selBgColor].color;
    cell.selectedBackgroundView.backgroundColor = ((theColor == nil) ? [UIColor clearColor] : theColor);
  } else {
    if (![cell.selectedBackgroundView isKindOfClass:[TiSelectedCellBackgroundView class]]) {
      cell.selectedBackgroundView = [[[TiSelectedCellBackgroundView alloc] initWithFrame:CGRectZero] autorelease];
    }
    TiSelectedCellBackgroundView *selectedBGView = (TiSelectedCellBackgroundView *)cell.selectedBackgroundView;
    selectedBGView.grouped = [[table tableView] style] == UITableViewStyleGrouped;
    UIColor *theColor = [TiUtils colorValue:selBgColor].color;
    if (theColor == nil) {
      switch (cell.selectionStyle) {
      case UITableViewCellSelectionStyleGray:
        theColor = [Webcolor webColorNamed:@"#d9d9d9"];
        break;
      case UITableViewCellSelectionStyleNone:
        theColor = [UIColor clearColor];
        break;
      case UITableViewCellSelectionStyleBlue:
        theColor = [Webcolor webColorNamed:@"#0272ed"];
        break;
      default:
        theColor = [Webcolor webColorNamed:@"#e0e0e0"];
        break;
      }
    }
    selectedBGView.fillColor = theColor;
    NSUInteger count = section.rowCount.unsignedIntegerValue;
    if (count == 1) {
      selectedBGView.position = TiCellBackgroundViewPositionSingleLine;
    } else {
      if (row == 0) {
        selectedBGView.position = TiCellBackgroundViewPositionTop;
      } else if (row == count - 1) {
        selectedBGView.position = TiCellBackgroundViewPositionBottom;
      } else {
        selectedBGView.position = TiCellBackgroundViewPositionMiddle;
      }
    }
  }
}

- (void)configureLeftSide:(UITableViewCell *)cell
{
  id image = [self valueForKey:@"leftImage"];
  if (image != nil) {
    NSURL *url = [TiUtils toURL:image proxy:(TiProxy *)table.proxy];
    UIImage *image = [[ImageLoader sharedLoader] loadImmediateImage:url];
    if (cell.imageView.image != image) {
      cell.imageView.image = image;
    }
  } else if (cell.imageView != nil && cell.imageView.image != nil) {
    cell.imageView.image = nil;
  }
}

- (void)configureIndentionLevel:(UITableViewCell *)cell
{
  cell.indentationLevel = [TiUtils intValue:[self valueForKey:@"indentionLevel"] def:0];
}

- (void)configureSelectionStyle:(UITableViewCell *)cell
{
  id value = [self valueForKey:@"selectionStyle"];
  if (value == nil) {
    if (table != nil) {
      // look at the tableview if not on the row
      value = [[table proxy] valueForUndefinedKey:@"selectionStyle"];
    }
  }
  if (value != nil) {
    cell.selectionStyle = [TiUtils intValue:value];
  } else {
    cell.selectionStyle = UITableViewCellSelectionStyleBlue;
  }
}

- (UIView *)parentViewForChild:(TiViewProxy *)child
{
  return rowContainerView;
}

- (BOOL)viewAttached
{
  return (callbackCell != nil) && (callbackCell.proxy == self);
}

- (BOOL)canHaveControllerParent
{
  return NO;
}

- (void)redelegateViews:(TiViewProxy *)proxy toView:(UIView *)touchDelegate;
{
  [[proxy view] setTouchDelegate:touchDelegate];
  NSArray *subproxies = [proxy children];
  for (TiViewProxy *childProxy in subproxies) {
    [self redelegateViews:childProxy toView:touchDelegate];
  }
}

- (TiProxy *)parentForBubbling
{
  return section;
}

- (UIView *)view
{
  return rowContainerView;
}

//Private method : For internal use only
- (TiUITableViewRowContainer *)currentRowContainerView
{
  if (rowContainerView == nil) {
    rowContainerView = [[TiUITableViewRowContainer alloc] init];
  }
  return (TiUITableViewRowContainer *)rowContainerView;
}
//Private method :For internal use only. Called from layoutSubviews of the cell.
- (void)triggerLayout
{
  if (modifyingRow) {
    return;
  }
  modifyingRow = YES;
#ifndef TI_USE_AUTOLAYOUT
  [TiLayoutQueue layoutProxy:self];
#endif
  modifyingRow = NO;
}

- (void)prepareTableRowForReuse
{
  if (!self.reusable) {
    [rowContainerView removeFromSuperview];
    return;
  }
  if (![self.tableClass isEqualToString:defaultRowTableClass]) {
    return;
  }
  RELEASE_TO_NIL(rowContainerView);

  // ... But that's not enough. We need to detatch the views
  // for all children of the row, to clean up memory.
  for (TiViewProxy *child in [self children]) {
    [child detachView];
  }
}

- (void)didReceiveMemoryWarning:(NSNotification *)notification
{
  if (self.viewAttached) {
    return;
  }

  RELEASE_TO_NIL(rowContainerView);
  for (TiViewProxy *child in [self children]) {
    [child detachView];
  }
}

- (void)configureChildren:(UITableViewCell *)cell
{
  // this method is called when the cell is initially created
  // to be initialized. on subsequent repaints of a re-used
  // table cell, the updateChildren below will be called instead
  configuredChildren = NO;
  if ([[self children] count] > 0) {
    UIView *contentView = cell.contentView;
    CGRect rect = [contentView bounds];
    rect.origin = CGPointZero;
    if (self.reusable || (rowContainerView == nil)) {
      if (self.reusable) {
        RELEASE_TO_NIL(rowContainerView);
        for (UIView *subview in [[cell contentView] subviews]) {
          if ([subview isKindOfClass:[TiUITableViewRowContainer class]]) {
            rowContainerView = [subview retain];
            break;
          }
        }
      }
      NSArray *rowChildren = [self children];
      if (self.reusable && (rowContainerView != nil)) {
        __block BOOL canReproxy = YES;
        NSArray *existingSubviews = [rowContainerView subviews];
        if ([rowChildren count] != [existingSubviews count]) {
          canReproxy = NO;
        } else {
          [rowChildren enumerateObjectsUsingBlock:^(TiViewProxy *proxy, NSUInteger idx, BOOL *stop) {
            TiUIView *uiview = [existingSubviews objectAtIndex:idx];
            if (![uiview validateTransferToProxy:proxy deep:YES]) {
              canReproxy = NO;
              *stop = YES;
            }
          }];
        }
        if (!canReproxy && ([existingSubviews count] > 0)) {
          DebugLog(@"[ERROR] TableViewRow structures for className %@ does not match", self.tableClass);
          [existingSubviews enumerateObjectsUsingBlock:^(TiUIView *child, NSUInteger idx, BOOL *stop) {
            [(TiViewProxy *)child.proxy detachView];
          }];
        }
      }
      if (rowContainerView == nil) {
        rowContainerView = [[TiUITableViewRowContainer alloc] initWithFrame:rect];
      } else {
        [rowContainerView setFrame:rect];
      }
      if ([rowContainerView superview] == nil) {
        [contentView addSubview:rowContainerView];
      }
      [rowContainerView setBackgroundColor:[UIColor clearColor]];
      [rowContainerView setAutoresizingMask:UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight];

      NSArray *existingSubviews = [rowContainerView subviews];
      [rowChildren enumerateObjectsUsingBlock:^(TiViewProxy *proxy, NSUInteger idx, BOOL *stop) {
        TiUIView *uiview = idx < [existingSubviews count] ? [existingSubviews objectAtIndex:idx] : nil;
        if (!CGRectEqualToRect([proxy sandboxBounds], rect)) {
          [proxy setSandboxBounds:rect];
        }
        [proxy windowWillOpen];
        [proxy setReproxying:YES];
        [uiview transferProxy:proxy deep:YES];
        [self redelegateViews:proxy toView:contentView];
        if (uiview == nil) {
          [rowContainerView addSubview:[proxy view]];
        }
        [proxy setReproxying:NO];
      }];
    } else {
      [[self children] enumerateObjectsUsingBlock:^(TiViewProxy *proxy, NSUInteger idx, BOOL *stop) {
        [self redelegateViews:proxy toView:contentView];
      }];
      [rowContainerView setFrame:rect];
      [contentView addSubview:rowContainerView];
    }
#ifdef TI_USE_AUTOLAYOUT
    [rowContainerView performSelector:@selector(updateWidthAndHeight)];
#endif
  }
  configuredChildren = YES;
}

- (void)configureTintColor:(UITableViewCell *)cell
{
  UIColor *theTint = nil;
  id theColor = [self valueForUndefinedKey:@"tintColor"];
  if (theColor != nil) {
    theTint = [[TiUtils colorValue:theColor] color];
  }
  if (theTint == nil) {
    theTint = [[table tableView] tintColor];
  }
  [cell setTintColor:theTint];
}

- (void)initializeTableViewCell:(UITableViewCell *)cell
{
  modifyingRow = YES;
  [self configureTintColor:cell];
  [self configureTitle:cell];
  [self configureSelectionStyle:cell];
  [self configureLeftSide:cell];
  [self configureRightSide:cell];
  [self configureBackground:cell];
  [self configureIndentionLevel:cell];
  [self configureChildren:cell];
  cell.accessibilityLabel = [TiUtils stringValue:[self valueForUndefinedKey:@"accessibilityLabel"]];
  cell.accessibilityValue = [TiUtils stringValue:[self valueForUndefinedKey:@"accessibilityValue"]];
  cell.accessibilityHint = [TiUtils stringValue:[self valueForUndefinedKey:@"accessibilityHint"]];
  modifyingRow = NO;
}

- (BOOL)isAttached
{
  return (table != nil) && ([self parent] != nil);
}

// TODO: SUPER MEGA UGLY but it's the only workaround for now.  zindex does NOT work with table rows.
// TODO: Add child locking methods for whenever we have to touch children outside TiViewProxy
- (void)willShow
{
  NSArray *subproxies = [self children];
  for (TiViewProxy *child in subproxies) {
    [child setParentVisible:YES];
  }
}

- (void)triggerAttach
{
  if (!attaching && ![self viewAttached]) {
    attaching = YES;
    [self windowWillOpen];
    [self willShow];
    attaching = NO;
  }
}

- (void)updateRow:(TiUITableViewAction *)action
{
  [table dispatchAction:action];
}

- (void)triggerRowUpdate
{
  if ([self isAttached] && self.viewAttached && !modifyingRow && !attaching) {
    TiUITableViewAction *action = [[[TiUITableViewAction alloc] initWithObject:self
                                                                     animation:nil
                                                                          type:TiUITableViewActionRowReload] autorelease];
    TiThreadPerformOnMainThread(
        ^{
          [self updateRow:action];
        },
        NO);
  }
}

- (void)windowWillOpen
{
  attaching = YES;
  [super windowWillOpen];
  [self setParentVisible:YES];
  attaching = NO;
}

- (void)triggerUpdateIfHeightChanged
{
  TiThreadPerformOnMainThread(
      ^{
        if ([self viewAttached] && rowContainerView != nil) {
          CGFloat curHeight = rowContainerView.bounds.size.height;
          CGSize newSize = [callbackCell computeCellSize];

          // TIMOB-19241: Fix the keyboard from losing focus after 1 character
          UITableViewCellSeparatorStyle separatorStyle = [[table tableView] separatorStyle];
          CGFloat heightDifference = fabs(newSize.height - curHeight);

          if (((separatorStyle != UITableViewCellSeparatorStyleNone) && (heightDifference >= 1.0)) || ((separatorStyle == UITableViewCellSeparatorStyleNone) && (heightDifference > 0.0))) {
            DeveloperLog(@"Height changing from %.1f to %.1f. Triggering update.", curHeight, newSize.height);
            [self triggerRowUpdate];
          } else {
            DeveloperLog(@"Height does not change. Just laying out children. Height %.1f", curHeight);
            // TIMOB-13121: Ensure touchdelegate is set if we are not going to reconstruct the row.
            if ([rowContainerView superview] != nil) {
              UIView *contentView = [rowContainerView superview];
              [[self children] enumerateObjectsUsingBlock:^(TiViewProxy *proxy, NSUInteger idx, BOOL *stop) {
                [self redelegateViews:proxy toView:contentView];
              }];
            }
            [callbackCell setNeedsDisplay];
          }
        } else {
          [callbackCell setNeedsDisplay];
        }
      },
      NO);
}

- (void)contentsWillChange
{
  if (!attaching) {
    [self triggerUpdateIfHeightChanged];
  }
}

- (void)childWillResize:(TiViewProxy *)child
{
  [self triggerUpdateIfHeightChanged];
}

- (TiProxy *)touchedViewProxyInCell:(UITableViewCell *)targetCell atPoint:(CGPoint *)point
{
  for (TiUITableViewRowContainer *thisContainer in [[targetCell contentView] subviews]) {
    if ([thisContainer isKindOfClass:[TiUITableViewRowContainer class]]) {
      TiProxy *result = [thisContainer hitTarget];
      *point = [thisContainer hitPoint];
      if (result != nil) {
        return result;
      }
    }
  }
  return self;
}

- (id)createEventObject:(id)initialObject
{
  NSMutableDictionary *dict = nil;
  if (initialObject == nil) {
    dict = [NSMutableDictionary dictionary];
  } else {
    dict = [NSMutableDictionary dictionaryWithDictionary:initialObject];
  }
  NSInteger index = [table indexForRow:self];
  [dict setObject:NUMINTEGER(index) forKey:@"index"];
  // TODO: We really need to ensure that a row's section is set upon creation - even if this means changing how tables work.
  if (section != nil) {
    [dict setObject:section forKey:@"section"];
  }
  [dict setObject:self forKey:@"row"];
  [dict setObject:self forKey:@"rowData"];
  [dict setObject:NUMBOOL(NO) forKey:@"detail"];
  [dict setObject:NUMBOOL(NO) forKey:@"searchMode"];

  return dict;
}

//TODO: Remove when deprication is done.
- (void)fireEvent:(NSString *)type withObject:(id)obj withSource:(id)source propagate:(BOOL)propagate reportSuccess:(BOOL)report errorCode:(int)code message:(NSString *)message;
{
  // merge in any row level properties for the event
  if (source != self) {
    obj = [self createEventObject:obj];
  }
  [callbackCell handleEvent:type];
  [super fireEvent:type withObject:obj withSource:source propagate:propagate reportSuccess:report errorCode:code message:message];
}

- (void)fireEvent:(NSString *)type withObject:(id)obj propagate:(BOOL)propagate reportSuccess:(BOOL)report errorCode:(NSInteger)code message:(NSString *)message;
{
  [callbackCell handleEvent:type];
  [super fireEvent:type withObject:obj propagate:propagate reportSuccess:report errorCode:code message:message];
}

- (BOOL)shouldUseBackgroundView
{
  return [self valueForKey:@"backgroundSelectedColor"]
      || [self valueForKey:@"selectedBackgroundColor"]
      || [self valueForKey:@"backgroundImage"]
      || [self valueForKey:@"backgroundSelectedImage"]
      || [self valueForKey:@"selectedBackgroundImage"]
      || [self valueForKey:@"backgroundLeftCap"]
      || [self valueForKey:@"backgroundTopCap"];
}

- (void)setBackgroundSelectedColor:(id)arg
{
  [self replaceValue:arg forKey:@"backgroundSelectedColor" notification:NO];
  TiThreadPerformOnMainThread(
      ^{
        if ([self viewAttached]) {
          [self configureBackground:callbackCell];
        }
      },
      NO);
}
- (void)setSelectedBackgroundColor:(id)arg
{
  DEPRECATED_REPLACED(@"selectedBackgroundColor", @"10.0.0", @"backgroundSelectedColor");
  [self replaceValue:arg forKey:@"selectedBackgroundColor" notification:NO];
  TiThreadPerformOnMainThread(
      ^{
        if ([self viewAttached]) {
          [self configureBackground:callbackCell];
        }
      },
      NO);
}

- (void)setBackgroundImage:(id)arg
{
  [self replaceValue:arg forKey:@"backgroundImage" notification:NO];
  TiThreadPerformOnMainThread(
      ^{
        if ([self viewAttached]) {
          [self configureBackground:callbackCell];
        }
      },
      NO);
}

- (void)setBackgroundSelectedImage:(id)arg
{
  [self replaceValue:arg forKey:@"backgroundSelectedImage" notification:NO];
  TiThreadPerformOnMainThread(
      ^{
        if ([self viewAttached]) {
          [self configureBackground:callbackCell];
        }
      },
      NO);
}
- (void)setSelectedBackgroundImage:(id)arg
{
  DEPRECATED_REPLACED(@"selectedBackgroundImage", @"10.0.0", @"backgroundSelectedImage");
  [self replaceValue:arg forKey:@"selectedBackgroundImage" notification:NO];
  TiThreadPerformOnMainThread(
      ^{
        if ([self viewAttached]) {
          [self configureBackground:callbackCell];
        }
      },
      NO);
}

- (void)setBackgroundGradient:(id)arg
{
  TiGradient *newGradient = [TiGradient gradientFromObject:arg proxy:self];
  [self replaceValue:newGradient forKey:@"backgroundGradient" notification:NO];
  TiThreadPerformOnMainThread(
      ^{
        [callbackCell setBackgroundGradient_:newGradient];
      },
      NO);
}

- (void)setBackgroundSelectedGradient:(id)arg
{
  TiGradient *newGradient = [TiGradient gradientFromObject:arg proxy:self];
  [self replaceValue:newGradient forKey:@"backgroundSelectedGradient" notification:NO];
  TiThreadPerformOnMainThread(
      ^{
        [callbackCell setBackgroundSelectedGradient_:newGradient];
      },
      NO);
}
- (void)setSelectedBackgroundGradient:(id)arg
{
  DEPRECATED_REPLACED(@"selectedBackgroundGradient", @"10.0.0", @"backgroundSelectedGradient");
  TiGradient *newGradient = [TiGradient gradientFromObject:arg proxy:self];
  [self replaceValue:newGradient forKey:@"selectedBackgroundGradient" notification:NO];
  TiThreadPerformOnMainThread(
      ^{
        [callbackCell setSelectedBackgroundGradient_:newGradient];
      },
      NO);
}

- (void)propertyChanged:(NSString *)key oldValue:(id)oldValue newValue:(id)newValue proxy:(TiProxy *)proxy
{
  // these properties should trigger a re-paint for the row
  static NSSet *TableViewRowProperties = nil;
  if (TableViewRowProperties == nil) {
    TableViewRowProperties = [[NSSet alloc] initWithObjects:
                                                @"title", @"accessibilityLabel", @"backgroundImage",
                                            @"leftImage", @"hasDetail", @"hasCheck", @"hasChild",
                                            @"indentionLevel", @"selectionStyle", @"color", @"selectedColor",
                                            @"height", @"width", @"backgroundColor", @"rightImage", @"tintColor",
                                            nil];
  }

  if ([TableViewRowProperties member:key] != nil) {
    TiThreadPerformOnMainThread(
        ^{
          if (![self viewAttached]) {
            return;
          }
          if ([key isEqualToString:@"height"] || [key isEqualToString:@"width"] || [key isEqualToString:@"indentionLevel"]) {
            [self triggerRowUpdate];
          } else if ([key isEqualToString:@"title"] || [key isEqualToString:@"color"] || [key isEqualToString:@"font"] || [key isEqualToString:@"selectedColor"]) {
            [self configureTitle:callbackCell];
            [callbackCell setNeedsDisplay];
          } else if ([key isEqualToString:@"hasCheck"] || [key isEqualToString:@"hasChild"] || [key isEqualToString:@"hasDetail"] || [key isEqualToString:@"rightImage"]) {
            [self configureRightSide:callbackCell];
            [self triggerUpdateIfHeightChanged];
          } else if ([key isEqualToString:@"leftImage"]) {
            [self configureLeftSide:callbackCell];
            [self triggerUpdateIfHeightChanged];
          } else if ([key isEqualToString:@"backgroundImage"]) {
            [self configureBackground:callbackCell];
            [callbackCell setNeedsDisplay];
          } else if ([key isEqualToString:@"backgroundColor"]) {
            [callbackCell setBackgroundColor:[[TiUtils colorValue:newValue] color]];
            [self triggerRowUpdate];
          } else if ([key isEqualToString:@"accessibilityLabel"]) {
            callbackCell.accessibilityLabel = [TiUtils stringValue:newValue];
          } else if ([key isEqualToString:@"tintColor"]) {
            [self configureTintColor:callbackCell];
          }
        },
        NO);
  }
}

- (TiDimension)defaultAutoHeightBehavior:(id)unused
{
  return TiDimensionAutoSize;
}
@end

#endif
