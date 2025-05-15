import ai.grazie.gradle.publish.maven.Publishing.publishToGraziePublicMaven

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
                api("ai.jetbrains.code.files:code-files-model:1.0.0-beta.55+0.4.45")
                api(project(":agents:agents-core"))
                api(project(":agents:agents-features:agents-features-common"))
                implementation(project(":prompt:prompt-markdown"))

                implementation(libs.ai.grazie.model.auth)
                implementation(libs.ai.grazie.utils.common)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }

        jvmTest {
            dependencies {
                implementation("ai.jetbrains.code.files:code-files-jvm:1.0.0-beta.55+0.4.45") {
                    exclude("org.jetbrains", "ij-parsing-core")
                }
                implementation(kotlin("test-junit5"))
                implementation(project(":agents:agents-test"))
                implementation(libs.mockk)
            }
        }
    }

    explicitApi()
}

publishToGraziePublicMaven()
