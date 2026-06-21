# Stopwatch Android

A compact native Android stopwatch app built in Java.

## Features

- Large stopwatch dial with centisecond display
- Start, stop, continue, reset, and lap controls
- Lap table with split and total columns
- Dark clock-style UI
- Hidden alternate lap-header mode

## Build

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="$HOME/Library/Android/sdk" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
./gradlew assembleDebug
```

The debug APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install

```sh
$HOME/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```
