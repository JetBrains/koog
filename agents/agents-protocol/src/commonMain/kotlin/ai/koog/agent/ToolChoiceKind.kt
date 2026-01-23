package ai.koog.agent

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
    public class Auto : ToolChoiceKind()

    /**
     *
     */
    @Serializable
    public class Named(public val toolName: String) : ToolChoiceKind()

    /**
     *
     */
    @Serializable
    public class None : ToolChoiceKind()

    /**
     *
     */
    @Serializable
    public class Required : ToolChoiceKind()
}
