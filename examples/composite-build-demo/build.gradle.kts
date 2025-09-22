plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
    id("ai.koog.gradle.plugins.credentialsresolver")
}

application.mainClass.set("ai.koog.example.composite_build_demo.MainKt")

dependencies {
    implementation(platform(libs.kotlin.bom))

    // From composite build
    //noinspection UseTomlInstead
    implementation("ai.koog:koog-agents")
}

val envs = credentialsResolver.resolve(
    layout.projectDirectory.file(provider { "env.properties" })
)
