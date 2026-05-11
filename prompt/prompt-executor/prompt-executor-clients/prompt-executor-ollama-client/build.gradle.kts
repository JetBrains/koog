import ai.koog.gradle.publish.maven.Publishing.publishToMaven

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":agents:agents-tools"))
                api(project(":http-client:http-client-core"))
                api(project(":http-client:http-client-ktor"))
                api(project(":prompt:prompt-llm"))
                api(project(":prompt:prompt-model"))
                api(project(":prompt:prompt-tokenizer"))
                api(project(":agents:agents-tools"))
                api(project(":prompt:prompt-executor:prompt-executor-clients"))
                api(project(":embeddings:embeddings-base"))

                api(libs.kotlinx.coroutines.core)
                api(libs.ktor.client.core)
                implementation(libs.oshai.kotlin.logging)
            }
        }

        androidUnitTest {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }

        appleTest {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }

        jsTest {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }

        wasmJsTest {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }

        commonTest {
            dependencies {
                implementation(project(":test-utils"))
                implementation(project(":agents:agents-features:agents-features-event-handler"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
            }
        }

        jvmTest {
            dependencies {
                implementation(project(":agents:agents-core"))
                implementation(project(":agents:agents-features:agents-features-event-handler"))
                implementation(project(":agents:agents-features:agents-features-trace"))
                implementation(libs.ktor.client.cio)
            }
        }
    }

    explicitApi()
}

publishToMaven()
