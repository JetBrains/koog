@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(InternalPromptAPI::class)

package ai.koog.prompt.executor.clients

import ai.koog.agents.annotations.JavaAPI
import ai.koog.prompt.annotations.InternalPromptAPI
import ai.koog.prompt.execution.utils.runOnIOBoundDispatcher
import ai.koog.prompt.llm.LLModel
import java.util.concurrent.ExecutorService

/**
 * JVM-specific abstract base class for LLM embedding providers.
 *
 * Extends [LLMEmbeddingProviderAPI] with blocking Java-friendly wrapper methods so that Java
 * callers can invoke embedding without dealing with Kotlin coroutines directly.
 * The blocking wrappers run on an IO-bound dispatcher unless a custom [ExecutorService] is provided.
 */
public actual abstract class LLMEmbeddingProvider actual constructor() : LLMEmbeddingProviderAPI {

    /**
     * Blocking Java-friendly wrapper around [embed] for single-text embedding.
     *
     * Runs the suspending [embed] call on an IO-bound dispatcher and blocks until the result
     * is available.
     *
     * @param text The text to embed.
     * @param model The model to use for embedding. Must have the Embed capability.
     * @param executorService An optional [ExecutorService] to control the execution context.
     *   If `null`, a default IO-bound dispatcher is used.
     * @return A list of floating-point values representing the embedding vector.
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
     * Blocking Java-friendly wrapper around [embed] for batch embedding.
     *
     * Runs the suspending [embed] call on an IO-bound dispatcher and blocks until the result
     * is available.
     *
     * @param inputs The list of texts to embed.
     * @param model The model to use for embedding. Must have the Embed capability.
     * @param executorService An optional [ExecutorService] to control the execution context.
     *   If `null`, a default IO-bound dispatcher is used.
     * @return A list of embedding vectors, one per input string.
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
