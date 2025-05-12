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
                api(project(":prompt:prompt-model"))
                implementation(project(":agents:agents-tools"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

publishToGraziePublicMaven()