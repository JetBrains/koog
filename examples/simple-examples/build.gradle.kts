plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    id("ai.koog.gradle.plugins.credentialsresolver")
}

dependencies {
    implementation(platform(libs.kotlin.bom))
    implementation(libs.mcp.server)
    implementation(libs.mcp.client)

    /*
     Koog dependencies from composite build.
     You can replace them with dependencies on the exact published version instead of composite build.
     */
    //noinspection UseTomlInstead
    implementation("ai.koog:koog-agents")
    //noinspection UseTomlInstead
    implementation("ai.koog:agents-mcp-server")
    //noinspection UseTomlInstead
    implementation("ai.koog:koog-ktor")
    //noinspection UseTomlInstead
    implementation("ai.koog:agents-features-sql")
    //noinspection UseTomlInstead
    implementation("ai.koog:agents-features-a2a-server")
    //noinspection UseTomlInstead
    implementation("ai.koog:agents-features-a2a-client")
    //noinspection UseTomlInstead
    implementation("ai.koog:a2a-transport-server-jsonrpc-http")
    //noinspection UseTomlInstead
    implementation("ai.koog:a2a-transport-client-jsonrpc-http")
    //noinspection UseTomlInstead
    implementation("ai.koog:agents-features-acp")
    //noinspection UseTomlInstead
    testImplementation("ai.koog:agents-test")

    implementation(libs.kotlinx.datetime)

    implementation(libs.logback.classic)

    implementation(platform(libs.opentelemetry.bom))
    implementation(platform(libs.ktor.bom))
    implementation(libs.opentelemetry.exporter.logging)
    implementation(libs.opentelemetry.exporter.otlp)

    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.server.cio)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}

tasks.withType<Test> {
    useJUnitPlatform()
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
registerRunExampleTask("runExampleStructuredOutputSimple", "ai.koog.agents.example.structuredoutput.SimpleExampleKt")
registerRunExampleTask(
    "runExampleStructuredOutputAdvancedWithBasicSchema",
    "ai.koog.agents.example.structuredoutput.AdvancedWithBasicSchemaKt"
)
registerRunExampleTask(
    "runExampleStructuredOutputAdvancedWithStandardSchema",
    "ai.koog.agents.example.structuredoutput.AdvancedWithStandardSchemaKt"
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
registerRunExampleTask("runExampleStreamingWithTools", "ai.koog.agents.example.streaming.StreamingAgentWithToolsKt")

registerRunExampleTask("runExampleGOAPGrouper", "ai.koog.agents.example.goap.GrouperAgentKt")
/*
 A2A examples
*/

// Simple joke generation
registerRunExampleTask("runExampleSimpleJokeAgentServer", "ai.koog.agents.example.a2a.simplejoke.ServerKt")
registerRunExampleTask("runExampleSimpleJokeAgentClient", "ai.koog.agents.example.a2a.simplejoke.ClientKt")

// Advanced joke generation
registerRunExampleTask("runExampleAdvancedJokeAgentServer", "ai.koog.agents.example.a2a.advancedjoke.ServerKt")
registerRunExampleTask("runExampleAdvancedJokeAgentClient", "ai.koog.agents.example.a2a.advancedjoke.ClientKt")


/*
 ACP examples
*/
registerRunExampleTask("runExampleAcpApp", "ai.koog.agents.example.acp.KoogAcpAppKt")

/*
 Langfuse examples
*/
registerRunExampleTask("runExampleLangfuseApp", "ai.koog.agents.example.features.langfuse.LangfuseKt")
