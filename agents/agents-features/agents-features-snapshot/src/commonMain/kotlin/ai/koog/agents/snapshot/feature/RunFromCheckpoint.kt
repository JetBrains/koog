@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.RollbackStrategy
import ai.koog.agents.core.agent.context.agentContextDataAdditionalKey
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.session.AIAgentRunSession
import ai.koog.agents.core.agent.session.AIAgentSessionInputs
import ai.koog.agents.core.annotation.InternalAgentsApi

/**
 * Runs the agent from a previously saved checkpoint.
 *
 * This extension creates a new session and injects the checkpoint data into the session's storage
 * so that the agent's graph strategy restores execution from the checkpoint's position.
 * The [Persistence] feature does **not** need to be installed on the agent for this to work.
 *
 * @param agentInput The input to provide to the agent.
 * @param checkpoint The checkpoint data to restore from.
 * @param rollbackStrategy The strategy to use when restoring state. Defaults to [RollbackStrategy.Default].
 * @param sessionId Optional session identifier. A random UUID is generated if not provided.
 * @return The output produced by the agent after resuming from the checkpoint.
 */
public suspend fun <Input, Output> AIAgent<Input, Output>.runFromCheckpoint(
    agentInput: Input,
    checkpoint: AgentCheckpointData,
    rollbackStrategy: RollbackStrategy = RollbackStrategy.Default,
    sessionId: String? = null,
): Output {
    return createSession(sessionId).runFromCheckpoint(agentInput, checkpoint, rollbackStrategy)
}

/**
 * Runs the session from a previously saved checkpoint.
 *
 * This extension injects the checkpoint data into the session's storage so that the agent's
 * graph strategy restores execution from the checkpoint's position.
 * The [Persistence] feature does **not** need to be installed on the agent for this to work.
 *
 * @param input The input to provide to the session.
 * @param checkpoint The checkpoint data to restore from.
 * @param rollbackStrategy The strategy to use when restoring state. Defaults to [RollbackStrategy.Default].
 * @return The output produced by the session after resuming from the checkpoint.
 */
public suspend fun <Input, Output, TContext : AIAgentContext> AIAgentRunSession<Input, Output, TContext>.runFromCheckpoint(
    input: Input,
    checkpoint: AgentCheckpointData,
    rollbackStrategy: RollbackStrategy = RollbackStrategy.Default,
): Output {
    val storage = AIAgentStorage()
    storage.set(agentContextDataAdditionalKey, checkpoint.toAgentContextData(rollbackStrategy))
    return run(input, AIAgentSessionInputs(storage = storage))
}
