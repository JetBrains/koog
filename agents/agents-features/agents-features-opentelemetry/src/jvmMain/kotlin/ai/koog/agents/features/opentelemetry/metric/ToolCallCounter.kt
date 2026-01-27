package ai.koog.agents.features.opentelemetry.metric

import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.Meter

/**
 * Build and register a Counter instrument for counting GenAI tool calls.
 *
 * The instrument name, description and unit are taken from [GenAIMetricNames.Client.Tool.Count].
 * This counter is created according to the OpenTelemetry Metrics API and pre-initialized with a
 * zero value to ensure the instrument appears in the exporter even without recorded data points.
 *
 * Recommended metric attributes when recording values (aligned with GenAI semantic conventions):
 * - gen_ai.operation.name (required)
 * - gen_ai.tool.name (recommended)
 *
 * Custom attributes:
 * - gen_ai.tool.call.status (recommended) — SUCCESS or ERROR
 */
internal fun createToolCallCounter(meter: Meter): LongCounter = meter
    .counterBuilder(GenAIMetricNames.Client.Tool.Count.name)
    .setDescription(GenAIMetricNames.Client.Tool.Count.description)
    .setUnit(GenAIMetricNames.Client.Tool.Count.unit)
    .build()
    .also { it.add(0) }
