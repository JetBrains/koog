import ai.koog.gradle.publish.maven.Publishing.publishToMaven

group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":agents:agents-core"))
                api(project(":agents:agents-features:agents-features-common"))
                api(project(":rag:rag-base"))

                api(libs.kotlinx.serialization.json)
                api(libs.ktor.serialization.kotlinx.json)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmMain {
            dependencies {
                api(libs.ktor.client.cio)
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

    explicitApi()
}

publishToMaven()
