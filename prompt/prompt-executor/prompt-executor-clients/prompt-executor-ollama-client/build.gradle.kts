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
                api(project(":agents:agents-tools"))
                api(project(":prompt:prompt-llm"))
                api(project(":prompt:prompt-model"))
                api(project(":prompt:prompt-tokenizer"))
                api(project(":agents:agents-tools"))
                api(project(":prompt:prompt-executor:prompt-executor-model"))
                api(project(":prompt:prompt-executor:prompt-executor-clients"))
                api(project(":prompt:prompt-executor:prompt-executor-llms"))
                api(project(":embeddings:embeddings-base"))

                api(libs.ktor.client.logging)
                api(libs.kotlinx.datetime)
                api(libs.kotlinx.coroutines.core)
                api(libs.ktor.client.content.negotiation)
                api(libs.ktor.serialization.kotlinx.json)
                api(libs.ktor.client.cio)
                implementation(libs.oshai.kotlin.logging)
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
                implementation(project(":agents:agents-features:agents-features-event-handler"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(project(":agents:agents-core"))
                implementation(project(":agents:agents-features:agents-features-event-handler"))
                implementation(project(":agents:agents-features:agents-features-trace"))
                implementation(project(":integration-tests"))
            }
        }
    }

    explicitApi()
}

publishToMaven()
