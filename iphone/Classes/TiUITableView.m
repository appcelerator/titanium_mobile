/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2014 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UITABLEVIEW

#import "TiUITableView.h"
#import "TiUITableViewProxy.h"
#import <TitaniumKit/ImageLoader.h>
#import <TitaniumKit/TiApp.h>
#import <TitaniumKit/TiLayoutQueue.h>
#import <TitaniumKit/TiProxy.h>
#import <TitaniumKit/TiUtils.h>
#import <TitaniumKit/TiViewProxy.h>
#import <TitaniumKit/TiWindowProxy.h>
#import <TitaniumKit/WebFont.h>
#import <TitaniumKit/Webcolor.h>

#define DEFAULT_SECTION_HEADERFOOTER_HEIGHT 29.0
#define GROUPED_MARGIN_WIDTH 18.0

@interface TiUIView (eventHandler)
;
- (void)handleListenerRemovedWithEvent:(NSString *)event;
- (void)handleListenerAddedWithEvent:(NSString *)event;
@end

@interface TiUITableView ()
@property (nonatomic, copy, readwrite) NSString *searchString;
@property (nonatomic, copy, readwrite) NSString *searchedString;

- (void)updateSearchResultIndexes;
- (CGFloat)computeRowWidth;
@end

@implementation TiUITableViewCell
@synthesize hitPoint, proxy;
#pragma mark Touch event handling

// TODO: Replace callback cells with blocks by changing fireEvent: to take special-case
// code which will allow better interactions with UIControl elements (such as buttons)
// and table rows/cells.
- (id)initWithStyle:(UITableViewCellStyle)style_ reuseIdentifier:(NSString *)reuseIdentifier_ row:(TiUITableViewRowProxy *)row_
{
  if (self = [super initWithStyle:style_ reuseIdentifier:reuseIdentifier_]) {
    proxy = [row_ retain];
    [proxy setCallbackCell:self];
    self.exclusiveTouch = YES;
  }

  return self;
}

- (void)dealloc
{
  [proxy setCallbackCell:nil];

  RELEASE_TO_NIL(proxy);
  RELEASE_TO_NIL(gradientLayer);
  RELEASE_TO_NIL(backgroundGradient);
  RELEASE_TO_NIL(selectedBackgroundGradient);
  [super dealloc];
}

- (void)setProxy:(TiUITableViewRowProxy *)proxy_
{
  if (proxy == proxy_) {
    return;
  }

  if ([proxy callbackCell] == self) {
    [proxy setCallbackCell:nil];
  }
  [proxy release];
  proxy = [proxy_ retain];
}

- (CGSize)computeCellSize
{
  CGFloat width = 0;
  if ([proxy table] != nil) {
    width = [proxy sizeWidthForDecorations:[[proxy table] computeRowWidth] forceResizing:YES];
  }
  CGFloat height = [proxy rowHeight:width];
  height = [[proxy table] tableRowHeight:height];

  // If there is a separator, then it's included as part of the row height as the system, so remove the pixel for it
  // from our cell size
  if ([[[proxy table] tableView] separatorStyle] == UITableViewCellSeparatorStyleSingleLine) {
    height -= 1;
  }

  return CGSizeMake(width, height);
}

- (void)prepareForReuse
{
  [super prepareForReuse];

  if (proxy.callbackCell == self) {
    [proxy prepareTableRowForReuse];
  }
}

- (UIView *)hitTest:(CGPoint)point withEvent:(UIEvent *)event
{
  hitPoint = point;
  return [super hitTest:point withEvent:event];
}

- (void)touchesBegan:(NSSet *)touches withEvent:(UIEvent *)event
{
  if (([[event touchesForView:self.contentView] count] > 0) || ([[event touchesForView:self.accessoryView] count] > 0)
      || ([[event touchesForView:self.imageView] count] > 0) || ([[event touchesForView:self.proxy.currentRowContainerView] count] > 0)) {
    if ([proxy _hasListeners:@"touchstart"]) {
      UITouch *touch = [touches anyObject];
      NSDictionary *evt = [self payloadWithTouch:touch];
      [proxy fireEvent:@"touchstart" withObject:evt propagate:YES];
    }
  }

  [super touchesBegan:touches withEvent:event];
}

- (void)touchesMoved:(NSSet *)touches withEvent:(UIEvent *)event
{
  if ([[event touchesForView:self.contentView] count] > 0 || ([[event touchesForView:self.accessoryView] count] > 0)
      || ([[event touchesForView:self.imageView] count] > 0) || ([[event touchesForView:self.proxy.currentRowContainerView] count] > 0)) {
    if ([proxy _hasListeners:@"touchmove"]) {
      UITouch *touch = [touches anyObject];
      NSMutableDictionary *evt = [NSMutableDictionary dictionaryWithDictionary:[TiUtils touchPropertiesToDictionary:touch andView:self]];
      [proxy fireEvent:@"touchmove" withObject:evt propagate:YES];
    }
  }
  [super touchesMoved:touches withEvent:event];
}

- (void)touchesEnded:(NSSet *)touches withEvent:(UIEvent *)event
{
  if (([[event touchesForView:self.contentView] count] > 0) || ([[event touchesForView:self.accessoryView] count] > 0)
      || ([[event touchesForView:self.imageView] count] > 0) || ([[event touchesForView:self.proxy.currentRowContainerView] count] > 0)) {
    if ([proxy _hasListeners:@"touchend"]) {
      UITouch *touch = [touches anyObject];
      NSDictionary *evt = [self payloadWithTouch:touch];
      [proxy fireEvent:@"touchend" withObject:evt propagate:YES];
    }
  }
  [super touchesEnded:touches withEvent:event];
}

- (void)touchesCancelled:(NSSet *)touches withEvent:(UIEvent *)event
{
  if ([proxy _hasListeners:@"touchcancel"]) {

    UITouch *touch = [touches anyObject];
    NSDictionary *evt = [NSMutableDictionary dictionaryWithDictionary:[TiUtils touchPropertiesToDictionary:touch andView:self]];
    [proxy fireEvent:@"touchcancel" withObject:evt propagate:YES];
  }
  [super touchesCancelled:touches withEvent:event];
}

- (void)handleEvent:(NSString *)type
{
  if ([type isEqual:@"touchstart"]) {
    [super setHighlighted:YES animated:NO];
  } else if ([type isEqual:@"touchend"]) {
    [super setHighlighted:NO animated:YES];
  }
}

- (void)layoutSubviews
{
  [super layoutSubviews];
  [gradientLayer setFrame:[self bounds]];

  // In order to avoid ugly visual behavior, whenever a cell is laid out, we MUST relayout the
  // row concurrently.
  [proxy triggerLayout];
}

- (BOOL)selectedOrHighlighted
{
  return [self isSelected] || [self isHighlighted];
}

- (void)updateGradientLayer:(BOOL)useSelected withAnimation:(BOOL)animated
{
  TiGradient *currentGradient = useSelected ? selectedBackgroundGradient : backgroundGradient;

  if (currentGradient == nil) {
    [gradientLayer removeFromSuperlayer];
    //Because there's the chance that the other state still has the gradient, let's keep it around.
    return;
  }

  if (gradientLayer == nil) {
    gradientLayer = [[TiGradientLayer alloc] init];
    [gradientLayer setNeedsDisplayOnBoundsChange:YES];
    [gradientLayer setFrame:[self bounds]];
  }

  [gradientLayer setGradient:currentGradient];

  CALayer *ourLayer = [[[self contentView] layer] superlayer];

  if ([gradientLayer superlayer] != ourLayer) {
    CALayer *contentLayer = [[self contentView] layer];
    [ourLayer insertSublayer:gradientLayer below:contentLayer];

    // If we're working with a row that just has a label drawn on it, we need to
    // set the background color of the label explicitly
    if ([[self textLabel] text] != nil) {
      [[self textLabel] setBackgroundColor:[UIColor clearColor]];
    }
  }
  if (animated) {
    CABasicAnimation *flash = [CABasicAnimation animationWithKeyPath:@"opacity"];
    flash.fromValue = [NSNumber numberWithFloat:0.0];
    flash.toValue = [NSNumber numberWithFloat:1.0];
    flash.duration = 1.0;
    [gradientLayer addAnimation:flash forKey:@"flashAnimation"];
  }
  [gradientLayer setNeedsDisplay];
}

- (void)setSelected:(BOOL)yn animated:(BOOL)animated
{
  [super setSelected:yn animated:animated];
  [self updateGradientLayer:yn | [self isHighlighted] withAnimation:animated];
}

- (void)setHighlighted:(BOOL)yn animated:(BOOL)animated
{
  [super setHighlighted:yn animated:animated];
  [self updateGradientLayer:yn | [self isSelected] withAnimation:animated];
}

- (void)setHighlighted:(BOOL)yn
{
  [super setHighlighted:yn];
  [self updateGradientLayer:yn | [self isSelected] withAnimation:NO];
}

- (void)setSelected:(BOOL)yn
{
  [super setSelected:yn];
  [self updateGradientLayer:yn | [self isHighlighted] withAnimation:NO];
}

- (void)setBackgroundGradient_:(TiGradient *)newGradient
{
  if (newGradient == backgroundGradient) {
    return;
  }
  [backgroundGradient release];
  backgroundGradient = [newGradient retain];

  if (![self selectedOrHighlighted]) {
    [self updateGradientLayer:NO withAnimation:NO];
  }
}

- (void)setBackgroundSelectedGradient_:(TiGradient *)newGradient
{
  if (newGradient == selectedBackgroundGradient) {
    return;
  }
  [selectedBackgroundGradient release];
  selectedBackgroundGradient = [newGradient retain];

  if ([self selectedOrHighlighted]) {
    [self updateGradientLayer:YES withAnimation:NO];
  }
}
- (void)setSelectedBackgroundGradient_:(TiGradient *)newGradient
{
  DEPRECATED_REPLACED(@"selectedBackgroundGradient", @"10.0.0", @"backgroundSelectedGradient");
  [self setBackgroundSelectedGradient_:newGradient];
}

- (NSMutableDictionary *)payloadWithTouch:(UITouch *)touch
{
  NSDictionary *touchProps = [TiUtils touchPropertiesToDictionary:touch andView:self];
  NSMutableDictionary *payload = [proxy createEventObject:nil];
  [payload addEntriesFromDictionary:touchProps];
  return payload;
}

@end

@implementation TiUITableView
#pragma mark Internal
@synthesize searchString, viewWillDetach, searchResultIndexes;

#ifdef TI_USE_AUTOLAYOUT
- (void)initializeTiLayoutView
{
  [super initializeTiLayoutView];
  [self setDefaultHeight:TiDimensionAutoFill];
  [self setDefaultWidth:TiDimensionAutoFill];
}
#endif

- (id)init
{
  if (self = [super init]) {
    hideOnSearch = YES; // Legacy behavior
    filterCaseInsensitive = YES; // defaults to true on search
    filterAnchored = NO; // defaults to false on search
    searchString = @"";
    defaultSeparatorInsets = UIEdgeInsetsZero;
    rowSeparatorInsets = UIEdgeInsetsZero;
    _dimsBackgroundDuringPresentation = YES;
    self.shouldDelayScrolling = YES;
  }
  return self;
}

- (void)dealloc
{
  if ([searchController isActive]) {
    searchController.view.hidden = YES;
    [searchController setActive:NO];
  }
  [[NSNotificationCenter defaultCenter] removeObserver:self];

  if (searchField != nil) {
    [searchField setDelegate:nil];
    RELEASE_TO_NIL(searchField);
  }

  if (headerViewProxy != nil) {
    [headerViewProxy setProxyObserver:nil];
    [headerViewProxy windowWillClose];
  }

  if (footerViewProxy != nil) {
    [footerViewProxy setProxyObserver:nil];
    [footerViewProxy windowWillClose];
  }

  searchController.searchResultsUpdater = nil;
  searchController.delegate = nil;
  RELEASE_TO_NIL(searchController);

  tableview.delegate = nil;
  tableview.dataSource = nil;
  RELEASE_TO_NIL(tableview);
  RELEASE_TO_NIL(dimmingView);
  RELEASE_TO_NIL(sectionIndex);
  RELEASE_TO_NIL(sectionIndexMap);
  RELEASE_TO_NIL(tableHeaderView);
  RELEASE_TO_NIL(searchScreenView);
  RELEASE_TO_NIL(filterAttribute);
  RELEASE_TO_NIL(searchResultIndexes);
  RELEASE_TO_NIL(tableHeaderPullView);
  RELEASE_TO_NIL(_searchedString);
  [searchString release];
#ifdef USE_TI_UIREFRESHCONTROL
  RELEASE_TO_NIL(_refreshControlProxy);
#endif
  [super dealloc];
}

- (BOOL)isScrollable
{
  return [TiUtils boolValue:[self.proxy valueForUndefinedKey:@"scrollable"] def:YES];
}

- (CGFloat)tableRowHeight:(CGFloat)height
{
  if (TiDimensionIsDip(rowHeight)) {
    if (rowHeight.value > height) {
      height = rowHeight.value;
    }
  }
  if (TiDimensionIsDip(minRowHeight)) {
    height = MAX(minRowHeight.value, height);
  }
  if (TiDimensionIsDip(maxRowHeight)) {
    height = MIN(maxRowHeight.value, height);
  }
  return height < 1 ? tableview.rowHeight : height;
}

//Allows use of scrollsToTop property on a table.
//Useful when you have multiple tables in your view, you can
//set which table will respond to tap on status bar to scroll to top.
//http://developer.apple.com/library/ios/#documentation/uikit/reference/UIScrollView_Class/Reference/UIScrollView.html
- (void)setScrollsToTop_:(id)value
{
  [[self tableView] setScrollsToTop:[TiUtils boolValue:value def:YES]];
}

