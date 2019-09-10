/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-2018 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifndef V8_UTIL_H
#define V8_UTIL_H

#include <map>
#include <stdio.h>
#include <v8.h>

#define ENTER_V8(context) \
	v8::HandleScope scope(context.GetIsolate());

#define IMMUTABLE_STRING_LITERAL_FROM_ARRAY(isolate, string_literal, length) \
	v8::String::NewExternalOneByte(isolate, new v8::ExternalOneByteStringResourceImpl(string_literal, length)).ToLocalChecked()

#define NEW_SYMBOL(isolate, string_literal) \
	v8::String::NewFromUtf8(isolate, string_literal "", v8::NewStringType::kInternalized).ToLocalChecked()

#define STRING_NEW(isolate, string_literal) \
	v8::String::NewFromUtf8(isolate, string_literal "", v8::NewStringType::kNormal).ToLocalChecked()

#define DEFINE_CONSTANT(isolate, target, name, value) \
	(target)->Set(NEW_SYMBOL(isolate, name), \
		value, static_cast<v8::PropertyAttribute>(v8::ReadOnly | v8::DontDelete))

#define DEFINE_INT_CONSTANT(isolate, target, name, value) \
	DEFINE_CONSTANT(isolate, target, name, v8::Integer::New(isolate, value))

#define DEFINE_NUMBER_CONSTANT(isolate, target, name, value) \
	DEFINE_CONSTANT(isolate, target, name, v8::Number::New(isolate, value))

#define DEFINE_STRING_CONSTANT(isolate, target, name, value) \
	DEFINE_CONSTANT(isolate, target, name, STRING_NEW(isolate, value))

#define DEFINE_TEMPLATE(isolate, target, name, tmpl) \
	target->Set(NEW_SYMBOL(isolate, name), tmpl->GetFunction())

#define DEFINE_METHOD(isolate, target, name, callback) \
	DEFINE_TEMPLATE(isolate, target, name, v8::FunctionTemplate::New(isolate, callback))


