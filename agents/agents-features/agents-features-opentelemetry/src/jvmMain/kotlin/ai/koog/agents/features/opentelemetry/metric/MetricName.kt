package ai.koog.agents.features.opentelemetry.metric

internal sealed interface MetricName {
    val name: String
    val description: String
    val unit: String

    fun String.concatKey(other: String) = this.plus(".$other")
}
