# Demo Compose App

> **⚡ Composite Build Notice**: This project uses Gradle's composite build feature to depend on the local root Koog project, 
> ensuring you always have the latest development version. This provides convenience and keeps everything in sync during development.
> For production use, you can replace these composite build dependencies with exact published versions from public repositories.
> Check [Composite Build Information](#-composite-build-information) below for more details.

## Overview
This is a simple demo Kotlin Multiplatform app built with Compose Multiplatform that demonstrates the capabilities of Koog, a Kotlin AI agentic framework.

## Setup
1. Open the project in IntelliJ IDEA or Android Studio
2. Build and run the application
3. Configure your API keys in the app settings

## Usage Examples

### Calculator Agent
An agent that can perform mathematical operations using tools for addition, subtraction, multiplication and division.

### Weather Agent
An agent that can provide weather information for a given location.

## Before running!
- check your system with [KDoctor](https://github.com/Kotlin/kdoctor)
- install JDK 17 or higher on your machine
- add `local.properties` file to the project root and set a path to Android SDK there

### Android
To run the application on android device/emulator:
- open project in Android Studio and run imported android run configuration

To build the application bundle:
- run `./gradlew :app:assembleDebug`
- find `.apk` file in `app/build/outputs/apk/debug/app-debug.apk`

### Desktop
Run the desktop application: `./gradlew :app:run`  
Run the desktop **hot reload** application: `./gradlew :app:hotRunJvm`

### iOS
To run the application on iPhone device/simulator:
- Open `iosApp/iosApp.xcproject` in Xcode and run standard configuration
- Or use [Kotlin Multiplatform Mobile plugin](https://plugins.jetbrains.com/plugin/14936-kotlin-multiplatform-mobile) for Android Studio


## 🔄 Composite Build Information

This project uses Gradle's composite build feature to include the root Koog project:

```kotlin
// settings.gradle.kts
includeBuild("../../.") {
    name = "koog"
}
```

This means:
- ✅ **Development**: Always uses the latest local Koog framework code
- ✅ **Convenience**: No need to publish and update versions during development
- ✅ **Sync**: Changes in the main framework are immediately available

**For production use**, replace composite build dependencies in `build.gradle.kts` with published versions:

```kotlin
// Replace this composite build approach:
implementation("ai.koog:koog-agents")

// With specific published versions:
implementation("ai.koog:koog-agents:VERSION")
```
