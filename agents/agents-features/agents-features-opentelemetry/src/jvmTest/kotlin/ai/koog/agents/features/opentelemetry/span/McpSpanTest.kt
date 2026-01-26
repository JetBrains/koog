package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.core.feature.handler.tool.McpTransportType
import ai.koog.agents.core.feature.model.AIAgentError
import ai.koog.agents.features.opentelemetry.integration.mcp.McpMethod
import ai.koog.agents.features.opentelemetry.mock.MockTracer
import io.opentelemetry.api.trace.SpanKind
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class McpSpanTest {

    private val mockTracer = MockTracer()

    @Test
    fun `startMcpClientSpan should create span with required attributes`() {
        val span = startMcpClientSpan(
            tracer = mockTracer,
            parentSpan = null,
            id = "test-id",
            method = McpMethod.TOOLS_CALL,
            toolName = "search",
            mcpProtocolVersion = "2025-06-18",
            mcpTransportType = McpTransportType.Pipe
        )

        assertNotNull(span)
        assertEquals(SpanType.MCP, span.type)
        assertEquals(SpanKind.CLIENT, span.kind)
        assertEquals("tools/call search", span.name)

        // Verify required attribute is present
        val mcpMethodAttr = span.attributes.find { it.key == "mcp.method.name" }
        assertNotNull(mcpMethodAttr)
        assertEquals("tools/call", mcpMethodAttr.value)
    }

    @Test
    fun `startMcpClientSpan should create span with tool name`() {
        val span = startMcpClientSpan(
            tracer = mockTracer,
            parentSpan = null,
            id = "test-id",
            method = McpMethod.TOOLS_CALL,
            toolName = "weather",
            mcpProtocolVersion = "2025-06-18",
            mcpTransportType = McpTransportType.Pipe
        )

        // Should have gen_ai.tool.name
        val toolNameAttr = span.attributes.find { it.key == "gen_ai.tool.name" }
        assertNotNull(toolNameAttr)
        assertEquals("weather", toolNameAttr.value)

        // Should have gen_ai.operation.name set to execute_tool
        val operationNameAttr = span.attributes.find { it.key == "gen_ai.operation.name" }
        assertNotNull(operationNameAttr)
        assertEquals("execute_tool", operationNameAttr.value)
    }

    @Test
    fun `endMcpClientSpan should add error attributes when error provided`() {
        val span = startMcpClientSpan(
            tracer = mockTracer,
            parentSpan = null,
            id = "test-id",
            method = McpMethod.TOOLS_CALL,
            mcpProtocolVersion = "2025-06-18",
            mcpTransportType = McpTransportType.Pipe
        )

        val error = AIAgentError(Throwable("Test error"))

        endMcpClientSpan(
            span = span,
            error = error,
            rpcStatusCode = "-32603"
        )

        val attrs = span.attributes.associate { it.key to it.value }
        assertNotNull(attrs["error.type"])
        assertEquals("-32603", attrs["rpc.response.status_code"])
    }

    @Test
    fun `endMcpClientSpan should add tool call result when provided`() {
        val span = startMcpClientSpan(
            tracer = mockTracer,
            parentSpan = null,
            id = "test-id",
            method = McpMethod.TOOLS_CALL,
            toolName = "search",
            mcpProtocolVersion = "2025-06-18",
            mcpTransportType = McpTransportType.Pipe
        )

        val toolResult = buildJsonObject {
            put("result", JsonPrimitive("success"))
        }

        endMcpClientSpan(
            span = span,
            toolCallResult = toolResult
        )

        val resultAttr = span.attributes.find { it.key == "gen_ai.tool.call.result" }
        assertNotNull(resultAttr)
    }

    @Test
    fun `MCP client have expected SpanKind`() {
        val clientSpan = startMcpClientSpan(
            tracer = mockTracer,
            parentSpan = null,
            id = "client-id",
            method = McpMethod.TOOLS_CALL,
            mcpProtocolVersion = "2025-06-18",
            mcpTransportType = McpTransportType.Pipe
        )

        assertEquals(SpanKind.CLIENT, clientSpan.kind)
    }

    @Test
    fun `MCP spans should support hierarchical structure`() {
        val parentSpan = startMcpClientSpan(
            tracer = mockTracer,
            parentSpan = null,
            id = "parent-id",
            method = McpMethod.TOOLS_LIST,
            mcpProtocolVersion = "2025-06-18",
            mcpTransportType = McpTransportType.Pipe
        )

        val childSpan = startMcpClientSpan(
            tracer = mockTracer,
            parentSpan = parentSpan,
            id = "child-id",
            method = McpMethod.TOOLS_CALL,
            toolName = "search",
            mcpProtocolVersion = "2025-06-18",
            mcpTransportType = McpTransportType.Pipe
        )

        assertEquals(parentSpan, childSpan.parentSpan)
    }

    @Test
    fun `MCP span naming should omit target when not provided`() {
        val span = startMcpClientSpan(
            tracer = mockTracer,
            parentSpan = null,
            id = "test-id",
            method = McpMethod.RESOURCES_LIST,
            mcpProtocolVersion = "2025-06-18",
            mcpTransportType = McpTransportType.Pipe
        )

        assertEquals("resources/list", span.name)
    }

    @Test
    fun `MCP spans should support tool call arguments`() {
        val arguments = buildJsonObject {
            put("query", JsonPrimitive("test query"))
            put("limit", JsonPrimitive(10))
        }

        val span = startMcpClientSpan(
            tracer = mockTracer,
            parentSpan = null,
            id = "test-id",
            method = McpMethod.TOOLS_CALL,
            toolName = "search",
            toolArgs = arguments,
            mcpProtocolVersion = "2025-06-18",
            mcpTransportType = McpTransportType.Pipe
        )

        val argsAttr = span.attributes.find { it.key == "gen_ai.tool.call.arguments" }
        assertNotNull(argsAttr)
    }
}
