package ai.koog.prompt.executor.llms

import ai.koog.prompt.executor.model.PromptExecutorEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal class PromptExecutorEventSink {

    private val writableEvents = MutableSharedFlow<PromptExecutorEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )

    val events: SharedFlow<PromptExecutorEvent> = writableEvents.asSharedFlow()

    suspend fun emit(event: PromptExecutorEvent) {
        writableEvents.emit(event)
    }
}
