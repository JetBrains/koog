@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.core.tools

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual class ToolRegistryBuilder {
    private val builder = ToolRegistry.Builder()

    public actual fun tool(tool: Tool<*, *>): ToolRegistryBuilder = apply { builder.tool(tool) }

    public actual fun tools(toolsList: List<Tool<*, *>>): ToolRegistryBuilder = apply { builder.tools(toolsList) }

    public actual fun build(): ToolRegistry = builder.build()
}
