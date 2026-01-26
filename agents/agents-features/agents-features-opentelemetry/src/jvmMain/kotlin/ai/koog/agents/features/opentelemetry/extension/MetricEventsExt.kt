package ai.koog.agents.features.opentelemetry.extension

import ai.koog.agents.features.opentelemetry.metric.MetricEvent
import kotlin.math.abs
import kotlin.math.pow

internal fun MetricEvent.getPositiveDurationSec(other: MetricEvent): Double =
    abs(this.timestamp.minus(other.timestamp).div((10.0.pow(6))))
