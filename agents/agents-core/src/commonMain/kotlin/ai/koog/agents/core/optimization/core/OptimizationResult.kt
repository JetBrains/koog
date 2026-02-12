package ai.koog.agents.core.optimization.core

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.annotation.InternalAgentsApi
import kotlinx.coroutines.withContext
import kotlin.run

/**
 * Result of an optimization run, containing the best configuration found
 * and associated metrics.
 *
 * @property config The best configuration found during optimization.
 * @property score The validation score achieved by this configuration.
 * @property iterations Number of configurations evaluated during search.
 * @property metadata Additional metadata about the optimization run (e.g., timing, per-node scores).
 */
public data class OptimizationResult(
    val config: OptimizationConfig,
    val score: Double,
    val iterations: Int,
    val metadata: Map<String, Any> = emptyMap(),
)

/**
 * Creates an optimized copy of the given agent with this result's [OptimizationConfig] baked in.
 *
 * The returned agent is a new [GraphAIAgent] instance that automatically injects the
 * optimization config into the coroutine context on every [AIAgent.run] call. The original
 * agent is not modified.
 *
 * The new agent:
 * - Has its own pipeline (user's features are re-installed via the original's `installFeatures` block)
 * - Shares the same strategy, executor, tools, and config as the original
 * - Overrides [AIAgent.run] to wrap execution with `withContext(optimizationConfig)`
 *
 * Example usage:
 * ```kotlin
 * val result = optimizer.optimize(executor, config, strategy, trainset, ...)
 *
 * val agent = AIAgent(executor, config, strategy) {
 *     // user features (event handlers, etc.)
 * }
 * val optimizedAgent = result.toAgent(agent)
 * optimizedAgent.run(input) // optimization config is applied automatically
 * ```
 *
 * @param TInput The agent's input type.
 * @param TOutput The agent's output type.
 * @param agent The original agent to create an optimized copy of.
 *  Must be a [GraphAIAgent] instance (which is the case for all graph-based agents).
 * @return A new agent with the optimization config baked in.
 * @throws IllegalArgumentException if the agent is not a [GraphAIAgent].
 */
@OptIn(InternalAgentsApi::class)
public fun <TInput, TOutput> OptimizationResult.toAgent(
    agent: AIAgent<TInput, TOutput>,
): AIAgent<TInput, TOutput> {
    val graphAgent = agent as? GraphAIAgent<TInput, TOutput>
        ?: error("toAgent requires a GraphAIAgent instance")

    val optimizationConfig = this.config

    return object : GraphAIAgent<TInput, TOutput>(
        inputType = graphAgent.inputType,
        outputType = graphAgent.outputType,
        promptExecutor = graphAgent.promptExecutor,
        agentConfig = graphAgent.agentConfig,
        strategy = graphAgent.strategy,
        toolRegistry = graphAgent.toolRegistry,
        clock = graphAgent.clock,
        installFeatures = graphAgent.installFeatures,
    ) {
        override suspend fun run(agentInput: TInput, sessionId: String?): TOutput =
            withContext(optimizationConfig) { super.run(agentInput, sessionId) }
    }
}
