package ai.koog.agents.core.tools

/**
 * A builder class for creating a [ToolRegistry] instance. This class provides methods to configure
 * and register tools, either individually or as a list, and then constructs a registry containing
 * the defined tools.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public expect class ToolRegistryBuilder() {
    /**
     * Add a tool to the registry
     */
    public fun tool(tool: Tool<*, *>): ToolRegistryBuilder

    /**
     * Add multiple tools to the registry
     */
    public fun tools(toolsList: List<Tool<*, *>>): ToolRegistryBuilder

    /**
     * Builds a [ToolRegistry] instance containing the tools added to the builder.
     */
    public fun build(): ToolRegistry
}

internal fun MutableList<Tool<*, *>>.addTool(tool: Tool<*, *>) {
    require(tool.name !in this.map { it.name }) { "Tool \"${tool.name}\" is already defined" }
    this.add(tool)
}
