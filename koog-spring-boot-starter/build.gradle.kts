import ai.koog.gradle.publish.maven.Publishing.publishToMaven

group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.jvm")
    id("ai.kotlin.jvm.publish")
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.management)
}

kotlin {
    explicitApi()
}

dependencies {
    api(project(":koog-agents"))

    implementation(project.dependencies.platform(libs.spring.boot.bom))
    api(libs.bundles.spring.boot.core)
    api(libs.reactor.kotlin.extensions)
    runtimeOnly(libs.ktor.client.apache5)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

publishToMaven()
