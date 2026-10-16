package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.typeToken
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

object SubmitTool : SimpleTool<SubmitTool.Args>(
    argsType = typeToken<Args>(),
    name = "submit",
    description = "Submit the final answer"
) {
    @Serializable
    data class Args(
        @property:LLMDescription("Answer to submit") val answer: String
    )

    override suspend fun execute(args: Args): String = "submitted: ${args.answer}"
}

object FailingSubmitTool : SimpleTool<FailingSubmitTool.Args>(
    argsType = typeToken<Args>(),
    name = "submit_failing",
    description = "Submit the final answer"
) {
    @Serializable
    data class Args(
        @property:LLMDescription("Answer to submit") val answer: String
    )

    override suspend fun execute(args: Args): String = error("submission rejected")
}

class SingleRunStrategyWithTerminalToolsTests {
    private val serializer = KotlinxSerializer()

    @Test
    fun test_terminal_tool_finishes_run_without_further_llm_call() = runTest {
        // "Restating the answer." is only reachable if the tool result is sent back to the LLM
        val mockLLMApi = getMockExecutor(serializer) {
            mockLLMToolCall(SubmitTool, SubmitTool.Args("42")) onRequestEquals "Solve task"
            mockLLMAnswer("Restating the answer.") onRequestContains "submitted"
            mockLLMAnswer("I don't know how to answer that.").asDefaultResponse
        }

        val agent = AIAgent(
            mockLLMApi,
            OllamaModels.Meta.LLAMA_3_2,
            strategy = singleRunStrategyWithTerminalTools(
                TerminalToolsConfig(toolNames = setOf(SubmitTool.name))
            ),
            toolRegistry = ToolRegistry { tool(SubmitTool) }
        )

        val result = agent.run("Solve task", null)

        assertEquals("submitted: 42", result)
    }

    @Test
    fun test_agent_result_maps_terminal_tool_output() = runTest {
        val mockLLMApi = getMockExecutor(serializer) {
            mockLLMToolCall(SubmitTool, SubmitTool.Args("42")) onRequestEquals "Solve task"
            mockLLMAnswer("I don't know how to answer that.").asDefaultResponse
        }

        val agent = AIAgent(
            mockLLMApi,
            OllamaModels.Meta.LLAMA_3_2,
            strategy = singleRunStrategyWithTerminalTools(
                TerminalToolsConfig(
                    toolNames = setOf(SubmitTool.name),
                    agentResult = { "done (${it.tool})" }
                )
            ),
            toolRegistry = ToolRegistry { tool(SubmitTool) }
        )

        val result = agent.run("Solve task", null)

        assertEquals("done (submit)", result)
    }

    @Test
    fun test_non_terminal_tool_result_goes_back_to_llm() = runTest {
        val mockLLMApi = getMockExecutor(serializer) {
            mockLLMToolCall(CreateTool, CreateTool.Args("solve")) onRequestEquals "Solve task"
            mockLLMAnswer("Tools called!") onRequestContains "created"
            mockLLMAnswer("I don't know how to answer that.").asDefaultResponse
        }

        val agent = AIAgent(
            mockLLMApi,
            OllamaModels.Meta.LLAMA_3_2,
            strategy = singleRunStrategyWithTerminalTools(
                TerminalToolsConfig(toolNames = setOf(SubmitTool.name))
            ),
            toolRegistry = ToolRegistry { tool(CreateTool) }
        )

        val result = agent.run("Solve task", null)

        assertEquals("Tools called!", result)
    }

    @Test
    fun test_failed_terminal_tool_does_not_finish_run() = runTest {
        // reaching "Recovered after failure." proves the failure was forwarded to the LLM
        val mockLLMApi = getMockExecutor(serializer) {
            mockLLMToolCall(FailingSubmitTool, FailingSubmitTool.Args("42")) onRequestEquals "Solve task"
            mockLLMAnswer("Recovered after failure.") onRequestContains "submission rejected"
            mockLLMAnswer("I don't know how to answer that.").asDefaultResponse
        }

        val agent = AIAgent(
            mockLLMApi,
            OllamaModels.Meta.LLAMA_3_2,
            strategy = singleRunStrategyWithTerminalTools(
                TerminalToolsConfig(toolNames = setOf(FailingSubmitTool.name))
            ),
            toolRegistry = ToolRegistry { tool(FailingSubmitTool) }
        )

        val result = agent.run("Solve task", null)

        assertEquals("Recovered after failure.", result)
    }
}
