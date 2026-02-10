package ai.koog.agents.core.optimization.optimizers.mipro

import ai.koog.agents.core.optimization.core.Dataset
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message

private const val DEFAULT_BATCH_SIZE = 10
private const val MAX_ITERATIONS = 10
private const val COMPLETE_SKIP_THRESHOLD = 5

/**
 * Creates a summary of the dataset by iteratively observing patterns in batches of examples.
 *
 * The process:
 * 1. Show first batch of examples to LLM, ask for observations
 * 2. Show next batch + prior observations, ask for additions or "COMPLETE"
 * 3. Repeat until "COMPLETE" is returned multiple times or max iterations reached
 * 4. Summarize all observations into 2-3 sentences
 *
 * Corresponds to dspy's create_dataset_summary() function.
 *
 * @param trainset The training dataset to summarize
 * @param promptExecutor Executor for running prompts
 * @param llModel The LLM model to use
 * @param batchSize Number of examples to show per batch
 * @return A 2-3 sentence summary of the dataset, or null if summarization fails
 */
internal suspend fun createDatasetSummary(
    trainset: Dataset,
    promptExecutor: PromptExecutor,
    llModel: LLModel,
    batchSize: Int = DEFAULT_BATCH_SIZE,
): String? {
    if (trainset.isEmpty()) return null

    // Step 1: Get initial observations from first batch
    val firstBatch = trainset.take(batchSize)
    val initialPrompt = datasetDescriptorPrompt(firstBatch)

    val initialObservations = try {
        val responses = promptExecutor.execute(initialPrompt, llModel)
        extractAssistantContent(responses)
    } catch (_: Exception) {
        return null
    }

    if (initialObservations.isBlank()) return null

    // Step 2: Iteratively refine observations with more batches
    var observations = initialObservations
    var skips = 0
    var iterationCount = 0

    for (batchStart in batchSize until trainset.size step batchSize) {
        iterationCount++
        if (iterationCount >= MAX_ITERATIONS) break

        val batchEnd = minOf(batchStart + batchSize, trainset.size)
        val batch = trainset.subList(batchStart, batchEnd)

        val refinementPrompt = datasetDescriptorWithPriorObservationsPrompt(batch, observations)

        val newObservations = try {
            val responses = promptExecutor.execute(refinementPrompt, llModel)
            extractAssistantContent(responses)
        } catch (_: Exception) {
            break
        }

        // Check if LLM signals completion
        if (newObservations.length >= 8 && newObservations.take(8).uppercase() == "COMPLETE") {
            skips++
            if (skips >= COMPLETE_SKIP_THRESHOLD) break
            continue
        }

        observations += "\n" + newObservations
    }

    // Step 3: Summarize all observations
    val summaryPrompt = observationSummarizerPrompt(observations)

    val summary = try {
        val responses = promptExecutor.execute(summaryPrompt, llModel)
        extractAssistantContent(responses)
    } catch (_: Exception) {
        // Fall back to first few sentences of observations
        observations.take(500)
    }

    return summary.ifBlank { null }
}

/**
 * Extracts the text content from assistant responses.
 */
internal fun extractAssistantContent(responses: List<Message.Response>): String {
    return responses
        .filterIsInstance<Message.Assistant>()
        .firstOrNull()
        ?.content
        ?: ""
}
