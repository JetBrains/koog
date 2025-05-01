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
                api(project(":code-agents:code-agents-local-features:code-agents-local-features-memory-old"))
                api(project(":code-prompt:code-prompt-executor:code-prompt-executor-model"))
                implementation(project(":code-prompt:code-prompt-markdown"))
                implementation(project(":code-prompt:code-prompt-structure"))
                implementation(libs.ai.grazie.utils.common)
                implementation(libs.kotlinx.datetime)
            }
        }
    }
}

publishToGraziePublicMaven()
