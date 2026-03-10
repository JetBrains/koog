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
                api(project(":prompt:prompt-model"))
                api(project(":prompt:prompt-structure"))
                api(project(":prompt:prompt-executor:prompt-executor-clients"))
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-anthropic-client"))
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-bedrock-client"))
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-deepseek-client"))
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-google-client"))
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-mistralai-client"))
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-ollama-client"))
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client-base"))
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openrouter-client"))
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-dashscope-client"))
                api(libs.kotlinx.coroutines.core)
                api(libs.oshai.kotlin.logging)
            }
        }

        jvmMain {
            dependencies {
                api(libs.kotlinx.coroutines.jdk9)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
            }
        }

        commonTest {
            dependencies {
                implementation(project(":agents:agents-test"))
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }

    explicitApi()
}

publishToMaven()
