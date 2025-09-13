package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequestsStreaming
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for streaming event handlers.
 * These tests verify that the streaming handlers (onBeforeStream, onStreamFrame, onAfterStream)
 * are properly invoked during LLM streaming operations.
 */
class StreamingEventHandlerTest {

    @Test
    fun `test streaming event handlers are invoked`() = runBlocking {
        val eventsCollector = TestEventsCollector()
        val strategyName = "streaming-test-strategy"
        val userMessage = "Test streaming"
        val assistantResponse = "Streaming response"

        // Using nodeLLMRequestsStreaming to actually test streaming events
        val strategy = strategy<String, String>(strategyName) {
            val llmNode by nodeLLMRequestsStreaming("streaming-llm-node")

            edge(nodeStart forwardTo llmNode transformed { userMessage ->
                listOf(Message.User(userMessage, RequestMetaInfo.create(testClock)))
            })
            edge(llmNode forwardTo nodeFinish transformed { responses ->
                responses.map { it.content }.joinToString(", ")
            })
        }

        val mockExecutor = getMockExecutor(clock = testClock) {
            mockLLMAnswer(assistantResponse) onRequestContains userMessage
        }

        val agent = createAgent(
            strategy = strategy,
            promptExecutor = mockExecutor,
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        )

        agent.run(userMessage)
        agent.close()

        val runId = eventsCollector.runId

        // Verify events are captured
        assertTrue(eventsCollector.collectedEvents.isNotEmpty(), "Should have collected events")

        // Verify streaming events are captured when using nodeLLMRequestsStreaming
        val beforeStreamEvents = eventsCollector.collectedEvents.filter { it.contains("OnBeforeStream") }
        val streamFrameEvents = eventsCollector.collectedEvents.filter { it.contains("OnStreamFrame") }
        val afterStreamEvents = eventsCollector.collectedEvents.filter { it.contains("OnAfterStream") }

        assertTrue(beforeStreamEvents.isNotEmpty(), "Should have OnBeforeStream events")
        assertTrue(streamFrameEvents.isNotEmpty(), "Should have OnStreamFrame events")
        assertTrue(afterStreamEvents.isNotEmpty(), "Should have OnAfterStream events")

        // Verify the stream frame contains the expected response
        val frameWithContent = streamFrameEvents.firstOrNull { it.contains(assistantResponse) }
        assertTrue(frameWithContent != null, "Stream frame should contain the assistant response")
    }

    @Test
    fun `test streaming events are captured with actual streaming nodes`() = runBlocking {
        // This test verifies that streaming events are properly captured when using streaming nodes
        val eventsCollector = TestEventsCollector()
        val testMessage = "Generate a response about streaming"
        val testResponse = "This is a response about streaming functionality"

        // Create an agent that actually uses streaming nodes
        val strategy = strategy<String, String>("streaming-test-strategy-2") {
            val streamingNode by nodeLLMRequestsStreaming("actual-streaming-node")

            edge(nodeStart forwardTo streamingNode transformed { userMessage ->
                listOf(Message.User(userMessage, RequestMetaInfo.create(testClock)))
            })
            edge(streamingNode forwardTo nodeFinish transformed { responses ->
                responses.firstOrNull()?.content ?: "No response"
            })
        }

        val mockExecutor = getMockExecutor(clock = testClock) {
            mockLLMAnswer(testResponse) onRequestContains testMessage
        }

        val agent = createAgent(
            strategy = strategy,
            promptExecutor = mockExecutor,
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        )

        agent.run(testMessage)
        agent.close()

        // Verify that streaming events were captured
        val streamingEventTypes = listOf("OnBeforeStream", "OnStreamFrame", "OnAfterStream")
        streamingEventTypes.forEach { eventType ->
            val eventsOfType = eventsCollector.collectedEvents.filter { it.contains(eventType) }
            assertTrue(eventsOfType.isNotEmpty(), "Should have captured $eventType events")
        }

        // Verify the overall event collection is working
        assertTrue(eventsCollector.collectedEvents.isNotEmpty(), "Should have captured events")
    }
}
