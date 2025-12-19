package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.features.opentelemetry.assertMapsEqual
import ai.koog.agents.features.opentelemetry.event.EventBodyFields
import ai.koog.agents.features.opentelemetry.mock.MockAttribute
import ai.koog.agents.features.opentelemetry.mock.MockGenAIAgentEvent
import ai.koog.agents.features.opentelemetry.mock.MockGenAIAgentSpan
import ai.koog.agents.features.opentelemetry.mock.MockSpan
import ai.koog.agents.features.opentelemetry.mock.MockTracer
import ai.koog.agents.utils.HiddenString
import io.opentelemetry.api.common.AttributeKey
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpanCollectorTest {

    @Test
    fun `spanProcessor should have zero spans initially`() {
        val spanCollector = SpanCollector(MockTracer(), verbose = true)
        assertEquals(0, spanCollector.activeSpansCount)
    }

    @Test
    fun `startSpan should add span to processor`() {
        val spanCollector = SpanCollector(MockTracer(), verbose = true)
        val spanId = "test-span-id"
        val spanName = "test-span-name"
        val span = MockGenAIAgentSpan(spanId, spanName)
        val executionInfo = AgentExecutionInfo(null, "test")

        spanCollector.startSpan(span, executionInfo)

        assertEquals(1, spanCollector.activeSpansCount)
    }

    @Test
    fun `getSpan should return span by id when it exists`() {
        val spanCollector = SpanCollector(MockTracer(), verbose = true)
        val spanId = "test-span-id"
        val spanName = "test-span-name"
        val span = MockGenAIAgentSpan(spanId, spanName)
        val executionInfo = AgentExecutionInfo(null, "test")

        spanCollector.startSpan(span, executionInfo)
        assertEquals(1, spanCollector.activeSpansCount)

        val actualSpan = spanCollector.getSpan(executionInfo)
        assertEquals(spanId, actualSpan?.id)
    }

    @Test
    fun `getSpan should return null when no spans are added`() {
        val spanCollector = SpanCollector(MockTracer(), verbose = true)
        val executionInfo = AgentExecutionInfo(null, "test")
        assertEquals(0, spanCollector.activeSpansCount)

        val retrievedSpan = spanCollector.getSpan(executionInfo)

        assertNull(retrievedSpan)
        assertEquals(0, spanCollector.activeSpansCount)
    }

    @Test
    fun `getSpan should return null when span with given id not found`() {
        val spanCollector = SpanCollector(MockTracer(), verbose = true)

        val spanId = "test-span-id"
        val spanName = "test-span-name"
        val span = MockGenAIAgentSpan(spanId, spanName)
        val executionInfo = AgentExecutionInfo(null, "test")

        spanCollector.startSpan(span, executionInfo)
        assertEquals(1, spanCollector.activeSpansCount)

        val nonExistentSpanExecutionInfo = AgentExecutionInfo(null, "non-existent-span")
        val retrievedSpan = spanCollector.getSpan(nonExistentSpanExecutionInfo)

        assertNull(retrievedSpan)
        assertEquals(1, spanCollector.activeSpansCount)
    }

    @Test
    fun `endSpan should decrease active span count`() {
        val spanCollector = SpanCollector(MockTracer(), verbose = true)
        val spanId = "test-span-id"
        val spanName = "test-span-name"
        val span = MockGenAIAgentSpan(spanId, spanName)
        val executionInfo = AgentExecutionInfo(null, spanId)
        assertEquals(0, spanCollector.activeSpansCount)

        spanCollector.startSpan(span, executionInfo)
        assertEquals(1, spanCollector.activeSpansCount)

        spanCollector.endSpan(span, executionInfo)
        assertEquals(0, spanCollector.activeSpansCount)

        val retrievedSpan = spanCollector.getSpan(executionInfo)
        assertNull(retrievedSpan)
    }

    @Test
    fun `endUnfinishedSpans should end spans that match the filter`() {
        val spanCollector = SpanCollector(MockTracer(), verbose = true)

        // Create spans with different IDs
        val span1Id = "span1"
        val span1Name = "test1-span-name"
        val span2Id = "span2"
        val span2Name = "test2-span-name"
        val span3Id = "span3"
        val span3Name = "test3-span-name"

        // Create and start spans
        val span1 = MockGenAIAgentSpan(span1Id, span1Name)
        val path1 = AgentExecutionInfo(null, span1Id)
        spanCollector.startSpan(span1, path1)

        val span2 = MockGenAIAgentSpan(span2Id, span2Name)
        val path2 = AgentExecutionInfo(null, span2Id)
        spanCollector.startSpan(span2, path2)

        val span3 = MockGenAIAgentSpan(span3Id, span3Name)
        val path3 = AgentExecutionInfo(null, span3Id)
        spanCollector.startSpan(span3, path3)

        assertEquals(3, spanCollector.activeSpansCount)

        // End one of the spans
        spanCollector.endSpan(span2, path2)
        assertEquals(2, spanCollector.activeSpansCount)

        // Verify initial state
        assertTrue(span1.isStarted)
        assertFalse(span1.isEnded)
        assertTrue(span2.isStarted)
        assertTrue(span2.isEnded)
        assertTrue(span3.isStarted)
        assertFalse(span3.isEnded)

        // End spans that match the filter (only span1)
        spanCollector.endUnfinishedSpans { span -> span.id == span1Id }

        // Verify span1 is ended, span2 was already ended, span3 is still not ended
        assertTrue(span1.isStarted)
        assertTrue(span1.isEnded)
        assertTrue(span2.isStarted)
        assertTrue(span2.isEnded)
        assertTrue(span3.isStarted)
        assertFalse(span3.isEnded)

        // End all remaining unfinished spans
        spanCollector.endUnfinishedSpans()

        // Verify all spans are ended
        assertTrue(span1.isStarted)
        assertTrue(span1.isEnded)
        assertTrue(span2.isStarted)
        assertTrue(span2.isEnded)
        assertTrue(span3.isStarted)
        assertTrue(span3.isEnded)
    }

    @Test
    fun `endUnfinishedSpans should end all spans`() {
        val spanCollector = SpanCollector(MockTracer(), verbose = true)

        val agentId = "test-agent"
        val runId = "test-run"

        val agentSpanId = agentId
        val agentSpanName = "agent-span-name"

        val agentRunSpanId = runId
        val agentRunSpanName = "agent-run-span-name"

        val nodeSpanId = "testNode"
        val nodeSpanName = "node-span-name"

        val toolSpanId = "testTool"
        val toolSpanName = "tool-span-name"

        // Create paths
        val agentPath = AgentExecutionInfo(null, agentSpanId)
        val agentRunPath = AgentExecutionInfo(agentPath, agentRunSpanId)
        val nodePath = AgentExecutionInfo(agentRunPath, nodeSpanId)
        val toolPath = AgentExecutionInfo(nodePath, toolSpanId)

        // Create and start spans
        val agentSpan = MockGenAIAgentSpan(agentSpanId, agentSpanName)
        val agentRunSpan = MockGenAIAgentSpan(agentRunSpanId, agentRunSpanName)
        val nodeSpan = MockGenAIAgentSpan(nodeSpanId, nodeSpanName)
        val toolSpan = MockGenAIAgentSpan(toolSpanId, toolSpanName)

        // Add spans to storage
        spanCollector.startSpan(agentSpan, agentPath)
        spanCollector.startSpan(agentRunSpan, agentRunPath)
        spanCollector.startSpan(nodeSpan, nodePath)
        spanCollector.startSpan(toolSpan, toolPath)
        assertEquals(4, spanCollector.activeSpansCount)

        // Verify initial state - all spans are started but not ended
        assertTrue(agentSpan.isStarted)
        assertFalse(agentSpan.isEnded)

        assertTrue(agentRunSpan.isStarted)
        assertFalse(agentRunSpan.isEnded)

        assertTrue(nodeSpan.isStarted)
        assertFalse(nodeSpan.isEnded)

        assertTrue(toolSpan.isStarted)
        assertFalse(toolSpan.isEnded)

        // End unfinished spans
        spanCollector.endUnfinishedSpans()

        // Verify that all spans are ended
        assertTrue(agentSpan.isStarted)
        assertTrue(agentSpan.isEnded)

        assertTrue(agentRunSpan.isStarted)
        assertTrue(agentRunSpan.isEnded)

        assertTrue(nodeSpan.isStarted)
        assertTrue(nodeSpan.isEnded)

        assertTrue(toolSpan.isStarted)
        assertTrue(toolSpan.isEnded)
    }

    @Test
    fun `test mask HiddenString values in attributes when verbose set to false`() {
        val spanCollector = SpanCollector(MockTracer(), verbose = false)

        val spanId = "test-span-id"
        val spanName = "test-span-name"
        val span = MockGenAIAgentSpan(spanId, spanName)
        val mockSpan = MockSpan()
        val executionInfo = AgentExecutionInfo(null, spanId)

        // Start the span to replace the span instance later with a mocked value
        spanCollector.startSpan(span, executionInfo)
        assertEquals(1, spanCollector.activeSpansCount)

        // Replace started span with the mock instance
        span.span = mockSpan
        val attributes = listOf(
            MockAttribute(key = "secretKey", value = HiddenString("super-secret")),
            MockAttribute(key = "arraySecretKey", value = listOf(HiddenString("one"), HiddenString("two"))),
            MockAttribute("regularKey", "visible")
        )
        span.addAttributes(attributes)

        spanCollector.endSpan(span, executionInfo)
        assertEquals(0, spanCollector.activeSpansCount)

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
        val spanCollector = SpanCollector(MockTracer(), verbose = false)

        val spanId = "test-span-id"
        val spanName = "test-span-name"
        val span = MockGenAIAgentSpan(spanId, spanName)
        val mockSpan = MockSpan()
        val executionInfo = AgentExecutionInfo(null, spanId)

        // Start the span to replace the span instance later with a mocked value
        spanCollector.startSpan(span, executionInfo)
        assertEquals(1, spanCollector.activeSpansCount)

        // Event with attribute HiddenString and a body field that contains HiddenString
        // Replace started span with the mock instance
        span.span = mockSpan
        val event = MockGenAIAgentEvent(name = "event").apply {
            addAttribute(MockAttribute("secretKey", HiddenString("secretValue")))
            addBodyField(EventBodyFields.Content("some sensitive content"))
        }
        span.addEvent(event)

        spanCollector.endSpan(span, executionInfo)
        assertEquals(0, spanCollector.activeSpansCount)

        // Assert collected event
        assertEquals(1, mockSpan.collectedEvents.size)
        val actualEventAttributes = mockSpan.collectedEvents.getValue("event").asMap()

        // Assert attributes for the collected event when the verbose flag is set to 'false'
        val expectedEventAttributes = mapOf(
            AttributeKey.stringKey("secretKey") to HiddenString.HIDDEN_STRING_PLACEHOLDER,
            AttributeKey.stringKey("content") to HiddenString.HIDDEN_STRING_PLACEHOLDER,
        )

        assertEquals(expectedEventAttributes.size, actualEventAttributes.size)
        assertMapsEqual(expectedEventAttributes, actualEventAttributes)
    }

    @Test
    fun `endSpan should remove node from tree`() {
        val spanCollector = SpanCollector(MockTracer(), verbose = true)
        val spanId = "test-span-id"
        val spanName = "test-span-name"
        val span = MockGenAIAgentSpan(spanId, spanName)
        val executionInfo = AgentExecutionInfo(null, spanId)

        spanCollector.startSpan(span, executionInfo)
        assertEquals(1, spanCollector.activeSpansCount)
        assertNotNull(spanCollector.getSpan(executionInfo))

        spanCollector.endSpan(span, executionInfo)
        assertEquals(0, spanCollector.activeSpansCount)
        assertNull(spanCollector.getSpan(executionInfo))
    }

    @Test
    fun `endSpan should throw exception when span has active children`() {
        val spanCollector = SpanCollector(MockTracer(), verbose = true)

        // Create parent and child spans
        val parentSpanId = "parent-span"
        val parentSpanName = "parent-span-name"
        val parentSpan = MockGenAIAgentSpan(parentSpanId, parentSpanName)
        val parentPath = AgentExecutionInfo(null, parentSpanId)

        val childSpanId = "child-span"
        val childSpanName = "child-span-name"
        val childSpan = MockGenAIAgentSpan(childSpanId, childSpanName, parentSpan)
        val childPath = AgentExecutionInfo(parentPath, childSpanId)

        // Start both spans
        spanCollector.startSpan(parentSpan, parentPath)
        spanCollector.startSpan(childSpan, childPath)

        assertEquals(2, spanCollector.activeSpansCount)

        // Try to end parent span while the child is still active
        val exception = assertFailsWith<IllegalStateException> {
            spanCollector.endSpan(parentSpan, parentPath)
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
    fun `endSpan should succeed when child spans are ended first`() {
        val spanCollector = SpanCollector(MockTracer(), verbose = true)

        // Create parent and child spans
        val parentSpanId = "parent-span"
        val parentSpanName = "parent-span-name"
        val parentSpan = MockGenAIAgentSpan(parentSpanId, parentSpanName)
        val parentPath = AgentExecutionInfo(null, parentSpanId)

        val childSpanId = "child-span"
        val childSpanName = "child-span-name"
        val childSpan = MockGenAIAgentSpan(childSpanId, childSpanName, parentSpan)
        val childPath = AgentExecutionInfo(parentPath, childSpanId)

        // Start both spans
        spanCollector.startSpan(parentSpan, parentPath)
        spanCollector.startSpan(childSpan, childPath)

        assertEquals(2, spanCollector.activeSpansCount)

        // End child span first
        spanCollector.endSpan(childSpan, childPath)
        assertEquals(1, spanCollector.activeSpansCount)
        assertNull(spanCollector.getSpan(childPath))

        // Now parent can be ended
        spanCollector.endSpan(parentSpan, parentPath)
        assertEquals(0, spanCollector.activeSpansCount)
        assertNull(spanCollector.getSpan(parentPath))
    }

    @Test
    fun `tree should maintain only active spans`() {
        val spanCollector = SpanCollector(MockTracer(), verbose = true)

        // Create a tree: parent -> child1, child2
        val parentSpanId = "parent"
        val parentSpan = MockGenAIAgentSpan(parentSpanId, "parent-span")
        val parentPath = AgentExecutionInfo(null, parentSpanId)

        val child1SpanId = "child1"
        val child1Span = MockGenAIAgentSpan(child1SpanId, "child1-span", parentSpan)
        val child1Path = AgentExecutionInfo(parentPath, child1SpanId)

        val child2SpanId = "child2"
        val child2Span = MockGenAIAgentSpan(child2SpanId, "child2-span", parentSpan)
        val child2Path = AgentExecutionInfo(parentPath, child2SpanId)

        // Start all spans
        spanCollector.startSpan(parentSpan, parentPath)
        spanCollector.startSpan(child1Span, child1Path)
        spanCollector.startSpan(child2Span, child2Path)

        assertEquals(3, spanCollector.activeSpansCount)

        // End child1
        spanCollector.endSpan(child1Span, child1Path)
        assertEquals(2, spanCollector.activeSpansCount)
        assertNull(spanCollector.getSpan(child1Path))
        assertNotNull(spanCollector.getSpan(child2Path))
        assertNotNull(spanCollector.getSpan(parentPath))

        // End child2
        spanCollector.endSpan(child2Span, child2Path)
        assertEquals(1, spanCollector.activeSpansCount)
        assertNull(spanCollector.getSpan(child1Path))
        assertNull(spanCollector.getSpan(child2Path))
        assertNotNull(spanCollector.getSpan(parentPath))

        // End parent
        spanCollector.endSpan(parentSpan, parentPath)
        assertEquals(0, spanCollector.activeSpansCount)
        assertNull(spanCollector.getSpan(parentPath))
    }

    @Test
    fun `endSpan should handle multiple children properly`() {
        val spanCollector = SpanCollector(MockTracer(), verbose = true)

        // Create a tree: parent -> child1, child2, child3
        val parentSpanId = "parent"
        val parentSpan = MockGenAIAgentSpan(parentSpanId, "parent-span")
        val parentPath = AgentExecutionInfo(null, parentSpanId)

        spanCollector.startSpan(parentSpan, parentPath)

        val childSpan1 = Pair(
            MockGenAIAgentSpan("child-id-1", "child-span-1", parentSpan),
            AgentExecutionInfo(parentPath, "child-id-1")
        )

        val childSpan2 = Pair(
            MockGenAIAgentSpan("child-id-2", "child-span-2", parentSpan),
            AgentExecutionInfo(parentPath, "child-id-2")
        )

        val childSpan3 = Pair(
            MockGenAIAgentSpan("child-id-3", "child-span-3", parentSpan),
            AgentExecutionInfo(parentPath, "child-id-3")
        )

        // Start child spans
        val childSpans = listOf(childSpan1, childSpan2, childSpan3)
        childSpans.forEach { spanData ->
            spanCollector.startSpan(spanData.first, spanData.second)
        }

        assertEquals((childSpans + parentSpan).size, spanCollector.activeSpansCount)

        // Try to end parent while child spans are active
        val exception = assertFailsWith<IllegalStateException> {
            spanCollector.endSpan(parentSpan, parentPath)
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
            spanCollector.endSpan(child.first, child.second)
        }

        assertEquals(1, spanCollector.activeSpansCount)

        // Now parent can be ended
        spanCollector.endSpan(parentSpan, parentPath)
        assertEquals(0, spanCollector.activeSpansCount)
    }

    @Test
    fun `endSpan should handle deep tree hierarchy`() {
        val spanCollector = SpanCollector(MockTracer(), verbose = true)

        // Create a deep tree: root -> level1 -> level2 -> level3
        val rootSpan = MockGenAIAgentSpan("root", "root-span")
        val rootPath = AgentExecutionInfo(null, "root")

        val level1Span = MockGenAIAgentSpan("level1", "level1-span", rootSpan)
        val level1Path = AgentExecutionInfo(rootPath, "level1")

        val level2Span = MockGenAIAgentSpan("level2", "level2-span", level1Span)
        val level2Path = AgentExecutionInfo(level1Path, "level2")

        val level3Span = MockGenAIAgentSpan("level3", "level3-span", level2Span)
        val level3Path = AgentExecutionInfo(level2Path, "level3")

        // Start all spans
        spanCollector.startSpan(rootSpan, rootPath)
        spanCollector.startSpan(level1Span, level1Path)
        spanCollector.startSpan(level2Span, level2Path)
        spanCollector.startSpan(level3Span, level3Path)

        assertEquals(4, spanCollector.activeSpansCount)

        // Try to end root - should fail
        assertFailsWith<IllegalStateException> {
            spanCollector.endSpan(rootSpan, rootPath)
        }

        // Try to end level1 - should fail
        assertFailsWith<IllegalStateException> {
            spanCollector.endSpan(level1Span, level1Path)
        }

        // Try to end level2 - should fail
        assertFailsWith<IllegalStateException> {
            spanCollector.endSpan(level2Span, level2Path)
        }

        // End from deepest to root
        spanCollector.endSpan(level3Span, level3Path)
        assertEquals(3, spanCollector.activeSpansCount)

        spanCollector.endSpan(level2Span, level2Path)
        assertEquals(2, spanCollector.activeSpansCount)

        spanCollector.endSpan(level1Span, level1Path)
        assertEquals(1, spanCollector.activeSpansCount)

        spanCollector.endSpan(rootSpan, rootPath)
        assertEquals(0, spanCollector.activeSpansCount)
    }
}
