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
import ai.koog.agents.core.optimization.optimizers.utils.findOptimizableModules
import ai.koog.agents.core.optimization.optimizers.utils.sampleLabeledDemonstrations
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Outcome of bootstrapping a single example.
 */
public sealed class BootstrapOutcome {
    /**
     * The teacher successfully produced output that passed the metric.
     *
     * @property traces Per-node demonstrations collected during execution.
     */
    public data class Success(val traces: Map<String, Demonstration<Any?, Any?>>) : BootstrapOutcome()

    /**
     * The bootstrap attempt failed.
     */
    public sealed class Failure : BootstrapOutcome() {
        /** The teacher produced output but it did not meet the metric threshold. */
        public data object MetricNotPassed : Failure()

        /** The teacher threw an exception during execution. */
        public data class ExceptionRaised(val exception: Exception) : Failure()
    }
}

/**
 * Generates demonstrations by running a "teacher" agent on training data and keeping
 * traces from successful executions.
 *
 * BootstrapFewShot is both a standalone optimizer and Step 1 of the full MIPRO v2 pipeline.
 * Unlike [LabeledFewShot], it generates new demonstrations by running an agent and collecting
 * per-node input/output traces via [TraceCollectionFeature].
 *
 * The algorithm:
 * 1. Pre-optimize the teacher with [LabeledFewShot] (if [maxLabeledDemos] > 0)
 * 2. For each training example, build a fresh teacher agent with trace collection,
 *    run it, and collect per-node traces from successful executions
 * 3. Build a student config combining bootstrapped traces + labeled fallback
 *
 * The optimizer takes agent components (executor, config, strategy) rather than a pre-built
 * agent, so it can construct its own internal teacher with the necessary tracing features.
 * Use [OptimizationResult.toAgent] to create a production agent with the optimization baked in.
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
 * @property maxLabeledDemos Maximum number of labeled demonstrations for teacher pre-optimization
 *  and fallback. Set to 0 to disable teacher pre-optimization.
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
     * Optimizes the strategy by bootstrapping demonstrations from teacher executions.
     *
     * For each training example, builds a fresh teacher agent with trace collection
     * enabled. The caller does not need to install [TraceCollectionFeature] themselves.
     *
     * @param TInput The strategy's input type.
     * @param TOutput The strategy's output type.
     * @param promptExecutor The executor for LLM calls.
     * @param agentConfig The agent configuration (prompt, model, max iterations).
     * @param strategy The strategy to optimize. Must contain [OptimizableNode]s.
     * @param trainset Training examples to bootstrap from.
     * @param toolRegistry Tools available to the agent. Defaults to empty.
     * @param nonTrainSet Dataset to be used for labeled few shot examples. If null, unused training examples become the nonTrainSet set.
     * @param metric Optional metric to evaluate bootstrap quality. If null, all bootstraps are accepted.
     * @param inputFromExample Maps an [Example] to the strategy's typed input.
     * @return The optimization result with bootstrapped + labeled demonstrations.
     */
    public suspend fun <TInput, TOutput> optimize(
        promptExecutor: PromptExecutor,
        agentConfig: AIAgentConfig,
        strategy: AIAgentGraphStrategy<TInput, TOutput>,
        trainset: Dataset,
        toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
        nonTrainSet: Dataset? = null,
        metric: Metric<TOutput>? = null,
        inputFromExample: (Example) -> TInput,
    ): OptimizationResult {
        require(trainset.isNotEmpty()) { "trainset is required for BootstrapFewShot" }

        val modules = strategy.findOptimizableModules()
        if (modules.isEmpty()) {
            return OptimizationResult(
                config = OptimizationConfig(),
                score = 0.0,
                iterations = 0,
            )
        }

        // Step 1: Teacher pre-optimization with LabeledFewShot
        val teacherConfig = if (maxLabeledDemos > 0) {
            OptimizationConfig(
                demonstrations = modules.associate { it.name to sampleLabeledDemonstrations(
                    it.demonstrations,
                    maxLabeledDemos,
                    random
                )
                }
            )
        } else {
            OptimizationConfig()
        }

        // Step 2: Bootstrap - collect traces from teacher executions
        val (name2traces, bootstrapNonTrainset) = bootstrap(
            promptExecutor = promptExecutor,
            agentConfig = agentConfig,
            strategy = strategy,
            toolRegistry = toolRegistry,
            modules = modules,
            trainset = trainset,
            teacherConfig = teacherConfig,
            metric = metric,
            inputFromExample = inputFromExample,
        )

        // Step 3: Train - build student config from bootstrapped + labeled demos
        val labeledExamples = nonTrainSet ?: bootstrapNonTrainset
        val config = train(modules, name2traces, labeledExamples)

        val totalBootstrapped = name2traces.values.sumOf { it.size }

        return OptimizationResult(
            config = config,
            score = 0.0,
            iterations = trainset.size,
            metadata = mapOf(
                "optimizer" to "BootstrapFewShot",
                "maxBootstrappedDemos" to maxBootstrappedDemos,
                "maxLabeledDemos" to maxLabeledDemos,
                "numModules" to modules.size,
                "totalBootstrapped" to totalBootstrapped,
            ),
        )
    }

    /**
     * Bootstrap phase: runs teacher on training examples and collects traces from successful runs.
     *
     * @return Pair of (map of nodes to bootstrapped demonstrations, dataset of training examples that were not bootstrapped successfully)
     */
    private suspend fun <TInput, TOutput> bootstrap(
        promptExecutor: PromptExecutor,
        agentConfig: AIAgentConfig,
        strategy: AIAgentGraphStrategy<TInput, TOutput>,
        toolRegistry: ToolRegistry,
        modules: List<OptimizableNode<*, *>>,
        trainset: Dataset,
        teacherConfig: OptimizationConfig,
        metric: Metric<TOutput>?,
        inputFromExample: (Example) -> TInput,
    ): Pair<Map<String, MutableList<Demonstration<Any?, Any?>>>, Dataset> {
        val name2traces = mutableMapOf<String, MutableList<Demonstration<Any?, Any?>>>()
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
                    modules = modules,
                    example = example,
                    teacherConfig = teacherConfig,
                    metric = metric,
                    inputFromExample = inputFromExample,
                )

                when (outcome) {
                    is BootstrapOutcome.Success -> {
                        // Store traces for each module
                        for ((nodeName, demo) in outcome.traces) {
                            name2traces.getOrPut(nodeName) { mutableListOf() }.add(demo)
                        }
                        bootstrappedIndices.add(index)
                        break // Move to next example
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

        // Training examples that were NOT bootstrapped remain ordinary examples, i.e. labeled few shot examples
        // Our optimizer shuffles them
        val notBootstrapped = trainset.filterIndexed { index, _ -> index !in bootstrappedIndices }
            .shuffled(random)

        return name2traces to notBootstrapped
    }

    /**
     * Bootstrap a single training example.
     *
     * Builds a fresh teacher agent with trace collection, runs it on the example,
     * and evaluates the result. Each call gets its own agent and trace storage,
     * making this safe for parallel execution.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <TInput, TOutput> bootstrapOneExample(
        promptExecutor: PromptExecutor,
        agentConfig: AIAgentConfig,
        strategy: AIAgentGraphStrategy<TInput, TOutput>,
        toolRegistry: ToolRegistry,
        modules: List<OptimizableNode<*, *>>,
        example: Example,
        teacherConfig: OptimizationConfig,
        metric: Metric<TOutput>?,
        inputFromExample: (Example) -> TInput,
    ): BootstrapOutcome {
        // Build a fresh teacher agent with its own trace collection
        val teacherAgent = GraphAIAgent<TInput, TOutput>(
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

        val collectedTraces = teacherAgent.createSession().pipeline()!!
            .feature(CollectedTraces::class, TraceCollectionFeature)
            ?: error("TraceCollectionFeature should have been installed on teacher agent")

        // Filter teacher demos: remove demos whose input and output both come from the
        // current example's data, to prevent the teacher from parroting the ground truth.
        val exampleValues = example.data.values.toSet()
        val filteredDemos = teacherConfig.demonstrations.mapValues { (_, demos) ->
            demos.filterNot { it.input in exampleValues && it.output in exampleValues }
        }
        val filteredConfig = OptimizationConfig(
            instructions = teacherConfig.instructions,
            demonstrations = filteredDemos,
        )

        // Run teacher
        val output: TOutput
        try {
            val input = inputFromExample(example)
            output = withContext(filteredConfig) {
                teacherAgent.run(input)
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

        // Collect traces: for each module, select one trace
        val traces = modules.mapNotNull { module ->
            val nodeTraces = collectedTraces.getTracesForNode(module.name)
            if (nodeTraces.isEmpty()) return@mapNotNull null
            module.name to selectTrace(nodeTraces, random)
        }.toMap()

        return BootstrapOutcome.Success(traces)
    }

    /**
     * Builds the student [OptimizationConfig] from bootstrapped traces and labeled fallback.
     *
     * For each module:
     * 1. Takes up to [maxBootstrappedDemos] bootstrapped traces
     * 2. Fills remaining slots (up to [maxLabeledDemos]) with labeled examples from valset
     */
    private fun train(
        modules: List<OptimizableNode<*, *>>,
        name2traces: Map<String, List<Demonstration<Any?, Any?>>>,
        labeledExamples: Dataset,
    ): OptimizationConfig {
        val demonstrations = mutableMapOf<String, List<Demonstration<*, *>>>()

        for (module in modules) {
            val bootstrapped = (name2traces[module.name] ?: emptyList())
                .take(maxBootstrappedDemos)

            // Calculate remaining labeled demo slots
            val remaining = (maxLabeledDemos - bootstrapped.size).coerceAtLeast(0)
                .coerceAtMost(labeledExamples.size)

            val labeled = if (remaining > 0) {
                // TODO: Double check again
                sampleLabeledDemonstrations(module.demonstrations, remaining, random)
            } else {
                emptyList()
            }

            demonstrations[module.name] = bootstrapped + labeled
        }

        return OptimizationConfig(demonstrations = demonstrations)
    }

    public companion object {
        /**
         * Selects a single trace from a list using deterministic 50/50 sampling.
         *
         * When there are multiple traces:
         * - 50% chance: sample from the first N-1 traces
         * - 50% chance: take the last trace
         *
         * This provides diversity while still favoring recent traces.
         *
         * @param traces Non-empty list of traces to select from.
         * @param random Random instance for selection.
         * @return A single selected trace.
         */
        internal fun selectTrace(
            traces: List<Demonstration<Any?, Any?>>,
            random: Random,
        ): Demonstration<Any?, Any?> {
            if (traces.size == 1) return traces.first()

            // Deterministic seed from trace content
            val seededRandom = Random(traces.hashCode().toLong())
            return if (seededRandom.nextBoolean()) {
                // Sample from first N-1
                traces.subList(0, traces.size - 1).random(random)
            } else {
                // Take last
                traces.last()
            }
        }
    }
}
