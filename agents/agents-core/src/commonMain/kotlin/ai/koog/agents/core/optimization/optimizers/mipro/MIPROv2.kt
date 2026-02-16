package ai.koog.agents.core.optimization.optimizers.mipro

import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.optimization.core.Dataset
import ai.koog.agents.core.optimization.core.Demonstration
import ai.koog.agents.core.optimization.core.Metric
import ai.koog.agents.core.optimization.core.OptimizationConfig
import ai.koog.agents.core.optimization.core.OptimizationResult
import ai.koog.agents.core.optimization.optimizers.utils.findOptimizableNodes
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.log2
import kotlin.math.max
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/**
 * Auto run mode presets for [MIPROv2].
 *
 * Each mode defines the number of candidate sets to generate and the maximum
 * validation set size. Higher modes explore more configurations but take longer.
 *
 * @property numCandidates Number of instruction/demo candidate sets to generate per node.
 * @property valSize Maximum number of validation examples to use.
 */
public enum class AutoRunMode(public val numCandidates: Int, public val valSize: Int) {
    LIGHT(numCandidates = 6, valSize = 100),
    MEDIUM(numCandidates = 12, valSize = 300),
    HEAVY(numCandidates = 18, valSize = 1000),
}

/**
 * Configuration for the [MIPROv2] optimizer.
 *
 * Use [auto] for preset configurations, or set [numCandidates] and [numTrials]
 * manually when [auto] is null.
 *
 * @property promptModel LLM model for meta-prompting (instruction proposal in Step 2).
 * @property maxBootstrappedDemos Maximum bootstrapped demonstrations per node.
 * @property maxLabeledDemos Maximum labeled demonstrations per node.
 * @property auto Auto run mode preset. Set to null for manual configuration.
 * @property numCandidates Number of candidate sets to generate. Required when [auto] is null.
 * @property numTrials Number of random search trials. Required when [auto] is null.
 * @property seed Random seed for reproducibility.
 * @property metricThreshold Threshold for accepting bootstrapped examples.
 * @property maxErrors Maximum errors before stopping a bootstrap run.
 * @property minibatch Whether to use minibatch evaluation during search.
 * @property minibatchSize Size of minibatch for evaluation.
 * @property minibatchFullEvalSteps How often to perform full evaluation during minibatch optimization.
 * @property proposerConfig Configuration for the [InstructionProposer].
 * @property parallelism Maximum concurrency for demo generation, instruction proposal, and
 *  evaluation during grid search. Set to 1 (default) for sequential execution; higher values
 *  require a [createStrategy][MIPROv2.optimize] factory that produces independent strategy instances.
 */
public data class MIPROv2Config(
    val promptModel: LLModel,
    val maxBootstrappedDemos: Int = 4,
    val maxLabeledDemos: Int = 4,
    val auto: AutoRunMode? = AutoRunMode.LIGHT,
    val numCandidates: Int? = null,
    val numTrials: Int? = null,
    val seed: Long = 42L,
    val metricThreshold: Double? = null,
    val maxErrors: Int? = null,
    val minibatch: Boolean = true,
    val minibatchSize: Int = 35,
    val minibatchFullEvalSteps: Int = 5,
    val proposerConfig: InstructionProposerConfig = InstructionProposerConfig(),
    val parallelism: Int = 1,
)

/**
 * MIPRO v2 (Multi-prompt Instruction Proposal Optimizer v2) optimizer.
 *
 * Implements the full MIPRO v2 pipeline:
 *
 * 1. **Step 1 — Demo generation**: Creates diverse candidate demo sets via [generateDemoSets],
 *    using [BootstrapFewShot][ai.koog.agents.core.optimization.optimizers.BootstrapFewShot]
 *    with different shuffling/sampling strategies.
 *
 * 2. **Step 2 — Instruction proposal**: Uses [InstructionProposer] to generate diverse
 *    instruction candidates for each optimizable node, grounded in dataset summaries,
 *    program structure, and demo examples.
 *
 * 3. **Step 3 — Random grid search**: Evaluates random (instruction, demo set) combinations
 *    on a validation set and returns the best-performing configuration.
 *
 * The optimizer follows the same component-based API as
 * [BootstrapFewShot][ai.koog.agents.core.optimization.optimizers.BootstrapFewShot]:
 * it takes agent components (executor, config, strategy) rather than a pre-built agent.
 *
 * Example usage:
 * ```kotlin
 * val config = MIPROv2Config(
 *     promptModel = OpenAIModels.Chat.GPT4o,
 *     auto = AutoRunMode.LIGHT,
 * )
 * val mipro = MIPROv2(config)
 * val result = mipro.optimize(
 *     promptExecutor = executor,
 *     agentConfig = agentConfig,
 *     strategy = myStrategy,
 *     trainset = trainingExamples,
 *     metric = { expected, actual -> if (expected == actual) 1.0 else 0.0 },
 * )
 *
 * val optimizedAgent = result.toAgent(originalAgent)
 * ```
 *
 * @property config The MIPRO v2 configuration.
 */
