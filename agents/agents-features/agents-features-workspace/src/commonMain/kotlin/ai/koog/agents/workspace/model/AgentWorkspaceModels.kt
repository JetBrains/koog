package ai.koog.agents.workspace.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Current lifecycle state of a supervised agent run. */
@Serializable
public enum class AgentWorkspaceRunStatus {
    QUEUED,
    RUNNING,
    WAITING_FOR_INPUT,
    CANCELLATION_REQUESTED,
    CANCELLED,
    COMPLETED,
    FAILED,
}

/** The boundary at which a cooperative cancellation request takes effect. */
@Serializable
public enum class AgentCancellationMode {
    IMMEDIATE,
    AFTER_NODE,
}

/** Supported external-input request shapes. */
@Serializable
public enum class AgentInputRequestKind {
    FREE_TEXT,
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    CONFIRMATION,
    APPROVAL,
}

/** A choice shown by a user-facing input surface. */
@Serializable
public data class AgentInputOption(
    val id: String,
    val label: String,
    val description: String? = null,
)

/**
 * A serializable request for external input.
 *
 * Applications can render this as a question card, confirmation, or approval surface. The
 * framework validates response cardinality but does not prescribe a UI.
 */
@Serializable
public data class AgentInputRequest(
    val id: String,
    val kind: AgentInputRequestKind,
    val prompt: String,
    val options: List<AgentInputOption> = emptyList(),
    val allowFreeText: Boolean = kind == AgentInputRequestKind.FREE_TEXT,
    val metadata: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(id.isNotBlank()) { "Input request id cannot be blank" }
        require(id.length <= 160) { "Input request id cannot exceed 160 characters" }
        require(prompt.isNotBlank()) { "Input request prompt cannot be blank" }
        require(prompt.length <= 8_000) { "Input request prompt cannot exceed 8000 characters" }
        require(options.size <= 20) { "Input requests cannot contain more than 20 options" }
        require(options.map { it.id }.distinct().size == options.size) { "Input option ids must be unique" }
        require(
            kind == AgentInputRequestKind.FREE_TEXT ||
                allowFreeText ||
                options.isNotEmpty()
        ) { "Choice requests require at least one option" }
    }
}

/** An external response supplied to a suspended run. */
@Serializable
public data class AgentInputResponse(
    val requestId: String,
    val selectedOptionIds: List<String> = emptyList(),
    val text: String? = null,
    val actor: String? = null,
    val surface: String? = null,
    val respondedAt: String,
    val metadata: JsonObject = JsonObject(emptyMap()),
)

/** Durable decision receipt used to reject stale or mismatched resumes. */
@Serializable
public data class AgentDecisionReceipt(
    val runId: String,
    val requestId: String,
    val checkpointId: String,
    val graphNode: String,
    val requestHash: String,
    val response: AgentInputResponse,
    val expiresAt: String? = null,
    val superseded: Boolean = false,
    val revalidated: Boolean = false,
)

/** Serializable description of why and where an agent run suspended. */
@Serializable
public data class AgentWorkspaceInterruption(
    val id: String,
    val runId: String,
    val graphNode: String,
    val checkpointId: String,
    val request: AgentInputRequest,
    val requestHash: String,
    val createdAt: String,
    val expiresAt: String? = null,
    val supersededAt: String? = null,
)

/** Extensible content emitted by an agent workspace. Unknown [kind] values remain readable. */
@Serializable
public data class AgentWorkspaceContent(
    val id: String,
    val kind: String,
    val title: String? = null,
    val mimeType: String? = null,
    val payload: JsonObject = JsonObject(emptyMap()),
    val schemaVersion: Int = 1,
) {
    init {
        require(id.isNotBlank()) { "Content id cannot be blank" }
        require(kind.isNotBlank()) { "Content kind cannot be blank" }
        require(schemaVersion > 0) { "Content schema version must be positive" }
    }
}

/** Reference to an artifact stored by the host application. */
@Serializable
public data class AgentArtifactReference(
    val id: String,
    val title: String,
    val mimeType: String,
    val uri: String,
    val sizeBytes: Long? = null,
    val checksum: String? = null,
    val metadata: JsonObject = JsonObject(emptyMap()),
)

/** A replayable event emitted by an agent workspace. */
@Serializable
public data class AgentWorkspaceEvent(
    val sequence: Long,
    val runId: String,
    val type: String,
    val data: JsonObject,
    val createdAt: String,
)

/** Durable host-owned state associated with one supervised run. */
@Serializable
public data class AgentWorkspaceRunSnapshot(
    val runId: String,
    val status: AgentWorkspaceRunStatus,
    val createdAt: String,
    val updatedAt: String,
    val revision: Long = 0,
    val interruption: AgentWorkspaceInterruption? = null,
    val decisionReceipt: AgentDecisionReceipt? = null,
    val cancellationMode: AgentCancellationMode? = null,
    val cancellationReason: String? = null,
    val error: String? = null,
) {
    init {
        require(revision >= 0) { "Run revision cannot be negative" }
    }
}

/** Explicit result of a supervised agent execution. */
public sealed interface AgentWorkspaceRunOutcome<out Output> {
    /** The run produced a final value. */
    public data class Completed<Output>(val value: Output) : AgentWorkspaceRunOutcome<Output>

    /** The run stopped at a durable external-input boundary. */
    public data class Suspended(val interruption: AgentWorkspaceInterruption) : AgentWorkspaceRunOutcome<Nothing>

    /** The run was intentionally cancelled. */
    public data class Cancelled(val mode: AgentCancellationMode, val reason: String?) : AgentWorkspaceRunOutcome<Nothing>

    /** The run failed unexpectedly. */
    public data class Failed(val error: Throwable) : AgentWorkspaceRunOutcome<Nothing>
}
