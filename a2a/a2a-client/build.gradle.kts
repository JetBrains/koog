import ai.koog.gradle.publish.maven.Publishing.publishToMaven

group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()

    js(IR) {
        browser()
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":a2a:a2a-core"))
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.coroutines.core)
                api(libs.ktor.client.core)
                api(libs.ktor.client.content.negotiation)
                api(libs.ktor.serialization.kotlinx.json)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotest.assertions)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(project(":a2a:a2a-transport:a2a-transport-client-jsonrpc-http"))

                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.logging)
                implementation(libs.testcontainers.junit)
                runtimeOnly(libs.slf4j.simple)
            }
        }

        jsTest {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }

    explicitApi()
}

publishToMaven()

tasks.register<Exec>("dockerBuildTestPythonA2AServer") {
    group = "docker"
    description = "Build Python A2A test server image"
    workingDir = file("../test-python-a2a-server")
    commandLine = listOf("docker", "build", "-t", "test-python-a2a-server", ".")
}

tasks.named("jvmTest") {
    dependsOn("dockerBuildTestPythonA2AServer")
}
