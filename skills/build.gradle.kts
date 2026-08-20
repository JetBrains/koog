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
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.core)
                api(project(":rag:rag-base"))
                implementation(libs.oshai.kotlin.logging)
            }
        }

        commonTest {
            dependencies {
                implementation(project(":test-utils"))
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.junit.jupiter.params)
                implementation(libs.kotlinx.coroutines.test)
                runtimeOnly(libs.logback.classic)
            }
        }
    }

    explicitApi()
}

publishToMaven()
