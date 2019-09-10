/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-2018 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#include <jni.h>
#include <v8.h>

#include "JNIUtil.h"
#include "Proxy.h"
#include "TypeConverter.h"
#include "V8Runtime.h"
#include "V8Util.h"

#define TAG "V8Function"

using namespace titanium;
using namespace v8;

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     org_appcelerator_kroll_runtime_v8_V8Function
 * Method:    nativeInvoke
 * Signature: (JJ[Ljava/lang/Object)V
 */
JNIEXPORT jobject JNICALL Java_org_appcelerator_kroll_runtime_v8_V8Function_nativeInvoke
	(JNIEnv *env, jobject caller, jlong thisPointer, jlong functionPointer, jobjectArray functionArguments)
{
	pid_t threadId = gettid();
	Isolate* isolate = V8Runtime::thread_isolateMap[threadId];
	if (isolate == NULL) {
		LOGE(TAG, "!!!Received a bad thread id (%i). Returning undefined.", threadId);
		return JNIUtil::undefinedObject;
	}
	HandleScope scope(isolate);

	// construct this from pointer
	if (thisPointer == 0) {
		LOGE(TAG, "!!!Received a bad pointer to the Proxy for 'this' in V8Function.nativeInvoke. Proxy may have been destroyed already. Returning undefined.");
		return JNIUtil::undefinedObject;
	}
	titanium::Proxy* proxy = (titanium::Proxy*) thisPointer;
	Local<Object> thisObject = proxy->handle(isolate);

	// construct function from "pointer" - we used to use pointers to Persistent to re-construct Functions
	// But that was a _BAD_ idea because V8 moves handles around as GC runs, resulting in the stored memory address being invalid
	// So now we basically use a global map with an incrementing key to store the functions, where the "pointer" is the indx the function is stored under in the map.
	auto it = TypeConverter::functions.find(functionPointer);
	if (it == TypeConverter::functions.end()) {
		LOGE(TAG, "!!!Received a bad 'pointer' to the V8Function, unable to find an entry for it. Returning undefined.");
		return JNIUtil::undefinedObject;
	}
	Persistent<Function, CopyablePersistentTraits<Function>> persistentJSFunction = TypeConverter::functions.at(functionPointer);
	Local<Function> jsFunction = persistentJSFunction.Get(isolate);

	// create function arguments
	int length;
	v8::Local<v8::Value>* jsFunctionArguments =
		TypeConverter::javaObjectArrayToJsArguments(isolate, env, functionArguments, &length);

	// call into the JS function with the provided argument
	TryCatch tryCatch(isolate);
	MaybeLocal<Value> object = jsFunction->Call(isolate->GetCurrentContext(), thisObject, length, jsFunctionArguments);

	// make sure to delete the arguments since the arguments array is built on the heap
	if (jsFunctionArguments) {
		delete jsFunctionArguments;
	}

	if (tryCatch.HasCaught()) {
		V8Util::openJSErrorDialog(isolate, tryCatch);
		V8Util::reportException(isolate, tryCatch);
	} // if exception, object should be empty handle...so returns undefined
	if (object.IsEmpty()) {
		return JNIUtil::undefinedObject;
	}

	bool isNew;
	return TypeConverter::jsValueToJavaObject(isolate, env, object.ToLocalChecked(), &isNew);
}

JNIEXPORT void JNICALL
Java_org_appcelerator_kroll_runtime_v8_V8Function_nativeRelease
	(JNIEnv *env, jclass clazz, jlong ptr)
{
	// Release the JS function so it can be collected.
	// We guard against "bad" pointers by searching by index before releasing
	auto it = TypeConverter::functions.find(ptr);
	if (it != TypeConverter::functions.end()) {
		auto jsFunction = it->second;
		jsFunction.Reset();
		TypeConverter::functions.erase(it);
	}
}

#ifdef __cplusplus
}
#endif
