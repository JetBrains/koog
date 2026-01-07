package ai.koog.agents.core.tools

/**
 * A builder class for constructing a `ToolRegistry`.
 *
 * The `ToolRegistryBuilder` provides an API for adding tools to the registry, either individually or as a list,
 * and for finalizing the construction of the registry. It ensures each tool has a unique name and that
 * the registry is configured appropriately.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual class ToolRegistryBuilder {
    /**
     * Instance of `ToolRegistry.Builder` used to construct and manage the registration of tools.
     *
     * The `builder` acts as the internal mechanism for incrementally adding tools to a `ToolRegistry`.
     * It ensures that each registered tool has a unique name and provides methods to finalize the registry construction process.
     *
     * The builder supports:
     * - Adding single or multiple tools to the registry.
     * - Finalizing the registration and creating a `ToolRegistry` instance containing the registered tools.
     */
    private val builder = ToolRegistry.Builder()

    /**
     * Adds a tool to the registry builder. This method registers a given tool, allowing it to be included
     * in the resulting tool registry when built.
     *
     * @param tool The tool instance to be added. Must not conflict with an already registered tool of the same name.
     * @return The instance of the `ToolRegistryBuilder` for method chaining.
     */
    public actual fun tool(tool: Tool<*, *>): ToolRegistryBuilder = apply { builder.tool(tool) }

    /**
     * Adds a list of tools to the tool registry builder.
     *
     * @param toolsList The list of tools to be added to the registry. Each tool is represented by an instance of [Tool] with specific arguments and result types.
     * @return The current instance of [ToolRegistryBuilder] to enable method chaining.
     */
    public actual fun tools(toolsList: List<Tool<*, *>>): ToolRegistryBuilder = apply { builder.tools(toolsList) }

    /**
     * Finalizes the tool registry configuration and constructs a `ToolRegistry` instance.
     *
     * This method gathers all tools added to the builder and creates a new `ToolRegistry`
     * instance containing those tools.
     *
     * @return A `ToolRegistry` instance containing the tools registered through the builder.
     */
    public actual fun build(): ToolRegistry = builder.build()
}
