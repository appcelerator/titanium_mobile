/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2015 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UIIOSPREVIEWCONTEXT

#import "TiPreviewingDelegate.h"
#import "TiUIListView.h"
#import "TiUIListViewProxy.h"
#import "TiUIScrollView.h"
#import "TiUITableView.h"
#import "TiUITableViewProxy.h"

@implementation TiPreviewingDelegate

- (instancetype)initWithPreviewContext:(TiUIiOSPreviewContextProxy *)previewContext
{
  if (self = [self init]) {
    [self setPreviewContext:previewContext];
  }

  return self;
}

- (void)dealloc
{
  [[_previewContext preview] forgetSelf];
  [[_previewContext sourceView] forgetSelf];

  RELEASE_TO_NIL(_listViewEvent);
  RELEASE_TO_NIL(_previewContext);
  [super dealloc];
}

- (void)previewingContext:(id<UIViewControllerPreviewing>)previewingContext commitViewController:(UIViewController *)viewControllerToCommit
{
  NSMutableDictionary *propertiesDict = [[[NSMutableDictionary alloc] initWithDictionary:[self listViewEvent]] autorelease];
  [propertiesDict setObject:_previewContext.preview forKey:@"preview"];
  [[self previewContext] fireEvent:@"pop" withObject:propertiesDict];
}

- (UIViewController *)previewingContext:(id<UIViewControllerPreviewing>)previewingContext viewControllerForLocation:(CGPoint)location
{
  if ([[self previewContext] preview] == nil) {
    return nil;
  }

  UITableView *tableView = [self ensureTableView];

  if (tableView != nil) {

    // If the tap was not on a cell, don't continue
    if ([tableView cellForRowAtIndexPath:[tableView indexPathForRowAtPoint:location]] == nil) {
      return nil;
    }

    [self setListViewEvent:[self receiveListViewEventFromIndexPath:[tableView indexPathForRowAtPoint:location]]];
    [[self previewContext] fireEvent:@"peek" withObject:[self listViewEvent]];
  } else {
    [[self previewContext] fireEvent:@"peek" withObject:@{ @"preview" : [[self previewContext] preview] }];
  }

  TiViewController *controller = [[[TiViewController alloc] initWithViewProxy:[[self previewContext] preview]] autorelease];
  [[[[self previewContext] preview] view] setFrame:[[controller view] bounds]];
  [[controller view] addSubview:[[[self previewContext] preview] view]];

  NSMutableArray *result = [NSMutableArray array];
  NSUInteger actionIndex = 0;

  if ([[self previewContext] contentHeight] > 0) {
    controller.preferredContentSize = CGSizeMake(0.0, [[self previewContext] contentHeight]);
  }

  for (id item in [[self previewContext] actions]) {
    if ([item isKindOfClass:[TiUIiOSPreviewActionProxy class]]) {
      [item setActionIndex:actionIndex];

      if ([self listViewEvent] != nil) {
        [item setListViewEvent:[self listViewEvent]];
      }

      [result addObject:[(TiUIiOSPreviewActionProxy *)item action]];

      actionIndex++;
    } else if ([item isKindOfClass:[TiUIiOSPreviewActionGroupProxy class]]) {
      [result addObject:[item actionGroup]];
    }
  }

  [controller setPreviewActions:result];
  [[[self previewContext] preview] windowWillOpen];
  [previewingContext setSourceRect:[self createSourceRectWithLocation:location]];

  return controller;
}

- (CGRect)createSourceRectWithLocation:(CGPoint)location
{
  UITableView *tableView = [self ensureTableView];

  if (tableView) {
    NSIndexPath *indexPath = [tableView indexPathForRowAtPoint:location];
    return [[tableView cellForRowAtIndexPath:indexPath] frame];
  }

  return CGRectZero; // The Frame is detected automatically on normal views
}

- (UITableView *)ensureTableView
{
#ifdef USE_TI_UILISTVIEW
  if ([[[self previewContext] sourceView] isKindOfClass:[TiUIListViewProxy class]]) {
    TiUIListViewProxy *listProxy = (TiUIListViewProxy *)[[self previewContext] sourceView];
    TiUIListView *view = (TiUIListView *)[listProxy view];

    return [view tableView];
  }
#endif
#ifdef USE_TI_UITABLEVIEW
  if ([[[self previewContext] sourceView] isKindOfClass:[TiUITableViewProxy class]]) {
    TiUITableViewProxy *tableProxy = (TiUITableViewProxy *)[[self previewContext] sourceView];
    TiUITableView *table = (TiUITableView *)[tableProxy view];

    return [table tableView];
  }
#endif

  return nil;
}

- (NSDictionary *)receiveListViewEventFromIndexPath:(NSIndexPath *)indexPath
{
  NSDictionary *event = @{
    @"sectionIndex" : NUMINTEGER(indexPath.section),
    @"itemIndex" : NUMINTEGER(indexPath.row),
    @"preview" : [[self previewContext] preview]
  };

#ifdef USE_TI_UILISTVIEW
  if ([[[self previewContext] sourceView] isKindOfClass:[TiUIListViewProxy class]]) {
    TiUIListViewProxy *listProxy = (TiUIListViewProxy *)[[self previewContext] sourceView];
    TiUIListSectionProxy *theSection = [listProxy sectionForIndex:indexPath.section];

    NSDictionary *theItem = [theSection itemAtIndex:indexPath.row];
    NSMutableDictionary *eventObject = [[NSMutableDictionary alloc] initWithDictionary:event];

    id propertiesValue = [theItem objectForKey:@"properties"];
    NSDictionary *properties = ([propertiesValue isKindOfClass:[NSDictionary class]]) ? propertiesValue : nil;
    id itemId = [properties objectForKey:@"itemId"];
    if (itemId != nil) {
      [eventObject setObject:itemId forKey:@"itemId"];
    }

    return [eventObject autorelease];
  }
#endif

  return event;
}

@end
#endif
