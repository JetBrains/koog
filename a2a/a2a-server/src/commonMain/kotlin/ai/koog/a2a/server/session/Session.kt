package ai.koog.a2a.server.session

import ai.koog.a2a.model.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

public class Session(
    public val eventProcessor: SessionEventProcessor,
    public val agentJob: Job
) {
    public val events: SharedFlow<Event> get() = eventProcessor.events
    public val contextId: String get() = eventProcessor.contextId

    public fun start() {
        agentJob.start()
    }

    public suspend fun join() {
        agentJob.join()
    }

    public fun close() {
        agentJob.cancel()
        eventProcessor.close()
    }
}

public fun Session(
    coroutineScope: CoroutineScope,
    eventProcessor: SessionEventProcessor,
    agentAction: suspend CoroutineScope.() -> Unit
): Session {
    val agentJob = coroutineScope.launch(start = CoroutineStart.LAZY, block = agentAction)
    return Session(eventProcessor, agentJob)
}
