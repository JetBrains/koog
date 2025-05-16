plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

group = "${rootProject.group}.prompt"
version = rootProject.version

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":agents:agents-tools"))
                implementation(project(":prompt:prompt-llm"))
                implementation(project(":prompt:prompt-model"))
                implementation(project(":agents:agents-tools"))
                implementation(project(":prompt:prompt-executor:prompt-executor-model"))
                implementation(project(":prompt:prompt-executor:prompt-executor-clients"))
                implementation(project(":prompt:prompt-executor:prompt-executor-llms"))
                implementation(project(":embeddings:embeddings-base"))

                implementation(libs.ktor.client.logging)
                implementation(libs.ai.grazie.utils.common)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }

        jsMain {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }


        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":agents:agents-features:agents-features-event-handler"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(kotlin("test-junit5"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(project(":agents:agents-core"))
                implementation(project(":agents:agents-features:agents-features-event-handler"))
                implementation(project(":agents:agents-features:agents-features-trace"))
            }
        }
    }

    explicitApi()
}
