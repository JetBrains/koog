package ai.koog.agents.features.opentelemetry.attribute

internal data class CustomAttribute(
    override val key: String,
    override val value: Any
) : Attribute
