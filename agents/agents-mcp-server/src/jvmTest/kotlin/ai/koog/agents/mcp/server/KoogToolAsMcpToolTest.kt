package ai.koog.agents.mcp.server

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.mcp.McpTool
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.testing.network.NetUtil.isPortAvailable
import ai.koog.agents.testing.tools.RandomNumberTool
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.cio.CIO
import io.modelcontextprotocol.kotlin.sdk.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIsNot
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class KoogToolAsMcpToolTest {

    private val logger = KotlinLogging.logger {}

    @OptIn(InternalAgentToolsApi::class)
    @Test
    fun testKoogToolAsMcpTool() = testMcpTool(RandomNumberTool()) { mcpTool, origin ->
        val args = buildJsonObject { put("seed", "42") }

        val result = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(20.seconds) {
                mcpTool.execute(args)
            }
        }

        logger.info { "Result: ${mcpTool.encodeResultToString(result)}" }

        val content = result?.content?.first() as TextContent
        assertEquals("${origin.last}", content.text)
    }

    @OptIn(InternalAgentToolsApi::class)
    @Test
    fun testKoogToolAsMcpToolWithoutOptionalArguments() = testMcpTool(RandomNumberTool()) { mcpTool, origin ->
        val args = EmptyJsonObject

        val result = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(20.seconds) {
                mcpTool.execute(args)
            }
        }

        logger.info { "Result: ${mcpTool.encodeResultToString(result)}" }

        val content = result?.content?.first() as TextContent
        assertEquals("${origin.last}", content.text)
    }

    @OptIn(InternalAgentToolsApi::class)
    @Test
    fun testKoogToolAsMcpToolWithInvalidArguments() = testMcpTool(RandomNumberTool()) { mcpTool, origin ->
        run {
            val errorArgs = buildJsonObject { put("seed", "forty-two") }

            val errorResult = withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(20.seconds) {
                    mcpTool.execute(errorArgs)
                }
            }

            assertTrue(errorResult?.isError ?: false)
        }

        // check that the server is still working
        run {
            val args = buildJsonObject { put("seed", "42") }

            val result = withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(20.seconds) {
                    mcpTool.execute(args)
                }
            }

            logger.info { "Result: ${mcpTool.encodeResultToString(result)}" }

            val content = result?.content?.first() as TextContent
            assertEquals("${origin.last}", content.text)
        }
    }

    @OptIn(InternalAgentToolsApi::class)
    @Test
    fun testKoogToolThrowingAnExceptionAsMcpTool() {
        val tool = ThrowingExceptionTool()

        testMcpTool(tool) { mcpTool, origin ->
            run {
                tool.throwing = true

                val args = EmptyJsonObject

                val errorResult = withContext(Dispatchers.Default.limitedParallelism(1)) {
                    withTimeout(20.seconds) {
                        mcpTool.execute(args)
                    }
                }

                assertTrue(errorResult?.isError ?: false)

                val last = origin.last
                assertNotNull(last)
                assertTrue(last.isFailure)
            }

            run {
                // check that the server is still working
                tool.throwing = false

                val args = EmptyJsonObject

                val result = withContext(Dispatchers.Default.limitedParallelism(1)) {
                    withTimeout(20.seconds) {
                        mcpTool.execute(args)
                    }
                }

                logger.info { "Result: ${mcpTool.encodeResultToString(result)}" }

                val content = result?.content?.first() as TextContent
                assertEquals("${origin.last?.getOrNull()}", content.text)
            }
        }
    }

    private fun <T : Tool<*, *>> testMcpTool(
        tool: T,
        block: suspend (McpTool, T) -> Unit,
    ) = runTest(timeout = 30.seconds) {
        assertIsNot<McpTool>(tool)

        val (server, connectors) = startSseMcpServer(
            factory = CIO,
            tools = ToolRegistry {
                tool(tool)
            },
        )

        val port = connectors.firstOrNull()?.port ?: 0
        assertNotEquals(0, port, "Port should not be 0")

        try {
            val toolRegistry = withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(20.seconds) {
                    McpToolRegistryProvider.fromTransport(
                        transport = McpToolRegistryProvider.defaultSseTransport("http://localhost:$port")
                    )
                }
            }

            assertEquals(
                listOf(tool.descriptor),
                toolRegistry.tools.map { it.descriptor },
            )

            val mcpTool = toolRegistry.getTool(tool.name) as McpTool
            block(mcpTool, tool)
        } finally {
            server.close()

            withContext(Dispatchers.Default.limitedParallelism(1)) {
                var result = Result.success(Unit)

                for (attempt in 1..3) {
                    result = runCatching {
                        assertTrue(isPortAvailable(port), "Port $port should be available")
                    }

                    if (result.isSuccess) {
                        break
                    } else {
                        delay(1.seconds)
                    }
                }

                result.getOrThrow()
            }
        }
    }
}
