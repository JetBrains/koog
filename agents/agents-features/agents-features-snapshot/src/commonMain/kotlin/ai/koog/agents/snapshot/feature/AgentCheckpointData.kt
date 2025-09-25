@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.context.AgentContextData
import ai.koog.agents.core.agent.context.RollbackStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.snapshot.providers.PersistencyUtils
import ai.koog.prompt.message.Message
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Represents the checkpoint data for an agent's state during a session.
 *
 * @property checkpointId The unique identifier of the checkpoint. This allows tracking and restoring the agent's session to a specific state.
 * @property messageHistory A list of messages exchanged in the session up to the checkpoint. Messages include interactions between the user, system, assistant, and tools.
 * @property nodeId The identifier of the node where the checkpoint was created.
 * @property lastInput Serialized input received for node with [nodeId]
 * @property properties Additional data associated with the checkpoint. This can be used to store additional information about the agent's state.
 */
@Serializable
public data class AgentCheckpointData(
    val checkpointId: String,
    val createdAt: Instant,
    val nodeId: String,
    val lastInput: JsonElement,
    val messageHistory: List<Message>,
    val properties: Map<String, JsonElement>? = null
)

/**
 * Creates a tombstone checkpoint for an agent's session.
 * A tombstone checkpoint represents a placeholder state with no real interactions or messages,
 * intended to mark a terminated or invalid session.
 *
 * @return An `AgentCheckpointData` instance with predefined properties indicating a tombstone state.
 */
@OptIn(ExperimentalUuidApi::class)
public fun tombstoneCheckpoint(time: Instant): AgentCheckpointData {
    return AgentCheckpointData(
        checkpointId = Uuid.random().toString(),
        createdAt = time,
        nodeId = PersistencyUtils.TOMBSTONE_CHECKPOINT_NAME,
        lastInput = JsonNull,
        messageHistory = emptyList(),
        properties = mapOf(PersistencyUtils.TOMBSTONE_CHECKPOINT_NAME to JsonPrimitive(true))
    )
}

/**
 * Converts an instance of [AgentCheckpointData] to [AgentContextData].
 *
 * The conversion maps the `messageHistory`, `nodeId`, and `lastInput` properties of
 * [AgentCheckpointData] directly to a new [AgentContextData] instance.
 *
 * @return A new [AgentContextData] instance containing the message history, node ID,
 * and last input from the [AgentCheckpointData].
 */
public fun AgentCheckpointData.toAgentContextData(rollbackStrategy: RollbackStrategy): AgentContextData {
    return AgentContextData(
        messageHistory = messageHistory,
        nodeId = nodeId,
        lastInput = lastInput,
        rollbackStrategy
    )
}

/**
 * Checks whether the `AgentCheckpointData` instance is marked as a tombstone.
 *
 * A tombstone typically indicates that the checkpoint represents a terminated or inactive state.
 *
 * @return `true` if the `properties` map contains a key-value pair where the key is "tombstone"
 *         and the value is a JSON primitive set to `true`, otherwise `false`.
 */
public fun AgentCheckpointData.isTombstone(): Boolean = properties?.get(PersistencyUtils.TOMBSTONE_CHECKPOINT_NAME) == JsonPrimitive(true)
