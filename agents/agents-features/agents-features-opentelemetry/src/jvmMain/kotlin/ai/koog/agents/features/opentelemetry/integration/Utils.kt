package ai.koog.agents.features.opentelemetry.integration

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.event.EventBodyField
import ai.koog.agents.features.opentelemetry.event.GenAIAgentEvent
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.agents.utils.HiddenString

/**
 * Transfers specific body fields of an event to custom attributes in the span
 * and subsequently removes these fields from the event's body.
 *
 * @param TBodyField The type of event body field to be processed, which must be a subclass of EventBodyField.
 * @param event The event containing body fields to be processed.
 * @param attributeCreate A function that transforms a body field of type TBodyField into an Attribute.
 */
internal inline fun <reified TBodyField : EventBodyField> GenAIAgentSpan.bodyFieldsToCustomAttribute(
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
 * Replaces specific body fields in a GenAI agent event by executing a specified action and
 * then removing them from the event.
 *
 * @param TBodyField The type of body fields to be replaced, extending EventBodyField.
 * @param event The GenAI agent event whose body fields are being processed.
 * @param processBodyFieldAction A lambda function defining the action to be performed on each matching
 *                                body field within the context of the span.
 */
internal inline fun <reified TBodyField> GenAIAgentSpan.replaceBodyFields(
    event: GenAIAgentEvent,
    processBodyFieldAction: GenAIAgentSpan.(TBodyField) -> Unit
) where TBodyField : EventBodyField {
    val eventBodyFields = event.bodyFields.filterIsInstance<TBodyField>().toList()

    eventBodyFields.forEach { bodyField ->
        processBodyFieldAction(bodyField)
    }

    eventBodyFields.forEach { bodyField -> event.removeBodyField(bodyField) }
}

internal val Any.isSdkArrayPrimitive
    get() = this is HiddenString ||
        this is CharSequence ||
        this is Char ||
        this is Boolean ||
        this is Long ||
        this is Int ||
        this is Float ||
        this is Double
