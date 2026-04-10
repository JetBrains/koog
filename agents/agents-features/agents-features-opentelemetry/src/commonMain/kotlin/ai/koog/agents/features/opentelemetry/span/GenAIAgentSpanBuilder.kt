package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.applyAttributes
import ai.koog.agents.features.opentelemetry.event.GenAIAgentEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.factory.ContextFactory
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.Tracer
import kotlin.time.Duration

internal class GenAIAgentSpanBuilder(
    private val spanType: SpanType,
    private val parentSpan: GenAIAgentSpan?,
    private val id: String,
    private val name: String,
    private val kind: SpanKind,
    private val startTimestamp: Duration? = null,
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

    fun buildAndStart(tracer: Tracer, contextFactory: ContextFactory): GenAIAgentSpan {
        val parentContext: Context = parentSpan?.context ?: contextFactory.root()

        val startedSpan = tracer.startSpan(
            name = name,
            parentContext = parentContext,
            spanKind = kind,
            startTimestamp = startTimestamp?.inWholeNanoseconds,
        ) {
            applyAttributes(attributes, verbose)
        }

        val context = contextFactory.storeSpan(parentContext, startedSpan)

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

        logger.debug { "${genAiSpan.logString} Span has been started." }

        return genAiSpan
    }
}
