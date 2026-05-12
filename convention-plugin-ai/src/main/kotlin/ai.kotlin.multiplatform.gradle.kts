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
    androidTarget()

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
    publications.withType(MavenPublication::class).all {
        if (name.contains("jvm", ignoreCase = true)) {
            artifact(javadocJar)
        }
    }
}

val isUnderTeamCity = System.getenv("TEAMCITY_VERSION") != null
signing {
    if (isUnderTeamCity) {
        signatories = GpgSignSignatoryProvider()
        sign(publishing.publications)
    }
}
