import ai.koog.gradle.publish.maven.Publishing.publishToMaven

group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlinx.serialization.core)
                api(libs.jetbrains.annotations)
                api(project(":agents:agents-utils"))
            }
        }
    }

    explicitApi()
}

publishToMaven()
