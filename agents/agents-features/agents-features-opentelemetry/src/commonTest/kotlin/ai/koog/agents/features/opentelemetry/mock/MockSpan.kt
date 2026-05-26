package ai.koog.agents.features.opentelemetry.mock

import io.opentelemetry.kotlin.tracing.Span
import io.opentelemetry.kotlin.tracing.SpanContext
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.StatusData
import io.opentelemetry.kotlin.tracing.TraceFlags
import io.opentelemetry.kotlin.tracing.TraceState
import io.opentelemetry.kotlin.tracing.data.SpanEventData
import io.opentelemetry.kotlin.tracing.data.SpanLinkData

/**
 * A mock implementation of Kotlin OTel SDK [Span] for testing.
 *
 * The 0.3.0 [Span] interface is intentionally narrow (write-only). Read access (`name`, `status`,
 * `attributes`, `events`, `links`, `spanKind`, `startTimestamp`) is exposed as plain test-only
 * properties on [MockSpan] so existing assertions keep working without going through `SpanData`.
 */
class MockSpan(
    val spanKind: SpanKind = SpanKind.INTERNAL,
    val startTimestamp: Long = 0L,
) : Span {

    var isStarted = true
    var isEnded = false

    var name: String = ""
        private set
    var status: StatusData = StatusData.Unset
        private set

    override val parent: SpanContext = MockSpanContext()

    private val _spanContext = MockSpanContext()
    override val spanContext: SpanContext get() = _spanContext

    private val _attributes = mutableMapOf<String, Any>()
    val attributes: Map<String, Any> get() = _attributes.toMap()

    private val _events = mutableListOf<MockSpanEventData>()
    val events: List<SpanEventData> get() = _events.toList()

    val links: List<SpanLinkData> = emptyList()

    // Collected data for test assertions
    val collectedAttributes: Map<String, Any> get() = _attributes.toMap()
    val collectedEvents: List<MockSpanEventData> get() = _events.toList()

    // Span lifecycle
    override fun setName(name: String) {
        this.name = name
    }

    override fun setStatus(status: StatusData) {
        this.status = status
    }

    override fun end() {
        isEnded = true
    }

    override fun end(timestamp: Long) {
        isEnded = true
    }

    override fun isRecording(): Boolean = isStarted && !isEnded

    // AttributesMutator
    override fun setBooleanAttribute(key: String, value: Boolean) {
        _attributes[key] = value
    }

    override fun setStringAttribute(key: String, value: String) {
        _attributes[key] = value
    }

    override fun setLongAttribute(key: String, value: Long) {
        _attributes[key] = value
    }

    override fun setDoubleAttribute(key: String, value: Double) {
        _attributes[key] = value
    }

    override fun setBooleanListAttribute(key: String, value: List<Boolean>) {
        _attributes[key] = value
    }

    override fun setStringListAttribute(key: String, value: List<String>) {
        _attributes[key] = value
    }

    override fun setLongListAttribute(key: String, value: List<Long>) {
        _attributes[key] = value
    }

    override fun setDoubleListAttribute(key: String, value: List<Double>) {
        _attributes[key] = value
    }

    // SpanEventCreator
    override fun addEvent(name: String, timestamp: Long?, attributes: (io.opentelemetry.kotlin.attributes.AttributesMutator.() -> Unit)?) {
        val eventAttrs = mutableMapOf<String, Any>()
        val mutator = MockAttributesMutator(eventAttrs)
        attributes?.invoke(mutator)
        _events.add(MockSpanEventData(name, timestamp ?: 0L, eventAttrs.toMap()))
    }

    // SpanLinkCreator
    override fun addLink(spanContext: SpanContext, attributes: (io.opentelemetry.kotlin.attributes.AttributesMutator.() -> Unit)?) {
        // no-op for tests
    }
}

/**
 * Mock SpanContext for testing.
 */
class MockSpanContext : SpanContext {
    override val traceId: String = "00000000000000000000000000000000"
    override val traceIdBytes: ByteArray = ByteArray(16)
    override val spanId: String = "0000000000000000"
    override val spanIdBytes: ByteArray = ByteArray(8)
    override val traceFlags: TraceFlags = MockTraceFlags
    override val isValid: Boolean = false
    override val isRemote: Boolean = false
    override val traceState: TraceState = MockTraceState
}

object MockTraceFlags : TraceFlags {
    override val isSampled: Boolean = false
    override val isRandom: Boolean = false
}

object MockTraceState : TraceState {
    override fun get(key: String): String? = null
    override fun asMap(): Map<String, String> = emptyMap()
    override fun put(key: String, value: String): TraceState = this
    override fun remove(key: String): TraceState = this
}

/**
 * Mock SpanEventData for assertions.
 */
data class MockSpanEventData(
    override val name: String,
    override val timestamp: Long,
    override val attributes: Map<String, Any>
) : SpanEventData

/**
 * Mock AttributesMutator that collects attributes into a map.
 */
class MockAttributesMutator(
    private val attrs: MutableMap<String, Any>
) : io.opentelemetry.kotlin.attributes.AttributesMutator {
    override fun setBooleanAttribute(key: String, value: Boolean) {
        attrs[key] = value
    }

    override fun setStringAttribute(key: String, value: String) {
        attrs[key] = value
    }

    override fun setLongAttribute(key: String, value: Long) {
        attrs[key] = value
    }

    override fun setDoubleAttribute(key: String, value: Double) {
        attrs[key] = value
    }

    override fun setBooleanListAttribute(key: String, value: List<Boolean>) {
        attrs[key] = value
    }

    override fun setStringListAttribute(key: String, value: List<String>) {
        attrs[key] = value
    }

    override fun setLongListAttribute(key: String, value: List<Long>) {
        attrs[key] = value
    }

    override fun setDoubleListAttribute(key: String, value: List<Double>) {
        attrs[key] = value
    }
}
