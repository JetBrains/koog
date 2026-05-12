package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.mock.MockAttribute
import ai.koog.agents.features.opentelemetry.mock.MockContextFactory
import ai.koog.agents.features.opentelemetry.mock.MockTracer
import io.opentelemetry.kotlin.tracing.SpanKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GenAIAgentSpanTest {

    //region Constructor

    @Test
    fun `constructor should initialize with parent`() {
        val tracer = MockTracer()
        val contextFactory = MockContextFactory()

        val parentSpan = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "parent.span",
            kind = SpanKind.CLIENT,
            name = "parent.span.name"
        ).buildAndStart(tracer, contextFactory)

        val childSpan = GenAIAgentSpanBuilder(
            spanType = SpanType.NODE,
            parentSpan = parentSpan,
            id = "parent.span.child",
            kind = SpanKind.INTERNAL,
            name = "parent.span.child.name"
        ).buildAndStart(tracer, contextFactory)

        assertEquals(parentSpan, childSpan.parentSpan)
    }

    @Test
    fun `constructor should initialize without parent`() {
        val tracer = MockTracer()
        val contextFactory = MockContextFactory()

        val span = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "span",
            kind = SpanKind.CLIENT,
            name = "span.name"
        ).buildAndStart(tracer, contextFactory)

        assertNull(span.parentSpan)
    }

    //endregion Constructor

    //region Properties

    @Test
    fun `name should return correct name without parent`() {
        val tracer = MockTracer()
        val contextFactory = MockContextFactory()

        val span = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "test.span",
            kind = SpanKind.CLIENT,
            name = "test.span.name"
        ).buildAndStart(tracer, contextFactory)

        assertEquals("test.span", span.id)
        assertEquals("test.span.name", span.name)
    }

    @Test
    fun `name should return correct name with parent`() {
        val tracer = MockTracer()
        val contextFactory = MockContextFactory()

        val parentSpan = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "parent.span",
            kind = SpanKind.CLIENT,
            name = "parent.span.name"
        ).buildAndStart(tracer, contextFactory)

        val childSpan = GenAIAgentSpanBuilder(
            spanType = SpanType.NODE,
            parentSpan = parentSpan,
            id = "parent.span.child",
            kind = SpanKind.INTERNAL,
            name = "parent.span.child.name"
        ).buildAndStart(tracer, contextFactory)

        assertEquals("parent.span.child", childSpan.id)
        assertEquals("parent.span.child.name", childSpan.name)
    }

    @Test
    fun `kind should return CLIENT by default`() {
        val tracer = MockTracer()
        val contextFactory = MockContextFactory()

        val span = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "test.span",
            kind = SpanKind.CLIENT,
            name = "test.span.name"
        ).buildAndStart(tracer, contextFactory)

        assertEquals(SpanKind.CLIENT, span.kind)
    }

    @Test
    fun `context should return value when initialized`() {
        val tracer = MockTracer()
        val contextFactory = MockContextFactory()

        val span = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "test.span",
            kind = SpanKind.CLIENT,
            name = "test.span.name"
        ).buildAndStart(tracer, contextFactory)

        assertNotNull(span.context)
    }

    @Test
    fun `span should return value when initialized`() {
        val tracer = MockTracer()
        val contextFactory = MockContextFactory()

        val span = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "test.span",
            kind = SpanKind.CLIENT,
            name = "test.span.name"
        ).buildAndStart(tracer, contextFactory)

        assertNotNull(span.span)
    }

    //endregion Properties

    //region Add Attributes

    @Test
    fun `add multiple attributes to span`() {
        val tracer = MockTracer()
        val contextFactory = MockContextFactory()

        val span = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "test.span",
            kind = SpanKind.CLIENT,
            name = "test.span.name"
        ).buildAndStart(tracer, contextFactory)

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
        val contextFactory = MockContextFactory()

        val span = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "test.span",
            kind = SpanKind.CLIENT,
            name = "test.span.name"
        ).buildAndStart(tracer, contextFactory)

        val attribute1 = MockAttribute("key", "value1")
        val attribute2 = MockAttribute("key", "value2")
        span.addAttribute(attribute1)
        span.addAttribute(attribute2)

        assertEquals(1, span.attributes.size)
        assertEquals(attribute2, span.attributes.single())
    }

    //endregion Add Attributes
}
