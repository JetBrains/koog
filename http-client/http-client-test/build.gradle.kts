plugins {
    id("ai.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
}

group = rootProject.group
version = rootProject.version

dependencies {
    api(project(":http-client:http-client-core"))
    api(libs.ktor.server.cio)
    api(libs.ktor.server.sse)
    api(libs.kotlinx.coroutines.test)
    api(libs.kotlinx.serialization.json)
    api(kotlin("test"))
    compileOnly(kotlin("test-junit5")) // must be provided by the dependent modules
    implementation(libs.kotlinx.coroutines.core)
}
