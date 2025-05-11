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
                implementation(kotlin("test"))

                implementation(project(":agents:agents-core"))
                implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
                implementation(project(":prompt:prompt-executor:prompt-executor-llms-all"))

                implementation(libs.ai.grazie.utils.common)
                implementation(libs.jetbrains.annotations)
                implementation(libs.logback.classic)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.ai.grazie.model.auth)
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
}

// Configure the publication to use the Grazie Public Maven repository
publishToGraziePublicMaven()
