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
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.tokenizer.Tokenizer
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.typeToken
import ai.koog.utils.time.KoogClock
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Disabled
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TokenCountTest {
    private val serializer = KotlinxSerializer()

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

    object TestTool : SimpleTool<TestTool.Args>(
        argsType = typeToken<Args>(),
        name = "test_tool",
        description = "A test tool for token counting"
    ) {
        @Serializable
        data class Args(
            @property:LLMDescription("Test message")
            val message: String
        )

        override suspend fun execute(args: Args): String {
            return "Test tool executed with: ${args.message}"
        }
    }

    private val mockTokenizer = MockTokenizer()
    private var response: Message.Assistant? = null

    private val clock = KoogClock.System

    private val systemPrompt = """
        You are a helpful assistant. Use tools when requested.
        Always provide clear and concise responses.
    """.trimIndent()

    private val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
        onLLMCallCompleted { eventContext ->
            response = eventContext.response
        }
    }

    @AfterTest
    fun teardown() {
        response = null
        mockTokenizer.reset()
    }

    @Test
    fun `test token counts for assistant responses`() = runTest {
        val testExecutor = getMockExecutor(
            serializer = serializer,
            tokenizer = mockTokenizer,
            clock = clock,
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

        val currentResponse = assertNotNull(response)

        assertEquals(listOf(MessagePart.Text("This is a test response with multiple words")), currentResponse.parts)
        currentResponse.metaInfo.inputTokensCount.shouldNotBeNull { shouldBeEqual(4) } // "Test simple response" = 3 words + 1 = 4 tokens
        currentResponse.metaInfo.outputTokensCount.shouldNotBeNull { shouldBeEqual(9) }
        currentResponse.metaInfo.totalTokensCount.shouldNotBeNull { shouldBeEqual(currentResponse.metaInfo.inputTokensCount!! + currentResponse.metaInfo.outputTokensCount!!) }
    }

    @Test
    fun `test token counts for tool call responses`() = runTest {
        val toolRegistry = ToolRegistry {
            tool(TestTool)
        }

        val responses = mutableListOf<Message.Assistant>()

        val testExecutor = getMockExecutor(
            serializer = serializer,
            tokenizer = mockTokenizer,
            clock = clock
        ) {
            mockLLMToolCall(TestTool, TestTool.Args("token count test")) onRequestEquals "Use test tool"
            mockLLMAnswer("Task completed successfully").asDefaultResponse
            mockTool(TestTool) alwaysReturns "Tool executed successfully with token tracking"
        }

        AIAgent(
            systemPrompt = systemPrompt,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            temperature = 0.0,
            toolRegistry = toolRegistry,
            maxIterations = 5,
            promptExecutor = testExecutor,
            installFeatures = {
                install(EventHandler) {
                    onLLMCallCompleted { eventContext ->
                        eventContext.response?.let { responses.add(it) }
                    }
                }
            }
        ).run("Use test tool")

        val currentResponse = assertNotNull(responses.firstOrNull())

        assertEquals(
            listOf(MessagePart.Tool.Call(tool = "test_tool", args = """{"message":"token count test"}""")),
            currentResponse.parts
        )
        currentResponse.metaInfo.inputTokensCount.shouldNotBeNull { shouldBeEqual(4) } // "Use test tool" = 3 words + 1 = 4 tokens
        currentResponse.metaInfo.outputTokensCount.shouldNotBeNull { shouldBeEqual(4) } // {"message":"token count test"} = 3 words + 1 = 4 tokens
        currentResponse.metaInfo.totalTokensCount.shouldNotBeNull { shouldBeEqual(currentResponse.metaInfo.inputTokensCount!! + currentResponse.metaInfo.outputTokensCount!!) }
    }

    @Test
    fun `test token counts across multiple responses`() = runTest {
        val toolRegistry = ToolRegistry {
            tool(SayToUser)
        }

        val initialTokenCount = mockTokenizer.totalTokens
        val responses = mutableListOf<Message.Assistant>()

        val testExecutor = getMockExecutor(
            serializer = serializer,
            tokenizer = mockTokenizer,
            clock = clock
        ) {
            mockLLMToolCall(SayToUser, SayToUser.Args("First message")) onRequestEquals "Send two messages"
            mockLLMAnswer("All tasks completed successfully").asDefaultResponse
            mockTool(SayToUser) alwaysReturns "Message sent successfully"
        }

        AIAgent(
            systemPrompt = systemPrompt,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            temperature = 0.0,
            toolRegistry = toolRegistry,
            maxIterations = 5,
            promptExecutor = testExecutor,
            installFeatures = {
                install(EventHandler) {
                    onLLMCallCompleted { eventContext ->
                        eventContext.response?.let { responses.add(it) }
                    }
                }
            }
        ).run("Send two messages")

        val currentResponse = assertNotNull(responses.firstOrNull())

        assertEquals(
            listOf(MessagePart.Tool.Call(tool = "say_to_user", args = """{"message":"First message"}""")),
            currentResponse.parts
        )
        currentResponse.metaInfo.inputTokensCount.shouldNotBeNull { shouldBeEqual(4) } // "Send two messages" = 3 words + 1 = 4 tokens
        currentResponse.metaInfo.outputTokensCount.shouldNotBeNull { shouldBeEqual(3) } // {"message":"First message"} = 2 words + 1 = 3 tokens
        currentResponse.metaInfo.totalTokensCount.shouldNotBeNull { shouldBeEqual(currentResponse.metaInfo.inputTokensCount!! + currentResponse.metaInfo.outputTokensCount!!) }
        mockTokenizer.totalTokens shouldBeGreaterThan initialTokenCount
    }

    @Test
    @Disabled("This test is flaky, need to investigate: https://youtrack.jetbrains.com/issue/KG-585/Investigate-flaky-TokenCountTest.test-token-counts-mixed-responses")
    fun `test token counts mixed responses`() = runTest {
        val toolRegistry = ToolRegistry {
            tool(TestTool)
        }

        val responses = mutableListOf<Message.Assistant>()

        val testExecutor = getMockExecutor(
            serializer = serializer,
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
            installFeatures = {
                install(EventHandler) {
                    onLLMCallCompleted { eventContext ->
                        eventContext.response?.let { responses.add(it) }
                    }
                }
            }
        ).run("Mixed response test")

        val currentResponse = assertNotNull(responses.firstOrNull())

        assertNotNull(currentResponse.parts.filterIsInstance<MessagePart.Tool.Call>().firstOrNull())
        assertNotNull(currentResponse.parts.filterIsInstance<MessagePart.Text>().firstOrNull())
        currentResponse.metaInfo.inputTokensCount.shouldNotBeNull { shouldBeEqual(4) } // "Mixed response test" = 3 words + 1 = 4 tokens
        currentResponse.metaInfo.outputTokensCount.shouldNotBeNull { shouldBeGreaterThan(0) }
        currentResponse.metaInfo.totalTokensCount.shouldNotBeNull { shouldBeEqual(currentResponse.metaInfo.inputTokensCount!! + currentResponse.metaInfo.outputTokensCount!!) }
    }
}
