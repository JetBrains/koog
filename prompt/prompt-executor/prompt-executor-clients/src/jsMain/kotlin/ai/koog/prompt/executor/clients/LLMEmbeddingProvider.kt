@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.prompt.executor.clients

/**
 * Extension of the LLMClient interface which includes functionality for generating text embeddings
 * in addition to executing prompts and streaming outputs.
 */
public actual abstract class LLMEmbeddingProvider actual constructor() : LLMEmbeddingProviderAPI
