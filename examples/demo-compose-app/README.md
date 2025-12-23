# Demo Compose App

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
- run `./gradlew :androidApp:assembleDebug :androidApp:assembleRelease`
- find `.apk` file in `androidApp/build/outputs/apk/debug/app-debug.apk`

### Desktop
Run the desktop application: `./gradlew :desktopApp:run`  
Run the desktop **hot reload** application: `./gradlew :desktopApp:hotRun`

### iOS
To run the application on iPhone device/simulator:
- Open `iosApp/iosApp.xcproject` in Xcode and run standard configuration
- Or use [Kotlin Multiplatform Mobile plugin](https://plugins.jetbrains.com/plugin/14936-kotlin-multiplatform-mobile) for Android Studio

### Web Distribution
Build web pack: `./gradlew :webApp:jsBrowserDevelopmentWebpack`  
Deploy a dir `webApp/build/dist/composeWebCompatibility/productionExecutable` to a web server

### JS Browser
Run the browser application: `./gradlew :webApp:jsBrowserDevelopmentRun --continue`

### Wasm Browser
Run the browser application: `./gradlew :webApp:wasmJsBrowserDevelopmentRun --continue`