- (void)setBackgroundColor:(TiColor *)color onTable:(UITableView *)table
{
  UIColor *defaultColor = [table style] == UITableViewStylePlain ? [UIColor whiteColor] : [UIColor groupTableViewBackgroundColor];
  UIColor *bgColor = [color _color];

  // WORKAROUND FOR APPLE BUG: 4.2 and lower don't like setting background color for grouped table views on iPad.
  // So, we check the table style and device, and if they match up wrong, we replace the background view with our own.
  if (([table style] == UITableViewStyleGrouped) && [TiUtils isIPad]) {
    UIView *bgView = [[[UIView alloc] initWithFrame:[table frame]] autorelease];
    [table setBackgroundView:bgView];
  }

  [table setBackgroundColor:(bgColor != nil ? bgColor : defaultColor)];
  [[table backgroundView] setBackgroundColor:[table backgroundColor]];

  [table setOpaque:![[table backgroundColor] isEqual:[UIColor clearColor]]];
}

- (BOOL)isSearchStarted
{
  return ([searchController isActive] && searchResultIndexes);
}

- (UITableView *)tableView
{
  if (tableview == nil) {
    id styleObject = [self.proxy valueForKey:@"style"];
    UITableViewStyle style = [TiUtils intValue:styleObject def:UITableViewStylePlain];
#ifdef VERBOSE
    NSLog(@"[DEBUG] Generating a new tableView, and style for %@ is %d", [self.proxy valueForKey:@"style"], style);
    if (styleObject == nil) {
      NSLog(@"[WARN] No style object!");
    }
#endif
    tableview = [[UITableView alloc] initWithFrame:CGRectMake(0, 0, [self bounds].size.width, [self bounds].size.height) style:style];
    tableview.delegate = self;
    tableview.dataSource = self;
    tableview.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;

    // Fixes incorrect heights in iOS 11 as we calculate them internally already
    tableview.estimatedRowHeight = 0;
    tableview.estimatedSectionFooterHeight = 0;
    tableview.estimatedSectionHeaderHeight = 0;

    if (TiDimensionIsDip(rowHeight)) {
      [tableview setRowHeight:rowHeight.value];
    } else {
      // TIMOB-17373 rowHeight on iOS8 is -1. Bug??
      [tableview setRowHeight:44];
    }

    BOOL initBackGround = YES;
    id bgInitValue = [[self proxy] valueForKey:@"backgroundColor"];
    if (style == UITableViewStyleGrouped) {
      //If the style is grouped do not call this method unless a backgroundColor is specified
      initBackGround = (bgInitValue != nil);
    }
    if (initBackGround) {
      [self setBackgroundColor:[TiUtils colorValue:bgInitValue] onTable:tableview];
    }

    [self updateSearchView];

    defaultSeparatorInsets = [tableview separatorInset];

    if (style == UITableViewStylePlain || style == UITableViewStyleGrouped) {
      tableview.layoutMargins = UIEdgeInsetsZero;
    }
    tableview.cellLayoutMarginsFollowReadableWidth = NO;
  }

  if ([tableview superview] != self) {
    [self addSubview:tableview];
  }

  return tableview;
}

- (id)accessibilityElement
{
  return [self tableView];
}

- (NSInteger)indexForRow:(TiUITableViewRowProxy *)row
{
  return [(TiUITableViewProxy *)[self proxy] indexForRow:row];
}

- (NSInteger)sectionIndexForIndex:(NSInteger)theindex
{
  return [(TiUITableViewProxy *)[self proxy] sectionIndexForIndex:theindex];
}

- (TiUITableViewRowProxy *)rowForIndex:(NSInteger)index section:(NSInteger *)section
{
  return [(TiUITableViewProxy *)[self proxy] rowForIndex:index section:section];
}

- (NSIndexPath *)indexPathFromInt:(NSInteger)index
{
  return [(TiUITableViewProxy *)[self proxy] indexPathFromInt:index];
}

- (NSInteger)rowIndexForIndexPath:(NSIndexPath *)index andSections:(NSArray *)sections
{
  NSInteger dataIndex = 0;
  NSInteger c = 0;
  NSInteger rowIndex = [index row];
  NSInteger sectionIdx = [index section];
  for (TiUITableViewSectionProxy *section in sections) {
    if (c == sectionIdx) {
      dataIndex += rowIndex;
      break;
    }
    dataIndex += section.rowCount.integerValue;
    c++;
  }
  return dataIndex;
}

- (void)reloadDataFromCount:(NSUInteger)oldCount toCount:(NSUInteger)newCount animation:(UITableViewRowAnimation)animation
{
  UITableView *table = [self tableView];

  // Apple kindly forces animations whenever we're inserting/deleting in a no-animation
  // way, meaning that we have to explicitly reload the whole visible table to get
  // the "right" behavior.
  if (animation == UITableViewRowAnimationNone) {
    TiThreadPerformOnMainThread(
        ^{
          [table reloadData];
        },
        YES);
    return;
  }

  //Table views hate having 0 sections, so we have to act like it has at least 1.
  oldCount = MAX(1, oldCount);
  newCount = MAX(1, newCount);

  NSUInteger commonality = MIN(oldCount, newCount);
  oldCount -= commonality;
  newCount -= commonality;

  [tableview beginUpdates];
  if (commonality > 0) {
    [table reloadSections:[NSIndexSet indexSetWithIndexesInRange:NSMakeRange(0, commonality)]
         withRowAnimation:animation];
  }
  if (oldCount > 0) {
    [table deleteSections:[NSIndexSet indexSetWithIndexesInRange:NSMakeRange(commonality, oldCount)]
         withRowAnimation:animation];
  }
  if (newCount > 0) {
    [table insertSections:[NSIndexSet indexSetWithIndexesInRange:NSMakeRange(commonality, newCount)]
         withRowAnimation:animation];
  }
  [tableview endUpdates];
}

- (void)replaceData:(NSMutableArray *)data animation:(UITableViewRowAnimation)animation
{
  //Technically, we should assert that sections is non-nil, but this code
  //won't have any problems in the case that it is actually nil.
  TiUITableViewProxy *ourProxy = (TiUITableViewProxy *)[self proxy];

  NSUInteger oldCount = ourProxy.sectionCount.unsignedIntegerValue;

  for (TiUITableViewSectionProxy *section in [(TiUITableViewProxy *)[self proxy] internalSections]) {
    if ([section parent] == ourProxy) {
      [ourProxy forgetSection:section];
    }
  }

  // Make sure that before we update the section count, the table has been created;
  // this prevents consistency errors on loading the initial dataset when it contains
  // more than one section.
  if (tableview == nil) {
    [self tableView];
  }

  [ourProxy setInternalSections:data];

  int newCount = 0; //Since we're iterating anyways, we might as well not get count.

  for (TiUITableViewSectionProxy *section in [(TiUITableViewProxy *)[self proxy] internalSections]) {
    [section setTable:self];
    [section setSection:newCount++];
    [ourProxy rememberSection:section];
    //TODO: Shouldn't this be done by Section itself? Doesn't it already?
    for (TiUITableViewRowProxy *row in section) {
      row.section = section;
      row.parent = section;
    }
  }
  if (![self isSearchStarted]) {
    [self reloadDataFromCount:oldCount toCount:newCount animation:animation];
  }
}

//Assertions no longer are needed; we ensure that the sections are not nil.
- (void)updateRow:(TiUITableViewRowProxy *)row
{
  row.table = self;
  NSMutableArray *rows = [row.section rows];

  TiUITableViewRowProxy *oldRow = nil;
  if ([rows count] > row.row) {
    oldRow = [rows objectAtIndex:row.row];
    if (oldRow != row) {
      [oldRow retain];
      oldRow.table = nil;
      oldRow.section = nil;
      oldRow.parent = nil;
      [row.section forgetProxy:oldRow];
      [oldRow release];
    }
  }

  if (oldRow != row) {
    [row.section rememberProxy:row];
    [rows replaceObjectAtIndex:row.row withObject:row];
    [row.section reorderRows];
  }
}

- (void)insertRow:(TiUITableViewRowProxy *)row before:(TiUITableViewRowProxy *)before
{
  row.table = self;
  row.section = before.section;
  NSMutableArray *rows = [row.section rows];
  [rows insertObject:row atIndex:row.row];
  [row.section rememberProxy:row];
  [row.section reorderRows];
}

- (void)insertRow:(TiUITableViewRowProxy *)row after:(TiUITableViewRowProxy *)after
{
  row.table = self;
  row.section = after.section;
  NSMutableArray *rows = [row.section rows];
  if (after.row + 1 == [rows count]) {
    [rows addObject:row];
  } else {
    [rows insertObject:row atIndex:after.row + 1];
  }
  [row.section rememberProxy:row];
  [row.section reorderRows];
}

- (void)deleteRow:(TiUITableViewRowProxy *)row
{
  [[row retain] autorelease];
  NSMutableArray *rows = [row.section rows];
#ifdef DEBUG
  ENSURE_VALUE_CONSISTENCY([rows containsObject:row], YES);
#endif
  [rows removeObject:row];
  [row.section forgetProxy:row];
  [row.section reorderRows];
}

- (void)appendRow:(TiUITableViewRowProxy *)row
{
  row.table = self;
  TiUITableViewSectionProxy *section = row.section;
  [section add:row];
  [row.section rememberProxy:row];
  [row.section reorderRows];
}

//Because UITableView does not like having 0 sections, we MUST maintain the facade of having at least one section,
//albeit with 0 rows. Because of this, we might come across several times where this fictional first section will
//be asked about. Because we don't want the sections array throwing range exceptions, sectionForIndex MUST be used
//for this protection.
- (TiUITableViewSectionProxy *)sectionForIndex:(NSInteger)index
{
  NSArray *sections = [(TiUITableViewProxy *)[self proxy] internalSections];
  if (index >= [sections count]) {
    return nil;
  }
  return [sections objectAtIndex:index];
}

- (void)refreshSearchControllerUsingReload:(BOOL)reloadSearch
{
  if ([searchController isActive]) {
    [self updateSearchResultIndexes];

    // Because -[UITableView reloadData] queues on the main runloop, we need to sync the search
    // table reload to the same method. The only time we reloadData, though, is when setting the
    // data, so toggle a flag to indicate what the search should do.
    if (reloadSearch) {
      [tableview reloadData];
    } else {
      [[self tableView] reloadSections:[NSIndexSet indexSetWithIndex:0]
                      withRowAnimation:UITableViewRowAnimationFade];
    }
  } else if (searchHidden) {
    [self hideSearchScreen:nil];
  }
}

