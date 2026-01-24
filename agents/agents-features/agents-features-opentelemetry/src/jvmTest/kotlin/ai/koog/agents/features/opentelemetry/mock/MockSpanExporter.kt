package ai.koog.agents.features.opentelemetry.mock

import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A mock span exporter that captures spans created by the OpenTelemetry feature.
 * This allows us to inject a MockTracer into the OpenTelemetry feature.
 *
 * @param filter a function that determines whether a given span should be exported. Defaults to exporting all spans.
 */
internal class MockSpanExporter : SpanExporter {

    companion object {
        private val createAgentSpanOperationAttribute =
            SpanAttributes.Operation.Name(SpanAttributes.Operation.OperationNameType.CREATE_AGENT)
    }

    private val _collectedSpans = mutableListOf<SpanData>()

    val collectedSpans: List<SpanData>
        get() = _collectedSpans

    val runIds: List<String>
        get() {
            return collectedSpans.mapNotNull { span ->
                span.attributes[AttributeKey.stringKey("gen_ai.conversation.id")]
            }.distinct()
        }

    val lastRunId: String
        get() = runIds.last()

    private val _isCollected: MutableStateFlow<Boolean> = MutableStateFlow(false)

    val isCollected: StateFlow<Boolean>
        get() = _isCollected.asStateFlow()

    override fun export(spans: MutableCollection<SpanData>): CompletableResultCode {
        spans.forEach { span ->
            _collectedSpans.add(span)

            val isCreateAgentSpan = span.attributes.asMap().any { (key, value) ->
                // Note! This code will wait until the first CreateAgentSpan is collected.
                //  If the test verifies multiple CreateAgentSpans, this check will give an unexpected result.
                key.key == createAgentSpanOperationAttribute.key && value == createAgentSpanOperationAttribute.value
            }

            if (isCreateAgentSpan) {
                _isCollected.value = true
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
