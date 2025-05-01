package ai.grazie.code.agents.example.memory

import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.local.KotlinAIAgent
import ai.grazie.code.agents.local.agent.ContextTransitionPolicy.*
import ai.grazie.code.agents.local.agent.LocalAgentConfig
import ai.grazie.code.agents.local.agent.LocalAgentStrategy
import ai.grazie.code.agents.local.dsl.builders.LocalAgentStrategyBuilder
import ai.grazie.code.agents.local.dsl.builders.forwardTo
import ai.grazie.code.agents.local.dsl.builders.strategy
import ai.grazie.code.agents.local.dsl.extensions.nodeLLMSendStageInput
import ai.grazie.code.agents.local.environment.AgentEnvironment
import ai.grazie.code.agents.local.environment.ReceivedToolResult
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.model.CodePromptExecutor
import ai.jetbrains.code.prompt.llm.JetBrainsAIModels
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Tests for the LocalAgent's llmHistoryTransitionPolicy functionality.
 * These tests verify that stages are created correctly based on the provided policy.
 */
class LLMHistoryTransitionPolicyTest {

    /**
     * Mock LLM executor that tracks conversation history for testing.
     */
    class MockLLMExecutor : CodePromptExecutor {
        val messageHistory = mutableListOf<String>()

        override suspend fun execute(prompt: Prompt): String {
            return execute(prompt, emptyList()).first().content
        }

        override suspend fun execute(prompt: Prompt, tools: List<ToolDescriptor>): List<Message.Response> {
            handlePrompt(prompt)

            val lastMessage = prompt.messages.last()

            val result = when {
                lastMessage.content == "What is the weather today?" -> {
                    Message.Assistant("It's sunny. And how are you doing today?")
                }

                lastMessage.content in listOf(
                    "It's sunny. And how are you doing today?",
                    "Result: It's sunny. And how are you doing today?"
                ) -> {
                    Message.Assistant("I'm fine. How about you?")
                }

                lastMessage.content == "I'm fine. How about you?" -> {
                    Message.Assistant("I'm doing great!")
                }

                lastMessage.content.contains("Create a comprehensive summary of this conversation.") -> {
                    Message.Assistant("TLDR: Q: weather?\n A: sunny\n Q: How r u?")
                }

                lastMessage.content == "TLDR: Q: weather?\n A: sunny\n Q: How r u?" -> {
                    Message.Assistant("imma ok")
                }

                else -> {
                    Message.Assistant("Good bye, then.")
                }
            }

            return result
                .also { messageHistory.add(it.content) }
                .let { listOf(it) }
        }

        override suspend fun executeStreaming(prompt: Prompt): Flow<String> = flow {
            emit(execute(prompt))
        }

        private fun handlePrompt(prompt: Prompt) {
            messageHistory.clear()
            prompt.messages.forEach { message ->
                messageHistory.add(message.content)
            }
        }
    }

    /**
     * Test environment that captures agent output.
     */
    class TestAgentEnvironment : AgentEnvironment {
        val output = CompletableDeferred<String>()

        override suspend fun sendTermination(result: String?) {
            result?.let { output.complete(it) }
        }

        override suspend fun reportProblem(exception: Throwable) {
            output.completeExceptionally(exception)
        }

        override suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> {
            return toolCalls.map { call ->
                ReceivedToolResult(
                    call.id,
                    call.tool,
                    "Tool execution result",
                    ToolResult.Text("Tool execution result")
                )
            }
        }
    }

    private lateinit var mockLLMExecutor: MockLLMExecutor
    private lateinit var testEnvironment: TestAgentEnvironment
    private lateinit var emptyToolRegistry: ToolRegistry
    private lateinit var dummyEventHandler: EventHandler
    private lateinit var dummyAgentConfig: LocalAgentConfig
    private lateinit var testScope: TestScope
    private lateinit var result: CompletableDeferred<String?>

    @BeforeEach
    fun setup() {
        mockLLMExecutor = MockLLMExecutor()
        testEnvironment = TestAgentEnvironment()
        testScope = TestScope()
        emptyToolRegistry = ToolRegistry {}
        result = CompletableDeferred()
        dummyEventHandler = EventHandler {
            handleResult {
                result.complete(it)
            }
        }
        dummyAgentConfig = LocalAgentConfig(
            prompt = prompt(JetBrainsAIModels.Google.Flash2_0, "test-agent") {},
            maxAgentIterations = 30
        )
    }

