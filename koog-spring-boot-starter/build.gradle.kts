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

    compileOnly(libs.bundles.spring.boot.web)
    compileOnly(libs.bundles.spring.boot.security)
    compileOnly(libs.spring.boot.actuator)
    compileOnly(libs.spring.boot.processor)

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.jupiter")
        exclude(group = "org.junit.platform")
    }
    testImplementation(platform("org.junit:junit-bom:${libs.versions.junit.get()}"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testImplementation(kotlin("test"))
}

publishToMaven()