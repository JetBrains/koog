@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.prompt.executor.clients

/**
 * Abstract base class for LLM embedding providers on Android.
 *
 * Implements [LLMEmbeddingProviderAPI]. Platform-specific blocking wrappers are not available on
 * this target; use the suspending [embed] methods from [LLMEmbeddingProviderAPI] directly.
 */
public actual abstract class LLMEmbeddingProvider actual constructor() : LLMEmbeddingProviderAPI