    private fun createRunnableAgent(strategy: LocalAgentStrategy): KotlinAIAgent = KotlinAIAgent(
        promptExecutor = mockLLMExecutor,
        toolRegistry = emptyToolRegistry,
        strategy = strategy,
        eventHandler = dummyEventHandler,
        agentConfig = dummyAgentConfig,
        cs = testScope
    )

    /**
     * Creates a simple stage for testing.
     *
     * @param name The name of the stage
     * @return A simple LocalAgentStage
     */
    private fun LocalAgentStrategyBuilder.createTestStage(
        name: String
    ) {
        stage(name) {
            val sendInput by nodeLLMSendStageInput()

            edge(nodeStart forwardTo sendInput)
            edge(sendInput forwardTo nodeFinish transformed { "Result: ${it.content}" })
        }
    }

    /**
     * Test creating an agent with the default llmHistoryTransitionPolicy (PERSIST_LLM_HISTORY).
     */
    @Test
    fun `test creating agent with default llmHistoryTransitionPolicy passes LLM history between stages`(): Unit =
        runBlocking {
            val strategy = strategy("default-agent") {
                createTestStage("stage1")
                createTestStage("stage2")
            }

            val agent = createRunnableAgent(strategy)
            agent.run("What is the weather today?")
            result.await()

            assertEquals(
                mockLLMExecutor.messageHistory,
                listOf(
                    "What is the weather today?",
                    "It's sunny. And how are you doing today?",
                    "Result: It's sunny. And how are you doing today?",
                    "I'm fine. How about you?"
                )
            )
        }

    /**
     * Test agent behavior with PERSIST_LLM_HISTORY policy.
     * This test verifies that the conversation history is preserved between stages.
     */
    @Test
    fun `test agent behavior with PERSIST_LLM_HISTORY policy`(): Unit =
        runBlocking {
            val strategy = strategy("default-agent", llmHistoryTransitionPolicy = PERSIST_LLM_HISTORY) {
                createTestStage("stage1")
                createTestStage("stage2")
            }

            val agent = createRunnableAgent(strategy)
            agent.run("What is the weather today?")
            result.await()

            assertEquals(
                listOf(
                    "What is the weather today?",
                    "It's sunny. And how are you doing today?",
                    "Result: It's sunny. And how are you doing today?",
                    "I'm fine. How about you?"
                ),
                mockLLMExecutor.messageHistory
            )
        }

    /**
     * Test agent behavior with COMPRESS_LLM_HISTORY policy.
     * This test verifies that the conversation history is compressed between stages.
     */
    @Test
    fun `test agent behavior with COMPRESS_LLM_HISTORY policy`(): Unit =
        runBlocking {
            val strategy = strategy("default-agent", llmHistoryTransitionPolicy = COMPRESS_LLM_HISTORY) {
                createTestStage("stage1")
                createTestStage("stage2")
            }

            val agent = createRunnableAgent(strategy)
            agent.run("What is the weather today?")
            result.await()

            assertEquals(
                listOf(
                    "What is the weather today?", // first user message retains in "compress history"
                    "TLDR: Q: weather?\n A: sunny\n Q: How r u?", // TLDR message from the LLM
                    "Result: It's sunny. And how are you doing today?", // result of the stage1, added as input in stage2
                    "I'm fine. How about you?" // result of stage2
                ),
                mockLLMExecutor.messageHistory
            )
        }

    /**
     * Test agent behavior with CLEAR_LLM_HISTORY policy.
     * This test verifies that the conversation history is cleared between stages.
     */
    @Test
    fun `test agent behavior with CLEAR_LLM_HISTORY policy`(): Unit =
        runBlocking {
            val strategy = strategy("default-agent", llmHistoryTransitionPolicy = CLEAR_LLM_HISTORY) {
                createTestStage("stage1")
                createTestStage("stage2")
            }

            val agent = createRunnableAgent(strategy)
            agent.run("What is the weather today?")
            result.await()

            assertEquals(
                listOf(
                    "Result: It's sunny. And how are you doing today?",
                    "I'm fine. How about you?"
                ),
                mockLLMExecutor.messageHistory
            )
        }
}