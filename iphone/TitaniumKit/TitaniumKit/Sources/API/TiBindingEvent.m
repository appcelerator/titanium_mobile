/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2018 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#import "TiBindingEvent.h"
#import "KrollObject.h"
#import "TiBindingRunLoop.h"
#import "TiBindingTiValue.h"
#import "TiExceptionHandler.h"
#import "TiUtils.h"
#import <JavaScriptCore/JavaScriptCore.h>
#include <libkern/OSAtomic.h>

extern JSStringRef kTiStringLength;

/** Event lifecycle, a documentation.
 
 The event structures are designed to be threadsafe, yet don't have a lock.
 How is this so? The trick is the lifecycle and atomic transactions on pendingEvents:
 
 Creation:
	Immutable variables can be set, only one thread has access.
	Can call FireEvent.
 FireEvent:
	While searching for new targetProxy, only one thread has access.
	pendingEvents is set to the number of threads that will be accessing it.
	Once targetProxy found, the event becomes fully immutable again, and event
	is sent to multiple run loops.
 EventProcess:
	Event is immutable during processing
	cancelBubble is mutable, but blindly set during processing, thus no race
	Once done processing, the pendingEvents is atomically decrimented.
	Once pendingEvents reaches 0, we are certain that no other threads are using
	this event. During this time, the event is again mutable for purposes of
	propagation- caching and then fireEvent.
	This also means that while processing, the event will NOT be deallocated
	under us.
 */

struct TiBindingEventOpaque {
  //Abstraction values and tread safety.
  int pendingEvents; //Mutable, acts as lock of sorts due to atomic decrement
  bool bubbles; //Immutable
  bool cancelBubble; //Mutable, set to true
  bool reportError; //Immutable
  NSInteger errorCode; //Immutable
  //Objective C version
  TiProxy *targetProxy; //Immutable in-event, mutable for bubbling.
  TiProxy *sourceProxy; //Immutable
  NSString *eventString; //Immutable
  NSDictionary *payloadDictionary; //Immutable
  NSString *errorMessageString; //Immutable
  //Immutable caching.
  JSStringRef eventStringRef; //Immutable
  JSStringRef errorMessageStringRef; //Immutable
  //Mutable caching in future.
  JSContextRef contextRef;
  JSObjectRef eventObjectRef;
};

void TiBindingEventProcess(TiBindingRunLoop runloop, void *payload);

pthread_once_t jsBindingRunOnce = PTHREAD_ONCE_INIT;
JSStringRef jsEventCancelBubbleStringRef = NULL;
JSStringRef jsEventTypeStringRef = NULL;
JSStringRef jsEventSourceStringRef = NULL;
JSStringRef jsEventBubblesStringRef = NULL;
JSStringRef jsEventSuccessStringRef = NULL;
JSStringRef jsEventErrorCodeStringRef = NULL;
JSStringRef jsEventErrorMessageStringRef = NULL;

void TiBindingInitialize()
{
  jsEventCancelBubbleStringRef = JSStringCreateWithUTF8CString("cancelBubble");
  jsEventTypeStringRef = JSStringCreateWithUTF8CString("type");
  jsEventSourceStringRef = JSStringCreateWithUTF8CString("source");
  jsEventBubblesStringRef = JSStringCreateWithUTF8CString("bubbles");
  jsEventSuccessStringRef = JSStringCreateWithUTF8CString("success");
  jsEventErrorCodeStringRef = JSStringCreateWithUTF8CString("code");
  jsEventErrorMessageStringRef = JSStringCreateWithUTF8CString("error");
}

TiBindingEvent TiBindingEventCreateWithNSObjects(TiProxy *target, TiProxy *source, NSString *type, NSDictionary *payload)
{
  TiBindingEvent result = malloc(sizeof(struct TiBindingEventOpaque));
  result->targetProxy = [target retain];
  result->sourceProxy = [source retain];
  result->eventString = [type copy];
  result->payloadDictionary = [payload copy];
  result->pendingEvents = 0;
  result->bubbles = false;
  result->cancelBubble = false;
  result->reportError = false;
  result->errorCode = 0;
  result->errorMessageString = nil;
  result->eventObjectRef = NULL;
  result->eventStringRef = JSStringCreateWithCFString((CFStringRef)result->eventString);
  result->errorMessageStringRef = NULL;
  result->contextRef = NULL;
  return result;
}

void TiBindingEventSetBubbles(TiBindingEvent event, bool bubbles)
{
  event->bubbles = bubbles;
}

