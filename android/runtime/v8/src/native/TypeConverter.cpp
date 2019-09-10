/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-2018 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#include <cstring>
#include <sstream>
#include <limits>

#include <jni.h>
#include <stdio.h>
#include <v8.h>

#include "AndroidUtil.h"
#include "TypeConverter.h"
#include "JNIUtil.h"
#include "Proxy.h"
#include "ProxyFactory.h"
#include "V8Runtime.h"
#include "V8Util.h"

#define TAG "TypeConverter"

using namespace titanium;

// The incrementing index used to store new persistent functions in our global map. Start at the minimum value possible and increment by one as we go
// Ideally we should "wrap around" when we reach max, but are we really expecting to go through more than 18,446,744,073,709,551,615 functions?
int64_t TypeConverter::functionIndex = std::numeric_limits<int64_t>::min();
// The global map to hold persistent functions. We use the index as our "pointer" to store and retrieve the function
std::map<int64_t, Persistent<Function, CopyablePersistentTraits<Function>>> TypeConverter::functions;

int64_t TypeConverter::resolverIndex = std::numeric_limits<int64_t>::min();
std::map<int64_t, Persistent<Promise::Resolver, CopyablePersistentTraits<Promise::Resolver>>> TypeConverter::resolvers;

/****************************** public methods ******************************/
jshort TypeConverter::jsNumberToJavaShort(Local<Number> jsNumber)
{
	return ((jshort) jsNumber->Value());
}

Local<Number> TypeConverter::javaShortToJsNumber(Isolate* isolate, jshort javaShort)
{
	return Number::New(isolate, (double) javaShort);
}

jint TypeConverter::jsNumberToJavaInt(Local<Number> jsNumber)
{
	return ((jint) jsNumber->Value());
}

Local<Number> TypeConverter::javaIntToJsNumber(Isolate* isolate, jint javaInt)
{
	return Number::New(isolate, (double) javaInt);
}

jlong TypeConverter::jsNumberToJavaLong(Local<Number> jsNumber)
{
	return ((jlong) jsNumber->Value());
}

Local<Number> TypeConverter::javaLongToJsNumber(Isolate* isolate, jlong javaLong)
{
	return Number::New(isolate, (double) javaLong);
}

jfloat TypeConverter::jsNumberToJavaFloat(Local<Number> jsNumber)
{
	return ((jfloat) jsNumber->Value());
}

Local<Number> TypeConverter::javaFloatToJsNumber(Isolate* isolate, jfloat javaFloat)
{
	return Number::New(isolate, (double) javaFloat);
}

jdouble TypeConverter::jsNumberToJavaDouble(Local<Number> jsNumber)
{
	return ((jdouble) jsNumber->Value());
}

Local<Number> TypeConverter::javaDoubleToJsNumber(Isolate* isolate, jdouble javaDouble)
{
	return Number::New(isolate, javaDouble);
}

jboolean TypeConverter::jsBooleanToJavaBoolean(Local<Boolean> jsBoolean)
{
	return (jsBoolean->Value()) == JNI_TRUE;
}

Local<Boolean> TypeConverter::javaBooleanToJsBoolean(Isolate* isolate, jboolean javaBoolean)
{
	return Boolean::New(isolate, (bool) javaBoolean);
}

jstring TypeConverter::jsStringToJavaString(Local<String> jsString)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (env == NULL) {
		return NULL;
	}
	return TypeConverter::jsStringToJavaString(env, jsString);
}

jstring TypeConverter::jsStringToJavaString(Isolate* isolate, Local<String> jsString)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (env == NULL) {
		return NULL;
	}
	return TypeConverter::jsStringToJavaString(isolate, env, jsString);
}

jstring TypeConverter::jsStringToJavaString(JNIEnv *env, Local<String> jsString)
{
	String::Value string(jsString);
	return env->NewString(reinterpret_cast<const jchar*>(*string), string.length());
}

jstring TypeConverter::jsStringToJavaString(Isolate* isolate, JNIEnv *env, Local<String> jsString)
{
	String::Value string(isolate, jsString);
	return env->NewString(reinterpret_cast<const jchar*>(*string), string.length());
}

jstring TypeConverter::jsValueToJavaString(Isolate* isolate, Local<Value> jsValue)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (env == NULL) {
		return NULL;
	}
	return TypeConverter::jsValueToJavaString(isolate, env, jsValue);
}

jstring TypeConverter::jsValueToJavaString(Isolate* isolate, JNIEnv *env, Local<Value> jsValue)
{
	if (jsValue.IsEmpty() || jsValue->IsNullOrUndefined()) {
		return NULL;
	}

	return TypeConverter::jsStringToJavaString(isolate, env, jsValue->ToString(isolate));
}

Local<Value> TypeConverter::javaStringToJsString(Isolate* isolate, jstring javaString)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (env == NULL) {
		return String::Empty(isolate);
	}
	return TypeConverter::javaStringToJsString(isolate, env, javaString);
}

Local<Value> TypeConverter::javaStringToJsString(Isolate* isolate, JNIEnv *env, jstring javaString)
{
	if (!javaString) {
		return Null(isolate);
	}

	int nativeStringLength = env->GetStringLength(javaString);
	const jchar *nativeString = env->GetStringChars(javaString, NULL);
	// FIXME Propagate MaybeLocal to caller?
	Local<String> jsString = String::NewFromTwoByte(isolate, nativeString, NewStringType::kNormal, nativeStringLength).ToLocalChecked();
	env->ReleaseStringChars(javaString, nativeString);

	return jsString;
}

jobject TypeConverter::jsDateToJavaDate(Local<Date> jsDate)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (env == NULL) {
		return NULL;
	}
	return TypeConverter::jsDateToJavaDate(env, jsDate);
}

jobject TypeConverter::jsDateToJavaDate(JNIEnv *env, Local<Date> jsDate)
{
	return env->NewObject(JNIUtil::dateClass, JNIUtil::dateInitMethod, (jlong) jsDate->ValueOf());
}

jlong TypeConverter::jsDateToJavaLong(Local<Date> jsDate)
{
	return (jlong) jsDate->ValueOf();
}

Local<Date> TypeConverter::javaDateToJsDate(Isolate* isolate, jobject javaDate)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (env == NULL) {
		return Local<Date>();
	}
	return TypeConverter::javaDateToJsDate(isolate, env, javaDate);
}

Local<Date> TypeConverter::javaDateToJsDate(Isolate* isolate, JNIEnv *env, jobject javaDate)
{
	jlong epochTime = env->CallLongMethod(javaDate, JNIUtil::dateGetTimeMethod);
	return TypeConverter::javaLongToJsDate(isolate, epochTime);
}

