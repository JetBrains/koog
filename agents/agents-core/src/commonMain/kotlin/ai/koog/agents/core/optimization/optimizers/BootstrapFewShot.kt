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
import kotlinx.coroutines.withContext
import kotlin.random.Random

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
 *     inputFromExample = { example -> example.data["question"] as String },
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
 * @property maxLabeledDemos Maximum number of labeled demonstrations used during bootstrapping
 *  and as fallback. Set to 0 to disable.
 * @property maxRounds Maximum retry rounds per training example.
 * @property maxErrors Maximum total exceptions before stopping. Null means unlimited.
 * @property metricThreshold Minimum metric score to accept a bootstrap.
 * @property random Random instance for reproducible sampling.
 */
public class BootstrapFewShot(
    public val maxBootstrappedDemos: Int = 4,
    public val maxLabeledDemos: Int = 16,
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
     * @param inputFromExample Maps an [Example] to the strategy's typed input.
     * @return The optimization result with bootstrapped and labeled demonstrations.
     */
    public suspend fun <TInput, TOutput> optimize(
        promptExecutor: PromptExecutor,
        agentConfig: AIAgentConfig,
        strategy: AIAgentGraphStrategy<TInput, TOutput>,
        trainset: Dataset,
        toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
        metric: Metric<TOutput>? = null,
        inputFromExample: (Example) -> TInput,
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
        val baseConfig = if (maxLabeledDemos > 0) {
            OptimizationConfig(
                demonstrations = optimizableNodes.associate {
                    it.name to sampleLabeledDemonstrations(
                        it.demonstrations,
                        maxLabeledDemos,
                        random
                    )
                }
            )
        } else {
            OptimizationConfig()
        }

        // Bootstrap - collect traces from executions
        val (bootstrappedTracesDict, notBootstrapped) = bootstrap(
            promptExecutor = promptExecutor,
            agentConfig = agentConfig,
            strategy = strategy,
            toolRegistry = toolRegistry,
            nodes = optimizableNodes,
            trainset = trainset,
            baseConfig = baseConfig,
            metric = metric,
            inputFromExample = inputFromExample,
        )

        // Build joint config from bootstrapped and labeled demos
        val config = buildOptimizationConfig(optimizableNodes, bootstrappedTracesDict)

        val totalBootstrapped = bootstrappedTracesDict.values.sumOf { it.size }

        return OptimizationResult(
            config = config,
            score = 0.0,
            iterations = trainset.size,
            metadata = mapOf(
                "optimizer" to "BootstrapFewShot",
                "maxBootstrappedDemos" to maxBootstrappedDemos,
                "maxLabeledDemos" to maxLabeledDemos,
                "numNodes" to optimizableNodes.size,
                "totalBootstrapped" to totalBootstrapped,
            ),
        )
    }

    /**
     * Bootstrap phase: runs agent on training examples and collects traces from successful runs.
     *
     * @return Pair of (maps of nodes to bootstrapped demonstrations, dataset of training examples that were not bootstrapped successfully)
     */
    private suspend fun <TInput, TOutput> bootstrap(
        promptExecutor: PromptExecutor,
        agentConfig: AIAgentConfig,
        strategy: AIAgentGraphStrategy<TInput, TOutput>,
        toolRegistry: ToolRegistry,
        nodes: List<OptimizableNode<*, *>>,
        trainset: Dataset,
        baseConfig: OptimizationConfig,
        metric: Metric<TOutput>?,
        inputFromExample: (Example) -> TInput,
    ): Pair<Map<String, MutableList<Demonstration<Any?, Any?>>>, Dataset> {
        val nodeName2BootstrappedTraces = mutableMapOf<String, MutableList<Demonstration<Any?, Any?>>>()
        val bootstrappedIndices = mutableSetOf<Int>()
        var errorCount = 0

        for ((index, example) in trainset.withIndex()) {
            // Check if we have enough bootstrapped demos
            if (bootstrappedIndices.size >= maxBootstrappedDemos) break

            // Check error budget
            if (maxErrors != null && errorCount >= maxErrors) break

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
                    inputFromExample = inputFromExample,
                )

                when (outcome) {
                    is BootstrapOutcome.Success -> {
                        // Store traces for each node
                        for ((nodeName, demo) in outcome.traces) {
                            nodeName2BootstrappedTraces.getOrPut(nodeName) { mutableListOf() }.add(demo)
                        }
                        bootstrappedIndices.add(index)
                        break // Move to the next example
                    }

                    is BootstrapOutcome.Failure.MetricNotPassed -> {
                        continue // Try next round
                    }

                    is BootstrapOutcome.Failure.ExceptionRaised -> {
                        errorCount++
                        if (maxErrors != null && errorCount >= maxErrors) break
                        continue
                    }
                }
            }
        }

        // Training examples that were NOT bootstrapped remain ordinary examples, i.e., labeled few shot examples
        // Our optimizer shuffles them
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
    @Suppress("UNCHECKED_CAST")
    private suspend fun <TInput, TOutput> bootstrapOneExample(
        promptExecutor: PromptExecutor,
        agentConfig: AIAgentConfig,
        strategy: AIAgentGraphStrategy<TInput, TOutput>,
        toolRegistry: ToolRegistry,
        nodes: List<OptimizableNode<*, *>>,
        example: Example,
        baseConfig: OptimizationConfig,
        metric: Metric<TOutput>?,
        inputFromExample: (Example) -> TInput,
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

        // Filter demos: remove demos whose input and output both come from the
        // current example's data to prevent parroting the ground truth.
        val exampleValues = example.data.values.toSet()
        val filteredDemos = baseConfig.demonstrations.mapValues { (_, demos) ->
            demos.filterNot { it.input in exampleValues && it.output in exampleValues }
        }
        val filteredConfig = OptimizationConfig(
            instructions = baseConfig.instructions,
            demonstrations = filteredDemos,
        )

        // Run agent
        val output: TOutput
        try {
            val input = inputFromExample(example)
            output = withContext(filteredConfig) {
                tracingAgent.run(input)
            }
        } catch (e: Exception) {
            return BootstrapOutcome.Failure.ExceptionRaised(e)
        }

        // Evaluate metric
        if (metric != null && example.hasLabel) {
            val expected = example.label as TOutput
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
     * 2. Fills remaining slots (up to [maxLabeledDemos]) with labeled demos from the node's demonstrations
     */
    private fun buildOptimizationConfig(
        optimizableNodes: List<OptimizableNode<*, *>>,
        bootstrappedTraces: Map<String, List<Demonstration<Any?, Any?>>>,
    ): OptimizationConfig {
        val jointDemonstrations = mutableMapOf<String, List<Demonstration<*, *>>>()

        for (node in optimizableNodes) {
            val bootstrapped = (bootstrappedTraces[node.name] ?: emptyList())
                .take(maxBootstrappedDemos)

            val remaining = (maxLabeledDemos - bootstrapped.size).coerceAtLeast(0)

            val labeled = if (remaining > 0) {
                sampleLabeledDemonstrations(node.demonstrations, remaining, random)
            } else {
                emptyList()
            }

            jointDemonstrations[node.name] = bootstrapped + labeled
        }

        return OptimizationConfig(demonstrations = jointDemonstrations)
    }
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
