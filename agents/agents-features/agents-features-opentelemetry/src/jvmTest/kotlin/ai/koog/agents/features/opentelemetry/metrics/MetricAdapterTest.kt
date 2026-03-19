package ai.koog.agents.features.opentelemetry.metrics

import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import ai.koog.agents.features.opentelemetry.metric.MetricCollector
import ai.koog.agents.features.opentelemetry.metric.adapter.restrictToolNameCardinality
import ai.koog.agents.features.opentelemetry.metric.events.createExecuteToolDurationHistogramMetricEvent
import ai.koog.agents.features.opentelemetry.metric.events.createToolCallCounterMetricEvent
import ai.koog.agents.features.opentelemetry.metrics.mock.TestMeter
import ai.koog.agents.features.opentelemetry.metrics.mock.getRecordsByCounterName
import ai.koog.agents.features.opentelemetry.metrics.mock.getRecordsByHistogramName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class MetricAdapterTest {

    @Test
    fun testRestrictToolNameCardinalityWithAllowedTool() {
        val meter = TestMeter()
        val config = OpenTelemetryConfig()

        val allowedToolNames = setOf("allowed-tool", "another-allowed-tool")
        config.restrictToolNameCardinality(allowedToolNames)

        val metricCollector = MetricCollector(meter, config)

        val toolName = "allowed-tool"
        metricCollector.addCounterMetricEvent(
            createToolCallCounterMetricEvent(
                id = "test-id",
                toolName = toolName,
                toolCallStatus = KoogAttributes.Koog.Tool.Call.StatusType.SUCCESS
            )
        )

        val toolCallCountRecords = meter.getRecordsByCounterName("koog.gen_ai.client.tool.call.count")
        assertEquals(2, toolCallCountRecords.size) // Initial 0 + 1 call

        val attributes = toolCallCountRecords.last().attributes
        assertTrue { attributes?.contains(GenAIAttributes.Tool.Name(toolName)) == true }
    }

    @Test
    fun testRestrictToolNameCardinalityWithDisallowedTool() {
        val meter = TestMeter()
        val config = OpenTelemetryConfig()

        val allowedToolNames = setOf("allowed-tool")
        val fallbackToolName = "filtered"
        config.restrictToolNameCardinality(allowedToolNames, fallbackToolName)

        val metricCollector = MetricCollector(meter, config)

        val toolName = "disallowed-tool"
        metricCollector.addCounterMetricEvent(
            createToolCallCounterMetricEvent(
                id = "test-id",
                toolName = toolName,
                toolCallStatus = KoogAttributes.Koog.Tool.Call.StatusType.SUCCESS
            )
        )

        val toolCallCountRecords = meter.getRecordsByCounterName("koog.gen_ai.client.tool.call.count")
        assertEquals(2, toolCallCountRecords.size) // Initial 0 + 1 call

        // Metric adapter should have processed the tool name
        // The metric should be recorded regardless of filtering
        assertEquals(1L, toolCallCountRecords.last().value)
    }

    @Test
    fun testRestrictToolNameCardinalityWithHistogram() {
        val meter = TestMeter()
        val config = OpenTelemetryConfig()

        val allowedToolNames = setOf("allowed-tool")
        config.restrictToolNameCardinality(allowedToolNames)

        val metricCollector = MetricCollector(meter, config)

        val toolName = "allowed-tool"
        val duration = 100.milliseconds

        metricCollector.recordHistogramMetricEvent(
            createExecuteToolDurationHistogramMetricEvent(
                id = "test-id",
                duration = duration,
                toolName = toolName,
                toolCallStatus = KoogAttributes.Koog.Tool.Call.StatusType.SUCCESS
            )
        )

        val histogramRecords = meter.getRecordsByHistogramName("gen_ai.client.operation.duration")
        assertEquals(1, histogramRecords.size)

        val attributes = histogramRecords.first().attributes
        assertTrue { attributes?.contains(GenAIAttributes.Tool.Name(toolName)) == true }
    }

    @Test
    fun testRestrictToolNameCardinalityWithMultipleTools() {
        val meter = TestMeter()
        val config = OpenTelemetryConfig()

        val allowedToolNames = setOf("tool-a", "tool-b")
        config.restrictToolNameCardinality(allowedToolNames)

        val metricCollector = MetricCollector(meter, config)

        // Add allowed tools
        metricCollector.addCounterMetricEvent(
            createToolCallCounterMetricEvent(
                id = "test-id",
                toolName = "tool-a",
                toolCallStatus = KoogAttributes.Koog.Tool.Call.StatusType.SUCCESS
            )
        )

        metricCollector.addCounterMetricEvent(
            createToolCallCounterMetricEvent(
                id = "test-id",
                toolName = "tool-b",
                toolCallStatus = KoogAttributes.Koog.Tool.Call.StatusType.SUCCESS
            )
        )

        // Add a disallowed tool
        metricCollector.addCounterMetricEvent(
            createToolCallCounterMetricEvent(
                id = "test-id",
                toolName = "tool-c",
                toolCallStatus = KoogAttributes.Koog.Tool.Call.StatusType.SUCCESS
            )
        )

        val toolCallCountRecords = meter.getRecordsByCounterName("koog.gen_ai.client.tool.call.count")
        assertEquals(4, toolCallCountRecords.size) // Initial 0 + 3 calls
    }

    @Test
    fun testRestrictToolNameCardinalityWithDefaultFallbackName() {
        val meter = TestMeter()
        val config = OpenTelemetryConfig()

        val allowedToolNames = setOf("allowed-tool")
        // Using default fallback name "filtered"
        config.restrictToolNameCardinality(allowedToolNames)

        val metricCollector = MetricCollector(meter, config)

        val toolName = "disallowed-tool"
        metricCollector.addCounterMetricEvent(
            createToolCallCounterMetricEvent(
                id = "test-id",
                toolName = toolName,
                toolCallStatus = KoogAttributes.Koog.Tool.Call.StatusType.SUCCESS
            )
        )

        val toolCallCountRecords = meter.getRecordsByCounterName("koog.gen_ai.client.tool.call.count")
        assertEquals(2, toolCallCountRecords.size)

        // Verify the metric was recorded (even if the tool name might be filtered)
        assertEquals(1L, toolCallCountRecords.last().value)
    }
}
