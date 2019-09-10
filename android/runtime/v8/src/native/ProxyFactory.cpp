/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-2017 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#include "ProxyFactory.h"

#include <stdio.h>
#include <v8.h>

#include "AndroidUtil.h"
#include "JavaObject.h"
#include "JNIUtil.h"
#include "JSException.h"
#include "KrollBindings.h"
#include "Proxy.h"
#include "TypeConverter.h"
#include "V8Util.h"

#define TAG "ProxyFactory"

using namespace v8;

namespace titanium {

#define LOG_JNIENV_ERROR(msgMore) \
	LOGE(TAG, "Unable to find class %s", msgMore);

Local<Object> ProxyFactory::createV8Proxy(v8::Isolate* isolate, jclass javaClass, jobject javaProxy)
{
	return ProxyFactory::createV8Proxy(isolate, ProxyFactory::getJavaClassName(isolate, javaClass), javaProxy);
}

Local<Object> ProxyFactory::createV8Proxy(v8::Isolate* isolate, Local<Value> className, jobject javaProxy)
{
	LOGD(TAG, "ProxyFactory::createV8Proxy");
	JNIEnv* env = JNIUtil::getJNIEnv();
	if (!env) {
		LOG_JNIENV_ERROR("while creating Java proxy.");
		return Local<Object>();
	}

	Local<Context> context = isolate->GetCurrentContext();
	v8::EscapableHandleScope scope(isolate);

	Local<Object> exports = KrollBindings::getBinding(isolate, className->ToString(context).FromMaybe(String::Empty(isolate)));

	if (exports.IsEmpty()) {
		v8::String::Utf8Value classStr(isolate, className);
		LOGE(TAG, "Failed to find class for %s", *classStr);
		LOG_JNIENV_ERROR("while creating V8 Proxy.");
		return Local<Object>();
	}

	// FIXME: We pick the first item in exports as the constructor. We should do something more intelligent (for ES6 look at default export?)
	Local<Array> names;
	MaybeLocal<Array> possibleNames = exports->GetPropertyNames(context);
	if (!possibleNames.ToLocal(&names) || names->Length() < 1) {
		v8::String::Utf8Value classStr(isolate, className);
		LOGE(TAG, "Failed to find constructor in exports for %s", *classStr);
		LOG_JNIENV_ERROR("while creating V8 Proxy.");
		return Local<Object>();
	}
	MaybeLocal<Value> possibleConstructor = exports->Get(context, names->Get(context, 0).ToLocalChecked());
	if (possibleConstructor.IsEmpty()) {
		v8::String::Utf8Value classStr(isolate, className);
		LOGE(TAG, "Failed to get constructor in exports for %s", *classStr);
		LOG_JNIENV_ERROR("while creating V8 Proxy.");
		return Local<Object>();
	}

	Local<Function> creator = possibleConstructor.ToLocalChecked().As<Function>();

	Local<Value> javaObjectExternal = External::New(isolate, javaProxy);
	TryCatch tryCatch(isolate);
	Local<Value> argv[1] = { javaObjectExternal };

	Local<Object> v8Proxy;
	MaybeLocal<Object> maybeV8Proxy = creator->NewInstance(context, 1, argv);
	if (!maybeV8Proxy.ToLocal(&v8Proxy)) {
		LOGE(TAG, "Exception thrown while creating V8 proxy.");
		V8Util::reportException(isolate, tryCatch);
		return Local<Object>();
	}

	// The v8Proxy is a JS Object containing an internal pointer to the Proxy object that wraps it in C++ world.
	titanium::Proxy* proxy = NativeObject::Unwrap<titanium::Proxy>(v8Proxy);
	// We take the address of that C++ Proxy/JavaObject and store it on the Java side to reference when we need to get the proxy or JS object again
	jlong ptr = (jlong) proxy;

	jobject javaV8Object = env->NewObject(JNIUtil::v8ObjectClass, JNIUtil::v8ObjectInitMethod, ptr);

	env->SetObjectField(javaProxy, JNIUtil::krollProxyKrollObjectField, javaV8Object);
	env->DeleteLocalRef(javaV8Object);

	return scope.Escape(v8Proxy);
}

jobject ProxyFactory::createJavaProxy(jclass javaClass, Local<Object> v8Proxy, const v8::FunctionCallbackInfo<v8::Value>& args)
{
	LOGD(TAG, "ProxyFactory::createJavaProxy");
	Isolate* isolate = args.GetIsolate();

	JNIEnv* env = JNIUtil::getJNIEnv();
	if (!env) {
		LOG_JNIENV_ERROR("while creating Java proxy.");
		return NULL;
	}

	Local<Context> context = isolate->GetCurrentContext();

	// Grab the Proxy pointer from the JSObject that wraps it,
	// pass along the address of the Proxy to use a pointer to get it back later when we deal with this object
	titanium::Proxy* proxy = NativeObject::Unwrap<titanium::Proxy>(v8Proxy); // v8Proxy holds pointer to Proxy object in internal field
	jlong pv8Proxy = (jlong) proxy; // So now we store pointer to the Proxy on Java side.

	// Determine if this constructor call was made within
	// the createXYZ() wrapper function. This can be tested by checking
	// if an Arguments object was passed as the sole argument.
	bool calledFromCreate = false;
	if (args.Length() == 1 && args[0]->IsObject()) {
		if (V8Util::constructorNameMatches(isolate, args[0].As<Object>(), "Arguments")) {
			calledFromCreate = true;
		}
	}


	// We also pass the creation URL of the proxy so we can track relative URLs
	// First see if this is getting passed in for us already
	jstring javaSourceUrl = NULL;

	// Convert the V8 arguments into Java types so they can be
	// passed to the Java creator method. Which converter we use
	// depends how this constructor was called.
	jobjectArray javaArgs;
	if (calledFromCreate) {
		Local<Object> arguments = args[0].As<Object>();
		MaybeLocal<Value> lengthValue = arguments->Get(context, Proxy::lengthSymbolMap[isolate].Get(isolate));
		Maybe<int32_t> length = Nothing<int32_t>();
		if (!lengthValue.IsEmpty()) {
			length = lengthValue.ToLocalChecked()->Int32Value(context);
		}
		int start = 0;

		// Get the scope variables if provided and extract the source URL.
		// We need to send that to the Java side when creating the proxy.
		if (length.FromMaybe(0) > 0) {
			MaybeLocal<Value> maybeFirstArgument = arguments->Get(context, 0);
			if (!maybeFirstArgument.IsEmpty()) {
				MaybeLocal<Object> scopeVars = maybeFirstArgument.ToLocalChecked()->ToObject(context);
				if (!scopeVars.IsEmpty() && V8Util::constructorNameMatches(isolate, scopeVars.ToLocalChecked(), "ScopeVars")) {
					MaybeLocal<Value> sourceUrl = scopeVars.ToLocalChecked()->Get(context, Proxy::sourceUrlSymbolMap[isolate].Get(isolate));
					javaSourceUrl = TypeConverter::jsValueToJavaString(isolate, env, sourceUrl.FromMaybe(String::Empty(isolate).As<Value>()));
					start = 1;
				}
			}
		}

		javaArgs = TypeConverter::jsObjectIndexPropsToJavaArray(isolate, env, arguments, start, length.FromMaybe(0));
	} else {
		javaArgs = TypeConverter::jsArgumentsToJavaArray(env, args);
	}

	// This does: Object javaV8Object = new V8Object(pv8Proxy);
	jobject javaV8Object = env->NewObject(JNIUtil::v8ObjectClass,
		JNIUtil::v8ObjectInitMethod, pv8Proxy);

	// Crap, we didn't get passed the sourceURL in context. So let's hack this and grab from current stack
	// So far in every case this was logged in our unit test suite, the URL was either:
	// - a ti:/whatever.js runtime file
	// - a module.id/bootstrap.js file
	// So, I think we can just skip this hack altogether? Or maybe just assume "app://app.js"?
	if (javaSourceUrl == NULL) {
		Local<String> sourceUrl = v8::StackTrace::CurrentStackTrace(isolate, 1, v8::StackTrace::kScriptName)->GetFrame(isolate, 0)->GetScriptNameOrSourceURL();
		// v8::String::Utf8Value sourceUrlThing(isolate, sourceUrl);
		// LOGE(TAG, "Was given no sourceURL. Trying to hack one from stack trace: %s", *sourceUrlThing);
		javaSourceUrl = TypeConverter::jsValueToJavaString(isolate, env, sourceUrl);
	}

	// This does: KrollProxy.createProxy(javaClass, javaV8Object, javaArgs, javaSourceUrl);
	// Which creates a new instance of the class using default no-arg constructor,
	// and then calls #setupProxy() with the rest of the args on the instance.
	jobject javaProxy = env->CallStaticObjectMethod(JNIUtil::krollProxyClass,
		JNIUtil::krollProxyCreateProxyMethod, javaClass, javaV8Object, javaArgs, javaSourceUrl);

	if (javaSourceUrl) {
		LOGD(TAG, "delete source url!");
		env->DeleteLocalRef(javaSourceUrl);
	}

	env->DeleteLocalRef(javaV8Object);
	env->DeleteLocalRef(javaArgs);
	// We don't delete the global jclass reference...

	if (env->ExceptionCheck()) {
		JSException::fromJavaException(isolate);
		env->ExceptionClear();
		return NULL;
	}

	return javaProxy;
}

Local<Value> ProxyFactory::getJavaClassName(v8::Isolate* isolate, jclass javaClass)
{
	LOGD(TAG, "ProxyFactory::getJavaClassName");
	JNIEnv* env = JNIUtil::getJNIEnv();
	if (!env) {
		LOGE(TAG, "Unable to get JNIEnv while getting Java class name as V8 value.");
		return Local<Value>();
	}

	v8::EscapableHandleScope scope(isolate);

	jstring javaClassName = JNIUtil::getClassName(javaClass);
	Local<Value> className = TypeConverter::javaStringToJsString(isolate, env, javaClassName);
	env->DeleteLocalRef(javaClassName);
	return scope.Escape(className);
}

void ProxyFactory::dispose(Isolate* isolate)
{
	// no-op for now
}

}
