import org.jetbrains.compose.reload.gradle.ComposeHotRun
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(libs.versions.javaVersion.get().toInt())

    android {
        namespace = "com.jetbrains.example.koog.share.ui"
        compileSdk = libs.versions.build.android.compileSdk.get().toInt()
        minSdk = libs.versions.build.android.minSdk.get().toInt()
    }

    jvm()

    iosArm64()
    iosSimulatorArm64()

    js { browser() }
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.datastore.core.okio)
            implementation(libs.jetbrains.compose.animation)
            implementation(libs.jetbrains.compose.animation.graphics)
            implementation(libs.jetbrains.compose.components.resources)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.material.icons.extended)
            implementation(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.ui)
            implementation(libs.jetbrains.compose.ui.tooling.preview)
            implementation(libs.jetbrains.compose.ui.util)
            implementation(libs.jetbrains.lifecycle.viewmodel.navigation3)
            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.koin.compose)
            implementation(libs.koog.agents)
            implementation(libs.koog.agents.additions)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.markdown.renderer)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(project.dependencies.platform(libs.ktor.bom))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.jetbrains.compose.ui.test)
        }

        androidMain.dependencies {
            implementation(libs.jetbrains.compose.ui.tooling)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.apache5)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        webMain.dependencies {
            implementation(libs.ktor.client.js)
        }

        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }

    targets
        .withType<KotlinNativeTarget>()
        .matching { it.konanTarget.family.isAppleFamily }
        .configureEach {
            binaries { framework { baseName = "commonApp" } }
        }
}

tasks.withType<ComposeHotRun>().configureEach {
    mainClass = "MainKt"
}
