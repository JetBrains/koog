package ai.koog.prompt.executor.factory

import ai.koog.prompt.executor.builder.MultiLLMPromptExecutorBuilder
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider

/**
 * Source-compatible factory for [MultiLLMPromptExecutorBuilder]. Returns a built [PromptExecutor].
 */
@Suppress("FunctionName")
public fun MultiLLMPromptExecutor(
    llmClients: Map<LLMProvider, LLMClient>,
    fallback: MultiLLMPromptExecutorBuilder.FallbackPromptExecutorSettings? = null,
): PromptExecutor = MultiLLMPromptExecutorBuilder(llmClients, fallback).build()

/**
 * Source-compatible factory for [MultiLLMPromptExecutorBuilder]. Returns a built [PromptExecutor].
 */
@Suppress("FunctionName")
public fun MultiLLMPromptExecutor(
    vararg llmClients: Pair<LLMProvider, LLMClient>,
    fallback: MultiLLMPromptExecutorBuilder.FallbackPromptExecutorSettings? = null,
): PromptExecutor = MultiLLMPromptExecutorBuilder(*llmClients, fallback = fallback).build()

/**
 * Source-compatible factory for [MultiLLMPromptExecutorBuilder]. Returns a built [PromptExecutor].
 */
@Suppress("FunctionName")
public fun MultiLLMPromptExecutor(
    llmClients: List<LLMClient>,
    fallback: MultiLLMPromptExecutorBuilder.FallbackPromptExecutorSettings? = null,
): PromptExecutor = MultiLLMPromptExecutorBuilder(llmClients, fallback).build()

/**
 * Source-compatible factory for [MultiLLMPromptExecutorBuilder]. Returns a built [PromptExecutor].
 */
@Suppress("FunctionName")
public fun MultiLLMPromptExecutor(vararg llmClients: LLMClient): PromptExecutor =
    MultiLLMPromptExecutorBuilder(*llmClients).build()
