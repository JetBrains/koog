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
        javaParameters.set(true)
    }
}
tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_17.toString()
    targetCompatibility = JavaVersion.VERSION_17.toString()
    options.compilerArgs.add("-parameters")
}
dependencies {
    api(project(":prompt:prompt-executor:prompt-executor-clients"))
    api(project(":prompt:prompt-executor:prompt-executor-llms"))
    api(project(":prompt:prompt-executor:prompt-executor-model"))
    implementation(project.dependencies.platform(libs.spring.boot.bom))
    implementation(project.dependencies.platform(libs.spring.ai.bom))
    api(libs.bundles.spring.boot.core)
    api(libs.spring.ai.model)
    implementation(libs.kotlinx.coroutines.reactor)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit.platform.launcher)
}
publishToMaven()
