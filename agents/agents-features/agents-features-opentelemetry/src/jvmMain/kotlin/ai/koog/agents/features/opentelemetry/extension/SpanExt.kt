package ai.koog.agents.features.opentelemetry.extension

import ai.koog.agents.core.feature.model.AIAgentError
import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.toSdkAttributes
import ai.koog.agents.features.opentelemetry.event.GenAIAgentEvent
import ai.koog.agents.features.opentelemetry.span.SpanEndStatus
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode

internal fun Span.setSpanStatus(endStatus: SpanEndStatus? = null) {
    val statusCode = endStatus?.code ?: StatusCode.OK
    val statusDescription = endStatus?.description ?: ""
    this.setStatus(statusCode, statusDescription)
}

internal fun Span.setSpanStatus(error: Throwable? = null) {
    if (error == null) {
        this.setStatus(StatusCode.OK)
        return
    }

    val statusCode = StatusCode.ERROR
    val statusDescription = error.message
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
            // Collect all body fields into separate attributes.
            // Please use [bodyFieldsToBodyAttribute] method to collect body fields into a single attribute
            // with the 'body' key and JSON string structure as a value.
            event.bodyFieldsToAttributes(verbose)
            addAll(event.attributes)
        }

        addEvent(event.name, attributes.toSdkAttributes(verbose))
    }
}

internal fun Throwable?.toSpanEndStatus(): SpanEndStatus =
    if (this == null) {
        SpanEndStatus(code = StatusCode.OK)
    } else {
        SpanEndStatus(code = StatusCode.ERROR, description = this.message)
    }

internal fun AIAgentError?.toSpanEndStatus(): SpanEndStatus =
    if (this == null) {
        SpanEndStatus(code = StatusCode.OK)
    } else {
        SpanEndStatus(code = StatusCode.ERROR, description = this.message)
    }
