package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.mock.MockAttribute
import ai.koog.agents.features.opentelemetry.mock.MockGenAIAgentEvent
import ai.koog.agents.features.opentelemetry.mock.MockGenAIAgentSpan
import ai.koog.agents.features.opentelemetry.mock.MockTracer
import io.opentelemetry.api.trace.StatusCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpanProcessorTest {

    @Test
    fun `spanProcessor should have zero spans initially`() {
        val spanProcessor = SpanProcessor(MockTracer())
        assertEquals(0, spanProcessor.spansCount)
    }

    @Test
    fun `startSpan should add span to processor`() {
        val spanProcessor = SpanProcessor(MockTracer())
        val spanId = "test-span-id"
        val span = MockGenAIAgentSpan(spanId)

        spanProcessor.startSpan(span)

        assertEquals(1, spanProcessor.spansCount)
    }

    @Test
    fun `getSpan should return span by id when it exists`() {
        val spanProcessor = SpanProcessor(MockTracer())
        val spanId = "test-span-id"
        val span = MockGenAIAgentSpan(spanId)

        spanProcessor.startSpan(span)
        assertEquals(1, spanProcessor.spansCount)

        val actualSpan = spanProcessor.getSpan<GenAIAgentSpan>(spanId)
        assertEquals(spanId, actualSpan?.spanId)
    }

    @Test
    fun `getSpan should return null when no spans are added`() {
        val spanProcessor = SpanProcessor(MockTracer())
        val spanId = "test-span-id"
        assertEquals(0, spanProcessor.spansCount)

        val retrievedSpan = spanProcessor.getSpan<GenAIAgentSpan>(spanId)

        assertNull(retrievedSpan)
        assertEquals(0, spanProcessor.spansCount)
    }

    @Test
    fun `getSpan should return null when span with given id not found`() {
        val spanProcessor = SpanProcessor(MockTracer())

        val spanId = "test-span-id"
        val span = MockGenAIAgentSpan(spanId)
        spanProcessor.startSpan(span)
        assertEquals(1, spanProcessor.spansCount)

        val nonExistentSpanId = "non-existent-span"
        val retrievedSpan = spanProcessor.getSpan<GenAIAgentSpan>(nonExistentSpanId)

        assertNull(retrievedSpan)
        assertEquals(1, spanProcessor.spansCount)
    }

    @Test
    fun `getSpanOrThrow should return span when it exists`() {
        val spanProcessor = SpanProcessor(MockTracer())
        val spanId = "test-span-id"
        val span = MockGenAIAgentSpan(spanId)
        assertEquals(0, spanProcessor.spansCount)

        spanProcessor.startSpan(span)
        assertEquals(1, spanProcessor.spansCount)
        val retrievedSpan = spanProcessor.getSpanOrThrow<GenAIAgentSpan>(spanId)

        assertEquals(spanId, retrievedSpan.spanId)
        assertEquals(1, spanProcessor.spansCount)
    }

    @Test
    fun `getSpanOrThrow should throw when span not found`() {
        val spanProcessor = SpanProcessor(MockTracer())
        val nonExistentSpanId = "non-existent-span"
        assertEquals(0, spanProcessor.spansCount)

        val exception = assertFailsWith<IllegalStateException> {
            spanProcessor.getSpanOrThrow<GenAIAgentSpan>(nonExistentSpanId)
        }
        assertEquals("Span with id: $nonExistentSpanId not found", exception.message)
        assertEquals(0, spanProcessor.spansCount)
    }

    @Test
    fun `getSpanOrThrow should throw when span is of wrong type`() {
        val spanProcessor = SpanProcessor(MockTracer())
        val spanId = "test-span-id"
        val span = MockGenAIAgentSpan(spanId)
        assertEquals(0, spanProcessor.spansCount)

        spanProcessor.startSpan(span)
        assertEquals(1, spanProcessor.spansCount)

        // We can't test this with our mock span since we can't create different types,
        // but we can verify the error message format by creating a fake exception
        val throwable = assertThrows<IllegalStateException> {
            spanProcessor.getSpanOrThrow<InvokeAgentSpan>(spanId)
        }

        assertEquals(
            "Span with id <$spanId> is not of expected type. Expected: <${InvokeAgentSpan::class.simpleName}>, actual: <${MockGenAIAgentSpan::class.simpleName}>",
            throwable.message
        )

        assertEquals(1, spanProcessor.spansCount)
    }

    @Test
    fun `endSpan should remove span from processor`() {
        val spanProcessor = SpanProcessor(MockTracer())
        val spanId = "test-span-id"
        val span = MockGenAIAgentSpan(spanId)
        assertEquals(0, spanProcessor.spansCount)

        spanProcessor.startSpan(span)
        assertEquals(1, spanProcessor.spansCount)

        spanProcessor.endSpan(spanId)
        assertEquals(0, spanProcessor.spansCount)

        val retrievedSpan = spanProcessor.getSpan<GenAIAgentSpan>(spanId)
        assertNull(retrievedSpan)
        assertEquals(0, spanProcessor.spansCount)
    }

    @Test
    fun `endSpan should throw when span not found`() {
        val spanProcessor = SpanProcessor(MockTracer())
        val nonExistentSpanId = "non-existent-span"
        assertEquals(0, spanProcessor.spansCount)

        val throwable = assertThrows<IllegalStateException> {
            spanProcessor.endSpan(nonExistentSpanId)
        }

        assertEquals(
            "Span with id '$nonExistentSpanId' not found. Make sure span was started or was not finished previously",
            throwable.message
        )
        assertEquals(0, spanProcessor.spansCount)
    }

    @Test
    fun `endUnfinishedSpans should end spans that match the filter`() {
        val spanProcessor = SpanProcessor(MockTracer())

        // Create spans with different IDs
        val span1Id = "span1"
        val span2Id = "span2"
        val span3Id = "span3"

        // Create and start spans
        val span1 = MockGenAIAgentSpan(span1Id)
        spanProcessor.startSpan(span1)

        val span2 = MockGenAIAgentSpan(span2Id)
        spanProcessor.startSpan(span2)

        val span3 = MockGenAIAgentSpan(span3Id)
        spanProcessor.startSpan(span3)

        assertEquals(3, spanProcessor.spansCount)

        // End one of the spans
        spanProcessor.endSpan(span2.spanId)
        assertEquals(2, spanProcessor.spansCount)

        // Verify initial state
        assertTrue(span1.isStarted)
        assertFalse(span1.isEnded)
        assertTrue(span2.isStarted)
        assertTrue(span2.isEnded)
        assertTrue(span3.isStarted)
        assertFalse(span3.isEnded)

        // End spans that match the filter (only span1)
        spanProcessor.endUnfinishedSpans { id -> id == span1Id }

        // Verify span1 is ended, span2 was already ended, span3 is still not ended
        assertTrue(span1.isStarted)
        assertTrue(span1.isEnded)
        assertTrue(span2.isStarted)
        assertTrue(span2.isEnded)
        assertTrue(span3.isStarted)
        assertFalse(span3.isEnded)

        // End all remaining unfinished spans
        spanProcessor.endUnfinishedSpans()

        // Verify all spans are ended
        assertTrue(span1.isStarted)
        assertTrue(span1.isEnded)
        assertTrue(span2.isStarted)
        assertTrue(span2.isEnded)
        assertTrue(span3.isStarted)
        assertTrue(span3.isEnded)

        // Verify status code is set to UNSET for spans ended by endUnfinishedSpans
        assertEquals(StatusCode.UNSET, span1.currentStatus)
        assertEquals(StatusCode.UNSET, span3.currentStatus)
    }

    @Test
    fun `endUnfinishedInvokeAgentSpans should end all spans except agent and agent run spans`() {
        val spanProcessor = SpanProcessor(MockTracer())

        val agentId = "test-agent"
        val runId = "test-run"

        val agentSpanId = CreateAgentSpan.createId(agentId)
        val agentRunSpanId = InvokeAgentSpan.createId(agentId, runId)
        val nodeSpanId = "agent.$agentId.run.$runId.node.testNode"
        val toolSpanId = "agent.$agentId.run.$runId.node.testNode.tool.testTool"

        // Create and start spans
        val agentSpan = MockGenAIAgentSpan(agentSpanId)
        val agentRunSpan = MockGenAIAgentSpan(agentRunSpanId)
        val nodeSpan = MockGenAIAgentSpan(nodeSpanId)
        val toolSpan = MockGenAIAgentSpan(toolSpanId)

        // Add spans to storage
        spanProcessor.startSpan(agentSpan)
        spanProcessor.startSpan(agentRunSpan)
        spanProcessor.startSpan(nodeSpan)
        spanProcessor.startSpan(toolSpan)
        assertEquals(4, spanProcessor.spansCount)

        // Verify initial state - all spans are started but not ended
        assertTrue(agentSpan.isStarted)
        assertFalse(agentSpan.isEnded)
        assertTrue(agentRunSpan.isStarted)
        assertFalse(agentRunSpan.isEnded)
        assertTrue(nodeSpan.isStarted)
        assertFalse(nodeSpan.isEnded)
        assertTrue(toolSpan.isStarted)
        assertFalse(toolSpan.isEnded)

        // End unfinished agent run spans
        spanProcessor.endUnfinishedInvokeAgentSpans(agentId, runId)

        // Verify that node and tool spans are ended, but agent and agent run spans are not
        assertTrue(agentSpan.isStarted)
        assertFalse(agentSpan.isEnded)
        assertTrue(agentRunSpan.isStarted)
        assertFalse(agentRunSpan.isEnded)
        assertTrue(nodeSpan.isStarted)
        assertTrue(nodeSpan.isEnded)
        assertTrue(toolSpan.isStarted)
        assertTrue(toolSpan.isEnded)

        // Verify status code is set to UNSET for spans ended by endUnfinishedAgentRunSpans
        assertEquals(StatusCode.UNSET, nodeSpan.currentStatus)
        assertEquals(StatusCode.UNSET, toolSpan.currentStatus)
    }

    @Test
    fun `addEventsToSpan should add events to the span`() {
        val spanProcessor = SpanProcessor(MockTracer())
        val spanId = "test-span-id"
        val span = MockGenAIAgentSpan(spanId)

        // Start the span
        spanProcessor.startSpan(span)
        assertEquals(1, spanProcessor.spansCount)

        // Create test events
        val event1 = MockGenAIAgentEvent(
            name = "test_event_1",
            attributes = listOf(
                MockAttribute("key1", "value1"),
                MockAttribute("key2", 42)
            )
        )
        val event2 = MockGenAIAgentEvent(
            name = "test_event_2",
            attributes = listOf(
                MockAttribute("key3", true)
            )
        )

        // Add events to the span
        spanProcessor.addEventsToSpan(spanId, listOf(event1, event2))

        // Verify the span still exists
        assertEquals(1, spanProcessor.spansCount)
        val retrievedSpan = spanProcessor.getSpan<GenAIAgentSpan>(spanId)
        assertNotNull(retrievedSpan)
        assertEquals(spanId, retrievedSpan.spanId)
    }

    @Test
    fun `addEventsToSpan should throw when span not found`() {
        val spanProcessor = SpanProcessor(MockTracer())
        val nonExistentSpanId = "non-existent-span"

        // Create a test event
        val event = MockGenAIAgentEvent(
            name = "test_event",
            attributes = listOf(
                MockAttribute("key1", "value1")
            )
        )

        // Try to add event to the non-existent span
        val exception = assertFailsWith<IllegalStateException> {
            spanProcessor.addEventsToSpan(nonExistentSpanId, listOf(event))
        }

        assertEquals("Span with id '$nonExistentSpanId' not found", exception.message)
    }
}
