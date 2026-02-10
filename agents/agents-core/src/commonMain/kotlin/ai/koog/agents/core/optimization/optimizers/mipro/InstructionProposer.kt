package ai.koog.agents.core.optimization.optimizers.mipro

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.optimization.OptimizableNode
import ai.koog.agents.core.optimization.core.Dataset
import ai.koog.agents.core.optimization.core.Demonstration
import ai.koog.agents.core.optimization.util.describeForOptimization
import ai.koog.agents.core.optimization.util.findOptimizableModules
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * Tips for instruction generation, randomly selected to encourage diversity.
 *
 * Corresponds to dspy's TIPS dictionary in grounded_proposer.py.
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
 * @param programAware Whether to include program structure description in context
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
 * Corresponds to dspy's GroundedProposer class.
 *
 * @param strategy The strategy containing optimizable nodes to generate instructions for
 * @param trainset Training dataset used for context
 * @param promptExecutor Executor for running LLM prompts
 * @param llModel The LLM model to use for instruction generation
 * @param config Configuration options
 * @param random Random instance for reproducibility
 */
public class InstructionProposer(
    private val strategy: AIAgentGraphStrategy<*, *>,
    private val trainset: Dataset,
    private val promptExecutor: PromptExecutor,
    private val llModel: LLModel,
    private val config: InstructionProposerConfig = InstructionProposerConfig(),
    private val random: Random = Random.Default,
    programDescription: String? = null,
) {
    private var datasetSummary: String? = null
    private var programCode: String? = null
    private var programDescription: String? = programDescription
    private val moduleDescriptions: MutableMap<String, String> = mutableMapOf()

    /**
     * Initialize the proposer by generating dataset summary, program description,
     * and per-module descriptions.
     *
     * If a [programDescription] was provided in the constructor, it is used as-is.
     * Otherwise, if [InstructionProposerConfig.programAware] is true, the LLM generates one.
     *
     * Similarly, if an [OptimizableNode] has a non-null [OptimizableNode.description],
     * it is used directly. Otherwise, if programAware, the LLM generates a description
     * of the module's role.
     *
     * Call this before [proposeInstructionsForProgram].
     */
    public suspend fun initialize() {
        if (config.useDatasetSummary) {
            try {
                datasetSummary = createDatasetSummary(
                    trainset = trainset,
                    promptExecutor = promptExecutor,
                    llModel = llModel,
                )
            } catch (_: Exception) {
                // Continue without dataset summary
            }
        }

        if (config.programAware) {
            try {
                programCode = strategy.describeForOptimization()
            } catch (_: Exception) {
                // Continue without program code
            }
        }

        // Generate program description via LLM if not user-provided
        if (programDescription == null && config.programAware && programCode != null) {
            try {
                val programExample = formatTrainsetExample()
                val prompt = describeProgramPrompt(programCode!!, programExample)
                val responses = promptExecutor.execute(prompt, llModel)
                programDescription = extractAssistantContent(responses).ifBlank { null }
            } catch (_: Exception) {
                // Continue without program description
            }
        }

        // Generate per-module descriptions
        if (config.programAware) {
            val modules = strategy.findOptimizableModules()
            for (module in modules) {
                if (module.description != null) {
                    // User-provided description on the node
                    moduleDescriptions[module.name] = module.description
                } else if (programCode != null && programDescription != null) {
                    // LLM-generated description
                    try {
                        val moduleCode = buildModuleCodeString(module.name, module)
                        val prompt = describeModulePrompt(programCode!!, programDescription!!, moduleCode)
                        val responses = promptExecutor.execute(prompt, llModel)
                        val desc = extractAssistantContent(responses).ifBlank { null }
                        if (desc != null) {
                            moduleDescriptions[module.name] = desc
                        }
                    } catch (_: Exception) {
                        // Continue without this module's description
                    }
                }
            }
        }
    }

    /**
     * Format a trainset example for use as the "program example" in [describeProgramPrompt].
     * Matches DSPy's use of task_demos as program_example.
     */
    private fun formatTrainsetExample(): String {
        if (trainset.isEmpty()) return "No examples available."
        val example = trainset.first()
        return example.data.entries.joinToString("\n") { (k, v) -> "$k: $v" }
    }

    /**
     * Generate instruction candidates for all optimizable nodes in the strategy.
     *
     * For each node, generates N instruction candidates by varying:
     * - The demo set used for context
     * - The tip (if setTipRandomly is true)
     *
     * @param demoCandidates Map from node name to list of demo sets (from Step 1).
     *  Each demo set is a list of [Demonstration]s with typed input/output.
     * @param numCandidates Number of instruction candidates to generate per node
     * @return Map from node name to list of N instruction candidates
     */
    public suspend fun proposeInstructionsForProgram(
        demoCandidates: Map<String, List<List<Demonstration<*, *>>>>?,
        numCandidates: Int,
    ): Map<String, List<String>> {
        val modules = strategy.findOptimizableModules()
        val proposedInstructions = mutableMapOf<String, MutableList<String>>()

        // Determine how many demo sets we have (or default to numCandidates if no demos)
        val numDemoSets = if (demoCandidates.isNullOrEmpty() || !config.useTaskDemos) {
            numCandidates
        } else {
            val firstModuleDemos = demoCandidates.values.firstOrNull()?.size ?: numCandidates
            minOf(firstModuleDemos, numCandidates)
        }

        for (module in modules) {
            val moduleName = module.name
            proposedInstructions[moduleName] = mutableListOf()

            for (demoSetIndex in 0 until numDemoSets) {
                val tip = selectTip()

                val instruction = proposeInstructionForModule(
                    moduleName = moduleName,
                    module = module,
                    demoCandidates = demoCandidates,
                    demoSetIndex = demoSetIndex,
                    tip = tip,
                )

                proposedInstructions[moduleName]!!.add(instruction)
            }
        }

        return proposedInstructions
    }

    /**
     * Generate a single instruction for a specific node.
     */
    private suspend fun proposeInstructionForModule(
        moduleName: String,
        module: OptimizableNode<*, *>,
        demoCandidates: Map<String, List<List<Demonstration<*, *>>>>?,
        demoSetIndex: Int,
        tip: String?,
    ): String {
        val moduleCodeString = buildModuleCodeString(moduleName, module)
        val taskDemos = gatherTaskDemos(moduleName, demoCandidates, demoSetIndex)
        val basicInstruction = module.instruction

        val promptConfig = GenerateInstructionPromptConfig(
            datasetSummary = datasetSummary,
            programDescription = programDescription,
            moduleCodeString = moduleCodeString,
            moduleDescription = moduleDescriptions[moduleName],
            taskDemos = taskDemos,
            basicInstruction = basicInstruction,
            tip = if (config.useTip) tip else null,
        )

        val instructionPrompt = generateModuleInstructionPrompt(promptConfig)

        return try {
            val responses = promptExecutor.execute(instructionPrompt, llModel)
            val proposedInstruction = extractAssistantContent(responses).trim()
            stripInstructionPrefixes(proposedInstruction).ifBlank { basicInstruction }
        } catch (_: Exception) {
            basicInstruction
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
        }
    }

    /**
     * Gather task demo examples from the demo candidates for context.
     *
     * Prefers bootstrapped demonstrations over labeled-only ones.
     */
    private fun gatherTaskDemos(
        moduleName: String,
        demoCandidates: Map<String, List<List<Demonstration<*, *>>>>?,
        demoSetIndex: Int,
    ): String {
        if (!config.useTaskDemos || demoCandidates.isNullOrEmpty()) {
            return "No task demos provided."
        }

        val moduleDemoCandidates = demoCandidates[moduleName]
        if (moduleDemoCandidates.isNullOrEmpty()) {
            return "No task demos provided."
        }

        // Get the current demo set and adjacent sets for more examples
        val adjacentSets = buildList {
            if (demoSetIndex < moduleDemoCandidates.size) {
                add(moduleDemoCandidates[demoSetIndex])
            }
            for (i in (demoSetIndex + 1) until moduleDemoCandidates.size) {
                add(moduleDemoCandidates[i])
            }
            for (i in 0 until demoSetIndex) {
                add(moduleDemoCandidates[i])
            }
        }

        // Gather bootstrapped examples first, up to numDemosInContext
        val examples = mutableListOf<String>()
        for (demoSet in adjacentSets) {
            for (demo in demoSet) {
                if (demo.isBootstrapped && examples.size < config.numDemosInContext) {
                    examples.add(formatDemonstrationAsExample(demo))
                }
            }
            if (examples.size >= config.numDemosInContext) break
        }

        // If we still need more, add non-bootstrapped demos
        if (examples.size < config.numDemosInContext) {
            for (demoSet in adjacentSets) {
                for (demo in demoSet) {
                    if (!demo.isBootstrapped && examples.size < config.numDemosInContext) {
                        examples.add(formatDemonstrationAsExample(demo))
                    }
                }
                if (examples.size >= config.numDemosInContext) break
            }
        }

        // Default to no demos if first demo set (index 0) or no examples gathered
        if (demoSetIndex == 0 || examples.isEmpty()) {
            return "No task demos provided."
        }

        return examples.joinToString("\n\n")
    }

    /**
     * Format a [Demonstration] as a string example for the LLM.
     */
    private fun formatDemonstrationAsExample(demo: Demonstration<*, *>): String {
        return buildString {
            appendLine("Input:")
            appendLine(demo.input.toString().take(500))
            val output = demo.output.toString()
            if (output.isNotBlank()) {
                appendLine("Output:")
                appendLine(output.take(500))
            }
        }
    }

    /**
     * Select a tip based on configuration.
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
            "PROPOSED INSTRUCTION:",
            "Proposed Instruction:",
            "INSTRUCTION:",
            "Instruction:",
            "Here is",
            "Here's",
        )

        var result = instruction
        for (prefix in prefixes) {
            if (result.startsWith(prefix, ignoreCase = true)) {
                result = result.removePrefix(prefix).trimStart()
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
