package ai.koog.agents.features.opentelemetry.metrics

import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import ai.koog.agents.features.opentelemetry.attribute.toSdkAttributes
import ai.koog.agents.features.opentelemetry.metric.events.createExecuteToolDurationHistogramMetricEvent
import ai.koog.agents.features.opentelemetry.metric.events.createLLMCallDurationHistogramMetricEvent
import ai.koog.agents.features.opentelemetry.metric.events.createLLMInputTokensMetricEvent
import ai.koog.agents.features.opentelemetry.metric.events.createLLMOutputTokensMetricEvent
import ai.koog.agents.features.opentelemetry.metric.events.createToolCallCounterMetricEvent
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MetricEventCreatorsTest {

    private val testModel = LLModel(
        provider = LLMProvider.OpenAI,
        id = "gpt-4",
        capabilities = listOf(LLMCapability.Tools, LLMCapability.Temperature),
        contextLength = 8192
    )

    @Test
    fun testCreateLLMInputTokensMetricEvent() {
        val inputTokens = 100L
        val event = createLLMInputTokensMetricEvent(
            id = "test-id",
            model = testModel,
            inputTokens = inputTokens
        )

        assertEquals("gen_ai.client.token.usage", event.metricName)
        assertEquals(inputTokens.toDouble(), event.value)

        // Verify attributes using toSdkAttributes
        val sdkAttributes = event.attributes.toSdkAttributes(verbose = true)
        assertTrue(sdkAttributes.contains(GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.TEXT_COMPLETION)))
        assertTrue(sdkAttributes.contains(GenAIAttributes.Provider.Name(LLMProvider.OpenAI)))
        assertTrue(sdkAttributes.contains(GenAIAttributes.Token.Type(GenAIAttributes.Token.TokenType.INPUT)))
        assertTrue(sdkAttributes.contains(GenAIAttributes.Request.Model(testModel)))
        assertTrue(sdkAttributes.contains(GenAIAttributes.Response.Model(testModel)))
    }

    @Test
    fun testCreateLLMOutputTokensMetricEvent() {
        val outputTokens = 200L
        val event = createLLMOutputTokensMetricEvent(
            id = "test-id",
            model = testModel,
            outputTokens = outputTokens
        )

        assertEquals("gen_ai.client.token.usage", event.metricName)
        assertEquals(outputTokens.toDouble(), event.value)

        // Verify attributes using toSdkAttributes
        val sdkAttributes = event.attributes.toSdkAttributes(verbose = true)
        assertTrue(sdkAttributes.contains(GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.TEXT_COMPLETION)))
        assertTrue(sdkAttributes.contains(GenAIAttributes.Provider.Name(LLMProvider.OpenAI)))
        assertTrue(sdkAttributes.contains(GenAIAttributes.Token.Type(GenAIAttributes.Token.TokenType.OUTPUT)))
        assertTrue(sdkAttributes.contains(GenAIAttributes.Request.Model(testModel)))
        assertTrue(sdkAttributes.contains(GenAIAttributes.Response.Model(testModel)))
    }

    @Test
    fun testCreateLLMCallDurationHistogramMetricEvent() {
        val duration = 1.5.seconds
        val event = createLLMCallDurationHistogramMetricEvent(
            id = "test-id",
            model = testModel,
            duration = duration
        )

        assertEquals("gen_ai.client.operation.duration", event.metricName)
        assertEquals(1.5, event.value)

        // Verify attributes using toSdkAttributes
        val sdkAttributes = event.attributes.toSdkAttributes(verbose = true)
        assertTrue(sdkAttributes.contains(GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.TEXT_COMPLETION)))
        assertTrue(sdkAttributes.contains(GenAIAttributes.Provider.Name(LLMProvider.OpenAI)))
        assertTrue(sdkAttributes.contains(GenAIAttributes.Request.Model(testModel)))
        assertTrue(sdkAttributes.contains(GenAIAttributes.Response.Model(testModel)))
    }

    @Test
    fun testCreateToolCallCounterMetricEvent() {
        val toolName = "test-tool"
        val status = KoogAttributes.Koog.Tool.Call.StatusType.SUCCESS

        val event = createToolCallCounterMetricEvent(
            id = "test-id",
            toolName = toolName,
            toolCallStatus = status
        )

        assertEquals("koog.gen_ai.client.tool.call.count", event.metricName)
        assertEquals(1L, event.value)

        // Verify attributes using toSdkAttributes
        val sdkAttributes = event.attributes.toSdkAttributes(verbose = true)
        assertTrue(sdkAttributes.contains(GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.EXECUTE_TOOL)))
        assertTrue(sdkAttributes.contains(GenAIAttributes.Tool.Name(toolName)))
        assertTrue(sdkAttributes.contains(KoogAttributes.Koog.Tool.Call.Status(status)))
    }

    @Test
    fun testCreateExecuteToolDurationHistogramMetricEvent() {
        val toolName = "test-tool"
        val status = KoogAttributes.Koog.Tool.Call.StatusType.SUCCESS
        val duration = 150.milliseconds

        val event = createExecuteToolDurationHistogramMetricEvent(
            id = "test-id",
            duration = duration,
            toolName = toolName,
            toolCallStatus = status
        )

        assertEquals("gen_ai.client.operation.duration", event.metricName)
        assertEquals(0.15, event.value)

        // Verify attributes using toSdkAttributes
        val sdkAttributes = event.attributes.toSdkAttributes(verbose = true)
        assertTrue(sdkAttributes.contains(GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.EXECUTE_TOOL)))
        assertTrue(sdkAttributes.contains(GenAIAttributes.Tool.Name(toolName)))
        assertTrue(sdkAttributes.contains(KoogAttributes.Koog.Tool.Call.Status(status)))
    }

    @Test
    fun testCreateToolCallCounterMetricEventWithDifferentStatuses() {
        val toolName = "test-tool"
        val statuses = listOf(
            KoogAttributes.Koog.Tool.Call.StatusType.SUCCESS,
            KoogAttributes.Koog.Tool.Call.StatusType.ERROR,
            KoogAttributes.Koog.Tool.Call.StatusType.VALIDATION_FAILED
        )

        statuses.forEach { status ->
            val event = createToolCallCounterMetricEvent(
                id = "test-id",
                toolName = toolName,
                toolCallStatus = status
            )

            assertEquals("koog.gen_ai.client.tool.call.count", event.metricName)
            assertEquals(1L, event.value)

            val sdkAttributes = event.attributes.toSdkAttributes(verbose = true)
            assertTrue(sdkAttributes.contains(KoogAttributes.Koog.Tool.Call.Status(status)))
        }
    }

    @Test
    fun testCreateExecuteToolDurationHistogramMetricEventWithDifferentStatuses() {
        val toolName = "test-tool"
        val duration = 100.milliseconds
        val statuses = listOf(
            KoogAttributes.Koog.Tool.Call.StatusType.SUCCESS,
            KoogAttributes.Koog.Tool.Call.StatusType.ERROR,
            KoogAttributes.Koog.Tool.Call.StatusType.VALIDATION_FAILED
        )

        statuses.forEach { status ->
            val event = createExecuteToolDurationHistogramMetricEvent(
                id = "test-id",
                duration = duration,
                toolName = toolName,
                toolCallStatus = status
            )

            assertEquals("gen_ai.client.operation.duration", event.metricName)
            assertEquals(0.1, event.value)

            val sdkAttributes = event.attributes.toSdkAttributes(verbose = true)
            assertTrue(sdkAttributes.contains(KoogAttributes.Koog.Tool.Call.Status(status)))
        }
    }

    @Test
    fun testDurationConversionToSeconds() {
        val testCases = listOf(
            1.seconds to 1.0,
            500.milliseconds to 0.5,
            1500.milliseconds to 1.5,
            100.milliseconds to 0.1
        )

        testCases.forEach { (duration, expectedSeconds) ->
            val event = createLLMCallDurationHistogramMetricEvent(
                id = "test-id",
                model = testModel,
                duration = duration
            )
            assertEquals(expectedSeconds, event.value)
        }
    }
}
