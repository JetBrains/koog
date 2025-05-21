group = "${rootProject.group}.integration-tests"
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        jvmMain {
            kotlin.srcDir("src/jvmTest/kotlin")
            dependencies {
                api(project(":agents:agents-ext"))
                api(project(":agents:agents-features:agents-features-event-handler"))
                api(project(":agents:agents-features:agents-features-trace"))
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-anthropic-client"))
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openrouter-client"))
                api(project(":prompt:prompt-executor:prompt-executor-llms-all"))
                api(kotlin("test"))
                api(kotlin("test-junit5"))
                api("org.junit.jupiter:junit-jupiter-params:5.9.2")
                api(libs.kotlinx.coroutines.test)
                api(libs.ktor.client.content.negotiation)
                api(libs.testcontainers)
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
                implementation(project(":prompt:prompt-executor:prompt-executor-llms-all"))
                implementation(kotlin("test"))
                implementation(kotlin("test-junit5"))
                implementation("org.junit.jupiter:junit-jupiter-params:5.9.2")
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.testcontainers)
            }
        }
    }
}
