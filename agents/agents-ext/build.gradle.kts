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
                api(project(":agents:agents-core"))
                api(project(":agents:agents-tools"))
                api(project(":agents:agents-utils"))
                api(project(":prompt:prompt-processor"))
                api(project(":rag:rag-base"))

                api(libs.kotlinx.serialization.json)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(project(":agents:agents-test"))
                implementation(project(":prompt:prompt-executor:prompt-executor-llms-all"))
                implementation(project(":test-utils"))
                implementation(project(":agents:agents-features:agents-features-event-handler"))
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.mockk)
            }
        }
    }

    explicitApi()
}

publishToMaven()
