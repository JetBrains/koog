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
                api(project(":rag:rag-base"))
                api(project(":serialization:serialization-core"))
                implementation(project(":prompt:prompt-markdown"))
                implementation(project(":prompt:prompt-xml"))

                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.schema.generator.json)
            }
        }

        jvmMain {
            dependencies {
                api(kotlin("reflect"))
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.junit.jupiter.params)
                implementation(libs.assertj.core)
            }
        }
    }

    explicitApi()
}

publishToMaven()
