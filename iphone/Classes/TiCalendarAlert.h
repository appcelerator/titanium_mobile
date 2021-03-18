/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_CALENDAR
@import JavaScriptCore;
@import TitaniumKit.ObjcProxy;

@class EKAlarm; // forward declare
@class CalendarModule;

@protocol TiCalendarAlertExports <JSExport>

// properties (and accessors)
@property (nonatomic, strong) NSDate *absoluteDate;
@property double relativeOffset;

@end

@interface TiCalendarAlert : ObjcProxy <TiCalendarAlertExports> {

  @private
  CalendarModule *module;
  EKAlarm *alert;
}

/**
  @deprecated Only here for backwards compatibility with SDK < 8.1.0. Use `initWithAlert:module:` instead.
  */
- (id)_initWithPageContext:(id<TiEvaluator>)context
                     alert:(EKAlarm *)alert_
                    module:(CalendarModule *)module_ __attribute__((deprecated));

- (id)initWithAlert:(EKAlarm *)alert_
             module:(CalendarModule *)module_;

- (EKAlarm *)alert;
@end

#endif