public class MIPROv2(private val config: MIPROv2Config) {

    init {
        if (config.auto == null && config.numCandidates == null) {
            throw IllegalArgumentException("numCandidates must be provided when auto is null")
        }
        if (config.auto != null && config.numCandidates != null) {
            throw IllegalArgumentException(
                "numCandidates cannot be set when auto is not null (it would be overridden by auto settings)"
            )
        }
    }

    /**
     * Runs the full MIPRO v2 optimization pipeline.
     *
     * @param TInput The strategy's input type.
     * @param TOutput The strategy's output type.
     * @param promptExecutor The executor for LLM calls (both task execution and meta-prompting).
     * @param agentConfig The agent configuration.
     * @param createStrategy Factory that creates fresh strategy instances. Called once upfront for
     *  inspection (node discovery, description generation) and once per concurrent evaluation when
     *  [MIPROv2Config.parallelism] > 1. For stateless strategies, `{ myStrategy }` is fine;
     *  for strategies with mutable closure state, return a new instance each time.
     * @param trainset Training examples.
     * @param metric Metric to evaluate candidate configurations.
     * @param valset Optional validation set. If null, split from [trainset].
     * @param toolRegistry Tools available to the agent.
     * @param describeInput Renders an input value as a human-readable string for dataset
     *  summarization. When null (default), uses JSON serialization with toString fallback.
     * @return The best [OptimizationResult] found during search.
     */
    public suspend fun <TInput, TOutput> optimize(
        promptExecutor: PromptExecutor,
        agentConfig: AIAgentConfig,
        createStrategy: () -> AIAgentGraphStrategy<TInput, TOutput>,
        trainset: Dataset<TInput, TOutput>,
        metric: Metric<TOutput>,
        valset: Dataset<TInput, TOutput>? = null,
        toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
        describeInput: ((TInput) -> String)? = null,
    ): OptimizationResult {
        val random = Random(config.seed)
        // Create one strategy upfront for inspection (node discovery, descriptions, etc.)
        val strategy = createStrategy()

        val zeroShotMode = config.maxBootstrappedDemos == 0 && config.maxLabeledDemos == 0

        // Validate numTrials for manual mode
        if (config.auto == null && config.numTrials == null) {
            val suggested = estimateNumTrials(strategy, zeroShotMode, config.numCandidates!!)
            throw IllegalArgumentException(
                "numTrials must be provided when auto is null. " +
                        "Given numCandidates=${config.numCandidates}, we recommend numTrials ~$suggested"
            )
        }

        // Validate and split datasets
        val (effectiveTrainset, effectiveValSet) = validateAndSplitDatasets(trainset, valset)

        // Compute hyperparameters
        val hyperParams = computeHyperparameters(strategy, effectiveValSet, zeroShotMode, random)

        // Step 1: Generate demo candidate sets
        logger.info { "=== MIPROv2 Step 1: Generating demo candidate sets ===" }
        val demoCandidates = generateDemoSets(
            promptExecutor = promptExecutor,
            agentConfig = agentConfig,
            strategy = strategy,
            trainset = effectiveTrainset,
            numCandidateSets = hyperParams.numFewShotCandidates,
            maxBootstrappedDemos = if (zeroShotMode) BOOTSTRAPPED_FEWSHOT_EXAMPLES_IN_CONTEXT else config.maxBootstrappedDemos,
            maxLabeledDemos = if (zeroShotMode) LABELED_FEWSHOT_EXAMPLES_IN_CONTEXT else config.maxLabeledDemos,
            metric = metric,
            metricThreshold = config.metricThreshold,
            maxErrors = config.maxErrors,
            toolRegistry = toolRegistry,
            random = random,
            parallelism = config.parallelism,
        )

        // Step 2: Propose instruction candidates
        logger.info { "=== MIPROv2 Step 2: Proposing instruction candidates ===" }
        val proposer = InstructionProposer.create(
            strategy = strategy,
            trainset = effectiveTrainset,
            promptExecutor = promptExecutor,
            llModel = config.promptModel,
            config = config.proposerConfig,
            random = random,
            describeInput = describeInput ?: { serializeOrToString(it, strategy.inputType) },
        )
        val instructionCandidates = proposer.proposeInstructionsForProgram(
            demoCandidates = demoCandidates,
            numCandidates = hyperParams.numInstructCandidates,
            parallelism = config.parallelism,
        )

        // Discard demos if zero-shot mode
        val finalDemoCandidates = if (zeroShotMode) null else demoCandidates

        // Step 3: Random grid search
        logger.info { "=== MIPROv2 Step 3: Random grid search (${hyperParams.numTrials} trials) ===" }
        return randomGridSearch(
            promptExecutor = promptExecutor,
            agentConfig = agentConfig,
            createStrategy = createStrategy,
            toolRegistry = toolRegistry,
            instructionCandidates = instructionCandidates,
            demoCandidates = finalDemoCandidates,
            valSet = hyperParams.valSet,
            numTrials = hyperParams.numTrials,
            minibatch = hyperParams.minibatch,
            metric = metric,
            random = random,
        )
    }

