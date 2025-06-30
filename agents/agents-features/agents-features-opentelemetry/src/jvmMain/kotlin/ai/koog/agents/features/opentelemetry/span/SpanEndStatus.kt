package ai.koog.agents.features.opentelemetry.feature

import io.opentelemetry.api.trace.StatusCode

internal data class SpanEndStatus(val code: StatusCode, val description: String? = null)
