package ai.koog.agents.features.opentelemetry.metric

import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.Meter

/**
 * Build and register a Counter instrument for tracking GenAI token usage.
 *
 * The instrument name, description and unit are taken from [GenAIMetricNames.Client.Token.Usage].
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
    .counterBuilder(GenAIMetricNames.Client.Token.Usage.name)
    .setDescription(GenAIMetricNames.Client.Token.Usage.description)
    .setUnit(GenAIMetricNames.Client.Token.Usage.unit)
    .build()
    .also { it.add(0) }
