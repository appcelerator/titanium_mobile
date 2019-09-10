/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-2018 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#ifndef TYPE_CONVERTER_H
#define TYPE_CONVERTER_H

#include <map>
#include <jni.h>
#include <v8.h>

#define MAX_STACK 10

namespace titanium {
class TypeConverter
{
public:
	// Our global map of "pointers" to persistent functions
	static std::map<int64_t, v8::Persistent<v8::Function, v8::CopyablePersistentTraits<v8::Function>>> functions;
	// The incrementing key to store the persistent functions
	static int64_t functionIndex;

	// Our global map of "pointers" to persistent functions
    static std::map<int64_t, v8::Persistent<v8::Promise::Resolver, v8::CopyablePersistentTraits<v8::Promise::Resolver>>> resolvers;
    // The incrementing key to store the persistent functions
    static int64_t resolverIndex;

	// short convert methods
	static jshort jsNumberToJavaShort(v8::Local<v8::Number> jsNumber);
	static v8::Local<v8::Number> javaShortToJsNumber(v8::Isolate* isolate, jshort javaShort);

	static inline jshort jsNumberToJavaShort(JNIEnv *env, v8::Local<v8::Number> jsNumber) {
		return jsNumberToJavaShort(jsNumber);
	}
	static inline v8::Local<v8::Number> javaShortToJsNumber(v8::Isolate* isolate, JNIEnv *env, jshort javaShort) {
		return javaShortToJsNumber(isolate, javaShort);
	}

	// int convert methods
	static jint jsNumberToJavaInt(v8::Local<v8::Number> jsNumber);
	static v8::Local<v8::Number> javaIntToJsNumber(v8::Isolate* isolate, jint javaInt);

	static inline jint jsNumberToJavaInt(JNIEnv *env, v8::Local<v8::Number> jsNumber) {
		return jsNumberToJavaInt(jsNumber);
	}
	static inline v8::Local<v8::Number> javaIntToJsNumber(v8::Isolate* isolate, JNIEnv *env, jint javaInt) {
		return javaIntToJsNumber(isolate, javaInt);
	}

	// long convert methods
	static jlong jsNumberToJavaLong(v8::Local<v8::Number> jsNumber);
	static v8::Local<v8::Number> javaLongToJsNumber(v8::Isolate* isolate, jlong javaLong);

	static inline jlong jsNumberToJavaLong(JNIEnv *env, v8::Local<v8::Number> jsNumber) {
		return jsNumberToJavaLong(jsNumber);
	}
	static inline v8::Local<v8::Number> javaLongToJsNumber(v8::Isolate* isolate, JNIEnv *env, jlong javaLong) {
		return javaLongToJsNumber(isolate, javaLong);
	}

	// float convert methods
	static jfloat jsNumberToJavaFloat(v8::Local<v8::Number> jsNumber);
	static v8::Local<v8::Number> javaFloatToJsNumber(v8::Isolate* isolate, jfloat javaFloat);

	static inline jfloat jsNumberToJavaFloat(JNIEnv *env, v8::Local<v8::Number> jsNumber) {
		return jsNumberToJavaFloat(jsNumber);
	}
	static inline v8::Local<v8::Number> javaFloatToJsNumber(v8::Isolate* isolate, JNIEnv *env, jfloat javaFloat) {
		return javaFloatToJsNumber(isolate, javaFloat);
	}

	// double convert methods
	static jdouble jsNumberToJavaDouble(v8::Local<v8::Number> jsNumber);
	static v8::Local<v8::Number> javaDoubleToJsNumber(v8::Isolate* isolate, jdouble javaDouble);

	static inline jdouble jsNumberToJavaDouble(JNIEnv *env, v8::Local<v8::Number> jsNumber) {
		return jsNumberToJavaDouble(jsNumber);
	}
	static inline v8::Local<v8::Number> javaDoubleToJsNumber(v8::Isolate* isolate, JNIEnv *env, jdouble javaDouble) {
		return javaDoubleToJsNumber(isolate, javaDouble);
	}

