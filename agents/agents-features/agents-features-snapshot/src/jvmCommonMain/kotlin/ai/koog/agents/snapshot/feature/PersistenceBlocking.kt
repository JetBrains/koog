@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.utils.runOnStrategyDispatcher

internal actual fun <T> runBlockingOnStrategy(
    agentConfig: AIAgentConfig,
    block: suspend () -> T,
): T = agentConfig.runOnStrategyDispatcher { block() }
