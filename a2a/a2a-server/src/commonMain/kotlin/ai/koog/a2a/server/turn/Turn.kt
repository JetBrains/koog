package ai.koog.a2a.server.turn

import ai.koog.a2a.exceptions.A2AException
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.Task
import ai.koog.a2a.server.exceptions.InvalidEventException
import ai.koog.a2a.server.exceptions.TaskOperationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow

/**
 * One turn of a task: a single [ai.koog.a2a.server.agent.AgentExecutor.execute] call, started with
 * [TurnManager.startTurn].
 *
 * Events of the task outside this turn (earlier or later turns, cancellation of an idle task) are not part of
 * [events]; use [TurnManager.subscribe] to follow a task across turns.
 *
 * @property taskId Task ID associated with this turn. The task might not exist yet, e.g. when the agent hasn't
 * created it yet or replies with a message instead.
 * @property contextId Context ID associated with this turn.
 * @property agentJob The job executing the agent for this turn; awaiting it propagates the agent's exception, if any.
 * A [kotlinx.coroutines.CancellationException] thrown by the agent itself (e.g. from its own `withTimeout`) is
 * rethrown as a failure, with the original exception as its cause.
 */
public class Turn internal constructor(
    public val taskId: String,
    public val contextId: String,
    public val agentJob: Deferred<Unit>,
    internal val eventProcessor: TurnEventProcessor,
    /** This turn's view of the task stream, see [events]. */
    private val view: ReceiveChannel<Event>,
    /** The job that waits for the agent job and then ends the turn, see [TurnManager.startTurn]. */
    private val monitor: Job,
    /**
     * For a turn that continues an existing task: the stored task as of right before the turn's first event (read when
     * the turn started, see [TurnManager.startTurn]), with the request's history limit applied. `null` for a turn of a
     * new task.
     */
    internal val taskSnapshot: Task?,
    /**
     * How the turn ended. Completed by [TurnManager] before the turn's end completes [events], so it tells a consumer
     * of [events] why the turn ended without a terminal update, but not which party sent an update it received.
     */
    internal val outcome: Deferred<TurnOutcome>,
) {
    /**
     * This turn's view of the task's event stream: every event the task receives while the turn runs (including
     * events published by a concurrent cancellation), plus the agent's [ai.koog.a2a.model.Message] reply, if any.
     * Completes when the turn ends or the task reaches a terminal state, whichever comes first.
     *
     * Delivery is back-pressured, so the flow must be collected (and collection cancelled when the consumer goes
     * away), see [TurnManager]. No event of the turn is lost, but it can be collected **only once**.
     */
    public val events: Flow<Event> = view.consumeAsFlow()

    /** Stops [events] for a consumer that won't collect it (any more), so deliveries to it don't wait. */
    internal fun cancelEvents() {
        view.cancel()
    }

    /**
     * Suspends until the turn has fully ended: the agent job completed, its events were published, the turn was
     * removed, and its resources were released.
     */
    public suspend fun join() {
        monitor.join()
    }
}

/** How a [Turn] ended, decided by [TurnManager] once the agent job has completed. */
internal sealed interface TurnOutcome {
    /**
     * The agent returned, or its job was cancelled (e.g. by CancelTask or server shutdown).
     *
     * @property rejection the first event of the turn rejected as invalid, if the agent went on despite it
     */
    data class Completed(val rejection: Exception?) : TurnOutcome

    /**
     * The agent failed with the exception rejecting one of its events ([InvalidEventException], or
     * [TaskOperationException] from the storage). The task, if it exists and isn't terminal, is marked failed, but that
     * update isn't part of [Turn.events]: the requester gets an error instead.
     */
    data class Rejected(val cause: Exception) : TurnOutcome

    /**
     * The agent failed with an [A2AException] before publishing any event of the turn, so the turn changed nothing:
     * the task is left as it was, and the requester gets [cause] as the error.
     */
    data class RequestError(val cause: A2AException) : TurnOutcome

    /**
     * The agent failed otherwise. The task, if it exists and isn't terminal, is marked failed, and [Turn.events] ends
     * with that update.
     */
    data class Failed(val cause: Throwable) : TurnOutcome

    companion object {
        /**
         * The outcome of a turn whose agent job ended with [failure] (`null` if it returned), with [rejection] being
         * the first of its events rejected as invalid, if any, and [isEventPublished] telling whether any event of the
         * turn was published (or may have been written to the storage). Rejected events and ignored [Task]s aren't
         * published events.
         */
        fun of(failure: Throwable?, rejection: Exception?, isEventPublished: Boolean): TurnOutcome = when (failure) {
            null, is CancellationException -> Completed(rejection)
            is InvalidEventException -> Rejected(failure)
            is TaskOperationException -> Rejected(failure)
            is A2AException -> if (isEventPublished) Failed(failure) else RequestError(failure)
            else -> Failed(failure)
        }
    }
}
