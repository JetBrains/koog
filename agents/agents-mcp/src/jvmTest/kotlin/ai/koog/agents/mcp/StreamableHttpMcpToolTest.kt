package ai.koog.agents.mcp

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.kotlinx.toKoogJSONObject
import io.kotest.assertions.json.shouldEqualJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(InternalAgentsApi::class)
class StreamableHttpMcpToolTest {
    companion object {
        private val testServer = TestMcpServer(transportMode = TestTransportMode.StreamableHttp)
        private lateinit var httpClient: HttpClient

        @BeforeAll
        @JvmStatic
        fun setup() {
            testServer.start()
            httpClient = HttpClient { install(SSE) }
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            httpClient.close()
            testServer.stop()
        }
    }

    private val serializer = KotlinxSerializer()

    private suspend fun testMcpTools(action: suspend (toolRegistry: ToolRegistry) -> Unit) {
        val toolRegistry = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(1.minutes) {
                McpToolRegistryProvider.streamableHttp {
                    url = "http://localhost:${testServer.resolvedPort}/mcp"
                    httpClient = StreamableHttpMcpToolTest.httpClient
                }
            }
        }

        action(toolRegistry)
    }

    @Test
    fun `test McpToolRegistry provides all tools via Streamable HTTP`() = runTest(timeout = 30.seconds) {
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
                            description = "Title to use in the greeting",
                            type = ToolParameterType.AnyOf(
                                types = arrayOf(
                                    ToolParameterDescriptor(type = ToolParameterType.Null, name = "", description = ""),
                                    ToolParameterDescriptor(type = ToolParameterType.String, name = "", description = "")
                                )
                            )
                        )
                    )
                ),
                ToolDescriptor(
                    name = "empty",
                    description = "An empty tool",
                )
            )

            val actualToolDescriptor = toolRegistry.tools.map { it.descriptor }
            assertEquals(expectedToolDescriptors, actualToolDescriptor)
        }
    }

    @OptIn(InternalAgentToolsApi::class)
    @Test
    fun `test greeting tool via Streamable HTTP`() = runTest(timeout = 30.seconds) {
        testMcpTools { toolRegistry ->
            val greetingTool = toolRegistry.getTool("greeting") as McpTool
            val args = buildJsonObject { put("name", "Test") }

            val result = withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(1.minutes) {
                    greetingTool.execute(args.toKoogJSONObject())
                }
            }

            val content = result.content.single() as TextContent
            assertEquals("Hello, Test!", content.text)

            val encodedResult = greetingTool.encodeResultToString(result, serializer)
            encodedResult shouldEqualJson """{"content":[{"text":"Hello, Test!","type":"text"}]}"""
        }
    }

    @Test
    fun `test empty tool via Streamable HTTP`() = runTest(timeout = 30.seconds) {
        testMcpTools { toolRegistry ->
            val emptyTool = toolRegistry.getTool("empty") as McpTool
            val args = EmptyJsonObject

            val result = withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(1.minutes) {
                    emptyTool.execute(args.toKoogJSONObject())
                }
            }
            assertEquals(emptyList(), result.content)
        }
    }
}
