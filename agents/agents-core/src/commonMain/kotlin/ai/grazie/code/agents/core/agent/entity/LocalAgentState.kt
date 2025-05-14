package ai.grazie.code.agents.core.agent.entity

import ai.grazie.code.agents.core.utils.ActiveProperty
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalStdlibApi::class)
internal class LocalAgentState internal constructor(
    iterations: Int = 0,
) : AutoCloseable {
    var iterations: Int by ActiveProperty(iterations) { isActive }

    private var isActive = true

    override fun close() {
        isActive = false
    }
}

public class LocalAgentStateManager internal constructor(
    private var state: LocalAgentState = LocalAgentState()
) {
    private val mutex = Mutex()

    internal suspend fun <T> withStateLock(block: suspend (LocalAgentState) -> T): T = mutex.withLock {
        val result = block(state)
        val newState = LocalAgentState(
            iterations = state.iterations
        )

        // close this snapshot and create a new one
        state.close()
        state = newState

        result
    }
}
