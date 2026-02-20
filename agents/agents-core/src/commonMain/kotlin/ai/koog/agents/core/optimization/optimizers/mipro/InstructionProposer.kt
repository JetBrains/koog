package ai.koog.agents.core.optimization.optimizers.mipro

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.optimization.core.OptimizableNode
import ai.koog.agents.core.optimization.core.Dataset
import ai.koog.agents.core.optimization.core.Demonstration
import ai.koog.agents.core.optimization.optimizers.utils.describeForOptimization
import ai.koog.agents.core.optimization.optimizers.utils.executeAndExtract
import ai.koog.agents.core.optimization.optimizers.utils.extractFieldDescriptionsFromType
import ai.koog.agents.core.optimization.optimizers.utils.findOptimizableNodes
import ai.koog.agents.core.optimization.optimizers.utils.serializeOrToString
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.KType

private val logger = KotlinLogging.logger {}

private const val NO_TASK_DEMOS = "No task demos provided."

/**
 * Tips for instruction generation, randomly selected to encourage diversity.
 *
 * Corresponds to DSPy's TIPS dictionary in grounded_proposer.py.
 */
public val TIPS: Map<String, String> = mapOf(
    "none" to "",
    "creative" to "Don't be afraid to be creative when creating the new instruction!",
    "simple" to "Keep the instruction clear and concise.",
    "description" to "Make sure your instruction is very informative and descriptive.",
    "high_stakes" to "The instruction should include a high stakes scenario in which the LM must solve the task!",
    "persona" to "Include a persona that is relevant to the task in the instruction (ie. \"You are a ...\")",
)

/**
 * Configuration for [InstructionProposer].
 *
 * @param useDatasetSummary Whether to generate and use a dataset summary for context
 * @param programAware Whether to include the program structure description in context
 * @param useTaskDemos Whether to include few-shot demo examples in context
 * @param numDemosInContext Maximum number of demo examples to include
 * @param useTip Whether to include a tip for instruction generation
 * @param setTipRandomly Whether to randomly select the tip (when useTip is true)
 */
public data class InstructionProposerConfig(
    val useDatasetSummary: Boolean = true,
    val programAware: Boolean = true,
    val useTaskDemos: Boolean = true,
    val numDemosInContext: Int = 3,
    val useTip: Boolean = true,
    val setTipRandomly: Boolean = true,
    val useInstructHistory: Boolean = false,
    val setHistoryRandomly: Boolean = false,
    val includeFieldDescriptions: Boolean = true,
)

/**
 * Generates instruction candidates for each optimizable node in a strategy.
 *
 * This is the core component of MIPRO Step 2. It uses an LLM to generate diverse
 * instruction variants by providing context about:
 * - The dataset (via a generated summary)
 * - The program structure (via [describeForOptimization])
 * - Few-shot demo examples
 * - Random tips for diversity
 *
 * Corresponds to DSPy's GroundedProposer class.
 *
 * @param strategy The strategy containing optimizable nodes to generate instructions for
 * @param promptExecutor Executor for running LLM prompts
 * @param llModel The LLM model to use for instruction generation
 * @param config Configuration options
 * @param random Random instance for reproducibility
 */
