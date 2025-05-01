import ai.grazie.gradle.publish.maven.publishToGraziePublicMaven

group = "${rootProject.group}.prompt"
version = rootProject.version

plugins {
    id("ai.kotlin.jvm")
    id("ai.kotlin.jvm.publish")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":code-prompt:code-prompt-executor:code-prompt-executor-model"))
    implementation(project(":prompt:prompt-executor:prompt-executor-tools"))

    implementation(libs.ai.grazie.utils.common)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
}

publishToGraziePublicMaven()
