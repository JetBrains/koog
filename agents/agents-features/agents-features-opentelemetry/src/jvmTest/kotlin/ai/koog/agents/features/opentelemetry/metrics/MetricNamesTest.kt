package ai.koog.agents.features.opentelemetry.metrics

import ai.koog.agents.features.opentelemetry.metric.GenAIMetrics
import ai.koog.agents.features.opentelemetry.metric.KoogMetrics
import kotlin.test.Test
import kotlin.test.assertEquals

class MetricNamesTest {
    @Test
    fun `test tool call count metric`() {
        val metric = KoogMetrics.Client.Tool.Call.Count
        assertEquals("koog.gen_ai.client.tool.call.count", metric.name)
        assertEquals("{call}", metric.unit)
    }

    @Test
    fun `test tool call operation duration metric`() {
        val metric = GenAIMetrics.Client.Operation.Duration
        assertEquals("gen_ai.client.operation.duration", metric.name)
        assertEquals("s", metric.unit)
    }

    @Test
    fun `test token usage metric`() {
        val metric = GenAIMetrics.Client.Token.Usage
        assertEquals("gen_ai.client.token.usage", metric.name)
        assertEquals("{token}", metric.unit)
    }
}