- (void)dispatchAction:(TiUITableViewAction *)action
{
  ENSURE_UI_THREAD(dispatchAction, action);

  NSMutableArray *sections = [(TiUITableViewProxy *)[self proxy] internalSections];
  BOOL reloadSearch = NO;

  TiViewProxy<TiKeyboardFocusableView> *chosenField = [[[TiApp controller] keyboardFocusedProxy] retain];
  BOOL hasFocus = [chosenField focused:nil];
  BOOL oldSuppress = [chosenField suppressFocusEvents];
  [chosenField setSuppressFocusEvents:YES];
  switch (action.type) {
  case TiUITableViewActionRowReload: {
    TiUITableViewRowProxy *row = (TiUITableViewRowProxy *)action.obj;
    if (![self isSearchStarted]) {
      NSIndexPath *path = [NSIndexPath indexPathForRow:row.row inSection:row.section.section];
      [tableview reloadRowsAtIndexPaths:[NSArray arrayWithObject:path] withRowAnimation:action.animation];
    }
    break;
  }
  case TiUITableViewActionUpdateRow: {
    TiUITableViewRowProxy *row = (TiUITableViewRowProxy *)action.obj;
    [self updateRow:row];
    if (![self isSearchStarted]) {
      NSIndexPath *path = [NSIndexPath indexPathForRow:row.row inSection:row.section.section];
      [tableview reloadRowsAtIndexPaths:[NSArray arrayWithObject:path] withRowAnimation:action.animation];
    }
    break;
  }
  case TiUITableViewActionSectionReload: {
    if (![self isSearchStarted]) {
      TiUITableViewSectionProxy *section = action.obj;
      NSIndexSet *path = [NSIndexSet indexSetWithIndex:section.section];
      [tableview reloadSections:path withRowAnimation:action.animation];
    }
    break;
  }
  case TiUITableViewActionInsertRowBefore: {
    TiUITableViewRowProxy *row = (TiUITableViewRowProxy *)action.obj;
    NSInteger index = row.row;
    TiUITableViewRowProxy *oldrow = [[row.section rows] objectAtIndex:index];
    [self insertRow:row before:oldrow];
    NSIndexPath *path = [NSIndexPath indexPathForRow:row.row inSection:row.section.section];

    if (action.animation == UITableViewRowAnimationNone) {
      [UIView setAnimationsEnabled:NO];
    }
    if (![self isSearchStarted]) {
      [tableview insertRowsAtIndexPaths:[NSArray arrayWithObject:path] withRowAnimation:action.animation];
    }

    if (action.animation == UITableViewRowAnimationNone) {
      [UIView setAnimationsEnabled:YES];
    }

    break;
  }
  case TiUITableViewActionInsertSectionBefore: {
    TiUITableViewRowProxy *row = (TiUITableViewRowProxy *)action.obj;
    NSInteger newSectionIndex = row.section.section;
    NSInteger rowIndex = row.row;
    row.row = 0;
    TiUITableViewSectionProxy *newSection = row.section;

    NSUInteger updateSectionIndex = (rowIndex == 0) ? newSectionIndex : newSectionIndex - 1;
    TiUITableViewSectionProxy *updateSection = [self sectionForIndex:updateSectionIndex];
    ;

    NSMutableArray *addRows = [NSMutableArray array];

    // If we're inserting before the first row, we can (and should!) skip all this stuff.
    if (rowIndex != 0) {
      NSMutableArray *removeRows = [NSMutableArray array];
      NSUInteger numrows = [[updateSection rows] count];
      for (NSInteger i = rowIndex; i < numrows; i++) {
        // Because rows are being bumped off, we need to keep grabbing the one in the initial index
        TiUITableViewRowProxy *moveRow = [[[updateSection rows] objectAtIndex:rowIndex] retain];

        [removeRows addObject:[NSIndexPath indexPathForRow:i inSection:updateSectionIndex]];
        /*We need to save the row proxy before deleting it, as the KrollObject might get finalized 
                     before appendRow can happen and thus leaving the proxy with no KrollObject associated with it.*/
        [(TiUITableViewProxy *)[self proxy] rememberProxy:moveRow];
        [self deleteRow:moveRow];

        moveRow.section = newSection;
        moveRow.row = (i - rowIndex) + 1;
        moveRow.parent = newSection;

        [addRows addObject:moveRow];
        [moveRow release];
      }
      if (![self isSearchStarted]) {
        [tableview deleteRowsAtIndexPaths:removeRows withRowAnimation:UITableViewRowAnimationNone];
      }
    }

    [sections insertObject:newSection atIndex:newSectionIndex];
    [self appendRow:row];
    for (TiUITableViewRowProxy *moveRow in addRows) {
      [self appendRow:moveRow];
      //Removing the temporarly saved proxy.
      [(TiUITableViewProxy *)[self proxy] forgetProxy:moveRow];
    }
    if (![self isSearchStarted]) {
      [tableview insertSections:[NSIndexSet indexSetWithIndex:newSectionIndex] withRowAnimation:action.animation];
    }

    break;
  }
  case TiUITableViewActionInsertRowAfter: {
    TiUITableViewRowProxy *row = (TiUITableViewRowProxy *)action.obj;
    NSInteger index = row.row - 1;
    TiUITableViewRowProxy *oldrow = nil;
    if (index < [[row.section rows] count]) {
      oldrow = [[row.section rows] objectAtIndex:index];
    }
    [self insertRow:row after:oldrow];
    NSIndexPath *path = [NSIndexPath indexPathForRow:row.row inSection:row.section.section];

    if (action.animation == UITableViewRowAnimationNone) {
      [UIView setAnimationsEnabled:NO];
    }
    if (![self isSearchStarted]) {
      [tableview insertRowsAtIndexPaths:[NSArray arrayWithObject:path] withRowAnimation:action.animation];
    }

    if (action.animation == UITableViewRowAnimationNone) {
      [UIView setAnimationsEnabled:YES];
    }

    break;
  }
  case TiUITableViewActionInsertSectionAfter: {
    TiUITableViewRowProxy *row = (TiUITableViewRowProxy *)action.obj;
    NSInteger newSectionIndex = row.section.section;
    NSInteger rowIndex = row.row; // Get the index of rows which will come after the new row
    row.row = 0; // Reset the row index to the right place
    TiUITableViewSectionProxy *newSection = row.section;

    // Move ALL of the rows after the row we're inserting after to the new section
    TiUITableViewSectionProxy *previousSection = [sections objectAtIndex:newSectionIndex - 1];
    NSUInteger numRows = [[previousSection rows] count];

    NSMutableArray *removeRows = [NSMutableArray array];
    NSMutableArray *addRows = [NSMutableArray array];
    for (NSInteger i = rowIndex; i < numRows; i++) {
      // Have to hold onto the row while we're moving it
      TiUITableViewRowProxy *moveRow = [[[previousSection rows] objectAtIndex:rowIndex] retain];
      [removeRows addObject:[NSIndexPath indexPathForRow:i inSection:newSectionIndex - 1]];
      [self deleteRow:moveRow];

      moveRow.section = newSection;
      moveRow.row = (i - rowIndex) + 1;
      moveRow.parent = newSection;

      [addRows addObject:moveRow];
      [moveRow release];
    }
    // 1st stage of update: Remove all those nasty old rows.
    if (![self isSearchStarted]) {
      [tableview deleteRowsAtIndexPaths:removeRows withRowAnimation:UITableViewRowAnimationNone];
    }

    // 2nd stage of update: Add in those shiny new rows and update the section.
    [sections insertObject:newSection atIndex:newSectionIndex];
    [self appendRow:row];
    for (TiUITableViewRowProxy *moveRow in addRows) {
      [self appendRow:moveRow];
    }
    if (![self isSearchStarted]) {
      [tableview insertSections:[NSIndexSet indexSetWithIndex:newSectionIndex] withRowAnimation:action.animation];
    }
    break;
  }
  case TiUITableViewActionDeleteRow: {
    TiUITableViewRowProxy *row = (TiUITableViewRowProxy *)action.obj;
    [self deleteRow:row];
    if (![self isSearchStarted]) {
      NSIndexPath *path = [NSIndexPath indexPathForRow:row.row inSection:row.section.section];
      [tableview deleteRowsAtIndexPaths:[NSArray arrayWithObject:path] withRowAnimation:action.animation];
    }
    break;
  }
  case TiUITableViewActionSetData: {
    [self replaceData:action.obj animation:action.animation];
    reloadSearch = YES;
    break;
  }
  case TiUITableViewActionAppendRow: {
    TiUITableViewRowProxy *row = (TiUITableViewRowProxy *)action.obj;
    [self appendRow:action.obj];
    if (![self isSearchStarted]) {
      NSIndexPath *path = [NSIndexPath indexPathForRow:row.row inSection:row.section.section];
      [tableview insertRowsAtIndexPaths:[NSArray arrayWithObject:path] withRowAnimation:action.animation];
    }
    break;
  }
  case TiUITableViewActionAppendRowWithSection: {
    TiUITableViewRowProxy *row = (TiUITableViewRowProxy *)action.obj;
    [sections addObject:row.section];
    [self appendRow:action.obj];
    if (![self isSearchStarted]) {
      [tableview insertSections:[NSIndexSet indexSetWithIndex:[sections count] - 1] withRowAnimation:action.animation];
    }
    break;
  }
  }
  if (hasFocus) {
    [chosenField focus:nil];
  }
  [chosenField setSuppressFocusEvents:oldSuppress];
  [chosenField release];
  [self refreshSearchControllerUsingReload:reloadSearch];
}

- (UIView *)titleViewForText:(NSString *)text footer:(BOOL)footer
{
  CGSize maxSize = CGSizeMake(320, 1000);
  UIFont *font = [[WebFont defaultBoldFont] font];
  UILabel *headerLabel = [[[UILabel alloc] initWithFrame:CGRectZero] autorelease];
  headerLabel.text = text;
  headerLabel.textColor = [UIColor blackColor];
  headerLabel.shadowColor = [UIColor whiteColor];
  headerLabel.shadowOffset = CGSizeMake(0, 1);
  headerLabel.font = font;
  headerLabel.backgroundColor = [UIColor clearColor];
  headerLabel.numberOfLines = 0;

  NSAttributedString *theString = [headerLabel attributedText];
  CGSize returnVal = [theString boundingRectWithSize:maxSize
                                             options:NSStringDrawingUsesLineFragmentOrigin
                                             context:nil]
                         .size;

  CGSize size = CGSizeMake(ceilf(returnVal.width), ceilf(returnVal.height));

  UITableViewStyle style = [[self tableView] style];
  int x = (style == UITableViewStyleGrouped) ? 15 : 10;
  int y = 10;
  int y2 = (footer) ? 0 : 10;
  UIView *containerView = [[[UIView alloc] initWithFrame:CGRectMake(0, y, size.width, size.height + 10)] autorelease];
  [headerLabel setFrame:CGRectMake(x, y2, size.width, size.height)];
  [containerView addSubview:headerLabel];

  return containerView;
}

- (TiUITableViewRowProxy *)rowForIndexPath:(NSIndexPath *)indexPath
{
  TiUITableViewSectionProxy *section = [self sectionForIndex:indexPath.section];
  if (!indexPath || section.rowCount.unsignedIntegerValue <= indexPath.row) {
    return nil;
  }
  return [section rowAtIndex:indexPath.row];
}

- (void)changeEditing:(BOOL)yn
{
  editing = yn;
  [self.proxy replaceValue:NUMBOOL(yn) forKey:@"editing" notification:NO];
}

- (void)changeMoving:(BOOL)yn
{
  moving = yn;
  [self.proxy replaceValue:NUMBOOL(yn) forKey:@"moving" notification:NO];
}

- (NSInteger)indexForIndexPath:(NSIndexPath *)path
{
  return [(TiUITableViewProxy *)[self proxy] indexForIndexPath:path];
}

- (void)triggerActionForIndexPath:(NSIndexPath *)indexPath
                         fromPath:(NSIndexPath *)fromPath
                        tableView:(UITableView *)ourTableView
                     wasAccessory:(BOOL)accessoryTapped
                           search:(BOOL)viaSearch
                             name:(NSString *)name
{
  NSIndexPath *index = indexPath;
  if (viaSearch && searchResultIndexes) {
    index = [self indexPathFromSearchIndex:[indexPath row]];
  }
  NSInteger sectionIdx = [index section];
  NSArray *sections = [(TiUITableViewProxy *)[self proxy] internalSections];
  TiUITableViewSectionProxy *section = [self sectionForIndex:sectionIdx];

  NSInteger dataIndex = [self rowIndexForIndexPath:index andSections:sections];
  TiUITableViewRowProxy *row = [section rowAtIndex:[index row]];

  NSMutableDictionary *eventObject = [NSMutableDictionary dictionaryWithObjectsAndKeys:
                                                              section, @"section",
                                                          NUMINTEGER(dataIndex), @"index",
                                                          row, @"row",
                                                          NUMBOOL(accessoryTapped), @"detail",
                                                          NUMBOOL(viaSearch), @"searchMode",
                                                          row, @"rowData",
                                                          nil];

  if (fromPath != nil) {
    NSNumber *fromIndex = NUMINTEGER([self indexForIndexPath:fromPath]);
    [eventObject setObject:fromIndex forKey:@"fromIndex"];
    [eventObject setObject:NUMINTEGER([fromPath row]) forKey:@"fromRow"];
    [eventObject setObject:NUMINTEGER([fromPath section]) forKey:@"fromSection"];
  }

  // fire it to our row since the row, section and table are
  // in a hierarchy and it will bubble up from there...

  UITableViewCell *thisCell = [ourTableView cellForRowAtIndexPath:indexPath];

  CGPoint point = [(TiUITableViewCell *)thisCell hitPoint];
  TiProxy *target = [row touchedViewProxyInCell:thisCell atPoint:&point];

  [eventObject setObject:NUMFLOAT(point.x) forKey:@"x"];
  [eventObject setObject:NUMFLOAT(point.y) forKey:@"y"];

  // Hiding the search screen after a search should not be something we do automatically;
  // see the behavior of, say, Contacts. If users want to hide search, they can do so
  // in an event callback.

  if (viaSearch) {
    self.shouldDelayScrolling = NO;
  }
  if ([target _hasListeners:name]) {
    [target fireEvent:name withObject:eventObject];
  }

  if (viaSearch) {
    if (hideOnSearch) {
      [self hideSearchScreen:nil];
    } else {
      /*
             TIMOB-7397. Observed that `searchBarTextDidBeginEditing` delegate 
             method was being called on screen transition which was causing a 
             visual glitch. Checking for isFirstResponder at this point always 
             returns false. Calling blur here so that the UISearchBar resigns 
             as first responder on main thread
            */
      [searchField performSelector:@selector(blur:) withObject:nil];
    }
  }
}

#pragma mark Overloaded view handling
- (UIView *)hitTest:(CGPoint)point withEvent:(UIEvent *)event
{
  UIView *result = [super hitTest:point withEvent:event];
  if (result == self) { //There is no valid reason why the TiUITableView will get an
    //touch event; it should ALWAYS be a child view.
    return nil;
  }
  return result;
}

- (void)touchesBegan:(NSSet *)touches withEvent:(UIEvent *)event
{
  // iOS idiom seems to indicate that you should never be able to interact with a table
  // while the 'delete' button is showing for a row, but touchesBegan:withEvent: is still triggered.
  // Turn it into a no-op while we're editing
  if (!editing && !moving) {
    [super touchesBegan:touches withEvent:event];
  }
}

- (void)handleListenerRemovedWithEvent:(NSString *)event
{
  if ([event isEqualToString:@"longpress"]) {
    for (UIGestureRecognizer *gesture in [tableview gestureRecognizers]) {
      if ([[gesture class] isEqual:[UILongPressGestureRecognizer class]]) {
        [tableview removeGestureRecognizer:gesture];
        return;
      }
    }
  }
  [super handleListenerRemovedWithEvent:event];
}

- (void)handleListenerAddedWithEvent:(NSString *)event
{
  ENSURE_UI_THREAD_1_ARG(event);
  if ([event isEqualToString:@"longpress"]) {
    UILongPressGestureRecognizer *longPress = [[UILongPressGestureRecognizer alloc] initWithTarget:self action:@selector(longPressGesture:)];
    [[self tableView] addGestureRecognizer:longPress];
    [longPress release];
    return;
  }
  [super handleListenerAddedWithEvent:event];
}

- (void)recognizedSwipe:(UISwipeGestureRecognizer *)recognizer
{
  if ([[self proxy] _hasListeners:@"swipe"]) {

    NSString *swipeString;
    switch ([recognizer direction]) {
    case UISwipeGestureRecognizerDirectionUp:
      swipeString = @"up";
      break;
    case UISwipeGestureRecognizerDirectionDown:
      swipeString = @"down";
      break;
    case UISwipeGestureRecognizerDirectionLeft:
      swipeString = @"left";
      break;
    case UISwipeGestureRecognizerDirectionRight:
      swipeString = @"right";
      break;
    default:
      swipeString = @"unknown";
      break;
    }

    BOOL viaSearch = [searchController isActive];
    UITableView *theTableView = [self tableView];
    CGPoint point = [recognizer locationInView:theTableView];
    CGPoint pointInView = [recognizer locationInView:self];
    NSIndexPath *indexPath = nil;

    if (viaSearch) {
      NSIndexPath *index = [theTableView indexPathForRowAtPoint:point];
      if (index != nil) {
        indexPath = [self indexPathFromSearchIndex:[index row]];
      }
    } else {
      indexPath = [theTableView indexPathForRowAtPoint:point];
    }

    NSMutableDictionary *event = [[TiUtils pointToDictionary:pointInView] mutableCopy];
    [event setValue:swipeString forKey:@"direction"];
    [event setObject:NUMBOOL(NO) forKey:@"detail"];
    [event setObject:NUMBOOL(viaSearch) forKey:@"search"];

    if (indexPath != nil) {
      //We have index path. Let us fill out section and row information. Also since the
      NSInteger sectionIdx = [indexPath section];
      NSArray *sections = [(TiUITableViewProxy *)[self proxy] internalSections];
      TiUITableViewSectionProxy *section = [self sectionForIndex:sectionIdx];

      NSInteger dataIndex = [self rowIndexForIndexPath:indexPath andSections:sections];
      TiUITableViewRowProxy *row = [section rowAtIndex:[indexPath row]];

      [event setObject:section forKey:@"section"];
      [event setObject:row forKey:@"row"];
      [event setObject:row forKey:@"rowData"];
      [event setObject:NUMINTEGER(dataIndex) forKey:@"index"];
    }
    [[self proxy] fireEvent:@"swipe" withObject:event];
    RELEASE_TO_NIL(event);
  }
}

