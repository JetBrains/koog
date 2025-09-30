package ai.koog.agents.features.opentelemetry.attribute

/**
 * A custom attribute with a key-value pair.
 *
 * @param key the attribute key
 * @param value the attribute value
 */
public data class CustomAttribute(
    override val key: String,
    override val value: Any
) : Attribute
