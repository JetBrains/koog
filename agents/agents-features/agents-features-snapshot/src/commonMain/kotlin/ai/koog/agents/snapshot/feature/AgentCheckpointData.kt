@file:Suppress("MissingKDocForPublicAPI") // TODO REMOVE

package ai.koog.agents.snapshot.feature

import ai.koog.prompt.message.Message

public class AgentCheckpointData(
    public val messageHistory: List<Message>,
    public val nodeId: String,
    public val lastInput: Any?,
) {
    public companion object {
        public const val SNAPSHOT_ID_PREFIX: String = "snapshot-"
    }

    override fun toString(): String = "AgentSnapshot(agentId=, snapshotId=')"

    public fun getSnapshotKey(): String = "$SNAPSHOT_ID_PREFIX${nodeId}"
}