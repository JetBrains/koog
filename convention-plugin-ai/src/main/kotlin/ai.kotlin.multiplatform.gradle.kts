@file:OptIn(ExperimentalWasmDsl::class)

import ai.koog.gradle.publish.maven.configureJvmJarManifest
import ai.koog.gradle.tests.configureTests
import ai.koog.gradle.xcframework.XCFrameworkConfig.configureXCFrameworkIfRequested
import jetbrains.sign.GpgSignSignatoryProvider
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    `maven-publish`
    id("ai.kotlin.configuration")
    id("ai.kotlin.dokka")
    id("com.android.library")
    id("signing")
}

// Per-module opt-out for the wasmJs target. Modules that depend on libraries with no wasmJs publication,
// for example, the "OTel Kotlin SDK 0.3.0" set `koog.target.wasmJs=false` in their gradle.properties.
// The target is not registered at all, and no wasm-js publication is produced.
val isWasmJsIncluded = (findProperty("koog.target.wasmJs") as? String)?.toBoolean() ?: true

kotlin {
    // Tiers are in accordance with <https://kotlinlang.org/docs/native-target-support.html>
    // Tier 1
    iosSimulatorArm64()
    iosArm64()

    // Tier 2

    // Tier 3
    iosX64()

    // Configure XCFramework for iOS targets (opt-in via -Pkoog.build.xcframework=true)
    configureXCFrameworkIfRequested(project)

    // Android
    androidTarget {
        // Without this, no Android variants are published to Maven, so androidMain
        // sources would be missing from the published artifacts
        // and only commonMain (e.g. Stub) would be accessible to Android consumers.
        publishLibraryVariants("release")
    }

    // jvm & js
    jvm {
        configureTests()
    }

    js(IR) {
        browser {
            binaries.library()
        }

        configureTests()
    }

    if (isWasmJsIncluded) {
        wasmJs {
            browser()
            nodejs()
            binaries.library()
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        /*
         Source set to share the code that is common to all non-JVM targets.
         */
        val nonJvmCommonMain by creating {
            dependsOn(commonMain.get())
        }

        val nonJvmCommonTest by creating {
            dependsOn(commonTest.get())
        }

        /*
          Source set to share the code between JVM and Android targets, since they both support certain JVM features.
         */
        val jvmCommonMain by creating {
            dependsOn(commonMain.get())
        }

        val jvmCommonTest by creating {
            dependsOn(commonTest.get())
        }

        appleMain {
            dependsOn(nonJvmCommonMain)
        }

        appleTest {
            dependsOn(nonJvmCommonTest)
        }

        jsMain {
            dependsOn(nonJvmCommonMain)
        }

        jsTest {
            dependsOn(nonJvmCommonTest)
        }

        if (isWasmJsIncluded) {
            wasmJsMain {
                dependsOn(nonJvmCommonMain)
            }

            wasmJsTest {
                dependsOn(nonJvmCommonTest)
            }
        }

        jvmMain {
            dependsOn(jvmCommonMain)
        }

        jvmTest {
            dependsOn(jvmCommonTest)
        }

        androidMain {
            dependsOn(jvmCommonMain)
        }

        androidUnitTest {
            dependsOn(jvmCommonTest)

            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
    }

}

android {
    compileSdk = 36
    namespace = "${project.group.toString().replace('-', '.')}.${project.name.replace('-', '.')}"

    // Without an explicit minSdk, AGP falls back to its default (`1`), which propagates
    // into the published AAR's merged manifest and forces every consumer to override it.
    // It also breaks transitive deps that require a higher minSdk (e.g. LiteRT requires 24+).
    defaultConfig {
        minSdk = 35
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

configureJvmJarManifest("jvmJar")

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

publishing {
    // Attach the javadoc jar to JVM, Android, and the root multiplatform publications.
    // Maven Central requires a `-javadoc.jar` next to every published artifact, including
    // the Android one produced via `publishLibraryVariants("release")`.
    publications.withType(MavenPublication::class).configureEach {
        if (
            name.contains("jvm", ignoreCase = true) ||
            name.contains("android", ignoreCase = true) ||
            name == "kotlinMultiplatform"
        ) {
            artifact(javadocJar)
        }
    }
}

val isUnderTeamCity = System.getenv("TEAMCITY_VERSION") != null
signing {
    if (isUnderTeamCity) {
        signatories = GpgSignSignatoryProvider()
        // Sign publications lazily as they are created. KMP and AGP add publications
        // (in particular the `*-android` one for `publishLibraryVariants("release")`)
        // after this block evaluates, so an eager `sign(publishing.publications)` call
        // could miss them and silently produce unsigned artifacts on CI.
        publishing.publications.withType(MavenPublication::class).configureEach {
            sign(this)
        }
    }
}
