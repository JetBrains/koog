@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.context.AgentContextData
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.prompt.message.Message
import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonElement

/**
 * Represents the checkpoint data for an agent's state during a session.
 *
 * @property checkpointId The unique identifier of the checkpoint. This allows tracking and restoring the agent's session to a specific state.
 * @property messageHistory A list of messages exchanged in the session up to the checkpoint. Messages include interactions between the user, system, assistant, and tools.
 * @property nodeId The identifier of the node where the checkpoint was created.
 * @property lastInput Serialized input received for node with [nodeId]
 */
@Serializable
public data class AgentCheckpointData(
    val checkpointId: String,
    val createdAt: Instant,
    val nodeId: String,
    val lastInput: JsonElement,
    val messageHistory: List<Message>,
)

/**
 * Converts an instance of [AgentCheckpointData] to [AgentContextData].
 *
 * The conversion maps the `messageHistory`, `nodeId`, and `lastInput` properties of
 * [AgentCheckpointData] directly to a new [AgentContextData] instance.
 *
 * @return A new [AgentContextData] instance containing the message history, node ID,
 * and last input from the [AgentCheckpointData].
 */
public fun AgentCheckpointData.toAgentContextData(): AgentContextData {
    return AgentContextData(
        messageHistory = messageHistory,
        nodeId = nodeId,
        lastInput = lastInput
    )
}