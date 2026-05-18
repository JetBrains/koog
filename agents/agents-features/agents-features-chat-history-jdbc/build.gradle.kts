import ai.koog.gradle.publish.maven.Publishing.publishToMaven


plugins {
    id("ai.kotlin.jvm")
    id("ai.kotlin.jvm.publish")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":agents:agents-features:agents-features-memory"))
    api(project(":agents:agents-features:agents-features-chat-memory-sql"))

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
    testImplementation("org.testcontainers:oracle-xe:${libs.versions.testcontainers.get()}")

    testImplementation(libs.h2)
    testImplementation(libs.postgresql)
    testImplementation(libs.mysql)
    testImplementation("com.oracle.database.jdbc:ojdbc11:23.26.2.0.0")
}

kotlin {
    explicitApi()
}

publishToMaven()
