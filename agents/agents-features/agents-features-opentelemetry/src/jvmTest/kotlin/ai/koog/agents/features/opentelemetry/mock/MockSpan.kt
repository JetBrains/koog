package ai.koog.agents.features.opentelemetry.mock

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * A mock implementation of Open Telemetry Span for testing.
 */
class MockSpan() : Span {

    var isStarted = true

    var isEnded = false

    var status: StatusCode? = null

    var statusDescription: String? = null

    private val _collectedAttributes = mutableMapOf<AttributeKey<*>, Any?>()

    private val _collectedEvents = mutableMapOf<String, Attributes>()

    val collectedAttributes: Map<AttributeKey<*>, Any?>
        get() = _collectedAttributes.toMap()

    val collectedEvents: Map<String, Attributes>
        get() = _collectedEvents.toMap()

    override fun <T : Any?> setAttribute(key: AttributeKey<T?>, value: T?): Span {
        _collectedAttributes[key] = value
        return this
    }

    override fun setAttribute(key: String, value: String?): Span {
        _collectedAttributes[AttributeKey.stringKey(key)] = value
        return this
    }
    override fun setAttribute(key: String, value: Boolean): Span {
        _collectedAttributes[AttributeKey.booleanKey(key)] = value
        return this
    }

    override fun setAttribute(key: String, value: Long): Span {
        _collectedAttributes[AttributeKey.longKey(key)] = value
        return this
    }

    override fun setAttribute(key: String, value: Double): Span {
        _collectedAttributes[AttributeKey.doubleKey(key)] = value
        return this
    }

    override fun setAttribute(key: AttributeKey<Long>, value: Int): Span {
        _collectedAttributes[key] = value
        return this
    }

    override fun addEvent(name: String, attributes: Attributes): Span {
        _collectedEvents[name] = attributes
        return this
    }

    override fun addEvent(name: String, attributes: Attributes, timestamp: Long, unit: TimeUnit): Span {
        _collectedEvents[name] = attributes
        return this
    }

    override fun addEvent(name: String?): Span? {
        return super.addEvent(name)
    }

    override fun addEvent(name: String?, attributes: Attributes?, timestamp: Instant?): Span? {
        return super.addEvent(name, attributes, timestamp)
    }

    override fun addEvent(name: String?, timestamp: Instant?): Span? {
        return super.addEvent(name, timestamp)
    }

    override fun addEvent(name: String?, timestamp: Long, unit: TimeUnit?): Span? {
        return super.addEvent(name, timestamp, unit)
    }

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

    override fun getSpanContext(): SpanContext = SpanContext.getInvalid()

    override fun isRecording(): Boolean = isStarted && !isEnded

    override fun storeInContext(context: Context): Context = context
}
