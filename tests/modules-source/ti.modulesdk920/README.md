
# Summary

This module is built with Titanium SDK 9.2.0.

Used by the unit test suite to verify module backward compatibility support when
running with a newer Titanium version (ex: Titanium SDK 10.0.0).

# How to Build

Prerequisite: Install Titanium SDK 9.2.0.GA

Instructions:
1. Open the Terminal.
2. `CD` to this folder.
3. Enter `./build.sh` to build for Android and iOS.
4. Commit the zip files under the `./*/dist` folder. (Will be extracted)

# Note

After doing a build, commit the built zip files under the `./android/dist` and `./ios/dist`
to the repo. These zip file will be automatically extracted to the generated test app.
