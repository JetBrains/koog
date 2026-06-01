package ai.koog.a2a.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.jvm.JvmStatic

/**
 * An event sent by the agent to notify the client of a change in a task's status.
 * This is typically used in streaming or subscription models.
 *
 * @property taskId The ID of the task that was updated.
 * @property contextId The context ID associated with the task.
 * @property status The new status of the task.
 * @property metadata Optional metadata for extensions.
 */
@Serializable
public data class TaskStatusUpdateEvent(
    override val taskId: String,
    override val contextId: String,
    public val status: TaskStatus,
    public val metadata: JsonObject? = null,
) : TaskEvent {
    public companion object {
        @JvmStatic
        public const val KIND: String = "statusUpdate"
    }
}

/**
 * An event sent by the agent to notify the client that an artifact has been
 * generated or updated. This is typically used in streaming models.
 *
 * @property taskId The ID of the task this artifact belongs to.
 * @property contextId The context ID associated with the task.
 * @property artifact The artifact that was generated or updated.
 * @property append If true, the content of this artifact should be appended to a previously sent artifact with the same ID.
 * @property lastChunk If true, this is the final chunk of the artifact.
 * @property metadata Optional metadata for extensions.
 */
@Serializable
public data class TaskArtifactUpdateEvent(
    override val taskId: String,
    override val contextId: String,
    public val artifact: Artifact,
    public val append: Boolean? = null,
    public val lastChunk: Boolean? = null,
    public val metadata: JsonObject? = null,
) : TaskEvent {
    public companion object {
        @JvmStatic
        public const val KIND: String = "artifactUpdate"
    }
}

