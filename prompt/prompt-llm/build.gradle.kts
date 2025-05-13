import ai.grazie.gradle.publish.maven.Publishing.publishToGraziePublicMaven

group = "${rootProject.group}.prompt"
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.serialization.core)
            }
        }
    }

    explicitApi()
}

publishToGraziePublicMaven()
