group = "${rootProject.group}.agents"
version = rootProject.version

plugins {
    id("ai.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
    id("ai.grazie.gradle.plugins.credentialsresolver")
    application
    alias(libs.plugins.shadow)
}

// Configure the application plugin with a default main class
application {
    mainClass.set("ai.grazie.code.agents.example.calculator.CalculatorKt")
}

dependencies {
    implementation(project(":code-agents:code-agents-local-features:code-agents-local-features-memory"))
    implementation(project(":agents:agents-local-strategies"))
    implementation(project(":agents:agents-tools-registry"))
    implementation("ai.jetbrains.code.exec:code-exec-jvm:1.0.0-beta.55+0.4.45")
    implementation("ai.jetbrains.code.exec:code-exec-tools:1.0.0-beta.55+0.4.45")
    implementation("ai.jetbrains.code.features:code-features-common:1.0.0-beta.55+0.4.45")
    implementation("ai.jetbrains.code.files:code-files-jvm:1.0.0-beta.55+0.4.45")
    implementation("ai.jetbrains.code.files:code-files-sandbox:1.0.0-beta.55+0.4.45")
    implementation("ai.jetbrains.code.files:code-files-tools:1.0.0-beta.55+0.4.45")
    implementation("ai.jetbrains.code.files:code-files-vfs:1.0.0-beta.55+0.4.45")

    implementation(project(":prompt:prompt-markdown"))
    implementation(project(":prompt:prompt-structure"))

    implementation(libs.ai.grazie.api.gateway.client)
    implementation(libs.ai.grazie.client.ktor)
    implementation(libs.logback.classic)

    testImplementation(kotlin("test"))
    testImplementation(project(":agents:agents-test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}

val envs = credentialsResolver.resolve(
    layout.projectDirectory.file(provider { "env.properties" })
)

fun registerRunExampleTask(name: String, mainClassName: String) = tasks.register<JavaExec>(name) {
    doFirst {
        standardInput = System.`in`
        standardOutput = System.out
        environment(envs.get())
    }

    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath
}

registerRunExampleTask("runExampleCalculator", "ai.grazie.code.agents.example.calculator.CalculatorKt")
registerRunExampleTask("runExampleCalculatorV2", "ai.grazie.code.agents.example.calculator.v2.CalculatorKt")
registerRunExampleTask("runExampleCalculatorLocal", "ai.grazie.code.agents.example.calculator.local.CalculatorKt")
registerRunExampleTask("runExampleErrorFixing", "ai.grazie.code.agents.example.errors.ErrorFixingAgentKt")
registerRunExampleTask("runExampleErrorFixingLocal", "ai.grazie.code.agents.example.errors.local.ErrorFixingLocalAgentKt")
registerRunExampleTask("runExampleGuesser", "ai.grazie.code.agents.example.guesser.GuesserKt")
registerRunExampleTask("runExampleEssay", "ai.grazie.code.agents.example.essay.EssayWriterKt")
registerRunExampleTask("runExampleFleetProjectTemplateGeneration", "ai.grazie.code.agents.example.templategen.FleetProjectTemplateGenerationKt")
registerRunExampleTask("runExampleTemplate", "ai.grazie.code.agents.example.template.TemplateKt")
registerRunExampleTask("runProjectAnalyzer", "ai.grazie.code.agents.example.ProjectAnalyzerAgentKt")
registerRunExampleTask("runExampleStructuredOutput", "ai.grazie.code.agents.example.structureddata.StructuredDataExampleKt")
registerRunExampleTask("runExampleMarkdownStreaming", "ai.grazie.code.agents.example.structureddata.MarkdownStreamingDataExampleKt")
registerRunExampleTask("runExampleMarkdownStreamingWithTool", "ai.grazie.code.agents.example.structureddata.MarkdownStreamingWithToolsExampleKt")
registerRunExampleTask("runExampleRiderProjectTemplate", "ai.grazie.code.agents.example.rider.project.template.RiderProjectTemplateKt")
registerRunExampleTask("runExampleExecSandbox", "ai.grazie.code.agents.example.execsandbox.ExecSandboxKt")
