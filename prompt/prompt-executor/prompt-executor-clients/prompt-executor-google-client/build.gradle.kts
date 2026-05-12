import ai.koog.gradle.publish.maven.Publishing.publishToMaven


plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":agents:agents-tools"))
                api(project(":http-client:http-client-core"))
                api(project(":utils"))
                api(project(":prompt:prompt-executor:prompt-executor-clients"))
                api(project(":prompt:prompt-llm"))
                api(project(":prompt:prompt-model"))
                api(project(":prompt:prompt-structure"))
                api(libs.kotlinx.coroutines.core)
                implementation(libs.oshai.kotlin.logging)
            }
        }

        jsTest {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }

        commonTest {
            dependencies {
                implementation(project(":test-utils"))
            }
        }

        jvmTest {
            dependencies {
                implementation(project(":http-client:http-client-ktor"))
                implementation(libs.ktor.client.mock)
                implementation(libs.ktor.client.cio)
            }
        }
    }

    explicitApi()
}

publishToMaven()
