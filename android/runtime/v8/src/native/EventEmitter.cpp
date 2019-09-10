/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-2018 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 *
 * Original code Copyright 2009 Ryan Dahl <ry@tinyclouds.org>
 */
#include <map>

#include "EventEmitter.h"
#include "V8Util.h"

#define TAG "EventEmitter"

using namespace v8;

namespace titanium {

std::map<Isolate *, Persistent<FunctionTemplate>> EventEmitter::constructorTemplate;

std::map<Isolate *, Persistent<String>> EventEmitter::emitSymbol;

void EventEmitter::eventEmitterConstructor(const FunctionCallbackInfo<Value>& args)
{
	HandleScope scope(args.GetIsolate());
	EventEmitter *emitter = new EventEmitter();
	emitter->Wrap(args.This());
}

void EventEmitter::initTemplate(Isolate* isolate)
{
	HandleScope scope(isolate);
	Local<FunctionTemplate> constructor = FunctionTemplate::New(isolate, eventEmitterConstructor);
	constructor->InstanceTemplate()->SetInternalFieldCount(1);
	constructor->SetClassName(NEW_SYMBOL(isolate, "EventEmitter"));
	constructorTemplate[isolate].Reset(isolate, constructor);

	emitSymbol[isolate].Reset(isolate, NEW_SYMBOL(isolate, "emit"));
}

void EventEmitter::dispose(Isolate* isolate)
{
	constructorTemplate[isolate].Reset();
	constructorTemplate.erase(isolate);
	emitSymbol[isolate].Reset();
	emitSymbol.erase(isolate);
}

} // namespace titanium
