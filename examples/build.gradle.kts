group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
    id("ai.koog.gradle.plugins.credentialsresolver")
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
    api(project(":agents:agents-ext"))
    api(project(":agents:agents-mcp"))
    api(project(":agents:agents-features:agents-features-event-handler"))
    api(project(":agents:agents-features:agents-features-memory"))
    api(project(":agents:agents-features:agents-features-opentelemetry"))
    api(project(":agents:agents-features:agents-features-snapshot"))
    api(project(":agents:agents-features:agents-features-sql"))

    api(project(":prompt:prompt-markdown"))
    api(project(":prompt:prompt-structure"))
    api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
    api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-anthropic-client"))
    api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-bedrock-client"))
    api(project(":prompt:prompt-executor:prompt-executor-llms"))
    api(project(":prompt:prompt-executor:prompt-executor-llms-all"))
    api(project(":koog-ktor"))

    api(libs.kotlinx.datetime)

    implementation(libs.logback.classic)
    implementation(libs.opentelemetry.exporter.logging)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.ktor.server.cio)
    implementation(project.dependencies.platform(libs.opentelemetry.bom))

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
registerRunExampleTask(
    "runExampleFleetProjectTemplateGeneration",
    "ai.koog.agents.example.templategen.FleetProjectTemplateGenerationKt"
)
registerRunExampleTask("runExampleTemplate", "ai.koog.agents.example.template.TemplateKt")
registerRunExampleTask("runProjectAnalyzer", "ai.koog.agents.example.ProjectAnalyzerAgentKt")
registerRunExampleTask(
    "runExampleAdvancedSimpleStructuredOutput",
    "ai.koog.agents.example.structuredoutput.AdvancedSimpleStructuredOutputExampleKt"
)
registerRunExampleTask(
    "runExampleAdvancedFullStructuredOutput",
    "ai.koog.agents.example.structuredoutput.AdvancedFullStructuredOutputExampleKt"
)
registerRunExampleTask(
    "runExampleMarkdownStreaming",
    "ai.koog.agents.example.structuredoutput.MarkdownStreamingDataExampleKt"
)
registerRunExampleTask(
    "runExampleMarkdownStreamingWithTool",
    "ai.koog.agents.example.structuredoutput.MarkdownStreamingWithToolsExampleKt"
)
registerRunExampleTask(
    "runExampleRiderProjectTemplate",
    "ai.koog.agents.example.rider.project.template.RiderProjectTemplateKt"
)
registerRunExampleTask("runExampleExecSandbox", "ai.koog.agents.example.execsandbox.ExecSandboxKt")
registerRunExampleTask("runExampleLoopComponent", "ai.koog.agents.example.components.loop.ProjectGeneratorKt")
registerRunExampleTask(
    "runExampleInstagramPostDescriber",
    "ai.koog.agents.example.attachments.InstagramPostDescriberKt"
)
registerRunExampleTask("runExampleRoutingViaGraph", "ai.koog.agents.example.banking.routing.RoutingViaGraphKt")
registerRunExampleTask(
    "runExampleRoutingViaAgentsAsTools",
    "ai.koog.agents.example.banking.routing.RoutingViaAgentsAsToolsKt"
)
registerRunExampleTask("runExampleFeatureOpenTelemetry", "ai.koog.agents.example.feature.OpenTelemetryKt")
registerRunExampleTask("runExampleBedrockAgent", "ai.koog.agents.example.client.BedrockAgentKt")
registerRunExampleTask("runExampleJokesWithModeration", "ai.koog.agents.example.moderation.JokesWithModerationKt")
registerRunExampleTask("runExampleFilePersistentAgent", "ai.koog.agents.example.snapshot.FilePersistentAgentExampleKt")
registerRunExampleTask("runExampleSQLPersistentAgent", "ai.koog.agents.example.snapshot.sql.SQLPersistentAgentExample")
registerRunExampleTask("runExampleWebSearchAgent", "ai.koog.agents.example.websearch.WebSearchAgentKt")

dokka {
    dokkaSourceSets.named("main") {
        suppress.set(true)
    }
}
