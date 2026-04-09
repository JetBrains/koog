package ai.koog.agents.features.opentelemetry.metric.events

import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
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
    duration: Duration,
    errorType: String? = null
): HistogramMetricEvent {
    val attributes = buildList<Attribute> {
        add(GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.TEXT_COMPLETION))
        add(GenAIAttributes.Provider.Name(model.provider))
        add(GenAIAttributes.Request.Model(model))
        add(GenAIAttributes.Response.Model(model))
        if (errorType != null) {
            add(CommonAttributes.Error.Type(errorType))
        }
    }

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
    duration: Duration,
    model: LLModel,
    errorType: String? = null
): HistogramMetricEvent {
    val attributes = buildList<Attribute> {
        add(GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.EXECUTE_TOOL))
        add(GenAIAttributes.Provider.Name(model.provider))
        add(GenAIAttributes.Tool.Name(toolName))
        add(KoogAttributes.Koog.Tool.Call.Status(toolCallStatus))
        if (errorType != null) {
            add(CommonAttributes.Error.Type(errorType))
        }
    }

    return HistogramMetricEvent(
        id = id,
        timestamp = Clock.System.now(),
        metricName = GenAIMetrics.Client.Operation.Duration.name,
        attributes = attributes,
        value = duration.toDouble(DurationUnit.SECONDS)
    )
}
