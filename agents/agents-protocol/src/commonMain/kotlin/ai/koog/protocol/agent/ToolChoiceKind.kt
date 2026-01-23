package ai.koog.protocol.agent

import kotlinx.serialization.Serializable

/**
 * Controls tool selection behavior for an agent.
 */
@Serializable
public sealed class ToolChoiceKind {

    /**
     * Agent automatically decides whether to use tools.
     */
    @Serializable
    public object Auto : ToolChoiceKind()

    /**
     * Agent must use a specific named tool.
     */
    @Serializable
    public data class Named(public val toolName: String) : ToolChoiceKind()

    /**
     * Agent cannot use any tools.
     */
    @Serializable
    public object None : ToolChoiceKind()

    /**
     * Agent must use at least one tool.
     */
    @Serializable
    public object Required : ToolChoiceKind()
}
