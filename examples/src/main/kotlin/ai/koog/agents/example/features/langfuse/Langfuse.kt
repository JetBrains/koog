package ai.koog.agents.example.features.langfuse

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Example of Koog agents tracing to [Langfuse](https://langfuse.com/)
 *
 * Agent traces are exported to:
 * - Console using [LoggingSpanExporter]
 * - Langfuse OTLP endpoint instance using [OtlpHttpSpanExporter]
 *
 * To run this example:
 *  1. Set up a Langfuse project and credentials as described [here](https://langfuse.com/docs/get-started#create-new-project-in-langfuse)
 *  2. Get Langfuse credentials as described [here](https://langfuse.com/faq/all/where-are-langfuse-api-keys)
 *  3. Set `LANGFUSE_HOST`, `LANGFUSE_PUBLIC_KEY`, and `LANGFUSE_SECRET_KEY` environment variables
 *
 * @see <a href="https://langfuse.com/docs/opentelemetry/get-started#opentelemetry-endpoint">Langfuse OpenTelemetry Docs</a>
 */
fun main() = runBlocking {
    val langfuseUrl = System.getenv()["LANGFUSE_HOST"] ?: throw IllegalArgumentException("LANGFUSE_HOST is not set")

    val agent = AIAgent(
        executor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
        llmModel = OpenAIModels.Reasoning.GPT4oMini,
        systemPrompt = "You are a code assistant. Provide concise code examples."
    ) {
        install(OpenTelemetry) {
            addSpanExporter(
                createLangfuseSpanExporter(
                    langfuseUrl,
                    System.getenv()["LANGFUSE_PUBLIC_KEY"] ?: throw IllegalArgumentException("LANGFUSE_PUBLIC_KEY is not set"),
                    System.getenv()["LANGFUSE_SECRET_KEY"] ?: throw IllegalArgumentException("LANGFUSE_SECRET_KEY is not set"),
                )
            )
        }
    }

    println("Running agent with Langfuse tracing")

    val result = agent.run("Tell me a joke about programming")

    println("Result: $result\nSee traces on $langfuseUrl")
}

private fun createLangfuseSpanExporter(
    langfuseUrl: String,
    langfusePublicKey: String,
    langfuseSecretKey: String,
): SpanExporter {
    val credentials = "$langfusePublicKey:$langfuseSecretKey"
    val auth = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))

    return OtlpHttpSpanExporter.builder()
        .setTimeout(30, TimeUnit.SECONDS)
        .setEndpoint("$langfuseUrl/api/public/otel/v1/traces")
        .addHeader("Authorization", "Basic $auth")
        .build()
}