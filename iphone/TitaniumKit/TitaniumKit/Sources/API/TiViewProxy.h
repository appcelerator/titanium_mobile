/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#import "TiProxy.h"
#import "TiRect.h"
#import "TiUIView.h"
#import "TiViewTemplate.h"
#import <pthread.h>

/**
 Protocol for views that can receive keyboard focus.
 */
@protocol TiKeyboardFocusableView

#pragma mark Public Titanium APIs.

/**
 Tells the view to focus.
 @param args Unused.
 */
- (void)focus:(id)args;

/**
 Tells the view to stop generating focus/blur events. This should not be
 JS-accessable, and is meant to handle tableview and layout issues.
 */
@property (nonatomic, readwrite, assign) BOOL suppressFocusEvents;

/**
 Tells the view to blur.
 @param args Unused.
 */
- (void)blur:(id)args;
/**
 Tells if this proxy is currently focused
 */
- (BOOL)focused:(id)unused;

#pragma mark Private internal APIs.

/**
 Returns keyboard accessory view.
 */
@property (nonatomic, readonly) UIView *keyboardAccessoryView;

/**
 Returns keyboard accessory height.
 */
@property (nonatomic, readonly) CGFloat keyboardAccessoryHeight;

@end

/*
 This Protocol will be implemented by objects that want to
 monitor views not in the normal view hierarchy.
*/
@protocol TiProxyObserver
@optional
- (void)proxyDidRelayout:(id)sender;

@end

@protocol TiViewEventOverrideDelegate <NSObject>
@required
- (NSDictionary *)overrideEventObject:(NSDictionary *)eventObject forEvent:(NSString *)eventType fromViewProxy:(TiViewProxy *)viewProxy;

@end

#pragma mark dirtyflags used by TiViewProxy
#define NEEDS_LAYOUT_CHILDREN 1
//Set this flag to true to disable instant updates
static const BOOL ENFORCE_BATCH_UPDATE = NO;

enum {
  TiRefreshViewPosition = 2,
  TiRefreshViewChildrenPosition,
  TiRefreshViewZIndex,
  TiRefreshViewSize,

  TiRefreshViewEnqueued,
};

@class TiAction, TiBlob;
//For TableRows, we need to have minimumParentHeightForWidth:

/**
 The class represents a proxy that is attached to a view.
 The class is not intended to be overriden.
 */
@interface TiViewProxy : TiProxy <LayoutAutosizing> {
  @protected
  //TODO: Actually have a rhyme and reason on keeping things @protected vs @private.
  //For now, for sake of proper value grouping, we're all under one roof.

#ifndef TI_USE_AUTOLAYOUT
#pragma mark Layout properties
  LayoutConstraint layoutProperties;
#endif
  int vzIndex;
  BOOL hidden; // This is the boolean version of ![TiUtils boolValue:visible def:YES]
  BOOL safeAreaProxyAdded;

#pragma mark Parent/Children relationships
  TiViewProxy *parent;
  pthread_rwlock_t childrenLock;
  NSMutableArray *children;
  //	NSMutableArray *pendingAdds;

#pragma mark Visual components
  TiUIView *view;
  UIBarButtonItem *barButtonItem;

#pragma mark Layout caches that can be recomputed
  CGFloat verticalLayoutBoundary;
  CGFloat horizontalLayoutBoundary;
  CGFloat horizontalLayoutRowHeight; //Note, this has nothing to do with table views.
  int lastChildArranged;

  CGRect sandboxBounds;
  CGPoint positionCache; //Recomputed and stored when position changes.
  CGRect sizeCache; //Recomputed and stored when size changes.
  UIViewAutoresizing autoresizeCache; //Changed by repositioning or resizing.

  BOOL parentVisible;
  //In most cases, this is the same as [parent parentVisible] && ![parent hidden]
  //However, in the case of windows attached to the root view, the parent is ALWAYS visible.
  //That is, will be true if and only if all parents are visible or are the root controller.
  //Use parentWillShow and parentWillHide to set this.

#pragma mark Housecleaning that is set and used
  NSRecursiveLock *destroyLock;

  BOOL windowOpened;
  BOOL windowOpening;

  int dirtyflags; //For atomic actions, best to be explicit about the 32 bitness.
  BOOL viewInitialized;
  BOOL repositioning;
  BOOL isUsingBarButtonItem;
  //This flag is set to true on beginning of _initWithProperties() call and false near the end of the method
  BOOL updateStarted;
  BOOL allowLayoutUpdate;

  NSMutableDictionary *layoutPropDictionary;

