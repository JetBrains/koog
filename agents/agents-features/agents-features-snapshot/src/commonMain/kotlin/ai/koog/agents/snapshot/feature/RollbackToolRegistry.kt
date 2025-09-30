package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.tools.Tool

/**
 * A registry for managing and retrieving rollback tools associated with specific tools.
 * This class allows for the association of tools that have rollback counterparts
 * and provides mechanisms for retrieval and addition of such tools.
 *
 * This class is immutable from an external perspective, ensuring thread safety.
 */
public class RollbackToolRegistry private constructor(rollbackToolsMap: Map<Tool<*, *>, Tool<*, *>> = emptyMap()) {

    /**
     * A mutable map that stores the association between tools and their corresponding rollback tools.
     * This map is initialized as a mutable copy of the `rollbackToolsMap`.
     * It is used to manage and track tools along with their rollback counterparts.
     */
    private val _rollbackToolsMap: MutableMap<Tool<*, *>, Tool<*, *>> = rollbackToolsMap.toMutableMap()

    /**
     * A public property representing a map of tools and their corresponding rollback tools.
     *
     * This property provides a read-only view of the internal rollback tools mapping,
     * allowing retrieval of paired tools that facilitate rollback operations.
     * The keys in this map are tools, and the values are their associated rollback tools.
     */
    public val rollbackToolsMap: Map<Tool<*, *>, Tool<*, *>>
        get() = _rollbackToolsMap.toMap()

    /**
     * Retrieves a rollback tool by its name.
     *
     * @param toolName The name of the tool to retrieve.
     * @return The rollback tool associated with the given name.
     * @throws IllegalArgumentException If no tool with the specified name is defined.
     */
    public fun getRollbackTool(toolName: String): Tool<*, *>? {
        return rollbackToolsMap
            .entries
            .firstOrNull { it.key.name == toolName }
            ?.value
    }

    /**
     * Combines the rollback tools from the current `RollbackToolRegistry` instance
     * and the specified `rollbackToolRegistry` into a new `RollbackToolRegistry`.
     *
     * @param rollbackToolRegistry The other `RollbackToolRegistry` whose rollback tools
     * will be combined with the current instance.
     * @return A new `RollbackToolRegistry` containing the combined rollback tools
     * from both instances.
     */
    public operator fun plus(rollbackToolRegistry: RollbackToolRegistry): RollbackToolRegistry {
        return RollbackToolRegistry(this.rollbackToolsMap + rollbackToolRegistry.rollbackToolsMap)
    }

    /**
     * Adds a tool and its corresponding rollback tool to the registry.
     * If the tool is already present in the registry, the method does nothing.
     *
     * @param tool the tool to add to the registry
     * @param rollbackTool the rollback tool associated with the specified tool
     */
    public fun <TArgs> add(tool: Tool<TArgs, *>, rollbackTool: Tool<TArgs, *>) {
        if (_rollbackToolsMap.contains(tool)) return
        _rollbackToolsMap[tool] = rollbackTool
    }

    /**
     * A builder class responsible for creating a RollbackToolRegistry while managing associations
     * between tools and their corresponding rollback tools.
     */
    public class Builder internal constructor() {
        /**
         * A map that associates tools with their corresponding rollback tools.
         *
         * This map is used to maintain the relationship between a tool and its designated rollback tool,
         * allowing for rollback actions to be registered and performed as needed. The keys represent tools,
         * while the associated values represent the rollback tools for each respective key.
         */
        private val rollbackToolsMap = mutableMapOf<Tool<*, *>, Tool<*, *>>()

        /**
         * Registers a rollback relationship between the provided tool and its corresponding rollback tool.
         * Ensures that the tool is not already defined in the rollback tools map.
         *
         * @param tool The primary tool to register.
         * @param rollbackTool The tool that acts as the rollback counterpart to the provided tool.
         * @throws IllegalArgumentException If the tool is already defined in the rollbackToolsMap.
         */
        public fun <TArgs> registerRollback(tool: Tool<TArgs, *>, rollbackTool: Tool<TArgs, *>) {
            require(tool.name !in rollbackToolsMap.map { it.key.name }) { "Tool \"${tool.name}\" is already defined" }
            rollbackToolsMap[tool] = rollbackTool
        }

        /**
         * Builds and returns an instance of [RollbackToolRegistry] initialized with the registered rollback tools.
         *
         * @return A [RollbackToolRegistry] instance containing the registered tools and their corresponding rollback tools.
         */
        internal fun build(): RollbackToolRegistry {
            return RollbackToolRegistry(rollbackToolsMap)
        }
    }

    /**
     * Companion object for the RollbackToolRegistry class.
     * Provides utility methods and predefined registry instances.
     */
    public companion object {
        /**
         * Invokes the builder pattern to create and configure an instance of [RollbackToolRegistry].
         *
         * @param init A lambda function used to configure the [Builder] object.
         * @return A fully built instance of [RollbackToolRegistry] configured using the [init] lambda.
         */
        public operator fun invoke(init: Builder.() -> Unit): RollbackToolRegistry = Builder().apply(init).build()

        /**
         * Represents an empty instance of the [RollbackToolRegistry].
         *
         * This constant provides a default, immutable `RollbackToolRegistry` with no registered tools.
         * It can be used as a placeholder or a base instance when no rollback tools are required.
         */
        public val EMPTY: RollbackToolRegistry = RollbackToolRegistry(emptyMap())
    }
}
