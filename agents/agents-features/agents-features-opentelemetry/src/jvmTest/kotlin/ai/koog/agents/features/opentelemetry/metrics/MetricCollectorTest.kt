package ai.koog.agents.features.opentelemetry.metrics

import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes.Token.TokenType
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes.Koog.Tool.Call.StatusType
import ai.koog.agents.features.opentelemetry.metric.LLMCallEnded
import ai.koog.agents.features.opentelemetry.metric.LLMCallStarted
import ai.koog.agents.features.opentelemetry.metric.MetricCollector
import ai.koog.agents.features.opentelemetry.metric.ToolCallEnded
import ai.koog.agents.features.opentelemetry.metric.ToolCallStarted
import ai.koog.agents.features.opentelemetry.metric.ToolCallStatus
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.opentelemetry.api.common.Attributes
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MetricCollectorTest {
    companion object {
        val tokenCountMetricName = "gen_ai.client.token.usage"
        val toolCallCountMetricName = "koog.tool.count"
        val operationDurationMetricName = "gen_ai.client.operation.duration"

        val model: LLModel = LLModel(
            provider = LLMProvider.Ollama,
            id = "llama3-groq-tool-use:8b",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Standard,
                LLMCapability.Tools
            ),
            contextLength = 8_192,
        )

        val countersAmount = 2
        val histogramsAmount = 1
    }

    @Test
    fun `test metric collector to initialize metrics`() {
        val meter = TestMeter()

        assertEquals(0, meter.counterValues.size)
        assertEquals(0, meter.histogramValues.size)

        val metricCollector = MetricCollector(meter)

        // Two counters and one histogram should be created
        assertEquals(countersAmount, meter.buildCounter.size)
        assertEquals(histogramsAmount, meter.buildHistogram.size)

        // For each counter-metric one starting value should be set
        assertEquals(countersAmount, meter.counterValues.size)
        assertEquals(0, meter.histogramValues.size)
    }

    @Test
    fun `test metric collector to create token counter`() {
        val meter = TestMeter()
        val metricCollector = MetricCollector(meter)

        assertContains(
            meter.buildCounter,
            Metric(tokenCountMetricName, "Total token count", "token")
        )
    }

    @Test
    fun `test metric collector to create tool call counter`() {
        val meter = TestMeter()
        val metricCollector = MetricCollector(meter)

        assertContains(
            meter.buildCounter,
            Metric(toolCallCountMetricName, "Tool calls count", "tool call")
        )
    }

    @Test
    fun `test metric collector to create operation duration histogram`() {
        val meter = TestMeter()
        val metricCollector = MetricCollector(meter)

        assertContains(
            meter.buildHistogram,
            Metric(operationDurationMetricName, "Operation duration", "s")
        )
    }

    @Test
    fun `test metric collector to process LLM Call`() {
        val meter = TestMeter()
        val metricCollector = MetricCollector(meter)

        val eventId = "event-id"
        val timestampStart = 100L
        val timestampEnd = 101L
        val model = model
        val inputTokenSpend = 100L
        val outputTokenSpend = 200L

        metricCollector.recordEvent(
            LLMCallStarted(
                id = eventId,
                timestamp = timestampStart,
                model = model,
                modelProvider = model.provider
            )
        )

        assertEquals(countersAmount, meter.counterValues.size)
        assertEquals(0, meter.histogramValues.size)

        metricCollector.recordEvent(
            LLMCallEnded(
                id = eventId,
                timestamp = timestampEnd,
                model = model,
                modelProvider = model.provider,
                inputTokenSpend = inputTokenSpend,
                outputTokenSpend = outputTokenSpend
            )
        )

        assertEquals(countersAmount + 2, meter.counterValues.size)
        assertEquals(histogramsAmount, meter.buildHistogram.size)

        // Token Count Metric
        // Check values of the token count metric
        val tokenCountRecords = meter.getRecordsByCounterName(tokenCountMetricName)

        assertContentEquals(
            listOf(0, inputTokenSpend, outputTokenSpend),
            tokenCountRecords.map { it.value }
        )

        // Check values' attributes of the input count metric
        val inputTokenAttributes = tokenCountRecords.getOrNull(1)?.attributes
        assertLlmModelAttributes(inputTokenAttributes, model, model.provider)
        assertLlmModelTokenAttribute(inputTokenAttributes, TokenType.INPUT)

        val outputTokenAttributes = tokenCountRecords.getOrNull(2)?.attributes
        assertLlmModelAttributes(outputTokenAttributes, model, model.provider)
        assertLlmModelTokenAttribute(outputTokenAttributes, TokenType.OUTPUT)

        // Operation Duration Metric
        // Check values of the operation duration metric
        assertContentEquals(
            listOf((timestampEnd - timestampStart).toSec()),
            meter.getRecordsByHistogramName(operationDurationMetricName).map { it.value }
        )

        val operationDurationAttributes =
            meter.getRecordsByHistogramName(operationDurationMetricName).getOrNull(0)?.attributes

        // Check values' attributes of the operation duration metric
        assertLlmModelAttributes(operationDurationAttributes, model, model.provider)
    }

    private fun assertLlmModelTokenAttribute(
        attributes: Attributes?,
        tokenType: TokenType
    ) {
        assertNotNull(attributes)
        assertTrue { attributes.contains(GenAIAttributes.Token.Type(tokenType)) }
    }

    private fun assertLlmModelAttributes(
        attributes: Attributes?,
        model: LLModel,
        modelProvider: LLMProvider
    ) {
        assertNotNull(attributes)
        assertTrue {
            attributes.contains(
                GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.TEXT_COMPLETION)
            )
        }
        assertTrue { attributes.contains(GenAIAttributes.Provider.Name(modelProvider)) }
        assertTrue { attributes.contains(GenAIAttributes.Response.Model(model)) }
    }

    @Test
    fun `test metric collector to process tool call`() {
        val cases = listOf(
            ToolCallStatus.SUCCESS to StatusType.SUCCESS,
            ToolCallStatus.FAILED to StatusType.ERROR,
            ToolCallStatus.VALIDATION_FAILED to StatusType.VALIDATION_FAILED
        )

        cases.forEach { (status, expectedStatus) ->
            val meter = TestMeter()
            val metricCollector = MetricCollector(meter)

            val eventId = "event-id"
            val timestampStart = 100L
            val timestampEnd = 101L
            val toolCallName = "test-tool"

            metricCollector.recordEvent(
                ToolCallStarted(
                    id = eventId,
                    timestamp = timestampStart,
                    toolName = toolCallName,
                )
            )

            assertEquals(countersAmount, meter.counterValues.size)
            assertEquals(0, meter.histogramValues.size)

            metricCollector.recordEvent(
                ToolCallEnded(
                    id = eventId,
                    timestamp = timestampEnd,
                    toolName = toolCallName,
                    status = status
                )
            )

            assertEquals(countersAmount + 1, meter.counterValues.size)
            assertEquals(histogramsAmount, meter.buildHistogram.size)

            // Tool Call Count Metric
            // Check values of the Tool Call Count Metric
            assertContentEquals(
                meter.getRecordsByCounterName(toolCallCountMetricName).map { it.value },
                listOf(0, 1)
            )

            val toolCallCountAttributes =
                meter.getRecordsByCounterName(toolCallCountMetricName).getOrNull(1)?.attributes

            // Check values' attributes of the input count metric
            assertToolCallAttributes(toolCallCountAttributes, toolCallName, expectedStatus)

            // Operation Duration Metric
            // Check values of the operation duration metric
            assertContentEquals(
                listOf((timestampEnd - timestampStart).toSec()),
                meter.getRecordsByHistogramName(operationDurationMetricName).map { it.value }
            )

            val operationDurationMetric =
                meter.getRecordsByHistogramName(operationDurationMetricName).getOrNull(0)

            // Check values' attributes of the operation duration metric
            assertToolCallAttributes(operationDurationMetric?.attributes, toolCallName, expectedStatus)
        }
    }

    private fun assertToolCallAttributes(
        attributes: Attributes?,
        toolCallName: String,
        status: StatusType,
    ) {
        assertNotNull(attributes)
        assertTrue {
            attributes.contains(
                GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.EXECUTE_TOOL)
            )
        }
        assertTrue { attributes.contains(GenAIAttributes.Tool.Name(toolCallName)) }
        assertTrue { attributes.contains(KoogAttributes.Koog.Tool.Call.Status(status)) }
    }
}
