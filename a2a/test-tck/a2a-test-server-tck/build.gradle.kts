plugins {
    id("ai.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
    application
}

val isBeta by extra(true)

application {
    mainClass = "ai.koog.a2a.test.tck.MainKt"
}

dependencies {
    implementation(project(":a2a:a2a-server"))
    implementation(project(":a2a:a2a-transport:a2a-transport-server-jsonrpc-http"))

    implementation(libs.ktor.server.netty)
    implementation(libs.oshai.kotlin.logging)
    runtimeOnly(libs.logback.classic)
}
