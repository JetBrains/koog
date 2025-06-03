package ai.koog.agents.core.tools

import kotlinx.serialization.Serializable

public interface ToolArgs {

    @Serializable
    public class Empty : ToolArgs
}
