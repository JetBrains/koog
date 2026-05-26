package ai.koog.agents.features.opentelemetry.mock

import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.opentelemetry.kotlin.tracing.model.ReadWriteSpan
import io.opentelemetry.kotlin.tracing.model.ReadableSpan

/**
 * A synchronous [SpanProcessor] that collects spans in memory for test assertions.
 *
 * Unlike [MockSpanExporter] used with `simpleSpanProcessor`, this processor receives spans via
 * [onEnd] which is called synchronously by the SDK inside its span-ending lock. This eliminates
 * the async hop of `scope.launch` introduced by `simpleSpanProcessor`, making span assertions
 * safe immediately after `agent.run()` returns with no polling or grace delays.
 */
internal class TestSpanProcessor : SpanProcessor {
    private val lock = Any()
    private val _collectedSpans = mutableListOf<SpanData>()

    val collectedSpans: List<SpanData>
        get() = synchronized(lock) { _collectedSpans.toList() }

    val lastRunId: String
        get() = collectedSpans
            .mapNotNull { it.attributes["gen_ai.conversation.id"] as? String }
            .distinct()
            .last()

    override fun onStart(span: ReadWriteSpan, parentContext: Context) {}

    override fun onEnding(span: ReadWriteSpan) {}

    override fun onEnd(span: ReadableSpan) {
        synchronized(lock) { _collectedSpans.add(span.toSpanData()) }
    }

    override fun isStartRequired(): Boolean = false

    override fun isEndRequired(): Boolean = true

    override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success

    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
}
