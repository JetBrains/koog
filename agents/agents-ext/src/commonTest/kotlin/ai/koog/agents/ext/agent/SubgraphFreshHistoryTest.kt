package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.testing.tools.TestFinishTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.message.Message
import ai.koog.utils.io.use
import kotlinx.coroutines.test.runTest
import kotlin.js.JsName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubgraphFreshHistoryTest {

    private val model = OpenAIModels.Chat.GPT4o
    private val finishTool = TestFinishTool

    private fun createAgentWithFreshHistory(
        freshHistory: Boolean,
        executor: ai.koog.prompt.executor.model.PromptExecutor,
        capturedPrompts: MutableList<Prompt>,
    ): AIAgent<String, String> {
        val strategy = strategy<String, String>("test-strategy") {
            val testSubgraph by subgraphWithTask<String, TestFinishTool.Args, String>(
                toolSelectionStrategy = ai.koog.agents.core.agent.entity.ToolSelectionStrategy.ALL,
                finishTool = finishTool,
                llmModel = model,
                runMode = ToolCalls.SEQUENTIAL,
                freshHistory = freshHistory,
            ) { input -> "Instruction for: $input" }

            nodeStart then testSubgraph then nodeFinish
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test-agent") {
                system("You are a parent system prompt.")
                user("Some prior conversation message.")
                assistant("Some prior assistant response.")
            },
            model = model,
            maxAgentIterations = 20,
        )

        return AIAgent(
            promptExecutor = executor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry { },
            installFeatures = {
                install(EventHandler) {
                    onLLMCallStarting {
                        capturedPrompts += it.prompt
                    }
                }
            },
        )
    }

    @Test
    @JsName("testFreshHistorySubgraphStartsWithEmptyHistoryAndSystemMessage")
    fun `test freshHistory subgraph starts with empty history and system message`() = runTest {
        val capturedPrompts = mutableListOf<Prompt>()

        val mockExecutor = getMockExecutor {
            mockLLMToolCall(finishTool, TestFinishTool.Args()) onCondition { true }
        }

        createAgentWithFreshHistory(
            freshHistory = true,
            executor = mockExecutor,
            capturedPrompts = capturedPrompts,
        ).use { agent ->
            agent.run("hello", null)
        }

        assertTrue(capturedPrompts.isNotEmpty(), "Expected at least one LLM call")

        val firstPrompt = capturedPrompts.first()
        val messages = firstPrompt.messages

        // With freshHistory=true, the subgraph should NOT have the parent's history.
        // It should start fresh with only a system message from defineTask.
        val systemMessages = messages.filterIsInstance<Message.System>()
        val userMessages = messages.filterIsInstance<Message.User>()
        val assistantMessages = messages.filterIsInstance<Message.Assistant>()

        // defineTask result ("Instruction for: hello") should be a system message
        assertEquals(1, systemMessages.size, "Expected exactly one system message from defineTask")
        assertTrue(
            systemMessages.first().content.contains("Instruction for: hello"),
            "System message should contain defineTask result, got: ${systemMessages.first().content}"
        )

        // Parent's user/assistant messages should NOT be present
        assertTrue(
            userMessages.none { it.content.contains("Some prior conversation message") },
            "Parent's user message should not be in fresh history"
        )
        assertTrue(
            assistantMessages.none { it.content.contains("Some prior assistant response") },
            "Parent's assistant message should not be in fresh history"
        )
    }

    @Test
    @JsName("testDefaultHistorySubgraphPreservesParentHistoryAndUsesUserMessage")
    fun `test default history subgraph preserves parent history and uses user message`() = runTest {
        val capturedPrompts = mutableListOf<Prompt>()

        val mockExecutor = getMockExecutor {
            mockLLMToolCall(finishTool, TestFinishTool.Args()) onCondition { true }
        }

        createAgentWithFreshHistory(
            freshHistory = false,
            executor = mockExecutor,
            capturedPrompts = capturedPrompts,
        ).use { agent ->
            agent.run("hello", null)
        }

        assertTrue(capturedPrompts.isNotEmpty(), "Expected at least one LLM call")

        val firstPrompt = capturedPrompts.first()
        val messages = firstPrompt.messages

        // With freshHistory=false (default), the subgraph inherits the parent's history.
        // The defineTask result should be a user message (default behavior).
        val systemMessages = messages.filterIsInstance<Message.System>()
        val userMessages = messages.filterIsInstance<Message.User>()

        // Parent's system message should be preserved
        assertTrue(
            systemMessages.any { it.content.contains("You are a parent system prompt") },
            "Parent's system message should be preserved"
        )

        // Parent's prior user message should be present
        assertTrue(
            userMessages.any { it.content.contains("Some prior conversation message") },
            "Parent's user message should be preserved in default mode"
        )

        // defineTask result should be a user message
        assertTrue(
            userMessages.any { it.content.contains("Instruction for: hello") },
            "defineTask result should be appended as a user message in default mode"
        )
    }

    @Test
    @JsName("testFreshHistoryDoesNotLeakSubgraphHistoryBackToParent")
    fun `test freshHistory does not leak subgraph history back to parent`() = runTest {
        val capturedPrompts = mutableListOf<Prompt>()

        val mockExecutor = getMockExecutor {
            mockLLMToolCall(finishTool, TestFinishTool.Args()) onCondition { true }
        }

        // Strategy with two sequential subgraphs — first fresh, then default.
        // The second should NOT see the first subgraph's history.
        val strategy = strategy<String, String>("test-strategy") {
            val freshSubgraph by subgraphWithTask<String, TestFinishTool.Args, String>(
                toolSelectionStrategy = ai.koog.agents.core.agent.entity.ToolSelectionStrategy.ALL,
                finishTool = finishTool,
                llmModel = model,
                runMode = ToolCalls.SEQUENTIAL,
                freshHistory = true,
            ) { input -> "Fresh instruction: $input" }

            val normalSubgraph by subgraphWithTask<String, TestFinishTool.Args, String>(
                toolSelectionStrategy = ai.koog.agents.core.agent.entity.ToolSelectionStrategy.ALL,
                finishTool = finishTool,
                llmModel = model,
                runMode = ToolCalls.SEQUENTIAL,
                freshHistory = false,
            ) { input -> "Normal instruction: $input" }

            nodeStart then freshSubgraph then normalSubgraph then nodeFinish
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test-agent") {
                system("Parent system prompt.")
            },
            model = model,
            maxAgentIterations = 40,
        )

        AIAgent(
            promptExecutor = mockExecutor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry { },
            installFeatures = {
                install(EventHandler) {
                    onLLMCallStarting {
                        capturedPrompts += it.prompt
                    }
                }
            },
        ).use { agent ->
            agent.run("hello", null)
        }

        assertTrue(capturedPrompts.size >= 2, "Expected at least two LLM calls (one per subgraph)")

        // The second subgraph's prompt (captured second) should still have parent's system prompt
        // but should NOT contain the fresh subgraph's system message
        val secondPrompt = capturedPrompts[1]
        val secondMessages = secondPrompt.messages

        assertTrue(
            secondMessages.filterIsInstance<Message.System>().any { it.content.contains("Parent system prompt") },
            "Second subgraph should see parent's system prompt"
        )

        assertTrue(
            secondMessages.none { it.content.contains("Fresh instruction:") },
            "Fresh subgraph's system message should not leak into the second subgraph"
        )
    }
}
