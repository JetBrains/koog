package ai.koog.agents.core.prompt.factory

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.prompt.builder.ContextualPromptExecutorBuilder
import ai.koog.prompt.executor.model.PromptExecutor

/**
 * Source-compatible factory for [ContextualPromptExecutorBuilder]. Returns a built [PromptExecutor].
 */
@InternalAgentsApi
@Suppress("FunctionName")
public fun ContextualPromptExecutor(
    executor: PromptExecutor,
    context: AIAgentContext,
): PromptExecutor = ContextualPromptExecutorBuilder(executor, context).build()
