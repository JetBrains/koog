import ai.koog.gradle.publish.maven.Publishing.publishToMaven

val isBeta by extra(true)

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":koog-agents"))
                api(project(":koog-agents-additions"))
                api(project(":utils"))
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.json)
                api(libs.ktor.server.core)
                implementation(project(":http-client:http-client-ktor"))
            }
        }

        jvmMain {
            dependencies {
                api(project(":agents:agents-mcp"))
            }
        }

        commonTest {
            dependencies {
                implementation(project(":test-utils"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.engine.defaults)
                implementation(libs.ktor.server.test.host)
            }
        }

        jsTest {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.ktor.server.config.yaml)
            }
        }
    }

    explicitApi()
}

publishToMaven()
