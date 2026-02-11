package ai.koog.agents.features.opentelemetry.metric.events

import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import ai.koog.agents.features.opentelemetry.metric.BaseMetricEvent
import ai.koog.agents.features.opentelemetry.metric.GenAIMetrics
import ai.koog.agents.features.opentelemetry.metric.HistogramMetricEvent
import ai.koog.agents.features.opentelemetry.metric.MetricEvent
import ai.koog.prompt.llm.LLModel
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.DurationUnit

internal fun AgentLifecycleEventContext.toMetricEvent(): MetricEvent {
    return BaseMetricEvent(
        id = this@toMetricEvent.eventId,
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
