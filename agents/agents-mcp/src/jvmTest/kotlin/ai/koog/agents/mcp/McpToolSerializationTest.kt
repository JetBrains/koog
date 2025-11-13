package ai.koog.agents.mcp

import ai.koog.agents.core.tools.ToolDescriptor
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class McpToolSerializationTest {
    private fun randomMcpTool(): McpTool = McpTool(
        mcpClient = Client(
            clientInfo = Implementation(
                name = "Test",
                version = "1.0"
            )
        ),
        descriptor = ToolDescriptor(
            name = "test-tool",
            description = "A test tool"
        ),
    )

    @Test
    fun `test encode result`() {
        val result = McpTool.Result(
            promptMessageContents = listOf(TextContent("Hello world"))
        )

        val expected = buildJsonObject {
            put("promptMessageContents", "Hello world")
        }

        assertEquals(expected, randomMcpTool().encodeResult(result))
    }

    @Test
    fun `test decode result`() {
        val expected = McpTool.Result(
            promptMessageContents = listOf(TextContent("Hello world"))
        )

        val json = buildJsonObject {
            put("promptMessageContents", "Hello world")
        }

        assertEquals(expected.textForLLM(), randomMcpTool().decodeResult(json).textForLLM())
    }
}
