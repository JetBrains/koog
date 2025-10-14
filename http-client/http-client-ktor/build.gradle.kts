import ai.koog.gradle.publish.maven.Publishing.publishToMaven

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
                api(project(":http-client:http-client-core"))
                implementation(project(":utils"))
                implementation(libs.ktor.client.core)
                implementation(libs.oshai.kotlin.logging)
            }
        }

        jvmTest {
            dependencies {
                implementation(project(":http-client:http-client-test"))
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.content.negotiation)
                implementation(kotlin("test-junit5"))
            }
        }
    }

    explicitApi()
}

publishToMaven()
