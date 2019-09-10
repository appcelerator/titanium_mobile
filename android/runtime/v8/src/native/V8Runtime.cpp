/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-2018 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#include <stdio.h>
#include <cstring>

#include <v8-platform.h>
#include <libplatform/libplatform.h>

#include "AndroidUtil.h"
#include "EventEmitter.h"
#include "Proxy.h"
#include "JNIUtil.h"
#include "JSDebugger.h"
#include "JSException.h"
#include "KrollBindings.h"
#include "ProxyFactory.h"
#include "ScriptsModule.h"
#include "TypeConverter.h"
#include "V8Util.h"

#include "V8Runtime.h"
#include "V8Snapshots.h"

#define TAG "V8Runtime"

// The port number on which the V8 debugger will listen on.
#define V8_DEBUGGER_PORT 9999

namespace titanium {

std::map<pid_t, Persistent<Context>> V8Runtime::globalContext;
std::map<pid_t, Persistent<Object>> V8Runtime::krollGlobalObject;
std::map<pid_t, Persistent<Object>> V8Runtime::moduleObject;
std::map<pid_t, Persistent<Function>> V8Runtime::runModuleFunction;

std::map<pid_t, jobject> V8Runtime::javaInstance;
std::map<pid_t, Locker *> V8Runtime::thread_lockerMap;
std::map<pid_t, Isolate *> V8Runtime::thread_isolateMap;
Platform* V8Runtime::platform = nullptr;
Isolate* V8Runtime::v8_isolate = nullptr;
bool V8Runtime::debuggerEnabled = false;
bool V8Runtime::DBG = false;
bool V8Runtime::initialized = false;
pid_t V8Runtime::runtimeThreadId = 0;

class ArrayBufferAllocator : public v8::ArrayBuffer::Allocator {
 public:
	virtual void* Allocate(size_t length) { return calloc(length, 1); }
	virtual void* AllocateUninitialized(size_t length) { return malloc(length); }
	virtual void Free(void* data, size_t) { free(data); }
};

// Make allocator global so it sticks around?
ArrayBufferAllocator allocator;

Local<Context> V8Runtime::GlobalContext()
{
	pid_t threadId = gettid();
	Isolate* isolate = thread_isolateMap[threadId];
	return globalContext[threadId].Get(isolate);
}

// Minimalistic logging function for internal JS
static void krollLog(const FunctionCallbackInfo<Value>& args)
{
	Isolate* isolate = args.GetIsolate();
	HandleScope scope(isolate);
	uint32_t len = args.Length();

	if (len < 2) {
		JSException::Error(isolate, "log: missing required tag and message arguments");
		return;
	}

	Local<String> tag = args[0].As<String>();
	Local<String> message = args[1].As<String>();
	Local<String> space = STRING_NEW(isolate, " ");
	for (uint32_t i = 2; i < len; ++i) {
		message = String::Concat(isolate, String::Concat(isolate, message, space), args[i].As<String>());
	}

	String::Utf8Value tagValue(isolate, tag);
	String::Utf8Value messageValue(isolate, message);
	__android_log_print(ANDROID_LOG_DEBUG, *tagValue, *messageValue);
}

/* static */
void V8Runtime::bootstrap(Local<Context> context, pid_t threadId)
{
	Isolate* isolate = context->GetIsolate();

	Local<Object> kroll = Object::New(isolate);
	krollGlobalObject[threadId].Reset(isolate, kroll);

	KrollBindings::initFunctions(kroll, context);

	SetMethod(context, isolate, kroll, "log", krollLog);
	// Move this into the EventEmitter::initTemplate call?
	Local<FunctionTemplate> eect = Local<FunctionTemplate>::New(isolate, EventEmitter::constructorTemplate[isolate]);
	{
		v8::TryCatch tryCatch(isolate);
		Local<Function> eventEmitterConstructor;
		MaybeLocal<Function> maybeEventEmitterConstructor = eect->GetFunction(context);
		if (!maybeEventEmitterConstructor.ToLocal(&eventEmitterConstructor)) {
			titanium::V8Util::fatalException(isolate, tryCatch);
			return;
		}
		kroll->Set(context, NEW_SYMBOL(isolate, "EventEmitter"), eventEmitterConstructor);
	}

	kroll->Set(context, NEW_SYMBOL(isolate, "runtime"), STRING_NEW(isolate, "v8"));
	kroll->Set(context, NEW_SYMBOL(isolate, "DBG"), v8::Boolean::New(isolate, V8Runtime::DBG));

	LOG_TIMER(TAG, "Executing kroll.js");

	TryCatch tryCatch(isolate);
	Local<Value> result = V8Util::executeString(isolate, KrollBindings::getMainSource(isolate), STRING_NEW(isolate, "ti:/kroll.js"));

	if (tryCatch.HasCaught()) {
		V8Util::reportException(isolate, tryCatch, true);
	}
	if (!result->IsFunction()) {
		LOGF(TAG, "kroll.js result is not a function");
		V8Util::reportException(isolate, tryCatch, true);
	}

	// Add a reference to the global object
	Local<Object> global = context->Global();

	// Expose the global object as a property on itself
	// (Allows you to set stuff on `global` from anywhere in JavaScript.)
	global->Set(context, NEW_SYMBOL(isolate, "global"), global);

	// Set the __dirname and __filename for the app.js.
	// For other files, it will be injected via the `NativeModule` JavaScript class
	global->Set(NEW_SYMBOL(isolate, "__filename"), STRING_NEW(isolate, "/app.js"));
	global->Set(NEW_SYMBOL(isolate, "__dirname"), STRING_NEW(isolate, "/"));

	Local<Function> mainFunction = result.As<Function>();
	Local<Value> args[] = { kroll };
	mainFunction->Call(context, global, 1, args);

	if (tryCatch.HasCaught()) {
		V8Util::reportException(isolate, tryCatch, true);
		LOGE(TAG, "Caught exception while bootstrapping Kroll");
	}
}

void V8Runtime::init(JNIEnv *env, jobject self, jobject debugger, jboolean DBG, jboolean profilerEnabled)
{
	pid_t threadId = gettid();
	if (!V8Runtime::initialized) {
		V8Runtime::runtimeThreadId = threadId;
		// Initialize V8.
		// TODO Enable this when we use snapshots?
		//V8::InitializeExternalStartupData(argv[0]);
		V8Runtime::platform = platform::CreateDefaultPlatform();
		V8::InitializePlatform(V8Runtime::platform);
		V8::Initialize();
		V8Runtime::initialized = true;
	}

	V8Runtime::DBG = DBG;

	V8Runtime::javaInstance[threadId] = env->NewGlobalRef(self);
	JNIUtil::initCache();

	Isolate* isolate;
	if (V8Runtime::thread_isolateMap[threadId] == nullptr) {
		// Create a new Isolate and make it the current one.
		Isolate::CreateParams create_params;
		create_params.array_buffer_allocator = &allocator;
#ifdef V8_SNAPSHOT_H
		create_params.snapshot_blob = &snapshot;
#endif
		isolate = Isolate::New(create_params);
		V8Runtime::thread_isolateMap[threadId] = isolate;
		V8Runtime::thread_lockerMap[threadId] = new Locker(isolate);
		isolate->Enter();

		if (V8Runtime::runtimeThreadId == threadId) {
			V8Runtime::v8_isolate = isolate;
		}

		// Log all uncaught V8 exceptions.
		isolate->AddMessageListener(V8Runtime::logV8Exception);
		// isolate->SetAbortOnUncaughtExceptionCallback(ShouldAbortOnUncaughtException);
		// isolate->SetAutorunMicrotasks(false);
		// isolate->SetFatalErrorHandler(OnFatalError);
		isolate->SetCaptureStackTraceForUncaughtExceptions(true, 10, v8::StackTrace::kOverview);
	} else {
		isolate = V8Runtime::thread_isolateMap[threadId];
		V8Runtime::thread_lockerMap[threadId] = new Locker(isolate);
		isolate->Enter();
	}

	HandleScope scope(isolate);
	EventEmitter::initTemplate(isolate);
}

void V8Runtime::nativeRunModule(JNIEnv *env, jobject self, jstring source, jstring filename, jobject activityProxy)
{
	pid_t threadId = gettid();
	Isolate* isolate = V8Runtime::thread_isolateMap[threadId];
	HandleScope scope(isolate);
	Local<Context> context = isolate->GetCurrentContext();

	if (V8Runtime::moduleObject[threadId].IsEmpty()) {
		Local<Object> module;
		{
			v8::TryCatch tryCatch(isolate);
			Local<Value> moduleValue;
			MaybeLocal<Value> maybeModule = V8Runtime::krollGlobalObject[threadId].Get(isolate)->Get(context, STRING_NEW(isolate, "Module"));
			if (!maybeModule.ToLocal(&moduleValue)) {
				titanium::V8Util::fatalException(isolate, tryCatch);
				return;
			}
			module = moduleValue.As<Object>();
			V8Runtime::moduleObject[threadId].Reset(isolate, module);
		}

		{
			v8::TryCatch tryCatch(isolate);
			Local<Value> runModule;
			MaybeLocal<Value> maybeRunModule = module->Get(context, STRING_NEW(isolate, "runModule"));
			if (!maybeRunModule.ToLocal(&runModule)) {
				titanium::V8Util::fatalException(isolate, tryCatch);
				return;
			}
			V8Runtime::runModuleFunction[threadId].Reset(isolate, runModule.As<Function>());
		}
	}

	Local<Value> jsSource = TypeConverter::javaStringToJsString(isolate, env, source);
	Local<Value> jsFilename = TypeConverter::javaStringToJsString(isolate, env, filename);
	Local<Value> jsActivity = TypeConverter::javaObjectToJsValue(isolate, env, activityProxy);

	Local<Value> args[] = { jsSource, jsFilename, jsActivity };
	TryCatch tryCatch(isolate);
	V8Runtime::runModuleFunction[threadId].Get(isolate)->Call(context, V8Runtime::moduleObject[threadId].Get(isolate), 3, args);

	if (tryCatch.HasCaught()) {
		V8Util::openJSErrorDialog(isolate, tryCatch);
		V8Util::reportException(isolate, tryCatch, true);
	}
}

void V8Runtime::nativeDispose(JNIEnv *env, jobject runtime)
{
	pid_t threadId = gettid();
	Isolate* isolate = V8Runtime::thread_isolateMap[threadId];

	// We use a new scope here so any new handles we create
	// while disposing are cleaned up before our global context
	// is disposed below.
	{
		HandleScope scope(isolate);

		// KrollBindings
		KrollBindings::dispose(isolate);
		EventEmitter::dispose(isolate);

		V8Runtime::globalContext[threadId].Get(isolate)->DetachGlobal();

	}

	// Dispose of each class' static cache / resources
	V8Util::dispose(isolate);
	ProxyFactory::dispose(isolate);

	V8Runtime::moduleObject[threadId].Reset();
	V8Runtime::moduleObject.erase(threadId);
	V8Runtime::runModuleFunction[threadId].Reset();
	V8Runtime::runModuleFunction.erase(threadId);
	V8Runtime::krollGlobalObject[threadId].Reset();
	V8Runtime::krollGlobalObject.erase(threadId);

	{
		HandleScope scope(isolate);
		V8Runtime::globalContext[threadId].Get(isolate)->Exit();
	}

	V8Runtime::globalContext[threadId].Reset();
	V8Runtime::globalContext.erase(threadId);

	// Removes the retained global reference to the V8Runtime
	env->DeleteGlobalRef(V8Runtime::javaInstance[threadId]);
	V8Runtime::javaInstance.erase(threadId);

	// Whereas most calls to IdleNotification get kicked off via Java (the looper's
	// idle event in V8Runtime.java), we can't count on that running anymore at this point.
	// So as our last act, run IdleNotification until it returns true so we can clean up all
	// the stuff we just released references for above.
	while (!isolate->IdleNotificationDeadline(V8Runtime::platform->MonotonicallyIncreasingTime() + 1.0));

	// Typically in a V8 embedded app, we'd clean everything up here. But since
	// an app may just be closed/backgrounded but still alive, we can't do this
	// because we can't re-initialize once it's disposed.

	// Do final cleanup
	isolate->Exit();
	delete(V8Runtime::thread_lockerMap[threadId]);
	V8Runtime::thread_lockerMap.erase(threadId);
	//isolate->Dispose();
	//V8::Dispose();
	//V8::ShutdownPlatform();
	//delete V8Runtime::platform;
}

void V8Runtime::logV8Exception(Local<Message> msg, Local<Value> data)
{
	Isolate* isolate = Isolate::GetCurrent();
	HandleScope scope(isolate);
	Local<Context> context = isolate->GetCurrentContext();
	// Log reason and location of the error.
	LOGD(TAG, *String::Utf8Value(isolate, msg->Get()));
	LOGD(TAG, "%s @ %d >>> %s",
		*String::Utf8Value(isolate, msg->GetScriptResourceName()),
		msg->GetLineNumber(context).FromMaybe(-1),
		*String::Utf8Value(isolate,
		msg->GetSourceLine(context).FromMaybe(-1)));
}

} // namespace titanium

