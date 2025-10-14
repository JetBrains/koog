import ai.koog.gradle.publish.maven.Publishing.publishToMaven

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

group = rootProject.group
version = rootProject.version

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlinx.serialization.json)
                api(libs.jetbrains.annotations)
                api(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.core)
                implementation(libs.oshai.kotlin.logging)
            }
        }

        jvmMain {
            dependencies {
                implementation(dependencies.platform(libs.okhttp.bom))
                implementation(libs.okhttp)
                implementation(libs.okhttp.sse)
            }
        }

        commonTest {
            dependencies {
                implementation(project(":test-utils"))
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.mock)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.sse)
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
