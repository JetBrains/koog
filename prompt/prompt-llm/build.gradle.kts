import ai.koog.gradle.publish.maven.Publishing.publishToMaven


plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":agents:agents-utils"))
                api(libs.kotlinx.serialization.core)
                api(libs.jetbrains.annotations)
            }
        }
    }

    explicitApi()
}

publishToMaven()
