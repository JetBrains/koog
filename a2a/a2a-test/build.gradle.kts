plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":a2a:a2a-core"))
                api(project(":a2a:a2a-client"))
                api(kotlin("test"))
                api(kotlin("test-annotations-common"))
                api(libs.kotest.assertions)
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.coroutines.test)
                api(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmMain {
            dependencies {
                api(kotlin("test-junit5"))
            }
        }

        jsMain {
            dependencies {
                api(kotlin("test-js"))
            }
        }
    }
}
