package ai.koog.agents.core.feature.model

import kotlinx.serialization.Serializable

/**
 * Retrieves the class name of the current [Throwable] instance.
 *
 * On the JVM platform, this property returns the fully qualified class name of the throwable.
 * On other platforms, it falls back to the simple class name of the throwable.
 *
 * This property is used to provide a platform-specific representation of the throwable's type,
 * which helps in categorizing or identifying the type of error.
 *
 * @receiver The [Throwable] whose type name is being accessed.
 * @return The class name of the throwable as a nullable string, or null if the type cannot be determined.
 */
internal expect val Throwable.typeName: String?

/**
 * Represents an error encountered by an AI agent, encapsulating error details.
 *
 * This class provides essential information to understand and debug errors occurring
 * during the execution of AI agent strategies, tools, or nodes.
 *
 * @property message An error message of the original throwable;
 * @property stackTrace The stack trace of the error as a string;
 * @property cause The stack trace of the root cause if available, or null if no cause is set;
 * @property type The class name of the type of the error, if available. On JVM this is the fully
 *           qualified name; on other platforms it falls back to the simple class name.
 */
@Serializable
public data class AIAgentError(
    public val message: String,
    public val stackTrace: String,
    public val cause: String? = null,
    public val type: String? = null,
) {

    /**
     * Secondary constructor that allows creating an instance of the class using a [Throwable].
     *
     * @param throwable The [Throwable] from which the error message, stack trace, and cause will be retrieved.
     */
    public constructor(throwable: Throwable) : this(
        message = throwable.message ?: "Unknown error",
        stackTrace = throwable.stackTraceToString(),
        cause = throwable.cause?.stackTraceToString(),
        type = throwable.typeName
    )
}

/**
 * Converts a [Throwable] instance to an [AIAgentError].
 *
 * @return The generated [AIAgentError] containing detailed information about the [Throwable].
 */
public fun Throwable.toAgentError(): AIAgentError = AIAgentError(this)
