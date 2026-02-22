package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.event.GenAIAgentEvent
import ai.koog.agents.features.opentelemetry.extension.setAttributes
import ai.koog.agents.features.opentelemetry.extension.setEvents
import ai.koog.agents.features.opentelemetry.extension.setSpanStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context

/**
 * Represents an abstract base class for a GenAI agent span in a trace.
 * A span represents a logical unit of work or operation within a trace and is
 * responsible for managing associated metadata, such as context, attributes, and events.
 */
internal class GenAIAgentSpan(
    val type: SpanType,
    val parentSpan: GenAIAgentSpan?,
    val id: String,
    val name: String,
    val span: Span,
    val context: Context,
    val kind: SpanKind,
    attributes: List<Attribute>,
    events: List<GenAIAgentEvent>,
) {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    private val _attributes: MutableList<Attribute> = attributes.toMutableList()

    private val _events: MutableList<GenAIAgentEvent> = events.toMutableList()

    /**
     * Provides a list of attributes associated with the span.
     * These attributes contain metadata and additional information about the span.
     */
    val attributes: List<Attribute>
        get() = _attributes

    /**
     * Provides access to the list of events associated with this span.
     * The events represent specific occurrences or milestones within the context of this span.
     */
    val events: List<GenAIAgentEvent>
        get() = _events

    val logString: String
        get() = "${this.javaClass.simpleName} (name: $name, id: $id)"

    fun addAttribute(attribute: Attribute) {
        logger.debug { "$logString Adding attribute to the span: ${attribute.key}" }

        val existingAttribute = attributes.find { it.key == attribute.key }
        if (existingAttribute != null) {
            logger.debug { "$logString Attribute with key '${attribute.key}' already exists. Overwriting existing attribute value." }
            removeAttribute(existingAttribute)
        }
        _attributes.add(attribute)
    }

    fun addAttributes(attributes: List<Attribute>) {
        logger.debug { "$logString Adding <${attributes.size}> attribute(s) to the span. Attributes:\n${attributes.joinToString("\n") { "- ${it.key}" }}" }
        attributes.forEach { addAttribute(it) }
    }

    fun removeAttribute(attribute: Attribute): Boolean {
        logger.debug { "$logString Removing attribute from span: ${attribute.key}" }
        return _attributes.remove(attribute)
    }

    // TODO: Update to add event directly into the OTel sdk span
    fun addEvent(event: GenAIAgentEvent) {
        logger.debug { "$logString Adding event to the span: ${event.name}" }
        _events.add(event)
    }

    // TODO: Update to add event directly into the OTel sdk span
    fun addEvents(events: List<GenAIAgentEvent>) {
        logger.debug { "$logString Adding <${events.size}> event(s) to the span. Events:\n${events.joinToString("\n") { "- ${it.name}" }}" }
        _events.addAll(events)
    }

    // TODO: Deprecate
    fun removeEvent(event: GenAIAgentEvent): Boolean {
        logger.debug { "$logString Removing event from span: ${event.name}" }
        return _events.remove(event)
    }

    fun end(
        spanEndStatus: SpanEndStatus? = null,
        verbose: Boolean = false,
    ) {
        logger.debug { "$logString Finishing the span." }

        span.setAttributes(attributes, verbose)
        span.setEvents(events, verbose)
        span.setSpanStatus(spanEndStatus)
        span.end()

        logger.debug { "$logString Span has been finished." }
    }
}
