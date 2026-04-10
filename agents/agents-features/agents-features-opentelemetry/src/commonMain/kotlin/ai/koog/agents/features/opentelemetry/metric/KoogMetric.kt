package ai.koog.agents.features.opentelemetry.metric

internal sealed interface KoogMetric : Metric {

    override val name: String
        get() = "koog.gen_ai"
}
