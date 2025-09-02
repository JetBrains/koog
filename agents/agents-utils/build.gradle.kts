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
                api(libs.jetbrains.annotations)
                api(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.core)
                implementation(libs.oshai.kotlin.logging)
            }
        }

        jvmMain {
        }

        commonTest {
            dependencies {
                implementation(project(":test-utils"))
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.mock)
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
