package ai.koog.integration.tests.features

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.testing.tools.MockLLMBuilder
import ai.koog.agents.testing.tools.RandomNumberTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.integration.tests.utils.tools.CalculatorOperation
import ai.koog.integration.tests.utils.tools.CalculatorTool
import ai.koog.integration.tests.utils.tools.SimpleCalculatorArgs
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for MCP feature with OpenTelemetry tracing.
 */
@Execution(ExecutionMode.SAME_THREAD)
class McpOpenTelemetryIntegrationTest {

    private lateinit var server: Server

    @BeforeEach
    fun cleanup() {
        server = runBlocking {
            startMcpServer(ToolRegistry { tool(RandomNumberTool()) })
        }
    }

    @AfterEach
    fun teardown() {
        runBlocking {
            closeMcpServer(server, McpServerPort)
        }
    }

    @OptIn(InternalAgentsApi::class)
    @Test
    fun `should create OpenTelemetry spans for MCP tool calls`() = runTestWithTimeout {
        runAgentWithMcpAndOtel({
            mockLLMToolCall(RandomNumberTool(), RandomNumberTool.Args(42)) onRequestEquals "test"
        }) { spans ->
            val mcpToolCall = spans.single { it.attributes.has("mcp.method.name") }
            verifyCallToolSpan(mcpToolCall = mcpToolCall, port = McpServerPort, toolName = RandomNumberTool().name)
        }
    }

    @OptIn(InternalAgentsApi::class)
    @Test
    fun `should create OpenTelemetry spans for regular tool calls`() = runTestWithTimeout {
        runAgentWithMcpAndOtel({
            mockLLMToolCall(CalculatorTool, SimpleCalculatorArgs(CalculatorOperation.ADD, 1, 1)) onRequestEquals "test"
        }) { spans ->
            val mcpToolCalls = spans.filter { it.attributes.has("mcp.method.name") }
            mcpToolCalls.size shouldBe 0
            val toolCalls = spans.filter { it.attributes.has("gen_ai.tool.name") }
            toolCalls.size shouldBe 1
            toolCalls.first().attributes[AttributeKey.stringKey("gen_ai.tool.name")] shouldBe CalculatorTool.name
        }
    }

    @OptIn(InternalAgentsApi::class)
    @Test
    fun `should trace multiple MCP tool calls in OpenTelemetry`() = runTestWithTimeout {
        runAgentWithMcpAndOtel({
            mockLLMToolCall(
                listOf(
                    RandomNumberTool() to RandomNumberTool.Args(1),
                    RandomNumberTool() to RandomNumberTool.Args(2),
                )
            ) onRequestEquals "test"
        }) { spans ->
            val mcpToolCall = spans.filter { it.attributes.has("mcp.method.name") }
            mcpToolCall.size shouldBe 2
            mcpToolCall.forEach { span ->
                verifyCallToolSpan(mcpToolCall = span, port = McpServerPort, toolName = RandomNumberTool().name)
            }
        }
    }

    private fun verifyCallToolSpan(mcpToolCall: SpanData, port: Int, toolName: String) {
        mcpToolCall.attributes[AttributeKey.stringKey("server.address")] shouldBe "http://localhost:$port"
        mcpToolCall.attributes[AttributeKey.longKey("server.port")] shouldBe port.toLong()
        mcpToolCall.attributes[AttributeKey.stringKey("gen_ai.tool.name")] shouldBe toolName
        mcpToolCall.attributes[AttributeKey.stringKey("mcp.protocol.version")] shouldBe LATEST_PROTOCOL_VERSION
    }

    private suspend fun runAgentWithMcpAndOtel(
        builder: MockLLMBuilder.() -> Unit = {},
        checkBody: (List<SpanData>) -> Unit
    ) {
        val spanExporter = InMemorySpanExporter.create()
        val agent = createAgentWithMcpAndOtel(spanExporter, builder)
        agent.run("test")
        checkBody(waitForSpans(spanExporter))
    }

    private suspend fun createAgentWithMcpAndOtel(
        spanExporter: SpanExporter,
        builder: MockLLMBuilder.() -> Unit = {},
    ): AIAgent<String, String> {
        val mcpTools = McpToolRegistryProvider.fromSseUrl("http://localhost:$McpServerPort")
        return AIAgent(
            promptExecutor = getMockExecutor {
                builder(this)
            },
            llmModel = OpenAIModels.Chat.GPT4o,
            toolRegistry = ToolRegistry { tool(CalculatorTool) } + mcpTools
        ) {
            install(OpenTelemetry) {
                setVerbose(true)
                addSpanExporter(spanExporter)
            }
        }
    }

    /**
     * Waits for spans to be collected by polling the exporter.
     */
    private suspend fun waitForSpans(
        spanExporter: InMemorySpanExporter,
        minSpans: Int = 1,
        timeoutSeconds: Long = 30
    ): List<SpanData> {
        return withTimeout(timeoutSeconds.seconds) {
            while (true) {
                val spans = spanExporter.finishedSpanItems
                if (spans.size >= minSpans) {
                    return@withTimeout spans
                }
                delay(100)
            }
            emptyList()
        }
    }

    private fun Attributes.has(name: String) = asMap().containsKey(AttributeKey.stringKey(name))
}
