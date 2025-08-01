group = "${rootProject.group}.integration-tests"
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
    id("ai.koog.gradle.plugins.credentialsresolver")
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
                implementation(project(":agents:agents-features:agents-features-snapshot"))
                implementation(
                    project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-anthropic-client")
                )
                implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
                implementation(
                    project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openrouter-client")
                )
                implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-google-client"))
                implementation(libs.junit.jupiter.params)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.aws.sdk.kotlin.sts)
                implementation(libs.aws.sdk.kotlin.bedrock)
                implementation(libs.aws.sdk.kotlin.bedrockruntime)
                implementation(libs.ktor.client.content.negotiation)
                runtimeOnly(libs.slf4j.simple)
            }
        }
    }
}

val envs = credentialsResolver.resolve(
    layout.projectDirectory.file(provider { "env.properties" })
)

tasks.withType<Test> {
    doFirst {
        environment(envs.get())
    }

    // Forward system properties to the test JVM
    System.getProperties().forEach { key, value ->
        systemProperty(key.toString(), value)
    }
}

dokka {
    dokkaSourceSets.configureEach {
        suppress.set(true)
    }
}
