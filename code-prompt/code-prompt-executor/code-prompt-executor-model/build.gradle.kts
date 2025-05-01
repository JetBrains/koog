import ai.grazie.gradle.publish.maven.publishToGraziePublicMaven

group = "${rootProject.group}.prompt"
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":code-agents:code-agents-core-tools"))
                api(project(":code-prompt:code-prompt-model"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

publishToGraziePublicMaven()
