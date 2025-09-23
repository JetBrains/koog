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
- run `./gradlew :app:assembleDebug`
- find `.apk` file in `app/build/outputs/apk/debug/app-debug.apk`

### Desktop
Run the desktop application: `./gradlew :app:run`  
Run the desktop **hot reload** application: `./gradlew :app:hotRunJvm`

### iOS
To run the application on iPhone device/simulator:
- Open `iosApp/iosApp.xcproject` in Xcode and run standard configuration
- Or use [Kotlin Multiplatform Mobile plugin](https://plugins.jetbrains.com/plugin/14936-kotlin-multiplatform-mobile) for Android Studio
