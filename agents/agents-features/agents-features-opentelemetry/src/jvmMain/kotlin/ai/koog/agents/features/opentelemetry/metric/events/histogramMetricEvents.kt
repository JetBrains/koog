package ai.koog.agents.features.opentelemetry.metric.events

import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import ai.koog.agents.features.opentelemetry.metric.BaseMetricEvent
import ai.koog.agents.features.opentelemetry.metric.GenAIMetrics
import ai.koog.agents.features.opentelemetry.metric.HistogramMetricEvent
import ai.koog.prompt.llm.LLModel
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * Creates a timestamped placeholder metric event used to capture the start time of an operation.
 * The actual metric attributes and value are populated later when the operation completes
 * via [createLLMCallDurationHistogramMetricEvent] or [createExecuteToolDurationHistogramMetricEvent].
 */
internal fun AgentLifecycleEventContext.toTimestampedMetricEvent(): BaseMetricEvent {
    return BaseMetricEvent(
        id = this@toTimestampedMetricEvent.eventId,
        timestamp = Clock.System.now(),
        metricName = GenAIMetrics.Client.Operation.Duration.name,
        attributes = emptyList()
    )
}

internal fun createLLMCallDurationHistogramMetricEvent(
    id: String,
    model: LLModel,
    duration: Duration
): HistogramMetricEvent {
    val attributes =
        listOf(
            GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.TEXT_COMPLETION),
            GenAIAttributes.Provider.Name(model.provider),
            GenAIAttributes.Response.Model(model)
        )

    return HistogramMetricEvent(
        id = id,
        timestamp = Clock.System.now(),
        metricName = GenAIMetrics.Client.Operation.Duration.name,
        attributes = attributes,
        value = duration.toDouble(DurationUnit.SECONDS)
    )
}

internal fun createExecuteToolDurationHistogramMetricEvent(
    id: String,
    toolName: String,
    toolCallStatus: KoogAttributes.Koog.Tool.Call.StatusType,
    duration: Duration
): HistogramMetricEvent {
    val attributes =
        listOf(
            GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.EXECUTE_TOOL),
            GenAIAttributes.Tool.Name(toolName),
            KoogAttributes.Koog.Tool.Call.Status(toolCallStatus)
        )

    return HistogramMetricEvent(
        id = id,
        timestamp = Clock.System.now(),
        metricName = GenAIMetrics.Client.Operation.Duration.name,
        attributes = attributes,
        value = duration.toDouble(DurationUnit.SECONDS)
    )
}
