/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-2015 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 *
 * Original code Copyright 2009 Ryan Dahl <ry@tinyclouds.org>
 */
#ifndef EVENT_EMITTER_H
#define EVENT_EMITTER_H

#include <map>

#include <v8.h>

#include "NativeObject.h"

namespace titanium {

using v8::Persistent;
using v8::Local;
using v8::Context;
using v8::Value;
using v8::String;
using v8::FunctionTemplate;
using v8::FunctionCallbackInfo;

// The base class for any object that emits events.
// Provides an interface for listening to and firing events.
// See events.js in the common runtime which implements most
// of the logic for managing listeners and dispatching events.
class EventEmitter : public NativeObject
{
public:
	static std::map<v8::Isolate *, Persistent<FunctionTemplate>> constructorTemplate;
	static std::map<v8::Isolate *, Persistent<String>> emitSymbol;

	static void eventEmitterConstructor(const FunctionCallbackInfo<Value>& args);
	static void initTemplate(v8::Isolate* isolate);
	static void dispose(v8::Isolate*);

protected:
	EventEmitter()
		: NativeObject()
	{
	}

	virtual ~EventEmitter()
	{
	}
};

}
#endif
