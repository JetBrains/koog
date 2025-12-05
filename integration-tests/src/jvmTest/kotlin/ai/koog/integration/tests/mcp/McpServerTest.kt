package ai.koog.integration.tests.mcp

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
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
import ai.koog.prompt.llm.LLModel
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.matchers.types.shouldNotBeTypeOf
import io.ktor.server.netty.Netty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class McpServerTest {

    companion object {
        @JvmStatic
        fun getModels() = listOf(
            OpenAIModels.Chat.GPT4o,
            // ToDo enable when fixed: KG-588 singleRunStrategy outputs empty response when using an MCP server
            // GoogleModels.Gemini2_5FlashLite
        )
    }

    @ParameterizedTest
    @MethodSource("getModels")
    fun integration_testMcpServerWithSSETransport(model: LLModel) = runTest(timeout = 1.minutes) {
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
                        transport = McpToolRegistryProvider.defaultSseTransport("http://localhost:$port")
                    )
                }
            }

            toolRegistry.tools.map { it.descriptor }.shouldContainExactly(randomNumberTool.descriptor)
            toolRegistry.tools.forEach { it.shouldBeTypeOf<McpTool>() }

            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(40.seconds) {
                    AIAgent(
                        promptExecutor = SingleLLMPromptExecutor(getLLMClientForProvider(model.provider)),
                        strategy = singleRunStrategy(),
                        llmModel = model,
                        toolRegistry = toolRegistry,
                    ).run("Provide random number using ${randomNumberTool.name}, YOU MUST USE TOOLS!")
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
