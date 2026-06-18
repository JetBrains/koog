package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.testing.tools.TestFinishTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.Prompt
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
                parallelTools = false,
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
    @JsName("testFreshHistorySubgraphKeepsParentSystemAndUsesUserMessageForTask")
    fun `test freshHistory subgraph keeps parent system message and uses user message for task`() = runTest {
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

        // With freshHistory=true: parent system message is retained, user/assistant turns are dropped.
        val systemMessages = messages.filterIsInstance<Message.System>()
        val userMessages = messages.filterIsInstance<Message.User>()
        val assistantMessages = messages.filterIsInstance<Message.Assistant>()

        // Parent's system message should be preserved
        assertEquals(1, systemMessages.size, "Expected exactly one system message (the parent's)")
        assertTrue(
            systemMessages.first().textContent().contains("You are a parent system prompt"),
            "Parent system message should be retained, got: ${systemMessages.first().textContent()}"
        )

        // defineTask result ("Instruction for: hello") should be a user message
        assertTrue(
            userMessages.any { it.textContent().contains("Instruction for: hello") },
            "defineTask result should be a user message, got user messages: ${userMessages.map { it.textContent() }}"
        )

        // Parent's user/assistant conversation turns should NOT be present
        assertTrue(
            userMessages.none { it.textContent().contains("Some prior conversation message") },
            "Parent's user conversation turn should not be inherited"
        )
        assertTrue(
            assistantMessages.none { it.textContent().contains("Some prior assistant response") },
            "Parent's assistant conversation turn should not be inherited"
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
            systemMessages.any { it.textContent().contains("You are a parent system prompt") },
            "Parent's system message should be preserved"
        )

        // Parent's prior user message should be present
        assertTrue(
            userMessages.any { it.textContent().contains("Some prior conversation message") },
            "Parent's user message should be preserved in default mode"
        )

        // defineTask result should be a user message
        assertTrue(
            userMessages.any { it.textContent().contains("Instruction for: hello") },
            "defineTask result should be appended as a user message in default mode"
        )
    }

    @Test
    @JsName("testFreshHistoryDoesNotLeakConversationTurnsToParentOrSiblings")
    fun `test freshHistory does not leak conversation turns to parent or siblings`() = runTest {
        val capturedPrompts = mutableListOf<Prompt>()

        val mockExecutor = getMockExecutor {
            mockLLMToolCall(finishTool, TestFinishTool.Args()) onCondition { true }
        }

        // Two sequential subgraphs: first fresh, then default.
        // The second (default) subgraph should NOT see the first subgraph's conversation turns.
        val strategy = strategy<String, String>("test-strategy") {
            val freshSubgraph by subgraphWithTask<String, TestFinishTool.Args, String>(
                toolSelectionStrategy = ai.koog.agents.core.agent.entity.ToolSelectionStrategy.ALL,
                finishTool = finishTool,
                llmModel = model,
                parallelTools = false,
                freshHistory = true,
            ) { input -> "Fresh instruction: $input" }

            val normalSubgraph by subgraphWithTask<String, TestFinishTool.Args, String>(
                toolSelectionStrategy = ai.koog.agents.core.agent.entity.ToolSelectionStrategy.ALL,
                finishTool = finishTool,
                llmModel = model,
                parallelTools = false,
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

        val secondMessages = capturedPrompts[1].messages

        // Parent system prompt is preserved in both subgraphs
        assertTrue(
            secondMessages.filterIsInstance<Message.System>().any { it.textContent().contains("Parent system prompt") },
            "Second subgraph should see parent's system prompt"
        )

        // Fresh subgraph's conversation turns must not leak into the second subgraph's prompt
        assertTrue(
            secondMessages.none { it.textContent().contains("Fresh instruction:") },
            "Fresh subgraph's conversation turns should not appear in the second subgraph's prompt"
        )
    }
}
