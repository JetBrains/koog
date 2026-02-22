@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.Tool

/**
 * A builder class responsible for creating a RollbackToolRegistry while managing associations
 * between tools and their corresponding rollback tools.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public expect open class RollbackToolRegistryBuilder constructor(
    delegate: RollbackToolRegistryBuilderImpl = RollbackToolRegistryBuilderImpl()
) : RollbackToolRegistryBuilderAPI {
    internal val delegate: RollbackToolRegistryBuilderImpl

    /**
     * Registers a rollback relationship between the provided tool and its corresponding rollback tool.
     * Ensures that the tool is not already defined in the rollback tools map.
     *
     * @param tool The primary tool to register.
     * @param rollbackTool The tool that acts as the rollback counterpart to the provided tool.
     * @throws IllegalArgumentException If the tool is already defined in the rollbackToolsMap.
     */
    public override fun <TArgs> registerRollback(
        tool: Tool<TArgs, *>,
        rollbackTool: Tool<TArgs, *>
    ): RollbackToolRegistryBuilder

    /**
     * Builds and returns an instance of [RollbackToolRegistry] initialized with the registered rollback tools.
     *
     * @return A [RollbackToolRegistry] instance containing the registered tools and their corresponding rollback tools.
     */
    public override fun build(): RollbackToolRegistry
}
