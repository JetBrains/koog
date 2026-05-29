import ai.koog.gradle.publish.maven.Publishing.publishToMaven

val isBeta by extra(true)

plugins {
    id("ai.kotlin.multiplatform")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":agents:agents-core"))
                api(project(":prompt:prompt-executor:prompt-executor-model-selection"))
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }

    explicitApi()
}

publishToMaven()
