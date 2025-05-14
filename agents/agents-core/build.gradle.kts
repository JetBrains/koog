import ai.grazie.gradle.publish.maven.Publishing.publishToGraziePublicMaven

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

                implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
                implementation(project(":prompt:prompt-markdown"))

                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.oshai.kotlin.logging)
            }
        }

        commonTest {
            dependencies {
                api(project(":agents:agents-features:agents-features-event-handler"))

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

publishToGraziePublicMaven()