	// boolean convert methods
	static jboolean jsBooleanToJavaBoolean(v8::Local<v8::Boolean> jsBoolean);
	static v8::Local<v8::Boolean> javaBooleanToJsBoolean(v8::Isolate* isolate, jboolean javaBoolean);

	static inline jboolean jsBooleanToJavaBoolean(JNIEnv *env, v8::Local<v8::Boolean> jsBoolean) {
		return jsBooleanToJavaBoolean(jsBoolean);
	}
	static inline v8::Local<v8::Boolean> javaBooleanToJsBoolean(v8::Isolate* isolate, JNIEnv *env, jboolean javaBoolean) {
		return javaBooleanToJsBoolean(isolate, javaBoolean);
	}

	// string convert methods
	static jstring jsStringToJavaString(v8::Local<v8::String> jsString);
	static jstring jsStringToJavaString(v8::Isolate* isolate, v8::Local<v8::String> jsString);
	static jstring jsValueToJavaString(v8::Isolate* isolate, v8::Local<v8::Value> jsValue);
	static v8::Local<v8::Value> javaStringToJsString(v8::Isolate* isolate, jstring javaString);

	static jstring jsStringToJavaString(JNIEnv *env, v8::Local<v8::String> jsString);
	static jstring jsStringToJavaString(v8::Isolate* isolate, JNIEnv *env, v8::Local<v8::String> jsString);
	static jstring jsValueToJavaString(v8::Isolate* isolate, JNIEnv *env, v8::Local<v8::Value> jsValue);
	static v8::Local<v8::Value> javaStringToJsString(v8::Isolate* isolate, JNIEnv *env, jstring javaString);

	// date convert methods
	static jobject jsDateToJavaDate(v8::Local<v8::Date> jsDate);
	static jlong jsDateToJavaLong(v8::Local<v8::Date> jsDate);
	static v8::Local<v8::Date> javaDateToJsDate(v8::Isolate* isolate, jobject);
	static v8::Local<v8::Date> javaLongToJsDate(v8::Isolate* isolate, jlong);

	static jobject jsDateToJavaDate(JNIEnv *env, v8::Local<v8::Date> jsDate);
	static v8::Local<v8::Date> javaDateToJsDate(v8::Isolate* isolate, JNIEnv *env, jobject);
	static inline jlong jsDateToJavaLong(JNIEnv *env, v8::Local<v8::Date> jsDate) {
		return jsDateToJavaLong(jsDate);
	}
	static inline v8::Local<v8::Date> javaLongToJsDate(v8::Isolate* isolate, JNIEnv *env, jlong javaLong) {
		return javaLongToJsDate(isolate, javaLong);
	}

	// function convert methods
	static jobject jsObjectToJavaFunction(v8::Isolate* isolate, v8::Local<v8::Object> jsObject);
	static v8::Local<v8::Function> javaObjectToJsFunction(v8::Isolate* isolate, jobject javaObject);

	static jobject jsObjectToJavaFunction(v8::Isolate* isolate, JNIEnv *env, v8::Local<v8::Object> jsObject);
	static v8::Local<v8::Function> javaObjectToJsFunction(v8::Isolate* isolate, JNIEnv *env, jobject javaObject);

	// promise resolvers convert methods
	static jobject jsObjectToJavaPromise(v8::Isolate* isolate, v8::Local<v8::Object> jsObject);
	static jobject jsObjectToJavaPromise(v8::Isolate* isolate, JNIEnv *env, v8::Local<v8::Object> jsObject);

	// arguments conversion
	static jobjectArray jsArgumentsToJavaArray(const v8::FunctionCallbackInfo<v8::Value>& args);

	// BIG FUCKING WARNING!!  make sure you call "delete" on the return value otherwise the
	// you have introduced a memory leak and the world will end.  plzkthksbye
	static v8::Local<v8::Value> * javaObjectArrayToJsArguments(v8::Isolate* isolate, jobjectArray javaObjectArray, int *length);

