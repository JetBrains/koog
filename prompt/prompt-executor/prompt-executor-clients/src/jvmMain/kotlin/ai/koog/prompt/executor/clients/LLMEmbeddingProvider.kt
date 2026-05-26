@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(InternalPromptAPI::class)

package ai.koog.prompt.executor.clients

import ai.koog.agents.annotations.JavaAPI
import ai.koog.prompt.annotations.InternalPromptAPI
import ai.koog.prompt.llm.LLModel
import ai.koog.utils.annotations.InternalKoogUtils
import ai.koog.utils.concurrency.runBlockingReentrant

/**
 * JVM-specific abstract base class for LLM embedding providers.
 *
 * Extends [LLMEmbeddingProviderAPI] with blocking Java-friendly wrapper methods so that Java
 * callers can invoke embedding without dealing with Kotlin coroutines directly.
 */
public actual abstract class LLMEmbeddingProvider actual constructor() : LLMEmbeddingProviderAPI {

    /**
     * Blocking Java-friendly wrapper around [embed] for single-text embedding.
     *
     * Runs the suspending [embed] call and blocks until the result is available.
     *
     * @param text The text to embed.
     * @param model The model to use for embedding. Must have the Embed capability.
     * @return A list of floating-point values representing the embedding vector.
     * @throws IllegalArgumentException if the model does not have the Embed capability.
     */
    @OptIn(InternalKoogUtils::class)
    @JavaAPI
    @JvmName("embed")
    public fun embedBlocking(
        text: String,
        model: LLModel
    ): List<Double> = runBlockingReentrant {
        embed(text, model)
    }

    /**
     * Blocking Java-friendly wrapper around [embed] for batch embedding.
     *
     * Runs the suspending [embed] call and blocks until the result is available.
     *
     * @param inputs The list of texts to embed.
     * @param model The model to use for embedding. Must have the Embed capability.
     * @return A list of embedding vectors, one per input string.
     * @throws IllegalArgumentException if the model does not have the Embed capability.
     */
    @OptIn(InternalKoogUtils::class)
    @JavaAPI
    @JvmName("embed")
    public fun embedBlocking(
        inputs: List<String>,
        model: LLModel
    ): List<List<Double>> = runBlockingReentrant {
        embed(inputs, model)
    }
}
