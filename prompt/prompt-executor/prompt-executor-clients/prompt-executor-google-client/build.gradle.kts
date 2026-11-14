import ai.koog.gradle.publish.maven.Publishing.publishToMaven

val isBeta by extra(true)

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
                api(project(":utils"))
                api(project(":prompt:prompt-executor:prompt-executor-clients"))
                api(project(":prompt:prompt-llm"))
                api(project(":prompt:prompt-model"))
                api(project(":prompt:prompt-structure"))
                api(libs.kotlinx.coroutines.core)
                implementation(libs.oshai.kotlin.logging)
            }
        }

        commonTest {
            dependencies {
                implementation(project(":test-utils"))
                implementation(libs.ktor.client.engine.defaults)
            }
        }

        jvmTest {
            dependencies {
                implementation(project(":http-client:http-client-ktor"))
                implementation(libs.ktor.client.mock)
            }
        }
    }

    explicitApi()
}

publishToMaven()