extern "C" {

using namespace titanium;

/*
 * Class:     org_appcelerator_kroll_runtime_v8_V8Runtime
 * Method:    nativeInit
 * Signature: (Lorg/appcelerator/kroll/runtime/v8/V8Runtime;)J
 */
JNIEXPORT void JNICALL Java_org_appcelerator_kroll_runtime_v8_V8Runtime_nativeInit(JNIEnv *env, jobject self, jobject debugger, jboolean DBG, jboolean profilerEnabled)
{
	V8Runtime::init(env, self, debugger, DBG, profilerEnabled);

	pid_t threadId = gettid();
	Isolate* isolate = V8Runtime::thread_isolateMap[threadId];
	HandleScope scope(isolate);

	// Using default global object template
	Local<Context> context = Context::New(isolate);
	context->Enter();
	V8Runtime::globalContext[threadId].Reset(isolate, context);
	if (V8Runtime::runtimeThreadId == threadId) {
		JSDebugger::init(env, debugger, context);
		if (debugger != nullptr) {
			V8Runtime::debuggerEnabled = true;
		}
	}
	V8Runtime::bootstrap(context, threadId);
	LOG_HEAP_STATS(isolate, TAG);
}

/*
 * Class:     org_appcelerator_kroll_runtime_v8_V8Runtime
 * Method:    nativeRunModule
 * Signature: (Ljava/lang/String;Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_org_appcelerator_kroll_runtime_v8_V8Runtime_nativeRunModule
	(JNIEnv *env, jobject self, jstring source, jstring filename, jobject activityProxy)
{
	V8Runtime::nativeRunModule(env, self, source, filename, activityProxy);
}

JNIEXPORT jobject JNICALL Java_org_appcelerator_kroll_runtime_v8_V8Runtime_nativeEvalString
	(JNIEnv *env, jobject self, jstring source, jstring filename)
{
	pid_t threadId = gettid();
	Isolate* isolate = V8Runtime::thread_isolateMap[threadId];
	HandleScope scope(isolate);

	Local<Value> jsSource = TypeConverter::javaStringToJsString(isolate, env, source);
	if (jsSource.IsEmpty() || !jsSource->IsString()) {
		LOGE(TAG, "Error converting Javascript string, aborting evalString");
		return NULL;
	}

	Local<Value> jsFilename = TypeConverter::javaStringToJsString(isolate, env, filename);

	TryCatch tryCatch(isolate);
	Local<Context> context = isolate->GetCurrentContext();
	ScriptOrigin origin(jsFilename);
	MaybeLocal<Script> maybeScript = Script::Compile(context, jsSource.As<String>(), &origin);
	if (maybeScript.IsEmpty()) {
		V8Util::openJSErrorDialog(isolate, tryCatch);
		V8Util::reportException(isolate, tryCatch, true);
		return NULL;
	}
	Local<Script> script = maybeScript.ToLocalChecked();
	MaybeLocal<Value> result = script->Run(context);
	if (tryCatch.HasCaught()) {
		V8Util::openJSErrorDialog(isolate, tryCatch);
		V8Util::reportException(isolate, tryCatch, true);
		return NULL;
	}

	return TypeConverter::jsValueToJavaObject(isolate, env, result.ToLocalChecked());
}

JNIEXPORT jboolean JNICALL Java_org_appcelerator_kroll_runtime_v8_V8Runtime_nativeIdle(JNIEnv *env, jobject self)
{
	pid_t threadId = gettid();
	// notify V8 of low memory to suggest a full GC
	V8Runtime::thread_isolateMap[threadId]->LowMemoryNotification();
	return true;
}

/*
 * Called by V8Runtime.java, this passes a KrollSourceCodeProvider java class instance
 * to KrollBindings, where it's stored and later used to retrieve an external CommonJS module's
 * Javascript code when require(moduleName) occurs in Javascript.
 * "External" CommonJS modules are CommonJS modules stored in external modules.
 */
JNIEXPORT void JNICALL Java_org_appcelerator_kroll_runtime_v8_V8Runtime_nativeAddExternalCommonJsModule
	(JNIEnv *env, jobject self, jstring moduleName, jobject sourceProvider)
{
	const char* mName = env->GetStringUTFChars(moduleName, NULL);
	jclass cls = env->GetObjectClass(sourceProvider);

	if (!cls) {
		LOGE(TAG, "Could not find source code provider class for module: %s", mName);
		return;
	}

	jmethodID method = env->GetMethodID(cls, "getSourceCode", "(Ljava/lang/String;)Ljava/lang/String;");
	env->DeleteLocalRef(cls);
	if (!method) {
		LOGE(TAG, "Could not find getSourceCode method in source code provider class for module: %s", mName);
		return;
	}

	KrollBindings::addExternalCommonJsModule(mName, env->NewGlobalRef(sourceProvider), method);
}

// This method disposes of all native resources used by V8 when
// all activities have been destroyed by the application.
//
// When a Persistent handle is Dispose()'d in V8, the internal
// pointer is not changed, handle->IsEmpty() returns false.
// As a consequence, we have to explicitly reset the handle
// to an empty handle using Persistent<Type>()
//
// Since we use lazy initialization in a lot of our code,
// there's probably not an easier way (unless we use boolean flags)

JNIEXPORT void JNICALL Java_org_appcelerator_kroll_runtime_v8_V8Runtime_nativeDispose(JNIEnv *env, jobject runtime)
{
	V8Runtime::nativeDispose(env, runtime);
}

jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
	JNIUtil::javaVm = vm;
	return JNI_VERSION_1_4;
}

} // extern "C"
