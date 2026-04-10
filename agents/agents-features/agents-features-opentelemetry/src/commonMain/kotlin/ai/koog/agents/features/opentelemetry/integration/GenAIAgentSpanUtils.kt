package ai.koog.agents.features.opentelemetry.integration

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.event.EventBodyField
import ai.koog.agents.features.opentelemetry.event.GenAIAgentEvent
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.agents.utils.HiddenString

/**
 * Converts every body field of type [TBodyField] on [event] into a span attribute, then removes
 * those fields from the event.
 *
 * @param event Event whose body fields are processed.
 * @param attributeCreate Maps a matching body field to the resulting attribute.
 */
public inline fun <reified TBodyField : EventBodyField> GenAIAgentSpan.bodyFieldsToCustomAttribute(
    event: GenAIAgentEvent,
    attributeCreate: (TBodyField) -> Attribute
) {
    val span = this
    val eventBodyFields = event.bodyFields.filterIsInstance<TBodyField>().toList()

    eventBodyFields.forEach { bodyField ->
        val attributeFromEvent = attributeCreate(bodyField)
        span.addAttribute(attributeFromEvent)
    }

    eventBodyFields.forEach { bodyField -> event.removeBodyField(bodyField) }
}

/**
 * Runs [processBodyFieldAction] on every body field of type [TBodyField] in [event], then
 * removes those fields from the event.
 *
 * @param event Event whose body fields are processed.
 * @param processBodyFieldAction Action to run on each matching body field, with the span as receiver.
 */
public inline fun <reified TBodyField> GenAIAgentSpan.replaceBodyFields(
    event: GenAIAgentEvent,
    processBodyFieldAction: GenAIAgentSpan.(TBodyField) -> Unit
) where TBodyField : EventBodyField {
    val eventBodyFields = event.bodyFields.filterIsInstance<TBodyField>().toList()

    eventBodyFields.forEach { bodyField ->
        processBodyFieldAction(bodyField)
    }

    eventBodyFields.forEach { bodyField -> event.removeBodyField(bodyField) }
}

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
