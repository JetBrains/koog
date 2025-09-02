import ai.koog.gradle.publish.maven.Publishing.publishToMaven

group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":agents:agents-tools"))
                api(project(":prompt:prompt-model"))
                api(libs.kotlinx.coroutines.core)
                api(libs.oshai.kotlin.logging)
            }
        }

        jvmMain {
            dependencies {
                api(libs.kotlinx.coroutines.jdk8)
            }
        }
    }

    explicitApi()
}

publishToMaven()