Local<Date> TypeConverter::javaLongToJsDate(Isolate* isolate, jlong javaLong)
{
	return Date::New(isolate, (double) javaLong).As<Date>(); // perversely, the date constructor returns Local<value> so we need to cast it
}

jobject TypeConverter::jsObjectToJavaFunction(Isolate* isolate, Local<Object> jsObject)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (!env) {
		return NULL;
	}
	return TypeConverter::jsObjectToJavaFunction(isolate, env, jsObject);
}

jobject TypeConverter::jsObjectToJavaFunction(Isolate* isolate, JNIEnv *env, Local<Object> jsObject)
{
	Local<Function> func = jsObject.As<Function>();
	Persistent<Function, CopyablePersistentTraits<Function>> jsFunction(isolate, func);
	jsFunction.MarkIndependent();

	// Place the persistent into some global table with incrementing index, use the index as the "ptr" here
	// Then when we re-construct, use the ptr value as index into the table to grab the persistent!
	jlong ptr = (jlong) functionIndex; // jlong is signed 64-bit, so int64_t should match up
	TypeConverter::functions[functionIndex] = jsFunction;
	functionIndex++;
	// Java code assumes 0 is null pointer. So we need to skip it. TODO fix this so we don't need to perform this special check?
	if (functionIndex == 0) {
		functionIndex++;
	}

	return env->NewObject(JNIUtil::v8FunctionClass, JNIUtil::v8FunctionInitMethod, ptr);
}

Local<Function> TypeConverter::javaObjectToJsFunction(Isolate* isolate, jobject javaObject)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (!env) {
		return Local<Function>();
	}
	return TypeConverter::javaObjectToJsFunction(isolate, env, javaObject);
}

Local<Function> TypeConverter::javaObjectToJsFunction(Isolate* isolate, JNIEnv *env, jobject javaObject)
{
	jlong v8ObjectPointer = env->GetLongField(javaObject, JNIUtil::v8ObjectPtrField);
	Persistent<Function, CopyablePersistentTraits<Function>> persistentV8Object = TypeConverter::functions.at(v8ObjectPointer);
	return persistentV8Object.Get(isolate);
}

jobject TypeConverter::jsObjectToJavaPromise(Isolate* isolate, Local<Object> jsObject)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (!env) {
		return NULL;
	}
	return TypeConverter::jsObjectToJavaPromise(isolate, env, jsObject);
}

jobject TypeConverter::jsObjectToJavaPromise(Isolate* isolate, JNIEnv *env, Local<Object> jsObject)
{
    Local<Promise::Resolver> resolver = jsObject.As<Promise::Resolver>();
    Persistent<Promise::Resolver, CopyablePersistentTraits<Promise::Resolver>> persistent(isolate, resolver);
    persistent.MarkIndependent();

	// Place the persistent into some global table with incrementing index, use the index as the "ptr" here
	// Then when we re-construct, use the ptr value as index into the table to grab the persistent!
	jlong ptr = (jlong) resolverIndex; // jlong is signed 64-bit, so int64_t should match up
	TypeConverter::resolvers[resolverIndex] = persistent;
	resolverIndex++;
	// Java code assumes 0 is null pointer. So we need to skip it. TODO fix this so we don't need to perform this special check?
	if (resolverIndex == 0) {
		resolverIndex++;
	}

	return env->NewObject(JNIUtil::v8PromiseClass, JNIUtil::v8PromiseInitMethod, ptr);
}
jobjectArray TypeConverter::jsArgumentsToJavaArray(const FunctionCallbackInfo<Value>& args)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (!env) {
		return NULL;
	}
	return TypeConverter::jsArgumentsToJavaArray(env, args);
}

jobjectArray TypeConverter::jsArgumentsToJavaArray(JNIEnv *env, const FunctionCallbackInfo<Value>& args)
{
	Isolate* isolate = args.GetIsolate();
	HandleScope scope(isolate);
	int argCount = args.Length();
	jobjectArray javaArgs = env->NewObjectArray(argCount, JNIUtil::objectClass, NULL);

	for (int i = 0; i < argCount; ++i)
	{
		Local<Value> v8Arg = args[i];
		bool isNew;
		jobject javaArg = jsValueToJavaObject(isolate, v8Arg, &isNew);
		env->SetObjectArrayElement(javaArgs, i, javaArg);

		if (isNew) {
			env->DeleteLocalRef(javaArg);
		}
	}

	return javaArgs;
}

// call "delete" on the return value otherwise the memory will never be released
Local<Value>* TypeConverter::javaObjectArrayToJsArguments(Isolate* isolate, jobjectArray javaObjectArray, int* length)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (!env) {
		return NULL;
	}
	return TypeConverter::javaObjectArrayToJsArguments(isolate, env, javaObjectArray, length);
}

Local<Value>* TypeConverter::javaObjectArrayToJsArguments(Isolate* isolate, JNIEnv *env, jobjectArray javaObjectArray, int* length)
{
	jsize javaArrayLength = env->GetArrayLength(javaObjectArray);
	Local<Value> *jsArguments = new Local<Value>[javaArrayLength];
	for (int i = 0; i < javaArrayLength; i++)
	{
		jobject arrayElement = env->GetObjectArrayElement(javaObjectArray, i);
		jsArguments[i] = TypeConverter::javaObjectToJsValue(isolate, env, arrayElement);
		env->DeleteLocalRef(arrayElement);
	}

	*length = javaArrayLength;
	return jsArguments;
}

jarray TypeConverter::jsArrayToJavaArray(Isolate* isolate, Local<Array> jsArray)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (env == NULL) {
		return NULL;
	}
	return TypeConverter::jsArrayToJavaArray(isolate, env, jsArray);
}

jarray TypeConverter::jsArrayToJavaArray(Isolate* isolate, JNIEnv *env, Local<Array> jsArray)
{
	int arrayLength = jsArray->Length();
	jobjectArray javaArray = env->NewObjectArray(arrayLength, JNIUtil::objectClass, NULL);
	if (javaArray == NULL) {
		LOGE(TAG, "unable to create new jobjectArray");
		return NULL;
	}

	Local<Context> context = isolate->GetCurrentContext();
	for (int i = 0; i < arrayLength; i++) {
		MaybeLocal<Value> element = jsArray->Get(context, i);
		if (element.IsEmpty()) {
			LOGE(TAG, "Failed to get element at index %d, inserting null", i);
			env->SetObjectArrayElement(javaArray, i, NULL);
		} else {
			bool isNew;
			jobject javaObject = jsValueToJavaObject(isolate, element.ToLocalChecked(), &isNew);
			env->SetObjectArrayElement(javaArray, i, javaObject);
			if (isNew) {
				env->DeleteLocalRef(javaObject);
			}
		}
	}

	return javaArray;
}