- (void)recognizedTap:(UITapGestureRecognizer *)recognizer
{
  BOOL viaSearch = [searchController isActive];
  UITableView *theTableView = [self tableView];
  CGPoint point = [recognizer locationInView:theTableView];
  CGPoint pointInView = [recognizer locationInView:self];
  NSIndexPath *indexPath = nil;

  if (viaSearch) {
    NSIndexPath *index = [theTableView indexPathForRowAtPoint:point];
    if (index != nil) {
      indexPath = [self indexPathFromSearchIndex:[index row]];
    }
  } else {
    indexPath = [theTableView indexPathForRowAtPoint:point];
  }

  NSMutableDictionary *event = [[TiUtils pointToDictionary:pointInView] mutableCopy];
  [event setObject:NUMBOOL(NO) forKey:@"detail"];
  [event setObject:NUMBOOL(viaSearch) forKey:@"search"];

  if (indexPath != nil) {
    //We have index path. Let us fill out section and row information. Also since the
    NSInteger sectionIdx = [indexPath section];
    NSArray *sections = [(TiUITableViewProxy *)[self proxy] internalSections];
    TiUITableViewSectionProxy *section = [self sectionForIndex:sectionIdx];

    NSInteger dataIndex = [self rowIndexForIndexPath:indexPath andSections:sections];
    TiUITableViewRowProxy *row = [section rowAtIndex:[indexPath row]];

    [event setObject:section forKey:@"section"];
    [event setObject:row forKey:@"row"];
    [event setObject:row forKey:@"rowData"];
    [event setObject:NUMINTEGER(dataIndex) forKey:@"index"];
  }

  if ([recognizer numberOfTouchesRequired] == 2) {
    if ([[self proxy] _hasListeners:@"twofingertap"]) {
      [[self proxy] fireEvent:@"twofingertap" withObject:event];
    }
  } else if ([recognizer numberOfTapsRequired] == 2) {
    //Because double-tap suppresses touchStart and double-click, we must do this:
    if ([[self proxy] _hasListeners:@"touchstart"]) {
      [[self proxy] fireEvent:@"touchstart" withObject:event propagate:YES];
    }
    if ([[self proxy] _hasListeners:@"dblclick"]) {
      [[self proxy] fireEvent:@"dblclick" withObject:event propagate:YES];
    }
    if ([[self proxy] _hasListeners:@"doubletap"]) {
      [[self proxy] fireEvent:@"doubletap" withObject:event];
    }
  } else {
    if ([[self proxy] _hasListeners:@"singletap"]) {
      [[self proxy] fireEvent:@"singletap" withObject:event];
    }
  }
  RELEASE_TO_NIL(event);
}

- (void)longPressGesture:(UILongPressGestureRecognizer *)recognizer
{
  if ([[self proxy] _hasListeners:@"longpress"] && [recognizer state] == UIGestureRecognizerStateBegan) {
    UITableView *ourTableView = [self tableView];
    CGPoint point = [recognizer locationInView:ourTableView];
    NSIndexPath *indexPath = [ourTableView indexPathForRowAtPoint:point];

    BOOL search = [searchController isActive];

    if (indexPath == nil) {
      //indexPath will also be nil if you click the header of the first section. TableView Bug??
      TiUITableViewSectionProxy *section = [self sectionForIndex:0];
      if (section != nil) {
        CGRect headerRect = [ourTableView rectForHeaderInSection:0];
        if (CGRectContainsPoint(headerRect, point)) {
          NSDictionary *eventObject = [NSDictionary dictionaryWithObjectsAndKeys:
                                                        section, @"section",
                                                    NUMBOOL(NO), @"detail",
                                                    NUMBOOL(search), @"searchMode",
                                                    NUMFLOAT(point.x), @"x",
                                                    NUMFLOAT(point.y), @"y",
                                                    nil];
          [[self proxy] fireEvent:@"longpress" withObject:eventObject];
          return;
        }
      }
      NSDictionary *event = [NSDictionary dictionaryWithObjectsAndKeys:
                                              NUMBOOL(NO), @"detail",
                                          NUMBOOL(search), @"searchMode",
                                          NUMFLOAT(point.x), @"x",
                                          NUMFLOAT(point.y), @"y",
                                          nil];
      [[self proxy] fireEvent:@"longpress" withObject:event];
      return;
    }

    if (!search) {
      //Make sure that the point does not fall into the rect for header or footer views
      CGRect headerRect = [ourTableView rectForHeaderInSection:[indexPath section]];
      CGRect footerRect = [ourTableView rectForFooterInSection:[indexPath section]];
      if (CGRectContainsPoint(headerRect, point) || CGRectContainsPoint(footerRect, point)) {
        TiUITableViewSectionProxy *section = [self sectionForIndex:[indexPath section]];
        NSDictionary *eventObject = [NSDictionary dictionaryWithObjectsAndKeys:
                                                      section, @"section",
                                                  NUMBOOL(NO), @"detail",
                                                  NUMBOOL(search), @"searchMode",
                                                  NUMFLOAT(point.x), @"x",
                                                  NUMFLOAT(point.y), @"y",
                                                  nil];
        [[self proxy] fireEvent:@"longpress" withObject:eventObject];
        return;
      }
    }
    if (!allowsSelectionSet || ![ourTableView allowsSelection]) {
      [ourTableView deselectRowAtIndexPath:indexPath animated:YES];
    }
    [self triggerActionForIndexPath:indexPath fromPath:nil tableView:ourTableView wasAccessory:NO search:search name:@"longpress"];
  }
}

#pragma mark Searchbar-related accessors

- (UIButton *)searchScreenView
{
  if (searchScreenView == nil) {
    searchScreenView = [[UIButton alloc] init];
    [searchScreenView addTarget:self action:@selector(hideSearchScreen:) forControlEvents:UIControlEventTouchUpInside];
    [searchScreenView setShowsTouchWhenHighlighted:NO];
    [searchScreenView setAdjustsImageWhenDisabled:NO];
    [searchScreenView setOpaque:NO];
    [searchScreenView setBackgroundColor:[UIColor blackColor]];
  }
  return searchScreenView;
}

#pragma mark Searchbar helper methods

- (NSIndexPath *)indexPathFromSearchIndex:(NSInteger)index
{
  int asectionIndex = 0;
  for (NSIndexSet *thisSet in searchResultIndexes) {
    NSUInteger thisSetCount = [thisSet count];
    if (index < thisSetCount) {
      NSUInteger rowIndex = [thisSet firstIndex];
      while (index > 0) {
        rowIndex = [thisSet indexGreaterThanIndex:rowIndex];
        index--;
      }
      return [NSIndexPath indexPathForRow:rowIndex inSection:asectionIndex];
    }
    asectionIndex++;
    index -= thisSetCount;
  }
  return nil;
}

- (void)updateSearchResultIndexes
{
  if ([searchString length] == 0) {
    RELEASE_TO_NIL(searchResultIndexes);

    //Need to reload the tableview, as some of the cells might be reused as part
    //of a previous search and as a result may not be visible on screen.
    [tableview reloadData];

    return;
  }
  NSEnumerator *searchResultIndexEnumerator;
  if (searchResultIndexes == nil) {
    NSUInteger sectionCount = [(TiUITableViewProxy *)[self proxy] sectionCount].unsignedIntegerValue;
    searchResultIndexes = [[NSMutableArray alloc] initWithCapacity:sectionCount];
    searchResultIndexEnumerator = nil;
  } else {
    searchResultIndexEnumerator = [searchResultIndexes objectEnumerator];
  }

  //TODO: If the search is adding letters to the previous search string, do it by elimination instead of adding.

  NSString *ourSearchAttribute = filterAttribute;
  if (ourSearchAttribute == nil) {
    ourSearchAttribute = @"title";
  }

  NSStringCompareOptions searchOpts = (filterCaseInsensitive ? NSCaseInsensitiveSearch : 0) | (filterAnchored ? NSAnchoredSearch : 0);

  for (TiUITableViewSectionProxy *thisSection in [(TiUITableViewProxy *)[self proxy] internalSections]) {
    NSMutableIndexSet *thisIndexSet = [searchResultIndexEnumerator nextObject];
    if (thisIndexSet == nil) {
      searchResultIndexEnumerator = nil; //Make sure we don't use the enumerator anymore.
      thisIndexSet = [NSMutableIndexSet indexSet];
      [searchResultIndexes addObject:thisIndexSet];
    } else {
      [thisIndexSet removeAllIndexes];
    }
    int cellIndex = 0;
    for (TiUITableViewRowProxy *row in [thisSection rows]) {
      id value = [row valueForKey:ourSearchAttribute];
      if (value != nil && [[TiUtils stringValue:value] rangeOfString:searchString options:searchOpts].location != NSNotFound) {
        [thisIndexSet addIndex:cellIndex];
      }
      cellIndex++;
    }
  }
}

- (void)frameSizeChanged:(CGRect)frame bounds:(CGRect)bounds
{
  if (searchHidden) {
    if (searchField != nil) {
      animateHide = YES;
      [self hideSearchScreen:nil];
    }
  } else {
    if (tableview != nil && searchField != nil) {
      [tableview setContentOffset:CGPointMake(0, 0)];
    }
  }

  if (!searchActivated) {
    [searchField ensureSearchBarHierarchy];
  }

  [super frameSizeChanged:frame bounds:bounds];

  if (tableHeaderPullView != nil) {
    tableHeaderPullView.frame = CGRectMake(0.0f, 0.0f - self.tableView.bounds.size.height, self.tableView.bounds.size.width, self.tableView.bounds.size.height);
    TiViewProxy *proxy = [self.proxy valueForUndefinedKey:@"headerPullView"];
    [TiUtils setView:[proxy view] positionRect:[tableHeaderPullView bounds]];
    [proxy windowWillOpen];
    [proxy layoutChildren:NO];
  }

  if ([[self tableView] tableHeaderView] != nil) {
    TiViewProxy *proxy = [self.proxy valueForUndefinedKey:@"headerView"];
    if (proxy != nil) {
      [proxy windowWillOpen];
      proxy.parentVisible = YES;
      [proxy refreshSize];
      [proxy willChangeSize];
      [proxy layoutChildren:NO];
    }
  }

  if ([tableview tableFooterView] != nil) {
    TiViewProxy *proxy = [self.proxy valueForUndefinedKey:@"footerView"];
    if (proxy != nil) {
      [proxy windowWillOpen];
      proxy.parentVisible = YES;
      [proxy refreshSize];
      [proxy willChangeSize];
      [proxy layoutChildren:NO];
    }
  }

  if ([searchController isActive]) {
    [self updateSearchControllerFrames];
  }
}

- (CGFloat)contentHeightForWidth:(CGFloat)suggestedWidth
{
  CGFloat height = 0.0;
  NSUInteger sectionCount = [self numberOfSectionsInTableView:tableview];
  for (NSUInteger section = 0; section < sectionCount; section++) {
    height += [self tableView:tableview heightForHeaderInSection:section];
    height += [self tableView:tableview heightForFooterInSection:section];

    NSUInteger rowCount = [self tableView:tableview numberOfRowsInSection:section];
    for (NSUInteger row = 0; row < rowCount; row++) {
      height += [self tableView:tableview heightForRowAtIndexPath:[NSIndexPath indexPathForRow:row inSection:section]];
    }
  }

  return height;
}

- (void)updateSearchControllerFrames
{
  if (![searchController isActive]) {
    return;
  }
  if (isSearchBarInNavigation) {
    dimmingView.frame = CGRectMake(0, 0, self.frame.size.width, self.frame.size.height);
  } else {
    [dimmingView setFrame:CGRectMake(0, searchController.searchBar.frame.size.height, self.frame.size.width, self.frame.size.height - searchController.searchBar.frame.size.height)];
    CGPoint convertedOrigin = [self.superview convertPoint:self.frame.origin toView:searchControllerPresenter.view];

    UIView *searchSuperView = [searchController.view superview];
    searchSuperView.frame = CGRectMake(convertedOrigin.x, convertedOrigin.y, self.frame.size.width, self.frame.size.height);

    CGFloat width = [searchField view].frame.size.width;
    UIView *view = searchController.searchBar.superview;
    view.frame = CGRectMake(0, 0, width, view.frame.size.height);
    searchController.searchBar.frame = CGRectMake(0, 0, width, searchController.searchBar.frame.size.height);
    [searchField ensureSearchBarHierarchy];
  }
}

#pragma mark Searchbar-related IBActions

- (void)hideSearchScreen:(id)sender
{
  if (viewWillDetach) {
    return;
  }

  // check to make sure we're not in the middle of a layout, in which case we
  // want to try later or we'll get weird drawing animation issues
  if (tableview.frame.size.width == 0) {
    [self performSelector:@selector(hideSearchScreen:) withObject:sender afterDelay:0.1];
    return;
  }

  if ([[searchField view] isFirstResponder]) {
    [[searchField view] resignFirstResponder];
    [self makeRootViewFirstResponder];
  }

  // This logic here is contingent on search controller deactivation
  // (-[TiUITableView searchDisplayControllerDidEndSearch:]) triggering a hide;
  // doing this ensures that:
  //
  // * The hide when the search controller was active is animated
  // * The animation only occurs once

  if ([searchController isActive]) {
    [searchController setActive:NO];
    searchActivated = NO;
    return;
  }

  // NOTE: Because of how tableview row reloads are scheduled, we always need to do this
  // because of where the hide might be triggered from.

  if (viewWillDetach) {
    return;
  }
  searchActivated = NO;
  NSArray *visibleRows = [tableview indexPathsForVisibleRows];
  [tableview reloadRowsAtIndexPaths:visibleRows withRowAnimation:UITableViewRowAnimationNone];

  // We only want to scroll if the following conditions are met:
  // 1. The top row of the first section (and hence searchbar) are visible (or there are no rows)
  // 2. The current offset is smaller than the new offset (otherwise the search is already hidden)

  if (searchHidden) {
    CGPoint offset = CGPointMake(0, MAX(TI_NAVBAR_HEIGHT, searchField.view.frame.size.height));
    if (([visibleRows count] == 0) || ([tableview contentOffset].y < offset.y && [visibleRows containsObject:[NSIndexPath indexPathForRow:0 inSection:0]])) {
      void (^hide)(void) = ^{
        [tableview setContentOffset:offset animated:NO];
      };

      // 0.2s was the default for UIView animation blocks pre-iOS 4, which is what was used here.
      // Note that we have to invoke the animation subsystem directly rather than implicitly to avoid
      // some undesirable redraw or multiple scroll behavior.

      if (animateHide) {
        [UIView animateWithDuration:0.2 animations:hide];
      } else {
        hide();
      }
    }
  }
  // Reset the animation hide flag to its default value
  animateHide = NO;
}

