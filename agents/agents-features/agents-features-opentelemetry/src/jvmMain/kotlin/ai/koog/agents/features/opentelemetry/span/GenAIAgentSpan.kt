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
 */
internal abstract class GenAIAgentSpan {

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
        get() = _context ?: error("Context for span '$id' is not initialized")
        set(value) {
            _context = value
        }

    /**
     * Represents the current active span within the `GenAIAgentSpan` context.
     * The span is initialized and managed as part of the tracing process.
     */
    var span: Span
        get() = _span ?: error("Span '$id' is not started")
        set(value) {
            _span = value
        }

    /**
     * The unique identifier for the span, providing a means to track and distinguish spans.
     */
    abstract val id: String

    /**
     * The name of the current span derived by removing the parent span ID prefix (if present)
     * from the current span ID and trimming leading dots. Represents a more human-readable
     * and simplified identifier for the current trace span.
     */
    abstract val name: String

    /**
     * Represents the kind of span that is being created or used.
     *
     * This property identifies the role and context of the span within a trace,
     * following predefined categories in OpenTelemetry's `SpanKind` enumeration.
     */
    open val kind: SpanKind = SpanKind.CLIENT

    /**
     * The parent span of the current span.
     */
    abstract val parentSpan: GenAIAgentSpan?

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

    fun addEvent(event: GenAIAgentEvent) {
        logger.debug { "$logString Adding event to the span: ${event.name}" }
        _events.add(event)
    }

    fun addEvents(events: List<GenAIAgentEvent>) {
        logger.debug { "$logString Adding <${events.size}> event(s) to the span. Events:\n${events.joinToString("\n") { "- ${it.name}" }}" }
        _events.addAll(events)
    }

    fun removeEvent(event: GenAIAgentEvent): Boolean {
        logger.debug { "$logString Removing event from span: ${event.name}" }
        return _events.remove(event)
    }
}
