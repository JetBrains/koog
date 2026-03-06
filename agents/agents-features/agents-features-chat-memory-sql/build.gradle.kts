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
                api(project(":agents:agents-features:agents-features-memory"))

                api(libs.kotlinx.serialization.json)
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
                api(libs.exposed.core)
                api(libs.exposed.dao)
                api(libs.exposed.jdbc)
                api(libs.exposed.json)
                api(libs.exposed.kotlin.datetime)
                compileOnly(libs.h2)
                compileOnly(libs.mysql)
                compileOnly(libs.postgresql)
                compileOnly(libs.sqlite)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(project(":test-utils"))
                implementation(libs.testcontainers)
                implementation(libs.testcontainers.postgresql)
                implementation(libs.testcontainers.mysql)

                runtimeOnly(libs.postgresql)
                runtimeOnly(libs.mysql)
                runtimeOnly(libs.h2)
                runtimeOnly(libs.sqlite)
            }
        }
    }

    explicitApi()
}

publishToMaven()