  id observer;
  id<TiViewEventOverrideDelegate> eventOverrideDelegate;
}

#pragma mark public API

@property (nonatomic, readonly) TiRect *size;
@property (nonatomic, readonly) TiRect *rect;
/*
 Provides access to z-index value.
 */
@property (nonatomic, readwrite, assign) int vzIndex;
/**
 Provides access to visibility of parent view proxy.
 */
@property (nonatomic, readwrite, assign) BOOL parentVisible; // For tableview magic ONLY

/**
 Returns children view proxies for the proxy.
 */
@property (nonatomic, readonly) NSArray *children;

- (void)setTempProperty:(id)propVal forKey:(id)propName;
- (void)processTempProperties:(NSDictionary *)arg;
- (BOOL)_hasListeners:(NSString *)type checkParent:(BOOL)check;
- (void)setProxyObserver:(id)arg;

/**
 Tells the view proxy to add a child proxy.
 @param arg A single proxy to add or NSArray of proxies.
 */
- (void)add:(id)arg;

/**
 Tells the view proxy to remove a child proxy.
 @param arg A single proxy to remove.
 */
- (void)remove:(id)arg;

/**
 Tells the view proxy to remove all child proxies.
 @param arg Ignored.
 */
- (void)removeAllChildren:(id)arg;

/**
 Tells the view proxy to set visibility on a child proxy to _YES_.
 @param arg A single proxy to show.
 */
- (void)show:(id)arg;

/**
 Tells the view proxy to set visibility on a child proxy to _NO_.
 @param arg A single proxy to hide.
 */
- (void)hide:(id)arg;

/**
 Clears all previously created motion effects (if set).
 @param unused An unused parameter for proxy swizzling.
 */
- (void)clearMotionEffects:(id)unused;

/**
 Returns the view by the given ID.
 @param arg The ID of the view to receive.
 */
- (id)getViewById:(id)arg;

/**
 Tells the view proxy to run animation on its view.
 @param arg An animation object.
 */
- (KrollPromise *)animate:(id)arg;

#ifndef TI_USE_AUTOLAYOUT
- (void)setTop:(id)value;
- (void)setBottom:(id)value;
- (void)setLeft:(id)value;
- (void)setRight:(id)value;
- (void)setWidth:(id)value;
- (void)setHeight:(id)value;
#endif
- (void)setZIndex:(id)value;
- (id)zIndex;

// See the code for setValue:forUndefinedKey: for why we can't have this
#ifndef TI_USE_AUTOLAYOUT
- (void)setMinWidth:(id)value;
- (void)setMinHeight:(id)value;
- (void)setCenter:(id)value;
#endif
- (NSMutableDictionary *)center;
- (id)animatedCenter;

- (void)setBackgroundGradient:(id)arg;
- (TiBlob *)toImage:(id)args;
- (TiPoint *)contentOffset;

#pragma mark nonpublic accessors not related to Housecleaning

/**
 Provides access to parent proxy of the view proxy.
 @see add:
 @see remove:
 @see children
 */
@property (nonatomic, assign) TiViewProxy *parent;
//TODO: make this a proper readwrite property declaration.

#ifndef TI_USE_AUTOLAYOUT
/**
 Provides access to layout properties of the underlying view.
 */
@property (nonatomic, readonly, assign) LayoutConstraint *layoutProperties;
#endif

/**
 Provides access to sandbox bounds of the underlying view.
 */
@property (nonatomic, readwrite, assign) CGRect sandboxBounds;
//This is unaffected by parentVisible. So if something is truely visible, it'd be [self visible] && parentVisible.
- (void)setHidden:(BOOL)newHidden withArgs:(id)args;

@property (nonatomic, retain) UIBarButtonItem *barButtonItem;
- (TiUIView *)barButtonViewForSize:(CGSize)bounds;

//NOTE: DO NOT SET VIEW UNLESS IN A TABLE VIEW, AND EVEN THEN.
@property (nonatomic, readwrite, retain) TiUIView *view;

@property (nonatomic, readwrite, assign) id<TiViewEventOverrideDelegate> eventOverrideDelegate;

/**
 Returns language conversion table.
 
 Subclasses may override.
 @return The dictionary 
 */
- (NSMutableDictionary *)langConversionTable;

#pragma mark Methods subclasses should override for behavior changes

/**
 Whether or not the view proxy can have non Ti-Views which have to be pushed to the bottom when adding children.
 **This method is only meant for legacy classes. New classes must implement the proper wrapperView code**
 Subclasses may override.
 @return _NO_ if the view proxy can have non Ti-Views in its view hierarchy
 */
- (BOOL)optimizeSubviewInsertion;

