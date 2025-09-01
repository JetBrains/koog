plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

group = rootProject.group
version = rootProject.version

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(kotlin("test"))
                api(libs.kotlinx.coroutines.test)
                api(libs.kotlinx.serialization.json)
            }
        }

        jvmMain {
            dependencies {
                api(kotlin("test-junit5"))
                implementation(libs.junit.jupiter.params)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }

    explicitApi()
}
