import ai.koog.gradle.publish.maven.Publishing.publishToMaven
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
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
        javaParameters.set(true)
    }
}
tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_17.toString()
    targetCompatibility = JavaVersion.VERSION_17.toString()
    options.compilerArgs.add("-parameters")
}
dependencies {
    implementation(project.dependencies.platform(libs.spring.boot.bom))
    api(libs.bundles.spring.boot.core)
}
publishToMaven()