- (void)scrollToTop:(NSInteger)top animated:(BOOL)animated
{
  [tableview setContentOffset:CGPointMake(0, top) animated:animated];
}

- (IBAction)showSearchScreen:(id)sender
{
  [tableview setContentOffset:CGPointZero animated:YES];
}

- (void)updateSearchView
{
  if (tableview == nil) {
    return;
  }

  ENSURE_UI_THREAD_0_ARGS;
  if (searchField == nil) {
    [tableview setTableHeaderView:nil];
    RELEASE_TO_NIL(tableHeaderView);
    RELEASE_TO_NIL(searchScreenView);
    RELEASE_TO_NIL(searchResultIndexes);
    return;
  }

  UIView *searchView = [searchField view];

  if (tableHeaderView == nil) {
    CGFloat wrapperHeight = TI_SEARCHBAR_HEIGHT;
    CGRect wrapperFrame = CGRectMake(0, 0, [tableview bounds].size.width, wrapperHeight);
    tableHeaderView = [[UIView alloc] initWithFrame:wrapperFrame];
    [tableHeaderView setAutoresizingMask:UIViewAutoresizingFlexibleWidth];
    [searchView setAutoresizingMask:UIViewAutoresizingFlexibleWidth];
    [[searchField searchBar] setAutoresizingMask:UIViewAutoresizingFlexibleWidth];
    [TiUtils setView:searchView positionRect:wrapperFrame];
    [tableHeaderView addSubview:searchView];
    if (!isSearchBarInNavigation) {
      [tableview setTableHeaderView:tableHeaderView];
    }
    [searchView sizeToFit];
  }
}

#pragma mark Search Bar Delegate

- (void)searchBarTextDidBeginEditing:(UISearchBar *)searchBar
{
  // called when text starts editing
  [self showSearchScreen:nil];
  searchActivated = YES;
  // Dont reload here since user started editing but not yet started typing.
  // Also if a previous search string exists this reload results in blank cells.
}

- (void)searchBarSearchButtonClicked:(UISearchBar *)searchBar
{
  // called when keyboard search button pressed
  [searchBar resignFirstResponder];
  [self makeRootViewFirstResponder];
}

- (void)searchBar:(UISearchBar *)searchBar textDidChange:(NSString *)searchText
{
  self.searchedString = (searchText == nil) ? @"" : searchText;
}

- (void)searchBarCancelButtonClicked:(UISearchBar *)searchBar
{
  // called when cancel button pressed
  isSearched = NO;
  [searchBar setText:nil];
  [self setSearchString:nil];
  [self updateSearchResultIndexes];
  if (searchActivated) {
    searchActivated = NO;
    [tableview reloadData];
  }
}

#pragma mark Section Header / Footer

- (TiUIView *)sectionView:(NSInteger)section forLocation:(NSString *)location section:(TiUITableViewSectionProxy **)sectionResult
{
  TiUITableViewSectionProxy *proxy = [self sectionForIndex:section];
  //In the event that proxy is nil, this all flows out to returning nil safely anyways.
  if (sectionResult != nil) {
    *sectionResult = proxy;
  }
  TiViewProxy *viewproxy = [proxy valueForKey:location];
  if (viewproxy != nil && [viewproxy isKindOfClass:[TiViewProxy class]]) {
    [viewproxy windowWillOpen];
    return [viewproxy view];
  }
  return nil;
}

#pragma mark Public APIs

- (void)scrollToIndex:(NSInteger)index position:(UITableViewScrollPosition)position animated:(BOOL)animated
{
  UITableView *table = [self tableView];
  NSIndexPath *path = [self indexPathFromInt:index];
  [table scrollToRowAtIndexPath:path atScrollPosition:position animated:animated];
}

- (void)setSeparatorInsets_:(id)arg
{
  DEPRECATED_REPLACED(@"UI.TableView.separatorInsets", @"5.2.0", @"UI.TableView.tableSeparatorInsets")
      [self setTableSeparatorInsets_:arg];
}

- (void)setTableSeparatorInsets_:(id)arg
{
  if ([arg isKindOfClass:[NSDictionary class]]) {
    CGFloat left = [TiUtils floatValue:@"left" properties:arg def:defaultSeparatorInsets.left];
    CGFloat right = [TiUtils floatValue:@"right" properties:arg def:defaultSeparatorInsets.right];
    [[self tableView] setSeparatorInset:UIEdgeInsetsMake(0, left, 0, right)];
  } else {
    [[self tableView] setSeparatorInset:defaultSeparatorInsets];
  }
  if (!searchActivated) {
    [[self tableView] setNeedsDisplay];
  }
}

- (void)setRowSeparatorInsets_:(id)arg
{
  if ([arg isKindOfClass:[NSDictionary class]]) {

    CGFloat left = [TiUtils floatValue:@"left" properties:arg def:defaultSeparatorInsets.left];
    CGFloat right = [TiUtils floatValue:@"right" properties:arg def:defaultSeparatorInsets.right];
    rowSeparatorInsets = UIEdgeInsetsMake(0, left, 0, right);
  }
  if (!searchActivated) {
    [[self tableView] setNeedsDisplay];
  }
}

- (void)setBackgroundColor_:(id)arg
{
  [[self proxy] replaceValue:arg forKey:@"backgroundColor" notification:NO];
  if (tableview != nil) {
    [self setBackgroundColor:[TiUtils colorValue:arg] onTable:[self tableView]];
  }
}

- (void)setBackgroundImage_:(id)arg
{
  NSURL *url = [TiUtils toURL:arg proxy:(TiProxy *)self.proxy];
  UIImage *image = [[ImageLoader sharedLoader] loadImmediateImage:url];
  [[self tableView] setBackgroundColor:[UIColor colorWithPatternImage:image]];

  self.backgroundImage = arg;
}

- (void)setAllowsSelection_:(id)arg
{
  allowsSelectionSet = [TiUtils boolValue:arg];
  [[self tableView] setAllowsSelection:allowsSelectionSet];
}

- (void)setAllowsSelectionDuringEditing_:(id)arg
{
  [[self tableView] setAllowsSelectionDuringEditing:[TiUtils boolValue:arg def:NO]];
}

- (void)setSeparatorStyle_:(id)arg
{
  [[self tableView] setSeparatorStyle:[TiUtils intValue:arg]];
}

- (void)setSeparatorColor_:(id)arg
{
  TiColor *color = [TiUtils colorValue:arg];
  [[self tableView] setSeparatorColor:[color _color]];
}

- (void)proxyDidRelayout:(id)sender
{
  TiThreadPerformOnMainThread(
      ^{
        if ((sender == headerViewProxy) && (headerViewProxy != nil)) {
          UIView *headerView = [[self tableView] tableHeaderView];
          [headerView setFrame:[headerView bounds]];
          [[self tableView] setTableHeaderView:headerView];
        } else if ((sender == footerViewProxy) && (footerViewProxy != nil)) {
          UIView *footerView = [[self tableView] tableFooterView];
          [footerView setFrame:[footerView bounds]];
          [[self tableView] setTableFooterView:footerView];
        }
      },
      NO);
}

- (void)setHeaderTitle_:(id)args
{
  if (headerViewProxy != nil) {
    [headerViewProxy setProxyObserver:nil];
    [[self proxy] forgetProxy:headerViewProxy];
    headerViewProxy = nil;
  }
  [[self tableView] setTableHeaderView:[self titleViewForText:[TiUtils stringValue:args] footer:NO]];
}

- (void)setFooterTitle_:(id)args
{
  if (footerViewProxy != nil) {
    [footerViewProxy setProxyObserver:nil];
    [[self proxy] forgetProxy:footerViewProxy];
    footerViewProxy = nil;
  }
  [[self tableView] setTableFooterView:[self titleViewForText:[TiUtils stringValue:args] footer:YES]];
}

- (void)setHeaderView_:(id)args
{
  ENSURE_SINGLE_ARG_OR_NIL(args, TiViewProxy);
  if (args != nil) {
    TiUIView *view = (TiUIView *)[args view];
    UITableView *table = [self tableView];
    [table setTableHeaderView:view];
    if (headerViewProxy != nil) {
      [headerViewProxy setProxyObserver:nil];
      [[self proxy] forgetProxy:headerViewProxy];
    }
    headerViewProxy = args;
    [headerViewProxy setProxyObserver:self];
    [[self proxy] rememberProxy:headerViewProxy];
  } else {
    if (headerViewProxy != nil) {
      [headerViewProxy setProxyObserver:nil];
      [[self proxy] forgetProxy:headerViewProxy];
      headerViewProxy = nil;
    }
    [[self tableView] setTableHeaderView:nil];
  }
}

- (void)setFooterView_:(id)args
{
  ENSURE_SINGLE_ARG_OR_NIL(args, TiViewProxy);
  if (args != nil) {
    [args windowWillOpen];
    UIView *view = [args view];
    [[self tableView] setTableFooterView:view];
    if (footerViewProxy != nil) {
      [footerViewProxy setProxyObserver:nil];
      [[self proxy] forgetProxy:footerViewProxy];
    }
    footerViewProxy = args;
    [footerViewProxy setProxyObserver:self];
    [[self proxy] rememberProxy:footerViewProxy];
  } else {
    if (footerViewProxy != nil) {
      [footerViewProxy setProxyObserver:nil];
      [[self proxy] forgetProxy:footerViewProxy];
      footerViewProxy = nil;
    }
    [[self tableView] setTableFooterView:nil];
  }
}

- (void)setSearch_:(id)search
{
  ENSURE_TYPE_OR_NIL(search, TiUISearchBarProxy);
  if (searchField != nil) {
    [searchField setDelegate:nil];
  }
  RELEASE_TO_NIL(searchField);
  RELEASE_TO_NIL(searchController);

  isSearchBarInNavigation = [TiUtils boolValue:[self.proxy valueForKey:@"showSearchBarInNavBar"] def:NO];

  if (search != nil) {
    //TODO: now that we're using the search controller, we can move away from
    //doing our own custom search screen since the controller gives this to us
    //for free
    searchField = [search retain];
    if (!isSearchBarInNavigation) {
      [searchField windowWillOpen];
    }
    [searchField setDelegate:self];
    [self tableView];
    [self updateSearchView];
    [self initSearhController];

    if (searchHidden) {
      // This seems like inconsistent behavior, as much of our 'search hide' logic works out to

      animateHide = YES;
      [self hideSearchScreen:nil];
      return;
    }
  } else {
    [self updateSearchView];
  }
}

- (void)initSearhController
{
  if (searchController == nil) {
    searchController = [[UISearchController alloc] initWithSearchResultsController:nil];
    searchController.hidesNavigationBarDuringPresentation = NO;
    searchController.obscuresBackgroundDuringPresentation = NO;
    searchController.searchBar.frame = CGRectMake(searchController.searchBar.frame.origin.x, searchController.searchBar.frame.origin.y, 0, 44.0);
    searchController.searchBar.autoresizingMask = UIViewAutoresizingFlexibleWidth;
    searchController.searchBar.placeholder = [[searchField searchBar] placeholder];
    searchController.searchBar.text = [[searchField searchBar] text];
    [searchField setSearchBar:searchController.searchBar];
    searchController.delegate = self;
    searchController.searchResultsUpdater = self;

    [TiUtils configureController:searchController withObject:self.proxy];

    if (_dimsBackgroundDuringPresentation) {
      [self createDimmingView];
    }

    NSNotificationCenter *nc = [NSNotificationCenter defaultCenter];
    [nc addObserver:self selector:@selector(keyboardWillChangeFrame:) name:UIKeyboardWillChangeFrameNotification object:nil];
    [nc addObserver:self selector:@selector(keyboardDidChangeFrame:) name:UIKeyboardDidChangeFrameNotification object:nil];
  }
}

- (void)setDimBackgroundForSearch_:(id)arg
{
  ENSURE_SINGLE_ARG_OR_NIL(arg, NSNumber);

  if (searchController && [TiUtils boolValue:arg def:YES]) {
    [self createDimmingView];
  } else {
    _dimsBackgroundDuringPresentation = [TiUtils boolValue:arg def:YES];
  }
}

- (void)setScrollIndicatorStyle_:(id)value
{
  [[self tableView] setIndicatorStyle:[TiUtils intValue:value def:UIScrollViewIndicatorStyleDefault]];
}

- (void)setShowVerticalScrollIndicator_:(id)value
{
  [[self tableView] setShowsVerticalScrollIndicator:[TiUtils boolValue:value]];
}

- (void)setSearchHidden_:(id)hide
{
  if ([TiUtils boolValue:hide]) {
    searchHidden = YES;
    if (searchField) {
      animateHide = YES;
      [self hideSearchScreen:nil];
    }
  } else {
    searchHidden = NO;
    if (searchField) {
      [self showSearchScreen:nil];
    }
  }
}

- (void)setHideSearchOnSelection_:(id)yn
{
  hideOnSearch = [TiUtils boolValue:yn def:YES];
}

- (void)setFilterAttribute_:(id)newFilterAttribute
{
  ENSURE_STRING_OR_NIL(newFilterAttribute);
  if (newFilterAttribute == filterAttribute) {
    return;
  }
  RELEASE_TO_NIL(filterAttribute);
  filterAttribute = [newFilterAttribute copy];
}

