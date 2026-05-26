package ai.koog.agents.features.opentelemetry.metric

import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig

internal actual fun createMetricCollector(config: OpenTelemetryConfig): MetricCollector = NoOpMetricCollector
