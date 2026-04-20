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
                api(project(":embeddings:embeddings-base"))
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.json)
            }
        }

        jvmMain {
            dependencies {
                implementation("com.pgvector:pgvector:0.1.6")
                implementation(libs.hikaricp)
                implementation(libs.oshai.kotlin.logging)
                runtimeOnly(libs.postgresql)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.io.core)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.junit.jupiter.params)
                implementation(libs.kotlinx.coroutines.test)

                implementation(project(":test-utils"))
                implementation(libs.testcontainers)
                implementation(libs.testcontainers.postgresql)
            }
        }
    }

    explicitApi()
}

publishToMaven()
