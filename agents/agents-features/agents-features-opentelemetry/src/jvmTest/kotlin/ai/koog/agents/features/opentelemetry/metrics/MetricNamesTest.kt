package ai.koog.agents.features.opentelemetry.metrics

import ai.koog.agents.features.opentelemetry.metric.GenAIMetricNames
import ai.koog.agents.features.opentelemetry.metric.KoogMetricNames
import kotlin.test.Test
import kotlin.test.assertEquals

class MetricNamesTest {
    @Test
    fun `test tool call count metric`() {
        val metric = KoogMetricNames.Tool.Count
        assertEquals("gen_ai.client.tool.count", metric.name)
        assertEquals("tool call", metric.unit)
    }

    @Test
    fun `test tool call operation duration metric`() {
        val metric = GenAIMetricNames.Client.Operation.Duration
        assertEquals("gen_ai.client.operation.duration", metric.name)
        assertEquals("s", metric.unit)
    }

    @Test
    fun `test token usage metric`() {
        val metric = GenAIMetricNames.Client.Token.Usage
        assertEquals("gen_ai.client.token.usage", metric.name)
        assertEquals("token", metric.unit)
    }
}
