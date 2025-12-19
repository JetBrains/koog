package ai.koog.agents.features.opentelemetry.mock

import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import io.opentelemetry.api.trace.StatusCode

internal class MockGenAIAgentSpan(
    override val id: String,
    override val name: String,
    override val parentSpan: GenAIAgentSpan? = null
) : GenAIAgentSpan() {

    val isStarted: Boolean
        get() = (span as MockSpan).isStarted

    val isEnded: Boolean
        get() = (span as MockSpan).isEnded

    val currentStatus: StatusCode?
        get() = (span as MockSpan).status
}
