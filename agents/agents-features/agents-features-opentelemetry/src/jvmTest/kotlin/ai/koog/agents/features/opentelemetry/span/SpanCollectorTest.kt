package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.features.opentelemetry.assertMapsEqual
import ai.koog.agents.features.opentelemetry.event.EventBodyFields
import ai.koog.agents.features.opentelemetry.mock.MockAttribute
import ai.koog.agents.features.opentelemetry.mock.MockGenAIAgentEvent
import ai.koog.agents.features.opentelemetry.mock.MockSpan
import ai.koog.agents.features.opentelemetry.mock.MockTracer
import ai.koog.agents.utils.HiddenString
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SpanCollectorTest {

    @Test
    fun `spanProcessor should have zero spans initially`() {
        val spanCollector = SpanCollector()
        assertEquals(0, spanCollector.activeSpansCount)
    }

    @Test
    fun `collectSpan should add span to processor`() {
        val spanCollector = SpanCollector()
        val tracer = MockTracer()
        val spanId = "test-span-id"
        val spanName = "test-span-name"
        val span = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = spanId,
            name = spanName,
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)
        val executionInfo = AgentExecutionInfo(null, "test")

        spanCollector.collectSpan(span, executionInfo)

        assertEquals(1, spanCollector.activeSpansCount)
    }

    @Test
    fun `getSpan should return span by id when it exists`() {
        val spanCollector = SpanCollector()
        val tracer = MockTracer()
        val spanId = "test-span-id"
        val spanName = "test-span-name"
        val span = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = spanId,
            name = spanName,
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)
        val executionInfo = AgentExecutionInfo(null, "test")

        spanCollector.collectSpan(span, executionInfo)
        assertEquals(1, spanCollector.activeSpansCount)

        val actualSpan = spanCollector.getSpan(executionInfo)
        assertEquals(spanId, actualSpan?.id)
    }

    @Test
    fun `getSpan should return null when no spans are added`() {
        val spanCollector = SpanCollector()
        val executionInfo = AgentExecutionInfo(null, "test")
        assertEquals(0, spanCollector.activeSpansCount)

        val retrievedSpan = spanCollector.getSpan(executionInfo)

        assertNull(retrievedSpan)
        assertEquals(0, spanCollector.activeSpansCount)
    }

    @Test
    fun `getSpan should return null when span with given id not found`() {
        val spanCollector = SpanCollector()
        val tracer = MockTracer()

        val spanId = "test-span-id"
        val spanName = "test-span-name"
        val span = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = spanId,
            name = spanName,
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)

        val executionInfo = AgentExecutionInfo(null, "test")

        spanCollector.collectSpan(span, executionInfo)
        assertEquals(1, spanCollector.activeSpansCount)

        val nonExistentSpanExecutionInfo = AgentExecutionInfo(null, "non-existent-span")
        val retrievedSpan = spanCollector.getSpan(nonExistentSpanExecutionInfo)

        assertNull(retrievedSpan)
        assertEquals(1, spanCollector.activeSpansCount)
    }

    @Test
    fun `removeSpan should decrease active span count`() {
        val spanCollector = SpanCollector()
        val tracer = MockTracer()
        val spanId = "test-span-id"
        val spanName = "test-span-name"
        val span = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = spanId,
            name = spanName,
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)
        val executionInfo = AgentExecutionInfo(null, spanId)
        assertEquals(0, spanCollector.activeSpansCount)

        spanCollector.collectSpan(span, executionInfo)
        assertEquals(1, spanCollector.activeSpansCount)

        spanCollector.removeSpan(span, executionInfo)
        assertEquals(0, spanCollector.activeSpansCount)

        val retrievedSpan = spanCollector.getSpan(executionInfo)
        assertNull(retrievedSpan)
    }

    @Test
    fun `test mask HiddenString values in attributes when verbose set to false`() {
        val tracer = MockTracer()

        val spanId = "test-span-id"
        val spanName = "test-span-name"

        val span = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = spanId,
            name = spanName,
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)

        // Get the mock span instance
        val mockSpan = span.span as MockSpan
        val attributes = listOf(
            MockAttribute(key = "secretKey", value = HiddenString("super-secret")),
            MockAttribute(key = "arraySecretKey", value = listOf(HiddenString("one"), HiddenString("two"))),
            MockAttribute("regularKey", "visible")
        )

        span.addAttributes(attributes)

        // End span to append all created events
        span.end()

        // Verify exact converted values when verbose is set to 'false'
        val expectedAttributes = mapOf(
            AttributeKey.stringKey("secretKey") to HiddenString.HIDDEN_STRING_PLACEHOLDER,
            AttributeKey.stringArrayKey("arraySecretKey") to listOf(HiddenString.HIDDEN_STRING_PLACEHOLDER, HiddenString.HIDDEN_STRING_PLACEHOLDER),
            AttributeKey.stringKey("regularKey") to "visible"
        )

        assertEquals(expectedAttributes.size, mockSpan.collectedAttributes.size)
        assertMapsEqual(expectedAttributes, mockSpan.collectedAttributes)
    }

    @Test
    fun `test mask HiddenString values in event attributes and body fields with verbose set to false`() {
        val tracer = MockTracer()

        val spanId = "test-span-id"
        val spanName = "test-span-name"

        val span = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = spanId,
            name = spanName,
            kind = SpanKind.CLIENT,
        ).buildAndStart(tracer)

        val spanEvent = MockGenAIAgentEvent(name = "event").apply {
            addAttribute(MockAttribute("secretKey", HiddenString("secretValue")))
            addBodyField(EventBodyFields.Content("some sensitive content"))
        }

        span.addEvent(spanEvent)

        // End span to append all created events
        span.end()

        // Event with attribute HiddenString and a body field that contains HiddenString
        // Assert collected event
        val actualSpanEvents = (span.span as MockSpan).collectedEvents
        assertEquals(1, actualSpanEvents.size)
        val actualEventAttributes = actualSpanEvents.getValue("event").asMap()

        // Assert attributes for the collected event when the verbose flag is set to 'false'
        val expectedEventAttributes = mapOf(
            AttributeKey.stringKey("secretKey") to HiddenString.HIDDEN_STRING_PLACEHOLDER,
            AttributeKey.stringKey("content") to HiddenString.HIDDEN_STRING_PLACEHOLDER,
        )

        assertEquals(expectedEventAttributes.size, actualEventAttributes.size)
        assertMapsEqual(expectedEventAttributes, actualEventAttributes)
    }

    @Test
    fun `removeSpan should remove node from tree`() {
        val spanCollector = SpanCollector()
        val tracer = MockTracer()
        val spanId = "test-span-id"
        val spanName = "test-span-name"
        val span = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = spanId,
            name = spanName,
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)
        val executionInfo = AgentExecutionInfo(null, spanId)

        spanCollector.collectSpan(span, executionInfo)
        assertEquals(1, spanCollector.activeSpansCount)
        assertNotNull(spanCollector.getSpan(executionInfo))

        spanCollector.removeSpan(span, executionInfo)
        assertEquals(0, spanCollector.activeSpansCount)
        assertNull(spanCollector.getSpan(executionInfo))
    }

    @Test
    fun `removeSpan should throw exception when span has active children`() {
        val spanCollector = SpanCollector()
        val tracer = MockTracer()

        // Create parent and child spans
        val parentSpanId = "parent-span"
        val parentSpanName = "parent-span-name"
        val parentSpan = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = parentSpanId,
            name = parentSpanName,
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)
        val parentPath = AgentExecutionInfo(null, parentSpanId)

        val childSpanId = "child-span"
        val childSpanName = "child-span-name"
        val childSpan = GenAIAgentSpanBuilder(
            spanType = SpanType.INVOKE_AGENT,
            parentSpan = parentSpan,
            id = childSpanId,
            name = childSpanName,
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)
        val childPath = AgentExecutionInfo(parentPath, childSpanId)

        // Start both spans
        spanCollector.collectSpan(parentSpan, parentPath)
        spanCollector.collectSpan(childSpan, childPath)

        assertEquals(2, spanCollector.activeSpansCount)

        // Try to end parent span while the child is still active
        val exception = assertFailsWith<IllegalStateException> {
            spanCollector.removeSpan(parentSpan, parentPath)
        }

        val expectedError =
            "${parentSpan.logString} Error deleting span node from the tree (path: ${parentPath.path()}). " +
                "Node still have <1> child span(s). Spans:\n" +
                " - ${childSpan.logString}, active: ${childSpan.span.isRecording}"

        val actualError = exception.message
        assertNotNull(actualError)
        assertEquals(expectedError, actualError)

        assertEquals(2, spanCollector.activeSpansCount)
    }

    @Test
    fun `removeSpan should succeed when child spans are ended first`() {
        val spanCollector = SpanCollector()
        val tracer = MockTracer()

        // Create parent and child spans
        val parentSpanId = "parent-span"
        val parentSpanName = "parent-span-name"
        val parentSpan = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = parentSpanId,
            name = parentSpanName,
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)
        val parentPath = AgentExecutionInfo(null, parentSpanId)

        val childSpanId = "child-span"
        val childSpanName = "child-span-name"
        val childSpan = GenAIAgentSpanBuilder(
            spanType = SpanType.INVOKE_AGENT,
            parentSpan = parentSpan,
            id = childSpanId,
            name = childSpanName,
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)
        val childPath = AgentExecutionInfo(parentPath, childSpanId)

        // Start both spans
        spanCollector.collectSpan(parentSpan, parentPath)
        spanCollector.collectSpan(childSpan, childPath)

        assertEquals(2, spanCollector.activeSpansCount)

        // End child span first
        spanCollector.removeSpan(childSpan, childPath)
        assertEquals(1, spanCollector.activeSpansCount)
        assertNull(spanCollector.getSpan(childPath))

        // Now parent can be ended
        spanCollector.removeSpan(parentSpan, parentPath)
        assertEquals(0, spanCollector.activeSpansCount)
        assertNull(spanCollector.getSpan(parentPath))
    }

    @Test
    fun `tree should maintain only active spans`() {
        val spanCollector = SpanCollector()
        val tracer = MockTracer()

        // Create a tree: parent -> child1, child2
        val parentSpanId = "parent"
        val parentSpan = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = parentSpanId,
            name = "parent-span",
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)
        val parentPath = AgentExecutionInfo(null, parentSpanId)

        val child1SpanId = "child1"
        val child1Span = GenAIAgentSpanBuilder(
            spanType = SpanType.INVOKE_AGENT,
            parentSpan = parentSpan,
            id = child1SpanId,
            name = "child1-span",
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)
        val child1Path = AgentExecutionInfo(parentPath, child1SpanId)

        val child2SpanId = "child2"
        val child2Span = GenAIAgentSpanBuilder(
            spanType = SpanType.NODE,
            parentSpan = parentSpan,
            id = child2SpanId,
            name = "child2-span",
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)
        val child2Path = AgentExecutionInfo(parentPath, child2SpanId)

        // Start all spans
        spanCollector.collectSpan(parentSpan, parentPath)
        spanCollector.collectSpan(child1Span, child1Path)
        spanCollector.collectSpan(child2Span, child2Path)

        assertEquals(3, spanCollector.activeSpansCount)

        // End child1
        spanCollector.removeSpan(child1Span, child1Path)
        assertEquals(2, spanCollector.activeSpansCount)
        assertNull(spanCollector.getSpan(child1Path))
        assertNotNull(spanCollector.getSpan(child2Path))
        assertNotNull(spanCollector.getSpan(parentPath))

        // End child2
        spanCollector.removeSpan(child2Span, child2Path)
        assertEquals(1, spanCollector.activeSpansCount)
        assertNull(spanCollector.getSpan(child1Path))
        assertNull(spanCollector.getSpan(child2Path))
        assertNotNull(spanCollector.getSpan(parentPath))

        // End parent
        spanCollector.removeSpan(parentSpan, parentPath)
        assertEquals(0, spanCollector.activeSpansCount)
        assertNull(spanCollector.getSpan(parentPath))
    }

    @Test
    fun `removeSpan should handle multiple children properly`() {
        val spanCollector = SpanCollector()
        val tracer = MockTracer()

        // Create a tree: parent -> child1, child2, child3
        val parentSpanId = "parent"
        val parentSpan = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = parentSpanId,
            name = "parent-span",
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)
        val parentPath = AgentExecutionInfo(null, parentSpanId)

        spanCollector.collectSpan(parentSpan, parentPath)

        val childSpan1 = Pair(
            GenAIAgentSpanBuilder(
                spanType = SpanType.INVOKE_AGENT,
                parentSpan = parentSpan,
                id = "child-id-1",
                name = "child-span-1",
                kind = SpanKind.INTERNAL,
            ).buildAndStart(tracer),
            AgentExecutionInfo(parentPath, "child-id-1")
        )

        val childSpan2 = Pair(
            GenAIAgentSpanBuilder(
                spanType = SpanType.NODE,
                parentSpan = parentSpan,
                id = "child-id-2",
                name = "child-span-2",
                kind = SpanKind.INTERNAL,
            ).buildAndStart(tracer),
            AgentExecutionInfo(parentPath, "child-id-2")
        )

        val childSpan3 = Pair(
            GenAIAgentSpanBuilder(
                spanType = SpanType.SUBGRAPH,
                parentSpan = parentSpan,
                id = "child-id-3",
                name = "child-span-3",
                kind = SpanKind.INTERNAL,
            ).buildAndStart(tracer),
            AgentExecutionInfo(parentPath, "child-id-3")
        )

        // Start child spans
        val childSpans = listOf(childSpan1, childSpan2, childSpan3)
        childSpans.forEach { spanData ->
            spanCollector.collectSpan(spanData.first, spanData.second)
        }

        assertEquals((childSpans + parentSpan).size, spanCollector.activeSpansCount)

        // Try to end parent while child spans are active
        val exception = assertFailsWith<IllegalStateException> {
            spanCollector.removeSpan(parentSpan, parentPath)
        }

        val expectedError =
            "${parentSpan.logString} Error deleting span node from the tree (path: ${parentPath.path()}). " +
                "Node still have <3> child span(s). Spans:\n" +
                " - ${childSpan1.first.logString}, active: ${childSpan1.first.span.isRecording}\n" +
                " - ${childSpan2.first.logString}, active: ${childSpan2.first.span.isRecording}\n" +
                " - ${childSpan3.first.logString}, active: ${childSpan3.first.span.isRecording}"

        val actualError = exception.message
        assertNotNull(actualError)

        assertEquals(expectedError, actualError)

        // End all span children
        childSpans.forEach { child ->
            spanCollector.removeSpan(child.first, child.second)
        }

        assertEquals(1, spanCollector.activeSpansCount)

        // Now parent can be ended
        spanCollector.removeSpan(parentSpan, parentPath)
        assertEquals(0, spanCollector.activeSpansCount)
    }

    @Test
    fun `removeSpan should handle deep tree hierarchy`() {
        val spanCollector = SpanCollector()
        val tracer = MockTracer()

        // Create a deep tree: root -> level1 -> level2 -> level3
        val rootSpan = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "root",
            name = "root-span",
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)
        val rootPath = AgentExecutionInfo(null, "root")

        val level1Span = GenAIAgentSpanBuilder(
            spanType = SpanType.INVOKE_AGENT,
            parentSpan = rootSpan,
            id = "level1",
            name = "level1-span",
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)
        val level1Path = AgentExecutionInfo(rootPath, "level1")

        val level2Span = GenAIAgentSpanBuilder(
            spanType = SpanType.NODE,
            parentSpan = level1Span,
            id = "level2",
            name = "level2-span",
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)
        val level2Path = AgentExecutionInfo(level1Path, "level2")

        val level3Span = GenAIAgentSpanBuilder(
            spanType = SpanType.EXECUTE_TOOL,
            parentSpan = level2Span,
            id = "level3",
            name = "level3-span",
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)
        val level3Path = AgentExecutionInfo(level2Path, "level3")

        // Start all spans
        spanCollector.collectSpan(rootSpan, rootPath)
        spanCollector.collectSpan(level1Span, level1Path)
        spanCollector.collectSpan(level2Span, level2Path)
        spanCollector.collectSpan(level3Span, level3Path)

        assertEquals(4, spanCollector.activeSpansCount)

        // Try to end root - should fail
        assertFailsWith<IllegalStateException> {
            spanCollector.removeSpan(rootSpan, rootPath)
        }

        // Try to end level1 - should fail
        assertFailsWith<IllegalStateException> {
            spanCollector.removeSpan(level1Span, level1Path)
        }

        // Try to end level2 - should fail
        assertFailsWith<IllegalStateException> {
            spanCollector.removeSpan(level2Span, level2Path)
        }

        // End from deepest to root
        spanCollector.removeSpan(level3Span, level3Path)
        assertEquals(3, spanCollector.activeSpansCount)

        spanCollector.removeSpan(level2Span, level2Path)
        assertEquals(2, spanCollector.activeSpansCount)

        spanCollector.removeSpan(level1Span, level1Path)
        assertEquals(1, spanCollector.activeSpansCount)

        spanCollector.removeSpan(rootSpan, rootPath)
        assertEquals(0, spanCollector.activeSpansCount)
    }
}
