/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-2018 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifndef PROXY_H
#define PROXY_H

#include <map>

#include <jni.h>
#include <v8.h>

#include "JavaObject.h"

namespace titanium {

// The base class for all Titanium proxy objects.
class Proxy : public JavaObject
{
public:
	enum {
		kJavaObject = 0,
		kInternalFieldCount // Just one internal field on proxies, and it wraps the java object
	};

	static std::map<v8::Isolate *, v8::Persistent<v8::FunctionTemplate>> baseProxyTemplateMap;
	static std::map<v8::Isolate *, v8::Persistent<v8::String>> javaClassSymbolMap;
	static std::map<v8::Isolate *, v8::Persistent<v8::String>> constructorSymbolMap;
	static std::map<v8::Isolate *, v8::Persistent<v8::String>> inheritSymbolMap;
	static std::map<v8::Isolate *, v8::Persistent<v8::String>> propertiesSymbolMap;
	static std::map<v8::Isolate *, v8::Persistent<v8::String>> lengthSymbolMap;
	static std::map<v8::Isolate *, v8::Persistent<v8::String>> sourceUrlSymbolMap;
	static v8::Persistent<v8::String> inheritSymbol;
	static v8::Persistent<v8::FunctionTemplate> baseProxyTemplate;

	Proxy();

	/**
	 * Initialize the base proxy template
	 * @param exports The module exports object to hang types/methods off of.
	 * @param context The context to use.
	 */
	static void bindProxy(v8::Local<v8::Object> exports, Local<Context> context);

	/**
	 * Query the property value from the internal property map ("_properties").
	 * Proxies that have only setters in Java store the value
	 * on the JavaScript side in this map. This getter is then
	 * used when the user script requests the value.
	 * See #proxyConstructor for creation of "_properties"
	 *
	 * @param name property name
	 * @param info property callback info used to return the value
	 */
	static void getProperty(v8::Local<v8::Name> name, const v8::PropertyCallbackInfo<v8::Value>& info);
	// Used when the property is called as a function
	static void getProperty(const v8::FunctionCallbackInfo<v8::Value>& info);

	/**
	 * Stores the new value for the property into the internal map ("_properties").
	 * See #proxyConstructor for creation of "_properties"
	 *
	 * @param name  property name
	 * @param value new value
	 * @param info  property setter callback info
	 */
	static void setProperty(v8::Local<v8::Name> name,
												v8::Local<v8::Value> value,
												const v8::PropertyCallbackInfo<void>& info);

	/**
	 * Setter that reports to the Java proxy when a property has changed.
	 * Used by proxies that use accessor based properties.
	 *
	 * @param name  property name
	 * @param value new value
	 * @param info  [description]
	 */
	static void onPropertyChanged(v8::Local<v8::Name> name,
														v8::Local<v8::Value> value,
														const v8::PropertyCallbackInfo<void>& info);
	// Used when called as a function
	static void onPropertyChanged(const v8::FunctionCallbackInfo<v8::Value>& args);

	/**
	 * Fetches an indexed property value from the Java proxy.
	 *
	 * @param index [description]
	 * @param info  [description]
	 */
	static void getIndexedProperty(uint32_t index,
																 const v8::PropertyCallbackInfo<v8::Value>& info);

	/**
	 * Sets an indexed property on the Java proxy.
	 *
	 * @param index index of the property to set
	 * @param value new value
	 * @param info  property callback info
	 */
	static void setIndexedProperty(uint32_t index,
																 v8::Local<v8::Value> value,
																 const v8::PropertyCallbackInfo<v8::Value>& info);

	/**
	 * Called by EventEmitter to notify when listeners
	 * are watching for a type of event. Notifies the Java proxy when this changes.
	 * @param args [description]
	 */
	static void hasListenersForEventType(const v8::FunctionCallbackInfo<v8::Value>& args);

	/**
	 * Called by EventEmitter when we fire events from JS to Java
	 *
	 * @param args [description]
	 */
	static void onEventFired(const v8::FunctionCallbackInfo<v8::Value>& args);

	// This provides Javascript a way to extend one of our native / wrapped
	// templates without needing to know about the internal java class.
	//
	// An example of what this might look like from JS:
	// var MyProxy = Ti.UI.View.inherit(function MyView(options) {
	//     // constructor code goes here.. (optional)
	// });
	template<typename ProxyClass>
	static void inherit(const v8::FunctionCallbackInfo<v8::Value>& args)
	{
		v8::Isolate* isolate = args.GetIsolate();
		v8::HandleScope scope(isolate);
		v8::Local<v8::Function> fn = args[0].As<v8::Function>();
 		v8::Local<v8::FunctionTemplate> newType = inheritProxyTemplate(
			isolate,
			ProxyClass::getProxyTemplate(isolate),
			ProxyClass::javaClass,
			fn->GetName()->ToString(isolate), fn);
		args.GetReturnValue().Set(newType->GetFunction());
	}

	/**
	 * Inherit a built-in proxy template for use in Javascript (used by generated code)
	 */
	static v8::Local<v8::FunctionTemplate> inheritProxyTemplate(
		v8::Isolate* isolate,
		v8::Local<v8::FunctionTemplate> superTemplate,
		jclass javaClass,
		v8::Local<v8::String> className,
		v8::Local<v8::Function> callback = v8::Local<v8::Function>());

	static void dispose(v8::Isolate* isolate);

private:

	/**
	 * This is the callback used when we need to construct a native Proxy for a JS object.
	 * Here we typically:
	 * - wrap the js object in a Proxy instance
	 * - define an own property "_properties" used for #getProperty and #setProperty callbacks
	 * - Grab the Java class inside an External from the Data() value.
	 * This got set back in #inheritProxyTemplate when we generate the FunctionTemplate
	 * - attach the Java Proxy instantiated to this native Proxy.
	 * - Deal with argunents passed
	 *
	 * @param args The constructor arguments.
	 */
	static void proxyConstructor(const v8::FunctionCallbackInfo<v8::Value>& args);

	/**
	 * Callback to propagate property changes up to the Java object from #setPropertiesAndFire called in JS.
	 *
	 * @param args The function arguments.
	 */
	static void proxyOnPropertiesChanged(const v8::FunctionCallbackInfo<v8::Value>& args);

	/**
	 * Used by proxyConstructor() to create a new V8/JS object. We need a way to
	 * pass the Java object we wrap in that JS Object. This is done by passing it
	 * as an v8::External argument.
	 * @param  args The arguments received in our proxy constructor call
	 * @return      The Java object passed within the arguments (first arg, as an External)
	 */
	static jobject unwrapJavaProxy(const v8::FunctionCallbackInfo<v8::Value>& args);
};

}

#endif
