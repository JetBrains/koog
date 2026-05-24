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
                runtimeOnly(libs.ktor.client.cio)
                runtimeOnly(libs.slf4j.simple)
            }
        }

        jvmTest {
            dependencies {
                implementation(project(":agents:agents-ext"))
                implementation(project(":agents:agents-planner"))
                implementation(project(":agents:agents-features:agents-features-event-handler"))
                implementation(project(":agents:agents-features:agents-features-trace"))
                implementation(project(":agents:agents-features:agents-features-snapshot"))
                implementation(project(":agents:agents-features:agents-features-acp"))
                implementation(project(":agents:agents-mcp"))
                implementation(project(":agents:agents-features:agents-features-opentelemetry"))
                implementation(project(":agents:agents-mcp-server"))
                implementation(project(":agents:agents-test"))
                implementation(project(":test-utils"))
                implementation(project(":agents:agents-cli"))
                implementation(
                    project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-anthropic-client")
                )
                implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
                implementation(
                    project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openrouter-client")
                )
                implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-google-client"))
                implementation(
                    project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-mistralai-client")
                )
                implementation(project(":agents:agents-features:agents-features-chat-history-aws"))
                implementation(project(":agents:agents-features:agents-features-longterm-memory-aws"))
                implementation(project(":serialization:serialization-jackson"))

                // External libraries
                implementation(libs.junit.jupiter.params)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotest.assertions.core)
                implementation(libs.assertj.core)
                implementation(libs.aws.sdk.kotlin.sts)
                implementation(libs.aws.sdk.kotlin.bedrock)
                implementation(libs.aws.sdk.kotlin.bedrockruntime)
                implementation(libs.aws.sdk.kotlin.bedrockagentcore)
                implementation(libs.aws.sdk.kotlin.bedrockagentcorecontrol)
                implementation(libs.awaitility)
                implementation(libs.ktor.client.content.negotiation)
                implementation(project.dependencies.platform(libs.opentelemetry.bom))
                implementation(libs.opentelemetry.sdk.testing)
                implementation(libs.opentelemetry.kotlin.exporters.inMemory)
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
    // Forward test-relevant system properties to the test JVM.
    // Exclude JVM-internal properties (java.*, sun.*, jdk.*, etc.) to avoid conflicts
    // when the Gradle daemon runs on a different JDK version than the test toolchain.
    val jvmInternalPrefixes = setOf("java.", "sun.", "jdk.", "os.", "user.", "file.", "line.", "path.", "native.", "stderr.", "stdout.")
    System.getProperties().forEach { key, value ->
        val k = key.toString()
        if (jvmInternalPrefixes.none { k.startsWith(it) }) {
            systemProperty(k, value)
        }
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
