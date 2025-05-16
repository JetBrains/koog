import ai.grazie.gradle.publish.maven.publishToGraziePublicMaven

group = "${rootProject.group}.prompt"
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.ktor.client.content.negotiation)
                implementation(kotlin("test"))
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-anthropic-client"))
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openrouter-client"))
                implementation(project(":agents:agents-features:agents-features-event-handler"))
                implementation(project(":agents:agents-features:agents-features-trace"))
                implementation(project(":agents:agents-tools"))
            }
        }
        commonTest {
            dependencies {
                implementation(libs.ai.grazie.utils.common)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation("org.junit.jupiter:junit-jupiter-params:5.9.2")
                implementation(project(":agents:agents-features:agents-features-trace"))
                implementation(project(":prompt:prompt-executor:prompt-executor-llms-all"))
            }
        }
    }
}

publishToGraziePublicMaven()
