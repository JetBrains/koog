package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.event.EventBodyFields
import ai.koog.agents.features.opentelemetry.mock.MockAttribute
import ai.koog.agents.features.opentelemetry.mock.MockGenAIAgentEvent
import ai.koog.agents.features.opentelemetry.mock.MockGenAIAgentSpan
import ai.koog.agents.features.opentelemetry.mock.MockSpan
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GenAIAgentSpanTest {

    @Test
    fun `constructor should initialize with parent`() {
        val parentSpan = MockGenAIAgentSpan("parent.span")
        val childSpan = MockGenAIAgentSpan("parent.span.child", parent = parentSpan)

        assertEquals(parentSpan, childSpan.parent)
    }

    @Test
    fun `constructor should initialize without parent`() {
        val span = MockGenAIAgentSpan("span")

        assertNull(span.parent)
    }

    @Test
    fun `name should return correct name without parent`() {
        val span = MockGenAIAgentSpan("test.span")

        assertEquals("test.span", span.spanId)
        assertEquals("test.span", span.name)
    }

    @Test
    fun `name should return correct name with parent`() {
        val parentSpan = MockGenAIAgentSpan("parent.span")
        val childSpan = MockGenAIAgentSpan("parent.span.child", parent = parentSpan)

        assertEquals("parent.span.child", childSpan.spanId)
        assertEquals("child", childSpan.name)
    }

    @Test
    fun `kind should return CLIENT by default`() {
        val span = MockGenAIAgentSpan("test.span")

        assertEquals(SpanKind.CLIENT, span.kind)
    }

    @Test
    fun `context should throw error when not initialized`() {
        val span = MockGenAIAgentSpan("test.span")

        val exception = assertFailsWith<IllegalStateException> {
            span.context
        }

        assertEquals("Context for span 'test.span' is not initialized", exception.message)
    }

    @Test
    fun `context should return value when initialized`() {
        val span = MockGenAIAgentSpan("test.span")
        val context = Context.root()

        span.context = context

        assertEquals(context, span.context)
    }

    @Test
    fun `span should throw error when not initialized`() {
        val span = MockGenAIAgentSpan("test.span")

        val exception = assertFailsWith<IllegalStateException> {
            span.span
        }

        assertEquals("Span 'test.span' is not started", exception.message)
    }

    @Test
    fun `span should return value when initialized`() {
        val span = MockGenAIAgentSpan("test.span")
        val mockSpan = MockSpan()

        span.span = mockSpan

        assertEquals(mockSpan, span.span)
    }

    @Test
    fun `addEvents should add events to span`() {
        val span = MockGenAIAgentSpan("test.span")
        val mockSpan = MockSpan()
        span.span = mockSpan

        val events = listOf(
            MockGenAIAgentEvent(name = "event1").apply {
                addAttribute(MockAttribute("key1", "value1"))
                addAttribute(MockAttribute("key2", 42))
            },
            MockGenAIAgentEvent(name = "event2").apply {
                addAttribute(MockAttribute("key3", true))
            },
        )

        events.forEach { event -> span.addEvent(event) }

        // Verify events were added to the internal events set
        assertEquals(2, span.events.size)
        assertTrue(span.events.contains(events[0]))
        assertTrue(span.events.contains(events[1]))
    }

    @Test
    fun `addEvents should handle duplicate events`() {
        val span = MockGenAIAgentSpan("test.span")
        val mockSpan = MockSpan()
        span.span = mockSpan

        val event = MockGenAIAgentEvent(name = "duplicate-event").apply {
            addAttribute(MockAttribute("key", "value"))
        }

        // Add the same event twice
        span.addEvent(event)
        span.addEvent(event)

        // Verify that both event were added
        assertEquals(2, span.events.size)
        assertTrue(span.events.contains(event))
    }

    @Test
    fun `addEvents should handle events with body fields`() {
        val span = MockGenAIAgentSpan("test.span")
        val mockSpan = MockSpan()
        span.span = mockSpan

        val event = MockGenAIAgentEvent(name = "event-with-body-fields").apply {
            addAttribute(MockAttribute("key", "value"))
            addBodyField(EventBodyFields.Content("test content"))
        }

        span.addEvent(event)

        // Verify the event was added
        assertEquals(1, span.events.size)
        assertTrue(span.events.contains(event))
    }

    @Test
    fun `addAttributes(list) should add multiple attributes to span`() {
        val span = MockGenAIAgentSpan("test.span")
        val mockSpan = MockSpan()
        span.span = mockSpan

        val attributes = listOf(
            MockAttribute("stringKey", "stringValue"),
            MockAttribute("numberKey", 123),
            MockAttribute("booleanKey", true)
        )

        span.addAttributes(attributes)

        assertEquals(3, span.attributes.size)
        assertTrue(span.attributes.containsAll(attributes))
    }

    @Test
    fun `addEvents(list) should add multiple events to span`() {
        val span = MockGenAIAgentSpan("test.span")
        val mockSpan = MockSpan()
        span.span = mockSpan

        val events = listOf(
            MockGenAIAgentEvent(name = "event1").apply { addAttribute(MockAttribute("stringKey", "stringValue")) },
            MockGenAIAgentEvent(name = "event2").apply { addAttribute(MockAttribute("numberKey", 2)) },
        )

        span.addEvents(events)

        assertEquals(2, span.events.size)
        assertTrue(span.events.containsAll(events))
    }
}
