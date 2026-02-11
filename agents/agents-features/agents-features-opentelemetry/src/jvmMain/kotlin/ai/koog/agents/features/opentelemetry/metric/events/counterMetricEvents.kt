package ai.koog.agents.features.opentelemetry.metric.events

import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import ai.koog.agents.features.opentelemetry.metric.CounterMetricEvent
import ai.koog.agents.features.opentelemetry.metric.GenAIMetrics
import ai.koog.agents.features.opentelemetry.metric.KoogMetrics
import ai.koog.prompt.llm.LLModel
import kotlinx.datetime.Clock

internal fun createLLMInputTokensMetricEvent(
    id: String,
    inputTokens: Long,
    model: LLModel
): CounterMetricEvent {
    val attributes = listOf(
        GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.TEXT_COMPLETION),
        GenAIAttributes.Provider.Name(model.provider),
        GenAIAttributes.Token.Type(GenAIAttributes.Token.TokenType.INPUT),
        GenAIAttributes.Response.Model(model)
    )

    return CounterMetricEvent(
        id = id,
        timestamp = Clock.System.now(),
        metricName = GenAIMetrics.Client.Token.Usage.name,
        value = inputTokens,
        attributes = attributes
    )
}

internal fun createLLMOutputTokensMetricEvent(
    id: String,
    outputTokens: Long,
    model: LLModel
): CounterMetricEvent {
    val attributes = listOf(
        GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.TEXT_COMPLETION),
        GenAIAttributes.Provider.Name(model.provider),
        GenAIAttributes.Token.Type(GenAIAttributes.Token.TokenType.OUTPUT),
        GenAIAttributes.Response.Model(model)
    )

    return CounterMetricEvent(
        id = id,
        timestamp = Clock.System.now(),
        metricName = GenAIMetrics.Client.Token.Usage.name,
        value = outputTokens,
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
        GenAIAttributes.Tool.Name(toolName),
        KoogAttributes.Koog.Tool.Call.Status(toolCallStatus)
    )

    return CounterMetricEvent(
        id = id,
        timestamp = Clock.System.now(),
        metricName = KoogMetrics.Tool.Count.name,
        attributes = attributes,
        1
    )
}
