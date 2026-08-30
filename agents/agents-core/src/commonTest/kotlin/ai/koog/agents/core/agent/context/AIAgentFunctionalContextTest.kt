package ai.koog.agents.core.agent.context

import ai.koog.agents.core.CalculatorChatExecutor.testClock
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.testing.tools.TestBlankTool
import ai.koog.agents.testing.tools.TestFinishTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.MessagePart
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.utils.io.use
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class AIAgentFunctionalContextTest : AgentTestBase() {

    private val serializer = KotlinxSerializer()

    /**
     * Verifies that `subtask` with `parallelTools = false` appends tool results for
     * ALL tool calls to the prompt when the LLM calls the finish tool together with another tool
     * in a single response.
     */
    @Test
    fun testSubtaskSequentialAllToolCallsHaveToolResults() = runTest {
        runAndAssertAllToolCallsHaveResults(parallelTools = false)
    }

    /**
     * Verifies that `subtask` with `parallelTools = true` appends tool results for
     * ALL tool calls to the prompt when the LLM calls the finish tool together with another tool
     * in a single response.
     */
    @Test
    fun testSubtaskParallelAllToolCallsHaveToolResults() = runTest {
        runAndAssertAllToolCallsHaveResults(parallelTools = true)
    }

    /**
     * Verifies that `subtask` appends tool results for every tool call to the prompt:
     * a regular tool call followed by the finish tool call across two LLM round-trips.
     */
    @Test
    fun testSubtaskSequentialAcrossMultipleRoundsHasToolResults() = runTest {
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
            parallelTools = false,
        )

        assertEqualToolCallAndResultCount(finalPrompt, expectedSize = 2)
    }

    @Test
    fun testForkCreatesIndependentStorageSnapshot() = runTest {
        val key = createStorageKey<String>("test-key")
        val context = createTestFunctionalContext()
        context.storage.set(key, "original-value")

        val fork = context.fork()
        fork.storage.set(key, "fork-value")

        assertNotSame(context.storage, fork.storage)
        assertEquals("original-value", context.storage.get(key))
        assertEquals("fork-value", fork.storage.get(key))
    }

    @Test
    fun testForkCreatesIndependentStateManagerSnapshot() = runTest {
        val context = createTestFunctionalContext()
        context.stateManager.withStateLock { state ->
            state.iterations = 1
        }

        val fork = context.fork()
        fork.stateManager.withStateLock { state ->
            state.iterations = 7
        }

        assertNotSame(context.stateManager, fork.stateManager)
        assertEquals(1, context.stateManager.withStateLock { it.iterations })
        assertEquals(7, fork.stateManager.withStateLock { it.iterations })
    }

    @Test
    fun testForkCreatesIndependentLLMContextSnapshot() = runTest {
        val context = createTestFunctionalContext()
        context.llm.writeSession {
            appendPrompt {
                user("original-message")
            }
        }

        val fork = context.fork()
        fork.llm.writeSession {
            appendPrompt {
                user("fork-message")
            }
        }

        assertNotSame(context.llm, fork.llm)
        assertEquals(listOf("original-message"), context.llm.prompt.messages.map { it.textContent() })
        assertEquals(listOf("original-message", "fork-message"), fork.llm.prompt.messages.map { it.textContent() })
    }

    private suspend fun runAndAssertAllToolCallsHaveResults(parallelTools: Boolean) {
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
            parallelTools = parallelTools,
        )

        assertEqualToolCallAndResultCount(finalPrompt, expectedSize = 2)
    }

    private suspend fun runAgentAndCapturePrompt(
        mockExecutor: PromptExecutor,
        toolRegistry: ToolRegistry,
        inputRequest: String,
        blankTool: TestBlankTool,
        finishTool: Tool<TestFinishTool.Args, String>,
        parallelTools: Boolean,
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
                    parallelTools = parallelTools,
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

    private fun createTestFunctionalContext(): AIAgentFunctionalContext {
        val config = createTestConfig()

        return AIAgentFunctionalContext(
            environment = createTestEnvironment(),
            agentId = testAgentId,
            runId = "test-run-id",
            agentInput = "test-input",
            config = config,
            llm = createTestLLMContext(),
            stateManager = createTestStateManager(),
            storage = createTestStorage(),
            strategyName = strategyName,
            pipeline = AIAgentFunctionalPipeline(config, testClock),
            executionInfo = AgentExecutionInfo(null, testAgentId),
            parentContext = null
        )
    }

    private fun assertEqualToolCallAndResultCount(prompt: Prompt, expectedSize: Int) {
        val parts = prompt.messages.flatMap { it.parts }
        val toolCalls = parts.filterIsInstance<MessagePart.Tool.Call>()
        val toolResults = parts.filterIsInstance<MessagePart.Tool.Result>()

        withClue("Equal number of tool calls and tool results") {
            toolCalls.size shouldBe expectedSize
            toolResults.size shouldBe expectedSize
        }
    }
}
