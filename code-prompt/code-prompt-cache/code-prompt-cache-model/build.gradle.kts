import ai.grazie.gradle.publish.maven.publishToGraziePublicMaven

group = "${rootProject.group}.prompt.cache"
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":code-agents:code-agents-core-tools"))
                api(project(":code-prompt:code-prompt-model"))
                implementation(libs.kotlinx.datetime)
            }
        }
    }
}

publishToGraziePublicMaven()