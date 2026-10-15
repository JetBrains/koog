package ai.koog.a2a.server.tasks

import ai.koog.a2a.model.Task

/**
 * Returns a copy of this task trimmed to the representation requested by a client.
 *
 * This is the reference implementation of the `historyLength` / `includeArtifacts` rules of the A2A `GetTask` and
 * `ListTasks` operations. Custom [TaskStorage] implementations should use it (or reproduce it exactly) when they
 * serve [TaskStorage.get] and [TaskStorage.list], so the wire representation stays protocol-compliant.
 *
 * - [historyLength]
 *   - `null`: no limit, [Task.history] is returned unchanged.
 *   - `0`: no history is requested, [Task.history] is set to `null` (the field is omitted on the wire, it is **not**
 *     an empty list).
 *   - `N > 0`: [Task.history] holds at most the `N` most recent messages. A `null` history stays `null`.
 * - [includeArtifacts]
 *   - `true`: [Task.artifacts] is returned unchanged.
 *   - `false`: [Task.artifacts] is set to `null` (the field is omitted on the wire).
 *
 * All other properties of the task are returned unchanged. The original task is never modified.
 *
 * @param historyLength the maximum number of most recent history messages to keep, `null` for no limit
 * @param includeArtifacts whether to keep the task artifacts
 * @throws IllegalArgumentException if [historyLength] is negative. [ai.koog.a2a.server.A2AServer] validates this
 * beforehand and reports it as an invalid params error.
 */
public fun Task.project(historyLength: Int?, includeArtifacts: Boolean): Task {
    require(historyLength == null || historyLength >= 0) { "historyLength must be non-negative, but was $historyLength" }

    val projectedHistory = when {
        historyLength == null -> history
        historyLength == 0 -> null
        else -> history?.takeLast(historyLength)
    }
    val projectedArtifacts = if (includeArtifacts) artifacts else null

    return if (projectedHistory == history && projectedArtifacts == artifacts) {
        this
    } else {
        copy(history = projectedHistory, artifacts = projectedArtifacts)
    }
}
