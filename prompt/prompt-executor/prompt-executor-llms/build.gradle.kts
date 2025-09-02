import ai.koog.gradle.publish.maven.Publishing.publishToMaven

group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":prompt:prompt-executor:prompt-executor-clients"))
                api(project(":prompt:prompt-executor:prompt-executor-model"))
                api(project(":agents:agents-tools"))
                api(project(":prompt:prompt-llm"))
                api(project(":prompt:prompt-model"))
                api(libs.kotlinx.coroutines.core)
                implementation(libs.oshai.kotlin.logging)
            }
        }
        commonTest {
            dependencies {
                api(project(":agents:agents-test"))
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