jobjectArray TypeConverter::jsArrayToJavaStringArray(Isolate* isolate, Local<Array> jsArray)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (env == NULL) {
		return NULL;
	}
	return TypeConverter::jsArrayToJavaStringArray(isolate, env, jsArray);
}

jobjectArray TypeConverter::jsArrayToJavaStringArray(Isolate* isolate, JNIEnv *env, Local<Array> jsArray)
{
	int arrayLength = jsArray->Length();
	jobjectArray javaArray = env->NewObjectArray(arrayLength, JNIUtil::stringClass, NULL);
	if (javaArray == NULL) {
		LOGE(TAG, "unable to create new jobjectArray");
		return NULL;
	}

	Local<Context> context = isolate->GetCurrentContext();
	for (int i = 0; i < arrayLength; i++) {
		MaybeLocal<Value> element = jsArray->Get(context, i);
		if (element.IsEmpty()) {
			LOGE(TAG, "Failed to get element at index %d, inserting null", i);
			env->SetObjectArrayElement(javaArray, i, NULL);
		} else {
			MaybeLocal<String> stringValue = element.ToLocalChecked()->ToString(context);
			if (stringValue.IsEmpty()) {
				LOGE(TAG, "Failed to coerce element at index %d into a string, inserting null", i);
				env->SetObjectArrayElement(javaArray, i, NULL);
			} else {
				jstring javaObject = jsStringToJavaString(isolate, env, stringValue.ToLocalChecked());
				env->SetObjectArrayElement(javaArray, i, javaObject);
				env->DeleteLocalRef(javaObject);
			}
		}
	}

	return javaArray;
}

Local<Array> TypeConverter::javaArrayToJsArray(Isolate* isolate, jbooleanArray javaBooleanArray)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (env == NULL) {
		return Array::New(isolate);
	}
	return TypeConverter::javaArrayToJsArray(isolate, env, javaBooleanArray);
}

Local<Array> TypeConverter::javaArrayToJsArray(Isolate* isolate, JNIEnv *env, jbooleanArray javaBooleanArray)
{
	Local<Context> context = isolate->GetCurrentContext();
	int arrayLength = env->GetArrayLength(javaBooleanArray);
	Local<Array> jsArray = Array::New(isolate, arrayLength);

	jboolean *arrayElements = env->GetBooleanArrayElements(javaBooleanArray, 0);
	for (int i = 0; i < arrayLength; i++) {
		jsArray->Set(context, (uint32_t) i, Boolean::New(isolate, arrayElements[i]));
	}

	return jsArray;
}

jshortArray TypeConverter::jsArrayToJavaShortArray(Isolate* isolate, Local<Array> jsArray)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (env == NULL) {
		return NULL;
	}
	return TypeConverter::jsArrayToJavaShortArray(isolate, env, jsArray);
}

jshortArray TypeConverter::jsArrayToJavaShortArray(Isolate* isolate, JNIEnv *env, Local<Array> jsArray)
{
	int arrayLength = jsArray->Length();
	jshortArray javaShortArray = env->NewShortArray(arrayLength);
	if (javaShortArray == NULL) {
		LOGE(TAG, "unable to create new jshortArray");
		return NULL;
	}

	Local<Context> context = isolate->GetCurrentContext();
	jshort* shortBuffer = new jshort[arrayLength];
	for (int i = 0; i < arrayLength; i++) {
		MaybeLocal<Value> element = jsArray->Get(context, i);
		if (element.IsEmpty()) {
			LOGE(TAG, "Failed to get element at index %d, inserting 0", i);
			shortBuffer[i] = 0;
		} else {
			MaybeLocal<Number> number = element.ToLocalChecked()->ToNumber(context);
			if (element.IsEmpty()) {
				LOGE(TAG, "Failed to coerce element at index %d into a Number, inserting 0", i);
				shortBuffer[i] = 0;
			} else {
				shortBuffer[i] = TypeConverter::jsNumberToJavaShort(number.ToLocalChecked());
			}
		}
	}
	env->SetShortArrayRegion(javaShortArray, 0, arrayLength, shortBuffer);

	return javaShortArray;
}

Local<Array> TypeConverter::javaArrayToJsArray(Isolate* isolate, jshortArray javaShortArray)
{
	return javaShortArrayToJsNumberArray(isolate, javaShortArray);
}

Local<Array> TypeConverter::javaArrayToJsArray(Isolate* isolate, JNIEnv *env, jshortArray javaShortArray)
{
	return javaShortArrayToJsNumberArray(isolate, env, javaShortArray);
}

jintArray TypeConverter::jsArrayToJavaIntArray(Isolate* isolate, Local<Array> jsArray)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (env == NULL) {
		return NULL;
	}
	return TypeConverter::jsArrayToJavaIntArray(isolate, env, jsArray);
}

jintArray TypeConverter::jsArrayToJavaIntArray(Isolate* isolate, JNIEnv *env, Local<Array> jsArray)
{
	int arrayLength = jsArray->Length();
	jintArray javaIntArray = env->NewIntArray(arrayLength);
	if (javaIntArray == NULL) {
		LOGE(TAG, "unable to create new jintArray");
		return NULL;
	}

	Local<Context> context = isolate->GetCurrentContext();
	jint* intBuffer = new jint[arrayLength];
	for (int i = 0; i < arrayLength; i++) {
		MaybeLocal<Value> element = jsArray->Get(context, i);
		if (element.IsEmpty()) {
			LOGE(TAG, "Failed to get element at index %d, inserting 0", i);
			intBuffer[i] = 0;
		} else {
			MaybeLocal<Number> number = element.ToLocalChecked()->ToNumber(context);
			if (element.IsEmpty()) {
				LOGE(TAG, "Failed to coerce element at index %d into a Number, inserting 0", i);
				intBuffer[i] = 0;
			} else {
				intBuffer[i] = TypeConverter::jsNumberToJavaInt(number.ToLocalChecked());
			}
		}
	}
	env->SetIntArrayRegion(javaIntArray, 0, arrayLength, intBuffer);

	return javaIntArray;
}

Local<Array> TypeConverter::javaArrayToJsArray(Isolate* isolate, jintArray javaIntArray)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (env == NULL) {
		return Local<Array>();
	}
	return TypeConverter::javaArrayToJsArray(isolate, env, javaIntArray);
}

