package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.testing.tools.TestBlankTool
import ai.koog.agents.testing.tools.TestFinishTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.Message
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.utils.io.use
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class AIAgentFunctionalContextTest {

    private val serializer = KotlinxSerializer()

    /**
     * Verifies that `subtaskWithMultiToolMode` (`ToolCalls.SEQUENTIAL`) appends tool results for
     * ALL tool calls to the prompt when the LLM calls the finish tool together with another tool
     * in a single response.
     */
    @Test
    fun testSubtaskWithMultiToolModeSequentialAllToolCallsHaveToolResults() = runTest {
        runAndAssertAllToolCallsHaveResults(ToolCalls.SEQUENTIAL)
    }

    /**
     * Verifies that `subtaskWithMultiToolMode` (`ToolCalls.PARALLEL`) appends tool results for
     * ALL tool calls to the prompt when the LLM calls the finish tool together with another tool
     * in a single response.
     */
    @Test
    fun testSubtaskWithMultiToolModeParallelAllToolCallsHaveToolResults() = runTest {
        runAndAssertAllToolCallsHaveResults(ToolCalls.PARALLEL)
    }

    /**
     * Verifies that `subtaskWithSingleToolMode` (`ToolCalls.SINGLE_RUN_SEQUENTIAL`) appends tool
     * results for every tool call to the prompt: a regular tool call followed by the finish tool
     * call across two LLM round-trips (single-tool mode allows only one tool per LLM response).
     */
    @Test
    fun testSubtaskWithSingleToolModeAllToolCallsHaveToolResults() = runTest {
        val blankTool = TestBlankTool()
        val finishTool = TestFinishTool

        val toolRegistry = ToolRegistry { tool(blankTool) }

        val inputRequest = "Test input"
        val blankToolResult = "Working on it"
        val finishToolResult = "Finished"

        val mockExecutor = getMockExecutor(serializer) {
            mockLLMToolCall(blankTool, TestBlankTool.Args(blankToolResult)) onRequestEquals inputRequest
            mockLLMToolCall(finishTool, TestFinishTool.Args(finishToolResult)) onRequestContains blankToolResult
        }

        val finalPrompt = runAgentAndCapturePrompt(
            mockExecutor = mockExecutor,
            toolRegistry = toolRegistry,
            inputRequest = inputRequest,
            blankTool = blankTool,
            finishTool = finishTool,
            runMode = ToolCalls.SINGLE_RUN_SEQUENTIAL,
        )

        assertEqualToolCallAndResultCount(finalPrompt, expectedSize = 2)
    }

    private suspend fun runAndAssertAllToolCallsHaveResults(runMode: ToolCalls) {
        val blankTool = TestBlankTool()
        val finishTool = TestFinishTool

        val toolRegistry = ToolRegistry { tool(blankTool) }

        val inputRequest = "Test input"
        val blankToolResult = "I'm done"
        val finishToolResult = "Finished"

        val mockExecutor = getMockExecutor(serializer) {
            @Suppress("UNCHECKED_CAST")
            mockLLMToolCall(
                listOf(
                    blankTool to TestBlankTool.Args(blankToolResult),
                    finishTool to TestFinishTool.Args(finishToolResult),
                ) as List<Pair<Tool<Any?, Any?>, Any?>>
            ) onRequestEquals inputRequest
        }

        val finalPrompt = runAgentAndCapturePrompt(
            mockExecutor = mockExecutor,
            toolRegistry = toolRegistry,
            inputRequest = inputRequest,
            blankTool = blankTool,
            finishTool = finishTool,
            runMode = runMode,
        )

        assertEqualToolCallAndResultCount(finalPrompt, expectedSize = 2)
    }

    private suspend fun runAgentAndCapturePrompt(
        mockExecutor: PromptExecutor,
        toolRegistry: ToolRegistry,
        inputRequest: String,
        blankTool: TestBlankTool,
        finishTool: Tool<TestFinishTool.Args, String>,
        runMode: ToolCalls,
    ): Prompt {
        lateinit var finalPrompt: Prompt

        AIAgent(
            promptExecutor = mockExecutor,
            llmModel = OpenAIModels.Chat.GPT4o,
            toolRegistry = toolRegistry,
            strategy = functionalStrategy<String, String> { input ->
                subtask(
                    taskDescription = input,
                    tools = listOf(blankTool),
                    finishTool = finishTool,
                    runMode = runMode,
                )
            },
            systemPrompt = "You are a test agent.",
        ) {
            install(EventHandler) {
                onAgentCompleted { ctx ->
                    finalPrompt = ctx.context.llm.prompt
                }
            }
        }.use { agent ->
            agent.run(inputRequest, null)
        }

        return finalPrompt
    }

    private fun assertEqualToolCallAndResultCount(prompt: Prompt, expectedSize: Int) {
        val toolCalls = prompt.messages.filterIsInstance<Message.Tool.Call>()
        val toolResults = prompt.messages.filterIsInstance<Message.Tool.Result>()

        withClue("Equal number of tool calls and tool results") {
            toolCalls.size shouldBe expectedSize
            toolResults.size shouldBe expectedSize
        }
    }
}
