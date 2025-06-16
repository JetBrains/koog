import ai.grazie.gradle.publish.maven.Publishing.publishToMaven

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
