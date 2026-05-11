package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.features.opentelemetry.AgentType
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.USER_PROMPT_PARIS
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Strategy.getSingleLLMCallStrategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.createAgent
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.defaultMockExecutor
import ai.koog.agents.features.opentelemetry.mock.MockSpanExporter
import ai.koog.utils.io.use
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
        MockSpanExporter().use { mockExporter ->
            val agent = createAgent(
                strategy = getSingleLLMCallStrategy(AgentType.Graph),
                executor = defaultMockExecutor,
            ) {
                val config = this
                with(OpenTelemetryConfigJvm) {
                    config.addSpanExporter(mockExporter.javaSdkExporter)
                }
            }

            agent.run(USER_PROMPT_PARIS, null)
            withContext(Dispatchers.Default) {
                withTimeoutOrNull(10.seconds) { mockExporter.isCollected.first { it } }
            }
            agent.close()

            assertTrue(
                mockExporter.collectedJavaSpans.isNotEmpty(),
                "Java-SDK SpanExporter must receive spans routed through the toOtelKotlinSpanExporter bridge"
            )
        }
    }
}
