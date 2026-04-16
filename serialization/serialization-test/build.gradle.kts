plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

group = rootProject.group
version = rootProject.version

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":serialization:serialization-core"))

                api(kotlin("test"))
                api(libs.kotest.assertions.core)
                api(libs.kotest.assertions.json)
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}