Local<Array> TypeConverter::javaArrayToJsArray(Isolate* isolate, JNIEnv *env, jintArray javaIntArray)
{
	int arrayLength = env->GetArrayLength(javaIntArray);
	Local<Array> jsArray = Array::New(isolate, arrayLength);

	Local<Context> context = isolate->GetCurrentContext();
	jint *arrayElements = env->GetIntArrayElements(javaIntArray, 0);
	for (int i = 0; i < arrayLength; i++) {
		jsArray->Set(context, (uint32_t) i, Integer::New(isolate, arrayElements[i]));
	}

	return jsArray;
}

jlongArray TypeConverter::jsArrayToJavaLongArray(Isolate* isolate, Local<Array> jsArray)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (env == NULL) {
		return NULL;
	}
	return TypeConverter::jsArrayToJavaLongArray(isolate, env, jsArray);
}

jlongArray TypeConverter::jsArrayToJavaLongArray(Isolate* isolate, JNIEnv *env, Local<Array> jsArray)
{
	int arrayLength = jsArray->Length();
	jlongArray javaLongArray = env->NewLongArray(arrayLength);
	if (javaLongArray == NULL) {
		LOGE(TAG, "unable to create new jlongArray");
		return NULL;
	}

	Local<Context> context = isolate->GetCurrentContext();
	jlong* longBuffer = new jlong[arrayLength];
	for (int i = 0; i < arrayLength; i++) {
		MaybeLocal<Value> element = jsArray->Get(context, i);
		if (element.IsEmpty()) {
			LOGE(TAG, "Failed to get element at index %d, inserting 0", i);
			longBuffer[i] = 0;
		} else {
			MaybeLocal<Number> number = element.ToLocalChecked()->ToNumber(context);
			if (element.IsEmpty()) {
				LOGE(TAG, "Failed to coerce element at index %d into a Number, inserting 0", i);
				longBuffer[i] = 0;
			} else {
				longBuffer[i] = TypeConverter::jsNumberToJavaLong(number.ToLocalChecked());
			}
		}
	}
	env->SetLongArrayRegion(javaLongArray, 0, arrayLength, longBuffer);

	return javaLongArray;
}

jfloatArray TypeConverter::jsArrayToJavaFloatArray(Isolate* isolate, Local<Array> jsArray)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (env == NULL) {
		return NULL;
	}
	return TypeConverter::jsArrayToJavaFloatArray(isolate, env, jsArray);
}

jfloatArray TypeConverter::jsArrayToJavaFloatArray(Isolate* isolate, JNIEnv *env, Local<Array> jsArray)
{
	int arrayLength = jsArray->Length();
	jfloatArray javaFloatArray = env->NewFloatArray(arrayLength);
	if (javaFloatArray == NULL) {
		LOGE(TAG, "unable to create new jfloatArray");
		return NULL;
	}

	Local<Context> context = isolate->GetCurrentContext();
	jfloat* floatBuffer = new jfloat[arrayLength];
	for (int i = 0; i < arrayLength; i++) {
		MaybeLocal<Value> element = jsArray->Get(context, i);
		if (element.IsEmpty()) {
			LOGE(TAG, "Failed to get element at index %d, inserting 0", i);
			floatBuffer[i] = 0;
		} else {
			MaybeLocal<Number> number = element.ToLocalChecked()->ToNumber(context);
			if (element.IsEmpty()) {
				LOGE(TAG, "Failed to coerce element at index %d into a Number, inserting 0", i);
				floatBuffer[i] = 0;
			} else {
				floatBuffer[i] = TypeConverter::jsNumberToJavaFloat(number.ToLocalChecked());
			}
		}
	}
	env->SetFloatArrayRegion(javaFloatArray, 0, arrayLength, floatBuffer);

	return javaFloatArray;
}

Local<Array> TypeConverter::javaArrayToJsArray(Isolate* isolate, jlongArray javaLongArray)
{
	return javaLongArrayToJsNumberArray(isolate, javaLongArray);
}

Local<Array> TypeConverter::javaArrayToJsArray(Isolate* isolate, JNIEnv *env, jlongArray javaLongArray)
{
	return javaLongArrayToJsNumberArray(isolate, env, javaLongArray);
}

Local<Array> TypeConverter::javaArrayToJsArray(Isolate* isolate, jfloatArray javaFloatArray)
{
	return javaFloatArrayToJsNumberArray(isolate, javaFloatArray);
}

Local<Array> TypeConverter::javaArrayToJsArray(Isolate* isolate, JNIEnv *env, jfloatArray javaFloatArray)
{
	return javaFloatArrayToJsNumberArray(isolate, env, javaFloatArray);
}

jdoubleArray TypeConverter::jsArrayToJavaDoubleArray(Isolate* isolate, Local<Array> jsArray)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (env == NULL) {
		return NULL;
	}
	return TypeConverter::jsArrayToJavaDoubleArray(isolate, env, jsArray);
}

jdoubleArray TypeConverter::jsArrayToJavaDoubleArray(Isolate* isolate, JNIEnv *env, Local<Array> jsArray)
{
	int arrayLength = jsArray->Length();
	jdoubleArray javaDoubleArray = env->NewDoubleArray(arrayLength);
	if (javaDoubleArray == NULL) {
		LOGE(TAG, "unable to create new jdoubleArray");
		return NULL;
	}

	Local<Context> context = isolate->GetCurrentContext();
	jdouble* doubleBuffer = new jdouble[arrayLength];
	for (int i = 0; i < arrayLength; i++) {
		MaybeLocal<Value> element = jsArray->Get(context, i);
		if (element.IsEmpty()) {
			LOGE(TAG, "Failed to get element at index %d, inserting 0", i);
			doubleBuffer[i] = 0;
		} else {
			MaybeLocal<Number> number = element.ToLocalChecked()->ToNumber(context);
			if (element.IsEmpty()) {
				LOGE(TAG, "Failed to coerce element at index %d into a Number, inserting 0", i);
				doubleBuffer[i] = 0;
			} else {
				doubleBuffer[i] = TypeConverter::jsNumberToJavaDouble(number.ToLocalChecked());
			}
		}
	}
	env->SetDoubleArrayRegion(javaDoubleArray, 0, arrayLength, doubleBuffer);

	return javaDoubleArray;
}

Local<Array> TypeConverter::javaArrayToJsArray(Isolate* isolate, jdoubleArray javaDoubleArray)
{
	return javaDoubleArrayToJsNumberArray(isolate, javaDoubleArray);
}

Local<Array> TypeConverter::javaArrayToJsArray(Isolate* isolate, JNIEnv *env, jdoubleArray javaDoubleArray)
{
	return javaDoubleArrayToJsNumberArray(isolate, env, javaDoubleArray);
}

