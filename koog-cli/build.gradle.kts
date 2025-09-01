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
                // This module provides reusable CLI/edit-tool components
                api(project(":agents:agents-core"))
                api(project(":agents:agents-tools"))
                api(project(":agents:agents-utils"))

                api(libs.kotlinx.serialization.json)
                // utilities from rag-base used by tests/util in edit tool
                api(project(":rag:rag-base"))
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
    }
    explicitApi()
}

publishToMaven()
