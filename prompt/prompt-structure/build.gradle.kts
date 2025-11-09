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
                api(project(":prompt:prompt-executor:prompt-executor-model"))

                api(project(":prompt:prompt-markdown"))
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.json)
                implementation(libs.oshai.kotlin.logging)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(project(":agents:agents-test"))
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
            }
        }

        jsTest {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }

    explicitApi()
}

publishToMaven()
