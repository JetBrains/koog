package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.tools.Tool

/**
 * API for [RollbackToolRegistryBuilder]
 */
public abstract class RollbackToolRegistryBuilderCommon<Self : RollbackToolRegistryBuilderCommon<Self>> internal constructor() {
    private val rollbackToolsMap = mutableMapOf<Tool<*, *>, Tool<*, *>>()

    /** Returns the current builder instance with the concrete type for chaining. */
    protected abstract fun self(): Self

    /**
     * Registers a rollback relationship between the provided tool and its corresponding rollback tool.
     * Ensures that the tool is not already defined in the rollback tools map.
     *
     * @param tool The primary tool to register.
     * @param rollbackTool The tool that acts as the rollback counterpart to the provided tool.
     * @throws IllegalArgumentException If the tool is already defined in the rollbackToolsMap.
     */
    public open fun <TArgs> registerRollback(
        tool: Tool<TArgs, *>,
        rollbackTool: Tool<TArgs, *>
    ): Self {
        require(tool.name !in rollbackToolsMap.map { it.key.name }) { "Tool \"${tool.name}\" is already defined" }
        rollbackToolsMap[tool] = rollbackTool
        return self()
    }

    /**
     * Builds and returns an instance of [RollbackToolRegistry] initialized with the registered rollback tools.
     *
     * @return A [RollbackToolRegistry] instance containing the registered tools and their corresponding rollback tools.
     */
    public open fun build(): RollbackToolRegistry {
        return RollbackToolRegistry(rollbackToolsMap)
    }

    internal fun registerRollbackUnsafe(
        tool: Tool<*, *>,
        rollbackTool: Tool<*, *>
    ): Self {
        require(tool.name !in rollbackToolsMap.map { it.key.name }) { "Tool \"${tool.name}\" is already defined" }
        rollbackToolsMap[tool] = rollbackTool
        return self()
    }
}
