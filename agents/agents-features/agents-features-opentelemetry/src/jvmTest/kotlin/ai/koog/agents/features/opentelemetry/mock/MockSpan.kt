package ai.koog.agents.features.opentelemetry.mock

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import java.util.concurrent.TimeUnit

/**
 * A mock implementation of Open Telemetry Span for testing.
 */
class MockSpan(): Span {

    var isStarted = true

    var isEnded = false

    var status: StatusCode? = null

    var statusDescription: String? = null

    override fun <T : Any?> setAttribute(key: AttributeKey<T?>, value: T?): Span = this

    override fun setAttribute(key: String, value: String?): Span = this

    override fun setAttribute(key: String, value: Boolean): Span = this

    override fun setAttribute(key: String, value: Long): Span = this

    override fun setAttribute(key: String, value: Double): Span = this

    override fun setAttribute(key: AttributeKey<Long>, value: Int): Span = this

    override fun addEvent(name: String, attributes: Attributes): Span = this
    override fun addEvent(name: String, attributes: Attributes, timestamp: Long, unit: TimeUnit): Span = this

    override fun setStatus(statusCode: StatusCode, description: String): Span {
        status = statusCode
        statusDescription = description
        return this
    }

    override fun recordException(exception: Throwable, additionalAttributes: Attributes): Span = this

    override fun updateName(name: String): Span = this

    override fun end() {
        isEnded = true
    }

    override fun end(timestamp: Long, unit: TimeUnit) {
        isEnded = true
    }

    override fun getSpanContext(): SpanContext = throw UnsupportedOperationException("Not implemented in test")

    override fun isRecording(): Boolean = isStarted && !isEnded

    override fun storeInContext(context: Context): Context = context
}
