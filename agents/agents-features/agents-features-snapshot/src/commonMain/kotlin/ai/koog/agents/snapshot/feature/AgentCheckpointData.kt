@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.context.AgentContextData
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.prompt.message.Message
import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Represents the checkpoint data for an agent's state during a session.
 *
 * This class captures the complete state of an agent at a specific point in time,
 * including execution context, memory snapshot, and optional custom data.
 * This enables "PortableAgent" functionality - agents that can be saved, transferred, and
 * restored with full context preservation.
 *
 * @property checkpointId The unique identifier of the checkpoint. This allows tracking and restoring the agent's session to a specific state.
 * @property createdAt The timestamp when this checkpoint was created.
 * @property nodeId The identifier of the node where the checkpoint was created.
 * @property lastInput Serialized input received for node with [nodeId]
 * @property messageHistory A list of messages exchanged in the session up to the checkpoint. Messages include interactions between the user, system, assistant, and tools.
 * @property memorySnapshot Optional snapshot of the agent's memory facts at checkpoint time. When present, enables memory-synchronized restoration.
 * @property extraSnapshotData Optional custom data for domain-specific state (e.g., game world state, IDE context, external system state).
 */
@Serializable
public data class AgentCheckpointData(
    val checkpointId: String,
    val createdAt: Instant,
    val nodeId: String,
    val lastInput: JsonElement,
    val messageHistory: List<Message>,
    val memorySnapshot: JsonObject? = null,
    val extraSnapshotData: JsonObject? = null,
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