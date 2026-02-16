package ai.koog.agents.core.optimization.optimizers.mipro

import ai.koog.agents.core.optimization.optimizers.utils.executeAndExtract
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

private const val DEFAULT_BATCH_SIZE = 10
private const val MAX_ITERATIONS = 10
private const val COMPLETE_SKIP_THRESHOLD = 5

/**
 * Creates a summary of the dataset by iteratively observing patterns in batches of examples.
 *
 * The process:
 * 1. Show the first batch of examples to LLM, ask for observations
 * 2. Show the next batch and prior observations, ask for additions or "COMPLETE"
 * 3. Repeat until "COMPLETE" is returned multiple times or max iterations reached
 * 4. Summarize all observations into 2-3 sentences
 *
 * Corresponds to DSPy's create_dataset_summary() function.
 *
 * @param renderedExamples Pre-rendered string representations of training examples
 * @param promptExecutor Executor for running prompts
 * @param llModel The LLM model to use
 * @param batchSize Number of examples to show per batch
 * @return A 2-3 sentences long summary of the dataset, or null if summarization fails
 */
internal suspend fun createDatasetSummary(
    renderedExamples: List<String>,
    promptExecutor: PromptExecutor,
    llModel: LLModel,
    batchSize: Int = DEFAULT_BATCH_SIZE,
): String? {
    if (renderedExamples.isEmpty()) return null

    // Step 1: Get initial observations from the first batch
    val firstBatch = renderedExamples.take(batchSize)
    val initialPrompt = datasetDescriptorPrompt(firstBatch)

    val initialObservations = promptExecutor.executeAndExtract(initialPrompt, llModel)
    if (initialObservations == null) {
        logger.warn { "Failed to get initial observations from LLM" }
        return null
    }

    // Step 2: Iteratively refine observations with more batches
    var observations = initialObservations
    var skips = 0
    var iterationCount = 0

    for (batchStart in batchSize until renderedExamples.size step batchSize) {
        iterationCount++
        if (iterationCount >= MAX_ITERATIONS) break

        val batchEnd = minOf(batchStart + batchSize, renderedExamples.size)
        val batch = renderedExamples.subList(batchStart, batchEnd)

        val refinementPrompt = datasetDescriptorWithPriorObservationsPrompt(batch, observations)

        val newObservations = promptExecutor.executeAndExtract(refinementPrompt, llModel)
        if (newObservations == null) {
            logger.debug { "LLM call failed during refinement at batch $iterationCount, stopping iteration" }
            break
        }

        // Check if LLM signals completion
        if (newObservations.trimStart().uppercase().startsWith("COMPLETE")) {
            skips++
            if (skips >= COMPLETE_SKIP_THRESHOLD) break
            continue
        }

        observations += "\n" + newObservations
    }

    // Step 3: Summarize all observations
    val summaryPrompt = observationSummarizerPrompt(observations)

    val summary = promptExecutor.executeAndExtract(summaryPrompt, llModel)
    if (summary == null) {
        logger.debug { "Failed to summarize observations, falling back to raw observations" }
        // Fall back to the first few sentences of observations
        return observations.take(500).ifBlank { null }
    }

    return summary
}