- (void)setIndex_:(NSArray *)index_
{
  RELEASE_TO_NIL(sectionIndex);
  RELEASE_TO_NIL(sectionIndexMap);

  sectionIndex = [[NSMutableArray alloc] initWithCapacity:[index_ count]];
  sectionIndexMap = [[NSMutableDictionary alloc] init];

  for (NSDictionary *entry in index_) {
    ENSURE_DICT(entry);

    NSString *title = [entry objectForKey:@"title"];
    id theindex = [entry objectForKey:@"index"];
    [sectionIndex addObject:title];
    [sectionIndexMap setObject:[NSNumber numberWithInt:[TiUtils intValue:theindex]] forKey:title];
  }

  // Instead of calling back through our mechanism to reload specific sections, because the entire index of the table
  // has been regenerated, we can assume it's okay to just reload the whole dataset.
  TiThreadPerformOnMainThread(
      ^{
        [[self tableView] reloadData];
      },
      NO);
}

- (void)setFilterAnchored_:(id)anchoredBool
{
  filterAnchored = [TiUtils boolValue:anchoredBool];
}

- (void)setFilterCaseInsensitive_:(id)caseBool
{
  filterCaseInsensitive = [TiUtils boolValue:caseBool];
}

- (void)setEditable_:(id)args
{
  editable = [TiUtils boolValue:args];
}

- (void)setMoveable_:(id)args
{
  moveable = [TiUtils boolValue:args];
}

- (void)setScrollable_:(id)args
{
  UITableView *table = [self tableView];
  [table setScrollEnabled:[TiUtils boolValue:args]];
}

- (void)setEditing_:(id)args withObject:(id)properties
{
  [self changeEditing:[TiUtils boolValue:args]];
  UITableView *table = [self tableView];
  BOOL animated = [TiUtils boolValue:@"animated" properties:properties def:YES];
  [table beginUpdates];
  [table setEditing:moving || editing animated:animated];
  [table endUpdates];
}

- (void)setMoving_:(id)args withObject:(id)properties
{
  [self changeMoving:[TiUtils boolValue:args]];
  UITableView *table = [self tableView];
  BOOL animated = [TiUtils boolValue:@"animated" properties:properties def:YES];
  [table beginUpdates];
  [table setEditing:moving || editing animated:animated];
  [table endUpdates];
}

- (void)setRowHeight_:(id)height
{
  rowHeight = [TiUtils dimensionValue:height];
  if (TiDimensionIsDip(rowHeight)) {
    [tableview setRowHeight:rowHeight.value];
  }
}

- (void)setMinRowHeight_:(id)height
{
  minRowHeight = [TiUtils dimensionValue:height];
}

- (void)setMaxRowHeight_:(id)height
{
  maxRowHeight = [TiUtils dimensionValue:height];
}

- (void)setRefreshControl_:(id)args
{
#ifdef USE_TI_UIREFRESHCONTROL
  ENSURE_SINGLE_ARG_OR_NIL(args, TiUIRefreshControlProxy);
  [[_refreshControlProxy control] removeFromSuperview];
  RELEASE_TO_NIL(_refreshControlProxy);
  [[self proxy] replaceValue:args forKey:@"refreshControl" notification:NO];
  if (args != nil) {
    _refreshControlProxy = [args retain];
    [[self tableView] setRefreshControl:_refreshControlProxy.control];
  }
#endif
}

- (void)setHeaderPullView_:(id)value
{
  ENSURE_TYPE_OR_NIL(value, TiViewProxy);
  if (value == nil) {
    [tableHeaderPullView removeFromSuperview];
    RELEASE_TO_NIL(tableHeaderPullView);
  } else {
    if (self.tableView.frame.size.width == 0) {
      [self performSelector:@selector(setHeaderPullView_:) withObject:value afterDelay:0.1];
      return;
    }
    tableHeaderPullView = [[UIView alloc] initWithFrame:CGRectMake(0.0f, 0.0f - self.tableView.bounds.size.height, self.tableView.bounds.size.width, self.tableView.bounds.size.height)];
    TiColor *pullBgColor = [TiUtils colorValue:[value valueForUndefinedKey:@"pullBackgroundColor"]];
    tableHeaderPullView.backgroundColor = ((pullBgColor == nil) ? [UIColor lightGrayColor] : [pullBgColor color]);
    UIView *view = [value view];
    [[self tableView] addSubview:tableHeaderPullView];
    [tableHeaderPullView addSubview:view];
    [TiUtils setView:view positionRect:[tableHeaderPullView bounds]];
    [value windowWillOpen];
    [value layoutChildren:NO];
  }
}

- (void)setContentOffset_:(id)args withObject:(id)obj
{
  CGPoint offset = [TiUtils pointValue:args];
  BOOL animated = [TiUtils boolValue:[obj objectForKey:@"animated"] def:NO];
  [tableview setContentOffset:offset animated:animated];
}

- (void)setContentInsets_:(id)value withObject:(id)props
{
  UIEdgeInsets insets = [TiUtils contentInsets:value];
  BOOL animated = [TiUtils boolValue:@"animated" properties:props def:NO];
  void (^setInset)(void) = ^{
    [self searchString];
    [tableview setContentInset:insets];
  };
  if (animated) {
    double duration = [TiUtils doubleValue:@"duration" properties:props def:300] / 1000;
    [UIView animateWithDuration:duration animations:setInset];
  } else {
    setInset();
  }
}

- (void)setAllowsMultipleSelectionDuringEditing_:(id)value
{
  ENSURE_TYPE(value, NSNumber);
  [[self proxy] replaceValue:value forKey:@"allowsMultipleSelectionDuringEditing" notification:NO];

  [[self tableView] setAllowsMultipleSelectionDuringEditing:[TiUtils boolValue:value]];
}

#pragma mark Datasource

#define RETURN_IF_SEARCH_TABLE_VIEW(result) \
  if ([searchController isActive]) {        \
    return result;                          \
  }

#define RETURN_IF_SEARCH_IS_ACTIVE(result) \
  if (searchActivated) {                   \
    return result;                         \
  }

- (NSInteger)tableView:(UITableView *)table numberOfRowsInSection:(NSInteger)section
{
  if ([self isSearchStarted]) {
    int rowCount = 0;
    for (NSIndexSet *thisSet in searchResultIndexes) {
      rowCount += [thisSet count];
    }
    return rowCount;
  }

  TiUITableViewSectionProxy *sectionProxy = [self sectionForIndex:section];
  return sectionProxy.rowCount.integerValue;
}

// Row display. Implementers should *always* try to reuse cells by setting each cell's reuseIdentifier and querying for available reusable cells with dequeueReusableCellWithIdentifier:
// Cell gets various attributes set automatically based on table (separators) and data source (accessory views, editing controls)

- (UITableViewCell *)tableView:(UITableView *)ourTableView cellForRowAtIndexPath:(NSIndexPath *)indexPath
{
  NSIndexPath *index = indexPath;
  if ([self isSearchStarted]) {
    index = [self indexPathFromSearchIndex:[indexPath row]];
  }

  TiUITableViewRowProxy *row = [self rowForIndexPath:index];
  [row triggerAttach];

  // the classname for all rows that have the same substainal layout will be the same
  // we reuse them for speed
  UITableViewCell *cell = [ourTableView dequeueReusableCellWithIdentifier:row.tableClass];

  if (cell == nil) {
    if (row.callbackCell != nil) {
      //Ensure that the proxy is associated with one cell only
      [row.callbackCell setProxy:nil];
    }
    cell = [[[TiUITableViewCell alloc] initWithStyle:UITableViewCellStyleDefault reuseIdentifier:row.tableClass row:row] autorelease];
  } else {
    //Ensure that the row is detached if reusing cells did not do so.
    [row prepareTableRowForReuse];
    // Have to reset the proxy on the cell, and the row's callback cell, as it may have been cleared in reuse operations (or reassigned)
    [(TiUITableViewCell *)cell setProxy:row];
    [row setCallbackCell:(TiUITableViewCell *)cell];
  }
  [row initializeTableViewCell:cell];

  if ([searchController isActive]) {
    [cell setLayoutMargins:UIEdgeInsetsZero];
  }

  if (rowSeparatorInsets.left != 0 || rowSeparatorInsets.right != 0) {
    [cell setSeparatorInset:rowSeparatorInsets];
  }

  return cell;
}

- (NSInteger)numberOfSectionsInTableView:(UITableView *)ourTableView
{
  //TIMOB-15526
  if ([searchController isActive] && ourTableView.backgroundColor == [UIColor clearColor]) {
    ourTableView.backgroundColor = [UIColor whiteColor];
  }

  if ([self isSearchStarted]) {
    return 1;
  }
  // One quirk of UITableView is that it really hates having 0 sections. Instead, supply 1 section, no rows.
  NSUInteger result = [(TiUITableViewProxy *)[self proxy] sectionCount].unsignedIntegerValue;
  return MAX(1, result);
}

- (NSString *)tableView:(UITableView *)ourTableView titleForHeaderInSection:(NSInteger)section
{
  RETURN_IF_SEARCH_TABLE_VIEW(nil);
  TiUITableViewSectionProxy *sectionProxy = [self sectionForIndex:section];
  return [sectionProxy headerTitle];
}

- (NSString *)tableView:(UITableView *)ourTableView titleForFooterInSection:(NSInteger)section
{
  RETURN_IF_SEARCH_TABLE_VIEW(nil);
  TiUITableViewSectionProxy *sectionProxy = [self sectionForIndex:section];
  return [sectionProxy footerTitle];
}

// After a row has the minus or plus button invoked (based on the UITableViewCellEditingStyle for the cell), the dataSource must commit the change
- (void)tableView:(UITableView *)ourTableView commitEditingStyle:(UITableViewCellEditingStyle)editingStyle forRowAtIndexPath:(NSIndexPath *)indexPath
{
  RETURN_IF_SEARCH_TABLE_VIEW();
  if (editingStyle == UITableViewCellEditingStyleDelete) {
    TiUITableViewSectionProxy *section = [self sectionForIndex:[indexPath section]];
    NSInteger index = [self indexForIndexPath:indexPath];
    UITableView *table = [self tableView];
    NSIndexPath *path = [self indexPathFromInt:index];

    // note, trigger action before the update since on the last delete it will be gone..
    [self triggerActionForIndexPath:indexPath fromPath:nil tableView:ourTableView wasAccessory:NO search:NO name:@"delete"];

    [[section rows] removeObjectAtIndex:[indexPath row]];

    // If the section is empty, we want to remove it as well.
    BOOL emptySection = ([[section rows] count] == 0);
    if (emptySection) {
      [[(TiUITableViewProxy *)[self proxy] internalSections] removeObjectAtIndex:[indexPath section]];
    }

    [table beginUpdates];
    if (emptySection) {
      NSIndexSet *thisSectionSet = [NSIndexSet indexSetWithIndex:[indexPath section]];
      if ([(TiUITableViewProxy *)[self proxy] sectionCount].unsignedIntegerValue > 0) {
        [table deleteSections:thisSectionSet withRowAnimation:UITableViewRowAnimationFade];
      } else //There always must be at least one section. So instead, we have it reload to clear out the header and footer, etc.
      {
        [table reloadSections:thisSectionSet withRowAnimation:UITableViewRowAnimationFade];
      }

    } else {
      [table deleteRowsAtIndexPaths:[NSArray arrayWithObject:path] withRowAnimation:UITableViewRowAnimationFade];
    }

    [table endUpdates];
  }
}

// Individual rows can opt out of having the -editing property set for them. If not implemented, all rows are assumed to be editable.
- (BOOL)tableView:(UITableView *)ourTableView canEditRowAtIndexPath:(NSIndexPath *)indexPath
{
  RETURN_IF_SEARCH_TABLE_VIEW(NO);

  TiUITableViewRowProxy *row = [self rowForIndexPath:indexPath];

  //If editable, then this is always true.
  if ([TiUtils boolValue:[row valueForKey:@"editable"] def:editable]) {
    return YES;
  }

  //Elsewhise, when not editing nor moving, return NO, so that swipes don't trigger.

  if (!editing && !moving) {
    return NO;
  }

  //Otherwise, when editing or moving, make sure that both can be done.

  return [TiUtils boolValue:[row valueForKey:@"moveable"] def:moving || moveable] || [TiUtils boolValue:[row valueForKey:@"editable"] def:editing];

  //Why are we checking editable twice? Well, once it's with the default of editable. The second time with the default of editing.
  //Effectively, editable is being tri-state.
}

- (BOOL)tableView:(UITableView *)ourTableView shouldIndentWhileEditingRowAtIndexPath:(NSIndexPath *)indexPath
{
  RETURN_IF_SEARCH_TABLE_VIEW(NO);
  TiUITableViewRowProxy *row = [self rowForIndexPath:indexPath];

  return [TiUtils boolValue:[row valueForKey:@"indentOnEdit"] def:editing];
}

// Allows the reorder accessory view to optionally be shown for a particular row. By default, the reorder control will be shown only if the datasource implements -tableView:moveRowAtIndexPath:toIndexPath:
- (BOOL)tableView:(UITableView *)ourTableView canMoveRowAtIndexPath:(NSIndexPath *)indexPath
{
  RETURN_IF_SEARCH_TABLE_VIEW(NO);

  TiUITableViewRowProxy *row = [self rowForIndexPath:indexPath];
  return [TiUtils boolValue:[row valueForKey:@"moveable"] def:moving || moveable];
}

// Allows customization of the editingStyle for a particular cell located at 'indexPath'. If not implemented, all editable cells will have UITableViewCellEditingStyleDelete set for them when the table has editing property set to YES.
- (UITableViewCellEditingStyle)tableView:(UITableView *)ourTableView editingStyleForRowAtIndexPath:(NSIndexPath *)indexPath
{
  RETURN_IF_SEARCH_TABLE_VIEW(UITableViewCellEditingStyleNone);
  TiUITableViewRowProxy *row = [self rowForIndexPath:indexPath];

  //Yes, this looks similar to canEdit, but here we need to make the distinction between moving and editing.

  //Actually, it's easier than that. editable or editing causes this to default true. Otherwise, it's the editable flag.
  if ([TiUtils boolValue:[row valueForKey:@"editable"] def:editable || editing]) {
    return UITableViewCellEditingStyleDelete;
  }
  return UITableViewCellEditingStyleNone;
}

