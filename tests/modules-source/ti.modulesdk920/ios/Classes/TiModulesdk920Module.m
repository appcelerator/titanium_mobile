/**
 * TiModuleSdk920
 *
 * Created by Axway
 * Copyright (c) 2020 Axway. All rights reserved.
 */

#import "TiModulesdk920Module.h"
#import "TiBase.h"
#import "TiHost.h"
#import "TiUtils.h"

@implementation TiModulesdk920Module

#pragma mark Internal

- (id)moduleGUID
{
  return @"c3d55776-adf1-4b31-a1b5-d71efed7d36f";
}

- (NSString *)moduleId
{
  return @"ti.modulesdk920";
}

#pragma mark Lifecycle

- (void)startup
{
  [super startup];
  _wasModuleInitialized = YES;
}

#pragma Public APIs

- (id)wasModuleInitialized
{
  return NUMBOOL(_wasModuleInitialized);
}

- (id)booleanValue
{
  return NUMBOOL(_boolValue);
}

- (void)setBooleanValue:(id)value
{
  ENSURE_TYPE(value, NSNumber);
  _boolValue = [TiUtils boolValue:value];
}

- (id)booleanArray
{
  return _boolArray;
}

- (void)setBooleanArray:(id)value
{
  ENSURE_TYPE_OR_NIL(value, NSArray);
  _boolArray = value;
}

- (id)intValue
{
  return NUMINT(_intValue);
}

- (void)setIntValue:(id)value
{
  ENSURE_TYPE(value, NSNumber);
  _intValue = [TiUtils intValue:value];
}

- (id)intArray
{
  return _intArray;
}

- (void)setIntArray:(id)value
{
  ENSURE_TYPE_OR_NIL(value, NSArray);
  _intArray = value;
}

- (id)longValue
{
  return NUMLONG(_longValue);
}

- (void)setLongValue:(id)value
{
  ENSURE_TYPE(value, NSNumber);
  _longValue = ((NSNumber *)value).longValue;
}

- (id)longArray
{
  return _longArray;
}

- (void)setLongArray:(id)value
{
  ENSURE_TYPE_OR_NIL(value, NSArray);
  _longArray = value;
}

- (id)floatValue
{
  return NUMFLOAT(_floatValue);
}

- (void)setFloatValue:(id)value
{
  ENSURE_TYPE(value, NSNumber);
  _floatValue = [TiUtils floatValue:value];
}

- (id)floatArray
{
  return _floatArray;
}

- (void)setFloatArray:(id)value
{
  ENSURE_TYPE_OR_NIL(value, NSArray);
  _floatArray = value;
}

- (id)doubleValue
{
  return NUMDOUBLE(_doubleValue);
}

- (void)setDoubleValue:(id)value
{
  ENSURE_TYPE(value, NSNumber);
  _doubleValue = [TiUtils doubleValue:value];
}

- (id)doubleArray
{
  return _doubleArray;
}

- (void)setDoubleArray:(id)value
{
  ENSURE_TYPE_OR_NIL(value, NSArray);
  _doubleArray = value;
}

- (id)stringValue
{
  return _stringValue;
}

- (void)setStringValue:(id)value
{
  ENSURE_TYPE_OR_NIL(value, NSString);
  _stringValue = value;
}

- (id)stringArray
{
  return _stringArray;
}

- (void)setStringArray:(id)value
{
  ENSURE_TYPE_OR_NIL(value, NSArray);
  _stringArray = value;
}

- (id)dateValue
{
  return _dateValue;
}

- (void)setDateValue:(id)value
{
  ENSURE_TYPE_OR_NIL(value, NSDate);
  _dateValue = value;
}

- (id)dateArray
{
  return _dateArray;
}

- (void)setDateArray:(id)value
{
  ENSURE_TYPE_OR_NIL(value, NSArray);
  _dateArray = value;
}

- (id)dictionaryValue
{
  return _dictionaryValue;
}

- (void)setDictionaryValue:(id)value
{
  ENSURE_TYPE_OR_NIL(value, NSDictionary);
  _dictionaryValue = value;
}

- (id)dictionaryArray
{
  return _dictionaryArray;
}

- (void)setDictionaryArray:(id)value
{
  ENSURE_TYPE_OR_NIL(value, NSArray);
  _dictionaryArray = value;
}

- (id)callback
{
  return _callback;
}

- (void)setCallback:(id)value
{
  ENSURE_TYPE(value, KrollCallback);
  _callback = value;
}

- (void)invokeCallbackAsync:(id)args
{
  ENSURE_TYPE_OR_NIL(args, NSArray);
  if (_callback) {
    [_callback callAsync:args thisObject:self];
  }
}

- (id)invokeCallbackSync:(id)args
{
  ENSURE_TYPE_OR_NIL(args, NSArray);
  if (_callback) {
    return [_callback call:args thisObject:self];
  }
  return nil;
}

#pragma mark Constants

MAKE_SYSTEM_PROP(INT_VALUE_1, 1);
MAKE_SYSTEM_STR(HELLO_WORLD, @"Hello World");

@end
