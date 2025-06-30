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
                api(project(":prompt:prompt-model"))
                api(project(":agents:agents-tools"))
                api(project(":prompt:prompt-executor:prompt-executor-model"))
                api(libs.kotlinx.coroutines.core)
            }
        }
        jvmMain {
            dependencies {
                api(kotlin("reflect"))
            }
        }
    }

    explicitApi()
}

publishToMaven()
