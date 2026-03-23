import ai.koog.gradle.publish.maven.Publishing.publishToMaven

group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":agents:agents-tools"))
    api(project(":prompt:prompt-executor:prompt-executor-clients"))
    api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-google-client"))
    api(project(":prompt:prompt-llm"))
    api(project(":prompt:prompt-model"))
    api(project(":prompt:prompt-structure"))
    api(project(":utils"))
    api(libs.google.genai)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.oshai.kotlin.logging)

    testImplementation(project(":test-utils"))
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.junit.jupiter.params)
}

publishToMaven()