Local<Array> TypeConverter::javaArrayToJsArray(Isolate* isolate, jobjectArray javaObjectArray)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (env == NULL) {
		return Array::New(isolate);
	}
	return TypeConverter::javaArrayToJsArray(isolate, env, javaObjectArray);
}

Local<Array> TypeConverter::javaArrayToJsArray(Isolate* isolate, JNIEnv *env, jobjectArray javaObjectArray)
{
	int arrayLength = env->GetArrayLength(javaObjectArray);
	Local<Array> jsArray = Array::New(isolate, arrayLength);

	Local<Context> context = isolate->GetCurrentContext();
	for (int i = 0; i < arrayLength; i++) {
		jobject javaArrayElement = env->GetObjectArrayElement(javaObjectArray, i);
		Local<Value> jsArrayElement = TypeConverter::javaObjectToJsValue(isolate, env, javaArrayElement);
		jsArray->Set(context, (uint32_t) i, jsArrayElement);
		env->DeleteLocalRef(javaArrayElement);
	}

	return jsArray;
}

// converts js value to java object and recursively converts sub objects if this
// object is a container type
jobject TypeConverter::jsValueToJavaObject(Isolate* isolate, Local<Value> jsValue, bool *isNew)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (env == NULL) {
		return NULL;
	}
	return TypeConverter::jsValueToJavaObject(isolate, env, jsValue, isNew);
}

jobject TypeConverter::jsValueToJavaObject(Isolate* isolate, JNIEnv *env, Local<Value> jsValue, bool *isNew)
{
	if (jsValue->IsNumber()) {
		*isNew = true;
		Local<Number> number = jsValue.As<Number>();
		if (jsValue->IsInt32()) {
			jint javaInt = TypeConverter::jsNumberToJavaInt(number);
			return env->NewObject(JNIUtil::integerClass, JNIUtil::integerInitMethod, javaInt);
		}
		jdouble javaDouble = TypeConverter::jsNumberToJavaDouble(number);
		return env->NewObject(JNIUtil::doubleClass, JNIUtil::doubleInitMethod, javaDouble);

	} else if (jsValue->IsBoolean()) {
		jboolean javaBoolean = TypeConverter::jsBooleanToJavaBoolean(jsValue.As<Boolean>());
		*isNew = true;
		return env->NewObject(JNIUtil::booleanClass, JNIUtil::booleanInitMethod, javaBoolean);

	} else if (jsValue->IsString()) {
		*isNew = true;
		return TypeConverter::jsStringToJavaString(isolate, env, jsValue.As<String>());

	} else if (jsValue->IsDate()) {
		return TypeConverter::jsDateToJavaDate(env, jsValue.As<Date>());

	} else if (jsValue->IsArray()) {
		*isNew = true;
		return TypeConverter::jsArrayToJavaArray(isolate, env, jsValue.As<Array>());

	} else if (jsValue->IsFunction()) {
		*isNew = true;
		return TypeConverter::jsObjectToJavaFunction(isolate, env, jsValue.As<Function>());

	} else if (jsValue->IsObject()) {
		Local<Object> jsObject = jsValue.As<Object>();

		if (JavaObject::isJavaObject(jsObject)) {
			*isNew = true;
			JavaObject *javaObject = JavaObject::Unwrap<JavaObject>(jsObject);
			return javaObject->getJavaObject();
		} else {

			Local<Context> context = isolate->GetCurrentContext();
			// Unwrap hyperloop JS wrappers to get native java proxy
			Local<String> nativeString = STRING_NEW(isolate, "$native");
			if (jsObject->HasOwnProperty(context, nativeString).FromMaybe(false)) {

				TryCatch tryCatch(isolate);
				Local<Value> nativeObject;
				MaybeLocal<Value> maybeNativeObject = jsObject->GetRealNamedProperty(context, nativeString);
				if (!maybeNativeObject.ToLocal(&nativeObject)) {
					V8Util::fatalException(isolate, tryCatch);
					return NULL;
				}

				MaybeLocal<Object> maybeObject = nativeObject->ToObject(context);
				if (!maybeObject.ToLocal(&jsObject)) {
					V8Util::fatalException(isolate, tryCatch);
					return NULL;
				}

				if (JavaObject::isJavaObject(jsObject)) {
					*isNew = true;
					JavaObject *javaObject = JavaObject::Unwrap<JavaObject>(jsObject);
					return javaObject->getJavaObject();
				}
			}

			// FIXME Handle when empty!
			Local<Array> objectKeys = jsObject->GetOwnPropertyNames(context).ToLocalChecked();
			int numKeys = objectKeys->Length();
			*isNew = true;
			jobject javaHashMap = env->NewObject(JNIUtil::hashMapClass, JNIUtil::hashMapInitMethod, numKeys);

			for (int i = 0; i < numKeys; i++) {
				// FIXME Handle when empty!
				Local<Value> jsObjectPropertyKey = objectKeys->Get(context, (uint32_t) i).ToLocalChecked();
				bool valueIsNew;
				jstring javaStringPropertyKey = TypeConverter::jsValueToJavaString(isolate, env, jsObjectPropertyKey);
				// FIXME Handle when empty!
				Local<Value> jsObjectPropertyValue = jsObject->Get(context, jsObjectPropertyKey).ToLocalChecked();
				jobject javaObjectPropertyValue = TypeConverter::jsValueToJavaObject(isolate, env, jsObjectPropertyValue, &valueIsNew);

				jobject result = env->CallObjectMethod(javaHashMap,
				                                       JNIUtil::hashMapPutMethod,
				                                       javaStringPropertyKey,
				                                       javaObjectPropertyValue);
				env->DeleteLocalRef(result);
				env->DeleteLocalRef(javaStringPropertyKey);

				if (valueIsNew) {
					env->DeleteLocalRef(javaObjectPropertyValue);
				}
			}

			return javaHashMap;
		}
	}

	if (!jsValue->IsNull() && !jsValue->IsUndefined()) {
		LOGW(TAG, "jsValueToJavaObject returning null.");
	}
	return NULL;
}

// converts js object to kroll dict and recursively converts sub objects if this
// object is a container type
jobject TypeConverter::jsObjectToJavaKrollDict(Isolate* isolate, Local<Value> jsValue, bool *isNew)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (env == NULL) {
		return NULL;
	}
	return TypeConverter::jsObjectToJavaKrollDict(isolate, env,jsValue,isNew);
}

