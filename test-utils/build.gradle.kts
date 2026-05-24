plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(kotlin("test"))
                api(libs.kotlinx.coroutines.test)
                api(libs.kotlinx.serialization.json)
                api(libs.kotest.assertions.json)
                api(libs.kotest.assertions.core)
                api(project(":http-client:http-client-core"))
            }
        }

        jvmMain {
            dependencies {
                api(kotlin("test-junit5"))
                api(libs.junit.jupiter.params)
                api(libs.testcontainers)
                api(libs.awaitility)
                implementation(project(":utils"))
                runtimeOnly(libs.slf4j.simple)
            }
        }
    }

    explicitApi()
}
