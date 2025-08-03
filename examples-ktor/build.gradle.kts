group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
    id("ai.koog.gradle.plugins.credentialsresolver")
    application
    alias(libs.plugins.ktor)
}

repositories {
    maven(url = "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

// Configure the application plugin with a default main class
application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

dependencies {
    // Koog dependencies
    api(project(":agents:agents-ext"))
    api(project(":agents:agents-mcp"))
    api(project(":agents:agents-features:agents-features-event-handler"))
    api(project(":agents:agents-features:agents-features-memory"))
    api(project(":agents:agents-features:agents-features-opentelemetry"))
    api(project(":agents:agents-features:agents-features-snapshot"))
    api(project(":agents:agents-test"))

    api(project(":prompt:prompt-markdown"))
    api(project(":prompt:prompt-structure"))
    api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
    api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-anthropic-client"))
    api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-bedrock-client"))
    api(project(":prompt:prompt-executor:prompt-executor-llms"))
    api(project(":prompt:prompt-executor:prompt-executor-llms-all"))
    api(project(":koog-ktor"))

    api(libs.kotlinx.datetime)

    // Ktor server dependencies
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.sse)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation("io.ktor:ktor-server-content-negotiation:3.2.2")
    // Logging
    implementation(libs.logback.classic)
    implementation(libs.opentelemetry.exporter.logging)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(project.dependencies.platform(libs.opentelemetry.bom))

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.ktor.server.test.host)
}

val envs = credentialsResolver.resolve(
    layout.projectDirectory.file(provider { "env.properties" })
)

// Ktor configuration
ktor {
    fatJar {
        archiveFileName.set("examples-ktor-fat.jar")
    }
}

tasks.withType<JavaExec> {
    environment(envs.get())
}

dokka {
    dokkaSourceSets.named("main") {
        suppress.set(true)
    }
}

tasks.register<JavaExec>("runKtorIntegration") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ai.koog.agents.example.ktor.KtorIntegrationExampleKt")
    args = listOf("-config=src/main/resources/application.yaml")
    environment(envs.get())
}

tasks.register<JavaExec>("runKtorGovernance") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ai.koog.agents.example.ktor.KtorGovernanceExampleKt")
    args = listOf("-config=src/main/resources/application-governance.yaml")
    environment(envs.get())
}
