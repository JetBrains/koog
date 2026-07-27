import ai.koog.gradle.publish.maven.Publishing.publishToMaven
import org.gradle.kotlin.dsl.implementation
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

val isBeta by extra(true)

plugins {
    id("ai.kotlin.jvm")
    id("ai.kotlin.jvm.publish")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    explicitApi()
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback.classic)
    implementation(libs.aws.sdk.kotlin.bedrockruntime)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.server.sse)
    testImplementation(kotlin("test-junit5"))
}

publishToMaven()
