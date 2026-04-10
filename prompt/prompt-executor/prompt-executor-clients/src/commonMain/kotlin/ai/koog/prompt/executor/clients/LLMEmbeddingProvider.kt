@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.prompt.executor.clients

/**
 * Abstract base class for LLM embedding providers.
 *
 * Implements [LLMEmbeddingProviderAPI] and, on JVM platforms, also exposes blocking Java-friendly
 * wrapper methods (`embedBlocking`) so that Java callers can invoke embedding without dealing with
 * Kotlin coroutines directly.
 */
public expect abstract class LLMEmbeddingProvider() : LLMEmbeddingProviderAPI
