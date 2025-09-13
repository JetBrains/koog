package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.event.GenAIAgentEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context

/**
 * Represents an abstract base class for a GenAI agent span in a trace.
 * A span represents a logical unit of work or operation within a trace and is
 * responsible for managing associated metadata, such as context, attributes, and events.
 *
 * @property parent The parent span. Null if this span is a root span.
 */
internal abstract class GenAIAgentSpan(
    val parent: GenAIAgentSpan?,
) {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    private var _context: Context? = null

    private var _span: Span? = null

    /**
     * Represents the context associated with the current span. The context provides
     * metadata and state information required to manage and propagate span information
     * effectively within the tracing framework.
     */
    var context: Context
        get() = _context ?: error("Context for span '$spanId' is not initialized")
        set(value) {
            _context = value
        }

    /**
     * Represents the current active span within the `GenAIAgentSpan` context.
     * The span is initialized and managed as part of the tracing process.
     */
    var span: Span
        get() = _span ?: error("Span '$spanId' is not started")
        set(value) {
            _span = value
        }

    /**
     * The name of the current span derived by removing the parent span ID prefix (if present)
     * from the current span ID and trimming leading dots. Represents a more human-readable
     * and simplified identifier for the current trace span.
     */
    val name: String
        get() = spanId.removePrefix(parent?.spanId ?: "").trimStart('.')

    /**
     * Represents the kind of span that is being created or used.
     *
     * This property identifies the role and context of the span within a trace,
     * following predefined categories in OpenTelemetry's `SpanKind` enumeration.
     */
    open val kind: SpanKind = SpanKind.CLIENT

    /**
     * The unique identifier for the span, providing a means to track and distinguish spans.
     */
    abstract val spanId: String

    private val _attributes = mutableListOf<Attribute>()

    private val _events = mutableListOf<GenAIAgentEvent>()

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

    fun addAttribute(attribute: Attribute) {
        logger.debug { "Adding attribute to span (name: $name, id: $spanId): ${attribute.key}" }
        _attributes.add(attribute)
    }

    fun addAttributes(attributes: List<Attribute>) {
        logger.debug { "Adding ${attributes.size} attributes to span (name: $name, id: $spanId):\n${attributes.joinToString("\n") { "- ${it.key}" }}" }
        _attributes.addAll(attributes)
    }

    fun removeAttribute(attribute: Attribute): Boolean {
        logger.debug { "Removing attribute from span (name: $name, id: $spanId): ${attribute.key}" }
        return _attributes.remove(attribute)
    }

    fun addEvent(event: GenAIAgentEvent) {
        logger.debug { "Adding event to span (name: $name, id: $spanId): ${event.name}" }
        _events.add(event)
    }

    fun addEvents(events: List<GenAIAgentEvent>) {
        logger.debug { "Adding ${events.size} events to span (name: $name, id: $spanId):\n${events.joinToString("\n") { "- ${it.name}" }}" }
        _events.addAll(events)
    }

    fun removeEvent(event: GenAIAgentEvent): Boolean {
        logger.debug { "Removing event from span (name: $name, id: $spanId): ${event.name}" }
        return _events.remove(event)
    }
}
