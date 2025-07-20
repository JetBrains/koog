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
                api(project(":agents:agents-utils"))

                api(libs.kotlinx.datetime)
                api(libs.kotlinx.io.core)
                api(libs.kotlinx.serialization.json)

                api(ktorLibs.client.contentNegotiation)
                api(ktorLibs.client.logging)
                api(ktorLibs.serialization.kotlinx.json)
                api(ktorLibs.server.sse)
                api(libs.oshai.kotlin.logging)
                api(ktorLibs.server.cio)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":agents:agents-test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmMain {
            dependencies {
                api(ktorLibs.client.cio)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
            }
        }

        jsMain {
            dependencies {
                api(ktorLibs.client.js)
            }
        }

        wasmJsMain {
            dependencies {
                api(ktorLibs.client.js)
            }
        }
    }

    explicitApi()
}

publishToMaven()
