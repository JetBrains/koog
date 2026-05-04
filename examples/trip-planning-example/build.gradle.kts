plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    application
}

application.mainClass.set("ai.koog.agents.examples.tripplanning.MainKt")

dependencies {
    implementation(libs.koog.agents)
    implementation(libs.kotlin.bom)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.logback.classic)
    implementation(libs.oshai.logging)
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    standardOutput = System.`out`
    errorOutput = System.err
}

tasks.test {
    useJUnitPlatform()
}
