package ai.koog.agents.core.agent

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
class ActAgentTests {
    @Test
    fun test_ActAgent_MixedResults() = runTest {
        val actualToolCalls = mutableListOf<String>()

        val testToolRegistry = ToolRegistry {
            tool(CreateTool)
        }

        val assistantResponse = "Hey, I want to call following tools:"
        val mockLLMApi = getMockExecutor(handleLastAssistantMessage = true) {
            mockLLMAnswer(assistantResponse) onRequestContains assistantResponse
            mockLLMAnswer("I don't know how to answer that.").asDefaultResponse

            val toolCalls = listOf(
                CreateTool to CreateTool.Args("solve"),
                CreateTool to CreateTool.Args("solve2"),
                CreateTool to CreateTool.Args("solve3"),
            )
            val assistantResponses = listOf(assistantResponse)
            mockLLMMixedResponse(toolCalls, assistantResponses) onRequestEquals "Solve task"
        }

        val agent = functionalAIAgent<String, String>(
            prompt = "You are helpful",
            promptExecutor = mockLLMApi,
            model = OllamaModels.Meta.LLAMA_3_2,
            toolRegistry = testToolRegistry,
            featureContext = {
                install(EventHandler) {
                    onToolCall { eventContext -> actualToolCalls += eventContext.toolArgs.toString() }
                }
            }
        ) { inputParam ->
            var responses = requestLLMMultiple(inputParam)

            while (responses.containsToolCalls()) {
                val tools = extractToolCalls(responses)
                val results = executeMultipleTools(tools)
                responses = sendMultipleToolResults(results)
            }

            responses.single().asAssistantMessage().content
        }

        val result = agent.run("Solve task")

        assertEquals(3, actualToolCalls.size)
        assertEquals(assistantResponse, result)
    }

    @Test
    fun test_ActAgent_Single_AssistantMessages() = runTest {
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

        // install feature using featureContext overload builder
        val agent = functionalAIAgent<String, String>(
            prompt = "You are helpful",
            promptExecutor = mockLLMApi,
            model = OllamaModels.Meta.LLAMA_3_2,
            toolRegistry = testToolRegistry,
            featureContext = {
                install(EventHandler) {
                    onToolCall { eventContext -> actualToolCalls += eventContext.toolArgs.toString() }
                }
            }
        ) { inputParam ->
            val resp = llm.writeSession {
                updatePrompt { user(inputParam) }
                requestLLM()
            }
            resp.content
        }

        val result = agent.run("Solve task")

        assertEquals(0, actualToolCalls.size)
        assertEquals("Task solved!!", result)
    }

    @Test
    fun test_ActAgent_Single_WithToolCall() = runTest {
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

        val agent = functionalAIAgent<String, String>(
            prompt = "You are helpful",
            promptExecutor = mockLLMApi,
            model = OllamaModels.Meta.LLAMA_3_2,
            toolRegistry = testToolRegistry,
            featureContext = {
                install(EventHandler) {
                    onToolCall { eventContext -> actualToolCalls += eventContext.toolArgs.toString() }
                }
            }
        ) { inputParam ->
            var responses = requestLLMMultiple(inputParam)

            while (responses.containsToolCalls()) {
                val tools = extractToolCalls(responses)
                val results = executeMultipleTools(tools)
                responses = sendMultipleToolResults(results)
            }

            responses.single().asAssistantMessage().content
        }

        val result = agent.run("Solve task")

        assertEquals(1, actualToolCalls.size)
        assertEquals("Tools called!", result)
    }
}
