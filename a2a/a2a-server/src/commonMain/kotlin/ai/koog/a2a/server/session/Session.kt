package ai.koog.a2a.server.session

import ai.koog.a2a.model.Event
import ai.koog.a2a.model.Task
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow

/**
 * One turn of a task: a single [ai.koog.a2a.server.agent.AgentExecutor.execute] call, started with
 * [SessionManager.startTurn].
 *
 * A session only covers its own turn. Events of the task that happen outside this turn (earlier or later turns,
 * cancellation of an idle task) are not part of [events]; use [SessionManager.subscribe] to follow a task across
 * turns.
 *
 * @property taskId Task ID associated with this session. The task might not exist yet, e.g. when the agent has not
 * created it yet or replies with a message instead.
 * @property contextId Context ID associated with this session.
 * @property agentJob The job executing the agent for this turn. [SessionManager] starts it right after creating the
 * session; awaiting it propagates the agent's exception, if any.
 */
public class Session internal constructor(
    public val taskId: String,
    public val contextId: String,
    public val agentJob: Deferred<Unit>,
    internal val eventProcessor: SessionEventProcessor,
    events: Flow<Event>,
    internal val completion: CompletableJob,
    /**
     * For a turn that continues an existing task: the stored task, read atomically with the registration of
     * [events], i.e. the state of the task right before the first event of [events]. `null` for a turn of a new task.
     */
    internal val taskSnapshot: Task?,
) {
    /**
     * This turn's view of the task's event stream: every event the task receives while the turn runs (including
     * events published by a concurrent cancellation), plus the agent's [ai.koog.a2a.model.Message] reply, if any.
     *
     * The flow completes after the last event of the turn, i.e. when the turn ends or the task reaches a terminal
     * state, whichever comes first.
     *
     * Delivery is back-pressured: the agent's event publishing waits until this flow's collector has accepted each
     * event, so the flow must be collected (and collection cancelled when the consumer goes away), see
     * [SessionManager]. It was registered before the agent started, so no event of the turn is lost, but it can be
     * collected **only once**.
     */
    public val events: Flow<Event> = events

    /**
     * Suspends until the turn has fully ended: the agent job has completed, its events have been published, and the
     * session has been removed from [SessionManager].
     */
    public suspend fun join() {
        completion.join()
    }
}
