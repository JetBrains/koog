package ai.koog.a2a.server.tasks

import ai.koog.a2a.model.Task

/**
 * Which parts of a task to return. The defaults give the full task.
 *
 * @property historyLength `null` keeps all history, `0` drops it (`history = null`), `N` keeps the `N` most recent
 * messages.
 * @property includeArtifacts `false` drops the artifacts (`artifacts = null`).
 * @throws IllegalArgumentException if [historyLength] is negative.
 */
public data class TaskProjection(
    public val historyLength: Int? = null,
    public val includeArtifacts: Boolean = true,
) {
    init {
        require(historyLength == null || historyLength >= 0) {
            "historyLength must be non-negative, but was $historyLength"
        }
    }
}

/** Only the task's id, contextId, status and metadata: for the server's bookkeeping reads. */
internal val STATUS_ONLY: TaskProjection = TaskProjection(historyLength = 0, includeArtifacts = false)

/**
 * Trims this task.
 *
 * @param projection the parts of the task to keep
 * @return this task trimmed as [projection] says, or `this` if nothing changes
 */
public fun Task.project(projection: TaskProjection): Task {
    val projectedHistory = when (val historyLength = projection.historyLength) {
        null -> history
        0 -> null
        else -> history?.takeLast(historyLength)
    }
    val projectedArtifacts = if (projection.includeArtifacts) artifacts else null

    return if (projectedHistory == history && projectedArtifacts == artifacts) {
        this
    } else {
        copy(history = projectedHistory, artifacts = projectedArtifacts)
    }
}
