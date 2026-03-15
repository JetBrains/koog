@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.core.tools

public actual class ToolRegistryBuilder {
    private val builder = ToolRegistry.Builder()

    public actual fun tool(tool: Tool<*, *>): ToolRegistryBuilder = apply { builder.tool(tool) }

    public actual fun tools(toolsList: List<Tool<*, *>>): ToolRegistryBuilder = apply { builder.tools(toolsList) }

    public actual fun build(): ToolRegistry = builder.build()
}
