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
                api(project(":agents:agents-tools"))
                api(project(":agents:agents-utils"))
                api(project(":prompt:prompt-executor:prompt-executor-clients"))
                api(project(":prompt:prompt-llm"))
                api(project(":prompt:prompt-model"))
                api(libs.kotlinx.coroutines.core)
                api(libs.ktor.client.content.negotiation)
                api(libs.ktor.serialization.kotlinx.json)
                api(libs.ktor.client.logging)
                api(libs.kotlinx.datetime)
                implementation(libs.oshai.kotlin.logging)
            }
        }

        jvmMain {
            dependencies {
                api(libs.ktor.client.cio)
            }
        }

        jsMain {
            dependencies {
                api(libs.ktor.client.js)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
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
