package ai.koog.agents.core.optimization.core

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy

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
 * Interface for strategy optimizers that find the best instruction and demonstration
 * configurations for nodes in an [AIAgentGraphStrategy].
 *
 * Optimizers implement various algorithms:
 * - **LabeledFewShot**: Simple optimizer that samples demonstrations from labeled data
 * - **BootstrapFewShot**: Generates demonstrations via LLM execution and filters by metric
 * - **MIPROv2**: Full optimization with instruction proposals and configuration search
 *
 * All optimizers follow these principles:
 * - Immutable: Never modify the input strategy; return results via [OptimizationResult]
 * - Parallel-safe: Configurations evaluated via coroutine context, not by mutating nodes
 * - Type-preserving: Output strategy has the same type signature as input
 *
 * Example usage:
 * ```kotlin
 * val optimizer = MIPROv2Optimizer(config)
 *
 * // Define a metric
 * val exactMatch: Metric<String> = { expected, actual -> if (expected == actual) 1.0 else 0.0 }
 *
 * // Optimize and get best configuration
 * val result = optimizer.optimize(
 *     strategy = myStrategy,
 *     trainset = trainingExamples,
 *     valset = validationExamples,
 *     metric = exactMatch
 * )
 *
 * println("Best score: ${result.score}")
 * println("Iterations: ${result.iterations}")
 * ```
 */
public interface StrategyOptimizer {

    /**
     * Optimizes the strategy's [OptimizableNode][ai.koog.agents.core.optimization.OptimizableNode]s
     * and returns the best configuration found.
     *
     * Only nodes created with the `optimizableNode` DSL participate in optimization. These nodes
     * declare their field mappings (`inputField`/`outputField`) which optimizers use to create
     * per-node demonstrations from the training data.
     *
     * During optimization, different instruction and demonstration combinations are evaluated
     * by passing them through the coroutine context via [OptimizationConfig].
     *
     * @param TInput The strategy's input type.
     * @param TOutput The strategy's output type.
     * @param strategy The strategy to optimize.
     * @param trainset Training examples used for bootstrapping demonstrations and evaluation.
     * @param valset Validation examples for evaluating configurations. If null, a portion of
     *  trainset will be split off for validation.
     * @param metric The metric function to evaluate predictions against expected labels.
     * @return The optimization result containing the best configuration found.
     */
    public suspend fun <TInput, TOutput> optimize(
        strategy: AIAgentGraphStrategy<TInput, TOutput>,
        trainset: Dataset,
        valset: Dataset? = null,
        metric: Metric<TOutput>,
    ): OptimizationResult
}

/**
 * Configuration for strategy optimizers.
 *
 * This provides common configuration options shared across different optimizer implementations.
 * Specific optimizers may have additional configuration via their own config classes.
 *
 * @property maxDemos Maximum number of demonstrations to include per node.
 * @property validationSplit Fraction of trainset to use for validation if valset is not provided.
 * @property parallelism Number of configurations to evaluate in parallel.
 * @property verbose Whether to log detailed progress information.
 */
public open class StrategyOptimizerConfig(
    public open val maxDemos: Int = 3,
    public open val validationSplit: Double = 0.2,
    public open val parallelism: Int = 4,
    public open val verbose: Boolean = false,
)
