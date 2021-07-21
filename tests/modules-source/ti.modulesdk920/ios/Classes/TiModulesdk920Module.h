/**
 * TiModuleSdk920
 *
 * Created by Axway
 * Copyright (c) 2020 Axway. All rights reserved.
 */

#import "TiModule.h"

@interface TiModulesdk920Module : TiModule {
  @private
  BOOL _wasModuleInitialized;
  BOOL _boolValue;
  NSArray *_boolArray;
  int _intValue;
  NSArray *_intArray;
  long _longValue;
  NSArray *_longArray;
  float _floatValue;
  NSArray *_floatArray;
  double _doubleValue;
  NSArray *_doubleArray;
  NSString *_stringValue;
  NSArray *_stringArray;
  NSDate *_dateValue;
  NSArray *_dateArray;
  NSDictionary *_dictionaryValue;
  NSArray *_dictionaryArray;
  KrollCallback *_callback;
}

- (void)invokeCallbackAsync:(id)args;
- (id)invokeCallbackSync:(id)args;

@end
