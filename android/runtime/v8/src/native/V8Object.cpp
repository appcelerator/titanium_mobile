/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2018 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#include <jni.h>
#include <stdio.h>
#include <unistd.h>
#include <v8.h>

#include "AndroidUtil.h"
#include "EventEmitter.h"
#include "JNIUtil.h"
#include "TypeConverter.h"
#include "Proxy.h"
#include "ProxyFactory.h"
#include "V8Runtime.h"
#include "V8Util.h"

#define TAG "V8Object"

using namespace titanium;
using namespace v8;

extern "C" {

JNIEXPORT void JNICALL
Java_org_appcelerator_kroll_runtime_v8_V8Object_nativeInitObject
	(JNIEnv *env, jclass clazz, jclass proxyClass, jobject proxyObject)
{
	pid_t threadId = gettid();
	Isolate* isolate = V8Runtime::thread_isolateMap[threadId];
	HandleScope scope(isolate);

	ProxyFactory::createV8Proxy(isolate, proxyClass, proxyObject);
}

JNIEXPORT void JNICALL
Java_org_appcelerator_kroll_runtime_v8_V8Object_nativeSetProperty
	(JNIEnv *env, jobject object, jlong ptr, jstring name, jobject value)
{
	pid_t threadId = gettid();
	Isolate* isolate = V8Runtime::thread_isolateMap[threadId];
	HandleScope scope(isolate);

	Local<Object> jsObject;
	if (ptr != 0) {
		titanium::Proxy* proxy = (titanium::Proxy*) ptr;
		jsObject = proxy->handle(isolate);
	} else {
		LOGE(TAG, "!!! Attempting to set a property on a Java object with no/deleted Proxy on C++ side! Attempting to revive it from Java object.");

		jobject proxySupportField = env->GetObjectField(object, JNIUtil::krollObjectProxySupportField);
		if (!proxySupportField) {
			return;
		}
		static jmethodID getMethodID = NULL;
		if (!getMethodID) {
			getMethodID = env->GetMethodID(env->FindClass("java/lang/ref/WeakReference"), "get", "()Ljava/lang/Object;");
		}
		jobject proxySupport = (jobject)env->CallObjectMethodA(proxySupportField, getMethodID, NULL);
		if (!proxySupport) {
			return;
		}
		jsObject = TypeConverter::javaObjectToJsValue(isolate, env, proxySupport).As<Object>();
	}
	Local<Context> context = isolate->GetCurrentContext();

	MaybeLocal<Value> maybeProperties = jsObject->Get(context, titanium::Proxy::propertiesSymbolMap[isolate].Get(isolate));
	if (!maybeProperties.IsEmpty()) {
		Local<Object> properties = maybeProperties.ToLocalChecked().As<Object>();
		Local<Value> jsName = TypeConverter::javaStringToJsString(isolate, env, name);
		Local<Value> jsValue = TypeConverter::javaObjectToJsValue(isolate, env, value);
		jsObject->SetAccessor(context, jsName->ToString(context).FromMaybe(String::Empty(isolate)), titanium::Proxy::getProperty, titanium::Proxy::onPropertyChanged);
		properties->Set(context, jsName, jsValue);
	}
}


JNIEXPORT jboolean JNICALL
Java_org_appcelerator_kroll_runtime_v8_V8Object_nativeFireEvent
	(JNIEnv *env, jobject jEmitter, jlong ptr, jobject jsource, jlong sourcePtr, jstring event, jobject data, jboolean bubble, jboolean reportSuccess, jint code, jstring errorMessage)
{
	pid_t threadId = gettid();
	Isolate* isolate = V8Runtime::thread_isolateMap[threadId];
	if (isolate == NULL) {
		LOGE(TAG, "nativeFireEvent. isolate is NULL");
		return JNI_FALSE;
	}
	HandleScope scope(isolate);

	Local<Value> jsEvent = TypeConverter::javaStringToJsString(isolate, env, event);

#ifdef TI_DEBUG
	v8::String::Utf8Value eventName(isolate, jsEvent);
	LOGV(TAG, "firing event \"%s\"", *eventName);
#endif

	Local<Object> emitter;
	if (ptr != 0) {
		titanium::Proxy* proxy = (titanium::Proxy*) ptr;
		emitter = proxy->handle(isolate);
	} else {
		emitter = TypeConverter::javaObjectToJsValue(isolate, env, jEmitter).As<Object>();
	}

	Local<String> symbol = EventEmitter::emitSymbol[isolate].Get(isolate);
	if (emitter.IsEmpty() || symbol.IsEmpty()) {
		return JNI_FALSE;
	}

	Local<Context> context = isolate->GetCurrentContext();
	MaybeLocal<Value> fireEventValue = emitter->Get(context, symbol);
	if (fireEventValue.IsEmpty() || !fireEventValue.ToLocalChecked()->IsFunction()) {
		return JNI_FALSE;
	}

	Local<Object> source;
	if ((jsource == NULL) || (jsource == jEmitter)) {
		source = emitter;
	} else if (sourcePtr != 0) {
		titanium::Proxy* proxy = (titanium::Proxy*) sourcePtr;
		source = proxy->handle(isolate);
	} else {
		source = TypeConverter::javaObjectToJsValue(isolate, env, jsource).As<Object>();
	}

	Local<Function> fireEvent = fireEventValue.ToLocalChecked().As<Function>();

	Local<Object> jsData = TypeConverter::javaHashMapToJsValue(isolate, env, data);

	jsData->Set(context, NEW_SYMBOL(isolate, "bubbles"), TypeConverter::javaBooleanToJsBoolean(isolate, bubble));
	jsData->Set(context, NEW_SYMBOL(isolate, "source"), source);

	if (reportSuccess || code != 0) {
		jsData->Set(context, NEW_SYMBOL(isolate, "success"), TypeConverter::javaBooleanToJsBoolean(isolate, code == 0));
		jsData->Set(context, NEW_SYMBOL(isolate, "code"), TypeConverter::javaIntToJsNumber(isolate, code));
	}

	if (errorMessage != NULL) {
		jsData->Set(context, NEW_SYMBOL(isolate, "error"), TypeConverter::javaStringToJsString(isolate, env, errorMessage));
	}

	TryCatch tryCatch(isolate);
	Local<Value> args[] = { jsEvent, jsData };
	MaybeLocal<Value> result = fireEvent->Call(context, emitter, 2, args);

	if (tryCatch.HasCaught()) {
		V8Util::openJSErrorDialog(isolate, tryCatch);
		V8Util::reportException(isolate, tryCatch);
		return JNI_FALSE;
	}

	return result.FromMaybe(False(isolate).As<Value>())->IsTrue() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobject JNICALL
Java_org_appcelerator_kroll_runtime_v8_V8Object_nativeCallProperty
	(JNIEnv* env, jobject javaObject, jlong ptr, jstring propertyName, jobjectArray args)
{
	pid_t threadId = gettid();
	Isolate* isolate = V8Runtime::thread_isolateMap[threadId];
	HandleScope scope(isolate);

	Local<Value> jsPropertyName = TypeConverter::javaStringToJsString(isolate, env, propertyName);

	Local<Object> jsObject;
	if (ptr != 0) {
		titanium::Proxy* proxy = (titanium::Proxy*) ptr;
		jsObject = proxy->handle(isolate);
	} else {
		LOGE(TAG, "!!! Attempting to call a property on a Java object with no/deleted Proxy on C++ side! Attempting to revive it from Java object.");
		jobject proxySupportField = env->GetObjectField(javaObject, JNIUtil::krollObjectProxySupportField);
		if (!proxySupportField) {
			return JNIUtil::undefinedObject;
		}
		static jmethodID getMethodID = NULL;
		if (!getMethodID) {
			getMethodID = env->GetMethodID(env->FindClass("java/lang/ref/WeakReference"), "get", "()Ljava/lang/Object;");
		}
		jobject proxySupport = (jobject)env->CallObjectMethodA(proxySupportField, getMethodID, NULL);
		if (proxySupport) {
			jsObject = TypeConverter::javaObjectToJsValue(isolate, env, proxySupport).As<Object>();
		}
	}

	if (jsObject.IsEmpty()) {
		LOGW(TAG, "Unable to get the JSObject representing this Java object, returning undefined.");
		return JNIUtil::undefinedObject;
	}

	Local<Context> context = isolate->GetCurrentContext();

	MaybeLocal<Value> property = jsObject->Get(context, jsPropertyName);
	if (property.IsEmpty() || !property.ToLocalChecked()->IsFunction()) {
		return JNIUtil::undefinedObject;
	}

	int argc = 0;
	Local<Value>* argv = NULL;
	if (args) {
		argv = TypeConverter::javaObjectArrayToJsArguments(isolate, args, &argc);
	}

	TryCatch tryCatch(isolate);
	Local<Function> function = property.ToLocalChecked().As<Function>();
	MaybeLocal<Value> returnValue = function->Call(context, jsObject, argc, argv);

	if (argv) {
		delete[] argv;
	}

	if (tryCatch.HasCaught()) {
		V8Util::openJSErrorDialog(isolate, tryCatch);
		V8Util::reportException(isolate, tryCatch);
	} // if exception, object should be empty handle...so returns undefined
	if (returnValue.IsEmpty()) {
		return JNIUtil::undefinedObject;
	}

	bool isNew;
	return TypeConverter::jsValueToJavaObject(isolate, env, returnValue.ToLocalChecked(), &isNew);
}

JNIEXPORT jboolean JNICALL
Java_org_appcelerator_kroll_runtime_v8_V8Object_nativeRelease
	(JNIEnv *env, jclass clazz, jlong refPointer)
{
	LOGD(TAG, "V8Object::nativeRelease");
	pid_t threadId = gettid();
	Isolate* isolate = V8Runtime::thread_isolateMap[threadId];
	HandleScope scope(isolate);

	if (refPointer) {
		// FIXME What's the right way to cast the long long int as a pointer?
		// Maybe we can move to more correct smart pointer usage?
		// http://stackoverflow.com/questions/26375215/c-shared-ptr-and-java-native-object-ownership
		titanium::Proxy* proxy = (titanium::Proxy*) refPointer;
		if (proxy && proxy->isDetached()) {
			// if the proxy is detached, delete it
			// This means we have already received notification from V8 that the JS side of the proxy can be deleted
			LOGD(TAG, "deleting titanium::Proxy with pointer value: %p", refPointer);
			delete proxy;
			return JNI_TRUE;
		}
	}

	return JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_org_appcelerator_kroll_runtime_v8_V8Object_nativeSetWindow
	(JNIEnv *env, jobject javaKrollWindow, jlong ptr, jobject javaWindow)
{
	pid_t threadId = gettid();
	Isolate* isolate = V8Runtime::thread_isolateMap[threadId];
	HandleScope scope(isolate);
	Local<Context> context = isolate->GetCurrentContext();

	Local<Object> jsKrollWindow;
	if (ptr != 0) {
		titanium::Proxy* proxy = (titanium::Proxy*) ptr;
		jsKrollWindow = proxy->handle(isolate);
	} else {
		jsKrollWindow = TypeConverter::javaObjectToJsValue(isolate, env, javaKrollWindow).As<Object>();
	}

	MaybeLocal<Value> setWindowValue = jsKrollWindow->Get(context, STRING_NEW(isolate, "setWindow"));
	if (setWindowValue.IsEmpty() || !setWindowValue.ToLocalChecked()->IsFunction()) {
		return;
	}

	Local<Function> setWindow = setWindowValue.ToLocalChecked().As<Function>();

	Local<Value> jsWindow = TypeConverter::javaObjectToJsValue(isolate, env, javaWindow);

	TryCatch tryCatch(isolate);
	if (!jsWindow->IsNull()) {
		Local<Value> args[] = { jsWindow };
		setWindow->Call(context, jsKrollWindow, 1, args);
	}

	if (tryCatch.HasCaught()) {
		V8Util::openJSErrorDialog(isolate, tryCatch);
		V8Util::reportException(isolate, tryCatch);
	}
}

} // extern "C"
