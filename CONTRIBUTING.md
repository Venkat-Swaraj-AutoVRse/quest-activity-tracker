# Contributing to Quest Activity Tracker

Thanks for your interest in contributing! This document explains how to set up
the project, the workflow for changes, and the conventions we follow.

## Prerequisites

- **JDK 17 or newer** (JDK 21 recommended). Make sure `JAVA_HOME` points at it.
- **Android SDK** with API level 34 and build-tools `34.0.0`.
  Android Studio installs these for you; command-line users can run
  `sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"`.
- A **Meta Quest 3 / 3S** in Developer Mode (for on-device testing) and `adb`.

> The project intentionally does **not** commit `org.gradle.java.home` in
> `gradle.properties`. Gradle uses your `JAVA_HOME`, so builds work across
> Windows/macOS/Linux without edits.

## Building

```bash
./gradlew assembleDebug        # macOS/Linux
.\gradlew.bat assembleDebug    # Windows
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Installing on a device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Usage-stats access must be granted manually (resets on reinstall):
adb shell appops set com.autovrse.quest.activitytracker android:get_usage_stats allow
adb shell am start -n com.autovrse.quest.activitytracker/.MainActivity
```

## Making changes

1. Fork the repo and create a topic branch: `git checkout -b my-feature`.
2. Make your change. Keep the Kotlin style consistent with the surrounding code.
3. Verify it builds: `./gradlew assembleDebug`.
4. Test on a device or the XR simulator where practical.
5. Commit with a clear message and open a Pull Request against `main`.

CI (GitHub Actions) builds every push/PR to `main`; a green build is required.

## Reporting bugs / requesting features

Please open a GitHub Issue with steps to reproduce, expected vs. actual
behavior, and your headset/OS version where relevant.

## Code of Conduct

By participating you agree to abide by the [Code of Conduct](CODE_OF_CONDUCT.md).
