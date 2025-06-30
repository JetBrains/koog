package ai.koog.agents.features.tokenizer.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.feature.withTesting
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.tokenizer.Tokenizer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test for the MessageTokenizer feature.
 *
 * This test verifies that the MessageTokenizer correctly tracks token usage.
 */
class MessageTokenizerTest {

    /**
     * A mock tokenizer that tracks the total tokens counted.
     *
     * This implementation counts tokens by simply counting characters and dividing by 4,
     * which is a very rough approximation but sufficient for testing purposes.
     * It also keeps track of the total tokens counted across all calls.
     */
    class MockTokenizer : Tokenizer {
        private var _totalTokens = 0

        /**
         * The total number of tokens counted across all calls to countTokens.
         */
        val totalTokens: Int
            get() = _totalTokens

        /**
         * Counts tokens by simply counting characters and dividing by 4.
         * Also adds to the running total of tokens counted.
         *
         * @param text The text to tokenize
         * @return The estimated number of tokens in the text
         */
        override fun countTokens(text: String): Int {
            // Simple approximation: 1 token ≈ 4 characters
            println("countTokens: $text")
            val tokens = (text.length / 4) + 1
            _totalTokens += tokens
            return tokens
        }

        /**
         * Resets the total tokens counter to 0.
         */
        fun reset() {
            _totalTokens = 0
        }
    }

    @Test
    fun testPromptTokenizer() = runTest {
        // Create a mock tokenizer to track token usage
        val mockTokenizer = MockTokenizer()

        // Create a prompt tokenizer with our mock tokenizer
        val promptTokenizer = OnDemandTokenizer(mockTokenizer)

        // Create a prompt with some messages
        val testPrompt = prompt("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
            assistant("Paris is the capital of France.")
        }

        // Count tokens in the prompt
        val totalTokens = promptTokenizer.tokenCountFor(testPrompt)

        // Verify that tokens were counted
        assertTrue(totalTokens > 0, "Total tokens should be greater than 0")

        // Verify that the tokenizer was used and counted tokens
        assertTrue(mockTokenizer.totalTokens > 0, "Tokenizer should have counted tokens")

        // Verify that the total tokens match what we expect
        assertEquals(totalTokens, mockTokenizer.totalTokens, "Total tokens should match the tokenizer's count")

        // Print the total tokens spent
        println("[DEBUG_LOG] Total tokens spent: ${mockTokenizer.totalTokens}")

        val requestMetainfo = RequestMetaInfo.create(Clock.System)
        val responseMetainfo = ResponseMetaInfo.create(Clock.System)
        // Count tokens for individual messages
        val systemTokens = promptTokenizer.tokenCountFor(Message.System("You are a helpful assistant.", requestMetainfo))
        val userTokens = promptTokenizer.tokenCountFor(Message.User("What is the capital of France?", requestMetainfo))
        val assistantTokens = promptTokenizer.tokenCountFor(Message.Assistant("Paris is the capital of France.", responseMetainfo))

        // Print token counts for each message
        println("[DEBUG_LOG] System message tokens: $systemTokens")
        println("[DEBUG_LOG] User message tokens: $userTokens")
        println("[DEBUG_LOG] Assistant message tokens: $assistantTokens")

        // Verify that the sum of individual message tokens equals the total
        val sumOfMessageTokens = systemTokens + userTokens + assistantTokens
        assertEquals(sumOfMessageTokens, totalTokens, "Sum of message tokens should equal total tokens")
    }

    @Test
    fun testCachingPromptTokenizer() = runTest {
        // Create a mock tokenizer to track token usage
        val mockTokenizer = MockTokenizer()

        // Create a prompt tokenizer with our mock tokenizer
        val promptTokenizer = CachingTokenizer(mockTokenizer)

        // Create a prompt with some messages
        val testPrompt = prompt("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
            assistant("Paris is the capital of France.")
        }

        assertEquals(0, promptTokenizer.cache.size)
        promptTokenizer.tokenCountFor(testPrompt)
        assertEquals(3, promptTokenizer.cache.size)
        promptTokenizer.clearCache()
        assertEquals(0, promptTokenizer.cache.size)
        promptTokenizer.tokenCountFor(testPrompt.messages[1])
        promptTokenizer.tokenCountFor(testPrompt.messages[2])
        assertEquals(2, promptTokenizer.cache.size)
        promptTokenizer.tokenCountFor(testPrompt)
        assertEquals(3, promptTokenizer.cache.size)
    }

    @Test
    fun testTokenizerInAgents() {
        val testToolRegistry = ToolRegistry {
            tool(TestTool1)
            tool(TestTool2)
        }

        val testPromptExecutor = getMockExecutor {
            mockLLMToolCall(TestTool1, TestTool.Args("What is the capital of France?")) onRequestEquals "France"
            mockTool(TestTool1) alwaysTells { "I don't know. And what is the capital of Spain?" }

            mockLLMToolCall(
                TestTool2,
                TestTool.Args("What is the capital of Spain?")
            ) onRequestEquals "I don't know. And what is the capital of Spain?"
            mockTool(TestTool2) alwaysTells { "Madrid" }

            mockLLMAnswer("Madrid is the final answer!") onRequestContains "Madrid"
        }

        val testStrategy = strategy("test") {
            val callLLM by nodeLLMRequest()
            val callTool by nodeExecuteTool()
            val sendToolResul by nodeLLMSendToolResult()

            val checkTokens by node<String, String> {
                val totalTokens = llm.readSession {
                    tokenizer.tokenCountFor(prompt)
                }

                "Total tokens: $totalTokens"
            }

            edge(nodeStart forwardTo callLLM)
            edge(callLLM forwardTo callTool onToolCall { true })
            edge(callLLM forwardTo checkTokens onAssistantMessage { true })
            edge(callTool forwardTo sendToolResul)
            edge(sendToolResul forwardTo callTool onToolCall { true })
            edge(sendToolResul forwardTo checkTokens onAssistantMessage { true })
            edge(checkTokens forwardTo nodeFinish)
        }

        val testConfig = AIAgentConfig(
            prompt = prompt("test-prompt") {
                system("You are a helpful assistant that helps with country capitals.")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 100
        )

        val agent = AIAgent(
            promptExecutor = testPromptExecutor,
            strategy = testStrategy,
            agentConfig = testConfig,
            toolRegistry = testToolRegistry
        ) {
            install(MessageTokenizer) {
                tokenizer = MockTokenizer()
            }
            withTesting()
        }

        val expectedTokens = with(MockTokenizer()) {
            countTokens("You are a helpful assistant that helps with country capitals.")
            countTokens("France")
            countTokens("{\"question\":\"What is the capital of France?\"}")
            countTokens("I don't know. And what is the capital of Spain?")
            countTokens("{\"question\":\"What is the capital of Spain?\"}")
            countTokens("Madrid")
            countTokens("Madrid is the final answer!")

            totalTokens
        }

        runBlocking {
            val result = agent.run("France")

            println(result)

            assertEquals("Total tokens: $expectedTokens", result)
        }
    }
}
