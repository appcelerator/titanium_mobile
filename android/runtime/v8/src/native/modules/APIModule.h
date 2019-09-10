/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-2016 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#ifndef API_MODULE_H
#define API_MODULE_H

#include <map>
#include <v8.h>

namespace titanium {
using namespace v8;

class APIModule
{
public:
	static void Initialize(Local<Object> target, Local<Context> context);
	static void Dispose(Isolate* isolate);

	static void logDebug(const FunctionCallbackInfo<Value>& args);
	static void logInfo(const FunctionCallbackInfo<Value>& args);
	static void logWarn(const FunctionCallbackInfo<Value>& args);
	static void logError(const FunctionCallbackInfo<Value>& args);
	static void logTrace(const FunctionCallbackInfo<Value>& args);
	static void logNotice(const FunctionCallbackInfo<Value>& args);
	static void logCritical(const FunctionCallbackInfo<Value>& args);
	static void logFatal(const FunctionCallbackInfo<Value>& args);
	static void log(const FunctionCallbackInfo<Value>& args);

	// Only used by debugger for terminating application.
	static void terminate(const FunctionCallbackInfo<Value>& args);

	// Schedule a debugger break next time JavaScript code runs.
	static void debugBreak(const FunctionCallbackInfo<Value>& args);

private:
	static void logInternal(int logLevel, const char *messageTag, const char *message);
	static std::map<Isolate *, Persistent<FunctionTemplate>> constructorTemplate;
	static Local<String> combineLogMessages(const FunctionCallbackInfo<Value>& args, int startIndex=0);
	static void getApiName(const FunctionCallbackInfo<Value>& args);
	static void getter_apiName(Local<Name> name, const PropertyCallbackInfo<Value>& args);
	static void getter_undefined(Local<Name> name, const PropertyCallbackInfo<Value>& args);
	static void undefined(const FunctionCallbackInfo<Value>& args);
};
}

#endif
