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
                api(project(":koog-agents"))
                api(project(":utils"))
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.json)
                api(libs.ktor.server.core)
            }
        }

        jvmMain {
            dependencies {
                api(project(":agents:agents-mcp"))
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.server.test.host)
            }
        }

        androidUnitTest {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }

        jsTest {
            dependencies {
                implementation(kotlin("test-js"))
                implementation(libs.ktor.client.js)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.cio)
            }
        }

        appleTest {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
    }

    explicitApi()
}

publishToMaven()
