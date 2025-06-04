package ai.koog.agents.mcp.provider

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.utils.Closeable

public interface McpClientProvider : Closeable {

    public fun getTools(): List<Tool>
}