TiProxy *TiBindingEventNextBubbleTargetProxy(TiBindingEvent event, TiProxy *currentTarget, BOOL parentOnly)
{
  while ((currentTarget != nil) && (![currentTarget _hasListeners:event->eventString] || parentOnly)) {
    if (!currentTarget->_bubbleParent || !event->bubbles || event->cancelBubble) {
      return nil;
    }
    parentOnly = false;
    currentTarget = [currentTarget parentForBubbling];

    //TIMOB-11691. Ensure that tableviewrowproxy modifies the event object before passing it along.
    if ([currentTarget respondsToSelector:@selector(createEventObject:)]) {
      NSDictionary *curPayload = event->payloadDictionary;
      NSDictionary *modifiedPayload = [currentTarget performSelector:@selector(createEventObject:) withObject:curPayload];
      [event->payloadDictionary release];
      event->payloadDictionary = [modifiedPayload copy];
    }
  }
  return currentTarget;
}

void TiBindingEventSetErrorCode(TiBindingEvent event, NSInteger code)
{
  event->reportError = true;
  event->errorCode = code;
}

void TiBindingEventSetErrorMessageWithNSString(TiBindingEvent event, NSString *message)
{
  [event->errorMessageString autorelease];
  event->errorMessageString = [message copy];
  if (event->errorMessageStringRef != NULL) {
    JSStringRelease(event->errorMessageStringRef);
  }
  if (message != nil) {
    event->errorMessageStringRef = JSStringCreateWithCFString((CFStringRef)message);
  } else {
    event->errorMessageStringRef = NULL;
  }
}

void TiBindingEventClearError(TiBindingEvent event)
{
  event->reportError = false;
}

void TiBindingEventFire(TiBindingEvent event)
{
  pthread_once(&jsBindingRunOnce, TiBindingInitialize);
  TiProxy *targetProxy = TiBindingEventNextBubbleTargetProxy(event, event->targetProxy, false);

  if (targetProxy == nil) { //Nobody to target, we're done here.
    TiBindingEventDispose(event);
    return;
  }

  int runloopcount = [targetProxy bindingRunLoopCount];

  if (event->targetProxy != targetProxy) {
    [event->targetProxy release];
    event->targetProxy = [targetProxy retain];
  }
  event->pendingEvents = runloopcount;
  if (runloopcount == 1) { //Main case: One run loop.
    TiBindingRunLoop ourRunLoop = [targetProxy primaryBindingRunLoop];
    if (ourRunLoop != nil) { // It's possible that the one remaining runloop
      //Was not the primaryBindingRunLoop. In which case, we flow to the
      //multiple run loops as an edge case.
      TiBindingRunLoopEnqueue(ourRunLoop, TiBindingEventProcess, event);
      return;
    }
  }

  if (runloopcount > 0) { //Edge case: Multiple run loops.
    NSArray *runLoopArray = [targetProxy bindingRunLoopArray];
    for (TiBindingRunLoop thisRunLoop in runLoopArray) {
      TiBindingRunLoopEnqueue(thisRunLoop, TiBindingEventProcess, event);
    }
    return;
  }

  //Extreme edge case. Proxy thinks it still has listeners, but no run loops?!
  TiProxy *newTarget = TiBindingEventNextBubbleTargetProxy(event, targetProxy, YES);
  if (event->targetProxy != newTarget) {
    [event->targetProxy release];
    event->targetProxy = [newTarget retain];
  }
  TiBindingEventFire(event);
}

