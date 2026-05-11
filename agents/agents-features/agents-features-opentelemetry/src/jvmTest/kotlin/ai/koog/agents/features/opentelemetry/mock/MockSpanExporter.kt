package ai.koog.agents.features.opentelemetry.mock

import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.utils.io.Closeable
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import io.opentelemetry.sdk.common.CompletableResultCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList
import io.opentelemetry.sdk.trace.data.SpanData as JavaSpanData
import io.opentelemetry.sdk.trace.export.SpanExporter as JavaSpanExporter

/**
 * A mock span exporter that captures spans created by the OpenTelemetry feature.
 *
 * JVM-only: uses `CopyOnWriteArrayList` so `collectedSpans` returns a thread-safe live view,
 * which existing agent-level tests rely on (they capture the reference before the agent runs
 * and expect it to reflect subsequent appends). Moving to commonTest would require replacing
 * the live-list semantics (e.g., polling a StateFlow snapshot), and updating all JVM tests
 * that rely on the aliasing.
 */
internal class MockSpanExporter : SpanExporter, Closeable {

    companion object {
        private val createAgentSpanOperationAttribute =
            GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.CREATE_AGENT)
    }

    private val _collectedSpans = CopyOnWriteArrayList<SpanData>()

    val collectedSpans: List<SpanData>
        get() = _collectedSpans

    private val _collectedJavaSpans = CopyOnWriteArrayList<JavaSpanData>()

    val collectedJavaSpans: List<JavaSpanData>
        get() = _collectedJavaSpans

    val runIds: List<String>
        get() {
            return collectedSpans.mapNotNull { span ->
                span.attributes["gen_ai.conversation.id"] as? String
            }.distinct()
        }

    val lastRunId: String
        get() = runIds.last()

    private val _isCollected: MutableStateFlow<Boolean> = MutableStateFlow(false)

    val isCollected: StateFlow<Boolean>
        get() = _isCollected.asStateFlow()

    val javaSdkExporter: JavaSpanExporter = object : JavaSpanExporter {
        override fun export(spans: Collection<JavaSpanData>): CompletableResultCode {
            _collectedJavaSpans.addAll(spans)
            if (spans.isNotEmpty()) _isCollected.value = true
            return CompletableResultCode.ofSuccess()
        }

        override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

        override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
    }

    override suspend fun export(telemetry: List<SpanData>): OperationResultCode {
        telemetry.forEach { span ->
            _collectedSpans.add(span)

            val isCreateAgentSpan = span.attributes.any { (key, value) ->
                // Note! This code will wait until the first CreateAgentSpan is collected.
                //  If the test verifies multiple CreateAgentSpans, this check will give an unexpected result.
                key == createAgentSpanOperationAttribute.key && value == createAgentSpanOperationAttribute.value
            }

            if (isCreateAgentSpan) {
                _isCollected.value = true
            }
        }

        return OperationResultCode.Success
    }

    override suspend fun forceFlush(): OperationResultCode {
        return OperationResultCode.Success
    }

    override suspend fun shutdown(): OperationResultCode {
        return OperationResultCode.Success
    }

    override suspend fun close() {
        shutdown()
    }
}
