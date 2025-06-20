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
                api(project(":agents:agents-features:agents-features-common"))
                api(project(":agents:agents-tools"))
                api(project(":agents:agents-utils"))
                api(project(":prompt:prompt-executor:prompt-executor-model"))
                api(project(":prompt:prompt-llm"))
                api(project(":prompt:prompt-structure"))

                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
                api(project(":prompt:prompt-markdown"))

                api(libs.kotlinx.datetime)
                api(libs.kotlinx.serialization.json)
                api(libs.ktor.client.content.negotiation)
                api(libs.ktor.client.logging)
                api(libs.ktor.serialization.kotlinx.json)
                implementation(libs.oshai.kotlin.logging)
            }
        }

        commonTest {
            dependencies {
                implementation(project(":agents:agents-features:agents-features-event-handler"))

                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(project(":agents:agents-test"))
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
            }
        }
    }

    explicitApi()
}

publishToMaven()
