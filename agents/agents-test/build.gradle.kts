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
                implementation(kotlin("test"))

                implementation(project(":agents:agents-core"))
                implementation(project(":agents:agents-ext"))
                implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
                implementation(project(":prompt:prompt-executor:prompt-executor-llms-all"))

                implementation(libs.jetbrains.annotations)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.logback.classic)
                implementation(libs.oshai.kotlin.logging)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(project(":agents:agents-features:agents-features-event-handler"))

                implementation(libs.ktor.client.cio)
            }
        }
    }

    explicitApi()
}

// Configure the publication to use the Grazie Public Maven repository
publishToGraziePublicMaven()
