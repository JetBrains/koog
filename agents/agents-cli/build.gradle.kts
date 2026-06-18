import ai.koog.gradle.publish.maven.Publishing.publishToMaven
import org.gradle.kotlin.dsl.implementation
import org.gradle.kotlin.dsl.project

val isBeta by extra(true)

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)

    sourceSets {
        commonMain {
            dependencies {
                api(project(":agents:agents-core"))

                implementation(libs.oshai.kotlin.logging)
            }
        }

        commonTest {
            dependencies {
                implementation(project(":agents:agents-test"))
                implementation(project(":test-utils"))
                implementation(libs.kotest.assertions.json)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
            }
        }
    }

    explicitApi()
}

publishToMaven()
