package ai.koog.agents.features.opentelemetry.metrics

import ai.koog.agents.features.opentelemetry.metric.MetricFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MetricFactoryTest {

    @Test
    fun testCreateTokenUsageHistogramMetric() {
        val metric = MetricFactory.createTokenUsageHistogramMetric()

        assertEquals("gen_ai.client.token.usage", metric.name)
        assertEquals("Number of input and output tokens used", metric.description)
        assertEquals("{token}", metric.unit)

        assertNotNull(metric.boundariesAdvice)
        assertTrue(metric.boundariesAdvice.isNotEmpty())

        val expectedBoundaries = listOf(
            1.0, 4.0, 16.0, 64.0, 256.0, 1024.0, 4096.0, 16384.0, 65536.0,
            262144.0, 1048576.0, 4194304.0, 16777216.0, 67108864.0
        )
        assertEquals(expectedBoundaries, metric.boundariesAdvice)
    }

    @Test
    fun testCreateToolCallCounterMetric() {
        val metric = MetricFactory.createToolCallCounterMetric()

        assertEquals("koog.gen_ai.client.tool.call.count", metric.name)
        assertEquals("Number of tool calls performed by the agent", metric.description)
        assertEquals("{call}", metric.unit)
    }

    @Test
    fun testCreateOperationDurationHistogramMetric() {
        val metric = MetricFactory.createOperationDurationHistogramMetric()

        assertEquals("gen_ai.client.operation.duration", metric.name)
        assertEquals("GenAI operation duration", metric.description)
        assertEquals("s", metric.unit)

        // Verify boundaries advice is set according to OpenTelemetry semantic conventions
        assertNotNull(metric.boundariesAdvice)
        assertTrue(metric.boundariesAdvice.isNotEmpty())

        val expectedBoundaries = listOf(
            0.01, 0.02, 0.04, 0.08, 0.16, 0.32, 0.64, 1.28, 2.56, 5.12, 10.24, 20.48, 40.96, 81.92
        )
        assertEquals(expectedBoundaries, metric.boundariesAdvice)
    }

    @Test
    fun testOperationDurationHistogramBoundariesAreOrdered() {
        val metric = MetricFactory.createOperationDurationHistogramMetric()

        val boundaries = metric.boundariesAdvice

        // Verify boundaries are in ascending order
        for (i in 0 until boundaries.size - 1) {
            assertTrue(
                boundaries[i] < boundaries[i + 1],
                "Boundaries should be in ascending order: ${boundaries[i]} should be less than ${boundaries[i + 1]}"
            )
        }
    }

    @Test
    fun testOperationDurationHistogramBoundariesCoverReasonableRange() {
        val metric = MetricFactory.createOperationDurationHistogramMetric()

        val boundaries = metric.boundariesAdvice

        // Verify the range covers from 10ms to ~82 seconds
        assertTrue(boundaries.first() >= 0.01, "First boundary should be at least 10ms")
        assertTrue(boundaries.last() <= 100.0, "Last boundary should be at most 100s")
    }
}
