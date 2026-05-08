package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.features.opentelemetry.AgentType
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.USER_PROMPT_PARIS
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Strategy.getSingleLLMCallStrategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.createAgent
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.defaultMockExecutor
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class OpenTelemetryConfigJvmExtTest {

    /**
     * Verifies that OpenTelemetryConfigJvm.addSpanExporter correctly
     * bridges a Java-SDK SpanExporter to the Kotlin SDK via `toOtelKotlinSpanExporter()` and
     * that spans actually flow to it during agent execution.
     */
    @Test
    fun testAddSpanExporterWithJavaSdkExporterDeliversSpansViaBridge() = runTest {
        val receivedSpans = CopyOnWriteArrayList<SpanData>()
        val spanReceived = MutableStateFlow(false)

        val javaSdkExporter = object : SpanExporter {
            override fun export(spans: Collection<SpanData>): CompletableResultCode {
                receivedSpans.addAll(spans)
                if (spans.isNotEmpty()) spanReceived.value = true
                return CompletableResultCode.ofSuccess()
            }
            override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()
            override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
        }

        val agent = createAgent(
            strategy = getSingleLLMCallStrategy(AgentType.Graph),
            executor = defaultMockExecutor,
        ) {
            val config = this
            with(OpenTelemetryConfigJvm) {
                config.addSpanExporter(javaSdkExporter)
            }
        }

        agent.run(USER_PROMPT_PARIS, null)
        withContext(Dispatchers.Default) {
            withTimeoutOrNull(10.seconds) { spanReceived.first { it } }
        }
        agent.close()

        assertTrue(
            receivedSpans.isNotEmpty(),
            "Java-SDK SpanExporter must receive spans routed through the toOtelKotlinSpanExporter bridge"
        )
    }
}
