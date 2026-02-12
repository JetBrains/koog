package ai.koog.agents.core.optimization.dsl

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.optimization.core.OptimizationConfig
import kotlinx.coroutines.currentCoroutineContext

/**
 * Gets the current [OptimizationConfig] from the coroutine context, if present.
 *
 * @return The current optimization config, or null if not in an optimization context.
 */
@Suppress("UnusedReceiverParameter")
public suspend fun AIAgentGraphContextBase.getOptimizationConfig(): OptimizationConfig? {
    return currentCoroutineContext()[OptimizationConfig]
}
