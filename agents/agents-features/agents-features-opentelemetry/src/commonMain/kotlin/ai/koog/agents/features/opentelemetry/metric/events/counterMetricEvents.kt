package ai.koog.agents.features.opentelemetry.metric.events

import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import ai.koog.agents.features.opentelemetry.metric.CounterMetricEvent
import ai.koog.agents.features.opentelemetry.metric.GenAIMetrics
import ai.koog.agents.features.opentelemetry.metric.HistogramMetricEvent
import ai.koog.agents.features.opentelemetry.metric.KoogMetrics
import ai.koog.prompt.llm.LLModel
import ai.koog.utils.time.KoogClock

internal fun createLLMInputTokensMetricEvent(
    id: String,
    inputTokens: Long,
    model: LLModel
): HistogramMetricEvent {
    val attributes = listOf(
        GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.TEXT_COMPLETION),
        GenAIAttributes.Provider.Name(model.provider),
        GenAIAttributes.Token.Type(GenAIAttributes.Token.TokenType.INPUT),
        GenAIAttributes.Request.Model(model),
        GenAIAttributes.Response.Model(model)
    )

    return HistogramMetricEvent(
        id = id,
        timestamp = KoogClock.System.now(),
        metricName = GenAIMetrics.Client.Token.Usage.name,
        value = inputTokens.toDouble(),
        attributes = attributes
    )
}

internal fun createLLMOutputTokensMetricEvent(
    id: String,
    outputTokens: Long,
    model: LLModel
): HistogramMetricEvent {
    val attributes = listOf(
        GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.TEXT_COMPLETION),
        GenAIAttributes.Provider.Name(model.provider),
        GenAIAttributes.Token.Type(GenAIAttributes.Token.TokenType.OUTPUT),
        GenAIAttributes.Request.Model(model),
        GenAIAttributes.Response.Model(model)
    )

    return HistogramMetricEvent(
        id = id,
        timestamp = KoogClock.System.now(),
        metricName = GenAIMetrics.Client.Token.Usage.name,
        value = outputTokens.toDouble(),
        attributes = attributes
    )
}

internal fun createToolCallCounterMetricEvent(
    id: String,
    toolName: String,
    toolCallStatus: KoogAttributes.Koog.Tool.Call.StatusType,
): CounterMetricEvent {
    val attributes = listOf(
        GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.EXECUTE_TOOL),
        GenAIAttributes.Provider.Name(KoogAttributes.PROVIDER_NAME),
        GenAIAttributes.Tool.Name(toolName),
        KoogAttributes.Koog.Tool.Call.Status(toolCallStatus)
    )

    return CounterMetricEvent(
        id = id,
        timestamp = KoogClock.System.now(),
        metricName = KoogMetrics.Client.Tool.Call.Count.name,
        attributes = attributes,
        value = 1L
    )
}
