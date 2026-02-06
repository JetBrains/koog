package ai.koog.agents.core.optimization.optimizers

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.optimization.core.Dataset
import ai.koog.agents.core.optimization.core.Demonstration
import ai.koog.agents.core.optimization.core.Example
import ai.koog.agents.core.optimization.core.Metric
import ai.koog.agents.core.optimization.core.OptimizationConfig
import ai.koog.agents.core.optimization.core.OptimizationResult
import ai.koog.agents.core.optimization.core.StrategyOptimizer
import ai.koog.agents.core.optimization.util.findOptimizableNodes
import kotlin.random.Random

/**
 * Simplest optimizer: samples labeled examples as demonstrations for each optimizable node.
 *
 * LabeledFewShot does not generate or evaluate candidate instructions. It takes labeled
 * training examples and converts them directly into [Demonstration]s that are assigned
 * to each optimizable node in the strategy. This is useful as a baseline or when
 * labeled examples are already high-quality.
 *
 * Each optimizable node receives an independently sampled (or ordered) subset of up to [k]
 * demonstrations. Examples without labels are skipped.
 *
 * Example usage:
 * ```kotlin
 * val optimizer = LabeledFewShot(k = 8, sample = true)
 * val result = optimizer.optimize(strategy, trainset, metric = exactMatch)
 *
 * // Use the result config via coroutine context
 * withContext(result.config) {
 *     agent.run(input)
 * }
 * ```
 *
 * @property k Maximum number of demonstrations per node.
 * @property sample If true, randomly samples from the trainset; if false, takes the first [k] examples.
 * @property random Random instance for reproducible sampling.
 */
public class LabeledFewShot(
    public val k: Int = 16,
    public val sample: Boolean = true,
    public val random: Random = Random(42L),
) : StrategyOptimizer {

    override suspend fun <TInput, TOutput> optimize(
        strategy: AIAgentGraphStrategy<TInput, TOutput>,
        trainset: Dataset,
        valset: Dataset?,
        metric: Metric,
    ): OptimizationResult {
        require(trainset.isNotEmpty()) { "trainset is required for LabeledFewShot" }

        val optimizableNodes = strategy.findOptimizableNodes()
        if (optimizableNodes.isEmpty()) {
            return OptimizationResult(
                config = OptimizationConfig(),
                score = 0.0,
                iterations = 0,
            )
        }

        val demonstrations = mutableMapOf<String, List<Demonstration<*, *>>>()

        for (node in optimizableNodes) {
            val selected = if (sample) {
                trainset.shuffled(random).take(k.coerceAtMost(trainset.size))
            } else {
                trainset.take(k.coerceAtMost(trainset.size))
            }

            val demos: List<Demonstration<Map<String, String>, String>> = selected
                .filter { it.hasLabel }
                .map { example ->
                    Demonstration(
                        input = example.inputOnly(),
                        output = example.label!!,
                        isBootstrapped = false,
                    )
                }

            demonstrations[node.name] = demos
        }

        val config = OptimizationConfig(
            demonstrations = demonstrations,
        )

        return OptimizationResult(
            config = config,
            score = 0.0,
            iterations = 1,
            metadata = mapOf(
                "optimizer" to "LabeledFewShot",
                "k" to k,
                "sample" to sample,
                "numNodes" to optimizableNodes.size,
            ),
        )
    }
}
