import ai.koog.gradle.publish.maven.Publishing.publishToMaven

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

group = rootProject.group
version = rootProject.version

kotlin {
    // LiteRT-LM has full implementation on JVM/Android, stubs on other platforms
    // The convention plugin handles all target declarations

    sourceSets {
        commonMain {
            dependencies {
                api(project(":agents:agents-tools"))
                api(project(":prompt:prompt-llm"))
                api(project(":prompt:prompt-model"))
                api(project(":prompt:prompt-executor:prompt-executor-model"))
                api(project(":prompt:prompt-executor:prompt-executor-clients"))

                api(libs.kotlinx.datetime)
                api(libs.kotlinx.coroutines.core)
                implementation(libs.oshai.kotlin.logging)
            }
        }

        jvmMain {
            dependencies {
                // LiteRT-LM JVM dependency
                // This is marked as compileOnly - users must add this dependency to their project
                // at runtime when they want to use the LiteRT-LM provider.
                // See: https://github.com/google-ai-edge/LiteRT-LM
                compileOnly(libs.litertlm.jvm)
            }
        }

        commonTest {
            dependencies {
                implementation(project(":test-utils"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmTest {
            dependencies {
                // Add LiteRT-LM for testing (when available)
                implementation(libs.litertlm.jvm)
                implementation(libs.mockk)
                implementation(libs.kotest.assertions.core)
            }
        }
    }

    explicitApi()
}

publishToMaven()
