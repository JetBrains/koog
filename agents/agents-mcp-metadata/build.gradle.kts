import ai.koog.gradle.publish.maven.Publishing.publishToMaven


plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }

    explicitApi()
}

publishToMaven()
