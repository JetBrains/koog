package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.event.GenAIAgentEvent
import ai.koog.agents.features.opentelemetry.extension.setAttributes
import ai.koog.agents.features.opentelemetry.extension.setEvents
import ai.koog.agents.features.opentelemetry.extension.setSpanStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal class SpanProcessor(
    private val tracer: Tracer,
    private val verbose: Boolean = false
) {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    private val _spans = ConcurrentHashMap<String, GenAIAgentSpan>()

    private val spansLock = ReentrantReadWriteLock()

    val spansCount: Int
        get() = _spans.count()

    fun addEventsToSpan(spanId: String, events: List<GenAIAgentEvent>) {
        spansLock.read {
            val span = _spans[spanId] ?: error("Span with id '$spanId' not found")
            span.addEvents(events)
        }
    }

    fun startSpan(
        span: GenAIAgentSpan,
        instant: Instant? = null,
    ) {
        logger.debug { "Starting span (name: ${span.name}, id: ${span.spanId})" }

        if (_spans.containsKey(span.spanId)) {
            logger.warn { "Span with id '${span.spanId}' already started" }
            return
        }

        val spanKind = span.kind
        val parentContext = span.parent?.context ?: Context.current()

        val spanBuilder = tracer.spanBuilder(span.name)
            .setStartTimestamp(instant ?: Instant.now())
            .setSpanKind(spanKind)
            .setParent(parentContext)

        spanBuilder.setAttributes(span.attributes, verbose)

        val startedSpan = spanBuilder.startSpan()

        // Store newly started span
        addSpan(span)

        // Update span context and span properties
        span.span = startedSpan
        span.context = startedSpan.storeInContext(parentContext)

        logger.debug { "Span has been started (name: ${span.name}, id: ${span.spanId})" }
    }

    fun endSpan(
        span: GenAIAgentSpan,
        spanEndStatus: SpanEndStatus? = null
    ) {
        logger.debug { "Finishing the span (id: ${span.spanId})" }

        val spanToFinish = span.span

        spanToFinish.setAttributes(span.attributes, verbose)
        spanToFinish.setEvents(span.events, verbose)
        spanToFinish.setSpanStatus(spanEndStatus)
        spanToFinish.end()

        val removedSpan = _spans.remove(span.spanId)
        if (removedSpan == null) {
            logger.warn {
                "Span with id '${span.spanId}' not found. Make sure you do not delete span with same id several times"
            }
        }
    }

    inline fun <reified T : GenAIAgentSpan> getSpan(spanId: String): T? {
        return _spans[spanId] as? T
    }

    inline fun <reified T : GenAIAgentSpan> getSpanOrThrow(spanId: String): T {
        val span = _spans[spanId] ?: error("Span with id: $spanId not found")
        return span as? T
            ?: error(
                "Span with id <$spanId> is not of expected type. Expected: <${T::class.simpleName}>, actual: <${span::class.simpleName}>"
            )
    }

    inline fun <reified T : GenAIAgentSpan> getSpanCatching(spanId: String): T? {
        val getSpanResult = runCatching { getSpanOrThrow<T>(spanId) }
        if (getSpanResult.isSuccess) {
            return getSpanResult.getOrNull()
        }

        val throwable = getSpanResult.exceptionOrNull()
        logger.error(throwable) { "Unable to get a span with id: $spanId. Error: ${throwable?.message}" }
        return null
    }

    fun endUnfinishedSpans(filter: (GenAIAgentSpan) -> Boolean = { true }) {
        _spans.values
            .filter { span ->
                val isRequireFinish = filter(span)
                isRequireFinish
            }
            .forEach { span ->
                logger.warn { "Force close span with id: ${span.spanId}" }
                endSpan(
                    span = span,
                    spanEndStatus = SpanEndStatus(StatusCode.UNSET)
                )
            }
    }

    fun endUnfinishedInvokeAgentSpans(agentId: String, runId: String) {
        val agentRunSpanId = InvokeAgentSpan.createId(agentId, runId)
        val agentSpanId = CreateAgentSpan.createId(agentId)

        endUnfinishedSpans(filter = { span -> span.spanId != agentSpanId && span.spanId != agentRunSpanId })
    }

    //region Private Methods

    private fun addSpan(span: GenAIAgentSpan) {
        spansLock.write {
            val spanId = span.spanId
            val existingSpan = _spans[spanId]

            check(existingSpan == null) { "Span with id '$spanId' already added" }

            _spans[span.spanId] = span
        }
    }

    //endregion Private Methods
}
