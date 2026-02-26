@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.prompt.executor.clients

/**
 * Common interface for direct communication with LLM providers.
 * This interface defines methods for executing prompts and streaming responses.
 *
 * Implements [AutoCloseable] as LLM clients typically work with IO resources. Always close it when finished.
 */
public expect abstract class LLMClient() : LLMClientAPI
