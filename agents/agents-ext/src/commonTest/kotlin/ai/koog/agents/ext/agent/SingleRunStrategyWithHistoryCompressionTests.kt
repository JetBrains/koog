package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object CreateTool : SimpleTool<CreateTool.Args>(
    argsSerializer = Args.serializer(),
    name = "create",
    description = "Create something"
) {
    @Serializable
    data class Args(
        @property:LLMDescription("Name of the entity to create") val name: String
    )

    override suspend fun execute(args: Args): String = "created"
}

class SingleRunStrategyWithHistoryCompressionTests {

    @Test
    fun test_compression_happens() = runTest {
        var compressionRequested = false

        val config = HistoryCompressionConfig(
            isHistoryTooBig = { it.messages.size > 2 },
            compressionStrategy = HistoryCompressionStrategy.WholeHistory
        )

        val mockLLMApi = getMockExecutor {
            mockLLMToolCall(CreateTool, CreateTool.Args("solve")) onRequestEquals "Solve task"
            mockLLMAnswer("TLDR summary.") onRequestContains "comprehensive summary" onCondition {
                compressionRequested = true
                true
            }
            mockLLMAnswer("I don't know how to answer that.").asDefaultResponse
        }

        val agent = AIAgent(
            mockLLMApi,
            OllamaModels.Meta.LLAMA_3_2,
            strategy = singleRunStrategyWithHistoryCompression(config),
            toolRegistry = ToolRegistry { tool(CreateTool) }
        )

        agent.run("Solve task", null)
        assertTrue(compressionRequested)
    }

    @Test
    fun test_no_compression_when_not_needed() = runTest {
        val config = HistoryCompressionConfig(
            isHistoryTooBig = { it.messages.size > 100 },
            compressionStrategy = HistoryCompressionStrategy.WholeHistory
        )

        val mockLLMApi = getMockExecutor {
            mockLLMToolCall(CreateTool, CreateTool.Args("solve")) onRequestEquals "Solve task"
            mockLLMAnswer("Tools called!") onRequestContains "created"
            mockLLMAnswer("I don't know how to answer that.").asDefaultResponse
        }

        val agent = AIAgent(
            mockLLMApi,
            OllamaModels.Meta.LLAMA_3_2,
            strategy = singleRunStrategyWithHistoryCompression(config),
            toolRegistry = ToolRegistry { tool(CreateTool) }
        )

        val result = agent.run("Solve task", null)
        assertEquals("Tools called!", result)
    }

    @Test
    fun test_sequential_mode() = runTest {
        var compressionRequested = false

        val config = HistoryCompressionConfig(
            isHistoryTooBig = { true },
            compressionStrategy = HistoryCompressionStrategy.WholeHistory
        )

        val mockLLMApi = getMockExecutor {
            mockLLMToolCall(listOf(CreateTool to CreateTool.Args("1"), CreateTool to CreateTool.Args("2"))) onRequestEquals "Solve task"
            mockLLMAnswer("TLDR") onRequestContains "comprehensive summary" onCondition {
                compressionRequested = true
                true
            }
            mockLLMAnswer("I don't know how to answer that.").asDefaultResponse
        }

        val agent = AIAgent(
            mockLLMApi,
            OllamaModels.Meta.LLAMA_3_2,
            strategy = singleRunStrategyWithHistoryCompression(config, ToolCalls.SEQUENTIAL),
            toolRegistry = ToolRegistry { tool(CreateTool) }
        )

        agent.run("Solve task", null)
        assertTrue(compressionRequested)
    }

    @Test
    fun test_parallel_mode() = runTest {
        var compressionRequested = false

        val config = HistoryCompressionConfig(
            isHistoryTooBig = { true },
            compressionStrategy = HistoryCompressionStrategy.WholeHistory
        )

        val mockLLMApi = getMockExecutor {
            mockLLMToolCall(listOf(CreateTool to CreateTool.Args("1"), CreateTool to CreateTool.Args("2"))) onRequestEquals "Solve task"
            mockLLMAnswer("TLDR") onRequestContains "comprehensive summary" onCondition {
                compressionRequested = true
                true
            }
            mockLLMAnswer("I don't know how to answer that.").asDefaultResponse
        }

        val agent = AIAgent(
            mockLLMApi,
            OllamaModels.Meta.LLAMA_3_2,
            strategy = singleRunStrategyWithHistoryCompression(config, ToolCalls.PARALLEL),
            toolRegistry = ToolRegistry { tool(CreateTool) }
        )

        agent.run("Solve task", null)
        assertTrue(compressionRequested)
    }
}
