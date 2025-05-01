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
                implementation(project(":prompt:prompt-markdown"))
            }
        }
    }
}

publishToGraziePublicMaven()
