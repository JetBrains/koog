import ai.koog.gradle.publish.maven.Publishing.publishToMaven


plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":agents:agents-core"))

                api(libs.kotlinx.serialization.json)
                api(libs.ktor.client.content.negotiation)
                api(libs.ktor.serialization.kotlinx.json)
            }
        }

        // TODO wait until ACP SDK supports KMP
        jvmMain {
            dependencies {
                api(libs.acp)
            }
        }
    }

    explicitApi()
}

publishToMaven()
