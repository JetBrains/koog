package ai.koog.agents.core.optimization.optimizers.mipro

import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.optimization.core.Dataset
import ai.koog.agents.core.optimization.core.Demonstration
import ai.koog.agents.core.optimization.core.Example
import ai.koog.agents.core.optimization.core.Metric
import ai.koog.agents.core.optimization.core.OptimizationConfig
import ai.koog.agents.core.optimization.core.OptimizationResult
import ai.koog.agents.core.optimization.optimizers.utils.findOptimizableModules
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.withContext
import kotlin.math.log2
import kotlin.math.max
import kotlin.random.Random

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
 *     inputFromExample = { it.data["question"] as String },
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
     * @param strategy The strategy to optimize. Must contain optimizable nodes.
     * @param trainset Training examples.
     * @param metric Metric to evaluate candidate configurations.
     * @param inputFromExample Maps an [Example] to the strategy's typed input.
     * @param valset Optional validation set. If null, split from [trainset].
     * @param toolRegistry Tools available to the agent.
     * @return The best [OptimizationResult] found during search.
     */
    public suspend fun <TInput, TOutput> optimize(
        promptExecutor: PromptExecutor,
        agentConfig: AIAgentConfig,
        strategy: AIAgentGraphStrategy<TInput, TOutput>,
        trainset: Dataset,
        metric: Metric<TOutput>,
        inputFromExample: (Example) -> TInput,
        valset: Dataset? = null,
        toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    ): OptimizationResult {
        val random = Random(config.seed)

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
        val (effectiveTrainset, effectiveValset) = validateAndSplitDatasets(trainset, valset)

        // Compute hyperparameters
        val hyperparams = computeHyperparameters(strategy, effectiveValset, zeroShotMode, random)

        // Step 1: Generate demo candidate sets
        val demoCandidates = generateDemoSets(
            promptExecutor = promptExecutor,
            agentConfig = agentConfig,
            strategy = strategy,
            trainset = effectiveTrainset,
            numCandidateSets = hyperparams.numFewshotCandidates,
            maxBootstrappedDemos = if (zeroShotMode) BOOTSTRAPPED_FEWSHOT_EXAMPLES_IN_CONTEXT else config.maxBootstrappedDemos,
            maxLabeledDemos = if (zeroShotMode) LABELED_FEWSHOT_EXAMPLES_IN_CONTEXT else config.maxLabeledDemos,
            metric = metric,
            metricThreshold = config.metricThreshold,
            maxErrors = config.maxErrors,
            toolRegistry = toolRegistry,
            inputFromExample = inputFromExample,
            random = random,
        )

        // Step 2: Propose instruction candidates
        val proposer = InstructionProposer.create(
            strategy = strategy,
            trainset = effectiveTrainset,
            promptExecutor = promptExecutor,
            llModel = config.promptModel,
            config = config.proposerConfig,
            random = random,
        )
        val instructionCandidates = proposer.proposeInstructionsForProgram(
            demoCandidates = demoCandidates,
            numCandidates = hyperparams.numInstructCandidates,
        )

        // Discard demos if zero-shot mode
        val finalDemoCandidates = if (zeroShotMode) null else demoCandidates

        // Step 3: Random grid search
        return randomGridSearch(
            promptExecutor = promptExecutor,
            agentConfig = agentConfig,
            strategy = strategy,
            toolRegistry = toolRegistry,
            instructionCandidates = instructionCandidates,
            demoCandidates = finalDemoCandidates,
            valset = hyperparams.valset,
            numTrials = hyperparams.numTrials,
            minibatch = hyperparams.minibatch,
            metric = metric,
            inputFromExample = inputFromExample,
            random = random,
        )
    }

    /**
     * Step 3: Random grid search over instruction/demo combinations.
     *
     * Samples random configurations and evaluates them on the validation set (or minibatch).
     * Tracks the best-performing configuration across all trials.
     */
    private suspend fun <TInput, TOutput> randomGridSearch(
        promptExecutor: PromptExecutor,
        agentConfig: AIAgentConfig,
        strategy: AIAgentGraphStrategy<TInput, TOutput>,
        toolRegistry: ToolRegistry,
        instructionCandidates: Map<String, List<String>>,
        demoCandidates: Map<String, List<List<Demonstration<*, *>>>>?,
        valset: Dataset,
        numTrials: Int,
        minibatch: Boolean,
        metric: Metric<TOutput>,
        inputFromExample: (Example) -> TInput,
        random: Random,
    ): OptimizationResult {
        val moduleNames = strategy.findOptimizableModules().map { it.name }

        // Evaluate baseline (empty config)
        val baselineConfig = OptimizationConfig()
        val baselineScore = evaluateConfig(
            config = baselineConfig,
            promptExecutor = promptExecutor,
            agentConfig = agentConfig,
            strategy = strategy,
            toolRegistry = toolRegistry,
            dataset = valset,
            metric = metric,
            inputFromExample = inputFromExample,
        )

        var bestScore = baselineScore
        var bestConfig = baselineConfig

        for (trial in 1..numTrials) {
            // Sample random instruction index per node
            val instructions = moduleNames.associate { name ->
                val candidates = instructionCandidates[name]
                if (candidates.isNullOrEmpty()) {
                    name to (strategy.findOptimizableModules().first { it.name == name }.instruction)
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

            // Evaluate on valset (or minibatch)
            val evalSet = if (minibatch) {
                createMinibatch(valset, config.minibatchSize, random)
            } else {
                valset
            }

            val score = evaluateConfig(
                config = trialConfig,
                promptExecutor = promptExecutor,
                agentConfig = agentConfig,
                strategy = strategy,
                toolRegistry = toolRegistry,
                dataset = evalSet,
                metric = metric,
                inputFromExample = inputFromExample,
            )

            if (score > bestScore) {
                bestScore = score
                bestConfig = trialConfig
            }

            // Periodic full evaluation when using minibatch
            if (minibatch && trial % config.minibatchFullEvalSteps == 0) {
                evaluateConfig(
                    config = bestConfig,
                    promptExecutor = promptExecutor,
                    agentConfig = agentConfig,
                    strategy = strategy,
                    toolRegistry = toolRegistry,
                    dataset = valset,
                    metric = metric,
                    inputFromExample = inputFromExample,
                )
            }
        }

        // Final full evaluation of best config
        val finalScore = evaluateConfig(
            config = bestConfig,
            promptExecutor = promptExecutor,
            agentConfig = agentConfig,
            strategy = strategy,
            toolRegistry = toolRegistry,
            dataset = valset,
            metric = metric,
            inputFromExample = inputFromExample,
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
     * Exceptions during individual example evaluation are counted as score 0.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <TInput, TOutput> evaluateConfig(
        config: OptimizationConfig,
        promptExecutor: PromptExecutor,
        agentConfig: AIAgentConfig,
        strategy: AIAgentGraphStrategy<TInput, TOutput>,
        toolRegistry: ToolRegistry,
        dataset: Dataset,
        metric: Metric<TOutput>,
        inputFromExample: (Example) -> TInput,
    ): Double {
        if (dataset.isEmpty()) return 0.0

        val agent = GraphAIAgent(
            inputType = strategy.inputType,
            outputType = strategy.outputType,
            promptExecutor = promptExecutor,
            agentConfig = agentConfig,
            strategy = strategy,
            toolRegistry = toolRegistry,
        )

        var totalScore = 0.0
        for (example in dataset) {
            val score = try {
                val input = inputFromExample(example)
                val output = withContext(config) {
                    agent.run(input)
                }
                if (example.hasLabel) {
                    metric(example.label as TOutput, output)
                } else {
                    0.0
                }
            } catch (_: Exception) {
                0.0
            }
            totalScore += score
        }

        return totalScore / dataset.size
    }

    /**
     * Validates and optionally splits datasets.
     *
     * If [valset] is null, splits [trainset] into train/val with 80% for validation
     * (matching dspy's hardcoded ratio).
     */
    private fun validateAndSplitDatasets(
        trainset: Dataset,
        valset: Dataset?,
    ): Pair<Dataset, Dataset> {
        require(trainset.isNotEmpty()) { "Trainset cannot be empty" }

        return if (valset == null) {
            require(trainset.size >= 2) {
                "Trainset must have at least 2 examples if no valset specified"
            }
            val valSize = minOf(1000, maxOf(1, (trainset.size * 0.80).toInt()))
            val cutoff = trainset.size - valSize
            trainset.take(cutoff) to trainset.drop(cutoff)
        } else {
            require(valset.isNotEmpty()) { "Validation set must have at least 1 example" }
            trainset to valset
        }
    }

    /**
     * Computes hyperparameters based on auto mode or manual settings.
     */
    private fun <TInput, TOutput> computeHyperparameters(
        strategy: AIAgentGraphStrategy<TInput, TOutput>,
        valset: Dataset,
        zeroShotMode: Boolean,
        random: Random,
    ): HyperparameterSet {
        if (config.auto == null) {
            val numCandidates = config.numCandidates!!
            return HyperparameterSet(
                numTrials = config.numTrials!!,
                valset = valset,
                minibatch = config.minibatch,
                numInstructCandidates = numCandidates,
                numFewshotCandidates = numCandidates,
            )
        }

        val autoSettings = config.auto
        val effectiveValset = createMinibatch(valset, autoSettings.valSize, random)
        val effectiveMinibatch = effectiveValset.size > MIN_MINIBATCH_SIZE

        // Allocate half of candidates to instructions when not zero-shot
        val numInstructCandidates = if (zeroShotMode) {
            autoSettings.numCandidates
        } else {
            (autoSettings.numCandidates * 0.5).toInt()
        }
        val numFewshotCandidates = autoSettings.numCandidates

        val numTrials = estimateNumTrials(strategy, zeroShotMode, autoSettings.numCandidates)

        return HyperparameterSet(
            numTrials = numTrials,
            valset = effectiveValset,
            minibatch = effectiveMinibatch,
            numInstructCandidates = numInstructCandidates,
            numFewshotCandidates = numFewshotCandidates,
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
        var numVars = strategy.findOptimizableModules().size
        if (!zeroShotMode) {
            numVars *= 2
        }
        return max(
            (2 * numVars * log2(numCandidates.toDouble())).toInt(),
            (1.5 * numCandidates).toInt(),
        )
    }

    private fun createMinibatch(dataset: Dataset, batchSize: Int, random: Random): Dataset {
        return if (batchSize >= dataset.size) {
            dataset
        } else {
            dataset.shuffled(random).take(batchSize)
        }
    }

    private data class HyperparameterSet(
        val numTrials: Int,
        val valset: Dataset,
        val minibatch: Boolean,
        val numInstructCandidates: Int,
        val numFewshotCandidates: Int,
    )

    private companion object {
        const val MIN_MINIBATCH_SIZE = 50
        const val BOOTSTRAPPED_FEWSHOT_EXAMPLES_IN_CONTEXT = 3
        const val LABELED_FEWSHOT_EXAMPLES_IN_CONTEXT = 0
    }
}
