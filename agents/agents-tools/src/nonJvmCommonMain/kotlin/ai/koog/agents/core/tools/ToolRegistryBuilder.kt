@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.tools

public actual class ToolRegistryBuilder {
    private val tools = mutableListOf<ToolBase<*, *>>()

    public actual fun tool(tool: ToolBase<*, *>): ToolRegistryBuilder = apply {
        tools.addTool(tool)
    }

    public actual fun tools(toolsList: List<ToolBase<*, *>>): ToolRegistryBuilder = apply {
        toolsList.forEach { tool(it) }
    }

    public actual fun build(): ToolRegistry {
        return ToolRegistry(tools.toList())
    }
}
