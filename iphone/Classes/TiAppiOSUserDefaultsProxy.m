/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2015 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_APPIOS

#import "TiAppiOSUserDefaultsProxy.h"
#import <TitaniumKit/TiUtils.h>

@implementation TiAppiOSUserDefaultsProxy {
  NSData *_defaultsNull;
}

- (void)dealloc
{
  TiThreadPerformOnMainThread(
      ^{
        [[NSNotificationCenter defaultCenter] removeObserver:self];
      },
      YES);
  self.defaultsObject = nil;
  RELEASE_TO_NIL(_defaultsNull);
  [super dealloc];
}

- (NSString *)apiName
{
  return @"Ti.App.iOS.UserDefaults";
}

- (void)_listenerAdded:(NSString *)type count:(int)count
{
  if (count == 1 && [type isEqual:@"change"]) {
    TiThreadPerformOnMainThread(
        ^{
          [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(NSUserDefaultsDidChange:) name:NSUserDefaultsDidChangeNotification object:nil];
        },
        YES);
  }
}

- (void)_listenerRemoved:(NSString *)type count:(int)count
{
  if (count == 0 && [type isEqual:@"change"]) {
    TiThreadPerformOnMainThread(
        ^{
          [[NSNotificationCenter defaultCenter] removeObserver:self name:NSUserDefaultsDidChangeNotification object:nil];
        },
        YES);
  }
}

- (void)_configure
{
  _defaultsNull = [[NSData alloc] initWithBytes:"NULL" length:4];
  [super _configure];
}

- (BOOL)propertyExists:(NSString *)key;
{
  if (![key isKindOfClass:[NSString class]])
    return NO;
  [self.defaultsObject synchronize];
  return ([self.defaultsObject objectForKey:key] != nil);
}

#define GETPROP                                                                \
  ENSURE_TYPE(args, NSArray);                                                  \
  NSString *key = [args objectAtIndex:0];                                      \
  id defaultValue = [args count] > 1 ? [args objectAtIndex:1] : [NSNull null]; \
  if (![self propertyExists:key])                                              \
    return defaultValue;

- (id)getBool:(id)args
{
  GETPROP
  return [NSNumber numberWithBool:[self.defaultsObject boolForKey:key]];
}

- (id)getDouble:(id)args
{
  GETPROP
  return [NSNumber numberWithDouble:[self.defaultsObject doubleForKey:key]];
}

- (id)getInt:(id)args
{
  GETPROP
  return NUMINTEGER([self.defaultsObject integerForKey:key]);
}

- (id)getString:(id)args
{
  GETPROP
  return [self.defaultsObject stringForKey:key];
}

- (id)getList:(id)args
{
  GETPROP
  NSArray *value = [self.defaultsObject arrayForKey:key];
  NSMutableArray *array = [[[NSMutableArray alloc] initWithCapacity:[value count]] autorelease];
  [(NSArray *)value enumerateObjectsUsingBlock:^(id obj, NSUInteger idx, BOOL *stop) {
    if ([obj isKindOfClass:[NSData class]] && [_defaultsNull isEqualToData:obj]) {
      obj = [NSNull null];
    }
    [array addObject:obj];
  }];
  return array;
}

- (id)getObject:(id)args
{
  GETPROP
  id theObject = [self.defaultsObject objectForKey:key];
  if ([theObject isKindOfClass:[NSData class]]) {
    return [NSKeyedUnarchiver unarchivedObjectOfClass:[NSData class] fromData:theObject error:nil];
  } else {
    return theObject;
  }
}

#define SETPROP                                                                              \
  ENSURE_TYPE(args, NSArray);                                                                \
  NSString *key = [args objectAtIndex:0];                                                    \
  id value = [args count] > 1 ? [args objectAtIndex:1] : nil;                                \
  if (value == nil || value == [NSNull null]) {                                              \
    [self.defaultsObject removeObjectForKey:key];                                            \
    [self.defaultsObject synchronize];                                                       \
    return;                                                                                  \
  }                                                                                          \
  if ([self propertyExists:key] && [[self.defaultsObject objectForKey:key] isEqual:value]) { \
    return;                                                                                  \
  }

- (void)setBool:(id)args
{
  SETPROP
      [self.defaultsObject setBool:[TiUtils boolValue:value]
                            forKey:key];
  [self.defaultsObject synchronize];
}

- (void)setDouble:(id)args
{
  SETPROP
      [self.defaultsObject setDouble:[TiUtils doubleValue:value]
                              forKey:key];
  [self.defaultsObject synchronize];
}

- (void)setInt:(id)args
{
  SETPROP
      [self.defaultsObject setInteger:[TiUtils intValue:value]
                               forKey:key];
  [self.defaultsObject synchronize];
}

- (void)setString:(id)args
{
  SETPROP
      [self.defaultsObject setObject:[TiUtils stringValue:value]
                              forKey:key];
  [self.defaultsObject synchronize];
}

- (void)setList:(id)args
{
  SETPROP
  if ([value isKindOfClass:[NSArray class]]) {
    NSMutableArray *array = [[[NSMutableArray alloc] initWithCapacity:[value count]] autorelease];
    [(NSArray *)value enumerateObjectsUsingBlock:^(id obj, NSUInteger idx, BOOL *stop) {
      if ([obj isKindOfClass:[NSNull class]]) {
        obj = _defaultsNull;
      }
      [array addObject:obj];
    }];
    value = array;
  }
  [self.defaultsObject setObject:value forKey:key];
  [self.defaultsObject synchronize];
}

- (void)setObject:(id)args
{
  SETPROP
  NSData *encoded = [NSKeyedArchiver archivedDataWithRootObject:value requiringSecureCoding:NO error:nil];
  [self.defaultsObject setObject:encoded forKey:key];
  [self.defaultsObject synchronize];
}

- (void)removeProperty:(id)args
{
  ENSURE_SINGLE_ARG(args, NSString);
  [self.defaultsObject removeObjectForKey:[TiUtils stringValue:args]];
  [self.defaultsObject synchronize];
}

- (void)removeAllProperties:(id)unused
{
  NSArray *keys = [[self.defaultsObject dictionaryRepresentation] allKeys];
  for (NSString *key in keys) {
    [self.defaultsObject removeObjectForKey:key];
  }
  [self.defaultsObject synchronize];
}

- (id)hasProperty:(id)args
{
  ENSURE_SINGLE_ARG(args, NSString);
  return NUMBOOL([self propertyExists:[TiUtils stringValue:args]]);
}

- (id)listProperties:(id)unused
{
  NSMutableArray *array = [NSMutableArray array];
  [array addObjectsFromArray:[[self.defaultsObject dictionaryRepresentation] allKeys]];
  return array;
}

- (void)NSUserDefaultsDidChange:(NSNotification *)note
{
  [self fireEvent:@"change" withObject:nil];
}

@end

#endif
