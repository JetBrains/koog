import ai.grazie.gradle.publish.maven.Publishing.publishToGraziePublicMaven

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
                api(project(":prompt:prompt-llm"))
                api(libs.kotlinx.serialization.json)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit5"))
            }
        }

        jsTest {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation("org.junit.jupiter:junit-jupiter-params:5.9.2")
            }
        }
    }

    explicitApi()
}

publishToGraziePublicMaven()
