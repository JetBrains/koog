package ai.koog.agents.core.environment

import ai.koog.agents.core.feature.model.AIAgentError
import kotlinx.serialization.Serializable

/**
 * Represents the possible result types for a tool operation.
 */
@Serializable
public sealed class ToolResultKind {

    /**
     * Represents a successful result in the context of a tool operation.
     */
    @Serializable
    public object Success : ToolResultKind()

    /**
     * Represents a failure result in the context of a tool operation.
     *
     * @property error The [Throwable] that caused the failure. It can be null if no specific throwable information is available.
     */
    @Serializable
    public data class Failure(public val error: AIAgentError?) : ToolResultKind()

    /**
     * Represents a validation error result in the context of a tool operation.
     *
     * @property error The specific tool exception that describes the details of the validation failure.
     */
    @Serializable
    public data class ValidationError(public val error: AIAgentError) : ToolResultKind()
}
