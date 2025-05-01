import ai.grazie.gradle.publish.maven.publishToGraziePublicMaven

group = "${rootProject.group}.agents"
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":agents:agents-local-features:agents-local-features-common"))

                implementation(libs.ai.grazie.model.auth)
                implementation(libs.ai.grazie.utils.common)
                implementation(libs.kotlinx.serialization.json)

                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.server.sse)
            }
        }

        jvmMain {
            dependencies {
                implementation("ai.jetbrains.code.files:code-files-jvm:1.0.0-beta.55+0.4.45")
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.server.cio)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
            }
        }
    }
}

publishToGraziePublicMaven()
