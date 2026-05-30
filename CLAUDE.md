# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SmartCheck AI (智能晨检系统) is an Android AIoT morning health inspection app for industrial Android boards (primarily Rockchip RK3566). It integrates face recognition, infrared temperature measurement, and hand foreign-object detection.

- **Language**: Kotlin (target JVM 17)
- **UI**: Jetpack Compose + Material3
- **Architecture**: MVVM + Clean Architecture (UI → ViewModel → UseCase → Repository → DataSource)
- **DI**: Hilt
- **Async**: Kotlin Coroutines + Flow

## Module Structure

Three Gradle modules:

| Module | Package | Purpose |
|--------|---------|---------|
| `:app` | `com.smartcheck.app` | Main application |
| `:face-sdk` | `com.smartcheck.sdk.face` | SeetaFace6 face recognition (JNI/C++) |
| `:hand-sdk` | `com.smartcheck.sdk` | RKNN hand detection (JNI/C++ + OpenCV) |

Vendor reference directories (`third_party/`, `sf6.0_android/`, `SeetaFace6-master/`) are not included in `settings.gradle.kts` and should not be edited.

## Common Commands

Prerequisites: JDK 17, Android SDK 34, NDK, CMake 3.22.1. On Windows use `gradlew.bat`; on macOS/Linux use `./gradlew`.

```bash
# Build
./gradlew clean
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :hand-sdk:assembleDebug :face-sdk:assembleDebug

# Native-only rebuild
./gradlew :hand-sdk:externalNativeBuildDebug :face-sdk:externalNativeBuildDebug

# Lint (Android Lint only; no ktlint/detekt/spotless configured)
./gradlew lint
./gradlew :app:lintDebug
./gradlew :app:lintFix

# Tests
./gradlew :app:testDebugUnitTest
./gradlew :app:testDebugUnitTest --tests "com.smartcheck.app.SomeTest"
./gradlew :app:testDebugUnitTest --tests "com.smartcheck.app.SomeTest.someMethod"
./gradlew :app:connectedDebugAndroidTest
```

View logs: `adb logcat -s Timber:*`

## Troubleshooting

- `fr_2_10.dat` is tracked via Git LFS (`face-sdk/src/main/assets/fr_2_10.dat`). If it appears as a tiny text pointer file, run `git lfs pull`.
- If native build fails, confirm NDK is installed and Gradle sees CMake 3.22.1.
- Duplicate `libc++_shared.so` packaging errors are handled by `pickFirsts` in `app/build.gradle.kts`.
- Clean and rebuild if you see stale native artifacts: `./gradlew clean && ./gradlew assembleDebug`.

## High-Level Architecture

### Data Flow

UI (`ui/screens/*`, `ui/components/*`) → ViewModel (`viewmodel/*`) → UseCase (`domain/usecase/*`) → Repository Interface (`domain/repository/*`) → Repository Implementation (`data/repository/*`) → Data Source (`data/db/*`, `data/serial/*`, `data/upload/*`, `api/*`, `ml/*`, `voice/*`)

### Dependency Injection (`di/AppModule.kt`)

- `AppModuleBinds` (abstract): binds interfaces to implementations (Repository, Auth, Voice, Temperature)
- `AppModule` (object `@Provides`): Room database (with 7 migrations v1→v8), DAOs, `FaceEngine`, `HandDetector`, `HttpClient`, `CoroutineScope`, `RecordUploadReporter`

### Navigation (`ui/navigation/AppNavigation.kt`)

Compose Navigation with route strings (not a sealed class). `startDestination = "login"`. Routes: `login` → `dashboard` → `check` | `employees` | `records` | `export` | `settings`, with detail routes like `employee_detail/{id}` and `record_detail/{id}`.

### Application Lifecycle (`App.kt`)

`@HiltAndroidApp` class that initializes in order: Timber logging (DebugTree + FileLoggingTree with rotation), `DeviceAuth`, crash handler, `HandDetector` (guarded for Rockchip only), and Ktor server (delayed start via Handler). Implements `CameraXConfig.Provider` with custom camera filter preferring IDs `"100"` and `"102"`.

### Database (`data/db/AppDatabase.kt`)

Room database version 8 with 5 entities: `UserEntity`, `RecordEntity`, `ApiTokenEntity`, `ApiAccessLogEntity`, `SystemUserEntity`. Has 7 manual migrations; `fallbackToDestructiveMigration()` is enabled.

### State Machine (Morning Check)

The core inspection flow is a strict linear state machine in `MorningCheckUseCase`:

```
IDLE → FACE_PASS → TEMP_MEASURING → HAND_CHECKING → ALL_PASS
         ↓              ↓                  ↓
      (fail)        TEMP_FAIL         HAND_FAIL
```

### ML Engines

- **FaceEngine** (`ml/FaceEngine.kt`): interface with `SeetaFaceEngine` (JNI via `:face-sdk`) and `MockFaceEngine` fallback. Initialized asynchronously in DI; crashes fallback to mock.
- **HandDetector** (`:hand-sdk`): RKNN + OpenCV JNI. Only initializes on Rockchip devices (`hardware/board/product` contains `"rk"`). Non-RK devices skip init gracefully.

### Embedded API Server (`api/KtorServerManager.kt`)

Ktor/Netty server on port 8080, auto-started in `App.onCreate()` with 1s delay. Provides REST API with JWT auth (via `java-jwt`), CORS, content negotiation (kotlinx.serialization), and access logging. Routes defined in `ApiService.configureRouting()`.

### Device Activation

`DeviceAuth` handles offline activation code validation. `HandSdkAuth` (in `:hand-sdk`) performs online MAC-based license verification against a server. Legacy devices can be exempt from MAC checks.

## Key Conventions

- **Logging**: `:app` uses Timber (`Timber.d/i/w/e`). `:hand-sdk`/`:face-sdk` use Android `Log.*`. Never mix them.
- **Coroutines**: `Dispatchers.Default` for CPU/ML work, `Dispatchers.IO` for DB/files/serial. Expose state via `StateFlow`.
- **Compose**: Reusable composables take `modifier: Modifier = Modifier`. CameraX analyzers must be off main thread.
- **JNI**: Keep glue thin. Release native resources (`AndroidBitmap_unlockPixels`, local refs). C++17 standard.
- **No lint tools configured**: rely on Android Studio formatting. Keep diffs minimal.
- **Star imports**: Preserve existing style in each file. UI files typically use star imports for Compose packages.

## Reference

- `AGENTS.md` — detailed agent coding guide (style rules, workflow expectations)
- `README.md` — project overview in Chinese
- `SETUP_GUIDE.md` — environment setup instructions
- `docs/` — 13 technical documentation files
