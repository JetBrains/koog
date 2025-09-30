package ai.koog.integration.tests.mcp

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpTool
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.server.startSseMcpServer
import ai.koog.agents.testing.network.NetUtil.isPortAvailable
import ai.koog.agents.testing.tools.RandomNumberTool
import ai.koog.integration.tests.utils.RetryUtils
import ai.koog.integration.tests.utils.TestUtils
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.netty.Netty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIsNot
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class McpServerTest {

    private val logger = KotlinLogging.logger {}

    @Test
    fun integration_testMcpServerWithSSETransport() = runTest(timeout = 1.minutes) {
        val openAIApiToken = TestUtils.readTestOpenAIKeyFromEnv()

        val randomNumberTool = RandomNumberTool()
        assertIsNot<McpTool>(randomNumberTool)

        val (server, connectors) = startSseMcpServer(
            factory = Netty,
            tools = ToolRegistry.Companion {
                tool(randomNumberTool)
            },
        )

        val port = connectors.firstOrNull()?.port ?: 0
        assertNotEquals(0, port, "Port should not be 0")

        try {
            val toolRegistry = withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(20.seconds) {
                    McpToolRegistryProvider.fromTransport(
                        transport = McpToolRegistryProvider.defaultSseTransport("http://localhost:$port/sse")
                    )
                }
            }

            assertEquals(
                listOf(randomNumberTool.descriptor),
                toolRegistry.tools.map { it.descriptor },
            )

            val result = withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(40.seconds) {
                    AIAgent(
                        promptExecutor = simpleOpenAIExecutor(openAIApiToken),
                        llmModel = OpenAIModels.Chat.GPT4o,
                        toolRegistry = toolRegistry,
                    ).run("Provide random number using ${randomNumberTool.name}")
                }
            }

            logger.info { "Result: $result" }

            assertContains(
                result.replace("[\\s,_]+".toRegex(), ""),
                randomNumberTool.last.toString(),
            )
        } finally {
            server.close()

            withContext(Dispatchers.Default.limitedParallelism(1)) {
                RetryUtils.withRetry {
                    assertTrue(isPortAvailable(port), "Port $port should be available")
                }
            }
        }
    }
}
