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
                api(project(":agents:agents-local-features:agents-local-features-memory-old"))
                api(project(":prompt:prompt-executor:prompt-executor-model"))
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
                implementation(project(":prompt:prompt-markdown"))
                implementation(project(":prompt:prompt-structure"))
                implementation(libs.ai.grazie.utils.common)
                implementation(libs.kotlinx.datetime)
            }
        }
    }
}

publishToGraziePublicMaven()
