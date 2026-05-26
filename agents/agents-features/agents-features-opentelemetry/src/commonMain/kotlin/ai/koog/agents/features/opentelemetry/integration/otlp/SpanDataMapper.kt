package ai.koog.agents.features.opentelemetry.integration.otlp

import io.opentelemetry.kotlin.InstrumentationScopeInfo
import io.opentelemetry.kotlin.resource.Resource
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.data.SpanEventData
import io.opentelemetry.kotlin.tracing.data.SpanLinkData

/**
 * Provides utility functions to map SpanData objects to their OTLP (OpenTelemetry Protocol) representations.
 */
public object OtlpSpanDataMapper {

    /**
     * Represents a bitmask flag indicating whether a span is sampled.
     *
     * Value: 0x01 (binary `00000001`), where this specific bit indicates
     * the sampled state of the span.
     */
    private const val FLAG_SAMPLED: Int = 0x01

    /**
     * Convert a list of [SpanData] to an OTLP export request.
     */
    internal fun List<SpanData>.toOtlpExportRequest(): OtlpExportRequest {
        val groupedByResource = groupBy { it.resource }
        val resourceSpans = groupedByResource.map { (resource, spansForResource) ->
            val groupedByScope = spansForResource.groupBy { it.instrumentationScopeInfo }
            val scopeSpans = groupedByScope.map { (scope, spansForScope) ->
                OtlpScopeSpans(
                    scope = scope.toOtlpScope(),
                    spans = spansForScope.map { it.toOtlpSpan() },
                )
            }
            OtlpResourceSpans(
                resource = resource.toOtlpResource(),
                scopeSpans = scopeSpans,
                schemaUrl = resource.schemaUrl,
            )
        }
        return OtlpExportRequest(resourceSpans = resourceSpans)
    }

    //region Private Methods

    private fun Resource.toOtlpResource(): OtlpResource =
        OtlpResource(attributes = attributes.toOtlpKeyValues())

    private fun InstrumentationScopeInfo.toOtlpScope(): OtlpScope =
        OtlpScope(
            name = name,
            version = version,
            attributes = attributes.takeIf { it.isNotEmpty() }?.toOtlpKeyValues(),
        )

    private fun SpanData.toOtlpSpan(): OtlpSpan {
        val parentSpanId = parent.takeIf { it.isValid }?.spanId
        return OtlpSpan(
            traceId = spanContext.traceId,
            spanId = spanContext.spanId,
            parentSpanId = parentSpanId,
            name = name,
            kind = spanKind.toOtlpKindCode(),
            startTimeUnixNano = startTimestamp.toString(),
            endTimeUnixNano = endTimestamp?.toString(),
            attributes = attributes.takeIf { it.isNotEmpty() }?.toOtlpKeyValues(),
            events = events.takeIf { it.isNotEmpty() }?.map { it.toOtlpEvent() },
            links = links.takeIf { it.isNotEmpty() }?.map { it.toOtlpLink() },
            status = OtlpStatus(
                code = status.statusCode.ordinal,
                message = status.description,
            ),
            flags = if (spanContext.traceFlags.isSampled) FLAG_SAMPLED else 0,
        )
    }

    private fun SpanEventData.toOtlpEvent(): OtlpEvent =
        OtlpEvent(
            timeUnixNano = timestamp.toString(),
            name = name,
            attributes = attributes.takeIf { it.isNotEmpty() }?.toOtlpKeyValues(),
        )

    private fun SpanLinkData.toOtlpLink(): OtlpLink =
        OtlpLink(
            traceId = spanContext.traceId,
            spanId = spanContext.spanId,
            attributes = attributes.takeIf { it.isNotEmpty() }?.toOtlpKeyValues(),
            flags = if (spanContext.traceFlags.isSampled) FLAG_SAMPLED else 0,
        )

    // OTLP wire ordinals (https://github.com/open-telemetry/opentelemetry-proto/blob/main/opentelemetry/proto/trace/v1/trace.proto)
    // differ from Kotlin SDK's SpanKind enum ordinals - keep the mapping explicit.
    private fun SpanKind.toOtlpKindCode(): Int = when (this) {
        SpanKind.INTERNAL -> 1
        SpanKind.SERVER -> 2
        SpanKind.CLIENT -> 3
        SpanKind.PRODUCER -> 4
        SpanKind.CONSUMER -> 5
    }

    private fun Map<String, Any>.toOtlpKeyValues(): List<OtlpKeyValue> =
        map { (key, value) -> OtlpKeyValue(key = key, value = value.toOtlpAnyValue()) }

    private fun Any.toOtlpAnyValue(): OtlpAnyValue = when (this) {
        is String -> OtlpAnyValue(stringValue = this)
        is Boolean -> OtlpAnyValue(boolValue = this)
        is Long -> OtlpAnyValue(intValue = toString())
        is Int -> OtlpAnyValue(intValue = toLong().toString())
        is Double -> OtlpAnyValue(doubleValue = this)
        is Float -> OtlpAnyValue(doubleValue = toDouble())
        is List<*> -> OtlpAnyValue(arrayValue = OtlpArrayValue(values = map { it?.toOtlpAnyValue() ?: OtlpAnyValue() }))
        else -> OtlpAnyValue(stringValue = toString())
    }

    //endregion Private Methods
}
