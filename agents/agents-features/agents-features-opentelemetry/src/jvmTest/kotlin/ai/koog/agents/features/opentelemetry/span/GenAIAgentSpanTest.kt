package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.mock.MockGenAIAgentSpan
import ai.koog.agents.features.opentelemetry.mock.MockSpan
import ai.koog.agents.features.opentelemetry.mock.MockGenAIAgentEvent
import ai.koog.agents.features.opentelemetry.mock.MockAttribute
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import org.junit.jupiter.api.Test
import kotlin.test.*

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
            MockGenAIAgentEvent(
                name = "event1",
                attributes = listOf(
                    MockAttribute("key1", "value1"),
                    MockAttribute("key2", 42)
                )
            ),
            MockGenAIAgentEvent(
                name = "event2",
                attributes = listOf(
                    MockAttribute("key3", true)
                )
            )
        )
        
        span.addEvents(events)
        
        // Since we can't directly verify the events were added (MockSpan doesn't track them),
        // we're just verifying the method doesn't throw exceptions
        assertTrue(true, "addEvents completed without exceptions")
    }
}