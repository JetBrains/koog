package ai.koog.a2a.server.session

import ai.koog.a2a.model.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

/**
 * Represents a session with lifecycle management.
 *
 * @property eventProcessor The session event processor
 * @property agentJob The execution process associated with this session's execution
 * @property events A stream of events generated during this session
 */
public class Session(
    public val eventProcessor: SessionEventProcessor,
    public val agentJob: Deferred<Unit>
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
     * Suspends until the session, i.e., event stream and agent job, complete.
     * Waits for the event stream to finish first, to avoid triggering the agent job prematurely.
     * Assumes that by the time event stream is finished, agent job will already be completed or canceled.
     */
    public suspend fun join() {
        events.collect()
        agentJob.join()
    }

    /**
     * [start] and then [join] the session.
     */
    public suspend fun startAndJoin() {
        start()
        join()
    }

    /**
     * Cancels the execution process, waiting for it to complete, and then closes event processor.
     */
    public suspend fun cancelAndJoin() {
        agentJob.cancelAndJoin()
        eventProcessor.close()
    }
}

/**
 * Creates an instance of [Session] with lazily started [Session.agentJob]
 *
 * @param coroutineScope The coroutine scope to use for running the [block]
 * @param eventProcessor The session event processor
 * @param block The block to be executed
 */
@Suppress("ktlint:standard:function-naming", "FunctionName")
public fun LazySession(
    coroutineScope: CoroutineScope,
    eventProcessor: SessionEventProcessor,
    block: suspend CoroutineScope.() -> Unit
): Session {
    return Session(
        eventProcessor = eventProcessor,
        agentJob = coroutineScope.async(start = CoroutineStart.LAZY, block = block)
    )
}
