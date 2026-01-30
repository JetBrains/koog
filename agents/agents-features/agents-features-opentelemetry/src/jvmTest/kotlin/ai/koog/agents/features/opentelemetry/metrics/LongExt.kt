package ai.koog.agents.features.opentelemetry.metrics

import kotlin.math.pow

internal fun Long.toSec() = this.toDouble().div(10.0.pow(6))
