package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.event.EventBodyFields
import ai.koog.agents.features.opentelemetry.mock.MockAttribute
import ai.koog.agents.features.opentelemetry.mock.MockGenAIAgentEvent
import ai.koog.agents.features.opentelemetry.mock.MockTracer
import io.opentelemetry.api.trace.SpanKind
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GenAIAgentSpanTest {

    //region Constructor

    @Test
    fun `constructor should initialize with parent`() {
        val tracer = MockTracer()

        val parentSpan = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "parent.span",
            kind = SpanKind.CLIENT,
            name = "parent.span.name"
        ).buildAndStart(tracer)

        val childSpan = GenAIAgentSpanBuilder(
            spanType = SpanType.NODE,
            parentSpan = parentSpan,
            id = "parent.span.child",
            kind = SpanKind.INTERNAL,
            name = "parent.span.child.name"
        ).buildAndStart(tracer)

        assertEquals(parentSpan, childSpan.parentSpan)
    }

    @Test
    fun `constructor should initialize without parent`() {
        val tracer = MockTracer()

        val span = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "span",
            kind = SpanKind.CLIENT,
            name = "span.name"
        ).buildAndStart(tracer)

        assertNull(span.parentSpan)
    }

    //endregion Constructor

    //region Properties

    @Test
    fun `name should return correct name without parent`() {
        val tracer = MockTracer()

        val span = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "test.span",
            kind = SpanKind.CLIENT,
            name = "test.span.name"
        ).buildAndStart(tracer)

        assertEquals("test.span", span.id)
        assertEquals("test.span.name", span.name)
    }

    @Test
    fun `name should return correct name with parent`() {
        val tracer = MockTracer()

        val parentSpan = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "parent.span",
            kind = SpanKind.CLIENT,
            name = "parent.span.name"
        ).buildAndStart(tracer)

        val childSpan = GenAIAgentSpanBuilder(
            spanType = SpanType.NODE,
            parentSpan = parentSpan,
            id = "parent.span.child",
            kind = SpanKind.INTERNAL,
            name = "parent.span.child.name"
        ).buildAndStart(tracer)

        assertEquals("parent.span.child", childSpan.id)
        assertEquals("parent.span.child.name", childSpan.name)
    }

    @Test
    fun `kind should return CLIENT by default`() {
        val tracer = MockTracer()

        val span = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "test.span",
            kind = SpanKind.CLIENT,
            name = "test.span.name"
        ).buildAndStart(tracer)

        assertEquals(SpanKind.CLIENT, span.kind)
    }

    @Test
    fun `context should return value when initialized`() {
        val tracer = MockTracer()

        val span = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "test.span",
            kind = SpanKind.CLIENT,
            name = "test.span.name"
        ).buildAndStart(tracer)

        assertNotNull(span.context)
    }

    @Test
    fun `span should return value when initialized`() {
        val tracer = MockTracer()

        val span = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "test.span",
            kind = SpanKind.CLIENT,
            name = "test.span.name"
        ).buildAndStart(tracer)

        assertNotNull(span.span)
    }

    //endregion Properties

    //region Add Events

    @Test
    fun `add valid events to span`() {
        val tracer = MockTracer()

        val span = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "test.span",
            kind = SpanKind.CLIENT,
            name = "test.span.name"
        ).buildAndStart(tracer)

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
    fun `add duplicate event should append`() {
        val tracer = MockTracer()

        val span = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "test.span",
            kind = SpanKind.CLIENT,
            name = "test.span.name"
        ).buildAndStart(tracer)

        val event = MockGenAIAgentEvent(name = "duplicate-event").apply {
            addAttribute(MockAttribute("key", "value"))
        }

        // Add the same event twice
        span.addEvent(event)
        span.addEvent(event)

        // Verify that both events were added
        assertEquals(2, span.events.size)
        assertTrue(span.events.contains(event))
    }

    @Test
    fun `add events with body fields`() {
        val tracer = MockTracer()

        val span = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "test.span",
            kind = SpanKind.CLIENT,
            name = "test.span.name"
        ).buildAndStart(tracer)

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
    fun `add multiple events to span`() {
        val tracer = MockTracer()

        val span = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "test.span",
            kind = SpanKind.CLIENT,
            name = "test.span.name"
        ).buildAndStart(tracer)

        val events = listOf(
            MockGenAIAgentEvent(name = "event1").apply { addAttribute(MockAttribute("stringKey", "stringValue")) },
            MockGenAIAgentEvent(name = "event2").apply { addAttribute(MockAttribute("numberKey", 2)) },
        )

        span.addEvents(events)

        assertEquals(2, span.events.size)
        assertTrue(span.events.containsAll(events))
    }

    //endregion Add Events

    //region Add Attributes

    @Test
    fun `add multiple attributes to span`() {
        val tracer = MockTracer()

        val span = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "test.span",
            kind = SpanKind.CLIENT,
            name = "test.span.name"
        ).buildAndStart(tracer)

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
    fun `add duplicate attribute should override value`() {
        val tracer = MockTracer()

        val span = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "test.span",
            kind = SpanKind.CLIENT,
            name = "test.span.name"
        ).buildAndStart(tracer)

        val attribute1 = MockAttribute("key", "value1")
        val attribute2 = MockAttribute("key", "value2")
        span.addAttribute(attribute1)
        span.addAttribute(attribute2)

        assertEquals(1, span.attributes.size)
        assertEquals(attribute2, span.attributes.single())
    }

    //endregion Add Attributes
}
