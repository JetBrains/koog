@file:OptIn(ExperimentalWasmDsl::class)

import ai.koog.gradle.publish.maven.configureJvmJarManifest
import ai.koog.gradle.tests.configureTests
import jetbrains.sign.GpgSignSignatoryProvider
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    `maven-publish`
    id("ai.kotlin.configuration")
    id("ai.kotlin.dokka")
    id("signing")
}

kotlin {
    jvm {
        configureTests()
    }

    compilerOptions {
        coreLibrariesVersion = "2.1.21"
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