    /**
     * Convenience overload that accepts a strategy instance directly.
     *
     * Equivalent to `optimize(createStrategy = { strategy }, ...)`. Use the [createStrategy]
     * overload instead when [MIPROv2Config.parallelism] > 1 and the strategy holds mutable
     * closure state that must be isolated per evaluation.
     */
    public suspend fun <TInput, TOutput> optimize(
        promptExecutor: PromptExecutor,
        agentConfig: AIAgentConfig,
        strategy: AIAgentGraphStrategy<TInput, TOutput>,
        trainset: Dataset<TInput, TOutput>,
        metric: Metric<TOutput>,
        valset: Dataset<TInput, TOutput>? = null,
        toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
        describeInput: ((TInput) -> String)? = null,
    ): OptimizationResult = optimize(
        promptExecutor = promptExecutor,
        agentConfig = agentConfig,
        createStrategy = { strategy },
        trainset = trainset,
        metric = metric,
        valset = valset,
        toolRegistry = toolRegistry,
        describeInput = describeInput,
    )

    /**
     * Step 3: Random grid search over instruction/demo combinations.
     *
     * Samples random configurations and evaluates them on the validation set (or minibatch).
     * Tracks the best-performing configuration across all trials.
     */
    private suspend fun <TInput, TOutput> randomGridSearch(
        promptExecutor: PromptExecutor,
        agentConfig: AIAgentConfig,
        createStrategy: () -> AIAgentGraphStrategy<TInput, TOutput>,
        toolRegistry: ToolRegistry,
        instructionCandidates: Map<String, List<String>>,
        demoCandidates: Map<String, List<List<Demonstration<*, *>>>>?,
        valSet: Dataset<TInput, TOutput>,
        numTrials: Int,
        minibatch: Boolean,
        metric: Metric<TOutput>,
        random: Random,
    ): OptimizationResult {
        val inspectionStrategy = createStrategy()
        val moduleNames = inspectionStrategy.findOptimizableNodes().map { it.name }

        // Evaluate baseline (empty config)
        logger.info { "Evaluating baseline on ${valSet.size} examples..." }
        val baselineConfig = OptimizationConfig()
        val baselineScore = evaluateConfig(
            config = baselineConfig,
            promptExecutor = promptExecutor,
            agentConfig = agentConfig,
            createStrategy = createStrategy,
            toolRegistry = toolRegistry,
            dataset = valSet,
            metric = metric,
        )

        var bestScore = baselineScore
        var bestConfig = baselineConfig
        logger.info { "Baseline score: ${fmt(baselineScore)}" }

        for (trial in 1..numTrials) {
            // Sample random instruction index per node
            val instructions = moduleNames.associate { name ->
                val candidates = instructionCandidates[name]
                if (candidates.isNullOrEmpty()) {
                    name to (inspectionStrategy.findOptimizableNodes().first { it.name == name }.instruction)
                } else {
                    name to candidates[random.nextInt(candidates.size)]
                }
            }

            // Sample random demo set index per node
            val demonstrations: Map<String, List<Demonstration<*, *>>> = if (demoCandidates != null) {
                moduleNames.associate { name ->
                    val candidates = demoCandidates[name]
                    if (candidates.isNullOrEmpty()) {
                        name to emptyList()
                    } else {
                        name to candidates[random.nextInt(candidates.size)]
                    }
                }
            } else {
                emptyMap()
            }

            val trialConfig = OptimizationConfig(
                instructions = instructions,
                demonstrations = demonstrations,
            )

            // Evaluate on valSet (or minibatch)
            val evalSet = if (minibatch) {
                createMinibatch(valSet, config.minibatchSize, random)
            } else {
                valSet
            }

            val score = evaluateConfig(
                config = trialConfig,
                promptExecutor = promptExecutor,
                agentConfig = agentConfig,
                createStrategy = createStrategy,
                toolRegistry = toolRegistry,
                dataset = evalSet,
                metric = metric,
            )

            if (score > bestScore) {
                logger.info { "Trial $trial/$numTrials: new best ${fmt(score)} (was ${fmt(bestScore)})" }
                bestScore = score
                bestConfig = trialConfig
            } else {
                logger.info { "Trial $trial/$numTrials: ${fmt(score)} (best=${fmt(bestScore)})" }
            }

            // Periodic full evaluation when using minibatch
            if (minibatch && trial % config.minibatchFullEvalSteps == 0) {
                evaluateConfig(
                    config = bestConfig,
                    promptExecutor = promptExecutor,
                    agentConfig = agentConfig,
                    createStrategy = createStrategy,
                    toolRegistry = toolRegistry,
                    dataset = valSet,
                    metric = metric,
                )
            }
        }

        // Final full evaluation of the best config
        val finalScore = evaluateConfig(
            config = bestConfig,
            promptExecutor = promptExecutor,
            agentConfig = agentConfig,
            createStrategy = createStrategy,
            toolRegistry = toolRegistry,
            dataset = valSet,
            metric = metric,
        )

        return OptimizationResult(
            config = bestConfig,
            score = finalScore,
            iterations = numTrials,
            metadata = mapOf(
                "optimizer" to "MIPROv2",
                "baselineScore" to baselineScore,
                "numTrials" to numTrials,
                "numModules" to moduleNames.size,
            ),
        )
    }

