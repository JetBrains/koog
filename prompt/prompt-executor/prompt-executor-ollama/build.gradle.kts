import ai.grazie.gradle.publish.maven.Publishing.publishToGraziePublicMaven

group = "${rootProject.group}.prompt"
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
    id("org.gradle.test-retry") version "1.5.3"
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":prompt:prompt-llm"))
                implementation(project(":prompt:prompt-model"))
                implementation(project(":agents:agents-tools"))
                implementation(project(":prompt:prompt-executor:prompt-executor-model"))

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.logging)
                implementation(libs.ai.grazie.utils.common)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":agents:agents-features:agents-features-event-handler"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jsTest {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(project(":agents:agents-core"))
                implementation(project(":agents:agents-features:agents-features-event-handler"))
                implementation(project(":agents:agents-features:agents-features-trace"))
                implementation("org.testcontainers:testcontainers:1.19.3")
                implementation("org.testcontainers:junit-jupiter:1.19.3")
            }
        }
    }

    explicitApi()
}

tasks.withType<Test> {
    retry {
        maxRetries.set(3)
        failOnPassedAfterRetry.set(true)
    }
}

publishToGraziePublicMaven()
