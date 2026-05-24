import ai.koog.gradle.publish.maven.Publishing.publishToMaven
import org.gradle.kotlin.dsl.implementation
import org.gradle.kotlin.dsl.project


plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":agents:agents-core"))
                api(project(":rag:rag-base"))

                api(libs.kotlinx.serialization.json)
                api(libs.ktor.serialization.kotlinx.json)
            }
        }

        commonTest {
            dependencies {
                implementation(project(":test-utils"))
                implementation(project(":agents:agents-ext"))
                implementation(project(":agents:agents-features:agents-features-event-handler"))
            }
        }

        jvmMain {
            dependencies {
                // SQL dependencies moved to agents-features-sql module
            }
        }

        jvmTest {
            dependencies {
                implementation(project(":agents:agents-test"))
                implementation(project(":agents:agents-planner"))
                implementation(project(":agents:agents-ext"))
                implementation(project(":agents:agents-features:agents-features-trace"))
                implementation(libs.mockk)
                implementation(libs.awaitility)
                implementation(libs.testcontainers)
                implementation(libs.testcontainers.postgresql)
            }
        }
    }

    explicitApi()
}

publishToMaven()
