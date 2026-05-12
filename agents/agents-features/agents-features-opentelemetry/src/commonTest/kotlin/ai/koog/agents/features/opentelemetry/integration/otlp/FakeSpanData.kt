package ai.koog.agents.features.opentelemetry.integration.otlp

import io.opentelemetry.kotlin.InstrumentationScopeInfo
import io.opentelemetry.kotlin.resource.MutableResource
import io.opentelemetry.kotlin.resource.Resource
import io.opentelemetry.kotlin.tracing.SpanContext
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.StatusData
import io.opentelemetry.kotlin.tracing.TraceFlags
import io.opentelemetry.kotlin.tracing.TraceState
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.data.SpanEventData
import io.opentelemetry.kotlin.tracing.data.SpanLinkData

internal data class FakeSpanContext(
    override val traceId: String = "test-trace-id",
    override val spanId: String = "test-span-id",
    override val isValid: Boolean = true,
    override val isRemote: Boolean = false,
    override val traceFlags: TraceFlags = FakeTraceFlags(isSampled = true),
    override val traceState: TraceState = FakeTraceState,
) : SpanContext {
    override val traceIdBytes: ByteArray = ByteArray(16)
    override val spanIdBytes: ByteArray = ByteArray(8)
}

internal data class FakeTraceFlags(
    override val isSampled: Boolean = true,
    override val isRandom: Boolean = false,
) : TraceFlags

internal object FakeTraceState : TraceState {
    override fun get(key: String): String? = null
    override fun asMap(): Map<String, String> = emptyMap()
    override fun put(key: String, value: String): TraceState = this
    override fun remove(key: String): TraceState = this
}

internal data class FakeResource(
    override val attributes: Map<String, Any> = emptyMap(),
    override val schemaUrl: String? = null,
) : Resource {
    override fun asNewResource(action: MutableResource.() -> Unit): Resource = this
    override fun merge(other: Resource): Resource = this
}

internal data class FakeInstrumentationScopeInfo(
    override val name: String = "test-scope",
    override val version: String? = "1.0.0",
    override val schemaUrl: String? = null,
    override val attributes: Map<String, Any> = emptyMap(),
) : InstrumentationScopeInfo

internal data class FakeSpanEventData(
    override val name: String,
    override val timestamp: Long,
    override val attributes: Map<String, Any> = emptyMap(),
) : SpanEventData

internal data class FakeSpanLinkData(
    override val spanContext: SpanContext,
    override val attributes: Map<String, Any> = emptyMap(),
) : SpanLinkData

internal data class FakeSpanData(
    override val name: String = "test-span",
    override val status: StatusData = StatusData.Ok,
    override val parent: SpanContext = FakeSpanContext(isValid = false),
    override val spanContext: SpanContext = FakeSpanContext(),
    override val spanKind: SpanKind = SpanKind.INTERNAL,
    override val startTimestamp: Long = 1L,
    override val events: List<SpanEventData> = emptyList(),
    override val links: List<SpanLinkData> = emptyList(),
    override val endTimestamp: Long? = 2L,
    override val resource: Resource = FakeResource(attributes = mapOf("service.name" to "test-service")),
    override val instrumentationScopeInfo: InstrumentationScopeInfo = FakeInstrumentationScopeInfo(),
    override val hasEnded: Boolean = true,
    override val attributes: Map<String, Any> = emptyMap(),
) : SpanData
