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
                api(project(":a2a:a2a-transport:a2a-transport-core-jsonrpc"))
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.coroutines.core)
            }
        }

        jvmMain {
            dependencies {
                api(libs.ktor.server.core)
                api(libs.ktor.server.sse)
                api(libs.ktor.server.content.negotiation)
                api(libs.ktor.serialization.kotlinx.json)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
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
