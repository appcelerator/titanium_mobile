/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-2016 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#include <android/log.h>
#include <v8.h>
#include <cstring>
#include <signal.h>
#include <unistd.h>

#include "AndroidUtil.h"
#include "APIModule.h"
#include "JNIUtil.h"
#include "V8Runtime.h"
#include "V8Util.h"
#include "org.appcelerator.kroll.KrollModule.h"
#include "JSDebugger.h"

namespace titanium {

using namespace v8;

#define LOG_LEVEL_TRACE 1
#define LOG_LEVEL_DEBUG 2
#define LOG_LEVEL_INFO 3
#define LOG_LEVEL_NOTICE 4
#define LOG_LEVEL_WARN 5
#define LOG_LEVEL_ERROR 6
#define LOG_LEVEL_CRITICAL 7
#define LOG_LEVEL_FATAL 8

#define LCAT "TiAPI"

std::map<Isolate *, Persistent<FunctionTemplate>> APIModule::constructorTemplate;


void APIModule::Initialize(Local<Object> target, Local<Context> context)
{
	Isolate* isolate = context->GetIsolate();
	HandleScope scope(isolate);
	Local<FunctionTemplate> constructor = FunctionTemplate::New(isolate);
	constructor->SetClassName(NEW_SYMBOL(isolate, "API"));
	constructorTemplate[isolate].Reset(isolate, constructor);

	// Hook methods to the API prototype, notice these aren't hooked to API
	// itself, instead we return a singleton of an API instance and export it
	// as Ti.API
	// Not sure why we then hook apiName as instance proprty, since
	// the difference is made moot by the singleton!
	SetProtoMethod(isolate, constructor, "debug", logDebug);
	SetProtoMethod(isolate, constructor, "info", logInfo);
	SetProtoMethod(isolate, constructor, "warn", logWarn);
	SetProtoMethod(isolate, constructor, "error", logError);
	SetProtoMethod(isolate, constructor, "trace", logTrace);
	SetProtoMethod(isolate, constructor, "notice", logNotice);
	SetProtoMethod(isolate, constructor, "critical", logCritical);
	SetProtoMethod(isolate, constructor, "fatal", logFatal);
	SetProtoMethod(isolate, constructor, "log", log);
	SetProtoMethod(isolate, constructor, "getApiName", APIModule::getApiName);

	// these are documented but non-functional
	SetProtoMethod(isolate, constructor, "getBubbleParent", APIModule::undefined);
	SetProtoMethod(isolate, constructor, "getLifecycleContainer", APIModule::undefined);
	SetProtoMethod(isolate, constructor, "setBubbleParent", APIModule::undefined);
	SetProtoMethod(isolate, constructor, "setLifecycleContainer", APIModule::undefined);

	// Add an "apiName" property to instances, which delegates to APIModule::getter_apiName
	// TODO Use a constant here?
	Local<ObjectTemplate> instanceTemplate = constructor->InstanceTemplate();
	instanceTemplate->SetAccessor(NEW_SYMBOL(isolate, "apiName"), APIModule::getter_apiName);

	instanceTemplate->SetAccessor(NEW_SYMBOL(isolate, "bubbleParent"), APIModule::getter_undefined);
	instanceTemplate->SetAccessor(NEW_SYMBOL(isolate, "lifecycleContainer"), APIModule::getter_undefined);

	// Expose a method for terminating the application for the debugger.
	// Debugger will send an evaluation request calling this method
	// when it wants the application to terminate immediately.
	if (V8Runtime::debuggerEnabled) {
		SetProtoMethod(isolate, constructor, "terminate", terminate);
		SetProtoMethod(isolate, constructor, "debugBreak", debugBreak);
	}
	// Make API extend from KrollModule
	constructor->Inherit(KrollModule::getProxyTemplate(context));

	// export an instance of API as "API" (basically make a static singleton)
	v8::TryCatch tryCatch(isolate);
	Local<Function> constructorFunction;
	MaybeLocal<Function> maybeConstructor = constructor->GetFunction(context);
	if (!maybeConstructor.ToLocal(&constructorFunction)) {
		V8Util::fatalException(isolate, tryCatch);
		return;
	}
	MaybeLocal<Object> maybeInstance = constructorFunction->NewInstance(context);
	Local<Object> moduleInstance;
	if (!maybeInstance.ToLocal(&moduleInstance)) {
		V8Util::fatalException(isolate, tryCatch);
		return;
	}
	target->Set(NEW_SYMBOL(isolate, "API"), moduleInstance);
}

void APIModule::logDebug(const FunctionCallbackInfo<Value>& args)
{
	Isolate* isolate = args.GetIsolate();
	HandleScope scope(isolate);
	v8::String::Utf8Value message(isolate, APIModule::combineLogMessages(args));
	APIModule::logInternal(LOG_LEVEL_DEBUG, LCAT, *message);
}

void APIModule::logInfo(const FunctionCallbackInfo<Value>& args)
{
	Isolate* isolate = args.GetIsolate();
	HandleScope scope(isolate);
	v8::String::Utf8Value message(isolate, APIModule::combineLogMessages(args));
	APIModule::logInternal(LOG_LEVEL_INFO, LCAT, *message);
}

void APIModule::logWarn(const FunctionCallbackInfo<Value>& args)
{
	Isolate* isolate = args.GetIsolate();
	HandleScope scope(isolate);
	v8::String::Utf8Value message(isolate, APIModule::combineLogMessages(args));
	APIModule::logInternal(LOG_LEVEL_WARN, LCAT, *message);
}

void APIModule::logError(const FunctionCallbackInfo<Value>& args)
{
	Isolate* isolate = args.GetIsolate();
	HandleScope scope(isolate);
	v8::String::Utf8Value message(isolate, APIModule::combineLogMessages(args));
	APIModule::logInternal(LOG_LEVEL_ERROR, LCAT, *message);
}

void APIModule::logTrace(const FunctionCallbackInfo<Value>& args)
{
	Isolate* isolate = args.GetIsolate();
	HandleScope scope(isolate);
	v8::String::Utf8Value message(isolate, APIModule::combineLogMessages(args));
	APIModule::logInternal(LOG_LEVEL_TRACE, LCAT, *message);
}

void APIModule::logNotice(const FunctionCallbackInfo<Value>& args)
{
	Isolate* isolate = args.GetIsolate();
	HandleScope scope(isolate);
	v8::String::Utf8Value message(isolate, APIModule::combineLogMessages(args));
	APIModule::logInternal(LOG_LEVEL_NOTICE, LCAT, *message);
}

void APIModule::logCritical(const FunctionCallbackInfo<Value>& args)
{
	Isolate* isolate = args.GetIsolate();
	HandleScope scope(isolate);
	v8::String::Utf8Value message(isolate, APIModule::combineLogMessages(args));
	APIModule::logInternal(LOG_LEVEL_CRITICAL, LCAT, *message);
}

void APIModule::logFatal(const FunctionCallbackInfo<Value>& args)
{
	Isolate* isolate = args.GetIsolate();
	HandleScope scope(isolate);
	v8::String::Utf8Value message(isolate, args[0]);
	APIModule::logInternal(LOG_LEVEL_FATAL, LCAT, *message);
}

// Seems to be for internal use only, should be OK.
static void debugLog(int logLevel, const char* message)
{
	JNIEnv* env = JNIUtil::getJNIEnv();
	if (env == NULL) {
		LOGE(LCAT, "Failed to get JNI environment.");
		return;
	}

	jstring javaMessage = env->NewStringUTF(message);
	env->CallStaticVoidMethod(JNIUtil::krollLoggingClass,
	                          JNIUtil::krollLoggingLogWithDefaultLoggerMethod,
	                          logLevel,
	                          javaMessage);
	env->DeleteLocalRef(javaMessage);
}

void APIModule::logInternal(int logLevel, const char *messageTag, const char *message)
{

	if (V8Runtime::debuggerEnabled) {
		debugLog(logLevel, message);
		return;
	}

	if (logLevel == LOG_LEVEL_TRACE) {
		__android_log_write(ANDROID_LOG_VERBOSE, messageTag, message);
	} else if (logLevel < LOG_LEVEL_INFO) {
		if (!V8Runtime::DBG) {
			return;
		}
		__android_log_write(ANDROID_LOG_DEBUG, messageTag, message);
	} else if (logLevel < LOG_LEVEL_WARN) {
		__android_log_write(ANDROID_LOG_INFO, messageTag, message);
	} else if (logLevel == LOG_LEVEL_WARN) {
		__android_log_write(ANDROID_LOG_WARN, messageTag, message);
	} else {
		__android_log_write(ANDROID_LOG_ERROR, messageTag, message);
	}
}

void APIModule::log(const FunctionCallbackInfo<Value>& args)
{
	Isolate* isolate = args.GetIsolate();
	HandleScope scope(isolate);
	if (args.Length()  == 1) {
		v8::String::Utf8Value message(isolate, args[0]);
		APIModule::logInternal(LOG_LEVEL_INFO, LCAT, *message);
	} else {
		v8::String::Utf8Value level(isolate, args[0]);
		v8::String::Utf8Value message(isolate, APIModule::combineLogMessages(args, 1));

		if (strcasecmp(*level, "TRACE") == 0) {
			APIModule::logInternal(LOG_LEVEL_TRACE, LCAT, *message);
		} else if (strcasecmp(*level, "DEBUG") == 0) {
			APIModule::logInternal(LOG_LEVEL_DEBUG, LCAT, *message);
		} else if (strcasecmp(*level, "INFO") == 0) {
			APIModule::logInternal(LOG_LEVEL_INFO, LCAT, *message);
		} else if (strcasecmp(*level, "NOTICE") == 0) {
			APIModule::logInternal(LOG_LEVEL_NOTICE, LCAT, *message);
		} else if (strcasecmp(*level, "WARN") == 0) {
			APIModule::logInternal(LOG_LEVEL_WARN, LCAT, *message);
		} else if (strcasecmp(*level, "ERROR") == 0) {
			APIModule::logInternal(LOG_LEVEL_ERROR, LCAT, *message);
		} else if (strcasecmp(*level, "CRITICAL") == 0) {
			APIModule::logInternal(LOG_LEVEL_CRITICAL, LCAT, *message);
		} else if (strcasecmp(*level, "FATAL") == 0) {
			APIModule::logInternal(LOG_LEVEL_FATAL, LCAT, *message);
		} else {
			int size = strlen(*level) + strlen(*message) + 4;
			char *fmessage = new char[size];
			snprintf(fmessage, size, "[%s] %s", *level, *message);
			APIModule::logInternal(LOG_LEVEL_INFO, LCAT, fmessage);
			delete [] fmessage;
		}
	}
}

Local<String> APIModule::combineLogMessages(const FunctionCallbackInfo<Value>& args, int startIndex)
{
	// Unfortunately there is no really reasonable way to do this in a memory
	// and speed-efficient manner. Instead what we have is a series of string
	// object concatenations, which is a rough emulation of what the + op would
	// do in JS. Requiring the whitespace between arguments complicates matters
	// by introducing the " " token.
	Isolate* isolate = args.GetIsolate();
	Local<Context> context = isolate->GetCurrentContext();
	Local<String> space = NEW_SYMBOL(isolate, " ");
	Local<String> message = String::Empty(isolate);
	Local<String> empty = String::Empty(isolate);
	for (int i=startIndex; i < args.Length(); i++) {
		message = String::Concat(isolate, message, String::Concat(isolate, space, args[i]->ToString(context).FromMaybe(empty)));
	}
	return message;
}

void APIModule::getApiName(const FunctionCallbackInfo<Value>& args)
{
	args.GetReturnValue().Set(STRING_NEW(args.GetIsolate(), "Ti.API"));
}

void APIModule::getter_apiName(Local<Name> name, const PropertyCallbackInfo<Value>& args)
{
	args.GetReturnValue().Set(STRING_NEW(args.GetIsolate(), "Ti.API"));
}

void APIModule::getter_undefined(Local<Name> name, const PropertyCallbackInfo<Value>& args)
{
	args.GetReturnValue().Set(Undefined(args.GetIsolate()));
}

void APIModule::terminate(const FunctionCallbackInfo<Value>& args)
{
	kill(getpid(), 9);
}

void APIModule::debugBreak(const FunctionCallbackInfo<Value>& args)
{
	JSDebugger::debugBreak();
}

void APIModule::undefined(const FunctionCallbackInfo<Value>& args)
{
	args.GetReturnValue().Set(Undefined(args.GetIsolate()));
}

void APIModule::Dispose(Isolate* isolate)
{
	constructorTemplate[isolate].Reset();
}

}