jobject TypeConverter::jsObjectToJavaKrollDict(Isolate* isolate, JNIEnv *env, Local<Value> jsValue, bool *isNew)
{
	if (jsValue->IsObject())
	{
		Local<Context> context = isolate->GetCurrentContext();
		Local<Object> jsObject = jsValue.As<Object>();
		// FIXME Handle when empty/getting own property names fails!
		Local<Array> objectKeys = jsObject->GetOwnPropertyNames(context).ToLocalChecked();
		int numKeys = objectKeys->Length();
		*isNew = true;
		jobject javaKrollDict = env->NewObject(JNIUtil::krollDictClass, JNIUtil::krollDictInitMethod, numKeys);

		for (int i = 0; i < numKeys; i++) {
			// FIXME Handle when empty!
			Local<Value> jsObjectPropertyKey = objectKeys->Get(context, (uint32_t) i).ToLocalChecked();
			bool valueIsNew;
			jstring javaStringPropertyKey = TypeConverter::jsValueToJavaString(isolate, env, jsObjectPropertyKey);
			// FIXME Handle when empty!
			Local<Value> jsObjectPropertyValue = jsObject->Get(context, jsObjectPropertyKey).ToLocalChecked();
			jobject javaObjectPropertyValue = TypeConverter::jsValueToJavaObject(isolate, env, jsObjectPropertyValue, &valueIsNew);

			jobject result = env->CallObjectMethod(javaKrollDict,
				                                   JNIUtil::krollDictPutMethod,
				                                   javaStringPropertyKey,
				                                   javaObjectPropertyValue);
			env->DeleteLocalRef(result);
			env->DeleteLocalRef(javaStringPropertyKey);

			if (valueIsNew) {
				env->DeleteLocalRef(javaObjectPropertyValue);
			}
		}

		return javaKrollDict;
	}

	if (!jsValue->IsNull() && !jsValue->IsUndefined()) {
		LOGW(TAG, "jsObjectToJavaKrollDict returning null.");
	}
	return NULL;
}


// converts js value to java error
jobject TypeConverter::jsValueToJavaError(Isolate* isolate, Local<Value> jsValue, bool* isNew)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (env == NULL) {
		return NULL;
	}
	return TypeConverter::jsValueToJavaError(isolate, env, jsValue, isNew);
}

jobject TypeConverter::jsValueToJavaError(Isolate* isolate, JNIEnv *env, Local<Value> jsValue, bool* isNew)
{
	if (jsValue->IsObject()) {
		Local<Object> jsObject = jsValue.As<Object>();

		// If it's a java object, we just return null for now.
		if (!JavaObject::isJavaObject(jsObject)) {

			Local<Context> context = isolate->GetCurrentContext();
			Local<String> stackString = STRING_NEW(isolate, "stack");
			Local<String> messageString = STRING_NEW(isolate, "message");
			if (jsObject->HasOwnProperty(context, stackString).FromMaybe(false) || jsObject->HasOwnProperty(context, messageString).FromMaybe(false)) {
				*isNew = true;

				// Potentially empty, default to Null so we return Java NULL to KrollException
				Local<Value> jsObjectMessageProperty = jsObject->GetRealNamedProperty(context, messageString).FromMaybe(Null(isolate).As<Value>());
				Local<Value> jsObjectStackProperty = jsObject->GetRealNamedProperty(context, stackString).FromMaybe(Null(isolate).As<Value>());

				return env->NewObject(JNIUtil::krollExceptionClass, JNIUtil::krollExceptionInitMethod,
							TypeConverter::jsValueToJavaString(isolate, env, jsObjectMessageProperty), TypeConverter::jsValueToJavaString(isolate, env, jsObjectStackProperty));
			}
		}

	} else  {
		*isNew = true;
		return env->NewObject(JNIUtil::krollExceptionClass, JNIUtil::krollExceptionInitMethod,
			TypeConverter::jsValueToJavaString(isolate, env, jsValue), NULL);
	}

	if (!jsValue->IsNull() && !jsValue->IsUndefined()) {
		LOGW(TAG, "jsValueToJavaObject returning null.");
	}
	return NULL;
}

// converts java hashmap to js value and recursively converts sub objects if this
// object is a container type. If javaObject is NULL, an empty object is created.
Local<Object> TypeConverter::javaHashMapToJsValue(Isolate* isolate, JNIEnv *env, jobject javaObject)
{
	Local<Object> jsObject = Object::New(isolate);
	if (!javaObject || !env) {
		return jsObject;
	}

	jobject hashMapSet = env->CallObjectMethod(javaObject, JNIUtil::hashMapKeySetMethod);
	jobjectArray hashMapKeys = (jobjectArray) env->CallObjectMethod(hashMapSet, JNIUtil::setToArrayMethod);
	env->DeleteLocalRef(hashMapSet);

	int hashMapKeysLength = env->GetArrayLength(hashMapKeys);
	bool isStringHashMap = env->IsInstanceOf(hashMapKeys, JNIUtil::stringArrayClass);

	for (int i = 0; i < hashMapKeysLength; i++) {
		jobject javaPairKey = env->GetObjectArrayElement(hashMapKeys, i);
		Local<Value> jsPairKey;
		if (isStringHashMap) {
			jsPairKey = TypeConverter::javaStringToJsString(isolate, env, (jstring)javaPairKey);
		} else {
			jsPairKey = TypeConverter::javaObjectToJsValue(isolate, env, javaPairKey);
		}

		jobject javaPairValue = env->CallObjectMethod(javaObject, JNIUtil::hashMapGetMethod, javaPairKey);
		env->DeleteLocalRef(javaPairKey);

		jsObject->Set(jsPairKey, TypeConverter::javaObjectToJsValue(isolate, env, javaPairValue));
		env->DeleteLocalRef(javaPairValue);
	}

	env->DeleteLocalRef(hashMapKeys);

	return jsObject;
}

// converts java object to js value and recursively converts sub objects if this
// object is a container type
Local<Value> TypeConverter::javaObjectToJsValue(Isolate* isolate, jobject javaObject)
{
	if (!javaObject) {
		return Null(isolate);
	}

	JNIEnv *env = JNIUtil::getJNIEnv();
	if (!env) {
		return Undefined(isolate);
	}
	return TypeConverter::javaObjectToJsValue(isolate, env, javaObject);
}