- (void)tableView:(UITableView *)ourTableView moveRowAtIndexPath:(NSIndexPath *)sourceIndexPath toIndexPath:(NSIndexPath *)destinationIndexPath
{
  RETURN_IF_SEARCH_TABLE_VIEW();
  NSInteger fromSectionIndex = [sourceIndexPath section];
  NSInteger toSectionIndex = [destinationIndexPath section];
  NSInteger fromRowIndex = [sourceIndexPath row];
  NSInteger toRowIndex = [destinationIndexPath row];

  if ((fromSectionIndex == toSectionIndex) && (fromRowIndex == toRowIndex)) {
    //No need to fire a move event if the row never moved
    return;
  }

  TiUITableViewSectionProxy *fromSection = [self sectionForIndex:fromSectionIndex];
  TiUITableViewSectionProxy *toSection = fromSectionIndex != toSectionIndex ? [self sectionForIndex:toSectionIndex] : fromSection;
  TiUITableViewRowProxy *fromRow = [fromSection rowAtIndex:fromRowIndex];
  // hold during the move in case the array is the last guy holding the retain count
  [fromRow retain];
  [fromSection remove:fromRow];
  if (([toSection rows] == nil) || ([[toSection rows] count] <= toRowIndex)) {
    [toSection add:fromRow];
  } else {
    [[toSection rows] insertObject:fromRow atIndex:toRowIndex];
    [toSection rememberProxy:fromRow];
  }
  fromRow.section = toSection;
  [toSection reorderRows];
  if (fromSectionIndex != toSectionIndex) {
    [fromSection reorderRows];
  }
  // now we can release from our retain above
  [fromRow autorelease];
  [self triggerActionForIndexPath:destinationIndexPath fromPath:sourceIndexPath tableView:ourTableView wasAccessory:NO search:NO name:@"move"];
}

- (BOOL)tableView:(UITableView *)tableView shouldBeginMultipleSelectionInteractionAtIndexPath:(NSIndexPath *)indexPath
{
  RETURN_IF_SEARCH_TABLE_VIEW(NO);

  return [TiUtils boolValue:[[self proxy] valueForUndefinedKey:@"allowsMultipleSelectionDuringEditing"] def:NO] && [TiUtils boolValue:[[self proxy] valueForUndefinedKey:@"allowsMultipleSelectionInteraction"] def:NO];
}

- (void)tableView:(UITableView *)tableView didBeginMultipleSelectionInteractionAtIndexPath:(NSIndexPath *)indexPath
{
  editing = YES;
}

- (void)tableViewDidEndMultipleSelectionInteraction:(UITableView *)tableView
{
  if ([self.proxy _hasListeners:@"rowsselected"]) {
    NSMutableArray *selectedItems = [NSMutableArray arrayWithCapacity:tableView.indexPathsForSelectedRows.count];
    NSMutableDictionary *startingRowObject = [NSMutableDictionary dictionaryWithCapacity:1];

    for (int i = 0; i < tableView.indexPathsForSelectedRows.count; i++) {
      NSIndexPath *index = tableView.indexPathsForSelectedRows[i];
      NSInteger sectionIdx = [index section];
      NSArray *sections = [(TiUITableViewProxy *)[self proxy] internalSections];
      TiUITableViewSectionProxy *section = [self sectionForIndex:sectionIdx];

      NSInteger dataIndex = [self rowIndexForIndexPath:index andSections:sections];

      TiUITableViewRowProxy *row = [section rowAtIndex:[index row]];

      NSMutableDictionary *eventObject = [NSMutableDictionary dictionaryWithObjectsAndKeys:
                                                                  section, @"section",
                                                              NUMINTEGER(dataIndex), @"index",
                                                              row, @"row",
                                                              row, @"rowData",
                                                              nil];
      [selectedItems addObject:eventObject];
    }
    [self.proxy fireEvent:@"rowsselected" withObject:@{ @"selectedRows" : selectedItems, @"startingRow" : startingRowObject }];
  }
}

#pragma mark Collation

- (NSArray *)sectionIndexTitlesForTableView:(UITableView *)ourTableView
{
  RETURN_IF_SEARCH_TABLE_VIEW(nil);
  if (sectionIndex != nil && !editing) {
    return sectionIndex;
  }
  return nil;
}

- (NSInteger)tableView:(UITableView *)ourTableView sectionForSectionIndexTitle:(NSString *)title atIndex:(NSInteger)index
{
  if (sectionIndexMap != nil) {
    // get the section for the row index
    int index = [[sectionIndexMap objectForKey:title] intValue];

    if ([(TiViewProxy *)[self proxy] _hasListeners:@"indexclick" checkParent:NO]) {
      NSDictionary *eventArgs = [NSDictionary dictionaryWithObjectsAndKeys:title, @"title", NUMINT(index), @"index", nil];
      [[self proxy] fireEvent:@"indexclick" withObject:eventArgs propagate:NO];
    }
    return [self sectionIndexForIndex:index];
  }
  return 0;
}

- (void)selectRow:(id)args
{
  NSInteger index = [TiUtils intValue:[args objectAtIndex:0]];
  NSIndexPath *path = [self indexPathFromInt:index];
  if (path == nil) {
    NSLog(@"[WARN] invalid index specified for selectRow");
    return;
  }
  TiUITableViewRowProxy *rowProxy = [self rowForIndexPath:path];

  if ([rowProxy callbackCell] == nil) {
    //Not displayed at present. Go ahead and scroll to row and reperform selectRow after delay
    [[self tableView] scrollToRowAtIndexPath:path atScrollPosition:UITableViewScrollPositionMiddle animated:NO];
    NSDictionary *dict = [NSDictionary dictionaryWithObject:NUMBOOL(NO) forKey:@"animated"];
    NSArray *newArgs = [NSArray arrayWithObjects:NUMINTEGER(index), dict, nil];
    [self performSelector:@selector(selectRow:) withObject:newArgs afterDelay:.1];
    return;
  }
  NSDictionary *dict = [args count] > 1 ? [args objectAtIndex:1] : nil;
  BOOL animated = [TiUtils boolValue:@"animated" properties:dict def:YES];
  int scrollPosition = [TiUtils intValue:@"position" properties:dict def:UITableViewScrollPositionMiddle];
  [[self tableView] selectRowAtIndexPath:path animated:animated scrollPosition:scrollPosition];
}

- (void)deselectRow:(id)args
{
  NSInteger index = [TiUtils intValue:[args objectAtIndex:0]];
  NSDictionary *dict = [args count] > 1 ? [args objectAtIndex:1] : nil;
  BOOL animated = [TiUtils boolValue:@"animated" properties:dict def:YES];
  NSIndexPath *path = [self indexPathFromInt:index];
  [[self tableView] deselectRowAtIndexPath:path animated:animated];
}

- (void)viewResignFocus
{
  // As Search controller is presented, we can not open window over it. If any other window get opened above it, we are deactivating Search Controller with saved state if it is activated. And activate Search Controller again when this window get focus in viewGetFocus method.

  if (!hideOnSearch && isSearched && [searchController isActive]) {
    [searchController setActive:false];
  } else {
    isSearched = NO;
  }
}

- (void)viewGetFocus
{
  if (isSearchBarInNavigation) {
    id proxy = [(TiViewProxy *)self.proxy parent];
    while ([proxy isKindOfClass:[TiViewProxy class]] && ![proxy isKindOfClass:[TiWindowProxy class]]) {
      proxy = [proxy parent];
    }
    UIViewController *controller;
    if ([proxy isKindOfClass:[TiWindowProxy class]]) {
      controller = [[proxy windowHoldingController] retain];
    } else {
      controller = [[[TiApp app] controller] retain];
    }
    if (!controller.navigationItem.searchController) {
      controller.navigationItem.searchController = searchController;
    }
    RELEASE_TO_NIL(controller);
  }
  if (!hideOnSearch && isSearched && self.searchedString && ![searchController isActive]) {
    isSearched = NO;
    searchController.searchBar.text = self.searchedString;
    [searchController performSelector:@selector(setActive:) withObject:@YES afterDelay:.1];
    [searchController.searchBar performSelector:@selector(becomeFirstResponder) withObject:nil afterDelay:.2];
  }
}

#pragma mark - DimmingView Manage

- (void)createDimmingView
{
  if (dimmingView == nil) {
    dimmingView = [[UIView alloc] initWithFrame:CGRectMake(0, searchController.searchBar.frame.size.height, self.frame.size.width, self.frame.size.height - searchController.searchBar.frame.size.height)];
    dimmingView.backgroundColor = [UIColor blackColor];
    dimmingView.alpha = .2;
    UITapGestureRecognizer *tapGesture = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(dismissSearchController)];
    [dimmingView addGestureRecognizer:tapGesture];
    [tapGesture release];
  }
}

- (void)showDimmingView
{
  dimmingView.frame = CGRectMake(0, searchController.searchBar.frame.size.height, self.frame.size.width, self.frame.size.height - searchController.searchBar.frame.size.height);
  if (!dimmingView.superview) {
    [self addSubview:dimmingView];
    [self bringSubviewToFront:dimmingView];
  }
}
- (void)hideDimmingView
{
  [dimmingView removeFromSuperview];
}

- (void)dismissSearchController
{
  [searchController setActive:NO];
}

- (void)keyboardWillChangeFrame:(NSNotification *)notification
{
  NSDictionary *userInfo = [notification userInfo];
  CGRect keyboardEndFrame = [[userInfo objectForKey:UIKeyboardFrameEndUserInfoKey] CGRectValue];
  CGPoint convertedOrigin = [self.superview convertPoint:self.frame.origin toView:searchControllerPresenter.view];

  CGRect mainScreenBounds = [[UIScreen mainScreen] bounds];
  CGFloat height = keyboardEndFrame.origin.y - mainScreenBounds.size.height < 0 ? keyboardEndFrame.origin.y - convertedOrigin.y : keyboardEndFrame.origin.y;

  [self keyboardDidShowAtHeight:height];
}

- (void)keyboardDidChangeFrame:(NSNotification *)notification
{
  NSDictionary *userInfo = [notification userInfo];
  CGRect keyboardEndFrame = [[userInfo objectForKey:UIKeyboardFrameEndUserInfoKey] CGRectValue];
  CGPoint convertedOrigin = [self.superview convertPoint:self.frame.origin toView:searchControllerPresenter.view];

  CGRect mainScreenBounds = [[UIScreen mainScreen] bounds];
  CGFloat height = keyboardEndFrame.origin.y - mainScreenBounds.size.height < 0 ? keyboardEndFrame.origin.y - convertedOrigin.y : keyboardEndFrame.origin.y;

  [self keyboardDidShowAtHeight:height];
}

#pragma mark Delegate

- (void)tableView:(UITableView *)ourTableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath
{
  BOOL search = NO;
  if ((!allowsSelectionSet || ![ourTableView allowsSelection]) && !editing) {
    [ourTableView deselectRowAtIndexPath:indexPath animated:YES];
  }
  if ([searchController isActive]) {
    search = YES;
  }
  [self triggerActionForIndexPath:indexPath fromPath:nil tableView:ourTableView wasAccessory:NO search:search name:@"click"];
}

- (void)tableView:(UITableView *)ourTableView willDisplayCell:(UITableViewCell *)cell forRowAtIndexPath:(NSIndexPath *)indexPath
{
  NSIndexPath *index = indexPath;
  if ([searchController isActive] && searchResultIndexes) {
    index = [self indexPathFromSearchIndex:[indexPath row]];
  }

  TiUITableViewRowProxy *row = [self rowForIndexPath:index];

  NSString *color = [row valueForKey:@"backgroundColor"];
  if (color == nil) {
    color = [self.proxy valueForKey:@"rowBackgroundColor"];
    if (color == nil) {
      color = [self.proxy valueForKey:@"backgroundColor"];
    }
  }
  UIColor *cellColor = [TiUtils colorValue:color].color;
  if (cellColor == nil) {
    cellColor = [UIColor whiteColor];
  }
  cell.backgroundColor = cellColor;
  if (CGColorGetAlpha([cellColor CGColor]) < 1.0) {
    [[cell textLabel] setBackgroundColor:[UIColor clearColor]];
  }
}

- (NSString *)tableView:(UITableView *)ourTableView titleForDeleteConfirmationButtonForRowAtIndexPath:(NSIndexPath *)indexPath
{
  RETURN_IF_SEARCH_TABLE_VIEW(nil);
  TiUITableViewRowProxy *ourRow = [self rowForIndexPath:indexPath];
  NSString *result = [TiUtils stringValue:[ourRow valueForKey:@"deleteButtonTitle"]];
  if (result == nil) {
    result = [[self proxy] valueForKey:@"deleteButtonTitle"];
  }

  if (result == nil) {
    result = NSLocalizedString(@"Delete", @"Table View Delete Confirm");
  }
  return result;
}

- (void)tableView:(UITableView *)tableView didEndEditingRowAtIndexPath:(NSIndexPath *)indexPath
{
  TiUITableViewRowProxy *row = [self rowForIndexPath:indexPath];
  if (row) {
    [row.section reorderRows];
  }
}

- (void)tableView:(UITableView *)ourTableView accessoryButtonTappedForRowWithIndexPath:(NSIndexPath *)indexPath
{
  BOOL search = NO;
  if (!allowsSelectionSet || ![ourTableView allowsSelection]) {
    [ourTableView deselectRowAtIndexPath:indexPath animated:YES];
  }
  if ([searchController isActive]) {
    search = YES;
  }
  [self triggerActionForIndexPath:indexPath fromPath:nil tableView:ourTableView wasAccessory:YES search:search name:@"click"];
}

- (NSInteger)tableView:(UITableView *)ourTableView indentationLevelForRowAtIndexPath:(NSIndexPath *)indexPath
{
  RETURN_IF_SEARCH_TABLE_VIEW(0);

  TiUITableViewRowProxy *row = [self rowForIndexPath:indexPath];
  id indent = [row valueForKey:@"indentionLevel"];
  return indent == nil ? 0 : [TiUtils intValue:indent];
}

