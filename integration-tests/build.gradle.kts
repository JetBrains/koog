group = "${rootProject.group}.integration-tests"
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform.server")
    alias(libs.plugins.kotlin.serialization)
    id("ai.koog.gradle.plugins.credentialsresolver")
    id("netty-convention")
}

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                implementation(project(":prompt:prompt-executor:prompt-executor-llms-all"))

                implementation(libs.testcontainers)
                implementation(libs.ktor.server.netty)
                implementation(kotlin("test-junit5"))
                runtimeOnly(libs.ktor.client.apache5)
                runtimeOnly(libs.slf4j.simple)
            }
        }

        jvmTest {
            dependencies {
                implementation(project(":agents:agents-ext"))
                implementation(project(":agents:agents-features:agents-features-event-handler"))
                implementation(project(":agents:agents-features:agents-features-trace"))
                implementation(project(":agents:agents-features:agents-features-snapshot"))
                implementation(project(":agents:agents-mcp"))
                implementation(project(":agents:agents-mcp-server"))
                implementation(project(":agents:agents-test"))
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
            }
        }
    }
}

configurations.all {
    // make sure we have Netty as a server, not CIO
    exclude(group = "io.ktor", module = "ktor-server-cio")
}

val envs = credentialsResolver.resolve(
    layout.projectDirectory.file(provider { "env.properties" })
)

tasks.withType<Test> {
    // Forward system properties to the test JVM
    System.getProperties().forEach { key, value ->
        systemProperty(key.toString(), value)
    }
}

// Try loading envs from file for integration tests only.
tasks.withType<Test>()
    .matching { it.name in listOf("jvmIntegrationTest", "jvmOllamaTest") }
    .configureEach {
        doFirst {
            logger.info("Loading envs from local file")
            environment(envs.get())
        }
    }

dokka {
    dokkaSourceSets.configureEach {
        suppress.set(true)
    }
}
