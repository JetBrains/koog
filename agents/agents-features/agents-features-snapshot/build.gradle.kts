import ai.koog.gradle.publish.maven.Publishing.publishToMaven

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
                api(project(":agents:agents-core"))
                api(project(":rag:rag-base"))

                api(libs.kotlinx.serialization.json)
                api(libs.ktor.serialization.kotlinx.json)
            }
        }

        commonTest {
            dependencies {
                implementation(project(":test-utils"))
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