public class InstructionProposer private constructor(
    private val strategy: AIAgentGraphStrategy<*, *>,
    private val promptExecutor: PromptExecutor,
    private val llModel: LLModel,
    private val config: InstructionProposerConfig,
    private val random: Random,
    private val userProgramDescription: String?,
    private val datasetSummary: String?,
    private val programCode: String?,
) {
    /** Factory for creating [InstructionProposer] instances. */
    public companion object {
        private const val MAX_INSTRUCT_IN_HISTORY = 5

        /**
         * Create an [InstructionProposer], generating dataset summary and program code upfront.
         *
         * Program and module descriptions are generated per-call in [proposeInstructionForNode]
         * to adapt to varying task demo context (matching DSPy's GroundedProposer.forward()).
         *
         * User-provided descriptions ([programDescription] and [OptimizableNode.description])
         * always skip LLM generation.
         *
         * @param describeInput Renders an input value as a human-readable string for dataset
         *  summarization and example display. Defaults to pretty-printed JSON serialization
         *  with [toString] fallback for non-serializable types.
         */
        public suspend fun <TInput, TOutput> create(
            strategy: AIAgentGraphStrategy<*, *>,
            trainset: Dataset<TInput, TOutput>,
            promptExecutor: PromptExecutor,
            llModel: LLModel,
            config: InstructionProposerConfig = InstructionProposerConfig(),
            random: Random = Random.Default,
            programDescription: String? = null,
            describeInput: (TInput) -> String = { serializeOrToString(it, strategy.inputType) },
        ): InstructionProposer {
            val renderedExamples = trainset.map { ex ->
                buildString {
                    append(describeInput(ex.input))
                    if (ex.hasLabel) append("\nlabel: ${ex.label}")
                }
            }

            val datasetSummary = if (config.useDatasetSummary) {
                createDatasetSummary(renderedExamples, promptExecutor, llModel)
            } else null

            val programCode = if (config.programAware) {
                try {
                    strategy.describeForOptimization()
                } catch (_: Exception) {
                    null
                }
            } else null

            return InstructionProposer(
                strategy = strategy,
                promptExecutor = promptExecutor,
                llModel = llModel,
                config = config,
                random = random,
                userProgramDescription = programDescription,
                datasetSummary = datasetSummary,
                programCode = programCode,
            )
        }
    }

    /**
     * Generate instruction candidates for all optimizable nodes in the strategy.
     *
     * For each node, generates N instruction candidates by varying:
     * - The demo set used for context
     * - The tip (if setTipRandomly is true)
     *
     * @param demoCandidates Map from node name to a list of demo sets (from Step 1).
     *  Each demo set is a list of [Demonstration]s with typed input/output.
     * @param numCandidates Number of instruction candidates to generate per node
     * @param parallelism Maximum number of concurrent instruction proposals per module.
     *  Set to 1 (default) for sequential execution.
     * @return Map from node name to a list of N instruction candidates
     */
    public suspend fun proposeInstructionsForProgram(
        demoCandidates: Map<String, List<List<Demonstration<*, *>>>>?,
        numCandidates: Int,
        previousInstructions: Map<String, List<Pair<String, Double>>> = emptyMap(),
        parallelism: Int = 1,
    ): Map<String, List<String>> {
        val modules = strategy.findOptimizableNodes()

        // Gap 3: 50/50 coin flip to toggle instruction history for this round
        val effectiveUseHistory = if (config.setHistoryRandomly) random.nextBoolean() else config.useInstructHistory

        // Determine how many demo sets we have (or default to numCandidates if no demos)
        val numDemoSets = if (demoCandidates.isNullOrEmpty() || !config.useTaskDemos) {
            numCandidates
        } else {
            val firstModuleDemos = demoCandidates.values.firstOrNull()?.size ?: numCandidates
            minOf(firstModuleDemos, numCandidates)
        }

        // Pre-select tips from parent random before launching (for determinism)
        val preSelectedTips = (0 until numDemoSets).map { selectTip() }

        return buildMap {
            for ((moduleIdx, module) in modules.withIndex()) {
                val moduleName = module.name
                logger.info { "Proposing instructions for module '${moduleName}' (${moduleIdx + 1}/${modules.size})..." }

                val semaphore = Semaphore(maxOf(1, parallelism))
                val instructions = coroutineScope {
                    (0 until numDemoSets).map { demoSetIndex ->
                        async {
                            semaphore.withPermit {
                                val instruction = proposeInstructionForNode(
                                    node = module,
                                    demoCandidates = demoCandidates,
                                    demoSetIndex = demoSetIndex,
                                    tip = preSelectedTips[demoSetIndex],
                                    effectiveUseHistory = effectiveUseHistory,
                                    previousInstructions = previousInstructions,
                                )
                                logger.info { "  Candidate ${demoSetIndex + 1}/$numDemoSets generated for '$moduleName'" }
                                instruction
                            }
                        }
                    }.awaitAll()
                }
                put(moduleName, instructions)
            }
        }
    }

    /**
     * Generate a single instruction for a specific node.
     *
     * Program and module descriptions are generated fresh per-call to adapt to
     * the varying task demo context (Gap 4). User-provided descriptions skip LLM.
     */
    private suspend fun proposeInstructionForNode(
        node: OptimizableNode<*, *>,
        demoCandidates: Map<String, List<List<Demonstration<*, *>>>>?,
        demoSetIndex: Int,
        tip: String?,
        effectiveUseHistory: Boolean,
        previousInstructions: Map<String, List<Pair<String, Double>>>,
    ): String {
        val nodeName = node.name
        val moduleCodeString = buildModuleCodeString(nodeName, node)
        val taskDemos = gatherTaskDemos(node, demoCandidates, demoSetIndex)
        val basicInstruction = node.instruction

        // Gap 4: Per-call program description
        val currentProgramDescription = userProgramDescription
            ?: if (config.programAware && programCode != null) {
                val prompt = describeProgramPrompt(programCode, taskDemos)
                promptExecutor.executeAndExtract(prompt, llModel)
            } else {
                null
            }

        // Gap 4: Per-call module description
        val currentModuleDescription = node.description
            ?: if (config.programAware && programCode != null && currentProgramDescription != null) {
                // Gap 5: Pass taskDemos as programExample
                val prompt = describeModulePrompt(programCode, currentProgramDescription, taskDemos, moduleCodeString)
                promptExecutor.executeAndExtract(prompt, llModel)
            } else {
                null
            }

        // Gap 2: Format instruction history if enabled
        val historyString = if (effectiveUseHistory) {
            formatInstructionHistory(nodeName, previousInstructions)
        } else {
            null
        }

        val promptConfig = GenerateInstructionPromptConfig(
            datasetSummary = datasetSummary,
            programCode = programCode,
            programDescription = currentProgramDescription,
            moduleCodeString = moduleCodeString,
            moduleDescription = currentModuleDescription,
            taskDemos = taskDemos,
            previousInstructions = historyString,
            basicInstruction = basicInstruction,
            tip = if (config.useTip) tip else null,
        )

        val instructionPrompt = generateModuleInstructionPrompt(promptConfig)

        val proposedInstruction =
            promptExecutor.executeAndExtract(instructionPrompt, llModel) ?: return basicInstruction
        return stripInstructionPrefixes(proposedInstruction.trim()).ifBlank { basicInstruction }
    }

    /**
     * Format instruction history for a module, sorted by score descending, taking top N, reversed.
     *
     * Matches DSPy's format: `"instruction text" | Score: X.XX`
     */
    private fun formatInstructionHistory(
        moduleName: String,
        previousInstructions: Map<String, List<Pair<String, Double>>>,
        maxHistory: Int = MAX_INSTRUCT_IN_HISTORY,
    ): String? {
        val history = previousInstructions[moduleName] ?: return null
        if (history.isEmpty()) return null

        val topEntries = history
            .sortedByDescending { it.second }
            .take(maxHistory)
            .reversed()

        return topEntries.joinToString("\n") { (instruction, score) ->
            val whole = score.toInt()
            val frac = ((score - whole) * 100 + 0.5).toInt()
            "\"$instruction\" | Score: $whole.${frac.toString().padStart(2, '0')}"
        }
    }

    /**
     * Build a string describing the node's type signature for the LLM.
     */
    private fun buildModuleCodeString(moduleName: String, module: OptimizableNode<*, *>): String {
        val inputTypeName = module.inputType.simpleTypeName()
        val outputTypeName = module.outputType.simpleTypeName()

        return buildString {
            appendLine("Module \"$moduleName\": $inputTypeName -> $outputTypeName")
            appendLine("Current instruction: \"${module.instruction}\"")
            if (config.includeFieldDescriptions) {
                val inputDescs = extractFieldDescriptionsFromType(module.inputType)
                if (inputDescs.isNotEmpty()) {
                    appendLine("Input fields:")
                    inputDescs.forEach { (name, desc) ->
                        appendLine("  - $name: $desc")
                    }
                }
                val outputDescs = extractFieldDescriptionsFromType(module.outputType)
                if (outputDescs.isNotEmpty()) {
                    appendLine("Output fields:")
                    outputDescs.forEach { (name, desc) ->
                        appendLine("  - $name: $desc")
                    }
                }
            }
        }
    }

    /**
     * Gather task demo examples from the demo candidates for context.
     *
     * Prefers bootstrapped demonstrations over labeled-only ones.
     */
    private fun gatherTaskDemos(
        node: OptimizableNode<*, *>,
        demoCandidates: Map<String, List<List<Demonstration<*, *>>>>?,
        demoSetIndex: Int,
    ): String {
        if (!config.useTaskDemos || demoCandidates.isNullOrEmpty()) {
            return NO_TASK_DEMOS
        }

        val moduleDemoCandidates = demoCandidates[node.name]
        if (moduleDemoCandidates.isNullOrEmpty()) {
            return NO_TASK_DEMOS
        }

        // Default to no demos for the first demo set (index 0) — matches DSPy behavior
        if (demoSetIndex == 0) {
            return NO_TASK_DEMOS
        }

        // Rotate demo sets so that demoSetIndex is first, wrapping around
        val safeIndex = demoSetIndex.coerceAtMost(moduleDemoCandidates.size)
        val rotatedSets = moduleDemoCandidates.subList(safeIndex, moduleDemoCandidates.size) +
            moduleDemoCandidates.subList(0, safeIndex)

        // Collect up to numDemosInContext examples, preferring bootstrapped ones
        val limit = config.numDemosInContext
        val allDemos = rotatedSets.flatten()
        val examples = buildList {
            for (demo in allDemos) {
                if (size >= limit) break
                if (demo.isBootstrapped) add(formatDemonstrationAsExample(demo, node.inputType, node.outputType))
            }
            if (size < limit) {
                for (demo in allDemos) {
                    if (size >= limit) break
                    if (!demo.isBootstrapped) add(formatDemonstrationAsExample(demo, node.inputType, node.outputType))
                }
            }
        }

        if (examples.isEmpty()) {
            return NO_TASK_DEMOS
        }

        return examples.joinToString("\n\n")
    }

    /**
     * Format a [Demonstration] as a string example for the LLM.
     * Uses pretty-printed JSON serialization with [toString] fallback.
     */
    private fun formatDemonstrationAsExample(
        demo: Demonstration<*, *>,
        inputType: KType,
        outputType: KType,
    ): String {
        return buildString {
            appendLine("Input:")
            appendLine(serializeOrToString(demo.input, inputType).take(500))
            val output = serializeOrToString(demo.output, outputType)
            if (output.isNotBlank()) {
                appendLine("Output:")
                appendLine(output.take(500))
            }
        }
    }

    /**
     * Select a tip based on the configuration.
     */
    private fun selectTip(): String? {
        if (!config.useTip) return null

        return if (config.setTipRandomly) {
            val tipKey = TIPS.keys.toList().random(random)
            TIPS[tipKey]
        } else {
            TIPS["none"]
        }
    }

    /**
     * Strip common prefixes that LLMs might add to instructions.
     */
    private fun stripInstructionPrefixes(instruction: String): String {
        val prefixes = listOf(
            "proposed instruction:",
            "instruction:",
            "here is",
            "here's",
        )

        var result = instruction
        for (prefix in prefixes) {
            if (result.startsWith(prefix, ignoreCase = true)) {
                result = result.drop(prefix.length).trimStart()
            }
        }
        return result.trim()
    }
}

/**
 * Gets a simple type name from a [KType], safe for commonMain (no jvmErasure).
 */
private fun KType.simpleTypeName(): String {
    return (classifier as? KClass<*>)?.simpleName ?: toString()
}
