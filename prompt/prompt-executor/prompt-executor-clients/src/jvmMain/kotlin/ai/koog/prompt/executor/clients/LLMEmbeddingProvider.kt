@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(InternalPromptAPI::class)

package ai.koog.prompt.executor.clients

import ai.koog.agents.annotations.JavaAPI
import ai.koog.prompt.annotations.InternalPromptAPI
import ai.koog.prompt.execution.utils.runOnIOBoundDispatcher
import ai.koog.prompt.llm.LLModel
import java.util.concurrent.ExecutorService

/**
 * Extension of the LLMClient interface which includes functionality for generating text embeddings
 * in addition to executing prompts and streaming outputs.
 */
public actual abstract class LLMEmbeddingProvider actual constructor() : LLMEmbeddingProviderAPI {

    /**
     * Embeds the given text using into a vector of double-precision numbers.
     *
     * @param text The text to embed.
     * @param model The model to use for embedding. Must have the Embed capability.
     * @return A list of floating-point values representing the embedding.
     * @throws IllegalArgumentException if the model does not have the Embed capability.
     */
    @JavaAPI
    @JvmName("embed")
    @JvmOverloads
    public fun embedBlocking(
        text: String,
        model: LLModel,
        executorService: ExecutorService? = null
    ): List<Double> = runOnIOBoundDispatcher(executorService) { embed(text, model) }

    /**
     * Embeds the given input using the given model into a vector of double-precision numbers.
     *
     * @param inputs The input to embed.
     * @param model The model to use for embedding. Must have the Embed capability.
     * @return A list of lists of floating-point values representing the embedding.
     * Each inner list represents a single input embedding.
     * @throws IllegalArgumentException if the model does not have the Embed capability.
     */
    @JavaAPI
    @JvmName("embed")
    @JvmOverloads
    public fun embedBlocking(
        inputs: List<String>,
        model: LLModel,
        executorService: ExecutorService? = null
    ): List<List<Double>> = runOnIOBoundDispatcher(executorService) { embed(inputs, model) }
}
