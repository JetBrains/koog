import ai.koog.gradle.publish.maven.Publishing.publishToMaven

group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

// FIXME Kotlin MCP SDK only supports JVM target for now, so we only provide JVM target for this module too. Fix later
kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(project(":agents:agents-tools"))
                api(project(":agents:agents-core"))
                api(project(":prompt:prompt-model"))
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
                api(project(":prompt:prompt-executor:prompt-executor-llms"))
                api(project(":prompt:prompt-executor:prompt-executor-llms-all"))

                api(libs.mcp)
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.io.core)
                api(libs.kotlinx.coroutines.core)
                api(libs.ktor.client.cio)
                api(libs.ktor.client.sse)
                implementation(libs.oshai.kotlin.logging)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(project(":agents:agents-test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }

    explicitApi()
}

publishToMaven()
