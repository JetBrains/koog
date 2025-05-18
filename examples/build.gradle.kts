group = "${rootProject.group}.agents"
version = rootProject.version

plugins {
    id("ai.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
    id("ai.grazie.gradle.plugins.credentialsresolver")
    application
    alias(libs.plugins.shadow)
}

repositories {
    maven(url = "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

// Configure the application plugin with a default main class
application {
    mainClass.set("ai.koog.agents.example.calculator.CalculatorKt")
}

dependencies {
    implementation(project(":agents:agents-ext"))
    implementation(project(":agents:agents-mcp"))
    implementation(project(":agents:agents-features:agents-features-event-handler"))
    implementation(project(":agents:agents-features:agents-features-memory"))
    implementation("ai.jetbrains.code.exec:code-exec-jvm:1.0.0-beta.55+0.4.45")
    implementation("ai.jetbrains.code.exec:code-exec-tools:1.0.0-beta.55+0.4.45")
    implementation("ai.jetbrains.code.features:code-features-common:1.0.0-beta.55+0.4.45")
    implementation("ai.jetbrains.code.files:code-files-jvm:1.0.0-beta.55+0.4.45")
    implementation("ai.jetbrains.code.files:code-files-sandbox:1.0.0-beta.55+0.4.45")
    implementation("ai.jetbrains.code.files:code-files-tools:1.0.0-beta.55+0.4.45")
    implementation("ai.jetbrains.code.files:code-files-vfs:1.0.0-beta.55+0.4.45")
    implementation(libs.kotlinx.datetime)

    implementation(project(":prompt:prompt-markdown"))
    implementation(project(":prompt:prompt-structure"))
    implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
    implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-anthropic-client"))
    implementation(project(":prompt:prompt-executor:prompt-executor-llms"))
    implementation(project(":prompt:prompt-executor:prompt-executor-llms-all"))

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

registerRunExampleTask("runExampleCalculator", "ai.koog.agents.example.calculator.CalculatorKt")
registerRunExampleTask("runExampleCalculatorV2", "ai.koog.agents.example.calculator.v2.CalculatorKt")
registerRunExampleTask("runExampleCalculatorLocal", "ai.koog.agents.example.calculator.local.CalculatorKt")
registerRunExampleTask("runExampleErrorFixing", "ai.koog.agents.example.errors.ErrorFixingAgentKt")
registerRunExampleTask("runExampleErrorFixingLocal", "ai.koog.agents.example.errors.local.ErrorFixingLocalAgentKt")
registerRunExampleTask("runExampleGuesser", "ai.koog.agents.example.guesser.GuesserKt")
registerRunExampleTask("runExampleEssay", "ai.koog.agents.example.essay.EssayWriterKt")
registerRunExampleTask("runExampleFleetProjectTemplateGeneration", "ai.koog.agents.example.templategen.FleetProjectTemplateGenerationKt")
registerRunExampleTask("runExampleTemplate", "ai.koog.agents.example.template.TemplateKt")
registerRunExampleTask("runProjectAnalyzer", "ai.koog.agents.example.ProjectAnalyzerAgentKt")
registerRunExampleTask("runExampleStructuredOutput", "ai.koog.agents.example.structureddata.StructuredDataExampleKt")
registerRunExampleTask("runExampleMarkdownStreaming", "ai.koog.agents.example.structureddata.MarkdownStreamingDataExampleKt")
registerRunExampleTask("runExampleMarkdownStreamingWithTool", "ai.koog.agents.example.structureddata.MarkdownStreamingWithToolsExampleKt")
registerRunExampleTask("runExampleRiderProjectTemplate", "ai.koog.agents.example.rider.project.template.RiderProjectTemplateKt")
registerRunExampleTask("runExampleExecSandbox", "ai.koog.agents.example.execsandbox.ExecSandboxKt")
registerRunExampleTask("runExampleLoopComponent", "ai.koog.agents.example.components.loop.ProjectGeneratorKt")

dokka {
    dokkaSourceSets.named("main") {
        suppress.set(true)
    }
}
