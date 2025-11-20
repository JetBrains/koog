package ai.koog.agents.mcp

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Execution(ExecutionMode.SAME_THREAD)
class McpToolTest {
    companion object {
        private const val testPort = 3001
        private val testServer = TestMcpServer(testPort)

        @BeforeAll
        @JvmStatic
        fun setup() {
            testServer.start()
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            testServer.stop()
        }
    }

    private suspend fun testMcpTools(action: suspend (toolRegistry: ToolRegistry) -> Unit) {
        val toolRegistry = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(1.minutes) {
                McpToolRegistryProvider.fromTransport(
                    transport = McpToolRegistryProvider.defaultSseTransport("http://localhost:$testPort"),
                    name = "test-client",
                    version = "0.1.0"
                )
            }
        }

        action(toolRegistry)
    }

    @Test
    fun `test McpToolRegistry provides all tools`() = runTest(timeout = 30.seconds) {
        testMcpTools { toolRegistry ->
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
                ),
                ToolDescriptor(
                    name = "empty",
                    description = "An empty tool",
                )
            )

            // Actual list of tools provided
            val actualToolDescriptor = toolRegistry.tools.map { it.descriptor }
            assertEquals(expectedToolDescriptors, actualToolDescriptor)
        }
    }

    @OptIn(InternalAgentToolsApi::class)
    @Test
    fun `test greeting tool`() = runTest(timeout = 30.seconds) {
        testMcpTools { toolRegistry ->
            val greetingTool = toolRegistry.getTool("greeting") as McpTool
            val args = buildJsonObject { put("name", "Test") }

            val result = withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(1.minutes) {
                    greetingTool.execute(args)
                }
            }

            val content = result?.content?.first() as TextContent
            assertEquals("Hello, Test!", content.text)

            val argsWithTitle = buildJsonObject {
                put("name", "Test")
                put("title", "Mr.")
            }
            val resultWithTitle = withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(1.minutes) {
                    greetingTool.execute(argsWithTitle)
                }
            }

            val contentWithTitle = resultWithTitle?.content?.first() as TextContent
            assertEquals("Hello, Mr. Test!", contentWithTitle.text)

            val encodedResult = greetingTool.encodeResultToString(result)
            assertEquals(
                //language=Json
                expected = """{"content":[{"text":"Hello, Test!","type":"text"}],"isError":false}""",
                actual = encodedResult
            )
        }
    }

    @Test
    fun `test empty tool`() = runTest(timeout = 30.seconds) {
        testMcpTools { toolRegistry ->
            val emptyTool = toolRegistry.getTool("empty") as McpTool
            val args = EmptyJsonObject

            val result = withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(1.minutes) {
                    emptyTool.execute(args)
                }
            }
            assertEquals(emptyList(), result?.content.orEmpty())

            val encodedResult = emptyTool.encodeResultToString(result)
            assertEquals(
                //language=Json
                expected = """{"content":[],"isError":false}""",
                actual = encodedResult
            )
        }
    }

    @Test
    fun `test encode result`() {
        val result = CallToolResult(listOf(TextContent("Hello world")))
        val toolDescriptor = ToolDescriptor(
            name = "test-tool",
            description = "A test tool",
            requiredParameters = emptyList(),
            optionalParameters = emptyList()
        )
        val mcpTool = McpTool(
            mcpClient = Client(clientInfo = Implementation(name = "Test", version = "1.0")),
            descriptor = toolDescriptor,
        )
        val encodedResult = mcpTool.encodeResultToString(result)

        assertEquals(
            //language=Json
            expected = """{"content":[{"text":"Hello world","type":"text"}],"isError":false}""",
            actual = encodedResult
        )
    }

    @Test
    fun `test encode null result`() {
        val result: CallToolResult? = null
        val toolDescriptor = ToolDescriptor(
            name = "test-tool",
            description = "A test tool",
            requiredParameters = emptyList(),
            optionalParameters = emptyList()
        )
        val mcpTool = McpTool(
            mcpClient = Client(clientInfo = Implementation(name = "Test", version = "1.0")),
            descriptor = toolDescriptor,
        )
        val encodedResult = mcpTool.encodeResultToString(result)

        assertEquals(
            expected = "null",
            actual = encodedResult
        )
    }
}