    /**
     * Evaluates an [OptimizationConfig] on a dataset by running the strategy for each example
     * and scoring with the metric. Returns the average score across all examples.
     *
     * Creates a fresh strategy and agent per evaluation via [createStrategy] to isolate mutable
     * closure state. Concurrency is bounded by [MIPROv2Config.parallelism] via a [Semaphore];
     * when parallelism is 1, execution is effectively sequential.
     *
     * Exceptions during individual example evaluation are counted as score 0.
     */
    private suspend fun <TInput, TOutput> evaluateConfig(
        config: OptimizationConfig,
        promptExecutor: PromptExecutor,
        agentConfig: AIAgentConfig,
        createStrategy: () -> AIAgentGraphStrategy<TInput, TOutput>,
        toolRegistry: ToolRegistry,
        dataset: Dataset<TInput, TOutput>,
        metric: Metric<TOutput>,
    ): Double {
        if (dataset.isEmpty()) return 0.0

        val semaphore = Semaphore(maxOf(1, this.config.parallelism))
        val completedMutex = Mutex()
        var completed = 0

        val scores = coroutineScope {
            dataset.map { example ->
                async {
                    semaphore.withPermit {
                        val score = try {
                            val strategy = createStrategy()
                            val agent = GraphAIAgent(
                                inputType = strategy.inputType,
                                outputType = strategy.outputType,
                                promptExecutor = promptExecutor,
                                agentConfig = agentConfig,
                                strategy = strategy,
                                toolRegistry = toolRegistry,
                            )
                            val output = withContext(config) {
                                agent.run(example.input)
                            }
                            if (example.hasLabel) {
                                metric(example.label!!, output)
                            } else {
                                0.0
                            }
                        } catch (_: Exception) {
                            0.0
                        }

                        val current = completedMutex.withLock { ++completed }
                        if (current % 10 == 0 || current == dataset.size) {
                            logger.info { "  Eval $current/${dataset.size} completed" }
                        }

                        score
                    }
                }
            }.awaitAll()
        }

        return scores.sum() / dataset.size
    }

