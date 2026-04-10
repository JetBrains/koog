package ai.koog.agents.features.opentelemetry.integration.datadog

import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DatadogExporterTest {

    @Test
    fun testAddDatadogExporterFailsWhenApiKeyMissing() {
        val config = OpenTelemetryConfig()
        val throwable = assertFailsWith<IllegalStateException> {
            config.addDatadogExporter()
        }

        assertEquals(
            throwable.message?.contains("Datadog API key is missing"),
            true,
            "Expected exception message to contain 'Datadog API key is missing', but got: ${throwable.message}"
        )
    }
}
