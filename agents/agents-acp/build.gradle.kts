import ai.koog.gradle.publish.maven.Publishing.publishToMaven

group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

// FIXME Kotlin ACP SDK only supports JVM target for now, so we only provide JVM target for this module too. Fix later
kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(project(":agents:agents-core"))
                api(project(":agents:agents-features:agents-features-acp"))
                api(project(":agents:agents-features:agents-features-event-handler"))
                implementation(libs.acp)
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.io.core)
                api(libs.kotlinx.coroutines.core)
                implementation(libs.oshai.kotlin.logging)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }

    explicitApi()
}

publishToMaven()
