group = "${rootProject.group}.integration-tests"
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                implementation(project(":prompt:prompt-executor:prompt-executor-llms-all"))
                implementation(libs.testcontainers)
                implementation(kotlin("test"))
                implementation(kotlin("test-junit5"))
            }
        }

        jvmTest {
            dependencies {
                implementation(project(":agents:agents-ext"))
                implementation(project(":agents:agents-features:agents-features-event-handler"))
                implementation(project(":agents:agents-features:agents-features-trace"))
                implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-anthropic-client"))
                implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
                implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openrouter-client"))
                implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-google-client"))
                implementation("org.junit.jupiter:junit-jupiter-params:5.9.2")
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.content.negotiation)
            }
        }
    }
}

dokka {
    dokkaSourceSets.configureEach {
        suppress.set(true)
    }
}
