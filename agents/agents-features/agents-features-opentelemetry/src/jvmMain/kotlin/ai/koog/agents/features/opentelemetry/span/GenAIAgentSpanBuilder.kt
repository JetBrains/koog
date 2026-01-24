package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.event.GenAIAgentEvent
import ai.koog.agents.features.opentelemetry.extension.setAttributes
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import java.time.Instant

internal class GenAIAgentSpanBuilder(
    private val spanType: SpanType,
    private val parentSpan: GenAIAgentSpan?,
    private val id: String,
    private val name: String,
    private val kind: SpanKind,
    private val instant: Instant? = null,
    private val verbose: Boolean = false,
) {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    private val attributes: MutableList<Attribute> = mutableListOf()

    private val events: MutableList<GenAIAgentEvent> = mutableListOf()

    fun addAttribute(attribute: Attribute): GenAIAgentSpanBuilder {
        attributes.add(attribute)
        return this
    }

    fun buildAndStart(tracer: Tracer): GenAIAgentSpan {
        val parentContext = parentSpan?.context ?: Context.current()

        val spanBuilder = tracer.spanBuilder(name)
            .setStartTimestamp(instant ?: Instant.now())
            .setSpanKind(kind)
            .setParent(parentContext)

        spanBuilder.setAttributes(attributes, verbose)

        val startedSpan = spanBuilder.startSpan()
        val context = startedSpan.storeInContext(parentContext)

        val genAiSpan = GenAIAgentSpan(
            parentSpan = parentSpan,
            type = spanType,
            span = startedSpan,
            id = id,
            name = name,
            kind = kind,
            context = context,
            attributes = attributes.toList(),
            events = events.toList(),
        )

        startedSpan

        logger.debug { "${genAiSpan.logString} Span has been started." }

        return genAiSpan
    }
}
