package ai.koog.agents.example.features.langfuse

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.tool
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.integration.langfuse.addLangfuseExporter
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.server.startSseMcpServer
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import io.ktor.server.cio.*
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Example of Koog agents tracing to [Langfuse](https://langfuse.com/)
 *
 * Agent traces are exported to:
 * - Langfuse OTLP endpoint instance using [OtlpHttpSpanExporter]
 *
 * To run this example:
 *  1. Set up a Langfuse project and credentials as described [here](https://langfuse.com/docs/get-started#create-new-project-in-langfuse)
 *  2. Get Langfuse credentials as described [here](https://langfuse.com/faq/all/where-are-langfuse-api-keys)
 *  3. Set `LANGFUSE_HOST`, `LANGFUSE_PUBLIC_KEY`, and `LANGFUSE_SECRET_KEY` environment variables
 *  4. Set `OPENAI_API_KEY` from [here](https://platform.openai.com/account/api-keys)
 *
 * @see <a href="https://langfuse.com/docs/opentelemetry/get-started#opentelemetry-endpoint">Langfuse OpenTelemetry Docs</a>
 */
suspend fun main() {
    val server = startSseMcpServer(CIO, port = 30001, host = "localhost", ToolRegistry {
        tool(::applyArithmetic)
    })
    delay(1.seconds)
    try {
        val tools = McpToolRegistryProvider.fromSseUrl("http://localhost:30001")
        val agent = AIAgent(
            promptExecutor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
            llmModel = OpenAIModels.Chat.GPT4_1Mini,
            systemPrompt = "You are a code assistant. Provide concise code examples.",
            toolRegistry = ToolRegistry { tool(::sin); tool(::cos) } + tools
        ) {
            install(OpenTelemetry) {
                addLangfuseExporter()
            }
        }

        println("Running agent with Langfuse tracing")

        val result = agent.run("Compute (sin(1) + cos(2)) * 69 using available tools")

        println("Result: $result\nSee traces on the Langfuse instance")
    } finally {
        server.close()
    }
}

@Tool
@LLMDescription("Calculates the result of an arithmetic expression")
fun applyArithmetic(
    @LLMDescription("Arithmetic operation: +-*/") op: String,
    @LLMDescription("First operand") a: Double,
    @LLMDescription("Second operand") b: Double
): Double = when (op) {
    "+" -> a + b
    "-" -> a - b
    "*" -> a * b
    "/" -> a / b
    else -> throw IllegalArgumentException("Unsupported operation: $op")
}

@Tool
@LLMDescription("sin")
fun sin(
    @LLMDescription("Operand") x: Double
): Double = kotlin.math.sin(x)

@Tool
@LLMDescription("cos")
fun cos(
    @LLMDescription("Operand") x: Double
): Double = kotlin.math.cos(x)
