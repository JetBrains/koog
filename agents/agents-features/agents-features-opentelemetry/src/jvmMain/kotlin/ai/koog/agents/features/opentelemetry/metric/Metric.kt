package ai.koog.agents.features.opentelemetry.metric

internal sealed interface Metric {
    val name: String
    val description: String
    val unit: String

    fun String.concatKey(other: String) = this.plus(".$other")
}

internal interface CounterMetric : Metric

internal interface HistogramMetric : Metric {
    val boundariesAdvice: List<Double>
}
