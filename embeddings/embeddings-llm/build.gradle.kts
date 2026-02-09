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
                api(project(":prompt:prompt-llm"))
                api(project(":prompt:prompt-executor:prompt-executor-clients"))
                api(project(":embeddings:embeddings-base"))
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.json)
                api(libs.ktor.client.content.negotiation)
                api(libs.ktor.serialization.kotlinx.json)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
                implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-google-client"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
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
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }

    explicitApi()
}

publishToMaven()
