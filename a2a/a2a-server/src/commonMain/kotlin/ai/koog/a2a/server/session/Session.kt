package ai.koog.a2a.server.session

import ai.koog.a2a.model.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Represents an active agent execution session with lifecycle management.
 *
 * @property eventProcessor The session event processor
 * @property agentJob The job executing the agent logic
 * @property contextId Unique context ID associated with this session
 * @property taskId Unique task ID associated with this session
 * @property events A stream of events generated during this session
 */
public class Session(
    public val eventProcessor: SessionEventProcessor,
    public val agentJob: Job
) {
    public val contextId: String get() = eventProcessor.contextId
    public val taskId: String get() = eventProcessor.taskId
    public val events: Flow<Event> get() = eventProcessor.events

    /**
     * Starts the [agentJob], if it hasn't already been started.
     */
    public fun start() {
        agentJob.start()
    }

    /*
     * Suspends until the session, i.e., agent job and event stream, complete.
     */
    public suspend fun join() {
        agentJob.join()
        events.collect()
    }

    /**
     * Cancels the agent job, waiting for it to complete, and then closes event processor.
     */
    public suspend fun cancel() {
        agentJob.cancelAndJoin()
        eventProcessor.close()
    }
}

/**
 * Factory function that creates a new [Session] with lazy-started [agentAction].
 *
 * @param coroutineScope The scope for launching the agent coroutine
 * @param eventProcessor The session event processor
 * @param agentAction The agent logic to execute
 * @return A new session instance
 */
@Suppress("ktlint:standard:function-naming", "FunctionName")
public fun AgentSession(
    coroutineScope: CoroutineScope,
    eventProcessor: SessionEventProcessor,
    agentAction: suspend CoroutineScope.() -> Unit
): Session {
    val agentJob = coroutineScope.launch(start = CoroutineStart.LAZY) {
        agentAction()
    }

    return Session(
        eventProcessor = eventProcessor,
        agentJob = agentJob
    )
}