Local<Value> TypeConverter::javaObjectToJsValue(Isolate* isolate, JNIEnv *env, jobject javaObject)
{
	if (!javaObject) {
		return Null(isolate);
	}

	if (env->IsInstanceOf(javaObject, JNIUtil::booleanClass)) {
		jboolean javaBoolean = env->CallBooleanMethod(javaObject, JNIUtil::booleanBooleanValueMethod);
		return javaBoolean ? True(isolate) : False(isolate);

	} else if (env->IsInstanceOf(javaObject, JNIUtil::numberClass)) {
		jdouble javaDouble = env->CallDoubleMethod(javaObject, JNIUtil::numberDoubleValueMethod);
		return Number::New(isolate, (double) javaDouble);

	} else if (env->IsInstanceOf(javaObject, JNIUtil::stringClass)) {
		return TypeConverter::javaStringToJsString(isolate, env, (jstring) javaObject);

	} else if (env->IsInstanceOf(javaObject, JNIUtil::dateClass)) {
		return TypeConverter::javaDateToJsDate(isolate, env, javaObject);

	} else if (env->IsInstanceOf(javaObject, JNIUtil::hashMapClass)) {
		return TypeConverter::javaHashMapToJsValue(isolate, env, javaObject);
	} else if (env->IsInstanceOf(javaObject, JNIUtil::krollProxyClass)) {
		jobject krollObject = env->GetObjectField(javaObject, JNIUtil::krollProxyKrollObjectField);
		if (krollObject && env->IsInstanceOf(krollObject, JNIUtil::v8ObjectClass)) {
			jlong v8ObjectPointer = env->GetLongField(krollObject, JNIUtil::v8ObjectPtrField);
			env->DeleteLocalRef(krollObject);

			if (v8ObjectPointer != 0) {
				titanium::Proxy* proxy = (titanium::Proxy*) v8ObjectPointer;
				Local<Object> v8Object = proxy->handle(isolate);
				// This is an ugly HACK
				// We're basically just temporarily calling ClearWeak and MakeWeak again hoping to extend the lifetime of this object
				// so it doesn't get GC'd
				jobject javaProxy = proxy->getJavaObject();
				proxy->unreferenceJavaObject(javaProxy);
				return v8Object;
			}
		}

		jclass javaObjectClass = env->GetObjectClass(javaObject);
		Local<Object> proxyHandle = ProxyFactory::createV8Proxy(isolate, javaObjectClass, javaObject);
		env->DeleteLocalRef(javaObjectClass);
		return proxyHandle;

	} else if (env->IsInstanceOf(javaObject, JNIUtil::v8FunctionClass)) {
		return javaObjectToJsFunction(isolate, javaObject);

	} else if (env->IsInstanceOf(javaObject, JNIUtil::objectArrayClass)) {
		return javaArrayToJsArray(isolate, (jobjectArray) javaObject);

	} else if (env->IsInstanceOf(javaObject, JNIUtil::shortArrayClass)) {
		return javaArrayToJsArray(isolate, (jshortArray) javaObject);

	} else if (env->IsInstanceOf(javaObject, JNIUtil::intArrayClass)) {
		return javaArrayToJsArray(isolate, (jintArray) javaObject);

	} else if (env->IsInstanceOf(javaObject, JNIUtil::longArrayClass)) {
		return javaArrayToJsArray(isolate, (jlongArray) javaObject);

	} else if (env->IsInstanceOf(javaObject, JNIUtil::floatArrayClass)) {
		return javaArrayToJsArray(isolate, (jfloatArray) javaObject);

	} else if (env->IsInstanceOf(javaObject, JNIUtil::doubleArrayClass)) {
		return javaArrayToJsArray(isolate, (jdoubleArray) javaObject);

	} else if (env->IsInstanceOf(javaObject, JNIUtil::booleanArrayClass)) {
		return javaArrayToJsArray(isolate, (jbooleanArray) javaObject);

	} else if (env->IsInstanceOf(javaObject, JNIUtil::throwableClass)) {
		return javaThrowableToJSError(isolate, (jthrowable) javaObject);

	} else if (env->IsSameObject(JNIUtil::undefinedObject, javaObject)) {
		return Undefined(isolate);
	}

	jclass javaObjectClass = env->GetObjectClass(javaObject);
	JNIUtil::logClassName("!!! Unable to convert unknown Java object class '%s' to JS value !!!", javaObjectClass, true);
	env->DeleteLocalRef(javaObjectClass);

	return Undefined(isolate);
}

jobjectArray TypeConverter::jsObjectIndexPropsToJavaArray(Isolate* isolate, Local<Object> jsObject, int start, int length)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (!env) {
		return NULL;
	}
	return TypeConverter::jsObjectIndexPropsToJavaArray(isolate, env, jsObject, start, length);
}

jobjectArray TypeConverter::jsObjectIndexPropsToJavaArray(Isolate* isolate, JNIEnv *env, Local<Object> jsObject, int start, int length)
{
	HandleScope scope(isolate);

	int arrayLength = length == 0 ? 0 : length - start;
	jobjectArray javaArray = env->NewObjectArray(arrayLength, JNIUtil::objectClass, NULL);
	int index = 0;

	Local<Context> context = isolate->GetCurrentContext();
	for (int index = start; index < length; ++index) {
		MaybeLocal<Value> prop = jsObject->Get(context, index);
		if (prop.IsEmpty()) {
			LOGE(TAG, "Failed to get element at index %d, inserting NULL", index);
			env->SetObjectArrayElement(javaArray, index - start, NULL);
		} else {
			bool isNew;
			jobject javaObject = jsValueToJavaObject(isolate, prop.ToLocalChecked(), &isNew);
			env->SetObjectArrayElement(javaArray, index - start, javaObject);
			if (isNew) {
				env->DeleteLocalRef(javaObject);
			}
		}
	}

	return javaArray;
}

/****************************** private methods ******************************/

// used mainly by the array conversion methods when converting java numeric types
// arrays to to the generic js number type
Local<Array> TypeConverter::javaDoubleArrayToJsNumberArray(Isolate* isolate, jdoubleArray javaDoubleArray)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (env == NULL) {
		return Array::New(isolate);
	}
	return TypeConverter::javaDoubleArrayToJsNumberArray(isolate, env, javaDoubleArray);
}

Local<Array> TypeConverter::javaDoubleArrayToJsNumberArray(Isolate* isolate, JNIEnv *env, jdoubleArray javaDoubleArray)
{
	int arrayLength = env->GetArrayLength(javaDoubleArray);
	Local<Array> jsArray = Array::New(isolate, arrayLength);

	Local<Context> context = isolate->GetCurrentContext();
	jdouble *arrayElements = env->GetDoubleArrayElements(javaDoubleArray, 0);
	for (int i = 0; i < arrayLength; i++) {
		jsArray->Set(context, (uint32_t) i, Number::New(isolate, arrayElements[i]));
	}
	env->ReleaseDoubleArrayElements(javaDoubleArray, arrayElements, JNI_ABORT);
	//Since we were only reading, there is no need to copy back. Thus, Abort.
	return jsArray;
}

