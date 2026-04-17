package ai.koog.agents.features.opentelemetry.integration.datadog

import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test

class DatadogExporterTest {

    @Test
    fun `test buildDatadogExporter fails when DD_API_KEY is not set`() {
        assertThrows<IllegalStateException> {
            buildDatadogExporter()
        }
    }

    @Test
    fun `test addDatadogExporter fails when DD_API_KEY is not set`() {
        val config = OpenTelemetryConfig()
        assertThrows<IllegalStateException> {
            config.addDatadogExporter()
        }
    }
}
