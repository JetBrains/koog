package ai.koog.agents.core.tools

import kotlinx.serialization.Serializable

/**
 * Represents the arguments for a tool operation.
 */
public interface ToolArgs {

    /**
     * Represents an empty implementation of the ToolArgs interface.
     */
    @Serializable
    public class Empty : ToolArgs
}
