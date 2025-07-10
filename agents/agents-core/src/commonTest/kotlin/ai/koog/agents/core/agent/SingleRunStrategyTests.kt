package ai.koog.agents.core.agent

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SingleRunStrategyTests {

    @Test
    fun test_SingleRunStrategy_Single_AssistantMessages() = runTest {
        val actualToolCalls = mutableListOf<String>()

        val testToolRegistry = ToolRegistry {
            tool(CreateTool)
        }

        val mockLLMApi = getMockExecutor {
            mockLLMAnswer("Hello!") onRequestContains "Hello"
            mockLLMAnswer("Tools called!") onRequestContains "created"
            mockLLMAnswer("Task solved!!") onRequestContains "Solve task"
            mockLLMAnswer("I don't know how to answer that.").asDefaultResponse
        }

        val agent = AIAgent(
            mockLLMApi,
            OllamaModels.Meta.LLAMA_3_2,
            toolRegistry = testToolRegistry) {
            install(EventHandler) {
                onToolCall { eventContext -> actualToolCalls += eventContext.toolArgs.toString() }
            }
        }

        val result = agent.run("Solve task")

        assertEquals(0, actualToolCalls.size)
        assertEquals("Task solved!!", result)
    }

    @Test
    fun test_SingleRunStrategy_Single_WithToolCall() = runTest {
        val actualToolCalls = mutableListOf<String>()

        val testToolRegistry = ToolRegistry {
            tool(CreateTool)
        }

        val mockLLMApi = getMockExecutor {
            mockLLMAnswer("Hello!") onRequestContains "Hello"
            mockLLMAnswer("Tools called!") onRequestContains "created"
            mockLLMAnswer("I don't know how to answer that.").asDefaultResponse
            mockLLMToolCall(CreateTool, CreateTool.Args("solve")) onRequestEquals "Solve task"
        }

        val agent = AIAgent(
            mockLLMApi,
            OllamaModels.Meta.LLAMA_3_2,
            toolRegistry = testToolRegistry) {
            install(EventHandler) {
                onToolCall { eventContext -> actualToolCalls += eventContext.toolArgs.toString() }
            }
        }

        val result = agent.run("Solve task")

        assertEquals(1, actualToolCalls.size)
        assertEquals("Tools called!", result)
    }

    @Test
    fun test_SingleRunStrategy_Sequential_AssistantMessages() = runTest {
        val actualToolCalls = mutableListOf<String>()

        val testToolRegistry = ToolRegistry {
            tool(CreateTool)
        }

        val mockLLMApi = getMockExecutor {
            mockLLMAnswer("Task solved!") onRequestContains "Solve task"
            mockLLMAnswer("I don't know how to answer that.").asDefaultResponse
        }

        val agent = AIAgent(
            mockLLMApi,
            OllamaModels.Meta.LLAMA_3_2,
            strategy = singleRunStrategy(ToolCalls.SEQUENTIAL),
            toolRegistry = testToolRegistry) {
            install(EventHandler) {
                onToolCall { eventContext -> actualToolCalls += eventContext.toolArgs.toString() }
            }
        }

        val result = agent.run("Solve task")

        assertEquals(0, actualToolCalls.size)
        assertEquals("Task solved!", result)
    }

    @Test
    fun test_SingleRunStrategy_Parallel_AssistantMessages() = runTest {
        val actualToolCalls = mutableListOf<String>()

        val testToolRegistry = ToolRegistry {
            tool(CreateTool)
        }

        val mockLLMApi = getMockExecutor {
            mockLLMAnswer("Task solved!") onRequestContains "Solve task"
            mockLLMAnswer("I don't know how to answer that.").asDefaultResponse
        }

        val agent = AIAgent(
            mockLLMApi,
            OllamaModels.Meta.LLAMA_3_2,
            strategy = singleRunStrategy(ToolCalls.PARALLEL),
            toolRegistry = testToolRegistry) {
            install(EventHandler) {
                onToolCall { eventContext -> actualToolCalls += eventContext.toolArgs.toString() }
            }
        }

        val result = agent.run("Solve task")

        assertEquals(0, actualToolCalls.size)
        assertEquals("Task solved!", result)
    }

    @Test
    fun test_SingleRunStrategy_Sequential_WithParallelToolCalls() = runTest {
        val actualToolCalls = mutableListOf<String>()

        val testToolRegistry = ToolRegistry {
            tool(CreateTool)
        }

        val mockLLMApi = getMockExecutor {
            mockLLMAnswer("Hello!") onRequestContains "Hello"
            mockLLMAnswer("Tools called!") onRequestContains "created"
            mockLLMAnswer("I don't know how to answer that.").asDefaultResponse

            // Mock LLM tool calls
            val toolCalls = listOf(
                CreateTool to CreateTool.Args("solve"),
                CreateTool to CreateTool.Args("solve2"),
                CreateTool to CreateTool.Args("solve3"),
            )
            mockLLMToolCall(toolCalls) onRequestEquals "Solve task"
        }

        val agent = AIAgent(
            mockLLMApi,
            OllamaModels.Meta.LLAMA_3_2,
            strategy = singleRunStrategy(ToolCalls.SEQUENTIAL),
            toolRegistry = testToolRegistry) {
            install(EventHandler) {
                onToolCall { eventContext -> actualToolCalls += eventContext.toolArgs.toString() }
            }
        }

        val result = agent.run("Solve task")

        assertEquals(3, actualToolCalls.size)
        assertEquals("Tools called!", result)
    }

    @Test
    fun test_SingleRunStrategy_Parallel_WithParallelToolCalls() = runTest {
        val actualToolCalls = mutableListOf<String>()

        val testToolRegistry = ToolRegistry {
            tool(CreateTool)
        }

        val mockLLMApi = getMockExecutor {
            mockLLMAnswer("Hello!") onRequestContains "Hello"
            mockLLMAnswer("Tools called!") onRequestContains "created"
            mockLLMAnswer("I don't know how to answer that.").asDefaultResponse

            // Mock LLM tool calls
            val toolCalls = listOf(
                CreateTool to CreateTool.Args("solve"),
                CreateTool to CreateTool.Args("solve2"),
                CreateTool to CreateTool.Args("solve3"),
            )
            mockLLMToolCall(toolCalls) onRequestEquals "Solve task"
        }

        val agent = AIAgent(
            mockLLMApi,
            OllamaModels.Meta.LLAMA_3_2,
            strategy = singleRunStrategy(ToolCalls.PARALLEL),
            toolRegistry = testToolRegistry) {
            install(EventHandler) {
                onToolCall { eventContext -> actualToolCalls += eventContext.toolArgs.toString() }
            }
        }

        val result = agent.run("Solve task")

        assertEquals(3, actualToolCalls.size)
        assertEquals("Tools called!", result)
    }

    @Test
    fun test_SingleRunStrategy_Sequential_MixedResults() = runTest {
        val actualToolCalls = mutableListOf<String>()

        val testToolRegistry = ToolRegistry {
            tool(CreateTool)
        }

        val assistantResponse = "Hey, I want to call following tools:"
        val mockLLMApi = getMockExecutor(handleLastAssistantMessage = true) {
            mockLLMAnswer(assistantResponse) onRequestContains assistantResponse
            mockLLMAnswer("I don't know how to answer that.").asDefaultResponse

            // Mock LLM tool calls
            val toolCalls = listOf(
                CreateTool to CreateTool.Args("solve"),
                CreateTool to CreateTool.Args("solve2"),
                CreateTool to CreateTool.Args("solve3"),
            )
            val assistantResponses = listOf(assistantResponse)
            mockLLMMixedResponse(toolCalls, assistantResponses) onRequestEquals "Solve task"
        }

        val agent = AIAgent(
            mockLLMApi,
            OllamaModels.Meta.LLAMA_3_2,
            strategy = singleRunStrategy(ToolCalls.SEQUENTIAL),
            toolRegistry = testToolRegistry) {
            install(EventHandler) {
                onToolCall { eventContext -> actualToolCalls += eventContext.toolArgs.toString() }
            }
        }

        val result = agent.run("Solve task")

        assertEquals(3, actualToolCalls.size)
        assertEquals(assistantResponse, result)
    }

    @Test
    fun test_SingleRunStrategy_Parallel_MixedResults() = runTest {
        val actualToolCalls = mutableListOf<String>()

        val testToolRegistry = ToolRegistry {
            tool(CreateTool)
        }

        val assistantResponse = "Hey, I want to call following tools:"
        val mockLLMApi = getMockExecutor(handleLastAssistantMessage = true) {
            mockLLMAnswer(assistantResponse) onRequestContains assistantResponse
            mockLLMAnswer("I don't know how to answer that.").asDefaultResponse

            // Mock LLM tool calls
            val toolCalls = listOf(
                CreateTool to CreateTool.Args("solve"),
                CreateTool to CreateTool.Args("solve2"),
                CreateTool to CreateTool.Args("solve3"),
            )
            val assistantResponses = listOf(assistantResponse)
            mockLLMMixedResponse(toolCalls, assistantResponses) onRequestEquals "Solve task"
        }

        val agent = AIAgent(
            mockLLMApi,
            OllamaModels.Meta.LLAMA_3_2,
            strategy = singleRunStrategy(ToolCalls.PARALLEL),
            toolRegistry = testToolRegistry) {
            install(EventHandler) {
                onToolCall { eventContext -> actualToolCalls += eventContext.toolArgs.toString() }
            }
        }

        val result = agent.run("Solve task")

        assertEquals(3, actualToolCalls.size)
        assertEquals(assistantResponse, result)
    }

}