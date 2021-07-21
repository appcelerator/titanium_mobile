#!/bin/sh

cd android
appc run -p android --build-only -s 9.2.0.GA
cd ../ios
appc run -p ios --build-only -s 9.2.0.GA
