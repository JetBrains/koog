@file:Suppress("MissingKDocForPublicAPI") // TODO REMOVE

package ai.koog.agents.snapshot.feature

import ai.koog.prompt.message.Message

public class AgentSnapshot(
    public val agentId: String,
    public val snapshotId: String,
    public val lastExecutedNodeId: String,
    public val messages: List<Message>,
    public val lastOutput: Any?) {

    public companion object {
        public const val SNAPSHOT_ID_PREFIX: String = "snapshot-"
    }

    override fun toString(): String = "AgentSnapshot(agentId='$agentId', snapshotId='$snapshotId')"

    public fun getSnapshotKey(): String = "$SNAPSHOT_ID_PREFIX$snapshotId"
}