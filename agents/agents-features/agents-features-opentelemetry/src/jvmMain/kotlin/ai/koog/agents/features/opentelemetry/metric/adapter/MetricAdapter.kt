package ai.koog.agents.features.opentelemetry.metric.adapter

import ai.koog.agents.features.opentelemetry.metric.MetricEvent

internal abstract class MetricAdapter {

    abstract fun <T : MetricEvent> process(metricEvent: T): T
}
