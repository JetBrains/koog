package ai.koog.agents.features.opentelemetry.metric

import io.opentelemetry.api.metrics.DoubleHistogram
import io.opentelemetry.api.metrics.Meter

/**
 * Build and register a Histogram instrument for measuring GenAI operations durations.
 *
 * The instrument name, description and unit are taken from [GenAIMetricNames.Client.Operation.Duration].
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
    .histogramBuilder(GenAIMetricNames.Client.Operation.Duration.name)
    .setDescription(GenAIMetricNames.Client.Operation.Duration.description)
    .setUnit(GenAIMetricNames.Client.Operation.Duration.unit)
    .setExplicitBucketBoundariesAdvice(
        listOf(
            0.01, 0.02, 0.04, 0.08, 0.16, 0.32, 0.64, 1.28, 2.56, 5.12, 10.24, 20.48, 40.96, 81.92
        )
    )
    .build()
