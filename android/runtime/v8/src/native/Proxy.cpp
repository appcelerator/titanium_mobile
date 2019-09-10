/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-2017 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#include <cstring>
#include <string>

#include <v8.h>
#include <unistd.h>
#include <jni.h>

#include "AndroidUtil.h"
#include "EventEmitter.h"
#include "JavaObject.h"
#include "JNIUtil.h"
#include "JSException.h"
#include "Proxy.h"
#include "ProxyFactory.h"
#include "TypeConverter.h"
#include "V8Util.h"
#include "V8Runtime.h"

#define TAG "Proxy"
#define INDEX_NAME 0
#define INDEX_OLD_VALUE 1
#define INDEX_VALUE 2

using namespace v8;

namespace titanium {

Persistent<FunctionTemplate> Proxy::baseProxyTemplate;
Persistent<String> Proxy::inheritSymbol;
std::map<Isolate *, Persistent<FunctionTemplate>> Proxy::baseProxyTemplateMap;
std::map<Isolate *, Persistent<String>> Proxy::javaClassSymbolMap;
std::map<Isolate *, Persistent<String>> Proxy::constructorSymbolMap;
std::map<Isolate *, Persistent<String>> Proxy::inheritSymbolMap;
std::map<Isolate *, Persistent<String>> Proxy::propertiesSymbolMap;
std::map<Isolate *, Persistent<String>> Proxy::lengthSymbolMap;
std::map<Isolate *, Persistent<String>> Proxy::sourceUrlSymbolMap;

Proxy::Proxy() :
	JavaObject()
{
}

void Proxy::bindProxy(Local<Object> exports, Local<Context> context)
{
	Isolate* isolate = context->GetIsolate();
	javaClassSymbolMap[isolate].Reset(isolate, NEW_SYMBOL(isolate, "__javaClass__"));
	constructorSymbolMap[isolate].Reset(isolate, NEW_SYMBOL(isolate, "constructor"));
	Local<v8::String> _inheritSymbol = NEW_SYMBOL(isolate, "inherit");
	inheritSymbolMap[isolate].Reset(isolate, _inheritSymbol);
	propertiesSymbolMap[isolate].Reset(isolate, NEW_SYMBOL(isolate, "_properties"));
	lengthSymbolMap[isolate].Reset(isolate, NEW_SYMBOL(isolate, "length"));
	sourceUrlSymbolMap[isolate].Reset(isolate, NEW_SYMBOL(isolate, "sourceUrl"));



	Local<FunctionTemplate> proxyTemplate = FunctionTemplate::New(isolate, 0, External::New(isolate, JNIUtil::krollProxyClass));
	Local<String> proxySymbol = NEW_SYMBOL(isolate, "Proxy");
	proxyTemplate->InstanceTemplate()->SetInternalFieldCount(kInternalFieldCount);
	proxyTemplate->SetClassName(proxySymbol);
	proxyTemplate->Inherit(EventEmitter::constructorTemplate[isolate].Get(isolate));

	SetProtoMethod(isolate, proxyTemplate, "_hasListenersForEventType", hasListenersForEventType);
	SetProtoMethod(isolate, proxyTemplate, "onPropertiesChanged", proxyOnPropertiesChanged);
	SetProtoMethod(isolate, proxyTemplate, "_onEventFired", onEventFired);

	baseProxyTemplateMap[isolate].Reset(isolate, proxyTemplate);

	v8::TryCatch tryCatch(isolate);
	Local<Function> constructor;
	MaybeLocal<Function> maybeConstructor = proxyTemplate->GetFunction(context);
	if (maybeConstructor.ToLocal(&constructor)) {
		exports->Set(context, proxySymbol, constructor);
	} else {
		V8Util::fatalException(isolate, tryCatch);
	}
    if (V8Runtime::runtimeThreadId == gettid()) {
        // TODO: remove on next module API breaking change
        //   grep 'titanium::Proxy::' ProxyBindingV8.cpp.fm
        baseProxyTemplate.Reset(isolate, proxyTemplate);
        inheritSymbol.Reset(isolate, _inheritSymbol);
    }
}

static Local<Value> getPropertyForProxy(Isolate* isolate, Local<Name> property, Local<Object> proxy)
{
	Local<Context> context = isolate->GetCurrentContext();
	// Call getProperty on the Proxy to get the property.
	// We define this method in JavaScript on the Proxy prototype.
	MaybeLocal<Value> maybeGetProperty = proxy->Get(context, STRING_NEW(isolate, "getProperty"));
	if (maybeGetProperty.IsEmpty()) {
		LOGE(TAG, "Unable to lookup Proxy.prototype.getProperty");
		return Undefined(isolate);
	}

	Local<Value> getProperty = maybeGetProperty.ToLocalChecked();
	if (!getProperty->IsFunction()) {
		LOGE(TAG, "Proxy.prototype.getProperty is not a Function!");
		return Undefined(isolate);
	}

	Local<Value> argv[1] = { property };
	MaybeLocal<Value> value = getProperty.As<Function>()->Call(context, proxy, 1, argv);
	return value.FromMaybe(Undefined(isolate).As<Value>());
}

// This variant is used when accessing a property in standard JS fashion (i.e. obj.text or obj['text'])
void Proxy::getProperty(Local<Name> property, const PropertyCallbackInfo<Value>& args)
{
	Isolate* isolate = args.GetIsolate();
	args.GetReturnValue().Set(getPropertyForProxy(isolate, property, args.Holder()));
}

// This variant is used when accessing a property through a getter method (i.e. obj.getText())
void Proxy::getProperty(const FunctionCallbackInfo<Value>& args)
{
	Isolate* isolate = args.GetIsolate();
	Local<Context> context = isolate->GetCurrentContext();
	// The name of the property can be passed either as
	// an argument or a data parameter.
	// Only support symbols/Strings for now. I think we handle indices differently
	Local<Name> name;
	if (args.Length() >= 1 && args[0]->IsName()) { // already String/Symbol
		name = args[0].As<Name>();
	} else if (args.Data()->IsName()) {
		name = args.Data().As<Name>();
	} else {
		JSException::Error(isolate, "Requires property name as Symbol or String.");
		return;
	}

	// Spit out deprecation notice to use normal property getter
	v8::String::Utf8Value propertyKey(isolate, name);
	LOGW(TAG, "Automatic getter methods for properties are deprecated in SDK 8.0.0 and will be removed in SDK 9.0.0. Please access the property in standard JS style: obj.%s; or obj['%s'];", *propertyKey, *propertyKey);

	args.GetReturnValue().Set(getPropertyForProxy(isolate, name, args.Holder()));
}

static void setPropertyOnProxy(Isolate* isolate, Local<Name> property, Local<Value> value, Local<Object> proxy)
{
	// Call Proxy.prototype.setProperty.
	Local<Context> context = isolate->GetCurrentContext();
	MaybeLocal<Value> maybeSetProperty = proxy->Get(context, STRING_NEW(isolate, "setProperty"));
	if (maybeSetProperty.IsEmpty()) {
		LOGE(TAG, "Unable to lookup Proxy.prototype.setProperty");
		return;
	}

	Local<Value>setProperty = maybeSetProperty.ToLocalChecked();
	if (!setProperty->IsFunction()) {
		LOGE(TAG, "Proxy.prototype.setProperty isn't a function!!!");
		return;
	}
	Local<Value> argv[2] = { property, value };
	setProperty.As<Function>()->Call(context, proxy, 2, argv);
}

void Proxy::setProperty(Local<Name> property, Local<Value> value, const PropertyCallbackInfo<void>& info)
{
	Isolate* isolate = info.GetIsolate();
	setPropertyOnProxy(isolate, property, value, info.This());
}

static void onPropertyChangedForProxy(Isolate* isolate, Local<String> property, Local<Value> value, Local<Object> proxyObject)
{
	Proxy* proxy = NativeObject::Unwrap<Proxy>(proxyObject);

	JNIEnv* env = JNIUtil::getJNIEnv();
	if (!env) {
		LOG_JNIENV_GET_ERROR(TAG);
		return;
	}
	// FIXME how can we handle symbols?
	Local<Context> context = isolate->GetCurrentContext();
	jstring javaProperty = TypeConverter::jsStringToJavaString(isolate, env, property);
	bool javaValueIsNew;
	jobject javaValue = TypeConverter::jsValueToJavaObject(isolate, env, value, &javaValueIsNew);

	jobject javaProxy = proxy->getJavaObject();
	if (javaProxy != NULL) {
		env->CallVoidMethod(javaProxy,
			JNIUtil::krollProxyOnPropertyChangedMethod,
			javaProperty,
			javaValue);
		proxy->unreferenceJavaObject(javaProxy);
	}

	env->DeleteLocalRef(javaProperty);
	if (javaValueIsNew) {
		env->DeleteLocalRef(javaValue);
	}

	if (env->ExceptionCheck()) {
		JSException::fromJavaException(isolate);
		env->ExceptionClear();
		return;
	}

	// Store new property value on JS internal map.
	setPropertyOnProxy(isolate, property, value, proxyObject);
}

// This variant is used when accessing a property in a standard way and setting a value (i.e. obj.text = 'whatever')
void Proxy::onPropertyChanged(Local<Name> property, Local<Value> value, const v8::PropertyCallbackInfo<void>& info)
{
	Isolate* isolate = info.GetIsolate();
	onPropertyChangedForProxy(isolate, property->ToString(isolate), value, info.Holder());
}

// This variant is used when accessing a property through a getter method (i.e. setText('whatever'))
void Proxy::onPropertyChanged(const v8::FunctionCallbackInfo<v8::Value>& args)
{
	Isolate* isolate = args.GetIsolate();
	if (args.Length() < 1) {
		JSException::Error(isolate, "Requires property name as first parameters.");
		return;
	}

	Local<Name> name = args.Data().As<Name>();
	// Spit out deprecation notice to use normal property setter, not setX() style method.
	v8::String::Utf8Value propertyKey(isolate, name);
	LOGW(TAG, "Automatic setter methods for properties are deprecated in SDK 8.0.0 and will be removed in SDK 9.0.0. Please modify the property in standard JS style: obj.%s = value; or obj['%s'] = value;", *propertyKey, *propertyKey);

	Local<Value> value = args[0];
	onPropertyChangedForProxy(isolate, name->ToString(isolate), value, args.Holder());
}

void Proxy::getIndexedProperty(uint32_t index, const PropertyCallbackInfo<Value>& info)
{
	Isolate* isolate = info.GetIsolate();
	JNIEnv* env = JNIUtil::getJNIEnv();
	if (!env) {
		JSException::GetJNIEnvironmentError(isolate);
		return;
	}

	Proxy* proxy = NativeObject::Unwrap<Proxy>(info.Holder());
	jobject javaProxy = proxy->getJavaObject();
	jobject value = env->CallObjectMethod(javaProxy,
		JNIUtil::krollProxyGetIndexedPropertyMethod,
		index);

	proxy->unreferenceJavaObject(javaProxy);

	if (env->ExceptionCheck()) {
		JSException::fromJavaException(isolate);
		env->ExceptionClear();
		return;
	}

	Local<Value> result = TypeConverter::javaObjectToJsValue(isolate, env, value);
	env->DeleteLocalRef(value);

	info.GetReturnValue().Set(result);
}

void Proxy::setIndexedProperty(uint32_t index, Local<Value> value, const PropertyCallbackInfo<Value>& info)
{
	Isolate* isolate = info.GetIsolate();
	JNIEnv* env = JNIUtil::getJNIEnv();
	if (!env) {
		LOG_JNIENV_GET_ERROR(TAG);
		// Returns undefined by default
		return;
	}

	Proxy* proxy = NativeObject::Unwrap<Proxy>(info.Holder());

	bool javaValueIsNew;
	jobject javaValue = TypeConverter::jsValueToJavaObject(isolate, env, value, &javaValueIsNew);
	jobject javaProxy = proxy->getJavaObject();
	env->CallVoidMethod(javaProxy,
		JNIUtil::krollProxySetIndexedPropertyMethod,
		index,
		javaValue);

	proxy->unreferenceJavaObject(javaProxy);
	if (javaValueIsNew) {
		env->DeleteLocalRef(javaValue);
	}

	if (env->ExceptionCheck()) {
		JSException::fromJavaException(isolate);
		env->ExceptionClear();
		return;
	}

	info.GetReturnValue().Set(value);
}

void Proxy::hasListenersForEventType(const v8::FunctionCallbackInfo<v8::Value>& args)
{
	Isolate* isolate = args.GetIsolate();
	JNIEnv* env = JNIUtil::getJNIEnv();
	if (!env) {
		JSException::GetJNIEnvironmentError(isolate);
		return;
	}

	Local<Object> holder = args.Holder();
	// If holder isn't the JavaObject wrapper we expect, look up the prototype chain
	if (!JavaObject::isJavaObject(holder)) {
		holder = holder->FindInstanceInPrototypeChain(baseProxyTemplateMap[isolate].Get(isolate));
	}
	Proxy* proxy = NativeObject::Unwrap<Proxy>(holder);

	// TODO Support Symbols for event types?
	Local<String> eventType = args[0].As<String>();
	Local<Boolean> hasListeners = args[1]->ToBoolean(isolate);

	jobject javaProxy = proxy->getJavaObject();
	jobject krollObject = env->GetObjectField(javaProxy, JNIUtil::krollProxyKrollObjectField);
	jstring javaEventType = TypeConverter::jsStringToJavaString(isolate, env, eventType);

	proxy->unreferenceJavaObject(javaProxy);

	env->CallVoidMethod(krollObject,
		JNIUtil::krollObjectSetHasListenersForEventTypeMethod,
		javaEventType,
		TypeConverter::jsBooleanToJavaBoolean(hasListeners));

	env->DeleteLocalRef(krollObject);
	env->DeleteLocalRef(javaEventType);

	if (env->ExceptionCheck()) {
		JSException::fromJavaException(isolate);
		env->ExceptionClear();
		return;
	}
}

void Proxy::onEventFired(const v8::FunctionCallbackInfo<v8::Value>& args)
{
	Isolate* isolate = args.GetIsolate();
	JNIEnv* env = JNIUtil::getJNIEnv();
	if (!env) {
		JSException::GetJNIEnvironmentError(isolate);
		return;
	}

	Local<Object> holder = args.Holder();
	// If holder isn't the JavaObject wrapper we expect, look up the prototype chain
	if (!JavaObject::isJavaObject(holder)) {
		holder = holder->FindInstanceInPrototypeChain(baseProxyTemplateMap[isolate].Get(isolate));
	}
	Proxy* proxy = NativeObject::Unwrap<Proxy>(holder);

	// TODO Support Symbols for event types?
	Local<String> eventType = args[0].As<String>();
	Local<Value> eventData = args[1];

	jobject javaProxy = proxy->getJavaObject();
	jobject krollObject = env->GetObjectField(javaProxy, JNIUtil::krollProxyKrollObjectField);

	jstring javaEventType = TypeConverter::jsStringToJavaString(isolate, env, eventType);
	bool isNew;
	jobject javaEventData = TypeConverter::jsValueToJavaObject(isolate, env, eventData, &isNew);

	proxy->unreferenceJavaObject(javaProxy);

	env->CallVoidMethod(krollObject,
		JNIUtil::krollObjectOnEventFiredMethod,
		javaEventType,
		javaEventData);

	env->DeleteLocalRef(krollObject);
	env->DeleteLocalRef(javaEventType);
	if (isNew) {
		env->DeleteLocalRef(javaEventData);
	}

	if (env->ExceptionCheck()) {
		JSException::fromJavaException(isolate);
		env->ExceptionClear();
		return;
	}
}

Local<FunctionTemplate> Proxy::inheritProxyTemplate(Isolate* isolate,
	Local<FunctionTemplate> superTemplate, jclass javaClass,
	Local<String> className, Local<Function> callback)
{
	EscapableHandleScope scope(isolate);

	// Wrap the java class in an External and we can access it via FunctionCallbackInfo.Data() in #proxyConstructor
	Local<FunctionTemplate> inheritedTemplate = FunctionTemplate::New(isolate, proxyConstructor, External::New(isolate, javaClass));

	inheritedTemplate->InstanceTemplate()->SetInternalFieldCount(kInternalFieldCount);
	inheritedTemplate->SetClassName(className);
	inheritedTemplate->Inherit(superTemplate);

	return scope.Escape(inheritedTemplate);
}

void Proxy::proxyConstructor(const v8::FunctionCallbackInfo<v8::Value>& args)
{
	LOGD(TAG, "Proxy::proxyConstructor");
	Isolate* isolate = args.GetIsolate();
	EscapableHandleScope scope(isolate);

	JNIEnv *env = JNIUtil::getJNIEnv();
	Local<Object> jsProxy = args.This();

	TryCatch tryCatch(isolate);

	// First things first, we need to wrap the object in case future calls need to unwrap proxy!
	Proxy* proxy = new Proxy();
	proxy->Wrap(jsProxy);
	proxy->Ref(); // force a reference so we don't get GC'd before we can attach the Java object

	Local<Context> context = isolate->GetCurrentContext();

	// every instance gets a special "_properties" object for us to use internally for get/setProperty
	jsProxy->DefineOwnProperty(context, propertiesSymbolMap[isolate].Get(isolate), Object::New(isolate), static_cast<PropertyAttribute>(DontEnum));

	// Now we hook up a java Object from the JVM...
	jobject javaProxy = Proxy::unwrapJavaProxy(args); // do we already have one that got passed in?
	bool deleteRef = false;
	if (!javaProxy) {
		if (args.Data().IsEmpty() || !args.Data()->IsExternal()) {
			String::Utf8Value jsClassName(isolate, jsProxy->GetConstructorName());
			LOGE(TAG, "No JNI Java Class reference set for proxy java proxy type %s", *jsClassName);
			return;
		}

		jclass javaClass = (jclass) args.Data().As<External>()->Value();
		// Now we create an instance of the class and hook it up
		javaProxy = ProxyFactory::createJavaProxy(javaClass, jsProxy, args);
		deleteRef = true;
	}
	proxy->attach(javaProxy);
	proxy->Unref(); // get rid of our forced reference so this can become weak now

	int length = args.Length();

	if (length > 0 && args[0]->IsObject()) {
		bool extend = true;
		Local<Object> createProperties = args[0].As<Object>();
		Local<String> constructorName = createProperties->GetConstructorName();
		if (strcmp(*v8::String::Utf8Value(isolate, constructorName), "Arguments") == 0) {
			extend = false;
			int32_t argsLength = createProperties->Get(context, lengthSymbolMap[isolate].Get(isolate)).FromMaybe(Integer::New(isolate, 0).As<Value>())->Int32Value(context).FromMaybe(0);
			if (argsLength > 1) {
				Local<Value> properties = createProperties->Get(context, 1).FromMaybe(Undefined(isolate).As<Value>());
				if (properties->IsObject()) {
					extend = true;
					createProperties = properties.As<Object>();
				}
			}
		}

		if (extend) {
			MaybeLocal<Array> maybePropertyNames = createProperties->GetOwnPropertyNames(context);
			if (!maybePropertyNames.IsEmpty()) { // FIXME Handle when empty!
				Local<Array> names = maybePropertyNames.ToLocalChecked();
				int length = names->Length();
				MaybeLocal<Value> maybeProperties = jsProxy->Get(context, propertiesSymbolMap[isolate].Get(isolate));
				if (!maybeProperties.IsEmpty()) { // FIXME Handle when empty!
					Local<Object> properties = maybeProperties.ToLocalChecked().As<Object>();

					for (int i = 0; i < length; ++i) {
						MaybeLocal<Value> maybeName = names->Get(context, i);
						if (maybeName.IsEmpty()) {
							continue;
						}
						Local<Value> name = maybeName.ToLocalChecked();
						MaybeLocal<Value> maybeValue = createProperties->Get(context, name);
						if (maybeValue.IsEmpty()) {
							continue;
						}

						Local<Value> value = maybeValue.ToLocalChecked();
						bool isProperty = true;
						if (name->IsName()) {
							Local<Name> nameStringOrSymbol = name.As<Name>();
							if (!jsProxy->HasRealNamedCallbackProperty(context, nameStringOrSymbol).FromMaybe(false)
								&& !jsProxy->HasRealNamedProperty(context, nameStringOrSymbol).FromMaybe(false)) {
								jsProxy->Set(context, name, value);
								isProperty = false;
							}
						}
						if (isProperty) {
							properties->Set(context, name, value);
						}
					}
				}
			}
		}
	}

	if (deleteRef) {
		JNIEnv *env = JNIUtil::getJNIEnv();
		if (env) {
			env->DeleteLocalRef(javaProxy);
		}
	}

	args.GetReturnValue().Set(scope.Escape(jsProxy));
}

void Proxy::proxyOnPropertiesChanged(const v8::FunctionCallbackInfo<v8::Value>& args)
{
	Isolate* isolate = args.GetIsolate();
	HandleScope scope(isolate);
	Local<Object> jsProxy = args.Holder();

	if (args.Length() < 1 || !(args[0]->IsArray())) {
		JSException::Error(isolate, "Proxy.propertiesChanged requires a list of lists of property name, the old value, and the new value");
		return;
	}

	JNIEnv *env = JNIUtil::getJNIEnv();
	if (!env) {
		JSException::GetJNIEnvironmentError(isolate);
		return;
	}

	Proxy* proxy = NativeObject::Unwrap<Proxy>(jsProxy);
	if (!proxy) {
		JSException::Error(isolate, "Failed to unwrap Proxy instance");
		return;
	}

	Local<Context> context = isolate->GetCurrentContext();
	Local<Array> changes = args[0].As<Array>();
	uint32_t length = changes->Length();
	jobjectArray jChanges = env->NewObjectArray(length, JNIUtil::objectClass, NULL);

	for (uint32_t i = 0; i < length; ++i) {
		// FIXME Actually handle possible empty values
		Local<Array> change = changes->Get(context, i).ToLocalChecked().As<Array>();
		Local<String> name = change->Get(context, INDEX_NAME).ToLocalChecked()->ToString(context).ToLocalChecked();
		Local<Value> oldValue = change->Get(context, INDEX_OLD_VALUE).ToLocalChecked();
		Local<Value> value = change->Get(context, INDEX_VALUE).ToLocalChecked();

		jobjectArray jChange = env->NewObjectArray(3, JNIUtil::objectClass, NULL);

		jstring jName = TypeConverter::jsStringToJavaString(isolate, env, name);
		env->SetObjectArrayElement(jChange, INDEX_NAME, jName);
		env->DeleteLocalRef(jName);

		bool isNew;
		jobject jOldValue = TypeConverter::jsValueToJavaObject(isolate, env, oldValue, &isNew);
		env->SetObjectArrayElement(jChange, INDEX_OLD_VALUE, jOldValue);
		if (isNew) {
			env->DeleteLocalRef(jOldValue);
		}

		jobject jValue = TypeConverter::jsValueToJavaObject(isolate, env, value, &isNew);
		env->SetObjectArrayElement(jChange, INDEX_VALUE, jValue);
		if (isNew) {
			env->DeleteLocalRef(jValue);
		}

		env->SetObjectArrayElement(jChanges, i, jChange);
		env->DeleteLocalRef(jChange);
	}

	jobject javaProxy = proxy->getJavaObject();
	env->CallVoidMethod(javaProxy, JNIUtil::krollProxyOnPropertiesChangedMethod, jChanges);
	env->DeleteLocalRef(jChanges);

	proxy->unreferenceJavaObject(javaProxy);

	if (env->ExceptionCheck()) {
		JSException::fromJavaException(isolate);
		env->ExceptionClear();
	}
}

void Proxy::dispose(Isolate* isolate)
{
	baseProxyTemplateMap[isolate].Reset();
	javaClassSymbolMap[isolate].Reset();
	constructorSymbolMap[isolate].Reset();
	inheritSymbolMap[isolate].Reset();
	propertiesSymbolMap[isolate].Reset();
	lengthSymbolMap[isolate].Reset();
	sourceUrlSymbolMap[isolate].Reset();
}

jobject Proxy::unwrapJavaProxy(const v8::FunctionCallbackInfo<v8::Value>& args)
{
	LOGD(TAG, "Proxy::unwrapJavaProxy");
	if (args.Length() != 1)
		return NULL;

	Local<Value> firstArgument = args[0];
	return firstArgument->IsExternal() ? (jobject) (firstArgument.As<External>()->Value()) : NULL;
}

} // namespace titanium
