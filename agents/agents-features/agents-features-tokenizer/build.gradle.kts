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
                api(project(":agents:agents-core"))
                api(project(":prompt:prompt-tokenizer"))

                api(libs.kotlinx.serialization.json)

                api(libs.ktor.client.content.negotiation)
                api(libs.ktor.serialization.kotlinx.json)
                api(libs.ktor.server.sse)
            }
        }

        jvmMain {
            dependencies {
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":agents:agents-test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit5"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.server.cio)
            }
        }
    }

    explicitApi()
}

publishToMaven()
