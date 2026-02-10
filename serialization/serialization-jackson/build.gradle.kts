import ai.koog.gradle.publish.maven.Publishing.publishToMaven

plugins {
    id("ai.kotlin.jvm")
}

group = rootProject.group
version = rootProject.version

kotlin {
    explicitApi()
}

dependencies {
    api(project(":serialization:serialization-core"))
    api(libs.jackson.module.kotlin)

    testImplementation(kotlin("test-junit5"))
    testImplementation(project(":serialization:serialization-test"))
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.assertions.json)
}

tasks.test {
    useJUnitPlatform()
}

publishToMaven()
