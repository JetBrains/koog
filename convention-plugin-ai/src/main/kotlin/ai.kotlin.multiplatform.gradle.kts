@file:OptIn(ExperimentalWasmDsl::class)

import ai.koog.gradle.publish.maven.configureJvmJarManifest
import com.android.build.api.variant.LibraryAndroidComponentsExtension
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

kotlin {
    androidTarget()

    sourceSets {
        androidUnitTest {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
    }

    compilerOptions {
        coreLibrariesVersion = "2.1.21"
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

extensions.configure<LibraryAndroidComponentsExtension>("androidComponents") {
    beforeVariants(selector().all()) { variantBuilder ->
        val booleanType = Boolean::class.javaPrimitiveType
        if (booleanType != null) {
            runCatching {
                variantBuilder.javaClass.getMethod("setEnableUnitTest", booleanType).invoke(variantBuilder, false)
            }
            runCatching {
                variantBuilder.javaClass.getMethod("setUnitTestEnabled", booleanType).invoke(variantBuilder, false)
            }
            runCatching {
                variantBuilder.javaClass.getMethod("setEnableAndroidTest", booleanType).invoke(variantBuilder, false)
            }
            runCatching {
                variantBuilder.javaClass.getMethod("setAndroidTestEnabled", booleanType).invoke(variantBuilder, false)
            }
        }
    }
}

tasks.findByName("jvmJar")?.let { configureJvmJarManifest("jvmJar") }

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
