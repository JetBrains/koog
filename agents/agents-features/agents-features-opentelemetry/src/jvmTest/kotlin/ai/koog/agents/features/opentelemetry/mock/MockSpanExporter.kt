package ai.koog.agents.features.opentelemetry.mock

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

/**
 * A mock span exporter that captures spans created by the OpenTelemetry feature.
 * This allows us to inject a MockTracer into the OpenTelemetry feature.
 *
 * @param filter a function that determines whether a given span should be exported. Defaults to exporting all spans.
 */
internal class MockSpanExporter : SpanExporter {

    private val _collectedSpans = mutableListOf<SpanData>()

    val collectedSpans: List<SpanData>
        get() = _collectedSpans

    private val _runIds = mutableListOf<String>()

    val runIds: List<String>
        get() = _runIds

    val lastRunId: String
        get() = _runIds.last()

    override fun export(spans: MutableCollection<SpanData>): CompletableResultCode {
        spans.forEach { span ->
            _collectedSpans.add(0, span)
        }

        // Workaround to get runId Uuid from the span name
        val runSpan = spans.find { it.name.startsWith("run.", true) }
        if (runSpan != null) {
            val runId = runSpan.name.removePrefix("run.")
            if (!_runIds.contains(runId)) {
                _runIds.add(runId)
            }
        }

        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }

    override fun shutdown(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }
}
