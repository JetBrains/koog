package ai.koog.agents.features.opentelemetry.extension

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.event.EventBodyField
import ai.koog.agents.features.opentelemetry.event.GenAIAgentEvent
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan

internal fun GenAIAgentEvent.toSpanAttributes(span: GenAIAgentSpan) {
    // Convert event attributes to Span Attributes
    attributes.forEach { attribute -> span.addAttribute(attribute) }

    // Convert Body Fields to Span Attributes
    bodyFields.forEach { bodyField ->
        val attribute = bodyField.toGenAIAttribute()
        span.addAttribute(attribute)
    }
}

internal fun EventBodyField.toGenAIAttribute(): Attribute {
    return CustomAttribute(key, value)
}
