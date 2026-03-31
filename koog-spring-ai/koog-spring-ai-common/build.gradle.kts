import ai.koog.gradle.publish.maven.Publishing.publishToMaven
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
group = rootProject.group
version = rootProject.version
plugins {
    id("ai.kotlin.jvm")
    id("ai.kotlin.jvm.publish")
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
publishToMaven()
