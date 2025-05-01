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
                api(project(":code-agents:code-agents-core"))
                api(project(":code-prompt:code-prompt-executor:code-prompt-executor-model"))
                api(project(":code-prompt:code-prompt-llm"))
                api(project(":code-prompt:code-prompt-structure"))

                implementation(project(":code-agents:code-agents-tools-registry"))
                implementation(project(":code-prompt:code-prompt-markdown"))

                implementation(libs.ai.grazie.api.gateway.client)
                implementation(libs.ai.grazie.client.ktor)
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
                implementation(project(":code-agents:code-agents-test"))
                implementation(libs.mockk)
            }
        }
    }
}

publishToGraziePublicMaven()
