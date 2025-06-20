@file:Suppress("MissingKDocForPublicAPI") // TODO REMOVE
@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.context.AgentContextData
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.prompt.message.Message

public class AgentCheckpointData(
    public val checkpointId: String,
    public val messageHistory: List<Message>,
    public val nodeId: String,
    public val lastInput: Any?,
) {
    public companion object {
        public const val SNAPSHOT_ID_PREFIX: String = "snapshot-"
    }

    override fun toString(): String = "AgentSnapshot(snapshotId=${getSnapshotKey()})')"

    public fun getSnapshotKey(): String = "$SNAPSHOT_ID_PREFIX${nodeId}-$checkpointId"
}

public fun AgentCheckpointData.toAgentContextData(): AgentContextData {
    return AgentContextData(
        messageHistory = messageHistory,
        nodeId = nodeId,
        lastInput = lastInput
    )
}