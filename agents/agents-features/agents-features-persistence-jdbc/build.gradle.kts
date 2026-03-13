import ai.koog.gradle.publish.maven.Publishing.publishToMaven

group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.jvm")
    id("ai.kotlin.jvm.publish")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":agents:agents-features:agents-features-snapshot"))
    api(project(":agents:agents-features:agents-features-sql"))

    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)

    compileOnly(libs.h2)
    compileOnly(libs.mysql)
    compileOnly(libs.postgresql)

    testImplementation(kotlin("test-junit5"))
    testImplementation(project(":test-utils"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.mysql)

    testImplementation(libs.h2)
    testImplementation(libs.postgresql)
    testImplementation(libs.mysql)
}

kotlin {
    explicitApi()
}

publishToMaven()
