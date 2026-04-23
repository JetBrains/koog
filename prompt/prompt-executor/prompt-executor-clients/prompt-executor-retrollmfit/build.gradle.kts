group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

android {
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

kotlin {
    sourceSets {
        // jvmCommonMain is a source set defined by the ai.kotlin.multiplatform convention plugin.
        // Both jvmMain and androidMain depend on it, so code here compiles for both JVM and Android.
        val jvmCommonMain by getting {
            dependencies {
                api(project(":prompt:prompt-executor:prompt-executor-clients"))
                api(project(":prompt:prompt-llm"))
                api(project(":prompt:prompt-model"))
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(kotlin("reflect"))
                implementation(libs.oshai.kotlin.logging)
            }
        }

        jvmTest {
            dependencies {
                implementation(project(":test-utils"))
                implementation(libs.ktor.client.mock)
                implementation(libs.kotlinx.coroutines.test)
                implementation(kotlin("test"))
            }
        }

        androidInstrumentedTest {
            dependencies {
                implementation(libs.ktor.client.okhttp)
                //noinspection UseTomlInstead
                implementation("androidx.test.ext:junit:1.2.1")
                //noinspection UseTomlInstead
                implementation("androidx.test:runner:1.6.2")
                implementation(kotlin("test"))
            }
        }
    }

    explicitApi()
}
