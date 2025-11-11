package ai.koog.integration.tests.mcp

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpTool
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.server.startSseMcpServer
import ai.koog.agents.testing.network.NetUtil.isPortAvailable
import ai.koog.agents.testing.tools.RandomNumberTool
import ai.koog.integration.tests.utils.RetryUtils
import ai.koog.integration.tests.utils.getLLMClientForProvider
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldNotBeTypeOf
import io.ktor.server.netty.Netty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class McpServerTest {

    @Test
    fun integration_testMcpServerWithSSETransport() = runTest(timeout = 1.minutes) {
        val randomNumberTool = RandomNumberTool()
        randomNumberTool.shouldNotBeTypeOf<McpTool>()

        val (server, connectors) = startSseMcpServer(
            factory = Netty,
            tools = ToolRegistry.Companion {
                tool(randomNumberTool)
            },
        )

        val port = connectors.firstOrNull()?.port ?: 0
        port shouldNotBe 0

        try {
            val toolRegistry = withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(20.seconds) {
                    McpToolRegistryProvider.fromTransport(
                        transport = McpToolRegistryProvider.defaultSseTransport("http://localhost:$port/sse")
                    )
                }
            }

            toolRegistry.tools.map { it.descriptor }.shouldContainExactly(randomNumberTool.descriptor)

            val model = OpenAIModels.Chat.GPT4o
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(40.seconds) {
                    AIAgent(
                        promptExecutor = SingleLLMPromptExecutor(getLLMClientForProvider(model.provider)),
                        llmModel = model,
                        toolRegistry = toolRegistry,
                    ).run("Provide random number using ${randomNumberTool.name}")
                }
            }.replace("[\\s,_]+".toRegex(), "").shouldContain(randomNumberTool.last.toString())
        } finally {
            server.close()

            withContext(Dispatchers.Default.limitedParallelism(1)) {
                RetryUtils.withRetry {
                    isPortAvailable(port).shouldBeTrue()
                }
            }
        }
    }
}
