@file:Suppress("UnusedReceiverParameter")

package ai.koog.agents.core.optimization.dsl

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.optimization.core.Demonstration
import ai.koog.agents.core.optimization.core.OptimizationConfig
import kotlinx.coroutines.currentCoroutineContext

/**
 * Gets the instruction for a node, checking the coroutine context for optimization overrides first.
 *
 * During MIPRO optimization, candidate instructions are passed via [OptimizationConfig] in the
 * coroutine context. This helper checks for such overrides before falling back to the default.
 *
 * This is called automatically by [OptimizableNode][ai.koog.agents.core.optimization.OptimizableNode]'s
 * execute lambda. It can also be used in custom [OptimizableNodePromptBuildFn][ai.koog.agents.core.optimization.OptimizableNodePromptBuildFn]
 * implementations, though typically the instruction is already resolved and passed as a parameter.
 *
 * @param nodeName The name of the node.
 * @param default The default instruction from the node definition, or null if none.
 * @return The instruction to use: context override if present, otherwise the default.
 * @throws IllegalStateException if neither context override nor default is available.
 */
public suspend fun AIAgentGraphContextBase.getNodeInstruction(
    nodeName: String,
    default: String?
): String {
    return currentCoroutineContext()[OptimizationConfig]?.getInstruction(nodeName)
        ?: default
        ?: error("No instruction available for node '$nodeName'. Provide a default or ensure OptimizationConfig is set.")
}

/**
 * Gets the instruction for a node if available, returning null otherwise.
 *
 * Unlike [getNodeInstruction], this does not throw if no instruction is available.
 *
 * @param nodeName The name of the node.
 * @param default The default instruction from the node definition, or null if none.
 * @return The instruction to use, or null if neither context override nor default is available.
 */
public suspend fun AIAgentGraphContextBase.getNodeInstructionOrNull(
    nodeName: String,
    default: String?
): String? {
    return currentCoroutineContext()[OptimizationConfig]?.getInstruction(nodeName) ?: default
}

/**
 * Gets the demonstrations for a node, checking the coroutine context for optimization overrides first.
 *
 * During MIPRO optimization, candidate demonstration sets are passed via [OptimizationConfig] in the
 * coroutine context. This helper checks for such overrides before falling back to the default.
 *
 * Note: Due to type erasure, this performs an unchecked cast. The caller must ensure type compatibility.
 *
 * This is called automatically by [OptimizableNode][ai.koog.agents.core.optimization.OptimizableNode]'s
 * execute lambda. It can also be used in custom [OptimizableNodePromptBuildFn][ai.koog.agents.core.optimization.OptimizableNodePromptBuildFn]
 * implementations, though typically demos are already resolved and passed as a parameter.
 *
 * @param TInput The input type of the demonstrations.
 * @param TOutput The output type of the demonstrations.
 * @param nodeName The name of the node.
 * @param default The default demonstrations (typically empty for a fresh node).
 * @return The demonstrations to use: context override if present, otherwise the default.
 */
public suspend inline fun <reified TInput, reified TOutput> AIAgentGraphContextBase.getNodeDemonstrations(
    nodeName: String,
    default: List<Demonstration<TInput, TOutput>>
): List<Demonstration<TInput, TOutput>> {
    return currentCoroutineContext()[OptimizationConfig]?.getTypedDemonstrations<TInput, TOutput>(nodeName)
        ?: default
}

/**
 * Checks whether an optimization configuration is present in the current coroutine context.
 *
 * This can be used to conditionally enable optimization-aware behavior in node lambdas.
 *
 * @return True if an [OptimizationConfig] is present in the coroutine context.
 */
public suspend fun AIAgentGraphContextBase.hasOptimizationConfig(): Boolean {
    return currentCoroutineContext()[OptimizationConfig] != null
}

/**
 * Gets the current [OptimizationConfig] from the coroutine context, if present.
 *
 * @return The current optimization config, or null if not in an optimization context.
 */
public suspend fun AIAgentGraphContextBase.getOptimizationConfig(): OptimizationConfig? {
    return currentCoroutineContext()[OptimizationConfig]
}
