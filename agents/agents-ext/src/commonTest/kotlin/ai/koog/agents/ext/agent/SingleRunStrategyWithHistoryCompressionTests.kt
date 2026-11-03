package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.typeToken
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object CreateTool : SimpleTool<CreateTool.Args>(
    argsType = typeToken<Args>(),
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
    private val serializer = KotlinxSerializer()

    @Test
    fun test_compression_happens() = runTest {
        var compressionRequested = false

        val config = HistoryCompressionConfig(
            isHistoryTooBig = { it.messages.size > 2 },
            compressionStrategy = HistoryCompressionStrategy.WholeHistory
        )

        val mockLLMApi = getMockExecutor(serializer) {
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

        val mockLLMApi = getMockExecutor(serializer) {
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
    fun test_no_compression_post_tool_mixed_response_executes_next_tool_call() = runTest {
        val actualToolCalls = mutableListOf<String>()
        val config = HistoryCompressionConfig(
            isHistoryTooBig = { it.messages.size > 100 },
            compressionStrategy = HistoryCompressionStrategy.WholeHistory
        )

        val intermediateResponse = "Need another tool."
        val finalResponse = "Task solved after second tool."
        var postFirstToolResultHandled = false

        val mockLLMApi = getMockExecutor(serializer) {
            mockLLMToolCall(CreateTool, CreateTool.Args("first")) onRequestEquals "Solve task"
            mockLLMMixedResponse(
                toolCalls = listOf(CreateTool to CreateTool.Args("second")),
                responses = listOf(intermediateResponse)
            ) onCondition { request ->
                if (request == "created" && !postFirstToolResultHandled) {
                    postFirstToolResultHandled = true
                    true
                } else {
                    false
                }
            }
            mockLLMAnswer(finalResponse) onCondition { request ->
                request == "created" && postFirstToolResultHandled
            }
            mockLLMAnswer("I don't know how to answer that.").asDefaultResponse
        }

        val agent = AIAgent(
            mockLLMApi,
            OllamaModels.Meta.LLAMA_3_2,
            strategy = singleRunStrategyWithHistoryCompression(config),
            toolRegistry = ToolRegistry { tool(CreateTool) }
        ) {
            install(EventHandler) {
                onToolCallStarting { eventContext -> actualToolCalls += eventContext.toolArgs.toString() }
            }
        }

        val result = agent.run("Solve task", null)

        assertEquals(listOf("""{"name":"first"}""", """{"name":"second"}"""), actualToolCalls)
        assertEquals(finalResponse, result)
    }

    @Test
    fun test_sequential_mode() = runTest {
        var compressionRequested = false

        val config = HistoryCompressionConfig(
            isHistoryTooBig = { true },
            compressionStrategy = HistoryCompressionStrategy.WholeHistory
        )

        val mockLLMApi = getMockExecutor(serializer) {
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
            strategy = singleRunStrategyWithHistoryCompression(config),
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

        val mockLLMApi = getMockExecutor(serializer) {
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
            strategy = singleRunStrategyWithHistoryCompression(config, true),
            toolRegistry = ToolRegistry { tool(CreateTool) }
        )

        agent.run("Solve task", null)
        assertTrue(compressionRequested)
    }

    @Test
    fun test_compressed_history_mixed_response_executes_next_tool_call() = runTest {
        val actualToolCalls = mutableListOf<String>()
        val compressedHistoryResponse = "TLDR summary."
        val intermediateResponse = "Need another tool after compression."
        val finalResponse = "Task solved after compressed history tool."
        var compressionFinished = false
        var compressedHistoryResponseHandled = false

        val config = HistoryCompressionConfig(
            isHistoryTooBig = { !compressionFinished },
            compressionStrategy = HistoryCompressionStrategy.WholeHistory
        )

        val mockLLMApi = getMockExecutor(serializer) {
            mockLLMToolCall(CreateTool, CreateTool.Args("first")) onRequestEquals "Solve task"
            mockLLMAnswer(compressedHistoryResponse) onCondition { request ->
                if (request.contains("comprehensive summary")) {
                    compressionFinished = true
                    true
                } else {
                    false
                }
            }
            mockLLMMixedResponse(
                toolCalls = listOf(CreateTool to CreateTool.Args("second")),
                responses = listOf(intermediateResponse)
            ) onCondition { request ->
                if (request.isEmpty() && compressionFinished && !compressedHistoryResponseHandled) {
                    compressedHistoryResponseHandled = true
                    true
                } else {
                    false
                }
            }
            mockLLMAnswer(finalResponse) onCondition { request ->
                request == "created" && compressedHistoryResponseHandled
            }
            mockLLMAnswer("I don't know how to answer that.").asDefaultResponse
        }

        val agent = AIAgent(
            mockLLMApi,
            OllamaModels.Meta.LLAMA_3_2,
            strategy = singleRunStrategyWithHistoryCompression(config),
            toolRegistry = ToolRegistry { tool(CreateTool) }
        ) {
            install(EventHandler) {
                onToolCallStarting { eventContext -> actualToolCalls += eventContext.toolArgs.toString() }
            }
        }

        val result = agent.run("Solve task", null)

        assertEquals(listOf("""{"name":"first"}""", """{"name":"second"}"""), actualToolCalls)
        assertEquals(finalResponse, result)
    }
}