	static jobjectArray jsArgumentsToJavaArray(JNIEnv *env, const v8::FunctionCallbackInfo<v8::Value>& args);
	static v8::Local<v8::Value> * javaObjectArrayToJsArguments(v8::Isolate* isolate, JNIEnv *env, jobjectArray javaObjectArray, int *length);


	// array convert methods
	static jarray jsArrayToJavaArray(v8::Isolate* isolate, v8::Local<v8::Array> jsArray);
	static jobjectArray jsArrayToJavaStringArray(v8::Isolate* isolate, v8::Local<v8::Array> jsArray);
	static v8::Local<v8::Array> javaArrayToJsArray(v8::Isolate* isolate, jbooleanArray javaBooleanArray);
	static jshortArray jsArrayToJavaShortArray(v8::Isolate* isolate, v8::Local<v8::Array> jsArray);
	static v8::Local<v8::Array> javaArrayToJsArray(v8::Isolate* isolate, jshortArray javaShortArray);
	static jintArray jsArrayToJavaIntArray(v8::Isolate* isolate, v8::Local<v8::Array> jsArray);
	static v8::Local<v8::Array> javaArrayToJsArray(v8::Isolate* isolate, jintArray javaIntArray);
	static jlongArray jsArrayToJavaLongArray(v8::Isolate* isolate, v8::Local<v8::Array> jsArray);
	static v8::Local<v8::Array> javaArrayToJsArray(v8::Isolate* isolate, jlongArray javaLongArray);
	static jfloatArray jsArrayToJavaFloatArray(v8::Isolate* isolate, v8::Local<v8::Array> jsArray);
	static v8::Local<v8::Array> javaArrayToJsArray(v8::Isolate* isolate, jfloatArray javaFloatArray);
	static jdoubleArray jsArrayToJavaDoubleArray(v8::Isolate* isolate, v8::Local<v8::Array> jsArray);
	static v8::Local<v8::Array> javaArrayToJsArray(v8::Isolate* isolate, jdoubleArray javaDoubleArray);
	static v8::Local<v8::Array> javaArrayToJsArray(v8::Isolate* isolate, jobjectArray javaObjectArray);

	static jarray jsArrayToJavaArray(v8::Isolate* isolate, JNIEnv *env, v8::Local<v8::Array> jsArray);
	static jobjectArray jsArrayToJavaStringArray(v8::Isolate* isolate, JNIEnv *env, v8::Local<v8::Array> jsArray);
	static v8::Local<v8::Array> javaArrayToJsArray(v8::Isolate* isolate, JNIEnv *env, jbooleanArray javaBooleanArray);
	static jshortArray jsArrayToJavaShortArray(v8::Isolate* isolate, JNIEnv *env, v8::Local<v8::Array> jsArray);
	static v8::Local<v8::Array> javaArrayToJsArray(v8::Isolate* isolate, JNIEnv *env, jshortArray javaShortArray);
	static jintArray jsArrayToJavaIntArray(v8::Isolate* isolate, JNIEnv *env, v8::Local<v8::Array> jsArray);
	static v8::Local<v8::Array> javaArrayToJsArray(v8::Isolate* isolate, JNIEnv *env, jintArray javaIntArray);
	static jlongArray jsArrayToJavaLongArray(v8::Isolate* isolate, JNIEnv *env, v8::Local<v8::Array> jsArray);
	static v8::Local<v8::Array> javaArrayToJsArray(v8::Isolate* isolate, JNIEnv *env, jlongArray javaLongArray);
	static jfloatArray jsArrayToJavaFloatArray(v8::Isolate* isolate, JNIEnv *env, v8::Local<v8::Array> jsArray);
	static v8::Local<v8::Array> javaArrayToJsArray(v8::Isolate* isolate, JNIEnv *env, jfloatArray javaFloatArray);
	static jdoubleArray jsArrayToJavaDoubleArray(v8::Isolate* isolate, JNIEnv *env, v8::Local<v8::Array> jsArray);
	static v8::Local<v8::Array> javaArrayToJsArray(v8::Isolate* isolate, JNIEnv *env, jdoubleArray javaDoubleArray);
	static v8::Local<v8::Array> javaArrayToJsArray(v8::Isolate* isolate, JNIEnv *env, jobjectArray javaObjectArray);

