import ai.koog.gradle.publish.maven.Publishing.publishToMaven
import org.gradle.api.tasks.testing.Test

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
                api(project(":agents:agents-features:agents-features-memory"))

                api(libs.kotlinx.serialization.json)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmMain {
            dependencies {
                api(libs.aws.sdk.kotlin.bedrockagentcore)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(project(":test-utils"))
                implementation(libs.mockk)
            }
        }
    }

    explicitApi()
}

// Disable JUnit5 parallel execution for this module.
// The AWS SDK BedrockAgentCoreClient relaxed mock is expensive to initialize via reflection,
// and parallel execution causes thread contention that makes the test suite hang.
tasks.withType<Test>().configureEach {
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}

publishToMaven()
