package ai.koog.agents.features.opentelemetry.metric.events

import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.llm.LLMCallStartingContext
import ai.koog.agents.core.feature.model.AIAgentError
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
 * Placeholder event that captures only the start timestamp. Attributes and value are filled in
 * on completion by [createExecuteToolDurationHistogramMetricEvent].
 */
internal fun AgentLifecycleEventContext.toTimestampedMetricEvent(): BaseMetricEvent {
    return BaseMetricEvent(
        id = this@toTimestampedMetricEvent.eventId,
        timestamp = Clock.System.now(),
        metricName = GenAIMetrics.Client.Operation.Duration.name,
        attributes = emptyList()
    )
}

/**
 * Start-time event pre-populated with the attributes required to record a failed duration
 * measurement if the LLM call never completes (e.g. the agent run fails before
 * [createLLMCallDurationHistogramMetricEvent] runs).
 */
internal fun LLMCallStartingContext.toLLMCallStartMetricEvent(): BaseMetricEvent {
    val attributes = listOf(
        GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.TEXT_COMPLETION),
        GenAIAttributes.Provider.Name(model.provider),
        GenAIAttributes.Request.Model(model),
        GenAIAttributes.Response.Model(model),
    )
    return BaseMetricEvent(
        id = eventId,
        timestamp = Clock.System.now(),
        metricName = GenAIMetrics.Client.Operation.Duration.name,
        attributes = attributes,
    )
}

internal fun createLLMCallDurationHistogramMetricEvent(
    id: String,
    model: LLModel,
    duration: Duration,
    error: Throwable? = null,
): HistogramMetricEvent {
    val attributes = buildList {
        add(GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.TEXT_COMPLETION))
        add(GenAIAttributes.Provider.Name(model.provider))
        add(GenAIAttributes.Request.Model(model))
        add(GenAIAttributes.Response.Model(model))
        error?.errorTypeAttribute()?.let { add(it) }
    }

    return HistogramMetricEvent(
        id = id,
        timestamp = Clock.System.now(),
        metricName = GenAIMetrics.Client.Operation.Duration.name,
        attributes = attributes,
        value = duration.toDouble(DurationUnit.SECONDS)
    )
}

/**
 * Converts a pending start-time event (see [toLLMCallStartMetricEvent]) into a failed-duration
 * histogram event, appending `error.type` as required by the OTel GenAI semantic conventions.
 */
internal fun BaseMetricEvent.toFailedDurationHistogramMetricEvent(
    error: Throwable?,
    duration: Duration,
): HistogramMetricEvent {
    val errorAttribute = error?.errorTypeAttribute() ?: CommonAttributes.Error.Type("_OTHER")
    return HistogramMetricEvent(
        id = id,
        timestamp = Clock.System.now(),
        metricName = metricName,
        attributes = attributes + errorAttribute,
        value = duration.toDouble(DurationUnit.SECONDS)
    )
}

internal fun createExecuteToolDurationHistogramMetricEvent(
    id: String,
    toolName: String,
    toolCallStatus: KoogAttributes.Koog.Tool.Call.StatusType,
    duration: Duration,
    error: AIAgentError? = null,
): HistogramMetricEvent {
    val attributes = buildList {
        add(GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.EXECUTE_TOOL))
        add(GenAIAttributes.Provider.Name(KoogAttributes.PROVIDER_NAME))
        add(GenAIAttributes.Tool.Name(toolName))
        add(KoogAttributes.Koog.Tool.Call.Status(toolCallStatus))
        error?.errorTypeAttribute()?.let { add(it) }
    }

    return HistogramMetricEvent(
        id = id,
        timestamp = Clock.System.now(),
        metricName = GenAIMetrics.Client.Operation.Duration.name,
        attributes = attributes,
        value = duration.toDouble(DurationUnit.SECONDS)
    )
}

private fun Throwable.errorTypeAttribute(): CommonAttributes.Error.Type =
    CommonAttributes.Error.Type(this::class.java.typeName)

private fun AIAgentError.errorTypeAttribute(): CommonAttributes.Error.Type =
    CommonAttributes.Error.Type(this::class.java.typeName)
