import ai.koog.gradle.publish.maven.Publishing.publishToMaven

plugins {
    id("ai.kotlin.jvm")
    id("ai.kotlin.jvm.publish")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":http-client:http-client-core"))

    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.webflux)

    implementation(project(":utils"))
    implementation(libs.kotlinx.coroutines.reactor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.oshai.kotlin.logging)
    implementation(libs.reactor.netty.http)
    implementation(libs.spring.context)

    testImplementation(project(":http-client:http-client-test"))
    testImplementation(project(":test-utils"))
    testImplementation(kotlin("test-junit5"))
}

kotlin {
    explicitApi()
}

publishToMaven()
