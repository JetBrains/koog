import ai.koog.gradle.publish.maven.Publishing.publishToMaven
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

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

// Override JVM target to 17 for Spring Boot 3.x compatibility
tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_17.toString()
    targetCompatibility = JavaVersion.VERSION_17.toString()
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
