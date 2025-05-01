import ai.grazie.gradle.publish.maven.publishToGraziePublicMaven

group = "${rootProject.group}.agents"
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":agents:agents-core-tools"))
                api("ai.jetbrains.code.files:code-files-model:1.0.0-beta.55+0.4.45")
                implementation("ai.jetbrains.code.files:code-files-tools:1.0.0-beta.55+0.4.45")
                implementation(project(":prompt:prompt-markdown"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
            }
        }
    }
}

publishToGraziePublicMaven()

