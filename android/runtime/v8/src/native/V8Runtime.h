/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-2016 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifndef V8_RUNTIME_H
#define V8_RUNTIME_H

#include <map>
#include <jni.h>
#include <unistd.h>
#include <v8.h>

using namespace v8;

namespace titanium {
class V8Runtime
{
public:
	static pid_t runtimeThreadId;
	static std::map<pid_t, Persistent<Context>> globalContext;
	static std::map<pid_t, Persistent<Object>> krollGlobalObject;
	static std::map<pid_t, Persistent<Object>> moduleObject;
	static std::map<pid_t, Persistent<Function>> runModuleFunction;

	static Isolate* v8_isolate;
	static Platform* platform;
	static std::map<pid_t, Isolate *> thread_isolateMap;
	static std::map<pid_t, Locker *> thread_lockerMap;

	static std::map<pid_t, jobject> javaInstance;

	static void logV8Exception(Local<Message> msg, Local<Value> data);
	static void bootstrap(Local<Context> globalContext, pid_t threadId);
	static void init(JNIEnv *env, jobject self, jobject debugger, jboolean DBG, jboolean profilerEnabled);
	static void nativeRunModule(JNIEnv *env, jobject self, jstring source, jstring filename, jobject activityProxy);
	static void nativeDispose(JNIEnv *env, jobject runtime);
	static bool debuggerEnabled;
	static bool DBG;
	static bool initialized;

	static Local<Context> GlobalContext();
};
}
;

#endif
