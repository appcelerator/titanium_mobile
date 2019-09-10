/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-2016 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#include <v8.h>
#include <jni.h>

#include "AndroidUtil.h"
#include "NativeObject.h"
#include "ScriptsModule.h"
#include "V8Runtime.h"
#include "V8Util.h"
#include "JNIUtil.h"
#include "TypeConverter.h"
#include "JSException.h"

#define TAG "ScriptsModule"

namespace titanium {
using namespace v8;

std::map<Isolate *, Persistent<FunctionTemplate>> WrappedScript::constructor_template;
std::map<Isolate *, Persistent<ObjectTemplate>> WrappedContext::global_template;

void WrappedContext::Initialize(Local<Object> target, Local<Context> context)
{
	Isolate* isolate = context->GetIsolate();
	HandleScope scope(isolate);

	Local<ObjectTemplate> gt = ObjectTemplate::New(isolate);
	gt->SetInternalFieldCount(1);
	global_template[isolate].Reset(isolate, gt);
}

WrappedContext* WrappedContext::Unwrap(v8::Isolate* isolate, Local<Object> global)
{
	HandleScope scope(isolate);
	return NativeObject::Unwrap<WrappedContext>(global->GetPrototype().As<Object>());
}

WrappedContext::WrappedContext(v8::Isolate* isolate, Local<Context> context)
	: context_(isolate, context)
{
	HandleScope scope(isolate);

	Local<Object> globalProxy = context->Global();
	Local<Object> global = globalProxy->GetPrototype().As<Object>();
	Wrap(global);
}

WrappedContext::~WrappedContext()
{
	if (!context_.IsEmpty()) {
		Dispose();
	}
}

void WrappedContext::Dispose()
{
	context_.Get(Isolate::GetCurrent())->DetachGlobal();
	context_.Reset();
}

void WrappedScript::Initialize(Local<Object> target, Local<Context> context)
{
	Isolate* isolate = context->GetIsolate();
	HandleScope scope(isolate);

	Local<FunctionTemplate> constructor = FunctionTemplate::New(isolate, WrappedScript::New);
	constructor->InstanceTemplate()->SetInternalFieldCount(1);
	Local<String> symbol = NEW_SYMBOL(isolate, "Script");
	constructor->SetClassName(symbol);

	constructor_template[isolate].Reset(isolate, constructor);

	SetProtoMethod(isolate, constructor, "runInContext", WrappedScript::RunInContext);
	SetProtoMethod(isolate, constructor, "runInThisContext", WrappedScript::RunInThisContext);
	SetProtoMethod(isolate, constructor, "runInNewContext", WrappedScript::RunInNewContext);

	SetTemplateMethod(isolate, constructor, "createContext", WrappedScript::CreateContext);
	SetTemplateMethod(isolate, constructor, "disposeContext", WrappedScript::DisposeContext);
	SetTemplateMethod(isolate, constructor, "runInContext", WrappedScript::CompileRunInContext);
	SetTemplateMethod(isolate, constructor, "runInThisContext", WrappedScript::CompileRunInThisContext);
	SetTemplateMethod(isolate, constructor, "runInNewContext", WrappedScript::CompileRunInNewContext);

	v8::TryCatch tryCatch(isolate);
	MaybeLocal<Function> maybeConstructor = constructor->GetFunction(context);
	Local<Function> localFunction;
	if (!maybeConstructor.ToLocal(&localFunction)) {
		V8Util::fatalException(isolate, tryCatch);
		return;
	}
	target->Set(symbol, localFunction);
}

void WrappedScript::New(const FunctionCallbackInfo<Value>& args)
{
	Isolate* isolate = args.GetIsolate();
	if (!args.IsConstructCall()) {
		args.GetReturnValue().Set(V8Util::newInstanceFromConstructorTemplate(constructor_template[isolate], args));
		return;
	}

	HandleScope scope(isolate);
	WrappedScript *t = new WrappedScript();
	t->Wrap(args.Holder());
	WrappedScript::EvalMachine<compileCode, thisContext, wrapExternal>(args);
}

WrappedScript::~WrappedScript()
{
	script_.Reset();
}

void WrappedScript::CreateContext(const FunctionCallbackInfo<Value>& args)
{
	Isolate* isolate = args.GetIsolate();
	EscapableHandleScope scope(isolate);

	Local<Value> securityToken = isolate->GetCurrentContext()->GetSecurityToken();

	Local<Context> context = Context::New(isolate, NULL, WrappedContext::global_template[isolate].Get(isolate));
	Local<Object> global = context->Global();

	// Allow current context access to newly created context's objects.
	context->SetSecurityToken(securityToken);

	// TODO Do we need to esnure the same context object is wrapped and returned?
	new WrappedContext(isolate, context); // wrap the context's global prototype...

	// If a sandbox is provided initial the new context's global with it.
	if (args.Length() > 0) {
		Local<Object> sandbox = args[0].As<Object>();
		Local<Array> keys = sandbox->GetPropertyNames();

		for (uint32_t i = 0; i < keys->Length(); i++) {
			Local<String> key = keys->Get(Integer::New(isolate, i)).As<String>();
			Local<Value> value = sandbox->Get(key);
			if (value == sandbox) {
				value = global;
			}
			global->Set(key, value);
		}
	}

	args.GetReturnValue().Set(scope.Escape(global));
}

void WrappedScript::DisposeContext(const FunctionCallbackInfo<Value>& args)
{
	Isolate* isolate = args.GetIsolate();
	HandleScope scope(isolate);

	if (args.Length() < 1) {
		JSException::Error(isolate, "Must pass the context as the first argument.");
		return;
	}

	WrappedContext* wrappedContext = WrappedContext::Unwrap(isolate, args[0].As<Object>());
	delete wrappedContext;
}

void WrappedScript::RunInContext(const FunctionCallbackInfo<Value>& args)
{
	return WrappedScript::EvalMachine<unwrapExternal, userContext, returnResult>(args);
}

void WrappedScript::RunInThisContext(const FunctionCallbackInfo<Value>& args)
{
	return WrappedScript::EvalMachine<unwrapExternal, thisContext, returnResult>(args);
}

void WrappedScript::RunInNewContext(const FunctionCallbackInfo<Value>& args)
{
	return WrappedScript::EvalMachine<unwrapExternal, newContext, returnResult>(args);
}

void WrappedScript::CompileRunInContext(const FunctionCallbackInfo<Value>& args)
{
	return WrappedScript::EvalMachine<compileCode, userContext, returnResult>(args);
}

void WrappedScript::CompileRunInThisContext(const FunctionCallbackInfo<Value>& args)
{
	return WrappedScript::EvalMachine<compileCode, thisContext, returnResult>(args);
}

void WrappedScript::CompileRunInNewContext(const FunctionCallbackInfo<Value>& args)
{
	return WrappedScript::EvalMachine<compileCode, newContext, returnResult>(args);
}

template<WrappedScript::EvalInputFlags input_flag, WrappedScript::EvalContextFlags context_flag,
	WrappedScript::EvalOutputFlags output_flag>
void WrappedScript::EvalMachine(const FunctionCallbackInfo<Value>& args)
{
	// TODO: This needs a major overhaul/update. We're way behind node's impl here: https://github.com/nodejs/node/blob/master/src/node_contextify.cc
	// Additionally, we don't actually use anything other than "this" context, as far as I know.
	Isolate* isolate = args.GetIsolate();
	Local<Context> currentContext = isolate->GetCurrentContext();

	HandleScope scope(isolate);

	if (input_flag == compileCode && args.Length() < 1) {
		isolate->ThrowException(Exception::TypeError(STRING_NEW(isolate, "needs at least 'code' argument.")));
		return;
	}

	const int sandbox_index = input_flag == compileCode ? 1 : 0;
	if (context_flag == userContext && args.Length() < (sandbox_index + 1)) {
		isolate->ThrowException(Exception::TypeError(STRING_NEW(isolate, "needs a 'context' argument.")));
		return;
	}

	Local<String> code;
	if (input_flag == compileCode) code = args[0].As<String>();

	Local<Object> sandbox;
	if (context_flag == newContext) {
		sandbox = args[sandbox_index]->IsObject() ? args[sandbox_index].As<Object>() : Object::New(isolate);
	} else if (context_flag == userContext) {
		sandbox = args[sandbox_index].As<Object>();
	}

	int filename_offset = 1;
	if (context_flag == thisContext) {
		filename_offset = 0;
	}

	const int filename_index = sandbox_index + filename_offset;
	Local<String> filename =
		args.Length() > filename_index ? args[filename_index].As<String>() : STRING_NEW(isolate, "evalmachine.<anonymous>");

	const int display_error_index = args.Length() - 1;
	bool display_error = false;
	if (args.Length() > display_error_index && args[display_error_index]->IsBoolean()
		&& args[display_error_index]->BooleanValue(currentContext).FromMaybe(false) == true) {
		display_error = true;
	}

	Persistent<Context> context;

	Local<Array> keys;
	unsigned int i;
	WrappedContext *nContext = NULL;

	if (context_flag == newContext) {
		// Create the new context
		context.Reset(isolate, Context::New(isolate));
	} else if (context_flag == userContext) {
		// Use the passed in context
		MaybeLocal<Object> contextArg = args[sandbox_index]->ToObject(currentContext);
		if (contextArg.IsEmpty()) {
			// FIXME Will this ever happen? This is not likley and probably the wrong way to handle this. We should at least log it...
			context.Reset(isolate, Context::New(isolate));
		} else {
			nContext = WrappedContext::Unwrap(isolate, contextArg.ToLocalChecked());
			context.Reset(isolate, nContext->context_);
		}
	}

	// Explicitly set up var to track context we shoudl use for compile/run of script.
	// When "thisContext", use teh current context from the isolate. Otherwise use the context we set in the Persistent above
	Local<Context> contextToUse = (context_flag == thisContext) ? currentContext : context.Get(isolate);

	// New and user context share code. DRY it up.
	if (context_flag == userContext || context_flag == newContext) {
		// Enter the context
		context.Get(isolate)->Enter();
	}

	Local<Value> result;
	Local<Script> script;

	if (input_flag == compileCode) {
		// well, here WrappedScript::New would suffice in all cases, but maybe
		// Compile has a little better performance where possible
		ScriptOrigin origin(filename);
		MaybeLocal<Script> maybeScript = Script::Compile(contextToUse, code, &origin);
		if (maybeScript.IsEmpty()) {
			// Hack because I can't get a proper stacktrace on SyntaxError
			args.GetReturnValue().Set(v8::Undefined(isolate));
			return;
		}
		script = maybeScript.ToLocalChecked();
	} else {
		WrappedScript *n_script = NativeObject::Unwrap<WrappedScript>(args.Holder());
		if (!n_script) {
			isolate->ThrowException(Exception::Error(STRING_NEW(isolate, "Must be called as a method of Script.")));
			return;
		} else if (n_script->script_.IsEmpty()) {
			isolate->ThrowException(Exception::Error(STRING_NEW(isolate, "'this' must be a result of previous "
				"new Script(code) call.")));
			return;
		}

		script = n_script->script_.Get(isolate);
	}

	if (output_flag == returnResult) {
		MaybeLocal<Value> maybeResult = script->Run(contextToUse);
		if (maybeResult.IsEmpty()) {
			if (context_flag == newContext) {
				context.Get(isolate)->DetachGlobal();
				context.Get(isolate)->Exit();
				context.Reset();
			}
			args.GetReturnValue().Set(v8::Undefined(isolate));
			return;
		}
		result = maybeResult.ToLocalChecked();
	} else {
		WrappedScript *n_script = NativeObject::Unwrap<WrappedScript>(args.Holder());
		if (!n_script) {
			isolate->ThrowException(Exception::Error(STRING_NEW(isolate, "Must be called as a method of Script.")));
			return;
		}
		n_script->script_.Reset(isolate, script);
		result = args.This();
	}

	if (context_flag == newContext) {
		// Clean up, clean up, everybody everywhere!
		context.Get(isolate)->DetachGlobal();
		context.Get(isolate)->Exit();
		context.Reset();
	} else if (context_flag == userContext) {
		// Exit the passed in context.
		context.Get(isolate)->Exit();
	}

	if (result->IsObject()) {
		Local<Context> creation = result.As<Object>()->CreationContext();
	}

	args.GetReturnValue().Set(result);
}

void ScriptsModule::Initialize(Local<Object> target, Local<Context> context)
{
	Isolate* isolate = context->GetIsolate();
	HandleScope scope(isolate);
	WrappedContext::Initialize(target, context);
	WrappedScript::Initialize(target, context);
}

void ScriptsModule::Dispose(Isolate* isolate)
{
	WrappedScript::constructor_template[isolate].Reset();
	WrappedScript::constructor_template.erase(isolate);
	WrappedContext::global_template[isolate].Reset();
	WrappedContext::global_template.erase(isolate);
}

}
