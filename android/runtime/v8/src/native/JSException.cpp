/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#include <jni.h>
#include <v8.h>

#include "TypeConverter.h"
#include "JSException.h"

using namespace v8;

namespace titanium {

Local<Value> JSException::fromJavaException(v8::Isolate* isolate, jthrowable javaException)
{
	JNIEnv* env = JNIUtil::getJNIEnv();
	if (!env) {
		return GetJNIEnvironmentError(isolate);
	}

	bool deleteRef = false;
	if (!javaException) {
		javaException = env->ExceptionOccurred();
		deleteRef = true;
	}
	env->ExceptionClear();

	Local<Object> error = TypeConverter::javaThrowableToJSError(isolate, env, javaException);
	if (deleteRef) {
		env->DeleteLocalRef(javaException);
	}
	// throw it
	return isolate->ThrowException(error);
}

}
