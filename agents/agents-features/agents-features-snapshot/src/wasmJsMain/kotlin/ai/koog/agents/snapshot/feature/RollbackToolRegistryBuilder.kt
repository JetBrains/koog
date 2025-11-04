@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.Tool

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")
public actual open class RollbackToolRegistryBuilder actual constructor(
    internal actual val delegate: RollbackToolRegistryBuilderImpl
) : RollbackToolRegistryBuilderAPI {
    actual override fun <TArgs> registerRollback(
        tool: Tool<TArgs, *>,
        rollbackTool: Tool<TArgs, *>
    ): RollbackToolRegistryBuilder = apply { delegate.registerRollback(tool, rollbackTool) }

    actual override fun build(): RollbackToolRegistry = delegate.build()
}
