package ai.koog.agents.core.utils

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.utils.annotations.InternalKoogUtils
import ai.koog.utils.concurrency.runBlockingReentrant

/**
 * Executes the given [block] on the [llmRequestDispatcher].
 */
@OptIn(InternalKoogUtils::class)
@InternalAgentsApi
public fun <T> AIAgentConfig.runBlockingOnLLMDispatcher(
    block: suspend () -> T
): T = runBlockingReentrant(llmRequestDispatcher, block)

/**
 * Executes the given [block] on the [strategyDispatcher].
 */
@OptIn(InternalKoogUtils::class)
@InternalAgentsApi
public fun <T> AIAgentConfig.runBlockingOnStrategyDispatcher(
    block: suspend () -> T
): T = runBlockingReentrant(strategyDispatcher, block)