	static v8::Local<v8::Object> javaThrowableToJSError(v8::Isolate* isolate, jthrowable javaException);
	static v8::Local<v8::Object> javaThrowableToJSError(v8::Isolate* isolate, JNIEnv *env, jthrowable javaException);

	// object convert methods
	static inline jobject jsValueToJavaObject(v8::Isolate* isolate, v8::Local<v8::Value> jsValue) {
		bool isNew;
		return jsValueToJavaObject(isolate, jsValue, &isNew);
	}

	static inline jobject jsValueToJavaObject(v8::Isolate* isolate, JNIEnv *env, v8::Local<v8::Value> jsValue) {
		bool isNew;
		return jsValueToJavaObject(isolate, env, jsValue, &isNew);
	}

	static jobject jsValueToJavaError(v8::Isolate* isolate, v8::Local<v8::Value> jsValue, bool *isNew);
	static jobject jsValueToJavaObject(v8::Isolate* isolate, v8::Local<v8::Value> jsValue, bool *isNew);
	static v8::Local<v8::Value> javaObjectToJsValue(v8::Isolate* isolate, jobject javaObject);
	static jobject jsObjectToJavaKrollDict(v8::Isolate* isolate, v8::Local<v8::Value> jsValue, bool *isNew);

	static jobject jsValueToJavaError(v8::Isolate* isolate, JNIEnv *env, v8::Local<v8::Value> jsValue, bool *isNew);
	static jobject jsValueToJavaObject(v8::Isolate* isolate, JNIEnv *env, v8::Local<v8::Value> jsValue, bool *isNew);
	static v8::Local<v8::Object> javaHashMapToJsValue(v8::Isolate* isolate, JNIEnv *env, jobject javaObject);
	static v8::Local<v8::Value> javaObjectToJsValue(v8::Isolate* isolate, JNIEnv *env, jobject javaObject);
	static jobject jsObjectToJavaKrollDict(v8::Isolate* isolate, JNIEnv *env, v8::Local<v8::Value> jsValue, bool *isNew);

	// Convert a JS object's indexed properties to a Java object array.
	// Starts at index zero and continues until length is reached.
	static jobjectArray jsObjectIndexPropsToJavaArray(v8::Isolate* isolate, v8::Local<v8::Object> jsObject, int start, int length);

	static jobjectArray jsObjectIndexPropsToJavaArray(v8::Isolate* isolate, JNIEnv *env, v8::Local<v8::Object> jsObject, int start, int length);

private:
	// utility methods
	static v8::Local<v8::Array> javaDoubleArrayToJsNumberArray(v8::Isolate* isolate, jdoubleArray javaDoubleArray);
	static v8::Local<v8::Array> javaLongArrayToJsNumberArray(v8::Isolate* isolate, jlongArray javaLongArray);
	static v8::Local<v8::Array> javaFloatArrayToJsNumberArray(v8::Isolate* isolate, jfloatArray javaFloatArray);
	static v8::Local<v8::Array> javaShortArrayToJsNumberArray(v8::Isolate* isolate, jshortArray javaShortArray);

	static v8::Local<v8::Array> javaDoubleArrayToJsNumberArray(v8::Isolate* isolate, JNIEnv *env, jdoubleArray javaDoubleArray);
	static v8::Local<v8::Array> javaLongArrayToJsNumberArray(v8::Isolate* isolate, JNIEnv *env, jlongArray javaLongArray);
	static v8::Local<v8::Array> javaFloatArrayToJsNumberArray(v8::Isolate* isolate, JNIEnv *env, jfloatArray javaFloatArray);
	static v8::Local<v8::Array> javaShortArrayToJsNumberArray(v8::Isolate* isolate, JNIEnv *env, jshortArray javaShortArray);

};
}

#endif