void TiBindingEventProcess(TiBindingRunLoop runloop, void *payload)
{
  TiBindingEvent event = payload;
  JSObjectRef eventObjectRef = NULL;
  JSValueRef eventTargetRef = NULL;
  JSValueRef eventStringRef = NULL;
  JSValueRef eventSourceRef = NULL;

  KrollObject *targetKrollObject = [event->targetProxy krollObjectForContext:runloop];
  JSObjectRef callbacksObjectRef = [targetKrollObject callbacksForEvent:event->eventStringRef];
  int callbackCount = 0;
  NSArray<KrollPromise *> *promises = [targetKrollObject promisesForEvent:event->eventString];
  int promiseCount = promises != nil ? (int)[promises count] : 0;
  JSContextRef context = [runloop context];

  if (callbacksObjectRef != NULL) {
    JSValueRef jsCallbackArrayLength = JSObjectGetProperty(context, callbacksObjectRef, kTiStringLength, NULL);
    callbackCount = (int)JSValueToNumber(context, jsCallbackArrayLength, NULL);
  }

  if (callbackCount > 0 || promiseCount > 0) {
    //Convert to JSObjectRefs
    if (eventObjectRef == NULL) {
      eventObjectRef = TiBindingTiValueFromNSDictionary(context, event->payloadDictionary);
    }
    if (eventTargetRef == NULL) {
      eventTargetRef = TiBindingTiValueFromProxy(context, event->targetProxy);
    }
    if (eventSourceRef == NULL) {
      eventSourceRef = TiBindingTiValueFromProxy(context, event->sourceProxy);
    }
    if (eventStringRef == NULL) {
      eventStringRef = JSValueMakeString(context, event->eventStringRef);
    }
    JSValueRef bubblesValue = JSValueMakeBoolean(context, event->bubbles);
    JSValueRef cancelBubbleValue = JSValueMakeBoolean(context, false);

    JSObjectSetProperty(context, eventObjectRef, jsEventBubblesStringRef, bubblesValue, kJSPropertyAttributeReadOnly, NULL);
    JSObjectSetProperty(context, eventObjectRef, jsEventTypeStringRef, eventStringRef, kJSPropertyAttributeReadOnly, NULL);
    JSObjectSetProperty(context, eventObjectRef, jsEventSourceStringRef, eventSourceRef, kJSPropertyAttributeReadOnly, NULL);

    //Error reporting
    if (event->reportError) {
      JSValueRef successValue = JSValueMakeBoolean(context, (event->errorCode == 0));
      JSValueRef codeValue = JSValueMakeNumber(context, (double)event->errorCode);

      JSObjectSetProperty(context, eventObjectRef, jsEventSuccessStringRef, successValue, kJSPropertyAttributeReadOnly, NULL);
      JSObjectSetProperty(context, eventObjectRef, jsEventErrorCodeStringRef, codeValue, kJSPropertyAttributeReadOnly, NULL);
    }

    if (event->errorMessageStringRef != NULL) {
      JSValueRef eventStringValueRef = JSValueMakeString(context, event->eventStringRef);
      JSObjectSetProperty(context, eventObjectRef, jsEventErrorMessageStringRef, eventStringValueRef, kJSPropertyAttributeReadOnly, NULL);
    }

    JSObjectSetProperty(context, eventObjectRef, jsEventCancelBubbleStringRef, cancelBubbleValue, kJSPropertyAttributeNone, NULL);

    NSDictionary *eventData = TiBindingTiValueToNSDictionary(context, eventObjectRef);
    [targetKrollObject resolvePromisesForEvent:event->eventString withObject:eventData];

    for (int i = 0; i < callbackCount; i++) {
      // Process event in context
      JSValueRef currentCallback = JSObjectGetPropertyAtIndex(context, callbacksObjectRef, i, NULL);
      if ((currentCallback == NULL) || (JSValueGetType(context, currentCallback) != kJSTypeObject)
          || !JSObjectIsFunction(context, (JSObjectRef)currentCallback)) {
        continue;
      }

      JSValueRef exception = NULL;
      JSObjectCallAsFunction(context, (JSObjectRef)currentCallback, (JSObjectRef)eventTargetRef, 1, (JSValueRef *)&eventObjectRef, &exception);
      if (exception != NULL) {
        [TiExceptionHandler.defaultExceptionHandler reportScriptError:exception inKrollContext:runloop];
      }

      // Note cancel bubble
      cancelBubbleValue = JSObjectGetProperty(context, eventObjectRef, jsEventCancelBubbleStringRef, NULL);
      if (JSValueToBoolean(context, cancelBubbleValue)) {
        event->cancelBubble = true; //Because we only set true, not read nor set false, there's no race condition?
      }
    }
  }

  int pendingEvents = OSAtomicDecrement32Barrier(&event->pendingEvents);
  if (pendingEvents > 0) {
    //Only the last event process gets to do propagation.
    return;
  }

  //Last one processing the event for this proxy, pass it on to the parent.
  TiProxy *newTarget = TiBindingEventNextBubbleTargetProxy(event, event->targetProxy, YES);
  if (event->targetProxy != newTarget) {
    [event->targetProxy release];
    event->targetProxy = [newTarget retain];
  }
  TiBindingEventFire(event);
  //See who gets it next.
}

void TiBindingEventDispose(TiBindingEvent event)
{
  [event->targetProxy release];
  [event->sourceProxy release];
  [event->eventString release];
  [event->payloadDictionary release];
  [event->errorMessageString release];
  if (event->eventStringRef != NULL) {
    JSStringRelease(event->eventStringRef);
  }
  if (event->errorMessageStringRef != NULL) {
    JSStringRelease(event->errorMessageStringRef);
  }
  if (event->eventObjectRef != NULL) {
    JSValueUnprotect(event->contextRef, event->eventObjectRef);
  }
  if (event->contextRef != NULL) {
    //TODO: Do we protect and release the context ref?
  }
  free(event);
}