    /**
     * Validates and optionally splits datasets.
     *
     * If [valSet] is null, splits [trainset] into train/val with 80% for validation
     * (matching DSPy's hardcoded ratio).
     */
    private fun <TInput, TOutput> validateAndSplitDatasets(
        trainset: Dataset<TInput, TOutput>,
        valSet: Dataset<TInput, TOutput>?,
    ): Pair<Dataset<TInput, TOutput>, Dataset<TInput, TOutput>> {
        require(trainset.isNotEmpty()) { "Trainset cannot be empty" }

        return if (valSet == null) {
            require(trainset.size >= 2) {
                "Trainset must have at least 2 examples if no valSet specified"
            }
            val valSize = minOf(1000, maxOf(1, (trainset.size * 0.80).toInt()))
            val cutoff = trainset.size - valSize
            trainset.take(cutoff) to trainset.drop(cutoff)
        } else {
            require(valSet.isNotEmpty()) { "Validation set must have at least 1 example" }
            trainset to valSet
        }
    }

    /**
     * Computes hyperparameters based on auto mode or manual settings.
     */
    private fun <TInput, TOutput> computeHyperparameters(
        strategy: AIAgentGraphStrategy<TInput, TOutput>,
        valSet: Dataset<TInput, TOutput>,
        zeroShotMode: Boolean,
        random: Random,
    ): HyperparameterSet<TInput, TOutput> {
        if (config.auto == null) {
            val numCandidates = config.numCandidates!!
            return HyperparameterSet(
                numTrials = config.numTrials!!,
                valSet = valSet,
                minibatch = config.minibatch,
                numInstructCandidates = numCandidates,
                numFewShotCandidates = numCandidates,
            )
        }

        val autoSettings = config.auto
        val effectiveValSet = createMinibatch(valSet, autoSettings.valSize, random)
        val effectiveMinibatch = effectiveValSet.size > MIN_MINIBATCH_SIZE

        // Allocate half of candidates to instructions when not zero-shot
        val numInstructCandidates = if (zeroShotMode) {
            autoSettings.numCandidates
        } else {
            (autoSettings.numCandidates * 0.5).toInt()
        }
        val numFewShotCandidates = autoSettings.numCandidates

        val numTrials = estimateNumTrials(strategy, zeroShotMode, autoSettings.numCandidates)

        return HyperparameterSet(
            numTrials = numTrials,
            valSet = effectiveValSet,
            minibatch = effectiveMinibatch,
            numInstructCandidates = numInstructCandidates,
            numFewShotCandidates = numFewShotCandidates,
        )
    }

    /**
     * Estimates a good number of trials based on the search space.
     *
     * Formula: max(2 * M * log2(N), 1.5 * N)
     * where M = numModules * (2 if not zero-shot, 1 otherwise) and N = numCandidates.
     */
    private fun <TInput, TOutput> estimateNumTrials(
        strategy: AIAgentGraphStrategy<TInput, TOutput>,
        zeroShotMode: Boolean,
        numCandidates: Int,
    ): Int {
        var numVars = strategy.findOptimizableNodes().size
        if (!zeroShotMode) {
            numVars *= 2
        }
        return max(
            (2 * numVars * log2(numCandidates.toDouble())).toInt(),
            (1.5 * numCandidates).toInt(),
        )
    }

    private fun <TInput, TOutput> createMinibatch(dataset: Dataset<TInput, TOutput>, batchSize: Int, random: Random): Dataset<TInput, TOutput> {
        return if (batchSize >= dataset.size) {
            dataset
        } else {
            dataset.shuffled(random).take(batchSize)
        }
    }

    private data class HyperparameterSet<TInput, TOutput>(
        val numTrials: Int,
        val valSet: Dataset<TInput, TOutput>,
        val minibatch: Boolean,
        val numInstructCandidates: Int,
        val numFewShotCandidates: Int,
    )

    private companion object {
        const val MIN_MINIBATCH_SIZE = 50
        const val BOOTSTRAPPED_FEWSHOT_EXAMPLES_IN_CONTEXT = 3
        const val LABELED_FEWSHOT_EXAMPLES_IN_CONTEXT = 0

        /** Format a Double to 3 decimal places (commonMain-compatible). */
        fun fmt(d: Double): String {
            val whole = d.toLong()
            val frac = ((d - whole) * 1000 + 0.5).toLong()
            return "$whole.${frac.toString().padStart(3, '0')}"
        }
    }
}
