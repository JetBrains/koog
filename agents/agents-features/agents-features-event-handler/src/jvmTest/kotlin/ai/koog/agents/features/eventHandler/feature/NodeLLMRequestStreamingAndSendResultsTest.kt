package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreamingAndSendResults
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for nodeLLMRequestStreamingAndSendResults function.
 * Verifies that the node correctly streams responses, collects them, and updates the prompt.
 */
class NodeLLMRequestStreamingAndSendResultsTest {

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
    fun `test nodeLLMRequestStreamingAndSendResults collects text responses`() = runBlocking {
        val eventsCollector = TestEventsCollector()
        val assistantResponse = "This is a streamed response that will be collected"

        val strategy = strategy<String, String>("streaming-collect-strategy") {
            val streamAndCollectNode by nodeLLMRequestStreamingAndSendResults<String>("stream-and-collect")

            edge(nodeStart forwardTo streamAndCollectNode)
            edge(
                streamAndCollectNode forwardTo nodeFinish transformed { messages ->
                    // Convert List<Message.Response> to String for the test
                    messages.firstOrNull()?.content ?: ""
                }
            )
        }

        val mockExecutor = getMockExecutor(clock = testClock) {
            // Match on the test user message from createAgent
            mockLLMAnswer(assistantResponse) onRequestContains "Test user message"
        }

        val agent = createStreamingTestAgent(
            strategy = strategy,
            promptExecutor = mockExecutor,
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        )

        val result = agent.run("input")
        agent.close()

        // Verify the result contains the assistant response
        assertEquals(assistantResponse, result, "Should contain the expected response")

        // Verify streaming events were captured
        val streamingEvents = eventsCollector.collectedEvents.filter {
            it.contains("OnBeforeStream") || it.contains("OnStreamFrame") || it.contains("OnAfterStream")
        }
        assertTrue(streamingEvents.isNotEmpty(), "Should have captured streaming events")
    }

    @Test
    fun `test nodeLLMRequestStreamingAndSendResults returns collected messages`() = runBlocking {
        val eventsCollector = TestEventsCollector()
        val assistantResponse = "Response from streaming LLM"

        val strategy = strategy<String, String>("streaming-response-strategy") {
            val streamNode by nodeLLMRequestStreamingAndSendResults<String>("stream-collect")

            edge(nodeStart forwardTo streamNode)
            edge(
                streamNode forwardTo nodeFinish transformed { messages ->
                    // Verify we got messages back
                    assertTrue(messages.isNotEmpty(), "Should have collected messages")
                    messages.firstOrNull()?.content ?: ""
                }
            )
        }

        val mockExecutor = getMockExecutor(clock = testClock) {
            mockLLMAnswer(assistantResponse) onRequestContains "Test user message"
        }

        val agent = createStreamingTestAgent(
            strategy = strategy,
            promptExecutor = mockExecutor,
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        )

        val result = agent.run("input")
        agent.close()

        // Verify the response was collected correctly
        assertEquals(assistantResponse, result, "Should return the streamed response")

        // Verify streaming events occurred
        val streamingEvents = eventsCollector.collectedEvents.filter {
            it.contains("OnBeforeStream") || it.contains("OnAfterStream")
        }
        assertTrue(streamingEvents.isNotEmpty(), "Should have streaming events")
    }

    @Test
    fun `test nodeLLMRequestStreamingAndSendResults with empty response`() = runBlocking {
        val eventsCollector = TestEventsCollector()

        val strategy = strategy<String, String>("empty-streaming-strategy") {
            val streamNode by nodeLLMRequestStreamingAndSendResults<String>("stream-empty")

            edge(nodeStart forwardTo streamNode)
            edge(
                streamNode forwardTo nodeFinish transformed { messages ->
                    messages.firstOrNull()?.content ?: ""
                }
            )
        }

        val mockExecutor = getMockExecutor(clock = testClock) {
            // Return empty response for test user message
            mockLLMAnswer("") onRequestContains "Test user message"
        }

        val agent = createStreamingTestAgent(strategy, promptExecutor = mockExecutor) {
            install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
        }

        val result = agent.run("input")
        agent.close()

        // Should return empty content
        assertEquals("", result, "Content should be empty")
    }

    // Define a data class for typed input
    data class TestData(val value: Int, val description: String)

    @Test
    fun `test nodeLLMRequestStreamingAndSendResults preserves input type`() = runBlocking {
        val eventsCollector = TestEventsCollector()
        val inputData = TestData(value = 42, description = "Test input")
        val assistantResponse = "Response for structured input"

        // Strategy that takes TestData as input to the streaming node
        val strategy = strategy<String, String>("typed-input-strategy") {
            val streamNode by nodeLLMRequestStreamingAndSendResults<TestData>("stream-typed")

            edge(
                nodeStart forwardTo streamNode transformed { userInput ->
                    // Transform String input to TestData
                    inputData
                }
            )
            edge(
                streamNode forwardTo nodeFinish transformed { messages ->
                    messages.firstOrNull()?.content ?: ""
                }
            )
        }

        val mockExecutor = getMockExecutor(clock = testClock) {
            mockLLMAnswer(assistantResponse) onRequestContains "Test user message"
        }

        val agent = createStreamingTestAgent(strategy, promptExecutor = mockExecutor) {
            install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
        }

        val result = agent.run("trigger")
        agent.close()

        // Verify the result
        assertEquals(assistantResponse, result, "Should contain the expected response")
    }
}
