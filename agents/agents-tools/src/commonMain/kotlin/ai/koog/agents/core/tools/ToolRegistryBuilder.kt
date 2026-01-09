package ai.koog.agents.core.tools

/**
 * A builder class for creating a `ToolRegistry` instance. This class provides methods to configure
 * and register tools, either individually or as a list, and then constructs a registry containing
 * the defined tools.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public expect class ToolRegistryBuilder() {
    /**
     * Registers a tool to the ToolRegistryBuilder.
     *
     * @param tool The tool to be registered. This should be an instance of the `Tool` class,
     *             parameterized with argument and result types.
     * @return The updated `ToolRegistryBuilder` instance, allowing for further tool registration
     *         or subsequent actions.
     */
    public fun tool(tool: Tool<*, *>): ToolRegistryBuilder

    /**
     * Registers a list of tools into the tool registry.
     *
     * @param toolsList A list of tools to be registered, where each tool is an instance of the `Tool` class.
     * @return A `ToolRegistryBuilder` instance for further configuration or building.
     */
    public fun tools(toolsList: List<Tool<*, *>>): ToolRegistryBuilder

    /**
     * Finalizes the configuration of the `ToolRegistryBuilder` and constructs a `ToolRegistry` instance.
     *
     * This method aggregates all the tools that have been added using the builder methods
     * and creates a new `ToolRegistry` object containing these tools.
     *
     * @return A `ToolRegistry` instance with the configured tools from the builder.
     */
    public fun build(): ToolRegistry
}
