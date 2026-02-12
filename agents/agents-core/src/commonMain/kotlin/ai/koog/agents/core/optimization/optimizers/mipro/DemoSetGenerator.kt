package ai.koog.agents.core.optimization.optimizers.mipro

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.optimization.core.Dataset
import ai.koog.agents.core.optimization.core.Demonstration
import ai.koog.agents.core.optimization.core.Example
import ai.koog.agents.core.optimization.core.Metric
import ai.koog.agents.core.optimization.optimizers.BootstrapFewShot
import ai.koog.agents.core.optimization.optimizers.utils.findOptimizableModules
import ai.koog.agents.core.optimization.optimizers.utils.sampleLabeledDemonstrations
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import kotlin.random.Random

/**
 * Generates N diverse demo candidate sets for each optimizable node in a strategy.
 *
 * This is Step 1 of the MIPRO v2 pipeline. It creates multiple candidate demo sets using
 * different strategies to encourage diversity:
 *
 * 1. **Zero-shot set** (empty demos per node) — if [includeNonBootstrapped] is true
 * 2. **Labeled-only set** (sampled from node's pre-existing demonstrations) — if
 *    [includeNonBootstrapped] is true and [maxLabeledDemos] > 0
 * 3. **Unshuffled bootstrap** — runs [BootstrapFewShot] on the original trainset without
 *    metric filtering (accepts all traces)
 * 4. **Shuffled bootstraps** — fills remaining slots by running [BootstrapFewShot] on
 *    shuffled copies of the trainset with random demo counts and metric threshold
 *
 * The total number of candidate sets per node is always exactly [numCandidateSets].
 *
 * @param TInput The strategy's input type.
 * @param TOutput The strategy's output type.
 * @param promptExecutor The executor for LLM calls.
 * @param agentConfig The agent configuration.
 * @param strategy The strategy containing optimizable nodes.
 * @param trainset Training examples to bootstrap from.
 * @param numCandidateSets Total number of candidate sets to generate per node.
 * @param maxBootstrappedDemos Maximum bootstrapped demos per node per bootstrap run.
 * @param maxLabeledDemos Maximum labeled demos per node.
 * @param metric Metric for evaluating bootstrapped examples (used in shuffled bootstrap).
 * @param metricThreshold Threshold for accepting bootstrapped examples in shuffled runs.
 * @param maxErrors Maximum errors before stopping a bootstrap run.
 * @param maxRounds Maximum retry rounds per example in bootstrap.
 * @param toolRegistry Tools available to the agent.
 * @param includeNonBootstrapped If true, includes zero-shot and labeled-only demo sets.
 *  If false, all slots are filled with bootstrap sets.
 * @param inputFromExample Maps an [Example] to the strategy's typed input.
 * @param random Random instance for reproducibility.
 * @return Map from node name to list of candidate demo sets (each set is a list of [Demonstration]s),
 *  or null if zero-shot mode (both maxBootstrappedDemos and maxLabeledDemos are 0).
 */
public suspend fun <TInput, TOutput> generateDemoSets(
    promptExecutor: PromptExecutor,
    agentConfig: AIAgentConfig,
    strategy: AIAgentGraphStrategy<TInput, TOutput>,
    trainset: Dataset,
    numCandidateSets: Int,
    maxBootstrappedDemos: Int,
    maxLabeledDemos: Int,
    metric: Metric<TOutput>?,
    metricThreshold: Double?,
    maxErrors: Int?,
    maxRounds: Int = 1,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    includeNonBootstrapped: Boolean = true,
    inputFromExample: (Example) -> TInput,
    random: Random = Random(42L),
): Map<String, List<List<Demonstration<*, *>>>>? {

    // Zero-shot mode: no demos at all
    if (maxBootstrappedDemos == 0 && maxLabeledDemos == 0) {
        return null
    }

    val modules = strategy.findOptimizableModules()
    if (modules.isEmpty()) return null

    // Output map: node name → mutable list of demo sets
    val out: MutableMap<String, MutableList<List<Demonstration<*, *>>>> =
        modules.associate { it.name to mutableListOf<List<Demonstration<*, *>>>() }.toMutableMap()

    /**
     * Extracts per-node demonstrations from a BootstrapFewShot result and adds them
     * as one candidate set per node.
     */
    fun addFromBootstrapResult(
        resultDemos: Map<String, List<Demonstration<*, *>>>
    ) {
        for (module in modules) {
            val demos = resultDemos[module.name] ?: emptyList()
            out.getOrPut(module.name) { mutableListOf() }.add(demos)
        }
    }

    var adjustedCount = numCandidateSets

    // 1. Zero-shot set: empty list per node
    if (includeNonBootstrapped) {
        adjustedCount--
        for (module in modules) {
            out.getOrPut(module.name) { mutableListOf() }.add(emptyList())
        }
    }

    // 2. Labeled-only set: sampled from node's pre-existing demonstrations
    if (maxLabeledDemos > 0 && includeNonBootstrapped) {
        adjustedCount--
        for (module in modules) {
            val labeled = sampleLabeledDemonstrations(
                module.demonstrations,
                k = maxLabeledDemos,
                random = random
            )
            out.getOrPut(module.name) { mutableListOf() }.add(labeled)
        }
    }

    // 3. Unshuffled bootstrap: no metric filtering (accept all traces)
    adjustedCount--
    val unshuffledOptimizer = BootstrapFewShot(
        maxBootstrappedDemos = maxBootstrappedDemos,
        maxLabeledDemos = maxLabeledDemos,
        maxRounds = maxRounds,
        maxErrors = maxErrors,
        metricThreshold = 1.0, // doesn't matter since metric is null
        random = random,
    )
    val unshuffledResult = unshuffledOptimizer.optimize(
        promptExecutor = promptExecutor,
        agentConfig = agentConfig,
        strategy = strategy,
        trainset = trainset,
        toolRegistry = toolRegistry,
        metric = null, // null metric → accept all traces
        inputFromExample = inputFromExample,
    )
    addFromBootstrapResult(unshuffledResult.config.demonstrations)

    // 4. Shuffled bootstraps: fill remaining slots with shuffled trainset + random demo count
    repeat(maxOf(0, adjustedCount)) {
        val shuffledTrainset = trainset.shuffled(random)
        val numDemos = random.nextInt(1, maxBootstrappedDemos + 1)

        val shuffledOptimizer = BootstrapFewShot(
            maxBootstrappedDemos = numDemos,
            maxLabeledDemos = maxLabeledDemos,
            maxRounds = maxRounds,
            maxErrors = maxErrors,
            metricThreshold = metricThreshold ?: 1.0,
            random = random,
        )
        val shuffledResult = shuffledOptimizer.optimize(
            promptExecutor = promptExecutor,
            agentConfig = agentConfig,
            strategy = strategy,
            trainset = shuffledTrainset,
            toolRegistry = toolRegistry,
            metric = metric,
            inputFromExample = inputFromExample,
        )
        addFromBootstrapResult(shuffledResult.config.demonstrations)
    }

    return out.mapValues { (_, v) -> v.toList() }
}
