import ai.koog.gradle.publish.maven.Publishing.publishToMaven

group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()

    js(IR) {
        browser()
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":a2a:a2a-core"))
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.coroutines.core)
                api(libs.ktor.client.core)
                api(libs.ktor.client.content.negotiation)
                api(libs.ktor.serialization.kotlinx.json)
                implementation(libs.oshai.kotlin.logging)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
            }
        }

        jsTest {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }

    explicitApi()
}

publishToMaven()
