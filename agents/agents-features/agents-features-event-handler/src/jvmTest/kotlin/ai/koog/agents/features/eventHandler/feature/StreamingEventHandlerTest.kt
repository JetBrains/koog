package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreaming
import ai.koog.agents.testing.tools.MockLLMBuilder
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.prompt.streaming.collectText
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for streaming functionality with event handlers.
 * These tests verify that LLM handlers (onBeforeLLMCall, onAfterLLMCall)
 * are properly invoked during LLM streaming operations.
 */
class StreamingEventHandlerTest {

    @Test
    fun `test streaming event handlers are invoked`() = runTest {
        val userMessage = "Test streaming"
        val assistantResponse = "Streaming response"
        // Using nodeLLMRequestStreaming to actually test streaming events
        val eventsCollector = mockStreaming(
            strategy = streamTextStrategy("streaming-test-strategy"),
            buildLlmMock = { mockLLMAnswer(assistantResponse) onRequestContains userMessage }
        ) { agent ->
            agent.run(userMessage)
        }

        // Verify events are captured
        assertEventsCollected(eventsCollector)

        // Verify LLM events are captured when using nodeLLMRequestsStreaming
        val beforeLLMEvents = eventsCollector.collectedEvents.filter { it.contains("OnBeforeLLMCall") }
        val afterLLMEvents = eventsCollector.collectedEvents.filter { it.contains("OnAfterLLMCall") }
        val streamFrameEvents = eventsCollector.collectedEvents.filter { it.contains("OnStreamFrame") }

        assertTrue(beforeLLMEvents.isNotEmpty(), "Should have OnBeforeLLMCall events for streaming")
        assertTrue(afterLLMEvents.isNotEmpty(), "Should have OnAfterLLMCall events for streaming")
        assertTrue(streamFrameEvents.isNotEmpty(), "Should have OnStreamFrame events")

        // Verify the stream frame contains the expected response
        val frameWithContent = streamFrameEvents.firstOrNull { it.contains(assistantResponse) }
        assertTrue(frameWithContent != null, "Stream frame should contain the assistant response")
    }

    @Test
    fun `test streaming events are captured with actual streaming nodes`() = runTest {
        // This test verifies that streaming events are properly captured when using streaming nodes
        val testMessage = "Generate a response about streaming"
        val testResponse = "This is a response about streaming functionality"
        val eventsCollector = mockStreaming(
            strategy = streamTextStrategy("streaming-test-strategy-2"),
            buildLlmMock = { mockLLMAnswer(testResponse) onRequestContains testMessage }
        ) { agent ->
            agent.run(testMessage)
        }
        // Verify the overall event collection is working
        assertEventsCollected(eventsCollector)
        // Verify that both LLM and streaming events were captured
        val eventTypes = listOf("OnBeforeLLMCall", "OnAfterLLMCall", "OnStreamFrame")
        assertTrue(
            actual = eventsCollector.collectedEvents.any { eventTypes.any(it::contains) },
            message = "Should have captured at least one event for streaming (${eventTypes.joinToString()})"
        )
    }
}

// Helpers

private fun assertEventsCollected(eventsCollector: TestEventsCollector) =
    assertTrue(eventsCollector.collectedEvents.isNotEmpty(), "Should have collected events")

private suspend fun mockStreaming(
    strategy: AIAgentGraphStrategy<String, String>,
    buildLlmMock: MockLLMBuilder.() -> Unit,
    runAgent: suspend (AIAgent<String, String>) -> Unit
): TestEventsCollector {
    val eventsCollector = TestEventsCollector()
    val agent: AIAgent<String, String> = createAgent(
        strategy = strategy,
        promptExecutor = getMockExecutor(clock = testClock) { buildLlmMock() }
    ) {
        install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
    }
    runAgent(agent)
    agent.close()
    return eventsCollector
}

private fun streamTextStrategy(strategyName: String) =
    strategy<String, String>(strategyName) {
        val llmNode by nodeLLMRequestStreaming("streaming-llm-node")
        edge(nodeStart forwardTo llmNode)
        edge(llmNode forwardTo nodeFinish transformed { it.collectText() })
    }
