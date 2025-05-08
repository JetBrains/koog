import ai.grazie.gradle.publish.maven.publishToGraziePublicMaven

group = "${rootProject.group}.agents"
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
                api(project(":prompt:prompt-executor:prompt-executor-model"))
                api(project(":prompt:prompt-llm"))
                api(project(":prompt:prompt-structure"))

                implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
                implementation(project(":prompt:prompt-markdown"))

                implementation(libs.ai.grazie.api.gateway.client)
                implementation(libs.ai.grazie.client.ktor)
                implementation(libs.ai.grazie.utils.common)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.serialization.kotlinx.json)
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
                implementation(project(":agents:agents-test"))

                implementation(libs.junit.jupiter.params)
                implementation(libs.mockk)
            }
        }
    }

}

publishToGraziePublicMaven()
