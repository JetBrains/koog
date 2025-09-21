package ai.koog.a2a.server.session

import ai.koog.a2a.model.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Represents an active agent execution session with lifecycle management.
 *
 * @property eventProcessor Handles session events and provides event streaming
 * @property agentJob The coroutine job executing the agent logic
 * @property contextId Unique context ID associated with this session, delegates to [SessionEventProcessor.contextId]
 * @property taskId Unique task ID associated with this session, delegates to [SessionEventProcessor.contextId]
 * @property events A stream of events generated during this session, delegates to [SessionEventProcessor.events]
 */
public class Session(
    public val eventProcessor: SessionEventProcessor,
    public val agentJob: Job
) {
    public val contextId: String get() = eventProcessor.contextId
    public val taskId: String get() = eventProcessor.taskId
    public val events: Flow<Event> get() = eventProcessor.events

    /**
     * Starts the agent execution job.
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
     * Cancels the agent job and closes the event processor
     */
    public suspend fun close() {
        agentJob.cancel()
        eventProcessor.close()
    }
}

/**
 * Creates a new [Session] with lazy-started agent execution.
 *
 * @param coroutineScope The scope for launching the agent coroutine
 * @param eventProcessor The session event processor
 * @param agentAction The agent logic to execute
 * @return A new session instance
 */
public fun Session(
    coroutineScope: CoroutineScope,
    eventProcessor: SessionEventProcessor,
    agentAction: suspend CoroutineScope.() -> Unit
): Session {
    val agentJob = coroutineScope.launch(start = CoroutineStart.LAZY, block = agentAction)
    return Session(eventProcessor, agentJob)
}