/**
 Whether or not the view proxy needs to suppress relayout.
 
 Subclasses may override.
 @return _YES_ if relayout should be suppressed, _NO_ otherwise.
 */
- (BOOL)suppressesRelayout;

/**
 Whether or not the view proxy supports navigation bar positioning.
 
 Subclasses may override.
 @return _YES_ if navigation bar positioning is supported, _NO_ otherwise.
 */
- (BOOL)supportsNavBarPositioning;

/**
 Whether or not the view proxy can have a UIController object in its parent view.
 
 Subclasses may override.
 @return _YES_ if the view proxy can have a UIController object in its parent view
 */
- (BOOL)canHaveControllerParent;

/**
 Whether or not the view proxy should detach its view on unload.
 
 Subclasses may override.
 @return _YES_ if the view should be detached, _NO_ otherwise.
 */
- (BOOL)shouldDetachViewOnUnload;

/**
 Returns parent view for child proxy.
 
 The method is used in cases when proxies hierarchy is different from views hierarchy.
 Subclasses may override.
 @param child The child view proxy for which return the parent view.
 @return The parent view
 */
- (UIView *)parentViewForChild:(TiViewProxy *)child;

#pragma mark Event trigger methods

/**
 Tells the view proxy that the attached window will open.
 @see windowDidOpen
 */
- (void)windowWillOpen;

/**
 Tells the view proxy that the attached window did open.
 @see windowWillOpen
 */
- (void)windowDidOpen;

/**
 Tells the view proxy that the attached window will close.
 @see windowDidClose
 */
- (void)windowWillClose;

/**
 Tells the view proxy that the attached window did close.
 @see windowWillClose
 */
- (void)windowDidClose;

/**
 Tells the view proxy that its properties are about to change.
 @see didFirePropertyChanges
 */
- (void)willFirePropertyChanges;

/**
 Tells the view proxy that its properties are changed.
 @see willFirePropertyChanges
 */
- (void)didFirePropertyChanges;

/**
 Tells the view proxy that a view will be attached to it.
 @see viewDidAttach
 */
- (void)viewWillAttach; // Need this for video player & possibly other classes which override newView

/**
 Tells the view proxy that a view was attached to it.
 @see viewWillAttach
 */
- (void)viewDidAttach;

/**
 Tells the view proxy that a view will be detached from it.
 @see viewDidDetach
 */
- (void)viewWillDetach;

/**
 Tells the view proxy that a view was detached from it.
 @see viewWillDetach
 */
- (void)viewDidDetach;

#pragma mark Housecleaning state accessors
//TODO: Sounds like the redundancy department of redundancy was here.
/**
 Whether or not a view is attached to the view proxy.
 @return _YES_ if the view proxy has a view attached to it, _NO_ otherwise.
 */
- (BOOL)viewAttached;

/**
 Whether or not the view proxy has been initialized.
 @return _YES_ if the view proxy has been initialized, _NO_ otherwise.
 */
- (BOOL)viewInitialized;

/**
 Whether or not the view proxy has been completely set up.
 @return _YES_ if the view proxy has been initialized and its view has a superview and non-empty bounds, _NO_ otherwise.
 */
- (BOOL)viewReady;

/**
 Whether or not a window attached to the view proxy has been opened.
 @return _YES_ if the view proxy's window has been opened, _NO_ otherwise.
 */
- (BOOL)windowHasOpened;

/**
 Whether or not a window attached to the view proxy is currently being opened.
 @return _YES_ if the view proxy's window is being opened, _NO_ otherwise.
 */
- (BOOL)windowIsOpening;

/**
 Whether or not the view proxy is using a bar button item.
 @return _YES_ if a bar button item is used, _NO_ otherwise.
 */
- (BOOL)isUsingBarButtonItem;

- (CGRect)appFrame; //TODO: Why is this here? It doesn't have anything to do with a specific instance.

#pragma mark Building up and tearing down
- (void)firePropertyChanges;

/**
 Returns a ne view corresponding to the view proxy.
 @return The created view.
 */
- (TiUIView *)newView;

/**
 Tells the view proxy to detach its view.
 */
- (void)detachView;

- (void)destroy;

/**
 Tells the view proxy to remove its bar button item.
 */
- (void)removeBarButtonView;

#pragma mark Callbacks

/**
 Tells the view proxy that its view animation did complete.
 @param animation The completed animation
 */
- (void)animationCompleted:(TiAnimation *)animation;
/**
 Tells the view attached to the view proxy to perform a selector with given arguments.
 @param selector The selector to perform.
 @param object The argument for the method performed.
 @param create The flag to create the view if the one is not attached.
 @param wait The flag to wait till the operation completes.
 */
