
group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}
kotlin {
    jvm()
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":a2a:a2a-client"))
                implementation(project(":a2a:a2a-server"))
                implementation(project(":a2a:a2a-transport:a2a-transport-client-jsonrpc-http"))
                implementation(project(":a2a:a2a-transport:a2a-transport-server-jsonrpc-http"))
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.oshai.kotlin.logging)
                runtimeOnly(libs.slf4j.simple)
                implementation(libs.ktor.server.cio)
                runtimeOnly(libs.ktor.client.cio)
            }
        }
    }

    explicitApi()
}
