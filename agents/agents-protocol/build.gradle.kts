import ai.koog.gradle.publish.maven.Publishing.publishToMaven
import org.gradle.kotlin.dsl.project

group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":agents:agents-core"))
                api(project(":agents:agents-mcp"))
                implementation(project(":prompt:prompt-executor:prompt-executor-llms-all"))

                api(libs.kotlinx.serialization.json)
                api(libs.ktor.client.content.negotiation)
                api(libs.ktor.serialization.kotlinx.json)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
                implementation(kotlin("test-junit5"))
                implementation(project(":agents:agents-test"))
                implementation(project(":utils"))
                implementation(libs.mcp.server)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.server.cio)
            }
        }
    }

    explicitApi()
}

publishToMaven()
