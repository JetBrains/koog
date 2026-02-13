package ai.koog.agents.core.optimization.optimizers

import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.optimization.core.OptimizableNode
import ai.koog.agents.core.optimization.core.Dataset
import ai.koog.agents.core.optimization.core.Demonstration
import ai.koog.agents.core.optimization.core.Example
import ai.koog.agents.core.optimization.core.Metric
import ai.koog.agents.core.optimization.core.OptimizationConfig
import ai.koog.agents.core.optimization.core.OptimizationResult
import ai.koog.agents.core.optimization.features.CollectedTraces
import ai.koog.agents.core.optimization.features.TraceCollectionFeature
import ai.koog.agents.core.optimization.features.collectTraces
import ai.koog.agents.core.optimization.optimizers.utils.findOptimizableNodes
import ai.koog.agents.core.optimization.optimizers.utils.sampleLabeledDemonstrations
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/**
 * Outcome of bootstrapping a single example.
 */
private sealed class BootstrapOutcome {
    /**
     * The agent successfully produced an output that passed the metric.
     *
     * @property traces Per-node demonstrations collected during execution.
     */
    data class Success(val traces: Map<String, Demonstration<Any?, Any?>>) : BootstrapOutcome()

    /**
     * The bootstrap attempt failed.
     */
    sealed class Failure : BootstrapOutcome() {
        /** The agent produced output, but it did not meet the metric threshold. */
        data object MetricNotPassed : Failure()

        /** The agent threw an exception during execution. */
        data class ExceptionRaised(val exception: Exception) : Failure()
    }
}

/**
 * Generates demonstrations by running an agent on training data and keeping
 * traces from successful executions.
 *
 * BootstrapFewShot is both a standalone optimizer and Step 1 of the full MIPRO v2 pipeline.
 * Unlike LabeledFewShot, it generates new demonstrations by running an agent and collecting
 * per-node input/output traces via [TraceCollectionFeature].
 *
 * The optimizer takes agent components (executor, config, strategy) rather than a pre-built
 * agent, so it can construct its own internal agent with the necessary tracing features.
 * Use OptimizationResult.toAgent to create a production agent with the optimization baked in.
 *
 * Example usage:
 * ```kotlin
 * val optimizer = BootstrapFewShot(maxBootstrappedDemos = 4, maxLabeledDemos = 8)
 * val result = optimizer.optimize(
 *     promptExecutor = executor,
 *     agentConfig = config,
 *     strategy = myStrategy,
 *     trainset = trainingExamples,
 *     metric = { expected, actual -> if (expected == actual) 1.0 else 0.0 },
 * )
 *
 * // Option 1: Use the result config via coroutine context
 * withContext(result.config) {
 *     agent.run(input)
 * }
 *
 * // Option 2: Create an optimized agent copy
 * val optimizedAgent = result.toAgent(originalAgent)
 * optimizedAgent.run(input)
 * ```
 *
 * @property maxBootstrappedDemos Maximum number of bootstrapped demonstrations per node.
 * @property maxTotalDemos Maximum number of labeled demonstrations used during bootstrapping
 *  and as fallback. Set to 0 to disable. In DSPy it is called maxLabeledDemos, but it is confusing.
 * @property maxRounds Maximum retry rounds per training example.
 * @property maxErrors Maximum total exceptions before stopping. Null means unlimited.
 * @property metricThreshold Minimum metric score to accept a bootstrap.
 * @property random Random instance for reproducible sampling.
 */
