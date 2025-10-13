package ai.koog.agents.example.features.opentelemetry

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.utils.io.use
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import kotlinx.coroutines.runBlocking

/**
 * Example of using OpenTelemetry with Koog agents
 *
 * Example has two trace exporters:
 * - Log (can be viewed in standard log output)
 * - Otlp (can be viewed on a deployed local server)
 *
 * To start a local OTLP server, please follow the instruction below.
 * Before running this example, start the OpenTelemetry services with:
 * ```
 * ./docker-compose up -d
 * ```
 *
 * After running, you can view traces in the Jaeger UI at: http://localhost:16686
 *
 * To stop the services:
 * ```
 * docker-compose down
 * ```
 */
fun main() = runBlocking {
    val agent = AIAgent(
        promptExecutor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
        llmModel = OpenAIModels.Reasoning.O4Mini,
        systemPrompt = "You are a code assistant. Provide concise code examples."
    ) {
        install(OpenTelemetry) {
            // Add a console logger for local debugging
            addSpanExporter(LoggingSpanExporter.create())

            // Send traces to OpenTelemetry collector
            addSpanExporter(
                OtlpGrpcSpanExporter.builder()
                    .setEndpoint("http://localhost:4317")
                    .build()
            )
        }
    }

    agent.use { agent ->
        println("Running agent with OpenTelemetry tracing...")

        val result = agent.run("Tell me a joke about programming")

        println(
            "Agent run completed with result: '$result'." +
                "\nCheck Jaeger UI at http://localhost:16686 to view traces"
        )
    }
}
