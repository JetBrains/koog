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
                api(project(":utils"))
                api(project(":prompt:prompt-executor:prompt-executor-clients"))
                api(project(":prompt:prompt-llm"))
                api(project(":prompt:prompt-model"))
                api(libs.kotlinx.coroutines.core)
                implementation(libs.oshai.kotlin.logging)
            }
        }

        commonTest {
            dependencies {
                implementation(project(":test-utils"))
                implementation(libs.kotest.assertions.json)
            }
        }

        jvmTest {
            dependencies {
                implementation(project(":http-client:http-client-ktor"))
                implementation(libs.ktor.client.cio)
            }
        }
    }

    explicitApi()
    compilerOptions {
        freeCompilerArgs.add("-opt-in=ai.koog.prompt.executor.clients.InternalLLMClientApi")
    }
}

publishToMaven()
