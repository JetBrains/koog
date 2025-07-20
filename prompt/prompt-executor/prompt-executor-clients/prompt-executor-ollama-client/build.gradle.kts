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

                api(ktorLibs.client.logging)
                api(libs.kotlinx.datetime)
                api(libs.kotlinx.coroutines.core)
                api(ktorLibs.client.contentNegotiation)
                api(ktorLibs.serialization.kotlinx.json)
                api(ktorLibs.client.cio)
                implementation(libs.oshai.kotlin.logging)
            }
        }

        jsMain {
            dependencies {
                api(ktorLibs.client.js)
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
                implementation(project(":integration-tests"))
            }
        }
    }

    explicitApi()
}

publishToMaven()