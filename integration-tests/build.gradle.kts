group = "${rootProject.group}.integration-tests"
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform.server")
    alias(libs.plugins.kotlin.serialization)
    id("ai.koog.gradle.plugins.credentialsresolver")
    id("netty-convention")
}

kotlin {
    jvmToolchain(21)

    sourceSets {
        jvmMain {
            dependencies {
                implementation(project(":prompt:prompt-executor:prompt-executor-llms-all"))

                implementation(libs.testcontainers)
                implementation(libs.ktor.server.netty)
                implementation("org.junit.jupiter:junit-jupiter-api:6.0.3")
                implementation("org.junit.jupiter:junit-jupiter-engine:6.0.3")
                implementation(kotlin("test-junit5"))
                runtimeOnly(libs.ktor.client.cio)
                runtimeOnly(libs.slf4j.simple)
            }
        }

        jvmTest {
            dependencies {
                implementation(project(":agents:agents-ext"))
                implementation(project(":agents:agents-features:agents-features-event-handler"))
                implementation(project(":agents:agents-features:agents-features-trace"))
                implementation(project(":agents:agents-features:agents-features-snapshot"))
                implementation(project(":agents:agents-features:agents-features-acp"))
                implementation(project(":agents:agents-mcp"))
                implementation(project(":agents:agents-features:agents-features-opentelemetry"))
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
                implementation(
                    project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-mistralai-client")
                )
                implementation("org.junit.jupiter:junit-jupiter-params:6.0.3")
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotest.assertions.core)
                implementation(libs.aws.sdk.kotlin.sts)
                implementation(libs.aws.sdk.kotlin.bedrock)
                implementation(libs.aws.sdk.kotlin.bedrockruntime)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.opentelemetry.sdk.testing)
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_21.toString()
    targetCompatibility = JavaVersion.VERSION_21.toString()
}

configurations.all {
    // make sure we have Netty as a server, not CIO
    exclude(group = "io.ktor", module = "ktor-server-cio")
    // Exclude JFR module that causes UTFDataFormatException during test discovery on Java 21
    exclude(group = "org.junit.platform", module = "junit-platform-jfr")
}

val envs = credentialsResolver.resolve(
    layout.projectDirectory.file(provider { "env.properties" })
)

tasks.withType<Test> {
    // Enable JUnit extension auto-detection for JdkWorkaroundsExtension
    systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")

    // Workaround for JDK 21 JFR MetadataLoader bug by pretending we're in native image context.
    // This makes JUnit skip JFR initialization without breaking ICU/Normalizer like --limit-modules does.
    // JUnit checks ImageInfo.inImageCode() which reads this property.
    systemProperty("org.graalvm.nativeimage.imagecode", "true")

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
