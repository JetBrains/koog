@file:OptIn(InternalAgentsApi::class)
@file:JvmName("RunFromCheckpointJvm")

package ai.koog.agents.snapshot.feature

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.context.RollbackStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.utils.runOnStrategyDispatcher

/**
 * Blocking version of [runFromCheckpoint] for use from Java.
 *
 * @see runFromCheckpoint
 */
@JavaAPI
@JvmOverloads
@JvmName("runFromCheckpoint")
public fun <Input, Output> AIAgent<Input, Output>.runFromCheckpointBlocking(
    agentInput: Input,
    checkpoint: AgentCheckpointData,
    rollbackStrategy: RollbackStrategy = RollbackStrategy.Default,
    sessionId: String? = null,
): Output = agentConfig.runOnStrategyDispatcher {
    runFromCheckpoint(agentInput, checkpoint, rollbackStrategy, sessionId)
}
