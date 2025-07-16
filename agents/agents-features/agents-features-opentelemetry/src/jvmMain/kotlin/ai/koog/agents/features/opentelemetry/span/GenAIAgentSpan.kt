package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.toSdkAttributes
import ai.koog.agents.features.opentelemetry.event.GenAIAgentEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context

internal abstract class GenAIAgentSpan(
    val parent: GenAIAgentSpan?,
) {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    private var _context: Context? = null

    private var _span: Span? = null


    var context: Context
        get() = _context ?: error("Context for span '${spanId}' is not initialized")
        set(value) {
            _context = value
        }

    var span: Span
        get() = _span ?: error("Span '${spanId}' is not started")
        set(value) {
            _span = value
        }

    val name: String
        get() = spanId.removePrefix(parent?.spanId ?: "").trimStart('.')


    open val kind: SpanKind = SpanKind.CLIENT

    abstract val spanId: String

    abstract val attributes: List<Attribute>


    fun addEvents(events: List<GenAIAgentEvent>) {
        events.forEach { event ->
            logger.debug { "Adding event '${event.name}' to span '${spanId}'" }

            // The 'opentelemetry-java' SDK does not have support for event body fields at the moment.
            // Pass body fields as attributes until an API is updated.
            val attributes = buildList {
                addAll(event.attributes)
                if (event.bodyFields.isNotEmpty()) {
                    add(event.bodyFieldsAsAttribute())
                }
            }

            span.addEvent(event.name, attributes.toSdkAttributes())
        }
    }
}
