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
                api(kotlin("test"))
                api(libs.kotlinx.coroutines.test)
                api(libs.kotlinx.serialization.json)
                api(libs.kotest.assertions.json)
                api(libs.kotest.assertions.core)
            }
        }

        jvmMain {
            dependencies {
                api(kotlin("test-junit5"))
                api(libs.junit.jupiter.params)
                api(libs.testcontainers)
                runtimeOnly(libs.slf4j.simple)
            }
        }
    }

    explicitApi()
}
