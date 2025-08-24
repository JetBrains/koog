package ai.koog.agents.features.opentelemetry.extension

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.event.EventBodyField

internal fun EventBodyField.toCustomAttribute(
    attributeCreator: ((EventBodyField) -> Attribute)? = null
): Attribute {
    return attributeCreator?.invoke(this)
        ?: CustomAttribute(key, value)
}
