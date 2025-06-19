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
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.io.core)
            }
        }
        
        jvmTest {
            dependencies {
                implementation(libs.junit.jupiter.params)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }

    explicitApi()
}

publishToGraziePublicMaven()
