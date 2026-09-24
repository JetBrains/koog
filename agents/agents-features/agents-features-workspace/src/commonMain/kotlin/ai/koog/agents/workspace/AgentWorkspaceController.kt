package ai.koog.agents.workspace

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.feature.persistence
import ai.koog.agents.workspace.model.AgentCancellationMode
import ai.koog.agents.workspace.model.AgentDecisionReceipt
import ai.koog.agents.workspace.model.AgentInputRequest
import ai.koog.agents.workspace.model.AgentInputRequestKind
import ai.koog.agents.workspace.model.AgentInputResponse
import ai.koog.agents.workspace.model.AgentWorkspaceContent
import ai.koog.agents.workspace.model.AgentWorkspaceEvent
import ai.koog.agents.workspace.model.AgentWorkspaceInterruption
import ai.koog.agents.workspace.model.AgentWorkspaceRunOutcome
import ai.koog.agents.workspace.model.AgentWorkspaceRunSnapshot
import ai.koog.agents.workspace.model.AgentWorkspaceRunStatus
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Coordinates externally supervised agent execution without owning graph state.
 *
 * Graph state remains in Koog Persistence. This controller stores interruption authority,
 * cancellation intent, user responses, and replayable workspace events.
 */
public class AgentWorkspaceController(
    private val store: AgentWorkspaceStore,
    private val now: () -> String = { Clock.System.now().toString() },
) {
    /** Starts a supervised run and converts terminal conditions to an explicit outcome. */
    public suspend fun <Output> run(
        runId: String,
        block: suspend () -> Output,
    ): AgentWorkspaceRunOutcome<Output> {
        val timestamp = now()
        val created = store.createRun(AgentWorkspaceRunSnapshot(runId, AgentWorkspaceRunStatus.RUNNING, timestamp, timestamp))
        if (!created) {
            return AgentWorkspaceRunOutcome.Failed(IllegalStateException("Workspace run '$runId' already exists"))
        }
        emit(runId, "run.started", JsonObject(emptyMap()))
        return execute(runId, block)
    }

    private suspend fun <Output> execute(
        runId: String,
        block: suspend () -> Output,
    ): AgentWorkspaceRunOutcome<Output> {
        return try {
            val value = block()
            transition(runId, AgentWorkspaceRunStatus.COMPLETED)
            emit(runId, "run.completed", JsonObject(emptyMap()))
            AgentWorkspaceRunOutcome.Completed(value)
        } catch (suspended: AgentWorkspaceSuspendedException) {
            AgentWorkspaceRunOutcome.Suspended(suspended.interruption)
        } catch (cancelled: AgentWorkspaceCancelledException) {
            transition(runId, AgentWorkspaceRunStatus.CANCELLED)
            emit(
                runId,
                "run.cancelled",
                JsonObject(mapOf("mode" to JsonPrimitive(cancelled.mode.name), "reason" to JsonPrimitive(cancelled.reason ?: "")))
            )
            AgentWorkspaceRunOutcome.Cancelled(cancelled.mode, cancelled.reason)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val snapshot = requireRun(runId)
            replace(snapshot, snapshot.copy(status = AgentWorkspaceRunStatus.FAILED, updatedAt = now(), error = error.message))
            emit(runId, "run.failed", JsonObject(mapOf("message" to JsonPrimitive(error.message ?: "Agent run failed"))))
            AgentWorkspaceRunOutcome.Failed(error)
        }
    }

    /** Runs a Koog agent under workspace lifecycle tracking. */
    public suspend fun <Input, Output> runAgent(
        runId: String,
        agent: AIAgent<Input, Output>,
        input: Input,
    ): AgentWorkspaceRunOutcome<Output> = run(runId) { agent.run(input, runId) }

    /** Resumes a Koog agent from a Persistence checkpoint after validating a current decision receipt. */
    public suspend fun <Input, Output> resumeAgent(
        runId: String,
        agent: AIAgent<Input, Output>,
        input: Input,
        checkpoint: AgentCheckpointData,
    ): AgentWorkspaceRunOutcome<Output> {
        claimResume(runId, checkpoint.checkpointId)?.let { return AgentWorkspaceRunOutcome.Failed(it) }
        return execute(runId) { Persistence.runFromCheckpoint(agent, input, checkpoint, sessionId = runId) }
    }

    internal suspend fun claimResume(runId: String, checkpointId: String): Throwable? {
        val snapshot: AgentWorkspaceRunSnapshot
        try {
            snapshot = requireRun(runId)
            require(snapshot.status == AgentWorkspaceRunStatus.WAITING_FOR_INPUT) { "Run '$runId' is not waiting for input" }
            val interruption = requireNotNull(snapshot.interruption) { "Run '$runId' has no pending interruption" }
            val receipt = requireNotNull(snapshot.decisionReceipt) { "Run '$runId' has no decision receipt" }
            require(receipt.checkpointId == checkpointId) { "Decision receipt does not match checkpoint" }
            require(receipt.requestHash == interruption.requestHash) { "Decision receipt is stale" }
            require(!receipt.superseded) { "Decision receipt has been superseded" }
            require(receipt.revalidated) { "Decision receipt must be revalidated before resume" }
            requireDecisionIsCurrent(receipt)
        } catch (error: IllegalArgumentException) {
            emit(runId, "run.resume_failed", JsonObject(mapOf("message" to JsonPrimitive(error.message ?: "Resume validation failed"))))
            return error
        }
        val claimed = store.compareAndSetRun(
            snapshot.revision,
            snapshot.copy(
                status = AgentWorkspaceRunStatus.RUNNING,
                updatedAt = now(),
                revision = snapshot.revision + 1,
            ),
        )
        if (!claimed) {
            val error = IllegalStateException("Run '$runId' was already resumed or changed")
            emit(runId, "run.resume_failed", JsonObject(mapOf("message" to JsonPrimitive(error.message.orEmpty()))))
            return error
        }
        emit(runId, "run.resumed", JsonObject(mapOf("checkpointId" to JsonPrimitive(checkpointId))))
        return null
    }

    /** Suspends [context] using its latest durable Persistence checkpoint. */
    public suspend fun suspendForInput(
        context: AIAgentContext,
        request: AgentInputRequest,
        requestHash: String,
        expiresAt: String? = null,
    ): Nothing {
        val checkpoint = requireNotNull(context.persistence().getLatestCheckpoint(context.runId)) {
            "A durable checkpoint is required before requesting external input"
        }
        suspendForInput(
            runId = context.runId,
            graphNode = context.executionInfo.path(),
            checkpointId = checkpoint.checkpointId,
            request = request,
            requestHash = requestHash,
            expiresAt = expiresAt,
        )
    }

    /** Persists an interruption and stops the current controlled run. */
    @OptIn(ExperimentalUuidApi::class)
    public suspend fun suspendForInput(
        runId: String,
        graphNode: String,
        checkpointId: String,
        request: AgentInputRequest,
        requestHash: String,
        expiresAt: String? = null,
    ): Nothing {
        val timestamp = now()
        val interruption = AgentWorkspaceInterruption(
            id = Uuid.random().toString(),
            runId = runId,
            graphNode = graphNode,
            checkpointId = checkpointId,
            request = request,
            requestHash = requestHash,
            createdAt = timestamp,
            expiresAt = expiresAt,
        )
        val snapshot = requireRun(runId)
        replace(
            snapshot,
            snapshot.copy(
                status = AgentWorkspaceRunStatus.WAITING_FOR_INPUT,
                updatedAt = timestamp,
                interruption = interruption,
                decisionReceipt = null,
            ),
        )
        emit(
            runId,
            "agent.input_required",
            JsonObject(
                mapOf(
                    "interruptionId" to JsonPrimitive(interruption.id),
                    "requestId" to JsonPrimitive(request.id),
                    "checkpointId" to JsonPrimitive(checkpointId),
                )
            )
        )
        throw AgentWorkspaceSuspendedException(interruption)
    }

    /** Records and validates an external response. Call [revalidateDecision] before resuming. */
    public suspend fun answer(runId: String, response: AgentInputResponse): AgentDecisionReceipt {
        val snapshot = requireRun(runId)
        val interruption = requireNotNull(snapshot.interruption) { "Run '$runId' is not waiting for input" }
        require(snapshot.status == AgentWorkspaceRunStatus.WAITING_FOR_INPUT) { "Run '$runId' is not waiting for input" }
        require(snapshot.decisionReceipt == null) { "Run '$runId' already has a response" }
        require(response.requestId == interruption.request.id) { "Response does not match the pending request" }
        validateResponse(interruption.request, response)

        val receipt = AgentDecisionReceipt(
            runId = runId,
            requestId = response.requestId,
            checkpointId = interruption.checkpointId,
            graphNode = interruption.graphNode,
            requestHash = interruption.requestHash,
            response = response,
            expiresAt = interruption.expiresAt,
        )
        replace(snapshot, snapshot.copy(updatedAt = now(), decisionReceipt = receipt))
        emit(runId, "agent.input_received", JsonObject(mapOf("requestId" to JsonPrimitive(response.requestId))))
        return receipt
    }

    /** Marks an unexpired decision receipt as revalidated against current external state. */
    public suspend fun revalidateDecision(runId: String): AgentDecisionReceipt {
        val snapshot = requireRun(runId)
        val receipt = requireNotNull(snapshot.decisionReceipt) { "Run '$runId' has no decision receipt" }
        require(!receipt.superseded) { "Decision receipt has been superseded" }
        requireDecisionIsCurrent(receipt)
        val updated = receipt.copy(revalidated = true)
        replace(snapshot, snapshot.copy(updatedAt = now(), decisionReceipt = updated))
        emit(runId, "agent.decision_revalidated", JsonObject(mapOf("requestId" to JsonPrimitive(receipt.requestId))))
        return updated
    }

    /** Requests cooperative cancellation. */
    public suspend fun requestCancellation(runId: String, mode: AgentCancellationMode, reason: String? = null) {
        val snapshot = requireRun(runId)
        if (snapshot.status in TERMINAL_STATUSES || snapshot.status == AgentWorkspaceRunStatus.CANCELLATION_REQUESTED) return
        replace(
            snapshot,
            snapshot.copy(
                status = AgentWorkspaceRunStatus.CANCELLATION_REQUESTED,
                updatedAt = now(),
                cancellationMode = mode,
                cancellationReason = reason,
            ),
        )
        emit(runId, "run.cancellation_requested", JsonObject(mapOf("mode" to JsonPrimitive(mode.name))))
    }

    /** Applies an immediate cancellation request before invoking a tool or model. */
    public suspend fun beforeAction(runId: String) {
        cancelIfRequested(runId, AgentCancellationMode.IMMEDIATE)
    }

    /** Applies immediate or after-node cancellation at a safe graph boundary. */
    public suspend fun afterNode(runId: String) {
        val snapshot = requireRun(runId)
        val mode = snapshot.cancellationMode ?: return
        if (snapshot.status == AgentWorkspaceRunStatus.CANCELLATION_REQUESTED) {
            throw AgentWorkspaceCancelledException(mode, snapshot.cancellationReason)
        }
    }

    /** Emits a typed content item without interpreting its application-specific payload. */
    public suspend fun emitContent(runId: String, content: AgentWorkspaceContent): AgentWorkspaceEvent = emit(
        runId,
        "content.created",
        JsonObject(mapOf("content" to Json.encodeToJsonElement(AgentWorkspaceContent.serializer(), content)))
    )

    /** Returns replayable events after [afterSequence]. */
    public suspend fun events(runId: String, afterSequence: Long = 0): List<AgentWorkspaceEvent> =
        store.listEvents(runId, afterSequence)

    /** Returns the durable workspace snapshot. */
    public suspend fun snapshot(runId: String): AgentWorkspaceRunSnapshot? = store.loadRun(runId)

    private suspend fun cancelIfRequested(runId: String, boundary: AgentCancellationMode) {
        val snapshot = requireRun(runId)
        if (
            snapshot.status == AgentWorkspaceRunStatus.CANCELLATION_REQUESTED &&
            snapshot.cancellationMode == boundary
        ) {
            throw AgentWorkspaceCancelledException(boundary, snapshot.cancellationReason)
        }
    }

    private fun validateResponse(request: AgentInputRequest, response: AgentInputResponse) {
        val selected = response.selectedOptionIds.distinct()
        val allowed = request.options.mapTo(mutableSetOf()) { it.id }
        require(selected.all(allowed::contains)) { "Response contains an unknown option" }
        when (request.kind) {
            AgentInputRequestKind.FREE_TEXT -> require(!response.text.isNullOrBlank()) { "A text response is required" }
            AgentInputRequestKind.SINGLE_CHOICE -> require(selected.size == 1) { "Exactly one option is required" }
            AgentInputRequestKind.MULTIPLE_CHOICE -> require(selected.isNotEmpty()) { "At least one option is required" }
            AgentInputRequestKind.CONFIRMATION,
            AgentInputRequestKind.APPROVAL -> require(selected.size == 1 || !response.text.isNullOrBlank()) {
                "A decision is required"
            }
        }
        require(request.allowFreeText || response.text.isNullOrBlank()) { "Free text is not allowed for this request" }
    }

    private fun requireDecisionIsCurrent(receipt: AgentDecisionReceipt) {
        val expiresAt = receipt.expiresAt ?: return
        require(Instant.parse(expiresAt) > Instant.parse(now())) { "Decision receipt has expired" }
    }

    private suspend fun transition(runId: String, status: AgentWorkspaceRunStatus) {
        val snapshot = requireRun(runId)
        replace(snapshot, snapshot.copy(status = status, updatedAt = now()))
    }

    private suspend fun replace(
        previous: AgentWorkspaceRunSnapshot,
        next: AgentWorkspaceRunSnapshot,
    ) {
        check(
            store.compareAndSetRun(
                previous.revision,
                next.copy(revision = previous.revision + 1),
            )
        ) { "Workspace run '${previous.runId}' changed concurrently" }
    }

    private suspend fun requireRun(runId: String): AgentWorkspaceRunSnapshot =
        requireNotNull(store.loadRun(runId)) { "Unknown workspace run '$runId'" }

    private suspend fun emit(runId: String, type: String, data: JsonObject): AgentWorkspaceEvent =
        store.appendEvent(AgentWorkspaceEvent(0L, runId, type, data, now()))

    private companion object {
        val TERMINAL_STATUSES: Set<AgentWorkspaceRunStatus> = setOf(
            AgentWorkspaceRunStatus.CANCELLED,
            AgentWorkspaceRunStatus.COMPLETED,
            AgentWorkspaceRunStatus.FAILED,
        )
    }
}

/** Internal control-flow exception converted to [AgentWorkspaceRunOutcome.Suspended]. */
public class AgentWorkspaceSuspendedException(
    public val interruption: AgentWorkspaceInterruption,
) : Exception("Agent run '${interruption.runId}' is waiting for external input")

/** Internal cooperative-cancellation signal converted to [AgentWorkspaceRunOutcome.Cancelled]. */
public class AgentWorkspaceCancelledException(
    public val mode: AgentCancellationMode,
    public val reason: String?,
) : CancellationException(reason ?: "Agent run cancelled")
