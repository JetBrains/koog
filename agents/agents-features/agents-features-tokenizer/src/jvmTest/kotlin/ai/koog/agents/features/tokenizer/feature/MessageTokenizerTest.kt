package ai.koog.agents.features.tokenizer.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.feature.withTesting
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.tokenizer.Tokenizer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

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
                    tokenizer().tokenCountFor(prompt)
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
            val result = agent.run("France", null)

            println(result)

            assertEquals("Total tokens: $expectedTokens", result)
        }
    }
}
