import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.reload.gradle.ComposeHotRun
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.hotReload)
}

kotlin {
    jvmToolchain(libs.versions.jdkVersion.get().toInt())

    androidTarget {
        // https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-test.html
        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.animation)
            implementation(compose.animationGraphics)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(compose.uiUtil)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.navigation.compose)
            implementation(libs.koin.compose)
            when (rootProject.name) {
                "koog-agents" -> implementation(project(":koog-agents"))
                "koog-demo-compose-app" -> implementation("ai.koog:koog-agents:0.4.1")
                else -> throw Error("Unexpected project name: ${rootProject.name}")
            }
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(project.dependencies.platform(libs.koin.bom))
        }

        androidMain.dependencies {
            implementation(compose.uiTooling)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.kotlinx.coroutines.android)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

android {
    namespace = "com.jetbrains.example.kotlin.agents.demo.app"
    compileSdk = 36

    buildFeatures {
        compose = true
    }

    defaultConfig {
        applicationId = "com.jetbrains.example.kotlin.agents.demo.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Koog Demo App"
            packageVersion = "1.0.0"

            linux {
                // iconFile.set(project.file("desktopAppIcons/LinuxIcon.png"))
            }
            windows {
                // iconFile.set(project.file("desktopAppIcons/WindowsIcon.ico"))
            }
            macOS {
                // iconFile.set(project.file("desktopAppIcons/MacosIcon.icns"))
                bundleID = "com.jetbrains.example.kotlin.agents.demo.app.desktopApp"
            }
        }
    }
}

tasks.withType<ComposeHotRun>().configureEach {
    mainClass = "MainKt"
}

configurations.all {
    // FIXME exclude netty from Koog dependencies?
    exclude(group = "io.netty", module = "*")
}
