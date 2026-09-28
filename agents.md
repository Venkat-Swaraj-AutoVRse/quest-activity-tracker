# Agent Documentation: Quest Activity Tracker

Welcome, Agent! This document explains the architecture, constraints, workflows, and past bug fixes for the Quest Activity Tracker project. Read this before writing any code.

## 1. Project Goal
A sideloaded, native 2D Android application for Meta Quest 3/3S headsets that tracks screen time usage (playtime, launch counts, last active timestamp) for user applications and VR experiences, featuring an embedded web companion server to view, monitor, and export statistics from a browser.

## 2. Technical Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (BOM `2023.10.01` to fix composition crashes)
- **Embedded Server**: Ktor (Netty engine, port `8080`, Gson serialization)
- **Min SDK**: 29 (Android 10 / Horizon OS)
- **Target SDK**: 34 (Android 14)
- **Data Source**: Android `UsageStatsManager`

---

## 3. Key Architecture & Customizations

### A. Resizable 2D Panel Configuration (Oculus Manifest Rules)
In [AndroidManifest.xml](app/src/main/AndroidManifest.xml):
- **NO `com.oculus.intent.category.VR` in intent-filters**: Forces immersive launch causing infinite black loading screen since there is no active OpenXR session.
- **Focus Aware**: Added `<meta-data android:name="com.oculus.vr.focusaware" android:value="true"/>`.
- **Application Type**: Set `<meta-data android:name="com.oculus.application.type" android:value="panel"/>`.
- **Resizable Window**: Configured layout boundaries for resizable panel display.

### B. Reflection for Hidden APIs
In [UsageTrackerHelper.kt](app/src/main/java/com/meta/quest/activitytracker/model/UsageTrackerHelper.kt):
- `UsageStats` hidden field `mLaunchCount` (or method `getAppLaunchCount`) is retrieved dynamically at runtime using Java Reflection to overcome SDK differences in Horizon OS.

### C. Ktor Embedded Web Server Service
In [UsageServerService.kt](app/src/main/java/com/meta/quest/activitytracker/service/UsageServerService.kt):
- Runs as an Android **Foreground Service** (`dataSync` type on API 34+) to remain active in the background when the user is playing immersive games.
- Serves:
  - `/api/usage`: returns usage stats mapped to an `AppUsageInfoDTO` (DTO excludes the Compose `ImageBitmap` icons to prevent circular reference crashes during Gson serialization).
  - `/`: serves static dashboard files from Android assets (`assets/web/`).
- Lifecycle status is exposed via the static `UsageServerService.isRunning` flag.
- Wi-Fi IP is resolved via `getLocalIpAddress()` in [ScreenTimeDashboard.kt](app/src/main/java/com/meta/quest/activitytracker/ui/ScreenTimeDashboard.kt) which uses a `try-catch` around `WifiManager` connection info; if `ACCESS_WIFI_STATE` is denied, it silently falls back to looping available `NetworkInterface` parameters without throwing exceptions.

### D. System Package Filtering
- Aggregations are filtered inside `shouldIgnorePackage()` to exclude background VR shells, system managers, and other services that aren't user applications. Ignored prefixes/wildcards include:
  - `com.oculus.*` (Oculus Shell, SystemUX, Browser, Store, AlertService, Metacam, etc.)
  - `com.android.*` & `android.*` (Android core services)
  - `horizon.*` & `horizonos.*` (Horizon OS platform services)
  - `com.autovrse.quest.activitytracker` (self-exclusion)
  - Background Meta diagnostic & testing services (`com.meta.systemui`, `com.meta.pclinkservice`, `com.meta.automation.*`, etc.)

---

## 4. Deploy & Run Workflow
Since the application queries `UsageStatsManager`, it requires restricted permissions granted via ADB.

1. **Build the Debug APK**:
   ```powershell
   .\gradlew.bat assembleDebug
   ```
2. **Install to Device**:
   ```powershell
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
3. **Grant Appops Permission** (Critical - permission resets on reinstall):
   ```powershell
   adb shell appops set com.autovrse.quest.activitytracker android:get_usage_stats allow
   ```
4. **Launch Application**:
   ```powershell
   adb shell am start -n com.autovrse.quest.activitytracker/.MainActivity
   ```

### 5. Web Companion Access Workflows
- **Direct Access**: Navigate to `http://<quest-ip>:8080` (both devices must be on the same local Wi-Fi network).
- **USB/ADB Forwarding**: Connect headset via USB and run the companion launcher:
  ```powershell
  .\start-companion.bat
  ```
  This automatically runs `adb forward tcp:8080 tcp:8080` and opens `http://localhost:8080`.

---

## 6. Lessons Learned & Fixed Issues

- **Theme Crash**: System Material3 DayNight themes are not always available on Horizon OS. Use AppCompat themes in [themes.xml](app/src/main/res/values/themes.xml).
- **Compose BOM Version Crash**: Downgraded to BOM `2023.10.01` to resolve a `NoSuchMethodError` inside `CircularProgressIndicator`'s material3 component.
- **Ktor Netty Duplicate Package Build Failures**: Added packaging rules in `app/build.gradle.kts` to exclude `META-INF/INDEX.LIST` and `META-INF/io.netty.versions.properties` to resolve AGP merge resource compilation errors.
- **WifiManager ACCESS_WIFI_STATE Crash**: Wrapped connection lookup in `try-catch` and added the permission in the manifest to resolve SecurityExceptions during local IP parsing.
- **Icon Serialization Crash**: Created `AppUsageInfoDTO` mapping inside `UsageServerService` route handler to filter out `ImageBitmap` icons before serialization.
