import ai.koog.gradle.publish.maven.Publishing.publishToMaven

plugins {
    id("ai.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
}

group = rootProject.group
version = rootProject.version

dependencies {
    api(project(":http-client:http-client-core"))
    implementation(project(":utils"))
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.oshai.kotlin.logging)

    testImplementation(project(":http-client:http-client-test"))
    testImplementation(project(":test-utils"))
    testImplementation(kotlin("test-junit5"))
}

kotlin {
    explicitApi()
}

publishToMaven()
