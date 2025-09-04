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
                api(project(":agents:agents-tools"))
                api(project(":prompt:prompt-llm"))
                api(project(":prompt:prompt-model"))
                api(project(":agents:agents-tools"))
                api(project(":prompt:prompt-executor:prompt-executor-model"))
                api(project(":prompt:prompt-executor:prompt-executor-clients"))
                api(project(":prompt:prompt-executor:prompt-executor-llms"))
                api(project(":embeddings:embeddings-base"))

                api(libs.ktor.client.logging)
                api(libs.kotlinx.datetime)
                api(libs.kotlinx.coroutines.core)
                api(libs.ktor.client.content.negotiation)
                api(libs.ktor.serialization.kotlinx.json)
                implementation(libs.oshai.kotlin.logging)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }

        appleMain {
            dependencies {
                api(libs.ktor.client.darwin)
            }
        }

        jsMain {
            dependencies {
                api(libs.ktor.client.js)
            }
        }

        wasmJsMain {
            dependencies {
                api(libs.ktor.client.cio)
            }
        }

        jvmMain {
            dependencies {
                api(libs.ktor.client.cio)
            }
        }

        commonTest {
            dependencies {
                implementation(project(":test-utils"))
                implementation(project(":agents:agents-features:agents-features-event-handler"))
            }
        }

        jvmTest {
            dependencies {
                implementation(project(":agents:agents-core"))
                implementation(project(":agents:agents-features:agents-features-event-handler"))
                implementation(project(":agents:agents-features:agents-features-trace"))
            }
        }
    }

    explicitApi()
}

publishToMaven()
