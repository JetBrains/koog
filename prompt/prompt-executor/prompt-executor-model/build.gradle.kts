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
                api(project(":prompt:prompt-llm"))
                api(project(":prompt:prompt-executor:prompt-executor-clients"))
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
                implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
                implementation(
                    project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-anthropic-client")
                )
                implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-google-client"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmTest {
            dependencies {
                implementation(project(":test-utils"))
                implementation(libs.mockito.junit.jupiter)
                implementation(libs.assertj.core)
            }
        }
    }

    explicitApi()
}

publishToMaven()
