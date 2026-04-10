package ai.koog.integration.tests.features

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.testing.tools.MockExecutorDSLBuilder
import ai.koog.agents.testing.tools.RandomNumberTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.integration.tests.utils.tools.CalculatorOperation
import ai.koog.integration.tests.utils.tools.CalculatorTool
import ai.koog.integration.tests.utils.tools.SimpleCalculatorArgs
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.serialization.kotlinx.KotlinxSerializer
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.opentelemetry.kotlin.tracing.model.ReadWriteSpan
import io.opentelemetry.kotlin.tracing.model.ReadableSpan
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test

/**
 * Integration tests for the MCP feature with OpenTelemetry tracing.
 */
@OptIn(InternalAgentsApi::class, ExperimentalApi::class)
class McpOpenTelemetryIntegrationTest {

    /**
     * Test-only [SpanProcessor] that captures spans synchronously on the calling thread in [onEnd].
     *
     * The production-style alternative — `simpleSpanProcessor(inMemorySpanExporter())` — schedules the
     * export through `scope.launch` on `Dispatchers.Default`, so spans land in the exporter
     * asynchronously after the span ends. Tests that read the exporter immediately after `agent.run`
     * returns would race with those launches and observe a partial set of spans (CI flake).
     *
     * `SpanProcessor.onEnd(ReadableSpan)` is non-suspending and `ReadableSpan.toSpanData()` is a
     * synchronous snapshot, so capturing here makes the result deterministic: when `agent.run`
     * returns, every span ended during the run is already in [spans] — no polling, no `forceFlush`,
     * no `runTest` scheduler tricks (the Kotlin SDK's simple/batch processors hold their own
     * `Dispatchers.Default` scope, which `runTest`/`advanceUntilIdle` cannot reach anyway).
     */
    @OptIn(ExperimentalApi::class)
    private class TestSpanProcessor : SpanProcessor {
        private val lock = Any()
        private val collectedSpans = mutableListOf<SpanData>()

        val spans: List<SpanData>
            get() {
                return synchronized(lock) {
                    collectedSpans.toList()
                }
            }

        override fun onStart(span: ReadWriteSpan, parentContext: Context) {}
        override fun onEnding(span: ReadWriteSpan) {}

        override fun onEnd(span: ReadableSpan) {
            synchronized(lock) {
                collectedSpans += span.toSpanData()
            }
        }

        override fun isStartRequired(): Boolean = false
        override fun isEndRequired(): Boolean = true

        override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
        override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
    }

    companion object {
        private lateinit var server: Server

        @BeforeAll
        @JvmStatic
        fun cleanup() {
            server = runBlocking {
                startMcpServer(ToolRegistry { tool(RandomNumberTool()) })
            }
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            runBlocking {
                closeMcpServer(server, McpServerPort)
            }
        }
    }

    private val serializer = KotlinxSerializer()

    @Test
    fun `should create OpenTelemetry spans for MCP tool calls`() = runTestWithTimeout {
        runAgentWithMcpAndOtel({
            mockLLMToolCall(RandomNumberTool(), RandomNumberTool.Args(42)) onRequestEquals "test"
        }) { spans ->
            val mcpToolCall = spans.single { it.attributes.containsKey("mcp.method.name") }
            verifyMcpSpanAttributes(mcpToolCall = mcpToolCall, port = McpServerPort, toolName = RandomNumberTool().name)
        }
    }

    @Test
    fun `should create OpenTelemetry spans for regular tool calls`() = runTestWithTimeout {
        runAgentWithMcpAndOtel({
            mockLLMToolCall(CalculatorTool, SimpleCalculatorArgs(CalculatorOperation.ADD, 1, 1)) onRequestEquals "test"
        }) { spans ->
            val mcpToolCalls = spans.filter { it.attributes.containsKey("mcp.method.name") }
            mcpToolCalls.size shouldBe 0
            val toolCalls = spans.filter { it.attributes.containsKey("gen_ai.tool.name") }
            toolCalls.size shouldBe 1
            toolCalls.first().attributes["gen_ai.tool.name"] shouldBe CalculatorTool.name
        }
    }

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
            val mcpToolCall = spans.filter { it.attributes.containsKey("mcp.method.name") }
            mcpToolCall.size shouldBe 2
        }
    }

    //region Private Methods

    private fun verifyMcpSpanAttributes(mcpToolCall: SpanData, port: Int, toolName: String) {
        mcpToolCall.attributes["mcp.protocol.version"] shouldBe LATEST_PROTOCOL_VERSION
        mcpToolCall.attributes["mcp.method.name"] shouldBe "tools/call"
        mcpToolCall.attributes["server.address"] shouldBe "http://localhost:$port"
        mcpToolCall.attributes["server.port"] shouldBe port.toLong()
        mcpToolCall.attributes["network.transport"] shouldBe "tcp"
        mcpToolCall.attributes["gen_ai.tool.name"] shouldBe toolName
    }

    private suspend fun runAgentWithMcpAndOtel(
        builder: MockExecutorDSLBuilder.() -> Unit = {},
        checkBody: (List<SpanData>) -> Unit
    ) {
        val spanProcessor = TestSpanProcessor()
        val agent = createAgentWithMcpAndOtel(builder, spanProcessor)
        agent.run("test")
        checkBody(spanProcessor.spans)
    }

    private suspend fun createAgentWithMcpAndOtel(
        builder: MockExecutorDSLBuilder.() -> Unit = {},
        spanProcessor: SpanProcessor,
    ): AIAgent<String, String> {
        val mcpTools = McpToolRegistryProvider.fromSseUrl("http://localhost:$McpServerPort")
        return AIAgent(
            promptExecutor = getMockExecutor(serializer) {
                builder(this)
            },
            llmModel = OpenAIModels.Chat.GPT4o,
            toolRegistry = ToolRegistry { tool(CalculatorTool) } + mcpTools
        ) {
            install(OpenTelemetry) {
                setVerbose(true)
                addSpanProcessor { spanProcessor }
            }
        }
    }

    //endregion Private Methods
}
