package ai.koog.protocol.agent

import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable
public sealed class ToolChoiceKind {

    /**
     *
     */
    @Serializable
    public object Auto : ToolChoiceKind()

    /**
     *
     */
    @Serializable
    public data class Named(public val toolName: String) : ToolChoiceKind()

    /**
     *
     */
    @Serializable
    public object None : ToolChoiceKind()

    /**
     *
     */
    @Serializable
    public object Required : ToolChoiceKind()
}
