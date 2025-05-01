import ai.grazie.gradle.publish.maven.publishToGraziePublicMaven

group = "${rootProject.group}.prompt"
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":code-prompt:code-prompt-cache:code-prompt-cache-model"))
                api(project(":code-prompt:code-prompt-executor:code-prompt-executor-model"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        
        jvmTest {
            dependencies {
                implementation(libs.junit.jupiter.params)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

publishToGraziePublicMaven()
