package ai.koog.agents.features.opentelemetry.attribute

internal data class CustomAttribute(
    override val key: String,
    override val value: Any,
    override val verbose: Boolean = false
) : Attribute
