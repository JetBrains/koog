package ai.koog.agents.features.opentelemetry.metric

internal sealed interface KoogMetricName : MetricName {
    override val name: String
        get() = "koog"
}
