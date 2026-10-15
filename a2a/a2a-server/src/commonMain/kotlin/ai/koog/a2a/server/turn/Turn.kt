package ai.koog.a2a.server.turn

import ai.koog.a2a.model.Event
import ai.koog.a2a.model.Task
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow

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
 */
public class Turn internal constructor(
    public val taskId: String,
    public val contextId: String,
    public val agentJob: Deferred<Unit>,
    internal val eventProcessor: TurnEventProcessor,
    events: Flow<Event>,
    internal val completion: CompletableJob,
    /**
     * For a turn that continues an existing task: the stored task, read atomically with the registration of [events],
     * i.e. as of right before its first event. `null` for a turn of a new task.
     */
    internal val taskSnapshot: Task?,
) {
    /**
     * This turn's view of the task's event stream: every event the task receives while the turn runs (including
     * events published by a concurrent cancellation), plus the agent's [ai.koog.a2a.model.Message] reply, if any.
     * Completes when the turn ends or the task reaches a terminal state, whichever comes first.
     *
     * Delivery is back-pressured, so the flow must be collected (and collection cancelled when the consumer goes
     * away), see [TurnManager]. No event of the turn is lost, but it can be collected **only once**.
     */
    public val events: Flow<Event> = events

    /** Suspends until the turn has fully ended: the agent job completed, its events were published, and the turn was removed. */
    public suspend fun join() {
        completion.join()
    }
}
