package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreaming
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.streaming.collectText
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.utils.io.use
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for nodeLLMRequestStreaming function.
 * Verifies that the node correctly streams responses, collects them, and updates the prompt.
 */
class NodeLLMRequestStreamingAndSendResultsTest {
    private val serializer = KotlinxSerializer()

    // Helper function to create agent without assistant message in initial prompt
    private fun createStreamingTestAgent(
        strategy: AIAgentGraphStrategy<String, String>,
        promptExecutor: PromptExecutor,
        installFeatures: GraphAIAgent.FeatureContext.() -> Unit = { }
    ): AIAgent<String, String> {
        val agentConfig = AIAgentConfig(
            prompt = prompt("test", clock = testClock) {
                system("Test system message")
                user("Test user message")
                // No assistant message here to avoid mock executor issues
            },
            model = OpenAIModels.Chat.GPT4o,
            maxAgentIterations = 10
        )

        return AIAgent(
            id = "test-agent-id",
            promptExecutor = promptExecutor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry { },
            clock = testClock,
            installFeatures = installFeatures
        )
    }

    @Test
    fun `test nodeLLMRequestStreaming collects text responses`() = runTest {
        val eventsCollector = TestEventsCollector()
        val assistantResponse = "This is a streamed response that will be collected"

        val strategy = strategy<String, String>("streaming-collect-strategy") {
            val streamAndCollectNode by nodeLLMRequestStreaming("stream-and-collect")

            edge(nodeStart forwardTo streamAndCollectNode)
            edge(streamAndCollectNode forwardTo nodeFinish transformed { it.collectText() })
        }

        val mockExecutor = getMockExecutor(serializer, clock = testClock) {
            // Match on the test user message from createAgent
            mockLLMAnswer(assistantResponse).asDefaultResponse
        }

        createStreamingTestAgent(
            strategy = strategy,
            promptExecutor = mockExecutor,
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        ).use { agent ->
            val result = agent.run("input", null)
            // Verify the result contains the assistant response
            assertEquals(assistantResponse, result, "Should contain the expected response")
        }

        // Verify streaming events were captured
        val streamingEvents = eventsCollector.collectedEvents.filter {
            it.contains("OnLLMStreamingStarting") ||
                it.contains("OnLLMStreamingFrameReceived") ||
                it.contains("OnLLMStreamingCompleted")
        }
        assertTrue(streamingEvents.isNotEmpty(), "Should have captured streaming events")
    }

    @Test
    fun `test nodeLLMRequestStreaming returns collected messages`() = runTest {
        val eventsCollector = TestEventsCollector()
        val assistantResponse = "Response from streaming LLM"

        val strategy = strategy<String, String>("streaming-response-strategy") {
            val streamNode by nodeLLMRequestStreaming("stream-collect")

            edge(nodeStart forwardTo streamNode)
            edge(streamNode forwardTo nodeFinish transformed { it.collectText() })
        }

        val mockExecutor = getMockExecutor(serializer, clock = testClock) {
            mockLLMAnswer(assistantResponse).asDefaultResponse
        }

        createStreamingTestAgent(
            strategy = strategy,
            promptExecutor = mockExecutor,
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        ).use { agent ->
            val result = agent.run("input", null)
            // Verify the response was collected correctly
            assertEquals(assistantResponse, result, "Should return the streamed response")
        }

        // Verify streaming events occurred
        val streamingEvents = eventsCollector.collectedEvents.filter {
            it.contains("OnLLMStreamingStarting") || it.contains("OnLLMStreamingCompleted")
        }
        assertTrue(streamingEvents.isNotEmpty(), "Should have streaming events")
    }

    @Test
    fun `test nodeLLMRequestStreaming with empty response`() = runTest {
        val eventsCollector = TestEventsCollector()

        val strategy = strategy<String, String>("empty-streaming-strategy") {
            val streamNode by nodeLLMRequestStreaming("stream-empty")

            edge(nodeStart forwardTo streamNode)
            edge(streamNode forwardTo nodeFinish transformed { it.collectText() })
        }

        val mockExecutor = getMockExecutor(serializer, clock = testClock) {
            // Return empty content by default; executeStreaming falls back to converting this to a stream.
            mockLLMAnswer("").asDefaultResponse
        }

        createStreamingTestAgent(
            strategy = strategy,
            promptExecutor = mockExecutor,
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        ).use { agent ->
            val result = agent.run("input", null)
            // Should return empty content
            assertEquals("", result, "Content should be empty")
        }
    }

    // Define a data class for typed input
    data class TestData(val value: Int, val description: String)

    @Test
    fun `test nodeLLMRequestStreaming preserves input transformation`() = runTest {
        val eventsCollector = TestEventsCollector()
        val inputData = TestData(value = 42, description = "Test input")
        val assistantResponse = "Response for structured input"

        // Strategy that transforms String input into a TestData value, then formats it
        // into a Message.User for the streaming node.
        val strategy = strategy<String, String>("typed-input-strategy") {
            val streamNode by nodeLLMRequestStreaming("stream-typed")

            edge(
                nodeStart forwardTo streamNode
                    transformed { inputData.description }
            )
            edge(streamNode forwardTo nodeFinish transformed { it.collectText() })
        }

        val mockExecutor = getMockExecutor(serializer, clock = testClock) {
            mockLLMAnswer(assistantResponse).asDefaultResponse
        }

        createStreamingTestAgent(
            strategy = strategy,
            promptExecutor = mockExecutor,
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        ).use { agent ->
            val result = agent.run("trigger", null)
            // Verify the result
            assertEquals(assistantResponse, result, "Should contain the expected response")
        }
    }
}
