package ai.koog.prompt.executor.factory

import ai.koog.prompt.executor.builder.RoutingLLMPromptExecutorBuilder
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.llms.LLMClientRouter
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider

/**
 * Source-compatible factory for [RoutingLLMPromptExecutorBuilder]. Returns a built [PromptExecutor].
 */
@Suppress("FunctionName")
public fun RoutingLLMPromptExecutor(
    clientRouter: LLMClientRouter,
    fallback: RoutingLLMPromptExecutorBuilder.FallbackPromptExecutorSettings? = null,
): PromptExecutor = RoutingLLMPromptExecutorBuilder(clientRouter, fallback).build()

@Suppress("FunctionName")
public fun RoutingLLMPromptExecutor(
    llmClients: Map<LLMProvider, List<LLMClient>>,
    fallback: RoutingLLMPromptExecutorBuilder.FallbackPromptExecutorSettings? = null,
): PromptExecutor = RoutingLLMPromptExecutorBuilder(llmClients, fallback).build()

@Suppress("FunctionName")
public fun RoutingLLMPromptExecutor(
    llmClients: List<LLMClient>,
    fallback: RoutingLLMPromptExecutorBuilder.FallbackPromptExecutorSettings? = null,
): PromptExecutor = RoutingLLMPromptExecutorBuilder(llmClients, fallback).build()

@Suppress("FunctionName")
public fun RoutingLLMPromptExecutor(
    vararg llmClients: LLMClient,
    fallback: RoutingLLMPromptExecutorBuilder.FallbackPromptExecutorSettings? = null,
): PromptExecutor = RoutingLLMPromptExecutorBuilder(*llmClients, fallback = fallback).build()
