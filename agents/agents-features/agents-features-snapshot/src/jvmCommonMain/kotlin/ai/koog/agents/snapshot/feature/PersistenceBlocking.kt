@file:OptIn(InternalKoogUtils::class)

package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.utils.annotations.InternalKoogUtils
import ai.koog.utils.concurrency.runBlockingReentrant

@OptIn(InternalAgentsApi::class, InternalKoogUtils::class)
internal actual fun <T> runBlockingOnStrategy(
    agentConfig: AIAgentConfig,
    block: suspend () -> T,
): T = runBlockingReentrant(agentConfig.strategyDispatcher) { block() }
