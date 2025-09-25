package ai.koog.agents.mcp

import ai.koog.agents.core.tools.DirectToolCallsEnabler
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import io.modelcontextprotocol.kotlin.sdk.ImageContent
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(InternalAgentToolsApi::class)
object TestToolEnabler : DirectToolCallsEnabler

class McpToolTest {
    private val testPort = 3001
    private val testServer = TestMcpServer(testPort)

    @BeforeTest
    fun setup() {
        testServer.start()
    }

    @AfterTest
    fun tearDown() {
        testServer.stop()
    }

    @OptIn(InternalAgentToolsApi::class)
    @Test
    fun `test McpTool with SSE transport`() = runTest(timeout = 30.seconds) {
        // Create a tool registry using McpToolRegistryProvider.fromTransport
        val toolRegistry = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(1.minutes) {
                McpToolRegistryProvider.fromTransport(
                    transport = McpToolRegistryProvider.defaultSseTransport("http://localhost:$testPort/sse"),
                    name = "test-client",
                    version = "0.1.0"
                )
            }
        }

        // A list of tools that the server is expected to provide
        val expectedToolDescriptors = listOf(
            ToolDescriptor(
                name = "greeting",
                description = "A simple greeting tool",
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "name",
                        type = ToolParameterType.String,
                        description = "A name to greet",
                    )
                ),
                optionalParameters = listOf(
                    ToolParameterDescriptor(
                        name = "title",
                        type = ToolParameterType.String,
                        description = "Title to use in the greeting",
                    )
                )
            )
        )

        // Actual list of tools provided
        val actualToolDescriptor = toolRegistry.tools.map { it.descriptor }
        assertEquals(expectedToolDescriptors, actualToolDescriptor)

        // Now test the actual tool
        val greetingTool = toolRegistry.getTool("greeting") as McpTool
        val args = McpTool.Args(buildJsonObject { put("name", "Test") })

        val result = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(1.minutes) {
                greetingTool.execute(args, TestToolEnabler)
            }
        }

        val content = result.promptMessageContents.first() as TextContent
        assertEquals("Hello, Test!", content.text)

        val argsWithTitle = McpTool.Args(
            buildJsonObject {
                put("name", "Test")
                put("title", "Mr.")
            }
        )
        val resultWithTitle = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(1.minutes) {
                greetingTool.execute(argsWithTitle, TestToolEnabler)
            }
        }

        val contentWithTitle = resultWithTitle.promptMessageContents.first() as TextContent
        assertEquals("Hello, Mr. Test!", contentWithTitle.text)
    }

    @Test
    fun `test McpTool Result toStringDefault with empty content`() {
        val result = McpTool.Result(emptyList())
        assertEquals("[No content]", result.toStringDefault())
    }

    @Test
    fun `test McpTool Result toStringDefault with single TextContent`() {
        val content = TextContent("Hello, World!")
        val result = McpTool.Result(listOf(content))
        assertEquals("Hello, World!", result.toStringDefault())
    }

    @Test
    fun `test McpTool Result toStringDefault with null text in TextContent`() {
        val content = TextContent(null)
        val result = McpTool.Result(listOf(content))
        assertEquals("", result.toStringDefault())
    }

    @Test
    fun `test McpTool Result toStringDefault with multiple TextContent`() {
        val content1 = TextContent("First line")
        val content2 = TextContent("Second line")
        val content3 = TextContent("Third line")
        val result = McpTool.Result(listOf(content1, content2, content3))
        assertEquals("First line\nSecond line\nThird line", result.toStringDefault())
    }

    @Test
    fun `test McpTool Result toStringDefault with mixed content types`() {
        val textContent = TextContent("Some text")
        val imageContent = ImageContent("image/png", "base64data")
        val result = McpTool.Result(listOf(textContent, imageContent))
        val resultString = result.toStringDefault()

        // The string should contain the text content and the toString representation of the image
        assert(resultString.contains("Some text"))
        assert(resultString.contains("ImageContent"))
    }

    @Test
    fun `test McpTool Result toStringDefault with mixed null and non-null text`() {
        val content1 = TextContent("Valid text")
        val content2 = TextContent(null)
        val content3 = TextContent("Another text")
        val result = McpTool.Result(listOf(content1, content2, content3))
        assertEquals("Valid text\n\nAnother text", result.toStringDefault())
    }
}
