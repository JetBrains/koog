import ai.grazie.gradle.publish.maven.Publishing.publishToGraziePublicMaven

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
                implementation(project(":agents:agents-tools"))
                implementation(project(":agents:agents-core"))
                implementation(project(":prompt:prompt-model"))
                implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
                implementation(project(":prompt:prompt-executor:prompt-executor-llms"))
                implementation(project(":prompt:prompt-executor:prompt-executor-llms-all"))

                api(libs.mcp)
                implementation(libs.kotlinx.io.core)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.sse)
                implementation(libs.ai.grazie.utils.common)
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

publishToGraziePublicMaven()
