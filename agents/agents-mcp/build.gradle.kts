import ai.grazie.gradle.publish.maven.publishToGraziePublicMaven

group = "${rootProject.group}.agents"
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}


kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                implementation(libs.mcp)
                implementation(libs.kotlinx.io.core)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(project(":agents:agents-core-tools"))
                implementation(project(":agents:agents-core"))
                implementation(project(":agents:agents-local"))
                implementation(project(":prompt:prompt-model"))
                implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
                implementation(project(":prompt:prompt-executor:prompt-executor-llms"))
                implementation(project(":prompt:prompt-executor:prompt-executor-llms-all"))
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(project(":agents:agents-test"))
                implementation(libs.mockk)
            }
        }
    }
}

publishToGraziePublicMaven()
