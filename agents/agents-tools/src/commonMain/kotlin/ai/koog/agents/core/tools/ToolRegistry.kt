package ai.koog.agents.core.tools

/**
 * A registry that manages a collection of tools for use by agents.
 *
 * ToolRegistry serves as a central repository for all tools available to an agent.
 * It provides functionality to register tools and retrieve them by name or type.
 *
 * Key features:
 * - Maintains a unique collection of named tools
 * - Provides methods to retrieve tools by name or type
 * - Supports merging multiple registries
 *
 * Usage examples:
 * 1. Creating a registry:
 *    ```
 *    val registry = ToolRegistry {
 *        tool(MyCustomTool())
 *        tool(AnotherTool())
 *    }
 *    ```
 * 2. Merging registries:
 *    ```
 *    val combinedRegistry = registry1 + registry2
 *    ```
 *
 * @property tools The list of tools contained in this registry
 */
public class ToolRegistry private constructor(tools: List<Tool<*, *>> = emptyList()) {

    private val _tools: MutableList<Tool<*, *>> = tools.toMutableList()

    /**
     * Provides an immutable list of tools currently available in the registry.
     *
     * The tools are sourced from the internal backing collection and returned as
     * a read-only list to prevent external modification of the registry state.
     */
    public val tools: List<Tool<*, *>>
        get() = _tools.toList()

    /**
     * Retrieves a tool by its name from the registry, or null if not found.
     *
     * This method searches for a tool with the specified name and returns null
     * if no matching tool is found.
     *
     * @param toolName The name of the tool to retrieve
     * @return The tool with the specified name, or null if not found
     */
    public fun getToolOrNull(toolName: String): Tool<*, *>? {
        return _tools.firstOrNull { it.name == toolName }
    }

    /**
     * Retrieves a tool by its name from the registry.
     *
     * This method searches for a tool with the specified name.
     *
     * @param toolName The name of the tool to retrieve
     * @return The tool with the specified name
     * @throws IllegalArgumentException if no tool with the specified name is found
     */
    public fun getTool(toolName: String): Tool<*, *> {
        return getToolOrNull(toolName)
            ?: throw IllegalArgumentException("Tool \"$toolName\" is not defined")
    }

    /**
     * Retrieves a tool by its type from registry.
     *
     * This method searches for a tool of the specified type.
     *
     * @param T The type of tool to retrieve
     * @return The tool of the specified type
     * @throws IllegalArgumentException if no tool of the specified type is found
     */
    public inline fun <reified T : Tool<*, *>> getTool(): T {
        return tools
            .firstOrNull { it::class == T::class }
            ?.let { it as? T }
            ?: throw IllegalArgumentException("Tool with type ${T::class} is not defined")
    }

    /**
     * Combines the tools from this registry and the provided registry into a new ToolRegistry.
     *
     * This method merges the tools from both registries, ensuring that each tool is included only once,
     * based on its name.
     *
     * @param toolRegistry The other ToolRegistry whose tools will be merged with the current registry.
     * @return A new ToolRegistry containing the combined list of tools from both registries.
     */
    public operator fun plus(toolRegistry: ToolRegistry): ToolRegistry {
        val mergedTools = (this.tools + toolRegistry.tools).distinctBy { it.name }
        return ToolRegistry(mergedTools)
    }

    /**
     * Adds a tool to the registry if it is not already present.
     *
     * @param tool The tool to be added to the registry.
     */
    public fun add(tool: Tool<*, *>) {
        if (_tools.contains(tool)) return
        _tools.add(tool)
    }

    /**
     * Adds multiple tools to the registry.
     *
     * This method accepts a variable number of tools and adds each of them to the registry.
     *
     * @param tools The tools to be added to the registry.
     */
    public fun addAll(vararg tools: Tool<*, *>) {
        tools.forEach { tool -> add(tool) }
    }

    /**
     * Builder class to construct and manage a registry of tools.
     *
     * This class allows for the registration of tools in a controlled manner.
     * It ensures that each tool added to the registry has a unique name.
     */
    public class Builder internal constructor() {
        private val tools = mutableListOf<Tool<*, *>>()

        /**
         * Add a tool to the registry
         */
        public fun tool(tool: Tool<*, *>) {
            require(tool.name !in tools.map { it.name }) { "Tool \"${tool.name}\" is already defined" }
            tools.add(tool)
        }

        /**
         * Add multiple tools to the registry
         */
        public fun tools(toolsList: List<Tool<*, *>>) {
            toolsList.forEach { tool(it) }
        }

        internal fun build(): ToolRegistry {
            return ToolRegistry(tools)
        }
    }

    /**
     * Companion object providing factory methods and constants for ToolRegistry.
     */
    public companion object {
        /**
         * Creates a new ToolRegistry using the provided builder initialization block.
         *
         * @param init A lambda that configures the registry by adding tools
         * @return A new ToolRegistry instance configured according to the initialization block
         */
        public operator fun invoke(init: Builder.() -> Unit): ToolRegistry = Builder().apply(init).build()

        /**
         * A constant representing an empty registry with no tools.
         */
        public val EMPTY: ToolRegistry = ToolRegistry(emptyList())
    }
}
