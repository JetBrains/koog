package ai.koog.agents.core.optimization.core

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
