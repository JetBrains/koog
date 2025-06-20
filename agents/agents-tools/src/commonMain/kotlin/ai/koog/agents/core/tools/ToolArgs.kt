package ai.koog.agents.core.tools

import kotlinx.serialization.Serializable

/**
 * Represents the arguments for a tool operation.
 * Args should be serializable, serializer should be presented in the Tool the arguments belong to.
 *
 */
public interface ToolArgs {

    /**
     * Represents an empty implementation of the ToolArgs interface.
     */
    @Serializable
    public class Empty : ToolArgs
}
