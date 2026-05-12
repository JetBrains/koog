package ai.koog.agents.core.utils

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.utils.annotations.InternalKoogUtils
import ai.koog.utils.concurrency.runBlockingReentrant
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executor

/**
 * Executes the given [block] on the [executor] if it is specified, or falls back to the [llmRequestDispatcher].
 */
@OptIn(InternalKoogUtils::class)
@InternalAgentsApi
public fun <T> AIAgentConfig.runBlockingOnLLMDispatcher(
    executor: Executor? = null,
    block: suspend () -> T
): T = runBlockingReentrant(executor?.asCoroutineDispatcher() ?: llmRequestDispatcher, block)

/**
 * Executes the given [block] on the [executor] if it is specified, or falls back to the [strategyDispatcher].
 */
@OptIn(InternalKoogUtils::class)
@InternalAgentsApi
public fun <T> AIAgentConfig.runBlockingOnStrategyDispatcher(
    executor: Executor? = null,
    block: suspend () -> T
): T {
    return runBlockingReentrant(executor?.asCoroutineDispatcher() ?: strategyDispatcher, block)
}
