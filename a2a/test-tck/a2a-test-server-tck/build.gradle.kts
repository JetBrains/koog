plugins {
    id("ai.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass = "ai.koog.a2a.test.tck.MainKt"
}

dependencies {
    implementation(project(":a2a:a2a-server"))
    implementation(project(":a2a:a2a-client"))
    implementation(project(":a2a:a2a-transport:a2a-transport-client-jsonrpc-http"))
    implementation(project(":a2a:a2a-transport:a2a-transport-server-jsonrpc-http"))

    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.server.netty)
    implementation(libs.oshai.kotlin.logging)
    runtimeOnly(libs.logback.classic)
}
