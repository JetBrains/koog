package ai.koog.agents.features.opentelemetry.metric

internal sealed interface GenAIMetricName : MetricName {
    override val name: String
        get() = "gen_ai"
}
