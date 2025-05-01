import ai.grazie.gradle.publish.maven.publishToGraziePublicMaven

group = "${rootProject.group}.prompt"
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":code-prompt:code-prompt-executor:code-prompt-executor-model"))

                implementation(project(":prompt:prompt-markdown"))

                implementation(libs.ai.grazie.utils.common)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
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
}

publishToGraziePublicMaven()
