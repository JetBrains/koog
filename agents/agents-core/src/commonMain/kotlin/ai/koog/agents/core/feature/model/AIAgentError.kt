package ai.koog.agents.core.feature.model

import kotlinx.serialization.Serializable

/**
 * Represents an error encountered by an AI agent, encapsulating error details such as
 * the message, stack trace, and an optional cause.
 *
 * This class provides essential information to understand and debug errors occurring
 * during the execution of AI agent strategies, tools, or nodes.
 *
 * Instances of this class can be created directly from a `Throwable`.
 *
 * @property message A human-readable description of the error. Defaults to "Unknown error"
 * if not provided by the originating throwable.
 * @property stackTrace The stack trace of the error as a string, providing a detailed
 * representation of where the error occurred.
 * @property cause The stack trace of the root cause if available, or null if no cause is set.
 * This helps trace back the chain of exceptions leading to the current error.
 */
@Serializable
public class AIAgentError private constructor(
    public val message: String,
    public val stackTrace: String,
    public val cause: String? = null
) {
    /**
     * Secondary constructor that allows creating an instance of the class using a [Throwable].
     *
     * @param throwable The [Throwable] from which the error message, stack trace, and cause will be retrieved.
     * The error message is derived from `throwable.message`, defaulting to "Unknown error" if null.
     * The stack trace is converted to a string using `throwable.stackTraceToString()`.
     * The cause is determined from `throwable.cause`, and its stack trace is converted to a string if not null.
     */
    public constructor(throwable: Throwable) : this(
        message = throwable.message ?: "Unknown error",
        stackTrace = throwable.stackTraceToString(),
        cause = throwable.cause?.stackTraceToString())
}

/**
 * Converts a [Throwable] instance to an [AIAgentError].
 *
 * @return The generated [AIAgentError] containing detailed information about the [Throwable].
 */
public fun Throwable.toAgentError(): AIAgentError = AIAgentError(this)