- (void)makeViewPerformSelector:(SEL)selector withObject:(id)object createIfNeeded:(BOOL)create waitUntilDone:(BOOL)wait;

#pragma mark Layout events, internal and external

/**
 Tells the view proxy that the attached view size will change.
 */
- (void)willChangeSize;

/**
 Tells the view proxy that the attached view position will change.
 */
- (void)willChangePosition;

/**
 Tells the view proxy that the attached view z-index will change.
 */
- (void)willChangeZIndex;

/**
 Tells the view proxy that the attached view layout will change.
 */
- (void)willChangeLayout;

/**
 Tells the view proxy that the attached view will show.
 */
- (void)willShow;

/**
 Tells the view proxy that the attached view will hide.
 */
- (void)willHide;

/**
 Tells the view proxy that the attached view contents will change.
 */
- (void)contentsWillChange;

/**
 Tells the view proxy that the attached view's parent size will change.
 */
- (void)parentSizeWillChange;

/**
 Tells the view proxy that the attached view's parent will change position and size.
 */
- (void)parentWillRelay;

/**
 Tells the view proxy that the attached view's parent will show.
 */
- (void)parentWillShow;

/**
 Tells the view proxy that the attached view's parent will hide.
 */
- (void)parentWillHide;

#pragma mark Layout actions

- (void)refreshView:(TiUIView *)transferView;

/**
 Tells the view proxy to force size refresh of the attached view.
 */
- (void)refreshSize;

/**
 Tells the view proxy to force position refresh of the attached view.
 */
- (void)refreshPosition;

/**
 Puts the view in the layout queue for rendering.
 */
- (void)willEnqueue;

//Unlike the other layout actions, this one is done by the parent of the one called by refreshView.
//This is the effect of refreshing the Z index via careful view placement.
- (void)insertSubview:(UIView *)childView forProxy:(TiViewProxy *)childProxy;

#pragma mark Layout commands that need refactoring out

- (void)determineSandboxBounds;

/**
 Tells the view to layout its children.
 @param optimize Internal use only. Always specify _NO_.
 */
- (void)layoutChildren:(BOOL)optimize;

/**
 Tells the view to layout its children only if there were any layout changes.
 */
- (void)layoutChildrenIfNeeded;

- (void)layoutChild:(TiViewProxy *)child optimize:(BOOL)optimize withMeasuredBounds:(CGRect)bounds;
- (NSArray *)measureChildren:(NSArray *)childArray;
- (CGRect)computeChildSandbox:(TiViewProxy *)child withBounds:(CGRect)bounds;

/**
 Tells the view to adjust its size and position according to the current layout constraints.
 */
- (void)relayout;

- (void)reposition; //Todo: Replace
/**
 Tells if the view is enqueued in the LayoutQueue
 */
- (BOOL)willBeRelaying;

- (BOOL)widthIsAutoFill;
- (BOOL)widthIsAutoSize;
- (BOOL)heightIsAutoFill;
- (BOOL)heightIsAutoSize;
- (BOOL)belongsToContext:(id<TiEvaluator>)context;

/**
 Tells the view that its child view size will change.
 @param child The child view
 */
- (void)childWillResize:(TiViewProxy *)child; //Todo: Replace

- (void)unarchiveFromTemplate:(id)viewTemplate;
+ (TiViewProxy *)unarchiveFromTemplate:(id)viewTemplate inContext:(id<TiEvaluator>)context;

@end

#define USE_VIEW_FOR_METHOD(resultType, methodname, inputType) \
  -(resultType)methodname : (inputType)value                   \
  {                                                            \
    return [[self view] methodname:value];                     \
  }

#define USE_VIEW_FOR_VERIFY_WIDTH USE_VIEW_FOR_METHOD(CGFloat, verifyWidth, CGFloat)
#define USE_VIEW_FOR_VERIFY_HEIGHT USE_VIEW_FOR_METHOD(CGFloat, verifyHeight, CGFloat)
#define USE_VIEW_FOR_CONTENT_WIDTH USE_VIEW_FOR_METHOD(CGFloat, contentWidthForWidth, CGFloat)
#define USE_VIEW_FOR_CONTENT_HEIGHT USE_VIEW_FOR_METHOD(CGFloat, contentHeightForWidth, CGFloat)

#define DECLARE_VIEW_CLASS_FOR_NEWVIEW(viewClass) \
  -(TiUIView *)newView                            \
  {                                               \
    return [[viewClass alloc] init];              \
  }
