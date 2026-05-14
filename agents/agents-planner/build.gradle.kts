import ai.koog.gradle.publish.maven.Publishing.publishToMaven

val isBeta by extra(true)

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":agents:agents-core"))
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.oshai.kotlin.logging)
            }
        }

        commonTest {
            dependencies {
                implementation(project(":agents:agents-test"))
                implementation(project(":test-utils"))
                implementation(libs.kotest.assertions.json)
                implementation(project(":prompt:prompt-executor:prompt-executor-llms-all"))
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-ollama-client"))
                implementation(project(":serialization:serialization-jackson"))
            }
        }
    }

    explicitApi()
}

publishToMaven()
