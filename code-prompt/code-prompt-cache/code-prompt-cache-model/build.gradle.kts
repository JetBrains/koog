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
                api(project(":prompt:prompt-model"))
                implementation(libs.kotlinx.datetime)
            }
        }
    }
}

publishToGraziePublicMaven()