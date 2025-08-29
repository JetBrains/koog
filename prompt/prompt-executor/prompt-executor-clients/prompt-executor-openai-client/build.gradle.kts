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
                api(libs.jetbrains.annotations)
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client-base"))
                api(project(":prompt:prompt-structure"))
                implementation(libs.oshai.kotlin.logging)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.junit.jupiter.params)
            }
        }
    }

    explicitApi()
}

publishToMaven()
