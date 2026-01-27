package ai.koog.agents.features.opentelemetry.metric

import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.Meter

/**
 * Build and register a Counter instrument for counting GenAI tool calls.
 *
 * The instrument name, description and unit are taken from [KoogMetricNames.Tool.Count].
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
    .counterBuilder(KoogMetricNames.Tool.Count.name)
    .setDescription(KoogMetricNames.Tool.Count.description)
    .setUnit(KoogMetricNames.Tool.Count.unit)
    .build()
    .also { it.add(0) }
