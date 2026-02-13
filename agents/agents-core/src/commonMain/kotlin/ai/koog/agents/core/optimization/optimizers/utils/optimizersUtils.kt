package ai.koog.agents.core.optimization.optimizers.utils

import ai.koog.agents.core.optimization.core.Demonstration
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/**
 * Randomly samples up to [k] demonstrations from [candidates].
 *
 * @param candidates The pool of demonstrations to sample from.
 * @param k Maximum number of demonstrations to return.
 * @param random The random source for shuffling.
 * @return A list of up to [k] randomly selected demonstrations.
 */
public fun <Input, Output> sampleLabeledDemonstrations(
    candidates: List<Demonstration<Input, Output>>,
    k: Int,
    random: Random,
): List<Demonstration<Input, Output>> {
    return candidates.shuffled(random).take(k.coerceAtMost(candidates.size))
}

/**
 * Extracts the text content from the first assistant response.
 */
internal fun extractAssistantContent(responses: List<Message.Response>): String {
    return responses
        .filterIsInstance<Message.Assistant>()
        .firstOrNull()
        ?.content
        ?: ""
}

/**
 * Executes a prompt and extracts the assistant's text content.
 *
 * @return The assistant's response text, or null if the call fails or returns blank.
 */
internal suspend fun PromptExecutor.executeAndExtract(
    prompt: Prompt,
    model: LLModel,
): String? {
    return try {
        val responses = execute(prompt, model)
        extractAssistantContent(responses).ifBlank { null }
    } catch (e: Exception) {
        logger.debug(e) { "LLM call failed for prompt '${prompt.id}'" }
        null
    }
}
