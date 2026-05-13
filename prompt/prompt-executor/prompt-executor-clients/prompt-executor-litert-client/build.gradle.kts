import ai.koog.gradle.publish.maven.Publishing.publishToMaven

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

group = rootProject.group
version = rootProject.version

kotlin {
    sourceSets {
        androidMain {
            dependencies {
                api(project(":prompt:prompt-executor:prompt-executor-clients"))
                api(project(":prompt:prompt-llm"))
                implementation(libs.android.litertlm)
                implementation(libs.mcp.client)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        androidUnitTest {
            dependencies {
                implementation(project(":test-utils"))
            }
        }
    }

    explicitApi()
}

publishToMaven()
