package ai.koog.agents.features.opentelemetry.metric

internal sealed interface GenAIMetric : Metric {
    override val name: String
        get() = "gen_ai"
}
