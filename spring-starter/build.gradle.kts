import ai.grazie.gradle.publish.maven.Publishing.publishToMaven

group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.management)
}

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(project(":koog-agents"))

                implementation(project.dependencies.enforcedPlatform(libs.spring.boot.bom))
                api(libs.bundles.spring.boot.core)
                api(libs.reactor.kotlin.extensions)

                compileOnly(libs.bundles.spring.boot.web)
                compileOnly(libs.bundles.spring.boot.security)
                compileOnly(libs.spring.boot.actuator)
                compileOnly(libs.spring.boot.processor)
            }
        }
    }

    explicitApi()
}

publishToMaven()