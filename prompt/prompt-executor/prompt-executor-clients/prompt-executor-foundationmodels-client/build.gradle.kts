import ai.koog.gradle.publish.maven.Publishing.publishToMaven

val isBeta by extra(true)

plugins {
    id("ai.kotlin.multiplatform")
}

group = rootProject.group
version = rootProject.version

kotlin {
    sourceSets {
        appleMain {
            dependencies {
                api(project(":prompt:prompt-executor:prompt-executor-clients"))
                api(project(":prompt:prompt-llm"))
            }
        }
    }

    explicitApi()
}

publishToMaven()
