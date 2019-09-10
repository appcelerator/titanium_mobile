#include <v8-platform.h>
#include <libplatform/libplatform.h>

#include "AndroidUtil.h"
#include "EventEmitter.h"
#include "JNIUtil.h"
#include "JSException.h"
#include "KrollBindings.h"
#include "TypeConverter.h"
#include "V8Util.h"

#include "V8Runtime.h"

#define TAG "V8Worker"

using namespace titanium;

namespace tiworker {

static void close(const FunctionCallbackInfo<Value>& args)
{
	JNIEnv* env = JNIUtil::getJNIEnv();
	Isolate* isolate = args.GetIsolate();
	pid_t threadId = gettid();
	jobject self = V8Runtime::javaInstance[threadId];

	env->CallVoidMethod(self, JNIUtil::krollWorkerCloseMethod);

	if (env->ExceptionCheck()) {
		JSException::fromJavaException(isolate);
		env->ExceptionClear();
		return;
	}
}

static void postMessage(const FunctionCallbackInfo<Value>& args)
{
	JNIEnv* env = JNIUtil::getJNIEnv();
	Isolate* isolate = args.GetIsolate();
	pid_t threadId = gettid();
	jobject self = V8Runtime::javaInstance[threadId];

	Local<Value> eventData = args[0];

	bool isNew;
	jobject javaEventData = TypeConverter::jsValueToJavaObject(isolate, env, eventData, &isNew);

	env->CallVoidMethod(self,
			JNIUtil::krollWorkerPostMessageMethod,
			javaEventData);

	if (isNew) {
		env->DeleteLocalRef(javaEventData);
	}

	if (env->ExceptionCheck()) {
		JSException::fromJavaException(isolate);
		env->ExceptionClear();
		return;
	}
}

static void onmessage(const FunctionCallbackInfo<Value>& args)
{
	//stub
}

} // namespace tiworker

extern "C" {

using namespace titanium;
using namespace tiworker;
using namespace v8;

JNIEXPORT void JNICALL Java_ti_modules_titanium_worker_V8Worker_nativeInit
  (JNIEnv *env, jobject self, jstring workerName)
{
	V8Runtime::init(env, self, nullptr, V8Runtime::DBG, false);

	pid_t threadId = gettid();
	Isolate* isolate = V8Runtime::thread_isolateMap[threadId];
	HandleScope scope(isolate);

	// Prepare global object template
	Local<FunctionTemplate> constructor = FunctionTemplate::New(isolate);
	Local<String> globalScopeSymbol = NEW_SYMBOL(isolate, "WorkerGlobalScope");
	constructor->SetClassName(globalScopeSymbol);
	constructor->Inherit(EventEmitter::constructorTemplate[isolate].Get(isolate));
	Local<ObjectTemplate> instanceTemplate = constructor->InstanceTemplate();
	Local<Context> context = Context::New(isolate, NULL, MaybeLocal<ObjectTemplate>(instanceTemplate));
	context->Enter();
	V8Runtime::globalContext[threadId].Reset(isolate, context);

	Local<Object> global = context->Global();
	// so it would possible to check `global.constructor === WorkerGlobalScope`
	global->Set(context, globalScopeSymbol, constructor->GetFunction(context).ToLocalChecked());

	// TODO: add context to debugger

	V8Runtime::bootstrap(context, threadId);

	Local<String> name = TypeConverter::javaStringToJsString(isolate, env, workerName).As<String>();

	// ti.worker API
	global->Set(context, NEW_SYMBOL(isolate, "worker"), global);
	SetMethod(context, isolate, global, "terminate", close);

	// Web Workers API
	SetMethod(context, isolate, global, "close", close);
	SetMethod(context, isolate, global, "postMessage", postMessage);
	SetMethod(context, isolate, global, "onmessage", onmessage);
	global->Set(context, NEW_SYMBOL(isolate, "self"), global);

	global->Set(context, NEW_SYMBOL(isolate, "name"), name);

	LOG_HEAP_STATS(isolate, TAG);
}

JNIEXPORT void JNICALL Java_ti_modules_titanium_worker_V8Worker_nativeRunModule
	(JNIEnv *env, jobject self, jstring source, jstring name, jobject activityProxy)
{
	V8Runtime::nativeRunModule(env, self, source, name, activityProxy);
}

JNIEXPORT void JNICALL Java_ti_modules_titanium_worker_V8Worker_nativeDispose(JNIEnv *env, jobject runtime)
{
	V8Runtime::nativeDispose(env, runtime);
}

JNIEXPORT void JNICALL Java_ti_modules_titanium_worker_V8Worker_nativeOnMessage(JNIEnv *env, jobject self, jobject message)
{
	pid_t threadId = gettid();
	Isolate* isolate = V8Runtime::thread_isolateMap[threadId];
	HandleScope scope(isolate);
	Local<Context> context = V8Runtime::globalContext[threadId].Get(isolate);
	Local<Object> global = context->Global();
	Local<Value> jsValue = TypeConverter::javaObjectToJsValue(isolate, env, message);
	Local<Value> argv[1] = { jsValue };
	
	MaybeLocal<Value> maybeOnMessage = global->Get(context, STRING_NEW(isolate, "onmessage"));
	if (!maybeOnMessage.IsEmpty()) {
		Local<Value> onMessage = maybeOnMessage.ToLocalChecked();
		if (onMessage->IsFunction()) {
			TryCatch tryCatch(isolate);
			onMessage.As<Function>()->Call(context, global, 1, argv);
			if (tryCatch.HasCaught()) {
				V8Util::openJSErrorDialog(isolate, tryCatch);
				V8Util::reportException(isolate, tryCatch, true);
				return;
			}
		}
	}

	Local<String> symbol = EventEmitter::emitSymbol[isolate].Get(isolate);
	if (symbol.IsEmpty()) {
		LOGE(TAG, "symbol.IsEmpty()");
		return;
	}
	MaybeLocal<Value> maybeEmit = global->Get(context, symbol);
	if (!maybeEmit.IsEmpty()) {
		Local<Value> emit = maybeEmit.ToLocalChecked();
		if (emit->IsFunction()) {
			Local<Value> args[] = { STRING_NEW(isolate, "message"), jsValue };
			TryCatch tryCatch(isolate);
			emit.As<Function>()->Call(context, global, 2, args);
			if (tryCatch.HasCaught()) {
				V8Util::openJSErrorDialog(isolate, tryCatch);
				V8Util::reportException(isolate, tryCatch, true);
				return;
			}
		} else {
 	 	 	LOGE(TAG, "!emit->IsFunction()");
		}
	} else {
		LOGE(TAG, "maybeEmit.IsEmpty()");
	}
}

} // extern "C"
