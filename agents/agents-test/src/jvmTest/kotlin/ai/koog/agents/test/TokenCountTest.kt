package ai.koog.agents.test

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.eventHandler.feature.EventHandlerConfig
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.tokenizer.Tokenizer
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.test.AfterTest
import kotlin.test.Test

class TokenCountTest {
    /**
     * A mock tokenizer that tracks the total tokens counted and provides deterministic token counts.
     * This implementation counts tokens by counting words and adding 1 for consistency and tests' transparency.
     */
    class MockTokenizer : Tokenizer {
        private var _totalTokens = 0

        val totalTokens: Int
            get() = _totalTokens

        override fun countTokens(text: String): Int {
            val tokens = text.trim().split(Regex("\\s+")).size + 1
            _totalTokens += tokens
            return tokens
        }

        fun reset() {
            _totalTokens = 0
        }
    }

    object TestTool : SimpleTool<TestTool.Args>() {
        @Serializable
        data class Args(
            @property:LLMDescription("Test message")
            val message: String
        )

        override val argsSerializer: KSerializer<Args> = Args.serializer()
        override val name: String = "test_tool"
        override val description: String = "A test tool for token counting"

        override suspend fun doExecute(args: Args): String {
            return "Test tool executed with: ${args.message}"
        }
    }

    private val mockTokenizer = MockTokenizer()
    private val responses = mutableListOf<Message.Response>()
    private var inputTokens: Int? = null
    private var outputTokens: Int? = null
    private var totalTokens: Int? = null

    private val clock = Clock.System

    private val systemPrompt = """
        You are a helpful assistant. Use tools when requested.
        Always provide clear and concise responses.
    """.trimIndent()

    private val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
        onLLMCallCompleted { eventContext ->
            responses.addAll(eventContext.responses)

            eventContext.responses.lastOrNull()?.metaInfo?.let { metaInfo ->
                inputTokens = metaInfo.inputTokensCount
                outputTokens = metaInfo.outputTokensCount
                totalTokens = metaInfo.totalTokensCount
            }
        }
    }

    @AfterTest
    fun teardown() {
        responses.clear()
        inputTokens = null
        outputTokens = null
        totalTokens = null
        mockTokenizer.reset()
    }

    @Test
    fun `test token counts for assistant responses`() = runTest {
        val testExecutor = getMockExecutor(
            tokenizer = mockTokenizer,
            clock = clock
        ) {
            mockLLMAnswer("This is a test response with multiple words") onRequestEquals "Test simple response"
        }

        AIAgent(
            systemPrompt = systemPrompt,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            temperature = 0.0,
            maxIterations = 3,
            promptExecutor = testExecutor,
            installFeatures = { install(EventHandler, eventHandlerConfig) }
        ).run("Test simple response")

        responses.shouldNotBeEmpty()

        responses.filterIsInstance<Message.Assistant>().firstOrNull().shouldNotBeNull()
        inputTokens.shouldNotBeNull { shouldBeEqual(4) } // "Test simple response" = 3 words + 1 = 4 tokens
        outputTokens.shouldNotBeNull { shouldBeEqual(9) }
        totalTokens.shouldNotBeNull { shouldBeEqual(inputTokens!! + outputTokens!!) }
    }

    @Test
    fun `test token counts for tool call responses`() = runTest {
        val toolRegistry = ToolRegistry {
            tool(TestTool)
        }

        val testExecutor = getMockExecutor(
            toolRegistry = toolRegistry,
            tokenizer = mockTokenizer,
            clock = clock
        ) {
            mockLLMToolCall(TestTool, TestTool.Args("token count test")) onRequestEquals "Use test tool"
            mockLLMAnswer("Task completed successfully") onRequestContains "Tool executed successfully"
            mockTool(TestTool) alwaysReturns "Tool executed successfully with token tracking"
        }

        AIAgent(
            systemPrompt = systemPrompt,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            temperature = 0.0,
            toolRegistry = toolRegistry,
            maxIterations = 5,
            promptExecutor = testExecutor,
            installFeatures = { install(EventHandler, eventHandlerConfig) }
        ).run("Use test tool")

        responses.shouldNotBeEmpty()

        responses.filterIsInstance<Message.Tool.Call>().firstOrNull().shouldNotBeNull()
        inputTokens.shouldNotBeNull { shouldBeEqual(8) } // "Test tool executed with: token count test" = 7 words + 1 = 8 tokens
        outputTokens.shouldNotBeNull { shouldBeEqual(2) }
        totalTokens.shouldNotBeNull { shouldBeEqual(inputTokens!! + outputTokens!!) }
    }

    @Test
    fun `test token counts across multiple responses`() = runTest {
        val toolRegistry = ToolRegistry {
            tool(SayToUser)
        }

        val initialTokenCount = mockTokenizer.totalTokens

        val testExecutor = getMockExecutor(
            toolRegistry = toolRegistry,
            tokenizer = mockTokenizer,
            clock = clock
        ) {
            mockLLMToolCall(SayToUser, SayToUser.Args("First message")) onRequestEquals "Send two messages"
            mockLLMAnswer("All tasks completed successfully") onRequestContains "Message sent successfully"
            mockTool(SayToUser) alwaysReturns "Message sent successfully"
        }

        AIAgent(
            systemPrompt = systemPrompt,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            temperature = 0.0,
            toolRegistry = toolRegistry,
            maxIterations = 5,
            promptExecutor = testExecutor,
            installFeatures = { install(EventHandler, eventHandlerConfig) }
        ).run("Send two messages")

        responses.shouldNotBeEmpty()

        responses.filterIsInstance<Message.Assistant>().shouldNotBeNull()
        responses.filterIsInstance<Message.Tool>().shouldNotBeNull()
        inputTokens.shouldNotBeNull { shouldBeEqual(2) } // "DONE" (SayToUser tool result) = 1 word + 1 = 2 tokens
        outputTokens.shouldNotBeNull { shouldBeEqual(2) }
        totalTokens.shouldNotBeNull { shouldBeEqual(inputTokens!! + outputTokens!!) }

        mockTokenizer.totalTokens.shouldNotBeNull {
            shouldBeGreaterThan(initialTokenCount)
        }
    }

    @Test
    fun `test token counts mixed responses`() = runTest {
        val toolRegistry = ToolRegistry {
            tool(TestTool)
        }

        val testExecutor = getMockExecutor(
            toolRegistry = toolRegistry,
            tokenizer = mockTokenizer,
            clock = clock
        ) {
            mockLLMMixedResponse(
                toolCalls = listOf(TestTool to TestTool.Args("mixed test")),
                responses = listOf("Here is a mixed response with both tool call and text")
            ) onRequestEquals "Mixed response test"

            mockTool(TestTool) alwaysReturns "Mixed tool execution result"
        }

        AIAgent(
            systemPrompt = systemPrompt,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            temperature = 0.0,
            toolRegistry = toolRegistry,
            maxIterations = 5,
            promptExecutor = testExecutor,
            installFeatures = { install(EventHandler, eventHandlerConfig) }
        ).run("Mixed response test")

        responses.shouldNotBeEmpty()

        responses.filterIsInstance<Message.Assistant>().shouldNotBeNull()
        responses.filterIsInstance<Message.Tool>().shouldNotBeNull()
        inputTokens.shouldNotBeNull { shouldBeEqual(4) } // "Mixed response test" = 3 words + 1 = 4 tokens
        outputTokens.shouldNotBeNull { shouldBeEqual(3) }
        totalTokens.shouldNotBeNull { shouldBeEqual(inputTokens!! + outputTokens!!) }
    }
}
