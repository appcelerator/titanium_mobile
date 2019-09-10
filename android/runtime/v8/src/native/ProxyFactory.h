/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-2016 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#ifndef PROXY_FACTORY_H
#define PROXY_FACTORY_H

#include <jni.h>
#include <v8.h>

namespace titanium {

// Titanium is split across two runtime environments: Java and V8.
// Code must be exposed on both sides of the "bridge". To accomplish this
// we create "proxy" objects on both sides.
//
//   V8 Proxy  <----> [V8-JNI bridge] <--> Java Proxy
//
// This factory helps in the construction of this proxy pair.
// The creation can be started on either the V8 or Java sides.
// Two "create" function are provided for both cases.
class ProxyFactory
{
public:

	static v8::Local<v8::Object> createV8Proxy(v8::Isolate* isolate, jclass javaClass, jobject javaProxy);

	/**
	 * Creates a proxy on the V8 side given an existing Java proxy.
	 * @param  isolate   The current V8 Isolate
	 * @param  className The name of the Java class of the object we're wrapping
	 * @param  javaProxy The actual Java object we're wrapping
	 * @return           A JS Object that wraps/holds the Java object within
	 */
	static v8::Local<v8::Object> createV8Proxy(v8::Isolate* isolate, v8::Local<v8::Value> className, jobject javaProxy);

	/**
	 * Creates a proxy on the Java side given an existing V8 proxy.
	 * @param  javaClass [description]
	 * @param  v8Proxy   [description]
	 * @param  args      [description]
	 * @return           [description]
	 */
	static jobject createJavaProxy(jclass javaClass, v8::Local<v8::Object> v8Proxy, const v8::FunctionCallbackInfo<v8::Value>& args);

	/**
	 * Given a jclass, get the name of the class and return it as a Local<Value>
	 * (using typical java.lang.Names, rather than jni slash separated names)
	 * @param javaClass
	 */
	static v8::Local<v8::Value> getJavaClassName(v8::Isolate* isolate, jclass javaClass);

	static void dispose(v8::Isolate* isolate);
};

}

#endif
