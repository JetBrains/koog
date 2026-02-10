package ai.koog.agents.core.optimization.optimizers.mipro

import ai.koog.agents.core.optimization.core.Example
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt

/**
 * Prompt builders for instruction proposal in MIPRO Step 2.
 *
 * These functions construct Koog prompts for:
 * - DatasetDescriptor: Observe patterns in a batch of examples
 * - DatasetDescriptorWithPriorObservations: Add to prior observations or signal completion
 * - ObservationSummarizer: Summarize observations into 2-3 sentences
 * - GenerateModuleInstruction: Generate a new instruction given context
 */

/**
 * Formats a batch of examples for display to the LLM.
 */
internal fun formatExampleBatch(examples: List<Example>): String {
    return examples.mapIndexed { index, example ->
        val fields = example.data.entries.joinToString(", ") { (k, v) -> "$k=$v" }
        "Example ${index + 1}: {$fields}"
    }.joinToString("\n")
}

/**
 * Prompt to observe patterns in a batch of dataset examples.
 *
 * Corresponds to dspy's DatasetDescriptor signature.
 */
internal fun datasetDescriptorPrompt(exampleBatch: List<Example>): Prompt = prompt("dataset-descriptor") {
    system(
        """Given several examples from a dataset please write observations about trends that hold for most or all of the samples.
Some areas you may consider in your observations: topics, content, syntax, conciseness, etc.
It will be useful to make an educated guess as to the nature of the task this dataset will enable. Don't be afraid to be creative."""
    )
    user(
        """EXAMPLES:
${formatExampleBatch(exampleBatch)}

Please write your observations about trends that hold for most or all of the samples."""
    )
}

/**
 * Prompt to add observations to prior observations, or signal completion.
 *
 * Corresponds to dspy's DatasetDescriptorWithPriorObservations signature.
 */
internal fun datasetDescriptorWithPriorObservationsPrompt(
    exampleBatch: List<Example>,
    priorObservations: String
): Prompt = prompt("dataset-descriptor-with-prior") {
    system(
        """Given several examples from a dataset please write observations about trends that hold for most or all of the samples.
I will also provide you with a few observations I have already made. Please add your own observations or if you feel the observations are comprehensive say 'COMPLETE'.
Some areas you may consider in your observations: topics, content, syntax, conciseness, etc.
It will be useful to make an educated guess as to the nature of the task this dataset will enable. Don't be afraid to be creative."""
    )
    user(
        """PRIOR OBSERVATIONS:
$priorObservations

NEW EXAMPLES:
${formatExampleBatch(exampleBatch)}

Please add new observations or respond with 'COMPLETE' if the observations are comprehensive."""
    )
}

/**
 * Prompt to summarize observations into a brief 2-3 sentence summary.
 *
 * Corresponds to dspy's ObservationSummarizer signature.
 */
internal fun observationSummarizerPrompt(observations: String): Prompt = prompt("observation-summarizer") {
    system(
        """Given a series of observations I have made about my dataset, please summarize them into a brief 2-3 sentence summary which highlights only the most important details."""
    )
    user(
        """OBSERVATIONS:
$observations

Please provide a two to three sentence summary of only the most significant highlights of these observations."""
    )
}

/**
 * Configuration for building a GenerateModuleInstruction prompt.
 */
internal data class GenerateInstructionPromptConfig(
    val datasetSummary: String?,
    val programDescription: String?,
    val moduleCodeString: String,
    val taskDemos: String,
    val basicInstruction: String,
    val tip: String?,
)

/**
 * Prompt to generate a new instruction for a module.
 *
 * Corresponds to dspy's GenerateSingleModuleInstruction signature,
 * with conditional sections based on what context is available.
 */
internal fun generateModuleInstructionPrompt(config: GenerateInstructionPromptConfig): Prompt =
    prompt("generate-instruction") {
        system(
            """Use the information below to learn about a task that we are trying to solve using calls to an LM, then generate a new instruction that will be used to prompt a Language Model to better solve the task."""
        )

        val sections = buildString {
            if (config.datasetSummary != null) {
                appendLine("DATASET SUMMARY:")
                appendLine(config.datasetSummary)
                appendLine()
            }

            if (config.programDescription != null) {
                appendLine("PROGRAM STRUCTURE:")
                appendLine(config.programDescription)
                appendLine()
            }

            appendLine("MODULE TO OPTIMIZE:")
            appendLine(config.moduleCodeString)
            appendLine()

            appendLine("TASK DEMO(S):")
            appendLine(config.taskDemos)
            appendLine()

            appendLine("BASIC INSTRUCTION:")
            appendLine(config.basicInstruction)

            if (config.tip != null && config.tip.isNotBlank()) {
                appendLine()
                appendLine("TIP:")
                appendLine(config.tip)
            }

            appendLine()
            appendLine("---")
            appendLine("Based on the above information, propose an improved instruction that will help the Language Model perform this task better. Output only the instruction text, nothing else.")
        }

        user(sections)
    }
