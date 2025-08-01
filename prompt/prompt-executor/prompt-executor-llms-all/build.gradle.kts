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
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-anthropic-client"))
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-google-client"))
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openrouter-client"))
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-ollama-client"))
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-bedrock-client"))
                api(project(":prompt:prompt-executor:prompt-executor-llms"))
                api(project(":agents:agents-core"))
                api(project(":agents:agents-ext"))
                api(project(":agents:agents-tools"))
                api(project(":agents:agents-features:agents-features-event-handler"))
                api(project(":agents:agents-features:agents-features-trace"))
                api(project(":prompt:prompt-llm"))
                api(project(":prompt:prompt-model"))
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.json)
                api(libs.ktor.client.content.negotiation)
            }
        }
        jvmMain {
            dependencies {
                api(libs.ktor.client.cio)
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
                implementation(libs.ktor.client.mock)
                runtimeOnly(libs.slf4j.simple)
            }
        }
    }

    explicitApi()
}

publishToMaven()