#define DEFINE_PROTOTYPE_METHOD_DATA(isolate, templ, name, callback, data) \
{ \
	v8::Local<v8::Signature> __callback##_SIG = v8::Signature::New(isolate, templ); \
	v8::Local<v8::FunctionTemplate> __callback##_TEM = \
	v8::FunctionTemplate::New(isolate, callback, data, __callback##_SIG); \
	templ->PrototypeTemplate()->Set(NEW_SYMBOL(isolate, name), \
		__callback##_TEM, static_cast<v8::PropertyAttribute>(DontEnum)); \
}

#define DEFINE_PROTOTYPE_METHOD(isolate, templ, name, callback) \
	DEFINE_PROTOTYPE_METHOD_DATA(isolate, templ, name, callback, v8::Local<v8::Value>())

#ifdef TI_DEBUG
# define LOG_HEAP_STATS(isolate, TAG) \
{ \
	v8::HeapStatistics stats; \
	isolate->GetHeapStatistics(&stats); \
	LOGE(TAG, "Heap stats:"); \
	LOGE(TAG, "   Total heap size:            %dk", stats.total_heap_size() / 1024); \
	LOGE(TAG, "   Total heap size executable: %dk", stats.total_heap_size_executable() / 1024); \
	LOGE(TAG, "   Used heap size:             %dk", stats.used_heap_size() / 1024); \
	LOGE(TAG, "   Heap size limit:            %dk", stats.heap_size_limit() / 1024); \
}
# define LOG_STACK_TRACE(isolate, TAG, ...) \
{ \
	v8::Local<v8::StackTrace> stackTrace = v8::StackTrace::CurrentStackTrace(isolate, 16); \
	uint32_t frameCount = stackTrace->GetFrameCount(); \
	LOGV(TAG, __VA_ARGS__); \
	for (uint32_t i = 0; i < frameCount; i++) { \
		v8::Local<v8::StackFrame> frame = stackTrace->GetFrame(i); \
		v8::String::Utf8Value fnName(frame->GetFunctionName()); \
		v8::String::Utf8Value scriptUrl(frame->GetScriptName()); \
		LOGV(TAG, "    at %s [%s:%d:%d]", *fnName, *scriptUrl, frame->GetLineNumber(), frame->GetColumn()); \
	} \
}
#else
# define LOG_HEAP_STATS(isolate, TAG)
# define LOG_STACK_TRACE(isolate, TAG)
#endif

namespace titanium {

inline v8::Local<v8::FunctionTemplate> NewFunctionTemplate(v8::Isolate* isolate, v8::FunctionCallback callback, v8::Local<v8::Signature> signature = v8::Local<v8::Signature>()) {
	return v8::FunctionTemplate::New(isolate, callback, v8::Local<v8::Value>(), signature);
}

inline void SetMethod(v8::Local<v8::Context> context, v8::Isolate* isolate, v8::Local<v8::Object> that, const char* name, v8::FunctionCallback callback) {
	v8::Local<v8::Function> function = NewFunctionTemplate(isolate, callback)->GetFunction(context).ToLocalChecked();
	v8::Local<v8::String> name_string = v8::String::NewFromUtf8(isolate, name, v8::NewStringType::kInternalized).ToLocalChecked();
	that->Set(name_string, function);
	function->SetName(name_string); // NODE_SET_METHOD() compatibility.
}

inline void SetProtoMethod(v8::Isolate* isolate, v8::Local<v8::FunctionTemplate> that, const char* name, v8::FunctionCallback callback) {
	v8::Local<v8::Signature> signature = v8::Signature::New(isolate, that);
	v8::Local<v8::FunctionTemplate> t = NewFunctionTemplate(isolate, callback);
	v8::Local<v8::String> name_string = v8::String::NewFromUtf8(isolate, name, v8::NewStringType::kInternalized).ToLocalChecked();
	that->PrototypeTemplate()->Set(name_string, t);
	t->SetClassName(name_string); // NODE_SET_PROTOTYPE_METHOD() compatibility.
}

inline void SetTemplateMethod(v8::Isolate* isolate, v8::Local<v8::FunctionTemplate> that, const char* name, v8::FunctionCallback callback) {
	v8::Local<v8::FunctionTemplate> t = NewFunctionTemplate(isolate, callback);
	v8::Local<v8::String> name_string = v8::String::NewFromUtf8(isolate, name, v8::NewStringType::kInternalized).ToLocalChecked();
	that->Set(name_string, t);
	t->SetClassName(name_string); // NODE_SET_METHOD() compatibility.
}

class Utf8Value {
	public:
	explicit Utf8Value(v8::Local<v8::Value> value);

	~Utf8Value() {
		if (str_ != str_st_) free(str_);
	}

	char* operator*() {
		return str_;
	};

	const char* operator*() const {
		return str_;
	};

	size_t length() const {
		return length_;
	};

	private:
	size_t length_;
	char* str_;
	char str_st_[1024];
};

class V8Util {
public:
	static v8::Local<v8::Value> executeString(v8::Isolate* isolate, v8::Local<v8::String> source, v8::Local<v8::Value> filename);
	static v8::Local<v8::Value> newInstanceFromConstructorTemplate(v8::Persistent<v8::FunctionTemplate>& t,
		const v8::FunctionCallbackInfo<v8::Value>& args);
	static void objectExtend(v8::Local<v8::Object> dest, v8::Local<v8::Object> src);
	static void reportException(v8::Isolate* isolate, v8::TryCatch &tryCatch, bool showLine = true);
	static void openJSErrorDialog(v8::Isolate* isolate, v8::TryCatch &tryCatch);
	static void fatalException(v8::Isolate* isolate, v8::TryCatch &tryCatch);
	static v8::Local<v8::String> jsonStringify(v8::Isolate* isolate, v8::Local<v8::Value> value);
	static bool constructorNameMatches(v8::Isolate* isolate, v8::Local<v8::Object>, const char* name);
	static bool isNaN(v8::Isolate* isolate, v8::Local<v8::Value> value);
	static void dispose(v8::Isolate* isolate);
	static std::string stackTraceString(v8::Isolate* isolate, v8::Local<v8::StackTrace> frames);
};

}

#endif