- (CGFloat)computeRowWidth
{
  CGFloat rowWidth = tableview.bounds.size.width;
#ifdef TI_USE_AUTOLAYOUT
  if (rowWidth == 0) {
    rowWidth = [[[[[UIApplication sharedApplication] delegate] window] rootViewController] view].bounds.size.width;
  }
#endif

  // Apple does not provide a good way to get information about the index sidebar size
  // in the event that it exists - it silently resizes row content which is "flexible width"
  // but this is not useful for us. This is a problem when we have Ti.UI.SIZE/FILL behavior
  // on row contents, which rely on the height of the row to be accurately precomputed.
  //
  // The following is unreliable since it uses a private API name, but one which has existed
  // since iOS 3.0. The alternative is to grab a specific subview of the tableview itself,
  // which is more fragile.

  NSArray *subviews = [tableview subviews];
  if ([subviews count] > 0) {
    // Obfuscate private class name
    Class indexview = NSClassFromString([@"UITableView" stringByAppendingString:@"Index"]);
    for (UIView *view in subviews) {
      if ([view isKindOfClass:indexview]) {
        rowWidth -= [view frame].size.width;
      }
    }
  }

  if (tableview.style == UITableViewStyleInsetGrouped) {
    rowWidth -= tableview.layoutMargins.left + tableview.layoutMargins.right;
  }
  return rowWidth;
}

#ifdef TI_USE_AUTOLAYOUT
- (CGFloat)tableView:(UITableView *)ourTableView estimatedHeightForRowAtIndexPath:(nonnull NSIndexPath *)indexPath
{
  return 45;
}
#endif

- (CGFloat)tableView:(UITableView *)ourTableView heightForRowAtIndexPath:(NSIndexPath *)indexPath
{
  NSIndexPath *index = indexPath;
  if ([self isSearchStarted]) {
    index = [self indexPathFromSearchIndex:[indexPath row]];
  }

  TiUITableViewRowProxy *row = [self rowForIndexPath:index];

  CGFloat width = [row sizeWidthForDecorations:[self computeRowWidth] forceResizing:YES];
  CGFloat height = [row rowHeight:width];
  height = [self tableRowHeight:height];
  return height < 1 ? tableview.rowHeight : height;
}

- (UIView *)tableView:(UITableView *)ourTableView viewForHeaderInSection:(NSInteger)section
{
  RETURN_IF_SEARCH_TABLE_VIEW(nil);
  return [self sectionView:section forLocation:@"headerView" section:nil];
}

- (UIView *)tableView:(UITableView *)ourTableView viewForFooterInSection:(NSInteger)section
{
  RETURN_IF_SEARCH_TABLE_VIEW(nil);
  return [self sectionView:section forLocation:@"footerView" section:nil];
}

- (CGFloat)tableView:(UITableView *)ourTableView heightForHeaderInSection:(NSInteger)section
{
  RETURN_IF_SEARCH_TABLE_VIEW(0.0);
  RETURN_IF_SEARCH_IS_ACTIVE(0.0);
  TiUITableViewSectionProxy *sectionProxy = nil;
  TiUIView *view = [self sectionView:section forLocation:@"headerView" section:&sectionProxy];
  TiViewProxy *viewProxy = (TiViewProxy *)[view proxy];
  CGFloat size = 0.0;
  if (viewProxy != nil) {
#ifndef TI_USE_AUTOLAYOUT
    LayoutConstraint *viewLayout = [viewProxy layoutProperties];
    switch (viewLayout->height.type) {
    case TiDimensionTypeDip:
      size += viewLayout->height.value;
      break;
    case TiDimensionTypeAuto:
      size += [viewProxy autoHeightForSize:[tableview bounds].size];
      break;
    default:
      size += DEFAULT_SECTION_HEADERFOOTER_HEIGHT;
      break;
    }
#endif
  }
  /*
     * This behavior is slightly more complex between iOS 4 and iOS 5 than you might believe, and Apple's
     * documentation is once again misleading. It states that in iOS 4 this value was "ignored if
     * -[delegate tableView:viewForHeaderInSection:] returned nil" but apparently a non-nil value for
     * -[delegate tableView:titleForHeaderInSection:] is considered a valid value for height handling as well,
     * provided it is NOT the empty string.
     * 
     * So for parity with iOS 4, iOS 5 must similarly treat the empty string header as a 'nil' value and
     * return a 0.0 height that is overridden by the system.
     */
  else if ([sectionProxy headerTitle] != nil) {
    if ([[sectionProxy headerTitle] isEqualToString:@""]) {
      return size;
    }
    size += [tableview sectionHeaderHeight];

    if (size < DEFAULT_SECTION_HEADERFOOTER_HEIGHT) {
      size += DEFAULT_SECTION_HEADERFOOTER_HEIGHT;
    }
  }
  return size;
}

- (CGFloat)tableView:(UITableView *)ourTableView heightForFooterInSection:(NSInteger)section
{
  RETURN_IF_SEARCH_TABLE_VIEW(0.0);
  RETURN_IF_SEARCH_IS_ACTIVE(0.0);
  TiUITableViewSectionProxy *sectionProxy = nil;
  TiUIView *view = [self sectionView:section forLocation:@"footerView" section:&sectionProxy];
  TiViewProxy *viewProxy = (TiViewProxy *)[view proxy];
  CGFloat size = 0;
  BOOL hasTitle = NO;
  if (viewProxy != nil) {
#ifndef TI_USE_AUTOLAYOUT
    LayoutConstraint *viewLayout = [viewProxy layoutProperties];
    switch (viewLayout->height.type) {
    case TiDimensionTypeDip:
      size += viewLayout->height.value;
      break;
    case TiDimensionTypeAuto:
      size += [viewProxy autoHeightForSize:[tableview bounds].size];
      break;
    default:
      size += DEFAULT_SECTION_HEADERFOOTER_HEIGHT;
      break;
    }
#endif
  } else if ([sectionProxy footerTitle] != nil) {
    hasTitle = YES;
    size += [tableview sectionFooterHeight];
  }
  if (hasTitle && size < DEFAULT_SECTION_HEADERFOOTER_HEIGHT) {
    size += DEFAULT_SECTION_HEADERFOOTER_HEIGHT;
  }
  return size;
}

- (void)keyboardDidShowAtHeight:(CGFloat)keyboardTop
{
  CGRect minimumContentRect = [tableview bounds];
  InsetScrollViewForKeyboard(tableview, keyboardTop, minimumContentRect.size.height + minimumContentRect.origin.y);
}

- (void)scrollToShowView:(TiUIView *)firstResponderView withKeyboardHeight:(CGFloat)keyboardTop
{
  if ([tableview isScrollEnabled]) {
    CGRect minimumContentRect = [tableview bounds];

    CGRect responderRect = [self convertRect:[firstResponderView bounds] fromView:firstResponderView];
    CGPoint offsetPoint = [tableview contentOffset];
    responderRect.origin.x += offsetPoint.x;
    responderRect.origin.y += offsetPoint.y;

    OffsetScrollViewForRect(tableview, keyboardTop, minimumContentRect.size.height + minimumContentRect.origin.y, responderRect);
  }
}

#pragma Scroll View Delegate

- (BOOL)scrollViewShouldScrollToTop:(UIScrollView *)scrollView
{
  // suspend image loader while we're scrolling to improve performance
  [[ImageLoader sharedLoader] suspend];
  return YES;
}

- (NSDictionary *)eventObjectForScrollView:(UIScrollView *)scrollView
{
  return [NSDictionary dictionaryWithObjectsAndKeys:
                           [TiUtils pointToDictionary:scrollView.contentOffset], @"contentOffset",
                       [TiUtils sizeToDictionary:scrollView.contentSize], @"contentSize",
                       [TiUtils sizeToDictionary:tableview.bounds.size], @"size",
                       nil];
}

- (void)fireScrollEvent:(UIScrollView *)scrollView
{
  if ([self.proxy _hasListeners:@"scroll"]) {
    [self.proxy fireEvent:@"scroll" withObject:[self eventObjectForScrollView:scrollView]];
  }
}

- (void)scrollViewDidScroll:(UIScrollView *)scrollView
{
  if (scrollView.isDragging || scrollView.isDecelerating) {
    [self fireScrollEvent:scrollView];
  }
}

- (void)scrollViewDidScrollToTop:(UIScrollView *)scrollView
{
  [self fireScrollEvent:scrollView];

  // resume image loader when we're done scrolling
  [[ImageLoader sharedLoader] resume];
}

- (void)scrollViewWillBeginDragging:(UIScrollView *)scrollView
{
  // suspend image loader while we're scrolling to improve performance
  [[ImageLoader sharedLoader] suspend];
  if ([self.proxy _hasListeners:@"dragStart"]) { //TODO: Deprecate old event.
    [self.proxy fireEvent:@"dragStart" withObject:nil];
  }
  if ([self.proxy _hasListeners:@"dragstart"]) {
    [self.proxy fireEvent:@"dragstart" withObject:nil];
  }
}

- (void)scrollViewDidEndDragging:(UIScrollView *)scrollView willDecelerate:(BOOL)decelerate
{
  if (!decelerate) {
    // resume image loader when we're done scrolling
    [[ImageLoader sharedLoader] resume];
  }
  if ([self.proxy _hasListeners:@"dragEnd"]) { //TODO: Deprecate old event
    [self.proxy fireEvent:@"dragEnd" withObject:[NSDictionary dictionaryWithObjectsAndKeys:[NSNumber numberWithBool:decelerate], @"decelerate", nil]];
  }
  if ([self.proxy _hasListeners:@"dragend"]) {
    [self.proxy fireEvent:@"dragend" withObject:[NSDictionary dictionaryWithObjectsAndKeys:[NSNumber numberWithBool:decelerate], @"decelerate", nil]];
  }

  //This section of code now moved to [TiUITextWidgetView updateKeyboardStatus]
  // Update keyboard status to insure that any fields actively being edited remain in view
  //if ([[[TiApp app] controller] keyboardVisible]) {
  //    [[[TiApp app] controller] performSelector:@selector(handleNewKeyboardStatus) withObject:nil afterDelay:0.0];
  //}
}

- (void)scrollViewDidEndDecelerating:(UIScrollView *)scrollView
{
  // resume image loader when we're done scrolling
  [[ImageLoader sharedLoader] resume];
  if ([self.proxy _hasListeners:@"scrollEnd"]) { //TODO: Deprecate old event.
    [self.proxy fireEvent:@"scrollEnd" withObject:[self eventObjectForScrollView:scrollView]];
  }
  if ([self.proxy _hasListeners:@"scrollend"]) {
    [self.proxy fireEvent:@"scrollend" withObject:[self eventObjectForScrollView:scrollView]];
  }
}

#pragma mark - UISearchControllerDelegate

- (void)willDismissSearchController:(UISearchController *)searchController
{
  [self hideDimmingView];
}

- (void)didDismissSearchController:(UISearchController *)searchController
{
  if (viewWillDetach) {
    return;
  }

  [self hideDimmingView];
  animateHide = YES;
  [self performSelector:@selector(hideSearchScreen:) withObject:nil afterDelay:0.2];
  // Since we clear the searchbar, the search string and indexes can be cleared as well.
  [self setSearchString:nil];
  RELEASE_TO_NIL(searchResultIndexes);
  RELEASE_TO_NIL(searchControllerPresenter);
  [searchField ensureSearchBarHierarchy];
}

- (void)presentSearchController:(UISearchController *)controller
{
  TiColor *resultsBackgroundColor = [TiUtils colorValue:[[self proxy] valueForKey:@"resultsBackgroundColor"]];
  TiColor *resultsSeparatorColor = [TiUtils colorValue:[[self proxy] valueForKey:@"resultsSeparatorColor"]];
  id resultsSeparatorInsets = [[self proxy] valueForKey:@"resultsSeparatorInsets"];
  id resultsSeparatorStyle = [[self proxy] valueForKey:@"resultsSeparatorStyle"];

  ENSURE_TYPE_OR_NIL(resultsSeparatorInsets, NSDictionary);
  ENSURE_TYPE_OR_NIL(resultsSeparatorStyle, NSNumber);

  if (resultsBackgroundColor) {
    // TIMOB-23281: Hack to support transparent backgrounds (not officially supported)
    UIColor *color = [resultsBackgroundColor _color] == [UIColor clearColor] ? [UIColor colorWithWhite:1.0 alpha:0.0001] : [resultsBackgroundColor _color];
    [tableview setBackgroundColor:color];
  }

  if (resultsSeparatorColor) {
    [tableview setSeparatorColor:[resultsSeparatorColor _color]];
  }

  if (resultsSeparatorInsets) {
    [tableview setSeparatorInset:[TiUtils contentInsets:resultsSeparatorInsets]];
  }

  if (resultsSeparatorStyle) {
    [tableview setSeparatorStyle:[TiUtils intValue:resultsSeparatorStyle def:UITableViewCellSeparatorStyleSingleLine]];
  }
  tableContentOffset = [tableview contentOffset];

  if (!searchControllerPresenter) {
    id proxy = [(TiViewProxy *)self.proxy parent];
    while ([proxy isKindOfClass:[TiViewProxy class]] && ![proxy isKindOfClass:[TiWindowProxy class]]) {
      proxy = [proxy parent];
    }
    if ([proxy isKindOfClass:[TiWindowProxy class]]) {
      searchControllerPresenter = [[proxy windowHoldingController] retain];
    } else {
      searchControllerPresenter = [[[TiApp app] controller] retain];
    }
  }
  searchControllerPresenter.definesPresentationContext = YES;

  [searchControllerPresenter presentViewController:controller
                                          animated:NO
                                        completion:^{
                                          isSearched = YES;
                                          [self showDimmingView];
                                          [self updateSearchControllerFrames];
                                        }];
}

#pragma mark - UISearchResultsUpdating

- (void)updateSearchResultsForSearchController:(UISearchController *)controller
{
  self.searchString = [controller.searchBar text];

  if (self.searchString.length > 0) {
    [self hideDimmingView];
  } else if (controller.isActive) {
    [self showDimmingView];
  }

  [self updateSearchResultIndexes];
  [tableview reloadData];
}

@end

#endif
