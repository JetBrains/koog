package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.tools.Tool

/**
 * API for [RollbackToolRegistryBuilder]
 */
public interface RollbackToolRegistryBuilderAPI {
    /**
     * Registers a rollback relationship between the provided tool and its corresponding rollback tool.
     * Ensures that the tool is not already defined in the rollback tools map.
     *
     * @param tool The primary tool to register.
     * @param rollbackTool The tool that acts as the rollback counterpart to the provided tool.
     * @throws IllegalArgumentException If the tool is already defined in the rollbackToolsMap.
     */
    public fun <TArgs> registerRollback(
        tool: Tool<TArgs, *>,
        rollbackTool: Tool<TArgs, *>
    ): RollbackToolRegistryBuilderAPI

    /**
     * Builds and returns an instance of [RollbackToolRegistry] initialized with the registered rollback tools.
     *
     * @return A [RollbackToolRegistry] instance containing the registered tools and their corresponding rollback tools.
     */
    public fun build(): RollbackToolRegistry
}