Local<Array> TypeConverter::javaLongArrayToJsNumberArray(Isolate* isolate, jlongArray javaLongArray)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (env == NULL) {
		return Array::New(isolate);
	}
	return TypeConverter::javaLongArrayToJsNumberArray(isolate, env, javaLongArray);
}

Local<Array> TypeConverter::javaLongArrayToJsNumberArray(Isolate* isolate, JNIEnv *env, jlongArray javaLongArray)
{
	int arrayLength = env->GetArrayLength(javaLongArray);
	Local<Array> jsArray = Array::New(isolate, arrayLength);

	Local<Context> context = isolate->GetCurrentContext();
	jlong *arrayElements = env->GetLongArrayElements(javaLongArray, 0);
	for (int i = 0; i < arrayLength; i++) {
		jsArray->Set(context, (uint32_t) i, Number::New(isolate, arrayElements[i]));
	}
	return jsArray;
}

Local<Array> TypeConverter::javaFloatArrayToJsNumberArray(Isolate* isolate, jfloatArray javaFloatArray)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (env == NULL) {
		return Array::New(isolate);
	}
	return TypeConverter::javaFloatArrayToJsNumberArray(isolate, env, javaFloatArray);
}

Local<Array> TypeConverter::javaFloatArrayToJsNumberArray(Isolate* isolate, JNIEnv *env, jfloatArray javaFloatArray)
{
	int arrayLength = env->GetArrayLength(javaFloatArray);
	Local<Array> jsArray = Array::New(isolate, arrayLength);

	Local<Context> context = isolate->GetCurrentContext();
	jfloat *arrayElements = env->GetFloatArrayElements(javaFloatArray, 0);
	for (int i = 0; i < arrayLength; i++) {
		jsArray->Set(context, (uint32_t) i, Number::New(isolate, arrayElements[i]));
	}
	env->ReleaseFloatArrayElements(javaFloatArray, arrayElements, JNI_ABORT);
	return jsArray;
}

Local<Array> TypeConverter::javaShortArrayToJsNumberArray(Isolate* isolate, jshortArray javaShortArray)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (env == NULL) {
		return Array::New(isolate);
	}
	return TypeConverter::javaShortArrayToJsNumberArray(isolate, env, javaShortArray);
}

Local<Array> TypeConverter::javaShortArrayToJsNumberArray(Isolate* isolate, JNIEnv *env, jshortArray javaShortArray)
{
	int arrayLength = env->GetArrayLength(javaShortArray);
	Local<Array> jsArray = Array::New(isolate, arrayLength);

	Local<Context> context = isolate->GetCurrentContext();
	jshort *arrayElements = env->GetShortArrayElements(javaShortArray, 0);
	for (int i = 0; i < arrayLength; i++) {
		jsArray->Set(context, (uint32_t) i, Number::New(isolate, arrayElements[i]));
	}
	env->ReleaseShortArrayElements(javaShortArray, arrayElements, JNI_ABORT);
	return jsArray;
}

Local<Object> TypeConverter::javaThrowableToJSError(Isolate* isolate, jthrowable javaException)
{
	JNIEnv *env = JNIScope::getEnv();
	if (env == NULL) {
		return Local<Object>();
	}
	return TypeConverter::javaThrowableToJSError(isolate, env, javaException);
}

Local<Object> TypeConverter::javaThrowableToJSError(v8::Isolate* isolate, JNIEnv *env, jthrowable javaException)
{
	// Grab the top-level error message
	jstring javaMessage = (jstring) env->CallObjectMethod(javaException, JNIUtil::throwableGetMessageMethod);
	Local<Value> message;
	if (!javaMessage) {
		message = STRING_NEW(isolate, "Unknown Java Exception occurred");
	} else {
		message = TypeConverter::javaStringToJsString(isolate, env, javaMessage);
		env->DeleteLocalRef(javaMessage);
	}

	// Create a JS Error holding this message
	// We use .As<String> here because we know that the return value of TypeConverter::javaStringToJsString
	// must be a String. Only other variant is Null when the javaMessage is null, which we already checked for above.
	// We use .As<Object> on Error because an Error is an Object.
	Local<Object> error = Exception::Error(message.As<String>()).As<Object>();

	// Now loop through the java stack and generate a JS String from the result and assign to Local<String> stack
	std::stringstream stackStream;
	jobjectArray frames = (jobjectArray) env->CallObjectMethod(javaException, JNIUtil::throwableGetStackTraceMethod);
	jsize frames_length = env->GetArrayLength(frames);
	for (int i = 0; i < (frames_length > MAX_STACK ? MAX_STACK : frames_length); i++) {
		jobject frame = env->GetObjectArrayElement(frames, i);
		jstring javaStack = (jstring) env->CallObjectMethod(frame, JNIUtil::stackTraceElementToStringMethod);

		const char* stackPtr = env->GetStringUTFChars(javaStack, NULL);
		stackStream << std::endl << "    " << stackPtr;

		env->ReleaseStringUTFChars(javaStack, stackPtr);
		env->DeleteLocalRef(javaStack);
	}
	stackStream << std::endl;

	Local<Context> context = isolate->GetCurrentContext();

	// Now explicitly assign our properly generated stacktrace
	Local<String> javaStack = String::NewFromUtf8(isolate, stackStream.str().c_str());
	error->Set(context, STRING_NEW(isolate, "nativeStack"), javaStack);

	// If we're using our custom error interface we can ask for a map of additional properties ot set on the JS Error
	if (env->IsInstanceOf(javaException, JNIUtil::jsErrorClass)) {
		jobject customProps = (jobject) env->CallObjectMethod(javaException, JNIUtil::getJSPropertiesMethod);
		if (customProps) {
			// Grab the custom properties
			Local<Object> props = TypeConverter::javaHashMapToJsValue(isolate, env, customProps);
			env->DeleteLocalRef(customProps);

			// Copy properties over to the JS Error!
			Local<Array> objectKeys = props->GetOwnPropertyNames(context).ToLocalChecked();
			int numKeys = objectKeys->Length();
			for (int i = 0; i < numKeys; i++) {
				// FIXME: Handle when empty!
				Local<Value> jsObjectPropertyKey = objectKeys->Get(context, (uint32_t) i).ToLocalChecked();
				Local<String> keyName = jsObjectPropertyKey.As<String>();

				Local<Value> jsObjectPropertyValue = props->Get(context, jsObjectPropertyKey).ToLocalChecked();
				error->Set(context, keyName, jsObjectPropertyValue);
			}
		}
	}

	return error;
}
