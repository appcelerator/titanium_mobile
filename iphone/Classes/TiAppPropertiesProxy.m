/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_APP

#import "TiAppPropertiesProxy.h"
#import <TitaniumKit/TiApp.h>
#import <TitaniumKit/TiUtils.h>

@implementation TiAppPropertiesProxy {
  NSData *_defaultsNull;
}

- (void)dealloc
{
  TiThreadPerformOnMainThread(
      ^{
        [[NSNotificationCenter defaultCenter] removeObserver:self];
      },
      YES);
  RELEASE_TO_NIL(defaultsObject);
  RELEASE_TO_NIL(_defaultsNull);
  [super dealloc];
}

- (NSString *)apiName
{
  return @"Ti.App.Properties";
}

- (void)_listenerAdded:(NSString *)type count:(int)count
{
  if (count == 1 && [type isEqual:@"change"]) {
    TiThreadPerformOnMainThread(
        ^{
          [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(NSUserDefaultsDidChange) name:NSUserDefaultsDidChangeNotification object:nil];
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
  defaultsObject = [[NSUserDefaults standardUserDefaults] retain];
  _defaultsNull = [[NSData alloc] initWithBytes:"NULL" length:4];
  [super _configure];
}

- (BOOL)propertyExists:(NSString *)key;
{
  if (![key isKindOfClass:[NSString class]])
    return NO;
  [defaultsObject synchronize];
  return ([defaultsObject objectForKey:key] != nil);
}

#define GETPROP                                                                \
  ENSURE_TYPE(args, NSArray);                                                  \
  NSString *key = [args objectAtIndex:0];                                      \
  id appProp = [[TiApp tiAppProperties] objectForKey:key];                     \
  if (appProp) {                                                               \
    return appProp;                                                            \
  }                                                                            \
  id defaultValue = [args count] > 1 ? [args objectAtIndex:1] : [NSNull null]; \
  if (![self propertyExists:key])                                              \
    return defaultValue;

- (id)getBool:(id)args
{
  GETPROP
  return [NSNumber numberWithBool:[defaultsObject boolForKey:key]];
}

- (id)getDouble:(id)args
{
  GETPROP
  return [NSNumber numberWithDouble:[defaultsObject doubleForKey:key]];
}

- (id)getInt:(id)args
{
  GETPROP
  return NUMINTEGER([defaultsObject integerForKey:key]);
}

- (id)getString:(id)args
{
  GETPROP
  return [defaultsObject stringForKey:key];
}

- (id)getList:(id)args
{
  GETPROP
  NSArray *value = [defaultsObject arrayForKey:key];
  NSMutableArray *array = [[[NSMutableArray alloc] initWithCapacity:[value count]] autorelease];

  [(NSArray *)value enumerateObjectsUsingBlock:^(id obj, NSUInteger idx, BOOL *stop) {
    id result = obj;
    if ([obj isKindOfClass:[NSData class]] && [_defaultsNull isEqualToData:obj]) {
      result = [NSNull null];
    } else if ([obj isKindOfClass:[NSDictionary class]]) {
      result = [self bridgedDictionaryFromDictionary:obj withType:[NSData class] toType:[NSNull class]];
    }
    [array addObject:result];
  }];
  return array;
}

- (id)getObject:(id)args
{
  GETPROP
  id theObject = [defaultsObject objectForKey:key];
  if ([theObject isKindOfClass:[NSData class]]) {
    return [NSKeyedUnarchiver unarchivedObjectOfClass:[NSData class] fromData:theObject error:nil];
  } else {
    return theObject;
  }
}

#define SETPROP                                                                         \
  ENSURE_TYPE(args, NSArray);                                                           \
  NSString *key = [args objectAtIndex:0];                                               \
  id appProp = [[TiApp tiAppProperties] objectForKey:key];                              \
  if (appProp) {                                                                        \
    DebugLog(@"[ERROR] Property \"%@\" already exist and cannot be overwritten", key);  \
    return;                                                                             \
  }                                                                                     \
  id value = [args count] > 1 ? [args objectAtIndex:1] : nil;                           \
  if (value == nil || value == [NSNull null]) {                                         \
    [defaultsObject removeObjectForKey:key];                                            \
    [defaultsObject synchronize];                                                       \
    return;                                                                             \
  }                                                                                     \
  if ([self propertyExists:key] && [[defaultsObject objectForKey:key] isEqual:value]) { \
    return;                                                                             \
  }

- (NSDictionary *)bridgedDictionaryFromDictionary:(NSDictionary *)dictionary withType:(Class)withType toType:(Class)toType
{
  NSMutableDictionary *result = [NSMutableDictionary dictionaryWithCapacity:[[dictionary allKeys] count]];

  for (NSUInteger i = 0; i < [[dictionary allKeys] count]; i++) {
    id key = [[dictionary allKeys] objectAtIndex:i];

    if ([[dictionary objectForKey:key] isKindOfClass:withType]) {

      if (withType == [NSData class]) {
        [result setObject:[NSNull null] forKey:key];
      } else if (withType == [NSNull class]) {
        [result setObject:_defaultsNull forKey:key];
      } else {
        NSLog(@"Unhandled bridge: %@", withType);
      }
    } else if ([[dictionary objectForKey:key] isKindOfClass:[NSDictionary class]]) {
      [result setObject:[self bridgedDictionaryFromDictionary:[dictionary objectForKey:key] withType:withType toType:toType] forKey:key];
    } else {
      [result setObject:[dictionary objectForKey:key] forKey:key];
    }
  }

  return result;
}

- (void)setBool:(id)args
{
  SETPROP
      [defaultsObject setBool:[TiUtils boolValue:value]
                       forKey:key];
  [defaultsObject synchronize];
}

- (void)setDouble:(id)args
{
  SETPROP
      [defaultsObject setDouble:[TiUtils doubleValue:value]
                         forKey:key];
  [defaultsObject synchronize];
}

- (void)setInt:(id)args
{
  SETPROP
      [defaultsObject setInteger:[TiUtils intValue:value]
                          forKey:key];
  [defaultsObject synchronize];
}

- (void)setString:(id)args
{
  SETPROP
      [defaultsObject setObject:[TiUtils stringValue:value]
                         forKey:key];
  [defaultsObject synchronize];
}

- (void)setList:(id)args
{
  SETPROP
  ENSURE_TYPE(value, NSArray);

  NSMutableArray *array = [[[NSMutableArray alloc] initWithCapacity:[(NSArray *)value count]] autorelease];
  [(NSArray *)value enumerateObjectsUsingBlock:^(id obj, NSUInteger idx, BOOL *stop) {
    if ([obj isKindOfClass:[NSNull class]]) {
      obj = _defaultsNull;
    } else if ([obj isKindOfClass:[NSDictionary class]]) {
      obj = [self bridgedDictionaryFromDictionary:obj withType:[NSNull class] toType:[NSData class]];
    }
    [array addObject:obj];
  }];
  value = array;
  [defaultsObject setObject:value forKey:key];
  [defaultsObject synchronize];
}

- (void)setObject:(id)args
{
  SETPROP
  NSData *encoded = [NSKeyedArchiver archivedDataWithRootObject:value requiringSecureCoding:NO error:nil];
  [defaultsObject setObject:encoded forKey:key];
  [defaultsObject synchronize];
}

- (void)removeProperty:(id)args
{
  ENSURE_SINGLE_ARG(args, NSString);
  if ([[TiApp tiAppProperties] objectForKey:args] != nil) {
    DebugLog(@"[ERROR] Cannot remove property \"%@\", it is read-only.", args);
    return;
  }
  [defaultsObject removeObjectForKey:[TiUtils stringValue:args]];
  [defaultsObject synchronize];
}

- (void)removeAllProperties:(id)unused
{
  NSArray *keys = [[defaultsObject dictionaryRepresentation] allKeys];
  for (NSString *key in keys) {
    [defaultsObject removeObjectForKey:key];
  }

  [defaultsObject synchronize];
}

- (id)hasProperty:(id)args
{
  ENSURE_SINGLE_ARG(args, NSString);
  BOOL inUserDefaults = [self propertyExists:[TiUtils stringValue:args]];
  BOOL inTiAppProperties = [[TiApp tiAppProperties] objectForKey:args] != nil;
  return NUMBOOL(inUserDefaults || inTiAppProperties);
}

- (id)listProperties:(id)unused
{
  NSMutableArray *array = [NSMutableArray array];
  [array addObjectsFromArray:[[defaultsObject dictionaryRepresentation] allKeys]];
  [array addObjectsFromArray:[[TiApp tiAppProperties] allKeys]];
  return array;
}

- (void)NSUserDefaultsDidChange
{
  [self fireEvent:@"change" withObject:nil];
}

@end

#endif
