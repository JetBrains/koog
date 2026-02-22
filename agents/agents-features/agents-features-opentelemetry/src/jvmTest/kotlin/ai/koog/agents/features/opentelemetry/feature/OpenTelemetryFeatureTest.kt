package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.features.opentelemetry.mock.MockSpan
import ai.koog.agents.features.opentelemetry.mock.MockTracer
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpanBuilder
import ai.koog.agents.features.opentelemetry.span.SpanCollector
import ai.koog.agents.features.opentelemetry.span.SpanType
import io.opentelemetry.api.trace.SpanKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenTelemetryFeatureTest {

    //region AppendRunId

    @Test
    fun testAppendRunIdForTopLevelPathWithoutRunId() {
        val executionInfo = AgentExecutionInfo(null, "parent")
        val openTelemetry = OpenTelemetry.Feature
        val patchedInfo = openTelemetry.appendRunId(executionInfo, null)
        assertEquals("parent", patchedInfo.path())
    }

    @Test
    fun testAppendRunIdForTopLevelPathWithRunId() {
        val executionInfo = AgentExecutionInfo(null, "parent")
        val openTelemetry = OpenTelemetry.Feature
        val patchedInfo = openTelemetry.appendRunId(executionInfo, "test-run-id")
        assertEquals("parent/test-run-id", patchedInfo.path())
    }

    @Test
    fun testAppendRunIdForNonTopLevelPath() {
        val executionInfo = AgentExecutionInfo(AgentExecutionInfo(null, "parent"), "child")
        val openTelemetry = OpenTelemetry.Feature
        val patchedInfo = openTelemetry.appendRunId(executionInfo, "test-run-id")
        assertEquals("parent/test-run-id/child", patchedInfo.path())
    }

    @Test
    fun testAppendRunIdForMultipleChildren() {
        val executionInfo = AgentExecutionInfo(AgentExecutionInfo(AgentExecutionInfo(null, "parent"), "child1"), "child2")
        val openTelemetry = OpenTelemetry.Feature
        val patchedInfo = openTelemetry.appendRunId(executionInfo, "test-run-id")
        assertEquals("parent/test-run-id/child1/child2", patchedInfo.path())
    }

    //endregion AppendRunId

    //region AppendId

    @Test
    fun testAppendIdForTopLevelExecutionInfo() {
        val executionInfo = AgentExecutionInfo(null, "parent")
        val openTelemetry = OpenTelemetry.Feature
        val patchedInfo = openTelemetry.appendId(executionInfo, "test-id")
        assertEquals("parent/test-id", patchedInfo.path())
    }

    @Test
    fun testAppendIdForPath() {
        val executionInfo = AgentExecutionInfo(AgentExecutionInfo(null, "parent"), "child")
        val openTelemetry = OpenTelemetry.Feature
        val patchedInfo = openTelemetry.appendId(executionInfo, "test-id")
        assertEquals("parent/child/test-id", patchedInfo.path())
    }

    //endregion AppendId

    //region End Unfinished Spans

    @Test
    fun testEndUnfinishedSpans_EndsAllSpansWhenNoFilterProvided() {
        val spanCollector = SpanCollector()
        val tracer = MockTracer()
        val openTelemetry = OpenTelemetry.Feature

        // Create and collect spans
        val span1 = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "span1",
            name = "span1-name",
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)
        spanCollector.collectSpan(span1, AgentExecutionInfo(null, "span1"))

        val span2 = GenAIAgentSpanBuilder(
            spanType = SpanType.INVOKE_AGENT,
            parentSpan = span1,
            id = "span2",
            name = "span2-name",
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)
        spanCollector.collectSpan(span2, AgentExecutionInfo(AgentExecutionInfo(null, "span1"), "span2"))

        assertEquals(2, spanCollector.activeSpansCount)

        // End all unfinished spans
        openTelemetry.endUnfinishedSpans(spanCollector, verbose = false)

        // Verify all spans are ended and removed
        assertEquals(0, spanCollector.activeSpansCount)
        val mockSpan1 = span1.span as MockSpan
        val mockSpan2 = span2.span as MockSpan
        assertTrue(mockSpan1.isEnded)
        assertTrue(mockSpan2.isEnded)
    }

    @Test
    fun testEndUnfinishedSpans_EndsOnlyMatchingSpansWhenFilterProvided() {
        val spanCollector = SpanCollector()
        val tracer = MockTracer()
        val openTelemetry = OpenTelemetry.Feature

        // Create spans of different types
        val createAgentSpan = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "create-agent",
            name = "create-agent-name",
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)
        spanCollector.collectSpan(createAgentSpan, AgentExecutionInfo(null, "create-agent"))

        val invokeAgentSpan = GenAIAgentSpanBuilder(
            spanType = SpanType.INVOKE_AGENT,
            parentSpan = createAgentSpan,
            id = "invoke-agent",
            name = "invoke-agent-name",
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)
        spanCollector.collectSpan(invokeAgentSpan, AgentExecutionInfo(AgentExecutionInfo(null, "create-agent"), "invoke-agent"))

        val nodeSpan = GenAIAgentSpanBuilder(
            spanType = SpanType.NODE,
            parentSpan = invokeAgentSpan,
            id = "node",
            name = "node-name",
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)
        spanCollector.collectSpan(nodeSpan, AgentExecutionInfo(AgentExecutionInfo(AgentExecutionInfo(null, "create-agent"), "invoke-agent"), "node"))

        assertEquals(3, spanCollector.activeSpansCount)

        // End only NODE spans (filter out CREATE_AGENT and INVOKE_AGENT)
        openTelemetry.endUnfinishedSpans(spanCollector, verbose = false) { span ->
            span.type == SpanType.NODE
        }

        // Verify only node span is ended
        assertEquals(2, spanCollector.activeSpansCount)
        val mockCreateAgentSpan = createAgentSpan.span as MockSpan
        val mockInvokeAgentSpan = invokeAgentSpan.span as MockSpan
        val mockNodeSpan = nodeSpan.span as MockSpan
        assertFalse(mockCreateAgentSpan.isEnded)
        assertFalse(mockInvokeAgentSpan.isEnded)
        assertTrue(mockNodeSpan.isEnded)
    }

    @Test
    fun testEndUnfinishedSpans_HandlesDeepHierarchy() {
        val spanCollector = SpanCollector()
        val tracer = MockTracer()
        val openTelemetry = OpenTelemetry.Feature

        // Create a deep hierarchy: root -> level1 -> level2 -> level3
        val rootSpan = GenAIAgentSpanBuilder(
            spanType = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "root",
            name = "root-name",
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)
        val rootPath = AgentExecutionInfo(null, "root")
        spanCollector.collectSpan(rootSpan, rootPath)

        val level1Span = GenAIAgentSpanBuilder(
            spanType = SpanType.INVOKE_AGENT,
            parentSpan = rootSpan,
            id = "level1",
            name = "level1-name",
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)
        val level1Path = AgentExecutionInfo(rootPath, "level1")
        spanCollector.collectSpan(level1Span, level1Path)

        val level2Span = GenAIAgentSpanBuilder(
            spanType = SpanType.NODE,
            parentSpan = level1Span,
            id = "level2",
            name = "level2-name",
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)
        val level2Path = AgentExecutionInfo(level1Path, "level2")
        spanCollector.collectSpan(level2Span, level2Path)

        val level3Span = GenAIAgentSpanBuilder(
            spanType = SpanType.EXECUTE_TOOL,
            parentSpan = level2Span,
            id = "level3",
            name = "level3-name",
            kind = SpanKind.INTERNAL,
        ).buildAndStart(tracer)
        val level3Path = AgentExecutionInfo(level2Path, "level3")
        spanCollector.collectSpan(level3Span, level3Path)

        assertEquals(4, spanCollector.activeSpansCount)

        // End all spans - should handle hierarchy correctly (leaf to root)
        openTelemetry.endUnfinishedSpans(spanCollector, verbose = false)

        // Verify all spans are ended
        assertEquals(0, spanCollector.activeSpansCount)

        val mockRootSpan = rootSpan.span as MockSpan
        val mockLevel1Span = level1Span.span as MockSpan
        val mockLevel2Span = level2Span.span as MockSpan
        val mockLevel3Span = level3Span.span as MockSpan

        assertTrue(mockRootSpan.isEnded)
        assertTrue(mockLevel1Span.isEnded)
        assertTrue(mockLevel2Span.isEnded)
        assertTrue(mockLevel3Span.isEnded)
    }

    //endregion End Unfinished Spans
}
