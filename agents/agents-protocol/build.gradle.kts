import ai.koog.gradle.publish.maven.Publishing.publishToMaven

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
                implementation(project(":prompt:prompt-executor:prompt-executor-llms-all"))

                api(libs.kotlinx.serialization.json)
                api(libs.ktor.client.content.negotiation)
                api(libs.ktor.serialization.kotlinx.json)
            }
        }

        // TODO wait until ACP SDK supports KMP
        jvmMain {
            dependencies {
                api(libs.acp)
                implementation(libs.ktor.client.cio)

            }
        }
    }

    explicitApi()
}

publishToMaven()
