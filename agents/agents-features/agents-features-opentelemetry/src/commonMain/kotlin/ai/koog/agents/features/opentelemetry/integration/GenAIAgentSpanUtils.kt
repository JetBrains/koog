package ai.koog.agents.features.opentelemetry.integration

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.agents.utils.HiddenString

/**
 * Replaces every attribute of type [TAttribute] on this span with the result of
 * [processAttributeAction]. No-op if none are present.
 *
 * @param processAttributeAction Maps an existing attribute to its replacement.
 */
public inline fun <reified TAttribute : Attribute> GenAIAgentSpan.replaceAttributes(
    processAttributeAction: GenAIAgentSpan.(TAttribute) -> Attribute
) {
    val attributesToReplace = this.attributes.filterIsInstance<TAttribute>()

    if (attributesToReplace.isEmpty()) {
        return
    }

    attributesToReplace.forEach { attributeToReplace ->
        val newAttribute = processAttributeAction(attributeToReplace)
        removeAttribute(attributeToReplace)
        addAttribute(newAttribute)
    }
}

/**
 * `true` if this value is a primitive the OpenTelemetry SDK can store directly in an attribute
 * array.
 */
public val Any.isSdkArrayPrimitive: Boolean
    get() = this is HiddenString ||
        this is CharSequence ||
        this is Char ||
        this is Boolean ||
        this is Long ||
        this is Int ||
        this is Float ||
        this is Double
