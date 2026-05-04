package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.config.AIAgentConfig

internal actual fun <T> runBlockingOnStrategy(
    agentConfig: AIAgentConfig,
    block: suspend () -> T,
): T = throw UnsupportedOperationException(
    "Blocking execution is only supported on JVM/Android targets."
)
