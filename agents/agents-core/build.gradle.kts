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
                api(project(":agents:agents-utils"))
                api(project(":prompt:prompt-executor:prompt-executor-model"))
                api(project(":prompt:prompt-llm"))
                api(project(":prompt:prompt-structure"))

                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
                api(project(":prompt:prompt-markdown"))

                api(libs.kotlinx.datetime)
                api(libs.kotlinx.io.core)
                api(libs.kotlinx.serialization.json)
                api(libs.ktor.client.content.negotiation)
                api(libs.ktor.client.logging)
                api(libs.ktor.serialization.kotlinx.json)
                api(libs.ktor.server.sse)
                api(libs.ktor.server.cio)

                implementation(libs.oshai.kotlin.logging)
            }
        }

        commonTest {
            dependencies {
                implementation(project(":agents:agents-features:agents-features-event-handler"))
                implementation(project(":agents:agents-test"))
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation("org.jetbrains.lincheck:lincheck:3.1")
                implementation(libs.junit.jupiter.params)
            }
        }
    }

    explicitApi()
}

publishToMaven()
