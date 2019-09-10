/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifndef JNI_UTIL_H
#define JNI_UTIL_H

#include <jni.h>
#include <stdint.h>
#include "AndroidUtil.h"

#define JNIENV_GET_ERROR_MSG "Unable to get current JNI environment."
#define LOG_JNIENV_GET_ERROR(tag) \
	LOGE(tag, JNIENV_GET_ERROR_MSG)

namespace titanium {
class JNIUtil
{
public:
	static JavaVM *javaVm;
	static JNIEnv* getJNIEnv();
	static void terminateVM();
	static void initCache();

	/**
	 * Looks up a Java class using java/lang/Class JNI naming style
	 * NOTE THAT THIS WILL RETURN A GLOBALLY REFERENCED CLASS!!!
	 * CALLERS SHOULD CALL JNIEnv->DeleteGlobalRef() to clean up once they've consumed the class!
	 * @param  className [description]
	 * @return           [description]
	 */
	static jclass findClass(const char *className);

	static jmethodID getMethodID(jclass javaClass, const char *methodName, const char *signature, bool isStatic = false);
	static jfieldID getFieldID(jclass javaClass, const char *fieldName, const char *signature);

	/**
	 * Gets the Java class name as a jstring in the java.lang.Class style
	 * @param  javaClass [description]
	 * @return           [description]
	 */
	static jstring getClassName(jclass javaClass);

	/**
	 * Logs the Java class name in java.lang.Class style
	 * @param format     [description]
	 * @param javaClass  [description]
	 * @param errorLevel [description]
	 */
	static void logClassName(const char *format, jclass javaClass, bool errorLevel = false);

	/**
	 * If the object is an V8Object, set the internal pointer to 0 to indicate the C++ proxy is deleted
	 * @param obj [description]
	 */
	static bool removePointer(jobject obj);

	static jobjectArray newObjectArray(int length, jobject initial = NULL);
	static void throwException(jclass clazz, const char *message);
	static void throwException(const char *className, const char *message);
	static void throwOutOfMemoryError(const char *message);
	static void throwNullPointerException(const char *message);

	static jobject undefinedObject;

	// Java classes
	static jclass classClass;
	static jclass objectClass;
	static jclass stringClass;
	static jclass numberClass;
	static jclass shortClass;
	static jclass integerClass;
	static jclass longClass;
	static jclass floatClass;
	static jclass doubleClass;
	static jclass booleanClass;
	static jclass stringArrayClass;
	static jclass objectArrayClass;
	static jclass shortArrayClass;
	static jclass intArrayClass;
	static jclass longArrayClass;
	static jclass floatArrayClass;
	static jclass doubleArrayClass;
	static jclass booleanArrayClass;

	static jclass arrayListClass;
	static jclass hashMapClass;
	static jclass dateClass;
	static jclass setClass;
	static jclass outOfMemoryError;
	static jclass throwableClass;
	static jclass stackTraceElementClass;
	static jclass nullPointerException;

	// Titanium classes
	static jclass v8PromiseClass;
	static jclass v8ObjectClass;
	static jclass v8FunctionClass;
	static jclass krollRuntimeClass;
	static jclass krollInvocationClass;
	static jclass krollExceptionClass;
	static jclass krollObjectClass;
	static jclass krollProxyClass;
	static jclass krollAssetHelperClass;
	static jclass krollLoggingClass;
	static jclass krollDictClass;
	static jclass tiJsErrorDialogClass;
	static jclass referenceTableClass;
	static jclass jsErrorClass;
	static jclass krollWorkerClass;

	// Java methods
	static jmethodID classGetNameMethod;
	static jmethodID arrayListInitMethod;
	static jmethodID arrayListAddMethod;
	static jmethodID arrayListGetMethod;
	static jmethodID arrayListRemoveMethod;
	static jmethodID hashMapInitMethod;
	static jmethodID hashMapGetMethod;
	static jmethodID hashMapPutMethod;
	static jmethodID hashMapKeySetMethod;
	static jmethodID hashMapRemoveMethod;
	static jmethodID setToArrayMethod;
	static jmethodID dateInitMethod;
	static jmethodID dateGetTimeMethod;
	static jmethodID integerInitMethod;
	static jmethodID doubleInitMethod;
	static jmethodID booleanInitMethod;
	static jmethodID booleanBooleanValueMethod;
	static jmethodID longInitMethod;
	static jmethodID numberDoubleValueMethod;
	static jmethodID throwableGetMessageMethod;
	static jmethodID throwableGetStackTraceMethod;
	static jmethodID stackTraceElementToStringMethod;

	// Titanium methods and fields
	static jfieldID v8ObjectPtrField;
	static jmethodID v8PromiseInitMethod;
	static jmethodID v8ObjectInitMethod;
	static jmethodID v8FunctionInitMethod;

	// KrollDict
	static jmethodID krollDictInitMethod;
	static jmethodID krollDictPutMethod;

	// ReferenceTable
	static jmethodID referenceTableCreateReferenceMethod;
	static jmethodID referenceTableDestroyReferenceMethod;
	static jmethodID referenceTableMakeWeakReferenceMethod;
	static jmethodID referenceTableMakeSoftReferenceMethod;
	static jmethodID referenceTableClearReferenceMethod;
	static jmethodID referenceTableGetReferenceMethod;
	static jmethodID referenceTableIsStrongReferenceMethod;

	// KrollRuntime
	static jint krollRuntimeDontIntercept;
	static jmethodID krollRuntimeDispatchExceptionMethod;

	static jmethodID krollInvocationInitMethod;
	static jmethodID krollExceptionInitMethod;

	// KrollObject
	static jfieldID krollObjectProxySupportField;
	static jmethodID krollObjectSetHasListenersForEventTypeMethod;
	static jmethodID krollObjectOnEventFiredMethod;

	// KrollProxy
	static jmethodID krollProxyCreateProxyMethod;
	static jfieldID krollProxyKrollObjectField;
	static jfieldID krollProxyModelListenerField;
	static jmethodID krollProxySetIndexedPropertyMethod;
	static jmethodID krollProxyGetIndexedPropertyMethod;
	static jmethodID krollProxyOnPropertyChangedMethod;
	static jmethodID krollProxyOnPropertiesChangedMethod;

	// KrollLogging
	static jmethodID krollLoggingLogWithDefaultLoggerMethod;
	static jmethodID krollWorkerCloseMethod;
	static jmethodID krollWorkerPostMessageMethod;

	// KrollAssetHelper
	static jmethodID krollAssetHelperReadAssetMethod;

	// CustomError
	static jmethodID getJSPropertiesMethod;

};

class JNIScope
{
public:
	JNIScope(JNIEnv *env)
			: prev(current)
	{
		JNIEnv *cur = getEnv();
		if (cur != env) {
			LOGE("JNIScope", "Error. JNIEnv %p != JNIEnv %p", cur, env);
		}
		current = env;
	}
	~JNIScope()
	{
		current = prev;
	}
	static JNIEnv* getEnv()
	{
		return current != NULL ? current : JNIUtil::getJNIEnv();
	}
private:
	JNIScope(const JNIScope&);
	void operator=(const JNIScope&);
	void* operator new(size_t size);
	void operator delete(void*, size_t);

	static JNIEnv* current;
	JNIEnv* prev;
};

}

#endif
