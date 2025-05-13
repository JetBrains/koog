package ai.grazie.code.agents.core.feature.model

import kotlinx.serialization.Serializable

@Serializable
public class AgentError private constructor(
    public val message: String,
    public val stackTrace: String,
    public val cause: String? = null
) {
    public constructor(throwable: Throwable) : this(
        message = throwable.message ?: "Unknown error",
        stackTrace = throwable.stackTraceToString(),
        cause = throwable.cause?.stackTraceToString())
}

public fun Throwable.toAgentError(): AgentError = AgentError(this)
