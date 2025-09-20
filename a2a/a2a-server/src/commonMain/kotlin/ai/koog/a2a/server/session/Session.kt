package ai.koog.a2a.server.session

import ai.koog.a2a.model.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * Represents an active agent execution session with lifecycle management.
 *
 * @property eventProcessor Handles session events and provides event streaming
 * @property agentJob The coroutine job executing the agent logic
 * @property events A stream of events generated during this session
 * @property contextId Unique context identifier for this session
 */
public class Session(
    public val eventProcessor: SessionEventProcessor,
    public val agentJob: Job
) {
    public val events: SharedFlow<Event> get() = eventProcessor.events
    public val contextId: String get() = eventProcessor.contextId

    /**
     * Starts the agent execution job.
     */
    public fun start() {
        agentJob.start()
    }

    /*
     * Suspends until the agent job completes
     */
    public suspend fun join() {
        agentJob.join()
    }

    /**
     * Cancels the agent job and closes the event processor
     */
    public fun close() {
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
