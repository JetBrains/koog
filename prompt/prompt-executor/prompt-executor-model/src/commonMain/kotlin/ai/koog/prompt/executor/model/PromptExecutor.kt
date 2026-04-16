@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.prompt.executor.model

/**
 * An interface representing an executor for processing LLM prompts.
 * This defines methods for executing prompts against models with or without tool assistance,
 * as well as for streaming responses.
 *
 * Implements [AutoCloseable] as prompt executors typically work with LLM clients. Always close it when finished.
 *
 * Note: a single [PromptExecutor] might embed multiple LLM clients for different LLM providers supporting different models.
 */
public expect abstract class PromptExecutor() : PromptExecutorAPI