public class BootstrapFewShot(
    public val maxBootstrappedDemos: Int = 4,
    public val maxTotalDemos: Int = 16,
    public val maxRounds: Int = 1,
    public val maxErrors: Int? = null,
    public val metricThreshold: Double = 1.0,
    public val random: Random = Random(42L),
) {

    /**
     * Optimizes the strategy by bootstrapping demonstrations from agent executions.
     *
     * For each training example, builds a fresh agent with a trace collection
     * enabled. The caller does not need to install [TraceCollectionFeature] themselves.
     *
     * @param TInput The strategy's input type.
     * @param TOutput The strategy's output type.
     * @param promptExecutor The executor for LLM calls.
     * @param agentConfig The agent configuration (prompt, model, max iterations).
     * @param strategy The strategy to optimize. Must contain [OptimizableNode]s.
     * @param trainset Training examples to bootstrap from.
     * @param toolRegistry Tools available to the agent. Defaults to empty.
     * @param metric Optional metric to evaluate bootstrap quality. If null, all bootstraps are accepted.
     * @return The optimization result with bootstrapped and labeled demonstrations.
     */
    public suspend fun <TInput, TOutput> optimize(
        promptExecutor: PromptExecutor,
        agentConfig: AIAgentConfig,
        strategy: AIAgentGraphStrategy<TInput, TOutput>,
        trainset: Dataset<TInput, TOutput>,
        toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
        metric: Metric<TOutput>? = null,
    ): OptimizationResult {
        require(trainset.isNotEmpty()) { "trainset is required for BootstrapFewShot" }

        val optimizableNodes = strategy.findOptimizableNodes()
        if (optimizableNodes.isEmpty()) {
            return OptimizationResult(
                config = OptimizationConfig(),
                score = 0.0,
                iterations = 0,
            )
        }

        // Populate the optimization config with initially labeled demos
        val baseConfig = if (maxTotalDemos > 0) {
            OptimizationConfig(
                demonstrations = optimizableNodes.associate {
                    it.name to sampleLabeledDemonstrations(
                        it.demonstrations,
                        maxTotalDemos,
                        random
                    )
                }
            )
        } else {
            OptimizationConfig()
        }

        // Bootstrap - collect traces from executions
        val (bootstrappedTraces, notBootstrappedExamples) = bootstrap(
            promptExecutor = promptExecutor,
            agentConfig = agentConfig,
            strategy = strategy,
            toolRegistry = toolRegistry,
            nodes = optimizableNodes,
            trainset = trainset,
            baseConfig = baseConfig,
            metric = metric,
        )

        // Build joint config from bootstrapped and labeled demos
        val config = buildOptimizationConfig(
            optimizableNodes, bootstrappedTraces, notBootstrappedExamples, strategy
        )

        val totalBootstrapped = bootstrappedTraces.values.sumOf { it.size }

        return OptimizationResult(
            config = config,
            score = 0.0,
            iterations = trainset.size,
            metadata = mapOf(
                "optimizer" to "BootstrapFewShot",
                "maxBootstrappedDemos" to maxBootstrappedDemos,
                "maxLabeledDemos" to maxTotalDemos,
                "numNodes" to optimizableNodes.size,
                "totalBootstrapped" to totalBootstrapped,
            ),
        )
    }

    /**
     * Bootstrap phase: runs agent on training examples and collects traces from successful runs.
     *
     * @return Pair of (node name → bootstrapped demonstrations, not-bootstrapped training examples).
     */
    private suspend fun <TInput, TOutput> bootstrap(
        promptExecutor: PromptExecutor,
        agentConfig: AIAgentConfig,
        strategy: AIAgentGraphStrategy<TInput, TOutput>,
        toolRegistry: ToolRegistry,
        nodes: List<OptimizableNode<*, *>>,
        trainset: Dataset<TInput, TOutput>,
        baseConfig: OptimizationConfig,
        metric: Metric<TOutput>?,
    ): Pair<Map<String, MutableList<Demonstration<Any?, Any?>>>, List<Example<TInput, TOutput>>> {
        val nodeName2BootstrappedTraces = mutableMapOf<String, MutableList<Demonstration<Any?, Any?>>>()
        val bootstrappedIndices = mutableSetOf<Int>()
        var errorCount = 0

        for ((index, example) in trainset.withIndex()) {
            if (bootstrappedIndices.size >= maxBootstrappedDemos) break

            // Check error budget
            if (maxErrors != null && errorCount >= maxErrors) break

            logger.info { "Bootstrapping example ${index + 1}/${trainset.size} (${bootstrappedIndices.size}/$maxBootstrappedDemos successful, $errorCount errors)" }

            for (round in 0 until maxRounds) {
                val outcome = bootstrapOneExample(
                    promptExecutor = promptExecutor,
                    agentConfig = agentConfig,
                    strategy = strategy,
                    toolRegistry = toolRegistry,
                    nodes = nodes,
                    example = example,
                    baseConfig = baseConfig,
                    metric = metric,
                )

                when (outcome) {
                    is BootstrapOutcome.Success -> {
                        // Store traces for each node
                        for ((nodeName, demo) in outcome.traces) {
                            nodeName2BootstrappedTraces.getOrPut(nodeName) { mutableListOf() }.add(demo)
                        }
                        bootstrappedIndices.add(index)
                        logger.info { "  -> Success (${bootstrappedIndices.size}/$maxBootstrappedDemos)" }
                        break // Move on to the next example
                    }

                    is BootstrapOutcome.Failure.MetricNotPassed -> {
                        logger.info { "  -> Metric not passed (round ${round + 1}/$maxRounds)" }
                        continue // Try next round
                    }

                    is BootstrapOutcome.Failure.ExceptionRaised -> {
                        errorCount++
                        logger.warn { "  -> Error (${errorCount}${if (maxErrors != null) "/$maxErrors" else ""}): ${outcome.exception.message}" }
                        if (maxErrors != null && errorCount >= maxErrors) break
                        continue
                    }
                }
            }
        }
        logger.info { "Bootstrap complete: ${bootstrappedIndices.size} successful out of ${trainset.size} examples" }

        val notBootstrapped = trainset.filterIndexed { index, _ -> index !in bootstrappedIndices }
            .shuffled(random)

        return nodeName2BootstrappedTraces to notBootstrapped
    }

    /**
     * Bootstrap a single training example.
     *
     * Builds a fresh agent with a trace collection, runs it on the example,
     * and evaluates the result. Each call gets its own agent and trace storage,
     * making this safe for parallel execution.
     */
    private suspend fun <TInput, TOutput> bootstrapOneExample(
        promptExecutor: PromptExecutor,
        agentConfig: AIAgentConfig,
        strategy: AIAgentGraphStrategy<TInput, TOutput>,
        toolRegistry: ToolRegistry,
        nodes: List<OptimizableNode<*, *>>,
        example: Example<TInput, TOutput>,
        baseConfig: OptimizationConfig,
        metric: Metric<TOutput>?,
    ): BootstrapOutcome {
        // Build a fresh agent with its own trace collection
        val tracingAgent = GraphAIAgent(
            inputType = strategy.inputType,
            outputType = strategy.outputType,
            promptExecutor = promptExecutor,
            agentConfig = agentConfig,
            strategy = strategy,
            toolRegistry = toolRegistry,
            installFeatures = {
                collectTraces {
                    collectOnlyOptimizable = true
                }
            },
        )

        val pipeline = tracingAgent.createSession().pipeline()
            ?: error("Pipeline should be available after createSession()")
        val collectedTraces = pipeline.feature(CollectedTraces::class, TraceCollectionFeature)
            ?: error("TraceCollectionFeature should have been installed on tracing agent")

        // Labeled examples from the dataset and labeled demonstrations from the optimizableNode
        // constructor could overlap. In this case, when we are bootstrapping an example,
        // which is represented in the labeled demonstrations for a particular node,
        // we don't want the agent to see it, so we filter it out.
        val filteredDemos = baseConfig.demonstrations.mapValues { (_, demos) ->
            demos.filterNot { demo -> demo.input == example.input }
        }
        val filteredConfig = OptimizationConfig(
            instructions = baseConfig.instructions,
            demonstrations = filteredDemos,
        )

        // Run agent
        val output = try {
            withContext(filteredConfig) { tracingAgent.run(example.input) }
        } catch (e: Exception) {
            return BootstrapOutcome.Failure.ExceptionRaised(e)
        }

        // Evaluate metric
        val expected = example.label
        if (metric != null && expected != null) {
            val score = metric(expected, output)
            if (score < metricThreshold) {
                return BootstrapOutcome.Failure.MetricNotPassed
            }
        }

        // Collect traces: for each node, select one trace
        val traces = nodes.mapNotNull { node ->
            val nodeTraces = collectedTraces.getTracesForNode(node.name)
            if (nodeTraces.isEmpty()) return@mapNotNull null
            node.name to selectTrace(nodeTraces, random)
        }.toMap()

        return BootstrapOutcome.Success(traces)
    }

    /**
     * Builds the joint [OptimizationConfig] from bootstrapped traces and labeled fallback.
     *
     * For each optimizable node:
     * 1. Takes up to [maxBootstrappedDemos] bootstrapped traces
     * 2. Fills remaining slots (up to [maxTotalDemos]) with labeled demos:
     *    - For nodes whose types match the strategy's input/output types (e.g., single-node
     *      strategies or end-to-end nodes): uses training examples as labeled fallback,
     *      matching DSPy's original behavior.
     *    - For intermediate nodes with different types (e.g., a "vote" node taking VoteInput):
     *      uses the node's own [OptimizableNode.demonstrations] to avoid type mismatches
     *      that would cause ClassCastExceptions at runtime (due to type erasure hiding the
     *      mismatch at compile time).
     */
    private fun <TInput, TOutput> buildOptimizationConfig(
        optimizableNodes: List<OptimizableNode<*, *>>,
        bootstrappedTraces: Map<String, List<Demonstration<Any?, Any?>>>,
        notBootstrappedExamples: List<Example<TInput, TOutput>>,
        strategy: AIAgentGraphStrategy<TInput, TOutput>,
    ): OptimizationConfig {
        val jointDemonstrations = mutableMapOf<String, List<Demonstration<*, *>>>()

        for (node in optimizableNodes) {
            val bootstrapped = (bootstrappedTraces[node.name] ?: emptyList())
                .take(maxBootstrappedDemos)

            val remaining = (maxTotalDemos - bootstrapped.size).coerceAtLeast(0)

            val labeled = if (remaining > 0) {
                val nodeTypesMatchStrategy =
                    node.inputType == strategy.inputType && node.outputType == strategy.outputType
                if (nodeTypesMatchStrategy) {
                    // Node types match strategy — training examples are valid demos
                    val labeledExamples = notBootstrappedExamples.map { exampleToDemonstration(it) }
                    sampleLabeledDemonstrations(labeledExamples, remaining, random)
                } else {
                    // Intermediate node with different types — use node's own demonstrations
                    sampleLabeledDemonstrations(node.demonstrations, remaining, random)
                }
            } else {
                emptyList()
            }

            jointDemonstrations[node.name] = bootstrapped + labeled
        }

        return OptimizationConfig(demonstrations = jointDemonstrations)
    }

    private fun <TInput, TOutput> exampleToDemonstration(
        example: Example<TInput, TOutput>,
    ) = Demonstration(input = example.input, output = example.label, isBootstrapped = false)

}

/**
 * Selects a single trace from a list using random sampling with recency bias.
 *
 * When there are multiple traces:
 * - 50% chance: sample from the first N-1 traces (diversity)
 * - 50% chance: take the last trace (recency)
 *
 * @param traces Non-empty list of traces to select from.
 * @param random Random instance for all decisions.
 * @return A single selected trace.
 */
private fun selectTrace(
    traces: List<Demonstration<Any?, Any?>>,
    random: Random,
): Demonstration<Any?, Any?> {
    if (traces.size == 1) return traces.first()

    return if (random.nextBoolean()) {
        traces.subList(0, traces.size - 1).random(random)
    } else {
        traces.last()
    }
}
