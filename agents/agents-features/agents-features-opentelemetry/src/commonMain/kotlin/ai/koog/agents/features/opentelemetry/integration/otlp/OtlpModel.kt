package ai.koog.agents.features.opentelemetry.integration.otlp

import kotlinx.serialization.Serializable

// OTLP/JSON wire shape (see https://opentelemetry.io/docs/specs/otlp/#json-protobuf-encoding).
// Field names are camelCase per the spec. int64 values (timestamps) are encoded as strings to
// avoid 64-bit precision loss in JS clients. traceId / spanId are hex strings.

@Serializable
internal data class OtlpExportRequest(
    val resourceSpans: List<OtlpResourceSpans>,
)

@Serializable
internal data class OtlpResourceSpans(
    val resource: OtlpResource,
    val scopeSpans: List<OtlpScopeSpans>,
    val schemaUrl: String? = null,
)

@Serializable
internal data class OtlpResource(
    val attributes: List<OtlpKeyValue>,
)

@Serializable
internal data class OtlpScopeSpans(
    val scope: OtlpScope,
    val spans: List<OtlpSpan>,
    val schemaUrl: String? = null,
)

@Serializable
internal data class OtlpScope(
    val name: String,
    val version: String? = null,
    val attributes: List<OtlpKeyValue>? = null,
)

@Serializable
internal data class OtlpSpan(
    val traceId: String,
    val spanId: String,
    val parentSpanId: String? = null,
    val name: String,
    val kind: Int,
    val startTimeUnixNano: String,
    val endTimeUnixNano: String? = null,
    val attributes: List<OtlpKeyValue>? = null,
    val events: List<OtlpEvent>? = null,
    val links: List<OtlpLink>? = null,
    val status: OtlpStatus? = null,
    val flags: Int? = null,
)

@Serializable
internal data class OtlpEvent(
    val timeUnixNano: String,
    val name: String,
    val attributes: List<OtlpKeyValue>? = null,
)

@Serializable
internal data class OtlpLink(
    val traceId: String,
    val spanId: String,
    val attributes: List<OtlpKeyValue>? = null,
    val flags: Int? = null,
)

@Serializable
internal data class OtlpStatus(
    val code: Int,
    val message: String? = null,
)

@Serializable
internal data class OtlpKeyValue(
    val key: String,
    val value: OtlpAnyValue,
)

@Serializable
internal data class OtlpAnyValue(
    val stringValue: String? = null,
    val boolValue: Boolean? = null,
    val intValue: String? = null,
    val doubleValue: Double? = null,
    val arrayValue: OtlpArrayValue? = null,
)

@Serializable
internal data class OtlpArrayValue(
    val values: List<OtlpAnyValue>,
)
