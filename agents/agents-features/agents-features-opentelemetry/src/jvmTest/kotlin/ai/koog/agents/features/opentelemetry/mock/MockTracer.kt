package ai.koog.agents.features.opentelemetry.mock

import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import java.util.concurrent.TimeUnit

/**
 * A mock implementation of the OpenTelemetry Tracer interface for testing.
 * This class creates MockSpanBuilder instances that can create MockSpan instances.
 */
class MockTracer() : Tracer {
    val createdSpans = mutableListOf<MockSpan>()
    
    override fun spanBuilder(spanName: String): SpanBuilder {
        return MockSpanBuilder(this)
    }

    fun clear() {
        createdSpans.clear()
    }
}

/**
 * A mock implementation of the OpenTelemetry SpanBuilder interface for testing.
 * This class creates MockSpan instances.
 */
class MockSpanBuilder(
    private val tracer: MockTracer
) : SpanBuilder {
    private var parent: GenAIAgentSpan? = null
    private var startTimestamp: Long = System.currentTimeMillis()
    private var startTimestampUnit: TimeUnit = TimeUnit.MILLISECONDS
    private val attributes = mutableMapOf<String, Any?>()
    
    override fun setParent(context: Context): SpanBuilder {
        return this
    }

    override fun setNoParent(): SpanBuilder {
        parent = null
        return this
    }

    override fun addLink(spanContext: SpanContext): SpanBuilder {
        return this
    }

    override fun addLink(spanContext: SpanContext, attributes: Attributes): SpanBuilder {
        return this
    }

    override fun setAttribute(key: String, value: String): SpanBuilder {
        attributes[key] = value
        return this
    }

    override fun setAttribute(key: String, value: Long): SpanBuilder {
        attributes[key] = value
        return this
    }

    override fun setAttribute(key: String, value: Double): SpanBuilder {
        attributes[key] = value
        return this
    }

    override fun setAttribute(key: String, value: Boolean): SpanBuilder {
        attributes[key] = value
        return this
    }

    override fun <T : Any?> setAttribute(key: AttributeKey<T?>, value: T & Any): SpanBuilder {
        attributes[key.key] = value
        return this
    }

    override fun setSpanKind(spanKind: SpanKind): SpanBuilder {
        return this
    }

    override fun setStartTimestamp(startTimestamp: Long, unit: TimeUnit): SpanBuilder {
        this.startTimestamp = startTimestamp
        this.startTimestampUnit = unit
        return this
    }

    override fun startSpan(): Span {
        val mockSpan = MockSpan()
        tracer.createdSpans.add(mockSpan)
        return mockSpan
    }
}
