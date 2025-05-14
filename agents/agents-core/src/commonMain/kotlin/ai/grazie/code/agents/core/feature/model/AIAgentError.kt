package ai.grazie.code.agents.core.feature.model

import kotlinx.serialization.Serializable

@Serializable
class AIAgentError private constructor(
    val message: String,
    val stackTrace: String,
    val cause: String? = null
) {
    constructor(throwable: Throwable) : this(
        message = throwable.message ?: "Unknown error",
        stackTrace = throwable.stackTraceToString(),
        cause = throwable.cause?.stackTraceToString())
}

fun Throwable.toAgentError() = AIAgentError(this)
