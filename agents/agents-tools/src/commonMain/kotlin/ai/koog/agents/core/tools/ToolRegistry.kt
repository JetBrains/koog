package ai.koog.agents.core.tools

import ai.koog.agents.core.tools.permissions.ToolPolicy
import ai.koog.agents.core.tools.permissions.ToolPolicyBuilder

/**
 * Entry in the tool registry containing both tool and its policy.
 */
internal data class ToolEntry(
    val tool: Tool<*, *>,
    val policy: ToolPolicy? = null
)

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
 * - Associates governance policies (permissions, rate limits, caching) with tools
 *
 * Usage examples:
 * 1. Creating a registry:
 *    ```
 *    val registry = ToolRegistry {
 *        tool(MyCustomTool())
 *        tool(AnotherTool()) {
 *            // Permission settings
 *            minimumRole = userRole
 *
 *            // Rate limiting settings
 *            rateLimits { ... }
 *
 *            // Caching settings
 *            cache { ... }
 *        }
 *    }
 *    ```
 * 2. Merging registries:
 *    ```
 *    val combinedRegistry = registry1 + registry2
 *    ```
 */
public class ToolRegistry internal constructor(
    entries: List<ToolEntry> = emptyList()
) {

    private val _entries: MutableList<ToolEntry> = entries.toMutableList()

    /**
     * Provides an immutable list of tools currently available in the registry.
     *
     * The tools are sourced from the internal backing collection and returned as
     * a read-only list to prevent external modification of the registry state.
     */
    public val tools: List<Tool<*, *>>
        get() = _entries.map { it.tool }

    /**
     * Get the policy (permissions, rate limits, caching) for a specific tool.
     *
     * @param tool The tool to get the policy for
     * @return The tool policy or null if no policy is defined
     */
    public fun getToolPolicy(tool: Tool<*, *>): ToolPolicy? =
        _entries.find { it.tool == tool }?.policy

    /**
     * Get the policy by tool name (for convenience during execution).
     *
     * @param toolName The name of the tool
     * @return The tool policy or null if no policy is defined
     */
    public fun getToolPolicyByName(toolName: String): ToolPolicy? =
        _entries.find { it.tool.name == toolName }?.policy

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
        return tools
            .firstOrNull { it.name == toolName }
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
     * Retrieves all tools by their type.
     *
     * @return All tools in the registry
     */
    public fun getToolsByType(): List<Tool<*, *>> = tools

    /**
     * Combines the tools from this registry and the provided registry into a new ToolRegistry.
     *
     * This method merges the tools from both registries, ensuring that each tool is included only once,
     * based on its name. Entries from the right-hand registry take precedence.
     *
     * @param toolRegistry The other ToolRegistry whose tools will be merged with the current registry.
     * @return A new ToolRegistry containing the combined list of tools from both registries.
     */
    public operator fun plus(toolRegistry: ToolRegistry): ToolRegistry {
        val toolsByName = mutableMapOf<String, ToolEntry>()

        // Add entries from this registry
        _entries.forEach { entry ->
            toolsByName[entry.tool.name] = entry
        }

        // Add/override with entries from other registry
        toolRegistry._entries.forEach { entry ->
            toolsByName[entry.tool.name] = entry
        }

        return ToolRegistry(toolsByName.values.toList())
    }

    /**
     * Adds a tool to the registry if it is not already present.
     *
     * @param tool The tool to be added to the registry.
     * @param policy Optional policy for the tool
     */
    public fun add(tool: Tool<*, *>, policy: ToolPolicy? = null) {
        if (_entries.any { it.tool.name == tool.name }) return
        _entries.add(ToolEntry(tool, policy))
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
        private val entries = mutableListOf<ToolEntry>()

        /**
         * Add a tool to the registry
         */
        public fun tool(tool: Tool<*, *>) {
            require(entries.none { it.tool.name == tool.name }) { "Tool \"${tool.name}\" is already defined" }
            entries.add(ToolEntry(tool))
        }

        /**
         * Add a tool to the registry with policy configuration
         */
        public fun tool(tool: Tool<*, *>, configure: ToolPolicyBuilder.() -> Unit) {
            require(entries.none { it.tool.name == tool.name }) { "Tool \"${tool.name}\" is already defined" }

            val policyBuilder = ToolPolicyBuilder()
            policyBuilder.configure()
            entries.add(ToolEntry(tool, policyBuilder.build()))
        }

        /**
         * Add multiple tools to the registry
         */
        public fun tools(toolsList: List<Tool<*, *>>) {
            toolsList.forEach { tool(it) }
        }

        internal fun build(): ToolRegistry {
            return ToolRegistry(entries)
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
