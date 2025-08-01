package ai.koog.agents.features.opentelemetry.attribute

internal interface Attribute {
    val key: String
    val value: Any

    val verbose: Boolean
        get() = false

    fun String.concatKey(other: String) = this.plus(".$other")
}
