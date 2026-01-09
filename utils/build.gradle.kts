import ai.koog.gradle.publish.maven.Publishing.publishToMaven

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

group = rootProject.group
version = rootProject.version

kotlin {
    jvm()
    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlinx.serialization.json)
                api(libs.jetbrains.annotations)
                api(libs.kotlinx.coroutines.core)
                implementation(libs.oshai.kotlin.logging)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
            }
        }
    }

    explicitApi()
}

publishToMaven()
