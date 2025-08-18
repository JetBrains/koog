package ai.koog.agents.features.opentelemetry.extension

import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.event.GenAIAgentEvent
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan

/**
 * Converts the attributes and body fields of a GenAIAgentEvent into span attributes
 * and adds them to the specified {@link GenAIAgentSpan}.
 *
 * @param span The span to which the converted attributes will be added.
 */
internal fun GenAIAgentEvent.toSpanAttributes(
    span: GenAIAgentSpan
) {
    // 1. Convert all event body fields into attributes
    this.bodyFieldsToAttributes()

    // 2. Convert all event attributes into the span attributes
    attributes.forEach { attribute -> span.addAttribute(attribute) }
}

/**
 * Converts the body fields of the event into attributes and adds them to the event.
 * Each body field is transformed into a custom attribute representation using the
 * `toCustomAttribute` method, and the result is added as an attribute to the event.
 */
internal fun GenAIAgentEvent.bodyFieldsToAttributes() {
    val bodyFieldsToProcess = bodyFields.toList()

    // Convert each Body Field into an event attribute
    bodyFieldsToProcess.forEach { bodyField ->
        val attribute = bodyField.toCustomAttribute()
        this.addAttribute(attribute)
    }

    // Clean all converted body fields
    bodyFieldsToProcess.forEach { bodyField -> removeBodyField(bodyField) }
}

/**
 * Converts the `bodyFields` of the event into a single JSON-like string representation and adds it
 * as a custom "body" attribute to the event's attribute list.
 *
 * If the `bodyFields` list is empty, the method does nothing.
 */
internal fun GenAIAgentEvent.bodyFieldsToBodyAttribute(verbose: Boolean) {
    if (bodyFields.isEmpty()) {
        return
    }

    val bodyFieldsToProcess = bodyFields.toList()

    val value = bodyFieldsToProcess.joinToString(separator = ",", prefix = "{", postfix = "}") { bodyField ->
        "\"${bodyField.key}\":${bodyField.valueString(verbose)}"
    }

    // Add a new 'body' attribute for the event
    addAttribute(CustomAttribute("body", value))

    // Clean all converted body fields
    bodyFieldsToProcess.forEach { bodyField -> this.removeBodyField(bodyField) }
}
