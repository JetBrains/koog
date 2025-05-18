import ai.grazie.gradle.publish.maven.Publishing.publishToGraziePublicMaven

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
                api(project(":prompt:prompt-cache:prompt-cache-model"))
                implementation(libs.ai.grazie.utils.common)
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.reactive)
                implementation(libs.lettuce.core)
            }
        }
    }

    explicitApi()
}

publishToGraziePublicMaven()
