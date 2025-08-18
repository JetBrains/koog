package ai.koog.agents.features.opentelemetry.extension

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.toSdkAttributes
import ai.koog.agents.features.opentelemetry.event.EventBodyField
import ai.koog.agents.features.opentelemetry.event.GenAIAgentEvent
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.agents.features.opentelemetry.span.SpanEndStatus
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode

internal fun Span.setSpanStatus(endStatus: SpanEndStatus? = null) {
    val statusCode = endStatus?.code ?: StatusCode.OK
    val statusDescription = endStatus?.description ?: ""
    this.setStatus(statusCode, statusDescription)
}

internal fun SpanBuilder.setAttributes(attributes: List<Attribute>, verbose: Boolean) {
    setAllAttributes(attributes.toSdkAttributes(verbose))
}

internal fun Span.setAttributes(attributes: List<Attribute>, verbose: Boolean) {
    setAllAttributes(attributes.toSdkAttributes(verbose))
}

internal fun Span.setEvents(events: List<GenAIAgentEvent>, verbose: Boolean) {
    events.forEach { event ->
        // The 'opentelemetry-java' SDK does not have support for event body fields at the moment.
        // Pass body fields as attributes until an API is updated.
        val attributes = buildList {
            addAll(event.attributes)
            if (event.bodyFields.isNotEmpty()) {
                add(event.bodyFieldsAsAttribute(verbose))
            }
        }

        addEvent(event.name, attributes.toSdkAttributes(verbose))
    }
}

internal inline fun <reified TBodyField> GenAIAgentSpan.eventBodyFieldToAttribute(
    event: GenAIAgentEvent,
    attributeCreate: (TBodyField) -> Attribute
) where TBodyField : EventBodyField {
    event.bodyFields.filterIsInstance<TBodyField>().forEach { bodyField ->
        val attributeFromEvent = attributeCreate(bodyField)
        this.addAttribute(attributeFromEvent)
    }

    this.removeEvent(event)
}
