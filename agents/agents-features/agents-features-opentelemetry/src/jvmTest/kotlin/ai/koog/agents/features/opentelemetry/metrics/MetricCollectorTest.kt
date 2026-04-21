package ai.koog.agents.features.opentelemetry.metrics

import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes.Token.TokenType
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes.Koog.Tool.Call.StatusType
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import ai.koog.agents.features.opentelemetry.metric.MetricCollector
import ai.koog.agents.features.opentelemetry.metric.events.createExecuteToolDurationHistogramMetricEvent
import ai.koog.agents.features.opentelemetry.metric.events.createLLMCallDurationHistogramMetricEvent
import ai.koog.agents.features.opentelemetry.metric.events.createLLMInputTokensMetricEvent
import ai.koog.agents.features.opentelemetry.metric.events.createLLMOutputTokensMetricEvent
import ai.koog.agents.features.opentelemetry.metric.events.createToolCallCounterMetricEvent
import ai.koog.agents.features.opentelemetry.metrics.mock.Metric
import ai.koog.agents.features.opentelemetry.metrics.mock.TestMeter
import ai.koog.agents.features.opentelemetry.metrics.mock.getRecordsByCounterName
import ai.koog.agents.features.opentelemetry.metrics.mock.getRecordsByHistogramName
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MetricCollectorTest {
    companion object {
        private const val tokenUsageMetricName = "gen_ai.client.token.usage"
        private const val toolCallCountMetricName = "koog.gen_ai.client.tool.call.count"
        private const val operationDurationMetricName = "gen_ai.client.operation.duration"

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

        val countersAmount = 1
        val histogramsAmount = 2
    }

    @Test
    fun testMetricCollectorToInitializeMetrics() {
        val meter = TestMeter()

        assertEquals(0, meter.counterValues.size)
        assertEquals(0, meter.histogramValues.size)

        // Initialize MetricCollector to register metrics on the meter
        val config = OpenTelemetryConfig()
        MetricCollector(meter, config)

        // One counter and two histograms should be created
        assertEquals(countersAmount, meter.buildCounter.size)
        assertEquals(histogramsAmount, meter.buildHistogram.size)

        // For each counter-metric one starting value should be set
        assertEquals(countersAmount, meter.counterValues.size)
        assertEquals(0, meter.histogramValues.size)
    }

    @Test
    fun testMetricCollectorToCreateTokenUsageHistogram() {
        val meter = TestMeter()
        val config = OpenTelemetryConfig()
        MetricCollector(meter, config)

        assertContains(
            meter.buildHistogram,
            Metric(tokenUsageMetricName, "Number of input and output tokens used", "{token}")
        )
    }

    @Test
    fun testMetricCollectorToCreateToolCallCounter() {
        val meter = TestMeter()
        val config = OpenTelemetryConfig()
        MetricCollector(meter, config)

        assertContains(
            meter.buildCounter,
            Metric(toolCallCountMetricName, "Number of tool calls performed by the agent", "{call}")
        )
    }

    @Test
    fun testMetricCollectorToCreateOperationDurationHistogram() {
        val meter = TestMeter()
        val config = OpenTelemetryConfig()
        MetricCollector(meter, config)

        assertContains(
            meter.buildHistogram,
            Metric(operationDurationMetricName, "GenAI operation duration", "s")
        )
    }

    @Test
    fun testMetricCollectorToProcessLLMTokens() {
        val meter = TestMeter()
        val config = OpenTelemetryConfig()
        val metricCollector = MetricCollector(meter, config)

        val model = model
        val inputTokenSpend = 100L
        val outputTokenSpend = 200L

        metricCollector.recordHistogramMetricEvent(
            createLLMInputTokensMetricEvent(
                id = "test-id",
                model = model,
                inputTokens = inputTokenSpend
            )
        )

        metricCollector.recordHistogramMetricEvent(
            createLLMOutputTokensMetricEvent(
                id = "test-id",
                model = model,
                outputTokens = outputTokenSpend
            )
        )

        // Token Usage Metric (histogram)
        // Check values of the token usage metric
        val tokenUsageRecords = meter.getRecordsByHistogramName(tokenUsageMetricName)

        assertContentEquals(
            listOf(inputTokenSpend.toDouble(), outputTokenSpend.toDouble()),
            tokenUsageRecords.map { it.value }
        )

        // Check values' attributes of the input token metric
        val inputTokenAttributes = tokenUsageRecords.getOrNull(0)?.attributes
        assertLlmModelAttributes(inputTokenAttributes, model, model.provider)
        assertLlmModelTokenAttribute(inputTokenAttributes, TokenType.INPUT)

        val outputTokenAttributes = tokenUsageRecords.getOrNull(1)?.attributes
        assertLlmModelAttributes(outputTokenAttributes, model, model.provider)
        assertLlmModelTokenAttribute(outputTokenAttributes, TokenType.OUTPUT)
    }

    @Test
    fun testMetricCollectorToProcessLLMCallDuration() {
        val meter = TestMeter()
        val config = OpenTelemetryConfig()
        val metricCollector = MetricCollector(meter, config)

        val model = model
        val duration = 1.seconds

        metricCollector.recordHistogramMetricEvent(
            createLLMCallDurationHistogramMetricEvent(
                id = "test-id",
                model = model,
                duration = duration
            )
        )

        // Operation Duration Metric
        // Check values of the operation duration metric
        assertContentEquals(
            listOf(duration.inWholeSeconds.toDouble()),
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
        assertTrue { attributes.contains(GenAIAttributes.Request.Model(model)) }
        assertTrue { attributes.contains(GenAIAttributes.Response.Model(model)) }
    }

    @Test
    fun testMetricCollectorToProcessToolCall() {
        val cases = listOf(
            StatusType.SUCCESS,
            StatusType.ERROR,
            StatusType.VALIDATION_FAILED
        )

        cases.forEach { status ->
            val meter = TestMeter()
            val config = OpenTelemetryConfig()
            val metricCollector = MetricCollector(meter, config)

            val toolCallName = "test-tool"
            val duration = 100.milliseconds

            metricCollector.addCounterMetricEvent(
                createToolCallCounterMetricEvent(
                    id = "test-id",
                    toolName = toolCallName,
                    toolCallStatus = status
                )
            )

            assertEquals(countersAmount + 1, meter.counterValues.size)

            // Tool Call Count Metric
            // Check values of the Tool Call Count Metric
            assertContentEquals(
                meter.getRecordsByCounterName(toolCallCountMetricName).map { it.value },
                listOf(0, 1)
            )

            val toolCallCountAttributes =
                meter.getRecordsByCounterName(toolCallCountMetricName).getOrNull(1)?.attributes

            // Check values' attributes of the tool call count metric
            assertToolCallAttributes(toolCallCountAttributes, toolCallName, status)

            // Record duration
            metricCollector.recordHistogramMetricEvent(
                createExecuteToolDurationHistogramMetricEvent(
                    id = "test-id",
                    duration = duration,
                    toolName = toolCallName,
                    toolCallStatus = status
                )
            )

            assertEquals(histogramsAmount, meter.buildHistogram.size)

            // Operation Duration Metric
            // Check values of the operation duration metric
            assertContentEquals(
                listOf(duration.inWholeMilliseconds.toDouble() / 1000),
                meter.getRecordsByHistogramName(operationDurationMetricName).map { it.value }
            )

            val operationDurationMetric =
                meter.getRecordsByHistogramName(operationDurationMetricName).getOrNull(0)

            // Check values' attributes of the operation duration metric
            assertToolCallAttributes(operationDurationMetric?.attributes, toolCallName, status)
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
