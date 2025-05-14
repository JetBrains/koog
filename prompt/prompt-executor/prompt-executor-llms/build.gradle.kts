import ai.grazie.gradle.publish.maven.Publishing.publishToGraziePublicMaven

group = "${rootProject.group}.prompt"
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
                implementation(project(":agents:agents-tools"))
                implementation(project(":prompt:prompt-llm"))
                implementation(project(":prompt:prompt-model"))
                implementation(libs.ai.grazie.utils.common)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
                implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-anthropic-client"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))

                implementation(libs.ktor.client.cio)
            }
        }
    }

    explicitApi()
}

publishToGraziePublicMaven()
