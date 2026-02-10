package ai.koog.agents.features.opentelemetry.metric

import io.opentelemetry.api.metrics.DoubleHistogram
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.Meter

internal object MetricsFactory {

    /**
     * Build and register a Counter instrument for tracking GenAI token usage.
     *
     * The instrument name, description, and unit are taken from [GenAIMetrics.Client.Token.Usage].
     * This counter is created according to the OpenTelemetry Metrics API. It is pre-initialized with
     * a zero value to make the instrument visible to exporters even if no data points were recorded yet.
     *
     * Recommended metric attributes when recording values (aligned with GenAI semantic conventions):
     * - gen_ai.operation.name (required)
     * - gen_ai.provider.name (required)
     * - gen_ai.response.model (recommended)
     * - gen_ai.token.type (recommended) — INPUT or OUTPUT
     */
    internal fun createTokenCounter(meter: Meter): LongCounter = meter
        .counterBuilder(GenAIMetrics.Client.Token.Usage.name)
        .setDescription(GenAIMetrics.Client.Token.Usage.description)
        .setUnit(GenAIMetrics.Client.Token.Usage.unit)
        .build()
        .also { it.add(0) }

    /**
     * Build and register a Counter instrument for counting GenAI tool calls.
     *
     * The instrument name, description and unit are taken from [KoogMetrics.Tool.Count].
     * This counter is created according to the OpenTelemetry Metrics API and pre-initialized with a
     * zero value to ensure the instrument appears in the exporter even without recorded data points.
     *
     * Recommended metric attributes when recording values (aligned with GenAI semantic conventions):
     * - gen_ai.operation.name (required)
     * - gen_ai.tool.name (recommended)
     *
     * Custom attributes:
     * - koog.tool.call.status (recommended)
     */
    internal fun createToolCallCounter(meter: Meter): LongCounter = meter
        .counterBuilder(KoogMetrics.Tool.Count.name)
        .setDescription(KoogMetrics.Tool.Count.description)
        .setUnit(KoogMetrics.Tool.Count.unit)
        .build()
        .also { it.add(0) }

    /**
     * Build and register a Histogram instrument for measuring GenAI operations durations.
     *
     * The instrument name, description, and unit are taken from [GenAIMetrics.Client.Operation.Duration].
     * This metric SHOULD be specified with ExplicitBucketBoundaries of
     * [0.01, 0.02, 0.04, 0.08, 0.16, 0.32, 0.64, 1.28, 2.56, 5.12, 10.24, 20.48, 40.96, 81.92] seconds
     * to provide a meaningful latency distribution for operation durations.
     *
     * Recommended metric attributes when recording values (aligned with GenAI semantic conventions):
     * - gen_ai.operation.name (required)
     * - gen_ai.tool.name (recommended, if applicable)
     * - gen_ai.tool.call.status (recommended, if applicable)
     * - gen_ai.response.model (recommended, if applicable)
     * - gen_ai.provider.name (recommended, if applicable)
     */
    internal fun createOperationDurationHistogram(meter: Meter): DoubleHistogram = meter
        .histogramBuilder(GenAIMetrics.Client.Operation.Duration.name)
        .setDescription(GenAIMetrics.Client.Operation.Duration.description)
        .setUnit(GenAIMetrics.Client.Operation.Duration.unit)
        .setExplicitBucketBoundariesAdvice(
            listOf(
                0.01, 0.02, 0.04, 0.08, 0.16, 0.32, 0.64, 1.28, 2.56, 5.12, 10.24, 20.48, 40.96, 81.92
            )
        )
        .build()
}
