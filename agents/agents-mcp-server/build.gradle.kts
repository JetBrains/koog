import ai.koog.gradle.publish.maven.Publishing.publishToMaven

group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform.server")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(libs.mcp.server)
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.sse)

                api(project(":agents:agents-tools"))
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(project(":agents:agents-mcp"))
                implementation(project(":agents:agents-test"))
                runtimeOnly(libs.ktor.client.cio)
                runtimeOnly(libs.slf4j.simple)
            }
        }
    }

    explicitApi()
}

publishToMaven()
